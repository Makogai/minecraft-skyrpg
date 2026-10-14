#!/usr/bin/env python3
"""Draws the griffin's art (docs/design/the_wild.md, "Griffins and mounts") and writes its model definition.

    python tools/art/generate_griffin.py [--preview DIR]

An eagle's head, neck ruff and wings on a lion's body: a hooked beak, feathered forelegs ending in talons, a tawny
lion rump with hind paws and a tufted tail, and three-jointed wings (arm, forearm, primaries) that fold in a zig-zag
against the back at rest and spread wide in flight. Ten feather colours (PALS), four rarities; the rare ones also write glow textures (griffin_<c>_glow.png, _glow2.png).
The cube table below is the single source of truth for the textures and for the generated Java (tools/art/cube_model.py).
Outputs (only these files):

    mounts/src/main/resources/assets/skyrpg_mounts/textures/entity/griffin/griffin_{golden,storm,snow}.png
    mounts/src/client/java/com/skyrpg/mounts/client/mob/GriffinLayout.java
    mounts/.../textures/item/{griffin_spawn_egg,griffin_whistle_*}.png and their models/items json
    mounts/.../textures/block/griffin_nest.png, griffin_nest_feather.png

Part tree: root > body > (neck > (head > (beak > beak_tip, jaw, crest, tuft_{r,l})), r_leg_f > r_leg_f_b, r_leg_h > r_leg_h_b,
wing_{r,l}_a > wing_{r,l}_b > wing_{r,l}_c, tail > tail_tip). Model space: y down, feet at y=24, -z is the front.
Wings spread to the sides at rest; the client model sets the folded, flapping and gliding poses (GriffinModel, mirrored
by anim_pose() here: keep the two in step).
"""
import json
import math
import random
import sys
from pathlib import Path

from PIL import Image

sys.path.insert(0, str(Path(__file__).resolve().parent))
import cube_model as cm  # noqa: E402
from cube_model import face_rects, get, hnoise, mix, mul, pick, put, ramp, vnoise  # noqa: E402

ROOT = Path(__file__).resolve().parents[2]
ASSETS = ROOT / "mounts/src/main/resources/assets/skyrpg_mounts"
TEX_DIR = ASSETS / "textures/entity/griffin"
JAVA_OUT = ROOT / "mounts/src/client/java/com/skyrpg/mounts/client/mob/GriffinLayout.java"

M = cm.CubeModel("griffin")
part, cube = M.part, M.cube


def sided(base, parent, pivot, rot, cubes, mat_of=None):
    """Right and left parts r_<base> / l_<base> holding the same cubes (pivot, rot and cubes given for the right side,
    x < 0; the left is the mirror image and shares the right's UVs). A parent containing {s} names the same side."""
    for s in ("r", "l"):
        px, py, pz = pivot
        rx, ry, rz = rot
        if s == "l":
            px, ry, rz = -px, -ry, -rz
        par = parent.replace("{s}", s) if parent else parent
        part(f"{s}_{base}", par, (px, py, pz), (rx, ry, rz))
        for cid, mat, pos, size, kw in cubes:
            if s == "r":
                cube(f"{s}_{base}", f"r_{cid}", mat, pos, size, **kw)
            else:
                cube(f"l_{base}", f"l_{cid}", mat, (-(pos[0] + size[0]), pos[1], pos[2]), size, share=f"r_{cid}", mirror=True,
                     **{k: v for k, v in kw.items() if k != "deco"})


# ---- body: a lion's rump and a deep chest, a ridge of mane down the back
part("body", None, (0, 12, 0))
cube("body", "rump", "fur", (-6, -5, 0), (12, 11, 14), deco="rump")
cube("body", "chest", "fur", (-6.5, -6, -11), (13, 12, 11), deco="chest")
cube("body", "ridge", "mane", (-2, -7, -9), (4, 1, 13), deco="ridge")

# ---- tail: a long lion's tail with a tuft
part("tail", "body", (0, -3, 14), (-0.25, 0, 0))
cube("tail", "tail", "fur", (-1.5, -1.5, 0), (3, 3, 9), deco="tail")
part("tail_tip", "tail", (0, 0, 9))
cube("tail_tip", "tuft", "mane", (-2.5, -2.5, 0), (5, 5, 5), deco="tailtuft")

# ---- neck and head: an eagle's, on a feathered ruff
part("neck", "body", (0, -4, -9), (0.2, 0, 0))
cube("neck", "neck", "feather", (-3, -10, -3), (6, 10, 6), deco="neck")
cube("neck", "ruff", "feather", (-4.5, -4, -4), (9, 5, 8), deco="ruff")
part("head", "neck", (0, -10, 0), (-0.2, 0, 0))
cube("head", "head", "feather", (-3, -5, -4), (6, 6, 6), deco="head")
part("beak", "head", (0, -1, -4))
cube("beak", "beak", "beak", (-1.5, -1.5, -5), (3, 3, 5), deco="beak")
part("beak_tip", "beak", (0, 0, -5))
cube("beak_tip", "hook", "beak", (-1, -1.5, -1), (2, 4, 1), deco="hook")
part("jaw", "head", (0, 1.5, -3), (0.05, 0, 0))
cube("jaw", "jaw", "beak", (-1, 0, -3), (2, 1, 4), deco="jaw")
part("crest", "head", (0, -4.5, 2), (-0.5, 0, 0))
cube("crest", "crest", "feather", (-2, -2, 0), (4, 2, 4), deco="crest")
sided("tuft", "head", (-2, -5, -0.5), (0.1, 0, -0.35), [("tuft", "feather", (-0.5, -4, -0.5), (1, 4, 1), dict(deco="tuft"))])

# ---- forelegs (an eagle's: feathered thigh, scaly shank, talons) and hind legs (a lion's: haunch and paw)
sided("leg_f", "body", (-4.5, 4, -7), (0, 0, 0), [("fthigh", "feather", (-2, 0, -2), (4, 5, 4), dict(deco="fthigh"))])
sided("leg_f_b", "{s}_leg_f", (0, 5, 0), (0, 0, 0), [
    ("fshank", "scale", (-1, 0, -1), (2, 2, 2), dict(deco="shank")),
    ("ffoot", "scale", (-2, 2, -4), (4, 1, 5), dict(deco="foot")),
    ("fclaw", "claw", (-1.5, 2, -5), (3, 1, 1), dict(deco="claw"))])
