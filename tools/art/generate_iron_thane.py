#!/usr/bin/env python3
"""Draws the Iron Thane's model textures (the Dwarven Halls boss) and writes its model definition.

    python tools/art/generate_iron_thane.py [--preview DIR]

A huge crowned dwarf-king construct (about 1.6 x 3.0 blocks before any scale): massive iron pauldrons, a gold crown
with glowing rune gems, a long braided iron beard that splits around a molten forge-core belly behind a grate, smoke
stacks on its back, and a two-handed forge hammer (part of the model) carried on its right shoulder. Built like the
Dwarven Construct (its materials and painter come from generate_dwarven_construct.py; see cube_models.py for the
table, packing and preview). Outputs:

    combat/src/main/resources/assets/skyrpg_combat/textures/entity/mob/iron_thane.png
    ... iron_thane_glow.png    (steady glow: eyes, runes)
    ... iron_thane_pulse.png   (the forge core, crown gems, stack embers and the hammer's hot faces; drawn fullbright
                                with a pulsing brightness)
    combat/src/client/java/com/skyrpg/combat/client/mob/IronThaneLayout.java
"""
import math
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import cube_models as cm  # noqa: E402
import generate_dwarven_construct as dc  # noqa: E402
from cube_models import _h, hexc, mix, mul, pick, ramp, vnoise  # noqa: E402

ROOT = Path(__file__).resolve().parents[2]
TEX_DIR = ROOT / "combat/src/main/resources/assets/skyrpg_combat/textures/entity/mob"
JAVA_OUT = ROOT / "combat/src/client/java/com/skyrpg/combat/client/mob/IronThaneLayout.java"
NAME = "iron_thane"
UNITS = (84, 96)  # preview canvas, in model pixels

# ------------------------------------------------------------------------------------------------ cube table
# Feet at y=24, the crown's peak near y=-26. The hips (y 12) carry the torso; legs hang from the root.
M = cm.Model(256)

M.part("hips", None, (0, 12, 0))
M.cube("hips", "pelvis", "brick", (-9, -4, -5.5), (18, 5, 11))
M.cube("hips", "belt", "brass", (-10.5, -5, -7), (21, 3, 14), deco="belt")
M.cube("hips", "buckle", "gold", (-3, -5.5, -7.3), (6, 4, 1), deco="buckle")
M.cube("hips", "skirt_back", "plate", (-8, -2, 5.7), (16, 7, 1), deco="tasset")
M.sided("tasset", "hips", (-5, -2, -6.4), (-0.06, 0, 0.05), [
    ("tasset", "plate", (-3.5, 0, -0.5), (7, 7, 1), dict(deco="tasset")),
])

M.part("torso", "hips", (0, -4, 0))
M.cube("torso", "belly", "plate", (-10, -10, -6.5), (20, 10, 13), deco="belly")
M.cube("torso", "chest", "plate", (-12, -22, -7), (24, 13, 14), deco="chest")
M.cube("torso", "seam", "gold", (-12.5, -9.6, -7.5), (25, 1, 15), deco="trim")
M.cube("torso", "gorget", "plate", (-8, -24.5, -6), (16, 3, 12), deco="gorget")
M.cube("torso", "forge", "brass", (-6.5, -9.3, -7.7), (13, 8, 1), deco="forge")
for i, x in enumerate((-3.5, -1.5, 0.5, 2.5)):
    M.cube("torso", f"bar{i}", "iron", (x, -8.6, -8.9), (1, 7, 1), share="bar0" if i else None, deco=None if i else "bar")
M.cube("torso", "bar_x", "iron", (-5, -5.6, -9.1), (10, 1, 1), deco="bar")
M.cube("torso", "backplate", "plate", (-10, -21, 7), (20, 15, 2), deco="backplate")
M.pair("torso", "stack", "iron", (-8.5, -31, 7.6), (4, 12, 4), deco="stack")
M.pair("torso", "stack_cap", "gold", (-9, -32, 7.1), (5, 1, 5), deco="cap")
M.part("core", "torso", (0, -5.3, -7.55))
M.cube("core", "core", "molten", (-5, -3, -0.5), (10, 6, 1), glow="pulse")

