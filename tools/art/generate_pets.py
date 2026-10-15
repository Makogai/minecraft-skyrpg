#!/usr/bin/env python3
"""Draws the pets mod's art: the item icons, and the 3D models of the pets that float at their owner's shoulder.

    python tools/art/generate_pets.py [--only icons|models] [--preview DIR]

Item icons: under pets/src/main/resources/assets/skyrpg_pets/, textures/item/<id>.png, models/item/<id>.json and
items/<id>.json for every item below, and lang/en_us.json. Uses generate_items.py's mask renderer (shading,
outlines). Each pet icon is a small creature facing the viewer; the eggs are drawn from an egg shape with a hint of
the creature on them.

3D pets (the second half of this file): for each of the five pets a cube table, painted once per rarity:

    pets/src/main/resources/assets/skyrpg_pets/textures/entity/pet/<pet>_<rarity>.png
    ... <pet>_<rarity>_glow.png   (only where something glows; drawn fullbright)
    pets/src/client/java/com/skyrpg/pets/client/model/<Pet>PetLayout.java

``--preview DIR`` also renders preview sheets into DIR: every rarity from the front, back and side, animation poses,
the pet beside a mock player in third person, and the textures.
"""
import json
import math
import sys
from pathlib import Path

import numpy as np
from PIL import Image

sys.path.insert(0, str(Path(__file__).resolve().parent))
import cube_model as cm  # noqa: E402
import generate_items as gi  # noqa: E402
from cube_model import face_rects, hexc, hnoise, mix, mul, put, vnoise  # noqa: E402

ROOT = Path(__file__).resolve().parents[2]
ASSETS = ROOT / "pets" / "src" / "main" / "resources" / "assets" / "skyrpg_pets"

M = gi.Material
VELVET = M(["0e0c14", "1c1824", "2c2638", "403850", "5a506c"])
PINK = M(["8a3a4a", "c26a7a", "e898a4", "ffc0c8", "ffe8ec"], texture=gi.flat)
CLAW = M(["6a604c", "a09478", "cfc4a4", "eae2c8", "fffaea"], texture=gi.flat)
LAMP = M(["7a3c06", "c26a0c", "f2a21c", "ffd25a", "fff2b0"], base=3)
BRASS = M(["4a3010", "7a5420", "b0823a", "d8ac5a", "f6dc96"], texture=gi.flat)
EYE_PALE = M(["a09478", "cfc4a4", "eae2c8", "fffaea", "ffffff"], base=3)
BEE_YELLOW = M(["8a5a08", "c08a14", "f0b828", "ffd860", "fff0a8"], texture=gi.speckle)
BEE_BLACK = M(["04040a", "0e0e16", "1c1c28", "2c2c3c", "44445a"], texture=gi.flat)
WING = M(["6a8aa8", "9ab8d4", "c8e0f0", "e8f6ff", "ffffff"], texture=gi.flat)
OWL_BROWN = M(["2a1c14", "46301f", "6a4c34", "8c6c4c", "b09068"], texture=gi.speckle)
OWL_BELLY = M(["6a5a46", "9a8a70", "c4b496", "e4d8bc", "fff4dc"], texture=gi.speckle)
OWL_MARK = M(["1a1008", "2e1c10", "46301f", "6a4c34", "8c6c4c"], texture=gi.flat)
EYE_WHITE = M(["a09080", "d0c4b0", "f4ecd8", "ffffff", "ffffff"], base=3, texture=gi.flat)
EYE_RING = M(["8a5a08", "c08a14", "f0b828", "ffd860", "fff0a8"], base=3, texture=gi.flat)
BEAK = M(["8a3a06", "c26a0c", "f09a20", "ffc050", "ffe090"], texture=gi.flat)
WOLF_GREY = M(["2a2e38", "484e5c", "6c7484", "9098a8", "bcc4d2"], texture=gi.speckle)
WOLF_LIGHT = M(["70788a", "9ca4b4", "c4ccd8", "e4eaf2", "ffffff"], texture=gi.speckle)
DARK = M(["06060a", "10101a", "1c1c28", "2c2c3c", "44445a"], texture=gi.flat)
COLLAR = M(["4a0a10", "7a141e", "b02030", "d84a50", "f08a8a"], texture=gi.flat)
TAG = M(["5e3a08", "9a6814", "d0a024", "f0d050", "fff4a8"], texture=gi.flat)
OTTER = M(["2a1a10", "4a3020", "6e4c34", "92704c", "b89470"], texture=gi.speckle)
OTTER_LIGHT = M(["7a6248", "a88c68", "ccb28c", "e8d4b0", "fff0d4"], texture=gi.flat)
OTTER_HAND = M(["1a0e08", "2e1c10", "46301f", "644430", "86604a"], texture=gi.flat)
SHELL = M(["7a7a98", "a8a8c8", "d4d4ec", "f0f0ff", "ffffff"], texture=gi.flat)
SHELL_RIDGE = M(["9a5a78", "c88aa4", "e8b0c4", "f8d4e0", "ffeef4"], texture=gi.flat)

PUMPKIN = M(["7a3006", "b8500c", "e8741a", "ff9a34", "ffc070"], texture=gi.flat)
PUMPKIN_DARK = M(["5a2004", "8a3a08", "b8500c", "d86a14", "f08a2c"], texture=gi.flat)
VINE_GREEN = M(["2c3a10", "3e5416", "566e22", "728c32", "92aa4a"], texture=gi.flat)
LEAF_GREEN = M(["1a4a14", "26681c", "3a8a28", "5aac3a", "8ad060"], texture=gi.flat)
CANDLE = M(["c8841c", "f0b028", "ffd23c", "ffe680", "fff8d0"], base=3, texture=gi.flat)

ITEMS = {}
NAMES = {"mole_pet": "Mole", "bee_pet": "Bumblebee", "owl_pet": "Owl", "pup_pet": "Wolf Pup", "otter_pet": "Otter", "jack_pet": "Jack o'Lantern",
         "mole_egg": "Mole Egg", "bee_egg": "Bumblebee Egg", "owl_egg": "Owl Egg", "pup_egg": "Wolf Pup Egg",
         "otter_egg": "Otter Egg", "golden_pet_egg": "Golden Pet Egg"}

# A velvet mole with a pink nose, big pale digging claws and a tiny miner's lamp.
ITEMS["mole_pet"] = ({"V": VELVET, "N": PINK, "C": CLAW, "E": EYE_PALE, "A": LAMP, "H": BRASS}, [
    "................",
    ".......AA.......",
    "......HHHH......",
    ".....VVVVVV.....",
    "....VVVVVVVV....",
    "...VVVVVVVVVV...",
    "...VVEVVVVEVV...",
    "...VVVVNNVVVV...",
    "..VVVVVNNVVVVV..",
    "..VVVVVVVVVVVV..",
    ".CCVVVVVVVVVVCC.",
    ".CCCVVVVVVVVCCC.",
    ".CCCC.VVVV.CCCC.",
    "..CC..VVVV..CC..",
    "................",
    "................",
])

# A round, fuzzy bumblebee: black antennae, big eyes, striped belly, pale wings.
ITEMS["bee_pet"] = ({"Y": BEE_YELLOW, "K": BEE_BLACK, "W": WING, "E": EYE_PALE}, [
    "................",
    "....K......K....",
    ".....K....K.....",
    "......YYYY......",
    ".....YYYYYY.....",
    "....YKEYYEKY....",
    "...YYYYYYYYYY...",
    "..WYKKKKKKKKYW..",
    ".WWYYYYYYYYYYWW.",
    ".WWYKKKKKKKKYWW.",
    "..WYYYYYYYYYYW..",
    "...YKKKKKKKKY...",
    "....YYYYYYYY....",
    ".....YYYYYY.....",
    "......KKKK......",
    "................",
])

# A brown-grey owl: ear tufts, big round eyes with yellow rings, a small beak, a feathered belly.
ITEMS["owl_pet"] = ({"B": OWL_BROWN, "T": OWL_MARK, "W": EYE_WHITE, "P": DARK, "Y": EYE_RING, "O": BEAK, "L": OWL_BELLY}, [
    "................",
    "...T........T...",
    "...TT......TT...",
    "...BBBBBBBBBB...",
    "..BBBBBBBBBBBB..",
    "..BWWWWBBWWWWB..",
    "..BWPPWBBWPPWB..",
    "..BWPPWOOWPPWB..",
    "..BWWWWOOWWWWB..",
    "..BBBBBBBBBBBB..",
    "..BBBLLLLLLBBB..",
    "..BBLLTLLTLLBB..",
    "...BLLLLLLLLB...",
    "....LTLLLLTL....",
    ".....OO..OO.....",
    "................",
])

# A grey wolf pup: pointed ears, a pale muzzle with a dark nose, a red collar with a gold tag.
ITEMS["pup_pet"] = ({"G": WOLF_GREY, "W": WOLF_LIGHT, "K": DARK, "N": DARK, "R": COLLAR, "Y": TAG}, [
    "................",
    "..GG........GG..",
    "..GGG......GGG..",
    "..GGGGGGGGGGGG..",
    "..GGGGGGGGGGGG..",
    "..GGGKGGGGKGGG..",
    "..GGGGGWWGGGGG..",
    "...GGGWWWWGGG...",
    "...GGGWNNWGGG...",
    "....GGGWWGGG....",
    "...RRRRRRRRRR...",
    "...RRRRRYRRRR...",
    "...GGGGWWGGGG...",
    "...GGGWWWWGGG...",
    "...GG.WWWW.GG...",
    "................",
])

# A brown otter holding a pink shell to its chest, whiskers out.
ITEMS["otter_pet"] = ({"B": OTTER, "L": OTTER_LIGHT, "K": DARK, "N": DARK, "W": OTTER_LIGHT, "H": OTTER_HAND, "S": SHELL,
                       "P": SHELL_RIDGE}, [
    "................",
    "....BB....BB....",
    "...BBBBBBBBBB...",
    "..BBBBBBBBBBBB..",
    "..BBBKBBBBKBBB..",
    "..BBBBLLLLBBBB..",
    ".WBBBLLNNLLBBBW.",
    "..BBBBLLLLBBBB..",
    "...BBBBBBBBBB...",
    "..BBLLLLLLLLBB..",
    "..BBLLSSSSLLBB..",
    "..BHHSSPPSSHHB..",
    "...BBSSSSSSBB...",
    "....BB....BB....",
    "................",
    "................",
])


# A floating jack-o'-lantern: orange ridged pumpkin, a curly green stem and leaf, a carved face glowing yellow.
ITEMS["jack_pet"] = ({"P": PUMPKIN, "D": PUMPKIN_DARK, "G": VINE_GREEN, "L": LEAF_GREEN, "Y": CANDLE}, [
    "................",
    ".......G.G......",
    ".......GG.......",
    "...LLL.GG.......",
    "..LLLL..........",
    "....DPPPPPPD....",
    "...PDPPPPPPDP...",
    "..PPDYYPPYYDPP..",
    ".PPPDYYPPYYDPPP.",
    ".PPPDPPPPPPDPPP.",
    ".PPPDPPPPPPDPPP.",
    ".PPPYYPYYPYYPPP.",
    "..PPDYYYYYYDPP..",
    "...PDPPPPPPDP...",
    "....DPPPPPPD....",
    "................",
])


# ---------------------------------------------------------------- eggs
def egg_shape():
    """Cells of an egg, narrower at the top, with a shade (0-4) from a light at the upper left."""
    cells = {}
    for y in range(16):
        t = (y - 8.2) / 6.6
        if abs(t) > 1:
            continue
        half = 5.2 * math.sqrt(1 - t * t) * (1 + 0.14 * t)
        for x in range(16):
            nx = (x - 7.5) / 5.2
            if abs(x - 7.5) <= half:
                ny = t
                nz = math.sqrt(max(0.0, 1 - min(1.0, nx * nx + ny * ny)))
                d = (nx * -0.5 + ny * -0.6 + nz * 0.62)
                cells[(x, y)] = max(0, min(4, round(1.1 + 2.5 * d)))
    return cells


def noise(*args):
    n = 0
    for a in args:
        n = (n * 1000003 + int(a) * 2654435761 + 97) & 0xFFFFFFFF
    n ^= n >> 15
    return (n * 2246822519) & 0xFFFFFFFF


def egg(base, marks):
    """(mask, tweak) of an egg: base letter 'E' everywhere, then marks(x, y) -> letter or None for each cell."""
    cells = egg_shape()
    mask = [["."] * 16 for _ in range(16)]
    tweak = [["."] * 16 for _ in range(16)]
    for (x, y), shade in cells.items():
        mask[y][x] = marks(x, y) or "E"
        tweak[y][x] = str(shade)
    return ["".join(r) for r in mask], ["".join(r) for r in tweak]


EGG_BROWN = M(["2a1a10", "46301c", "6a4a2c", "8c6a40", "b08c5c"], texture=gi.flat)
STONE = M(["3a3e46", "5a606a", "808890", "a8b0b8", "d0d6dc"], texture=gi.flat)
HONEY = M(["8a5a08", "c08a14", "f0b828", "ffd860", "fff0a8"], texture=gi.flat)
CREAM = M(["8a7a5c", "b8a880", "e0d4aa", "f4ecd0", "fffaea"], texture=gi.flat)
EGG_GREY = M(["3a3e48", "5a6070", "808898", "a8b0c0", "d4dae6"], texture=gi.flat)
SEA = M(["0a3a46", "10606a", "1a9a9a", "50ccbc", "b0f8ec"], texture=gi.flat)
FOAM = M(["80c0c8", "a8e0e4", "d0f8f8", "f0ffff", "ffffff"], texture=gi.flat)
GOLD_EGG = M(["6a4208", "a8700e", "e0a620", "fcd84c", "fffab0"], texture=gi.flat)
SPARK = M(["d0c070", "f4e8a0", "fffad0", "ffffff", "ffffff"], texture=gi.flat)


def mole_marks(x, y):
    n = noise(x, y, 1) % 100
    if n < 16:
        return "S"
    return "D" if (x + y * 2) % 11 == 0 else None


def bee_marks(x, y):
    band = (y + (0 if abs(x - 7.5) < 2 else 1 if abs(x - 7.5) < 4 else 2)) % 4
    return "K" if y in range(4, 14) and band == 0 else None


def owl_marks(x, y):
    # Speckles, and a few little chevrons like feather tips.
    if (x, y) in {(5, 6), (6, 7), (7, 6), (9, 10), (10, 11), (11, 10), (5, 12), (6, 13)}:
        return "F"
    return "F" if noise(x, y, 4) % 100 < 9 else None


def pup_marks(x, y):
    paw = {(5, 9), (6, 7), (9, 7), (10, 9), (6, 10), (7, 10), (8, 10), (9, 10), (5, 11), (6, 11), (7, 11), (8, 11), (9, 11),
           (10, 11), (6, 12), (7, 12), (8, 12), (9, 12)}
    return "D" if (x, y) in paw else None


