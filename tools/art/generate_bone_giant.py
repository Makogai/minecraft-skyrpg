#!/usr/bin/env python3
"""Draws the Bone Giant's model textures (the Bone Sea's giant skeleton, docs/design/nether.md) and writes its model
definition.

    python tools/art/generate_bone_giant.py [--preview DIR]

A hunched giant skeleton (about 1.4 x 3.6 blocks): a cracked skull with an underbite jaw and soul-blue eyes, a hollow
ribcage with a soul fire burning between the ribs, a vertebral spine curving over into the neck, long knuckle-dragging
arms, and a femur club (the thigh bone of something even bigger, its knee end studded with obsidian shards) in its right
hand. Built like the Iron Thane (see cube_models.py for the table, packing and preview). Outputs:

    combat/src/main/resources/assets/skyrpg_combat/textures/entity/mob/bone_giant.png
    ... bone_giant_glow.png    (steady glow: eye sockets, the skull's crack, the spine's joints)
    ... bone_giant_pulse.png   (the soul fire in the ribcage; drawn fullbright with a pulsing brightness)
    combat/src/client/java/com/skyrpg/combat/client/mob/BoneGiantLayout.java
    the spawn egg's texture and item model
"""
import math
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import cube_models as cm  # noqa: E402
import generate_dwarven_construct as dc  # noqa: E402
from cube_models import _h, hexc, mix, mul, pick, ramp, vnoise  # noqa: E402

ROOT = Path(__file__).resolve().parents[2]
ASSETS = ROOT / "combat/src/main/resources/assets/skyrpg_combat"
TEX_DIR = ASSETS / "textures/entity/mob"
JAVA_OUT = ROOT / "combat/src/client/java/com/skyrpg/combat/client/mob/BoneGiantLayout.java"
NAME = "bone_giant"
HITBOX = (1.4, 3.5)  # keep in step with NetherMobs.BONE_GIANT

# ------------------------------------------------------------------------------------------------ cube table
# Feet at y=24. The hips (y 4.4) carry a torso leaning forward LEAN rad (the hunch); the neck and head lean back up so the
# face looks ahead. The ribs are hollow rings (back, side and front bars) around the soul fire. Legs hang from the root,
# a little bent at the knee.
M = cm.Model(128)
LEAN = 0.5
NECK = -0.3
HIPS = (0, 4.4, 3)

M.part("hips", None, HIPS)
M.cube("hips", "pelvis", "bone", (-5.5, -1, -3), (11, 3, 6), deco="pelvis")
M.pair("hips", "ilium", "bone", (-8, -6, -2.5), (4, 6, 5), deco="ilium")
M.cube("hips", "sacrum", "bone", (-2, -5, 0.5), (4, 5, 3), deco="vertebra")
M.cube("hips", "coccyx", "bone", (-1, -0.5, 2.5), (2, 3, 2))

M.part("torso", "hips", (0, -3.5, 1), (LEAN, 0, 0))
M.cube("torso", "spine", "bone", (-1.5, -24, -1.5), (3, 24, 3), deco="spine")
M.cube("torso", "ridge", "bone", (-0.5, -23, 1.5), (1, 20, 2), deco="ridge")
# (y, half width, depth) of each rib; the last is a floating rib that doesn't reach the sternum
RIBS = [(-22, 7, 9), (-18.5, 8, 10), (-15, 8, 10), (-11.5, 7, 9), (-8, 6, 7)]
for i, (y, w, d) in enumerate(RIBS):
    cubes = [(f"ribs{i}", "bone", (-w, 0, -d), (2, 2, d + 1), dict(deco="rib")),
             (f"ribb{i}", "bone", (-w + 2, 0, -1), (w - 2, 2, 2), dict(deco="rib"))]
    if i < len(RIBS) - 1:
        cubes.append((f"ribf{i}", "bone", (-w + 2, 0, -d), (w - 2, 2, 2), dict(deco="ribfront")))
    M.sided(f"rib{i}", "torso", (-1.5, y, -0.5), (0.2, 0, 0), cubes)
M.cube("torso", "sternum", "bone", (-1.5, -20.5, -11.3), (3, 12, 2), deco="sternum")
M.cube("torso", "clavicle", "bone", (-10.5, -24.5, -9), (21, 2, 3), deco="clavicle")
M.pair("torso", "scapula", "bone", (-9, -24, 0.5), (6, 9, 1), deco="scapula")
# bone spikes jutting back from the upper spine: the hump's silhouette
for i, (y, h, rot) in enumerate([(-21.5, 6, -1.0), (-17.5, 5, -1.05), (-13.5, 4, -1.1)]):
    M.part(f"spike{i}", "torso", (0, y, 1.2), (rot, 0, 0))
    M.cube(f"spike{i}", f"spike{i}", "bone", (-1, -h, -1), (2, h, 2), deco="spike")
M.part("soul", "torso", (0, -15, -5))
M.cube("soul", "soul", "soul", (-4.5, -6, -3), (9, 12, 6), glow="pulse")

M.part("neck", "torso", (0, -23.5, -1), (NECK, 0, 0))
M.cube("neck", "neck", "bone", (-2, -5, -2), (4, 5, 4), deco="vertebra")
M.part("head", "neck", (0, -4.5, -0.5), (-(LEAN + NECK), 0, 0))
M.cube("head", "cranium", "bone", (-5.5, -9.5, -9), (11, 9, 10), deco="skull")
M.cube("head", "brow", "bone", (-6, -6.5, -10), (12, 2, 2), deco="brow")
M.pair("head", "cheek", "bone", (-6, -3, -8), (2, 2, 4), deco="cheek")
M.cube("head", "maxilla", "bone", (-4.5, -0.5, -9.5), (9, 2, 5), deco="teeth_top")
M.part("jaw", "head", (0, 1, -3), (0.12, 0, 0))
M.cube("jaw", "mandible", "bone", (-5, 0, -7.5), (10, 3, 8), deco="teeth_bottom")
M.pair("jaw", "tusk", "bone", (-4.8, -1.5, -7.3), (1, 2, 1), deco="tusk")

