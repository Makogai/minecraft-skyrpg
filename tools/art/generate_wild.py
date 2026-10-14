#!/usr/bin/env python3
"""Draws the Wild's items (docs/design/the_wild.md) and writes their assets. Add-only: new texture, model and item files,
and new keys appended to two lang files (nothing existing is rewritten).

    python tools/art/generate_wild.py

World mod (world/src/main/resources/assets/skyrpg_world/): blood_shard, gale_essence, ectoplasm, the six boss trophies
(blighted_heart, cinder_core, frozen_crown, bog_mothers_eye, wraith_lantern, stormfeather) and the six Heart Sigils.
Items mod: the Moonblood Charm's icon and its worn texture (a recolour of the Blightheart Amulet's).
"""
import json
import math
import sys
from pathlib import Path

sys.dont_write_bytecode = True
from PIL import Image  # noqa: E402

ROOT = Path(__file__).resolve().parents[2]
WORLD = ROOT / "world" / "src" / "main" / "resources" / "assets" / "skyrpg_world"
ITEMS = ROOT / "items" / "src" / "main" / "resources" / "assets" / "skyrpg_items"


def rgb(h):
    h = h.lstrip("#")
    return tuple(int(h[i:i + 2], 16) for i in (0, 2, 4))


def ramp(*hexes):
    return [rgb(h) for h in hexes]


def canvas():
    return Image.new("RGBA", (16, 16), (0, 0, 0, 0))


def put(img, x, y, color, a=255):
    if 0 <= x < 16 and 0 <= y < 16:
        img.putpixel((x, y), tuple(color) + (a,))


def shade(r, v):
    """Pick from a dark-to-light ramp by v in 0..1."""
    return r[max(0, min(len(r) - 1, int(v * len(r))))]


def outline(img, color):
    """A one-pixel outline around everything drawn."""
    src = img.copy()
    for y in range(16):
        for x in range(16):
            if src.getpixel((x, y))[3] == 0:
                for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                    nx, ny = x + dx, y + dy
                    if 0 <= nx < 16 and 0 <= ny < 16 and src.getpixel((nx, ny))[3] > 0:
                        put(img, x, y, color)
                        break


def solid(inside, light, r, edge, glow=None):
    """Fills every pixel for which inside(x, y) holds, shaded by light(x, y) in 0..1."""
    img = canvas()
    for y in range(16):
        for x in range(16):
            if inside(x, y):
                put(img, x, y, shade(r, light(x, y)))
    outline(img, edge)
    return img


def sphere(cx, cy, rad):
    def light(x, y):
        dx, dy = (x - cx) / rad, (y - cy) / rad
        return max(0.0, min(0.999, 0.55 - dx * 0.35 - dy * 0.45 + 0.25 * (1 - math.hypot(dx, dy))))
    return light


def disc(cx, cy, rad):
    return lambda x, y: (x - cx) ** 2 + (y - cy) ** 2 <= rad * rad


# ---------------------------------------------------------------- the icons
def blood_shard():
    r = ramp("3a0610", "6a0f1c", "a3162a", "d92a3c", "ff7a82")

    def axes(x, y):
        # An elongated crystal lying on the diagonal, pointed at both ends, fattest a little past the middle.
        u = ((x - 8) - (y - 8)) / 1.414
        v = ((x - 8) + (y - 8)) / 1.414
        return u, v

    def inside(x, y):
        u, v = axes(x, y)
        return abs(u) / 7.5 + abs(v) / 3.4 <= 1.0

    def light(x, y):
        u, v = axes(x, y)
        return max(0.0, min(0.999, 0.55 - v * 0.14 + u * 0.03 + (0.25 if abs(v) < 0.8 and u < 3 else 0)))
    img = solid(inside, light, r, rgb("1c0208"))
    for x, y in ((6, 9), (7, 8)):
        put(img, x, y, r[4])
    return img


def gale_essence():
    r = ramp("2c5470", "5a96b8", "96cde6", "d4f0fa", "ffffff")
    img = canvas()
    # Three wind curls: arcs of increasing radius.
    for rad, start, gap in ((2.5, 0.3, 0.9), (4.5, 1.5, 1.2), (6.5, 2.7, 1.5)):
        for i in range(60):
            a = start + i * (2 * math.pi - gap) / 60
            x = round(8 + math.cos(a) * rad)
            y = round(8 + math.sin(a) * rad * 0.9)
            put(img, x, y, shade(r, 0.35 + 0.65 * (i / 60)))
    for x, y in ((8, 8), (9, 8), (8, 9)):
        put(img, x, y, r[4])
    outline(img, rgb("1c3a50"))
    return img


