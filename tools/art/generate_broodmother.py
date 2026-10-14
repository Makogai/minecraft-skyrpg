#!/usr/bin/env python3
"""Draws the Broodmother slayer boss's model textures (one per tier) and writes its model definition.

    python tools/art/generate_broodmother.py

Same architecture as generate_tyrant.py: the CUBES table below is the single source of truth. It lists every cube of the
model (part, position, size, material), packs them into a 256x256 texture with Minecraft's standard box layout, paints
that texture once per tier, and writes the Java that builds the LayerDefinition (BroodmotherLayout.java), so the cubes
and their UVs cannot drift apart. Outputs:

    combat/src/main/resources/assets/skyrpg_combat/textures/entity/slayer/broodmother_model_t{1..4}.png
    ... broodmother_model_t{1..4}_glow.png   (only the glowing pixels; drawn fullbright by BroodmotherBossRenderer)
    combat/src/client/java/com/skyrpg/combat/client/slayer/BroodmotherLayout.java

Parts that only exist from a higher tier (plates, ridge, back eggs, silk, crown, drips, knee pads) are painted in every
texture but hidden by BroodmotherModel below their tier. Box layout as in generate_tyrant.py (model -z is the front).
"""
import math
import random
import sys
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parents[2]
TEX_DIR = ROOT / "combat/src/main/resources/assets/skyrpg_combat/textures/entity/slayer"
JAVA_OUT = ROOT / "combat/src/client/java/com/skyrpg/combat/client/slayer/BroodmotherLayout.java"
SIZE = 256

# ------------------------------------------------------------------------------------------------ cube table
PARTS = []
CUBES = []


def part(name, parent, pivot, rot=(0, 0, 0)):
    PARTS.append(dict(name=name, parent=parent, pivot=pivot, rot=rot))


def cube(pname, cid, mat, pos, size, share=None, mirror=False, **kw):
    CUBES.append(dict(part=pname, id=cid, mat=mat, pos=pos, size=size, share=share, mirror=mirror, **kw))


# ---- body: low wide cephalothorax
part("body", None, (0, 14, 0))
cube("body", "thorax", "chitin", (-6, -3, -5), (12, 6, 10), deco="thorax")
cube("body", "hump", "chitin_dark", (-4, -5, -4), (8, 2, 8), deco="hump")
# bristly hair tufts on the thorax
for i, (hx, hz) in enumerate([(-5, -3), (4, -2), (-5, 2), (4, 3), (-1, 4)]):
    cube("body", f"hair_t{i}", "hair", (hx, -6, hz), (1, 3, 1), sk="hair3")

# T2+: chitin armour plates over the back
part("plates", "body", (0, 0, 0))
cube("plates", "pl_a", "plate", (-6, -6, -6), (12, 2, 4), deco="plate", sk="plate12")
cube("plates", "pl_b", "plate", (-6, -6, -1), (12, 2, 4), deco="plate", sk="plate12")
cube("plates", "pl_c", "plate", (-5, -5, 3), (10, 2, 3), deco="plate")

# T3+: spiked carapace ridge on the thorax
part("ridge", "body", (0, 0, 0))
for i, (rz, rh) in enumerate([(-3, 4), (1, 5)]):
    cube("ridge", f"rspk{i}_b", "horn", (-1.5, -6 - 2, rz), (3, 2, 3), sk="rbase")
    cube("ridge", f"rspk{i}_t", "horn", (-0.5, -8 - rh, rz + 1), (1, rh, 1), sk=f"rtip{rh}")

# ---- head with the eye cluster, mandibles and fangs
part("head", "body", (0, 1, -5))
cube("head", "head", "chitin", (-4, -3, -5), (8, 6, 5), deco="head")
cube("head", "brow", "chitin_dark", (-4, -4, -5), (8, 1, 3))
cube("head", "head_hair0", "hair", (-4, -6, -4), (1, 3, 1), share="hair_t0")
cube("head", "head_hair1", "hair", (3, -6, -4), (1, 3, 1), share="hair_t0")
for side, sx, sg in (("r", -2.5, 1), ("l", 2.5, -1)):
    mir = side == "l"
    sh = lambda cid: (cid.replace("_l", "_r") if mir else None)
    part(f"mand_{side}", "head", (sx, 1.5, -5))
    cube(f"mand_{side}", f"mand_{side}", "chitin_dark", (-1.5, -1.5, -3), (3, 3, 3), share="mand_r" if mir else None, mirror=mir)
    part(f"fang_{side}", f"mand_{side}", (0, 0.5, -1.5))
    cube(f"fang_{side}", f"fang_{side}", "fang", (-1, 0, -1), (2, 5, 2), share="fang_r" if mir else None, mirror=mir, deco="fang")
    part(f"tip_{side}", f"fang_{side}", (0, 5, 0), rot=(0, 0, 0.7 * sg * -1))
    cube(f"tip_{side}", f"tip_{side}", "fang", (-0.5, 0, -0.5), (1, 4, 1), share="tip_r" if mir else None, mirror=mir, deco="fang")
    part(f"drip_{side}", f"tip_{side}", (0, 4, 0))
    cube(f"drip_{side}", f"drip_{side}", "venom", (-0.5, 0, -0.5), (1, 3, 1), share="drip_r" if mir else None, mirror=mir, glow="all")
