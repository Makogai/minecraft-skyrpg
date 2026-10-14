#!/usr/bin/env python3
"""Draws the Bone Marshal slayer boss's model textures (four tiers) and writes its model definition.

    python tools/art/generate_bone_marshal.py

The CUBES table below is the single source of truth: it lists every cube of the model (part, position, size,
material), packs them into a 128-wide texture with Minecraft's standard box layout, paints one texture per tier
and writes the Java that builds the LayerDefinition (BoneMarshalLayout.java), so cubes and UVs cannot drift apart.
Which parts show at which tier is decided in BoneMarshalModel (parts are painted in every tier's texture).
Outputs:

    combat/src/main/resources/assets/skyrpg_combat/textures/entity/slayer/bone_marshal_model_t{1..4}.png
    ... bone_marshal_model_t{1..4}_glow.png   (only the glowing pixels; drawn fullbright by BoneMarshalBossRenderer)
    combat/src/client/java/com/skyrpg/combat/client/slayer/BoneMarshalLayout.java

Box layout for a cube at texOffs(u, v) of size (w, h, d), as vanilla maps it (model -z is the front):
    top (u+d, v)  bottom (u+d+w, v)           each w x d
    row at y = v+d:  -x side (u, d wide) | front -z (u+d, w) | +x side (u+d+w, d) | back +z (u+2d+w, w)
Left-hand cubes reuse the right-hand cube's UVs and are mirrored in the model. Cube SIZES are whole pixels
(the UV maths floors them); positions may be fractional.
"""
import math
import random
import sys
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parents[2]
TEX_DIR = ROOT / "combat/src/main/resources/assets/skyrpg_combat/textures/entity/slayer"
JAVA_OUT = ROOT / "combat/src/client/java/com/skyrpg/combat/client/slayer/BoneMarshalLayout.java"
W = 128
MAXH = 256

PARTS = []
CUBES = []


def part(name, parent, pivot, rot=(0, 0, 0)):
    PARTS.append(dict(name=name, parent=parent, pivot=pivot, rot=rot))


def cube(pname, cid, mat, pos, size, share=None, mirror=False, **kw):
    CUBES.append(dict(part=pname, id=cid, mat=mat, pos=pos, size=size, share=share, mirror=mirror, **kw))


def pair(pname, cid, mat, pos, size, **kw):
    """A right cube (pos is the right-hand side, x < 0) and its mirrored left copy in the same part."""
    cube(pname, cid + "_r", mat, pos, size, **kw)
    kw2 = {k: v for k, v in kw.items() if k not in ("deco", "glow", "sk")}
    cube(pname, cid + "_l", mat, (-(pos[0] + size[0]), pos[1], pos[2]), size, share=cid + "_r", mirror=True, **kw2)


def sided(base, parent, pivot, rot, cubes):
    """Right and left parts r_<base>/l_<base> (pivot given for the right side) holding the same cubes."""
    for s in ("r", "l"):
        name = f"{s}_{base}"
        par = parent.replace("{s}", s)
        px, py, pz = pivot
        rx, ry, rz = rot
        if s == "l":
            px, ry, rz = -px, -ry, -rz
        part(name, par, (px, py, pz), (rx, ry, rz))
        for cid, mat, pos, size, kw in cubes:
            if s == "r":
                cube(name, "r_" + cid, mat, pos, size, **kw)
            else:
                kw2 = {k: v for k, v in kw.items() if k not in ("deco", "glow", "sk")}
                cube(name, "l_" + cid, mat, (-(pos[0] + size[0]), pos[1], pos[2]), size, share="r_" + cid, mirror=True, **kw2)


# ------------------------------------------------------------------------------------------------ cube table
# Feet at y=24 of the root. hips at y=10; the waist is the torso's base, everything above hangs off it with y < 0.
part("hips", None, (0, 10, 0))
cube("hips", "pelvis", "bone", (-3.5, 0, -2), (7, 3, 4))
part("belt", "hips", (0, 0, 0))
cube("belt", "belt", "leather", (-4, -2, -2.5), (8, 2, 5), deco="belt")
cube("belt", "belt_drop", "leather", (-1, 0, -3), (2, 4, 1))
part("waist", "hips", (0, 0, 0))

part("spine", "waist", (0, 0, 0))
cube("spine", "spine", "bone", (-1, -12, 0.5), (2, 12, 2))
cube("spine", "neck", "bone", (-1, -13.5, -1), (2, 3, 2))
for i, vy in enumerate((-10.5, -7.5, -4.5, -1.5)):
    cube("spine", f"vert{i}", "bone", (-1.5, vy, 1.5), (3, 1, 1), share="vert0" if i else None)
part("clavicle", "waist", (0, 0, 0))
cube("clavicle", "clav", "bone", (-6, -12, -1.5), (12, 1, 3))

# the dark breastplate behind the ribs (T1: just the dark hollow of the ribcage)
part("plate", "waist", (0, 0, 0))
cube("plate", "plate", "plate", (-3.5, -11.5, -2.5), (7, 10, 4), deco="plate")
part("ribs", "waist", (0, 0, 0))
cube("ribs", "rib0_r", "bone", (-4.5, -11, -3), (5, 1, 5), deco="rib")
cube("ribs", "rib0_l", "bone", (-0.5, -11, -3), (5, 1, 5), share="rib0_r", mirror=True)
for i, ry in enumerate((-8, -5, -2), start=1):
    cube("ribs", f"rib{i}_r", "bone", (-4.5, ry, -3), (5, 1, 5), share="rib0_r")
    cube("ribs", f"rib{i}_l", "bone", (-0.5, ry, -3), (5, 1, 5), share="rib0_r", mirror=True)
cube("ribs", "sternum", "bone", (-0.5, -11, -3.5), (1, 9, 1))

