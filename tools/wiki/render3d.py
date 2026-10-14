"""Offline renderer for block/item model JSON (elements, faces, UVs, textures, rotations), for the wiki's big item pictures.

Draws the model the way the game's GUI does: the model's ``display.gui`` rotation, orthographic, camera looking along -z with +y
up, then fitted into a square transparent canvas. Textures are sampled nearest (crisp texels); the picture is rendered at 4x and
averaged down so the silhouette is smooth. Renders are cached by a hash of the model and its textures (``.cache/renders``).
"""
import hashlib
import io
import json
import math
import os

import numpy as np
from PIL import Image

from icons import first_model, split_id

VERSION = "3"
FACES = ("north", "south", "east", "west", "up", "down")
NORMALS = {"north": (0, 0, -1), "south": (0, 0, 1), "east": (1, 0, 0), "west": (-1, 0, 0), "up": (0, 1, 0), "down": (0, -1, 0)}
DEFAULT_GUI = (30.0, 225.0, 0.0)
# The game's yaw sign: Minecraft's display rotation is composed so that +y turns the model the opposite way to a plain
# right-handed rotation. Flip this if the offline render ever comes out mirrored against the in-game one.
YAW_SIGN = 1.0
LIGHT = np.array([-0.35, 0.8, 0.5])
LIGHT = LIGHT / np.linalg.norm(LIGHT)
FLAT_PARENTS = ("builtin/generated", "builtin/entity", "item/generated", "item/handheld", "item/handheld_rod",
                "builtin/missing", "builtin/compass", "builtin/clock")


def rot_x(a):
    c, s = math.cos(a), math.sin(a)
    return np.array([[1, 0, 0], [0, c, -s], [0, s, c]], dtype=float)


def rot_y(a):
    c, s = math.cos(a), math.sin(a)
    return np.array([[c, 0, s], [0, 1, 0], [-s, 0, c]], dtype=float)


def rot_z(a):
    c, s = math.cos(a), math.sin(a)
    return np.array([[c, -s, 0], [s, c, 0], [0, 0, 1]], dtype=float)


def face_corners(frm, to, name):
    """Top-left, top-right, bottom-right, bottom-left as seen from outside the face (same layout as the game)."""
    x1, y1, z1 = frm
    x2, y2, z2 = to
    if name == "north":
        return [(x2, y2, z1), (x1, y2, z1), (x1, y1, z1), (x2, y1, z1)]
    if name == "south":
        return [(x1, y2, z2), (x2, y2, z2), (x2, y1, z2), (x1, y1, z2)]
    if name == "east":
        return [(x2, y2, z2), (x2, y2, z1), (x2, y1, z1), (x2, y1, z2)]
    if name == "west":
        return [(x1, y2, z1), (x1, y2, z2), (x1, y1, z2), (x1, y1, z1)]
    if name == "up":
        return [(x1, y2, z1), (x2, y2, z1), (x2, y2, z2), (x1, y2, z2)]
    return [(x1, y1, z2), (x2, y1, z2), (x2, y1, z1), (x1, y1, z1)]


def default_uv(frm, to, name):
    x1, y1, z1 = frm
    x2, y2, z2 = to
    return {
        "down": [x1, 16 - z2, x2, 16 - z1], "up": [x1, z1, x2, z2],
        "north": [16 - x2, 16 - y2, 16 - x1, 16 - y1], "south": [x1, 16 - y2, x2, 16 - y1],
        "west": [z1, 16 - y2, z2, 16 - y1], "east": [16 - z2, 16 - y2, 16 - z1, 16 - y1],
    }[name]


class Resolved:
    def __init__(self, model_id, elements, textures, display, gui_light):
        self.model_id, self.elements, self.textures, self.display, self.gui_light = model_id, elements, textures, display, gui_light


