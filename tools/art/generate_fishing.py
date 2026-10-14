#!/usr/bin/env python3
"""Draws the fishing mod's items and writes their models.

    python tools/art/generate_fishing.py

Writes, under fishing/src/main/resources/assets/skyrpg_fishing/: textures/item/<id>.png, models/item/<id>.json and
items/<id>.json for every item below, and lang/en_us.json (item names and the creative tab). Uses generate_items.py's
mask renderer and palettes (shading, outlines). The fishing rods and the fishing armour are drawn by generate_items.py,
because they belong to skyrpg_items.
"""
import json
import math
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import generate_items as gi  # noqa: E402

ROOT = Path(__file__).resolve().parents[2]
ASSETS = ROOT / "fishing" / "src" / "main" / "resources" / "assets" / "skyrpg_fishing"

TIDE = gi.Material(["0a3a46", "126070", "1e9a9c", "50d0c0", "b8fff0"], texture=gi.sheen)
TIDE_AQUA = gi.Material(["1a6a84", "30a0b8", "70d4e0", "b0f4f8", "f0ffff"], texture=gi.flat)
TIDE_VIOLET = gi.Material(["3a2a70", "5c4aa0", "8a78d0", "b8a8f0", "e8e0ff"], texture=gi.flat)
TIDE_DARK = gi.Material(["04202a", "083844", "0c5260", "147484", "2098a4"], texture=gi.flat)
SILVER_RIM = gi.Material(["3a5058", "607c84", "92b0b6", "c4dce0", "f4ffff"], texture=gi.sheen)
PEARL = gi.Material(["02040c", "060e22", "0e1c44", "1a3068", "2c4c94"])
COLD = gi.Material(["0a5a6a", "10889c", "30c8e0", "80f0ff", "e0ffff"], base=3)
DEEP_TEAL = gi.Material(["04242c", "0a4452", "10687a", "1c8c9c", "3cb4be"], texture=gi.flat)
SCALE_GOLD = gi.Material(["5e3a08", "9a6814", "d0a024", "f0d050", "fff4a8"], texture=gi.flat)
WORM = gi.Material(["8a2a48", "c8507a", "f0809e", "ffb0c4", "ffe0ea"])
HOOK = gi.Material(["3a3d44", "5e636e", "878d99", "b4bac4", "e2e6ec"], texture=gi.flat)
GLOW = gi.Material(["3a5a00", "7aa800", "b8e820", "e0ff60", "fbffc0"], base=3)
GLOW_HALO = gi.Material(["1a2a00", "3a5a00", "5a8a00", "7aa800", "a0d010"], base=2)
LURE = gi.Material(["04060c", "0a101e", "141c32", "222e4c", "38486e"])
TOOTH = gi.Material(["6a604c", "a09478", "cfc4a4", "eae2c8", "fffaea"])

ITEMS = {}


def grid():
    return [["."] * 16 for _ in range(16)]


def rows_of(g):
    return ["".join(r) for r in g]


def disc(g, cx, cy, r, ch):
    for y in range(16):
        for x in range(16):
            if math.hypot(x - cx, y - cy) <= r:
                g[y][x] = ch


# A teal fish scale that shifts from aqua through teal to violet, with ribs fanning out from its root.
ITEMS["tidescale"] = ({"T": TIDE, "A": TIDE_AQUA, "V": TIDE_VIOLET, "D": TIDE_DARK}, [
    "................",
    ".....AAAATT.....",
    "...AAAAATTTTT...",
    "..AAAATTTTTTVV..",
    "..AATTTTTTTVVV..",
    ".AATTTTTTTVVVV..",
    ".AATTTTTTTTVVV..",
    ".ATTTTTTTTTVVV..",
    "..TTTTTTTTTVV...",
    "..TTTTTTTTVV....",
    "...TTTTTTTV.....",
    ".....TTTTT......",
    "......DD........",
    "................",
    "................",
    "................",
], [
    "................",
    "................",
    "................",
    "................",
    "....1...1...1...",
    "....1...1...1...",
    "...1....1....1..",
    "...1....1....1..",
    "....1...1...1...",
    ".....1..1..1....",
    "......1.1.1.....",
    "................",
    "................",
    "................",
    "................",
    "................",
])

# Nine scales pressed into one round plate: a 3x3 of scales inside a bright rim, with a diagonal shine.
g, t = grid(), [["."] * 16 for _ in range(16)]
disc(g, 7.5, 7.5, 6.6, "R")
disc(g, 7.5, 7.5, 5.4, "D")
for y in range(16):
    for x in range(16):
        if g[y][x] == "D":
            cx, cy = (x - 3) // 3, (y - 3) // 3
            if x in (5, 8, 11) or y in (5, 8, 11):
                g[y][x] = "D"
            else:
                g[y][x] = ("A", "T", "V")[(cx + cy) % 3]
