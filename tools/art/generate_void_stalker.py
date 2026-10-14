#!/usr/bin/env python3
"""Draws the Void Stalker slayer boss's model textures (one per tier) and writes its model definition.

    python tools/art/generate_void_stalker.py

Same architecture as generate_tyrant.py: the CUBES table below is the single source of truth. It lists every cube
of the model, packs them into a 128x128 texture with Minecraft's standard box layout, paints one texture per tier
and writes the Java that builds the LayerDefinition (VoidStalkerLayout.java), so cubes and UVs cannot drift apart.
Outputs:

    combat/src/main/resources/assets/skyrpg_combat/textures/entity/slayer/void_stalker_model_t{1..4}.png
    ... void_stalker_model_t{1..4}_glow.png   (only the glowing pixels; drawn fullbright by VoidStalkerBossRenderer)
    combat/src/client/java/com/skyrpg/combat/client/slayer/VoidStalkerLayout.java

Box layout for a cube at texOffs(u, v) of size (w, h, d), as vanilla maps it (model -z is the front):
    top (u+d, v)  bottom (u+d+w, v)           each w x d
    row at y = v+d:  -x side (u, d wide) | front -z (u+d, w) | +x side (u+d+w, d) | back +z (u+2d+w, w)
Left-hand cubes reuse the right-hand cube's UVs and are mirrored in the model.
Parts hidden below a tier carry tier=N in part(); the Java gets them as VoidStalkerLayout.MIN_TIER.
"""
import math
import random
import sys
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parents[2]
TEX_DIR = ROOT / "combat/src/main/resources/assets/skyrpg_combat/textures/entity/slayer"
JAVA_OUT = ROOT / "combat/src/client/java/com/skyrpg/combat/client/slayer/VoidStalkerLayout.java"
SIZE = 128
SHIELD_TILES = 32

# ------------------------------------------------------------------------------------------------ cube table
# Pivots are relative to the parent part (model pixels, y down, feet at y=24 for the root).
PARTS = []
CUBES = []


def part(name, parent, pivot, rot=(0, 0, 0), tier=1):
    PARTS.append(dict(name=name, parent=parent, pivot=pivot, rot=rot, tier=tier))


def cube(pname, cid, mat, pos, size, share=None, mirror=False, **kw):
    CUBES.append(dict(part=pname, id=cid, mat=mat, pos=pos, size=size, share=share, mirror=mirror, **kw))


# ---- legs (digitigrade: thigh forward-down, shin back-down, foot flat), under the root
for side, sx, mir in (("right", -2.5, False), ("left", 2.5, True)):
    p = side[0]

    def s(name, mir=mir):
        return ("r_" + name) if mir else None

    part(f"{side}_thigh", "root", (sx, 3.2, 0), rot=(-0.6, 0, 0))
    cube(f"{side}_thigh", f"{p}_thigh", "void", (-1.5, 0, -1.5), (3, 11, 3), share=s("thigh"), mirror=mir)
    cube(f"{side}_thigh", f"{p}_knee", "obsidian", (-1, 8, -2.5), (2, 3, 1), share=s("knee"), mirror=mir)
    part(f"{side}_shin", f"{side}_thigh", (0, 11, 0), rot=(1.1, 0, 0))
    cube(f"{side}_shin", f"{p}_shin", "void_dark", (-1, 0, -1), (2, 11, 2), share=s("shin"), mirror=mir)
    cube(f"{side}_shin", f"{p}_spur", "finger", (-0.5, 1, 1), (1, 5, 1), share=s("spur"), mirror=mir)
    part(f"{side}_foot", f"{side}_shin", (0, 11, 0), rot=(-0.5, 0, 0))
    cube(f"{side}_foot", f"{p}_foot", "void", (-1.5, 0, -5), (3, 2, 6), share=s("foot"), mirror=mir)
    cube(f"{side}_foot", f"{p}_toe", "finger", (-0.5, 0.5, -9), (1, 1, 4), share=s("toe"), mirror=mir)

