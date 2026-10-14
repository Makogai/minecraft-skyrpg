#!/usr/bin/env python3
"""Draws the Dwarven Construct's model textures and writes its model definition.

    python tools/art/generate_dwarven_construct.py [--preview DIR]

A stone-and-brass dwarven automaton (about 1.0 x 2.1 blocks): a deepslate-brick body, oxidised-copper shoulders,
boiler and smoke stacks, brass joints and bands, a stubby helmeted head with glowing eye slits and an iron braided-beard
grille, and a glowing cyan rune core in the chest. The CUBES table below is the single source of truth (see
cube_models.py): it is packed into the texture, painted, and written out as DwarvenConstructLayout.java. Outputs:

    combat/src/main/resources/assets/skyrpg_combat/textures/entity/mob/dwarven_construct.png
    ... dwarven_construct_glow.png    (steady glow: eye slits, runes, stack vents; drawn fullbright)
    ... dwarven_construct_pulse.png   (the rune core; drawn fullbright with a pulsing brightness)
    combat/src/client/java/com/skyrpg/combat/client/mob/DwarvenConstructLayout.java
    the spawn egg's texture and item model

The material painters here are shared with generate_iron_thane.py. --preview renders views and poses of the model
into DIR (see cube_models.render_view).
"""
import json
import math
import random
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import cube_models as cm  # noqa: E402
from cube_models import _h, add, hexc, mix, mul, pick, ramp, vnoise  # noqa: E402

ROOT = Path(__file__).resolve().parents[2]
ASSETS = ROOT / "combat/src/main/resources/assets/skyrpg_combat"
TEX_DIR = ASSETS / "textures/entity/mob"
JAVA_OUT = ROOT / "combat/src/client/java/com/skyrpg/combat/client/mob/DwarvenConstructLayout.java"
NAME = "dwarven_construct"

# ------------------------------------------------------------------------------------------------ cube table
# Feet at y=24. The hips (y 15) carry the torso; the head sinks into the shoulders; arms hang to above the knee.
M = cm.Model(128)

M.part("hips", None, (0, 15, 0))
M.cube("hips", "pelvis", "brick", (-6, -4, -4), (12, 5, 8))
M.cube("hips", "belt", "brass", (-6.5, -5, -5), (13, 2, 10), deco="belt")
M.cube("hips", "buckle", "brass", (-2, -5.5, -5.5), (4, 3, 1), deco="buckle")
M.cube("hips", "apron", "iron", (-2.5, -3, -5), (5, 6, 1), deco="apron")
M.cube("hips", "seat", "iron", (-4, -3.2, 3.6), (8, 4, 1), deco="apron")

M.part("torso", "hips", (0, -4, 0))
M.cube("torso", "belly", "brick", (-7, -6, -4.5), (14, 6, 9))
M.cube("torso", "chest", "brick", (-8, -16, -5.5), (16, 11, 11), deco="chest")
M.cube("torso", "band", "brass", (-8.5, -5.9, -6), (17, 1, 12), deco="band")
M.cube("torso", "frame", "brass", (-3, -9.5, -6.5), (6, 6, 1), deco="frame")
M.cube("torso", "gorget", "copper", (-5, -17.5, -4.5), (10, 2, 8), deco="gorget")
M.cube("torso", "boiler", "copper", (-5, -15, 5.5), (10, 11, 4), deco="boiler")
M.cube("torso", "stack_r", "copper", (-4.5, -22, 6.3), (3, 8, 3), deco="stack")
M.cube("torso", "stack_l", "copper", (1.5, -20, 6.3), (3, 6, 3), deco="stack")
M.cube("torso", "cap_r", "brass", (-5, -23, 5.8), (4, 1, 4), deco="cap")
M.cube("torso", "cap_l", "brass", (1, -21, 5.8), (4, 1, 4), share="cap_r")
M.pair("torso", "pipe", "copper", (-9, -14, 4), (4, 2, 2), deco="pipe")
M.part("core", "torso", (0, -6.5, -6.5))
M.cube("core", "core", "rune", (-2, -2, -0.6), (4, 4, 1), glow="pulse")

