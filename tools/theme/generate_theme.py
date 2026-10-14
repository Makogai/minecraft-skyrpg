#!/usr/bin/env python3
"""Generates the "SkyRPG Theme" resource pack: Minecraft's own screens in the SkyRPG look.

    python tools/theme/generate_theme.py              # writes core/src/main/resources/resourcepacks/skyrpg_theme
    python tools/theme/generate_theme.py --preview out.png

Reads vanilla textures straight from the 26.3 client jar and writes only what the pack overrides:
  * container backgrounds and panel-grey sprites: vanilla's GUI greys remapped pixel for pixel onto the
    SkyRPG palette, so every slot stays exactly where it was;
  * widget sprites (buttons, sliders, text fields, checkboxes, tabs, scrollers) and tooltips: drawn
    from scratch at vanilla's sizes, keeping vanilla's .mcmeta so nine-slice stretching still works;
  * menu / options backgrounds and separators.
The output folder is wiped and rebuilt on every run, so don't hand-edit files in it.
"""
import argparse
import io
import json
import shutil
import zipfile
from collections import deque
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont

ROOT = Path(__file__).resolve().parents[2]
JAR = Path.home() / ".gradle" / "caches" / "fabric-loom" / "26.3" / "minecraft-client.jar"
OUT = ROOT / "core" / "src" / "main" / "resources" / "resourcepacks" / "skyrpg_theme"
GUI = "assets/minecraft/textures/gui/"

# SkyRPG palette (matches core/src/client/.../ui/Theme.java)
WINDOW = (18, 22, 32)
CARD = (23, 28, 38)
CARD_HOVER = (29, 35, 48)
BORDER = (37, 44, 57)
BORDER_LIGHT = (46, 54, 70)
TRACK = (10, 13, 18)
TEXT = (233, 237, 244)
MUTED = (141, 150, 168)
ACCENT = (242, 179, 61)

# Where vanilla's container greys go.
PANEL = (22, 27, 36)        # #C6C6C6 panel fill
OUTLINE = (43, 51, 67)      # #000000 on the outer edge
DARK_INSIDE = (9, 11, 16)   # #000000 inside (e.g. the player preview box)
SLOT = (12, 15, 21)         # #8B8B8B slot fill (and the arrows drawn in it)
SLOT_SHADOW = (6, 8, 12)    # #373737 slot top-left edge
SLOT_LIGHT = (35, 42, 55)   # #FFFFFF slot bottom-right edge
MID = (58, 67, 84)          # #555555 inside a panel (not part of the bevel)
BEVEL_DEPTH = 3             # vanilla bevels sit within 3px of a panel's outer edge


# ---------------------------------------------------------------- vanilla palette remap
def neutral(rgb):
    return max(rgb) - min(rgb) <= 3


def lerp(a, b, t):
    t = max(0.0, min(1.0, t))
    return tuple(round(a[i] + (b[i] - a[i]) * t) for i in range(3))


def generic_grey(v):
    """Any vanilla grey without a special meaning, mapped monotonically into the dark palette."""
    if v >= 198:
        return lerp(PANEL, (60, 69, 86), (v - 198) / 57)
    if v >= 139:
        return lerp(SLOT, PANEL, (v - 139) / 59)
    if v >= 55:
        return lerp(SLOT_SHADOW, SLOT, (v - 55) / 84)
    return lerp(DARK_INSIDE, SLOT_SHADOW, v / 55)


def edge_distance(img):
    """Steps (4-neighbour) from each opaque pixel to the nearest transparent pixel or the image border."""
    w, h = img.size
    px = img.load()
    dist = [[None] * w for _ in range(h)]
    queue = deque()
    for y in range(h):
        for x in range(w):
            if px[x, y][3] == 0:
                dist[y][x] = 0
                queue.append((x, y))
            elif x in (0, w - 1) or y in (0, h - 1):
                dist[y][x] = 1
                queue.append((x, y))
    while queue:
        x, y = queue.popleft()
        d = dist[y][x]
        if d >= BEVEL_DEPTH + 1:
            continue
        for nx, ny in ((x + 1, y), (x - 1, y), (x, y + 1), (x, y - 1)):
            if 0 <= nx < w and 0 <= ny < h and dist[ny][nx] is None:
                dist[ny][nx] = d + 1
                queue.append((nx, ny))
    return dist


