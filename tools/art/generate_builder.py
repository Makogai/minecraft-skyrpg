#!/usr/bin/env python3
"""Draws the Builder Station block and writes its block assets and data.

    python tools/art/generate_builder.py                  # writes the files below
    python tools/art/generate_builder.py --preview DIR    # also writes a preview of the faces at 8x

Writes, for skyrpg_builder:builder_station (see builder/.../BuilderBlock.java):
  * assets: textures/block/builder_station_{top,side,front,front_active,bottom}.png, blockstates/builder_station.json
    (four facings x active), models/block/builder_station{,_active}.json (orientable with bottom),
    items/builder_station.json, lang/en_us.json
  * data: loot_table/blocks/builder_station.json (drops itself), skyrpg/recipes/builder_station.json (Artisan Bench,
    Foraging 5), and adds it to minecraft:mineable/axe
Drawn from scratch: a drafting table with blueprint paper pinned on top (white grid lines, a house plan), oak sides
with rulers and drawers, and a crystal hologram lens on the front that glows cyan while it builds.
"""
import argparse
import json
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parents[2]
ASSETS = ROOT / "builder" / "src" / "main" / "resources" / "assets" / "skyrpg_builder"
DATA = ROOT / "builder" / "src" / "main" / "resources" / "data"

OAK = ["3a2414", "5a3a20", "7c522e", "9c6c3e", "b88a52"]  # darkest first
PAPER = ["1c3f73", "245090", "2d62ab", "3a74c2"]  # blueprint blues, darkest first
LINE = "a8cdf2"
INK = "eaf4ff"
BRASS = ["7a5420", "b0823a", "e0b860"]
LENS_OFF = ["1d3640", "2c5866", "3f7f8f", "6fb4c2"]
LENS_ON = ["2a8f99", "5ce1e6", "a8f4f6", "f0ffff"]


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


def frame(img, dark=OAK[0], light=OAK[3]):
    for i in range(16):
        img.putpixel((i, 0), rgb(light))
        img.putpixel((0, i), rgb(light))
        img.putpixel((i, 15), rgb(dark))
        img.putpixel((15, i), rgb(dark))


def top():
    img = Image.new("RGBA", (16, 16))
    planks(img)
    frame(img)
    # Blueprint paper with a faint grid.
    for y in range(2, 14):
        for x in range(2, 14):
            shade = 2 if (x + y) % 7 else 1
            if x in (2, 13) or y in (2, 13):
                shade = 1
            img.putpixel((x, y), rgb(PAPER[shade]))
            if (x - 2) % 3 == 0 or (y - 2) % 3 == 0:
                img.putpixel((x, y), rgb(PAPER[3]))
    # A little house plan in white ink: walls, roof line, door.
    for x in range(4, 12):
        img.putpixel((x, 11), rgb(INK))
    for y in range(7, 12):
        img.putpixel((4, y), rgb(INK))
        img.putpixel((11, y), rgb(INK))
    roof = [(4, 7), (5, 6), (6, 5), (7, 4), (8, 4), (9, 5), (10, 6), (11, 7)]
    for x, y in roof:
        img.putpixel((x, y), rgb(INK))
    for y in range(9, 12):
        img.putpixel((7, y), rgb(LINE))
        img.putpixel((8, y), rgb(LINE))
    # Brass pins in the corners.
    for x, y in [(2, 2), (13, 2), (2, 13), (13, 13)]:
        img.putpixel((x, y), rgb(BRASS[2]))
    return img


def side():
    img = Image.new("RGBA", (16, 16))
    planks(img)
    frame(img)
    # Table top edge.
    for x in range(16):
        img.putpixel((x, 1), rgb(OAK[4]))
        img.putpixel((x, 2), rgb(OAK[1]))
    # A ruler along the top with tick marks.
    for x in range(1, 15):
        img.putpixel((x, 4), rgb(BRASS[1]))
        img.putpixel((x, 5), rgb(BRASS[0] if x % 2 else BRASS[2]))
    # Two drawers with brass handles.
    for x0 in (2, 9):
        for y in range(8, 14):
            for x in range(x0, x0 + 5):
                edge = y in (8, 13) or x in (x0, x0 + 4)
                img.putpixel((x, y), rgb(OAK[1] if edge else OAK[3]))
        img.putpixel((x0 + 2, 10), rgb(BRASS[2]))
        img.putpixel((x0 + 2, 11), rgb(BRASS[1]))
    return img


