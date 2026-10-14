#!/usr/bin/env python3
"""Draws the enchanting mod's items and the enchanting screen's arcane textures.

    python tools/art/generate_enchants.py

Writes, under enchants/src/main/resources/assets/skyrpg_enchants/:
- textures/item/<id>.png, models/item/<id>.json and items/<id>.json for the Arcane Tome and Runic Dust (generate_items.py's
  mask renderer and shading);
- textures/gui/enchanting/*.png: white-on-transparent rune rings, a soft glow and a spark, which the screen tints and
  spins. Drawn at four times their size and scaled down, so the lines stay smooth.
"""
import json
import math
import random
import sys
from pathlib import Path

from PIL import Image, ImageDraw, ImageFilter

sys.path.insert(0, str(Path(__file__).resolve().parent))
import generate_items as gi  # noqa: E402

ROOT = Path(__file__).resolve().parents[2]
ASSETS = ROOT / "enchants" / "src" / "main" / "resources" / "assets" / "skyrpg_enchants"

SPINE = gi.Material(["1a0626", "2c0c40", "441660", "5e2282", "7a34a4"])
LEATHER = gi.Material(["2a0c40", "461666", "662496", "8a3cc0", "b064e4"], texture=gi.stitch)
GILT = gi.Material(["5e3a08", "9a6814", "d0a024", "f0d050", "fff4a8"], texture=gi.sheen)
RUNE = gi.Material(["3a2a8a", "5a6ae0", "7ad4ff", "c0f4ff", "ffffff"], base=3)
PAGES = gi.Material(["6e5a40", "a08a66", "d8ccb0", "eee6d0", "fffaf0"], base=3)
DUST = gi.Material(["2a0c4a", "4a1a7a", "7a34b8", "a868e8", "dcb4ff"], texture=gi.speckle)
GLINT = gi.Material(["8a5ae0", "b48cff", "e0ccff", "f4ecff", "ffffff"], base=4)

ITEMS = {
    # A violet tome with gilt corners and a glowing rune on its cover.
    "arcane_tome": ({"S": SPINE, "P": LEATHER, "G": GILT, "R": RUNE, "w": PAGES}, [
        "................",
        "..SGPPPPPPPPG...",
        "..SPPPPPPPPPPw..",
        "..SPPPPRRPPPPw..",
        "..SPPPRPPRPPPw..",
        "..SPPRPPPPRPPw..",
        "..SPPRPRRPRPPw..",
        "..SPPRPRRPRPPw..",
        "..SPPRPPPPRPPw..",
        "..SPPPRPPRPPPw..",
        "..SPPPPRRPPPPw..",
        "..SPPPPPPPPPPw..",
        "..SGPPPPPPPPGw..",
        "...wwwwwwwwwww..",
        "................",
        "................",
    ]),
    # A small heap of glittering violet dust: what's left of a salvaged tome.
    "runic_dust": ({"D": DUST, "L": DUST.shifted(base=4), "*": GLINT}, [
        "................",
        "................",
        "........*.......",
        "...*............",
        "..........*.....",
        "......DDD.......",
        ".....DDLDD...*..",
        "....DDLLLDD.....",
        "...DDDLLLDDD....",
        "..DDDDDLDDDDD...",
        ".DDDDDDDDDDDDD..",
        ".DDDDDDDDDDDDDD.",
        "..DDDDDDDDDDDD..",
        "................",
        "................",
        "................",
    ]),
}
ITEM_NAMES = {"arcane_tome": "Arcane Tome", "runic_dust": "Runic Dust"}

S = 4  # supersampling for the GUI textures


def write_json(path, obj):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(obj, indent=2) + "\n", encoding="utf-8", newline="\n")


def circle(draw, cx, cy, r, width, alpha=255):
    draw.ellipse((cx - r, cy - r, cx + r, cy + r), outline=(255, 255, 255, alpha), width=width)


def glyph(draw, cx, cy, size, angle, rng, alpha):
    """A made-up rune: a stem and a few strokes, turned to face the ring's centre."""
    strokes = []
    h = size
    w = size * 0.6
    strokes.append(((0, -h / 2), (0, h / 2)))
    for _ in range(rng.randint(2, 3)):
        kind = rng.choice(["branch", "bar", "hook", "chevron"])
        y = rng.uniform(-h / 2, h / 2)
        if kind == "branch":
            side = rng.choice([-1, 1])
            strokes.append(((0, y), (side * w / 2, y - h / 4)))
        elif kind == "bar":
            strokes.append(((-w / 2, y), (w / 2, y)))
        elif kind == "hook":
            side = rng.choice([-1, 1])
            strokes.append(((0, y), (side * w / 2, y)))
            strokes.append(((side * w / 2, y), (side * w / 2, y + h / 4)))
        else:
            strokes.append(((-w / 2, y), (0, y - h / 5)))
            strokes.append(((0, y - h / 5), (w / 2, y)))
    cos, sin = math.cos(angle), math.sin(angle)
    for (x1, y1), (x2, y2) in strokes:
        p1 = (cx + x1 * cos - y1 * sin, cy + x1 * sin + y1 * cos)
        p2 = (cx + x2 * cos - y2 * sin, cy + x2 * sin + y2 * cos)
        draw.line([p1, p2], fill=(255, 255, 255, alpha), width=max(1, int(size / 7)))


def finish(img, size):
    return img.resize((size, size), Image.LANCZOS)