M.part("head", "torso", (0, -21.5, -4))
M.cube("head", "skull", "brass", (-5.5, -8, -5), (11, 9, 9), deco="face")
M.cube("head", "brow", "plate", (-6, -6, -6), (12, 2, 2), deco="brow")
M.cube("head", "nose", "brass", (-1, -4, -6.1), (2, 4, 1))
M.pair("head", "ear", "gold", (-6.2, -4.5, -3.2), (1, 3, 3), deco="rivet")
M.cube("head", "moustache", "beard", (-5, -1.2, -6.4), (10, 2, 2), deco="moustache")
M.part("crown", "head", (0, -8, 0))
M.cube("crown", "ring", "gold", (-6, -2.5, -6), (12, 3, 12), deco="crown")
M.cube("crown", "peak", "gold", (-1, -6.5, -6.3), (2, 4, 1), deco="peak")
M.cube("crown", "peak_back", "gold", (-1, -6.5, 5.3), (2, 4, 1), share="peak")
M.pair("crown", "point_f", "gold", (-6.3, -5, -6.3), (2, 3, 1), deco="point")
M.pair("crown", "point_b", "gold", (-6.3, -5, 5.3), (2, 3, 1), deco="point")
M.pair("crown", "point_s", "gold", (-6.3, -5.5, -1), (1, 3, 2), deco="point")
M.cube("crown", "gem", "gem", (-1, -1.8, -6.6), (2, 2, 1), glow="pulse")
M.pair("crown", "gem_s", "gem", (-6.6, -1.8, -0.5), (1, 1, 1), glow="pulse")

M.part("beard", "head", (0, 1.2, -5.5))
M.cube("beard", "beard_top", "beard", (-6, -1, -1.2), (12, 6, 3), deco="beard_top")
M.cube("beard", "beard_mid", "beard", (-4.5, 5, -0.8), (9, 3, 3))
M.cube("beard", "beard_tip", "beard", (-2.5, 8, -0.4), (5, 2, 2))
M.cube("beard", "beard_ring", "gold", (-3, 7.4, -0.7), (6, 1, 3))
M.sided("braid", "beard", (-3.8, 5.5, 0.6), (0, 0, 0.42), [
    ("braid", "beard", (-1.5, 0, -1.5), (3, 9, 3), {}),
    ("braid_ring", "gold", (-2, 2, -2), (4, 1, 4), dict(deco="rivet")),
    ("braid_ring2", "gold", (-2, 6.3, -2), (4, 1, 4), {}),
    ("braid_tip", "gold", (-1, 9, -1), (2, 2, 2), {}),
])

M.sided("arm", "torso", (-14.5, -17.5, 0), (0.0, 0, 0.3), [
    ("pauldron", "plate", (-6, -6, -6.5), (11, 7, 13), dict(deco="pauldron")),
    ("pauld_dome", "plate", (-4.5, -7.5, -5), (8, 2, 10), dict(deco="dome")),
    ("pauld_lame", "plate", (-5.5, 0.6, -6), (10, 3, 12), dict(deco="lame")),
    ("upper", "brick", (-4, 1, -4), (8, 9, 8), {}),
])
M.sided("forearm", "{s}_arm", (0, 10, 0), (-1.05, 0, 0), [
    ("elbow", "brass", (-4.5, -1.5, -4.5), (9, 3, 9), dict(deco="rivet")),
    ("gauntlet", "plate", (-4.5, 1, -4.5), (9, 7, 9), dict(deco="gauntlet")),
    ("cuff", "gold", (-5, 1, -5), (10, 2, 10), dict(deco="trim")),
    ("fist", "stone", (-4, 8, -4), (8, 6, 8), dict(deco="fist")),
])


def set_rot(name, rot):
    next(p for p in M.parts if p["name"] == name)["rot"] = rot


# rest pose: the right hand holds the hammer upright at its side like a sceptre, the left arm hangs
set_rot("l_arm", (0.0, 0, -0.1))
set_rot("l_forearm", (-0.25, 0, 0))

# the forge hammer, held in the right fist; the haft runs along the part's -y, the head's striking faces are at +-z
M.part("hammer", "r_forearm", (0, 11, 0), (1.05, 0, -0.12))
M.cube("hammer", "haft", "wood", (-1.5, -17, -1.5), (3, 23, 3), deco="haft")
M.cube("hammer", "pommel", "gold", (-2, 6, -2), (4, 3, 4), deco="trim")
M.cube("hammer", "haft_band", "gold", (-2, -12, -2), (4, 1, 4))
M.cube("hammer", "haft_band2", "gold", (-2, -6, -2), (4, 1, 4), share="haft_band")
M.cube("hammer", "socket", "brass", (-2.5, -18.4, -2.5), (5, 2, 5), deco="rivet")
M.cube("hammer", "head", "plate", (-4.5, -26, -7), (9, 9, 14), deco="hammer")
M.cube("hammer", "band_f", "gold", (-5, -26.5, -5.2), (10, 10, 2), deco="trim")
M.cube("hammer", "band_b", "gold", (-5, -26.5, 3.2), (10, 10, 2), share="band_f")
M.cube("hammer", "face_f", "iron", (-3.5, -25, -7.8), (7, 7, 1), deco="strike")
M.cube("hammer", "face_b", "iron", (-3.5, -25, 6.8), (7, 7, 1), share="face_f")

