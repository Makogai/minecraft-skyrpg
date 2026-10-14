#!/usr/bin/env python3
"""Generates the SkyRPG chat icon font: pixel-art sprites drawn here, laid out in one atlas, plus the bitmap font
definition that maps private-use codepoints (U+E000...) to them.

    python tools/art/generate_chat_icons.py                 # writes the files below
    python tools/art/generate_chat_icons.py --preview DIR   # also writes an 8x labelled contact sheet to DIR

Writes under chat/src/main/resources:
  * assets/skyrpg_chat/textures/font/icons.png   the atlas (16 columns of 10x10 cells)
  * assets/skyrpg_chat/font/icons.json           one bitmap provider (ascent 8, height 10) + a space provider
Also prints the codepoint table (paste it into docs/design/chat.md).

Sprites: masks of up to 9 rows (they are bottom-aligned, so the last row sits on the text baseline) and up to 9
columns; '.' is empty and every other character indexes PAL. Glyph widths are trimmed to the drawn pixels by
Minecraft, and each glyph advances 1px past its right edge. Sprites are drawn in full colour: use them with a white
(or no) text colour. The "tint" group is grey-scale on purpose: the text colour multiplies it.
"""
import json
import sys
from pathlib import Path

from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parent.parent.parent
RES = ROOT / "chat" / "src" / "main" / "resources" / "assets" / "skyrpg_chat"
CELL = 10
COLS = 16
FIRST = 0xE000

PAL = {
    'k': (0x22, 0x1a, 0x2c), 'K': (0x3a, 0x30, 0x4a),
    'w': (255, 255, 255), 'g': (0xc9, 0xce, 0xd8), 'G': (0x8d, 0x96, 0xa8), 'd': (0x58, 0x61, 0x74),
    'r': (0xff, 0x5a, 0x5a), 'R': (0xb0, 0x2a, 0x3a), 'p': (0xff, 0xa8, 0xa8),
    'y': (0xf2, 0xb3, 0x3d), 'Y': (0xb8, 0x79, 0x1f), 'l': (0xff, 0xe0, 0x8a),
    'o': (0xff, 0x8a, 0x2a), 'O': (0xd8, 0x50, 0x1c),
    'b': (0x5c, 0xa8, 0xff), 'B': (0x2e, 0x62, 0xc0), 'a': (0x5c, 0xe1, 0xe6), 'A': (0x2a, 0x9a, 0xa6),
    'n': (0x5c, 0xd6, 0x8a), 'N': (0x2c, 0x8a, 0x52),
    'u': (0xb9, 0x8c, 0xff), 'U': (0x6a, 0x46, 0xb0), 'v': (0xf0, 0x8c, 0xe8),
    'm': (0x9a, 0x6a, 0x3a), 'M': (0x5e, 0x3c, 0x20), 's': (0x9a, 0xa0, 0xaa), 'S': (0x6a, 0x70, 0x7a),
}

# (name, group, mask). Order = codepoint order from U+E000.
S = []


def add(name, group, *rows):
    S.append((name, group, rows))


# ---- emotes -----------------------------------------------------------------------------------------------
add("heart", "emote",
    ".kkk.kkk.", "kpprkrrrk", "kprrrrrRk", "krrrrrrRk", ".krrrrRk.", "..krrRk..", "...kRk...", "....k....")
add("star", "emote",
    "....k....", "...kyk...", "kkkkylkkk", "kyllllllk", ".kyllllk.", "..kylYk..", ".kylYYYk.", ".kYkkkYk.", "..k...k..")
add("skull", "emote",
    "..kkkkk..", ".kwwwwwk.", "kwwwwwwgk", "kwkkwkkgk", "kwkkwkkgk", ".kwwkwgk.", "..kwwwk..", "..kgkgk..", "...kkk...")
add("fire", "emote",
    "....k....", "...kok.k.", "..kooOkok", "..koyOooK", ".kooylyoK", ".koylllyK", ".kooyllyK", "..kooyyOk", "...kkkk..")