class Renderer:
    def __init__(self, assets, cache_dir):
        self.a = assets
        self.cache = os.path.join(cache_dir, "renders")
        os.makedirs(self.cache, exist_ok=True)
        self.tex_memo = {}

    # ------------------------------------------------------------------ model resolution
    def resolve(self, model_id, depth=0):
        """Merged model (elements, textures, display, gui_light) following parents, or None if it has no elements."""
        if depth > 10:
            return None
        ns, path = split_id(model_id)
        if any(path.endswith(p) for p in FLAT_PARENTS):
            return None
        m = self.a.read_json(ns, "models/%s.json" % path)
        if m is None:
            return None
        textures, display, elements, light = {}, {}, None, None
        parent = m.get("parent")
        if parent:
            p = self.resolve(parent, depth + 1)
            if p is not None:
                textures.update(p.textures)
                display.update(p.display)
                elements, light = p.elements, p.gui_light
        textures.update(m.get("textures", {}))
        display.update(m.get("display", {}))
        if "elements" in m:
            elements = m["elements"]
        if "gui_light" in m:
            light = m["gui_light"]
        if not elements:
            return None
        return Resolved(model_id, elements, textures, display, light or "side")

    def model_for_item(self, item_id):
        ns, path = split_id(item_id)
        candidates = []
        d = self.a.read_json(ns, "items/%s.json" % path)
        if d is not None:
            mid = first_model(d.get("model", d))
            if mid:
                candidates.append(mid)
        candidates += ["%s:item/%s" % (ns, path), "%s:block/%s" % (ns, path)]
        for mid in candidates:
            r = self.resolve(mid)
            if r is not None and not self.all_flat(r):
                return r
        return None

    @staticmethod
    def all_flat(model):
        """Crossed planes (plants and the like) read better as their flat sprite."""
        for el in model.elements:
            if min(abs(a - b) for a, b in zip(el["from"], el["to"])) > 1e-6:
                return False
        return True

    def texture(self, ref, textures):
        n = 0
        while isinstance(ref, str) and ref.startswith("#") and n < 10:
            ref = textures.get(ref[1:])
            n += 1
        if isinstance(ref, dict):
            ref = ref.get("sprite")
        if not isinstance(ref, str) or ref.startswith("#"):
            return None, None
        if ref in self.tex_memo:
            return self.tex_memo[ref]
        ns, path = split_id(ref)
        data = self.a.read(ns, "textures/%s.png" % path)
        out = (None, None)
        if data:
            im = Image.open(io.BytesIO(data)).convert("RGBA")
            w, h = im.size
            if h > w and h % w == 0:      # animated strip: first frame
                im = im.crop((0, 0, w, w))
            out = (np.array(im), hashlib.sha1(data).hexdigest())
        self.tex_memo[ref] = out
        return out

    # ------------------------------------------------------------------ rendering
    def render_item(self, item_id, size=256):
        """PNG bytes of the item's model, or None if it has no 3D model (or no usable texture)."""
        model = self.model_for_item(item_id)
        if model is None:
            return None
        gui = model.display.get("gui", {})
        rot = tuple(gui.get("rotation", DEFAULT_GUI)) if gui else DEFAULT_GUI
        digest = hashlib.sha1()
        digest.update(VERSION.encode())
        digest.update(json.dumps([model.elements, model.display.get("gui"), model.gui_light, size], sort_keys=True).encode())
        used = {}
        for el in model.elements:
            for f in el.get("faces", {}).values():
                arr, h = self.texture(f.get("texture"), model.textures)
                if arr is not None:
                    used[f.get("texture")] = arr
                    digest.update(h.encode())
        key = digest.hexdigest()
        path = os.path.join(self.cache, key + ".png")
        if os.path.exists(path):
            with open(path, "rb") as f:
                return f.read()
        if not used:
            return None
        img = self.draw(model, rot, size)
        if img is None:
            return None
        buf = io.BytesIO()
        img.save(buf, "PNG", optimize=True)
        data = buf.getvalue()
        tmp = path + ".tmp"
        with open(tmp, "wb") as f:
            f.write(data)
        os.replace(tmp, path)
        return data

    def faces(self, model):
        out = []
        for el in model.elements:
            frm, to = el["from"], el["to"]
            rot = el.get("rotation")
            shade = el.get("shade", True)
            for name, face in el.get("faces", {}).items():
                if name not in NORMALS:
                    continue
                arr, _ = self.texture(face.get("texture"), model.textures)
                if arr is None:
                    continue
                pts = np.array(face_corners(frm, to, name), dtype=float)
                nrm = np.array(NORMALS[name], dtype=float)
                if rot and rot.get("angle"):
                    origin = np.array(rot.get("origin", [8, 8, 8]), dtype=float)
                    ang = math.radians(rot["angle"])
                    mat = {"x": rot_x, "y": rot_y, "z": rot_z}[rot.get("axis", "y")](ang)
                    pts = (pts - origin) @ mat.T + origin
                    nrm = mat @ nrm
                uv = list(face.get("uv") or default_uv(frm, to, name))
                u1, v1, u2, v2 = uv
                base = [(u1, v1), (u2, v1), (u2, v2), (u1, v2)]
                k = (int(face.get("rotation", 0)) // 90) % 4
                if k:
                    base = base[-k:] + base[:-k]
                out.append({"pts": pts, "nrm": nrm, "uv": base, "tex": arr, "shade": shade,
                            "emit": face.get("light_emission", 0) > 0 or el.get("light_emission", 0) > 0, "name": name})
        return out

    def draw(self, model, gui_rot, size, ss=4):
        rx, ry, rz = (math.radians(a) for a in gui_rot)
        view = rot_x(rx) @ rot_y(ry * YAW_SIGN) @ rot_z(rz)
        faces = self.faces(model)
        if not faces:
            return None
        for f in faces:
            f["p"] = (f["pts"] - 8.0) @ view.T
            f["n"] = view @ f["nrm"]
        allp = np.concatenate([f["p"] for f in faces])
        minx, maxx = allp[:, 0].min(), allp[:, 0].max()
        miny, maxy = allp[:, 1].min(), allp[:, 1].max()
        extent = max(maxx - minx, maxy - miny, 1e-6)
        n = size * ss
        scale = n * 0.88 / extent
        cx, cy = (minx + maxx) / 2, (miny + maxy) / 2
        img = np.zeros((n, n, 4), dtype=np.float32)
        zbuf = np.full((n, n), -1e9, dtype=np.float32)
        flat = model.gui_light == "front"
        for f in faces:
            if f["n"][2] <= 1e-6:
                continue
            p = f["p"]
            sx = n / 2 + (p[:, 0] - cx) * scale
            sy = n / 2 - (p[:, 1] - cy) * scale
            sz = p[:, 2]
            x0, x1 = int(max(0, math.floor(sx.min()))), int(min(n - 1, math.ceil(sx.max())))
            y0, y1 = int(max(0, math.floor(sy.min()))), int(min(n - 1, math.ceil(sy.max())))
            if x1 < x0 or y1 < y0:
                continue
            # p(s, t) = P0 + s (P1 - P0) + t (P3 - P0), solved per pixel
            ax, ay = sx[1] - sx[0], sy[1] - sy[0]
            bx, by = sx[3] - sx[0], sy[3] - sy[0]
            det = ax * by - ay * bx
            if abs(det) < 1e-9:
                continue
            gx, gy = np.meshgrid(np.arange(x0, x1 + 1) + 0.5, np.arange(y0, y1 + 1) + 0.5)
            dx, dy = gx - sx[0], gy - sy[0]
            s = (dx * by - dy * bx) / det
            t = (ax * dy - ay * dx) / det
            inside = (s >= 0) & (s < 1) & (t >= 0) & (t < 1)
            if not inside.any():
                continue
            uv = f["uv"]
            u = (uv[0][0] + s * (uv[1][0] - uv[0][0]) + t * (uv[3][0] - uv[0][0]))
            v = (uv[0][1] + s * (uv[1][1] - uv[0][1]) + t * (uv[3][1] - uv[0][1]))
            tex = f["tex"]
            th, tw = tex.shape[:2]
            tx = np.clip((u / 16.0 * tw).astype(int), 0, tw - 1)
            ty = np.clip((v / 16.0 * th).astype(int), 0, th - 1)
            px = tex[ty, tx]
            z = sz[0] + s * (sz[1] - sz[0]) + t * (sz[3] - sz[0])
            sub = zbuf[y0:y1 + 1, x0:x1 + 1]
            ok = inside & (px[..., 3] >= 128) & (z > sub)
            if not ok.any():
                continue
            if f["emit"] or flat or not f["shade"]:
                lit = 1.0
            else:
                lit = 0.6 + 0.4 * max(0.0, float(f["n"] @ LIGHT))
            rgb = px[..., :3].astype(np.float32) * lit
            region = img[y0:y1 + 1, x0:x1 + 1]
            region[ok, :3] = rgb[ok]
            region[ok, 3] = 255.0
            sub[ok] = z[ok]
        # average down (premultiplied, so edges don't pick up dark fringes)
        a = img[..., 3:4] / 255.0
        pm = np.concatenate([img[..., :3] * a, a], axis=2)
        pm = pm.reshape(size, ss, size, ss, 4).mean(axis=(1, 3))
        alpha = pm[..., 3:4]
        rgb = np.where(alpha > 1e-4, pm[..., :3] / np.maximum(alpha, 1e-4), 0.0)
        out = np.concatenate([np.clip(rgb, 0, 255), np.clip(alpha * 255.0, 0, 255)], axis=2).round().astype(np.uint8)
        return Image.fromarray(out, "RGBA")
