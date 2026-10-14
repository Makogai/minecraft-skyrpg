#!/usr/bin/env python3
"""Draws the Magma Tyrant's model textures (the Infernal Citadel's boss, docs/design/nether.md) and writes its model
definition.

    python tools/art/generate_magma_tyrant.py [--preview DIR]

A huge molten-rock titan (about 2.0 x 3.6 blocks before any scale): cracked basalt and blackstone plates over a molten
core that shows between the chest plates and through glowing seams, boulder shoulders with basalt horns, smoking vent
columns on its back, giant fists with lava dripping from them, a low brutish head with burning eyes and a molten maw,
and a crown of flame. Built like the Iron Thane (see cube_models.py for the table, packing and preview). Outputs:

    combat/src/main/resources/assets/skyrpg_combat/textures/entity/mob/magma_tyrant.png
    ... magma_tyrant_glow.png    (steady glow: the eyes)
    ... magma_tyrant_pulse.png   (the core, the cracks, the crown's flames and the lava; drawn fullbright with a pulsing
                                  brightness)
    combat/src/client/java/com/skyrpg/combat/client/mob/MagmaTyrantLayout.java
"""
import math
import random
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import cube_models as cm  # noqa: E402
import generate_dwarven_construct as dc  # noqa: E402
from cube_models import _h, hexc, mix, mul, pick, ramp, vnoise  # noqa: E402

ROOT = Path(__file__).resolve().parents[2]
TEX_DIR = ROOT / "combat/src/main/resources/assets/skyrpg_combat/textures/entity/mob"
JAVA_OUT = ROOT / "combat/src/client/java/com/skyrpg/combat/client/mob/MagmaTyrantLayout.java"
NAME = "magma_tyrant"
HITBOX = (2.0, 3.6)  # keep in step with NetherMobs.MAGMA_TYRANT

# ------------------------------------------------------------------------------------------------ cube table
# Feet at y=24. The hips (y 8) carry the torso, leaning a little forward; the head sits low between the boulder
# shoulders; the crown's flames reach y -34.
M = cm.Model(256)
LEAN = 0.12

M.part("hips", None, (0, 8, 0))
M.cube("hips", "pelvis", "blackstone", (-10, -4, -6.5), (20, 7, 13), deco="pelvis")
M.cube("hips", "loin", "basalt", (-5, 2, -7.6), (10, 7, 2), deco="loin")
M.cube("hips", "loin_back", "basalt", (-6, 2, 5.6), (12, 6, 2), deco="loin")

M.part("torso", "hips", (0, -3, 0.5), (LEAN, 0, 0))
M.cube("torso", "waist", "magma", (-9, -8, -6), (18, 8, 12), deco="seams")
M.pair("torso", "ab", "basalt", (-8, -7.5, -7.2), (6, 6, 2), deco="ab")
M.cube("torso", "chest", "blackstone", (-12, -22, -7), (24, 15, 14), deco="chest")
M.pair("torso", "pec", "basalt", (-12.5, -21.5, -9.5), (10, 10, 3), deco="pec")
M.cube("torso", "collar", "basalt", (-8, -24.5, -5), (16, 3, 10), deco="collar")
M.cube("torso", "back_plate", "blackstone", (-10, -21, 6.5), (20, 13, 2), deco="back")
M.part("core", "torso", (0, -15.5, -8.5))
M.cube("core", "core", "molten", (-3, -4.5, -1.5), (6, 9, 3), glow="pulse")

# smoking vent columns on the back, leaning out, their tips glowing
M.sided("vent", "torso", (-5.5, -19, 7.5), (-0.45, 0, 0.3), [
    ("vent", "basalt", (-2, -10, -2), (4, 10, 4), dict(deco="column")),
    ("vent_cap", "basalt", (-1.5, -12, -1.5), (3, 2, 3), dict(deco="column")),
    ("vent_tip", "magma", (-1, -12.5, -1), (2, 1, 2), dict(glow="pulse")),
])
M.sided("vent2", "torso", (-8.5, -12, 8), (-0.6, 0, 0.6), [
    ("vent2", "basalt", (-1, -6, -1), (2, 6, 2), dict(deco="column")),
])
M.part("vent_c", "torso", (0, -16, 8))
M.cube("vent_c", "vent_c", "basalt", (-2, -12, -2), (4, 12, 4), deco="column")
M.cube("vent_c", "vent_c_cap", "basalt", (-1.5, -14, -1.5), (3, 2, 3), deco="column")
M.cube("vent_c", "vent_c_tip", "magma", (-1, -14.5, -1), (2, 1, 2), glow="pulse")
set_vent_c = (-0.5, 0, 0)

