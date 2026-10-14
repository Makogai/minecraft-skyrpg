#!/usr/bin/env python3
"""Draws the combat mod's items and writes their models.

    python tools/art/generate_combat.py

Writes, under combat/src/main/resources/assets/skyrpg_combat/: textures/item/<id>.png, models/item/<id>.json and
items/<id>.json for every item below, and textures/entity/training_dummy.png, the 64x64 player skin (classic wide-arm
layout) the training dummy's Mannequin wears (profile texture skyrpg_combat:entity/training_dummy). Uses
generate_items.py's mask renderer and palettes (shading, outlines).
"""
import json
import math
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import generate_items as gi  # noqa: E402

ROOT = Path(__file__).resolve().parents[2]
ASSETS = ROOT / "combat" / "src" / "main" / "resources" / "assets" / "skyrpg_combat"

BONE = gi.Material(["6a604c", "a09478", "cfc4a4", "eae2c8", "fffaea"])
CORD = gi.Material(["5a3a08", "9a6814", "d0a024", "f0d050", "fff4a8"], texture=gi.sheen)
GEM = gi.Material(["5a0a14", "9a1426", "d02a3a", "f06a70", "ffc0c0"], base=3)
BRONZE = gi.Material(["4a2a10", "7a4a1c", "b0742e", "d8a050", "f4d08a"], texture=gi.sheen)
GOLD = gi.Material(["5e3a08", "9a6814", "d0a024", "f0d050", "fff4a8"], texture=gi.sheen)
STEEL = gi.Material(["3a3d44", "5e636e", "878d99", "b4bac4", "e2e6ec"], texture=gi.sheen)
RIBBON = gi.Material(["4a0a10", "7a141e", "b02030", "d84a50", "f08a8a"])
WAX = gi.Material(["4a0a10", "7a141e", "a81e2c", "d0404a", "f07a7a"], base=2)
DREAD = gi.Material(["1a1414", "2e2424", "4a3a38", "6e5a54", "9a827a"])
BLOOD = gi.Material(["4a0606", "7a0c0c", "b81a1a", "e84040", "ff8a8a"], base=3)
VOID = gi.Material(["0c0612", "1a0c26", "2c1440", "44205e", "62307e"])
NIGHT = gi.Material(["4a0a5a", "7a1a8e", "b03ac8", "e070f0", "ffc8ff"], base=3)

ITEMS = {
    # A curved fang on a gold cord with a red gem: taken from an Elite.
    "elite_trophy": ({"B": BONE, "C": CORD, "G": GEM}, [
        "................",
        "..CC........CC..",
        "...CC......CC...",
        "....CC....CC....",
        ".....CCGGCC.....",
        "......CGGC......",
        "......BBBB......",
        ".....BBBBB......",
        ".....BBBBB......",
        "....BBBBB.......",
        "....BBBB........",
        "....BBBB........",
        "....BBB.........",
        ".....BB.........",
        "......B.........",
        "................",
    ]),
    # A bronze medal on a red ribbon, a sword on its face: the Combat Master's currency.
    "valor_mark": ({"R": RIBBON, "M": BRONZE, "S": STEEL}, [
        "................",
        "....RR....RR....",
        ".....RR..RR.....",
        "......RRRR......",
        ".......RR.......",
        ".....MMMMMM.....",
        "....MMMMSMMM....",
        "...MMMMMSMMMM...",
        "...MMMMMSMMMM...",
        "...MMMSSSSSMM...",
        "...MMMMMSMMMM...",
        "...MMMMMSMMMM...",
        "....MMMMMMMM....",
        ".....MMMMMM.....",
        "................",
        "................",
    ]),
    # A gold seal pressed into red wax, with a star: nine Valor Marks.
    "valor_seal": ({"G": GOLD, "W": WAX, "S": STEEL}, [
        "................",
        "......WWWW......",
        "....WWWWWWWW....",
        "...WWGGGGGGWW...",
        "..WWGGGGGGGGWW..",
        "..WGGGGSGGGGGW..",
        ".WWGGGGSGGGGGWW.",
        ".WGGGSSSSSGGGGW.",
        ".WGGGGSSSGGGGGW.",
        ".WWGGGSGSGGGGWW.",
        "..WGGGGGGGGGGW..",
        "..WWGGGGGGGGWW..",
        "...WWGGGGGGWW...",
        "....WWWWWWWW.W..",
        "......WWWW...WW.",
        "...............W",
    ]),
    # A long, dark, hooked fang with a bloody tip.
    "dread_fang": ({"D": DREAD, "B": BLOOD}, [
        "................",
        "..........DDDD..",
        ".........DDDDDD.",
        "........DDDDDDD.",
        "........DDDDDD..",
        ".......DDDDDD...",
        ".......DDDDD....",
        "......DDDDD.....",
        "......DDDD......",
        ".....DDDD.......",
        ".....DDD........",
        "....DDD.........",
        "....DD..........",
        "...BD...........",
        "...B............",
        "..B.............",
    ]),
    # A swirling violet orb wrapped in black: the rarest combat drop.
    "nightmare_core": ({"V": VOID, "N": NIGHT}, [
        "................",
        ".....VVVVVV.....",
        "...VVVVVVVVVV...",
        "..VVVNNNNVVVVV..",
        "..VVNNNNNNNVVV..",
        ".VVNNNVVVNNNVVV.",
        ".VVNNVVNNVNNVVV.",
        ".VVNNVNNNNVNNVV.",
        ".VVNNVNNNNVNNVV.",
        ".VVVNNVNNVVNNVV.",
        ".VVVNNNVVVNNVVV.",
        "..VVVNNNNNNNVV..",
        "..VVVVNNNNVVVV..",
        "...VVVVVVVVVV...",
        ".....VVVVVV.....",
        "................",
    ]),
}


BURLAP = gi.Material(["6a5028", "9a7a3c", "c4a25a", "e0c880", "f4e4b0"], texture=gi.weave)
TARGET_RED = gi.Material(["4a0a10", "7a141e", "b02030", "d84a50", "f08a8a"])
TARGET_WHITE = gi.Material(["8a8a90", "b4b4bc", "d4d4dc", "ececf2", "ffffff"])
POST = gi.Material(["3e2614", "5e3a1e", "82542c", "a8723e", "c99a5e"])
ITEMS["training_dummy"] = ({"B": BURLAP, "S": POST.shifted(base=0), "R": TARGET_RED, "W": TARGET_WHITE, "C": CORD, "K": POST}, [
    "................",
    ".....BBBBBB.....",
    ".....BSBBSB.....",
    ".....BBBBBB.....",
    "......BBBB......",
    "..CBBBBBBBBBBC..",
    "..CBBBBBBBBBBC..",
    ".....BRRRRB.....",
    ".....RWWWWR.....",
    ".....RWRRWR.....",
    ".....RWWWWR.....",
    ".....BRRRRB.....",
    ".......KK.......",
    ".......KK.......",
    "....KKKKKKKK....",
    "................",
])


def _noise(*args):
    n = 0
    for a in args:
        n = (n * 1000003 + int(a) * 2654435761 + 97) & 0xFFFFFFFF
    n ^= n >> 15
    return (n * 2246822519) & 0xFFFFFFFF