def front(lens):
    img = side()
    # Replace the drawers with a hologram projector lens in a brass ring.
    for y in range(7, 15):
        for x in range(1, 15):
            img.putpixel((x, y), rgb(OAK[2] if (x + y) % 5 else OAK[1]))
    cx, cy = 7.5, 10.5
    for y in range(6, 15):
        for x in range(3, 13):
            d = ((x - cx) ** 2 + (y - cy) ** 2) ** 0.5
            if d <= 2.6:
                # Glass: brighter towards the top-left (light), with a sparkle.
                t = max(0.0, min(1.0, 1 - ((x - cx + 1) + (y - cy + 1)) / 6))
                img.putpixel((x, y), rgb(lens[1 + int(t * 2.4)]))
            elif d <= 3.6:
                img.putpixel((x, y), rgb(BRASS[1] if (x + y) % 3 else BRASS[2]))
            elif d <= 4.2:
                img.putpixel((x, y), rgb(BRASS[0]))
    img.putpixel((6, 9), rgb(lens[3]))
    img.putpixel((7, 9), rgb(lens[2]))
    return img


def bottom():
    img = Image.new("RGBA", (16, 16))
    planks(img, rows=2)
    frame(img, OAK[0], OAK[1])
    return img


def write_json(path, data):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(data, indent=2) + "\n", encoding="utf-8")


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--preview")
    args = parser.parse_args()
    textures = {
        "builder_station_top": top(),
        "builder_station_side": side(),
        "builder_station_front": front(LENS_OFF),
        "builder_station_front_active": front(LENS_ON),
        "builder_station_bottom": bottom(),
    }
    (ASSETS / "textures" / "block").mkdir(parents=True, exist_ok=True)
    for name, img in textures.items():
        img.save(ASSETS / "textures" / "block" / f"{name}.png")
    if args.preview:
        out = Path(args.preview)
        out.mkdir(parents=True, exist_ok=True)
        sheet = Image.new("RGBA", (16 * 5 + 4 * 6, 24), (30, 32, 40, 255))
        for i, img in enumerate(textures.values()):
            sheet.paste(img, (4 + i * 20, 4))
        sheet.resize((sheet.width * 8, sheet.height * 8), Image.NEAREST).save(out / "builder_station_faces.png")

    for suffix, front_texture in (("", "builder_station_front"), ("_active", "builder_station_front_active")):
        write_json(ASSETS / "models" / "block" / f"builder_station{suffix}.json", {
            "parent": "minecraft:block/orientable_with_bottom",
            "textures": {
                "top": "skyrpg_builder:block/builder_station_top",
                "bottom": "skyrpg_builder:block/builder_station_bottom",
                "side": "skyrpg_builder:block/builder_station_side",
                "front": f"skyrpg_builder:block/{front_texture}",
            },
        })
    variants = {}
    for facing, y in (("north", 0), ("east", 90), ("south", 180), ("west", 270)):
        for active in ("false", "true"):
            variant = {"model": "skyrpg_builder:block/builder_station" + ("_active" if active == "true" else "")}
            if y:
                variant["y"] = y
            variants[f"active={active},facing={facing}"] = variant
    write_json(ASSETS / "blockstates" / "builder_station.json", {"variants": variants})
    write_json(ASSETS / "items" / "builder_station.json", {
        "model": {"type": "minecraft:model", "model": "skyrpg_builder:block/builder_station"}})
    write_json(ASSETS / "lang" / "en_us.json", {
        "block.skyrpg_builder.builder_station": "Builder Station",
        "item.skyrpg_builder.builder_station": "Builder Station",
    })

    write_json(DATA / "skyrpg_builder" / "loot_table" / "blocks" / "builder_station.json", {
        "type": "minecraft:block",
        "pools": [{"rolls": 1, "entries": [{"type": "minecraft:item", "name": "skyrpg_builder:builder_station"}],
                   "condition": {"type": "minecraft:survives_explosion"}}],
        "random_sequence": "skyrpg_builder:blocks/builder_station",
    })
    write_json(DATA / "skyrpg_builder" / "skyrpg" / "recipes" / "builder_station.json", {
        "category": "general",
        "result": "skyrpg_builder:builder_station",
        "ingredients": {
            "minecraft:crafting_table": 1,
            "minecraft:paper": 8,
            "minecraft:iron_ingot": 6,
            "minecraft:redstone": 8,
            "minecraft:amethyst_shard": 4,
        },
        "requires": {"skill": "skyrpg_skills:foraging", "level": 5},
    })
    write_json(DATA / "minecraft" / "tags" / "block" / "mineable" / "axe.json", {"values": ["skyrpg_builder:builder_station"]})
    print("Wrote the Builder Station assets and data.")


if __name__ == "__main__":
    main()