M.sided("arm", "torso", (-11, -21.5, -5.5), (-0.72, 0, 0.1), [
    ("shoulder", "bone", (-3, -3, -3), (6, 6, 6), dict(deco="joint")),
    ("humerus", "bone", (-2, 3, -2), (4, 12, 4), dict(deco="longbone")),
])
M.sided("forearm", "{s}_arm", (0, 15.5, 0), (-0.35, 0, 0), [
    ("elbow", "bone", (-2.5, -1.5, -2.5), (5, 3, 5), dict(deco="joint")),
    ("radius", "bone", (-2.5, 1.5, -1.5), (2, 12, 3), dict(deco="longbone")),
    ("ulna", "bone", (0.5, 1.5, -1.5), (2, 12, 3), dict(deco="longbone")),
    ("wrist", "bone", (-3, 13, -2.5), (6, 2, 5), dict(deco="joint")),
])
M.sided("hand", "{s}_forearm", (0, 15, -0.5), (0, 0, 0), [
    ("palm", "bone", (-3, 0, -2.5), (6, 3, 5), dict(deco="palm")),
    ("claws", "bone", (-3.5, 2.5, -3.1), (7, 5, 1), dict(deco="claw")),
])

# the femur club, gripped in the right hand: the haft runs along the part's +y, the knee end (the striking end) at the
# bottom, the femoral head poking out above the fist. At rest it drags behind on the ground.
CLUB_REST = (1.6, -0.4, 0.6)  # x, y, z: trailing on the ground out to the right

M.part("club", "r_hand", (0, 1.5, -0.5), CLUB_REST)
M.cube("club", "club_knob", "bone", (-2.5, -8, -2.5), (5, 4, 5), deco="joint")
M.cube("club", "club_grip", "wrap", (-1.5, -4, -1.5), (3, 7, 3), deco="wrap")
M.cube("club", "club_shaft", "bone", (-2, 3, -2), (4, 15, 4), deco="femur")
M.cube("club", "club_head", "bone", (-3.5, 17, -3.5), (7, 6, 7), deco="condyle")
M.pair("club", "club_cond", "bone", (-3.5, 22.5, -2.5), (3, 2, 5), deco="joint")
M.cube("club", "shard0", "obsidian", (-4.5, 18, -1), (1, 3, 2), deco="shard")
M.cube("club", "shard1", "obsidian", (3.5, 19, -0.5), (1, 2, 2), deco="shard")
M.cube("club", "shard2", "obsidian", (-1, 18.5, -4.5), (2, 2, 1), deco="shard")
M.cube("club", "shard3", "obsidian", (0, 17.5, 3.5), (2, 3, 1), deco="shard")

M.sided("leg", None, (-4.5, HIPS[1], HIPS[2]), (-0.2, 0, 0), [
    ("hip_ball", "bone", (-2.5, -2.5, -2.5), (5, 5, 5), dict(deco="joint")),
    ("femur", "bone", (-2, 2, -2), (4, 8, 4), dict(deco="longbone")),
])
M.sided("shin", "{s}_leg", (0, 10, 0), (0.2, 0, 0), [
    ("knee", "bone", (-3, -1.5, -3), (6, 3, 6), dict(deco="joint")),
    ("patella", "bone", (-1.5, -1, -3.8), (3, 3, 1), {}),
    ("tibia", "bone", (-2, 1.5, -1.5), (3, 7, 3), dict(deco="longbone")),
    ("fibula", "bone", (1, 1.5, -0.5), (1, 7, 1), {}),
    ("foot", "bone", (-3, 8, -7), (6, 2, 9), dict(deco="foot")),
    ("toes", "bone", (-3.5, 9, -9), (7, 1, 2), dict(deco="toes")),
])


def set_rot(name, rot):
    next(p for p in M.parts if p["name"] == name)["rot"] = rot


# rest pose: the left arm hangs further forward than the club arm
set_rot("l_arm", (-0.85, 0, -0.14))
set_rot("l_forearm", (-0.5, 0, 0))


# ------------------------------------------------------------------------------------------------ palette

class Pal(dc.Pal):
    def __init__(self):
        super().__init__()
        self.bone = ramp("3a342a", "524a3c", "6c6350", "877d66", "a2977c", "bab094", "d0c7ab", "e4dcc2")
        self.grime = hexc("4a3c30")      # soul-sand stains
        self.char = hexc("221d18")       # scorched by the Nether
        self.soul = ramp("041224", "072038", "0b3050", "10486e", "186a92", "2690b6")
        self.soul_glow = (70, 205, 255)
        self.soul_hot = (200, 248, 255)
        self.eye = (190, 245, 255)
        self.wrap = ramp("1e1610", "2c2018", "3c2c20", "4e3a2a", "604834")
        self.obsidian = ramp("0a0712", "130c20", "1d1230", "2a1a46", "3c2662", "5a3c8a")


SIDES = ("left", "front", "right", "back")