# ---- hips, cloak
part("hips", None, (0, 3.2, 0))
cube("hips", "pelvis", "void_dark", (-3, -2, -2), (6, 4, 4))
part("cloak_front", "hips", (0, -1, -2.6))
cube("cloak_front", "cl_front", "cloak", (-3, 0, 0), (6, 12, 1))
part("cloak_back", "hips", (0, -1, 2.6))
cube("cloak_back", "cl_back", "cloak", (-4, 0, 0), (8, 14, 1))
part("cloak_right", "hips", (-3, -1, 0))
cube("cloak_right", "cl_right", "cloak", (-1, 0, -2), (1, 10, 4))
part("cloak_left", "hips", (3, -1, 0))
cube("cloak_left", "cl_left", "cloak", (0, 0, -2), (1, 10, 4), share="cl_right", mirror=True)
# tier III: the trailing rift cloak replaces the back and side tatters
part("rift_back", "hips", (0, -1, 2.6), tier=3)
cube("rift_back", "rift_b", "rift", (-6, 0, 0), (12, 13, 1))
part("rift_tail", "rift_back", (0, 13, 0), tier=3)
cube("rift_tail", "rift_t", "rift", (-5, 0, 0), (10, 13, 1))
part("rift_wing_right", "hips", (-3, -1, 1), rot=(0, 0, 0.3), tier=3)
cube("rift_wing_right", "rift_wr", "rift", (-1, 0, 0), (1, 15, 8))
part("rift_wing_left", "hips", (3, -1, 1), rot=(0, 0, -0.3), tier=3)
cube("rift_wing_left", "rift_wl", "rift", (0, 0, 0), (1, 15, 8), share="rift_wr", mirror=True)

# ---- torso: narrow abdomen, ribcage of two cracked plates around a recessed glowing core
part("waist", "hips", (0, -2, 0))
cube("waist", "abdomen", "void", (-2, -6, -1.5), (4, 6, 3))
cube("waist", "ab_rib0", "void_dark", (-3, -5, -2), (6, 1, 3))
cube("waist", "ab_rib1", "void_dark", (-3, -2.5, -2), (6, 1, 3), share="ab_rib0")
part("chest", "waist", (0, -6, 0))
cube("chest", "plate_r", "void", (-4, -8, -2.5), (3, 8, 5), deco="plate")
cube("chest", "plate_l", "void", (1, -8, -2.5), (3, 8, 5), share="plate_r", mirror=True)
cube("chest", "chest_back", "void_dark", (-1, -8, 1.5), (2, 8, 1))
cube("chest", "ribbar0", "obsidian", (-1.5, -6.5, -2.8), (3, 1, 1))
cube("chest", "ribbar1", "obsidian", (-1.5, -2.5, -2.8), (3, 1, 1), share="ribbar0")
cube("chest", "collar", "void_dark", (-6, -9, -2), (12, 2, 4))
part("core", "chest", (0, -4, 0))
cube("core", "core", "core", (-1.5, -3, -1.5), (3, 6, 3))
part("spine", "waist", (0, 0, 0), tier=3)
for i, (sy, sz, sh_, sd) in enumerate([(-3, 1.5, 4, 3), (-7, 1.5, 5, 3), (-11, 2.5, 6, 3), (-14.5, 2.5, 5, 3), (-17.5, 2.5, 4, 2)]):
    cube("spine", f"spk{i}", "crystal", (-1, sy - sh_ / 2, sz), (2, sh_, sd), sk=f"spk{sh_}{sd}")

