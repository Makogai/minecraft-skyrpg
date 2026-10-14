"""The Forgotten Crypt v2 (skyrpg_world:forgotten_crypt): a two-floor burial complex under forests and plains.

Spine (jigsaw, placement_priority 10 so it places first):
  entrance -> f1/spine_1 -> f1/spine_2 -> f1/spine_3 -> f1/champion (arena, gate) -> f1/stairs (descends 10, turns east)
  -> f2/spine_1 -> f2/spine_2 -> f2/spine_3 -> f2/boss (warden hall)
Side branches (priority 0): every spine hall has an east and a west door into  corr_a -> rooms_a -> corr_b -> rooms_b
(rooms_b are leaf variants without side doors).  Doorway convention: floor y=0, jigsaw at y=1 in the middle of the wall,
3x3 opening (y=1..3) carved around it, final_state air.  Dead ends are sealed by 3x3x1 caps.
Floor 1 = "Upper Crypt" (stone bricks, mob Lv 12-16); floor 2 = "Ossuary Depths" (deepslate tiles / polished
blackstone, soul fire, mob Lv 15-19).  Floor 2's floor is 10 blocks lower; all floor-2 side pieces are <= 10 tall.
"""
import sys, pathlib, random, json, shutil
from types import SimpleNamespace as NS
sys.path.insert(0, str(pathlib.Path(__file__).resolve().parents[1]))
from dungeon_kit import Pack, Piece

AIR = "minecraft:air"
SB = "minecraft:stone_bricks"
DB = "minecraft:deepslate_bricks"
DT = "minecraft:deepslate_tiles"
PD = "minecraft:polished_deepslate"
CHISEL = "minecraft:chiseled_deepslate"
PBB = "minecraft:polished_blackstone_bricks"
PB = "minecraft:polished_blackstone"
CPB = "minecraft:chiseled_polished_blackstone"
BONE = "minecraft:bone_block"
BOOKS = "minecraft:bookshelf"
DRIP = "minecraft:pointed_dripstone[thickness=tip,vertical_direction=up,waterlogged=false]"
WATER = "minecraft:water[level=0]"
EMPTY = "minecraft:empty"
P = "forgotten_crypt"

PAL = {
    1: NS(wall=SB, floor=DT, pillar=DB, polish=PD, chisel=CHISEL, base=12, crack="minecraft:cracked_stone_bricks",
          stairs="minecraft:deepslate_brick_stairs", slab="minecraft:deepslate_brick_slab"),
    2: NS(wall=DT, floor=PB, pillar=PBB, polish=PB, chisel=CPB, base=15, crack="minecraft:cracked_deepslate_tiles",
          stairs="minecraft:polished_blackstone_brick_stairs", slab="minecraft:polished_blackstone_brick_slab"),
}

WEATHER = {
    SB: [("minecraft:cracked_stone_bricks", 0.14), ("minecraft:mossy_stone_bricks", 0.18)],
    DB: [("minecraft:cracked_deepslate_bricks", 0.14)],
    DT: [("minecraft:cracked_deepslate_tiles", 0.13)],
    PBB: [("minecraft:cracked_polished_blackstone_bricks", 0.16)],
    "minecraft:stone_brick_stairs": [("minecraft:mossy_stone_brick_stairs", 0.35)],
    "minecraft:cobblestone": [("minecraft:mossy_cobblestone", 0.5)],
}

STATS = {"packs": 0, "wave_packs": 0}


# ---------------------------------------------------------------- basic builders
def new(path, w, h, d, fl):
    pal = PAL[fl]
    p = Piece(f"{P}/f{fl}/{path}", w, h, d)
    p.pal, p.fl, p.base = pal, fl, pal.base
    p.doors = []
    p.rng = random.Random(p.path)
    p.box(0, 0, 0, w - 1, h - 1, d - 1, pal.wall, inside=AIR, floor=pal.floor)
    return p


def shell_piece(path, w, h, d, fl):
    pal = PAL[fl]
    p = Piece(f"{P}/f{fl}/{path}", w, h, d)
    p.pal, p.fl, p.base = pal, fl, pal.base
    p.doors = []
    p.rng = random.Random(p.path)
    return p


def doorway(p, side, at=None, y0=1):
    w, h, d = p.size
    if side in ("north", "south"):
        c = w // 2 if at is None else at
        z = 0 if side == "north" else d - 1
        cells = [(x, z) for x in range(c - 1, c + 2)]
    else:
        c = d // 2 if at is None else at
        x = 0 if side == "west" else w - 1
        cells = [(x, z) for z in range(c - 1, c + 2)]
    for (x, z) in cells:
        for y in range(y0, y0 + 3):
            p.set(x, y, z, AIR)
        p.set(x, y0 + 3, z, p.pal.chisel)
    p.doors.append((side, c))


def link(p, side, name, target=None, pool=EMPTY, at=None, priority=0, y=1):
    doorway(p, side, at, y)
    p.door(side, name, target, pool, at=at, y=y, placement_priority=priority)


def web(p, x, y, z):
    if p.inside(x, y, z) and p.get(x, y, z) in (None, AIR):
        p.set(x, y, z, "minecraft:cobweb")


def webs(p, n):
    w, h, d = p.size
    for _ in range(n * 4):
        if n <= 0:
            break
        x, z = p.rng.randint(1, w - 2), p.rng.randint(1, d - 2)
        y = h - 2 if p.rng.random() < 0.7 else p.rng.randint(1, 2)
        if p.get(x, y, z) == AIR and (x in (1, w - 2) or z in (1, d - 2)):
            p.set(x, y, z, "minecraft:cobweb")
            n -= 1


def candle(p, x, y, z, n=2):
    p.set(x, y, z, f"minecraft:candle[candles={n},lit=true]")


def skull(p, x, y, z, rot=0, kind="skeleton_skull"):
    p.set(x, y, z, f"minecraft:{kind}[rotation={rot}]")


def lantern(p, x, ytop, z, drop=2):
    for y in range(ytop, ytop - drop, -1):
        p.set(x, y, z, "minecraft:iron_chain[axis=y]")
    p.set(x, ytop - drop, z, "minecraft:soul_lantern[hanging=true]")


def pillar(p, x, z, y1, y2):
    p.fill(x, y1, z, x, y2, z, p.pal.pillar)
    p.set(x, y2, z, p.pal.chisel)
    p.set(x, y1, z, p.pal.polish)


def coffin(p, x, z, along="z", y=1):
    for i in range(3 if along == "z" else 2):
        cx, cz = (x, z + i) if along == "z" else (x + i, z)
        p.set(cx, y, cz, "minecraft:spruce_planks")
        p.set(cx, y + 1, cz, "minecraft:dark_oak_slab[type=bottom]")


def banner(p, x, y, z, facing, color="black"):
    p.set(x, y, z, f"minecraft:{color}_wall_banner[facing={facing}]", {"id": "minecraft:banner"})


def bars(p, x, y, z, axis):
    if axis == "x":
        p.set(x, y, z, "minecraft:iron_bars[east=true,west=true,north=false,south=false]")
    else:
        p.set(x, y, z, "minecraft:iron_bars[north=true,south=true,east=false,west=false]")


def slit(p, x, y, z, axis, radius=10):
    """An arrow slit: iron bars in the wall with an arrows trap behind them. axis = the way the wall runs."""
    bars(p, x, y, z, axis)
    p.marker(x, y, z, "trap", type="arrows", radius=radius, every=50, damage=4)


def spikes(p, x, z, radius=1):
    for dx in (0, 1):
        if p.inside(x + dx, 1, z):
            p.set(x + dx, 1, z, DRIP)
    p.marker(x, 1, z, "trap", type="spikes", radius=radius, every=40, damage=4)


def brazier(p, x, z, y=1, radius=3, trap=True):
    p.set(x, y, z, p.pal.polish)
    p.set(x, y + 1, z, "minecraft:soul_campfire[lit=true,facing=north]")
    if trap:
        p.marker(x, y + 1, z, "trap", type="flames", radius=radius, every=100, damage=3)


def soul_patch(p, x, z):
    p.set(x, 0, z, "minecraft:soul_soil")
    p.set(x, 1, z, "minecraft:soul_fire")


def fin(p, n_webs=6):
    webs(p, n_webs)
    p.weather("w", WEATHER)
    return p


# ---------------------------------------------------------------- mobs
def mob(p, t, n, k, name, main=None, head=None, chest=None, legs=None, feet=None, elite=False):
    m = {"type": t, "count": n, "level": p.base + k, "elite": elite, "name": name}
    eq = {slot: v for slot, v in (("mainhand", main), ("head", head), ("chest", chest), ("legs", legs), ("feet", feet)) if v}
    if eq:
        m["equipment"] = eq
    return m


def archer(p, n, k=0):
    return mob(p, "minecraft:skeleton", n, k, "Crypt Archer", "minecraft:bow",
               "minecraft:chainmail_helmet" if p.fl == 1 else "minecraft:iron_helmet")


def knight(p, n, k=2):
    return mob(p, "minecraft:skeleton", n, k, "Bone Knight",
               "minecraft:stone_sword" if p.fl == 1 else "minecraft:iron_sword", "minecraft:iron_helmet",
               "minecraft:chainmail_chestplate" if p.fl == 1 else "minecraft:iron_chestplate")


def dead(p, n, k=0):
    return mob(p, "minecraft:zombie", n, k, "Restless Dead", "minecraft:stone_sword" if p.fl == 1 else "minecraft:iron_sword",
               "minecraft:chainmail_helmet" if p.fl == 2 else None)


def husk(p, n, k=1):
    return mob(p, "minecraft:husk", n, k, "Dust Walker")


def stray(p, n, k=1):
    return mob(p, "minecraft:stray", n, k, "Frostbitten Dead", "minecraft:bow")


def wither(p, n, k=2):
    if p.fl == 1:
        return knight(p, n, k)
    return mob(p, "minecraft:wither_skeleton", n, k, "Ashen Guard", "minecraft:stone_sword", "minecraft:chainmail_helmet")


