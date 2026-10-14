#!/usr/bin/env python3
"""Draws the Sporeling's model texture (and its glow layer) and writes its model definition.

    python tools/art/generate_sporeling.py [--preview DIR]

A knee-high mushroom creature of the Fungal Depths (docs/design/the_deep.md): a big violet cap with glowing spots,
glowing gills underneath, a pale stalk body with two glowing eyes in the cap's shadow, little arms and stubby legs.
Same pipeline as the slayer bosses (see generate_broodmother.py): the cube table below is the single source of truth
for the texture and for the generated Java, via tools/art/cube_model.py. Outputs:

    combat/src/main/resources/assets/skyrpg_combat/textures/entity/mob/sporeling.png
    ... sporeling_glow.png   (only the glowing pixels; drawn fullbright by SporelingRenderer)
    combat/src/client/java/com/skyrpg/combat/client/mob/SporelingLayout.java

``--preview DIR`` also renders preview sheets of the model (views, poses, mob-size thumbnails) into DIR.
Part tree: root > body > (cap, arm_r, arm_l), leg_r, leg_l.
"""
import math
import random
import sys
from pathlib import Path

from PIL import Image

sys.path.insert(0, str(Path(__file__).resolve().parent))
import cube_model as cm  # noqa: E402
from cube_model import face_rects, get, hexc, mix, mul, pick, put, ramp, vnoise, hnoise  # noqa: E402

ROOT = Path(__file__).resolve().parents[2]
TEX_DIR = ROOT / "combat/src/main/resources/assets/skyrpg_combat/textures/entity/mob"
JAVA_OUT = ROOT / "combat/src/client/java/com/skyrpg/combat/client/mob/SporelingLayout.java"

# ------------------------------------------------------------------------------------------------ cube table
M = cm.CubeModel("sporeling")
part, cube = M.part, M.cube

# body (stalk) pivots at the hips, so the waddle rolls it over the legs
part("body", None, (0, 21, 0))
cube("body", "stem", "stem", (-3, -5, -2.5), (6, 5, 5), deco="face")
# the cap sits on top of the stalk: gills underneath, then a rounded dome in three steps
part("cap", "body", (0, -5, 0))
cube("cap", "gills", "gill", (-5, -1, -5), (10, 1, 10), deco="gills")
cube("cap", "cap_main", "cap", (-6, -4, -6), (12, 3, 12), deco="cap_main")
cube("cap", "cap_mid", "cap", (-5, -6, -5), (10, 2, 10), deco="cap_mid")
cube("cap", "cap_top", "cap", (-3, -7, -3), (6, 1, 6), deco="cap_top")
# little arms at the shoulders, hanging a bit outwards
for side, sg in (("r", 1), ("l", -1)):
    mir = side == "l"
    part(f"arm_{side}", "body", (-3 * sg, -3.5, 0), rot=(0, 0, 0.22 * sg))
    cube(f"arm_{side}", f"arm_{side}", "limb", (-1.5 if not mir else -0.5, -0.5, -1), (2, 4, 2),
         share="arm_r" if mir else None, mirror=mir, deco="arm")
# stubby legs on the root, so they stay planted while the body rolls
for side, sg in (("r", 1), ("l", -1)):
    mir = side == "l"
    part(f"leg_{side}", None, (-1.6 * sg, 21, 0))
    cube(f"leg_{side}", f"leg_{side}", "limb", (-1.5, 0, -1.5), (3, 3, 3), share="leg_r" if mir else None, mirror=mir, deco="leg")
    cube(f"leg_{side}", f"toe_{side}", "foot", (-1.5, 2, -2.5), (3, 1, 1), share="toe_r" if mir else None, mirror=mir)


