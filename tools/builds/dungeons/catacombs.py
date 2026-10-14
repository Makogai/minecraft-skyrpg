"""The Catacombs (skyrpg_world:catacombs): a labyrinth of crypts deep under the Magma Rifts layer (y -60 to -40).
Mob levels 59-61, three Bone Wardens (Lv 60 mini-bosses, one Ossuary Key each), boss Lv 62 (the Bonelord).
Built like the other v2 dungeons (see docs/design/dungeons.md and docs/design/end_game.md) on dungeon_kit and endgame_kit.

Layout (jigsaw, size 16, max_distance 116). There is no surface: the start piece is placed at an absolute height
(start_height -58: its local y=1, the doorway floor, sits at y=-58) with no heightmap projection and no terrain adaptation.
Every piece has its floor at local y=0 and 3x3 doorways from y=1, so the labyrinth is one flat level at y -59..-50.

  "entrance" (25 x 24 x 25, the start piece): a sealed stair, solid rock around it. The top (local y=18, about y=-41, at the
           Magma Rifts layer) is a small sealed bone chamber (a sign, candles, skulls, the dungeon marker); a square-spiral
           stair of 18 blocks leads down to the labyrinth. Players tunnel into the chamber: it is "a sealed stair found in
           the rifts".
  SPINE    s1 -> s2 -> tomb gate -> the Bonelord's Crypt (each spine stage is 21 deep; reach is about 107).
           s1 and s2 choose between mazes (a 6 x 5 cell maze with loops, traps, packs and chests in the dead ends) and halls
           (a crypt hall, an ossuary hall, a lever hall and a plate hall; the last two block the spine with a gate until the
           puzzle is solved). Every spine piece has a west and an east door at its middle row.
  WARDENS  s1 west, s1 east and s2 west lead to the 'wardens' pool, whose only piece is a Bone Warden's chamber (each warden
           drops one Ossuary Key). s2 east leads to the ordinary side rooms (ossuary, bone pit, sarcophagi, trap gallery,
           shrine, lever and plate vaults, charnel). Elites also drop Ossuary Keys (35%), so a lost warden can be replaced.
  TOMB     "tomb/gate": three bone doors in a row, each opened by one Ossuary Key (a key_door marker uses up one key, so
           three sealed doors chain three keys), then the Bonelord's Crypt (31 x 17 x 31, boss, a hoard behind a door that
           opens when he dies).

Doors: cat_spine_in / cat_side_in / cat_tail_in receive; the _out ones target the matching _in. Spine doors have priority 10.

Run:  python tools/builds/dungeons/catacombs.py       (add --show <piece path> <y> to print a slice of a piece)
Offline checks (all run on every build): jigsaw validation, block and loot item ids, walk test (every door and marker
reachable, door markers opened), pack sizes, spine rules, and the reach table.
"""
import random
import sys
import pathlib

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parent))
from endgame_kit import (Pal, Style, Room, Theme, Shaft, cap_piece, mob, wchoice,  # noqa: E402
                         it, book, rolls, table, lpool, run_main, AIR, BARS)

CAT = "catacombs"
DUN = "skyrpg_world:catacombs"
M, W, C = "skyrpg_mining:", "skyrpg_world:", "skyrpg_combat:"

DB, DT, PD = "minecraft:deepslate_bricks", "minecraft:deepslate_tiles", "minecraft:polished_deepslate"
CDB, CDT, CHD, COBD = "minecraft:cracked_deepslate_bricks", "minecraft:cracked_deepslate_tiles", "minecraft:chiseled_deepslate", "minecraft:cobbled_deepslate"
PBB, PB, CHPB, BS = "minecraft:polished_blackstone_bricks", "minecraft:polished_blackstone", "minecraft:chiseled_polished_blackstone", "minecraft:blackstone"
BONE = "minecraft:bone_block"
SKULL = "minecraft:skeleton_skull[rotation={r}]"
SOULL = "minecraft:soul_lantern"
SLAB_DS = "minecraft:polished_deepslate_slab[type=bottom]"

PAL = {
    1: Pal(DB, DT, PD, PD, CHD, {DB: [(CDB, .18), (COBD, .05), ("minecraft:tuff", .02)], DT: [(CDT, .16), (COBD, .03)], PD: [(DB, .06)]}, SOULL),
    2: Pal(PBB, PB, DT, PB, CHPB, {PBB: [("minecraft:cracked_polished_blackstone_bricks", .14), (BS, .06)], PB: [(BS, .08)]}, SOULL),
}
ST = Style(CAT, "cat", PAL, {1: f"{CAT}/rooms", 2: f"{CAT}/rooms"}, {1: f"{CAT}/leaves", 2: f"{CAT}/leaves"}, "catacombs",
           {1: 14, 2: 17}, sign="crimson")
THEME = Theme(
    [("minecraft:deepslate", 50), (BS, 14), ("minecraft:basalt", 8), ("minecraft:smooth_basalt", 6), (COBD, 12), ("minecraft:tuff", 4),
     ("minecraft:magma_block", 2), ("minecraft:deepslate_coal_ore", 2), ("minecraft:deepslate_gold_ore", 1), ("minecraft:deepslate_diamond_ore", 1)],
    [("minecraft:deepslate", 50)], [("minecraft:deepslate", 50)],
    [(DB, 50), (CDB, 22), (DT, 10), (COBD, 10), (BONE, 4), (PD, 4)],
    [("minecraft:deepslate_brick_stairs", 76), ("minecraft:deepslate_tile_stairs", 16), ("minecraft:cobbled_deepslate_stairs", 8)],
    (DT, PD), light=SOULL + "[hanging=true]")
SPX = 14          # the x of every spine doorway on the 25-wide spine pieces


def L(d=0, fl=1):
    return 59 + d + (1 if fl == 2 else 0)


# ---------------------------------------------------------------------------------------------------------------------
# Mobs
# ---------------------------------------------------------------------------------------------------------------------
def soldier(n, lvl, name="Bone Soldier", elite=False):
    return mob("minecraft:skeleton", n, lvl, name, elite, mainhand="minecraft:iron_sword", head="minecraft:iron_helmet", chest="minecraft:chainmail_chestplate")


def archer(n, lvl, name="Crypt Archer"):
    return mob("minecraft:skeleton", n, lvl, name, False, mainhand="minecraft:bow", head="minecraft:chainmail_helmet")


def wraith(n, lvl, name="Tomb Wraith", elite=False):
    return mob("minecraft:wither_skeleton", n, lvl, name, elite, mainhand="minecraft:stone_sword", head="minecraft:iron_helmet")


def risen(n, lvl, name="Risen Dead", elite=False):
    return mob("minecraft:zombie", n, lvl, name, elite, mainhand="minecraft:iron_axe", head="minecraft:iron_helmet", chest="minecraft:iron_chestplate")


def caller(n, lvl, name="Grave Caller"):
    return mob("skyrpg_combat:gravecaller", n, lvl, name)


def marshal(n, lvl, name="Bone Marshal", elite=True):
    return mob("skyrpg_combat:bone_marshal", n, lvl, name, elite)


def pack(lvl_d=0, fl=1, big=False):
    """Two waves of crypt mobs."""
    a = L(lvl_d, fl)
    w1 = [soldier(4, a), archer(2, a + 1)]
    w2 = [risen(3, a + 1), wraith(2, a + 1), caller(1, a + 1)] if not big else [wraith(3, a + 2), risen(3, a + 1), marshal(1, a + 2)]
    return [w1, w2]


# ---------------------------------------------------------------------------------------------------------------------
# Shared decor
# ---------------------------------------------------------------------------------------------------------------------
def room(path, w, h, d, fl=1, tail=True):
    r = Room(ST, path, w, h, d, fl)
    r.shell()
    r.door("south", "side_in")
    if tail:
        r.door("north", "tail_out")
    r.lane("x")
    return r


def spine_piece(path, h, fl, out_pool, west_pool, east_pool, w=25, d=21):
    """A spine piece: south in and north out at x=SPX, west and east doors at the middle row."""
    r = Room(ST, path, w, h, d, fl)
    return r


def spine_doors(r, out_pool, west_pool, east_pool):
    r.door("south", "spine_in", at=SPX)
    r.door("north", "spine_out", pool=out_pool, at=SPX)
    r.door("west", "side_out", pool=west_pool, at=10)
    r.door("east", "side_out", pool=east_pool, at=10)
    r.reserve(SPX - 1, 0, SPX + 1, r.d - 1)
    r.reserve(0, 9, r.w - 1, 11)