def skin():
    """The training dummy's 64x64 player skin: a burlap and straw body, a stitched X-eyed face, a red and white target on
    the chest, rope ties at the wrists, and wooden posts for legs. Classic (wide-arm) layout."""
    img = gi.Image.new("RGBA", (64, 64), (0, 0, 0, 0))

    def burlap(x, y, tone=0):
        weave = -22 if (x + (y % 2) * 2) % 4 == 3 else 0
        fleck = -14 if _noise(x, y, 5) % 9 == 0 else 0
        base = (196, 160, 92)
        return tuple(max(0, min(255, c + weave + fleck + tone)) for c in base)

    def wood(x, y, tone=0):
        grain = -20 if (x * 3 + _noise(x, 7) % 3) % 5 == 0 else 0
        knot = -30 if _noise(x, y // 3, 9) % 11 == 0 else 0
        base = (130, 88, 48)
        return tuple(max(0, min(255, c + grain + knot + tone)) for c in base)

    def rope(x, y):
        return (214, 178, 100) if (x + y) % 3 else (150, 112, 56)

    def paint_part(box, fn):
        for face, (x0, y0, w, h) in box.items():
            for v in range(h):
                for u in range(w):
                    tone = 10 if face == "top" else -18 if face in ("bottom", "left", "back") else 0
                    img.putpixel((x0 + u, y0 + v), fn(face, u, v, w, h, x0 + u, y0 + v, tone) + (255,))

    stitch = (74, 46, 22)

    def head(face, u, v, w, h, x, y, tone):
        c = burlap(x, y, tone)
        if face == "top" and _noise(x, y, 3) % 3 == 0:
            c = (226, 196, 96)  # a tuft of straw
        if face == "front":
            if (u, v) in ((1, 3), (3, 3), (2, 4), (1, 5), (3, 5), (4, 3), (6, 3), (5, 4), (4, 5), (6, 5)):
                c = stitch  # a stitched X for each eye
            if v == 6 and 2 <= u <= 5:
                c = stitch if u % 2 == 0 else c
        if face in ("front", "left", "right", "back") and u in (0, w - 1) and v % 2 == 0:
            c = tuple(int(k * 0.8) for k in c)  # stitched seam
        return c

    def body(face, u, v, w, h, x, y, tone):
        c = burlap(x, y, tone)
        if face == "front":
            d = ((u - 3.5) ** 2 + (v - 5.5) ** 2) ** 0.5
            if d < 4.6:
                c = ((200, 40, 48) if d < 1.3 else (240, 238, 232) if d < 2.5 else (200, 40, 48) if d < 3.6 else (240, 238, 232))
                c = tuple(max(0, k + tone) for k in c)
        if v == h - 1 or (face == "front" and v == 0):
            c = tuple(int(k * 0.85) for k in c)
        return c

    def arm(face, u, v, w, h, x, y, tone):
        if face == "top":
            return burlap(x, y, tone)
        if face == "bottom":
            return (226, 196, 96) if _noise(x, y, 1) % 2 else burlap(x, y, tone)
        if v in (9, 10):
            return tuple(max(0, k + tone) for k in rope(u + (face == "front") * 2, v))
        if v == 11:
            return (226, 196, 96) if _noise(x, y, 2) % 3 else burlap(x, y, tone)  # straw poking out
        return burlap(x, y, tone)

    def leg(face, u, v, w, h, x, y, tone):
        c = wood(x if face in ("front", "back") else x + 3, y, tone)
        if face == "bottom":
            return (92, 60, 32)
        return c

    from generate_items import ARM, BODY, HEAD, LEG, box
    paint_part(HEAD, head)
    paint_part(BODY, body)
    paint_part(ARM, arm)
    paint_part(box(32, 48, 4, 12, 4), arm)
    paint_part(LEG, leg)
    paint_part(box(16, 48, 4, 12, 4), leg)
    return img


# ---------------------------------------------------------------- slayer bosses: entity textures
# Each keeps the vanilla UV layout of its base mob exactly (the vanilla model draws it). Painted per box face with
# a colour function, then shaded by face and a little noise.
FACE_LIGHT = {"top": 1.1, "bottom": 0.7, "front": 1.0, "right": 0.92, "left": 0.92, "back": 0.85}


def mix(a, b, t):
    return tuple(round(a[i] + (b[i] - a[i]) * t) for i in range(3))


def paint_box(img, rects, fn, light=True):
    """Paints each face rect with fn(face, u, v, w, h, x, y) -> (r, g, b) or None (leave alone)."""
    for face, (x0, y0, w, h) in rects.items():
        for v in range(h):
            for u in range(w):
                c = fn(face, u, v, w, h, x0 + u, y0 + v)
                if c is None:
                    continue
                if light:
                    f = FACE_LIGHT[face] * (1 + ((_noise(x0 + u, y0 + v, 9) % 100) - 50) / 50 * 0.06)
                    c = tuple(max(0, min(255, round(k * f))) for k in c)
                img.putpixel((x0 + u, y0 + v), tuple(c) + (255,))


def mottle(base, x, y, salt=1, dark=14, light=10):
    n = _noise(x, y, salt) % 9
    return tuple(max(0, min(255, k - dark)) for k in base) if n == 0 else \
        tuple(max(0, min(255, k + light)) for k in base) if n == 1 else base


def cracks(face_w, face_h, seed, count=2, length=7):
    """Cells of a few wandering cracks across a face, as a set of (u, v)."""
    cells = set()
    for k in range(count):
        u = _noise(seed, k, 1) % face_w
        v = _noise(seed, k, 2) % max(1, face_h // 2)
        for step in range(length):
            cells.add((u, v))
            u = max(0, min(face_w - 1, u + (_noise(seed, k, step + 5) % 3) - 1))
            v += 1
            if v >= face_h:
                break
    return cells


# ---- Grave Tyrant: zombie layout (64x64)
def grave_tyrant():
    img = gi.Image.new("RGBA", (64, 64), (0, 0, 0, 0))
    flesh, flesh_dark = (92, 116, 80), (66, 84, 58)
    iron, iron_dark, gold = (96, 98, 108), (58, 60, 70), (190, 150, 50)
    boil, boil_hi = (112, 52, 138), (160, 96, 180)
    cloak, cloak_dark = (112, 26, 44), (74, 16, 30)
    rag, rag_dark = (66, 38, 84), (44, 26, 60)
    bone = (206, 196, 164)
    fur = (156, 156, 150)

    def head(face, u, v, w, h, x, y):
        c = mottle(flesh, x, y)
        if face == "top":
            return iron if u in (0, 7) or v in (0, 7) else (92, 20, 38) if (u + v) % 3 else cloak_dark
        crown = v < 3 and face != "bottom"
        if crown:
            c = iron_dark if v == 2 else iron
            if v == 1 and u in (1, 4, 6):
                c = gold  # rivets
            if (face, u, v) in (("front", 3, 0), ("front", 3, 1), ("front", 4, 2), ("right", 2, 0), ("right", 3, 1), ("back", 5, 0),
                                ("back", 5, 1), ("back", 4, 2), ("left", 4, 0), ("left", 4, 1)):
                c = (22, 22, 28)  # cracks
            return c
        if face == "front":
            if (u, v) in ((1, 3), (2, 3), (1, 4), (2, 4), (5, 3), (6, 3), (5, 4), (6, 4)):
                c = (24, 16, 20)
            if (u, v) in ((2, 4), (5, 4)):
                c = (214, 236, 96)
            if (u, v) in ((3, 5), (4, 5)):
                c = flesh_dark
            if v == 6 and 1 <= u <= 6:
                c = (222, 214, 172) if u % 2 == 0 else (34, 16, 20)
            if v == 7 and 2 <= u <= 5:
                c = (34, 16, 20)
            if (u, v) in ((0, 5), (0, 6)):
                c = bone
            if (u, v) in ((6, 5), (7, 5), (7, 6), (6, 6)):
                c = boil_hi if (u, v) == (7, 5) else boil
        if face == "back" and v in (3, 4, 5, 6) and _noise(x, y, 3) % 3 == 0:
            c = (150, 150, 146)  # lank grey hair
        if face in ("left", "right") and (u, v) in ((2, 5), (3, 5)):
            c = boil
        return c

    def body(face, u, v, w, h, x, y):
        c = mottle(flesh_dark, x, y)
        if face in ("top", "bottom"):
            return fur if face == "top" else rag_dark
        if v < 2:
            return mottle(fur, x, y, 4, 22, 14)
        if v == 9:
            return gold if face == "front" and u in (3, 4) else iron_dark
        if face == "back":
            c = cloak if (u % 3) else cloak_dark
            if v >= 8 and _noise(x, y, 5) % 3 == 0:
                c = flesh_dark
            if (u, v) in ((3, 4), (4, 4), (3, 5), (4, 5), (2, 5), (5, 5)):
                c = (150, 118, 50)
            return c
        if face == "front":
            if u in (0, 1, 6, 7) or v >= 10:
                c = rag if (_noise(x, y, 6) % 5) else flesh
                if v >= 10 and _noise(x, y, 7) % 3 == 0:
                    c = rag_dark
            elif v in (3, 5, 7) and 2 <= u <= 5:
                c = bone
            else:
                c = mottle(flesh_dark, x, y, 8)
            if (u, v) in ((1, 6), (2, 6), (1, 7), (2, 7), (5, 3), (6, 3), (5, 4), (6, 4)):
                c = boil_hi if (u, v) in ((1, 6), (5, 3)) else boil
            return c
        # sides: cloak at the back half, rags at the front half
        if u >= 2:
            return cloak if v < 10 else cloak_dark
        return rag if v < 10 else rag_dark

    def arm(face, u, v, w, h, x, y):
        if face == "top":
            return rag
        if v < 4:
            c = rag if v < 3 or _noise(x, y, 11) % 2 else flesh
            return rag_dark if v == 3 and _noise(x, y, 12) % 2 else c
        if v >= 10:
            return bone if (u + v) % 2 else (176, 166, 136)
        c = mottle(flesh, x, y, 13)
        if face == "front" and (u, v) in ((1, 6), (2, 6), (1, 7)):
            c = boil_hi if (u, v) == (1, 6) else boil
        if face == "right" and (u, v) in ((1, 5), (2, 5)):
            c = bone
        return c

    def leg(face, u, v, w, h, x, y):
        if face == "bottom":
            return (14, 14, 18)
        if face == "top":
            return rag_dark
        if v >= 9:
            return (14, 14, 18) if v == 11 else (26, 24, 30)
        c = rag if _noise(x, y, 14) % 4 else rag_dark
        if face == "front" and (u, v) in ((1, 4), (2, 4), (1, 5)):
            c = flesh
        if v == 8 and _noise(x, y, 15) % 2:
            c = flesh_dark
        return c

    paint_box(img, gi.box(0, 0, 8, 8, 8), head)
    paint_box(img, gi.box(16, 16, 8, 12, 4), body)
    paint_box(img, gi.box(40, 16, 4, 12, 4), arm)
    paint_box(img, gi.box(0, 16, 4, 12, 4), leg)
    paint_box(img, gi.box(32, 48, 4, 12, 4), arm)  # the left limbs (drawn here in case the model doesn't mirror)
    paint_box(img, gi.box(16, 48, 4, 12, 4), leg)
    return img


# ---- Broodmother: spider layout (64x32)
def broodmother():
    img = gi.Image.new("RGBA", (64, 32), (0, 0, 0, 0))
    black, red, red_dark = (24, 14, 18), (144, 24, 30), (90, 14, 22)
    bone = (226, 216, 188)
    amber, amber_hi = (255, 176, 30), (255, 238, 140)

    def carapace(x, y):
        n = _noise(x, y, 21) % 6
        return red if n == 0 else red_dark if n == 1 else black

    def head(face, u, v, w, h, x, y):
        c = carapace(x, y)
        if face == "front":
            eyes = {(2, 3): amber_hi, (5, 3): amber_hi, (3, 3): amber, (4, 3): amber, (2, 4): amber, (3, 4): amber, (4, 4): amber,
                    (5, 4): amber, (1, 2): amber, (6, 2): amber, (3, 1): amber, (4, 1): amber, (0, 4): amber, (7, 4): amber,
                    (1, 5): amber, (6, 5): amber}
            if (u, v) in eyes:
                return eyes[(u, v)]
            if v == 6 and 1 <= u <= 6:
                return red
            if v == 7 and u in (2, 5):
                return bone
            if v == 6 and u in (2, 5):
                return bone
        if face == "top" and u in (3, 4):
            return red
        return c

    def thorax(face, u, v, w, h, x, y):
        c = carapace(x, y)
        return red if (face in ("top", "back") and (u + v) % 4 == 0) else c

    def abdomen(face, u, v, w, h, x, y):
        c = carapace(x, y)
        if face == "top":
            if u in (0, 1, 8, 9):
                c = red if v % 3 else red_dark
            spine = u in (4, 5) and 3 <= v <= 11
            ribs = v in (4, 6, 8, 10) and 2 <= u <= 7 and abs(u - 4.5) >= 1.5 - (v - 4) * 0.0
            skull = 0 <= v <= 2 and 3 <= u <= 6
            if spine or ribs:
                c = bone
            if skull:
                c = bone if not ((v == 1 and u in (3, 6)) or (v == 2 and u in (4, 5))) else (30, 10, 14)
            return c
        if face in ("right", "left"):
            return red if (u + v) % 4 in (0, 1) else c
        if face == "back":
            return (200, 60, 40) if v in (3, 4) and u in (4, 5) else c
        if face == "bottom":
            return mix(c, (170, 60, 60), 0.25)
        return red if v == 0 or (u in (0, 9)) else c

    def leg(face, u, v, w, h, x, y):
        if face in ("right", "left"):
            return black
        if u % 5 == 4:
            return red
        if u == 15:
            return bone
        return black if (u + v) % 3 else (40, 26, 30)

    paint_box(img, gi.box(32, 4, 8, 8, 8), head)
    paint_box(img, gi.box(0, 0, 6, 6, 6), thorax)
    paint_box(img, gi.box(0, 12, 10, 8, 12), abdomen)
    paint_box(img, gi.box(18, 0, 16, 2, 2), leg)
    return img


# ---- Bone Marshal: skeleton layout (64x32)
def bone_marshal():
    img = gi.Image.new("RGBA", (64, 32), (0, 0, 0, 0))
    bone, bone_dark, bone_light = (206, 190, 138), (146, 126, 82), (232, 218, 172)
    steel, steel_dark, gold = (120, 124, 136), (78, 82, 94), (216, 172, 40)
    red, red_dark = (156, 26, 34), (104, 16, 24)
    gap = (38, 32, 24)

    def head(face, u, v, w, h, x, y):
        if face == "top":
            return red if u in (3, 4) else steel
        c = mottle(bone, x, y, 31, 22, 14)
        if face != "bottom" and v < 4:
            c = steel_dark if (face, u, v) in (("front", 2, 1), ("front", 3, 1), ("right", 5, 1), ("left", 2, 2), ("back", 5, 2)) else steel
            if v == 3:
                c = gold
            if face == "front" and u in (3, 4) and v >= 3:
                c = gold  # nasal guard
            if face == "back" and u in (3, 4):
                c = red  # a horsehair crest
            return c
        if face == "front":
            if (u, v) in ((1, 4), (2, 4), (1, 5), (2, 5), (5, 4), (6, 4), (5, 5), (6, 5)):
                c = (24, 18, 14)
            if (u, v) in ((2, 5), (5, 5)):
                c = (236, 48, 34)
            if (u, v) in ((3, 6), (4, 6)):
                c = gap
            if v == 7 and 1 <= u <= 6:
                c = bone_light if u % 2 else gap
        if face == "back" and u in (3, 4) and v in (4, 5, 6):
            c = red
        return c

    def body(face, u, v, w, h, x, y):
        if face in ("top", "bottom"):
            return bone
        if face == "front" or face == "back":
            if v >= 9:
                c = bone if (u, v) != (3, 10) else gap
            elif v % 2 == 0:
                c = bone_light if u in (3, 4) else bone
            else:
                c = bone if u in (3, 4) else gap
            if face == "front":
                centre = 0.6 + v * 0.7
                if abs(u - centre) < 1.1 and v <= 10:
                    c = red if abs(u - centre) < 0.7 else red_dark  # the sash, shoulder to hip
                if (u, v) in ((6, 9), (7, 9), (6, 10)):
                    c = gold  # its knot
                if (u, v) in ((6, 11), (7, 11)):
                    c = red
            elif v in (8, 9):
                c = red
            return c
        if v in (8, 9) and face == "left":
            return red
        return bone if v % 2 == 0 else bone_dark

    def arm(face, u, v, w, h, x, y):
        if v < 4:
            if face == "top":
                return steel
            return gold if v == 3 else (steel_dark if (u + v) % 4 == 0 else steel)
        return bone if v % 5 else bone_dark

    def leg(face, u, v, w, h, x, y):
        return bone_dark if v in (5, 6) else (bone_light if u == 0 and v % 2 else bone)

    paint_box(img, gi.box(0, 0, 8, 8, 8), head)
    paint_box(img, gi.box(16, 16, 8, 12, 4), body)
    paint_box(img, gi.box(40, 16, 2, 12, 2), arm)
    paint_box(img, gi.box(0, 16, 2, 12, 2), leg)
    return img


# ---- Void Stalker: enderman layout (64x32)
def void_stalker():
    img = gi.Image.new("RGBA", (64, 32), (0, 0, 0, 0))
    indigo, indigo_dark, indigo_light = (30, 22, 70), (16, 12, 42), (50, 38, 104)
    cyan, cyan_glow, white = (72, 236, 255), (30, 120, 170), (230, 252, 255)

    def base(face, u, v, w, h, x, y, salt):
        c = indigo_light if _noise(x, y, salt) % 8 == 0 else indigo_dark if _noise(x, y, salt + 1) % 5 == 0 else indigo
        s = _noise(x, y, salt + 2) % 41
        if s == 0:
            c = white
        elif s == 1:
            c = cyan
        return c

    def with_cracks(face, u, v, w, h, x, y, salt, count=2, length=7):
        c = base(face, u, v, w, h, x, y, salt)
        cr = cracks(w, h, _noise(x - u, y - v, salt), count, length)
        if (u, v) in cr:
            return cyan
        if any((u + du, v + dv) in cr for du, dv in ((1, 0), (-1, 0), (0, 1), (0, -1))):
            return mix(c, cyan_glow, 0.55)
        return c

    def head(face, u, v, w, h, x, y):
        c = with_cracks(face, u, v, w, h, x, y, 41, 1, 5)
        if face == "front":
            if v == 3:
                return (8, 6, 22)  # a heavy brow
            left = {(1, 4): cyan, (2, 4): white, (3, 4): cyan, (2, 5): cyan, (1, 5): cyan_glow}
            right = {(4, 4): cyan, (5, 4): white, (6, 4): cyan, (5, 5): cyan, (6, 5): cyan_glow}
            if (u, v) in left or (u, v) in right:
                return {**left, **right}[(u, v)]
            if (u, v) in ((3, 1), (4, 2), (4, 1), (3, 2)):
                return cyan if (u, v) in ((3, 1), (4, 2)) else cyan_glow  # a rune on the brow
            if v == 7 and u % 2 == 0 and 1 <= u <= 6:
                return cyan_glow
        return c

    def jaw(face, u, v, w, h, x, y):
        c = base(face, u, v, w, h, x, y, 51)
        if face == "front":
            if v == 0 and u % 2 == 0:
                return cyan  # glowing fangs
            if 1 <= v <= 4 and 1 <= u <= 6:
                return (8, 4, 20)
        return c

    def body(face, u, v, w, h, x, y):
        return with_cracks(face, u, v, w, h, x, y, 61, 2, 9)

    def limb(face, u, v, w, h, x, y):
        c = with_cracks(face, u, v, w, h, x, y, 71, 1, 8)
        if v >= 27 and face in ("front", "back", "right", "left"):
            return mix(c, indigo_light, 0.4)
        return c

    paint_box(img, gi.box(0, 0, 8, 8, 8), head)
    paint_box(img, gi.box(0, 16, 8, 8, 8), jaw)
    paint_box(img, gi.box(32, 16, 8, 12, 4), body)
    paint_box(img, gi.box(56, 0, 2, 30, 2), limb)
    return img


BOSS_TEXTURES = {"grave_tyrant": grave_tyrant, "broodmother": broodmother, "bone_marshal": bone_marshal, "void_stalker": void_stalker}


# ---------------------------------------------------------------- slayer item icons
ASH = gi.Material(["3a4636", "586a52", "7a9072", "9ab090", "c4d8bc"])
GLASS = gi.Material(["5a7a88", "8ab0c0", "b8dce8", "dcf2fa", "ffffff"], texture=gi.flat)
CORK = gi.Material(["3e2614", "5e3a1e", "82542c", "a8723e", "c99a5e"])
SILK = gi.Material(["a07884", "d0a8b0", "ecd0d4", "f8e8ea", "ffffff"])
SILK_RED = gi.Material(["4a0a10", "7a141e", "a81e2c", "d0404a", "f07a7a"])
MARROW_BONE = gi.Material(["6a604c", "a09478", "cfc4a4", "eae2c8", "fffaea"])
MARROW = gi.Material(["4a0606", "7a0c0c", "b81a1a", "e84040", "ff8a8a"], base=3)
VOID_CRYSTAL = gi.Material(["06020e", "120a24", "221438", "3a2460", "56388a"])
VOID_GLOW = gi.Material(["0a5a6a", "10889c", "30c8e0", "80f0ff", "e0ffff"], base=3)
HEART = gi.Material(["2a0a3a", "4a1462", "7a2a94", "a458c0", "d8a0e8"])
TOXIN = gi.Material(["0a3a10", "16661c", "2ea030", "74e050", "d4ff9a"], base=3)
SAC = gi.Material(["0e3a14", "1a6a24", "34a838", "6ee060", "baff90"], base=3)
SIGIL_GOLD = gi.Material(["5e3a08", "9a6814", "d0a024", "f0d050", "fff4a8"], texture=gi.sheen)
SIGIL_RED = gi.Material(["3a0610", "6a0e1a", "a01c2a", "d03a44", "f07a7a"])
SKULL = gi.Material(["6a604c", "a09478", "cfc4a4", "eae2c8", "fffaea"], texture=gi.flat)
NULL_BLACK = gi.Material(["000000", "04040a", "0a0a14", "14142a", "24244a"])


def mask_from(g):
    return ["".join(r) for r in g]


def blank():
    return [["."] * 16 for _ in range(16)]


def put_all(g, cells, ch):
    for x, y in cells:
        g[y][x] = ch


ITEMS["tyrants_ash"] = ({"K": CORK, "G": GLASS, "g": GLASS.shifted(base=4), "A": ASH, "a": ASH.shifted(base=1), "W": GLASS.shifted(base=4)}, [
    "................",
    "......KKKK......",
    "......KKKK......",
    "......GggG......",
    "......GggG......",
    ".....GggggG.....",
    "....GgWggggG....",
    "....GggggggG....",
    "....GaAAaAAG....",
    "....GAAaAAAG....",
    "....GAaAAaAG....",
    "....GAAAAAAG....",
    ".....GGGGGG.....",
    "................",
    "................",
    "................",
])

g = blank()
for y in range(2, 14):
    half = 4.6 * math.sqrt(max(0.0, 1 - ((y - 8) / 6.2) ** 2))
    for x in range(16):
        if abs(x - 7.5) <= half:
            g[y][x] = "W" if (x + y) % 3 else "S"
for y in (7, 8):
    for x in range(16):
        if g[y][x] != ".":
            g[y][x] = "R"
put_all(g, ((5, 1), (8, 1), (10, 1), (6, 14), (9, 14), (11, 14)), "S")
put_all(g, ((7, 0), (9, 0)), "W")
ITEMS["brood_silk"] = ({"W": SILK.shifted(base=3), "S": SILK, "R": SILK_RED}, mask_from(g))

g = [list(r) for r in [
    "................",
    "...........BB...",
    "..........BBBB..",
    ".........BBMMB..",
    "........BBMMMB..",
    ".......BBMMMBB..",
    "......BBMMMBB...",
    ".....BBMMMBB....",
    "....BBMMMBB.....",
    "...BBMMMBB......",
    "..BBMMBBB.......",
    "..BBMBBB........",
    "...BBBB.........",
    "....BB..........",
    "................",
    "................"]]
for x, y in ((7, 6), (5, 8), (3, 10)):
    g[y][x - 1] = "."
ITEMS["marrow_shard"] = ({"B": MARROW_BONE, "M": MARROW}, mask_from(g))

ITEMS["void_fragment"] = ({"V": VOID_CRYSTAL, "C": VOID_GLOW, "g": VOID_GLOW.shifted(base=2)}, [
    "................",
    ".......VV.......",
    "......VVVV......",
    ".....VVCCVV.....",
    "..g..VVCCVV.....",
    "....VVVCCVVV....",
    "....VVCCCCVV..g.",
    "....VVCCCCVV....",
    "....VVVCCVVV....",
    ".g...VVCCVV.....",
    ".....VVVCVV..g..",
    "......VVVV......",
    "......VVV.......",
    ".......VV.......",
    "................",
    "................",
])

ITEMS["plague_heart"] = ({"P": HEART, "L": HEART.shifted(base=4), "G": TOXIN, "T": TOXIN.shifted(base=1)}, [
    "................",
    "....TT....TT....",
    "...PPPP..PPPP...",
    "..PPLPPPPPPPPP..",
    ".PPLLPPPGPPPPPP.",
    ".PPLPPPPGGPPPPP.",
    ".PPPPPPPPGPPPPP.",
    "..PPPPGPPPPPPP..",
    "..PPPPPGPPPPPP..",
    "...PPPPPPPPPP...",
    "....PPPPPPPP....",
    ".....PPPPPP.....",
    "......PPPP......",
    ".......PP.......",
    "................",
    "................",
])

g = blank()
for y in range(16):
    for x in range(16):
        d = math.hypot((x - 7.5) / 1.0, (y - 8.5) / 1.12)
        if d <= 5.4:
            g[y][x] = "A" if d < 1.8 else "L" if d < 3.4 else "G" if d < 4.6 else "D"
put_all(g, ((7, 1), (8, 1), (7, 2), (8, 2), (7, 3), (8, 3)), "D")
put_all(g, ((7, 14), (7, 15)), "G")
put_all(g, ((10, 6), (11, 7)), "W")
ITEMS["venom_gland"] = ({"A": SAC.shifted(base=4), "L": SAC.shifted(base=3), "G": SAC, "D": SAC.shifted(base=0), "W": SAC.shifted(base=4)},
                        mask_from(g))

g = blank()
for y in range(16):
    for x in range(16):
        d = math.hypot(x - 7.5, y - 9.0)
        if d <= 5.6:
            g[y][x] = "U" if d > 4.6 else "D" if d > 3.9 else "R"
put_all(g, ((4, 0), (5, 0), (6, 0), (4, 1), (5, 1), (6, 1), (4, 2), (5, 2), (9, 0), (10, 0), (11, 0), (9, 1), (10, 1), (11, 1), (10, 2),
            (11, 2), (5, 3), (6, 3), (9, 3), (10, 3)), "B")
skull = ["..WWWW..", ".WWWWWW.", ".WKWWKW.", ".WWKKWW.", "..WWWW..", "..WKWK.."]
for dy, row in enumerate(skull):
    for dx, ch in enumerate(row):
        if ch != ".":
            g[6 + dy][4 + dx] = ch
ITEMS["marshals_sigil"] = ({"U": SIGIL_GOLD, "D": SIGIL_GOLD.shifted(base=1, texture=gi.flat), "R": SIGIL_RED, "B": SIGIL_RED.shifted(base=1),
                            "W": SKULL, "K": SKULL.shifted(base=0)}, mask_from(g))

g = blank()
t = [["."] * 16 for _ in range(16)]
for y in range(16):
    for x in range(16):
        d = math.hypot(x - 7.5, y - 8.0)
        if d <= 5.4:
            g[y][x] = "N"
            t[y][x] = str(max(0, min(3, 3 - int(math.hypot(x - 5.2, y - 5.6) / 3.6))))
# a tilted ring of cyan round the orb: the near half in front, the far half hidden behind it
for k in range(0, 360, 6):
    a = math.radians(k)
    ex, ey = 7.0 * math.cos(a), 2.1 * math.sin(a)
    rx, ry = ex * math.cos(-0.35) - ey * math.sin(-0.35), ex * math.sin(-0.35) + ey * math.cos(-0.35)
    px, py = round(7.5 + rx), round(8.0 + ry)
    front = math.sin(a) > 0
    if 0 <= px < 16 and 0 <= py < 16 and (front or g[py][px] == "."):
        g[py][px] = "C"
        t[py][px] = "." if front else "2"
put_all(g, ((7, 8), (8, 8), (7, 9)), "V")
ITEMS["null_core"] = ({"N": NULL_BLACK, "C": VOID_GLOW, "V": gi.Material(["1a0a3a", "3a1a6a", "6a3aa8", "a478e8", "e0c8ff"], base=3)},
                      mask_from(g), mask_from(t))

ITEM_NAMES = {"tyrants_ash": "Tyrant's Ash", "brood_silk": "Brood Silk", "marrow_shard": "Marrow Shard", "void_fragment": "Void Fragment",
              "plague_heart": "Plague Heart", "venom_gland": "Venom Gland", "marshals_sigil": "Marshal's Sigil", "null_core": "Null Core"}


# ---------------------------------------------------------------- mob textures (entity/mob/<id>.png)
# Each uses the exact UV layout and size of the vanilla mob its renderer wraps. The renderers drop the vanilla overlay
# layers (clothing overlay, drowned outer layer, spider eyes), so everything is in the one base texture.
Image = gi.Image


def rp(r, x, y, salt, bias=1):
    """Noise-picked colour from a ramp (list of rgb), centred on index bias."""
    n = _noise(x, y, salt) % 12
    i = bias + (-1 if n == 0 else 1 if n >= 10 else 0)
    return r[max(0, min(len(r) - 1, i))]


def grain(r, x, y, salt, bias=2):
    """Vertical wood/bark grain: per-column offset plus a little noise."""
    col = _noise(x, 0, salt) % 5
    n = _noise(x, y, salt + 1) % 8
    i = bias + (-1 if col == 0 else 1 if col == 4 else 0) + (-1 if n == 0 else 1 if n == 7 else 0)
    return r[max(0, min(len(r) - 1, i))]


def fsalt(face):
    return sum(map(ord, face))


def put(img, rects, face, u, v, c, light=False):
    x0, y0, w, h = rects[face]
    if 0 <= u < w and 0 <= v < h:
        if light:
            f = FACE_LIGHT[face]
            c = tuple(min(255, round(k * f)) for k in c)
        img.putpixel((x0 + u, y0 + v), tuple(c) + (255,))


def stamp(img, rects, face, shape, u0, v0, pal, light=True, glow=None, gpal=None, flip=False):
    """Draws a char-grid shape at (u0, v0) of a face; chars not in pal are skipped. flip mirrors it horizontally."""
    x0, y0, w, h = rects[face]
    for dv, row in enumerate(shape):
        if flip:
            row = row[::-1]
        for du, ch in enumerate(row):
            if ch not in pal:
                continue
            u, v = u0 + du, v0 + dv
            if 0 <= u < w and 0 <= v < h:
                put(img, rects, face, u, v, pal[ch], light)
                if glow is not None and gpal and ch in gpal:
                    glow.putpixel((x0 + u, y0 + v), tuple(gpal[ch]) + (255,))


def hang(img, rects, face, cols, ramp, top=0, light=True):
    """Icicle/strand columns hanging from row `top`: cols is [(u, length)], ramp is light-to-dark along the length."""
    for u, ln in cols:
        for k in range(ln):
            put(img, rects, face, u, top + k, ramp[min(len(ramp) - 1, k * len(ramp) // max(1, ln))], light)


# ---- Glimmer Crawler: spider layout (64x32), plus an emissive layer of crystals and eyes
def glimmer_crawler_pair():
    img = Image.new("RGBA", (64, 32), (0, 0, 0, 0))
    glow = Image.new("RGBA", (64, 32), (0, 0, 0, 0))
    SL = [(22, 26, 36), (34, 40, 54), (48, 56, 74), (66, 76, 98), (88, 100, 126)]
    CR = {"0": (60, 44, 118), "1": (104, 86, 188), "2": (146, 188, 232), "3": (214, 244, 255)}
    CG = {"1": (130, 104, 236), "2": (160, 222, 255), "3": (238, 255, 255)}
    BIG = [".010.", "01230", "12332", "01230", ".010."]
    SM = [".1.", "123", "12."]
    TALL = [".3.", ".23", "123", "122", "011"]
    MID = [".2.", "123", "122"]

    def slate(face, u, v, w, h, x, y, salt=1):
        bias = 2
        if face == "top" and v % 4 == 3:
            bias = 1  # chitin plate seams
        if face in ("right", "front", "left", "back"):
            bias += (1 if v == 0 else 0) - (1 if v >= h - 2 else 0)
        return rp(SL, x, y, salt, bias)

    def S(rects, face, shape, u, v, **kw):
        stamp(img, rects, face, shape, u, v, CR, glow=glow, gpal=CG, **kw)

    HEAD = gi.box(32, 4, 8, 8, 8)
    NECK = gi.box(0, 0, 6, 6, 6)
    BODY = gi.box(0, 12, 10, 8, 12)
    LEG = gi.box(18, 0, 16, 2, 2)
    for rects, salt in ((HEAD, 3), (NECK, 5), (BODY, 7), (LEG, 9)):
        paint_box(img, rects, lambda f, u, v, w, h, x, y, s=salt: slate(f, u, v, w, h, x, y, s))
    for y in range(24, 32):  # the two spare columns beside the body's side faces
        for x in (44, 45):
            img.putpixel((x, y), SL[1] + (255,))
    # legs: a joint ring and little crystals
    for face in ("front", "back", "top", "bottom"):
        for u in (7, 8):
            for v in range(2):
                put(img, LEG, face, u, v, SL[1], True)
    S(LEG, "top", ["12", "23"], 3, 0)
    S(LEG, "top", ["12", "23"], 11, 0)
    S(LEG, "front", ["2", "3"], 4, 0)
    S(LEG, "front", ["1", "2"], 12, 0)
    S(LEG, "back", ["2", "3"], 11, 0)
    S(LEG, "back", ["1", "2"], 3, 0)
    # head: crystal bud, eyes, fangs
    S(HEAD, "top", SM, 4, 1)
    S(HEAD, "top", [".1", "12"], 1, 5)
    S(HEAD, "right", [".2.", "123"], 3, 0)
    S(HEAD, "left", [".2.", "123"], 3, 0)
    EYE, EYE2, VIO = (214, 255, 255), (80, 200, 235), (136, 112, 240)
    for (u, v, c) in ((1, 3, EYE2), (2, 3, EYE), (5, 3, EYE), (6, 3, EYE2), (3, 2, VIO), (4, 2, VIO), (1, 4, VIO), (6, 4, VIO)):
        put(img, HEAD, "front", u, v, c)
        glow.putpixel((HEAD["front"][0] + u, HEAD["front"][1] + v), c + (255,))
    for (u, v, c) in ((2, 6, (196, 208, 224)), (5, 6, (196, 208, 224)), (2, 7, (140, 152, 172)), (5, 7, (140, 152, 172))):
        put(img, HEAD, "front", u, v, c)
    # neck and body back crystals
    S(NECK, "top", SM, 1, 1)
    S(BODY, "top", BIG, 1, 1)
    S(BODY, "top", BIG, 4, 6)
    S(BODY, "top", SM, 6, 1)
    S(BODY, "top", [".1", "12"], 1, 8)
    S(BODY, "right", TALL, 3, 0)
    S(BODY, "right", MID, 8, 1)
    S(BODY, "left", TALL, 7, 0)
    S(BODY, "left", MID, 2, 1)
    S(BODY, "back", MID, 2, 1)
    S(BODY, "back", TALL, 6, 0)
    S(BODY, "front", MID, 6, 0)
    return img, glow


def glimmer_crawler():
    return glimmer_crawler_pair()[0]


def glimmer_crawler_glow():
    return glimmer_crawler_pair()[1]


# ---- Bog Hag: witch layout (64x128)
def bog_hag():
    img = Image.new("RGBA", (64, 128), (0, 0, 0, 0))
    SK = [(44, 58, 44), (62, 80, 58), (86, 106, 76), (110, 130, 94), (138, 156, 116)]
    HR = [(24, 32, 24), (36, 48, 34), (50, 64, 44)]
    HAT = [(16, 28, 18), (26, 44, 28), (38, 62, 40), (56, 84, 52)]
    ROBE = [(36, 30, 20), (54, 48, 32), (74, 68, 42), (96, 90, 56)]
    MS = [(58, 94, 42), (84, 120, 54), (114, 148, 74)]
    LILY = {"a": (38, 90, 42), "b": (62, 128, 58), "c": (96, 166, 78), "v": (150, 200, 110)}
    WART = (116, 92, 56)

    def head(face, u, v, w, h, x, y):
        c = rp(SK, x, y, 11, 2)
        if face == "top":
            return rp(HR, x, y, 12, 1)
        if face == "bottom":
            return rp(SK, x, y, 13, 0)
        if face == "front":
            if v == 0:
                return rp(HR, x, y, 14, 1)
            if v == 1:
                return rp(HR, x, y, 14, 1) if (u in (0, 1, 2, 5, 6, 7) or _noise(u, 1, 3) % 2) else c
            return c
        if face in ("right", "left"):
            b = u if face == "right" else 7 - u  # 0 = back edge, 7 = front edge
            hairlen = 4 + _noise(b, 2, 5) % 6
            if b <= 3 and v < hairlen + 2:
                return rp(HR, x, y, 15, 1)
            if v <= 1:
                return rp(HR, x, y, 15, 1)
            if 4 <= v <= 6 and b == 3:
                return SK[3]  # a pointed ear
            return c
        if face == "back":
            hairlen = 6 + _noise(u, 1, 7) % 4
            return rp(HR, x, y, 16, 1) if v < hairlen else rp(SK, x, y, 17, 1)
        return c

    HD = gi.box(0, 0, 8, 10, 8)
    paint_box(img, HD, head)
    BR = (30, 38, 28)
    for (u, v, c) in ((1, 3, BR), (2, 3, BR), (5, 3, BR), (6, 3, BR),
                      (1, 4, (232, 222, 100)), (2, 4, (30, 24, 10)), (5, 4, (30, 24, 10)), (6, 4, (232, 222, 100)),
                      (1, 5, SK[0]), (2, 5, SK[0]), (5, 5, SK[0]), (6, 5, SK[0]),
                      (1, 6, WART), (6, 7, WART), (6, 6, SK[3]), (1, 7, SK[1]),
                      (2, 8, (30, 22, 22)), (3, 8, (30, 22, 22)), (4, 8, (30, 22, 22)), (5, 8, (30, 22, 22)),
                      (3, 9, (216, 208, 172)), (5, 9, (216, 208, 172)), (1, 8, SK[1]), (6, 8, SK[1])):
        put(img, HD, "front", u, v, c, True)
    # nose box: long and warty
    NO = gi.box(24, 0, 2, 4, 2)
    paint_box(img, NO, lambda f, u, v, w, h, x, y: rp(SK, x, y, 21, 3 if f != "bottom" else 1))
    for (f, u, v, c) in (("front", 0, 1, WART), ("front", 1, 2, WART), ("front", 1, 3, (110, 86, 60)), ("front", 0, 3, (96, 78, 56)),
                         ("right", 0, 2, WART), ("left", 1, 1, WART)):
        put(img, NO, f, u, v, c, True)
    MO = gi.box(0, 0, 1, 1, 1)
    for f in MO:
        put(img, MO, f, 0, 0, (140, 112, 64))

    # hat: brim, body, cone, tip
    def brim(face, u, v, w, h, x, y):
        if face in ("right", "front", "left", "back"):
            if v == 1:
                return MS[0] if _noise(u, 1, 4) % 3 == 0 else MS[1] if _noise(u, 1, 5) % 3 == 1 else HAT[0]
            return rp(HAT, x, y, 31, 2)
        n = _noise(u // 2, v // 2, 33) % 6
        if face == "top":
            return MS[n % 2] if n < 2 else rp(HAT, x, y, 32, 2)
        return rp(HAT, x, y, 34, 0)

    paint_box(img, gi.box(0, 64, 10, 2, 10), brim)

    def hat2(face, u, v, w, h, x, y):
        if face in ("right", "left", "back"):
            if v == 3:
                return rp(HAT, x, y, 41, 0)  # a rotten band
            n = _noise(u // 2, v, 43 + fsalt(face)) % 7
            return MS[1] if n == 0 else rp(HAT, x, y, 42, 2)
        return rp(HAT, x, y, 44, 2 if face == "top" else 1)

    H2 = gi.box(0, 76, 7, 4, 7)
    paint_box(img, H2, hat2)
    MUSH = {"c": (200, 86, 40), "C": (240, 138, 66), "w": (244, 230, 196), "s": (224, 208, 168), "S": (172, 156, 120)}
    stamp(img, H2, "front", [".cCc...", "cCwCc..", "..s..cC", "..S..s."], 0, 0, MUSH)
    stamp(img, H2, "top", [".cc.", "cCwc", "ccCc"], 2, 2, MUSH)

    H3 = gi.box(0, 87, 4, 4, 4)
    paint_box(img, H3, lambda f, u, v, w, h, x, y: rp(HAT, x, y, 51, 3 if f == "top" else 2))
    stamp(img, H3, "front", ["pp.", "pSp", ".pp"], 0, 1, {"p": (104, 82, 50), "S": (160, 138, 96)})
    stamp(img, H3, "left", ["cC", "ws"], 1, 1, MUSH)

    H4 = gi.box(0, 95, 1, 2, 1)
    paint_box(img, H4, lambda f, u, v, w, h, x, y: HAT[3] if f == "top" else rp(HAT, x, y, 61, 1))
    put(img, H4, "front", 0, 1, MS[2])
    put(img, H4, "right", 0, 1, MS[1])
    put(img, H4, "left", 0, 1, MS[1])
    put(img, H4, "back", 0, 1, MS[2])

    # robe
    def robe(face, u, v, w, h, x, y, salt):
        fold = (u * 7 + fsalt(face)) % 5
        bias = 2 + (1 if fold == 0 else -1 if fold == 3 else 0)
        if v >= h - 4:
            bias -= 1
        c = rp(ROBE, x, y, salt, bias)
        if v >= h - 3 and _noise(x, y, salt + 8) % 4 == 0:
            c = MS[0]
        return c

    BODY = gi.box(16, 20, 8, 12, 6)
    paint_box(img, BODY, lambda f, u, v, w, h, x, y: robe(f, u, v, w, h, x, y, 71))
    for y in range(20, 26):
        for x in (38, 39):
            img.putpixel((x, y), ROBE[1] + (255,))
    JK = gi.box(0, 38, 8, 20, 6)

    def jacket(face, u, v, w, h, x, y):
        if face == "bottom":
            return None
        if face == "top":
            return ROBE[1] if u in (0, 7) else None
        if face == "front" and v <= 1 and 2 <= u <= 5:
            return rp(SK, x, y, 81, 1)  # open collar
        c = robe(face, u, v, w, h, x, y, 83)
        if v == h - 1 and _noise(x, y, 88) % 3 == 0:
            return None  # ragged hem
        if v == h - 2 and _noise(x, y, 89) % 7 == 0:
            return None
        if v == 8:
            return (112, 88, 46) if (u + (fsalt(face) % 2)) % 2 == 0 else (84, 64, 34)
        return c

    paint_box(img, JK, jacket)
    for face, shapes in (("front", ((1, 11), (5, 15))), ("back", ((2, 10), (4, 15))), ("right", ((1, 13),)), ("left", ((2, 11),))):
        for (u, v) in shapes:
            stamp(img, JK, face, [".aab.", "abcba", "abvca", ".bcb."] if face in ("front", "back") else ["aab.", "abca", ".bba"], u, v, LILY)
    for u in (3, 4):
        put(img, JK, "front", u, 8, (214, 204, 164), True)  # belt buckle: a bone toggle

    ARM = gi.box(44, 22, 4, 8, 4)

    def sleeve(face, u, v, w, h, x, y):
        if face in ("right", "front", "left", "back") and v >= 6:
            return rp(SK, x, y, 91, 2)
        if face in ("right", "front", "left", "back") and v == 5:
            return ROBE[0]
        return robe(face, u, v, w, h, x, y, 93)

    paint_box(img, ARM, sleeve)
    MID = gi.box(40, 38, 8, 4, 4)
    paint_box(img, MID, lambda f, u, v, w, h, x, y: robe(f, u, v, w, h, x, y, 95))
    for u in (1, 2, 5, 6):
        for v in (1, 2):
            put(img, MID, "front", u, v, rp(SK, u, v, 96, 2), True)
    LEGB = gi.box(0, 22, 4, 12, 4)

    def leg(face, u, v, w, h, x, y):
        if face in ("right", "front", "left", "back"):
            if v < 6:
                return rp(ROBE, x, y, 97, 0)
            if v == 6:
                return MS[1] if _noise(u, 3, 98) % 2 else MS[0]
            return rp([(24, 18, 12), (40, 30, 22), (58, 46, 32), (78, 62, 42)], x, y, 99, 1 if v < 10 else 0)
        return rp(ROBE, x, y, 97, 0)

    paint_box(img, LEGB, leg)
    return img


# ---- Frost Revenant: skeleton layout (64x32)
def frost_revenant():
    img = Image.new("RGBA", (64, 32), (0, 0, 0, 0))
    BN = [(58, 84, 112), (96, 132, 160), (142, 180, 204), (188, 218, 236), (232, 246, 255)]
    DK = (12, 22, 42)
    CL = [(46, 56, 76), (66, 80, 104), (90, 106, 130), (118, 134, 156)]
    ICE = [(240, 253, 255), (196, 238, 252), (150, 212, 238)]
    CY, CYE = (140, 244, 255), (64, 164, 212)

    def bone(face, u, v, w, h, x, y, salt=1, bias=2):
        c = rp(BN, x, y, salt, bias)
        return BN[4] if _noise(x, y, salt + 3) % 14 == 0 else c

    def cloth(x, y, salt, bias=2):
        return rp(CL, x, y, salt, bias)

    HEAD = gi.box(0, 0, 8, 8, 8)

    def head(face, u, v, w, h, x, y):
        c = bone(face, u, v, w, h, x, y, 21)
        if face == "top":
            return BN[3] if _noise(x, y, 22) % 3 else rp(BN, x, y, 22, 2)
        if face == "bottom":
            return rp(BN, x, y, 23, 0)
        if v == 0:
            return BN[4] if _noise(x, y, 24) % 3 == 0 else BN[3]  # frosted crown
        if face == "front" and v == 2:
            return rp(BN, x, y, 25, 3)
        return c

    paint_box(img, HEAD, head)
    stamp(img, HEAD, "front", ["dE", "EC"], 1, 3, {"d": DK, "E": CYE, "C": CY}, light=False)
    stamp(img, HEAD, "front", ["Ed", "CE"], 5, 3, {"d": DK, "E": CYE, "C": CY}, light=False)
    for u, v in ((3, 5), (4, 5), (3, 6), (4, 6)):
        put(img, HEAD, "front", u, v, DK if v == 5 else BN[0])
    for u in range(1, 7):
        put(img, HEAD, "front", u, 7, BN[3] if u % 2 else DK, True)  # teeth
    for u in (0, 7):
        put(img, HEAD, "front", u, 6, BN[1], True)
    hang(img, HEAD, "front", [(0, 3), (3, 2), (7, 3), (5, 1)], ICE, 1)
    hang(img, HEAD, "right", [(1, 3), (3, 2), (5, 4), (6, 2)], ICE, 1)
    hang(img, HEAD, "left", [(1, 2), (2, 4), (4, 2), (6, 3)], ICE, 1)
    hang(img, HEAD, "back", [(1, 2), (3, 4), (5, 3), (6, 2)], ICE, 1)

    BODY = gi.box(16, 16, 8, 12, 4)

    def body(face, u, v, w, h, x, y):
        c = bone(face, u, v, w, h, x, y, 31, 1)
        if face == "front":
            d = v - u
            if 1 <= d <= 3 and v <= 10 and _noise(x, y, 35) % 9 != 0:
                return ICE[1] if d == 1 and _noise(x, y, 36) % 2 else cloth(x, y, 33, 2 if d == 2 else 1)
            if v >= 9 and 1 <= u <= 6 and v < 10 + _noise(u, 4, 37) % 3:
                return cloth(x, y, 34, 1)
            rib = v in (0, 2, 4, 6)
            if rib and (v == 0 or 1 <= u <= 6):
                return rp(BN, x, y, 32, 3)
            if u in (3, 4):
                return rp(BN, x, y, 32, 2)
            if v >= 10 and 1 <= u <= 6:
                return rp(BN, x, y, 32, 1)
            return DK
        if face == "back":
            if 4 <= v <= 6:
                return ICE[1] if v == 4 and _noise(x, y, 38) % 3 == 0 else cloth(x, y, 33, 2)
            if v >= 9 and v < 10 + _noise(u, 5, 37) % 3:
                return cloth(x, y, 34, 1)
            if u in (3, 4):
                return rp(BN, x, y, 32, 3)
            if (1 <= v <= 3 or 7 <= v <= 8) and (1 <= u <= 2 or 5 <= u <= 6):
                return rp(BN, x, y, 32, 2)
            return rp(BN, x, y, 32, 0)
        if face in ("right", "left"):
            if v in (5, 6):
                return cloth(x, y, 33, 2)
            return rp(BN, x, y, 39, 2 if v % 2 == 0 else 1)
        return c

    paint_box(img, BODY, body)
    hang(img, BODY, "front", [(1, 3), (6, 4), (2, 2)], ICE, 7)

    def limb(face, u, v, w, h, x, y):
        c = bone(face, u, v, w, h, x, y, 41)
        if face in ("right", "front", "left", "back"):
            if v <= 1:
                return ICE[1] if _noise(x, y, 42) % 2 else BN[4]
            if v in (3, 4, 5) or v in (8, 9):
                if v == 3 and _noise(x, y, 43) % 4 == 0:
                    return c
                return cloth(x, y, 44, 2 if v in (4, 8) else 1)
            if v == 11:
                return ICE[1] if _noise(x, y, 45) % 2 else BN[4]
        return c

    paint_box(img, gi.box(40, 16, 2, 12, 2), limb)
    paint_box(img, gi.box(0, 16, 2, 12, 2), limb)
    return img


# ---- Ember Imp: blaze layout (64x32)
def ember_imp():
    img = Image.new("RGBA", (64, 32), (0, 0, 0, 0))
    CH = [(12, 10, 12), (24, 20, 22), (38, 32, 32), (56, 48, 46), (78, 68, 62)]
    LV = [(150, 38, 8), (222, 86, 14), (255, 150, 28), (255, 214, 76), (255, 244, 170)]

    def char(face, u, v, w, h, x, y, salt, count=3, length=6):
        c = rp(CH, x, y, salt, 1 + (1 if face == "top" else 0))
        cr = cracks(w, h, _noise(x - u, y - v, salt), count, length)
        if (u, v) in cr:
            return LV[3] if _noise(x, y, salt + 4) % 3 == 0 else LV[2]
        if any((u + du, v + dv) in cr for du, dv in ((1, 0), (-1, 0), (0, 1), (0, -1))):
            return mix(c, LV[1], 0.5)
        return c

    HEAD = gi.box(0, 0, 8, 8, 8)
    def imp_head(f, u, v, w, h, x, y):
        if f == "front" and 1 <= u <= 6 and 2 <= v <= 7:
            return rp(CH, x, y, 51, 1)  # keep the face itself clean so it reads
        return char(f, u, v, w, h, x, y, 51, 1 if f in ("top", "front") else 2, 4 if f in ("top", "front") else 6)

    paint_box(img, HEAD, imp_head)
    horn = {"T": (255, 196, 64), "H": (156, 58, 24), "h": (86, 30, 20)}
    stamp(img, HEAD, "front", ["T..", "HHh", ".hh"], 0, 0, horn, light=False)
    stamp(img, HEAD, "front", ["T..", "HHh", ".hh"], 5, 0, horn, light=False, flip=True)
    eye = {"y": (255, 226, 60), "Y": (255, 252, 180), "b": (36, 12, 10)}
    stamp(img, HEAD, "front", ["yb", "yY"], 1, 3, eye, light=False)
    stamp(img, HEAD, "front", ["yb", "yY"], 5, 3, eye, light=False, flip=True)
    for u in (3, 4):
        put(img, HEAD, "front", u, 5, (8, 4, 6))
    for (u, v, c) in ((1, 5, LV[2]), (6, 5, LV[2]), (1, 6, LV[1]), (2, 6, (244, 224, 180)), (3, 6, LV[3]), (4, 6, LV[3]),
                      (5, 6, (244, 224, 180)), (6, 6, LV[1]), (2, 7, (40, 10, 6)), (3, 7, LV[0]), (4, 7, LV[0]), (5, 7, (40, 10, 6))):
        put(img, HEAD, "front", u, v, c)
    stamp(img, HEAD, "top", ["hH", "Hh"], 1, 1, horn)
    stamp(img, HEAD, "top", ["Hh", "hH"], 5, 1, horn)
    for face in ("right", "left"):
        stamp(img, HEAD, face, ["TH", "Hh"], 3, 0, horn)

    ROD = gi.box(0, 16, 2, 8, 2)

    def rod(face, u, v, w, h, x, y):
        if face == "top":
            return LV[4] if (u + v) % 2 == 0 else LV[3]
        if face == "bottom":
            return LV[1]
        if v == 0:
            return LV[3]
        if v == 1:
            return LV[2]
        if v == 2:
            return mix(rp(CH, x, y, 61, 2), LV[1], 0.5)
        if v == 7:
            return LV[1]
        if v == 6:
            return mix(rp(CH, x, y, 61, 2), LV[0], 0.5)
        if face in ("front", "back") and ((u == 0 and v == 4) or (u == 1 and v == 5)):
            return LV[2]
        if face in ("right", "left") and u == 1 and v == 3:
            return LV[1]
        return rp(CH, x, y, 62, 2 if face == "front" else 1)

    paint_box(img, ROD, rod)
    return img


# ---- Grove Sentinel: zombie layout (64x64)
def grove_sentinel():
    img = Image.new("RGBA", (64, 64), (0, 0, 0, 0))
    BK = [(30, 20, 12), (50, 34, 19), (72, 50, 28), (96, 70, 40), (122, 92, 56)]
    MS = [(30, 50, 26), (48, 78, 36), (72, 108, 48), (104, 140, 66)]
    LF = [(34, 84, 34), (58, 128, 44), (96, 172, 62), (150, 210, 92)]
    VN = [(22, 56, 26), (38, 92, 38), (66, 132, 52)]
    AMB, AMB2, AMBD, DK = (255, 170, 36), (255, 226, 120), (190, 100, 18), (16, 9, 4)
    SIDES = ("right", "front", "left", "back")

    def wood(face, u, v, w, h, x, y, salt, moss=12, top=0, bottom=0):
        c = grain(BK, x, y, salt, 2)
        if face == "top":
            c = rp(BK, x, y, salt, 2)
        blob = _noise(u // 2, v // 2, salt + fsalt(face)) % 100
        thr = moss + (top if v < 3 else 0) + (bottom if v >= h - 3 else 0)
        if blob < thr:
            return rp(MS, x, y, salt + 2, 2 if blob % 3 else 1)
        return c

    HEAD = gi.box(0, 0, 8, 8, 8)

    def head(face, u, v, w, h, x, y):
        s = 7
        if face == "top":
            return rp(LF, x, y, 5, 1) if _noise(x, y, 6) % 3 else wood(face, u, v, w, h, x, y, s, 30)
        if face == "bottom":
            return rp(BK, x, y, 8, 1)
        if face in SIDES and v == 0:
            return rp(LF, x, y, 9, 2) if _noise(x, y, 10) % 5 else BK[1]
        if face in SIDES and v == 1:
            return rp(LF, x, y, 9, 1) if _noise(x, y, 11) % 2 else wood(face, u, v, w, h, x, y, s)
        if face == "front":
            if u in (0, 7) or v == 2:
                return rp(BK, x, y, 12, 1)
            return rp(BK, x, y, 13, 3)  # the smoothed, carved mask
        if face == "back":
            return wood(face, u, v, w, h, x, y, s, 22, 0)
        return wood(face, u, v, w, h, x, y, s, 14)

    paint_box(img, HEAD, head)
    EYEPAL = {"d": DK, "A": AMB, "B": AMB2, "C": AMBD}
    # carved eyes: a dark socket ring around an amber knot-hole
    for (flip, u0) in ((False, 1), (True, 5)):
        stamp(img, HEAD, "front", [".dd.", "dBAd", "dACd", ".dd."], u0 - 1, 2, EYEPAL, light=False, flip=flip)
    # re-draw so the knot is amber, not ringed twice: ring cells that are not part of the eye
    for u in (3, 4):
        put(img, HEAD, "front", u, 4, BK[4])  # nose ridge
        put(img, HEAD, "front", u, 5, BK[4])
        put(img, HEAD, "front", u, 6, BK[3] if u == 3 else BK[2])
    for u in range(1, 7):
        put(img, HEAD, "front", u, 7, DK if 2 <= u <= 5 else BK[0])
    for u in (2, 3, 4, 5):
        put(img, HEAD, "front", u, 6, BK[0] if u % 2 else DK)
    for (u, v) in ((0, 2), (0, 3), (7, 2), (7, 3), (0, 4)):
        put(img, HEAD, "front", u, v, rp(LF, u, v, 14, 2), True)  # leaves tucked behind the mask
    hang(img, HEAD, "back", [(1, 5), (4, 7), (6, 4)], [VN[2], VN[1], VN[0]], 2)

    BODY = gi.box(16, 16, 8, 12, 4)

    def body(face, u, v, w, h, x, y):
        c = wood(face, u, v, w, h, x, y, 21, 12, 24, 14)
        if face == "front" and abs((7 - u) * 1.5 - v + 1) < 1 and v <= 11:
            return rp(VN, x, y, 22, 1)
        return c

    paint_box(img, BODY, body)
    stamp(img, BODY, "front", [".dd.", "dAAd", "dBAd", ".dd."], 2, 3, EYEPAL, light=False)
    for (u, v) in ((6, 1), (7, 0), (2, 8), (1, 9)):
        put(img, BODY, "front", u, v, LF[2], True)
    for (u, v) in ((1, 2), (2, 5)):
        put(img, BODY, "back", u, v, LF[1], True)

    def arm(face, u, v, w, h, x, y):
        c = wood(face, u, v, w, h, x, y, 31, 10, 36, 0)
        if face in SIDES:
            if v >= 10:
                return rp(BK, x, y, 32, 1 if v == 11 else 2 if (u + v) % 2 else 1)  # root fingers
            gu = (SIDES.index(face) * 4 + u)
            if 2 <= v <= 9 and (v - gu) % 7 == 0:
                return rp(VN, x, y, 33, 1)
        return c

    paint_box(img, gi.box(40, 16, 4, 12, 4), arm)
    paint_box(img, gi.box(32, 48, 4, 12, 4), arm)

    def leg(face, u, v, w, h, x, y):
        c = wood(face, u, v, w, h, x, y, 41, 10, 22, 30)
        if face in SIDES and v >= 10:
            return rp(BK, x, y, 42, 1 if (u + v) % 2 else 2)
        return c

    paint_box(img, gi.box(0, 16, 4, 12, 4), leg)
    paint_box(img, gi.box(16, 48, 4, 12, 4), leg)
    return img


# ---- Tide Lurker: zombie/drowned layout (64x64)
def tide_lurker():
    img = Image.new("RGBA", (64, 64), (0, 0, 0, 0))
    SC = [(18, 56, 56), (30, 88, 84), (46, 124, 110), (74, 160, 138), (112, 198, 168)]
    NV = [(12, 18, 40), (22, 32, 66), (36, 50, 96), (66, 82, 128)]
    STR = (108, 124, 162)
    KL = [(22, 52, 26), (38, 82, 34), (64, 118, 46), (100, 148, 62)]
    SIDES = ("right", "front", "left", "back")
    BARN = {"a": (216, 214, 196), "k": (70, 68, 60)}
    FIN = {"r": (136, 214, 192), "m": (40, 112, 124), "e": (20, 66, 80)}

    def scales(face, u, v, w, h, x, y, salt=1):
        cu = (u + (v // 2) % 2) % 2
        cv = v % 2
        i = 2 + (1 if cu == 0 and cv == 0 else -1 if cu == 1 and cv == 1 else 0)
        n = _noise(x, y, salt) % 10
        i += -1 if n == 0 else 1 if n == 9 else 0
        return SC[max(0, min(4, i))]

    def kelp(rects, face, u0, ln, phase=0, top=0):
        for k in range(ln):
            off = 1 if ((k + phase) // 2) % 2 else 0
            put(img, rects, face, u0 + off, top + k, KL[3] if k == ln - 1 else KL[2] if k % 2 else KL[1], True)
            put(img, rects, face, u0 + 1 - off, top + k, KL[0], True) if k % 3 == 0 else None

    HEAD = gi.box(0, 0, 8, 8, 8)

    def head(face, u, v, w, h, x, y):
        c = scales(face, u, v, w, h, x, y, 11)
        if face == "top":
            return rp(KL, x, y, 12, 1) if _noise(u // 2, v, 13) % 3 else c
        if face in ("right", "left") and v <= 1:
            return rp(KL, x, y, 12, 1)
        if face == "back" and v <= 2:
            return rp(KL, x, y, 12, 1)
        return c

    paint_box(img, HEAD, head)
    for face in ("right", "left"):
        stamp(img, HEAD, face, ["..rm.", ".rmme", "rmmrm", ".rmme", "..rm."], 1 if face == "right" else 2, 2, FIN, flip=(face == "left"))
    ring = (6, 26, 30)
    for (flip, u0) in ((False, 0), (True, 4)):
        stamp(img, HEAD, "front", [".dd.", "dcCd", "dPWd", ".dd."], u0, 2, {"d": ring, "c": (96, 214, 204), "C": (156, 246, 234), "P": (204, 255, 246), "W": (255, 255, 255)}, light=False, flip=flip)
    for u in (3, 4):
        put(img, HEAD, "front", u, 5, ring)
    for u in range(1, 7):
        put(img, HEAD, "front", u, 6, (8, 30, 32))
    for u in (2, 5):
        put(img, HEAD, "front", u, 6, (232, 238, 222))
        put(img, HEAD, "front", u, 7, (232, 238, 222))
    for u in (3, 4):
        put(img, HEAD, "front", u, 7, (6, 20, 22))
    for u, v in ((0, 5), (0, 6), (7, 5), (7, 6)):
        put(img, HEAD, "front", u, v, SC[0], True)
    stamp(img, HEAD, "top", [".a.", "aka"], 1, 1, BARN)
    stamp(img, HEAD, "top", [".a.", "aka"], 5, 5, BARN)
    stamp(img, HEAD, "back", [".a.", "aka"], 5, 4, BARN)
    kelp(HEAD, "front", 0, 4, 0)
    kelp(HEAD, "front", 6, 3, 1)
    kelp(HEAD, "right", 1, 6, 0)
    kelp(HEAD, "right", 5, 3, 2)
    kelp(HEAD, "left", 2, 5, 1)
    kelp(HEAD, "left", 6, 4, 0)
    kelp(HEAD, "back", 1, 7, 0)
    kelp(HEAD, "back", 4, 5, 1)
    kelp(HEAD, "back", 6, 7, 2)

    BODY = gi.box(16, 16, 8, 12, 4)

    def cloth(face, u, v, w, h, x, y, salt, hem):
        if v >= hem + _noise(u, 3, salt + fsalt(face)) % 3:
            return scales(face, u, v, w, h, x, y, salt)
        if _noise(x, y, salt + 5) % 19 == 0:
            return scales(face, u, v, w, h, x, y, salt)  # a tear
        stripe = v % 3 == 1
        return rp(NV, x, y, salt, 3 if stripe else 1 + (1 if _noise(x, y, salt + 6) % 4 == 0 else 0)) if not stripe else \
            (STR if _noise(x, y, salt + 7) % 3 else NV[3])

    paint_box(img, BODY, lambda f, u, v, w, h, x, y: cloth(f, u, v, w, h, x, y, 21, 9) if f != "bottom" else NV[0])
    stamp(img, BODY, "front", ["a.....a", ".aaaaa."], 0, 0, {"a": (196, 206, 230)})
    stamp(img, BODY, "front", [".a.", "aka"], 5, 5, BARN)
    stamp(img, BODY, "back", [".a.", "aka"], 1, 3, BARN)
    for k in range(7):  # a strand of kelp across the chest
        put(img, BODY, "front", 1 + k // 1 if k < 7 else 7, 2 + (k * 7) // 8, KL[1 + k % 2], True)
    put(img, BODY, "front", 0, 3, KL[3], True)

    def arm(face, u, v, w, h, x, y):
        if face in SIDES and v <= 3 + _noise(u, 2, 31 + fsalt(face)) % 2:
            return (STR if _noise(x, y, 32) % 3 else NV[3]) if v % 3 == 1 else rp(NV, x, y, 33, 1)
        return scales(face, u, v, w, h, x, y, 34)

    for rect in (gi.box(40, 16, 4, 12, 4), gi.box(32, 48, 4, 12, 4)):
        paint_box(img, rect, arm)
        stamp(img, rect, "front", [".a.", "aka"], 0, 7, BARN)
        kelp(rect, "back", 1, 4, 0, 6)

    def leg(face, u, v, w, h, x, y):
        if face in SIDES and v <= 7 + _noise(u, 4, 41 + fsalt(face)) % 3:
            return (STR if _noise(x, y, 42) % 3 else NV[3]) if v % 3 == 1 else rp(NV, x, y, 43, 1)
        c = scales(face, u, v, w, h, x, y, 44)
        return mix(c, SC[0], 0.4) if v == 11 else c

    for rect in (gi.box(0, 16, 4, 12, 4), gi.box(16, 48, 4, 12, 4)):
        paint_box(img, rect, leg)
        stamp(img, rect, "front", [".a.", "aka"], 1, 8, BARN)
    kelp(gi.box(0, 16, 4, 12, 4), "front", 0, 3, 1, 9)
    kelp(gi.box(16, 48, 4, 12, 4), "front", 2, 3, 0, 9)
    return img


# ---- Hollow Wisp: vex layout (32x32)
def hollow_wisp():
    img = Image.new("RGBA", (32, 32), (0, 0, 0, 0))
    WP = [(152, 178, 228), (186, 210, 246), (216, 232, 253), (240, 248, 255), (255, 255, 255)]
    LAV = [(150, 130, 210), (184, 166, 236), (212, 200, 250), (238, 230, 255)]
    HOLE = (22, 26, 72)

    def spirit(face, u, v, w, h, x, y, salt, bias=3, ragged=False):
        if ragged:
            if v == h - 1 and _noise(x, y, salt + 9) % 2 == 0:
                return None
            if v == h - 2 and _noise(x, y, salt + 8) % 5 == 0:
                return None
        edge = u in (0, w - 1) or v in (0, h - 1)
        b = bias + (1 if edge and _noise(x, y, salt + 3) % 2 else 0) - (1 if v >= h - 2 and h > 3 else 0)
        c = rp(WP, x, y, salt, b)
        if h >= 5 and v >= h - 2:
            c = mix(c, LAV[2], 0.4)
        return c

    HEAD = gi.box(0, 0, 5, 5, 5)
    paint_box(img, HEAD, lambda f, u, v, w, h, x, y: spirit(f, u, v, w, h, x, y, 7, 2))
    for (u, v) in ((0, 2), (1, 1), (1, 2), (4, 2), (3, 1), (3, 2), (2, 4)):
        put(img, HEAD, "front", u, v, HOLE)
    put(img, HEAD, "front", 1, 3, (120, 134, 220))
    put(img, HEAD, "front", 3, 3, (120, 134, 220))

    paint_box(img, gi.box(0, 10, 3, 4, 2), lambda f, u, v, w, h, x, y: spirit(f, u, v, w, h, x, y, 17, 2))
    paint_box(img, gi.box(0, 16, 3, 5, 2), lambda f, u, v, w, h, x, y: spirit(f, u, v, w, h, x, y, 27, 2, True))
    for r in (gi.box(23, 0, 2, 4, 2), gi.box(23, 6, 2, 4, 2)):
        paint_box(img, r, lambda f, u, v, w, h, x, y: LAV[2] if f in ("right", "front", "left", "back") and v == h - 1
                  else spirit(f, u, v, w, h, x, y, 37, 3))

    # wings: soft lavender membrane with pale veins and a feathered edge
    for v in range(5):
        for u in range(8):
            x, y = 16 + u, 22 + v
            if v >= 3 and u <= 2 and _noise(x, y, 47) % 2 == 0:
                continue
            if v == 4 and _noise(x, y, 48) % 3 == 0:
                continue
            c = LAV[2] if (u - 2 * v) % 4 == 3 else LAV[1] if (u + v) % 5 else LAV[0]
            if u >= 6:
                c = LAV[3] if v < 3 else c
            img.putpixel((x, y), c + (255,))
    return img


# ---- Gravecaller: zombie layout (64x64)
def gravecaller():
    img = Image.new("RGBA", (64, 64), (0, 0, 0, 0))
    GP = [(62, 52, 72), (86, 74, 96), (110, 98, 120), (136, 124, 142), (160, 148, 164)]
    ROT = (92, 108, 74)
    BL = [(16, 12, 24), (28, 22, 40), (44, 36, 62), (62, 52, 84), (86, 74, 112)]
    TRIM = [(70, 40, 100), (104, 62, 146)]
    BONE, BONED = (214, 204, 172), (150, 140, 112)
    GR, GRH, GRD = (60, 204, 84), (156, 255, 160), (24, 92, 40)
    SIDES = ("right", "front", "left", "back")

    def robe(face, u, v, w, h, x, y, salt):
        fold = (u * 5 + fsalt(face)) % 4
        return rp(BL, x, y, salt, 2 + (1 if fold == 0 else -1 if fold == 2 else 0))

    HEAD = gi.box(0, 0, 8, 8, 8)

    def head(face, u, v, w, h, x, y):
        if face == "bottom":
            return rp(GP, x, y, 12, 0)
        if face == "top":
            return robe(face, u, v, w, h, x, y, 11) if u not in (3, 4) else rp(BL, x, y, 11, 3)
        if face == "front":
            if u in (0, 7) or v < 2 or (v == 2 and u in (1, 6)):
                return robe(face, u, v, w, h, x, y, 13)
            c = rp(GP, x, y, 14, 1 if (v == 2 or u in (1, 6)) else 3)
            return c
        return robe(face, u, v, w, h, x, y, 15)

    paint_box(img, HEAD, head)
    for (u, v, c) in ((1, 3, (24, 16, 30)), (2, 3, (24, 16, 30)), (5, 3, (24, 16, 30)), (6, 3, (24, 16, 30)),
                      (1, 4, GR), (2, 4, GRH), (5, 4, GRH), (6, 4, GR), (2, 5, GRD), (5, 5, GRD)):
        put(img, HEAD, "front", u, v, c, c in ((24, 16, 30),))
    for (u, v, c) in ((3, 5, GP[0]), (4, 5, GP[0]), (6, 5, ROT), (2, 6, ROT), (6, 7, BONE), (6, 6, BONED), (1, 6, GP[3]), (1, 7, GP[1]),
                      (2, 6, (28, 18, 28)), (3, 6, (28, 18, 28)), (4, 6, (28, 18, 28)), (5, 6, (28, 18, 28)), (2, 7, (190, 180, 168)), (4, 7, (190, 180, 168))):
        put(img, HEAD, "front", u, v, c, True)
    stamp(img, HEAD, "front", ["BB", "bB"], 3, 0, {"B": BONE, "b": BONED})
    for u in (3, 4):
        for v in range(8):
            put(img, HEAD, "back", u, v, BL[0] if v % 2 else BL[1], True)
    for face in ("right", "left"):
        for v in range(3, 7):
            put(img, HEAD, face, 3 if face == "right" else 4, v, BL[0], True)

    BODY = gi.box(16, 16, 8, 12, 4)

    def body(face, u, v, w, h, x, y):
        if face == "bottom":
            return BL[0]
        if face in SIDES and v == h - 1:
            return TRIM[0] if _noise(x, y, 23) % 4 else None
        if face == "front" and u in (0, 7):
            return TRIM[1] if v % 4 else TRIM[0]
        if face in SIDES and v == 8:
            return (96, 74, 50) if (u + fsalt(face)) % 2 else (70, 52, 36)
        return robe(face, u, v, w, h, x, y, 21)

    paint_box(img, BODY, body)
    RUNE = {"G": GR, "H": GRH, "D": GRD}
    stamp(img, BODY, "front", ["..GG..", ".G..G.", "G.HH.G", "G.HH.G", ".G..G.", "..GG.."], 1, 1, RUNE, light=False)
    for (u, v) in ((0, 7), (7, 7)):
        pass
    for (u, v) in ((1, 8), (3, 8), (6, 8)):
        put(img, BODY, "front", u, v, BONE, True)
    for v in (1, 4, 7):
        stamp(img, BODY, "back", ["BB", "bb"], 3, v, {"B": BONE, "b": BONED})
    for u in (0, 7):
        put(img, BODY, "top", u, 1, BONE)

    def arm(face, u, v, w, h, x, y):
        if face in SIDES:
            if v == 7:
                return TRIM[1] if u % 2 else TRIM[0]
            if v == 8:
                return BONE if _noise(x, y, 33) % 4 else BONED
            if v >= 9:
                if v == 11:
                    return (34, 26, 34)
                return ROT if _noise(x, y, 34) % 7 == 0 else rp(GP, x, y, 35, 2)
            return robe(face, u, v, w, h, x, y, 36)
        return robe(face, u, v, w, h, x, y, 36)

    paint_box(img, gi.box(40, 16, 4, 12, 4), arm)
    paint_box(img, gi.box(32, 48, 4, 12, 4), arm)

    def leg(face, u, v, w, h, x, y):
        if face in SIDES:
            if v == 9:
                return TRIM[0]
            if v >= 10:
                return rp(GP, x, y, 43, 0) if _noise(x, y, 44) % 3 == 0 else rp(BL, x, y, 45, 1)
        return robe(face, u, v, w, h, x, y, 41)

    paint_box(img, gi.box(0, 16, 4, 12, 4), leg)
    paint_box(img, gi.box(16, 48, 4, 12, 4), leg)
    return img


MOB_TEXTURES = {"glimmer_crawler": glimmer_crawler, "glimmer_crawler_glow": glimmer_crawler_glow, "bog_hag": bog_hag,
                "frost_revenant": frost_revenant, "ember_imp": ember_imp, "grove_sentinel": grove_sentinel,
                "tide_lurker": tide_lurker, "hollow_wisp": hollow_wisp, "gravecaller": gravecaller}


# ---------------------------------------------------------------- mob drop item icons
TWIG = gi.Material(["2a1a0c", "4a3016", "6e4a26", "94683a", "b8905a"], texture=gi.grains)
STRING = gi.Material(["5a4a30", "8a7650", "b8a478", "d8c898", "f0e4be"], texture=gi.flat)
BEAD = gi.Material(["0e3a1c", "1a6a2c", "30a448", "6ee080", "c8ffd0"], base=3)
FROST = gi.Material(["3a6a96", "6aa0cc", "9ad0ee", "c8ecfa", "f2fcff"], base=2)
FROST_W = gi.Material(["8ab4d0", "b8d8ec", "dcf0fa", "f0fbff", "ffffff"], base=3)
CHAR = gi.Material(["0c0a0c", "1c1818", "322c2a", "4c423e", "6e605a"], texture=gi.speckle)
MOLTEN = gi.Material(["7a1a06", "c0400c", "f07a14", "ffb02c", "fff0a0"], base=3)
MOLTEN_Y = gi.Material(["c0400c", "f07a14", "ffb02c", "ffe060", "fffcd0"], base=3)
WISP_L = gi.Material(["5a78b0", "90b0e0", "c0d8f8", "e4f0ff", "ffffff"], base=3)
WISP_W = gi.Material(["90b0e0", "c0d8f8", "e4f0ff", "ffffff", "ffffff"], base=3)
WISP_V = gi.Material(["4a3a8a", "7a6ac8", "a898e8", "d0c4ff", "f4eeff"], base=3)

ITEMS["hags_charm"] = ({"T": TWIG, "S": STRING, "G": BEAD, "B": BONE}, [
    "................",
    "......SSSS......",
    ".....SS..SS.....",
    ".....S....S.....",
    "..TT..S..S..TT..",
    "...TTTTSSTTTT...",
    "..T....SS....T..",
    ".......SS.......",
    "......GGGG......",
    "......GGGG......",
    ".......SS.......",
    "......BBBB......",
    ".......BB.......",
    ".......BB.......",
    "......BBBB......",
    "................",
])
ITEMS["rime_shard"] = ({"D": FROST.shifted(base=1), "L": FROST.shifted(base=3), "F": FROST_W}, [
    "................",
    "............FF..",
    "...........LLF..",
    "..........DLLL..",
    ".........DDLLL..",
    "........DDDLLL..",
    ".......DDDDLLL..",
    "......DDDDDLLL..",
    ".....DDDDDLLL...",
    "....DDDDDLLLL...",
    "...FDDDDDLLL....",
    "..FFDDDDLLL.....",
    "..FFDDDLL.......",
    "...FFDLL........",
    "....FF..........",
    "................",
])
ITEMS["ember_core"] = ({"C": CHAR, "M": MOLTEN, "Y": MOLTEN_Y}, [
    "................",
    "......CCCC......",
    "....CCCCCCCC....",
    "...CCMMCCCCCC...",
    "..CCMMMYCCCCCC..",
    "..CMMYYMMCCCCC..",
    ".CCMYYYYYMMCCCC.",
    ".CCCMYYYYYYMCCC.",
    ".CCCCMMYYYYMCCC.",
    ".CCCCCCMYYMMCCC.",
    "..CCCCCCMMMCCC..",
    "..CCCCCCCMCCCC..",
    "...CCCCCCCCCC...",
    "....CCCCCCCC....",
    "......CCCC......",
    "................",
])
ITEMS["wisp_essence"] = ({"K": CORK, "G": GLASS, "L": WISP_L, "W": WISP_W, "V": WISP_V}, [
    "................",
    "......KKKK......",
    "......KKKK......",
    ".....GGGGGG.....",
    "......GLLG......",
    "......GLLG......",
    ".....GLLLLG.....",
    "....GLLWWLLG....",
    "...GLLWWVVLLG...",
    "...GLVLWLLWLG...",
    "...GLWLLLVWLG...",
    "...GLLVWWLLLG...",
    "...GLLLLVLLLG...",
    "....GLLLLLLG....",
    ".....GGGGGG.....",
    "................",
])
ITEM_NAMES.update({"hags_charm": "Hag's Charm", "rime_shard": "Rime Shard", "ember_core": "Ember Core", "wisp_essence": "Wisp Essence"})

# Spawn eggs for the world mobs: vanilla's egg shape, each in its mob's colours (shell, spots).
EGG = [
    "................",
    "......BBBB......",
    ".....BBBBBB.....",
    "....BBSBBBBB....",
    "....BSSBBBBB....",
    "...BBBBBBSBBB...",
    "...BBBBBSSSBB...",
    "...BBSBBBSBBB...",
    "...BSSSBBBBBB...",
    "...BBSBBBBSBB...",
    "...BBBBBBSSBB...",
    "....BBBSBBBB....",
    "....BBSSBBBB....",
    ".....BBBBBB.....",
    "......BBBB......",
    "................",
]
EGGS = {
    "glimmer_crawler": (["14181e", "232a33", "343e4a", "4a5868", "62748a"], ["0a5a6a", "128a9a", "2cc4cc", "6ae8e8", "c8ffff"]),
    "bog_hag": (["1e2a14", "2e4020", "42582e", "5a7440", "76925a"], ["3a1450", "5a2478", "7e38a4", "a456cc", "cc8aee"]),
    "frost_revenant": (["5a6e80", "7e94a8", "a4bccc", "c8dce8", "eaf6ff"], ["2a3a4a", "3e5466", "56708a", "7a96b0", "a6c0d6"]),
    "ember_imp": (["3a0a04", "5c1406", "84200c", "aa3214", "cc4a20"], ["8a3a00", "c46000", "f28a10", "ffb43c", "ffe08a"]),
    "grove_sentinel": (["2e1e10", "46301a", "604428", "7c5a36", "9a7448"], ["1d4a1c", "2c6a28", "449036", "68b84a", "9ade70"]),
    "tide_lurker": (["0a2e34", "10464e", "1a626a", "2a8288", "44a4a6"], ["1a3a1a", "2a5428", "3e7038", "5a8e4a", "7eae64"]),
    "hollow_wisp": (["6a6088", "8a80aa", "aaa2c8", "cac4e2", "eae6fa"], ["8a8aa0", "b0b0c8", "d4d4ea", "eeeeff", "ffffff"]),
    "gravecaller": (["161c16", "242c22", "343e30", "485440", "5e6c52"], ["7a705a", "a0947a", "c4b898", "e2d8bc", "faf4e2"]),
}
for mob, (shell, spots) in EGGS.items():
    ITEMS[f"{mob}_spawn_egg"] = ({"B": gi.Material(shell), "S": gi.Material(spots, base=3)}, EGG)
    ITEM_NAMES[f"{mob}_spawn_egg"] = mob.replace("_", " ").title() + " Spawn Egg"


def write_json(path, obj):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(obj, indent=2) + "\n", encoding="utf-8", newline="\n")


def build():
    for name, (mats, mask, *tweak) in ITEMS.items():
        img = gi.render(mask, mats, tweak[0] if tweak else None)
        out = ASSETS / "textures" / "item" / f"{name}.png"
        out.parent.mkdir(parents=True, exist_ok=True)
        img.save(out)
        write_json(ASSETS / "models" / "item" / f"{name}.json",
                   {"parent": "minecraft:item/generated", "textures": {"layer0": f"skyrpg_combat:item/{name}"}})
        write_json(ASSETS / "items" / f"{name}.json", {"model": {"type": "minecraft:model", "model": f"skyrpg_combat:item/{name}"}})
    out = ASSETS / "textures" / "entity" / "training_dummy.png"
    out.parent.mkdir(parents=True, exist_ok=True)
    skin().save(out)
    for name, paint in BOSS_TEXTURES.items():
        out = ASSETS / "textures" / "entity" / "slayer" / f"{name}.png"
        out.parent.mkdir(parents=True, exist_ok=True)
        paint().save(out)
    for name, paint in MOB_TEXTURES.items():
        out = ASSETS / "textures" / "entity" / "mob" / f"{name}.png"
        out.parent.mkdir(parents=True, exist_ok=True)
        paint().save(out)
    lang_path = ASSETS / "lang" / "en_us.json"
    lang = json.loads(lang_path.read_text(encoding="utf-8"))
    for name, title in ITEM_NAMES.items():
        lang[f"item.skyrpg_combat.{name}"] = title
    write_json(lang_path, lang)
    print(f"wrote {len(ITEMS)} combat item(s), the training dummy skin, {len(BOSS_TEXTURES)} slayer boss textures and {len(MOB_TEXTURES)} mob textures")


if __name__ == "__main__":
    build()