M.part("head", "torso", (0, -15, -2))
M.cube("head", "skull", "stone", (-4, -6, -4.5), (8, 7, 8), deco="head")
M.cube("head", "helm", "copper", (-4.5, -6.5, -5), (9, 2, 9), deco="helm")
M.cube("head", "crest", "brass", (-0.5, -7.5, -5.2), (1, 1, 9))
M.cube("head", "brow", "iron", (-4.5, -4.5, -5.5), (9, 1, 1), deco="brow")
M.cube("head", "visor", "iron", (-3.5, -3.5, -5.1), (7, 2, 1), deco="visor")
M.cube("head", "nose", "stone", (-1, -3, -5.9), (2, 3, 1), deco="nose")
M.pair("head", "cheek", "brass", (-4.6, -3.6, -3.4), (1, 3, 3), deco="rivet")
M.part("beard", "head", (0, 0, -4.5))
M.cube("beard", "grille", "iron", (-3.5, -0.5, -1), (7, 4, 1), deco="grille")
M.cube("beard", "moustache", "iron", (-4.5, -1.2, -1.5), (9, 1, 1), deco="braid")
M.pair("beard", "braid", "iron", (-4, 3.3, -1.2), (2, 4, 1), deco="braid")
M.cube("beard", "braid_c", "iron", (-1, 3.3, -1.3), (2, 2, 1), deco="braid")
M.pair("beard", "bead", "brass", (-4.5, 6.6, -1.7), (3, 1, 2))
M.cube("beard", "bead_c", "brass", (-1.5, 4.6, -1.8), (3, 1, 2), share="bead_r")

ARM = [
    ("pauldron", "copper", (-4, -4, -4.5), (7, 5, 9), dict(deco="pauldron")),
    ("pauld_top", "copper", (-3, -5, -3.5), (5, 1, 7), dict(deco="dome")),
    ("pauld_rim", "brass", (-4.5, 0, -5), (8, 1, 10), dict(deco="rivet")),
    ("upper", "brick", (-3, 0, -2.5), (5, 6, 5), {}),
]
FORE = [
    ("elbow", "brass", (-3, -1, -3), (6, 2, 6), dict(deco="rivet")),
    ("fore", "stone", (-3, 1, -3), (6, 5, 6), dict(deco="fore")),
    ("fist", "stone", (-3.5, 6, -3.5), (7, 5, 7), dict(deco="fist")),
    ("knuckle", "brass", (-4, 7, -4), (8, 2, 8), dict(deco="knuckle")),
]
M.sided("arm", "torso", (-10, -13, 0), (0, 0, 0.08), ARM)
M.sided("forearm", "{s}_arm", (-0.5, 6, 0), (-0.12, 0, 0), FORE)

LEG = [
    ("thigh", "brick", (-3, -1, -3), (6, 5, 6), {}),
    ("knee", "brass", (-3.5, 3, -3.5), (7, 2, 7), dict(deco="rivet")),
    ("shin", "brick", (-3, 5, -3), (6, 3, 6), {}),
    ("boot", "stone", (-3.5, 6, -5), (7, 3, 9), dict(deco="boot")),
    ("toe", "brass", (-3, 6.9, -5.5), (6, 2, 1), dict(deco="rivet")),
]
M.sided("leg", None, (-3.5, 15, 0), (0, 0, 0), LEG)


# ------------------------------------------------------------------------------------------------ palette

class Pal:
    """The dwarven construct materials (the Iron Thane overrides some)."""

    def __init__(self):
        self.brick = ramp("23232a", "2d2d35", "37373f", "42424b", "4e4e57", "5c5c65")
        self.mortar = hexc("141418")
        self.stone = ramp("2a2a32", "34343c", "3f3f48", "4b4b54", "585862", "686872")
        self.brass = ramp("3a260c", "5a3e14", "7e5a24", "a27c36", "c29c4c", "dcbc6c", "f0dc9c")
        self.patina = hexc("3e6a52")
        self.copper = ramp("183e36", "205244", "2a6656", "357c68", "48967e", "64b298")
        self.raw = ramp("5a2c1a", "7a3e24", "9c5230", "bc6a3e", "d88a56")
        self.iron = ramp("1c1e24", "272a31", "33373f", "41464f", "525862", "686e79", "868d98")
        self.rune = ramp("06303a", "0a4a58", "0e6a78", "14949e", "22c0c8", "60e4ea")
        self.gold = ramp("4a2e06", "74500e", "a07418", "c89a26", "e6c040", "f8e080")
        self.glow = (60, 230, 255)
        self.eye = (150, 245, 255)
        self.rune_glow = 0.55  # how bright the carved runes glow


def bevel(face, px, py, fw, fh):
    """Light on the upper-left edges and shadow on the lower-right ones, so neighbouring cubes read apart."""
    k = 1.0
    if face in ("left", "front", "right", "back"):
        if py == 0:
            k *= 1.1
        elif py == fh - 1:
            k *= 0.8
        if px == 0 or px == fw - 1:
            k *= 0.92
    else:
        if px in (0, fw - 1) or py in (0, fh - 1):
            k *= 1.06 if face == "top" else 0.9
    return k


FACE_LIGHT = {"top": 1.06, "bottom": 0.78, "left": 0.94, "front": 1.0, "right": 0.94, "back": 0.9}