# T4: a crown of horn spikes
part("crown", "head", (0, -3, 0))
cube("crown", "crown_ring", "horn", (-4, -1, -5), (8, 1, 5), deco="crown")
for i, (cx, ch, cz) in enumerate([(-3.5, 4, -4), (-1.5, 6, -4.5), (0.5, 6, -4.5), (2.5, 4, -4)]):
    cube("crown", f"crown_b{i}", "horn", (cx, -1 - ch, cz), (1, ch, 1), sk=f"crown{ch}")
cube("crown", "crown_c", "horn", (-0.5, -9, -4.5), (1, 2, 1), sk="crowntop")

# ---- abdomen: the swollen egg sac
part("abdomen", "body", (0, -0.5, 5))
cube("abdomen", "stalk", "chitin_dark", (-2, -1.5, -1), (4, 3, 2))
part("sac", "abdomen", (0, 1, 8))
cube("sac", "sac", "sac", (-8, -7, -7), (16, 13, 14), deco="sac")
cube("sac", "spinneret", "chitin_dark", (-2, -2, 7), (4, 3, 3))
cube("sac", "sac_hair0", "hair", (-8.5, -3, -3), (1, 3, 1), share="hair_t0")
cube("sac", "sac_hair1", "hair", (7.5, -3, 1), (1, 3, 1), share="hair_t0")
cube("sac", "sac_hair2", "hair", (6.5, 3, -5), (1, 3, 1), share="hair_t0")
part("sac_plates", "sac", (0, 0, 0))
cube("sac_plates", "sp_a", "plate", (-6, -8, -6), (12, 1, 4), share="pl_a_s", deco="plate")
cube("sac_plates", "sp_b", "plate", (-6, -8, -1), (12, 1, 4), share="pl_a_s", deco="plate")
cube("sac_plates", "sp_c", "plate", (-5, -8, 4), (10, 1, 3), deco="plate")
part("sac_ridge", "sac", (0, 0, 0))
for i, (rz, rh) in enumerate([(-5, 4), (-1, 5), (3, 4), (6, 3)]):
    cube("sac_ridge", f"sspk{i}_b", "horn", (-1.5, -10, rz), (3, 2, 3), sk="rbase")
    cube("sac_ridge", f"sspk{i}_t", "horn", (-0.5, -10 - rh, rz + 1), (1, rh, 1), sk=f"rtip{rh}")
part("back_eggs", "sac", (0, 0, 0))
for i, (ex, ey, ez, es) in enumerate([(-7, -11, -3, 4), (3, -12, 0, 5), (-6, -10, 5, 4), (-10, -4, -5, 3), (7, -3, -4, 3)]):
    cube("back_eggs", f"egg{i}", "eggsac", (ex, ey, ez), (es, es, es), sk=f"eggs{es}", deco="eggsac")
for i, (sx, sy, sz) in enumerate([(-6, 6, -3), (5, 6, 0), (-3, 6, 6), (7, 3, 4)]):
    part(f"silk{i}", "sac", (sx, sy, sz))
    cube(f"silk{i}", f"silk{i}_a", "silk", (-0.5, 0, -0.5), (1, 9, 1), sk="silk9")
    cube(f"silk{i}", f"silk{i}_b", "silk", (-0.5, 9, -0.5), (1, 5, 1), sk="silk5")