add("coin", "emote",
    "..kkkkk..", ".klyyyyk.", "klyyYyyYk", "kyyYllYyk", "kyyYllYyk", "kyyYYYYyk", "kyyyyyyYk", ".kYYYYYk.", "..kkkkk..")
add("gem", "emote",
    "", ".kkkkkkk.", "kaawwaaAk", "kakaaakAk", ".kaaaaAk.", "..kaaAk..", "...kAk...", "....k....")
add("crown", "emote",
    "", "k...k...k", "kk.kyk.kk", "kykkylkyk", "kyyylllyk", "kyyyyyyyk", "kYrYbYrYk", "kYYYYYYYk", ".kkkkkkk.")
add("sword", "emote",
    "......kk.", ".....kwgk", "....kwgk.", "...kwgk..", "k.kwgk...", "kkwgk....", ".kyk.....", "kmkyk....", "kk.kk....")
add("shield", "emote",
    "kkkkkkkkk", "kbbbbBBBk", "kbwwbBBBk", "kbwbbBBBk", "kbbbbBBBk",
    ".kbbbBBk.", "..kbbBk..", "...kkk...")

add("pickaxe", "emote",
    "", ".kkggggk.", "kgwwwggGk", "kgkkmkkGk", "kk..km.kk", ".....km..", "....km...", "....kmk..", ".....k...")
add("axe", "emote",
    "", "..kkk....", ".kwggk...", "kwwggkmk.", "kwggGkmk.", ".kGGkmk..", "....kmk..", "...kmk...", "...kk....")
add("hoe", "emote",
    "", ".kkkkk...", "kgwwgGk..", "kkkkgGkmk", ".....kmk.", "....kmk..", "...kmk...", "..kmk....", "..kk.....")
add("rod", "emote",
    ".....kkk.", "....kmk.k", "...kmk..k", "..kmk...k", ".kmk....k", "kmk.....k", "kk.....kbk", "........kk",)
add("potion", "emote",
    "...kkk...", "...kgk...", "...kgk...", "..kkgkk..", ".kuuuuuk.",
    "kuvuuuuUk", "kuuuuuUUk", ".kUUUUUk.", "..kkkkk..")

add("trophy", "emote",
    "", "kkkkkkkkk", "kyllyyyYk", "kyllyyyYk", ".kyyyyYk.", "..kyyYk..", "...kyk...", "..kyyyk..", ".kkkkkkk.")
add("lightning", "emote",
    "", "....kkkk.", "...kllyk.", "..kllyk..", ".kllyyyk.", "..kkkyyk.", "....kyk..", "...kyk...", "...kk....")
add("moon", "emote",
    "", "..kkkk...", ".kllyk...", "kllyk....", "kly.k....", "kly.k....", "klyYk..k.", ".klyYkk..", "..kkkk...")
add("sun", "emote",
    "....o....", ".o..o..o.", "..kkkkk..", "o.kllyk.o", "ookllyk.oo", "o.kyyYk.o", "..kkkkk..", ".o..o..o.", "....o....")
add("check", "emote",
    "", "......kk.", ".....knk.", "k...knk..", "kk.knk...", "knkknk...", ".knnk....", "..kk.....")
add("cross", "emote",
    "", "kk...kk..", "krk.krk..", ".krkrk...", "..krk....", ".krkrk...", "krk.krk..", "kk...kk..")
add("wave", "emote",
    "", "..k.k.k..", ".kwkwkwk.", ".kwwwwwkk", "kkwwwwwwk", "kwwwwwwgk", ".kwwwwgk.", "..kwwgk..", "...kkk...")
add("note", "emote",
    "", "...kkkkk.", "...kuuuk.", "...ku..k.", "...ku..k.", "..kuk.kuk", ".kuukkuuk", "..kk..kk.")