def base_pixel(mat, pal, X, Y, px, py, fw, fh, face, seed):
    n1 = vnoise(X, Y, 3.0, seed)
    n3 = _h(X, Y, seed + 3)
    if mat == "bone":
        n0 = vnoise(X, Y, 6.0, seed)
        t = 0.64 + 0.26 * (n0 - 0.5) + 0.1 * (n1 - 0.5) + 0.05 * (n3 - 0.5)
        if face in SIDES:
            t += 0.1 * (1 - py / max(1, fh - 1)) - 0.05
        col = pick(pal.bone, t)
        if face in SIDES and _h(X, 0, seed + 9) > 0.85:
            col = mul(col, 0.95)  # grain along the bone
        if n3 > 0.96:
            col = mul(col, 0.78)  # pits
        g = vnoise(X + 11, Y + 3, 5.0, seed + 21)
        if g > 0.74:
            col = mix(col, pal.grime, min(0.35, (g - 0.74) * 2.0))
        ch = vnoise(X + 40, Y, 3.0, seed + 7)
        if ch > 0.86:
            col = mix(col, pal.char, 0.35)
        return col
    if mat == "soul":
        return pick(pal.soul, 0.35 + 0.55 * vnoise(X, Y, 1.6, seed) + 0.1 * (n3 - 0.5))
    if mat == "wrap":
        # rag wrapping: diagonal turns of cloth, dark grooves between them
        k = (px + py) % 4
        col = pick(pal.wrap, 0.25 + 0.15 * k + 0.2 * (n1 - 0.5))
        if k == 3:
            col = mul(col, 0.7)
        return col
    if mat == "obsidian":
        col = pick(pal.obsidian, 0.25 + 0.4 * n1 + 0.15 * (n3 - 0.5))
        if n3 > 0.9:
            col = pick(pal.obsidian, 0.95)
        return col
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
                    cv.put(fx + px, fy + py, mul(col, dc.FACE_LIGHT[face] * dc.bevel(face, px, py, fw, fh)))
        if c.get("deco"):
            decos[c["deco"]](cv, c, rects, pal, s)
        if c.get("glow"):
            glow_all(cv, c, rects, pal, s, c["glow"])
    return cv


def glow_all(cv, c, rects, pal, seed, layer):
    """The soul fire: flickering blue flame tongues, white-hot low in the middle."""
    for face, (fx, fy, fw, fh) in rects.items():
        for py in range(fh):
            for px in range(fw):
                X, Y = fx + px, fy + py
                n = vnoise(X, Y * 0.6, 1.8, seed + 2)
                up = py / max(1, fh - 1) if face in SIDES else 0.6
                k = 0.45 + 0.45 * n + 0.25 * up
                col = mix(pal.soul_glow, pal.soul_hot, max(0.0, (k - 0.85) * 3))
                cv.glow(X, Y, mul(col, min(1.0, k)), layer)


# ------------------------------------------------------------------------------------------------ decorations

def rows(cv, rect, ys, col):
    fx, fy, fw, fh = rect
    for r in ys:
        yy = r if r >= 0 else fh + r
        for px in range(fw):
            cv.put(fx + px, fy + yy, col)


def shade(cv, x, y, k):
    cv.put(x, y, mul(cv.get(x, y), k))


def soul_seam(cv, x, y, pal, k=0.5):
    """A thin gap glowing with soul light."""
    cv.put(x, y, (14, 26, 34))
    cv.glow(x, y, mul(pal.soul_glow, k))