def ectoplasm():
    r = ramp("0e3a32", "1e7060", "3ab89a", "86e8c8", "e8fff6")

    def inside(x, y):
        return (x - 8) ** 2 / 36 + (y - 9) ** 2 / 25 <= 1 or (x - 8) ** 2 / 9 + (y - 4) ** 2 / 9 <= 1 and y < 7
    img = solid(inside, sphere(7, 8, 7), r, rgb("08201c"))
    for x, y in ((6, 7), (7, 7), (6, 8), (10, 11), (5, 11)):
        put(img, x, y, r[4])
    put(img, 9, 8, rgb("0e3a32"))
    put(img, 6, 11, rgb("0e3a32"))
    return img


def blighted_heart():
    r = ramp("14040c", "3a0c22", "6a1636", "9c2650", "d24a78")

    def inside(x, y):
        left = (x - 5.5) ** 2 + (y - 5.5) ** 2 <= 11
        right = (x - 10.5) ** 2 + (y - 5.5) ** 2 <= 11
        tip = y >= 6 and abs(x - 8) <= (14 - y) * 0.95
        return left or right or tip
    img = solid(inside, sphere(6, 5, 9), r, rgb("08020a"))
    for x, y in ((8, 5), (8, 6), (7, 7), (9, 8), (8, 9), (8, 10), (7, 11)):
        put(img, x, y, rgb("0a0208"))
    for x, y in ((5, 4), (4, 5)):
        put(img, x, y, r[4])
    return img


def cinder_core():
    r = ramp("0c0c0e", "262628", "3e3c3e", "5a5658", "7c7678")
    img = solid(disc(8, 8, 6), sphere(7, 6, 8), r, rgb("060606"))
    fire = ramp("7a1a00", "c43a00", "ff7a10", "ffc040", "fff2a0")
    cracks = [(8, 3), (8, 4), (9, 5), (9, 6), (8, 7), (8, 8), (7, 9), (7, 10), (8, 11), (8, 12), (5, 7), (6, 7), (4, 8), (10, 8), (11, 9), (12, 9),
              (10, 10)]
    for i, (x, y) in enumerate(cracks):
        put(img, x, y, shade(fire, 0.4 + 0.6 * ((i * 7) % 10) / 10))
    return img


def frozen_crown():
    r = ramp("1c3a5a", "3a74a8", "74b4e0", "b4e0fa", "f4fcff")
    pts = set()
    for x in range(2, 14):
        for y in range(9, 13):
            pts.add((x, y))
    for peak in (3, 8, 12):
        for k in range(6):
            w = (6 - k) // 2
            for dx in range(-w, w + 1):
                pts.add((peak + dx, 8 - k))
    pts = {p for p in pts if 1 <= p[0] <= 14}
    img = solid(lambda x, y: (x, y) in pts, lambda x, y: 0.9 - (y - 2) / 12.0 * 0.6 + (0.1 if (x + y) % 5 == 0 else 0), r, rgb("0c1c30"))
    for x in (4, 8, 11):
        put(img, x, 10, rgb("e8c050"))
    return img


def bog_mothers_eye():
    img = solid(disc(8, 8, 6), sphere(7, 6, 9), ramp("8a9a70", "b4c48c", "d8e4b4", "f0f6d8", "ffffff"), rgb("1c2a10"))
    iris = ramp("1c4a10", "3a7a1c", "62b02c", "9adc48")
    for y in range(16):
        for x in range(16):
            if (x - 8) ** 2 + (y - 8) ** 2 <= 10:
                put(img, x, y, shade(iris, 0.9 - math.hypot(x - 8, y - 8) / 4.0))
    for y in range(5, 12):
        put(img, 8, y, rgb("060a04"))
        if 6 <= y <= 9:
            put(img, 7, y, rgb("060a04"))
    put(img, 6, 6, rgb("ffffff"))
    for x, y in ((3, 8), (4, 9), (12, 9), (13, 8)):
        put(img, x, y, rgb("a03030"))
    return img


