#!/usr/bin/env python3
"""Generates the client assets for the foraging mod: log/plank/leaf/sapling/stump textures and item icons (pixel art
drawn here, nothing copied from vanilla), blockstates, models, item definitions and lang.

    python tools/art/generate_foraging.py                 # writes the files below
    python tools/art/generate_foraging.py --preview OUT   # also writes a labelled contact sheet (OUT is a .png path)

Writes ONLY under foraging/src/main/resources/assets/skyrpg_foraging (data files are authored elsewhere):
  * textures/block/   <wood>_log, _log_top, _planks, _leaves, _sapling; <stump>_stump_{side,top,side_depleted,cut,bottom}
  * textures/item/    heartwood_splinter, heartwood_bundle, golden_acorn, elder_heartwood
  * blockstates, models/block, models/item, items, lang
The block/item lists match the foraging mod's block and item registries.

Palettes are 5 shades, darkest first. Block textures are procedural (seeded, so output is stable); icons and
saplings are small masks with a legend of ramps, icons get a 1px outline in a darkened version of what they touch.
"""
import json
import math
import random
import sys
from pathlib import Path

from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parent.parent.parent
RES = ROOT / "foraging" / "src" / "main" / "resources"
NS = "skyrpg_foraging"
ASSETS = RES / "assets" / NS

WOODS = ["ironbark", "mistpine", "duskwillow", "emberwood", "starwood"]
STUMPS = ["oak", "birch", "spruce", "ironbark", "mistpine", "duskwillow", "emberwood"]
ITEMS = {"heartwood_splinter": "Heartwood Splinter", "heartwood_bundle": "Heartwood Bundle",
         "golden_acorn": "Golden Acorn", "elder_heartwood": "Elder Heartwood"}


def write_json(path, value):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(value, indent=2) + "\n", encoding="utf-8")


# ---------------------------------------------------------------- palettes (5 shades, darkest first)
BARK = {
    "oak": [(38, 28, 16), (64, 48, 28), (92, 70, 40), (120, 94, 54), (150, 120, 74)],
    "birch": [(120, 118, 110), (180, 178, 170), (214, 212, 204), (234, 232, 224), (250, 248, 242)],
    "spruce": [(30, 20, 12), (50, 34, 20), (74, 50, 30), (98, 70, 42), (124, 92, 58)],
    "ironbark": [(24, 25, 30), (44, 46, 54), (66, 69, 80), (94, 98, 110), (130, 134, 146)],
    "mistpine": [(84, 108, 142), (120, 148, 182), (154, 182, 210), (192, 214, 234), (232, 244, 254)],
    "duskwillow": [(26, 16, 28), (48, 30, 44), (70, 46, 64), (94, 64, 84), (124, 90, 108)],
    "emberwood": [(8, 6, 8), (22, 18, 20), (36, 30, 32), (52, 44, 46), (76, 64, 64)],
    "starwood": [(168, 158, 136), (202, 194, 172), (226, 220, 200), (242, 238, 222), (255, 253, 244)],
}
WOOD = {  # inner wood (log ends, stump cuts)
    "oak": [(140, 108, 60), (172, 134, 76), (196, 158, 94), (214, 180, 116), (232, 204, 148)],
    "birch": [(170, 150, 98), (200, 180, 122), (222, 204, 148), (236, 222, 172), (248, 238, 200)],
    "spruce": [(88, 60, 34), (114, 80, 46), (138, 100, 58), (160, 120, 72), (184, 144, 92)],
    "ironbark": [(70, 32, 18), (104, 50, 26), (140, 72, 38), (174, 100, 54), (204, 134, 84)],
    "mistpine": [(124, 146, 160), (160, 182, 192), (192, 210, 216), (218, 232, 234), (242, 250, 250)],
    "duskwillow": [(72, 40, 78), (100, 60, 106), (130, 84, 134), (160, 112, 164), (192, 150, 196)],
    "emberwood": [(20, 14, 14), (34, 26, 26), (50, 38, 36), (68, 52, 48), (90, 70, 62)],
    "starwood": [(190, 160, 90), (222, 194, 120), (242, 222, 160), (252, 240, 196), (255, 250, 226)],
}
EMBER = [(150, 40, 10), (220, 80, 16), (255, 130, 30), (255, 190, 70), (255, 236, 150)]
GOLD = [(150, 110, 20), (200, 150, 30), (240, 190, 50), (255, 224, 100), (255, 246, 170)]
MOSS = [(24, 52, 24), (38, 80, 34), (56, 110, 44), (84, 144, 58), (120, 180, 80)]
SPROUT = [(22, 70, 28), (40, 120, 40), (70, 170, 56), (116, 214, 78), (176, 244, 120)]
LEAF = {  # (main ramp, optional second ramp for two-tone leaves)
    "ironbark": ([(12, 38, 24), (22, 62, 38), (36, 88, 54), (58, 116, 74), (92, 146, 104)], None),
    "mistpine": ([(18, 66, 72), (32, 100, 104), (54, 136, 132), (92, 172, 162), (148, 214, 198)], None),
    "duskwillow": ([(36, 20, 76), (64, 38, 120), (98, 62, 162), (136, 94, 198), (178, 138, 230)],
                   [(22, 42, 34), (38, 68, 50), (60, 98, 68), (90, 130, 90), (128, 164, 118)]),
    "emberwood": ([(74, 10, 10), (126, 20, 14), (176, 38, 20), (218, 64, 28), (244, 102, 44)],
                  [(146, 46, 8), (202, 82, 14), (238, 126, 24), (255, 172, 48), (255, 212, 98)]),
    "starwood": ([(66, 104, 156), (100, 144, 198), (138, 182, 228), (178, 214, 246), (220, 238, 255)], None),
}
AMBER = [(84, 28, 14), (146, 58, 22), (198, 98, 32), (236, 148, 54), (255, 206, 112)]
TWINE = [(96, 70, 40), (146, 112, 64), (190, 154, 92), (222, 192, 128), (244, 224, 168)]
ACORN = [(130, 80, 8), (190, 124, 16), (240, 178, 36), (255, 220, 84), (255, 247, 190)]
ACORNCAP = [(96, 58, 10), (150, 96, 16), (200, 140, 28), (232, 176, 48), (255, 214, 96)]
ELDER = [(14, 8, 22), (30, 18, 46), (52, 32, 74), (78, 50, 108), (108, 72, 144)]
PURPLE = [(70, 30, 140), (126, 60, 214), (180, 100, 255), (224, 164, 255), (250, 230, 255)]


