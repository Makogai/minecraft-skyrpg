#!/usr/bin/env python3
"""Draws the Grave Tyrant slayer boss's model textures and writes its model definition.

    python tools/art/generate_tyrant.py

The CUBES table below is the single source of truth: it lists every cube of the model (part, position, size,
material), packs them into a 256x128 texture with Minecraft's standard box layout, paints that texture, and
writes the Java that builds the LayerDefinition (GraveTyrantLayout.java), so the cubes and their UVs cannot
drift apart. Outputs:

    combat/src/main/resources/assets/skyrpg_combat/textures/entity/slayer/grave_tyrant_model_t1..t4.png
    ... grave_tyrant_model_t1_glow..t4_glow.png   (only the glowing pixels; drawn fullbright by GraveTyrantRenderer)
    combat/src/client/java/com/skyrpg/combat/client/slayer/GraveTyrantLayout.java

Box layout for a cube at texOffs(u, v) of size (w, h, d), as vanilla maps it (model -z is the front):
    top (u+d, v)  bottom (u+d+w, v)           each w x d
    row at y = v+d:  -x side (u, d wide) | front -z (u+d, w) | +x side (u+d+w, d) | back +z (u+2d+w, w)
Left-hand cubes reuse the right-hand cube's UVs and are mirrored in the model.
"""
import math
import random
import sys
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parents[2]
TEX_DIR = ROOT / "combat/src/main/resources/assets/skyrpg_combat/textures/entity/slayer"
JAVA_OUT = ROOT / "combat/src/client/java/com/skyrpg/combat/client/slayer/GraveTyrantLayout.java"
W = 256  # texture width / height (the layout is generated from these)
H = 128
SIZE = W
TIERS = (1, 2, 3, 4)

# ------------------------------------------------------------------------------------------------ cube table
# Parts form a tree; pivots are relative to the parent (model pixels, y down, feet at y=24 for the root).
# Cube: id, material, (x, y, z), (w, h, d) and optional keywords:
#   mirror=True        mirrored copy; must give share=<id of the cube it mirrors> (same size)
#   share=<id>         use that cube's UVs
#   tier=N             minimum slayer tier that shows this cube (default: its part's tier)
# part(..., tier=N) hides the whole part (and its children) below tier N. A cube whose tier is above its part's
# tier is moved into a generated child part "<part>_tN" so the model can toggle it; every tier's texture paints
# every cube, the model just hides what the tier does not show.
PARTS = []  # dicts: name, parent, pivot, rot, tier
CUBES = []  # dicts


def part(name, parent, pivot, rot=(0, 0, 0), tier=1):
    PARTS.append(dict(name=name, parent=parent, pivot=pivot, rot=rot, tier=tier))


def cube(pname, cid, mat, pos, size, share=None, mirror=False, **kw):
    CUBES.append(dict(part=pname, id=cid, mat=mat, pos=pos, size=size, share=share, mirror=mirror, **kw))


part("hips", None, (0, 12, 0))
part("shroud_front", "hips", (0, -1, -7))
cube("shroud_front", "sh_front", "linen", (-8, 0, 0), (16, 11, 1))
part("shroud_strip", "hips", (0, -1, -8.2))
cube("shroud_strip", "sh_strip", "linen_dark", (-3, 0, 0), (6, 12, 1))
part("shroud_back", "hips", (0, -1, 6))
cube("shroud_back", "sh_back", "linen", (-8, 0, 0), (16, 11, 1), share="sh_front")
# T3: extra torn strips hanging off the back of the shroud
part("shroud_rags", "shroud_back", (0, 0, 1.2), tier=3)
cube("shroud_rags", "rag_a", "linen_dark", (-7, 0, 0), (4, 15, 1))
cube("shroud_rags", "rag_b", "linen_dark", (3, 0, 0), (4, 13, 1))
cube("shroud_rags", "rag_c", "linen_dark", (-2, 0, 0.5), (3, 16, 1))
part("shroud_right", "hips", (-9.5, -1, 0))
cube("shroud_right", "sh_right", "linen", (0, 0, -5), (1, 10, 10))
part("shroud_left", "hips", (8.5, -1, 0))
cube("shroud_left", "sh_left", "linen", (0, 0, -5), (1, 10, 10), share="sh_right", mirror=True)