# ---- eight jointed legs: yaw root > femur > tibia > tarsus, with knee pads (T2+)
LEG_Z = (-4, -1.5, 1.5, 4)
LEG_YAW = (-0.85, -0.3, 0.3, 0.85)
for side, sg in (("r", 1), ("l", -1)):
    mir = side == "l"
    for i in range(4):
        n = f"{side}{i}"
        sh = (lambda base: base if (mir or i) else None)
        part(f"leg_{n}", "body", (-5.5 * sg, 0.5, LEG_Z[i]), rot=(0, LEG_YAW[i] * sg, 0))
        part(f"leg_{n}_a", f"leg_{n}", (0, 0, 0), rot=(0, 0, 0.6 * sg))
        x0 = -8 if not mir else 0
        cube(f"leg_{n}_a", f"{n}_a", "leg", (x0, -1.5, -1.5), (8, 3, 3), share="r0_a" if (mir or i) else None, mirror=mir, deco="leg")
        part(f"leg_{n}_b", f"leg_{n}_a", (-8 * sg, 0, 0), rot=(0, 0, -1.5 * sg))
        x0 = -10 if not mir else 0
        cube(f"leg_{n}_b", f"{n}_b", "leg", (x0, -1, -1), (10, 2, 2), share="r0_b" if (mir or i) else None, mirror=mir, deco="leg")
        part(f"leg_{n}_c", f"leg_{n}_b", (-10 * sg, 0, 0), rot=(0, 0, -0.5 * sg))
        x0 = -7 if not mir else 0
        cube(f"leg_{n}_c", f"{n}_c", "leg", (x0, -0.5, -0.5), (7, 1, 1), share="r0_c" if (mir or i) else None, mirror=mir, deco="leg")
        part(f"pad_{n}_1", f"leg_{n}_a", (-8 * sg, 0, 0))
        cube(f"pad_{n}_1", f"{n}_p1", "plate", (-2, -2, -2), (4, 4, 4), share="r0_p1" if (mir or i) else None, mirror=mir, deco="pad")
        part(f"pad_{n}_2", f"leg_{n}_b", (-10 * sg, 0, 0))
        cube(f"pad_{n}_2", f"{n}_p2", "plate", (-1.5, -1.5, -1.5), (3, 3, 3), share="r0_p2" if (mir or i) else None, mirror=mir, deco="pad")

# ---- cocoon shell (COCOONED): a silk cocoon wrapped around the whole spider
part("cocoon", None, (0, 13, 2))
cube("cocoon", "co_mid", "cocoon", (-10, -12, -15), (20, 22, 30), deco="cocoon")
cube("cocoon", "co_front", "cocoon", (-8, -10, -18), (16, 18, 3), deco="cocoon")
cube("cocoon", "co_back", "cocoon", (-8, -10, 15), (16, 18, 4), deco="cocoon")
cube("cocoon", "co_top", "cocoon", (-6, -14, -10), (12, 2, 20), deco="cocoon")
for i, bz in enumerate([-9, -3, 3, 9]):
    cube("cocoon", f"co_band{i}", "cocoon_band", (-10.5, -12.5, bz), (21, 23, 1), sk="coband")

# ---- spiderling ring (SHIELDED)
part("shield", None, (0, 11, 2))
for i in range(6):
    a = i * math.pi / 3
    part(f"sl{i}", "shield", (round(17 * math.cos(a), 2), 0, round(17 * math.sin(a), 2)), rot=(0, -a, 0))
    cube(f"sl{i}", f"sl{i}_abd", "spiderling", (-2, -2, 0), (4, 4, 4), sk="sl_abd", deco="spiderling")
    cube(f"sl{i}", f"sl{i}_head", "spiderling", (-1.5, -1.5, -3), (3, 3, 3), sk="sl_head", deco="spiderling")
    cube(f"sl{i}", f"sl{i}_legs0", "spiderling", (-5, 0.5, -2), (10, 1, 1), sk="sl_legs")
    cube(f"sl{i}", f"sl{i}_legs1", "spiderling", (-5, 0.5, 0), (10, 1, 1), sk="sl_legs")
    cube(f"sl{i}", f"sl{i}_legs2", "spiderling", (-5, 0.5, 2), (10, 1, 1), sk="sl_legs")

# "pl_a_s" share hack: sac plates are 12x1x4 while pl_a is 12x2x4; they get their own UVs instead
for c in CUBES:
    if c["share"] == "pl_a_s":
        c["share"] = "sp_a" if c["id"] != "sp_a" else None


# ------------------------------------------------------------------------------------------------ UV packing
def net(c):
    w, h, d = c["size"]
    return int(2 * d + 2 * w), int(d + h)