def caller(p, n=1, k=3):
    return mob(p, "skyrpg_combat:gravecaller", n, k, "Gravecaller")


def wisp(p, n, k=2):
    return mob(p, "skyrpg_combat:hollow_wisp", n, k, "Hollow Wisp")


def drowned(p, n, k=1):
    return mob(p, "minecraft:drowned", n, k, "Drowned Dead", "minecraft:trident")


def captain(p, k=4):
    if p.fl == 1:
        return mob(p, "minecraft:zombie", 1, k, "Crypt Captain", "minecraft:iron_sword", "minecraft:iron_helmet",
                   "minecraft:iron_chestplate", "minecraft:iron_leggings", "minecraft:iron_boots", elite=True)
    return mob(p, "minecraft:wither_skeleton", 1, k, "Wight Captain", "minecraft:iron_sword", "minecraft:iron_helmet",
               "minecraft:iron_chestplate", "minecraft:iron_leggings", "minecraft:iron_boots", elite=True)


def pk(p, x, y, z, *waves, radius=10, spread=3, group=None):
    total = sum(m["count"] for w in waves for m in w)
    assert 4 <= total <= 10, (p.path, total)
    kw = {"radius": radius, "spread": spread}
    if group:
        kw["group"] = group
    if len(waves) == 1:
        kw["mobs"] = waves[0]
    else:
        kw["waves"] = list(waves)
    p.marker(x, y, z, "spawner", **kw)
    STATS["packs"] += 1
    STATS["wave_packs"] += len(waves) > 1


# ---------------------------------------------------------------- caps
def make_caps(pack, fl):
    pal = PAL[fl]
    for name in ("crypt_room", "crypt_corr", "crypt_spine"):
        c = Piece(f"{P}/f{fl}/caps/{name}", 3, 3, 1)
        c.fill(0, 0, 0, 2, 2, 0, pal.wall)
        c.set(1, 1, 0, pal.crack)
        c.jigsaw(1, 0, 0, "north_up", name, name, EMPTY, final_state=pal.wall)
        c.weather("cap", WEATHER)
        pack.add(c)
        pack.pool(f"{P}/f{fl}/caps_{name}", [(c.path, 1)])


# ---------------------------------------------------------------- entrance (surface mausoleum + stair shaft)
K, EW, ED, EH, Z0 = 14, 11, 23, 25, 2


def entrance():
    e = Piece(f"{P}/entrance", EW, EH, ED)
    e.pal = PAL[1]
    e.rng = random.Random("entrance")
    e.fill(0, 0, Z0, 10, K - 1, Z0 + 10, DB)
    for z in range(Z0 + 11, ED):
        i = z - (Z0 + 3)
        top = (K - 1 - i) + 5 if z <= Z0 + 15 else 5
        e.fill(3, 0, z, 7, min(top, K - 1), z, DB)
    e.fill(0, K - 1, Z0, 10, K - 1, Z0 + 10, SB)
    e.fill(0, K, Z0, 10, K + 5, Z0 + 10, SB)
    e.fill(1, K, Z0 + 1, 9, K + 5, Z0 + 9, AIR)
    e.fill(0, K + 6, Z0, 10, K + 6, Z0 + 10, SB)
    e.fill(1, K - 1, Z0 + 1, 9, K - 1, Z0 + 9, DT)
    e.fill(4, K - 1, Z0 + 1, 6, K - 1, Z0 + 2, PD)
    for x in range(0, 5):
        y = K + 6 + x
        for z in range(Z0, Z0 + 11):
            e.fill(x, K + 6, z, x, y - 1, z, SB)
            e.fill(10 - x, K + 6, z, 10 - x, y - 1, z, SB)
            e.set(x, y, z, "minecraft:stone_brick_stairs[facing=east,half=bottom]")
            e.set(10 - x, y, z, "minecraft:stone_brick_stairs[facing=west,half=bottom]")
    e.fill(5, K + 6, Z0, 5, K + 10, Z0 + 10, SB)
    for (x, z) in ((2, 7), (3, 8), (8, 5), (7, 6), (1, 3)):
        yy = K + 6 + x if x <= 5 else K + 6 + (10 - x)
        e.set(x, yy, Z0 + z, AIR)
    e.fill(9, K + 3, Z0 + 10, 10, K + 5, Z0 + 10, AIR)
    e.fill(10, K + 4, Z0 + 9, 10, K + 5, Z0 + 9, AIR)
    for x, z in ((0, Z0), (10, Z0), (0, Z0 + 10), (10, Z0 + 10)):
        e.fill(x, K, z, x, K + 5, z, DB)
        e.set(x, K + 5, z, CHISEL)
    for z in (Z0 + 3, Z0 + 7):
        for x in (0, 10):
            e.set(x, K + 3, z, "minecraft:iron_bars[north=true,south=true,east=false,west=false]")
    for x in range(4, 7):
        for y in range(K, K + 4):
            e.set(x, y, Z0, AIR)
        e.set(x, K + 4, Z0, CHISEL)
    for y in range(K, K + 4):
        e.set(3, y, Z0, DB)
        e.set(7, y, Z0, DB)
    e.set(3, K + 4, Z0, CHISEL)
    e.set(7, K + 4, Z0, CHISEL)
    for i in range(13):
        z = Z0 + 3 + i
        ys = K - 1 - i
        for x in (4, 5, 6):
            for y in range(ys + 1, ys + 5):
                e.set(x, y, z, AIR)
            e.set(x, ys, z, "minecraft:deepslate_brick_stairs[facing=north,half=bottom]")
    for z in range(Z0 + 16, ED - 1):
        for x in (4, 5, 6):
            for y in range(1, 5):
                e.set(x, y, z, AIR)
            e.set(x, 0, z, DT)
    for i in (3, 6, 9, 12):
        z = Z0 + 3 + i
        ys = K - 1 - i
        e.set(3, ys + 2, z, "minecraft:soul_lantern[hanging=false]")
        e.set(7, ys + 2, z, "minecraft:soul_lantern[hanging=false]")
    e.fill(4, 1, ED - 1, 6, 3, ED - 1, AIR)
    e.fill(4, 4, ED - 1, 6, 4, ED - 1, CHISEL)
    e.door("south", "crypt_entrance_out", "crypt_spine", f"{P}/f1/spine_1", at=5, placement_priority=10)
    coffin(e, 2, Z0 + 2, "z", y=K)
    coffin(e, 8, Z0 + 2, "z", y=K)
    for x, z in ((2, 8), (8, 8)):
        e.set(x, K, Z0 + z, "minecraft:stone_brick_slab[type=bottom]")
        candle(e, x, K + 1, Z0 + z, 3)
    skull(e, 1, K, Z0 + 9, 6)
    skull(e, 9, K, Z0 + 8, 10)
    skull(e, 3, K + 1, Z0 + 1, 8)
    candle(e, 1, K, Z0 + 1, 2)
    candle(e, 9, K, Z0 + 1, 3)
    for x, z in ((3, 9), (7, 9)):
        lantern(e, x, K + 5, Z0 + z, 2)
    for x, z in ((1, 6), (9, 6), (5, 9)):
        e.set(x, K, Z0 + z, "minecraft:mossy_cobblestone")
    for x, z in ((1, 1), (9, 1), (1, 9), (9, 9), (9, 5), (1, 4)):
        web(e, x, K + 4, Z0 + z)
    banner(e, 3, K + 3, Z0 + 1, "south")
    banner(e, 7, K + 3, Z0 + 1, "south")
    e.set(8, K - 1, 1, "minecraft:mossy_stone_bricks")
    e.set(8, K, 1, "skyrpg_world:waystone")
    e.set(2, K - 1, 1, DB)
    e.set(2, K, 1, "minecraft:deepslate_brick_wall")
    e.set(2, K + 1, 1, "minecraft:soul_lantern[hanging=false]")
    e.set(9, K, 1, "minecraft:mossy_cobblestone_wall")
    e.marker(5, K + 1, Z0 + 5, "dungeon", dungeon="skyrpg_world:forgotten_crypt")
    e.weather("w", WEATHER)
    return e