for k in range(5):
    t[4 + k][4 + k] = "4"
ITEMS["tidescale_plate"] = ({"R": SILVER_RIM, "D": TIDE_DARK, "A": TIDE_AQUA, "T": TIDE, "V": TIDE_VIOLET}, rows_of(g), rows_of(t))

# A dark pearl: a sphere lit from the upper left, with one cold cyan glint.
g, t = grid(), [["."] * 16 for _ in range(16)]
disc(g, 7.5, 8.0, 5.6, "N")
for y in range(16):
    for x in range(16):
        if g[y][x] == "N":
            d = math.hypot(x - 5.2, y - 5.6) / 9.0
            t[y][x] = str(max(0, min(3, 3 - int(d * 4.2))))
for x, y in ((4, 5), (5, 4), (5, 5), (4, 6)):
    g[y][x] = "C"
    t[y][x] = "."
ITEMS["abyssal_pearl"] = ({"N": PEARL, "C": COLD}, rows_of(g), rows_of(t))

# A big jagged scale: deep teal, gold-edged, with a few bright flecks near the root.
leviathan = [
    "................",
    ".......GG.......",
    "......GTTG......",
    "....GGTTTTGG....",
    "...GTTTAATTTG...",
    "..GTTTATTTTTTG..",
    "..GTTATTTTTTTG..",
    ".GTTTTTTTTTTTTG.",
    ".GTTTTTTTTTTTTG.",
    "..GTTTTTTTTTTG..",
    "..GGTTTTTTTTGG..",
    "...GTTTTTTTTG...",
    "....GGTTTTGG....",
    ".....GGTTGG.....",
    "......GGGG......",
    "................",
]
lg = [list(r) for r in leviathan]
for x, y in ((1, 8), (14, 7), (2, 10), (13, 5)):
    lg[y][x] = "."
for x, y in ((0, 6), (15, 9), (3, 3), (12, 3)):
    lg[y][x] = "G"
lt = [["."] * 16 for _ in range(16)]
for x, y in ((8, 7), (9, 8), (10, 9), (9, 10), (7, 11), (6, 9)):
    lt[y][x] = "1"
ITEMS["leviathan_scale"] = ({"T": DEEP_TEAL, "G": SCALE_GOLD, "A": TIDE_AQUA}, rows_of(lg), rows_of(lt))


def worm_grid(thick=2):
    g = grid()
    cells = []
    for x in range(2, 12):
        y = 10 + round(2.2 * math.sin((x - 2) / 1.5))
        for k in range(thick):
            cells.append((x, y + k))
    return g, cells


# A pink worm threaded on a small silver hook.
g, cells = worm_grid()
for x, y in cells:
    g[y][x] = "W"
for x, y in ((12, 2), (13, 2), (13, 3), (12, 3)):
    g[y][x] = "H"
g[3][12] = "."
for y in range(4, 11):
    g[y][13] = "H"
for x, y in ((13, 11), (12, 12), (11, 12), (10, 11), (10, 10)):
    g[y][x] = "H"
w_tweak = [["."] * 16 for _ in range(16)]
for i, (x, y) in enumerate(cells):
    if (x % 2) == 0:
        w_tweak[y][x] = "1"
ITEMS["worm_bait"] = ({"W": WORM, "H": HOOK}, rows_of(g), rows_of(w_tweak))

# A glowing worm curled up, with a halo of light motes.
g, cells = worm_grid(3)
halo = [["."] * 16 for _ in range(16)]
for x, y in cells:
    g[y][x] = "G"
for x, y in ((2, 5), (5, 3), (9, 4), (13, 6), (12, 13), (4, 14), (8, 14), (1, 11)):
    g[y][x] = "M"
g_tweak = [["."] * 16 for _ in range(16)]
for x, y in cells:
    g_tweak[y][x] = "4" if (x + y) % 3 == 0 else "3"
ITEMS["glowworm_bait"] = ({"G": GLOW, "M": GLOW_HALO}, rows_of(g), rows_of(g_tweak))

# An anglerfish lure: a dark body full of teeth, a stalk, and a glowing cyan bulb.
ITEMS["abyssal_bait"] = ({"N": LURE, "C": COLD, "W": COLD.shifted(base=4), "T": TOOTH, "H": HOOK, "E": COLD.shifted(base=3)}, [
    "........CCC.....",
    ".......CCWCC....",
    "........CCC.....",
    ".......N........",
    "......N.........",
    "...NNNNNN.......",
    "..NNNNNNNN......",
    ".NNNENNNNNN.....",
    ".NTTNNNNNNNN....",
    ".NNTNTNNNNNNN...",
    "..NNTNTNNNNNN...",
    "...NNNNNNNNN....",
    ".....NNNNNHH....",
    ".............H..",
    "............H...",
    "................",
])

