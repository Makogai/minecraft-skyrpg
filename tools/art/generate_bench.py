#!/usr/bin/env python3
"""Draws the Artisan Bench block and writes its block assets and data.

    python tools/art/generate_bench.py                  # writes the files below
    python tools/art/generate_bench.py --preview DIR    # also writes a preview of the faces at 8x

Writes, for skyrpg_items:artisan_bench (see items/.../crafting/Bench.java):
  * assets: textures/block/artisan_bench_{top,side,front,bottom}.png, blockstates/artisan_bench.json (four facings),
    models/block/artisan_bench.json (orientable), items/artisan_bench.json
  * data: loot_table/blocks/artisan_bench.json (drops itself), recipe/artisan_bench.json (crafting table + iron +
    Glimmer Crystals), and adds it to minecraft:mineable/axe
The bench's name in the lang file comes from generate_items.py.
Drawn from scratch: an oak workbench with a cutting mat, tools on the front rack and a glimmer inlay.
"""
import argparse
import json
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parents[2]
ASSETS = ROOT / "items" / "src" / "main" / "resources" / "assets" / "skyrpg_items"
DATA = ROOT / "items" / "src" / "main" / "resources" / "data"

OAK = ["3a2414", "5a3a20", "7c522e", "9c6c3e", "b88a52"]  # darkest first
IRON = ["3a3d44", "6a707c", "9aa1ad", "c8ced6", "eef1f5"]
GLIMMER = ["3a2a6a", "6a4ac0", "9a7ae8", "c8b0ff", "f0e8ff"]
MAT = ["1e3a32", "2a5446", "3a6e5c"]
LEATHER = ["4a2a14", "6e4020", "8e5a30"]
BRASS = ["7a5420", "b0823a", "e0b860"]


def rgb(hex_color):
    return tuple(int(hex_color[i:i + 2], 16) for i in (0, 2, 4)) + (255,)


def planks(img, x0=0, y0=0, w=16, h=16, rows=4):
    """Horizontal oak boards with seams and grain."""
    for y in range(y0, y0 + h):
        board = (y - y0) * rows // h
        seam = (y - y0) % (h // rows) == h // rows - 1
        for x in range(x0, x0 + w):
            shade = 2 + (1 if (x * 7 + board * 5) % 11 == 0 else 0) - (1 if (x * 3 + board * 13) % 9 == 0 else 0)
            if seam:
                shade = 0 if (x + board * 4) % 8 else 1
            img.putpixel((x, y), rgb(OAK[max(0, min(4, shade))]))


def frame(img, color=OAK[0], light=OAK[3]):
    for i in range(16):
        img.putpixel((i, 0), rgb(light))
        img.putpixel((0, i), rgb(light))
        img.putpixel((i, 15), rgb(color))
        img.putpixel((15, i), rgb(color))


def top():
    img = Image.new("RGBA", (16, 16))
    planks(img)
    # Cutting mat with a grid, and a glimmer inlay in the corner.
    for y in range(3, 12):
        for x in range(2, 11):
            line = (x - 2) % 3 == 0 or (y - 3) % 3 == 0
            img.putpixel((x, y), rgb(MAT[2] if line else MAT[1]))
    for i in range(2, 11):
        img.putpixel((i, 2), rgb(MAT[0]))
        img.putpixel((i, 12), rgb(MAT[0]))
    for (x, y), c in {(12, 3): 1, (13, 3): 2, (12, 4): 2, (13, 4): 3, (12, 2): 0, (14, 4): 1, (13, 5): 1}.items():
        img.putpixel((x, y), rgb(GLIMMER[c]))
    # A small hammer lying on the bench.
    for x in range(11, 15):
        img.putpixel((x, 10), rgb(OAK[3]))
    for y in range(9, 12):
        img.putpixel((14, y), rgb(IRON[2]))
        img.putpixel((15, y), rgb(IRON[1]))
    frame(img)
    return img


def side():
    img = Image.new("RGBA", (16, 16))
    planks(img, rows=4)
    # Thick top board, legs and a drawer with a brass pull.
    for x in range(16):
        img.putpixel((x, 0), rgb(OAK[4]))
        img.putpixel((x, 1), rgb(OAK[3]))
        img.putpixel((x, 2), rgb(OAK[0]))
    for y in range(3, 16):
        for x in (0, 1, 14, 15):
            img.putpixel((x, y), rgb(OAK[1] if x in (1, 15) else OAK[2]))
    for y in range(5, 10):
        for x in range(3, 13):
            edge = y in (5, 9) or x in (3, 12)
            img.putpixel((x, y), rgb(OAK[1] if edge else OAK[3]))
    for x in (7, 8):
        img.putpixel((x, 7), rgb(BRASS[2]))
    img.putpixel((7, 8), rgb(BRASS[0]))
    img.putpixel((8, 8), rgb(BRASS[0]))
    for x in range(2, 14):
        img.putpixel((x, 13), rgb(OAK[0]))
    return img


def front():
    img = side()
    # Replace the drawer with a tool rack: a saw, a chisel and a mallet on a leather strap.
    for y in range(4, 12):
        for x in range(2, 14):
            img.putpixel((x, y), rgb(OAK[1] if y == 4 or y == 11 else OAK[2]))
    for x in range(2, 14):
        img.putpixel((x, 5), rgb(LEATHER[1]))
    for x in (4, 8, 11):
        img.putpixel((x, 5), rgb(BRASS[2]))
    # Saw
    for y in range(6, 11):
        img.putpixel((3, y), rgb(IRON[3]))
        img.putpixel((4, y), rgb(IRON[2] if y % 2 else IRON[1]))
    img.putpixel((3, 6), rgb(OAK[4]))
    img.putpixel((4, 6), rgb(OAK[4]))
    # Chisel
    for y in range(6, 10):
        img.putpixel((8, y), rgb(OAK[4] if y < 8 else IRON[3]))
    img.putpixel((8, 10), rgb(IRON[4]))
    # Mallet
    for y in range(6, 11):
        img.putpixel((11, y), rgb(OAK[3]))
    for x in range(10, 13):
        img.putpixel((x, 6), rgb(OAK[4]))
        img.putpixel((x, 7), rgb(OAK[3]))
    # Glimmer gem set in the top board.
    img.putpixel((7, 1), rgb(GLIMMER[3]))
    img.putpixel((8, 1), rgb(GLIMMER[2]))
    return img


def bottom():
    img = Image.new("RGBA", (16, 16))
    planks(img, rows=2)
    return img


def write_json(path, obj):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(obj, indent=2) + "\n", encoding="utf-8", newline="\n")


