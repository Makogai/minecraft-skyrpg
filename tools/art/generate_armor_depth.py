#!/usr/bin/env python3
"""Gives worn SkyRPG armour depth, so it looks like its icons instead of a skin-tight print.

    python tools/art/generate_armor_depth.py                  # writes the files below
    python tools/art/generate_armor_depth.py --preview DIR    # also writes 3D contact sheets to DIR

Run it after generate_items.py: that one draws the flat worn layers, this one re-shades them.
Writes, under items/src/main/resources/assets/skyrpg_items/textures/entity/:
  * equipment/humanoid/<set>.png, equipment/humanoid_leggings/<set>.png
        the worn layers from generate_items.ARMOR_SETS, re-shaded so plates and trims read as raised:
        lit top rows and left edges, shaded bottom rows, a dark seam under every trim
  * armor_detail/<set>.png         texture of the set's detail model (brims, crests, pauldrons, cuffs...)
  * armor_detail/<set>_glow.png    pixels of it that glow in the dark, for sets that have any
and items/src/client/java/com/skyrpg/items/client/armor/ArmorDetailMeshes.java, the detail models'
geometry. The geometry lives here, next to the paint, because this script also packs every box onto the
texture; the Java file is generated from it, so never edit that by hand.

How a detail model works: it is a player model with no cubes of its own, plus boxes hanging off the
head, body, arms and legs, so they follow every animation. Each box belongs to one armour piece
(helmet, chest, legs, boots) and only shows while that piece is worn. Boxes are given by their extent in
model pixels, in the coordinates of the part they hang off (as PlayerModel's addBox calls). Vanilla armour
sits 1 pixel outside the skin (leggings 0.5, boots 0.9); details start just inside it and stick out from
there. check() refuses a box that would lie on the same plane as the armour, the skin or another box (it
would flicker in game, though not in the previews), or that reaches into the room accessories are worn in
(docs/design/accessories.md, "Depth lanes").

Each face of a box is painted with the same materials and auto shading as generate_items.py, so the
details match the icons and the worn layers.
"""
import argparse
import math
import sys
import textwrap
from pathlib import Path

from PIL import Image, ImageDraw

sys.dont_write_bytecode = True  # importing generate_items would leave a __pycache__ in the repo
import generate_items as gi  # noqa: E402

ROOT = gi.ROOT
ENTITY_TEXTURES = gi.ITEM_ASSETS / "textures" / "entity"
JAVA_OUT = (ROOT / "items" / "src" / "client" / "java" / "com" / "skyrpg" / "items" / "client" / "armor"
            / "ArmorDetailMeshes.java")


# ---------------------------------------------------------------- re-shaded worn layers
def shades(img, mask, materials):
    """The ramp index every painted pixel of a rendered sheet ended up with."""
    out = [[None] * len(mask[0]) for _ in mask]
    for y, row in enumerate(mask):
        for x, ch in enumerate(row):
            if ch != ".":
                out[y][x] = materials[ch].ramp.index(img.getpixel((x, y))[:3])
    return out


def is_trim(mask, faces, x, y):
    """Whether (x, y) lies in a trim: a band at most two pixels tall running across its whole face."""
    face = faces[y][x]
    if face is None or face[0] not in gi.SIDES or mask[y][x] == ".":
        return False
    _, fx, fy, fw, fh = face
    ch = mask[y][x]
    if any(mask[y][fx + u] != ch for u in range(fw)):
        return False
    top = y
    while top > fy and all(mask[top - 1][fx + u] == ch for u in range(fw)):
        top -= 1
    bottom = y
    while bottom < fy + fh - 1 and all(mask[bottom + 1][fx + u] == ch for u in range(fw)):
        bottom += 1
    return bottom - top < 2 and fh > 3


def bevel(mask, faces):
    """Shade changes that make a worn layer's faces read as bevelled plates with raised trims.

    On every side face the top row and left edge catch the light and the bottom row is in shadow. A trim
    gets one step more contrast at its edges, the pixel under it a dark seam instead of a highlight and the
    pixel over it no shadow, so it looks like a raised band. Small motifs are left as drawn.
    """
    h, w = len(mask), len(mask[0])
    out = [[0] * w for _ in range(h)]
    for y in range(h):
        for x in range(w):
            ch = mask[y][x]
            if ch == "." or faces[y][x] is None:
                continue
            face, fx, fy, fw, fh = faces[y][x]
            u, v = x - fx, y - fy
            delta = 0
            if face in gi.SIDES:
                if v == 0:
                    delta += 1
                elif v == fh - 1:
                    delta -= 1
                if u == 0 and fw > 2:
                    delta += 1
            if v > 0 and mask[y - 1][x] not in (".", ch):
                if is_trim(mask, faces, x, y - 1):
                    delta -= 2  # dark seam under a trim (cancels the usual highlight)
                elif is_trim(mask, faces, x, y):
                    delta += 1  # a trim's top edge
            if v < fh - 1 and mask[y + 1][x] not in (".", ch):
                if is_trim(mask, faces, x, y + 1):
                    delta += 1  # no shadow over a trim
                elif is_trim(mask, faces, x, y):
                    delta -= 1  # a trim's bottom edge
            out[y][x] = delta
    return out


_flat_sheet_render = gi.Sheet.render


def bevelled_sheet_render(sheet, materials):
    """gi.Sheet.render with the bevel pass: renders flat, then pins every pixel's shade with a tweak."""
    flat = _flat_sheet_render(sheet, materials)
    base = shades(flat, sheet.mask, materials)
    deltas = bevel(sheet.mask, sheet.faces)
    tweak = [row[:] for row in sheet.tweak]
    for y, row in enumerate(sheet.mask):
        for x, ch in enumerate(row):
            if ch != "." and not tweak[y][x].isdigit():
                tweak[y][x] = str(max(0, min(4, base[y][x] + deltas[y][x])))
    return gi.render(sheet.mask, materials, tweak, outline=False, faces=sheet.faces)


def worn_layers(set_id, bevelled=True):
    """(outer, inner) worn layers of a set, as generate_items draws them, re-shaded unless told not to."""
    gi.Sheet.render = bevelled_sheet_render if bevelled else _flat_sheet_render
    try:
        return gi.ARMOR_SETS[set_id]()
    finally:
        gi.Sheet.render = _flat_sheet_render


# ---------------------------------------------------------------- detail models
PARTS = ("head", "body", "right_arm", "left_arm", "right_leg", "left_leg")
PIECES = ("helmet", "chest", "legs", "boots")
OPPOSITE = {"right_arm": "left_arm", "left_arm": "right_arm", "right_leg": "left_leg", "left_leg": "right_leg"}
# Face names as generate_items.box uses them, and the Direction each one is in ModelPart.Cube.
DIRECTIONS = {"top": "DOWN", "bottom": "UP", "right": "WEST", "front": "NORTH", "left": "EAST", "back": "SOUTH"}


class Pivot:
    """A hinge for boxes that sit at an angle (a sprig, a plume): position in its part, rotation in degrees."""

    def __init__(self, name, pos, rot):
        self.name, self.pos, self.rot = name, pos, rot

    def mirrored(self):
        x, y, z = self.pos
        rx, ry, rz = self.rot
        return Pivot(self.name + "_m", (-x, y, z), (rx, -ry, -rz))


class Cube:
    """One box: its extent (x0, y0, z0, x1, y1, z1) in model pixels, and how its faces are painted.

    The texture gets round(extent) pixels per side; a box that isn't a whole number of pixels is stretched
    to fit with a CubeDeformation. A side of 0 makes a flat card: give faces= to draw only one side of it
    (the renderer shows both).
    """

    def __init__(self, piece, part, label, extent, paint, tweak=None, faces=None, pivot=None, glow=None):
        assert piece in PIECES and part in PARTS, (piece, part)
        self.piece, self.part, self.label = piece, part, label
        self.extent = extent
        self.paint, self.tweak = paint, tweak
        self.faces = faces
        self.pivot = pivot
        self.glow = glow  # materials whose pixels glow
        self.mirror = False
        self.source = self  # the box whose texture this one shows
        self.uv = None

    @property
    def size(self):
        x0, y0, z0, x1, y1, z1 = self.extent
        return tuple(0 if b == a else max(1, math.floor(b - a + 0.5)) for a, b in ((x0, x1), (y0, y1), (z0, z1)))

    @property
    def grow(self):
        x0, y0, z0, x1, y1, z1 = self.extent
        return tuple(((b - a) - s) / 2 for (a, b), s in zip(((x0, x1), (y0, y1), (z0, z1)), self.size))

    @property
    def origin(self):
        """addBox's corner: the extent's minimum, moved in by the growth."""
        return tuple(a + g for a, g in zip(self.extent[:3], self.grow))

    def visible_faces(self):
        return self.faces or tuple(DIRECTIONS)

    def mirrored(self):
        """The same box on the other side (x flipped), showing this one's texture mirrored."""
        x0, y0, z0, x1, y1, z1 = self.extent
        twin = Cube(self.piece, OPPOSITE.get(self.part, self.part), self.label, (-x1, y0, z0, -x0, y1, z1),
                    self.paint, self.tweak, self.faces, self.pivot and self.pivot.mirrored(), self.glow)
        twin.mirror = not self.mirror
        twin.source = self.source
        return twin


class DetailSet:
    """One set's detail model: its boxes, the materials they're painted with, and a one-line summary."""

    def __init__(self, set_id, materials, summary):
        self.id, self.materials, self.summary = set_id, materials, summary
        self.cubes = []
        self.size = None

    def box(self, piece, part, label, extent, paint, **kw):
        cube = Cube(piece, part, label, extent, paint, **kw)
        self.cubes.append(cube)
        return cube

    def pair(self, piece, part, label, extent, paint, **kw):
        """A box and its mirror image on the other side (other arm or leg, or x flipped)."""
        cube = self.box(piece, part, label, extent, paint, **kw)
        self.cubes.append(cube.mirrored())
        return cube

    def ring(self, piece, part, label, x0, x1, z0, z1, y0, y1, thick, paint, front_thick=None, back_thick=None, **kw):
        """A band of four boxes round a box: outer extent x0..x1 / z0..z1, each side `thick` deep."""
        ft = front_thick or thick
        bt = back_thick or thick
        self.box(piece, part, label + ", front", (x0, y0, z0, x1, y1, z0 + ft), paint, **kw)
        self.box(piece, part, label + ", back", (x0, y0, z1 - bt, x1, y1, z1), paint, **kw)
        self.box(piece, part, label + ", right", (x0, y0, z0 + ft, x0 + thick, y1, z1 - bt), paint, **kw)
        self.box(piece, part, label + ", left", (x1 - thick, y0, z0 + ft, x1, y1, z1 - bt), paint, **kw)


# ---------------------------------------------------------------- painting
def hashed(*args):
    """A small stable hash, for scatter that comes out the same on every run."""
    n = 0
    for a in args:
        n = (n * 1000003 + int(a) * 2654435761 + 97) & 0xFFFFFFFF
    n ^= n >> 15
    n = (n * 2246822519) & 0xFFFFFFFF
    return n ^ (n >> 13)


def paint(default, **faces):
    """A painter: `default` material everywhere, or per face a material, rows of a mask, or a function.

    A function gets (u, v, w, h): the pixel's position on that face and the face's size. Rows must match
    the face size; '.' is transparent. `sides=` sets the four side faces at once, `ends=` top and bottom.
    """
    if "sides" in faces:
        for f in gi.SIDES:
            faces.setdefault(f, faces["sides"])
    if "ends" in faces:
        for f in ("top", "bottom"):
            faces.setdefault(f, faces["ends"])

    def pick(face, u, v, w, h):
        spec = faces.get(face, default)
        if callable(spec):
            return spec(u, v, w, h)
        if isinstance(spec, str):
            return spec
        assert len(spec) == h and all(len(r) == w for r in spec), (face, w, h)
        return spec[v][u]

    return pick


def rows(*bands):
    """Side-face painter by row: rows(("S", 2), ("G", 1), ...) from the top; the last band repeats."""
    def pick(u, v, w, h):
        y = 0
        for ch, n in bands:
            y += n
            if v < y:
                return ch
        return bands[-1][0]
    return pick


def bottom_row(main, edge):
    return lambda u, v, w, h: edge if v == h - 1 else main


def frayed(main, salt=0, edge_rows=1):
    """Fur and straw: the bottom row has ragged gaps, so the edge isn't a ruler-straight line."""
    def pick(u, v, w, h):
        if v >= h - edge_rows and hashed(u, v, w, salt) % 3 == 0:
            return "."
        return main
    return pick


def tweaks(**faces):
    """Like paint(), for the tweak grid: '+' / '-' nudge a shade, a digit pins it."""
    return paint(".", **faces)


def lit_edge(u, v, w, h):
    return "+" if v == 0 else None


def rim_light(u, v, w, h):
    """Top faces: the outer ring a shade lighter, like a rounded edge catching the light."""
    return "+" if u in (0, w - 1) or v in (0, h - 1) else None