add("gg", "emote",
    "", ".kkk.kkk.", "kgwkkgwkk", "kwkkkwkkk", "kwkgkwkgk", "kgwwkgwwk", ".kkk.kkk.")

# ---- misc UI icons ----------------------------------------------------------------------------------------
add("envelope", "ui",
    "", "", "kkkkkkkkk", "kwkwwwkwk", "kwwkwkwwk", "kwwwkwwwk", "kgggggggk", "kkkkkkkkk")
add("robot", "ui",
    "....y....", "....k....", ".kkkkkkk.", "kGGGGGGGk", "kGaGGGaGk", "kGGGGGGGk", "kGkdkdkGk", ".kkkkkkk.", "..k...k..")
add("join", "ui",
    "", "...n.....", "....n....", "nnnnnn...", "nnnnnnn..", "nnnnnn...", "....n....", "...n.....")
add("leave", "ui",
    "", ".....r...", "....r....", "...rrrrrr", "..rrrrrrr", "...rrrrrr", "....r....", ".....r...")
add("welcome", "ui",
    "", "...y.....", "..yly..y.", "ylllllyly", "..yly..y.", "...y.....", "y.....y..", ".........")

# ---- skills ------------------------------------------------------------------------------------------------
add("skill_combat", "skill",
    "k.......k", "kk.....kk", "gkk...kkg", ".gwkkkkwg.", "..kwwkwk..", "...kkwk...", "..kykyk...", ".kmk.kmk..", ".kk...kk..")
add("skill_farming", "skill",
    "...y.....", "..yly.y..", ".kylykyly", "..yYy.yYy", ".yly.yly.", "..yYkyYy.", "...nkn...", "...nkn...", "..nNkNn..")
add("skill_fishing", "skill",
    "", "", "....kkkk.", "..kkbbbbk", ".kbbbwbbk", "kabbbbbbBk", ".kabbbBBk", "..kkkBBk.", "....kkk..")
add("skill_foraging", "skill",
    "...kkk...", "..knnnk..", ".knnNnnk.", "knnnnNnnk", ".knNnnnk.", "..kknkk...", "...kmk...", "...kmk...", "..kkmkk..")
add("skill_mining", "skill",
    "..kkkkk..", ".ksssssk.", "ksaasssSk", "ksaaSssSk", "kssssaaSk",
    "kssSsaaSk", "ksSSsSSSk", ".kkkkkkk.")

# ---- rarity gems -------------------------------------------------------------------------------------------
GEMS = [("common", 'w', 'g', 'G'), ("uncommon", 'n', 'N', 'N'), ("rare", 'b', 'B', 'B'), ("epic", 'u', 'U', 'U'),
        ("legendary", 'y', 'Y', 'l'), ("mythic", 'r', 'R', 'p')]
for _name, _hi, _lo, _gl in GEMS:
    add("rarity_" + _name, "rarity",
        "...kkk...", "..k" + _gl + _hi + _hi + "k..", ".k" + _gl + _hi * 3 + _lo + "k.", ".k" + _hi * 4 + _lo + "k.",
        "..k" + _hi + _lo + _lo + "k..", "...k" + _lo + "k...", "....k....")

# ---- badge frame / pills (grey-scale: the text colour tints them) ---------------------------------------------
add("badge_left", "frame",
    "...ww", "..w..", ".w...", ".w...", ".w...",
    ".w...", ".w...", "..w..", "...ww")

add("badge_fill", "frame",
    "wwww", "wwww", "wwww", "wwww", "wwww",
    "wwww", "wwww", "wwww", "wwww")

add("badge_right", "frame",
    "ww...", "..w..", "...w.", "...w.", "...w.",
    "...w.", "...w.", "..w..", "ww...")

add("pill_left", "frame",
    "....w", "...w.", "..w..", ".w...", "w....",
    ".w...", "..w..", "...w.", "....w")

add("pill_fill", "frame",
    "wwww", "wwww", "wwww", "wwww", "wwww",
    "wwww", "wwww", "wwww", "wwww")

