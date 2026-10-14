#!/usr/bin/env python3
"""Draws the Fire Beetle's model texture (and its glow layer) and writes its model definition.

    python tools/art/generate_fire_beetle.py [--preview DIR]

A big six-legged beetle of the Magma Rifts (docs/design/the_deep.md): a black basalt shell split by glowing magma
cracks, a pronotum with two short horns, a low head with curved mandibles, and three jointed legs a side. When it
winds up a charge its wing cases lift and show the molten body underneath.
Same pipeline as the slayer bosses (see generate_broodmother.py): the cube table below is the single source of truth
for the texture and for the generated Java, via tools/art/cube_model.py. Outputs:

    combat/src/main/resources/assets/skyrpg_combat/textures/entity/mob/fire_beetle.png
    ... fire_beetle_glow.png   (only the glowing pixels; drawn fullbright by FireBeetleRenderer)
    combat/src/client/java/com/skyrpg/combat/client/mob/FireBeetleLayout.java

``--preview DIR`` also renders preview sheets of the model (views, poses, mob-size thumbnails) into DIR.
Part tree: root > body > (horn_{r,l} > horn_{r,l}_tip, head > mand_{r,l} > mand_{r,l}_tip,
elytra_r, elytra_l, leg_{r,l}{0..2} > leg_*_a > leg_*_b).
"""
import math
import random
import sys
from pathlib import Path

from PIL import Image

sys.path.insert(0, str(Path(__file__).resolve().parent))
import cube_model as cm  # noqa: E402
from cube_model import face_rects, get, mix, mul, pick, put, ramp, vnoise, hnoise  # noqa: E402

ROOT = Path(__file__).resolve().parents[2]
TEX_DIR = ROOT / "combat/src/main/resources/assets/skyrpg_combat/textures/entity/mob"
JAVA_OUT = ROOT / "combat/src/client/java/com/skyrpg/combat/client/mob/FireBeetleLayout.java"

# ------------------------------------------------------------------------------------------------ cube table
M = cm.CubeModel("fire_beetle")
part, cube = M.part, M.cube

LEG_Z = (-6, -0.5, 5)
LEG_YAW = (-0.62, 0.05, 0.6)
FEMUR_TILT = 0.35  # the femur rises a little from under the shell
TIBIA_TILT = 0.12  # relative to the femur: the tibia comes down to the ground, splayed outwards

# body: the underside, the molten abdomen under the wing cases, the pronotum (thorax shield)
part("body", None, (0, 17, 0))
cube("body", "belly", "belly", (-6, 0, -8), (12, 3, 18), deco="belly")
cube("body", "abdomen", "molten", (-6.5, -2.5, -1), (13, 3, 11), deco="molten")  # fully inside the wing cases
cube("body", "pronotum", "shell", (-7, -3, -9), (14, 4, 7), deco="shell")
cube("body", "pron_mid", "shell", (-6, -4, -9), (12, 1, 7), deco="shell")
cube("body", "pron_top", "shell", (-4, -5, -8), (8, 1, 5), deco="shell")
# two short horns on the front of the pronotum, tilted forwards and a little outwards
for side, sg in (("r", 1), ("l", -1)):
    mir = side == "l"
    part(f"horn_{side}", "body", (-3 * sg, -4.5, -7.5), rot=(0.55, 0, -0.25 * sg))
    cube(f"horn_{side}", f"horn_{side}", "horn", (-1, -3, -1), (2, 3, 2), share="horn_r" if mir else None, mirror=mir, deco="horn")
    part(f"horn_{side}_tip", f"horn_{side}", (0, -3, 0), rot=(0.45, 0, 0))
    cube(f"horn_{side}_tip", f"horn_{side}_tip", "horn", (-0.5, -2, -0.5), (1, 2, 1), share="horn_r_tip" if mir else None, mirror=mir,
         deco="horn_tip")