def stud(u, v, w, h):
    """A rivet head: one bright pixel in the middle."""
    return "4" if (u, v) == (w // 2, h // 2) else None


# ---------------------------------------------------------------- the starter sets
def harvester():
    m = {"S": gi.STRAW, "F": gi.STRAW.shifted(texture=gi.flat), "Z": gi.STRAW.shifted(base=1),
         "Y": gi.STRAW.shifted(base=3, texture=gi.flat), "G": gi.LEAF.shifted(texture=gi.flat), "W": gi.WHEAT,
         "L": gi.SOIL_LEATHER, "K": gi.SOIL_LEATHER.shifted(base=1, texture=gi.flat)}
    d = DetailSet("harvester", m, "a wide woven brim, a leaf band and a wheat sprig; a leaf collar, a wheat sheaf and "
                                  "rolled sleeves; straw knee patches and a belt; straw boot cuffs and toe caps")
    # Helmet: a domed crown, the wide woven brim just above the eyes, a raised leaf band and a sprig of wheat.
    d.box("helmet", "head", "crown", (-4, -10, -4, 4, -9, 4), paint("S", top="F"), tweak=tweaks(top=rim_light))

    def brim(ring_of):
        """Woven in rings round the crown: a sun-bleached rim, then straw, darker where the crown shades it."""
        def top(u, v, w, h):
            ring = ring_of(u, v, w, h)
            return "Y" if ring == 0 else "S" if ring == 1 else "Z"
        return paint("Z", top=top, front="S", back="S", right="S", left="S")

    weave = tweaks(top=lambda u, v, w, h: "-" if (u + v) % 3 == 0 and (u * v) % 2 else None)
    d.box("helmet", "head", "brim, front", (-7.5, -5, -7.5, 7.5, -4, -4.5), brim(lambda u, v, w, h: h - 1 - v), tweak=weave)
    d.box("helmet", "head", "brim, back", (-7.5, -5, 4.5, 7.5, -4, 7.5), brim(lambda u, v, w, h: v), tweak=weave)
    d.box("helmet", "head", "brim, right", (-7.5, -5, -4.5, -4.5, -4, 4.5), brim(lambda u, v, w, h: u), tweak=weave)
    d.box("helmet", "head", "brim, left", (4.5, -5, -4.5, 7.5, -4, 4.5), brim(lambda u, v, w, h: w - 1 - u), tweak=weave)
    d.ring("helmet", "head", "leaf band", -5.5, 5.5, -5.5, 5.5, -6.5, -5.5, 1, paint("G"), tweak=tweaks(sides=lit_edge))
    sprig = Pivot("wheat_sprig", (5.2, -6, 1.5), (-20, 0, 18))
    ear = ["..W..", ".WWW.", "WWWWW", ".WWW.", ".WWW.", "..W..", "..Z..", "..Z.."]
    lit = ["..4..", ".4...", "4....", ".....", ".....", ".....", ".....", "....."]
    d.box("helmet", "head", "wheat sprig", (-2.5, -8, 0, 2.5, 0, 0), paint(".", front=ear), tweak=tweaks(front=lit),
          faces=("front",), pivot=sprig)
    d.box("helmet", "head", "wheat sprig, across", (0, -8, -2.5, 0, 0, 2.5), paint(".", right=ear), tweak=tweaks(right=lit),
          faces=("right",), pivot=sprig)

    # Chest: a leaf collar round the neck that dips into a V at the front (flat where an amulet hangs),
    # a wheat sheaf tied at the belt, and the sleeves rolled up into straw cuffs above the leaf hems.
    collar = paint("G")
    d.pair("chest", "body", "collar", (-4.4, -1.6, -3.55, -2.2, 0.4, -2.6), collar,
           tweak=tweaks(sides=lit_edge, top=rim_light))
    d.pair("chest", "body", "collar, V", (-2.2, -0.4, -3.25, -0.8, 1.6, -2.6), collar, tweak=tweaks(sides=lit_edge))
    d.pair("chest", "body", "collar, shoulder", (-4.4, -1.6, -2.6, -2.2, -0.6, 2.6), collar, tweak=tweaks(top=lit_edge))
    d.box("chest", "body", "collar, back", (-4.4, -1.6, 2.6, 4.4, 0.2, 3.25), collar, tweak=tweaks(sides=lit_edge))
    d.box("chest", "body", "wheat sheaf", (-1.5, 5.0, -3.6, 1.5, 9.4, -2.6),
          paint("S", front=["W.W", "WWW", "GGG", "ZSZ"], top="W"), tweak=tweaks(front=["4.4", "+4+", "...", "..."]))
    d.pair("chest", "right_arm", "rolled sleeve", (-4.45, 0.4, -3.45, 2.45, 1.6, 3.45), paint("Z", sides="Y"),
           tweak=tweaks(sides=lambda u, v, w, h: "-" if u % 3 == 2 else None))

    # Leggings: stitched straw knee patches, a belt with a buckle. Both stay inside a chestplate's tunic.
    d.pair("legs", "right_leg", "knee patch", (-2.0, 3.1, -3.05, 2.0, 5.9, -2.1),
           paint("S", front=["ZSSZ", "SYYS", "ZSSZ"]), tweak=tweaks(front=lit_edge))
    d.ring("legs", "body", "belt", -4.8, 4.8, -2.8, 2.8, 7.1, 8.1, 0.6, paint("L"))
    d.box("legs", "body", "buckle", (-1, 6.8, -2.85, 1, 8.4, -2.3), paint("Y", sides="Z"), tweak=tweaks(front=["4.", ".-"]))

    # Boots: a straw cuff with a ragged fringe, and a leather toe cap.
    cuff = paint("S", sides=lambda u, v, w, h: "Y" if v == 0 else frayed("S", 5)(u, v, w, h), bottom="Z")
    for leg in ("right_leg", "left_leg"):
        d.ring("boots", leg, "cuff", -3.4, 3.4, -3.4, 3.4, 5.8, 7.8, 0.8, cuff)
    d.pair("boots", "right_leg", "toe cap", (-2.5, 10.3, -3.6, 2.5, 12.8, -2.4), paint("L", front=rows(("L", 2), ("K", 1))),
           tweak=tweaks(top=rim_light, front=lit_edge))
    return d


def prospector():
    m = {"T": gi.SLATE, "D": gi.SLATE.shifted(base=1), "C": gi.COPPER.shifted(texture=gi.polish), "A": gi.AMBER,
         "K": gi.COPPER.shifted(base=1, texture=gi.flat), "R": gi.COPPER.shifted(base=3, texture=gi.flat)}
    d = DetailSet("prospector", m, "a hard hat with a copper ridge, a brim with a deep front peak and an amber lamp that "
                                   "glows; raised copper braces with rivets and shoulder caps; copper knee plates; "
                                   "copper toe caps and cuffs")
    # Helmet: the slate dome, a copper ridge over the top, a hard-hat brim with a deep peak, and the lamp.
    d.box("helmet", "head", "crown", (-4, -10, -4, 4, -9, 4), paint("T"), tweak=tweaks(top=rim_light))
    d.box("helmet", "head", "ridge", (-1, -10.6, -4.6, 1, -9.6, 4.6), paint("C", sides="K"), tweak=tweaks(top=lit_edge))
    d.box("helmet", "head", "ridge, front", (-1, -10.4, -5.6, 1, -8.6, -4.6), paint("C", front=["CC", "KK"]))
    d.box("helmet", "head", "ridge, back", (-1, -10.4, 4.6, 1, -5.4, 5.6), paint("C", back=bottom_row("C", "K")))
    d.ring("helmet", "head", "brim", -6.4, 6.4, -8.4, 6.4, -5.1, -4.1, 1.9, paint("C", sides="K", bottom="K"),
           front_thick=3.9, tweak=tweaks(top=lambda u, v, w, h: "+" if v == h - 1 else None))
    lamp = ["CAAC", "CAAC", "KKKK"]
    d.box("helmet", "head", "lamp", (-2, -8.9, -6.5, 2, -5.9, -4.5), paint("K", front=lamp, top="C"),
          tweak=tweaks(front=[".4..", "....", "...."]), glow="A")

    # Chest: copper braces standing off the jacket (over the shoulders to the back), rivets, a belt with
    # an amber buckle, and copper shoulder caps with one lame each.
    brace = paint("C", top="K")
    d.pair("chest", "body", "brace", (-3.75, -1.6, -3.5, -2.5, 8.3, -2.55), brace)
    d.pair("chest", "body", "brace, over the shoulder", (-3.75, -1.6, -2.55, -2.5, -0.6, 2.55), paint("C"))
    d.pair("chest", "body", "brace, back", (-3.75, -1.6, 2.55, -2.5, 8.3, 3.3), brace)
    d.pair("chest", "body", "rivet", (-3.4, 0.2, -3.8, -2.85, 0.75, -3.4), paint("R"))
    d.ring("chest", "body", "belt", -5.4, 5.4, -3.4, 3.3, 8.3, 9.5, 0.8, paint("C"), tweak=tweaks(sides=lit_edge))
    d.box("chest", "body", "buckle", (-1.0, 7.9, -3.7, 1.0, 9.45, -3.2), paint("K", front=["AA", "AA"]))
    cap = paint("C", bottom="K", sides=bottom_row("C", "K"))
    d.pair("chest", "right_arm", "shoulder cap", (-4.7, -3.7, -3.7, 0.9, -1.3, 3.7), cap,
           tweak=tweaks(top=rim_light, sides=lit_edge))
    d.pair("chest", "right_arm", "lame", (-4.9, -1.3, -3.5, 0.4, 0.3, 3.5), cap, tweak=tweaks(sides=lit_edge))
    d.pair("chest", "right_arm", "cap rivet", (-5.0, -2.8, -0.3, -4.6, -2.2, 0.3), paint("R"))

    # Leggings: copper knee plates with a rivet, and the amber buckle (inside a chestplate's jacket).
    d.pair("legs", "right_leg", "knee plate", (-2.0, 3.8, -3.05, 2.0, 6.2, -2.1), paint("C", front=bottom_row("C", "K")),
           tweak=tweaks(front=stud))
    d.box("legs", "body", "buckle", (-1.1, 7.0, -2.8, 1.1, 9.2, -2.3), paint("K", front=["AA", "AA"]))

    # Boots: copper cuff rings and toe caps.
    for leg in ("right_leg", "left_leg"):
        d.ring("boots", leg, "cuff", -3.3, 3.3, -3.3, 3.3, 5.8, 7.3, 0.7, paint("C", bottom="K"),
               tweak=tweaks(sides=lit_edge))
    d.pair("boots", "right_leg", "toe cap", (-2.6, 9.3, -3.6, 2.6, 12.8, -2.4), paint("C", front=rows(("C", 2), ("K", 2))),
           tweak=tweaks(top=rim_light))
    return d


def woodsman():
    m = {"O": gi.OAK, "K": gi.OAK.shifted(base=1, texture=gi.flat), "M": gi.MOSS, "N": gi.MOSS.shifted(base=3),
         "H": gi.HIDE, "I": gi.IRON.shifted(texture=gi.flat), "J": gi.LEAF}
    d = DetailSet("woodsman", m, "a thick fur roll, hanging ear flaps and an oak-leaf sprig; a fur mantle collar and "
                                 "shoulder pads, a laced front, a hide belt with an iron buckle; leather knee patches; "
                                 "fur boot cuffs and laces")
    # Helmet: leather crown, a thick roll of moss fur, fur ear flaps and an oak leaf tucked in.
    d.box("helmet", "head", "crown", (-4, -10, -4, 4, -9, 4), paint("O"),
          tweak=tweaks(top=lambda u, v, w, h: "-" if u == 3 or v == 4 else None))
    fur = paint("M", sides=frayed("M", 1), top="N")
    d.ring("helmet", "head", "fur roll", -6, 6, -6, 6, -6.7, -3.8, 1.5, fur, tweak=tweaks(sides=lit_edge))
    d.pair("helmet", "head", "ear flap", (-6.1, -4.0, -2.1, -5.0, 0.7, 2.1), paint("M", sides=frayed("M", 2)))
    leaf = Pivot("oak_leaf", (5.6, -6.5, 2), (-25, 0, 20))
    leaf_mask = ["..J..", ".JJJ.", "JJJJJ", ".JJJ.", "JJJJJ", ".JJJ.", "..O.."]
    d.box("helmet", "head", "oak leaf", (-2.5, -7, 0, 2.5, 0, 0), paint(".", front=leaf_mask),
          tweak=tweaks(front=["..4..", ".4...", "4....", "....-", "....-", "...-.", "....."]), faces=("front",), pivot=leaf)

    # Chest: a fur mantle round the collar (flat where an amulet hangs), fur shoulder pads, a laced front
    # and a hide belt with an iron buckle.
    d.pair("chest", "body", "mantle", (-5.6, -1.7, -3.6, -2.0, 0.9, 3.3), fur, tweak=tweaks(sides=lit_edge))
    d.box("chest", "body", "mantle, front", (-2.0, -1.4, -3.3, 2.0, 0.4, -2.5), paint("M", front=frayed("M", 3)))
    d.box("chest", "body", "mantle, back", (-2.0, -1.7, 2.4, 2.0, 0.9, 3.3), fur)
    d.pair("chest", "right_arm", "shoulder pad", (-4.8, -3.8, -3.7, 0.9, -0.6, 3.7), fur,
           tweak=tweaks(top=rim_light, sides=lit_edge))
    lacing = ["H.H", ".H.", "H.H", ".H.", "H.H", ".H."]
    d.box("chest", "body", "laced front", (-1.5, 1.2, -3.3, 1.5, 7.4, -2.6), paint("O", front=lacing, ends="O"),
          tweak=tweaks(front=lambda u, v, w, h: "+" if u == 1 else None))
    d.ring("chest", "body", "belt", -5.4, 5.4, -3.4, 3.3, 8.3, 9.5, 0.8, paint("H"), tweak=tweaks(sides=lit_edge))
    d.box("chest", "body", "buckle", (-1.0, 7.9, -3.7, 1.0, 9.45, -3.2), paint("I", front=["II", "I."]),
          tweak=tweaks(front=["4.", ".."]))

    # Leggings: stitched leather knee patches.
    d.pair("legs", "right_leg", "knee patch", (-1.8, 3.9, -3.0, 1.8, 6.1, -2.1), paint("O"),
           tweak=tweaks(front=lambda u, v, w, h: "-" if u in (0, w - 1) and v % 2 == 0 else None))

    # Boots: thick fur cuffs and crossed laces on the front.
    for leg in ("right_leg", "left_leg"):
        d.ring("boots", leg, "fur cuff", -3.6, 3.6, -3.6, 3.6, 5.4, 8.4, 1.0, paint("M", sides=frayed("M", 4), top="N"),
               tweak=tweaks(sides=lit_edge))
    d.pair("boots", "right_leg", "laces", (-1.5, 8.4, -3.2, 1.5, 11.0, -2.6), paint("O", front=["H.H", ".H.", "H.H"]))
    return d


def vanguard():
    m = {"E": gi.STEEL.shifted(texture=gi.polish), "P": gi.STEEL.shifted(texture=gi.flat),
         "D": gi.STEEL.shifted(base=1, texture=gi.flat), "R": gi.CRIMSON, "Q": gi.CRIMSON.shifted(base=3),
         "U": gi.GOLD.shifted(texture=gi.flat)}
    d = DetailSet("vanguard", m, "a crimson plume on a gold crest, a gold brow ridge, nose and cheek guards; "
                                 "two-plate gold-trimmed pauldrons, a centre keel and a raised chevron; knee cops "
                                 "and crimson tassets; layered sabatons with gold cuffs")
    # Helmet: a gold crest holder carrying a stepped crimson plume that sweeps down the back, a gold brow
    # ridge round the helm, a nose guard and two cheek guards.
    d.box("helmet", "head", "crest", (-1, -9.8, -5.4, 1, -8.8, 5.4), paint("U"), tweak=tweaks(top=lit_edge))
    plume = paint("R", top="Q", sides=lambda u, v, w, h: "Q" if v == 0 else "R")
    d.box("helmet", "head", "plume, front", (-0.5, -11.8, -4.5, 0.5, -9.6, -1.5), plume)
    d.box("helmet", "head", "plume, crown", (-0.5, -12.8, -1.5, 0.5, -9.6, 2.5), plume)
    d.box("helmet", "head", "plume, back", (-0.5, -12.2, 2.5, 0.5, -9.6, 5.5), plume)
    d.box("helmet", "head", "plume, tail", (-0.5, -11.2, 5.5, 0.5, -7.2, 7.0), plume)
    d.ring("helmet", "head", "brow ridge", -5.5, 5.5, -5.6, 5.5, -6.6, -5.3, 1, paint("U"), front_thick=1.1,
           tweak=tweaks(sides=lit_edge))
    d.box("helmet", "head", "nose guard", (-0.6, -5.4, -5.8, 0.6, -2.4, -4.6), paint("E", front=bottom_row("E", "U")))
    cheek = paint("E", sides=bottom_row("E", "U"))
    d.pair("helmet", "head", "cheek guard", (-5.6, -5.3, -5.6, -3.6, -1.2, -1.0), cheek, tweak=tweaks(sides=lit_edge))

    # Chest: two-plate pauldrons with gold trim, a raised gold chevron on the tabard (flat where an amulet
    # hangs) with a gold keel running down from its point, and a gold belt.
    plate = paint("P", bottom="D", sides=bottom_row("P", "U"))
    d.pair("chest", "right_arm", "pauldron", (-4.9, -3.9, -3.9, 1.2, -1.1, 3.9), plate,
           tweak=tweaks(top=rim_light, sides=lit_edge))
    d.pair("chest", "right_arm", "pauldron, lower plate", (-5.2, -1.4, -3.6, 0.5, 0.9, 3.6), plate,
           tweak=tweaks(sides=lit_edge))
    d.box("chest", "body", "chevron", (-2.6, 2.5, -3.3, -1.2, 3.7, -2.6), paint("U"))
    d.box("chest", "body", "chevron, right", (1.2, 2.5, -3.3, 2.6, 3.7, -2.6), paint("U"))
    d.box("chest", "body", "chevron, point", (-1.25, 3.7, -3.3, 1.25, 5.0, -2.6), paint("U"))
    d.box("chest", "body", "keel", (-0.6, 5.0, -3.55, 0.6, 9.5, -2.6), paint("U"), tweak=tweaks(front=lit_edge))
    d.ring("chest", "body", "belt", -5.35, 5.35, -3.35, 3.3, 7.2, 8.3, 0.7, paint("U"), tweak=tweaks(sides=lit_edge))

    # Leggings: knee cops with a gold rivet and a side wing, crimson tassets over the thighs.
    d.pair("legs", "right_leg", "knee cop", (-2.2, 3.4, -3.2, 2.2, 6.4, -2.1), paint("P", front=bottom_row("P", "U")),
           tweak=tweaks(front=stud, top=rim_light))
    d.pair("legs", "right_leg", "knee wing", (-3.0, 3.8, -2.2, -2.1, 6.0, 0.8), paint("P", sides=bottom_row("P", "U")))
    tasset = paint("R", sides=bottom_row("R", "U"), bottom="U")
    d.pair("legs", "right_leg", "tasset", (-2.8, 0.6, -3.2, 2.8, 3.4, -2.2), tasset, tweak=tweaks(sides=lit_edge))
    d.pair("legs", "right_leg", "tasset, side", (-3.2, 0.6, -2.2, -2.2, 3.4, 2.2), tasset)

    # Boots: layered sabaton plates down to the toe and a gold cuff.
    for leg in ("right_leg", "left_leg"):
        d.ring("boots", leg, "cuff", -3.3, 3.3, -3.3, 3.3, 5.9, 7.2, 0.7, paint("U"), tweak=tweaks(sides=lit_edge))
    sabaton = paint("P", front=bottom_row("P", "D"))
    # Overlapping plates never share a side: each is a little wider or narrower than the next.
    top_lit = tweaks(top=rim_light)
    d.pair("boots", "right_leg", "sabaton", (-2.6, 8.8, -3.4, 2.6, 10.4, -2.5), sabaton, tweak=top_lit)
    d.pair("boots", "right_leg", "sabaton, middle", (-2.75, 10.2, -3.8, 2.75, 11.6, -2.4), sabaton, tweak=top_lit)
    d.pair("boots", "right_leg", "sabaton, toe", (-2.45, 11.4, -4.2, 2.45, 12.8, -2.6), sabaton, tweak=top_lit)
    return d


# ---------------------------------------------------------------- mining sets
def delver():
    m = {"T": gi.DELVE_LEATHER, "D": gi.DELVE_LEATHER.shifted(base=1), "P": gi.DELVE_LEATHER.shifted(base=3, texture=gi.flat),
         "C": gi.BRASS.shifted(texture=gi.polish), "K": gi.BRASS.shifted(base=1, texture=gi.flat),
         "R": gi.BRASS.shifted(base=3, texture=gi.flat), "A": gi.AMBER}
    d = DetailSet("delver", m, "a two-step brass dome with a boxed lamp that glows under a reflector hood, a brass band, "
                               "ear flaps and a neck guard; leather braces with brass studs, a patch, a hip pouch and "
                               "shoulder pads; brass-rimmed knee pads, a belt buckle and a glowing hip lantern; buckled "
                               "boot cuffs and toe caps")
    # Helmet: a brass dome, a band, and the lamp: a glass box under a reflector hood on the brow.
    d.box("helmet", "head", "crown", (-4, -10, -4, 4, -9, 4), paint("C"), tweak=tweaks(top=rim_light))
    d.box("helmet", "head", "dome", (-3, -11, -3, 3, -10, 3), paint("C", top="K"), tweak=tweaks(top=rim_light))
    d.ring("helmet", "head", "brass band", -5.5, 5.5, -5.5, 5.5, -6.6, -5.6, 1, paint("K"), tweak=tweaks(sides=lit_edge))
    lamp = ["KAAAK", "KAAAK", "KKKKK"]
    d.box("helmet", "head", "lamp", (-2.5, -8.9, -6.5, 2.5, -5.9, -4.6), paint("K", front=lamp, top="C"),
          tweak=tweaks(front=["..4..", ".....", "....."]), glow="A")
    d.box("helmet", "head", "reflector hood", (-3, -9.9, -7, 3, -8.9, -4), paint("C", sides="K"), tweak=tweaks(top=lit_edge))
    flap = paint("T", sides=bottom_row("T", "D"))
    d.pair("helmet", "head", "ear flap", (-6.1, -4.0, -2.1, -5.0, 0.7, 2.1), flap, tweak=tweaks(sides=lit_edge))
    d.box("helmet", "head", "neck guard", (-3.5, -4.0, 5.1, 3.5, 0.6, 6.0), paint("T", back=bottom_row("T", "D")),
          tweak=tweaks(back=lit_edge))

    # Chest: a leather collar, braces over the shoulders studded with brass, a patch, a hip pouch, shoulder pads.
    leather = paint("T", top="D")
    d.pair("chest", "body", "collar", (-4.4, -1.6, -3.55, -2.2, 0.4, -2.6), leather, tweak=tweaks(sides=lit_edge, top=rim_light))
    d.pair("chest", "body", "collar, V", (-2.2, -0.4, -3.25, -0.8, 1.6, -2.6), leather, tweak=tweaks(sides=lit_edge))
    d.pair("chest", "body", "collar, shoulder", (-4.4, -1.6, -2.6, -2.2, -0.6, 2.6), leather, tweak=tweaks(top=lit_edge))
    d.box("chest", "body", "collar, back", (-4.4, -1.6, 2.6, 4.4, 0.2, 3.25), leather, tweak=tweaks(sides=lit_edge))
    brace = paint("D", top="T")
    d.pair("chest", "body", "brace", (-3.75, 1.4, -3.5, -2.5, 8.3, -2.55), brace)
    d.pair("chest", "body", "brace, back", (-3.75, -0.6, 2.55, -2.5, 8.3, 3.3), brace)
    d.pair("chest", "body", "stud", (-3.4, 2.6, -3.8, -2.85, 3.15, -3.4), paint("R"))
    d.pair("chest", "body", "stud, low", (-3.4, 5.6, -3.8, -2.85, 6.15, -3.4), paint("R"))
    d.box("chest", "body", "patch", (0.5, 5.5, -3.3, 2.5, 7.5, -2.6), paint("P"),
          tweak=tweaks(front=lambda u, v, w, h: "-" if u in (0, w - 1) or v in (0, h - 1) else None))
    d.box("chest", "body", "hip pouch", (-6.0, 5.5, -2, -5.0, 9.5, 2),
          paint("T", right=["DDDD", "DRRD", "TTTT", "TTTT"], top="D"), tweak=tweaks(right=lit_edge))
    pad = paint("T", sides=bottom_row("T", "D"), top="D")
    d.pair("chest", "right_arm", "shoulder pad", (-4.7, -3.7, -3.6, 0.8, -1.5, 3.6), pad,
           tweak=tweaks(top=rim_light, sides=lit_edge))
    d.pair("chest", "right_arm", "pad stud", (-5.0, -2.8, -0.3, -4.6, -2.2, 0.3), paint("R"))

    # Leggings: brass-rimmed knee pads over leather straps, a belt with a buckle, a glowing hip lantern.
    d.pair("legs", "right_leg", "knee pad", (-2.0, 3.5, -3.2, 2.0, 6.5, -2.1),
           paint("C", front=["CCCC", "CDDC", "CCCC"], sides="K"), tweak=tweaks(top=rim_light, front=lit_edge))
    d.pair("legs", "right_leg", "knee strap", (-2.1, 2.3, -3.0, 2.1, 3.3, -2.2), paint("D"), tweak=tweaks(sides=lit_edge))
    d.ring("legs", "body", "belt", -4.8, 4.8, -2.8, 2.8, 7.1, 8.1, 0.6, paint("D"), tweak=tweaks(sides=lit_edge))
    d.box("legs", "body", "buckle", (-1.1, 6.9, -2.85, 1.1, 8.5, -2.3), paint("C", sides="K"), tweak=tweaks(front=["4.", ".."]))
    d.box("legs", "body", "lantern", (4.9, 7.4, -1, 6.0, 9.4, 1), paint("K", sides="A"), glow="A")
    d.box("legs", "body", "lantern, handle", (5.1, 6.8, -0.5, 5.8, 7.4, 0.5), paint("K"))

    # Boots: a folded leather cuff with a brass buckle, and a leather toe cap.
    for leg in ("right_leg", "left_leg"):
        d.ring("boots", leg, "cuff", -3.4, 3.4, -3.4, 3.4, 5.8, 7.6, 0.8, paint("D", bottom="T"), tweak=tweaks(sides=lit_edge))
    d.pair("boots", "right_leg", "buckle", (-0.8, 6.0, -3.8, 0.8, 7.4, -3.4), paint("C", sides="K"))
    d.pair("boots", "right_leg", "toe cap", (-2.5, 10.3, -3.6, 2.5, 12.8, -2.4), paint("T", front=rows(("T", 2), ("D", 1))),
           tweak=tweaks(top=rim_light, front=lit_edge))
    return d


def skyforged():
    m = {"E": gi.SKYIRON.shifted(texture=gi.polish), "P": gi.SKYIRON.shifted(texture=gi.flat),
         "D": gi.SKYIRON.shifted(base=1, texture=gi.flat), "F": gi.SKYIRON.shifted(base=4, texture=gi.flat),
         "G": gi.SKYIRON.shifted(base=3, texture=gi.flat), "U": gi.GOLD.shifted(texture=gi.flat), "R": gi.PRISM_GLOW}
    d = DetailSet("skyforged", m, "a gold crest fin and brow ridge, a prism gem and a fan-shaped wing of four feathers on each "
                                  "temple; three stacked gold-rimmed pauldrons, a gold gorget and belt, a prism gem with "
                                  "gold wings on the breast; winged knee cops and gold-hemmed tassets; layered sabatons "
                                  "with gold cuffs and a feather wing at each ankle")
    # Helmet: a crest fin along the top, a gold brow ridge with a glowing prism gem, and a wing on each temple.
    fin = paint("F", top="G", sides=lambda u, v, w, h: "G" if v == 0 else "F")
    d.box("helmet", "head", "crest", (-1, -9.8, -5.4, 1, -8.8, 5.4), paint("U"), tweak=tweaks(top=lit_edge))
    d.box("helmet", "head", "fin, front", (-0.5, -10.8, -4.5, 0.5, -9.8, -1.5), fin)
    d.box("helmet", "head", "fin, crown", (-0.5, -11.8, -1.5, 0.5, -9.8, 2.5), fin)
    d.box("helmet", "head", "fin, back", (-0.5, -11.0, 2.5, 0.5, -9.8, 5.5), fin)
    d.ring("helmet", "head", "brow ridge", -5.5, 5.5, -5.6, 5.5, -6.6, -5.3, 1, paint("U"), front_thick=1.1,
           tweak=tweaks(sides=lit_edge))
    d.box("helmet", "head", "prism gem", (-1, -8.4, -5.9, 1, -6.4, -4.9), paint("R"), tweak=tweaks(front=["4.", ".."]), glow="R")
    d.box("helmet", "head", "nose guard", (-0.6, -5.4, -5.8, 0.6, -2.4, -4.6), paint("E", front=bottom_row("E", "U")))
    d.pair("helmet", "head", "cheek guard", (-5.6, -5.3, -5.6, -3.6, -1.2, -1.0), paint("E", sides=bottom_row("E", "U")),
           tweak=tweaks(sides=lit_edge))
    feather = paint("F", top="G", bottom="G", back="U", right="F")
    ribs = tweaks(right=lambda u, v, w, h: "-" if u % 3 == 2 else None)
    # The wing is a fan of four feathers hinged at the temple, the lowest nearly level and each one steeper than the last.
    tip = paint("F", top="G", bottom="G", right="U")
    for i, (length, tilt) in enumerate(((6, 8), (5, 30), (4, 52), (3, 74))):
        hinge = Pivot(f"wing_{i + 1}", (-5.0, -5.0, 0.5), (0, 0, tilt))
        d.pair("helmet", "head", f"wing, feather {i + 1}", (-length, -0.7, -1.5, 0, 0.7, 2.0), tip, tweak=ribs, pivot=hinge)

    # Chest: a gold gorget, three stacked pauldrons, a prism gem with gold wings on the breast, a gold belt.
    gold = paint("U")
    d.pair("chest", "body", "gorget", (-4.4, -1.6, -3.55, -2.2, 0.4, -2.6), gold, tweak=tweaks(sides=lit_edge, top=rim_light))
    d.pair("chest", "body", "gorget, V", (-2.2, -0.4, -3.25, -0.8, 1.6, -2.6), gold, tweak=tweaks(sides=lit_edge))
    d.pair("chest", "body", "gorget, shoulder", (-4.4, -1.6, -2.6, -2.2, -0.6, 2.6), gold, tweak=tweaks(top=lit_edge))
    d.box("chest", "body", "gorget, back", (-4.4, -1.6, 2.6, 4.4, 0.2, 3.25), gold, tweak=tweaks(sides=lit_edge))
    plate = paint("P", bottom="D", sides=bottom_row("P", "U"))
    d.pair("chest", "right_arm", "pauldron", (-5.0, -4.2, -3.8, 1.4, -2.2, 3.8), plate, tweak=tweaks(top=rim_light, sides=lit_edge))
    d.pair("chest", "right_arm", "pauldron, middle", (-5.4, -2.6, -4.0, 0.8, -0.6, 4.0), plate, tweak=tweaks(sides=lit_edge))
    d.pair("chest", "right_arm", "pauldron, lower", (-5.8, -1.0, -4.2, 0.4, 1.2, 4.2), plate, tweak=tweaks(sides=lit_edge))
    d.box("chest", "body", "breast gem", (-1, 2.2, -3.3, 1, 4.2, -2.6), paint("R"), tweak=tweaks(front=["4.", ".."]), glow="R")
    d.pair("chest", "body", "wing bar", (1, 2, -3.3, 4, 3, -2.6), gold, tweak=tweaks(front=lit_edge))
    d.pair("chest", "body", "wing bar, middle", (1, 3, -3.3, 3, 4, -2.6), gold)
    d.pair("chest", "body", "wing bar, low", (1, 4, -3.3, 2, 5, -2.6), gold)
    d.ring("chest", "body", "belt", -5.35, 5.35, -3.35, 3.3, 7.2, 8.3, 0.7, gold, tweak=tweaks(sides=lit_edge))

    # Leggings: knee cops with a feather wing, gold-hemmed tassets over the thighs.
    d.pair("legs", "right_leg", "knee cop", (-2.2, 3.4, -3.2, 2.2, 6.4, -2.1), paint("P", front=bottom_row("P", "U")),
           tweak=tweaks(front=stud, top=rim_light))
    d.pair("legs", "right_leg", "knee wing", (-3.4, 3.6, -2.0, -2.4, 4.6, 1.0), feather, tweak=ribs)
    d.pair("legs", "right_leg", "knee wing, low", (-3.7, 4.6, -2.0, -2.7, 5.6, 2.0), feather, tweak=ribs)
    tasset = paint("P", sides=bottom_row("P", "U"), bottom="U")
    d.pair("legs", "right_leg", "tasset", (-2.8, 0.6, -3.2, 2.8, 3.4, -2.2), tasset, tweak=tweaks(sides=lit_edge))
    d.pair("legs", "right_leg", "tasset, side", (-3.2, 0.6, -2.2, -2.2, 3.4, 2.2), tasset)

    # Boots: a gold cuff, three layered sabaton plates and a three-feather wing at each ankle.
    for leg in ("right_leg", "left_leg"):
        d.ring("boots", leg, "cuff", -3.3, 3.3, -3.3, 3.3, 5.9, 7.2, 0.7, gold, tweak=tweaks(sides=lit_edge))
    sabaton = paint("P", front=bottom_row("P", "D"))
    top_lit = tweaks(top=rim_light)
    d.pair("boots", "right_leg", "sabaton", (-2.6, 8.8, -3.4, 2.6, 10.4, -2.5), sabaton, tweak=top_lit)
    d.pair("boots", "right_leg", "sabaton, middle", (-2.75, 10.2, -3.8, 2.75, 11.6, -2.4), sabaton, tweak=top_lit)
    d.pair("boots", "right_leg", "sabaton, toe", (-2.45, 11.4, -4.2, 2.45, 12.8, -2.6), sabaton, tweak=top_lit)
    d.pair("boots", "right_leg", "ankle wing", (-4.4, 7.4, -1.0, -3.2, 8.4, 3.0), feather, tweak=ribs)
    d.pair("boots", "right_leg", "ankle wing, middle", (-4.8, 6.4, -1.0, -3.6, 7.4, 4.5), feather, tweak=ribs)
    d.pair("boots", "right_leg", "ankle wing, top", (-5.2, 5.4, -1.0, -4.0, 6.4, 6.0), feather, tweak=ribs)
    return d


def deepwarden():
    m = {"E": gi.DEEP_STEEL.shifted(texture=gi.polish), "P": gi.DEEP_STEEL.shifted(texture=gi.flat),
         "D": gi.DEEP_STEEL.shifted(base=1, texture=gi.flat), "H": gi.DEEP_STEEL.shifted(base=1, texture=gi.cloth),
         "U": gi.IRON.shifted(texture=gi.flat), "R": gi.TEAL_GLOW}
    d = DetailSet("deepwarden", m, "a hood with a pointed back, drapes and a brow lip over a plated visor with a glowing slit; "
                                   "a gorget, rune-lit breast plates and heavy pauldrons with a glowing rune; rune-lit "
                                   "knee plates, tassets and an iron belt; iron cuffs with runes and three-plate sabatons")
    # Helmet: the hood's drapes and point, a visor in two plates with a glowing slit between them, an iron ridge.
    d.box("helmet", "head", "crown", (-4, -10, -4, 4, -9, 4), paint("H"), tweak=tweaks(top=rim_light))
    d.box("helmet", "head", "ridge", (-0.5, -10.6, -4.8, 0.5, -9.6, 4.8), paint("U"), tweak=tweaks(top=lit_edge))
    drape = paint("H", bottom="D", sides=bottom_row("H", "D"))
    d.box("helmet", "head", "hood, back", (-5.6, -8.6, 3.6, 5.6, -1.0, 6.2), drape, tweak=tweaks(sides=lit_edge))
    d.pair("helmet", "head", "hood, side", (-5.8, -8.6, -4.4, -4.8, -2.0, 3.6), drape, tweak=tweaks(sides=lit_edge))
    d.box("helmet", "head", "hood, lip", (-5.6, -9.2, -5.8, 5.6, -6.6, -4.6), paint("H", front=bottom_row("H", "D")),
          tweak=tweaks(front=lit_edge))
    d.box("helmet", "head", "hood, point", (-1.5, -9.6, 4.5, 1.5, -6.0, 7.5), paint("H", bottom="D"), tweak=tweaks(sides=lit_edge))
    d.box("helmet", "head", "visor, upper", (-4.5, -6.4, -5.8, 4.5, -4.2, -4.6), paint("E", front=bottom_row("E", "D")),
          tweak=tweaks(front=lit_edge))
    d.box("helmet", "head", "visor, lower", (-4.5, -2.8, -5.8, 4.5, -0.6, -4.6),
          paint("P", front=["PPDPPPDPP", "PPDPPPDPP"]), tweak=tweaks(front=lit_edge))
    d.box("helmet", "head", "visor slit", (-4, -4.2, -5.3, 4, -2.8, -4.8), paint("R"), tweak=tweaks(front=lambda u, v, w, h: "+"),
          glow="R")

    # Chest: a plated gorget, two breast plates round a glowing rune, heavy two-plate pauldrons, an iron belt.
    plate = paint("P", sides=bottom_row("P", "U"))
    d.pair("chest", "body", "gorget", (-5.6, -1.7, -3.6, -2.0, 0.9, 3.3), plate, tweak=tweaks(sides=lit_edge))
    d.box("chest", "body", "gorget, front", (-2.0, -1.4, -3.3, 2.0, 0.4, -2.5), plate)
    d.box("chest", "body", "gorget, back", (-2.0, -1.7, 2.4, 2.0, 0.9, 3.3), plate)
    d.pair("chest", "body", "breast plate", (-3.5, 1.0, -3.2, -0.5, 6.0, -2.6), paint("P", front=bottom_row("P", "U")),
           tweak=tweaks(front=lit_edge))
    d.box("chest", "body", "rune, stem", (-0.5, 1.5, -3.3, 0.5, 6.5, -2.8), paint("R"), glow="R")
    d.box("chest", "body", "rune, bar", (-1.5, 2.5, -3.25, 1.5, 3.5, -2.85), paint("R"), glow="R")
    heavy = paint("P", bottom="D", sides=bottom_row("P", "U"))
    d.pair("chest", "right_arm", "pauldron", (-5.0, -4.0, -4.0, 1.3, -1.0, 4.0), heavy, tweak=tweaks(top=rim_light, sides=lit_edge))
    d.pair("chest", "right_arm", "pauldron, lower", (-5.4, -1.0, -3.8, 0.8, 1.5, 3.8), heavy, tweak=tweaks(sides=lit_edge))
    d.pair("chest", "right_arm", "pauldron rune", (-3.5, -4.5, -0.5, -0.5, -4.0, 0.5), paint("R"), glow="R")
    d.ring("chest", "body", "belt", -5.4, 5.4, -3.4, 3.3, 8.3, 9.5, 0.8, paint("U"), tweak=tweaks(sides=lit_edge))

    # Leggings: knee plates with a glowing rune, tassets, an iron belt with a glowing clasp.
    d.pair("legs", "right_leg", "knee plate", (-2.2, 3.3, -3.2, 2.2, 6.3, -2.1), paint("P", front=bottom_row("P", "U")),
           tweak=tweaks(front=stud, top=rim_light))
    d.pair("legs", "right_leg", "knee rune", (-0.5, 3.8, -3.5, 0.5, 5.8, -3.2), paint("R"), glow="R")
    tasset = paint("P", sides=bottom_row("P", "U"), bottom="U")
    d.pair("legs", "right_leg", "tasset", (-2.8, 0.6, -3.2, 2.8, 3.3, -2.2), tasset, tweak=tweaks(sides=lit_edge))
    d.pair("legs", "right_leg", "tasset, side", (-3.2, 0.6, -2.2, -2.2, 3.3, 2.2), tasset)
    d.ring("legs", "body", "belt", -4.8, 4.8, -2.8, 2.8, 7.1, 8.1, 0.6, paint("U"), tweak=tweaks(sides=lit_edge))
    d.box("legs", "body", "clasp", (-1.0, 6.9, -2.85, 1.0, 8.3, -2.3), paint("R"), glow="R")

    # Boots: iron cuffs with a glowing rune, three layered sabaton plates.
    for leg in ("right_leg", "left_leg"):
        d.ring("boots", leg, "cuff", -3.3, 3.3, -3.3, 3.3, 5.9, 7.2, 0.7, paint("U"), tweak=tweaks(sides=lit_edge))
    d.pair("boots", "right_leg", "cuff rune", (-0.5, 6.1, -3.7, 0.5, 7.0, -3.3), paint("R"), glow="R")
    sabaton = paint("P", front=bottom_row("P", "D"))
    top_lit = tweaks(top=rim_light)
    d.pair("boots", "right_leg", "sabaton", (-2.6, 8.8, -3.4, 2.6, 10.4, -2.5), sabaton, tweak=top_lit)
    d.pair("boots", "right_leg", "sabaton, middle", (-2.75, 10.2, -3.8, 2.75, 11.6, -2.4), sabaton, tweak=top_lit)
    d.pair("boots", "right_leg", "sabaton, toe", (-2.45, 11.4, -4.2, 2.45, 12.8, -2.6), sabaton, tweak=top_lit)
    return d


def emberheart():
    m = {"T": gi.OBSIDIAN, "D": gi.OBSIDIAN.shifted(base=1, texture=gi.flat), "C": gi.EMBER.shifted(texture=gi.flat),
         "K": gi.EMBER.shifted(base=1, texture=gi.flat), "A": gi.HEART}
    d = DetailSet("emberheart", m, "curved obsidian horns with glowing tips, glowing eyes, cheek guards and a heartstone on the "
                                   "brow; a big glowing heartstone in an ember frame on the chest, spiked pauldrons; spiked "
                                   "knee plates, tassets and a heartstone clasp; ember cuffs and spiked sabatons")
    # Helmet: two curved horns tipped with ember, glowing eyes, cheek guards and a heartstone on the brow.
    d.box("helmet", "head", "crown", (-4, -10, -4, 4, -9, 4), paint("T"), tweak=tweaks(top=rim_light))
    d.ring("helmet", "head", "brow ridge", -5.5, 5.5, -5.6, 5.5, -6.6, -5.3, 1, paint("T"), front_thick=1.1,
           tweak=tweaks(sides=lit_edge))
    d.box("helmet", "head", "heartstone", (-1, -8.4, -5.9, 1, -6.4, -4.9), paint("A"), tweak=tweaks(front=["4.", ".."]), glow="A")
    d.pair("helmet", "head", "eye", (-3.6, -4.1, -5.3, -1.2, -2.9, -4.9), paint("C"), tweak=tweaks(front=lambda u, v, w, h: "+"),
           glow="C")
    d.pair("helmet", "head", "cheek guard", (-5.6, -5.3, -5.6, -3.6, -1.2, -1.0), paint("T", sides=bottom_row("T", "K")),
           tweak=tweaks(sides=lit_edge))
    horn = Pivot("horn", (-4.0, -8.4, -0.5), (-12, 0, -24))
    d.pair("helmet", "head", "horn", (-1.5, -3, -1.5, 1.5, 0, 1.5), paint("T", top="K"), pivot=horn)
    d.pair("helmet", "head", "horn, middle", (-1, -6, -1, 1, -3, 1), paint("T"), pivot=horn)
    d.pair("helmet", "head", "horn, tip", (-0.5, -8, -0.5, 0.5, -6, 0.5), paint("C"), pivot=horn, glow="C")

    # Chest: a big heartstone in an ember frame, spiked obsidian pauldrons with ember-edged lames, an ember belt.
    d.box("chest", "body", "heartstone", (-1.5, 2.0, -3.3, 1.5, 5.0, -2.6), paint("A"), tweak=tweaks(front=["4..", "...", "..."]),
          glow="A")
    d.box("chest", "body", "frame, top", (-2.5, 1.0, -3.2, 2.5, 2.0, -2.6), paint("K"), tweak=tweaks(front=lit_edge))
    d.box("chest", "body", "frame, bottom", (-2.5, 5.0, -3.2, 2.5, 6.0, -2.6), paint("K"))
    d.pair("chest", "body", "frame, side", (-2.5, 2.0, -3.2, -1.5, 5.0, -2.6), paint("K"))
    plate = paint("T", bottom="D", sides=bottom_row("T", "K"))
    d.pair("chest", "right_arm", "pauldron", (-4.8, -3.9, -3.8, 1.2, -1.1, 3.8), plate, tweak=tweaks(top=rim_light, sides=lit_edge))
    d.pair("chest", "right_arm", "pauldron, lower", (-5.1, -1.4, -3.5, 0.4, 0.9, 3.5), plate, tweak=tweaks(sides=lit_edge))
    d.pair("chest", "right_arm", "spike", (-3.8, -6.6, -1.0, -1.8, -3.9, 1.0), paint("T", top="C"), glow="C")
    d.ring("chest", "body", "belt", -5.4, 5.4, -3.4, 3.3, 8.3, 9.5, 0.8, paint("K"), tweak=tweaks(sides=lit_edge))

    # Leggings: spiked knee plates with ember rims, ragged tassets, a heartstone clasp.
    d.pair("legs", "right_leg", "knee plate", (-2.2, 3.6, -3.2, 2.2, 6.2, -2.1), paint("T", front=bottom_row("T", "K")),
           tweak=tweaks(top=rim_light))
    d.pair("legs", "right_leg", "knee spike", (-0.8, 4.0, -4.5, 0.8, 5.4, -3.2), paint("T", front="C"), glow="C")
    tasset = paint("T", sides=bottom_row("T", "K"), bottom="K")
    d.pair("legs", "right_leg", "tasset", (-2.8, 0.6, -3.2, 2.8, 3.4, -2.2), tasset, tweak=tweaks(sides=lit_edge))
    d.pair("legs", "right_leg", "tasset, side", (-3.2, 0.6, -2.2, -2.2, 3.4, 2.2), tasset)
    d.box("legs", "body", "clasp", (-1.1, 6.9, -2.85, 1.1, 8.5, -2.3), paint("K", front="A"), glow="A")

    # Boots: ember cuffs and obsidian sabatons with a glowing toe spike.
    for leg in ("right_leg", "left_leg"):
        d.ring("boots", leg, "cuff", -3.3, 3.3, -3.3, 3.3, 5.9, 7.2, 0.7, paint("K"), tweak=tweaks(sides=lit_edge))
    sabaton = paint("T", front=bottom_row("T", "K"))
    top_lit = tweaks(top=rim_light)
    d.pair("boots", "right_leg", "sabaton", (-2.6, 8.8, -3.4, 2.6, 10.4, -2.5), sabaton, tweak=top_lit)
    d.pair("boots", "right_leg", "sabaton, middle", (-2.75, 10.2, -3.8, 2.75, 11.6, -2.4), sabaton, tweak=top_lit)
    d.pair("boots", "right_leg", "sabaton, toe", (-2.45, 11.4, -4.2, 2.45, 12.8, -2.6), sabaton, tweak=top_lit)
    d.pair("boots", "right_leg", "toe spike", (-0.5, 11.5, -5.4, 0.5, 12.5, -4.2), paint("T", front="C"), glow="C")
    return d


def lumenward():
    m = {"E": gi.LUMEN_CRYSTAL.shifted(texture=gi.polish), "P": gi.LUMEN_CRYSTAL.shifted(texture=gi.flat),
         "D": gi.LUMEN_CRYSTAL.shifted(base=1, texture=gi.flat), "W": gi.LUMEN_CRYSTAL.shifted(base=4, texture=gi.flat),
         "U": gi.GOLD.shifted(texture=gi.flat), "R": gi.LUMEN_GLOW}
    d = DetailSet("lumenward", m, "a gold circlet with a glowing brow gem under a crown of five crystal spikes with glowing "
                                  "tips; crystal pauldrons with three spikes each, a glowing core in a gold frame, a gold "
                                  "gorget and belt; crystal knee cops with shards; crystal sabatons with gold cuffs; light "
                                  "motes drifting by the head and chest")
    spike = paint("E", top="R", sides=lambda u, v, w, h: "R" if v == 0 else "E")
    # Helmet: a gold circlet, a glowing gem, a tall central spike and four tilted ones, drifting motes.
    d.ring("helmet", "head", "circlet", -5.5, 5.5, -5.5, 5.5, -6.4, -5.4, 1, paint("U"), tweak=tweaks(sides=lit_edge))
    d.box("helmet", "head", "brow gem", (-1, -7.6, -5.9, 1, -5.6, -4.9), paint("R"), tweak=tweaks(front=["4.", ".."]), glow="R")
    d.box("helmet", "head", "spire", (-1, -15, -1, 1, -9, 1), paint("E", top="R", sides=lambda u, v, w, h: "R" if v == 0 else "E"),
          tweak=tweaks(sides=lit_edge), glow="R")
    side = Pivot("spike", (-3.0, -9.0, 0.0), (0, 0, -22))
    d.pair("helmet", "head", "spike", (-1, -4, -1, 1, 0, 1), paint("E"), pivot=side, tweak=tweaks(sides=lit_edge))
    d.pair("helmet", "head", "spike, tip", (-0.5, -6.5, -0.5, 0.5, -4, 0.5), paint("R"), pivot=side, glow="R")
    rear = Pivot("spike_back", (-1.8, -9.0, 3.0), (24, 0, -12))
    d.pair("helmet", "head", "spike, back", (-1, -3, -1, 1, 0, 1), paint("E"), pivot=rear, tweak=tweaks(sides=lit_edge))
    d.pair("helmet", "head", "spike, back tip", (-0.5, -5, -0.5, 0.5, -3, 0.5), paint("R"), pivot=rear, glow="R")
    d.pair("helmet", "head", "mote", (5.8, -10.6, -2.0, 6.8, -9.6, -1.0), paint("R"), glow="R")

    # Chest: a gold gorget, pauldrons crowned with three crystal spikes each, a glowing core in a gold frame.
    gold = paint("U")
    d.pair("chest", "body", "gorget", (-4.4, -1.6, -3.55, -2.2, 0.4, -2.6), gold, tweak=tweaks(sides=lit_edge, top=rim_light))
    d.pair("chest", "body", "gorget, V", (-2.2, -0.4, -3.25, -0.8, 1.6, -2.6), gold, tweak=tweaks(sides=lit_edge))
    d.pair("chest", "body", "gorget, shoulder", (-4.4, -1.6, -2.6, -2.2, -0.6, 2.6), gold, tweak=tweaks(top=lit_edge))
    d.box("chest", "body", "gorget, back", (-4.4, -1.6, 2.6, 4.4, 0.2, 3.25), gold, tweak=tweaks(sides=lit_edge))
    plate = paint("E", bottom="D", sides=bottom_row("E", "U"))
    d.pair("chest", "right_arm", "pauldron", (-4.8, -3.9, -3.8, 1.2, -1.1, 3.8), plate, tweak=tweaks(top=rim_light, sides=lit_edge))
    d.pair("chest", "right_arm", "pauldron, lower", (-5.1, -1.4, -3.5, 0.4, 0.9, 3.5), plate, tweak=tweaks(sides=lit_edge))
    out = Pivot("spike_out", (-3.6, -3.9, 0.0), (0, 0, -28))
    d.pair("chest", "right_arm", "spike", (-1, -4, -1, 1, 0, 1), paint("E"), pivot=out, tweak=tweaks(sides=lit_edge))
    d.pair("chest", "right_arm", "spike, tip", (-0.5, -6.5, -0.5, 0.5, -4, 0.5), paint("R"), pivot=out, glow="R")
    front = Pivot("spike_front", (-2.0, -3.9, -2.2), (-26, 0, -8))
    d.pair("chest", "right_arm", "spike, front", (-1, -3, -1, 1, 0, 1), paint("E"), pivot=front, tweak=tweaks(sides=lit_edge))
    d.pair("chest", "right_arm", "spike, front tip", (-0.5, -5, -0.5, 0.5, -3, 0.5), paint("R"), pivot=front, glow="R")
    back = Pivot("spike_back", (-2.0, -3.9, 2.2), (26, 0, -8))
    d.pair("chest", "right_arm", "spike, back", (-1, -3, -1, 1, 0, 1), paint("E"), pivot=back, tweak=tweaks(sides=lit_edge))
    d.pair("chest", "right_arm", "spike, back tip", (-0.5, -5, -0.5, 0.5, -3, 0.5), paint("R"), pivot=back, glow="R")
    d.box("chest", "body", "core", (-1.2, 2.2, -3.3, 1.2, 4.6, -2.6), paint("R"), tweak=tweaks(front=["4.", ".."]), glow="R")
    d.box("chest", "body", "frame, top", (-2.3, 1.2, -3.2, 2.3, 2.2, -2.65), gold, tweak=tweaks(front=lit_edge))
    d.box("chest", "body", "frame, bottom", (-2.3, 4.6, -3.2, 2.3, 5.6, -2.65), gold)
    d.pair("chest", "body", "frame, side", (-2.3, 2.2, -3.2, -1.2, 4.6, -2.65), gold)
    d.ring("chest", "body", "belt", -5.35, 5.35, -3.35, 3.3, 7.2, 8.3, 0.7, gold, tweak=tweaks(sides=lit_edge))
    d.pair("chest", "body", "mote", (5.8, 3.0, -1.0, 6.8, 4.0, 0.0), paint("R"), glow="R")

    # Leggings: crystal knee cops with a shard, gold-hemmed tassets, a gold belt with a glowing gem.
    d.pair("legs", "right_leg", "knee cop", (-2.2, 3.4, -3.2, 2.2, 6.4, -2.1), paint("E", front=bottom_row("E", "U")),
           tweak=tweaks(top=rim_light))
    d.pair("legs", "right_leg", "knee shard", (-0.7, 4.2, -4.8, 0.7, 5.6, -3.2), paint("E", front="W"),
           tweak=tweaks(sides=lit_edge))
    tasset = paint("E", sides=bottom_row("E", "U"), bottom="U")
    d.pair("legs", "right_leg", "tasset", (-2.8, 0.6, -3.2, 2.8, 3.4, -2.2), tasset, tweak=tweaks(sides=lit_edge))
    d.pair("legs", "right_leg", "tasset, side", (-3.2, 0.6, -2.2, -2.2, 3.4, 2.2), tasset)
    d.ring("legs", "body", "belt", -4.8, 4.8, -2.8, 2.8, 7.1, 8.1, 0.6, gold, tweak=tweaks(sides=lit_edge))
    d.box("legs", "body", "gem", (-1.0, 6.9, -2.85, 1.0, 8.2, -2.3), paint("R"), glow="R")

    # Boots: gold cuffs, crystal sabatons with a toe shard.
    for leg in ("right_leg", "left_leg"):
        d.ring("boots", leg, "cuff", -3.3, 3.3, -3.3, 3.3, 5.9, 7.2, 0.7, gold, tweak=tweaks(sides=lit_edge))
    sabaton = paint("E", front=bottom_row("E", "U"))
    top_lit = tweaks(top=rim_light)
    d.pair("boots", "right_leg", "sabaton", (-2.6, 8.8, -3.4, 2.6, 10.4, -2.5), sabaton, tweak=top_lit)
    d.pair("boots", "right_leg", "sabaton, middle", (-2.75, 10.2, -3.8, 2.75, 11.6, -2.4), sabaton, tweak=top_lit)
    d.pair("boots", "right_leg", "sabaton, toe", (-2.45, 11.4, -4.2, 2.45, 12.8, -2.6), sabaton, tweak=top_lit)
    d.pair("boots", "right_leg", "toe shard", (-0.5, 10.4, -5.6, 0.5, 11.6, -4.2), paint("E", front="R"), glow="R")
    return d


# ---------------------------------------------------------------- farming sets
def fieldhand():
    m = {"S": gi.DENIM, "Z": gi.DENIM.shifted(base=1), "G": gi.CRIMSON.shifted(texture=gi.flat), "W": gi.WHEAT,
         "L": gi.SOIL_LEATHER, "K": gi.SOIL_LEATHER.shifted(base=1, texture=gi.flat)}
    d = DetailSet("fieldhand", m, "a two-step denim cap with a button, a red bandana band with a knot and tails, a short "
                                  "stiff peak and a wheat sprig; denim straps, a bib with a pocket and buttons, rolled red "
                                  "sleeves; leather knee patches and a belt; rolled denim boot cuffs, laces and toe caps")
    # Helmet: a soft flat cap, the bandana tied round it, and a short peak instead of a brim.
    d.box("helmet", "head", "crown", (-4.5, -10, -4.5, 4.5, -9, 4.5), paint("S"), tweak=tweaks(top=rim_light))
    d.box("helmet", "head", "dome", (-3.5, -11, -3.5, 3.5, -10, 3.5), paint("S"), tweak=tweaks(top=rim_light))
    d.box("helmet", "head", "button", (-0.5, -11.8, -0.5, 0.5, -11, 0.5), paint("W"))
    d.ring("helmet", "head", "bandana", -5.5, 5.5, -5.5, 5.5, -6.6, -5.6, 1, paint("G"), tweak=tweaks(sides=lit_edge))
    d.box("helmet", "head", "peak", (-4.5, -5.5, -6.0, 4.5, -4.5, -4.6), paint("Z", top="S"), tweak=tweaks(top=rim_light))
    d.box("helmet", "head", "peak, tip", (-3, -5.5, -7.2, 3, -4.5, -6.0), paint("Z", top="S"), tweak=tweaks(top=rim_light))
    d.box("helmet", "head", "knot", (-1.5, -6.6, 5.6, 1.5, -4.6, 6.6), paint("G"), tweak=tweaks(sides=lit_edge))
    d.pair("helmet", "head", "bandana tail", (-1.6, -4.6, 5.8, -0.4, 0.0, 6.4),
           paint("G", sides=lambda u, v, w, h: frayed("G", 6)(u, v, w, h)))
    sprig = Pivot("wheat_sprig", (5.2, -6, 1.5), (-20, 0, 18))
    ear = ["..W..", ".WWW.", "WWWWW", ".WWW.", ".WWW.", "..W..", "..L..", "..L.."]
    lit = ["..4..", ".4...", "4....", ".....", ".....", ".....", ".....", "....."]
    d.box("helmet", "head", "wheat sprig", (-2.5, -8, 0, 2.5, 0, 0), paint(".", front=ear), tweak=tweaks(front=lit),
          faces=("front",), pivot=sprig)
    d.box("helmet", "head", "wheat sprig, across", (0, -8, -2.5, 0, 0, 2.5), paint(".", right=ear), tweak=tweaks(right=lit),
          faces=("right",), pivot=sprig)

    # Chest: denim straps over the shoulders, a bib with a pocket and two buttons, red sleeves rolled up.
    strap = paint("S", sides="Z")
    d.pair("chest", "body", "strap", (-3.6, -1.6, -3.35, -2.1, 3.0, -2.7), strap, tweak=tweaks(front=lit_edge))
    d.pair("chest", "body", "strap, shoulder", (-3.6, -1.6, -2.7, -2.1, -0.6, 2.7), paint("S"), tweak=tweaks(top=lit_edge))
    d.pair("chest", "body", "strap, back", (-3.6, -1.6, 2.7, -2.1, 3.0, 3.3), strap)
    d.box("chest", "body", "bib", (-3, 3, -3.3, 3, 9, -2.6),
          paint("S", front=["WSSSSW", "SSSSSS", "SZZZZS", "SZSSZS", "SZZZZS", "SSSSSS"]),
          tweak=tweaks(front=["4....4", "......", "......", "......", "......", "......"]))
    d.pair("chest", "right_arm", "rolled sleeve", (-4.5, 0.8, -3.5, 2.5, 2.4, 3.5),
           paint("G", sides=lambda u, v, w, h: "Z" if v == h - 1 else "G"), tweak=tweaks(sides=lambda u, v, w, h: "-" if u % 3 == 2 else None))

    # Leggings: leather knee patches, a belt with a wheat-gold buckle.
    d.pair("legs", "right_leg", "knee patch", (-1.8, 3.6, -3.0, 1.8, 6.0, -2.1), paint("L", front=["LWWL", "LLLL"]),
           tweak=tweaks(front=lit_edge))
    d.ring("legs", "body", "belt", -4.8, 4.8, -2.8, 2.8, 7.1, 8.1, 0.6, paint("L"))
    d.box("legs", "body", "buckle", (-1, 6.8, -2.85, 1, 8.4, -2.3), paint("W", sides="K"), tweak=tweaks(front=["4.", ".-"]))

    # Boots: a rolled denim cuff, laces and a leather toe cap.
    cuff = paint("S", sides=lambda u, v, w, h: "Z" if v == 0 else "S", bottom="Z")
    for leg in ("right_leg", "left_leg"):
        d.ring("boots", leg, "cuff", -3.5, 3.5, -3.5, 3.5, 5.6, 7.6, 0.9, cuff, tweak=tweaks(sides=lit_edge))
    d.pair("boots", "right_leg", "laces", (-1.5, 8.4, -3.2, 1.5, 11.0, -2.6), paint("L", front=["W.W", ".W.", "W.W"]))
    d.pair("boots", "right_leg", "toe cap", (-2.5, 10.3, -3.6, 2.5, 12.8, -2.4), paint("L", front=rows(("L", 2), ("K", 1))),
           tweak=tweaks(top=rim_light, front=lit_edge))
    return d


def sunward():
    m = {"S": gi.LINEN_GOLD, "Z": gi.LINEN_GOLD.shifted(base=1), "Y": gi.LINEN_GOLD.shifted(base=3, texture=gi.flat),
         "G": gi.SUN_ORANGE, "W": gi.AMBER, "L": gi.HIDE, "K": gi.HIDE.shifted(base=1, texture=gi.flat)}
    d = DetailSet("sunward", m, "a tall golden crown with a spike, a very wide brim edged in orange, a band with a glowing "
                                "sun disc and four rays; an orange collar, a glowing sun disc with rays on the breast, "
                                "pauldrons with three sun-ray fins, an orange sash; knee guards and a hanging tabard; "
                                "orange-strapped boots")
    # Helmet: a tall crown, a very wide brim with an orange edge, and the sun disc on the band.
    d.box("helmet", "head", "crown", (-4, -10, -4, 4, -9, 4), paint("S"), tweak=tweaks(top=rim_light))
    d.box("helmet", "head", "dome", (-3, -11, -3, 3, -10, 3), paint("S"), tweak=tweaks(top=rim_light))
    d.box("helmet", "head", "crown tip", (-1, -13, -1, 1, -11, 1), paint("Y"), tweak=tweaks(sides=lit_edge))

    def brim(ring_of):
        def top(u, v, w, h):
            ring = ring_of(u, v, w, h)
            return "G" if ring == 0 else "Y" if ring == 1 else "S"
        return paint("G", top=top, bottom="G")

    weave = tweaks(top=lambda u, v, w, h: "-" if (u + v) % 3 == 0 and (u * v) % 2 else None)
    d.box("helmet", "head", "brim, front", (-8.5, -5, -8.5, 8.5, -4, -4.5), brim(lambda u, v, w, h: h - 1 - v), tweak=weave)
    d.box("helmet", "head", "brim, back", (-8.5, -5, 4.5, 8.5, -4, 8.5), brim(lambda u, v, w, h: v), tweak=weave)
    d.box("helmet", "head", "brim, right", (-8.5, -5, -4.5, -4.5, -4, 4.5), brim(lambda u, v, w, h: u), tweak=weave)
    d.box("helmet", "head", "brim, left", (4.5, -5, -4.5, 8.5, -4, 4.5), brim(lambda u, v, w, h: w - 1 - u), tweak=weave)
    d.ring("helmet", "head", "band", -5.5, 5.5, -5.5, 5.5, -6.6, -5.6, 1, paint("G"), tweak=tweaks(sides=lit_edge))
    d.box("helmet", "head", "sun disc", (-1.5, -8.4, -5.7, 1.5, -5.4, -4.9), paint("W"), tweak=tweaks(front=["4..", "...", "..."]),
          glow="W")
    d.box("helmet", "head", "ray, top", (-0.5, -9.8, -5.7, 0.5, -8.4, -4.9), paint("G", top="W"), glow="W")
    d.pair("helmet", "head", "ray", (-3.5, -7.4, -5.7, -1.5, -6.4, -4.9), paint("G", sides="W"), glow="W")

    # Chest: an orange collar, the sun on the breast with four rays, pauldrons with sun-ray fins, an orange sash.
    orange = paint("G")
    d.pair("chest", "body", "collar", (-4.4, -1.6, -3.55, -2.2, 0.4, -2.6), orange, tweak=tweaks(sides=lit_edge, top=rim_light))
    d.pair("chest", "body", "collar, V", (-2.2, -0.4, -3.25, -0.8, 1.6, -2.6), orange, tweak=tweaks(sides=lit_edge))
    d.pair("chest", "body", "collar, shoulder", (-4.4, -1.6, -2.6, -2.2, -0.6, 2.6), orange, tweak=tweaks(top=lit_edge))
    d.box("chest", "body", "collar, back", (-4.4, -1.6, 2.6, 4.4, 0.2, 3.25), orange, tweak=tweaks(sides=lit_edge))
    d.box("chest", "body", "sun", (-1.5, 1.8, -3.3, 1.5, 4.8, -2.6), paint("W"), tweak=tweaks(front=["4..", "...", "..."]),
          glow="W")
    d.box("chest", "body", "sun ray, top", (-0.5, 0.2, -3.3, 0.5, 1.8, -2.6), orange)
    d.box("chest", "body", "sun ray, bottom", (-0.5, 4.8, -3.3, 0.5, 6.4, -2.6), orange)
    d.pair("chest", "body", "sun ray", (-3.6, 2.8, -3.3, -1.5, 3.8, -2.6), orange)
    plate = paint("S", bottom="Z", sides=bottom_row("S", "G"))
    d.pair("chest", "right_arm", "pauldron", (-4.8, -3.8, -3.7, 1.1, -1.4, 3.7), plate, tweak=tweaks(top=rim_light, sides=lit_edge))
    fin = paint("G", top="W")
    d.pair("chest", "right_arm", "fin, front", (-3.4, -5.4, -2.5, -2.2, -3.8, -1.5), fin, glow="W")
    d.pair("chest", "right_arm", "fin, middle", (-3.4, -6.2, -0.5, -2.2, -3.8, 0.5), fin, glow="W")
    d.pair("chest", "right_arm", "fin, back", (-3.4, -5.4, 1.5, -2.2, -3.8, 2.5), fin, glow="W")
    d.ring("chest", "body", "sash", -5.35, 5.35, -3.35, 3.3, 7.2, 8.3, 0.7, orange, tweak=tweaks(sides=lit_edge))

    # Leggings: knee guards with an orange hem, a tabard hanging front and back with a sun on it.
    d.pair("legs", "right_leg", "knee guard", (-2.2, 3.6, -3.2, 2.2, 5.6, -2.1), paint("S", front=bottom_row("S", "G")),
           tweak=tweaks(top=rim_light))
    d.box("legs", "body", "tabard", (-2.5, 8.0, -2.95, 2.5, 12.0, -2.55),
          paint("S", front=["SSSSS", "SZWZS", "SSZSS", "GGGGG"]), tweak=tweaks(front=lit_edge))
    d.box("legs", "body", "tabard, back", (-2.5, 8.0, 2.55, 2.5, 12.0, 2.95), paint("S", back=bottom_row("S", "G")),
          tweak=tweaks(back=lit_edge))

    # Boots: a golden cuff with an orange hem, an orange strap, a hide toe cap.
    for leg in ("right_leg", "left_leg"):
        d.ring("boots", leg, "cuff", -3.4, 3.4, -3.4, 3.4, 5.6, 7.0, 0.8, paint("S", sides=bottom_row("S", "G")),
               tweak=tweaks(sides=lit_edge))
    d.pair("boots", "right_leg", "strap", (-2.0, 8.6, -3.2, 2.0, 9.4, -2.6), orange)
    d.pair("boots", "right_leg", "toe cap", (-2.5, 10.3, -3.6, 2.5, 12.8, -2.4), paint("L", front=rows(("L", 2), ("K", 1))),
           tweak=tweaks(top=rim_light, front=lit_edge))
    return d


def frostgrove():
    m = {"O": gi.FROST_PINE, "K": gi.FROST_PINE.shifted(base=1, texture=gi.flat), "M": gi.WOOL_WHITE,
         "H": gi.ICE, "I": gi.SILVER.shifted(texture=gi.flat), "J": gi.FROSTBERRY}
    d = DetailSet("frostgrove", m, "a pine hood with drapes and a frostberry bobble round a thick fur-framed face opening; a "
                                  "fur mantle, ice toggles, a berry rope belt with a silver buckle, a fur hem and shoulder "
                                  "pads with ice crystals; fur knee guards; fur boot cuffs, laces and ice toe caps")
    fur = paint("M", sides=frayed("M", 1), top="M")
    # Helmet: the hood, a fur frame round the face, and a bobble of frostberries.
    d.box("helmet", "head", "crown", (-4, -10, -4, 4, -9, 4), paint("O"), tweak=tweaks(top=rim_light))
    d.box("helmet", "head", "bobble", (-0.5, -12, -0.5, 0.5, -11, 0.5), paint("J"), glow="J")
    d.box("helmet", "head", "bobble, left", (-1.5, -11, -0.5, -0.5, -10, 0.5), paint("J"), glow="J")
    d.box("helmet", "head", "bobble, right", (0.5, -11, -0.5, 1.5, -10, 0.5), paint("J"), glow="J")
    drape = paint("O", bottom="K", sides=bottom_row("O", "K"))
    d.box("helmet", "head", "hood, back", (-5.4, -8.4, 3.8, 5.4, -2.0, 6.0), drape, tweak=tweaks(sides=lit_edge))
    d.pair("helmet", "head", "hood, side", (-5.6, -8.4, -4.0, -4.8, -2.0, 3.8), drape, tweak=tweaks(sides=lit_edge))
    d.box("helmet", "head", "fur, brow", (-2.4, -6.4, -6.0, 2.4, -4.8, -4.6), fur, tweak=tweaks(sides=lit_edge))
    d.pair("helmet", "head", "fur, cheek", (-5.8, -6.4, -6.0, -2.4, 0.6, -4.6), paint("M", sides=frayed("M", 2), top="M"),
           tweak=tweaks(sides=lit_edge))
    d.box("helmet", "head", "fur, chin", (-2.4, -1.2, -6.0, 2.4, 0.8, -4.6), paint("M", sides=frayed("M", 3), top="M"))

    # Chest: a fur mantle, ice toggles down the front, a berry rope belt and a fur hem, shoulder pads with ice crystals.
    d.pair("chest", "body", "mantle", (-5.6, -1.7, -3.6, -2.0, 0.9, 3.3), fur, tweak=tweaks(sides=lit_edge))
    d.box("chest", "body", "mantle, front", (-2.0, -1.4, -3.3, 2.0, 0.4, -2.5), paint("M", front=frayed("M", 3)))
    d.box("chest", "body", "mantle, back", (-2.0, -1.7, 2.4, 2.0, 0.9, 3.3), fur)
    for i, y in enumerate((1.6, 3.4, 5.2)):
        d.pair("chest", "body", f"toggle {i + 1}", (2.3, y, -3.55, 3.5, y + 0.8, -3.1), paint("H"), tweak=tweaks(front=lit_edge))
    d.ring("chest", "body", "belt", -5.2, 5.2, -3.3, 3.2, 6.6, 7.5, 0.6, paint("J"), tweak=tweaks(sides=lit_edge))
    d.box("chest", "body", "buckle", (-1, 6.2, -3.7, 1, 8.2, -3.2), paint("I", front=["II", "I."]), tweak=tweaks(front=["4.", ".."]))
    d.ring("chest", "body", "hem", -5.4, 5.4, -3.5, 3.3, 8.0, 9.4, 0.9, paint("M", sides=frayed("M", 4)), tweak=tweaks(sides=lit_edge))
    d.pair("chest", "right_arm", "shoulder pad", (-4.8, -3.8, -3.7, 0.9, -0.6, 3.7), fur, tweak=tweaks(top=rim_light, sides=lit_edge))
    crystal = paint("H", top="I")
    d.pair("chest", "right_arm", "ice crystal", (-3.6, -5.8, -1.5, -2.6, -3.8, -0.5), crystal, tweak=tweaks(sides=lit_edge))
    d.pair("chest", "right_arm", "ice crystal, small", (-2.4, -5.0, 0.5, -1.4, -3.8, 1.5), crystal)

    # Leggings: fur knee guards, a berry belt with a silver buckle.
    d.pair("legs", "right_leg", "knee guard", (-2.1, 3.6, -3.1, 2.1, 6.0, -2.2), paint("M", sides=frayed("M", 5), top="M"),
           tweak=tweaks(sides=lit_edge))
    d.ring("legs", "body", "belt", -4.8, 4.8, -2.8, 2.8, 7.1, 8.1, 0.6, paint("J"))
    d.box("legs", "body", "buckle", (-1, 6.8, -2.85, 1, 8.4, -2.3), paint("I", sides="K"), tweak=tweaks(front=["4.", ".-"]))

    # Boots: thick fur cuffs, silver laces and an ice toe cap.
    for leg in ("right_leg", "left_leg"):
        d.ring("boots", leg, "fur cuff", -3.6, 3.6, -3.6, 3.6, 5.4, 8.4, 1.0, paint("M", sides=frayed("M", 4), top="M"),
               tweak=tweaks(sides=lit_edge))
    d.pair("boots", "right_leg", "laces", (-1.5, 8.4, -3.2, 1.5, 11.0, -2.6), paint("O", front=["I.I", ".I.", "I.I"]))
    d.pair("boots", "right_leg", "toe cap", (-2.5, 10.6, -3.7, 2.5, 12.8, -2.4), paint("H", front=rows(("H", 2), ("I", 1))),
           tweak=tweaks(top=rim_light, front=lit_edge))
    return d


def cinderfield():
    m = {"S": gi.CHARCOAL, "Z": gi.CHARCOAL.shifted(base=1), "G": gi.PEPPER_RED.shifted(texture=gi.cloth),
         "W": gi.EMBER.shifted(texture=gi.flat), "L": gi.OBSIDIAN, "K": gi.OBSIDIAN.shifted(base=1, texture=gi.flat)}
    d = DetailSet("cinderfield", m, "a three-step pointed hat with a bent, glowing tip, a burnt brim with ember flecks and a "
                                    "red band; a red scarf with a glowing-edged tail, obsidian pauldrons with glowing spikes, "
                                    "a seed pouch, an obsidian belt with an ember buckle; ragged tassets and knee plates "
                                    "with glowing hems; red boot cuffs and obsidian toe caps with ember tips")

    def embers(rim, base):
        """Brim top: a dark rim, flecks of ember scattered over the charcoal."""
        def pick(u, v, w, h):
            if rim(u, v, w, h) == 0:
                return "Z"
            return "W" if hashed(u, v, w, h) % 9 == 0 else base
        return pick

    # Helmet: a pointed hat in three steps with a bent tip, a wide burnt brim and a pepper-red band.
    d.box("helmet", "head", "crown", (-4, -10, -4, 4, -9, 4), paint("S"), tweak=tweaks(top=rim_light))
    d.box("helmet", "head", "crown, middle", (-3, -11, -3, 3, -10, 3), paint("S"), tweak=tweaks(top=rim_light))
    d.box("helmet", "head", "crown, upper", (-2, -12, -2, 2, -11, 2), paint("S"), tweak=tweaks(top=rim_light))
    tip = Pivot("tip", (0, -12, 0), (-30, 0, 0))
    d.box("helmet", "head", "tip", (-1, -3.5, -1, 1, 0, 1), paint("Z"), pivot=tip)
    d.box("helmet", "head", "tip, ember", (-0.5, -5, -0.5, 0.5, -3.5, 0.5), paint("W"), pivot=tip, glow="W")

    def brim(ring_of):
        return paint("Z", top=embers(ring_of, "S"), sides=lambda u, v, w, h: frayed("Z", 2)(u, v, w, h))

    d.box("helmet", "head", "brim, front", (-7.5, -5, -7.5, 7.5, -4, -4.5), brim(lambda u, v, w, h: h - 1 - v))
    d.box("helmet", "head", "brim, back", (-7.5, -5, 4.5, 7.5, -4, 7.5), brim(lambda u, v, w, h: v))
    d.box("helmet", "head", "brim, right", (-7.5, -5, -4.5, -4.5, -4, 4.5), brim(lambda u, v, w, h: u))
    d.box("helmet", "head", "brim, left", (4.5, -5, -4.5, 7.5, -4, 4.5), brim(lambda u, v, w, h: w - 1 - u))
    d.ring("helmet", "head", "band", -5.5, 5.5, -5.5, 5.5, -6.6, -5.6, 1, paint("G"), tweak=tweaks(sides=lit_edge))
    # (glow set below, with the brim's flecks)
    for c in d.cubes:
        if c.label.startswith("brim"):
            c.glow = "W"

    # Chest: a red scarf with a glowing-edged tail, obsidian pauldrons with glowing spikes, an ember-buckled belt.
    scarf = paint("G")
    d.pair("chest", "body", "scarf", (-4.4, -1.6, -3.55, -2.2, 0.4, -2.6), scarf, tweak=tweaks(sides=lit_edge, top=rim_light))
    d.pair("chest", "body", "scarf, V", (-2.2, -0.4, -3.25, -0.8, 1.6, -2.6), scarf, tweak=tweaks(sides=lit_edge))
    d.pair("chest", "body", "scarf, shoulder", (-4.4, -1.6, -2.6, -2.2, -0.6, 2.6), scarf, tweak=tweaks(top=lit_edge))
    d.box("chest", "body", "scarf, back", (-4.4, -1.6, 2.6, 4.4, 0.2, 3.25), scarf, tweak=tweaks(sides=lit_edge))
    d.box("chest", "body", "scarf tail", (2.4, 0.6, -3.3, 3.9, 7.0, -2.6),
          paint("G", front=lambda u, v, w, h: "W" if v == h - 1 else "G"), tweak=tweaks(front=lit_edge), glow="W")
    plate = paint("L", bottom="K", top=lambda u, v, w, h: "W" if hashed(u, v, w, 3) % 8 == 0 else "L",
                  sides=bottom_row("L", "W"))
    d.pair("chest", "right_arm", "pauldron", (-4.8, -3.9, -3.8, 1.2, -1.1, 3.8), plate, tweak=tweaks(top=rim_light, sides=lit_edge),
           glow="W")
    d.pair("chest", "right_arm", "spike", (-3.6, -6.5, -0.5, -2.6, -3.9, 0.5), paint("L", top="W"), glow="W")
    d.ring("chest", "body", "belt", -5.4, 5.4, -3.4, 3.3, 8.3, 9.5, 0.8, paint("L"), tweak=tweaks(sides=lit_edge))
    d.box("chest", "body", "buckle", (-1.0, 7.9, -3.7, 1.0, 9.45, -3.2), paint("K", front=["WW", "WW"]), glow="W")
    d.box("chest", "body", "seed pouch", (-6.2, 5.8, -1.8, -5.0, 9.3, 1.8),
          paint("K", right=["WWWW", "KKKK", "KKKK", "KKKK"], top="L"), tweak=tweaks(right=lit_edge), glow="W")

    # Leggings: ragged charcoal tassets, obsidian knee plates with a glowing hem, an ember clasp.
    d.pair("legs", "right_leg", "knee plate", (-2.2, 3.6, -3.2, 2.2, 6.2, -2.1), paint("L", front=bottom_row("L", "W")),
           tweak=tweaks(top=rim_light), glow="W")
    tasset = paint("S", sides=lambda u, v, w, h: frayed("S", 7)(u, v, w, h))
    d.pair("legs", "right_leg", "tasset", (-2.8, 0.6, -3.2, 2.8, 3.4, -2.2), tasset, tweak=tweaks(sides=lit_edge))
    d.pair("legs", "right_leg", "tasset, side", (-3.2, 0.6, -2.2, -2.2, 3.4, 2.2), tasset)
    d.box("legs", "body", "clasp", (-1.1, 7.0, -2.8, 1.1, 9.2, -2.3), paint("K", front=["WW", "WW"]), glow="W")

    # Boots: ragged red cuffs, obsidian toe caps with an ember tip.
    for leg in ("right_leg", "left_leg"):
        d.ring("boots", leg, "cuff", -3.4, 3.4, -3.4, 3.4, 5.8, 7.6, 0.8,
               paint("G", sides=lambda u, v, w, h: frayed("G", 5, 2)(u, v, w, h)), tweak=tweaks(sides=lit_edge))
    d.pair("boots", "right_leg", "toe cap", (-2.5, 10.3, -3.6, 2.5, 12.8, -2.4), paint("L", front=rows(("L", 2), ("W", 1))),
           tweak=tweaks(top=rim_light), glow="W")
    return d


def moonharvest():
    m = {"O": gi.MIDNIGHT, "K": gi.MIDNIGHT.shifted(base=1, texture=gi.flat), "M": gi.SILVER.shifted(texture=gi.flat),
         "H": gi.MOON_GLOW}
    d = DetailSet("moonharvest", m, "a silver circlet under a big glowing crescent, a star-flecked veil down the back and "
                                    "sides and drifting motes; a silver collar with a glowing crescent clasp, pauldrons "
                                    "with glowing stars, bell sleeves, a silver belt with a crescent buckle; star knee plates "
                                    "and silver-hemmed tassets; silver cuffs and upturned pointed toes")

    def veil(u, v, w, h):
        """Midnight cloth with a silver hem and the odd star."""
        if v == h - 1:
            return "M"
        return "H" if hashed(u, v, w, 11) % 12 == 0 else "O"

    # Helmet: a silver circlet, a crescent that glows, a veil of star-flecked midnight cloth behind.
    d.box("helmet", "head", "crown", (-4, -10, -4, 4, -9, 4), paint("O"), tweak=tweaks(top=rim_light))
    d.ring("helmet", "head", "circlet", -5.5, 5.5, -5.5, 5.5, -6.6, -5.6, 1, paint("M"), tweak=tweaks(sides=lit_edge))
    d.box("helmet", "head", "crescent", (-3, -8.6, -5.8, 3, -7.4, -4.9), paint("H"), tweak=tweaks(front=lit_edge), glow="H")
    d.pair("helmet", "head", "crescent, horn", (-4, -10.6, -5.8, -3, -8.6, -4.9), paint("H"), tweak=tweaks(front=lit_edge), glow="H")
    d.pair("helmet", "head", "crescent, tip", (-3.5, -11.4, -5.8, -3, -10.6, -4.9), paint("H"), glow="H")
    d.box("helmet", "head", "veil, back", (-4.5, -8.8, 4.6, 4.5, -0.4, 5.9), paint("O", sides=veil), tweak=tweaks(sides=lit_edge),
          glow="H")
    d.pair("helmet", "head", "veil, side", (-5.8, -8.6, -2.0, -4.8, -1.0, 4.6), paint("O", sides=veil), tweak=tweaks(sides=lit_edge),
           glow="H")
    d.pair("helmet", "head", "mote", (5.8, -9.8, -2.0, 6.8, -8.8, -1.0), paint("H"), glow="H")
    d.pair("helmet", "head", "mote, low", (6.4, -6.8, 2.0, 7.4, -5.8, 3.0), paint("H"), glow="H")

    # Chest: a silver collar with a crescent clasp, pauldrons with a glowing star, bell sleeves, a silver belt.
    silver = paint("M")
    d.pair("chest", "body", "collar", (-4.4, -1.6, -3.55, -2.2, 0.4, -2.6), silver, tweak=tweaks(sides=lit_edge, top=rim_light))
    d.pair("chest", "body", "collar, V", (-2.2, -0.4, -3.25, -0.8, 1.6, -2.6), silver, tweak=tweaks(sides=lit_edge))
    d.pair("chest", "body", "collar, shoulder", (-4.4, -1.6, -2.6, -2.2, -0.6, 2.6), silver, tweak=tweaks(top=lit_edge))
    d.box("chest", "body", "collar, back", (-4.4, -1.6, 2.6, 4.4, 0.2, 3.25), silver, tweak=tweaks(sides=lit_edge))
    d.box("chest", "body", "clasp plate", (-2.5, 0.6, -3.2, 2.5, 5.4, -2.7), paint("M"), tweak=tweaks(front=lit_edge))
    d.box("chest", "body", "crescent", (-0.5, 1.0, -3.3, 1.5, 2.0, -2.65), paint("H"), glow="H")
    d.box("chest", "body", "crescent, horn", (-1.5, 2.0, -3.3, -0.5, 4.0, -2.65), paint("H"), glow="H")
    d.box("chest", "body", "crescent, tip", (-0.5, 4.0, -3.3, 1.5, 5.0, -2.65), paint("H"), glow="H")
    cap = paint("M", bottom="K", sides=bottom_row("M", "O"))
    d.pair("chest", "right_arm", "epaulette", (-4.7, -3.7, -3.7, 0.9, -1.3, 3.7), cap, tweak=tweaks(top=rim_light, sides=lit_edge))
    d.pair("chest", "right_arm", "star", (-3.3, -4.6, -0.7, -1.7, -3.7, 0.9), paint("H"), glow="H")
    d.pair("chest", "right_arm", "bell sleeve", (-4.9, 2.8, -3.6, 2.9, 4.2, 3.6), paint("O", sides=bottom_row("O", "M")),
           tweak=tweaks(sides=lit_edge))
    d.ring("chest", "body", "belt", -5.4, 5.4, -3.4, 3.3, 8.3, 9.5, 0.8, silver, tweak=tweaks(sides=lit_edge))
    d.box("chest", "body", "buckle", (-1.0, 7.9, -3.7, 1.0, 9.45, -3.2), paint("M", front=["HH", "H."]), glow="H")

    # Leggings: knee plates with a glowing star, silver-hemmed tassets, a silver sash.
    d.pair("legs", "right_leg", "knee plate", (-2.2, 3.6, -3.2, 2.2, 6.0, -2.1), paint("O", front=bottom_row("O", "M")),
           tweak=tweaks(top=rim_light, front=lit_edge))
    d.pair("legs", "right_leg", "knee star", (-0.5, 4.2, -3.5, 0.5, 5.2, -3.2), paint("H"), glow="H")
    tasset = paint("O", sides=bottom_row("O", "M"), bottom="M")
    d.pair("legs", "right_leg", "tasset", (-2.8, 0.6, -3.2, 2.8, 3.4, -2.2), tasset, tweak=tweaks(sides=lit_edge))
    d.pair("legs", "right_leg", "tasset, side", (-3.2, 0.6, -2.2, -2.2, 3.4, 2.2), tasset)
    d.ring("legs", "body", "sash", -4.8, 4.8, -2.8, 2.8, 7.1, 8.1, 0.6, silver)

    # Boots: silver cuffs and pointed toes that turn up at the tip.
    for leg in ("right_leg", "left_leg"):
        d.ring("boots", leg, "cuff", -3.3, 3.3, -3.3, 3.3, 5.9, 7.2, 0.7, silver, tweak=tweaks(sides=lit_edge))
    d.pair("boots", "right_leg", "toe", (-2.5, 10.5, -4.6, 2.5, 12.8, -2.4), paint("O", front=rows(("O", 2), ("M", 1))),
           tweak=tweaks(top=rim_light))
    d.pair("boots", "right_leg", "toe tip", (-1.0, 9.8, -5.8, 1.0, 11.4, -4.6), paint("M"), tweak=tweaks(sides=lit_edge))
    return d


# ---------------------------------------------------------------- foraging sets
def shingle_collar(d, mat, tweak_top=True):
    """The neck piece every chest shares: two sides, a V at the front (flat where an amulet hangs) and a back."""
    p = paint(mat)
    d.pair("chest", "body", "collar", (-4.4, -1.6, -3.55, -2.2, 0.4, -2.6), p, tweak=tweaks(sides=lit_edge, top=rim_light))
    d.pair("chest", "body", "collar, V", (-2.2, -0.4, -3.25, -0.8, 1.6, -2.6), p, tweak=tweaks(sides=lit_edge))
    d.pair("chest", "body", "collar, shoulder", (-4.4, -1.6, -2.6, -2.2, -0.6, 2.6), p, tweak=tweaks(top=lit_edge))
    d.box("chest", "body", "collar, back", (-4.4, -1.6, 2.6, 4.4, 0.2, 3.25), p, tweak=tweaks(sides=lit_edge))


def barkguard():
    m = {"B": gi.IRONBARK.shifted(texture=gi.shingle), "P": gi.IRONBARK.shifted(texture=gi.flat),
         "D": gi.IRONBARK.shifted(base=1, texture=gi.flat), "L": gi.SOIL_LEATHER,
         "K": gi.SOIL_LEATHER.shifted(base=1, texture=gi.flat), "G": gi.LEAF.shifted(texture=gi.flat),
         "Y": gi.LEAF.shifted(base=3, texture=gi.flat), "R": gi.IRON.shifted(texture=gi.flat)}
    d = DetailSet("barkguard", m, "a bark cap under three overlapping shingle bands with a leaf at the side; stepped shingle "
                                  "strips down the chest and back, stacked shingle pauldrons, a leather collar and belt; "
                                  "shingle knee plates; shingle-edged boot cuffs and leather toe caps")
    # Helmet: the bark crown, three shingle bands stepping out as they go down, a little leaf.
    d.box("helmet", "head", "crown", (-4, -10, -4, 4, -9, 4), paint("B"), tweak=tweaks(top=rim_light))
    for i, (r, y0) in enumerate(((5.2, -8.6), (5.5, -7.4), (5.8, -6.2))):
        d.ring("helmet", "head", f"shingle band {i + 1}", -r, r, -r, r, y0, y0 + 1.2, 0.8,
               paint("B", bottom="D", sides=bottom_row("B", "D")), tweak=tweaks(sides=lit_edge))
    leaf = Pivot("leaf", (3.0, -9.6, -0.5), (-10, 0, 34))
    blade = [".GG.", "GYGG", "GGYG", ".GGG", "..D.", "..D."]
    lit = ["....", ".4..", "....", "....", "....", "...."]
    d.box("helmet", "head", "leaf", (-2, -6, 0, 2, 0, 0), paint(".", front=blade), tweak=tweaks(front=lit), faces=("front",),
          pivot=leaf)
    d.box("helmet", "head", "leaf, across", (0, -6, -2, 0, 0, 2), paint(".", right=blade), tweak=tweaks(right=lit),
          faces=("right",), pivot=leaf)

    # Chest: a leather collar, shingle strips stepping out down the chest, a leather belt, stacked shingle pauldrons.
    shingle_collar(d, "L")
    strip = paint("B", bottom="D", front=bottom_row("B", "D"), sides=bottom_row("B", "D"))
    d.box("chest", "body", "strip 1", (-4.2, 0.9, -3.2, 4.2, 2.7, -2.4), strip, tweak=tweaks(front=lit_edge))
    d.box("chest", "body", "strip 2", (-4.4, 2.5, -3.25, 4.4, 4.3, -2.36), strip, tweak=tweaks(front=lit_edge))
    d.box("chest", "body", "strip 3", (-4.55, 4.1, -3.3, 4.55, 5.9, -2.32), strip, tweak=tweaks(front=lit_edge))
    d.box("chest", "body", "strip 4", (-4.6, 5.7, -3.5, 4.6, 7.5, -2.28), strip, tweak=tweaks(front=lit_edge))
    d.box("chest", "body", "strip, back 1", (-4.2, 1.0, 2.6, 4.2, 2.8, 3.25), strip, tweak=tweaks(back=lit_edge))
    d.box("chest", "body", "strip, back 2", (-4.4, 2.6, 2.7, 4.4, 4.4, 3.2), strip, tweak=tweaks(back=lit_edge))
    d.ring("chest", "body", "belt", -5.4, 5.4, -3.4, 3.3, 8.3, 9.5, 0.8, paint("L"), tweak=tweaks(sides=lit_edge))
    d.box("chest", "body", "buckle", (-1.0, 7.9, -3.7, 1.0, 9.45, -3.2), paint("R"), tweak=tweaks(front=["4.", ".."]))
    plate = paint("B", bottom="D", sides=bottom_row("B", "D"))
    d.pair("chest", "right_arm", "pauldron", (-4.8, -3.9, -3.8, 1.2, -1.1, 3.8), plate, tweak=tweaks(top=rim_light, sides=lit_edge))
    d.pair("chest", "right_arm", "pauldron, middle", (-5.1, -1.4, -3.5, 0.4, 0.9, 3.5), plate, tweak=tweaks(sides=lit_edge))
    d.pair("chest", "right_arm", "pauldron, lower", (-5.3, 0.6, -3.3, -0.1, 2.6, 3.3), plate, tweak=tweaks(sides=lit_edge))

    # Leggings: two shingle knee plates, a leather belt with a rivet.
    knee = paint("B", bottom="D", front=bottom_row("B", "D"))
    d.pair("legs", "right_leg", "knee plate", (-2.1, 3.0, -3.1, 2.1, 4.8, -2.2), knee, tweak=tweaks(top=rim_light))
    d.pair("legs", "right_leg", "knee plate, lower", (-2.2, 4.6, -3.25, 2.2, 6.4, -2.15), knee, tweak=tweaks(front=lit_edge))
    d.ring("legs", "body", "belt", -4.8, 4.8, -2.8, 2.8, 7.1, 8.1, 0.6, paint("L"))
    d.box("legs", "body", "buckle", (-1, 6.8, -2.85, 1, 8.4, -2.3), paint("R", sides="K"), tweak=tweaks(front=["4.", ".-"]))

    # Boots: two stacked shingle cuffs and a leather toe cap.
    for leg in ("right_leg", "left_leg"):
        d.ring("boots", leg, "cuff", -3.5, 3.5, -3.5, 3.5, 5.8, 7.0, 0.8, paint("B", bottom="D", sides=bottom_row("B", "D")),
               tweak=tweaks(sides=lit_edge))
        d.ring("boots", leg, "cuff, lower", -3.7, 3.7, -3.7, 3.7, 6.8, 8.0, 0.8, paint("B", bottom="D", sides=bottom_row("B", "D")),
               tweak=tweaks(sides=lit_edge))
    d.pair("boots", "right_leg", "toe cap", (-2.5, 10.3, -3.6, 2.5, 12.8, -2.4), paint("L", front=rows(("L", 2), ("K", 1))),
           tweak=tweaks(top=rim_light, front=lit_edge))
    return d


def mistwarden():
    m = {"W": gi.MIST_WOOL, "K": gi.MIST_WOOL.shifted(base=1, texture=gi.flat), "F": gi.FUR, "T": gi.MISTPINE.shifted(texture=gi.flat),
         "L": gi.SOIL_LEATHER, "J": gi.SOIL_LEATHER.shifted(base=1, texture=gi.flat)}
    d = DetailSet("mistwarden", m, "a pale wool hood with a peak, a fur-edged face opening and a drape down the back; a thick fur "
                                   "collar ring, mistpine toggles down the coat, a cord belt and toggle buckle, a fur hem and "
                                   "fur-edged shoulders; wool tassets; fur-lined boot cuffs with toggle laces")
    fur = paint("F", sides=frayed("F", 1), top="F")
    # Helmet: the hood with a peak and a drape behind, fur trim round the face opening.
    d.box("helmet", "head", "crown", (-4, -10, -4, 4, -9, 4), paint("W"), tweak=tweaks(top=rim_light))
    d.box("helmet", "head", "peak", (-1.5, -11.2, -1.5, 1.5, -10, 1.5), paint("W"), tweak=tweaks(top=rim_light))
    drape = paint("W", bottom="K", sides=bottom_row("W", "K"))
    d.box("helmet", "head", "hood, back", (-5.4, -8.4, 3.8, 5.4, -2.0, 6.0), drape, tweak=tweaks(sides=lit_edge))
    d.pair("helmet", "head", "hood, side", (-5.6, -8.4, -4.0, -4.8, -2.0, 3.8), drape, tweak=tweaks(sides=lit_edge))
    d.box("helmet", "head", "fur, brow", (-2.4, -6.4, -6.0, 2.4, -4.8, -4.6), fur, tweak=tweaks(sides=lit_edge))
    d.pair("helmet", "head", "fur, cheek", (-5.8, -6.4, -6.0, -2.4, 0.6, -4.6), paint("F", sides=frayed("F", 2), top="F"),
           tweak=tweaks(sides=lit_edge))
    d.box("helmet", "head", "fur, chin", (-2.4, -1.2, -6.0, 2.4, 0.8, -4.6), paint("F", sides=frayed("F", 3), top="F"))

    # Chest: the fur collar ring (open at the front for an amulet), toggles, a cord belt and a fur hem.
    d.pair("chest", "body", "fur collar", (-4.7, -1.8, -3.55, -2.2, 0.6, 3.25), fur, tweak=tweaks(sides=lit_edge, top=rim_light))
    d.box("chest", "body", "fur collar, front", (-2.2, -1.8, -3.55, 2.2, -0.1, -2.6), paint("F", front=frayed("F", 5)),
          tweak=tweaks(sides=lit_edge))
    d.box("chest", "body", "fur collar, back", (-2.2, -1.8, 2.6, 2.2, 0.6, 3.25), fur)
    for i, y in enumerate((1.6, 3.4, 5.2)):
        d.pair("chest", "body", f"toggle {i + 1}", (2.4, y, -3.55, 3.9, y + 0.9, -3.1), paint("T"), tweak=tweaks(front=lit_edge))
    d.ring("chest", "body", "cord belt", -5.2, 5.2, -3.3, 3.2, 6.6, 7.5, 0.6, paint("K"), tweak=tweaks(sides=lit_edge))
    d.box("chest", "body", "buckle", (-1, 6.2, -3.7, 1, 8.2, -3.2), paint("T", front=["TT", "T."]), tweak=tweaks(front=["4.", ".."]))
    d.ring("chest", "body", "hem", -5.4, 5.4, -3.5, 3.3, 8.0, 9.4, 0.9, paint("F", sides=frayed("F", 4)), tweak=tweaks(sides=lit_edge))
    d.pair("chest", "right_arm", "shoulder", (-4.8, -3.8, -3.7, 0.9, -0.6, 3.7),
           paint("W", sides=bottom_row("W", "F"), bottom="F"), tweak=tweaks(top=rim_light, sides=lit_edge))

    # Leggings: wool tassets, a cord belt with a toggle buckle.
    tasset = paint("W", sides=bottom_row("W", "F"), bottom="F")
    d.pair("legs", "right_leg", "tasset", (-2.8, 0.6, -3.2, 2.8, 3.4, -2.2), tasset, tweak=tweaks(sides=lit_edge))
    d.pair("legs", "right_leg", "tasset, side", (-3.2, 0.6, -2.2, -2.2, 3.4, 2.2), tasset)
    d.ring("legs", "body", "belt", -4.8, 4.8, -2.8, 2.8, 7.1, 8.1, 0.6, paint("K"))
    d.box("legs", "body", "buckle", (-1, 6.8, -2.85, 1, 8.4, -2.3), paint("T", sides="K"), tweak=tweaks(front=["4.", ".-"]))

    # Boots: thick fur cuffs, toggle laces and a leather toe cap.
    for leg in ("right_leg", "left_leg"):
        d.ring("boots", leg, "fur cuff", -3.6, 3.6, -3.6, 3.6, 5.4, 8.4, 1.0, paint("F", sides=frayed("F", 4), top="F"),
               tweak=tweaks(sides=lit_edge))
    d.pair("boots", "right_leg", "laces", (-1.5, 8.4, -3.2, 1.5, 11.0, -2.6), paint("L", front=["T.T", ".T.", "T.T"]))
    d.pair("boots", "right_leg", "toe cap", (-2.5, 10.6, -3.7, 2.5, 12.8, -2.4), paint("L", front=rows(("L", 2), ("J", 1))),
           tweak=tweaks(top=rim_light, front=lit_edge))
    return d


def willowshade():
    m = {"R": gi.WILLOW_ROBE, "D": gi.WILLOW_ROBE.shifted(base=0, texture=gi.flat), "L": gi.WILLOW_LEAF.shifted(texture=gi.flat),
         "Y": gi.WILLOW_LEAF.shifted(base=3, texture=gi.flat), "W": gi.LAVENDER, "V": gi.LAVENDER.shifted(base=1, texture=gi.flat)}
    d = DetailSet("willowshade", m, "a deep purple hood with a drape, a lavender wrap and a veil of hanging willow strands over "
                                    "the face, down the back and the sides; a wrap collar, strands falling from the shoulders and "
                                    "chest, a sash and a ragged leaf hem; strand tassets and wraps at the knee; leaf-fringed "
                                    "boot cuffs with soft wrapped toes")

    def strand(salt):
        """A hanging leaf strand: green with the odd pale leaf, ragged at the bottom."""
        def pick(u, v, w, h):
            if v >= h - 1 and hashed(u, v, w, salt) % 2 == 0:
                return "."
            return "Y" if hashed(u, v, w, salt, 3) % 5 == 0 else "L"
        return pick

    # Helmet: the hood and its drape, a lavender wrap round the brow, and the veil of strands.
    d.box("helmet", "head", "crown", (-4, -10, -4, 4, -9, 4), paint("R"), tweak=tweaks(top=rim_light))
    drape = paint("R", bottom="D", sides=bottom_row("R", "D"))
    d.box("helmet", "head", "hood, back", (-5.4, -8.4, 3.8, 5.4, -2.0, 6.0), drape, tweak=tweaks(sides=lit_edge))
    d.pair("helmet", "head", "hood, side", (-5.6, -8.4, -4.0, -4.8, -2.0, 3.8), drape, tweak=tweaks(sides=lit_edge))
    d.ring("helmet", "head", "wrap", -5.5, 5.5, -5.5, 5.5, -6.6, -5.6, 1, paint("W"), tweak=tweaks(sides=lit_edge))
    for i, (x0, y1) in enumerate(((3.4, -0.8), (1.8, -2.6), (0.2, -1.6))):
        d.pair("helmet", "head", f"veil strand {i + 1}", (-x0 - 0.8, -5.8, -5.9, -x0, y1, -5.3), paint("L", sides=strand(i + 1)),
               tweak=tweaks(sides=lit_edge))
    for i, (x0, y1) in enumerate(((3.2, 1.4), (1.6, 0.3), (0.0, 1.9))):
        d.pair("helmet", "head", f"back strand {i + 1}", (-x0 - 1.0, -3.0, 6.0, -x0, y1, 6.6), paint("L", sides=strand(10 + i)),
               tweak=tweaks(sides=lit_edge))
    d.pair("helmet", "head", "side strand", (-6.4, -6.4, -2.8, -5.6, -0.8, -1.8), paint("L", sides=strand(20)))
    d.pair("helmet", "head", "side strand, back", (-6.4, -6.0, 0.8, -5.6, 0.4, 1.8), paint("L", sides=strand(21)))

    # Chest: a wrap collar, strands falling from the shoulders and the chest, a sash and a ragged leaf hem.
    shingle_collar(d, "W")
    d.pair("chest", "right_arm", "mantle", (-4.8, -3.8, -3.7, 0.9, -0.6, 3.7), paint("R", bottom="D", sides=bottom_row("R", "D")),
           tweak=tweaks(top=rim_light, sides=lit_edge))
    for i, (z0, y1) in enumerate(((-2.8, 4.3), (-0.6, 3.3), (1.6, 4.1))):
        d.pair("chest", "right_arm", f"arm strand {i + 1}", (-5.3, -0.8, z0, -4.4, y1, z0 + 1.0), paint("L", sides=strand(30 + i)))
    d.pair("chest", "body", "chest strand", (2.4, 0.8, -3.5, 3.2, 7.0, -2.9), paint("L", sides=strand(40)),
           tweak=tweaks(sides=lit_edge))
    d.pair("chest", "body", "chest strand, outer", (3.6, 1.4, -3.5, 4.4, 5.5, -2.9), paint("L", sides=strand(41)))
    d.pair("chest", "body", "back strand", (2.4, 0.6, 2.7, 3.4, 6.5, 3.25), paint("L", sides=strand(42)))
    d.pair("chest", "body", "back strand, inner", (0.6, 0.6, 2.7, 1.6, 4.5, 3.25), paint("L", sides=strand(43)))
    d.ring("chest", "body", "sash", -5.2, 5.2, -3.3, 3.2, 6.6, 7.8, 0.6, paint("W"), tweak=tweaks(sides=lit_edge))
    d.ring("chest", "body", "hem", -5.4, 5.4, -3.5, 3.3, 8.0, 9.4, 0.9, paint("L", sides=frayed("L", 4, 2)), tweak=tweaks(sides=lit_edge))

    # Leggings: strand tassets, a knee wrap and a sash.
    d.pair("legs", "right_leg", "tasset", (-2.8, 0.6, -3.2, 2.8, 3.4, -2.2), paint("R", bottom="D", sides=bottom_row("R", "D")),
           tweak=tweaks(sides=lit_edge))
    d.pair("legs", "right_leg", "tasset, side", (-3.2, 0.6, -2.2, -2.2, 3.4, 2.2), paint("R", bottom="D", sides=bottom_row("R", "D")))
    d.pair("legs", "right_leg", "knee wrap", (-2.2, 4.4, -3.1, 2.2, 5.4, -2.2), paint("W"), tweak=tweaks(sides=lit_edge))
    d.pair("legs", "right_leg", "strand", (-1.8, 3.4, -3.2, -1.0, 7.0, -2.6), paint("L", sides=strand(51)))
    d.pair("legs", "right_leg", "strand, short", (-0.6, 3.4, -3.2, 0.2, 5.6, -2.6), paint("L", sides=strand(52)))
    d.ring("legs", "body", "sash", -4.8, 4.8, -2.8, 2.8, 7.1, 8.1, 0.6, paint("W"))

    # Boots: a wrap and a ragged leaf fringe at the cuff, soft wrapped toes.
    for leg in ("right_leg", "left_leg"):
        d.ring("boots", leg, "wrap", -3.3, 3.3, -3.3, 3.3, 8.4, 9.4, 0.7, paint("W"), tweak=tweaks(sides=lit_edge))
        d.ring("boots", leg, "fringe", -3.6, 3.6, -3.6, 3.6, 5.4, 7.8, 0.8, paint("L", sides=frayed("L", 5, 2)),
               tweak=tweaks(sides=lit_edge))
    d.pair("boots", "right_leg", "toe", (-2.5, 10.3, -3.6, 2.5, 12.8, -2.4), paint("W", front=rows(("W", 2), ("R", 1))),
           tweak=tweaks(top=rim_light, front=lit_edge))
    return d


def emberbark():
    m = {"C": gi.CHAR.shifted(texture=gi.shingle), "P": gi.CHAR.shifted(texture=gi.flat), "D": gi.CHAR.shifted(base=1, texture=gi.flat),
         "E": gi.EMBER.shifted(texture=gi.flat), "A": gi.EMBERITE}
    d = DetailSet("emberbark", m, "a charred bark helm under three plate bands with glowing seams, a ridge with ember "
                                  "speckles, cheek guards and drifting sparks; stepped plate strips with glowing seams, "
                                  "emberite studs and a big emberite stone on the chest, stacked pauldrons with bark spikes; "
                                  "seamed knee plates and tassets; glowing-seamed boot cuffs and sabatons")
    def ember_seam(u, v, w, h):
        """A seam of glowing ember along the bottom row, with gaps so it flickers rather than rules a line."""
        return "E" if v == h - 1 and hashed(u, v, w, 7) % 3 else "C"

    seam = paint("C", bottom="D", sides=ember_seam)

    def speckled(u, v, w, h):
        return "E" if hashed(u, v, w, 5) % 6 == 0 else "C"

    # Helmet: crown flecked with embers, a ridge along the top, plate bands with glowing seams, cheek guards, sparks.
    d.box("helmet", "head", "crown", (-4, -10, -4, 4, -9, 4), paint("C", top=speckled), tweak=tweaks(top=rim_light), glow="E")
    d.box("helmet", "head", "ridge", (-0.6, -11.4, -4.4, 0.6, -10, 4.4), paint("C", top="E"), tweak=tweaks(top=lit_edge), glow="E")
    for i, (r, y0) in enumerate(((5.2, -8.8), (5.5, -6.9))):
        d.ring("helmet", "head", f"plate band {i + 1}", -r, r, -r, r, y0, y0 + 2.0, 0.8, seam, tweak=tweaks(sides=lit_edge), glow="E")
    d.pair("helmet", "head", "cheek guard", (-5.6, -5.0, -5.6, -3.6, -1.0, -1.0), paint("C", sides=ember_seam),
           tweak=tweaks(sides=lit_edge), glow="E")
    d.pair("helmet", "head", "spark", (3.6, -13.0, -1.0, 4.6, -12.0, 0.0), paint("E"), glow="E")
    d.pair("helmet", "head", "spark, small", (1.6, -14.0, 1.0, 2.2, -13.4, 1.6), paint("A"), glow="A")

    # Chest: stepped plate strips with glowing seams, emberite studs and a big stone, stacked pauldrons with spikes.
    shingle_collar(d, "P")
    strip = paint("C", bottom="D", front=ember_seam, sides=ember_seam)
    d.box("chest", "body", "strip 1", (-4.2, 0.9, -3.15, 4.2, 2.7, -2.4), strip, tweak=tweaks(front=lit_edge), glow="E")
    d.box("chest", "body", "strip 2", (-4.4, 2.5, -3.2, 4.4, 4.3, -2.36), strip, tweak=tweaks(front=lit_edge), glow="E")
    d.box("chest", "body", "strip 3", (-4.55, 4.1, -3.25, 4.55, 5.9, -2.32), strip, tweak=tweaks(front=lit_edge), glow="E")
    d.box("chest", "body", "strip 4", (-4.6, 5.7, -3.5, 4.6, 7.5, -2.28), strip, tweak=tweaks(front=lit_edge), glow="E")
    d.box("chest", "body", "strip, back 1", (-4.2, 1.0, 2.6, 4.2, 2.8, 3.25), strip, glow="E")
    d.box("chest", "body", "strip, back 2", (-4.4, 2.6, 2.7, 4.4, 4.4, 3.2), strip, glow="E")
    d.box("chest", "body", "stone", (-1.3, 2.7, -3.3, 1.3, 4.5, -2.6), paint("A"), tweak=tweaks(front=["4..", "..."]), glow="A")
    d.pair("chest", "body", "stud", (2.4, 1.3, -3.5, 3.2, 2.1, -3.2), paint("A"), glow="A")
    d.pair("chest", "body", "stud, middle", (3.4, 3.1, -3.55, 4.2, 3.9, -3.25), paint("A"), glow="A")
    d.pair("chest", "body", "stud, low", (2.4, 6.2, -3.9, 3.2, 7.0, -3.5), paint("A"), glow="A")
    d.ring("chest", "body", "belt", -5.4, 5.4, -3.4, 3.3, 8.3, 9.5, 0.8, paint("P"), tweak=tweaks(sides=lit_edge))
    plate = paint("C", bottom="D", sides=ember_seam)
    d.pair("chest", "right_arm", "pauldron", (-4.8, -3.9, -3.8, 1.2, -1.1, 3.8), plate, tweak=tweaks(top=rim_light, sides=lit_edge),
           glow="E")
    d.pair("chest", "right_arm", "pauldron, middle", (-5.1, -1.4, -3.5, 0.4, 0.9, 3.5), plate, tweak=tweaks(sides=lit_edge), glow="E")
    d.pair("chest", "right_arm", "pauldron, lower", (-5.3, 0.6, -3.3, -0.1, 2.6, 3.3), plate, tweak=tweaks(sides=lit_edge), glow="E")
    d.pair("chest", "right_arm", "spike", (-3.8, -6.6, -1.0, -1.8, -3.9, 1.0), paint("C", top="E"), glow="E")

    # Leggings: seamed knee plates, tassets with glowing hems, an emberite clasp.
    d.pair("legs", "right_leg", "knee plate", (-2.1, 3.0, -3.1, 2.1, 4.8, -2.15), paint("C", bottom="D", front=ember_seam),
           tweak=tweaks(top=rim_light), glow="E")
    d.pair("legs", "right_leg", "knee plate, lower", (-2.2, 4.6, -3.25, 2.2, 6.4, -2.1), paint("C", front=ember_seam),
           tweak=tweaks(front=lit_edge), glow="E")
    tasset = paint("C", sides=ember_seam, bottom="E")
    d.pair("legs", "right_leg", "tasset", (-2.8, 0.6, -3.2, 2.8, 3.4, -2.2), tasset, tweak=tweaks(sides=lit_edge), glow="E")
    d.pair("legs", "right_leg", "tasset, side", (-3.2, 0.6, -2.2, -2.2, 3.4, 2.2), tasset, glow="E")
    d.box("legs", "body", "clasp", (-1.1, 6.9, -2.85, 1.1, 8.5, -2.3), paint("P", front="A"), glow="A")

    # Boots: charred cuffs with a glowing seam, plated sabatons.
    for leg in ("right_leg", "left_leg"):
        d.ring("boots", leg, "cuff", -3.5, 3.5, -3.5, 3.5, 5.8, 7.8, 0.8, seam, tweak=tweaks(sides=lit_edge), glow="E")
    sabaton = paint("C", front=ember_seam)
    top_lit = tweaks(top=rim_light)
    d.pair("boots", "right_leg", "sabaton", (-2.6, 8.8, -3.4, 2.6, 10.4, -2.5), sabaton, tweak=top_lit, glow="E")
    d.pair("boots", "right_leg", "sabaton, middle", (-2.75, 10.2, -3.8, 2.75, 11.6, -2.4), sabaton, tweak=top_lit, glow="E")
    d.pair("boots", "right_leg", "sabaton, toe", (-2.45, 11.4, -4.2, 2.45, 12.8, -2.6), sabaton, tweak=top_lit, glow="E")
    return d


def starwood():
    m = {"V": gi.IVORY, "I": gi.IVORY.shifted(texture=gi.flat), "D": gi.IVORY.shifted(base=1, texture=gi.flat),
         "U": gi.GOLD.shifted(texture=gi.flat), "T": gi.STARTWIG, "L": gi.STARLIGHT, "P": gi.ELDER}
    d = DetailSet("starwood", m, "a gold circlet with little lights under a crown of starwood twigs with glowing tips, drifting "
                                 "motes; a gold gorget, ivory shoulders with glowing stars, hanging mantle panels with gold hems "
                                 "and an Elder Heartwood brooch in a gold frame; ivory knee cops with a glowing star and gold-hemmed "
                                 "tassets; gold cuffs glinting with light, ivory sabatons with a glowing shard")
    gold = paint("U")
    twig_top = paint("T", top="L", sides=lambda u, v, w, h: "L" if v == 0 else "T")
    # Helmet: a gold circlet with lights at the knots, a tall central twig and four tilted ones, drifting motes.
    d.ring("helmet", "head", "circlet", -5.5, 5.5, -5.5, 5.5, -6.4, -5.4, 1, gold, tweak=tweaks(sides=lit_edge))
    d.pair("helmet", "head", "light", (3.0, -7.6, -5.9, 4.0, -6.6, -4.9), paint("L"), glow="L")
    d.box("helmet", "head", "light, centre", (-0.5, -7.8, -5.9, 0.5, -6.8, -4.9), paint("L"), glow="L")
    d.box("helmet", "head", "twig", (-0.7, -13, -0.7, 0.7, -9, 0.7), paint("T"), tweak=tweaks(sides=lit_edge))
    d.box("helmet", "head", "twig, tip", (-0.5, -14.2, -0.5, 0.5, -13, 0.5), paint("L"), glow="L")
    side = Pivot("twig_side", (-3.0, -9.0, 0.0), (0, 0, -22))
    d.pair("helmet", "head", "twig, side", (-0.7, -4, -0.7, 0.7, 0, 0.7), paint("T"), pivot=side, tweak=tweaks(sides=lit_edge))
    d.pair("helmet", "head", "twig, side tip", (-0.5, -5.4, -0.5, 0.5, -4, 0.5), paint("L"), pivot=side, glow="L")
    front = Pivot("twig_front", (-1.8, -9.0, -3.0), (-24, 0, -12))
    d.pair("helmet", "head", "twig, front", (-0.7, -3, -0.7, 0.7, 0, 0.7), paint("T"), pivot=front, tweak=tweaks(sides=lit_edge))
    d.pair("helmet", "head", "twig, front tip", (-0.5, -4.4, -0.5, 0.5, -3, 0.5), paint("L"), pivot=front, glow="L")
    rear = Pivot("twig_back", (-1.8, -9.0, 3.0), (24, 0, -12))
    d.pair("helmet", "head", "twig, back", (-0.7, -3, -0.7, 0.7, 0, 0.7), paint("T"), pivot=rear, tweak=tweaks(sides=lit_edge))
    d.pair("helmet", "head", "twig, back tip", (-0.5, -4.4, -0.5, 0.5, -3, 0.5), paint("L"), pivot=rear, glow="L")
    d.pair("helmet", "head", "mote", (5.8, -10.6, -2.0, 6.8, -9.6, -1.0), paint("L"), glow="L")

    # Chest: a gold gorget, ivory shoulders with a glowing star, hanging mantle panels with gold hems, the brooch.
    shingle_collar(d, "U")
    plate = paint("I", bottom="D", sides=bottom_row("I", "U"))
    d.pair("chest", "right_arm", "pauldron", (-4.8, -3.9, -3.8, 1.2, -1.1, 3.8), plate, tweak=tweaks(top=rim_light, sides=lit_edge))
    d.pair("chest", "right_arm", "pauldron, lower", (-5.1, -1.4, -3.5, 0.4, 0.9, 3.5), plate, tweak=tweaks(sides=lit_edge))
    d.pair("chest", "right_arm", "star", (-3.3, -4.6, -0.7, -1.7, -3.7, 0.9), paint("L"), glow="L")
    panel = paint("I", bottom="U", front=bottom_row("I", "U"), sides=bottom_row("I", "U"))
    d.pair("chest", "body", "mantle panel", (-5.2, -0.6, -3.6, -2.4, 6.4, -2.8), panel, tweak=tweaks(front=lit_edge))
    d.box("chest", "body", "mantle panel, back", (-4.3, -0.6, 2.7, 4.3, 7.0, 3.2), panel, tweak=tweaks(back=lit_edge))
    d.box("chest", "body", "brooch", (-1.2, 1.8, -3.3, 1.2, 4.2, -2.6), paint("P"), tweak=tweaks(front=["4.", ".."]), glow="P")
    d.box("chest", "body", "frame, top", (-2.3, 0.8, -3.2, 2.3, 1.8, -2.5), gold, tweak=tweaks(front=lit_edge))
    d.box("chest", "body", "frame, bottom", (-2.3, 4.2, -3.2, 2.3, 5.0, -2.5), gold)
    d.pair("chest", "body", "frame, side", (-2.3, 1.8, -3.2, -1.2, 4.2, -2.5), gold)
    d.ring("chest", "body", "belt", -5.35, 5.35, -3.35, 3.3, 7.2, 8.3, 0.7, gold, tweak=tweaks(sides=lit_edge))

    # Leggings: knee cops with a glowing star, gold-hemmed tassets, a gold belt with a small light.
    d.pair("legs", "right_leg", "knee cop", (-2.2, 3.4, -3.2, 2.2, 6.4, -2.1), paint("I", front=bottom_row("I", "U")),
           tweak=tweaks(top=rim_light))
    d.pair("legs", "right_leg", "knee star", (-0.5, 4.2, -3.5, 0.5, 5.2, -3.2), paint("L"), glow="L")
    tasset = paint("I", sides=bottom_row("I", "U"), bottom="U")
    d.pair("legs", "right_leg", "tasset", (-2.8, 0.6, -3.2, 2.8, 3.4, -2.2), tasset, tweak=tweaks(sides=lit_edge))
    d.pair("legs", "right_leg", "tasset, side", (-3.2, 0.6, -2.2, -2.2, 3.4, 2.2), tasset)
    d.ring("legs", "body", "belt", -4.8, 4.8, -2.8, 2.8, 7.1, 8.1, 0.6, gold, tweak=tweaks(sides=lit_edge))
    d.box("legs", "body", "light", (-0.7, 6.9, -2.85, 0.7, 8.2, -2.3), paint("L"), glow="L")

    # Boots: gold cuffs glinting with light, ivory sabatons, a glowing shard at the toe.
    glint = paint("U", sides=lambda u, v, w, h: "L" if v == 0 and u % 3 == 1 else "U")
    for leg in ("right_leg", "left_leg"):
        d.ring("boots", leg, "cuff", -3.3, 3.3, -3.3, 3.3, 5.9, 7.2, 0.7, glint, tweak=tweaks(sides=lit_edge), glow="L")
    sabaton = paint("I", front=bottom_row("I", "U"))
    top_lit = tweaks(top=rim_light)
    d.pair("boots", "right_leg", "sabaton", (-2.6, 8.8, -3.4, 2.6, 10.4, -2.5), sabaton, tweak=top_lit)
    d.pair("boots", "right_leg", "sabaton, middle", (-2.75, 10.2, -3.8, 2.75, 11.6, -2.4), sabaton, tweak=top_lit)
    d.pair("boots", "right_leg", "sabaton, toe", (-2.45, 11.4, -4.2, 2.45, 12.8, -2.6), sabaton, tweak=top_lit)
    d.pair("boots", "right_leg", "toe shard", (-0.5, 10.4, -5.6, 0.5, 11.6, -4.2), paint("I", front="L"), glow="L")
    return d


# ---------------------------------------------------------------- combat sets
STRIPS = {1: (-4.2, 0.9, -3.15, 4.2, 2.7, -2.4), 2: (-4.4, 2.5, -3.2, 4.4, 4.3, -2.36), 3: (-4.55, 4.1, -3.25, 4.55, 5.9, -2.32),
          4: (-4.6, 5.7, -3.5, 4.6, 7.5, -2.28)}


def plate_body(d, *, strip, collar, belt, buckle, pauldron, knee, cuff, boot, glow=None, strips=(1, 2, 3, 4), tassets=None,
               back_strips=True):
    """Chest, leggings and boots shared by the plated combat sets: stepped strips, a belt, three stacked pauldrons,
    two knee plates, a cuff and a toe or sabaton. Each set paints it differently and adds its own pieces."""
    shingle_collar(d, collar)
    for i in strips:
        d.box("chest", "body", f"strip {i}", STRIPS[i], strip, tweak=tweaks(front=lit_edge), glow=glow)
    if back_strips:
        d.box("chest", "body", "strip, back 1", (-4.2, 1.0, 2.6, 4.2, 2.8, 3.25), strip, glow=glow)
        d.box("chest", "body", "strip, back 2", (-4.4, 2.6, 2.7, 4.4, 4.4, 3.2), strip, glow=glow)
    d.ring("chest", "body", "belt", -5.4, 5.4, -3.4, 3.3, 8.3, 9.5, 0.8, belt, tweak=tweaks(sides=lit_edge))
    d.box("chest", "body", "buckle", (-1.0, 7.9, -3.7, 1.0, 9.45, -3.2), buckle, glow=glow)
    d.pair("chest", "right_arm", "pauldron", (-4.8, -3.9, -3.8, 1.2, -1.1, 3.8), pauldron, tweak=tweaks(top=rim_light, sides=lit_edge),
           glow=glow)
    d.pair("chest", "right_arm", "pauldron, middle", (-5.1, -1.4, -3.5, 0.4, 0.9, 3.5), pauldron, tweak=tweaks(sides=lit_edge), glow=glow)
    d.pair("chest", "right_arm", "pauldron, lower", (-5.3, 0.6, -3.3, -0.1, 2.6, 3.3), pauldron, tweak=tweaks(sides=lit_edge), glow=glow)
    d.pair("legs", "right_leg", "knee plate", (-2.1, 3.0, -3.1, 2.1, 4.8, -2.15), knee, tweak=tweaks(top=rim_light), glow=glow)
    d.pair("legs", "right_leg", "knee plate, lower", (-2.2, 4.6, -3.25, 2.2, 6.4, -2.1), knee, tweak=tweaks(front=lit_edge), glow=glow)
    if tassets:
        d.pair("legs", "right_leg", "tasset", (-2.8, 0.6, -3.2, 2.8, 3.4, -2.2), tassets, tweak=tweaks(sides=lit_edge), glow=glow)
        d.pair("legs", "right_leg", "tasset, side", (-3.2, 0.6, -2.2, -2.2, 3.4, 2.2), tassets, glow=glow)
    d.ring("legs", "body", "belt", -4.8, 4.8, -2.8, 2.8, 7.1, 8.1, 0.6, belt)
    d.box("legs", "body", "clasp", (-1.1, 6.9, -2.85, 1.1, 8.5, -2.3), buckle, glow=glow)
    for leg in ("right_leg", "left_leg"):
        d.ring("boots", leg, "cuff", -3.5, 3.5, -3.5, 3.5, 5.8, 7.8, 0.8, cuff, tweak=tweaks(sides=lit_edge), glow=glow)
    if boot == "toe":
        d.pair("boots", "right_leg", "toe cap", (-2.5, 10.3, -3.6, 2.5, 12.8, -2.4), paint("T", front=rows(("T", 2), ("D", 1))),
               tweak=tweaks(top=rim_light, front=lit_edge))
    elif boot is not None:
        top_lit = tweaks(top=rim_light)
        d.pair("boots", "right_leg", "sabaton", (-2.6, 8.8, -3.4, 2.6, 10.4, -2.5), boot, tweak=top_lit, glow=glow)
        d.pair("boots", "right_leg", "sabaton, middle", (-2.75, 10.2, -3.8, 2.75, 11.6, -2.4), boot, tweak=top_lit, glow=glow)
        d.pair("boots", "right_leg", "sabaton, toe", (-2.45, 11.4, -4.2, 2.45, 12.8, -2.6), boot, tweak=top_lit, glow=glow)


def fangguard():
    m = {"T": gi.FANG_LEATHER, "D": gi.FANG_LEATHER.shifted(base=1, texture=gi.flat), "K": gi.FANG_LEATHER.shifted(base=0, texture=gi.flat),
         "F": gi.FANG_BONE, "H": gi.FANG_BONE.shifted(base=1, texture=gi.flat)}
    d = DetailSet("fangguard", m, "a dark leather hood with a drape and a jaw of bone fangs for a brim, fangs along the sides and "
                                  "back; leather panels studded with fangs, a fang buckle, upswept fang shoulder spikes; fang "
                                  "knee studs; leather boot cuffs and toe caps")
    d.box("helmet", "head", "crown", (-4, -10, -4, 4, -9, 4), paint("T"), tweak=tweaks(top=rim_light))
    drape = paint("T", bottom="D", sides=bottom_row("T", "D"))
    d.box("helmet", "head", "hood, back", (-5.4, -8.4, 3.8, 5.4, -2.0, 6.0), drape, tweak=tweaks(sides=lit_edge))
    d.pair("helmet", "head", "hood, side", (-5.6, -8.4, -4.0, -4.8, -2.0, 3.8), drape, tweak=tweaks(sides=lit_edge))
    d.ring("helmet", "head", "brim band", -5.5, 5.5, -5.5, 5.5, -6.6, -5.6, 1, paint("D"), tweak=tweaks(sides=lit_edge))
    fang = paint("F", front=rows(("F", 1), ("H", 1)), sides=bottom_row("F", "H"))
    for i, x in enumerate((0.3, 1.9, 3.5)):
        d.pair("helmet", "head", f"fang {i + 1}", (-x - 1.0, -5.7, -5.95, -x, -4.3, -5.35), fang, tweak=tweaks(sides=lit_edge))
    for i, z in enumerate((-3.2, -1.0, 1.2, 3.0)):
        d.pair("helmet", "head", f"fang, side {i + 1}", (-6.1, -5.8, z, -5.5, -3.8, z + 1.0), fang)
    for i, x in enumerate((0.3, 2.1, 3.9)):
        d.pair("helmet", "head", f"fang, back {i + 1}", (-x - 1.0, -5.8, 6.0, -x, -3.8, 6.6), fang)

    plate_body(d, strip=paint("T", bottom="D", front=bottom_row("T", "D"), sides=bottom_row("T", "D")), collar="D",
               belt=paint("D"), buckle=paint("F"), pauldron=paint("T", bottom="D", sides=bottom_row("T", "D")),
               knee=paint("T", bottom="D", front=bottom_row("T", "D")), cuff=paint("T", bottom="D", sides=bottom_row("T", "D")),
               boot="toe", strips=(2, 4))
    spike = Pivot("fang_spike", (-3.6, -3.9, 0.0), (0, 0, -25))
    d.pair("chest", "right_arm", "shoulder fang", (-0.8, -3.6, -0.8, 0.8, 0, 0.8), paint("F", top="H", sides=bottom_row("F", "H")),
           pivot=spike, tweak=tweaks(sides=lit_edge))
    for i, (x, y) in enumerate(((2.5, 1.3), (3.6, 3.1), (2.5, 6.2))):
        d.pair("chest", "body", f"stud {i + 1}", (x, y, -3.6, x + 0.9, y + 0.8, -3.3), paint("F"))
    d.pair("legs", "right_leg", "fang stud", (-0.5, 4.0, -3.8, 0.5, 5.2, -3.3), paint("F", front="H"))
    return d


def stormplate():
    m = {"S": gi.SKYPLATE.shifted(texture=gi.polish), "P": gi.SKYPLATE.shifted(texture=gi.flat), "D": gi.SKYPLATE.shifted(base=0, texture=gi.flat),
         "Y": gi.BOLT.shifted(base=3, texture=gi.flat), "R": gi.BOLT.shifted(base=4, texture=gi.flat), "T": gi.SKYPLATE.shifted(texture=gi.flat)}
    d = DetailSet("stormplate", m, "a skyiron helm with yellow brow bands, a tall lightning-bolt crest with a glowing tip, a nose "
                                   "guard and cheek plates; stepped plates with a glowing bolt rune down the chest and back, "
                                   "yellow-edged pauldrons with glowing studs; rune-marked knee plates; yellow-edged sabatons")
    plate = paint("S", bottom="D", sides=bottom_row("S", "Y"))
    d.box("helmet", "head", "crown", (-4, -10, -4, 4, -9, 4), paint("S"), tweak=tweaks(top=rim_light))
    d.box("helmet", "head", "ridge", (-0.6, -11.4, -4.4, 0.6, -10, 4.4), paint("S", top="Y"), tweak=tweaks(top=lit_edge))
    for i, (r, y0) in enumerate(((5.2, -8.8), (5.5, -6.9))):
        d.ring("helmet", "head", f"plate band {i + 1}", -r, r, -r, r, y0, y0 + 2.0, 0.8, plate, tweak=tweaks(sides=lit_edge))
    d.box("helmet", "head", "nose guard", (-0.6, -6.0, -5.7, 0.6, -2.2, -5.1), paint("S"), tweak=tweaks(front=lit_edge))
    d.pair("helmet", "head", "cheek guard", (-5.6, -5.0, -5.6, -3.6, -1.0, -1.0), paint("S", sides=bottom_row("S", "Y")),
           tweak=tweaks(sides=lit_edge))
    d.box("helmet", "head", "bolt crest, 1", (-0.55, -12.4, -3.4, 0.55, -10.4, -2.4), paint("Y"), tweak=tweaks(sides=lit_edge), glow="R")
    d.box("helmet", "head", "bolt crest, 2", (-0.5, -14.0, -2.8, 0.5, -12.2, -1.8), paint("Y"), glow="R")
    d.box("helmet", "head", "bolt crest, 3", (-0.45, -15.6, -2.2, 0.45, -13.8, -1.2), paint("Y"), glow="R")
    d.box("helmet", "head", "bolt crest, tip", (-0.5, -16.8, -1.8, 0.5, -15.4, -0.8), paint("R"), glow="R")

    plate_body(d, strip=paint("S", bottom="D", front=bottom_row("S", "Y"), sides=bottom_row("S", "Y")), collar="P",
               belt=paint("P", sides=bottom_row("P", "Y")), buckle=paint("Y"), pauldron=plate,
               knee=paint("S", bottom="D", front=bottom_row("S", "Y")), cuff=paint("S", bottom="D", sides=bottom_row("S", "Y")),
               boot=paint("S", front=bottom_row("S", "Y")), strips=(2, 4),
               tassets=paint("S", sides=bottom_row("S", "Y"), bottom="Y"))
    d.box("chest", "body", "rune, 1", (-0.2, 0.8, -3.3, 1.2, 2.0, -2.5), paint("R"), glow="R")
    d.box("chest", "body", "rune, 2", (-1.2, 2.0, -3.3, 0.2, 3.2, -2.5), paint("R"), glow="R")
    d.box("chest", "body", "rune, 3", (-0.2, 3.2, -3.3, 1.2, 4.6, -2.5), paint("R"), glow="R")
    d.pair("chest", "right_arm", "stud", (-3.4, -4.6, -0.5, -2.4, -3.9, 0.5), paint("R"), glow="R")
    d.pair("legs", "right_leg", "knee rune", (-0.5, 4.0, -3.5, 0.5, 5.4, -3.3), paint("R"), glow="R")
    return d


def riftweave():
    m = {"C": gi.RIFT_CLOTH, "D": gi.RIFT_CLOTH.shifted(base=0, texture=gi.flat), "F": gi.RIFT_CLOTH.shifted(base=3, texture=gi.flat),
         "M": gi.RIFT_SEAM}
    d = DetailSet("riftweave", m, "a deep violet cowl with a bent peak, a drape and a thrust-forward rim, a rift seam glowing "
                                  "across the brow and down the back; folded cloth hanging crooked across the chest beside "
                                  "glowing rift seams, bell sleeves, a sash with a magenta knot and a ragged hem; crooked "
                                  "tassets and knee folds; ragged boot cuffs with curled toes")

    def ragged(salt):
        return lambda u, v, w, h: "." if v >= h - 1 and hashed(u, v, w, salt) % 2 == 0 else "C"

    d.box("helmet", "head", "crown", (-4, -10, -4, 4, -9, 4), paint("C"), tweak=tweaks(top=rim_light))
    tip = Pivot("tip", (0.5, -10, 0), (-32, 0, 14))
    d.box("helmet", "head", "peak", (-1.2, -4.5, -1.2, 1.2, 0, 1.2), paint("C", top="F"), pivot=tip, tweak=tweaks(sides=lit_edge))
    d.box("helmet", "head", "peak, seam", (-0.5, -6.5, -0.5, 0.5, -4.5, 0.5), paint("M"), pivot=tip, glow="M")
    drape = paint("C", bottom="D", sides=bottom_row("C", "D"))
    d.box("helmet", "head", "hood, back", (-5.4, -8.4, 3.8, 5.4, -2.0, 6.0), drape, tweak=tweaks(sides=lit_edge))
    d.pair("helmet", "head", "hood, side", (-5.6, -8.4, -4.0, -4.8, -2.0, 3.8), drape, tweak=tweaks(sides=lit_edge))
    d.box("helmet", "head", "rim, brow", (-3.2, -7.0, -6.6, 3.2, -5.0, -4.6), paint("C", front=bottom_row("C", "D")),
          tweak=tweaks(sides=lit_edge))
    d.pair("helmet", "head", "rim, cheek", (-5.9, -7.0, -6.6, -3.2, 0.2, -4.6), paint("C", sides=ragged(2)), tweak=tweaks(sides=lit_edge))
    d.box("helmet", "head", "seam, brow", (-2.6, -5.0, -6.9, 2.6, -4.5, -6.6), paint("M"), glow="M")
    d.box("helmet", "head", "seam, back", (-0.5, -8.0, 6.0, 0.5, -1.0, 6.5), paint("M"), glow="M")

    shingle_collar(d, "D")
    d.pair("chest", "right_arm", "mantle", (-4.8, -3.8, -3.7, 0.9, -0.6, 3.7), paint("C", bottom="D", sides=bottom_row("C", "D")),
           tweak=tweaks(top=rim_light, sides=lit_edge))
    d.pair("chest", "right_arm", "bell sleeve", (-4.9, 2.8, -3.6, 2.9, 4.2, 3.6), paint("C", sides=ragged(5)), tweak=tweaks(sides=lit_edge))
    fold = Pivot("fold", (3.6, 0.9, -3.45), (0, 0, -22))
    d.pair("chest", "body", "fold", (-0.7, 0, -0.3, 0.7, 4.6, 0.3), paint("F"), pivot=fold, tweak=tweaks(sides=lit_edge))
    d.pair("chest", "body", "fold, seam", (0.7, 0, -0.25, 1.2, 4.4, 0.25), paint("M"), pivot=fold, glow="M")
    d.pair("chest", "body", "fold, lower", (2.4, 4.8, -3.55, 4.0, 7.6, -3.1), paint("C", sides=ragged(7)), tweak=tweaks(front=lit_edge))
    d.box("chest", "body", "seam, back", (-0.5, 0.6, 2.7, 0.5, 8.0, 3.15), paint("M"), glow="M")
    d.pair("chest", "body", "back fold", (0.9, 0.8, 2.7, 3.6, 7.4, 3.25), paint("C", sides=ragged(8)))
    d.ring("chest", "body", "sash", -5.2, 5.2, -3.3, 3.2, 6.6, 7.8, 0.6, paint("D"), tweak=tweaks(sides=lit_edge))
    d.box("chest", "body", "knot", (-1, 6.2, -3.7, 1, 8.2, -3.2), paint("M"), glow="M")
    d.ring("chest", "body", "hem", -5.4, 5.4, -3.5, 3.3, 8.0, 9.4, 0.9, paint("C", sides=ragged(9)), tweak=tweaks(sides=lit_edge))

    tasset = paint("C", sides=ragged(10), bottom="D")
    d.pair("legs", "right_leg", "tasset", (-2.8, 0.6, -3.2, 2.8, 3.4, -2.2), tasset, tweak=tweaks(sides=lit_edge))
    d.pair("legs", "right_leg", "tasset, side", (-3.2, 0.6, -2.2, -2.2, 3.4, 2.2), tasset)
    d.pair("legs", "right_leg", "knee fold", (-2.2, 4.0, -3.1, 2.2, 6.2, -2.2), paint("C", front=bottom_row("C", "D")),
           tweak=tweaks(top=rim_light))
    d.pair("legs", "right_leg", "knee seam", (-0.4, 4.2, -3.4, 0.4, 6.0, -3.1), paint("M"), glow="M")
    d.ring("legs", "body", "sash", -4.8, 4.8, -2.8, 2.8, 7.1, 8.1, 0.6, paint("D"))
    for leg in ("right_leg", "left_leg"):
        d.ring("boots", leg, "cuff", -3.5, 3.5, -3.5, 3.5, 5.6, 7.8, 0.8, paint("C", sides=ragged(11)), tweak=tweaks(sides=lit_edge))
    d.pair("boots", "right_leg", "toe", (-2.5, 10.5, -4.6, 2.5, 12.8, -2.4), paint("C", front=rows(("C", 2), ("D", 1))),
           tweak=tweaks(top=rim_light))
    d.pair("boots", "right_leg", "toe curl", (-1.0, 9.8, -5.8, 1.0, 11.4, -4.6), paint("D", front="M"), glow="M")
    return d


def cinderguard():
    m = {"P": gi.CHAR.shifted(texture=gi.shingle), "B": gi.CHAR.shifted(texture=gi.flat), "D": gi.CHAR.shifted(base=1, texture=gi.flat),
         "E": gi.EMBER.shifted(texture=gi.flat), "A": gi.EMBERITE}

    def ember_seam(u, v, w, h):
        return "E" if v == h - 1 and hashed(u, v, w, 9) % 3 else "P"

    d = DetailSet("cinderguard", m, "a blackened great helm with a visor that glows like coals, plate bands with ember seams, a "
                                    "crest of three spikes tipped in ember; stepped plates with ember seams round a glowing "
                                    "core in a plate frame, pauldrons with spikes; seamed knee plates and tassets; sabatons "
                                    "with ember toes")
    plate = paint("P", bottom="D", sides=ember_seam)
    d.box("helmet", "head", "crown", (-4, -10, -4, 4, -9, 4), paint("P"), tweak=tweaks(top=rim_light))
    for i, (r, y0) in enumerate(((5.2, -8.8), (5.5, -6.9))):
        d.ring("helmet", "head", f"plate band {i + 1}", -r, r, -r, r, y0, y0 + 2.0, 0.8, paint("P", bottom="D", sides=ember_seam),
               tweak=tweaks(sides=lit_edge), glow="E")
    d.box("helmet", "head", "visor", (-3.8, -4.7, -5.6, 3.8, -3.3, -5.0), paint("E"), tweak=tweaks(front=lit_edge), glow="E")
    d.box("helmet", "head", "visor, brow", (-4.2, -5.7, -5.9, 4.2, -4.7, -5.3), paint("B"), tweak=tweaks(front=lit_edge))
    d.pair("helmet", "head", "cheek guard", (-5.6, -3.3, -5.6, -3.8, -0.6, -1.0), paint("P", sides=ember_seam), glow="E")
    d.box("helmet", "head", "crest", (-0.6, -11.6, -4.0, 0.6, -10, 4.0), paint("P", top="E"), tweak=tweaks(top=lit_edge), glow="E")
    d.box("helmet", "head", "crest spike", (-0.6, -14.0, -0.6, 0.6, -11.6, 0.6), paint("B"))
    d.box("helmet", "head", "crest tip", (-0.5, -15.0, -0.5, 0.5, -14.0, 0.5), paint("A"), glow="A")
    d.pair("helmet", "head", "spark", (3.6, -12.0, -1.0, 4.6, -11.0, 0.0), paint("E"), glow="E")

    plate_body(d, strip=paint("P", bottom="D", front=ember_seam, sides=ember_seam), collar="B",
               belt=paint("B"), buckle=paint("A"), pauldron=plate, knee=paint("P", bottom="D", front=ember_seam),
               cuff=paint("P", bottom="D", sides=ember_seam), boot=paint("P", front=ember_seam), glow="E",
               tassets=paint("P", sides=ember_seam, bottom="E"))
    d.box("chest", "body", "core", (-1.3, 2.7, -3.3, 1.3, 4.5, -2.6), paint("A"), tweak=tweaks(front=["4..", "..."]), glow="A")
    d.pair("chest", "body", "stud", (2.4, 1.3, -3.5, 3.2, 2.1, -3.2), paint("A"), glow="A")
    d.pair("chest", "right_arm", "spike", (-3.8, -6.6, -1.0, -1.8, -3.9, 1.0), paint("B", top="E"), glow="E")
    d.pair("boots", "right_leg", "toe ember", (-0.6, 11.9, -4.6, 0.6, 12.6, -4.3), paint("A"), glow="A")
    return d


def starforged():
    m = {"N": gi.NIGHTSTEEL.shifted(texture=gi.polish), "B": gi.NIGHTSTEEL.shifted(texture=gi.flat), "D": gi.NIGHTSTEEL.shifted(base=0, texture=gi.flat),
         "S": gi.SILVER.shifted(texture=gi.flat), "P": gi.PRISM_GLOW, "W": gi.STARWHITE}
    d = DetailSet("starforged", m, "a silver circlet under a crown of tilted prism points with glowing tips and drifting stars; "
                                   "night-blue plates with silver trim, glowing stars scattered over the chest and shoulders, "
                                   "a silver gorget; star knee plates and silver-hemmed tassets; silver cuffs and a prism shard "
                                   "at each toe")
    silver = paint("S")
    plate = paint("N", bottom="D", sides=bottom_row("N", "S"))
    d.ring("helmet", "head", "circlet", -5.5, 5.5, -5.5, 5.5, -6.4, -5.4, 1, silver, tweak=tweaks(sides=lit_edge))
    d.box("helmet", "head", "prism, brow", (-1, -8.0, -5.9, 1, -6.0, -4.9), paint("P"), tweak=tweaks(front=["4.", ".."]), glow="P")
    d.box("helmet", "head", "spire", (-0.8, -15, -0.8, 0.8, -9.5, 0.8), paint("P", sides=lambda u, v, w, h: "N" if v > 1 else "P"),
          tweak=tweaks(sides=lit_edge), glow="P")
    side = Pivot("point", (-3.0, -9.0, 0.0), (0, 0, -22))
    d.pair("helmet", "head", "point", (-0.8, -4, -0.8, 0.8, 0, 0.8), paint("S"), pivot=side, tweak=tweaks(sides=lit_edge))
    d.pair("helmet", "head", "point, tip", (-0.6, -6.5, -0.6, 0.6, -4, 0.6), paint("P"), pivot=side, glow="P")
    rear = Pivot("point_back", (-1.8, -9.0, 3.0), (24, 0, -12))
    d.pair("helmet", "head", "point, back", (-0.8, -3, -0.8, 0.8, 0, 0.8), paint("S"), pivot=rear, tweak=tweaks(sides=lit_edge))
    d.pair("helmet", "head", "point, back tip", (-0.6, -5, -0.6, 0.6, -3, 0.6), paint("P"), pivot=rear, glow="P")
    d.pair("helmet", "head", "star", (5.8, -10.6, -2.0, 6.8, -9.6, -1.0), paint("W"), glow="W")
    d.pair("helmet", "head", "star, low", (6.4, -6.8, 2.0, 7.2, -6.0, 2.8), paint("W"), glow="W")

    plate_body(d, strip=paint("N", bottom="D", front=bottom_row("N", "S"), sides=bottom_row("N", "S")), collar="S",
               belt=silver, buckle=paint("P"), pauldron=plate, knee=paint("N", bottom="D", front=bottom_row("N", "S")),
               cuff=paint("S", sides=lambda u, v, w, h: "W" if v == 0 and u % 3 == 1 else "S"), boot=paint("N", front=bottom_row("N", "S")),
               strips=(1, 3), tassets=paint("N", sides=bottom_row("N", "S"), bottom="S"))
    d.pair("chest", "right_arm", "star", (-3.3, -4.6, -0.7, -1.7, -3.7, 0.9), paint("W"), glow="W")
    d.pair("chest", "body", "star, chest", (2.6, 1.2, -3.55, 3.4, 2.0, -3.25), paint("W"), glow="W")
    d.pair("chest", "body", "star, low", (3.4, 5.0, -3.6, 4.2, 5.8, -3.3), paint("W"), glow="W")
    d.pair("legs", "right_leg", "knee star", (-0.5, 4.2, -3.5, 0.5, 5.2, -3.3), paint("W"), glow="W")
    d.pair("boots", "right_leg", "toe shard", (-0.5, 10.4, -5.6, 0.5, 11.6, -4.2), paint("P"), glow="P")
    return d


# ---------------------------------------------------------------- fishing sets
def angler():
    m = {"S": gi.STRAW, "F": gi.STRAW.shifted(texture=gi.flat), "Z": gi.STRAW.shifted(base=1), "Y": gi.STRAW.shifted(base=3, texture=gi.flat),
         "K": gi.OLIVE.shifted(base=0, texture=gi.flat), "V": gi.OLIVE, "P": gi.OLIVE.shifted(base=1, texture=gi.flat),
         "R": gi.CRIMSON, "W": gi.WOOL_WHITE.shifted(texture=gi.flat), "H": gi.IRON.shifted(texture=gi.flat),
         "B": gi.RUBBER_BLACK, "G": gi.RUBBER_GREEN, "D": gi.RUBBER_GREEN.shifted(base=1, texture=gi.flat)}
    d = DetailSet("angler", m, "a wide woven straw brim round a crown with an olive band, a red lure with a white tail and a "
                               "silver hook tucked in it; an olive vest with buttoned pockets on the chest and at the belt, a "
                               "tackle pouch on the hip; rubber wader knee patches and belt; folded rubber boot cuffs and toe caps")
    d.box("helmet", "head", "crown", (-4, -10, -4, 4, -9, 4), paint("S", top="F"), tweak=tweaks(top=rim_light))

    def brim(ring_of):
        def top(u, v, w, h):
            ring = ring_of(u, v, w, h)
            return "Y" if ring == 0 else "S" if ring == 1 else "Z"
        return paint("Z", top=top, front="S", back="S", right="S", left="S")

    weave = tweaks(top=lambda u, v, w, h: "-" if (u + v) % 3 == 0 and (u * v) % 2 else None)
    d.box("helmet", "head", "brim, front", (-7.5, -5, -7.5, 7.5, -4, -4.5), brim(lambda u, v, w, h: h - 1 - v), tweak=weave)
    d.box("helmet", "head", "brim, back", (-7.5, -5, 4.5, 7.5, -4, 7.5), brim(lambda u, v, w, h: v), tweak=weave)
    d.box("helmet", "head", "brim, right", (-7.5, -5, -4.5, -4.5, -4, 4.5), brim(lambda u, v, w, h: u), tweak=weave)
    d.box("helmet", "head", "brim, left", (4.5, -5, -4.5, 7.5, -4, 4.5), brim(lambda u, v, w, h: w - 1 - u), tweak=weave)
    d.ring("helmet", "head", "band", -5.5, 5.5, -5.5, 5.5, -6.5, -5.5, 1, paint("K"), tweak=tweaks(sides=lit_edge))
    d.box("helmet", "head", "lure", (2.2, -7.4, -6.4, 3.8, -5.6, -5.6), paint("R", front=["RR", "RW"]), tweak=tweaks(front=["4.", ".."]))
    d.box("helmet", "head", "lure, tail", (3.8, -6.9, -6.2, 4.8, -6.0, -5.8), paint("W"))
    d.box("helmet", "head", "hook", (2.6, -5.6, -6.3, 3.0, -4.6, -5.9), paint("H"))

    shingle_collar(d, "V")
    pocket = paint("P", front=["PP", "PH"], bottom="K")
    d.pair("chest", "body", "vest panel", (2.2, 0.8, -3.2, 4.4, 7.8, -2.5), paint("V", bottom="K", front=bottom_row("V", "K")),
           tweak=tweaks(front=lit_edge))
    d.pair("chest", "body", "pocket", (2.4, 1.8, -3.45, 4.2, 3.8, -3.2), pocket, tweak=tweaks(front=lit_edge))
    d.pair("chest", "body", "pocket, lower", (2.4, 5.0, -3.45, 4.2, 7.2, -3.2), pocket, tweak=tweaks(front=lit_edge))
    d.ring("chest", "body", "belt", -5.4, 5.4, -3.4, 3.3, 8.3, 9.5, 0.8, paint("K"), tweak=tweaks(sides=lit_edge))
    d.box("chest", "body", "buckle", (-1.0, 7.9, -3.7, 1.0, 9.45, -3.2), paint("H"), tweak=tweaks(front=["4.", ".."]))
    d.box("chest", "body", "tackle pouch", (-6.2, 5.8, -1.8, -5.0, 9.3, 1.8),
          paint("K", top="P"), tweak=tweaks(right=lit_edge))
    d.pair("legs", "right_leg", "knee patch", (-2.0, 3.6, -3.05, 2.0, 6.0, -2.1), paint("D", front=bottom_row("D", "G")),
           tweak=tweaks(top=rim_light))
    d.ring("legs", "body", "wader belt", -4.8, 4.8, -2.8, 2.8, 7.1, 8.1, 0.6, paint("G"))
    d.box("legs", "body", "wader buckle", (-1, 6.8, -2.85, 1, 8.4, -2.3), paint("H", sides="K"), tweak=tweaks(front=["4.", ".-"]))
    for leg in ("right_leg", "left_leg"):
        d.ring("boots", leg, "wader fold", -3.6, 3.6, -3.6, 3.6, 5.4, 8.0, 1.0, paint("G", bottom="D", sides=bottom_row("G", "D")),
               tweak=tweaks(sides=lit_edge))
    d.pair("boots", "right_leg", "toe cap", (-2.5, 10.3, -3.6, 2.5, 12.8, -2.4), paint("B", front=rows(("B", 2), ("D", 1))),
           tweak=tweaks(top=rim_light, front=lit_edge))
    return d


def reefwalker():
    m = {"C": gi.BRASS.shifted(texture=gi.flat), "K": gi.BRASS.shifted(base=1, texture=gi.flat), "H": gi.BRASS.shifted(base=4, texture=gi.flat),
         "R": gi.CORAL, "Q": gi.CORAL.shifted(base=4, texture=gi.flat), "V": gi.CANVAS, "S": gi.SHELL.shifted(texture=gi.flat),
         "T": gi.CORAL_TEAL.shifted(texture=gi.flat), "D": gi.CORAL_TEAL.shifted(base=1, texture=gi.flat)}
    d = DetailSet("reefwalker", m, "a brass diving helm built of two domed bands, side and back plates, a riveted porthole frame, "
                                   "a branching coral crest and an air valve with a hose; a brass collar, stepped shell plates "
                                   "over sailcloth, shell pauldrons, a rope belt; shell knee plates; canvas boot cuffs and long "
                                   "ribbed teal flippers")
    band = paint("C", bottom="K", sides=bottom_row("C", "K"))
    d.box("helmet", "head", "crown", (-4, -10, -4, 4, -9, 4), paint("C"), tweak=tweaks(top=rim_light))
    d.ring("helmet", "head", "dome band 1", -5.2, 5.2, -5.2, 5.2, -8.8, -6.8, 0.8, band, tweak=tweaks(sides=lit_edge))
    d.ring("helmet", "head", "dome band 2", -5.5, 5.5, -5.5, 5.5, -6.9, -4.9, 0.8, band, tweak=tweaks(sides=lit_edge))
    d.pair("helmet", "head", "side plate", (-5.9, -4.9, -4.6, -5.1, -0.8, 4.6), band, tweak=tweaks(sides=lit_edge))
    d.box("helmet", "head", "back plate", (-4.6, -4.9, 5.1, 4.6, -0.8, 5.9), band, tweak=tweaks(sides=lit_edge))
    rivets = lambda u, v, w, h: "H" if u in (0, w - 1) else "C"
    d.box("helmet", "head", "frame, top", (-4.4, -5.4, -5.9, 4.4, -4.4, -5.1), paint("C", front=rivets), tweak=tweaks(front=lit_edge))
    d.box("helmet", "head", "frame, bottom", (-4.4, -1.2, -5.9, 4.4, -0.2, -5.1), paint("C", front=rivets))
    d.pair("helmet", "head", "frame, side", (-4.4, -4.4, -5.9, -3.4, -1.2, -5.1), paint("C"))
    d.ring("helmet", "head", "neck ring", -5.3, 5.3, -5.3, 5.3, -0.2, 1.4, 1.0, paint("K", sides=bottom_row("K", "C")),
           tweak=tweaks(sides=lit_edge))
    d.box("helmet", "head", "valve", (-1.4, -6.6, 5.9, 1.4, -4.4, 7.4), paint("C", back=["HHH", "CKC"]), tweak=tweaks(sides=lit_edge))
    d.box("helmet", "head", "hose", (-0.7, -4.4, 6.2, 0.7, 0.2, 7.0), paint("K"))
    d.box("helmet", "head", "coral", (-0.7, -13.5, -0.7, 0.7, -10, 0.7), paint("R", top="Q"), tweak=tweaks(sides=lit_edge))
    branch = Pivot("branch", (-1.2, -10, 0), (0, 0, 30))
    d.pair("helmet", "head", "coral branch", (-0.7, -4.5, -0.7, 0.7, 0, 0.7), paint("R"), pivot=branch, tweak=tweaks(sides=lit_edge))
    d.pair("helmet", "head", "coral branch, tip", (-0.5, -6.0, -0.5, 0.5, -4.5, 0.5), paint("Q"), pivot=branch)
    fore = Pivot("branch_fore", (0, -10, -1.0), (-30, 0, 0))
    d.box("helmet", "head", "coral branch, fore", (-0.7, -4.0, -0.7, 0.7, 0, 0.7), paint("R"), pivot=fore)
    d.box("helmet", "head", "coral branch, fore tip", (-0.5, -5.4, -0.5, 0.5, -4.0, 0.5), paint("Q"), pivot=fore)

    plate_body(d, strip=paint("S", bottom="V", front=bottom_row("S", "V"), sides=bottom_row("S", "V")), collar="C",
               belt=paint("K"), buckle=paint("C"), pauldron=paint("S", bottom="V", sides=bottom_row("S", "V")),
               knee=paint("S", bottom="V", front=bottom_row("S", "V")), cuff=paint("V", bottom="K", sides=bottom_row("V", "K")),
               boot=None)
    ribs = lambda u, v, w, h: "D" if u % 2 == 0 else "T"
    flipper = paint("T", top=ribs, sides="D", bottom="D")
    d.pair("boots", "right_leg", "flipper", (-3.4, 12.2, -8.6, 3.4, 12.7, -2.0), flipper, tweak=tweaks(top=rim_light))
    d.pair("boots", "right_leg", "flipper, tip", (-4.4, 12.2, -10.2, 4.4, 12.7, -8.6), flipper)
    d.pair("boots", "right_leg", "boot", (-2.6, 9.0, -3.4, 2.6, 11.2, -2.5), paint("T", front=bottom_row("T", "D")),
           tweak=tweaks(top=rim_light))
    return d


def abyssal():
    m = {"N": gi.DEEP_NAVY, "D": gi.DEEP_NAVY.shifted(base=0, texture=gi.flat), "P": gi.PEARL_WHITE, "C": gi.CYAN_GLOW,
         "I": gi.DARK_IRON.shifted(texture=gi.flat), "B": gi.DARK_IRON.shifted(base=1, texture=gi.flat)}
    d = DetailSet("abyssal", m, "a deep navy hood with a drape, a pearl brow and pearl cheek linings, glowing cyan strands hanging "
                                "from the hem; a pearl collar, a long mantle with glowing cyan drips from its edge and the "
                                "shoulders, a cord belt; heavy navy tassets; iron boot bands and sabatons with a cyan glint")
    drape = paint("N", bottom="D", sides=bottom_row("N", "D"))

    def drip(salt):
        def pick(u, v, w, h):
            if v >= h - 1 and hashed(u, v, w, salt) % 2 == 0:
                return "."
            return "C"
        return pick

    d.box("helmet", "head", "crown", (-4, -10, -4, 4, -9, 4), paint("N"), tweak=tweaks(top=rim_light))
    d.box("helmet", "head", "hood, back", (-5.4, -8.4, 3.8, 5.4, -2.0, 6.0), drape, tweak=tweaks(sides=lit_edge))
    d.pair("helmet", "head", "hood, side", (-5.6, -8.4, -4.0, -4.8, -2.0, 3.8), drape, tweak=tweaks(sides=lit_edge))
    d.ring("helmet", "head", "pearl brow", -5.5, 5.5, -5.5, 5.5, -6.6, -5.6, 1, paint("P"), tweak=tweaks(sides=lit_edge))
    d.pair("helmet", "head", "pearl lining", (-5.5, -5.6, -5.9, -4.5, -0.6, -5.0), paint("P"), tweak=tweaks(sides=lit_edge))
    for i, x in enumerate((0.4, 2.0, 3.6)):
        d.pair("helmet", "head", f"pearl {i + 1}", (-x - 0.8, -7.4, -5.9, -x, -6.6, -5.1), paint("P"))
    for i, (x0, y1) in enumerate(((2.2, 1.6), (0.4, 0.4), (4.0, 0.8))):
        d.pair("helmet", "head", f"drip {i + 1}", (-x0 - 0.8, -3.0, 6.0, -x0, y1, 6.6), paint("C", sides=drip(i + 1)), glow="C")
    d.pair("helmet", "head", "drip, side", (-6.4, -4.0, -1.0, -5.6, 1.2, 0.0), paint("C", sides=drip(7)), glow="C")

    shingle_collar(d, "P")
    d.pair("chest", "right_arm", "mantle", (-4.8, -3.8, -3.7, 0.9, -0.6, 3.7), paint("N", bottom="D", sides=bottom_row("N", "D")),
           tweak=tweaks(top=rim_light, sides=lit_edge))
    d.pair("chest", "right_arm", "mantle, lower", (-5.1, -1.4, -3.5, 0.4, 0.9, 3.5), paint("N", bottom="D", sides=bottom_row("N", "C")),
           tweak=tweaks(sides=lit_edge), glow="C")
    for i, (z0, y1) in enumerate(((-2.8, 4.3), (-0.6, 3.3), (1.6, 4.1))):
        d.pair("chest", "right_arm", f"arm drip {i + 1}", (-5.3, 0.9, z0, -4.5, y1, z0 + 1.0), paint("C", sides=drip(20 + i)), glow="C")
    d.pair("chest", "body", "panel", (-5.2, -0.6, -3.6, -2.4, 5.4, -2.8), paint("N", bottom="D", front=bottom_row("N", "C"),
           sides=bottom_row("N", "D")), tweak=tweaks(front=lit_edge), glow="C")
    d.box("chest", "body", "panel, back", (-4.3, -0.6, 2.7, 4.3, 7.0, 3.1), paint("N", bottom="D", back=bottom_row("N", "C"),
          sides=bottom_row("N", "D")), glow="C")
    d.pair("chest", "body", "drip", (2.8, 5.4, -3.6, 3.6, 9.2, -3.0), paint("C", sides=drip(30)), glow="C")
    d.pair("chest", "body", "drip, outer", (4.2, 5.4, -3.6, 5.0, 8.0, -3.0), paint("C", sides=drip(31)), glow="C")
    d.ring("chest", "body", "belt", -5.2, 5.2, -3.3, 3.2, 6.6, 7.5, 0.6, paint("D"), tweak=tweaks(sides=lit_edge))
    d.box("chest", "body", "buckle", (-1, 6.2, -3.7, 1, 8.2, -3.2), paint("P"), tweak=tweaks(front=["4.", ".."]))

    tasset = paint("N", sides=bottom_row("N", "D"), bottom="D")
    d.pair("legs", "right_leg", "tasset", (-2.8, 0.6, -3.2, 2.8, 3.4, -2.2), tasset, tweak=tweaks(sides=lit_edge))
    d.pair("legs", "right_leg", "tasset, side", (-3.2, 0.6, -2.2, -2.2, 3.4, 2.2), tasset)
    d.pair("legs", "right_leg", "knee stud", (-0.5, 4.2, -3.4, 0.5, 5.2, -3.1), paint("C"), glow="C")
    d.ring("legs", "body", "belt", -4.8, 4.8, -2.8, 2.8, 7.1, 8.1, 0.6, paint("D"))
    for leg in ("right_leg", "left_leg"):
        d.ring("boots", leg, "iron band", -3.6, 3.6, -3.6, 3.6, 5.4, 7.6, 1.0, paint("I", sides=bottom_row("I", "B"), bottom="B"),
               tweak=tweaks(sides=lit_edge))
    sabaton = paint("I", front=bottom_row("I", "B"))
    top_lit = tweaks(top=rim_light)
    d.pair("boots", "right_leg", "sabaton", (-2.6, 8.8, -3.4, 2.6, 10.4, -2.5), sabaton, tweak=top_lit)
    d.pair("boots", "right_leg", "sabaton, middle", (-2.75, 10.2, -3.8, 2.75, 11.6, -2.4), sabaton, tweak=top_lit)
    d.pair("boots", "right_leg", "sabaton, toe", (-2.45, 11.4, -4.2, 2.45, 12.8, -2.6), sabaton, tweak=top_lit)
    d.pair("boots", "right_leg", "toe glint", (-0.4, 11.6, -4.6, 0.4, 12.2, -4.3), paint("C"), glow="C")
    return d


def leviathan():
    m = {"T": gi.SEA_TEAL.shifted(texture=gi.fish_scales), "F": gi.SEA_TEAL.shifted(base=3, texture=gi.flat),
         "D": gi.SEA_TEAL.shifted(base=0, texture=gi.flat), "G": gi.PALE_GOLD.shifted(texture=gi.flat),
         "H": gi.PALE_GOLD.shifted(base=4, texture=gi.flat), "A": gi.CYAN_GLOW}
    d = DetailSet("leviathan", m, "a pale gold circlet under a crown of sea-beast spines, curving out and back with glowing "
                                  "tips, over a scaled coif; scale-plate strips down the chest with gold edges, gold-edged "
                                  "pauldrons with fin spikes, fin tassets; scaled knee plates; gold-banded boot cuffs, fins "
                                  "on both sides of each boot")
    gold = paint("G")
    drape = paint("T", bottom="F", sides=bottom_row("T", "F"))
    d.box("helmet", "head", "crown", (-4, -10, -4, 4, -9, 4), paint("T"), tweak=tweaks(top=rim_light))
    d.box("helmet", "head", "coif, back", (-5.4, -8.4, 3.8, 5.4, -2.0, 6.0), drape, tweak=tweaks(sides=lit_edge))
    d.pair("helmet", "head", "coif, side", (-5.6, -8.4, -4.0, -4.8, -2.0, 3.8), drape, tweak=tweaks(sides=lit_edge))
    d.ring("helmet", "head", "circlet", -5.7, 5.7, -5.7, 5.7, -6.6, -5.4, 1, gold, tweak=tweaks(sides=lit_edge))
    d.box("helmet", "head", "gem", (-0.8, -8.0, -6.1, 0.8, -6.4, -5.3), paint("A"), glow="A")
    d.box("helmet", "head", "spire", (-0.8, -15, -0.8, 0.8, -9.5, 0.8), paint("G", top="H"), tweak=tweaks(sides=lit_edge))
    d.box("helmet", "head", "spire, tip", (-0.5, -16.2, -0.5, 0.5, -15, 0.5), paint("A"), glow="A")
    out = Pivot("spine", (-3.4, -9.0, 0.0), (0, 0, -28))
    d.pair("helmet", "head", "spine", (-0.8, -4.5, -0.8, 0.8, 0, 0.8), gold, pivot=out, tweak=tweaks(sides=lit_edge))
    d.pair("helmet", "head", "spine, tip", (-0.5, -6.4, -0.5, 0.5, -4.5, 0.5), paint("A"), pivot=out, glow="A")
    far = Pivot("spine_far", (-4.6, -8.2, 0.0), (0, 0, -62))
    d.pair("helmet", "head", "spine, far", (-0.7, -3.6, -0.7, 0.7, 0, 0.7), gold, pivot=far)
    d.pair("helmet", "head", "spine, far tip", (-0.5, -5.0, -0.5, 0.5, -3.6, 0.5), paint("A"), pivot=far, glow="A")
    fore = Pivot("spine_fore", (-1.8, -9.0, -3.0), (-26, 0, -12))
    d.pair("helmet", "head", "spine, front", (-0.7, -3.4, -0.7, 0.7, 0, 0.7), gold, pivot=fore)
    d.pair("helmet", "head", "spine, front tip", (-0.5, -4.8, -0.5, 0.5, -3.4, 0.5), paint("A"), pivot=fore, glow="A")
    rear = Pivot("spine_back", (-1.8, -9.0, 3.0), (26, 0, -12))
    d.pair("helmet", "head", "spine, back", (-0.7, -3.4, -0.7, 0.7, 0, 0.7), gold, pivot=rear)
    d.pair("helmet", "head", "spine, back tip", (-0.5, -4.8, -0.5, 0.5, -3.4, 0.5), paint("A"), pivot=rear, glow="A")

    plate_body(d, strip=paint("T", bottom="D", front=bottom_row("T", "G"), sides=bottom_row("T", "G")), collar="G",
               belt=gold, buckle=paint("A"), pauldron=paint("T", bottom="D", sides=bottom_row("T", "G")),
               knee=paint("T", bottom="D", front=bottom_row("T", "G")), cuff=paint("G", sides=bottom_row("G", "H")),
               boot=paint("T", front=bottom_row("T", "G")), glow="A",
               tassets=paint("F", sides=bottom_row("F", "G"), bottom="G"))
    fin = Pivot("fin", (-3.6, -3.9, 0.0), (0, 0, -18))
    d.pair("chest", "right_arm", "fin spike", (-0.4, -4.4, -1.8, 0.4, 0, 1.8), paint("F", top="G", sides=bottom_row("F", "G")), pivot=fin)
    d.pair("chest", "body", "scale gem", (2.6, 1.3, -3.5, 3.4, 2.1, -3.2), paint("A"), glow="A")
    d.pair("boots", "right_leg", "boot fin", (-4.0, 8.0, -1.2, -3.3, 11.4, 2.4), paint("F", sides=bottom_row("F", "G")))
    return d


# ---------------------------------------------------------------- region and dungeon sets
def glint(u, v, w, h):
    """One bright pixel in the top left corner of a face."""
    return "4" if (u, v) == (0, 0) else None


def along(pos, rot, length):
    """Where a spike of `length` standing on a hinge at `pos`, turned by `rot` (degrees), ends."""
    v = mat_vec(rotation(*(math.radians(r) for r in rot)), (0.0, -length, 0.0))
    return tuple(round(a + b, 3) for a, b in zip(pos, v))


def spike(d, piece, part, label, pos, rot, length, width, mat, tip=None, tip_len=1.4, tip_width=None, glow=None,
          mirrored=True, sides=None):
    """A slim box standing on a hinge at `pos`, tilted by `rot` (degrees), optionally capped by a tip box."""
    pv = Pivot(label, pos, rot)
    add = d.pair if mirrored else d.box
    h = width / 2
    add(piece, part, label, (-h, -length, -h, h, 0, h), paint(mat, sides=sides or mat), pivot=pv, tweak=tweaks(sides=lit_edge))
    if tip:
        t = (tip_width or width * 0.75) / 2
        add(piece, part, label + ", tip", (-t, -length - tip_len, -t, t, -length, t), paint(tip), pivot=pv, glow=glow)


def chain(d, piece, part, label, pos, segments, mat, tip=None, glow=None, mirrored=True, tip_len=1.4, tip_width=None):
    """Jointed spikes: each segment (rotation, length, width) starts where the one before it ends."""
    for i, (rot, length, width) in enumerate(segments):
        last = i == len(segments) - 1
        spike(d, piece, part, f"{label} {i + 1}", pos, rot, length, width, mat, tip=tip if last else None, tip_len=tip_len,
              tip_width=tip_width, glow=glow, mirrored=mirrored)
        pos = along(pos, rot, length)


def helm_bands(d, plate, edge=None, glow=None):
    d.box("helmet", "head", "crown", (-4, -10, -4, 4, -9, 4), paint("P"), tweak=tweaks(top=rim_light))
    for i, (r, y0) in enumerate(((5.2, -8.8), (5.5, -6.9))):
        d.ring("helmet", "head", f"plate band {i + 1}", -r, r, -r, r, y0, y0 + 2.0, 0.8, plate, tweak=tweaks(sides=lit_edge), glow=glow)


def blightforged():
    m = dict(gi._BF)
    m.update({"W": gi.BIRCH.shifted(base=2, texture=gi.flat), "w": gi.BIRCH.shifted(base=1, texture=gi.flat),
              "G": gi.ELDER, "g": gi.ELDER.shifted(base=2, texture=gi.flat)})
    d = DetailSet("blightforged", m, "a corrupted helm of purple-grey plate crowned with a dead pale-wood antler that branches "
                                     "into glowing blight crystals, a crystal spire and cheek plates; stepped plates with a "
                                     "glowing blight core, crimson-edged pauldrons sprouting wood thorns and crystals, a "
                                     "wooden belt; thorned knee plates; wooden boot cuffs and crystal toe shards")
    plate = paint("P", bottom="D", sides=bottom_row("P", "D"))
    helm_bands(d, plate)
    d.box("helmet", "head", "brow", (-4.2, -5.0, -5.9, 4.2, -3.8, -5.3), paint("K", front=bottom_row("K", "A")), tweak=tweaks(front=lit_edge))
    d.pair("helmet", "head", "cheek guard", (-5.6, -4.8, -5.6, -3.8, -0.8, -1.0), paint("P", sides=bottom_row("P", "A")),
           tweak=tweaks(sides=lit_edge))
    # dead-wood antler: a beam with two tines, crystals at every tip
    chain(d, "helmet", "head", "antler", (-3.0, -9.6, -0.6), [((0, 0, -30), 4.2, 1.4), ((0, 0, -72), 3.4, 1.1)], "W", tip="G", glow="G")
    chain(d, "helmet", "head", "antler tine", along((-3.0, -9.6, -0.6), (0, 0, -30), 2.4), [((-22, 0, -8), 3.2, 1.0)], "W", tip="G", glow="G")
    chain(d, "helmet", "head", "antler tine, back", (-2.4, -9.6, 2.0), [((-30, 0, -24), 3.6, 1.0)], "W", tip="G", glow="G")
    d.box("helmet", "head", "spire", (-0.9, -14.4, -0.9, 0.9, -10, 0.9), paint("g", sides=lambda u, v, w, h: "G" if v < 2 else "g"),
          tweak=tweaks(sides=lit_edge), glow="G")
    d.box("helmet", "head", "spire, tip", (-0.5, -16.0, -0.5, 0.5, -14.4, 0.5), paint("G"), glow="G")
    spike(d, "helmet", "head", "shard", (-1.2, -9.6, 0.4), (0, 0, -20), 3.8, 1.0, "g", tip="G", glow="G")
    spike(d, "helmet", "head", "shard, back", (-0.9, -9.6, 2.6), (-28, 0, -10), 3.4, 0.9, "g", tip="G", glow="G")

    plate_body(d, strip=paint("P", bottom="D", front=bottom_row("P", "A"), sides=bottom_row("P", "A")), collar="D",
               belt=paint("W", sides=bottom_row("W", "w")), buckle=paint("G"), pauldron=plate,
               knee=paint("P", bottom="D", front=bottom_row("P", "A")), cuff=paint("W", sides=bottom_row("W", "w")),
               boot=paint("P", front=bottom_row("P", "A")), strips=(1, 3), glow="G",
               tassets=paint("P", sides=bottom_row("P", "A"), bottom="A"))
    d.box("chest", "body", "core", (-1.3, 2.5, -3.3, 1.3, 4.6, -2.6), paint("G"), tweak=tweaks(front=glint), glow="G")
    chain(d, "chest", "right_arm", "thorn", (-3.6, -3.9, -1.6), [((0, 0, -28), 3.4, 1.3), ((0, 0, -62), 2.6, 1.0)], "W", tip="G", glow="G")
    chain(d, "chest", "right_arm", "thorn, back", (-3.4, -3.9, 1.6), [((-18, 0, -40), 3.2, 1.2)], "W", tip="G", glow="G")
    spike(d, "chest", "right_arm", "crystal", (-4.6, -1.2, 0.0), (0, 0, -64), 3.0, 1.2, "g", tip="G", glow="G")
    spike(d, "chest", "body", "belt shard", (-4.8, 8.6, -0.8), (0, 0, -38), 3.4, 1.0, "g", tip="G", glow="G")
    d.pair("chest", "body", "vein", (2.4, 1.2, -3.55, 3.2, 2.6, -3.3), paint("A"))
    spike(d, "legs", "right_leg", "knee thorn", (0.0, 4.6, -3.0), (74, 0, 0), 2.4, 1.0, "W", tip="G", tip_len=1.0, glow="G")
    spike(d, "boots", "right_leg", "toe shard", (0.0, 11.2, -3.6), (80, 0, 0), 2.0, 1.0, "g", tip="G", tip_len=1.0, glow="G")
    return d


def cinderplate():
    m = dict(gi._CP)
    m.update({"F": gi._CP["C"].shifted(texture=gi.flat)})

    def cracked(salt):
        def pick(u, v, w, h):
            n = hashed(u, v, w, salt) % 19
            return "A" if n in (0, 1) else "B" if n == 2 and u % 2 else "P"
        return pick

    def hem_ember(u, v, w, h):
        return "A" if v == h - 1 and hashed(u, v, w, 3) % 3 else "C"

    d = DetailSet("cinderplate", m, "a blackened bascinet with a glowing visor slit, a crest of swept-back plate fins edged in ember "
                                    "and an ash-cloth mantle; cracked stepped plates with ember seams and an ash-cloth tabard, "
                                    "ember-spiked pauldrons, a core of embers; cracked knee plates and sabatons with ember toes")
    plate = paint("P", bottom="D", sides=cracked(5))
    helm_bands(d, paint("P", bottom="D", sides=cracked(2)), glow="AB")
    d.box("helmet", "head", "visor", (-3.8, -4.4, -5.7, 3.8, -3.4, -5.1), paint("A", front=rows(("B", 1))), tweak=tweaks(front=lit_edge), glow="AB")
    d.box("helmet", "head", "visor, brow", (-4.3, -5.8, -5.9, 4.3, -4.6, -5.3), paint("K", front=bottom_row("K", "A")), tweak=tweaks(front=lit_edge), glow="A")
    d.pair("helmet", "head", "cheek guard", (-5.6, -3.4, -5.6, -3.8, -0.4, -1.0), paint("P", sides=cracked(4)), tweak=tweaks(sides=lit_edge), glow="A")
    for i, (z0, top) in enumerate(((-3.6, -11.0), (-1.9, -12.4), (-0.2, -12.0), (1.5, -11.2), (3.0, -10.6))):
        d.box("helmet", "head", f"fin {i + 1}", (-0.5, top, z0, 0.5, -10, z0 + 1.5), paint("P", top="A", sides=lambda u, v, w, h: "A" if v == 0 else "P"),
              tweak=tweaks(sides=lit_edge), glow="A")
    d.box("helmet", "head", "mantle", (-5.4, -8.4, 3.8, 5.4, -2.0, 6.0), paint("C", bottom="F", sides=hem_ember), tweak=tweaks(sides=lit_edge), glow="A")
    d.pair("helmet", "head", "mantle, side", (-5.6, -8.4, 0.6, -4.8, -2.0, 3.8), paint("C", sides=hem_ember), glow="A")
    d.pair("helmet", "head", "mote", (3.6, -12.4, -1.0, 4.4, -11.6, -0.2), paint("B"), glow="B")
    d.pair("helmet", "head", "mote, low", (5.9, -9.8, 1.0, 6.5, -9.2, 1.6), paint("A"), glow="A")

    plate_body(d, strip=paint("P", bottom="D", front=cracked(6), sides=cracked(7)), collar="K",
               belt=paint("K", sides=lambda u, v, w, h: "A" if v == h - 1 and u % 3 else "K"), buckle=paint("B"),
               pauldron=plate, knee=paint("P", bottom="D", front=cracked(8)), cuff=paint("P", bottom="D", sides=cracked(9)),
               boot=paint("P", front=cracked(10)), glow="AB", tassets=paint("P", sides=cracked(11), bottom="A"))
    d.box("chest", "body", "tabard", (-2.9, 5.4, -3.9, 2.9, 9.4, -3.25), paint("C", front=hem_ember, sides="F"), tweak=tweaks(front=lit_edge), glow="A")
    d.box("chest", "body", "core", (-1.3, 2.4, -3.3, 1.3, 4.4, -2.6), paint("B"), tweak=tweaks(front=glint), glow="B")
    for i, (x, y, w, h) in enumerate(((2.4, 1.0, 0.6, 1.8), (3.5, 3.2, 0.6, 1.4), (2.6, 4.8, 0.6, 1.2))):
        d.pair("chest", "body", f"crack {i + 1}", (x, y, -3.55, x + w, y + h, -3.3), paint("A"), glow="A")
    spike(d, "chest", "right_arm", "spike", (-3.6, -3.9, 0.0), (0, 0, -34), 3.6, 1.4, "K", tip="A", glow="A")
    spike(d, "chest", "right_arm", "spike, front", (-3.6, -3.9, -1.9), (24, 0, -48), 2.6, 1.0, "K", tip="A", glow="A")
    spike(d, "chest", "right_arm", "spike, back", (-3.6, -3.9, 1.9), (-24, 0, -48), 2.6, 1.0, "K", tip="A", glow="A")
    d.pair("boots", "right_leg", "toe ember", (-0.6, 11.9, -4.6, 0.6, 12.6, -4.3), paint("B"), glow="B")
    return d


def rimeguard():
    m = dict(gi._RG)
    m.update({"S": gi.RIME_STEEL.shifted(base=3, texture=gi.flat), "I": gi.ICE.shifted(base=2, texture=gi.flat)})

    def fur(salt):
        return frayed("C", salt)

    d = DetailSet("rimeguard", m, "a pale steel helm crowned with a ring of cursed ice spikes, a fur-lined brow and cheek flaps; "
                                  "stepped steel plates under a thick fur collar, fur-trimmed pauldrons bristling with glowing "
                                  "frost spikes and hanging icicles; ice-spiked knee plates; fur boot cuffs and icicle toes")
    plate = paint("P", bottom="D", sides=bottom_row("P", "A"))
    helm_bands(d, plate)
    d.ring("helmet", "head", "fur brow", -5.6, 5.6, -5.6, 5.6, -6.8, -5.4, 1.0, paint("C", bottom="C", sides=fur(1)), tweak=tweaks(sides=lit_edge))
    d.pair("helmet", "head", "fur flap", (-6.0, -5.4, -4.0, -5.0, -0.6, 1.0), paint("C", sides=fur(2)), tweak=tweaks(sides=lit_edge))
    d.pair("helmet", "head", "cheek guard", (-5.6, -5.4, -5.7, -3.8, -1.0, -4.8), paint("P", sides=bottom_row("P", "A")), tweak=tweaks(sides=lit_edge))
    d.box("helmet", "head", "fur, back", (-5.4, -6.6, 4.3, 5.4, -1.0, 6.0), paint("C", sides=fur(3)), tweak=tweaks(sides=lit_edge))
    d.box("helmet", "head", "spire", (-0.9, -15.0, -0.9, 0.9, -10, 0.9), paint("I", sides=lambda u, v, w, h: "A" if v < 3 else "I"),
          tweak=tweaks(sides=lit_edge), glow="A")
    d.box("helmet", "head", "spire, tip", (-0.5, -16.8, -0.5, 0.5, -15.0, 0.5), paint("B"), glow="B")
    for i, (z, rot, ln) in enumerate(((-2.8, (-18, 0, -30), 4.4), (0.0, (0, 0, -34), 5.0), (2.8, (18, 0, -30), 4.4))):
        spike(d, "helmet", "head", f"frost spike {i + 1}", (-2.4, -9.6, z), rot, ln, 1.2, "I", tip="B", tip_len=1.6, glow="B")
    spike(d, "helmet", "head", "frost spike, low", (-4.0, -9.4, 0.0), (0, 0, -70), 3.6, 1.1, "I", tip="B", tip_len=1.4, glow="B")

    plate_body(d, strip=paint("P", bottom="D", front=bottom_row("P", "A"), sides=bottom_row("P", "A")), collar="C",
               belt=paint("C", sides=fur(4)), buckle=paint("A"), pauldron=paint("P", bottom="D", sides=bottom_row("P", "A")),
               knee=paint("P", bottom="D", front=bottom_row("P", "A")), cuff=paint("C", sides=fur(5)),
               boot=paint("P", front=bottom_row("P", "A")), strips=(2, 4), glow="AB",
               tassets=paint("P", sides=bottom_row("P", "C"), bottom="C"))
    d.pair("chest", "right_arm", "fur mantle", (-5.4, -1.7, -3.9, -0.6, -0.2, 3.9), paint("C", sides=fur(6)), tweak=tweaks(top=rim_light))
    d.box("chest", "body", "frost core", (-1.2, 2.4, -3.3, 1.2, 4.6, -2.6), paint("A"), tweak=tweaks(front=glint), glow="A")
    chain(d, "chest", "right_arm", "ice spike", (-3.8, -3.9, 0.0), [((0, 0, -26), 4.4, 1.5)], "I", tip="B", tip_len=1.6, glow="B")
    chain(d, "chest", "right_arm", "ice spike, front", (-3.6, -3.9, -2.0), [((22, 0, -48), 3.0, 1.1)], "I", tip="B", glow="B")
    chain(d, "chest", "right_arm", "ice spike, back", (-3.6, -3.9, 2.0), [((-22, 0, -48), 3.0, 1.1)], "I", tip="B", glow="B")
    for i, (z0, y1) in enumerate(((-2.4, 4.2), (-0.4, 3.6), (1.6, 4.0))):
        d.pair("chest", "right_arm", f"icicle {i + 1}", (-5.0, 2.6, z0, -4.4, y1, z0 + 0.8), paint("A", sides=lambda u, v, w, h: "B" if v == h - 1 else "A"), glow="AB")
    spike(d, "legs", "right_leg", "knee spike", (0.0, 4.6, -3.0), (72, 0, 0), 2.6, 1.1, "I", tip="B", tip_len=1.2, glow="B")
    spike(d, "boots", "right_leg", "toe icicle", (0.0, 11.2, -3.6), (80, 0, 0), 2.4, 1.1, "I", tip="B", tip_len=1.2, glow="B")
    return d


def mirewoven():
    mud = gi.Material(["1e160c", "33261a", "4c3a26", "6a5236", "8a6e48"], texture=gi.speckle)
    m = dict(gi._MW)
    m.update({"M": gi.MOSS, "N": gi.MOSS.shifted(base=1, texture=gi.flat), "U": mud, "Y": mud.shifted(base=1, texture=gi.flat),
              "R": gi._REED.shifted(texture=gi.flat), "r": gi._REED.shifted(base=1, texture=gi.flat)})

    def dangle(salt, main="N"):
        def pick(u, v, w, h):
            if v >= h - 1 and hashed(u, v, w, salt) % 2 == 0:
                return "."
            return main
        return pick

    d = DetailSet("mirewoven", m, "a mud-stained hood with a reed-woven brow band, a crest of swaying reeds with cattail heads, "
                                  "moss clumps and hanging bog vines with toxic green glints; moss mantles, leather panels "
                                  "lashed with reed bands, a reed fringe at the waist and a mud-rope belt; mud knee pads with "
                                  "moss, reed shin bands; reed-wrapped boot cuffs and mud toe caps")
    drape = paint("P", bottom="D", sides=bottom_row("P", "D"))
    d.box("helmet", "head", "crown", (-4, -10, -4, 4, -9, 4), paint("P"), tweak=tweaks(top=rim_light))
    d.box("helmet", "head", "hood, back", (-5.4, -8.4, 3.8, 5.4, -2.0, 6.0), drape, tweak=tweaks(sides=lit_edge))
    d.pair("helmet", "head", "hood, side", (-5.6, -8.4, -4.0, -4.8, -2.0, 3.8), drape, tweak=tweaks(sides=lit_edge))
    d.ring("helmet", "head", "reed band", -5.5, 5.5, -5.5, 5.5, -6.8, -5.6, 1.0, paint("C"), tweak=tweaks(sides=lit_edge))
    d.box("helmet", "head", "moss", (-3.4, -11.0, -3.0, 0.6, -10, 1.2), paint("M", sides=dangle(1, "M")), tweak=tweaks(top=rim_light))
    d.box("helmet", "head", "moss, small", (1.2, -10.8, -1.0, 3.6, -10, 3.0), paint("N"))
    d.pair("helmet", "head", "moss, side", (-6.0, -8.0, -2.8, -5.0, -5.9, 0.6), paint("M", sides=dangle(2, "M")))
    for i, (x, rot, ln) in enumerate(((-2.8, (-10, 0, -22), 6.0), (-1.2, (-14, 0, -8), 7.2), (0.8, (-6, 0, 8), 6.4), (2.4, (-18, 0, 20), 5.4))):
        spike(d, "helmet", "head", f"reed {i + 1}", (x, -9.6, 1.4 - i * 0.7), rot, ln, 0.8, "R", tip="U", tip_len=1.8, tip_width=1.3,
              mirrored=False)
    for i, (x0, y1) in enumerate(((2.2, 1.8), (0.4, 0.4), (4.0, 0.8))):
        d.pair("helmet", "head", f"vine {i + 1}", (-x0 - 0.8, -3.0, 6.0, -x0, y1, 6.6), paint("N", sides=dangle(3 + i)))
    d.pair("helmet", "head", "vine, side", (-6.4, -4.0, -1.0, -5.6, 1.4, 0.0), paint("N", sides=dangle(8)))
    d.pair("helmet", "head", "glint", (1.6, -3.8, -5.6, 2.6, -2.9, -5.2), paint("A"), glow="AB")

    shingle_collar(d, "M")
    d.pair("chest", "right_arm", "mantle", (-4.8, -3.8, -3.7, 0.9, -0.6, 3.7), paint("M", bottom="N", sides=dangle(10, "M")),
           tweak=tweaks(top=rim_light, sides=lit_edge))
    d.pair("chest", "right_arm", "mantle, lower", (-5.1, -1.4, -3.5, 0.4, 0.9, 3.5), paint("P", bottom="D", sides=bottom_row("P", "D")),
           tweak=tweaks(sides=lit_edge))
    d.pair("chest", "right_arm", "arm wrap", (-4.7, 1.4, -3.4, 1.7, 2.6, 3.4), paint("C"), tweak=tweaks(sides=lit_edge))
    d.pair("chest", "right_arm", "arm wrap, lower", (-4.7, 3.0, -3.4, 1.7, 4.2, 3.4), paint("C"), tweak=tweaks(sides=lit_edge))
    d.pair("chest", "body", "panel", (2.0, 0.8, -3.2, 4.2, 7.2, -2.8), paint("P", bottom="D", front=bottom_row("P", "D")), tweak=tweaks(front=lit_edge))
    d.box("chest", "body", "lash, upper", (-4.4, 1.8, -3.3, 4.4, 2.8, -2.9), paint("R", front=bottom_row("R", "r")), tweak=tweaks(front=lit_edge))
    d.box("chest", "body", "lash, lower", (-4.4, 5.0, -3.3, 4.4, 6.0, -2.9), paint("R", front=bottom_row("R", "r")), tweak=tweaks(front=lit_edge))
    d.box("chest", "body", "knot", (-0.8, 3.0, -3.3, 0.8, 4.8, -2.9), paint("A"), glow="AB")
    d.box("chest", "body", "panel, back", (-4.3, 0.8, 2.7, 4.3, 8.0, 3.15), paint("P", bottom="D", sides=bottom_row("P", "D")))
    d.box("chest", "body", "lash, back", (-4.5, 3.4, 2.65, 4.5, 4.6, 3.25), paint("R", sides=bottom_row("R", "r")))
    d.ring("chest", "body", "belt", -5.2, 5.2, -3.3, 3.2, 6.6, 7.6, 0.6, paint("Y"), tweak=tweaks(sides=lit_edge))
    d.ring("chest", "body", "fringe", -5.4, 5.4, -3.5, 3.3, 8.0, 9.4, 0.9, paint("R", sides=dangle(12, "R")), tweak=tweaks(sides=lit_edge))
    d.box("chest", "body", "pouch", (-6.2, 5.8, -1.6, -5.2, 9.2, 1.6), paint("U", top="N"), tweak=tweaks(right=lit_edge))

    tasset = paint("P", sides=dangle(13, "P"), bottom="D")
    d.pair("legs", "right_leg", "tasset", (-2.8, 0.6, -3.2, 2.8, 3.4, -2.2), tasset, tweak=tweaks(sides=lit_edge))
    d.pair("legs", "right_leg", "tasset, side", (-3.2, 0.6, -2.2, -2.2, 3.4, 2.2), tasset)
    d.pair("legs", "right_leg", "knee pad", (-2.1, 3.6, -3.1, 2.1, 5.8, -2.2), paint("U", top="M", front=bottom_row("U", "Y")), tweak=tweaks(top=rim_light))
    d.pair("legs", "right_leg", "shin band", (-2.7, 6.4, -2.7, 2.7, 7.4, 2.7), paint("R"), tweak=tweaks(sides=lit_edge))
    d.ring("legs", "body", "rope", -4.8, 4.8, -2.8, 2.8, 7.1, 8.1, 0.6, paint("Y"))
    for leg in ("right_leg", "left_leg"):
        d.ring("boots", leg, "reed cuff", -3.5, 3.5, -3.5, 3.5, 5.8, 7.8, 0.8, paint("R", sides=dangle(14, "R")), tweak=tweaks(sides=lit_edge))
    d.pair("boots", "right_leg", "toe cap", (-2.5, 10.3, -3.6, 2.5, 12.8, -2.4), paint("U", front=rows(("U", 2), ("Y", 1))),
           tweak=tweaks(top=rim_light, front=lit_edge))
    d.pair("boots", "right_leg", "toe moss", (-1.6, 10.0, -3.4, 0.2, 10.3, -2.6), paint("M"))
    return d


def arachne_carapace():
    m = dict(gi._AC)
    m.update({"E": gi.CRIMSON.shifted(base=4, texture=gi.flat), "G": gi.CHITIN_GREEN.shifted(base=2, texture=gi.flat),
              "L": gi.CHITIN_GREEN.shifted(base=1, texture=gi.flat)})
    d = DetailSet("arachne_carapace", m, "a chitin helm with a ridge of plates, six glowing red eyes of three sizes across the brow, "
                                         "a pair of mandible fangs and a spider-leg crest; chitin plate strips with a glowing red "
                                         "hourglass, shoulders carrying three jointed spider legs each; spiked knee plates, "
                                         "clawed sabatons")
    plate = paint("P", bottom="D", sides=bottom_row("P", "D"))
    helm_bands(d, plate)
    d.box("helmet", "head", "ridge", (-0.7, -11.2, -3.6, 0.7, -10, 3.6), paint("P", top="C"), tweak=tweaks(top=lit_edge))
    d.box("helmet", "head", "brow", (-4.4, -6.2, -5.9, 4.4, -5.2, -5.3), paint("K", front=bottom_row("K", "D")), tweak=tweaks(front=lit_edge))
    d.pair("helmet", "head", "cheek guard", (-5.6, -4.9, -5.6, -3.8, -0.8, -1.0), paint("P", sides=bottom_row("P", "D")), tweak=tweaks(sides=lit_edge))
    d.pair("helmet", "head", "eye, large", (-1.9, -8.2, -5.95, -0.7, -7.0, -5.45), paint("E"), glow="EB")
    d.pair("helmet", "head", "eye, medium", (-3.6, -8.0, -5.95, -2.6, -7.0, -5.45), paint("E"), glow="EB")
    d.pair("helmet", "head", "eye, small", (-2.5, -9.0, -5.95, -1.7, -8.4, -5.45), paint("E"), glow="EB")
    d.pair("helmet", "head", "eye, side", (-5.95, -8.0, -3.6, -5.45, -7.2, -2.4), paint("E"), glow="EB")
    # mandibles: two curved fangs folding in under the brow
    d.pair("helmet", "head", "mandible", (-3.0, -4.0, -5.9, -2.1, -2.0, -5.3), paint("C", sides=bottom_row("C", "G")), tweak=tweaks(sides=lit_edge))
    d.pair("helmet", "head", "mandible, tip", (-2.6, -2.0, -5.9, -1.9, -0.8, -5.3), paint("G"))
    # spider-leg crest rising behind the ridge
    chain(d, "helmet", "head", "crest leg", (-1.2, -9.8, 1.8), [((-34, 0, -22), 3.4, 1.2), ((-70, 0, -92), 3.2, 1.0)], "C", glow=None)
    chain(d, "helmet", "head", "crest leg, low", (-2.8, -9.4, 3.2), [((-50, 0, -50), 3.0, 1.0), ((-30, 0, -120), 2.6, 0.9)], "C")

    plate_body(d, strip=paint("P", bottom="D", front=bottom_row("P", "D"), sides=bottom_row("P", "D")), collar="K",
               belt=paint("K"), buckle=paint("E"), pauldron=paint("P", bottom="D", sides=bottom_row("P", "D")),
               knee=paint("P", bottom="D", front=bottom_row("P", "D")), cuff=paint("P", bottom="D", sides=bottom_row("P", "D")),
               boot=paint("P", front=bottom_row("P", "D")), strips=(2, 4), glow="EB",
               tassets=paint("P", sides=bottom_row("P", "D"), bottom="D"))
    # the hourglass: two cones meeting in a waist, on the chest and bigger on the back
    d.box("chest", "body", "hourglass, top", (-1.4, 1.0, -3.3, 1.4, 2.0, -2.7), paint("E"), glow="EB")
    d.box("chest", "body", "hourglass, waist", (-0.6, 2.0, -3.3, 0.6, 3.0, -2.7), paint("E"), glow="EB")
    d.box("chest", "body", "hourglass, bottom", (-1.4, 3.0, -3.3, 1.4, 4.0, -2.7), paint("E"), glow="EB")
    d.box("chest", "body", "hourglass, back top", (-2.0, 1.3, 3.0, 2.0, 2.7, 3.28), paint("E"), glow="EB")
    d.box("chest", "body", "hourglass, back waist", (-0.9, 2.7, 3.0, 0.9, 4.1, 3.28), paint("E"), glow="EB")
    d.box("chest", "body", "hourglass, back bottom", (-2.0, 4.1, 3.0, 2.0, 5.7, 3.28), paint("E"), glow="EB")
    d.pair("chest", "body", "chest spike", (2.6, 2.0, -3.6, 3.2, 3.0, -3.3), paint("C"))
    # three spider legs on each pauldron: up and out, then bending down
    for i, (z, xr, zr, l1, l2) in enumerate(((-2.4, 20, -50, 2.8, 3.6), (0.0, 0, -58, 3.2, 4.0), (2.4, -20, -50, 2.8, 3.6))):
        chain(d, "chest", "right_arm", f"leg {i + 1}", (-3.8, -3.6, z), [((xr, 0, zr), l1, 1.2), ((xr * 0.6, 0, zr - 85), l2, 0.9),
                                                                                ((xr * 0.4, 0, zr - 150), 2.0, 0.8)], "P", tip="A", tip_len=0.8, glow="A")
    spike(d, "legs", "right_leg", "knee spike", (0.0, 4.6, -3.0), (74, 0, 0), 2.4, 1.0, "C", tip="E", tip_len=1.0, glow="E")
    spike(d, "boots", "right_leg", "claw", (-1.2, 11.2, -3.6), (82, 0, 0), 2.4, 0.9, "C")
    spike(d, "boots", "right_leg", "claw, inner", (0.2, 11.2, -3.6), (82, 0, 0), 2.4, 0.9, "C", mirrored=False)
    return d


def tide_regalia():
    m = dict(gi._TR)
    m.update({"S": gi.SHELL.shifted(texture=gi.plates), "s": gi.SHELL.shifted(base=1, texture=gi.flat),
              "R": gi.CORAL, "Q": gi.CORAL.shifted(base=4, texture=gi.flat), "q": gi.CORAL.shifted(base=1, texture=gi.flat),
              "H": gi.GOLD.shifted(base=4, texture=gi.flat)})
    d = DetailSet("tide_regalia", m, "a gold circlet under a crown of branching coral with pearl tips and a great pearl at the brow, "
                                     "prismarine fins sweeping back; prismarine scale plates with gold edges and a pearl heart, "
                                     "stacked scallop-shell pauldrons crowned with coral, a gold gorget; gold-edged knee plates "
                                     "with coral; gold boot cuffs and pearl-tipped fins")
    gold = paint("A", sides=bottom_row("A", "H"))
    plate = paint("P", bottom="D", sides=bottom_row("P", "A"))
    d.box("helmet", "head", "crown", (-4, -10, -4, 4, -9, 4), paint("P"), tweak=tweaks(top=rim_light))
    d.ring("helmet", "head", "circlet", -5.6, 5.6, -5.6, 5.6, -7.0, -5.6, 1.0, gold, tweak=tweaks(sides=lit_edge))
    d.ring("helmet", "head", "plate band", -5.2, 5.2, -5.2, 5.2, -8.8, -7.2, 0.8, plate, tweak=tweaks(sides=lit_edge))
    d.box("helmet", "head", "pearl, brow", (-1.1, -8.0, -6.3, 1.1, -5.9, -5.5), paint("B"), tweak=tweaks(front=glint), glow="B")
    d.pair("helmet", "head", "pearl, side", (-3.6, -6.9, -5.9, -2.6, -5.9, -5.4), paint("B"), glow="B")
    d.pair("helmet", "head", "cheek guard", (-5.6, -5.4, -5.7, -3.8, -0.8, -1.0), paint("P", sides=bottom_row("P", "A")), tweak=tweaks(sides=lit_edge))
    d.pair("helmet", "head", "fin", (-6.2, -8.4, 0.0, -5.4, -3.0, 6.6), paint("P", sides=bottom_row("P", "A")), tweak=tweaks(sides=lit_edge))
    d.pair("helmet", "head", "fin, low", (-6.2, -3.0, 2.0, -5.4, -0.6, 6.0), paint("D"))
    # the coral crown: a tall branching centre and a fan of branches each side, pearls at every tip
    d.box("helmet", "head", "coral, centre", (-0.9, -14.0, -0.9, 0.9, -10, 0.9), paint("R", sides=bottom_row("R", "q")), tweak=tweaks(sides=lit_edge))
    d.box("helmet", "head", "pearl, centre", (-0.8, -15.6, -0.8, 0.8, -14.0, 0.8), paint("B"), glow="B")
    for i, (z, rot, ln) in enumerate(((-2.2, (-14, 0, -24), 4.2), (0.0, (0, 0, -40), 4.6), (2.2, (14, 0, -24), 4.0))):
        chain(d, "helmet", "head", f"coral {i + 1}", (-2.0, -9.6, z), [(rot, ln, 1.2)], "R", tip="B", tip_len=1.2, glow="B", tip_width=1.4)
    chain(d, "helmet", "head", "coral, branch", along((-2.0, -9.6, 0.0), (0, 0, -40), 2.6), [((0, 0, -78), 2.8, 1.0)], "R", tip="B", glow="B")
    chain(d, "helmet", "head", "coral, low", (-3.8, -9.4, -0.6), [((0, 0, -76), 3.4, 1.1)], "R", tip="B", glow="B", tip_width=1.3)

    plate_body(d, strip=paint("P", bottom="D", front=bottom_row("P", "A"), sides=bottom_row("P", "A")), collar="A",
               belt=paint("A", sides=bottom_row("A", "H")), buckle=paint("B"),
               pauldron=paint("S", bottom="s", sides=bottom_row("S", "A")),
               knee=paint("P", bottom="D", front=bottom_row("P", "A")), cuff=paint("A", sides=bottom_row("A", "H")),
               boot=None, strips=(1, 3), glow="B", tassets=paint("P", sides=bottom_row("P", "A"), bottom="A"))
    d.box("chest", "body", "pearl heart", (-1.3, 2.4, -3.3, 1.3, 4.7, -2.6), paint("B"), tweak=tweaks(front=glint), glow="B")
    d.pair("chest", "body", "shell", (2.4, 1.0, -3.55, 4.2, 2.8, -3.3), paint("S", bottom="s", front=bottom_row("S", "A")), tweak=tweaks(front=lit_edge))
    d.pair("chest", "body", "shell, low", (2.4, 5.0, -3.55, 4.2, 6.4, -3.3), paint("S", front=bottom_row("S", "A")), tweak=tweaks(front=lit_edge))
    d.pair("chest", "right_arm", "shell, top", (-4.4, -4.5, -2.4, -0.4, -3.9, 2.4), paint("S", bottom="s", sides=bottom_row("S", "A")), tweak=tweaks(top=rim_light))
    chain(d, "chest", "right_arm", "coral", (-3.6, -4.4, 0.0), [((0, 0, -26), 3.4, 1.3)], "R", tip="B", tip_len=1.0, glow="B")
    chain(d, "chest", "right_arm", "coral, front", (-3.4, -4.4, -2.0), [((16, 0, -44), 2.8, 1.1)], "R", tip="B", tip_len=1.0, glow="B")
    chain(d, "chest", "right_arm", "coral, back", (-3.4, -4.4, 2.0), [((-16, 0, -44), 2.8, 1.1)], "R", tip="B", tip_len=1.0, glow="B")
    d.pair("legs", "right_leg", "knee coral", (-0.7, 4.2, -3.5, 0.7, 5.4, -3.3), paint("R"))
    d.pair("boots", "right_leg", "boot", (-2.6, 9.0, -3.4, 2.6, 11.2, -2.5), paint("P", front=bottom_row("P", "A")), tweak=tweaks(top=rim_light))
    d.pair("boots", "right_leg", "boot, toe", (-2.45, 11.0, -4.2, 2.45, 12.8, -2.6), paint("P", front=bottom_row("P", "A")), tweak=tweaks(top=rim_light))
    spike(d, "boots", "right_leg", "boot fin", (-3.6, 9.4, 0.0), (0, 0, 60), 3.2, 0.8, "P", tip="B", tip_len=0.8, glow="B")
    return d


def wardens_shroud():
    m = dict(gi._SHROUD_MATS)
    m.update({"O": gi.FANG_BONE.shifted(texture=gi.flat), "Q": gi.FANG_BONE.shifted(base=1, texture=gi.flat),
              "V": gi.DREAD_BONE.shifted(base=2, texture=gi.flat), "W": gi.ELDER.shifted(base=4, texture=gi.flat)})

    def tatter(salt, main="T"):
        def pick(u, v, w, h):
            if v >= h - 1 and hashed(u, v, w, salt) % 2 == 0:
                return "."
            return main
        return pick

    def wisp(salt):
        def pick(u, v, w, h):
            if v >= h - 1 and hashed(u, v, w, salt) % 2 == 0:
                return "."
            return "P" if v < h - 1 else "W"
        return pick

    d = DetailSet("wardens_shroud", m, "a tall tattered hood with a drape, a thrust-forward rim, a bone brow with glowing purple soul-lights "
                                       "and wisps trailing from the hem; burial wrappings over a bone rib cage and spine with a glowing "
                                       "soul gem, bone-spiked shoulders under shroud mantles, a bone-clasped belt; bone knee guards "
                                       "and ragged wrapped tassets; wrapped boot cuffs with bone toes")
    drape = paint("T", bottom="D", sides=bottom_row("T", "D"))
    d.box("helmet", "head", "crown", (-4, -10, -4, 4, -9, 4), paint("T"), tweak=tweaks(top=rim_light))
    tip = Pivot("peak", (0.3, -10, 0.8), (-26, 0, 8))
    d.box("helmet", "head", "peak", (-1.3, -4.4, -1.3, 1.3, 0, 1.3), paint("T", top="D"), pivot=tip, tweak=tweaks(sides=lit_edge))
    d.box("helmet", "head", "hood, back", (-5.4, -8.4, 3.8, 5.4, -1.0, 6.2), paint("T", bottom="D", sides=tatter(1)), tweak=tweaks(sides=lit_edge))
    d.pair("helmet", "head", "hood, side", (-5.6, -8.4, -4.0, -4.8, -1.4, 3.8), paint("T", sides=tatter(2)), tweak=tweaks(sides=lit_edge))
    d.box("helmet", "head", "rim, brow", (-3.4, -7.2, -6.8, 3.4, -5.2, -4.8), paint("T", front=bottom_row("T", "D")), tweak=tweaks(sides=lit_edge))
    d.pair("helmet", "head", "rim, cheek", (-5.9, -7.2, -6.6, -3.4, -0.2, -4.6), paint("T", sides=tatter(3)), tweak=tweaks(sides=lit_edge))
    d.box("helmet", "head", "bone brow", (-3.0, -5.2, -6.4, 3.0, -4.4, -5.6), paint("O", front=bottom_row("O", "Q")), tweak=tweaks(front=lit_edge))
    d.pair("helmet", "head", "soul-light", (-3.0, -4.2, -5.9, -1.0, -2.6, -5.3), paint("P", front=["PW", "PP"]), tweak=tweaks(front=glint), glow="PW")
    d.pair("helmet", "head", "horn", (-5.3, -9.2, -1.5, -4.3, -6.0, -0.5), paint("O", sides=bottom_row("O", "Q")), tweak=tweaks(sides=lit_edge))
    spike(d, "helmet", "head", "horn, tip", (-4.6, -9.0, -1.0), (0, 0, -42), 3.2, 1.0, "O", tip="Q", tip_len=1.0)
    for i, (x0, y1) in enumerate(((2.2, 1.8), (0.4, 0.6), (4.0, 1.0))):
        d.pair("helmet", "head", f"wisp {i + 1}", (-x0 - 0.8, -1.0, 6.2, -x0, y1 + 1.0, 6.8), paint("P", sides=wisp(4 + i)), glow="PW")
    d.pair("helmet", "head", "wisp, side", (-6.5, -2.0, -1.0, -5.9, 2.0, 0.0), paint("P", sides=wisp(9)), glow="PW")

    # chest: burial wrappings, a bone rib cage, spine and a soul gem
    d.pair("chest", "body", "collar", (-4.4, -1.6, -3.55, -2.2, 0.4, -2.6), paint("D"), tweak=tweaks(sides=lit_edge, top=rim_light))
    d.pair("chest", "body", "collar, V", (-2.2, -0.4, -3.25, -0.8, 1.6, -2.6), paint("D"), tweak=tweaks(sides=lit_edge))
    d.pair("chest", "body", "collar, shoulder", (-4.4, -1.6, -2.6, -2.2, -0.6, 2.6), paint("D"), tweak=tweaks(top=lit_edge))
    d.box("chest", "body", "collar, back", (-4.4, -1.6, 2.6, 4.4, 0.2, 3.25), paint("D"), tweak=tweaks(sides=lit_edge))
    d.pair("chest", "body", "wrap", (-5.3, 0.8, -3.6, -3.4, 8.0, -2.85), paint("T", sides=tatter(5), bottom="D"), tweak=tweaks(front=lit_edge))
    for i, y in enumerate((1.2, 2.8, 4.4, 5.8)):
        w = (3.2, 3.6, 3.4, 2.6)[i]
        d.pair("chest", "body", f"rib {i + 1}", (-w, y, -3.28, -0.5, y + 0.8, -2.8), paint("O", front=bottom_row("O", "Q")), tweak=tweaks(front=lit_edge))
    d.box("chest", "body", "breastbone", (-0.5, 0.8, -3.25, 0.5, 6.6, -2.7), paint("Q"), tweak=tweaks(front=lit_edge))
    d.box("chest", "body", "soul gem", (-1.1, 2.4, -3.3, 1.1, 4.0, -2.6), paint("P"), tweak=tweaks(front=glint), glow="P")
    d.box("chest", "body", "spine", (-0.7, 0.6, 2.7, 0.7, 8.0, 3.25), paint("O", back=bottom_row("O", "Q")), tweak=tweaks(back=lit_edge))
    for i, y in enumerate((1.6, 3.2, 4.8)):
        d.pair("chest", "body", f"rib, back {i + 1}", (-3.4, y, 2.7, -0.7, y + 0.8, 3.2), paint("O"))
    d.pair("chest", "right_arm", "mantle", (-4.8, -3.8, -3.7, 0.9, -0.6, 3.7), paint("T", bottom="D", sides=tatter(6)),
           tweak=tweaks(top=rim_light, sides=lit_edge))
    d.pair("chest", "right_arm", "mantle, lower", (-5.1, -1.4, -3.5, 0.4, 0.9, 3.5), paint("T", sides=tatter(7)), tweak=tweaks(sides=lit_edge))
    d.pair("chest", "right_arm", "bone plate", (-4.2, -4.8, -2.2, -1.0, -3.9, 2.2), paint("O", sides=bottom_row("O", "Q")), tweak=tweaks(top=rim_light))
    chain(d, "chest", "right_arm", "shoulder bone", (-3.4, -4.6, -1.0), [((0, 0, -30), 3.2, 1.2)], "O", tip="Q", tip_len=1.0)
    chain(d, "chest", "right_arm", "shoulder bone, back", (-3.4, -4.6, 1.2), [((-20, 0, -48), 2.8, 1.0)], "O", tip="Q", tip_len=1.0)
    d.ring("chest", "body", "belt", -5.2, 5.2, -3.3, 3.2, 6.6, 7.6, 0.6, paint("D"), tweak=tweaks(sides=lit_edge))
    d.box("chest", "body", "clasp", (-1.2, 6.0, -3.7, 1.2, 8.2, -3.2), paint("O", front=["PP", "QQ"]), tweak=tweaks(front=lit_edge))
    d.pair("chest", "body", "hang", (2.6, 7.7, -3.7, 4.2, 9.3, -3.25), paint("T", sides=tatter(8), bottom="D"))
    d.ring("chest", "body", "hem", -5.4, 5.4, -3.5, 3.3, 8.0, 9.4, 0.9, paint("T", sides=tatter(9)), tweak=tweaks(sides=lit_edge))

    tasset = paint("T", sides=tatter(10), bottom="D")
    d.pair("legs", "right_leg", "tasset", (-2.8, 0.6, -3.2, 2.8, 3.4, -2.2), tasset, tweak=tweaks(sides=lit_edge))
    d.pair("legs", "right_leg", "tasset, side", (-3.2, 0.6, -2.2, -2.2, 3.4, 2.2), tasset)
    d.pair("legs", "right_leg", "knee guard", (-1.9, 3.6, -3.2, 1.9, 5.8, -2.2), paint("O", front=bottom_row("O", "Q")), tweak=tweaks(top=rim_light, front=lit_edge))
    d.pair("legs", "right_leg", "knee soul", (-0.5, 4.2, -3.5, 0.5, 5.2, -3.25), paint("P"), glow="P")
    d.pair("legs", "right_leg", "wrap", (-2.7, 6.2, -2.7, 2.7, 7.4, 2.7), paint("T", sides=tatter(11)), tweak=tweaks(sides=lit_edge))
    d.ring("legs", "body", "belt", -4.8, 4.8, -2.8, 2.8, 7.1, 8.1, 0.6, paint("D"))
    for leg in ("right_leg", "left_leg"):
        d.ring("boots", leg, "cuff", -3.5, 3.5, -3.5, 3.5, 5.8, 7.8, 0.8, paint("T", sides=tatter(12), bottom="D"), tweak=tweaks(sides=lit_edge))
    d.pair("boots", "right_leg", "toe bone", (-2.3, 10.4, -3.9, 2.3, 12.8, -2.4), paint("O", front=rows(("O", 2), ("Q", 1))), tweak=tweaks(top=rim_light, front=lit_edge))
    d.pair("boots", "right_leg", "toe soul", (-0.4, 11.4, -4.2, 0.4, 12.0, -3.95), paint("P"), glow="P")
    return d


def runeforged():
    m = dict(gi._RF)
    m.update({"H": gi.BRASS.shifted(base=4, texture=gi.flat), "h": gi.BRASS.shifted(base=1, texture=gi.flat),
              "R": gi.CYAN_GLOW.shifted(base=4, texture=gi.flat), "I": gi.DARK_IRON.shifted(base=2, texture=gi.flat)})
    d = DetailSet("runeforged", m, "a horned dwarven helm of deepslate plate with brass-capped curling horns, a brass crest with a glowing "
                                   "rune line, a glowing brow rune and a braided iron beard guard held by brass clasps; banded "
                                   "deepslate plates with a glowing rune diamond on the chest, brass-edged pauldrons with rune stones "
                                   "and short horns, a brass belt; rune-marked knee plates; brass boot cuffs and sabatons")
    plate = paint("P", bottom="D", sides=bottom_row("P", "D"))
    helm_bands(d, plate)
    d.ring("helmet", "head", "brass brim", -5.7, 5.7, -5.7, 5.7, -4.9, -4.0, 1.0, paint("A", sides=bottom_row("A", "H")), tweak=tweaks(sides=lit_edge))
    d.box("helmet", "head", "crest", (-0.8, -11.0, -4.4, 0.8, -10, 4.4), paint("A", top="R"), tweak=tweaks(sides=lit_edge), glow="R")
    d.box("helmet", "head", "brow rune", (-3.2, -7.9, -5.8, 3.2, -7.1, -5.4), paint("R"), tweak=tweaks(front=glint), glow="R")
    d.pair("helmet", "head", "cheek guard", (-5.6, -3.9, -5.7, -3.8, -0.8, -1.0), paint("P", sides=bottom_row("P", "A")), tweak=tweaks(sides=lit_edge))
    chain(d, "helmet", "head", "horn", (-5.0, -7.4, -0.4), [((0, 0, -78), 3.0, 1.8), ((0, 0, -32), 3.4, 1.3)], "A", tip="H", tip_len=1.2, tip_width=0.9)
    # braided iron beard guard: three braids hanging in front of the chest, held by two brass clasps
    d.box("helmet", "head", "beard, centre", (-1.0, 0.6, -5.5, 1.0, 4.6, -4.3), paint("C", front=bottom_row("C", "I"), sides=bottom_row("C", "I")),
          tweak=tweaks(sides=lit_edge))
    d.pair("helmet", "head", "beard, side", (-3.0, 0.6, -5.2, -1.2, 3.6, -4.2), paint("C", front=bottom_row("C", "I"), sides=bottom_row("C", "I")),
           tweak=tweaks(sides=lit_edge))
    d.box("helmet", "head", "beard clasp", (-3.2, 1.6, -5.7, 3.2, 2.5, -3.9), paint("A", front=bottom_row("A", "H")), tweak=tweaks(front=lit_edge))
    d.pair("helmet", "head", "beard guard", (-5.4, -1.0, -5.4, -3.2, 0.6, -4.7), paint("C", sides=bottom_row("C", "I")), tweak=tweaks(sides=lit_edge))
    d.box("helmet", "head", "moustache", (-3.8, -1.6, -5.6, 3.8, -0.6, -5.0), paint("C", front=bottom_row("C", "I")), tweak=tweaks(front=lit_edge))

    plate_body(d, strip=paint("P", bottom="D", front=bottom_row("P", "D"), sides=bottom_row("P", "D")), collar="A",
               belt=paint("A", sides=bottom_row("A", "H")), buckle=paint("H"), pauldron=paint("P", bottom="D", sides=bottom_row("P", "A")),
               knee=paint("P", bottom="D", front=bottom_row("P", "D")), cuff=paint("A", sides=bottom_row("A", "H")),
               boot=paint("P", front=bottom_row("P", "D")), strips=(2, 4), glow="R",
               tassets=paint("P", sides=bottom_row("P", "D"), bottom="A"))
    # the rune diamond on the chest, a smaller rune each side and a rune line down the back
    d.box("chest", "body", "rune, top", (-0.5, 1.0, -3.3, 0.5, 2.0, -2.7), paint("R"), glow="R")
    d.box("chest", "body", "rune, middle", (-1.5, 2.0, -3.3, 1.5, 3.0, -2.7), paint("R"), glow="R")
    d.box("chest", "body", "rune, bottom", (-0.5, 3.0, -3.3, 0.5, 4.0, -2.7), paint("R"), glow="R")
    d.pair("chest", "body", "rune, side", (2.6, 1.4, -3.55, 3.6, 2.4, -3.3), paint("R"), glow="R")
    d.pair("chest", "body", "rune, low", (3.0, 5.0, -3.55, 4.0, 6.0, -3.3), paint("R"), glow="R")
    d.box("chest", "body", "rune, back", (-0.6, 1.4, 3.0, 0.6, 5.6, 3.28), paint("R"), glow="R")
    d.pair("chest", "body", "rune, back side", (1.4, 2.2, 3.0, 2.6, 3.4, 3.28), paint("R"), glow="R")
    d.pair("chest", "right_arm", "rune stone", (-3.4, -4.6, -0.7, -2.2, -3.9, 0.7), paint("R"), glow="R")
    spike(d, "chest", "right_arm", "pauldron horn", (-3.6, -3.9, -1.8), (0, 0, -34), 2.6, 1.3, "A", tip="H", tip_len=0.8)
    spike(d, "chest", "right_arm", "pauldron horn, back", (-3.6, -3.9, 1.8), (0, 0, -34), 2.6, 1.3, "A", tip="H", tip_len=0.8)
    d.pair("legs", "right_leg", "knee rune", (-0.5, 4.0, -3.5, 0.5, 5.4, -3.3), paint("R"), glow="R")
    d.pair("boots", "right_leg", "toe rune", (-0.5, 11.6, -4.3, 0.5, 12.2, -4.0), paint("R"), glow="R")
    return d


def hellforged():
    m = dict(gi._HF)
    m.update({"M": gi.EMBERITE.shifted(base=4, texture=gi.flat), "E": gi.EMBER.shifted(texture=gi.flat),
              "I": gi._HELL_IRON.shifted(base=2, texture=gi.flat), "i": gi._HELL_IRON.shifted(base=1, texture=gi.flat),
              "H": gi._HELL_IRON.shifted(base=3, texture=gi.flat)})
    d = DetailSet("hellforged", m, "a black iron great helm crowned with a forge-red band of spikes between two great curling "
                                   "horns of black iron, broken molten seams glowing under its plate bands, a heavy brow lit "
                                   "from below by the visor slit, cheek guards and a vented chin plate; magma-cracked plates "
                                   "round the Tyrant's glowing core, spiked pauldrons with molten-tipped spikes, a forge-red "
                                   "belt with a glowing buckle; spiked knee plates and seamed tassets; seamed boot cuffs and "
                                   "spiked sabatons")

    def seam(salt, main="P"):
        """Black iron whose bottom row is a broken molten seam (about one pixel in three glows)."""
        def pick(u, v, w, h):
            if v == h - 1:
                return "E" if hashed(u, w, salt) % 3 == 0 else "D"
            return main
        return pick

    def cracked(*cells):
        """A plate's front: black iron with bright molten cracks at these (u, v) cells, the bottom row dark."""
        def pick(u, v, w, h):
            if (u, v) in cells:
                return "B"
            return "D" if v == h - 1 else "P"
        return pick

    # helm: plate bands over molten seams, a forge-red crown of black iron spikes, two great horns
    helm_bands(d, paint("P", bottom="D", sides=seam(1)), glow="E")
    d.ring("helmet", "head", "crown band", -4.7, 4.7, -4.7, 4.7, -10.7, -9.5, 0.6, paint("A", sides=bottom_row("A", "D")),
           tweak=tweaks(sides=lit_edge))
    spike(d, "helmet", "head", "crown spike, front", (0.0, -10.4, -4.2), (-12, 0, 0), 3.0, 1.2, "I", tip="B", tip_len=1.0, glow="B",
          mirrored=False)
    spike(d, "helmet", "head", "crown spike", (-2.6, -10.4, -4.0), (-10, 0, -10), 2.2, 1.0, "I", tip="A", tip_len=0.8)
    spike(d, "helmet", "head", "crown spike, side", (-4.2, -10.4, -1.2), (0, 0, -14), 2.4, 1.0, "I", tip="A", tip_len=0.8)
    spike(d, "helmet", "head", "crown spike, back", (-2.4, -10.4, 4.2), (12, 0, -10), 2.0, 1.0, "I", tip="A", tip_len=0.8)
    chain(d, "helmet", "head", "horn", (-5.0, -7.6, -0.2), [((0, 0, -84), 2.8, 2.2), ((0, 0, -52), 2.6, 1.8), ((-6, 0, -16), 2.8, 1.4),
                                                          ((-10, 0, 12), 2.2, 1.0)], "I", tip="H", tip_len=1.2, tip_width=0.7)
    d.box("helmet", "head", "brow", (-4.3, -5.9, -6.0, 4.3, -4.4, -5.3), paint("i", front=bottom_row("i", "B")),
          tweak=tweaks(front=lit_edge), glow="B")
    d.pair("helmet", "head", "cheek guard", (-5.6, -3.4, -5.7, -3.8, -0.4, -1.0), paint("P", sides=seam(2)),
           tweak=tweaks(sides=lit_edge), glow="E")
    d.box("helmet", "head", "chin plate", (-2.0, -1.8, -5.8, 2.0, -0.2, -5.25), paint("P", front=["PEEP", "DDDD"]),
          tweak=tweaks(front=lit_edge), glow="E")

    # chest: magma-cracked stepped plates round the Tyrant's core, spiked pauldrons, a forge-red belt
    plate_body(d, strip=paint("P"), collar="A", belt=paint("A", sides=bottom_row("A", "i")), buckle=paint("M"),
               pauldron=paint("P", bottom="D", sides=seam(3)), knee=paint("P", bottom="D", front=seam(4)),
               cuff=paint("P", bottom="D", sides=seam(5)), boot=paint("P", front=seam(6)), strips=(), glow="EM",
               tassets=paint("P", sides=seam(7), bottom="A"))
    for i, cells in enumerate((((6, 0), (5, 1)), ((5, 0), (4, 1), (1, 0), (2, 1)), ((5, 0), (6, 1), (2, 0), (2, 1)),
                               ((6, 0), (7, 1), (1, 0), (2, 1)))):
        d.box("chest", "body", f"cracked plate {i + 1}", STRIPS[i + 1],
              paint("P", bottom="D", front=cracked(*cells), sides=bottom_row("P", "D")), tweak=tweaks(front=lit_edge), glow="B")
    d.box("chest", "body", "core", (-1.1, 2.8, -3.3, 1.1, 4.4, -2.6), paint("M"), tweak=tweaks(front=["4.", ".."]), glow="M")
    for side in ("", ", back"):
        spike(d, "chest", "right_arm", f"pauldron spike{side}", (-3.4, -3.9, 1.8 if side else -1.8), (8 if side else -8, 0, -26),
              3.2, 1.4, "I", tip="B", tip_len=1.0, glow="B")
    spike(d, "chest", "right_arm", "pauldron spike, outer", (-4.9, -2.2, 0.0), (0, 0, -78), 2.6, 1.2, "I", tip="A", tip_len=0.9)

    # legs and boots: spiked knees, spiked toes
    spike(d, "legs", "right_leg", "knee spike", (0.0, 4.4, -3.0), (72, 0, 0), 2.2, 1.0, "I", tip="A", tip_len=0.9)
    spike(d, "boots", "right_leg", "toe spike", (0.0, 11.4, -3.9), (82, 0, 0), 1.8, 1.0, "I", tip="B", tip_len=0.8, glow="B")
    return d


def gravewarden():
    m = dict(gi._GW)
    m.update({"a": gi._GRAVESTONE.shifted(base=1, texture=gi.flat), "S": gi._GRAVESTONE.shifted(base=3, texture=gi.flat),
              "H": gi._GW_STEEL.shifted(base=3, texture=gi.flat), "E": gi.PLAGUE.shifted(base=2, texture=gi.flat)})
    d = DetailSet("gravewarden", m, "a rotting plate helm under a small gravestone crest with a glowing green cross, a plague-green "
                                    "visor slit and burial cloth hanging at the back; a cracked gravestone cuirass round a glowing "
                                    "cross, gravestone pauldrons with rotted iron spikes, a cloth-hung belt and a slab on the back; "
                                    "gravestone knee plates and ragged tassets; rotted sabatons")

    def rot(salt):
        def pick(u, v, w, h):
            if v == h - 1:
                return "." if hashed(u, v, w, salt) % 3 == 0 else "D"
            return "C"
        return pick

    def cross(u, v, w, h):
        return "B" if u in (w // 2 - 1, w // 2) or v == h // 2 else "A"

    helm_bands(d, paint("P", bottom="D", sides=bottom_row("P", "D")), glow="B")
    d.box("helmet", "head", "gravestone crest", (-2.2, -13.0, -0.8, 2.2, -10.0, 0.8), paint("A", front=cross, sides=bottom_row("A", "a")),
          tweak=tweaks(front=lit_edge, top=rim_light), glow="B")
    d.box("helmet", "head", "visor slit", (-3.8, -4.7, -5.75, 3.8, -3.9, -5.05), paint("B"), glow="B")
    d.box("helmet", "head", "brow", (-4.3, -5.9, -6.0, 4.3, -4.7, -5.3), paint("D", front=bottom_row("P", "D")), tweak=tweaks(front=lit_edge))
    d.pair("helmet", "head", "cheek guard", (-5.6, -3.4, -5.7, -3.8, -0.4, -1.0), paint("P", sides=bottom_row("P", "D")), tweak=tweaks(sides=lit_edge))
    d.box("helmet", "head", "burial cloth", (-4.6, -8.7, 4.8, 4.6, -0.6, 5.8), paint("C", sides=rot(1)), tweak=tweaks(sides=lit_edge))
    spike(d, "helmet", "head", "rotted spike", (-3.4, -10.4, 0.0), (0, 0, -22), 3.0, 1.2, "H", tip="B", tip_len=0.9, glow="B")

    plate_body(d, strip=paint("P", bottom="D", front=bottom_row("P", "D"), sides=bottom_row("P", "D")), collar="A", belt=paint("C", sides=rot(2)),
               buckle=paint("a"), pauldron=paint("A", bottom="a", sides=bottom_row("A", "a")),
               knee=paint("A", bottom="a", front=bottom_row("A", "a")), cuff=paint("C", sides=rot(3)),
               boot=paint("P", front=bottom_row("P", "D")), strips=(2, 4), glow="B",
               tassets=paint("C", sides=rot(4), bottom="D"), back_strips=False)
    d.box("chest", "body", "cross, top", (-0.5, 1.0, -3.3, 0.5, 2.0, -2.7), paint("B"), glow="B")
    d.box("chest", "body", "cross, bar", (-1.5, 2.0, -3.3, 1.5, 3.0, -2.7), paint("B"), glow="B")
    d.box("chest", "body", "cross, foot", (-0.5, 3.0, -3.3, 0.5, 5.0, -2.7), paint("B"), glow="B")
    d.box("chest", "body", "gravestone, back", (-2.4, 1.2, 2.8, 2.4, 6.2, 3.25), paint("A", back=bottom_row("A", "a")), tweak=tweaks(back=lit_edge))
    d.box("chest", "body", "crack, back", (-0.3, 1.6, 3.15, 0.3, 4.6, 3.3), paint("B"), glow="B")
    spike(d, "chest", "right_arm", "pauldron spike", (-3.6, -3.9, -1.8), (-6, 0, -30), 3.0, 1.3, "H", tip="B", tip_len=0.9, glow="B")
    spike(d, "chest", "right_arm", "pauldron spike, back", (-3.6, -3.9, 1.8), (6, 0, -30), 3.0, 1.3, "H", tip="B", tip_len=0.9, glow="B")
    d.pair("chest", "right_arm", "pauldron stone", (-4.2, -4.9, -2.0, -1.4, -3.9, 2.0), paint("A", front=cross, sides=bottom_row("A", "a")),
           tweak=tweaks(top=rim_light))
    d.pair("legs", "right_leg", "knee glow", (-0.5, 4.2, -3.5, 0.5, 5.2, -3.3), paint("B"), glow="B")
    spike(d, "boots", "right_leg", "toe spike", (0.0, 11.4, -3.9), (82, 0, 0), 1.8, 1.0, "H", tip="B", tip_len=0.8, glow="B")
    return d


def silkstalker():
    m = dict(gi._SS)
    m.update({"S": gi._SILK.shifted(base=3, texture=gi.flat), "L": gi._CHITIN_DK.shifted(base=3, texture=gi.flat),
              "E": gi.VENOM.shifted(base=4, texture=gi.flat), "H": gi._CHITIN_DK.shifted(base=2, texture=gi.flat)})
    d = DetailSet("silkstalker", m, "a chitin hood ringed with venom-green eyes under four arching spider-leg spikes, silk "
                                    "wrapped at the chin and nape; chitin plates bound in silk round a glowing venom hourglass, "
                                    "spider-leg spikes arching off the shoulders, a silk belt; silk-wrapped tassets and spiked "
                                    "knees; chitin boots with spider-leg heel spikes")

    def strands(salt):
        def pick(u, v, w, h):
            if v == h - 1 and hashed(u, v, w, salt) % 3 == 0:
                return "."
            return "A" if hashed(u, v, salt) % 4 else "C"
        return pick

    helm_bands(d, paint("P", bottom="D", sides=bottom_row("P", "D")), glow="B")
    # venom eyes: two big ones, two small ones above, two low on the cheeks
    d.pair("helmet", "head", "eye, big", (-3.6, -6.8, -5.75, -2.0, -5.4, -5.05), paint("B"), glow="B")
    d.pair("helmet", "head", "eye, small", (-1.7, -7.4, -5.7, -0.8, -6.6, -5.05), paint("B"), glow="B")
    d.pair("helmet", "head", "eye, cheek", (-4.4, -4.6, -5.7, -3.4, -3.8, -5.05), paint("B"), glow="B")
    d.box("helmet", "head", "silk, chin", (-3.0, -2.0, -5.8, 3.0, -0.2, -5.1), paint("A", front=strands(1)), tweak=tweaks(front=lit_edge))
    d.box("helmet", "head", "silk, nape", (-4.6, -8.6, 4.8, 4.6, -0.6, 5.8), paint("A", sides=strands(2)), tweak=tweaks(sides=lit_edge))
    chain(d, "helmet", "head", "leg", (-2.6, -10.4, -1.6), [((0, 0, -35), 3.0, 1.1), ((0, 0, -95), 3.0, 0.9), ((0, 0, -150), 2.8, 0.7)], "P",
          tip="B", tip_len=1.0, glow="B")
    chain(d, "helmet", "head", "leg, back", (-2.6, -10.4, 1.8), [((0, 0, -30), 2.6, 1.0), ((0, 0, -90), 2.6, 0.8), ((0, 0, -145), 2.4, 0.6)], "H",
          tip="A", tip_len=0.9)

    plate_body(d, strip=paint("P", bottom="D", front=bottom_row("P", "D"), sides=bottom_row("P", "D")), collar="A", belt=paint("A", sides=bottom_row("A", "C")),
               buckle=paint("B"), pauldron=paint("P", bottom="D", sides=bottom_row("P", "H")),
               knee=paint("P", bottom="D", front=bottom_row("P", "D")), cuff=paint("A", sides=strands(3)),
               boot=paint("P", front=bottom_row("P", "D")), strips=(1, 3), glow="B",
               tassets=paint("A", sides=strands(4), bottom="C"))
    d.box("chest", "body", "hourglass, top", (-1.5, 2.2, -3.3, 1.5, 3.2, -2.7), paint("B"), glow="B")
    d.box("chest", "body", "hourglass, waist", (-0.5, 3.2, -3.3, 0.5, 4.2, -2.7), paint("B"), glow="B")
    d.box("chest", "body", "hourglass, bottom", (-1.5, 4.2, -3.3, 1.5, 5.2, -2.7), paint("B"), glow="B")
    d.box("chest", "body", "hourglass, back", (-1.1, 2.0, 3.0, 1.1, 5.4, 3.28), paint("B"), glow="B")
    chain(d, "chest", "right_arm", "shoulder leg", (-3.4, -3.9, -1.6), [((0, 0, -38), 3.2, 1.2), ((-6, 0, -100), 3.0, 0.9), ((-10, 0, -150), 2.6, 0.7)], "P",
          tip="B", tip_len=1.0, glow="B")
    chain(d, "chest", "right_arm", "shoulder leg, back", (-3.4, -3.9, 1.6), [((0, 0, -30), 2.8, 1.0), ((6, 0, -95), 2.6, 0.8), ((10, 0, -145), 2.2, 0.6)], "H",
          tip="A", tip_len=0.9)
    spike(d, "legs", "right_leg", "knee spike", (0.0, 4.4, -3.0), (72, 0, 0), 2.2, 1.0, "P", tip="B", tip_len=0.9, glow="B")
    spike(d, "boots", "right_leg", "heel spike", (0.0, 11.0, 2.4), (-72, 0, 0), 2.4, 1.0, "H", tip="A", tip_len=0.8)
    spike(d, "boots", "right_leg", "heel spike, outer", (-1.6, 11.0, 2.4), (-60, 0, -20), 2.0, 0.8, "H", tip="A", tip_len=0.7, mirrored=True)
    spike(d, "boots", "right_leg", "toe spike", (0.0, 11.4, -3.9), (82, 0, 0), 1.8, 1.0, "P", tip="B", tip_len=0.8, glow="B")
    return d


def marshals_bonemail():
    m = dict(gi._BM)
    m.update({"O": gi._BONE.shifted(base=4, texture=gi.flat), "Q": gi._BONE.shifted(base=1, texture=gi.flat),
              "R": gi.CRIMSON.shifted(base=3, texture=gi.flat), "V": gi.CRIMSON.shifted(base=1, texture=gi.flat)})
    d = DetailSet("marshals_bonemail", m, "a bone skull helm with a tall red crest, dark eye sockets with ember-red lights and a toothed "
                                          "jaw; bone plate over a rib cage with a glowing red sigil, bone-spiked pauldrons, a banner-cloth "
                                          "belt; a long banner hanging over each thigh with bone knee guards; bone greaves")

    def banner(salt):
        def pick(u, v, w, h):
            if v >= h - 2 and (u + v) % 2 == 0:
                return "."
            return "R" if (u, v) == (w // 2, 1) else "A"
        return pick

    def teeth(u, v, w, h):
        return "D" if u % 2 else "O"

    helm_bands(d, paint("P", bottom="D", sides=bottom_row("P", "D")), glow="B")
    d.box("helmet", "head", "crest", (-0.7, -13.4, -4.6, 0.7, -10.0, 4.4), paint("A", sides=bottom_row("A", "V"), top="R"), tweak=tweaks(sides=lit_edge))
    d.box("helmet", "head", "crest, tuft", (-0.9, -12.4, 4.0, 0.9, -8.0, 5.9), paint("A", sides=frayed("A", 5)), tweak=tweaks(sides=lit_edge))
    d.box("helmet", "head", "brow ridge", (-4.4, -6.2, -6.0, 4.4, -5.0, -5.3), paint("Q", front=bottom_row("P", "D")), tweak=tweaks(front=lit_edge))
    d.pair("helmet", "head", "eye socket", (-3.4, -5.2, -5.9, -1.2, -3.2, -5.2), paint("K"), tweak=tweaks(front=lit_edge))
    d.pair("helmet", "head", "eye light", (-2.7, -4.6, -6.0, -1.9, -3.8, -5.9), paint("B"), glow="B")
    d.box("helmet", "head", "jaw", (-2.6, -1.8, -5.8, 2.6, -0.2, -5.2), paint("P", front=teeth), tweak=tweaks(front=lit_edge))
    d.pair("helmet", "head", "cheek bone", (-5.6, -3.4, -5.7, -3.8, -0.4, -1.0), paint("P", sides=bottom_row("P", "D")), tweak=tweaks(sides=lit_edge))
    spike(d, "helmet", "head", "helm spike", (-3.8, -10.4, -1.0), (0, 0, -28), 2.6, 1.0, "O", tip="Q", tip_len=0.8)

    plate_body(d, strip=paint("P"), collar="P", belt=paint("A", sides=bottom_row("A", "V")), buckle=paint("O"),
               pauldron=paint("P", bottom="D", sides=bottom_row("P", "Q")), knee=paint("P", bottom="D", front=bottom_row("P", "D")),
               cuff=paint("P", bottom="D", sides=bottom_row("P", "Q")), boot=paint("P", front=bottom_row("P", "D")), strips=(),
               glow="B", back_strips=False)
    for i, y in enumerate((1.0, 2.6, 4.2, 5.6)):
        w = (3.4, 3.8, 3.6, 2.8)[i]
        d.pair("chest", "body", f"rib {i + 1}", (-w, y, -3.28, -0.5, y + 0.9, -2.8), paint("O", front=bottom_row("O", "Q")), tweak=tweaks(front=lit_edge))
    d.box("chest", "body", "breastbone", (-0.5, 0.8, -3.25, 0.5, 6.6, -2.7), paint("Q"), tweak=tweaks(front=lit_edge))
    d.box("chest", "body", "sigil", (-1.1, 2.4, -3.3, 1.1, 4.2, -2.9), paint("B"), glow="B")
    d.box("chest", "body", "spine", (-0.7, 0.6, 2.7, 0.7, 8.0, 3.25), paint("O", back=bottom_row("O", "Q")), tweak=tweaks(back=lit_edge))
    for i, y in enumerate((1.6, 3.2, 4.8)):
        d.pair("chest", "body", f"rib, back {i + 1}", (-3.4, y, 2.7, -0.7, y + 0.8, 3.2), paint("O"))
    for side, z in (("", -1.8), (", back", 1.8)):
        spike(d, "chest", "right_arm", f"pauldron bone{side}", (-3.6, -3.9, z), (0, 0, -34), 3.0, 1.3, "O", tip="Q", tip_len=1.0)
    d.pair("legs", "right_leg", "banner", (-1.8, 0.6, -3.2, 1.8, 6.6, -2.6), paint("A", front=banner(1), sides=bottom_row("A", "V")), tweak=tweaks(front=lit_edge))
    d.pair("legs", "right_leg", "banner sigil", (-0.6, 2.0, -3.4, 0.6, 3.2, -3.2), paint("B"), glow="B")
    d.pair("legs", "right_leg", "knee guard", (-1.9, 6.0, -3.0, 1.9, 7.6, -2.2), paint("O", front=bottom_row("O", "Q")), tweak=tweaks(top=rim_light, front=lit_edge))
    spike(d, "boots", "right_leg", "toe bone", (0.0, 11.4, -3.9), (82, 0, 0), 1.8, 1.0, "O", tip="Q", tip_len=0.8)
    return d


def voidwalker():
    m = dict(gi._VW)
    m.update({"S": gi._VOID_PLATE.shifted(base=3, texture=gi.flat), "O": gi._VOID_PLATE.shifted(base=1, texture=gi.flat),
              "W": gi.RIFT.shifted(base=4, texture=gi.flat), "T": gi._VOID_ROBE.shifted(base=2, texture=gi.flat)})
    d = DetailSet("voidwalker", m, "a tall obsidian-purple cowl with a drape, a dark recess lit by two violet eyes, violet seams "
                                   "and floating void shards round the head; a long robe split by glowing violet seams under an "
                                   "obsidian breastplate, jagged obsidian pauldrons and drifting shards at the shoulders and "
                                   "waist; seamed trousers with obsidian knee plates; seamed boot cuffs with void shards")

    def tatter(salt, main="P"):
        def pick(u, v, w, h):
            if v >= h - 1 and hashed(u, v, w, salt) % 2 == 0:
                return "."
            return main
        return pick

    def seam(u, v, w, h):
        return "B" if u in (w // 2 - 1, w // 2) else "P"

    def seam_side(salt):
        def pick(u, v, w, h):
            if v == h - 1:
                return "B" if hashed(u, w, salt) % 3 == 0 else "D"
            return "P"
        return pick

    d.box("helmet", "head", "crown", (-4, -10, -4, 4, -9, 4), paint("P"), tweak=tweaks(top=rim_light))
    tip = Pivot("peak", (0.3, -10, 0.8), (-22, 0, 6))
    d.box("helmet", "head", "peak", (-1.5, -4.8, -1.5, 1.5, 0, 1.5), paint("P", top="D"), pivot=tip, tweak=tweaks(sides=lit_edge))
    d.box("helmet", "head", "cowl, back", (-5.4, -8.4, 3.8, 5.4, -1.0, 6.2), paint("P", bottom="D", sides=seam_side(1)), tweak=tweaks(sides=lit_edge))
    d.pair("helmet", "head", "cowl, side", (-5.6, -8.4, -4.0, -4.8, -1.4, 3.8), paint("P", sides=tatter(2)), tweak=tweaks(sides=lit_edge))
    d.box("helmet", "head", "cowl, rim", (-3.6, -7.4, -6.8, 3.6, -5.2, -4.8), paint("P", front=bottom_row("P", "D")), tweak=tweaks(sides=lit_edge))
    d.pair("helmet", "head", "cowl, cheek", (-5.9, -7.2, -6.6, -3.6, -0.2, -4.6), paint("P", sides=tatter(3)), tweak=tweaks(sides=lit_edge))
    d.box("helmet", "head", "recess", (-3.2, -5.2, -6.3, 3.2, -1.4, -5.6), paint("K"))
    d.pair("helmet", "head", "violet eye", (-2.5, -4.4, -6.5, -1.3, -3.4, -6.2), paint("B"), glow="B")
    d.box("helmet", "head", "seam, brow", (-3.4, -6.0, -6.9, 3.4, -5.4, -6.6), paint("B"), glow="B")
    d.box("helmet", "head", "seam, back", (-0.5, -8.2, 6.2, 0.5, -1.2, 6.5), paint("B"), glow="B")
    d.pair("helmet", "head", "void shard", (-7.8, -9.0, -1.0, -7.0, -6.0, 0.2), paint("B", sides=bottom_row("B", "W")),
           pivot=Pivot("head shard", (-7.4, -7.5, -0.4), (0, 0, 16)), glow="BW")
    d.pair("helmet", "head", "void shard, small", (-6.4, -12.0, 1.0, -5.8, -10.0, 1.8), paint("B"),
           pivot=Pivot("head shard small", (-6.1, -11.0, 1.4), (14, 0, -24)), glow="B")

    d.pair("chest", "body", "collar", (-4.4, -1.6, -3.55, -2.2, 0.4, -2.6), paint("A"), tweak=tweaks(sides=lit_edge, top=rim_light))
    d.pair("chest", "body", "collar, shoulder", (-4.4, -1.6, -2.6, -2.2, -0.6, 2.6), paint("A"), tweak=tweaks(top=lit_edge))
    d.box("chest", "body", "collar, back", (-4.4, -1.6, 2.6, 4.4, 0.2, 3.25), paint("A"), tweak=tweaks(sides=lit_edge))
    d.box("chest", "body", "robe, front", (-4.2, 0.6, -3.1, 4.2, 7.4, -2.5), paint("P", front=seam, sides=bottom_row("P", "D")), tweak=tweaks(front=lit_edge), glow="B")
    d.pair("chest", "body", "robe, flap", (-5.1, 0.8, -3.2, -3.4, 7.2, -2.6), paint("P", sides=seam_side(4)), tweak=tweaks(sides=lit_edge), glow="B")
    d.box("chest", "body", "robe, back", (-4.2, 0.8, 2.7, 4.2, 7.4, 3.25), paint("P", back=seam, sides=bottom_row("P", "D")), tweak=tweaks(back=lit_edge), glow="B")
    d.box("chest", "body", "breastplate", (-3.0, 1.0, -3.28, 3.0, 4.6, -2.9), paint("A", front=bottom_row("A", "S")), tweak=tweaks(front=lit_edge, top=rim_light))
    d.box("chest", "body", "core", (-0.8, 2.0, -3.3, 0.8, 3.6, -3.2), paint("B"), glow="B")
    d.ring("chest", "body", "sash", -5.2, 5.2, -3.3, 3.2, 6.6, 7.6, 0.6, paint("A", sides=bottom_row("A", "C")), tweak=tweaks(sides=lit_edge))
    d.ring("chest", "body", "hem", -5.4, 5.4, -3.5, 3.3, 8.0, 9.4, 0.9, paint("P", sides=tatter(5)), tweak=tweaks(sides=lit_edge))
    d.pair("chest", "right_arm", "pauldron", (-4.8, -3.8, -3.7, 0.9, -0.8, 3.7), paint("A", bottom="O", sides=bottom_row("A", "O")),
           tweak=tweaks(top=rim_light, sides=lit_edge))
    spike(d, "chest", "right_arm", "pauldron shard", (-3.4, -3.9, 0.0), (0, 0, -24), 4.2, 1.6, "A", tip="B", tip_len=1.2, glow="B")
    spike(d, "chest", "right_arm", "pauldron shard, front", (-3.6, -3.9, -2.2), (-10, 0, -40), 2.6, 1.1, "O", tip="B", tip_len=0.9, glow="B")
    d.pair("chest", "right_arm", "void shard", (-7.2, -4.0, -0.6, -6.4, -0.6, 0.6), paint("B", sides=bottom_row("B", "W")),
           pivot=Pivot("arm shard", (-6.8, -2.2, 0.0), (10, 0, 14)), glow="BW")
    d.pair("chest", "body", "void shard, waist", (-7.0, 4.4, 1.6, -6.3, 7.0, 2.6), paint("B"),
           pivot=Pivot("waist shard", (-6.6, 5.7, 2.1), (-12, 0, 10)), glow="B")

    d.pair("legs", "right_leg", "tasset", (-2.8, 0.6, -3.2, 2.8, 3.4, -2.2), paint("P", sides=seam_side(6), bottom="D"), tweak=tweaks(sides=lit_edge), glow="B")
    d.pair("legs", "right_leg", "tasset, side", (-3.2, 0.6, -2.2, -2.2, 3.4, 2.2), paint("P", sides=seam_side(7)), glow="B")
    d.pair("legs", "right_leg", "knee plate", (-1.9, 3.6, -3.2, 1.9, 5.8, -2.2), paint("A", front=bottom_row("A", "S")), tweak=tweaks(top=rim_light, front=lit_edge))
    d.pair("legs", "right_leg", "leg seam", (-0.4, 5.9, -3.0, 0.4, 7.0, -2.3), paint("B"), glow="B")
    d.ring("legs", "body", "belt", -4.8, 4.8, -2.8, 2.8, 7.1, 8.1, 0.6, paint("A"))
    for leg in ("right_leg", "left_leg"):
        d.ring("boots", leg, "cuff", -3.5, 3.5, -3.5, 3.5, 5.8, 7.8, 0.8, paint("P", sides=seam_side(8), bottom="D"), tweak=tweaks(sides=lit_edge), glow="B")
    d.pair("boots", "right_leg", "toe plate", (-2.3, 10.4, -3.9, 2.3, 12.8, -2.4), paint("A", front=bottom_row("A", "S")), tweak=tweaks(top=rim_light, front=lit_edge))
    d.pair("boots", "right_leg", "void shard, boot", (-5.4, 9.6, -0.6, -4.7, 12.0, 0.6), paint("B"),
           pivot=Pivot("boot shard", (-5.0, 10.8, 0.0), (8, 0, 12)), glow="B")
    return d


def hallowed():
    m = {"P": gi.PUMPKIN, "D": gi.PUMPKIN.shifted(base=1, texture=gi.flat), "G": gi.CANDLE, "S": gi.PUMPKIN_STEM,
         "V": gi.LEAF.shifted(texture=gi.flat), "N": gi.HALLOW_NIGHT, "K": gi.HALLOW_NIGHT.shifted(base=0, texture=gi.flat),
         "O": gi.PUMPKIN.shifted(texture=gi.flat), "U": gi.HALLOW_PURPLE, "R": gi.ROPE, "W": gi.STRAW,
         "Y": gi.STRAW.shifted(base=3, texture=gi.flat)}
    d = DetailSet("hallowed", m, "a whole carved pumpkin over the head with ribs, a curled stem, a leaf and a glowing "
                                 "jack-o'-lantern face; an orange collar, stitched pockets and a glowing pumpkin brooch, "
                                 "pumpkin pauldrons; patched knees, a rope belt and straw poking out of the boots; orange "
                                 "boot cuffs and toe caps")
    # Helmet: a pumpkin bigger than the head. Its face is the only part that glows.
    # A classic jack-o'-lantern: big triangle eyes, a nose, and a wide grin with teeth.
    face = ["PPPPPPPPPPPP", "PPGPPPPPPGPP", "PGGGPPPPGGGP", "GGGGGPPGGGGG", "PPPPPGGPPPPP", "PPPPPPPPPPPP",
            "PGPPPPPPPPGP", "PGGPGGGGPGGP", "PPGGGGGGGGPP", "PPPGGPPGGPPP", "PPPPPPPPPPPP"]

    def ridged(cols):
        def pick(u, v, w, h):
            return "D" if u in cols else "P"
        return pick

    d.box("helmet", "head", "pumpkin", (-6, -9.8, -6, 6, 0.8, 6),
          paint("P", front=face, right=ridged((3, 8)), left=ridged((3, 8)), back=ridged((3, 8)), top=ridged((3, 8))),
          tweak=tweaks(top=rim_light, front=lit_edge), glow="G")
    d.box("helmet", "head", "rib, back", (-1, -9.3, 6, 1, 0.4, 6.7), paint("D"), tweak=tweaks(sides=lit_edge))
    d.pair("helmet", "head", "rib, side", (-6.7, -9.3, -1, -6, 0.4, 1), paint("D"), tweak=tweaks(sides=lit_edge))
    d.pair("helmet", "head", "rib, front", (-6.5, -9.3, -4.4, -6, 0.4, -3.4), paint("D"), tweak=tweaks(sides=lit_edge))
    d.box("helmet", "head", "stem", (-1.2, -12.6, -1.2, 1.2, -9.7, 1.2), paint("S"), tweak=tweaks(sides=lit_edge, top=rim_light))
    d.box("helmet", "head", "stem, curl", (0.4, -13.8, -0.6, 2.8, -12.6, 0.6), paint("S"), tweak=tweaks(top=lit_edge))
    d.box("helmet", "head", "leaf", (1.4, -10.6, -0.7, 5.4, -9.9, 1.3), paint("V"), tweak=tweaks(top=rim_light))
    d.box("helmet", "head", "vine", (-4.6, -10.5, -2, -3.2, -9.9, -0.8), paint("V"), tweak=tweaks(top=lit_edge))

    # Chest: an orange collar, stitched pockets, a glowing pumpkin brooch, pumpkin pauldrons and orange cuffs.
    collar = paint("O")
    d.pair("chest", "body", "collar", (-4.4, -1.6, -3.55, -2.2, 0.4, -2.6), collar, tweak=tweaks(sides=lit_edge, top=rim_light))
    d.pair("chest", "body", "collar, V", (-2.2, -0.4, -3.25, -0.8, 1.6, -2.6), collar, tweak=tweaks(sides=lit_edge))
    d.pair("chest", "body", "collar, shoulder", (-4.4, -1.6, -2.6, -2.2, -0.6, 2.6), collar, tweak=tweaks(top=lit_edge))
    d.box("chest", "body", "collar, back", (-4.4, -1.6, 2.6, 4.4, 0.2, 3.25), collar, tweak=tweaks(sides=lit_edge))
    d.box("chest", "body", "brooch", (-1.5, 1.0, -3.3, 1.5, 4.0, -2.65), paint("P", front=["GPG", "PGP", "PPP"], top="S"),
          tweak=tweaks(front=lit_edge), glow="G")
    d.pair("chest", "body", "pocket", (-3.6, 5.8, -3.35, -0.8, 8.0, -2.7), paint("N", front=rows(("O", 1), ("N", 1))),
           tweak=tweaks(sides=lit_edge, front=lit_edge))
    cap = paint("N", bottom="K", sides=bottom_row("N", "O"))
    d.pair("chest", "right_arm", "epaulette", (-4.7, -3.7, -3.7, 0.9, -1.3, 3.7), cap, tweak=tweaks(top=rim_light, sides=lit_edge))
    d.pair("chest", "right_arm", "pumpkin", (-3.3, -4.9, -1.0, -1.1, -3.7, 1.2), paint("P", top="S", sides=ridged((1,))))
    d.pair("chest", "right_arm", "cuff", (-4.9, 2.8, -3.6, 2.9, 4.2, 3.6), paint("N", sides=bottom_row("N", "O")),
           tweak=tweaks(sides=lit_edge))
    d.ring("chest", "body", "belt", -5.4, 5.4, -3.4, 3.3, 8.3, 9.5, 0.8, paint("K"), tweak=tweaks(sides=lit_edge))
    d.box("chest", "body", "buckle", (-1.0, 7.9, -3.7, 1.0, 9.45, -3.2), paint("O", front=["GG", "GG"]), glow="G")

    # Leggings: orange knee patches, a rope belt with a knot, straw poking out above the boots.
    d.pair("legs", "right_leg", "knee patch", (-2.0, 3.1, -3.05, 2.0, 5.9, -2.1),
           paint("O", front=["OOOO", "OUUO", "OOOO"]), tweak=tweaks(front=lit_edge))
    d.ring("legs", "body", "belt", -4.8, 4.8, -2.8, 2.8, 7.1, 8.1, 0.6, paint("R"))
    d.box("legs", "body", "knot", (-0.8, 7.0, -3.1, 0.8, 8.8, -2.3), paint("R"), tweak=tweaks(front=lit_edge))
    d.pair("legs", "body", "belt straw", (1.6, 8.1, -2.9, 3.0, 10.2, -2.3), paint("W", front=frayed("W", 3, 2)))
    for leg in ("right_leg", "left_leg"):
        d.ring("legs", leg, "straw hem", -3.2, 3.2, -3.2, 3.2, 8.0, 9.6, 0.7,
               paint("W", sides=lambda u, v, w, h: "Y" if v == 0 else frayed("W", 7, 2)(u, v, w, h)))

    # Boots: orange cuffs and toe caps.
    for leg in ("right_leg", "left_leg"):
        d.ring("boots", leg, "cuff", -3.3, 3.3, -3.3, 3.3, 5.8, 7.3, 0.7, paint("O", bottom="K"), tweak=tweaks(sides=lit_edge))
    d.pair("boots", "right_leg", "toe cap", (-2.5, 10.3, -3.6, 2.5, 12.8, -2.4), paint("N", front=rows(("N", 2), ("O", 1))),
           tweak=tweaks(top=rim_light, front=lit_edge))
    return d


DETAIL_SETS = {"harvester": harvester, "prospector": prospector, "woodsman": woodsman, "vanguard": vanguard,
               "delver": delver, "skyforged": skyforged, "deepwarden": deepwarden, "emberheart": emberheart,
               "lumenward": lumenward, "fieldhand": fieldhand, "sunward": sunward, "frostgrove": frostgrove,
               "cinderfield": cinderfield, "moonharvest": moonharvest, "barkguard": barkguard,
               "mistwarden": mistwarden, "willowshade": willowshade, "emberbark": emberbark, "starwood": starwood,
               "fangguard": fangguard, "stormplate": stormplate, "riftweave": riftweave, "cinderguard": cinderguard,
               "starforged": starforged,
               "angler": angler, "reefwalker": reefwalker, "abyssal": abyssal, "leviathan": leviathan,
               "blightforged": blightforged, "cinderplate": cinderplate, "rimeguard": rimeguard, "mirewoven": mirewoven,
               "arachne_carapace": arachne_carapace, "tide_regalia": tide_regalia, "wardens_shroud": wardens_shroud,
               "runeforged": runeforged, "hellforged": hellforged,
               "gravewarden": gravewarden, "silkstalker": silkstalker, "marshals_bonemail": marshals_bonemail,
               "voidwalker": voidwalker, "hallowed": hallowed}


# ---------------------------------------------------------------- checks
# What detail keeps clear of, as boxes (x0, y0, z0, x1, y1, z1) in each part's space. SURFACES: the vanilla
# armour, the skin and its outer layer (wide and slim arms); a detail face lying on one of them, facing the
# same way, would flicker against it. LANES: the room accessories wear into (docs/design/accessories.md,
# "Depth lanes"): over the upper chest only 1.3 out, the back 1.3, the waist 1.1, the forearms 1.2.
FAR = 99
SURFACES = {
    "head": [(-5, -9, -5, 5, 1, 5), (-4.5, -8.5, -4.5, 4.5, 0.5, 4.5), (-4, -8, -4, 4, 0, 4)],
    "body": [(-5, -1, -3, 5, 13, 3), (-4.5, -0.5, -2.5, 4.5, 12.5, 2.5), (-4.25, -0.25, -2.25, 4.25, 12.25, 2.25),
             (-4, 0, -2, 4, 12, 2)],
    "right_arm": [(-4, -3, -3, 2, 11, 3), (-3.25, -2.25, -2.25, 1.25, 10.25, 2.25), (-2.25, -2.25, -2.25, 1.25, 10.25, 2.25),
                  (-3, -2, -2, 1, 10, 2), (-2, -2, -2, 1, 10, 2)],
    "right_leg": [(-2.9, -0.9, -2.9, 2.9, 12.9, 2.9), (-2.4, -0.4, -2.4, 2.4, 12.4, 2.4),
                  (-2.25, -0.25, -2.25, 2.25, 12.25, 2.25), (-2, 0, -2, 2, 12, 2)],
}
LANES = {
    "body": [("amulet", (-2, 0, -FAR, 2, 5, -3.3)), ("cloak", (-FAR, -FAR, 3.3, FAR, FAR, FAR)),
             ("belt", (-FAR, 9.5, -FAR, FAR, 12.5, -3.1)), ("belt", (-FAR, 9.5, 3.1, FAR, 12.5, FAR)),
             ("belt", (-FAR, 9.5, -FAR, -5.1, 12.5, FAR)), ("belt", (5.1, 9.5, -FAR, FAR, 12.5, FAR))],
    "right_arm": [("bracers", (-FAR, 4.5, -FAR, -4.2, 9, FAR)), ("bracers", (-FAR, 4.5, -FAR, FAR, 9, -3.2)),
                  ("bracers", (-FAR, 4.5, 3.2, FAR, 9, FAR)), ("bracers", (2.2, 4.5, -FAR, FAR, 9, FAR))],
}


def flip(box):
    x0, y0, z0, x1, y1, z1 = box
    return -x1, y0, z0, -x0, y1, z1


SURFACES["left_arm"] = [flip(b) for b in SURFACES["right_arm"]]
SURFACES["left_leg"] = SURFACES["right_leg"]
LANES["left_arm"] = [(name, flip(b)) for name, b in LANES["right_arm"]]


def box_faces(box):
    """(axis, position, facing, rectangle on the other two axes) for the six faces of a box."""
    x0, y0, z0, x1, y1, z1 = box
    return [(0, x0, -1, (y0, y1, z0, z1)), (0, x1, 1, (y0, y1, z0, z1)), (1, y0, -1, (x0, x1, z0, z1)),
            (1, y1, 1, (x0, x1, z0, z1)), (2, z0, -1, (x0, x1, y0, y1)), (2, z1, 1, (x0, x1, y0, y1))]


def overlap(a, b):
    """Overlap of two ranges given as (a0, a1, b0, b1, ...) pairs: the product of the overlapping lengths."""
    out = 1.0
    for i in range(0, len(a), 2):
        out *= max(0.0, min(a[i + 1], b[i + 1]) - max(a[i], b[i]))
    return out


def check(detail):
    """Fails on detail that would flicker against the armour, the skin or other detail, or sits where accessories go."""
    plain = [c for c in detail.cubes if c.pivot is None]  # hinged cards stand clear of everything
    for i, c in enumerate(plain):
        x0, y0, z0, x1, y1, z1 = c.extent
        for lane, (lx0, ly0, lz0, lx1, ly1, lz1) in LANES.get(c.part, ()):
            assert overlap((x0, x1, y0, y1, z0, z1), (lx0, lx1, ly0, ly1, lz0, lz1)) < 1e-6, \
                f"{detail.id}: '{c.label}' reaches into the {lane} lane"
        others = [(o.label, o.extent) for o in plain[i + 1:] if o.part == c.part]
        others += [("the armour or skin", s) for s in SURFACES[c.part]]
        for label, other in others:
            for axis, at, facing, rect in box_faces(c.extent):
                for axis2, at2, facing2, rect2 in box_faces(other):
                    if axis == axis2 and facing == facing2 and abs(at - at2) < 0.02 and overlap(rect, rect2) > 1e-3:
                        raise AssertionError(f"{detail.id}: '{c.label}' shares a face plane with {label} "
                                             f"({'xyz'[axis]} = {at})")


# ---------------------------------------------------------------- texture layout and paint
def face_rects(cube, u=0, v=0):
    """{face: (x, y, w, h)} of the faces the box draws, for texture offset (u, v); empty faces left out."""
    w, h, d = cube.size
    rects = gi.box(u, v, w, h, d)
    return {f: r for f, r in rects.items() if f in cube.visible_faces() and r[2] > 0 and r[3] > 0}


def pack(cubes):
    """Shelf-packs every box that owns its texture; returns the texture size. Deterministic."""
    owners = [c for c in cubes if c.source is c]
    sizes = {}
    for c in owners:
        rects = face_rects(c).values()
        sizes[id(c)] = (max(x + w for x, y, w, h in rects), max(y + h for x, y, w, h in rects))
    order = sorted(owners, key=lambda c: (-sizes[id(c)][1], -sizes[id(c)][0]))
    for width in (64, 128):
        x = y = shelf = 0
        placed = {}
        for c in order:
            w, h = sizes[id(c)]
            if x + w > width:
                x, y, shelf = 0, y + shelf, 0
            placed[id(c)] = (x, y)
            x += w
            shelf = max(shelf, h)
        height = y + shelf
        if height <= width:
            break
    for c in cubes:
        c.uv = placed[id(c.source)]
    texture_height = 32
    while texture_height < height:
        texture_height *= 2
    return width, texture_height


def paint_detail(detail):
    """Renders the detail texture (and its glow pixels, or None) with generate_items' shading."""
    width, height = detail.size
    mask = [["."] * width for _ in range(height)]
    tweak = [["."] * width for _ in range(height)]
    faces = [[None] * width for _ in range(height)]
    glowing = []
    for c in detail.cubes:
        if c.source is not c:
            continue
        for face, (x0, y0, w, h) in face_rects(c, *c.uv).items():
            for v in range(h):
                for u in range(w):
                    x, y = x0 + u, y0 + v
                    assert mask[y][x] == "." and faces[y][x] is None, f"{detail.id}: {c.label} overlaps"
                    mask[y][x] = c.paint(face, u, v, w, h)
                    t = c.tweak(face, u, v, w, h) if c.tweak else None
                    tweak[y][x] = t or "."
                    faces[y][x] = (face, x0, y0, w, h)
                    if c.glow and mask[y][x] in c.glow:
                        glowing.append((x, y))
    img = gi.render(mask, detail.materials, tweak, outline=False, faces=faces)
    glow = None
    if glowing:
        glow = Image.new("RGBA", img.size, (0, 0, 0, 0))
        for x, y in glowing:
            glow.putpixel((x, y), img.getpixel((x, y)))
    return img, glow


# ---------------------------------------------------------------- Java
def jfloat(f):
    f = round(f, 4)
    text = f"{f:.4f}".rstrip("0")
    return (text + "0" if text.endswith(".") else text) + "F"


def java_box(c):
    x, y, z = c.origin
    w, h, d = c.size
    gx, gy, gz = c.grow
    args = ", ".join(jfloat(a) for a in (x, y, z, w, h, d))
    if c.faces:
        assert not any(abs(g) > 1e-6 for g in (gx, gy, gz)), f"{c.label}: a card must be whole pixels"
        sides = ", ".join("Direction." + DIRECTIONS[f] for f in c.faces)
        args += f", EnumSet.of({sides})"
    elif any(abs(g) > 1e-6 for g in (gx, gy, gz)):
        args += f", new CubeDeformation({jfloat(gx)}, {jfloat(gy)}, {jfloat(gz)})"
    mirror = ".mirror()" if c.mirror else ""
    unmirror = ".mirror(false)" if c.mirror else ""
    return f".texOffs({c.uv[0]}, {c.uv[1]}){mirror}.addBox({args}){unmirror} // {c.label}"


def java_set(detail):
    summary = textwrap.wrap(f"{detail.id.capitalize()}: {detail.summary}.", 110)
    lines = ["    /**"] + [f"     * {line}" for line in summary] + ["     */",
             f"    static LayerDefinition {detail.id}() {{",
             "        MeshDefinition mesh = ArmorDetailModel.createEmptyMesh();",
             "        PartDefinition root = mesh.getRoot();"]
    for piece in PIECES:
        lines.append(f"        // {piece}")
        for part in PARTS:
            cubes = [c for c in detail.cubes if c.piece == piece and c.part == part]
            if not cubes:
                continue
            group = f"{piece}{''.join(w.capitalize() for w in part.split('_'))}"
            plain = [c for c in cubes if c.pivot is None]
            hinges = list(dict.fromkeys(c.pivot.name for c in cubes if c.pivot))
            target = f"PartDefinition {group} = " if hinges else ""
            lines.append(f"        {target}root.getChild(\"{part}\")"
                         f".addOrReplaceChild(\"{piece}\", CubeListBuilder.create()")
            for c in plain:
                lines.append(f"                {java_box(c)}")
            lines[-1] = lines[-1].replace(" //", ", PartPose.ZERO); //", 1) if plain else lines[-1] + ", PartPose.ZERO);"
            for name in hinges:
                hinged = [c for c in cubes if c.pivot and c.pivot.name == name]
                p = hinged[0].pivot
                lines.append(f"        {group}.addOrReplaceChild(\"{name}\", CubeListBuilder.create()")
                for c in hinged:
                    lines.append(f"                {java_box(c)}")
                pose = ", ".join([jfloat(a) for a in p.pos] + [jfloat(math.radians(r)) for r in p.rot])
                lines[-1] = lines[-1].replace(" //", f", PartPose.offsetAndRotation({pose})); //", 1)
    lines.append(f"        return LayerDefinition.create(mesh, {detail.size[0]}, {detail.size[1]});")
    lines.append("    }")
    return "\n".join(lines)


def write_java(details):
    body = "\n\n".join(java_set(d) for d in details)
    builders = ("CubeDeformation", "CubeListBuilder", "LayerDefinition", "MeshDefinition", "PartDefinition")
    imports = ["net.minecraft.client.model.geom.PartPose"]
    imports += [f"net.minecraft.client.model.geom.builders.{b}" for b in builders]
    if any(c.faces for d in details for c in d.cubes):
        imports = ["java.util.EnumSet"] + imports + ["net.minecraft.core.Direction"]
    text = (
        "package com.skyrpg.items.client.armor;\n\n"
        + "".join(f"import {i};\n" for i in imports)
        + "\n"
        "/**\n"
        " * The armour detail models' geometry, one mesh per set. GENERATED by tools/art/generate_armor_depth.py,\n"
        " * which also paints the textures these boxes are mapped onto (it picks the texture offsets), so change\n"
        " * the shapes there and re-run it instead of editing this file.\n"
        " * <p>\n"
        " * Each mesh is an empty player mesh with the detail hanging off its parts, one group per armour piece\n"
        " * (\"helmet\", \"chest\", \"legs\", \"boots\"), so it moves with the body. Units are model pixels.\n"
        " */\n"
        "final class ArmorDetailMeshes {\n"
        "    private ArmorDetailMeshes() {\n"
        "    }\n\n"
        + body + "\n}\n")
    JAVA_OUT.write_text(text, encoding="utf-8", newline="\n")


# ---------------------------------------------------------------- 3D preview
# A tiny software renderer: orthographic, depth-tested, textured boxes laid out exactly like
# ModelPart.Cube (UVs, mirroring), lit like Minecraft entities. Good enough to judge shapes and paint.
PIVOTS = {"head": (0, 0, 0), "body": (0, 0, 0), "right_arm": (-5, 2, 0), "left_arm": (5, 2, 0),
          "right_leg": (-1.9, 12, 0), "left_leg": (1.9, 12, 0)}


def mat_mul(a, b):
    return [[sum(a[i][k] * b[k][j] for k in range(3)) for j in range(3)] for i in range(3)]


def mat_vec(m, v):
    return tuple(sum(m[i][k] * v[k] for k in range(3)) for i in range(3))


def rotation(xr, yr, zr):
    """Rz * Ry * Rx, as JOML's rotationZYX that ModelPart uses."""
    cx, sx, cy, sy, cz, sz = math.cos(xr), math.sin(xr), math.cos(yr), math.sin(yr), math.cos(zr), math.sin(zr)
    rx = [[1, 0, 0], [0, cx, -sx], [0, sx, cx]]
    ry = [[cy, 0, sy], [0, 1, 0], [-sy, 0, cy]]
    rz = [[cz, -sz, 0], [sz, cz, 0], [0, 0, 1]]
    return mat_mul(rz, mat_mul(ry, rx))


def polygons(origin, size, grow, mirror, uv):
    """ModelPart.Cube's polygons: (face, [(position, (u, v)) x 4]), positions in part space."""
    (x, y, z), (w, h, d), (gx, gy, gz), (u, v) = origin, size, grow, uv
    x0, y0, z0, x1, y1, z1 = x - gx, y - gy, z - gz, x + w + gx, y + h + gy, z + d + gz
    if mirror:
        x0, x1 = x1, x0
    t0, t1, t2, t3 = (x0, y0, z0), (x1, y0, z0), (x1, y1, z0), (x0, y1, z0)
    l0, l1, l2, l3 = (x0, y0, z1), (x1, y0, z1), (x1, y1, z1), (x0, y1, z1)
    u0, u1, u2, u22, u3, u4 = u, u + d, u + d + w, u + d + w + w, u + d + w + d, u + d + w + d + w
    v0, v1, v2 = v, v + d, v + d + h
    quads = {"top": ([l1, l0, t0, t1], u1, v0, u2, v1), "bottom": ([t2, t3, l3, l2], u2, v1, u22, v0),
             "right": ([t0, l0, l3, t3], u0, v1, u1, v2), "front": ([t1, t0, t3, t2], u1, v1, u2, v2),
             "left": ([l1, t1, t2, l2], u2, v1, u3, v2), "back": ([l0, l1, l2, l3], u3, v1, u4, v2)}
    out = {}
    for face, (verts, a, b, c, e) in quads.items():
        uvs = [(c, b), (a, b), (a, e), (c, e)]
        out[face] = list(zip(verts, uvs))
    return out


class Scene:
    def __init__(self):
        self.boxes = []  # (part, pivot or None, origin, size, grow, mirror, uv, texture, faces, emissive)

    def add(self, part, origin, size, grow, mirror, uv, texture, faces=None, pivot=None, emissive=False):
        self.boxes.append((part, pivot, origin, size, grow, mirror, uv, texture, faces, emissive))


def skin_texture():
    """A plain made-up skin to dress: tan face, dark hair, a slate shirt and trousers."""
    skin = Image.new("RGBA", (64, 64), (0, 0, 0, 0))
    d = ImageDraw.Draw(skin)

    def fill(b, faces, color):
        for f in faces:
            x, y, w, h = b[f]
            d.rectangle([x, y, x + w - 1, y + h - 1], fill=color)

    tone, hair, shirt = (205, 158, 122, 255), (74, 50, 36, 255), (74, 92, 110, 255)
    pants, shoe = (52, 56, 76, 255), (60, 52, 48, 255)
    head = gi.box(0, 0, 8, 8, 8)
    fill(head, head, tone)
    fill(head, ["top", "back"], hair)
    for f in ("right", "left"):
        x, y, w, h = head[f]
        d.rectangle([x, y, x + w - 1, y + 2], fill=hair)
    x, y, w, h = head["front"]
    d.rectangle([x, y, x + 7, y + 1], fill=hair)
    for ex in (1, 5):
        d.point([(x + ex, y + 4)], fill=(255, 255, 255, 255))
        d.point([(x + ex + 1, y + 4)], fill=(60, 70, 140, 255))
    d.rectangle([x + 3, y + 6, x + 4, y + 6], fill=(150, 100, 80, 255))
    for b in (gi.box(16, 16, 8, 12, 4), gi.box(40, 16, 4, 12, 4), gi.box(32, 48, 4, 12, 4)):
        fill(b, b, shirt)
    for b in (gi.box(40, 16, 4, 12, 4), gi.box(32, 48, 4, 12, 4)):
        fill(b, ["right", "front", "left", "back", "bottom"], tone)
        for f in gi.SIDES:
            x, y, w, h = b[f]
            d.rectangle([x, y, x + w - 1, y + 3], fill=shirt)
    for b in (gi.box(0, 16, 4, 12, 4), gi.box(16, 48, 4, 12, 4)):
        fill(b, b, pants)
        for f in gi.SIDES:
            x, y, w, h = b[f]
            d.rectangle([x, y + 10, x + w - 1, y + h - 1], fill=shoe)
    return skin


def wearer(scene, skin, outer=None, inner=None, detail=None, detail_tex=None, glow=None, pieces=PIECES):
    """Adds a player in a set of armour: skin, the worn layers and the detail boxes."""
    for part, origin, size, uv, mirror in (
            ("head", (-4, -8, -4), (8, 8, 8), (0, 0), False), ("body", (-4, 0, -2), (8, 12, 4), (16, 16), False),
            ("right_arm", (-3, -2, -2), (4, 12, 4), (40, 16), False), ("left_arm", (-1, -2, -2), (4, 12, 4), (32, 48), False),
            ("right_leg", (-2, 0, -2), (4, 12, 4), (0, 16), False), ("left_leg", (-2, 0, -2), (4, 12, 4), (16, 48), False)):
        scene.add(part, origin, size, (0, 0, 0), mirror, uv, skin)
    if outer is not None:
        armour = {"helmet": [("head", (-4, -8, -4), (8, 8, 8), (0, 0), False, 1.0)],
                  "chest": [("body", (-4, 0, -2), (8, 12, 4), (16, 16), False, 1.0),
                            ("right_arm", (-3, -2, -2), (4, 12, 4), (40, 16), False, 1.0),
                            ("left_arm", (-1, -2, -2), (4, 12, 4), (40, 16), True, 1.0)],
                  "boots": [("right_leg", (-2, 0, -2), (4, 12, 4), (0, 16), False, 0.9),
                            ("left_leg", (-2, 0, -2), (4, 12, 4), (0, 16), True, 0.9)]}
        for piece, boxes in armour.items():
            if piece in pieces:
                for part, origin, size, uv, mirror, g in boxes:
                    scene.add(part, origin, size, (g, g, g), mirror, uv, outer)
        if "legs" in pieces:
            for part, origin, size, uv, mirror, g in (("body", (-4, 0, -2), (8, 12, 4), (16, 16), False, 0.5),
                                                      ("right_leg", (-2, 0, -2), (4, 12, 4), (0, 16), False, 0.4),
                                                      ("left_leg", (-2, 0, -2), (4, 12, 4), (0, 16), True, 0.4)):
                scene.add(part, origin, size, (g, g, g), mirror, uv, inner)
    if detail is not None:
        for c in detail.cubes:
            if c.piece in pieces:
                pivot = (c.pivot.pos, c.pivot.rot) if c.pivot else None
                scene.add(c.part, c.origin, c.size, c.grow, c.mirror, c.uv, detail_tex, c.faces, pivot)
                if glow is not None and c.glow:
                    scene.add(c.part, c.origin, c.size, c.grow, c.mirror, c.uv, glow, c.faces, pivot, emissive=True)


LIGHTS = [tuple(a / math.sqrt(0.2 ** 2 + 1 + 0.7 ** 2) for a in v) for v in ((0.2, -1.0, -0.7), (-0.2, -1.0, 0.7))]


def draw(scene, yaw=0.0, pitch=0.0, scale=6, pose=None, background=None):
    """Renders the scene seen from the front (yaw turns it, pitch tilts it), `scale` pixels per model pixel."""
    pose = pose or {}
    view = mat_mul(rotation(math.radians(pitch), 0, 0), rotation(0, math.radians(yaw), 0))
    tris = []
    for part, pivot, origin, size, grow, mirror, uv, texture, faces, emissive in scene.boxes:
        rot = rotation(*pose.get(part, (0, 0, 0)))
        hinge = (pivot[0], rotation(*(math.radians(r) for r in pivot[1]))) if pivot else None
        for face, verts in polygons(origin, size, grow, mirror, uv).items():
            if faces and face not in faces:
                continue
            pts = []
            for p, t in verts:
                if hinge:
                    p = tuple(a + b for a, b in zip(mat_vec(hinge[1], p), hinge[0]))
                p = tuple(a + b for a, b in zip(mat_vec(rot, p), PIVOTS[part]))
                pts.append((mat_vec(view, p), t))
            normal = cross(sub_v(pts[1][0], pts[0][0]), sub_v(pts[2][0], pts[0][0]))
            length = math.sqrt(sum(a * a for a in normal)) or 1
            normal = tuple(a / length * (-1 if mirror else 1) for a in normal)
            light = min(1.0, 0.4 + 0.6 * sum(max(0.0, sum(n * l for n, l in zip(normal, ld))) for ld in LIGHTS))
            tris.append((pts, texture, light, emissive))
    tris.sort(key=lambda t: t[3])  # glow goes on top of what it lights up
    xs = [p[0][0] for t in tris for p in t[0]]
    ys = [p[0][1] for t in tris for p in t[0]]
    pad = 4
    ox, oy = -min(xs) * scale + pad, -min(ys) * scale + pad
    w, h = int((max(xs) - min(xs)) * scale) + 2 * pad, int((max(ys) - min(ys)) * scale) + 2 * pad
    img = Image.new("RGBA", (w, h), background or (0, 0, 0, 0))
    depth = [[math.inf] * w for _ in range(h)]
    px = img.load()
    for pts, texture, light, emissive in tris:
        tex = texture.load()
        tw, th = texture.size
        for (pa, ta), (pb, tb), (pc, tc) in ((pts[0], pts[1], pts[2]), (pts[0], pts[2], pts[3])):
            ax, ay = pa[0] * scale + ox, pa[1] * scale + oy
            bx, by = pb[0] * scale + ox, pb[1] * scale + oy
            cx, cy = pc[0] * scale + ox, pc[1] * scale + oy
            area = (bx - ax) * (cy - ay) - (by - ay) * (cx - ax)
            if abs(area) < 1e-9:
                continue
            for y in range(max(0, int(min(ay, by, cy))), min(h, int(max(ay, by, cy)) + 1)):
                for x in range(max(0, int(min(ax, bx, cx))), min(w, int(max(ax, bx, cx)) + 1)):
                    sx, sy = x + 0.5, y + 0.5
                    wa = ((bx - sx) * (cy - sy) - (by - sy) * (cx - sx)) / area
                    wb = ((cx - sx) * (ay - sy) - (cy - sy) * (ax - sx)) / area
                    wc = 1 - wa - wb
                    if wa < -1e-6 or wb < -1e-6 or wc < -1e-6:
                        continue
                    z = wa * pa[2] + wb * pb[2] + wc * pc[2]
                    # Ties go to whatever is drawn later, as in the game (vanilla's arm and body armour
                    # share a plane at the shoulders); glow sits exactly on its box.
                    if z > depth[y][x] + (0.05 if emissive else 0.001):
                        continue
                    u = wa * ta[0] + wb * tb[0] + wc * tc[0]
                    v = wa * ta[1] + wb * tb[1] + wc * tc[1]
                    r, g, b, a = tex[min(tw - 1, max(0, int(u))), min(th - 1, max(0, int(v)))]
                    if a < 26:
                        continue
                    if emissive:
                        px[x, y] = (r, g, b, 255)
                    else:
                        depth[y][x] = z
                        px[x, y] = (round(r * light), round(g * light), round(b * light), 255)
    return img


def sub_v(a, b):
    return tuple(x - y for x, y in zip(a, b))


def cross(a, b):
    return (a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0])


# Part rotations (radians, as HumanoidModel.setupAnim sets them): mid-stride, and standing with the idle arm sway.
WALK = {"right_arm": (-0.55, 0, 0.08), "left_arm": (0.55, 0, -0.08),
        "right_leg": (0.6, 0.005, 0.005), "left_leg": (-0.6, -0.005, -0.005)}
IDLE = {"right_arm": (0, 0, 0.06), "left_arm": (0, 0, -0.06)}


def preview(detail, texture, glow, directory):
    """Contact sheet: the icons next to the worn set before and after, from every side, walking, from above
    and at a distance, plus the detail texture. Returns the 3/4 view for the overview."""
    skin = skin_texture()
    outer, inner = worn_layers(detail.id)
    flat_outer, flat_inner = worn_layers(detail.id, bevelled=False)
    after = Scene()
    wearer(after, skin, outer, inner, detail, texture, glow)
    before = Scene()
    wearer(before, skin, flat_outer, flat_inner)
    scale = 8
    rows_ = [[("before (flat layers)", draw(before, -30, 12, scale, IDLE)), ("after", draw(after, -30, 12, scale, IDLE)),
              ("front", draw(after, 0, 0, scale, IDLE)), ("back", draw(after, 180, 0, scale, IDLE))],
             [("3/4 back", draw(after, 150, 12, scale, IDLE)), ("side", draw(after, -90, 0, scale, IDLE)),
              ("walking", draw(after, 35, 10, scale, WALK)), ("from above", draw(after, -20, 50, scale, IDLE))]]
    icons = [gi.big(gi.render(*gi.ICONS[f"{detail.id}_{p}"]), 5) for p in ("helmet", "chestplate", "leggings", "boots")]
    distance = [draw(after, yaw, 10, 2, IDLE) for yaw in (-30, 150)] + [draw(after, -30, 10, 1, IDLE)]
    tex_big = gi.big(texture, 4)
    row_heights = [max(v.height for _, v in r) + 22 for r in rows_]
    width = 24 + 80 + 24 + max(sum(v.width + 20 for _, v in r) for r in rows_) + 4
    height = 36 + sum(row_heights) + 24 + max(tex_big.height, max(s.height for s in distance)) + 24
    sheet = Image.new("RGBA", (width, height), gi.BG + (255,))
    d = ImageDraw.Draw(sheet)
    for i, line in enumerate(textwrap.wrap(f"{detail.id}: {detail.summary}", 150)[:2]):
        d.text((24, 4 + i * 12), line, fill=gi.LABEL)
    for i, ic in enumerate(icons):
        sheet.paste(gi.checker(80, 80, 16), (24, 36 + i * 92))
        sheet.alpha_composite(ic, (24, 36 + i * 92))
    y = 36
    for r, rh in zip(rows_, row_heights):
        x = 24 + 80 + 24
        for label, v in r:
            d.text((x, y), label, fill=gi.LABEL)
            sheet.alpha_composite(v, (x, y + 16))
            x += v.width + 20
        y += rh
    y += 24
    d.text((24, y - 16), f"armor_detail/{detail.id}.png, {texture.width}x{texture.height}, at 4x; the set at 2x and 1x",
           fill=gi.LABEL)
    sheet.paste(gi.checker(tex_big.width, tex_big.height, 16), (24, y))
    sheet.alpha_composite(tex_big, (24, y))
    x = 24 + tex_big.width + 24
    for s in distance:
        sheet.alpha_composite(s, (x, y))
        x += s.width + 16
    sheet.save(directory / f"depth_{detail.id}.png")
    return rows_[0][1][1]


# ---------------------------------------------------------------- output
def build():
    results = {}
    for set_id, design in DETAIL_SETS.items():
        outer, inner = worn_layers(set_id)
        for layer, img in (("humanoid", outer), ("humanoid_leggings", inner)):
            img.save(ENTITY_TEXTURES / "equipment" / layer / f"{set_id}.png")
        detail = design()
        check(detail)
        detail.size = pack(detail.cubes)
        texture, glow = paint_detail(detail)
        out = ENTITY_TEXTURES / "armor_detail"
        out.mkdir(parents=True, exist_ok=True)
        texture.save(out / f"{set_id}.png")
        glow_path = out / f"{set_id}_glow.png"
        if glow is not None:
            glow.save(glow_path)
        elif glow_path.exists():
            glow_path.unlink()
        results[set_id] = (detail, texture, glow)
    write_java([d for d, _, _ in results.values()])
    return results


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--preview", type=Path, help="directory to write 3D contact sheets to")
    args = parser.parse_args()
    results = build()
    if args.preview:
        args.preview.mkdir(parents=True, exist_ok=True)
        overview = [preview(detail, texture, glow, args.preview) for detail, texture, glow in results.values()]
        size = (sum(v.width + 16 for v in overview) + 16, max(v.height for v in overview) + 32)
        sheet = Image.new("RGBA", size, gi.BG + (255,))
        x = 16
        for v in overview:
            sheet.alpha_composite(v, (x, 16))
            x += v.width + 16
        sheet.save(args.preview / "depth_overview.png")
    cubes = sum(len(d.cubes) for d, _, _ in results.values())
    print(f"wrote {len(results)} re-shaded armour sets and their detail models ({cubes} boxes)")