def clamp(v, lo, hi):
    return max(lo, min(hi, v))


def darken(img, f):
    out = img.copy()
    for y in range(img.height):
        for x in range(img.width):
            r, g, b, a = img.getpixel((x, y))
            out.putpixel((x, y), (int(r * f), int(g * f), int(b * f), a))
    return out


def put(img, x, y, c):
    if 0 <= x < img.width and 0 <= y < img.height:
        img.putpixel((x, y), tuple(c)[:3] + (255,))


# ---------------------------------------------------------------- sprites (item icons, saplings)
def sprite(rows, outlined=True, **ramps):
    """Mask -> RGBA. Legend: '1'-'5' ramp A, 'a'-'e' ramp B, 'p'-'t' ramp C, 'u'-'y' ramp D (all darkest to lightest),
    '#' near-black. '.' is empty; short rows are padded."""
    legend = {"#": (24, 20, 28)}
    for chars, key in (("12345", "A"), ("abcde", "B"), ("pqrst", "C"), ("uvwxy", "D")):
        if key in ramps:
            for ch, col in zip(chars, ramps[key]):
                legend[ch] = col
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    for y, row in enumerate(rows[:16]):
        assert len(row) <= 16, (y, row)
        for x, ch in enumerate(row):
            if ch != ".":
                img.putpixel((x, y), legend[ch] + (255,))
    return outline(img) if outlined else img


def outline(img):
    out = img.copy()
    for y in range(16):
        for x in range(16):
            if img.getpixel((x, y))[3] == 0:
                for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                    nx, ny = x + dx, y + dy
                    if 0 <= nx < 16 and 0 <= ny < 16 and img.getpixel((nx, ny))[3] > 0:
                        c = img.getpixel((nx, ny))
                        out.putpixel((x, y), (int(c[0] * .3), int(c[1] * .3), int(c[2] * .3), 255))
                        break
    return out


# ---------------------------------------------------------------- bark / rings / planks / leaves
def bark(species, seed):
    """Vertical bark ridges: per-column base shade, broken into short offset runs, dark crevices, plus a species flourish."""
    ramp = BARK[species]
    rnd = random.Random(seed)
    base = [rnd.choice((1, 2, 2, 2, 3, 3, 4)) for _ in range(16)]
    for x in range(16):  # a crevice is shadowed, its right neighbour catches light
        if base[x] == 1 and base[(x + 1) % 16] < 3:
            base[(x + 1) % 16] = 3
    grid = [[2] * 16 for _ in range(16)]
    for x in range(16):
        y = -rnd.randint(0, 4)
        while y < 16:
            run = rnd.randint(3, 8)
            d = rnd.choice((-1, 0, 0, 0, 1))
            for yy in range(max(0, y), min(16, y + run)):
                grid[yy][x] = clamp(base[x] + d, 0, 4)
            y += run
    if species == "birch":  # papery white with dark horizontal lenticels
        for y in range(16):
            for x in range(16):
                grid[y][x] = clamp(grid[y][x], 1, 4) if rnd.random() < .7 else 3
        for _ in range(7):
            x, y, w = rnd.randint(0, 12), rnd.randint(0, 15), rnd.randint(2, 4)
            for i in range(w):
                grid[y][(x + i) % 16] = 0
    if species == "ironbark":  # riveted plates: horizontal seams broken by dark gaps
        for sy, off in ((5, 3), (11, 9)):
            for x in range(16):
                grid[sy][x] = 1 if (x + off) % 5 else 0
                grid[sy + 1][x] = clamp(grid[sy + 1][x] + 1, 0, 4)
    img = Image.new("RGBA", (16, 16))
    for y in range(16):
        for x in range(16):
            img.putpixel((x, y), ramp[grid[y][x]] + (255,))
    if species == "emberwood":  # glowing cracks follow the crevices
        for x in range(16):
            if base[x] <= 1:
                y = rnd.randint(0, 6)
                for yy in range(y, min(16, y + rnd.randint(4, 9))):
                    if rnd.random() < .85:
                        put(img, x, yy, EMBER[1 if yy % 3 else 2])
                        for nx in ((x - 1) % 16, (x + 1) % 16):
                            r, g, b, _ = img.getpixel((nx, yy))
                            if (r, g, b) not in EMBER:
                                put(img, nx, yy, (clamp(r + 34, 0, 255), clamp(g + 8, 0, 255), b))
        for _ in range(3):
            put(img, rnd.randint(0, 15), rnd.randint(0, 15), EMBER[3])
    if species == "starwood":
        for _ in range(9):
            put(img, rnd.randint(0, 15), rnd.randint(0, 15), GOLD[rnd.choice((2, 3, 3, 4))])
    if species == "mistpine":  # cold streaks and frost glints
        for _ in range(5):
            x, y = rnd.randint(0, 15), rnd.randint(0, 11)
            for yy in range(y, y + rnd.randint(2, 4)):
                put(img, x, yy, ramp[0])
        for _ in range(6):
            put(img, rnd.randint(0, 15), rnd.randint(0, 15), (255, 255, 255))
    if species == "duskwillow":  # long vertical fissures with a violet sheen
        for x in rnd.sample(range(16), 3):
            y = rnd.randint(0, 5)
            for yy in range(y, y + rnd.randint(7, 11)):
                put(img, x, yy % 16, ramp[0])
        for _ in range(7):
            put(img, rnd.randint(0, 15), rnd.randint(0, 15), (146, 104, 168))
    return img