M.sided("leg", None, (-5.5, 12, 0), (0, 0, 0), [
    ("thigh", "brick", (-4.5, 0, -4.5), (9, 7, 9), {}),
    ("knee", "plate", (-4, 4.5, -5.6), (8, 3, 2), dict(deco="knee")),
    ("greave", "plate", (-5, 7, -5), (10, 3, 10), dict(deco="greave")),
    ("boot", "stone", (-5.5, 9, -7.5), (11, 3, 13), dict(deco="boot")),
    ("toe", "gold", (-4.5, 9.9, -8.2), (9, 2, 1), {}),
])


# ------------------------------------------------------------------------------------------------ palette

class Pal(dc.Pal):
    def __init__(self):
        super().__init__()
        self.brick = ramp("1a1a20", "222228", "2b2b32", "35353d", "404048", "4c4c55")
        self.stone = ramp("24242b", "2d2d35", "37373f", "42424b", "4e4e57", "5c5c66")
        self.plate = ramp("121318", "191b21", "21242b", "2a2e36", "353a43", "434952", "565d68")
        self.gold = ramp("4a2c04", "70480a", "9c6c14", "c49222", "e2b83a", "f6dc78", "fff2c0")
        self.molten = ramp("4a0c02", "7a1c04", "b2380a", "e26414", "ff9a2c", "ffd060")
        self.molten_glow = (255, 130, 30)
        self.wood = ramp("1e120a", "2c1a0e", "3c2414", "4e301c", "603e26")
        self.eye = (255, 224, 140)
        self.patina = hexc("4a3414")
        self.beard = ramp("3c414a", "535963", "6c737e", "878f9a", "a3abb5", "c2c9d1", "dde2e8")
        self.ember = (255, 120, 30)


def base_pixel(mat, pal, X, Y, px, py, fw, fh, face, seed):
    if mat == "plate":
        n1 = vnoise(X, Y, 3.0, seed)
        n3 = _h(X, Y, seed + 3)
        t = 0.3 + 0.4 * n1 + 0.12 * (n3 - 0.5)
        if face in ("left", "front", "right", "back"):
            t += 0.12 * (1 - py / max(1, fh - 1)) - 0.04
        col = pick(pal.plate, t)
        if vnoise(X, Y, 2.0, seed + 13) > 0.82:
            col = mix(col, (80, 40, 24), 0.3)  # a little rust in the seams
        if (X * 5 + Y * 3) % 23 == 0 and n1 > 0.55:
            col = mul(col, 1.35)  # scratches catching the light
        return col
    if mat == "beard":
        # braided steel: vertical plaits 3 wide, each a column of slanted strands (alternating), dark grooves between
        col_i, lx = px // 3, px % 3
        d = (py + (lx if col_i % 2 == 0 else 2 - lx)) % 3
        t = (0.75, 0.5, 0.3)[d] + 0.12 * (vnoise(X, Y, 2.0, seed) - 0.5)
        if face in ("top", "bottom"):
            t = 0.45 + 0.2 * (vnoise(X, Y, 1.5, seed) - 0.5)
        col = pick(pal.beard, t)
        if lx == 2 and face not in ("top", "bottom"):
            col = mul(col, 0.7)
        return col
    if mat == "gem":
        return pick(pal.rune, 0.5 + 0.4 * vnoise(X, Y, 1.5, seed))
    return dc.base_pixel(mat, pal, X, Y, px, py, fw, fh, face, seed)


# ------------------------------------------------------------------------------------------------ decorations

def trim_rows(cv, rect, rows, col):
    fx, fy, fw, fh = rect
    for r in rows:
        yy = r if r >= 0 else fh + r
        for px in range(fw):
            cv.put(fx + px, fy + yy, col)


