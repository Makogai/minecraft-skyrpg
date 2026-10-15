#!/usr/bin/env python3
"""A stand-in mod icon in the set's style (the gold-framed tile of tools/art/icons/builder.png), made from one of the
mod's 16x16 item textures scaled up, on a glow of the given colour. For when a new mod needs an icon before a proper
one is drawn; install_icons.py then puts it in the mod.

    python tools/art/placeholder_icon.py <module> <item texture png> <glow hex>
    python tools/art/placeholder_icon.py crates crates/src/main/resources/assets/skyrpg_crates/textures/item/hallows_crate.png FF8A1E
"""
import sys
from collections import deque
from pathlib import Path

import numpy as np
from PIL import Image, ImageFilter

HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[1]
NAVY = np.array([10, 22, 48], np.float32)


def interior(frame):
    """The pixels inside the gold border, by flood fill from the middle over anything that isn't gold."""
    a = np.asarray(frame, np.int32)
    gold = (a[..., 0] > 180) & (a[..., 1] > 120) & (a[..., 2] < 120) & (a[..., 3] > 200)
    h, w = gold.shape
    inside = np.zeros((h, w), bool)
    todo = deque([(h // 2, w // 2)])
    while todo:
        y, x = todo.popleft()
        if 0 <= y < h and 0 <= x < w and not inside[y, x] and not gold[y, x]:
            inside[y, x] = True
            todo.extend(((y + 1, x), (y - 1, x), (y, x + 1), (y, x - 1)))
    return inside


def main():
    module, texture, glow_hex = sys.argv[1], ROOT / sys.argv[2], sys.argv[3]
    glow = np.array([int(glow_hex[i:i + 2], 16) for i in (0, 2, 4)], np.float32)
    frame = Image.open(HERE / "icons" / "builder.png").convert("RGBA")
    W, H = frame.size
    inside = interior(frame)
    yy, xx = np.mgrid[0:H, 0:W].astype(np.float32)
    d = np.sqrt(((xx - W * 0.5) / (W * 0.42)) ** 2 + ((yy - H * 0.5) / (H * 0.42)) ** 2)
    t = np.clip(1 - d, 0, 1) ** 1.4 * 0.8
    bg = NAVY * (1 - t[..., None]) + glow * t[..., None]
    out = np.asarray(frame, np.float32).copy()
    out[inside, :3] = bg[inside]
    out[inside, 3] = 253
    icon = Image.fromarray(out.astype(np.uint8), "RGBA")
    item = Image.open(texture).convert("RGBA")
    item = item.crop(item.getbbox())
    scale = int(min(W * 0.6 / item.width, H * 0.6 / item.height))
    big = item.resize((item.width * scale, item.height * scale), Image.NEAREST)
    px, py = (W - big.width) // 2, (H - big.height) // 2
    shadow = Image.new("RGBA", icon.size, (0, 0, 0, 0))
    shadow.paste((6, 10, 24, 170), (px + 6, py + 6), big.split()[3])
    icon.alpha_composite(shadow.filter(ImageFilter.GaussianBlur(4)))
    icon.alpha_composite(big, (px, py))
    final = np.asarray(icon).copy()
    border = ~inside & (np.asarray(frame)[..., 3] > 0)
    final[border] = np.asarray(frame)[border]
    final[~inside & ~border] = 0
    target = HERE / "icons" / f"{module}.png"
    Image.fromarray(final, "RGBA").save(target, optimize=True)
    print("wrote", target)


if __name__ == "__main__":
    main()
