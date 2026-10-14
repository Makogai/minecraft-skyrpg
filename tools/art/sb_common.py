"""Shared plumbing for generate_shields_bows.py: colour ramps and noise, the item-model (``elements``) builder, and a small
offline renderer that draws such a model the way Minecraft's block-model baker lays it out, so a model can be judged without
starting the game.

Face UVs follow Minecraft: (u1, v1) is the top-left corner of the face as seen from outside it, (u2, v2) the bottom-right,
in 0..16 over the whole texture. Seen from outside, screen-right is -x on the north face, +x on the south face, -z on the east
face, +z on the west face, and +x on the up and down faces.
"""
import json
import math

import numpy as np
from PIL import Image


def hexc(h):
    return tuple(int(h[i:i + 2], 16) for i in (0, 2, 4))


class Ramp:
    """Six colours, darkest first; fractional indexes blend."""

    def __init__(self, *colors):
        assert len(colors) == 6
        self.c = [hexc(c) if isinstance(c, str) else tuple(c) for c in colors]

    def __getitem__(self, t):
        t = max(0.0, min(5.0, float(t)))
        i = int(t)
        f = t - i
        a = self.c[i]
        b = self.c[min(5, i + 1)]
        return tuple(int(round(a[k] + (b[k] - a[k]) * f)) for k in range(3))


# ---------------------------------------------------------------------------------------------------- noise
def _hash(ix, iy, seed):
    n = (ix * 374761393 + iy * 668265263 + seed * 2147483647) & 0xFFFFFFFF
    n = ((n ^ (n >> 13)) * 1274126177) & 0xFFFFFFFF
    return ((n ^ (n >> 16)) & 0xFFFF) / 65535.0


def vnoise(x, y, seed=0):
    """Smooth value noise in 0..1."""
    ix, iy = math.floor(x), math.floor(y)
    fx, fy = x - ix, y - iy
    fx = fx * fx * (3 - 2 * fx)
    fy = fy * fy * (3 - 2 * fy)
    a = _hash(ix, iy, seed)
    b = _hash(ix + 1, iy, seed)
    c = _hash(ix, iy + 1, seed)
    d = _hash(ix + 1, iy + 1, seed)
    return (a + (b - a) * fx) + ((c + (d - c) * fx) - (a + (b - a) * fx)) * fy


def fbm(x, y, seed=0, octaves=3):
    total, amp, norm = 0.0, 1.0, 0.0
    for o in range(octaves):
        total += amp * vnoise(x, y, seed + o * 17)
        norm += amp
        x, y, amp = x * 2.03, y * 2.03, amp * 0.5
    return total / norm


def clamp01(v):
    return max(0.0, min(1.0, v))


def seg_dist(px, py, ax, ay, bx, by):
    """Distance from a point to the segment a-b."""
    dx, dy = bx - ax, by - ay
    l2 = dx * dx + dy * dy
    t = 0.0 if l2 == 0 else clamp01(((px - ax) * dx + (py - ay) * dy) / l2)
    qx, qy = ax + t * dx, ay + t * dy
    return math.hypot(px - qx, py - qy)


def path_dist(px, py, pts):
    return min(seg_dist(px, py, pts[i][0], pts[i][1], pts[i + 1][0], pts[i + 1][1]) for i in range(len(pts) - 1))


def in_poly(x, y, pts):
    inside = False
    j = len(pts) - 1
    for i in range(len(pts)):
        xi, yi = pts[i]
        xj, yj = pts[j]
        if (yi > y) != (yj > y) and x < (xj - xi) * (y - yi) / (yj - yi) + xi:
            inside = not inside
        j = i
    return inside


# ---------------------------------------------------------------------------------------------------- models
FACES = ("north", "south", "east", "west", "up", "down")


class ItemModel:
    """A block-style item model: cuboids (optionally one rotation each) with per-face texture and UVs."""

    def __init__(self):
        self.elements = []
        self.textures = {}
        self.display = {}
        self.gui_light = "side"

    def box(self, frm, to, faces, rotation=None, shade=True):
        """faces: {name: (texture, (u1, v1, u2, v2)) or (texture, uv, emission)}."""
        el = {"from": [round(v, 4) for v in frm], "to": [round(v, 4) for v in to], "faces": {}}
        if not shade:
            el["shade"] = False
        if rotation:
            el["rotation"] = rotation
        for name, spec in faces.items():
            tex, uv = spec[0], spec[1]
            face = {"uv": [round(v, 4) for v in uv], "texture": "#" + tex}
            if len(spec) > 2 and spec[2]:
                face["light_emission"] = spec[2]
            el["faces"][name] = face
        self.elements.append(el)

    def to_json(self):
        out = {"textures": self.textures}
        if self.gui_light != "side":
            out["gui_light"] = self.gui_light
        out["elements"] = self.elements
        out["display"] = self.display
        return out

    def write(self, path):
        with open(path, "w", encoding="utf8", newline="\n") as f:
            json.dump(self.to_json(), f, indent=1)
            f.write("\n")