def remap(img):
    """Vanilla GUI greys -> SkyRPG palette. Colours (fire, bubbles, icons) are left alone."""
    img = img.convert("RGBA")
    dist = edge_distance(img)
    px = img.load()
    w, h = img.size
    for y in range(h):
        for x in range(w):
            r, g, b, a = px[x, y]
            if a == 0 or not neutral((r, g, b)):
                continue
            v = round((r + g + b) / 3)
            d = dist[y][x]
            near_edge = d is not None and d <= BEVEL_DEPTH
            if a < 255:
                out = generic_grey(v)
            elif v <= 2:
                out = OUTLINE if d == 1 else DARK_INSIDE
            elif v >= 253:
                out = PANEL if near_edge else SLOT_LIGHT   # panel bevel is flattened, slot highlight kept
            elif 84 <= v <= 86:
                out = PANEL if near_edge else MID
            elif 197 <= v <= 199:
                out = PANEL
            elif 138 <= v <= 140:
                out = SLOT
            elif 54 <= v <= 56:
                out = SLOT_SHADOW
            else:
                out = generic_grey(v)
            px[x, y] = (*out, a)
    return img


def wants_remap(path, img):
    """Sprites drawn as part of a vanilla grey panel: tabs, slots, scrollers, beacon buttons."""
    name = path.rsplit("/", 1)[-1]
    if any(word in name for word in ("progress", "bubbles", "arrow", "error", "out_of_stock", "lit_")):
        return False
    if "/slot/" in path:  # empty-slot silhouettes (armor, ingots...) keep their look
        return False
    if "advancements/" in path and "frame" in name:  # advancement icons sit on world textures, not panels
        return False
    data = [p for p in img.convert("RGBA").get_flattened_data() if p[3] > 0]
    if not data:
        return False
    greys = sum(1 for p in data if neutral(p[:3])) / len(data)
    panel = any(p[:3] in ((198, 198, 198), (139, 139, 139)) for p in data)
    return greys >= 0.95 and panel


# ---------------------------------------------------------------- widget drawing
def blank(w, h):
    return Image.new("RGBA", (w, h), (0, 0, 0, 0))


def box(img, x0, y0, x1, y1, fill, border=None, cut=True):
    """Fill [x0, x1) x [y0, y1) with a 1px border and the four corner pixels cut off."""
    d = ImageDraw.Draw(img)
    color = border or fill
    d.rectangle((x0, y0, x1 - 1, y1 - 1), fill=color)
    if border:
        d.rectangle((x0 + 1, y0 + 1, x1 - 2, y1 - 2), fill=fill)
    if cut:
        for cx, cy in ((x0, y0), (x1 - 1, y0), (x0, y1 - 1), (x1 - 1, y1 - 1)):
            img.putpixel((cx, cy), (0, 0, 0, 0))
    return img


def rgba(rgb, a=255):
    return (*rgb, a)


def button(fill, border):
    return box(blank(200, 20), 0, 0, 200, 20, rgba(fill), rgba(border))


def tab(selected, highlighted):
    """130x24, nine-slice border L2 T2 R2 B0. Unselected tabs are 2px shorter, like vanilla."""
    img = blank(130, 24)
    top = 0 if selected else 2
    fill = rgba(CARD_HOVER if (selected and highlighted) else CARD if selected else (14, 17, 24), 235)
    side = rgba(BORDER_LIGHT if selected else BORDER)
    d = ImageDraw.Draw(img)
    d.rectangle((0, top, 129, 23), fill=side)
    d.rectangle((1, top + 1, 128, 23), fill=fill)
    for cx in (0, 129):
        img.putpixel((cx, top), (0, 0, 0, 0))
    if selected:
        d.rectangle((1, top, 128, top + 1), fill=rgba(ACCENT))
    elif highlighted:
        d.rectangle((1, top + 1, 128, top + 1), fill=rgba(ACCENT, 200))
    if not selected:
        d.line((0, 23, 129, 23), fill=rgba(BORDER_LIGHT))  # separator under the tab row
    return img