# ------------------------------------------------------------------------------------------------ palette
class Pal:
    cap = ramp("2c1238", "3d1a4d", "512463", "672f79", "7e3c8e")
    cap_dark = ramp("1e0c28", "2a1236", "3a1a48")
    under = ramp("3a2440", "4c3052", "5e3c62")
    gill = ramp("8a6c90", "a284a6", "b89cba", "ccb4cc")
    stem = ramp("b4a690", "c6b8a0", "d6cab2", "e4dac4", "efe7d4")
    limb = ramp("9a8c78", "ab9d88", "bcae98", "cbbfa8")
    foot = ramp("5a4a40", "6a584c", "7a6858")
    glow = (182, 255, 92)
    glow_core = (226, 255, 168)
    glow_edge = (110, 210, 60)
    eye = (196, 255, 120)


FACE_LIGHT = {"top": 1.1, "bottom": 0.72, "right": 0.9, "front": 1.0, "left": 0.9, "back": 0.84}


# ------------------------------------------------------------------------------------------------ painting
def base_pixel(c, X, Y, px, py, fw, fh, face, seed):
    mat = c["mat"]
    n1 = vnoise(X, Y, 3.0, seed)
    n3 = hnoise(X, Y, seed + 3)
    if mat == "cap":
        if face == "bottom":
            col = pick(Pal.under, 0.3 + 0.5 * n1)
            return col
        t = 0.35 + 0.45 * n1 + 0.2 * (n3 - 0.5)
        if face in ("front", "back", "left", "right"):
            t += 0.12 * (1 - py / max(1, fh - 1)) - 0.1  # lighter towards the top of the sides
            if py == fh - 1:
                t -= 0.25  # dark rim
        col = pick(Pal.cap, t)
        if n3 > 0.93:
            col = mul(col, 1.18)
        return col
    if mat == "gill":
        return pick(Pal.gill, 0.3 + 0.5 * n1)
    if mat == "stem":
        t = 0.4 + 0.4 * n1 + 0.2 * (n3 - 0.5)
        if face in ("front", "back", "left", "right") and (X * 7 + seed) % 5 == 0:
            t -= 0.18  # vertical fibres
        return pick(Pal.stem, t)
    if mat == "limb":
        t = 0.4 + 0.45 * n1 + 0.15 * (n3 - 0.5)
        if face in ("front", "back", "left", "right") and py == fh - 1:
            t -= 0.35
        return pick(Pal.limb, t)
    if mat == "foot":
        return pick(Pal.foot, 0.3 + 0.5 * n1)
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
                        k *= 1.05
                    if px == 0 or px == fw - 1:
                        k *= 0.95
                col = mul(col, k)
                put(img, fx + px, fy + py, col)
    d = c.get("deco")
    if d:
        DECOS[d](img, glow, c, rects, seed)


def spot(img, glow, x, y, size, bounds, dim=1.0):
    """A glowing cap spot: size 1 (dot), 2 (2x2) or 3 (3x3 with cut corners), with a darker ring on the cap."""
    bx, by, bw, bh = bounds
    if size == 1:
        cells = [(0, 0)]
    elif size == 2:
        cells = [(0, 0), (1, 0), (0, 1), (1, 1)]
    else:
        cells = [(1, 0), (0, 1), (1, 1), (2, 1), (1, 2), (0, 0), (2, 0), (0, 2), (2, 2)]
        cells = cells[:5] if size == 3 else cells
    for dx, dy in cells:
        X, Y = x + dx, y + dy
        if bx <= X < bx + bw and by <= Y < by + bh:
            core = (dx, dy) == (0, 0) and size > 1 or (size == 3 and (dx, dy) == (1, 1))
            col = Pal.glow_core if core else Pal.glow
            put(img, X, Y, mix((70, 110, 40), col, 0.5))
            put(glow, X, Y, mul(col, dim), 255)