part("waist", "hips", (0, 0, 0))
part("torso", "waist", (0, 0, 0))
cube("torso", "chest", "flesh", (-10, -19, -2), (20, 13, 8), deco="chest")
cube("torso", "collar", "flesh_dark", (-10, -19, -6), (20, 3, 4))
cube("torso", "belly", "flesh", (-8, -6, -5), (16, 6, 10))
cube("torso", "hump", "flesh_dark", (-8, -24, 0), (16, 5, 8))
part("ribs", "waist", (0, 0, 0))
cube("ribs", "rib_r0", "bone", (-9, -16, -6), (6, 1, 4))
cube("ribs", "rib_l0", "bone", (3, -16, -6), (6, 1, 4), share="rib_r0", mirror=True)
cube("ribs", "rib_r1", "bone", (-9, -13, -6), (6, 1, 4), share="rib_r0")
cube("ribs", "rib_l1", "bone", (3, -13, -6), (6, 1, 4), share="rib_r0", mirror=True)
cube("ribs", "rib_r2", "bone", (-9, -10, -6), (6, 1, 4), share="rib_r0")
cube("ribs", "rib_l2", "bone", (3, -10, -6), (6, 1, 4), share="rib_r0", mirror=True)
cube("ribs", "stern_r", "bone", (-4, -17, -6), (1, 9, 1))
cube("ribs", "stern_l", "bone", (3, -17, -6), (1, 9, 1), share="stern_r", mirror=True)
part("heart", "waist", (0, -13, -2))
cube("heart", "heart", "heart", (-3, -3, -1), (6, 6, 3), glow="all")
part("spine", "waist", (0, 0, 0))
# vertical spikes on the hump and shoulders, and vertebrae pointing back
for i, (sx, sy, sz, sh) in enumerate([(0, -24, 3, 6), (-5, -24, 2, 4), (5, -24, 2, 4)]):
    cube("spine", f"spk{i}_b", "bone", (sx - 1.5, sy - 3, sz), (3, 3, 3), share="spk0_b" if i else None)
    cube("spine", f"spk{i}_t", "bone", (sx - 0.5, sy - 3 - sh, sz + 1), (1, sh, 1), share=None if i == 0 else None, sk=f"tip{sh}")
for i, (vy, vz) in enumerate([(-21, 8), (-17, 6), (-13, 6), (-9, 6)]):
    cube("spine", f"vert{i}_b", "bone", (-1.5, vy - 1.5, vz), (3, 3, 3), share="spk0_b")
    cube("spine", f"vert{i}_t", "bone", (-0.5, vy - 0.5, vz + 3), (1, 1, 3), sk="vtip")
# T3: big bone spikes down the back and on the hump, and a second tombstone strapped across the back
for i, sx in enumerate((-6, 6)):
    cube("spine", f"big{i}_b", "bone", (sx - 2, -23, 6), (4, 4, 4), share="big0_b" if i else None, tier=3)
    cube("spine", f"big{i}_t", "bone", (sx - 1, -22, 10), (2, 2, 6), share="big0_t" if i else None, tier=3)
cube("spine", "bigtop_b", "bone", (-2, -28, 2), (4, 4, 4), share="big0_b", tier=3)
cube("spine", "bigtop_t", "bone", (-1, -38, 3), (2, 10, 2), tier=3)
part("back_stone", "waist", (0, 0, 0), tier=3)
cube("back_stone", "bs_slab", "stone", (-5, -17, 9), (10, 14, 3), deco="slab2")
cube("back_stone", "bs_strap_a", "bandage", (-6, -15, 8.5), (12, 1, 4))
cube("back_stone", "bs_strap_b", "bandage", (-6, -8, 8.5), (12, 1, 4), share="bs_strap_a")
cube("back_stone", "bs_arch", "stone", (-4, -19, 9), (8, 2, 3))
part("head", "waist", (0, -15, -4))
cube("head", "skull", "skull", (-4, -8, -4), (8, 6, 8), deco="skull")
cube("head", "brow", "skull_dark", (-4, -7, -5), (8, 2, 1))
cube("head", "teeth_u", "bone", (-3, -2, -4), (6, 1, 1), deco="teeth")
part("jaw", "head", (0, -1, 1.5))
cube("jaw", "jaw", "skull", (-3, 0, -6), (6, 2, 8), deco="jaw")
cube("jaw", "fang_r", "bone", (-2.5, -2, -6), (1, 2, 1))
cube("jaw", "fang_l", "bone", (1.5, -2, -6), (1, 2, 1), share="fang_r", mirror=True)
part("crown", "head", (0, -6, 0), tier=4)
cube("crown", "crown_ring", "gold", (-5, -3, -5), (10, 3, 10), deco="crown", glow="gems")
for i, (cx, cy, cz, w, h, d) in enumerate([(-5, -7, -5, 2, 4, 2), (3, -7, -5, 2, 4, 2), (-5, -7, 3, 2, 4, 2), (3, -7, 3, 2, 4, 2)]):
    cube("crown", f"crown_p{i}", "gold", (cx, cy, cz), (w, h, d), share="crown_p0" if i else None)
cube("crown", "crown_front", "gold", (-1, -8, -5), (2, 5, 1))
cube("crown", "crown_back", "gold", (-1, -8, 4), (2, 5, 1), share="crown_front")
cube("crown", "crown_side_r", "gold", (-5, -7, -1), (1, 4, 2))
cube("crown", "crown_side_l", "gold", (4, -7, -1), (1, 4, 2), share="crown_side_r", mirror=True)

part("right_arm", "waist", (-14, -15, -1))
cube("right_arm", "r_delt", "flesh", (-5, -4, -5), (10, 7, 10))
cube("right_arm", "r_upper", "flesh_dark", (-4, 3, -4), (8, 9, 8))
cube("right_arm", "r_pauld", "iron", (-6, -6, -6), (12, 3, 12), deco="pauldron", tier=2)
cube("right_arm", "r_dome", "iron", (-4, -8, -4), (8, 2, 8), tier=2)
cube("right_arm", "r_horn", "iron_dark", (-1, -12, -1), (2, 4, 2), tier=2)
cube("right_arm", "r_trim", "gold", (-6.5, -4.5, -6.5), (13, 1, 13), deco="trim", tier=4)
for i, (sx, sz) in enumerate([(-5, -5), (3, -5), (-5, 3), (3, 3)]):
    cube("right_arm", f"r_spk{i}", "bone", (sx, -11, sz), (2, 5, 2), share="r_spk0" if i else None, tier=3)