# ---- lava fishing (the Magmaline Rod, in the Nether)
MAGMA_CRUST = gi.Material(["0a0606", "180c0a", "281612", "3e231a", "5a3424"], texture=gi.flat)
MOLTEN = gi.Material(["5a1004", "a02a08", "e05a14", "ffa040", "fff0b0"], base=3)
EMBER_RED = gi.Material(["3a0806", "6a140a", "a42a10", "d84a1a", "ff8a3a"], texture=gi.flat)
EMBER_GOLD = gi.Material(["7a3a06", "c46a0e", "f2a420", "ffd060", "fff4c0"], texture=gi.flat)

# A Magma Fish: a black-crusted fish cracked with glowing magma, a bright eye, molten fins and tail.
ITEMS["magma_fish"] = ({"C": MAGMA_CRUST.shifted(base=3), "M": MOLTEN, "F": MOLTEN.shifted(base=2), "E": MOLTEN.shifted(base=4)}, [
    "................",
    "................",
    "................",
    "......FF........",
    ".....FCCF.......",
    "....CCCCCCC...FF",
    "...CCCMMCCCC.FFF",
    "..CECCCCMMCCCFF.",
    ".CCCCMCCCCMCCF..",
    "..CCCCMMCCCMCFF.",
    "...CCCCCMMCC.FFF",
    "....CCCCCCC...FF",
    "......FCF.......",
    "................",
    "................",
    "................",
])

# An Ember Scale: the Tidescale's shape in ember colours, from deep red at the root to gold at the tip.
ITEMS["ember_scale"] = ({"T": gi.EMBER.shifted(texture=gi.sheen), "A": EMBER_GOLD, "V": EMBER_RED, "D": MAGMA_CRUST.shifted(base=3)},
                        ITEMS["tidescale"][1], ITEMS["tidescale"][2])

# A Molten Pearl: a sphere of magma in a cooled black shell, glowing through its cracks, with one hot glint.
g, t = grid(), [["."] * 16 for _ in range(16)]
disc(g, 7.5, 8.0, 5.6, "K")
for x, y in ((10, 4), (9, 5), (9, 6), (8, 7), (7, 8), (7, 9), (6, 10), (5, 11), (9, 8), (10, 9), (11, 9), (6, 9), (4, 9)):
    g[y][x] = "M"
for y in range(16):
    for x in range(16):
        if g[y][x] == "K":
            d = math.hypot(x - 5.2, y - 5.6) / 9.0
            t[y][x] = str(max(0, min(3, 3 - int(d * 4.2))))
for x, y in ((4, 4), (5, 4), (4, 5)):
    g[y][x] = "W"
    t[y][x] = "."
ITEMS["molten_pearl"] = ({"K": MAGMA_CRUST, "M": MOLTEN, "W": MOLTEN.shifted(base=4)}, rows_of(g), rows_of(t))


def write_json(path, obj):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(obj, indent=2) + "\n", encoding="utf-8", newline="\n")


NAMES = {"tidescale": "Tidescale", "tidescale_plate": "Tidescale Plate", "abyssal_pearl": "Abyssal Pearl",
         "leviathan_scale": "Leviathan Scale", "worm_bait": "Worm Bait", "glowworm_bait": "Glowworm Bait",
         "abyssal_bait": "Abyssal Bait"}
NAMES.update({"magma_fish": "Magma Fish", "ember_scale": "Ember Scale", "molten_pearl": "Molten Pearl"})


def build():
    for name, (mats, mask, *tweak) in ITEMS.items():
        img = gi.render(mask, mats, tweak[0] if tweak else None)
        out = ASSETS / "textures" / "item" / f"{name}.png"
        out.parent.mkdir(parents=True, exist_ok=True)
        img.save(out)
        write_json(ASSETS / "models" / "item" / f"{name}.json",
                   {"parent": "minecraft:item/generated", "textures": {"layer0": f"skyrpg_fishing:item/{name}"}})
        write_json(ASSETS / "items" / f"{name}.json", {"model": {"type": "minecraft:model", "model": f"skyrpg_fishing:item/{name}"}})
    lang = {"itemGroup.skyrpg_fishing": "SkyRPG Fishing"}
    lang.update({f"item.skyrpg_fishing.{name}": title for name, title in NAMES.items()})
    write_json(ASSETS / "lang" / "en_us.json", lang)
    print(f"wrote {len(ITEMS)} fishing item(s) and the lang file")


if __name__ == "__main__":
    build()