# the head: low in front of the pronotum, glowing eyes at the corners
part("head", "body", (0, 1, -9))
cube("head", "head", "shell", (-4, -3, -4), (8, 5, 4), deco="head")
cube("head", "clypeus", "shell", (-3, -3, -5), (6, 2, 1), deco="clypeus")
for side, sg in (("r", 1), ("l", -1)):
    mir = side == "l"
    cube("head", f"palp_{side}", "leg", (-2.5 * sg - 0.5, 1, -4.5), (1, 2, 1), share="palp_r" if mir else None, mirror=mir)
    # mandibles: a thick base angled outwards, a hooked tip curving back in
    part(f"mand_{side}", "head", (-2.5 * sg, 1, -4), rot=(0, 0.4 * sg, 0))
    cube(f"mand_{side}", f"mand_{side}", "mandible", (-1, -1, -3), (2, 2, 3), share="mand_r" if mir else None, mirror=mir, deco="mand")
    part(f"mand_{side}_tip", f"mand_{side}", (0, 0, -3), rot=(0, -1.15 * sg, 0))
    cube(f"mand_{side}_tip", f"mand_{side}_tip", "mandible", (-0.5, -1, -3), (1, 2, 3),
         share="mand_r_tip" if mir else None, mirror=mir, deco="mand_tip")
# the wing cases, hinged at the front of the midline so they can lift and part
for side, sg in (("r", 1), ("l", -1)):
    mir = side == "l"
    part(f"elytra_{side}", "body", (0, -4, -2))
    # a dome in three layers, rounded off at the back by the tail piece
    cube(f"elytra_{side}", f"ely_{side}", "shell", (-9 if not mir else 0, 1, 0), (9, 5, 13), share="ely_r" if mir else None, mirror=mir,
         deco="elytra")
    cube(f"elytra_{side}", f"ely_{side}_mid", "shell", (-8 if not mir else 0, -1, 1), (8, 2, 12), share="ely_r_mid" if mir else None,
         mirror=mir, deco="elytra_top")
    cube(f"elytra_{side}", f"ely_{side}_top", "shell", (-5 if not mir else 0, -2, 2), (5, 1, 9), share="ely_r_top" if mir else None,
         mirror=mir, deco="elytra_top")
    cube(f"elytra_{side}", f"ely_{side}_tail", "shell", (-7 if not mir else 0, 1, 13), (7, 4, 2), share="ely_r_tail" if mir else None,
         mirror=mir, deco="shell")
# six legs: yaw root > femur (out from under the shell) > tibia (down to the ground) with a claw
for side, sg in (("r", 1), ("l", -1)):
    mir = side == "l"
    for i in range(3):
        n = f"{side}{i}"
        own = not mir and i == 0
        part(f"leg_{n}", "body", (-5 * sg, 2, LEG_Z[i]), rot=(0, LEG_YAW[i] * sg, 0))
        part(f"leg_{n}_a", f"leg_{n}", (0, 0, 0), rot=(0, 0, FEMUR_TILT * sg))
        cube(f"leg_{n}_a", f"{n}_a", "leg", (-4 if not mir else 0, -1, -1), (4, 2, 2), share=None if own else "r0_a", mirror=mir, deco="femur")
        part(f"leg_{n}_b", f"leg_{n}_a", (-4 * sg, 0, 0), rot=(0, 0, TIBIA_TILT * sg))
        cube(f"leg_{n}_b", f"{n}_b", "leg", (-1, -0.5, -1), (2, 7, 2), share=None if own else "r0_b", mirror=mir, deco="tibia")
        cube(f"leg_{n}_b", f"{n}_c", "claw", (-0.5, 6, -1.5), (1, 1, 2), share=None if own else "r0_c", mirror=mir)


# ------------------------------------------------------------------------------------------------ palette
class Pal:
    shell = ramp("0d0b0d", "151216", "1d181d", "262027", "312830")
    leg = ramp("0f0c0e", "181316", "221b1e", "2c2326")
    belly = ramp("24110d", "321812", "421f16", "52281b")
    crack_deep = (176, 34, 8)
    crack = (255, 92, 14)
    crack_hot = (255, 160, 40)
    crack_core = (255, 226, 130)
    heat = (92, 22, 10)       # the shell near a crack, tinted
    molten = ramp("ff6a10", "ff8a1c", "ffae34", "ffd060")
    eye = (255, 190, 60)
    claw = ramp("3a2a20", "4c3628")


