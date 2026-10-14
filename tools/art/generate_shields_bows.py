#!/usr/bin/env python3
"""Draws SkyRPG's seven shields and seven bows as 3D item models (``elements`` cuboids) with their textures.

    python tools/art/generate_shields_bows.py                 # writes the assets
    python tools/art/generate_shields_bows.py --preview DIR   # also writes offline renders of every model to DIR
    python tools/art/generate_shields_bows.py --only oak_buckler,hunters_bow

Only files of these fourteen items are written (item definitions, models, textures), all under
items/src/main/resources/assets/skyrpg_items/; nothing else is touched.

Shields: each is a height-field of 24x24 cells (a cell is 0.5-0.85 model units, so the tiers grow from 9 to 20 units wide).
A tier's silhouette, its raised rims, ridges and bosses are layers on that grid; every run of cells with the same layer becomes
one cuboid whose front face shows its slice of a painted front texture. Glowing parts (ember cracks, storm veins, the Astral
Aegis' stars) are a second, transparent texture drawn on flat quads in front of the cuboids with ``light_emission`` set, so they
shine in the dark (the Astral Aegis' is animated). Models: ``<id>`` (carried) and ``<id>_blocking`` (raised).

Bows: each is a limb profile (stacked boxes with a curve and a taper) plus ornaments, with a string and, while drawing, a nocked
arrow; ``<id>``, ``<id>_pulling_0``, ``_1``, ``_2``.
"""
import argparse
import json
import math
import os
import random
import sys
from pathlib import Path

import numpy as np
from PIL import Image

sys.path.insert(0, str(Path(__file__).resolve().parent))
from sb_common import ItemModel, Ramp, clamp01, fbm, in_poly, path_dist, render, seg_dist, vnoise  # noqa: E402
import generate_weapon_art as wa  # noqa: E402  (its palettes)

ROOT = Path(__file__).resolve().parents[2]
ASSETS = ROOT / "items" / "src" / "main" / "resources" / "assets" / "skyrpg_items"
NS = "skyrpg_items"

N = 24            # shield grid
PPC = 4           # texture pixels per cell
T = N * PPC       # front texture size
FRONT = "south"   # the face that carries the painted front (the one a viewer at +z sees)

# ----------------------------------------------------------------------------------------------- palettes
OAK, DARK_WOOD, IRON, STEEL, SKYIRON, STORM = wa.OAK, wa.DARK_WOOD, wa.IRON, wa.STEEL, wa.SKYIRON, wa.STORM
CINDER, EMBER, GOLD, COPPER, PATINA, VOID, STAR, LEATHER = wa.CINDER, wa.EMBER, wa.GOLD, wa.COPPER, wa.PATINA, wa.VOID, wa.STAR, wa.LEATHER
RED_LEATHER, BRONZE, PLANK = wa.RED_LEATHER, wa.BRONZE, wa.PLANK
ICE = Ramp("1d3a66", "2f5f9a", "5598cc", "8ccaee", "c8ecff", "ffffff")
FROST_WOOD = Ramp("2a3a4a", "46607a", "6c8ca8", "98b8d0", "c4dcec", "eef8ff")
BLACKSTEEL = Ramp("08080b", "141419", "22222a", "32323c", "46464f", "62626d")
STORMIRON = Ramp("0e1424", "1a2540", "2a3a5c", "3d5278", "587096", "7c96b8")
ASTRAL = Ramp("06031a", "100830", "1c1052", "2c1c7a", "4430a8", "7a68e0")
STARSTEEL = Ramp("30365c", "4c5486", "7a84bc", "a8b2e4", "d8defc", "ffffff")
WILLOW = Ramp("1a1a22", "2c2c3c", "40405a", "58587a", "747498", "9696b8")
STARWOOD = Ramp("120c2a", "221846", "34286a", "4a3c92", "6a5cb8", "9c90e0")
EMBERWOOD = Ramp("1c0c06", "36170a", "5c2810", "86401a", "b05c26", "d88444")
MISTPINE = Ramp("2a3438", "465458", "667a80", "8aa0a6", "b0c6cc", "dcecf0")
IRONBARK = Ramp("231a10", "3b2c1a", "56412a", "755a3c", "947652", "b89874")
SKYWIRE = Ramp("2a3d55", "46688a", "6a96bc", "98c4e4", "c8ecff", "f4fcff")
STRING = wa.STRING
CORD_EMBER = Ramp("4a0e00", "8a2200", "cc4400", "ff7a10", "ffb43c", "fff0a0")
CYAN_GLOW = Ramp("0a2a4a", "14508a", "2a8ad0", "5cc0f8", "a8e4ff", "ffffff")


def mix(c1, c2, t):
    return tuple(int(round(c1[i] + (c2[i] - c1[i]) * t)) for i in range(3))


# ----------------------------------------------------------------------------------------------- shields: grid helpers
def cells():
    for cy in range(N):
        for cx in range(N):
            yield cx, cy, cx + 0.5, cy + 0.5


def poly(points):
    m = np.zeros((N, N), dtype=bool)
    for cx, cy, fx, fy in cells():
        m[cy, cx] = in_poly(fx, fy, points)
    return m


def disc(cx0, cy0, r):
    m = np.zeros((N, N), dtype=bool)
    for cx, cy, fx, fy in cells():
        m[cy, cx] = math.hypot(fx - cx0, fy - cy0) <= r
    return m


def edge_dist(mask):
    """Distance in cells from each cell centre to the nearest cell outside the shape (the grid's border counts as outside)."""
    outside = [(x + 0.5, y + 0.5) for y in range(-1, N + 1) for x in range(-1, N + 1)
               if not (0 <= x < N and 0 <= y < N and mask[y, x])]
    d = np.zeros((N, N))
    for cx, cy, fx, fy in cells():
        if mask[cy, cx]:
            d[cy, cx] = min(math.hypot(fx - ox, fy - oy) for ox, oy in outside)
    return d