part("right_forearm", "right_arm", (0, 11, 0))
cube("right_forearm", "r_fore", "bandage", (-4, 0, -4), (8, 9, 8))
cube("right_forearm", "r_cuff", "iron", (-4.5, 5, -4.5), (9, 2, 9), tier=2)
cube("right_forearm", "r_fist", "flesh", (-4, 8, -4), (8, 4, 8))
cube("right_forearm", "r_dangle", "chain", (4.5, 10.5, -0.5), (1, 6, 1), tier=2)
part("club", "right_forearm", (0, 10, 0), rot=(1.45, 0, 0))
cube("club", "c_handle", "bandage", (-1.5, -8, -1.5), (3, 14, 3))
cube("club", "c_slab", "stone", (-6, -24, -2), (12, 16, 4), deco="slab", glow="slab")
cube("club", "c_arch", "stone", (-5, -27, -2), (10, 3, 4))
cube("club", "c_cap", "stone", (-3, -29, -2), (6, 2, 4))
cube("club", "c_collar", "iron", (-3, -10, -3), (6, 3, 6), tier=2)
cube("club", "c_chain", "chain", (-6.5, -15, -2.5), (13, 1, 5), tier=2)
part("club_chain2", "club", (0, 0, 0), tier=4)
cube("club_chain2", "c_chain2a", "chain", (-6.5, -20, -2.5), (13, 1, 5), share="c_chain")
cube("club_chain2", "c_chain2b", "chain", (-6.5, -11, -2.5), (13, 1, 5), share="c_chain")

part("left_arm", "waist", (14, -14, -1))
cube("left_arm", "l_shoulder", "flesh", (-3, -3, -3), (6, 6, 6))
cube("left_arm", "l_upper", "flesh_dark", (-3, 2, -3), (6, 8, 6))
cube("left_arm", "l_spike", "bone", (-1, -8, -1), (2, 5, 2))
cube("left_arm", "l_big0", "bone", (-4, -9, -1.5), (2, 6, 2), tier=3)
cube("left_arm", "l_big1", "bone", (2, -9, -1.5), (2, 6, 2), share="l_big0", tier=3)
part("left_forearm", "left_arm", (0, 9, 0))
cube("left_forearm", "l_fore", "flesh", (-3, 0, -3), (6, 7, 6))
cube("left_forearm", "l_hand", "flesh_dark", (-3, 7, -3), (6, 2, 6))
for i, cx in enumerate([-2.5, -1, 0.5, 2]):
    cube("left_forearm", f"claw{i}", "bone", (cx, 9, -0.5), (1, 7, 1), share="claw0" if i else None)
    cube("left_forearm", f"clawt{i}", "bone", (cx, 16, -1.5), (1, 2, 1), share="clawt0" if i else None)

part("right_leg", "root", (-5, 12, 0))
cube("right_leg", "r_leg", "bandage", (-4, 0, -4), (8, 9, 8))
cube("right_leg", "r_foot", "flesh_dark", (-4.5, 9, -6), (9, 3, 10), deco="foot")
part("left_leg", "root", (5, 12, 0))
cube("left_leg", "l_leg", "bandage", (-4, 0, -4), (8, 9, 8), share="r_leg", mirror=True)
cube("left_leg", "l_foot", "flesh_dark", (-4.5, 9, -6), (9, 3, 10), share="r_foot", mirror=True)



def expand():
    """Moves cubes above their part's tier into generated child parts, and records each part's tier path."""
    by_name = {p["name"]: p for p in PARTS}
    for c in CUBES:
        pt = by_name[c["part"]]
        if c.get("tier") is None:
            c["tier"] = pt["tier"]
        elif c["tier"] > pt["tier"]:
            sub = f"{pt['name']}_t{c['tier']}"
            if sub not in by_name:
                np_ = dict(name=sub, parent=pt["name"], pivot=(0, 0, 0), rot=(0, 0, 0), tier=c["tier"])
                PARTS.append(np_)
                by_name[sub] = np_
            c["part"] = sub

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
            # tallest-first with a random jitter: the shelf packer wastes less on some orders
            order = sorted(uniq, key=lambda c: -net(c)[1] + rng.uniform(-4, 4))
        x = y = shelf = 0
        placed = []
        for c in order:
            w, h = net(c)
            if x + w > W:
                x, y, shelf = 0, y + shelf, 0
            placed.append((c, x, y))
            x += w
            shelf = max(shelf, h)
        rows = y + shelf
        if best is None or rows < best[0]:
            best = (rows, placed)
    shelf_rows, placed = best
    for c, x, y in placed:
        c["u"], c["v"] = x, y
    if shelf_rows > H:
        sys.exit(f"cubes need {shelf_rows} rows of texture, only {H} available")
    y, shelf = shelf_rows, 0
    for c in CUBES:
        if c["share"] is not None:
            o = by_id[c["share"]]
            while o["share"] is not None:
                o = by_id[o["share"]]
            if o["size"] != c["size"]:
                sys.exit(f"{c['id']} shares UVs with {o['id']} but their sizes differ")
            c["u"], c["v"] = o["u"], o["v"]
    return y + shelf


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