FACE_LIGHT = {"top": 1.14, "bottom": 0.7, "right": 0.9, "front": 1.0, "left": 0.9, "back": 0.84}


# ------------------------------------------------------------------------------------------------ painting
def base_pixel(c, X, Y, px, py, fw, fh, face, seed):
    mat = c["mat"]
    n1 = vnoise(X, Y, 3.0, seed)
    n3 = hnoise(X, Y, seed + 3)
    if mat in ("shell", "horn", "mandible"):
        col = pick(Pal.shell, 0.25 + 0.55 * n1 + 0.25 * (n3 - 0.5))
        if n3 > 0.94:
            col = mul(col, 1.45)  # glassy basalt glints
        return col
    if mat == "leg":
        return pick(Pal.leg, 0.3 + 0.5 * n1 + 0.2 * (n3 - 0.5))
    if mat == "belly":
        return pick(Pal.belly, 0.3 + 0.5 * n1 + 0.2 * (n3 - 0.5))
    if mat == "molten":
        return pick(Pal.molten, 0.3 + 0.6 * n1)
    if mat == "claw":
        return pick(Pal.claw, n1)
    return (255, 0, 255)


def paint_cube(img, glow, c):
    rects = face_rects(c)
    seed = sum(ord(ch) * (i + 1) for i, ch in enumerate(c["id"]))
    for face, (fx, fy, fw, fh) in rects.items():
        for py in range(fh):
            for px in range(fw):
                col = base_pixel(c, fx + px, fy + py, px, py, fw, fh, face, seed)
                k = FACE_LIGHT[face]
                if face in ("front", "back", "left", "right"):
                    if py == 0:
                        k *= 1.1
                    if px == 0 or px == fw - 1:
                        k *= 0.92
                col = mul(col, k)
                put(img, fx + px, fy + py, col)
    d = c.get("deco")
    if d:
        DECOS[d](img, glow, c, rects, seed)


def cracks(img, glow, rect, seed, cell=6.0, strength=1.0):
    """Magma cracks: the borders of a wobbly Voronoi pattern across the face, one pixel wide, glow; the junctions burn
    brightest and the shell next to a crack is heated red."""
    fx, fy, fw, fh = rect
    if fw < 2 and fh < 2:
        return
    rng = random.Random(seed)
    n = max(2, round(fw * fh / (cell * cell)) + 1)
    pts = [(rng.uniform(-1, fw + 1), rng.uniform(-1, fh + 1)) for _ in range(n)]

    def label(px, py):
        return min(range(n), key=lambda i: (px + 0.5 - pts[i][0]) ** 2 + (py + 0.5 - pts[i][1]) ** 2
                   + 6 * vnoise(px + 17 * i, py + 31 * i, 1.6, seed))

    lab = [[label(px, py) for px in range(fw)] for py in range(fh)]
    crack = set()
    for py in range(fh):
        for px in range(fw):
            if px + 1 < fw and lab[py][px + 1] != lab[py][px] or py + 1 < fh and lab[py + 1][px] != lab[py][px]:
                crack.add((px, py))
    for px, py in crack:
        X, Y = fx + px, fy + py
        meet = {lab[y][x] for x in (px - 1, px, px + 1) for y in (py - 1, py, py + 1) if 0 <= x < fw and 0 <= y < fh}
        col = Pal.crack_core if len(meet) >= 3 else mix(Pal.crack, Pal.crack_hot, vnoise(X, Y, 2.5, seed + 11) * 1.2 - 0.1)
        put(img, X, Y, mix(Pal.crack_deep, col, 0.4))
        put(glow, X, Y, col, int(255 * strength))
    for px, py in crack:
        for nx, ny in ((px + 1, py), (px - 1, py), (px, py + 1), (px, py - 1)):
            if 0 <= nx < fw and 0 <= ny < fh and (nx, ny) not in crack:
                X, Y = fx + nx, fy + ny
                put(img, X, Y, mix(get(img, X, Y), Pal.heat, 0.45))


