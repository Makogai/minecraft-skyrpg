#!/usr/bin/env python3
"""Draws the accessories: their item icons and the textures they're worn with on the player.

    python tools/art/generate_accessories.py                  # writes the assets below
    python tools/art/generate_accessories.py --preview DIR    # also writes a contact sheet and 3D previews to DIR

Same engine and palettes as generate_items.py (masks, auto shading, a coloured outline on icons; see its
docstring), which this imports. Writes, under items/src/main/resources/assets/skyrpg_items/:
  * textures/item/<id>.png                  16x16 icon
  * textures/entity/accessory/<id>.png      worn texture, in its slot's UV layout
  * items/<id>.json, models/item/<id>.json
Their names in the lang file come from generate_items.py, which reads every item definition.

Worn textures: every slot has one shape, AccessoryModel.java in the items client code, and SLOTS below has
the same cubes with the same UVs (keep the two in step). An item paints only the cubes it uses and leaves the
rest transparent, which is how one shape is a stone pendant or a gem, a sash or a toolbelt. Each cube is
modelled twice (bare, and further out over chest armour) with the same UVs, so one texture fits both.
"""
import argparse
import math
import sys
from pathlib import Path

sys.dont_write_bytecode = True  # importing generate_items shouldn't leave a __pycache__ behind
import generate_items as gi  # noqa: E402
from PIL import Image, ImageDraw  # noqa: E402

M = gi.Material

# ---------------------------------------------------------------- palettes (darkest first)
CORD = M(["2a170c", "442614", "62391e", "83512c", "a46e40"])
EMBER = M(["220707", "48100f", "721b17", "a02a20", "c94a32"])
EMBER_CORE = M(["902c06", "d05a0c", "f78f1e", "ffc650", "fff1b8"], base=3)
SILVER = M(["3a3e4a", "656b7b", "979fb0", "c7cedb", "f1f4f9"], texture=gi.sheen)
STARGLASS = M(["1b1450", "33278a", "5244c2", "8478ec", "c9c2ff"])
STARLIGHT = M(["8478ec", "b0a8ff", "d8d4ff", "f0eeff", "ffffff"], base=4)
WOOL = M(["1b2729", "2c3e41", "41585b", "5d7476", "829898"], texture=gi.cloth)
BRASS = M(["4a300f", "7a571c", "ab852c", "d6b44a", "f3dd8a"], texture=gi.sheen)
DUSK = M(["07030d", "130a24", "231340", "361d5e", "4d2a80"])
LINEN = M(["7a5212", "ae7e22", "d8aa3e", "f0cf6c", "fbe9ac"], texture=gi.weave)
DARK_LEATHER = M(["22140b", "382213", "52341d", "6d4829", "8b6038"], texture=gi.stitch)


def strips(u, v, w, h):
    """Bark: vertical strips, every other one a shade darker."""
    return -1 if u % 2 else 0


BARK = M(["2a1a0e", "45301c", "634628", "856038", "a87e4c"], texture=strips)


# ---------------------------------------------------------------- slot shapes
# The cubes of AccessoryModel.java, per slot: the part they hang under, (x, y, z), (w, h, d), texture
# offset (u, v), grow (CubeDeformation), mirrored or not, and for the cape its own pivot (it is turned round
# like vanilla's cape and sways). Positions are model pixels; `depth` is how far out the fit sits, bare or
# over armour. Only the preview uses the positions; the sizes and offsets decide the texture layout.
class Cube:
    def __init__(self, part, name, pos, size, uv, grow=(0, 0, 0), mirror=False, pivot=None):
        self.part, self.name, self.pos, self.size, self.uv = part, name, pos, size, uv
        self.grow, self.mirror, self.pivot = grow, mirror, pivot

    def faces(self):
        return gi.box(self.uv[0], self.uv[1], *self.size)


def amulet(depth, slim=False, armour=False):
    front = -2 - depth
    return [Cube("body", "cord", (-4, 0, -2), (8, 5, 4), (0, 0), (depth, 0.1, depth)),
            Cube("body", "pendant", (-2, 1, front - 0.925), (4, 4, 1), (0, 9), (0, 0, -0.125))]


def cloak(depth, slim=False, armour=False):
    front = -2 - depth
    cubes = [Cube("body", "cape", (-5, 0, -1), (10, 16, 1), (0, 0), pivot=(0, 0, 2 + depth + 0.05)),
             Cube("body", "mantle", (-4, 0, -2), (8, 5, 4), (22, 0), (depth, 0.15, depth)),
             Cube("body", "collar", (-5, -0.5, 1.8 + depth), (10, 2, 2), (22, 9), (0.1, 0, 0)),
             Cube("body", "clasp", (-3, 0.5, front - 0.5), (1, 2, 1), (46, 0)),
             Cube("body", "clasp", (2, 0.5, front - 0.5), (1, 2, 1), (46, 0))]
    if not armour:
        width, u = (3, 38) if slim else (4, 22)
        drape = (depth, 0.15, depth)
        cubes += [Cube("right_arm", "shoulder", (-2 if slim else -3, -2, -2), (width, 3, 4), (u, 13), drape),
                  Cube("left_arm", "shoulder", (-1, -2, -2), (width, 3, 4), (u, 13), drape, mirror=True)]
    return cubes


def belt(depth, slim=False, armour=False):
    front = -2 - depth
    return [Cube("body", "band", (-4, 9.5, -2), (8, 3, 4), (0, 0), (depth, 0, depth)),
            Cube("body", "buckle", (-2, 9.5, front - 0.925), (4, 3, 1), (0, 7), (0, 0.2, -0.125)),
            Cube("body", "knot", (-4, 10, front - 0.925), (2, 2, 1), (10, 7), (0.1, 0.1, -0.125)),
            Cube("body", "pouch", (1, 10, front - 1.8), (3, 3, 2), (16, 7), (0, 0, -0.25)),
            Cube("body", "handle", (2, 7.5, front - 1.3), (1, 4, 1), (26, 7), (-0.25, 0, -0.25)),
            Cube("body", "head", (1, 7, front - 1.3), (3, 1, 1), (0, 12), (0, 0, -0.1)),
            Cube("right_leg", "tail", (-2, 0, front - 0.8), (2, 4, 1), (8, 12), (0, 0, -0.25))]


def bracers(depth, slim=False, armour=False):
    thin = slim and not armour  # armour sleeves are wide on slim players too
    width, v = (3, 8) if thin else (4, 0)
    cuff, plate = (depth, 0.25, depth), (-0.125, 0, 0)
    outer = width - 1 + depth
    return [Cube("right_arm", "cuff", (1 - width, 5, -2), (width, 4, 4), (0, v), cuff),
            Cube("right_arm", "plate", (-outer - 0.925, 4.5, -1.5), (1, 5, 3), (16, 0), plate),
            Cube("left_arm", "cuff", (-1, 5, -2), (width, 4, 4), (0, v), cuff, mirror=True),
            Cube("left_arm", "plate", (outer - 0.075, 4.5, -1.5), (1, 5, 3), (16, 0), plate, mirror=True)]


# slot: (texture size, shape, bare depth, armoured depth)
SLOTS = {
    "amulet": ((32, 16), amulet, 0.35, 1.45),
    "cloak": ((64, 32), cloak, 0.5, 1.4),
    "belt": ((32, 32), belt, 0.55, 1.2),
    "bracers": ((32, 32), bracers, 0.35, 1.25),
}


def uv_boxes(slot):
    """Cube name -> its face rectangles, for every cube of a slot; slim arms' own UVs are "<name>_slim"."""
    _, shape, bare, _ = SLOTS[slot]
    boxes = {cube.name: cube.faces() for cube in shape(bare)}
    for cube in shape(bare, slim=True):
        if cube.faces() != boxes[cube.name]:
            boxes[cube.name + "_slim"] = cube.faces()
    return boxes


class Sheet(gi.Sheet):
    """A worn texture being painted, for one slot's cubes (generate_items' Sheet is the 64x32 armour one)."""

    def __init__(self, slot):
        (w, h), *_ = SLOTS[slot]
        self.boxes = uv_boxes(slot)
        self.mask = [["."] * w for _ in range(h)]
        self.tweak = [["."] * w for _ in range(h)]
        self.faces = [[None] * w for _ in range(h)]
        for b in self.boxes.values():
            for face, (x0, y0, fw, fh) in b.items():
                for y in range(y0, y0 + fh):
                    for x in range(x0, x0 + fw):
                        self.faces[y][x] = (face, x0, y0, fw, fh)

    def rows(self, cube, face, rows):
        """Paints a face from mask rows (one string per row, '.' left as it is)."""
        x0, y0, w, h = self.boxes[cube][face]
        assert len(rows) == h and all(len(r) == w for r in rows), (cube, face)
        for v, row in enumerate(rows):
            for u, ch in enumerate(row):
                if ch != ".":
                    self.mask[y0 + v][x0 + u] = ch

    def tweaks(self, cube, face, rows):
        x0, y0, w, h = self.boxes[cube][face]
        assert len(rows) == h and all(len(r) == w for r in rows), (cube, face)
        for v, row in enumerate(rows):
            for u, ch in enumerate(row):
                if ch != ".":
                    self.tweak[y0 + v][x0 + u] = ch

    def whole(self, cube, ch, faces=("top", "bottom") + gi.SIDES):
        self.fill(self.boxes[cube], faces, ch)


# ---------------------------------------------------------------- the accessories
# id: (icon mask, icon materials, icon tweak, worn painter). Icons are drawn like generate_items' ICONS.
ACCESSORIES = {}