# ------------------------------------------------------------------------------------------------ painting
FACES = ("top", "bottom", "left", "front", "right", "back")
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
    """Per-tier look. tier 1 rotting grey-green, 2 plague (rust, dark bandages, faint heart), 3 dread (veins, runes,
    black shroud), 4 king (blackened skin, emerald cracks, gold)."""

    def __init__(self, tier):
        self.tier = tier
        t = tier
        self.t4 = t == 4
        self.flesh = [hexc(x) for x in (
            ("565f4c", "6a7660", "7e8a74", "949f8a"), ("4a5a3e", "5e7050", "708260", "869678"),
            ("36443a", "465848", "58705a", "6a826a"), ("1c2420", "28322c", "344038", "424e46"))[t - 1]]
        self.flesh_dark = [hexc(x) for x in (
            ("3e4638", "4c5644", "5c6852", "6c7a62"), ("34402c", "445438", "566a48", "687e58"),
            ("242e28", "303c34", "3e4c42", "4c5c50"), ("141a18", "1e2622", "2a342e", "36423c"))[t - 1]]
        self.bruise = hexc(("5a4e5e", "5e4a62", "46385a", "3a2a54")[t - 1])
        self.glow = ((150, 215, 70), (120, 230, 80), (80, 250, 110), (60, 255, 150))[t - 1]
        self.glow_dim = tuple(int(v * 0.33) for v in self.glow)
        # how strong each emissive feature is (0 = none)
        self.eye = (0.3, 0.5, 0.8, 1.0)[t - 1]
        self.heart_glow = (0.0, 0.35, 0.65, 1.0)[t - 1]
        self.rune = (0.0, 0.45, 0.85, 1.0)[t - 1]
        self.vein = (0.0, 0.0, 0.03, 0.05)[t - 1]  # half-width of the glowing veins/cracks in the skin noise
        self.bone = [hexc(x) for x in (
            ("a8a088", "c4bda0", "ddd5b8", "f0e8cc"), ("a0987f", "bcb598", "d4ccb0", "e8e0c4"),
            ("8c8a78", "a8a590", "c0bca4", "d6d2b8"), ("848474", "9c9c8a", "b4b4a0", "c8c8b4"))[t - 1]]
        self.iron = [hexc(x) for x in ("3c2c24", "5e3c28", "86502c", "a8683a")]
        if t >= 3:
            self.iron = [hexc(x) for x in ("2c211c", "45301f", "65401f", "835230")]
        if t == 4:
            self.iron = [hexc(x) for x in ("1c1c20", "2c2c32", "403c40", "58525a")]
        self.iron_dark = [hexc(x) for x in ("22201f", "34302e", "4a423c", "66584a")]
        self.stone = [hexc(x) for x in (
            ("5c625e", "747a76", "8e948e", "a6aca4"), ("565c56", "6c726a", "848a80", "9aa094"),
            ("484e4c", "5c6260", "727876", "868c88"), ("363a3a", "464a4a", "585e5c", "6a706e"))[t - 1]]
        self.moss = hexc(("3e6a38", "3e6a38", "2e5a34", "1e4a30")[t - 1])
        self.linen = [hexc(x) for x in (
            ("8c7e64", "a8987a", "c2b290", "d8c8a4"), ("847860", "9c8e72", "b4a688", "c8ba98"),
            ("1c1a1c", "262426", "322e32", "403c40"), ("0e0e12", "16161a", "1e1e24", "28282e"))[t - 1]]
        self.tear = (0.0, 0.04, 0.1, 0.14)[t - 1]  # extra ragged-hole bias of the shroud
        self.stain = hexc(("4e3a24", "4a3620", "2a2224", "1a1618")[t - 1])
        self.band = [hexc(x) for x in (
            ("a89c78", "c4b88e"), ("7c6c4a", "92825a"), ("524834", "665a40"), ("2e2a24", "3c362c"))[t - 1]]
        self.band_dark = [hexc(x) for x in (
            ("7c6e50", "92845f"), ("56482e", "685838"), ("342c1e", "443a28"), ("1c1a16", "26221c"))[t - 1]]
        self.chain = [hexc(x) for x in ("1c1c20", "34343a", "50505a", "72727c")]
        self.gold = [hexc(x) for x in ("5c3c14", "8a5e1e", "b8862c", "e0b648")]
        self.heart = [hexc(x) for x in (
            ("34402c", "46543a", "58684a", "6c7c5c"), ("3a6a14", "5c9020", "86b834", "b0dc58"),
            ("1a6a2a", "2a9a3c", "4cc860", "8af0a0"), ("0c5a30", "18883e", "30c060", "70f0a0"))[t - 1]]
        self.blood = hexc(("5a1c1c", "5a1c1c", "4a1a22", "3a1020")[t - 1])


def pick(ramp, t):
    t = max(0.0, min(0.9999, t))
    return ramp[int(t * len(ramp))]