# ---- head: elongated, faceless, with a vertical glowing slit
part("head", "chest", (0, -8.5, 0))
cube("head", "neck", "void_dark", (-1, -2, -1), (2, 4, 2))
cube("head", "skull", "void", (-2.5, -10, -2.5), (5, 10, 5), deco="skull")
cube("head", "crest", "obsidian", (-1, -9, 2.5), (2, 7, 3))
cube("head", "horn_r", "obsidian", (-3.5, -8, -1), (1, 5, 2))
cube("head", "horn_l", "obsidian", (2.5, -8, -1), (1, 5, 2), share="horn_r", mirror=True)
part("halo", "head", (0, -15, 0), tier=4)
for i in range(6):
    a = i * math.pi / 3
    part(f"halo_s{i}", "halo", (round(math.cos(a) * 7, 2), 0, round(math.sin(a) * 7, 2)), rot=(0, -a, 0), tier=4)
    cube(f"halo_s{i}", f"halo_{i}", "crystal", (-1, -3, -1), (2, 6, 2), share="halo_0" if i else None)

# ---- arms: very long, thin, ending in finger blades; mirrored for the left
for side, sx, mir in (("right", -5.5, False), ("left", 5.5, True)):
    p = side[0]
    sgn = 1 if mir else -1  # +1 = the +x side of the body

    def s(name, mir=mir):
        return ("r_" + name) if mir else None

    part(f"{side}_arm", "chest", (sx, -7, 0), rot=(0, 0, -0.06 * sgn))
    cube(f"{side}_arm", f"{p}_upper", "void", (-1, -1, -1), (2, 11, 2), share=s("upper"), mirror=mir)
    # tier II obsidian shoulder crystals
    for k, (rz, ch) in enumerate([(0.5, 6), (-0.3, 5)]):
        part(f"{side}_crys{k}", f"{side}_arm", (sgn * 1.2, -1, 0), rot=(0, 0, sgn * rz), tier=2)
        cube(f"{side}_crys{k}", f"{p}_crys{k}", "crystal", (-1.5, -ch, -1.5), (3, ch, 3), share=s(f"crys{k}"), mirror=mir)
    part(f"{side}_forearm", f"{side}_arm", (0, 10, 0), rot=(-0.25, 0, 0))
    cube(f"{side}_forearm", f"{p}_fore", "void_dark", (-1, 0, -1), (2, 12, 2), share=s("fore"), mirror=mir)
    cube(f"{side}_forearm", f"{p}_elbow", "obsidian", (-0.5, -1, 1), (1, 3, 2), share=s("elbow"), mirror=mir)
    part(f"{side}_hand", f"{side}_forearm", (0, 12, 0))
    cube(f"{side}_hand", f"{p}_palm", "void", (-1.5, 0, -1), (3, 2, 2), share=s("palm"), mirror=mir)
    for k, (fx, fl) in enumerate([(-1, 9), (0, 11), (1, 9)]):
        part(f"{side}_f{k}", f"{side}_hand", (fx, 2, 0))
        share = f"r_f{k}" if mir else ("r_f0" if k == 2 else None)
        cube(f"{side}_f{k}", f"{p}_f{k}", "finger", (-0.5, 0, -0.5), (1, fl, 1), share=share, mirror=mir)
    # tier III second pair of arms, small, from the lower ribs
    part(f"{side}_sarm", "chest", (sgn * 3.0, -2.5, -1.5), rot=(-0.3, 0, -0.12 * sgn), tier=3)
    cube(f"{side}_sarm", f"{p}_supper", "void", (-1, 0, -1), (2, 7, 2), share=s("supper"), mirror=mir)
    part(f"{side}_sfore", f"{side}_sarm", (0, 7, 0), rot=(-0.5, 0, 0), tier=3)
    cube(f"{side}_sfore", f"{p}_sfore", "void_dark", (-1, 0, -1), (2, 7, 2), share="r_supper", mirror=mir)
    for k, fx in enumerate((-0.5, 0.5)):
        part(f"{side}_sf{k}", f"{side}_sfore", (fx, 7, 0), tier=3)
        cube(f"{side}_sf{k}", f"{p}_sf{k}", "finger", (-0.5, 0, -0.5), (1, 6, 1), share="r_sf0" if (mir or k) else None, mirror=mir)