class Shield:
    """One shield's height-field and painters. Subclasses fill ``mask`` and ``layer`` and implement ``paint``/``glow``."""
    id = ""
    cs = 0.6                 # model units per cell
    yoff = 0.0               # shift of the shape in model y
    frames = 1               # glow animation frames
    back = DARK_WOOD
    edge = IRON

    def __init__(self):
        self.mask = np.zeros((N, N), dtype=bool)
        self.layer = np.zeros((N, N), dtype=int)
        self.build()
        self.ed = edge_dist(self.mask)
        self.finish()

    def build(self):
        raise NotImplementedError

    def finish(self):
        pass

    # --- painting: returns (ramp, shade index) for the front, None for outside
    def paint(self, fx, fy, h, ed):
        raise NotImplementedError

    def glow(self, fx, fy, h, ed, frame):
        """RGBA (0..255) of the emissive layer at this point, or None."""
        return None

    def side_slot(self, cx0, cx1, cy0, cy1):
        """The slot (x, y in a 16x16 texture) holding the average colour of a block of cells, for the sides of raised parts."""
        region = self.front_arr[cy0 * PPC:(cy1 + 1) * PPC, cx0 * PPC:(cx1 + 1) * PPC]
        solid = region[region[:, :, 3] > 0]
        rgb = tuple(int(v * 0.78) // 6 * 6 for v in solid[:, :3].mean(axis=0)) if len(solid) else (60, 60, 60)
        if rgb not in self.side_colors:
            self.side_colors.append(rgb)
        i = self.side_colors.index(rgb)
        return i % 16, i // 16

    def layer_z(self, h):
        base = [0, 1.1, 1.7, 2.3, 2.9, 3.4, 3.8][min(h, 6)]
        return base * (0.55 + self.cs * 0.7)


def front_images(sh):
    """The painted front, the emissive layer (a list of frames) and the back, each T x T."""
    layer_px = np.zeros((T, T), dtype=int)
    mask_px = np.zeros((T, T), dtype=bool)
    ed_px = np.zeros((T, T))
    for py in range(T):
        for px in range(T):
            cx, cy = px // PPC, py // PPC
            if sh.mask[cy, cx]:
                mask_px[py, px] = True
                layer_px[py, px] = sh.layer[cy, cx]
                ed_px[py, px] = sh.ed[cy, cx]
    front = Image.new("RGBA", (T, T), (0, 0, 0, 0))
    back = Image.new("RGBA", (T, T), (0, 0, 0, 0))
    glows = [Image.new("RGBA", (T, T), (0, 0, 0, 0)) for _ in range(sh.frames)]
    fp, bp = front.load(), back.load()
    for py in range(T):
        for px in range(T):
            if not mask_px[py, px]:
                continue
            fx, fy = (px + 0.5) / PPC, (py + 0.5) / PPC
            h = layer_px[py, px]
            ramp, idx = sh.paint(fx, fy, h, ed_px[py, px])
            # Bevels: light from the top left. Raised edges catch it, the foot of a raised part and the far edges fall into shadow.
            def lay(x, y):
                return layer_px[y, x] if 0 <= x < T and 0 <= y < T and mask_px[y, x] else 0
            up, left, down, right = lay(px, py - 1), lay(px - 1, py), lay(px, py + 1), lay(px + 1, py)
            up2, left2 = lay(px, py - 2), lay(px - 2, py)
            if up < h or left < h:
                idx += 1.1
            elif up2 < h or left2 < h:
                idx += 0.45
            if down < h or right < h:
                idx -= 1.2
            if up > h or left > h:
                idx -= 0.9
            outer = not (up and left and down and right)
            rgb = ramp[idx]
            if outer:
                rgb = tuple(int(v * 0.45) for v in ramp[max(0, idx - 2.5)])
            fp[px, py] = rgb + (255,)
            # The back: plain, a little lighter at the rim.
            n = fbm(fx * 1.3, fy * 0.5, 9) * 1.4
            bp[px, py] = (tuple(int(v * 0.5) for v in sh.back[1.0 + n + (0.6 if ed_px[py, px] < 1.6 else 0)]) if outer
                          else sh.back[1.0 + n + (0.6 if ed_px[py, px] < 1.6 else 0)]) + (255,)
            for f in range(sh.frames):
                g = sh.glow(fx, fy, h, ed_px[py, px], f)
                if g is not None and g[3] > 0:
                    glows[f].putpixel((px, py), tuple(int(v) for v in g))
    return front, glows, back


def misc_image(sh):
    """16x16: the top half is the shield's edge material (side faces), the bottom half is leather for straps and the grip."""
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 255))
    px = img.load()
    for y in range(8):
        for x in range(16):
            px[x, y] = sh.edge[1.6 + 1.6 * fbm(x * 0.5, y * 0.9, 4) + (0.8 if y in (0, 1) else 0) - (0.8 if y in (6, 7) else 0)] + (255,)
    for y in range(8, 16):
        for x in range(16):
            px[x, y] = LEATHER[1.6 + 1.5 * fbm(x * 0.4, y * 0.7, 2) - (0.8 if y in (8, 15) else 0)] + (255,)
    return img


def shield_model(sh, front_glows_nonempty, blocking):
    """The cuboids: runs of equal-height cells, merged vertically where they match."""
    m = ItemModel()
    m.textures = {"front": f"{NS}:item/{sh.id}_front", "back": f"{NS}:item/{sh.id}_back", "misc": f"{NS}:item/{sh.id}_misc",
                  "side": f"{NS}:item/{sh.id}_side", "particle": f"{NS}:item/{sh.id}_misc", "layer0": f"{NS}:item/{sh.id}_icon"}
    if front_glows_nonempty:
        m.textures["glow"] = f"{NS}:item/{sh.id}_glow"
    cs = sh.cs
    sh.side_colors = []
    zb = 8.0 - 0.6  # back of the plate

    def mx(c):
        return 8.0 + (c - N / 2) * cs

    def my(c):
        return 8.0 + (N / 2 - c) * cs + sh.yoff

    runs = []
    for cy in range(N):
        cx = 0
        while cx < N:
            if not sh.mask[cy, cx]:
                cx += 1
                continue
            h = int(sh.layer[cy, cx])
            cx2 = cx
            while cx2 + 1 < N and sh.mask[cy, cx2 + 1] and sh.layer[cy, cx2 + 1] == h:
                cx2 += 1
            runs.append([cx, cx2, cy, cy, h])
            cx = cx2 + 1
    merged = []
    for r in runs:
        for o in merged:
            if o[0] == r[0] and o[1] == r[1] and o[4] == r[4] and o[3] + 1 == r[2]:
                o[3] = r[3]
                break
        else:
            merged.append(r)
    glow_alpha = sh.glow_mask
    for cx0, cx1, cy0, cy1, h in merged:
        x1, x2 = mx(cx0), mx(cx1 + 1)
        y2, y1 = my(cy0), my(cy1 + 1)
        zt = zb + sh.layer_z(h)
        u1, u2 = cx0 / N * 16, (cx1 + 1) / N * 16
        v1, v2 = cy0 / N * 16, (cy1 + 1) / N * 16
        # Raised parts (rims, ridges, bosses) have sides in their own colour: a one-pixel sample of their front.
        if h <= 1:
            side = ("misc", (0, 0, 16, 8))
        else:
            sx, sy = sh.side_slot(cx0, cx1, cy0, cy1)
            side = ("side", (sx + 0.3, sy + 0.3, sx + 0.7, sy + 0.7))
        faces = {
            FRONT: ("front", (u1, v1, u2, v2)),
            "north": ("back", (16 - u2, v1, 16 - u1, v2)),
            "east": side, "west": side, "up": side, "down": side,
        }
        m.box((x1, y1, zb), (x2, y2, zt), faces)
        if front_glows_nonempty and glow_alpha[cy0:cy1 + 1, cx0:cx1 + 1].any():
            m.box((x1, y1, zt + 0.03), (x2, y2, zt + 0.03), {FRONT: ("glow", (u1, v1, u2, v2), 15)}, shade=False)
    # The back: a grip and two straps.
    cxm, cym = mx(N / 2), my(N / 2)
    w = N * cs
    leather = ("misc", (0, 8, 16, 16))
    def strap(frm, to):
        m.box(frm, to, {f: leather for f in ("north", "south", "east", "west", "up", "down")})
    strap((cxm - 0.45, cym - 0.45 - 2.2 * (cs / 0.6), zb - 1.7), (cxm + 0.45, cym + 0.45 + 2.2 * (cs / 0.6), zb - 1.2))  # grip bar
    strap((cxm - 0.45, cym - 0.45 - 2.2 * (cs / 0.6), zb - 1.7), (cxm + 0.45, cym - 0.45 - 2.2 * (cs / 0.6) + 0.1, zb))
    strap((cxm - 0.45, cym + 0.35 + 2.2 * (cs / 0.6), zb - 1.7), (cxm + 0.45, cym + 0.45 + 2.2 * (cs / 0.6), zb))
    arm = 0.45 + 0.35 * (cs / 0.6)
    strap((cxm - 2.2, cym - 3.4 * (cs / 0.6), zb - 0.9), (cxm - 1.3, cym + 3.4 * (cs / 0.6), zb))
    m.display = shield_display(sh, blocking)
    m.gui_light = "front"
    return m


