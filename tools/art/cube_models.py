#!/usr/bin/env python3
"""Shared machinery for the cube-model generators of the combat mod's own mobs (generate_dwarven_construct.py,
generate_iron_thane.py).

A generator builds a Model (parts and cubes), packs the cubes into a texture with Minecraft's standard box UV layout,
paints that texture (and its emissive layers) with the helpers below, writes the Java LayerDefinition with
Model.java(), and can render preview images of the result with render_view() / sheet() without starting the game.

Box layout for a cube at texOffs(u, v) of size (w, h, d), as vanilla maps it (model -z is the front, y is down):
    top (u+d, v)  bottom (u+d+w, v)           each w x d
    row at y = v+d:  -x side (u, d wide) | front -z (u+d, w) | +x side (u+d+w, d) | back +z (u+2d+w, w)
Mirrored cubes (left-hand copies) reuse the right-hand cube's UVs. Cube sizes must be whole pixels; positions may be
fractional.
"""
import math
import random
import sys

import numpy as np
from PIL import Image, ImageDraw


# ------------------------------------------------------------------------------------------------ cube table

class Model:
    """Parts form a tree; pivots are relative to the parent (model pixels, y down, feet at y=24 for the root)."""

    def __init__(self, width):
        self.width = width
        self.parts = []
        self.cubes = []
        self.height = 0

    def part(self, name, parent, pivot, rot=(0, 0, 0)):
        if parent is not None and not any(p["name"] == parent for p in self.parts):
            sys.exit(f"part {name}: parent {parent} is not defined yet")
        self.parts.append(dict(name=name, parent=parent, pivot=tuple(pivot), rot=tuple(rot)))

    def cube(self, pname, cid, mat, pos, size, share=None, mirror=False, **kw):
        if any(float(s) != int(s) for s in size):
            sys.exit(f"cube {cid}: sizes must be whole pixels")
        if any(c["id"] == cid for c in self.cubes):
            sys.exit(f"cube {cid} defined twice")
        self.cubes.append(dict(part=pname, id=cid, mat=mat, pos=tuple(pos), size=tuple(int(s) for s in size),
                               share=share, mirror=mirror, **kw))

    def pair(self, pname, cid, mat, pos, size, **kw):
        """A right-hand cube (pos on the right, x < 0) and its mirrored left copy in the same part."""
        self.cube(pname, cid + "_r", mat, pos, size, **kw)
        self.cube(pname, cid + "_l", mat, (-(pos[0] + size[0]), pos[1], pos[2]), size, share=cid + "_r", mirror=True,
                  **{k: v for k, v in kw.items() if k not in ("deco", "glow")})

    def sided(self, base, parent, pivot, rot, cubes):
        """Right and left parts r_<base> / l_<base> holding the same cubes (pivot and cubes given for the right side,
        x < 0). A parent containing {s} names the same side's part. Each cube is (id, material, pos, size, kwargs)."""
        for s in ("r", "l"):
            px, py, pz = pivot
            rx, ry, rz = rot
            if s == "l":
                px, ry, rz = -px, -ry, -rz
            par = parent.replace("{s}", s) if parent else parent
            self.part(f"{s}_{base}", par, (px, py, pz), (rx, ry, rz))
            for cid, mat, pos, size, kw in cubes:
                if s == "r":
                    self.cube(f"r_{base}", "r_" + cid, mat, pos, size, **kw)
                else:
                    self.cube(f"l_{base}", "l_" + cid, mat, (-(pos[0] + size[0]), pos[1], pos[2]), size, share="r_" + cid,
                              mirror=True, **{k: v for k, v in kw.items() if k not in ("deco", "glow")})

    # -------------------------------------------------------------------------------------------- UV packing

    def pack(self, max_h=512, attempts=800, seed=7):
        """Shelf-packs every cube that owns its UVs; shared cubes take their owner's. Returns the texture height
        (rows used, rounded up to a multiple of 16)."""
        W = self.width
        by_id = {c["id"]: c for c in self.cubes}
        uniq = [c for c in self.cubes if c["share"] is None]

        def net(c):
            w, h, d = c["size"]
            return 2 * d + 2 * w, d + h

        for c in uniq:
            if net(c)[0] > W:
                sys.exit(f"cube {c['id']} is wider than the texture")
        best = None
        rng = random.Random(seed)
        orders = [lambda c: (-net(c)[1], -net(c)[0]), lambda c: (-net(c)[0], -net(c)[1]), lambda c: (-net(c)[0] * net(c)[1], 0)]
        for attempt in range(attempts):
            if attempt < len(orders):
                order = sorted(uniq, key=orders[attempt])
            else:
                order = sorted(uniq, key=lambda c: -net(c)[1] + rng.uniform(-5, 5))
            # shelves: fill each shelf left to right; a cube goes into the first shelf it fits (height and width)
            shelves = []  # [y, height, x]
            placed = []
            for c in order:
                w, h = net(c)
                for sh in shelves:
                    if sh[1] >= h and sh[2] + w <= W:
                        placed.append((c, sh[2], sh[0]))
                        sh[2] += w
                        break
                else:
                    y = shelves[-1][0] + shelves[-1][1] if shelves else 0
                    shelves.append([y, h, w])
                    placed.append((c, 0, y))
            rows = shelves[-1][0] + shelves[-1][1] if shelves else 0
            if best is None or rows < best[0]:
                best = (rows, placed)
        rows, placed = best
        for c, x, y in placed:
            c["u"], c["v"] = x, y
        for c in self.cubes:
            if c["share"] is not None:
                o = by_id[c["share"]]
                while o["share"] is not None:
                    o = by_id[o["share"]]
                if o["size"] != c["size"]:
                    sys.exit(f"{c['id']} shares UVs with {o['id']} but their sizes differ")
                c["u"], c["v"] = o["u"], o["v"]
        self.height = int(math.ceil(rows / 16.0) * 16)
        if self.height > max_h:
            sys.exit(f"cubes need {rows} rows of texture, only {max_h} allowed")
        return rows

    def owners(self):
        return [c for c in self.cubes if c["share"] is None]

    # -------------------------------------------------------------------------------------------- Java

    def java(self, package, cls, generator, doc, extra=""):
        """The Layout class; `extra` is Java inserted into the class body (generated constants)."""
        children = {}
        for p in self.parts:
            children.setdefault(p["parent"], []).append(p)
        cubes_of = {}
        for c in self.cubes:
            cubes_of.setdefault(c["part"], []).append(c)
        out = []

        def emit(p, parent_var):
            pad = "        "
            var = "p_" + p["name"]
            b = [f"{pad}    CubeListBuilder.create()"]
            mirrored = False
            for c in cubes_of.get(p["name"], []):
                if c["mirror"] != mirrored:
                    b.append(f"{pad}        .mirror({'true' if c['mirror'] else 'false'})")
                    mirrored = c["mirror"]
                w, h, d = c["size"]
                x, y, z = c["pos"]
                b.append(f"{pad}        .texOffs({c['u']}, {c['v']}).addBox({fnum(x)}, {fnum(y)}, {fnum(z)}, {fnum(w)}, {fnum(h)}, {fnum(d)})")
            px, py, pz = p["pivot"]
            rx, ry, rz = p["rot"]
            pose = (f"PartPose.offsetAndRotation({fnum(px)}, {fnum(py)}, {fnum(pz)}, {fnum(rx)}, {fnum(ry)}, {fnum(rz)})"
                    if (rx or ry or rz) else f"PartPose.offset({fnum(px)}, {fnum(py)}, {fnum(pz)})")
            decl = f"PartDefinition {var} = " if children.get(p["name"]) else ""
            out.append(f'{pad}{decl}{parent_var}.addOrReplaceChild("{p["name"]}",')
            out.extend(b)
            out.append(f"{pad}        , {pose});")
            for ch in children.get(p["name"], []):
                emit(ch, var)

        for p in children.get(None, []):
            emit(p, "root")
        body = "\n".join(out)
        return f"""package {package};

import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;

/**
 * {doc} GENERATED by tools/art/{generator} from the same cube table the textures are
 * painted from: edit the table there, never this file.
 */
final class {cls} {{
    private {cls}() {{
    }}
{extra}
    static LayerDefinition create() {{
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
{body}
        return LayerDefinition.create(mesh, {self.width}, {self.height});
    }}
}}
"""