# T4 cuirass: gold-trimmed black iron frame around a window with the spectral chest fire
part("cuirass", "waist", (0, 0, 0))
cube("cuirass", "cu_top", "iron", (-4.5, -12.5, -3.6), (9, 3, 1), deco="cuirass", trim=True)
cube("cuirass", "cu_bot", "iron", (-4, -4.5, -3.6), (8, 3, 1), trim=True)
pair("cuirass", "cu_side", "iron", (-4.5, -9.5, -3.6), (3, 5, 1), trim=True)
cube("cuirass", "cu_back", "iron", (-4.5, -12.5, 1.6), (9, 10, 1), deco="cuirass_back", trim=True)
pair("cuirass", "cu_flank", "iron", (-5, -12, -2.6), (1, 9, 4), trim=True)
part("chest_fire", "waist", (0, -7, -3))
cube("chest_fire", "fire_a", "fire", (-1.5, -2.5, -0.3), (3, 5, 1), glow="all")
part("flame_tip", "chest_fire", (0, -2.5, 0))
cube("flame_tip", "fire_b", "fire", (-1, -3, -0.3), (2, 3, 1), glow="all")

part("cape", "waist", (0, -11, 2.5))
cube("cape", "cape", "cloth", (-5, 0, 0), (10, 9, 1), tatter=1, deco="cape")
part("cape_ext", "cape", (0, 9, 0))
cube("cape_ext", "cape_ext", "cloth", (-5, 0, 0), (10, 7, 1), tatter=2, deco="cape")
part("cape_ext2", "cape_ext", (0, 7, 0))
cube("cape_ext2", "cape_ext2", "cloth", (-5, 0, 0), (10, 6, 1), tatter=3, deco="cape")

part("quiver", "waist", (2, -2, 3.5), (0, 0, -0.43))
cube("quiver", "q_body", "leather", (-2, -12, 0), (4, 12, 3), deco="quiver")
cube("quiver", "q_rim", "leather", (-2.5, -12.5, -0.5), (5, 1, 4))
for i, (ax, ah) in enumerate(((-1.5, 3), (0, 4), (-0.5, 2))):
    cube("quiver", f"q_arrow{i}", "arrow", (ax, -12.5 - ah, 0.8 + (i % 2) * 0.8), (1, ah, 1), sk=f"qa{ah}")
part("quiver_band", "quiver", (0, 0, 0))
cube("quiver_band", "q_band", "iron", (-2.5, -8, -0.5), (5, 1, 4), trim=True)
cube("quiver_band", "q_band2", "iron", (-2.5, -3, -0.5), (5, 1, 4), share="q_band")
part("quiver_extra", "quiver", (0, 0, 0))
for i, (ax, ah) in enumerate(((1, 3), (-2, 4))):
    cube("quiver_extra", f"q_arrow{i + 3}", "arrow", (ax - 0.5, -12.5 - ah, 1.2), (1, ah, 1), sk=f"qa{ah}")

# war banners on the back (T3: one, T4: two)
part("banner", "waist", (-0.5, -4, 5.5))
cube("banner", "pole", "wood", (-0.5, -30, -0.5), (1, 38, 1))
cube("banner", "bar", "wood", (-5, -30, -0.5), (10, 1, 1))
cube("banner", "finial", "iron", (-1, -33, -1), (2, 3, 2), trim=True)
part("flag", "banner", (0, -29, 0))
cube("flag", "flag", "flag", (-4.5, 0, 0.5), (9, 14, 1), tatter=1, deco="flag")
part("flag_tail", "flag", (0, 14, 0))
cube("flag_tail", "flag_tail", "flag", (-4.5, 0, 0.5), (9, 6, 1), tatter=3, deco="flag_tail")
part("banner2", "waist", (5.5, -3, 5.5), (0, 0, -0.14))
cube("banner2", "pole2", "wood", (-0.5, -24, -0.5), (1, 30, 1))
cube("banner2", "bar2", "wood", (-4, -24, -0.5), (8, 1, 1))
cube("banner2", "skull2", "bone", (-1.5, -28, -1.5), (3, 4, 3), deco="minisk")
part("flag2", "banner2", (0, -23, 0))
cube("flag2", "flag2", "flag", (-3.5, 0, 0.5), (7, 12, 1), tatter=2, deco="flag")

# the bone shield (SHIELDED), hovering in front of the crossed arms
part("shield", "waist", (0, -6, -6.5))
cube("shield", "sh_main", "shield", (-7, -8, 0), (14, 16, 1), deco="shield", glow="shield")
cube("shield", "sh_ridge_v", "shield", (-1, -9, -1), (2, 18, 1))
cube("shield", "sh_ridge_h", "shield", (-8, -1, -1), (16, 2, 1))
cube("shield", "sh_boss", "bone", (-2.5, -3.5, -2.5), (5, 5, 2), deco="boss", glow="boss")

# ---- head
part("head", "waist", (0, -12, 0))
cube("head", "skull", "bone", (-4, -8, -4), (8, 8, 8), deco="skull")
cube("head", "teeth_u", "bone", (-3, -1, -4.5), (6, 1, 1), deco="teeth")
cube("head", "cheek", "bone_dark", (-4.5, -3, -4.2), (9, 1, 2))
part("jaw", "head", (0, -1, 2))
cube("jaw", "jaw", "bone", (-3, 0, -6.5), (6, 2, 7), deco="jaw")
part("helm", "head", (0, 0, 0))
cube("helm", "helm_dome", "iron", (-4.5, -9.5, -4.5), (9, 4, 9), deco="helm", trim=True)
cube("helm", "helm_brow", "iron", (-4.5, -7.5, -5), (9, 2, 1), trim=True)
cube("helm", "helm_back", "iron", (-4.5, -6, 3.6), (9, 6, 1), trim=True)
pair("helm", "helm_side", "iron", (-5, -7, -3.5), (1, 6, 7), trim=True)
cube("helm", "nose", "iron", (-0.5, -7.5, -5.4), (1, 6, 1))
part("helm_t3", "head", (0, 0, 0))
cube("helm_t3", "crest", "plate", (-0.5, -12, -4.5), (1, 3, 9))
pair("helm_t3", "cheek_g", "iron", (-5, -4, -3.5), (1, 3, 5), trim=True)
pair("helm_t3", "visor", "iron", (-4.5, -7.5, -5.3), (3, 1, 1), trim=True)
sided("horn", "helm_t3", (-4.5, -7.5, 0), (0, 0, 0.2), [
    ("horn_a", "bone_dark", (-4, -1.5, -1.5), (4, 3, 3), dict(sk="horna")),
])
sided("horn_tip", "{s}_horn", (-4, 0, 0), (0, 0, 0.9), [
    ("horn_b", "bone", (-5, -1, -1), (5, 2, 2), dict(sk="hornb")),
])
part("crown", "head", (0, 0, 0))
cube("crown", "crown_ring", "iron", (-4.5, -9.5, -4.5), (9, 2, 9), deco="crown", trim=True)
for i, (cx, cz, ph) in enumerate(((-4.5, -4.5, 4), (3.5, -4.5, 4), (-4.5, 3.5, 4), (3.5, 3.5, 4), (-0.5, -4.5, 6), (-0.5, 3.5, 4), (-4.5, -0.5, 4), (3.5, -0.5, 4))):
    cube("crown", f"cpt{i}", "iron", (cx, -9.5 - ph, cz), (1, ph, 1), sk=f"cp{ph}", trim=True, glow="tip" if i < 5 else None)