# Display transforms. Tuned against screenshots (see docs/design/shields_and_bows.md).
def shield_display(sh, blocking):
    """Hand and GUI transforms for a shield. The engine mirrors x, y and z-rotation for the left hand, so the same numbers serve
    both hands. First person: the painted front faces the camera (+z), like the banner side of a vanilla shield."""
    extent = max(N * sh.cs * 0.9, 9.0)
    gui = min(1.3, 14.5 / extent)
    k = 0.9 * min(1.0, (9.6 / extent) ** 0.9)
    carry = {"rotation": [0, 15, -4], "translation": [-2.0, 6.0, -2.0], "scale": [k, k, k]}
    raised = {"rotation": [0, -75, -68], "translation": [13.0, 17.0, 8.0], "scale": [k, k, k]}
    fp = raised if blocking else carry
    tp = {"rotation": [0, 90, 0], "translation": [0, 0, 0], "scale": [0.75, 0.75, 0.75]}
    return {
        "gui": {"rotation": [14, -32, 0], "translation": [0, 0, 0], "scale": [gui, gui, gui]},
        "ground": {"rotation": [0, 0, 0], "translation": [0, 3, 0], "scale": [0.45, 0.45, 0.45]},
        "fixed": {"rotation": [0, 180, 0], "translation": [0, 0, 0], "scale": [gui * 0.8] * 3},
        "firstperson_lefthand": fp, "firstperson_righthand": fp,
        "thirdperson_lefthand": tp, "thirdperson_righthand": tp,
    }


DISPLAY_BOW = {
    "gui": {"rotation": [10, 50, 0], "translation": [0, 0, 0], "scale": [0.62, 0.62, 0.62]},
    "ground": {"rotation": [0, 0, 0], "translation": [0, 3, 0], "scale": [0.4, 0.4, 0.4]},
    "fixed": {"rotation": [0, 90, 0], "translation": [0, 0, 0], "scale": [0.6, 0.6, 0.6]},
    # The model stands along y with the arrow pointing to -z, so first person needs almost no turn: the arrow points in
    # towards the crosshair and the bow cants a little, small and low on the right (tuned against SkyCam screenshots).
    "firstperson_righthand": {"rotation": [0, 0, 15], "translation": [3.5, 1.0, -1.0], "scale": [0.42, 0.42, 0.42]},
    "firstperson_lefthand": {"rotation": [0, 0, 15], "translation": [3.5, 1.0, -1.0], "scale": [0.42, 0.42, 0.42]},
    # Third person: vanilla's bow transform applied to the same model-to-sprite relation that the first-person pose implies.
    "thirdperson_righthand": {"rotation": [-14.7, -4.2, 24.1], "translation": [-1, -2, 2.5], "scale": [0.6, 0.6, 0.6]},
    "thirdperson_lefthand": {"rotation": [-14.7, -4.2, 24.1], "translation": [-1, -2, 2.5], "scale": [0.6, 0.6, 0.6]},
}


# ================================================================================================= the seven shields
class OakBuckler(Shield):
    id, cs, yoff = "oak_buckler", 0.52, 0.0
    back, edge = DARK_WOOD, DARK_WOOD

    def build(self):
        self.mask = disc(12, 12, 9.0)
        self.layer[self.mask] = 1
        for cx, cy, fx, fy in cells():
            if self.mask[cy, cx]:
                d = math.hypot(fx - 12, fy - 12)
                self.layer[cy, cx] = 4 if d <= 1.6 else 3 if d <= 3.3 else 2 if d >= 7.4 else 1

    def paint(self, fx, fy, h, ed):
        d = math.hypot(fx - 12, fy - 12)
        if d <= 3.3:   # the iron boss
            ang = math.atan2(fy - 12, fx - 12)
            return IRON, 2.6 + (1.6 if d <= 1.6 else 0) + 0.9 * math.cos(ang - 2.3) * (d / 3.3) - (0.8 if d > 2.9 else 0)
        for k in range(8):   # rivets on the rim ring
            a = k * math.pi / 4 + math.pi / 8
            if math.hypot(fx - (12 + 7.9 * math.cos(a)), fy - (12 + 7.9 * math.sin(a))) < 0.62:
                return IRON, 4.0 - 0.9 * ((fy - 12 - 7.9 * math.sin(a)) > 0)
        if h == 2:     # the wooden rim
            return DARK_WOOD, 2.4 + 1.2 * fbm(fx * 0.4, fy * 0.4, 3)
        board = int((fx + 9.0) / 3.0)
        frac = ((fx + 9.0) / 3.0) - board
        grain = fbm(fx * 0.5, fy * 0.07 + board * 3.1, 7)
        idx = 2.2 + 1.5 * grain + 0.35 * ((board * 7) % 3 - 1)
        if frac < 0.1 or frac > 0.93:
            idx -= 1.6
        if abs(fy - 12) < 0.9 and d > 3.3 and d < 7.6:   # an iron strap across
            return IRON, 2.2 + 0.6 * fbm(fx, fy, 5)
        return OAK, idx


class IronKite(Shield):
    id, cs, yoff = "iron_kite_shield", 0.58, -0.4
    back, edge = DARK_WOOD, IRON

    def build(self):
        self.mask = poly([(4, 1), (20, 1), (20, 8), (12, 23.2), (4, 8)])
        self.layer[self.mask] = 1

    def finish(self):
        for cx, cy, fx, fy in cells():
            if not self.mask[cy, cx]:
                continue
            h = 1
            if self.ed[cy, cx] <= 1.6:
                h = 2
            if abs(fx - 12) <= 1.2 or abs(fy - 7.5) <= 1.0:
                h = max(h, 2)
            if math.hypot(fx - 12, fy - 7.5) <= 2.4:
                h = 3
            self.layer[cy, cx] = h

    def paint(self, fx, fy, h, ed):
        d = math.hypot(fx - 12, fy - 7.5)
        if d <= 2.4:
            return IRON, 3.3 + (1.3 if d < 1.2 else 0) - 0.5 * ((fx - 12) + (fy - 7.5)) / 2.4
        for px_, py_ in ((8, 4.5), (16, 4.5), (8, 11), (16, 11), (12, 15), (12, 3)):   # rivets
            if math.hypot(fx - px_, fy - py_) < 0.55:
                return STEEL, 4.2
        for t in range(14):   # rivets along the rim
            pass
        if h == 2:
            return IRON, 1.9 + 0.8 * fbm(fx * 0.9, fy * 0.9, 2)
        quad = (fx < 12) ^ (fy < 7.5)
        return STEEL, 2.9 + (0.5 if quad else -0.1) + 1.0 * fbm(fx * 0.5, fy * 0.5, 11) - 0.5 + 0.5 * math.sin(fx * 1.5 + fy) * 0.0


class SkyironBulwark(Shield):
    id, cs, yoff = "skyiron_bulwark", 0.66, -0.5
    back, edge = DARK_WOOD, SKYIRON

    def build(self):
        self.mask = poly([(4, 0), (20, 0), (22, 2), (22, 16), (18, 24), (6, 24), (2, 16), (2, 2)])
        self.layer[self.mask] = 1

    def finish(self):
        for cx, cy, fx, fy in cells():
            if not self.mask[cy, cx]:
                continue
            h = 1
            if self.ed[cy, cx] <= 1.8:
                h = 2
            if (abs(fx - 12) <= 1.6 and 2 <= fy <= 21) or (abs(fy - 9.2) <= 1.3 and 4.5 <= fx <= 19.5):
                h = 3
            if math.hypot(fx - 12, fy - 9.2) <= 3.2:
                h = 4
            if math.hypot(fx - 12, fy - 9.2) <= 1.4:
                h = 5
            self.layer[cy, cx] = h

    def paint(self, fx, fy, h, ed):
        d = math.hypot(fx - 12, fy - 9.2)
        if d <= 3.2:
            return SKYIRON, 3.4 + (1.2 if d < 1.4 else 0) + (0.7 if d > 2.7 else 0) * 0 - 0.4 * ((fx - 12) + (fy - 9.2)) / 3.2
        if h == 2 and 1.0 <= ed <= 1.8:
            return SKYIRON, 5.0       # the pale trim line on the rim
        for px_, py_ in ((3.5, 3.5), (20.5, 3.5), (3.5, 14), (20.5, 14), (12, 22), (7.2, 20.0), (16.8, 20.0)):
            if math.hypot(fx - px_, fy - py_) < 0.6:
                return STEEL, 4.4
        if h == 3:
            return SKYIRON, 3.3 + 0.6 * fbm(fx * 0.8, fy * 0.8, 3)
        if h == 2:
            return SKYIRON, 2.6 + 0.5 * fbm(fx, fy, 6)
        # plate: dark blue steel with riveted panel lines
        seam = abs(((fx + 1) % 6) - 3) > 2.8 or abs(((fy + 2) % 7) - 3.5) > 3.3
        return STORMIRON, 2.0 + 1.0 * fbm(fx * 0.35, fy * 0.5, 8) - (0.9 if seam else 0)