def base_pixel(mat, pal, X, Y, px, py, fw, fh, face, seed):
    n1 = vnoise(X, Y, 3.0, seed)
    n3 = _h(X, Y, seed + 3)
    if mat == "brick":
        bh, bw = 3, 6
        row = py // bh
        off = 3 if row % 2 else 0
        col_i = (px + off) // bw
        iy, ix = py % bh, (px + off) % bw
        tone = _h(row, col_i, seed + 17)
        col = pick(pal.brick, 0.2 + 0.55 * tone + 0.25 * (n1 - 0.5) + 0.12 * (n3 - 0.5))
        if iy == bh - 1 or ix == bw - 1:
            col = mix(pal.mortar, col, 0.25)
        elif iy == 0:
            col = mul(col, 1.1)
        if vnoise(X + 5, Y, 2.0, seed + 40) > 0.83 and n3 > 0.4:
            col = mul(col, 0.7)  # a chipped corner
        if (X * 3 + Y * 5) % 19 == 0 and n1 > 0.6:
            col = mix(col, pal.mortar, 0.6)  # hairline crack
    elif mat == "stone":
        col = pick(pal.stone, 0.3 + 0.45 * n1 + 0.15 * (n3 - 0.5))
        if py % 4 == 3 and face != "top":
            col = mul(col, 0.92)  # chisel lines
        if n3 > 0.94:
            col = mul(col, 0.75)
    elif mat in ("brass", "gold"):
        r = pal.brass if mat == "brass" else pal.gold
        t = 0.3 + 0.35 * n1 + 0.12 * (n3 - 0.5)
        if face in ("left", "front", "right", "back"):
            t += 0.22 * (1 - py / max(1, fh - 1)) - 0.08  # sheen from the top
        elif face == "top":
            t += 0.15
        col = pick(r, t)
        if vnoise(X, Y, 2.5, seed + 9) > 0.76:
            col = mix(col, pal.patina if mat == "brass" else r[1], 0.45)
        if (X + 2 * Y) % 13 == 0 and n3 > 0.5:
            col = mul(col, 1.15)  # scratch glint
    elif mat == "copper":
        col = pick(pal.copper, 0.25 + 0.5 * n1 + 0.15 * (n3 - 0.5))
        worn = vnoise(X + 31, Y + 7, 3.5, seed + 23)
        edge = min(px, py, fw - 1 - px, fh - 1 - py)
        if worn > 0.7 - (0.12 if edge == 0 else 0):
            col = pick(pal.raw, 0.3 + 0.5 * vnoise(X, Y, 2, seed + 4))  # bare copper where it's worn
        elif (X * 7) % 5 == 0 and vnoise(X, Y / 4.0, 2.0, seed + 5) > 0.55 and face != "top":
            col = mul(col, 1.18)  # verdigris streaks
    elif mat == "iron":
        col = pick(pal.iron, 0.25 + 0.45 * n1 + 0.12 * (n3 - 0.5))
        if py % 2 == 0 and face != "top":
            col = mul(col, 1.04)  # brushed
        if vnoise(X, Y, 2.0, seed + 13) > 0.8:
            col = mix(col, (96, 52, 30), 0.35)  # rust
    elif mat == "rune":
        col = pick(pal.rune, 0.45 + 0.45 * vnoise(X, Y, 1.5, seed) + 0.1 * (n3 - 0.5))
    elif mat == "molten":
        col = pick(pal.molten, 0.4 + 0.5 * vnoise(X, Y, 1.6, seed))
    elif mat == "beard":
        # braided iron: diagonal plaits, light on the ridges
        k = (px + py) % 4
        col = pick(pal.iron, 0.3 + 0.12 * k + 0.2 * (n1 - 0.5))
        if (px - py) % 4 == 0:
            col = mul(col, 0.75)
    elif mat == "wood":
        col = pick(pal.wood, 0.3 + 0.4 * vnoise(X, Y * 0.3, 2.0, seed) + 0.15 * (n3 - 0.5))
    else:
        col = (255, 0, 255)
    return col


def paint_cube(cv, c, pal, decos, seed=1, base=None):
    base = base or base_pixel
    rects = cm.face_rects(c)
    s = seed + sum(ord(ch) for ch in c["id"])
    for face, (fx, fy, fw, fh) in rects.items():
        for py in range(fh):
            for px in range(fw):
                X, Y = fx + px, fy + py
                col = base(c["mat"], pal, X, Y, px, py, fw, fh, face, s)
                col = mul(col, FACE_LIGHT[face] * bevel(face, px, py, fw, fh))
                cv.put(X, Y, col)
    if c.get("deco"):
        decos[c["deco"]](cv, c, rects, pal, s)
    if c.get("glow"):
        glow_face_all(cv, c, rects, pal, s, c["glow"])


def glow_face_all(cv, c, rects, pal, seed, layer):
    """The whole cube glows (the rune core): bright facets, a white-hot centre on the front."""
    base = {"rune": pal.glow, "molten": getattr(pal, "molten_glow", None), "gem": pal.glow}[c["mat"]]
    for face, (fx, fy, fw, fh) in rects.items():
        for py in range(fh):
            for px in range(fw):
                X, Y = fx + px, fy + py
                k = 0.55 + 0.45 * vnoise(X, Y, 1.5, seed + 2)
                cx, cy = (fw - 1) / 2, (fh - 1) / 2
                if face == "front" and abs(px - cx) <= 0.6 and abs(py - cy) <= 0.6:
                    col = (255, 250, 200) if c["mat"] == "molten" else (255, 255, 255)
                    cv.glow(X, Y, col, layer)
                    continue
                cv.glow(X, Y, mul(base, k), layer)


