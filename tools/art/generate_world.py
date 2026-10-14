#!/usr/bin/env python3
"""Draws the world module's items and the waystone block, and writes their assets, lang and item definitions.

    python tools/art/generate_world.py                  # writes the files below
    python tools/art/generate_world.py --preview DIR    # also writes a contact sheet to DIR

Same engine and palettes as generate_items.py (masks, auto shading, a coloured outline on icons; see its docstring),
which this imports. Writes, under world/src/main/resources/:
  * assets/skyrpg_world/textures/item/<id>.png   16x16 icons: crypt_key, hollow_key, tide_key (vault keys),
                                                 warden_sigil, arachne_eye, tide_pearl (boss tokens)
  * assets/skyrpg_world/models/item/<id>.json, items/<id>.json
  * assets/skyrpg_world/textures/block/waystone_{side,side_lit,top,base}.png, models/block/waystone{,_lit}.json,
    blockstates/waystone.json (boolean `lit`), items/waystone.json (shows the lit model)
  * the danger regions' blocks (blightgrass, blight_crystal, ashen_soil, cursed_ice, mire_mud) and essences
  * the deep layers' blocks (glimmerstone, glimmer_cluster, glowcap, glowcap_block, spore_puffball{,_burst},
    dwarven_runestone; models, blockstates, item models) and items (glimmer_geode, glowcap_spores, dwarven_cog,
    rift_ember, thane_key, thanes_rune)
  * the Nether's blocks (ember_ash, void_crack: the new biomes' ground), the Infernal Citadel's key and trophy (citadel_key,
    tyrants_core) and the three biomes' names (biome.skyrpg_world.<id>)
  * the End's blocks (starstone, astral_crystal, void_bloom, void_cap), the Astral Shard, the Astral Observatory's key and trophy
    (observatory_key, star_warden_core) and the three End biomes' names
  * assets/skyrpg_world/lang/en_us.json
  * data/skyrpg_world/skyrpg/items/<id>.json     the items mod's item definitions (name, rarity, type, description)
"""
import argparse
import math
import sys
from pathlib import Path

sys.dont_write_bytecode = True  # importing generate_items shouldn't leave a __pycache__ behind
import generate_items as gi  # noqa: E402
from PIL import Image  # noqa: E402

M = gi.Material
WORLD = gi.ROOT / "world" / "src" / "main" / "resources"
ASSETS = WORLD / "assets" / "skyrpg_world"
DATA = WORLD / "data" / "skyrpg_world" / "skyrpg" / "items"

# ---------------------------------------------------------------- palettes (darkest first)
BRONZE = M(["3a2208", "6a4214", "9c6a24", "cc9440", "f2c874"], texture=gi.sheen)
BONE = M(["5a5240", "8c8068", "bcb094", "e0d6b8", "fffaea"])
CHITIN = M(["04120a", "0a2a16", "14482a", "24703e", "3a9e58"])
VENOM = M(["1c4a0c", "3a7a1c", "62b02c", "8ee03c", "d4ff9a"], base=3)
PRISM = M(["0a3a42", "16686e", "28a096", "5cd8c8", "c0fff4"], base=3)
CORAL = M(["8a2e44", "c2506a", "e87a8c", "ffa8b0", "ffd8dc"])
SOUL = M(["3a1062", "6a2ca8", "9a5ae0", "c898ff", "f0dcff"], base=3)
EYE_GREEN = M(["0a3a14", "16662a", "2ca043", "6ad46a", "c4ffb0"])
PUPIL = M(["020a04", "06140a", "0c2412", "163a1e", "225030"], base=0)
PEARL = M(["0c4a4a", "157a74", "22b0a0", "5ee0c8", "e8fffa"], base=3)


# ---------------------------------------------------------------- vault keys
def key_mask(bow, shaft_len, teeth):
    """A key lying on the diagonal: bow at the top right, shaft running down-left, teeth on its lower-right side.
    Letters: B bow, G gem, S shaft (lit), D shaft (shade), N collar, T teeth and bit. teeth: (steps down the shaft, length)."""
    cells = {}
    bx, by = 10, 3  # bow centre
    for dy, row in enumerate(bow):
        for dx, ch in enumerate(row):
            if ch != ".":
                cells[(bx - len(row) // 2 + dx, by - len(bow) // 2 + dy)] = ch
    x, y = bx - 1, by + 3
    for k in range(shaft_len):
        cells[(x - k, y + k)] = "S"
        cells[(x - k + 1, y + k)] = "D"
    for dx in range(3):
        cells[(x + dx, y)] = "N"
    for k, length in teeth:
        for j in range(length):
            cells[(x - k + 2 + j, y + k)] = "T"
    cells[(x - shaft_len + 1, y + shaft_len)] = "T"
    return ["".join(cells.get((c, r), ".") for c in range(16)) for r in range(16)]


RING = [".BBBB.", "BB..BB", "B....B", "B....B", "BB..BB", ".BBBB."]
DIAMOND = ["..BB..", ".BBBB.", "BBGGBB", "BBGGBB", ".BBBB.", "..BB.."]
CLOVER = [".BB.BB.", "BBBBBBB", "BBBGBBB", "BBBBBBB", ".BB.BB.", "..BBB.."]
TEETH = ((4, 2), (6, 3), (8, 2))

ITEMS = {}  # id -> (mask, materials, tweak)


def item(name, mask, mats, tweak=None):
    assert len(mask) == 16 and all(len(r) == 16 for r in mask), name
    ITEMS[name] = (mask, mats, tweak)


item("crypt_key", key_mask(RING, 9, TEETH),
     {"B": BRONZE, "G": SOUL, "S": BONE.shifted(base=3), "D": BONE.shifted(base=1), "N": BRONZE.shifted(base=3),
      "T": BONE.shifted(base=2)})
item("hollow_key", key_mask(DIAMOND, 9, TEETH),
     {"B": CHITIN.shifted(base=3), "G": VENOM, "S": CHITIN.shifted(base=3), "D": CHITIN.shifted(base=1),
      "N": VENOM.shifted(base=2), "T": CHITIN.shifted(base=4)})
item("tide_key", key_mask(CLOVER, 9, TEETH),
     {"B": PRISM.shifted(base=2), "G": CORAL.shifted(base=3), "S": PRISM.shifted(base=2), "D": PRISM.shifted(base=1),
      "N": CORAL.shifted(base=2), "T": PRISM.shifted(base=3)})


# ---------------------------------------------------------------- boss tokens
def sphere_tweak(mask, cx, cy, r, bands=5):
    """Shade digits for a lit sphere: light from the upper left."""
    out = []
    for y in range(16):
        row = ""
        for x in range(16):
            if mask[y][x] == ".":
                row += "."
                continue
            nx, ny = (x - cx) / r, (y - cy) / r
            nz = math.sqrt(max(0.0, 1 - nx * nx - ny * ny))
            light = max(0.0, -0.5 * nx - 0.55 * ny + 0.65 * nz)
            row += str(max(0, min(bands - 1, int(light * bands * 0.95))))
        out.append(row)
    return out


# Warden's Sigil: a round bone seal with a purple soul-rune burning in it.
_sigil = [["." if math.hypot(x - 7.5, y - 7.5) > 6.4 else ("E" if math.hypot(x - 7.5, y - 7.5) > 5.2 else "B")
           for x in range(16)] for y in range(16)]
for v, row in enumerate(["...PP...", "..P..P..", "..P..P..", ".P.PP.P.", "..PPPP..", "...PP...", "..P..P..", ".P....P."]):
    for u, ch in enumerate(row):
        if ch == "P":
            _sigil[4 + v][4 + u] = "P"
for (x, y) in ((7, 1), (8, 1), (7, 14), (8, 14), (1, 7), (1, 8), (14, 7), (14, 8)):
    _sigil[y][x] = "N"
item("warden_sigil", ["".join(r) for r in _sigil],
     {"B": BONE.shifted(base=2), "E": BONE.shifted(base=1, texture=gi.flat), "P": SOUL.shifted(base=3, texture=gi.flat),
      "N": SOUL.shifted(base=1, texture=gi.flat)})

# Arachne's Eye: a big compound eye, staggered facets in green, a slit pupil, a highlight.
_eye = [["." for _ in range(16)] for _ in range(16)]
_eye_t = [["." for _ in range(16)] for _ in range(16)]
for y in range(16):
    for x in range(16):
        nx, ny = (x - 7.5) / 6.6, (y - 7.5) / 6.6
        d = math.hypot(nx, ny)
        if d > 1.0:
            continue
        _eye[y][x] = "G"
        facet = ((x + (y % 2)) // 2 + y // 2) % 3
        lit = max(0.0, -0.55 * nx - 0.6 * ny + 0.5 * math.sqrt(max(0.0, 1 - d * d)))
        _eye_t[y][x] = str(max(0, min(4, int(lit * 4.2) + (1 if facet == 0 else 0) - (1 if facet == 2 else 0))))
for y in range(4, 12):
    for x in ((7,) if y in (4, 11) else (7, 8)):
        _eye[y][x] = "K"
        _eye_t[y][x] = "."
for (x, y) in ((5, 4), (4, 5), (5, 5)):
    _eye_t[y][x] = "4"
item("arachne_eye", ["".join(r) for r in _eye],
     {"G": EYE_GREEN.shifted(texture=gi.flat), "K": PUPIL.shifted(texture=gi.flat)}, ["".join(r) for r in _eye_t])

# Tide Pearl: a luminous teal pearl with a soft halo.
_pm = ["".join("P" if math.hypot(x - 7.5, y - 7.5) <= 5.0 else ("H" if math.hypot(x - 7.5, y - 7.5) <= 6.6 else ".")
               for x in range(16)) for y in range(16)]
_pt = [list(r) for r in sphere_tweak([r.replace("H", ".") for r in _pm], 7.5, 7.5, 5.0)]
for y in range(16):
    for x in range(16):
        if _pm[y][x] == "H":
            _pt[y][x] = "1"
for (x, y) in ((5, 5), (6, 5), (5, 6)):
    _pt[y][x] = "4"
item("tide_pearl", _pm, {"P": PEARL.shifted(texture=gi.flat), "H": PRISM.shifted(base=0, texture=gi.flat)},
     ["".join(r) for r in _pt])


# ---------------------------------------------------------------- waystone block textures
STONE = [(0x24, 0x26, 0x2c), (0x3a, 0x3d, 0x46), (0x54, 0x57, 0x62), (0x70, 0x74, 0x80), (0x92, 0x96, 0xa2)]
MOSS = [(0x2a, 0x40, 0x2c), (0x3c, 0x5a, 0x38)]
RUNE_DIM = (0x20, 0x22, 0x28)
GLOW = {"halo": (0x1a, 0x78, 0x8c), "mid": (0x2c, 0xc4, 0xe0), "core": (0x9c, 0xfa, 0xff), "white": (0xe8, 0xff, 0xff)}

RUNE = ["...XX...",
        "..X..X..",
        "...XX...",
        "...XX...",
        ".X.XX.X.",
        ".XXXXXX.",
        "...XX...",
        ".X.XX.X.",
        "...XX..."]  # 8 wide at columns 4..11, rows 3..11; the obelisk body shows columns 3..12, rows 2..12


def noise(x, y, salt=0):
    n = (x * 374761393 + y * 668265263 + salt * 2246822519) & 0xFFFFFFFF
    n = ((n ^ (n >> 13)) * 1274126177) & 0xFFFFFFFF
    return n % 100


def stone_pixel(x, y, salt, light=0):
    s = 2 + light
    n = noise(x, y, salt)
    if n < 14:
        s -= 1
    elif n > 90:
        s += 1
    return STONE[max(0, min(4, s))]


def side_texture(lit):
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 255))
    for y in range(16):
        for x in range(16):
            light = 0
            if x == 3 or y == 2:
                light = 1   # lit edge, upper left
            if x == 12 or y == 12:
                light = -1
            img.putpixel((x, y), stone_pixel(x, y, 1, light) + (255,))
    # Cap band (rows 0-1): a lighter trim.
    for x in range(4, 12):
        img.putpixel((x, 0), STONE[3] + (255,))
        img.putpixel((x, 1), STONE[1] + (255,))
    for x in range(3, 13):
        img.putpixel((x, 12), STONE[1] + (255,))
        img.putpixel((x, 2), STONE[4] + (255,))
    # A few cracks and a patch of moss low on the stone.
    for (x, y) in ((4, 10), (4, 11), (5, 11), (11, 4), (11, 5)):
        img.putpixel((x, y), STONE[1] + (255,))
    for (x, y), c in {(10, 11): 1, (11, 11): 0, (11, 10): 1, (9, 11): 0}.items():
        img.putpixel((x, y), MOSS[c] + (255,))
    cells = {(4 + u, 3 + v) for v, row in enumerate(RUNE) for u, ch in enumerate(row) if ch == "X"}
    if lit:
        for (x, y) in cells:
            for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                n = (x + dx, y + dy)
                if n not in cells and 3 <= n[0] <= 12 and 2 < n[1] < 12:
                    img.putpixel(n, GLOW["halo"] + (255,))
        for (x, y) in cells:
            img.putpixel((x, y), GLOW["mid"] + (255,))
            if x in (7, 8) and y in (4, 5, 6, 7, 9):
                img.putpixel((x, y), GLOW["core"] + (255,))
        for x in (7, 8):
            img.putpixel((x, 3), GLOW["white"] + (255,))
            img.putpixel((x, 0), GLOW["core"] + (255,))
            img.putpixel((x, 1), GLOW["mid"] + (255,))
    else:
        for (x, y) in cells:
            img.putpixel((x, y), RUNE_DIM + (255,))
        for (x, y) in cells:   # a faint lip under each cut, so the carving reads
            n = (x + 1, y + 1)
            if n not in cells and n[0] <= 12 and n[1] < 12:
                img.putpixel(n, STONE[3] + (255,))
    return img


def top_texture():
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 255))
    for y in range(16):
        for x in range(16):
            edge = min(x, y, 15 - x, 15 - y)
            img.putpixel((x, y), stone_pixel(x, y, 2, 1 if edge in (4, 5) and x + y < 16 else 0) + (255,))
    for i in range(4, 12):  # the cap's rim (it spans 4..12), lit on the upper left
        for (x, y) in ((i, 4), (4, i)):
            img.putpixel((x, y), STONE[4] + (255,))
        for (x, y) in ((i, 11), (11, i)):
            img.putpixel((x, y), STONE[1] + (255,))
    for (x, y) in ((7, 7), (8, 7), (7, 8), (8, 8)):
        img.putpixel((x, y), STONE[1] + (255,))
    img.putpixel((7, 7), STONE[0] + (255,))
    return img