def deco_cap_main(img, glow, c, rects, seed):
    # the top is a 1-pixel ring around cap_mid: a few dots on it
    fx, fy, fw, fh = rects["top"]
    for x, y in [(0, 3), (11, 7), (4, 11), (8, 0), (0, 9)]:
        spot(img, glow, fx + x, fy + y, 1, rects["top"])
    # the brim: one spot and one dot on each side, in the upper rows
    for face, layout in (("front", [(2, 0, 2), (8, 1, 1)]), ("back", [(7, 0, 2), (2, 1, 1)]),
                         ("left", [(5, 0, 2), (1, 1, 1)]), ("right", [(3, 0, 2), (9, 1, 1)])):
        sx, sy, sw, sh = rects[face]
        for x, y, size in layout:
            spot(img, glow, sx + x, sy + y, size, rects[face])
    # gill lines show on the underside of the brim, glowing faintly
    bx, by, bw, bh = rects["bottom"]
    for py in range(bh):
        for px in range(bw):
            if min(px, py, bw - 1 - px, bh - 1 - py) == 0 and (px + py) % 2 == 0:
                put(img, bx + px, by + py, mix(get(img, bx + px, by + py), Pal.gill[1], 0.55))
                put(glow, bx + px, by + py, Pal.glow_edge, 70)


def deco_cap_mid(img, glow, c, rects, seed):
    # a 2-pixel ring around cap_top shows on top: the big spots
    fx, fy, fw, fh = rects["top"]
    for x, y, size in [(0, 1, 2), (8, 4, 2), (4, 8, 2), (5, 0, 1), (1, 7, 1), (8, 8, 1)]:
        spot(img, glow, fx + x, fy + y, size, rects["top"])
    for face, x in (("front", 6), ("back", 2), ("left", 3), ("right", 6)):
        sx, sy, sw, sh = rects[face]
        spot(img, glow, sx + x, sy, 1, rects[face])


def deco_cap_top(img, glow, c, rects, seed):
    fx, fy, fw, fh = rects["top"]
    spot(img, glow, fx + 1, fy + 1, 2, rects["top"])
    spot(img, glow, fx + 4, fy + 4, 1, rects["top"])
    # a lighter crown
    for py in range(fh):
        for px in range(fw):
            if (px, py) not in ((1, 1), (2, 1), (1, 2), (2, 2), (4, 4)):
                put(img, fx + px, fy + py, mix(get(img, fx + px, fy + py), Pal.cap[4], 0.25))


def deco_gills(img, glow, c, rects, seed):
    # radial gill lines on the underside, a glowing ring around the stalk
    bx, by, bw, bh = rects["bottom"]
    cx, cy = (bw - 1) / 2, (bh - 1) / 2
    for py in range(bh):
        for px in range(bw):
            dx, dy = px - cx, py - cy
            r = math.hypot(dx, dy)
            ang = math.atan2(dy, dx)
            line = (math.sin(ang * 14) > 0.35)
            X, Y = bx + px, by + py
            if line:
                put(img, X, Y, mul(get(img, X, Y), 0.62))
            else:
                put(img, X, Y, mix(get(img, X, Y), (230, 214, 236), 0.25))
                put(glow, X, Y, Pal.glow_edge, int(40 + 60 * min(1, r / 4.5)))
    # the sides of the gill plate: dark slits
    for face in ("front", "back", "left", "right"):
        sx, sy, sw, sh = rects[face]
        for px in range(sw):
            if px % 2 == 0:
                put(img, sx + px, sy, mul(Pal.gill[0], 0.7))