def accessory(name, mats, mask, tweak=None):
    assert len(mask) == 16 and all(len(r) == 16 for r in mask), name
    if tweak:
        assert len(tweak) == 16 and all(len(r) == 16 for r in tweak), name + " tweak"

    def register(worn):
        ACCESSORIES[name] = (mask, mats, tweak, worn)
        return worn
    return register


def necklace(s, ch):
    """The cord: over the shoulders, round the back, and down the chest to the pendant's bail."""
    s.rows("cord", "front", [".{0}....{0}.".format(ch),
                             "..{0}..{0}..".format(ch),
                             "........",
                             "........",
                             "........"])
    s.fill(s.boxes["cord"], ["right", "left", "back"], ch, rows=[0])
    s.fill(s.boxes["cord"], ["top"], ch, cols=[1, 6])


def drape(s, cube, cloth, edge, ragged):
    """A shoulder drape: over the top, down the outer side, with a sloping front and back edge.
    On these faces the outer side is u = 0 on the front and u = w - 1 on the back (vanilla's UVs)."""
    b = s.boxes[cube]
    w, d = b["front"][2], b["right"][2]
    s.fill(b, ["top"], cloth)
    s.fill(b, ["right"], cloth, rows=[0, 1])
    s.fill(b, ["right"], edge, rows=[2], cols=range(0, d, 2) if ragged else range(d))
    s.fill(b, ["front", "back", "left"], cloth, rows=[0])
    s.put(b, "front", 0, 1, edge)
    s.put(b, "back", w - 1, 1, edge)


# ---- amulets
@accessory("emberstone_pendant", {"C": CORD, "E": EMBER, "A": EMBER_CORE, "F": EMBER_CORE.shifted(base=1)}, [
    "................",
    "..C..........C..",
    "..C..........C..",
    "...C........C...",
    "...C........C...",
    "....C......C....",
    ".....C....C.....",
    "......CCCC......",
    ".....EEEEEE.....",
    "....EEEEEEEE....",
    "....EEEFAEEE....",
    "....EEAAAFEE....",
    "....EEEAFEEE....",
    ".....EEEEEE.....",
    "......EEEE......",
    "................",
], [
    "................",
    "................",
    "................",
    "................",
    "................",
    "................",
    "................",
    "......3..3......",
    ".....4..........",
    "....4...........",
    "........4.......",
    ".......4........",
    "................",
    "................",
    "................",
    "................",
])
def emberstone_worn():
    s = Sheet("amulet")
    necklace(s, "C")
    # A smooth river stone, its core still glowing.
    s.rows("pendant", "front", [".EE.",
                                "EAFE",
                                "EFGE",
                                ".EE."])
    s.tweaks("pendant", "front", [".4..",
                                  "4...",
                                  "....",
                                  "...."])
    s.rows("pendant", "back", [".EE.", "EEEE", "EEEE", ".EE."])
    s.rows("pendant", "top", [".CC."])
    s.rows("pendant", "bottom", [".EE."])
    for face in ("right", "left"):
        s.rows("pendant", face, [".", "E", "E", "."])
    return s.render({"C": CORD.shifted(base=3), "E": EMBER, "A": EMBER_CORE.shifted(base=4),
                     "F": EMBER_CORE.shifted(base=2), "G": EMBER_CORE.shifted(base=1)})


@accessory("starglass_amulet", {"S": SILVER, "G": STARGLASS, "X": STARLIGHT}, [
    "................",
    "..S..........S..",
    "..S..........S..",
    "...S........S...",
    "...S........S...",
    "....S......S....",
    ".....S....S.....",
    "......SSSS......",
    ".....SGGGGS.....",
    "....SGGXGGGS....",
    "....SGXGGGGS....",
    ".....SGGGGS.....",
    "......SGGS......",
    ".......SS.......",
    "................",
    "................",
], [
    "................",
    "..3.............",
    "..1..........1..",
    "...3........3...",
    "...1........1...",
    "....3......3....",
    ".....1....1.....",
    "......4.........",
    ".....4.33.......",
    "....4.3.43......",
    "......4.4.......",
    "......2..1......",
    ".......21.......",
    "................",
    "................",
    "................",
])
def starglass_worn():
    s = Sheet("amulet")
    necklace(s, "S")
    s.tweak_face(s.boxes["cord"], "back", lambda u, v, w, h: "1" if u % 2 else None)
    # A drop of starglass under a silver cap; the setting shows on its sides.
    s.rows("pendant", "front", [".SS.",
                                "GXGG",
                                "GGGG",
                                ".GG."])
    s.tweaks("pendant", "front", ["....",
                                  "....",
                                  "...1",
                                  "..1."])
    s.rows("pendant", "back", [".SS.", "GGGG", "GGGG", ".GG."])
    s.rows("pendant", "top", [".SS."])
    s.rows("pendant", "bottom", [".SS."])
    for face in ("right", "left"):
        s.rows("pendant", face, [".", "S", "S", "."])
    return s.render({"S": SILVER, "G": STARGLASS, "X": STARLIGHT})


# ---- cloaks
@accessory("wayfarer_mantle", {"W": WOOL, "L": WOOL.shifted(base=0, texture=gi.flat), "M": WOOL.shifted(base=3),
                               "K": WOOL.shifted(base=0, texture=gi.flat), "H": WOOL.shifted(base=1, texture=gi.flat),
                               "B": BRASS, "P": gi.HIDE}, [
    "................",
    "......MMMM......",
    ".....MMMMMM.....",
    "....MMKKKKMM....",
    "....MKKKKKKM....",
    "....MMKKKKMM....",
    "...WWMMBBMMWW...",
    "..WWWWWLLWWWWW..",
    "..WWWWWLLWWWWW..",
    ".WWWWWWLLWWWWWW.",
    ".WPPWWLLLLWWWWW.",
    ".WPPWWLLLLWWWWW.",
    "WWWWWLLLLLLWWWWW",
    "HHHHHLLLLLLHHHHH",
    "WW.WW......WW.WW",
    "................",
], [
    "................",
    "......4.........",
    ".....4..........",
    "....4...........",
    "....4...........",
    "................",
    "...4...4........",
    "..4.-......-....",
    "....-......-....",
    "...........-+...",
    "...........-+...",
    "..-.........-...",
    "..-.........-...",
    "................",
    "................",
    "................",
])
def wayfarer_worn():
    s = Sheet("cloak")
    # Cape: oiled wool in loose folds, a darker hem stripe, one leather patch.
    s.whole("cape", "W")
    s.fill(s.boxes["cape"], ["back"], "L")
    s.tweak_face(s.boxes["cape"], "front",
                 lambda u, v, w, h: "-" if u in (2, 6) and v > 3 else "+" if u in (1, 5, 8) and v > 5 else None)
    s.fill(s.boxes["cape"], gi.SIDES, "H", rows=[13, 14])
    for u, v in ((6, 8), (7, 8), (6, 9), (7, 9), (8, 9), (6, 10), (7, 10)):
        s.put(s.boxes["cape"], "front", u, v, "P")
    s.put(s.boxes["cape"], "front", 8, 10, tweak="-")
    # Mantle: a lighter wool capelet with lapels and brass toggles, a rolled collar over the cape.
    s.rows("mantle", "front", ["MM....MM",
                               "MM....MM",
                               "MM....MM",
                               "MM....MM",
                               "M......M"])
    s.fill(s.boxes["mantle"], ["right", "left", "back"], "M", rows=range(0, 4))
    s.rows("mantle", "back", ["........", "........", "........", "........", "M.M.M.M."])
    s.whole("mantle", "M", faces=("top",))
    s.whole("collar", "M")
    s.fill(s.boxes["collar"], ["back", "front", "bottom"], "H", rows=[1])
    s.whole("clasp", "B")
    s.put(s.boxes["clasp"], "front", 0, 0, tweak="4")
    for cube in ("shoulder", "shoulder_slim"):
        drape(s, cube, "M", "H", ragged=False)
    return s.render({"W": WOOL, "L": WOOL.shifted(base=1, texture=gi.flat), "M": WOOL.shifted(base=3),
                     "H": WOOL.shifted(base=1, texture=gi.flat), "B": BRASS, "P": gi.HIDE})


