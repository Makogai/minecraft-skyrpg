#!/usr/bin/env python3
"""Draws the Brainrots mod icon in the style of the others: the gold-framed tile from the Builder icon, its inside
repainted with a navy-to-pink glow, Tung Tung Tung Sahur in the middle and a few music notes (they all say their name).

    python tools/art/make_brainrots_icon.py      # writes tools/art/icons/brainrots.png (then run install_icons.py)
"""
import sys
from collections import deque
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw, ImageFilter

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))
sys.path.insert(0, str(HERE / "brainrots"))
import cube_model as cm  # noqa: E402
import tung_tung_sahur  # noqa: E402

ICONS = HERE / "icons"
KEY = (255, 0, 255)
PINK = np.array([255, 122, 217], np.float32)
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


def model_picture(size):
    M, paint = tung_tung_sahur.build()
    M.pack(sizes=(64, 128))
    tex, glow = paint(M)
    pic = cm.render(M, tex, glow, yaw=-0.55, pitch=0.25, scale=6.5, size=(size, size), center=(0.5, 0.5), bg=KEY, ground=False,
                    look_at=(0, 9, 0))
    a = np.asarray(pic.convert("RGB"), np.int32)
    alpha = np.where((a[..., 0] == KEY[0]) & (a[..., 1] == KEY[1]) & (a[..., 2] == KEY[2]), 0, 255).astype(np.uint8)
    rgba = Image.fromarray(np.dstack([a.astype(np.uint8), alpha]), "RGBA")
    return rgba.crop(rgba.getbbox())


def note(draw, x, y, s, color):
    """A pixel eighth note: head, stem and flag, s pixels to a step."""
    draw.rectangle([x, y + 4 * s, x + 2 * s - 1, y + 6 * s - 1], fill=color)
    draw.rectangle([x + 2 * s, y, x + 3 * s - 1, y + 5 * s - 1], fill=color)
    draw.rectangle([x + 3 * s, y + s, x + 4 * s - 1, y + 2 * s - 1], fill=color)
    draw.rectangle([x + 4 * s, y + 2 * s, x + 5 * s - 1, y + 3 * s - 1], fill=color)


def main():
    frame = Image.open(ICONS / "builder.png").convert("RGBA")
    W, H = frame.size
    inside = interior(frame)
    # Navy with a pink glow rising from behind the brainrot.
    yy, xx = np.mgrid[0:H, 0:W].astype(np.float32)
    d = np.sqrt(((xx - W * 0.5) / (W * 0.42)) ** 2 + ((yy - H * 0.58) / (H * 0.46)) ** 2)
    t = np.clip(1 - d, 0, 1) ** 1.3 * 0.95
    bg = NAVY * (1 - t[..., None]) + PINK * t[..., None]
    out = np.asarray(frame, np.float32).copy()
    out[inside, :3] = bg[inside]
    out[inside, 3] = 253
    icon = Image.fromarray(out.astype(np.uint8), "RGBA")

    # The brainrot, big and pixel-sharp, with a soft dark shadow so it stands off the glow.
    pic = model_picture(220)
    target_h = int(H * 0.66)
    pic = pic.resize((max(1, round(pic.width * target_h / pic.height)), target_h), Image.NEAREST)
    px, py = (W - pic.width) // 2 + 6, int(H * 0.86) - pic.height
    shadow = Image.new("RGBA", icon.size, (0, 0, 0, 0))
    shadow.paste((6, 10, 24, 150), (px + 5, py + 5), pic.split()[3])
    icon.alpha_composite(shadow.filter(ImageFilter.GaussianBlur(4)))
    icon.alpha_composite(pic, (px, py))

    # Music notes floating up, and a couple of sparkles.
    draw = ImageDraw.Draw(icon)
    note(draw, 46, 58, 4, (255, 236, 250, 255))
    note(draw, 186, 44, 3, (255, 176, 232, 255))
    note(draw, 196, 120, 3, (255, 236, 250, 230))
    for sx, sy, r in ((70, 168, 3), (176, 196, 2), (60, 110, 2)):
        draw.rectangle([sx - r, sy, sx + r, sy], fill=(255, 255, 255, 220))
        draw.rectangle([sx, sy - r, sx, sy + r], fill=(255, 255, 255, 220))
    # Keep everything inside the frame.
    final = np.asarray(icon).copy()
    final[~inside & ~(np.asarray(frame)[..., 3] > 0)] = 0
    keep = np.asarray(frame).copy()
    border = ~inside & (np.asarray(frame)[..., 3] > 0)
    final[border] = keep[border]
    Image.fromarray(final, "RGBA").save(ICONS / "brainrots.png", optimize=True)
    print("wrote", ICONS / "brainrots.png")


if __name__ == "__main__":
    main()