def fnum(v):
    v = round(float(v), 4)
    return f"{v:g}f"


def java_floats(name, values, doc):
    """A generated `static final float[]` constant for a Layout class."""
    nums = ", ".join(fnum(v) for v in values)
    return f"\n    /** {doc} */\n    static final float[] {name} = {{{nums}}};\n"


# ------------------------------------------------------------------------------------------------ painting helpers

FACES = ("top", "bottom", "left", "front", "right", "back")


def face_rects(c):
    u, v = c["u"], c["v"]
    w, h, d = c["size"]
    return {
        "top": (u + d, v, w, d), "bottom": (u + d + w, v, w, d),
        "left": (u, v + d, d, h), "front": (u + d, v + d, w, h),
        "right": (u + d + w, v + d, d, h), "back": (u + 2 * d + w, v + d, w, h),
    }


def _h(x, y, s):
    n = (int(x) * 374761393 + int(y) * 668265263 + int(s) * 2147483647) & 0xFFFFFFFF
    n = ((n ^ (n >> 13)) * 1274126177) & 0xFFFFFFFF
    return ((n ^ (n >> 16)) & 0xFFFF) / 65535.0


def vnoise(x, y, scale, seed):
    fx, fy = x / scale, y / scale
    x0, y0 = math.floor(fx), math.floor(fy)
    tx, ty = fx - x0, fy - y0
    tx, ty = tx * tx * (3 - 2 * tx), ty * ty * (3 - 2 * ty)
    a = _h(x0, y0, seed) * (1 - tx) + _h(x0 + 1, y0, seed) * tx
    b = _h(x0, y0 + 1, seed) * (1 - tx) + _h(x0 + 1, y0 + 1, seed) * tx
    return a * (1 - ty) + b * ty