class EmberAegis(Shield):
    id, cs, yoff = "emberforged_aegis", 0.7, -0.4
    back, edge = BLACKSTEEL, BLACKSTEEL
    frames = 4

    def build(self):
        self.mask = poly([(2, 5), (5, 0), (8, 4), (12, 3), (16, 4), (19, 0), (22, 5), (22, 15), (12, 24), (2, 15)])
        self.layer[self.mask] = 1
        rnd = random.Random(31)
        self.cracks = []
        for k in range(9):
            a = k * 2 * math.pi / 9 + rnd.uniform(-0.2, 0.2)
            pts = [(12, 10.5)]
            r = 2.5
            while r < 13:
                a += rnd.uniform(-0.45, 0.45)
                r += rnd.uniform(1.6, 2.6)
                pts.append((12 + r * math.cos(a), 10.5 + r * math.sin(a) * 0.95))
            self.cracks.append(pts)

    def finish(self):
        for cx, cy, fx, fy in cells():
            if not self.mask[cy, cx]:
                continue
            h = 1
            if self.ed[cy, cx] <= 1.7:
                h = 2
            if abs(fx - 12) <= 1.3 and 2 <= fy <= 22:
                h = 3
            if math.hypot(fx - 12, fy - 10.5) <= 3.8:
                h = 3
            if math.hypot(fx - 12, fy - 10.5) <= 2.4:
                h = 2
            self.layer[cy, cx] = h

    def crack_d(self, fx, fy):
        return min(path_dist(fx, fy, p) for p in self.cracks)

    def paint(self, fx, fy, h, ed):
        d = math.hypot(fx - 12, fy - 10.5)
        cd = self.crack_d(fx, fy)
        if d <= 2.4:
            return CINDER, 0.6
        if cd < 0.42 and d > 2.0 and ed > 1.4:
            return CINDER, 0.2
        base = 1.6 + 1.3 * fbm(fx * 0.5, fy * 0.5, 5)
        if h == 2 and d > 2.4:   # rim
            base += 1.0
        if h == 3:
            base += 1.2
        # rivets
        for px_, py_ in ((5.0, 6.0), (19.0, 6.0), (4.0, 12.5), (20.0, 12.5), (8, 18.5), (16, 18.5)):
            if math.hypot(fx - px_, fy - py_) < 0.55:
                return CINDER, 4.6
        return CINDER, base

    def glow(self, fx, fy, h, ed, frame):
        pulse = 0.85 + 0.15 * math.sin(frame * math.pi / 2 + fx * 0.3)
        d = math.hypot(fx - 12, fy - 10.5)
        if d <= 2.4:
            t = 1 - d / 2.4
            c = EMBER[3 + 2 * t]
            return (*c, 255)
        cd = self.crack_d(fx, fy)
        if cd < 0.42 and ed > 1.4:
            t = 1 - cd / 0.42
            c = EMBER[2.2 + 2.4 * t * pulse]
            return (*c, 255)
        if cd < 0.8 and ed > 1.4:
            return (*EMBER[1.5], 150)
        # the horns glow at their tips
        if h >= 1 and fy < 3.0 and (abs(fx - 5) < 1.2 or abs(fx - 19) < 1.2) and ed < 2.2:
            return (*EMBER[3.5], 255)
        return None


class FrostBarrier(Shield):
    id, cs, yoff = "frostward_barrier", 0.74, -0.2
    back, edge = FROST_WOOD, ICE
    frames = 4

    def build(self):
        self.mask = poly([(12, 0), (21, 5.5), (21, 15), (12, 24), (3, 15), (3, 5.5)])
        # icicle shards on the rim: little triangles on the upper shoulders and the lower sides
        for tip in ((5.5, 3.2), (18.5, 3.2)):
            self.mask |= poly([(tip[0] - 1.2, tip[1] + 2), (tip[0], tip[1] - 2.5), (tip[0] + 1.2, tip[1] + 2)])
        self.layer[self.mask] = 1

    def finish(self):
        for cx, cy, fx, fy in cells():
            if not self.mask[cy, cx]:
                continue
            l1 = abs(fx - 12) / 9.5 + abs(fy - 11.5) / 12.5
            self.layer[cy, cx] = 1 if l1 > 0.86 else 2 if l1 > 0.62 else 3 if l1 > 0.36 else 4 if l1 > 0.14 else 5

    def paint(self, fx, fy, h, ed):
        ang = math.atan2(fy - 11.5, (fx - 12) * 1.25)
        sector = int((ang + math.pi) / (math.pi / 4))
        shade = [0.5, -0.4, 0.2, -0.7, 0.8, -0.2, 0.4, -0.5][sector % 8]
        idx = -0.4 + 0.95 * h + 0.7 * shade + 0.55 * fbm(fx * 0.6, fy * 0.6, 12)
        if abs(((ang + math.pi) % (math.pi / 4)) - math.pi / 8) < 0.04 and h >= 2:     # facet lines
            idx += 1.1
        return ICE, idx

    def glow(self, fx, fy, h, ed, frame):
        # sparkles that wander from frame to frame
        for k in range(7):
            sx = 5 + (k * 7.3 + frame * 1.7) % 14
            sy = 3 + (k * 11.9 + frame * 2.9) % 18
            if abs(fx - sx) < 0.55 and abs(fy - sy) < 0.55 and h >= 2:
                return (230, 248, 255, 255)
        return None


class Thunderguard(Shield):
    id, cs, yoff = "thunderguard", 0.78, -0.3
    back, edge = STORMIRON, COPPER
    frames = 4

    def build(self):
        self.mask = poly([(1, 1), (7, 3), (12, 0), (17, 3), (23, 1), (22, 12), (18, 19), (12, 24), (6, 19), (2, 12)])
        self.layer[self.mask] = 1
        self.bolts = [[(3, 2.5), (6, 6), (5, 8), (8.5, 9.5)], [(21, 2.5), (18, 6), (19, 8), (15.5, 9.5)],
                      [(12, 20.5), (10, 17), (12, 15.5), (11, 13.5)]]

    def finish(self):
        for cx, cy, fx, fy in cells():
            if not self.mask[cy, cx]:
                continue
            h = 1
            d = math.hypot(fx - 12, fy - 10.5)
            if self.ed[cy, cx] <= 1.7:
                h = 2
            if d <= 5.0:
                h = 2
            if min(path_dist(fx, fy, b) for b in self.bolts) <= 0.85:
                h = 3
            if d <= 3.6:
                h = 3
            if d <= 2.0:
                h = 4
            self.layer[cy, cx] = h

    def bolt_d(self, fx, fy):
        return min(path_dist(fx, fy, b) for b in self.bolts)

    def paint(self, fx, fy, h, ed):
        d = math.hypot(fx - 12, fy - 10.5)
        if d <= 2.0:
            return CYAN_GLOW, 3.5
        if 3.6 < d <= 5.0 or d <= 3.6:
            ring = COPPER if d > 2.6 else CYAN_GLOW
            return ring, (3.0 + 0.8 * fbm(fx, fy, 3) - (0.9 if d > 4.6 else 0)) if ring is COPPER else 1.5
        if h == 3:
            return COPPER, 3.0 + 0.7 * fbm(fx * 0.9, fy * 0.9, 5)
        if h == 2:
            patina = fbm(fx * 0.5, fy * 0.5, 14) > 0.58
            return (PATINA, 2.6 + 0.8 * fbm(fx, fy, 2)) if patina else (COPPER, 2.4 + 0.8 * fbm(fx, fy, 2))
        for px_, py_ in ((6, 9), (18, 9), (12, 17), (4, 6), (20, 6)):
            if math.hypot(fx - px_, fy - py_) < 0.55:
                return COPPER, 4.4
        return STORMIRON, 2.0 + 1.5 * fbm(fx * 0.4, fy * 0.5, 17)

    def glow(self, fx, fy, h, ed, frame):
        d = math.hypot(fx - 12, fy - 10.5)
        flick = 0.8 + 0.2 * math.sin(frame * 1.6 + fx * 0.4 + fy * 0.3)
        if d <= 2.0:
            c = CYAN_GLOW[3.0 + 2.0 * (1 - d / 2.0) * flick]
            return (*c, 255)
        bd = self.bolt_d(fx, fy)
        if bd < 0.4 and ed > 1.2:
            return (*CYAN_GLOW[3.6 + 1.4 * flick], 255)
        if bd < 0.75 and ed > 1.2:
            return (*CYAN_GLOW[2.2], 170)
        if 2.0 < d <= 2.5:
            return (*CYAN_GLOW[2.4], 200)
        return None