# ---------------------------------------------------------------- spine halls
def spine_hall(fl, name, w, h, d, style, nxt, waves):
    p = new(f"spine/{name}", w, h, d, fl)
    link(p, "north", "crypt_spine")
    link(p, "south", "crypt_spine_out", "crypt_spine", nxt, priority=10)
    for s in ("east", "west"):
        link(p, s, "crypt_side_out", "crypt_corr", f"{P}/f{fl}/corr_a")
    cx, cz = w // 2, d // 2
    p.fill(cx - 1, 0, 1, cx + 1, 0, d - 2, p.pal.polish)
    # traps: arrow slits in both long walls, spike plates in the aisle
    for z in (3, d - 4):
        slit(p, 0, 2, z, "z")
        slit(p, w - 1, 2, z, "z")
    spikes(p, cx - 1, cz - 3)
    spikes(p, cx + 1, cz + 3)
    for z in range(4, d - 2, 5):
        lantern(p, cx, h - 2, z, 2)
    for z in (cz - 4, cz + 4):
        banner(p, 1, 4, z, "east", "purple")
        banner(p, w - 2, 4, z, "west", "purple")
    if style == "pillared":
        for z in range(3, d - 2, 4):
            if abs(z - cz) <= 1:
                continue
            for x in (cx - 3, cx + 3):
                pillar(p, x, z, 1, h - 2)
                candle(p, x - 1 if x < cx else x + 1, 1, z, 3)
        brazier(p, 2, 2, radius=3)
        brazier(p, w - 3, d - 3, radius=3)
        p.chest(1, 1, d - 2, "east", "chests/crypt_common")
        pk(p, cx, 1, cz, *waves)
    elif style == "tombs":
        # sealed alcove (secret) in the north-east corner
        ax = w - 3
        p.fill(ax, 1, 1, ax + 1, 3, 1, p.pal.wall)
        p.fill(ax, 1, 4, ax + 1, 3, 4, p.pal.wall)
        p.fill(ax - 1, 1, 1, ax - 1, 3, 4, p.pal.crack)
        p.fill(ax, 1, 2, ax + 1, 3, 3, AIR)
        p.chest(ax + 1, 1, 2, "west", "chests/crypt_secret")
        candle(p, ax, 1, 3, 3)
        p.marker(ax + 1, 1, 2, "secret", name="Sealed Burial")
        for z in range(6, d - 3, 4):
            if z + 2 >= cz - 2 and z <= cz + 2:
                continue
            coffin(p, 2, z, "z")
            coffin(p, w - 4, z, "z")
            candle(p, 3, 1, z + 1, 2)
            candle(p, w - 5, 1, z + 1, 2)
            skull(p, 1, 1, z + 1, 4)
            skull(p, w - 2, 1, z + 1, 12)
        for x in (cx - 3, cx + 3):
            pillar(p, x, 2, 1, h - 2)
            pillar(p, x, d - 3, 1, h - 2)
        p.chest(1, 1, d - 2, "east", "chests/crypt_common")
        brazier(p, w - 2, d - 2, radius=3)
        pk(p, cx, 1, cz, *waves)
    else:  # brazier
        for dx, dz in ((-3, -3), (3, -3), (-3, 3), (3, 3)):
            brazier(p, cx + dx, cz + dz, radius=3)
        p.fill(cx - 2, 0, cz - 2, cx + 2, 0, cz + 2, p.pal.pillar)
        p.set(cx, 1, cz, p.pal.polish)
        p.set(cx, 2, cz, "minecraft:soul_campfire[lit=true,facing=north]")
        p.marker(cx, 2, cz, "trap", type="flames", radius=4, every=100, damage=3)
        for x, z in ((2, 2), (w - 3, 2), (2, d - 3), (w - 3, d - 3)):
            pillar(p, x, z, 1, h - 2)
        for z in (2, d - 3):
            skull(p, cx - 4, 1, z, 4)
            skull(p, cx + 4, 1, z, 12)
        p.chest(1, 1, 1, "east", "chests/crypt_common")
        if fl == 2:
            for x, z in ((2, cz - 3), (w - 3, cz + 3)):
                soul_patch(p, x, z)
        pk(p, cx, 1, cz + 5 if cz + 5 < d - 2 else cz, *waves, radius=11)
    return fin(p, 10)


# ---------------------------------------------------------------- champion arena
def champion_arena():
    fl = 1
    p = new("champion/bone_knight_arena", 21, 9, 21, fl)
    link(p, "north", "crypt_spine")
    link(p, "south", "crypt_spine_out", "crypt_spine", f"{P}/f1/stairs", priority=10)
    for s in ("east", "west"):
        link(p, s, "crypt_side_out", "crypt_corr", f"{P}/f{fl}/corr_a")
    p.fill(8, 0, 1, 12, 0, 19, p.pal.polish)
    p.fill(1, 0, 8, 19, 0, 12, p.pal.polish)
    p.fill(8, 0, 8, 12, 0, 12, p.pal.pillar)
    p.set(10, 0, 10, p.pal.chisel)
    for x, z in ((4, 4), (16, 4), (4, 16), (16, 16), (4, 10), (16, 10), (10, 4)):
        pillar(p, x, z, 1, 7)
    for x, z in ((2, 2), (18, 2), (2, 18), (18, 18)):
        brazier(p, x, z, radius=4)
    for x in (6, 14):
        for z in (7, 13):
            lantern(p, x, 7, z, 3)
    for z in (6, 14):
        banner(p, 1, 4, z, "east", "red")
        banner(p, 19, 4, z, "west", "red")
    for x in (6, 14):
        banner(p, x, 5, 1, "south", "purple")
    # arrow slits along the north wall hit anyone fleeing back
    for x in (3, 17):
        slit(p, x, 2, 0, "x", radius=12)
    p.chest(1, 1, 3, "east", "chests/crypt_rare")
    candle(p, 1, 1, 4, 3)
    candle(p, 1, 1, 2, 3)
    # the gate
    for x in (9, 10, 11):
        for y in (1, 2, 3):
            bars(p, x, y, 19, "x")
    for y in (1, 2, 3):
        p.set(8, y, 19, p.pal.pillar)
        p.set(12, y, 19, p.pal.pillar)
    for x in range(8, 13):
        p.set(x, 4, 19, p.pal.chisel)
    p.marker(10, 2, 19, "door", group="champion", message="The Bone Knight's gate grinds open.")
    p.marker(10, 1, 10, "boss", boss="skyrpg_world:crypt_champion", radius=12, group="champion")
    p.entity(10, 5, 15, {"id": "minecraft:text_display", "text": {"text": "The Bone Knight holds the way down", "color": "gray"},
                         "billboard": "center", "line_width": 200})
    return fin(p, 14)


# ---------------------------------------------------------------- stairwell (enter north wall at y=11, exit east wall at y=1)
def stairwell():
    W, H, D = 27, 20, 11
    p = new("stairs/descent", W, H, D, 1)
    p.base = 15
    link(p, "north", "crypt_spine", at=5, y=11)
    link(p, "east", "crypt_spine_out", "crypt_spine", f"{P}/f2/spine_1", at=5, priority=10)
    # upper platform (floor y=10) and its solid base
    p.fill(1, 1, 1, 8, 9, 9, p.pal.pillar)
    p.fill(1, 10, 1, 8, 10, 9, DT)
    p.fill(4, 10, 1, 6, 10, 9, PD)
    # stairs down east
    for i in range(10):
        x, ys = 9 + i, 9 - i
        for z in range(3, 8):
            p.set(x, ys, z, f"{p.pal.stairs}[facing=west,half=bottom]")
            if ys >= 2:
                p.fill(x, 1, z, x, ys - 1, z, p.pal.pillar)
        for z in (3, 7):
            bars(p, x, ys + 1, z, "x")
    for z in (1, 2, 8, 9):
        bars(p, 8, 11, z, "z")
    # lower platform: blackstone floor
    p.fill(19, 0, 1, 25, 0, 9, PB)
    p.fill(1, 0, 1, 17, 0, 9, PB)
    for x, z in ((12, 2), (12, 8), (16, 2), (16, 8), (22, 2), (22, 8), (4, 2), (4, 8)):
        lantern(p, x, H - 2, z, 5)
    for z in (2, 8):
        for x in (10, 14, 17):
            soul_patch(p, x, z)
    for x in (13, 21):
        banner(p, x, 14, 1, "south", "purple")
        banner(p, x, 14, 9, "north", "purple")
    for z in (2, 8):
        pillar(p, 20, z, 1, 8)
        pillar(p, 24, z, 1, 8)
    brazier(p, 2, 2, y=11, radius=3)
    brazier(p, 2, 8, y=11, radius=3)
    pk(p, 22, 1, 5, [wither(p, 3, 2), archer(p, 2, 1)], [knight(p, 3, 3)], radius=10, spread=2)
    return fin(p, 8)


# ---------------------------------------------------------------- boss hall (31x15x31)
def boss_room():
    fl = 2
    p = new("boss/warden_hall", 31, 15, 31, fl)
    p.fill(26, 0, 0, 30, 14, 30, p.pal.wall)
    p.fill(0, 0, 26, 30, 14, 30, p.pal.wall)
    link(p, "north", "crypt_spine", at=13)
    # aisle and dais
    p.fill(12, 0, 1, 14, 0, 16, PBB)
    p.fill(8, 1, 17, 18, 1, 24, p.pal.polish)
    p.fill(9, 2, 18, 17, 2, 23, PBB)
    p.fill(11, 3, 19, 15, 3, 22, DT)
    for x in range(8, 19):
        p.set(x, 1, 16, f"{p.pal.stairs}[facing=south,half=bottom]")
    for x in range(9, 18):
        p.set(x, 2, 17, f"{p.pal.stairs}[facing=south,half=bottom]")
    for x in range(11, 16):
        p.set(x, 3, 18, f"{p.pal.stairs}[facing=south,half=bottom]")
    # sarcophagus
    p.fill(12, 4, 20, 14, 4, 21, p.pal.chisel)
    p.fill(12, 5, 20, 14, 5, 21, f"{p.pal.slab}[type=bottom]")
    for x in (11, 15):
        candle(p, x, 4, 20, 4)
        candle(p, x, 4, 21, 3)
    # braziers (flames traps) around the dais and the entrance
    for x, z in ((7, 16), (19, 16), (7, 24), (19, 24), (9, 6), (17, 6)):
        brazier(p, x, z, radius=4)
    # pillars
    for x in (6, 20):
        for z in (4, 8, 12, 16, 20):
            pillar(p, x, z, 1, 13)
            candle(p, x + (1 if x == 6 else -1), 1, z, 3)
    # chandeliers
    for x, z in ((9, 9), (17, 9), (13, 12), (9, 14), (17, 14), (13, 4)):
        lantern(p, x, 13, z, 4)
    # balconies (west and east): platform y=6, stairs up from the north end, railing
    for side in ("w", "e"):
        xs = (1, 4) if side == "w" else (21, 24)
        stair_x = (1, 2) if side == "w" else (23, 24)
        edge = 4 if side == "w" else 21
        p.fill(xs[0], 6, 6, xs[1], 6, 22, p.pal.pillar)
        for z in range(6, 23):
            bars(p, edge, 7, z, "z")
        for k in range(5):
            for x in stair_x:
                p.set(x, 1 + k, 1 + k, f"{p.pal.stairs}[facing=south,half=bottom]")
                if k:
                    p.fill(x, 1, 1 + k, x, k, 1 + k, p.pal.wall)
        for x in stair_x:
            p.fill(x, 1, 6, x, 5, 6, p.pal.wall)
        for z in (9, 13, 17, 21):
            p.fill(edge, 1, z, edge, 5, z, p.pal.pillar)
    p.chest(1, 7, 21, "east", "chests/crypt_rare")
    p.chest(24, 7, 21, "west", "chests/crypt_common")
    p.fill(1, 7, 6, 1, 7, 7, AIR)
    for z in (8, 14, 20):
        banner(p, 1, 10, z, "east", "purple")
        banner(p, 25, 10, z, "west", "purple")
    for x in (7, 19):
        banner(p, x, 9, 1, "south", "purple")
    # ossuary niches against the north wall
    for x in (3, 5, 21, 23):
        p.set(x, 1, 1, BONE)
        skull(p, x, 2, 1, 8)
    # warden gate -> treasure alcove (south)
    for x in (12, 13, 14):
        for y in (1, 2, 3):
            bars(p, x, y, 26, "x")
    p.fill(11, 1, 27, 15, 4, 29, AIR)
    for x in (11, 12, 13, 14, 15):
        p.set(x, 0, 27, p.pal.floor)
    p.chest(13, 1, 29, "north", "chests/crypt_common")
    candle(p, 11, 1, 29, 3)
    candle(p, 15, 1, 29, 3)
    p.marker(13, 2, 26, "door", group="warden", message="The sealed gate grinds open.")
    # vault behind a key door (east wall x=26)
    p.fill(27, 1, 11, 29, 4, 19, AIR)
    for z in (14, 15, 16):
        for y in (1, 2, 3):
            p.set(26, y, z, "minecraft:chiseled_stone_bricks")
    for y in (1, 2, 3):
        p.set(26, y, 13, p.pal.chisel)
        p.set(26, y, 17, p.pal.chisel)
    for z in range(13, 18):
        p.set(26, 4, z, p.pal.chisel)
    p.marker(26, 2, 15, "key_door", key="skyrpg_world:crypt_key",
             message="Locked. The Warden's vault needs a crypt key.")
    p.chest(29, 1, 15, "west", "chests/crypt_vault")
    for z in (11, 19):
        p.set(29, 1, z, "minecraft:gold_block")
        candle(p, 29, 2, z, 3)
        candle(p, 28, 1, z, 3)
    p.sign(25, 2, 13, ["The Warden's", "Vault", "", "Needs a crypt key"], "minecraft:dark_oak_wall_sign[facing=west]")
    p.marker(28, 1, 15, "secret", name="Warden's Vault")
    p.marker(13, 1, 11, "boss", boss="skyrpg_world:crypt_warden", radius=14, group="warden")
    return fin(p, 14)