sided("leg_h", "body", (-4.5, 4, 10), (0, 0, 0), [("haunch", "fur", (-2.5, -1, -3), (5, 7, 6), dict(deco="haunch"))])
sided("leg_h_b", "{s}_leg_h", (0, 6, 1), (0, 0, 0), [
    ("paw", "paw", (-2, 0, -3), (4, 2, 5), dict(deco="paw")),
    ("hclaw", "claw", (-1.5, 1, -4), (3, 1, 1), dict(deco="claw"))])

# ---- wings: arm, forearm, primaries (each part reaches along -x from its pivot; the left is the mirror image)
sided("wing_a", "body", (-4.5, -5, -5), (0, 0, 0), [
    ("arm", "feather", (-9, -1, -3), (9, 2, 6), dict(deco="covert")),
    ("arm_t", "flight", (-9, -0.5, 3), (9, 1, 3), dict(deco="trail"))])
sided("wing_b", "{s}_wing_a", (-9, 0, 0), (0, 0, 0), [
    ("fore", "feather", (-10, -1, -3), (10, 2, 7), dict(deco="covert")),
    ("fore_t", "flight", (-10, -0.5, 4), (10, 1, 4), dict(deco="trail"))])
sided("wing_c", "{s}_wing_b", (-10, 0, 0), (0, 0, 0), [
    ("prim", "flight", (-14, -0.5, -2), (14, 1, 10), dict(deco="primaries"))])

# the model's bounds, for fitting the K menu preview: (width of the widest side in blocks) is computed from the folded pose
FOLD = dict(a=1.5, b=3.0, c=-1.5)  # yaw of each wing segment relative to its parent when folded (right wing; negate for the left)


# ------------------------------------------------------------------------------------------------ palettes
class Pal:
    def __init__(self, name, feather, flight, fur, mane, beak, scale, claw, eye, tip, bar, face=None, glow=None, gtip=None, whistle=None):
        self.name = name
        self.face = ramp(*face) if face else ramp(*feather)
        # glow: None, or a set of {"eyes", "tips", "bars", "stars"}; gtip is the colour ramp of glowing tips; whistle the icon's feather ramp
        self.glow = glow
        self.gtip = ramp(*gtip) if gtip else None
        self.whistle = whistle or list(feather)
        self.feather, self.flight, self.fur, self.mane = ramp(*feather), ramp(*flight), ramp(*fur), ramp(*mane)
        self.beak, self.scale, self.claw = ramp(*beak), ramp(*scale), ramp(*claw)
        self.eye, self.tip, self.bar = eye, ramp(*tip), ramp(*bar)


