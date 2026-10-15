#!/usr/bin/env python3
"""Puts the SkyRPG icon set into the mods and the pack.

    python tools/art/install_icons.py [SOURCE_DIR]    # default: icons/ (the full-size images, not committed)

SOURCE_DIR holds the generated images named NN-skyrpg[-part].png (1024x1024) and NN-skyrpg-banner.png. Writes:
  * tools/art/icons/<module>.png: 256x256 masters (committed, so the set survives without the big originals)
  * <module>/src/main/resources/assets/<mod id>/icon.png: 128x128, and "icon" in that mod's fabric.mod.json
    (Mod Menu and the launcher show it)
  * tools/pack/icon.png: the pack's icon, used for the Prism instance (tools/publish_pack.py)
  * tools/art/icons/banner.png: the wide logo, 800 wide
Without SOURCE_DIR images it reinstalls from the committed masters.
"""
import json
import re
import sys
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parents[2]
MASTERS = ROOT / "tools" / "art" / "icons"
# Image name part -> module folder. "skyrpg" is the pack itself and core.
MODULES = {
    "skyrpg": "core", "economy": "economy", "skills": "skills", "hub": "hub", "stats": "stats", "items": "items",
    "npcs": "npcs", "events": "events", "mining": "mining", "utilities": "utils", "farming": "farming",
    "foraging": "foraging", "fishing": "fishing", "pets": "pets", "combat": "combat", "enchanting": "enchants",
    "world": "world", "vfx": "vfx", "chat": "chat", "progress": "progress", "mounts": "mounts", "party": "party",
    "magic": "magic", "helper": "helper", "wiki": "wiki", "welcome": "welcome", "builder": "builder", "brainrots": "brainrots",
}


def part_of(path):
    match = re.match(r"\d+-skyrpg(?:-([a-z]+))?\.png$", path.name)
    if not match:
        return None
    return match.group(1) or "skyrpg"


def main():
    source = Path(sys.argv[1]) if len(sys.argv) > 1 else ROOT / "icons"
    MASTERS.mkdir(parents=True, exist_ok=True)
    found = 0
    if source.is_dir():
        for path in sorted(source.glob("*.png")):
            part = part_of(path)
            image = Image.open(path).convert("RGBA")
            if part == "banner":
                width = 800
                image.resize((width, round(image.height * width / image.width)), Image.LANCZOS).save(MASTERS / "banner.png", optimize=True)
                found += 1
            elif part in MODULES:
                image.resize((256, 256), Image.LANCZOS).save(MASTERS / f"{MODULES[part]}.png", optimize=True)
                found += 1
    print(f"Masters: {found} new from {source}" if found else f"Using the committed masters in {MASTERS}")

    installed = 0
    for module in sorted(set(MODULES.values())):
        master = MASTERS / f"{module}.png"
        mod_json = ROOT / module / "src" / "main" / "resources" / "fabric.mod.json"
        if not master.exists() or not mod_json.exists():
            print(f"  skipped {module} (no icon or no mod)")
            continue
        text = mod_json.read_bytes().decode("utf-8")
        nl = "\r\n" if "\r\n" in text else "\n"
        mod_id = json.loads(text)["id"]
        target = ROOT / module / "src" / "main" / "resources" / "assets" / mod_id / "icon.png"
        target.parent.mkdir(parents=True, exist_ok=True)
        Image.open(master).resize((128, 128), Image.LANCZOS).save(target, optimize=True)
        icon_line = f'"icon": "assets/{mod_id}/icon.png"'
        if '"icon"' not in text:
            text = text.replace(f'  "license": "All-Rights-Reserved",{nl}', f'  "license": "All-Rights-Reserved",{nl}  {icon_line},{nl}', 1)
            if icon_line not in text:
                raise SystemExit(f"Couldn't add the icon to {mod_json}")
            mod_json.write_bytes(text.encode("utf-8"))
        installed += 1
    pack_icon = ROOT / "tools" / "pack" / "icon.png"
    Image.open(MASTERS / "core.png").resize((128, 128), Image.LANCZOS).save(pack_icon, optimize=True)
    print(f"Installed {installed} mod icons and the pack icon ({pack_icon.relative_to(ROOT)}).")


if __name__ == "__main__":
    main()
