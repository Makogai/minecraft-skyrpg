#!/usr/bin/env python3
"""Builds the "classic stat symbols" pack: the stat icon font image redrawn with the plain Unicode
symbols SkyRPG used before its own pixel icons (❤ ❈ ❁ ☣ ...), taken from Minecraft's own Unifont so they
look exactly like they did in chat and tooltips.

    python tools/theme/generate_classic_icons.py

Writes core/src/main/resources/resourcepacks/skyrpg_classic_icons/ (a built-in pack the "Classic stat
symbols" setting turns on). It only swaps skyrpg_core:textures/font/stat_icons.png; the font file and the
private-use characters the code sends stay the same, so switching needs no restart, just a resource reload.
"""
import json
import zipfile
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parent.parent.parent
PACK = ROOT / "core" / "src" / "main" / "resources" / "resourcepacks" / "skyrpg_classic_icons"
ASSETS = Path.home() / ".gradle" / "caches" / "fabric-loom" / "assets"
CELL = 16  # Unifont glyphs are 16 px tall; the font file draws them at height 8 like vanilla does

# In font order: U+E100 .. U+E117 (see StatTypes and core's assets/minecraft/font/default.json).
SYMBOLS = [
    "❤",  # health
    "❈",  # defense
    "❁",  # strength
    "☣",  # crit chance
    "☠",  # crit damage
    "✦",  # speed
    "✎",  # intelligence
    "⸕",  # mining speed
    "☘",  # mining fortune
    "☘",  # farming fortune
    "☘",  # foraging fortune
    "✯",  # magic find
    "☯",  # farming insight
    "☯",  # mining insight
    "☯",  # foraging insight
    "☯",  # combat insight
    "❁",  # damage
    "✎",  # mana
    "⛁",  # coins
    "★",  # level
    "☂",  # fishing speed
    "α",  # sea creature chance
    "☘",  # fishing fortune
    "☯",  # fishing insight
]


def unifont_zip():
    for index in sorted((ASSETS / "indexes").glob("*.json"), key=lambda p: p.stat().st_mtime, reverse=True):
        objects = json.loads(index.read_text(encoding="utf-8"))["objects"]
        entry = objects.get("minecraft/font/unifont.zip")
        if entry:
            digest = entry["hash"]
            return ASSETS / "objects" / digest[:2] / digest
    raise SystemExit("Couldn't find Minecraft's unifont.zip in the Loom asset cache (run the client once).")


def load_glyphs(wanted):
    """Code point -> 16 rows of bits (width 8 or 16) from the .hex files in unifont.zip."""
    glyphs = {}
    with zipfile.ZipFile(unifont_zip()) as archive:
        for name in archive.namelist():
            if not name.endswith(".hex"):
                continue
            for line in archive.read(name).decode("ascii").splitlines():
                code, _, bits = line.partition(":")
                if code and int(code, 16) in wanted:
                    width = len(bits) * 4 // 16
                    glyphs[int(code, 16)] = (width, [int(bits[r * width // 4:(r + 1) * width // 4], 16) for r in range(16)])
    return glyphs


def main():
    wanted = {ord(s) for s in SYMBOLS}
    glyphs = load_glyphs(wanted)
    missing = sorted(hex(c) for c in wanted - glyphs.keys())
    if missing:
        raise SystemExit(f"Unifont has no glyph for {missing}")
    image = Image.new("RGBA", (16 * CELL, 2 * CELL), (0, 0, 0, 0))
    for i, symbol in enumerate(SYMBOLS):
        width, rows = glyphs[ord(symbol)]
        left, top = (i % 16) * CELL, (i // 16) * CELL
        for y, row in enumerate(rows):
            for x in range(width):
                if row >> (width - 1 - x) & 1:
                    image.putpixel((left + x, top + y), (255, 255, 255, 255))
    out = PACK / "assets" / "skyrpg_core" / "textures" / "font" / "stat_icons.png"
    out.parent.mkdir(parents=True, exist_ok=True)
    image.save(out)
    (PACK / "pack.mcmeta").write_text(json.dumps({"pack": {
        "description": "Classic Unicode stat symbols instead of SkyRPG's pixel icons",
        "min_format": 97, "max_format": 97}}, indent=2) + "\n", encoding="utf-8")
    print(f"Wrote {out.relative_to(ROOT)} ({len(SYMBOLS)} symbols)")


if __name__ == "__main__":
    main()