# ---------------------------------------------------------------- rooms
def room(name, w, h, d, fl, leaf, doors=("south",)):
    p = new(f"{'rooms_b' if leaf else 'rooms_a'}/{name}", w, h, d, fl)
    link(p, "north", "crypt_room")
    if not leaf:
        for s in doors:
            link(p, s, "crypt_room_out", "crypt_corr", f"{P}/f{fl}/corr_b")
    return p


def ossuary(fl, leaf):
    p = room("ossuary", 15, 8, 15, fl, leaf)
    for x, z in ((1, 1), (12, 1), (1, 12), (12, 12)):
        p.fill(x, 1, z, x + 1, 2, z + 1, BONE)
        p.set(x, 3, z, BONE)
        skull(p, x + 1, 3, z + 1, p.rng.randint(0, 15))
    for z in (4, 6, 8, 10):
        p.set(1, 1, z, BONE)
        skull(p, 1, 2, z, 4)
        p.set(13, 1, z, BONE)
        skull(p, 13, 2, z, 12)
    for _ in range(18):
        x, z = p.rng.randint(2, 12), p.rng.randint(2, 12)
        if 6 <= x <= 8 and (z <= 2 or z >= 12):
            continue
        p.set(x, 0, z, BONE)
    for x, z in ((4, 4), (10, 4), (4, 10), (10, 10)):
        lantern(p, x, 6, z, 2)
    p.chest(13, 1, 7, "west", "chests/crypt_common")
    candle(p, 7, 1, 3, 3)
    candle(p, 7, 1, 11, 3)
    pk(p, 7, 1, 7, [archer(p, 3), dead(p, 2)], [knight(p, 2), archer(p, 2)], radius=10, spread=4)
    return fin(p, 14)


def catacomb(fl, leaf):
    p = room("catacomb", 11, 7, 25, fl, leaf)
    p.fill(4, 0, 1, 6, 0, 23, p.pal.polish)
    for z in (2, 5, 8, 11, 14, 17, 20):
        for x in (3, 7):
            p.fill(x, 1, z, x, 5, z, p.pal.pillar)
        for x in (4, 5, 6):
            p.set(x, 5, z, p.pal.chisel)
    for z in (3, 6, 9, 12, 15, 18, 21):
        coffin(p, 1, z, "x")
        candle(p, 1, 1, z + 1, 2)
        skull(p, 2, 1, z + 1, 4)
        if z == 12:
            p.fill(7, 1, z, 7, 3, z + 1, p.pal.crack)
            p.set(7, 4, z, p.pal.wall)
            p.set(7, 4, z + 1, p.pal.wall)
            p.set(8, 1, z, AIR)
            p.chest(9, 1, z + 1, "west", "chests/crypt_secret")
            candle(p, 8, 1, z, 3)
            p.marker(9, 1, z + 1, "secret", name="Hidden Burial")
        else:
            coffin(p, 8, z, "x")
            candle(p, 8, 1, z + 1, 2)
            skull(p, 9, 1, z + 1, 12)
    for z in (5, 11, 17, 23):
        lantern(p, 5, 5, z, 1)
    p.sign(1, 3, 1, ["Not all graves", "are marked."], "minecraft:dark_oak_wall_sign[facing=east]")
    p.chest(1, 1, 23, "east", "chests/crypt_common")
    pk(p, 5, 1, 12, [dead(p, 3), archer(p, 2)], [knight(p, 2), wisp(p, 1)], radius=10, spread=3)
    return fin(p, 12)


def lever_room(fl, leaf, kind="secret"):
    treasury = kind == "treasury"
    p = room("treasury" if treasury else "lever_room", 13, 8, 13, fl, leaf, doors=())
    p.fill(1, 1, 9, 11, 6, 9, p.pal.wall)
    for x in (5, 6, 7):
        for y in (1, 2, 3):
            bars(p, x, y, 9, "x")
    for y in (1, 2, 3):
        p.set(4, y, 9, p.pal.chisel)
        p.set(8, y, 9, p.pal.chisel)
    for x in (4, 5, 6, 7, 8):
        p.set(x, 4, 9, p.pal.chisel)
    p.fill(4, 1, 10, 8, 4, 11, AIR)
    candle(p, 4, 1, 11, 3)
    candle(p, 8, 1, 11, 3)
    if treasury:
        p.chest(6, 1, 11, "north", "chests/crypt_rare")
        for x in (4, 8):
            p.set(x, 1, 10, "minecraft:gold_block")
        p.set(6, 1, 10, "minecraft:gold_block")
        p.marker(6, 2, 9, "door", group="lever", message="The treasury gate grinds open.")
        levers = [(11, 2, "west", True), (11, 5, "west", False), (11, 8, "west", True), (1, 3, "east", False), (1, 7, "east", True)]
        p.sign(11, 5, 4, ["Three kings sat,", "two slept below.", "Wake the first, the", "third, the last."],
               "minecraft:dark_oak_wall_sign[facing=west]", glow=True)
        p.entity(6, 4, 7, {"id": "minecraft:text_display", "text": {"text": "Five levers guard the treasury", "color": "gold"},
                           "billboard": "center", "line_width": 200})
        pk(p, 6, 1, 4, [captain(p, 4), archer(p, 3, 2)], [knight(p, 3, 3)], radius=10, spread=3)
    else:
        p.chest(6, 1, 11, "north", "chests/crypt_secret")
        p.marker(6, 1, 11, "secret", name="Sleepers' Cache")
        p.marker(6, 2, 9, "door", group="lever", message="The gate grinds open.")
        levers = [(11, 2, "west", True), (11, 4, "west", False), (11, 6, "west", False), (11, 8, "west", True)]
        p.sign(11, 5, 5, ["Wake the first", "and the last.", "Let the middle", "two sleep."],
               "minecraft:dark_oak_wall_sign[facing=west]", glow=True)
        p.entity(6, 4, 8, {"id": "minecraft:text_display", "text": {"text": "Four sleepers hold the gate", "color": "gray"},
                           "billboard": "center", "line_width": 200})
        pk(p, 6, 1, 5, [dead(p, 3), archer(p, 2), husk(p, 1)], radius=8, spread=3)
    for x, z, face, want in levers:
        p.set(x, 2, z, f"minecraft:lever[face=wall,facing={face},powered=false]")
        p.set(x, 1, z, p.pal.chisel)
        p.set(x, 3, z, p.pal.chisel)
        p.marker(x, 2, z, "lever", group="lever", want=want)
    for x, z in ((3, 3), (9, 3), (3, 7), (9, 7)):
        pillar(p, x, z, 1, 6)
    p.fill(5, 0, 1, 7, 0, 8, p.pal.polish)
    for x, z in ((4, 5), (8, 5)):
        lantern(p, x, 6, z, 2)
    for x in (2, 10):
        banner(p, x, 4, 1, "south")
    if treasury:
        brazier(p, 2, 10, radius=3)
        brazier(p, 10, 10, radius=3)
    return fin(p, 10)