def deco_face(img, glow, c, rects, seed):
    fx, fy, fw, fh = rects["front"]
    # shadow under the cap
    for px in range(fw):
        put(img, fx + px, fy, mul(get(img, fx + px, fy), 0.55))
        put(img, fx + px, fy + 1, mul(get(img, fx + px, fy + 1), 0.8))
    # two glowing eyes (1x2, a dark socket around them) and a small mouth
    for ex in (1, 4):
        for ey in (1, 2):
            put(img, fx + ex, fy + ey, (60, 80, 40))
            put(glow, fx + ex, fy + ey, Pal.eye if ey == 2 else Pal.glow_core)
    for px in (2, 3):
        put(img, fx + px, fy + 3, (58, 40, 46))
    # the same shadow on the other sides
    for face in ("back", "left", "right"):
        sx, sy, sw, sh = rects[face]
        for px in range(sw):
            put(img, sx + px, sy, mul(get(img, sx + px, sy), 0.55))
            put(img, sx + px, sy + 1, mul(get(img, sx + px, sy + 1), 0.82))
    # a few freckles
    rng = random.Random(seed)
    for face in ("left", "right", "back"):
        sx, sy, sw, sh = rects[face]
        for _ in range(2):
            put(img, sx + rng.randrange(sw), sy + rng.randint(2, sh - 1), Pal.stem[0])


def deco_arm(img, glow, c, rects, seed):
    # stubby hands: the last row darker, a glowing fleck on the back of the hand
    for face in ("front", "back", "left", "right"):
        sx, sy, sw, sh = rects[face]
        for px in range(sw):
            put(img, sx + px, sy + sh - 1, mix(get(img, sx + px, sy + sh - 1), Pal.foot[1], 0.6))


def deco_leg(img, glow, c, rects, seed):
    for face in ("front", "back", "left", "right"):
        sx, sy, sw, sh = rects[face]
        for px in range(sw):
            put(img, sx + px, sy + sh - 1, mix(get(img, sx + px, sy + sh - 1), Pal.foot[1], 0.7))


DECOS = {"cap_main": deco_cap_main, "cap_mid": deco_cap_mid, "cap_top": deco_cap_top, "gills": deco_gills, "face": deco_face,
         "arm": deco_arm, "leg": deco_leg}


def paint():
    img = Image.new("RGBA", (M.width, M.height), (0, 0, 0, 0))
    glow = Image.new("RGBA", (M.width, M.height), (0, 0, 0, 0))
    for c in M.owners():
        paint_cube(img, glow, c)
    return img, glow


# ------------------------------------------------------------------------------------------------ preview
def anim_pose(t, walk_pos=0.0, walk_speed=0.0, attack=0.0):
    """SporelingModel.setupAnim, ported (keep the two in step): the waddle, the cap bob and breathing, the head-butt."""
    sin, cos = math.sin, math.cos
    speed = min(1.0, walk_speed * 1.5)
    pos = walk_pos * 0.9
    s, c = sin(pos), cos(pos)
    passing = 1 - abs(s)

    def sink(x):
        return 3 * cos(x) + (2.5 if x > 0 else 1.5) * abs(sin(x)) - 3

    xr, xl = 0.6 * s * speed, -0.6 * s * speed
    body_rot = [0.08 * speed, 0, 0.16 * s * speed]
    cap_rot = [-0.05 * speed, 0, -0.09 * sin(pos - 0.6) * speed]
    flap = 0.25 * abs(s) * speed + 0.05 * sin(t * 0.12)
    arm_r = [-0.7 * s * speed, 0, flap]
    arm_l = [0.7 * s * speed, 0, -flap]
    if attack > 0:
        k = sin(attack * math.pi)
        body_rot[0] += 0.55 * k
        cap_rot[0] += 0.25 * k
        arm_r[0] -= 2.0 * k
        arm_l[0] -= 2.0 * k
    breathe = 1 + 0.025 * sin(t * 0.09)
    return {
        "leg_r": {"rot": (xr, 0, 0), "off": (0, -sink(xr) - max(0.0, -c) * speed, 0)},
        "leg_l": {"rot": (xl, 0, 0), "off": (0, -sink(xl) - max(0.0, c) * speed, 0)},
        "body": {"rot": tuple(body_rot), "off": (0, -passing * 0.7 * speed, 0)},
        "cap": {"rot": tuple(cap_rot), "off": (0, sin(pos * 2 - 0.9) * 0.45 * speed + sin(t * 0.09) * 0.25, 0),
                "scale": (breathe, 2 - breathe, breathe)},
        "arm_r": {"rot": tuple(arm_r)},
        "arm_l": {"rot": tuple(arm_l)},
    }


