#!/usr/bin/env python3
"""Draws the Ender Wyrm's model textures (the final boss, docs/design/end_game.md) and writes its model definition.

    python tools/art/generate_ender_wyrm.py [--preview DIR] [--no-write]

A long serpentine dragon: a horned head with a glowing void maw, then SEGMENTS body segments (each its own model part;
the client places them along the head's recent path, see EnderWyrmModel), small wings on the first three, a tail fin.
Dark obsidian-purple scales with glowing violet void seams. Outputs:

    combat/src/main/resources/assets/skyrpg_combat/textures/entity/mob/ender_wyrm.png
    ... ender_wyrm_glow.png    (steady glow: eyes, maw)
    ... ender_wyrm_pulse.png   (seams, crest, fin edges, wing veins; drawn fullbright with a pulsing brightness)
    ... the spawn egg, wyrm_scale.png and void_heart.png item textures and their models
    combat/src/client/java/com/skyrpg/combat/client/mob/EnderWyrmLayout.java

POSE CONVENTION (mirrored in EnderWyrmModel.place): a part at world offset (dx, dy, dz) from the entity's feet, heading
yaw (Minecraft degrees) and pitch (positive = looking down) is placed at model x = -dx*16, y = 24 - dy*16, z = dz*16
with yRot = yaw - pi and xRot = pitch. The head sits 1.5 blocks above the feet, so its model y is 0.
"""
import json
import math
import random
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import cube_models as cm  # noqa: E402
from cube_models import _h, hexc, mix, mul, pick, ramp, vnoise  # noqa: E402
from PIL import Image  # noqa: E402

ROOT = Path(__file__).resolve().parents[2]
TEX_DIR = ROOT / "combat/src/main/resources/assets/skyrpg_combat/textures/entity/mob"
ITEM_DIR = ROOT / "combat/src/main/resources/assets/skyrpg_combat"
JAVA_OUT = ROOT / "combat/src/client/java/com/skyrpg/combat/client/mob/EnderWyrmLayout.java"
NAME = "ender_wyrm"

SEGMENTS = 12
HEAD_TO_SEG0 = 2.1
HEAD_SCALE = 1.5     # the head part is drawn 1.5x (EnderWyrmModel.HEAD_SCALE)
SPACING = 1.25       # blocks between segment centres
WINGED = (1, 2, 3)   # segments with wings
HEAD_HEIGHT = 1.5    # blocks above the entity's feet

W = [18, 18, 17, 16, 15, 13, 12, 10, 9, 8, 6, 5]
H = [round(w * 0.85) for w in W]
D = [24] * 8 + [22, 22, 20, 20]
SPIKE = [9, 9, 8, 8, 7, 7, 6, 6, 5, 5, 4, 4]
WING_L = {1: 26, 2: 22, 3: 18}

# ------------------------------------------------------------------------------------------------ cube table
M = cm.Model(256)

# --- head (pivot = the head's centre; the front is -z)
M.part("head", None, (0, 0, 0))
M.cube("head", "skull", "scale", (-9, -7, -14), (18, 12, 20), deco="scales")
M.cube("head", "snout", "scale", (-6, -3, -30), (12, 7, 16), deco="scales")
M.cube("head", "nostril", "scale", (-4, -4.5, -30.5), (8, 2, 6), deco="scales")
M.pair("head", "brow", "scale", (-10, -9, -14), (8, 3, 12), deco="scales")
M.pair("head", "eye", "eye", (-9.5, -3, -13), (4, 3, 5), glow="glow")
M.pair("head", "frill", "scale", (-13, -4, -2), (4, 10, 10), deco="frill")
M.cube("head", "crest", "scale", (-2, -11, -12), (4, 4, 16), deco="crest")
M.cube("head", "crest2", "scale", (-1.5, -15, -5), (3, 5, 6), deco="crest")
M.cube("head", "crest3", "scale", (-1.5, -13, 3), (3, 4, 6), deco="crest")
M.pair("head", "fang_a", "bone", (-5.5, 4, -29), (2, 4, 2))
M.pair("head", "fang_b", "bone", (-5.5, 4, -21), (2, 3, 2))
M.sided("horn", "head", (-6, -6, 2), (0.5, -0.35, 0), [
    ("horn", "bone", (-2.5, -2.5, 0), (5, 5, 16), dict(deco="horn")),
])
M.sided("horn2", "{s}_horn", (0, 0, 16), (0.35, -0.25, 0), [
    ("horn2", "bone", (-1.5, -1.5, 0), (3, 3, 14), dict(deco="horn")),
])
M.part("jaw", "head", (0, 5, -2))
M.cube("jaw", "jawbone", "scale", (-6.5, 0, -27), (13, 3, 26), deco="scales")
M.cube("jaw", "tongue", "maw", (-5, -1, -26), (10, 1, 22), glow="glow")
M.pair("jaw", "jfang_a", "bone", (-6, -3, -26), (2, 3, 2))
M.pair("jaw", "jfang_b", "bone", (-6, -2, -16), (2, 2, 2))
M.cube("jaw", "chin", "scale", (-3, 3, -26), (6, 2, 8), deco="scales")