def checkbox(selected, highlighted):
    img = box(blank(20, 20), 0, 0, 20, 20, rgba(TRACK), rgba(ACCENT if highlighted else BORDER_LIGHT))
    if selected:
        d = ImageDraw.Draw(img)
        check = [(5, 10), (8, 13), (14, 6)]
        for width_offset in (0, 1):
            d.line([(x, y + width_offset) for x, y in check], fill=rgba(ACCENT), width=2)
    return img


def scroller():
    return box(blank(6, 32), 0, 0, 6, 32, rgba((70, 80, 100)), rgba((88, 99, 122)))


def scroller_background():
    return box(blank(6, 32), 0, 0, 6, 32, rgba(TRACK, 220), rgba(BORDER))


def tooltip_background():
    """100x100, nine-slice border 9; vanilla's painted area is (8,8)-(91,91) with cut corners."""
    img = blank(100, 100)
    box(img, 8, 8, 92, 92, (13, 16, 23, 245))
    return img


def tooltip_frame():
    """1px frame just inside the background: gold along the top, quiet grey elsewhere."""
    img = blank(100, 100)
    d = ImageDraw.Draw(img)
    side = rgba((52, 60, 78), 255)
    d.line((9, 10, 9, 89), fill=side)
    d.line((90, 10, 90, 89), fill=side)
    d.line((10, 90, 89, 90), fill=side)
    d.line((10, 9, 89, 9), fill=rgba(ACCENT, 235))
    return img


def tile(color, size=16):
    return Image.new("RGBA", (size, size), color)


def separator(top, bottom):
    img = blank(32, 2)
    ImageDraw.Draw(img).line((0, 0, 31, 0), fill=top)
    ImageDraw.Draw(img).line((0, 1, 31, 1), fill=bottom)
    return img


def pack_icon():
    img = Image.new("RGBA", (64, 64), rgba(WINDOW))
    d = ImageDraw.Draw(img)
    d.rectangle((0, 0, 63, 63), outline=rgba(BORDER_LIGHT))
    d.rectangle((2, 2, 61, 3), fill=rgba(ACCENT))
    d.rectangle((14, 18, 49, 45), outline=rgba(ACCENT), width=3)
    d.rectangle((22, 26, 41, 37), fill=rgba(CARD_HOVER))
    return img


WIDGETS = {
    "button": lambda: button(CARD_HOVER, BORDER_LIGHT),
    "button_highlighted": lambda: button((33, 40, 54), ACCENT),
    "button_disabled": lambda: button((15, 18, 25), (30, 35, 46)),
    "slider": lambda: button(TRACK, BORDER),
    "slider_highlighted": lambda: button(TRACK, ACCENT),
    "slider_handle": lambda: box(blank(8, 20), 0, 0, 8, 20, rgba((58, 67, 86)), rgba((88, 99, 122))),
    "slider_handle_highlighted": lambda: box(blank(8, 20), 0, 0, 8, 20, rgba((70, 80, 100)), rgba(ACCENT)),
    "text_field": lambda: button(TRACK, BORDER_LIGHT),
    "text_field_highlighted": lambda: button(TRACK, ACCENT),
    "checkbox": lambda: checkbox(False, False),
    "checkbox_highlighted": lambda: checkbox(False, True),
    "checkbox_selected": lambda: checkbox(True, False),
    "checkbox_selected_highlighted": lambda: checkbox(True, True),
    "tab": lambda: tab(False, False),
    "tab_highlighted": lambda: tab(False, True),
    "tab_selected": lambda: tab(True, False),
    "tab_selected_highlighted": lambda: tab(True, True),
    "scroller": scroller,
    "scroller_background": scroller_background,
}