def rings(species, seed, ring_gap=1.7):
    """A log end: bark frame around concentric growth rings (a faint knot off-centre keeps it from looking printed)."""
    rnd = random.Random(seed)
    wood, frame = WOOD[species], BARK[species]
    img = Image.new("RGBA", (16, 16))
    cx, cy = 7.5 + rnd.choice((-.4, .4)), 7.5 + rnd.choice((-.4, .4))
    for y in range(16):
        for x in range(16):
            edge = min(x, y, 15 - x, 15 - y)
            if edge == 0:
                c = frame[1]
            elif edge == 1:
                c = frame[2 if (x + y) % 3 else 3]
            else:
                d = math.hypot(x - cx, y - cy) * .65 + max(abs(x - cx), abs(y - cy)) * .35
                k = d / ring_gap
                frac = k - int(k)
                idx = 3 if frac < .45 else 2 if frac < .75 else 1
                if int(k) % 2:
                    idx -= 1 if idx > 1 else 0
                if rnd.random() < .06:
                    idx = clamp(idx + rnd.choice((-1, 1)), 0, 4)
                c = wood[idx]
            img.putpixel((x, y), c + (255,))
    for x, y in ((7, 7), (8, 7), (7, 8), (8, 8)):  # pith
        put(img, x, y, wood[0])
    if species == "emberwood":  # the heart still smoulders
        for y in range(16):
            for x in range(16):
                d = math.hypot(x - 7.5, y - 7.5)
                if min(x, y, 15 - x, 15 - y) < 2:
                    continue
                if d < 1.6:
                    put(img, x, y, EMBER[4])
                elif d < 2.8:
                    put(img, x, y, EMBER[3])
                elif d < 4.2:
                    put(img, x, y, EMBER[2] if (x + y) % 2 else EMBER[1])
                elif d < 5.4 and rnd.random() < .55:
                    put(img, x, y, EMBER[0] if rnd.random() < .6 else EMBER[1])
        for x, y in ((2, 2), (13, 3), (3, 13), (12, 12)):
            put(img, x, y, EMBER[1])
    if species == "starwood":
        for _ in range(6):
            x, y = rnd.randint(3, 12), rnd.randint(3, 12)
            put(img, x, y, GOLD[3])
    return img


