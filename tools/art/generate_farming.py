#!/usr/bin/env python3
"""Generates everything data-like for the farming mod: crop/planter/sprinkler/item textures (pixel art drawn
here, nothing copied from vanilla), blockstates and models, loot tables, tags, recipes and lang.

    python tools/art/generate_farming.py                 # writes the files below
    python tools/art/generate_farming.py --preview DIR   # also writes a labelled 8x contact sheet to DIR

Writes under farming/src/main/resources:
  * assets/skyrpg_farming/textures/block/   crop stages, starbloom stages, planter side/top/bottom, sprinklers
  * assets/skyrpg_farming/textures/item/    16x16 icon for every item
  * assets/skyrpg_farming/{blockstates,models,items,lang}
  * data/skyrpg_farming/{loot_table/blocks,recipe}, data/minecraft/tags/block/*, data/skyrpg_stats/tags/block/*
The block/item lists match FarmingBlocks/FarmingItems.

Sprites: a mask is a list of 16-char rows ('.' empty, short rows are padded). Characters index a legend of
5-shade ramps (see sprite()); icons get a 1px outline in a darkened version of the pixel they surround.
"""
import json
import random
import sys
from pathlib import Path

from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parent.parent.parent
RES = ROOT / "farming" / "src" / "main" / "resources"
NS = "skyrpg_farming"
ASSETS = RES / "assets" / NS
DATA = RES / "data" / NS
MC_TAGS = RES / "data" / "minecraft" / "tags" / "block"

CROPS = ["sunwheat", "frostberry", "emberpepper", "moonmelon"]
PRODUCT = {"sunwheat": "sunwheat_sheaf", "frostberry": "frostberries", "emberpepper": "emberpepper",
           "moonmelon": "moonmelon_slice"}
CROP_NAMES = {"sunwheat": "Sunwheat", "frostberry": "Frostberry Bush", "emberpepper": "Emberpepper Plant",
              "moonmelon": "Moonmelon Vine"}
# planter id -> (crop texture prefix, final stage ref, growing stage ref)
PLANTERS = {
    "wheat_planter": ("minecraft:block/wheat_stage7", "minecraft:block/wheat_stage2", "Wheat Planter"),
    "carrot_planter": ("minecraft:block/carrots_stage3", "minecraft:block/carrots_stage1", "Carrot Planter"),
    "potato_planter": ("minecraft:block/potatoes_stage3", "minecraft:block/potatoes_stage1", "Potato Planter"),
    "sunwheat_planter": (f"{NS}:block/sunwheat_stage3", f"{NS}:block/sunwheat_stage1", "Sunwheat Planter"),
    "frostberry_planter": (f"{NS}:block/frostberry_stage3", f"{NS}:block/frostberry_stage1", "Frostberry Planter"),
    "emberpepper_planter": (f"{NS}:block/emberpepper_stage3", f"{NS}:block/emberpepper_stage1", "Emberpepper Planter"),
    "moonmelon_planter": (f"{NS}:block/moonmelon_stage3", f"{NS}:block/moonmelon_stage1", "Moonmelon Planter"),
    "starbloom_planter": (f"{NS}:block/starbloom_stage3", f"{NS}:block/starbloom_stage1", "Starbloom Planter"),
}
SPRINKLERS = {"copper_sprinkler": "Copper Sprinkler", "skyiron_sprinkler": "Skyiron Sprinkler",
              "emberite_sprinkler": "Emberite Sprinkler"}
ITEMS = {
    "dewdrop": "Dewdrop", "dew_vial": "Dew Vial", "lucky_clover": "Lucky Clover", "verdant_heart": "Verdant Heart",
    "sunwheat_seeds": "Sunwheat Seeds", "frostberry_seeds": "Frostberry Seeds", "emberpepper_seeds": "Emberpepper Seeds",
    "moonmelon_seeds": "Moonmelon Seeds", "starbloom_seed": "Starbloom Seed", "sunwheat_sheaf": "Sunwheat Sheaf",
    "frostberries": "Frostberries", "emberpepper": "Emberpepper", "moonmelon_slice": "Moonmelon Slice",
    "starbloom_petal": "Starbloom Petal", "rich_compost": "Rich Compost", "sunbread": "Sunbread",
    "miners_chili": "Miner's Chili", "frostberry_tart": "Frostberry Tart", "moonmelon_juice": "Moonmelon Juice",
    "starbloom_tea": "Starbloom Tea",
}


def write_json(path, value):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(value, indent=2) + "\n", encoding="utf-8")