@accessory("duskwing_cloak", {"V": DUSK, "L": DUSK.shifted(base=1), "F": DUSK.shifted(base=3), "D": DUSK.shifted(base=1),
                              "E": gi.ORCHID.shifted(base=1), "S": SILVER, "R": gi.RIFT}, [
    "................",
    "......EFFE......",
    ".....EFVVVD.....",
    "...EFVVVSSVVVD..",
    "..EFVVVVSSVVVVD.",
    ".EFVVVVVLLVVVVVD",
    ".EVVVVVVLLVVVVVD",
    ".EVVRVVVLLVVVVVD",
    ".EVVVVVVLLVVVRVD",
    "EFVVVVVLLLLVVVVD",
    "EVVVVVVLLLLVVVVD",
    "EVVVVVLLLLLLVVVD",
    "EVEVVE.LLLL.VVDV",
    "E.EV.E..LL..D.DD",
    "...E.E......D...",
    "................",
], [
    "................",
    "................",
    "................",
    ".........4......",
    "................",
    ".....-......-...",
    ".....-......-...",
    ".....-......-...",
    ".....-......-...",
    "....-........-..",
    "....-........-..",
    "................",
    "................",
    "................",
    "................",
    "................",
])
def duskwing_worn():
    s = Sheet("cloak")
    # Cape: night-black feathers, edge light, a jagged feather hem and a few caught stars.
    hem = [15, 13, 11, 14, 15, 15, 14, 11, 13, 15]  # each column's last row
    b = s.boxes["cape"]
    for face in ("front", "back"):
        for u in range(10):
            s.fill(b, [face], "V", rows=range(0, hem[u] + 1), cols=[u])
            s.put(b, face, u, hem[u], "E")
    s.fill(b, ["front"], "E", rows=range(2, 12), cols=[0, 9])
    s.fill(b, ["back"], "L", rows=range(0, 11), cols=range(1, 9))
    s.fill(b, ["right"], "V", rows=range(0, hem[9] + 1))
    s.fill(b, ["left"], "V", rows=range(0, hem[0] + 1))
    s.fill(b, ["top"], "V")
    s.fill(b, ["bottom"], "E", cols=[0, 4, 5, 9])
    for u, v in ((2, 4), (6, 7), (4, 9)):
        s.put(b, "front", u, v, "R")
    s.tweak_face(b, "front", lambda u, v, w, h: "-" if u in (2, 7) and 3 < v < 13 else None)
    # Mantle and collar: a ruff of feathers with lit tips, silver toggles.
    s.rows("mantle", "front", ["VV....VV",
                               "VV....VV",
                               "EV....VE",
                               "VE....EV",
                               "E......E"])
    s.fill(s.boxes["mantle"], ["right", "left", "back"], "V", rows=range(0, 4))
    s.rows("mantle", "back", ["........", "........", "........", "........", "E.E..E.E"])
    s.whole("mantle", "V", faces=("top",))
    s.whole("collar", "V")
    s.rows("collar", "back", ["EVEVVEVEVE", "VVVVVVVVVV"])
    s.rows("collar", "top", ["VEVEEVEVEV", "VVVVVVVVVV"])
    s.whole("clasp", "S")
    s.put(s.boxes["clasp"], "front", 0, 1, "R")
    for cube in ("shoulder", "shoulder_slim"):
        drape(s, cube, "V", "E", ragged=True)
    return s.render({"V": DUSK, "L": DUSK.shifted(base=3), "E": gi.ORCHID.shifted(base=1), "S": SILVER,
                     "R": gi.RIFT.shifted(base=3)})


# ---- belts
@accessory("sunwoven_sash", {"S": LINEN, "D": LINEN.shifted(base=1, texture=gi.flat), "G": gi.LEAF,
                             "K": LINEN.shifted(base=3, texture=gi.flat)}, [
    "................",
    "................",
    "....DDDDDDDD....",
    "..DDDGGGGGGDDD..",
    ".DDG........GDD.",
    ".SD..........DS.",
    ".SS..........SS.",
    ".GSS........SKKG",
    "..GSSS....SSKKKG",
    "...GGSSSSSSSKKG.",
    ".....GGGGGGSSKS.",
    "..........SSGSS.",
    "..........SG.GS.",
    ".........SSG..SS",
    ".........GG...GG",
    "................",
])
def sunwoven_worn():
    s = Sheet("belt")
    # Wheat-gold linen with a leaf-green edge, knotted on the right hip; the tail hangs along the thigh.
    s.whole("band", "S")
    s.band(s.boxes["band"], [0], "G")
    s.whole("knot", "K")
    for face in ("front", "back"):
        s.rows("tail", face, ["SS", "SS", "SS", "GG"])
    for face in ("right", "left"):
        s.rows("tail", face, ["S", "S", "S", "G"])
    s.rows("tail", "top", ["SS"])
    s.rows("tail", "bottom", ["GG"])
    s.tweaks("tail", "front", ["..", "-.", ".-", ".."])
    return s.render({"S": LINEN, "G": gi.LEAF.shifted(texture=gi.cloth), "K": LINEN.shifted(base=3)})


@accessory("tinker_belt", {"L": DARK_LEATHER, "D": DARK_LEATHER.shifted(base=1, texture=gi.flat), "C": gi.COPPER,
                           "K": gi.HANDLE, "I": gi.IRON, "P": DARK_LEATHER.shifted(base=3, texture=gi.flat)}, [
    "................",
    ".IIII...........",
    ".IIIIK..........",
    "...K............",
    "...K.DDDDDD.....",
    "..DKDD....DDDD..",
    ".DDK.........DD.",
    ".LPPP........LL.",
    "LPPPPP......LLL.",
    "LPPCPPL....LLL..",
    "LPPPPPLCCCCLL...",
    ".LPPPLLC..CL....",
    "..LLL.LCCCCL....",
    ".......LLLL.....",
    "................",
    "................",
], [
    "................",
    ".4..............",
    "................",
    "................",
    "................",
    "................",
    "................",
    "..4.............",
    ".4..............",
    "...4............",
    "........4.......",
    "................",
    "................",
    "................",
    "................",
    "................",
])
def tinker_worn():
    s = Sheet("belt")
    # Dark leather with stitched edges, a copper buckle (the strap shows through it), a pouch with a
    # hammer in it, and a little amber lamp hanging on a strap at the right hip.
    s.whole("band", "L")
    for face in ("front", "back"):
        s.tweak_face(s.boxes["band"], face, lambda u, v, w, h: "+" if v != 1 and u % 2 == 0 else None)
    s.whole("buckle", "B")
    for face in ("front", "back"):
        s.rows("buckle", face, ["BBBB", "B..B", "BBBB"])
    s.whole("pouch", "L")
    s.rows("pouch", "front", ["DDD", "DCD", "LLL"])
    s.rows("pouch", "top", ["DDD", "DDD"])
    s.whole("handle", "K")
    s.whole("head", "I")
    for face in ("front", "back"):
        s.rows("tail", face, ["LL", "LL", "AA", "CC"])
    for face in ("right", "left"):
        s.rows("tail", face, ["L", "L", "C", "C"])
    s.rows("tail", "bottom", ["CC"])
    return s.render({"L": DARK_LEATHER, "D": DARK_LEATHER.shifted(base=1, texture=gi.flat), "C": gi.COPPER,
                     "B": gi.COPPER.shifted(base=1, texture=gi.flat), "K": gi.HANDLE, "I": gi.IRON, "A": gi.AMBER})


# ---- bracers (icons: a pair, the left one in front, the right one behind and a shade darker)
@accessory("ironbark_bracers", {"O": BARK, "Q": BARK.shifted(base=3, texture=gi.flat), "M": gi.MOSS, "I": gi.IRON,
                                "D": BARK.shifted(base=1, texture=gi.flat), "P": BARK.shifted(base=1),
                                "R": BARK.shifted(base=2, texture=gi.flat), "N": gi.MOSS.shifted(base=1),
                                "J": gi.IRON.shifted(base=1), "B": BARK.shifted(base=0, texture=gi.flat)}, [
    "........RRRRR...",
    "........RPPPPR..",
    ".......RPPPPPPR.",
    "..QQQQ.RPPPPPR..",
    ".QOOOOQ.JPPPJ...",
    "QOOOOOOQPPPPP...",
    ".QOOOOO.NNNNN...",
    "..MMMMM.PPPPP...",
    "..OOOOO.PPPPP...",
    "..IOOOI.PPPPP...",
    "..OOOOO.BBBBB...",
    "..OOOOO.........",
    "..MMMMM.........",
    "..OOOOO.........",
    "..DDDDD.........",
    "................",
])
def ironbark_worn():
    s = Sheet("bracers")
    # Strips of bark bound with moss; an ironbark guard riveted on the outside.
    for cube in ("cuff", "cuff_slim"):
        s.whole(cube, "O")
        s.band(s.boxes[cube], [1], "M")
        s.fill(s.boxes[cube], ["top", "bottom"], "D")
    s.whole("plate", "O")
    s.rows("plate", "right", ["IOI", "OOO", "OMO", "OOO", "IOI"])
    return s.render({"O": BARK, "D": BARK.shifted(base=1, texture=gi.flat), "M": gi.MOSS, "I": gi.IRON})


@accessory("duelist_vambraces", {"E": gi.STEEL, "R": gi.CRIMSON, "U": gi.GOLD, "N": gi.STEEL.shifted(base=0, texture=gi.flat),
                                 "F": gi.STEEL.shifted(base=1), "S": gi.CRIMSON.shifted(base=1),
                                 "V": gi.GOLD.shifted(base=1, texture=gi.flat)}, [
    "........VVVVV...",
    "........VFFFFV..",
    ".......VFFFFFFV.",
    "..UUUU.VFFFFFV..",
    ".UEEEEU.FFFFF...",
    "UEEEEEEU.FFFF...",
    ".UEEEEE.SSSSS...",
    "..EEEEE.FFFFF...",
    "..RRRRR.FNFFF...",
    "..EEEEE.FFFFF...",
    "..EEEEE.VVVVV...",
    "..ENEEE.........",
    "..EEEEE.........",
    "..EEEEE.........",
    "..UUUUU.........",
    "................",
], [
    "................",
    "................",
    ".........4......",
    "................",
    "..4.............",
    ".4..............",
    "..4.............",
    "...4............",
    "................",
    "...4............",
    "...4............",
    "................",
    "...4............",
    "...4............",
    "................",
    "................",
])
def duelist_worn():
    s = Sheet("bracers")
    # Polished steel cuffs edged in gold over a crimson lining; the guard plate is nicked from parries.
    for cube in ("cuff", "cuff_slim"):
        s.whole(cube, "E")
        s.band(s.boxes[cube], [0, 3], "U")
        s.fill(s.boxes[cube], ["top", "bottom"], "R")
    s.whole("plate", "E")
    s.rows("plate", "right", ["UUU", "UEU", "UEU", "UEU", "UUU"])
    s.tweaks("plate", "right", ["...", ".4.", "...", "...", "..."])
    for face in ("front", "back"):
        s.rows("plate", face, ["U", "U", "N", "U", "N"])
    return s.render({"E": gi.STEEL.shifted(texture=gi.polish), "U": gi.GOLD.shifted(texture=gi.flat), "R": gi.CRIMSON,
                     "N": gi.STEEL.shifted(base=0, texture=gi.flat)})