def hotbar():
    """182x22. Vanilla draws item i at (3 + 20i, 3), so each 18x18 slot sits at (2 + 20i, 2)."""
    img = box(blank(182, 22), 0, 0, 182, 22, (12, 15, 21, 205), rgba(BORDER_LIGHT, 230))
    for i in range(9):
        box(img, 2 + i * 20, 2, 20 + i * 20, 20, (7, 9, 14, 215), rgba((31, 37, 49)), cut=False)
    return img


def hotbar_selection():
    """24x23, drawn 1px up-left of the selected slot's cell: the slot box is (3,3)-(21,21) here."""
    img = blank(24, 23)
    d = ImageDraw.Draw(img)
    d.rectangle((1, 1, 22, 21), outline=rgba(ACCENT, 90))
    d.rectangle((2, 2, 21, 20), outline=rgba(ACCENT))
    for x, y in ((2, 2), (21, 2), (2, 20), (21, 20)):
        img.putpixel((x, y), rgba(ACCENT, 140))
    return img


def offhand(slot_x):
    """29x24. The item sits at (slot_x + 1, 4); a 22x22 panel with one slot around it."""
    img = blank(29, 24)
    box(img, slot_x - 2, 1, slot_x + 20, 23, (12, 15, 21, 205), rgba(BORDER_LIGHT, 230))
    box(img, slot_x, 3, slot_x + 18, 21, (7, 9, 14, 215), rgba((31, 37, 49)), cut=False)
    return img


def bar(fill, highlight=None):
    """182x5 HUD bar (experience, jump): rounded, with a lighter top line on progress bars."""
    img = box(blank(182, 5), 0, 0, 182, 5, fill)
    if highlight:
        ImageDraw.Draw(img).line((1, 1, 180, 1), fill=highlight)
    return img


def effect_background(ambient):
    return box(blank(24, 24), 0, 0, 24, 24, (12, 15, 21, 170 if ambient else 215), rgba(BORDER if ambient else BORDER_LIGHT))


HUD = {
    "hotbar": hotbar,
    "hotbar_selection": hotbar_selection,
    "hotbar_offhand_left": lambda: offhand(2),
    "hotbar_offhand_right": lambda: offhand(9),
    "experience_bar_background": lambda: bar((10, 13, 18, 220)),
    "experience_bar_progress": lambda: bar(rgba((92, 214, 138)), rgba((170, 240, 196))),
    "jump_bar_background": lambda: bar((10, 13, 18, 220)),
    "jump_bar_cooldown": lambda: bar(rgba((58, 67, 84))),
    "jump_bar_progress": lambda: bar(rgba(ACCENT), rgba((250, 215, 140))),
    "effect_background": lambda: effect_background(False),
    "effect_background_ambient": lambda: effect_background(True),
}

BACKGROUNDS = {
    "menu_background.png": lambda: tile((8, 11, 17, 140)),
    "inworld_menu_background.png": lambda: tile((8, 11, 17, 140)),
    "menu_list_background.png": lambda: tile((6, 8, 12, 150)),
    "inworld_menu_list_background.png": lambda: tile((6, 8, 12, 150)),
    "header_separator.png": lambda: separator(rgba(BORDER_LIGHT), (5, 7, 10, 200)),
    "inworld_header_separator.png": lambda: separator(rgba(BORDER_LIGHT), (5, 7, 10, 200)),
    "footer_separator.png": lambda: separator((5, 7, 10, 200), rgba(BORDER_LIGHT)),
    "inworld_footer_separator.png": lambda: separator((5, 7, 10, 200), rgba(BORDER_LIGHT)),
}