# ---------------------------------------------------------------- palettes (5 shades, darkest first)
GREEN = [(22, 52, 28), (36, 94, 42), (58, 140, 54), (100, 188, 70), (160, 226, 108)]
LEAF = [(30, 66, 30), (46, 110, 40), (74, 156, 52), (120, 200, 72), (180, 236, 120)]
GOLD = [(120, 66, 10), (186, 116, 20), (236, 174, 40), (252, 216, 88), (255, 246, 184)]
STRAW = [(110, 80, 24), (170, 128, 40), (214, 172, 66), (240, 208, 108), (255, 240, 170)]
ICE = [(40, 84, 150), (80, 140, 210), (140, 196, 244), (200, 232, 255), (246, 252, 255)]
FROSTLEAF = [(24, 70, 74), (36, 110, 108), (60, 150, 140), (110, 194, 176), (176, 232, 214)]
RED = [(90, 10, 14), (150, 20, 20), (210, 40, 30), (240, 84, 50), (255, 150, 110)]
EMBER = [(130, 40, 8), (210, 84, 16), (250, 140, 30), (255, 196, 70), (255, 240, 160)]
VIOLET = [(34, 20, 74), (62, 40, 120), (100, 66, 168), (146, 110, 210), (200, 176, 244)]
SILVER = [(90, 96, 120), (140, 148, 172), (190, 198, 218), (226, 232, 246), (255, 255, 255)]
PINK = [(130, 40, 100), (210, 80, 150), (246, 140, 190), (255, 206, 228), (255, 250, 252)]
BROWN = [(52, 32, 18), (84, 54, 30), (120, 80, 44), (160, 112, 64), (200, 154, 96)]
SOIL = [(30, 20, 16), (48, 32, 24), (66, 44, 32), (88, 60, 42), (112, 80, 56)]
CYAN = [(20, 92, 150), (36, 150, 210), (80, 206, 240), (170, 238, 255), (255, 255, 255)]
GLASS = [(90, 130, 150), (140, 186, 204), (186, 224, 236), (222, 244, 252), (255, 255, 255)]
CREAM = [(150, 110, 70), (200, 160, 100), (232, 204, 150), (250, 232, 190), (255, 248, 226)]
COPPER = [(100, 50, 28), (170, 90, 40), (220, 128, 62), (246, 174, 100), (255, 218, 160)]
PATINA = [(24, 90, 84), (40, 140, 124), (70, 184, 160), (130, 226, 196), (200, 250, 232)]
SKYMETAL = [(70, 100, 140), (120, 160, 204), (170, 206, 238), (216, 236, 252), (255, 255, 255)]
DARKRED = [(40, 10, 12), (80, 18, 20), (120, 30, 24), (160, 50, 30), (200, 80, 40)]


def sprite(rows, **ramps):
    """Mask -> RGBA with an outline. Legend: '1'-'5' ramp A, 'a'-'e' ramp B, 'p'-'t' ramp C, 'u'-'y' ramp D
    (all darkest to lightest), '#' near-black."""
    legend = {"#": (24, 20, 28)}
    for chars, key in (("12345", "A"), ("abcde", "B"), ("pqrst", "C"), ("uvwxy", "D")):
        if key in ramps:
            for ch, col in zip(chars, ramps[key]):
                legend[ch] = col
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    for y, row in enumerate(rows[:16]):
        assert len(row) <= 16, (y, row)
        for x, ch in enumerate(row):
            if ch != ".":
                img.putpixel((x, y), legend[ch] + (255,))
    return outline(img)


def outline(img):
    out = img.copy()
    for y in range(16):
        for x in range(16):
            if img.getpixel((x, y))[3] == 0:
                for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                    nx, ny = x + dx, y + dy
                    if 0 <= nx < 16 and 0 <= ny < 16 and img.getpixel((nx, ny))[3] > 0:
                        c = img.getpixel((nx, ny))
                        out.putpixel((x, y), (int(c[0] * .3), int(c[1] * .3), int(c[2] * .3), 255))
                        break
    return out


# ---------------------------------------------------------------- item masks
M = {}
M["dewdrop"] = (["................", "................", ".......3........", ".......3........", "......343.......",
                 "......343.......", ".....33433......", "....3544333.....", "....3554333.....", "....3444332.....",
                 "....3344332.....", ".....33332......", "......222.......", "................"], dict(A=CYAN))
M["dew_vial"] = (["................", "......qrq.......", "......pqp.......", "......454.......", "......4a4.......",
                  ".....45a54......", "....455ccb4.....", "....45cccdb4....", "....4ccdddb4....", "....4cddeeb4....",
                  "....4bdddbb4....", ".....4bbbb4.....", "......4444......", "................"],
                 dict(A=GLASS, B=CYAN, C=BROWN))
M["lucky_clover"] = (["................", "...33....33.....", "..3443..3443....", "..34543.34543...", "...3453.3543....",
                      "....3443443.....", "....3445543.....", "...3453.3543....", "..34543.34543...", "..3443..3443....",
                      "...33.2..33.....", "........2.......", ".........2......", "..........2.....", "................"],
                     dict(A=GREEN))