# ---- floating void shards (root level, positioned by the model) and the shield facets
part("shards", None, (0, 0, 0))
for i in range(8):
    part(f"shard{i}", "shards", (0, 0, 0))
    cube(f"shard{i}", f"shard{i}", "crystal", (-1.5, -3.5, -1.5), (3, 7, 3), share="shard0" if i else None)
part("shield", None, (0, 0, 0))
for i in range(SHIELD_TILES):
    part(f"tile{i}", "shield", (0, 0, 0))
    cube(f"tile{i}", f"tile{i}", "shield", (-4, -4, -0.5), (8, 8, 1), share="tile0" if i else None, deco="tile")

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
        if attempt < len(orders):
            order = sorted(uniq, key=orders[attempt])
        else:
            order = sorted(uniq, key=lambda c: -net(c)[1] + rng.uniform(-4, 4))
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


def ramp(*s):
    return [hexc(x) for x in s]


def pick(r, t):
    t = max(0.0, min(0.9999, t))
    return r[int(t * len(r))]


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


class Palette:
    def __init__(self, tier):
        self.tier = tier
        i = tier - 1
        self.void = ramp(*[("0a0910", "100e17", "171420", "1f1b2b"), ("0c0a14", "131022", "1b172e", "251f3c"),
                           ("0d0a18", "161129", "201a38", "2c2347"), ("0e0a1c", "191230", "241b42", "322659")][i])
        self.void_dark = [mul(c, 0.7) for c in self.void]
        self.obsidian = ramp(*[("0c0814", "180f2a", "261840", "3a2860"), ("0a0612", "1e1038", "36206a", "5a38a8"),
                               ("0a0614", "24123e", "42267e", "7048c8"), ("100820", "2c1658", "5028a0", "8a60ec")][i])
        self.crystal = ramp(*[("1a0e30", "2c1850", "442878", "5c3a9c"), ("1c0e3a", "34186a", "5a2ca8", "8650e0"),
                              ("20104a", "40207e", "6e3cc8", "a070ff"), ("2a1460", "5428a8", "8a50f0", "d0b0ff")][i])
        self.core = ramp(*[("3a1458", "5a2288", "7c34b8", "a04ee0"), ("5a22a0", "7e3ad4", "a35cf2", "c88cff"),
                           ("7a3ad0", "a05cf0", "c88aff", "e6c0ff"), ("c0a0ff", "e0d0ff", "f4ecff", "ffffff")][i])
        self.glow = [(150, 70, 220), (185, 100, 245), (210, 130, 255), (240, 225, 255)][i]
        self.vein = [(0, 0, 0), (120, 56, 200), (150, 80, 235), (235, 215, 255)][i]
        self.finger = ramp("14121c", "24202e", "3c3650", "6a608a")
        self.cloak = ramp(*[("08070d", "0e0c14", "15121d", "1c1827"), ("0a0810", "100d19", "181424", "211b30"),
                            ("0b0813", "120f1e", "1a1629", "241d36"), ("0c0916", "141022", "1d182f", "271f3e")][i])


def put(img, x, y, col, a=255):
    if 0 <= x < SIZE and 0 <= y < SIZE:
        img.putpixel((x, y), (int(col[0]), int(col[1]), int(col[2]), a))