def deco_shell(img, glow, c, rects, seed):
    for face, r in rects.items():
        if face != "bottom":
            cracks(img, glow, r, seed + sum(map(ord, face)) * 7, cell=6.5 if face == "top" else 5.5)


def deco_elytra(img, glow, c, rects, seed):
    # cracks on the outside; the seam along the midline glows; the inside (seen when they lift) is hot
    for face in ("top", "right", "front", "back"):
        cracks(img, glow, rects[face], seed + sum(map(ord, face)) * 7, cell=6.0)
    tx, ty, tw, th = rects["top"]
    for py in range(th):  # the inner (midline) column of the top: the seam (the right-hand column of the right case)
        put(img, tx + tw - 1, ty + py, Pal.crack_deep)
        put(glow, tx + tw - 1, ty + py, Pal.crack, 220)
    lx, ly, lw, lh = rects["left"]  # the inner side (+x of the right case)
    for py in range(lh):
        for px in range(lw):
            put(img, lx + px, ly + py, mix(Pal.crack_deep, Pal.shell[1], 0.5))
            put(glow, lx + px, ly + py, Pal.crack, 150)
    bx, by, bw, bh = rects["bottom"]
    for py in range(bh):
        for px in range(bw):
            put(img, bx + px, by + py, mix(Pal.shell[0], Pal.crack_deep, 0.35))
            put(glow, bx + px, by + py, Pal.crack_deep, 90)


def deco_elytra_top(img, glow, c, rects, seed):
    for face in ("top", "right", "front", "back"):
        cracks(img, glow, rects[face], seed + sum(map(ord, face)) * 7, cell=6.5 if face == "top" else 5.0)
    tx, ty, tw, th = rects["top"]
    for py in range(th):
        put(img, tx + tw - 1, ty + py, Pal.crack_deep)
        put(glow, tx + tw - 1, ty + py, Pal.crack_hot, 235)
    lx, ly, lw, lh = rects["left"]
    for py in range(lh):
        for px in range(lw):
            put(glow, lx + px, ly + py, Pal.crack, 150)


def deco_head(img, glow, c, rects, seed):
    for face in ("top", "right", "left"):
        cracks(img, glow, rects[face], seed + sum(map(ord, face)) * 7, cell=4.5, strength=0.9)
    # glowing eyes at the front corners, on the front and on the sides
    fx, fy, fw, fh = rects["front"]
    for ex in (0, fw - 1):
        for ey in (1,):
            put(img, fx + ex, fy + ey, (120, 60, 10))
            put(glow, fx + ex, fy + ey, Pal.eye)
    for face, col_x in (("right", lambda w: w - 1), ("left", lambda w: 0)):
        sx, sy, sw, sh = rects[face]
        put(img, sx + col_x(sw), sy + 1, (120, 60, 10))
        put(glow, sx + col_x(sw), sy + 1, Pal.eye)
    # the mouth: dark, with a hot glow deep inside
    for px in range(2, fw - 2):
        put(img, fx + px, fy + fh - 1, (20, 6, 4))
        put(glow, fx + px, fy + fh - 1, Pal.crack_deep, 140)


def deco_clypeus(img, glow, c, rects, seed):
    fx, fy, fw, fh = rects["front"]
    for px in range(fw):
        if px % 2 == 0:
            put(img, fx + px, fy + fh - 1, mul(get(img, fx + px, fy + fh - 1), 0.6))


def deco_horn(img, glow, c, rects, seed):
    for face in ("front", "back", "left", "right"):
        sx, sy, sw, sh = rects[face]
        for px in range(sw):  # heated towards the top (the horn points up: row 0 is its upper end)
            put(img, sx + px, sy, mix(get(img, sx + px, sy), Pal.crack_deep, 0.5))
            put(glow, sx + px, sy, Pal.crack_deep, 120)


def deco_horn_tip(img, glow, c, rects, seed):
    for face, (sx, sy, sw, sh) in rects.items():
        for py in range(sh):
            for px in range(sw):
                k = 1 - py / max(1, sh)
                put(img, sx + px, sy + py, mix(Pal.crack_deep, Pal.crack_hot, k))
                put(glow, sx + px, sy + py, mix(Pal.crack, Pal.crack_core, k), int(170 + 85 * k))