def planks(species, seed):
    """Four horizontal boards with grain, a dark seam under each and a staggered butt joint."""
    ramp = {"ironbark": [(54, 36, 30), (84, 52, 38), (116, 70, 44), (146, 92, 56), (172, 118, 76)],
            "mistpine": [(132, 150, 162), (162, 182, 192), (190, 208, 216), (214, 228, 234), (238, 246, 248)],
            "duskwillow": [(54, 30, 60), (82, 48, 88), (108, 68, 112), (134, 92, 138), (160, 120, 164)],
            "emberwood": [(22, 16, 16), (40, 30, 28), (58, 42, 38), (78, 56, 48), (98, 72, 62)],
            "starwood": [(206, 180, 118), (232, 212, 156), (246, 234, 186), (254, 246, 214), (255, 252, 236)]}[species]
    rnd = random.Random(seed)
    img = Image.new("RGBA", (16, 16))
    joint = [rnd.randint(2, 13) for _ in range(4)]
    tone = [2, 3, 2, 3]
    rnd.shuffle(tone)
    for b in range(4):
        for dy in range(4):
            y = b * 4 + dy
            for x in range(16):
                idx = tone[b]
                if dy == 3:
                    idx = 0
                elif dy == 0:
                    idx = clamp(idx + 1, 0, 4)
                elif rnd.random() < .22:  # grain streak
                    idx = clamp(idx + rnd.choice((-1, -1, 1)), 1, 4)
                if x == joint[b] and dy < 3:
                    idx = 0
                img.putpixel((x, y), ramp[idx] + (255,))
        # grain runs that continue over a few pixels
        gy = b * 4 + rnd.choice((1, 2))
        gx = rnd.randint(0, 8)
        for i in range(rnd.randint(4, 7)):
            put(img, (gx + i) % 16, gy, ramp[clamp(tone[b] - 1, 1, 4)])
    if species == "starwood":  # gold grain
        for b in range(4):
            gy = b * 4 + 1 + (b % 2)
            gx = rnd.randint(0, 6)
            for i in range(rnd.randint(5, 9)):
                put(img, (gx + i) % 16, gy, GOLD[2 if i % 3 else 3])
        for _ in range(4):
            put(img, rnd.randint(0, 15), rnd.randint(0, 14), GOLD[4])
    if species == "emberwood":  # glowing hairline in the seams
        for b in range(4):
            for x in range(16):
                if rnd.random() < .22:
                    put(img, x, b * 4 + 3, EMBER[0 if rnd.random() < .6 else 1])
        for _ in range(3):
            put(img, rnd.randint(0, 15), rnd.choice((3, 7, 11, 15)), EMBER[2])
    if species == "ironbark":  # nail heads
        for b in range(4):
            put(img, (joint[b] + 2) % 16, b * 4 + 1, (150, 154, 168))
    if species == "duskwillow":
        for _ in range(4):
            put(img, rnd.randint(0, 15), rnd.randint(0, 14), (172, 130, 186))
    if species == "mistpine":
        for _ in range(4):
            put(img, rnd.randint(0, 15), rnd.randint(0, 14), (255, 255, 255))
    return img


def leaves(species, seed, hole=.11):
    """Pre-coloured leaves: smoothed noise gives clumps, the darkest clumps get 1px holes (transparent, like vanilla)."""
    main, second = LEAF[species]
    rnd = random.Random(seed)

    def noise():
        raw = [[rnd.random() for _ in range(16)] for _ in range(16)]
        out = [[0.0] * 16 for _ in range(16)]
        for y in range(16):
            for x in range(16):
                s = 0.0
                for dy in (-1, 0, 1):
                    for dx in (-1, 0, 1):
                        s += raw[(y + dy) % 16][(x + dx) % 16] * (2.0 if dx == 0 and dy == 0 else 1.0)
                out[y][x] = s / 10.0 + raw[y][x] * .15
        return out

    n1, n2 = noise(), noise()
    flat = sorted(v for row in n1 for v in row)
    q = [flat[int(len(flat) * f)] for f in (.10, .34, .70, .92)]
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    holes = set()
    for y in range(16):
        for x in range(16):
            v = n1[y][x]
            idx = 0 if v < q[0] else 1 if v < q[1] else 2 if v < q[2] else 3 if v < q[3] else 4
            if v < flat[int(len(flat) * (hole * 1.5))] and rnd.random() < .7:
                near = sum((x + dx, y + dy) in holes for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)))
                if near == 0 or rnd.random() < .25:
                    holes.add((x, y))
                    continue
            ramp = second if second and n2[y][x] > .5 else main
            img.putpixel((x, y), ramp[idx] + (255,))
    # light catches the top-left of each clump
    for y in range(16):
        for x in range(16):
            p = img.getpixel((x, y))
            if p[3] and rnd.random() < .06:
                put(img, x, y, tuple(clamp(int(c * 1.15) + 4, 0, 255) for c in p[:3]))
    if species == "mistpine":  # frost specks
        for _ in range(11):
            x, y = rnd.randint(0, 15), rnd.randint(0, 15)
            if img.getpixel((x, y))[3]:
                put(img, x, y, (244, 252, 255) if rnd.random() < .7 else (200, 232, 240))
    if species == "starwood":  # tiny four-point sparkles
        for sx, sy in ((4, 4), (11, 10)):
            for dx, dy in ((0, 0), (1, 0), (-1, 0), (0, 1), (0, -1)):
                put(img, sx + dx, sy + dy, (255, 255, 255) if dx == dy == 0 else (214, 232, 255))
        for _ in range(5):
            x, y = rnd.randint(0, 15), rnd.randint(0, 15)
            if img.getpixel((x, y))[3]:
                put(img, x, y, (255, 255, 255))
    if species == "ironbark":  # grey cast: a few cold lichen pixels
        for _ in range(8):
            x, y = rnd.randint(0, 15), rnd.randint(0, 15)
            if img.getpixel((x, y))[3]:
                put(img, x, y, (88, 112, 100))
    if species == "emberwood":
        for _ in range(6):
            x, y = rnd.randint(0, 15), rnd.randint(0, 15)
            if img.getpixel((x, y))[3]:
                put(img, x, y, (255, 224, 120))
    return img