# ---------------------------------------------------------------------------------------------------- renderer
_SHADE = {"up": 1.0, "down": 0.5, "north": 0.8, "south": 0.8, "east": 0.6, "west": 0.6}


def _face_corners(el, name):
    (x1, y1, z1), (x2, y2, z2) = el["from"], el["to"]
    # top-left, top-right, bottom-right, bottom-left as seen from outside the face
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


def _rot_matrix(rx, ry, rz):
    ax, ay, az = (math.radians(a) for a in (rx, ry, rz))
    mx = np.array([[1, 0, 0], [0, math.cos(ax), -math.sin(ax)], [0, math.sin(ax), math.cos(ax)]])
    my = np.array([[math.cos(ay), 0, math.sin(ay)], [0, 1, 0], [-math.sin(ay), 0, math.cos(ay)]])
    mz = np.array([[math.cos(az), -math.sin(az), 0], [math.sin(az), math.cos(az), 0], [0, 0, 1]])
    return mz @ my @ mx


def render(model, images, rot=(0, 0, 0), size=256, scale=None, bg=(34, 36, 46, 255), shade=True, center=(8, 8, 8), flat_light=False):
    """Draws the model's elements. ``images`` maps texture names (the keys of model.textures) to PIL images. ``rot`` is applied about
    the model centre (x, then y, then z), then it is projected orthographically; the camera looks along -z with +y up."""
    arrays = {k: np.array(v.convert("RGBA")) for k, v in images.items()}
    img = np.zeros((size, size, 4), dtype=np.uint8)
    img[:] = bg
    zbuf = np.full((size, size), -1e9)
    r = _rot_matrix(*rot)
    scale = scale or size / 26.0
    c = np.array(center, dtype=float)
    for el in model.elements:
        for name, face in el["faces"].items():
            tex = arrays.get(face["texture"][1:])
            if tex is None:
                continue
            th, tw = tex.shape[:2]
            pts = [r @ (np.array(p, dtype=float) - c) for p in _face_corners(el, name)]
            u1, v1, u2, v2 = face["uv"]
            uvs = [(u1, v1), (u2, v1), (u2, v2), (u1, v2)]
            nrm = r @ np.array({"north": (0, 0, -1), "south": (0, 0, 1), "east": (1, 0, 0), "west": (-1, 0, 0), "up": (0, 1, 0), "down": (0, -1, 0)}[name], dtype=float)
            if nrm[2] <= 0:
                continue  # back-facing
            lit = 1.0 if (not shade or flat_light) else _SHADE[name] * 0.6 + 0.4 * max(0.0, nrm[2]) + 0.0
            if not shade or flat_light:
                lit = 0.92
            emissive = face.get("light_emission", 0) > 0
            sp = [(size / 2 + p[0] * scale, size / 2 - p[1] * scale, p[2]) for p in pts]
            for tri in ((0, 1, 2), (0, 2, 3)):
                a, b, d = (sp[i] for i in tri)
                ta, tb, td = (uvs[i] for i in tri)
                minx = int(max(0, math.floor(min(a[0], b[0], d[0]))))
                maxx = int(min(size - 1, math.ceil(max(a[0], b[0], d[0]))))
                miny = int(max(0, math.floor(min(a[1], b[1], d[1]))))
                maxy = int(min(size - 1, math.ceil(max(a[1], b[1], d[1]))))
                den = (b[1] - d[1]) * (a[0] - d[0]) + (d[0] - b[0]) * (a[1] - d[1])
                if abs(den) < 1e-9:
                    continue
                for y in range(miny, maxy + 1):
                    for x in range(minx, maxx + 1):
                        px, py = x + 0.5, y + 0.5
                        l1 = ((b[1] - d[1]) * (px - d[0]) + (d[0] - b[0]) * (py - d[1])) / den
                        l2 = ((d[1] - a[1]) * (px - d[0]) + (a[0] - d[0]) * (py - d[1])) / den
                        l3 = 1 - l1 - l2
                        if l1 < -1e-6 or l2 < -1e-6 or l3 < -1e-6:
                            continue
                        z = l1 * a[2] + l2 * b[2] + l3 * d[2]
                        if z < zbuf[y, x]:
                            continue
                        u = l1 * ta[0] + l2 * tb[0] + l3 * td[0]
                        v = l1 * ta[1] + l2 * tb[1] + l3 * td[1]
                        tx = min(tw - 1, max(0, int(u / 16 * tw)))
                        ty = min(th - 1, max(0, int(v / 16 * th)))
                        px_rgba = tex[ty, tx]
                        if px_rgba[3] < 128:
                            continue
                        zbuf[y, x] = z
                        k = 1.0 if emissive else lit
                        img[y, x] = (int(px_rgba[0] * k), int(px_rgba[1] * k), int(px_rgba[2] * k), 255)
    return Image.fromarray(img, "RGBA")