def pack():
    owners = {}
    for c in CUBES:
        key = c.get("sk")
        if c["share"] is None and key is not None:
            if (key, c["size"]) in owners:
                c["share"] = owners[(key, c["size"])]
            else:
                owners[(key, c["size"])] = c["id"]
    by_id = {c["id"]: c for c in CUBES}
    uniq = [c for c in CUBES if c["share"] is None]
    best = None
    rng = random.Random(7)
    orders = [lambda c: (-net(c)[1], -net(c)[0]), lambda c: (-net(c)[0], -net(c)[1]), lambda c: (-net(c)[0] * net(c)[1], 0)]
    for attempt in range(600):
        order = sorted(uniq, key=orders[attempt]) if attempt < len(orders) else sorted(uniq, key=lambda c: -net(c)[1] + rng.uniform(-4, 4))
        x = y = shelf = 0
        placed = []
        for c in order:
            w, h = net(c)
            if x + w > SIZE:
                x, y, shelf = 0, y + shelf, 0
            placed.append((c, x, y))
            x += w
            shelf = max(shelf, h)
        rows = y + shelf
        if best is None or rows < best[0]:
            best = (rows, placed)
    rows, placed = best
    for c, x, y in placed:
        c["u"], c["v"] = x, y
    if rows > SIZE:
        sys.exit(f"cubes need {rows} rows of texture, only {SIZE} available")
    for c in CUBES:
        if c["share"] is not None:
            o = by_id[c["share"]]
            while o["share"] is not None:
                o = by_id[o["share"]]
            if o["size"] != c["size"]:
                sys.exit(f"{c['id']} shares UVs with {o['id']} but their sizes differ")
            c["u"], c["v"] = o["u"], o["v"]
    return rows


# ------------------------------------------------------------------------------------------------ noise
def _h(x, y, s):
    n = (x * 374761393 + y * 668265263 + s * 2147483647) & 0xFFFFFFFF
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


def hexc(s):
    return tuple(int(s[i:i + 2], 16) for i in (0, 2, 4))


def ramp(*hexes):
    return [hexc(h) for h in hexes]


def pick(r, t):
    t = max(0.0, min(0.9999, t))
    return r[int(t * len(r))]


# ------------------------------------------------------------------------------------------------ palettes
class Palette:
    def __init__(self, tier):
        self.t = tier
        self.chitin = {1: ramp("1c1612", "2a2018", "3a2d22", "4c3c2e"), 2: ramp("1c1615", "2c2220", "3e312d", "524039"),
                       3: ramp("1c1620", "2c2232", "3e3246", "514460"), 4: ramp("14101a", "201a2a", "30283c", "42384e")}[tier]
        self.plate = {1: ramp("3c3a34", "58544a", "746e60", "908874"), 2: ramp("3c3a34", "58544a", "746e60", "908874"),
                      3: ramp("28382e", "3e5242", "566e5a", "728c74"), 4: ramp("a8a088", "c4bda0", "ddd5b8", "f0e8cc")}[tier]
        self.horn = {3: ramp("2c2620", "483e32", "665844", "887860"), 4: ramp("b8b098", "d0c8ac", "e6dec4", "f6f0da")}.get(tier, ramp("2c2620", "483e32", "665844", "887860"))
        self.fang = ramp("b8ae90", "d4caa8", "e8dfc0", "f6efd6") if tier < 4 else ramp("d8d0b4", "e8e0c6", "f4eed8", "fffaea")
        self.sac = {1: ramp("54442e", "685638", "7e6a44", "947e50"), 2: ramp("54502f", "686338", "7e7a42", "98924e"),
                    3: ramp("4c5a34", "62743e", "7a9048", "94ac58"), 4: ramp("246434", "338644", "48a858", "6ccc78")}[tier]
        self.egg = ramp("a89870", "c2b284", "dccc9c", "f0e2b4")
        self.vein = (90, 40, 40)
        self.silk = ramp("b0b0a8", "cdcdc4", "e2e2d8", "f6f6ee")
        self.bone = ramp("a8a088", "c4bda0", "ddd5b8", "f0e8cc")
        self.sl = ramp("1a2a18", "26401f", "345a2a", "447838")
        self.venom = {1: (90, 200, 60), 2: (110, 230, 60), 3: (130, 255, 70), 4: (90, 255, 130)}[tier]
        self.eye = {1: (150, 24, 20), 2: (205, 40, 24), 3: (255, 70, 30), 4: (255, 120, 60)}[tier]
        self.eye_core = {1: (200, 60, 40), 2: (255, 110, 70), 3: (255, 170, 110), 4: (255, 240, 210)}[tier]
        self.egg_strength = {1: 0.4, 2: 0.55, 3: 0.7, 4: 0.5}[tier]


# ------------------------------------------------------------------------------------------------ painting
FACE_LIGHT = {"top": 1.12, "bottom": 0.72, "left": 0.88, "front": 1.0, "right": 0.88, "back": 0.82}