SAPLING_LEGEND = {  # d dark, l mid, h light, T trunk, t trunk shade, s special
    "ironbark": dict(d=(14, 42, 26), l=(30, 76, 46), h=(58, 116, 74), T=(70, 73, 84), t=(38, 40, 48), s=(92, 146, 104)),
    "mistpine": dict(d=(24, 80, 84), l=(54, 136, 132), h=(124, 192, 178), T=(142, 162, 186), t=(96, 114, 138), s=(244, 252, 255)),
    "duskwillow": dict(d=(40, 24, 82), l=(98, 62, 162), h=(150, 110, 210), T=(70, 46, 64), t=(40, 26, 38), s=(90, 130, 90)),
    "emberwood": dict(d=(150, 30, 18), l=(218, 64, 28), h=(255, 170, 50), T=(38, 32, 34), t=(18, 14, 16), s=(255, 224, 120)),
    "starwood": dict(d=(80, 120, 176), l=(138, 182, 228), h=(200, 226, 250), T=(226, 220, 200), t=(180, 170, 146), s=(255, 255, 255)),
}
SAPLING_MASK = {
    "ironbark": ["................", "................", ".....dddddd.....", "....dllllhhd....", "...dllhllllld...",
                 "...dlllllhlld...", "...dhllllllld...", "....dllldllld...", ".....ddlTldd....", ".......TT.......",
                 ".......Tt.......", ".......TT.......", ".......Tt.......", "......tTTt......", "......TTTT......"],
    "mistpine": ["................", ".......s........", ".......h........", "......dll.......", "......lhl.......",
                 ".....dllll......", ".....dllhl.d....", "....dlllllld....", "....dlhlllld....", "...dllllsllld...",
                 ".....ddtTdd.....", ".......Tt.......", ".......Tt.......", "......tTt.......", "......tTTt......"],
    "duskwillow": ["................", ".....dddd.......", "...ddlllhdd.....", "..dllhllllld....", "..dlllllllhld...",
                   ".dllhlTllllld...", ".dl.l.T.l.ld....", ".dl.l.Tt.l.l....", ".l..l.T.s.l.....", ".l..d.Tt..l.....",
                   ".d....T...d.....", "......Tt........", "......T.........", ".....tTt........", ".....TTT........"],
    "emberwood": ["................", ".......h........", "......hl.h......", "..h..hll.lh.....", "..lh.dlllld.h...",
                  "...lllldllll....", "..dlhllllhld....", "...dllhlldd.....", "....ddlTdd......", ".......Tt.......",
                  ".......T........", "......tT........", "......Tt........", "......TTt.......", ".....tTTT......."],
    "starwood": ["................", "....s...........", "...dddd...s.....", "..dllhhld.......", ".dllhllllld.....",
                 ".dlhllslhld.....", ".dllllllhld.....", "..dlslhlld......", "...ddlTldd..s...", ".....sTt........",
                 ".......T........", ".......Tt.......", "......tTt.......", "......tTTt......", "................"],
}


def sapling(wood):
    legend = SAPLING_LEGEND[wood]
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    for y, row in enumerate(SAPLING_MASK[wood]):
        assert len(row) <= 16, (wood, y, row)
        for x, ch in enumerate(row):
            if ch != ".":
                img.putpixel((x, y), legend[ch] + (255,))
    return img


# ---------------------------------------------------------------- stumps
def stump_side(species, depleted):
    img = bark(species, 700 + STUMPS.index(species) * 5)
    rnd = random.Random(800 + STUMPS.index(species))
    # the root flare: a darker foot and a lighter lip just under the cut
    for x in range(16):
        r, g, b, _ = img.getpixel((x, 15))
        put(img, x, 15, (int(r * .7), int(g * .7), int(b * .7)))
        r, g, b, _ = img.getpixel((x, 0))
        put(img, x, 0, (clamp(int(r * 1.15) + 6, 0, 255), clamp(int(g * 1.15) + 6, 0, 255), clamp(int(b * 1.15) + 6, 0, 255)))
    if depleted:
        img = darken(img, .8)
        for _ in range(9):  # mossy clumps, heavier near the ground
            x, y = rnd.randint(0, 15), int(16 - abs(rnd.gauss(0, 4.5)))
            y = clamp(y, 3, 15)
            for dx, dy in ((0, 0), (1, 0), (0, 1), (-1, 0)):
                if rnd.random() < .75:
                    put(img, (x + dx) % 16, clamp(y + dy, 0, 15), MOSS[rnd.choice((1, 2, 2, 3))])
        for _ in range(3):
            put(img, rnd.randint(0, 15), rnd.randint(8, 15), MOSS[4])
    return img


def stump_top(species):
    """Log end with a fresh sprout / leafy tuft growing from the middle."""
    img = rings(species, 900 + STUMPS.index(species))
    s = SPROUT
    tuft = [  # (dx, dy, shade) around (7,7)
        (0, 1, 1), (0, 0, 1), (0, -1, 2),
        (-1, -1, 2), (-2, -2, 3), (-3, -2, 2), (-2, -3, 3), (-3, -3, 4),
        (1, -2, 3), (2, -3, 3), (3, -3, 4), (2, -4, 2), (3, -2, 2), (1, -3, 2),
        (-1, 0, 0), (1, 0, 0),
    ]
    for dx, dy, sh in tuft:
        x, y = 7 + dx, 8 + dy
        # shadowed outline pixel under the leaf so it separates from the rings
        put(img, x, y, s[sh])
    # a tiny leafy fan: two leaves left and right of the stem
    for x, y, sh in ((5, 7, 3), (4, 7, 2), (4, 8, 1), (9, 8, 2), (10, 8, 3), (10, 9, 1), (6, 8, 2), (8, 9, 2)):
        put(img, x, y, s[sh])
    for x, y in ((7, 9), (8, 8)):
        put(img, x, y, s[1])
    return img