cube("crown", "cgem", "gold", (-1, -8.7, -4.9), (2, 1, 1), glow="gem")

# ---- arms: long bony arms, pauldrons, the left forearm carries the bow
sided("arm", "waist", (-6.5, -11, 0), (0, 0, 0), [
    ("ball", "bone", (-1.5, -1.5, -1.5), (3, 3, 3), {}),
    ("upper", "bone", (-1, 1.5, -1), (2, 6, 2), {}),
])
sided("forearm", "{s}_arm", (0, 7.5, 0), (0, 0, 0), [
    ("fore", "bone", (-1, 0, -1), (2, 7, 2), {}),
    ("hand", "bone", (-1.5, 7, -1.5), (3, 2, 3), dict(deco="hand")),
])
sided("pauld1", "{s}_arm", (0, 0, 0), (0, 0, 0), [
    ("p1", "iron", (-3.5, -3, -3), (7, 3, 6), dict(deco="pauld", trim=True)),
])
sided("pauld2", "{s}_arm", (0, 0, 0), (0, 0, 0), [
    ("p2", "iron", (-4, 0, -3.5), (8, 2, 7), dict(deco="pauld", trim=True)),
    ("p3", "iron", (-2.5, -5, -2.5), (5, 2, 5), dict(trim=True)),
    ("sp_top", "bone", (-0.5, -9, -0.5), (1, 4, 1), dict(sk="sp4")),
    ("sp_back", "bone", (-0.5, -3, 2.5), (1, 1, 3), dict(sk="spb")),
])

# ---- the greatbow, held in the left hand. Local frame: grip at the origin, limbs along y, string toward +z.
part("bow", "l_forearm", (0, 8, 0))
cube("bow", "grip", "wood", (-1.5, -3, -1.5), (3, 6, 3), deco="grip")
part("bow_up", "bow", (0, -3, 0), (0.3, 0, 0))
cube("bow_up", "limb_u", "wood", (-1, -8, -1), (2, 8, 2), deco="limb")
part("bow_up_plate", "bow_up", (0, 0, 0))
cube("bow_up_plate", "plate_u", "steel", (-1.5, -7, -1.5), (3, 4, 3), deco="limbplate", glow="rune")
part("bow_up_tip", "bow_up", (0, -8, 0), (0.45, 0, 0))
cube("bow_up_tip", "tip_u", "wood", (-0.5, -5, -0.5), (1, 5, 1))
part("bow_up_cap", "bow_up_tip", (0, -5, 0))
cube("bow_up_cap", "cap_u", "bone", (-1, -2, -1), (2, 2, 2), deco="cap")
part("bow_dn", "bow", (0, 3, 0), (-0.3, 0, 0))
cube("bow_dn", "limb_d", "wood", (-1, 0, -1), (2, 8, 2), share="limb_u")
part("bow_dn_plate", "bow_dn", (0, 0, 0))
cube("bow_dn_plate", "plate_d", "steel", (-1.5, 3, -1.5), (3, 4, 3), share="plate_u")
part("bow_dn_tip", "bow_dn", (0, 8, 0), (-0.45, 0, 0))
cube("bow_dn_tip", "tip_d", "wood", (-0.5, 0, -0.5), (1, 5, 1), share="tip_u")
part("bow_dn_cap", "bow_dn_tip", (0, 5, 0))
cube("bow_dn_cap", "cap_d", "bone", (-1, 0, -1), (2, 2, 2), share="cap_u")
# the string: two halves meeting at the nock (scaled thin in the model); arrow nocked on it
part("string_u", "bow", (0, -15.8, -7.1))
cube("string_u", "str_u", "string", (-0.5, 0, -0.5), (1, 16, 1), glow="all")
part("string_d", "bow", (0, 15.8, -7.1))
cube("string_d", "str_d", "string", (-0.5, -16, -0.5), (1, 16, 1), share="str_u")
part("arrow", "bow", (0, 0, -7.1))
cube("arrow", "ar_shaft", "wood", (-0.5, -0.5, -17), (1, 1, 17), sk="arshaft")
cube("arrow", "ar_head", "steel", (-1, -1, -20), (2, 2, 3), glow="head")
cube("arrow", "ar_fletch_v", "arrow", (-0.5, -2, -4), (1, 4, 3), sk="arfl")
cube("arrow", "ar_fletch_h", "arrow", (-2, -0.5, -4), (4, 1, 3), sk="arfh")