def wraith_lantern():
    img = canvas()
    frame, glass = rgb("30283c"), ramp("2a6ac0", "5aa0f0", "9ad0ff", "d8f0ff", "ffffff")
    for x in range(6, 10):
        put(img, x, 1, frame)
    put(img, 5, 2, frame)
    put(img, 10, 2, frame)
    for x in range(4, 12):
        put(img, x, 3, frame)
        put(img, x, 13, frame)
    for y in range(4, 13):
        put(img, 4, y, frame)
        put(img, 11, y, frame)
        for x in range(5, 11):
            put(img, x, y, shade(glass, 1 - math.hypot(x - 7.5, y - 8.5) / 4.8))
    for x in range(5, 11):
        put(img, x, 14, frame)
    put(img, 7, 8, rgb("ffffff"))
    put(img, 8, 8, rgb("ffffff"))
    put(img, 7, 9, rgb("e8f8ff"))
    outline(img, rgb("0c0814"))
    return img


def stormfeather():
    r = ramp("2a3448", "4a5a78", "7a8cac", "b4c4dc", "e8f0fc")
    img = canvas()
    for i in range(14):
        x, y = 13 - i, 2 + i
        width = 3 if 2 <= i <= 10 else 2
        for w in range(-width // 2, width // 2 + 1):
            put(img, x + w, y, shade(r, 0.35 + 0.6 * (1 - abs(w) / 2.0) + (0.1 if i % 3 == 0 else 0)))
        put(img, x + 2, y + 1, shade(r, 0.3))
    for i in range(0, 12, 3):
        put(img, 12 - i, 3 + i, rgb("2a3448"))
    outline(img, rgb("10141c"))
    spark = rgb("ffe85a")
    for x, y in ((8, 5), (9, 6), (8, 7), (9, 8), (8, 9)):
        put(img, x, y, spark)
    return img


SIGIL_COLORS = {
    "blightlands": ("3a0a14", "7c1a2c", "c8344c", "ff7088"),
    "ashen_wastes": ("3a1a06", "8a420c", "e87a1c", "ffc060"),
    "frostgrave": ("123a5c", "3a82b8", "86c8f0", "e8f8ff"),
    "mire": ("14300c", "2e6a1c", "62b02c", "b8f070"),
    "haunted_moors": ("2a2440", "5a4c8c", "9a8ce0", "e0d8ff"),
    "shattered_highlands": ("1c3a5c", "3a74b0", "7cc0f0", "e0f4ff"),
}
SIGIL_POINTS = {"blightlands": 3, "ashen_wastes": 4, "frostgrave": 6, "mire": 5, "haunted_moors": 7, "shattered_highlands": 8}


def sigil(kind):
    dark, mid, bright, glow = (rgb(h) for h in SIGIL_COLORS[kind])
    stone = ramp("1c1c24", "30303c", "484856", "646472", "808090")
    img = solid(disc(8, 8, 7), sphere(6, 5, 12), stone, rgb("0a0a10"))
    for y in range(16):
        for x in range(16):
            d = math.hypot(x - 7.5, y - 7.5)
            if 5.0 <= d <= 5.9:
                put(img, x, y, mid)
    n = SIGIL_POINTS[kind]
    for i in range(n):
        a = math.pi * 2 * i / n - math.pi / 2
        for t in (1.0, 2.0, 3.0, 4.0, 5.0):
            put(img, round(7.5 + math.cos(a) * t), round(7.5 + math.sin(a) * t), bright if t < 5 else glow)
    put(img, 7, 7, glow)
    put(img, 8, 7, glow)
    put(img, 7, 8, glow)
    put(img, 8, 8, glow)
    return img


def moonblood_charm():
    img = canvas()
    silver = ramp("3a3e48", "6a707e", "9aa0b0", "cdd2de", "f4f6fc")
    red = ramp("3a0610", "7a1020", "b81c32", "ee4458", "ff9aa4")
    # A cord, a silver clasp ring and a red moon cradled in it.
    for x, y in ((3, 1), (3, 2), (4, 3), (12, 1), (12, 2), (11, 3), (5, 4), (10, 4), (6, 5), (9, 5)):
        put(img, x, y, rgb("5a1424"))
    for y in range(16):
        for x in range(16):
            d = math.hypot(x - 7.5, y - 10)
            if 4.0 <= d <= 5.6:
                put(img, x, y, shade(silver, 0.9 - (x - 3) / 14.0 * 0.6 - (y - 5) / 12.0 * 0.15))
            elif d < 4.0:
                moon = math.hypot(x - 7.5, y - 10) <= 3.4 and math.hypot(x - 9.0, y - 9.0) > 2.9
                if moon:
                    put(img, x, y, shade(red, 0.8 - (x - 4) / 9.0 * 0.6))
                else:
                    put(img, x, y, rgb("1a1018"))
    outline(img, rgb("0e0810"))
    return img


WORLD_ICONS = {
    "blood_shard": ("Blood Shard", blood_shard),
    "gale_essence": ("Gale Essence", gale_essence),
    "ectoplasm": ("Ectoplasm", ectoplasm),
    "blighted_heart": ("Blighted Heart", blighted_heart),
    "cinder_core": ("Cinder Core", cinder_core),
    "frozen_crown": ("Frozen Crown", frozen_crown),
    "bog_mothers_eye": ("Bog Mother's Eye", bog_mothers_eye),
    "wraith_lantern": ("Wraith Lantern", wraith_lantern),
    "stormfeather": ("Stormfeather", stormfeather),
}
SIGIL_NAMES = {"blightlands": "Blightlands", "ashen_wastes": "Ashen Wastes", "frostgrave": "Frostgrave", "mire": "Mire",
               "haunted_moors": "Haunted Moors", "shattered_highlands": "Shattered Highlands"}


def write_json(path, data):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(data, indent=2) + "\n", encoding="utf-8")