def guard_room(fl, leaf):
    p = room("guard_room", 19, 8, 15, fl, leaf, doors=("east", "south"))
    p.fill(8, 1, 5, 10, 1, 7, "minecraft:dark_oak_planks")
    candle(p, 9, 2, 6, 4)
    candle(p, 8, 2, 5, 2)
    candle(p, 10, 2, 7, 2)
    for x in (1, 17):
        for z in (1, 13):
            p.set(x, 1, z, "minecraft:barrel[facing=up]")
    p.set(2, 1, 12, "minecraft:anvil[facing=north]")
    p.set(3, 1, 13, "minecraft:grindstone[face=floor,facing=north]")
    p.chest(17, 1, 11, "west", "chests/crypt_rare")
    p.chest(1, 1, 7, "east", "chests/crypt_common")
    for x in (4, 14):
        for z in (4, 10):
            pillar(p, x, z, 1, 6)
    for x, z in ((6, 3), (12, 3), (6, 11), (12, 11)):
        lantern(p, x, 6, z, 2)
    for x in (3, 15):
        banner(p, x, 4, 1, "south", "red")
    banner(p, 1, 4, 3, "east", "red")
    banner(p, 1, 4, 11, "east", "red")
    pk(p, 9, 1, 9, [captain(p, 4), archer(p, 3, 2)], [knight(p, 2, 3), dead(p, 2, 2)], radius=11, spread=3)
    return fin(p, 8)


def chapel(fl, leaf):
    p = room("chapel", 15, 10, 21, fl, leaf, doors=("west",))
    p.fill(7, 0, 1, 7, 0, 14, p.pal.polish)
    for z in (4, 6, 8, 11, 13):
        for x in (3, 4, 5, 9, 10, 11):
            p.set(x, 1, z, "minecraft:spruce_stairs[facing=north,half=bottom]")
    p.fill(4, 1, 15, 10, 1, 19, p.pal.polish)
    p.fill(6, 2, 17, 8, 2, 19, p.pal.pillar)
    for y in range(2, 9):
        p.set(7, y, 19, p.pal.chisel)
    for x in (6, 8):
        candle(p, x, 3, 18, 3)
    skull(p, 7, 3, 18, 8)
    for z in (6, 12):
        lantern(p, 7, 8, z, 2)
    banner(p, 5, 6, 19, "north", "purple")
    banner(p, 9, 6, 19, "north", "purple")
    banner(p, 13, 4, 4, "west")
    banner(p, 1, 4, 14, "east")
    for x, z in ((1, 2), (13, 2), (1, 17), (13, 17)):
        pillar(p, x, z, 1, 8)
    p.chest(12, 1, 19, "west", "chests/crypt_common")
    pk(p, 7, 1, 10, [dead(p, 3), husk(p, 2)], [caller(p, 1), wisp(p, 2), knight(p, 1)], radius=11, spread=3)
    return fin(p, 12)


def tomb_cell(fl, leaf):
    p = room("tomb_cell", 9, 6, 9, fl, leaf, doors=())
    for x in (2, 6):
        coffin(p, x, 3, "z")
    for x, z in ((1, 1), (7, 1), (1, 7), (7, 7)):
        candle(p, x, 1, z, 3)
    skull(p, 4, 1, 4, 0)
    candle(p, 4, 1, 5, 4)
    p.chest(4, 1, 7, "north", "chests/crypt_common")
    lantern(p, 4, 4, 3, 1)
    pk(p, 4, 1, 5, [archer(p, 3), dead(p, 2)], radius=7, spread=3)
    return fin(p, 6)


def embalming(fl, leaf):
    p = room("embalming_chamber", 15, 7, 17, fl, leaf)
    for x in (3, 10):
        p.fill(x, 1, 4, x + 1, 1, 10, "minecraft:quartz_slab[type=bottom]")
        for z in (5, 8):
            skull(p, x, 2, z, p.rng.randint(0, 15))
        candle(p, x + 1, 2, 7, 2)
    for x, z in ((1, 1), (13, 1), (1, 15), (13, 15)):
        p.set(x, 1, z, "minecraft:cauldron")
    p.set(1, 1, 8, "minecraft:brewing_stand")
    p.set(13, 1, 8, "minecraft:brewing_stand")
    p.set(7, 1, 1, "minecraft:cauldron")
    for x, z in ((3, 2), (11, 2), (3, 14), (11, 14), (7, 8)):
        lantern(p, x, 5, z, 2)
    for z in (4, 12):
        for y in (3, 4):
            web(p, 6, y, z)
            web(p, 8, y, z)
    p.marker(4, 1, 7, "trap", type="poison", radius=4, every=80, damage=2)
    p.marker(11, 1, 7, "trap", type="poison", radius=4, every=80, damage=2)
    p.marker(7, 1, 12, "trap", type="poison", radius=3, every=80, damage=2)
    p.chest(13, 1, 12, "west", "chests/crypt_rare")
    p.chest(1, 1, 3, "east", "chests/crypt_common")
    pk(p, 7, 1, 7, [dead(p, 3), husk(p, 2)], [knight(p, 2), wisp(p, 2)], radius=10, spread=3)
    return fin(p, 8)


def bone_throne(fl, leaf):
    p = room("bone_throne", 25, 10, 25, fl, leaf, doors=("east", "west"))
    p.fill(11, 0, 1, 13, 0, 16, p.pal.polish)
    # throne dais
    p.fill(8, 1, 17, 16, 1, 23, p.pal.polish)
    p.fill(10, 2, 19, 14, 2, 23, p.pal.pillar)
    for x in range(8, 17):
        p.set(x, 1, 16, f"{p.pal.stairs}[facing=south,half=bottom]")
    for x in range(10, 15):
        p.set(x, 2, 18, f"{p.pal.stairs}[facing=south,half=bottom]")
    p.fill(11, 3, 23, 13, 7, 23, BONE)
    p.set(12, 3, 22, f"{p.pal.stairs}[facing=south,half=bottom]")
    p.set(11, 3, 22, BONE)
    p.set(13, 3, 22, BONE)
    for x in (11, 12, 13):
        skull(p, x, 8, 23, 8)
    p.chest(9, 2, 22, "north", "chests/crypt_rare")
    p.chest(15, 2, 22, "north", "chests/crypt_common")
    for x in (4, 20):
        for z in (4, 8, 12, 16, 20):
            p.fill(x, 1, z, x, 8, z, BONE)
            p.set(x, 9 - 1, z, p.pal.chisel)
            skull(p, x + (1 if x == 4 else -1), 1, z, 4)
    for x, z in ((7, 5), (17, 5), (7, 15), (17, 15)):
        brazier(p, x, z, radius=4)
    for x, z in ((9, 9), (15, 9), (12, 12)):
        lantern(p, x, 8, z, 3)
    for z in (4, 12, 20):
        banner(p, 1, 5, z, "east", "red")
        banner(p, 23, 5, z, "west", "red")
    for _ in range(26):
        x, z = p.rng.randint(2, 22), p.rng.randint(2, 22)
        if 11 <= x <= 13 and z <= 16:
            continue
        if p.get(x, 0, z) == p.pal.floor:
            p.set(x, 0, z, BONE)
    pk(p, 12, 1, 12, [archer(p, 3), dead(p, 2)], [knight(p, 2), wither(p, 1)], [captain(p, 4)], radius=13, spread=4)
    return fin(p, 14)


def flooded(fl, leaf):
    p = room("flooded_crypt", 21, 8, 21, fl, leaf)
    # A sunken pool: one layer of water inside a solid rim, so it can't spill through the doorways.
    for x in range(4, 17):
        for z in range(4, 17):
            if x in (4, 16) or z in (4, 16):
                p.set(x, 1, z, "minecraft:mossy_stone_bricks" if fl == 1 else PBB)
                continue
            p.set(x, 0, z, "minecraft:mossy_stone_bricks" if fl == 1 else PBB)
            p.set(x, 1, z, WATER)
    p.fill(9, 1, 9, 11, 1, 11, p.pal.pillar)
    for z in (6, 8):
        p.set(10, 1, z, "minecraft:mossy_cobblestone")
    p.set(10, 2, 10, "minecraft:chest[facing=south]", {"id": "minecraft:chest", "LootTable": "skyrpg_world:chests/crypt_rare"})
    p.marker(10, 2, 10, "chest", loot="skyrpg_world:chests/crypt_rare", tier="rare")
    p.marker(10, 2, 11, "secret", name="Drowned Cache")
    for x, z in ((4, 4), (16, 4), (4, 16), (16, 16)):
        pillar(p, x, z, 1, 6)
    for x, z in ((8, 3), (12, 3), (3, 10), (17, 10)):
        lantern(p, x, 6, z, 2)
    for x, z in ((1, 1), (19, 1), (1, 19)):
        skull(p, x, 1, z, 4)
        candle(p, x, 1, z + 1 if z < 19 else z - 1, 3)
    p.chest(1, 1, 18, "east", "chests/crypt_common")
    pk(p, 10, 1, 17, [drowned(p, 3), wisp(p, 2)], [caller(p, 1), dead(p, 3)], radius=12, spread=3)
    return fin(p, 8)


def collapsed(fl, leaf):
    p = room("collapsed_gallery", 13, 10, 27, fl, leaf)
    for x in range(1, 12):
        for z in range(9, 16):
            p.set(x, 0, z, "minecraft:magma_block")
    for z in (10, 13):
        p.fill(6, 1, z, 6, 1, z, p.pal.wall)
    p.set(4, 1, 12, p.pal.wall)
    p.set(8, 1, 12, p.pal.wall)
    for z in (3, 6, 19, 22):
        for x in (2, 10):
            pillar(p, x, z, 1, 8)
    for x, z in ((2, 12), (10, 12)):
        p.fill(x, 1, z, x, 3, z, p.pal.pillar)
    for _ in range(26):
        x, z = p.rng.randint(1, 11), p.rng.choice(list(range(1, 8)) + list(range(16, 26)))
        if 5 <= x <= 7 and (z <= 2 or z >= 24):
            continue
        if p.get(x, 1, z) == AIR:
            p.set(x, 1, z, p.rng.choice(["minecraft:cobblestone", "minecraft:gravel", "minecraft:cobbled_deepslate"]))
    for z in (10, 13):
        web(p, 5, 3, z)
        web(p, 7, 3, z)
    slit(p, 0, 2, 12, "z")
    slit(p, 12, 2, 12, "z")
    for z in (3, 22):
        lantern(p, 6, 8, z, 2)
    p.chest(11, 1, 24, "west", "chests/crypt_rare")
    p.chest(1, 1, 2, "east", "chests/crypt_common")
    pk(p, 6, 1, 20, [archer(p, 3, 2), stray(p, 2)], [knight(p, 2), caller(p, 1)], radius=13, spread=3)
    return fin(p, 10)