# ---------------------------------------------------------------- build
def build():
    jar = zipfile.ZipFile(JAR)
    names = set(jar.namelist())
    read = lambda name: Image.open(io.BytesIO(jar.read(name)))  # noqa: E731
    version = json.loads(jar.read("version.json"))
    resource_format = version["pack_version"]["resource_major"]

    if OUT.exists():
        shutil.rmtree(OUT)
    written = 0

    def save(name, image):
        nonlocal written
        target = OUT / name
        target.parent.mkdir(parents=True, exist_ok=True)
        image.save(target)
        meta = name + ".mcmeta"
        if meta in names:  # nine-slice / scaling data must ship next to the texture
            target.with_name(target.name + ".mcmeta").write_bytes(jar.read(meta))
        written += 1

    (OUT / "pack.mcmeta").parent.mkdir(parents=True, exist_ok=True)
    (OUT / "pack.mcmeta").write_text(json.dumps({"pack": {
        "description": "SkyRPG look for Minecraft's own menus, buttons and inventories",
        "min_format": resource_format,
        "max_format": resource_format,
    }}, indent=2) + "\n", encoding="utf-8")
    pack_icon().save(OUT / "pack.png")

    for name in sorted(names):
        if not name.endswith(".png"):
            continue
        full_texture = (name.startswith(GUI + "container/") or name in (GUI + "recipe_book.png", GUI + "advancements/window.png"))
        panel_sprite = name.startswith((GUI + "sprites/container/", GUI + "sprites/recipe_book/", GUI + "sprites/advancements/"))
        if full_texture:
            save(name, remap(read(name)))
        elif panel_sprite:
            image = read(name)
            if wants_remap(name, image):
                save(name, remap(image))

    for widget, make in WIDGETS.items():
        save(GUI + f"sprites/widget/{widget}.png", make())
    for sprite, make in HUD.items():
        save(GUI + f"sprites/hud/{sprite}.png", make())
    save(GUI + "sprites/tooltip/background.png", tooltip_background())
    save(GUI + "sprites/tooltip/frame.png", tooltip_frame())
    for file, make in BACKGROUNDS.items():
        save(GUI + file, make())
    print(f"Wrote {written} textures to {OUT} (resource format {resource_format})")


# ---------------------------------------------------------------- preview
def nine_slice(sprite, w, h, border):
    """Rough nine-slice stretch for previews (the game tiles edges; close enough to judge the look)."""
    sw, sh = sprite.size
    b = border
    out = blank(w, h)
    parts = [(0, b, 0, b), (b, sw - b, b, w - b), (sw - b, sw, w - b, w)]
    rows = [(0, b, 0, b), (b, sh - b, b, h - b), (sh - b, sh, h - b, h)]
    for sx0, sx1, dx0, dx1 in parts:
        for sy0, sy1, dy0, dy1 in rows:
            if sx1 > sx0 and sy1 > sy0 and dx1 > dx0 and dy1 > dy0:
                out.alpha_composite(sprite.crop((sx0, sy0, sx1, sy1)).resize((dx1 - dx0, dy1 - dy0), Image.NEAREST), (dx0, dy0))
    return out