class AstralAegis(Shield):
    id, cs, yoff = "astral_aegis", 0.86, -0.6
    back, edge = ASTRAL, STARSTEEL
    frames = 8

    def build(self):
        self.mask = poly([(12, 0), (16, 3), (21, 0), (23, 6), (22, 15), (12, 24), (2, 15), (1, 6), (3, 0), (8, 3)])
        self.layer[self.mask] = 1
        rnd = random.Random(77)
        self.stars = [(rnd.uniform(3, 21), rnd.uniform(4, 20), rnd.random(), rnd.choice((0.5, 0.6, 0.8, 1.1))) for _ in range(34)]
        self.links = [(12, 10), (7, 6), (5, 12), (9, 17), (12, 10), (17, 7), (19, 13), (15, 18), (12, 10)]
        pts = []
        for k in range(16):
            r = 7.4 if k % 2 == 0 else 3.4
            a = k * math.pi / 8 - math.pi / 2
            pts.append((12 + r * math.cos(a), 10.5 + r * math.sin(a)))
        self.star_pts = pts

    def finish(self):
        for cx, cy, fx, fy in cells():
            if not self.mask[cy, cx]:
                continue
            h = 1
            if self.ed[cy, cx] <= 2.0:
                h = 2
            if in_poly(fx, fy, self.star_pts):
                h = 3
            if math.hypot(fx - 12, fy - 10.5) <= 2.3:
                h = 4
            if fy < 3.2 and self.ed[cy, cx] <= 2.4:
                h = 3
            self.layer[cy, cx] = h

    def paint(self, fx, fy, h, ed):
        d = math.hypot(fx - 12, fy - 10.5)
        if d <= 2.3:
            return GOLD, 3.2 + 1.5 * (1 - d / 2.3) - 0.4 * ((fx - 12) + (fy - 10.5)) / 2.3
        if h == 3 and in_poly(fx, fy, self.star_pts):
            return GOLD, 2.6 + 0.9 * fbm(fx, fy, 4) - 0.5 * ((fx - 12) + (fy - 10.5)) / 7
        if h == 3 or h == 2:
            if 1.2 <= ed <= 1.7 and h == 2:
                return GOLD, 3.0      # a gold inlay line along the rim
            return STARSTEEL, 2.4 + 1.0 * fbm(fx * 0.8, fy * 0.8, 6) + (0.7 if h == 3 else 0)
        neb = fbm(fx * 0.18, fy * 0.18, 21, 4)
        return ASTRAL, 0.6 + 2.2 * neb + 0.5 * math.sin(fy * 0.35 + fx * 0.2)

    def glow(self, fx, fy, h, ed, frame):
        if h == 1 or (h == 2 and ed > 2.0):
            for sx, sy, ph, r in self.stars:
                tw = 0.5 + 0.5 * math.sin(frame * math.pi / 4 * 1.0 + ph * 6.28)
                rad = r * (0.5 + 0.5 * tw)
                dd = math.hypot(fx - sx, fy - sy)
                if dd < rad * 0.7:
                    return (255, 255, 255, 255)
                if (abs(fx - sx) < 0.28 and abs(fy - sy) < rad * 1.6) or (abs(fy - sy) < 0.28 and abs(fx - sx) < rad * 1.6):
                    if tw > 0.4:
                        return (200, 220, 255, 235)
            ld = path_dist(fx, fy, self.links)
            if ld < 0.22 and h == 1:
                return (150, 170, 255, int(120 + 90 * (0.5 + 0.5 * math.sin(frame * math.pi / 4))))
            # a slow nebula shimmer that travels down the face
            band = 0.5 + 0.5 * math.sin((fy - frame * 3.0) * 0.5 + fx * 0.15)
            n = fbm(fx * 0.3 + 40, fy * 0.3, 33)
            if n > 0.56 and h == 1:
                return (110, 80, 255, int(60 + 80 * band))
        d = math.hypot(fx - 12, fy - 10.5)
        if d <= 2.3:
            t = 0.7 + 0.3 * math.sin(frame * math.pi / 4)
            return (*GOLD[3.5 + 1.5 * (1 - d / 2.3) * t], 255)
        return None