M.part("head", "torso", (0, -22.5, -4.5))
M.cube("head", "skull", "blackstone", (-5.5, -8, -6.5), (11, 8, 10), deco="face")
M.cube("head", "brow", "basalt", (-6, -6, -7.5), (12, 2, 2), deco="brow")
M.pair("head", "cheek", "basalt", (-6.5, -4, -5.5), (2, 3, 6), deco="cheek")
M.part("jaw", "head", (0, -1, -2), (0.08, 0, 0))
M.cube("jaw", "jaw", "basalt", (-6, 0, -6), (12, 4, 8), deco="jaw")
M.pair("jaw", "fang", "basalt", (-5, -1.5, -6.3), (1, 2, 1), deco="fang")

# the crown: a molten circlet ringed with flames (each its own part, so they flicker apart)
M.part("crown", "head", (0, -8, -1))
M.cube("crown", "circlet", "blackstone", (-6, -2, -6), (12, 2, 11), deco="circlet")
M.cube("crown", "fire_bed", "flame", (-4, -3, -4), (8, 2, 7), deco="flame_bed")
# each flame: (x, z, size class); a class stacks a wide base, a narrower middle and a thin tip: ((w, h, d), base height)
FLAME_CLASSES = {
    "big": [((3, 4, 3), 0.0), ((2, 3, 2), 4.0), ((1, 2, 1), 7.0)],
    "mid": [((2, 3, 2), 0.0), ((1, 3, 1), 3.0)],
    "small": [((2, 2, 2), 0.0), ((1, 2, 1), 2.0)],
}
FLAMES = [(0, -4.4, "big"), (-3.4, -4.2, "mid"), (3.4, -4.2, "mid"), (-4.7, -0.8, "mid"), (4.7, -0.8, "mid"),
          (-3.4, 3.2, "small"), (3.4, 3.2, "small"), (0, 3.4, "mid"), (-1.2, -0.6, "big"), (1.8, 0.8, "mid")]
_owners = {}
for i, (x, z, cls) in enumerate(FLAMES):
    M.part(f"flame{i}", "crown", (x, -2, z))
    for j, ((w, h, d), y0) in enumerate(FLAME_CLASSES[cls]):
        tier = ("flame_base", "flame_mid", "flame_tip")[j if cls == "big" or j == 0 else 2]
        key_ = (w, h, d, tier)
        M.cube(f"flame{i}", f"flame{i}_{j}", "flame", (-w / 2, -y0 - h, -d / 2), (w, h, d), deco=tier,
               share=_owners.get(key_))
        _owners.setdefault(key_, f"flame{i}_{j}")

M.sided("arm", "torso", (-15.5, -18.5, -0.5), (0.0, 0, 0.18), [
    ("boulder", "blackstone", (-6, -6.5, -6.5), (11, 9, 13), dict(deco="boulder")),
    ("boulder_top", "basalt", (-5, -8, -5), (9, 2, 10), dict(deco="dome")),
    ("upper", "basalt", (-4, 2, -4), (8, 10, 8), dict(deco="rockarm")),
])
M.sided("horn", "{s}_arm", (-2, -7.5, 0), (0.1, 0, -0.45), [
    ("horn", "basalt", (-1.5, -6, -1.5), (3, 6, 3), dict(deco="column")),
    ("horn_tip", "magma", (-1, -7, -1), (2, 1, 2), dict(glow="pulse")),
])
M.sided("forearm", "{s}_arm", (-0.5, 12, 0), (-0.25, 0, 0), [
    ("elbow", "blackstone", (-4.5, -1.5, -4.5), (9, 3, 9), dict(deco="ring")),
    ("fore", "magma", (-5, 1, -5), (10, 9, 10), dict(deco="seams")),
    ("fist", "blackstone", (-6, 9.5, -6.5), (12, 9, 12), dict(deco="fist")),
    ("knuckle", "basalt", (-6.5, 10, -7.5), (13, 4, 2), dict(deco="knuckle")),
])
# lava strands hanging from the fist (the model stretches them)
M.sided("drip", "{s}_forearm", (0, 18.5, 0), (0, 0, 0), [
    ("drip_a", "lava", (-4.5, 0, -3.5), (1, 3, 1), dict(glow="pulse")),
    ("drip_b", "lava", (1.5, 0, -1), (2, 2, 1), dict(glow="pulse")),
    ("drip_c", "lava", (-1.5, 0, 2.5), (1, 4, 1), dict(glow="pulse")),
])

M.sided("leg", None, (-6.5, 8, 0.5), (0, 0, 0), [
    ("thigh", "basalt", (-4.5, -1, -4.5), (9, 8, 9), dict(deco="column")),
    ("kneecap", "blackstone", (-3.5, 4.5, -5.6), (7, 3, 2), dict(deco="ring")),
    ("shin", "magma", (-5, 7, -5), (10, 6, 10), dict(deco="seams")),
    ("foot", "blackstone", (-5.5, 13, -6.5), (11, 3, 12), dict(deco="foot")),
    ("toes", "basalt", (-5, 14, -8.5), (10, 2, 2), dict(deco="toes")),
])


def set_rot(name, rot):
    next(p for p in M.parts if p["name"] == name)["rot"] = rot


set_rot("vent_c", set_vent_c)


# ------------------------------------------------------------------------------------------------ palette