# ---------------------------------------------------------------- slayer accessories (dropped by the slayer bosses)
SOCKET = M(["0c0604", "140a06", "1c0e08", "26160c", "34200f"], base=0)
SILK = M(["7e5c6c", "b890a4", "e6c8d6", "f8e8ef", "ffffff"], texture=gi.cloth, base=3)


# ---- amulets
@accessory("tyrants_amulet", {"C": gi.DARK_IRON.shifted(base=3, texture=gi.flat), "I": gi.IRON.shifted(base=2),
                              "D": gi.IRON.shifted(base=1, texture=gi.flat), "K": gi.DARK_IRON.shifted(base=0, texture=gi.flat),
                              "G": gi.PLAGUE.shifted(base=2), "H": gi.PLAGUE.shifted(base=4, texture=gi.flat)}, [
    "................",
    "..C..........C..",
    "..C..........C..",
    "...C........C...",
    "...C........C...",
    "....C......C....",
    ".....C....C.....",
    "......CCCC......",
    "....I..II..I....",
    "....II.II.II....",
    "....IKIIIIII....",
    "....IKIGGIII....",
    "....IIGHGGIK....",
    "....IIIGGIKI....",
    "....DDDDDDDD....",
    "................",
], [
    "................",
    "................",
    "................",
    "................",
    "................",
    "................",
    "................",
    "................",
    "....4...4..4....",
    "................",
    "................",
    "....4...........",
    "................",
    "................",
    "................",
    "................",
])
def tyrant_worn():
    s = Sheet("amulet")
    necklace(s, "C")
    # A broken iron crown with a plague gem in its brow.
    s.rows("pendant", "front", ["I..I",
                                "IGGI",
                                "KGHI",
                                "DDDD"])
    s.rows("pendant", "back", ["I..I", "IIII", "IKII", "DDDD"])
    s.rows("pendant", "top", ["IIII"])
    s.rows("pendant", "bottom", ["DDDD"])
    for face in ("right", "left"):
        s.rows("pendant", face, ["I", "I", "I", "D"])
    return s.render({"C": gi.DARK_IRON.shifted(base=3, texture=gi.flat), "I": gi.IRON.shifted(base=2),
                     "D": gi.IRON.shifted(base=1, texture=gi.flat), "K": gi.DARK_IRON.shifted(base=0, texture=gi.flat),
                     "G": gi.PLAGUE.shifted(base=2), "H": gi.PLAGUE.shifted(base=4, texture=gi.flat)})


# ---- cloaks
@accessory("broodweave_cloak", {"V": SILK, "L": SILK.shifted(base=2), "F": SILK.shifted(base=4), "D": SILK.shifted(base=1),
                                "E": SILK.shifted(base=1, texture=gi.flat), "S": gi.GOLD,
                                "R": gi.CRIMSON.shifted(base=3, texture=gi.flat),
                                "B": gi.CRIMSON.shifted(base=1, texture=gi.flat)}, [
    "................",
    "......EFFE......",
    ".....EFVVVD.....",
    "...EFVVVSSVVVD..",
    "..EFVVVVSSVVVVD.",
    ".EFVVVVVVVVVVVVD",
    ".EVVVRVVVVRVVVVD",
    ".EVVVVRBBRVVVVVD",
    ".EVVVRRBBRRVVVVD",
    "EFVVVRVVVVRVVVLD",
    "EVVVVVVLLLLVVVVD",
    "EVVVVVLLLLLLVVVD",
    "EVEVVE.LLLL.VVDV",
    "E.EV.E..LL..D.DD",
    "...E.E......D...",
    "................",
], [
    "................",
    "................",
    "................",
    ".........4......",
    "................",
    ".....4......4...",
    ".....4......4...",
    "................",
    "................",
    "....-........-..",
    "....-........-..",
    "................",
    "................",
    "................",
    "................",
    "................",
])
def broodweave_worn():
    s = Sheet("cloak")
    # Cape: pale silk with a soft rose hem, folds down the back and a red spider sigil between the shoulders.
    hem = [15, 15, 14, 15, 15, 15, 15, 14, 15, 15]
    b = s.boxes["cape"]
    for face in ("front", "back"):
        for u in range(10):
            s.fill(b, [face], "V", rows=range(0, hem[u] + 1), cols=[u])
            s.put(b, face, u, hem[u], "E")
    s.fill(b, ["front"], "E", rows=range(2, 14), cols=[0, 9])
    s.fill(b, ["back"], "L", rows=range(0, 12), cols=range(1, 9))
    s.fill(b, ["right"], "V", rows=range(0, hem[9] + 1))
    s.fill(b, ["left"], "V", rows=range(0, hem[0] + 1))
    s.fill(b, ["top"], "V")
    s.fill(b, ["bottom"], "E")
    for u, v in ((2, 3), (7, 3), (3, 4), (6, 4), (2, 5), (3, 5), (6, 5), (7, 5), (2, 7), (7, 7)):
        s.put(b, "front", u, v, "R")
    for u, v in ((4, 4), (5, 4), (4, 5), (5, 5)):
        s.put(b, "front", u, v, "B")
    s.tweak_face(b, "front", lambda u, v, w, h: "-" if u in (2, 7) and 9 < v < 14 else None)
    s.rows("mantle", "front", ["VV....VV",
                               "VV....VV",
                               "EV....VE",
                               "VE....EV",
                               "E......E"])
    s.fill(s.boxes["mantle"], ["right", "left", "back"], "V", rows=range(0, 4))
    s.rows("mantle", "back", ["........", "........", "........", "........", "E.E..E.E"])
    s.whole("mantle", "V", faces=("top",))
    s.whole("collar", "V")
    s.rows("collar", "back", ["EVEVVEVEVE", "VVVVVVVVVV"])
    s.rows("collar", "top", ["VEVEEVEVEV", "VVVVVVVVVV"])
    s.whole("clasp", "S")
    s.put(s.boxes["clasp"], "front", 0, 1, "R")
    for cube in ("shoulder", "shoulder_slim"):
        drape(s, cube, "V", "E", ragged=False)
    return s.render({"V": SILK, "L": SILK.shifted(base=2), "E": SILK.shifted(base=1, texture=gi.flat), "S": gi.GOLD,
                     "R": gi.CRIMSON.shifted(base=3, texture=gi.flat), "B": gi.CRIMSON.shifted(base=1, texture=gi.flat)})


# ---- belts
@accessory("marshals_belt", {"L": DARK_LEATHER, "D": DARK_LEATHER.shifted(base=1, texture=gi.flat),
                             "R": gi.CRIMSON.shifted(base=2, texture=gi.flat), "G": gi.GOLD,
                             "K": SOCKET.shifted(texture=gi.flat), "H": gi.GOLD.shifted(base=4, texture=gi.flat)}, [
    "................",
    "................",
    "................",
    "................",
    ".....GGGGGG.....",
    "....GHGGGGGG....",
    "RRRRGGGGGGGGRRRR",
    "LLLLGKKGGKKGLLLL",
    "LLLLGKKGGKKGLLLL",
    "RRRRGGGKKGGGRRRR",
    "DDDD.GKGGKG.DDDD",
    ".....GGGGGG.....",
    "......GGGG......",
    "................",
    "................",
    "................",
])
def marshal_worn():
    s = Sheet("belt")
    # Dark leather with red piping top and bottom and a gold skull for a buckle.
    s.whole("band", "L")
    for face in ("front", "back", "right", "left"):
        w = s.boxes["band"][face][2]
        s.rows("band", face, ["R" * w, "L" * w, "R" * w])
    s.whole("buckle", "G")
    s.rows("buckle", "front", ["KGGK", "GKKG", "GKGK"])
    s.rows("buckle", "top", ["HHHH"])
    s.whole("knot", "D")
    return s.render({"L": DARK_LEATHER, "D": DARK_LEATHER.shifted(base=1, texture=gi.flat),
                     "R": gi.CRIMSON.shifted(base=2, texture=gi.flat), "G": gi.GOLD,
                     "K": SOCKET.shifted(texture=gi.flat), "H": gi.GOLD.shifted(base=4, texture=gi.flat)})