# --- body segments (pivot = the segment's centre; rest pose is a straight line behind the head)
for i in range(SEGMENTS):
    M.part(f"seg{i}", None, (0, 0, (HEAD_TO_SEG0 + SPACING * i) * 16))
    w, h, d = W[i], H[i], D[i]
    M.cube(f"seg{i}", f"body{i}", "scale", (-w / 2, -h / 2, -d / 2), (w, h, d), deco="body")
    s = SPIKE[i]
    M.cube(f"seg{i}", f"spike{i}", "scale", (-1.5, -h / 2 - s, -4), (3, s + 1, 8), deco="spike")
    if i in WINGED:
        L = WING_L[i]
        M.sided(f"wing{i}", f"seg{i}", (-w / 2 + 1, -h / 2 + 3, 0), (0, 0, 0.3), [
            (f"bone{i}", "bone", (-L, -1.5, -1.5), (L, 3, 3), dict(deco="horn")),
        ])
        M.sided(f"web{i}", "{s}_" + f"wing{i}", (0, 0, 0), (0, 0, 0), [
            (f"web{i}", "membrane", (-(L - 1), -0.5, 1.5), (L - 1, 1, 12), dict(deco="web")),
        ])
last = SEGMENTS - 1
M.part("fin", f"seg{last}", (0, 0, D[last] / 2))
M.cube("fin", "fin_h", "scale", (-12, -0.5, 0), (24, 1, 14), deco="fin")
M.cube("fin", "fin_v", "scale", (-0.5, -9, 0), (1, 18, 12), deco="fin")
M.cube("fin", "fin_tip", "scale", (-2, -2, 13), (4, 4, 8), deco="crest")


# ------------------------------------------------------------------------------------------------ painting
OBS = ramp("0a0714", "120c20", "1b1230", "281a44", "382459", "4d3376")
BONE = ramp("120d1c", "1e1630", "30244a", "4c3b72", "7a64a6")
WEB = ramp("0e0918", "170f28", "21163a", "2d1f4d")
VOID = (176, 78, 255)
VOID_HI = (232, 176, 255)
SIDES = ("left", "front", "right", "back")
FACE_LIGHT = {"top": 1.12, "bottom": 0.8, "left": 0.95, "front": 1.0, "right": 0.95, "back": 0.88}