class Pal(dc.Pal):
    def __init__(self):
        super().__init__()
        self.basalt = ramp("1e1e23", "28282e", "323239", "3d3d45", "4a4a53", "58585f", "6a6a74")
        self.blackstone = ramp("151217", "1c181f", "241f28", "2d2732", "372f3d", "433a4a", "52475a")
        self.crust = ramp("1a0805", "2c0e07", "42150a", "5a1d0c", "74260e")
        self.molten = ramp("7a1e04", "b03c08", "e06814", "ff9a2c", "ffc858", "fff0a8")
        self.molten_glow = (255, 120, 24)
        self.hot = (255, 210, 120)
        self.flame = ramp("ff3c08", "ff6410", "ff8c1c", "ffb434", "ffd860", "fff4b0")
        self.eye = (255, 236, 160)


SIDES = ("left", "front", "right", "back")


def base_pixel(mat, pal, X, Y, px, py, fw, fh, face, seed):
    n1 = vnoise(X, Y, 3.0, seed)
    n3 = _h(X, Y, seed + 3)
    if mat == "basalt":
        # columnar basalt: vertical columns 3 wide with dark joints, each its own tone; the tops are hexagon-ish cells
        if face in SIDES:
            col_i = (px + int(_h(0, py // 5, seed) * 3)) // 3
            tone = _h(col_i, fw, seed + 17)
            t = 0.25 + 0.45 * tone + 0.2 * (n1 - 0.5) + 0.08 * (n3 - 0.5)
            t += 0.1 * (1 - py / max(1, fh - 1))
            col = pick(pal.basalt, t)
            if (px + int(_h(0, py // 5, seed) * 3)) % 3 == 2:
                col = mul(col, 0.62)
            elif py % 5 == 0 and n3 > 0.5:
                col = mul(col, 0.8)
        else:
            cell = vnoise(X, Y, 2.0, seed + 5)
            col = pick(pal.basalt, 0.3 + 0.5 * cell)
            if abs(cell - 0.5) < 0.05:
                col = mul(col, 0.6)
        return col
    if mat == "blackstone":
        t = 0.3 + 0.45 * n1 + 0.15 * (n3 - 0.5)
        if face in SIDES:
            t += 0.12 * (1 - py / max(1, fh - 1)) - 0.04
            if py % 4 == 3 and _h(X // 3, Y, seed) > 0.4:
                t -= 0.18  # bedding layers
        col = pick(pal.blackstone, t)
        if n3 > 0.95:
            col = mix(col, (90, 74, 96), 0.5)  # glints
        return col
    if mat == "magma":
        # magma-block crust: dark cells; the bright seams between them are painted by the seams deco
        cell = vnoise(X, Y, 2.2, seed + 7)
        return pick(pal.crust, 0.2 + 0.7 * cell + 0.1 * (n3 - 0.5))
    if mat in ("molten", "lava"):
        return mul(pick(pal.molten, 0.2 + 0.5 * vnoise(X, Y, 1.6, seed)), 0.55)
    if mat == "flame":
        k = py / max(1, fh - 1) if face in SIDES else 0.9
        return pick(pal.flame, 0.15 + 0.8 * k + 0.1 * (n3 - 0.5))
    return dc.base_pixel(mat, pal, X, Y, px, py, fw, fh, face, seed)


def paint(model, pal, decos):
    cv = cm.Canvas(model.width, model.height, layers=("glow", "pulse"))
    for c in model.owners():
        rects = cm.face_rects(c)
        s = 1 + sum(ord(ch) for ch in c["id"])
        for face, (fx, fy, fw, fh) in rects.items():
            for py in range(fh):
                for px in range(fw):
                    col = base_pixel(c["mat"], pal, fx + px, fy + py, px, py, fw, fh, face, s)
                    k = dc.FACE_LIGHT[face] * dc.bevel(face, px, py, fw, fh) if c["mat"] not in ("flame", "molten", "lava") else 1.0
                    cv.put(fx + px, fy + py, mul(col, k))
        if c.get("deco"):
            decos[c["deco"]](cv, c, rects, pal, s)
        if c.get("glow"):
            glow_all(cv, c, rects, pal, s, c["glow"])
    return cv


def glow_all(cv, c, rects, pal, seed, layer):
    """A cube that glows all over: the molten core and lava (white-hot middles), or a flame (yellow-white at the root,
    orange-red at the tip)."""
    for face, (fx, fy, fw, fh) in rects.items():
        for py in range(fh):
            for px in range(fw):
                X, Y = fx + px, fy + py
                n = vnoise(X, Y, 1.5, seed + 2)
                if c["mat"] == "flame":
                    k = py / max(1, fh - 1) if face in SIDES else (1.0 if face == "bottom" else 0.75)
                    col = mix((255, 70, 10), (255, 236, 150), k * 0.85 + 0.15 * n)
                    cv.glow(X, Y, mul(col, 0.75 + 0.25 * k))  # flames burn steadily (they flicker by scale)
                    continue
                cx, cy = (fw - 1) / 2, (fh - 1) / 2
                d = max(abs(px - cx) / max(1, fw / 2), abs(py - cy) / max(1, fh / 2))
                col = mix(pal.molten_glow, pal.hot, max(0.0, 1 - d) * 0.9 + 0.25 * (n - 0.5))
                if c["mat"] == "molten" and face == "front" and vnoise(X, Y, 1.2, seed + 9) > 0.72 and d > 0.4:
                    cv.put(X, Y, (40, 12, 6))  # floating crust
                    col = mul(col, 0.35)
                cv.glow(X, Y, mul(col, 0.65 + 0.35 * n), layer)


# ------------------------------------------------------------------------------------------------ decorations

def shade(cv, x, y, k):
    cv.put(x, y, mul(cv.get(x, y), k))


def hot(cv, x, y, pal, k=1.0, layer="pulse"):
    """A glowing crack pixel: dark molten red in the texture, its light in the pulse layer (the two add up in game)."""
    k = max(0.0, min(1.0, k))
    cv.put(x, y, mix((70, 16, 6), (150, 46, 10), k))
    cv.glow(x, y, mul(mix((210, 60, 8), (255, 150, 40), max(0.0, k - 0.5) * 2), 0.45 + 0.55 * k), layer)


def warm(cv, x, y, pal, k=0.35):
    """Rock next to a crack, lit by it."""
    cv.put(x, y, mix(cv.get(x, y), (120, 36, 10), k))
    cv.glow(x, y, mul((200, 56, 8), k * 0.45), "pulse")


def cracks(cv, rect, pal, seed, n=2, length=None, strength=0.95, horizontal=False):
    """Meandering cracks across a face, glowing, with short branches; the rock either side warms."""
    fx, fy, fw, fh = rect
    rng = random.Random(seed)
    for _ in range(n):
        if horizontal:
            x, y = 0, rng.randrange(fh)
        else:
            x, y = rng.randrange(fw), 0 if rng.random() < 0.7 else rng.randrange(max(1, fh // 2))
        steps = length or int((fh if not horizontal else fw) * 0.8) + 2
        drift = rng.choice((-1, 1))
        for i in range(steps):
            if not (0 <= x < fw and 0 <= y < fh):
                break
            hot(cv, fx + x, fy + y, pal, strength * (1 - 0.45 * i / steps))
            for dx, dy in (((1, 0), (-1, 0)) if not horizontal else ((0, 1), (0, -1))):
                if 0 <= x + dx < fw and 0 <= y + dy < fh:
                    warm(cv, fx + x + dx, fy + y + dy, pal, 0.3)
            r = rng.random()
            step = drift if r < 0.35 else (-drift if r < 0.45 else 0)
            if horizontal:
                x += 1
                y += step
            else:
                y += 1
                x += step
            if rng.random() < 0.18:  # a short branch
                bx, by = x, y
                bd = rng.choice((-1, 1))
                for _j in range(rng.randrange(2, 4)):
                    bx += bd if not horizontal else 1
                    by += 1 if not horizontal else bd
                    if 0 <= bx < fw and 0 <= by < fh:
                        hot(cv, fx + bx, fy + by, pal, strength * 0.55)


def bottom_glow(cv, rect, pal, k=0.5, rows=2):
    """The lower edge of a plate lit from the molten body beneath it."""
    fx, fy, fw, fh = rect
    for r in range(rows):
        for px in range(fw):
            warm(cv, fx + px, fy + fh - 1 - r, pal, k * (1 - r / rows))


def deco_seams(cv, c, rects, pal, seed):
    # magma: the bright seams between the crust cells
    for face, (fx, fy, fw, fh) in rects.items():
        for py in range(fh):
            for px in range(fw):
                X, Y = fx + px, fy + py
                cell = vnoise(X, Y, 2.2, seed + 7)
                edge = abs(vnoise(X, Y, 3.4, seed + 31) - 0.5)
                if edge < 0.045:
                    hot(cv, X, Y, pal, 0.9 - edge * 4)
                elif edge < 0.09:
                    warm(cv, X, Y, pal, 0.35)
                elif cell > 0.75:
                    cv.put(X, Y, mul(cv.get(X, Y), 1.15))


def deco_pelvis(cv, c, rects, pal, seed):
    for face in SIDES:
        cracks(cv, rects[face], pal, seed + len(face), n=1, length=6)
        bottom_glow(cv, rects[face], pal, 0.45)


def deco_loin(cv, c, rects, pal, seed):
    fx, fy, fw, fh = rects["front"]
    cracks(cv, rects["front"], pal, seed, n=2)
    for px in range(fw):
        shade(cv, fx + px, fy, 1.2)


def deco_ab(cv, c, rects, pal, seed):
    for face in SIDES:
        bottom_glow(cv, rects[face], pal, 0.5)
    cracks(cv, rects["front"], pal, seed, n=1, length=5)


def deco_chest(cv, c, rects, pal, seed):
    for face in ("left", "right", "back", "front"):
        cracks(cv, rects[face], pal, seed + len(face), n=2)
    tx, ty, tw, th = rects["top"]
    cracks(cv, rects["top"], pal, seed + 9, n=2)


def deco_pec(cv, c, rects, pal, seed):
    # chest plates: a heavy rim, cracks running to the core, lit from the gap where the core shows
    fx, fy, fw, fh = rects["front"]
    for px in range(fw):
        shade(cv, fx + px, fy, 1.18)
    cracks(cv, rects["front"], pal, seed, n=1, horizontal=True, length=fw)
    for py in range(fh):
        warm(cv, fx + fw - 1, fy + py, pal, 0.55)
        warm(cv, fx + fw - 2, fy + py, pal, 0.25)
    bottom_glow(cv, rects["front"], pal, 0.5)
    for face in ("right",):  # the inner side, facing the core
        rx, ry, rw, rh = rects[face]
        for py in range(rh):
            for px in range(rw):
                warm(cv, rx + px, ry + py, pal, 0.7)


def deco_collar(cv, c, rects, pal, seed):
    for face in SIDES:
        bottom_glow(cv, rects[face], pal, 0.4, rows=1)
    cracks(cv, rects["top"], pal, seed, n=1, length=6)


def deco_back(cv, c, rects, pal, seed):
    bx, by, bw, bh = rects["back"]
    cracks(cv, rects["back"], pal, seed, n=3)


def deco_column(cv, c, rects, pal, seed):
    # a basalt column: warm glowing base, a hot fissure up one side
    for face in SIDES:
        fx, fy, fw, fh = rects[face]
        for px in range(fw):
            warm(cv, fx + px, fy + fh - 1, pal, 0.35)
    fx, fy, fw, fh = rects["front"]
    x = fw // 2
    for py in range(fh // 3, fh):
        if _h(x, py, seed) > 0.3:
            hot(cv, fx + x, fy + py, pal, 0.7)
    tx, ty, tw, th = rects["top"]
    for py in range(th):
        for px in range(tw):
            warm(cv, tx + px, ty + py, pal, 0.5)


def deco_face(cv, c, rects, pal, seed):
    """The head: deep sockets under the brow with burning eyes (rows 4-5), a ridge of a nose, glowing cracks down the
    cheeks."""
    fx, fy, fw, fh = rects["front"]
    for ex in (1, fw - 4):
        for px in range(ex, ex + 3):
            cv.put(fx + px, fy + 3, (12, 6, 4))
            cv.put(fx + px, fy + 4, (255, 220, 120))
            cv.put(fx + px, fy + 5, (150, 60, 14))
            cv.put(fx + px, fy + 6, (36, 12, 6))
            cv.glow(fx + px, fy + 4, pal.eye if px == ex + 1 else mul(pal.eye, 0.85))
            cv.glow(fx + px, fy + 5, mul(pal.molten_glow, 0.8))
            cv.glow(fx + px, fy + 6, mul(pal.molten_glow, 0.3))
            cv.glow(fx + px, fy + 3, mul(pal.molten_glow, 0.3))
    for py in range(3, fh):
        cv.put(fx + fw // 2, fy + py, mul(cv.get(fx + fw // 2, fy + py), 1.3))
    for py in range(fh):
        shade(cv, fx, fy + py, 0.8)
        shade(cv, fx + fw - 1, fy + py, 0.8)
    # a crack from the right eye down the cheek, and one over the brow on the left
    for px, py in ((2, 6), (2, 7), (1, 7)):
        hot(cv, fx + px, fy + py, pal, 0.7)
    for px, py in ((fw - 3, 0), (fw - 2, 1), (fw - 2, 2)):
        hot(cv, fx + px, fy + py, pal, 0.6)
    for face in ("left", "right", "back", "top"):
        cracks(cv, rects[face], pal, seed + len(face), n=1, length=6)


def deco_brow(cv, c, rects, pal, seed):
    fx, fy, fw, fh = rects["front"]
    for px in range(fw):
        shade(cv, fx + px, fy, 1.2)
    # a frown: the brow dips in the middle
    cv.put(fx + fw // 2, fy + fh - 1, mul(pal.basalt[1], 0.8))
    bx, by, bw, bh = rects["bottom"]
    for py in range(bh):
        for px in range(bw):
            cv.put(bx + px, by + py, (36, 14, 8))
            cv.glow(bx + px, by + py, mul(pal.molten_glow, 0.45 if 1 <= px <= 3 or bw - 4 <= px <= bw - 2 else 0.15))


def deco_cheek(cv, c, rects, pal, seed):
    for face in ("bottom",):
        fx, fy, fw, fh = rects[face]
        for py in range(fh):
            for px in range(fw):
                shade(cv, fx + px, fy + py, 0.7)


def deco_jaw(cv, c, rects, pal, seed):
    # the maw: a molten glow behind jagged basalt teeth along the top edge, the inside of the mouth white-hot
    fx, fy, fw, fh = rects["front"]
    for px in range(fw):
        tooth = px % 2 == 0 and 0 < px < fw - 1
        if not tooth:
            hot(cv, fx + px, fy, pal, 1.0)
        else:
            cv.put(fx + px, fy, pick(pal.basalt, 0.8))
        if _h(px, 1, seed) > 0.55:
            warm(cv, fx + px, fy + 1, pal, 0.5)
    for px in range(fw):
        shade(cv, fx + px, fy + fh - 1, 0.8)
    tx, ty, tw, th = rects["top"]
    for py in range(th):
        for px in range(tw):
            if 0 < px < tw - 1 and py > 0:
                hot(cv, tx + px, ty + py, pal, 0.85 + 0.15 * (py / th))
    for face in ("left", "right"):
        cracks(cv, rects[face], pal, seed + len(face), n=1, length=4)


def deco_fang(cv, c, rects, pal, seed):
    for face in SIDES:
        fx, fy, fw, fh = rects[face]
        cv.put(fx, fy, pick(pal.basalt, 0.95))


def deco_circlet(cv, c, rects, pal, seed):
    # a blackstone circlet with a molten inlay band and glowing studs
    for face in SIDES:
        fx, fy, fw, fh = rects[face]
        for px in range(fw):
            hot(cv, fx + px, fy + fh - 1, pal, 0.75 if px % 3 else 1.0)
            if px % 3 == 1:
                shade(cv, fx + px, fy, 1.3)
    tx, ty, tw, th = rects["top"]
    for py in range(th):
        for px in range(tw):
            hot(cv, tx + px, ty + py, pal, 0.9)


def deco_boulder(cv, c, rects, pal, seed):
    for face in SIDES:
        cracks(cv, rects[face], pal, seed + len(face), n=1)
        bottom_glow(cv, rects[face], pal, 0.55)
    cracks(cv, rects["top"], pal, seed + 3, n=1)


def deco_dome(cv, c, rects, pal, seed):
    for face in SIDES:
        fx, fy, fw, fh = rects[face]
        for px in range(fw):
            shade(cv, fx + px, fy, 1.15)


def deco_rockarm(cv, c, rects, pal, seed):
    for face in SIDES:
        cracks(cv, rects[face], pal, seed + len(face), n=1)


def deco_ring(cv, c, rects, pal, seed):
    for face in SIDES:
        fx, fy, fw, fh = rects[face]
        for px in range(fw):
            shade(cv, fx + px, fy, 1.15)
        bottom_glow(cv, rects[face], pal, 0.5, rows=1)


def deco_fist(cv, c, rects, pal, seed):
    # a huge rock fist: four fingers curled under (dark seams), a crack across the back of the hand, the underside and
    # the finger tips glowing where the lava drips from
    fx, fy, fw, fh = rects["front"]
    for gx in (3, 6, 9):
        for py in range(3, fh):
            cv.put(fx + gx, fy + py, mul(cv.get(fx + gx, fy + py), 0.55))
    for px in range(fw):
        warm(cv, fx + px, fy + fh - 1, pal, 0.6)
        if px % 3 != 0:
            warm(cv, fx + px, fy + fh - 2, pal, 0.3)
    for face in ("left", "right", "back"):
        cracks(cv, rects[face], pal, seed + len(face), n=1)
        bottom_glow(cv, rects[face], pal, 0.55, rows=2)
    cracks(cv, rects["top"], pal, seed + 5, n=1)
    bx, by, bw, bh = rects["bottom"]
    for py in range(bh):
        for px in range(bw):
            if vnoise(bx + px, by + py, 2.0, seed) > 0.45:
                hot(cv, bx + px, by + py, pal, 0.85)
            else:
                warm(cv, bx + px, by + py, pal, 0.55)


def deco_knuckle(cv, c, rects, pal, seed):
    # the knuckle ridge: four knuckles, lit from the cracks between them
    for face in ("front", "top"):
        fx, fy, fw, fh = rects[face]
        for px in range(fw):
            k = px % 3
            for py in range(fh):
                if k == 0:
                    warm(cv, fx + px, fy + py, pal, 0.5)
                elif py == 0:
                    shade(cv, fx + px, fy + py, 1.25)
    fx, fy, fw, fh = rects["bottom"]
    for py in range(fh):
        for px in range(fw):
            warm(cv, fx + px, fy + py, pal, 0.5)


def deco_foot(cv, c, rects, pal, seed):
    for face in SIDES:
        bottom_glow(cv, rects[face], pal, 0.6, rows=1)
    cracks(cv, rects["top"], pal, seed, n=2)


def deco_toes(cv, c, rects, pal, seed):
    for face in ("front", "top"):
        fx, fy, fw, fh = rects[face]
        for px in range(fw):
            if px % 3 == 2:
                for py in range(fh):
                    hot(cv, fx + px, fy + py, pal, 0.7)


def flame_tier(cv, c, rects, pal, seed, lo, hi):
    """A flame cube: colours run from `lo` at its root to `hi` at its top; fullbright in the steady glow layer."""
    for face, (fx, fy, fw, fh) in rects.items():
        for py in range(fh):
            for px in range(fw):
                k = 1 - py / max(1, fh - 1) if face in SIDES else (1.0 if face == "top" else 0.0)
                n = vnoise(fx + px, fy + py, 1.4, seed)
                col = mix(lo, hi, k * 0.8 + 0.2 * n)
                cv.put(fx + px, fy + py, mul(col, 0.6))
                cv.glow(fx + px, fy + py, mul(col, 0.85))


def deco_flame_base(cv, c, rects, pal, seed):
    flame_tier(cv, c, rects, pal, seed, (255, 236, 150), (255, 170, 50))


def deco_flame_mid(cv, c, rects, pal, seed):
    flame_tier(cv, c, rects, pal, seed, (255, 170, 50), (255, 100, 18))


def deco_flame_tip(cv, c, rects, pal, seed):
    flame_tier(cv, c, rects, pal, seed, (255, 100, 18), (220, 50, 10))


def deco_flame_bed(cv, c, rects, pal, seed):
    flame_tier(cv, c, rects, pal, seed, (255, 200, 90), (255, 130, 30))


DECOS = {"flame_base": deco_flame_base, "flame_mid": deco_flame_mid, "flame_tip": deco_flame_tip,
         "flame_bed": deco_flame_bed, "seams": deco_seams, "pelvis": deco_pelvis, "loin": deco_loin, "ab": deco_ab, "chest": deco_chest,
         "pec": deco_pec, "collar": deco_collar, "back": deco_back, "column": deco_column, "face": deco_face,
         "brow": deco_brow, "cheek": deco_cheek, "jaw": deco_jaw, "fang": deco_fang, "circlet": deco_circlet,
         "boulder": deco_boulder, "dome": deco_dome, "rockarm": deco_rockarm, "ring": deco_ring, "fist": deco_fist,
         "knuckle": deco_knuckle, "foot": deco_foot, "toes": deco_toes}


# ------------------------------------------------------------------------------------------------ poses (as MagmaTyrantModel)

smooth = dc.smooth

# Key poses, deltas added to the rest pose: right arm x / z, right forearm, left arm x / z, left forearm, torso lean,
# head pitch, jaw, and how far (model pixels) the hips sink. MagmaTyrantModel blends to the same numbers.
KEYS = ("ra", "raz", "rf", "la", "laz", "lf", "lean", "head", "jaw", "drop")
ZERO = {k: 0.0 for k in KEYS}


def key(**kw):
    d = dict(ZERO)
    d.update(kw)
    return d


# The slam (MagmaTyrantEntity.SLAM_POSE_TICKS = 28): both fists raised overhead 0-12, smashed down 12-15 (the blow lands
# at 15), held to 18, recovered by 28
SLAM_RAISE = key(ra=-2.6, raz=0.12, rf=-0.45, la=-2.6, laz=-0.12, lf=-0.45, lean=-0.18, head=0.3, jaw=0.45, drop=-1.2)
SLAM_HIT = key(ra=-0.95, raz=-0.22, rf=-0.15, la=-0.95, laz=0.22, lf=-0.15, lean=0.55, head=-0.45, jaw=0.25, drop=3.5)
SLAM_TOTAL = 28.0


def slam_weights(e):
    """(target pose, weight) of the slam `e` ticks in (keep in step with MagmaTyrantModel)."""
    if e < 12:
        return SLAM_RAISE, smooth(e / 12)
    if e < 15:
        k = smooth((e - 12) / 3)
        return {q: SLAM_RAISE[q] + (SLAM_HIT[q] - SLAM_RAISE[q]) * k for q in KEYS}, 1.0
    if e < 18:
        return SLAM_HIT, 1.0
    return SLAM_HIT, 1.0 - smooth((e - 18) / 10)


PART_OF = dict(ra=("r_arm", "rx"), raz=("r_arm", "rz"), rf=("r_forearm", "rx"), la=("l_arm", "rx"),
               laz=("l_arm", "rz"), lf=("l_forearm", "rx"), lean=("torso", "rx"), head=("head", "rx"),
               jaw=("jaw", "rx"), drop=("hips", "y"))


def pulse(t):
    return max(0.0, math.sin(t * 0.12)) ** 3


def pose(walk_pos=0.0, speed=0.0, slam=None, t=0.0, yaw=0.0, pitch=0.0):
    """The deltas MagmaTyrantModel.setupAnim adds to the rest pose (keep the two in step)."""
    pos = walk_pos * 0.38
    c, s = math.cos(pos), math.sin(pos)
    lift = abs(s) ** 0.6
    breath = math.sin(t * 0.05)
    cur = dict(ZERO)
    # ---- the heavy walk: short strides, the body dropping onto each footfall, shoulders rolling, fists swinging
    cur["ra"] = -c * 0.32 * speed
    cur["la"] = c * 0.32 * speed
    cur["lean"] = 0.05 * speed + breath * 0.02
    cur["jaw"] = 0.03 + 0.03 * math.sin(t * 0.07)
    cur["drop"] = (1 - lift) * 1.5 * speed - 0.5 * speed
    roll = s * 0.07 * speed
    w = 0.0
    if slam is not None:
        target, w = slam_weights(slam)
        cur = {k: cur[k] + (target[k] - cur[k]) * w for k in KEYS}
        roll *= 1 - w
    p = {}
    for k, (part, axis) in PART_OF.items():
        p.setdefault(part, {})[axis] = cur[k]
    p["hips"]["rz"] = roll
    p["torso"]["ry"] = c * 0.08 * speed * (1 - w)
    p["r_leg"] = {"rx": c * 0.5 * speed}
    p["l_leg"] = {"rx": -c * 0.5 * speed}
    calm = (1 - speed) * (1 - w)
    p["head"]["rx"] += pitch - cur["lean"] * 0.5
    p["head"]["ry"] = yaw + math.sin(t * 0.02) * 0.12 * calm
    beat = pulse(t)
    p["core"] = {"s": 1 + 0.1 * beat}
    p["crown"] = {"rz": -roll * 0.8}
    for i in range(len(FLAMES)):
        p[f"flame{i}"] = {"sy": 0.8 + 0.25 * math.sin(t * 0.45 + i * 1.7) + 0.1 * math.sin(t * 1.1 + i * 2.9),
                          "sx": 1.0, "sz": 1.0}
    for side in ("r", "l"):
        p[f"{side}_drip"] = {"sy": 0.7 + 0.6 * ((t * 0.04 + (0.5 if side == "l" else 0.0)) % 1.0), "sx": 1.0, "sz": 1.0}
    return p


HERO_EXTRA = [(-1.0, 0.15, "slam raise", dict(slam=12)), (-1.2, 0.1, "slam impact", dict(slam=15))]


# ------------------------------------------------------------------------------------------------ main

def build(write=True):
    rows_used = M.pack(max_h=512)
    print(f"packed {len(M.cubes)} cubes, {len(M.owners())} unique UV regions, {rows_used} rows used, texture {M.width}x{M.height}")
    cm.check_zfight(M)
    cv = paint(M, Pal(), DECOS)
    if write:
        TEX_DIR.mkdir(parents=True, exist_ok=True)
        cv.img.save(TEX_DIR / f"{NAME}.png")
        cv.layers["glow"].save(TEX_DIR / f"{NAME}_glow.png")
        cv.layers["pulse"].save(TEX_DIR / f"{NAME}_pulse.png")
        order = ", ".join(KEYS)
        extra = (cm.java_floats("SLAM_RAISE", [SLAM_RAISE[k] for k in KEYS],
                                f"The slam's raised pose, deltas added to the rest pose: {order}.")
                 + cm.java_floats("SLAM_HIT", [SLAM_HIT[k] for k in KEYS], "The slam's impact pose (same order).")
                 + f"\n    /** How many flame parts the crown has (flame0 ... flame{len(FLAMES) - 1}). */\n"
                 + f"    static final int FLAMES = {len(FLAMES)};\n")
        JAVA_OUT.write_text(M.java("com.skyrpg.combat.client.mob", "MagmaTyrantLayout", "generate_magma_tyrant.py",
                                   "The Magma Tyrant's cubes and their UVs.", extra), encoding="utf-8", newline="\n")
        print("wrote textures and", JAVA_OUT.name)
    return cv


def main():
    cv = build(write="--no-write" not in sys.argv)
    if "--preview" in sys.argv:
        out = Path(sys.argv[sys.argv.index("--preview") + 1])
        walk = [(dict(pose=pose(walk_pos=math.pi / 2 / 0.38 * f, speed=1.0, t=10 * f), yaw=-1.2, pitch=0.1), f"walk {f}")
                for f in (0.0, 1.0)]
        slam = [(dict(pose=pose(slam=e), yaw=-1.25, pitch=0.12), f"slam tick {e}") for e in (8, 12, 13.5, 15)]
        dc.previews(M, cv, out, hitbox=HITBOX, tag=NAME, pose_fn=pose, extra=walk + slam, ppx=6, units=(96, 100))
        glows = [(cv.layers["glow"], 1.0), (cv.layers["pulse"], 0.9)]
        shots = [cm.render_view(M, cv.img, glows, pose=pose(t=t), yaw=y, pitch=0.15, ppx=11, size=(640, 700), feet=(320, 720))
                 for y, t in ((-0.45, 0), (2.6, 7))]
        dark = cm.render_view(M, cv.img, glows, pose=pose(), yaw=-0.5, pitch=0.12, ppx=11, size=(640, 700), feet=(320, 720),
                              light=0.25)
        cm.sheet(shots + [dark], ["face and core", "back vents", "in the citadel's dark"], out / f"{NAME}_closeup.png")
        print("previews in", out)


if __name__ == "__main__":
    main()