def library(fl, leaf):
    p = room("library_of_the_dead", 21, 9, 19, fl, leaf)
    for z in (4, 8, 12):
        for x0, x1 in ((2, 7), (13, 18)):
            p.fill(x0, 1, z, x1, 3, z, BOOKS)
    p.fill(1, 1, 2, 1, 3, 16, BOOKS)
    p.fill(19, 1, 2, 19, 3, 13, BOOKS)
    p.fill(18, 1, 15, 19, 3, 17, BOOKS)
    p.set(19, 1, 16, "minecraft:chest[facing=west]", {"id": "minecraft:chest", "LootTable": "skyrpg_world:chests/crypt_secret"})
    p.marker(19, 1, 16, "chest", loot="skyrpg_world:chests/crypt_secret", tier="secret")
    p.marker(19, 1, 16, "secret", name="Hidden Study")
    for x in (9, 11):
        p.set(x, 1, 3, "minecraft:lectern[facing=south,has_book=false,powered=false]")
    p.set(10, 1, 15, "minecraft:enchanting_table")
    for x, z in ((5, 6), (15, 6), (5, 10), (15, 10), (10, 3)):
        lantern(p, x, 7, z, 2)
    p.fill(9, 0, 1, 11, 0, 17, p.pal.polish)
    p.entity(10, 4, 14, {"id": "minecraft:text_display", "text": {"text": "The last page is missing", "color": "dark_purple"},
                         "billboard": "center", "line_width": 200})
    p.chest(2, 1, 17, "east", "chests/crypt_common")
    for x in (7, 13):
        banner(p, x, 5, 1, "south", "purple")
    pk(p, 10, 1, 9, [wisp(p, 3), archer(p, 2, 1)], [caller(p, 2), knight(p, 2)], radius=11, spread=3)
    return fin(p, 14)


def prison(fl, leaf):
    p = room("prison_cells", 15, 7, 21, fl, leaf)
    for side_x, bar_x, ox in ((1, 4, 1), (11, 10, 11)):
        for z in range(1, 20):
            for y in (1, 2, 3):
                bars(p, bar_x, y, z, "z")
        for z in (6, 11, 16):
            p.fill(ox, 1, z, ox + 2, 3, z, p.pal.wall)
        for z in (3, 8, 13):
            p.set(ox + 1, 1, z, "minecraft:cobweb")
            skull(p, ox, 1, z + 1, p.rng.randint(0, 15))
            p.set(ox + 2, 1, z + 1, BONE)
    # the locked cell: bars become a door, chest inside
    p.fill(10, 1, 17, 10, 3, 19, "minecraft:copper_grate")
    p.marker(10, 2, 18, "door", group="ambush", message="The cell door swings open.")
    p.chest(13, 1, 18, "west", "chests/crypt_rare")
    candle(p, 12, 1, 17, 3)
    for z in (3, 9, 15):
        lantern(p, 7, 5, z, 1)
    p.fill(5, 0, 1, 9, 0, 19, p.pal.polish)
    pk(p, 7, 1, 10, [dead(p, 3), husk(p, 1)], [archer(p, 3)], [knight(p, 2)], radius=10, spread=2, group="ambush")
    return fin(p, 6)


# ---------------------------------------------------------------- corridors
def corridor_exit(p, side, variant):
    link(p, side, "crypt_corr_out", "crypt_room", f"{P}/f{p.fl}/rooms_{variant}")