# ---- bracers
@accessory("voidbound_bracers", {"E": gi.VOID_INDIGO.shifted(base=3), "U": gi.VOID.shifted(base=2, texture=gi.flat),
                                 "C": gi.VOID_CYAN.shifted(base=3, texture=gi.flat), "F": gi.VOID_INDIGO.shifted(base=2),
                                 "Q": gi.VOID_CYAN.shifted(base=1, texture=gi.flat), "V": gi.VOID.shifted(base=1, texture=gi.flat),
                                 "N": gi.VOID_INDIGO.shifted(base=4, texture=gi.flat)}, [
    "........VVVVV...",
    "........VFFFFV..",
    ".......VFFFFFFV.",
    "..UUUU.VFFFQFV..",
    ".UEEEEU.FFQFF...",
    "UEEECEEU.FFFF...",
    ".UECCEE.FQQFF...",
    "..EECEE.FFQFF...",
    "..CCCCC.FFFFF...",
    "..EECEE.FQFFF...",
    "..ECCEE.VVVVV...",
    "..EECEE.........",
    "..ECEEE.........",
    "..EEEEE.........",
    "..UUUUU.........",
    "................",
], [
    "................",
    "................",
    ".........4......",
    "................",
    "..4.............",
    ".1..............",
    "..4.............",
    "...4............",
    "................",
    "................",
    "................",
    "...4............",
    "................",
    "................",
    "................",
    "................",
])
def voidbound_worn():
    s = Sheet("bracers")
    # Dark indigo cuffs rimmed in black, a glowing cyan rune on each, and a faintly lit guard plate.
    for cube in ("cuff", "cuff_slim"):
        s.whole(cube, "E")
        s.band(s.boxes[cube], [0, 3], "U")
        s.fill(s.boxes[cube], ["top", "bottom"], "U")
        if s.boxes[cube]["front"][2] == 4:
            s.rows(cube, "front", ["....", "CEC.", ".C..", "...."])
            s.rows(cube, "back", ["....", ".CEC", "..C.", "...."])
        else:
            s.rows(cube, "front", ["...", "CEC", ".C.", "..."])
            s.rows(cube, "back", ["...", "CEC", ".C.", "..."])
    s.whole("plate", "E")
    s.rows("plate", "right", ["UUU", "UCU", "UEU", "UCU", "UUU"])
    for face in ("front", "back"):
        s.rows("plate", face, ["U", "C", "E", "C", "U"])
    return s.render({"E": gi.VOID_INDIGO.shifted(texture=gi.polish), "U": gi.VOID.shifted(base=2, texture=gi.flat),
                     "C": gi.VOID_CYAN.shifted(base=3, texture=gi.flat)})


# ---------------------------------------------------------------- dungeon accessories (world dungeon boss drops)
WIDOW_SILK = M(["08080e", "14141e", "24243a", "3c3c58", "5a5a7c"], texture=gi.cloth)
WEB = M(["7a8090", "a8aebe", "d0d6e2", "eceff6", "ffffff"], base=3)


@accessory("widowsilk_cloak", {"V": WIDOW_SILK, "L": WIDOW_SILK.shifted(base=3), "F": WIDOW_SILK.shifted(base=4),
                               "D": WIDOW_SILK.shifted(base=1), "E": WIDOW_SILK.shifted(base=1, texture=gi.flat),
                               "S": WEB, "R": gi.VENOM.shifted(base=3, texture=gi.flat),
                               "B": gi.VENOM.shifted(base=1, texture=gi.flat)}, [
    "................",
    "......EFFE......",
    ".....EFVVVD.....",
    "...EFVVVSSVVVD..",
    "..EFVVVVSSVVVVD.",
    ".EFVVSVVSSVVSVVD",
    ".EVVSVVRSSRVVSVD",
    ".EVSVVVRBBRVVVSD",
    ".EVVSVVRBBRVVSVD",
    "EFVVVSVVRRVVSVLD",
    "EVVVVVSVVVVSVVVD",
    "EVVVVVLSVVSLVVVD",
    "EVEVVE.LSSL.VVDV",
    "E.EV.E..LL..D.DD",
    "...E.E......D...",
    "................",
], [
    "................",
    "................",
    "................",
    ".........4......",
    "................",
    ".....-......-...",
    ".....-......-...",
    "................",
    "................",
    "....-........-..",
    "....-........-..",
    "................",
    "................",
    "................",
    "................",
    "................",
])
def widowsilk_worn():
    s = Sheet("cloak")
    # Cape: black widow silk with a ragged hem, a pale web across the shoulders and a venom-green hourglass.
    hem = [15, 14, 15, 13, 15, 15, 14, 15, 13, 15]
    b = s.boxes["cape"]
    for face in ("front", "back"):
        for u in range(10):
            s.fill(b, [face], "V", rows=range(0, hem[u] + 1), cols=[u])
            s.put(b, face, u, hem[u], "E")
    s.fill(b, ["front"], "E", rows=range(2, 14), cols=[0, 9])
    s.fill(b, ["back"], "L", rows=range(0, 12), cols=range(1, 9))
    s.fill(b, ["right"], "V", rows=range(0, hem[9] + 1))
    s.fill(b, ["left"], "V", rows=range(0, hem[0] + 1))
    s.fill(b, ["top"], "V")
    s.fill(b, ["bottom"], "E")
    for u in range(10):
        s.put(b, "back", u, 2 + (u % 3 == 1), "S")
    for v in range(2, 12, 3):
        for u in range(1, 9):
            if (u + v) % 2 == 0:
                s.put(b, "back", u, v, "S")
    for u, v in ((4, 5), (5, 5), (4, 8), (5, 8), (5, 6), (4, 6), (5, 7), (4, 7)):
        s.put(b, "back", u, v, "R" if v in (5, 8) else "B")
    s.tweak_face(b, "front", lambda u, v, w, h: "-" if u in (2, 7) and 9 < v < 14 else None)
    s.rows("mantle", "front", ["VV....VV",
                               "VV....VV",
                               "EV....VE",
                               "VE....EV",
                               "E......E"])
    s.fill(s.boxes["mantle"], ["right", "left", "back"], "V", rows=range(0, 4))
    s.rows("mantle", "back", ["........", "........", "........", "........", "E.E..E.E"])
    s.whole("mantle", "V", faces=("top",))
    s.whole("collar", "V")
    s.rows("collar", "back", ["EVEVVEVEVE", "VVVVVVVVVV"])
    s.rows("collar", "top", ["VEVEEVEVEV", "VVVVVVVVVV"])
    s.whole("clasp", "S")
    s.put(s.boxes["clasp"], "front", 0, 1, "R")
    for cube in ("shoulder", "shoulder_slim"):
        drape(s, cube, "V", "E", ragged=True)
    return s.render({"V": WIDOW_SILK, "L": WIDOW_SILK.shifted(base=3), "E": WIDOW_SILK.shifted(base=1, texture=gi.flat), "S": WEB,
                     "R": gi.VENOM.shifted(base=3, texture=gi.flat), "B": gi.VENOM.shifted(base=1, texture=gi.flat)})


PEARL_SETTING = M(["3a2a0c", "6a501c", "a07e30", "d0a850", "f4dc90"], texture=gi.sheen)
TIDE_CORD = M(["0a2a30", "124a52", "1c6e74", "2c9496", "4cc0b8"])


@accessory("tidemother_pendant", {"C": TIDE_CORD, "S": PEARL_SETTING, "P": gi.CORAL_TEAL.shifted(base=3, texture=gi.flat),
                                  "Q": gi.CORAL_TEAL.shifted(base=4, texture=gi.flat),
                                  "R": gi.CORAL_TEAL.shifted(base=1, texture=gi.flat)}, [
    "................",
    "..C..........C..",
    "..C..........C..",
    "...C........C...",
    "...C........C...",
    "....C......C....",
    ".....C....C.....",
    "......CSSC......",
    ".....SSSSSS.....",
    "....SPPQQPPS....",
    "....SPQQQPPS....",
    "....SPQQPPRS....",
    "....SPPPPRRS....",
    ".....SRRRRS.....",
    "......SSSS......",
    "................",
], [
    "................",
    "................",
    "................",
    "................",
    "................",
    "................",
    "................",
    "................",
    "................",
    "................",
    ".....4..........",
    "................",
    "................",
    "................",
    "................",
    "................",
])
def tidemother_worn():
    s = Sheet("amulet")
    necklace(s, "C")
    # A big pearl in a gold claw setting.
    s.rows("pendant", "front", [".SS.",
                                "PQQP",
                                "PQPR",
                                ".SS."])
    s.tweaks("pendant", "front", ["....",
                                  ".4..",
                                  "....",
                                  "...."])
    s.rows("pendant", "back", [".SS.", "PPPP", "PPRR", ".SS."])
    s.rows("pendant", "top", [".SS."])
    s.rows("pendant", "bottom", [".SS."])
    for face in ("right", "left"):
        s.rows("pendant", face, [".", "P", "R", "."])
    return s.render({"C": TIDE_CORD, "S": PEARL_SETTING, "P": gi.CORAL_TEAL.shifted(base=3, texture=gi.flat),
                     "Q": gi.CORAL_TEAL.shifted(base=4, texture=gi.flat), "R": gi.CORAL_TEAL.shifted(base=1, texture=gi.flat)})


# ---------------------------------------------------------------- danger-region accessories (crafted from region essences)
BLIGHT_CORD = M(["1a0610", "34101e", "56182c", "7c2640", "a8405c"])
FROST_WEAVE = M(["2a4058", "4a6c8c", "7098b8", "a0c4de", "d4ecfa"], texture=gi.cloth)
RIME_TRIM = M(["6a8aa8", "98b8d2", "c4dcee", "e4f2fa", "ffffff"], base=3)
ASH_PLATE = M(["0a0808", "1a1514", "2c2424", "443a3a", "625656"], texture=gi.sheen)
BOG_LEATHER = M(["10160c", "202c14", "34441e", "4c6228", "6c8438"], texture=gi.stitch)
BOG_SLIME = M(["1c4a0c", "3a7a1c", "62b02c", "8ee03c", "c4f878"], base=3)


