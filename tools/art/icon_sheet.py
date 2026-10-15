#!/usr/bin/env python3
"""Draws one picture of every SkyRPG mod's icon with its name under it, for update notes on Discord.

    python tools/art/icon_sheet.py OUT.png [--new builder]    # --new marks modules with a NEW badge (repeatable)

Uses the 256px masters in tools/art/icons (see install_icons.py) and the banner on top. Windows fonts (Segoe UI).
"""
import argparse
from pathlib import Path

from PIL import Image, ImageDraw, ImageFilter, ImageFont

ROOT = Path(__file__).resolve().parents[2]
ICONS = ROOT / "tools" / "art" / "icons"
# Module -> name on the sheet, in the order players meet them.
NAMES = [
    ("core", "Core"), ("hub", "Hub"), ("welcome", "Welcome"), ("skills", "Skills"), ("stats", "Stats"), ("items", "Items"),
    ("economy", "Economy"), ("npcs", "NPCs"), ("progress", "Journal"), ("mining", "Mining"), ("farming", "Farming"),
    ("foraging", "Foraging"), ("fishing", "Fishing"), ("combat", "Combat"), ("enchants", "Enchanting"), ("magic", "Magic"),
    ("world", "World"), ("pets", "Pets"), ("mounts", "Mounts"), ("party", "Party"), ("events", "Events"), ("chat", "Chat"),
    ("utils", "Utilities"), ("vfx", "VFX"), ("wiki", "Wiki"), ("helper", "SkyHelper"), ("builder", "Builder"), ("brainrots", "Brainrots"), ("crates", "Crates"),
]
BG_TOP = (21, 27, 40)
BG_BOTTOM = (10, 13, 20)
GOLD = (242, 179, 61)
TEXT = (233, 237, 244)
MUTED = (141, 150, 168)
CYAN = (92, 225, 230)


def font(name, size):
    for candidate in (Path("C:/Windows/Fonts") / name, Path("/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf")):
        if candidate.exists():
            return ImageFont.truetype(str(candidate), size)
    return ImageFont.load_default()


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("out")
    parser.add_argument("--new", action="append", default=[])
    args = parser.parse_args()

    columns, cell_w, cell_h, icon = 7, 150, 168, 116
    rows = (len(NAMES) + columns - 1) // columns
    banner = Image.open(ICONS / "banner.png").convert("RGBA")
    banner = banner.resize((640, round(banner.height * 640 / banner.width)), Image.LANCZOS)
    margin = 36
    width = columns * cell_w + margin * 2
    head = banner.height + 46
    height = head + rows * cell_h + 40
    sheet = Image.new("RGBA", (width, height))
    draw = ImageDraw.Draw(sheet)
    for y in range(height):
        t = y / height
        draw.line([(0, y), (width, y)], fill=tuple(round(a + (b - a) * t) for a, b in zip(BG_TOP, BG_BOTTOM)))
    # A faint blueprint grid (drawn on its own layer so it blends).
    grid = Image.new("RGBA", (width, height))
    grid_draw = ImageDraw.Draw(grid)
    for x in range(0, width, 24):
        grid_draw.line([(x, 0), (x, height)], fill=(92, 168, 255, 14))
    for y in range(0, height, 24):
        grid_draw.line([(0, y), (width, y)], fill=(92, 168, 255, 14))
    sheet.alpha_composite(grid)
    sheet.alpha_composite(banner, ((width - banner.width) // 2, 8))
    subtitle = f"{len(NAMES)} mods, one pack"
    sub_font = font("segoeuib.ttf", 26)
    sw = draw.textlength(subtitle, font=sub_font)
    draw.text(((width - sw) / 2, banner.height + 6), subtitle, font=sub_font, fill=MUTED)

    name_font = font("segoeuib.ttf", 21)
    badge_font = font("segoeuib.ttf", 15)
    for i, (module, name) in enumerate(NAMES):
        path = ICONS / f"{module}.png"
        if not path.exists():
            continue
        cx = margin + (i % columns) * cell_w + cell_w // 2
        top = head + (i // columns) * cell_h
        image = Image.open(path).convert("RGBA").resize((icon, icon), Image.LANCZOS)
        # A soft glow under each icon.
        glow = Image.new("RGBA", (icon + 40, icon + 40), (0, 0, 0, 0))
        ImageDraw.Draw(glow).ellipse((10, 24, icon + 30, icon + 34), fill=(0, 0, 0, 140))
        glow = glow.filter(ImageFilter.GaussianBlur(10))
        sheet.alpha_composite(glow, (cx - icon // 2 - 20, top - 14))
        sheet.alpha_composite(image, (cx - icon // 2, top))
        tw = draw.textlength(name, font=name_font)
        color = CYAN if module in args.new else (GOLD if module == "core" else TEXT)
        draw.text((cx - tw / 2, top + icon + 8), name, font=name_font, fill=color)
        if module in args.new:
            label = "NEW"
            bw = draw.textlength(label, font=badge_font) + 14
            bx = cx + icon // 2 - bw + 8
            by = top - 6
            draw.rounded_rectangle((bx, by, bx + bw, by + 22), radius=6, fill=(30, 111, 120), outline=CYAN, width=2)
            draw.text((bx + 7, by + 1), label, font=badge_font, fill=(240, 255, 255))
    out = Path(args.out)
    sheet.convert("RGB").save(out, quality=92) if out.suffix.lower() in (".jpg", ".jpeg") else sheet.save(out, optimize=True)
    print(f"Wrote {out} ({width}x{height}, {out.stat().st_size // 1024} KB)")


if __name__ == "__main__":
    main()