def stump_cut(species):
    """Pale, freshly cut end (rings lightened) with an axe notch bitten into the near-left edge."""
    base = rings(species, 1000 + STUMPS.index(species), ring_gap=1.9)
    light = Image.new("RGBA", (16, 16))
    rnd = random.Random(1100 + STUMPS.index(species))
    wood = WOOD[species]
    if species in ("ironbark", "emberwood", "duskwillow"):
        wood = [tuple(clamp(int(c * 1.25) + 24, 0, 255) for c in col) for col in wood]
    if species == "emberwood":  # char fell away; pale ash-grey wood underneath
        wood = [(88, 70, 62), (122, 100, 88), (152, 128, 112), (178, 154, 136), (204, 182, 162)]
    for y in range(16):
        for x in range(16):
            r, g, b, _ = base.getpixel((x, y))
            edge = min(x, y, 15 - x, 15 - y)
            if edge <= 1:
                c = BARK[species][1 if edge == 0 else 2]
            else:
                # remap by nearest shade in this species' ring ramp, then shift into the lightened ramp
                src = WOOD[species] if species != "emberwood" else WOOD[species]
                idx = min(range(5), key=lambda i: sum((a - b2) ** 2 for a, b2 in zip(src[i], (r, g, b))))
                if species == "emberwood":
                    idx = rnd.choice((1, 2, 2, 3)) if edge > 1 else 1
                c = wood[idx]
            light.putpixel((x, y), tuple(c) + (255,))
    # axe notch: a wedge cut in from the top-left rim, dark inside, bright cut edge
    wedge = [(2, 5), (3, 5), (4, 5), (5, 5), (3, 6), (4, 6), (5, 6), (4, 7), (5, 7)]
    wedge = [(x, y - 2) for x, y in wedge]  # (2..5, 3..5)
    for x, y in wedge:
        put(light, x, y, wood[0])
    for x, y in ((2, 2), (3, 2), (4, 2), (5, 2), (6, 3), (6, 4), (5, 5), (6, 5)):
        put(light, x, y, wood[4])
    for x, y in ((3, 4), (4, 4), (4, 5), (3, 3)):
        put(light, x, y, tuple(int(c * .55) for c in wood[0]))
    # chop marks
    for x, y in ((9, 10), (10, 10), (11, 10), (10, 11)):
        put(light, x, y, wood[1])
    return light


# ---------------------------------------------------------------- item icons
def splinter_icon():
    rows = ["................", "..............3.", ".............34.", "............345.", "...........3453.",
            "..........3453..", ".........3453...", "........3453....", ".......3453.....", "......3453......",
            ".....3453.......", "....3453........", "...3453.........", "..3443..........", "..323...........", "..2............."]
    img = sprite(rows, A=AMBER)
    for x, y in ((11, 4), (9, 6), (7, 8), (5, 10), (3, 12)):  # grain pulled along the shard
        if img.getpixel((x, y))[3]:
            put(img, x, y, AMBER[1])
    for x, y in ((12, 3), (10, 5), (8, 7)):
        put(img, x, y, AMBER[4])
    return img


def bundle_icon():
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    staves = [(1, 4, 14), (4, 2, 14), (7, 1, 13), (10, 3, 14)]  # (x, top, bottom): 2px staves with 1px gaps
    for n, (x, top, bot) in enumerate(staves):
        for y in range(top, bot + 1):
            put(img, x, y, AMBER[3 if (y + n) % 5 else 2])
            put(img, x + 1, y, AMBER[1 if (y + n) % 4 else 2])
        put(img, x, top, AMBER[4])
        put(img, x + 1, top, AMBER[3])
        put(img, x, bot, AMBER[1])
        put(img, x + 1, bot, AMBER[0])
    img = outline(img)
    for y in (8, 9):  # twine, wrapped twice, with a knot and loose ends
        for x in range(1, 12):
            put(img, x, y, TWINE[3] if y == 8 else TWINE[1])
        put(img, 0, y, outline_dark(TWINE[0]))
        put(img, 12, y, outline_dark(TWINE[0]))
    put(img, 6, 8, TWINE[4])
    put(img, 6, 9, TWINE[2])
    for x, y, c in ((7, 10, 2), (8, 11, 1), (6, 10, 1), (5, 11, 0)):
        put(img, x, y, TWINE[c])
    return img


def outline_dark(c):
    return tuple(int(v * .3) for v in c)


