#!/usr/bin/env python3
"""Draws SkyRPG's own item art: item icons, worn armour layers and the stat icon font.

    python tools/art/generate_items.py                  # writes the assets below
    python tools/art/generate_items.py --preview DIR    # also writes contact sheets to DIR

Everything is drawn here from palettes and pixel masks; nothing is copied or recoloured from vanilla.
Writes, under items/src/main/resources/assets/skyrpg_items/:
  * textures/item/<id>.png                      16x16 icon for every item but the accessories
  * textures/entity/equipment/humanoid/<set>.png and humanoid_leggings/<set>.png   64x32 worn layers
  * equipment/<set>.json, items/<id>.json, models/item/<id>.json, lang/en_us.json
and, under core/src/main/resources/assets/:
  * skyrpg_core/textures/font/stat_icons.png    8x8 stat icons, greyscale so text colour tints them
  * minecraft/font/default.json                 adds them to the default font at U+E100..U+E113
Item names in the lang file come from the definitions in data/skyrpg_items/skyrpg/items: every item
drawn here, then any other definition (accessories) in name order.

Other scripts build on this one:
  * generate_armor_depth.py re-shades the worn armour layers written here, so run it after this one
    (otherwise the flat layers come back).
  * generate_accessories.py draws the accessories' icons and worn textures.

How sprites work: a mask is a grid of characters. '.' is empty; an uppercase letter is a material
(a 5-shade ramp, darkest first). Each pixel starts at its material's base shade, gets lighter where its
top or left neighbour is a different region and darker where its bottom or right neighbour is, then
the material's texture (weave, grain, plates...) nudges it. An optional tweak grid overrides single
pixels: a digit sets the shade, '+'/'-' nudge it. Icons get a 1px outline in a darkened version of
whatever it surrounds.
"""
import argparse
import json
from pathlib import Path

from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parents[2]
ITEM_ASSETS = ROOT / "items" / "src" / "main" / "resources" / "assets" / "skyrpg_items"
ITEM_DATA = ROOT / "items" / "src" / "main" / "resources" / "data" / "skyrpg_items" / "skyrpg" / "items"
CORE_ASSETS = ROOT / "core" / "src" / "main" / "resources" / "assets"


def rgb(hex_color):
    return tuple(int(hex_color[i:i + 2], 16) for i in (0, 2, 4))


def scale(color, f):
    return tuple(max(0, min(255, round(c * f))) for c in color)


# ---------------------------------------------------------------- textures
# Called with the pixel's position (u, v) inside its icon or armour face and that area's size (w, h);
# they return a shade nudge.
def flat(u, v, w, h):
    return 0


def weave(u, v, w, h):
    """Straw: staggered strands."""
    return -1 if (u + (v % 2) * 2) % 4 == 3 else 0


def grains(u, v, w, h):
    """Wheat ears: alternating grains."""
    return 1 if (u + v) % 2 == 0 else 0


def speckle(u, v, w, h):
    """Fur, stone: sparse darker flecks."""
    n = (u * 374761393 + v * 668265263 + w * 1442695041) & 0xFFFFFFFF
    n = ((n ^ (n >> 13)) * 1274126177) & 0xFFFFFFFF
    return -1 if n % 7 == 0 else 0


def plates(u, v, w, h):
    """Plate armour: a dark seam every third row."""
    return -1 if v % 3 == 2 else 0


def sheen(u, v, w, h):
    """Polished metal on icons: a diagonal glint."""
    return 1 if (u - v) % 8 == 0 else 0


def polish(u, v, w, h):
    """Polished metal on armour faces: a bright streak near the lit edge."""
    return 1 if u == 1 and w >= 4 else 0


def cloth(u, v, w, h):
    """Woven cloth: a faint diagonal twill."""
    return -1 if (u + v * 3) % 7 == 0 else 0


def stitch(u, v, w, h):
    """Leather: faint stitching."""
    return -1 if (u * 2 + v) % 6 == 0 else 0


class Material:
    def __init__(self, ramp, base=2, texture=flat):
        self.ramp = [rgb(c) for c in ramp]
        self.base = base
        self.texture = texture

    def color(self, shade):
        return self.ramp[max(0, min(len(self.ramp) - 1, shade))]

    def shifted(self, base=None, texture=None):
        m = Material([], self.base if base is None else base, self.texture if texture is None else texture)
        m.ramp = self.ramp
        return m


# ---------------------------------------------------------------- palettes (darkest first)
STRAW = Material(["6b4413", "a06a1c", "d39b2c", "efc75a", "fbe9a0"], texture=weave)
WHEAT = Material(["7a4f10", "b07d1a", "dfab2e", "f5d465", "fff2b8"], texture=grains)
LEAF = Material(["1f4a23", "2f6e2c", "4c9a38", "7cc24e", "b6e27a"])
SOIL_LEATHER = Material(["3e2614", "5e3a1e", "82542c", "a8723e", "c99a5e"], texture=stitch)

SLATE = Material(["2a2f3a", "414857", "5d6678", "8089a0", "aab3c8"], texture=speckle)
COPPER = Material(["5a2a14", "8a4422", "bb6532", "e08a4c", "f6b47a"], texture=sheen)
AMBER = Material(["7a3c06", "c26a0c", "f2a21c", "ffd25a", "fff2b0"], base=3)

OAK = Material(["3a2212", "5a361c", "7c4e2a", "a06a3c", "c48e5a"], texture=stitch)
MOSS = Material(["233a1c", "34522a", "4c6e36", "6d8e46", "98b462"], texture=speckle)
HIDE = Material(["5c3c22", "86583a", "ad7a52", "cfa070", "e9c79a"])
IRON = Material(["3a3d44", "5e636e", "878d99", "b4bac4", "e2e6ec"], texture=sheen)

STEEL = Material(["2c3244", "505a72", "8290a8", "b8c4d6", "eef2f8"], texture=sheen)
CRIMSON = Material(["3e0a10", "6c121c", "9c1e26", "c8363a", "e86a5e"])
GOLD = Material(["5e3a08", "9a6814", "d0a024", "f0d050", "fff4a8"], texture=sheen)

VOID = Material(["0b0414", "1c0c32", "33175a", "4f2584", "6e38ac"])
RIFT = Material(["6a34b8", "9a5ce8", "c490ff", "e6ccff", "ffffff"], base=2)
DUSK_METAL = Material(["17151f", "2a2738", "433f55", "625d79", "8a85a2"], texture=sheen)
ORCHID = Material(["5a1060", "9a2aa0", "d050d0", "f090f0", "ffd8ff"], base=3)
WYRM = Material(["0a0418", "1a0c34", "311858", "502e88", "7650b6"], texture=sheen)
WYRM_BONE = Material(["3a2a48", "6a5680", "9a88b0", "c8bad8", "efe8f8"], texture=flat)
WYRM_GLOW = Material(["5a1ca0", "9a48e8", "c078ff", "e6b4ff", "fff0ff"], base=3)
WYRM_SPARK = Material(["b890f0", "e0c8ff", "f6eaff", "ffffff", "ffffff"], base=3)

BIRCH = Material(["6e5a40", "a08a66", "c8b48c", "e6d8b4", "faf2dc"])
BLOOM = Material(["2a7a3e", "46aa56", "74d47a", "b2f0a8", "eaffe0"], base=3)
VINE = Material(["1d4a26", "2c6a34", "44904a", "6cb862", "9ede88"])
HANDLE = Material(["3e2614", "5e3a1e", "82542c", "a8723e", "c99a5e"])


def render(mask, materials, tweak=None, outline=True, faces=None):
    """Turns a mask (list of strings or lists) into an RGBA image. See the module docstring.

    With faces (armour), each pixel knows its face rectangle: shading stops at face seams so they stay
    continuous, textures use face-local coordinates, and side faces get a lit top edge and a shaded
    right edge so the boxes read as rounded rather than flat.
    """
    h, w = len(mask), len(mask[0])
    img = Image.new("RGBA", (w, h), (0, 0, 0, 0))

    def at(x, y):
        return mask[y][x] if 0 <= x < w and 0 <= y < h else "."

    def differs(x, y, nx, ny, ch):
        if faces is not None:
            if not (0 <= nx < w and 0 <= ny < h) or faces[ny][nx] != faces[y][x]:
                return False  # seams between armour faces stay continuous
        return at(nx, ny) != ch

    for y in range(h):
        for x in range(w):
            ch = mask[y][x]
            if ch == ".":
                continue
            m = materials[ch]
            shade = m.base
            lit = differs(x, y, x, y - 1, ch) or differs(x, y, x - 1, y, ch)
            dark = differs(x, y, x, y + 1, ch) or differs(x, y, x + 1, y, ch)
            if faces is not None:
                face, fx, fy, fw, fh = faces[y][x]
                u, v = x - fx, y - fy
                if face in SIDES:
                    lit = lit or v == 0
                    dark = dark or u == fw - 1
            else:
                u, v, fw, fh = x, y, w, h
            shade += int(lit) - int(dark)
            shade += m.texture(u, v, fw, fh)
            t = tweak[y][x] if tweak else "."
            if t.isdigit():
                shade = int(t)
            elif t == "+":
                shade += 1
            elif t == "-":
                shade -= 1
            img.putpixel((x, y), m.color(shade) + (255,))

    if outline:
        for y in range(h):
            for x in range(w):
                if at(x, y) != ".":
                    continue
                darkest = None
                for nx, ny in ((x + 1, y), (x - 1, y), (x, y + 1), (x, y - 1)):
                    ch = at(nx, ny)
                    if ch != ".":
                        c = materials[ch].ramp[0]
                        if darkest is None or sum(c) < sum(darkest):
                            darkest = c
                if darkest is not None:
                    img.putpixel((x, y), scale(darkest, 0.5) + (255,))
    return img


# ---------------------------------------------------------------- item icons
# Each entry: mask rows, material map, optional tweak rows.
ICONS = {}


def icon(name, mats, mask, tweak=None):
    assert len(mask) == 16 and all(len(r) == 16 for r in mask), name
    if tweak:
        assert len(tweak) == 16 and all(len(r) == 16 for r in tweak), name + " tweak"
    ICONS[name] = (mask, mats, tweak)


HARVESTER = {"S": STRAW, "Z": STRAW.shifted(base=1), "G": LEAF, "W": WHEAT, "L": SOIL_LEATHER,
             "K": SOIL_LEATHER.shifted(base=1, texture=flat)}
icon("harvester_helmet", HARVESTER, [
    "................",
    "................",
    ".............W..",
    "......SSSS..WW..",
    ".....SSSSSS.WW..",
    ".....SSSSSS.W...",
    ".....SSSSSSG....",
    ".....SSSSSSG....",
    ".....GGGGGG.....",
    "..SSSSSSSSSSSS..",
    ".SSSSSSSSSSSSSS.",
    ".ZZZZZZZZZZZZZZ.",
    "..ZZZZZZZZZZZZ..",
    "................",
    "................",
    "................",
], [
    "................",
    "................",
    "................",
    "......4.........",
    ".....4..........",
    "................",
    "................",
    "................",
    ".....4..........",
    "..44............",
    ".4..............",
    "................",
    "................",
    "................",
    "................",
    "................",
])
icon("harvester_chestplate", HARVESTER, [
    "................",
    "................",
    "..SSSG....GSSS..",
    ".SSSSSG..GSSSSS.",
    ".SSSSSSGGSSSSSS.",
    ".SS.SSSWWSSS.SS.",
    ".SS.SSSWWSSS.SS.",
    ".GG.SSSWWSSS.GG.",
    "....SSSSGSSS....",
    "....GGGGGGGG....",
    "....SSSSSSSS....",
    "....SSSSSSSS....",
    "....ZZZZZZZZ....",
    "................",
    "................",
    "................",
])
icon("harvester_leggings", HARVESTER, [
    "................",
    "................",
    "...LLLLLLLLLL...",
    "...GGGGGGGGGG...",
    "...GGGGGGGGGG...",
    "...GGGG..GGGG...",
    "...GGGG..GGGG...",
    "...SSSG..GSSS...",
    "...SSSG..GSSS...",
    "...GGGG..GGGG...",
    "...GGGG..GGGG...",
    "...GGGG..GGGG...",
    "...ZZZZ..ZZZZ...",
    "................",
    "................",
    "................",
])
icon("harvester_boots", HARVESTER, [
    "................",
    "................",
    "................",
    "................",
    "...SSSS...SSSS..",
    "...LLLL...LLLL..",
    "...LLLL...LLLL..",
    "...LLLL...LLLL..",
    "...LLLL...LLLL..",
    ".LLLLLL.LLLLLL..",
    ".LLLLLL.LLLLLL..",
    ".KKKKKK.KKKKKK..",
    "................",
    "................",
    "................",
    "................",
])

PROSPECTOR = {"T": SLATE, "D": SLATE.shifted(base=1), "C": COPPER, "K": COPPER.shifted(base=1, texture=flat),
              "A": AMBER}
icon("prospector_helmet", PROSPECTOR, [
    "................",
    "................",
    "................",
    "......TTTT......",
    "....TTTTTTTT....",
    "...TTTTCCTTTT...",
    "...TTTCAACTTT...",
    "...TTTCAACTTT...",
    "...TTTTCCTTTT...",
    "...TTTTTTTTTT...",
    ".CCCCCCCCCCCCCC.",
    "..KKKKKKKKKKKK..",
    "................",
    "................",
    "................",
    "................",
])
icon("prospector_chestplate", PROSPECTOR, [
    "................",
    "................",
    "..CCTC....CTCC..",
    ".CCCTTC..CTTCCC.",
    ".CCTTCTTTTCTTCC.",
    ".TT.TCTTTTCT.TT.",
    ".TT.TCTAATCT.TT.",
    ".KK.TCTTTTCT.KK.",
    "....TCTTTTCT....",
    "....CCCAACCC....",
    "....TTTTTTTT....",
    "....TTTTTTTT....",
    "....DDDDDDDD....",
    "................",
    "................",
    "................",
])
icon("prospector_leggings", PROSPECTOR, [
    "................",
    "................",
    "...CCCCAACCCC...",
    "...TTTTTTTTTT...",
    "...TTTTTTTTTT...",
    "...TTTT..TTTT...",
    "...TTTT..TTTT...",
    "...CCCC..CCCC...",
    "...CCCC..CCCC...",
    "...TTTT..TTTT...",
    "...TTTT..TTTT...",
    "...TTTT..TTTT...",
    "...DDDD..DDDD...",
    "................",
    "................",
    "................",
])
icon("prospector_boots", PROSPECTOR, [
    "................",
    "................",
    "................",
    "................",
    "...CCCC...CCCC..",
    "...DDDD...DDDD..",
    "...DDDD...DDDD..",
    "...DDDD...DDDD..",
    "...DDDD...DDDD..",
    ".CCDDDD.CCDDDD..",
    ".CCDDDD.CCDDDD..",
    ".KKKKKK.KKKKKK..",
    "................",
    "................",
    "................",
    "................",
])

WOODSMAN = {"O": OAK, "K": OAK.shifted(base=1, texture=flat), "M": MOSS, "H": HIDE, "I": IRON, "J": LEAF}
icon("woodsman_helmet", WOODSMAN, [
    "................",
    "................",
    "............JJ..",
    "......OOOO.JJJ..",
    ".....OOOOOOJJ...",
    "....OOOOOOOO....",
    "....OOOOOOOO....",
    "....OOOOOOOO....",
    "...MMMMMMMMMM...",
    "..MMMMMMMMMMMM..",
    "..MMMMMMMMMMMM..",
    "..MM........MM..",
    "................",
    "................",
    "................",
    "................",
], [
    "................",
    "................",
    "................",
    "......4.........",
    ".....4.1........",
    "....4..1........",
    ".......1........",
    ".......1........",
    "................",
    "................",
    "................",
    "................",
    "................",
    "................",
    "................",
    "................",
])
icon("woodsman_chestplate", WOODSMAN, [
    "................",
    "................",
    "..MMMM....MMMM..",
    ".MMMMMM..MMMMMM.",
    ".MMMMMMHHMMMMMM.",
    ".OO.OOOHOOOO.OO.",
    ".OO.OOOOHOOO.OO.",
    ".OO.OOOHOOOO.OO.",
    "....OOOOHOOO....",
    "....HHHIIHHH....",
    "....OOOOOOOO....",
    "....OOOOOOOO....",
    "....KKKKKKKK....",
    "................",
    "................",
    "................",
])
icon("woodsman_leggings", WOODSMAN, [
    "................",
    "................",
    "...OOOOIIOOOO...",
    "...MMMMMMMMMM...",
    "...MMMMMMMMMM...",
    "...MMMM..MMMM...",
    "...MMMM..MMMM...",
    "...OOOM..MOOO...",
    "...OOOM..MOOO...",
    "...MMMM..MMMM...",
    "...MMMM..MMMM...",
    "...MMMM..MMMM...",
    "...HHHH..HHHH...",
    "................",
    "................",
    "................",
])
icon("woodsman_boots", WOODSMAN, [
    "................",
    "................",
    "................",
    "................",
    "...MMMM...MMMM..",
    "...MMMM...MMMM..",
    "...OOOO...OOOO..",
    "...OOOO...OOOO..",
    "...OOOO...OOOO..",
    ".OOOOOO.OOOOOO..",
    ".OOOOOO.OOOOOO..",
    ".KKKKKK.KKKKKK..",
    "................",
    "................",
    "................",
    "................",
])