def outer_ring():
    n = 256 * S
    img = Image.new("RGBA", (n, n), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    c = n / 2
    rng = random.Random(7)
    circle(d, c, c, 124 * S, 2 * S)
    circle(d, c, c, 100 * S, 2 * S)
    circle(d, c, c, 94 * S, S, 150)
    # Ticks round the outside, longer every fourth.
    for i in range(96):
        a = i / 96 * math.tau
        r1 = 124 * S
        r2 = (118 if i % 4 else 113) * S
        d.line([(c + math.cos(a) * r1, c + math.sin(a) * r1), (c + math.cos(a) * r2, c + math.sin(a) * r2)],
               fill=(255, 255, 255, 200), width=S)
    # Runes between the circles.
    count = 20
    for i in range(count):
        a = i / count * math.tau
        r = 107 * S
        glyph(d, c + math.cos(a) * r, c + math.sin(a) * r, 13 * S, a + math.pi / 2, rng, 235)
    # Dots on the inner dashed circle.
    for i in range(40):
        a = (i + 0.5) / 40 * math.tau
        r = 94 * S
        x, y = c + math.cos(a) * r, c + math.sin(a) * r
        d.ellipse((x - 1.6 * S, y - 1.6 * S, x + 1.6 * S, y + 1.6 * S), fill=(255, 255, 255, 220))
    return finish(img, 256)


def inner_ring():
    n = 256 * S
    img = Image.new("RGBA", (n, n), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    c = n / 2
    circle(d, c, c, 92 * S, 2 * S)
    # Two triangles: a six-pointed star.
    for turn in (0, math.pi / 3):
        pts = [(c + math.cos(turn + k * math.tau / 3 - math.pi / 2) * 90 * S,
                c + math.sin(turn + k * math.tau / 3 - math.pi / 2) * 90 * S) for k in range(3)]
        d.line(pts + [pts[0]], fill=(255, 255, 255, 230), width=2 * S, joint="curve")
    # Little circles on its points.
    for k in range(6):
        a = k * math.tau / 6 - math.pi / 2
        x, y = c + math.cos(a) * 90 * S, c + math.sin(a) * 90 * S
        d.ellipse((x - 9 * S, y - 9 * S, x + 9 * S, y + 9 * S), fill=(0, 0, 0, 0), outline=(255, 255, 255, 255), width=2 * S)
        d.ellipse((x - 3 * S, y - 3 * S, x + 3 * S, y + 3 * S), fill=(255, 255, 255, 255))
    circle(d, c, c, 46 * S, 2 * S, 220)
    circle(d, c, c, 40 * S, S, 140)
    for k in range(12):
        a = k * math.tau / 12
        d.line([(c + math.cos(a) * 46 * S, c + math.sin(a) * 46 * S), (c + math.cos(a) * 52 * S, c + math.sin(a) * 52 * S)],
               fill=(255, 255, 255, 200), width=S)
    return finish(img, 256)


def glow():
    n = 128
    img = Image.new("RGBA", (n, n), (0, 0, 0, 0))
    c = (n - 1) / 2
    for y in range(n):
        for x in range(n):
            t = min(1.0, math.hypot(x - c, y - c) / c)
            a = int(255 * (1 - t) ** 2.2)
            img.putpixel((x, y), (255, 255, 255, a))
    return img


def spark():
    n = 16
    img = Image.new("RGBA", (n, n), (0, 0, 0, 0))
    c = (n - 1) / 2
    for y in range(n):
        for x in range(n):
            t = min(1.0, math.hypot(x - c, y - c) / c)
            core = (1 - t) ** 3
            cross = max(0.0, 1 - abs(x - c) / 1.2) * (1 - abs(y - c) / c) + max(0.0, 1 - abs(y - c) / 1.2) * (1 - abs(x - c) / c)
            a = int(255 * min(1.0, core * 1.4 + cross * 0.55))
            img.putpixel((x, y), (255, 255, 255, a))
    return img


def build():
    for name, (mats, mask) in ITEMS.items():
        assert len(mask) == 16 and all(len(r) == 16 for r in mask), name
        img = gi.render(mask, mats)
        out = ASSETS / "textures" / "item" / f"{name}.png"
        out.parent.mkdir(parents=True, exist_ok=True)
        img.save(out)
        write_json(ASSETS / "models" / "item" / f"{name}.json",
                   {"parent": "minecraft:item/generated", "textures": {"layer0": f"skyrpg_enchants:item/{name}"}})
        write_json(ASSETS / "items" / f"{name}.json", {"model": {"type": "minecraft:model", "model": f"skyrpg_enchants:item/{name}"}})
    gui = ASSETS / "textures" / "gui" / "enchanting"
    gui.mkdir(parents=True, exist_ok=True)
    outer_ring().save(gui / "rune_ring_outer.png")
    inner_ring().save(gui / "rune_ring_inner.png")
    glow().save(gui / "glow.png")
    spark().save(gui / "spark.png")
    lang_path = ASSETS / "lang" / "en_us.json"
    lang = json.loads(lang_path.read_text(encoding="utf-8")) if lang_path.exists() else {}
    for name, title in ITEM_NAMES.items():
        lang[f"item.skyrpg_enchants.{name}"] = title
    lang["itemGroup.skyrpg_enchants"] = "SkyRPG Enchanting"
    write_json(lang_path, lang)
    print(f"wrote {len(ITEMS)} enchanting item(s) and 4 GUI textures")


if __name__ == "__main__":
    build()