def paint_cube(img, glow, c, pal):
    rects = face_rects(c)
    mat = c["mat"]
    seed = pal.tier * 977 + sum(ord(ch) * (i + 1) for i, ch in enumerate(c["id"]))
    for face, (fx, fy, fw, fh) in rects.items():
        for py in range(fh):
            for px in range(fw):
                X, Y = fx + px, fy + py
                col, alpha, gcol, ga = base_pixel(mat, pal, X, Y, px, py, fw, fh, face, seed)
                k = FACE_LIGHT[face]
                if mat not in ("core", "shield"):
                    if face in ("left", "front", "right", "back"):
                        if py == 0:
                            k *= 1.1
                        if py == fh - 1:
                            k *= 0.82
                        if px == 0 or px == fw - 1:
                            k *= 0.94
                    col = mul(col, k)
                put(img, X, Y, col, alpha)
                if gcol is not None and alpha > 0:
                    put(glow, X, Y, gcol, ga)
    deco = c.get("deco")
    if deco:
        DECOS[deco](img, glow, c, rects, pal, seed)


def veins(pal, X, Y, seed):
    """Purple veins from tier II, glowing cracks of light across everything at tier IV: (colour, glow alpha) or None."""
    t = pal.tier
    if t < 2:
        return None
    r = abs(vnoise(X, Y, 5.0, seed + 50) - 0.5)
    thr = {2: 0.022, 3: 0.034, 4: 0.05}[t]
    if r < thr:
        return pal.vein, {2: 150, 3: 200, 4: 255}[t]
    if t == 4:
        r2 = abs(vnoise(X + 31, Y, 3.0, seed + 60) - 0.5)
        if r2 < 0.03:
            return mix(pal.vein, pal.glow, 0.4), 220
    return None


def base_pixel(mat, pal, X, Y, px, py, fw, fh, face, seed):
    n1 = vnoise(X, Y, 4.0, seed)
    n3 = _h(X, Y, seed + 3)
    alpha, gcol, ga = 255, None, 0
    if mat in ("void", "void_dark"):
        r = pal.void if mat == "void" else pal.void_dark
        col = pick(r, 0.2 + 0.5 * n1 + 0.2 * (n3 - 0.5))
        v = veins(pal, X, Y, seed)
        if v:
            col, gcol, ga = v[0], v[0], v[1]
    elif mat == "obsidian":
        col = pick(pal.obsidian, 0.15 + 0.5 * n1 + 0.2 * (n3 - 0.5))
        if (X + Y) % 9 == 0 or ((X + Y) % 9 == 1 and n1 > 0.5):
            col = mix(col, pal.obsidian[3], 0.55)  # glassy streak
        v = veins(pal, X, Y, seed)
        if v:
            col, gcol, ga = v[0], v[0], v[1]
    elif mat == "crystal":
        col = pick(pal.crystal, 0.1 + 0.7 * n1 + 0.2 * (n3 - 0.5))
        edge = px in (0, fw - 1) or py in (0, fh - 1)
        base_a = [70, 110, 150, 200][pal.tier - 1]
        # the shard's inside glows through its facets, brighter at the centre
        glow_k = 0.45 + 0.55 * (1 - abs(px - fw / 2) / (fw / 2 + 0.1)) if not edge else 0.3
        gcol, ga = mix(pal.crystal[3], pal.glow, 0.5), int(base_a * glow_k)
        if face in ("top", "bottom"):
            ga = int(ga * 0.6)
    elif mat == "core":
        t = 0.3 + 0.6 * vnoise(X, Y, 2.0, seed)
        if (px + py * 2 + seed) % 5 == 0:
            t *= 0.7
        col = pick(pal.core, t)
        gcol, ga = col, 255
    elif mat == "finger":
        col = pick(pal.finger, 0.15 + 0.5 * n1 + (0.3 if px == 0 else 0))
        if py >= fh - 2 and fh > 3:
            col, gcol, ga = mix(col, pal.glow, 0.5), pal.glow, [90, 140, 190, 255][pal.tier - 1]  # glowing blade tip
    elif mat == "cloak":
        col = pick(pal.cloak, 0.2 + 0.6 * n1 + 0.2 * (n3 - 0.5))
        if py % 3 == 0:
            col = mul(col, 0.92)
        if face in ("front", "back", "left", "right"):
            ragged = fh - 1 - py
            hole = vnoise(X, Y, 2.5, seed + 41)
            if (ragged < 4 and hole > 0.4 + 0.12 * ragged) or hole > 0.86 or (ragged < 7 and (px * 7 + seed) % 5 == 0 and ragged < 1 + (px * 3 + seed) % 6):
                alpha = 0
            if fh >= 10 and py < 2:
                alpha = 255
        v = veins(pal, X, Y, seed) if pal.tier >= 3 and alpha else None
        if v and pal.tier == 4 and vnoise(X, Y, 4.0, seed + 70) > 0.5:
            col, gcol, ga = v[0], v[0], int(v[1] * 0.6)
    elif mat == "rift":
        # the inside of a rift: deep indigo with a nebula and stars
        grad = py / max(1, fh - 1)
        col = mix((12, 8, 34), (30, 14, 70), vnoise(X, Y, 6.0, seed + 5) * 0.8 + grad * 0.3)
        neb = vnoise(X, Y, 3.0, seed + 9)
        if neb > 0.62:
            col = mix(col, (90, 40, 170), (neb - 0.62) * 2.2)
            gcol, ga = (110, 50, 200), int((neb - 0.62) * 2.2 * 140)
        st = _h(X, Y, seed + 13)
        if st > 0.955:
            col = (240, 230, 255) if st > 0.985 else (170, 140, 255)
            gcol, ga = col, 255
        ragged = fh - 1 - py
        hole = vnoise(X, Y, 2.5, seed + 41)
        if face in ("front", "back", "left", "right") and ((ragged < 4 and hole > 0.42 + 0.1 * ragged) or hole > 0.88 or (ragged < 6 and (px * 5 + seed) % 4 == 0 and ragged < 1 + (px * 3 + seed) % 5)):
            alpha = 0
            gcol = None
        if fh >= 10 and py < 2:
            alpha = 255
    elif mat == "shield":
        col = pal.glow
        edge = px in (0, fw - 1) or py in (0, fh - 1)
        diag = px == py or px + py == fw - 1
        if fw > 2 and fh > 2:
            if edge:
                gcol, ga = pal.glow, 255
            elif diag:
                col = mul(pal.glow, 0.7)
                gcol, ga = col, 200
            elif (px + py) % 2 == 0:
                col = mul(pal.glow, 0.35)
                gcol, ga = col, 110
            else:
                alpha = 0
        else:
            gcol, ga = pal.glow, 255
    else:
        col = (255, 0, 255)
    return col, alpha, gcol, ga