@accessory("blightheart_amulet", {"C": BLIGHT_CORD, "S": gi.BLIGHT_STEEL.shifted(base=2, texture=gi.flat),
                                  "P": gi.CRIMSON.shifted(base=3, texture=gi.flat), "Q": gi.CRIMSON.shifted(base=4, texture=gi.flat),
                                  "R": gi.CRIMSON.shifted(base=1, texture=gi.flat)}, [
    "................",
    "..C..........C..",
    "..C..........C..",
    "...C........C...",
    "...C........C...",
    "....C......C....",
    ".....C....C.....",
    "....SSSSSSSS....",
    "...SPPQSSPPPS...",
    "...SPQQPPPPPS...",
    "...SPPPPPPRRS...",
    "....SPPPPRRS....",
    ".....SPPRRS.....",
    "......SRRS......",
    ".......SS.......",
    "................",
], [
    "................",
    "................",
    "................",
    "................",
    "................",
    "................",
    "................",
    "................",
    "................",
    "................",
    "................",
    "................",
    "................",
    "................",
    "................",
    "................",
])
def blightheart_worn():
    s = Sheet("amulet")
    necklace(s, "C")
    # A crimson heart of blight crystal in a dark iron claw setting.
    s.rows("pendant", "front", ["SSSS",
                                "PQPS",
                                "SPRS",
                                ".SS."])
    s.tweaks("pendant", "front", ["....",
                                  ".4..",
                                  "....",
                                  "...."])
    s.rows("pendant", "back", ["SSSS", "PPPP", "PRRR", ".SS."])
    s.rows("pendant", "top", [".SS."])
    s.rows("pendant", "bottom", [".SS."])
    for face in ("right", "left"):
        s.rows("pendant", face, ["S", "P", "R", "."])
    return s.render({"C": BLIGHT_CORD, "S": gi.BLIGHT_STEEL.shifted(base=2, texture=gi.flat),
                     "P": gi.CRIMSON.shifted(base=3, texture=gi.flat), "Q": gi.CRIMSON.shifted(base=4, texture=gi.flat),
                     "R": gi.CRIMSON.shifted(base=1, texture=gi.flat)})


@accessory("frostwoven_cloak", {"V": FROST_WEAVE, "L": FROST_WEAVE.shifted(base=3), "F": FROST_WEAVE.shifted(base=4),
                                "D": FROST_WEAVE.shifted(base=1), "E": FROST_WEAVE.shifted(base=1, texture=gi.flat),
                                "S": RIME_TRIM, "R": gi.RIME_STEEL.shifted(base=3, texture=gi.flat),
                                "B": gi.FROSTBERRY.shifted(base=2, texture=gi.flat)}, [
    "................",
    "......EFFE......",
    ".....EFVVVD.....",
    "...EFVVVSSVVVD..",
    "..EFVVVVSSVVVVD.",
    ".EFVVVVVVVVVVVVD",
    ".EVVVRVVVVRVVVVD",
    ".EVVVVRBBRVVVVVD",
    ".EVVVRRBBRRVVVVD",
    "EFVVVRVVVVRVVVLD",
    "EVVVVVVLLLLVVVVD",
    "EVVVVVLLLLLLVVVD",
    "ESEVVE.LLLL.VVSV",
    "E.ES.E..LL..D.SD",
    "...E.E......D...",
    "................",
], None)
def frostwoven_worn():
    s = Sheet("cloak")
    # Cape: frost-blue weave with a jagged rime hem, pale ice crystals down the back and a berry-blue clasp.
    hem = [15, 13, 15, 14, 15, 13, 15, 14, 13, 15]
    b = s.boxes["cape"]
    for face in ("front", "back"):
        for u in range(10):
            s.fill(b, [face], "V", rows=range(0, hem[u] + 1), cols=[u])
            s.put(b, face, u, hem[u], "S")
    s.fill(b, ["front"], "E", rows=range(2, 14), cols=[0, 9])
    s.fill(b, ["back"], "L", rows=range(0, 12), cols=range(1, 9))
    s.fill(b, ["right"], "V", rows=range(0, hem[9] + 1))
    s.fill(b, ["left"], "V", rows=range(0, hem[0] + 1))
    s.fill(b, ["top"], "V")
    s.fill(b, ["bottom"], "E")
    for u, v in ((4, 3), (5, 3), (3, 4), (4, 4), (5, 4), (6, 4), (4, 5), (5, 5), (4, 9), (5, 9), (3, 10), (6, 10), (4, 11), (5, 11)):
        s.put(b, "back", u, v, "R")
    for u in range(10):
        s.put(b, "back", u, 13 if u % 2 else 14, "S")
    s.tweak_face(b, "front", lambda u, v, w, h: "-" if u in (2, 7) and 9 < v < 14 else None)
    s.rows("mantle", "front", ["VV....VV",
                               "VV....VV",
                               "EV....VE",
                               "VE....EV",
                               "E......E"])
    s.fill(s.boxes["mantle"], ["right", "left", "back"], "V", rows=range(0, 4))
    s.rows("mantle", "back", ["........", "........", "........", "........", "S.S..S.S"])
    s.whole("mantle", "S", faces=("top",))
    s.whole("collar", "V")
    s.rows("collar", "back", ["SVSVVSVSVS", "VVVVVVVVVV"])
    s.rows("collar", "top", ["VSVSSVSVSV", "VVVVVVVVVV"])
    s.whole("clasp", "S")
    s.put(s.boxes["clasp"], "front", 0, 1, "B")
    for cube in ("shoulder", "shoulder_slim"):
        drape(s, cube, "V", "S", ragged=True)
    return s.render({"V": FROST_WEAVE, "L": FROST_WEAVE.shifted(base=3), "E": FROST_WEAVE.shifted(base=1, texture=gi.flat), "S": RIME_TRIM,
                     "R": gi.RIME_STEEL.shifted(base=3, texture=gi.flat), "B": gi.FROSTBERRY.shifted(base=2, texture=gi.flat)})


@accessory("ashen_bracers", {"E": ASH_PLATE, "U": gi.ASH_STEEL.shifted(base=1, texture=gi.flat),
                             "C": gi.EMBERITE.shifted(base=3, texture=gi.flat), "F": ASH_PLATE.shifted(base=3),
                             "Q": gi.EMBERITE.shifted(base=1, texture=gi.flat), "V": gi.ASH_STEEL.shifted(base=0, texture=gi.flat),
                             "N": gi.EMBERITE.shifted(base=4, texture=gi.flat)}, [
    "........VVVVV...",
    "........VFFFFV..",
    ".......VFFFFFFV.",
    "..UUUU.VFFFQFV..",
    ".UEEEEU.FFQFF...",
    "UEEECEEU.FFFF...",
    ".UECCEE.FQQFF...",
    "..EECEE.FFQFF...",
    "..CCCCC.FFFFF...",
    "..EECEE.FQFFF...",
    "..ECCEE.VVVVV...",
    "..EECEE.........",
    "..ECEEE.........",
    "..EEEEE.........",
    "..UUUUU.........",
    "................",
], [
    "................",
    "................",
    ".........4......",
    "................",
    "..4.............",
    ".1..............",
    "..4.............",
    "...4............",
    "................",
    "................",
    "................",
    "...4............",
    "................",
    "................",
    "................",
    "................",
])
def ashen_worn():
    s = Sheet("bracers")
    # Blackened ash-iron cuffs with a glowing ember crack on each, and a scorched guard plate.
    for cube in ("cuff", "cuff_slim"):
        s.whole(cube, "E")
        s.band(s.boxes[cube], [0, 3], "U")
        s.fill(s.boxes[cube], ["top", "bottom"], "U")
        if s.boxes[cube]["front"][2] == 4:
            s.rows(cube, "front", ["....", "CEC.", ".C..", "...."])
            s.rows(cube, "back", ["....", ".CEC", "..C.", "...."])
        else:
            s.rows(cube, "front", ["...", "CEC", ".C.", "..."])
            s.rows(cube, "back", ["...", "CEC", ".C.", "..."])
    s.whole("plate", "E")
    s.rows("plate", "right", ["UUU", "UCU", "UEU", "UCU", "UUU"])
    for face in ("front", "back"):
        s.rows("plate", face, ["U", "C", "E", "C", "U"])
    return s.render({"E": ASH_PLATE, "U": gi.ASH_STEEL.shifted(base=1, texture=gi.flat),
                     "C": gi.EMBERITE.shifted(base=3, texture=gi.flat)})


@accessory("bog_belt", {"L": BOG_LEATHER, "D": BOG_LEATHER.shifted(base=1, texture=gi.flat),
                        "R": BOG_SLIME.shifted(base=1, texture=gi.flat), "G": gi.BOG_STEEL.shifted(base=3),
                        "K": BOG_SLIME.shifted(base=2, texture=gi.flat), "H": BOG_SLIME.shifted(base=4, texture=gi.flat)}, [
    "................",
    "................",
    "................",
    "................",
    ".....GGGGGG.....",
    "....GHGGGGGG....",
    "RRRRGGKKKKGGRRRR",
    "LLLLGKHKKHKGLLLL",
    "LLLLGKKKKKKGLLLL",
    "RRRRGGKKKKGGRRRR",
    "DDDD.GGKKGG.DDDD",
    ".....GGGGGG.....",
    "......GGGG......",
    "................",
    "................",
    "................",
])
def bog_worn():
    s = Sheet("belt")
    # Peat-dark leather with slime-green piping and a steel buckle holding a glob of Mire slime.
    s.whole("band", "L")
    for face in ("front", "back", "right", "left"):
        w = s.boxes["band"][face][2]
        s.rows("band", face, ["R" * w, "L" * w, "R" * w])
    s.whole("buckle", "G")
    s.rows("buckle", "front", ["GKKG", "KHKK", "GKKG"])
    s.rows("buckle", "top", ["HHHH"])
    s.whole("knot", "D")
    return s.render({"L": BOG_LEATHER, "D": BOG_LEATHER.shifted(base=1, texture=gi.flat),
                     "R": BOG_SLIME.shifted(base=1, texture=gi.flat), "G": gi.BOG_STEEL.shifted(base=3),
                     "K": BOG_SLIME.shifted(base=2, texture=gi.flat), "H": BOG_SLIME.shifted(base=4, texture=gi.flat)})