def base_texture():
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 255))
    for y in range(16):
        for x in range(16):
            col = (x + ((y // 4) % 2) * 4) % 8
            seam = y % 4 == 3 or col == 7
            light = -1 if seam else (1 if y % 4 == 0 else 0)
            img.putpixel((x, y), stone_pixel(x, y, 3, light) + (255,))
    for (x, y), c in {(2, 1): 1, (3, 1): 0, (3, 2): 1, (12, 5): 0, (13, 5): 1}.items():
        img.putpixel((x, y), MOSS[c] + (255,))
    return img


# ---------------------------------------------------------------- models
def waystone_model(lit):
    side = "skyrpg_world:block/waystone_side_lit" if lit else "skyrpg_world:block/waystone_side"

    def faces(up, sides, down):
        return {"north": {"texture": sides}, "east": {"texture": sides}, "south": {"texture": sides},
                "west": {"texture": sides}, "up": {"texture": up}, "down": {"texture": down}}
    return {
        "textures": {"particle": "#side", "side": side, "top": "skyrpg_world:block/waystone_top",
                     "base": "skyrpg_world:block/waystone_base"},
        "elements": [
            {"name": "base", "from": [1, 0, 1], "to": [15, 3, 15], "faces": faces("#base", "#base", "#base")},
            {"name": "obelisk", "from": [3, 3, 3], "to": [13, 14, 13], "faces": faces("#top", "#side", "#base")},
            {"name": "cap", "from": [4, 14, 4], "to": [12, 16, 12], "faces": faces("#top", "#side", "#base")},
        ],
    }


# id: (name, rarity, type, description)
NAMES = {
    "crypt_key": ("Crypt Key", "rare", "Dungeon Key", "Opens the vault in a Forgotten Crypt. Used up when you open it."),
    "hollow_key": ("Hollow Key", "rare", "Dungeon Key", "Opens the vault in a Spider Hollow. Used up when you open it."),
    "tide_key": ("Tide Key", "rare", "Dungeon Key", "Opens the vault in a Drowned Temple. Used up when you open it."),
    "warden_sigil": ("Warden's Sigil", "epic", "Boss Trophy", "A bone seal still warm with stolen souls. Dropped by the Crypt Warden."),
    "arachne_eye": ("Arachne's Eye", "epic", "Boss Trophy", "A thousand facets, and every one of them is watching you. Dropped by Queen Arachne."),
    "tide_pearl": ("Tide Pearl", "epic", "Boss Trophy", "It glows with the light of a deeper sea. Dropped by the Tidemother."),
    "waystone": ("Waystone", "rare", "Block", "Right-click to attune, then travel between the waystones you have found."),
}


# ---------------------------------------------------------------- danger regions: blocks
def _rgb(h):
    return tuple(int(h[i:i + 2], 16) for i in (0, 2, 4))


def _ramp(*hexes):
    return [_rgb(h) for h in hexes]


def _pick(ramp, v):
    return ramp[max(0, min(len(ramp) - 1, v))]


def _canvas(bg=(0, 0, 0, 255)):
    return Image.new("RGBA", (16, 16), bg)


def _put(img, x, y, rgb, a=255):
    if 0 <= x < 16 and 0 <= y < 16:
        img.putpixel((x, y), tuple(rgb) + (a,))


def _speckled(img, ramp, salt, base=2, lo=18, hi=84):
    """Fill with a noisy ramp (two noise octaves so it clumps a little rather than reading as static)."""
    for y in range(16):
        for x in range(16):
            n = noise(x, y, salt)
            clump = noise(x // 2, y // 2, salt + 50)
            v = base
            if n < lo:
                v -= 1
            elif n > hi:
                v += 1
            if clump < 22:
                v -= 1
            elif clump > 80:
                v += 1
            _put(img, x, y, _pick(ramp, v))


BLIGHT = _ramp("1a1620", "2a2430", "3c3446", "524860", "6c6078")
MAGENTA = _ramp("5a1250", "962088", "d03cc0", "f47cea")
DIRT = _ramp("2e1e12", "432c1a", "5a3b22", "73502e", "8a6640")


def blightgrass_top():
    img = _canvas()
    _speckled(img, BLIGHT, 11, base=2)
    # Withered blades: short dark streaks with a pale dead tip.
    for i in range(22):
        x, y = noise(i, 3, 7) % 16, noise(i, 5, 7) % 16
        _put(img, x, y, _pick(BLIGHT, 0))
        _put(img, x, (y + 1) % 16, _pick(BLIGHT, 1))
        _put(img, (x + 1) % 16, (y - 1) % 16, _pick(BLIGHT, 4))
    # Sickly magenta veins: wandering lines that wrap around the tile edge (so they tile).
    for v in range(3):
        x, y = noise(v, 1, 21) % 16, noise(v, 2, 21) % 16
        for step in range(7):
            _put(img, x, y, _pick(MAGENTA, 3 if step == 3 else (1 if step % 3 else 2)))
            x = (x + (1 if noise(v, step, 23) % 3 else -1)) % 16
            y = (y + (1 if step % 2 else 0)) % 16
    return img


def blightgrass_side():
    img = _canvas()
    _speckled(img, DIRT, 12, base=2)
    for i in range(7):  # pebbles
        x, y = 1 + noise(i, 1, 31) % 14, 6 + noise(i, 2, 31) % 9
        _put(img, x, y, _pick(DIRT, 4))
        _put(img, x + 1, y, _pick(DIRT, 3))
        _put(img, x, y + 1, _pick(DIRT, 0))
    for x in range(16):  # ragged overhang
        h = 4 + noise(x, 0, 41) % 3
        if noise(x, 1, 42) > 75:
            h += 1
        for y in range(h):
            n = noise(x, y, 43)
            _put(img, x, y, _pick(BLIGHT, 3 + (1 if n > 80 else 0) - (1 if n < 20 else 0) - (1 if y == h - 1 else 0)))
        _put(img, x, h, _pick(DIRT, 0))  # shadow under the lip
        if noise(x, 2, 44) > 80 and h > 3:
            _put(img, x, 1, _pick(MAGENTA, 1))
    for x in (2, 9, 13):  # dangling roots
        h = 4 + noise(x, 0, 41) % 3
        _put(img, x, h + 1, _pick(BLIGHT, 1))
    return img


ASH = _ramp("0c0c0e", "18181c", "26262c", "383840", "4c4c56")
EMBER = _ramp("6a1e08", "c0400c", "f27816", "ffc04a")


def ashen_soil():
    img = _canvas()
    _speckled(img, ASH, 61, base=2, lo=22, hi=80)
    for i in range(5):  # pale ash flakes
        x, y = noise(i, 1, 62) % 15, noise(i, 2, 62) % 16
        _put(img, x, y, _pick(ASH, 4))
        _put(img, x + 1, y, _pick(ASH, 3))
    for i in range(5):  # cracks
        x, y = noise(i, 3, 63) % 13, noise(i, 4, 63) % 14
        for k in range(3):
            _put(img, x + k, y + (k // 2), _pick(ASH, 0))
    for i, (x, y) in enumerate(((3, 4), (11, 2), (7, 9), (13, 12), (2, 13), (9, 14))):  # ember specks, warm halo
        for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            _put(img, x + dx, y + dy, _pick(EMBER, 0))
        _put(img, x, y, _pick(EMBER, 2 + (i % 2)))
        if i % 3 == 0:
            _put(img, x + 1, y + 1, _pick(EMBER, 1))
    return img


ICE = _ramp("5a8ab4", "88b8dc", "b0dcf0", "d4f0fc", "f4fcff")
FROZEN = _ramp("0c1e3a", "163258", "24487a")


def cursed_ice():
    img = _canvas()
    for y in range(16):
        for x in range(16):
            v = 2 + (1 if (x + y) % 9 in (0, 1) else 0) - (1 if (x - y) % 11 == 0 else 0)
            n = noise(x, y, 71)
            v += 1 if n > 88 else (-1 if n < 10 else 0)
            _put(img, x, y, _pick(ICE, v))
    # Dark shapes frozen inside: a hunched silhouette and a reaching hand, each with a faded halo.
    shapes = ["..XX....",
              ".XXXX...",
              ".XXXXX..",
              "..XXXXX.",
              "...XXXX.",
              "..X..XX."]
    cells = {(3 + u, 3 + v) for v, row in enumerate(shapes) for u, ch in enumerate(row) if ch == "X"}
    cells |= {(12, 11), (12, 12), (13, 12), (11, 12), (12, 13), (13, 10)}
    for (x, y) in cells:
        for dx in (-1, 0, 1):
            for dy in (-1, 0, 1):
                if (x + dx, y + dy) not in cells:
                    _put(img, x + dx, y + dy, _pick(ICE, 0))
    for (x, y) in cells:
        _put(img, x, y, _pick(FROZEN, 1 if (x + y) % 3 else 0))
    for (x, y) in ((4, 3), (5, 4), (12, 11)):
        _put(img, x, y, _pick(FROZEN, 2))
    for (x, y) in ((0, 1), (1, 0), (14, 4), (15, 3), (2, 14), (3, 15)):  # frost glints
        _put(img, x, y, _pick(ICE, 4))
    return img


MUD = _ramp("0c1608", "1a2410", "2a3416", "3e4a20", "56622e")
BUBBLE = _ramp("0e1a0a", "6a8a30", "9ab848", "d2f080")


def mire_mud():
    img = _canvas()
    _speckled(img, MUD, 81, base=2, lo=22, hi=82)
    for y in range(16):  # slow sludge swirls
        for x in range(16):
            k = (x + 2 * y + (y // 4) * 3) % 13
            if k == 0:
                _put(img, x, y, _pick(MUD, 1))
            elif k == 1 and noise(x, y, 82) > 40:
                _put(img, x, y, _pick(MUD, 4))
    for (cx, cy, r) in ((4, 4, 2), (11, 8, 3), (6, 12, 2), (13, 2, 1)):
        for dy in range(-r, r + 1):
            for dx in range(-r, r + 1):
                d = math.hypot(dx, dy)
                if r - 0.6 <= d <= r + 0.5:
                    _put(img, cx + dx, cy + dy, _pick(BUBBLE, 1 if dx + dy > 0 else 2))
                elif d < r - 0.6:
                    _put(img, cx + dx, cy + dy, _pick(MUD, 0))
        _put(img, cx - max(1, r - 1), cy - max(1, r - 1), _pick(BUBBLE, 3))
    return img


CRYSTAL = _ramp("2a0420", "5c0a3c", "98154e", "d42a68", "ff6a9a", "ffc4dc")


def blight_crystal():
    """A cluster of faceted shards growing up from the bottom of the tile (cross model: transparent background)."""
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    # (centre x, base y, half-width, tip y): hexagonal columns with pointed tips, small ones painted first.
    shards = [(1, 15, 1, 8), (14, 15, 1, 7), (11, 15, 2, 4), (4, 15, 2, 6), (7, 15, 2, 1)]
    px = img.load()
    for (cx, base, hw, tip) in shards:
        cells = set()
        for y in range(tip, base + 1):
            t = y - tip
            w = min(hw, (t + 1) // 2)
            for x in range(cx - w, cx + w + 1):
                cells.add((x, y))
                rel = (x - cx) / (hw + 0.5)
                v = 3 - int(round(rel * 1.6))  # left facets bright, right facets dark
                if x == cx - w and w == hw:
                    v = 5 if t < base - tip - 3 else 4
                if x == cx + w and w > 0:
                    v = min(v, 1)
                if t == 0:
                    v = 5
                if y > base - 3:
                    v -= 1
                _put(img, x, y, _pick(CRYSTAL, v))
        for (x, y) in cells:  # outline this shard over whatever is behind it (sides and tip, not the bottom edge)
            for dx, dy in ((1, 0), (-1, 0), (0, -1)):
                n = (x + dx, y + dy)
                if n not in cells and 0 <= n[0] < 16 and 0 <= n[1] < 16:
                    px[n] = CRYSTAL[0] + (255,)
    solid = {(x, y) for y in range(16) for x in range(16) if px[x, y][3]}
    for (x, y) in ((7, 5), (6, 8), (11, 9), (4, 11), (8, 11)):  # hot glowing cores
        if (x, y) in solid:
            _put(img, x, y, _pick(CRYSTAL, 5))
    return img


# ---------------------------------------------------------------- danger regions: items
def _shape_icon(pred, light, ramp, outline, glow=None):
    """Icon from a coverage predicate and a light function (0..1); a dark outline around it, optional soft glow."""
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    solid = {(x, y) for y in range(16) for x in range(16) if pred(x, y)}
    if glow:
        for (x, y) in list(solid):
            for dx in (-1, 0, 1):
                for dy in (-1, 0, 1):
                    n = (x + dx, y + dy)
                    if n not in solid:
                        _put(img, n[0], n[1], glow, 90)
    for (x, y) in solid:
        _put(img, x, y, _pick(ramp, int(light(x, y) * len(ramp) * 0.97)))
    for (x, y) in solid:
        for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            n = (x + dx, y + dy)
            if n not in solid:
                _put(img, n[0], n[1], outline)
    return img, solid


def _sphere_light(cx, cy, r):
    def f(x, y):
        nx, ny = (x - cx) / r, (y - cy) / r
        nz = math.sqrt(max(0.0, 1 - nx * nx - ny * ny))
        return max(0.0, min(0.999, -0.5 * nx - 0.55 * ny + 0.6 * nz + 0.1))
    return f


def blight_essence():
    """A tiny stoppered vial with a crimson-purple wisp swirling inside."""
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    GL = _ramp("1a0818", "3a1048", "6a2090", "a038c8", "e070f0", "ffc4ff")
    OUT = _rgb("120414")
    cx, cy, r = 7.5, 9.5, 5.0
    neck = lambda x, y: x in (6, 7, 8, 9) and 3 <= y <= 4
    solid = {(x, y) for y in range(16) for x in range(16)
             if math.hypot(x - cx, y - cy) <= r or neck(x, y)}
    for (x, y) in solid:
        for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            n = (x + dx, y + dy)
            if n not in solid:
                _put(img, n[0], n[1], OUT)
    light = _sphere_light(cx, cy, r)
    for (x, y) in solid:
        if neck(x, y):
            _put(img, x, y, (0xb8, 0xa8, 0xd0) if x < 8 else (0x80, 0x70, 0xa0))
            continue
        a = math.atan2(y - cy, x - cx) + math.hypot(x - cx, y - cy) * 0.9  # spiral wisp
        wisp = (math.sin(a * 1.5) + 1) / 2
        v = int(2 + wisp * 2.2 - (1 - light(x, y)) * 1.6)
        _put(img, x, y, _pick(GL, v))
    for (x, y) in solid:  # glass rim on the lit side
        if not neck(x, y) and ((x - 1, y) not in solid or (x, y - 1) not in solid):
            _put(img, x, y, (0xd8, 0xc4, 0xf0) if x < 8 else _pick(GL, 2))
    for (x, y) in ((4, 9), (4, 10), (5, 8)):
        _put(img, x, y, (0xf4, 0xe8, 0xff))
    for x in (6, 7, 8, 9):  # cork
        _put(img, x, 2, (0x7a, 0x52, 0x32) if x < 8 else (0x56, 0x38, 0x20))
        _put(img, x, 1, OUT)
    _put(img, 5, 2, OUT)
    _put(img, 10, 2, OUT)
    return img


def cinder_essence():
    """A glowing ember-orange orb of compressed ash."""
    AS = _ramp("2a0a04", "6a1c08", "b83c0c", "f07a18", "ffc24a", "fff0b0")
    cx = cy = 7.5
    img, solid = _shape_icon(lambda x, y: math.hypot(x - cx, y - cy) <= 5.4, _sphere_light(cx, cy, 5.4), AS,
                             _rgb("1a0602"), glow=(0xe0, 0x50, 0x10))
    for (x, y) in solid:  # grey ash crust broken by glowing cracks
        if noise(x, y, 91) < 24 and math.hypot(x - cx, y - cy) > 2.5:
            _put(img, x, y, _rgb("3a3236") if x + y > 14 else _rgb("5a5256"))
    for (x, y) in ((7, 7), (8, 7), (7, 8), (8, 8), (9, 9), (6, 9)):
        _put(img, x, y, _pick(AS, 5 if (x, y) == (7, 7) else 4))
    for (x, y) in ((5, 5), (6, 4)):
        _put(img, x, y, _pick(AS, 5))
    return img


def frost_essence():
    """A pale icy-blue crystal shard, tilted, with a bright ridge between its facets."""
    FR = _ramp("0c2848", "1c5a8c", "3a8cc0", "74c0e8", "bce8fc", "f4ffff")

    def axes(x, y):
        return (x - 7.5) * 0.72 + (7.5 - y) * 0.69, -(x - 7.5) * 0.69 + (7.5 - y) * 0.72

    def inside(x, y):
        u, w = axes(x, y)
        half = 3.0 if abs(u) < 3.5 else max(0.0, 3.0 - (abs(u) - 3.5) * 1.4)
        return abs(u) <= 7.0 and abs(w) <= half

    def light(x, y):
        u, w = axes(x, y)
        return max(0.0, min(0.999, 0.5 - w * 0.17 + u * 0.03 + (0.12 if noise(x, y, 95) > 85 else 0)))
    img, solid = _shape_icon(inside, light, FR, _rgb("06182e"), glow=(0x70, 0xc0, 0xff))
    for (x, y) in solid:
        if -0.4 < axes(x, y)[1] < 0.7:
            _put(img, x, y, _pick(FR, 4))
    for (x, y) in ((6, 8), (7, 6), (9, 4)):
        if (x, y) in solid:
            _put(img, x, y, _pick(FR, 5))
    return img


def mire_essence():
    """A toxic green droplet with a glossy highlight."""
    TX = _ramp("0a2a08", "14561a", "2c8c26", "58c43a", "94ee54", "dcffa8")

    def inside(x, y):
        if y < 6:
            return y >= 2 and abs(x - 7.5) <= (y - 1.2) * 0.85
        return math.hypot(x - 7.5, y - 9.6) <= 5.0

    def light(x, y):
        return max(0.0, min(0.999, 0.55 - (x - 7.5) * 0.07 + (9.6 - y) * 0.05 - max(0.0, (y - 11) * 0.12)))
    img, solid = _shape_icon(inside, light, TX, _rgb("051404"), glow=(0x50, 0xd0, 0x30))
    for (x, y) in solid:  # murkier lower right
        if (x - 7.5) + (y - 9.6) > 4.2:
            _put(img, x, y, _pick(TX, 1))
    for (x, y) in ((5, 8), (5, 9), (6, 7)):
        _put(img, x, y, _pick(TX, 5))
    _put(img, 9, 12, _pick(TX, 4))
    return img


def dread_crystal():
    """A large dark-red faceted crystal, black core, hot glow."""
    DR = _ramp("1a0206", "3e0610", "6e0c1c", "a41830", "d8344c", "ff7a8a")

    def inside(x, y):
        dy = (y - 7.5) / 7.4
        return abs(x - 7.5) <= 5.6 * (1 - abs(dy) ** 1.8) + 0.4

    def light(x, y):  # four facets: upper-left bright, upper-right and lower-left mid, lower-right dark
        left, up = x < 7.5, y < 7.5
        return (0.62 if (left and up) else 0.45 if (up or left) else 0.22) + (0.1 if noise(x, y, 97) > 88 else 0)
    img, solid = _shape_icon(inside, light, DR, _rgb("0a0004"), glow=(0xc0, 0x10, 0x30))
    for (x, y) in solid:
        if x in (7, 8) and 2 <= y <= 13:
            _put(img, x, y, _pick(DR, 2 if y > 7 else 3))
        if y in (7, 8):
            _put(img, x, y, _pick(DR, 1))
    for (x, y) in ((6, 6), (7, 6), (8, 6), (6, 7), (7, 7), (8, 7), (9, 7), (6, 8), (7, 8), (8, 8), (9, 8), (7, 9), (8, 9)):
        if (x, y) in solid:  # black core
            _put(img, x, y, _rgb("040002"))
    _put(img, 7, 7, _pick(DR, 3))  # a pulse of heat inside it
    _put(img, 8, 8, _rgb("3a0410"))
    for (x, y) in ((5, 3), (4, 5), (5, 4)):
        _put(img, x, y, _pick(DR, 5))
    return img


REGION_BLOCKS = {
    "blightgrass_top": blightgrass_top, "blightgrass_side": blightgrass_side, "blight_crystal": blight_crystal,
    "ashen_soil": ashen_soil, "cursed_ice": cursed_ice, "mire_mud": mire_mud,
}
REGION_ITEMS = {
    "blight_essence": blight_essence, "cinder_essence": cinder_essence, "frost_essence": frost_essence,
    "mire_essence": mire_essence, "dread_crystal": dread_crystal,
}
REGION_ITEM_INFO = {
    "blight_essence": ("Blight Essence", "uncommon", "Region Material",
                       "Dropped by monsters in the Blightlands. Used for Blightforged gear."),
    "cinder_essence": ("Cinder Essence", "uncommon", "Region Material",
                       "Dropped by monsters in the Ashen Wastes. Used for Cinderforged gear."),
    "frost_essence": ("Frost Essence", "uncommon", "Region Material",
                      "Dropped by monsters in Frostgrave. Used for Frostbound gear."),
    "mire_essence": ("Mire Essence", "uncommon", "Region Material",
                     "Dropped by monsters in the Mire. Used for Mirebound gear."),
    "dread_crystal": ("Dread Crystal", "epic", "Rare Region Drop",
                      "A rare drop from monsters in any danger region. It hums with dread."),
}
REGION_BLOCK_NAMES = {"blightgrass": "Blightgrass", "blight_crystal": "Blight Crystal", "ashen_soil": "Ashen Soil",
                      "cursed_ice": "Cursed Ice", "mire_mud": "Mire Mud"}
REGION_NAMES = {"blightlands": "The Blightlands", "ashen_wastes": "The Ashen Wastes", "frostgrave": "Frostgrave",
                "mire": "The Mire"}


def build_regions(lang):
    tex = ASSETS / "textures"
    out = {}
    for name, fn in {**REGION_BLOCKS, **REGION_ITEMS}.items():
        img = fn()
        assert img.size == (16, 16), name
        path = tex / ("item" if name in REGION_ITEMS else "block") / f"{name}.png"
        path.parent.mkdir(parents=True, exist_ok=True)
        img.save(path)
        out[name] = img
    S = "skyrpg_world:"

    def face(texture, cull):
        return {"uv": [0, 0, 16, 16], "texture": texture, "cullface": cull}
    gi.write_json(ASSETS / "models" / "block" / "blightgrass.json", {
        "parent": "minecraft:block/block",
        "textures": {"particle": "minecraft:block/dirt", "bottom": "minecraft:block/dirt",
                     "top": S + "block/blightgrass_top", "side": S + "block/blightgrass_side"},
        "elements": [{"from": [0, 0, 0], "to": [16, 16, 16], "faces": {
            "down": face("#bottom", "down"), "up": face("#top", "up"), "north": face("#side", "north"),
            "south": face("#side", "south"), "west": face("#side", "west"), "east": face("#side", "east")}}]})
    gi.write_json(ASSETS / "models" / "block" / "blight_crystal.json", {
        "parent": "minecraft:block/cross", "textures": {"cross": S + "block/blight_crystal"}})
    for name in ("ashen_soil", "cursed_ice", "mire_mud"):
        gi.write_json(ASSETS / "models" / "block" / f"{name}.json",
                      {"parent": "minecraft:block/cube_all", "textures": {"all": S + f"block/{name}"}})
    for name in REGION_BLOCK_NAMES:
        gi.write_json(ASSETS / "blockstates" / f"{name}.json", {"variants": {"": {"model": S + f"block/{name}"}}})
        if name == "blight_crystal":  # a flat sprite icon, like vanilla's amethyst cluster item
            gi.write_json(ASSETS / "models" / "item" / f"{name}.json",
                          {"parent": "minecraft:item/generated", "textures": {"layer0": S + "block/blight_crystal"}})
            gi.write_json(ASSETS / "items" / f"{name}.json", {"model": {"type": "minecraft:model", "model": S + f"item/{name}"}})
        else:
            gi.write_json(ASSETS / "items" / f"{name}.json", {"model": {"type": "minecraft:model", "model": S + f"block/{name}"}})
        lang[f"block.skyrpg_world.{name}"] = REGION_BLOCK_NAMES[name]
    for name, (title, rarity, kind, description) in REGION_ITEM_INFO.items():
        gi.write_json(ASSETS / "models" / "item" / f"{name}.json",
                      {"parent": "minecraft:item/generated", "textures": {"layer0": S + f"item/{name}"}})
        gi.write_json(ASSETS / "items" / f"{name}.json", {"model": {"type": "minecraft:model", "model": S + f"item/{name}"}})
        gi.write_json(DATA / f"{name}.json", {"name": title, "rarity": rarity, "type": kind, "description": description})
        lang[f"item.skyrpg_world.{name}"] = title
    for key, title in REGION_NAMES.items():
        lang[f"region.skyrpg_world.{key}"] = title
    return out


# ---------------------------------------------------------------- the deep: layer blocks
GSTONE = _ramp("34343c", "4a4a54", "60606c", "787884", "92929e")
GLINT = _ramp("5a3e96", "8a6ad0", "b89cff", "e4d8ff", "ffffff")
GCRYSTAL = _ramp("24163e", "432a78", "7052b8", "a084e8", "cebcff", "f6f0ff")
TEAL = _ramp("0a3432", "11564f", "1a7f72", "2aa894", "4fd8c0", "aefff0")
SLATE_DARK = _ramp("16161c", "202028", "2a2a34", "363642", "444452")
FORGE = {"halo": (0x5a, 0x2a, 0x08), "mid": (0xd8, 0x80, 0x1c), "core": (0xff, 0xc8, 0x50), "white": (0xff, 0xf2, 0xc4)}


def _wput(img, x, y, rgb):
    """Put that wraps around the tile edge, for textures that must tile."""
    img.putpixel((x % 16, y % 16), tuple(rgb) + (255,))


def glimmerstone():
    """Grey cave stone shot through with lavender glints of Glimmer (they wrap, so the texture tiles)."""
    img = _canvas()
    _speckled(img, GSTONE, 101, base=2, lo=20, hi=82)
    for i in range(3):  # faint strata
        y0 = 2 + noise(i, 1, 102) % 12
        for x in range(16):
            if noise(x, i, 104) > 30:
                _wput(img, x, y0 + (x // 5 + i) % 2, _pick(GSTONE, 1))
    # (x, y, size): 0 a lone glint, 1 a two-pixel streak, 2 a star with a dim halo
    for (x, y, size) in ((3, 2, 2), (11, 1, 0), (14, 7, 1), (7, 7, 0), (1, 10, 1), (9, 12, 2), (14, 14, 0), (5, 14, 0), (12, 4, 0)):
        if size == 2:
            for dx, dy in ((1, 1), (-1, 1), (1, -1), (-1, -1)):
                _wput(img, x + dx, y + dy, _pick(GLINT, 0))
            for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                _wput(img, x + dx, y + dy, _pick(GLINT, 2))
            _wput(img, x, y, _pick(GLINT, 4))
        elif size == 1:
            _wput(img, x, y, _pick(GLINT, 4))
            _wput(img, x + 1, y, _pick(GLINT, 2))
            _wput(img, x, y + 1, _pick(GLINT, 1))
        else:
            _wput(img, x, y, _pick(GLINT, 3))
            _wput(img, x + 1, y + 1, _pick(GLINT, 0))
    return img


def _shard_cluster(shards, ramp, cores):
    """Faceted shards growing up from the bottom of a transparent tile (the blight crystal's look, any colour)."""
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    px = img.load()
    for (cx, base, hw, tip) in shards:
        cells = set()
        for y in range(tip, base + 1):
            t = y - tip
            w = min(hw, (t + 1) // 2)
            for x in range(cx - w, cx + w + 1):
                cells.add((x, y))
                v = 3 - int(round((x - cx) / (hw + 0.5) * 1.6))
                if x == cx - w and w == hw:
                    v = 5 if t < base - tip - 3 else 4
                if x == cx + w and w > 0:
                    v = min(v, 1)
                if t == 0:
                    v = 5
                if y > base - 3:
                    v -= 1
                _put(img, x, y, _pick(ramp, v))
        for (x, y) in cells:
            for dx, dy in ((1, 0), (-1, 0), (0, -1)):
                n = (x + dx, y + dy)
                if n not in cells and 0 <= n[0] < 16 and 0 <= n[1] < 16:
                    px[n] = ramp[0] + (255,)
    solid = {(x, y) for y in range(16) for x in range(16) if px[x, y][3]}
    for (x, y) in cores:
        if (x, y) in solid:
            _put(img, x, y, _pick(ramp, 5))
    return img


def glimmer_cluster():
    """Lavender Glimmer crystals, a crowd of thin shards (cross model, rotated to face out of any surface)."""
    return _shard_cluster([(2, 15, 1, 9), (13, 15, 1, 5), (10, 15, 2, 2), (5, 15, 2, 4), (8, 15, 1, 7)], GCRYSTAL,
                          ((10, 4), (5, 6), (9, 8), (13, 8), (2, 11), (6, 10)))


CAP = M(["0a3432", "11564f", "1a7f72", "2aa894", "4fd8c0"], base=3)
CAP_SPOT = M(["7ae8d4", "a8fff0", "d0fff8", "f0fffc", "ffffff"], base=2, texture=gi.flat)
GILLS = M(["041a18", "082c28", "0e403a", "16564e", "1e6e62"], base=2)
STALK = M(["5a6a62", "86988c", "b0c2b4", "d6e4d8", "eef8f0"], base=2)
PUFF = M(["5a5e3e", "8a8e62", "b6ba8a", "dadeb2", "f4f6dc"], base=2)
PUFF_DARK = M(["2a2c1a", "3e4026", "565a36", "6e7246", "868a58"], base=1)
SPORE = M(["6a7a1c", "9aae2c", "c8dc48", "e6f47a", "faffc0"], base=3, texture=gi.flat)


def glowcap():
    """A pair of small mushrooms with glowing teal caps (cross model)."""
    mask = ["................",
            "................",
            "....CCCCCC......",
            "...CCSCCCCC.....",
            "..CCCCCCSCCC....",
            "..CSCCCCCCCC....",
            "..CCCCCCCCSC....",
            "..GGGGGGGGGG....",
            "......TT........",
            "......TT....cc..",
            "......TT...cSccc",
            "......TT...gggg.",
            "......TT....TT..",
            ".....TTTT...TT..",
            ".....TTTT...TT..",
            "....TTTTTT.TTTT."]
    mats = {"C": CAP, "c": CAP.shifted(base=4), "S": CAP_SPOT, "G": GILLS, "g": GILLS, "T": STALK}
    return gi.render(mask, mats, outline=False)


def glowcap_block():
    """The cap of a giant glowcap: teal flesh with glowing pale spots (cube)."""
    img = _canvas()
    _speckled(img, TEAL, 121, base=2, lo=20, hi=84)
    for (cx, cy, r) in ((3.5, 3.5, 1.7), (11.5, 5.5, 1.2), (7.5, 11.0, 1.9), (13.5, 13.0, 1.0), (1.5, 12.5, 1.0), (10.0, 0.5, 0.8)):
        for y in range(int(cy - r - 1), int(cy + r + 2)):
            for x in range(int(cx - r - 1), int(cx + r + 2)):
                d = math.hypot(x - cx, y - cy)
                if d <= r - 0.9:
                    _wput(img, x, y, (0xe8, 0xff, 0xfa) if d < r * 0.4 else (0xb4, 0xff, 0xf0))
                elif d <= r:
                    _wput(img, x, y, (0x7a, 0xe8, 0xd4))
                elif d <= r + 0.7:
                    _wput(img, x, y, _pick(TEAL, 5))
    return img


def spore_puffball():
    """A fat pale puffball, speckled, with a pore on top (cross model)."""
    mask = ["................",
            "................",
            "................",
            "................",
            "................",
            "......PPPP......",
            "....PPPOOPPP....",
            "...PPPPPPPPPP...",
            "..PPPPPPPPPPPP..",
            "..PPPPPPPPPPPP..",
            "..PPPPPPPPPPPP..",
            "..PPPPPPPPPPPP..",
            "...PPPPPPPPPP...",
            "...PPPPPPPPPP...",
            "....BBBBBBBB....",
            ".....BBBBBB....."]
    tweak = [list(r) for r in sphere_tweak([r.replace("B", ".").replace("O", "P") for r in mask], 7.5, 9.5, 5.6)]
    for y in range(16):
        for x in range(16):
            if mask[y][x] == "P" and noise(x, y, 131) > 80:
                tweak[y][x] = "0" if noise(x, y, 132) > 40 else "4"  # dark speckles and pale warts
    mats = {"P": PUFF, "O": PUFF_DARK.shifted(base=0), "B": PUFF_DARK}
    return gi.render(mask, mats, ["".join(r) for r in tweak], outline=False)


def spore_puffball_burst():
    """The spent puffball: a torn, flat, hollow shell with the last spores drifting off (cross model)."""
    mask = ["................",
            "................",
            "................",
            "................",
            "................",
            "....S......S....",
            "................",
            "........S.......",
            "..S.............",
            ".............S..",
            "......S.........",
            "...P.P..P..P.P..",
            "..PPPPPPPPPPPP..",
            "..PDDDDDDDDDDP..",
            "...PPPPPPPPPP...",
            "....BBBBBBBB...."]
    mats = {"P": PUFF.shifted(base=1), "D": PUFF_DARK.shifted(base=0), "B": PUFF_DARK, "S": SPORE}
    return gi.render(mask, mats, outline=False)


DWARF_RUNE = ["....X....",
              "...XXX...",
              "..X.X.X..",
              "....X....",
              ".X..X..X.",
              "..X.X.X..",
              "...XXX...",
              "....X....",
              "...X.X..."]


def dwarven_runestone():
    """Dark deepslate bricks with a dwarven rune cut through them that still glows forge-orange (cube)."""
    img = _canvas()
    for y in range(16):
        for x in range(16):
            col = (x + ((y // 4) % 2) * 4) % 8
            seam = y % 4 == 3 or col == 7
            v = 2 + (-2 if seam else (1 if y % 4 == 0 else 0))
            n = noise(x, y, 141)
            v += -1 if n < 14 else (1 if n > 90 and not seam else 0)
            _put(img, x, y, _pick(SLATE_DARK, v))
    cells = {(3 + u, 3 + v) for v, row in enumerate(DWARF_RUNE) for u, ch in enumerate(row) if ch == "X"}
    for (x, y) in cells:  # the cut: dark below and right of each stroke, so it reads as carved
        for n in ((x + 1, y), (x, y + 1)):
            if n not in cells:
                _put(img, n[0], n[1], FORGE["halo"])
    for (x, y) in cells:
        _put(img, x, y, FORGE["core"] if x == 7 else FORGE["mid"])
    for (x, y) in ((7, 3), (7, 8)):
        _put(img, x, y, FORGE["white"])
    return img


# ---------------------------------------------------------------- the deep: drops, key and trophy
def glimmer_geode():
    """A rough grey geode cracked open on the front, lavender Glimmer crystals glowing inside."""
    ROCK = _ramp("221e20", "3a3434", "564e4a", "746a62", "958a7e")
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    cx, cy, r = 7.5, 8.0, 6.3
    light = _sphere_light(cx, cy, r)
    solid = {(x, y) for y in range(16) for x in range(16) if math.hypot((x - cx) * 1.0, (y - cy) * 1.08) <= r}
    for (x, y) in solid:
        v = int(light(x, y) * 4.9)
        if noise(x, y, 151) > 84:
            v -= 1
        _put(img, x, y, _pick(ROCK, v))
    ox, oy, rx, ry = 8.6, 9.0, 3.7, 3.2  # the opening
    for (x, y) in solid:
        d = math.hypot((x - ox) / rx, (y - oy) / ry)
        if d <= 1.0:
            a = math.atan2(y - oy, x - ox)
            facet = int((a + math.pi) / (math.pi / 3)) % 3
            v = 2 + facet - (1 if d > 0.75 else 0) + (1 if d < 0.35 else 0)
            _put(img, x, y, _pick(GCRYSTAL, v))
        elif d <= 1.3:
            _put(img, x, y, (0xc8, 0xbe, 0xb0) if (x - ox) + (y - oy) < 0 else (0x8a, 0x80, 0x74))  # the cut rim
    for (x, y) in solid:
        for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            n = (x + dx, y + dy)
            if n not in solid:
                _put(img, n[0], n[1], _rgb("100c0e"))
    for (x, y) in ((8, 8), (9, 9), (7, 10)):
        _put(img, x, y, _pick(GCRYSTAL, 5))
    for (x, y) in ((4, 4), (5, 3)):
        _put(img, x, y, _pick(ROCK, 4))
    return img


def glowcap_spores():
    """A little drift of glowing teal spores, one big, a few small."""
    SP = _ramp("0a3a36", "147060", "24a890", "5cdcc0", "b0fff0", "f0fffc")
    orbs = [(6.5, 9.5, 3.5), (11.0, 5.5, 2.4), (11.0, 11.5, 1.9), (3.5, 4.5, 1.6), (13.5, 14.0, 1.1), (7.5, 2.5, 1.0)]

    def which(x, y):
        for (ox, oy, r) in orbs:
            if math.hypot(x - ox, y - oy) <= r:
                return ox, oy, r
        return None

    def light(x, y):
        o = which(x, y)
        return _sphere_light(*o)(x, y)
    img, solid = _shape_icon(lambda x, y: which(x, y) is not None, light, SP, _rgb("041a18"), glow=(0x40, 0xe0, 0xc0))
    for (x, y) in ((5, 8), (10, 4), (10, 10), (3, 4)):
        _put(img, x, y, _pick(SP, 5))
    return img


def dwarven_cog():
    """A brass cog from a dwarven machine, eight teeth, a glowing rune-core in the hub."""
    BRASS = _ramp("3a2408", "6a4612", "a0701e", "d0a03a", "f4d278", "fff4c4")
    cx = cy = 7.5

    def inside(x, y):
        d = math.hypot(x - cx, y - cy)
        a = (math.atan2(y - cy, x - cx) + math.pi / 8) % (math.pi / 4)
        outer = 7.4 if a < math.pi / 8 else 5.6
        return 1.5 < d <= outer

    def light(x, y):
        d = math.hypot(x - cx, y - cy)
        base = 0.5 - (x - cx) * 0.03 - (y - cy) * 0.035
        return max(0.0, min(0.999, base + (0.18 if 3.2 < d < 4.4 else 0) - (0.2 if d <= 2.6 else 0)))
    img, solid = _shape_icon(inside, light, BRASS, _rgb("1a1004"))
    for (x, y) in ((7, 7), (8, 7), (7, 8), (8, 8)):
        _put(img, x, y, FORGE["core"] if (x, y) == (7, 7) else FORGE["mid"])
    for (x, y) in solid:  # rivets on the hub ring
        if abs(math.hypot(x - cx, y - cy) - 3.8) < 0.5 and (x + y) % 4 == 0:
            _put(img, x, y, _pick(BRASS, 5))
    return img


def rift_ember():
    """A shard of living flame from the Magma Rifts: a molten teardrop, white-hot in the middle, crusted at the foot."""
    FL = _ramp("3a0802", "7a1404", "c03008", "f06414", "ffb030", "fff0a0")

    def half(y):
        if y >= 10.5:
            return math.sqrt(max(0.0, 4.4 ** 2 - (y - 10.5) ** 2))
        return 4.4 * max(0.0, (y - 1.0) / 9.5) ** 0.75

    def centre(y):
        return 7.5 + max(0.0, 10.5 - y) * 0.13

    def inside(x, y):
        return abs(x - centre(y)) <= half(y) + 0.2 and y >= 1

    def light(x, y):
        h = max(0.5, half(y))
        return max(0.0, min(0.999, 1.0 - abs(x - centre(y)) / h * 0.85 - max(0.0, y - 11) * 0.06))
    img, solid = _shape_icon(inside, light, FL, _rgb("1a0400"), glow=(0xf0, 0x60, 0x10))
    for (x, y) in solid:  # a basalt crust at the foot
        if y >= 13 and noise(x, y, 161) < 45:
            _put(img, x, y, _rgb("2a2426") if x > 7 else _rgb("3e3638"))
    return img


CROWN = ["B..B..B", "BB.B.BB", "BBBBBBB", "BBGGGBB", "BBBBBBB", ".BBBBB."]
FORGE_GEM = M(["6a1404", "b03008", "f06a14", "ffb040", "fff0b0"], base=3)


def thane_key():
    """Thane's Key: a gold key with a crown for a bow and a forge-ember gem, on a dark iron shaft."""
    return gi.render(key_mask(CROWN, 9, TEETH),
                     {"B": gi.GOLD, "G": FORGE_GEM.shifted(texture=gi.flat), "S": gi.IRON.shifted(base=2), "D": gi.IRON.shifted(base=1),
                      "N": gi.GOLD.shifted(base=3), "T": gi.IRON.shifted(base=2)})


def thanes_rune():
    """Thane's Rune: a dark hexagonal rune tablet in a gold rim, the Iron Thane's crown-rune burning in it."""
    BRASS = _ramp("3a2408", "6a4612", "a0701e", "d0a03a", "f4d278")
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))

    def inside(x, y, shrink=0.0):
        dx, dy = abs(x - 7.5), abs(y - 7.5)
        return dx <= 6.6 - shrink and dy <= 7.6 - shrink - max(0.0, dx - 0.4) * 0.62
    solid = {(x, y) for y in range(16) for x in range(16) if inside(x, y)}
    for (x, y) in solid:
        if not inside(x, y, 1.2):
            lit = (x - 7.5) + (y - 7.5) < 0
            _put(img, x, y, _pick(BRASS, 4 if lit else 2))
        else:
            v = 2 - (1 if (x - 7.5) + (y - 7.5) > 3 else 0) + (1 if (x - 7.5) + (y - 7.5) < -5 else 0)
            _put(img, x, y, _pick(SLATE_DARK, v + 1))
    rune = ["X.X.X",
            "XXXXX",
            "..X..",
            ".XXX.",
            "X.X.X",
            "..X.."]
    cells = {(5 + u, 4 + v) for v, row in enumerate(rune) for u, ch in enumerate(row) if ch == "X"}
    for (x, y) in cells:
        for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            n = (x + dx, y + dy)
            if n not in cells and inside(n[0], n[1], 1.2):
                _put(img, n[0], n[1], FORGE["halo"])
    for (x, y) in cells:
        _put(img, x, y, FORGE["core"] if y in (5, 7) else FORGE["mid"])
    _put(img, 7, 5, FORGE["white"])
    for (x, y) in solid:
        for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            n = (x + dx, y + dy)
            if n not in solid:
                _put(img, n[0], n[1], _rgb("140c02"))
    return img


DEEP_BLOCKS = {"glimmerstone": glimmerstone, "glimmer_cluster": glimmer_cluster, "glowcap": glowcap, "glowcap_block": glowcap_block,
               "spore_puffball": spore_puffball, "spore_puffball_burst": spore_puffball_burst, "dwarven_runestone": dwarven_runestone}
DEEP_ITEMS = {"glimmer_geode": glimmer_geode, "glowcap_spores": glowcap_spores, "dwarven_cog": dwarven_cog, "rift_ember": rift_ember,
              "thane_key": thane_key, "thanes_rune": thanes_rune}
DEEP_CUBES = ("glimmerstone", "glowcap_block", "dwarven_runestone")
DEEP_BLOCK_INFO = {
    "glimmerstone": ("Glimmerstone", "common", "Block", "Cave stone shot through with glowing Glimmer. Found in the Glimmer Caverns."),
    "glimmer_cluster": ("Glimmer Cluster", "common", "Block",
                        "A glowing crystal cluster from the Glimmer Caverns. Breaks into Glimmer Shards (Silk Touch keeps it whole)."),
    "glowcap": ("Glowcap", "common", "Block", "A little mushroom that glows. The Fungal Depths are full of them."),
    "glowcap_block": ("Glowcap Block", "common", "Block", "The cap of a giant glowcap, bright enough to read by."),
    "spore_puffball": ("Spore Puffball", "uncommon", "Block",
                       "Bursts into a cloud of poison spores when someone walks close, then regrows. Grows in the Fungal Depths."),
    "dwarven_runestone": ("Dwarven Runestone", "uncommon", "Block", "Deepslate carved with dwarven runes that still glow. From the Dwarven Halls."),
}
DEEP_ITEM_INFO = {
    "glimmer_geode": ("Glimmer Geode", "rare", "Rare Layer Drop",
                      "Right-click to crack it open: 3-8 Glimmer Shards, and sometimes a Prism Fragment. Dropped by monsters in the Glimmer Caverns."),
    "glowcap_spores": ("Glowcap Spores", "uncommon", "Layer Material", "Dropped by monsters in the Fungal Depths."),
    "dwarven_cog": ("Dwarven Cog", "uncommon", "Layer Material",
                    "A brass cog from a dwarven machine. Dropped by monsters in the Dwarven Halls. Used for Runeforged armour."),
    "rift_ember": ("Rift Ember", "uncommon", "Layer Material", "It never cools. Dropped by monsters in the Magma Rifts."),
    "thane_key": ("Thane's Key", "rare", "Dungeon Key", "Opens the vault in the Dwarven Halls. Used up when you open it."),
    "thanes_rune": ("Thane's Rune", "epic", "Boss Trophy", "The Iron Thane's crown-rune, still warm from the forge. Dropped by the Iron Thane."),
}
# The amethyst-cluster rotations: the model points up, these turn it to face out of any surface.
CLUSTER_FACING = {"up": {}, "down": {"x": 180}, "north": {"x": 90}, "south": {"x": 90, "y": 180}, "east": {"x": 90, "y": 90},
                  "west": {"x": 90, "y": 270}}


def build_deep(lang):
    tex = ASSETS / "textures"
    out = {}
    for name, fn in {**DEEP_BLOCKS, **DEEP_ITEMS}.items():
        img = fn()
        assert img.size == (16, 16), name
        path = tex / ("item" if name in DEEP_ITEMS else "block") / f"{name}.png"
        path.parent.mkdir(parents=True, exist_ok=True)
        img.save(path)
        out[name] = img
    S = "skyrpg_world:"
    for name in DEEP_BLOCKS:
        parent = "minecraft:block/cube_all" if name in DEEP_CUBES else "minecraft:block/cross"
        key = "all" if name in DEEP_CUBES else "cross"
        gi.write_json(ASSETS / "models" / "block" / f"{name}.json", {"parent": parent, "textures": {key: S + f"block/{name}"}})
    gi.write_json(ASSETS / "blockstates" / "glimmer_cluster.json", {"variants": {
        f"facing={facing}": {"model": S + "block/glimmer_cluster", **turn} for facing, turn in CLUSTER_FACING.items()}})
    gi.write_json(ASSETS / "blockstates" / "spore_puffball.json", {"variants": {
        "burst=false": {"model": S + "block/spore_puffball"}, "burst=true": {"model": S + "block/spore_puffball_burst"}}})
    for name, (title, rarity, kind, description) in DEEP_BLOCK_INFO.items():
        if name not in ("glimmer_cluster", "spore_puffball"):
            gi.write_json(ASSETS / "blockstates" / f"{name}.json", {"variants": {"": {"model": S + f"block/{name}"}}})
        if name in DEEP_CUBES:
            gi.write_json(ASSETS / "items" / f"{name}.json", {"model": {"type": "minecraft:model", "model": S + f"block/{name}"}})
        else:  # plants and clusters show as flat sprites, like vanilla's
            gi.write_json(ASSETS / "models" / "item" / f"{name}.json",
                          {"parent": "minecraft:item/generated", "textures": {"layer0": S + f"block/{name}"}})
            gi.write_json(ASSETS / "items" / f"{name}.json", {"model": {"type": "minecraft:model", "model": S + f"item/{name}"}})
        gi.write_json(DATA / f"{name}.json", {"name": title, "rarity": rarity, "type": kind, "description": description})
        lang[f"block.skyrpg_world.{name}"] = title
    for name, (title, rarity, kind, description) in DEEP_ITEM_INFO.items():
        gi.write_json(ASSETS / "models" / "item" / f"{name}.json",
                      {"parent": "minecraft:item/generated", "textures": {"layer0": S + f"item/{name}"}})
        gi.write_json(ASSETS / "items" / f"{name}.json", {"model": {"type": "minecraft:model", "model": S + f"item/{name}"}})
        gi.write_json(DATA / f"{name}.json", {"name": title, "rarity": rarity, "type": kind, "description": description})
        lang[f"item.skyrpg_world.{name}"] = title
    return out


# ---------------------------------------------------------------- the nether: biome ground, the citadel's key and trophy
CHAR = _ramp("100c0c", "1c1414", "2a1e1c", "3a2a26", "4e3a32")
VOID = _ramp("2a0848", "5a14a0", "9a3cf0", "d08cff", "f8e8ff")
BLACKSTONE = _ramp("140f16", "1e1820", "28222c", "342c38", "423848")


def ember_ash():
    """The Ember Forest's ground: charcoal ash crowded with glowing embers, a few hot cracks (cube, tiles)."""
    img = _canvas()
    _speckled(img, CHAR, 171, base=2, lo=20, hi=78)
    for i in range(4):  # hot cracks, short and wrapping
        x, y = noise(i, 1, 172) % 16, noise(i, 2, 172) % 16
        for k in range(3 + noise(i, 3, 172) % 2):
            _wput(img, x + k, y + (k * (1 if i % 2 else -1)) // 2, _pick(EMBER, 0))
    for i in range(11):  # embers: a warm halo, a bright heart
        x, y = noise(i, 4, 173) % 16, noise(i, 5, 173) % 16
        for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            _wput(img, x + dx, y + dy, _pick(EMBER, 0))
        _wput(img, x, y, _pick(EMBER, 2 + (i % 2)))
    return img


def void_crack():
    """The Obsidian Spires' void crack: blackstone split by a glowing violet fracture that meets the middle of every edge,
    so cracks join up from block to block (cube)."""
    img = _canvas()
    _speckled(img, BLACKSTONE, 181, base=2, lo=18, hi=84)
    seam = set()
    x = 7
    for y in range(16):  # top to bottom, wandering a pixel either way and back to the middle at both ends
        seam.add((x, y))
        if 2 <= y <= 12:
            x = max(5, min(10, x + (noise(y, 1, 182) % 3) - 1))
        else:
            x = 7 if x > 8 else 8 if x < 7 else x
    y = 8
    for x2 in range(16):  # left to right the same way
        seam.add((x2, y))
        if 2 <= x2 <= 12:
            y = max(5, min(10, y + (noise(x2, 2, 182) % 3) - 1))
        else:
            y = 8 if y > 8 else 7 if y < 7 else y
    for (sx, sy) in seam:  # a dark lip, then the glow
        for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            if (sx + dx, sy + dy) not in seam:
                _put(img, sx + dx, sy + dy, _pick(VOID, 0))
    for (sx, sy) in seam:
        _put(img, sx, sy, _pick(VOID, 3 if noise(sx, sy, 183) > 30 else 2))
    for (sx, sy) in ((7, 8), (8, 7), (8, 8)):
        _put(img, sx, sy, _pick(VOID, 4))
    return img


FLAME_BOW = ["..B..B..", ".BB.BB.B", "BBBBBBBB", "BBGGGGBB", "BBBGGBBB", ".BBBBBB.", "..BBBB.."]
CHAR_IRON = M(["181010", "2e2020", "4a3430", "6a4a40", "8c6454"])
KEY_FLAME = M(["5a1404", "a83010", "e2641c", "ffa040", "ffe08a"], base=3)


def citadel_key():
    """Citadel Key: a blackened iron key, its bow a ring of flame around a white-hot core, gold collar."""
    return gi.render(key_mask(FLAME_BOW, 9, TEETH),
                     {"B": KEY_FLAME, "G": FORGE_GEM.shifted(base=4, texture=gi.flat), "S": CHAR_IRON.shifted(base=3),
                      "D": CHAR_IRON.shifted(base=1), "N": gi.GOLD.shifted(base=3), "T": CHAR_IRON.shifted(base=3)})


def tyrants_core():
    """Tyrant's Core: the Magma Tyrant's molten heart, a glowing faceted core in a cracked basalt shell, crowned with fire."""
    MOLTEN = _ramp("3a0802", "8a1c06", "d8460e", "ff8a24", "ffd060", "fff4c0")
    cx, cy = 7.5, 9.0

    def body(x, y):
        return abs(x - cx) / 6.2 + abs(y - cy) / 6.4 <= 1.0

    def flame(x, y):  # three tongues of fire on top: the crown
        for fx, h in ((4.5, 3), (7.5, 5), (10.5, 3)):
            if 0 <= (cy - 5.0) - y <= h and abs(x - fx) <= 1.2 * (1 - ((cy - 5.0) - y) / (h + 1)):
                return True
        return False
    img, solid = _shape_icon(lambda x, y: body(x, y) or flame(x, y), _sphere_light(cx, cy, 6.0), MOLTEN, _rgb("1a0400"),
                             glow=(0xf0, 0x60, 0x10))
    for (x, y) in solid:
        if flame(x, y) and not body(x, y):
            _put(img, x, y, _pick(MOLTEN, 5 if x in (7, 8) and y < 3 else 4))
        elif body(x, y) and abs(x - cx) / 6.2 + abs(y - cy) / 6.4 > 0.62 and noise(x, y, 191) < 55:
            _put(img, x, y, _rgb("2a2426") if x + y > 17 else _rgb("443a3c"))  # the basalt shell
    for (x, y) in ((7, 8), (8, 8), (7, 9), (8, 9)):
        _put(img, x, y, _pick(MOLTEN, 5))
    return img


NETHER_BLOCKS = {"ember_ash": ember_ash, "void_crack": void_crack}
NETHER_ITEMS = {"citadel_key": citadel_key, "tyrants_core": tyrants_core}
NETHER_INFO = {
    "ember_ash": ("Ember Ash", "common", "Block", "Ash that never quite goes out. The floor of the Ember Forest in the Nether."),
    "void_crack": ("Void Crack", "uncommon", "Block", "Blackstone split by a seam of glowing void. Found across the Obsidian Spires in the Nether."),
    "citadel_key": ("Citadel Key", "rare", "Dungeon Key",
                    "Opens the vault behind the Molten Throne in the Infernal Citadel. Used up when you open it."),
    "tyrants_core": ("Tyrant's Core", "epic", "Boss Trophy", "The Magma Tyrant's molten heart, still beating. Dropped by the Magma Tyrant."),
}
BIOME_NAMES = {"ember_forest": "Ember Forest", "bone_sea": "Bone Sea", "obsidian_spires": "Obsidian Spires"}


def build_nether(lang):
    tex = ASSETS / "textures"
    out = {}
    for name, fn in {**NETHER_BLOCKS, **NETHER_ITEMS}.items():
        img = fn()
        assert img.size == (16, 16), name
        path = tex / ("item" if name in NETHER_ITEMS else "block") / f"{name}.png"
        path.parent.mkdir(parents=True, exist_ok=True)
        img.save(path)
        out[name] = img
    S = "skyrpg_world:"
    for name in NETHER_BLOCKS:
        gi.write_json(ASSETS / "models" / "block" / f"{name}.json", {"parent": "minecraft:block/cube_all", "textures": {"all": S + f"block/{name}"}})
        gi.write_json(ASSETS / "blockstates" / f"{name}.json", {"variants": {"": {"model": S + f"block/{name}"}}})
        gi.write_json(ASSETS / "items" / f"{name}.json", {"model": {"type": "minecraft:model", "model": S + f"block/{name}"}})
    for name in NETHER_ITEMS:
        gi.write_json(ASSETS / "models" / "item" / f"{name}.json",
                      {"parent": "minecraft:item/generated", "textures": {"layer0": S + f"item/{name}"}})
        gi.write_json(ASSETS / "items" / f"{name}.json", {"model": {"type": "minecraft:model", "model": S + f"item/{name}"}})
    for name, (title, rarity, kind, description) in NETHER_INFO.items():
        gi.write_json(DATA / f"{name}.json", {"name": title, "rarity": rarity, "type": kind, "description": description})
        lang[f"{'block' if name in NETHER_BLOCKS else 'item'}.skyrpg_world.{name}"] = title
    for name, title in BIOME_NAMES.items():
        lang[f"biome.skyrpg_world.{name}"] = title
    return out


# ---------------------------------------------------------------- the end: biome blocks, the observatory's key and trophy
ENDSTONE = _ramp("6a6a48", "88885e", "a4a478", "bcbc92", "d2d2ac")
STARLIGHT = _ramp("2a4a8a", "5a8ae0", "a8d0ff", "e8f4ff", "ffffff")
ASTRAL = _ramp("0c1c44", "1c3a82", "3a6ad0", "78a8ff", "c0dcff", "f4faff")
BLOOM = _ramp("200838", "4a1478", "8a34d0", "c46cff", "f0b8ff", "fff0ff")
VCAP = _ramp("1a0630", "341060", "56208c", "7c34b8", "a456dc", "d498ff")


def starstone():
    """End stone with stars in it: pale four-point glints with a cold blue halo (cube, tiles)."""
    img = _canvas()
    _speckled(img, ENDSTONE, 201, base=2, lo=20, hi=82)
    for (x, y, size) in ((3, 3, 2), (11, 2, 0), (13, 9, 1), (7, 8, 2), (1, 12, 0), (10, 13, 1), (5, 14, 0), (14, 14, 0), (6, 1, 0)):
        if size == 2:
            for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                _wput(img, x + dx, y + dy, _pick(STARLIGHT, 1))
            for dx, dy in ((1, 1), (-1, 1), (1, -1), (-1, -1)):
                _wput(img, x + dx, y + dy, _pick(ENDSTONE, 1))
            _wput(img, x, y, _pick(STARLIGHT, 4))
            _wput(img, x + 2, y, _pick(STARLIGHT, 2))
            _wput(img, x - 2, y, _pick(STARLIGHT, 2))
            _wput(img, x, y + 2, _pick(STARLIGHT, 2))
            _wput(img, x, y - 2, _pick(STARLIGHT, 2))
        elif size == 1:
            _wput(img, x, y, _pick(STARLIGHT, 4))
            _wput(img, x + 1, y, _pick(STARLIGHT, 2))
            _wput(img, x, y + 1, _pick(STARLIGHT, 2))
        else:
            _wput(img, x, y, _pick(STARLIGHT, 3))
    return img


def astral_crystal():
    """Blue-white astral crystals, a crowd of tall shards (cross model, rotated to face out of any surface)."""
    return _shard_cluster([(3, 15, 1, 6), (12, 15, 1, 4), (8, 15, 2, 1), (5, 15, 1, 8), (10, 15, 1, 7)], ASTRAL,
                          ((8, 4), (5, 6), (9, 8), (3, 9), (11, 10), (6, 11)))


def void_bloom():
    """A glowing violet flower on a thin stem (cross model)."""
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    stem = _ramp("0a2a28", "145048", "1e7a6c")
    for y in range(8, 16):
        _put(img, 7 if y % 3 else 8, y, _pick(stem, 1))
        _put(img, 8 if y % 3 else 7, y, _pick(stem, 2))
    for (x, y) in ((5, 11), (4, 10), (10, 12), (11, 11)):
        _put(img, x, y, _pick(stem, 2))
    for (cx, cy, r) in ((7.5, 5.0, 3.6), (3.5, 9.5, 1.8), (12.0, 10.0, 1.8)):
        for y in range(int(cy - r - 1), int(cy + r + 2)):
            for x in range(int(cx - r - 1), int(cx + r + 2)):
                d = math.hypot(x - cx, (y - cy) * 1.15)
                if d <= r and 0 <= x < 16 and 0 <= y < 16:
                    v = 3 if d < r * 0.45 else 2 if d < r * 0.8 else 1
                    _put(img, x, y, _pick(BLOOM, v + (1 if (x + y) % 5 == 0 else 0)))
    _put(img, 7, 4, _pick(BLOOM, 5))
    _put(img, 8, 5, _pick(BLOOM, 5))
    _put(img, 7, 5, _pick(BLOOM, 4))
    return img


def void_cap():
    """The canopy of a void tree: deep purple flesh with glowing violet spots (cube, tiles)."""
    img = _canvas()
    _speckled(img, VCAP, 211, base=2, lo=20, hi=84)
    for (cx, cy, r) in ((3.5, 3.0, 1.6), (11.5, 6.5, 1.3), (6.5, 11.5, 1.8), (13.5, 13.5, 1.0), (1.0, 12.0, 1.0), (10.0, 0.5, 0.8)):
        for y in range(int(cy - r - 1), int(cy + r + 2)):
            for x in range(int(cx - r - 1), int(cx + r + 2)):
                d = math.hypot(x - cx, y - cy)
                if d <= r - 0.9:
                    _wput(img, x, y, (0xf4, 0xd8, 0xff) if d < r * 0.4 else (0xd8, 0xa0, 0xff))
                elif d <= r:
                    _wput(img, x, y, _pick(BLOOM, 3))
                elif d <= r + 0.7:
                    _wput(img, x, y, _pick(VCAP, 4))
    return img


def astral_shard():
    """Astral Shard: a long faceted blue-white crystal sliver with a bright star at its heart."""
    def inside(x, y):
        dy = (y - 7.5) / 7.4
        return abs(x - 7.5 - (y - 7.5) * 0.12) <= 3.4 * (1 - abs(dy) ** 1.4) + 0.3

    def light(x, y):
        left = x < 7.5 + (y - 7.5) * 0.12
        return (0.7 if left else 0.38) + (0.1 if noise(x, y, 221) > 88 else 0)
    img, solid = _shape_icon(inside, light, ASTRAL, _rgb("050a1e"), glow=(0x50, 0x90, 0xff))
    for (x, y) in solid:
        if abs(x - 7.5 - (y - 7.5) * 0.12) < 0.8:
            _put(img, x, y, _pick(ASTRAL, 4))
    for (x, y) in ((7, 7), (8, 7), (7, 8), (8, 8), (7, 6), (8, 9)):
        if (x, y) in solid:
            _put(img, x, y, _pick(ASTRAL, 5))
    return img


STAR_BOW = [".BB..BB.", "BBBBBBBB", ".BBGGBB.", "BBBGGBBB", ".BBBBBB.", "..B..B.."]
STAR_STEEL = M(["0c1430", "1c2a5a", "34508c", "5a82c4", "a0c8ff"], base=3)
STAR_GEM = M(["5a78c8", "90b4ff", "c8dcff", "eef6ff", "ffffff"], base=3)


def observatory_key():
    """Observatory Key: a dark star-steel key, its bow a star set with a white-blue gem."""
    return gi.render(key_mask(STAR_BOW, 9, TEETH),
                     {"B": STAR_STEEL.shifted(base=3), "G": STAR_GEM.shifted(texture=gi.flat), "S": STAR_STEEL.shifted(base=3),
                      "D": STAR_STEEL.shifted(base=1), "N": gi.GOLD.shifted(base=3), "T": STAR_STEEL.shifted(base=4)})


def star_warden_core():
    """Star Warden's Core: a dark violet sphere holding a swirl of stars, ringed by a thin silver orbit."""
    CORE = _ramp("0a0418", "1e0c40", "3a1a7a", "5e30b0", "8c5ce0", "c8a8ff")
    cx, cy = 7.5, 8.0

    def body(x, y):
        return math.hypot(x - cx, y - cy) <= 5.4

    def ring(x, y):
        return abs(math.hypot((x - cx) / 7.4, (y - cy + (x - cx) * 0.35) / 2.6) - 1.0) < 0.14
    img, solid = _shape_icon(lambda x, y: body(x, y) or ring(x, y), _sphere_light(cx, cy, 5.6), CORE, _rgb("05020c"), glow=(0x70, 0x50, 0xe0))
    for (x, y) in solid:
        if ring(x, y):
            _put(img, x, y, _pick(STARLIGHT, 2 if (x + y) % 3 else 3))
    for (x, y) in ((6, 6), (9, 8), (7, 10), (10, 5), (5, 9)):
        if body(x, y):
            _put(img, x, y, (0xff, 0xff, 0xff))
            for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                if body(x + dx, y + dy) and noise(x + dx, y + dy, 231) < 60:
                    _put(img, x + dx, y + dy, _pick(CORE, 5))
    return img


END_BLOCKS = {"starstone": starstone, "astral_crystal": astral_crystal, "void_bloom": void_bloom, "void_cap": void_cap}
END_ITEMS = {"astral_shard": astral_shard, "observatory_key": observatory_key, "star_warden_core": star_warden_core}
END_CUBES = ("starstone", "void_cap")
END_INFO = {
    "starstone": ("Starstone", "uncommon", "Block", "End stone with stars in it. Found across the Astral Fields in the End."),
    "astral_crystal": ("Astral Crystal", "uncommon", "Block",
                       "A glowing crystal cluster from the Astral Fields. Breaks into Astral Shards (Silk Touch keeps it whole)."),
    "void_bloom": ("Void Bloom", "uncommon", "Block", "A violet flower that glows in the dark. It grows in the Void Gardens in the End."),
    "void_cap": ("Void Cap", "common", "Block", "The glowing canopy of a void tree, from the Void Gardens in the End."),
    "astral_shard": ("Astral Shard", "rare", "Material",
                     "A sliver of an Astral Crystal, full of cold starlight. Dropped by Astral Crystals; used for the Ender Wyrm altar."),
    "observatory_key": ("Observatory Key", "rare", "Dungeon Key",
                        "Opens the vault behind the Star Warden's dome in the Astral Observatory. Used up when you open it."),
    "star_warden_core": ("Star Warden's Core", "epic", "Boss Trophy",
                         "The Star Warden's heart, a small night sky. Dropped by the Star Warden."),
}
END_BIOME_NAMES = {"void_gardens": "Void Gardens", "shattered_isles": "Shattered Isles", "astral_fields": "Astral Fields"}


# ---------------------------------------------------------------- Ruined Citadel and Catacombs: keys and trophies
CREST_BOW = [".BBBB.", "BBGGBB", "BBGGBB", ".BBBB.", "..BB.."]
SKULL_BOW = [".BBBB.", "BBBBBB", "BGBBGB", "BBBBBB", ".B..B."]
BLACK_IRON = M(["08080c", "1c1c24", "383844", "5c5c6c", "8c8c9e"], base=3)
CREST_GEM = M(["5a0c14", "96182a", "d03040", "f87080", "ffc0c8"], base=3)
OSSUARY_BONE = M(["4a4638", "7a7462", "a8a28a", "d0cab0", "f4f0dc"], base=3)
SOCKET = M(["020402", "06100a", "0e2a18", "1c5a34", "34a060"], base=1)


def citadel_crest_key():
    """Citadel Crest Key: a black-iron key whose bow is a small heater shield set with a red gem."""
    return gi.render(key_mask(CREST_BOW, 9, TEETH),
                     {"B": BLACK_IRON.shifted(base=3), "G": CREST_GEM.shifted(texture=gi.flat), "S": BLACK_IRON.shifted(base=3),
                      "D": BLACK_IRON.shifted(base=1), "N": gi.GOLD.shifted(base=3), "T": BLACK_IRON.shifted(base=4)})


def ossuary_key():
    """Ossuary Key: a key of grey bone, its bow a little skull with green-lit eye sockets."""
    return gi.render(key_mask(SKULL_BOW, 9, TEETH),
                     {"B": OSSUARY_BONE.shifted(base=3), "G": SOCKET.shifted(texture=gi.flat), "S": OSSUARY_BONE.shifted(base=3),
                      "D": OSSUARY_BONE.shifted(base=1), "N": OSSUARY_BONE.shifted(base=1), "T": OSSUARY_BONE.shifted(base=4)})


def fallen_crest():
    """Fallen Crest: a dented black-iron heater shield with a cracked crimson crest."""
    IRON = _ramp("0a0a10", "1c1c26", "363644", "585868", "8a8a9c", "bcbccc")

    def shield(x, y):
        if not 2 <= y <= 14:
            return False
        return abs(x - 7.5) <= 5.6 - max(0.0, y - 8) * 0.8

    def light(x, y):
        return max(0.0, min(0.999, 0.55 + (7.5 - x) * 0.04 + (6 - y) * 0.03 + (0.12 if noise(x, y, 241) > 86 else 0)))
    img, solid = _shape_icon(shield, light, IRON, _rgb("050508"), glow=(0xc0, 0x20, 0x30))
    RED = CREST_GEM
    for (x, y) in solid:
        if abs(x - 7.5) < 1.0 and 4 <= y <= 12 or 5 <= y <= 6 and abs(x - 7.5) < 3.6:
            _put(img, x, y, _pick(_ramp("5a0c14", "96182a", "d03040", "f87080"), 1 + (x + y) % 3 if (x + y) % 4 else 3))
    for (x, y) in ((6, 9), (7, 10), (9, 8)):
        if (x, y) in solid:
            _put(img, x, y, _pick(IRON, 0))
    return img


def bonelord_phylactery():
    """Bonelord's Phylactery: a bone-stoppered glass vessel holding a sickly green soul-light."""
    SOULGREEN = _ramp("041a10", "0c3a24", "1c6a3e", "3cae62", "8cf0a0", "e0ffe8")
    cx, cy = 7.5, 9.6

    def vessel(x, y):
        return math.hypot(x - cx, (y - cy) * 1.05) <= 4.7 or (6 <= x <= 9 and 3 <= y <= 6)
    img, solid = _shape_icon(vessel, _sphere_light(cx, cy, 5.2), SOULGREEN, _rgb("02080a"), glow=(0x50, 0xe0, 0x80))
    for (x, y) in solid:
        if 6 <= x <= 9 and y <= 4:
            _put(img, x, y, _pick(_ramp("5a5240", "8c8068", "bcb094", "e0d6b8"), 1 + (x + y) % 3))
    for (x, y) in ((7, 9), (8, 10), (7, 11), (9, 9)):
        if (x, y) in solid:
            _put(img, x, y, (0xf4, 0xff, 0xf8))
    for (x, y) in ((5, 11), (10, 11)):
        if (x, y) in solid:
            _put(img, x, y, _pick(SOULGREEN, 5))
    return img


CRYPT_ITEMS = {"citadel_crest_key": citadel_crest_key, "fallen_crest": fallen_crest, "ossuary_key": ossuary_key,
               "bonelord_phylactery": bonelord_phylactery}
CRYPT_TITLES = {"citadel_crest_key": "Citadel Crest Key", "fallen_crest": "Fallen Crest", "ossuary_key": "Ossuary Key",
                "bonelord_phylactery": "Bonelord's Phylactery"}


def build_crypt_items(lang):
    """Textures, item models, client item definitions and lang for the four items above. Their data item JSONs
    (skyrpg/items/) are written with the dungeons, so this leaves them alone."""
    out = {}
    S = "skyrpg_world:"
    for name, fn in CRYPT_ITEMS.items():
        img = fn()
        assert img.size == (16, 16), name
        path = ASSETS / "textures" / "item" / f"{name}.png"
        path.parent.mkdir(parents=True, exist_ok=True)
        img.save(path)
        out[name] = img
        gi.write_json(ASSETS / "models" / "item" / f"{name}.json",
                      {"parent": "minecraft:item/generated", "textures": {"layer0": S + f"item/{name}"}})
        gi.write_json(ASSETS / "items" / f"{name}.json", {"model": {"type": "minecraft:model", "model": S + f"item/{name}"}})
        lang[f"item.skyrpg_world.{name}"] = CRYPT_TITLES[name]
    return out


def build_end(lang):
    tex = ASSETS / "textures"
    out = {}
    for name, fn in {**END_BLOCKS, **END_ITEMS}.items():
        img = fn()
        assert img.size == (16, 16), name
        path = tex / ("item" if name in END_ITEMS else "block") / f"{name}.png"
        path.parent.mkdir(parents=True, exist_ok=True)
        img.save(path)
        out[name] = img
    S = "skyrpg_world:"
    for name in END_BLOCKS:
        cube = name in END_CUBES
        gi.write_json(ASSETS / "models" / "block" / f"{name}.json", {
            "parent": "minecraft:block/cube_all" if cube else "minecraft:block/cross",
            "textures": {"all" if cube else "cross": S + f"block/{name}"}})
        if name == "astral_crystal":
            gi.write_json(ASSETS / "blockstates" / f"{name}.json", {"variants": {
                f"facing={facing}": {"model": S + f"block/{name}", **turn} for facing, turn in CLUSTER_FACING.items()}})
        else:
            gi.write_json(ASSETS / "blockstates" / f"{name}.json", {"variants": {"": {"model": S + f"block/{name}"}}})
        if cube:
            gi.write_json(ASSETS / "items" / f"{name}.json", {"model": {"type": "minecraft:model", "model": S + f"block/{name}"}})
        else:
            gi.write_json(ASSETS / "models" / "item" / f"{name}.json",
                          {"parent": "minecraft:item/generated", "textures": {"layer0": S + f"block/{name}"}})
            gi.write_json(ASSETS / "items" / f"{name}.json", {"model": {"type": "minecraft:model", "model": S + f"item/{name}"}})
    for name in END_ITEMS:
        gi.write_json(ASSETS / "models" / "item" / f"{name}.json",
                      {"parent": "minecraft:item/generated", "textures": {"layer0": S + f"item/{name}"}})
        gi.write_json(ASSETS / "items" / f"{name}.json", {"model": {"type": "minecraft:model", "model": S + f"item/{name}"}})
    for name, (title, rarity, kind, description) in END_INFO.items():
        gi.write_json(DATA / f"{name}.json", {"name": title, "rarity": rarity, "type": kind, "description": description})
        lang[f"{'item' if name in END_ITEMS else 'block'}.skyrpg_world.{name}"] = title
    for name, title in END_BIOME_NAMES.items():
        lang[f"biome.skyrpg_world.{name}"] = title
    return out


def build():
    tex = ASSETS / "textures"
    icons = {}
    for name, (mask, mats, tweak) in ITEMS.items():
        gi.write_json(ASSETS / "items" / f"{name}.json", {"model": {"type": "minecraft:model", "model": f"skyrpg_world:item/{name}"}})
        gi.write_json(ASSETS / "models" / "item" / f"{name}.json",
                      {"parent": "minecraft:item/generated", "textures": {"layer0": f"skyrpg_world:item/{name}"}})
        icons[name] = gi.render(mask, mats, tweak)
        out = tex / "item" / f"{name}.png"
        out.parent.mkdir(parents=True, exist_ok=True)
        icons[name].save(out)
    blocks = {"waystone_side": side_texture(False), "waystone_side_lit": side_texture(True),
              "waystone_top": top_texture(), "waystone_base": base_texture()}
    for name, img in blocks.items():
        out = tex / "block" / f"{name}.png"
        out.parent.mkdir(parents=True, exist_ok=True)
        img.save(out)
    gi.write_json(ASSETS / "models" / "block" / "waystone.json", waystone_model(False))
    gi.write_json(ASSETS / "models" / "block" / "waystone_lit.json", waystone_model(True))
    gi.write_json(ASSETS / "blockstates" / "waystone.json", {"variants": {
        "lit=false": {"model": "skyrpg_world:block/waystone"}, "lit=true": {"model": "skyrpg_world:block/waystone_lit"}}})
    gi.write_json(ASSETS / "items" / "waystone.json", {"model": {"type": "minecraft:model", "model": "skyrpg_world:block/waystone_lit"}})
    lang = {"itemGroup.skyrpg_world": "SkyRPG World"}
    for name, (title, rarity, kind, description) in NAMES.items():
        lang[f"item.skyrpg_world.{name}"] = title
        gi.write_json(DATA / f"{name}.json", {"name": title, "rarity": rarity, "type": kind, "description": description})
    lang["block.skyrpg_world.waystone"] = "Waystone"
    regions = build_regions(lang)
    deep = build_deep(lang)
    nether = build_nether(lang)
    end = build_end(lang)
    end.update(build_crypt_items(lang))
    gi.write_json(ASSETS / "lang" / "en_us.json", lang)
    blocks.update(regions)
    blocks.update(deep)
    blocks.update(nether)
    blocks.update(end)
    return icons, blocks


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--preview", type=Path, help="directory to write a contact sheet to")
    args = parser.parse_args()
    icons, blocks = build()
    if args.preview:
        args.preview.mkdir(parents=True, exist_ok=True)
        images = list(icons.values()) + list(blocks.values())
        sheet = Image.new("RGBA", (10 * 136 + 8, 144 * ((len(images) + 9) // 10)), (24, 28, 38, 255))
        for i, img in enumerate(images):
            sheet.alpha_composite(img.resize((128, 128), Image.NEAREST), (8 + (i % 10) * 136, 8 + (i // 10) * 144))
        sheet.save(args.preview / "world.png")
    print(f"wrote {len(icons)} item icons, {len(blocks)} block textures, models, blockstate, lang and item definitions")