def deco_trim(cv, c, rects, pal, seed):
    for face in ("front", "back", "left", "right"):
        fx, fy, fw, fh = rects[face]
        for px in range(fw):
            cv.put(fx + px, fy, pal.gold[-2])
            if fh > 1:
                cv.put(fx + px, fy + fh - 1, pal.gold[1])


def deco_tasset(cv, c, rects, pal, seed):
    for face in ("front", "back"):
        r = rects[face]
        trim_rows(cv, r, (-1,), pal.gold[3])
        trim_rows(cv, r, (-2,), pal.gold[1])
        fx, fy, fw, fh = r
        for px in range(1, fw - 1, 2):
            cv.put(fx + px, fy + 1, pal.gold[-1])
        cv.put(fx + fw // 2, fy + fh // 2, pal.gold[4])


def deco_belly(cv, c, rects, pal, seed):
    # the furnace belly: riveted plates; soot above the forge mouth
    fx, fy, fw, fh = rects["front"]
    for py in range(fh):
        for px in range(fw):
            if py < 2:
                cv.put(fx + px, fy + py, mix(cv.get(fx + px, fy + py), (10, 8, 8), 0.4))
    for face in ("left", "right", "back"):
        r = rects[face]
        dc.rivets(cv, r, pal, step=3, rows=(1, -2), metal=pal.gold)


def deco_chest(cv, c, rects, pal, seed):
    # heavy chest plates: gold trim, vertical plate seams with rivets, the royal anvil on the back
    fx, fy, fw, fh = rects["front"]
    trim_rows(cv, rects["front"], (0,), pal.gold[4])
    trim_rows(cv, rects["front"], (1,), pal.gold[2])
    for sx in (5, fw - 6):
        for py in range(2, fh):
            cv.put(fx + sx, fy + py, pal.plate[0])
            cv.put(fx + sx + 1, fy + py, pal.plate[5])
            if py % 3 == 0:
                cv.put(fx + sx - 1, fy + py, pal.gold[5])
                cv.put(fx + sx + 2, fy + py, pal.gold[5])
    for face in ("left", "right"):
        r = rects[face]
        trim_rows(cv, r, (0,), pal.gold[4])
        dc.rivets(cv, r, pal, step=3, rows=(3, -3), metal=pal.gold)
    bx, by, bw, bh = rects["back"]
    trim_rows(cv, rects["back"], (0,), pal.gold[4])
    tx, ty, tw, th = rects["top"]
    for px in range(tw):
        cv.put(tx + px, ty, pal.gold[4])
        cv.put(tx + px, ty + th - 1, pal.gold[3])


def deco_gorget(cv, c, rects, pal, seed):
    for face in ("front", "back", "left", "right"):
        r = rects[face]
        trim_rows(cv, r, (-1,), pal.gold[4])
        fx, fy, fw, fh = r
        for px in range(1, fw - 1, 3):
            cv.put(fx + px, fy + 1, pal.gold[-1])


def deco_forge(cv, c, rects, pal, seed):
    # the brass forge mouth: bright rim, soot-black recess lit by the core, rivets on the rim
    fx, fy, fw, fh = rects["front"]
    for py in range(fh):
        for px in range(fw):
            rim = px == 0 or px == fw - 1 or py == 0 or py == fh - 1
            if rim:
                col = pal.brass[5] if py == 0 else pal.brass[3]
                cv.put(fx + px, fy + py, col)
            else:
                cv.put(fx + px, fy + py, (40, 16, 8))
                cv.glow(fx + px, fy + py, mul(pal.molten_glow, 0.45), "pulse")
    for px in range(1, fw - 1, 3):
        cv.put(fx + px, fy, pal.brass[-1])
        cv.put(fx + px, fy + fh - 1, pal.brass[-1])


def deco_bar(cv, c, rects, pal, seed):
    for face in cm.FACES:
        fx, fy, fw, fh = rects[face]
        for py in range(fh):
            for px in range(fw):
                cv.put(fx + px, fy + py, pick(pal.iron, 0.35 + 0.2 * ((px + py) % 2)))
    # the edges facing the fire glow faintly
    for face in ("left", "right", "bottom"):
        fx, fy, fw, fh = rects[face]
        for py in range(fh):
            for px in range(fw):
                cv.glow(fx + px, fy + py, mul(pal.molten_glow, 0.35), "pulse")


def deco_backplate(cv, c, rects, pal, seed):
    bx, by, bw, bh = rects["back"]
    trim_rows(cv, rects["back"], (0, -1), pal.gold[3])
    for py in range(bh):
        cv.put(bx, by + py, pal.gold[2])
        cv.put(bx + bw - 1, by + py, pal.gold[2])
    # three glowing vent slits and a big rune
    for vy in (4, 7, 10):
        for px in range(3, bw - 3):
            cv.put(bx + px, by + vy, (30, 10, 6))
            cv.glow(bx + px, by + vy, mul(pal.molten_glow, 0.7), "pulse")
    dc.rune(cv, bx + bw // 2 - 1, by + 1, 4, pal, 0.5)


def deco_stack(cv, c, rects, pal, seed):
    for face in ("front", "back", "left", "right"):
        fx, fy, fw, fh = rects[face]
        for py in range(fh):
            for px in range(fw):
                if py % 4 == 3:
                    cv.put(fx + px, fy + py, pal.gold[3] if px % 2 else pal.gold[2])
                elif py < 3:
                    cv.put(fx + px, fy + py, mix(cv.get(fx + px, fy + py), (8, 8, 10), 0.5))


def deco_cap(cv, c, rects, pal, seed):
    tx, ty, tw, th = rects["top"]
    for py in range(1, th - 1):
        for px in range(1, tw - 1):
            cv.put(tx + px, ty + py, (40, 12, 4))
            cv.glow(tx + px, ty + py, mul(pal.ember, 0.9 if (px + py) % 2 else 0.6), "pulse")


def deco_face(cv, c, rects, pal, seed):
    # a stern stone face: deep eye sockets under the brow with burning eyes, carved cheeks
    fx, fy, fw, fh = rects["front"]
    eye_row = 4
    for ex in (1, fw - 4):
        for px in range(ex, ex + 3):
            cv.put(fx + px, fy + eye_row, (14, 8, 6))
            cv.put(fx + px, fy + eye_row - 1, (20, 14, 12))
        for px in (ex, ex + 1, ex + 2):
            cv.glow(fx + px, fy + eye_row, pal.eye if px != ex else mul(pal.eye, 0.7))
            cv.glow(fx + px, fy + eye_row - 1, mul(pal.ember, 0.45))
        cv.put(fx + ex + 1, fy + eye_row, (120, 70, 20))
    for py in range(eye_row + 1, fh):
        cv.put(fx + 1, fy + py, mul(pal.stone[0], 0.9))
        cv.put(fx + fw - 2, fy + py, mul(pal.stone[0], 0.9))
    for face in ("left", "right"):
        sx, sy, sw, sh = rects[face]
        for py in range(sh):
            cv.put(sx + sw // 2, sy + py, mul(pal.stone[0], 0.85))
    bx, by, bw, bh = rects["back"]
    dc.rune(cv, bx + bw // 2 - 1, by + 3, 0, pal, 0.5)


def deco_brow(cv, c, rects, pal, seed):
    for face in ("front",):
        r = rects[face]
        trim_rows(cv, r, (0,), pal.gold[4])
    fx, fy, fw, fh = rects["bottom"]
    for py in range(fh):
        for px in range(fw):
            cv.put(fx + px, fy + py, (12, 10, 10))


def deco_moustache(cv, c, rects, pal, seed):
    for face in ("front", "bottom", "left", "right"):
        fx, fy, fw, fh = rects[face]
        for py in range(fh):
            for px in range(fw):
                k = (px - py) % 3
                cv.put(fx + px, fy + py, pick(pal.beard, 0.3 + 0.2 * k))


def deco_crown(cv, c, rects, pal, seed):
    # the crown band: bright rims, engraved knotwork, a rune gem set in each side
    for face in ("front", "back", "left", "right"):
        fx, fy, fw, fh = rects[face]
        for px in range(fw):
            cv.put(fx + px, fy, pal.gold[-1])
            cv.put(fx + px, fy + fh - 1, pal.gold[1])
            cv.put(fx + px, fy + 1, pal.gold[3] if (px // 2) % 2 else pal.gold[4])
        if face in ("back",):
            cx = fx + fw // 2
            cv.put(cx - 1, fy + 1, pal.rune[3])
            cv.put(cx, fy + 1, pal.rune[3])
            cv.glow(cx - 1, fy + 1, pal.glow, "pulse")
            cv.glow(cx, fy + 1, pal.glow, "pulse")
    tx, ty, tw, th = rects["top"]
    for py in range(th):
        for px in range(tw):
            inner = 0 < px < tw - 1 and 0 < py < th - 1
            if inner:
                cv.put(tx + px, ty + py, mix(pal.gold[1], (40, 20, 10), 0.4))


def deco_peak(cv, c, rects, pal, seed):
    fx, fy, fw, fh = rects["front"]
    for py in range(fh):
        cv.put(fx, fy + py, pal.gold[-2])
    cv.put(fx, fy, pal.gold[-1])
    cv.put(fx + 1, fy, pal.gold[-1])


def deco_point(cv, c, rects, pal, seed):
    fx, fy, fw, fh = rects["front"]
    for px in range(fw):
        cv.put(fx + px, fy, pal.gold[-1])


def deco_beard_top(cv, c, rects, pal, seed):
    # the beard's top hides the mouth: a dark slit between moustache and beard
    tx, ty, tw, th = rects["top"]
    for px in range(tw):
        cv.put(tx + px, ty + th - 1, (14, 12, 14))


def deco_pauldron(cv, c, rects, pal, seed):
    for face in ("front", "back", "left", "right"):
        r = rects[face]
        trim_rows(cv, r, (-1,), pal.gold[4])
        trim_rows(cv, r, (-2,), pal.gold[2])
        dc.rivets(cv, r, pal, step=3, rows=(1,), metal=pal.gold)
    lx, ly, lw, lh = rects["left"]
    dc.rune(cv, lx + lw // 2 - 1, ly + 1, 2, pal, 0.75)  # a rune on the outer side


def deco_dome(cv, c, rects, pal, seed):
    tx, ty, tw, th = rects["top"]
    for py in range(th):
        cv.put(tx + tw // 2, ty + py, pal.gold[4])
        cv.put(tx + tw // 2 - 1, ty + py, pal.gold[2])
    for face in ("front", "back", "left", "right"):
        trim_rows(cv, rects[face], (0,), pal.plate[-1])


def deco_lame(cv, c, rects, pal, seed):
    for face in ("front", "back", "left", "right"):
        trim_rows(cv, rects[face], (-1,), pal.gold[3])


def deco_gauntlet(cv, c, rects, pal, seed):
    for face in ("front", "back", "left", "right"):
        fx, fy, fw, fh = rects[face]
        for py in range(2, fh, 3):
            for px in range(fw):
                cv.put(fx + px, fy + py, pal.plate[0])
    lx, ly, lw, lh = rects["left"]
    dc.rune(cv, lx + lw // 2 - 1, ly + 2, 1, pal, 0.6)


def deco_knee(cv, c, rects, pal, seed):
    fx, fy, fw, fh = rects["front"]
    trim_rows(cv, rects["front"], (0,), pal.gold[4])
    cv.put(fx + fw // 2, fy + 1, pal.gold[-1])


def deco_greave(cv, c, rects, pal, seed):
    for face in ("front", "back", "left", "right"):
        trim_rows(cv, rects[face], (0,), pal.gold[3])


def deco_haft(cv, c, rects, pal, seed):
    # dark wood with a leather-wrapped grip near the bottom (where the fist holds it)
    for face in ("front", "back", "left", "right"):
        fx, fy, fw, fh = rects[face]
        for py in range(fh - 9, fh):
            for px in range(fw):
                cv.put(fx + px, fy + py, (58, 34, 20) if (px + py) % 3 else (36, 20, 12))


def deco_hammer(cv, c, rects, pal, seed):
    # the hammer head: riveted iron, gold edging, a big cyan rune on each flat side
    for face in ("left", "right", "top", "bottom"):
        fx, fy, fw, fh = rects[face]
        for px in range(fw):
            cv.put(fx + px, fy, pal.gold[3])
            cv.put(fx + px, fy + fh - 1, pal.gold[2])
    for face in ("left", "right"):
        fx, fy, fw, fh = rects[face]
        cx, cy = fx + fw // 2, fy + fh // 2
        # a diamond rune frame and a glyph inside
        for k in range(4):
            for sx, sy in ((cx - 4 + k, cy - k), (cx - 4 + k, cy + k), (cx + 3 - k, cy - k), (cx + 3 - k, cy + k)):
                cv.put(sx, sy, pal.rune[2])
                cv.glow(sx, sy, mul(pal.glow, 0.85))
        dc.rune(cv, cx - 1, cy - 2, 3, pal, 1.0)
    tx, ty, tw, th = rects["top"]
    for py in range(2, th - 2, 3):
        cv.put(tx + tw // 2, ty + py, pal.gold[-1])


def deco_strike(cv, c, rects, pal, seed):
    # the striking face, red-hot from the forge
    fx, fy, fw, fh = rects["front"]
    for py in range(fh):
        for px in range(fw):
            d = max(abs(px - (fw - 1) / 2), abs(py - (fh - 1) / 2))
            col = pick(pal.molten, 0.9 - d / 5.0)
            cv.put(fx + px, fy + py, mul(col, 0.7))
            cv.glow(fx + px, fy + py, mul(pal.molten_glow, max(0.25, 1.0 - d / 4.5)), "pulse")
    for face in ("left", "right", "top", "bottom"):
        fx, fy, fw, fh = rects[face]
        for py in range(fh):
            for px in range(fw):
                cv.glow(fx + px, fy + py, mul(pal.molten_glow, 0.3), "pulse")


DECOS = dict(dc.DECOS)
DECOS.update({"trim": deco_trim, "tasset": deco_tasset, "belly": deco_belly, "chest": deco_chest, "gorget": deco_gorget,
              "forge": deco_forge, "bar": deco_bar, "backplate": deco_backplate, "stack": deco_stack, "cap": deco_cap,
              "face": deco_face, "brow": deco_brow, "moustache": deco_moustache, "crown": deco_crown, "peak": deco_peak,
              "point": deco_point, "beard_top": deco_beard_top, "pauldron": deco_pauldron, "dome": deco_dome,
              "lame": deco_lame, "gauntlet": deco_gauntlet, "knee": deco_knee, "greave": deco_greave, "haft": deco_haft,
              "hammer": deco_hammer, "strike": deco_strike})


def paint(model, pal, decos):
    return dc.paint(model, pal, decos, base=base_pixel)


# ------------------------------------------------------------------------------------------------ poses (as IronThaneModel)

smooth = dc.smooth

# Absolute rotations of the slam's key poses (the rest pose is in the cube table): right arm x / z, right forearm,
# hammer x / z, left arm x / z, left forearm, torso lean. IronThaneModel blends to the same numbers.
KEYS = ("ra", "raz", "rf", "hm", "hz", "la", "laz", "lf", "lean")
REST = dict(ra=0.0, raz=0.3, rf=-1.05, hm=1.05, hz=-0.12, la=0.0, laz=-0.1, lf=-0.25, lean=0.0)
SLAM_RAISE = dict(ra=-2.85, raz=0.12, rf=-0.25, hm=2.0, hz=0.0, la=-2.75, laz=-0.15, lf=-0.55, lean=-0.22)
SLAM_HIT = dict(ra=-0.65, raz=0.1, rf=-0.3, hm=2.25, hz=0.0, la=-1.05, laz=-0.2, lf=-0.45, lean=0.42)
SLAM_TOTAL = 24.0  # IronThaneEntity.SLAM_POSE_TICKS: raise 0-10, smash 10-13 (the blow lands at 13), hold to 16, recover to 24
SLAM_DROP = 2.0  # how far the hips sink at the impact


def slam_weights(elapsed):
    """(target pose, weight, impact) of the slam `elapsed` ticks in (keep in step with IronThaneModel)."""
    if elapsed < 10:
        return SLAM_RAISE, smooth(elapsed / 10), 0.0
    if elapsed < 13:
        k = smooth((elapsed - 10) / 3)
        return {key: SLAM_RAISE[key] + (SLAM_HIT[key] - SLAM_RAISE[key]) * k for key in KEYS}, 1.0, k
    if elapsed < 16:
        return SLAM_HIT, 1.0, 1.0
    w = 1.0 - smooth((elapsed - 16) / 8)
    return SLAM_HIT, w, w


def pose(walk_pos=0.0, speed=0.0, slam=None, t=0.0, yaw=0.0, pitch=0.0):
    """The deltas IronThaneModel.setupAnim adds to the rest pose (keep the two in step)."""
    p = {}

    def d(name, **kw):
        q = p.setdefault(name, {})
        for k, v in kw.items():
            q[k] = q.get(k, 0) + v

    pos = walk_pos * 0.45
    c, s = math.cos(pos), math.sin(pos)
    lift = abs(s) ** 0.6
    breath = math.sin(t * 0.06)
    cur = dict(REST)
    cur["ra"] += -c * 0.12 * speed
    cur["la"] += c * 0.35 * speed
    cur["lean"] += 0.05 * speed + breath * 0.02
    d("r_leg", rx=c * 0.6 * speed)
    d("l_leg", rx=-c * 0.6 * speed)
    d("r_tasset", rx=-max(0.0, -c) * 0.5 * speed)
    d("l_tasset", rx=-max(0.0, c) * 0.5 * speed)
    hips_y = -lift * 1.6 * speed
    hips_rz = s * 0.05 * speed
    if slam is not None:
        target, w, impact = slam_weights(slam)
        cur = {k: cur[k] + (target[k] - cur[k]) * w for k in KEYS}
        hips_y = hips_y * (1 - w) + SLAM_DROP * impact
        hips_rz *= 1 - w
    d("hips", y=hips_y, rz=hips_rz)
    d("torso", rx=cur["lean"], ry=c * 0.05 * speed)
    d("r_arm", rx=cur["ra"] - REST["ra"], rz=cur["raz"] - REST["raz"])
    d("r_forearm", rx=cur["rf"] - REST["rf"])
    d("hammer", rx=cur["hm"] - REST["hm"], rz=cur["hz"] - REST["hz"])
    d("l_arm", rx=cur["la"] - REST["la"], rz=cur["laz"] - REST["laz"])
    d("l_forearm", rx=cur["lf"] - REST["lf"])
    calm = (1 - speed) * (1 - (slam_weights(slam)[1] if slam is not None else 0.0))
    d("head", rx=pitch - cur["lean"] * 0.6, ry=yaw + math.sin(t * 0.025) * 0.18 * calm)
    d("crown", rz=-hips_rz * 0.8)
    d("beard", rx=-cur["lean"] * 0.5 + math.sin(t * 0.05) * 0.03)
    sway = c * 0.08 * speed + math.sin(t * 0.07) * 0.04
    d("r_braid", rz=sway)
    d("l_braid", rz=sway)
    beat = max(0.0, math.sin(t * 0.15)) ** 3
    p.setdefault("core", {})["s"] = 1 + 0.08 * beat
    return p


HERO_EXTRA = [(-1.0, 0.15, "slam raise", dict(slam=10)), (-1.2, 0.1, "slam impact", dict(slam=12.5))]


# ------------------------------------------------------------------------------------------------ main

def main():
    rows = M.pack(max_h=512)
    print(f"packed {len(M.cubes)} cubes, {len(M.owners())} unique UV regions, {rows} rows used, texture {M.width}x{M.height}")
    cm.check_zfight(M)
    pal = Pal()
    cv = paint(M, pal, DECOS)
    TEX_DIR.mkdir(parents=True, exist_ok=True)
    cv.img.save(TEX_DIR / f"{NAME}.png")
    cv.layers["glow"].save(TEX_DIR / f"{NAME}_glow.png")
    cv.layers["pulse"].save(TEX_DIR / f"{NAME}_pulse.png")
    order = "right arm x / z, right forearm, hammer x / z, left arm x / z, left forearm, torso lean"
    extra = (cm.java_floats("SLAM_RAISE", [SLAM_RAISE[k] for k in KEYS], f"The slam's raised pose (absolute rotations): {order}.")
             + cm.java_floats("SLAM_HIT", [SLAM_HIT[k] for k in KEYS], "The slam's impact pose (same order).")
             + "\n    /** How far (model pixels) the hips sink at the slam's impact. */\n"
             + f"    static final float SLAM_DROP = {cm.fnum(SLAM_DROP)};\n")
    JAVA_OUT.write_text(M.java("com.skyrpg.combat.client.mob", "IronThaneLayout", "generate_iron_thane.py",
                               "The Iron Thane's cubes and their UVs.", extra), encoding="utf-8", newline="\n")
    print("wrote textures and", JAVA_OUT.name)
    if "--preview" in sys.argv:
        out = Path(sys.argv[sys.argv.index("--preview") + 1])
        walk = [(dict(pose=pose(walk_pos=math.pi / 2 / 0.45 * f, speed=1.0), yaw=-1.2, pitch=0.1), f"walk {f}") for f in (0.0, 1.0)]
        slam = [(dict(pose=pose(slam=e), yaw=-1.25, pitch=0.12), f"slam tick {e}") for e in (5, 10, 11.5, 13)]
        dc.previews(M, cv, out, hitbox=(1.6, 3.0), tag=NAME, pose_fn=pose, extra=walk + slam, ppx=6, units=(84, 96))
        print("previews in", out)


if __name__ == "__main__":
    main()