M["verdant_heart"] = (["................", "................", "...4444..4444...", "..455554455554..",
                       "..455555555555..", "..4555aa5555d4..", "..3455bbbbbd4...", "...345bbbbd43...",
                       "....345bbd43....", ".....3455d3.....", "......3453......", ".......33.......", "................"],
                      dict(A=GREEN, B=[(120, 255, 140), (150, 255, 160), (190, 255, 190), (230, 255, 230), (255, 255, 255)]))


def seeds(main, accent):
    return (["................", "................", "................", ".......43.......", "......5543......",
             ".......33.......", "...43.......43..", "..5543.....5543.", "...33...43..33..", ".......5543.....",
             "........33......", "................"], dict(A=main, B=accent))


M["sunwheat_seeds"] = seeds(GOLD, STRAW)
M["frostberry_seeds"] = seeds(ICE, FROSTLEAF)
M["emberpepper_seeds"] = seeds(CREAM, RED)
M["moonmelon_seeds"] = seeds(VIOLET, SILVER)
M["starbloom_seed"] = (["................", "................", ".......3........", ".......4........", "......343.......",
                        "...3445555443...", "....3455554.....", ".....34554......", "....3455543.....", "....345.543.....",
                        "...34.....43....", "................"], dict(A=PINK))
M["sunwheat_sheaf"] = (["................", "...4.....4..4...", "..434...434.43..", "..434..43343.3..", "..343..343.343..",
                        "...3....3.4.3...", "...33.43333.33..", "....3333333333..", "....rrrrrrrr....", "....pqqqqqqp....",
                        "....3433433.....", "...334.334.3....", "..33.4.33.43....", "..3...3.3...3...", "................"],
                       dict(A=GOLD, C=RED))
def _berries():
    g = [["."] * 16 for _ in range(16)]
    shape = [".333.", "34553", "35543", "34443", ".222."]
    for bx, by in ((5, 4), (1, 8), (9, 8)):
        for j, row in enumerate(shape):
            for i, ch in enumerate(row):
                if ch != ".":
                    g[by + j][bx + i] = ch
    for x, y in ((7, 1), (8, 1), (6, 2), (7, 2), (8, 2), (9, 2), (7, 3), (8, 3)):
        g[y][x] = "b" if (x + y) % 2 else "c"
    return ["".join(r) for r in g]


M["frostberries"] = (_berries(), dict(A=ICE, B=FROSTLEAF[2:] + FROSTLEAF[:2]))
M["emberpepper"] = (["................", "..........ab....", ".........abb....", "........ab#.....", "........3443....",
                     ".......345543...", ".......345543...", "......3455443...", "......3455443...", ".....345544332..",
                     ".....34554432...", "....3455443.....", "....344432......", "....3442........", ".....32........."],
                    dict(A=RED, B=GREEN))


def _slice():
    import math
    g = [["."] * 16 for _ in range(16)]
    for y in range(3, 11):
        half = math.sqrt(7.6 ** 2 - (y - 3) ** 2) if y > 3 else 7.6
        for x in range(16):
            d = abs(x - 7.5)
            if d <= half:
                depth = half - d
                bottom = math.sqrt(max(0, 7.6 ** 2 - (y - 3) ** 2)) - d
                edge = min(7.6 - math.hypot(d, y - 3), 9)
                if y == 3:
                    ch = "e"
                elif edge < 1.0:
                    ch = "p"
                elif edge < 2.0:
                    ch = "q"
                else:
                    ch = "d" if (x + y) % 5 else "c"
                g[y][x] = ch
    for x, y in ((4, 5), (8, 5), (11, 5), (6, 7), (9, 7)):
        g[y][x] = "#"
    return ["".join(r) for r in g]


M["moonmelon_slice"] = (_slice(), dict(B=[(120, 86, 176), (170, 140, 220), (206, 186, 240), (232, 220, 252), (250, 246, 255)],
                                       C=VIOLET))
M["starbloom_petal"] = (["................", "........4.......", ".......45.......", "......455.......", ".....4555.......",
                         ".....4555 3.....", "....35554.......", "....35555.......", "....35554.......", ".....3554.......",
                         ".....3344.......", "......33........"], dict(A=PINK))
M["rich_compost"] = (["................", "................", ".........b.d....", "........bab.d...", ".....d.dbb.bd...",
                      "....bdbd.b.d....", ".....1111.......", "....233333......", "...23344333.....", "..2334444433....",
                      "..23444444433...", ".2334445444433..", ".2344444444443..", "..223333333322..", "................"],
                     dict(A=SOIL, B=GREEN))
M["sunbread"] = (["................", "................", "................", "....3444444.....", "...344555544....",
                  "..3445544554 4..", ".344554554554 4.", ".3445544544544..", ".34455445445442.", "..344554554422..",
                  "...3444444422...", "....22222222....", "................"], dict(A=GOLD))
M["miners_chili"] = (["................", "................", "......u.........", ".....xy.....y...", "..4444444444444.",
                      ".4cdcdddeddcddd4", ".4dddcdddddedcc4", ".4444444444444 4", "..3344444444332.",
                      "...2333333332...", "....22222222....", "................"],
                     dict(A=BROWN, B=RED, D=EMBER))
