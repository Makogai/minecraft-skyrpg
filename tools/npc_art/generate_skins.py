#!/usr/bin/env python3
"""Paints the SkyRPG NPC skins: original 64x64 player skins, one per hub NPC.

    python tools/npc_art/generate_skins.py [preview.png]

Writes npcs/src/main/resources/assets/skyrpg_npcs/textures/entity/npc/<id>.png (the NPC data files point at
skyrpg_npcs:entity/npc/<id>) and, if a path is given, a contact sheet with every skin seen from the front and
the back.

Skins are painted per body part in the standard skin layout. Each part is a box whose four sides are
unrolled into one strip (right side, front, left side, back), so painters can think in "around the body"
coordinates for things like belts, stripes and plaid. Material colours get gentle shading (lighter at the
top, darker on the sides and back) and a little noise so cloth and hair don't look flat; face details are
placed exactly.
"""
import random
import sys
from pathlib import Path

from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parents[2]
OUT = ROOT / "npcs" / "src" / "main" / "resources" / "assets" / "skyrpg_npcs" / "textures" / "entity" / "npc"

FACE_LIGHT = {"top": 1.07, "bottom": 0.8, "front": 1.0, "right": 0.93, "left": 0.93, "back": 0.88}


def rgb(hex_color):
    h = hex_color.lstrip("#")
    return int(h[0:2], 16), int(h[2:4], 16), int(h[4:6], 16)


def tone(color, factor):
    return tuple(max(0, min(255, round(v * factor))) for v in color[:3])


def blend(a, b, t):
    return tuple(round(a[i] + (b[i] - a[i]) * t) for i in range(3))


def layout(u, v, w, h, d):
    """Where each face of a w*h*d box starting at (u, v) sits in the texture."""
    return {"top": (u + d, v, w, d), "bottom": (u + d + w, v, w, d), "right": (u, v + d, d, h),
            "front": (u + d, v + d, w, h), "left": (u + d + w, v + d, d, h), "back": (u + d + w + d, v + d, w, h)}


class Pixel:
    """One texel of one face. (x, y) are face-local; s runs around the box (None on top and bottom)."""

    def __init__(self, skin, part, face, u, v, x, y, w, h, s, overlay):
        self.skin, self.part, self.face = skin, part, face
        self.u, self.v, self.x, self.y, self.w, self.h, self.s = u, v, x, y, w, h, s
        self.overlay = overlay

    def set(self, color, noise=0.03, raw=False):
        self.skin.paint(self.u, self.v, color, self.face, self.y, self.h, noise, raw)

    def clear(self):
        self.skin.img.putpixel((self.u, self.v), (0, 0, 0, 0))


class Skin:
    def __init__(self, slim, seed):
        self.slim = slim
        self.rng = random.Random(seed)
        self.img = Image.new("RGBA", (64, 64), (0, 0, 0, 0))
        aw = 3 if slim else 4
        self.size = {"head": (8, 8, 8), "body": (8, 12, 4), "rarm": (aw, 12, 4), "larm": (aw, 12, 4),
                     "rleg": (4, 12, 4), "lleg": (4, 12, 4)}
        self.base = {"head": layout(0, 0, 8, 8, 8), "body": layout(16, 16, 8, 12, 4), "rarm": layout(40, 16, aw, 12, 4),
                     "larm": layout(32, 48, aw, 12, 4), "rleg": layout(0, 16, 4, 12, 4), "lleg": layout(16, 48, 4, 12, 4)}
        self.over = {"head": layout(32, 0, 8, 8, 8), "body": layout(16, 32, 8, 12, 4), "rarm": layout(40, 32, aw, 12, 4),
                     "larm": layout(48, 48, aw, 12, 4), "rleg": layout(0, 32, 4, 12, 4), "lleg": layout(0, 48, 4, 12, 4)}

    def paint(self, u, v, color, face, y, h, noise, raw):
        if raw:
            c = color
        else:
            f = FACE_LIGHT[face]
            if face not in ("top", "bottom") and h > 1:
                f *= 1.035 - 0.07 * (y / (h - 1))
            f *= 1 + self.rng.uniform(-noise, noise)
            c = tone(color, f)
        self.img.putpixel((u, v), (c[0], c[1], c[2], 255))

    def pixels(self, part, overlay=False, faces=None):
        w, h, d = self.size[part]
        for face, (fu, fv, fw, fh) in (self.over if overlay else self.base)[part].items():
            if faces and face not in faces:
                continue
            for y in range(fh):
                for x in range(fw):
                    s = {"right": x, "front": d + x, "left": d + w + x, "back": d + w + d + x}.get(face)
                    yield Pixel(self, part, face, fu + x, fv + y, x, y, fw, fh, s, overlay)

    def fill(self, parts, color, overlay=False, faces=None, when=None, noise=0.03):
        for part in parts if isinstance(parts, (list, tuple)) else [parts]:
            for p in self.pixels(part, overlay, faces):
                if when is None or when(p):
                    p.set(color(p) if callable(color) else color, noise)

    def at(self, part, face, x, y, color, overlay=False, raw=True, noise=0.02):
        fu, fv, fw, fh = (self.over if overlay else self.base)[part][face]
        if 0 <= x < fw and 0 <= y < fh:
            self.paint(fu + x, fv + y, color, face, y, fh, noise, raw)

    def face(self, x, y, color, overlay=False, raw=True):
        self.at("head", "front", x, y, color, overlay, raw)

    def save(self, path):
        path.parent.mkdir(parents=True, exist_ok=True)
        self.img.save(path)


# ---------------------------------------------------------------- shared pieces

def base_skin(sk, skin):
    sk.fill(["head", "body", "rarm", "larm", "rleg", "lleg"], skin, noise=0.012)


def eyes(sk, iris, white=(244, 244, 240), brow=None, y=4, inner=True):
    """Two-pixel eyes on row y, looking slightly inward; optional brows on the row above."""
    if inner:
        sk.face(1, y, white), sk.face(2, y, iris), sk.face(5, y, iris), sk.face(6, y, white)
    else:
        sk.face(1, y, iris), sk.face(2, y, white), sk.face(5, y, white), sk.face(6, y, iris)
    if brow:
        for x in (1, 2, 5, 6):
            sk.face(x, y - 1, brow)


def nose_mouth(sk, shadow, mouth, wide=False, nose_y=5, mouth_y=6):
    sk.face(3, nose_y, shadow), sk.face(4, nose_y, tone(shadow, 1.04))
    for x in ((2, 3, 4, 5) if wide else (3, 4)):
        sk.face(x, mouth_y, mouth)


def hair_short(sk, hair, dark, light, front_rows=2, side_rows=4, back_rows=7, fringe=None):
    """Short hair: covers the top, the upper sides and most of the back; fringe pixels drop onto the forehead."""
    sk.fill("head", lambda p: light if (p.x + p.y) % 5 == 0 else hair, faces=["top"], noise=0.05)
    sk.fill("head", hair, faces=["front"], when=lambda p: p.y < front_rows, noise=0.05)
    sk.fill("head", hair, faces=["right", "left"], when=lambda p: p.y < side_rows or (p.y < side_rows + 2 and
            ((p.face == "right" and p.x < 3) or (p.face == "left" and p.x > 4))), noise=0.05)
    sk.fill("head", lambda p: dark if p.y == back_rows - 1 else hair, faces=["back"], when=lambda p: p.y < back_rows, noise=0.05)
    for x, y in fringe or []:
        sk.face(x, y, dark)


def ears(sk, skin_shadow, y=4):
    """A darker ear pixel pair on each side, mid-head."""
    for face, x in (("right", 3), ("left", 4)):
        sk.at("head", face, x, y, skin_shadow, raw=False)
        sk.at("head", face, x, y + 1, tone(skin_shadow, 0.95), raw=False)


def shoes(sk, color, rows=3, sole=None, toe=None):
    h = 12
    sk.fill(["rleg", "lleg"], color, when=lambda p: p.face in ("top",) or (p.s is not None and p.y >= h - rows), noise=0.04)
    sk.fill(["rleg", "lleg"], sole or tone(color, 0.6), faces=["bottom"])
    for leg in ("rleg", "lleg"):
        for x in range(4):
            sk.at(leg, "front", x, h - 1, sole or tone(color, 0.7), raw=False)
        if toe:
            sk.at(leg, "front", 1, h - 2, toe), sk.at(leg, "front", 2, h - 2, toe)


def sleeves(sk, color, rows, cuff=None, noise=0.035):
    sk.fill(["rarm", "larm"], color, when=lambda p: p.face == "top" or (p.s is not None and p.y < rows), noise=noise)
    if cuff:
        sk.fill(["rarm", "larm"], cuff, when=lambda p: p.s is not None and p.y == rows - 1, noise=noise)


def hands(sk, color, rows=2):
    sk.fill(["rarm", "larm"], color, when=lambda p: p.face == "bottom" or (p.s is not None and p.y >= 12 - rows), noise=0.02)