def skulls(r, cells, y=1):
    for i, (x, z) in enumerate(cells):
        if r.free(x, z, y):
            r.set(x, y, z, SKULL.format(r=(x * 3 + z * 5) % 16))


def sarcophagus(r, x, z, length=3, along="x"):
    cells = [(x + i, z) if along == "x" else (x, z + i) for i in range(length)]
    if not all(r.free(cx, cz) for cx, cz in cells):
        return False
    for i, (cx, cz) in enumerate(cells):
        r.set(cx, 1, cz, "minecraft:polished_deepslate_slab[type=top]" if i else "minecraft:chiseled_deepslate")
        r.keep.add((cx, cz))
    return True


def webs(r, n, x1, z1, x2, z2, y=None):
    for _ in range(n):
        x, z = r.rng.randint(x1, x2), r.rng.randint(z1, z2)
        yy = r.h - 3 if y is None else y
        if r.get(x, yy, z) == AIR:
            r.set(x, yy, z, "minecraft:cobweb")


def candles(r, cells, y=1):
    for x, z in cells:
        if r.free(x, z, y):
            r.set(x, y, z, f"minecraft:candle[candles={r.rng.randint(1, 4)},lit=true]")


def bone_pile(r, cx, cz, rad=1):
    for x in range(cx - rad, cx + rad + 1):
        for z in range(cz - rad, cz + rad + 1):
            if r.free(x, z):
                r.set(x, 1, z, BONE if (x + z) % 2 == 0 else "minecraft:bone_block[axis=x]")
                if abs(x - cx) + abs(z - cz) == 0 and r.free(x, z, 2):
                    r.set(x, 2, z, SKULL.format(r=(x + z * 3) % 16))
                r.keep.add((x, z))


# ---------------------------------------------------------------------------------------------------------------------
# Mazes: a 6 x 5 cell maze (cells are 3 x 3, walls 1 thick) with loops; every cell is reachable
# ---------------------------------------------------------------------------------------------------------------------
MZ_C, MZ_R = 6, 5


def build_maze(pack_, name, seed, out_pool, west_pool, east_pool, density=1):
    w, h, d = 25, 6, 21
    r = Room(ST, f"f1/{name}", w, h, d, 1)
    p, pal = r.p, r.pal
    rng = random.Random(seed)
    p.fill(0, 0, 0, w - 1, h - 1, d - 1, pal.wall)
    for x in range(w):
        for z in range(d):
            p.set(x, 0, z, pal.inlay if rng.random() < .12 else pal.floor)
    x0 = lambda c: 4 * c + 1  # noqa: E731
    z0 = lambda rr: 4 * rr + 1  # noqa: E731
    for c in range(MZ_C):
        for rr in range(MZ_R):
            for x in range(x0(c), x0(c) + 3):
                for z in range(z0(rr), z0(rr) + 3):
                    for y in range(1, h - 1):
                        p.set(x, y, z, AIR)
    start = (MZ_R - 1, 3)                                     # the cell behind the south door (row 4, col 3 -> x 13..15)
    seen, stack, passages = {start}, [start], set()
    while stack:
        rr, c = stack[-1]
        opts = [(rr + dr, c + dc) for dr, dc in ((1, 0), (-1, 0), (0, 1), (0, -1))
                if 0 <= rr + dr < MZ_R and 0 <= c + dc < MZ_C and (rr + dr, c + dc) not in seen]
        if not opts:
            stack.pop()
            continue
        nxt = rng.choice(opts)
        seen.add(nxt)
        passages.add(frozenset(((rr, c), nxt)))
        stack.append(nxt)
    extra = [frozenset(((rr, c), (rr + dr, c + dc))) for rr in range(MZ_R) for c in range(MZ_C) for dr, dc in ((1, 0), (0, 1))
             if rr + dr < MZ_R and c + dc < MZ_C]
    for e in extra:
        if e not in passages and rng.random() < .16:
            passages.add(e)

    def open_wall(a, b):
        (r1, c1), (r2, c2) = sorted(tuple(e) for e in (a, b))
        if r1 == r2:                                           # horizontal neighbours: the wall column between them
            x = x0(c1) + 3
            for z in range(z0(r1), z0(r1) + 3):
                for y in range(1, h - 1):
                    p.set(x, y, z, AIR)
        else:
            z = z0(r1) + 3
            for x in range(x0(c1), x0(c1) + 3):
                for y in range(1, h - 1):
                    p.set(x, y, z, AIR)
    for e in passages:
        a, b = tuple(e)
        open_wall(a, b)
    deg = {(rr, c): 0 for rr in range(MZ_R) for c in range(MZ_C)}
    open_dirs = {k: set() for k in deg}
    for e in passages:
        a, b = tuple(e)
        for u, v in ((a, b), (b, a)):
            deg[u] += 1
            open_dirs[u].add(((v[0] > u[0]) - (v[0] < u[0]), (v[1] > u[1]) - (v[1] < u[1])))
    # doors: south (x=SPX, row 4), north (x=SPX, row 0), west and east at row 2 (z=10)
    spine_doors(r, out_pool, west_pool, east_pool)
    for c in range(MZ_C):                                      # make sure the door cells line up with the grid
        pass
    cells = [(rr, c) for rr in range(MZ_R) for c in range(MZ_C)]
    special = {(MZ_R - 1, 3), (0, 3), (2, 0), (2, MZ_C - 1)}
    dead = [k for k in cells if deg[k] == 1 and k not in special]
    rng.shuffle(dead)
    others = [k for k in cells if k not in special and k not in dead]
    rng.shuffle(others)

    def centre(k):
        return x0(k[1]) + 1, z0(k[0]) + 1
    for i, k in enumerate(dead):
        cx, cz = centre(k)
        (dr, dc), = open_dirs[k]
        far = (cx - dc, cz - dr)                                # the middle of the edge opposite the opening
        face = {(1, 0): "north", (-1, 0): "south", (0, 1): "west", (0, -1): "east"}[(dr, dc)]
        if i % 3 == 0:
            r.chest(far[0], far[1], face, "rare" if i == 0 else "common")
        elif i % 3 == 1:
            bone_pile(r, far[0], far[1], 0)
            r.set(far[0], 1, far[1], "minecraft:barrel[facing=up]")
            r.p.barrel(far[0], 2, far[1], face, "skyrpg_world:chests/catacombs_common", tier="common") if False else None
        else:
            r.spawn(cx, cz, *pack(0)[:1], radius=7, spread=1)
    for i, k in enumerate(others[:3 + density]):
        cx, cz = centre(k)
        if i % 3 == 0:
            r.spawn(cx, cz, *pack(i % 2), radius=8, spread=2)
        elif i % 3 == 1:
            r.spikes(cx, cz, 1)
        else:
            r.poison(cx, cz, 1)
    # arrow slits: dispensers in a wall facing a corridor
    placed = 0
    for k in others[3 + density:] + dead:
        if placed >= 2 + density:
            break
        cx, cz = centre(k)
        for dr, dc, face in ((0, -1, "east"), (0, 1, "west"), (-1, 0, "south"), (1, 0, "north")):
            if (dr, dc) not in open_dirs[k] and 0 <= k[0] + dr < MZ_R and 0 <= k[1] + dc < MZ_C or False:
                wx, wz = cx + dc * 2, cz + dr * 2
                if (dr, dc) not in open_dirs[k] and p.get(wx, 2, wz) == pal.wall and p.get(cx + dc, 2, cz + dr) == AIR:
                    r.arrows(wx, 2, wz, face, radius=9)
                    placed += 1
                    break
    for k in cells:                                            # dressing in the cell corners and ceilings
        cx, cz = centre(k)
        if rng.random() < .55:
            r.set(cx, h - 2, cz, SOULL + "[hanging=true]")
        for sx, sz in ((cx - 1, cz - 1), (cx + 1, cz - 1), (cx - 1, cz + 1), (cx + 1, cz + 1)):
            roll = rng.random()
            if roll < .10:
                p.set(sx, 1, sz, SKULL.format(r=rng.randrange(16)))
            elif roll < .16:
                p.set(sx, 1, sz, "minecraft:candle[candles=2,lit=true]")
            elif roll < .30:
                p.set(sx, h - 2, sz, "minecraft:cobweb")
            elif roll < .38:
                p.set(sx, 1, sz, "minecraft:bone_block[axis=y]")
    p.sign(SPX - 2, 2, MZ_R * 4 - 1, ["THE LABYRINTH", "Turn back.", "The dead know the way."], block="minecraft:crimson_wall_sign[facing=north]", color="white", glow=True) if False else None
    r.finish(pack_)
    return r.p