M["frostberry_tart"] = (["................", "................", "................", "....3444444.....", "..334555555443..",
                         ".345bbbbbbbb543.", ".34bbcdcbbdcbb43", ".34bcddcbbcdd b3", ".34bbccbbbbccb43", ".334bbbbbbbbb433",
                         "..33444444444332", "...2233333322...", "................"],
                        dict(A=CREAM, B=ICE))
M["moonmelon_juice"] = (["................", "......qrq.......", "......pqp.......", ".......4........", "......454.......",
                         ".....45454......", "....4bbcbb4.....", "....4bccbb4.....", "....4cddcb4.....", "....4cdddc4.....",
                         "....4bcddb4.....", ".....4bbb4......", "......444.......", "................"],
                        dict(A=GLASS, B=[(60, 34, 120), (96, 62, 170), (140, 100, 210), (190, 160, 244), (240, 226, 255)],
                             C=BROWN))
M["starbloom_tea"] = (["................", "......5.........", ".......5..5.....", "......5..5......", ".......5........",
                       "................", "..444444444.....", ".4cccccccc4.....", ".4cddddddc4444..", ".4bbbbbbb4 4 4..",
                       ".4bbbbbbb4 4 4..", "..4bbbbb4 444...", "...4bbbb4.......", "....44444.......", "................"],
                      dict(A=CREAM, B=PINK))

# Hand-fixes: some masks above contain stray spaces; normalise them to the intended character.
for k, (rows, ramps) in list(M.items()):
    M[k] = ([r.replace(" ", ".") for r in rows], ramps)


# ---------------------------------------------------------------- crop drawing
def canvas():
    return Image.new("RGBA", (16, 16), (0, 0, 0, 0))


def put(img, x, y, col):
    if 0 <= x < 16 and 0 <= y < 16:
        img.putpixel((x, y), tuple(col) + (255,))


def blob(img, rnd, cx, cy, rx, ry, ramp, noise=0.25):
    """Leafy bush: an ellipse lit from the top-left, with ragged edges."""
    for y in range(16):
        for x in range(16):
            dx, dy = (x - cx) / rx, (y - cy) / ry
            d = dx * dx + dy * dy
            if d <= 1.0 + rnd.uniform(-noise, noise) * 0.5:
                lit = 2.2 - (dx + dy) * 1.0 + rnd.uniform(-0.5, 0.5)
                put(img, x, y, ramp[max(0, min(4, int(lit)))])


def berry(img, x, y, ramp, size=2):
    for dy in range(size):
        for dx in range(size):
            put(img, x + dx, y + dy, ramp[2])
    put(img, x, y, ramp[4])
    put(img, x + size - 1, y + size - 1, ramp[1])