# ---- legs: bone legs, iron greaves (T2+), knee guards (T3+)
sided("leg", "root", (-2.5, 10, 0), (0, 0, 0), [
    ("shin", "bone", (-1, 0, -1), (2, 13, 2), {}),
    ("foot", "bone", (-1.5, 12, -3.5), (3, 2, 5), dict(deco="foot")),
])
sided("greave", "{s}_leg", (0, 0, 0), (0, 0, 0), [
    ("gv", "iron", (-2, 5, -2), (4, 8, 4), dict(deco="greave", trim=True)),
    ("gv_cuff", "iron", (-2.5, 4, -2.5), (5, 1, 5), dict(trim=True)),
    ("gv_boot", "iron", (-2, 12, -3.5), (4, 2, 5), dict(trim=True)),
])
sided("knee", "{s}_leg", (0, 0, 0), (0, 0, 0), [
    ("kn", "iron", (-2, 2.5, -3), (4, 3, 2), dict(trim=True)),
    ("kn_spike", "bone", (-0.5, 3.5, -5.5), (1, 1, 3), dict(sk="knsp")),
])

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
            if x + w > W:
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
    if rows > MAXH:
        sys.exit(f"cubes need {rows} rows of texture, only {MAXH} available")
    for c in CUBES:
        if c["share"] is not None:
            o = by_id[c["share"]]
            while o["share"] is not None:
                o = by_id[o["share"]]
            if o["size"] != c["size"]:
                sys.exit(f"{c['id']} shares UVs with {o['id']} but their sizes differ")
            c["u"], c["v"] = o["u"], o["v"]
    h = 64
    while h < rows:
        h *= 2
    return rows, h


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


def ramp(*hs):
    return [hexc(s) for s in hs]


def pick(r, t):
    t = max(0.0, min(0.9999, t))
    return r[int(t * len(r))]


# ------------------------------------------------------------------------------------------------ palettes
FACES = ("top", "bottom", "left", "front", "right", "back")
FACE_LIGHT = {"top": 1.12, "bottom": 0.72, "left": 0.88, "front": 1.0, "right": 0.88, "back": 0.82}

TIERS = {
    1: dict(
        bone=ramp("8a7a4c", "a8965c", "c4b27a", "dccc94"),
        iron=ramp("2a2018", "3a2e22", "4a3c2e", "5c4c3a"),
        plate=ramp("120c0a", "1c1410", "28201a", "342a22"),
        steel=ramp("5a4a36", "74603f", "8e7850", "a89060"),
        cloth=ramp("3c3a2a", "4e4a34", "625c42", "786f52"),
        wood=ramp("5a4026", "74552f", "8e6a3c", "a88448"),
        string=ramp("a89a78", "c8bc98", "e0d6b4", "f0e8cc"),
        glow=(255, 150, 40), glow_dim=(120, 60, 20), trim=None, seed=100),
    2: dict(
        bone=ramp("96865a", "b4a470", "ccbe8c", "e2d4a4"),
        iron=ramp("3c3e44", "54575e", "6e727a", "8a8e96"),
        plate=ramp("1c1c22", "2a2a32", "383840", "484850"),
        steel=ramp("4a4e56", "646a74", "808892", "9ca4b0"),
        cloth=ramp("4a2a22", "5e3428", "74432f", "8c5640"),
        wood=ramp("40301c", "584226", "70552e", "866a3a"),
        string=ramp("a89a78", "c8bc98", "e0d6b4", "f0e8cc"),
        glow=(90, 150, 255), glow_dim=(30, 50, 110), trim=None, seed=200),
    3: dict(
        bone=ramp("8a8c90", "a6aab0", "c2c6c8", "dce0e0"),
        iron=ramp("26303c", "364454", "485a6e", "62788e"),
        plate=ramp("141a24", "1e2634", "2a384a", "384a60"),
        steel=ramp("5a6470", "78848e", "98a4ae", "b8c4ce"),
        cloth=ramp("3a1018", "501822", "68202c", "80303c"),
        wood=ramp("2c2418", "3e3222", "524230", "685640"),
        string=ramp("8aa4c4", "a8c0dc", "c4d8ec", "e0f0ff"),
        glow=(80, 190, 255), glow_dim=(26, 70, 120), trim=None, seed=300),
    4: dict(
        bone=ramp("7c8aa0", "98a8bc", "b8c8d8", "d8e8f4"),
        iron=ramp("14141c", "1e1e28", "2a2a38", "3a3a4c"),
        plate=ramp("0a0a10", "12121a", "1a1a24", "24242e"),
        steel=ramp("1a1a24", "2a2a3a", "3c3c52", "50507a"),
        cloth=ramp("14101c", "201a2c", "2e263e", "403650"),
        wood=ramp("120e18", "1c1626", "28203a", "38304e"),
        string=ramp("90b0ff", "b0ccff", "d0e4ff", "f0f8ff"),
        glow=(170, 230, 255), glow_dim=(50, 100, 190), trim=ramp("5c3c14", "8a5e1e", "b8862c", "e0b648"), seed=400),
}
LEATHER = ramp("2e1c10", "442a16", "5a3a1e", "744c28")
FIRE = {3: ramp("1840b0", "2c78e0", "58b8ff", "c0f0ff"), 4: ramp("2058d0", "40a0ff", "90d8ff", "f0ffff")}
GOLD = ramp("5c3c14", "8a5e1e", "b8862c", "e0b648")


class Pal:
    def __init__(self, tier):
        self.tier = tier
        self.d = TIERS[tier]
        for k, v in self.d.items():
            setattr(self, k, v)
        self.fire = FIRE.get(tier, FIRE[3])
        self.gilded = tier == 4
        if self.trim is None:
            self.trim = self.iron


def face_rects(c):
    u, v = c["u"], c["v"]
    w, h, d = (int(n) for n in c["size"])
    return {
        "top": (u + d, v, w, d), "bottom": (u + d + w, v, w, d),
        "left": (u, v + d, d, h), "front": (u + d, v + d, w, h),
        "right": (u + d + w, v + d, d, h), "back": (u + 2 * d + w, v + d, w, h),
    }


def put(img, x, y, col, a=255):
    if 0 <= x < img.width and 0 <= y < img.height:
        img.putpixel((x, y), (int(col[0]), int(col[1]), int(col[2]), a))


