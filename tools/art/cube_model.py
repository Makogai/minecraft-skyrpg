"""Shared plumbing for the cube-model generators of our world mobs (generate_sporeling.py, generate_fire_beetle.py).

The slayer boss generators (generate_broodmother.py and friends) each carry their own copy of this; the newer, smaller
mobs share it instead. What is here:

* ``CubeModel``: the cube table (parts with pivots and rotations, cubes with materials), UV packing with Minecraft's
  standard box layout, and the Java ``*Layout`` class that builds the LayerDefinition from the same table, so the
  cubes and their UVs cannot drift apart.
* Painting helpers: value noise, colour ramps, the face rectangles of a cube's UV net.
* ``render``: a small offline renderer (orthographic, textured, z-buffered, glow layer on top) that draws the model
  the way Minecraft's ModelPart does, for preview sheets. Pose overrides let a preview show animation poses.

Box layout (as ModelPart.Cube builds it): for a cube at texOffs (u, v) with size (w, h, d) the net is
top (u+d, v, w, d), bottom (u+d+w, v, w, d), right side (u, v+d, d, h), front (u+d, v+d, w, h),
left side (u+d+w, v+d, d, h), back (u+2d+w, v+d, w, h). Model space: y grows downwards, the ground is y 24,
-z is the front, -x is the mob's right.
"""
import math
import random
import sys

import numpy as np
from PIL import Image, ImageDraw


# ------------------------------------------------------------------------------------------------ cube table
class CubeModel:
    def __init__(self, name):
        self.name = name
        self.parts = []
        self.cubes = []
        self.width = self.height = 0

    def part(self, name, parent, pivot, rot=(0, 0, 0)):
        if any(p["name"] == name for p in self.parts):
            sys.exit(f"duplicate part {name}")
        self.parts.append(dict(name=name, parent=parent, pivot=tuple(pivot), rot=tuple(rot)))

    def cube(self, pname, cid, mat, pos, size, share=None, mirror=False, **kw):
        if any(c["id"] == cid for c in self.cubes):
            sys.exit(f"duplicate cube {cid}")
        self.cubes.append(dict(part=pname, id=cid, mat=mat, pos=tuple(pos), size=tuple(size), share=share, mirror=mirror, **kw))

    # -------------------------------------------------------------------------------------------- UV packing
    @staticmethod
    def net(c):
        w, h, d = (int(math.ceil(n)) for n in c["size"])
        return 2 * d + 2 * w, d + h

    def pack(self, sizes=(64, 128, 256)):
        """Shelf-packs every cube that owns its UVs into the smallest square texture that fits. Cubes with the same
        ``sk`` (share key) and size share one region; ``share`` names a cube to copy UVs from."""
        owners = {}
        for c in self.cubes:
            key = c.get("sk")
            if c["share"] is None and key is not None:
                if (key, c["size"]) in owners:
                    c["share"] = owners[(key, c["size"])]
                else:
                    owners[(key, c["size"])] = c["id"]
        by_id = {c["id"]: c for c in self.cubes}
        uniq = [c for c in self.cubes if c["share"] is None]
        for size in sizes:
            best = None
            rng = random.Random(7)
            net = self.net
            orders = [lambda c: (-net(c)[1], -net(c)[0]), lambda c: (-net(c)[0], -net(c)[1]), lambda c: (-net(c)[0] * net(c)[1], 0)]
            for attempt in range(400):
                order = sorted(uniq, key=orders[attempt]) if attempt < len(orders) else sorted(uniq, key=lambda c: -net(c)[1] + rng.uniform(-4, 4))
                x = y = shelf = 0
                placed = []
                ok = True
                for c in order:
                    w, h = net(c)
                    if w > size:
                        ok = False
                        break
                    if x + w > size:
                        x, y, shelf = 0, y + shelf, 0
                    placed.append((c, x, y))
                    x += w
                    shelf = max(shelf, h)
                rows = y + shelf
                if ok and (best is None or rows < best[0]):
                    best = (rows, placed)
            if best is not None and best[0] <= size:
                rows, placed = best
                for c, x, y in placed:
                    c["u"], c["v"] = x, y
                self.width = self.height = size
                for c in self.cubes:
                    if c["share"] is not None:
                        o = by_id[c["share"]]
                        while o["share"] is not None:
                            o = by_id[o["share"]]
                        if o["size"] != c["size"]:
                            sys.exit(f"{c['id']} shares UVs with {o['id']} but their sizes differ")
                        c["u"], c["v"] = o["u"], o["v"]
                return rows
        sys.exit(f"{self.name}: the cubes do not fit a {sizes[-1]} texture")

    def owners(self):
        """Cubes that own their UV region (shared regions are painted once, by their owner)."""
        return [c for c in self.cubes if c["share"] is None]

    # -------------------------------------------------------------------------------------------- Java
    def java(self, package, class_name, generator, what, extra=""):
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
            b = [f"{pad}CubeListBuilder.create()"]
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
            used = bool(children.get(p["name"]))
            decl = f"PartDefinition {var} = " if used else ""
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
 * {what}'s cubes and their UVs. GENERATED by {generator} from the same cube table
 * the textures are painted from: edit the table there, never this file.
 */