def preview(path):
    font = ImageFont.load_default()
    load = lambda name: Image.open(OUT / name).convert("RGBA")  # noqa: E731
    sheet = Image.new("RGBA", (660, 520), (64, 92, 60, 255))
    d = ImageDraw.Draw(sheet)
    for y in range(0, 520, 16):  # a blocky "world" behind the screens
        for x in range(0, 660, 16):
            if (x // 16 + y // 16) % 2:
                d.rectangle((x, y, x + 15, y + 15), fill=(76, 106, 70, 255))
    label = (201, 208, 220, 255)

    inv = load(GUI + "container/inventory.png").crop((0, 0, 176, 166))
    sheet.alpha_composite(inv, (8, 8))
    d.text((8 + 97, 8 + 8), "Crafting", fill=label, font=font)
    chest = load(GUI + "container/generic_54.png").crop((0, 0, 176, 222))
    sheet.alpha_composite(chest, (196, 8))
    d.text((196 + 8, 8 + 6), "Large Chest", fill=label, font=font)
    d.text((196 + 8, 8 + 128), "Inventory", fill=label, font=font)

    x0 = 388
    y = 8
    for name, text, color in (("button", "Done", (255, 255, 255)), ("button_highlighted", "Done (hover)", (255, 255, 255)),
                              ("button_disabled", "Disabled", (160, 160, 160))):
        sheet.alpha_composite(nine_slice(load(GUI + f"sprites/widget/{name}.png"), 200, 20, 3), (x0, y))
        d.text((x0 + 70, y + 5), text, fill=(*color, 255), font=font)
        y += 26
    sheet.alpha_composite(nine_slice(load(GUI + "sprites/widget/slider.png"), 200, 20, 1), (x0, y))
    sheet.alpha_composite(load(GUI + "sprites/widget/slider_handle.png"), (x0 + 120, y))
    d.text((x0 + 60, y + 5), "FOV: 70", fill=(255, 255, 255, 255), font=font)
    y += 26
    sheet.alpha_composite(nine_slice(load(GUI + "sprites/widget/text_field_highlighted.png"), 200, 20, 1), (x0, y))
    d.text((x0 + 4, y + 5), "My World_", fill=(224, 224, 224, 255), font=font)
    y += 26
    for i, name in enumerate(("checkbox", "checkbox_highlighted", "checkbox_selected", "checkbox_selected_highlighted")):
        sheet.alpha_composite(load(GUI + f"sprites/widget/{name}.png"), (x0 + i * 26, y))
    sheet.alpha_composite(nine_slice(load(GUI + "sprites/widget/scroller_background.png"), 6, 60, 1), (x0 + 120, y))
    sheet.alpha_composite(nine_slice(load(GUI + "sprites/widget/scroller.png"), 6, 24, 1), (x0 + 120, y + 8))
    y += 30
    for i, name in enumerate(("tab_selected", "tab", "tab_highlighted")):
        sheet.alpha_composite(nine_slice(load(GUI + f"sprites/widget/{name}.png"), 84, 24, 2), (x0 + i * 86, y))
        d.text((x0 + i * 86 + 22, y + 9), ("Game", "World", "More")[i], fill=(255, 255, 255, 255), font=font)

    tip_bg = nine_slice(load(GUI + "sprites/tooltip/background.png"), 176, 76, 9)
    tip_fr = nine_slice(load(GUI + "sprites/tooltip/frame.png"), 176, 76, 10)
    tx, ty = 400, 260
    sheet.alpha_composite(tip_bg, (tx, ty))
    sheet.alpha_composite(tip_fr, (tx, ty))
    for i, (line, color) in enumerate((("Diamond Sword", (85, 255, 255)), ("When in Main Hand:", (170, 170, 170)),
                                       (" 7 Attack Damage", (0, 170, 0)), ("minecraft:diamond_sword", (85, 85, 85)))):
        d.text((tx + 13, ty + 13 + i * 12), line, fill=(*color, 255), font=font)

    creative = load(GUI + "container/creative_inventory/tab_items.png").crop((0, 0, 195, 136))
    sheet.alpha_composite(load(GUI + "sprites/container/creative_inventory/tab_top_selected_1.png"), (8, 342))
    sheet.alpha_composite(load(GUI + "sprites/container/creative_inventory/tab_top_unselected_2.png"), (36, 344))
    sheet.alpha_composite(creative, (8, 370))
    furnace = load(GUI + "container/furnace.png").crop((0, 0, 176, 166))
    sheet.alpha_composite(furnace, (212, 342))
    sheet.resize((sheet.width * 2, sheet.height * 2), Image.NEAREST).save(path)
    print(f"Preview: {path}")


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--preview", type=Path, help="also write a contact sheet PNG here")
    args = parser.parse_args()
    build()
    if args.preview:
        preview(args.preview)