PALS = {
    "golden": Pal("golden",
                  # a pale eagle front, dark brown flight feathers and a tawny lion body, so the parts read apart
                  feather=("b8955a", "d2b47a", "e6cf9e", "f3e4c0", "fcf4e0"),
                  flight=("3e2410", "5a3618", "7a4c20", "a06a2c", "c48e3e"),
                  fur=("7a4e1e", "96642a", "b07c36", "c8964a", "dcb066"),
                  mane=("3e2610", "56381a", "704a24", "8c5e30"),
                  beak=("c8821c", "e8a424", "f6c444", "ffe27a"),
                  scale=("a8861c", "c8a62c", "e0c44c", "f4de78"),
                  claw=("2a2218", "3c3020", "56452e"),
                  eye=(255, 190, 40), tip=("2a1a0a", "3e2812", "58381a"), bar=("f6e6b0", "ecd490")),
    "storm": Pal("storm",
                 feather=("5c6878", "7a889a", "98a6b8", "b8c4d2", "d8e0ea"),
                 flight=("1c2430", "2a3646", "3e4e64", "5a6e88", "7e94b0"),
                 fur=("4c4640", "5e564e", "746a60", "8c8074", "a4988a"),
                 mane=("2e2a26", "3e3832", "52483f", "6a5e52"),
                 beak=("a67c20", "c89a2c", "e0b840", "f2d468"),
                 scale=("7a6a3c", "968454", "b0a068", "ccbc86"),
                 claw=("16181c", "22262c", "343a44"),
                 eye=(110, 200, 255), tip=("0c1018", "161e2a", "222e3e"), bar=("a8bed6", "8aa4c2")),
    "snow": Pal("snow",
                feather=("b4c0cc", "ccd6e0", "e0e8f0", "f0f5fa", "fcfeff"),
                flight=("8c9cae", "a8b8c8", "c6d2de", "e0e8f0", "f6faff"),
                fur=("b8a888", "cdbd9c", "dfd0b0", "ece0c4", "f6eed8"),
                mane=("a49474", "baa888", "ccbc9c", "ded0b2"),
                beak=("c8a040", "e0b850", "f0cc68", "fce290"),
                scale=("b8a460", "d0bc78", "e4d296", "f4e8b6"),
                claw=("3a3a40", "505058", "6c6c76"),
                eye=(120, 190, 255), tip=("6e7c8c", "8494a6", "9caabc"), bar=("c6d2de", "dde6f0")),
    "russet": Pal("russet",
                  # a red-brown hawk front with a cream face, a rusty lion body
                  feather=("6c2c1e", "8a3c28", "a64c32", "c06440", "d88458"),
                  flight=("26120a", "3a1e10", "52301a", "6e4424", "8c5c30"),
                  fur=("94521c", "b06824", "c8802e", "dc9a42", "ecb464"),
                  mane=("4a2210", "64341a", "7e4622", "9a5c2c"),
                  beak=("a67a1c", "c89828", "e0b640", "f2d468"),
                  scale=("a08030", "c0a048", "d8bc68", "ecd48c"),
                  claw=("20160e", "30221a", "46342a"),
                  eye=(255, 176, 40), tip=("1a0c06", "2c160c", "40241a"), bar=("ead6aa", "d8c088"),
                  face=("c2a074", "d6bb90", "e6d2aa", "f2e6c6")),
    "midnight": Pal("midnight",
                    feather=("2a2460", "3a3282", "4c46a2", "625cc0", "8680dc"),
                    flight=("0c0a20", "161230", "221c46", "30285e", "423a7c"),
                    fur=("0a0a10", "121218", "1a1a24", "242432", "30303e"),
                    mane=("06060a", "0e0e14", "16161e", "202028"),
                    beak=("505060", "6a6a7c", "8a8a9e", "b0b0c4"),
                    scale=("585868", "74748a", "9494aa", "b8b8cc"),
                    claw=("08080c", "101016", "1c1c26"),
                    eye=(226, 234, 248), tip=("04040a", "0a0a16", "12121f"), bar=("98a2c0", "7a84a6")),
    "verdant": Pal("verdant",
                   feather=("1a6a5a", "248470", "30a086", "44bc9e", "6ad8b6"),
                   flight=("082620", "10382f", "1a4e42", "266a58", "368a72"),
                   fur=("46461a", "5c5c24", "74722e", "8e8c3c", "a8a654"),
                   mane=("2a2a10", "3a3a16", "4e4e20", "66662c"),
                   beak=("a8842a", "c8a038", "e0bc50", "f2d878"),
                   scale=("7a8030", "98a044", "b4bc5e", "d0d884"),
                   claw=("141a0e", "202a16", "323e22"),
                   eye=(196, 255, 120), tip=("04120c", "0a1e16", "122c20"), bar=("a8e8c8", "80d0a8")),
    "ember": Pal("ember",
                 feather=("b43a0c", "d4500e", "ee701a", "ff9230", "ffb460"),
                 flight=("260a06", "3c120a", "58200c", "7c2c10", "a43c14"),
                 fur=("181616", "262222", "342e2e", "463e3e", "5a5050"),
                 mane=("0e0c0c", "181414", "241e1e", "322a2a"),
                 beak=("5a2a10", "7a3c14", "a0541c", "c87430"),
                 scale=("603414", "804a1c", "a06428", "c08040"),
                 claw=("100c0c", "1a1616", "2a2424"),
                 eye=(255, 150, 30), tip=("1c0804", "2c0e06", "401608"), bar=("ff9a40", "e07020"),
                 glow={"eyes", "tips"}, gtip=("ff4a0a", "ff7a1a", "ffa830", "ffd870")),
    "frost": Pal("frost",
                 feather=("a6cee8", "c0e0f4", "d8f0fc", "ecf8ff", "fcffff"),
                 flight=("46688e", "58809e", "6c98bc", "86b2d6", "a6cdf0"),
                 fur=("7e92ac", "96aac2", "aec2d6", "c6d6e4", "dce8f2"),
                 mane=("647894", "7a8eaa", "92a6be", "aabcd0"),
                 beak=("84b4cc", "a4cee2", "c2e4f4", "e0f6fc"),
                 scale=("88b4c8", "a8d0e0", "c8e8f4", "e4f8fc"),
                 claw=("2c3c52", "3e5470", "587290"),
                 eye=(140, 240, 255), tip=("2a5a88", "3c78a8", "58a0d0"), bar=("e0f4ff", "c0e4f8"),
                 glow={"eyes", "tips"}, gtip=("38b8ff", "70d8ff", "a8ecff", "e0faff")),
    "prismatic": Pal("prismatic",
                     # a pearl base: the client tints the whole griffin through the rainbow
                     feather=("dcd8e6", "e8e4f0", "f2eff8", "fbf9ff", "ffffff"),
                     flight=("6c6888", "807c9c", "9894b2", "b0acc8", "ccc8de"),
                     fur=("a49cb6", "b6aec8", "c8c0da", "d8d2e8", "e8e4f4"),
                     mane=("82799a", "9a91b0", "afa7c4", "c4bdd6"),
                     beak=("d6c694", "e6d9ae", "f2e8c8", "fcf6e2"),
                     scale=("d0c490", "e0d6aa", "f0e8c8", "fcf8e4"),
                     claw=("524e66", "66627c", "82809a"),
                     eye=(255, 255, 255), tip=("5a5672", "6c688a", "8480a4"), bar=("ffffff", "f0eaff"),
                     glow={"eyes", "tips", "bars"}, gtip=("e0dcff", "f2f0ff", "ffffff")),
    "astral": Pal("astral",
                  feather=("141c4c", "1e2a66", "2a3a84", "3a4ca2", "505ec0"),
                  flight=("0a0620", "140c34", "201648", "2e2060", "3e2c7c"),
                  fur=("0c1030", "141a44", "1c2458", "26306c", "323e84"),
                  mane=("080a22", "0e1232", "141a42", "1c2454"),
                  beak=("80889e", "a0a8c0", "c0c8dc", "e0e4f0"),
                  scale=("747c9a", "9098b8", "b0b8d4", "d0d8ec"),
                  claw=("06060e", "0c0c1a", "161628"),
                  eye=(190, 212, 255), tip=("04040e", "080818", "0e0e24"), bar=("5a6ac8", "44509c"),
                  glow={"eyes", "stars"}),
}
FACE_LIGHT = {"top": 1.12, "bottom": 0.72, "right": 0.92, "front": 1.0, "left": 0.92, "back": 0.86}