def deco_skull(img, glow, c, rects, pal, seed):
    t = pal.tier
    fx, fy, fw, fh = rects["front"]  # 5 wide, 10 tall
    cx = fx + fw // 2
    top, bot = {1: (3, 7), 2: (2, 8), 3: (1, 9), 4: (0, 9)}[t]
    for y in range(fy + top - 1, fy + bot + 2):  # a black ring round the slit
        for dx in (-1, 0, 1):
            if fy <= y < fy + fh:
                put(img, cx + dx, y, (4, 3, 8))
    for y in range(fy + top, fy + bot + 1):
        mid = (top + bot) / 2
        fade = 1 - abs((y - fy) - mid) / ((bot - top) / 2 + 1)
        k = [0.55, 0.8, 1.0, 1.0][t - 1] * (0.55 + 0.45 * fade)
        col = tuple(int(v * k) for v in pal.glow)
        put(img, cx, y, col)
        put(glow, cx, y, col)
        if t >= 3:  # a wider slit with a hot centre
            for dx in (-1, 1):
                col2 = tuple(int(v * 0.45 * (0.5 + fade)) for v in pal.glow)
                put(img, cx + dx, y, col2)
                put(glow, cx + dx, y, col2)
    if t == 4:  # cracks of light running out of the slit
        for dy, dxs in ((2, (-2, -1)), (4, (1, 2)), (6, (-2, -1)), (8, (1, 2))):
            for dx in dxs:
                put(img, cx + dx, fy + dy, pal.vein)
                put(glow, cx + dx, fy + dy, pal.vein)
    bx, by, bw, bh = rects["back"]
    for k in range(4 + t):  # a seam down the back of the head
        put(img, bx + bw // 2 + (k % 2), by + 1 + k, mul(pal.glow, 0.4))
        if t >= 2:
            put(glow, bx + bw // 2 + (k % 2), by + 1 + k, mul(pal.glow, 0.6))


def deco_plate(img, glow, c, rects, pal, seed):
    # the chest cracks open around the core: glowing lips on both long edges of the front, plus a jagged crack
    t = pal.tier
    fx, fy, fw, fh = rects["front"]
    for py in range(fh):
        for px in (0, fw - 1):
            if (py * 5 + px + seed) % 3 != 0:
                col = tuple(int(v * [0.35, 0.6, 0.8, 1.0][t - 1]) for v in pal.glow)
                put(img, fx + px, fy + py, col)
                put(glow, fx + px, fy + py, col)
    x = fw // 2
    for py in range(1, fh - 1):
        r = _h(py, fx, seed)
        x = max(0, min(fw - 1, x + (1 if r > 0.6 else -1 if r < 0.3 else 0)))
        if t >= 2:
            put(img, fx + x, fy + py, pal.vein)
            put(glow, fx + x, fy + py, pal.vein, 200 if t < 4 else 255)


def deco_tile(img, glow, c, rects, pal, seed):
    # only the front (outer) face of a shield facet is drawn; the back stays empty
    fx, fy, fw, fh = rects["back"]
    for py in range(fh):
        for px in range(fw):
            put(img, fx + px, fy + py, (0, 0, 0), 0)
            put(glow, fx + px, fy + py, (0, 0, 0), 0)


DECOS = {"skull": deco_skull, "plate": deco_plate, "tile": deco_tile}


def paint(tier):
    pal = Palette(tier)
    img = Image.new("RGBA", (SIZE, SIZE), (0, 0, 0, 0))
    glow = Image.new("RGBA", (SIZE, SIZE), (0, 0, 0, 0))
    for c in CUBES:
        if c["share"] is None:
            paint_cube(img, glow, c, pal)
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

    def emit(p, parent_var, indent):
        pad = "        " + "    " * indent
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
        out.append(f'{pad}PartDefinition {var} = {parent_var}.addOrReplaceChild("{p["name"]}",')
        out.extend(b)
        out.append(f"{pad}        , {pose});")
        for ch in children.get(p["name"], []):
            emit(ch, var, indent)

    for p in children.get("root", []) + children.get(None, []):
        emit(p, "root", 0)
    body = "\n".join(out)
    tiers = ",\n".join(f'            Map.entry("{p["name"]}", {p["tier"]})' for p in PARTS if p["tier"] > 1)
    return f"""package com.skyrpg.combat.client.slayer;

import java.util.Map;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;

/**
 * The Void Stalker's cubes and their UVs. GENERATED by tools/art/generate_void_stalker.py from the same cube
 * table the textures are painted from: edit the table there, never this file.
 */
final class VoidStalkerLayout {{
    /** Number of shield facet parts (tile0..). */
    static final int SHIELD_TILES = {SHIELD_TILES};

    /** Parts that only exist from this tier on (everything else is always shown). */
    static final Map<String, Integer> MIN_TIER = Map.ofEntries(
{tiers});

    private VoidStalkerLayout() {{
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
        img.save(TEX_DIR / f"void_stalker_model_t{tier}.png")
        glow.save(TEX_DIR / f"void_stalker_model_t{tier}_glow.png")
    JAVA_OUT.write_text(java(), encoding="utf-8")
    print("wrote textures and", JAVA_OUT.name)


if __name__ == "__main__":
    main()