def legs(sk, color, rows=12, noise=0.035):
    sk.fill(["rleg", "lleg"], color, when=lambda p: p.face == "top" or (p.s is not None and p.y < rows), noise=noise)


def belt(sk, color, rows=(9,), buckle=None, buckle_x=(3, 4)):
    sk.fill("body", color, when=lambda p: p.s is not None and p.y in rows, noise=0.03)
    if buckle:
        for x in buckle_x:
            for y in rows:
                sk.at("body", "front", x, y, buckle)


# ---------------------------------------------------------------- the NPCs

def banker():
    sk = Skin(False, 11)
    skin, shadow = rgb("E8C4A8"), rgb("D2A68A")
    hair, dark, grey = rgb("3B3532"), rgb("2A2522"), rgb("8C8782")
    navy, navy_dark, lapel = rgb("26324F"), rgb("1B243A"), rgb("1E2840")
    vest, white, tie, gold = rgb("6E2233"), rgb("EEEDEA"), rgb("8E2A3C"), rgb("E3B54A")
    base_skin(sk, skin)
    # Neat side-parted hair, greying at the temples.
    hair_short(sk, hair, dark, rgb("4A4440"), front_rows=1, side_rows=3, back_rows=6, fringe=[(5, 1), (6, 1), (7, 1), (0, 1)])
    sk.face(1, 0, rgb("54504C"))  # the parting
    for face, xs in (("right", (4, 5, 6)), ("left", (1, 2, 3))):
        for x in xs:
            sk.at("head", face, x, 2, grey, raw=False), sk.at("head", face, x, 3, tone(grey, 0.9), raw=False)
    ears(sk, shadow)
    eyes(sk, rgb("3D5A80"), brow=dark)
    sk.face(3, 5, shadow), sk.face(4, 5, tone(shadow, 1.05))
    for x in (2, 3, 4, 5):
        sk.face(x, 6, grey if x in (2, 5) else rgb("5E5853"))  # trimmed moustache
    sk.face(3, 7, rgb("B07A6A")), sk.face(4, 7, rgb("B07A6A"))
    # Gold spectacles on the hat layer (eyes show through the holes).
    for x in (1, 2, 5, 6):
        sk.face(x, 3, gold, overlay=True), sk.face(x, 5, tone(gold, 0.85), overlay=True)
    sk.face(0, 4, gold, overlay=True), sk.face(3, 4, gold, overlay=True), sk.face(4, 4, gold, overlay=True), sk.face(7, 4, gold, overlay=True)
    for face, xs in (("right", range(3, 8)), ("left", range(0, 5))):
        for x in xs:
            sk.at("head", face, x, 4, tone(gold, 0.8), overlay=True)
    # Three-piece suit.
    sk.fill("body", navy, noise=0.03)
    front = ["JLCCCCLJ", "JLCTTCLJ", "JJLTTLJJ", "JJLVVLJJ", "JJJVVJJJ", "JJJGJJJJ", "JJJJJJJJ", "JJJGJJJJ", "JJJJJJJJ",
             "JJJJJJJJ", "JJJJJJJJ", "JJJSSJJJ"]
    colors = {"J": None, "L": lapel, "C": white, "T": tie, "V": vest, "G": gold, "S": navy_dark}
    for y, row in enumerate(front):
        for x, ch in enumerate(row):
            if colors[ch]:
                sk.at("body", "front", x, y, colors[ch], raw=ch in "CGT")
    for x, y in ((1, 6), (2, 6), (3, 6)):
        sk.at("body", "front", x, y, tone(gold, 0.9))  # watch chain
    sk.fill("body", navy_dark, when=lambda p: p.face == "back" and p.x in (3, 4) and p.y >= 9)  # coat vent
    sk.at("body", "front", 1, 2, white, overlay=True), sk.at("body", "front", 1, 3, vest, overlay=True)  # pocket square
    sleeves(sk, navy, 10)
    sk.fill(["rarm", "larm"], white, when=lambda p: p.s is not None and p.y == 9, noise=0.01)
    sk.at("rarm", "front", 1, 9, gold), sk.at("larm", "front", 1, 9, gold)
    hands(sk, skin)
    legs(sk, rgb("2C2E35"))
    sk.fill(["rleg", "lleg"], rgb("32343C"), when=lambda p: p.face == "front" and p.x == 1 and p.y < 10, noise=0.01)  # crease
    shoes(sk, rgb("16171A"), rows=2, toe=rgb("3E424A"))
    return sk


def farmer():
    sk = Skin(True, 22)
    skin, shadow, cheek = rgb("F0C8A0"), rgb("DCAD84"), rgb("E89C8A")
    hair, dark, light = rgb("A5502A"), rgb("7C381C"), rgb("C46C3C")
    straw, straw_dark, ribbon = rgb("E6C877"), rgb("C9A452"), rgb("B8402F")
    cream, denim, denim_dark, brass = rgb("F2E7D0"), rgb("4D70A8"), rgb("3B5786"), rgb("D9A441")
    base_skin(sk, skin)
    hair_short(sk, hair, dark, light, front_rows=2, side_rows=6, back_rows=8, fringe=[(0, 2), (7, 2), (6, 2)])
    eyes(sk, rgb("4F8A3C"), brow=dark)
    sk.face(1, 5, cheek), sk.face(6, 5, cheek)
    sk.face(2, 5, rgb("C98E6A")), sk.face(5, 5, rgb("C98E6A"))  # freckles
    sk.face(3, 5, shadow)
    sk.face(3, 6, rgb("C86B5E")), sk.face(4, 6, rgb("C86B5E")), sk.face(2, 6, rgb("D88A78"))
    # Straw hat with a red ribbon.
    sk.fill("head", lambda p: straw_dark if (p.x + p.y) % 2 else straw, overlay=True, faces=["top"], noise=0.04)
    sk.fill("head", lambda p: ribbon if p.y == 2 else (straw_dark if p.y == 3 or (p.x + p.y) % 3 == 0 else straw),
            overlay=True, when=lambda p: p.s is not None and p.y < 4 and not (p.face == "front" and p.y == 3 and 2 <= p.x <= 5), noise=0.04)
    # A braid down the back.
    for y in range(8):
        sk.at("head", "back", 3, y, dark if y % 2 else hair, raw=False), sk.at("head", "back", 4, y, hair if y % 2 else light, raw=False)
    for y in range(7):
        sk.at("body", "back", 3, y, dark if y % 2 else hair, overlay=True, raw=False)
        sk.at("body", "back", 4, y, hair if y % 2 else light, overlay=True, raw=False)
    sk.at("body", "back", 3, 7, ribbon, overlay=True), sk.at("body", "back", 4, 7, ribbon, overlay=True)
    # Blouse, dungarees and a neckerchief.
    sk.fill("body", cream, noise=0.025)
    sk.fill("body", denim, when=lambda p: p.s is not None and (p.y >= 4 or (p.face in ("front", "back") and p.x in (1, 2, 5, 6))), noise=0.04)
    sk.fill("body", denim_dark, when=lambda p: p.face == "front" and 2 <= p.x <= 5 and p.y in (5, 6))  # bib pocket
    sk.at("body", "front", 1, 4, brass), sk.at("body", "front", 6, 4, brass)
    sk.fill("body", ribbon, when=lambda p: p.s is not None and p.y == 0, noise=0.03)
    sk.at("body", "front", 3, 1, ribbon), sk.at("body", "front", 4, 1, ribbon), sk.at("body", "front", 3, 2, tone(ribbon, 0.85))
    sleeves(sk, cream, 5, cuff=rgb("E0D3B8"))
    legs(sk, denim)
    sk.fill(["rleg", "lleg"], rgb("7393C4"), when=lambda p: p.s is not None and p.y == 9)
    sk.fill(["rleg", "lleg"], denim_dark, when=lambda p: p.face == "front" and p.y in (4, 5) and p.x in (1, 2))
    shoes(sk, rgb("6B4426"), rows=2, toe=rgb("82573A"))
    return sk