def build():
    faces = {"top": top(), "side": side(), "front": front(), "bottom": bottom()}
    for name, img in faces.items():
        out = ASSETS / "textures" / "block" / f"artisan_bench_{name}.png"
        out.parent.mkdir(parents=True, exist_ok=True)
        img.save(out)
    write_json(ASSETS / "models" / "block" / "artisan_bench.json", {
        "parent": "minecraft:block/orientable_with_bottom",
        "textures": {f: f"skyrpg_items:block/artisan_bench_{f}" for f in ("top", "side", "front", "bottom")},
    })
    rotations = {"north": 0, "east": 90, "south": 180, "west": 270}
    write_json(ASSETS / "blockstates" / "artisan_bench.json", {"variants": {
        f"facing={facing}": ({"model": "skyrpg_items:block/artisan_bench", "y": y} if y else {"model": "skyrpg_items:block/artisan_bench"})
        for facing, y in rotations.items()}})
    write_json(ASSETS / "items" / "artisan_bench.json", {"model": {"type": "minecraft:model", "model": "skyrpg_items:block/artisan_bench"}})
    write_json(DATA / "skyrpg_items" / "loot_table" / "blocks" / "artisan_bench.json", {
        "type": "minecraft:block",
        "pools": [{"rolls": 1, "entries": [{"type": "minecraft:item", "name": "skyrpg_items:artisan_bench"}],
                   "conditions": [{"condition": "minecraft:survives_explosion"}]}],
        "random_sequence": "skyrpg_items:blocks/artisan_bench"})
    write_json(DATA / "skyrpg_items" / "recipe" / "artisan_bench.json", {
        "type": "minecraft:crafting_shaped",
        "category": "misc",
        "key": {"G": "skyrpg_mining:glimmer_crystal", "I": "minecraft:iron_ingot", "C": "minecraft:crafting_table"},
        "pattern": ["GIG", "ICI", "GIG"],
        "result": {"id": "skyrpg_items:artisan_bench"}})
    tag = DATA / "minecraft" / "tags" / "block" / "mineable" / "axe.json"
    values = json.loads(tag.read_text(encoding="utf-8"))["values"] if tag.exists() else []
    if "skyrpg_items:artisan_bench" not in values:
        values.append("skyrpg_items:artisan_bench")
    write_json(tag, {"replace": False, "values": values})
    return faces


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--preview", type=Path, help="directory to write the preview to")
    args = parser.parse_args()
    faces = build()
    if args.preview:
        args.preview.mkdir(parents=True, exist_ok=True)
        sheet = Image.new("RGBA", (len(faces) * 136 + 8, 144), (24, 28, 38, 255))
        for i, img in enumerate(faces.values()):
            sheet.alpha_composite(img.resize((128, 128), Image.NEAREST), (8 + i * 136, 8))
        sheet.save(args.preview / "artisan_bench.png")
    print("wrote the Artisan Bench's textures, models, blockstate, loot table, recipe and tag")
