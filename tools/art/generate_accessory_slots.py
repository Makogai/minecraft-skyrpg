#!/usr/bin/env python3
"""Draws the empty accessory slot icons: the faint outlines an empty amulet, cloak, belt, bracers or backpack slot shows.

    python tools/art/generate_accessory_slots.py                  # writes the sprites below
    python tools/art/generate_accessory_slots.py --preview DIR    # also writes a preview sheet to DIR

Writes items/src/main/resources/assets/skyrpg_items/textures/gui/sprites/container/slot/<slot>.png (16x16,
in the gui atlas as skyrpg_items:container/slot/<slot>, see AccessorySlot.emptyIcon). They follow vanilla's
armour slot icons: a 1px outline in one colour, #555555, which reads on vanilla's grey slots and the theme's
dark ones alike (the theme pack leaves slot icons alone). Drawn here from masks, not copied from vanilla.
"""
import argparse
from pathlib import Path

from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parents[2]
OUT = ROOT / "items" / "src" / "main" / "resources" / "assets" / "skyrpg_items" / "textures" / "gui" / "sprites" / "container" / "slot"
COLOR = (0x55, 0x55, 0x55, 255)

# '#' is the outline, '.' is empty. Same order as AccessorySlot.
ICONS = {
    # A necklace hanging from the neck, with a cut gem.
    "amulet": """
................
.#............#.
.#............#.
..#..........#..
..#..........#..
...#........#...
....##....##....
......#..#......
.......##.......
......#..#......
.....#....#.....
....#......#....
.....#....#.....
......#..#......
.......##.......
................
""",
    # A cape from behind: collar and clasp, widening to a notched hem.
    "cloak": """
................
.....######.....
....#..##..#....
...#..#..#..#...
...#...##...#...
..#..........#..
..#..........#..
..#..........#..
..#..........#..
.#............#.
.#............#.
.#............#.
.#............#.
.#............#.
.#.###.##.###.#.
..#...#..#...#..
""",
    # A strap through a buckle with its prong, a hole in the loose end.
    "belt": """
................
................
................
................
....######......
#####....######.
#...#....#.....#
#...####.#..#..#
#...#....#.....#
#####....######.
....######......
................
................
................
................
................
""",
    # A pair of arm guards, wide at the elbow and strapped towards the wrist.
    "bracers": """
................
................
..####....####..
.#....#..#....#.
.######..######.
.#....#..#....#.
.#....#..#....#.
..#..#....#..#..
..####....####..
..#..#....#..#..
..#..#....#..#..
..####....####..
...##......##...
................
................
................
""",
    # Not an accessory: the backpack slots right of the inventory. A pack with a carry loop and a pocket.
    "backpack": """
................
.....######.....
....#......#....
....########....
...#........#...
..#..........#..
..#.########.#..
..#.#..##..#.#..
..#.#......#.#..
..#.########.#..
..#..........#..
..#..........#..
..#..........#..
..#..........#..
...##########...
................
""",
}


def draw(mask):
    rows = mask.strip("\n").split("\n")
    assert len(rows) == 16 and all(len(row) == 16 for row in rows), "masks are 16x16"
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    for y, row in enumerate(rows):
        for x, ch in enumerate(row):
            if ch == "#":
                img.putpixel((x, y), COLOR)
    return img


def build():
    icons = {}
    OUT.mkdir(parents=True, exist_ok=True)
    for name, mask in ICONS.items():
        icons[name] = draw(mask)
        icons[name].save(OUT / f"{name}.png")
    return icons


# ---------------------------------------------------------------- preview
# Slot wells as the inventory draws them: (fill, top-left edge, bottom-right edge).
WELLS = {
    "vanilla": ((0x8B, 0x8B, 0x8B), (0x37, 0x37, 0x37), (0xFF, 0xFF, 0xFF)),
    "theme": ((0x0C, 0x0F, 0x15), (0x06, 0x08, 0x0C), (0x23, 0x2A, 0x37)),
}


def well(icon, colors):
    fill, shadow, light = (c + (255,) for c in colors)
    img = Image.new("RGBA", (18, 18), light)
    d = ImageDraw.Draw(img)
    d.rectangle([0, 0, 16, 16], fill=shadow)
    d.rectangle([1, 1, 16, 16], fill=fill)
    img.putpixel((17, 0), fill)
    img.putpixel((0, 17), fill)
    img.alpha_composite(icon, (1, 1))
    return img


def preview(icons, path, f=6):
    """Each icon in a vanilla and a themed slot at 6x, then the column at 2x as the inventory shows it."""
    cell = 18 * f
    img = Image.new("RGBA", (len(icons) * (cell + 12) + 12 + 2 * 18 * 2 + 24, len(WELLS) * (cell + 24) + 12), (24, 28, 38, 255))
    d = ImageDraw.Draw(img)
    for row, colors in enumerate(WELLS.values()):
        y = 12 + row * (cell + 24)
        for i, (name, icon) in enumerate(icons.items()):
            x = 12 + i * (cell + 12)
            img.alpha_composite(well(icon, colors).resize((cell, cell), Image.NEAREST), (x, y))
            d.text((x, y + cell + 4), name, fill=(200, 206, 220))
        x = 12 + len(icons) * (cell + 12) + row * (18 * 2 + 12)
        for i, icon in enumerate(icons.values()):
            img.alpha_composite(well(icon, colors).resize((36, 36), Image.NEAREST), (x, 12 + i * 36))
    img.save(path)


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--preview", type=Path, help="directory to write the preview sheet to")
    args = parser.parse_args()
    icons = build()
    if args.preview:
        args.preview.mkdir(parents=True, exist_ok=True)
        preview(icons, args.preview / "accessory_slots.png")
    print(f"wrote {len(icons)} accessory slot icons to {OUT.relative_to(ROOT)}")