def deco_mand(img, glow, c, rects, seed):
    # a hot edge along the inner side of the base
    for face in ("left", "top"):
        sx, sy, sw, sh = rects[face]
        for py in range(sh):
            put(glow, sx + (sw - 1 if face == "top" else 0), sy + py, Pal.crack_deep, 100)


def deco_mand_tip(img, glow, c, rects, seed):
    # dark chitin; the last pixel before the point heats up and the point itself glows
    for face in ("top", "bottom", "left", "right"):
        sx, sy, sw, sh = rects[face]
        along_x = face in ("left", "right")  # the front (the point) is the right-hand column of "right", the left of "left"
        for py in range(sh):
            for px in range(sw):
                if along_x:
                    k = (px + 0.5) / sw if face == "right" else 1 - (px + 0.5) / sw
                else:  # top and bottom: the front is the last row
                    k = (py + 0.5) / sh
                if k > 0.6:
                    put(img, sx + px, sy + py, mix(get(img, sx + px, sy + py), Pal.crack_deep, 0.7))
                    put(glow, sx + px, sy + py, Pal.crack, 200)
    fx, fy, fw, fh = rects["front"]
    for py in range(fh):
        for px in range(fw):
            put(img, fx + px, fy + py, Pal.crack_hot)
            put(glow, fx + px, fy + py, Pal.crack_core, 255)


def deco_belly(img, glow, c, rects, seed):
    # segment lines underneath, smouldering
    bx, by, bw, bh = rects["bottom"]
    for py in range(bh):
        if py % 3 == 1:
            for px in range(1, bw - 1):
                put(img, bx + px, by + py, mix(get(img, bx + px, by + py), Pal.crack_deep, 0.6))
                put(glow, bx + px, by + py, Pal.crack_deep, 150)
    for face in ("left", "right", "front", "back"):
        sx, sy, sw, sh = rects[face]
        for px in range(sw):
            if px % 3 == 1:
                put(glow, sx + px, sy + sh - 1, Pal.crack_deep, 110)


def deco_molten(img, glow, c, rects, seed):
    # the body under the wing cases: molten, all aglow, with dark crust plates floating on it
    rng = random.Random(seed)
    for face, (sx, sy, sw, sh) in rects.items():
        for py in range(sh):
            for px in range(sw):
                X, Y = sx + px, sy + py
                crust = vnoise(X, Y, 2.2, seed + 5) > 0.62
                if crust:
                    put(img, X, Y, Pal.shell[2])
                else:
                    col = get(img, X, Y)
                    put(glow, X, Y, col, 235)
    del rng


def deco_femur(img, glow, c, rects, seed):
    # the knee (the far end of the femur, the left-hand column of the side faces) smoulders
    for face in ("top", "front", "back", "bottom"):
        sx, sy, sw, sh = rects[face]
        kx = sx + sw - 1 if face == "back" else sx  # the back face runs the other way
        for py in range(sh):
            put(img, kx, sy + py, mix(get(img, kx, sy + py), Pal.crack_deep, 0.6))
            put(glow, kx, sy + py, Pal.crack, 170)
    tx, ty, tw, th = rects["top"]
    for px in range(1, tw - 1, 2):
        put(img, tx + px, ty, mul(get(img, tx + px, ty), 1.5))


def deco_tibia(img, glow, c, rects, seed):
    # little spines down the back edge and a hot joint at the top
    for face in ("front", "back", "left", "right"):
        sx, sy, sw, sh = rects[face]
        for px in range(sw):
            put(glow, sx + px, sy, Pal.crack_deep, 120)
        for py in range(2, sh - 1, 2):
            put(img, sx, sy + py, mul(get(img, sx, sy + py), 1.6))


DECOS = {"shell": deco_shell, "elytra": deco_elytra, "elytra_top": deco_elytra_top, "head": deco_head,
         "clypeus": deco_clypeus, "horn": deco_horn, "horn_tip": deco_horn_tip, "mand": deco_mand, "mand_tip": deco_mand_tip,
         "belly": deco_belly, "molten": deco_molten, "femur": deco_femur, "tibia": deco_tibia}