def write_item(assets, namespace, name, image):
    texture = assets / "textures" / "item" / f"{name}.png"
    texture.parent.mkdir(parents=True, exist_ok=True)
    image.save(texture)
    write_json(assets / "models" / "item" / f"{name}.json", {"parent": "minecraft:item/generated", "textures": {"layer0": f"{namespace}:item/{name}"}})
    write_json(assets / "items" / f"{name}.json", {"model": {"type": "minecraft:model", "model": f"{namespace}:item/{name}"}})


def append_lang(path, entries):
    """Adds the keys that aren't there yet, at the end, leaving everything else as it was (the files use LF endings)."""
    text = path.read_bytes().decode("utf-8")
    existing = json.loads(text)
    new = {k: v for k, v in entries.items() if k not in existing}
    if not new:
        return
    body = text.rstrip()
    assert body.endswith("}")
    body = body[:-1].rstrip()
    lines = ",\n".join(f"  {json.dumps(k)}: {json.dumps(v, ensure_ascii=False)}" for k, v in new.items())
    path.write_bytes((body + ","+"\n" + lines + "\n}\n").encode("utf-8"))


def main():
    world_lang = {}
    for name, (title, draw) in WORLD_ICONS.items():
        write_item(WORLD, "skyrpg_world", name, draw())
        world_lang[f"item.skyrpg_world.{name}"] = title
    for kind, title in SIGIL_NAMES.items():
        write_item(WORLD, "skyrpg_world", f"{kind}_sigil", sigil(kind))
        world_lang[f"item.skyrpg_world.{kind}_sigil"] = f"{title} Heart Sigil"
    append_lang(WORLD / "lang" / "en_us.json", world_lang)
    write_item(ITEMS, "skyrpg_items", "moonblood_charm", moonblood_charm())
    # Worn: the Blightheart Amulet's texture, redder and brighter (same model layout).
    worn = Image.open(ITEMS / "textures" / "entity" / "accessory" / "blightheart_amulet.png").convert("RGBA")
    for y in range(worn.height):
        for x in range(worn.width):
            r, g, b, a = worn.getpixel((x, y))
            if a:
                worn.putpixel((x, y), (min(255, int(r * 1.2) + 10), min(255, int(g * 1.1) + 14), min(255, int(b * 1.15) + 22), a))
    out = ITEMS / "textures" / "entity" / "accessory" / "moonblood_charm.png"
    worn.save(out)
    append_lang(ITEMS / "lang" / "en_us.json", {"item.skyrpg_items.moonblood_charm": "Moonblood Charm"})
    print(f"drew {len(WORLD_ICONS) + len(SIGIL_NAMES) + 1} icons")


if __name__ == "__main__":
    main()