def deco_pelvis(cv, c, rects, pal, seed):
    fx, fy, fw, fh = rects["front"]
    # the pubic arch: a dark notch in the middle of the bottom edge
    for px in range(fw // 2 - 2, fw // 2 + 2):
        cv.put(fx + px, fy + fh - 1, (26, 22, 18))
    for px in (fw // 2 - 1, fw // 2):
        cv.put(fx + px, fy + fh - 2, (40, 34, 28))
    # obturator holes either side
    for hx in (2, fw - 3):
        cv.put(fx + hx, fy + 1, (24, 20, 16))
        cv.put(fx + hx, fy + 2, (30, 26, 20))


def deco_ilium(cv, c, rects, pal, seed):
    # flared hip wings: a pale crest along the top, a hollow in the outer face
    for face in SIDES:
        rows(cv, rects[face], (0,), pal.bone[-1])
    lx, ly, lw, lh = rects["left"]
    for py in range(2, lh - 1):
        for px in range(1, lw - 1):
            shade(cv, lx + px, ly + py, 0.78)


def deco_vertebra(cv, c, rects, pal, seed):
    for face in SIDES:
        fx, fy, fw, fh = rects[face]
        for py in range(2, fh, 3):
            for px in range(fw):
                soul_seam(cv, fx + px, fy + py, pal, 0.35)


def deco_spine(cv, c, rects, pal, seed):
    # stacked vertebrae: a knobbly segment every 3 pixels, the discs between them leaking faint soul light
    for face in SIDES:
        fx, fy, fw, fh = rects[face]
        for py in range(fh):
            for px in range(fw):
                if py % 3 == 2:
                    soul_seam(cv, fx + px, fy + py, pal, 0.4)
                elif py % 3 == 0:
                    shade(cv, fx + px, fy + py, 1.12)


def deco_ridge(cv, c, rects, pal, seed):
    # the spinous processes: knobs pointing back, one per vertebra
    for face in ("back", "left", "right"):
        fx, fy, fw, fh = rects[face]
        for py in range(fh):
            for px in range(fw):
                if py % 3 == 2:
                    cv.put(fx + px, fy + py, mul(pal.bone[1], 0.8))
                elif py % 3 == 0:
                    shade(cv, fx + px, fy + py, 1.15)


def deco_rib(cv, c, rects, pal, seed):
    # a rib bar: bright along the top edge, shadowed below, its underside lit cold blue by the soul fire inside
    for face in SIDES:
        fx, fy, fw, fh = rects[face]
        for px in range(fw):
            shade(cv, fx + px, fy, 1.12)
            if fh > 1:
                shade(cv, fx + px, fy + fh - 1, 0.74)
    bx, by, bw, bh = rects["bottom"]
    for py in range(bh):
        for px in range(bw):
            cv.put(bx + px, by + py, mix(cv.get(bx + px, by + py), (70, 120, 150), 0.3))
    # a crack through one rib
    if c["id"] == "r_ribs2":
        fx, fy, fw, fh = rects["left"]
        cv.put(fx + fw // 2, fy, (30, 26, 22))
        cv.put(fx + fw // 2 + 1, fy + 1, (30, 26, 22))


def deco_ribfront(cv, c, rects, pal, seed):
    deco_rib(cv, c, rects, pal, seed)
    # the inner end, where it meets the sternum: yellowed cartilage
    fx, fy, fw, fh = rects["front"]
    for py in range(fh):
        for k in (1, 2):
            cv.put(fx + fw - k, fy + py, mix(cv.get(fx + fw - k, fy + py), (160, 138, 84), 0.5 if k == 1 else 0.25))


def deco_tusk(cv, c, rects, pal, seed):
    for face in SIDES:
        fx, fy, fw, fh = rects[face]
        cv.put(fx, fy, pal.bone[-1])
        cv.put(fx, fy + fh - 1, pal.bone[3])


def deco_sternum(cv, c, rects, pal, seed):
    fx, fy, fw, fh = rects["front"]
    for py in range(2, fh, 3):
        cv.put(fx, fy + py, (40, 34, 28))
        cv.put(fx + fw - 1, fy + py, (40, 34, 28))
    for py in range(fh):
        shade(cv, fx + 1, fy + py, 1.08)
    cv.put(fx + 1, fy + fh - 1, mul(pal.bone[2], 0.9))


def deco_clavicle(cv, c, rects, pal, seed):
    for face in SIDES:
        rows(cv, rects[face], (-1,), mul(pal.bone[2], 0.85))
    fx, fy, fw, fh = rects["front"]
    for px in range(fw):
        if abs(px - (fw - 1) / 2) < 1.2:
            cv.put(fx + px, fy, (36, 30, 24))  # where the two collarbones meet


def deco_scapula(cv, c, rects, pal, seed):
    # the shoulder blade: a raised spine across the top third, a darker hollow below
    bx, by, bw, bh = rects["back"]
    for px in range(bw):
        cv.put(bx + px, by + 2, pal.bone[-1])
        cv.put(bx + px, by + 3, mul(pal.bone[2], 0.8))
    for py in range(4, bh):
        for px in range(1, bw - 1 if py < bh - 2 else bw - 2):
            shade(cv, bx + px, by + py, 0.86)


def deco_skull(cv, c, rects, pal, seed):
    """The cranium: eye sockets under the brow (rows 5-7), the nasal cavity, a crack from the crown down to the right eye
    with soul light glowing inside, suture lines on top, a hole at the back where the spine enters."""
    fx, fy, fw, fh = rects["front"]
    socket = (10, 12, 16)
    for ex in (1, fw - 4):
        for py in range(4, 8):
            for px in range(ex, ex + 3):
                corner = py == 7 and px in (ex, ex + 2)
                cv.put(fx + px, fy + py, mul(pal.bone[1], 0.75) if corner else socket)
                if not corner:
                    cv.glow(fx + px, fy + py, mul(pal.soul_glow, 0.3))
        # the burning pupil
        cv.put(fx + ex + 1, fy + 5, (130, 210, 235))
        cv.put(fx + ex + 1, fy + 6, (80, 160, 200))
        cv.glow(fx + ex + 1, fy + 5, pal.eye)
        cv.glow(fx + ex + 1, fy + 6, mul(pal.soul_glow, 0.85))
        cv.glow(fx + ex, fy + 5, mul(pal.soul_glow, 0.55))
        cv.glow(fx + ex + 2, fy + 5, mul(pal.soul_glow, 0.55))
        cv.glow(fx + ex + 1, fy + 7, mul(pal.soul_glow, 0.45))
    # nasal cavity: a dark triangle between and below the eyes
    cx = fw // 2
    for px, py in ((cx, 7), (cx - 1, 8), (cx, 8), (cx + 1, 8)):
        cv.put(fx + px, fy + py, (16, 14, 14))
    # temples: shade the edges of the face
    for py in range(fh):
        shade(cv, fx, fy + py, 0.82)
        shade(cv, fx + fw - 1, fy + py, 0.82)
    # the crack: from the top edge near the right of the face (model right = texture left on the front) down to the
    # socket, branching; soul light seeps out of it
    crack = [(4, 0), (3, 1), (3, 2), (2, 3), (3, 4)]
    for px, py in crack:
        cv.put(fx + px, fy + py, (20, 22, 26))
        cv.glow(fx + px, fy + py, mul(pal.soul_glow, 0.7))
    for px, py in ((4, 1), (5, 1)):
        cv.put(fx + px, fy + py, mul(pal.bone[1], 0.8))
    tx, ty, tw, th = rects["top"]
    # the crack runs on over the top of the head, gaping wider
    path = [(3, th - 1), (3, th - 2), (4, th - 3), (4, th - 4), (3, th - 5), (4, th - 6), (5, th - 7), (5, th - 8)]
    for i, (px, py) in enumerate(path):
        cv.put(tx + px, ty + py, (16, 18, 22))
        cv.glow(tx + px, ty + py, mul(pal.soul_glow, 0.85 if i < 5 else 0.6))
        if i < 4:
            cv.put(tx + px + 1, ty + py, (34, 40, 46))
            cv.glow(tx + px + 1, ty + py, mul(pal.soul_glow, 0.35))
    # sutures: zig-zags across the crown
    for px in range(tw):
        py = 3 + (px % 2)
        if abs(px - 4) > 1:
            cv.put(tx + px, ty + py, mul(cv.get(tx + px, ty + py), 0.86))
    # the back: foramen and the occipital ridge
    bx, by, bw, bh = rects["back"]
    for px in range(1, bw - 1):
        cv.put(bx + px, by + bh - 3, mul(pal.bone[2], 0.85))
    for px in (bw // 2 - 1, bw // 2):
        cv.put(bx + px, by + bh - 1, (20, 18, 16))
        cv.put(bx + px, by + bh - 2, (28, 24, 20))
    # the sides: the ear hole and a temporal hollow
    for side in ("left", "right"):
        sx, sy, sw, sh = rects[side]
        cv.put(sx + 2, sy + sh - 3, (22, 20, 18))
        cv.put(sx + 3, sy + sh - 3, (30, 26, 22))
        for py in range(3, sh - 4):
            for px in range(sw - 4, sw - 1):
                shade(cv, sx + px, sy + py, 0.88)
    bx, by, bw, bh = rects["bottom"]
    for py in range(bh):
        for px in range(bw):
            shade(cv, bx + px, by + py, 0.7)


def deco_brow(cv, c, rects, pal, seed):
    fx, fy, fw, fh = rects["front"]
    for px in range(fw):
        shade(cv, fx + px, fy, 1.1)
    # the brow dips over the nose
    cv.put(fx + fw // 2, fy + fh - 1, mul(pal.bone[2], 0.9))
    cv.put(fx + fw // 2 - 1, fy + fh - 1, mul(pal.bone[2], 0.9))
    # the crack splits the brow on the right
    cv.put(fx + 3, fy, (20, 22, 26))
    cv.glow(fx + 3, fy, mul(pal.soul_glow, 0.6))
    bx, by, bw, bh = rects["bottom"]
    for py in range(bh):
        for px in range(bw):
            cv.put(bx + px, by + py, (24, 24, 26))
            if 1 <= px <= 3 or bw - 4 <= px <= bw - 2:
                cv.glow(bx + px, by + py, mul(pal.soul_glow, 0.3))


def deco_cheek(cv, c, rects, pal, seed):
    for face in ("bottom",):
        fx, fy, fw, fh = rects[face]
        for py in range(fh):
            for px in range(fw):
                shade(cv, fx + px, fy + py, 0.75)


def teeth(cv, rect, pal, top, seed):
    fx, fy, fw, fh = rect
    for px in range(fw):
        gap = px % 2 == 1
        missing = _h(px, 7, seed) > 0.82
        for py in range(fh):
            if missing:
                cv.put(fx + px, fy + py, (18, 16, 16))
            elif gap:
                cv.put(fx + px, fy + py, mul(pal.bone[2], 0.6))
            else:
                cv.put(fx + px, fy + py, pick(pal.bone, 0.85 - 0.1 * (py if top else fh - 1 - py)))


def deco_teeth_top(cv, c, rects, pal, seed):
    fx, fy, fw, fh = rects["front"]
    teeth(cv, (fx, fy + 1, fw, fh - 1), pal, True, seed)
    for side in ("left", "right"):
        sx, sy, sw, sh = rects[side]
        teeth(cv, (sx, sy + 1, sw, sh - 1), pal, True, seed + 1)
    bx, by, bw, bh = rects["bottom"]
    for py in range(bh):
        for px in range(bw):
            cv.put(bx + px, by + py, (20, 16, 18))
            cv.glow(bx + px, by + py, mul(pal.soul_glow, 0.25))


def deco_teeth_bottom(cv, c, rects, pal, seed):
    tx, ty, tw, th = rects["top"]
    for py in range(th):
        for px in range(tw):
            inner = 0 < px < tw - 1 and py > 0
            cv.put(tx + px, ty + py, (22, 18, 20) if inner else cv.get(tx + px, ty + py))
            if inner:
                cv.glow(tx + px, ty + py, mul(pal.soul_glow, 0.3))
    fx, fy, fw, fh = rects["front"]
    # big lower fangs at the corners poke up over the lip
    teeth(cv, (fx, fy, fw, 1), pal, False, seed)
    for px in (1, fw - 2):
        cv.put(fx + px, fy, pal.bone[-1])
    for py in range(fh):
        shade(cv, fx, fy + py, 0.85)
        shade(cv, fx + fw - 1, fy + py, 0.85)


def deco_joint(cv, c, rects, pal, seed):
    # knobbly joint ends: darker rims, a soul-lit seam across the middle
    for face in SIDES:
        fx, fy, fw, fh = rects[face]
        for px in range(fw):
            shade(cv, fx + px, fy, 1.08)
            shade(cv, fx + px, fy + fh - 1, 0.78)
        if fh >= 3:
            mid = fh // 2
            for px in range(1, fw - 1):
                if _h(px, mid, seed) > 0.35:
                    cv.put(fx + px, fy + mid, mul(cv.get(fx + px, fy + mid), 0.82))
    for face in ("top", "bottom"):
        fx, fy, fw, fh = rects[face]
        for py in range(fh):
            for px in range(fw):
                d = max(abs(px - (fw - 1) / 2) / max(1, fw / 2), abs(py - (fh - 1) / 2) / max(1, fh / 2))
                shade(cv, fx + px, fy + py, 1.08 - 0.25 * d)


def deco_longbone(cv, c, rects, pal, seed):
    # a long bone: lighter flared ends, the shaft a little darker in the middle, grain along it, a few nicks
    for face in SIDES:
        fx, fy, fw, fh = rects[face]
        for py in range(fh):
            m = 1 - abs(py - (fh - 1) / 2) / max(1, (fh - 1) / 2)
            for px in range(fw):
                shade(cv, fx + px, fy + py, 1.06 - 0.12 * m)
        for i in range(2):
            ny = int(_h(i, fh, seed) * (fh - 2)) + 1
            nx = int(_h(i, fw, seed + 1) * fw)
            cv.put(fx + nx, fy + ny, mul(pal.bone[1], 0.85))


def deco_palm(cv, c, rects, pal, seed):
    # metacarpals: finger bones running down the back of the hand
    for face in ("front", "back"):
        fx, fy, fw, fh = rects[face]
        for px in range(1, fw, 2):
            for py in range(fh):
                shade(cv, fx + px, fy + py, 0.78)


def deco_claw(cv, c, rects, pal, seed):
    # curled finger bones: joints every two pixels, dark pointed tips
    for face in ("front", "back", "left", "right"):
        fx, fy, fw, fh = rects[face]
        for py in range(fh):
            for px in range(fw):
                if face in ("front", "back") and px % 2 == 1:
                    cv.put(fx + px, fy + py, (26, 22, 18))
                elif py == 1 or py == 3:
                    shade(cv, fx + px, fy + py, 0.82)
        for px in range(0, fw, 2):
            cv.put(fx + px, fy + fh - 1, mul(pal.bone[1], 0.6))


def deco_femur(cv, c, rects, pal, seed):
    deco_longbone(cv, c, rects, pal, seed)
    # old blood and soot towards the striking end, gouges where it has hit things
    for face in SIDES:
        fx, fy, fw, fh = rects[face]
        for py in range(fh // 2, fh):
            for px in range(fw):
                k = (py - fh // 2) / max(1, fh // 2)
                if vnoise(fx + px, fy + py, 2.0, seed + 30) > 0.55:
                    cv.put(fx + px, fy + py, mix(cv.get(fx + px, fy + py), (70, 34, 24), 0.35 * k))
        for i in range(3):
            gy = int(_h(i, 3, seed + len(face)) * (fh - 3)) + 1
            gx = int(_h(i, 5, seed + len(face)) * max(1, fw - 1))
            cv.put(fx + gx, fy + gy, (40, 32, 26))
            cv.put(fx + gx, fy + gy + 1, mul(pal.bone[2], 0.8))


def deco_condyle(cv, c, rects, pal, seed):
    deco_joint(cv, c, rects, pal, seed)
    # battered knee end: cracks and soot
    for face in SIDES + ("bottom",):
        fx, fy, fw, fh = rects[face]
        for py in range(fh):
            for px in range(fw):
                if vnoise(fx + px, fy + py, 1.5, seed + 3) > 0.72:
                    cv.put(fx + px, fy + py, mix(cv.get(fx + px, fy + py), pal.char, 0.5))
        x = int(_h(1, 1, seed + len(face)) * (fw - 2)) + 1
        for py in range(fh - 1):
            cv.put(fx + x + (py % 2), fy + py, (30, 26, 22))


def deco_wrap(cv, c, rects, pal, seed):
    for face in SIDES:
        fx, fy, fw, fh = rects[face]
        rows(cv, rects[face], (0, -1), mul(pal.wrap[0], 0.9))
        # a loose tail of rag hanging at the bottom on one side
        if face == "front":
            cv.put(fx + 1, fy + fh - 1, pal.wrap[3])


def deco_shard(cv, c, rects, pal, seed):
    # an obsidian shard hammered into the bone: glassy, a purple glint at the tip
    for face in SIDES:
        fx, fy, fw, fh = rects[face]
        cv.put(fx, fy, pick(pal.obsidian, 0.95))
        for py in range(fh):
            if py % 2 == 0:
                cv.put(fx + fw - 1, fy + py, pick(pal.obsidian, 0.7))


def deco_foot(cv, c, rects, pal, seed):
    # metatarsals along the top, soul-sand grime around the sole
    tx, ty, tw, th = rects["top"]
    for px in range(1, tw, 2):
        for py in range(th):
            shade(cv, tx + px, ty + py, 0.8)
    for face in SIDES:
        fx, fy, fw, fh = rects[face]
        for px in range(fw):
            cv.put(fx + px, fy + fh - 1, mix(cv.get(fx + px, fy + fh - 1), pal.grime, 0.7))


def deco_toes(cv, c, rects, pal, seed):
    for face in ("front", "top"):
        fx, fy, fw, fh = rects[face]
        for px in range(fw):
            for py in range(fh):
                if px % 2 == 1:
                    cv.put(fx + px, fy + py, (28, 24, 20))
    fx, fy, fw, fh = rects["front"]
    for px in range(0, fw, 2):
        cv.put(fx + px, fy + fh - 1, mix(cv.get(fx + px, fy + fh - 1), pal.grime, 0.6))


def deco_spike(cv, c, rects, pal, seed):
    # a bone spike: pale at the tip, darker and soul-lit at the root where it leaves the spine
    for face in SIDES:
        fx, fy, fw, fh = rects[face]
        for py in range(fh):
            for px in range(fw):
                k = py / max(1, fh - 1)
                cv.put(fx + px, fy + py, mix(cv.get(fx + px, fy + py), pal.bone[-1], 0.4 * (1 - k)))
        for px in range(fw):
            soul_seam(cv, fx + px, fy + fh - 1, pal, 0.3)


DECOS = {"spike": deco_spike, "pelvis": deco_pelvis, "ilium": deco_ilium, "vertebra": deco_vertebra, "spine": deco_spine, "ridge": deco_ridge,
         "rib": deco_rib, "ribfront": deco_ribfront, "tusk": deco_tusk, "sternum": deco_sternum, "clavicle": deco_clavicle, "scapula": deco_scapula, "skull": deco_skull,
         "brow": deco_brow, "cheek": deco_cheek, "teeth_top": deco_teeth_top, "teeth_bottom": deco_teeth_bottom,
         "joint": deco_joint, "longbone": deco_longbone, "palm": deco_palm, "claw": deco_claw, "femur": deco_femur,
         "condyle": deco_condyle, "wrap": deco_wrap, "shard": deco_shard, "foot": deco_foot, "toes": deco_toes}


# ------------------------------------------------------------------------------------------------ poses (as BoneGiantModel)

smooth = dc.smooth

# Every key pose is a vector of deltas added to the rest pose (the cube table's rotations): right arm x / z, right
# forearm, club, left arm x / z, left forearm, torso lean, torso twist, head pitch, jaw, right leg, right shin, left leg,
# left shin, and how far (model pixels) the hips sink. BoneGiantModel blends to the same numbers.
KEYS = ("ra", "raz", "rf", "club", "la", "laz", "lf", "lean", "twist", "head", "jaw", "rl", "rs", "ll", "ls", "drop")
ZERO = {k: 0.0 for k in KEYS}


def key(**kw):
    d = dict(ZERO)
    d.update(kw)
    return d


# The club swing (BoneGiantEntity.SWING_TICKS = 20): raise 0-8, smash 8-11 (the blow lands at 11), hold to 13, recover to 20
SWING_RAISE = key(ra=-2.35, raz=-0.15, rf=-0.85, club=0.7, la=0.35, lf=0.1, lean=-0.32, twist=0.4, head=0.2, jaw=0.25)
SWING_HIT = key(ra=-0.5, raz=-0.05, rf=-0.4, club=0.3, la=-0.25, lf=-0.2, lean=0.1, twist=-0.35, head=-0.2, jaw=0.15,
                drop=1.5)
# The bone quake (BoneGiantEntity.QUAKE_TICKS = 34): rear up 0-12 and hold, trembling, to 20 (the 1 s telegraph), stomp
# 20-23 (it lands at 22), hold to 26, recover to 34
QUAKE_RAISE = key(ra=-2.5, raz=-0.35, rf=-0.6, club=0.4, la=-2.25, laz=0.35, lf=-0.4, lean=-0.42, head=0.25, jaw=0.55,
                  rl=-1.0, rs=1.15, ll=0.1, ls=0.15, drop=-1.0)
QUAKE_HIT = key(ra=-0.1, raz=-0.1, rf=-0.2, club=0.85, la=0.15, laz=0.05, lf=-0.2, lean=0.38, head=-0.3, jaw=0.4,
                rl=-0.25, rs=0.3, ll=-0.15, ls=0.35, drop=2.5)

SWING_TOTAL = 20.0
QUAKE_TOTAL = 34.0


def swing_weights(e):
    """(target pose, weight) of the club swing `e` ticks in (keep in step with BoneGiantModel)."""
    if e < 8:
        return SWING_RAISE, smooth(e / 8)
    if e < 11:
        k = smooth((e - 8) / 3)
        return {q: SWING_RAISE[q] + (SWING_HIT[q] - SWING_RAISE[q]) * k for q in KEYS}, 1.0
    if e < 13:
        return SWING_HIT, 1.0
    return SWING_HIT, 1.0 - smooth((e - 13) / 7)


def quake_weights(e, t=0.0):
    """(target pose, weight) of the bone quake `e` ticks in (keep in step with BoneGiantModel)."""
    if e < 12:
        return QUAKE_RAISE, smooth(e / 12)
    if e < 20:
        shake = math.sin(t * 2.6) * 0.04
        target = dict(QUAKE_RAISE)
        target["lean"] += shake
        target["twist"] += shake
        return target, 1.0
    if e < 23:
        k = smooth((e - 20) / 2.5)
        return {q: QUAKE_RAISE[q] + (QUAKE_HIT[q] - QUAKE_RAISE[q]) * k for q in KEYS}, 1.0
    if e < 26:
        return QUAKE_HIT, 1.0
    return QUAKE_HIT, 1.0 - smooth((e - 26) / 8)


PART_OF = dict(ra=("r_arm", "rx"), raz=("r_arm", "rz"), rf=("r_forearm", "rx"), club=("club", "rx"), la=("l_arm", "rx"),
               laz=("l_arm", "rz"), lf=("l_forearm", "rx"), lean=("torso", "rx"), twist=("torso", "ry"),
               head=("head", "rx"), jaw=("jaw", "rx"), rl=("r_leg", "rx"), rs=("r_shin", "rx"), ll=("l_leg", "rx"),
               ls=("l_shin", "rx"), drop=("hips", "y"))


def pose(walk_pos=0.0, speed=0.0, swing=None, quake=None, t=0.0, yaw=0.0, pitch=0.0):
    """The deltas BoneGiantModel.setupAnim adds to the rest pose (keep the two in step). swing / quake: ticks in."""
    pos = walk_pos * 0.4
    c, s = math.cos(pos), math.sin(pos)
    lift = abs(s) ** 0.6
    breath = math.sin(t * 0.05)
    cur = dict(ZERO)
    # ---- the lumbering walk: long slow strides, knees bending on the forward swing, the body rolling side to side
    cur["rl"] = c * 0.55 * speed
    cur["ll"] = -c * 0.55 * speed
    cur["rs"] = max(0.0, s) * 0.75 * speed
    cur["ls"] = max(0.0, -s) * 0.75 * speed
    cur["ra"] = -c * 0.22 * speed
    cur["la"] = c * 0.4 * speed
    cur["lean"] = 0.06 * speed + breath * 0.025
    cur["twist"] = c * 0.1 * speed
    cur["jaw"] = 0.04 + 0.04 * math.sin(t * 0.09)
    cur["drop"] = -lift * 1.4 * speed
    roll = s * 0.06 * speed
    w = 0.0
    for ticks, fn in ((swing, swing_weights), (quake, lambda e: quake_weights(e, t))):
        if ticks is not None:
            target, w = fn(ticks)
            cur = {k: cur[k] + (target[k] - cur[k]) * w for k in KEYS}
            roll *= 1 - w
    p = {}
    for k, (part, axis) in PART_OF.items():
        p.setdefault(part, {})[axis] = cur[k]
    p["hips"]["rz"] = roll
    calm = (1 - speed) * (1 - w)
    p["head"]["rx"] += pitch - cur["lean"] * 0.5
    p["head"]["ry"] = yaw + math.sin(t * 0.03) * 0.15 * calm - cur["twist"] * 0.6
    p["neck"] = {"rz": -roll * 0.6}
    flick = 1 + 0.06 * max(0.0, math.sin(t * 0.21)) ** 2
    p["soul"] = {"s": flick}
    return p


# ------------------------------------------------------------------------------------------------ spawn egg

def write_egg():
    import generate_combat as gc  # noqa: E402  (vanilla's egg shape and the mask renderer)
    gi = gc.gi
    shell = ["6a6252", "8c836e", "aea489", "cbc2a4", "e4dcc2"]
    spots = ["0a3a5a", "106a8e", "2aa6d0", "6ad8f2", "c8f6ff"]
    img = gi.render(gc.EGG, {"B": gi.Material(shell), "S": gi.Material(spots, base=3)})
    name = f"{NAME}_spawn_egg"
    img.save(ASSETS / "textures" / "item" / f"{name}.png")
    gc.write_json(ASSETS / "models" / "item" / f"{name}.json",
                  {"parent": "minecraft:item/generated", "textures": {"layer0": f"skyrpg_combat:item/{name}"}})
    gc.write_json(ASSETS / "items" / f"{name}.json", {"model": {"type": "minecraft:model", "model": f"skyrpg_combat:item/{name}"}})


# ------------------------------------------------------------------------------------------------ main

def build(write=True):
    rows_used = M.pack(max_h=256)
    print(f"packed {len(M.cubes)} cubes, {len(M.owners())} unique UV regions, {rows_used} rows used, texture {M.width}x{M.height}")
    cm.check_zfight(M)
    cv = paint(M, Pal(), DECOS)
    if write:
        TEX_DIR.mkdir(parents=True, exist_ok=True)
        cv.img.save(TEX_DIR / f"{NAME}.png")
        cv.layers["glow"].save(TEX_DIR / f"{NAME}_glow.png")
        cv.layers["pulse"].save(TEX_DIR / f"{NAME}_pulse.png")
        order = ", ".join(KEYS)
        extra = (cm.java_floats("SWING_RAISE", [SWING_RAISE[k] for k in KEYS],
                                f"The club swing's raised pose, deltas added to the rest pose: {order}.")
                 + cm.java_floats("SWING_HIT", [SWING_HIT[k] for k in KEYS], "The club swing landing (same order).")
                 + cm.java_floats("QUAKE_RAISE", [QUAKE_RAISE[k] for k in KEYS], "The bone quake's rearing telegraph (same order).")
                 + cm.java_floats("QUAKE_HIT", [QUAKE_HIT[k] for k in KEYS], "The bone quake's stomp (same order)."))
        JAVA_OUT.write_text(M.java("com.skyrpg.combat.client.mob", "BoneGiantLayout", "generate_bone_giant.py",
                                   "The Bone Giant's cubes and their UVs.", extra), encoding="utf-8", newline="\n")
        write_egg()
        print("wrote textures,", JAVA_OUT.name, "and the spawn egg")
    return cv


def main():
    cv = build(write="--no-write" not in sys.argv)
    if "--preview" in sys.argv:
        out = Path(sys.argv[sys.argv.index("--preview") + 1])
        walk = [(dict(pose=pose(walk_pos=math.pi / 2 / 0.4 * f, speed=1.0), yaw=-1.2, pitch=0.1), f"walk {f}") for f in (0.0, 1.0)]
        swing = [(dict(pose=pose(swing=e), yaw=-0.6, pitch=0.12), f"swing tick {e}") for e in (8, 11)]
        quake = [(dict(pose=pose(quake=e), yaw=-1.0, pitch=0.12), f"quake tick {e}") for e in (16, 22)]
        dc.previews(M, cv, out, hitbox=HITBOX, tag=NAME, pose_fn=pose, extra=walk + swing + quake, ppx=6, units=(84, 96))
        glows = [(cv.layers["glow"], 1.0), (cv.layers["pulse"], 0.9)]
        shots = [cm.render_view(M, cv.img, glows, pose=pose(), yaw=y, pitch=0.15, ppx=13, size=(560, 640), feet=(280, 780))
                 for y in (-0.45, 2.5)]
        cm.sheet(shots, ["face and ribcage", "the hump"], out / f"{NAME}_closeup.png")
        print("previews in", out)


if __name__ == "__main__":
    main()