def face_rects(c):
    u, v = c["u"], c["v"]
    w, h, d = (int(n) for n in c["size"])
    return {
        "top": (u + d, v, w, d), "bottom": (u + d + w, v, w, d),
        "left": (u, v + d, d, h), "front": (u + d, v + d, w, h),
        "right": (u + d + w, v + d, d, h), "back": (u + 2 * d + w, v + d, w, h),
    }


def put(img, x, y, col, a=255):
    if 0 <= x < SIZE and 0 <= y < SIZE:
        img.putpixel((x, y), (int(col[0]), int(col[1]), int(col[2]), a))


def paint_cube(img, glow, c, pal, rng_seed):
    rects = face_rects(c)
    seed = rng_seed + sum(ord(ch) for ch in c["id"])
    for face, (fx, fy, fw, fh) in rects.items():
        for py in range(fh):
            for px in range(fw):
                X, Y = fx + px, fy + py
                col = base_pixel(c["mat"], pal, X, Y, px, py, fw, fh, face, seed)
                k = FACE_LIGHT[face]
                if face in ("left", "front", "right", "back"):
                    if py == 0:
                        k *= 1.08
                    if py == fh - 1:
                        k *= 0.82
                    if px == 0 or px == fw - 1:
                        k *= 0.94
                col = mul(col, k)
                img.putpixel((X, Y), (int(col[0]), int(col[1]), int(col[2]), 255))
    d = c.get("deco")
    if d:
        DECOS[d](img, glow, c, rects, pal, seed)
    if c.get("glow") == "all":
        for face, (fx, fy, fw, fh) in rects.items():
            for py in range(fh):
                for px in range(fw):
                    col = mul(pal.venom, 0.7 + 0.5 * vnoise(fx + px, fy + py, 2.0, seed))
                    put(glow, fx + px, fy + py, col)


def base_pixel(mat, pal, X, Y, px, py, fw, fh, face, seed):
    n1 = vnoise(X, Y, 4.0, seed)
    n2 = vnoise(X, Y, 2.0, seed + 7)
    n3 = _h(X, Y, seed + 3)
    if mat in ("chitin", "chitin_dark", "leg"):
        t = 0.2 + 0.55 * n1 + 0.25 * (n3 - 0.5)
        if mat == "chitin_dark":
            t -= 0.12
        col = pick(pal.chitin, t)
        if n2 > 0.82:
            col = mul(col, 1.3)  # glossy sheen
        if n3 > 0.95:
            col = mul(col, 0.6)
        if mat == "leg" and (px + 2 * py) % 7 == 0 and n1 > 0.45:
            col = mul(col, 1.25)  # fine hairs
        return col
    if mat == "plate":
        col = pick(pal.plate, 0.25 + 0.5 * n1 + 0.2 * (n3 - 0.5))
        if (px + py) % 6 == 0:
            col = mul(col, 1.15)
        if py == fh - 1 or px == 0:
            col = mul(col, 0.8)
        return col
    if mat == "horn":
        return pick(pal.horn, 0.3 + 0.5 * n1 + 0.4 * (py / max(1, fh - 1)) - 0.2)
    if mat == "fang":
        col = pick(pal.fang, 0.35 + 0.5 * n1 + 0.2 * (n3 - 0.5))
        return mul(col, 0.8) if (px + py) % 5 == 0 else col
    if mat == "sac":
        col = pick(pal.sac, 0.3 + 0.5 * n1 + 0.25 * (n2 - 0.5))
        if vnoise(X, Y, 6.0, seed + 5) > 0.7:
            col = mul(col, 1.15)  # thin translucent patches
        return col
    if mat == "eggsac":
        col = pick(pal.silk, 0.3 + 0.5 * n1 + 0.2 * (n3 - 0.5))
        if (X * 3 + Y * 2) % 7 == 0:
            col = mul(col, 0.8)
        return col
    if mat == "silk":
        col = pick(pal.silk, 0.5 + 0.5 * n1)
        return mul(col, 0.9) if py % 2 else col
    if mat == "cocoon":
        col = pick(pal.silk, 0.35 + 0.55 * n1 + 0.2 * (n3 - 0.5))
        if (X + Y * 2) % 5 == 0 or (X * 2 - Y) % 7 == 0:
            col = mul(col, 0.85)  # criss-cross strands
        if py % 4 == 0:
            col = mul(col, 0.93)
        return col
    if mat == "cocoon_band":
        col = mul(pick(pal.silk, 0.2 + 0.4 * n1), 0.78)
        if (X + Y) % 3 == 0:
            col = mul(col, 0.85)
        return col
    if mat == "hair":
        return mix(pick(pal.chitin, 0.4 + 0.4 * n3), (90, 74, 56), 0.5 if py == 0 else 0.0)
    if mat == "spiderling":
        return pick(pal.sl, 0.25 + 0.5 * n1 + 0.2 * n3)
    if mat == "venom":
        return mul(pal.venom, 0.7)
    return (255, 0, 255)