# ---------------------------------------------------------------------------------------------------------------------
# Halls (spine pieces, 25 x h x 21)
# ---------------------------------------------------------------------------------------------------------------------
def hall(path, h, out_pool, west_pool, east_pool, fl=1):
    r = Room(ST, path, 25, h, 21, fl)
    r.shell()
    r.ribs(6, 3)
    spine_doors(r, out_pool, west_pool, east_pool)
    return r


def hall_crypt(pack_, tag, out_pool, wp, ep):
    r = hall(f"f1/crypt_{tag}", 9, out_pool, wp, ep)
    for z in (3, 6, 14, 17):
        sarcophagus(r, 2, z, 4)
        sarcophagus(r, 19, z, 4)
    for z in (4, 16):
        for x in (8, 20):
            r.col(x, z, accent=BONE)
    for z in (3, 9, 15):
        r.hang(SPX - 4, z, 2)
        r.hang(SPX + 3, z, 2)
    candles(r, [(7, 8), (7, 12), (21, 8), (21, 12), (9, 18), (9, 2)])
    webs(r, 14, 1, 1, 23, 19)
    r.chest(2, 19, "north", "common")
    r.chest(22, 1, "south", "rare")
    r.arrows(0, 3, 5, "east")
    r.arrows(24, 3, 15, "west")
    r.spikes(6, 14, 1)
    r.spawn(8, 8, *pack(0), radius=10, spread=4)
    return r.finish(pack_)


def hall_ossuary(pack_, tag, out_pool, wp, ep):
    r = hall(f"f1/ossuary_{tag}", 10, out_pool, wp, ep)
    for z in range(2, 19):                                    # walls of bones: bone block and skulls in niches
        if 8 <= z <= 12:
            continue
        for x in (1, 23):
            for y in (1, 2, 3):
                r.set(x, y, z, BONE if (y + z) % 2 else r.pal.trim)
            r.set(x, 4, z, SKULL.format(r=(z * 5) % 16)) if False else None
    for x, z in ((6, 4), (6, 16), (21, 4), (21, 16)):
        bone_pile(r, x, z, 1)
    for x in (9, 19):
        for z in (4, 16):
            r.col(x, z, accent=BONE)
    for z in (3, 9, 15):
        r.hang(SPX - 4, z, 3)
        r.hang(SPX + 3, z, 3)
    webs(r, 12, 1, 1, 23, 19)
    r.banners((5, 15), y=5, color="gray")
    r.chest(3, 2, "south", "common")
    r.chest(21, 18, "north", "rare")
    r.arrows(0, 3, 16, "east")
    r.arrows(24, 3, 4, "west")
    r.poison(7, 10, 2)
    r.spawn(8, 12, *pack(1, big=True), radius=10, spread=4)
    return r.finish(pack_)


def hall_gate(pack_, tag, out_pool, wp, ep, levers):
    """The spine is shut by a grate across the whole hall (row z=6): solve the lever or the plate puzzle in the southern half."""
    r = hall(f"f1/{'lever' if levers else 'plate'}_{tag}", 9, out_pool, wp, ep)
    p = r.p
    for x in range(1, 24):
        for y in range(1, 8):
            p.set(x, y, 6, r.pal.wall)
    for x in range(SPX - 1, SPX + 2):
        for y in (1, 2, 3):
            p.set(x, y, 6, BARS)
    p.marker(SPX, 2, 6, "door", group="gate", message="The bone grate shudders open.")
    r.reserve(0, 5, 24, 7)
    if levers:
        for (x, z, face, want) in ((1, 15, "east", True), (23, 17, "west", False), (1, 19, "east", True), (23, 13, "west", True)):
            r.lever(x, 2, z, face, want, "gate")
        r.text(SPX, 4, 8, "Four levers: pull the three that are bright. Leave the dark one.", "gold")
        for x, z in ((1, 15), (1, 19), (23, 13)):
            r.set(x, 4, z, SOULL.replace("minecraft:", "minecraft:") + "[hanging=false]") if False else None
    else:
        for (x, z) in ((3, 16), (21, 16), (SPX - 6, 3 + 14)):
            r.plate(x, z, "gate", "minecraft:polished_blackstone_pressure_plate")
        r.text(SPX, 4, 8, "Three stones in the floor. Wake them all.", "gold")
        r.spikes(5, 14, 1)
        r.spikes(19, 18, 1)
    for x in (7, 21):
        for z in (8, 14):
            r.col(x, z, accent=BONE)
    for z in (3, 9, 15):
        r.hang(SPX - 4, z, 2)
    skulls(r, [(2, 8), (22, 8), (2, 12), (22, 18)])
    webs(r, 10, 1, 1, 23, 19)
    r.chest(22, 2, "south", "rare")
    r.chest(3, 2, "south", "common")
    r.arrows(0, 3, 9, "east")
    r.arrows(24, 3, 9, "west")
    r.spawn(SPX, 14, *pack(1), radius=10, spread=4)
    return r.finish(pack_)


# ---------------------------------------------------------------------------------------------------------------------
# Side rooms (enter by the south door) and leaves
# ---------------------------------------------------------------------------------------------------------------------
def room_ossuary(pack_, fl=1):
    r = room("rooms/ossuary", 19, 9, 17)
    for x in (1, 17):
        for z in range(2, 15):
            for y in (1, 2, 3, 4):
                r.set(x, y, z, BONE if (y + z) % 2 else PD)
    for z in range(1, 16):
        for x in range(1, 18):
            pass
    bone_pile(r, 9, 6, 2)
    r.set(9, 3, 6, "minecraft:lit_candle") if False else None
    for x, z in ((4, 4), (14, 4), (4, 12), (14, 12)):
        r.col(x, z, y1=7, accent=BONE)
    r.chest(3, 15, "north", "rare")
    r.chest(15, 15, "north", "common")
    r.hang(9, 3, 2)
    r.hang(9, 11, 2)
    r.arrows(0, 3, 8, "east")
    r.spawn(9, 9, *pack(0)[:1], radius=9, spread=3)
    return r.finish(pack_)


def room_bonepit(pack_, fl=1):
    r = room("rooms/bone_pit", 17, 10, 17)
    for x in range(4, 13):
        for z in range(4, 13):
            if (x, z) in r.keep:
                continue
            h = 3 if max(abs(x - 8), abs(z - 8)) <= 1 else 2 if max(abs(x - 8), abs(z - 8)) == 2 else 1
            for y in range(1, h + 1):
                r.set(x, y, z, BONE if (x + y + z) % 3 else "minecraft:bone_block[axis=x]")
            r.keep.add((x, z))
    r.set(8, 4, 8, "minecraft:soul_campfire[facing=north,lit=true]")
    skulls(r, [(5, 5), (11, 11), (5, 11)], y=2) if False else None
    for x, z in ((2, 2), (14, 2), (2, 14), (14, 14)):
        r.col(x, z, y1=7, accent=BONE)
    r.chest(2, 8, "east", "rare")
    r.chest(14, 8, "west", "common")
    r.hang(8, 2, 2)
    r.hang(8, 14, 2)
    webs(r, 8, 1, 1, 15, 15)
    r.poison(8, 3, 2)
    r.spawn(8, 14, [soldier(4, L(1)), archer(2, L(1)), caller(1, L(1))], [wraith(3, L(2)), risen(3, L(2))], radius=9, spread=3)
    return r.finish(pack_)


def room_sarcophagi(pack_, fl=1):
    r = room("rooms/sarcophagi", 17, 9, 17)
    for z in (4, 8, 12):
        sarcophagus(r, 2, z, 4)
        sarcophagus(r, 11, z, 4)
    candles(r, [(7, 4), (9, 4), (7, 12), (9, 12), (8, 8)])
    r.chest(4, 15, "north", "rare")
    r.chest(2, 15, "north", "common")
    r.hang(8, 6, 2)
    r.hang(8, 12, 2)
    r.spikes(8, 8, 1)
    r.spawn(8, 9, [risen(4, L(1)), wraith(2, L(1))], [risen(3, L(2)), soldier(3, L(2), "Entombed Knight", True)], radius=9, spread=3)
    return r.finish(pack_)


def room_trap_gallery(pack_, fl=1):
    r = room("rooms/trap_gallery", 11, 7, 21)
    for z in (4, 8, 12, 16):
        r.arrows(0, 2, z, "east", radius=8, damage=16, every=40)
        r.arrows(10, 2, z + 2, "west", radius=8, damage=16, every=40)
    r.spikes(5, 6, 1)
    r.spikes(5, 11, 1)
    r.spikes(5, 15, 1)
    r.chest(2, 1, "south", "rare") if False else None
    r.chest(1, 2, "south", "rare")
    r.chest(9, 2, "south", "common")
    r.hang(5, 4, 1)
    r.hang(5, 12, 1)
    r.spawn(5, 10, [soldier(3, L(2)), archer(3, L(2))], radius=8, spread=2)
    return r.finish(pack_)