# ------------------------------------------------------------------------------------------------ decorations

RUNES = [  # 3x4 dwarven glyphs, carved into stone and glowing faintly
    ["X.X", "XX.", "X.X", "X.."],
    [".X.", "XXX", ".X.", "X.X"],
    ["XX.", "X.X", "XX.", "X.X"],
    ["X..", "XX.", "X.X", "X.."],
    ["XXX", "..X", ".X.", "X.."],
]


def rune(cv, x, y, glyph, pal, strength=1.0, layer="glow"):
    for gy, line in enumerate(RUNES[glyph % len(RUNES)]):
        for gx, ch in enumerate(line):
            if ch == "X":
                cv.put(x + gx, y + gy, mul(pal.rune[2], 0.9))
                cv.glow(x + gx, y + gy, mul(pal.glow, pal.rune_glow * strength), layer)


def rivets(cv, rect, pal, step=3, rows=(0,), metal=None):
    fx, fy, fw, fh = rect
    r = metal or pal.brass
    for py in rows:
        yy = py if py >= 0 else fh + py
        for px in range(1, fw - 1, step):
            cv.put(fx + px, fy + yy, r[-1])
            if yy + 1 < fh:
                cv.put(fx + px, fy + yy + 1, mul(r[1], 0.9))


def deco_rivet(cv, c, rects, pal, seed):
    for face in ("front", "back", "left", "right"):
        fx, fy, fw, fh = rects[face]
        if fh >= 2 and fw >= 3:
            for px in range(1, fw - 1, 3):
                cv.put(fx + px, fy + fh // 2, pal.brass[-1] if c["mat"] != "brass" else pal.brass[1])


def deco_belt(cv, c, rects, pal, seed):
    # a brass belt with an engraved zig-zag of dwarven knotwork and rivets along the edge
    for face in ("front", "back", "left", "right"):
        fx, fy, fw, fh = rects[face]
        for px in range(fw):
            k = (px % 4)
            cv.put(fx + px, fy + (1 if k in (1, 2) else 0), mul(pal.brass[1], 0.9))
        for px in range(2, fw, 4):
            cv.put(fx + px, fy + fh - 1, pal.brass[-1])


def deco_buckle(cv, c, rects, pal, seed):
    # a little cog: dark hub, teeth on the rim
    fx, fy, fw, fh = rects["front"]
    for py in range(fh):
        for px in range(fw):
            edge = px in (0, fw - 1) or py in (0, fh - 1)
            cv.put(fx + px, fy + py, pal.brass[5] if edge and (px + py) % 2 == 0 else pal.brass[3])
    cv.put(fx + 1, fy + 1, pal.brass[1])
    cv.put(fx + 2, fy + 1, pal.brass[1])


def deco_apron(cv, c, rects, pal, seed):
    fx, fy, fw, fh = rects["front"]
    for py in range(fh):
        cv.put(fx, fy + py, pal.brass[3])
        cv.put(fx + fw - 1, fy + py, pal.brass[2])
    for px in range(fw):
        cv.put(fx + px, fy, pal.brass[4])
    cv.put(fx + 1, fy + 1, pal.brass[-1])
    cv.put(fx + fw - 2, fy + 1, pal.brass[-1])
    # a hammer-and-anvil mark
    cx = fx + fw // 2
    for py in range(2, fh - 1):
        cv.put(cx, fy + py, pal.iron[0])
    cv.put(cx - 1, fy + 2, pal.iron[0])
    cv.put(cx + 1, fy + 2, pal.iron[0])


def deco_chest(cv, c, rects, pal, seed):
    fx, fy, fw, fh = rects["front"]
    # a brass seam down the sides of the front, and faint runes either side of the core frame
    for gx, gy, g in ((2, 5, 1), (fw - 5, 5, 4)):
        rune(cv, fx + gx, fy + gy, g, pal, 0.6)
    # the back: a riveted access plate behind the boiler
    bx, by, bw, bh = rects["back"]
    for px in range(bw):
        cv.put(bx + px, by, pal.brass[3])
    # the shoulders' top: worn flat where the pauldrons sit
    tx, ty, tw, th = rects["top"]
    for px in range(tw):
        cv.put(tx + px, ty + th // 2, mul(pal.brick[1], 0.9))


def deco_band(cv, c, rects, pal, seed):
    for face in ("front", "back", "left", "right"):
        fx, fy, fw, fh = rects[face]
        for px in range(1, fw - 1, 3):
            cv.put(fx + px, fy, pal.brass[-1])


def deco_frame(cv, c, rects, pal, seed):
    # the brass housing of the rune core: bright rim, dark recess, rivets on the corners, glowing spill around the core
    fx, fy, fw, fh = rects["front"]
    for py in range(fh):
        for px in range(fw):
            rim = px in (0, fw - 1) or py in (0, fh - 1)
            col = pal.brass[4] if rim else mul(pal.brass[1], 0.8)
            cv.put(fx + px, fy + py, col)
            if not rim:
                d = max(abs(px - (fw - 1) / 2), abs(py - (fh - 1) / 2))
                cv.glow(fx + px, fy + py, mul(pal.glow, 0.35 if d > 2 else 0.5), "pulse")
    for px, py in ((0, 0), (fw - 1, 0), (0, fh - 1), (fw - 1, fh - 1)):
        cv.put(fx + px, fy + py, pal.brass[-1])


def deco_gorget(cv, c, rects, pal, seed):
    for face in ("front", "back", "left", "right"):
        fx, fy, fw, fh = rects[face]
        for px in range(fw):
            cv.put(fx + px, fy + fh - 1, pal.brass[2])


def deco_boiler(cv, c, rects, pal, seed):
    # the copper boiler on the back: brass bands, a pressure gauge, and a glowing rune vent
    bx, by, bw, bh = rects["back"]
    for py in (1, bh - 2):
        for px in range(bw):
            cv.put(bx + px, by + py, pal.brass[3] if px % 3 else pal.brass[-1])
    # vent grille
    for py in range(4, 8):
        for px in range(2, bw - 2):
            if px % 2 == 0:
                cv.put(bx + px, by + py, pal.iron[0])
                cv.glow(bx + px, by + py, mul(pal.glow, 0.7))
            else:
                cv.put(bx + px, by + py, pal.iron[3])
    # gauge
    for px, py in ((bw - 3, 3), (bw - 2, 3)):
        cv.put(bx + px, by + py, (220, 214, 190))
    for face in ("left", "right"):
        fx, fy, fw, fh = rects[face]
        for py in (1, fh - 2):
            for px in range(fw):
                cv.put(fx + px, fy + py, pal.brass[3])


def deco_stack(cv, c, rects, pal, seed):
    # smoke stacks: brass rings, soot towards the top
    for face in ("front", "back", "left", "right"):
        fx, fy, fw, fh = rects[face]
        for py in range(fh):
            for px in range(fw):
                if py % 3 == 2:
                    cv.put(fx + px, fy + py, pal.brass[3])
                elif py < 2:
                    cv.put(fx + px, fy + py, mix(cv.get(fx + px, fy + py), (20, 20, 22), 0.55))


def deco_cap(cv, c, rects, pal, seed):
    # the open top of a stack, with rune-light glowing inside
    tx, ty, tw, th = rects["top"]
    for py in range(1, th - 1):
        for px in range(1, tw - 1):
            cv.put(tx + px, ty + py, (16, 20, 24))
            cv.glow(tx + px, ty + py, mul(pal.glow, 0.8))


def deco_pipe(cv, c, rects, pal, seed):
    for face in ("front", "back", "top", "bottom"):
        fx, fy, fw, fh = rects[face]
        for py in range(fh):
            cv.put(fx + 1, fy + py, pal.brass[4])


def deco_head(cv, c, rects, pal, seed):
    # stern carved cheeks, a mouth slot hidden by the grille, a rune on the back of the head
    fx, fy, fw, fh = rects["front"]
    for py in range(fh):
        cv.put(fx, fy + py, mul(cv.get(fx, fy + py), 0.8))
        cv.put(fx + fw - 1, fy + py, mul(cv.get(fx + fw - 1, fy + py), 0.8))
    bx, by, bw, bh = rects["back"]
    rune(cv, bx + 2, by + 2, 3, pal, 0.6)
    for side in ("left", "right"):
        sx, sy, sw, sh = rects[side]
        for py in range(2, sh - 1):
            cv.put(sx + sw // 2, sy + py, mul(pal.stone[0], 0.9))  # ear seam


def deco_helm(cv, c, rects, pal, seed):
    for face in ("front", "back", "left", "right"):
        fx, fy, fw, fh = rects[face]
        for px in range(fw):
            cv.put(fx + px, fy + fh - 1, pal.brass[3])
            if px % 3 == 1:
                cv.put(fx + px, fy, pal.brass[-1])
    tx, ty, tw, th = rects["top"]
    for py in range(th):
        cv.put(tx + tw // 2, ty + py, pal.brass[2])


def deco_brow(cv, c, rects, pal, seed):
    fx, fy, fw, fh = rects["front"]
    for px in range(fw):
        cv.put(fx + px, fy, pal.iron[5] if px % 2 else pal.iron[4])


def deco_visor(cv, c, rects, pal, seed):
    # a dark eye band with two glowing slits (2 wide), the nose bridge between them
    fx, fy, fw, fh = rects["front"]
    for py in range(fh):
        for px in range(fw):
            cv.put(fx + px, fy + py, (10, 12, 16))
    for ex in (1, fw - 3):
        for px in (ex, ex + 1):
            cv.put(fx + px, fy, (40, 120, 130))
            cv.glow(fx + px, fy, pal.eye)
            cv.glow(fx + px, fy + 1, mul(pal.glow, 0.45))
    for face in ("top", "left", "right", "bottom"):
        rx, ry, rw, rh = rects[face]
        for py in range(rh):
            for px in range(rw):
                cv.put(rx + px, ry + py, pal.iron[1])


def deco_nose(cv, c, rects, pal, seed):
    fx, fy, fw, fh = rects["front"]
    cv.put(fx, fy + fh - 1, pal.stone[0])
    cv.put(fx + 1, fy + fh - 1, pal.stone[0])


def deco_grille(cv, c, rects, pal, seed):
    # the beard grille: vertical iron bars over a dark mouth, plaited at the bottom
    fx, fy, fw, fh = rects["front"]
    for py in range(fh):
        for px in range(fw):
            if px % 2 == 0:
                col = pick(pal.iron, 0.55 + 0.25 * (py / fh))
            else:
                col = (14, 14, 18) if py < fh - 1 else pal.iron[2]
            cv.put(fx + px, fy + py, col)
    for px in range(fw):
        cv.put(fx + px, fy, pal.iron[5])


def deco_braid(cv, c, rects, pal, seed):
    for face in ("front", "back", "left", "right"):
        fx, fy, fw, fh = rects[face]
        for py in range(fh):
            for px in range(fw):
                cv.put(fx + px, fy + py, pick(pal.iron, 0.35 + 0.35 * ((px + py) % 2)))


def deco_pauldron(cv, c, rects, pal, seed):
    # oxidised copper shoulder: brass trim at the bottom, a raised rib on top, rivets
    for face in ("front", "back", "left", "right"):
        fx, fy, fw, fh = rects[face]
        for px in range(fw):
            cv.put(fx + px, fy + fh - 1, pal.brass[3])
        for px in range(1, fw - 1, 3):
            cv.put(fx + px, fy + 1, pal.brass[-1])
    tx, ty, tw, th = rects["top"]
    for py in range(th):
        cv.put(tx + tw // 2, ty + py, pal.copper[-1])
        cv.put(tx + tw // 2 - 1, ty + py, mul(pal.copper[0], 0.9))
    # an outward-facing rune on the outer side (the -x side; mirrored for the left)
    lx, ly, lw, lh = rects["left"]
    rune(cv, lx + lw // 2 - 1, ly, 0, pal, 0.7)


def deco_dome(cv, c, rects, pal, seed):
    tx, ty, tw, th = rects["top"]
    for py in range(th):
        cv.put(tx + tw // 2, ty + py, pal.copper[-1])
    for face in ("front", "back", "left", "right"):
        fx, fy, fw, fh = rects[face]
        for px in range(fw):
            cv.put(fx + px, fy, mul(cv.get(fx + px, fy), 1.12))


def deco_fist(cv, c, rects, pal, seed):
    # knuckles: four bumps on the front and underside, finger seams
    for face in ("front", "bottom"):
        fx, fy, fw, fh = rects[face]
        for px in range(fw):
            for py in range(fh):
                if px % 2 == 1 and face == "front":
                    cv.put(fx + px, fy + py, mul(cv.get(fx + px, fy + py), 0.82))
        for px in range(0, fw, 2):
            cv.put(fx + px, fy + (fh - 1 if face == "front" else 0), pal.stone[-1])
    sx, sy, sw, sh = rects["right"]
    for py in range(sh):
        cv.put(sx + 1, sy + py, mul(pal.stone[0], 0.85))  # thumb seam


def deco_fore(cv, c, rects, pal, seed):
    # polished forearm plates: a brass strip down the outer side, a rune on the back of the arm
    for face in ("left", "front", "back"):
        fx, fy, fw, fh = rects[face]
        for py in range(fh):
            cv.put(fx, fy + py, mul(cv.get(fx, fy + py), 0.8))
    lx, ly, lw, lh = rects["left"]
    for py in range(lh):
        cv.put(lx + lw // 2, ly + py, pal.brass[3] if py % 2 else pal.brass[5])
    bx, by, bw, bh = rects["back"]
    rune(cv, bx + 2, by + 1, 2, pal, 0.5)


def deco_knuckle(cv, c, rects, pal, seed):
    for face in ("front", "back", "left", "right"):
        fx, fy, fw, fh = rects[face]
        for px in range(0, fw, 2):
            cv.put(fx + px, fy, pal.brass[-1])
            cv.put(fx + px, fy + fh - 1, pal.brass[1])


def deco_boot(cv, c, rects, pal, seed):
    fx, fy, fw, fh = rects["front"]
    for px in range(fw):
        cv.put(fx + px, fy, pal.brass[3])
    tx, ty, tw, th = rects["top"]
    for px in range(tw):
        cv.put(tx + px, ty + th - 3, pal.brass[2])  # strap across the instep


DECOS = {"rivet": deco_rivet, "belt": deco_belt, "buckle": deco_buckle, "apron": deco_apron, "chest": deco_chest,
         "band": deco_band, "frame": deco_frame, "gorget": deco_gorget, "boiler": deco_boiler, "stack": deco_stack,
         "cap": deco_cap, "pipe": deco_pipe, "head": deco_head, "helm": deco_helm, "brow": deco_brow, "visor": deco_visor,
         "nose": deco_nose, "grille": deco_grille, "braid": deco_braid, "pauldron": deco_pauldron, "fist": deco_fist,
         "knuckle": deco_knuckle, "boot": deco_boot, "dome": deco_dome, "fore": deco_fore}


def paint(model, pal, decos, base=None):
    cv = cm.Canvas(model.width, model.height, layers=("glow", "pulse"))
    for c in model.owners():
        paint_cube(cv, c, pal, decos, base=base)
    return cv


# ------------------------------------------------------------------------------------------------ poses (as DwarvenConstructModel)

def smooth(x):
    x = max(0.0, min(1.0, x))
    return x * x * (3 - 2 * x)


# The punch's two key poses, added to the rest pose: right arm, right forearm, torso yaw, torso lean, left arm, left
# forearm (written into DwarvenConstructLayout for DwarvenConstructModel).
PUNCH_KEYS = ("ra", "rf", "ty", "tx", "la", "lf")
PUNCH_BACK = dict(ra=0.55, rf=-1.0, ty=-0.35, tx=-0.05, la=-0.35, lf=-0.7)
PUNCH_HIT = dict(ra=-1.55, rf=0.05, ty=0.45, tx=0.18, la=-0.5, lf=-0.9)


def pose(walk_pos=0.0, speed=0.0, attack=0.0, t=0.0, yaw=0.0, pitch=0.0):
    """The deltas DwarvenConstructModel.setupAnim adds to the rest pose (keep the two in step)."""
    p = {}

    def d(name, **kw):
        q = p.setdefault(name, {})
        for k, v in kw.items():
            q[k] = q.get(k, 0) + v

    pos = walk_pos * 0.5
    c, s = math.cos(pos), math.sin(pos)
    lift = abs(s) ** 0.6
    d("r_leg", rx=c * 0.75 * speed)
    d("l_leg", rx=-c * 0.75 * speed)
    d("hips", y=-lift * 1.4 * speed, rz=s * 0.06 * speed)
    d("torso", ry=c * 0.07 * speed, rx=math.sin(t * 0.06) * 0.015)
    d("r_arm", rx=-c * 0.4 * speed)
    d("l_arm", rx=c * 0.4 * speed)
    d("head", rx=pitch, ry=yaw - c * 0.07 * speed)
    beat = max(0.0, math.sin(t * 0.2)) ** 4
    d("core", s=0.0)
    p["core"]["s"] = 1 + 0.12 * beat
    if attack > 0:
        # pull back, drive the fist forward, recover
        if attack < 0.35:
            w, k = smooth(attack / 0.35), 0.0
        elif attack < 0.6:
            w, k = 1.0, smooth((attack - 0.35) / 0.25)
        else:
            w, k = 1.0 - smooth((attack - 0.6) / 0.4), 1.0
        q = {key: PUNCH_BACK[key] + (PUNCH_HIT[key] - PUNCH_BACK[key]) * k for key in PUNCH_KEYS}
        d("r_arm", rx=q["ra"] * w)
        d("r_forearm", rx=q["rf"] * w)
        d("torso", ry=q["ty"] * w, rx=q["tx"] * w)
        d("l_arm", rx=q["la"] * w)
        d("l_forearm", rx=q["lf"] * w)
    return p


# ------------------------------------------------------------------------------------------------ spawn egg

def write_egg():
    import generate_combat as gc  # noqa: E402  (vanilla's egg shape and the mask renderer)
    gi = gc.gi
    shell = ["1e1e24", "2c2c34", "3c3c46", "4e4e5a", "626270"]
    spots = ["0a5a6a", "128a9a", "2cc4cc", "6ae8e8", "c8ffff"]
    img = gi.render(gc.EGG, {"B": gi.Material(shell), "S": gi.Material(spots, base=3)})
    name = f"{NAME}_spawn_egg"
    out = ASSETS / "textures" / "item" / f"{name}.png"
    img.save(out)
    gc.write_json(ASSETS / "models" / "item" / f"{name}.json",
                  {"parent": "minecraft:item/generated", "textures": {"layer0": f"skyrpg_combat:item/{name}"}})
    gc.write_json(ASSETS / "items" / f"{name}.json", {"model": {"type": "minecraft:model", "model": f"skyrpg_combat:item/{name}"}})


# ------------------------------------------------------------------------------------------------ main

def previews(model, cv, out_dir, render_scale=1.0, hitbox=(1.0, 2.1), tag=NAME, pose_fn=pose, extra=(), ppx=10, units=(44, 50)):
    out_dir = Path(out_dir)
    out_dir.mkdir(parents=True, exist_ok=True)
    tex = cv.img
    glows = [(cv.layers["glow"], 1.0), (cv.layers["pulse"], 0.9)]
    uw, uh = units
    kw = dict(scale=render_scale, box=hitbox, ppx=ppx, size=(int(ppx * uw), int(ppx * uh)), feet=(ppx * uw / 2, ppx * uh - ppx * 4))
    views = [
        (dict(pose=pose_fn(), yaw=-0.6, pitch=0.25), "rest 3/4"),
        (dict(pose=pose_fn(), yaw=0.0, pitch=0.0), "front"),
        (dict(pose=pose_fn(), yaw=-1.5708, pitch=0.0), "side (right)"),
        (dict(pose=pose_fn(), yaw=2.6, pitch=0.25), "back 3/4"),
        (dict(pose=pose_fn(), yaw=-0.6, pitch=0.25, light=0.28), "in a dark cave"),
    ] + list(extra)
    imgs, labels = [], []
    for v, label in views:
        args = dict(kw)
        args.update(v)
        imgs.append(cm.render_view(model, tex, glows, **args))
        labels.append(label)
    cm.sheet(imgs, labels, out_dir / f"{tag}_views.png", cols=4)
    # a closer look at the face and chest
    face = cm.render_view(model, tex, glows, pose=pose_fn(), yaw=-0.35, pitch=0.15, ppx=16 * 50 / uh, size=(640, 720),
                          feet=(320, 690), scale=render_scale)
    face.save(out_dir / f"{tag}_closeup.png")
    tex.resize((tex.width * 4, tex.height * 4), 0).save(out_dir / f"{tag}_texture_x4.png")


def main():
    rows = M.pack(max_h=256)
    print(f"packed {len(M.cubes)} cubes, {len(M.owners())} unique UV regions, {rows} rows used, texture {M.width}x{M.height}")
    pal = Pal()
    cv = paint(M, pal, DECOS)
    TEX_DIR.mkdir(parents=True, exist_ok=True)
    cv.img.save(TEX_DIR / f"{NAME}.png")
    cv.layers["glow"].save(TEX_DIR / f"{NAME}_glow.png")
    cv.layers["pulse"].save(TEX_DIR / f"{NAME}_pulse.png")
    extra = (cm.java_floats("PUNCH_BACK", [PUNCH_BACK[k] for k in PUNCH_KEYS],
                            "The punch's wind-up, added to the rest pose: right arm, right forearm, torso yaw, torso lean, "
                            "left arm, left forearm.")
             + cm.java_floats("PUNCH_HIT", [PUNCH_HIT[k] for k in PUNCH_KEYS], "The punch landing (same order)."))
    JAVA_OUT.write_text(M.java("com.skyrpg.combat.client.mob", "DwarvenConstructLayout", "generate_dwarven_construct.py",
                               "The Dwarven Construct's cubes and their UVs.", extra), encoding="utf-8", newline="\n")
    write_egg()
    print("wrote textures,", JAVA_OUT.name, "and the spawn egg")
    if "--preview" in sys.argv:
        out = Path(sys.argv[sys.argv.index("--preview") + 1])
        walk = [(dict(pose=pose(walk_pos=math.pi / 2 / 0.5 * f, speed=1.0, t=0), yaw=-1.2, pitch=0.1), f"walk {f}")
                for f in (0.0, 0.5, 1.0)]
        punch = [(dict(pose=pose(attack=a), yaw=-0.9, pitch=0.15), f"punch {a}") for a in (0.3, 0.55)]
        previews(M, cv, out, extra=walk + punch)
        # the Forgeguard: the same model at scale 1.7, beside a player-sized box
        big = cm.render_view(M, cv.img, [(cv.layers["glow"], 1.0), (cv.layers["pulse"], 0.9)], pose=pose(), yaw=-0.6,
                             pitch=0.2, ppx=5, size=(520, 560), scale=1.7, box=(1.7, 2.1 * 1.7))
        small = cm.render_view(M, cv.img, [(cv.layers["glow"], 1.0), (cv.layers["pulse"], 0.9)], pose=pose(), yaw=-0.6,
                               pitch=0.2, ppx=5, size=(520, 560), box=(0.6, 1.8))
        cm.sheet([big, small], ["Forgeguard (scale 1.7)", "construct, player-size box"], out / f"{NAME}_forgeguard.png")
        print("previews in", out)


if __name__ == "__main__":
    main()