SLOT_OF = {"emberstone_pendant": "amulet", "starglass_amulet": "amulet", "wayfarer_mantle": "cloak",
           "duskwing_cloak": "cloak", "sunwoven_sash": "belt", "tinker_belt": "belt",
           "ironbark_bracers": "bracers", "duelist_vambraces": "bracers",
           "tyrants_amulet": "amulet", "broodweave_cloak": "cloak", "marshals_belt": "belt",
           "voidbound_bracers": "bracers", "widowsilk_cloak": "cloak", "tidemother_pendant": "amulet",
           "blightheart_amulet": "amulet", "ashen_bracers": "bracers", "frostwoven_cloak": "cloak", "bog_belt": "belt"}


# ---------------------------------------------------------------- output
def build():
    icons, worn = {}, {}
    for name, (mask, mats, tweak, paint) in ACCESSORIES.items():
        icons[name] = gi.render(mask, mats, tweak)
        worn[name] = paint()
        for path, img in ((f"textures/item/{name}.png", icons[name]), (f"textures/entity/accessory/{name}.png", worn[name])):
            out = gi.ITEM_ASSETS / path
            out.parent.mkdir(parents=True, exist_ok=True)
            img.save(out)
        gi.write_json(gi.ITEM_ASSETS / "items" / f"{name}.json",
                      {"model": {"type": "minecraft:model", "model": f"skyrpg_items:item/{name}"}})
        gi.write_json(gi.ITEM_ASSETS / "models" / "item" / f"{name}.json",
                      {"parent": "minecraft:item/generated", "textures": {"layer0": f"skyrpg_items:item/{name}"}})
    return icons, worn


# ---------------------------------------------------------------- previews
# A small software renderer for sanity checks: real cube geometry with vanilla's UV mapping
# (ModelPart.Cube), on a stand-in player, optionally in SkyRPG armour, drawn with a painter's sort.
SKIN, HAIR, SHIRT, PANTS, SHOES = (198, 150, 118), (74, 52, 36), (64, 104, 118), (52, 60, 112), (78, 78, 84)


def stand_in_skin(slim):
    """A plain 64x64 player skin: hair, eyes, shirt, trousers, shoes."""
    img = Image.new("RGBA", (64, 64), (0, 0, 0, 0))

    def paint(b, color, faces=("top", "bottom") + gi.SIDES, rows=None):
        for face in faces:
            x0, y0, w, h = b[face]
            for v in rows if rows is not None else range(h):
                for u in range(w):
                    if v < h:
                        img.putpixel((x0 + u, y0 + v), color + (255,))

    head = gi.box(0, 0, 8, 8, 8)
    paint(head, SKIN)
    paint(head, HAIR, ["top"])
    paint(head, HAIR, gi.SIDES, rows=[0, 1])
    paint(head, HAIR, ["back"], rows=range(0, 6))
    x0, y0, _, _ = head["front"]
    for u, color in ((1, (240, 240, 240)), (2, (60, 70, 140)), (5, (60, 70, 140)), (6, (240, 240, 240))):
        img.putpixel((x0 + u, y0 + 4), color + (255,))
    for u in (3, 4):
        img.putpixel((x0 + u, y0 + 6), (150, 90, 80, 255))
    paint(gi.box(16, 16, 8, 12, 4), SHIRT)
    arm_w = 3 if slim else 4
    for b in (gi.box(40, 16, arm_w, 12, 4), gi.box(32, 48, arm_w, 12, 4)):
        paint(b, SKIN)
        paint(b, SHIRT, ["top"] + list(gi.SIDES), rows=range(0, 4))
    for b in (gi.box(0, 16, 4, 12, 4), gi.box(16, 48, 4, 12, 4)):
        paint(b, PANTS)
        paint(b, SHOES, gi.SIDES, rows=range(9, 12))
        paint(b, SHOES, ["bottom"])
    return img


def mat_mul(a, b):
    return tuple(tuple(sum(a[i][k] * b[k][j] for k in range(3)) for j in range(3)) for i in range(3))


def mat_vec(a, p):
    return tuple(sum(a[i][k] * p[k] for k in range(3)) for i in range(3))


def rotation(x=0.0, y=0.0, z=0.0):
    """JOML's rotationZYX(z, y, x), as ModelPart uses it."""
    cx, sx, cy, sy, cz, sz = math.cos(x), math.sin(x), math.cos(y), math.sin(y), math.cos(z), math.sin(z)
    rx = ((1, 0, 0), (0, cx, -sx), (0, sx, cx))
    ry = ((cy, 0, sy), (0, 1, 0), (-sy, 0, cy))
    rz = ((cz, -sz, 0), (sz, cz, 0), (0, 0, 1))
    return mat_mul(mat_mul(rz, ry), rx)


PIVOTS = {"head": (0, 0, 0), "body": (0, 0, 0), "right_arm": (-5, 2, 0), "left_arm": (5, 2, 0),
          "right_leg": (-1.9, 12, 0), "left_leg": (1.9, 12, 0)}


def cube_quads(cube, texture, transform):
    """(corners, colour, normal) per opaque texel of a cube, in model space. Mirrors ModelPart.Cube."""
    (x0, y0, z0), (w, h, d), (tu, tv) = cube.pos, cube.size, cube.uv
    gx, gy, gz = cube.grow
    lo = [x0 - gx, y0 - gy, z0 - gz]
    hi = [x0 + w + gx, y0 + h + gy, z0 + d + gz]
    if cube.mirror:
        lo[0], hi[0] = hi[0], lo[0]
    t0, t1, t2, t3 = (lo[0], lo[1], lo[2]), (hi[0], lo[1], lo[2]), (hi[0], hi[1], lo[2]), (lo[0], hi[1], lo[2])
    l0, l1, l2, l3 = (lo[0], lo[1], hi[2]), (hi[0], lo[1], hi[2]), (hi[0], hi[1], hi[2]), (lo[0], hi[1], hi[2])
    u0, u1, u2, u22, u3, u4 = tu, tu + d, tu + d + w, tu + d + w + w, tu + d + w + d, tu + d + w + d + w
    v0, v1, v2 = tv, tv + d, tv + d + h
    polygons = [((l1, l0, t0, t1), (u1, v0, u2, v1)), ((t2, t3, l3, l2), (u2, v1, u22, v0)),
                ((t0, l0, l3, t3), (u0, v1, u1, v2)), ((t1, t0, t3, t2), (u1, v1, u2, v2)),
                ((l1, t1, t2, l2), (u2, v1, u3, v2)), ((l0, l1, l2, l3), (u3, v1, u4, v2))]
    rot, offset = transform
    out = []
    for verts, (pu0, pv0, pu1, pv1) in polygons:
        if pu0 == pu1 or pv0 == pv1:
            continue
        a, b, c = verts[1], verts[0], verts[2]  # at (pu0, pv0), (pu1, pv0), (pu0, pv1)

        def at(u, v):
            su, sv = (u - pu0) / (pu1 - pu0), (v - pv0) / (pv1 - pv0)
            p = tuple(a[i] + su * (b[i] - a[i]) + sv * (c[i] - a[i]) for i in range(3))
            q = mat_vec(rot, p)
            return tuple(q[i] + offset[i] for i in range(3))

        e1 = tuple(b[i] - a[i] for i in range(3))
        e2 = tuple(c[i] - a[i] for i in range(3))
        n = (e1[1] * e2[2] - e1[2] * e2[1], e1[2] * e2[0] - e1[0] * e2[2], e1[0] * e2[1] - e1[1] * e2[0])
        n = mat_vec(rot, n)
        length = math.sqrt(sum(k * k for k in n)) or 1
        n = tuple(k / length for k in n)
        for tvv in range(int(min(pv0, pv1)), int(max(pv0, pv1))):
            for tuu in range(int(min(pu0, pu1)), int(max(pu0, pu1))):
                color = texture.getpixel((tuu, tvv))
                if color[3] == 0:
                    continue
                out.append(((at(tuu, tvv), at(tuu + 1, tvv), at(tuu + 1, tvv + 1), at(tuu, tvv + 1)), color, n))
    return out


def player_cubes(slim):
    arm = 3 if slim else 4
    return [Cube("head", "head", (-4, -8, -4), (8, 8, 8), (0, 0)),
            Cube("body", "body", (-4, 0, -2), (8, 12, 4), (16, 16)),
            Cube("right_arm", "arm", (-2 if slim else -3, -2, -2), (arm, 12, 4), (40, 16)),
            Cube("left_arm", "arm", (-1, -2, -2), (arm, 12, 4), (32, 48)),
            Cube("right_leg", "leg", (-2, 0, -2), (4, 12, 4), (0, 16)),
            Cube("left_leg", "leg", (-2, 0, -2), (4, 12, 4), (16, 48))]