def mix(a, b, t):
    t = max(0.0, min(1.0, t))
    return tuple(a[i] + (b[i] - a[i]) * t for i in range(3))


def mul(c, k):
    return tuple(max(0, min(255, v * k)) for v in c)


def add(c, k):
    return tuple(max(0, min(255, v + k)) for v in c)


def hexc(s):
    return tuple(int(s[i:i + 2], 16) for i in (0, 2, 4))


def ramp(*hs):
    return [hexc(h) for h in hs]


def pick(r, t):
    t = max(0.0, min(0.9999, t))
    return r[int(t * len(r))]


class Canvas:
    """The texture and its emissive layers (each a separate RGBA image of the same size)."""

    def __init__(self, w, h, layers=("glow",)):
        self.w, self.h = w, h
        self.img = Image.new("RGBA", (w, h), (0, 0, 0, 0))
        self.layers = {name: Image.new("RGBA", (w, h), (0, 0, 0, 0)) for name in layers}

    def put(self, x, y, col, a=255):
        if 0 <= x < self.w and 0 <= y < self.h:
            self.img.putpixel((int(x), int(y)), (int(col[0]), int(col[1]), int(col[2]), a))

    def get(self, x, y):
        return self.img.getpixel((int(x), int(y)))[:3]

    def glow(self, x, y, col, layer="glow", a=255):
        if 0 <= x < self.w and 0 <= y < self.h:
            self.layers[layer].putpixel((int(x), int(y)), (int(col[0]), int(col[1]), int(col[2]), a))


# ------------------------------------------------------------------------------------------------ preview

def _rot(rx, ry, rz):
    cx, sx = math.cos(rx), math.sin(rx)
    cy, sy = math.cos(ry), math.sin(ry)
    cz, sz = math.cos(rz), math.sin(rz)
    Rx = np.array([[1, 0, 0], [0, cx, -sx], [0, sx, cx]])
    Ry = np.array([[cy, 0, sy], [0, 1, 0], [-sy, 0, cy]])
    Rz = np.array([[cz, -sz, 0], [sz, cz, 0], [0, 0, 1]])
    return Rz @ Ry @ Rx  # Quaternionf.rotationZYX(z, y, x), as ModelPart.translateAndRotate


def _mat(t, r, s=(1, 1, 1)):
    m = np.eye(4)
    m[:3, :3] = _rot(*r) @ np.diag(s)
    m[:3, 3] = t
    return m


def part_matrices(model, pose=None):
    """World matrices of every part. pose[name] may hold rx/ry/rz (added to the rest rotation), x/y/z (added to the
    pivot), s (uniform scale) or sx/sy/sz, and visible=False."""
    pose = pose or {}
    mats, vis = {}, {}
    for p in model.parts:
        q = pose.get(p["name"], {})
        px, py, pz = p["pivot"]
        rx, ry, rz = p["rot"]
        t = (px + q.get("x", 0), py + q.get("y", 0), pz + q.get("z", 0))
        r = (rx + q.get("rx", 0), ry + q.get("ry", 0), rz + q.get("rz", 0))
        s = q.get("s", 1)
        sc = (q.get("sx", s), q.get("sy", s), q.get("sz", s))
        parent = mats[p["parent"]] if p["parent"] else np.eye(4)
        mats[p["name"]] = parent @ _mat(t, r, sc)
        vis[p["name"]] = q.get("visible", True) and (vis[p["parent"]] if p["parent"] else True)
    return mats, vis