def base_pixel(mat, X, Y, px, py, fw, fh, face, seed):
    n1 = vnoise(X, Y, 3.0, seed)
    n3 = _h(X, Y, seed + 3)
    if mat == "scale":
        if face == "bottom":
            band = (py // 3) % 2
            return mul(pick(BONE, 0.5 + 0.2 * band + 0.2 * (n1 - 0.5)), 0.9)
        row = py // 3
        sx = (px + (row % 2) * 2) % 4
        sy = py % 3
        t = 0.38 + 0.3 * (n1 - 0.5) + 0.1 * (n3 - 0.5)
        if sx == 0 or sy == 2:
            t -= 0.25
        elif sx in (1, 2) and sy == 0:
            t += 0.2
        return pick(OBS, t + (0.1 if face == "top" else 0))
    if mat == "bone":
        k = py / max(1, fh - 1) if face in SIDES else 0.5
        col = pick(BONE, 0.25 + 0.5 * n1 + 0.2 * (1 - k) + 0.1 * (n3 - 0.5))
        if (px + py // 4) % 5 == 0:
            col = mul(col, 0.8)
        return col
    if mat == "membrane":
        return pick(WEB, 0.3 + 0.6 * n1 + 0.2 * (n3 - 0.5))
    if mat == "eye":
        return (30, 10, 50)
    if mat == "maw":
        return (50, 16, 90)
    return (255, 0, 255)


def crack(cv, rect, seed, n=2, layer="pulse", strength=1.0):
    fx, fy, fw, fh = rect
    rng = random.Random(seed)
    for _ in range(n):
        x = rng.randrange(fw)
        y = rng.randrange(fh)
        dx = rng.choice((-1, 1))
        for _s in range(rng.randint(5, 11)):
            X, Y = fx + x, fy + y
            col = mix(VOID, VOID_HI, rng.random() * 0.5)
            cv.put(X, Y, mix(cv.get(X, Y), (70, 24, 120), 0.8))
            cv.glow(X, Y, mul(col, (0.55 + 0.45 * rng.random()) * strength), layer)
            for ax, ay in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                if rng.random() < 0.25 and 0 <= x + ax < fw and 0 <= y + ay < fh:
                    cv.put(fx + x + ax, fy + y + ay, mix(cv.get(fx + x + ax, fy + y + ay), (60, 20, 100), 0.5))
            if rng.random() < 0.65:
                x += dx
            else:
                y += rng.choice((-1, 1)) if rng.random() < 0.7 else 0
            x = max(0, min(fw - 1, x))
            y = max(0, min(fh - 1, y))


def deco_scales(cv, c, rects, seed):
    for face in ("left", "right", "top", "front"):
        if rects[face][2] * rects[face][3] > 40:
            crack(cv, rects[face], seed + hash(face) % 97, n=1 + (seed % 2))


def deco_body(cv, c, rects, seed):
    """Void seams: a glowing ring at each segment end (the joint), glowing cracks on the flanks and a dorsal line."""
    for face in ("left", "right"):
        fx, fy, fw, fh = rects[face]
        for py in range(fh):
            for px in (0, fw - 1):
                n = 0.6 + 0.4 * _h(px, py, seed)
                cv.put(fx + px, fy + py, mix(cv.get(fx + px, fy + py), (70, 24, 120), 0.7))
                cv.glow(fx + px, fy + py, mul(VOID, n * 0.8), "pulse")
        crack(cv, rects[face], seed + (1 if face == "left" else 2), n=2)
    fx, fy, fw, fh = rects["top"]
    for py in range(fh):
        for px in (fw // 2 - 1, fw // 2):
            if (py // 2) % 3 != 2:
                cv.put(fx + px, fy + py, (86, 30, 150))
                cv.glow(fx + px, fy + py, mul(mix(VOID, VOID_HI, 0.3), 0.85), "pulse")
    for face in ("front", "back"):
        fx, fy, fw, fh = rects[face]
        for px in range(fw):
            for py in range(fh):
                if px in (0, fw - 1) or py in (0, fh - 1):
                    cv.glow(fx + px, fy + py, mul(VOID, 0.5), "pulse")
    fx, fy, fw, fh = rects["bottom"]
    for py in range(fh):
        if py % 3 == 0:
            for px in range(2, fw - 2):
                cv.glow(fx + px, fy + py, mul(VOID, 0.22), "pulse")


def deco_spike(cv, c, rects, seed):
    for face in SIDES:
        fx, fy, fw, fh = rects[face]
        for py in range(fh):
            k = 1 - py / max(1, fh - 1)
            for px in range(fw):
                if k > 0.5 and _h(px, py, seed) < 0.3 + 0.6 * (k - 0.5):
                    cv.put(fx + px, fy + py, (76, 30, 130))
                    cv.glow(fx + px, fy + py, mul(mix(VOID, VOID_HI, k - 0.5), 0.45 + 0.5 * k), "pulse")
    fx, fy, fw, fh = rects["top"]
    for py in range(fh):
        for px in range(fw):
            cv.put(fx + px, fy + py, (110, 44, 190))
            cv.glow(fx + px, fy + py, VOID_HI, "pulse")


def deco_crest(cv, c, rects, seed):
    deco_spike(cv, c, rects, seed)


def deco_frill(cv, c, rects, seed):
    for face in ("left", "right", "front", "back"):
        fx, fy, fw, fh = rects[face]
        for py in range(fh):
            for px in range(fw):
                if px % 2 == 0 and py % 3 == 1:
                    cv.glow(fx + px, fy + py, mul(VOID, 0.55), "pulse")
                    cv.put(fx + px, fy + py, (70, 26, 120))


def deco_horn(cv, c, rects, seed):
    for face in SIDES + ("top",):
        fx, fy, fw, fh = rects[face]
        if face == "top" or True:
            for py in range(fh):
                for px in range(fw):
                    if (px + py) % 6 == 0 and _h(px, py, seed) < 0.5:
                        cv.glow(fx + px, fy + py, mul(VOID, 0.35), "pulse")
    fx, fy, fw, fh = rects["back"]
    for py in range(fh):
        for px in range(fw):
            cv.put(fx + px, fy + py, (150, 100, 220))
            cv.glow(fx + px, fy + py, mul(VOID_HI, 0.7), "pulse")


def deco_web(cv, c, rects, seed):
    for face in ("top", "bottom"):
        fx, fy, fw, fh = rects[face]
        for py in range(fh):
            for px in range(fw):
                if px % 4 == 1 and (py + px // 4) % 2 == 0 or py == fh - 1 or px == 0:
                    cv.put(fx + px, fy + py, (80, 30, 140))
                    cv.glow(fx + px, fy + py, mul(VOID, 0.6 + 0.3 * _h(px, py, seed)), "pulse")


def deco_fin(cv, c, rects, seed):
    for face in ("top", "bottom", "left", "right"):
        fx, fy, fw, fh = rects[face]
        for py in range(fh):
            for px in range(fw):
                edge = px in (0, fw - 1) or py in (0, fh - 1)
                if edge or (px + py) % 5 == 0:
                    cv.put(fx + px, fy + py, (90, 34, 160))
                    cv.glow(fx + px, fy + py, mul(VOID, 0.55 if not edge else 0.9), "pulse")


DECOS = dict(scales=deco_scales, body=deco_body, spike=deco_spike, crest=deco_crest, frill=deco_frill, horn=deco_horn,
             web=deco_web, fin=deco_fin)


def glow_all(cv, c, rects, seed):
    for face, (fx, fy, fw, fh) in rects.items():
        for py in range(fh):
            for px in range(fw):
                n = vnoise(fx + px, fy + py, 1.5, seed)
                if c["mat"] == "eye":
                    cx, cy = (fw - 1) / 2, (fh - 1) / 2
                    d = max(abs(px - cx) / max(1, fw / 2), abs(py - cy) / max(1, fh / 2))
                    col = mix((200, 120, 255), (255, 240, 255), max(0.0, 1 - d))
                    cv.glow(fx + px, fy + py, col)
                    cv.put(fx + px, fy + py, (60, 20, 100))
                else:  # the maw: bright violet fire, whiter in the middle
                    col = mix((130, 40, 230), (235, 170, 255), 0.35 + 0.5 * n)
                    cv.glow(fx + px, fy + py, col)


def paint(model):
    cv = cm.Canvas(model.width, model.height, layers=("glow", "pulse"))
    for c in model.owners():
        rects = cm.face_rects(c)
        s = 1 + sum(ord(ch) for ch in c["id"])
        for face, (fx, fy, fw, fh) in rects.items():
            for py in range(fh):
                for px in range(fw):
                    col = base_pixel(c["mat"], fx + px, fy + py, px, py, fw, fh, face, s)
                    k = FACE_LIGHT[face]
                    if face in SIDES and (py == 0):
                        k *= 1.08
                    cv.put(fx + px, fy + py, mul(col, k))
        if c.get("deco"):
            DECOS[c["deco"]](cv, c, rects, s)
        if c.get("glow"):
            glow_all(cv, c, rects, s)
    return cv


# ------------------------------------------------------------------------------------------------ pose (mirrors EnderWyrmModel)
def snake_path(t=0.0, n=SEGMENTS, amp=2.2, wave=0.9, dive=0.0):
    """Head and segment world offsets (dx, dy, dz, yaw_deg, pitch_deg) for a head flying along +z with an S-curve body."""
    pts = []
    total = [0.0] + [HEAD_TO_SEG0 + SPACING * i for i in range(n)]
    for dist in total:
        z = -dist
        x = amp * math.sin(dist * wave + t) * min(1.0, dist / 4.0)
        y = HEAD_HEIGHT + 0.7 * math.sin(dist * 0.6 + t * 0.7) * min(1.0, dist / 4.0) - dive * dist * 0.35
        pts.append((x, y, z))
    out = []
    for i, p in enumerate(pts):
        a = pts[i - 1] if i > 0 else (p[0], p[1], p[2] + 1.0)
        b = pts[i + 1] if i + 1 < len(pts) else p
        if i == 0:
            hx, hy, hz = a[0] - p[0], a[1] - p[1], a[2] - p[2]
            hx, hy, hz = 0, -dive * 0.3, 1
        else:
            hx, hy, hz = a[0] - b[0], a[1] - b[1], a[2] - b[2]
        yaw = math.degrees(math.atan2(-hx, hz))
        pitch = -math.degrees(math.atan2(hy, math.hypot(hx, hz)))
        out.append((p[0], p[1] - 0.0, p[2], yaw, pitch))
    return out


def pose(path=None, flap=0.0, jaw=0.0, **kw):
    path = path or snake_path(**kw)
    rest = {p["name"]: p["pivot"] for p in M.parts}
    pz = {}

    def place(name, p):
        dx, dy, dz, yaw, pitch = p
        mx, my, mz = -dx * 16, 24 - dy * 16, dz * 16
        rx, ry, rz = rest[name]
        pz[name] = dict(x=mx - rx, y=my - ry, z=mz - rz, rx=math.radians(pitch), ry=math.radians(yaw) - math.pi, **({'s': HEAD_SCALE} if name == 'head' else {}))

    place("head", path[0])
    for i in range(SEGMENTS):
        place(f"seg{i}", path[1 + i])
    pz["jaw"] = dict(rx=jaw)
    for i in WINGED:
        for s, sign in (("r", 1), ("l", -1)):
            pz[f"{s}_wing{i}"] = dict(rz=sign * (flap + 0.1 * i))
    return pz


# ------------------------------------------------------------------------------------------------ items
def write_items():
    from cube_model import write_spawn_egg
    import generate_combat as gc
    gi = gc.gi
    shell = ["1a0f2e", "2e1b52", "4a2d82", "7a4cc4", "c9a0ff"]
    spots = ["2a0a3a", "5a1a8e", "a040e0", "d880ff", "fbe8ff"]
    write_spawn_egg(ROOT, NAME, shell, spots)
    # item textures drawn by hand at 16x16
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    scale = [(7, 1), (8, 1)] + [(x, 2) for x in range(6, 10)] + [(x, 3) for x in range(5, 11)] + [(x, 4) for x in range(4, 12)] \
        + [(x, 5) for x in range(3, 13)] + [(x, 6) for x in range(3, 13)] + [(x, 7) for x in range(3, 13)] \
        + [(x, 8) for x in range(3, 13)] + [(x, 9) for x in range(4, 12)] + [(x, 10) for x in range(4, 12)] \
        + [(x, 11) for x in range(5, 11)] + [(x, 12) for x in range(6, 10)] + [(7, 13), (8, 13)]
    for x, y in scale:
        t = (x + y * 0.6) / 20
        col = pick(OBS, 0.3 + 0.4 * (1 - abs(x - 7.5) / 6) + 0.15 * _h(x, y, 5))
        img.putpixel((x, y), tuple(int(v) for v in col) + (255,))
    outline = (8, 4, 16, 255)
    for x, y in scale:
        for ax, ay in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            if (x + ax, y + ay) not in scale and 0 <= x + ax < 16 and 0 <= y + ay < 16:
                img.putpixel((x + ax, y + ay), outline)
    for x, y in [(7, 3), (7, 4), (8, 5), (8, 6), (7, 7), (6, 8), (6, 9), (7, 10), (8, 11), (5, 5), (4, 6), (10, 7), (11, 8), (9, 9)]:
        img.putpixel((x, y), (186, 96, 255, 255))
    for x, y in [(7, 5), (8, 7), (7, 9), (9, 8)]:
        img.putpixel((x, y), (240, 200, 255, 255))
    img.save(ITEM_DIR / "textures/item/wyrm_scale.png")
    # the void heart: a violet crystal heart with a dark core
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    rows = {2: [(3, 6), (9, 12)], 3: [(2, 7), (8, 13)], 4: [(2, 13)], 5: [(2, 13)], 6: [(2, 13)], 7: [(3, 12)], 8: [(4, 11)],
            9: [(5, 10)], 10: [(6, 9)], 11: [(7, 8)], 12: [(7, 8)]}
    cells = set()
    for y, spans in rows.items():
        for a, b in spans:
            for x in range(a, b + 1):
                cells.add((x, y))
    for x, y in cells:
        d = math.hypot(x - 7.5, y - 6.0) / 7.0
        col = mix((230, 150, 255), (90, 20, 170), min(1, d * 1.1))
        img.putpixel((x, y), tuple(int(v) for v in col) + (255,))
    for x, y in cells:
        for ax, ay in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            if (x + ax, y + ay) not in cells and 0 <= x + ax < 16 and 0 <= y + ay < 16:
                img.putpixel((x + ax, y + ay), (22, 6, 40, 255))
    for x, y in [(6, 5), (7, 5), (7, 6), (8, 6), (8, 7), (7, 7), (6, 6)]:
        img.putpixel((x, y), (14, 4, 26, 255))
    for x, y in [(4, 4), (5, 3), (11, 4)]:
        img.putpixel((x, y), (255, 240, 255, 255))
    img.save(ITEM_DIR / "textures/item/void_heart.png")
    for item in ("wyrm_scale", "void_heart"):
        for path, obj in ((ITEM_DIR / "models/item" / f"{item}.json",
                           {"parent": "minecraft:item/generated", "textures": {"layer0": f"skyrpg_combat:item/{item}"}}),
                          (ITEM_DIR / "items" / f"{item}.json",
                           {"model": {"type": "minecraft:model", "model": f"skyrpg_combat:item/{item}"}})):
            path.write_text(json.dumps(obj, indent=2) + "\n", encoding="utf-8", newline="\n")


# ------------------------------------------------------------------------------------------------ the Void Sigil item
def write_sigil():
    """The Void Sigil (summons the Wyrm in the End): an obsidian disc ringed in violet light with a glowing slit eye."""
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    cx, cy = 7.5, 7.5
    outline = (14, 6, 28, 255)
    for y in range(16):
        for x in range(16):
            d = math.hypot(x - cx, y - cy)
            if d <= 6.6:
                if d >= 5.4:
                    col = mix((168, 96, 240), (96, 40, 170), (y / 15.0)) if d < 6.2 else (40, 18, 74)
                else:
                    t = min(1.0, d / 5.4)
                    col = mix((46, 24, 84), (14, 6, 30), t)
                    col = mix(col, (70, 38, 120), 0.35 * _h(x, y, 11))
                img.putpixel((x, y), tuple(int(v) for v in col) + (255,))
    for y in range(16):
        for x in range(16):
            if img.getpixel((x, y))[3] == 0:
                for ax, ay in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                    nx, ny = x + ax, y + ay
                    if 0 <= nx < 16 and 0 <= ny < 16 and img.getpixel((nx, ny))[3] and math.hypot(nx - cx, ny - cy) > 5:
                        img.putpixel((x, y), outline)
                        break
    # the slit eye: a vertical almond, bright core
    eye = {3: [7, 8], 4: [7, 8], 5: [6, 7, 8, 9], 6: [6, 7, 8, 9], 7: [6, 7, 8, 9], 8: [6, 7, 8, 9], 9: [6, 7, 8, 9], 10: [7, 8], 11: [7, 8]}
    for y, xs in eye.items():
        for x in xs:
            img.putpixel((x, y), (214, 130, 255, 255) if y not in (3, 11) else (150, 70, 220, 255))
    for y in range(5, 11):
        img.putpixel((7, y), (12, 4, 24, 255))
        img.putpixel((8, y), (12, 4, 24, 255))
    img.putpixel((6, 6), (255, 240, 255, 255))
    for x, y in ((2, 5), (13, 5), (4, 12), (11, 12), (7, 2), (8, 13)):
        img.putpixel((x, y), (240, 214, 255, 255))
    img.save(ITEM_DIR / "textures/item/void_sigil.png")


# ------------------------------------------------------------------------------------------------ main
def build(write=True):
    rows_used = M.pack(max_h=512)
    print(f"packed {len(M.cubes)} cubes, {len(M.owners())} unique UV regions, {rows_used} rows, texture {M.width}x{M.height}")
    cv = paint(M)
    if write:
        TEX_DIR.mkdir(parents=True, exist_ok=True)
        cv.img.save(TEX_DIR / f"{NAME}.png")
        cv.layers["glow"].save(TEX_DIR / f"{NAME}_glow.png")
        cv.layers["pulse"].save(TEX_DIR / f"{NAME}_pulse.png")
        extra = (f"\n    /** How many body segments the Wyrm has (seg0 ... seg{SEGMENTS - 1}). */\n    static final int SEGMENTS = {SEGMENTS};\n"
                 f"\n    /** Blocks from the head's centre to the first segment's centre. */\n    static final float HEAD_TO_SEG0 = {HEAD_TO_SEG0}f;\n"
                 f"\n    /** Blocks between the centres of neighbouring segments. */\n    static final float SPACING = {SPACING}f;\n"
                 f"\n    /** Blocks above the entity's feet that the head's centre sits. */\n    static final float HEAD_HEIGHT = {HEAD_HEIGHT}f;\n"
                 f"\n    /** The segments that carry wings (parts r_wing<i> / l_wing<i>, each with a web child). */\n"
                 f"    static final int[] WINGED = {{{', '.join(str(i) for i in WINGED)}}};\n")
        JAVA_OUT.write_text(M.java("com.skyrpg.combat.client.mob", "EnderWyrmLayout", "generate_ender_wyrm.py",
                                   "The Ender Wyrm's cubes and their UVs.", extra), encoding="utf-8", newline="\n")
        write_items()
        print("wrote textures,", JAVA_OUT.name, "and the items")
    return cv


def main():
    if "--sigil-only" in sys.argv:
        write_sigil()
        print("wrote void_sigil.png")
        return
    write_sigil()
    cv = build(write="--no-write" not in sys.argv)
    if "--preview" in sys.argv:
        out = Path(sys.argv[sys.argv.index("--preview") + 1])
        out.mkdir(parents=True, exist_ok=True)
        glows = [(cv.layers["glow"], 1.0), (cv.layers["pulse"], 0.9)]
        shots, labels = [], []
        kw = dict(ppx=5, size=(1000, 640), feet=(500, 400))
        for label, yaw, pitch, ps in (("top-down", 3.14159, 1.4, pose(t=0.3)), ("side", -1.57, 0.2, pose(t=0.3)),
                                      ("3/4 front", -0.7, 0.2, pose(t=0.3, flap=0.5, jaw=0.5)),
                                      ("3/4 back", 2.4, 0.3, pose(t=1.2, flap=-0.2)),
                                      ("dive", -1.2, 0.1, pose(t=0.0, amp=0.5, dive=1.0, flap=0.4))):
            shots.append(cm.render_view(M, cv.img, glows, pose=ps, yaw=yaw, pitch=pitch, **kw))
            labels.append(label)
        for im, lb in zip(shots, labels):
            im.save(out / f"wyrm_{lb.replace('/', '_').replace(' ', '_')}.png")
        head = dict(ppx=16, size=(900, 700), feet=(450, 600))
        close = [cm.render_view(M, cv.img, glows, pose=pose(t=0.3, jaw=0.6), yaw=y, pitch=0.15, **head) for y in (-0.5, -1.4, 0.5)]
        cm.sheet(close, ["head 3/4", "head side", "head front"], out / "wyrm_head_closeup.png")
        cm.sheet(shots, labels, out / "wyrm_sheet.png", cols=2)
        print("previews in", out)


if __name__ == "__main__":
    main()