def paint():
    img = Image.new("RGBA", (M.width, M.height), (0, 0, 0, 0))
    glow = Image.new("RGBA", (M.width, M.height), (0, 0, 0, 0))
    for c in M.owners():
        paint_cube(img, glow, c)
    return img, glow


# ------------------------------------------------------------------------------------------------ preview
WINDUP, DASH, RECOVER = 20, 14, 10  # FireBeetleEntity.WINDUP_TICKS, DASH_TICKS, RECOVER_TICKS


def smooth(x):
    c = max(0.0, min(1.0, x))
    return c * c * (3 - 2 * c)


def charge_weights(ticks):
    """FireBeetleRenderer.extractRenderState: the synced charge countdown as wind-up and dash pose weights."""
    total = WINDUP + DASH + RECOVER
    windup = dash = 0.0
    if ticks > 0:
        e = total - max(0.01, ticks)
        if e < WINDUP:
            windup = smooth(e / 5)
        elif e < WINDUP + DASH:
            windup = 1 - smooth((e - WINDUP) / 3)
            dash = smooth((e - WINDUP) / 2)
        else:
            dash = 1 - smooth((e - WINDUP - DASH) / RECOVER)
    return windup, dash


def anim_pose(t, walk_pos=0.0, walk_speed=0.0, charge_ticks=0, attack=0.0):
    """FireBeetleModel.setupAnim, ported (keep the two in step): the tripod gait, the bite and the charge poses."""
    sin, cos = math.sin, math.cos
    windup, dash = charge_weights(charge_ticks)
    speed = min(1.0, walk_speed * 1.4) * (1 - windup)
    pos = walk_pos * 1.3
    quiver = sin(t * 2.7) * 0.025 * windup
    bite = 0.0
    if attack > 0:
        bite = smooth(attack / 0.5) if attack < 0.5 else 1 - smooth((attack - 0.5) / 0.2)
    twitch = max(0.0, min(1.0, (sin(t * 0.7) * sin(t * 0.13) - 0.6) * 3))
    opn = 0.06 + 0.2 * twitch + 0.65 * bite + 0.55 * windup + 0.75 * dash
    flutter = sin(t * 1.9) * 0.05 * windup
    pose = {
        "body": {"off": (0, -abs(sin(pos)) * 0.35 * speed + 1.3 * windup + 0.6 * dash, 0),
                 "rot": (0.16 * windup + 0.12 * dash + quiver, 0, cos(pos) * 0.025 * speed + quiver)},
        "head": {"rot": (0.25 * windup + 0.2 * dash, 0, 0)},
        "mand_r": {"rot": (0, opn, 0)},
        "mand_l": {"rot": (0, -opn, 0)},
        "elytra_r": {"rot": (0.3 * windup + flutter, 0.12 * windup, 0.32 * windup + flutter)},
        "elytra_l": {"rot": (0.3 * windup + flutter, -0.12 * windup, -0.32 * windup - flutter)},
    }
    paw = max(0.0, sin(t * 0.8)) * windup
    for k in range(6):
        side, i = (0 if k < 3 else 1), k % 3
        sg = 1 if side == 0 else -1
        n = ("r" if side == 0 else "l") + str(i)
        c = pos + ((i + side) % 2) * math.pi
        lift = max(0.0, -cos(c)) * speed
        yaw = sg * 0.35 * sin(c) * speed + (sg * 0.2 * windup if i == 2 else 0)
        femur = sg * 0.35 * lift + sg * ((0.45, 0.23, -0.02)[i] * windup + (0.29, 0.12, -0.03)[i] * dash)
        if k == 0:
            femur += 0.45 * paw
            yaw -= 0.25 * paw
        pose[f"leg_{n}"] = {"rot": (0, yaw, 0)}
        pose[f"leg_{n}_a"] = {"rot": (0, 0, femur)}
        pose[f"leg_{n}_b"] = {"rot": (0, 0, -sg * 0.15 * lift)}
    return pose