# ------------------------------------------------------------------------------------------------ painting
def feather_px(X, Y, pal_ramp, seed, row_h=3, per=4):
    """Overlapping scallops: rows of feathers, every other row shifted, each darker at its lower edge and between."""
    row = Y // row_h
    xo = X + (row % 2) * (per // 2)
    fx, fy = xo % per, Y % row_h
    n = vnoise(X, Y, 2.5, seed)
    t = 0.45 + 0.4 * n + 0.1 * (hnoise(X, Y, seed + 3) - 0.5)
    if fy == row_h - 1:
        t -= 0.2
    elif fy == 0:
        t += 0.12
    if fx == 0:
        t -= 0.13
    return pick(pal_ramp, t)


def base_pixel(c, pal, X, Y, face, seed):
    mat = c["mat"]
    n = vnoise(X, Y, 3.0, seed)
    h = hnoise(X, Y, seed + 5)
    if mat == "feather":
        return feather_px(X, Y, pal.feather, seed)
    if mat == "flight":
        return pick(pal.flight, 0.4 + 0.45 * vnoise(X * 0.5, Y, 2.0, seed) + 0.15 * (h - 0.5))
    if mat == "fur":
        streak = vnoise(X * 0.7, Y * 1.4, 2.2, seed)
        col = pick(pal.fur, 0.3 + 0.5 * streak + 0.2 * (h - 0.5))
        return mul(col, 1.18) if h > 0.93 else col
    if mat == "mane":
        return pick(pal.mane, 0.25 + 0.6 * vnoise(X * 0.8, Y * 1.6, 1.8, seed) + 0.2 * (h - 0.5))
    if mat == "paw":
        return pick(pal.fur, 0.35 + 0.45 * n + 0.2 * (h - 0.5))
    if mat == "beak":
        return pick(pal.beak, 0.35 + 0.5 * n)
    if mat == "scale":
        return pick(pal.scale, 0.3 + 0.5 * n + 0.2 * ((X + Y) % 2))
    if mat == "claw":
        return pick(pal.claw, n)
    return (255, 0, 255)


def paint_cube(img, pal, c):
    rects = face_rects(c)
    seed = sum(ord(ch) * (i + 1) for i, ch in enumerate(c["id"].replace("l_", "r_", 1)))
    for face, (fx, fy, fw, fh) in rects.items():
        for py in range(fh):
            for px in range(fw):
                col = base_pixel(c, pal, fx + px, fy + py, face, seed)
                k = FACE_LIGHT[face]
                if face in ("front", "back", "left", "right") and py == 0:
                    k *= 1.08
                put(img, fx + px, fy + py, mul(col, k))
    d = c.get("deco")
    if d:
        DECOS[d](img, pal, c, rects, seed)


# the glow layers of the texture being painted: [0] eyes and half the stars, [1] wing tips, trim and the other stars
GL = [None, None]


def glow_put(k, x, y, col):
    if GL[k] is not None:
        put(GL[k], x, y, col)


def cut(img, x, y):
    img.putpixel((int(x), int(y)), (0, 0, 0, 0))
    for g in GL:
        if g is not None and 0 <= int(x) < g.width and 0 <= int(y) < g.height:
            g.putpixel((int(x), int(y)), (0, 0, 0, 0))


def deco_rump(img, pal, c, rects, seed):
    # a paler belly; faint lion-coat flecks along the flanks
    bx, by, bw, bh = rects["bottom"]
    for py in range(bh):
        for px in range(bw):
            put(img, bx + px, by + py, mix(get(img, bx + px, by + py), pal.fur[-1], 0.55))
    for face in ("left", "right"):
        sx, sy, sw, sh = rects[face]
        for py in range(sh - 3, sh):
            for px in range(sw):
                put(img, sx + px, sy + py, mix(get(img, sx + px, sy + py), pal.fur[-1], 0.25 * (py - sh + 4)))


def deco_chest(img, pal, c, rects, seed):
    # the chest is feathered like the eagle half: scallops on the front and the upper sides
    for face in ("front", "left", "right", "top"):
        fx, fy, fw, fh = rects[face]
        for py in range(fh):
            for px in range(fw):
                if face == "top" or py < fh * 0.62:
                    col = feather_px(fx + px, fy + py, pal.feather, seed + 9)
                    put(img, fx + px, fy + py, mul(col, FACE_LIGHT[face] * (1.08 if py == 0 and face != "top" else 1.0)))
    deco_rump(img, pal, c, rects, seed)


def deco_ridge(img, pal, c, rects, seed):
    for face, (fx, fy, fw, fh) in rects.items():
        for py in range(fh):
            for px in range(fw):
                if (px + py) % 3 == 0:
                    put(img, fx + px, fy + py, mul(get(img, fx + px, fy + py), 0.8))


def deco_tail(img, pal, c, rects, seed):
    pass


def deco_tailtuft(img, pal, c, rects, seed):
    for face, (fx, fy, fw, fh) in rects.items():
        for py in range(fh):
            for px in range(fw):
                if hnoise(fx + px, fy + py, seed) > 0.72:
                    put(img, fx + px, fy + py, mul(get(img, fx + px, fy + py), 0.72))


def deco_neck(img, pal, c, rects, seed):
    pass


def deco_ruff(img, pal, c, rects, seed):
    # the white-ish collar: brighter feathers, ragged lower edge in rows
    for face, (fx, fy, fw, fh) in rects.items():
        for py in range(fh):
            for px in range(fw):
                put(img, fx + px, fy + py, mul(get(img, fx + px, fy + py), 1.12))
    for face in ("front", "left", "right", "back"):
        fx, fy, fw, fh = rects[face]
        for px in range(fw):
            if (px + fx) % 2 == 0:
                put(img, fx + px, fy + fh - 1, mul(get(img, fx + px, fy + fh - 1), 0.8))


def deco_head(img, pal, c, rects, seed):
    # smooth head feathers, a darker brow line, and the eyes at the front corners of the sides
    for face, (fx, fy, fw, fh) in rects.items():
        for py in range(fh):
            for px in range(fw):
                col = pick(pal.face, 0.55 + 0.35 * vnoise(fx + px, fy + py, 2.0, seed) + 0.1 * (hnoise(fx + px, fy + py, seed) - 0.5))
                put(img, fx + px, fy + py, mul(col, FACE_LIGHT[face] * (1.15 if face in ("front", "top") else 1.0)))
    for face in ("front", "left", "right"):
        fx, fy, fw, fh = rects[face]
        for px in range(fw):
            put(img, fx + px, fy + 1, mul(get(img, fx + px, fy + 1), 0.62))
    eye = pal.eye
    for face, ex in (("right", 0), ("left", 0)):
        # right (the -x side) shows its front in the right-hand column; left (+x) in the left-hand column
        fx, fy, fw, fh = rects[face]
        cx = fx + (fw - 3 if face == "right" else 1)
        put(img, cx, fy + 2, eye)
        put(img, cx + 1, fy + 2, eye)
        put(img, cx, fy + 3, (30, 22, 14))
        put(img, cx + 1, fy + 3, eye)
        put(img, cx, fy + 1, (24, 18, 10))
        put(img, cx + 1, fy + 1, (24, 18, 10))
        if pal.glow and "eyes" in pal.glow:
            for gx, gy in ((cx, fy + 2), (cx + 1, fy + 2), (cx + 1, fy + 3)):
                glow_put(0, gx, gy, eye)
    # the front: the beak base darkens the middle
    fx, fy, fw, fh = rects["front"]
    for py in range(2, 5):
        for px in range(2, fw - 2):
            put(img, fx + px, fy + py, mix(get(img, fx + px, fy + py), pal.beak[0], 0.55))


def deco_beak(img, pal, c, rects, seed):
    for face, (fx, fy, fw, fh) in rects.items():
        for py in range(fh):
            for px in range(fw):
                t = 0.65 + 0.3 * (1 - py / max(1, fh)) if face in ("left", "right") else 0.5
                col = pick(pal.beak, t * 0.6 + 0.4 * vnoise(fx + px, fy + py, 2.0, seed))
                put(img, fx + px, fy + py, mul(col, FACE_LIGHT[face]))
    fx, fy, fw, fh = rects["front"]
    for px in range(fw):
        put(img, fx + px, fy + fh - 1, mul(get(img, fx + px, fy + fh - 1), 0.7))
    # nostrils on the sides
    for face in ("left", "right"):
        sx, sy, sw, sh = rects[face]
        px = sx + (sw - 2 if face == "right" else 1)
        put(img, px, sy + 1, (40, 28, 10))


def deco_hook(img, pal, c, rects, seed):
    for face, (fx, fy, fw, fh) in rects.items():
        for py in range(fh):
            for px in range(fw):
                t = 0.35 + 0.5 * (1 - py / max(1, fh))
                put(img, fx + px, fy + py, mul(pick(pal.beak, t), FACE_LIGHT[face]))
    fx, fy, fw, fh = rects["front"]
    for px in range(fw):
        put(img, fx + px, fy + fh - 1, mul(pal.beak[0], 0.9))


def deco_jaw(img, pal, c, rects, seed):
    pass


def deco_crest(img, pal, c, rects, seed):
    for face, (fx, fy, fw, fh) in rects.items():
        for py in range(fh):
            for px in range(fw):
                put(img, fx + px, fy + py, mul(pick(pal.feather, 0.25 + 0.2 * ((px + py) % 2) + 0.4 * (py / max(1, fh))), FACE_LIGHT[face]))


def deco_tuft(img, pal, c, rects, seed):
    for face, (fx, fy, fw, fh) in rects.items():
        for py in range(fh):
            for px in range(fw):
                put(img, fx + px, fy + py, mul(pick(pal.feather, 0.3 + 0.12 * py), FACE_LIGHT[face]))


def deco_fthigh(img, pal, c, rects, seed):
    for face in ("front", "left", "right", "back"):
        fx, fy, fw, fh = rects[face]
        for px in range(fw):
            put(img, fx + px, fy + fh - 1, mul(get(img, fx + px, fy + fh - 1), 0.75))


def deco_shank(img, pal, c, rects, seed):
    for face, (fx, fy, fw, fh) in rects.items():
        for py in range(fh):
            for px in range(fw):
                if (px + py) % 2 == 0:
                    put(img, fx + px, fy + py, mul(get(img, fx + px, fy + py), 0.82))


def deco_foot(img, pal, c, rects, seed):
    deco_shank(img, pal, c, rects, seed)


def deco_claw(img, pal, c, rects, seed):
    pass


def deco_haunch(img, pal, c, rects, seed):
    for face in ("left", "right", "front", "back"):
        fx, fy, fw, fh = rects[face]
        for py in range(fh):
            for px in range(fw):
                if hnoise(fx + px, fy + py, seed + 1) > 0.85:
                    put(img, fx + px, fy + py, mul(get(img, fx + px, fy + py), 0.82))


def deco_paw(img, pal, c, rects, seed):
    # pale toe-beans line on the underside; a darker pad edge
    bx, by, bw, bh = rects["bottom"]
    for py in range(bh):
        for px in range(bw):
            put(img, bx + px, by + py, mix(pal.claw[1], pal.fur[0], 0.5))
    fx, fy, fw, fh = rects["front"]
    for px in range(fw):
        if px % 2 == 0:
            put(img, fx + px, fy + fh - 1, mul(get(img, fx + px, fy + fh - 1), 0.7))


def deco_covert(img, pal, c, rects, seed):
    """Wing coverts: scalloped feathers on the outer faces, brighter near the leading edge (the front)."""
    for face in ("top", "bottom"):
        fx, fy, fw, fh = rects[face]
        for py in range(fh):
            for px in range(fw):
                col = feather_px(fx + px, fy + py, pal.feather, seed + 2, row_h=3, per=3)
                if face == "bottom":
                    col = mul(col, 0.88)
                put(img, fx + px, fy + py, mul(col, FACE_LIGHT[face] if face == "top" else 0.85))


def deco_trail(img, pal, c, rects, seed):
    """The secondaries along the trailing edge: long feathers (strips along the span) with a dark tip and a pale bar."""
    for face in ("top", "bottom"):
        fx, fy, fw, fh = rects[face]
        for py in range(fh):
            for px in range(fw):
                col = pick(pal.flight, 0.35 + 0.35 * ((px // 2 + py) % 2) + 0.2 * vnoise(fx + px, fy + py, 2.0, seed))
                if py == fh - 1:
                    col = pick(pal.tip, 0.4 + 0.3 * (px % 2))
                    if pal.glow and "tips" in pal.glow:
                        col = pick(pal.gtip, 0.3 + 0.4 * (px % 2))
                        glow_put(1, fx + px, fy + py, col)
                put(img, fx + px, fy + py, mul(col, FACE_LIGHT[face] if face == "top" else 0.85))


def deco_primaries(img, pal, c, rects, seed):
    """The long primaries: feather strips along the span, barred, darker tips outward and ragged trailing fingers."""
    w, h, d = c["size"]
    for face in ("top", "bottom"):
        fx, fy, fw, fh = rects[face]
        for py in range(fh):
            # feather strips: a strip is 2 rows wide, with its shaft on the leading row
            strip = py // 2
            for px in range(fw):
                x_out = 1.0 - px / (fw - 1)  # 0 at the shoulder end (right-hand columns of the right wing), 1 at the tip
                t = 0.45 + 0.28 * (strip % 2) + 0.15 * (hnoise(px, strip, seed) - 0.5) + 0.12 * vnoise(fx + px, fy + py, 2.0, seed)
                col = pick(pal.flight, t)
                if py % 2 == 0:
                    col = mul(col, 1.12)
                if (px + strip * 3) % 6 in (0, 1) and x_out > 0.2:
                    col = mix(col, pal.bar[strip % 2], 0.32)
                    if pal.glow and "bars" in pal.glow and py % 2 == 0:
                        glow_put(1, fx + px, fy + py, mix(col, pal.bar[strip % 2], 0.8))
                if x_out > 0.78 - 0.08 * ((strip + 1) % 3) or (pal.glow and "tips" in pal.glow and x_out > 0.68):
                    col = mix(col, pick(pal.tip, 0.4 + 0.3 * ((px + py) % 2)), min(1.0, (x_out - 0.7) * 4))
                    if pal.glow and "tips" in pal.glow and x_out > 0.68:
                        col = pick(pal.gtip, 0.25 + 0.5 * ((px + py) % 2) + 0.25 * (x_out - 0.68) * 3)
                        glow_put(1, fx + px, fy + py, col)
                put(img, fx + px, fy + py, mul(col, FACE_LIGHT[face] if face == "top" else 0.85))
        # trailing fingers: rows are z (row 0 is the trailing edge for top and bottom alike); cut notches between strips
        for strip in range(0, fh // 2):
            length = 2 + (strip * 5 + seed) % 3  # how far the feather reaches past the strip below (in notch depth)
            for py in range(2 * strip, 2 * strip + 2):
                pass
        for px in range(fw):
            x_out = 1.0 - px / (fw - 1)
            notch = 0
            if px % 3 == 1:
                notch = 2 if x_out > 0.15 else 0
            elif px % 3 == 2:
                notch = 1 if x_out > 0.4 else 0
            for py in range(min(notch, fh)):
                cut(img, fx + px, fy + py)
    # the thin side faces take a mid colour so the edge doesn't show a seam
    for face in ("left", "right", "front", "back"):
        fx, fy, fw, fh = rects[face]
        for py in range(fh):
            for px in range(fw):
                put(img, fx + px, fy + py, mul(pick(pal.flight, 0.35), 0.9))


DECOS = {k[5:]: v for k, v in dict(globals()).items() if k.startswith("deco_")}


def add_stars(img, pal):
    """Astral: star specks over every face (half in each glow layer so they twinkle apart) and faint lines joining the
    stars of the larger faces, a constellation."""
    rng = random.Random(4242)
    star_cols = [(255, 255, 255), (200, 220, 255), (255, 236, 190), (170, 190, 255)]
    for c in M.owners():
        for face, (fx, fy, fw, fh) in face_rects(c).items():
            stars = []
            for py in range(fh):
                for px in range(fw):
                    if img.getpixel((fx + px, fy + py))[3] > 0 and rng.random() < 0.05:
                        stars.append((fx + px, fy + py))
            if fw >= 7 and fh >= 7:
                # a constellation: up to four stars of this face in a chain of faint dots
                chain = sorted(stars[:4], key=lambda p: p[0])
                for (x0, y0), (x1, y1) in zip(chain, chain[1:]):
                    n = max(abs(x1 - x0), abs(y1 - y0))
                    if n == 0 or n > 10:
                        continue
                    for i in range(1, n):
                        x, y = round(x0 + (x1 - x0) * i / n), round(y0 + (y1 - y0) * i / n)
                        if img.getpixel((x, y))[3] > 0 and (x, y) not in stars:
                            put(img, x, y, mix(get(img, x, y), (96, 120, 220), 0.35))
                            glow_put(0, x, y, (54, 74, 150))
            for x, y in stars:
                col = rng.choice(star_cols)
                put(img, x, y, mul(col, 0.8))
                glow_put(rng.randint(0, 1), x, y, col)


def paint(pal):
    img = Image.new("RGBA", (M.width, M.height), (0, 0, 0, 0))
    GL[0] = Image.new("RGBA", (M.width, M.height), (0, 0, 0, 0)) if pal.glow else None
    GL[1] = Image.new("RGBA", (M.width, M.height), (0, 0, 0, 0)) if pal.glow else None
    for c in M.owners():
        paint_cube(img, pal, c)
    if pal.glow and "stars" in pal.glow:
        add_stars(img, pal)
    return img


# ------------------------------------------------------------------------------------------------ animation (mirrored in GriffinModel.setupAnim)
def anim_pose(t, walk_pos=0.0, walk_speed=0.0, mode="ground", look=(0.0, 0.0)):
    """mode: "ground" (wings folded, walking), "flap" (flying, flapping), "glide" (flying, wings spread and still)."""
    sin, cos = math.sin, math.cos
    pose = {}
    if mode == "ground":
        fold = 1.0
    else:
        fold = 0.0
    sp = min(1.0, walk_speed * 1.6) if mode == "ground" else 0.0
    pos = walk_pos * 0.9
    pose["body"] = {"off": (0, -abs(sin(pos)) * 0.5 * sp + sin(t * 0.07) * 0.2, 0),
                    "rot": (0.06 if mode == "glide" else (0.0 if mode == "ground" else -0.06 + 0.04 * sin(t * 0.6)), 0, 0)}
    pose["neck"] = {"rot": (0.2 + (0.35 if mode == "glide" else 0.0) + 0.05 * sin(t * 0.09), 0, 0)}
    pose["head"] = {"rot": (-0.2 - (0.3 if mode == "glide" else 0.0) + look[1], look[0], 0)}
    pose["jaw"] = {"rot": (0.05 + max(0.0, sin(t * 0.21 + 1) - 0.85) * 1.5, 0, 0)}
    pose["tail"] = {"rot": (-0.25 + (0.25 if mode != "ground" else 0.0), sin(t * 0.11) * 0.12 + sin(pos) * 0.15 * sp, 0)}
    for side, sg in (("r", 1), ("l", -1)):
        if mode == "ground":
            c = pos + (0 if side == "r" else math.pi)
            pose[f"{side}_leg_f"] = {"rot": (sin(c) * 0.7 * sp, 0, 0)}
            pose[f"{side}_leg_f_b"] = {"rot": (max(0.0, -sin(c)) * 0.6 * sp, 0, 0)}
            pose[f"{side}_leg_h"] = {"rot": (-sin(c) * 0.7 * sp, 0, 0)}
            pose[f"{side}_leg_h_b"] = {"rot": (max(0.0, sin(c)) * 0.6 * sp, 0, 0)}
        else:
            pose[f"{side}_leg_f"] = {"rot": (0.9, 0, 0)}
            pose[f"{side}_leg_f_b"] = {"rot": (1.0, 0, 0)}
            pose[f"{side}_leg_h"] = {"rot": (-0.5, 0, 0)}
            pose[f"{side}_leg_h_b"] = {"rot": (0.6, 0, 0)}
        # wings
        if mode == "ground":
            breathe = sin(t * 0.08) * 0.03
            pose[f"{side}_wing_a"] = {"rot": (0, sg * FOLD["a"], sg * (0.1 + breathe))}
            pose[f"{side}_wing_b"] = {"rot": (0, sg * FOLD["b"], sg * breathe), "off": (0, -2.1, 0)}
            pose[f"{side}_wing_c"] = {"rot": (0, sg * FOLD["c"], 0), "off": (0, -1.5, 0)}
        elif mode == "flap":
            f = t * 0.75
            pose[f"{side}_wing_a"] = {"rot": (0, 0, sg * (sin(f) * 0.7 + 0.12))}
            pose[f"{side}_wing_b"] = {"rot": (0, sg * -0.05, sg * (sin(f - 0.55) * 0.5 - 0.03))}
            pose[f"{side}_wing_c"] = {"rot": (0, sg * -0.1, sg * (sin(f - 1.1) * 0.55 - 0.05))}
        else:
            pose[f"{side}_wing_a"] = {"rot": (0, sg * 0.08, sg * (0.1 + sin(t * 0.1) * 0.02))}
            pose[f"{side}_wing_b"] = {"rot": (0, sg * -0.1, sg * 0.0)}
            pose[f"{side}_wing_c"] = {"rot": (0, sg * -0.2, sg * -0.05)}
    return pose


# ------------------------------------------------------------------------------------------------ preview
def preview(out_dir):
    out_dir = Path(out_dir)
    out_dir.mkdir(parents=True, exist_ok=True)
    rows = []
    for name, pal in PALS.items():
        tex = Image.open(TEX_DIR / f"griffin_{name}.png").convert("RGBA")
        if pal.glow:  # the glow layers added on, and a mid-rainbow tint for prismatic
            for g in ("glow", "glow2"):
                glow = Image.open(TEX_DIR / f"griffin_{name}_{g}.png").convert("RGBA")
                tex = Image.composite(glow, tex, glow.getchannel("A"))
        if name == "prismatic":
            px = tex.load()
            for yy in range(tex.height):
                for xx in range(tex.width):
                    r, g2, b, a = px[xx, yy]
                    px[xx, yy] = (int(r * 0.55), int(g2 * 0.95), int(b * 0.85), a)
        r = lambda **kw: cm.render(M, tex, None, look_at=(0, 12, 0), **kw)
        big = dict(scale=7, size=(330, 300), center=(0.5, 0.55))
        views = [r(yaw=0.7, pitch=0.35, pose=anim_pose(3), **big), r(yaw=-0.9, pitch=0.25, pose=anim_pose(3), **big),
                 r(yaw=math.pi / 2, pitch=0.05, pose=anim_pose(3, 2.0, 0.6), **big),
                 r(yaw=2.4, pitch=0.45, pose=anim_pose(3), **big)]
        rows.append(cm.sheet(views, [f"{name} folded 3/4", "other side", "walking side", "back"]))
    flap = [cm.render(M, Image.open(TEX_DIR / "griffin_golden.png"), None, look_at=(0, 12, 0), yaw=0.5, pitch=0.3,
                      pose=anim_pose(t, mode="flap"), scale=6, size=(330, 300), center=(0.5, 0.5)) for t in (0, 3, 6, 9)]
    rows.append(cm.sheet(flap, ["flap 0", "flap 3", "flap 6", "flap 9"]))
    fr = [cm.render(M, Image.open(TEX_DIR / "griffin_storm.png"), None, look_at=(0, 12, 0), yaw=y, pitch=p, pose=anim_pose(5, mode="glide"),
                    scale=6, size=(330, 300), center=(0.5, 0.5)) for y, p in ((0.5, 0.3), (0.0, 0.2), (math.pi / 2, 0.1), (0.5, 1.2))]
    rows.append(cm.sheet(fr, ["glide 3/4", "glide front", "glide side", "glide top"]))
    cm.stack(rows).save(out_dir / "griffin_preview.png")
    cm.texture_sheet(Image.open(TEX_DIR / "griffin_golden.png"), Image.new("RGBA", (M.width, M.height)), zoom=4).save(out_dir / "griffin_texture.png")
    print("wrote previews to", out_dir)


# ------------------------------------------------------------------------------------------------ items and block
def write_json(path, obj):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(obj, indent=2) + "\n", encoding="utf-8", newline="\n")


def spawn_egg():
    import generate_combat as gc
    import generate_items as gi
    img = gi.render(gc.EGG, {"B": gi.Material(["8a5a14", "a8741c", "c8962c", "e0b448", "f2d070"], base=2),
                             "S": gi.Material(["322c26", "463e34", "5c5042", "8c8074", "d8c8a8"], base=3)})
    (ASSETS / "textures/item").mkdir(parents=True, exist_ok=True)
    img.save(ASSETS / "textures/item/griffin_spawn_egg.png")
    write_json(ASSETS / "models/item/griffin_spawn_egg.json", {"parent": "minecraft:item/generated", "textures": {"layer0": "skyrpg_mounts:item/griffin_spawn_egg"}})
    write_json(ASSETS / "items/griffin_spawn_egg.json", {"model": {"type": "minecraft:model", "model": "skyrpg_mounts:item/griffin_spawn_egg"}})


WHISTLE = [
    "................",
    "..........ff....",
    ".........ffff...",
    "........fFfff...",
    ".......fFFfff...",
    "..bbb.fFFfff....",
    ".bBBBbFFfff.....",
    ".bBBBBBbf.......",
    "bbBBBBBBb.......",
    "bBBBBBBBb.......",
    ".bBBBBBbb.......",
    "..bbbbbb........",
    "................",
    "................",
    "................",
    "................",
]


def whistles():
    import generate_items as gi
    brass = gi.Material(["5e3a08", "9a6814", "d0a024", "f0d050", "fff4a8"], texture=gi.sheen)
    defs = {"golden": (["8a5a14", "a8741c", "c8962c", "e0b448", "f2d070"]), "storm": (["323c4a", "465366", "5c6c82", "7488a0", "98acc4"]),
            "snow": (["b4c0cc", "ccd6e0", "e0e8f0", "f0f5fa", "fcfeff"])}
    for n, pal in PALS.items():
        defs.setdefault(n, pal.whistle)
    (ASSETS / "textures/item").mkdir(parents=True, exist_ok=True)
    for name, feather in defs.items():
        img = gi.render(WHISTLE, {"b": brass, "B": brass.shifted(base=3), "f": gi.Material(feather, base=2), "F": gi.Material(feather, base=3)})
        img.save(ASSETS / f"textures/item/griffin_whistle_{name}.png")
        write_json(ASSETS / f"models/item/griffin_whistle_{name}.json",
                   {"parent": "minecraft:item/generated", "textures": {"layer0": f"skyrpg_mounts:item/griffin_whistle_{name}"}})
    # one item, three looks: picked by the colour name in its custom model data
    write_json(ASSETS / "items/griffin_whistle.json", {"model": {
        "type": "minecraft:select", "property": "minecraft:custom_model_data", "index": 0,
        "cases": [{"when": n, "model": {"type": "minecraft:model", "model": f"skyrpg_mounts:item/griffin_whistle_{n}"}} for n in defs],
        "fallback": {"type": "minecraft:model", "model": "skyrpg_mounts:item/griffin_whistle_golden"}}})


def nest_block():
    rng = random.Random(11)
    sticks = ramp("2e1c0c", "46301a", "62431f", "84602c", "a47c3c")
    hay = ramp("8a6a1c", "b08c2a", "d4b04a", "ecd078")
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 255))
    for y in range(16):
        for x in range(16):
            # straw weave with crossing sticks
            t = vnoise(x * 1.7, y * 0.6, 2.0, 5) * 0.5 + hnoise(x, y, 3) * 0.5
            col = pick(hay, t)
            if (x + y * 2) % 7 in (0, 1) or (x * 2 - y) % 9 == 0:
                col = pick(sticks, 0.3 + 0.6 * hnoise(x, y, 8))
            put(img, x, y, col)
    (ASSETS / "textures/block").mkdir(parents=True, exist_ok=True)
    img.save(ASSETS / "textures/block/griffin_nest.png")
    # a couple of white-and-gold feathers lying in the nest
    f = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    shaft = [(3, 12), (4, 11), (5, 10), (6, 9), (7, 8), (8, 7), (9, 6), (10, 5), (11, 4), (12, 3)]
    for i, (x, y) in enumerate(shaft):
        put(f, x, y, (236, 226, 196))
        for k in range(1, 3):
            if i > 1 and i < 9:
                put(f, x - k, y - 1 + (k % 2), (230, 200, 120) if k == 1 else (200, 150, 70))
                put(f, x + k - 1, y + 1, (214, 176, 90) if k == 1 else (170, 120, 50))
    f.save(ASSETS / "textures/block/griffin_nest_feather.png")
    box = lambda x0, y0, z0, x1, y1, z1: {"from": [x0, y0, z0], "to": [x1, y1, z1], "faces": {
        d: {"uv": [0, 0, 16, 16] if d in ("up", "down") else [0, 16 - (y1 - y0), 16, 16], "texture": "#nest"} for d in ("down", "up", "north", "south", "west", "east")}}
    elements = [box(0, 0, 0, 16, 2, 16), box(0, 2, 0, 16, 5, 2), box(0, 2, 14, 16, 5, 16), box(0, 2, 2, 2, 5, 14), box(14, 2, 2, 16, 5, 14),
                box(1, 5, 1, 4, 6, 4), box(12, 5, 12, 15, 6, 15), box(12, 5, 1, 15, 6, 3),
                box(4, 2, 4, 12, 3, 12)]
    elements.append({"from": [3, 3.01, 3], "to": [13, 3.01, 13], "rotation": {"origin": [8, 3, 8], "axis": "y", "angle": 22.5},
                     "faces": {"up": {"uv": [0, 0, 16, 16], "texture": "#feather"}}})
    write_json(ASSETS / "models/block/griffin_nest.json", {"textures": {"nest": "skyrpg_mounts:block/griffin_nest", "feather": "skyrpg_mounts:block/griffin_nest_feather",
                                                                       "particle": "skyrpg_mounts:block/griffin_nest"}, "elements": elements})
    write_json(ASSETS / "models/item/griffin_nest.json", {"parent": "skyrpg_mounts:block/griffin_nest"})
    write_json(ASSETS / "items/griffin_nest.json", {"model": {"type": "minecraft:model", "model": "skyrpg_mounts:item/griffin_nest"}})
    write_json(ASSETS / "blockstates/griffin_nest.json", {"variants": {"": {"model": "skyrpg_mounts:block/griffin_nest"}}})


def main():
    rows = M.pack(sizes=(64, 128, 256))
    print(f"packed {len(M.cubes)} cubes, {len(M.owners())} unique UV regions, {rows} rows of a {M.width}x{M.height} texture")
    TEX_DIR.mkdir(parents=True, exist_ok=True)
    for name, pal in PALS.items():
        paint(pal).save(TEX_DIR / f"griffin_{name}.png")
        if pal.glow:
            GL[0].save(TEX_DIR / f"griffin_{name}_glow.png")
            GL[1].save(TEX_DIR / f"griffin_{name}_glow2.png")
    JAVA_OUT.parent.mkdir(parents=True, exist_ok=True)
    JAVA_OUT.write_text(M.java("com.skyrpg.mounts.client.mob", "GriffinLayout", "tools/art/generate_griffin.py", "The griffin"),
                        encoding="utf-8", newline="\n")
    spawn_egg()
    whistles()
    nest_block()
    print("wrote textures, items, the nest block and", JAVA_OUT.name)
    if "--preview" in sys.argv:
        preview(sys.argv[sys.argv.index("--preview") + 1])


if __name__ == "__main__":
    main()