def otter_marks(x, y):
    for row in (5, 8, 11):
        if y == row + (1 if (x // 2) % 2 else 0) and 4 <= x <= 11:
            return "W"
    return None


def golden_marks(x, y):
    plus = {(4, 5), (3, 5), (5, 5), (4, 4), (4, 6), (11, 9), (10, 9), (12, 9), (11, 8), (11, 10), (7, 12), (6, 12), (8, 12), (7, 11), (7, 13)}
    return "W" if (x, y) in plus else ("H" if (x + y) % 7 == 0 else None)


for name, mats, marks in (
        ("mole_egg", {"E": EGG_BROWN, "S": STONE, "D": M(["14090a", "22140c", "34200f", "4a3018", "624426"], texture=gi.flat)}, mole_marks),
        ("bee_egg", {"E": HONEY, "K": BEE_BLACK}, bee_marks),
        ("owl_egg", {"E": CREAM, "F": OWL_MARK}, owl_marks),
        ("pup_egg", {"E": EGG_GREY, "D": M(["0e1016", "1a1e28", "283040", "384458", "4c5c76"], texture=gi.flat)}, pup_marks),
        ("otter_egg", {"E": SEA, "W": FOAM}, otter_marks),
        ("golden_pet_egg", {"E": GOLD_EGG, "W": SPARK, "H": GOLD_EGG.shifted(base=3)}, golden_marks)):
    mask, tweak = egg("E", marks)
    ITEMS[name] = (mats, mask, tweak)


def write_json(path, obj):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(obj, indent=2) + "\n", encoding="utf-8", newline="\n")


def build_icons():
    for name, (mats, mask, *tweak) in ITEMS.items():
        img = gi.render(mask, mats, tweak[0] if tweak else None)
        out = ASSETS / "textures" / "item" / f"{name}.png"
        out.parent.mkdir(parents=True, exist_ok=True)
        img.save(out)
        write_json(ASSETS / "models" / "item" / f"{name}.json",
                   {"parent": "minecraft:item/generated", "textures": {"layer0": f"skyrpg_pets:item/{name}"}})
        write_json(ASSETS / "items" / f"{name}.json", {"model": {"type": "minecraft:model", "model": f"skyrpg_pets:item/{name}"}})
    lang = {"itemGroup.skyrpg_pets": "SkyRPG Pets"}
    lang.update({f"item.skyrpg_pets.{name}": title for name, title in NAMES.items()})
    write_json(ASSETS / "lang" / "en_us.json", lang)
    print(f"wrote {len(ITEMS)} pet item(s) and the lang file")


# =====================================================================================================================
# 3D pets: the models that float at their owner's shoulder
# =====================================================================================================================
# The same pipeline as the world mobs (generate_sporeling.py): each pet's cube table below is the single source of
# truth for its textures and for its generated Java layout, using tools/art/cube_model.py (cube table, UV packing,
# painting helpers, preview renderer). cube_model.py is shared, so what the pets need on top lives here:
#   * cubes are given by their absolute corners, designed as if no part were rotated. Sizes are rounded to whole
#     texels for the UV net and the rest is made up with a CubeDeformation (vanilla's trick), so every face stays on
#     the pixel grid however small the cube;
#   * zero-thickness planes (wings, petals, the bandana) and translucent texels (the bee's wings);
#   * rarity forms, like the slayer tiers: a part or cube can be limited to some rarities (vis=...). Cubes that differ
#     from their part go into a generated child part; the layout lists every such part with its rarities so the model
#     can toggle them. Each rarity also gets its own texture (colours) and, where something glows, a glow layer.
# Model space is entity model space: pixels, y down, -z the pet's front, -x its right. The origin is the point that
# floats beside the shoulder (PetLayer puts it there and scales the pet by SCALE).

PET_TEX = ASSETS / "textures" / "entity" / "pet"
PET_JAVA = ROOT / "pets" / "src" / "client" / "java" / "com" / "skyrpg" / "pets" / "client" / "model"
PET_PACKAGE = "com.skyrpg.pets.client.model"
RARITIES = ("common", "uncommon", "rare", "epic", "legendary")
COMMON, UNCOMMON, RARE, EPIC, LEGENDARY = range(5)
EVERY = 0b11111


def only(*rarities):
    return sum(1 << r for r in set(rarities))


def since(rarity):
    return EVERY & ~((1 << rarity) - 1)


def span(first, last):
    return only(*range(first, last + 1))


class PetCubes(cm.CubeModel):
    """A pet's cube table: parts and cubes in absolute coordinates, with the rarities that show them."""

    def __init__(self, name, cls, what, scale, translucent=False):
        super().__init__(name)
        self.cls = cls  # Java class prefix: <cls>Layout, <cls>Model
        self.what = what
        self.scale = scale
        self.translucent = translucent
        self.abs = {None: (0.0, 0.0, 0.0)}
        self.palette = None  # rarity -> {material: Mat or plain value}
        self.pose = None  # (pet, t, **anim) -> preview pose, a sketch of the Java model's setupAnim

    def part(self, name, parent, pivot, rot=(0, 0, 0), vis=EVERY):
        """A part pivoting at ``pivot`` (absolute). Hidden for rarities outside ``vis``, and so are its children."""
        base = self.abs[parent]
        super().part(name, parent, tuple(p - b for p, b in zip(pivot, base)), rot)
        self.parts[-1]["vis"] = vis
        self.abs[name] = tuple(float(p) for p in pivot)

    def part_pair(self, name, parent_r, parent_l, pivot, rot=(0, 0, 0), vis=EVERY):
        """<name>_r on the pet's right (-x) and its mirror image <name>_l."""
        self.part(name + "_r", parent_r, pivot, rot, vis)
        self.part(name + "_l", parent_l, (-pivot[0], pivot[1], pivot[2]), (rot[0], -rot[1], -rot[2]), vis)

    def box(self, pname, cid, mat, lo, hi, inflate=0.0, vis=None, deco=None, share=None, mirror=False, sk=None):
        """A cube from ``lo`` to ``hi`` (absolute corners). A zero extent makes a plane."""
        piv = self.abs[pname]
        size, grow, pos = [], [], []
        for a, b, p in zip(lo, hi, piv):
            ext = b - a
            n = 0 if ext == 0 else max(1, round(ext))
            size.append(n)
            grow.append(0.0 if n == 0 else (ext - n) / 2 + inflate)
            pos.append(a + (ext - n) / 2 - p)
        kw = dict(grow=tuple(grow), vis=vis, deco=deco)
        if sk:
            kw["sk"] = sk
        self.cube(pname, cid, mat, tuple(pos), tuple(size), share=share, mirror=mirror, **kw)

    def pair(self, part_r, part_l, cid, mat, lo, hi, **kw):
        """A cube on the pet's right (-x) and its mirror image on the left, sharing the right one's UVs."""
        self.box(part_r, cid + "_r", mat, lo, hi, **kw)
        kw.pop("sk", None)
        self.box(part_l, cid + "_l", mat, (-hi[0], lo[1], lo[2]), (-lo[0], hi[1], hi[2]), share=cid + "_r", mirror=True, **kw)

    def ring(self, pname, cid, mat, center, half, bar, thick, **kw):
        """A flat square ring (a halo) of four bars around ``center``, ``half`` its outer half-width."""
        x, y, z = center
        y0, y1 = y - thick / 2, y + thick / 2
        self.box(pname, cid + "_f", mat, (x - half, y0, z - half), (x + half, y1, z - half + bar), sk=cid + "_fb", **kw)
        self.box(pname, cid + "_b", mat, (x - half, y0, z + half - bar), (x + half, y1, z + half), sk=cid + "_fb", **kw)
        self.box(pname, cid + "_r", mat, (x - half, y0, z - half + bar), (x - half + bar, y1, z + half - bar), sk=cid + "_s", **kw)
        self.box(pname, cid + "_l", mat, (x + half - bar, y0, z - half + bar), (x + half, y1, z + half - bar), sk=cid + "_s", **kw)

    # -------------------------------------------------------------------------------------------- rarity parts
    def expand(self):
        """Moves each cube shown for other rarities than its part into a generated child part the model toggles."""
        by = {p["name"]: p for p in self.parts}
        for c in self.cubes:
            p = by[c["part"]]
            vis = c.get("vis")
            if vis is None or vis == p["vis"]:
                continue
            sub = f"{p['name']}_x" + "".join(str(r + 1) for r in range(5) if vis >> r & 1)
            if sub not in by:
                self.part(sub, p["name"], self.abs[p["name"]], vis=vis)
                by[sub] = self.parts[-1]
            c["part"] = sub

    def hidden(self, rarity, blink=False):
        """Parts not drawn for this rarity (with their children); the eyelids too unless blinking."""
        out = {p["name"] for p in self.parts if not p["vis"] >> rarity & 1}
        if not blink:
            out.add("lids")
        grew = True
        while grew:
            grew = False
            for p in self.parts:
                if p["parent"] in out and p["name"] not in out:
                    out.add(p["name"])
                    grew = True
        return out

    # -------------------------------------------------------------------------------------------- Java
    def java(self, glow_mask):
        children, cubes_of = {}, {}
        for p in self.parts:
            children.setdefault(p["parent"], []).append(p)
        for c in self.cubes:
            cubes_of.setdefault(c["part"], []).append(c)
        out = []

        def emit(p, parent_var):
            pad = "        "
            b = [f"{pad}CubeListBuilder.create()"]
            mirrored = False
            for c in cubes_of.get(p["name"], []):
                if c["mirror"] != mirrored:
                    b.append(f"{pad}        .mirror({'true' if c['mirror'] else 'false'})")
                    mirrored = c["mirror"]
                args = ", ".join(jnum(n) for n in (*c["pos"], *c["size"]))
                g = c["grow"]
                if any(g):
                    args += f", new CubeDeformation({jnum(g[0])})" if g[0] == g[1] == g[2] else \
                        f", new CubeDeformation({', '.join(jnum(n) for n in g)})"
                b.append(f"{pad}        .texOffs({c['u']}, {c['v']}).addBox({args})")
            px, py, pz = p["pivot"]
            rx, ry, rz = p["rot"]
            pose = (f"PartPose.offsetAndRotation({jnum(px)}, {jnum(py)}, {jnum(pz)}, {jnum(rx)}, {jnum(ry)}, {jnum(rz)})"
                    if (rx or ry or rz) else f"PartPose.offset({jnum(px)}, {jnum(py)}, {jnum(pz)})")
            var = "p_" + p["name"]
            decl = f"PartDefinition {var} = " if children.get(p["name"]) else ""
            out.append(f'{pad}{decl}{parent_var}.addOrReplaceChild("{p["name"]}",')
            out.extend(b)
            out.append(f"{pad}        , {pose});")
            for ch in children.get(p["name"], []):
                emit(ch, var)

        for p in children.get(None, []):
            emit(p, "root")
        toggles = [p for p in self.parts if p["vis"] != EVERY]
        names = ", ".join(f'"{p["name"]}"' for p in toggles)
        masks = ", ".join(f"0b{p['vis']:05b}" for p in toggles)
        body = "\n".join(out)
        return f"""package {PET_PACKAGE};

import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;

/**
 * {self.what}'s cubes and their UVs, and which parts each rarity shows. GENERATED by tools/art/generate_pets.py from the
 * same cube table the textures are painted from: edit the table there, never this file.
 */
final class {self.cls}Layout {{
    /** Textures: textures/entity/pet/{self.name}_&lt;rarity&gt;.png, and _glow.png for the rarities in {{@link #GLOW_RARITIES}}. */
    static final String NAME = "{self.name}";
    /** Model pixels to player model pixels: how big the pet floats at the shoulder. */
    static final float SCALE = {jnum(self.scale)};
    /** Whether some texels are see-through (drawn with the translucent entity render type). */
    static final boolean TRANSLUCENT = {'true' if self.translucent else 'false'};
    /** Bit r (0 = Common) set: that rarity has a glow layer, drawn fullbright. */
    static final int GLOW_RARITIES = 0b{glow_mask:05b};
    /** Parts only some rarities show: RARITY_PARTS[i] is shown for rarity r when bit r of RARITY_MASKS[i] is set. */
    static final String[] RARITY_PARTS = {{{names}}};
    static final int[] RARITY_MASKS = {{{masks}}};

    private {self.cls}Layout() {{
    }}

    static LayerDefinition create() {{
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
{body}
        return LayerDefinition.create(mesh, {self.width}, {self.height});
    }}
}}
"""


def jnum(v):
    v = round(float(v), 5) + 0.0
    return f"{v:g}f"


# ------------------------------------------------------------------------------------------------ materials
FACE_LIGHT = {"top": 1.06, "bottom": 0.8, "right": 0.93, "front": 1.0, "left": 0.93, "back": 0.88}
SIDES = ("front", "back", "left", "right")
NOISE = {"fur": 1.0, "fuzz": 1.5, "soft": 0.55, "flat": 0.15, "metal": 0.3, "gem": 0.1, "glass": 0.0, "speck": 0.0}


class Mat:
    """How a material is painted: a dark-to-light ramp of five colours, a style, alpha, and an optional glow."""

    def __init__(self, ramp, style="fur", alpha=255, glow=None, glow_alpha=255, base=0.5):
        self.ramp = [hexc(h) for h in ramp]
        self.style = style
        self.alpha = alpha
        self.glow = hexc(glow) if isinstance(glow, str) else glow
        self.glow_alpha = glow_alpha
        self.base = base

    def at(self, t):
        return pick(self.ramp, t)

    def texel(self, face, cell, size, X, Y, seed):
        xi, yi, zi = cell
        w, h, d = size
        st = self.style
        n = vnoise(X, Y, 2.0, seed) - 0.5
        s = hnoise(X, Y, seed + 7) - 0.5
        t = self.base + NOISE[st] * (0.3 * n + 0.22 * s)
        if face in SIDES:
            t += 0.12 - 0.24 * (yi / max(1, h - 1))
        elif face == "top":
            t += 0.12
        else:
            t -= 0.15
        if st == "metal":
            if face in SIDES and yi == 0:
                t += 0.22
            if face == "top" and (xi + zi) % 4 == 1:
                t += 0.22
        elif st == "gem":
            t = 0.85 - 0.5 * ((xi + yi) / max(1, w + h - 2)) if face in SIDES else 0.8
        elif st == "glass":
            t = self.base
        elif st == "speck":
            t = self.base + (0.25 if hnoise(X, Y, seed + 3) > 0.8 else 0) - (0.3 if hnoise(X, Y, seed + 5) > 0.85 else 0)
        return mul(self.at(t), FACE_LIGHT[face]), self.alpha


def pick(r, t):
    return cm.pick(r, t)


def cells(c):
    """Every texel of the cube's net as (face, X, Y, px, py, (xi, yi, zi)): X, Y in the texture, px, py in the face,
    and the cell of the cube it shows (xi 0 = the -x side, yi 0 = the top, zi 0 = the front). Painting by cell keeps
    patterns continuous around the cube, and both faces of a plane alike."""
    w, h, d = (int(n) for n in c["size"])
    for face, (fx, fy, fw, fh) in face_rects(c).items():
        for py in range(fh):
            for px in range(fw):
                if face == "front":
                    cell = (px, py, 0)
                elif face == "back":
                    cell = (w - 1 - px, py, max(0, d - 1))
                elif face == "right":
                    cell = (0, py, d - 1 - px)
                elif face == "left":
                    cell = (max(0, w - 1), py, px)
                elif face == "top":
                    cell = (px, 0, d - 1 - py)
                else:
                    cell = (px, max(0, h - 1), d - 1 - py)
                yield face, fx + px, fy + py, px, py, cell


class Ctx:
    """What a decoration paints on: the cube, its face rectangles, the rarity's palette."""

    def __init__(self, img, glow, c, pal, rarity, seed):
        self.img, self.glow, self.c, self.pal, self.r, self.seed = img, glow, c, pal, rarity, seed
        self.rects = face_rects(c)
        self.size = tuple(int(n) for n in c["size"])

    def col(self, mat, t=0.5):
        return self.pal[mat].at(t)

    def dims(self, face):
        return self.rects[face][2:]

    def px(self, face, x, y, col, a=255):
        fx, fy, fw, fh = self.rects[face]
        if 0 <= x < fw and 0 <= y < fh:
            put(self.img, fx + x, fy + y, col, a)

    def lit(self, face, x, y, col, a=255):
        """A glowing texel: on the texture and on the glow layer."""
        self.px(face, x, y, col)
        fx, fy, fw, fh = self.rects[face]
        if 0 <= x < fw and 0 <= y < fh:
            put(self.glow, fx + x, fy + y, col, a)

    def get(self, face, x, y):
        fx, fy, fw, fh = self.rects[face]
        return self.img.getpixel((fx + x, fy + y))

    def tint(self, face, x, y, col, t):
        fx, fy, fw, fh = self.rects[face]
        if 0 <= x < fw and 0 <= y < fh:
            old = self.img.getpixel((fx + x, fy + y))
            put(self.img, fx + x, fy + y, mix(old[:3], col, t), old[3])

    def shade(self, face, x, y, k):
        fx, fy, fw, fh = self.rects[face]
        if 0 <= x < fw and 0 <= y < fh:
            old = self.img.getpixel((fx + x, fy + y))
            put(self.img, fx + x, fy + y, mul(old[:3], k), old[3])

    def clear(self, face, x, y):
        fx, fy, fw, fh = self.rects[face]
        if 0 <= x < fw and 0 <= y < fh:
            put(self.img, fx + x, fy + y, (0, 0, 0), 0)

    def stamp(self, face, x0, y0, rows, colors, lit=()):
        """Pixel art: one character per texel, '.' leaves it, others look up ``colors`` (in ``lit``: glowing too)."""
        for y, row in enumerate(rows):
            for x, ch in enumerate(row):
                if ch in colors:
                    col = colors[ch]
                    if col is None:
                        self.clear(face, x0 + x, y0 + y)
                    elif ch in lit:
                        self.lit(face, x0 + x, y0 + y, col)
                    else:
                        self.px(face, x0 + x, y0 + y, col)

    def each(self, faces=None):
        """(face, px, py, (xi, yi, zi)) for every texel, optionally only on some faces."""
        for face, X, Y, px, py, cell in cells(self.c):
            if faces is None or face in faces:
                yield face, px, py, cell


DECOS = {}


def deco(fn):
    DECOS[fn.__name__] = fn
    return fn


def paint(pet, rarity):
    """The texture and glow layer of one rarity. Every cube is painted (like the slayer tiers), but only the cubes this
    rarity shows may glow, so a rarity with nothing glowing gets no glow layer."""
    pal = pet.palette(rarity)
    img = Image.new("RGBA", (pet.width, pet.height), (0, 0, 0, 0))
    glow = Image.new("RGBA", (pet.width, pet.height), (0, 0, 0, 0))
    hidden = pet.hidden(rarity, blink=True)
    shown = {c["share"] or c["id"] for c in pet.cubes if c["part"] not in hidden}
    for c in pet.owners():
        seed = sum(ord(ch) * (i + 1) for i, ch in enumerate(c["id"]))
        mat = pal[c["mat"]]
        g = glow if c["id"] in shown else Image.new("RGBA", glow.size)
        for face, X, Y, px, py, cell in cells(c):
            col, a = mat.texel(face, cell, tuple(int(n) for n in c["size"]), X, Y, seed)
            put(img, X, Y, col, a)
            if mat.glow is not None:
                put(g, X, Y, mat.glow, mat.glow_alpha)
        names = c.get("deco") or ()
        for name in ((names,) if isinstance(names, str) else names):
            DECOS[name](Ctx(img, g, c, pal, rarity, seed))
    return img, glow


# ------------------------------------------------------------------------------------------------ shared colours
GOLD = ["6a4208", "a8700e", "e0a620", "fcd84c", "fffab0"]
IRON = ["3a3e48", "5a606c", "80889a", "a8b0c0", "d8dee8"]
DIAMOND = ["0e5a5e", "1a8a8a", "38c0b8", "7ae8de", "d0fff8"]
AMETHYST = ["3a1660", "5a2490", "8040c0", "a870e0", "d8b0ff"]
RUBY = ["5a0a18", "8a1424", "c42838", "ee5a62", "ffb0b0"]
SAPPHIRE = ["0a1e5a", "14308a", "2850c4", "5a88ee", "b0d0ff"]
EMERALD = ["0a4a24", "107036", "1ea050", "50d080", "b0ffd0"]
WOOD = ["3a2412", "5a3a1e", "7a522c", "9a6c3c", "b88a52"]
DARK = ["08080c", "121218", "1c1c26", "2a2a36", "3c3c4a"]
SKIN = ["8a4a58", "b06a7a", "d08c9a", "eab0bc", "ffd4dc"]
BRASS = ["4a3010", "7a5420", "b0823a", "d8ac5a", "f6dc96"]
LEATHER = ["2a1a10", "3e2818", "543822", "6a4a2e", "805c3a"]
STONE = ["3a3e46", "5a606a", "7c838c", "a0a8b0", "c8ced4"]
PINK = ["a04a5a", "c86a7a", "e88c9a", "ffb0bc", "ffd8de"]
ORANGE = ["8a4a08", "c06a10", "f09a20", "ffc050", "ffe090"]
SILVER = ["6a6a78", "9090a0", "b8b8c8", "dcdce8", "ffffff"]
WHITE = (255, 255, 255)
EYE_DARK = (18, 16, 24)


def glint(ctx, face, x, y, rarity_glow=LEGENDARY):
    """A white catch-light in an eye; it glows on Legendary pets."""
    if ctx.r >= rarity_glow:
        ctx.lit(face, x, y, WHITE)
    else:
        ctx.px(face, x, y, WHITE)


# ------------------------------------------------------------------------------------------------ shared decorations
@deco
def lid(ctx):
    """An eyelid (a plane over the eye, shown while blinking): the face colour, a dark lash line at the bottom."""
    for face, px, py, (xi, yi, zi) in ctx.each():
        w, h, d = ctx.size
        if yi == h - 1:
            ctx.px(face, px, py, mul(ctx.col(ctx.c["mat"], 0.15), 0.6))
        elif yi == h - 2:
            ctx.px(face, px, py, ctx.col(ctx.c["mat"], 0.35))


@deco
def lens(ctx):
    """A goggle lens: glassy, a frame colour on the sides, a catch-light."""
    w, h, d = ctx.size
    for face in ("left", "right", "top", "bottom", "back"):
        fw, fh = ctx.dims(face)
        for y in range(fh):
            for x in range(fw):
                ctx.px(face, x, y, ctx.col("frame", 0.45 + 0.1 * (y == 0)))
    fw, fh = ctx.dims("front")
    for y in range(fh):
        for x in range(fw):
            ctx.px("front", x, y, ctx.col("lens", 0.65 - 0.25 * (x + y) / max(1, fw + fh - 2)))
    ctx.px("front", 0, 0, mix(ctx.col("lens", 0.9), WHITE, 0.7))


@deco
def gem(ctx):
    """A cut gem: light top-left, dark bottom-right, a sparkle; glowing on Legendary pets."""
    fw, fh = ctx.dims("front")
    for y in range(fh):
        for x in range(fw):
            t = 0.85 - 0.6 * (x + y) / max(1, fw + fh - 2)
            if ctx.r >= LEGENDARY:
                ctx.lit("front", x, y, ctx.col("gem", t))
            else:
                ctx.px("front", x, y, ctx.col("gem", t))
    if ctx.r >= LEGENDARY:
        ctx.lit("front", 0, 0, WHITE)
    else:
        ctx.px("front", 0, 0, mix(ctx.col("gem", 0.9), WHITE, 0.6))


@deco
def crown_band(ctx):
    """The crown's band: gold, with gems along its front that glow."""
    fw, fh = ctx.dims("front")
    gems = ("crown_gem_a", "crown_gem_b")
    for i, x in enumerate(range(1 if fw > 3 else 0, fw, 2)):
        col = ctx.pal[gems[i % 2]]
        ctx.lit("front", x, fh - 1, hexc(col) if isinstance(col, str) else col)
    for face in ("left", "right", "back"):
        fw, fh = ctx.dims(face)
        for x in range(1, fw, 2):
            ctx.lit(face, x, fh - 1, hexc(ctx.pal["crown_gem_a"]))


@deco
def crown_point(ctx):
    for face, px, py, (xi, yi, zi) in ctx.each(SIDES):
        if yi == 0:
            ctx.lit(face, px, py, mix(ctx.col("gold", 0.95), WHITE, 0.5), 200)


@deco
def halo(ctx):
    """A halo bar: bright gold, fully glowing, with a few white sparkles."""
    for face, px, py, (xi, yi, zi) in ctx.each():
        col = ctx.col("halo", 0.7 + 0.25 * ((xi + zi) % 3 == 0))
        ctx.lit(face, px, py, col, 235)


@deco
def nose(ctx):
    """A dark nose with a shine on top."""
    for face, px, py, (xi, yi, zi) in ctx.each(("top",)):
        ctx.px(face, px, py, ctx.col("nose", 0.8 if xi == 0 else 0.6))
    fw, fh = ctx.dims("front")
    ctx.px("front", 0, 0, ctx.col("nose", 0.75))


# ================================================================================================ the Mole
def mole():
    m = PetCubes("mole", "MolePet", "The Mole pet", scale=0.76)
    m.part("pet", None, (0, 0, 0))
    m.part("body", "pet", (0, 0, 0))
    m.box("body", "body", "fur", (-3.5, -2.5, -3), (3.5, 3.5, 4), deco="mole_body")
    m.box("body", "tail", "skin", (-0.5, 1.5, 4), (0.5, 2.5, 5.5))
    m.pair("body", "body", "foot", "skin", (-3, 3, 1), (-1, 4, 3.5), deco="mole_foot")
    # the head: a fuzzy front end with a long pink snout; beady eyes under the helmet's brim
    m.part("head", "body", (0, -1.5, -3))
    m.box("head", "head", "fur", (-3, -5.5, -7.5), (3, -0.5, -2.5), deco="mole_face")
    m.box("head", "snout", "snout", (-1, -2.5, -9.5), (1, -0.5, -7.5))
    m.box("head", "nose", "skin", (-0.75, -2.5, -10.25), (0.75, -1, -9.5), deco="mole_nose")
    m.part("lids", "head", (0, -1.5, -3))
    m.pair("lids", "lids", "lid", "fur", (-2, -4.5, -7.55), (-1, -2.5, -7.55), deco="lid")
    # the miner's helmet with its lamp (the lamp lens always glows)
    m.part("helmet", "head", (0, -5.5, -5))
    m.box("helmet", "dome", "helmet", (-3.25, -7.5, -7.75), (3.25, -5.5, -2.25), deco="helmet_dome")
    m.box("helmet", "cap", "helmet", (-2.25, -8.25, -6.75), (2.25, -7.5, -3.25))
    m.box("helmet", "brim", "helmet", (-3.75, -5.75, -8.5), (3.75, -5.25, -1.75))
    m.box("helmet", "lamp", "lamp", (-1.25, -7.75, -8.5), (1.25, -5.75, -7.75), deco="lamp")
    # big digging paws at the front, palms out
    m.part_pair("paw", "body", "body", (-3.25, 1, -3), rot=(0, 0.45, -0.3))
    m.pair("paw_r", "paw_l", "paw", "skin", (-6, -0.5, -3.5), (-3, 2.5, -2.5), deco="mole_paw")
    # Uncommon and up: a pickaxe in the right paw (iron, diamond, amethyst, gold)
    m.part("pick", "paw_r", (-4.5, 0.5, -3.6), rot=(0, 0, 0.35), vis=since(UNCOMMON))
    m.box("pick", "pick_handle", "wood", (-4.75, -4, -3.85), (-4.25, 2.5, -3.35))
    m.box("pick", "pick_head", "pickaxe", (-7, -4.75, -4), (-2.5, -3.75, -3.2), deco="pick_head")
    # Epic and up: goggles strapped round the helmet
    m.part("goggles", "helmet", (0, -6.75, -5), vis=only(EPIC))
    m.box("goggles", "strap", "strap", (-3.5, -7.25, -8), (3.5, -6.5, -2))
    m.pair("goggles", "goggles", "lens", "lens", (-3.1, -7.6, -8.4), (-1.5, -6.1, -7.9), deco="lens")
    # Legendary: a crown on the (now golden) helmet
    m.part("crown", "helmet", (0, -8.5, -5), vis=only(LEGENDARY))
    m.box("crown", "crown_band", "gold", (-2.5, -9, -7), (2.5, -8, -3), deco="crown_band")
    for i, (x, z, top) in enumerate(((0, -7, -10.25), (-2, -7, -9.75), (2, -7, -9.75), (-2, -4, -9.75), (2, -4, -9.75))):
        m.box("crown", f"crown_pt{i}", "gold", (x - 0.5, top, z), (x + 0.5, -9, z + 1), sk="crown_pt", deco="crown_point")
    m.palette = mole_palette
    m.pose = mole_pose
    return m


def mole_palette(r):
    fur = (["2a2228", "3a3038", "4c4048", "605260", "766874"],
           ["4a3420", "664a2e", "82603c", "9e784e", "ba9264"],
           ["1e2636", "2c3650", "3e4a6a", "526084", "6a78a0"],
           ["261630", "38204a", "4c2c62", "62397c", "7a4a96"],
           ["141018", "201a26", "2e2636", "3e3448", "52465e"])[r]
    helmet = (["8a5a08", "c08a14", "e8b020", "ffd040", "fff0a0"],
              ["7a3408", "b05010", "e07020", "ff9a40", "ffc890"],
              ["2a4060", "3e5a80", "5a7aa0", "82a0c0", "b8d0e8"],
              ["4a2a10", "6e4018", "9a5c24", "c08038", "e0a860"],
              GOLD)[r]
    return {
        "fur": Mat(fur, "fur"),
        "skin": Mat(SKIN, "soft"),
        "snout": Mat(["5a3a40", "7a5058", "9a6a72", "b8868c", "d4a4a8"], "soft"),
        "helmet": Mat(helmet, "metal"),
        "lamp": Mat(["2a2a30", "40404a", "5a5a64", "76767e", "9a9aa2"] if r < LEGENDARY else GOLD, "metal"),
        "lamp_light": ("fff0a0", "ffd890", "d8f4ff", "f0d0ff", "ffffff")[r],
        "wood": Mat(WOOD, "flat"),
        "pickaxe": Mat((IRON, IRON, DIAMOND, AMETHYST, GOLD)[r], "metal"),
        "strap": Mat(LEATHER, "flat"),
        "frame": Mat(BRASS, "metal"),
        "lens": Mat(["3a1660", "6a2aa0", "9a50d8", "c890f0", "f0d8ff"], "flat"),
        "gold": Mat(GOLD, "metal"),
        "crown_gem_a": "ff4060",
        "crown_gem_b": "40c8ff",
        "claw": ("e8dcc0", "f0e4c8", "f4f0e8", "f0e4ff", "ffe070")[r],
    }


@deco
def mole_body(ctx):
    # a lighter belly on the front and underneath
    w, h, d = ctx.size
    for face, px, py, (xi, yi, zi) in ctx.each(("front", "bottom")):
        if face == "front" and 1 <= xi <= w - 2 and yi >= 2 or face == "bottom" and 1 <= xi <= w - 2 and zi <= 3:
            ctx.tint(face, px, py, ctx.col("fur", 0.95), 0.35)


@deco
def mole_face(ctx):
    w, h, d = ctx.size
    ctx.stamp("front", 0, 0, [
        "......",
        ".e..e.",
        ".k..k.",
        "......",
        "p....p",
    ], {"e": (70, 74, 96), "k": EYE_DARK, "p": hexc("d07888")})
    if ctx.r >= LEGENDARY:
        ctx.lit("front", 1, 1, (170, 200, 255))
        ctx.lit("front", 4, 1, (170, 200, 255))
    # a lighter muzzle round the snout
    for x in range(1, 5):
        ctx.tint("front", x, 4, ctx.col("fur", 0.9), 0.3)


@deco
def mole_nose(ctx):
    ctx.stamp("front", 0, 0, ["hl", "nn"], {"l": hexc("f2b0bc"), "h": hexc("ffe0e6"), "n": hexc("a04a5c")})


@deco
def mole_foot(ctx):
    for face, px, py, (xi, yi, zi) in ctx.each(("top",)):
        if zi == 0 and xi % 2 == 0:
            ctx.px(face, px, py, hexc(ctx.pal["claw"]))


@deco
def mole_paw(ctx):
    # a pink palm with a darker pad, three pale claws along the top
    claw = hexc(ctx.pal["claw"])
    for face in ("front", "back"):
        ctx.stamp(face, 0, 0, ["cdc", "...", ".p."], {"c": claw, "d": mul(claw, 0.8), "p": hexc("b05a6c")},
                  lit="cd" if ctx.r >= LEGENDARY else "")
    for face in ("top",):
        fw, fh = ctx.dims(face)
        for x in range(fw):
            ctx.px(face, x, fh - 1, claw if x != 1 else mul(claw, 0.8))


@deco
def helmet_dome(ctx):
    # a darker band where the strap of the lamp runs, a highlight on top
    for face, px, py, (xi, yi, zi) in ctx.each(SIDES):
        if yi == 1:
            ctx.tint(face, px, py, ctx.col("helmet", 0.1), 0.35)


@deco
def lamp(ctx):
    light = hexc(ctx.pal["lamp_light"])
    fw, fh = ctx.dims("front")
    for y in range(fh):
        for x in range(fw):
            ctx.lit("front", x, y, mix(light, WHITE, 0.5 if (x, y) == (0, 0) else 0.0))


@deco
def pick_head(ctx):
    # pointed ends: lighter tips, the middle (where the handle goes) darker
    for face, px, py, (xi, yi, zi) in ctx.each():
        w = ctx.size[0]
        if xi in (0, w - 1):
            ctx.tint(face, px, py, WHITE, 0.25)
        elif xi in (w // 2 - 1, w // 2) and face in ("front", "back"):
            ctx.shade(face, px, py, 0.75)


def mole_pose(t, look=(0.0, 0.0), blink=False, hop=0.0, dig=0.0, **_):
    """A sketch of MolePetModel's animation for previews."""
    s = math.sin
    h = s(hop * math.pi)
    paw = 0.12 * s(t * 0.15) + 0.7 * dig * s(t * 1.3) - 1.1 * h
    return {
        "pet": {"off": (0, s(t * 0.1) * 0.8 - 5 * h, 0)},
        "head": {"rot": (math.radians(look[1]) * 0.6, math.radians(look[0]) * 0.7, 0)},
        "paw_r": {"rot": (paw, 0, 0)},
        "paw_l": {"rot": (0.12 * s(t * 0.15 + 2) - 0.7 * dig * s(t * 1.3) - 1.1 * h, 0, 0)},
    }


# ================================================================================================ the Bumblebee
def bee():
    m = PetCubes("bee", "BeePet", "The Bumblebee pet", scale=0.74, translucent=True)
    m.part("pet", None, (0, 0, 0))
    m.part("body", "pet", (0, 0, 0))
    # a round striped body inside a shell of loose fluff
    m.box("body", "body", "fuzz", (-3.5, -2.5, -2.5), (3.5, 2.5, 4.5), deco="bee_stripes")
    m.box("body", "body_core", "fuzz", (-3, -3, -2.5), (3, 3, 4.5), deco="bee_stripes")
    m.box("body", "fluff", "fuzz", (-3.5, -2.5, -2.5), (3.5, 2.5, 4.5), inflate=0.4, deco="bee_fluff")
    m.box("body", "tuft", "fuzz", (-0.5, -4, -2), (0.5, -3, -1))
    m.box("body", "stinger", "stinger", (-0.5, -0.25, 4.75), (0.5, 0.75, 6.25))
    m.pair("body", "body", "leg_f", "dark", (-2.25, 2.75, -1.25), (-1.25, 4.25, -0.25), sk="leg")
    m.pair("body", "body", "leg_b", "dark", (-2.25, 2.75, 1.25), (-1.25, 4.25, 2.25), sk="leg")
    m.part("head", "body", (0, -0.5, -2.5))
    m.box("head", "head", "face", (-3, -3, -6.5), (3, 2, -2.5), deco="bee_face")
    m.part("lids", "head", (0, -0.5, -2.5))
    m.pair("lids", "lids", "lid", "face", (-3, -2, -6.55), (-1, 1, -6.55), deco="lid")
    m.part_pair("antenna", "head", "head", (-1.25, -3, -5), rot=(0.35, 0, -0.35))
    m.pair("antenna_r", "antenna_l", "stalk", "dark", (-1.5, -6, -5.25), (-1, -3, -4.75))
    m.pair("antenna_r", "antenna_l", "knob", "dark", (-1.75, -7, -5.5), (-0.75, -6, -4.5))
    # see-through wings
    m.part_pair("wing", "body", "body", (-1.25, -3.25, 1), rot=(0, 0.3, 0.65))
    m.pair("wing_r", "wing_l", "wing", "wing", (-8.25, -3.25, -1.5), (-1.25, -3.25, 3.5), deco="bee_wing")
    # Uncommon and up: a flower tucked behind the antenna (a daisy, a forget-me-not, a violet, a golden one)
    m.part("flower", "head", (2.75, -2.75, -4.5), rot=(0, -0.4, 0.2), vis=since(UNCOMMON))
    m.box("flower", "petals_a", "petal", (1.25, -4.25, -4.5), (4.25, -1.25, -4.5), deco="flower")
    m.box("flower", "petals_b", "petal", (2.75, -4.25, -6), (2.75, -1.25, -3), deco="flower")
    # Epic and up: a striped scarf with a fluttering end
    m.part("scarf", "body", (0, 0, -2.75), vis=only(EPIC))
    m.box("scarf", "scarf_ring", "scarf", (-3.5, -3.5, -3.25), (3.5, 2.5, -2.25), deco="scarf")
    m.part("scarf_tail", "scarf", (2.75, 2, -3), rot=(0, 0, -0.15))
    m.box("scarf_tail", "scarf_end", "scarf", (2.25, 2, -3.4), (3.5, 6, -2.65), deco="scarf_end")
    # Legendary: a halo
    m.part("halo", "head", (0, -8.25, -4.5), rot=(-0.25, 0, 0), vis=only(LEGENDARY))
    m.ring("halo", "halo", "halo", (0, -8.25, -4.5), half=2.75, bar=0.75, thick=0.5, deco="halo")
    m.palette = bee_palette
    m.pose = bee_pose
    return m


def bee_palette(r):
    fluff = (["a06a08", "d09a10", "f0c020", "ffdc50", "fff09a"],
             ["8a4a08", "b86a10", "e08e1c", "f4b040", "ffd480"],
             ["4a6a90", "6a90b8", "90b8dc", "b8d8f0", "e8f6ff"],
             ["4a1a6a", "6a2a90", "8a40b0", "a860c8", "c890e0"],
             ["8a5a08", "c08a14", "f0c030", "ffe070", "fffac0"])[r]
    band = (["101014", "1a1a22", "26262f", "34343e", "44444f"],
            ["1c1008", "2a180c", "3a2212", "4a2e18", "5c3a20"],
            ["101a30", "18264a", "22325e", "2e4274", "3c5288"],
            ["120a18", "1c1026", "281838", "34204a", "42285c"],
            ["c8b890", "e0d4b0", "f0e8cc", "faf4e4", "ffffff"])[r]
    wing = (["8ab0d0", "a8c8e4", "c8e0f4", "e4f2ff", "ffffff"],
            ["c0a070", "d8c098", "ecdcc0", "f8f0e0", "ffffff"],
            ["6a9ad8", "8ab4ec", "b0d0f8", "d8ecff", "ffffff"],
            ["a080d0", "b8a0e4", "d0c0f4", "ece0ff", "ffffff"],
            ["e0b040", "f0cc70", "fae4a0", "fff4d0", "ffffff"])[r]
    petal = (None, ["c8c8c0", "e0e0d8", "f4f4f0", "ffffff", "ffffff"], ["2a50b0", "3a70d8", "5a90f0", "8ab4ff", "c0d8ff"],
             ["5a1a8a", "7a2ab0", "9a48d0", "c080ec", "e0b8ff"], GOLD)[r] or GOLD
    return {
        "fuzz": Mat(fluff, "fuzz"),
        "band": Mat(band, "fuzz"),
        "face": Mat(fluff, "soft"),
        "dark": Mat(DARK if r < LEGENDARY else ["3a2a08", "5a4010", "7a5a18", "9a7424", "b89030"], "flat"),
        "stinger": Mat(DARK if r < LEGENDARY else GOLD, "metal"),
        "wing": Mat(wing, "glass", alpha=96, base=0.6),
        "vein": Mat(wing, "flat", alpha=170, base=0.3),
        "petal": Mat(petal, "flat"),
        "petal_eye": "ffd040" if r != LEGENDARY else "fff8e0",
        "scarf": Mat(RUBY, "soft"),
        "scarf_stripe": "fff4ea",
        "halo": Mat(GOLD, "flat", base=0.8),
        "blush": "f07890",
    }


def bee_band(zi):
    """The body's stripes front to back: yellow behind the head, then black, yellow, black, a yellow tail."""
    return zi in (2, 3, 5)


@deco
def bee_stripes(ctx):
    band = ctx.pal["band"]
    for face, px, py, (xi, yi, zi) in ctx.each():
        if face != "front" and face != "back" and bee_band(zi):
            n = hnoise(px, py, ctx.seed)
            ctx.px(face, px, py, band.at(0.45 + 0.35 * (n - 0.5)))


@deco
def bee_fluff(ctx):
    """The fluff shell: loose tufts on the sides (most texels see-through), the colour of the stripe underneath,
    so the outline looks fuzzy without blurring the stripes."""
    band, fuzz = ctx.pal["band"], ctx.pal["fuzz"]
    for face, px, py, (xi, yi, zi) in ctx.each():
        n = hnoise(px * 3 + 1, py * 5 + 2, ctx.seed)
        keep = {"left": 0.3, "right": 0.3, "top": 0.0, "bottom": 0.0, "front": 0.12, "back": 0.2}[face]
        if face in ("left", "right") and yi == 0:
            keep = 0.55  # the upper edge is the fuzziest
        if n >= keep:
            ctx.clear(face, px, py)
            continue
        mat = band if face in ("left", "right") and bee_band(zi) else fuzz
        ctx.px(face, px, py, mat.at(0.55 + 0.2 * (hnoise(px, py, ctx.seed + 9) - 0.5)))


@deco
def bee_face(ctx):
    blush = hexc(ctx.pal["blush"])
    ctx.stamp("front", 0, 0, [
        "......",
        "wk..wk",
        "kk..kk",
        "kkm.kk",
        ".b..b.",
    ], {"k": EYE_DARK, "w": WHITE, "m": (60, 30, 20), "b": blush})
    ctx.px("front", 3, 3, (60, 30, 20))
    if ctx.r >= LEGENDARY:
        ctx.lit("front", 0, 1, WHITE)
        ctx.lit("front", 4, 1, WHITE)


@deco
def bee_wing(ctx):
    """A rounded wing, wide at the tip: see-through, with darker veins along the edge and through the middle."""
    w, h, d = ctx.size  # 7 x 0 x 5: xi 0 is the tip on the right wing, the root at xi 6
    shape = [
        ".ooo...",
        "ooooo..",
        "ovvvvvv",
        "oooooo.",
        ".oooo..",
    ]
    vein = ctx.pal["vein"]
    wing = ctx.pal["wing"]
    for face, px, py, (xi, yi, zi) in ctx.each(("top", "bottom")):
        ch = shape[zi][xi]
        if ch == ".":
            ctx.clear(face, px, py)
            continue
        border = (zi in (0, d - 1) or xi == 0 or shape[zi][xi - 1] == "." or (xi + 1 < w and shape[zi][xi + 1] == ".")
                  or shape[zi - 1][xi] == "." or (zi + 1 < d and shape[zi + 1][xi] == "."))
        if ch == "v" or border:
            ctx.px(face, px, py, vein.at(0.35), vein.alpha)
            if ctx.r >= LEGENDARY:
                fx, fy, fw, fh = ctx.rects[face]
                put(ctx.glow, fx + px, fy + py, hexc("ffe890"), 150)
        else:
            ctx.px(face, px, py, wing.at(0.75 - 0.1 * xi / w), wing.alpha)


@deco
def flower(ctx):
    rows = ["pPp", "PeP", "pPp"]
    petal = ctx.pal["petal"]
    for face, px, py, (xi, yi, zi) in ctx.each():
        u = xi if ctx.size[0] else zi
        ch = rows[yi][u]
        if ch == "e":
            ctx.px(face, px, py, hexc(ctx.pal["petal_eye"]))
        elif ch == "P":
            ctx.px(face, px, py, petal.at(0.75))
        else:
            ctx.px(face, px, py, petal.at(0.45)) if (yi + u) % 4 else ctx.clear(face, px, py)


@deco
def scarf(ctx):
    """Red knit with a white stripe round the middle."""
    stripe = hexc(ctx.pal["scarf_stripe"])
    w, h, d = ctx.size
    for face, px, py, (xi, yi, zi) in ctx.each():
        if face in SIDES and yi == h // 2 or face in ("top", "bottom") and xi % 4 == 1:
            ctx.px(face, px, py, stripe)


@deco
def scarf_end(ctx):
    stripe = hexc(ctx.pal["scarf_stripe"])
    w, h, d = ctx.size
    for face, px, py, (xi, yi, zi) in ctx.each():
        if yi in (1, h - 2):
            ctx.px(face, px, py, stripe)
        if yi == h - 1 and face in SIDES and px % 2:
            ctx.clear(face, px, py)  # a fringe


def bee_pose(t, look=(0.0, 0.0), blink=False, hop=0.0, flap=1.0, **_):
    s = math.sin
    h = s(hop * math.pi)
    w = 0.55 * s(t * 1.9) * flap
    return {
        "pet": {"off": (0, s(t * 0.16) * 1.0 - 4 * h, 0), "rot": (s(t * 0.16 + 1) * 0.06 - hop * 2 * math.pi if hop else 0, 0, 0)},
        "head": {"rot": (math.radians(look[1]) * 0.6, math.radians(look[0]) * 0.7, 0)},
        "wing_r": {"rot": (0, 0, w)},
        "wing_l": {"rot": (0, 0, -w)},
        "scarf_tail": {"rot": (0, 0, 0.1 * s(t * 0.3))},
        "antenna_r": {"rot": (0.1 * s(t * 0.3), 0, 0)},
        "antenna_l": {"rot": (0.1 * s(t * 0.3 + 1), 0, 0)},
        "halo": {"rot": (0, t * 0.05, 0)},
    }


# ================================================================================================ the Owl
def owl():
    m = PetCubes("owl", "OwlPet", "The Owl pet", scale=0.7)
    m.part("pet", None, (0, 0, 0))
    m.part("body", "pet", (0, 0, 0))
    m.box("body", "body", "feather", (-3.5, -1.5, -3), (3.5, 4, 3), deco="owl_body")
    m.box("body", "tail", "feather_dark", (-1.5, 2.5, 2.5), (1.5, 4, 4.5))
    m.pair("body", "body", "foot", "beak", (-2.25, 3.75, -2.75), (-0.75, 4.75, -1.25), deco="talons")
    m.part_pair("wing", "body", "body", (-3.5, -1, 0), rot=(0, 0, 0.08))
    m.pair("wing_r", "wing_l", "wing", "feather", (-4.5, -1, -2.5), (-3.5, 4, 2.5), deco="owl_wing")
    # a big round head: a pale facial disc, huge eyes, a little beak, two ear tufts
    m.part("head", "body", (0, -1.5, -0.5))
    m.box("head", "head", "feather", (-4, -7.5, -3.75), (4, -1.5, 2.25), deco="owl_head")
    m.pair("head", "head", "eye", "eye", (-3.5, -6, -4.25), (-0.5, -3, -3.75), deco="owl_eye")
    m.box("head", "beak", "beak", (-0.5, -3.25, -4.5), (0.5, -1.75, -3.75), deco="beak")
    m.part("lids", "head", (0, -1.5, -0.5))
    m.pair("lids", "lids", "lid", "feather", (-3.5, -6, -4.3), (-0.5, -3, -4.3), deco="lid")
    m.part_pair("tuft", "head", "head", (-3, -7.5, -2.5), rot=(-0.15, 0, -0.35))
    m.pair("tuft_r", "tuft_l", "tuft", "feather_dark", (-3.75, -10, -3), (-2.25, -7.5, -2), deco="tuft")
    # Uncommon and up: a sprig of leaves on the head
    m.part("sprig", "head", (0.75, -7.5, -0.5), rot=(0, 0.6, 0.2), vis=since(UNCOMMON))
    m.box("sprig", "leaf_a", "leaf", (-0.75, -10.5, -0.5), (2.25, -7.5, -0.5), deco="leaf")
    m.box("sprig", "leaf_b", "leaf", (0.75, -10.5, -2), (0.75, -7.5, 1), deco="leaf")
    # Rare and up: a monocle on a chain over the left eye
    m.part("monocle", "head", (2, -4.5, -4.4), vis=since(RARE))
    m.box("monocle", "mono_t", "gold", (0.25, -6.25, -4.55), (3.75, -5.75, -4.3), sk="mono_tb")
    m.box("monocle", "mono_b", "gold", (0.25, -3.25, -4.55), (3.75, -2.75, -4.3), sk="mono_tb")
    m.box("monocle", "mono_r", "gold", (0.25, -5.75, -4.55), (0.75, -3.25, -4.3), sk="mono_s")
    m.box("monocle", "mono_l", "gold", (3.25, -5.75, -4.55), (3.75, -3.25, -4.3), sk="mono_s")
    m.box("monocle", "chain", "gold", (3.4, -2.75, -4.45), (3.7, 0.25, -4.15), deco="chain")
    # Epic and up: an amulet with a gem on a golden cord
    m.part("amulet", "body", (0, -1.25, -3), vis=since(EPIC))
    m.box("amulet", "cord", "gold", (-3.75, -1.75, -3.25), (3.75, -1.25, 3.25))
    m.box("amulet", "setting", "gold", (-1.25, -1, -3.5), (1.25, 1.5, -3))
    m.box("amulet", "jewel", "gem", (-0.75, -0.5, -3.75), (0.75, 1, -3.25), deco="gem")
    # Legendary: a halo
    m.part("halo", "head", (0, -11.25, -0.75), rot=(-0.2, 0, 0), vis=only(LEGENDARY))
    m.ring("halo", "halo", "halo", (0, -11.25, -0.75), half=3, bar=0.75, thick=0.5, deco="halo")
    m.palette = owl_palette
    m.pose = owl_pose
    return m


def owl_palette(r):
    feather = (["2e2016", "46301f", "634630", "82603f", "a07c54"],
               ["4a1e0e", "6e2c12", "94401a", "b45c28", "d07c40"],
               ["8890a0", "b0b8c4", "d4dae2", "eef2f6", "ffffff"],
               ["161a3a", "22285a", "30387a", "44509a", "5c6cb8"],
               ["8a6a30", "b8944a", "dcc070", "f2e0a0", "fffbe8"])[r]
    belly = (["8a7458", "b09a78", "d4c09c", "eadcbc", "fff4dc"],
             ["a0704a", "c08c60", "dcaa7c", "f0c8a0", "ffe4c8"],
             ["c0c8d0", "dce2e8", "eef2f6", "fafcfe", "ffffff"],
             ["6a6a9a", "8a8ab8", "aaaad4", "cacaec", "e8e8ff"],
             ["d8d0c0", "e8e2d4", "f4f0e6", "fbf9f2", "ffffff"])[r]
    dark = (["1a1008", "2e1c10", "46301f", "6a4c34", "8c6c4c"],
            ["2a0e06", "441a0a", "602610", "7c3618", "984a24"],
            ["2a2e36", "3e444e", "565e6a", "707a88", "8c96a4"],
            ["0c0e24", "14183a", "1e2450", "2a3268", "3a4484"],
            ["6a4a18", "8a6424", "aa8032", "c89c44", "e4bc5c"])[r]
    iris = ("f0b820", "ff9a20", "f8d040", "c890ff", "70f0ff")[r]
    return {
        "feather": Mat(feather, "fur"),
        "feather_dark": Mat(dark, "fur"),
        "belly": Mat(belly, "soft"),
        "fleck": "3a3e48" if r == RARE else None,
        "eye": Mat(["a07010", "c89018", "f0b820", "ffd860", "fff0a0"], "flat"),
        "iris": iris,
        "beak": Mat((ORANGE, ORANGE, ORANGE, SILVER, GOLD)[r], "flat"),
        "leaf": Mat(["1a4a14", "26681c", "3a8a28", "5aac3a", "8ad060"], "flat"),
        "stem": "5a3a1e",
        "gold": Mat(GOLD, "metal"),
        "gem": Mat(AMETHYST if r < LEGENDARY else SAPPHIRE, "gem"),
        "halo": Mat(GOLD, "flat", base=0.8),
        "rune": "70f0ff",
    }


@deco
def owl_body(ctx):
    w, h, d = ctx.size
    belly = ctx.pal["belly"]
    dark = ctx.pal["feather_dark"]
    for face, px, py, (xi, yi, zi) in ctx.each():
        if face == "front" and 1 <= xi <= w - 2 or face == "bottom" and 1 <= xi <= w - 2 and zi < 4:
            ctx.px(face, px, py, belly.at(0.5 + 0.3 * (hnoise(px, py, ctx.seed) - 0.5)))
            if face == "front" and yi in (1, 3) and xi % 2 == (yi // 2) % 2:
                ctx.px(face, px, py, dark.at(0.55))  # little chevrons
        elif face in ("left", "right", "back") and (yi + xi + zi) % 3 == 0 and yi > 0:
            ctx.px(face, px, py, dark.at(0.6))
        fleck = ctx.pal["fleck"]
        if fleck and hnoise(px * 7, py * 3, ctx.seed + 1) > 0.93:
            ctx.px(face, px, py, hexc(fleck))


@deco
def owl_head(ctx):
    w, h, d = ctx.size
    disc = [
        "........",
        ".DDDDDD.",
        "DDDDDDDD",
        "DDDDDDDD",
        ".DDDDDD.",
        "..DDDD..",
    ]
    belly = ctx.pal["belly"]
    for y, row in enumerate(disc):
        for x, ch in enumerate(row):
            if ch == "D":
                ctx.px("front", x, y, belly.at(0.45 + 0.25 * (hnoise(x, y, ctx.seed) - 0.5)))
    # a dark rim round the disc and a V between the eyes
    dark = ctx.pal["feather_dark"]
    for x, y in ((0, 1), (7, 1), (0, 4), (7, 4), (1, 5), (6, 5), (3, 1), (4, 1)):
        ctx.px("front", x, y, dark.at(0.4))
    fleck = ctx.pal["fleck"]
    for face, px, py, (xi, yi, zi) in ctx.each(("top", "left", "right", "back")):
        if (xi * 3 + zi * 5 + yi * 2) % 7 == 0:
            ctx.px(face, px, py, dark.at(0.55))
        if fleck and hnoise(px * 7, py * 3, ctx.seed + 1) > 0.92:
            ctx.px(face, px, py, hexc(fleck))


@deco
def owl_eye(ctx):
    iris = hexc(ctx.pal["iris"])
    ring = mul(iris, 0.55)
    lit = "iI" if ctx.r >= LEGENDARY else ""
    ctx.stamp("front", 0, 0, ["iiI", "iwk", "Ikk"], {"i": iris, "I": mix(iris, WHITE, 0.3), "k": EYE_DARK, "w": WHITE}, lit=lit)
    if ctx.r >= LEGENDARY:
        ctx.lit("front", 1, 1, WHITE)
    for face in ("left", "right", "top", "bottom"):
        fw, fh = ctx.dims(face)
        for y in range(fh):
            for x in range(fw):
                ctx.px(face, x, y, ring)


@deco
def beak(ctx):
    for face, px, py, (xi, yi, zi) in ctx.each():
        if yi == ctx.size[1] - 1:
            ctx.shade(face, px, py, 0.7)


@deco
def talons(ctx):
    for face, px, py, (xi, yi, zi) in ctx.each(("front", "top")):
        if xi % 2 == 0 and (face == "front" or zi == 0):
            ctx.shade(face, px, py, 0.6)


@deco
def owl_wing(ctx):
    """Folded wing feathers: rows of lighter edges, dark tips; glowing runes on Legendary."""
    dark = ctx.pal["feather_dark"]
    belly = ctx.pal["belly"]
    w, h, d = ctx.size
    for face, px, py, (xi, yi, zi) in ctx.each(("left", "right", "back", "front")):
        if yi == h - 1:
            ctx.px(face, px, py, dark.at(0.35))
        elif yi % 2 == 1 and (zi + yi // 2) % 2 == 0:
            ctx.px(face, px, py, belly.at(0.35))
        if ctx.r >= LEGENDARY and face in ("left", "right") and (yi, zi) in ((1, 1), (2, 2), (1, 3), (3, 2)):
            ctx.lit(face, px, py, hexc(ctx.pal["rune"]))


@deco
def tuft(ctx):
    for face, px, py, (xi, yi, zi) in ctx.each(SIDES):
        if yi == 0:
            ctx.shade(face, px, py, 0.6)


@deco
def leaf(ctx):
    rows = [".LL", "LlL", "sL."]
    leaf_m = ctx.pal["leaf"]
    for face, px, py, (xi, yi, zi) in ctx.each():
        u = xi if ctx.size[0] else zi
        ch = rows[yi][u]
        if ch == ".":
            ctx.clear(face, px, py)
        elif ch == "s":
            ctx.px(face, px, py, hexc(ctx.pal["stem"]))
        else:
            ctx.px(face, px, py, leaf_m.at(0.75 if ch == "l" else 0.5))


@deco
def chain(ctx):
    for face, px, py, (xi, yi, zi) in ctx.each():
        if yi % 2:
            ctx.shade(face, px, py, 0.6)


def owl_pose(t, look=(0.0, 0.0), blink=False, hop=0.0, flap=0.0, tilt=0.0, **_):
    s = math.sin
    h = s(hop * math.pi)
    open_ = (0.25 + 0.9 * abs(s(t * 0.9))) * max(flap, h)
    return {
        "pet": {"off": (0, s(t * 0.1) * 0.8 - 5 * h, 0)},
        "head": {"rot": (math.radians(look[1]) * 0.6, math.radians(look[0]) * 0.85, tilt)},
        "wing_r": {"rot": (0, 0, open_)},
        "wing_l": {"rot": (0, 0, -open_)},
        "halo": {"rot": (0, t * 0.05, 0)},
    }


# ================================================================================================ the Wolf Pup
def pup():
    m = PetCubes("pup", "PupPet", "The Wolf Pup pet", scale=0.74)
    m.part("pet", None, (0, 0, 0))
    m.part("body", "pet", (0, 0, 0))
    m.box("body", "body", "fur", (-2.5, -1.5, -3), (2.5, 2.5, 3.5), deco="pup_body")
    for name, z in (("leg_f", -2), ("leg_b", 2)):
        m.part_pair(name, "body", "body", (-1.5, 2, z))
        m.pair(name + "_r", name + "_l", name, "fur", (-2.25, 2, z - 0.75), (-0.75, 4.5, z + 0.75), deco="pup_leg", sk="leg")
    m.part("tail", "body", (0, -0.75, 3.25), rot=(0.75, 0, 0))
    m.box("tail", "tail", "fur", (-0.75, -1.5, 3.25), (0.75, 0, 7.25), deco="pup_tail")
    # a big head: floppy ears, a pale snout with a black nose, the tongue out
    m.part("head", "body", (0, -1, -3))
    m.box("head", "head", "fur", (-3.5, -5.5, -6.5), (3.5, -0.5, -1.5), deco="pup_face")
    m.box("head", "snout", "muzzle", (-1.5, -2.5, -8.5), (1.5, -0.5, -6.5), deco="pup_snout")
    m.box("head", "nose", "nose", (-0.75, -2.75, -8.75), (0.75, -1.75, -8.25), deco="nose")
    m.box("head", "tongue", "tongue", (-0.5, -0.75, -8.25), (0.5, 0.25, -7.5))
    m.part("lids", "head", (0, -1, -3))
    m.pair("lids", "lids", "lid", "fur", (-2.5, -4.5, -6.55), (-0.5, -2.5, -6.55), deco="lid")
    m.part_pair("ear", "head", "head", (-3.25, -5.25, -4.25), rot=(0, 0, 0.45))
    m.pair("ear_r", "ear_l", "ear", "ear", (-3.75, -5.25, -5.5), (-2.75, -2.25, -3), deco="pup_ear")
    # Uncommon: a red collar with a tag. Rare, Epic: a bandana. Legendary: a gold collar with a gem
    m.part("collar", "body", (0, 0.5, -2.75), vis=since(UNCOMMON))
    m.box("collar", "collar", "collar", (-2.75, -1.75, -3.25), (2.75, 2.75, -2.25), deco="collar")
    m.box("collar", "tag", "gold", (-0.5, 2.5, -3.5), (0.5, 3.5, -3.25), vis=only(UNCOMMON))
    m.box("collar", "bandana", "collar", (-2.5, 1.75, -3.4), (2.5, 4.75, -3.4), vis=span(RARE, EPIC), deco="bandana")
    m.box("collar", "collar_gem", "gem", (-0.75, 0.75, -3.6), (0.75, 2.25, -3.2), vis=only(LEGENDARY), deco="gem")
    # Epic and up: goggles pushed up on the forehead
    m.part("goggles", "head", (0, -5, -4), vis=only(EPIC))
    m.box("goggles", "strap", "strap", (-3.75, -5.25, -6.75), (3.75, -4.75, -1.25))
    m.pair("goggles", "goggles", "lens", "lens", (-3, -6.25, -7), (-1, -4.75, -6.5), deco="lens")
    # Legendary: a crown
    m.part("crown", "head", (0, -6, -4), vis=only(LEGENDARY))
    m.box("crown", "crown_band", "gold", (-2, -6.75, -5), (2, -5.75, -2.5), deco="crown_band")
    for i, (x, top) in enumerate(((-1.5, -7.75), (0, -8.25), (1.5, -7.75))):
        m.box("crown", f"crown_pt{i}", "gold", (x - 0.5, top, -5), (x + 0.5, -6.75, -4), sk="crown_pt", deco="crown_point")
    m.palette = pup_palette
    m.pose = pup_pose
    return m


def pup_palette(r):
    fur = (["2e323c", "484e5a", "646c7a", "848c9a", "a8b0bc"],
           ["3a2414", "5a3a20", "7c5430", "9c7044", "bc905c"],
           ["1e2228", "2e3440", "444c5c", "5c6678", "7a849a"],
           ["16121e", "221c2e", "302840", "403654", "524668"],
           ["a89870", "ccbc94", "e6dab8", "f6eedc", "ffffff"])[r]
    light = (["8890a0", "a8b0be", "c8d0dc", "e2e8f0", "ffffff"],
             ["b09070", "ccac88", "e4c8a4", "f4e0c4", "fff4e4"],
             ["b8c0cc", "d4dae2", "eaeef2", "f8fafc", "ffffff"],
             ["6a6084", "8a80a4", "aaa0c4", "c8c0e0", "e8e0ff"],
             ["e0d8c8", "eee8dc", "f8f4ec", "fdfbf6", "ffffff"])[r]
    collar = (RUBY, ["5a0a10", "8a141e", "b82030", "e04a50", "f08a8a"], SAPPHIRE, AMETHYST, GOLD)[r]
    return {
        "fur": Mat(fur, "fur"),
        "light": Mat(light, "soft"),
        "muzzle": Mat(light, "soft"),
        "ear": Mat([*fur[:1], *fur[:4]] if r != LEGENDARY else ["8a7a58", "a89870", "ccbc94", "e6dab8", "f6eedc"], "fur"),
        "nose": Mat(DARK, "flat"),
        "tongue": Mat(PINK, "flat"),
        "eye": ("1a1410", "1a1410", "5ab8f0", "f0a020", "50e0ff")[r],
        "collar": Mat(collar, "metal" if r == LEGENDARY else "soft"),
        "gold": Mat(GOLD, "metal"),
        "gem": Mat(SAPPHIRE, "gem"),
        "strap": Mat(LEATHER, "flat"),
        "frame": Mat(IRON, "metal"),
        "lens": Mat(["0a4a4a", "107070", "20a0a0", "50d0c8", "a0fff0"], "flat"),
        "crown_gem_a": "ff4060",
        "crown_gem_b": "40c8ff",
        "mark": "60e8ff",
    }


@deco
def pup_body(ctx):
    w, h, d = ctx.size
    light = ctx.pal["light"]
    for face, px, py, (xi, yi, zi) in ctx.each():
        chest = face == "front" and yi >= 1 or face == "bottom" or face in ("left", "right") and yi == h - 1
        if chest:
            ctx.px(face, px, py, light.at(0.5 + 0.25 * (hnoise(px, py, ctx.seed) - 0.5)))
    if ctx.r >= LEGENDARY:  # a glowing spirit stripe down the back
        for face, px, py, (xi, yi, zi) in ctx.each(("top", "back")):
            if xi == w // 2 and (face == "back" and yi <= 1 or face == "top" and zi % 2 == 0):
                ctx.lit(face, px, py, hexc(ctx.pal["mark"]))


@deco
def pup_face(ctx):
    eye = hexc(ctx.pal["eye"])
    light = ctx.pal["light"]
    mask = ctx.r == RARE  # husky: a pale mask round the eyes and muzzle
    ctx.stamp("front", 0, 0, [
        ".b...b.",
        ".we.we.",
        ".kk.kk.",
        "..mmm..",
        ".cmmmc.",
    ], {"w": WHITE, "e": eye, "k": mul(eye, 0.45) if eye != hexc("1a1410") else EYE_DARK,
        "b": light.at(0.7) if not mask else ctx.col("fur", 0.25), "m": light.at(0.55), "c": hexc("f0a0b0")},
        lit="we" if ctx.r >= LEGENDARY else "")
    if mask:
        for x, y in ((0, 2), (6, 2), (0, 3), (6, 3), (3, 1), (3, 2), (1, 3), (5, 3)):
            ctx.px("front", x, y, light.at(0.7))
    if ctx.r >= LEGENDARY:  # a glowing rune on the forehead
        ctx.lit("front", 3, 0, hexc(ctx.pal["mark"]))
        ctx.lit("front", 3, 1, hexc(ctx.pal["mark"]))


@deco
def pup_snout(ctx):
    ctx.stamp("front", 0, 0, ["...", "k.k"], {"k": mul(ctx.col("muzzle", 0.2), 0.5)})


@deco
def pup_ear(ctx):
    # pink inside (the face towards the head), darker tips
    for face, px, py, (xi, yi, zi) in ctx.each():
        if face == "left" and 0 < zi < ctx.size[2] - 1 and 0 < yi < ctx.size[1] - 1:
            ctx.px(face, px, py, hexc("e8a0ac"))
        elif yi == ctx.size[1] - 1:
            ctx.shade(face, px, py, 0.7)


@deco
def pup_leg(ctx):
    light = ctx.pal["light"]
    for face, px, py, (xi, yi, zi) in ctx.each():
        if face == "bottom" or yi == ctx.size[1] - 1:
            ctx.px(face, px, py, light.at(0.6))


@deco
def pup_tail(ctx):
    light = ctx.pal["light"]
    for face, px, py, (xi, yi, zi) in ctx.each():
        if zi == ctx.size[2] - 1:
            ctx.px(face, px, py, light.at(0.7))


@deco
def collar(ctx):
    if ctx.r == UNCOMMON:  # studs
        for face, px, py, (xi, yi, zi) in ctx.each(SIDES):
            if (xi + zi) % 2 == 0 and yi == ctx.size[1] // 2:
                ctx.px(face, px, py, ctx.col("gold", 0.8))
    elif ctx.r in (RARE, EPIC):  # bandana cloth with dots
        for face, px, py, (xi, yi, zi) in ctx.each():
            if (xi + yi + zi) % 3 == 0:
                ctx.px(face, px, py, mix(ctx.col("collar", 0.6), WHITE, 0.7))


@deco
def bandana(ctx):
    rows = ["BBBBB", ".BBB.", "..B.."]
    for face, px, py, (xi, yi, zi) in ctx.each():
        if rows[yi][xi] == ".":
            ctx.clear(face, px, py)
        elif (xi + yi) % 2 == 0:
            ctx.px(face, px, py, mix(ctx.col("collar", 0.6), WHITE, 0.7))


def pup_pose(t, look=(0.0, 0.0), blink=False, hop=0.0, speed=0.0, **_):
    s = math.sin
    h = s(hop * math.pi)
    wag = s(t * (0.7 + 0.8 * h)) * (0.5 + 0.3 * h)
    paddle = s(t * 0.6) * (0.15 + 0.6 * speed)
    flop = 0.15 * s(t * 0.1 - 0.8)
    return {
        "pet": {"off": (0, s(t * 0.1) * 0.8 - 5 * h, 0), "rot": (0, 0, s(hop * math.pi * 4) * 0.2 * h)},
        "head": {"rot": (math.radians(look[1]) * 0.6, math.radians(look[0]) * 0.7, 0)},
        "tail": {"rot": (0, wag, 0)},
        "ear_r": {"rot": (0, 0, flop)},
        "ear_l": {"rot": (0, 0, -flop)},
        "leg_f_r": {"rot": (paddle, 0, 0)}, "leg_f_l": {"rot": (-paddle, 0, 0)},
        "leg_b_r": {"rot": (-paddle, 0, 0)}, "leg_b_l": {"rot": (paddle, 0, 0)},
    }


# ================================================================================================ the Otter
def otter():
    m = PetCubes("otter", "OtterPet", "The Otter pet", scale=0.74)
    # floating on its back, reclined: the pale belly up, the head raised at the front, feet and tail up at the back
    m.part("pet", None, (0, 0, 0), rot=(-0.3, 0, 0))
    m.part("body", "pet", (0, 0, 0))
    m.box("body", "body", "fur", (-3, -1.5, -3.5), (3, 2, 3.5), deco="otter_body")
    m.part("head", "body", (0, -0.5, -3.5), rot=(0.3, 0, 0))
    m.box("head", "head", "fur", (-3, -5, -8), (3, 0, -3), deco="otter_face")
    m.box("head", "muzzle", "face", (-2, -2, -9), (2, 0, -8), deco="otter_muzzle")
    m.box("head", "nose", "nose", (-0.75, -2.5, -9.25), (0.75, -1.75, -8.75), deco="nose")
    m.part("lids", "head", (0, -0.5, -3.5))
    m.pair("lids", "lids", "lid", "fur", (-2, -4, -8.05), (-1, -2, -8.05), deco="lid")
    m.part_pair("ear", "head", "head", (-2.5, -4.75, -5.5))
    m.pair("ear_r", "ear_l", "ear", "fur_dark", (-3.25, -5.75, -6), (-2.25, -4.75, -5))
    m.part_pair("paw", "body", "body", (-1.5, -1.5, -2), rot=(0.3, 0, 0.2))
    m.pair("paw_r", "paw_l", "paw", "fur_dark", (-2.75, -3, -2.75), (-1.25, -1.5, -1.25))
    # what it holds on its chest: a pebble (Common), a fish (Uncommon to Epic), a glowing pearl (Legendary)
    m.part("held", "body", (0, -2, -2))
    m.box("held", "pebble", "stone", (-1.25, -3.25, -3), (1.25, -1.5, -1), vis=only(COMMON), deco="pebble")
    m.box("held", "fish", "fish", (-0.75, -3.75, -2.75), (0.75, -2.25, 1.25), vis=span(UNCOMMON, EPIC), deco="fish")
    m.box("held", "fin", "fin", (0, -4.5, 1.25), (0, -1.5, 3.25), vis=span(UNCOMMON, EPIC), deco="fin")
    m.box("held", "pearl", "pearl", (-1, -3.5, -3), (1, -1.5, -1), vis=only(LEGENDARY), deco="pearl")
    m.part_pair("foot", "body", "body", (-1.5, -1.5, 2.75))
    m.pair("foot_r", "foot_l", "foot", "fur_dark", (-2.25, -3.75, 2.25), (-0.75, -1.5, 3.25), deco="otter_foot")
    m.part("tail", "body", (0, 0.25, 3.5))
    m.box("tail", "tail", "fur", (-1.25, -0.5, 3.5), (1.25, 1, 6.5))
    m.box("tail", "tail_tip", "fur_dark", (-0.75, -0.25, 6.5), (0.75, 0.75, 8))
    # Rare: a starfish on its head. Epic: swimming goggles. Legendary: a crown
    m.part("starfish", "head", (0, -5.1, -5.5), rot=(-0.2, 0.3, 0), vis=only(RARE))
    m.box("starfish", "star", "star", (-2.5, -5.1, -8), (2.5, -5.1, -3), deco="star")
    m.part("goggles", "head", (0, -4.4, -5.5), vis=only(EPIC))
    m.box("goggles", "strap", "strap", (-3.25, -4.75, -8.25), (3.25, -4.25, -2.75))
    m.pair("goggles", "goggles", "lens", "lens", (-2.75, -5.75, -8.5), (-0.75, -4, -8), deco="lens")
    m.part("crown", "head", (0, -5.5, -5.5), vis=only(LEGENDARY))
    m.box("crown", "crown_band", "gold", (-1.75, -6, -7), (1.75, -5, -4.5), deco="crown_band")
    for i, (x, top) in enumerate(((-1.25, -7), (0, -7.5), (1.25, -7))):
        m.box("crown", f"crown_pt{i}", "gold", (x - 0.5, top, -7), (x + 0.5, -6, -6), sk="crown_pt", deco="crown_point")
    m.palette = otter_palette
    m.pose = otter_pose
    return m


def otter_palette(r):
    fur = (["2a1a10", "442a1a", "5e3c26", "7a5234", "966a46"],
           ["4a2c14", "6a4220", "8c5a2e", "ac7440", "c89058"],
           ["2a2626", "403a38", "58504c", "706662", "8a7e78"],
           ["1a1210", "2a1c18", "3c2a24", "503a32", "664c42"],
           ["6a4a1a", "946a28", "bc9038", "dcb450", "f4d878"])[r]
    face = (["8a7058", "aa9070", "c8ae8c", "e0caa8", "f4e4c8"],
            ["a08060", "c0a07c", "dcc09c", "f0dcbc", "fff4dc"],
            ["9a948c", "b8b2aa", "d4d0c8", "ece8e2", "ffffff"],
            ["8a6e5c", "a88a76", "c6a894", "e0c4b0", "f4e0d0"],
            ["d8d0c0", "e8e2d4", "f4f0e6", "fbf9f2", "ffffff"])[r]
    fish = (None, ["4a5a7a", "6a7c9c", "90a2bc", "b8c8dc", "e4eef8"], ["4a5a7a", "6a7c9c", "90a2bc", "b8c8dc", "e4eef8"],
            ["8a2a08", "c04a10", "f07a20", "ffa850", "ffd8a0"], None)[r] or ["4a5a7a", "6a7c9c", "90a2bc", "b8c8dc", "e4eef8"]
    return {
        "fur": Mat(fur, "fur"),
        "fur_dark": Mat([*fur[:1], *fur[:4]], "fur"),
        "face": Mat(face, "soft"),
        "nose": Mat(DARK, "flat"),
        "stone": Mat(STONE, "speck"),
        "fish": Mat(fish, "flat"),
        "fin": Mat(fish, "flat", base=0.35),
        "pearl": Mat(["c8b0c0", "e0c8d8", "f4e4ee", "fff4fa", "ffffff"], "flat", glow="fff0f8", glow_alpha=200, base=0.7),
        "star": Mat(["a03a20", "d05a30", "f08048", "ffa870", "ffd0a8"], "flat"),
        "strap": Mat(["0a3a46", "10606a", "1a8a8a", "50b8b0", "90e0d8"], "flat"),
        "frame": Mat(["0a3a46", "10606a", "1a8a8a", "50b8b0", "90e0d8"], "flat"),
        "lens": Mat(["0a3a6a", "105a9a", "2a8ad0", "6ab8f0", "c0e8ff"], "flat"),
        "gold": Mat(GOLD, "metal"),
        "crown_gem_a": "40c8ff",
        "crown_gem_b": "ff60a0",
    }


@deco
def otter_body(ctx):
    face = ctx.pal["face"]
    w, h, d = ctx.size
    for face_, px, py, (xi, yi, zi) in ctx.each():
        if face_ == "top" and 1 <= xi <= w - 2 or face_ in ("left", "right") and yi == 0 or face_ == "front":
            ctx.px(face_, px, py, face.at(0.5 + 0.25 * (hnoise(px, py, ctx.seed) - 0.5)))


@deco
def otter_face(ctx):
    face = ctx.pal["face"]
    ctx.stamp("front", 0, 0, [
        "......",
        ".w..w.",
        ".k..k.",
        "LLLLLL",
        "LLLLLL",
    ], {"w": WHITE, "k": EYE_DARK, "L": face.at(0.5)}, lit="w" if ctx.r >= LEGENDARY else "")
    for face_, px, py, (xi, yi, zi) in ctx.each(("bottom", "left", "right")):
        if face_ == "bottom" or yi >= 3:
            ctx.px(face_, px, py, face.at(0.45))


@deco
def otter_muzzle(ctx):
    ctx.stamp("front", 0, 0, ["d..d", ".mm."], {"d": mul(ctx.col("face", 0.3), 0.7), "m": mul(ctx.col("face", 0.2), 0.5)})


@deco
def otter_foot(ctx):
    for face, px, py, (xi, yi, zi) in ctx.each(("top",)):
        if xi % 2 == 0:
            ctx.shade(face, px, py, 0.7)


@deco
def pebble(ctx):
    w, h, d = ctx.size
    for face, px, py, (xi, yi, zi) in ctx.each():
        if face == "top":
            ctx.tint(face, px, py, WHITE, 0.15)
        if face == "top" and (xi, zi) == (0, 0):
            ctx.tint(face, px, py, WHITE, 0.4)


@deco
def fish(ctx):
    w, h, d = ctx.size
    for face, px, py, (xi, yi, zi) in ctx.each():
        if face in ("left", "right") and yi == 0 and zi == 0:
            ctx.px(face, px, py, EYE_DARK)  # eyes near the front
        elif face == "top":
            ctx.px(face, px, py, ctx.col("fish", 0.25 if zi % 2 else 0.35))
        elif face == "bottom":
            ctx.px(face, px, py, ctx.col("fish", 0.85))
        if ctx.r == EPIC and face in ("left", "right", "top") and zi in (1, 3):
            ctx.px(face, px, py, WHITE)  # a clownfish's stripes


@deco
def fin(ctx):
    rows = ["F.", "FF", "FF"]
    for face, px, py, (xi, yi, zi) in ctx.each():
        if rows[yi][zi] == ".":
            ctx.clear(face, px, py)


@deco
def pearl(ctx):
    for face, px, py, (xi, yi, zi) in ctx.each():
        if face == "top" and (xi, zi) == (0, 1) or face == "front" and (xi, yi) == (0, 0):
            ctx.lit(face, px, py, WHITE)


@deco
def star(ctx):
    rows = ["..S..", "SSSSS", ".SSS.", ".S.S.", "S...S"]
    for face, px, py, (xi, yi, zi) in ctx.each():
        ch = rows[ctx.size[2] - 1 - zi][xi]
        if ch == ".":
            ctx.clear(face, px, py)
        elif (xi, zi) == (2, 2):
            ctx.px(face, px, py, ctx.col("star", 0.85))
        elif (xi + zi) % 3 == 0:
            ctx.px(face, px, py, ctx.col("star", 0.75))


def otter_pose(t, look=(0.0, 0.0), blink=False, hop=0.0, roll=0.0, **_):
    s = math.sin
    h = s(hop * math.pi)
    return {
        "pet": {"off": (0, s(t * 0.08) * 0.8 - 5 * h, 0), "rot": (s(t * 0.05) * 0.04, 0, s(t * 0.07) * 0.12 + roll * 2 * math.pi)},
        "head": {"rot": (math.radians(look[1]) * 0.5, math.radians(look[0]) * 0.6, 0)},
        "foot_r": {"rot": (0.25 * s(t * 0.25), 0, 0)},
        "foot_l": {"rot": (-0.25 * s(t * 0.25), 0, 0)},
        "tail": {"rot": (0.15 * s(t * 0.15), 0, 0)},
        "paw_r": {"rot": (-0.5 * h, 0, 0)},
        "paw_l": {"rot": (-0.5 * h, 0, 0)},
    }


# ================================================================================================ the Jack o'Lantern
def jack():
    m = PetCubes("jack", "JackPet", "The Jack o'Lantern pet", scale=0.72)
    # a pumpkin head floating on its own (no body): it bobs and sways, and the carved face glows
    m.part("pet", None, (0, 0, 0))
    m.part("head", "pet", (0, 0, 0))
    m.box("head", "pumpkin", "skin", (-4, -4, -4), (4, 4, 4), deco=("pumpkin_ridges", "jack_face"))
    m.pair("head", "head", "lobe", "skin", (-6, -3, -3.25), (-4, 3, 3.25), deco="pumpkin_ridges")
    m.part("stem", "head", (0, -4, 0), rot=(0, 0, 0.18))
    m.box("stem", "stem", "stem", (-1, -6.5, -1), (1, -4, 1), deco="stem_top")
    m.box("stem", "stem_cap", "stem", (-1.5, -7.5, -1.5), (1.5, -6.5, 1.5), deco="stem_top")
    m.part("curl", "stem", (1, -6, 0))
    m.box("curl", "curl_a", "vine", (1, -6.25, -0.5), (2.5, -5.25, 0.5))
    m.box("curl", "curl_b", "vine", (2, -5.25, -0.5), (3, -4.25, 0.5))
    m.part("leaf", "stem", (-1, -5, 0), rot=(0, 0, 0.35))
    m.box("leaf", "leaf", "leaf", (-4.25, -8, 0), (-1.25, -5, 0), deco="jack_leaf")
    # Uncommon and up: two little vine arms
    m.part_pair("arm", "head", "head", (-6, 0, 0), rot=(0, 0, 0.5), vis=since(UNCOMMON))
    m.pair("arm_r", "arm_l", "arm", "vine", (-9, -0.5, -0.5), (-6, 0.5, 0.5), deco="vine_arm")
    # Rare and up: a flickering wisp floating beside it
    m.part("wisp", "pet", (7.5, -6.5, 0), vis=since(RARE))
    m.box("wisp", "wisp_a", "flame", (6.5, -7.5, -1), (8.5, -5.5, 1), deco="flame")
    m.box("wisp", "wisp_b", "flame", (7, -9.5, -0.5), (8, -7.5, 0.5), deco="flame_tip")
    # Legendary: a candle-glow halo
    m.part("halo", "head", (0, -10, 0), rot=(-0.1, 0, 0), vis=only(LEGENDARY))
    m.ring("halo", "halo", "halo", (0, -10, 0), half=4, bar=0.75, thick=0.5, deco="halo")
    m.palette = jack_palette
    m.pose = jack_pose
    return m


def jack_palette(r):
    skin = (["6a2804", "a8480a", "e06c16", "fc8e2c", "ffb866"],
            ["7a1e06", "b83a0a", "ee5c10", "ff8426", "ffae5c"],
            ["782c06", "b85a0c", "f08216", "ffa82a", "ffd066"],
            ["2c1250", "48227c", "6a3cb0", "8e5ed6", "bc98f2"],
            ["7a3004", "c05c08", "f88a14", "ffb02e", "ffe080"])[r]
    glow = ("ffd23c", "ffd84a", "ffe070", "b4ff84", "fff4b8")[r]
    stem = (["2c3a10", "3e5416", "566e22", "728c32", "92aa4a"],
            ["2c3a10", "3e5416", "566e22", "728c32", "92aa4a"],
            ["2c3a10", "3e5416", "566e22", "728c32", "92aa4a"],
            ["1c3a28", "2a5a3a", "3c7c52", "58a06c", "88c898"],
            GOLD)[r]
    leaf = (["1a4a14", "26681c", "3a8a28", "5aac3a", "8ad060"],) * 3 + (["14503a", "1e7a56", "2ea078", "58c898", "98ecc4"], GOLD)
    return {
        "skin": Mat(skin, "soft", base=0.55),
        "stem": Mat(stem, "flat"),
        "vine": Mat(stem, "flat", base=0.45),
        "leaf": Mat(leaf[r], "flat"),
        "glow": glow,
        "flame": Mat(["e07818", "f8a424", "ffcc3c", "ffe27a", "fff6c8"] if r != EPIC else ["3aa83a", "60d058", "98f080", "c4ffa8", "f0ffe0"],
                     "flat", glow=glow, glow_alpha=235, base=0.7),
        "halo": Mat(GOLD, "flat", base=0.8),
    }


@deco
def pumpkin_ridges(ctx):
    """Darker grooves between the pumpkin's ribs."""
    w, h, d = ctx.size
    for face, px, py, (xi, yi, zi) in ctx.each():
        if face in ("front", "back", "top", "bottom") and w >= 6 and xi in (2, w - 3):
            ctx.shade(face, px, py, 0.8)
        elif face in ("left", "right") and w < 6 and zi in (1, d - 2) and d >= 5:
            ctx.shade(face, px, py, 0.88)


@deco
def jack_face(ctx):
    """Carved triangle eyes and a jagged grin: lit from inside, so they glow."""
    glow = hexc(ctx.pal["glow"])
    core = mix(glow, WHITE, 0.45)
    ctx.stamp("front", 0, 0, [
        "........",
        "........",
        ".c....c.",
        "EEE..EEE",
        "........",
        "E.E..E.E",
        "EEEEEEEE",
        ".E.EE.E.",
    ], {"E": glow, "c": core}, lit="Ec")


@deco
def jack_leaf(ctx):
    rows = [".LL", "lLL", "LL."]
    for face, px, py, (xi, yi, zi) in ctx.each():
        ch = rows[yi][xi]
        if ch == ".":
            ctx.clear(face, px, py)
        else:
            ctx.px(face, px, py, ctx.col("leaf", 0.8 if ch == "l" else 0.5))


@deco
def stem_top(ctx):
    for face, px, py, (xi, yi, zi) in ctx.each(("top",)):
        ctx.shade(face, px, py, 0.8 if (xi + zi) % 2 else 1.0)


@deco
def vine_arm(ctx):
    for face, px, py, (xi, yi, zi) in ctx.each():
        if xi == 0 and face in ("left", "right", "front", "back", "top", "bottom"):
            ctx.shade(face, px, py, 1.15)


@deco
def flame(ctx):
    for face, px, py, (xi, yi, zi) in ctx.each():
        ctx.lit(face, px, py, ctx.col("flame", 0.75 - 0.15 * yi / max(1, ctx.size[1] - 1)))


@deco
def flame_tip(ctx):
    for face, px, py, (xi, yi, zi) in ctx.each():
        ctx.lit(face, px, py, ctx.col("flame", 0.95))


def jack_pose(t, look=(0.0, 0.0), blink=False, hop=0.0, roll=0.0, tilt=0.0, **_):
    s = math.sin
    h = s(hop * math.pi)
    return {
        "pet": {"off": (0, s(t * 0.12) * 0.8 - 5 * h, 0), "rot": (0, 0, roll * 2 * math.pi)},
        "head": {"rot": (math.radians(look[1]) * 0.5, math.radians(look[0]) * 0.6, s(t * 0.06) * 0.08 + tilt)},
        "stem": {"rot": (0, 0, s(t * 0.09) * 0.06)},
        "curl": {"rot": (0, 0, s(t * 0.2) * 0.15)},
        "arm_r": {"rot": (0, 0, s(t * 0.15) * 0.3)},
        "arm_l": {"rot": (0, 0, -s(t * 0.15 + 1) * 0.3)},
        "wisp": {"off": (0, s(t * 0.2) * 0.7, 0)},
        "halo": {"rot": (0, t * 0.05, 0)},
    }


PETS = [mole(), bee(), owl(), pup(), otter(), jack()]


# ------------------------------------------------------------------------------------------------ preview renderer
# cube_model.render draws one model; the pets also need planes (whose two faces coincide), translucent texels,
# CubeDeformation growth and a scene with a mock player for scale, so this is an adapted copy.
NORMALS = {"down": (0, -1, 0), "up": (0, 1, 0), "west": (-1, 0, 0), "north": (0, 0, -1), "east": (1, 0, 0), "south": (0, 0, 1)}


def faces_grown(c):
    """cube_model._faces with the cube's CubeDeformation applied to the corners (the UVs stay)."""
    gx, gy, gz = c.get("grow", (0, 0, 0))
    x0, y0, z0 = c["pos"]
    w, h, d = c["size"]
    grown = dict(c, pos=(x0 - gx, y0 - gy, z0 - gz), size=(w + 2 * gx, h + 2 * gy, d + 2 * gz))
    out = []
    # _faces takes UVs from the (ceil'd) size: feed it the original size for the UVs and fix the corners after
    plain = cm._faces(c)
    moved = cm._faces(grown) if any(c.get("grow", (0, 0, 0))) else plain
    for (name, _, uvs, rect), (_, verts, _, _) in zip(plain, moved):
        out.append((name, verts, uvs, rect))
    return out


def render_scene(items, yaw=0.6, pitch=0.35, scale=10.0, size=(320, 320), center=(0.5, 0.5), look_at=(0, 0, 0),
                 bg=(46, 50, 60), ambient=1.0):
    """items: (model, tex, glow, pose, hidden, M), M a 4x4 placing the model in the scene. Returns an RGB image."""
    W, H = size
    color = np.zeros((H, W, 3), np.float32)
    color[:] = bg
    zbuf = np.full((H, W), np.inf, np.float32)
    gbuf = np.zeros((H, W, 4), np.float32)
    view = cm._rot(pitch, 0, 0) @ cm._rot(0, yaw, 0)
    look = view @ np.array(look_at, float)
    cx, cy = W * center[0], H * center[1]
    light = np.array([0.25, -1.0, -0.55])
    light /= np.linalg.norm(light)
    translucent = []

    def proj(p):
        q = view @ p
        return np.array([(q[0] - look[0]) * scale + cx, (q[1] - look[1]) * scale + cy, q[2]])

    for model, tex, glow, pose, hidden, M in items:
        T = np.asarray(tex.convert("RGBA"), dtype=np.float32)
        G = np.asarray(glow.convert("RGBA"), dtype=np.float32) if glow is not None else None
        mats = cm.part_matrices(model, pose)
        for c in model.cubes:
            if c["part"] in hidden:
                continue
            Mc = M @ mats[c["part"]]
            R = Mc[:3, :3]
            plane = 0 in c["size"]
            for name, verts, uvs, rect in faces_grown(c):
                if rect[2] <= rect[0] or rect[3] <= rect[1]:
                    continue  # a zero-area face of a plane
                n = np.array(NORMALS[name], float)
                if c["mirror"]:
                    n[0] = -n[0]
                n = R @ n
                n /= max(1e-9, np.linalg.norm(n))
                vn = view @ n
                see_through = is_translucent(T, rect)
                if vn[2] > 1e-6 and not (plane and see_through):
                    continue  # facing away (translucent planes show both faces, as in game)
                P3 = [(Mc @ np.array([*vv, 1.0]))[:3] for vv in verts]
                S = [proj(p) for p in P3]
                shade = min(1.0, max(0.35, 0.55 + 0.45 * abs(float(np.dot(n, light))) * (1 if vn[2] <= 0 else 0.6))) * ambient
                for tri in ((0, 1, 2), (0, 2, 3)):
                    raster(S, uvs, rect, tri, T, G, color, zbuf, gbuf, shade, "opaque")
                if see_through:
                    translucent.append((float(np.mean([s[2] for s in S])), S, uvs, rect, T, shade))
    translucent.sort(key=lambda e: -e[0])
    for depth, S, uvs, rect, T, shade in translucent:
        for tri in ((0, 1, 2), (0, 2, 3)):
            raster(S, uvs, rect, tri, T, None, color, zbuf, gbuf, shade, "blend")
    a = gbuf[..., 3:4] / 255.0
    out = color * (1 - a) + gbuf[..., :3] * a
    return Image.fromarray(np.clip(out, 0, 255).astype(np.uint8))


def is_translucent(T, rect):
    region = T[int(rect[1]):int(rect[3]), int(rect[0]):int(rect[2]), 3]
    return bool(((region > 0) & (region < 250)).any())


def raster(S, uvs, rect, tri, T, G, color, zbuf, gbuf, shade, mode):
    H, W = zbuf.shape
    a, b, c = (S[i] for i in tri)
    ua, ub, uc = (np.array(uvs[i], float) for i in tri)
    xmin = max(0, int(math.floor(min(a[0], b[0], c[0]))))
    xmax = min(W - 1, int(math.ceil(max(a[0], b[0], c[0]))))
    ymin = max(0, int(math.floor(min(a[1], b[1], c[1]))))
    ymax = min(H - 1, int(math.ceil(max(a[1], b[1], c[1]))))
    if xmin > xmax or ymin > ymax:
        return
    den = (b[1] - c[1]) * (a[0] - c[0]) + (c[0] - b[0]) * (a[1] - c[1])
    if abs(den) < 1e-9:
        return
    xs, ys = np.meshgrid(np.arange(xmin, xmax + 1) + 0.5, np.arange(ymin, ymax + 1) + 0.5)
    w0 = ((b[1] - c[1]) * (xs - c[0]) + (c[0] - b[0]) * (ys - c[1])) / den
    w1 = ((c[1] - a[1]) * (xs - c[0]) + (a[0] - c[0]) * (ys - c[1])) / den
    w2 = 1 - w0 - w1
    inside = (w0 >= -1e-4) & (w1 >= -1e-4) & (w2 >= -1e-4)
    if not inside.any():
        return
    z = w0 * a[2] + w1 * b[2] + w2 * c[2]
    u = w0 * ua[0] + w1 * ub[0] + w2 * uc[0]
    v = w0 * ua[1] + w1 * ub[1] + w2 * uc[1]
    ui = np.clip(np.clip(np.floor(u), rect[0], rect[2] - 1).astype(int), 0, T.shape[1] - 1)
    vi = np.clip(np.clip(np.floor(v), rect[1], rect[3] - 1).astype(int), 0, T.shape[0] - 1)
    texel = T[vi, ui]
    alpha = texel[..., 3]
    zb = zbuf[ymin:ymax + 1, xmin:xmax + 1]
    col = color[ymin:ymax + 1, xmin:xmax + 1]
    if mode == "opaque":
        m = inside & (alpha >= 250) & (z < zb - 1e-4)
        if not m.any():
            return
        zb[m] = z[m]
        col[m] = texel[..., :3][m] * shade
        gb = gbuf[ymin:ymax + 1, xmin:xmax + 1]
        gb[m] = G[vi, ui][m] if G is not None else 0
    else:
        m = inside & (alpha > 25) & (alpha < 250) & (z < zb - 1e-4)
        if not m.any():
            return
        k = (alpha[m] / 255.0)[:, None]
        col[m] = col[m] * (1 - k) + texel[..., :3][m] * shade * k


# a mock player (Steve's proportions, flat colours) to judge the pet's size and place at the shoulder
PLAYER = cm.CubeModel("player")
PLAYER.part("root", None, (0, 0, 0))
for _cid, _mat, _pos, _size in (("head", "skin", (-4, -8, -4), (8, 8, 8)), ("hair", "hair", (-4.5, -8.5, -4.5), (8.5, 3, 8.5)),
                                ("torso", "shirt", (-4, 0, -2), (8, 12, 4)), ("arm_r", "shirt", (-8, 0, -2), (4, 12, 4)),
                                ("arm_l", "shirt", (4, 0, -2), (4, 12, 4)), ("leg_r", "pants", (-4, 12, -2), (4, 12, 4)),
                                ("leg_l", "pants", (0, 12, -2), (4, 12, 4))):
    PLAYER.cube("root", _cid, _mat, _pos, tuple(math.ceil(n) for n in _size), grow=tuple((n - math.ceil(n)) / 2 for n in _size))
PLAYER.pack(sizes=(128,))


def player_texture():
    tex = Image.new("RGBA", (PLAYER.width, PLAYER.height), (0, 0, 0, 0))
    colors = {"skin": (196, 150, 120), "hair": (70, 46, 30), "shirt": (60, 150, 160), "pants": (60, 60, 140)}
    for c in PLAYER.cubes:
        for face, X, Y, px, py, cell in cells(c):
            col = colors[c["mat"]]
            if c["id"] == "head" and face == "front" and py == 4 and px in (1, 2, 5, 6):
                col = (255, 255, 255) if px in (1, 6) else (60, 50, 120)
            put(tex, X, Y, col)
    return tex


def translate(x, y, z):
    m = np.eye(4)
    m[:3, 3] = (x, y, z)
    return m


def scaling(k):
    return np.diag([k, k, k, 1.0])


# The pet's place in player model pixels (PetLayer.REST_* x 16): beside and above the right shoulder.
REST = (-0.8 * 16, -0.32 * 16, 0.1 * 16)


def pet_textures(pet):
    out = []
    for r, name in enumerate(RARITIES):
        tex = Image.open(PET_TEX / f"{pet.name}_{name}.png")
        g = PET_TEX / f"{pet.name}_{name}_glow.png"
        out.append((tex, Image.open(g) if g.exists() else None))
    return out


def bounds(pet):
    """Centre and largest extent of the pet with every accessory, at rest, for framing previews."""
    mats = cm.part_matrices(pet)
    pts = []
    for c in pet.cubes:
        for name, verts, uvs, rect in faces_grown(c):
            pts.extend((mats[c["part"]] @ np.array([*v, 1.0]))[:3] for v in verts)
    pts = np.array(pts)
    lo, hi = pts.min(axis=0), pts.max(axis=0)
    return tuple((lo + hi) / 2), float((hi - lo).max())


def preview(out_dir):
    out_dir = Path(out_dir)
    out_dir.mkdir(parents=True, exist_ok=True)
    ptex = player_texture()
    grid = []
    for pet in PETS:
        texs = pet_textures(pet)
        centre, extent = bounds(pet)
        big = dict(scale=200 / extent, size=(260, 260), look_at=centre)

        def one(r, pose=None, blink=False, **kw):
            tex, glow = texs[r]
            args = dict(big, **kw)
            return render_scene([(pet, tex, glow, pose or pet.pose(12.0), pet.hidden(r, blink), np.eye(4))], **args)

        front = [one(r, yaw=0.55, pitch=0.3) for r in range(5)]
        back = [one(r, yaw=2.5, pitch=0.35) for r in range(5)]
        side = [one(r, yaw=math.pi / 2, pitch=0.1) for r in range(5)]
        labels = [f"{pet.name} {n}" for n in RARITIES]
        rows = [cm.sheet(front, labels), cm.sheet(back, [l + " (back)" for l in labels]), cm.sheet(side, [l + " (side)" for l in labels])]
        poses = [("blink", dict(blink=True), {}), ("look left", {}, dict(look=(-50, 0))), ("look up", {}, dict(look=(20, -30))),
                 ("hop", {}, dict(hop=0.5)), ("anim", {}, dict(flap=1.0, dig=1.0, speed=1.0, tilt=0.3, roll=0.25)),
                 ("anim t+3", {}, dict(flap=1.0, dig=1.0, speed=1.0, roll=0.5))]
        imgs = []
        for i, (lab, kw, anim) in enumerate(poses):
            imgs.append(one(LEGENDARY if i % 2 else RARE, pose=pet.pose(12.0 + 3 * (i == 5), **anim), yaw=0.55, pitch=0.3, **kw))
        rows.append(cm.sheet(imgs, [p[0] for p in poses]))
        # in game: beside a player, seen from behind and the side as a third-person camera would
        scene = []
        for r in range(5):
            tex, glow = texs[r]
            place = translate(*REST) @ scaling(pet.scale)
            items = [(PLAYER, ptex, None, None, set(), np.eye(4)), (pet, tex, glow, pet.pose(12.0), pet.hidden(r), place)]
            scene.append(render_scene(items, yaw=2.65, pitch=0.25, scale=5.0, size=(200, 200), look_at=(-4, 2, 0)))
        rows.append(cm.sheet(scene, [f"{n} (3rd person)" for n in RARITIES]))
        cm.stack(rows).save(out_dir / f"{pet.name}_preview.png")
        grid.append(cm.sheet([one(r, yaw=0.55, pitch=0.3, scale=130 / extent, size=(170, 170)) for r in range(5)], labels))
        cm.stack([cm.texture_sheet(texs[r][0], texs[r][1] or Image.new("RGBA", texs[r][0].size), zoom=4)
                  for r in (COMMON, LEGENDARY)]).save(out_dir / f"{pet.name}_textures.png")
    cm.stack(grid).save(out_dir / "all_pets.png")
    print("wrote previews to", out_dir)


def build_models():
    PET_TEX.mkdir(parents=True, exist_ok=True)
    PET_JAVA.mkdir(parents=True, exist_ok=True)
    for pet in PETS:
        pet.expand()
        rows = pet.pack(sizes=(64, 128))
        glow_mask = 0
        for r, name in enumerate(RARITIES):
            img, glow = paint(pet, r)
            img.save(PET_TEX / f"{pet.name}_{name}.png")
            glow_path = PET_TEX / f"{pet.name}_{name}_glow.png"
            if glow.getbbox():
                glow.save(glow_path)
                glow_mask |= 1 << r
            else:
                glow_path.unlink(missing_ok=True)
        out = PET_JAVA / f"{pet.cls}Layout.java"
        out.write_text(pet.java(glow_mask), encoding="utf-8", newline="\n")
        print(f"{pet.name}: {len(pet.cubes)} cubes, {len(pet.owners())} UV regions in {rows}/{pet.height} rows of "
              f"{pet.width}x{pet.height}, glow on {glow_mask:05b}, wrote {out.name}")


def main():
    only_ = sys.argv[sys.argv.index("--only") + 1] if "--only" in sys.argv else None
    if only_ in (None, "icons"):
        build_icons()
    if only_ in (None, "models"):
        build_models()
        if "--preview" in sys.argv:
            preview(sys.argv[sys.argv.index("--preview") + 1])


if __name__ == "__main__":
    main()