def straight(path, d, fl, variant):
    p = new(f"corr_{variant}/{path}", 5, 6, d, fl)
    link(p, "north", "crypt_corr")
    corridor_exit(p, "south", variant)
    for z in range(3, d - 3, 4):
        lantern(p, 2, 4, z, 1)
    skull(p, 1, 1, d // 2 + 1, 4)
    candle(p, 3, 1, d // 2 - 1, 3)
    return p


def corridor_long(fl, variant):
    p = straight("straight_long", 13, fl, variant)
    for z in (4, 8):
        for x in (1, 3):
            p.set(x, 1, z, "minecraft:stone_brick_wall")
            p.set(x, 2, z, "minecraft:stone_brick_wall")
    pk(p, 2, 1, 6, [archer(p, 2, 1), knight(p, 2, 2)], radius=7, spread=2)
    return fin(p, 4)


def corridor_plain(fl, variant):
    return fin(straight("straight", 9, fl, variant), 4)


def corridor_trapped(fl, variant):
    p = straight("trapped", 19, fl, variant)
    for z in (4, 9, 14):
        slit(p, 0, 2, z, "z", radius=8)
        slit(p, 4, 2, z + 1 if z < 14 else z, "z", radius=8)
    spikes(p, 1, 7)
    spikes(p, 3, 12)
    spikes(p, 2, 16)
    return fin(p, 4)


def shell_around(p, cells):
    cells = set(cells)
    for c in cells:
        p.set(*c, AIR)
    for (x, y, z) in cells:
        for dx in (-1, 0, 1):
            for dy in (-1, 0, 1):
                for dz in (-1, 0, 1):
                    n = (x + dx, y + dy, z + dz)
                    if p.inside(*n) and n not in cells and p.get(*n) is None:
                        p.set(*n, p.pal.wall)
    for (x, y, z) in cells:
        if y == 1:
            p.set(x, 0, z, p.pal.floor)


def corner(fl, variant):
    p = shell_piece(f"corr_{variant}/corner", 9, 6, 9, fl)
    cells = [(x, y, z) for y in range(1, 5) for x in range(3, 6) for z in range(3, 8)]
    cells += [(x, y, z) for y in range(1, 5) for x in range(5, 8) for z in range(3, 6)]
    shell_around(p, cells)
    link(p, "south", "crypt_corr")
    corridor_exit(p, "east", variant)
    lantern(p, 4, 4, 4, 1)
    skull(p, 3, 1, 3, 2)
    candle(p, 3, 1, 4, 3)
    web(p, 3, 4, 3)
    web(p, 7, 4, 3)
    return fin(p, 3)


def tee(fl, variant):
    p = shell_piece(f"corr_{variant}/tee", 9, 6, 9, fl)
    cells = [(x, y, z) for y in range(1, 5) for x in range(3, 6) for z in range(4, 8)]
    cells += [(x, y, z) for y in range(1, 5) for x in range(1, 8) for z in range(3, 6)]
    shell_around(p, cells)
    link(p, "south", "crypt_corr")
    corridor_exit(p, "east", variant)
    corridor_exit(p, "west", variant)
    lantern(p, 4, 4, 4, 1)
    skull(p, 1, 1, 5, 6)
    skull(p, 7, 1, 3, 10)
    candle(p, 4, 1, 3, 3)
    web(p, 1, 4, 3)
    web(p, 7, 4, 5)
    return fin(p, 3)


# ---------------------------------------------------------------- loot
def item(name, weight, lo=None, hi=None, funcs=None):
    e = {"type": "minecraft:item", "name": name, "weight": weight}
    f = list(funcs or [])
    if lo is not None:
        f.append({"function": "minecraft:set_count",
                  "count": {"type": "minecraft:uniform", "min": lo, "max": hi if hi is not None else lo}})
    if f:
        e["functions"] = f
    return e


def book(weight):
    return item("minecraft:book", weight, funcs=[{"function": "minecraft:enchant_randomly"}])


def table(pools, typ="minecraft:chest"):
    return {"type": typ, "pools": pools}


def pool_(entries, rolls=1, conditions=None):
    p = {"rolls": rolls, "bonus_rolls": 0.0, "entries": entries}
    if conditions:
        p["conditions"] = conditions
    return p


def opt(name, c, lo=None, hi=None):
    """A pool that gives `name` (count lo..hi) with chance c (1.0 = always)."""
    return pool_([item(name, 1, lo, hi)],
                 conditions=None if c >= 1 else [{"condition": "minecraft:random_chance", "chance": c}])


def uni(lo, hi):
    return {"type": "minecraft:uniform", "min": lo, "max": hi}


GEAR = ["skyrpg_items:cryptbreaker", "skyrpg_items:wardens_shroud_helmet", "skyrpg_items:wardens_shroud_chestplate",
        "skyrpg_items:wardens_shroud_leggings", "skyrpg_items:wardens_shroud_boots"]


def one_gear(c):
    return pool_([item(g, 1) for g in GEAR], conditions=[{"condition": "minecraft:random_chance", "chance": c}])


def loot(pack):
    pack.loot_table("chests/crypt_common", table([
        pool_([item("minecraft:bone", 14, 2, 8), item("minecraft:arrow", 12, 4, 12), item("minecraft:bread", 10, 1, 4),
               item("minecraft:iron_nugget", 10, 3, 9), item("minecraft:gold_nugget", 8, 3, 9),
               item("minecraft:iron_ingot", 9, 1, 4), item("minecraft:gold_ingot", 7, 1, 3),
               item("minecraft:emerald", 6, 1, 3), item("minecraft:string", 6, 1, 4),
               item("skyrpg_mining:glimmer_shard", 9, 1, 3), item("skyrpg_combat:valor_mark", 7, 1, 2),
               item("skyrpg_enchants:runic_dust", 5, 1, 2), item("minecraft:lapis_lazuli", 6, 2, 6),
               book(5), item("minecraft:golden_apple", 2), item("minecraft:name_tag", 2), item("minecraft:saddle", 2)],
              rolls=uni(3, 6)),
        opt("skyrpg_enchants:runic_dust", 0.45, 1, 3),
        opt("skyrpg_combat:valor_mark", 0.4, 1, 3),
        opt("minecraft:golden_apple", 0.12),
        opt("skyrpg_combat:dread_fang", 0.05),
        opt("skyrpg_mining:prism_fragment", 0.04),
    ]))
    pack.loot_table("chests/crypt_rare", table([
        pool_([item("minecraft:emerald", 8, 3, 6), item("minecraft:gold_ingot", 8, 2, 5), item("minecraft:iron_ingot", 6, 3, 6),
               item("minecraft:diamond", 4, 1, 2), item("minecraft:golden_apple", 4, 1, 2), book(6),
               item("skyrpg_mining:glimmer_shard", 8, 2, 5), item("skyrpg_mining:glimmer_crystal", 3, 1, 2),
               item("skyrpg_combat:valor_mark", 6, 2, 4), item("skyrpg_mining:skyiron_ingot", 4, 1, 2),
               item("skyrpg_mining:duskquartz_shard", 4, 1, 3), item("skyrpg_mining:prism_fragment", 3, 1, 2),
               item("skyrpg_combat:marrow_shard", 3, 1, 2), item("skyrpg_combat:wisp_essence", 3, 1, 2),
               item("minecraft:name_tag", 2), item("minecraft:saddle", 2)], rolls=uni(4, 7)),
        opt("skyrpg_enchants:runic_dust", 0.75, 2, 4),
        opt("skyrpg_combat:dread_fang", 0.15),
        opt("skyrpg_mining:prism_fragment", 0.35, 1, 2),
        opt("skyrpg_mining:skyiron_ingot", 0.25, 1, 3),
        opt("skyrpg_combat:valor_seal", 0.10),
        opt("skyrpg_combat:plague_heart", 0.08),
    ]))
    pack.loot_table("chests/crypt_secret", table([
        pool_([item("minecraft:emerald", 10, 3, 7), item("minecraft:gold_ingot", 10, 3, 6), item("minecraft:iron_ingot", 8, 3, 7),
               item("minecraft:diamond", 5, 1, 3), item("minecraft:golden_apple", 5, 1, 2), book(8),
               item("skyrpg_mining:glimmer_crystal", 5, 1, 3), item("skyrpg_mining:prism_fragment", 6, 1, 3),
               item("skyrpg_mining:skyiron_ingot", 5, 1, 3), item("skyrpg_combat:valor_mark", 6, 2, 5),
               item("skyrpg_mining:duskquartz_shard", 4, 2, 4), item("skyrpg_combat:marrow_shard", 4, 1, 3),
               item("minecraft:name_tag", 4), item("minecraft:saddle", 4)], rolls=uni(4, 6)),
        opt("skyrpg_enchants:runic_dust", 0.8, 3, 5),
        opt("skyrpg_mining:prism_fragment", 0.5, 1, 3),
        opt("skyrpg_combat:dread_fang", 0.2),
        opt("skyrpg_combat:valor_seal", 0.15),
        opt("skyrpg_mining:emberite_ingot", 0.10),
        opt("skyrpg_mining:heartstone", 0.08),
        opt("skyrpg_farming:lucky_clover", 0.10),
        opt("skyrpg_foraging:golden_acorn", 0.10),
        pool_([item("skyrpg_pets:owl_egg", 1), item("skyrpg_pets:pup_egg", 1)],
              conditions=[{"condition": "minecraft:random_chance", "chance": 0.03}]),
    ]))
    pack.loot_table("chests/crypt_vault", table([
        pool_([item("minecraft:emerald", 12, 5, 10), item("minecraft:gold_ingot", 12, 4, 8), item("minecraft:iron_ingot", 8, 4, 8),
               item("minecraft:golden_apple", 8, 1, 3), book(14), item("minecraft:diamond", 8, 2, 4),
               item("skyrpg_mining:glimmer_crystal", 8, 2, 4), item("skyrpg_mining:skyiron_ingot", 6, 2, 4),
               item("skyrpg_combat:valor_mark", 8, 3, 6), item("skyrpg_mining:duskquartz_shard", 5, 2, 5),
               item("skyrpg_combat:marrow_shard", 5, 2, 4), item("skyrpg_combat:wisp_essence", 5, 2, 4),
               item("minecraft:name_tag", 4), item("minecraft:saddle", 4)], rolls=uni(5, 8)),
        opt("skyrpg_world:warden_sigil", 1.0, 2, 3),
        opt("skyrpg_enchants:runic_dust", 0.9, 3, 6),
        opt("skyrpg_mining:prism_fragment", 1.0, 2, 5),
        opt("skyrpg_mining:prism", 0.6),
        opt("skyrpg_combat:dread_fang", 0.4, 1, 2),
        opt("skyrpg_combat:valor_seal", 0.6, 1, 2),
        opt("skyrpg_combat:nightmare_core", 0.10),
        opt("skyrpg_combat:elite_trophy", 0.2),
        opt("skyrpg_combat:tyrants_ash", 0.15),
        opt("skyrpg_mining:emberite_ingot", 0.15, 1, 2),
        opt("skyrpg_mining:heartstone", 0.10),
        opt("skyrpg_farming:lucky_clover", 0.15),
        opt("skyrpg_foraging:golden_acorn", 0.15),
        opt("skyrpg_pets:golden_pet_egg", 0.02),
        one_gear(0.20),
        opt("minecraft:enchanted_golden_apple", 0.10),
    ]))
    pack.loot_table("bosses/crypt_champion", table([
        opt("skyrpg_world:warden_sigil", 0.5, 1, 2),
        opt("skyrpg_world:crypt_key", 0.35),
        opt("skyrpg_enchants:runic_dust", 0.8, 1, 3),
        opt("skyrpg_combat:valor_mark", 1.0, 2, 5),
        opt("minecraft:iron_ingot", 1.0, 3, 8),
        opt("minecraft:bone", 1.0, 6, 16),
    ], typ="minecraft:generic"))
    pack.loot_table("bosses/crypt_warden", table([
        opt("skyrpg_world:warden_sigil", 1.0, 2, 4),
        opt("skyrpg_world:crypt_key", 1.0),
        opt("minecraft:emerald", 1.0, 6, 14),
        opt("minecraft:gold_ingot", 1.0, 4, 10),
        opt("skyrpg_enchants:runic_dust", 0.9, 3, 6),
        opt("skyrpg_combat:valor_mark", 1.0, 3, 6),
        opt("skyrpg_combat:nightmare_core", 0.25),
    ] + [pool_([item(g, 1)], conditions=[{"condition": "minecraft:random_chance", "chance": 0.15}]) for g in GEAR] + [
        pool_([item("minecraft:map", 1, funcs=[
            {"function": "minecraft:exploration_map", "destination": "skyrpg_world:on_hollow_maps",
             "decoration": "minecraft:red_x", "zoom": 2, "skip_existing_chunks": False},
            {"function": "minecraft:set_name", "target": "item_name", "name": "Spider Hollow Map"}])],
              conditions=[{"condition": "minecraft:random_chance", "chance": 0.4}]),
    ], typ="minecraft:generic"))
    pack.loot_table("rewards/crypt_warden", table([
        opt("skyrpg_world:warden_sigil", 1.0, 2, 3),
        opt("skyrpg_combat:valor_seal", 1.0),
        opt("skyrpg_mining:prism_fragment", 1.0, 2, 4),
        opt("skyrpg_combat:dread_fang", 1.0),
        opt("skyrpg_enchants:runic_dust", 1.0, 4, 8),
        opt("minecraft:golden_apple", 1.0, 1, 3),
        opt("skyrpg_pets:golden_pet_egg", 0.01),
        opt("minecraft:enchanted_golden_apple", 0.15),
    ] + [pool_([item(g, 1)], conditions=[{"condition": "minecraft:random_chance", "chance": 0.10}]) for g in GEAR]))


# ---------------------------------------------------------------- bosses / dungeon JSON
def boss_json(pack):
    def mobd(t, n, lvl, name, main=None):
        m = {"type": t, "count": n, "level": lvl, "elite": False, "name": name}
        if main:
            m["equipment"] = {"mainhand": main}
        return m

    pack.json("skyrpg/dungeon_bosses/crypt_champion.json", {
        "name": "The Bone Knight",
        "tier": "champion",
        "entity": "minecraft:skeleton",
        "dungeon": "skyrpg_world:forgotten_crypt",
        "level": 16,
        "health": 1200,
        "damage": 10,
        "speed": 0.27,
        "scale": 1.4,
        "knockback_resistance": 0.6,
        "bar_color": "white",
        "equipment": {"mainhand": "minecraft:iron_sword", "head": "minecraft:iron_helmet", "chest": "minecraft:iron_chestplate",
                      "legs": "minecraft:iron_leggings", "feet": "minecraft:iron_boots"},
        "intro": "None pass the Bone Knight.",
        "death": "The Bone Knight collapses. The gate to the depths grinds open.",
        "max_minions": 4,
        "coins": [800, 1200],
        "delving_xp": 400,
        "loot": "skyrpg_world:bosses/crypt_champion",
        "first_clear": {"coins": 1500, "xp": {"skyrpg_skills:combat": 500}},
        "reset_ticks": 72000,
        "moves": [
            {"type": "slam", "every": 200, "radius": 5, "damage": 10, "windup": 22,
             "warn": "The Bone Knight raises his blade!"},
            {"type": "summon", "every": 380, "max": 4,
             "mobs": [mobd("minecraft:skeleton", 2, 15, "Crypt Archer", "minecraft:bow"),
                      mobd("minecraft:zombie", 1, 15, "Restless Dead", "minecraft:stone_sword")],
             "warn": "Bones rattle in the dark!"},
            {"type": "blink", "every": 280, "warn": "The Bone Knight slips behind you!"},
        ],
    })
    pack.json("skyrpg/dungeon_bosses/crypt_warden.json", {
        "name": "The Crypt Warden",
        "entity": "minecraft:wither_skeleton",
        "dungeon": "skyrpg_world:forgotten_crypt",
        "level": 18,
        "health": 4500,
        "damage": 14,
        "speed": 0.28,
        "scale": 1.6,
        "knockback_resistance": 0.8,
        "bar_color": "purple",
        "equipment": {"mainhand": "minecraft:netherite_sword", "head": "minecraft:netherite_helmet",
                      "chest": "minecraft:netherite_chestplate", "legs": "minecraft:netherite_leggings",
                      "feet": "minecraft:netherite_boots"},
        "intro": "Who disturbs the Warden's rest? The dead keep their oaths.",
        "death": "The Warden crumbles to dust. The gate behind him groans open.",
        "max_minions": 8,
        "coins": [3000, 4500],
        "delving_xp": 1500,
        "health_per_player": 0.6,
        "loot": "skyrpg_world:bosses/crypt_warden",
        "reward_loot": "skyrpg_world:rewards/crypt_warden",
        "first_clear": {"coins": 5000, "xp": {"skyrpg_skills:combat": 1500}},
        "reset_ticks": 72000,
        "phases": [
            {"below": 0.66, "message": "The Warden calls the dead to arms!"},
            {"below": 0.33, "message": "The Warden's eyes burn violet - the crypt shakes!"},
        ],
        "moves": [
            {"type": "slam", "every": 220, "radius": 6, "damage": 14, "windup": 24,
             "warn": "The Warden raises his blade - the floor cracks!"},
            {"type": "summon", "every": 420, "max": 6,
             "mobs": [mobd("minecraft:skeleton", 3, 17, "Crypt Archer", "minecraft:bow"),
                      mobd("minecraft:zombie", 2, 17, "Restless Dead", "minecraft:iron_sword")],
             "warn": "The tombs answer his call!"},
            {"type": "blink", "every": 300, "warn": "The Warden vanishes into the dark..."},
            {"type": "shockwave", "every": 340, "radius": 9, "damage": 10, "push": 1.6,
             "warn": "A grave-chill gathers around the Warden!"},
            {"type": "volley", "every": 260, "below": 0.66, "projectile": "fireball", "count": 3, "damage": 8,
             "spread": 0.2, "warn": "Soulfire gathers in his palm!"},
            {"type": "ward", "every": 700, "below": 0.5, "duration": 140, "reduction": 0.85,
             "warn": "Spectral plate hardens around the Warden!"},
            {"type": "enrage", "below": 0.33, "speed": 0.33, "damage": 0.35,
             "warn": "The Warden's eyes burn violet!"},
        ],
    })
    pack.json("skyrpg/dungeons/forgotten_crypt.json", {
        "key": "skyrpg_world:crypt_key",
        "name": "Forgotten Crypt",
        "structure": "skyrpg_world:forgotten_crypt",
        "color": "#9C7BD8",
        "icon": "minecraft:skeleton_skull",
        "level": 12,
        "boss": "skyrpg_world:crypt_warden",
        "reset_ticks": 72000,
        "description": "A sealed two-floor burial complex under the forest floor.",
    })


# ---------------------------------------------------------------- build
def build(pack):
    make_caps(pack, 1)
    make_caps(pack, 2)
    pack.add(entrance())
    pack.pool(f"{P}/entrance", [(f"{P}/entrance", 1)])

    def S(fl, step, nxt, defs):
        halls = []
        for (name, w, h, d, style, waves_fn) in defs:
            p = spine_hall(fl, name, w, h, d, style, f"{P}/f{fl}/{nxt}", waves_fn)
            halls.append(pack.add(p))
        pack.pool(f"{P}/f{fl}/spine_{step}", [(h.path, 2) for h in halls], fallback=f"{P}/f{fl}/caps_crypt_spine")

    # floor 1 spine (waves are built lazily: need the piece for mob levels, so use stubs filled in below)
    # floor-specific wave lists are built inside a tiny factory so mob levels follow the floor
    def mk(fl, which):
        stub = NS(base=PAL[fl].base, fl=fl)
        a, k, d_, h_, s_, w_, c_, wi = archer, knight, dead, husk, stray, wither, caller, wisp
        table_ = {
            "a": [[a(stub, 3), d_(stub, 2)], [k(stub, 2), a(stub, 2)]],
            "b": [[d_(stub, 3), h_(stub, 2)], [s_(stub, 2, 1), k(stub, 2, 3), wi(stub, 1)]],
            "c": [[a(stub, 4, 1), s_(stub, 2, 2)], [k(stub, 3, 3), c_(stub, 1, 3)]],
            "d": [[w_(stub, 3, 2), a(stub, 2, 2)], [k(stub, 3, 3), wi(stub, 2, 3)]],
        }
        return table_[which]

    spine_defs = {
        1: [(1, "spine_2", [("antechamber", 11, 7, 13, "pillared", mk(1, "a")), ("vestibule", 15, 7, 13, "tombs", mk(1, "b"))]),
            (2, "spine_3", [("nave", 15, 8, 15, "pillared", mk(1, "c")), ("hall_of_tombs", 21, 9, 15, "tombs", mk(1, "a"))]),
            (3, "champion", [("gallery", 17, 9, 15, "brazier", mk(1, "b")), ("reliquary", 21, 9, 13, "brazier", mk(1, "c"))])],
        2: [(1, "spine_2", [("bone_gate", 13, 8, 13, "pillared", mk(2, "d")), ("ash_vestibule", 17, 8, 13, "tombs", mk(2, "b"))]),
            (2, "spine_3", [("sunken_nave", 15, 9, 15, "brazier", mk(2, "c")), ("wraith_hall", 21, 9, 15, "pillared", mk(2, "d"))]),
            (3, "boss", [("black_gallery", 17, 9, 15, "tombs", mk(2, "c")), ("soul_reliquary", 21, 9, 13, "brazier", mk(2, "d"))])],
    }
    for fl, steps in spine_defs.items():
        for step, nxt, defs in steps:
            S(fl, step, nxt, defs)

    pack.add(champion_arena())
    pack.pool(f"{P}/f1/champion", [(f"{P}/f1/champion/bone_knight_arena", 1)], fallback=f"{P}/f1/caps_crypt_spine")
    pack.add(stairwell())
    pack.pool(f"{P}/f1/stairs", [(f"{P}/f1/stairs/descent", 1)], fallback=f"{P}/f1/caps_crypt_spine")
    pack.add(boss_room())
    pack.pool(f"{P}/f2/boss", [(f"{P}/f2/boss/warden_hall", 1)], fallback=f"{P}/f2/caps_crypt_spine")

    makers = [(ossuary, 2), (catacomb, 2), (lever_room, 1), (lambda f, l: lever_room(f, l, "treasury"), 1), (guard_room, 2),
              (chapel, 2), (tomb_cell, 2), (embalming, 2), (bone_throne, 2), (flooded, 2), (collapsed, 2),
              (library, 2), (prison, 2)]
    for fl in (1, 2):
        for leaf, pool_name in ((False, "rooms_a"), (True, "rooms_b")):
            els = []
            for fn, wgt in makers:
                piece = pack.add(fn(fl, leaf))
                els.append((piece.path, wgt))
            pack.pool(f"{P}/f{fl}/{pool_name}", els, fallback=f"{P}/f{fl}/caps_crypt_room")
        for variant in ("a", "b"):
            corrs = [(corridor_plain(fl, variant), 3), (corridor_long(fl, variant), 2), (corner(fl, variant), 3),
                     (tee(fl, variant), 2), (corridor_trapped(fl, variant), 2)]
            for c, _ in corrs:
                pack.add(c)
            pack.pool(f"{P}/f{fl}/corr_{variant}", [(c.path, w) for c, w in corrs], fallback=f"{P}/f{fl}/caps_crypt_corr")

    pack.structure("forgotten_crypt", start_pool=f"{P}/entrance",
                   biomes="#skyrpg_world:has_structure/forgotten_crypt", size=16, max_distance=116,
                   step="underground_structures", adaptation="beard_thin", heightmap="WORLD_SURFACE_WG",
                   start_height=-K)
    pack.structure_set("forgotten_crypt", spacing=28, separation=10, salt=61201711)
    pack.biome_tag("forgotten_crypt", ["#minecraft:is_forest", "minecraft:plains", "minecraft:sunflower_plains",
                                       "minecraft:meadow"])
    loot(pack)
    boss_json(pack)


def report(pack):
    from collections import Counter
    kinds = Counter()
    per_piece = {}
    for path, piece in pack.pieces.items():
        c = Counter(e["nbt"]["data"]["skyrpg"] for e in piece.entities if "skyrpg" in e["nbt"].get("data", {}))
        per_piece[path] = c
        kinds.update(c)
    print(f"{len(pack.pieces)} pieces, {len(pack.pools)} pools")
    for path, piece in sorted(pack.pieces.items()):
        if "/caps/" in path:
            continue
        print(f"  {path.split('/', 1)[1]:46s} {piece.size[0]:>3}x{piece.size[1]:>2}x{piece.size[2]:>3}  "
              + " ".join(f"{k}:{v}" for k, v in sorted(per_piece[path].items())))
    print("marker kinds over all templates:", dict(kinds))
    # trap/secret/wave guarantees on the spine
    for fl in (1, 2):
        halls = [p for p in pack.pieces if p.startswith(f"{P}/f{fl}/spine/")]
        mins = Counter()
        t = [per_piece[h].get("trap", 0) for h in halls]
        print(f"floor {fl}: spine halls={len(halls)}, min traps per hall={min(t)}")
    print("spawner packs:", STATS["packs"], "of which waves:", STATS["wave_packs"])


if __name__ == "__main__":
    pack = Pack()
    build(pack)
    # stale v1 pool files would reference deleted pieces: clear this dungeon's pool folder first
    shutil.rmtree(pack.resources / "data" / "skyrpg_world" / "worldgen" / "template_pool" / "forgotten_crypt",
                  ignore_errors=True)
    pack.save(clean=["forgotten_crypt"])
    # Written after save(): Pack.validate() mistakes tags/worldgen/structure/*.json for a structure file.
    pack.structure_tag("on_crypt_maps", ["forgotten_crypt"])
    for path, data in pack.files.items():
        if "/tags/worldgen/structure/" in path:
            out = pack.resources / path
            out.parent.mkdir(parents=True, exist_ok=True)
            out.write_text(json.dumps(data, indent=2) + chr(10), encoding="utf-8")
    report(pack)