def acorn_icon():
    rows = ["................", ".........pq.....", "........pq......", "......ppqq......", "....bcbcbcbc....",
            "...bcbcbcbcbc...", "...cbcbcbcbcb...", "...bbbbbbbbbb...", "...2544444332...", "...2554444332...",
            "...2555444332...", "....24444432....", "....22444322....", ".....224322.....", "......2322......", ".......3........"]
    img = sprite(rows, A=ACORN, B=ACORNCAP, C=ACORNCAP)
    for x, y in ((5, 9), (5, 10), (6, 10), (5, 11)):  # specular
        put(img, x, y, ACORN[4])
    put(img, 5, 9, (255, 255, 255))
    put(img, 12, 2, (255, 255, 255))  # sparkle
    for x, y in ((12, 1), (12, 3), (11, 2), (13, 2)):
        put(img, x, y, ACORN[4])
    return img


def elder_icon():
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    rnd = random.Random(1500)
    cx, cy = 7.5, 8.0
    for y in range(16):
        for x in range(16):
            dx, dy = x - cx, y - cy
            ang = math.atan2(dy, dx)
            rad = 6.6 + .7 * math.sin(ang * 3 + 1) + .4 * math.cos(ang * 5)
            d = math.hypot(dx, dy)
            if d > rad:
                continue
            if d > rad - 1.2:
                c = ELDER[1] if (x + y) % 3 else ELDER[2]
            else:
                k = d
                if k < 1.2:
                    c = PURPLE[4]
                elif k < 2.2:
                    c = PURPLE[3]
                elif k < 3.0:
                    c = ELDER[0]
                elif k < 4.0:
                    c = PURPLE[2] if (x + y) % 2 else PURPLE[1]
                elif k < 4.9:
                    c = ELDER[1]
                else:
                    c = PURPLE[0] if rnd.random() < .6 else ELDER[2]
            img.putpixel((x, y), c + (255,))
    img = outline(img)
    # the outline reads as a violet-black aura; add a few drifting motes
    for x, y, c in ((1, 2, PURPLE[2]), (14, 3, PURPLE[3]), (14, 13, PURPLE[2]), (1, 13, PURPLE[1]), (8, 0, PURPLE[3]), (15, 8, PURPLE[1])):
        if img.getpixel((x, y))[3] == 0:
            put(img, x, y, c)
    return img


# ---------------------------------------------------------------- model / blockstate helpers
def block_item(name):
    write_json(ASSETS / "items" / f"{name}.json", {"model": {"type": "minecraft:model", "model": f"{NS}:block/{name}"}})


def generated_item(name, texture):
    write_json(ASSETS / "models" / "item" / f"{name}.json", {"parent": "minecraft:item/generated", "textures": {"layer0": texture}})
    write_json(ASSETS / "items" / f"{name}.json", {"model": {"type": "minecraft:model", "model": f"{NS}:item/{name}"}})


def cube(name, parent, textures):
    write_json(ASSETS / "models" / "block" / f"{name}.json", {"parent": parent, "textures": textures})