def _faces(c):
    """(corners in order [U2V1, U1V1, U1V2, U2V2], (U1, V1, U2, V2), model normal) of each face, as ModelPart.Cube."""
    x, y, z = c["pos"]
    w, h, d = c["size"]
    x0, x1 = x, x + w
    if c["mirror"]:
        x0, x1 = x1, x0
    y0, y1, z0, z1 = y, y + h, z, z + d
    v1, v2, v3, v4 = (x0, y0, z0), (x1, y0, z0), (x1, y1, z0), (x0, y1, z0)
    v5, v6, v7, v8 = (x0, y0, z1), (x1, y0, z1), (x1, y1, z1), (x0, y1, z1)
    u, v = c["u"], c["v"]
    U = (u, u + d, u + d + w, u + d + w + w, u + d + w + d, u + d + w + d + w)
    V = (v, v + d, v + d + h)
    m = -1 if c["mirror"] else 1
    return [
        ([v6, v5, v1, v2], (U[1], V[0], U[2], V[1]), (0, -1, 0)),
        ([v3, v4, v8, v7], (U[2], V[1], U[3], V[0]), (0, 1, 0)),
        ([v1, v5, v8, v4], (U[0], V[1], U[1], V[2]), (-m, 0, 0)),
        ([v2, v1, v4, v3], (U[1], V[1], U[2], V[2]), (0, 0, -1)),
        ([v6, v2, v3, v7], (U[2], V[1], U[4], V[2]), (m, 0, 0)),
        ([v5, v6, v7, v8], (U[4], V[1], U[5], V[2]), (0, 0, 1)),
    ]


def check_zfight(model, pose=None, quiet=False):
    """Warns about faces of different cubes that lie in the same plane, face the same way and overlap in the rest pose:
    they flicker in game. Returns the list of (cube a, cube b, face direction)."""
    mats, _ = part_matrices(model, pose)
    faces = []
    for c in model.cubes:
        Mx = mats[c["part"]]
        for corners, _uv, normal in _faces(c):
            n = Mx[:3, :3] @ np.array(normal, dtype=np.float64)
            axis = int(np.argmax(np.abs(n)))
            if abs(n[axis]) < 0.999:
                continue  # only axis-aligned faces are checked
            pts = np.array([(Mx @ np.array([*p, 1.0]))[:3] for p in corners])
            others = [i for i in range(3) if i != axis]
            lo, hi = pts[:, others].min(axis=0), pts[:, others].max(axis=0)
            faces.append((c["id"], axis, np.sign(n[axis]), pts[0, axis], lo, hi))
    found = []
    for i in range(len(faces)):
        a = faces[i]
        for j in range(i + 1, len(faces)):
            b = faces[j]
            if a[0] == b[0] or a[1] != b[1] or a[2] != b[2] or abs(a[3] - b[3]) > 0.01:
                continue
            ov = np.minimum(a[5], b[5]) - np.maximum(a[4], b[4])
            if (ov > 0.01).all():
                found.append((a[0], b[0], ("x", "y", "z")[a[1]] + ("+" if a[2] > 0 else "-")))
    if not quiet:
        for a, b, d in found:
            print(f"  z-fight: {a} / {b} ({d})")
    return found


LIGHT0 = np.array([-0.3, -1.0, -0.6]) / np.linalg.norm([-0.3, -1.0, -0.6])
LIGHT1 = np.array([0.3, -1.0, 0.6]) / np.linalg.norm([0.3, -1.0, 0.6])