def armour_cubes():
    """(cube, layer) for a full set, like HumanoidArmorLayer: outer layer 1.0 out, leggings 0.5, boots 0.9."""
    def part(name, pos, size, uv, grow, mirror=False):
        return Cube(name, name, pos, size, uv, (grow, grow, grow), mirror)
    return [(part("head", (-4, -8, -4), (8, 8, 8), (0, 0), 1.0), "humanoid"),
            (part("body", (-4, 0, -2), (8, 12, 4), (16, 16), 1.0), "humanoid"),
            (part("right_arm", (-3, -2, -2), (4, 12, 4), (40, 16), 1.0), "humanoid"),
            (part("left_arm", (-1, -2, -2), (4, 12, 4), (40, 16), 1.0, True), "humanoid"),
            (part("body", (-4, 0, -2), (8, 12, 4), (16, 16), 0.5), "humanoid_leggings"),
            (part("right_leg", (-2, 0, -2), (4, 12, 4), (0, 16), 0.4), "humanoid_leggings"),
            (part("left_leg", (-2, 0, -2), (4, 12, 4), (0, 16), 0.4, True), "humanoid_leggings"),
            (part("right_leg", (-2, 0, -2), (4, 12, 4), (0, 16), 0.9), "humanoid"),
            (part("left_leg", (-2, 0, -2), (4, 12, 4), (0, 16), 0.9, True), "humanoid")]


def figure_quads(outfit, worn, slim=False, armour=None, pose=None):
    """Every textured quad of a player wearing `outfit` (accessory ids), optionally a SkyRPG armour set."""
    pose = pose or {}
    quads = []

    def transform(part, cube=None):
        rot = rotation(*pose.get(part, (0, 0, 0)))
        offset = PIVOTS[part]
        if cube is not None and cube.pivot:
            # The cape: turned round, then swung out (AccessoryModel.sway with no lean is 6 degrees).
            swing = rotation(x=math.radians(pose.get("cape", 6.0)))
            offset = tuple(o + c for o, c in zip(offset, mat_vec(rot, cube.pivot)))
            rot = mat_mul(rot, mat_mul(swing, rotation(y=math.pi)))
        return rot, offset

    skin = stand_in_skin(slim)
    for cube in player_cubes(slim):
        quads += cube_quads(cube, skin, transform(cube.part))
    if armour:
        layers = {layer: Image.open(gi.ITEM_ASSETS / "textures" / "entity" / "equipment" / layer / f"{armour}.png").convert("RGBA")
                  for layer in ("humanoid", "humanoid_leggings")}
        for cube, layer in armour_cubes():
            quads += cube_quads(cube, layers[layer], transform(cube.part))
    for name in outfit:
        _, shape, bare, armoured = SLOTS[SLOT_OF[name]]
        for cube in shape(armoured if armour else bare, slim, bool(armour)):
            quads += cube_quads(cube, worn[name], transform(cube.part, cube))
    return quads


TO_LIGHT = [(0.25, -0.9, -0.6), (-0.3, -0.8, 0.6)]


def draw_view(quads, yaw, pitch, scale):
    """Quads seen from a camera turned `yaw` degrees round the figure (0 = in front), tilted `pitch` down."""
    view = mat_mul(rotation(x=math.radians(pitch)), rotation(y=math.radians(yaw)))
    lights = [tuple(c / math.sqrt(sum(k * k for k in t)) for c in t) for t in TO_LIGHT]
    placed = []
    for corners, color, n in quads:
        pts = [mat_vec(view, p) for p in corners]
        if mat_vec(view, n)[2] > 0:  # both sides are drawn (no culling); light the side we see
            n = tuple(-k for k in n)
        light = 0.42 + 0.58 * min(1.0, sum(max(0.0, sum(n[i] * l[i] for i in range(3))) for l in lights))
        shaded = tuple(min(255, round(k * light)) for k in color[:3]) + (255,)
        placed.append((sum(p[2] for p in pts) / 4, pts, shaded))
    xs = [p[0] for _, pts, _ in placed for p in pts]
    ys = [p[1] for _, pts, _ in placed for p in pts]
    left, top, pad = min(xs), min(ys), 2 * scale
    img = Image.new("RGBA", (round((max(xs) - left) * scale + 2 * pad), round((max(ys) - top) * scale + 2 * pad)), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    for _, pts, color in sorted(placed, key=lambda q: -q[0]):
        d.polygon([((p[0] - left) * scale + pad, (p[1] - top) * scale + pad) for p in pts], fill=color, outline=color)
    return img


VIEWS = [("front", 0, 8), ("three quarter", 35, 12), ("side", 90, 6), ("back three quarter", 150, 12), ("back", 180, 8)]
WALK = {"right_arm": (0.55, 0, 0.05), "left_arm": (-0.55, 0, -0.05), "right_leg": (-0.6, 0, 0.005),
        "left_leg": (0.6, 0, -0.005), "cape": 26.0}
OUTFITS = [
    ("bare, wide", ["emberstone_pendant", "wayfarer_mantle", "sunwoven_sash", "ironbark_bracers"], False, None),
    ("Vanguard armour", ["starglass_amulet", "duskwing_cloak", "tinker_belt", "duelist_vambraces"], False, "vanguard"),
    ("bare, slim", ["starglass_amulet", "duskwing_cloak", "tinker_belt", "duelist_vambraces"], True, None),
    ("Harvester armour", ["emberstone_pendant", "wayfarer_mantle", "sunwoven_sash", "ironbark_bracers"], False, "harvester"),
]


def preview_figures(worn, path, scale=9):
    rows = []
    for label, outfit, slim, armour in OUTFITS:
        still = figure_quads(outfit, worn, slim, armour)
        walking = figure_quads(outfit, worn, slim, armour, WALK)
        tiles = [(name, draw_view(still, yaw, pitch, scale)) for name, yaw, pitch in VIEWS]
        tiles.append(("walking", draw_view(walking, 120, 10, scale)))
        tiles.append(("1x", draw_view(still, 35, 12, 1)))
        tiles.append(("2x", draw_view(still, 150, 12, 2)))
        rows.append((label, tiles))
    width = max(sum(t.width + 24 for _, t in tiles) for _, tiles in rows) + 24
    height = sum(max(t.height for _, t in tiles) + 44 for _, tiles in rows) + 24
    img = Image.new("RGBA", (width, height), gi.BG + (255,))
    d = ImageDraw.Draw(img)
    y = 16
    for label, tiles in rows:
        d.text((24, y), label, fill=gi.LABEL)
        x, top = 24, y + 18
        for name, tile in tiles:
            img.alpha_composite(tile, (x, top))
            d.text((x, top + tile.height + 2), name, fill=(140, 148, 164))
            x += tile.width + 24
        y = top + max(t.height for _, t in tiles) + 26
    img.save(path)


def preview_sheet(icons, worn, path):
    """Icons at 8x and at in-game sizes, and every worn texture at 8x with its cube faces outlined."""
    names = list(icons)
    cell, pad = 128, 24
    worn_w = max(w.width for w in worn.values()) * 8
    left = 4 * (cell + pad) + pad
    worn_h = [0, 0]
    for i, name in enumerate(names):
        worn_h[i % 2] += worn[name].height * 8 + 30
    height = max(2 * (cell + pad + 14) + 3 * 16 + 120, max(worn_h)) + pad
    img = Image.new("RGBA", (left + 2 * (worn_w + pad), height), gi.BG + (255,))
    d = ImageDraw.Draw(img)
    for i, name in enumerate(names):
        x, y = pad + (i % 4) * (cell + pad), pad + (i // 4) * (cell + pad + 14)
        img.paste(gi.checker(cell, cell, 16), (x, y))
        img.alpha_composite(gi.big(icons[name], 8), (x, y))
        d.text((x, y + cell + 2), name, fill=gi.LABEL)
    y0 = pad + 2 * (cell + pad + 14)
    for f, y in ((1, y0), (2, y0 + 24), (3, y0 + 64)):
        x = pad
        for name in names:
            d.rectangle([x, y, x + 16 * f + 3, y + 16 * f + 3], fill=(20, 24, 33))
            img.alpha_composite(gi.big(icons[name], f), (x + 2, y + 2))
            x += 16 * f + 8
    xs, ys = [left, left + worn_w + pad], [pad, pad]
    for i, name in enumerate(names):
        tex, x, y = worn[name], xs[i % 2], ys[i % 2]
        d.text((x, y), f"{name} ({SLOT_OF[name]}, {tex.width}x{tex.height})", fill=gi.LABEL)
        img.paste(gi.checker(tex.width * 8, tex.height * 8, 8), (x, y + 14))
        for b in uv_boxes(SLOT_OF[name]).values():
            for fx, fy, fw, fh in b.values():
                d.rectangle([x + fx * 8, y + 14 + fy * 8, x + (fx + fw) * 8 - 1, y + 14 + (fy + fh) * 8 - 1],
                            fill=(66, 74, 92), outline=(96, 106, 128))
        img.alpha_composite(gi.big(tex, 8), (x, y + 14))
        ys[i % 2] = y + tex.height * 8 + 30
    img.save(path)


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--preview", type=Path, help="directory to write the contact sheet and 3D previews to")
    args = parser.parse_args()
    icons, worn = build()
    if args.preview:
        args.preview.mkdir(parents=True, exist_ok=True)
        preview_sheet(icons, worn, args.preview / "accessories_sheet.png")
        preview_figures(worn, args.preview / "accessories_worn.png")
    print(f"wrote {len(icons)} accessory icons and worn textures")