def main():
    bt = ASSETS / "textures" / "block"
    it = ASSETS / "textures" / "item"
    bt.mkdir(parents=True, exist_ok=True)
    it.mkdir(parents=True, exist_ok=True)
    lang = {"itemGroup.skyrpg_foraging": "SkyRPG Foraging", "tag.item.skyrpg_foraging.stumps": "Stumps"}
    sheet = {"woods": [], "stumps": [], "items": []}

    def save(img, folder, name, group, label=None):
        img.save(folder / f"{name}.png")
        sheet[group].append((label or name, img))

    # Woods.
    for i, wood in enumerate(WOODS):
        label = wood.capitalize()
        save(bark(wood, 10 + i), bt, f"{wood}_log", "woods", f"{wood} log")
        save(rings(wood, 20 + i), bt, f"{wood}_log_top", "woods", "log top")
        save(planks(wood, 30 + i), bt, f"{wood}_planks", "woods", "planks")
        save(leaves(wood, 40 + i), bt, f"{wood}_leaves", "woods", "leaves")
        save(sapling(wood), bt, f"{wood}_sapling", "woods", "sapling")

        write_json(ASSETS / "blockstates" / f"{wood}_log.json", {"variants": {
            "axis=y": {"model": f"{NS}:block/{wood}_log"},
            "axis=z": {"model": f"{NS}:block/{wood}_log_horizontal", "x": 90},
            "axis=x": {"model": f"{NS}:block/{wood}_log_horizontal", "x": 90, "y": 90}}})
        end, side = f"{NS}:block/{wood}_log_top", f"{NS}:block/{wood}_log"
        cube(f"{wood}_log", "minecraft:block/cube_column", {"end": end, "side": side})
        cube(f"{wood}_log_horizontal", "minecraft:block/cube_column_horizontal", {"end": end, "side": side})
        write_json(ASSETS / "blockstates" / f"{wood}_planks.json", {"variants": {"": {"model": f"{NS}:block/{wood}_planks"}}})
        cube(f"{wood}_planks", "minecraft:block/cube_all", {"all": f"{NS}:block/{wood}_planks"})
        write_json(ASSETS / "blockstates" / f"{wood}_leaves.json", {"variants": {"": {"model": f"{NS}:block/{wood}_leaves"}}})
        cube(f"{wood}_leaves", "minecraft:block/leaves", {"all": f"{NS}:block/{wood}_leaves"})
        write_json(ASSETS / "blockstates" / f"{wood}_sapling.json", {"variants": {
            "stage=0": {"model": f"{NS}:block/{wood}_sapling"}, "stage=1": {"model": f"{NS}:block/{wood}_sapling"}}})
        cube(f"{wood}_sapling", "minecraft:block/cross", {"cross": f"{NS}:block/{wood}_sapling"})
        for part in ("log", "planks", "leaves"):
            block_item(f"{wood}_{part}")
        generated_item(f"{wood}_sapling", f"{NS}:block/{wood}_sapling")
        for part in ("Log", "Planks", "Leaves", "Sapling"):
            lang[f"block.{NS}.{wood}_{part.lower()}"] = f"{label} {part}"

    # Stumps.
    for s in STUMPS:
        side, top = f"{NS}:block/{s}_stump_side", f"{NS}:block/{s}_stump_top"
        save(stump_side(s, False), bt, f"{s}_stump_side", "stumps", f"{s} side")
        save(stump_top(s), bt, f"{s}_stump_top", "stumps", "top")
        save(stump_side(s, True), bt, f"{s}_stump_side_depleted", "stumps", "side dep")
        save(stump_cut(s), bt, f"{s}_stump_cut", "stumps", "cut")
        bottom = rings(s, 1200 + STUMPS.index(s))
        save(bottom, bt, f"{s}_stump_bottom", "stumps", "bottom")
        cube(f"{s}_stump", "minecraft:block/cube_bottom_top", {"top": top, "bottom": f"{NS}:block/{s}_stump_bottom", "side": side})
        cube(f"{s}_stump_depleted", "minecraft:block/cube_bottom_top", {
            "top": f"{NS}:block/{s}_stump_cut", "bottom": f"{NS}:block/{s}_stump_bottom", "side": f"{NS}:block/{s}_stump_side_depleted"})
        write_json(ASSETS / "blockstates" / f"{s}_stump.json", {"variants": {
            "depleted=false": {"model": f"{NS}:block/{s}_stump"}, "depleted=true": {"model": f"{NS}:block/{s}_stump_depleted"}}})
        block_item(f"{s}_stump")
        lang[f"block.{NS}.{s}_stump"] = f"{s.capitalize()} Stump"

    # Items.
    icons = {"heartwood_splinter": splinter_icon(), "heartwood_bundle": bundle_icon(),
             "golden_acorn": acorn_icon(), "elder_heartwood": elder_icon()}
    for name, label in ITEMS.items():
        save(icons[name], it, name, "items")
        generated_item(name, f"{NS}:item/{name}")
        lang[f"item.{NS}.{name}"] = label
    write_json(ASSETS / "lang" / "en_us.json", lang)

    # Validate: every json parses and every texture a model references exists.
    missing = []
    for p in ASSETS.rglob("*.json"):
        data = json.loads(p.read_text(encoding="utf-8"))
        if p.parent.parts[-2:] in (("models", "block"), ("models", "item")):
            for ref in data.get("textures", {}).values():
                if ref.startswith(NS + ":") and not (ASSETS / "textures" / f"{ref[len(NS) + 1:]}.png").exists():
                    missing.append((p.name, ref))
    assert not missing, missing

    if "--preview" in sys.argv:
        out = Path(sys.argv[sys.argv.index("--preview") + 1])
        out.parent.mkdir(parents=True, exist_ok=True)
        scale, pad = 7, 6
        cell = 16 * scale + pad * 2
        rows = []
        for i in range(0, len(sheet["woods"]), 5):
            rows.append(sheet["woods"][i:i + 5])
        for i in range(0, len(sheet["stumps"]), 5):
            rows.append(sheet["stumps"][i:i + 5])
        rows.append(sheet["items"])
        cols = 10
        img = Image.new("RGBA", (cols * cell, len(rows) * (cell + 14)), (24, 26, 34, 255))
        draw = ImageDraw.Draw(img)
        # arrange woods: 5 textures per wood, two woods side by side per row
        flat = []
        for grp in ("woods", "stumps", "items"):
            flat.append(sheet[grp])
        y = 0
        for grp in flat:
            for i, (n, t) in enumerate(grp):
                gx, gy = i % cols, i // cols
                x0, y0 = gx * cell, y + gy * (cell + 14)
                bg = (110, 150, 200, 255) if "leaves" in n or "sapling" in n else (60, 54, 66, 255)
                img.paste(Image.new("RGBA", (cell - 4, cell - 4), bg), (x0 + 2, y0 + 2))
                img.alpha_composite(t.resize((16 * scale, 16 * scale), Image.NEAREST), (x0 + pad, y0 + pad))
                draw.text((x0 + 4, y0 + cell - 2), n, fill=(230, 230, 240, 255))
            y += ((len(grp) + cols - 1) // cols) * (cell + 14)
        img.crop((0, 0, img.width, y)).save(out)
        print("preview:", out)
    print(f"Wrote {len(WOODS)} woods, {len(STUMPS)} stumps, {len(ITEMS)} items")


if __name__ == "__main__":
    main()