def paint_cube(img, glow_img, c, pal):
    rects = face_rects(c)
    mat = c["mat"]
    seed = pal.seed + sum(ord(ch) for ch in c["id"])
    for face, (fx, fy, fw, fh) in rects.items():
        for py in range(fh):
            for px in range(fw):
                X, Y = fx + px, fy + py
                col, alpha = base_pixel(mat, pal, X, Y, px, py, fw, fh, face, seed, c)
                k = FACE_LIGHT[face]
                if face in ("left", "front", "right", "back"):
                    if py == 0:
                        k *= 1.08
                    if py == fh - 1:
                        k *= 0.82
                    if px == 0 or px == fw - 1:
                        k *= 0.94
                col = mul(col, k)
                put(img, X, Y, col, alpha)
    if c.get("trim") and pal.gilded:
        trim(img, rects, pal)
    deco = c.get("deco")
    if deco:
        DECOS[deco](img, glow_img, c, rects, pal, seed)
    g = c.get("glow")
    if g and pal.tier >= GLOW_MIN.get(g, 1):
        GLOWERS[g](img, glow_img, c, rects, pal, seed)


def trim(img, rects, pal):
    """Gold edging on the tier IV black iron."""
    for face in ("front", "back", "left", "right"):
        fx, fy, fw, fh = rects[face]
        for px in range(fw):
            put(img, fx + px, fy, pal.trim[3])
            put(img, fx + px, fy + fh - 1, pal.trim[1])
        if fw > 2 and fh > 2:
            for py in range(fh):
                put(img, fx, fy + py, pal.trim[2])
                put(img, fx + fw - 1, fy + py, pal.trim[1])
    fx, fy, fw, fh = rects["top"]
    for px in range(fw):
        put(img, fx + px, fy, pal.trim[2])
        put(img, fx + px, fy + fh - 1, pal.trim[2])


def base_pixel(mat, pal, X, Y, px, py, fw, fh, face, seed, c):
    n1 = vnoise(X, Y, 4.0, seed)
    n3 = _h(X, Y, seed + 3)
    alpha = 255
    t = pal.tier
    if mat in ("bone", "bone_dark", "shield"):
        v = 0.3 + 0.5 * n1 + 0.2 * (n3 - 0.5)
        if mat == "bone_dark":
            v -= 0.25
        col = pick(pal.bone, v)
        if vnoise(X, Y, 3.0, seed + 21) > 0.8:
            col = mul(col, 0.8)
        if (X + 2 * Y) % 11 == 0 and n1 > 0.55:
            col = mul(col, 0.7)
        if t == 1 and vnoise(X + 5, Y, 3.0, seed + 8) > 0.78:
            col = mix(col, (96, 70, 30), 0.5)  # grave stains on the yellowed bone
    elif mat == "plate":
        col = pick(pal.plate, 0.2 + 0.6 * vnoise(X, Y, 3.0, seed) + 0.2 * (n3 - 0.5))
    elif mat == "iron":
        col = pick(pal.iron, 0.2 + 0.7 * vnoise(X, Y, 3.0, seed) + 0.25 * (n3 - 0.5))
        if t == 2 and vnoise(X, Y, 2.0, seed + 5) > 0.72:
            col = mix(col, (110, 70, 40), 0.55)  # rust
        if px % 4 == 1 and py % 4 == 1 and fw > 6:
            col = mul(col, 0.65)  # rivets
    elif mat == "steel":
        col = pick(pal.steel, 0.25 + 0.6 * vnoise(X, Y, 3.0, seed) + 0.2 * (n3 - 0.5))
        if vnoise(X, Y, 2.0, seed + 5) > 0.8:
            col = mul(col, 0.7)
    elif mat == "gold":
        col = pick(GOLD, 0.3 + 0.6 * vnoise(X, Y, 3.0, seed) + 0.3 * (n3 - 0.5))
    elif mat == "leather":
        col = pick(LEATHER, 0.2 + 0.6 * n1 + 0.2 * (n3 - 0.5))
        if (X + Y) % 7 == 0 and n1 > 0.5:
            col = mul(col, 0.75)
    elif mat in ("cloth", "flag"):
        col = pick(pal.cloth, 0.25 + 0.55 * n1 + 0.3 * (n3 - 0.5))
        if py % 3 == 0:
            col = mul(col, 0.94)
        if mat == "flag":
            col = mul(col, 0.9)
        if vnoise(X, Y, 3.5, seed + 31) > 0.74:
            col = mul(col, 0.7)
        tat = c.get("tatter")
        if tat and face in ("front", "back", "left", "right"):
            ragged = fh - 1 - py
            hole = vnoise(X, Y, 2.5, seed + 41)
            lim = 2 + tat
            if (ragged < lim and hole > 0.42 + 0.1 * ragged) or hole > 0.9 or \
                    (ragged < lim + 3 and (px * 7 + seed) % 5 == 0 and ragged < 1 + (px * 3 + seed) % (lim + 1)):
                alpha = 0
            if py < 2:
                alpha = 255
    elif mat == "wood":
        col = pick(pal.wood, 0.25 + 0.55 * vnoise(X * 3, Y, 5.0, seed) + 0.2 * (n3 - 0.5))
        if (Y * 2 + X) % 9 == 0:
            col = mul(col, 0.8)  # grain
    elif mat == "string":
        col = pick(pal.string, 0.5 + 0.5 * n3)
    elif mat == "arrow":
        col = pick(pal.bone, 0.6 + 0.4 * n3) if (px + py) % 2 == 0 else mul(pick(pal.bone, 0.4), 0.8)
    elif mat == "fire":
        col = pick(pal.fire, 0.3 + 0.7 * vnoise(X, Y, 2.0, seed))
    else:
        col = (255, 0, 255)
    return col, alpha


# ------------------------------------------------------------------------------------------------ decorations