# ================================================================================================= the seven bows
class Atlas:
    """A 64x64 texture of 16x16 material swatches. A face samples a patch of its swatch at 4 pixels per model unit."""

    def __init__(self, seed):
        self.img = Image.new("RGBA", (64, 64), (0, 0, 0, 255))
        self.slots = {}
        self.rnd = random.Random(seed)
        self.seed = seed

    def add(self, name, kind, ramp, base=2.6, spread=1.4):
        i = len(self.slots)
        assert i < 16
        self.slots[name] = i
        ox, oy = (i % 4) * 16, (i // 4) * 16
        px = self.img.load()
        for y in range(16):
            for x in range(16):
                n = fbm(x * 0.35 + i * 7, y * 0.12 + 3, self.seed + i, 3)
                if kind == "wood":
                    idx = base - spread / 2 + spread * n + (0.5 if (x % 5 == 1) else 0) - (0.5 if (x % 7 == 4) else 0)
                elif kind == "wrap":
                    idx = base + (0.9 if ((x + y) % 4 < 2) else -0.5) + 0.4 * (n - 0.5)
                elif kind == "metal":
                    idx = base + spread * (n - 0.5) + (0.9 if y < 3 else 0) - (0.9 if y > 12 else 0)
                elif kind == "glow":
                    idx = base + 1.0 * (n - 0.5) * 2
                elif kind == "ice":
                    idx = base + spread * (fbm(x * 0.5, y * 0.5, self.seed + 9) - 0.5) * 2 + (0.8 if (x + y) % 7 == 0 else 0)
                else:
                    idx = base + 0.5 * (n - 0.5)
                px[ox + x, oy + y] = ramp[idx] + (255,)

    def uv(self, name, fw, fh):
        i = self.slots[name]
        ox, oy = (i % 4) * 16, (i // 4) * 16
        w = max(1.0, min(16.0, fw * 4))
        h = max(1.0, min(16.0, fh * 4))
        x = ox + self.rnd.uniform(0, 16 - w)
        y = oy + self.rnd.uniform(0, 16 - h)
        return (x / 4, y / 4, (x + w) / 4, (y + h) / 4)


GLOW_MATS = {"cord", "glowgem", "ember", "bolt", "starlight", "glowstr"}


class BowBuilder:
    def __init__(self, spec, draw):
        self.spec, self.draw = spec, draw
        self.m = ItemModel()
        self.atlas = spec.atlas
        self.m.textures = {"tex": f"{NS}:item/{spec.id}_bow", "particle": f"{NS}:item/{spec.id}_bow", "layer0": f"{NS}:item/{spec.id}_icon"}
        self.m.gui_light = "side"

    def cube(self, mat, frm, to, rotation=None):
        fx, fy, fz = (abs(to[i] - frm[i]) for i in range(3))
        emit = 15 if mat in GLOW_MATS else 0
        dims = {"north": (fx, fy), "south": (fx, fy), "east": (fz, fy), "west": (fz, fy), "up": (fx, fz), "down": (fx, fz)}
        faces = {}
        for name, (w, h) in dims.items():
            if w <= 0 or h <= 0:
                continue
            faces[name] = ("tex", self.atlas.uv(mat, w, h), emit)
        lo = [min(frm[i], to[i]) for i in range(3)]
        hi = [max(frm[i], to[i]) for i in range(3)]
        self.m.box(lo, hi, faces, rotation=rotation, shade=not emit)


def limb_z(spec, t, draw):
    """z of the limb's centre line at t in -1..1 along it (grip at 0). The grip is the point farthest forward (-z)."""
    a = abs(t)
    z = 8.0 - spec.curve / 2 + spec.curve * a ** 1.7
    z -= spec.recurve * max(0.0, a - 0.72) * 3.2
    z += spec.flex * draw * a ** 2
    return z


def build_bow(spec, draw=None):
    """draw is None for the idle model, else how far (model units) the string is pulled back."""
    b = BowBuilder(spec, draw)
    d = 0.0 if draw is None else draw
    half = spec.length / 2
    steps = int(spec.length)
    ycenter = 8.0
    for i in range(steps):
        y0 = ycenter - half + i
        t = (y0 + 0.5 - ycenter) / half
        a = abs(t)
        w = spec.w_grip + (spec.w_tip - spec.w_grip) * a
        z = limb_z(spec, t, d)
        dep = spec.depth * (1.0 - 0.25 * a)
        mat = spec.limb_mat(i, t)
        b.cube(mat, (8 - w / 2, y0, z - dep / 2), (8 + w / 2, y0 + 1.0, z + dep / 2))
    gz = limb_z(spec, 0, d)
    gh = spec.grip_len
    b.cube("wrap", (8 - spec.w_grip / 2 - 0.15, ycenter - gh / 2, gz - spec.depth / 2 - 0.15),
           (8 + spec.w_grip / 2 + 0.15, ycenter + gh / 2, gz + spec.depth / 2 + 0.15))
    # the string runs from tip to nock to tip; the tips' z follows the limbs
    top_y, bot_y = ycenter + half - 0.4, ycenter - half + 0.4
    tz = limb_z(spec, 1.0, d) + 0.1
    nock = tz + d
    sw = spec.string_w
    smat = spec.string_mat
    if draw is None:
        b.cube(smat, (8 - sw / 2, bot_y, tz - sw / 2), (8 + sw / 2, top_y, tz + sw / 2))
    else:
        pieces = 8
        for k in range(pieces):
            for sign in (1, -1):
                f0, f1 = k / pieces, (k + 1) / pieces
                y_a = ycenter + sign * (half - 0.4) * (1 - f0)
                y_b = ycenter + sign * (half - 0.4) * (1 - f1)
                z_a = tz + d * f0
                z_b = tz + d * f1
                b.cube(smat, (8 - sw / 2, min(y_a, y_b), min(z_a, z_b) - sw / 2), (8 + sw / 2, max(y_a, y_b), max(z_a, z_b) + sw / 2))
        shaft_back = nock + 0.4
        shaft_front = 8.0 - spec.curve / 2 - 4.5
        b.cube("shaft", (8 - 0.25, ycenter - 0.05, shaft_front), (8 + 0.25, ycenter + 0.45, shaft_back))
        b.cube("head", (8 - 0.6, ycenter - 0.4, shaft_front - 1.4), (8 + 0.6, ycenter + 0.8, shaft_front))
        b.cube("fletch", (8 - 0.1, ycenter - 0.7, shaft_back - 1.8), (8 + 0.1, ycenter + 1.1, shaft_back))
        b.cube("fletch", (8 - 0.9, ycenter + 0.15, shaft_back - 1.8), (8 + 0.9, ycenter + 0.35, shaft_back))
    spec.ornaments(b, d)
    b.m.display = DISPLAY_BOW
    return b


class BowSpec:
    id = ""
    length = 18
    curve = 3.0
    recurve = 0.0
    flex = 0.25
    w_grip, w_tip = 1.5, 0.9
    depth = 1.2
    grip_len = 3.0
    string_w = 0.22
    string_mat = "string"
    seed = 1

    def __init__(self):
        # Chunkier than the first sketches: a bow this size should read as a solid object in the hand.
        self.w_grip, self.w_tip, self.depth = self.w_grip * 1.3, self.w_tip * 1.3, self.depth * 1.3
        self.grip_len = self.grip_len * 1.15
        self.atlas = Atlas(self.seed)
        self.setup(self.atlas)
        self.atlas.add("shaft", "wood", wa.PLANK, 3.0, 1.0)
        self.atlas.add("head", "metal", wa.IRON, 3.0, 1.0)
        self.atlas.add("fletch", "plain", wa.FEATHER, 3.0)
        if "string" not in self.atlas.slots:
            self.atlas.add("string", "plain", STRING, 3.6)

    def setup(self, atlas):
        raise NotImplementedError

    def limb_mat(self, i, t):
        return "limb"

    def ornaments(self, b, d):
        pass


def band(b, spec, mat, t, h=0.6, grow=0.25, d=0.0):
    """A ring around the limb at t (-1..1), h units tall."""
    y = 8.0 + t * spec.length / 2
    a = abs(t)
    w = spec.w_grip + (spec.w_tip - spec.w_grip) * a + grow
    z = limb_z(spec, t, d)
    dep = spec.depth * (1.0 - 0.25 * a) + grow
    b.cube(mat, (8 - w / 2, y - h / 2, z - dep / 2), (8 + w / 2, y + h / 2, z + dep / 2))


def tip_cap(b, spec, mat, sign, d=0.0, length=1.4):
    y = 8.0 + sign * spec.length / 2
    z = limb_z(spec, sign * 1.0, d)
    w = spec.w_tip + 0.3
    dep = spec.depth * 0.75 + 0.3
    y0, y1 = (y - length + 0.4, y + 0.4) if sign > 0 else (y - 0.4, y + length - 0.4)
    b.cube(mat, (8 - w / 2, y0, z - dep / 2), (8 + w / 2, y1, z + dep / 2))


class HuntersBow(BowSpec):
    id, length, curve, seed = "hunters_bow", 18, 3.0, 11

    def setup(self, a):
        a.add("limb", "wood", IRONBARK, 2.6, 1.6)
        a.add("dark", "wood", IRONBARK, 1.4, 1.0)
        a.add("wrap", "wrap", LEATHER, 2.5)

    def ornaments(self, b, d):
        for sign in (1, -1):
            tip_cap(b, self, "dark", sign, d, 1.2)
        for t in (-0.4, 0.4):
            band(b, self, "dark", t, 0.5, 0.2, d)


class RecurveBow(BowSpec):
    id, length, curve, recurve, seed = "recurve_bow", 19, 3.4, 2.2, 12
    w_grip, w_tip, depth = 1.7, 1.0, 1.3

    def setup(self, a):
        a.add("limb", "wood", MISTPINE, 3.0, 1.6)
        a.add("dark", "wood", IRONBARK, 2.0, 1.0)
        a.add("wrap", "wrap", RED_LEATHER, 2.6)
        a.add("brass", "metal", BRONZE, 3.0, 1.2)

    def ornaments(self, b, d):
        for sign in (1, -1):
            tip_cap(b, self, "brass", sign, d, 1.5)
            band(b, self, "brass", sign * 0.55, 0.45, 0.2, d)
        band(b, self, "brass", 0.17, 0.4, 0.3, d)
        band(b, self, "brass", -0.17, 0.4, 0.3, d)


class SkyironLongbow(BowSpec):
    id, length, curve, seed = "skyiron_longbow", 24, 2.6, 13
    w_grip, w_tip, depth = 1.5, 0.9, 1.3
    grip_len = 3.4

    def setup(self, a):
        a.add("limb", "wood", IRONBARK, 2.8, 1.4)
        a.add("plate", "metal", SKYIRON, 3.0, 1.4)
        a.add("wrap", "wrap", LEATHER, 2.4)
        a.add("trim", "metal", SKYWIRE, 3.6, 1.0)
        a.add("string", "plain", SKYWIRE, 4.0)

    def ornaments(self, b, d):
        for sign in (1, -1):
            tip_cap(b, self, "trim", sign, d, 1.7)
            for t in (0.25, 0.5, 0.75):
                band(b, self, "plate", sign * t, 1.5, 0.35, d)
            band(b, self, "trim", sign * 0.9, 0.35, 0.4, d)
        band(b, self, "trim", 0.12, 0.35, 0.45, d)
        band(b, self, "trim", -0.12, 0.35, 0.45, d)


class EmberBow(BowSpec):
    id, length, curve, recurve, seed = "ember_bow", 20, 3.6, 2.6, 14
    w_grip, w_tip, depth = 1.8, 1.0, 1.4

    def setup(self, a):
        a.add("limb", "wood", EMBERWOOD, 2.0, 1.6)
        a.add("dark", "wood", CINDER, 2.2, 1.0)
        a.add("wrap", "wrap", RED_LEATHER, 2.0)
        a.add("cord", "glow", CORD_EMBER, 3.6)
        a.add("ember", "glow", EMBER, 4.0)
        a.add("string", "plain", wa.FEATHER, 3.4)

    def ornaments(self, b, d):
        for sign in (1, -1):
            for t in (0.2, 0.38, 0.56, 0.74):
                band(b, self, "cord", sign * t, 0.5, 0.22, d)
            tip_cap(b, self, "dark", sign, d, 1.4)
            # a flame licking forward off each tip
            y = 8.0 + sign * (self.length / 2 + 0.2)
            z = limb_z(self, sign, d)
            for k, (h, w) in enumerate(((1.4, 0.9), (1.1, 0.6), (0.8, 0.35))):
                yy = y + sign * 0.2 * k
                b.cube("ember", (8 - w / 2, yy - (h if sign < 0 else 0), z - 0.45 - 0.55 * (k + 1)),
                       (8 + w / 2, yy + (h if sign > 0 else 0), z - 0.45 - 0.55 * k))
        b.cube("ember", (7.3, 8 - 0.5, limb_z(self, 0, d) - self.depth / 2 - 0.5), (8.7, 8 + 0.5, limb_z(self, 0, d) - self.depth / 2))


class FrostbiteBow(BowSpec):
    id, length, curve, recurve, seed = "frostbite_bow", 21, 3.3, 1.2, 15
    w_grip, w_tip, depth = 1.7, 1.0, 1.3

    def setup(self, a):
        a.add("limb", "wood", FROST_WOOD, 2.8, 1.4)
        a.add("ice", "ice", ICE, 3.0, 0.8)
        a.add("icewhite", "ice", ICE, 4.2, 0.4)
        a.add("wrap", "wrap", STRING, 3.0)
        a.add("string", "plain", ICE, 4.4)

    def ornaments(self, b, d):
        rnd = random.Random(5)
        for sign in (1, -1):
            for t in (0.2, 0.42, 0.62, 0.82):
                tt = sign * t
                y = 8.0 + tt * self.length / 2
                z = limb_z(self, tt, d)
                side = 1 if rnd.random() < 0.5 else -1
                h = rnd.uniform(1.4, 2.4)
                # an ice crystal growing out of the limb: a column with a smaller tip
                b.cube("ice", (8 + side * 0.4 - 0.5, y - 0.5, z - 0.9), (8 + side * 0.4 + 0.5, y + h * 0.7, z + 0.9))
                b.cube("icewhite", (8 + side * 0.4 - 0.3, y + h * 0.7, z - 0.5), (8 + side * 0.4 + 0.3, y + h, z + 0.5))
            # icicles hanging off the tips
            y = 8.0 + sign * self.length / 2
            z = limb_z(self, sign, d)
            b.cube("ice", (8 - 0.55, y - 1.5 if sign < 0 else y - 0.6, z - 0.55), (8 + 0.55, y + 0.6 if sign < 0 else y + 1.2, z + 0.55))
        gz = limb_z(self, 0, d)
        b.cube("icewhite", (8 - 1.3, 8 - 2.2, gz - 0.9), (8 + 1.3, 8 - 1.2, gz + 0.9))
        for k in range(3):
            b.cube("ice", (8 - 0.9 + k * 0.7, 8 - 3.6 - k * 0.3, gz - 0.3), (8 - 0.5 + k * 0.7, 8 - 2.2, gz + 0.3))


class StormcallerBow(BowSpec):
    id, length, curve, recurve, seed = "stormcaller_bow", 22, 3.4, 1.6, 16
    w_grip, w_tip, depth = 1.7, 1.0, 1.3

    def setup(self, a):
        a.add("limb", "wood", EMBERWOOD, 2.6, 1.3)
        a.add("copper", "metal", COPPER, 3.0, 1.2)
        a.add("patina", "metal", PATINA, 3.0, 1.0)
        a.add("wrap", "wrap", LEATHER, 2.2)
        a.add("bolt", "glow", CYAN_GLOW, 4.0)
        a.add("string", "plain", CYAN_GLOW, 4.2)

    def ornaments(self, b, d):
        for sign in (1, -1):
            # copper wire wound round the limb, with blue beads between the turns
            for k in range(6):
                t = sign * (0.14 + k * 0.13)
                band(b, self, "copper" if k % 2 == 0 else "patina", t, 0.4, 0.22, d)
            for t in (0.3, 0.6):
                band(b, self, "bolt", sign * t, 0.3, 0.32, d)
            # a lightning-rod spike off each tip
            y = 8.0 + sign * self.length / 2
            z = limb_z(self, sign, d)
            b.cube("copper", (8 - 0.3, y if sign > 0 else y - 2.6, z - 0.3), (8 + 0.3, y + 2.6 if sign > 0 else y, z + 0.3))
            b.cube("bolt", (8 - 0.2, y + 2.6 if sign > 0 else y - 3.3, z - 0.2), (8 + 0.2, y + 3.3 if sign > 0 else y - 2.6, z + 0.2))
            tip_cap(b, self, "copper", sign, d, 1.2)
        gz = limb_z(self, 0, d)
        b.cube("bolt", (8 - 0.7, 8 - 0.7, gz - self.depth / 2 - 0.7), (8 + 0.7, 8 + 0.7, gz - self.depth / 2))
        band(b, self, "copper", 0.14, 0.4, 0.4, d)
        band(b, self, "copper", -0.14, 0.4, 0.4, d)


class StarfallBow(BowSpec):
    id, length, curve, recurve, seed = "starfall_bow", 24, 3.2, 3.0, 17
    w_grip, w_tip, depth = 1.9, 1.0, 1.4
    grip_len = 3.4

    def setup(self, a):
        a.add("limb", "wood", STARWOOD, 2.4, 1.6)
        a.add("inlay", "metal", STARSTEEL, 3.4, 1.2)
        a.add("gold", "metal", GOLD, 3.2, 1.4)
        a.add("wrap", "wrap", wa.CLOTH_BLUE, 2.6)
        a.add("starlight", "glow", ASTRAL, 5.0)
        a.add("glowstr", "glow", STARSTEEL, 5.0)
        a.add("string", "plain", STARSTEEL, 4.6)

    def ornaments(self, b, d):
        for sign in (1, -1):
            for t in (0.18, 0.4, 0.62, 0.82):
                band(b, self, "inlay", sign * t, 0.35, 0.2, d)
            tip_cap(b, self, "gold", sign, d, 1.6)
            # a four-point star on each tip
            y = 8.0 + sign * (self.length / 2 + 0.7)
            z = limb_z(self, sign, d)
            b.cube("starlight", (8 - 0.25, y - 1.1, z - 0.25), (8 + 0.25, y + 1.1, z + 0.25))
            b.cube("starlight", (8 - 0.8, y - 0.25, z - 0.25), (8 + 0.8, y + 0.25, z + 0.25))
            # wings: stepped feathers sweeping back from the grip
            gz = limb_z(self, 0, d)
            for k in range(4):
                y0 = 8.0 + sign * (1.4 + k * 1.15)
                xo = 1.0 + 0.55 * (4 - k)
                mat = "inlay" if k % 2 == 0 else "gold"
                for side in (-1, 1):
                    x0, x1 = (8 + 0.9, 8 + 0.9 + xo) if side > 0 else (8 - 0.9 - xo, 8 - 0.9)
                    b.cube(mat, (x0, min(y0, y0 + sign * 0.9), gz + 0.1 + 0.25 * k), (x1, max(y0, y0 + sign * 0.9), gz + 0.4 + 0.25 * k))
        gz = limb_z(self, 0, d)
        b.cube("glowstr", (8 - 0.8, 8 - 0.8, gz - self.depth / 2 - 0.8), (8 + 0.8, 8 + 0.8, gz - self.depth / 2))
        band(b, self, "gold", 0.14, 0.4, 0.45, d)
        band(b, self, "gold", -0.14, 0.4, 0.45, d)


BOWS = [HuntersBow, RecurveBow, SkyironLongbow, EmberBow, FrostbiteBow, StormcallerBow, StarfallBow]
DRAWS = [None, 1.2, 3.6, 6.4]


def write_bow(cls, bow_cfg, preview_dir=None):
    spec = cls()
    tex = ASSETS / "textures" / "item"
    tex.mkdir(parents=True, exist_ok=True)
    models = []
    for k, draw in enumerate(DRAWS):
        b = build_bow(spec, draw)
        name = spec.id if draw is None else f"{spec.id}_pulling_{k - 1}"
        b.m.write(ASSETS / "models" / "item" / f"{name}.json")
        models.append(b.m)
    spec.atlas.img.save(tex / f"{spec.id}_bow.png")
    images = {"tex": spec.atlas.img, "particle": spec.atlas.img}
    icon = render(models[0], images, rot=(0, 70, 0), size=128, scale=128 / 27, bg=(0, 0, 0, 0))
    icon.save(tex / f"{spec.id}_icon.png")
    write_item_definition(spec.id, bow=bow_cfg)
    if preview_dir:
        views = [render(models[0], images, rot=(0, 90, 0), size=256, scale=256 / 28),
                 render(models[3], images, rot=(0, 90, 0), size=256, scale=256 / 28),
                 render(models[3], images, rot=(-10, 35, 0), size=256, scale=256 / 28),
                 render(models[0], images, rot=(0, 0, 0), size=256, scale=256 / 28)]
        sheet = Image.new("RGBA", (256 * 4, 256))
        for i, v in enumerate(views):
            sheet.paste(v, (256 * i, 0))
        sheet.save(Path(preview_dir) / f"{spec.id}.png")
    return spec


SHIELDS = [OakBuckler, IronKite, SkyironBulwark, EmberAegis, FrostBarrier, Thunderguard, AstralAegis]


def write_shield(cls, preview_dir=None):
    sh = cls()
    front, glows, back = front_images(sh)
    sh.glow_mask = np.zeros((N, N), dtype=bool)
    sh.front_arr = np.array(front)
    has_glow = any(g.getbbox() for g in glows)
    if has_glow:
        for g in glows:
            arr = np.array(g)[:, :, 3] > 0
            for cy in range(N):
                for cx in range(N):
                    if arr[cy * PPC:(cy + 1) * PPC, cx * PPC:(cx + 1) * PPC].any():
                        sh.glow_mask[cy, cx] = True
    tex = ASSETS / "textures" / "item"
    tex.mkdir(parents=True, exist_ok=True)
    out = {}
    front.save(tex / f"{sh.id}_front.png")
    back.save(tex / f"{sh.id}_back.png")
    misc = misc_image(sh)
    misc.save(tex / f"{sh.id}_misc.png")
    if has_glow:
        if sh.frames > 1:
            strip = Image.new("RGBA", (T, T * sh.frames), (0, 0, 0, 0))
            for i, g in enumerate(glows):
                strip.paste(g, (0, i * T))
            strip.save(tex / f"{sh.id}_glow.png")
            with open(tex / f"{sh.id}_glow.png.mcmeta", "w", newline="\n") as f:
                json.dump({"animation": {"frametime": 5, "interpolate": sh.id == "astral_aegis"}}, f, indent=1)
        else:
            glows[0].save(tex / f"{sh.id}_glow.png")
    models = {}
    for blocking in (False, True):
        m = shield_model(sh, has_glow, blocking)
        models[blocking] = m
        name = sh.id + ("_blocking" if blocking else "")
        m.write(ASSETS / "models" / "item" / f"{name}.json")
    side = Image.new("RGBA", (16, 16), (0, 0, 0, 255))
    for i, rgb in enumerate(sh.side_colors[:256]):
        side.putpixel((i % 16, i // 16), rgb + (255,))
    side.save(tex / f"{sh.id}_side.png")
    images = {"front": front, "back": back, "misc": misc, "particle": misc, "side": side}
    if has_glow:
        images["glow"] = glows[0]
    # icon (for the wiki and anything that wants a flat picture): a three-quarter render of the model
    icon = render(models[False], images, rot=(-12, 28, 0), size=128, scale=128 / (N * sh.cs * 1.15), bg=(0, 0, 0, 0))
    icon.save(tex / f"{sh.id}_icon.png")
    write_item_definition(sh.id, shield=True)
    if preview_dir:
        views = [render(models[False], images, rot=r, size=256, scale=256 / (N * sh.cs * 1.15)) for r in ((0, 0, 0), (-10, 35, 0), (0, 180, 0))]
        sheet = Image.new("RGBA", (256 * 3, 256))
        for i, v in enumerate(views):
            sheet.paste(v, (256 * i, 0))
        sheet.save(Path(preview_dir) / f"{sh.id}.png")
    return sh


def write_item_definition(item_id, shield=False, bow=None):
    path = ASSETS / "items" / f"{item_id}.json"
    path.parent.mkdir(parents=True, exist_ok=True)
    if shield:
        d = {"model": {"type": "minecraft:condition", "property": "minecraft:using_item",
                       "on_true": {"type": "minecraft:model", "model": f"{NS}:item/{item_id}_blocking"},
                       "on_false": {"type": "minecraft:model", "model": f"{NS}:item/{item_id}"}}}
    else:
        scale = 0.05 * bow.get("draw_speed", 1.0)
        d = {"model": {"type": "minecraft:condition", "property": "minecraft:using_item",
                       "on_false": {"type": "minecraft:model", "model": f"{NS}:item/{item_id}"},
                       "on_true": {"type": "minecraft:range_dispatch", "property": "minecraft:use_duration", "scale": round(scale, 4),
                                   "fallback": {"type": "minecraft:model", "model": f"{NS}:item/{item_id}_pulling_0"},
                                   "entries": [{"threshold": 0.65, "model": {"type": "minecraft:model", "model": f"{NS}:item/{item_id}_pulling_1"}},
                                               {"threshold": 0.9, "model": {"type": "minecraft:model", "model": f"{NS}:item/{item_id}_pulling_2"}}]}}}
    with open(path, "w", encoding="utf8", newline="\n") as f:
        json.dump(d, f, indent=2)
        f.write("\n")


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--preview")
    ap.add_argument("--only", default="")
    args = ap.parse_args()
    only = set(filter(None, args.only.split(",")))
    if args.preview:
        os.makedirs(args.preview, exist_ok=True)
    for cls in SHIELDS:
        if only and cls.id not in only:
            continue
        write_shield(cls, args.preview)
        print("shield", cls.id)
    for cls in BOWS:
        if only and cls.id not in only:
            continue
        item_file = ROOT / "items" / "src" / "main" / "resources" / "data" / "skyrpg_items" / "skyrpg" / "items" / f"{cls.id}.json"
        cfg = json.load(open(item_file, encoding="utf8")).get("bow", {})
        write_bow(cls, cfg, args.preview)
        print("bow", cls.id)


if __name__ == "__main__":
    main()
