#!/usr/bin/env python3
"""Generates the SkyRPG chat stickers: 48x48 pixel-art PNGs drawn here with Pillow (no anti-aliasing).

    python tools/art/generate_stickers.py                 # writes the PNGs and the sticker list
    python tools/art/generate_stickers.py --preview DIR   # also writes a 4x contact sheet (preview.png) to DIR

Writes under chat/src/main/resources/assets/skyrpg_chat:
  * textures/sticker/<name>.png    48x48, transparent outside the rounded sticker shape
  * stickers.txt                   the names in display order (the chat mod reads this list)
Every sticker is a rounded plate with a 1px dark outline and a 1px white sticker edge, an emblem, and an optional
caption. Send one with [s:name] (or [sticker:name]) in chat; /stickers lists them.
"""
import sys
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont

ROOT = Path(__file__).resolve().parent.parent.parent
RES = ROOT / "chat" / "src" / "main" / "resources" / "assets" / "skyrpg_chat"
SIZE = 48

INK = (0x1a, 0x14, 0x24, 255)
WHITE = (255, 255, 255, 255)
YELLOW = (0xf2, 0xb3, 0x3d, 255)
YELLOW_L = (0xff, 0xe0, 0x8a, 255)
YELLOW_D = (0xb8, 0x79, 0x1f, 255)
RED = (0xff, 0x5a, 0x5a, 255)
RED_D = (0xb0, 0x2a, 0x3a, 255)
PINK = (0xff, 0xa8, 0xc8, 255)
GREEN = (0x5c, 0xd6, 0x8a, 255)
GREEN_D = (0x2c, 0x8a, 0x52, 255)
BLUE = (0x5c, 0xa8, 0xff, 255)
BLUE_D = (0x2e, 0x62, 0xc0, 255)
PURPLE = (0x6a, 0x46, 0xb0, 255)
PURPLE_L = (0xb9, 0x8c, 0xff, 255)
BROWN = (0x9a, 0x6a, 0x3a, 255)
BROWN_D = (0x5e, 0x3c, 0x20, 255)
GREY = (0xc9, 0xce, 0xd8, 255)
GREY_D = (0x58, 0x61, 0x74, 255)
ORANGE = (0xff, 0x8a, 0x2a, 255)

_font_cache = {}


def font(size):
    if size not in _font_cache:
        f = ImageFont.load_default(size=size)
        _font_cache[size] = f
    return _font_cache[size]


