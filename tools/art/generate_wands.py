#!/usr/bin/env python3
"""Art of the magic mod: seven wand models (3D cuboids with glowing tips) and twelve spell scroll icons.

    python tools/art/generate_wands.py                 # writes the assets
    python tools/art/generate_wands.py --preview DIR   # also writes offline renders of every wand to DIR

Writes into magic/src/main/resources/assets/skyrpg_magic/ only (items/, models/item/, textures/item/).

A wand is modelled standing along y (grip at the bottom, tip up, centred on x = z = 8). Vanilla's handheld sprites are diagonal
(bottom left to top right), and its hand transforms are made for that, so the display transforms here are vanilla's own with a
45 degree turn about z folded in (the same trick the bows use): the wand then sits in the hand exactly like a vanilla tool.
Glowing parts use ``light_emission`` and no shading.
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
from sb_common import ItemModel, Ramp as _Ramp, fbm, render  # noqa: E402


def Ramp(*colors):
    return _Ramp(*[c.lstrip('#') for c in colors])


ROOT = Path(__file__).resolve().parents[2]
ASSETS = ROOT / "magic" / "src" / "main" / "resources" / "assets" / "skyrpg_magic"
NS = "skyrpg_magic"


# ------------------------------------------------------------------------------------------------------ materials
WOOD = Ramp("#3A2A1C", "#5A4128", "#7A5A38", "#9A7648", "#BC9A66", "#D8BC88")
BIRCH = Ramp("#7A6A50", "#A08A68", "#C4B08C", "#DCCCA8", "#EEE0C0", "#FFF4D8")
DARKWOOD = Ramp("#1A1210", "#2A1C16", "#3C281E", "#523626", "#6A4630", "#84583A")
LEATHER = Ramp("#2A1810", "#3E2418", "#58341E", "#744628", "#92602F", "#B07C42")
GOLD = Ramp("#5A3A0C", "#8A5E14", "#B98A1E", "#E0B232", "#F5D25A", "#FFF0A0")
COPPER = Ramp("#4A2410", "#7A3E1C", "#A8562A", "#CE7438", "#E89A58", "#F8C088")
SKYIRON = Ramp("#202A38", "#34445A", "#4C6280", "#6A82A4", "#8EA6C6", "#BCD0E8")
SILVER = Ramp("#2A3440", "#46566A", "#6A7E94", "#90A6BC", "#BCD0E0", "#E8F4FF")
NAVY = Ramp("#0A0E24", "#121A3A", "#1C2A52", "#2A3C70", "#3C528C", "#566CAA")
ICE = Ramp("#2A5A8A", "#3C7CB0", "#5CA0D6", "#86C4EE", "#B6E2FA", "#E8F8FF")
LAPIS = Ramp("#0C1A5A", "#1630A0", "#2A4CD6", "#4E70FF", "#86A0FF", "#C0D0FF")
PINK = Ramp("#5A1A4A", "#8A2A70", "#BC48A0", "#E070C8", "#F8A0E8", "#FFD8F8")
VIOLET = Ramp("#2A0A5A", "#4A1A90", "#6E34C8", "#9658F0", "#BC8AFF", "#E6CCFF")
EMBER = Ramp("#8A1A00", "#C43A06", "#F06A12", "#FF9A2A", "#FFC85A", "#FFF0A8")
STORM = Ramp("#8A7000", "#C4A000", "#F0D020", "#FFEC50", "#FFF8A0", "#FFFFFF")
STAR = Ramp("#A07A20", "#D0A838", "#F0CC58", "#FFE488", "#FFF2C0", "#FFFFFF")

GLOWS = {"lapis", "glimmer", "orb", "heart", "flame", "icecore", "bolt", "star", "spark", "crack", "gem"}


class Atlas:
    """A 64x64 texture of up to sixteen 16x16 swatches; a face samples a patch of one (4 pixels per model unit)."""

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


class Wand:
    """One wand's geometry. Coordinates are in the standing frame: x and z around 8, y up the shaft."""
    id = ""
    length = 20.0       # tip to pommel, in model units (sets the display scale)
    seed = 1
    shift = 2.0         # the whole model moves up this much so the grip lands in the hand

    def __init__(self):
        self.atlas = Atlas(self.seed)
        self.m = ItemModel()
        self.m.textures = {"tex": f"{NS}:item/{self.id}_wand", "particle": f"{NS}:item/{self.id}_wand", "layer0": f"{NS}:item/{self.id}_icon"}
        self.setup(self.atlas)
        self.build()
        self.m.display = display(self.length)

    def setup(self, atlas):
        raise NotImplementedError

    def build(self):
        raise NotImplementedError

    def cube(self, mat, x0, x1, y0, y1, z0, z1, rotation=None):
        """An axis-aligned box in the standing frame (x and z are offsets from the centre line)."""
        y0 += self.shift
        y1 += self.shift
        frm = [8 + min(x0, x1), min(y0, y1), 8 + min(z0, z1)]
        to = [8 + max(x0, x1), max(y0, y1), 8 + max(z0, z1)]
        fx, fy, fz = (abs(to[i] - frm[i]) for i in range(3))
        emit = 15 if mat in GLOWS else 0
        dims = {"north": (fx, fy), "south": (fx, fy), "east": (fz, fy), "west": (fz, fy), "up": (fx, fz), "down": (fx, fz)}
        faces = {}
        for name, (w, h) in dims.items():
            if w > 0 and h > 0:
                faces[name] = ("tex", self.atlas.uv(mat, w, h), emit)
        rot = None
        if rotation:
            rot = {"angle": rotation[0], "axis": rotation[1], "origin": [8 + rotation[2], rotation[3] + self.shift, 8 + rotation[4]]}
        self.m.box(frm, to, faces, rotation=rot, shade=not emit)

    def rod(self, mat, y0, y1, w, d=None, ox=0.0, oz=0.0):
        d = w if d is None else d
        self.cube(mat, ox - w / 2, ox + w / 2, y0, y1, oz - d / 2, oz + d / 2)

    def band(self, mat, y, h, w):
        self.rod(mat, y, y + h, w)

    def grip(self, y0, y1, w=1.55):
        self.rod("wrap", y0, y1, w)
        for k in range(int((y1 - y0) // 1.4)):
            self.rod("wrapdark", y0 + 0.5 + k * 1.4, y0 + 0.75 + k * 1.4, w + 0.12)


def display(length):
    """Vanilla's handheld transforms with a 45 degree turn about z folded in, scaled so longer wands are not in proportion."""
    k = (20.0 / length) ** 0.45

    def rot(x, y, z):
        d = math.radians
        rx = np.array([[1, 0, 0], [0, math.cos(d(x)), -math.sin(d(x))], [0, math.sin(d(x)), math.cos(d(x))]])
        ry = np.array([[math.cos(d(y)), 0, math.sin(d(y))], [0, 1, 0], [-math.sin(d(y)), 0, math.cos(d(y))]])
        rz = np.array([[math.cos(d(z)), -math.sin(d(z)), 0], [math.sin(d(z)), math.cos(d(z)), 0], [0, 0, 1]])
        p = np.array([[math.cos(d(-45)), -math.sin(d(-45)), 0], [math.sin(d(-45)), math.cos(d(-45)), 0], [0, 0, 1]])
        r = rx @ ry @ rz @ p
        ang = (math.atan2(-r[1, 2], r[2, 2]), math.asin(max(-1.0, min(1.0, r[0, 2]))), math.atan2(-r[0, 1], r[0, 0]))
        return [round(math.degrees(a), 2) for a in ang]

    tp = {"rotation": rot(0, -90, 55), "translation": [0, 4.0, 0.5], "scale": [round(0.75 * k, 4)] * 3}
    fp = {"rotation": rot(0, -90, 25), "translation": [-1.4, 2.4, 1.13], "scale": [round(0.5 * k, 4)] * 3}
    return {
        "gui": {"rotation": [0, 0, 0], "translation": [0, 0, 0], "scale": [round(k * 0.95, 4)] * 3},
        "ground": {"rotation": [0, 0, 0], "translation": [0, 3, 0], "scale": [0.5, 0.5, 0.5]},
        "fixed": {"rotation": [0, 180, 0], "translation": [0, 0, 0], "scale": [round(k * 0.9, 4)] * 3},
        "firstperson_righthand": fp, "firstperson_lefthand": fp,
        "thirdperson_righthand": tp, "thirdperson_lefthand": tp,
    }


# ------------------------------------------------------------------------------------------------------ the wands
class Apprentice(Wand):
    id, length, seed = "apprentice_wand", 18.0, 11

    def setup(self, a):
        a.add("shaft", "wood", BIRCH, 3.0, 1.2)
        a.add("wrap", "wrap", LEATHER, 2.4)
        a.add("wrapdark", "wrap", LEATHER, 1.2)
        a.add("copper", "metal", COPPER, 3.0, 1.0)
        a.add("lapis", "glow", LAPIS, 3.2)

    def build(self):
        self.rod("copper", -2.0, -0.6, 1.7)
        self.rod("shaft", -0.6, 14.4, 1.2)
        self.grip(1.0, 5.4)
        self.band("copper", 5.4, 0.7, 1.55)
        self.band("copper", 13.4, 0.8, 1.6)
        for sx in (-0.7, 0.7):
            for sz in (-0.7, 0.7):
                self.rod("copper", 14.2, 16.3, 0.4, ox=sx, oz=sz)
        self.rod("lapis", 14.3, 16.1, 1.5)
        self.rod("lapis", 16.1, 16.7, 0.8)


class Gilded(Wand):
    id, length, seed = "gilded_wand", 20.0, 22

    def setup(self, a):
        a.add("shaft", "wood", BIRCH, 3.6, 1.0)
        a.add("wrap", "wrap", LEATHER, 2.4)
        a.add("wrapdark", "wrap", LEATHER, 1.2)
        a.add("gold", "metal", GOLD, 3.2, 1.0)
        a.add("glimmer", "glow", PINK, 3.4)
        a.add("gem", "glow", VIOLET, 4.0)

    def build(self):
        self.rod("gold", -2.2, -0.5, 1.8)
        self.rod("shaft", -0.5, 15.2, 1.25)
        self.grip(0.8, 5.4)
        for y in (5.4, 8.8, 12.2):
            self.band("gold", y, 0.8, 1.75)
        # the crown: four prongs flaring out around the crystal
        for sx, sz in ((-1, -1), (1, -1), (-1, 1), (1, 1)):
            self.rod("gold", 15.0, 16.0, 0.5, ox=0.65 * sx, oz=0.65 * sz)
            self.rod("gold", 16.0, 18.4, 0.45, ox=0.95 * sx, oz=0.95 * sz)
        self.rod("gold", 14.8, 15.4, 2.2)
        self.rod("glimmer", 15.4, 18.0, 1.4)
        self.rod("gem", 18.0, 19.0, 0.8)


class SkyironRod(Wand):
    id, length, seed = "skyiron_rod", 22.0, 33

    def setup(self, a):
        a.add("steel", "metal", SKYIRON, 3.2, 1.2)
        a.add("rib", "metal", SILVER, 3.4, 1.0)
        a.add("wrap", "wrap", LEATHER, 2.4)
        a.add("wrapdark", "wrap", LEATHER, 1.2)
        a.add("orb", "glow", VIOLET, 3.6)
        a.add("spark", "glow", LAPIS, 4.2)

    def build(self):
        self.rod("rib", -2.2, -0.4, 2.0)
        self.rod("steel", -0.4, 17.0, 1.4)
        self.grip(0.6, 5.8, 1.7)
        for y in (6.6, 9.4, 12.2, 15.0):
            self.band("rib", y, 0.55, 1.95)
        for sx, sz in ((-1, 0), (1, 0), (0, -1), (0, 1)):
            self.rod("steel", 16.6, 19.4, 0.5, ox=0.95 * sx, oz=0.95 * sz)
        self.rod("rib", 16.4, 17.0, 2.4)
        # a mana orb held over the prongs, with motes round it
        self.cube("orb", -1.1, 1.1, 17.6, 19.8, -1.1, 1.1)
        self.cube("orb", -0.7, 0.7, 19.8, 20.5, -0.7, 0.7)
        for ox, oy, oz in ((1.9, 18.2, 0.4), (-1.8, 19.3, -0.5), (0.3, 21.4, 1.4)):
            self.cube("spark", ox - 0.3, ox + 0.3, oy, oy + 0.6, oz - 0.3, oz + 0.3)


class Emberheart(Wand):
    id, length, seed = "emberheart_staff", 26.0, 44

    def setup(self, a):
        a.add("wood", "wood", DARKWOOD, 2.8, 1.4)
        a.add("wrap", "wrap", LEATHER, 2.0)
        a.add("wrapdark", "wrap", LEATHER, 1.0)
        a.add("iron", "metal", SKYIRON, 2.0, 1.0)
        a.add("heart", "glow", EMBER, 3.8)
        a.add("flame", "glow", EMBER, 4.6)
        a.add("crack", "glow", EMBER, 2.6)

    def build(self):
        self.rod("iron", -3.2, -1.2, 1.8)
        # a gnarled shaft: each segment sits a little off the one below
        offsets = [0.0, 0.15, -0.1, 0.18, -0.12, 0.1, -0.15, 0.12]
        for i, off in enumerate(offsets):
            self.rod("wood", -1.2 + i * 2.15, -1.2 + (i + 1) * 2.15 + 0.05, 1.5, ox=off, oz=-off * 0.6)
        self.grip(1.0, 6.0, 1.8)
        for y in (8.4, 12.2):
            self.rod("crack", y, y + 0.5, 1.2, 1.7)
        self.band("iron", 6.0, 0.6, 1.9)
        # the fork that cradles the heart
        self.cube("wood", -1.9, -0.9, 15.8, 21.0, -0.7, 0.7, rotation=(22.5, "z", -1.4, 16.0, 0))
        self.cube("wood", 0.9, 1.9, 15.8, 21.0, -0.7, 0.7, rotation=(-22.5, "z", 1.4, 16.0, 0))
        self.cube("wood", -0.8, 0.8, 15.0, 16.4, -0.8, 0.8)
        self.rod("iron", 15.6, 16.2, 2.6)
        self.cube("heart", -1.25, 1.25, 17.3, 19.8, -1.25, 1.25)
        for ox, oy, oz, s in ((0, 20.0, 0, 0.9), (-0.7, 20.8, 0.3, 0.6), (0.6, 21.2, -0.3, 0.55), (0.1, 22.1, 0.1, 0.4)):
            self.cube("flame", ox - s / 2, ox + s / 2, oy, oy + s * 1.3, oz - s / 2, oz + s / 2)


class Frostspire(Wand):
    id, length, seed = "frostspire_staff", 26.0, 55

    def setup(self, a):
        a.add("shaft", "metal", SILVER, 3.0, 1.2)
        a.add("ice", "ice", ICE, 3.2, 1.2)
        a.add("wrap", "wrap", NAVY, 3.0)
        a.add("wrapdark", "wrap", NAVY, 1.4)
        a.add("band", "metal", SKYIRON, 3.2, 1.0)
        a.add("icecore", "glow", ICE, 4.4)

    def build(self):
        self.rod("band", -3.0, -1.0, 1.9)
        self.rod("shaft", -1.0, 17.0, 1.35)
        self.grip(1.0, 6.2, 1.7)
        for y in (6.2, 11.0, 15.4):
            self.band("band", y, 0.7, 1.85)
        # the spire: stacked, tapering crystal with side shards
        self.rod("ice", 16.6, 17.6, 2.6)
        self.rod("icecore", 17.6, 19.6, 2.0)
        self.rod("icecore", 19.6, 21.6, 1.4)
        self.rod("icecore", 21.6, 23.4, 0.9)
        self.rod("icecore", 23.4, 24.6, 0.45)
        for sx in (-1, 1):
            self.cube("ice", sx * 1.2 - 0.4, sx * 1.2 + 0.4, 16.8, 19.8, -0.4, 0.4, rotation=(-22.5 * sx, "z", sx * 1.2, 17.0, 0))
        for sz in (-1, 1):
            self.cube("ice", -0.4, 0.4, 16.8, 19.4, sz * 1.2 - 0.4, sz * 1.2 + 0.4, rotation=(22.5 * sz, "x", 0, 17.0, sz * 1.2))


class Tempest(Wand):
    id, length, seed = "tempest_scepter", 28.0, 66

    def setup(self, a):
        a.add("shaft", "metal", GOLD, 2.6, 1.0)
        a.add("dark", "metal", SKYIRON, 1.6, 1.0)
        a.add("wrap", "wrap", LEATHER, 2.0)
        a.add("wrapdark", "wrap", LEATHER, 1.0)
        a.add("bolt", "glow", STORM, 3.4)
        a.add("spark", "glow", STORM, 4.8)
        a.add("gem", "glow", LAPIS, 4.6)

    def build(self):
        self.rod("shaft", -3.0, -1.0, 2.0)
        self.rod("dark", -1.0, 18.0, 1.5)
        self.grip(0.6, 6.0, 1.85)
        self.band("shaft", 6.0, 0.8, 1.95)
        # a lightning bolt zig-zagging up one side of the shaft
        steps = [(0.95, 8.0), (0.45, 9.8), (1.05, 11.4), (0.5, 13.2), (1.05, 14.8)]
        for i, (x, y) in enumerate(steps):
            self.cube("bolt", x - 0.4, x + 0.4, y, y + 1.9, -0.45, 0.45)
        self.rod("shaft", 17.4, 18.2, 2.5)
        # four prongs hold a crackling diamond
        for sx, sz in ((-1, -1), (1, -1), (-1, 1), (1, 1)):
            self.rod("shaft", 18.0, 22.4, 0.5, ox=1.15 * sx, oz=1.15 * sz)
        self.cube("bolt", -1.2, 1.2, 18.6, 21.4, -1.2, 1.2, rotation=(45, "y", 0, 20.0, 0))
        self.cube("gem", -0.7, 0.7, 19.1, 20.9, -0.7, 0.7, rotation=(45, "y", 0, 20.0, 0))
        self.rod("shaft", 22.2, 23.0, 2.6)
        for ox, oy, oz in ((2.0, 21.0, 0.5), (-1.9, 23.2, -0.4), (0.4, 25.0, 0.6), (-0.6, 18.5, -2.0)):
            self.cube("spark", ox - 0.3, ox + 0.3, oy, oy + 0.7, oz - 0.3, oz + 0.3)


class Starweaver(Wand):
    id, length, seed = "starweaver_staff", 30.0, 77

    def setup(self, a):
        a.add("shaft", "metal", NAVY, 3.0, 1.2)
        a.add("gold", "metal", GOLD, 3.4, 1.0)
        a.add("wrap", "wrap", NAVY, 2.4)
        a.add("wrapdark", "wrap", NAVY, 1.2)
        a.add("star", "glow", STAR, 4.4)
        a.add("spark", "glow", STAR, 5.0)
        a.add("inlay", "glow", STAR, 2.6)

    def build(self):
        self.rod("gold", -3.2, -1.2, 2.0)
        self.rod("shaft", -1.2, 19.0, 1.5)
        self.grip(0.6, 6.4, 1.85)
        for y in (6.4, 10.2, 14.0, 17.4):
            self.band("gold", y, 0.7, 1.95)
        # a thread of starlight spiralling up the shaft
        for i in range(6):
            x = (0.82, 0.0, -0.82, 0.0, 0.82, 0.0)[i]
            z = (0.0, 0.82, 0.0, -0.82, 0.0, 0.82)[i]
            self.cube("inlay", x - 0.18, x + 0.18, 7.2 + i * 1.7, 8.3 + i * 1.7, z - 0.18, z + 0.18)
        # a golden frame round a star
        self.rod("gold", 18.6, 19.4, 3.0)
        for sx in (-1, 1):
            self.cube("gold", sx * 1.9 - 0.3, sx * 1.9 + 0.3, 19.4, 24.6, -0.3, 0.3)
        self.cube("gold", -2.2, 2.2, 24.6, 25.3, -0.3, 0.3)
        self.cube("star", -1.8, 1.8, 21.6, 22.4, -0.4, 0.4)
        self.cube("star", -0.4, 0.4, 20.2, 23.8, -0.4, 0.4)
        self.cube("star", -0.8, 0.8, 21.2, 22.8, -0.8, 0.8)
        for ox, oy, oz in ((2.8, 20.0, 0.4), (-2.9, 23.0, -0.4), (0.3, 27.0, 0.3), (-1.4, 17.6, 2.0), (1.7, 26.0, -1.2)):
            self.cube("spark", ox - 0.3, ox + 0.3, oy, oy + 0.6, oz - 0.3, oz + 0.3)


WANDS = [Apprentice, Gilded, SkyironRod, Emberheart, Frostspire, Tempest, Starweaver]


def write_wand(cls, preview_dir=None):
    wand = cls()
    tex = ASSETS / "textures" / "item"
    tex.mkdir(parents=True, exist_ok=True)
    (ASSETS / "models" / "item").mkdir(parents=True, exist_ok=True)
    (ASSETS / "items").mkdir(parents=True, exist_ok=True)
    wand.m.write(ASSETS / "models" / "item" / f"{wand.id}.json")
    wand.atlas.img.save(tex / f"{wand.id}_wand.png")
    images = {"tex": wand.atlas.img, "particle": wand.atlas.img}
    icon = render(wand.m, images, rot=(-6, 28, -38), size=128, scale=128 / 30, bg=(0, 0, 0, 0), center=(8, 10, 8))
    icon.save(tex / f"{wand.id}_icon.png")
    with open(ASSETS / "items" / f"{wand.id}.json", "w", encoding="utf8", newline="\n") as f:
        json.dump({"model": {"type": "minecraft:model", "model": f"{NS}:item/{wand.id}"}}, f, indent=2)
        f.write("\n")
    if preview_dir:
        views = [render(wand.m, images, rot=r, size=256, scale=256 / 34, center=(8, 10, 8)) for r in ((0, 0, 0), (0, 40, 0), (-10, 90, 0))]
        sheet = Image.new("RGBA", (256 * 3, 256))
        for i, v in enumerate(views):
            sheet.paste(v, (256 * i, 0))
        sheet.save(Path(preview_dir) / f"{wand.id}.png")
    return wand


# ------------------------------------------------------------------------------------------------------ the scrolls
# 9x9 glyphs, one per spell: '#' is the glyph colour, '+' a lighter highlight, '.' nothing.
GLYPHS = {
    "firebolt": ["....#....", "...##....", "..###.#..", ".####.##.", ".#####.#.", ".+####.#.", "..+###...", "...+##...", "....#...."],
    "fireball": ["...###...", "..#####..", ".#######.", "####+####", "###+++###", "####+####", ".#######.", "..#####..", "...###..."],
    "meteor": ["......###", ".....####", "....#####", "...+####.", "..+###...", ".+###....", "+###.....", "###......", "#........"],
    "frost_shard": ["....#....", "...###...", "...###...", "..#+##...", "..#+###..", ".##+###..", ".###+###.", "..#####..", "...###..."],
    "blizzard": ["#...#...#", ".#..#..#.", "..#.#.#..", "...###...", "#########", "...###...", "..#.#.#..", ".#..#..#.", "#...#...#"],
    "chain_lightning": ["#.....#..", "##...##..", ".#..#.#..", "..###.#..", "...##.###", "..##.#..#", ".##.##..#", "##.....##", "#.....###"],
    "thunderclap": ["....#....", "...##....", "..##.....", ".#####...", "...##....", "..##.....", ".##......", ".#.......", "#........"],
    "arcane_missiles": ["..#......", ".###..#..", "..#..###.", "......#..", "...#.....", "..###....", "...#..#..", ".....###.", "......#.."],
    "blink": ["....#....", "...###...", "..#.#.#..", "....#....", "....#....", "..#.#.#..", "...###...", "....#....", "....#...."],
    "mana_shield": [".#######.", "#+++++++#", "#+#####+#", "#+#####+#", "#+#####+#", ".#+###+#.", "..#+#+#..", "...###...", "....#...."],
    "healing_pulse": ["...###...", "...###...", "...###...", "#########", "#########", "#########", "...###...", "...###...", "...###..."],
    "entangle": [".####....", "#....#...", "#.##.#...", "#.#..#...", "#.####...", ".#....##.", "..####..#", ".....#..#", "......##."],
}
ELEMENT = {"fire": ("#FF7A2A", "#FFC070", "#8A2A00"), "frost": ("#6FD0FF", "#DFF6FF", "#1A5A8A"), "lightning": ("#FFE040", "#FFFBC0", "#8A6E00"),
           "arcane": ("#B070FF", "#EAD2FF", "#4A1A8A"), "nature": ("#5CD65C", "#C8FFC0", "#1A6A2A")}
SPELL_ELEMENT = {"firebolt": "fire", "fireball": "fire", "meteor": "fire", "frost_shard": "frost", "blizzard": "frost", "chain_lightning": "lightning",
                 "thunderclap": "lightning", "arcane_missiles": "arcane", "blink": "arcane", "mana_shield": "arcane", "healing_pulse": "nature",
                 "entangle": "nature"}


def hexc(h):
    return tuple(int(h[i:i + 2], 16) for i in (1, 3, 5))


def scroll_image(spell):
    """A rolled parchment, tilted like a vanilla item, with an element-coloured border, seal and a glyph."""
    main, light, dark = (hexc(c) for c in ELEMENT[SPELL_ELEMENT[spell]])
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    px = img.load()
    paper = [hexc(c) for c in ("#8A6A3A", "#B8955A", "#D8BC84", "#EAD7A4", "#F6E8BE")]
    # the sheet: x 2..13, y 3..13
    for y in range(3, 14):
        for x in range(2, 14):
            n = fbm(x * 0.6, y * 0.6, 5, 2)
            px[x, y] = paper[2 + (1 if n > 0.55 else 0) + (1 if (x + y) % 9 == 0 else 0) - (1 if n < 0.3 else 0)] + (255,)
    # border in the element colour
    for x in range(2, 14):
        px[x, 3] = main + (255,)
        px[x, 13] = dark + (255,)
    for y in range(3, 14):
        px[2, y] = main + (255,)
        px[13, y] = dark + (255,)
    # rolled ends
    for x in range(1, 15):
        for y, col in ((1, "#C9A56A"), (2, "#A98046"), (14, "#A98046"), (15, "#7A5A2A")):
            px[x, y] = hexc(col) + (255,)
    for y in (1, 2, 14, 15):
        px[1, y] = (0, 0, 0, 0)
        px[14, y] = (0, 0, 0, 0)
    # the glyph, centred in 11x10 (9x9 plus margin)
    for gy, row in enumerate(GLYPHS[spell]):
        for gx, ch in enumerate(row):
            if ch in "#+":
                x, y = 3 + gx + 0, 4 + gy
                px[x, y] = (light if ch == "+" else main) + (255,)
    # wax seal ribbon at the bottom corner
    for (x, y) in ((11, 11), (12, 11), (11, 12), (12, 12)):
        px[x, y] = dark + (255,)
    px[11, 11] = main + (255,)
    return img


def write_scrolls():
    tex = ASSETS / "textures" / "item"
    (ASSETS / "models" / "item").mkdir(parents=True, exist_ok=True)
    (ASSETS / "items").mkdir(parents=True, exist_ok=True)
    for spell in GLYPHS:
        name = f"{spell}_scroll"
        scroll_image(spell).save(tex / f"{name}.png")
        with open(ASSETS / "models" / "item" / f"{name}.json", "w", encoding="utf8", newline="\n") as f:
            json.dump({"parent": "minecraft:item/generated", "textures": {"layer0": f"{NS}:item/{name}"}}, f, indent=2)
            f.write("\n")
        with open(ASSETS / "items" / f"{name}.json", "w", encoding="utf8", newline="\n") as f:
            json.dump({"model": {"type": "minecraft:model", "model": f"{NS}:item/{name}"}}, f, indent=2)
            f.write("\n")


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--preview")
    ap.add_argument("--only", default="")
    args = ap.parse_args()
    only = set(filter(None, args.only.split(",")))
    if args.preview:
        os.makedirs(args.preview, exist_ok=True)
    for cls in WANDS:
        if only and cls.id not in only:
            continue
        write_wand(cls, args.preview)
        print("wand", cls.id)
    write_scrolls()
    print("scrolls", len(GLYPHS))
    if args.preview:
        sheet = Image.new("RGBA", (16 * 12 * 4, 16 * 4), (30, 32, 40, 255))
        for i, spell in enumerate(GLYPHS):
            sheet.paste(scroll_image(spell).resize((64, 64), Image.NEAREST), (i * 64, 0), scroll_image(spell).resize((64, 64), Image.NEAREST))
        sheet.save(Path(args.preview) / "scrolls.png")


if __name__ == "__main__":
    main()