def paint_cube(img, glow_img, c, pal, rng_seed):
    rects = face_rects(c)
    mat = c["mat"]
    seed = rng_seed + sum(ord(ch) for ch in c["id"])
    deco = c.get("deco")
    for face, (fx, fy, fw, fh) in rects.items():
        for py in range(fh):
            for px in range(fw):
                X, Y = fx + px, fy + py
                col, alpha = base_pixel(mat, pal, X, Y, px, py, fw, fh, face, seed, c)
                # chiselled edge: lit top-left, dark bottom-right, to separate neighbouring cubes
                k = FACE_LIGHT[face]
                if face in ("left", "front", "right", "back"):
                    if py == 0:
                        k *= 1.08
                    if py == fh - 1:
                        k *= 0.82
                    if px == 0 or px == fw - 1:
                        k *= 0.94
                col = mul(col, k)
                img.putpixel((X, Y), (int(col[0]), int(col[1]), int(col[2]), alpha))
                if pal.vein > 0 and mat in ("flesh", "flesh_dark") and alpha:
                    # glowing veins (tier III) / emerald cracks (tier IV) running through the skin
                    r1 = abs(vnoise(X, Y, 6.0, seed + 50) - 0.5)
                    r2 = abs(vnoise(X + 17, Y + 5, 9.0, seed + 61) - 0.5) if pal.tier == 4 else 1.0
                    if r1 < pal.vein or r2 < pal.vein * 0.8:
                        k2 = 0.55 if pal.tier == 3 else 0.9
                        gc = tuple(int(v * k2) for v in pal.glow)
                        img.putpixel((X, Y), (int(gc[0] * 0.45), int(gc[1] * 0.55), int(gc[2] * 0.4), 255))
                        glow_img.putpixel((X, Y), (gc[0], gc[1], gc[2], 255))
    if deco:
        DECOS[deco](img, glow_img, c, rects, pal, seed)
    if c.get("glow") == "all":
        paint_heart(img, glow_img, c, rects, pal, seed)