def preview(out_dir):
    out_dir = Path(out_dir)
    out_dir.mkdir(parents=True, exist_ok=True)
    tex = Image.open(TEX_DIR / "sporeling.png")
    glow = Image.open(TEX_DIR / "sporeling_glow.png")
    r = lambda **kw: cm.render(M, tex, glow, look_at=(0, 16, 0), **kw)
    big = dict(scale=14, size=(300, 300), center=(0.5, 0.5))
    views = [r(yaw=0.55, pitch=0.42, **big), r(yaw=-0.7, pitch=0.3, **big), r(yaw=math.pi / 2, pitch=0.05, **big),
             r(yaw=2.6, pitch=0.45, **big), r(yaw=0.4, pitch=-0.5, **big), r(yaw=0.6, pitch=1.2, **big)]
    row1 = cm.sheet(views, ["front 3/4", "front 3/4 other side", "side", "back 3/4", "from below (gills)", "from above"])
    dark = dict(ambient=0.22, bg=(10, 10, 14))
    step = anim_pose(10, walk_pos=math.pi / 2 / 0.9, walk_speed=1.0)
    poses = [r(yaw=0.55, pitch=0.35, pose=step, **big), r(yaw=math.pi / 2, pitch=0.1, pose=step, **big),
             r(yaw=1.1, pitch=0.3, pose=anim_pose(10, attack=0.5), **big), r(yaw=0.55, pitch=0.42, **dark, **big),
             r(yaw=2.4, pitch=0.42, **dark, **big)]
    row2 = cm.sheet(poses, ["walk (mid step)", "walk side", "head-butt", "dark cave", "dark cave back"])
    small = [r(yaw=0.55, pitch=0.42, scale=s, size=(int(26 * s), int(26 * s)), center=(0.5, 0.55)) for s in (2, 3, 5)]
    small += [r(yaw=0.55, pitch=0.42, scale=s, size=(int(26 * s), int(26 * s)), center=(0.5, 0.55), **dark) for s in (3, 5)]
    # several at mob size, as a group would walk
    row3 = cm.sheet(small, ["~20 blocks", "~12 blocks", "~7 blocks", "dark 12", "dark 7"])
    cm.stack([row1, row2, row3]).save(out_dir / "sporeling_preview.png")
    cm.texture_sheet(tex, glow, zoom=6).save(out_dir / "sporeling_texture.png")
    print("wrote previews to", out_dir)


def main():
    rows = M.pack(sizes=(64, 128))
    print(f"packed {len(M.cubes)} cubes, {len(M.owners())} unique UV regions, {rows}/{M.height} rows of a {M.width}x{M.height} texture")
    TEX_DIR.mkdir(parents=True, exist_ok=True)
    img, glow = paint()
    img.save(TEX_DIR / "sporeling.png")
    glow.save(TEX_DIR / "sporeling_glow.png")
    JAVA_OUT.parent.mkdir(parents=True, exist_ok=True)
    JAVA_OUT.write_text(M.java("com.skyrpg.combat.client.mob", "SporelingLayout", "tools/art/generate_sporeling.py", "The Sporeling"),
                        encoding="utf-8", newline="\n")
    cm.write_spawn_egg(ROOT, "sporeling", ["2c1238", "3d1a4d", "512463", "672f79", "7e3c8e"],
                       ["4a8a1a", "6ab82a", "96e046", "b6ff5c", "e2ffa8"])
    print("wrote textures, the spawn egg and", JAVA_OUT.name)
    if "--preview" in sys.argv:
        preview(sys.argv[sys.argv.index("--preview") + 1])


if __name__ == "__main__":
    main()