def blacksmith():
    sk = Skin(False, 33)
    skin, shadow, shine = rgb("8D5A3B"), rgb("754A30"), rgb("A8744F")
    beard, beard_light = rgb("1E1A18"), rgb("3A3532")
    leather, leather_dark, shirt = rgb("80522F"), rgb("613D23"), rgb("4A4F58")
    brass, lens, glove = rgb("A8834A"), rgb("E8892B"), rgb("4A3222")
    base_skin(sk, skin)
    sk.fill("head", lambda p: shine if (p.x, p.y) in ((2, 5), (3, 5), (3, 4)) else skin, faces=["top"], noise=0.02)
    sk.fill("head", tone(skin, 0.92), faces=["back"], noise=0.02)
    ears(sk, shadow)
    eyes(sk, rgb("2B1A10"), white=(236, 230, 222), brow=beard)
    sk.face(0, 3, beard), sk.face(7, 3, beard)
    sk.face(3, 5, shadow), sk.face(4, 5, tone(shadow, 0.92))
    # Big black beard, with a braid that hangs onto the chest.
    for y in range(5, 8):
        for x in range(8):
            if y == 5 and 2 <= x <= 5:
                continue
            if y == 6 and x in (3, 4):
                sk.face(x, y, rgb("5A2E22"))
                continue
            sk.face(x, y, beard_light if (x * 3 + y) % 5 == 0 else beard, raw=False)
    for face, xs in (("right", range(4, 8)), ("left", range(0, 4))):
        for x in xs:
            for y in range(4, 8):
                if y > 4 or x in (7, 0):
                    sk.at("head", face, x, y, beard, raw=False)
    sk.fill("head", beard, faces=["bottom"], when=lambda p: p.y < 5)
    for y in range(3):
        sk.at("body", "front", 3, y, beard, overlay=True, raw=False), sk.at("body", "front", 4, y, beard_light if y == 1 else beard, overlay=True, raw=False)
    sk.at("body", "front", 3, 3, brass, overlay=True), sk.at("body", "front", 4, 3, brass, overlay=True)
    # Brass goggles pushed up on the forehead.
    sk.fill("head", rgb("3A2A1E"), overlay=True, when=lambda p: p.s is not None and p.y == 2)
    for x, c in ((0, brass), (1, lens), (2, lens), (3, brass), (4, brass), (5, lens), (6, lens), (7, brass)):
        sk.face(x, 2, c, overlay=True)
    sk.face(1, 1, brass, overlay=True), sk.face(2, 1, brass, overlay=True), sk.face(5, 1, brass, overlay=True), sk.face(6, 1, brass, overlay=True)
    sk.face(2, 2, rgb("F4B05A"), overlay=True), sk.face(6, 2, rgb("F4B05A"), overlay=True)
    # Work shirt, a heavy leather apron, gloves and boots.
    sk.fill("body", shirt)
    sk.fill("body", leather, when=lambda p: p.face == "front" and (p.y >= 2 and 1 <= p.x <= 6 or p.x in (1, 6)))
    sk.fill("body", leather_dark, when=lambda p: p.face == "back" and ((p.x == p.y // 2 + 1) or (p.x == 6 - p.y // 2)) and p.y < 8)
    belt(sk, leather_dark, rows=(8,), buckle=brass)
    sk.fill("body", leather, overlay=True, when=lambda p: p.face == "front" and 2 <= p.y and 1 <= p.x <= 6, noise=0.05)
    sk.fill("body", leather_dark, overlay=True, when=lambda p: p.face == "front" and p.y in (6, 7) and 2 <= p.x <= 5)  # pocket
    sk.at("body", "front", 2, 6, rgb("9A6A42"), overlay=True)
    for y in range(3, 12, 2):
        sk.at("body", "front", 1, y, rgb("A87A4E"), overlay=True), sk.at("body", "front", 6, y, rgb("A87A4E"), overlay=True)
    sk.at("body", "front", 5, 9, rgb("2E2A28"), overlay=True), sk.at("body", "front", 2, 4, rgb("2E2A28"), overlay=True)  # soot
    sk.fill(["rleg", "lleg"], leather, overlay=True, when=lambda p: p.face == "front" and p.y < 5, noise=0.05)
    sk.fill(["rleg", "lleg"], leather_dark, overlay=True, when=lambda p: p.face == "front" and p.y == 4)
    sleeves(sk, shirt, 4, cuff=rgb("4C5058"))
    sk.fill(["rarm", "larm"], shine, when=lambda p: p.face == "front" and p.x == 1 and 5 <= p.y <= 7, noise=0.02)
    sk.fill(["rarm", "larm"], glove, when=lambda p: p.face == "bottom" or (p.s is not None and p.y >= 9), noise=0.04)
    sk.fill(["rarm", "larm"], rgb("6B4A32"), when=lambda p: p.s is not None and p.y == 9)
    legs(sk, rgb("3A2E26"))
    shoes(sk, rgb("1F1B18"), rows=3, toe=rgb("6D7278"))
    return sk


def lumberjack():
    sk = Skin(False, 44)
    skin, shadow, nose = rgb("EBC1A1"), rgb("D4A282"), rgb("DE9682")
    beard, beard_dark, beard_light = rgb("B5562A"), rgb("8C3E1C"), rgb("D06E3A")
    green, green_dark, green_light = rgb("2F6B3A"), rgb("23522C"), rgb("3E8A4C")
    red, red_dark, black = rgb("B8312F"), rgb("7E2222"), rgb("1F1A1A")
    base_skin(sk, skin)
    hair_short(sk, beard, beard_dark, beard_light, front_rows=2, side_rows=5, back_rows=7)
    ears(sk, shadow)
    eyes(sk, rgb("4A7FB5"), brow=beard_dark)
    sk.face(3, 5, nose), sk.face(4, 5, nose)
    for y in range(5, 8):
        for x in range(8):
            if y == 5 and 2 <= x <= 5:
                continue
            if y == 6 and x in (3, 4):
                sk.face(x, y, rgb("9C4A3A"))
                continue
            sk.face(x, y, beard_light if (x + y * 2) % 4 == 0 else (beard_dark if (x * y) % 7 == 3 else beard), raw=False)
    for face, xs in (("right", range(4, 8)), ("left", range(0, 4))):
        for x in xs:
            for y in range(4, 8):
                sk.at("head", face, x, y, beard, raw=False)
    sk.fill("head", beard, faces=["bottom"], when=lambda p: p.y < 5)
    # Green knit beanie with a ribbed fold.
    sk.fill("head", lambda p: green_light if (p.x + p.y) % 4 == 0 else green, overlay=True, faces=["top"], noise=0.04)
    sk.fill("head", lambda p: (green_light if p.s % 2 else green) if p.y >= 2 else green, overlay=True,
            when=lambda p: p.s is not None and p.y < 4, noise=0.04)
    sk.fill("head", green_dark, overlay=True, when=lambda p: p.s is not None and p.y == 1)
    # Buffalo-check flannel, suspenders, jeans.
    def plaid(p):
        a = ((p.s if p.s is not None else p.x) // 2) % 2
        b = (p.y // 2) % 2
        return black if a and b else (red_dark if a or b else red)
    sk.fill("body", plaid, noise=0.03)
    sk.fill(["rarm", "larm"], plaid, when=lambda p: p.face == "top" or (p.s is not None and p.y < 7), noise=0.03)
    sk.fill(["rarm", "larm"], rgb("C64A44"), when=lambda p: p.s is not None and p.y == 7)
    sk.fill(["rarm", "larm"], tone(skin, 0.96), when=lambda p: p.face == "front" and p.y == 9 and p.x % 2 == 0, noise=0.01)
    hands(sk, skin)
    strap = rgb("5A3A22")
    sk.fill("body", strap, when=lambda p: p.face in ("front", "back") and p.x in (1, 6) and p.y < 10, noise=0.03)
    sk.at("body", "front", 1, 8, rgb("D9A441")), sk.at("body", "front", 6, 8, rgb("D9A441"))
    belt(sk, rgb("3A2A1E"), rows=(10,))
    legs(sk, rgb("3E5A8A"))
    sk.fill("body", rgb("3E5A8A"), when=lambda p: p.s is not None and p.y == 11)
    sk.fill("rleg", rgb("344D78"), when=lambda p: p.face == "right" and p.x == 2 and p.y < 9)
    sk.fill("lleg", rgb("344D78"), when=lambda p: p.face == "left" and p.x == 1 and p.y < 9)
    shoes(sk, rgb("5C3B22"), rows=3, toe=rgb("8A6440"))
    return sk


def enchanter():
    sk = Skin(True, 55)
    skin, shadow = rgb("F2D9CC"), rgb("DFC0B0")
    hair, hair_dark, hair_light = rgb("E2DFEE"), rgb("B6B0CF"), rgb("FAF8FF")
    robe, robe_dark, robe_light, gold, rune = rgb("4B2A7B"), rgb("34205A"), rgb("6A40A8"), rgb("E0B44A"), rgb("8FE3FF")
    base_skin(sk, skin)
    # Long silver hair framing the face.
    sk.fill("head", lambda p: hair_light if (p.x * 2 + p.y) % 5 == 0 else hair, faces=["top", "back", "right", "left"], noise=0.04)
    sk.fill("head", hair, faces=["front"], when=lambda p: p.y < 2 or (p.x in (0, 7) and p.y < 8), noise=0.04)
    sk.face(1, 2, hair_dark), sk.face(6, 2, hair_dark), sk.face(2, 2, hair)
    sk.fill("head", skin, faces=["right"], when=lambda p: p.x >= 6 and 3 <= p.y <= 5, noise=0.01)
    sk.fill("head", skin, faces=["left"], when=lambda p: p.x <= 1 and 3 <= p.y <= 5, noise=0.01)
    eyes(sk, rgb("9B4DFF"), white=rgb("E6CCFF"), brow=hair_dark)
    sk.face(3, 5, shadow)
    sk.face(3, 6, rgb("C27A8A")), sk.face(4, 6, rgb("C27A8A"))
    # A deep purple hood, gold-edged.
    sk.fill("head", lambda p: robe_light if (p.x + p.y) % 6 == 0 else robe, overlay=True, faces=["top", "back"], noise=0.04)
    sk.fill("head", robe, overlay=True, faces=["right", "left"], when=lambda p: p.y < 7 and not
            ((p.face == "right" and p.x >= 6 and p.y >= 2) or (p.face == "left" and p.x <= 1 and p.y >= 2)), noise=0.04)
    sk.fill("head", lambda p: gold if p.y == 1 or p.x in (0, 7) else robe, overlay=True, faces=["front"],
            when=lambda p: p.y < 2 or (p.x in (0, 7) and p.y < 7), noise=0.02)
    # Robe with a gold collar, an amethyst pendant, a sash and glowing runes on the hem.
    sk.fill("body", robe, noise=0.035)
    sk.fill("body", robe_dark, when=lambda p: p.face == "front" and p.x in (3, 4) and p.y > 3)
    sk.fill("body", gold, when=lambda p: p.face == "front" and p.x in (2, 5) and p.y > 3, noise=0.02)
    sk.fill("body", gold, when=lambda p: p.s is not None and p.y == 0, noise=0.02)
    sk.at("body", "front", 3, 2, rgb("C77DFF")), sk.at("body", "front", 4, 2, rgb("E8C1FF")), sk.at("body", "front", 3, 3, rgb("9B4DFF"))
    sk.at("body", "front", 3, 1, gold), sk.at("body", "front", 4, 1, gold)
    belt(sk, robe_light, rows=(7,), buckle=gold)
    for x, y in ((1, 10), (6, 9), (2, 3), (5, 3)):
        sk.at("body", "back", x, y, rune)
    sleeves(sk, robe, 10)
    sk.fill(["rarm", "larm"], robe, overlay=True, when=lambda p: p.s is not None and 6 <= p.y <= 10, noise=0.04)
    sk.fill(["rarm", "larm"], gold, overlay=True, when=lambda p: p.s is not None and p.y == 10, noise=0.02)
    hands(sk, skin)
    legs(sk, robe)
    sk.fill(["rleg", "lleg"], robe, overlay=True, when=lambda p: p.s is not None and p.y < 9, noise=0.04)
    sk.fill(["rleg", "lleg"], gold, overlay=True, when=lambda p: p.s is not None and p.y == 8, noise=0.02)
    sk.at("rleg", "front", 1, 6, rune, overlay=True), sk.at("lleg", "front", 2, 4, rune, overlay=True)
    shoes(sk, rgb("2A1A3A"), rows=2)
    return sk


def tavern_keeper():
    sk = Skin(False, 66)
    skin, shadow, cheek = rgb("F2C2A2"), rgb("DA9E7E"), rgb("E68F7A")
    hair, hair_dark, hair_light = rgb("6B4A2E"), rgb("543A22"), rgb("8A6440")
    shirt, vest, apron, stain = rgb("EFEAE0"), rgb("6E4426"), rgb("E8DFC8"), rgb("CDB88E")
    base_skin(sk, skin)
    sk.fill("head", lambda p: rgb("FFD9BE") if (p.x, p.y) in ((3, 4), (4, 4), (3, 5)) else skin, faces=["top"], noise=0.015)
    sk.fill("head", hair, faces=["right", "left", "back"], when=lambda p: 2 <= p.y <= 5, noise=0.05)
    ears(sk, shadow)
    eyes(sk, rgb("3F6FA0"), brow=hair_dark)
    sk.face(0, 3, hair), sk.face(7, 3, hair)
    sk.face(3, 4, shadow), sk.face(1, 5, cheek), sk.face(6, 5, cheek)
    # Mutton chops and a grin.
    for x, y in ((0, 4), (0, 5), (0, 6), (1, 6), (7, 4), (7, 5), (7, 6), (6, 6), (0, 7), (7, 7)):
        sk.face(x, y, hair_light if y == 6 else hair, raw=False)
    for x in (2, 3, 4, 5):
        sk.face(x, 5, hair_dark if x in (2, 5) else hair)
    sk.face(2, 6, rgb("9A4E3E")), sk.face(3, 6, rgb("F4F0E6")), sk.face(4, 6, rgb("F4F0E6")), sk.face(5, 6, rgb("9A4E3E"))
    sk.face(3, 7, rgb("D48E78")), sk.face(4, 7, rgb("D48E78"))
    # Shirt, waistcoat, long apron with a towel over one shoulder.
    sk.fill("body", shirt, noise=0.02)
    sk.fill("body", vest, when=lambda p: p.face in ("right", "left", "back") or (p.face == "front" and p.x in (0, 1, 6, 7)), noise=0.04)
    sk.at("body", "front", 1, 3, rgb("D9A441")), sk.at("body", "front", 1, 5, rgb("D9A441"))
    sk.fill("body", rgb("B83A30"), when=lambda p: p.face == "front" and p.x in (3, 4) and p.y in (0, 1))
    sk.fill("body", apron, overlay=True, when=lambda p: p.face == "front" and p.y >= 3 and 1 <= p.x <= 6, noise=0.03)
    sk.fill("body", rgb("CFC4AA"), overlay=True, when=lambda p: p.s is not None and p.y == 6 and p.face != "front")
    for x, y in ((2, 7), (5, 9), (4, 10)):
        sk.at("body", "front", x, y, stain, overlay=True)
    sk.fill(["rleg", "lleg"], apron, overlay=True, when=lambda p: p.face == "front" and p.y < 7, noise=0.03)
    sk.at("rleg", "front", 2, 3, stain, overlay=True)
    towel = lambda p: rgb("F2F2F2") if (p.y + (p.s or p.x)) % 3 else rgb("4E7FB5")
    sk.fill("rarm", towel, overlay=True, when=lambda p: p.face == "top" or (p.s is not None and p.y < 3 and p.face != "left"))
    sk.fill("body", towel, overlay=True, when=lambda p: p.face in ("front", "back") and p.x == 0 and p.y < 4)
    sleeves(sk, shirt, 5, cuff=rgb("DDD6C8"), noise=0.02)
    hands(sk, skin)
    legs(sk, rgb("4A3524"))
    shoes(sk, rgb("2E2219"), rows=2, toe=rgb("4A3A2C"))
    return sk


def builder():
    sk = Skin(True, 77)
    skin, shadow = rgb("D9A07A"), rgb("C08560")
    hair, hair_dark, hair_light = rgb("3B2618"), rgb("2A1A10"), rgb("57392A")
    teal, teal_dark, khaki, khaki_dark = rgb("2F7C80"), rgb("23605F"), rgb("A89466"), rgb("8C7A52")
    bandana, dot, leather = rgb("C23B2E"), rgb("F0E0D0"), rgb("6A4325")
    base_skin(sk, skin)
    hair_short(sk, hair, hair_dark, hair_light, front_rows=2, side_rows=5, back_rows=7, fringe=[(0, 2), (7, 2)])
    ears(sk, shadow)
    eyes(sk, rgb("6B5A2A"), brow=hair_dark)
    sk.face(3, 5, shadow), sk.face(3, 6, rgb("B5675A")), sk.face(4, 6, rgb("B5675A"))
    # Red bandana and a ponytail.
    sk.fill("head", lambda p: dot if (p.x * 3 + p.y) % 7 == 0 else bandana, overlay=True, faces=["top"], noise=0.03)
    sk.fill("head", lambda p: dot if (p.s * 3 + p.y) % 7 == 0 else bandana, overlay=True,
            when=lambda p: p.s is not None and p.y < 2, noise=0.03)
    sk.at("head", "back", 3, 2, bandana, overlay=True), sk.at("head", "back", 4, 2, bandana, overlay=True)
    sk.at("head", "back", 3, 3, tone(bandana, 0.8), overlay=True), sk.at("head", "back", 5, 3, tone(bandana, 0.8), overlay=True)
    for y in range(4, 8):
        sk.at("head", "back", 3, y, hair_light if y % 2 else hair, overlay=True, raw=False)
        sk.at("head", "back", 4, y, hair, overlay=True, raw=False)
    for y in range(3):
        sk.at("body", "back", 3, y, hair, overlay=True, raw=False), sk.at("body", "back", 4, y, hair_light, overlay=True, raw=False)
    # A pencil behind the ear.
    sk.at("head", "right", 3, 3, rgb("E8C23A"), overlay=True), sk.at("head", "right", 4, 3, rgb("E8C23A"), overlay=True)
    sk.at("head", "right", 5, 3, rgb("F2A0A0"), overlay=True)
    # Work shirt, tool belt with a hammer, canvas trousers.
    sk.fill("body", teal, noise=0.035)
    sk.fill("body", teal_dark, when=lambda p: p.face == "front" and ((p.x, p.y) in ((2, 1), (5, 1), (1, 3), (6, 3)) or (p.x == 4 and p.y > 1)))
    sk.at("body", "front", 4, 3, rgb("D9D2C4")), sk.at("body", "front", 4, 6, rgb("D9D2C4"))
    belt(sk, leather, rows=(9, 10), buckle=rgb("C9A24A"))
    sk.fill("body", rgb("8A5A36"), overlay=True, when=lambda p: p.y in (9, 10, 11) and ((p.face == "front" and p.x in (0, 1, 6, 7)) or p.face in ("right", "left")))
    sk.at("body", "front", 0, 11, rgb("5C3A20"), overlay=True), sk.at("body", "front", 7, 11, rgb("5C3A20"), overlay=True)
    for y in range(0, 4):
        sk.at("rleg", "right", 2, y, rgb("8A5A32"), overlay=True)
    sk.at("rleg", "right", 1, 0, rgb("6D7278"), overlay=True), sk.at("rleg", "right", 2, 0, rgb("8D949C"), overlay=True)
    sk.at("rleg", "right", 3, 0, rgb("6D7278"), overlay=True)
    sleeves(sk, teal, 5, cuff=rgb("3B9095"))
    sk.fill(["rarm", "larm"], rgb("B98B5A"), when=lambda p: p.face == "bottom" or (p.s is not None and p.y >= 9), noise=0.04)
    legs(sk, khaki)
    sk.fill(["rleg", "lleg"], khaki_dark, when=lambda p: p.face == "front" and p.y in (5, 6), noise=0.03)
    sk.fill(["rleg", "lleg"], rgb("C7B48A"), when=lambda p: p.face == "front" and p.y == 5 and p.x in (1, 2))
    shoes(sk, rgb("4A3626"), rows=3, toe=rgb("6A5240"))
    return sk


def weaponsmith():
    sk = Skin(True, 88)
    skin, shadow = rgb("F1CFB5"), rgb("DAB091")
    hair, sheen = rgb("1C1C22"), rgb("2E3A52")
    red, red_dark, leather, steel, steel_light, steel_dark = (rgb("8E2026"), rgb("6B171B"), rgb("2A2A30"),
                                                              rgb("8F99A6"), rgb("C5CDD6"), rgb("5E6773"))
    base_skin(sk, skin)
    # Undercut: long on top, shaved at the sides.
    sk.fill("head", lambda p: sheen if (p.x + p.y) % 4 == 0 else hair, faces=["top"], noise=0.04)
    sk.fill("head", hair, faces=["front"], when=lambda p: p.y < 1 or (p.y == 1 and p.x >= 2) or (p.y == 2 and p.x >= 5), noise=0.04)
    sk.fill("head", lambda p: hair if p.y < 2 else rgb("C9A68C"), faces=["right", "left"], when=lambda p: p.y < 5, noise=0.03)
    sk.fill("head", lambda p: hair if p.y < 3 else rgb("C9A68C"), faces=["back"], when=lambda p: p.y < 6, noise=0.03)
    ears(sk, shadow)
    eyes(sk, rgb("7A8796"), brow=hair)
    sk.face(3, 5, shadow)
    sk.face(3, 6, rgb("B87068")), sk.face(4, 6, rgb("B87068"))
    sk.face(6, 4, rgb("D4958A")), sk.face(5, 5, rgb("D4958A")), sk.face(6, 6, rgb("D4958A"))  # scar
    # Red leather jerkin with a crest, a scarf, a steel pauldron.
    sk.fill("body", red, noise=0.035)
    sk.fill("body", red_dark, when=lambda p: p.s is not None and (p.y in (5, 6) and p.face != "front"))
    for x, y in ((2, 3), (5, 3), (3, 4), (4, 4), (3, 5), (4, 5), (2, 6), (5, 6)):
        sk.at("body", "front", x, y, steel_light)  # crossed blades
    belt(sk, leather, rows=(8,), buckle=rgb("B9C0C9"))
    sk.fill("body", rgb("B3272D"), overlay=True, when=lambda p: (p.s is not None and p.y == 0) or (p.face == "back" and p.x in (5, 6) and p.y < 5))
    sk.at("body", "front", 2, 1, rgb("B3272D"), overlay=True)
    sk.fill("rarm", lambda p: steel_light if p.y == 0 or p.face == "top" else (steel_dark if p.y == 3 else steel), overlay=True,
            when=lambda p: p.face == "top" or (p.s is not None and p.y < 4 and p.face != "left"))
    sk.at("rarm", "front", 1, 1, steel_dark, overlay=True)
    sleeves(sk, leather, 10)
    sk.fill(["rarm", "larm"], lambda p: steel_light if p.y == 6 else steel, overlay=True, when=lambda p: p.s is not None and 6 <= p.y <= 8)
    sk.fill(["rarm", "larm"], leather, when=lambda p: p.s is not None and p.y == 10)
    sk.fill(["rarm", "larm"], skin, when=lambda p: p.s is not None and p.y == 11, noise=0.01)
    legs(sk, rgb("34343E"))
    sk.fill(["rleg", "lleg"], lambda p: steel_light if p.y == 4 else steel, overlay=True, when=lambda p: p.face == "front" and 4 <= p.y <= 5)
    shoes(sk, steel_dark, rows=4, toe=steel)
    sk.fill(["rleg", "lleg"], rgb("2A2A30"), when=lambda p: p.s is not None and p.y == 8)
    return sk


def curio_dealer():
    sk = Skin(True, 99)
    skin, shadow = rgb("A9714B"), rgb("8F5D3B")
    hair, hair_light = rgb("241712"), rgb("3D2A20")
    wrap, wrap_dark, gem = rgb("1F6F78"), rgb("15525A"), rgb("E84393")
    coat, coat_dark, coat_light, gold, scarf = rgb("1E6B52"), rgb("154D3B"), rgb("2A8A6B"), rgb("E0B44A"), rgb("6A2C7A")
    base_skin(sk, skin)
    # Dark curls under a teal headwrap with a gem.
    sk.fill("head", lambda p: hair_light if (p.x * p.y + p.x) % 3 == 0 else hair, faces=["top", "back", "right", "left"], noise=0.05)
    sk.fill("head", skin, faces=["right"], when=lambda p: p.x >= 5 and p.y >= 3, noise=0.01)
    sk.fill("head", skin, faces=["left"], when=lambda p: p.x <= 2 and p.y >= 3, noise=0.01)
    sk.fill("head", hair, faces=["front"], when=lambda p: p.y < 2 or (p.x in (0, 7) and p.y < 6), noise=0.05)
    sk.fill("head", lambda p: wrap_dark if (p.s or p.x) % 3 == 0 else wrap, overlay=True,
            when=lambda p: p.face == "top" or (p.s is not None and p.y < 2), noise=0.04)
    sk.face(3, 1, gem, overlay=True), sk.face(4, 1, rgb("FF7AB8"), overlay=True), sk.face(3, 0, gold, overlay=True), sk.face(4, 0, gold, overlay=True)
    for face, xs in (("right", (0, 1, 2)), ("left", (5, 6, 7))):
        for x in xs:
            sk.at("head", face, x, 2 + (x % 2), hair_light, overlay=True, raw=False)  # curls escaping
    for x in range(8):
        sk.at("head", "back", x, 2 + (x % 3 == 0), hair, overlay=True, raw=False)
    ears(sk, shadow)
    eyes(sk, rgb("2A1A12"), brow=hair)
    sk.face(3, 5, shadow)
    sk.face(3, 6, rgb("7A3E36")), sk.face(4, 6, rgb("7A3E36")), sk.face(5, 6, rgb("7A3E36"))
    # A gold monocle on a chain.
    sk.face(1, 3, gold, overlay=True), sk.face(2, 3, gold, overlay=True), sk.face(1, 5, gold, overlay=True), sk.face(2, 5, gold, overlay=True)
    sk.face(0, 4, gold, overlay=True), sk.face(3, 4, gold, overlay=True), sk.face(0, 5, tone(gold, 0.8), overlay=True)
    for y in (6, 7):
        sk.at("head", "right", 7, y, tone(gold, 0.8), overlay=True)
    # Emerald velvet coat with gold buttons, bead necklaces and a purple scarf.
    sk.fill("body", lambda p: coat_light if p.face == "front" and p.x in (1, 6) and p.y % 4 == 1 else coat, noise=0.04)
    sk.fill("body", coat_dark, when=lambda p: p.face == "front" and p.x in (3, 4))
    for y in (3, 5, 7, 9):
        sk.at("body", "front", 2, y, gold), sk.at("body", "front", 5, y, gold)
    sk.fill("body", scarf, when=lambda p: p.s is not None and p.y == 0)
    sk.fill("body", scarf, overlay=True, when=lambda p: p.face == "front" and p.x == 2 and p.y < 6)
    for x, y, c in ((3, 1, "F2B33D"), (4, 2, "5CE1E6"), (5, 1, "F2B33D"), (2, 2, "E84393"), (3, 3, "5CE1E6"), (4, 3, "F2B33D")):
        sk.at("body", "front", x, y, rgb(c), overlay=True)
    sk.fill("body", coat_dark, when=lambda p: p.face == "front" and p.y == 7 and p.x in (0, 1, 6, 7))  # pocket flaps
    sleeves(sk, coat, 10)
    sk.fill(["rarm", "larm"], gold, when=lambda p: p.s is not None and p.y == 9, noise=0.02)
    hands(sk, skin)
    legs(sk, lambda p: rgb("5A2D4E") if (p.s or p.x) % 3 == 0 else rgb("4A2540"))
    shoes(sk, rgb("2A1E1A"), rows=2, toe=gold)
    return sk


def florist():
    sk = Skin(True, 111)
    skin, shadow, cheek, freckle = rgb("F6D7C3"), rgb("E6BCA2"), rgb("F2A5A0"), rgb("D9A588")
    hair, hair_dark, hair_light = rgb("E3B26A"), rgb("C89548"), rgb("F2CC88")
    yellow, sage, sage_dark = rgb("F6E7A1"), rgb("7FA66B"), rgb("5F8550")
    base_skin(sk, skin)
    hair_short(sk, hair, hair_dark, hair_light, front_rows=2, side_rows=6, back_rows=7,
               fringe=[(0, 2), (1, 2), (6, 2), (7, 2), (0, 3), (7, 3)])
    sk.face(2, 1, hair_light), sk.face(5, 1, hair_dark)
    eyes(sk, rgb("3C8C7C"), brow=hair_dark)
    sk.face(1, 5, cheek), sk.face(6, 5, cheek), sk.face(2, 5, freckle), sk.face(5, 5, freckle), sk.face(4, 4, freckle)
    sk.face(3, 5, shadow)
    sk.face(2, 6, rgb("C86E68")), sk.face(3, 6, rgb("D9827A")), sk.face(4, 6, rgb("D9827A")), sk.face(5, 6, rgb("C86E68"))
    # A crown of flowers.
    vine, vine_dark = rgb("4E9A3A"), rgb("3A7A2C")
    blooms = ["FF8AD8", "FFD84A", "FFFFFF", "7FB5FF", "FF6F6F"]
    sk.fill("head", lambda p: vine_dark if p.y == 1 else vine, overlay=True, when=lambda p: p.s is not None and p.y in (0, 1), noise=0.04)
    for i, s in enumerate(range(1, 32, 3)):
        face, x = ("right", s) if s < 8 else ("front", s - 8) if s < 16 else ("left", s - 16) if s < 24 else ("back", s - 24)
        sk.at("head", face, x, 0, rgb(blooms[i % len(blooms)]), overlay=True)
        sk.at("head", face, x, 1, rgb("FFE27A") if blooms[i % len(blooms)] != "FFD84A" else rgb("E89B2E"), overlay=True)
    # Yellow shirt and sage dungarees with flowers poking out of the pocket.
    sk.fill("body", yellow, noise=0.025)
    sk.fill("body", sage, when=lambda p: p.s is not None and (p.y >= 5 or (p.face in ("front", "back") and p.x in (1, 6))), noise=0.04)
    sk.fill("body", sage_dark, when=lambda p: p.face == "front" and 2 <= p.x <= 5 and p.y in (6, 7, 8))
    sk.at("body", "front", 1, 5, rgb("E0C068")), sk.at("body", "front", 6, 5, rgb("E0C068"))
    for x, y, c in ((3, 5, "4E9A3A"), (4, 4, "4E9A3A"), (3, 4, "FF8AD8"), (4, 3, "FFD84A"), (5, 5, "4E9A3A"), (5, 4, "FFFFFF")):
        sk.at("body", "front", x, y, rgb(c), overlay=True)
    sleeves(sk, yellow, 4, cuff=rgb("E8D58A"))
    hands(sk, skin)
    legs(sk, sage)
    sk.fill(["rleg", "lleg"], rgb("9CBF88"), when=lambda p: p.s is not None and p.y == 8)
    sk.at("rleg", "front", 1, 5, rgb("8A6A4A")), sk.at("lleg", "front", 2, 6, rgb("8A6A4A"))  # garden dirt
    shoes(sk, rgb("7A5235"), rows=3, toe=rgb("946A48"))
    return sk


def fisher():
    sk = Skin(False, 131)
    skin, shadow, nose, cheek = rgb("E0B08C"), rgb("C79872"), rgb("D4876E"), rgb("D99578")
    grey, grey_dark, grey_light = rgb("A9AEB6"), rgb("7C828C"), rgb("D0D4DA")
    yellow, yellow_dark, yellow_light = rgb("F0BE22"), rgb("C4921A"), rgb("FFD95A")
    navy, navy_dark, navy_light = rgb("2C3E68"), rgb("22304F"), rgb("4A5F8C")
    rubber, rubber_dark, rubber_light = rgb("2F4B38"), rgb("1F3226"), rgb("46694F")
    base_skin(sk, skin)
    # Grey hair under the hat: a fringe over the forehead, long at the sides and back (the braid starts here).
    sk.fill("head", lambda p: grey_light if (p.x + p.y) % 5 == 0 else grey, faces=["top"], noise=0.05)
    sk.fill("head", grey, faces=["front"], when=lambda p: p.y < 2, noise=0.05)
    for x, y in ((0, 2), (1, 2), (6, 2), (7, 2), (0, 3), (7, 3)):
        sk.face(x, y, grey_dark)
    sk.fill("head", grey, faces=["right", "left"], when=lambda p: p.y < 5 or (p.y < 7 and ((p.face == "right" and p.x < 3) or (p.face == "left" and p.x > 4))), noise=0.05)
    sk.fill("head", grey, faces=["back"], when=lambda p: p.y < 8, noise=0.05)
    sk.fill("head", grey_dark, faces=["back"], when=lambda p: p.x in (3, 4) and p.y >= 6)
    ears(sk, shadow)
    # A weathered face: pale sea-grey eyes, crow's feet, wind-chapped cheeks, a firm mouth.
    eyes(sk, rgb("6F9BAF"), brow=grey_dark)
    sk.face(0, 5, shadow), sk.face(7, 5, shadow), sk.face(1, 5, cheek), sk.face(6, 5, cheek)
    sk.face(3, 5, nose), sk.face(4, 5, nose), sk.face(2, 6, shadow), sk.face(5, 6, shadow)
    sk.face(3, 6, rgb("B9705F")), sk.face(4, 6, rgb("B9705F"))
    # Yellow sou'wester: a high crown, a short brim in front, a long flap down the back, a chin strap.
    sk.fill("head", lambda p: yellow_light if p.x in (3, 4) else yellow, overlay=True, faces=["top"], noise=0.04)
    sk.fill("head", yellow, overlay=True, faces=["front"], when=lambda p: p.y < 2, noise=0.04)
    sk.fill("head", yellow_dark, overlay=True, faces=["front"], when=lambda p: p.y == 2)
    sk.fill("head", yellow, overlay=True, faces=["right", "left"], when=lambda p: p.y < 3 or (p.y < 6 and ((p.face == "right" and p.x < 3) or (p.face == "left" and p.x > 4))), noise=0.04)
    sk.fill("head", yellow_dark, overlay=True, faces=["right", "left"], when=lambda p: (p.y == 3 and ((p.face == "right" and p.x >= 3) or (p.face == "left" and p.x <= 4))) or (p.y == 6 and ((p.face == "right" and p.x < 3) or (p.face == "left" and p.x > 4))))
    sk.fill("head", yellow, overlay=True, faces=["back"], when=lambda p: p.y < 6, noise=0.04)
    sk.fill("head", yellow_dark, overlay=True, faces=["back"], when=lambda p: p.y == 6)
    for face, x in (("right", 4), ("left", 3)):
        for y in range(3, 8):
            sk.at("head", face, x, y, yellow_dark, overlay=True)
    # Oilskin coat: a high collar, a button placket, patch pockets, a long hem, rolled sleeves.
    sk.fill("body", yellow, noise=0.03)
    sk.fill("body", yellow_light, when=lambda p: p.face == "front" and p.y == 0 and p.x not in (3, 4), noise=0.02)
    sk.fill("body", yellow_dark, when=lambda p: p.s is not None and p.y == 1)
    sk.fill("body", yellow_dark, when=lambda p: p.face == "front" and p.x == 3 and p.y > 1)
    for y in (3, 5, 7, 9):
        sk.at("body", "front", 4, y, rgb("5A3A1E"))
    for x0 in (0, 5):
        for x in range(x0, x0 + 3):
            sk.at("body", "front", x, 7, yellow_dark), sk.at("body", "front", x, 9, yellow_dark)
        sk.at("body", "front", x0, 8, yellow_dark), sk.at("body", "front", x0 + 2, 8, yellow_dark)
    sk.at("body", "front", 1, 3, rgb("C9CED6"))  # a hook pinned to the collar
    sk.fill("body", yellow_dark, overlay=True, when=lambda p: p.s is not None and p.y >= 9, noise=0.04)
    sk.fill("body", yellow_dark, when=lambda p: p.face == "back" and p.x in (3, 4) and p.y > 1)
    sleeves(sk, yellow, 6, cuff=yellow_dark)
    sk.fill(["rarm", "larm"], yellow_light, when=lambda p: p.face == "front" and p.y in (1, 3) and p.x == 1, noise=0.02)
    hands(sk, skin, rows=6)
    sk.fill(["rarm", "larm"], shadow, when=lambda p: p.face == "front" and p.y in (8, 10) and p.x % 2 == 0, noise=0.01)
    # The grey braid hangs down her back over the coat, tied off with red twine.
    for y in range(0, 10):
        for x in (3, 4):
            sk.at("body", "back", x, y, grey if (x + y) % 2 else grey_dark, overlay=True)
        sk.at("body", "back", 3 if y % 2 else 4, y, grey_light, overlay=True)
    sk.at("body", "back", 3, 9, rgb("B8312F"), overlay=True), sk.at("body", "back", 4, 9, rgb("B8312F"), overlay=True)
    # Navy trousers rolled to the shin over green rubber boots.
    legs(sk, navy, rows=8)
    sk.fill(["rleg", "lleg"], navy_light, when=lambda p: p.s is not None and p.y == 6)
    sk.fill(["rleg", "lleg"], navy_dark, when=lambda p: p.s is not None and p.y == 7)
    sk.fill("body", navy, when=lambda p: p.s is not None and p.y == 11)
    shoes(sk, rubber, rows=4, sole=rubber_dark, toe=rubber_light)
    sk.fill(["rleg", "lleg"], rubber_light, when=lambda p: p.s is not None and p.y == 8)
    return sk


def slayer_master():
    sk = Skin(False, 207)
    skin, shadow, cheek = rgb("E6C6AA"), rgb("CFA688"), rgb("D9A08A")
    scar = rgb("C97A7A")
    silver, silver_dark, silver_light = rgb("D6DAE2"), rgb("A4AAB8"), rgb("F0F2F6")
    hood, hood_dark = rgb("2E2528"), rgb("1E181A")
    coat, coat_dark, coat_light = rgb("463630"), rgb("2E2420"), rgb("5E4A3E")
    strap, buckle = rgb("5A3E26"), rgb("C4CAD4")
    bone, bone_dark = rgb("EAE0C4"), rgb("BEB296")
    boots, glove = rgb("241C1C"), rgb("1E1A1C")
    base_skin(sk, skin)
    # Silver-white hair: a fringe under the hood and long strands down both sides of the face.
    sk.fill("head", lambda p: silver_light if (p.x + p.y) % 4 == 0 else silver, faces=["top"], noise=0.04)
    sk.fill("head", silver, faces=["front"], when=lambda p: p.y < 3 and (p.y < 2 or p.x in (0, 1, 3, 6, 7)), noise=0.04)
    sk.fill("head", silver, faces=["front"], when=lambda p: p.x in (0, 7) and p.y < 8, noise=0.04)
    sk.fill("head", silver, faces=["right", "left"], when=lambda p: p.y < 8, noise=0.04)
    sk.fill("head", silver_dark, faces=["back"], when=lambda p: p.y < 8, noise=0.05)
    ears(sk, shadow)
    # A hard face: pale amber eyes, one cut across by a scar, a flat mouth.
    eyes(sk, rgb("C9A24A"), brow=silver_dark)
    sk.face(5, 1, silver_dark), sk.face(6, 1, silver_dark)
    sk.face(6, 5, cheek), sk.face(1, 5, cheek)
    sk.face(3, 5, shadow), sk.face(4, 5, shadow)
    for x in (3, 4):
        sk.face(x, 6, rgb("A8645A"))
    for x, y in ((6, 2), (6, 3), (6, 4), (5, 5), (5, 6)):
        sk.face(x, y, scar)
    # Dark leather hood: pulled low over the brow, falling past the ears.
    sk.fill("head", lambda p: hood_dark if (p.x + p.y) % 5 == 0 else hood, overlay=True, faces=["top"], noise=0.04)
    sk.fill("head", hood, overlay=True, faces=["front"], when=lambda p: p.y < 2, noise=0.04)
    sk.fill("head", hood_dark, overlay=True, faces=["front"], when=lambda p: p.y == 1 and p.x in (0, 7))
    sk.fill("head", hood, overlay=True, faces=["right", "left"], when=lambda p: p.y < 8 and ((p.face == "right" and p.x < 6) or (p.face == "left" and p.x > 1)) or p.y < 2, noise=0.04)
    sk.fill("head", hood, overlay=True, faces=["back"], noise=0.04)
    sk.fill("head", hood_dark, overlay=True, faces=["back"], when=lambda p: p.y == 7)
    # Longcoat: dark leather with a high collar, buckled straps, a long skirt.
    sk.fill("body", coat, noise=0.03)
    sk.fill("body", coat_light, when=lambda p: p.s is not None and p.y == 0, noise=0.02)
    sk.fill("body", coat_dark, when=lambda p: p.face == "front" and p.x == 3 and p.y > 1)
    for y in (3, 6, 9):
        sk.fill("body", strap, when=lambda p, y=y: p.face == "front" and p.y == y)
        sk.at("body", "front", 1, y, buckle), sk.at("body", "front", 6, y, buckle)
    sk.fill("body", coat_dark, overlay=True, when=lambda p: p.s is not None and p.y >= 9, noise=0.04)
    sk.fill("body", coat_dark, when=lambda p: p.face == "back" and p.x == 3 and p.y > 1)
    # A bandolier of crossbow bolts, shoulder to hip, over the coat.
    for i in range(10):
        x, y = min(7, round(i * 0.8)), i + 1
        sk.at("body", "front", x, y, strap, overlay=True)
        if i % 2 == 0:
            sk.at("body", "front", min(7, x + 1), y, buckle, overlay=True)  # a bolt head
            sk.at("body", "front", max(0, x - 1), y, rgb("8A3A2A"), overlay=True)  # its red fletching
    # A necklace of bone beads and a fang.
    for x, y in ((2, 1), (3, 2), (4, 2), (5, 1)):
        sk.at("body", "front", x, y, bone, overlay=True)
    sk.at("body", "front", 3, 3, bone, overlay=True), sk.at("body", "front", 4, 3, bone_dark, overlay=True)
    sk.at("body", "front", 3, 4, bone_dark, overlay=True)
    sleeves(sk, coat, 9, cuff=strap)
    sk.at("rarm", "front", 1, 5, buckle), sk.at("larm", "front", 2, 5, buckle)
    sk.fill(["rarm", "larm"], glove, when=lambda p: p.face == "bottom" or (p.s is not None and p.y >= 9), noise=0.02)
    legs(sk, rgb("2A262E"))
    shoes(sk, boots, rows=5, sole=rgb("120E0E"), toe=rgb("3A2E2E"))
    sk.at("rleg", "front", 1, 7, buckle), sk.at("lleg", "front", 2, 7, buckle)
    return sk


def provisioner():
    sk = Skin(False, 122)
    skin, shadow = rgb("D9A57E"), rgb("BF8A63")
    beard, beard_dark, beard_light = rgb("D8D8D8"), rgb("A9A9A9"), rgb("F2F2F2")
    navy, navy_light = rgb("233A5E"), rgb("2E4C78")
    coat, coat_dark, coat_light = rgb("E2B52E"), rgb("B98F1E"), rgb("F2CD5A")
    base_skin(sk, skin)
    hair_short(sk, beard_dark, rgb("8E8E8E"), beard, front_rows=2, side_rows=5, back_rows=7)
    ears(sk, shadow)
    sk.face(1, 4, shadow), sk.face(2, 4, rgb("4E7FB5")), sk.face(5, 4, rgb("4E7FB5")), sk.face(6, 4, shadow)  # squinting
    for x in (1, 2, 5, 6):
        sk.face(x, 3, beard_dark)
    sk.face(0, 3, shadow), sk.face(7, 3, shadow)  # weathered lines
    sk.face(3, 4, shadow), sk.face(3, 5, rgb("CC8E6E")), sk.face(4, 5, rgb("C48868"))
    for y in range(5, 8):
        for x in range(8):
            if y == 5 and 2 <= x <= 5:
                continue
            sk.face(x, y, beard_light if (x + y) % 3 == 0 else beard, raw=False)
    sk.face(3, 6, beard_dark), sk.face(4, 6, beard_dark)
    sk.face(5, 6, rgb("5A3A22")), sk.face(6, 6, rgb("5A3A22")), sk.face(7, 6, rgb("6B4426")), sk.face(7, 5, rgb("6B4426"))  # pipe
    for face, xs in (("right", range(4, 8)), ("left", range(0, 4))):
        for x in xs:
            for y in range(4, 8):
                sk.at("head", face, x, y, beard, raw=False)
    sk.fill("head", beard, faces=["bottom"], when=lambda p: p.y < 5)
    # Navy knit cap.
    sk.fill("head", lambda p: navy_light if (p.x + p.y) % 3 == 0 else navy, overlay=True, faces=["top"], noise=0.04)
    sk.fill("head", lambda p: navy_light if p.y >= 1 and p.s % 2 else navy, overlay=True, when=lambda p: p.s is not None and p.y < 3, noise=0.04)
    # Yellow oilskin coat over a striped shirt; tall rubber boots.
    sk.fill("body", coat, noise=0.035)
    sk.fill("body", lambda p: rgb("F0F0F0") if p.y % 2 == 0 else navy_light, when=lambda p: p.face == "front" and p.x in (3, 4) and p.y < 9)
    sk.fill("body", coat_dark, when=lambda p: p.face == "front" and p.x in (2, 5))
    for y in (2, 5, 8):
        sk.at("body", "front", 1, y, rgb("6B4426")), sk.at("body", "front", 6, y, rgb("6B4426"))  # toggles
    sk.fill("body", coat_light, overlay=True, when=lambda p: p.s is not None and p.y == 0, noise=0.03)
    sk.fill("body", coat, overlay=True, when=lambda p: p.y >= 9 and (p.face != "front" or p.x not in (3, 4)), noise=0.04)
    sleeves(sk, coat, 10)
    sk.fill(["rarm", "larm"], coat_dark, when=lambda p: p.s is not None and p.y == 9)
    hands(sk, skin)
    legs(sk, rgb("2A3550"))
    shoes(sk, rgb("1A1C20"), rows=6, toe=rgb("2E3238"))
    sk.fill(["rleg", "lleg"], rgb("2E3238"), when=lambda p: p.s is not None and p.y == 6)
    sk.fill(["rleg", "lleg"], rgb("3A3F48"), when=lambda p: p.face == "front" and p.x == 1 and 7 <= p.y <= 9)  # shine
    return sk


def mining_master():
    sk = Skin(True, 44)
    skin, shadow, cheek = rgb("C9926A"), rgb("AE7A55"), rgb("C77F6A")
    hair, dark, light = rgb("5B5F66"), rgb("3E4248"), rgb("8A9098")
    helmet, helmet_dark, lamp, glow = rgb("D9A441"), rgb("A87A26"), rgb("FFF2B0"), rgb("FFD45A")
    vest, vest_dark, shirt, crystal = rgb("2C6E6A"), rgb("1E4E4B"), rgb("B9B3A6"), rgb("C9B8FF")
    soot, trousers, boots = rgb("3A332E"), rgb("4A4038"), rgb("2A2420")
    base_skin(sk, skin)
    hair_short(sk, hair, dark, light, front_rows=2, side_rows=6, back_rows=8, fringe=[(0, 3), (7, 3)])
    eyes(sk, rgb("2F6E8A"), brow=dark)
    sk.face(1, 5, cheek), sk.face(6, 5, cheek)
    sk.face(3, 5, shadow)
    sk.face(3, 6, rgb("9E5E4A")), sk.face(4, 6, rgb("9E5E4A"))
    sk.face(5, 4, soot), sk.face(1, 6, soot)  # soot smudges
    # Brass hard hat with a lamp on the front.
    sk.fill("head", lambda p: helmet_dark if (p.x + p.y) % 5 == 0 else helmet, overlay=True, faces=["top"], noise=0.03)
    sk.fill("head", lambda p: helmet_dark if p.y == 2 else helmet, overlay=True,
            when=lambda p: p.s is not None and p.y < 3, noise=0.03)
    for x, c in ((3, glow), (4, glow)):
        sk.face(x, 0, lamp, overlay=True), sk.face(x, 1, c, overlay=True)
    # A grey braid down the back, tied with a teal cord.
    for y in range(8):
        sk.at("head", "back", 3, y, dark if y % 2 else hair, raw=False), sk.at("head", "back", 4, y, hair if y % 2 else light, raw=False)
    for y in range(6):
        sk.at("body", "back", 3, y, dark if y % 2 else hair, overlay=True, raw=False)
        sk.at("body", "back", 4, y, hair if y % 2 else light, overlay=True, raw=False)
    sk.at("body", "back", 3, 6, vest, overlay=True), sk.at("body", "back", 4, 6, vest, overlay=True)
    # Linen shirt under a teal work vest with a Glimmer Crystal pendant, tool belt, rolled sleeves.
    sk.fill("body", shirt, noise=0.03)
    sk.fill("body", vest, when=lambda p: p.s is not None and (p.face != "front" or p.x in (0, 1, 2, 5, 6, 7)), noise=0.04)
    sk.fill("body", vest_dark, when=lambda p: p.face == "front" and p.x in (1, 6) and p.y in (5, 6))  # pockets
    sk.at("body", "front", 3, 2, crystal), sk.at("body", "front", 4, 2, rgb("9C88E0")), sk.at("body", "front", 3, 3, rgb("9C88E0"))
    sk.at("body", "front", 3, 1, rgb("D9A441")), sk.at("body", "front", 4, 1, rgb("D9A441"))
    belt(sk, rgb("5A3A22"), rows=(9,), buckle=rgb("D9A441"))
    sk.fill("body", soot, overlay=True, when=lambda p: p.face == "front" and (p.x, p.y) in ((2, 10), (5, 7)))
    sleeves(sk, shirt, 3, cuff=rgb("A39C8E"))
    sk.fill(["rarm", "larm"], rgb("6E4A30"), when=lambda p: p.face == "bottom" or (p.s is not None and p.y >= 10), noise=0.04)  # gloves
    legs(sk, trousers)
    sk.fill(["rleg", "lleg"], rgb("5C5046"), when=lambda p: p.face == "front" and p.y in (5, 6))  # knee patches
    shoes(sk, boots, rows=3, toe=rgb("7A7F86"))
    return sk


NPCS = {
    "banker": ("Cassius Vellum", banker), "farmer": ("Mabel Thornfield", farmer), "blacksmith": ("Brannoc Ashforge", blacksmith),
    "lumberjack": ("Oswin Fernhollow", lumberjack), "enchanter": ("Seraphine Vael", enchanter),
    "tavern_keeper": ("Hollis Barrowmead", tavern_keeper), "builder": ("Petra Quarrystone", builder),
    "weaponsmith": ("Kestrel Vane", weaponsmith), "curio_dealer": ("Odessa Quill", curio_dealer),
    "florist": ("Pip Bramblewick", florist), "fisher": ("Marina Saltwick", fisher), "slayer_master": ("Morwen Ashgrave", slayer_master), "provisioner": ("Dorrin Saltmarsh", provisioner),
    "mining_master": ("Tamsin Deepholt", mining_master),
}


# ---------------------------------------------------------------- preview

def flat_view(sk, back=False):
    """The skin flattened like a paper doll, seen from the front or the back, overlays on top."""
    view = Image.new("RGBA", (16, 32), (0, 0, 0, 0))
    aw = 3 if sk.slim else 4
    face = "back" if back else "front"
    first_arm, second_arm = ("larm", "rarm") if back else ("rarm", "larm")
    first_leg, second_leg = ("lleg", "rleg") if back else ("rleg", "lleg")
    spots = [("head", 4, 0), ("body", 4, 8), (first_arm, 4 - aw, 8), (second_arm, 12, 8), (first_leg, 4, 20), (second_leg, 8, 20)]
    for layer in (sk.base, sk.over):
        for part, x, y in spots:
            u, v, w, h = layer[part][face]
            view.alpha_composite(sk.img.crop((u, v, u + w, v + h)), (x, y))
    return view


def contact_sheet(skins, path):
    scale, cell_w, cell_h, cols = 6, 16 * 6 * 2 + 30, 32 * 6 + 46, 4
    rows = (len(skins) + cols - 1) // cols
    sheet = Image.new("RGBA", (cols * cell_w + 20, rows * cell_h + 20), (18, 21, 30, 255))
    draw = ImageDraw.Draw(sheet)
    for i, (npc_id, (name, sk)) in enumerate(skins.items()):
        cx, cy = 10 + (i % cols) * cell_w, 10 + (i // cols) * cell_h
        draw.rounded_rectangle((cx + 4, cy + 4, cx + cell_w - 4, cy + cell_h - 4), radius=6, fill=(26, 31, 42, 255), outline=(41, 48, 62, 255))
        for j, back in enumerate((False, True)):
            view = flat_view(sk, back).resize((16 * scale, 32 * scale), Image.NEAREST)
            sheet.alpha_composite(view, (cx + 12 + j * (16 * scale + 6), cy + 12))
        draw.text((cx + 12, cy + cell_h - 30), f"{name}  ({npc_id})", fill=(233, 237, 244, 255))
    sheet.save(path)


def main():
    skins = {}
    for npc_id, (name, paint) in NPCS.items():
        sk = paint()
        sk.save(OUT / f"{npc_id}.png")
        skins[npc_id] = (name, sk)
    print(f"Wrote {len(skins)} skins to {OUT}")
    if len(sys.argv) > 1:
        contact_sheet(skins, Path(sys.argv[1]))
        print(f"Preview: {sys.argv[1]}")


if __name__ == "__main__":
    main()