def room_shrine(pack_, fl=1):
    r = room("rooms/shrine", 15, 9, 15)
    for dx in range(-1, 2):
        for dz in range(-1, 2):
            r.set(7 + dx, 1, 4 + dz, PB)
    r.set(7, 2, 4, "minecraft:soul_campfire[facing=north,lit=true]")
    r.keep |= {(x, z) for x in range(6, 9) for z in range(3, 6)}
    candles(r, [(3, 3), (11, 3), (3, 11), (11, 11), (5, 6), (9, 6)])
    for x, z in ((2, 7), (12, 7)):
        r.col(x, z, y1=6, accent=BONE)
    r.chest(2, 12, "north", "rare")
    r.chest(12, 12, "north", "rare")
    r.hang(7, 8, 2)
    r.spawn(7, 9, [wraith(3, L(2), "Shrine Wraith", True), archer(3, L(1))], radius=8, spread=3)
    return r.finish(pack_)


def room_lever_vault(pack_, fl=1):
    r = room("rooms/lever_vault", 15, 10, 17, tail=False)
    p = r.p
    for x in range(1, 14):
        for y in (1, 2, 3):
            r.set(x, y, 7, r.pal.wall)
    for x in (6, 7, 8):
        for y in (1, 2, 3):
            r.set(x, y, 7, BARS)
    p.marker(7, 2, 7, "door", group="levers", message="The vault grate rises.")
    for z, want in ((10, False), (12, True), (14, True)):
        r.lever(1, 2, z, "east", want, "levers")
    r.text(3, 3, 12, "The first one sleeps. Wake the others.", "gold")
    r.chest(3, 2, "south", "vault")
    r.chest(11, 2, "south", "vault")
    r.chest(7, 2, "south", "rare")
    r.hang(7, 4, 1)
    r.spawn(7, 12, [wraith(3, L(1)), archer(3, L(2))], radius=8, spread=3)
    return r.finish(pack_)


def room_plate_vault(pack_, fl=1):
    r = room("rooms/plate_vault", 15, 9, 17, tail=False)
    p = r.p
    for x in range(1, 14):
        for y in (1, 2, 3):
            r.set(x, y, 7, r.pal.wall)
    for x in (6, 7, 8):
        for y in (1, 2, 3):
            r.set(x, y, 7, BARS)
    p.marker(7, 2, 7, "door", group="plates", message="The vault grate rises.")
    for x in (3, 7, 11):
        r.plate(x, 11, "plates")
    r.text(7, 3, 9, "Three stones, three sleepers.", "gray")
    r.spikes(5, 12, 1)
    r.spikes(9, 12, 1)
    r.chest(3, 2, "south", "vault")
    r.chest(11, 2, "south", "rare")
    r.hang(7, 4, 1)
    r.spawn(7, 13, [soldier(3, L(1)), risen(3, L(1)), caller(1, L(2))], radius=8, spread=3)
    return r.finish(pack_)


def room_charnel(pack_, fl=1):
    r = room("rooms/charnel", 17, 8, 15)
    for pos in ((3, 3), (13, 3), (3, 11), (13, 11), (8, 7)):
        bone_pile(r, pos[0], pos[1], 1)
    webs(r, 14, 1, 1, 15, 13, y=5)
    r.cache(1, 11, 4, 13, (2, 12), "east", r.pal.wall, "The Gravedigger's Cache")
    r.chest(15, 7, "west", "common")
    r.poison(8, 11, 2)
    r.hang(8, 4, 1)
    r.spawn(8, 9, [risen(4, L(1)), soldier(3, L(1))], [wraith(3, L(2)), caller(2, L(2))], radius=8, spread=3)
    return r.finish(pack_)


ROOMS = [(room_ossuary, 3), (room_bonepit, 3), (room_sarcophagi, 3), (room_trap_gallery, 2), (room_shrine, 2), (room_lever_vault, 2),
         (room_plate_vault, 2), (room_charnel, 2)]


def leaf(path, w, d):
    r = Room(ST, path, w, 7, d, 1)
    r.shell()
    r.door("south", "tail_in")
    r.lane("x")
    return r


def leaf_urns(pack_, fl=1):
    r = leaf("leaves/urns", 9, 9)
    for x in (2, 6):
        r.set(x, 1, 2, "minecraft:decorated_pot")
        r.keep.add((x, 2))
    r.chest(4, 1, "south", "common")
    r.hang(4, 4, 1)
    r.spawn(4, 5, [soldier(4, L(1)), archer(2, L(1))], radius=7, spread=2)
    return r.finish(pack_)


def leaf_nook(pack_, fl=1):
    r = leaf("leaves/nook", 9, 11)
    r.cache(1, 1, 4, 4, (2, 2), "south", CDB, "The Robbed Grave")
    r.hang(4, 6, 1)
    r.spikes(6, 6, 1)
    r.spawn(4, 6, [risen(4, L(1)), wraith(2, L(1))], radius=7, spread=2)
    return r.finish(pack_)


def leaf_grave(pack_, fl=1):
    r = leaf("leaves/grave", 11, 9)
    sarcophagus(r, 2, 2, 3)
    r.poison(8, 4, 1)
    r.chest(9, 2, "south", "rare")
    r.hang(5, 4, 1)
    r.spawn(5, 5, [wraith(3, L(2)), soldier(2, L(1)), archer(1, L(1))], radius=7, spread=2)
    return r.finish(pack_)


LEAVES = [leaf_urns, leaf_nook, leaf_grave]