def base_pixel(mat, pal, X, Y, px, py, fw, fh, face, seed, c):
    n1 = vnoise(X, Y, 4.0, seed)
    n2 = vnoise(X, Y, 2.0, seed + 7)
    n3 = _h(X, Y, seed + 3)
    alpha = 255
    if mat in ("flesh", "flesh_dark"):
        ramp = pal.flesh if mat == "flesh" else pal.flesh_dark
        col = pick(ramp, 0.25 + 0.5 * n1 + 0.25 * (n3 - 0.5) + 0.2 * (n2 - 0.5))
        if vnoise(X + 40, Y, 3.0, seed + 11) > 0.72:
            col = mix(col, pal.bruise, 0.6)
        if vnoise(X, Y + 90, 5.0, seed + 13) > 0.78:
            col = mix(col, pal.blood, 0.55)
        if n3 > 0.93:
            col = mul(col, 0.65)
        # stitched seams and raised veins
        if (X * 3 + Y) % 23 == 0 and vnoise(X, Y, 6.0, seed + 5) > 0.5:
            col = mix(col, (30, 36, 28), 0.7)
    elif mat in ("bone", "skull", "skull_dark"):
        t = 0.3 + 0.5 * n1 + 0.2 * (n3 - 0.5)
        if mat == "skull_dark":
            t -= 0.25
        col = pick(pal.bone, t)
        if vnoise(X, Y, 3.0, seed + 21) > 0.8:
            col = mul(col, 0.8)
        if (X + 2 * Y) % 11 == 0 and n1 > 0.55:
            col = mul(col, 0.7)  # hairline cracks
    elif mat in ("iron", "iron_dark"):
        ramp = pal.iron if mat == "iron" else pal.iron_dark
        col = pick(ramp, 0.2 + 0.7 * vnoise(X, Y, 3.0, seed) + 0.25 * (n3 - 0.5))
        if mat == "iron" and vnoise(X, Y, 2.0, seed + 5) > 0.7:
            col = mix(col, pal.iron_dark[1], 0.5)
        if mat == "iron" and (px % 4 == 1 and py % 4 == 1 and fw > 6):
            col = pal.iron_dark[3]  # rivets
    elif mat == "gold":
        col = pick(pal.gold, 0.25 + 0.6 * vnoise(X, Y, 3.0, seed) + 0.3 * (n3 - 0.5))
        if vnoise(X, Y, 2.0, seed + 5) > 0.76:
            col = mix(col, (60, 40, 20), 0.6)
    elif mat == "stone":
        col = pick(pal.stone, 0.2 + 0.55 * n1 + 0.3 * (n3 - 0.5))
        if vnoise(X, Y, 4.0, seed + 9) > 0.66 and (face in ("top", "bottom") or py > fh * 0.55):
            col = mix(col, pal.moss, 0.7)
        if (X * 5 + Y * 3) % 17 == 0 and n2 > 0.5:
            col = mul(col, 0.65)
    elif mat in ("linen", "linen_dark"):
        k = 1.0 if mat == "linen" else 0.78
        col = mul(pick(pal.linen, 0.25 + 0.55 * n1 + 0.3 * (n3 - 0.5)), k)
        if py % 3 == 0:
            col = mul(col, 0.94)  # weave
        if vnoise(X, Y, 3.5, seed + 31) > 0.7:
            col = mix(col, pal.stain, 0.55)
        if face in ("front", "back", "left", "right"):
            ragged = fh - 1 - py  # distance from the bottom edge
            hole = vnoise(X, Y, 2.5, seed + 41)
            if (ragged < 3 and hole > 0.45 - pal.tear + 0.12 * ragged) or hole > 0.84 - pal.tear * 0.6 or (ragged < 6 and (px * 7 + seed) % 5 == 0 and ragged < 1 + (px * 3 + seed) % 5):
                alpha = 0
            if fh >= 10 and py < 2:
                alpha = 255
    elif mat == "bandage":
        band = ((py + px // 3) // 2) % 2
        col = pick(pal.band if band else pal.band_dark, 0.3 + 0.5 * n1 + 0.2 * n3)
        if (py + px // 3) % 2 == 0 and band:
            col = mul(col, 0.9)
        if vnoise(X, Y, 3.0, seed + 5) > 0.78:
            col = mix(col, pal.stain, 0.6)
        if vnoise(X + 11, Y, 3.0, seed + 8) > 0.82:
            col = mix(col, pal.blood, 0.7)
        if vnoise(X, Y + 5, 2.5, seed + 15) > 0.88:
            col = pick(pal.flesh_dark, 0.3 + 0.3 * n3)  # a gap in the wrapping
    elif mat == "chain":
        link = (px + (py % 2)) % 3
        col = pick(pal.chain, 0.15 + 0.5 * n1 + (0.25 if link == 0 else 0))
        if vnoise(X, Y, 2.5, seed + 9) > 0.82:
            col = mix(col, pal.iron[2], 0.7)
    elif mat == "heart":
        col = pick(pal.heart, 0.3 + 0.6 * vnoise(X, Y, 2.5, seed))
    else:
        col = (255, 0, 255)
    return col, alpha


def paint_heart(img, glow_img, c, rects, pal, seed):
    if pal.heart_glow <= 0:
        return  # a dead, dull heart on tier I
    for face, (fx, fy, fw, fh) in rects.items():
        for py in range(fh):
            for px in range(fw):
                X, Y = fx + px, fy + py
                t = 0.35 + 0.65 * vnoise(X, Y, 2.0, seed + 2)
                if (px + py * 2 + seed) % 5 == 0:
                    t *= 0.6  # veins
                col = pick(pal.heart, t)
                k = pal.heart_glow
                glow_img.putpixel((X, Y), (int(col[0] * k), int(col[1] * k), int(col[2] * k), 255))


def gl(pal, k):
    """The tier's glow colour scaled by k * the tier's rune strength."""
    return tuple(int(v * k * pal.rune) for v in pal.glow)


def put(img, x, y, col, a=255):
    if 0 <= x < W and 0 <= y < H:
        img.putpixel((x, y), (int(col[0]), int(col[1]), int(col[2]), a))


def deco_chest(img, glow, c, rects, pal, seed):
    # the open ribcage: a dark wet cavity behind the heart, with torn flesh around it
    fx, fy, fw, fh = rects["front"]
    for py in range(fh):
        for px in range(fw):
            if 6 <= px <= 13 and 2 <= py <= 11:
                edge = px in (6, 13) or py in (2, 11)
                col = (50, 14, 18) if not edge else (88, 34, 34)
                col = mix(col, (24, 6, 10), vnoise(fx + px, fy + py, 2.0, seed))
                put(img, fx + px, fy + py, col)
                if pal.tier >= 2:  # the rib cage starts to glow from within
                    put(glow, fx + px, fy + py, tuple(int(v * (0.1 + 0.07 * pal.tier)) for v in pal.glow))
    # claw marks across the pecs
    for k in range(3):
        for s in range(8):
            put(img, fx + 1 + k * 2 + s // 3, fy + 1 + s, mul(pal.flesh_dark[0], 0.7))
            put(img, fx + 14 + k * 2 + s // 3, fy + 3 + s, pal.blood)
    # the back: knotted spine scars
    bx, by, bw, bh = rects["back"]
    for py in range(bh):
        put(img, bx + bw // 2, by + py, mul(pal.flesh_dark[0], 0.7))
        if py % 3 == 0:
            for dx in (-2, -1, 1, 2):
                put(img, bx + bw // 2 + dx, by + py, mul(pal.flesh_dark[0], 0.8))


def deco_skull(img, glow, c, rects, pal, seed):
    fx, fy, fw, fh = rects["front"]
    dark = (14, 18, 12)
    eye = lambda k: tuple(int(v * k * pal.eye) for v in pal.glow)
    for ex in (1, 5):
        for py in range(3, 6):
            for px in range(ex, ex + 3):
                put(img, fx + px, fy + py, dark if (px, py) != (ex + 1, 4) else pal.glow_dim)
        # the glowing pupil, 2 wide x 1 tall (2 tall on tier IV)
        for px in (ex, ex + 1):
            put(glow, fx + px, fy + 4, eye(1.0))
            if pal.tier == 4:
                put(glow, fx + px, fy + 3, eye(0.9))
        put(glow, fx + ex + 1, fy + 3, eye(0.7))
    # nose hole
    for px, py in ((3, 5), (4, 5)):
        put(img, fx + px, fy + py, dark)
    # cracks on the cranium
    tx, ty, tw, th = rects["top"]
    for k in range(5):
        put(img, tx + 3 + k, ty + 2 + (k * 2) % 3, mul(pal.bone[0], 0.6))
    # glowing seam on the back of the skull
    bx, by, bw, bh = rects["back"]
    for k in range(4):
        put(img, bx + 3 + (k % 2), by + 1 + k, pal.glow_dim)
        if pal.tier >= 2:
            put(glow, bx + 3 + (k % 2), by + 1 + k, tuple(int(v * 0.6 * pal.eye) for v in pal.glow))


def deco_teeth(img, glow, c, rects, pal, seed):
    fx, fy, fw, fh = rects["front"]
    for px in range(fw):
        put(img, fx + px, fy, pal.bone[3] if px % 2 == 0 else mul(pal.bone[1], 0.7))


def deco_jaw(img, glow, c, rects, pal, seed):
    tx, ty, tw, th = rects["top"]
    for py in range(th):
        for px in range(tw):
            put(img, tx + px, ty + py, mix((70, 20, 24), (40, 10, 14), vnoise(tx + px, ty + py, 2.0, seed)))
    # a faint green throat glow (tier III and up)
    for px in range(1, tw - 1):
        for py in range(3, th - 1):
            if pal.tier >= 3:
                put(glow, tx + px, ty + py, tuple(int(v * 0.3 * pal.eye) for v in pal.glow), 200)
    fx, fy, fw, fh = rects["front"]
    for px in range(fw):
        put(img, fx + px, fy, mul(pal.bone[1], 0.8))


def deco_crown(img, glow, c, rects, pal, seed):
    for face in ("front", "back", "left", "right"):
        fx, fy, fw, fh = rects[face]
        cx = fx + fw // 2
        for px in range(fw):
            put(img, fx + px, fy, pal.gold[3])
            put(img, fx + px, fy + fh - 1, pal.gold[0])
        # an emerald set in the band
        for dx in (-1, 0):
            for dy in (0, 1):
                put(img, cx + dx, fy + 1 + dy, (30, 150, 80) if pal.t4 else (60, 120, 50))
                put(glow, cx + dx, fy + 1 + dy, pal.glow)
        for dx in (-2, 1):
            put(img, cx + dx, fy + 1, pal.gold[3])


def deco_pauldron(img, glow, c, rects, pal, seed):
    # a riveted rim and a crude plate seam
    for face in ("front", "back", "left", "right"):
        fx, fy, fw, fh = rects[face]
        for px in range(fw):
            put(img, fx + px, fy + fh - 1, pal.gold[1] if pal.t4 else pal.iron_dark[0])
            if px % 3 == 1:
                put(img, fx + px, fy + 1, pal.gold[3] if pal.t4 else pal.iron_dark[3])
    tx, ty, tw, th = rects["top"]
    for k in range(tw):
        put(img, tx + k, ty + th // 2, pal.iron_dark[0])


def deco_trim(img, glow, c, rects, pal, seed):
    # gold trim ring of the tier IV pauldron: bright lit edges
    for face in ("front", "back", "left", "right"):
        fx, fy, fw, fh = rects[face]
        for px in range(fw):
            put(img, fx + px, fy, pal.gold[3])


def deco_foot(img, glow, c, rects, pal, seed):
    fx, fy, fw, fh = rects["front"]
    for px in range(fw):
        if px % 3 != 1:
            put(img, fx + px, fy + fh - 1, pal.bone[1])  # toe nails / bone tips poking out
    tx, ty, tw, th = rects["top"]
    for py in range(th):
        for px in range(tw):
            if (px + py) % 3 == 0:
                put(img, tx + px, ty + py, pal.band[0])  # bandage strips on top of the foot


def deco_slab(img, glow, c, rects, pal, seed):
    rng = random.Random(seed)
    for face in ("front", "back"):
        fx, fy, fw, fh = rects[face]
        # carved cross, 2 px wide, with a lit edge on the upper-left and a shadowed edge below-right
        parts_ = [(5, 3, 2, 11), (3, 5, 6, 2)]
        for (cx, cy, cw, ch) in parts_:
            for py in range(ch):
                for px in range(cw):
                    put(img, fx + cx + px, fy + cy + py, mul(pal.stone[0], 0.5))
        for (cx, cy, cw, ch) in parts_:
            for px in range(cw):
                put(img, fx + cx + px, fy + cy - 1, pal.stone[3])
            for py in range(ch):
                put(img, fx + cx - 1, fy + cy + py, pal.stone[3])
        # rounded top corners are hidden by the arch; chip the lower corners
        for k in range(3):
            put(img, fx + k, fy + fh - 1 - k // 2, (0, 0, 0), 0) if False else None
        # glowing rune cracks wandering out of the cross
        starts = [(6, 14), (2, 6), (9, 6), (6, 2)]
        for sx, sy in starts:
            x, y = sx, sy
            dx = -1 if sx < 6 else 1
            dy = 1 if sy > 8 else -1
            for step in range(rng.randint(5, 9)):
                if 0 <= x < fw and 0 <= y < fh:
                    if pal.rune > 0:
                        put(img, fx + x, fy + y, pal.glow_dim)
                        put(glow, fx + x, fy + y, gl(pal, 1.0))
                    else:
                        put(img, fx + x, fy + y, mul(pal.stone[0], 0.55))  # tier I: just a dead crack
                r = rng.random()
                if r < 0.45:
                    x += dx if sx not in (6,) else rng.choice((-1, 1))
                elif r < 0.9:
                    y += dy
                else:
                    x += rng.choice((-1, 1))
                    y += dy
        # scratched runes in the corners
        for rx, ry in ((1, 1), (9, 1), (1, 12), (9, 12)):
            for k in range(3):
                if rng.random() < 0.8:
                    put(img, fx + rx + k % 2, fy + ry + k, pal.stone[0])
                    if face == "front" and k == 1:
                        put(glow, fx + rx + k % 2, fy + ry + k, gl(pal, 0.8))
    # cracks on the side faces
    for face in ("left", "right"):
        fx, fy, fw, fh = rects[face]
        for k in range(fh):
            if k % 5 == 2:
                put(img, fx + (k // 5) % fw, fy + k, mul(pal.stone[0], 0.6))


def deco_slab2(img, glow, c, rects, pal, seed):
    # the small tombstone strapped on the back: a thin cross and a rune crack
    rng = random.Random(seed)
    for face in ("front", "back"):
        fx, fy, fw, fh = rects[face]
        for (cx, cy, cw, ch) in ((4, 3, 2, 9), (2, 5, 6, 2)):
            for py in range(ch):
                for px in range(cw):
                    put(img, fx + cx + px, fy + cy + py, mul(pal.stone[0], 0.5))
                    if pal.rune > 0 and (px + py) % 2 == 0:
                        put(glow, fx + cx + px, fy + cy + py, gl(pal, 0.7))
        x, y = 8, 1
        for _ in range(8):
            if 0 <= x < fw and 0 <= y < fh:
                put(img, fx + x, fy + y, pal.glow_dim if pal.rune > 0 else mul(pal.stone[0], 0.55))
                put(glow, fx + x, fy + y, gl(pal, 1.0))
            y += 1
            x += rng.choice((-1, 0, 0))


DECOS = {"chest": deco_chest, "skull": deco_skull, "teeth": deco_teeth, "jaw": deco_jaw, "crown": deco_crown,
         "pauldron": deco_pauldron, "foot": deco_foot, "slab": deco_slab, "slab2": deco_slab2, "trim": deco_trim}


def paint(tier):
    pal = Palette(tier)
    img = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    glow = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    seed = 1  # same seed on every tier so the weathering lines up from tier to tier
    for c in CUBES:
        paint_cube(img, glow, c, pal, seed)
    return img, glow


# ------------------------------------------------------------------------------------------------ Java

def fnum(v):
    s = f"{float(v):g}"
    return s + "f"


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
        b = [f'{pad}CubeListBuilder.create()']
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
        out.append(f'{pad[:-4]}PartDefinition {var} = {parent_var}.addOrReplaceChild("{p["name"]}",')
        out.extend(b)
        out.append(f"{pad}        , {pose});")
        for ch in children.get(p["name"], []):
            emit(ch, var, indent)

    for p in children.get("root", []) + children.get(None, []):
        emit(p, "root", 0)
    body = "\n".join(out)
    paths = {}

    def path_of(name):
        if name in paths:
            return paths[name]
        p = next(q for q in PARTS if q["name"] == name)
        base = "root" if p["parent"] in (None, "root") else path_of(p["parent"])
        paths[name] = f'{base}.getChild("{name}")'
        return paths[name]

    tier_lines = []
    for p in PARTS:
        if p["tier"] > 1:
            tier_lines.append(f'        {path_of(p["name"])}.visible = tier >= {p["tier"]};')
    toggles = "\n".join(tier_lines)
    return f"""package com.skyrpg.combat.client.slayer;

import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;

/**
 * The Grave Tyrant's cubes and their UVs. GENERATED by tools/art/generate_tyrant.py from the same cube table
 * the textures are painted from: edit the table there, never this file.
 */
final class GraveTyrantLayout {{
    private GraveTyrantLayout() {{
    }}

    static LayerDefinition create() {{
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
{body}
        return LayerDefinition.create(mesh, {W}, {H});
    }}

    /** Shows only the parts the slayer tier (1 to 4) reveals; call every frame after resetPose. */
    static void applyTier(ModelPart root, int tier) {{
{toggles}
    }}
}}
"""


def main():
    expand()
    rows = pack()
    print(f"packed {len(CUBES)} cubes, {sum(1 for c in CUBES if c['share'] is None)} unique UV regions, {rows}/{H} rows used")
    TEX_DIR.mkdir(parents=True, exist_ok=True)
    for old in ("grave_tyrant_model.png", "grave_tyrant_model_glow.png", "grave_tyrant_model_t4.png", "grave_tyrant_model_t4_glow.png"):
        (TEX_DIR / old).unlink(missing_ok=True)  # pre-progression names, replaced by _t1.._t4
    for tier in TIERS:
        img, glow = paint(tier)
        # every tier paints every cube; the model hides the parts above the boss's tier
        img.save(TEX_DIR / f"grave_tyrant_model_t{tier}.png")
        glow.save(TEX_DIR / f"grave_tyrant_model_t{tier}_glow.png")
    JAVA_OUT.write_text(java(), encoding="utf-8")
    print("wrote textures and", JAVA_OUT.name)


if __name__ == "__main__":
    main()