add("pill_right", "frame",
    "w....", ".w...", "..w..", "...w.", "....w",
    "...w.", "..w..", ".w...", "w....")

add("level", "tint",
    "....w....", "...www...", "wwwwwwwww", ".wwwwwww.", "..wwwww..", "..wwwww..", ".wwg.gww.", ".ww...ww.", "..w...w..")


def mask_image(rows):
    rows = list(rows)
    rows = [""] * (9 - len(rows)) + rows if len(rows) < 9 else rows
    return rows


def sprite(rows, hi_override=None):
    rows = mask_image(rows)
    img = Image.new("RGBA", (CELL, CELL), (0, 0, 0, 0))
    for y, row in enumerate(rows):
        for x, ch in enumerate(row):
            if ch == '.' or x >= CELL:
                continue
            img.putpixel((x, y), PAL[ch] + (255,))
    return img


def codepoint(i):
    return FIRST + i


def main():
    preview = None
    if "--preview" in sys.argv:
        preview = Path(sys.argv[sys.argv.index("--preview") + 1])
    rows_needed = (len(S) + COLS - 1) // COLS
    atlas = Image.new("RGBA", (COLS * CELL, rows_needed * CELL), (0, 0, 0, 0))
    for i, (name, group, rows) in enumerate(S):
        atlas.paste(sprite(rows), ((i % COLS) * CELL, (i // COLS) * CELL))
    (RES / "textures" / "font").mkdir(parents=True, exist_ok=True)
    (RES / "font").mkdir(parents=True, exist_ok=True)
    atlas.save(RES / "textures" / "font" / "icons.png")

    chars = []
    for r in range(rows_needed):
        line = ""
        for c in range(COLS):
            i = r * COLS + c
            line += chr(codepoint(i)) if i < len(S) else "\u0000"
        chars.append(line)
    # Unused cells must not map to anything: use U+E0FF.. spare codes that are never typed. A bitmap provider
    # needs full rows, so pad with the "no glyph" char U+0000 (Minecraft skips it).
    font = {"providers": [
        {"type": "bitmap", "file": "skyrpg_chat:font/icons.png", "ascent": 8, "height": 10, "chars": chars},
        # Pulls the next glyph back over the 1px gap Minecraft adds after each bitmap glyph.
        {"type": "space", "advances": {"": -1, "": 1, "": 2, "": 3, "": 4}},
    ]}
    (RES / "font" / "icons.json").write_text(json.dumps(font, indent=2, ensure_ascii=True) + "\n", encoding="utf-8")

    print("Codepoints (font skyrpg_chat:icons):")
    for i, (name, group, rows) in enumerate(S):
        print(f"  U+{codepoint(i):04X}  {name:<18} {group}")
    print("  U+E0F0 space -1px, U+E0F1..E0F4 space +1..+4px")

    if preview:
        preview.mkdir(parents=True, exist_ok=True)
        scale = 8
        cell = CELL * scale
        cols = 8
        n = len(S)
        rws = (n + cols - 1) // cols
        # Three backgrounds so the outlines are checked on both dark and light chat panels.
        sheet = Image.new("RGB", (cols * (cell + 16), rws * (cell + 30)), (28, 32, 44))
        d = ImageDraw.Draw(sheet)
        for i, (name, group, rows) in enumerate(S):
            x = (i % cols) * (cell + 16) + 8
            y = (i // cols) * (cell + 30) + 6
            d.rectangle([x, y, x + cell, y + cell], fill=(44, 50, 66))
            d.line([x, y + 8 * scale + scale, x + cell, y + 8 * scale + scale], fill=(80, 40, 40))  # baseline
            tile = sprite(rows).resize((cell, cell), Image.NEAREST)
            sheet.paste(tile, (x, y), tile)
            d.text((x, y + cell + 4), f"{codepoint(i):04X} {name}", fill=(200, 205, 216))
        sheet.save(preview / "icons_preview.png")


if __name__ == "__main__":
    main()