def sunwheat(stage):
    img = canvas()
    heights = [3, 6, 10, 14][stage]
    for i, x in enumerate((1, 4, 7, 10, 13)):
        h = heights - (i % 2) - (1 if stage == 3 and i == 2 else 0) + (1 if i == 1 and stage else 0)
        h = max(2, h)
        for k in range(h):
            y = 15 - k
            col = GREEN[2 if k % 2 else 1] if stage < 2 else (STRAW[1] if stage == 2 and k < h - 3 else GREEN[3]) if stage == 2 else STRAW[2 if k % 2 else 1]
            put(img, x, y, col)
        if stage >= 1 and h > 4:
            put(img, x + (1 if i % 2 else -1), 15 - h // 2, GREEN[3] if stage < 3 else STRAW[3])
        if stage >= 2:
            top = 15 - h
            for k in range(4 if stage == 3 else 2):
                put(img, x, top + k, GOLD[2 if k % 2 else 3] if stage == 3 else GREEN[3])
                if stage == 3:
                    put(img, x + 1, top + k + 1, GOLD[3 - (k % 2)])
                    put(img, x - 1, top + k + 1, GOLD[1 + (k % 2)])
            if stage == 3:
                put(img, x, top - 1, GOLD[3])
    if stage == 3:
        for x, y in ((3, 2), (9, 0), (12, 3), (6, 5), (0, 4), (14, 7)):
            put(img, x, y, (255, 250, 210))
    return img


def frostberry(stage):
    rnd = random.Random(31 + stage)
    img = canvas()
    if stage == 0:
        for x in (5, 9):
            put(img, x, 15, FROSTLEAF[2]); put(img, x, 14, FROSTLEAF[3]); put(img, x - 1, 13, FROSTLEAF[3]); put(img, x + 1, 13, FROSTLEAF[4])
        return img
    sizes = {1: (4, 4, 11), 2: (6, 5, 10), 3: (7, 6, 9)}[stage]
    blob(img, rnd, 7.5, 15 - sizes[2] + sizes[1] - 1, sizes[0], sizes[1], FROSTLEAF)
    if stage >= 2:
        for x, y in ([(4, 9), (9, 11), (7, 7)] if stage == 2 else [(3, 8), (6, 6), (10, 8), (4, 11), (8, 10), (11, 11), (7, 13), (12, 5)]):
            berry(img, x, y, ICE)
    if stage == 3:
        for x, y in ((2, 5), (13, 9)):
            put(img, x, y, (230, 250, 255))
    return img


def emberpepper(stage):
    img = canvas()
    h = [3, 7, 11, 13][stage]
    for k in range(h):
        put(img, 7, 15 - k, GREEN[1]); put(img, 8, 15 - k, GREEN[2])
    # leaf pairs up the stem
    levels = {0: [13], 1: [12, 9], 2: [13, 10, 7, 4], 3: [13, 10, 7, 4, 2]}[stage]
    for i, y in enumerate(levels):
        span = 3 if stage > 1 else 2
        for s in (-1, 1):
            xs = 7 if s < 0 else 8
            for k in range(1, span + 1):
                put(img, xs + s * k, y - (k > 1), LEAF[2 + (k % 2)])
            put(img, xs + s * span, y + 1, LEAF[1])
            put(img, xs + s, y - 1, LEAF[3])
    if stage >= 2:
        col = GREEN if stage == 2 else RED
        for x, y, ln in ((4, 8, 3), (10, 5, 3), (12, 9, 3), (3, 12, 3)) if stage == 3 else ((4, 9, 2), (11, 8, 2)):
            for k in range(ln):
                put(img, x + (k > 1), y + k, col[2 if k < ln - 1 else 1])
            put(img, x, y, col[3]); put(img, x, y - 1, GREEN[1])
            put(img, x + 1, y + 1, col[4] if stage == 3 else col[3])
    if stage == 3:
        for x, y in ((2, 7), (6, 3), (13, 3), (14, 12), (1, 11), (9, 12)):
            put(img, x, y, EMBER[2 + (x % 2)])
        for x, y in ((6, 3), (13, 3)):
            put(img, x, y, EMBER[4])
    return img


def melon(img, x, y, size=4):
    """A round violet melon with a lit top-left, shaded bottom-right and silver specks."""
    for dy in range(size):
        for dx in range(size):
            if (dx in (0, size - 1)) and (dy in (0, size - 1)):
                continue
            t = dx + dy
            put(img, x + dx, y + dy, VIOLET[1 if t > size else 2 if t > 1 else 3])
    put(img, x + 1, y, VIOLET[4])
    put(img, x + size - 2, y + size - 2, SILVER[2])
    put(img, x + 1, y + size - 1, SILVER[3])


def moonmelon(stage):
    img = canvas()
    if stage == 0:
        for x in (4, 10):
            put(img, x, 15, GREEN[1]); put(img, x, 14, GREEN[2]); put(img, x - 1, 13, GREEN[3]); put(img, x + 1, 13, GREEN[3])
        return img
    vine = [(1, 14), (2, 14), (3, 13), (4, 13), (5, 14), (6, 14), (7, 15), (8, 15), (9, 14), (10, 14), (11, 13), (12, 13), (13, 14), (14, 14)]
    if stage == 1:
        vine = vine[3:11]
    if stage == 2:
        vine = vine[1:13]
    for x, y in vine:
        put(img, x, y, GREEN[1]); put(img, x, y + 1, GREEN[0])
    for x, y in ([(4, 11), (9, 12)] if stage == 1 else [(2, 10), (6, 9), (11, 10), (9, 12)] if stage == 2 else [(2, 9), (6, 7), (10, 9), (12, 6), (4, 12)]):
        for dx, dy, c in ((0, 0, GREEN[3]), (1, 0, GREEN[2]), (-1, 1, GREEN[2]), (0, 1, GREEN[3]), (1, 1, GREEN[1]), (0, 2, GREEN[1]), (-1, 0, GREEN[2])):
            put(img, x + dx, y + dy, c)
    if stage == 2:
        melon(img, 5, 11, 3)
    if stage == 3:
        melon(img, 3, 11, 5)
        melon(img, 9, 11, 5)
        melon(img, 6, 6, 4)
        for x, y in ((1, 5), (13, 5), (14, 10)):
            put(img, x, y, SILVER[4])
    return img


def star(img, cx, cy, big):
    """A little five-point flower: bright centre, pink arms."""
    arms = [(0, -1), (1, 0), (0, 1), (-1, 0)]
    for dx, dy in arms:
        put(img, cx + dx, cy + dy, PINK[2])
        if big:
            put(img, cx + dx * 2, cy + dy * 2, PINK[1])
    for dx, dy in ((1, 1), (-1, -1), (1, -1), (-1, 1)):
        if big:
            put(img, cx + dx, cy + dy, PINK[3])
    put(img, cx, cy, PINK[4])


def starbloom(stage):
    img = canvas()
    stems = [(3, 9), (8, 6), (12, 10)] if stage == 3 else [(4, 11), (8, 9), (12, 12)]
    for x, top in stems:
        for y in range(top, 16):
            put(img, x, y, GREEN[2 if y % 2 else 1])
        put(img, x - 1, 13 if x < 8 else 12, LEAF[3]); put(img, x + 1, 14, LEAF[2])
        if stage == 3:
            star(img, x, top - 1, True)
        else:
            put(img, x, top - 1, PINK[3]); put(img, x, top, PINK[1])
    if stage == 3:
        for x, y in ((1, 3), (14, 5), (6, 1), (10, 2)):
            put(img, x, y, PINK[4])
    return img


# ---------------------------------------------------------------- block textures
def planks(rnd, w=16, h=16, rows=4):
    img = Image.new("RGBA", (w, h))
    per = h // rows
    for y in range(h):
        for x in range(w):
            r = y // per
            base = [BROWN[3], BROWN[2], BROWN[3], BROWN[2]][r % 4]
            if y % per == per - 1:
                base = BROWN[1]
            elif y % per == 0:
                base = BROWN[4] if rnd.random() < .5 else BROWN[3]
            if rnd.random() < .12:
                base = BROWN[max(0, BROWN.index(base) - 1)] if base in BROWN else base
            if (x + r * 5) % 16 == 0:
                base = BROWN[1]
            img.putpixel((x, y), base + (255,))
    return img


def planter_side():
    return planks(random.Random(7))


def planter_top():
    rnd = random.Random(8)
    img = Image.new("RGBA", (16, 16))
    for y in range(16):
        for x in range(16):
            edge = min(x, y, 15 - x, 15 - y)
            if edge == 0:
                c = BROWN[4] if (x < 8) == (y < 8) else BROWN[3]
            elif edge == 1:
                c = BROWN[2] if rnd.random() > .2 else BROWN[1]
            elif edge == 2:
                c = SOIL[0]
            else:
                c = SOIL[rnd.choice((1, 1, 2, 2, 3))]
                if rnd.random() < .06:
                    c = SOIL[4]
            img.putpixel((x, y), c + (255,))
    return img


def planter_bottom():
    img = planks(random.Random(9))
    return Image.eval(img, lambda v: int(v * .75)) if False else darken(img, .75)


def darken(img, f):
    out = img.copy()
    for y in range(img.height):
        for x in range(img.width):
            r, g, b, a = img.getpixel((x, y))
            out.putpixel((x, y), (int(r * f), int(g * f), int(b * f), a))
    return out


def sprinkler_tex(name, metal, accent, glow, seed):
    """Metal body (texture mapped by block coordinates: plate bottom rows, pole, head), tier accent bands."""
    rnd = random.Random(seed)
    img = Image.new("RGBA", (16, 16))
    for y in range(16):
        for x in range(16):
            v = rnd.random()
            idx = 1 if v < .2 else 2 if v < .75 else 3
            if x in (0, 15) or y in (0, 15):
                idx = 1
            if (x + y) % 7 == 0 and rnd.random() < .5:
                idx = 4
            img.putpixel((x, y), metal[idx] + (255,))
    for y in (5, 6, 13, 14, 15):  # head / plate bands in the accent colour
        for x in range(16):
            img.putpixel((x, y), accent[2 if y in (5, 14) else 1] + (255,))
    for y in (7, 8, 9, 10, 11, 12):
        for x in range(16):
            if x % 4 == 0:
                img.putpixel((x, y), accent[0 if x % 8 == 0 else 1] + (255,))
    for x, y in ((6, 6), (9, 6), (7, 10), (10, 3)):
        img.putpixel((x, y), glow + (255,))
    return img


SPRINKLER_ART = {
    "copper_sprinkler": (COPPER, PATINA, (150, 255, 220)),
    "skyiron_sprinkler": (SKYMETAL, ICE, (255, 255, 255)),
    "emberite_sprinkler": (DARKRED, EMBER, (255, 214, 90)),
}


# ---------------------------------------------------------------- models / data
def crop_planes(y0, y1, tex):
    def el(frm, to, faces):
        return {"from": frm, "to": to, "shade_direction_override": "up", "faces": faces}

    def f(uv):
        return {"uv": uv, "texture": tex}
    return [
        el([4, y0, 0], [4, y1, 16], {"west": f([0, 0, 16, 16]), "east": f([16, 0, 0, 16])}),
        el([12, y0, 0], [12, y1, 16], {"west": f([16, 0, 0, 16]), "east": f([0, 0, 16, 16])}),
        el([0, y0, 4], [16, y1, 4], {"north": f([0, 0, 16, 16]), "south": f([16, 0, 0, 16])}),
        el([0, y0, 12], [16, y1, 12], {"north": f([16, 0, 0, 16]), "south": f([0, 0, 16, 16])}),
    ]


def planter_template():
    box = {"from": [0, 0, 0], "to": [16, 10, 16], "faces": {
        "north": {"uv": [0, 0, 16, 10], "texture": "#side"}, "south": {"uv": [0, 0, 16, 10], "texture": "#side"},
        "east": {"uv": [0, 0, 16, 10], "texture": "#side"}, "west": {"uv": [0, 0, 16, 10], "texture": "#side"},
        "up": {"uv": [0, 0, 16, 16], "texture": "#top"}, "down": {"uv": [0, 0, 16, 16], "texture": "#bottom", "cullface": "down"}}}
    return {"ambientocclusion": False, "textures": {"particle": "#side"}, "elements": [box] + crop_planes(9, 25, "#crop")}


def sprinkler_model(name):
    tex = f"{NS}:block/{name}"

    def box(a, b):
        return {"from": a, "to": b, "faces": {d: {"texture": "#t"} for d in ("north", "south", "east", "west", "up", "down")}}
    els = [box([4, 0, 4], [12, 2, 12]), box([7, 2, 7], [9, 9, 9]), box([5, 9, 5], [11, 11, 11]),
           box([4, 9.5, 7.5], [5, 10.5, 8.5]), box([11, 9.5, 7.5], [12, 10.5, 8.5]),
           box([7.5, 9.5, 4], [8.5, 10.5, 5]), box([7.5, 9.5, 11], [8.5, 10.5, 12])]
    return {"textures": {"t": tex, "particle": tex}, "elements": els}


def loot_self(block):
    return {"type": "minecraft:block", "pools": [{"rolls": 1, "condition": {"type": "minecraft:survives_explosion"},
            "entries": [{"type": "minecraft:item", "name": f"{NS}:{block}"}]}], "random_sequence": f"{NS}:blocks/{block}"}


def loot_crop(crop):
    ripe = {"type": "minecraft:match_block", "blocks": f"{NS}:{crop}", "state": {"age": "3"}}
    product = {"type": "minecraft:item", "condition": ripe, "name": f"{NS}:{PRODUCT[crop]}"}
    if crop == "moonmelon":
        product["modifier"] = [{"type": "minecraft:set_count", "count": 2}]
    seeds = f"{NS}:{crop}_seeds"
    return {"type": "minecraft:block", "modifier": {"type": "minecraft:explosion_decay"}, "pools": [
        {"entries": [{"type": "minecraft:alternatives", "children": [product, {"type": "minecraft:item", "name": seeds}]}], "rolls": 1},
        {"condition": ripe, "entries": [{"type": "minecraft:item", "modifier": {
            "type": "minecraft:apply_bonus", "enchantment": "minecraft:fortune", "formula": "minecraft:binomial_with_bonus_count",
            "parameters": {"extra": 1, "probability": 0.5}}, "name": seeds}], "rolls": 1}],
        "random_sequence": f"{NS}:blocks/{crop}"}


def item_files(name, tex):
    tex.save(ASSETS / "textures" / "item" / f"{name}.png")
    write_json(ASSETS / "models" / "item" / f"{name}.json", {"parent": "minecraft:item/generated", "textures": {"layer0": f"{NS}:item/{name}"}})
    write_json(ASSETS / "items" / f"{name}.json", {"model": {"type": "minecraft:model", "model": f"{NS}:item/{name}"}})


def block_item(name):
    write_json(ASSETS / "items" / f"{name}.json", {"model": {"type": "minecraft:model", "model": f"{NS}:block/{name}"}})


def main():
    bt = ASSETS / "textures" / "block"
    bt.mkdir(parents=True, exist_ok=True)
    (ASSETS / "textures" / "item").mkdir(parents=True, exist_ok=True)
    lang = {"itemGroup.skyrpg_farming": "SkyRPG Farming"}
    sheet_items, sheet_blocks = [], []

    # Crops.
    drawers = {"sunwheat": sunwheat, "frostberry": frostberry, "emberpepper": emberpepper, "moonmelon": moonmelon}
    for crop in CROPS:
        variants = {}
        for n in range(4):
            tex = drawers[crop](n)
            tex.save(bt / f"{crop}_stage{n}.png")
            sheet_blocks.append((f"{crop} {n}", tex))
            write_json(ASSETS / "models" / "block" / f"{crop}_stage{n}.json",
                       {"parent": "minecraft:block/crop", "textures": {"crop": f"{NS}:block/{crop}_stage{n}"}})
            variants[f"age={n}"] = {"model": f"{NS}:block/{crop}_stage{n}"}
        write_json(ASSETS / "blockstates" / f"{crop}.json", {"variants": variants})
        write_json(DATA / "loot_table" / "blocks" / f"{crop}.json", loot_crop(crop))
        lang[f"block.{NS}.{crop}"] = CROP_NAMES[crop]
    for n in (1, 3):
        tex = starbloom(n)
        tex.save(bt / f"starbloom_stage{n}.png")
        sheet_blocks.append((f"starbloom {n}", tex))

    # Planters.
    for name, tex in (("planter_side", planter_side()), ("planter_top", planter_top()), ("planter_bottom", planter_bottom())):
        tex.save(bt / f"{name}.png")
        sheet_blocks.append((name, tex))
    write_json(ASSETS / "models" / "block" / "planter_template.json", planter_template())
    for pid, (ripe, growing, label) in PLANTERS.items():
        for suffix, crop in (("", ripe), ("_growing", growing)):
            write_json(ASSETS / "models" / "block" / f"{pid}{suffix}.json", {"parent": f"{NS}:block/planter_template", "textures": {
                "side": f"{NS}:block/planter_side", "top": f"{NS}:block/planter_top", "bottom": f"{NS}:block/planter_bottom", "crop": crop}})
        write_json(ASSETS / "blockstates" / f"{pid}.json", {"variants": {
            "ripe=true": {"model": f"{NS}:block/{pid}"}, "ripe=false": {"model": f"{NS}:block/{pid}_growing"}}})
        block_item(pid)
        write_json(DATA / "loot_table" / "blocks" / f"{pid}.json", loot_self(pid))
        lang[f"block.{NS}.{pid}"] = label

    # Sprinklers.
    for i, (sid, label) in enumerate(SPRINKLERS.items()):
        metal, accent, glow = SPRINKLER_ART[sid]
        tex = sprinkler_tex(sid, metal, accent, glow, 50 + i)
        tex.save(bt / f"{sid}.png")
        sheet_blocks.append((sid.replace("_sprinkler", " spr"), tex))
        write_json(ASSETS / "models" / "block" / f"{sid}.json", sprinkler_model(sid))
        write_json(ASSETS / "blockstates" / f"{sid}.json", {"variants": {"": {"model": f"{NS}:block/{sid}"}}})
        block_item(sid)
        write_json(DATA / "loot_table" / "blocks" / f"{sid}.json", loot_self(sid))
        lang[f"block.{NS}.{sid}"] = label

    # Items.
    for name, label in ITEMS.items():
        rows, ramps = M[name]
        tex = sprite(rows, **ramps)
        item_files(name, tex)
        sheet_items.append((name, tex))
        lang[f"item.{NS}.{name}"] = label
    write_json(ASSETS / "lang" / "en_us.json", lang)

    # Tags.
    crops = [f"{NS}:{c}" for c in CROPS]
    write_json(MC_TAGS / "crops.json", {"replace": False, "values": crops})
    write_json(MC_TAGS / "maintains_farmland.json", {"replace": False, "values": crops})
    write_json(MC_TAGS / "mineable" / "axe.json", {"replace": False, "values": [f"{NS}:{p}" for p in PLANTERS]})
    write_json(MC_TAGS / "mineable" / "pickaxe.json", {"replace": False, "values": [f"{NS}:{s}" for s in SPRINKLERS]})
    write_json(RES / "data" / "skyrpg_stats" / "tags" / "block" / "farming_fortune_blocks.json", {"replace": False, "values": crops})

    # Recipes: 9 dewdrops <-> 1 vial.
    write_json(DATA / "recipe" / "dew_vial.json", {"type": "minecraft:crafting_shapeless", "category": "misc",
               "ingredients": [f"{NS}:dewdrop"] * 9, "result": {"id": f"{NS}:dew_vial"}})
    write_json(DATA / "recipe" / "dewdrop_from_vial.json", {"type": "minecraft:crafting_shapeless", "category": "misc",
               "ingredients": [f"{NS}:dew_vial"], "result": {"count": 9, "id": f"{NS}:dewdrop"}})

    # Validate.
    for p in RES.rglob("*.json"):
        json.loads(p.read_text(encoding="utf-8"))

    if "--preview" in sys.argv:
        out = Path(sys.argv[sys.argv.index("--preview") + 1])
        out.mkdir(parents=True, exist_ok=True)
        tiles = [(n, t, (48, 40, 52)) for n, t in sheet_items] + [(n, t, (96, 78, 60)) for n, t in sheet_blocks]
        cols, cell = 8, 136
        rows = (len(tiles) + cols - 1) // cols
        sheet = Image.new("RGBA", (cols * cell, rows * (cell + 16)), (24, 26, 34, 255))
        draw = ImageDraw.Draw(sheet)
        for i, (n, t, bg) in enumerate(tiles):
            x, y = (i % cols) * cell, (i // cols) * (cell + 16)
            sheet.paste(Image.new("RGBA", (128, 128), bg + (255,)), (x + 4, y + 2))
            sheet.alpha_composite(t.resize((128, 128), Image.NEAREST), (x + 4, y + 2))
            draw.text((x + 4, y + 132), n, fill=(230, 230, 240, 255))
        sheet.save(out / "farming_sheet.png")
        print("preview:", out / "farming_sheet.png")
    print(f"Wrote {len(CROPS)} crops, {len(PLANTERS)} planters, {len(SPRINKLERS)} sprinklers, {len(ITEMS)} items")


if __name__ == "__main__":
    main()