final class {class_name} {{
{extra}    private {class_name}() {{
    }}

    static LayerDefinition create() {{
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
{body}
        return LayerDefinition.create(mesh, {self.width}, {self.height});
    }}
}}
"""


def fnum(v):
    v = round(float(v), 5)
    return f"{v:g}f"


# ------------------------------------------------------------------------------------------------ painting helpers
def _h(x, y, s):
    n = (x * 374761393 + y * 668265263 + s * 2147483647) & 0xFFFFFFFF
    n = ((n ^ (n >> 13)) * 1274126177) & 0xFFFFFFFF
    return ((n ^ (n >> 16)) & 0xFFFF) / 65535.0


def hnoise(x, y, s):
    return _h(int(x), int(y), int(s))


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


def hexc(s):
    return tuple(int(s[i:i + 2], 16) for i in (0, 2, 4))


def ramp(*hexes):
    return [hexc(h) for h in hexes]


def pick(r, t):
    t = max(0.0, min(0.9999, t))
    return r[int(t * len(r))]


def face_rects(c):
    """The six faces of a cube's UV net as (x, y, w, h). In "top" and "bottom" the row nearest y + h is the front
    edge; in "right" (the -x side) the front is the right-hand column, in "left" (+x) the left-hand column."""
    u, v = c["u"], c["v"]
    w, h, d = (int(math.ceil(n)) for n in c["size"])
    return {
        "top": (u + d, v, w, d), "bottom": (u + d + w, v, w, d),
        "right": (u, v + d, d, h), "front": (u + d, v + d, w, h),
        "left": (u + d + w, v + d, d, h), "back": (u + 2 * d + w, v + d, w, h),
    }


def put(img, x, y, col, a=255):
    if 0 <= x < img.width and 0 <= y < img.height:
        img.putpixel((int(x), int(y)), (int(col[0]), int(col[1]), int(col[2]), int(a)))


def get(img, x, y):
    return img.getpixel((int(x), int(y)))[:3]


# ------------------------------------------------------------------------------------------------ spawn eggs
def write_spawn_egg(root, mob, shell, spots):
    """Writes skyrpg_combat:<mob>_spawn_egg's texture, item model and item definition, in the style of the other
    world mob eggs (vanilla's egg shape from generate_combat.py, a shell ramp and a spots ramp of five hex colours)."""
    import json
    import generate_combat as gc
    import generate_items as gi
    name = f"{mob}_spawn_egg"
    assets = root / "combat/src/main/resources/assets/skyrpg_combat"
    img = gi.render(gc.EGG, {"B": gi.Material(shell), "S": gi.Material(spots, base=3)})
    (assets / "textures/item").mkdir(parents=True, exist_ok=True)
    img.save(assets / "textures/item" / f"{name}.png")
    for path, obj in ((assets / "models/item" / f"{name}.json", {"parent": "minecraft:item/generated", "textures": {"layer0": f"skyrpg_combat:item/{name}"}}),
                      (assets / "items" / f"{name}.json", {"model": {"type": "minecraft:model", "model": f"skyrpg_combat:item/{name}"}})):
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(json.dumps(obj, indent=2) + "\n", encoding="utf-8", newline="\n")


# ------------------------------------------------------------------------------------------------ preview renderer
def _rot(rx, ry, rz):
    cx, sx = math.cos(rx), math.sin(rx)
    cy, sy = math.cos(ry), math.sin(ry)
    cz, sz = math.cos(rz), math.sin(rz)
    Rx = np.array([[1, 0, 0], [0, cx, -sx], [0, sx, cx]])
    Ry = np.array([[cy, 0, sy], [0, 1, 0], [-sy, 0, cy]])
    Rz = np.array([[cz, -sz, 0], [sz, cz, 0], [0, 0, 1]])
    return Rz @ Ry @ Rx  # Quaternionf.rotationZYX(z, y, x)


def part_matrices(model, pose=None):
    """World 4x4 matrix of every part. ``pose`` maps a part name to a dict with optional ``rot`` (added to the
    part's rotation), ``off`` (added to its pivot) and ``scale``."""
    pose = pose or {}
    parts = {p["name"]: p for p in model.parts}
    cache = {}

    def mat(name):
        if name is None:
            return np.eye(4)
        if name in cache:
            return cache[name]
        p = parts[name]
        o = pose.get(name, {})
        piv = np.add(p["pivot"], o.get("off", (0, 0, 0)))
        rot = np.add(p["rot"], o.get("rot", (0, 0, 0)))
        sc = o.get("scale", (1, 1, 1))
        if not isinstance(sc, (tuple, list)):
            sc = (sc, sc, sc)
        m = np.eye(4)
        m[:3, 3] = piv
        r = np.eye(4)
        r[:3, :3] = _rot(*rot)
        s = np.diag([sc[0], sc[1], sc[2], 1.0])
        cache[name] = mat(p["parent"]) @ m @ r @ s
        return cache[name]

    return {p["name"]: mat(p["name"]) for p in model.parts}


def _faces(c):
    """The cube's six quads as (direction, [4 corners], [4 uvs]) following ModelPart.Cube exactly."""
    x0, y0, z0 = c["pos"]
    w, h, d = c["size"]
    x1, y1, z1 = x0 + w, y0 + h, z0 + d
    if c["mirror"]:
        x0, x1 = x1, x0
    t0, t1, t2, t3 = (x0, y0, z0), (x1, y0, z0), (x1, y1, z0), (x0, y1, z0)
    l0, l1, l2, l3 = (x0, y0, z1), (x1, y0, z1), (x1, y1, z1), (x0, y1, z1)
    u, v = c["u"], c["v"]
    W, H, D = (int(math.ceil(n)) for n in c["size"])
    u0, u1, u2, u22, u3, u4 = u, u + D, u + D + W, u + D + 2 * W, u + 2 * D + W, u + 2 * D + 2 * W
    v0, v1, v2 = v, v + D, v + D + H

    def poly(verts, a, b, c_, d_):
        # vertices[0] -> (c_, b), [1] -> (a, b), [2] -> (a, d_), [3] -> (c_, d_)   (u0,v0,u1,v1 = a,b,c_,d_)
        return verts, [(c_, b), (a, b), (a, d_), (c_, d_)]

    out = []
    for name, verts, args in (
            ("down", [l1, l0, t0, t1], (u1, v0, u2, v1)),
            ("up", [t2, t3, l3, l2], (u2, v1, u22, v0)),
            ("west", [t0, l0, l3, t3], (u0, v1, u1, v2)),
            ("north", [t1, t0, t3, t2], (u1, v1, u2, v2)),
            ("east", [l1, t1, t2, l2], (u2, v1, u3, v2)),
            ("south", [l0, l1, l2, l3], (u3, v1, u4, v2))):
        vs, uvs = poly(verts, *args)
        rect = (min(a[0] for a in uvs), min(a[1] for a in uvs), max(a[0] for a in uvs), max(a[1] for a in uvs))
        out.append((name, vs, uvs, rect))
    return out


def render(model, tex, glow, pose=None, hide=(), yaw=0.6, pitch=0.42, scale=8.0, size=(480, 400), center=(0.5, 0.62),
           ambient=1.0, glow_on=True, bg=(46, 50, 60), ground=True, look_at=(0, 18, 0)):
    """Draws the model. ``yaw`` turns the model (0 = facing the viewer), ``pitch`` tilts it so its top shows."""
    W, H = size
    T = np.asarray(tex.convert("RGBA"), dtype=np.float32)
    G = np.asarray(glow.convert("RGBA"), dtype=np.float32) if glow is not None else None
    color = np.zeros((H, W, 3), np.float32)
    color[:] = bg
    zbuf = np.full((H, W), np.inf, np.float32)
    gbuf = np.zeros((H, W, 4), np.float32)
    mats = part_matrices(model, pose)
    view = _rot(pitch, 0, 0) @ _rot(0, yaw, 0)
    cx, cy = W * center[0], H * center[1]
    look = view @ np.array(look_at, float)
    light = np.array([0.25, -1.0, -0.55])
    light /= np.linalg.norm(light)
    hidden = set(hide)

    def proj(p):
        q = view @ p
        return np.array([(q[0] - look[0]) * scale + cx, (q[1] - look[1]) * scale + cy, q[2]])

    if ground:
        # a ground grid at y 24, one line per block
        gimg = Image.fromarray(color.astype(np.uint8))
        dr = ImageDraw.Draw(gimg)
        for k in range(-3, 4):
            for a, b in (((k * 16, 24, -48), (k * 16, 24, 48)), ((-48, 24, k * 16), (48, 24, k * 16))):
                pa, pb = proj(np.array(a, float)), proj(np.array(b, float))
                dr.line([(pa[0], pa[1]), (pb[0], pb[1])], fill=(62, 68, 80), width=1)
        color = np.asarray(gimg, dtype=np.float32).copy()

    for c in model.cubes:
        if c["part"] in hidden or c.get("hidden"):
            continue
        M = mats[c["part"]]
        x0, y0, z0 = c["pos"]
        w, h, d = c["size"]
        centre = (M @ np.array([x0 + w / 2, y0 + h / 2, z0 + d / 2, 1.0]))[:3]
        for name, verts, uvs, rect in _faces(c):
            P3 = [(M @ np.array([*vv, 1.0]))[:3] for vv in verts]
            n = np.mean(P3, axis=0) - centre
            nl = np.linalg.norm(n)
            if nl < 1e-9:
                continue
            nn = n / nl
            S = [proj(p) for p in P3]
            # Minecraft-ish entity light: brighter on top and in front, darker below
            shade = 0.55 + 0.45 * max(0.0, float(np.dot(nn, light)))
            shade = min(1.0, max(0.35, shade))
            for tri in ((0, 1, 2), (0, 2, 3)):
                _raster(S, uvs, rect, tri, T, G, color, zbuf, gbuf, shade * ambient)
    out = color
    if glow_on and G is not None:
        a = gbuf[..., 3:4] / 255.0
        out = color * (1 - a) + gbuf[..., :3] * a
    return Image.fromarray(np.clip(out, 0, 255).astype(np.uint8))


def _raster(S, uvs, rect, tri, T, G, color, zbuf, gbuf, shade):
    H, W = zbuf.shape
    a, b, c = (S[i] for i in tri)
    ua, ub, uc = (np.array(uvs[i], float) for i in tri)
    xmin = max(0, int(math.floor(min(a[0], b[0], c[0]))))
    xmax = min(W - 1, int(math.ceil(max(a[0], b[0], c[0]))))
    ymin = max(0, int(math.floor(min(a[1], b[1], c[1]))))
    ymax = min(H - 1, int(math.ceil(max(a[1], b[1], c[1]))))
    if xmin > xmax or ymin > ymax:
        return
    den = (b[1] - c[1]) * (a[0] - c[0]) + (c[0] - b[0]) * (a[1] - c[1])
    if abs(den) < 1e-9:
        return
    xs, ys = np.meshgrid(np.arange(xmin, xmax + 1) + 0.5, np.arange(ymin, ymax + 1) + 0.5)
    w0 = ((b[1] - c[1]) * (xs - c[0]) + (c[0] - b[0]) * (ys - c[1])) / den
    w1 = ((c[1] - a[1]) * (xs - c[0]) + (a[0] - c[0]) * (ys - c[1])) / den
    w2 = 1 - w0 - w1
    eps = -1e-4
    inside = (w0 >= eps) & (w1 >= eps) & (w2 >= eps)
    if not inside.any():
        return
    z = w0 * a[2] + w1 * b[2] + w2 * c[2]
    u = w0 * ua[0] + w1 * ub[0] + w2 * uc[0]
    v = w0 * ua[1] + w1 * ub[1] + w2 * uc[1]
    ui = np.clip(np.floor(u), rect[0], rect[2] - 1).astype(int)
    vi = np.clip(np.floor(v), rect[1], rect[3] - 1).astype(int)
    ui = np.clip(ui, 0, T.shape[1] - 1)
    vi = np.clip(vi, 0, T.shape[0] - 1)
    texel = T[vi, ui]
    opaque = texel[..., 3] > 25
    zb = zbuf[ymin:ymax + 1, xmin:xmax + 1]
    m = inside & opaque & (z < zb)
    if not m.any():
        return
    zb[m] = z[m]
    col = color[ymin:ymax + 1, xmin:xmax + 1]
    col[m] = texel[..., :3][m] * shade
    gb = gbuf[ymin:ymax + 1, xmin:xmax + 1]
    if G is not None:
        gt = G[vi, ui]
        gb[m] = gt[m]
    else:
        gb[m] = 0


def sheet(images, labels=None, bg=(30, 32, 40), pad=8):
    """Lays images out in a row with optional labels underneath."""
    labels = labels or [""] * len(images)
    w = sum(i.width for i in images) + pad * (len(images) + 1)
    h = max(i.height for i in images) + pad * 2 + (16 if any(labels) else 0)
    out = Image.new("RGB", (w, h), bg)
    d = ImageDraw.Draw(out)
    x = pad
    for im, lab in zip(images, labels):
        out.paste(im.convert("RGB"), (x, pad))
        if lab:
            d.text((x + 2, pad + im.height + 2), lab, fill=(200, 205, 215))
        x += im.width + pad
    return out


def stack(rows, bg=(30, 32, 40)):
    w = max(r.width for r in rows)
    h = sum(r.height for r in rows)
    out = Image.new("RGB", (w, h), bg)
    y = 0
    for r in rows:
        out.paste(r, (0, y))
        y += r.height
    return out


def texture_sheet(tex, glow, zoom=4, bg=(60, 60, 70)):
    """The texture and its glow layer side by side, zoomed, on a checker so empty pixels show."""
    w, h = tex.size
    out = Image.new("RGB", (w * zoom * 2 + 24, h * zoom + 16), (30, 32, 40))
    for k, im in enumerate((tex, glow)):
        base = Image.new("RGBA", (w, h), bg + (255,))
        for y in range(h):
            for x in range(w):
                if (x // 4 + y // 4) % 2:
                    base.putpixel((x, y), (72, 72, 84, 255))
        base.alpha_composite(im)
        out.paste(base.resize((w * zoom, h * zoom), Image.NEAREST).convert("RGB"), (8 + k * (w * zoom + 8), 8))
    return out