# ---------------------------------------------------------------------------------------------------------------------
# Lanes: solid blocks cut by 3-wide corridors (crossroads, tees, straights), the stone of the labyrinth
# ---------------------------------------------------------------------------------------------------------------------
def lane_base(path, w, h, d, rects, inkind):
    r = Room(ST, path, w, h, d, 1)
    p, pal = r.p, r.pal
    p.fill(0, 0, 0, w - 1, h - 1, d - 1, pal.wall)
    for x in range(w):
        for z in range(d):
            p.set(x, 0, z, pal.inlay if r.rng.random() < .12 else pal.floor)
    for x1, z1, x2, z2 in rects:
        for x in range(x1, x2 + 1):
            for z in range(z1, z2 + 1):
                for y in range(1, h - 1):
                    p.set(x, y, z, AIR)
    r.door("south", inkind, at=w // 2)
    return r


def lane_dress(r, rng, cells, every=6):
    p = r.p
    for i, (x, z) in enumerate(cells):
        if i % every == 3 and p.get(x, r.h - 2, z) == AIR:
            p.set(x, r.h - 2, z, SOULL + "[hanging=true]")
        roll = rng.random()
        if roll < .08:
            r.set(x, 1, z, SKULL.format(r=rng.randrange(16)))
        elif roll < .12:
            r.set(x, 1, z, "minecraft:candle[candles=2,lit=true]")
        elif roll < .22:
            r.set(x, r.h - 2, z, "minecraft:cobweb")


def lane_cross(pack_, inkind="tail_in", name="lanes/cross"):
    r = lane_base(name, 21, 6, 21, [(9, 1, 11, 19), (1, 9, 19, 11)], inkind)
    r.door("north", "tail_out", pool=f"{CAT}/lanes2", at=10)
    r.door("west", "tail_out", pool=f"{CAT}/leaves", at=10)
    r.door("east", "leaf_out", pool=f"{CAT}/leaves", at=10)
    lane_dress(r, r.rng, [(x, 9 + dz) for x in range(1, 20) for dz in (0, 2)] + [(9 + dx, z) for z in range(1, 20) for dx in (0, 2)])
    r.spikes(10, 10, 1)
    r.arrows(8, 2, 4, "east", radius=8)
    r.arrows(12, 2, 16, "west", radius=8)
    r.arrows(4, 2, 8, "south", radius=8)
    r.chest(9, 17, "east", "common")
    r.spawn(10, 5, [soldier(4, L(1)), archer(2, L(1))], radius=7, spread=1)
    return r.finish(pack_)


def lane_tee(pack_, inkind="tail_in", name="lanes/tee"):
    r = lane_base(name, 21, 6, 15, [(9, 3, 11, 13), (1, 3, 19, 5)], inkind)
    r.door("west", "tail_out", pool=f"{CAT}/lanes2", at=4)
    r.door("east", "leaf_out", pool=f"{CAT}/leaves", at=4)
    lane_dress(r, r.rng, [(x, 3 + dz) for x in range(1, 20) for dz in (0, 2)] + [(9 + dx, z) for z in range(6, 14) for dx in (0, 2)])
    r.poison(10, 8, 1)
    r.arrows(8, 2, 10, "east", radius=8)
    r.chest(11, 12, "west", "rare") if False else r.chest(9, 12, "east", "rare")
    r.spawn(10, 4, [wraith(3, L(1)), risen(3, L(1))], radius=7, spread=1)
    return r.finish(pack_)


def lane_straight(pack_, inkind="tail_in", name="lanes/straight", out_pool=None, out_kind="tail_out"):
    r = lane_base(name, 11, 6, 25, [(4, 1, 6, 23)], inkind)
    r.door("north", out_kind, pool=out_pool or f"{CAT}/leaves", at=5)
    lane_dress(r, r.rng, [(x, z) for z in range(1, 24) for x in (4, 6)])
    r.spikes(5, 8, 1)
    r.spikes(5, 16, 1)
    r.arrows(3, 2, 12, "east", radius=8)
    r.arrows(7, 2, 18, "west", radius=8)
    r.chest(4, 20, "east", "common")
    r.spawn(5, 12, [soldier(3, L(1)), archer(3, L(1))], radius=7, spread=1)
    return r.finish(pack_)


LANES = [lane_cross, lane_tee, lane_straight]


# ---------------------------------------------------------------------------------------------------------------------
# The sealed stair (start piece "entrance"): rock around a square-spiral stair; a bone chamber at the top
# ---------------------------------------------------------------------------------------------------------------------
def build_entrance(pack_):
    Wd, H = 25, 24
    r = Room(ST, "entrance", Wd, H, Wd, 1)
    p = r.p
    sh = Shaft(r, THEME, deep=True)
    sh.fill_rock(H - 1)
    landings, legs = sh.helix([(9, 9), (17, 9), (17, 17), (9, 17)], 18)
    assert landings[-1][2] == 0, landings
    for z in range(19, 24):                                    # the exit corridor south of the bottom landing
        for x in (8, 9, 10):
            p.set(x, 0, z, DT if (x + z) % 3 else PD)
            for y in range(1, 5):
                sh.carve(x, y, z)
    for x in range(2, 11):                                     # the sealed chamber on the top landing (floor y=18)
        for z in range(4, 15):
            p.set(x, 18, z, DT if (x + z) % 4 else PD)
            for y in range(19, 23):
                sh.carve(x, y, z)
    sh.line()
    for x in range(3, 10):                                     # a bier of bone and candles, skull niches along the west wall
        p.set(x, 19, 12, BONE)
    for x in (4, 6, 8):
        p.set(x, 20, 12, f"minecraft:candle[candles=3,lit=true]")
    for z in (6, 8, 10):
        p.set(2, 19, z, SKULL.format(r=4))
    p.set(5, 19, 6, "minecraft:cobweb")
    p.set(9, 22, 5, "minecraft:cobweb")
    for x, z in ((4, 6), (8, 6), (4, 13)):
        p.set(x, 22, z, SOULL + "[hanging=true]")
    for z in (7, 9, 11):                                       # a cracked, shallow west wall: dig here
        p.set(1, 20, z, CDB)
    p.sign(6, 20, 4, ["SEALED", "The dead keep", "their own counsel."], block="minecraft:crimson_wall_sign[facing=south]", color="white", glow=True)
    p.chest(10, 19, 5, "west", "skyrpg_world:chests/catacombs_common", tier="common")
    p.marker(6, 19, 9, "dungeon", dungeon=DUN)
    # landings: packs and a chest on the way down
    lo = [(x, z, y) for x, z, y in landings]
    r.spawn(lo[1][0], lo[1][1], [soldier(4, L(0)), archer(2, L(0))], [wraith(3, L(1)), risen(3, L(1))], radius=9, spread=2, y=lo[1][2] + 1)
    r.spawn(lo[2][0], lo[2][1], [risen(4, L(1)), soldier(3, L(1))], [wraith(2, L(1), "Stair Warden", True), archer(3, L(1))], radius=9, spread=2, y=lo[2][2] + 1)
    r.chest(lo[1][0] + 1, lo[1][1] - 1, "south", "common", y=lo[1][2] + 1) if False else None
    r.chest(19, 7, "east", "common", y=lo[1][2] + 1) if False else None
    cx, cz, yf = legs[1][1][len(legs[1][1]) // 2]
    r.arrows(cx + 2, yf + 2, cz, "west", radius=8, damage=14, every=50)
    r.door("south", "spine_out", pool=f"{CAT}/s1", at=9, y=1)
    return r.finish(pack_)


# ---------------------------------------------------------------------------------------------------------------------
# The Bone Warden's chamber (a dead end off the spine, one of three)
# ---------------------------------------------------------------------------------------------------------------------
def build_warden(pack_):
    w, h, d = 21, 11, 21
    r = Room(ST, "wardens/chamber", w, h, d, 1)
    r.shell()
    r.ribs(7, 4)
    p = r.p
    cx = w // 2
    r.door("south", "side_in")
    r.door("north", "tail_out", pool=f"{CAT}/lanes")
    r.lane("x")
    for x in range(cx - 4, cx + 5):                           # a dais of bone and polished blackstone
        for z in range(6, 15):
            dist = max(abs(x - cx), abs(z - 10))
            if dist <= 4 and (abs(x - cx) + abs(z - 10)) <= 6:
                p.set(x, 0, z, PB if dist <= 2 else CHD)
    r.reserve(cx - 4, 5, cx + 4, 15)
    for x, z in ((4, 5), (16, 5), (4, 15), (16, 15), (cx, 3)):
        r.col(x, z, y1=h - 4, accent=BONE)
    for x, z in ((2, 2), (18, 2), (2, 18), (18, 18)):
        r.brazier(x, z)
    for z in (4, 10, 16):
        r.hang(cx - 5, z, 3)
        r.hang(cx + 5, z, 3)
    bone_pile(r, 3, 10, 1)
    bone_pile(r, 17, 10, 1)
    r.banners((6, 14), y=6, color="black")
    webs(r, 10, 1, 1, 19, 19)
    r.chest(2, 19, "north", "rare")
    r.chest(18, 19, "north", "rare")
    r.arrows(0, 3, 10, "east", radius=11, damage=17)
    r.arrows(20, 3, 10, "west", radius=11, damage=17)
    r.spikes(cx - 6, 14, 1)
    r.spikes(cx + 6, 6, 1)
    r.spawn(cx, 17, [soldier(4, L(2)), archer(3, L(2))], [wraith(3, L(2)), risen(3, L(2))], radius=9, spread=3)
    p.marker(cx, 2, 10, "boss", boss="skyrpg_world:bone_warden", radius=13, group="warden")
    return r.finish(pack_)


# ---------------------------------------------------------------------------------------------------------------------
# The tomb gate (three bone doors, one Ossuary Key each) and the Bonelord's Crypt
# ---------------------------------------------------------------------------------------------------------------------
def build_gate(pack_):
    w, h, d = 15, 8, 21
    r = Room(ST, "tomb/gate", w, h, d, 2)
    r.shell()
    p = r.p
    cx = w // 2
    r.door("south", "spine_in")
    r.door("north", "spine_out", pool=f"{CAT}/crypt")
    r.lane("x")
    for n, z in enumerate((15, 9, 3), 1):
        for x in range(1, w - 1):
            for y in range(1, h - 1):
                p.set(x, y, z, r.pal.wall)
        for x in (cx - 1, cx, cx + 1):
            for y in (1, 2, 3):
                p.set(x, y, z, BONE)
        p.marker(cx, 2, z, "key_door", key="skyrpg_world:ossuary_key",
                 message=f"A seal of bone. It takes an Ossuary Key ({n} of 3).")
        for x in (cx - 2, cx + 2):                            # skull sockets beside each door
            p.set(x, 2, z + 1, SKULL.format(r=8)) if False else None
        r.set(cx - 3, 3, z + 1, f"minecraft:crimson_wall_sign[facing=south]") if False else None
        r.sign(cx - 3, 2, z + 1, [f"SEAL {n}", "Press an Ossuary", "Key to the bone."], "south")
        r.keep |= {(x, z) for x in range(w)}
    for z in (17, 11, 5):                                      # chambers between the seals: columns, candles, a pack each
        for x in (3, w - 4):
            r.col(x, z, y1=h - 3, accent=BONE)
        r.hang(cx, z, 2)
        candles(r, [(cx - 3, z + 1), (cx + 3, z + 1)])
    bone_pile(r, 2, 12, 0)
    bone_pile(r, w - 3, 6, 0)
    r.spawn(cx, 13, [wraith(3, L(2, 2), "Seal Guard", True), soldier(3, L(2, 2))], radius=8, spread=2)
    r.spawn(cx, 6, [wraith(3, L(2, 2), "Seal Guard", True), archer(3, L(2, 2))], [risen(3, L(2, 2)), caller(2, L(2, 2))], radius=8, spread=2)
    r.spikes(cx, 18, 1)
    r.chest(2, 18, "north", "rare") if False else None
    r.chest(w - 3, 1, "south", "rare")
    r.text(cx, 5, 18, "Three seals. Three keys. Three wardens.", "gold")
    return r.finish(pack_)


def build_sanctum(pack_):
    w, d, h = 31, 31, 18
    r = Room(ST, "tomb/crypt", w, h, d, 2)
    r.shell(inlay=False)
    p = r.p
    cx, cz = w // 2, 21
    r.door("south", "spine_in")
    r.reserve(cx - 1, 24, cx + 1, d - 1)
    top = h - 2
    for x in range(1, w - 1):                                 # the hoard alcove behind the Bonelord's throne (x 11..19, z 1..5)
        for y in range(1, top):
            if x <= 10 or x >= 20:
                p.set(x, y, 7, PBB) if False else None
    for z in range(1, 7):
        for y in range(1, top):
            p.set(10, y, z, PBB)
            p.set(20, y, z, PBB)
    for x in range(11, 20):
        for y in range(1, top):
            p.set(x, y, 6, PBB)
    for x in (14, 15, 16):
        for y in (1, 2, 3):
            p.set(x, y, 6, "minecraft:crying_obsidian")
    p.marker(15, 2, 6, "door", group="bonelord", message="The Bonelord's hoard grinds open.")
    for x in range(11, 20):
        for z in range(1, 6):
            for y in range(1, top):
                p.set(x, y, z, AIR if y < 8 else PBB)
    p.chest(12, 1, 1, "south", "skyrpg_world:chests/catacombs_vault", tier="vault")
    p.chest(18, 1, 1, "south", "skyrpg_world:chests/catacombs_vault", tier="vault")
    p.chest(15, 1, 1, "south", "skyrpg_world:chests/catacombs_secret", tier="secret")
    p.marker(15, 1, 3, "secret", name="The Bonelord's Hoard")
    for x in (13, 17):
        p.set(x, 1, 1, "minecraft:gold_block")
    p.set(15, 6, 3, SOULL + "[hanging=true]")
    # the throne: a bone-white seat against the wall, flanked by soul fire, at the end of the lane (z=7..9, x 12..18)
    for x in range(12, 19):
        p.set(x, 0, 7, CHD)
        p.set(x, 0, 8, CHD)
    for x in (12, 18):
        p.set(x, 1, 8, "minecraft:polished_blackstone_brick_wall")
        p.set(x, 2, 8, "minecraft:soul_campfire[facing=north,lit=true]")
    for y in range(1, 5):
        p.set(15, y, 8, BONE)
    p.set(15, 5, 8, SKULL.format(r=0))
    p.set(14, 3, 8, BONE)
    p.set(16, 3, 8, BONE)
    r.reserve(10, 1, 20, 8)
    r.sign(cx - 4, 2, 7, ["The hoard is", "sealed in stone.", "It opens for the", "Bonelord's end."], "south") if False else None
    # ribs: great bone arches across the arena
    for z in (12, 18, 24, 29):
        for x in range(1, w - 1):
            if x in (1, w - 2) or abs(x - cx) > 12:
                for y in range(1, top):
                    p.set(x, y, z, BONE) if (y % 3 != 0 and x in (1, w - 2)) else None
    for x, z in ((6, 12), (24, 12), (6, 29), (24, 29), (9, 18), (21, 18)):
        r.col(x, z, y1=top - 1, accent=BONE)
    for x, z in ((cx - 7, cz), (cx + 7, cz), (cx, cz - 7), (cx, cz + 7), (9, 26), (21, 26), (9, 16), (21, 16)):
        r.hang(x, z, 4)
    for x, z in ((4, 21), (26, 21), (4, 14), (26, 14)):
        r.brazier(x, z)
    r.banners((11, 17, 23, 29), y=9, color="black")
    r.spikes(9, 13, 1)
    r.spikes(21, 13, 1)
    r.arrows(0, 4, 12, "east", radius=14, damage=20)
    r.arrows(w - 1, 4, 12, "west", radius=14, damage=20)
    r.rubble(8, 3, 10, w - 4, d - 3, [BONE, "minecraft:bone_block[axis=x]", "minecraft:cobblestone_slab[type=bottom]", CDB])
    p.marker(cx, 2, cz, "boss", boss="skyrpg_world:bonelord", radius=18, group="bonelord")
    return r.finish(pack_, exempt=("secret",))


# ---------------------------------------------------------------------------------------------------------------------
# Loot
# ---------------------------------------------------------------------------------------------------------------------
HELL_INGOT = M + "hellforged_ingot"
LUMEN, ATT_LUMEN, DUSK, PRISM_F, PRISM, HEART = (M + "lumenite_crystal", M + "attuned_lumenite", M + "duskquartz_shard",
                                                M + "prism_fragment", M + "prism", M + "heartstone")
DREAD = W + "dread_crystal"
EGG_RARE = [it("skyrpg_pets:pup_egg", 1, 1, 1), it("skyrpg_pets:mole_egg", 1, 1, 1)]
GOLDEN_EGG = "skyrpg_pets:golden_pet_egg"
KEY = W + "ossuary_key"


def loot(pack_):
    common = [it("minecraft:bone", 8, 6, 16), it("minecraft:bone_meal", 6, 4, 10), it("minecraft:gold_ingot", 6, 3, 8),
              it("minecraft:experience_bottle", 6, 3, 7), it("skyrpg_enchants:runic_dust", 6, 3, 6), it(C + "marrow_shard", 7, 2, 5),
              it(DUSK, 6, 2, 5), it(LUMEN, 5, 1, 3), it("minecraft:amethyst_shard", 5, 4, 8), it("minecraft:candle", 4, 2, 5), book(3)]
    pack_.loot_table("chests/catacombs_common", table(
        lpool(rolls(4, 6), common),
        lpool(1, [it(C + "dread_fang", 3, 1, 1), it(DREAD, 1, 1, 1), it(PRISM_F, 2, 1, 1), it(C + "valor_mark", 3, 1, 1)], .24)))
    rare = [it("minecraft:diamond", 6, 2, 5), it("minecraft:gold_ingot", 6, 6, 12), it("minecraft:experience_bottle", 6, 5, 10),
            it("skyrpg_enchants:runic_dust", 8, 4, 9), it(C + "marrow_shard", 7, 4, 8), it(DUSK, 7, 3, 6), it(LUMEN, 7, 2, 4),
            it(PRISM_F, 5, 1, 2), it(HELL_INGOT, 3, 1, 1), it(DREAD, 3, 1, 1), it(C + "dread_fang", 4, 1, 2), book(5)]
    pack_.loot_table("chests/catacombs_rare", table(
        lpool(rolls(5, 7), rare),
        lpool(1, [it(C + "elite_trophy", 3, 1, 1), it(C + "valor_mark", 4, 1, 2), it(HEART, 2, 1, 1), it(DREAD, 2, 1, 1)], .55),
        lpool(1, [it(KEY, 1, 1, 1)], .05),                                     # a spare Ossuary Key now and then
        lpool(1, [it(C + "nightmare_core", 1, 1, 1)], .07),
        lpool(1, EGG_RARE, .035)))
    secret = [it("minecraft:diamond", 7, 3, 6), it("minecraft:gold_ingot", 7, 8, 16), it("skyrpg_enchants:runic_dust", 9, 5, 11),
              it(C + "marrow_shard", 7, 5, 9), it(DUSK, 8, 4, 8), it(LUMEN, 8, 3, 5), it(ATT_LUMEN, 3, 1, 1), it(PRISM_F, 7, 1, 3),
              it(HELL_INGOT, 5, 1, 2), it(DREAD, 4, 1, 2), book(6)]
    pack_.loot_table("chests/catacombs_secret", table(
        lpool(rolls(5, 8), secret),
        lpool(1, [it(PRISM, 3, 1, 1), it(HEART, 3, 1, 2), it(C + "valor_seal", 3, 1, 1), it(C + "elite_trophy", 3, 1, 2)]),
        lpool(1, [it(KEY, 1, 1, 1)], .10),
        lpool(1, [it(C + "nightmare_core", 1, 1, 1)], .10),
        lpool(1, [it(GOLDEN_EGG, 1, 1, 1)], .016),
        lpool(1, EGG_RARE, .05)))
    vault = [it("minecraft:diamond", 8, 4, 8), it("minecraft:gold_ingot", 7, 10, 20), it("skyrpg_enchants:runic_dust", 9, 6, 14),
             it(C + "marrow_shard", 7, 6, 12), it(DUSK, 8, 4, 8), it(LUMEN, 8, 3, 6), it(ATT_LUMEN, 4, 1, 2), it(PRISM_F, 8, 2, 4),
             it(HELL_INGOT, 6, 1, 3), it(DREAD, 5, 1, 2), it(C + "valor_seal", 4, 1, 2), it(C + "elite_trophy", 5, 1, 3), book(8)]
    pack_.loot_table("chests/catacombs_vault", table(
        lpool(rolls(6, 8), vault),
        lpool(1, [it(PRISM, 2, 1, 1), it(HEART, 3, 1, 2)], .6),
        lpool(1, [it(C + "nightmare_core", 1, 1, 1)], .15),
        lpool(1, [it(GOLDEN_EGG, 1, 1, 1)], .022),
        lpool(1, EGG_RARE, .06),
        lpool(1, [it("minecraft:enchanted_golden_apple", 1, 1, 1)], .15)))
    pack_.loot_table("bosses/bone_warden", table(
        lpool(1, [it(KEY, 1)]),                                               # exactly one Ossuary Key per warden
        lpool(rolls(2, 4), [it("minecraft:diamond", 6, 2, 4), it(C + "marrow_shard", 7, 5, 10), it(DUSK, 6, 3, 6), it(LUMEN, 6, 2, 4),
                            it("minecraft:experience_bottle", 6, 4, 9), it("skyrpg_enchants:runic_dust", 8, 5, 10), book(4)]),
        lpool(1, [it(PRISM_F, 1, 1, 2)], .45),
        lpool(1, [it(HELL_INGOT, 1, 1, 2)], .30),
        lpool(1, [it(DREAD, 1, 1, 1)], .25),
        lpool(1, [it(C + "elite_trophy", 1, 1, 1)], .35),
        lpool(1, [it(C + "valor_mark", 1, 1, 2)], .30),
        lpool(1, [it(C + "nightmare_core", 1, 1, 1)], .08),
        lpool(1, EGG_RARE, .04)))
    pack_.loot_table("bosses/bonelord", table(
        lpool(1, [it(W + "bonelord_phylactery", 1, 1, 1)]),                    # the trophy: always one ...
        lpool(1, [it(W + "bonelord_phylactery", 1, 1, 1)], .25),               # ... and a quarter of the time a second
        lpool(1, [it(HELL_INGOT, 1, 3, 5)]),
        lpool(1, [it(PRISM_F, 1, 3, 5)]),
        lpool(1, [it(LUMEN, 1, 4, 8)]),
        lpool(1, [it(DUSK, 1, 6, 12)]),
        lpool(1, [it(C + "marrow_shard", 1, 8, 16)]),
        lpool(rolls(5, 7), [it("minecraft:diamond", 6, 4, 8), it("minecraft:gold_ingot", 6, 12, 24), it("skyrpg_enchants:runic_dust", 9, 8, 16),
                            it(DREAD, 5, 1, 3), it(ATT_LUMEN, 3, 1, 2), it("minecraft:experience_bottle", 7, 8, 16),
                            it(C + "dread_fang", 5, 1, 3), book(5)]),
        lpool(1, [it(PRISM, 1, 1, 1)], .35),
        lpool(1, [it(HEART, 1, 1, 2)], .35),
        lpool(1, [it(C + "valor_seal", 1, 1, 2)], .50),
        lpool(1, [it(C + "elite_trophy", 1, 1, 2)], .60),
        lpool(1, [it(C + "nightmare_core", 1, 1, 1)], .25),
        lpool(1, [it(GOLDEN_EGG, 1, 1, 1)], .045),
        lpool(1, EGG_RARE, .10)))
    pack_.loot_table("rewards/bonelord", table(
        lpool(rolls(5, 7), [it("skyrpg_enchants:runic_dust", 9, 8, 16), it("minecraft:diamond", 7, 5, 10), it("minecraft:experience_bottle", 7, 8, 16),
                            it(DUSK, 8, 5, 10), it(LUMEN, 8, 4, 8), it(PRISM_F, 8, 2, 5), it(HELL_INGOT, 6, 1, 3), it(DREAD, 5, 1, 3),
                            it(C + "marrow_shard", 7, 6, 12), it(C + "valor_seal", 5, 1, 2), it(C + "elite_trophy", 5, 1, 3),
                            it(HEART, 4, 1, 2), it(ATT_LUMEN, 3, 1, 2), book(6)]),
        lpool(1, [it(PRISM, 1, 1, 1)], .28),
        lpool(1, [it(C + "nightmare_core", 1, 1, 1)], .22),
        lpool(1, [it(GOLDEN_EGG, 1, 1, 1)], .05),
        lpool(1, EGG_RARE, .12)))


def tome_drops(pack_):
    def drops(tag, entries):
        pack_.json(f"skyrpg/tome_drops/{tag}.json", {
            "tag": f"skyrpg_boss=skyrpg_world:{tag}",
            "drops": [{"enchant": f"skyrpg_enchants:{e}", "level": lv, "chance": ch, "min_tier": 0} for e, lv, ch in entries]})
    drops("bonelord", [("soulrend", 4, 0.15), ("soulrend", 5, 0.06), ("soul_siphon", 4, 0.14), ("reapers_mark", 5, 0.10),
                       ("bloodthirst", 5, 0.10), ("cleave", 6, 0.12), ("vitality", 7, 0.12), ("hallowed_edge", 7, 0.10),
                       ("aegis", 7, 0.10), ("rift_echo", 5, 0.08), ("final_stand", 5, 0.06)])
    drops("bone_warden", [("soulrend", 3, 0.10), ("soul_siphon", 3, 0.10), ("hallowed_edge", 5, 0.08), ("aegis", 5, 0.08),
                          ("vitality", 5, 0.08), ("cleave", 4, 0.08)])


def bosses(pack_):
    pack_.json("skyrpg/dungeon_bosses/bone_warden.json", {
        "name": "A Bone Warden", "entity": "skyrpg_combat:bone_giant", "dungeon": DUN, "tier": "champion", "level": 60,
        "health": 15000, "damage": 40, "speed": 0.26, "scale": 1.3, "knockback_resistance": 0.9, "bar_color": "white",
        "health_per_player": 0.6, "coins": [11000, 14500], "delving_xp": 3800,
        "intro": "A Bone Warden unfolds from its alcove, a key of grey bone hanging from its ribs.",
        "death": "The Warden collapses into a heap of bones. An Ossuary Key rattles loose from the pile.",
        "max_minions": 4, "loot": "skyrpg_world:bosses/bone_warden", "reset_ticks": 72000,
        "first_clear": {"coins": 12000, "xp": {"skyrpg_skills:combat": 5800}},
        "moves": [
            {"type": "slam", "every": 190, "radius": 6, "damage": 34, "knockup": 0.9, "windup": 22, "particle": "minecraft:smoke",
             "warn": "The Warden brings a fist of bone down!"},
            {"type": "leap", "every": 290, "damage": 30, "radius": 4, "range": 16, "warn": "The Warden lurches across the room!"},
            {"type": "cloud", "every": 330, "effect": "minecraft:wither", "amplifier": 1, "duration": 100, "radius": 3.5, "on": "target",
             "warn": "A grave-cloud rolls off the Warden!"},
            {"type": "ward", "every": 800, "below": 0.5, "duration": 100, "reduction": 0.75, "warn": "Bone plates lock over the Warden!"},
            {"type": "summon", "every": 480, "max": 4,
             "mobs": [{"type": "minecraft:skeleton", "count": 2, "level": 59, "name": "Bone Soldier",
                       "equipment": {"mainhand": "minecraft:iron_sword", "head": "minecraft:iron_helmet"}}],
             "warn": "The Warden calls the dead from the walls!"}]})
    pack_.json("skyrpg/dungeon_bosses/bonelord.json", {
        "name": "The Bonelord", "entity": "skyrpg_combat:bone_giant", "dungeon": DUN, "level": 62, "health": 60000, "damage": 70,
        "speed": 0.27, "scale": 2.0, "knockback_resistance": 1.0, "bar_color": "purple", "health_per_player": 0.6,
        "equipment": {"head": "minecraft:golden_helmet"},
        "coins": [48000, 62000], "delving_xp": 24000,
        "intro": "The throne of bones unfolds and a crowned giant stands. 'Three keys, and still you come. The Catacombs have waited a long time for fresh bones.'",
        "death": "The Bonelord's crown rolls away and the great frame comes apart rib by rib. His phylactery lies glowing among the bones, and the hoard unseals.",
        "max_minions": 8, "loot": "skyrpg_world:bosses/bonelord", "reward_loot": "skyrpg_world:rewards/bonelord",
        "first_clear": {"coins": 110000, "xp": {"skyrpg_skills:combat": 30000}},
        "reset_ticks": 72000,
        "phases": [{"below": 0.66, "message": "The Bonelord howls. Bones tear out of the walls and the dead march!"},
                   {"below": 0.33, "message": "The Bonelord's crown burns green. The floor of the crypt begins to rot!"}],
        "moves": [
            {"type": "slam", "every": 200, "radius": 8, "damage": 48, "knockup": 1.0, "windup": 24, "particle": "minecraft:smoke",
             "warn": "The Bonelord raises a fist the size of a cart!"},
            {"type": "volley", "every": 230, "projectile": "arrow", "count": 8, "damage": 20, "spread": 0.22,
             "warn": "The Bonelord flings a volley of ribs!"},
            {"type": "leap", "every": 320, "damage": 42, "radius": 5, "range": 22, "warn": "The Bonelord leaps, shaking the crypt!"},
            {"type": "cloud", "every": 320, "effect": "minecraft:wither", "amplifier": 1, "duration": 120, "radius": 4.0, "on": "target",
             "warn": "A grave-cloud boils out of the Bonelord's chest!"},
            {"type": "summon", "every": 460, "below": 0.66, "max": 6,
             "mobs": [{"type": "minecraft:wither_skeleton", "count": 2, "level": 60, "name": "Tomb Wraith",
                       "equipment": {"mainhand": "minecraft:stone_sword", "head": "minecraft:iron_helmet"}},
                      {"type": "minecraft:skeleton", "count": 3, "level": 60, "name": "Crypt Archer",
                       "equipment": {"mainhand": "minecraft:bow", "head": "minecraft:chainmail_helmet"}}],
             "warn": "The dead of the Catacombs rise!"},
            {"type": "pull", "every": 400, "below": 0.66, "radius": 16, "strength": 1.2, "particle": "minecraft:soul",
             "warn": "The Bonelord drags you towards his throne!"},
            {"type": "heal", "every": 540, "below": 0.5, "percent": 0.03, "warn": "The phylactery drinks the crypt's dead!"},
            {"type": "ward", "every": 900, "below": 0.5, "duration": 100, "reduction": 0.7, "warn": "Ribs close like a cage around the Bonelord!"},
            {"type": "molten", "every": 240, "below": 0.33, "count": 14, "radius": 5, "delay": 25, "duration": 120, "damage": 36,
             "warn": "The floor rots away green. Move!"},
            {"type": "shockwave", "every": 300, "below": 0.33, "radius": 11, "damage": 32, "push": 2.0, "particle": "minecraft:soul_fire_flame",
             "warn": "A wave of grave-cold rolls out from the Bonelord!"},
            {"type": "slam", "every": 170, "below": 0.33, "radius": 9, "damage": 54, "knockup": 1.1, "windup": 20, "particle": "minecraft:soul_fire_flame",
             "warn": "The crypt cracks under his fist!"},
            {"type": "enrage", "below": 0.33, "speed": 0.35, "damage": 0.4, "warn": "The Bonelord's crown burns green!"}]})


# ---------------------------------------------------------------------------------------------------------------------
# The structure
# ---------------------------------------------------------------------------------------------------------------------
def build(pack_):
    P = lambda s: f"{CAT}/{s}"  # noqa: E731
    build_entrance(pack_)
    for fl in (1, 2):
        for kind in ("spine", "side", "tail"):
            cap_piece(pack_, ST, kind, fl)
            pack_.pool(P(f"caps_{kind}{fl}"), [(P(f"caps/{kind}{fl}"), 1)])
    # stage 1: warden doors on both sides; stage 2: a warden on the west, side rooms on the east
    stages = {1: (P("s2"), P("wardens"), P("wardens")), 2: (P("gate"), P("wardens"), P("rooms"))}
    elems = {1: [], 2: []}
    for st_, specs in ((1, [("maze", "a", 31), ("maze", "b", 32), ("crypt", "a", 0), ("lever", "a", 1)]),
                       (2, [("maze", "c", 33), ("maze", "d", 34), ("ossuary", "a", 0), ("plate", "a", 1)])):
        out, wp, ep = stages[st_]
        for kind, tag, extra in specs:
            if kind == "maze":
                pc = build_maze(pack_, f"maze_{tag}", extra, out, wp, ep, density=1 + (tag in "bd"))
            elif kind == "crypt":
                pc = hall_crypt(pack_, tag, out, wp, ep)
            elif kind == "ossuary":
                pc = hall_ossuary(pack_, tag, out, wp, ep)
            else:
                pc = hall_gate(pack_, tag, out, wp, ep, levers=(kind == "lever"))
            elems[st_].append((pc.path, 3))
        pack_.pool(P(f"s{st_}"), elems[st_], fallback=P("caps_spine1"))
    leaves = [(fn(pack_).path, 1) for fn in LEAVES]
    pack_.pool(P("leaves"), leaves, fallback=P("caps_tail1"))
    lanes = [(fn(pack_).path, 3) for fn in LANES]
    pack_.pool(P("lanes"), lanes, fallback=P("caps_tail1"))
    straight2 = lane_straight(pack_, name="lanes/straight_b", out_pool=P("leaves"))
    pack_.pool(P("lanes2"), [(straight2.path, 3)] + leaves, fallback=P("caps_tail1"))
    rooms = [(fn(pack_).path, wgt) for fn, wgt in ROOMS]
    corr = lane_straight(pack_, inkind="side_in", name="rooms/corridor", out_pool=P("lanes2"), out_kind="leaf_out")
    pack_.pool(P("rooms"), rooms + [(corr.path, 5)], fallback=P("caps_side1"))
    build_warden(pack_)
    build_gate(pack_)
    build_sanctum(pack_)
    pack_.pool(P("entrance"), [(P("entrance"), 1)])
    pack_.pool(P("wardens"), [(P("wardens/chamber"), 1)], fallback=P("caps_side1"))
    pack_.pool(P("gate"), [(P("tomb/gate"), 1)], fallback=P("caps_spine1"))
    pack_.pool(P("crypt"), [(P("tomb/crypt"), 1)], fallback=P("caps_spine2"))
    pack_.biome_tag(CAT, ["#minecraft:is_overworld"])
    pack_.structure(CAT, start_pool=P("entrance"), biomes=f"#skyrpg_world:has_structure/{CAT}", size=16, max_distance=116,
                    step="underground_structures", adaptation="none", heightmap=None, start_height=-58)
    pack_.structure_set(CAT, 64, 26, 26111206)
    pack_.structure_tag("on_catacomb_maps", [CAT])
    loot(pack_)
    tome_drops(pack_)
    pack_.json("skyrpg/dungeons/catacombs.json", {
        "key": "skyrpg_world:ossuary_key", "name": "The Catacombs", "structure": DUN, "color": "#B8C4B0",
        "icon": "minecraft:wither_skeleton_skull", "level": 62, "boss": "skyrpg_world:bonelord", "reset_ticks": 72000,
        "description": "A labyrinth of crypts under the Magma Rifts, reached by a sealed stair. Hunt three Bone Wardens for their Ossuary Keys, open the tomb and face the Bonelord."})
    bosses(pack_)
    pack_.json("skyrpg/items/ossuary_key.json", {
        "name": "Ossuary Key", "rarity": "rare", "type": "Dungeon Key",
        "description": "A key of grey bone from a Bone Warden in the Catacombs. Each of the three seals of the tomb takes one. Used up when you open it."})
    pack_.json("skyrpg/items/bonelord_phylactery.json", {
        "name": "Bonelord's Phylactery", "rarity": "epic", "type": "Boss Trophy",
        "description": "The glowing vessel of the Bonelord, dropped in the Catacombs. One of the trophies for the Ender Wyrm altar."})


def group(path):
    if path.endswith("/entrance"):
        return "entrance"
    if "/tomb/" in path:
        return "tomb"
    if "/wardens/" in path:
        return "wardens"
    return "labyrinth"


if __name__ == "__main__":
    run_main(build, ST, f"{CAT}/caps/", f"{CAT}/entrance", group)