def preview(out_dir):
    out_dir = Path(out_dir)
    out_dir.mkdir(parents=True, exist_ok=True)
    tex = Image.open(TEX_DIR / "fire_beetle.png")
    glow = Image.open(TEX_DIR / "fire_beetle_glow.png")
    r = lambda **kw: cm.render(M, tex, glow, look_at=(0, 17, 0), **kw)
    big = dict(scale=9, size=(330, 280), center=(0.5, 0.52))
    views = [r(yaw=0.6, pitch=0.42, **big), r(yaw=-0.75, pitch=0.3, **big), r(yaw=math.pi / 2, pitch=0.05, **big),
             r(yaw=2.5, pitch=0.5, **big), r(yaw=0.0, pitch=0.1, **big)]
    row1 = cm.sheet(views, ["front 3/4", "front 3/4 other side", "side", "back 3/4", "front"])
    dark = dict(ambient=0.2, bg=(10, 10, 14))
    views2 = [r(yaw=0.6, pitch=1.25, **big), r(yaw=0.6, pitch=0.42, **dark, **big), r(yaw=2.5, pitch=0.5, **dark, **big),
              r(yaw=0.6, pitch=-0.45, **big), r(yaw=0.6, pitch=0.42, pose=anim_pose(10, walk_pos=1.2, walk_speed=1.0), **big)]
    row2 = cm.sheet(views2, ["from above", "dark cave", "dark back", "from below", "walking"])
    windup = anim_pose(14, charge_ticks=32)
    dash = anim_pose(24, walk_pos=4.0, walk_speed=1.0, charge_ticks=18)
    poses = [r(yaw=0.6, pitch=0.42, pose=windup, **big), r(yaw=math.pi / 2, pitch=0.1, pose=anim_pose(2, charge_ticks=32), **big),
             r(yaw=2.4, pitch=0.5, pose=windup, **dark, **big), r(yaw=0.6, pitch=0.35, pose=dash, **big),
             r(yaw=0.3, pitch=0.3, pose=anim_pose(5, attack=0.45), **big)]
    row3 = cm.sheet(poses, ["charge wind-up", "wind-up side (paw)", "wind-up dark back", "dash", "bite (mandibles open)"])
    small = [r(yaw=0.6, pitch=0.42, scale=s, size=(int(34 * s), int(26 * s)), center=(0.5, 0.55)) for s in (2, 3, 5)]
    small += [r(yaw=0.6, pitch=0.42, scale=s, size=(int(34 * s), int(26 * s)), center=(0.5, 0.55), **dark) for s in (3, 5)]
    row4 = cm.sheet(small, ["~20 blocks", "~12 blocks", "~7 blocks", "dark 12", "dark 7"])
    cm.stack([row1, row2, row3, row4]).save(out_dir / "fire_beetle_preview.png")
    cm.texture_sheet(tex, glow, zoom=4).save(out_dir / "fire_beetle_texture.png")
    print("wrote previews to", out_dir)


def main():
    rows = M.pack(sizes=(64, 128))
    print(f"packed {len(M.cubes)} cubes, {len(M.owners())} unique UV regions, {rows}/{M.height} rows of a {M.width}x{M.height} texture")
    TEX_DIR.mkdir(parents=True, exist_ok=True)
    img, glow = paint()
    img.save(TEX_DIR / "fire_beetle.png")
    glow.save(TEX_DIR / "fire_beetle_glow.png")
    JAVA_OUT.parent.mkdir(parents=True, exist_ok=True)
    JAVA_OUT.write_text(M.java("com.skyrpg.combat.client.mob", "FireBeetleLayout", "tools/art/generate_fire_beetle.py", "The Fire Beetle"),
                        encoding="utf-8", newline="\n")
    cm.write_spawn_egg(ROOT, "fire_beetle", ["0d0b0d", "151216", "1d181d", "262027", "312830"],
                       ["8a2a04", "c44a0c", "ff6a10", "ffa030", "ffe08a"])
    print("wrote textures, the spawn egg and", JAVA_OUT.name)
    if "--preview" in sys.argv:
        preview(sys.argv[sys.argv.index("--preview") + 1])


if __name__ == "__main__":
    main()