def plate(color, edge):
    """A fresh canvas with the sticker plate: white edge, ink outline, coloured body with a lighter top band."""
    img = Image.new("RGBA", (SIZE, SIZE), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    d.rounded_rectangle((0, 0, SIZE - 1, SIZE - 1), 8, fill=WHITE)
    d.rounded_rectangle((1, 1, SIZE - 2, SIZE - 2), 7, fill=INK)
    d.rounded_rectangle((2, 2, SIZE - 3, SIZE - 3), 6, fill=color)
    d.rounded_rectangle((2, 2, SIZE - 3, 11), 6, fill=edge)
    d.rectangle((3, 8, SIZE - 4, 11), fill=color)
    return img, d


def text(d, xy, s, size, fill=WHITE, outline=INK, anchor="mm"):
    d.fontmode = "1"
    f = font(size)
    x, y = xy
    for dx, dy in ((-1, 0), (1, 0), (0, -1), (0, 1), (-1, -1), (1, 1), (-1, 1), (1, -1)):
        d.text((x + dx, y + dy), s, font=f, fill=outline, anchor=anchor)
    d.text((x, y), s, font=f, fill=fill, anchor=anchor)


def circle(d, cx, cy, r, fill, outline=INK):
    d.ellipse((cx - r, cy - r, cx + r, cy + r), fill=fill, outline=outline)


def face(d, cx=24, cy=22, r=15):
    circle(d, cx, cy, r, YELLOW)
    d.arc((cx - r + 2, cy - r + 2, cx + r - 2, cy + r - 2), 200, 300, fill=YELLOW_L)


def caption(d, s):
    text(d, (24, 40), s, 9)


# ------------------------------------------------------------------------------------------------ stickers
def gg():
    img, d = plate((0x2e, 0x62, 0xc0, 255), (0x5c, 0xa8, 0xff, 255))
    text(d, (24, 22), "GG", 26, YELLOW_L)
    d.line((10, 38, 38, 38), fill=YELLOW, width=2)
    return img


def wow():
    img, d = plate((0x6a, 0x46, 0xb0, 255), (0xb9, 0x8c, 0xff, 255))
    face(d, 24, 19, 13)
    d.ellipse((17, 15, 21, 21), fill=WHITE, outline=INK)
    d.ellipse((27, 15, 31, 21), fill=WHITE, outline=INK)
    d.rectangle((19, 18, 20, 19), fill=INK)
    d.rectangle((29, 18, 30, 19), fill=INK)
    d.ellipse((20, 24, 28, 31), fill=INK)
    d.line((15, 9, 18, 11), fill=INK, width=2)
    d.line((33, 9, 30, 11), fill=INK, width=2)
    text(d, (24, 40), "WOW", 11, YELLOW_L)
    return img


def lol():
    img, d = plate((0x2c, 0x8a, 0x52, 255), (0x5c, 0xd6, 0x8a, 255))
    face(d, 24, 19, 13)
    d.line((16, 15, 20, 17), fill=INK, width=2)
    d.line((16, 19, 20, 17), fill=INK, width=2)
    d.line((32, 15, 28, 17), fill=INK, width=2)
    d.line((32, 19, 28, 17), fill=INK, width=2)
    d.pieslice((16, 20, 32, 34), 0, 180, fill=INK)
    d.rectangle((18, 22, 30, 23), fill=WHITE)
    d.ellipse((13, 22, 16, 27), fill=BLUE)
    d.ellipse((32, 22, 35, 27), fill=BLUE)
    text(d, (24, 41), "LOL", 10, YELLOW_L)
    return img


def rip():
    img, d = plate((0x3a, 0x40, 0x52, 255), (0x58, 0x61, 0x74, 255))
    d.rectangle((14, 14, 33, 38), fill=GREY, outline=INK)
    d.pieslice((14, 6, 33, 24), 180, 360, fill=GREY, outline=INK)
    d.rectangle((15, 15, 32, 38), fill=GREY)
    d.line((17, 16, 17, 36), fill=WHITE)
    d.rectangle((14, 38, 33, 40), fill=GREY_D, outline=INK)
    text(d, (24, 21), "RIP", 9, GREY_D, outline=WHITE)
    d.line((21, 31, 27, 31), fill=GREY_D)
    d.line((24, 28, 24, 36), fill=GREY_D)
    d.rectangle((6, 38, 12, 40), fill=GREEN_D)
    d.rectangle((36, 38, 42, 40), fill=GREEN_D)
    return img


def hype():
    img, d = plate((0xb0, 0x2a, 0x3a, 255), (0xff, 0x5a, 0x5a, 255))
    pts = [(24, 4), (28, 14), (38, 10), (33, 20), (44, 24), (33, 28), (38, 38), (28, 34), (24, 44), (20, 34), (10, 38), (15, 28), (4, 24),
           (15, 20), (10, 10), (20, 14)]
    d.polygon(pts, fill=YELLOW, outline=INK)
    text(d, (24, 24), "HYPE", 13, RED_D, outline=WHITE)
    return img


def thumbsup():
    img, d = plate((0x2e, 0x62, 0xc0, 255), (0x5c, 0xa8, 0xff, 255))
    d.rectangle((9, 21, 15, 38), fill=BLUE_D, outline=INK)
    d.rounded_rectangle((15, 21, 38, 38), 3, fill=YELLOW, outline=INK)
    d.rounded_rectangle((18, 8, 25, 24), 3, fill=YELLOW, outline=INK)
    d.rectangle((16, 22, 24, 30), fill=YELLOW)
    for y in (27, 31, 35):
        d.line((20, y, 36, y), fill=YELLOW_D)
    d.line((19, 10, 19, 18), fill=YELLOW_L)
    return img


def hearteyes():
    img, d = plate((0xb0, 0x2a, 0x3a, 255), (0xff, 0x7a, 0x8a, 255))
    face(d, 24, 22, 15)
    for cx in (17, 31):
        d.polygon([(cx - 4, 17), (cx - 2, 15), (cx, 17), (cx + 2, 15), (cx + 4, 17), (cx, 23)], fill=RED, outline=INK)
    d.pieslice((16, 25, 32, 37), 0, 180, fill=INK)
    d.rectangle((18, 29, 30, 30), fill=WHITE)
    d.ellipse((11, 27, 15, 30), fill=PINK)
    d.ellipse((33, 27, 37, 30), fill=PINK)
    text(d, (24, 42), "<3", 8, PINK)
    return img


def angry():
    img, d = plate((0xb0, 0x2a, 0x3a, 255), (0xff, 0x5a, 0x5a, 255))
    circle(d, 24, 22, 15, RED)
    d.polygon([(11, 12), (21, 18), (21, 21), (11, 15)], fill=INK)
    d.polygon([(37, 12), (27, 18), (27, 21), (37, 15)], fill=INK)
    d.rectangle((16, 21, 20, 25), fill=WHITE, outline=INK)
    d.rectangle((28, 21, 32, 25), fill=WHITE, outline=INK)
    d.rectangle((18, 22, 20, 25), fill=INK)
    d.rectangle((28, 22, 30, 25), fill=INK)
    d.arc((15, 29, 33, 41), 180, 360, fill=INK, width=2)
    d.line((19, 31, 29, 31), fill=INK)
    text(d, (24, 43), "GRR", 7, YELLOW_L)
    return img


def sleepy():
    img, d = plate((0x1e, 0x2a, 0x5a, 255), (0x2e, 0x62, 0xc0, 255))
    face(d, 22, 25, 14)
    d.line((14, 24, 20, 24), fill=INK, width=2)
    d.line((24, 24, 30, 24), fill=INK, width=2)
    d.ellipse((19, 30, 27, 36), fill=INK)
    d.ellipse((30, 30, 33, 34), fill=BLUE)
    text(d, (34, 12), "z", 10, WHITE)
    text(d, (40, 7), "Z", 8, WHITE)
    text(d, (29, 7), "z", 7, WHITE)
    return img


def coins():
    img, d = plate((0x58, 0x61, 0x74, 255), (0x8d, 0x96, 0xa8, 255))
    for cx, cy in ((10, 8), (22, 13), (36, 9), (15, 21), (30, 20), (40, 26), (8, 31)):
        circle(d, cx, cy, 4, YELLOW)
        d.line((cx - 1, cy - 2, cx - 1, cy + 2), fill=YELLOW_D)
        d.point((cx - 2, cy - 2), fill=YELLOW_L)
    d.ellipse((12, 30, 38, 44), fill=YELLOW_D, outline=INK)
    d.ellipse((13, 28, 37, 40), fill=YELLOW, outline=INK)
    d.ellipse((17, 31, 33, 37), fill=YELLOW_L)
    text(d, (24, 34), "$", 9, YELLOW_D, outline=YELLOW_L)
    return img


def crit():
    img, d = plate((0xb0, 0x2a, 0x3a, 255), (0xff, 0x8a, 0x2a, 255))
    pts = [(24, 3), (28, 15), (41, 9), (33, 20), (45, 24), (33, 28), (41, 39), (28, 33), (24, 45), (20, 33), (7, 39), (15, 28), (3, 24),
           (15, 20), (7, 9), (20, 15)]
    d.polygon(pts, fill=ORANGE, outline=INK)
    d.polygon([(24, 10), (27, 20), (37, 24), (27, 28), (24, 38), (21, 28), (11, 24), (21, 20)], fill=YELLOW_L, outline=INK)
    text(d, (24, 24), "CRIT", 10, RED_D, outline=WHITE)
    return img


def chest():
    img, d = plate((0x3a, 0x30, 0x4a, 255), (0x6a, 0x46, 0xb0, 255))
    d.rectangle((8, 20, 39, 38), fill=BROWN, outline=INK)
    d.pieslice((8, 10, 39, 30), 180, 360, fill=BROWN, outline=INK)
    d.rectangle((9, 19, 38, 24), fill=BROWN)
    for x in (8, 21, 34):
        d.rectangle((x, 12, x + 5, 38), fill=GREY, outline=INK)
    d.line((9, 25, 38, 25), fill=INK)
    d.rectangle((21, 22, 26, 30), fill=YELLOW, outline=INK)
    d.rectangle((23, 25, 24, 27), fill=INK)
    d.line((10, 30, 19, 30), fill=BROWN_D)
    d.line((28, 33, 37, 33), fill=BROWN_D)
    for x, y in ((11, 8), (36, 7), (30, 4)):
        d.point((x, y), fill=YELLOW_L)
        d.point((x - 1, y), fill=YELLOW)
        d.point((x + 1, y), fill=YELLOW)
        d.point((x, y - 1), fill=YELLOW)
        d.point((x, y + 1), fill=YELLOW)
    return img


def owl():
    img, d = plate((0x5e, 0x3c, 0x20, 255), (0x9a, 0x6a, 0x3a, 255))
    d.polygon([(11, 10), (17, 14), (13, 18)], fill=BROWN, outline=INK)
    d.polygon([(37, 10), (31, 14), (35, 18)], fill=BROWN, outline=INK)
    d.ellipse((10, 12, 38, 42), fill=BROWN, outline=INK)
    d.ellipse((17, 28, 31, 40), fill=YELLOW_L)
    for y in (31, 34, 37):
        d.line((20, y, 28, y), fill=YELLOW_D)
    for cx in (18, 30):
        circle(d, cx, 21, 6, WHITE)
        circle(d, cx, 21, 3, INK)
        d.point((cx - 1, 20), fill=WHITE)
    d.polygon([(22, 24), (26, 24), (24, 29)], fill=ORANGE, outline=INK)
    d.line((14, 31, 11, 36), fill=BROWN_D, width=2)
    d.line((34, 31, 37, 36), fill=BROWN_D, width=2)
    d.rectangle((17, 41, 20, 43), fill=ORANGE)
    d.rectangle((28, 41, 31, 43), fill=ORANGE)
    return img


def creeper():
    img, d = plate((0x2c, 0x8a, 0x52, 255), (0x5c, 0xd6, 0x8a, 255))
    d.rectangle((9, 9, 38, 38), fill=GREEN, outline=INK)
    shades = [(11, 11, 15, 14), (22, 10, 27, 13), (31, 18, 36, 22), (12, 30, 17, 35), (30, 31, 36, 36), (23, 33, 26, 36)]
    for r in shades:
        d.rectangle(r, fill=GREEN_D)
    d.rectangle((14, 15, 21, 22), fill=INK)
    d.rectangle((26, 15, 33, 22), fill=INK)
    d.rectangle((21, 22, 26, 29), fill=INK)
    d.rectangle((17, 26, 30, 36), fill=INK)
    d.rectangle((21, 26, 26, 30), fill=INK)
    d.rectangle((17, 30, 20, 36), fill=INK)
    d.rectangle((27, 30, 30, 36), fill=INK)
    text(d, (24, 43), "sss", 7, YELLOW_L)
    return img


def trophy():
    img, d = plate((0x6a, 0x46, 0xb0, 255), (0xb9, 0x8c, 0xff, 255))
    d.arc((7, 9, 20, 25), 90, 270, fill=YELLOW_D, width=3)
    d.arc((28, 9, 41, 25), 270, 90, fill=YELLOW_D, width=3)
    d.polygon([(14, 8), (34, 8), (33, 22), (28, 28), (20, 28), (15, 22)], fill=YELLOW, outline=INK)
    d.rectangle((16, 9, 19, 21), fill=YELLOW_L)
    d.rectangle((22, 28, 26, 34), fill=YELLOW_D, outline=INK)
    d.rectangle((15, 34, 33, 39), fill=BROWN, outline=INK)
    d.rectangle((13, 39, 35, 42), fill=BROWN_D, outline=INK)
    d.polygon([(24, 12), (26, 17), (31, 17), (27, 20), (28, 25), (24, 22), (20, 25), (21, 20), (17, 17), (22, 17)], fill=YELLOW_L)
    return img


STICKERS = [
    ("gg", gg), ("wow", wow), ("lol", lol), ("rip", rip), ("hype", hype),
    ("thumbsup", thumbsup), ("hearteyes", hearteyes), ("angry", angry), ("sleepy", sleepy), ("coins", coins),
    ("crit", crit), ("chest", chest), ("owl", owl), ("creeper", creeper), ("trophy", trophy),
]


def main():
    out = RES / "textures" / "sticker"
    out.mkdir(parents=True, exist_ok=True)
    images = []
    for name, fn in STICKERS:
        img = fn()
        assert img.size == (SIZE, SIZE), name
        img.save(out / f"{name}.png")
        images.append(img)
    (RES / "stickers.txt").write_text("\n".join(name for name, _ in STICKERS) + "\n", encoding="utf-8")
    print("stickers:", " ".join(name for name, _ in STICKERS))
    if "--preview" in sys.argv:
        target = Path(sys.argv[sys.argv.index("--preview") + 1])
        target.mkdir(parents=True, exist_ok=True)
        cols = 5
        rows = (len(images) + cols - 1) // cols
        scale = 4
        sheet = Image.new("RGBA", (cols * (SIZE * scale + 8) + 8, rows * (SIZE * scale + 8) + 8), (0x22, 0x28, 0x38, 255))
        for i, img in enumerate(images):
            big = img.resize((SIZE * scale, SIZE * scale), Image.NEAREST)
            sheet.alpha_composite(big, (8 + (i % cols) * (SIZE * scale + 8), 8 + (i // cols) * (SIZE * scale + 8)))
        sheet.save(target / "preview.png")


if __name__ == "__main__":
    main()