def render_view(model, tex, glows=(), pose=None, yaw=-0.55, pitch=0.3, ppx=7, size=(520, 560), light=1.0,
                feet=None, bg=(34, 34, 40), box=None, scale=1.0):
    """Renders the model orthographically. tex: RGBA image; glows: [(RGBA image, strength)] added on top fullbright
    (as RenderTypes.eyes does). feet: screen (x, y) of the model's feet. box: (width, height) of the hitbox in blocks,
    drawn as a wireframe. scale: the renderer's extra scale (about the feet)."""
    W, H = size
    feet = feet or (W / 2, H - 40)
    base = np.asarray(tex.convert("RGBA"), dtype=np.float32)
    glow_arrs = [(np.asarray(g.convert("RGBA"), dtype=np.float32), k) for g, k in glows]
    zbuf = np.full((H, W), np.inf, dtype=np.float32)
    color = np.zeros((H, W, 3), dtype=np.float32)
    color[:] = bg
    mats, vis = part_matrices(model, pose)
    view = _rot(pitch, 0, 0) @ _rot(0, yaw, 0)
    th, tw = base.shape[:2]

    def to_screen(p):
        q = view @ ((np.asarray(p, dtype=np.float64) - np.array([0, 24, 0])) * scale)
        return np.array([feet[0] + q[0] * ppx, feet[1] + q[1] * ppx, q[2]])

    for c in model.cubes:
        if not vis[c["part"]]:
            continue
        M = mats[c["part"]]
        for corners, (U1, V1, U2, V2), normal in _faces(c):
            if U1 == U2 or V1 == V2:
                continue
            pts = [M @ np.array([*p, 1.0]) for p in corners]
            sp = [to_screen(p[:3]) for p in pts]
            n = M[:3, :3] @ np.array(normal, dtype=np.float64)
            n /= np.linalg.norm(n)
            shade = min(1.0, 0.4 + 0.6 * (max(0.0, n @ LIGHT0) + max(0.0, n @ LIGHT1))) * light
            o, a, b = sp[1], sp[0] - sp[1], sp[2] - sp[1]
            det = a[0] * b[1] - a[1] * b[0]
            if abs(det) < 1e-6:
                continue
            xs = [p[0] for p in sp]
            ys = [p[1] for p in sp]
            x0, x1 = max(0, int(math.floor(min(xs)))), min(W, int(math.ceil(max(xs))) + 1)
            y0, y1 = max(0, int(math.floor(min(ys)))), min(H, int(math.ceil(max(ys))) + 1)
            if x0 >= x1 or y0 >= y1:
                continue
            gx, gy = np.meshgrid(np.arange(x0, x1) + 0.5, np.arange(y0, y1) + 0.5)
            dx, dy = gx - o[0], gy - o[1]
            s = (dx * b[1] - dy * b[0]) / det
            t = (a[0] * dy - a[1] * dx) / det
            inside = (s >= 0) & (s < 1) & (t >= 0) & (t < 1)
            if not inside.any():
                continue
            depth = o[2] + s * a[2] + t * b[2]
            tu = U1 + s * (U2 - U1)
            tv = V1 + t * (V2 - V1)
            iu = np.floor(tu - (1e-4 if U2 < U1 else 0)).astype(int)
            iv = np.floor(tv - (1e-4 if V2 < V1 else 0)).astype(int)
            iu = np.clip(iu, min(U1, U2), max(U1, U2) - 1).clip(0, tw - 1)
            iv = np.clip(iv, min(V1, V2), max(V1, V2) - 1).clip(0, th - 1)
            texel = base[iv, iu]
            region = zbuf[y0:y1, x0:x1]
            ok = inside & (texel[..., 3] > 8) & (depth < region)
            if not ok.any():
                continue
            rgb = texel[..., :3] * shade
            for g, k in glow_arrs:
                gt = g[iv, iu]
                rgb = rgb + gt[..., :3] * (gt[..., 3:4] / 255.0) * k
            region[ok] = depth[ok]
            color[y0:y1, x0:x1][ok] = rgb[ok]
    img = Image.fromarray(np.clip(color, 0, 255).astype(np.uint8), "RGB")
    if box:
        bw, bh = box[0] * 16 / 2, box[1] * 16
        corners = [(sx * bw, 24 - sy * bh, sz * bw) for sx in (-1, 1) for sy in (0, 1) for sz in (-1, 1)]
        pts = {}
        for (cx, cy, cz) in corners:
            q = view @ (np.array([cx, cy - 24, cz]))  # hitbox is not scaled by the renderer's model scale
            pts[(cx, cy, cz)] = (feet[0] + q[0] * ppx, feet[1] + q[1] * ppx)
        draw = ImageDraw.Draw(img)
        keys = list(pts)
        for i in range(len(keys)):
            for j in range(i + 1, len(keys)):
                a_, b_ = keys[i], keys[j]
                if sum(1 for k in range(3) if a_[k] != b_[k]) == 1:
                    draw.line([pts[a_], pts[b_]], fill=(90, 200, 90), width=1)
    return img


def sheet(images, labels, path, cols=None):
    cols = cols or len(images)
    rows = (len(images) + cols - 1) // cols
    w, h = images[0].size
    out = Image.new("RGB", (w * cols, (h + 18) * rows), (20, 20, 24))
    draw = ImageDraw.Draw(out)
    for i, (im, label) in enumerate(zip(images, labels)):
        x, y = (i % cols) * w, (i // cols) * (h + 18)
        out.paste(im, (x, y + 18))
        draw.text((x + 6, y + 3), label, fill=(220, 220, 220))
    out.save(path)
    return out