def deco_head(img, glow, c, rects, pal, seed):
    fx, fy, fw, fh = rects["front"]
    dark = (10, 8, 8)
    # eight eyes: two big (2x2), two medium (1x2), four small (1x1)
    eyes = [(1, 2, 2, 2), (5, 2, 2, 2), (3, 1, 1, 2), (4, 1, 1, 2), (1, 0, 1, 1), (6, 0, 1, 1), (0, 4, 1, 1), (7, 4, 1, 1)]
    for ex, ey, ew, eh in eyes:
        for py in range(ey - 1, ey + eh + 1):
            for px in range(ex - 1, ex + ew + 1):
                if 0 <= px < fw and 0 <= py < fh and not (ex <= px < ex + ew and ey <= py < ey + eh):
                    put(img, fx + px, fy + py, dark)
    for ex, ey, ew, eh in eyes:
        for py in range(ey, ey + eh):
            for px in range(ex, ex + ew):
                core = (ew + eh >= 3) and px == ex and py == ey
                col = pal.eye_core if core else pal.eye
                put(img, fx + px, fy + py, mul(pal.eye, 0.55))
                put(glow, fx + px, fy + py, col)
    # mouth slit
    for px in range(2, 6):
        put(img, fx + px, fy + 5, dark)
    if pal.t == 4:  # bone-white markings: a skull-like brow and cheek stripes
        for px in (0, 7):
            for py in range(1, 4):
                put(img, fx + px, fy + py, pal.bone[3])
        for px in (2, 3, 4, 5):
            put(img, fx + px, fy, pal.bone[2])
    tx, ty, tw, th = rects["top"]
    for k in range(th):
        put(img, tx + tw // 2, ty + k, mul(pal.chitin[0], 0.7))
    if pal.t >= 3:
        for px, py in ((1, 0), (6, 0)):
            put(img, fx + px, fy + py, pal.horn[2])


def deco_thorax(img, glow, c, rects, pal, seed):
    tx, ty, tw, th = rects["top"]
    for k in range(th):
        put(img, tx + tw // 2 - 1 + (k % 2), ty + k, mul(pal.chitin[0], 0.75))
    for face in ("front", "back", "left", "right"):
        fx, fy, fw, fh = rects[face]
        for px in range(fw):
            if px % 3 == 0:
                put(img, fx + px, fy + fh - 2, mul(pal.chitin[0], 0.7))


def deco_hump(img, glow, c, rects, pal, seed):
    tx, ty, tw, th = rects["top"]
    cx = tx + tw // 2
    # a chevron mark: faint in the lower tiers, bone-white on the Queen
    for k in range(4):
        col = pal.bone[3] if pal.t == 4 else mul(pal.chitin[3], 1.1)
        put(img, cx - 1 - k, ty + 2 + k, col)
        put(img, cx + k, ty + 2 + k, col)
    if pal.t >= 3:
        for k in range(4):
            put(glow, cx - 1 - k, ty + 2 + k, pal.venom, 150)
            put(glow, cx + k, ty + 2 + k, pal.venom, 150)


def deco_plate(img, glow, c, rects, pal, seed):
    for face in ("front", "back", "left", "right"):
        fx, fy, fw, fh = rects[face]
        for px in range(fw):
            put(img, fx + px, fy + fh - 1, mul(pal.plate[0], 0.8))
    tx, ty, tw, th = rects["top"]
    for px in range(tw):
        if px % 4 == 1:
            put(img, tx + px, ty + th // 2, mul(pal.plate[0], 0.8))
    if pal.t == 4:
        for px in range(2, tw - 2):
            put(img, tx + px, ty, pal.bone[3])


def deco_pad(img, glow, c, rects, pal, seed):
    fx, fy, fw, fh = rects["front"]
    put(img, fx + fw // 2, fy + fh // 2, mul(pal.plate[3], 1.15))
    put(img, fx + fw // 2 - 1, fy + fh // 2 - 1, mul(pal.plate[3], 1.05))


def deco_leg(img, glow, c, rects, pal, seed):
    for face in ("top", "bottom", "front", "back"):
        fx, fy, fw, fh = rects[face]
        for py in range(fh):
            if pal.t == 4 and face in ("top", "front", "back"):
                for px in (0, 1):  # bone-white band at the far end of each segment
                    if fw > 4:
                        put(img, fx + px, fy + py, pal.bone[2 + px % 2])


def deco_fang(img, glow, c, rects, pal, seed):
    if pal.t < 2:
        return
    for face in ("front", "back", "left", "right"):
        fx, fy, fw, fh = rects[face]
        for py in range(fh):
            for px in range(fw):
                k = (py + 1) / fh
                if k > 0.4:
                    put(glow, fx + px, fy + py, mul(pal.venom, k), int(120 + 100 * k))
                    put(img, fx + px, fy + py, mix(pal.fang[1], (60, 120, 40), (k - 0.4)))


def deco_crown(img, glow, c, rects, pal, seed):
    for face in ("front", "back", "left", "right"):
        fx, fy, fw, fh = rects[face]
        for px in range(fw):
            put(img, fx + px, fy, pal.bone[3])


def deco_eggsac(img, glow, c, rects, pal, seed):
    if pal.t < 3:
        return
    for face, (fx, fy, fw, fh) in rects.items():
        put(glow, fx + fw // 2, fy + fh // 2, pal.venom, 140)  # a faint embryo inside
        put(img, fx + fw // 2, fy + fh // 2, (30, 70, 20))


SPIDERLING = [(0, 0), (-1, -1), (1, -1), (-1, 1), (1, 1), (-2, -1), (2, -1), (-2, 1), (2, 1), (0, 1), (0, -1)]


def deco_sac(img, glow, c, rects, pal, seed):
    t = pal.t
    for face, (fx, fy, fw, fh) in rects.items():
        rng = random.Random(seed + sum(ord(ch) for ch in face) * 31)
        # --- egg shapes under the membrane
        count = (fw * fh) // (50 if t < 4 else 130)
        for _ in range(count):
            cx, cy = rng.uniform(0, fw), rng.uniform(0, fh)
            rx, ry = rng.uniform(1.6, 3.0), rng.uniform(1.8, 3.2)
            for py in range(int(cy - ry - 1), int(cy + ry + 2)):
                for px in range(int(cx - rx - 1), int(cx + rx + 2)):
                    if 0 <= px < fw and 0 <= py < fh:
                        d = ((px - cx) / rx) ** 2 + ((py - cy) / ry) ** 2
                        if d <= 1:
                            lit = 1 - ((px - cx) / rx + (py - cy) / ry + 2) / 4
                            col = pick(pal.egg, 0.15 + 0.8 * lit)
                            if d > 0.62:
                                col = mul(col, 0.62)
                            fxx, fyy = fx + px, fy + py
                            old = img.getpixel((fxx, fyy))[:3]
                            put(img, fxx, fyy, mix(old, col, pal.egg_strength))
        # --- veins (T2+), glowing
        if t >= 2:
            for _ in range((fw + fh) // 6):
                x, y = rng.uniform(0, fw), rng.uniform(0, fh)
                ang = rng.uniform(0, math.tau)
                for _s in range(rng.randint(8, 16)):
                    ang += rng.uniform(-0.6, 0.6)
                    x += math.cos(ang)
                    y += math.sin(ang)
                    ix, iy = int(x), int(y)
                    if 0 <= ix < fw and 0 <= iy < fh:
                        put(img, fx + ix, fy + iy, mix(img.getpixel((fx + ix, fy + iy))[:3], pal.vein, 0.7))
                        put(glow, fx + ix, fy + iy, pal.venom, {2: 80, 3: 150, 4: 210}[t])
        # --- venom markings (T3+): a hourglass on top, chevrons on the sides
        if t >= 3 and face == "top":
            for py in range(fh):
                half = max(0, int(abs(py - fh / 2) / 2.0))
                for px in range(fw // 2 - 1 - half, fw // 2 + 1 + half):
                    if abs(py - fh / 2) > 1 and 0 <= px < fw and py % 6 not in (5,):
                        put(glow, fx + px, fy + py, pal.venom, 230)
                        put(img, fx + px, fy + py, mul(pal.venom, 0.35))
        if t >= 3 and face in ("left", "right", "front", "back"):
            for row in range(2, fh - 2, 5):
                for px in range(fw):
                    y = row + abs(px - fw // 2) // 2
                    if y < fh:
                        put(glow, fx + px, fy + y, pal.venom, 200)
                        put(img, fx + px, fy + y, mul(pal.venom, 0.3))
        # --- T4: the whole sac glows venom green with dark spiderlings inside
        if t == 4:
            for py in range(fh):
                for px in range(fw):
                    g = 0.75 + 0.5 * vnoise(fx + px, fy + py, 3.0, seed + 9)
                    put(glow, fx + px, fy + py, mul(pal.venom, g), 150)
            for _ in range(max(1, (fw * fh) // 70)):
                cx, cy = rng.randint(3, max(3, fw - 4)), rng.randint(2, max(2, fh - 3))
                for dx, dy in SPIDERLING:
                    if 0 <= cx + dx < fw and 0 <= cy + dy < fh:
                        put(img, fx + cx + dx, fy + cy + dy, (10, 22, 10))
                        put(glow, fx + cx + dx, fy + cy + dy, (0, 0, 0), 0)
                if cx - 1 >= 0 and cy - 1 >= 0:
                    put(glow, fx + cx, fy + cy - 1, (255, 60, 40), 255)


def deco_cocoon(img, glow, c, rects, pal, seed):
    pass


def deco_spiderling(img, glow, c, rects, pal, seed):
    fx, fy, fw, fh = rects["front"]
    for face, (rx, ry, rw, rh) in rects.items():
        for py in range(rh):
            for px in range(rw):
                if (px + py) % 2 == 0:
                    put(glow, rx + px, ry + py, pal.venom, 150)
    for px in range(fw):
        if px % 2 == 0 and fw >= 3:
            put(glow, fx + px, fy + 1, (255, 60, 40))
            put(img, fx + px, fy + 1, (120, 20, 16))


DECOS = {"head": deco_head, "thorax": deco_thorax, "hump": deco_hump, "plate": deco_plate, "pad": deco_pad, "leg": deco_leg,
         "fang": deco_fang, "crown": deco_crown, "eggsac": deco_eggsac, "sac": deco_sac, "cocoon": deco_cocoon, "spiderling": deco_spiderling}


def paint(tier):
    pal = Palette(tier)
    img = Image.new("RGBA", (SIZE, SIZE), (0, 0, 0, 0))
    glow = Image.new("RGBA", (SIZE, SIZE), (0, 0, 0, 0))
    for c in CUBES:
        if c["share"] is None:  # shared UV regions are painted once, by their owner
            paint_cube(img, glow, c, pal, 100 * tier)
    return img, glow


# ------------------------------------------------------------------------------------------------ Java
def fnum(v):
    return f"{float(v):g}f"


def java():
    children = {}
    for p in PARTS:
        children.setdefault(p["parent"], []).append(p)
    cubes_of = {}
    for c in CUBES:
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
        pose = f"PartPose.offsetAndRotation({fnum(px)}, {fnum(py)}, {fnum(pz)}, {fnum(rx)}, {fnum(ry)}, {fnum(rz)})" if (rx or ry or rz) else f"PartPose.offset({fnum(px)}, {fnum(py)}, {fnum(pz)})"
        out.append(f'{pad}PartDefinition {var} = {parent_var}.addOrReplaceChild("{p["name"]}",')
        out.extend(b)
        out.append(f"{pad}        , {pose});")
        for ch in children.get(p["name"], []):
            emit(ch, var)

    for p in children.get(None, []):
        emit(p, "root")
    body = "\n".join(out)
    return f"""package com.skyrpg.combat.client.slayer;

import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;

/**
 * The Broodmother's cubes and their UVs. GENERATED by tools/art/generate_broodmother.py from the same cube table
 * the textures are painted from: edit the table there, never this file.
 */
final class BroodmotherLayout {{
    private BroodmotherLayout() {{
    }}

    static LayerDefinition create() {{
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
{body}
        return LayerDefinition.create(mesh, {SIZE}, {SIZE});
    }}
}}
"""


def main():
    rows = pack()
    print(f"packed {len(CUBES)} cubes, {sum(1 for c in CUBES if c['share'] is None)} unique UV regions, {rows}/{SIZE} rows used")
    TEX_DIR.mkdir(parents=True, exist_ok=True)
    for tier in (1, 2, 3, 4):
        img, glow = paint(tier)
        img.save(TEX_DIR / f"broodmother_model_t{tier}.png")
        glow.save(TEX_DIR / f"broodmother_model_t{tier}_glow.png")
    JAVA_OUT.write_text(java(), encoding="utf-8")
    print("wrote textures and", JAVA_OUT.name)


if __name__ == "__main__":
    main()