def deco_skull(img, glow, c, rects, pal, seed):
    fx, fy, fw, fh = rects["front"]
    dark = (10, 10, 14)
    t = pal.tier
    for ex in (1, 5):
        for py in range(3, 6):
            for px in range(ex, ex + 2 + (1 if t < 4 else 1)):
                put(img, fx + px, fy + py, dark)
        # the glowing pupils (bigger and brighter on higher tiers)
        gcol = pal.glow if t > 1 else pal.glow_dim
        for px in range(ex, ex + 2):
            put(glow, fx + px, fy + 4, gcol)
            if t >= 3:
                put(glow, fx + px, fy + 3, tuple(int(v * 0.7) for v in gcol))
            if t >= 4:
                put(glow, fx + px, fy + 5, tuple(int(v * 0.55) for v in gcol))
        put(img, fx + ex, fy + 4, pal.glow_dim)
    for px, py in ((3, 5), (4, 5), (3, 6), (4, 6)):
        put(img, fx + px, fy + py, dark)
    # cracks in the cranium
    tx, ty, tw, th = rects["top"]
    for k in range(6):
        put(img, tx + 2 + k, ty + 2 + (k * 2) % 3, mul(pal.bone[0], 0.55))
    bx, by, bw, bh = rects["back"]
    for k in range(5):
        put(img, bx + 3 + (k % 2), by + k, mul(pal.bone[0], 0.55))
    # a crack running down the brow
    for k in range(3):
        put(img, fx + 2 + k // 2, fy + k, mul(pal.bone[0], 0.5))
    if t >= 2:
        for k in range(3):
            put(glow, fx + 2 + k // 2, fy + k, tuple(int(v * 0.35) for v in pal.glow))


def deco_teeth(img, glow, c, rects, pal, seed):
    fx, fy, fw, fh = rects["front"]
    for px in range(fw):
        put(img, fx + px, fy, pal.bone[3] if px % 2 == 0 else mul(pal.bone[1], 0.6))


def deco_jaw(img, glow, c, rects, pal, seed):
    fx, fy, fw, fh = rects["front"]
    # the cracked jaw: a dark fracture, a missing tooth and a chipped corner
    for k in range(fh):
        put(img, fx + 2 + k, fy + k, (12, 10, 10))
    for px in range(fw):
        put(img, fx + px, fy, pal.bone[3] if px % 2 == 0 else mul(pal.bone[1], 0.6))
    put(img, fx + 4, fy, (12, 10, 10))
    tx, ty, tw, th = rects["top"]
    for py in range(th):
        for px in range(tw):
            put(img, tx + px, ty + py, mix((30, 14, 14), (14, 8, 8), vnoise(tx + px, ty + py, 2.0, seed)))
    if pal.tier >= 3:
        for px in range(1, tw - 1):
            for py in range(2, th - 1):
                put(glow, tx + px, ty + py, tuple(int(v * 0.3) for v in pal.glow), 200)


def deco_plate(img, glow, c, rects, pal, seed):
    # the ribcage hollow: bands between the ribs light up from tier III
    fx, fy, fw, fh = rects["front"]
    for ry in (1, 4, 7):  # the gaps between ribs (ribs sit at -11, -8, -5, -2; plate starts at -11.5)
        for px in range(1, fw - 1):
            put(img, fx + px, fy + ry, mul(pal.plate[0], 0.7))
            put(img, fx + px, fy + ry + 1, mul(pal.plate[0], 0.7))
            if pal.tier >= 3:
                k = 0.5 if pal.tier == 3 else 0.95
                v = 0.6 + 0.4 * vnoise(fx + px, fy + ry, 2.0, seed)
                col = tuple(int(x * k * v) for x in pal.glow)
                put(glow, fx + px, fy + ry, col)
                put(glow, fx + px, fy + ry + 1, tuple(int(x * 0.6) for x in col))
    # the breastplate's seam and dents
    for py in range(fh):
        if py % 3 != 1:
            put(img, fx + fw // 2, fy + py, mul(pal.plate[0], 0.6))


def deco_rib(img, glow, c, rects, pal, seed):
    fx, fy, fw, fh = rects["front"]
    for px in range(0, fw, 2):
        put(img, fx + px, fy, mul(pal.bone[3], 0.95))
    if pal.tier >= 3:
        # blue light bleeding along the lower edge
        for px in range(fw):
            put(glow, fx + px, fy + fh - 1, tuple(int(v * (0.25 if pal.tier == 3 else 0.45)) for v in pal.glow), 160)


def deco_cuirass(img, glow, c, rects, pal, seed):
    fx, fy, fw, fh = rects["front"]
    cx = fx + fw // 2
    for k in (-1, 0, 1):
        put(img, cx + k, fy + 1, pal.trim[3])
    put(img, cx, fy + 2, pal.trim[3])
    for px in range(1, fw - 1, 2):
        put(img, fx + px, fy + fh - 2, pal.trim[2])


def deco_cuirass_back(img, glow, c, rects, pal, seed):
    fx, fy, fw, fh = rects["back"]
    for py in range(fh):
        put(img, fx + fw // 2, fy + py, pal.trim[1])
    for py in range(2, fh, 3):
        for dx in (-2, -1, 1, 2):
            put(img, fx + fw // 2 + dx, fy + py, pal.trim[2])


def deco_belt(img, glow, c, rects, pal, seed):
    fx, fy, fw, fh = rects["front"]
    for dx in (-1, 0):
        for dy in (0, 1):
            put(img, fx + fw // 2 + dx, fy + dy, (170, 140, 70) if pal.tier < 4 else pal.trim[3])


def deco_cape(img, glow, c, rects, pal, seed):
    fx, fy, fw, fh = rects["back"]
    if pal.tier >= 3:
        # a worn embroidered border down the edges
        col = (150, 120, 60) if pal.tier == 3 else pal.trim[2]
        for py in range(fh):
            put(img, fx, fy + py, mul(col, 0.7))
            put(img, fx + fw - 1, fy + py, mul(col, 0.7))
    elif pal.tier == 2:
        for py in range(0, fh, 3):
            put(img, fx + 2, fy + py, mul(pal.cloth[0], 0.8))


def deco_quiver(img, glow, c, rects, pal, seed):
    for face in ("front", "back"):
        fx, fy, fw, fh = rects[face]
        for py in range(1, fh, 2):
            put(img, fx + 1, fy + py, mul(LEATHER[0], 0.8))
            put(img, fx + fw - 2, fy + py, mul(LEATHER[0], 0.8))


def deco_flag(img, glow, c, rects, pal, seed):
    if c["id"] not in ("flag", "flag2"):
        return
    for face in ("front", "back"):
        fx, fy, fw, fh = rects[face]
        cx = fx + fw // 2
        g = (210, 190, 140) if pal.tier == 3 else pal.trim[3]
        # a skull emblem
        for (px, py) in ((-1, 3), (0, 3), (1, 3), (-2, 4), (-1, 4), (0, 4), (1, 4), (2, 4), (-2, 5), (2, 5), (-1, 6), (0, 6), (1, 6), (-1, 7), (1, 7)):
            put(img, cx + px, fy + py, g)
        for px in (-1, 1):
            put(img, cx + px, fy + 5, (10, 10, 14))
            if pal.tier >= 4:
                put(glow, cx + px, fy + 5, pal.glow)
        for px in range(fw):
            put(img, fx + px, fy + 1, mul(g, 0.6))
    if pal.tier >= 4:
        fx, fy, fw, fh = rects["front"]
        for py in range(9, fh - 2, 2):
            put(glow, fx + fw // 2, fy + py, pal.glow_dim)


def deco_flag_tail(img, glow, c, rects, pal, seed):
    pass


def deco_minisk(img, glow, c, rects, pal, seed):
    fx, fy, fw, fh = rects["front"]
    for px in (0, 2):
        put(img, fx + px, fy + 1, (10, 10, 14))
        put(glow, fx + px, fy + 1, pal.glow)


def deco_shield(img, glow, c, rects, pal, seed):
    fx, fy, fw, fh = rects["front"]
    rim = pal.bone[0]
    for px in range(fw):
        put(img, fx + px, fy, rim)
        put(img, fx + px, fy + fh - 1, rim)
    for py in range(fh):
        put(img, fx, fy + py, rim)
        put(img, fx + fw - 1, fy + py, rim)
    # rib-like grooves
    for py in range(2, fh - 2, 3):
        for px in range(2, fw - 2):
            put(img, fx + px, fy + py, mul(pal.bone[1], 0.75))


def deco_boss(img, glow, c, rects, pal, seed):
    fx, fy, fw, fh = rects["front"]
    for px in (1, 3):
        for py in (2,):
            put(img, fx + px, fy + py, (10, 10, 14))
            put(glow, fx + px, fy + py, pal.glow)
    put(img, fx + 2, fy + 3, (10, 10, 14))


def deco_hand(img, glow, c, rects, pal, seed):
    fx, fy, fw, fh = rects["front"]
    for px in range(0, fw, 1):
        put(img, fx + px, fy + fh - 1, mul(pal.bone[1], 0.7) if px % 2 else pal.bone[3])


def deco_pauld(img, glow, c, rects, pal, seed):
    for face in ("front", "back", "left", "right"):
        fx, fy, fw, fh = rects[face]
        for px in range(fw):
            put(img, fx + px, fy + fh - 1, pal.plate[0])
            if px % 3 == 1:
                put(img, fx + px, fy, mul(pal.iron[3], 1.0))
    tx, ty, tw, th = rects["top"]
    for k in range(tw):
        put(img, tx + k, ty + th // 2, pal.plate[0])


def deco_helm(img, glow, c, rects, pal, seed):
    for face in ("front", "back", "left", "right"):
        fx, fy, fw, fh = rects[face]
        for px in range(fw):
            if px % 3 == 1:
                put(img, fx + px, fy + fh - 1, mul(pal.iron[3], 0.9))
    tx, ty, tw, th = rects["top"]
    for k in range(tw):
        put(img, tx + k, ty + th // 2, pal.plate[0])


def deco_crown(img, glow, c, rects, pal, seed):
    for face in ("front", "back", "left", "right"):
        fx, fy, fw, fh = rects[face]
        for px in range(fw):
            if px % 2 == 0:
                put(img, fx + px, fy + 1, pal.trim[1])
    tx, ty, tw, th = rects["top"]
    for k in range(tw):
        put(img, tx + k, ty + th // 2, pal.plate[0])


def deco_greave(img, glow, c, rects, pal, seed):
    for face in ("front", "back", "left", "right"):
        fx, fy, fw, fh = rects[face]
        for py in range(fh):
            put(img, fx + fw // 2, fy + py, mul(pal.iron[0], 0.8))
        for px in range(fw):
            put(img, fx + px, fy + fh // 2, mul(pal.iron[0], 0.85))


def deco_foot(img, glow, c, rects, pal, seed):
    fx, fy, fw, fh = rects["front"]
    for px in range(fw):
        put(img, fx + px, fy + fh - 1, mul(pal.bone[1], 0.8))


def deco_grip(img, glow, c, rects, pal, seed):
    for face in ("front", "back", "left", "right"):
        fx, fy, fw, fh = rects[face]
        for py in range(fh):
            put(img, fx, fy + py, LEATHER[1]) if py % 2 == 0 else None
            for px in range(fw):
                if (px + py) % 2 == 0:
                    put(img, fx + px, fy + py, LEATHER[2 if py % 3 else 1])


def deco_limb(img, glow, c, rects, pal, seed):
    if pal.tier == 1:
        return
    for face in ("front", "back"):
        fx, fy, fw, fh = rects[face]
        for py in range(0, fh, 3):
            for px in range(fw):
                put(img, fx + px, fy + py, pal.bone[1] if pal.tier < 4 else pal.trim[1])


def deco_limbplate(img, glow, c, rects, pal, seed):
    for face in ("front", "back", "left", "right"):
        fx, fy, fw, fh = rects[face]
        for px in range(fw):
            put(img, fx + px, fy, mul(pal.steel[3], 1.0))
            put(img, fx + px, fy + fh - 1, mul(pal.steel[0], 0.7))
        if pal.tier >= 4:
            for py in range(fh):
                put(img, fx, fy + py, pal.trim[2])
    if pal.tier >= 3:
        pass


def deco_cap(img, glow, c, rects, pal, seed):
    for face in ("front", "back", "left", "right"):
        fx, fy, fw, fh = rects[face]
        put(img, fx + fw // 2, fy, (10, 10, 14))  # the string notch


DECOS = {k[len("deco_"):]: v for k, v in list(globals().items()) if k.startswith("deco_")}


# ------------------------------------------------------------------------------------------------ glow painters
GLOW_MIN = {"all": 3, "shield": 1, "boss": 1, "rune": 4, "tip": 4, "gem": 4, "head": 3}


def glow_all(img, glow, c, rects, pal, seed):
    for face, (fx, fy, fw, fh) in rects.items():
        for py in range(fh):
            for px in range(fw):
                X, Y = fx + px, fy + py
                if c["mat"] == "string":
                    k = 0.55 if pal.tier == 3 else 1.0
                    col = tuple(int(v * k) for v in pal.glow)
                else:
                    t = 0.4 + 0.6 * vnoise(X, Y, 2.0, seed + 2)
                    if (px + py * 2 + seed) % 5 == 0:
                        t *= 0.6
                    col = pick(pal.fire, t)
                    col = tuple(int(v) for v in col)
                put(glow, X, Y, col)
                if c["mat"] == "fire":
                    put(img, X, Y, col)


def glow_shield(img, glow, c, rects, pal, seed):
    fx, fy, fw, fh = rects["front"]
    k = 0.6 if pal.tier < 3 else 1.0
    col = tuple(int(v * k) for v in pal.glow)
    for px in range(fw):
        put(glow, fx + px, fy, col)
        put(glow, fx + px, fy + fh - 1, col)
    for py in range(fh):
        put(glow, fx, fy + py, col)
        put(glow, fx + fw - 1, fy + py, col)
    if pal.tier >= 2:
        for py in range(2, fh - 2, 3):
            for px in range(3, fw - 3, 2):
                put(glow, fx + px, fy + py, tuple(int(v * 0.35) for v in pal.glow))


def glow_boss(img, glow, c, rects, pal, seed):
    pass


def glow_rune(img, glow, c, rects, pal, seed):
    for face in ("front", "back"):
        fx, fy, fw, fh = rects[face]
        for py in range(1, fh - 1):
            if py % 2 == 1:
                put(glow, fx + fw // 2, fy + py, pal.glow)
                put(img, fx + fw // 2, fy + py, pal.glow_dim)


def glow_tip(img, glow, c, rects, pal, seed):
    for face in ("front", "back", "left", "right"):
        fx, fy, fw, fh = rects[face]
        put(glow, fx, fy, tuple(int(v * 0.8) for v in pal.glow))


def glow_gem(img, glow, c, rects, pal, seed):
    for face, (fx, fy, fw, fh) in rects.items():
        for py in range(fh):
            for px in range(fw):
                put(img, fx + px, fy + py, (60, 120, 220))
                put(glow, fx + px, fy + py, pal.glow)


def glow_head(img, glow, c, rects, pal, seed):
    fx, fy, fw, fh = rects["front"]
    k = 0.5 if pal.tier == 3 else 1.0
    for py in range(fh):
        for px in range(fw):
            put(glow, fx + px, fy + py, tuple(int(v * k) for v in pal.glow))


GLOWERS = {"all": glow_all, "shield": glow_shield, "boss": glow_boss, "rune": glow_rune, "tip": glow_tip,
           "gem": glow_gem, "head": glow_head}


def paint(tier, height):
    pal = Pal(tier)
    img = Image.new("RGBA", (W, height), (0, 0, 0, 0))
    glow = Image.new("RGBA", (W, height), (0, 0, 0, 0))
    for c in CUBES:
        if c["share"] is None:
            paint_cube(img, glow, c, pal)
    return img, glow


# ------------------------------------------------------------------------------------------------ Java

def fnum(v):
    return f"{float(v):g}f"


def java(height):
    children = {}
    for p in PARTS:
        children.setdefault(p["parent"], []).append(p)
    cubes_of = {}
    for c in CUBES:
        cubes_of.setdefault(c["part"], []).append(c)
    out = []

    def emit(p, parent_var, indent):
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
        out.append(f'{pad}PartDefinition {var} = {parent_var}.addOrReplaceChild("{p["name"]}",')
        out.extend(b)
        out.append(f"{pad}        , {pose});")
        for ch in children.get(p["name"], []):
            emit(ch, var, indent)

    for p in children.get("root", []) + children.get(None, []):
        emit(p, "root", 0)
    body = "\n".join(out)
    return f"""package com.skyrpg.combat.client.slayer;

import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;

/**
 * The Bone Marshal's cubes and their UVs. GENERATED by tools/art/generate_bone_marshal.py from the same cube
 * table the textures are painted from: edit the table there, never this file.
 */
final class BoneMarshalLayout {{
    private BoneMarshalLayout() {{
    }}

    static LayerDefinition create() {{
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
{body}
        return LayerDefinition.create(mesh, {W}, {height});
    }}
}}
"""


def main():
    rows, height = pack()
    print(f"packed {len(CUBES)} cubes, {sum(1 for c in CUBES if c['share'] is None)} unique UV regions, {rows} rows used, texture {W}x{height}")
    TEX_DIR.mkdir(parents=True, exist_ok=True)
    for tier in (1, 2, 3, 4):
        img, glow = paint(tier, height)
        img.save(TEX_DIR / f"bone_marshal_model_t{tier}.png")
        glow.save(TEX_DIR / f"bone_marshal_model_t{tier}_glow.png")
    JAVA_OUT.write_text(java(height), encoding="utf-8")
    print("wrote textures and", JAVA_OUT.name)


if __name__ == "__main__":
    main()