VANGUARD = {"E": STEEL, "D": STEEL.shifted(base=0, texture=flat), "R": CRIMSON, "U": GOLD}
icon("vanguard_helmet", VANGUARD, [
    "................",
    "........RRR.....",
    "......UURRRRR...",
    ".....EEUUE.RRR..",
    "....EEEUUEE..R..",
    "...EEEEUUEEE....",
    "...EEEEUUEEEE...",
    "...EDDDUUDDDE...",
    "...EEEEUUEEEE...",
    "...EEEEUUEEEE...",
    "...UEEEEEEEEU...",
    "...UUUUUUUUUU...",
    "................",
    "................",
    "................",
    "................",
], [
    "................",
    "........4.......",
    "........4.......",
    ".....4..........",
    "....4...........",
    "...4............",
    "...4............",
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
icon("vanguard_chestplate", VANGUARD, [
    "................",
    "................",
    "..EEEU....UEEE..",
    ".EEEEEU..UEEEEE.",
    ".UUUEEEUUEEEUUU.",
    ".EE.EERRRREE.EE.",
    ".EE.EEURRUEE.EE.",
    ".EE.EERUUREE.EE.",
    "....EERRRREE....",
    "....UUUUUUUU....",
    "....EERRRREE....",
    "....EERRRREE....",
    "....DDRRRRDD....",
    "................",
    "................",
    "................",
])
icon("vanguard_leggings", VANGUARD, [
    "................",
    "................",
    "...UUUUUUUUUU...",
    "...RRRREERRRR...",
    "...RRRREERRRR...",
    "...EEEE..EEEE...",
    "...EEEE..EEEE...",
    "...EUUE..EUUE...",
    "...EEEE..EEEE...",
    "...EEEE..EEEE...",
    "...EEEE..EEEE...",
    "...EEEE..EEEE...",
    "...UUUU..UUUU...",
    "................",
    "................",
    "................",
])
icon("vanguard_boots", {**VANGUARD, "E": STEEL.shifted(texture=plates)}, [
    "................",
    "................",
    "................",
    "................",
    "...UUUU...UUUU..",
    "...EEEE...EEEE..",
    "...EEEE...EEEE..",
    "...EEEE...EEEE..",
    "...EEEE...EEEE..",
    ".EEEEEE.EEEEEE..",
    ".EEEEEE.EEEEEE..",
    ".DDDDDD.DDDDDD..",
    "................",
    "................",
    "................",
    "................",
])

icon("harvester_hoe", {"U": GOLD.shifted(texture=flat), "K": HANDLE, "G": LEAF}, [
    "................",
    "................",
    ".....UUUUUUUU...",
    "...UUUUUUUUKK...",
    "..UU......GG....",
    "..U......KK.....",
    "........KK......",
    ".......KK.......",
    "......KK........",
    ".....KK.........",
    "....KK..........",
    "...KK...........",
    "..KK............",
    ".KK.............",
    "................",
    "................",
])
icon("prospector_pickaxe", {"T": SLATE.shifted(texture=flat), "C": COPPER.shifted(texture=flat), "A": AMBER,
                            "K": HANDLE}, [
    "................",
    "....TTTTT.......",
    "...TTTTTTTCC....",
    "..TT.....TAAC...",
    "..T.......CAC...",
    ".........K.TT...",
    "........KK..TT..",
    ".......KK...TT..",
    "......KK....TT..",
    ".....KK.....TT..",
    "....KK......TT..",
    "...KK......TT...",
    "..KK......TT....",
    ".KK.............",
    ".K..............",
    "................",
])
icon("woodsman_axe", {"I": IRON.shifted(texture=flat), "K": HANDLE, "M": MOSS.shifted(texture=flat), "H": HIDE}, [
    "................",
    "......IIII..KK..",
    "....IIIIIIIKK...",
    "...IIIIIIIKK....",
    "...IIIIIIKK.....",
    "....IIIIKK......",
    ".....IIKK.......",
    "......MM........",
    ".....MM.........",
    "....KK..........",
    "...HH...........",
    "..HH............",
    ".HH.............",
    "................",
    "................",
    "................",
], [
    "................",
    "......44.1......",
    "....44....1.....",
    "...44....1......",
    "...4....1.......",
    "....4..1........",
    ".....4..........",
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
icon("vanguard_blade", {"E": STEEL.shifted(texture=flat), "R": CRIMSON, "U": GOLD.shifted(texture=flat)}, [
    "................",
    "..............E.",
    ".............EE.",
    "............EE..",
    "...........EE...",
    "..........EE....",
    ".........EE.....",
    "........EE......",
    "...U...EE.......",
    "....U.EE........",
    ".....UE.........",
    "....RRU.........",
    "...RR..U........",
    "..UR............",
    "..U.............",
    "................",
])
icon("riftblade", {"F": RIFT, "V": VOID, "H": RIFT.shifted(base=0), "X": RIFT.shifted(base=4),
                   "N": DUSK_METAL.shifted(base=3), "Q": DUSK_METAL.shifted(base=1, texture=flat), "P": ORCHID}, [
    "................",
    ".............FV.",
    "............FVV.",
    "...........FVXH.",
    "..........FVVH..",
    ".........FVXH...",
    "..N.....FVVH....",
    "..N....FVVH.....",
    "...N..FVXH......",
    "....NFVVH.......",
    ".....PVH........",
    "....QNP.........",
    "...QN..N........",
    "..PP....NN......",
    "..P.............",
    "................",
], [
    "................",
    ".............4..",
    "............4...",
    "...........4....",
    "..........4.....",
    ".........4......",
    "........4.......",
    "................",
    "................",
    "................",
    ".....4..........",
    "................",
    "................",
    "..4.............",
    "................",
    "................",
])
icon("mending_wand", {"B": BIRCH, "G": VINE, "Q": BLOOM, "U": GOLD.shifted(texture=flat)}, [
    "................",
    "...........GQ...",
    "..........GQQQ..",
    "...........QQG..",
    "..........UUG...",
    ".........BU.....",
    "........GB......",
    ".......BB.......",
    "......BG........",
    ".....GB.........",
    "....BB..........",
    "...BG...........",
    "..GB............",
    "..B.............",
    "................",
    "................",
], [
    "................",
    "............4...",
    "...........4....",
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


# ---------------------------------------------------------------- worn armour (64x32)
def box(u, v, w, h, d):
    """Face rectangles (x, y, width, height) of a model box's texture, vanilla's cube UV layout."""
    return {"top": (u + d, v, w, d), "bottom": (u + d + w, v, w, d),
            "right": (u, v + d, d, h), "front": (u + d, v + d, w, h),
            "left": (u + d + w, v + d, d, h), "back": (u + d + w + d, v + d, w, h)}


HEAD = box(0, 0, 8, 8, 8)
BODY = box(16, 16, 8, 12, 4)
ARM = box(40, 16, 4, 12, 4)
LEG = box(0, 16, 4, 12, 4)
SIDES = ("right", "front", "left", "back")


class Sheet:
    """A 64x32 armour layer being painted: material chars and tweaks, face by face."""

    def __init__(self):
        self.mask = [["."] * 64 for _ in range(32)]
        self.tweak = [["."] * 64 for _ in range(32)]
        self.faces = [[None] * 64 for _ in range(32)]
        for part, b in (("head", HEAD), ("body", BODY), ("arm", ARM), ("leg", LEG)):
            for face, (x0, y0, w, h) in b.items():
                for y in range(y0, y0 + h):
                    for x in range(x0, x0 + w):
                        self.faces[y][x] = (face, x0, y0, w, h)

    def fill(self, b, faces, ch, rows=None, cols=None):
        """Fills face-local rows/cols (ranges) of the given faces."""
        for face in faces:
            x0, y0, w, h = b[face]
            for v in rows if rows is not None else range(h):
                for u in cols if cols is not None else range(w):
                    if 0 <= u < w and 0 <= v < h:
                        self.mask[y0 + v][x0 + u] = ch

    def band(self, b, rows, ch, faces=SIDES):
        """A horizontal band all the way round a box."""
        self.fill(b, faces, ch, rows=rows)

    def put(self, b, face, u, v, ch=None, tweak=None):
        x0, y0, w, h = b[face]
        if ch is not None:
            self.mask[y0 + v][x0 + u] = ch
        if tweak is not None:
            self.tweak[y0 + v][x0 + u] = tweak

    def clear(self, b, face, u, v):
        self.put(b, face, u, v, ".")

    def tweak_face(self, b, face, fn):
        x0, y0, w, h = b[face]
        for v in range(h):
            for u in range(w):
                t = fn(u, v, w, h)
                if t:
                    self.tweak[y0 + v][x0 + u] = t

    def render(self, materials):
        return render(self.mask, materials, self.tweak, outline=False, faces=self.faces)


def rim(u, v, w, h):
    """Darkens the outer ring of a face (a hat's crown edge, a plate's border)."""
    return "-" if u in (0, w - 1) or v in (0, h - 1) else None


def harvester_layers(m=None):
    m = m or {"S": STRAW, "Z": STRAW.shifted(base=1), "G": LEAF.shifted(texture=cloth), "W": WHEAT, "L": SOIL_LEATHER,
              "K": SOIL_LEATHER.shifted(base=0, texture=flat)}
    outer = Sheet()
    # Straw hat: woven crown, green band, a stiff brim, and a wheat ear tucked in on one side.
    outer.fill(HEAD, ["top"], "S")
    outer.tweak_face(HEAD, "top", rim)
    outer.band(HEAD, range(0, 2), "S")
    outer.band(HEAD, [2], "G")
    outer.band(HEAD, [3], "Z")
    for u, v in ((5, 0), (6, 0), (5, 1), (6, 1), (6, 2)):
        outer.put(HEAD, "left", u, v, "W")
    # Tunic: woven straw, green collar and belt, a wheat sheaf stitched on the chest.
    outer.fill(BODY, SIDES, "S", rows=range(0, 10))
    outer.band(BODY, [9], "Z")
    outer.band(BODY, [7], "G")
    for u in range(2, 6):
        outer.clear(BODY, "front", u, 0)
    for u in (3, 4):
        outer.clear(BODY, "front", u, 1)
    for u, v in ((1, 0), (6, 0), (2, 1), (5, 1), (3, 2), (4, 2)):
        outer.put(BODY, "front", u, v, "G")
    for u, v in ((3, 3), (4, 3), (3, 4), (4, 4), (3, 5), (4, 5)):
        outer.put(BODY, "front", u, v, "W")
    outer.put(BODY, "front", 3, 6, "G")
    outer.put(BODY, "front", 4, 6, "G")
    outer.fill(ARM, ["top"], "S")
    outer.band(ARM, range(0, 4), "S")
    outer.band(ARM, [4], "G")
    # Boots: brown leather with straw cuffs.
    outer.band(LEG, range(6, 12), "L")
    outer.band(LEG, [6], "S")
    outer.fill(LEG, ["bottom"], "K")
    outer.put(LEG, "front", 1, 8, tweak="4")
    outer.put(LEG, "front", 2, 9, tweak="4")

    inner = Sheet()
    # Trousers: leaf green, straw knee patches, brown belt.
    inner.band(LEG, range(0, 9), "G")
    inner.fill(LEG, ["top"], "G")
    inner.fill(LEG, ["front"], "S", rows=range(4, 6))
    inner.band(BODY, range(7, 12), "G")
    inner.band(BODY, [7], "L")
    inner.put(BODY, "front", 3, 7, "S")
    inner.put(BODY, "front", 4, 7, "S")
    return outer.render(m), inner.render(m)


def prospector_layers(m=None):
    m = m or {"T": SLATE, "D": SLATE.shifted(base=1), "C": COPPER.shifted(texture=polish), "A": AMBER,
              "K": COPPER.shifted(base=1, texture=flat)}
    outer = Sheet()
    # Hard hat: slate shell, copper ridge and brim, an amber lamp on the front.
    outer.fill(HEAD, ["top"], "T")
    outer.fill(HEAD, ["top"], "C", cols=[3, 4])
    outer.band(HEAD, range(0, 3), "T")
    outer.band(HEAD, [3], "C")
    outer.fill(HEAD, ["front"], "C", rows=range(0, 3), cols=range(2, 6))
    outer.fill(HEAD, ["front"], "A", rows=range(0, 2), cols=range(3, 5))
    outer.put(HEAD, "front", 3, 0, tweak="4")
    # Jacket: slate canvas, copper braces with rivets, amber pendant and belt buckle.
    outer.fill(BODY, SIDES, "T", rows=range(0, 10))
    outer.band(BODY, [9], "D")
    for u in (3, 4):
        outer.clear(BODY, "front", u, 0)
    for face in ("front", "back"):
        outer.fill(BODY, [face], "C", rows=range(0, 10), cols=[1, 6])
        outer.put(BODY, face, 1, 1, tweak="4")
        outer.put(BODY, face, 6, 1, tweak="4")
    outer.band(BODY, [8], "C")
    outer.put(BODY, "front", 3, 3, "A")
    outer.put(BODY, "front", 4, 3, "A")
    outer.put(BODY, "front", 3, 8, "A")
    outer.put(BODY, "front", 4, 8, "A")
    outer.fill(ARM, ["top"], "C")
    outer.band(ARM, range(0, 3), "C")
    outer.band(ARM, [3, 4], "T")
    outer.put(ARM, "front", 1, 1, tweak="4")
    outer.put(ARM, "right", 1, 1, tweak="4")
    # Boots: dark slate, copper cuffs and toe caps.
    outer.band(LEG, range(6, 12), "D")
    outer.band(LEG, [6], "C")
    outer.fill(LEG, ["front"], "C", rows=range(9, 12))
    outer.fill(LEG, ["bottom"], "K")

    inner = Sheet()
    inner.band(LEG, range(0, 9), "T")
    inner.fill(LEG, ["top"], "T")
    inner.fill(LEG, ["front", "right", "left"], "C", rows=range(4, 6))
    inner.band(BODY, range(7, 12), "T")
    inner.band(BODY, [7, 8], "C")
    inner.put(BODY, "front", 3, 7, "A")
    inner.put(BODY, "front", 4, 7, "A")
    inner.put(BODY, "front", 3, 8, "A")
    inner.put(BODY, "front", 4, 8, "A")
    return outer.render(m), inner.render(m)


def woodsman_layers(m=None):
    m = m or {"O": OAK, "K": OAK.shifted(base=0, texture=flat), "M": MOSS, "H": HIDE, "I": IRON.shifted(texture=flat),
              "J": LEAF}
    outer = Sheet()
    # Leather cap with a moss fur trim and ear flaps, an oak leaf tucked in.
    outer.fill(HEAD, ["top"], "O")
    outer.tweak_face(HEAD, "top", lambda u, v, w, h: "-" if u == 3 or v == 4 else None)
    outer.band(HEAD, range(0, 2), "O")
    outer.band(HEAD, range(2, 4), "M")
    outer.fill(HEAD, ["right", "left"], "M", rows=range(4, 6), cols=range(2, 6))
    outer.fill(HEAD, ["back"], "M", rows=range(4, 5))
    for u, v in ((5, 0), (6, 0), (6, 1)):
        outer.put(HEAD, "left", u, v, "J")
    # Jerkin: leather, moss mantle over the shoulders, laced front, hide belt with an iron buckle.
    outer.fill(BODY, SIDES, "O", rows=range(0, 10))
    outer.band(BODY, range(0, 2), "M")
    for u in (3, 4):
        outer.clear(BODY, "front", u, 0)
    for v in range(2, 7):
        outer.put(BODY, "front", 3 + (v % 2), v, "H")
    outer.band(BODY, [8], "H")
    outer.put(BODY, "front", 3, 8, "I")
    outer.put(BODY, "front", 4, 8, "I")
    outer.fill(ARM, ["top"], "M")
    outer.band(ARM, range(0, 3), "M")
    outer.band(ARM, [3, 4], "O")
    # Boots: leather with fur cuffs and laces.
    outer.band(LEG, range(6, 12), "O")
    outer.band(LEG, range(6, 8), "M")
    outer.fill(LEG, ["bottom"], "K")
    outer.put(LEG, "front", 1, 9, "H")
    outer.put(LEG, "front", 2, 10, "H")

    inner = Sheet()
    # Breeches: moss wool, leather knee patches, leather belt with an iron buckle.
    inner.band(LEG, range(0, 9), "M")
    inner.fill(LEG, ["top"], "M")
    inner.fill(LEG, ["front"], "O", rows=range(4, 6))
    inner.band(BODY, range(7, 12), "M")
    inner.band(BODY, [7], "O")
    inner.put(BODY, "front", 3, 7, "I")
    inner.put(BODY, "front", 4, 7, "I")
    return outer.render(m), inner.render(m)


def vanguard_layers(m=None):
    m = m or {"E": STEEL.shifted(texture=polish), "P": STEEL.shifted(texture=plates), "D": STEEL.shifted(base=0, texture=flat),
              "R": CRIMSON, "U": GOLD.shifted(texture=flat)}
    outer = Sheet()
    # Helm: steel with a red crest, gold brow band, nose guard and cheek guards.
    outer.fill(HEAD, ["top"], "E")
    outer.fill(HEAD, ["top"], "R", cols=[3, 4])
    outer.band(HEAD, range(0, 2), "E")
    outer.band(HEAD, [2], "U")
    outer.fill(HEAD, ["right", "left", "back"], "E", rows=range(3, 6))
    outer.fill(HEAD, ["back"], "E", rows=[6])
    outer.fill(HEAD, ["right", "left", "back"], "U", rows=[6], cols=range(0, 8))
    outer.fill(HEAD, ["front"], "E", rows=[3], cols=[0, 1, 3, 4, 6, 7])
    outer.fill(HEAD, ["front"], "E", rows=[4, 5], cols=[0, 7])
    outer.fill(HEAD, ["right", "left"], ".", rows=[6], cols=range(0, 8))
    outer.fill(HEAD, ["right", "left"], "U", rows=[5], cols=range(0, 8))
    # Cuirass: steel, red tabard with a gold chevron in front, red cloak behind, gold belt.
    outer.fill(BODY, SIDES, "E", rows=range(0, 10))
    for u in (3, 4):
        outer.clear(BODY, "front", u, 0)
    outer.fill(BODY, ["front"], "R", rows=range(1, 10), cols=range(2, 6))
    for u, v in ((2, 3), (5, 3), (3, 4), (4, 4)):
        outer.put(BODY, "front", u, v, "U")
    outer.fill(BODY, ["back"], "R", rows=range(1, 10), cols=range(1, 7))
    for v in range(2, 10):
        for u in (2, 4):
            outer.put(BODY, "back", u, v, tweak="-")
        outer.put(BODY, "back", 3, v, tweak="+")
    outer.put(BODY, "back", 1, 0, "U")
    outer.put(BODY, "back", 6, 0, "U")
    outer.band(BODY, [7], "U", faces=("right", "front", "left"))
    outer.put(BODY, "back", 0, 7, "U")
    outer.put(BODY, "back", 7, 7, "U")
    outer.put(BODY, "front", 1, 0, "U")
    outer.put(BODY, "front", 6, 0, "U")
    outer.fill(ARM, ["top"], "P")
    outer.band(ARM, range(0, 4), "P")
    outer.band(ARM, [4], "U")
    # Sabatons: plated steel, gold cuffs.
    outer.band(LEG, range(6, 12), "P")
    outer.band(LEG, [6], "U")
    outer.fill(LEG, ["bottom"], "D")

    inner = Sheet()
    # Greaves: steel legs with gold-riveted knee cops, red tassets at the hip.
    inner.band(LEG, range(0, 9), "E")
    inner.fill(LEG, ["top"], "E")
    inner.fill(LEG, ["front"], "P", rows=range(3, 6))
    inner.put(LEG, "front", 1, 4, "U")
    inner.put(LEG, "front", 2, 4, "U")
    inner.band(BODY, range(7, 12), "E")
    inner.band(BODY, [7], "U")
    inner.fill(BODY, ["front", "back"], "R", rows=range(8, 12), cols=range(1, 7))
    return outer.render(m), inner.render(m)


# ---------------------------------------------------------------- mining gear (the Mining Master's sets)
BRASS = Material(["4a3010", "7a5420", "b0823a", "d8ac5a", "f6dc96"], texture=sheen)
DELVE_LEATHER = Material(["2e1c10", "4a2e1a", "6a4428", "8e5e38", "b4824e"], texture=stitch)
DEEP_STEEL = Material(["0e1626", "1c2a44", "2c4064", "46608c", "7090ba"], texture=sheen)
TEAL_GLOW = Material(["0c4a4a", "157a74", "22b0a0", "5ee0c8", "b4fff0"], base=3)
SKYIRON = Material(["3a5470", "5c7c9c", "86a8c8", "b4d2ec", "e6f4ff"], texture=sheen)
PRISM_GLOW = Material(["5a3a9a", "8a6ad0", "b8a0f0", "dcd0ff", "ffffff"], base=3)
OBSIDIAN = Material(["120a10", "22121c", "34182a", "4c2438", "6a3450"], texture=speckle)
EMBER = Material(["6a1a04", "a83a0c", "e26a1a", "ffa040", "ffe08a"], texture=sheen)
HEART = Material(["6a0a2a", "a8143e", "e0306a", "ff7aa6", "ffd0e0"], base=3)
DUSKQUARTZ = Material(["2a2040", "4a3a72", "6a56a0", "a492cc", "e8defc"], texture=sheen)
LUMEN_CRYSTAL = Material(["3c6a88", "6aa4c4", "a6daf0", "dcf6ff", "ffffff"], texture=sheen)
LUMEN_GLOW = Material(["3a8ab0", "66c4e4", "a8ecff", "e0fbff", "ffffff"], base=3)

def stamp(s, b, face, rows, u0=0, v0=0, tweak=False):
    """Paints a face from rows of material letters ('.' leaves a pixel alone, '_' clears it); with tweak=True
    the rows hold shade digits for the tweak grid instead."""
    for v, row in enumerate(rows):
        for u, ch in enumerate(row):
            if ch == ".":
                continue
            if tweak:
                s.put(b, face, u0 + u, v0 + v, tweak=ch)
            else:
                s.put(b, face, u0 + u, v0 + v, "." if ch == "_" else ch)


def flanks(s, b, rows, v0=0, tweak=False):
    """Stamps the right face with rows (as wide as the face) and the left face with them mirrored."""
    stamp(s, b, "right", rows, 0, v0, tweak)
    stamp(s, b, "left", [r[::-1] for r in rows], 0, v0, tweak)


def sprite(top, *rows):
    """A 16x16 icon mask from its rows, the first one at row `top`."""
    return ["." * 16] * top + list(rows) + ["." * 16] * (16 - top - len(rows))


def half(top, *rows):
    """Like sprite, from the left 8 columns of a mirror-symmetric icon."""
    return sprite(top, *(r + r[::-1] for r in rows))


# ---------------------------------------------------------------- mining sets: worn layers
def delver_layers(m):
    outer = Sheet()
    # Brass miner's helmet: a dome, a dark brass brim and a boxed lamp, leather ear flaps and neck guard.
    outer.fill(HEAD, ["top"], "C")
    outer.fill(HEAD, ["top"], "T", cols=[3, 4])
    outer.tweak_face(HEAD, "top", rim)
    outer.band(HEAD, range(0, 3), "C")
    outer.band(HEAD, [3], "K")
    stamp(outer, HEAD, "front", ["..KKKK..", "..KAAK..", "..KAAK.."])
    stamp(outer, HEAD, "front", ["D......D", "D......D"], v0=4)
    flanks(outer, HEAD, ["..TTTT..", "..TTTT..", "...DD..."], v0=4)
    stamp(outer, HEAD, "back", [".TTTTTT.", ".TTTTTT.", "..DDDD.."], v0=4)
    outer.put(HEAD, "front", 3, 1, tweak="4")
    # Jerkin: patched leather with a studded bandolier, a brass buckle belt and elbow patches.
    outer.fill(BODY, SIDES, "T", rows=range(0, 10))
    stamp(outer, BODY, "front", ["TTT__TTT", "TDTTTTTT", "TTCTTPPT", "TTTDTPPT", "TTTTCTTT", "TPPTTDTT", "TPPTTTCT",
                                 "TTTTTTTT", "DDDCCDDD", "TTTTTTTT"])
    stamp(outer, BODY, "back", ["TTTTTTTT", "TDTTTTDT", "TTDTTDTT", "TTPPPPTT", "TTPPPPTT", "TTPPPPTT", "TTTDDTTT",
                                "TTTTTTTT", "DDDDDDDD", "TTTTTTTT"])
    flanks(outer, BODY, ["TTTT", "TTTT", "TTTT", "TPPT", "TPPT", "TTTT", "TTTT", "TTTT", "DDDD", "TTTT"])
    outer.fill(ARM, ["top"], "T")
    outer.band(ARM, range(0, 3), "T")
    outer.band(ARM, [3], "D")
    outer.band(ARM, [4], "K")
    stamp(outer, ARM, "front", [".PP."], v0=1)
    # Boots: leather, folded cuff, two brass-buckled straps.
    outer.band(LEG, range(6, 12), "T")
    outer.band(LEG, [6, 8, 10], "D")
    stamp(outer, LEG, "front", ["..C."], v0=8)
    stamp(outer, LEG, "front", ["..C."], v0=10)
    outer.band(LEG, [11], "K")
    outer.fill(LEG, ["bottom"], "K")

    inner = Sheet()
    # Trousers: leather with brass-rimmed knee pads, a belt with a brass buckle and a patched pocket.
    inner.band(LEG, range(0, 9), "T")
    inner.fill(LEG, ["top"], "T")
    stamp(inner, LEG, "front", ["CCCC", "CDDC", "CDDC", "CCCC"], v0=3)
    flanks(inner, LEG, ["DDDD", "DDDD"], v0=4)
    inner.band(BODY, range(7, 12), "T")
    inner.band(BODY, [7], "D")
    stamp(inner, BODY, "front", ["...CC..."], v0=7)
    stamp(inner, BODY, "front", [".....PP.", ".....PP."], v0=9)
    return outer.render(m), inner.render(m)


def skyforged_layers(m):
    outer = Sheet()
    # Winged helm: sky plate, gold brow band, a prism gem, nose guard, feathers swept back from the temples.
    outer.fill(HEAD, ["top"], "E")
    outer.fill(HEAD, ["top"], "U", cols=[3, 4])
    outer.tweak_face(HEAD, "top", rim)
    outer.band(HEAD, range(0, 2), "E")
    outer.band(HEAD, [2], "U")
    outer.band(HEAD, range(3, 6), "E", faces=("right", "left", "back"))
    stamp(outer, HEAD, "front", ["EEEEEEEE", "EEERREEE", "UUUUUUUU", "EE_UU_EE", "EE_UU_EE", "EE____EE"])
    flanks(outer, HEAD, ["FFF.....", "FFFFF...", "........", "FFFF....", "FF......"])
    outer.put(HEAD, "front", 3, 1, tweak="4")
    # Cuirass: gold-trimmed plate with a winged prism emblem; feathers spread over the back; layered pauldrons.
    outer.fill(BODY, SIDES, "E", rows=range(0, 10))
    stamp(outer, BODY, "front", ["UEE__EEU", "EEEEEEEE", "UUERREUU", "EUURRUUE", "EEUUUUEE", "EEEUUEEE", "EEEEEEEE",
                                 "EEEEEEEE", "UUUUUUUU", "EEEEEEEE"])
    stamp(outer, BODY, "back", ["UEEEEEEU", "FFEUUEFF", "FFFUUFFF", "EFFUUFFE", "EEFUUFEE", "EEEUUEEE", "EEEUUEEE",
                                "EEEUUEEE", "UUUUUUUU", "EEEEEEEE"])
    flanks(outer, BODY, ["EEEE"] * 8 + ["UUUU", "EEEE"])
    outer.fill(ARM, ["top"], "E")
    outer.band(ARM, range(0, 4), "E")
    outer.band(ARM, [2, 4], "U")
    outer.put(BODY, "front", 3, 2, tweak="4")
    # Sabatons: sky plate, gold cuff, a feather wing at each ankle.
    outer.band(LEG, range(6, 12), "E")
    outer.band(LEG, [6], "U")
    outer.band(LEG, [8, 10], "P")
    outer.fill(LEG, ["bottom"], "D")
    flanks(outer, LEG, ["....", "FFF.", "FF.."], v0=7)

    inner = Sheet()
    # Greaves: sky plate legs, gold-rimmed knee cops, faulds with a gold hem at the hip.
    inner.band(LEG, range(0, 9), "E")
    inner.fill(LEG, ["top"], "E")
    stamp(inner, LEG, "front", ["PPPP", "PUUP", "PPPP"], v0=3)
    inner.band(BODY, range(7, 12), "E")
    inner.band(BODY, [7], "U")
    inner.fill(BODY, ["front", "back"], "P", rows=range(8, 11), cols=range(1, 7))
    inner.fill(BODY, ["front", "back"], "U", rows=[11], cols=range(1, 7))
    return outer.render(m), inner.render(m)


def deepwarden_layers(m):
    outer = Sheet()
    # Great helm under a hood: the cowl frames a steel face plate with a glowing visor slit.
    outer.fill(HEAD, ["top"], "H")
    outer.tweak_face(HEAD, "top", rim)
    outer.fill(HEAD, SIDES, "H", rows=range(0, 8))
    stamp(outer, HEAD, "front", ["........", "........", "HEEEEEEH", "HEEUUEEH", "HRRRRRRH", "HEEUUEEH", "HEEDDEEH"])
    flanks(outer, HEAD, ["........", "........", "....EEE.", "....EEE.", "....RRR.", "....EEE.", "....EEE.", "DDDDDDDD"])
    stamp(outer, HEAD, "back", ["........", "........", "...DD...", "...DD...", "...DD...", "...DD...", "...DD...", "DDDDDDDD"])
    outer.put(HEAD, "front", 3, 4, tweak="4")
    outer.put(HEAD, "front", 4, 4, tweak="4")
    # Cuirass: heavy plate with a glowing rune, iron rivets, broad pauldrons and an iron belt.
    outer.fill(BODY, SIDES, "E", rows=range(0, 10))
    stamp(outer, BODY, "front", ["EEE__EEE", "EPEEEEPE", "EEEREREE", "EEEEREEE", "EEEEREEE", "EEEEREEE", "EEEEREEE",
                                 "PPPPPPPP", "UUUUUUUU", "DDDDDDDD"])
    stamp(outer, BODY, "back", ["EEEEEEEE", "EEEDDEEE", "EEEDDEEE", "EREDDERE", "EREDDERE", "EEEDDEEE", "EEEDDEEE",
                                "PPPDDPPP", "UUUUUUUU", "DDDDDDDD"])
    flanks(outer, BODY, ["EEEE", "EPPE", "EEEE", "EEEE", "EEEE", "EEEE", "EEEE", "PPPP", "UUUU", "DDDD"])
    outer.fill(ARM, ["top"], "P")
    outer.band(ARM, range(0, 2), "P")
    outer.band(ARM, [2, 3], "E")
    outer.band(ARM, [4], "U")
    stamp(outer, ARM, "front", [".R..", "..R."], v0=2)
    # Sabatons: iron cuff, plated shins, a rune on the front.
    outer.band(LEG, range(6, 12), "E")
    outer.band(LEG, [6], "U")
    outer.band(LEG, range(8, 11), "P")
    outer.band(LEG, [11], "D")
    outer.fill(LEG, ["bottom"], "D")
    stamp(outer, LEG, "front", ["..R.", ".RR.", "..R."], v0=7)

    inner = Sheet()
    # Greaves: plated knees with a rune, an iron belt and heavy faulds.
    inner.band(LEG, range(0, 9), "E")
    inner.fill(LEG, ["top"], "E")
    inner.fill(LEG, ["front"], "P", rows=range(3, 7))
    stamp(inner, LEG, "front", [".R..", "RR.."], v0=1)
    stamp(inner, LEG, "front", ["..R.", ".RR."], v0=4)
    inner.band(BODY, range(7, 12), "E")
    inner.band(BODY, [7], "U")
    inner.fill(BODY, ["front", "back"], "P", rows=range(8, 12), cols=range(1, 7))
    inner.put(BODY, "front", 3, 9, "R")
    inner.put(BODY, "front", 4, 10, "R")
    return outer.render(m), inner.render(m)


def emberheart_layers(m):
    outer = Sheet()
    # Horned obsidian helm, split by ember cracks, with glowing slit eyes.
    outer.fill(HEAD, ["top"], "T")
    outer.tweak_face(HEAD, "top", rim)
    stamp(outer, HEAD, "top", ["...C....", "...CC...", "....C...", "....CC..", "...C....", "..CC....", "..C.....", "..CC...."])
    outer.band(HEAD, range(0, 6), "T")
    outer.band(HEAD, [6], "D")
    stamp(outer, HEAD, "front", ["....C...", "...C....", "...CC...", "....C...", ".CC..CC."])
    flanks(outer, HEAD, ["........", "..C.....", "..CC....", "...C....", "....CC..", ".....C.."])
    stamp(outer, HEAD, "back", ["........", "...C....", "..CC....", "..C.....", "...C....", "...CC..."])
    for u in (1, 2, 5, 6):
        outer.put(HEAD, "front", u, 4, tweak="4")
    # Cuirass: obsidian plates cracked with ember, a heartstone set in the chest, an ember belt.
    outer.fill(BODY, SIDES, "T", rows=range(0, 10))
    stamp(outer, BODY, "front", ["TTT__TTT", "TTTKKTTT", "TCKAAKCT", "TKAAAAKT", "TCKAAKCT", "TTTKKTTT", "TTCTTTTT",
                                 "TTTCTTTT", "KKKKKKKK", "TTTTTTTT"])
    stamp(outer, BODY, "back", ["TTTTTTTT", "TTTTCTTT", "TTTCCTTT", "TTCTTTTT", "TTCTTCTT", "TTTTTCCT", "TCTTTTTT",
                                "TCTTTTTT", "KKKKKKKK", "TTTTTTTT"])
    flanks(outer, BODY, ["TTTT", "TTTT", "TTTT", "TCTT", "TTCT", "TTTT", "TTTT", "TTTT", "KKKK", "TTTT"])
    for u, v in ((3, 3), (4, 3)):
        outer.put(BODY, "front", u, v, tweak="4")
    outer.fill(ARM, ["top"], "T")
    outer.band(ARM, range(0, 4), "T")
    outer.band(ARM, [4], "K")
    stamp(outer, ARM, "front", [".C..", "..C.", ".C.."], v0=1)
    # Boots: obsidian, an ember cuff, a crack up the shin and glowing soles.
    outer.band(LEG, range(6, 12), "T")
    outer.band(LEG, [6, 11], "K")
    stamp(outer, LEG, "front", [".C..", "..C.", ".C.."], v0=8)
    outer.fill(LEG, ["bottom"], "D")

    inner = Sheet()
    # Greaves: obsidian with ember cracks and knee guards, an ember belt with a small heartstone.
    inner.band(LEG, range(0, 9), "T")
    inner.fill(LEG, ["top"], "T")
    stamp(inner, LEG, "front", ["..C.", ".C..", ".C..", "TKKT", "TKKT", "..C."], v0=1)
    inner.band(BODY, range(7, 12), "T")
    inner.band(BODY, [7], "K")
    stamp(inner, BODY, "front", ["...AA..."], v0=7)
    stamp(inner, BODY, "front", [".C......", "..C...C.", "......C."], v0=9)
    return outer.render(m), inner.render(m)


def lumenward_layers(m):
    outer = Sheet()
    # Crystal circlet helm: faceted pale plate, a gold band, a glowing brow gem and drifting light motes.
    outer.fill(HEAD, ["top"], "E")
    stamp(outer, HEAD, "top", ["D......D", ".D....D.", "..D..D..", "...DD...", "...DD...", "..D..D..", ".D....D.", "D......D"])
    outer.tweak_face(HEAD, "top", rim)
    outer.band(HEAD, range(0, 3), "E")
    outer.band(HEAD, [3], "U")
    outer.band(HEAD, range(4, 7), "E", faces=("right", "left", "back"))
    stamp(outer, HEAD, "front", ["EEEEEEEE", "EEERREEE", "EEERREEE", "UUUUUUUU", "EE____EE", "EE____EE", "UE____EU"])
    flanks(outer, HEAD, ["EEEEEREE", "EDEEEEED", "EEEDEEEE", "........", "...DEEEE", "..EEEEED", "....DDEE"])
    stamp(outer, HEAD, "back", ["EEEEEEEE", "EREEEEDE", "EEDEEDEE", "........", "EEEEDEEE", "EDEEEEER", "EEEDDEEE"])
    outer.put(HEAD, "front", 3, 1, tweak="4")
    outer.put(HEAD, "front", 4, 2, tweak="4")
    # Cuirass: faceted crystal plate, a glowing lumen core, gold trim, motes scattered over it.
    outer.fill(BODY, SIDES, "E", rows=range(0, 10))
    stamp(outer, BODY, "front", ["UEE__EEU", "EDEEEEDE", "EEDRRDEE", "EEDRRDEE", "EDEEEEDE", "EEEUUEEE", "EREEEEEE",
                                 "EEEEEEER", "UUUUUUUU", "EEEEEEEE"])
    stamp(outer, BODY, "back", ["UEEEEEEU", "EDEEEEDE", "EEDEEDEE", "EEEDDEEE", "EEDEEDEE", "EDEEEEDE", "EEEEEERE",
                                "ERDEEEEE", "UUUUUUUU", "EEEEEEEE"])
    flanks(outer, BODY, ["EEEE", "EDEE", "EEDE", "EEER", "EEDE", "EDEE", "EEEE", "EEEE", "UUUU", "EEEE"])
    outer.fill(ARM, ["top"], "E")
    outer.band(ARM, range(0, 4), "E")
    outer.band(ARM, [4], "U")
    stamp(outer, ARM, "front", [".D..", "..RE", ".D.."], v0=1)
    # Boots: crystal with a gold cuff, facets and a mote.
    outer.band(LEG, range(6, 12), "E")
    outer.band(LEG, [6], "U")
    stamp(outer, LEG, "front", ["...R", ".D..", "..D.", ".D.."], v0=7)
    outer.band(LEG, [11], "D")
    outer.fill(LEG, ["bottom"], "D")

    inner = Sheet()
    # Greaves: crystal legs with gold knee cops around a glowing stone, gold belt with a gem.
    inner.band(LEG, range(0, 9), "E")
    inner.fill(LEG, ["top"], "E")
    stamp(inner, LEG, "front", ["..D.", ".D..", "UUUU", "URRU", "UUUU", "..D.", ".D.."], v0=0)
    inner.band(BODY, range(7, 12), "E")
    inner.band(BODY, [7], "U")
    stamp(inner, BODY, "front", ["...RR...", "..D..D..", ".D....D.", "...D.R.."], v0=7)
    return outer.render(m), inner.render(m)


# Mining sets: (worn layers, icon materials, worn materials). Each has its own cut, drawn above and below.
MINING_SETS = {
    "delver": (delver_layers,
               {"T": DELVE_LEATHER, "D": DELVE_LEATHER.shifted(base=1), "C": BRASS, "K": BRASS.shifted(base=1, texture=flat), "A": AMBER,
                "P": DELVE_LEATHER.shifted(base=3, texture=flat)},
               {"T": DELVE_LEATHER, "D": DELVE_LEATHER.shifted(base=1), "C": BRASS.shifted(texture=polish), "A": AMBER,
                "K": BRASS.shifted(base=1, texture=flat), "P": DELVE_LEATHER.shifted(base=3, texture=flat)}),
    "emberheart": (emberheart_layers,
                   {"T": OBSIDIAN, "D": OBSIDIAN.shifted(base=1), "C": EMBER, "K": EMBER.shifted(base=1, texture=flat), "A": HEART},
                   {"T": OBSIDIAN, "D": OBSIDIAN.shifted(base=1), "C": EMBER.shifted(texture=polish), "A": HEART,
                    "K": EMBER.shifted(base=1, texture=flat)}),
    "deepwarden": (deepwarden_layers,
                   {"E": DEEP_STEEL, "D": DEEP_STEEL.shifted(base=0, texture=flat), "R": TEAL_GLOW, "U": IRON,
                    "H": DEEP_STEEL.shifted(base=1, texture=cloth), "P": DEEP_STEEL.shifted(texture=plates)},
                   {"E": DEEP_STEEL.shifted(texture=polish), "P": DEEP_STEEL.shifted(texture=plates),
                    "D": DEEP_STEEL.shifted(base=0, texture=flat), "R": TEAL_GLOW, "U": IRON.shifted(texture=flat),
                    "H": DEEP_STEEL.shifted(base=1, texture=cloth)}),
    "skyforged": (skyforged_layers,
                  {"E": SKYIRON, "D": SKYIRON.shifted(base=0, texture=flat), "R": PRISM_GLOW, "U": GOLD,
                   "F": SKYIRON.shifted(base=4, texture=flat)},
                  {"E": SKYIRON.shifted(texture=polish), "P": SKYIRON.shifted(texture=plates),
                   "D": SKYIRON.shifted(base=0, texture=flat), "R": PRISM_GLOW, "U": GOLD.shifted(texture=flat),
                   "F": SKYIRON.shifted(base=4, texture=flat)}),
    "lumenward": (lumenward_layers,
                  {"E": LUMEN_CRYSTAL, "D": LUMEN_CRYSTAL.shifted(base=1, texture=flat), "R": LUMEN_GLOW, "U": GOLD},
                  {"E": LUMEN_CRYSTAL.shifted(texture=polish), "D": LUMEN_CRYSTAL.shifted(base=1, texture=flat),
                   "R": LUMEN_GLOW, "U": GOLD.shifted(texture=flat)}),
}

# Icons. Delver: brass lamp helmet, patched jerkin, knee pads.
icon("delver_helmet", MINING_SETS["delver"][1], half(
    2, "......KK", "......KA", "......KA", ".....CKK", "....CCCC", "...CCCCC", "...CCCCC", "..KKKKKK", "..TT....",
    "..TT....", "..TD...."))
icon("delver_chestplate", MINING_SETS["delver"][1], sprite(
    2, "..TTTT....TTTT..", ".TTTTTT..TTTTTT.", ".TTTTTTTTTTTTTT.", ".TT.TDTTTTPP.TT.", ".TT.TTCTTTPP.TT.",
    ".CC.TTTDTTTT.CC.", "....TPPTCTTT....", "....TPPTTDTT....", "....KKKCCKKK....", "....TTTTTTTT....", "....DDDDDDDD...."))
icon("delver_leggings", MINING_SETS["delver"][1], half(
    2, "...CCCKA", "...TTTTT", "...TTTTT", "...TTTT.", "...CCCC.", "...CDDC.", "...CDDC.", "...CCCC.", "...TTTT.",
    "...TTTT.", "...DDDD."))
icon("delver_boots", MINING_SETS["delver"][1], half(
    4, "...DDDD.", "...TTTT.", "...TCCT.", "...TTTT.", "...TCCT.", ".TTTTTT.", ".TTTTTT.", ".DDDDDD."))

# Skyforged: winged helm, gold-trimmed breastplate with layered pauldrons, winged greaves and boots.
icon("skyforged_helmet", MINING_SETS["skyforged"][1], half(
    1, "F.......", "FF....UU", "FFF..UEE", "FFFF.EER", ".FFFUEEE", "...EUUUU", "...EEEEE", "...EE..U", "...EE..U",
    "...DD..."))
icon("skyforged_chestplate", MINING_SETS["skyforged"][1], half(
    2, "..EEEU..", ".EEEEEU.", ".UUUUEEU", ".EE.EUEE", ".UU.EUUR", ".EE.EEUU", ".UU.EEEU", "....UUUU", "....EEEE",
    "....EEEE", "....DDDD"))
icon("skyforged_leggings", MINING_SETS["skyforged"][1], half(
    2, "...UUUUU", "...EEEEE", "...EEEEE", "..FEEEE.", ".FFUUUU.", "..FEEEE.", "...EEEE.", "...EEEE.", "...EEEE.",
    "...EEEE.", "...UUUU."))
icon("skyforged_boots", MINING_SETS["skyforged"][1], half(
    4, "...UUUU.", "FF.EEEE.", ".FFEEEE.", "..FEEEE.", "...EEEE.", ".EEEEEE.", ".EEEEEE.", ".DDDDDD."))

# Deepwarden: hooded helm with a glowing visor slit, heavy rune-cut plate.
icon("deepwarden_helmet", MINING_SETS["deepwarden"][1], half(
    1, "......HH", ".....HHH", "....HHEE", "...HHEEE", "...HHEEE", "...HHRRR", "...HHEEU", "...HHEEU", "..HHHEED",
    "..HHHHDD", "...HH..."))
icon("deepwarden_chestplate", MINING_SETS["deepwarden"][1], half(
    2, ".PPPPP..", "PPEEEPP.", "PEEEEEEE", ".EE.EREE", ".PE.EERE", ".PE.EEER", "....EEER", "....UUUU", "....PPPP",
    "....PPPP", "....DDDD"))
icon("deepwarden_leggings", MINING_SETS["deepwarden"][1], half(
    2, "...UUUUU", "...EEEEE", "...EEEEE", "...PPPP.", "...PRRP.", "...PPPP.", "...EEEE.", "...EEEE.", "...EEEE.",
    "...EEEE.", "...DDDD."))
icon("deepwarden_boots", MINING_SETS["deepwarden"][1], half(
    4, "...UUUU.", "...EEEE.", "...EEEE.", "...PPPP.", "...PRRP.", ".EEEEEE.", ".EEEEEE.", ".DDDDDD."))

# Emberheart: horned obsidian helm, a heartstone in the chest, ember cracks.
icon("emberheart_helmet", MINING_SETS["emberheart"][1], half(
    1, "T.......", "TT......", ".TT.TTCC", "..TTTTTC", "...TTTTT", "...TTCCT", "...TTTTT", "...TTTTT", "...KKKKK",
    "...TDDDD"))
icon("emberheart_chestplate", MINING_SETS["emberheart"][1], half(
    0, ".T......", ".TT.T...", "..TTTT..", ".TTTTTT.", ".TTTTCTT", ".TT.TTKK", ".TT.TKAA", ".KK.TKAA", "....TTKK",
    "....KKKK", "....TTTT", "....TCTT", "....DDDD"))
icon("emberheart_leggings", MINING_SETS["emberheart"][1], half(
    2, "...KKKAA", "...TTTTT", "...TTCTT", "...TTTT.", "...KKKK.", "...TTCT.", "...TKKT.", "...TTTT.", "...TCTT.",
    "...TTTT.", "...DDDD."))
icon("emberheart_boots", MINING_SETS["emberheart"][1], half(
    4, "...KKKK.", "...TTTT.", "...TCTT.", "...TTTT.", "...TTCT.", ".TTTTTT.", ".TTTCTT.", ".KKKKKK."))

# Lumenward: crystal crown, spiked shoulders, a glowing core.
icon("lumenward_helmet", MINING_SETS["lumenward"][1], half(
    0, ".......R", ".......E", "..E...EE", "..EE.EEE", "...EEEEE", "...EEEER", "...UUUUU", "...EEEEE", "...EE...",
    "...EE...", "...DD..."))
icon("lumenward_chestplate", MINING_SETS["lumenward"][1], half(
    0, ".E......", ".EE.E...", "..EEEE..", ".EEEEEE.", "UUEEEEEE", ".EE.EDEE", ".EE.EERR", ".EE.EDRR", ".UU.EEER",
    "....UUUU", "....EEEE", "....EEEE", "....DDDD"))
icon("lumenward_leggings", MINING_SETS["lumenward"][1], half(
    2, "...UUURR", "...EEEEE", "...EEDEE", "...EEEE.", "...UUUU.", "...URRU.", "...UUUU.", "...EDEE.", "...EEEE.",
    "...EEDE.", "...DDDD."))
icon("lumenward_boots", MINING_SETS["lumenward"][1], half(
    3, "...E....", "...UUUU.", "...EEEE.", "...EDEE.", "...EEEE.", "...EEEE.", ".EEEEEE.", ".EEDEEE.", ".DDDDDD."))

# Pickaxes, weakest to strongest: head, rivets/lamp, handle.
MINING_PICKAXES = {
    "quarry_pick": {"T": IRON.shifted(texture=flat), "C": IRON.shifted(base=1, texture=flat), "A": IRON, "K": HANDLE},
    "delver_pickaxe": {"T": BRASS.shifted(texture=flat), "C": DELVE_LEATHER.shifted(texture=flat), "A": AMBER, "K": HANDLE},
    "skyiron_pickaxe": {"T": SKYIRON.shifted(texture=flat), "C": GOLD.shifted(texture=flat), "A": PRISM_GLOW, "K": DUSK_METAL},
    "duskbore_pick": {"T": DUSKQUARTZ.shifted(texture=flat), "C": SKYIRON.shifted(texture=flat), "A": TEAL_GLOW, "K": DUSK_METAL},
    "emberheart_pickaxe": {"T": EMBER.shifted(texture=flat), "C": OBSIDIAN.shifted(texture=flat), "A": HEART, "K": OBSIDIAN},
    "lumen_breaker": {"T": Material(["4a8ab0", "7ac4e4", "b0ecff", "e0fbff", "ffffff"]), "C": GOLD.shifted(texture=flat),
                      "A": PRISM_GLOW, "K": DUSK_METAL},
}
for pick_name, pick_mats in MINING_PICKAXES.items():
    mask, _, tweak = ICONS["prospector_pickaxe"]
    ICONS[pick_name] = (mask, pick_mats, tweak)

# Farming sets, each with its own cut: set: (worn layers, icon materials, worn materials).
DENIM = Material(["1c2840", "2c3e62", "3e5888", "5a78aa", "88a4cc"], texture=cloth)
LINEN_GOLD = Material(["6a4a10", "a87a20", "d8aa3a", "f2d070", "fff0b0"], texture=weave)
SUN_ORANGE = Material(["7a3a08", "b8601a", "e88a2a", "ffb050", "ffd890"])
FROST_PINE = Material(["1a3a34", "2a5a50", "3e7c6e", "62a494", "9cd0c0"], texture=cloth)
ICE = Material(["4a7898", "74a8c8", "a4d4ec", "d0f0ff", "ffffff"], texture=sheen)
WOOL_WHITE = Material(["8a8a90", "b4b4bc", "d4d4dc", "ececf2", "ffffff"], texture=cloth)
FROSTBERRY = Material(["24306a", "3a4ea0", "5a76d0", "8eaaf0", "c8d8ff"], base=3)
CHARCOAL = Material(["141418", "24242a", "36363e", "4c4c56", "6a6a76"], texture=cloth)
PEPPER_RED = Material(["5a0a08", "8a1810", "c02a1a", "e85a3a", "ff9a7a"])
MIDNIGHT = Material(["0a0e24", "161e40", "243066", "34448e", "4e62b4"], texture=cloth)
SILVER = Material(["5a6070", "8a92a4", "b8c0d0", "dce2ee", "ffffff"], texture=sheen)
MOON_GLOW = Material(["4a4a8a", "7a7ac0", "b0b0f0", "dcdcff", "ffffff"], base=3)
def fieldhand_layers(m):
    outer = Sheet()
    # Flat denim cap: a red bandana band, a short peak, bandana tails down the back.
    outer.fill(HEAD, ["top"], "S")
    outer.tweak_face(HEAD, "top", rim)
    outer.put(HEAD, "top", 3, 3, "W")
    outer.band(HEAD, range(0, 2), "S")
    outer.band(HEAD, [2], "G")
    outer.band(HEAD, [3], "Z", faces=("front",))
    flanks(outer, HEAD, ["........", "........", "........", "....ZZZZ"])
    stamp(outer, HEAD, "back", ["........", "........", "........", "...GG...", "..G..G..", "..G..G.."])
    # Dungarees: red shirt, denim bib and straps with wheat-gold buttons, a patch pocket, a patched back.
    outer.fill(BODY, SIDES, "G", rows=range(0, 3))
    outer.fill(BODY, SIDES, "S", rows=range(3, 10))
    stamp(outer, BODY, "front", ["GGG__GGG", "GGSGGSGG", "GGSGGSGG", "SSWSSWSS", "SSSSSSSS", "SSZZZZSS", "SSZZZZSS",
                                 "SSSSSSSS", "SSSSSSSS", "ZZZZZZZZ"])
    stamp(outer, BODY, "back", ["GGGGGGGG", "GGSGGSGG", "GGGSSGGG", "SSSSSSSS", "SSSSSSSS", "SSSSLLSS", "SSSSLLSS",
                                "SSSSSSSS", "SSSSSSSS", "ZZZZZZZZ"])
    outer.band(BODY, [9], "Z", faces=("right", "left"))
    outer.fill(ARM, ["top"], "G")
    outer.band(ARM, range(0, 4), "G")
    outer.band(ARM, [4], "Z")
    # Boots: leather under rolled denim cuffs.
    outer.band(LEG, range(6, 12), "L")
    outer.band(LEG, [6], "S")
    outer.band(LEG, [7], "Z")
    outer.band(LEG, [11], "K")
    outer.fill(LEG, ["bottom"], "K")
    outer.put(LEG, "front", 1, 9, tweak="4")
    outer.put(LEG, "front", 2, 10, tweak="4")

    inner = Sheet()
    # Dungaree legs: leather knee patches with straw stitches, a leather belt with a wheat-gold buckle.
    inner.band(LEG, range(0, 9), "S")
    inner.fill(LEG, ["top"], "S")
    stamp(inner, LEG, "front", ["LLLL", "LWWL", "LLLL"], v0=3)
    inner.band(BODY, range(7, 12), "S")
    inner.band(BODY, [7], "L")
    stamp(inner, BODY, "front", ["...WW..."], v0=7)
    return outer.render(m), inner.render(m)


def sunward_layers(m):
    outer = Sheet()
    # Sun hat: a golden crown, an orange band, a glowing sun disc on the front.
    outer.fill(HEAD, ["top"], "S")
    outer.tweak_face(HEAD, "top", rim)
    outer.band(HEAD, range(0, 3), "S")
    outer.band(HEAD, [3], "G")
    stamp(outer, HEAD, "front", ["SSGWWGSS", "SGWWWWGS", "SSGWWGSS"])
    outer.put(HEAD, "front", 3, 1, tweak="4")
    # Robe: golden linen, a sunburst on the chest and back, an orange sash and a dotted hem.
    outer.fill(BODY, SIDES, "S", rows=range(0, 10))
    stamp(outer, BODY, "front", ["GGG__GGG", "SSSGGSSS", "SSWGGWSS", "SSGWWGSS", "SSGWWGSS", "SSWGGWSS", "SSSSSSSS",
                                 "GGGGGGGG", "SSSSSSSS", "GWGWGWGW"])
    stamp(outer, BODY, "back", ["GGGGGGGG", "SSSSSSSS", "SSSSSSSS", "SSGGGGSS", "SSGWWGSS", "SSGWWGSS", "SSGGGGSS",
                                "GGGGGGGG", "SSSSSSSS", "GWGWGWGW"])
    flanks(outer, BODY, ["GGGG", "SSSS", "SSSS", "SSSS", "SSSS", "SSSS", "SSSS", "GGGG", "SSSS", "GGGG"])
    outer.fill(ARM, ["top"], "G")
    outer.band(ARM, range(0, 4), "S")
    outer.band(ARM, [0, 4], "G")
    # Sandal boots: hide with a golden cuff and an orange strap.
    outer.band(LEG, range(6, 12), "L")
    outer.band(LEG, [6], "S")
    outer.band(LEG, [8], "G")
    outer.band(LEG, [11], "K")
    outer.fill(LEG, ["bottom"], "K")

    inner = Sheet()
    # Skirted trousers: gold linen with an orange stripe and knee band, a long tabard panel front and back.
    inner.band(LEG, range(0, 9), "S")
    inner.fill(LEG, ["top"], "S")
    inner.band(LEG, [5], "G")
    flanks(inner, LEG, [".G.."] * 5, v0=0)
    flanks(inner, LEG, [".G.."] * 3, v0=6)
    inner.band(BODY, range(7, 12), "S")
    inner.band(BODY, [7], "L")
    stamp(inner, BODY, "front", ["...WW..."], v0=7)
    inner.fill(BODY, ["front", "back"], "Z", rows=range(8, 11), cols=range(2, 6))
    inner.band(BODY, [11], "G")
    return outer.render(m), inner.render(m)


def frostgrove_layers(m):
    outer = Sheet()
    # Parka hood: pine green with a thick white fur ring round the face opening and a fur chin.
    outer.fill(HEAD, ["top"], "O")
    outer.tweak_face(HEAD, "top", rim)
    outer.fill(HEAD, ["top"], "M", rows=range(6, 8))
    outer.fill(HEAD, SIDES, "O", rows=range(0, 7))
    stamp(outer, HEAD, "front", ["........", "........", "MMMMMMMM", "MM____MM", "MM____MM", "MM____MM", "MM____MM",
                                 "MMMMMMMM"])
    flanks(outer, HEAD, ["........", "........", "......MM", "......MM", "......MM", "......MM", "......MM", "MMMMMMMM"])
    stamp(outer, HEAD, "back", ["........", "...KK...", "...KK...", "...KK...", "...KK...", "...KK...", "...KK...", "MMMMMMMM"])
    # Parka: a fur collar, an ice-toggle placket, a frostberry rope belt with a silver buckle, a fur hem.
    outer.fill(BODY, SIDES, "O", rows=range(0, 10))
    stamp(outer, BODY, "front", ["MMM__MMM", "MMMMMMMM", "OOOKKOOO", "OOHKKHOO", "OOOKKOOO", "OOHKKHOO", "OOOKKOOO",
                                 "OOHKKHOO", "JJJIIJJJ", "MMMMMMMM"])
    stamp(outer, BODY, "back", ["MMMMMMMM", "MMMMMMMM", "OOOKKOOO", "OOOKKOOO", "OOOKKOOO", "OOOKKOOO", "OOOKKOOO",
                                "OOOKKOOO", "JJJJJJJJ", "MMMMMMMM"])
    flanks(outer, BODY, ["MMMM", "MMMM", "OOOO", "OOOO", "OOOO", "OOOO", "OOOO", "OOOO", "JJJJ", "MMMM"])
    outer.fill(ARM, ["top"], "O")
    outer.band(ARM, range(0, 3), "O")
    outer.band(ARM, [3, 4], "M")
    # Boots: fur-lined with silver laces.
    outer.band(LEG, range(6, 12), "O")
    outer.band(LEG, [6, 7], "M")
    outer.band(LEG, [11], "H")
    outer.fill(LEG, ["bottom"], "K")
    stamp(outer, LEG, "front", [".I..", "..I.", ".I.."], v0=8)

    inner = Sheet()
    # Trousers: pine wool with dark side seams and icy knee studs, a berry belt, a fur hem.
    inner.band(LEG, range(0, 9), "O")
    inner.fill(LEG, ["top"], "O")
    flanks(inner, LEG, [".K.."] * 9)
    stamp(inner, LEG, "front", [".HH.", ".HH."], v0=4)
    inner.band(BODY, range(7, 12), "O")
    inner.band(BODY, [7], "J")
    stamp(inner, BODY, "front", ["...II..."], v0=7)
    inner.band(BODY, [11], "M")
    return outer.render(m), inner.render(m)


def cinderfield_layers(m):
    outer = Sheet()
    # Scorched hat: charcoal with ember flecks, a pepper-red band and a ragged burnt fringe.
    outer.fill(HEAD, ["top"], "S")
    outer.tweak_face(HEAD, "top", rim)
    stamp(outer, HEAD, "top", ["........", "..W.....", "........", ".....W..", "........", "..W.....", "........", "......W."])
    outer.band(HEAD, range(0, 3), "S")
    outer.band(HEAD, [3], "G")
    stamp(outer, HEAD, "front", ["SSSWSSSS", "SSSSSSWS", "SWSSSSSS", "........", "G.G..G.G"])
    flanks(outer, HEAD, ["........", "..W.....", "........", "........", "G.G.G.G."])
    stamp(outer, HEAD, "back", ["........", "....W...", "...W....", "........", "G.G.G.G.", "G...G..."])
    # Tunic: charcoal, a pepper-red scarf with a hanging tail, ember cracks, an obsidian belt, a burnt hem.
    outer.fill(BODY, SIDES, "S", rows=range(0, 9))
    stamp(outer, BODY, "front", ["GGG__GGG", "GGGGGGGG", "SGGSSSSS", "SGGSSSWS", "SGGSSWWS", "SGGSSWSS", "SWWSWSSS",
                                 "SSSSSSSS", "LLLWWLLL", "SW.SSW.S"])
    stamp(outer, BODY, "back", ["GGGGGGGG", "GGGGGGGG", "SSSSSSSS", "SSSWSSSS", "SSWWSSSS", "SSWSSSWS", "SSSSSWWS",
                                "SSSSSSWS", "LLLLLLLL", "S.SSW.SS"])
    flanks(outer, BODY, ["GGGG", "GGGG", "SSSS", "SSSS", "SSWS", "SSSS", "SSSS", "SSSS", "LLLL", "S.SW"])
    outer.fill(ARM, ["top"], "S")
    outer.band(ARM, range(0, 3), "S")
    outer.band(ARM, [3], "G")
    flanks(outer, ARM, ["....", "....", "....", "....", "Z.Z."])
    stamp(outer, ARM, "front", ["Z.Z."], v0=4)
    stamp(outer, ARM, "back", ["Z.Z."], v0=4)
    # Boots: obsidian under a red cuff, a crack up the front, glowing soles.
    outer.band(LEG, range(6, 12), "L")
    outer.band(LEG, [6], "G")
    outer.band(LEG, [11], "W")
    outer.fill(LEG, ["bottom"], "K")
    stamp(outer, LEG, "front", [".W..", "..W."], v0=8)

    inner = Sheet()
    # Trousers: charcoal with ember cracks, red knee patches, an obsidian belt with an ember buckle, a ragged hem.
    inner.band(LEG, range(0, 9), "S")
    inner.fill(LEG, ["top"], "S")
    stamp(inner, LEG, "front", ["..W.", ".W..", "....", ".GG.", ".GG.", "..W."], v0=1)
    flanks(inner, LEG, ["....", "..W.", ".W.."], v0=2)
    inner.band(BODY, range(7, 12), "S")
    inner.band(BODY, [7], "L")
    stamp(inner, BODY, "front", ["...WW...", "........", ".W......", "......W."], v0=7)
    stamp(inner, BODY, "front", ["S_SS_SS_"], v0=11)
    stamp(inner, BODY, "back", ["S_SS_SS_"], v0=11)
    return outer.render(m), inner.render(m)


def moonharvest_layers(m):
    outer = Sheet()
    # Crescent circlet and veil: a silver band under a glowing crescent, a midnight veil framing the face.
    outer.fill(HEAD, ["top"], "O")
    outer.tweak_face(HEAD, "top", rim)
    stamp(outer, HEAD, "top", ["........", "..H.....", "........", ".....H..", "........", "........", "...H....", "........"])
    outer.band(HEAD, range(0, 3), "O")
    outer.band(HEAD, [3], "M")
    stamp(outer, HEAD, "front", ["OHOOOOHO", "OOHOOHOO", "OOOHHOOO"])
    flanks(outer, HEAD, ["........", "........", "........", "........", "OOOOOO..", "OOOOO...", "OKOO...."])
    stamp(outer, HEAD, "back", ["........", "........", "........", "........", "OOOOOOOO", "OOHOOOOO", "OKOOOOKO", "OOOOOHOO"])
    outer.put(HEAD, "front", 3, 2, tweak="4")
    outer.put(HEAD, "front", 4, 2, tweak="4")
    # Robe: midnight with a silver collar, a glowing crescent on the chest and a bigger one on the back, silver hem.
    outer.fill(BODY, SIDES, "O", rows=range(0, 10))
    stamp(outer, BODY, "front", ["MMM__MMM", "OMMOOMMO", "OOOOOOHO", "OOOHHOOO", "OOHOOOOO", "OOHOOOOO", "OOOHHOOO",
                                 "OOOOOHOO", "KKKKKKKK", "MMMMMMMM"])
    stamp(outer, BODY, "back", ["MMMMMMMM", "OOOOOOOO", "OOOHHHOO", "OOHOOOOO", "OOHOOOOO", "OOHOOOOO", "OOOHHHOO",
                                "OOOOOOHO", "KKKKKKKK", "MMMMMMMM"])
    flanks(outer, BODY, ["MMMM", "OOOO", "OOHO", "OOOO", "OOOO", "OOOO", "OOOO", "OOOO", "KKKK", "MMMM"])
    outer.fill(ARM, ["top"], "O")
    outer.band(ARM, range(0, 4), "O")
    outer.band(ARM, [4], "M")
    stamp(outer, ARM, "front", [".H.."], v0=1)
    # Boots: midnight with silver cuffs and toes, a star on the front.
    outer.band(LEG, range(6, 12), "O")
    outer.band(LEG, [6, 11], "M")
    outer.fill(LEG, ["bottom"], "K")
    stamp(outer, LEG, "front", ["..H.", ".H.."], v0=8)

    inner = Sheet()
    # Trousers: midnight with a silver side stripe and a star on the knee, a silver sash with a crescent clasp.
    inner.band(LEG, range(0, 9), "O")
    inner.fill(LEG, ["top"], "O")
    flanks(inner, LEG, [".M.."] * 9)
    stamp(inner, LEG, "front", ["..H."], v0=4)
    inner.band(BODY, range(7, 12), "O")
    inner.band(BODY, [7], "M")
    stamp(inner, BODY, "front", ["...HH..."], v0=7)
    inner.band(BODY, [11], "M")
    return outer.render(m), inner.render(m)


PUMPKIN = Material(["5e2a08", "9c4a0e", "e8761b", "f79a2c", "ffb347"])
CANDLE = Material(["c0561a", "e88a20", "ffc84a", "ffe27a", "fff3b0"], base=3)
HALLOW_NIGHT = Material(["0c0812", "1b1424", "2e2140", "4a3466", "6c4e92"], base=1, texture=cloth)
HALLOW_PURPLE = Material(["2a1048", "4a2478", "6e3ca8", "9462d0", "bc90f0"], texture=cloth)
PUMPKIN_STEM = Material(["2c2210", "4a3a18", "6a5426", "8a7034", "a88a44"])
ROPE = Material(["4a3a22", "7a6238", "a88a50", "c9ad6c", "e4cf92"], texture=weave)
DARK_IRON = Material(["0e0d14", "1f1e2a", "34323f", "55536a", "8886a0"], texture=sheen)
BONE = Material(["4a4034", "8a7c66", "bcae92", "e4d8bc", "faf2dc"])
SOUL = Material(["0e3844", "1a6e80", "36c6d9", "7df9ff", "e6ffff"])
BLACKWOOD = Material(["07050b", "120d1a", "1e1630", "30244a", "443468"], base=1)


def hallowed_layers(m):
    outer = Sheet()
    # Jack's Grin: a carved pumpkin over the whole head, a glowing candle-lit face, ridges down the sides.
    outer.fill(HEAD, ["top"], "P")
    outer.tweak_face(HEAD, "top", rim)
    stamp(outer, HEAD, "top", ["........", "...SS...", "...SS...", "........", "........", "........", "........", "........"])
    outer.band(HEAD, range(0, 8), "P")
    stamp(outer, HEAD, "front", ["PDPPPPDP", "PDPPPPDP", "PDPGPGDP", "PDGGPGGP", "PDPPGPDP", "PGGGGGGP", "PDGPGPGP", "PPPPPPPP"])
    flanks(outer, HEAD, ["PPPPPPPP", "DPPPPPPD", "PPPPPPPP", "PPPDPPPP", "PPPDPPPP", "DPPDPPPD", "PPPPPPPP", "PPPPPPPP"])
    stamp(outer, HEAD, "back", ["PPPPPPPP", "PDPPPPDP", "PDPPPPDP", "PDPPPPDP", "PDPPPPDP", "PDPPPPDP", "PDPPPPDP", "PPPPPPPP"])
    # Coat: black wool with an orange collar, stitched pockets and a little pumpkin on the chest, orange hem.
    outer.fill(BODY, SIDES, "N", rows=range(0, 12))
    stamp(outer, BODY, "front", ["OOO__OOO", "NNNNNNNN", "NNNOONNN", "NNPPPPNN", "NNPGGPNN", "NNNPPNNN", "NNNNNNNN",
                                 "NOONNOON", "NONNNNON", "NNNNNNNN", "NNNNNNNN", "OOOOOOOO"])
    stamp(outer, BODY, "back", ["OOOOOOOO", "NNNNNNNN", "NNNOONNN", "NNPPPPNN", "NPGPPGPN", "NPPGGPPN", "NNPPPPNN",
                                "NNNNNNNN", "NNNONNNN", "NNNNNNNN", "NNNNNNNN", "OOOOOOOO"])
    flanks(outer, BODY, ["OOOO", "NNNN", "NNON", "NNNN", "NNNN", "NNNN", "NNNN", "NNNN", "NNNN", "NNNN", "NNNN", "OOOO"])
    outer.fill(ARM, ["top"], "N")
    outer.band(ARM, range(0, 4), "N")
    outer.band(ARM, [4], "O")
    stamp(outer, ARM, "front", [".O.."], v0=1)
    # Boots: black with orange cuffs and criss-cross laces, a purple sheen on the toe.
    outer.band(LEG, range(6, 12), "N")
    outer.band(LEG, [6], "O")
    outer.fill(LEG, ["bottom"], "K")
    stamp(outer, LEG, "front", ["O..O", ".OO.", "O..O", "UU..", "...."], v0=7)
    flanks(outer, LEG, ["....", ".U..", "...."], v0=9)

    inner = Sheet()
    # Scarecrow trousers: dark patched legs (orange and purple patches), straw at the hem, a rope belt.
    inner.band(LEG, range(0, 9), "N")
    inner.fill(LEG, ["top"], "N")
    stamp(inner, LEG, "front", ["OOO.", "OPO.", "OOO."], v0=2)
    stamp(inner, LEG, "front", [".UU.", ".UU."], v0=5)
    stamp(inner, LEG, "back", [".UU.", ".UU."], v0=3)
    flanks(inner, LEG, ["..O.", "...."], v0=1)
    stamp(inner, LEG, "front", ["SNSS"], v0=8)
    stamp(inner, LEG, "back", ["SSNS"], v0=8)
    flanks(inner, LEG, ["SNSN"], v0=8)
    inner.band(BODY, range(7, 12), "N")
    inner.band(BODY, [7], "R")
    stamp(inner, BODY, "front", ["...RR...", "..S..S.."], v0=7)
    return outer.render(m), inner.render(m)


# Farming sets: (worn layers, icon materials, worn materials). Each has its own cut.
FARMING_SETS = {
    "fieldhand": (fieldhand_layers,
                  {"S": DENIM, "Z": DENIM.shifted(base=1), "G": CRIMSON, "W": WHEAT, "L": SOIL_LEATHER,
                   "K": SOIL_LEATHER.shifted(base=1, texture=flat)},
                  {"S": DENIM, "Z": DENIM.shifted(base=1), "G": CRIMSON.shifted(texture=cloth), "W": WHEAT, "L": SOIL_LEATHER,
                   "K": SOIL_LEATHER.shifted(base=0, texture=flat)}),
    "sunward": (sunward_layers,
                {"S": LINEN_GOLD, "Z": LINEN_GOLD.shifted(base=1), "G": SUN_ORANGE, "W": AMBER, "L": HIDE,
                 "K": HIDE.shifted(base=1, texture=flat)},
                {"S": LINEN_GOLD, "Z": LINEN_GOLD.shifted(base=1), "G": SUN_ORANGE.shifted(texture=cloth), "W": AMBER, "L": HIDE,
                 "K": HIDE.shifted(base=0, texture=flat)}),
    "frostgrove": (frostgrove_layers,
                   {"O": FROST_PINE, "K": FROST_PINE.shifted(base=1, texture=flat), "M": WOOL_WHITE, "H": ICE, "I": SILVER,
                    "J": FROSTBERRY},
                   {"O": FROST_PINE, "K": FROST_PINE.shifted(base=0, texture=flat), "M": WOOL_WHITE, "H": ICE,
                    "I": SILVER.shifted(texture=flat), "J": FROSTBERRY}),
    "cinderfield": (cinderfield_layers,
                    {"S": CHARCOAL, "Z": CHARCOAL.shifted(base=1), "G": PEPPER_RED, "W": EMBER, "L": OBSIDIAN,
                     "K": OBSIDIAN.shifted(base=1, texture=flat)},
                    {"S": CHARCOAL, "Z": CHARCOAL.shifted(base=1), "G": PEPPER_RED.shifted(texture=cloth), "W": EMBER, "L": OBSIDIAN,
                     "K": OBSIDIAN.shifted(base=0, texture=flat)}),
    "moonharvest": (moonharvest_layers,
                    {"O": MIDNIGHT, "K": MIDNIGHT.shifted(base=1, texture=flat), "M": SILVER, "H": MOON_GLOW},
                    {"O": MIDNIGHT, "K": MIDNIGHT.shifted(base=0, texture=flat), "M": SILVER.shifted(texture=cloth),
                     "H": MOON_GLOW}),
    "hallowed": (hallowed_layers,
                 {"P": PUMPKIN, "D": PUMPKIN.shifted(base=1), "G": CANDLE, "S": PUMPKIN_STEM, "N": HALLOW_NIGHT,
                  "K": HALLOW_NIGHT.shifted(base=0, texture=flat), "O": PUMPKIN.shifted(texture=flat), "U": HALLOW_PURPLE,
                  "R": ROPE, "L": LEAF},
                 {"P": PUMPKIN, "D": PUMPKIN.shifted(base=1), "G": CANDLE, "S": PUMPKIN_STEM, "N": HALLOW_NIGHT,
                  "K": HALLOW_NIGHT.shifted(base=0, texture=flat), "O": PUMPKIN.shifted(texture=flat), "U": HALLOW_PURPLE,
                  "R": ROPE.shifted(texture=weave), "L": LEAF}),
}

# Icons. Fieldhand: flat denim cap with a red band, dungarees over a red shirt, patched knees.
icon("fieldhand_helmet", FARMING_SETS["fieldhand"][1], half(
    3, ".....SSS", "....SSSS", "...SSSSS", "...SSSSS", "...GGGGG", ".GZZZZZZ", ".G.ZZZZZ", ".G......"))
icon("fieldhand_chestplate", FARMING_SETS["fieldhand"][1], half(
    2, "..GGSG..", ".GGGSSG.", ".GGGWSSS", ".GG.SSSS", ".GG.SSZZ", ".ZZ.SSZZ", "....SSSS", "....SSSS", "....SSSS",
    "....ZZZZ"))
icon("fieldhand_leggings", FARMING_SETS["fieldhand"][1], half(
    2, "...LLLLL", "...SSSSS", "...SSSSS", "...SSSS.", "...SLLS.", "...SLLS.", "...SSSS.", "...SSSS.", "...SSSS.",
    "...SSSS.", "...ZZZZ."))
icon("fieldhand_boots", FARMING_SETS["fieldhand"][1], half(
    4, "...SSSS.", "...ZZZZ.", "...LLLL.", "...LLLL.", "...LLLL.", ".LLLLLL.", ".LLLLLL.", ".KKKKKK."))

# Sunward: a pointed sun hat with a disc, a golden robe with a sunburst, sandal boots.
icon("sunward_helmet", FARMING_SETS["sunward"][1], half(
    2, ".......S", "......SS", ".....SSW", "....SSWW", "....SSWW", "...GGGGG", ".SSSSSSS", "SSSSSSSS", ".ZZZZZZZ"))
icon("sunward_chestplate", FARMING_SETS["sunward"][1], half(
    2, "..SSSG..", ".SSSSSG.", ".SSSSSSS", ".SS.SSGG", ".SS.SGWW", ".SS.SGWW", ".GG.SSGG", "....GGGG", "....SSSS",
    "....SSSS", "....GGGG"))
icon("sunward_leggings", FARMING_SETS["sunward"][1], half(
    2, "...GGGGG", "...SSSSS", "...SSSSS", "...SSSS.", "...GGGG.", "...SSSS.", "...SSSS.", "...SSSS.", "...SSSS.",
    "...SSSS.", "...GGGG."))
icon("sunward_boots", FARMING_SETS["sunward"][1], half(
    4, "...SSSS.", "...GGGG.", "...LLLL.", "...LLLL.", "...GGGG.", ".LLLLLL.", ".LLLLLL.", ".KKKKKK."))

# Frostgrove: a fur-ringed parka hood with a berry, a toggled parka, fur-lined boots.
icon("frostgrove_helmet", FARMING_SETS["frostgrove"][1], half(
    1, ".......J", "......OO", ".....OOO", "....OOOO", "...OOOOO", "...MMMMM", "..MMKKKK", "..MMKKKK", "..MMKKKK",
    "..MMMMMM"))
icon("frostgrove_chestplate", FARMING_SETS["frostgrove"][1], half(
    2, "..MMMM..", ".MMMMMM.", ".OOOOOOK", ".OO.OOHK", ".OO.OOOK", ".MM.OOHK", "....OOOK", "....JJJI", "....OOOK",
    "....MMMM"))
icon("frostgrove_leggings", FARMING_SETS["frostgrove"][1], half(
    2, "...JJJII", "...OOOOO", "...OOOOO", "...OOOO.", "...OHHO.", "...OHHO.", "...OOOO.", "...OOOO.", "...OOOO.",
    "...MMMM.", "...MMMM."))
icon("frostgrove_boots", FARMING_SETS["frostgrove"][1], half(
    4, "...MMMM.", "...MMMM.", "...OOOO.", "...OIIO.", "...OOOO.", ".OOOOOO.", ".OOOOOO.", ".HHHHHH."))

# Cinderfield: a scorched pointed hat, a charcoal tunic with a red scarf and ember cracks, a ragged hem.
icon("cinderfield_helmet", FARMING_SETS["cinderfield"][1], half(
    1, ".......S", "......SS", ".....SSS", ".....SWS", "....SSSS", "...GGGGG", ".SSSSSSS", "SSSSSSSZ", ".ZZ.Z.W."))
icon("cinderfield_chestplate", FARMING_SETS["cinderfield"][1], half(
    2, "..SSSS..", ".SSGGGG.", ".SSSGGSS", ".SS.GGSW", ".SS.GSWS", ".ZZ.SSWS", "....SSSW", "....LLWW", "....SSSS",
    "....SWSS", "....S.S."))
icon("cinderfield_leggings", FARMING_SETS["cinderfield"][1], half(
    2, "...LLLWW", "...SSSSS", "...SSWSS", "...SSSS.", "...SGGS.", "...SGGS.", "...SSSS.", "...SWSS.", "...SSSS.",
    "...SSSS.", "...S.S.."))
icon("cinderfield_boots", FARMING_SETS["cinderfield"][1], half(
    4, "...GGGG.", "...LLLL.", "...LLWL.", "...LLLL.", "...LWLL.", ".LLLLLL.", ".LLLLLL.", ".WWWWWW."))

# Moonharvest: a crescent circlet and veil, a midnight robe with a silver crescent, star-trimmed legs.
icon("moonharvest_helmet", FARMING_SETS["moonharvest"][1], half(
    2, "......OO", ".....OOO", "....OHOO", "...OOOHH", "...MMMMM", "..OOOOOO", "..OOO...", "..OOO...", "..KKO..."))
icon("moonharvest_chestplate", FARMING_SETS["moonharvest"][1], sprite(
    2, "..OOMM....MMOO..", ".OOOOMM..MMOOOO.", ".MMMOOOOOOOOMMM.", ".OO.OOOHHOOO.OO.", ".OO.OOHOOOOO.OO.",
    ".MM.OOHOOOOO.MM.", "....OOOHHOOO....", "....MMMMMMMM....", "....OOOOOOHO....", "....OOOOOOOO....",
    "....KKKKKKKK...."))
icon("moonharvest_leggings", FARMING_SETS["moonharvest"][1], half(
    2, "...MMMHH", "...OOOOO", "...OOOOO", "...OOOO.", "...OHOO.", "...OOOO.", "...OOOO.", "...OOOO.", "...OOOO.",
    "...OOOO.", "...MMMM."))
icon("moonharvest_boots", FARMING_SETS["moonharvest"][1], half(
    4, "...MMMM.", "...OOOO.", "...OHOO.", "...OOOO.", "...OOOO.", ".OOOOOO.", ".OOOOOO.", ".MMMMMM."))

# Hallowed: a carved jack-o-lantern head, a black coat with a pumpkin on the chest, patched trousers, orange-laced boots.
icon("hallowed_helmet", FARMING_SETS["hallowed"][1], [
    "................",
    ".......SS.......",
    ".......SS..LL...",
    "....PPPPPPPP.L..",
    "..PPPPDPPDPPPP..",
    ".PPPPPDPPDPPPPP.",
    ".PPPGPDPPDPGPPP.",
    ".PPGGGDPPDGGGPP.",
    ".PPPPPDGGDPPPPP.",
    ".PPPPPDPPDPPPPP.",
    ".PPGGGGGGGGGGPP.",
    ".PPPGPGGGGPGPPP.",
    ".PPPPPPGGPPPPPP.",
    "..PPPPPPPPPPPP..",
    "...PPPPPPPPPP...",
    "................"])
icon("hallowed_chestplate", FARMING_SETS["hallowed"][1], sprite(
    2, "..NNOO....OONN..", ".NNNNOO..OONNNN.", ".NNNNNNOONNNNNN.", ".NN.NNNOONNN.NN.", ".NN.NNPPPPNN.NN.",
    ".NN.NNPGGPNN.NN.", ".OO.NNPPPPNN.OO.", "....NNNNNNNN....", "....NOONNOON....", "....NNNNNNNN....",
    "....OOOOOOOO...."))
icon("hallowed_leggings", FARMING_SETS["hallowed"][1], half(
    2, "...RRRRR", "...NNNNN", "...NNNNN", "...NNNN.", "...NOON.", "...NOON.", "...NNNN.", "...NUUN.", "...NUUN.",
    "...NNNN.", "..SNSNS.", "..S.S.S."))
icon("hallowed_boots", FARMING_SETS["hallowed"][1], half(
    4, "...OOOO.", "...NNNN.", "...NOON.", "...NNNN.", "...NOON.", ".NNNNNN.", ".NUNNNN.", ".KKKKKK."))

# Farming tools: a sickle, then scythes. B blade, E its bright edge, K haft, G grip wrap.
SICKLE_MASK = [
    "................",
    ".....BBBBB......",
    "...BBEEEEEBB....",
    "..BEE.....BB....",
    ".BE.........B...",
    ".BE.............",
    ".BE.............",
    "..BE............",
    "...BBG..........",
    ".....KK.........",
    "......KK........",
    ".......KK.......",
    "........KK......",
    ".........KK.....",
    "................",
    "................",
]
SCYTHE_MASK = [
    "................",
    "...BBBBBBBB.....",
    ".BBEEEEEEEBBKK..",
    "BBE.......KKGG..",
    "BE.......KK.....",
    "B.......KK......",
    ".......KK.......",
    "......KK........",
    ".....GG.........",
    "....KK..........",
    "...KK...........",
    "..KK............",
    ".KK.............",
    "................",
    "................",
    "................",
]
REAPER_MASK = [
    "................",
    "...BBBBBBBB..S..",
    ".BBEEEEEEEBBPP..",
    "BBE.......KKPP..",
    "BE.......KK.....",
    "B.......KK......",
    ".......VV.......",
    "......VV........",
    ".....VV.........",
    "....KK..........",
    "...KK...........",
    "..KK............",
    ".KK.............",
    "................",
    "................",
    "................",
]
SOULREAPER_MASK = [
    "..BBBBBBBB.F.F..",
    ".BBEEEEEEEBWWW..",
    "BBE.......KWXW..",
    "BE.......KK.W...",
    "BE.......KK.....",
    "BE......KK......",
    "B......KK.......",
    "B.....KK........",
    ".....WW.........",
    "....KK..........",
    "...KK...........",
    "..KK............",
    ".WW.............",
    "................",
    "................",
    "................",
]
FARMING_TOOLS = {
    "thornfield_sickle": (SICKLE_MASK, {"B": IRON.shifted(texture=flat), "E": IRON.shifted(base=4, texture=flat), "K": HANDLE,
                                        "G": LEAF}),
    "sunreaper_scythe": (SCYTHE_MASK, {"B": GOLD.shifted(texture=flat), "E": GOLD.shifted(base=4, texture=flat), "K": HANDLE,
                                       "G": SUN_ORANGE}),
    "frostbite_scythe": (SCYTHE_MASK, {"B": ICE.shifted(texture=flat), "E": ICE.shifted(base=4, texture=flat), "K": DUSK_METAL,
                                       "G": FROSTBERRY}),
    "cinder_scythe": (SCYTHE_MASK, {"B": EMBER.shifted(texture=flat), "E": EMBER.shifted(base=4, texture=flat), "K": OBSIDIAN,
                                    "G": PEPPER_RED}),
    "moonlit_reaper": (SCYTHE_MASK, {"B": SILVER.shifted(texture=flat), "E": MOON_GLOW.shifted(base=4), "K": MIDNIGHT,
                                     "G": MOON_GLOW}),
    "reapers_scythe": (REAPER_MASK, {"B": DARK_IRON.shifted(texture=flat), "E": PUMPKIN.shifted(base=4, texture=flat),
                                     "K": HANDLE.shifted(base=1), "V": HALLOW_PURPLE, "P": PUMPKIN, "S": PUMPKIN_STEM}),
    "soulreaper": (SOULREAPER_MASK, {"B": SOUL.shifted(base=1, texture=flat), "E": SOUL.shifted(base=3, texture=flat),
                                     "F": SOUL.shifted(base=4, texture=flat), "K": BLACKWOOD, "W": BONE,
                                     "X": BLACKWOOD.shifted(base=0, texture=flat)}),
}
for tool_name, (tool_mask, tool_mats) in FARMING_TOOLS.items():
    icon(tool_name, tool_mats, tool_mask)

# Backpacks, smallest to largest. S strap, B body, F flap, P front pocket, U buckle, R bedroll.
SATCHEL_MASK = [
    "................",
    "....SSSSSSSS....",
    "...S........S...",
    "..S..........S..",
    "..S..........S..",
    "..S..........S..",
    ".BBBBBBBBBBBBBB.",
    ".BFFFFFFFFFFFFB.",
    ".BFFFFFFFFFFFFB.",
    ".BBFFFFUUFFFFBB.",
    ".BBBBBBUUBBBBBB.",
    ".BBBBBBBBBBBBBB.",
    ".BBBBBBBBBBBBBB.",
    "..BBBBBBBBBBBB..",
    "................",
    "................",
]
PACK_MASK = [
    "................",
    ".....SSSSSS.....",
    "....S......S....",
    "....BBBBBBBB....",
    "...BFFFFFFFFB...",
    "..BBFFFFFFFFBB..",
    "..BBFFFFFFFFBB..",
    "..BBBFFUUFFBBB..",
    "..BBBBBUUBBBBB..",
    "..BBPPPPPPPPBB..",
    "..BBPPPPPPPPBB..",
    "..BBPPPUUPPPBB..",
    "..BBPPPPPPPPBB..",
    "..BBBBBBBBBBBB..",
    "...BBBBBBBBBB...",
    "................",
]
ROLL_MASK = [
    "................",
    "...RRRRRRRRRR...",
    "..RRRRRRRRRRRR..",
    "...RRRRRRRRRR...",
    "...BFFFFFFFFB...",
    "..BBFFFFFFFFBB..",
    "..BBFFFFFFFFBB..",
    "..BBBFFUUFFBBB..",
    "..BBBBBUUBBBBB..",
    "..BBPPPPPPPPBB..",
    "..BBPPPPPPPPBB..",
    "..BBPPPUUPPPBB..",
    "..BBPPPPPPPPBB..",
    "..BBBBBBBBBBBB..",
    "...BBBBBBBBBB...",
    "................",
]
CANVAS = Material(["4a4028", "6e6240", "948660", "b8aa80", "d8cca4"], texture=cloth)
FOREST = Material(["1c3420", "2a4c2e", "3c6a40", "5a8c58", "86b67e"], texture=cloth)
NAVY = Material(["101a30", "1c2c4c", "2c426e", "466294", "7090c0"], texture=cloth)
BLANKET = Material(["4a0e10", "7a1a1c", "a8302c", "d05a48", "f08e78"], texture=weave)
BACKPACKS = {
    "satchel": (SATCHEL_MASK, {"S": SOIL_LEATHER.shifted(texture=flat), "B": HIDE.shifted(texture=stitch),
                               "F": HIDE.shifted(base=3, texture=flat), "U": BRASS}),
    "trail_pack": (PACK_MASK, {"S": SOIL_LEATHER.shifted(texture=flat), "B": CANVAS, "F": SOIL_LEATHER.shifted(texture=flat),
                               "P": CANVAS.shifted(base=1), "U": IRON}),
    "ranger_pack": (PACK_MASK, {"S": OAK.shifted(texture=flat), "B": FOREST, "F": OAK.shifted(base=3, texture=flat),
                                "P": FOREST.shifted(base=1), "U": GOLD}),
    "voyager_pack": (ROLL_MASK, {"R": BLANKET, "B": NAVY, "F": DELVE_LEATHER.shifted(base=3, texture=flat),
                                 "P": NAVY.shifted(base=1), "U": GOLD}),
    "colossal_pack": (ROLL_MASK, {"R": ORCHID.shifted(base=2, texture=cloth), "B": VOID.shifted(texture=cloth),
                                  "F": DUSK_METAL.shifted(base=3, texture=flat), "P": VOID.shifted(base=1), "U": RIFT}),
}
for pack_name, (pack_mask, pack_mats) in BACKPACKS.items():
    icon(pack_name, pack_mats, pack_mask)

# ---------------------------------------------------------------- foraging gear (the Forester's axes and sets)
def shingle(u, v, w, h):
    """Overlapping bark shingles: a dark seam every third row, notched in a staggered pattern above it."""
    if v % 3 == 2:
        return -1
    return -1 if v % 3 == 1 and (u + (v // 3) * 2) % 4 == 0 else 0


IRONBARK = Material(["241810", "3c2a1e", "58402e", "785a40", "9c7a58"], texture=speckle)
DARK_IRON = Material(["16181c", "2a2d34", "44484f", "62676f", "8a909a"], texture=sheen)
RUST = Material(["3a1606", "6a2a0e", "9a4418", "c4682a", "e8924a"])
MISTPINE = Material(["3e4c4a", "607270", "8a9e9a", "b4c8c4", "dcece8"])
FROST_STEEL = Material(["3a5476", "6688b0", "9cc0e0", "cce6f8", "f4fcff"], texture=sheen)
HOARFROST = Material(["a8d0ea", "d0eaf8", "eaf8ff", "f8fdff", "ffffff"])
WILLOW_STEEL = Material(["341660", "582c9a", "8452c8", "b088ec", "e4d0ff"], texture=sheen)
WILLOW_WOOD = Material(["1c1030", "2e1c48", "44305e", "5e4678", "7c6498"])
CHAR = Material(["0c0a0a", "1a1614", "2c2624", "423a36", "5e524c"], texture=speckle)
EMBERITE = Material(["6e1604", "b8320a", "ff6a1a", "ffa84c", "fff0aa"], base=3)
IVORY = Material(["b8ac88", "d0c49e", "e6dcb8", "f4eccf", "fffcf0"], texture=cloth)
STARTWIG = Material(["5a4a30", "7e6a46", "a89464", "ccb888", "eee0b4"])
STARLIGHT = Material(["c89a20", "f0cc50", "fff09a", "fffbd0", "ffffff"], base=3)
ELDER = Material(["3a1062", "6a2ca8", "9a5ae0", "c898ff", "f0dcff"], base=3)
MIST_WOOL = Material(["6c7078", "9498a2", "b8bcc6", "d6dae2", "f0f2f6"], texture=cloth)
FUR = Material(["8a8478", "b0aa9c", "d0cabc", "e8e4d8", "faf8f0"], texture=speckle)
WILLOW_ROBE = Material(["1a0c2c", "2e1a48", "46296a", "633f8c", "8a62b0"], texture=cloth)
WILLOW_LEAF = Material(["14301c", "224a2c", "38703e", "58a05a", "8ccc84"])
LAVENDER = Material(["4a3c5c", "6c5c80", "8e7ea4", "b0a2c4", "d4cae4"], texture=cloth)

# ---- the axes, each a step up from the one before. Haft runs bottom-left to top-right as in woodsman_axe.
icon("ironbark_hatchet", {"I": DARK_IRON.shifted(texture=flat), "R": RUST.shifted(texture=flat), "K": IRONBARK.shifted(texture=flat),
                          "J": DARK_IRON.shifted(base=1, texture=flat)}, [
    "................",
    "............KK..",
    "....RIIIIIIKK...",
    "...RIIIIIIKK....",
    "...RIIIIIKK.....",
    "....RIIIKK......",
    ".....RIKK.......",
    "......JJ........",
    ".....KK.........",
    "....KK..........",
    "...KK...........",
    "..KK............",
    ".KK.............",
    "................",
    "................",
    "................",
], [
    "................",
    "................",
    "......44.1......",
    "....44..1.......",
    "........1.......",
    ".....4..........",
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
icon("mistpine_splitter", {"P": FROST_STEEL.shifted(texture=flat), "E": FROST_STEEL.shifted(base=4, texture=flat),
                           "F": HOARFROST, "H": MISTPINE, "W": MISTPINE.shifted(base=1), "J": FROST_STEEL.shifted(base=1, texture=flat),
                           "Q": FROST_STEEL.shifted(base=1, texture=flat)}, [
    "................",
    "...EPPPPPPPPHH..",
    "..EFPPPPPPPHHP..",
    "..EPPPPPQQHHQQ..",
    ".FEPFPPQQHHQ....",
    "...EPPQQHH......",
    "....EPQHH.......",
    "......JJ........",
    ".....HH.........",
    "....WW..........",
    "...HH...........",
    "..WW............",
    ".HH.............",
    "................",
    "................",
    "................",
], [
    "................",
    "................",
    "...4..........1.",
    "..4.......1.....",
    ".....4......1...",
    ".....1111.......",
    ".....111........",
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
icon("willowcleaver", {"B": WILLOW_STEEL.shifted(texture=flat), "E": WILLOW_STEEL.shifted(base=4, texture=flat),
                       "D": WILLOW_STEEL.shifted(base=1, texture=flat), "K": WILLOW_WOOD, "W": LAVENDER.shifted(texture=flat),
                       "G": WILLOW_LEAF.shifted(texture=flat)}, [
    "................",
    "..DDDDDDDDDDKK..",
    ".BBBBBBBBBBKK...",
    "BEEEEEEEEEKK....",
    "BE.....BBKK.....",
    "......GGKK......",
    ".......KK.......",
    "......WW........",
    ".....KK.........",
    "....KK..........",
    "...WW...........",
    "..KK............",
    ".KK.............",
    "GG..............",
    "................",
    "................",
], [
    "................",
    "................",
    "..........4.....",
    ".4....4.........",
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
icon("emberbrand_axe", {"C": CHAR.shifted(texture=flat), "E": EMBER.shifted(base=3, texture=flat), "A": EMBER.shifted(base=4, texture=flat),
                        "H": CHAR.shifted(base=1, texture=flat)}, [
    "................",
    "..EECCCCCCCCHH..",
    ".EECCCACCCCHHC..",
    "EECCECCCCCHHCC..",
    "EECCCCACCHHC....",
    ".EECECCCHH......",
    "..EECCCHH.......",
    "....EEHH........",
    ".....HH.........",
    "....HH..........",
    "...EE...........",
    "..HH............",
    ".HH.............",
    "HH..............",
    "................",
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
icon("starwood_greataxe", {"V": IVORY.shifted(texture=flat), "U": GOLD.shifted(texture=flat), "H": STARTWIG,
                           "S": ELDER, "G": GOLD.shifted(base=3, texture=flat), "W": IVORY.shifted(base=4, texture=flat)}, [
    "..............G.",
    "............HH..",
    "..UUUU.....HH...",
    ".UVVVVU...HH....",
    "UVVWVVVU.HH.....",
    "UVVVVVVVUHH.....",
    ".UVVVVVVVSH.....",
    "..UUVVVVSSS.....",
    ".....GSSSGUU....",
    ".....HHVVVVVU...",
    "....HHUVVVVVVU..",
    "...HH.UVVVVWVU..",
    "..HH...UVVVVVU..",
    ".HH.....UUUUU...",
    "HH..............",
    "G...............",
], [
    "................",
    "................",
    "................",
    "................",
    "................",
    "................",
    "................",
    ".......4........",
    "................",
    "................",
    "................",
    "................",
    "................",
    "................",
    "................",
    "................",
])


# ---- worn layers
def barkguard_layers(m):
    outer = Sheet()
    # Bark cap: shingled crown over a dark seam and a leather band, ear flaps behind, a leaf tucked in at the side.
    outer.fill(HEAD, ["top"], "B")
    outer.tweak_face(HEAD, "top", rim)
    outer.band(HEAD, range(0, 3), "B")
    outer.band(HEAD, [3], "D")
    outer.band(HEAD, [4], "L", faces=("right", "left", "back"))
    flanks(outer, HEAD, ["........", "........", "........", "........", "..LLLL..", "..LLLL..", "...KK..."])
    stamp(outer, HEAD, "back", [".LLLLLL.", ".LLLLLL.", "..KKKK.."], v0=4)
    for u, v in ((5, 0), (6, 0), (6, 1), (5, 1)):
        outer.put(HEAD, "left", u, v, "G")
    outer.put(HEAD, "left", 5, 1, "D")
    # Cuirass: rows of ironbark shingles over leather, a leather collar and belt with an iron buckle.
    outer.fill(BODY, SIDES, "B", rows=range(0, 10))
    stamp(outer, BODY, "front", ["BBLLLLBB", "BBBLLBBB", "BBBBBBBB", "BBBBBBBB", "BBBBBBBB", "BBBBBBBB", "BBBBBBBB",
                                 "BBBBBBBB", "LLLRRLLL", "BBBBBBBB"])
    stamp(outer, BODY, "back", ["BBBBBBBB", "BBBBBBBB", "BBBBBBBB", "BBBBBBBB", "BBBBBBBB", "BBBBBBBB", "BBBBBBBB",
                                "BBBBBBBB", "LLLLLLLL", "BBBBBBBB"])
    outer.band(BODY, [8], "L", faces=("right", "left"))
    outer.put(BODY, "front", 3, 8, tweak="4")
    outer.fill(ARM, ["top"], "B")
    outer.band(ARM, range(0, 3), "B")
    outer.band(ARM, [3], "D")
    outer.band(ARM, [4], "L")
    # Boots: leather, a shingled cuff, a rivet.
    outer.band(LEG, range(6, 12), "L")
    outer.band(LEG, range(6, 8), "B")
    outer.band(LEG, [8], "D")
    outer.fill(LEG, ["bottom"], "K")
    outer.put(LEG, "front", 1, 10, "R")
    outer.put(LEG, "front", 2, 10, "R")

    inner = Sheet()
    # Leathers: knee plates of shingle, a belt with a rivet.
    inner.band(LEG, range(0, 9), "L")
    inner.fill(LEG, ["top"], "L")
    inner.fill(LEG, ["front"], "B", rows=range(3, 6))
    inner.band(BODY, range(7, 12), "L")
    inner.band(BODY, [7], "K")
    inner.put(BODY, "front", 3, 7, "R")
    inner.put(BODY, "front", 4, 7, "R")
    return outer.render(m), inner.render(m)


def mistwarden_layers(m):
    outer = Sheet()
    # Hood: pale wool all round, a fur ring framing the face opening, a drape at the back.
    outer.fill(HEAD, ["top"], "W")
    outer.tweak_face(HEAD, "top", rim)
    outer.fill(HEAD, ["right", "left", "back"], "W")
    stamp(outer, HEAD, "front", ["WWWWWWWW", "WFFFFFFW", "WF____FW", "WF____FW", "WF____FW", "WF____FW", "WFFFFFFW", "WWWWWWWW"])
    flanks(outer, HEAD, ["WWWWWWWW"] + ["WWWWWWWF"] * 6 + ["WWWWWWWW"])
    stamp(outer, HEAD, "back", ["WWWWWWWW"] * 3 + ["WWWDDWWW"] * 5)
    # Coat: wool with a fur collar and hem, a front seam fastened with mistpine toggles, a dark belt.
    outer.fill(BODY, SIDES, "W", rows=range(0, 10))
    stamp(outer, BODY, "front", ["FF____FF", "FFFFFFFF", "WWTDTWWW", "WWWDWWWW", "WWTDTWWW", "WWWDWWWW", "WWTDTWWW",
                                 "WWWDWWWW", "KKKTTKKK", "FFFFFFFF"])
    stamp(outer, BODY, "back", ["FFFFFFFF", "FFFFFFFF", "WWWWWWWW", "WWWDDWWW", "WWWDDWWW", "WWWWWWWW", "WWWWWWWW",
                                "WWWWWWWW", "KKKKKKKK", "FFFFFFFF"])
    flanks(outer, BODY, ["FFFF", "FFFF", "WWWW", "WWWW", "WWWW", "WWWW", "WWWW", "WWWW", "KKKK", "FFFF"])
    outer.fill(ARM, ["top"], "F")
    outer.band(ARM, range(0, 3), "W")
    outer.band(ARM, [3, 4], "F")
    # Boots: fur-lined leather with a mistpine toggle.
    outer.band(LEG, range(6, 12), "L")
    outer.band(LEG, range(6, 8), "F")
    outer.fill(LEG, ["bottom"], "K")
    outer.put(LEG, "front", 1, 9, "T")
    outer.put(LEG, "front", 2, 10, "T")

    inner = Sheet()
    # Trousers: wool, a dark cord belt with a toggle.
    inner.band(LEG, range(0, 9), "W")
    inner.fill(LEG, ["top"], "W")
    inner.fill(LEG, ["front"], "K", rows=range(4, 5))
    inner.band(BODY, range(7, 12), "W")
    inner.band(BODY, [7], "K")
    inner.put(BODY, "front", 3, 7, "T")
    inner.put(BODY, "front", 4, 7, "T")
    return outer.render(m), inner.render(m)


def willowshade_layers(m):
    outer = Sheet()
    # Hood: deep purple, a shadowed rim, and a veil of willow strands hanging over the face and down the back.
    outer.fill(HEAD, ["top"], "R")
    outer.tweak_face(HEAD, "top", rim)
    outer.fill(HEAD, ["right", "left"], "R", rows=range(0, 7))
    stamp(outer, HEAD, "front", ["RRRRRRRR", "RDDDDDDR", "RL_LL_LR", "RL_L__LR", "RL_LL_LR", "RL_L__LR", "RL_LL_LR", "L__L_L_L"])
    flanks(outer, HEAD, ["RRRRRRRR"] * 5 + ["RRRRRRRL", "LLR.LL.L", "L..L..L."])
    stamp(outer, HEAD, "back", ["RRRRRRRR", "RRRRRRRR", "RRRRRRRR", "RLRRLRRL", "RLRLLRLR", "LLRLRLLR", "L.RL.LL.", "L..L..L."])
    # Robe: purple, soft lavender wraps crossing the chest, willow strands hanging from the shoulders.
    outer.fill(BODY, SIDES, "R", rows=range(0, 10))
    stamp(outer, BODY, "front", ["WW____WW", "RWWRRWWR", "LRRWWRRL", "LRRRWWRL", "RRWWRRWW", "RRRRRRRR", "LRRRRRRL",
                                 "WWWWWWWW", "RLRRRRLR", "RLRLRLRL"])
    stamp(outer, BODY, "back", ["WWWWWWWW", "RRRRRRRR", "RLRRRRLR", "RLRRRRLR", "RRRRRRRR", "LRRRRRRL", "LRRLLRRL",
                                "WWWWWWWW", "RLRRLRLR", "LRLRLRLR"])
    flanks(outer, BODY, ["WWWW", "RRRR", "RRLR", "RRLR", "RRRR", "RRRR", "LRRR", "WWWW", "RLRL", "LRLR"])
    outer.fill(ARM, ["top"], "R")
    outer.band(ARM, range(0, 3), "R")
    outer.band(ARM, [3, 4], "W")
    stamp(outer, ARM, "front", ["L..L", "L..L", ".L.."], v0=6)
    stamp(outer, ARM, "back", ["L..L", ".LL."], v0=6)
    # Boots: wrapped in lavender over purple, ragged at the cuff.
    outer.band(LEG, range(6, 12), "R")
    outer.band(LEG, [7, 9], "W")
    outer.band(LEG, [6], "L")
    outer.fill(LEG, ["bottom"], "D")

    inner = Sheet()
    # Skirts: purple with willow strands and a wrap at the knee and hip.
    inner.band(LEG, range(0, 9), "R")
    inner.fill(LEG, ["top"], "R")
    inner.band(LEG, [4], "W")
    stamp(inner, LEG, "front", ["L..L", "L.L.", "...L"], v0=5)
    inner.band(BODY, range(7, 12), "R")
    inner.band(BODY, [7], "W")
    stamp(inner, BODY, "front", ["...LL...", "..L..L.."], v0=9)
    return outer.render(m), inner.render(m)


def emberbark_layers(m):
    outer = Sheet()
    # Smouldering helm: charred bark plates split by glowing seams, a dark brow and a nasal guard.
    outer.fill(HEAD, ["top"], "C")
    outer.tweak_face(HEAD, "top", rim)
    stamp(outer, HEAD, "top", ["...E....", "...EE...", "....E...", ".E..EE..", ".EE.....", "...E.E..", "...EEE..", "......E."])
    outer.band(HEAD, range(0, 3), "C")
    outer.band(HEAD, [3], "D")
    stamp(outer, HEAD, "front", ["CCCCCCCC", "CCECCCCC", "CCCCCCEC", "DDDDDDDD", "CC_CC_CC", "C______C", "C______C", "CC____CC"])
    flanks(outer, HEAD, ["CCCCCCCC", "CCCECCCC", "CCECCCCC", "DDDDDDDD", "CCCCEECC", "CCCEC...", "CCCC....", "CCDD...."])
    stamp(outer, HEAD, "back", ["CCCCCCCC", "CCCECCCC", "CCCEECCC", "DDDDDDDD", "CCCECCCC", "CCCEECCC", "CCCCECCC", "CCCCCCCC"])
    for u in (1, 6):
        outer.put(HEAD, "front", u, 4, tweak="3")
    # Cuirass: charred plates with glowing seams, emberite studs, a glowing belt seam.
    outer.fill(BODY, SIDES, "C", rows=range(0, 10))
    stamp(outer, BODY, "front", ["CCC__CCC", "CACCCCAC", "CCCCCCCC", "CCEEECCC", "CECCECAC", "CCCCCECC", "CACCCCCC",
                                 "CCCECCAC", "EEEEEEEE", "DCCCCCCD"])
    stamp(outer, BODY, "back", ["CCCCCCCC", "CCCCECCC", "CCCEECCC", "CCECCCCC", "CCECCECC", "CCCCCEEC", "CECCCCCC",
                                "CECCCCCC", "EEEEEEEE", "DCCCCCCD"])
    flanks(outer, BODY, ["CCCC", "CCCC", "CCCC", "CECC", "CCEC", "CCCC", "CCCC", "CCCC", "EEEE", "DCCD"])
    for u, v in ((1, 1), (6, 1), (6, 4), (1, 6), (6, 7)):
        outer.put(BODY, "front", u, v, tweak="4")
    outer.fill(ARM, ["top"], "C")
    outer.band(ARM, range(0, 4), "C")
    outer.band(ARM, [4], "E")
    stamp(outer, ARM, "front", [".E..", "..E.", ".E.."], v0=1)
    # Boots: charred bark, a glowing cuff seam and sole.
    outer.band(LEG, range(6, 12), "C")
    outer.band(LEG, [6], "E")
    stamp(outer, LEG, "front", [".E..", "..E.", ".E.."], v0=8)
    outer.band(LEG, [11], "D")
    outer.fill(LEG, ["bottom"], "E")

    inner = Sheet()
    # Greaves: bark with seams and knee guards, a belt of glowing seam with an emberite stud.
    inner.band(LEG, range(0, 9), "C")
    inner.fill(LEG, ["top"], "C")
    stamp(inner, LEG, "front", ["..E.", ".E..", ".E..", "DDDD", "DEED", "DDDD", "..E."], v0=1)
    inner.band(BODY, range(7, 12), "C")
    inner.band(BODY, [7], "E")
    stamp(inner, BODY, "front", ["...AA...", ".E......", "..E...E."], v0=8)
    return outer.render(m), inner.render(m)


def starwood_layers(m):
    outer = Sheet()
    # Crown: an ivory coif under a gold circlet woven with starwood twigs, little lights at the knots.
    outer.fill(HEAD, ["top"], "V")
    stamp(outer, HEAD, "top", ["T..T..T.", ".T.TT.T.", "..TT.T..", "TTT.TTTT", "..TT.T..", ".T.TT.T.", "T..T..T.", "........"])
    outer.tweak_face(HEAD, "top", rim)
    outer.band(HEAD, range(0, 2), "V")
    outer.band(HEAD, [2], "T")
    outer.band(HEAD, [3], "U", faces=SIDES)
    stamp(outer, HEAD, "front", ["VVVVVVVV", "VVVVVVVV", "TLTTTLTT", "UUUUUUUU", "........", "........", "........", "........"])
    flanks(outer, HEAD, ["VVVVVVVV", "VVVVVVVV", "TTTLTTTT", "UUUUUUUU", "..VVVVVV", "..VVVVVV", "...VVVV.", "....VV.."])
    stamp(outer, HEAD, "back", ["VVVVVVVV", "VVVVVVVV", "TTLTTTLT", "UUUUUUUU", "VVVVVVVV", "VVVVVVVV", ".VVVVVV.", "..VVVV.."])
    for u, v in ((1, 2), (5, 2)):
        outer.put(HEAD, "front", u, v, tweak="4")
    # Mantle: ivory with a gold collar and hem, an Elder Heartwood brooch in a gold frame at the throat.
    outer.fill(BODY, SIDES, "V", rows=range(0, 10))
    stamp(outer, BODY, "front", ["UUU__UUU", "UVVUUVVU", "VVUPPUVV", "VVUPPUVV", "VVVUUVVV", "VVVVVVVV", "VVVVVVVV",
                                 "VVVVVVVV", "UUUUUUUU", "VVVVVVVV"])
    stamp(outer, BODY, "back", ["UUUUUUUU", "VVVUUVVV", "VVVUUVVV", "VVVVVVVV", "VVVVVVVV", "VVVVVVVV", "VVVVVVVV",
                                "VVVVVVVV", "UUUUUUUU", "VVVVVVVV"])
    flanks(outer, BODY, ["UUUU", "VVVV", "VVVV", "VVVV", "VVVV", "VVVV", "VVVV", "VVVV", "UUUU", "VVVV"])
    for u, v in ((3, 2), (4, 3)):
        outer.put(BODY, "front", u, v, tweak="4")
    outer.fill(ARM, ["top"], "U")
    outer.band(ARM, range(0, 3), "V")
    outer.band(ARM, [3], "U")
    outer.band(ARM, [4], "V")
    # Greaves: ivory, a gold cuff and a faint glow.
    outer.band(LEG, range(6, 12), "V")
    outer.band(LEG, [6], "U")
    outer.band(LEG, [11], "U")
    stamp(outer, LEG, "front", ["..L.", ".LL.", "..L."], v0=7)
    outer.fill(LEG, ["bottom"], "D")

    inner = Sheet()
    # Leggings: ivory with gold knee cops around a soft light, a gold belt.
    inner.band(LEG, range(0, 9), "V")
    inner.fill(LEG, ["top"], "V")
    stamp(inner, LEG, "front", ["UUUU", "ULLU", "UUUU"], v0=3)
    stamp(inner, LEG, "front", [".L..", "L.L."], v0=7)
    inner.band(BODY, range(7, 12), "V")
    inner.band(BODY, [7], "U")
    stamp(inner, BODY, "front", ["...LL...", "........", "..L..L.."], v0=8)
    return outer.render(m), inner.render(m)


FORAGING_SETS = {
    "barkguard": (barkguard_layers,
                  {"B": IRONBARK.shifted(texture=shingle), "D": IRONBARK.shifted(base=1, texture=flat),
                   "L": SOIL_LEATHER, "K": SOIL_LEATHER.shifted(base=1, texture=flat), "G": LEAF, "R": IRON.shifted(texture=flat)},
                  {"B": IRONBARK.shifted(texture=shingle), "D": IRONBARK.shifted(base=1, texture=flat),
                   "L": SOIL_LEATHER, "K": SOIL_LEATHER.shifted(base=0, texture=flat), "G": LEAF.shifted(texture=cloth),
                   "R": IRON.shifted(texture=flat)}),
    "mistwarden": (mistwarden_layers,
                   {"W": MIST_WOOL, "F": FUR, "T": MISTPINE.shifted(texture=flat), "K": MIST_WOOL.shifted(base=1, texture=flat),
                    "D": MIST_WOOL.shifted(base=1, texture=flat), "L": SOIL_LEATHER},
                   {"W": MIST_WOOL, "F": FUR, "T": MISTPINE.shifted(texture=flat), "K": MIST_WOOL.shifted(base=0, texture=flat),
                    "D": MIST_WOOL.shifted(base=1, texture=flat), "L": SOIL_LEATHER.shifted(texture=cloth)}),
    "willowshade": (willowshade_layers,
                    {"R": WILLOW_ROBE, "D": WILLOW_ROBE.shifted(base=0, texture=flat), "L": WILLOW_LEAF,
                     "W": LAVENDER},
                    {"R": WILLOW_ROBE, "D": WILLOW_ROBE.shifted(base=0, texture=flat), "L": WILLOW_LEAF.shifted(texture=cloth),
                     "W": LAVENDER}),
    "emberbark": (emberbark_layers,
                  {"C": CHAR.shifted(texture=shingle), "D": CHAR.shifted(base=1, texture=flat), "E": EMBER.shifted(texture=flat),
                   "A": EMBERITE},
                  {"C": CHAR.shifted(texture=shingle), "D": CHAR.shifted(base=1, texture=flat), "E": EMBER.shifted(texture=flat),
                   "A": EMBERITE}),
    "starwood": (starwood_layers,
                 {"V": IVORY, "D": IVORY.shifted(base=1, texture=flat), "U": GOLD, "T": STARTWIG,
                  "L": STARLIGHT, "P": ELDER},
                 {"V": IVORY, "D": IVORY.shifted(base=1, texture=flat), "U": GOLD.shifted(texture=flat), "T": STARTWIG,
                  "L": STARLIGHT, "P": ELDER}),
}

# Icons. Barkguard: a shingled bark cap with a leaf, a shingle cuirass over leather, leather-belted legs.
icon("barkguard_helmet", FORAGING_SETS["barkguard"][1], sprite(
    1, "...........GG...", ".....BBBBGGG....", "....BBBBBBBG....", "...BBBBBBBBBB...", "...BBBBBBBBBB...",
    "...DDDDDDDDDD...", "...LLLLLLLLLL...", "...LL......LL...", "...KK......KK..."))
icon("barkguard_chestplate", FORAGING_SETS["barkguard"][1], half(
    2, "..BBBLLL", ".BBBBBBL", ".BBBBBBB", ".BB.BBBB", ".BB.BBBB", ".LL.BBBB", "....BBBB", "....LLLR", "....BBBB",
    "....DDDD"))
icon("barkguard_leggings", FORAGING_SETS["barkguard"][1], half(
    2, "...LLLLR", "...BBBBB", "...BBBBB", "...BBBB.", "...DDDD.", "...BBBB.", "...BBBB.", "...BBBB.", "...LLLL.",
    "...KKKK."))
icon("barkguard_boots", FORAGING_SETS["barkguard"][1], half(
    4, "...BBBB.", "...DDDD.", "...LLLL.", "...LLLL.", "...LLLL.", ".LLLLLL.", ".LLLLLL.", ".KKKKKK."))

# Mistwarden: a fur-ringed hood, a pale wool coat with mistpine toggles, fur-topped boots.
icon("mistwarden_helmet", FORAGING_SETS["mistwarden"][1], half(
    1, ".......W", "......WW", ".....WWW", "....WWWW", "...WWWWW", "...WFFFF", "...WFKKK", "...WFKKK", "...WFFFF",
    "..WWWWWW", "..DD...."))
icon("mistwarden_chestplate", FORAGING_SETS["mistwarden"][1], half(
    2, "..WWFFFF", ".WWWFFFF", ".WWWWWWD", ".WW.WTWD", ".WW.WWWD", ".KK.WTWD", "....WWWD", "....WTWD", "....WWWD",
    "....FFFF"))
icon("mistwarden_leggings", FORAGING_SETS["mistwarden"][1], half(
    2, "...KKKKT", "...WWWWW", "...WWWWW", "...WWWW.", "...WWWW.", "...WWWW.", "...WWWW.", "...WWWW.", "...WWWW.",
    "...DDDD."))
icon("mistwarden_boots", FORAGING_SETS["mistwarden"][1], half(
    4, "...FFFF.", "...FFFF.", "...LLLL.", "...LTLL.", "...LLLL.", ".LLLLLL.", ".LLLLLL.", ".KKKKKK."))

# Willowshade: a veiled hood hung with willow strands, a wrapped robe, ragged skirts.
icon("willowshade_helmet", FORAGING_SETS["willowshade"][1], half(
    1, ".......R", "......RR", ".....RRR", "....RRRR", "...RRRRR", "...RRDDD", "..LRRDDD", "..LRRDDD", "..RRRRRR",
    ".L.L.L.L", ".L..L.L.", "..L....."))
icon("willowshade_chestplate", FORAGING_SETS["willowshade"][1], half(
    2, "..RRRWWW", ".RRRRRWW", ".LRRRRRW", ".LR.LRRR", ".LR.RRRR", ".WW.LRRR", "....WWWW", "....RLRR", "....RLRL",
    "....L.L."))
icon("willowshade_leggings", FORAGING_SETS["willowshade"][1], half(
    2, "...WWWWW", "...RRRRR", "...RLRRR", "...RLRR.", "...RRRR.", "...WWWW.", "...RLRR.", "...RLRR.", "...RRRR.",
    "...L.LR."))
icon("willowshade_boots", FORAGING_SETS["willowshade"][1], half(
    4, "...RLRL.", "...WWWW.", "...RRRR.", "...WWWW.", "...RRRR.", ".RRRRRR.", ".RRRRRR.", ".DDDDDD."))

# Emberbark: a smouldering plated helm, a studded cuirass with glowing seams, charred greaves and glowing soles.
icon("emberbark_helmet", FORAGING_SETS["emberbark"][1], half(
    1, ".......A", "....CCCE", "...CCCEC", "...CCECC", "...CCCCE", "...DDDDD", "...CEEEE", "...CC...", "...CC...",
    "...DD..."))
icon("emberbark_chestplate", FORAGING_SETS["emberbark"][1], half(
    2, "..CCCDDD", ".CCCCCCC", ".CECCCEC", ".CC.CACC", ".CC.CCEC", ".DD.CACC", "....CCEC", "....DEEE", "....CACC",
    "....DDDD"))
icon("emberbark_leggings", FORAGING_SETS["emberbark"][1], half(
    2, "...DDDAA", "...CCCCC", "...CCECC", "...CCCC.", "...DEED.", "...CCCC.", "...CECC.", "...CCCC.", "...CCCC.",
    "...DDDD."))
icon("emberbark_boots", FORAGING_SETS["emberbark"][1], half(
    4, "...DDDD.", "...CCCC.", "...CECC.", "...CCCC.", "...CCEC.", ".CCCCCC.", ".CCCCCC.", ".EEEEEE."))

# Starwood: a twig crown with little lights, an ivory and gold mantle with a purple brooch, faintly glowing greaves.
icon("starwood_helmet", FORAGING_SETS["starwood"][1], half(
    1, ".L...L.L", ".T..TT.T", ".TT.TTTT", "..TUUUUU", "..VVVVVV", "..VVVVVV", "..VVDDDD", "..VVDDDD", "..VV....",
    "..DD...."))
icon("starwood_chestplate", FORAGING_SETS["starwood"][1], half(
    2, "..VVVUUU", ".VVVVVUU", ".VVVVVUP", ".VV.VVUP", ".VV.VVUU", ".UU.VVVV", "....VDVV", "....UUUU", "....VVVV",
    "....UUUU"))
icon("starwood_leggings", FORAGING_SETS["starwood"][1], half(
    2, "...UUUUU", "...VVVVV", "...VVLVV", "...VVVV.", "...ULLU.", "...VVVV.", "...VVVV.", "...VLLV.", "...VVVV.",
    "...UUUU."))
icon("starwood_boots", FORAGING_SETS["starwood"][1], half(
    4, "...UUUU.", "...VVVV.", "...VVLV.", "...VVVV.", "...VVVV.", ".VVVVVV.", ".VVVVVV.", ".UUUUUU."))


# ---------------------------------------------------------------- combat gear (the Combat Master's swords and sets)
FANG_LEATHER = Material(["1a120e", "2e201a", "46332a", "634a3c", "84665a"], texture=stitch)
FANG_BONE = Material(["6a604c", "a09478", "cfc4a4", "eae2c8", "fffaea"])
DREAD_BONE = Material(["2a2020", "463836", "6e5a54", "9a827a", "c8b4aa"])
BOLT = Material(["8a6a00", "d0a010", "ffd820", "fff070", "ffffe0"], base=3)
SKYPLATE = Material(["2a4a6e", "4a74a0", "74a0cc", "a4c8ea", "dcf0ff"], texture=sheen)
RIFT_CLOTH = Material(["1c0c34", "3a1a62", "5c2c92", "8448bc", "b078e0"], texture=cloth)
RIFT_SEAM = Material(["6a0a5a", "b01a96", "f03cd0", "ff88ec", "ffe0fa"], base=3)
NIGHTSTEEL = Material(["080e28", "10205a", "1c3a8a", "3058b4", "6e92dc"], texture=sheen)
STARWHITE = Material(["a0b0e0", "d0daf8", "f0f4ff", "ffffff", "ffffff"], base=3)


def sword(blade, guard, grip, length=11, base=(4, 11), bend=(), extra=()):
    """A 16x16 sword mask on the frame vanguard_blade uses: blade running up to the right, guard across its foot.

    blade: letters across a blade row, left to right. guard: letters of the cross-guard from its upper-left tip
    to its lower-right tip (odd length, the middle on the blade's axis). grip: letters going down-left. bend: columns
    the blade bows to the right at each step. extra: (col, row, letter) overrides, applied last.
    """
    cells = {}
    bx, by = base
    for k in range(length):
        cx = bx + k + (bend[k] if k < len(bend) else 0)
        row = blade if k < length - 1 else blade[-1]
        for i, ch in enumerate(row):
            cells[(cx + i - len(row) // 2, by - k)] = ch
    half = len(guard) // 2
    for i, ch in enumerate(guard):
        gx, gy = bx - 1 + i - half, by + 1 + i - half
        cells[(gx, gy)] = ch
        cells.setdefault((gx + 1, gy), ch)
    for i, ch in enumerate(grip):
        cells[(bx - 2 - i, by + 2 + i)] = ch
    for c, r, ch in extra:
        cells[(c, r)] = ch
    return ["".join(cells.get((c, r), ".") for c in range(16)) for r in range(16)]


icon("fangsteel_sabre", {"D": IRON.shifted(base=1, texture=flat), "S": IRON.shifted(texture=flat), "E": IRON.shifted(base=4, texture=flat),
                         "G": DARK_IRON.shifted(texture=flat), "F": DREAD_BONE.shifted(base=3), "R": CRIMSON, "W": CRIMSON.shifted(base=1),
                         "K": DARK_IRON.shifted(base=3, texture=flat)},
     sword("DSE", "GGGFGGG", "RWK", bend=(0, 0, 1, 1, 2, 2, 2, 2, 1, 1, 0)))
icon("stormedge", {"D": SKYPLATE.shifted(base=1, texture=flat), "S": SKYPLATE.shifted(texture=flat),
                   "E": SKYPLATE.shifted(base=4, texture=flat), "Y": BOLT, "U": GOLD.shifted(texture=flat),
                   "L": SOIL_LEATHER.shifted(base=1, texture=flat), "V": GOLD.shifted(base=4, texture=flat)},
     sword("DSE", "UUUVUUU", "LLU",
           extra=((7, 8, "Y"), (7, 7, "Y"), (9, 6, "Y"), (9, 5, "Y"), (11, 4, "Y"), (11, 3, "Y"), (6, 9, "Y"))))
icon("cinderfang", {"D": CHAR.shifted(base=1, texture=flat), "C": CHAR.shifted(base=2, texture=flat),
                    "E": EMBER.shifted(base=3, texture=flat), "A": EMBER.shifted(base=4, texture=flat),
                    "G": CHAR.shifted(base=1, texture=flat), "V": ELDER, "T": EMBER.shifted(texture=flat),
                    "L": CHAR.shifted(base=3, texture=flat)},
     sword("DCE", "GTGVGTG", "LGT",
           extra=((9, 8, "E"), (11, 6, "E"), (13, 4, "E"), (9, 7, "C"), (11, 5, "C"), (6, 9, "A"), (9, 6, "A"), (12, 3, "A"),
                  (2, 12, "V"), (3, 13, "V"))))
icon("starfall_blade", {"D": NIGHTSTEEL.shifted(base=1, texture=flat), "N": NIGHTSTEEL.shifted(texture=flat),
                        "E": NIGHTSTEEL.shifted(base=4, texture=flat), "W": STARWHITE, "A": SILVER, "P": PRISM_GLOW,
                        "L": NIGHTSTEEL.shifted(base=0, texture=flat)},
     sword("DNNE", "PAAPAAP", "LAP",
           extra=((5, 9, "W"), (7, 7, "W"), (9, 6, "W"), (8, 8, "W"), (10, 4, "W"), (12, 3, "W"), (11, 5, "W"), (13, 2, "W"),
                  (2, 12, "P"), (3, 12, "P"))))
HANDHELD_COMBAT = {"fangsteel_sabre", "stormedge", "cinderfang", "starfall_blade"}


# ---- slayer weapons (dropped by the slayer bosses)
PLAGUE = Material(["1c3a12", "3a6a26", "7fbf5f", "b4e890", "eaffd4"], base=3)
GRAVE_STEEL = Material(["070c08", "121c14", "22342a", "385040", "52725a"], texture=sheen)
CHITIN = Material(["0a0306", "260e12", "4e1a1e", "86302c", "c0392b"])
VENOM = Material(["2a5a10", "58a424", "8ee03c", "c4f878", "f0ffc8"], base=3)
IVORY = Material(["6e6450", "a89c80", "d6ccae", "efe7cf", "fffcf0"])
MARROW = Material(["4a0a10", "7e1218", "b82028", "e04a44", "ff8a7a"], base=3)
VOID_INDIGO = Material(["0a0618", "160c30", "2a1b4a", "44306e", "62498e"], texture=sheen)
VOID_CYAN = Material(["1c6a82", "3aa8bc", "5ce1e6", "a4f4f4", "eaffff"], base=3)

icon("gravebane", {"D": GRAVE_STEEL.shifted(base=2, texture=flat), "S": GRAVE_STEEL.shifted(base=3, texture=flat),
                   "E": PLAGUE.shifted(base=2, texture=flat), "H": PLAGUE.shifted(base=4, texture=flat),
                   "B": FANG_BONE.shifted(base=2, texture=flat), "K": FANG_BONE.shifted(base=0, texture=flat),
                   "G": FANG_BONE.shifted(base=1, texture=flat), "N": FANG_BONE.shifted(base=3, texture=flat)},
     sword("DSSE", "GGBGG", "BNK", length=10, base=(4, 11),
           extra=((5, 10, "E"), (7, 8, "H"), (9, 6, "H"), (10, 5, "E"), (8, 9, "S"), (11, 4, "E"))))
icon("broodfang", {"D": CHITIN.shifted(base=2, texture=flat), "C": CHITIN.shifted(base=3, texture=flat),
                   "E": CHITIN.shifted(base=4, texture=flat), "V": VENOM.shifted(texture=flat),
                   "W": VENOM.shifted(base=4, texture=flat), "G": CHITIN.shifted(base=1, texture=flat),
                   "L": CHITIN.shifted(base=0, texture=flat), "H": CHITIN.shifted(base=4, texture=flat)},
     sword("DCE", "GLGLG", "GHL", length=11, bend=(0, 0, 0, 0, 1, 1, 1, 1, 0, 0, -1),
           extra=((13, 2, "W"), (14, 3, "V"), (14, 4, "W"))))
icon("marrowblade", {"D": IVORY.shifted(base=1, texture=flat), "S": IVORY.shifted(base=3, texture=flat),
                     "E": IVORY.shifted(base=4, texture=flat), "R": MARROW.shifted(base=2, texture=flat),
                     "M": MARROW.shifted(base=3, texture=flat), "U": GOLD.shifted(texture=flat),
                     "V": GOLD.shifted(base=4, texture=flat), "L": FANG_BONE.shifted(base=1, texture=flat),
                     "K": FANG_BONE.shifted(base=0, texture=flat)},
     sword("DRE", "UUUVUUU", "LLK", length=11))
_notch = [(4 + k + 1 - 0, 11 - k, ".") for k in (3, 6, 8)] + [(4 + k - 2, 11 - k, ".") for k in (4, 7)]
icon("voidrender", {"D": VOID_INDIGO.shifted(base=2, texture=flat), "N": VOID_INDIGO.shifted(base=3, texture=flat),
                    "E": VOID_INDIGO.shifted(base=4, texture=flat), "C": VOID_CYAN.shifted(base=2, texture=flat),
                    "W": VOID_CYAN.shifted(base=4, texture=flat), "G": VOID.shifted(base=1, texture=flat),
                    "P": VOID_CYAN.shifted(base=0, texture=flat), "L": VOID.shifted(base=2, texture=flat)},
     sword("DNNE", "GGPGG", "LGL", length=11,
           extra=tuple(_notch) + ((6, 9, "C"), (7, 8, "W"), (8, 8, "C"), (9, 6, "C"), (10, 5, "W"), (11, 4, "C"),
                                  (12, 2, "W"), (3, 3, "W"), (8, 2, "W"), (2, 12, "P"))))
# Voidreaver: a black blade edged in violet void light, a bone-fanged guard, a Void Heart at the pommel.
icon("voidreaver", {"D": WYRM.shifted(base=1, texture=flat), "N": WYRM.shifted(base=2, texture=flat),
                    "E": WYRM_GLOW.shifted(base=2, texture=flat), "C": WYRM_GLOW.shifted(base=3, texture=flat),
                    "W": WYRM_SPARK, "G": WYRM_BONE.shifted(base=2, texture=flat),
                    "P": WYRM_GLOW.shifted(base=1, texture=flat), "L": WYRM.shifted(base=0, texture=flat)},
     sword("DNNE", "GGPGG", "LGL", length=12,
           extra=((6, 9, "C"), (7, 8, "W"), (8, 7, "C"), (9, 6, "C"), (10, 5, "W"), (11, 4, "C"), (12, 3, "W"), (13, 2, "C"),
                  (3, 3, "W"), (9, 2, "W"), (2, 12, "P"), (3, 12, "P"))))


# ---- combat armour: worn layers
def fangguard_layers(m):
    outer = Sheet()
    # Hood: dark leather with a jaw of fangs for a brim, hanging low at the back and sides.
    outer.fill(HEAD, ["top"], "T")
    outer.tweak_face(HEAD, "top", rim)
    outer.band(HEAD, range(0, 3), "T")
    stamp(outer, HEAD, "front", ["........", "........", "........", "FFFFFFFF", "F.F..F.F"])
    flanks(outer, HEAD, ["........", "........", "........", "FFFFFFFF", "F.F.F.F.", "....TTTT", "....TTTT", "....DDDD"])
    stamp(outer, HEAD, "back", ["........", "........", "........", "FFFFFFFF", "TTTTTTTT", "TTTDDTTT", "TTTDDTTT", "DDDDDDDD"])
    # Jerkin: dark leather, bone fang studs, a laced collar, a belt with a fang buckle.
    outer.fill(BODY, SIDES, "T", rows=range(0, 10))
    stamp(outer, BODY, "front", ["TTTDDTTT", "TFTTTTFT", "TTTTTTTT", "TTTTTTTT", "TFTTTTFT", "TTTTTTTT", "TFTTTTFT", "TTTTTTTT",
                                 "DDDFFDDD", "TTTTTTTT"])
    stamp(outer, BODY, "back", ["TTTTTTTT", "TFTTTTFT", "TTTDDTTT", "TTTDDTTT", "TTTDDTTT", "TTTDDTTT", "TTTDDTTT", "TTTTTTTT",
                                "DDDDDDDD", "TTTTTTTT"])
    outer.band(BODY, [8], "D", faces=("right", "left"))
    outer.fill(ARM, ["top"], "T")
    outer.band(ARM, range(0, 3), "T")
    outer.band(ARM, [3], "D")
    outer.band(ARM, [4], "F")
    # Boots: leather with a bone cuff and a stud.
    outer.band(LEG, range(6, 12), "T")
    outer.band(LEG, [6], "F")
    outer.band(LEG, [7], "D")
    outer.fill(LEG, ["bottom"], "K")
    outer.put(LEG, "front", 1, 9, "F")
    outer.put(LEG, "front", 2, 10, "F")

    inner = Sheet()
    inner.band(LEG, range(0, 9), "T")
    inner.fill(LEG, ["top"], "T")
    stamp(inner, LEG, "front", ["TFFT", "TTTT", "TDDT"], v0=3)
    inner.band(BODY, range(7, 12), "T")
    inner.band(BODY, [7], "D")
    inner.put(BODY, "front", 3, 7, "F")
    inner.put(BODY, "front", 4, 7, "F")
    return outer.render(m), inner.render(m)


def stormplate_layers(m):
    outer = Sheet()
    # Helm: skyiron with a yellow brow trim, a crest stripe, a visor opening.
    outer.fill(HEAD, ["top"], "S")
    outer.fill(HEAD, ["top"], "Y", cols=[3, 4])
    outer.tweak_face(HEAD, "top", rim)
    outer.band(HEAD, range(0, 3), "S")
    outer.band(HEAD, [3], "Y")
    stamp(outer, HEAD, "front", ["SSSSSSSS", "SSSSSSSS", "SSSSSSSS", "YYYYYYYY", "S______S", "SSS__SSS", "SSSSSSSS", "SSSDDSSS"])
    flanks(outer, HEAD, ["SSSSSSSS"] * 3 + ["YYYYYYYY"] + ["SSSSSSSS"] * 4)
    stamp(outer, HEAD, "back", ["SSSSSSSS"] * 3 + ["YYYYYYYY", "SSSRSSSS", "SSRSSSSS", "SSSRSSSS", "SSSSSSSS"])
    outer.put(HEAD, "back", 3, 4, tweak="4")
    # Cuirass: skyiron plate, yellow shoulder trims, glowing bolt runes down the front and back.
    outer.fill(BODY, SIDES, "S", rows=range(0, 10))
    stamp(outer, BODY, "front", ["SSS__SSS", "YYSSSSYY", "SSSSRSSS", "SSSRSSSS", "SSSSRSSS", "SSSRSSSS", "SSSSRSSS", "SSSSSSSS",
                                 "YYYYYYYY", "SSSSSSSS"])
    stamp(outer, BODY, "back", ["SSSSSSSS", "YYSSSSYY", "SSSSRSSS", "SSSRSSSS", "SSSSRSSS", "SSSRSSSS", "SSSSRSSS", "SSSSSSSS",
                                "YYYYYYYY", "SSSSSSSS"])
    flanks(outer, BODY, ["SSSS", "YYYY", "SSSS", "SSRS", "SSSS", "SSSS", "SSSS", "SSSS", "YYYY", "SSSS"])
    for v in (2, 4, 6):
        outer.put(BODY, "front", 4, v, tweak="4")
    for v in (3, 5):
        outer.put(BODY, "front", 3, v, tweak="4")
    outer.fill(ARM, ["top"], "S")
    outer.band(ARM, range(0, 4), "S")
    outer.band(ARM, [1], "Y")
    outer.band(ARM, [4], "Y")
    # Sabatons.
    outer.band(LEG, range(6, 12), "S")
    outer.band(LEG, [6], "Y")
    stamp(outer, LEG, "front", ["..R.", ".R..", "..R."], v0=8)
    outer.fill(LEG, ["bottom"], "D")

    inner = Sheet()
    inner.band(LEG, range(0, 9), "S")
    inner.fill(LEG, ["top"], "S")
    stamp(inner, LEG, "front", ["YYYY", "SRRS", "YYYY"], v0=3)
    inner.band(BODY, range(7, 12), "S")
    inner.band(BODY, [7], "Y")
    stamp(inner, BODY, "front", ["...RR..."], v0=8)
    return outer.render(m), inner.render(m)


def riftweave_layers(m):
    outer = Sheet()
    # Cowl: deep violet, the opening ringed by shadow, a rift seam glowing across the crown and down the back.
    outer.fill(HEAD, ["top"], "C")
    outer.tweak_face(HEAD, "top", rim)
    stamp(outer, HEAD, "top", ["...M....", "...M....", "..DM....", "..D.M...", "...DM...", "....M...", "....DM..", ".....M.."])
    outer.fill(HEAD, ["right", "left", "back"], "C")
    stamp(outer, HEAD, "front", ["CCCCCCCC", "CDDDDDDC", "CD____DC", "CD____DC", "CD____DC", "CD____DC", "CDDDDDDC", "CCCDDCCC"])
    flanks(outer, HEAD, ["CCCCCCCC", "CCCCCCCC", "CCCDCCCC", "CCCCDCCC", "CCCCCCCD", "CCCCCCCD", "CCCCCCCC", "CCCCCCCC"])
    stamp(outer, HEAD, "back", ["CCCCCCCC", "CCCCMCCC", "CCCCMCCC", "CDCCMCCC", "CCDCMCCC", "CCCDMCCC", "CCCCMDCC", "CCCCCDCC"])
    outer.put(HEAD, "front", 3, 1, "M")
    outer.put(HEAD, "front", 4, 1, "M")
    # Robe: folds that don't quite meet, dark seams with magenta in them, a sash.
    outer.fill(BODY, SIDES, "C", rows=range(0, 10))
    stamp(outer, BODY, "front", ["CCC__CCC", "CDCCCCCC", "CCDCCCMC", "CCCDCMCC", "CCCCDMCC", "CDCCCDCC", "CCDCCCDC", "CCCDCCCD",
                                 "DDDMMDDD", "CCCDCCDC"])
    stamp(outer, BODY, "back", ["CCCCCCCC", "CCCCMCCC", "CCCCMCCC", "CDCCMCCC", "CCDCMCCC", "CCCDMCDC", "CCCCMDCC", "CDCCMCCC",
                                "DDDDDDDD", "CCDCCCDC"])
    flanks(outer, BODY, ["CCCC", "CDCC", "CCDC", "CCCC", "CMCC", "CCCC", "DCCC", "CCCC", "DDDD", "CCDC"])
    outer.fill(ARM, ["top"], "C")
    outer.band(ARM, range(0, 3), "C")
    outer.band(ARM, [3, 4], "D")
    stamp(outer, ARM, "front", [".M..", "..D."], v0=1)
    outer.band(LEG, range(6, 12), "C")
    outer.band(LEG, [6, 9], "D")
    stamp(outer, LEG, "front", ["..M.", ".M.."], v0=7)
    outer.fill(LEG, ["bottom"], "D")

    inner = Sheet()
    inner.band(LEG, range(0, 9), "C")
    inner.fill(LEG, ["top"], "C")
    stamp(inner, LEG, "front", ["DC..", ".DC.", "..MD", "..D."], v0=1)
    inner.band(BODY, range(7, 12), "C")
    inner.band(BODY, [7], "D")
    stamp(inner, BODY, "front", ["...MM...", ".D......", "..D...D."], v0=8)
    return outer.render(m), inner.render(m)


def cinderguard_layers(m):
    outer = Sheet()
    # Great helm: blackened plate, a glowing visor slit across the eyes, ember cracks on the crown.
    outer.fill(HEAD, ["top"], "P")
    outer.tweak_face(HEAD, "top", rim)
    stamp(outer, HEAD, "top", ["...E....", "...EE...", "....E...", "....EE..", "...E....", "..EE....", "..E.....", "..EE...."])
    outer.fill(HEAD, SIDES, "P", rows=range(0, 8))
    stamp(outer, HEAD, "front", ["PPPPPPPP", "PPPPPPPP", "PPPPPPPP", "DDDDDDDD", "PEEEEEEP", "PPPDDPPP", "PPPPPPPP", "PPDDDDPP"])
    flanks(outer, HEAD, ["PPPPPPPP", "PPPEPPPP", "PPEPPPPP", "DDDDDDDD", "PPPPEEEE", "PPPPPPPP", "PPPPPPPP", "PPDDDDPP"])
    for u in (1, 2, 3, 4, 5, 6):
        outer.put(HEAD, "front", u, 4, tweak="4")
    # Cuirass: blackened plate with a glowing core behind a window in the breastplate.
    outer.fill(BODY, SIDES, "P", rows=range(0, 10))
    stamp(outer, BODY, "front", ["PPP__PPP", "PDPPPPDP", "PPEEEEPP", "PEAAAAEP", "PEAAAAEP", "PPEEEEPP", "PPPDDPPP", "PPPPPPPP",
                                 "EEEEEEEE", "DPPPPPPD"])
    stamp(outer, BODY, "back", ["PPPPPPPP", "PPPPEPPP", "PPPEEPPP", "PPEPPPPP", "PPEPPEPP", "PPPPPEEP", "PEPPPPPP", "PEPPPPPP",
                                "EEEEEEEE", "DPPPPPPD"])
    flanks(outer, BODY, ["PPPP", "PPPP", "PPPP", "PEPP", "PPEP", "PPPP", "PPPP", "PPPP", "EEEE", "DPPD"])
    for u, v in ((3, 3), (4, 3), (3, 4), (4, 4)):
        outer.put(BODY, "front", u, v, tweak="4")
    outer.fill(ARM, ["top"], "P")
    outer.band(ARM, range(0, 4), "P")
    outer.band(ARM, [4], "E")
    stamp(outer, ARM, "front", [".E..", "..E."], v0=1)
    outer.band(LEG, range(6, 12), "P")
    outer.band(LEG, [6], "D")
    stamp(outer, LEG, "front", [".E..", "..E.", ".E.."], v0=8)
    outer.band(LEG, [11], "E")
    outer.fill(LEG, ["bottom"], "E")

    inner = Sheet()
    inner.band(LEG, range(0, 9), "P")
    inner.fill(LEG, ["top"], "P")
    stamp(inner, LEG, "front", ["..E.", ".E..", "DDDD", "DEED", "DDDD", "..E."], v0=1)
    inner.band(BODY, range(7, 12), "P")
    inner.band(BODY, [7], "E")
    stamp(inner, BODY, "front", ["...AA...", ".E......", "..E...E."], v0=8)
    return outer.render(m), inner.render(m)


def starforged_layers(m):
    outer = Sheet()
    # Crown: a night-blue coif under a silver circlet, stars in the cloth.
    outer.fill(HEAD, ["top"], "N")
    stamp(outer, HEAD, "top", [".W...W..", "....N...", "W.....W.", "...W....", "......W.", ".W......", "....W..W", "..W....."])
    outer.tweak_face(HEAD, "top", rim)
    stamp(outer, HEAD, "front", ["NNNNNNNN", "NWNNNNWN", "NNNNNNNN", "SSSSSSSS"])
    flanks(outer, HEAD, ["NNNNNNNN", "NNWNNNNN", "NNNNNNWN", "SSSSSSSS", "..NNNNNN", "..NWNNNN", "...NNNN.", "....NN.."])
    stamp(outer, HEAD, "back", ["NNNNNNNN", "NNNWNNNN", "NWNNNNNN", "SSSSSSSS", "NNNNNNNN", "NNNNNWNN", ".NNNNNN.", "..NNNN.."])
    # Plate: night-blue, silver trim, stars, a prism at the throat.
    outer.fill(BODY, SIDES, "N", rows=range(0, 10))
    stamp(outer, BODY, "front", ["SSS__SSS", "SNNNNNNS", "NNNPPNNN", "NWNPPNNN", "NNNNNNWN", "NNWNNNNN", "NNNNWNNN", "NWNNNNWN",
                                 "SSSSSSSS", "NNNNNNNN"])
    stamp(outer, BODY, "back", ["SSSSSSSS", "SNNWNNNS", "NNNNNNNN", "NWNNNNWN", "NNNNWNNN", "NNWNNNNN", "NNNNNWNN", "NWNNNNNN",
                                "SSSSSSSS", "NNNNNNNN"])
    flanks(outer, BODY, ["SSSS", "SNNS", "NNNN", "NWNN", "NNNN", "NNWN", "NNNN", "NWNN", "SSSS", "NNNN"])
    outer.put(BODY, "front", 3, 2, tweak="4")
    outer.fill(ARM, ["top"], "S")
    outer.band(ARM, range(0, 4), "N")
    outer.band(ARM, [4], "S")
    stamp(outer, ARM, "front", [".W..", "..W."], v0=1)
    outer.band(LEG, range(6, 12), "N")
    outer.band(LEG, [6], "S")
    stamp(outer, LEG, "front", ["..W.", ".W..", "...W"], v0=7)
    outer.band(LEG, [11], "S")
    outer.fill(LEG, ["bottom"], "D")

    inner = Sheet()
    inner.band(LEG, range(0, 9), "N")
    inner.fill(LEG, ["top"], "N")
    stamp(inner, LEG, "front", ["SSSS", "SWWS", "SSSS"], v0=3)
    stamp(inner, LEG, "front", [".W..", "...W"], v0=7)
    inner.band(BODY, range(7, 12), "N")
    inner.band(BODY, [7], "S")
    stamp(inner, BODY, "front", ["...PP...", "..W..W.."], v0=8)
    return outer.render(m), inner.render(m)


def wyrmscale_layers(m):
    outer = Sheet()
    # Crown: a scaled hood with bone horns; void light in the eyes.
    outer.fill(HEAD, ["top"], "S")
    stamp(outer, HEAD, "top", [".W...W..", "....S...", "W.....W.", "...W....", "......W.", ".W......", "....W..W", "..W....."])
    outer.tweak_face(HEAD, "top", rim)
    stamp(outer, HEAD, "front", ["SSSSSSSS", "SWSSSSWS", "SSSSSSSS", "HHHHHHHH"])
    flanks(outer, HEAD, ["SSSSSSSS", "SSWSSSSS", "SSSSSSWS", "HHHHHHHH", "..SSSSSS", "..SWSSSS", "...SSSS.", "....SS.."])
    stamp(outer, HEAD, "back", ["SSSSSSSS", "SSSWSSSS", "SWSSSSSS", "HHHHHHHH", "SSSSSSSS", "SSSSSWSS", ".SSSSSS.", "..SSSS.."])
    # Plate: overlapping black-violet scale, bone trim, glowing void seams, a core at the throat.
    outer.fill(BODY, SIDES, "S", rows=range(0, 10))
    stamp(outer, BODY, "front", ["HHH__HHH", "HSSSSSSH", "SSSEESSS", "SWSEESSS", "SSSSSSWS", "SSWSSSSS", "SSSSWSSS", "SWSSSSWS",
                                 "HHHHHHHH", "SSSSSSSS"])
    stamp(outer, BODY, "back", ["HHHHHHHH", "HSSWSSSH", "SSSSSSSS", "SWSSSSWS", "SSSSWSSS", "SSWSSSSS", "SSSSSWSS", "SWSSSSSS",
                                "HHHHHHHH", "SSSSSSSS"])
    flanks(outer, BODY, ["HHHH", "HSSH", "SSSS", "SWSS", "SSSS", "SSWS", "SSSS", "SWSS", "HHHH", "SSSS"])
    outer.put(BODY, "front", 3, 2, tweak="4")
    outer.fill(ARM, ["top"], "H")
    outer.band(ARM, range(0, 4), "S")
    outer.band(ARM, [4], "H")
    stamp(outer, ARM, "front", [".W..", "..W."], v0=1)
    outer.band(LEG, range(6, 12), "S")
    outer.band(LEG, [6], "H")
    stamp(outer, LEG, "front", ["..W.", ".W..", "...W"], v0=7)
    outer.band(LEG, [11], "H")
    outer.fill(LEG, ["bottom"], "D")

    inner = Sheet()
    inner.band(LEG, range(0, 9), "S")
    inner.fill(LEG, ["top"], "S")
    stamp(inner, LEG, "front", ["HHHH", "HWWH", "HHHH"], v0=3)
    stamp(inner, LEG, "front", [".W..", "...W"], v0=7)
    inner.band(BODY, range(7, 12), "S")
    inner.band(BODY, [7], "H")
    stamp(inner, BODY, "front", ["...EE...", "..W..W.."], v0=8)
    return outer.render(m), inner.render(m)


COMBAT_SETS = {
    "fangguard": (fangguard_layers,
                  {"T": FANG_LEATHER, "D": FANG_LEATHER.shifted(base=1, texture=flat), "F": FANG_BONE,
                   "K": FANG_LEATHER.shifted(base=0, texture=flat)},
                  {"T": FANG_LEATHER, "D": FANG_LEATHER.shifted(base=1, texture=flat), "F": FANG_BONE,
                   "K": FANG_LEATHER.shifted(base=0, texture=flat)}),
    "stormplate": (stormplate_layers,
                   {"S": SKYPLATE.shifted(texture=plates), "D": SKYPLATE.shifted(base=0, texture=flat),
                    "Y": BOLT.shifted(base=3, texture=flat), "R": BOLT.shifted(base=4, texture=flat)},
                   {"S": SKYPLATE.shifted(texture=polish), "D": SKYPLATE.shifted(base=0, texture=flat),
                    "Y": BOLT.shifted(base=3, texture=flat), "R": BOLT.shifted(base=4, texture=flat)}),
    "riftweave": (riftweave_layers,
                  {"C": RIFT_CLOTH, "D": RIFT_CLOTH.shifted(base=0, texture=flat), "M": RIFT_SEAM},
                  {"C": RIFT_CLOTH, "D": RIFT_CLOTH.shifted(base=0, texture=flat), "M": RIFT_SEAM}),
    "cinderguard": (cinderguard_layers,
                    {"P": CHAR.shifted(texture=plates), "D": CHAR.shifted(base=1, texture=flat), "E": EMBER.shifted(texture=flat),
                     "A": EMBERITE},
                    {"P": CHAR.shifted(texture=plates), "D": CHAR.shifted(base=1, texture=flat), "E": EMBER.shifted(texture=flat),
                     "A": EMBERITE}),
    "starforged": (starforged_layers,
                   {"N": NIGHTSTEEL, "D": NIGHTSTEEL.shifted(base=0, texture=flat), "S": SILVER, "P": PRISM_GLOW, "W": STARWHITE},
                   {"N": NIGHTSTEEL.shifted(texture=polish), "D": NIGHTSTEEL.shifted(base=0, texture=flat),
                    "S": SILVER.shifted(texture=flat), "P": PRISM_GLOW, "W": STARWHITE}),
    "wyrmscale": (wyrmscale_layers,
                  {"S": WYRM.shifted(texture=plates), "D": WYRM.shifted(base=0, texture=flat), "H": WYRM_BONE, "E": WYRM_GLOW, "W": WYRM_SPARK},
                  {"S": WYRM.shifted(texture=plates), "D": WYRM.shifted(base=0, texture=flat), "H": WYRM_BONE.shifted(texture=flat),
                   "E": WYRM_GLOW, "W": WYRM_SPARK}),
}

# Icons. Fangguard: a hood with a jaw of fangs for a brim, a studded jerkin, fang-cuffed boots.
icon("fangguard_helmet", COMBAT_SETS["fangguard"][1], half(
    1, "......TT", ".....TTT", "....TTTT", "...TTTTT", "...TTTTT", "...FFFFF", "...FDFDF", "...TDDDD", "..TTD...", "..TT...."))
icon("fangguard_chestplate", COMBAT_SETS["fangguard"][1], half(
    2, "..TTTDDD", ".TTTTTTD", ".TTTTFTT", ".TT.TTTT", ".TF.TTFT", ".DD.TTTT", "....TFTT", "....DDDF", "....TTTT", "....DDDD"))
icon("fangguard_leggings", COMBAT_SETS["fangguard"][1], half(
    2, "...DDDDF", "...TTTTT", "...TTTTT", "...TTTT.", "...TFFT.", "...TTTT.", "...TTTT.", "...TTTT.", "...TTTT.", "...DDDD."))
icon("fangguard_boots", COMBAT_SETS["fangguard"][1], half(
    4, "...TTTT.", "...FFFF.", "...TTTT.", "...TFTT.", "...TTTT.", ".TTTTTT.", ".TTTTTT.", ".KKKKKK."))

# Stormplate: a crested skyiron helm with a bolt trim, plate with a yellow lightning bolt.
icon("stormplate_helmet", COMBAT_SETS["stormplate"][1], half(
    1, ".......Y", "......YY", ".....SSY", "....SSSS", "...SSSSS", "...SSSSS", "...SYYYY", "...SDDDD", "...SSDDD", "...SS...", "...DD..."))
icon("stormplate_chestplate", COMBAT_SETS["stormplate"][1], half(
    2, "..SSSDDD", ".SSSSSSD", ".YYYSSSY", ".SS.SSYS", ".SS.SYSS", ".DD.SSYS", "....SYSS", "....YYYY", "....SSSS", "....DDDD"))
icon("stormplate_leggings", COMBAT_SETS["stormplate"][1], half(
    2, "...YYYYY", "...SSSSS", "...SSSSS", "...SSSS.", "...SYYS.", "...SSSS.", "...SSYS.", "...SSSS.", "...SSSS.", "...DDDD."))
icon("stormplate_boots", COMBAT_SETS["stormplate"][1], half(
    4, "...YYYY.", "...SSSS.", "...SSSS.", "...SYYS.", "...SSSS.", ".SSSSSS.", ".SSSSSS.", ".DDDDDD."))

# Riftweave: a deep cowl with two violet glints, a robe cut by glowing rift seams, ragged skirts.
icon("riftweave_helmet", COMBAT_SETS["riftweave"][1], half(
    1, ".......C", "......CC", ".....CCC", "....CCCC", "...CCCCC", "...CCDDD", "..CCDDDD", "..CCDMDD", "..CCCCCC", ".CCC...."))
icon("riftweave_chestplate", COMBAT_SETS["riftweave"][1], half(
    2, "..CCCDDD", ".CCCCCDC", ".CDCCCMC", ".CD.CMDC", ".CC.MDCC", ".MM.CCCC", "....CCCC", "....DDDD", "....CMCC", "....CCCM"))
icon("riftweave_leggings", COMBAT_SETS["riftweave"][1], half(
    2, "...DDDDD", "...CCCCC", "...CCDCC", "...CCCC.", "...CDMC.", "...CCDC.", "...CCCC.", "...CMCC.", "...CCCC.", "...D.DD."))
icon("riftweave_boots", COMBAT_SETS["riftweave"][1], half(
    4, "...CCCC.", "...DDDD.", "...CCCC.", "...CMCC.", "...CCCC.", ".CCCCCC.", ".CCCCCC.", ".DDDDDD."))

# Cinderguard: a blackened great helm with a coal-red visor, a cuirass with a glowing core, glowing soles.
icon("cinderguard_helmet", COMBAT_SETS["cinderguard"][1], half(
    1, ".....PPP", "....PPPP", "...PPPPP", "...PPPPP", "...DDDDD", "...PEEEE", "...PEEEE", "...PPPPP", "...PP...", "...DD..."))
icon("cinderguard_chestplate", COMBAT_SETS["cinderguard"][1], half(
    2, "..PPPDDD", ".PPPPPPD", ".PPPPPEE", ".PP.PPEA", ".PP.PPEA", ".DD.PPEE", "....PPPD", "....EEEE", "....PPPP", "....DDDD"))
icon("cinderguard_leggings", COMBAT_SETS["cinderguard"][1], half(
    2, "...DDDAA", "...PPPPP", "...PPPPP", "...PPPP.", "...DEED.", "...PPPP.", "...PEPP.", "...PPPP.", "...PPPP.", "...DDDD."))
icon("cinderguard_boots", COMBAT_SETS["cinderguard"][1], half(
    4, "...DDDD.", "...PPPP.", "...PEPP.", "...PPPP.", "...PPEP.", ".PPPPPP.", ".PPPPPP.", ".EEEEEE."))

# Starforged: a silver crown with prism points, night-blue plate scattered with stars, a prism at the throat.
icon("starforged_helmet", COMBAT_SETS["starforged"][1], half(
    1, ".P...P.P", ".S..SS.S", ".SSSSSSS", "..NNNNNN", "..NWNNNN", "..NNNNNW", "..NNDDDD", "..NNDDDD", "..NN....", "..DD...."))
icon("starforged_chestplate", COMBAT_SETS["starforged"][1], half(
    2, "..NNSSSS", ".SNNNNNS", ".SSNNWNN", ".NN.NNNP", ".NW.NNNP", ".SS.NWNN", "....NNNS", "....SSSS", "....NWNN", "....SSSS"))
icon("starforged_leggings", COMBAT_SETS["starforged"][1], half(
    2, "...SSSSS", "...NNNNN", "...NWNNN", "...NNNN.", "...SPPS.", "...NNNN.", "...NNNW.", "...NNNN.", "...NWNN.", "...SSSS."))
icon("starforged_boots", COMBAT_SETS["starforged"][1], half(
    4, "...SSSS.", "...NNNN.", "...NWNN.", "...NNNN.", "...NNNN.", ".NNNNNN.", ".NNNNNN.", ".SSSSSS."))

# Wyrmscale: a horned scale hood, black-violet scale plate with glowing void seams, a core at the throat.
icon("wyrmscale_helmet", COMBAT_SETS["wyrmscale"][1], half(
    1, ".E...E.E", ".H..HH.H", ".HHHHHHH", "..SSSSSS", "..SWSSSS", "..SSSSSW", "..SSDDDD", "..SSDDDD", "..SS....", "..DD...."))
icon("wyrmscale_chestplate", COMBAT_SETS["wyrmscale"][1], half(
    2, "..SSHHHH", ".HSSSSSH", ".HHSSWSS", ".SS.SSSE", ".SW.SSSE", ".HH.SWSS", "....SSSH", "....HHHH", "....SWSS", "....HHHH"))
icon("wyrmscale_leggings", COMBAT_SETS["wyrmscale"][1], half(
    2, "...HHHHH", "...SSSSS", "...SWSSS", "...SSSS.", "...HEEH.", "...SSSS.", "...SSSW.", "...SSSS.", "...SWSS.", "...HHHH."))
icon("wyrmscale_boots", COMBAT_SETS["wyrmscale"][1], half(
    4, "...HHHH.", "...SSSS.", "...SWSS.", "...SSSS.", "...SSSS.", ".SSSSSS.", ".SSSSSS.", ".HHHHHH."))

# ---------------------------------------------------------------- fishing gear (the Fishing Master's rods and sets)
def fish_scales(u, v, w, h):
    """Overlapping fish scales: every other row is a seam, with a bright fleck at the root of each scale."""
    if v % 2 == 1:
        return -1
    return 1 if (u + (v // 2) * 2) % 4 == 0 else 0


DRIFT = Material(["4a4238", "6e6454", "948a76", "b8ae98", "d8d0bc"], texture=speckle)
TWINE = Material(["5a4a30", "8a7650", "b0a070", "d0c490", "ece0b8"])
ROD_LINE = Material(["8a9098", "b4bac4", "d8dce4", "f0f2f6", "ffffff"])
CORAL = Material(["8a2e44", "c2506a", "e87a8c", "ffa8b0", "ffd8dc"])
SHELL = Material(["a08a8a", "d0bcbc", "f0e4e0", "fff8f4", "ffffff"], texture=plates)
DEEP_NAVY = Material(["04081a", "0a1430", "12204c", "1e3270", "30489a"], texture=cloth)
CYAN_GLOW = Material(["0a5a6a", "10889c", "30c8e0", "80f0ff", "e0ffff"], base=3)
PEARL_WHITE = Material(["8a94a8", "b8c2d4", "dce4f0", "f2f6fc", "ffffff"])
OLIVE = Material(["26280e", "3e4220", "5a6030", "7a8442", "9ca85e"], texture=cloth)
SHIRT_TAN = Material(["5a4a34", "86704c", "b09a6c", "d2bc8c", "ecdcb0"], texture=cloth)
RUBBER_GREEN = Material(["1c2a1e", "2c4230", "406048", "587e60", "78a082"], texture=sheen)
RUBBER_BLACK = Material(["0e0e12", "1c1c24", "2a2a36", "3c3c4c", "565668"], texture=sheen)
SEA_TEAL = Material(["06303a", "0c5660", "14808a", "24b0b0", "6adcd0"], texture=fish_scales)
PALE_GOLD = Material(["7a6a30", "a8963e", "d4c46a", "ece08c", "fffcd0"], texture=sheen)
CORAL_TEAL = Material(["0e4a4e", "16787a", "24a8a0", "5cd8c8", "b0fff0"])

# ---- fishing rods: vanilla FishingRodItems, so each has a rod texture and a _cast one (line out, nothing to see on the rod)
ROD_CAST = {}


def rod_mask(wraps=(), reel=(), cast=False, extra=(), bobber=True):
    """A 16x16 rod mask: shaft from the lower left to the upper right, a thicker handle, reel cells, a line with a bobber.

    S shaft, W wrap, H handle, G tip guide, L line, B bobber. reel and extra are (x, y, letter) cells.
    """
    cells = {}
    for k in range(13):
        x, y = 1 + k, 14 - k
        cells[(x, y)] = "W" if k in wraps else "S"
        if k < 3:
            cells[(x, y + 1)] = "H"
            cells[(x, y)] = "H"
    cells[(14, 1)] = "G"
    if not cast:
        for y in range(2, 11):
            cells[(14, y)] = "L"
        if bobber:
            cells[(14, 11)] = "B"
            cells[(14, 12)] = "B"
    else:
        cells[(14, 2)] = "L"
    for x, y, ch in list(reel) + list(extra):
        cells[(x, y)] = ch
    return ["".join(cells.get((c, r), ".") for c in range(16)) for r in range(16)]


def render_rod(mask, mats, tweak=None):
    """A rod icon whose fishing line (L) and bobber (B) are single pixels, drawn after the outline so they stay thin."""
    body = [row.replace("L", ".").replace("B", ".") for row in mask]
    img = render(body, mats, tweak)
    for y, row in enumerate(mask):
        for x, ch in enumerate(row):
            if ch == "L":
                img.putpixel((x, y), mats["L"].color(2) + (255,))
            elif ch == "B":
                img.putpixel((x, y), mats["B"].color(3 if y % 2 else 1) + (255,))
    return img


def rod_icon(name, mats, **kw):
    icon(name, mats, rod_mask(cast=False, **kw))
    ROD_CAST[name] = (rod_mask(cast=True, **kw), mats, None)


# Driftwood: a pale, knotted stick, twine round the handle, a plain iron reel.
rod_icon("driftwood_rod", {"S": DRIFT.shifted(texture=flat), "W": DRIFT.shifted(base=1, texture=flat), "H": TWINE,
                           "G": IRON.shifted(texture=flat), "L": ROD_LINE, "B": CRIMSON, "R": IRON.shifted(texture=flat),
                           "Q": IRON.shifted(base=4, texture=flat), "N": DRIFT.shifted(base=0, texture=flat)},
         wraps=(3, 7, 11), reel=((5, 11, "R"), (6, 11, "R"), (5, 12, "R"), (6, 12, "Q")),
         extra=((8, 7, "N"), (11, 4, "N")))
# Reef: a coral-pink shaft wrapped in teal, a coral nub at the tip, a shell for a reel.
rod_icon("reef_rod", {"S": CORAL.shifted(texture=flat), "W": CORAL_TEAL.shifted(texture=flat), "H": CORAL_TEAL.shifted(base=1),
                      "G": CORAL_TEAL.shifted(base=3, texture=flat), "L": ROD_LINE, "B": CRIMSON, "R": SHELL.shifted(texture=flat),
                      "P": SHELL.shifted(base=1, texture=flat), "C": CORAL.shifted(base=3, texture=flat)},
         wraps=(2, 6, 10), reel=((5, 11, "R"), (6, 11, "R"), (5, 12, "P"), (6, 12, "R"), (6, 13, "P")),
         extra=((13, 1, "C"), (12, 1, "C"), (13, 0, "C")))
# Stormcaller: steel-blue, a yellow lightning wrap zigzagging up the shaft, a dark reel with a bolt on it.
rod_icon("stormcaller_rod", {"S": SKYPLATE.shifted(texture=flat), "W": BOLT.shifted(texture=flat), "H": SOIL_LEATHER.shifted(base=1),
                             "G": BOLT.shifted(base=3, texture=flat), "L": ROD_LINE, "B": BOLT.shifted(base=3, texture=flat),
                             "R": DARK_IRON.shifted(texture=flat), "P": BOLT.shifted(base=3, texture=flat)},
         wraps=(3, 5, 7, 9, 11), reel=((5, 11, "R"), (6, 11, "R"), (5, 12, "R"), (6, 12, "P")),
         extra=((6, 9, "W"), (8, 7, "W"), (10, 5, "W"), (12, 3, "W")))
# Abyssal: dark navy, pale wraps, a reel with a glowing cyan pearl in it.
rod_icon("abyssal_rod", {"S": DEEP_NAVY.shifted(texture=flat), "W": DEEP_NAVY.shifted(base=3, texture=flat),
                         "H": DEEP_NAVY.shifted(base=1, texture=flat), "G": CYAN_GLOW, "L": CYAN_GLOW.shifted(base=2),
                         "B": CYAN_GLOW, "R": DARK_IRON.shifted(base=1, texture=flat), "C": CYAN_GLOW.shifted(base=4)},
         wraps=(2, 5, 8, 11), reel=((5, 11, "R"), (6, 11, "R"), (7, 11, "R"), (5, 12, "R"), (6, 12, "C"), (7, 12, "R"),
                                    (5, 13, "R"), (6, 13, "R"), (7, 13, "R")))
# Leviathan: a teal shaft with gold wraps and a spiked gold tip, a gold-rimmed scale for a reel plate.
rod_icon("leviathan_rod", {"S": SEA_TEAL.shifted(texture=flat), "W": GOLD.shifted(texture=flat), "H": GOLD.shifted(base=1),
                           "G": PALE_GOLD.shifted(base=3, texture=flat), "L": ROD_LINE, "B": GOLD.shifted(base=3),
                           "R": GOLD.shifted(texture=flat), "P": SEA_TEAL.shifted(base=3, texture=flat),
                           "A": CYAN_GLOW.shifted(base=3)},
         wraps=(2, 4, 6, 8, 10, 12), reel=((5, 11, "R"), (6, 11, "R"), (7, 11, "R"), (5, 12, "R"), (6, 12, "P"), (7, 12, "R"),
                                           (5, 13, "R"), (6, 13, "R"), (7, 13, "R")),
         extra=((14, 0, "G"), (13, 1, "G"), (12, 2, "A"), (6, 12, "A")))


# ---- fishing armour: worn layers
def angler_layers(m):
    outer = Sheet()
    # Straw hat crown with a dark band and a red lure tucked in it.
    outer.fill(HEAD, ["top"], "S")
    outer.tweak_face(HEAD, "top", rim)
    outer.band(HEAD, range(0, 3), "S")
    outer.band(HEAD, [3], "K")
    outer.put(HEAD, "front", 5, 3, "R")
    outer.put(HEAD, "front", 6, 3, "H")
    # Olive vest over a tan shirt, pockets with buttons, a strap at the waist.
    outer.fill(BODY, SIDES, "T", rows=range(0, 10))
    stamp(outer, BODY, "front", ["VV____VV", "VVTTTTVV", "VPPTTPPV", "VPHTTHPV", "VVVTTVVV", "VPPPPPPV", "VPHPPHPV", "VPPPPPPV",
                                 "KKKKKKKK", "VVVVVVVV"])
    stamp(outer, BODY, "back", ["VVVVVVVV", "VVVVVVVV", "VVVVVVVV", "VVPPPPVV", "VVPHHPVV", "VVVVVVVV", "VVVVVVVV", "VVVVVVVV",
                                "KKKKKKKK", "VVVVVVVV"])
    flanks(outer, BODY, ["VVVV", "VVVV", "TVVT", "TVVT", "TVVT", "TVVT", "TVVT", "TVVT", "KKKK", "VVVV"])
    outer.fill(ARM, ["top"], "T")
    outer.band(ARM, range(0, 5), "T")
    outer.band(ARM, [5], "K")
    # Rubber boots with a folded wader cuff.
    outer.band(LEG, range(6, 12), "B")
    outer.band(LEG, range(6, 8), "W")
    outer.fill(LEG, ["bottom"], "K")
    outer.put(LEG, "front", 1, 9, "H")

    inner = Sheet()
    # Waders: rubber from the chest down, shoulder straps, patched knees.
    inner.band(LEG, range(0, 9), "W")
    inner.fill(LEG, ["top"], "W")
    stamp(inner, LEG, "front", ["DDDD", "DDDD"], v0=4)
    inner.band(BODY, range(7, 12), "W")
    inner.band(BODY, [7], "K")
    inner.put(BODY, "front", 3, 7, "H")
    inner.put(BODY, "front", 4, 7, "H")
    return outer.render(m), inner.render(m)


def reefwalker_layers(m):
    outer = Sheet()
    # Diving helm: a brass dome with a coral crest, rivets and a porthole you can see the face through.
    outer.fill(HEAD, ["top"], "C")
    outer.fill(HEAD, ["top"], "R", cols=[3, 4])
    outer.tweak_face(HEAD, "top", rim)
    outer.fill(HEAD, ["right", "left", "back"], "C")
    stamp(outer, HEAD, "front", ["CCCCCCCC", "CCKKKKCC", "CK____KC", "CK____KC", "CK____KC", "CK____KC", "CCKKKKCC", "KKKKKKKK"])
    flanks(outer, HEAD, ["CCCCCCCC", "CHCCCCHC", "CCCCCCCC", "CCCCCCCC", "CCCHCCCC", "CCCCCCCC", "CHCCCCHC", "KKKKKKKK"])
    stamp(outer, HEAD, "back", ["CCCCCCCC", "CHCCCCHC", "CCCKKCCC", "CCCKKCCC", "CCCKKCCC", "CCCCCCCC", "CHCCCCHC", "KKKKKKKK"])
    # Sailcloth shirt under overlapping shell plates, a brass collar, a rope belt.
    outer.fill(BODY, SIDES, "V", rows=range(0, 10))
    stamp(outer, BODY, "front", ["CCC__CCC", "SSCCCCSS", "VSSSSSSV", "VVSSSSVV", "VSSSSSSV", "VVSSSSVV", "VSSSSSSV", "VVVVVVVV",
                                 "KKKKCKKK", "VVVVVVVV"])
    stamp(outer, BODY, "back", ["CCCCCCCC", "SSSSSSSS", "VSSSSSSV", "VVSSSSVV", "VSSSSSSV", "VVSSSSVV", "VSSSSSSV", "VVVVVVVV",
                                "KKKKKKKK", "VVVVVVVV"])
    flanks(outer, BODY, ["CCCC", "SSSS", "VSSV", "VVVV", "VSSV", "VVVV", "VSSV", "VVVV", "KKKK", "VVVV"])
    outer.fill(ARM, ["top"], "S")
    outer.band(ARM, range(0, 3), "S")
    outer.band(ARM, range(3, 5), "V")
    # Flippers: teal rubber over a canvas cuff.
    outer.band(LEG, range(6, 12), "T")
    outer.band(LEG, [6], "V")
    outer.band(LEG, [11], "D")
    outer.fill(LEG, ["bottom"], "D")

    inner = Sheet()
    inner.band(LEG, range(0, 9), "V")
    inner.fill(LEG, ["top"], "V")
    stamp(inner, LEG, "front", ["SSSS", "SSSS", "SSSS"], v0=3)
    inner.band(BODY, range(7, 12), "V")
    inner.band(BODY, [7], "K")
    inner.put(BODY, "front", 3, 7, "C")
    inner.put(BODY, "front", 4, 7, "C")
    return outer.render(m), inner.render(m)


def abyssal_layers(m):
    outer = Sheet()
    # Hood: deep navy, the face opening lined with pearl.
    outer.fill(HEAD, ["top"], "N")
    outer.tweak_face(HEAD, "top", rim)
    outer.fill(HEAD, ["right", "left"], "N", rows=range(0, 7))
    stamp(outer, HEAD, "front", ["NNNNNNNN", "NPPPPPPN", "NP____PN", "NP____PN", "NP____PN", "NP____PN", "NPPPPPPN", "NNNNNNNN"])
    flanks(outer, HEAD, ["NNNNNNNN"] * 6 + ["NNNNNNNC", "NCNN.N.C"])
    stamp(outer, HEAD, "back", ["NNNNNNNN", "NNNNNNNN", "NNNDDNNN", "NNNDDNNN", "NNNDDNNN", "NNNNNNNN", "NCNNNNCN", "NN.NN.N."])
    # Mantle: pearl collar, a dark hem, cyan drips running down from it.
    outer.fill(BODY, SIDES, "N", rows=range(0, 10))
    stamp(outer, BODY, "front", ["PP____PP", "NNNPPNNN", "NNNNNNNN", "NNNNNNNN", "DDDDDDDD", "NCNNNNCN", "NNNCNNNN", "NNNNNNNN",
                                 "DDDDDDDD", "NNCNNCNN"])
    stamp(outer, BODY, "back", ["PPPPPPPP", "NNNNNNNN", "NNNNNNNN", "NNNNNNNN", "DDDDDDDD", "NNCNNNCN", "NNNNCNNN", "NNNNNNNN",
                                "DDDDDDDD", "NCNNNNCN"])
    flanks(outer, BODY, ["PPPP", "NNNN", "NNNN", "NNNN", "DDDD", "NCNN", "NNNC", "NNNN", "DDDD", "NNCN"])
    for u, v in ((1, 5), (6, 5), (3, 6), (2, 9), (5, 9)):
        outer.put(BODY, "front", u, v, tweak="4")
    outer.fill(ARM, ["top"], "N")
    outer.band(ARM, range(0, 4), "N")
    outer.band(ARM, [3], "D")
    outer.band(ARM, [4], "N")
    stamp(outer, ARM, "front", [".C..", "...C"], v0=5)
    # Heavy boots: iron-shod, a cyan glint in the cuff.
    outer.band(LEG, range(6, 12), "I")
    outer.band(LEG, [6], "D")
    outer.band(LEG, [7], "N")
    outer.put(LEG, "front", 1, 7, "C")
    outer.fill(LEG, ["bottom"], "D")

    inner = Sheet()
    inner.band(LEG, range(0, 9), "N")
    inner.fill(LEG, ["top"], "N")
    stamp(inner, LEG, "front", ["DDDD", "NCNN", "NNNN", "DDDD"], v0=4)
    inner.band(BODY, range(7, 12), "N")
    inner.band(BODY, [7], "D")
    inner.put(BODY, "front", 3, 7, "C")
    inner.put(BODY, "front", 4, 7, "C")
    return outer.render(m), inner.render(m)


def leviathan_layers(m):
    outer = Sheet()
    # Crown: a scaled teal coif under a pale gold circlet (the spines are drawn in 3D).
    outer.fill(HEAD, ["top"], "T")
    outer.tweak_face(HEAD, "top", rim)
    outer.band(HEAD, range(0, 3), "T")
    outer.band(HEAD, [3], "G")
    flanks(outer, HEAD, ["TTTTTTTT"] * 3 + ["GGGGGGGG", "..TTTTTT", "..TTTTTT", "...TTTT.", "....TT.."])
    stamp(outer, HEAD, "back", ["TTTTTTTT"] * 3 + ["GGGGGGGG", "TTTTTTTT", "TTTTTTTT", ".TTTTTT.", "..TTTT.."])
    outer.put(HEAD, "front", 3, 3, "A")
    outer.put(HEAD, "front", 4, 3, "A")
    # Plate: overlapping teal scales, gold trim down the middle and at the waist, ragged fin hem.
    outer.fill(BODY, SIDES, "T", rows=range(0, 10))
    stamp(outer, BODY, "front", ["GGG__GGG", "FTTGGTTF", "TTTGGTTT", "TTTGGTTT", "TTTTTTTT", "TTTGGTTT", "TTTTTTTT", "TTTTTTTT",
                                 "GGGGGGGG", "FTFTFTFT"])
    stamp(outer, BODY, "back", ["GGGGGGGG", "FTTGGTTF", "TTTGGTTT", "TTTGGTTT", "TTTGGTTT", "TTTGGTTT", "TTTTTTTT", "TTTTTTTT",
                                "GGGGGGGG", "FTFTFTFT"])
    flanks(outer, BODY, ["GGGG", "FTTF", "TTTT", "TTTT", "TTTT", "TTTT", "TTTT", "TTTT", "GGGG", "FTFT"])
    outer.put(BODY, "front", 3, 3, "A", tweak="4")
    outer.fill(ARM, ["top"], "G")
    outer.band(ARM, range(0, 4), "T")
    outer.band(ARM, [4], "G")
    outer.band(ARM, [1], "F")
    # Boots: scaled, a gold cuff, a fin on each side.
    outer.band(LEG, range(6, 12), "T")
    outer.band(LEG, [6], "G")
    outer.band(LEG, [11], "G")
    flanks(outer, LEG, ["....", "FFF.", "FF..", "F..."], v0=7)
    outer.fill(LEG, ["bottom"], "D")

    inner = Sheet()
    # Fin tassets over scaled legs, a gold belt with a sea-glass stud.
    inner.band(LEG, range(0, 9), "T")
    inner.fill(LEG, ["top"], "T")
    stamp(inner, LEG, "front", ["FFFF", "FTTF", "FFFF"], v0=2)
    stamp(inner, LEG, "front", ["GGGG", "GAAG", "GGGG"], v0=5)
    inner.band(BODY, range(7, 12), "T")
    inner.band(BODY, [7], "G")
    stamp(inner, BODY, "front", ["...AA...", "..FTTF.."], v0=8)
    return outer.render(m), inner.render(m)


FISHING_SETS = {
    "angler": (angler_layers,
               {"S": STRAW, "Z": STRAW.shifted(base=1), "K": OLIVE.shifted(base=0, texture=flat), "R": CRIMSON, "H": IRON.shifted(texture=flat),
                "V": OLIVE, "P": OLIVE.shifted(base=1, texture=flat), "T": SHIRT_TAN, "D": RUBBER_GREEN.shifted(base=1, texture=flat),
                "W": RUBBER_GREEN, "B": RUBBER_BLACK},
               {"S": STRAW, "K": OLIVE.shifted(base=0, texture=flat), "R": CRIMSON, "H": IRON.shifted(texture=flat),
                "V": OLIVE, "P": OLIVE.shifted(base=1, texture=flat), "T": SHIRT_TAN, "D": RUBBER_GREEN.shifted(base=1, texture=flat),
                "W": RUBBER_GREEN, "B": RUBBER_BLACK}),
    "reefwalker": (reefwalker_layers,
                   {"C": BRASS, "K": BRASS.shifted(base=1, texture=flat), "H": BRASS.shifted(base=4, texture=flat), "R": CORAL,
                    "V": CANVAS, "S": SHELL, "T": CORAL_TEAL.shifted(texture=flat), "D": CORAL_TEAL.shifted(base=1, texture=flat),
                    "G": Material(["0a3a4a", "1a6a80", "40a8c4", "90dcf0", "e0fcff"], base=3)},
                   {"C": BRASS.shifted(texture=polish), "K": BRASS.shifted(base=1, texture=flat), "H": BRASS.shifted(base=4, texture=flat),
                    "R": CORAL, "V": CANVAS, "S": SHELL, "T": CORAL_TEAL.shifted(texture=flat),
                    "D": CORAL_TEAL.shifted(base=1, texture=flat)}),
    "abyssal": (abyssal_layers,
                {"N": DEEP_NAVY, "D": DEEP_NAVY.shifted(base=0, texture=flat), "P": PEARL_WHITE, "C": CYAN_GLOW,
                 "I": DARK_IRON.shifted(texture=plates)},
                {"N": DEEP_NAVY, "D": DEEP_NAVY.shifted(base=0, texture=flat), "P": PEARL_WHITE, "C": CYAN_GLOW,
                 "I": DARK_IRON.shifted(texture=plates)}),
    "leviathan": (leviathan_layers,
                  {"T": SEA_TEAL, "F": SEA_TEAL.shifted(base=3, texture=flat), "G": PALE_GOLD, "A": CYAN_GLOW,
                   "D": SEA_TEAL.shifted(base=0, texture=flat)},
                  {"T": SEA_TEAL, "F": SEA_TEAL.shifted(base=3, texture=flat), "G": PALE_GOLD.shifted(texture=flat), "A": CYAN_GLOW,
                   "D": SEA_TEAL.shifted(base=0, texture=flat)}),
}

# Icons. Angler: a wide straw hat with a lure in the band, a pocketed vest, rubber waders and boots.
icon("angler_helmet", FISHING_SETS["angler"][1], sprite(
    3, "......SSSS......", ".....SSSSSS.....", ".....SSSSSS.....", ".....KKKKKRR....", "..SSSSSSSSSRHS..", ".SSSSSSSSSSSSSS.",
    ".ZZZZZZZZZZZZZZ.", "..ZZZZZZZZZZZZ.."))
icon("angler_chestplate", FISHING_SETS["angler"][1], half(
    2, "..VVVVKK", ".VVVVVVK", ".VVVVVVV", ".TT.PPVV", ".TT.PHVV", ".TT.VVVV", ".TT.PPPV", "....PHPV", "....PPPV", "....KKKK"))
icon("angler_leggings", FISHING_SETS["angler"][1], half(
    2, "...KKKKH", "...WWWWW", "...WWWWW", "...WWWW.", "...WDDW.", "...WWWW.", "...WWWW.", "...WWWW.", "...WWWW.", "...BBBB."))
icon("angler_boots", FISHING_SETS["angler"][1], half(
    4, "...WWWW.", "...BBBB.", "...BBBB.", "...BHBB.", "...BBBB.", ".BBBBBB.", ".BBBBBB.", ".KKKKKK."))

# Reefwalker: a brass diving helm with a coral crest and porthole, shell plates over sailcloth, flippers.
icon("reefwalker_helmet", FISHING_SETS["reefwalker"][1], half(
    1, ".....RRR", "....CCCC", "...CCCCC", "...CCHCC", "...CKGGG", "...CKGGG", "...CCKKK", "..CCCCCC", "..KKKKKK"))
icon("reefwalker_chestplate", FISHING_SETS["reefwalker"][1], half(
    2, "..SSCCCC", ".SSSSVVV", ".SSSVVVV", ".VV.SSSS", ".VV.SSSS", ".VK.SSSS", "....SSSS", "....KKKK", "....VVVV", "....KKKK"))
icon("reefwalker_leggings", FISHING_SETS["reefwalker"][1], half(
    2, "...KKKKC", "...VVVVV", "...VVVVV", "...VVVV.", "...VSSV.", "...VSSV.", "...VVVV.", "...VVVV.", "...VVVV.", "...KKKK."))
icon("reefwalker_boots", FISHING_SETS["reefwalker"][1], half(
    4, "...VVVV.", "...TTTT.", "...TTTT.", "..TTTTT.", ".TTTTTT.", "TTTTTTT.", "TDTDTDT."))

# Abyssal: a navy hood lined with pearl, a mantle dripping glowing cyan, heavy iron-shod boots.
icon("abyssal_helmet", FISHING_SETS["abyssal"][1], half(
    1, ".......N", "......NN", ".....NNN", "....NNNN", "...NNNNN", "...NPPPP", "...NPDDD", "...NPDCD", "...NPPPP", "..NNNNNN", "..NC..C."))
icon("abyssal_chestplate", FISHING_SETS["abyssal"][1], half(
    2, "..NNNPPP", ".NNNNNNP", ".NNNNNNN", ".DDD.NNN", ".NCN.NCN", ".NNN.NNN", ".NNN.NNN", ".DDD.DDD", "....NNCN", "....NNNN"))
icon("abyssal_leggings", FISHING_SETS["abyssal"][1], half(
    2, "...DDDDC", "...NNNNN", "...NNNNN", "...NNNN.", "...NCNN.", "...NNNN.", "...NNNN.", "...NNNN.", "...NNNN.", "...DDDD."))
icon("abyssal_boots", FISHING_SETS["abyssal"][1], half(
    4, "...DDDD.", "...NNNN.", "...NNCN.", "...IIII.", "...IIII.", ".IIIIII.", ".IIIIII.", ".DDDDDD."))

# Leviathan: a crown of pale gold spines, overlapping teal scale plate with gold trim, fin tassets.
icon("leviathan_helmet", FISHING_SETS["leviathan"][1], half(
    0, ".......G", ".G..G..G", ".GG.GG.G", ".GGGGGGG", "..TTTTTT", "..TTTTTT", "..TTDDDD", "..TTDDDD", "..TT....", "..DD...."))
icon("leviathan_chestplate", FISHING_SETS["leviathan"][1], half(
    2, "..TTGGGG", ".FTTTTTG", "FFTTTTTT", ".TT.TTTT", ".TT.TTTT", ".GG.TTTT", "....TTTT", "....GGGG", "....TTTT", "....FFFF"))
icon("leviathan_leggings", FISHING_SETS["leviathan"][1], half(
    2, "...GGGGG", "...TTTTT", "...TTTTT", "...TTTT.", "...GFFG.", "...TTTT.", "...TFFT.", "...TTTT.", "...TTTT.", "...FFFF."))
icon("leviathan_boots", FISHING_SETS["leviathan"][1], half(
    4, "...GGGG.", "...TTTT.", "...TTTT.", "...TFFT.", "...TTTT.", ".TTTTTT.", ".TTTTTT.", ".GGGGGG."))

# ---- world dungeon gear (boss drops from the skyrpg_world dungeons)
GRAVE_CLOTH = Material(["201a1c", "3a3236", "57494a", "7a6a66", "a09088"], texture=cloth)
CHITIN_GREEN = Material(["04120a", "0a2a16", "14482a", "24703e", "3a9e58"])
PRISMARINE = Material(["0a3a42", "16686e", "28a096", "5cd8c8", "c0fff4"], base=3)
COAL_IRON = Material(["0a0c10", "181c24", "2a303c", "424a5a", "646e82"], texture=sheen)

icon("cryptbreaker", {"D": COAL_IRON.shifted(base=1, texture=flat), "S": COAL_IRON.shifted(base=3, texture=flat),
                      "E": FANG_BONE.shifted(base=3, texture=flat), "V": ELDER.shifted(base=3, texture=flat),
                      "H": ELDER.shifted(base=4, texture=flat), "B": FANG_BONE.shifted(base=2, texture=flat),
                      "G": FANG_BONE.shifted(base=1, texture=flat), "K": FANG_BONE.shifted(base=0, texture=flat),
                      "N": DREAD_BONE.shifted(base=3, texture=flat)},
     sword("DSSE", "GGBVBGG", "BNK", length=10, base=(4, 11),
           extra=((5, 10, "V"), (7, 8, "H"), (9, 6, "V"), (10, 5, "H"), (8, 9, "S"), (11, 4, "V"), (6, 9, "V"))))
icon("arachne_fang", {"D": CHITIN_GREEN.shifted(base=2, texture=flat), "C": CHITIN_GREEN.shifted(base=3, texture=flat),
                      "E": VENOM.shifted(base=3, texture=flat), "V": VENOM.shifted(texture=flat),
                      "W": VENOM.shifted(base=4, texture=flat), "G": CHITIN_GREEN.shifted(base=1, texture=flat),
                      "L": CHITIN_GREEN.shifted(base=0, texture=flat), "H": VENOM.shifted(base=3, texture=flat)},
     sword("DCE", "GLGLG", "GHL", length=10, bend=(0, 0, 0, 1, 1, 1, 2, 2, 2, 2),
           extra=((14, 2, "W"), (14, 3, "V"), (15, 4, "W"), (6, 9, "V"))))
icon("tidecaller_blade", {"D": PRISMARINE.shifted(base=1, texture=flat), "S": PRISMARINE.shifted(base=2, texture=flat),
                          "E": PRISMARINE.shifted(base=4, texture=flat), "C": CORAL.shifted(base=2),
                          "O": CORAL.shifted(base=3), "W": PRISMARINE.shifted(base=4, texture=flat),
                          "L": DEEP_NAVY.shifted(base=3, texture=flat), "K": DEEP_NAVY.shifted(base=1, texture=flat),
                          "G": CORAL.shifted(base=1)},
     sword("DSSE", "COGOC", "LKL", length=11, bend=(0, 0, 1, 1, 0, 0, 1, 1, 0, 0, 0),
           extra=((6, 9, "W"), (8, 7, "W"), (10, 5, "W"), (12, 3, "W"), (7, 8, "E"), (9, 6, "E"), (11, 4, "E"))))


# ---- danger-region gear (crafted from region essences)
BLIGHT_STEEL = Material(["14040e", "34102a", "5a1c42", "8a2e60", "c05a8c"], texture=sheen)
ASH_STEEL = Material(["060505", "141010", "262020", "3e3434", "5a4e4e"], texture=sheen)
RIME_STEEL = Material(["4a6a88", "7a9cbc", "a8cae2", "d0e8f6", "ffffff"], texture=sheen)
BOG_STEEL = Material(["0c1c0a", "1c3414", "34562a", "56803a", "88b04c"], texture=sheen)

icon("blightreaver", {"D": BLIGHT_STEEL.shifted(base=1, texture=flat), "S": BLIGHT_STEEL.shifted(base=2, texture=flat),
                      "E": CRIMSON.shifted(base=3, texture=flat), "G": BLIGHT_STEEL.shifted(base=0, texture=flat),
                      "R": CRIMSON.shifted(base=4, texture=flat), "K": DARK_IRON.shifted(base=1, texture=flat),
                      "B": CRIMSON.shifted(base=2, texture=flat)},
     sword("DSSE", "GGBRBGG", "KBK", length=10, base=(4, 11),
           extra=((5, 10, "R"), (7, 8, "E"), (9, 6, "R"), (10, 5, "E"), (8, 9, "S"), (11, 4, "R"), (6, 9, "R"))))
icon("cinderbrand", {"D": ASH_STEEL.shifted(base=1, texture=flat), "S": ASH_STEEL.shifted(base=2, texture=flat),
                     "E": EMBERITE.shifted(base=3, texture=flat), "H": EMBERITE.shifted(base=4, texture=flat),
                     "G": CHAR.shifted(base=2, texture=flat), "K": CHAR.shifted(base=0, texture=flat),
                     "O": EMBERITE.shifted(base=2, texture=flat)},
     sword("DSE", "GGOHOGG", "KGK", length=10, bend=(0, 0, 1, 1, 0, 0, 1, 1, 0, 0),
           extra=((6, 9, "O"), (8, 7, "H"), (10, 5, "O"), (12, 3, "H"), (7, 8, "E"), (9, 6, "E"), (11, 4, "E"))))
icon("rimecleaver", {"D": RIME_STEEL.shifted(base=1, texture=flat), "S": RIME_STEEL.shifted(base=3, texture=flat),
                     "E": RIME_STEEL.shifted(base=4, texture=flat), "G": FROST_STEEL.shifted(base=1, texture=flat),
                     "B": FROST_STEEL.shifted(base=3, texture=flat), "L": FROSTBERRY.shifted(base=1, texture=flat),
                     "K": FROSTBERRY.shifted(base=3, texture=flat)},
     sword("DSSE", "GGBEBGG", "LKL", length=11, base=(4, 11),
           extra=((6, 9, "E"), (8, 7, "E"), (10, 5, "E"), (12, 3, "E"), (7, 8, "S"), (9, 6, "S"), (11, 4, "S"))))
icon("mirefang", {"D": BOG_STEEL.shifted(base=1, texture=flat), "C": BOG_STEEL.shifted(base=3, texture=flat),
                  "E": VENOM.shifted(base=3, texture=flat), "V": VENOM.shifted(texture=flat),
                  "W": VENOM.shifted(base=4, texture=flat), "G": BOG_STEEL.shifted(base=1, texture=flat),
                  "L": BOG_STEEL.shifted(base=0, texture=flat), "H": VENOM.shifted(base=3, texture=flat)},
     sword("DCE", "GLGLG", "GHL", length=10, bend=(0, 0, 1, 1, 1, 2, 2, 2, 3, 3),
           extra=((14, 2, "W"), (13, 3, "V"), (15, 4, "W"), (6, 9, "V"), (8, 7, "V"))))


def wardens_shroud_layers(m):
    outer = Sheet()
    # Hood: tattered grave-cloth over a dark iron brow, two purple soul-lights where the eyes are.
    outer.fill(HEAD, ["top"], "T")
    outer.tweak_face(HEAD, "top", rim)
    outer.band(HEAD, range(0, 3), "T")
    stamp(outer, HEAD, "front", ["TTTTTTTT", "TTTTTTTT", "TTTTTTTT", "IIIIIIII", "TP....PT", "T......T"])
    flanks(outer, HEAD, ["TTTTTTTT", "TTTTTTTT", "TTTTTTTT", "IIIIIIII", "T.T.T.T.", "....TTTT", "....TTTT", "....DDDD"])
    stamp(outer, HEAD, "back", ["TTTTTTTT", "TTTTTTTT", "TTTTTTTT", "IIIIIIII", "TTTTTTTT", "TTTDDTTT", "TTTDDTTT", "DTDTDTDT"])
    # Shroud: burial wrappings over dark iron plates, a soul gem in the breast, a ragged hem.
    outer.fill(BODY, SIDES, "T", rows=range(0, 10))
    stamp(outer, BODY, "front", ["TTTDDTTT", "TDTTTTDT", "TTIIIITT", "TIIDPDIT", "TIIIPIIT", "TTIIIITT", "TTTDDTTT", "TDTTTTDT",
                                 "DDDIIDDD", "TTTTTTTT"])
    stamp(outer, BODY, "back", ["TTTTTTTT", "TDTTTTDT", "TTTDDTTT", "TTTDPTTT", "TTTDDTTT", "TTTDDTTT", "TTTDDTTT", "TTTTTTTT",
                                "DDDDDDDD", "TDTTDTTD"])
    outer.band(BODY, [8], "D", faces=("right", "left"))
    outer.fill(ARM, ["top"], "I")
    outer.band(ARM, range(0, 3), "T")
    outer.band(ARM, [1], "I")
    outer.band(ARM, [3], "D")
    outer.band(ARM, [4], "I")
    # Boots: wrapped greaves, an iron cuff and a soul-light.
    outer.band(LEG, range(6, 12), "T")
    outer.band(LEG, [6], "I")
    outer.band(LEG, [7], "D")
    outer.fill(LEG, ["bottom"], "K")
    outer.put(LEG, "front", 1, 9, "P")
    outer.put(LEG, "front", 2, 10, "D")

    inner = Sheet()
    inner.band(LEG, range(0, 9), "T")
    inner.fill(LEG, ["top"], "T")
    stamp(inner, LEG, "front", ["TIIT", "TTTT", "TDDT"], v0=3)
    inner.band(BODY, range(7, 12), "T")
    inner.band(BODY, [7], "D")
    inner.put(BODY, "front", 3, 7, "P")
    inner.put(BODY, "front", 4, 7, "P")
    return outer.render(m), inner.render(m)


_SHROUD_MATS = {"T": GRAVE_CLOTH, "D": COAL_IRON.shifted(base=1, texture=flat), "I": COAL_IRON.shifted(texture=polish),
                "K": COAL_IRON.shifted(base=0, texture=flat), "P": ELDER.shifted(texture=flat)}
_SHROUD_ICON = {"T": GRAVE_CLOTH, "D": COAL_IRON.shifted(base=1, texture=flat), "I": COAL_IRON.shifted(texture=flat),
                "K": COAL_IRON.shifted(base=0, texture=flat), "P": ELDER.shifted(texture=flat)}
DUNGEON_SETS = {"wardens_shroud": (wardens_shroud_layers, _SHROUD_ICON, _SHROUD_MATS)}

# Warden's Shroud: a tattered hood over a dark iron brow with two purple eyes, wrapped plate with a soul gem.
icon("wardens_shroud_helmet", _SHROUD_ICON, half(
    1, "......TT", ".....TTT", "....TTTT", "...TTTTT", "...TTTTT", "...IIIII", "...IPDDD", "...TDDDD", "..TTD...", "..TT.T.."))
icon("wardens_shroud_chestplate", _SHROUD_ICON, half(
    2, "..IITDDD", ".ITTTTTD", ".TTTTIIT", ".TT.TIPP", ".TI.TIPP", ".DD.TTII", "....TTTT", "....DDDI", "....TTTT", "....DTDT"))
icon("wardens_shroud_leggings", _SHROUD_ICON, half(
    2, "...DDDDI", "...TTTTT", "...TTTTT", "...TTTT.", "...TIIT.", "...TTTT.", "...TTTT.", "...TTTT.", "...TTTT.", "...D.DD."))
icon("wardens_shroud_boots", _SHROUD_ICON, half(
    4, "...TTTT.", "...IIII.", "...TTTT.", "...TPTT.", "...TTTT.", ".TTTTTT.", ".TTTTTT.", ".KKKKKK."))

# ---- region and dungeon armour sets (blightforged, cinderplate, rimeguard, mirewoven, arachne_carapace, tide_regalia)
# Letters: P main plate, D dark, K darkest, A first accent, B second accent, C trim / cloth (see each set's mats).
def _limbs(o, i, shoulder, arm, arm_trim, boot, boot_trim, toe, gem, inner, plate, dark, glint):
    """Shoulders, vambraces, boots and the inner (leggings) layer shared by the new sets."""
    o.fill(ARM, ["top"], shoulder)
    o.band(ARM, range(0, 2), shoulder)
    o.band(ARM, [2], arm_trim)
    o.band(ARM, [3, 4], arm)
    o.band(ARM, [5], arm_trim)
    o.band(LEG, range(6, 12), boot)
    o.band(LEG, [6], boot_trim)
    o.band(LEG, [7], dark)
    o.fill(LEG, ["bottom"], "K")
    o.put(LEG, "front", 1, 9, gem)
    o.put(LEG, "front", 2, 10, toe)
    i.band(LEG, range(0, 9), inner)
    i.fill(LEG, ["top"], inner)
    stamp(i, LEG, "front", [inner + plate + plate + inner, inner * 4, inner + dark * 2 + inner], v0=3)
    i.band(BODY, range(7, 12), inner)
    i.band(BODY, [7], dark)
    i.put(BODY, "front", 3, 7, glint)
    i.put(BODY, "front", 4, 7, glint)


def _sides(o, ch, rows, hem="D"):
    o.fill(BODY, SIDES, ch, rows=range(0, rows))
    o.band(BODY, [8], hem, faces=("right", "left"))


def blightforged_layers(m):
    o, i = Sheet(), Sheet()
    o.fill(HEAD, ["top"], "P")
    o.tweak_face(HEAD, "top", rim)
    for u, v in ((1, 1), (6, 1), (3, 5), (4, 6)):
        o.put(HEAD, "top", u, v, "B")
    o.band(HEAD, range(0, 3), "P")
    stamp(o, HEAD, "front", ["PPPPPPPP", "PAPPPPAP", "PPPDDPPP", "DDDDDDDD", "P.A..A.P", "P......P"])
    flanks(o, HEAD, ["PPPPPPPP", "PPAPPAPP", "PPPPPPPP", "DDDDDDDD", "P.P.P.P.", "....PPPP", "....PPPP", "....DDDD"])
    stamp(o, HEAD, "back", ["PPPPPPPP", "PPAPPAPP", "PPPAAPPP", "DDDDDDDD", "PPPPPPPP", "PPPAAPPP", "PPPAAPPP", "DPDPDPDP"])
    _sides(o, "P", 10)
    stamp(o, BODY, "front", ["PPPDDPPP", "PADPPDAP", "PPAPPAPP", "PPPABAPP", "PPPPAPPP", "PPPPAPPP", "PPPDDPPP", "PDPPPPDP",
                             "DDDPPDDD", "CCCCCCCC"])
    stamp(o, BODY, "back", ["PPPPPPPP", "PADPPDAP", "PPPAAPPP", "PPAPPAPP", "PPPAAPPP", "PPPPPPPP", "PPPDDPPP", "PDPPPPDP",
                            "DDDDDDDD", "PPPPPPPP"])
    _limbs(o, i, "B", "P", "D", "P", "D", "B", "A", "C", "P", "D", "A")
    return o.render(m), i.render(m)


def cinderplate_layers(m):
    o, i = Sheet(), Sheet()
    o.fill(HEAD, ["top"], "P")
    o.tweak_face(HEAD, "top", rim)
    for u, v in ((3, 2), (4, 3), (2, 5), (5, 6)):
        o.put(HEAD, "top", u, v, "A")
    o.band(HEAD, range(0, 3), "P")
    stamp(o, HEAD, "front", ["PPPPPPPP", "PPPAPPPP", "PPPPAPPP", "DDDDDDDD", "PB....BP", "P......P"])
    flanks(o, HEAD, ["PPPPPPPP", "PPPAPPPP", "PPPPPAPP", "DDDDDDDD", "P.P.P.P.", "....PPPP", "....PCCC", "....DDDD"])
    stamp(o, HEAD, "back", ["PPPPPPPP", "PPAPPPPP", "PPPPPAPP", "DDDDDDDD", "CCCCCCCC", "CCCCCCCC", "CCCACCCC", "CDCDCDCD"])
    _sides(o, "P", 10, "A")
    stamp(o, BODY, "front", ["PPPDDPPP", "PDPPPPDP", "PPAPPPAP", "PPPAAPPP", "PBPPAPBP", "PPAPPAPP", "PPPAPPPP", "PDPPPAPP",
                             "DDDPPDDD", "CCCCCCCC"])
    stamp(o, BODY, "back", ["PPPPPPPP", "PDPPPPDP", "PPPAPPPP", "PPPPAPPP", "PAPPPPAP", "PPPAPPPP", "PPPPAPPP", "PPPPPPPP",
                            "DDDDDDDD", "CDCCDCCD"])
    _limbs(o, i, "D", "P", "A", "P", "D", "A", "B", "C", "P", "D", "A")
    return o.render(m), i.render(m)


def rimeguard_layers(m):
    o, i = Sheet(), Sheet()
    o.fill(HEAD, ["top"], "P")
    o.tweak_face(HEAD, "top", rim)
    for u, v in ((1, 1), (6, 1), (3, 3), (4, 4)):
        o.put(HEAD, "top", u, v, "A")
    o.band(HEAD, range(0, 3), "P")
    stamp(o, HEAD, "front", ["PPPPPPPP", "PPPAAPPP", "PPPPPPPP", "CCCCCCCC", "CD....DC", "C......C"])
    flanks(o, HEAD, ["PPPPPPPP", "PPPAAPPP", "PPPPPPPP", "CCCCCCCC", "CCCCCCCC", "....CCCC", "....CCCC", "....CCCC"])
    stamp(o, HEAD, "back", ["PPPPPPPP", "PPPAAPPP", "PPPPPPPP", "CCCCCCCC", "CCCCCCCC", "CCCCCCCC", "CCCCCCCC", "CCCCCCCC"])
    _sides(o, "P", 10, "C")
    stamp(o, BODY, "front", ["CCCCCCCC", "CPPDDPPC", "PPPABAPP", "PPAPPAPP", "PPPAAPPP", "PPPPPPPP", "PDPAAPDP", "PPPPPPPP",
                             "CCCCCCCC", "CCCCCCCC"])
    stamp(o, BODY, "back", ["CCCCCCCC", "CPPDDPPC", "PPPAAPPP", "PPPPPPPP", "PPPAAPPP", "PPPPPPPP", "PPDDDDPP", "PPPPPPPP",
                            "CCCCCCCC", "CCCCCCCC"])
    _limbs(o, i, "C", "P", "A", "P", "C", "A", "B", "P", "D", "C", "A")
    return o.render(m), i.render(m)


def mirewoven_layers(m):
    o, i = Sheet(), Sheet()
    o.fill(HEAD, ["top"], "P")
    o.tweak_face(HEAD, "top", rim)
    o.band(HEAD, range(0, 3), "P")
    for v in (1, 3, 5):
        o.fill(HEAD, ["top"], "C", rows=[v])
    stamp(o, HEAD, "front", ["PCPCPCPC", "CPCPCPCP", "PCPCPCPC", "DDDDDDDD", "PA....AP", "P......P"])
    flanks(o, HEAD, ["PCPCPCPC", "CPCPCPCP", "PCPCPCPC", "DDDDDDDD", "P.P.P.P.", "....PPPP", "....PAPP", "....DDDD"])
    stamp(o, HEAD, "back", ["PCPCPCPC", "CPCPCPCP", "PCPCPCPC", "DDDDDDDD", "PPPPPPPP", "PPPAAPPP", "PPPAAPPP", "DPDPDPDP"])
    _sides(o, "P", 10)
    stamp(o, BODY, "front", ["CPCPPCPC", "PCPCCPCP", "CPCDDCPC", "PCPABPCP", "CPCDDCPC", "PCPCCPCP", "CPCPPCPC", "PCPCCPCP",
                             "DDDDDDDD", "CCPCCPCC"])
    stamp(o, BODY, "back", ["CPCPPCPC", "PCPCCPCP", "CPCPPCPC", "PCPACPCP", "CPCPPCPC", "PCPCCPCP", "CPCPPCPC", "PCPCCPCP",
                            "DDDDDDDD", "CCPCCPCC"])
    _limbs(o, i, "C", "P", "D", "P", "C", "A", "A", "P", "C", "D", "A")
    return o.render(m), i.render(m)


def arachne_carapace_layers(m):
    o, i = Sheet(), Sheet()
    o.fill(HEAD, ["top"], "P")
    o.tweak_face(HEAD, "top", rim)
    o.band(HEAD, range(0, 3), "P")
    for u in range(1, 7, 2):
        o.put(HEAD, "top", u, 3, "A")
    stamp(o, HEAD, "front", ["PPPPPPPP", "PCPPPPCP", "PPPDDPPP", "DDDDDDDD", "PA.AA.AP", "P..AA..P"])
    flanks(o, HEAD, ["PPPPPPPP", "PPCPPCPP", "PPPPPPPP", "DDDDDDDD", "P.P.P.P.", "....PPPP", "....PPPP", "....DDDD"])
    stamp(o, HEAD, "back", ["PPPPPPPP", "PCPPPPCP", "PPPDDPPP", "DDDDDDDD", "PPPAAPPP", "PPPPAPPP", "PPPAAPPP", "DPDPDPDP"])
    _sides(o, "P", 10)
    stamp(o, BODY, "front", ["CPPDDPPC", "PCPPPPCP", "PPPAAPPP", "PPPPAPPP", "PPPPAPPP", "PPPAAPPP", "PPPDDPPP", "PCPPPPCP",
                             "DDDPPDDD", "PPPPPPPP"])
    stamp(o, BODY, "back", ["CPPDDPPC", "PCPPPPCP", "PPPAAPPP", "PPPPAPPP", "PPPPAPPP", "PPPAAPPP", "PPPDDPPP", "PCPPPPCP",
                            "DDDDDDDD", "PDPPDPPD"])
    _limbs(o, i, "C", "P", "D", "P", "D", "A", "A", "P", "C", "D", "A")
    return o.render(m), i.render(m)


def tide_regalia_layers(m):
    o, i = Sheet(), Sheet()
    o.fill(HEAD, ["top"], "P")
    o.tweak_face(HEAD, "top", rim)
    for u, v in ((2, 2), (5, 2), (3, 4), (4, 5)):
        o.put(HEAD, "top", u, v, "A")
    o.band(HEAD, range(0, 3), "P")
    stamp(o, HEAD, "front", ["PDPDPDPD", "DPDPDPDP", "PPPCCPPP", "AAAAAAAA", "PB....BP", "P......P"])
    flanks(o, HEAD, ["PDPDPDPD", "DPDPDPDP", "PPCPPCPP", "AAAAAAAA", "P.P.P.P.", "....PDPD", "....DPDP", "....AAAA"])
    stamp(o, HEAD, "back", ["PDPDPDPD", "DPDPDPDP", "PDPDPDPD", "AAAAAAAA", "DPDPDPDP", "PDPDPDPD", "DPDPDPDP", "AAAAAAAA"])
    _sides(o, "P", 10, "A")
    stamp(o, BODY, "front", ["APPCCPPA", "PDPDPDPD", "DPDPBPDP", "PDPBBBPD", "DPDPBPDP", "PDPDPDPD", "DPDPDPDP", "PDPDPDPD",
                             "AAAAAAAA", "CPCPCPCP"])
    stamp(o, BODY, "back", ["APPPPPPA", "PDPDPDPD", "DPDPDPDP", "PDPDPDPD", "DPDPDPDP", "PDPDPDPD", "DPDPDPDP", "PDPDPDPD",
                            "AAAAAAAA", "DPDPDPDP"])
    _limbs(o, i, "A", "P", "A", "P", "A", "B", "B", "D", "P", "A", "B")
    return o.render(m), i.render(m)


# Blightforged: purple-grey plate with crimson veins and violet crystal shards.
_BF_PLATE = Material(["120c18", "241a2e", "3a2e46", "54476a", "74688c"], texture=sheen)
_BF = {"P": _BF_PLATE.shifted(texture=polish), "D": _BF_PLATE.shifted(base=1, texture=flat),
       "K": _BF_PLATE.shifted(base=0, texture=flat), "A": CRIMSON.shifted(base=3, texture=flat),
       "B": BLIGHT_STEEL.shifted(base=4, texture=flat), "C": DUSK_METAL.shifted(base=1, texture=cloth)}
_BF_ICON = {**_BF, "P": _BF_PLATE.shifted(texture=flat), "C": DUSK_METAL.shifted(base=1, texture=flat)}
# Cinderplate: blackened iron, glowing ember cracks, ash-grey cloth.
_ASH_CLOTH = Material(["2a2828", "46423f", "666059", "88807a", "aea6a0"], texture=cloth)
_CP = {"P": ASH_STEEL.shifted(base=1, texture=polish), "D": ASH_STEEL.shifted(base=0, texture=flat),
       "K": ASH_STEEL.shifted(base=0, texture=flat), "A": EMBERITE.shifted(base=3, texture=flat),
       "B": EMBERITE.shifted(base=4, texture=flat), "C": _ASH_CLOTH}
_CP_ICON = {**_CP, "P": ASH_STEEL.shifted(base=1, texture=flat), "C": _ASH_CLOTH.shifted(texture=flat)}
# Rimeguard: pale icy steel, frost crystals, blue-white fur.
_FROST_FUR = Material(["7a96b8", "a6c0dc", "cde0f2", "e8f3fb", "ffffff"], texture=speckle)
_RG = {"P": RIME_STEEL.shifted(base=2, texture=polish), "D": FROST_STEEL.shifted(base=1, texture=flat),
       "K": FROST_STEEL.shifted(base=0, texture=flat), "A": ICE.shifted(base=3, texture=flat),
       "B": HOARFROST.shifted(base=3, texture=flat), "C": _FROST_FUR}
_RG_ICON = {**_RG, "P": RIME_STEEL.shifted(base=2, texture=flat)}
# Mirewoven: mossy green leather, woven reeds, toxic green accents.
_BOG_LEATHER = Material(["15220f", "263a1a", "3c5628", "5a7a38", "7ea04c"], texture=stitch)
_REED = Material(["4a4a1c", "7a7a2c", "a8a646", "d0cd72", "eeeca2"], texture=weave)
_MW = {"P": _BOG_LEATHER, "D": _BOG_LEATHER.shifted(base=0, texture=flat), "K": _BOG_LEATHER.shifted(base=0, texture=flat),
       "A": VENOM.shifted(base=3, texture=flat), "B": VENOM.shifted(base=4, texture=flat), "C": _REED}
_MW_ICON = {**_MW, "P": _BOG_LEATHER.shifted(texture=flat), "C": _REED.shifted(texture=flat)}
# Carapace of Arachne: glossy black-green chitin with red hourglass marks.
_CHITIN = Material(["010806", "061410", "0e2a1e", "1a4a34", "2e7050"], texture=sheen)
_AC = {"P": _CHITIN.shifted(base=2, texture=polish), "D": _CHITIN.shifted(base=1, texture=flat),
       "K": _CHITIN.shifted(base=0, texture=flat), "A": CRIMSON.shifted(base=3, texture=flat),
       "B": CRIMSON.shifted(base=4, texture=flat), "C": CHITIN_GREEN.shifted(base=3, texture=flat)}
_AC_ICON = {**_AC, "P": _CHITIN.shifted(base=2, texture=flat)}
# Tidemother Regalia: prismarine and gold scales, coral and pearl.
_PEARL = Material(["8a8aa4", "b6b6cc", "dcdcec", "f4f4fc", "ffffff"], base=3)
_TR = {"P": PRISMARINE.shifted(base=2, texture=plates), "D": PRISMARINE.shifted(base=1, texture=plates),
       "K": PRISMARINE.shifted(base=0, texture=flat), "A": GOLD.shifted(texture=polish),
       "B": _PEARL, "C": CORAL.shifted(base=3, texture=flat)}
_TR_ICON = {**_TR, "P": PRISMARINE.shifted(base=2, texture=flat), "D": PRISMARINE.shifted(base=1, texture=flat),
            "A": GOLD.shifted(texture=flat)}

# Runeforged (The Deep, the Dwarven Halls): deepslate plate banded in brass, glowing cyan runes, a braided iron beard guard.
def runeforged_layers(m):
    o, i = Sheet(), Sheet()
    o.fill(HEAD, ["top"], "P")
    o.tweak_face(HEAD, "top", rim)
    for u, v in ((3, 1), (4, 1), (3, 5), (4, 5)):
        o.put(HEAD, "top", u, v, "B")
    o.band(HEAD, range(0, 3), "P")
    stamp(o, HEAD, "front", ["PPPPPPPP", "PABBBBAP", "PPPAAPPP", "AAAAAAAA", "PA....AP", "PCCCCCCP", "CCDCCDCC", "CDCCCCDC"])
    flanks(o, HEAD, ["PPPPPPPP", "PPBBBBPP", "PPPPPPPP", "AAAAAAAA", "P.P.P.P.", "....CCCC", "....CDCC", "....CCDD"])
    stamp(o, HEAD, "back", ["PPPPPPPP", "PPBBBBPP", "PPPAAPPP", "AAAAAAAA", "PPPPPPPP", "PPPABAPP", "PPPAAPPP", "DPDPDPDP"])
    _sides(o, "P", 10, "A")
    stamp(o, BODY, "front", ["APPDDPPA", "PDPPPPDP", "PPPBBPPP", "PPBPPBPP", "PPPBBPPP", "PPPPPPPP", "PDPAAPDP", "PPPPPPPP",
                             "AAAAAAAA", "CCDCCDCC"])
    stamp(o, BODY, "back", ["PPPPPPPP", "PDPPPPDP", "PPBBBBPP", "PPPBBPPP", "PPPBBPPP", "PPBBBBPP", "PDPPPPDP", "PPPPPPPP",
                            "AAAAAAAA", "PPPPPPPP"])
    _limbs(o, i, "A", "P", "A", "P", "A", "B", "B", "P", "P", "D", "B")
    return o.render(m), i.render(m)


_RUNE_SLATE = Material(["0a0c12", "161a24", "262c3a", "3a4254", "566078"], texture=sheen)
_RF = {"P": _RUNE_SLATE.shifted(texture=polish), "D": _RUNE_SLATE.shifted(base=1, texture=flat),
       "K": _RUNE_SLATE.shifted(base=0, texture=flat), "A": BRASS.shifted(texture=polish),
       "B": CYAN_GLOW.shifted(texture=flat), "C": DARK_IRON.shifted(base=3, texture=weave)}
_RF_ICON = {**_RF, "P": _RUNE_SLATE.shifted(texture=flat), "A": BRASS.shifted(texture=flat), "C": DARK_IRON.shifted(base=3, texture=flat)}

# (worn layers, icon materials, worn materials)
NEW_ARMOR_SETS = {
    "blightforged": (blightforged_layers, _BF_ICON, _BF),
    "cinderplate": (cinderplate_layers, _CP_ICON, _CP),
    "rimeguard": (rimeguard_layers, _RG_ICON, _RG),
    "mirewoven": (mirewoven_layers, _MW_ICON, _MW),
    "arachne_carapace": (arachne_carapace_layers, _AC_ICON, _AC),
    "tide_regalia": (tide_regalia_layers, _TR_ICON, _TR),
    "runeforged": (runeforged_layers, _RF_ICON, _RF),
}

# Icons (16x16, left half of a mirror-symmetric sprite), helmet / chestplate / leggings / boots.
# Blightforged: crystal horns on the helm and crystal pauldrons, crimson veins.
icon("blightforged_helmet", _BF_ICON, half(
    1, "..B...BA", ".BB..BPA", "..PPPPPP", "..PPPPAP", "..PPAPPP", "..DDDDDD", "..PPA...", "..PPD...", "..PD....", "..P.D..."))
icon("blightforged_chestplate", _BF_ICON, half(
    2, "BB.PPDDD", "BBPPPPPD", ".PPPPAAP", ".PP.PAPP", ".PD.PPAB", ".DD.PPAP", "....PPPP", "....DDDA", "....PPPP", "....PDPD"))
icon("blightforged_leggings", _BF_ICON, half(
    2, "...DDDDA", "...PPPPP", "...PAPPP", "...PPPP.", "...PPAP.", "...PPPP.", "...PPAP.", "...PPPP.", "...PPPP.", "...D.DD."))
icon("blightforged_boots", _BF_ICON, half(
    4, "...PPPP.", "...DDDD.", "...PAPP.", "...PPPP.", "...PPAP.", ".BPPPPP.", ".BPPPPP.", ".KKKKKK."))
# Cinderplate: blackened plate cracked with ember, ash-cloth tabard.
icon("cinderplate_helmet", _CP_ICON, half(
    1, "......PP", ".....PPA", "....PPPP", "...PPAPP", "...PPPAP", "...DDDDD", "...PB...", "...PD...", "..PPA...", "..CC.C.."))
icon("cinderplate_chestplate", _CP_ICON, half(
    2, "..DDPDDD", ".DPPPPPD", ".PPAPPAP", ".PP.PAPP", ".PD.PBPP", ".AD.PPAP", "....CCCC", "....DDDD", "....CCCC", "....CDCD"))
icon("cinderplate_leggings", _CP_ICON, half(
    2, "...DDDDD", "...PPAPP", "...PPPPP", "...PPPA.", "...PAPP.", "...PPPP.", "...PPAP.", "...PPPP.", "...PPPP.", "...D.DD."))
icon("cinderplate_boots", _CP_ICON, half(
    4, "...CCCC.", "...PPPP.", "...PAPP.", "...PPPP.", "...PPAP.", ".PPPPPP.", ".PPPPPB.", ".KKKKKK."))
# Rimeguard: pale steel, frost-crystal crest, fur-lined collar and pauldrons.
icon("rimeguard_helmet", _RG_ICON, half(
    1, ".....ABB", "....AAPP", "....PPPP", "...PPPPP", "...PPPPP", "...CCCCC", "...CD...", "...CC...", "..CCC...", "..CC.C.."))
icon("rimeguard_chestplate", _RG_ICON, half(
    2, ".CCCCCCC", "CCPPPDDC", "CCPPPABP", ".CP.PAAP", ".CP.PPPP", ".DP.PDAP", "....PPPP", "....CCCC", "....CCCC", "....CCCC"))
icon("rimeguard_leggings", _RG_ICON, half(
    2, "...DDDDD", "...PPPPP", "...PPAPP", "...PPPP.", "...PADP.", "...PPPP.", "...PPPP.", "...PPAP.", "...CCCC.", "...C.CC."))
icon("rimeguard_boots", _RG_ICON, half(
    4, "...CCCC.", "...CCCC.", "...PAPP.", "...PPPP.", "...PPPP.", ".CPPPPP.", ".CCPPPP.", ".KKKKKK."))
# Mirewoven: reed-woven leather with toxic green glints.
icon("mirewoven_helmet", _MW_ICON, half(
    1, "...C.C.C", "..CPCPCP", "..PCPCPC", "..CPCPCP", "..PPPPPP", "..DDDDDD", "..PA....", "..PP....", "..PD....", "..P.D..."))
icon("mirewoven_chestplate", _MW_ICON, half(
    2, "..CCPDDD", ".CPCPCPD", ".PCPCCPC", ".PC.CPPA", ".CP.PCPB", ".DD.CPCP", "....PCPC", "....DDDD", "....CPCC", "....PCPC"))
icon("mirewoven_leggings", _MW_ICON, half(
    2, "...DDDDD", "...PCPCP", "...CPCPC", "...PCPC.", "...CPAP.", "...PCPC.", "...CPCP.", "...PCPC.", "...CPCP.", "...D.DD."))
icon("mirewoven_boots", _MW_ICON, half(
    4, "...PCPC.", "...CCCC.", "...PCPC.", "...PAPC.", "...CPCP.", ".PPCPCP.", ".PPPPPP.", ".KKKKKK."))
# Carapace of Arachne: glossy chitin, spiked pauldrons and a red hourglass.
icon("arachne_carapace_helmet", _AC_ICON, half(
    1, "..C...CC", ".CP..CPP", "..PPPPPP", "..PPPPPP", "..PCPPPP", "..DDDDDD", "..PAA...", "..PA....", "..PPD...", "..P.P..."))
icon("arachne_carapace_chestplate", _AC_ICON, half(
    2, "CC.PPDDD", "CPPPPPPD", ".PPPPPAA", ".PP.PPPA", ".PD.PPPA", ".DD.PPAA", "....PPPP", "....DDDD", "....PPDP", "....PDPD"))
icon("arachne_carapace_leggings", _AC_ICON, half(
    2, "...DDDDD", "...PPPAA", "...PPPPA", "...PPPP.", "...PCPP.", "...PPPP.", "...PPCP.", "...PPPP.", "...PPPP.", "...D.DD."))
icon("arachne_carapace_boots", _AC_ICON, half(
    4, "...PPPP.", "...DDDD.", "...PPPP.", "...PAPP.", "...PPPP.", ".CPPPPP.", ".CCPPPP.", ".KKKKKK."))
# Tidemother Regalia: prismarine-and-gold scale mail, coral crown, pearl at the heart.
icon("tide_regalia_helmet", _TR_ICON, half(
    1, "...C.CCC", "..CCCCCC", "..AAAAAA", "..PDPDPD", "..DPDPDP", "..AAAAAA", "..PB....", "..PD....", "..PD....", "..A.A..."))
icon("tide_regalia_chestplate", _TR_ICON, half(
    2, "AAA.PPAA", "APDPDPDA", ".DPDPDBB", ".PD.PBBB", ".DP.DPBB", ".AA.PDPD", "....DPDP", "....AAAA", "....PDPD", "....DPDP"))
icon("tide_regalia_leggings", _TR_ICON, half(
    2, "...AAAAA", "...PDPDP", "...DPDPD", "...PDPD.", "...DPBP.", "...PDPD.", "...DPDP.", "...PDPD.", "...DPDP.", "...A.AA."))
icon("tide_regalia_boots", _TR_ICON, half(
    4, "...AAAA.", "...PDPD.", "...DPDP.", "...PBPD.", "...DPDP.", ".CAPDPD.", ".CAAAAA.", ".KKKKKK."))
# Runeforged: a horned deepslate helm with cyan brow runes and a braided iron beard guard, brass-banded plate, a rune in the breast.
icon("runeforged_helmet", _RF_ICON, half(
    1, "AA.....B", ".AA.PPPP", ".APPPPPP", "..PPBBPP", "..PPPPPP", "..AAAAAA", "..PA....", "..PCCCCC", "..CCDCDC", "..CD.D.D"))
icon("runeforged_chestplate", _RF_ICON, half(
    2, "AA.PPDDD", "AAPPPPPD", ".PPPPBBP", ".PP.PBPP", ".PD.PPBP", ".DD.PPPB", "....PPPP", "....AAAA", "....PPPP", "....CDCD"))
icon("runeforged_leggings", _RF_ICON, half(
    2, "...AAAAA", "...PPPPP", "...PBPPP", "...PPPP.", "...PPBP.", "...PPPP.", "...PBPP.", "...PPPP.", "...PPPP.", "...D.DD."))
icon("runeforged_boots", _RF_ICON, half(
    4, "...PPPP.", "...AAAA.", "...PPPP.", "...PBPP.", "...PPPP.", ".APPPPP.", ".AAPPPP.", ".KKKKKK."))


# Thane's Hammer: a slab of runed deepslate on a long haft, brass-capped, a cyan rune burning in the face. Drawn on a
# 45 degree grid: s runs up the haft, t across it.
def _thanes_hammer():
    rows = []
    for y in range(16):
        row = ""
        for x in range(16):
            s, t = x - y, x + y
            ch = "."
            if -12 <= s <= 2 and t in (15, 16):
                ch = "A" if s <= -11 or s in (-5, -4) else "L" if s % 4 == 0 else "W"
            if 3 <= s <= 8 and 10 <= t <= 21:
                ch = "E" if s == 8 else "D" if s == 3 else "S"
                if t <= 11 or t >= 20:
                    ch = "H" if s == 8 else "A"
                if 5 <= s <= 6 and 14 <= t <= 17:
                    ch = "B"
            row += ch
        rows.append(row)
    return rows


_TH_SLATE = _RUNE_SLATE.shifted(texture=flat)
icon("thanes_hammer", {"S": _TH_SLATE.shifted(base=2), "E": _TH_SLATE.shifted(base=3), "D": _TH_SLATE.shifted(base=1),
                       "A": BRASS.shifted(texture=flat), "H": BRASS.shifted(base=4, texture=flat),
                       "B": CYAN_GLOW.shifted(texture=flat), "W": OAK.shifted(texture=flat),
                       "L": OAK.shifted(base=1, texture=flat)}, _thanes_hammer())


# ---- the Nether (docs/design/nether.md)
# Hellforged (Hellforged Ingots and the Magma Tyrant's core): black iron plate split by glowing molten seams, a crown of
# horns, spiked pauldrons and a magma-cracked breastplate.
_HELL_IRON = Material(["070708", "121114", "201d21", "332e33", "4e4649"], texture=sheen)
_FORGE_RED = Material(["2a0806", "4c120a", "741e10", "a0321a", "cc5424"], texture=sheen)


def hellforged_layers(m):
    o, i = Sheet(), Sheet()
    o.fill(HEAD, ["top"], "P")
    o.tweak_face(HEAD, "top", rim)
    stamp(o, HEAD, "top", ["A..AA..A", "......B.", ".....B..", "..BBB...", ".B..B...", "....B...", "...B....", "A..AA..A"])
    o.band(HEAD, range(0, 3), "P")
    stamp(o, HEAD, "front", ["APAPPAPA", "PPPPPBPP", "PPPPBPPP", "DDDDDDDD", "PB....BP", "PPPDDPPP", "PDPBBPDP", "DPPPPPPD"])
    flanks(o, HEAD, ["APPAPPAP", "PPBPPPPP", "PPPBPPPP", "DDDDDDDD", "P.P.P.P.", "....PPPP", "....PBPP", "....DDDD"])
    stamp(o, HEAD, "back", ["PAPPAPPA", "PPPBPPPP", "PPBPPPPP", "DDDDDDDD", "PPPPPPPP", "PPPBPPPP", "PPPPBPPP", "DPDPDPDP"])
    _sides(o, "P", 10, "A")
    # The breastplate cracked by the Tyrant's heat: a jagged seam from the right shoulder down to the left hip, branching.
    stamp(o, BODY, "front", ["APPDDPPA", "PDPPPPBP", "PPPPPBPP", "PBBPBPPP", "PPPBBPPP", "PPPPBPBP", "PDPBPPPB", "PPBPPPDP",
                             "AAAAAAAA", "CCDCCDCC"])
    stamp(o, BODY, "back", ["PPPPPPPP", "PDPPPPDP", "PPPBPPPP", "PPBBPPPP", "PPPPBPPP", "PPPPPBPP", "PDPPPPDP", "PPPPPPPP",
                            "AAAAAAAA", "PPPPPPPP"])
    _limbs(o, i, "A", "P", "D", "P", "A", "B", "B", "P", "B", "D", "B")
    return o.render(m), i.render(m)


_HF = {"P": _HELL_IRON.shifted(texture=polish), "D": _HELL_IRON.shifted(base=1, texture=flat),
       "K": _HELL_IRON.shifted(base=0, texture=flat), "A": _FORGE_RED.shifted(texture=polish),
       "B": EMBERITE.shifted(texture=flat), "C": ASH_STEEL.shifted(base=2, texture=weave)}
_HF_ICON = {**_HF, "P": _HELL_IRON.shifted(base=3, texture=flat), "D": _HELL_IRON.shifted(base=2, texture=flat),
            "A": _FORGE_RED.shifted(texture=flat), "C": ASH_STEEL.shifted(base=3, texture=flat)}
NEW_ARMOR_SETS["hellforged"] = (hellforged_layers, _HF_ICON, _HF)

# Hellforged icons: a black iron helm crowned with forge-red spikes between two big horns, a glowing visor; spiked
# pauldrons over a magma-cracked breastplate.
icon("hellforged_helmet", _HF_ICON, half(
    0, "B.......", "PB.....A", "PP...A.A", ".PP..AAA", "..PPAAAA", "..PPPPPP", "..PPPPBP", "..PPPBPP", "..DDDDDD", "..PBBBBB",
    "..PPPDPP", "..PDPDPD", "..PPPPPP", "...DPDPD"))
icon("hellforged_chestplate", _HF_ICON, half(
    1, "B..B....", "PB.P....", "PAAA.DDD", "AAAAPPPD", ".AAPPPBP", ".PP.PBPP", ".PD.PPBB", ".DD.PBPP", "....BPPP", "....AAAA",
    "....PPPP", "....CDCD"))
icon("hellforged_leggings", _HF_ICON, half(
    2, "...AAAAA", "...PPPPP", "...PBPPP", "...PPBP.", "...PPPP.", "...PBPP.", "...PPPP.", "...PPBP.", "...PPPP.", "...D.DD."))
icon("hellforged_boots", _HF_ICON, half(
    4, "...AAAA.", "...PPPP.", "...PBPP.", "...PPPP.", "...PPBP.", ".BPPPPP.", ".PAPPPP.", ".KKKKKK."))

# ---- slayer armour (docs/design/slayers.md): Gravewarden (Grave Tyrant), Silkstalker (Broodmother), Marshal's Bonemail
# (Bone Marshal) and Voidwalker (Void Stalker). Letters as above: P plate, D dark, K darkest, A accent, B glow, C trim / cloth.
_GRAVESTONE = Material(["1c241e", "323e36", "4a5a50", "6a7c70", "90a496"], texture=speckle)
_GW_STEEL = Material(["080c09", "141d16", "24352a", "3a5242", "56705e"], texture=sheen)
_GW = {"P": _GW_STEEL.shifted(texture=polish), "D": _GW_STEEL.shifted(base=1, texture=flat), "K": _GW_STEEL.shifted(base=0, texture=flat),
       "A": _GRAVESTONE.shifted(texture=polish), "B": PLAGUE.shifted(texture=flat), "C": GRAVE_CLOTH.shifted(texture=weave)}
_GW_ICON = {**_GW, "P": _GW_STEEL.shifted(base=3, texture=flat), "D": _GW_STEEL.shifted(base=2, texture=flat),
            "A": _GRAVESTONE.shifted(texture=flat), "C": GRAVE_CLOTH.shifted(texture=flat)}


def gravewarden_layers(m):
    o, i = Sheet(), Sheet()
    o.fill(HEAD, ["top"], "P")
    o.tweak_face(HEAD, "top", rim)
    stamp(o, HEAD, "top", ["A..AA..A", "........", "..B..B..", "...BB...", "...BB...", "..B..B..", "........", "A..AA..A"])
    o.band(HEAD, range(0, 3), "P")
    stamp(o, HEAD, "front", ["APPAAPPA", "PPPPPPPP", "PPBPPBPP", "DDDDDDDD", "P.B..B.P", "PPPDDPPP", "PCPPPPCP", "DPPCCPPD"])
    flanks(o, HEAD, ["APPAPPAP", "PPPPPPPP", "PPPPBPPP", "DDDDDDDD", "P.P.P.P.", "....PCPC", "....PPCP", "....DDDD"])
    stamp(o, HEAD, "back", ["PAAPPAAP", "PPPPPPPP", "PPPBBPPP", "DDDDDDDD", "PPPPPPPP", "PCPPPPCP", "PPCPPCPP", "DPDPDPDP"])
    _sides(o, "P", 10, "C")
    # a gravestone cross lit plague-green on the breast, burial cloth hanging from the belt, a cracked slab on the back
    stamp(o, BODY, "front", ["APPDDPPA", "PDPPPPDP", "PPPPBPPP", "PPPBBBPP", "PPPPBPPP", "PPPPBPPP", "PDPPPPDP", "PCPPPPCP",
                             "AAAAAAAA", "CCDCCDCC"])
    stamp(o, BODY, "back", ["PPPPPPPP", "PDAAAADP", "PPAPPAPP", "PPABBAPP", "PPAPBAPP", "PPAAAAPP", "PDPPPPDP", "PPPPPPPP",
                            "AAAAAAAA", "CDCCDCCD"])
    _limbs(o, i, "A", "P", "D", "P", "A", "B", "B", "C", "P", "D", "B")
    return o.render(m), i.render(m)


_SILK = Material(["6a6a74", "9a9aa8", "c4c4d2", "e6e6f0", "ffffff"], texture=weave)
_CHITIN_DK = Material(["080606", "161010", "2a1e18", "423226", "62503a"], texture=sheen)
_SS = {"P": _CHITIN_DK.shifted(texture=polish), "D": _CHITIN_DK.shifted(base=1, texture=flat), "K": _CHITIN_DK.shifted(base=0, texture=flat),
       "A": _SILK.shifted(texture=weave), "B": VENOM.shifted(texture=flat), "C": _SILK.shifted(base=2, texture=weave)}
_SS_ICON = {**_SS, "P": _CHITIN_DK.shifted(base=3, texture=flat), "D": _CHITIN_DK.shifted(base=2, texture=flat),
            "A": _SILK.shifted(texture=flat), "C": _SILK.shifted(base=2, texture=flat)}


def silkstalker_layers(m):
    o, i = Sheet(), Sheet()
    o.fill(HEAD, ["top"], "P")
    o.tweak_face(HEAD, "top", rim)
    stamp(o, HEAD, "top", ["A..AA..A", ".A.AA.A.", "..AAAA..", "AAAAAAAA", "AAAAAAAA", "..AAAA..", ".A.AA.A.", "A..AA..A"])
    o.band(HEAD, range(0, 3), "P")
    stamp(o, HEAD, "front", ["PPPPPPPP", "PAAPPAAP", "PBAPPABP", "DDDDDDDD", "PBB..BBP", "PPPCCPPP", "PCPPPPCP", "DPPPPPPD"])
    flanks(o, HEAD, ["PPPPPPPP", "PAAPPAAP", "PPPBPPPP", "DDDDDDDD", "P.P.P.P.", "....PCPC", "....PPCP", "....DDDD"])
    stamp(o, HEAD, "back", ["PPPPPPPP", "PAAPPAAP", "PPAAAAPP", "DDDDDDDD", "PPPAAPPP", "PPAAAAPP", "PPPAAPPP", "DPDPDPDP"])
    _sides(o, "P", 10, "A")
    # chitin plates under a silk wrap, a venom-green hourglass, spider-leg ribs
    stamp(o, BODY, "front", ["APPDDPPA", "PDCCCCDP", "PPCBBCPP", "PPPCBPPP", "PPPBBPPP", "PPCBBCPP", "PDCCCCDP", "PPPPPPPP",
                             "AAAAAAAA", "CCDCCDCC"])
    stamp(o, BODY, "back", ["PPPPPPPP", "PDPPPPDP", "PPCCCCPP", "PPCDDCPP", "PPPCCPPP", "PPPDDPPP", "PPCCCCPP", "PDPPPPDP",
                            "AAAAAAAA", "PPPPPPPP"])
    _limbs(o, i, "P", "P", "A", "P", "A", "B", "B", "C", "P", "D", "B")
    return o.render(m), i.render(m)


_BONE = Material(["5e5640", "968a6c", "c8bc9c", "e8e0c4", "fffcec"], texture=sheen)
_BM = {"P": _BONE.shifted(texture=polish), "D": _BONE.shifted(base=1, texture=flat), "K": _BONE.shifted(base=0, texture=flat),
       "A": CRIMSON.shifted(texture=cloth), "B": MARROW.shifted(texture=flat), "C": DREAD_BONE.shifted(base=2, texture=flat)}
_BM_ICON = {**_BM, "P": _BONE.shifted(base=3, texture=flat), "D": _BONE.shifted(base=2, texture=flat),
            "A": CRIMSON.shifted(texture=flat), "C": DREAD_BONE.shifted(base=2, texture=flat)}


def marshals_bonemail_layers(m):
    o, i = Sheet(), Sheet()
    o.fill(HEAD, ["top"], "P")
    o.tweak_face(HEAD, "top", rim)
    stamp(o, HEAD, "top", ["........", "...AA...", "...AA...", "...AA...", "...AA...", "...AA...", "...AA...", "........"])
    o.band(HEAD, range(0, 3), "P")
    stamp(o, HEAD, "front", ["PPPAAPPP", "PPPAAPPP", "PDPPPPDP", "DDDDDDDD", "PCC..CCP", "PP.CC.PP", "PDPPPPDP", "DPDPPDPD"])
    flanks(o, HEAD, ["PPPAAPPP", "PPPAAPPP", "PPPPPPPP", "DDDDDDDD", "P.P.P.P.", "....PDPD", "....PPDP", "....DDDD"])
    stamp(o, HEAD, "back", ["PPPAAPPP", "PPPAAPPP", "PDPPPPDP", "DDDDDDDD", "PPPPPPPP", "PDPPPPDP", "PPDPPDPP", "DPDPDPDP"])
    _sides(o, "P", 10, "C")
    # bone plate over a rib cage with the Marshal's sigil lit red, a banner cloth tassel down each side of the belt
    stamp(o, BODY, "front", ["PPPDDPPP", "PDPPPPDP", "PCCDDCCP", "PPCCCCPP", "PCCDDCCP", "PPCCCCPP", "PDPCCPDP", "PPPBBPPP",
                             "AAAAAAAA", "ACDCCDCA"])
    stamp(o, BODY, "back", ["PPPPPPPP", "PDPPPPDP", "PPPDDPPP", "PPPDDPPP", "PPCDDCPP", "PPPDDPPP", "PPCDDCPP", "PDPPPPDP",
                            "AAAAAAAA", "AAPPPPAA"])
    _limbs(o, i, "P", "P", "D", "P", "A", "B", "B", "A", "P", "D", "B")
    return o.render(m), i.render(m)


_VOID_ROBE = Material(["07040f", "120a24", "1f1238", "321e58", "4a3080"], texture=cloth)
_VOID_PLATE = Material(["0a0612", "180e2a", "2a1a48", "44306e", "6a4aa0"], texture=sheen)
_VW = {"P": _VOID_ROBE.shifted(texture=cloth), "D": _VOID_ROBE.shifted(base=1, texture=flat), "K": _VOID_ROBE.shifted(base=0, texture=flat),
       "A": _VOID_PLATE.shifted(texture=polish), "B": RIFT.shifted(base=2, texture=flat), "C": ELDER.shifted(base=2, texture=flat)}
_VW_ICON = {**_VW, "P": _VOID_ROBE.shifted(base=3, texture=flat), "A": _VOID_PLATE.shifted(base=3, texture=flat)}


def voidwalker_layers(m):
    o, i = Sheet(), Sheet()
    o.fill(HEAD, ["top"], "P")
    o.tweak_face(HEAD, "top", rim)
    stamp(o, HEAD, "top", ["B......B", "........", "..A..A..", "...AA...", "...AA...", "..A..A..", "........", "B......B"])
    o.band(HEAD, range(0, 3), "P")
    stamp(o, HEAD, "front", ["PPPPPPPP", "PPAAAAPP", "PAAPPAAP", "PADDDDAP", "PDBDDBDP", "PDDDDDDP", "PPDDDDPP", "PPPPPPPP"])
    flanks(o, HEAD, ["PPPPPPPP", "PPAAAAPP", "PPPPPPPP", "PADDDDDD", "PDP.PDPD", "....PDPD", "....PPDP", "....DCDC"])
    stamp(o, HEAD, "back", ["PPPPPPPP", "PPAAAAPP", "PPPBBPPP", "PAPBBPAP", "PPPBBPPP", "PPPPPPPP", "PPPBBPPP", "DPDPDPDP"])
    _sides(o, "P", 10, "C")
    # robes split by glowing violet seams, an obsidian-purple breastplate and sash
    stamp(o, BODY, "front", ["PAPBBPAP", "PPABBAPP", "PPPBBPPP", "PAPBBPAP", "PPPBBPPP", "PPPBBPPP", "PPABBAPP", "PPPBBPPP",
                             "AAACCAAA", "PPBBBBPP"])
    stamp(o, BODY, "back", ["PPPPPPPP", "PAPBBPAP", "PPPBBPPP", "PPPBBPPP", "PPPBBPPP", "PPPBBPPP", "PPPBBPPP", "PPPPPPPP",
                            "AAAAAAAA", "PBPPPPBP"])
    _limbs(o, i, "A", "P", "B", "P", "A", "B", "B", "P", "A", "D", "B")
    return o.render(m), i.render(m)


NEW_ARMOR_SETS["gravewarden"] = (gravewarden_layers, _GW_ICON, _GW)
NEW_ARMOR_SETS["silkstalker"] = (silkstalker_layers, _SS_ICON, _SS)
NEW_ARMOR_SETS["marshals_bonemail"] = (marshals_bonemail_layers, _BM_ICON, _BM)
NEW_ARMOR_SETS["voidwalker"] = (voidwalker_layers, _VW_ICON, _VW)


def _slayer_legs_boots(sid, icons, belt="A", glow="B"):
    icon(f"{sid}_leggings", icons, half(
        2, f"...{belt * 5}", "...PPPPP", f"...P{glow}PPP", "...PPDP.", "...PPPP.", f"...PD{glow}P.", "...PPPP.", f"...PP{glow}P.", "...PPPP.", "...D.DD."))
    icon(f"{sid}_boots", icons, half(
        4, f"...{belt}{belt}{belt}{belt}.", "...PPPP.", f"...P{glow}PP.", "...PPPP.", f"...PP{glow}P.", ".DPPPPP.", f".P{belt}PPPP.", ".KKKKKK."))


# Gravewarden: a gravestone-crowned plate helm with plague-green eyes; gravestone pauldrons, a green cross on the breast.
icon("gravewarden_helmet", _GW_ICON, half(
    2, "....AAAA", "...AAPPP", "..APPPPP", "..PPPPPP", "..DDDDDD", "..PBBPPP", "..PPDPDP", "..PDPCPC", "...PPPPP", "...DPDPD"))
icon("gravewarden_chestplate", _GW_ICON, half(
    1, "AAA.....", "APA..DDD", "APPA.PPD", "AAAAPPPD", ".PP.PPPB", ".PD.PPBB", ".DD.PPPB", ".DD.PCPP", "....PPPP", "....AAAA",
    "....PPPP", "....CDCD"))
_slayer_legs_boots("gravewarden", _GW_ICON)

# Silkstalker: a chitin hood ringed with venom-green eyes; spider-leg spikes off silk-bound shoulders.
icon("silkstalker_helmet", _SS_ICON, half(
    2, "...AAPPP", "..APPPPP", "..PPPPPP", "..PBPBPP", "..PPPPPP", "..DDDDDD", "..PBBPPP", "..PPCPCP", "...PCPPP", "...DPDPD"))
icon("silkstalker_chestplate", _SS_ICON, half(
    1, ".D......", "D.D.....", "PPPD.DDD", "PPPPCPCP", ".PPPCCPC", ".PD.PCBB", ".DP.PCCB", ".DD.PPCC", "....BCPP", "....AAAA",
    "....PPPP", "....CDCD"))
_slayer_legs_boots("silkstalker", _SS_ICON)

# Marshal's Bonemail: a skull helm with a red bone crest; bone plate over ribs, banner tassets.
icon("marshals_bonemail_helmet", _BM_ICON, half(
    1, "......AA", "......AA", "...PPPAA", "..PPPPPP", "..PPPPPP", "..DDDDDD", "..PCC.PP", "..PP.CPP", "..PDPDPD", "...PPPPP", "...DPDPD"))
icon("marshals_bonemail_chestplate", _BM_ICON, half(
    1, "PP......", "PPP..DDD", "PPPP.PPD", ".PPP.PCC", ".PP.PCCP", ".PD.PPCC", ".DD.PCCP", ".DD.PPPB", "....PPPB", "....AAAA",
    "....PPPP", "....ACDC"))
_slayer_legs_boots("marshals_bonemail", _BM_ICON, belt="A", glow="C")

# Voidwalker: a deep cowl with two violet eyes, shards floating off the shoulders, glowing seams down the robe.
icon("voidwalker_helmet", _VW_ICON, half(
    0, "B.......", "........", "....PPPP", "...PPPPP", "..PPAAAA", "..PPPPPP", "..PADDDD", "..PDBBDD", "..PDDDDD", "..PPDDDD", "...PPPPP",
    "...DPPDP"))
icon("voidwalker_chestplate", _VW_ICON, half(
    1, "B..B....", ".B......", "AAA.....", "AAAAPBBB", ".AA.PBBB", ".PP.PBBB", ".PD.PPBB", ".DD.PPBB", "....PPBB", "....AAAC",
    "....PBBB", "....PBBB"))
_slayer_legs_boots("voidwalker", _VW_ICON, belt="A", glow="B")


# Magmaline Rod (Tier V, fishes in lava): a black iron shaft wrapped in molten line, a forge-red grip, the Tyrant's core
# glowing in the reel and an ember at the tip.
rod_icon("magmaline_rod", {"S": _HELL_IRON.shifted(base=3, texture=flat), "W": EMBERITE.shifted(texture=flat),
                           "H": _FORGE_RED.shifted(base=2), "G": EMBERITE.shifted(base=4, texture=flat), "L": EMBER,
                           "B": _FORGE_RED.shifted(base=3), "R": _HELL_IRON.shifted(base=2, texture=flat),
                           "P": EMBERITE.shifted(base=4, texture=flat), "A": EMBERITE.shifted(base=3, texture=flat)},
         wraps=(2, 5, 8, 11), reel=((5, 11, "R"), (6, 11, "R"), (7, 11, "R"), (5, 12, "R"), (6, 12, "P"), (7, 12, "R"),
                                    (5, 13, "R"), (6, 13, "R"), (7, 13, "R")),
         extra=((14, 0, "G"), (13, 1, "G"), (12, 2, "A")))


ARMOR_SETS = {"harvester": harvester_layers, "prospector": prospector_layers,
              "woodsman": woodsman_layers, "vanguard": vanguard_layers}
for set_name, (layers, _, worn_mats) in {**MINING_SETS, **FARMING_SETS, **FORAGING_SETS, **COMBAT_SETS, **FISHING_SETS, **DUNGEON_SETS, **NEW_ARMOR_SETS}.items():
    ARMOR_SETS[set_name] = (lambda layers=layers, worn=worn_mats: layers(worn))


# ---------------------------------------------------------------- stat icons (8x8, greyscale)
# '.' empty, '1'..'5' grey levels (dark to white). Text colour multiplies them, so white = full stat colour.
GREYS = {"1": 70, "2": 110, "3": 160, "4": 205, "5": 255}
STAT_ICONS = [
    ("health", [
        ".22.22..",
        "2552442.",
        "2555442.",
        "2554432.",
        ".25443..",
        "..243...",
        "...2....",
        "........",
    ]),
    ("defense", [
        "2222222.",
        "2554332.",
        "2554332.",
        "2554332.",
        ".25432..",
        "..242...",
        "...2....",
        "........",
    ]),
    ("strength", [  # fist
        ".22222..",
        "2525252.",
        "2525252.",
        "2444442.",
        "2555552.",
        ".24442..",
        "..222...",
        "........",
    ]),
    ("crit_chance", [  # target
        "..222...",
        ".25552..",
        "2522252.",
        "2525252.",
        "2522252.",
        ".25552..",
        "..222...",
        "........",
    ]),
    ("crit_damage", [  # skull
        ".22222..",
        "2555552.",
        "2115112.",
        "2115112.",
        "2552552.",
        ".25552..",
        ".25252..",
        "........",
    ]),
    ("speed", [  # winged boot
        "...2222.",
        "2..2552.",
        "52.2552.",
        "2522552.",
        ".2255542",
        "..255552",
        "..222222",
        "........",
    ]),
    ("intelligence", [  # orb on a stand
        "..222...",
        ".25542..",
        "2554432.",
        "2544332.",
        ".24332..",
        "..222...",
        ".22222..",
        "........",
    ]),
    ("mining_speed", [  # pickaxe
        "..2222..",
        ".255552.",
        "..22.252",
        "...252.2",
        "..252.2.",
        ".252....",
        "252.....",
        ".2......",
    ]),
    ("mining_fortune", [  # gem and a plus
        "......2.",
        ".....252",
        ".2222.2.",
        "255552..",
        "2545442.",
        ".24442..",
        "..242...",
        "...2....",
    ]),
    ("farming_fortune", [  # wheat ear and a plus
        "......2.",
        "..2..252",
        ".252..2.",
        "25452...",
        ".25452..",
        "..2542..",
        "...2.2..",
        "....2...",
    ]),
    ("foraging_fortune", [  # log end and a plus
        "......2.",
        ".....252",
        "..222.2.",
        ".25552..",
        "2532352.",
        "2523252.",
        "2532352.",
        ".22222..",
    ]),
    ("magic_find", [  # eye with a glint
        "......2.",
        "..222252",
        ".255552.",
        "25511552",
        ".255552.",
        "..2222..",
        "........",
        "........",
    ]),
    ("farming_insight", [  # sprout
        ".....22.",
        "....2552",
        ".22.252.",
        "2552.2..",
        ".2252...",
        "...42...",
        "...4....",
        ".22422..",
    ]),
    ("mining_insight", [  # oil lamp
        "....2...",
        "...252..",
        "...242..",
        "222222..",
        "2555552.",
        ".2555222",
        "..2222..",
        "........",
    ]),
    ("foraging_insight", [  # leaf
        ".....22.",
        "...22552",
        "..255542",
        ".2554552",
        ".254552.",
        ".24552..",
        "2.222...",
        "........",
    ]),
    ("combat_insight", [  # crossed blades
        "2.....2.",
        "52...25.",
        ".52.25..",
        "..252...",
        ".25.52..",
        "252..252",
        ".2....2.",
        "........",
    ]),
    ("damage", [  # blade
        "......22",
        ".....252",
        "....2552",
        "...2552.",
        "2.2552..",
        ".2552...",
        ".22.....",
        "2..2....",
    ]),
    ("mana", [  # droplet
        "...2....",
        "..252...",
        ".25552..",
        ".25542..",
        "2555442.",
        "2554432.",
        ".22222..",
        "........",
    ]),
    ("coins", [
        "..222...",
        ".25542..",
        "2543432.",
        "2545332.",
        "2543332.",
        ".23332..",
        "..222...",
        "........",
    ]),
    ("level", [  # star
        "...2....",
        "..252...",
        "2225222.",
        "2555552.",
        ".25552..",
        ".25252..",
        ".2..2...",
        "........",
    ]),

    ("fishing_speed", [  # fish hook
        "....22..",
        "....52..",
        "....52..",
        "....52..",
        "2...52..",
        "52..52..",
        ".2555...",
        "..22....",
    ]),
    ("sea_creature_chance", [  # a fin above a wave
        "...2....",
        "..252...",
        ".2552...",
        "25552...",
        "........",
        ".22..22.",
        "2552255.",
        "22.552..",
    ]),
    ("fishing_fortune", [  # a fish
        "........",
        "..222..2",
        ".255522.",
        "25555552",
        "2545552.",
        ".255522.",
        "..222..2",
        "........",
    ]),
    ("fishing_insight", [  # a lantern on a line
        "...2....",
        "...2....",
        "..222...",
        ".25552..",
        ".25452..",
        ".25552..",
        "..222...",
        "........",
    ]),
]
STAT_COLORS = {  # only for previews; the game tints with StatType colours
    "health": 0xFF5555, "defense": 0x55FF55, "strength": 0xE5534B, "crit_chance": 0x5C8DFF,
    "crit_damage": 0x5C8DFF, "speed": 0xFFFFFF, "intelligence": 0x55FFFF, "mining_speed": 0xFFAA00,
    "mining_fortune": 0xFFAA00, "farming_fortune": 0xFFAA00, "foraging_fortune": 0xFFAA00,
    "magic_find": 0x55FFFF, "farming_insight": 0x3FD0D4, "mining_insight": 0x3FD0D4,
    "foraging_insight": 0x3FD0D4, "combat_insight": 0x3FD0D4, "damage": 0xFF5555, "mana": 0x55FFFF,
    "coins": 0xFFAA00, "level": 0xFFFF55, "fishing_speed": 0x55FFFF, "sea_creature_chance": 0x3FA9F5,
    "fishing_fortune": 0xFFAA00, "fishing_insight": 0x3FD0D4,
}
FIRST_CODEPOINT = 0xE100


def stat_icon(rows):
    assert len(rows) == 8 and all(len(r) == 8 for r in rows), rows
    img = Image.new("RGBA", (8, 8), (0, 0, 0, 0))
    for y, row in enumerate(rows):
        for x, ch in enumerate(row):
            if ch != ".":
                g = GREYS[ch]
                img.putpixel((x, y), (g, g, g, 255))
    return img


def stat_sheet():
    """16 icons per row; the font provider's char grid matches."""
    cols = 16
    rows = (len(STAT_ICONS) + cols - 1) // cols
    sheet = Image.new("RGBA", (cols * 8, rows * 8), (0, 0, 0, 0))
    chars = []
    for r in range(rows):
        line = ""
        for c in range(cols):
            i = r * cols + c
            if i < len(STAT_ICONS):
                sheet.paste(stat_icon(STAT_ICONS[i][1]), (c * 8, r * 8))
                line += chr(FIRST_CODEPOINT + i)
            else:
                line += "\u0000"
        chars.append(line)
    return sheet, chars


# ---------------------------------------------------------------- output
HANDHELD = {"harvester_hoe", "prospector_pickaxe", "woodsman_axe", "vanguard_blade", "riftblade", "mending_wand",
            "quarry_pick", "delver_pickaxe", "skyiron_pickaxe", "duskbore_pick", "emberheart_pickaxe", "lumen_breaker",
            "thornfield_sickle", "sunreaper_scythe", "frostbite_scythe", "cinder_scythe", "moonlit_reaper", "reapers_scythe", "soulreaper",
            "ironbark_hatchet", "mistpine_splitter", "willowcleaver", "emberbrand_axe", "starwood_greataxe",
            "fangsteel_sabre", "stormedge", "cinderfang", "starfall_blade",
            "gravebane", "broodfang", "marrowblade", "voidrender", "voidreaver",
            "cryptbreaker", "arachne_fang", "tidecaller_blade",
            "blightreaver", "cinderbrand", "rimecleaver", "mirefang", "thanes_hammer"}


def write_json(path, obj):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(obj, indent=2) + "\n", encoding="utf-8", newline="\n")


def build():
    icons = {}
    for name, (mask, mats, tweak) in ICONS.items():
        img = render_rod(mask, mats, tweak) if name in ROD_CAST else render(mask, mats, tweak)
        icons[name] = img
        out = ITEM_ASSETS / "textures" / "item" / f"{name}.png"
        out.parent.mkdir(parents=True, exist_ok=True)
        img.save(out)
        if name in ROD_CAST:
            # A vanilla FishingRodItem: the cast model shows while the line is out, like vanilla's fishing_rod.json.
            cast_mask, cast_mats, cast_tweak = ROD_CAST[name]
            render_rod(cast_mask, cast_mats, cast_tweak).save(ITEM_ASSETS / "textures" / "item" / f"{name}_cast.png")
            for model in (name, f"{name}_cast"):
                write_json(ITEM_ASSETS / "models" / "item" / f"{model}.json",
                           {"parent": "minecraft:item/handheld_rod", "textures": {"layer0": f"skyrpg_items:item/{model}"}})
            write_json(ITEM_ASSETS / "items" / f"{name}.json",
                       {"model": {"type": "minecraft:condition", "property": "minecraft:fishing_rod/cast",
                                  "on_true": {"type": "minecraft:model", "model": f"skyrpg_items:item/{name}_cast"},
                                  "on_false": {"type": "minecraft:model", "model": f"skyrpg_items:item/{name}"}}})
            continue
        write_json(ITEM_ASSETS / "items" / f"{name}.json",
                   {"model": {"type": "minecraft:model", "model": f"skyrpg_items:item/{name}"}})
        write_json(ITEM_ASSETS / "models" / "item" / f"{name}.json",
                   {"parent": "minecraft:item/handheld" if name in HANDHELD else "minecraft:item/generated",
                    "textures": {"layer0": f"skyrpg_items:item/{name}"}})

    layers = {}
    for set_id, paint in ARMOR_SETS.items():
        outer, inner = paint()
        layers[set_id] = (outer, inner)
        for layer, img in (("humanoid", outer), ("humanoid_leggings", inner)):
            out = ITEM_ASSETS / "textures" / "entity" / "equipment" / layer / f"{set_id}.png"
            out.parent.mkdir(parents=True, exist_ok=True)
            img.save(out)
        texture = f"skyrpg_items:{set_id}"
        write_json(ITEM_ASSETS / "equipment" / f"{set_id}.json",
                   {"layers": {"humanoid": [{"texture": texture}], "humanoid_leggings": [{"texture": texture}]}})

    write_json(ITEM_ASSETS / "lang" / "en_us.json", lang_names())

    sheet, chars = stat_sheet()
    out = CORE_ASSETS / "skyrpg_core" / "textures" / "font" / "stat_icons.png"
    out.parent.mkdir(parents=True, exist_ok=True)
    sheet.save(out)
    write_json(CORE_ASSETS / "minecraft" / "font" / "default.json",
               {"providers": [{"type": "bitmap", "file": "skyrpg_core:font/stat_icons.png",
                               "height": 8, "ascent": 7, "chars": chars}]})
    return icons, layers


def lang_names():
    """Item names from their definitions: the items drawn here in ICONS order, then the rest sorted."""
    lang = {"itemGroup.skyrpg_items": "SkyRPG", "key.skyrpg_items.backpack": "Open Backpack", "block.skyrpg_items.artisan_bench": "Artisan Bench"}
    others = sorted(path.stem for path in ITEM_DATA.glob("*.json") if path.stem not in ICONS)
    for name in list(ICONS) + others:
        definition = json.loads((ITEM_DATA / f"{name}.json").read_text(encoding="utf-8"))
        lang[f"item.skyrpg_items.{name}"] = definition["name"]
    return lang


# ---------------------------------------------------------------- previews
BG = (24, 28, 38)
TILE = (36, 42, 56)
LABEL = (200, 206, 220)


def checker(w, h, cell=8):
    img = Image.new("RGBA", (w, h))
    d = ImageDraw.Draw(img)
    for y in range(0, h, cell):
        for x in range(0, w, cell):
            d.rectangle([x, y, x + cell - 1, y + cell - 1], fill=(58, 64, 78) if (x // cell + y // cell) % 2 else (48, 54, 66))
    return img


def big(img, f):
    return img.resize((img.width * f, img.height * f), Image.NEAREST)


def tinted(img, color):
    r, g, b = (color >> 16) & 255, (color >> 8) & 255, color & 255
    out = Image.new("RGBA", img.size, (0, 0, 0, 0))
    for y in range(img.height):
        for x in range(img.width):
            p = img.getpixel((x, y))
            if p[3]:
                out.putpixel((x, y), (p[0] * r // 255, p[1] * g // 255, p[2] * b // 255, 255))
    return out


def preview_items(icons, path):
    names = list(icons)
    cols, cell, pad, stat_cols = 8, 128, 24, 10
    rows = (len(names) + cols - 1) // cols
    stat_rows = (len(STAT_ICONS) + stat_cols - 1) // stat_cols
    width = max(cols * (cell + pad) + pad, len(names) * (16 * 3 + 6) + 2 * pad)
    height = rows * (cell + pad + 14) + pad + 40 + 16 * 3 + 20 + stat_rows * (64 + 30) + pad
    img = Image.new("RGBA", (width, height), BG + (255,))
    d = ImageDraw.Draw(img)
    for i, name in enumerate(names):
        x = pad + (i % cols) * (cell + pad)
        y = pad + (i // cols) * (cell + pad + 14)
        img.paste(checker(cell, cell, 16), (x, y))
        tile = big(icons[name], 8)
        img.alpha_composite(tile, (x, y))
        d.text((x, y + cell + 2), name, fill=LABEL)
    # In-game scale: a hotbar row at 2x and 3x on a slot colour.
    y0 = pad + rows * (cell + pad + 14)
    for f, y in ((2, y0), (3, y0 + 40)):
        x = pad
        for name in names:
            d.rectangle([x, y, x + 16 * f + 3, y + 16 * f + 3], fill=(20, 24, 33))
            img.alpha_composite(big(icons[name], f), (x + 2, y + 2))
            x += 16 * f + 6
    # Stat icons: white at 8x, then tinted at 3x on a tooltip colour.
    y = y0 + 40 + 16 * 3 + 20
    for i, (name, rows_) in enumerate(STAT_ICONS):
        x = pad + (i % stat_cols) * (64 + 60)
        yy = y + (i // stat_cols) * (64 + 30)
        icon_img = stat_icon(rows_)
        img.paste(checker(64, 64, 8), (x, yy))
        img.alpha_composite(big(icon_img, 8), (x, yy))
        img.alpha_composite(big(tinted(icon_img, STAT_COLORS[name]), 3), (x + 68, yy + 8))
        img.alpha_composite(big(tinted(icon_img, STAT_COLORS[name]), 2), (x + 68, yy + 40))
        d.text((x, yy + 66), f"{FIRST_CODEPOINT + i:X} {name}", fill=LABEL)
    img.save(path)


def figure(outer, inner, back=False):
    """Front (or back) view of a wearer: head, body, arms and legs faces laid out flat."""
    face = "back" if back else "front"
    skin = Image.new("RGBA", (16, 32), (0, 0, 0, 0))
    d = ImageDraw.Draw(skin)
    d.rectangle([4, 0, 11, 7], fill=(196, 150, 120, 255))
    d.rectangle([4, 8, 11, 19], fill=(70, 90, 130, 255))
    d.rectangle([0, 8, 3, 19], fill=(196, 150, 120, 255))
    d.rectangle([12, 8, 15, 19], fill=(196, 150, 120, 255))
    d.rectangle([4, 20, 11, 31], fill=(60, 60, 90, 255))
    if not back:
        d.point([(5, 4), (6, 4), (9, 4), (10, 4)], fill=(60, 40, 120, 255))

    def crop(img, b, name, mirror=False):
        x, y, w, h = b[name]
        part = img.crop((x, y, x + w, y + h))
        return part.transpose(Image.FLIP_LEFT_RIGHT) if mirror else part

    for layer in (inner, outer):
        skin.alpha_composite(crop(layer, HEAD, face), (4, 0))
        skin.alpha_composite(crop(layer, BODY, face), (4, 8))
        skin.alpha_composite(crop(layer, ARM, face), (0, 8))
        skin.alpha_composite(crop(layer, ARM, face, mirror=True), (12, 8))
        skin.alpha_composite(crop(layer, LEG, face), (4, 20))
        skin.alpha_composite(crop(layer, LEG, face, mirror=True), (8, 20))
    return skin


def preview_armor(layers, icons, directory):
    for set_id, (outer, inner) in layers.items():
        img = Image.new("RGBA", (64 * 8 * 2 + 72, 32 * 8 + 32 * 8 + 80), BG + (255,))
        d = ImageDraw.Draw(img)
        for i, (label, layer) in enumerate((("humanoid", outer), ("humanoid_leggings", inner))):
            x = 24 + i * (64 * 8 + 24)
            img.paste(checker(64 * 8, 32 * 8, 8), (x, 24))
            img.alpha_composite(big(layer, 8), (x, 24))
            d.text((x, 8), f"{set_id} {label}", fill=LABEL)
        y = 32 * 8 + 48
        x = 24
        for back in (False, True):
            fig = big(figure(outer, inner, back), 8)
            img.alpha_composite(fig, (x, y))
            x += fig.width + 24
        for piece in ("helmet", "chestplate", "leggings", "boots"):
            img.alpha_composite(big(icons[f"{set_id}_{piece}"], 6), (x, y))
            x += 16 * 6 + 12
        img.save(directory / f"armor_{set_id}.png")


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--preview", type=Path, help="directory to write contact sheets to")
    args = parser.parse_args()
    icons, layers = build()
    if args.preview:
        args.preview.mkdir(parents=True, exist_ok=True)
        preview_items(icons, args.preview / "items_sheet.png")
        preview_armor(layers, icons, args.preview)
    print(f"wrote {len(icons)} item icons, {len(layers)} armour sets, {len(STAT_ICONS)} stat icons")
