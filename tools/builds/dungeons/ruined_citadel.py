"""The Ruined Citadel (skyrpg_world:ruined_citadel): a three-floor castle ruin sunk into mountain peaks and slopes.
Mob levels 54-58, champion Lv 57 (the Knight-Captain), boss Lv 58 (the Fallen Knight). Built like the other v2 dungeons
(see docs/design/dungeons.md and docs/design/end_game.md) on dungeon_kit and endgame_kit.

Layout (jigsaw, size 16, max_distance 116). It is stacked vertically (stairwells) instead of spreading wide, so every
part stays inside the 110-block reach limit. All floors lie at local y=0 of their pieces; every doorway is 3x3 at y=1.

  SURFACE  "entrance" (25 x 16 x 25, the start piece, projected to WORLD_SURFACE_WG with start_height 0): the ruined
           Gatehouse built into the peak (two broken towers, a gate arch, a plaza with banners, a collapsed statue, a
           waystone). Under it, joined by a vertical jigsaw (down_north / up_north), "stairwell" (25 x 20 x 25) holds the
           square-spiral stair down to floor 1 (landings with packs and a chest); its exit is on the EAST wall.
  FLOOR 1  "The Outer Ward" (Lv 54-56): f1_s1 -> f1_s2 (spine halls, 19 deep: sunken courtyards, barracks, stables, mess
           halls) -> the Knight-Captain's Hall (champion, bar plug) -> descent 1 (switchback stairs, 16 down, a 90 degree turn).
  FLOOR 2  "The Keep" (Lv 56-57): f2_s1 -> f2_s2 (libraries, great halls, chapels, statue galleries) -> descent 2 (16 down).
  FLOOR 3  "The Throne Room" (Lv 57-58): the Hall of Banners (a portcullis puzzle: three levers) -> the Throne Room (boss,
           a key-door vault for the Citadel Crest Key and a hoard nook that opens when the Fallen Knight dies).
  Side rooms hang off the spine halls of floors 1 and 2 (floor 2: only the side that faces back towards the entrance; the
  far side only gets leaves). Everything falls back to 1-thick caps.

Doors: rc_spine_in / rc_side_in / rc_tail_in receive; the _out ones target the matching _in. Spine doors have priority 10.

Run:  python tools/builds/dungeons/ruined_citadel.py       (add --show <piece path> <y> to print a slice of a piece)
Offline checks (all run on every build): jigsaw validation, block and loot item ids, walk test (every door and marker
reachable, door markers opened), pack sizes, spine rules, and the reach table.
"""
import math
import sys
import pathlib

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parent))
from endgame_kit import (Pal, Style, Room, Theme, Split, Shaft, descent_piece, cap_piece, mob, total, wchoice,  # noqa: E402
                         it, book, rolls, chance, table, lpool, run_main, autoconnect, AIR, CHAIN, BARS, Piece)

RC = "ruined_citadel"
DUN = "skyrpg_world:ruined_citadel"
M, W, C = "skyrpg_mining:", "skyrpg_world:", "skyrpg_combat:"

SB, CSB, MSB, CHSB = "minecraft:stone_bricks", "minecraft:cracked_stone_bricks", "minecraft:mossy_stone_bricks", "minecraft:chiseled_stone_bricks"
PA, SS, COB, MCOB = "minecraft:polished_andesite", "minecraft:smooth_stone", "minecraft:cobblestone", "minecraft:mossy_cobblestone"
TB, PT, CTB = "minecraft:tuff_bricks", "minecraft:polished_tuff", "minecraft:chiseled_tuff_bricks"
PBB, PB, CHPB = "minecraft:polished_blackstone_bricks", "minecraft:polished_blackstone", "minecraft:chiseled_polished_blackstone"
GILD = "minecraft:gilded_blackstone"
WALL_PBB = "minecraft:polished_blackstone_brick_wall"

PAL = {
    1: Pal(SB, PA, SS, PA, CHSB, {SB: [(CSB, .15), (MSB, .12), (COB, .03)], PA: [(SB, .05)], SS: [(PA, .1)]}, "minecraft:lantern"),
    2: Pal(TB, PT, CTB, PT, CTB, {TB: [("minecraft:tuff", .10), ("minecraft:cobbled_deepslate", .03), (SB, .02)], PT: [(TB, .06)]}, "minecraft:lantern"),
    3: Pal(PBB, PB, PBB, PB, CHPB, {PBB: [("minecraft:cracked_polished_blackstone_bricks", .12), ("minecraft:blackstone", .05)],
                                    PB: [("minecraft:blackstone", .08)]}, "minecraft:soul_lantern"),
}
ST = Style(RC, "rc", PAL, {1: f"{RC}/f1_rooms", 2: f"{RC}/f2_rooms", 3: f"{RC}/f2_rooms"},
           {1: f"{RC}/f1_leaves", 2: f"{RC}/f2_leaves", 3: f"{RC}/f2_leaves"}, "ruined_citadel", {1: 11, 2: 13, 3: 15})
THEME1 = Theme(
    [("minecraft:stone", 40), ("minecraft:andesite", 14), ("minecraft:tuff", 8), ("minecraft:gravel", 4), ("minecraft:granite", 6),
     (COB, 6), ("minecraft:coal_ore", 3), ("minecraft:iron_ore", 3), ("minecraft:copper_ore", 2), ("minecraft:emerald_ore", 1)],
    [("minecraft:stone", 40), ("minecraft:tuff", 14), ("minecraft:deepslate", 16), ("minecraft:andesite", 8), ("minecraft:iron_ore", 3),
     ("minecraft:coal_ore", 3), ("minecraft:diamond_ore", 1), ("minecraft:lapis_ore", 2)],
    [("minecraft:deepslate", 60), ("minecraft:tuff", 12), ("minecraft:cobbled_deepslate", 14), ("minecraft:deepslate_iron_ore", 3),
     ("minecraft:deepslate_diamond_ore", 1), ("minecraft:deepslate_gold_ore", 2)],
    [(SB, 56), (CSB, 18), (MSB, 10), (COB, 8), (PA, 8)],
    [("minecraft:stone_brick_stairs", 80), ("minecraft:cobblestone_stairs", 12), ("minecraft:mossy_stone_brick_stairs", 8)],
    (SB, PA))
THEME2 = Theme(THEME1.rock_top, THEME1.rock_mid, THEME1.rock_deep, [(TB, 60), ("minecraft:tuff", 20), (PT, 10), (CTB, 10)],
               [("minecraft:tuff_brick_stairs", 80), ("minecraft:cobbled_deepslate_stairs", 12), ("minecraft:polished_tuff_stairs", 8)], (TB, PT))
LVL = {1: 54, 2: 56, 3: 57}


def L(fl, d=0):
    return LVL[fl] + d


# ---------------------------------------------------------------------------------------------------------------------
# Mobs: knights and archers in armour, castle guards (vindicators, pillagers)
# ---------------------------------------------------------------------------------------------------------------------
def knight(n, lvl, name="Citadel Knight", elite=False, fl=1):
    gear = ("minecraft:iron_sword", "minecraft:iron_helmet", "minecraft:iron_chestplate") if fl == 1 else \
        ("minecraft:diamond_sword", "minecraft:diamond_helmet", "minecraft:iron_chestplate")
    return mob("minecraft:zombie", n, lvl, name, elite, mainhand=gear[0], head=gear[1], chest=gear[2], legs="minecraft:iron_leggings")


def archer(n, lvl, name="Castle Archer", fl=1):
    return mob("minecraft:skeleton", n, lvl, name, False, mainhand="minecraft:bow", head="minecraft:iron_helmet",
               chest="minecraft:chainmail_chestplate" if fl == 1 else "minecraft:iron_chestplate")


def skel_knight(n, lvl, name="Skeleton Knight", elite=False):
    return mob("minecraft:skeleton", n, lvl, name, elite, mainhand="minecraft:diamond_sword", head="minecraft:iron_helmet",
               chest="minecraft:iron_chestplate", legs="minecraft:iron_leggings")


def guard(n, lvl, name="Castle Guard", elite=False):
    return mob("minecraft:vindicator", n, lvl, name, elite, mainhand="minecraft:iron_axe", head="minecraft:iron_helmet")


def crossbow(n, lvl, name="Castle Crossbowman"):
    return mob("minecraft:pillager", n, lvl, name, False, mainhand="minecraft:crossbow", head="minecraft:chainmail_helmet")


def std_waves(fl, a=0):
    if fl == 1:
        return [[knight(4, L(fl, a)), archer(2, L(fl, a + 1))], [guard(3, L(fl, a + 1)), skel_knight(2, L(fl, a + 1)), crossbow(1, L(fl, a))]]
    return [[knight(3, L(fl, a), fl=2), archer(3, L(fl, a + 1), fl=2)], [guard(3, L(fl, a + 1)), skel_knight(3, L(fl, a + 2)), crossbow(2, L(fl, a + 1))]]


# ---------------------------------------------------------------------------------------------------------------------
# Rooms
# ---------------------------------------------------------------------------------------------------------------------
def room(path, w, h, d, fl, tail=True):
    r = Room(ST, path, w, h, d, fl)
    r.shell()
    r.door("south", "side_in")
    if tail:
        r.door("north", "tail_out")
    r.lane("x")
    return r


def spine(r, out_pool, sides):
    r.door("south", "spine_in")
    r.door("north", "spine_out", pool=out_pool)
    for s, kind in sides:
        r.door(s, kind)
    r.lane("x")
    if sides:
        zc = r.d // 2
        r.reserve(0, zc - 1, r.w - 1, zc + 1)


def hall(path, fl, w, h, out_pool, sides):
    r = Room(ST, path, w, h, 19, fl)
    r.shell()
    r.ribs(6, 3)
    spine(r, out_pool, sides)
    return r


def table_row(r, x, z, n, along="x", block="minecraft:dark_oak_slab[type=top]"):
    for i in range(n):
        xx, zz = (x + i, z) if along == "x" else (x, z + i)
        if r.free(xx, zz):
            r.set(xx, 1, zz, block)
            r.keep.add((xx, zz))


def hall_courtyard(pack, fl, tag, w, out_pool, sides):
    """A sunken courtyard: a ruined well, dead trees, fallen statues, a broken colonnade."""
    r = hall(f"f{fl}/courtyard_{tag}", fl, w, 14, out_pool, sides)
    cx = w // 2
    r.ribs(6, 3)
    for x in (cx - 6, cx + 6):
        for z in (3, 7, 12, 16):
            r.col(x, z, y1=r.rng.choice([8, 10, 11]))
    for (x, z) in ((cx - 4, 5), (cx + 4, 14)):          # dead trees
        for y in range(1, 6):
            r.set(x, y, z, "minecraft:dark_oak_log[axis=y]")
        for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            r.set(x + dx, 4, z + dz, "minecraft:dark_oak_log[axis=y]")
        r.keep.add((x, z))
    for dx in range(-1, 2):                              # the well
        for dz in range(-1, 2):
            if (dx, dz) != (0, 0):
                r.set(cx + 4 + dx, 1, 9 + dz, COB if dx * dz else "minecraft:cobblestone_wall")
    r.set(cx + 4, 1, 9, "minecraft:water_cauldron[level=3]")
    r.reserve(cx + 3, 8, cx + 5, 10)
    for x, z in ((cx - 4, 10), (cx + 2, 4)):             # a fallen statue: plinth, torso, skull
        r.set(x, 1, z, PA)
        r.set(x + 1, 1, z, "minecraft:stone_brick_slab[type=bottom]")
        r.set(x, 2, z, "minecraft:skeleton_skull[rotation=6]")
        r.keep |= {(x, z), (x + 1, z)}
    r.banners((4, 14), y=6, color="red")
    r.banners((9,), y=6, color="white")
    for z in (3, 9, 15):
        r.hang(cx - 3, z, 3)
        r.hang(cx + 3, z, 3)
    r.rubble(8, 2, 2, w - 3, 16)
    r.chest(2, 2, "south", "common")
    r.chest(w - 3, 16, "north", "common" if fl == 1 else "rare")
    r.arrows(0, 3, 5, "east")
    r.arrows(w - 1, 3, 13, "west")
    r.spikes(cx - 8, 11, 1)
    r.spawn(cx, 9, *std_waves(fl), radius=10, spread=5)
    return r.finish(pack)


def hall_barracks(pack, fl, tag, w, out_pool, sides):
    r = hall(f"f{fl}/barracks_{tag}", fl, w, 11, out_pool, sides)
    cx = w // 2
    for z in (3, 5, 13, 15):
        r.set(1, 1, z, "minecraft:red_bed[facing=west,part=head]")
        r.set(2, 1, z, "minecraft:red_bed[facing=west,part=foot]")
        r.set(w - 2, 1, z, "minecraft:red_bed[facing=east,part=head]")
        r.set(w - 3, 1, z, "minecraft:red_bed[facing=east,part=foot]")
        r.reserve(1, z, 2, z)
        r.reserve(w - 3, z, w - 2, z)
    for z in (4, 14):
        for x in (3, w - 4):
            r.set(x, 1, z, "minecraft:barrel[facing=up]")
            r.keep.add((x, z))
    for x0 in (cx - 5, cx + 3):
        table_row(r, x0, 8, 3)
        table_row(r, x0, 10, 3)
        for x in range(x0, x0 + 3):
            if r.free(x, 7):
                r.set(x, 1, 7, "minecraft:dark_oak_stairs[facing=south]")
            if r.free(x, 11):
                r.set(x, 1, 11, "minecraft:dark_oak_stairs[facing=north]")
    r.set(3, 1, 8, "minecraft:grindstone[face=floor,facing=east]")
    r.set(w - 4, 1, 8, "minecraft:smoker[facing=west,lit=true]")
    r.keep |= {(3, 8), (w - 4, 8)}
    r.chest(2, r.d - 3, "north", "common")
    r.chest(w - 3, 2, "south", "common" if fl == 1 else "rare")
    for z in (3, 9, 15):
        r.hang(cx - 4, z)
        r.hang(cx + 4, z)
    r.banners((6, 12), y=5, color="red")
    r.rubble(4, 4, 2, w - 5, 16)
    r.arrows(w - 1, 3, 4, "west")
    waves = std_waves(fl, 0)
    waves[1][0] = knight(3, L(fl, 2), "Citadel Sergeant", True, fl)
    r.spawn(cx, 9, *waves, radius=10, spread=5)
    return r.finish(pack)


def hall_stables(pack, fl, tag, w, out_pool, sides):
    r = hall(f"f{fl}/stables_{tag}", fl, w, 10, out_pool, sides)
    cx = w // 2
    for z in (3, 6, 12, 15):
        for x0, step in ((1, 1), (w - 2, -1)):
            for dx in (0, step, 2 * step):
                r.set(x0 + dx, 1, z, "minecraft:hay_block[axis=x]" if dx == 0 else "minecraft:air")
            r.set(x0 + 3 * step, 1, z, "minecraft:dark_oak_fence")
            r.set(x0 + 3 * step, 2, z, "minecraft:dark_oak_fence")
            r.keep |= {(x0 + dx * step if False else x0 + dx, z) for dx in (0, step, 2 * step, 3 * step)}
        r.set(cx - 2, 1, z, "minecraft:dark_oak_fence") if z in (3, 15) and r.free(cx - 2, z) else None
    r.p.entity(2, 1, 3, {"id": "minecraft:skeleton_horse", "NoAI": True, "Silent": True, "Tame": True, "PersistenceRequired": True})
    r.p.entity(w - 3, 1, 15, {"id": "minecraft:skeleton_horse", "NoAI": True, "Silent": True, "Tame": True, "PersistenceRequired": True})
    for x, z in ((cx - 4, 9), (cx + 4, 9)):
        r.set(x, 1, z, "minecraft:hay_block[axis=y]")
        r.keep.add((x, z))
    for z in (3, 9, 15):
        r.hang(cx - 3, z)
        r.hang(cx + 3, z)
    r.chest(w - 3, 2, "south", "common")
    r.chest(2, 16, "north", "common" if fl == 1 else "rare")
    r.rubble(6, 4, 2, w - 5, 16)
    r.spikes(cx + 6, 11, 1)
    r.spawn(cx, 9, [knight(4, L(fl, 1)), skel_knight(2, L(fl, 1)), archer(2, L(fl, 2))],
            [guard(3, L(fl, 2)), knight(3, L(fl, 2), "Citadel Rider", True)], radius=10, spread=5)
    return r.finish(pack)


def hall_mess(pack, fl, tag, w, out_pool, sides):
    r = hall(f"f{fl}/mess_{tag}", fl, w, 11, out_pool, sides)
    cx = w // 2
    for x0 in (cx - 6, cx + 4):
        for z in (4, 14):
            table_row(r, x0, z, 3)
    for x in (cx - 7, cx + 7):
        r.col(x, 9, accent=GILD)
    r.set(2, 1, 2, "minecraft:furnace[facing=south,lit=true]")
    r.set(3, 1, 2, "minecraft:smoker[facing=south,lit=true]")
    r.set(w - 3, 1, 2, "minecraft:blast_furnace[facing=south,lit=true]")
    r.keep |= {(2, 2), (3, 2), (w - 3, 2)}
    for z in (3, 9, 15):
        r.hang(cx - 3, z)
        r.hang(cx + 3, z)
    r.banners((5, 9, 13), y=5, color="white")
    r.chest(w - 3, 16, "north", "common")
    r.chest(2, 16, "north", "common")
    r.arrows(0, 3, 14, "east")
    r.spawn(cx, 9, *std_waves(fl, 1), radius=10, spread=5)
    return r.finish(pack)


def hall_library(pack, fl, tag, w, out_pool, sides):
    r = hall(f"f{fl}/library_{tag}", fl, w, 12, out_pool, sides)
    cx = w // 2
    for x in (1, w - 2):
        for z in range(2, 17):
            if abs(z - 9) > 1 or not sides:
                for y in (1, 2, 3, 4):
                    r.set(x, y, z, "minecraft:bookshelf")
    for x in (cx - 5, cx + 5):
        for z in (4, 8, 14):
            for y in (1, 2):
                r.set(x, y, z, "minecraft:bookshelf")
            r.set(x, 3, z, "minecraft:chiseled_bookshelf[facing=south]") if False else None
            r.keep.add((x, z))
    for x, z, face in ((cx - 3, 5, "east"), (cx + 3, 13, "west")):
        r.set(x, 1, z, f"minecraft:lectern[facing={face}]")
        r.keep.add((x, z))
    for z in (3, 9, 15):
        r.hang(cx - 3, z, 3)
        r.hang(cx + 3, z, 3)
    r.chest(2, 2, "south", "rare")
    r.chest(w - 3, 16, "north", "common")
    r.rubble(5, 3, 2, w - 4, 16, ["minecraft:bookshelf", "minecraft:cobweb", "minecraft:gravel"])
    r.arrows(0, 6, 4, "east")
    r.spikes(cx + 7, 12, 1)
    r.spawn(cx, 9, *std_waves(fl, 0), radius=10, spread=5)
    return r.finish(pack)


def hall_great(pack, fl, tag, w, out_pool, sides):
    r = hall(f"f{fl}/great_{tag}", fl, w, 13, out_pool, sides)
    cx = w // 2
    for x in (cx - 5, cx + 5):
        for z in (3, 6, 12, 15):
            r.col(x, z, accent=GILD)
    for z in range(1, 18):
        for x in (cx - 1, cx, cx + 1):
            if r.get(x, 1, z) == AIR:
                r.set(x, 1, z, "minecraft:red_carpet")
    for x in (cx - 3, cx + 3):
        for z in (2, 16):
            r.brazier(x, z)
    for z in (3, 9, 15):
        r.hang(cx - 3, z, 3)
        r.hang(cx + 3, z, 3)
    r.banners((4, 9, 14), y=7, color="red")
    r.chest(2, 2, "south", "rare")
    r.chest(w - 3, 16, "north", "common")
    r.rubble(6, 2, 2, w - 3, 16)
    r.arrows(0, 3, 6, "east")
    r.arrows(w - 1, 3, 12, "west")
    r.spikes(cx - 8, 11, 1)
    r.spawn(cx, 9, *std_waves(fl, 2), radius=10, spread=5)
    return r.finish(pack)


def hall_chapel(pack, fl, tag, w, out_pool, sides):
    r = hall(f"f{fl}/chapel_{tag}", fl, w, 13, out_pool, sides)
    cx = w // 2
    for z in (4, 7, 11, 14):                              # pews
        for x0 in (cx - 4, cx + 2):
            for x in range(x0, x0 + 3):
                if r.free(x, z):
                    r.set(x, 1, z, "minecraft:spruce_stairs[facing=north]")
                    r.keep.add((x, z))
    for z, b in ((3, "minecraft:quartz_slab[type=bottom]"), (4, "minecraft:chiseled_quartz_block"), (5, "minecraft:quartz_slab[type=bottom]")):
        r.set(1, 1, z, b)                                 # the altar against the west wall
        r.keep.add((1, z))
    r.set(1, 2, 4, "minecraft:candle[candles=3,lit=true]")
    for z in (4, 14):
        for y in range(3, 8):
            r.set(0, y, z, "minecraft:blue_stained_glass")
    for z in (3, 9, 15):
        r.hang(cx, z, 3)
    for x in (cx - 6, cx + 6):
        r.col(x, 3, accent=GILD)
        r.col(x, 15, accent=GILD)
    r.banners((6, 12), y=7, color="white")
    r.chest(2, 2, "south", "rare")
    r.chest(w - 3, 16, "north", "common")
    r.arrows(w - 1, 3, 5, "west")
    r.spawn(cx, 9, [skel_knight(4, L(fl, 0)), archer(2, L(fl, 1), fl=2)],
            [knight(3, L(fl, 2), "Fallen Priest", True, 2), skel_knight(3, L(fl, 2))], radius=10, spread=5)
    return r.finish(pack)


def hall_gallery(pack, fl, tag, w, out_pool, sides):
    """A gallery of knight statues in alcoves, one of them hollow."""
    r = hall(f"f{fl}/gallery_{tag}", fl, w, 12, out_pool, sides)
    cx = w // 2
    for x in (2, w - 3):
        for z in (3, 7, 11, 15):
            for y in (1, 2):
                r.set(x, y, z, PT if y == 1 else "minecraft:iron_bars")
            r.set(x, 3, z, "minecraft:wither_skeleton_skull[rotation=0]")
            r.keep.add((x, z))
    for x in (cx - 4, cx + 4):
        for z in (5, 13):
            for y in range(1, 5):
                r.set(x, y, z, CTB if y in (2, 3) else PT)
            r.set(x, 5, z, "minecraft:iron_bars")
            r.keep.add((x, z))
    for z in (3, 9, 15):
        r.hang(cx - 3, z, 3)
        r.hang(cx + 3, z, 3)
    r.banners((5, 9, 13), y=6, color="gray")
    r.chest(3, 2, "south", "rare")
    r.chest(w - 4, 16, "north", "common")
    r.spikes(cx + 7, 8, 1)
    r.arrows(0, 3, 9, "east")
    r.spawn(cx, 9, *std_waves(fl, 1), radius=10, spread=5)
    return r.finish(pack)


HALL_FNS = {"courtyard": hall_courtyard, "barracks": hall_barracks, "stables": hall_stables, "mess": hall_mess,
            "library": hall_library, "great": hall_great, "chapel": hall_chapel, "gallery": hall_gallery}
SPINE = {
    1: {1: [("courtyard", "a", 27), ("barracks", "a", 23), ("stables", "a", 25)],
        2: [("mess", "a", 23), ("courtyard", "b", 25), ("barracks", "b", 25)]},
    2: {1: [("library", "a", 25), ("great", "a", 27), ("chapel", "a", 23)],
        2: [("gallery", "a", 25), ("great", "b", 25), ("library", "b", 23)]},
}
SPINE_SIDES = {1: [("west", "side_out"), ("east", "side_out")], 2: [("east", "side_out"), ("west", "leaf_out")]}


# -- side rooms ---------------------------------------------------------------------------------------------------------
def room_armoury(pack, fl):
    """Weapon racks behind a plate door: three plates in the courtyard-side alcoves open the inner stores."""
    r = room(f"f{fl}/armoury", 15, 9, 17, fl, tail=False)
    p = r.p
    for x in range(1, 14):
        p.set(x, 1, 8, SB if fl == 1 else TB) if False else None
    for x in (3, 7, 11):
        r.plate(x, 4, "armoury")
    for x in range(1, 14):
        for y in (1, 2, 3):
            r.set(x, y, 8, r.pal.wall)
    for x in (6, 7, 8):
        for y in (1, 2, 3):
            r.set(x, y, 8, "minecraft:iron_bars")
    p.marker(7, 2, 8, "door", group="armoury", message="The armoury grate rises.")
    r.text(7, 3, 6, "Stand on all three stones", "gray")
    for x in (2, 12):
        for z in (10, 12, 14):
            r.set(x, 1, z, "minecraft:fletching_table") if x == 2 and z == 12 else r.set(x, 1, z, "minecraft:dark_oak_fence")
            r.keep.add((x, z))
    r.chest(2, 15, "north", "rare")
    r.chest(12, 15, "north", "rare")
    r.chest(4, 15, "north", "common" if fl == 1 else "rare")
    r.hang(7, 12, 1)
    r.spawn(7, 5, [knight(3, L(fl, 1), fl=fl), archer(3, L(fl, 1), fl=fl)], radius=8, spread=3)
    return r.finish(pack)


def room_kitchen(pack, fl):
    r = room(f"f{fl}/kitchen", 17, 9, 15, fl)
    for x in (3, 6):
        r.set(x, 1, 3, "minecraft:furnace[facing=south,lit=true]")
        r.keep.add((x, 3))
    r.set(9, 1, 3, "minecraft:smoker[facing=south,lit=true]")
    r.keep.add((9, 3))
    table_row(r, 4, 7, 7)
    table_row(r, 4, 9, 7)
    for x in (2, 14):
        for z in (7, 9, 11):
            r.set(x, 1, z, "minecraft:barrel[facing=up]")
            r.keep.add((x, z))
    r.set(14, 1, 3, "minecraft:cauldron")
    r.chest(14, 13, "north", "common")
    r.hang(8, 5, 1)
    r.hang(8, 11, 1)
    r.spawn(8, 8, [knight(4, L(fl, 0), "Citadel Cook", False, fl), guard(2, L(fl, 1))], radius=8, spread=3)
    return r.finish(pack)


def room_store(pack, fl):
    r = room(f"f{fl}/storeroom", 15, 9, 15, fl)
    for x in (2, 4, 10, 12):
        for z in (3, 5, 9):
            r.set(x, 1, z, "minecraft:barrel[facing=up]")
            r.set(x, 2, z, "minecraft:barrel[facing=up]") if (x + z) % 4 == 0 else None
            r.keep.add((x, z))
    r.cache(1, 11, 4, 13, (2, 12), "east", r.pal.wall if fl == 1 else "minecraft:cracked_stone_bricks", "The Quartermaster's Cache")
    r.chest(12, 12, "north", "rare")
    r.hang(7, 4, 1)
    r.hang(7, 10, 1)
    r.spikes(7, 7, 1)
    r.spawn(7, 8, [knight(3, L(fl, 1), fl=fl), skel_knight(2, L(fl, 1)), archer(2, L(fl, 1), fl=fl)], radius=8, spread=3)
    return r.finish(pack)


def room_guardroom(pack, fl):
    r = room(f"f{fl}/guardroom", 17, 9, 13, fl)
    table_row(r, 6, 6, 5)
    for x in (3, 13):
        r.set(x, 1, 3, "minecraft:dark_oak_fence")
        r.set(x, 2, 3, "minecraft:dark_oak_fence")
        r.keep.add((x, 3))
    r.arrows(0, 3, 6, "east")
    r.arrows(16, 3, 6, "west")
    r.chest(2, 11, "north", "common" if fl == 1 else "rare")
    r.hang(8, 4, 1)
    r.banner(8, 5, 11, "north", "red") if False else None
    r.spawn(8, 6, [guard(3, L(fl, 2)), crossbow(3, L(fl, 2)), skel_knight(2, L(fl, 2))], radius=8, spread=3)
    return r.finish(pack)


def room_well(pack, fl):
    r = room(f"f{fl}/cellar_well", 15, 11, 15, fl)
    for dx in range(-2, 3):
        for dz in range(-2, 3):
            if max(abs(dx), abs(dz)) == 2:
                r.set(7 + dx, 1, 7 + dz, COB if (dx + dz) % 2 else "minecraft:cobblestone_wall")
            elif max(abs(dx), abs(dz)) == 1:
                r.set(7 + dx, 0, 7 + dz, "minecraft:blue_ice") if False else r.set(7 + dx, 0, 7 + dz, "minecraft:polished_deepslate")
    r.reserve(5, 5, 9, 9)
    for x, z in ((2, 2), (12, 2), (2, 12), (12, 12)):
        r.col(x, z, y1=8)
    r.chest(3, 7, "east", "rare")
    r.hang(7, 3, 2)
    r.hang(7, 11, 2)
    r.poison(7, 7, 2)
    r.spawn(7, 11, [skel_knight(3, L(fl, 1)), knight(3, L(fl, 2), fl=fl)], radius=8, spread=3)
    return r.finish(pack)


def room_scriptorium(pack, fl):
    r = room(f"f{fl}/scriptorium", 17, 9, 15, fl)
    for x in (1, 15):
        for z in range(2, 13):
            for y in (1, 2, 3):
                r.set(x, y, z, "minecraft:bookshelf")
    for z in (4, 8):
        for x in (4, 12):
            r.set(x, 1, z, "minecraft:lectern[facing=south]")
            r.keep.add((x, z))
    table_row(r, 6, 6, 5)
    r.chest(3, 13, "north", "rare")
    r.hang(8, 4, 1)
    r.hang(8, 9, 1)
    r.spawn(8, 7, [skel_knight(4, L(fl, 0)), archer(3, L(fl, 2), fl=2)], radius=8, spread=3)
    return r.finish(pack)


def room_treasury(pack, fl):
    """Three levers in the antechamber; get the order right and the treasury gate rises."""
    r = room(f"f{fl}/treasury", 15, 10, 17, fl, tail=False)
    p = r.p
    for x in range(1, 14):
        for y in (1, 2, 3):
            r.set(x, y, 7, r.pal.wall)
    for x in (6, 7, 8):
        for y in (1, 2, 3):
            r.set(x, y, 7, "minecraft:iron_bars")
    p.marker(7, 2, 7, "door", group="levers", message="The treasury gate grinds open.")
    for z, want in ((10, True), (12, False), (14, True)):
        r.lever(1, 2, z, "east", want, "levers")
    r.sign(1, 3, 12, ["UP, DOWN, UP", "so the old", "knights swore"], "east") if False else r.text(2, 3, 12, "Up, down, up. Like the banners.", "gold")
    r.banner(13, 5, 10, "west", "red")
    r.banner(13, 5, 12, "west", "white")
    r.banner(13, 5, 14, "west", "red")
    r.chest(3, 2, "south", "vault")
    r.chest(11, 2, "south", "vault")
    r.chest(7, 2, "south", "rare")
    r.hang(7, 4, 1)
    r.spawn(7, 12, [skel_knight(3, L(fl, 1)), guard(3, L(fl, 1)), archer(2, L(fl, 2), fl=2)], radius=8, spread=3)
    return r.finish(pack)


def room_solar(pack, fl):
    r = room(f"f{fl}/solar", 17, 10, 15, fl)
    r.set(2, 1, 3, "minecraft:red_bed[facing=south,part=head]")
    r.set(2, 1, 4, "minecraft:red_bed[facing=south,part=foot]")
    r.set(14, 1, 3, "minecraft:red_bed[facing=south,part=head]")
    r.set(14, 1, 4, "minecraft:red_bed[facing=south,part=foot]")
    r.keep |= {(2, 3), (2, 4), (14, 3), (14, 4)}
    for x in range(5, 12):
        for z in range(5, 10):
            r.set(x, 1, z, "minecraft:red_carpet")
    table_row(r, 7, 7, 3)
    r.chest(8, 2, "south", "rare")
    r.chest(14, 12, "north", "common")
    r.banner(8, 5, 12, "north", "red") if False else None
    r.hang(8, 4, 2)
    r.hang(8, 10, 2)
    r.spawn(8, 8, [knight(4, L(fl, 1), "Citadel Noble", True, fl), skel_knight(3, L(fl, 1))], radius=8, spread=3)
    return r.finish(pack)


def room_cells(pack, fl):
    r = room(f"f{fl}/cells", 17, 9, 15, fl)
    for x0 in (2, 10):
        for z0 in (3, 8):
            for dx in range(0, 5):
                for dz in range(0, 4):
                    if dx in (0, 4) or dz in (0, 3):
                        if not (dx == 2 and dz == 3 and False):
                            for y in (1, 2, 3):
                                r.set(x0 + dx, y, z0 + dz, BARS)
            r.set(x0 + 2, 1, z0 + 2, "minecraft:hay_block[axis=y]")
            r.keep |= {(x0 + dx, z0 + dz) for dx in range(5) for dz in range(4)}
    r.chest(3, 13, "north", "common" if fl == 1 else "rare")
    r.hang(8, 6, 1)
    r.spawn(8, 7, [skel_knight(3, L(fl, 1)), guard(2, L(fl, 1)), knight(2, L(fl, 1), fl=fl)], radius=8, spread=3)
    return r.finish(pack)


def room_shrine(pack, fl):
    r = room(f"f{fl}/shrine", 13, 9, 13, fl)
    for dx in range(-1, 2):
        for dz in range(-1, 2):
            r.set(6 + dx, 1, 6 + dz, "minecraft:polished_andesite" if fl == 1 else PT)
    r.set(6, 2, 6, "minecraft:soul_campfire[facing=north,lit=true]")
    r.keep |= {(x, z) for x in range(5, 8) for z in range(5, 8)}
    for x, z in ((2, 2), (10, 2), (2, 10), (10, 10)):
        r.col(x, z, y1=6)
    r.chest(3, 11, "north", "rare")
    r.hang(6, 3, 2)
    r.spawn(6, 9, [skel_knight(3, L(fl, 2), "Shrine Knight", True), archer(3, L(fl, 1), fl=fl)], radius=8, spread=3)
    return r.finish(pack)


ROOMS = {1: [(room_armoury, 3), (room_kitchen, 3), (room_store, 3), (room_guardroom, 3), (room_well, 2)],
         2: [(room_scriptorium, 3), (room_treasury, 3), (room_solar, 2), (room_cells, 3), (room_shrine, 3), (room_armoury, 2)]}


def leaf(path, w, d, fl):
    r = Room(ST, path, w, 7, d, fl)
    r.shell()
    r.door("south", "tail_in")
    r.lane("x")
    return r


def leaf_closet(pack, fl):
    r = leaf(f"f{fl}/leaf_closet", 9, 9, fl)
    for x in (2, 6):
        r.set(x, 1, 2, "minecraft:barrel[facing=up]")
        r.keep.add((x, 2))
    r.chest(4, 1, "south", "common" if fl == 1 else "rare")
    r.hang(4, 4, 1)
    r.spawn(4, 5, [knight(4, L(fl, 1), fl=fl), archer(2, L(fl, 1), fl=fl)], radius=7, spread=2)
    return r.finish(pack)


def leaf_nook(pack, fl):
    r = leaf(f"f{fl}/leaf_nook", 9, 11, fl)
    r.cache(1, 1, 4, 4, (2, 2), "south", CSB if fl == 1 else "minecraft:tuff", "The Deserter's Nook")
    r.set(7, 1, 2, "minecraft:barrel[facing=up]")
    r.keep.add((7, 2))
    r.hang(4, 6, 1)
    r.spikes(6, 6, 1)
    r.spawn(4, 6, [skel_knight(4, L(fl, 1)), guard(2, L(fl, 1))], radius=7, spread=2)
    return r.finish(pack)


def leaf_post(pack, fl):
    r = leaf(f"f{fl}/leaf_post", 11, 9, fl)
    r.arrows(10, 3, 4, "west", radius=8)
    r.chest(2, 1, "south", "common" if fl == 1 else "rare")
    r.set(8, 1, 2, "minecraft:grindstone[face=floor,facing=east]")
    r.keep.add((8, 2))
    r.hang(5, 4, 1)
    r.spawn(5, 4, [guard(3, L(fl, 2)), crossbow(2, L(fl, 2)), skel_knight(1, L(fl, 2))], radius=7, spread=2)
    return r.finish(pack)


LEAVES = [leaf_closet, leaf_nook, leaf_post]


# ---------------------------------------------------------------------------------------------------------------------
# The surface: the ruined Gatehouse (start piece "entrance") and the spiral stairwell under it
# ---------------------------------------------------------------------------------------------------------------------
STAIR_H = 20
GATE_H = 16


def build_entrance(pack):
    Wd = 25
    gate = Room(ST, "entrance", Wd, GATE_H, Wd, 1)
    stair = Room(ST, "stairwell", Wd, STAIR_H, Wd, 1)
    g = gate.p
    rng = gate.rng
    split = Split(g, stair.p, STAIR_H)
    sh = Shaft(stair, THEME1, p=split)
    sh.fill_rock(STAIR_H - 1)
    # the plaza (a circle): paving in front, ground behind
    for x in range(Wd):
        for z in range(Wd):
            rr = math.hypot(x - 12, z - 12)
            if rr > 11.4:
                continue
            if z >= 13 and rr <= 9.6:
                g.set(x, 0, z, rng.choice([SB, SB, CSB, PA, COB, MSB]))
            else:
                g.set(x, 0, z, wchoice(rng, [("minecraft:grass_block", 45), ("minecraft:coarse_dirt", 15), ("minecraft:stone", 25), ("minecraft:gravel", 15)]))
    for x in range(Wd):
        for z in range(13, Wd):
            if math.hypot(x - 12, z - 12) <= 9.6:
                for y in range(1, 11):
                    if g.get(x, y, z) is None:
                        g.set(x, y, z, AIR)
    # the gatehouse mass: a solid brick body with two broken towers, built into the slope (x 3..21, z 0..12)
    def jag(x, z, base, var):
        return max(0, base - int(rng.random() * var))
    for x in range(3, 22):
        for z in range(0, 13):
            tower = x <= 8 or x >= 16
            top = jag(x, z, 13 if tower else 8, 5 if tower else 3)
            if z < 3:
                top = min(top, 6)
            for y in range(0, top + 1):
                g.set(x, y, z, wchoice(rng, [(SB, 56), (CSB, 22), (MSB, 12), (COB, 6), (CHSB, 4)]))
    for x, z in ((3, 12), (8, 12), (16, 12), (21, 12)):       # tower corner buttresses
        for y in range(0, 5):
            g.set(x, y, z, PA)
    # the arch: a 3-wide opening in the facade (z=12) leading to the tunnel (z 6..12) and the landing
    for z in range(6, 13):
        for x in (11, 12, 13):
            g.set(x, 0, z, PA if (x + z) % 3 else SB)
            for y in range(1, 5):
                sh.carve(x, y, z)
    for x in (10, 14):
        for y in range(1, 7):
            g.set(x, y, 12, PA if y % 2 == 0 else CHSB)
    for x in range(10, 15):
        g.set(x, 5, 12, CHSB)
        g.set(x, 6, 12, SB)
    for x in (11, 12, 13):
        g.set(x, 4, 12, BARS)                                   # the rusted portcullis, half raised
    g.set(12, 4, 12, AIR)
    for x, y in ((14, 6), (15, 8), (7, 9), (17, 10), (4, 12), (20, 11)):
        if g.get(x, y, 12) is not None:
            g.set(x, y, 12, AIR)
    waypoints = [(12, 4), (20, 4), (20, 20)]
    landings, legs = sh.helix(waypoints, 0)
    assert landings[-1][2] == -STAIR_H, landings
    for x in (22, 23, 24):
        for z in (19, 20, 21):
            split.set(x, -STAIR_H, z, THEME1.landing[0])
            for y in range(1, 5):
                sh.carve(x, -STAIR_H + y, z)
    sh.line()
    for z in (7, 10):
        g.set(11, 4, z, "minecraft:lantern[hanging=true]")
        g.set(13, 4, z, "minecraft:lantern[hanging=true]")
    # plaza dressing: braziers, banners on poles, a collapsed statue, fallen stones, the waystone and the dungeon marker
    for x in (8, 16):
        g.set(x, 1, 14, "minecraft:polished_blackstone_brick_wall")
        g.set(x, 2, 14, "minecraft:polished_blackstone_brick_wall")
        g.set(x, 3, 14, "minecraft:soul_campfire[facing=north,lit=true]")
    for x in (5, 19):
        for y in range(1, 6):
            g.set(x, y, 17, "minecraft:dark_oak_fence")
        g.set(x, 5, 18, "minecraft:red_banner[rotation=8]") if False else None
        g.set(x, 6, 17, "minecraft:red_banner[rotation=0]")
    for x in range(17, 20):
        for z in range(19, 22):
            g.set(x, 1, z, PA)
    g.set(18, 2, 20, "minecraft:stone_brick_wall")
    g.set(18, 3, 20, "minecraft:stone_brick_wall")
    g.set(18, 4, 20, CSB)
    g.set(17, 2, 20, "minecraft:stone_brick_stairs[facing=east,half=bottom]")
    g.set(18, 2, 19, "minecraft:skeleton_skull[rotation=4]")
    for x, z, b in ((14, 16, CHSB), (15, 17, "minecraft:stone_brick_stairs[facing=east]"), (13, 19, "minecraft:stone_brick_slab[type=bottom]"),
                    (10, 20, COB), (11, 17, CSB), (16, 20, "minecraft:cobblestone_slab[type=bottom]"), (7, 20, MCOB)):
        g.set(x, 1, z, b)
    g.set(7, 1, 17, "skyrpg_world:waystone")
    g.marker(12, 1, 16, "dungeon", dungeon=DUN)
    # landings: packs, a chest, an arrow trap in the long leg
    (_, _, _), (x2, z2, y2), (x3, z3, y3) = landings
    lo = lambda y: y + STAIR_H  # noqa: E731
    stair.spawn(x2, z2, [knight(4, L(1, 0)), archer(2, L(1, 1))], [guard(3, L(1, 1)), skel_knight(2, L(1, 1))], radius=9, spread=2, y=lo(y2) + 1)
    stair.spawn(x3, z3, [skel_knight(3, L(1, 1)), archer(3, L(1, 1))], [knight(3, L(1, 1), "Gate Sergeant", True), guard(2, L(1, 1))],
                radius=9, spread=2, y=lo(y3) + 1)
    stair.chest(21, 3, "south", "common", y=lo(y2) + 1)
    stair.chest(21, 21, "north", "common", y=lo(y3) + 1)
    cx, cz, yf = legs[1][1][len(legs[1][1]) // 2]
    stair.arrows(cx - 2, lo(yf + 2), cz, "east", radius=8, damage=11, every=50)
    stair.door("east", "spine_out", pool=f"{RC}/f1_s1", at=20, y=1)
    g.jigsaw(12, 0, 14, "down_north", "rc_stair_out", "rc_stair_in", f"{RC}/stairwell", final_state=SB)
    stair.p.jigsaw(12, STAIR_H - 1, 14, "up_north", "rc_stair_in", "rc_stair_in", "minecraft:empty", final_state=SB)
    for rm in (gate, stair):
        rm.finish(pack)
    return gate.p, stair.p


# ---------------------------------------------------------------------------------------------------------------------
# The Knight-Captain's Hall (floor 1 champion): 29 x 13 x 21, the north door is shut with a bar plug until he falls
# ---------------------------------------------------------------------------------------------------------------------
def build_champion(pack):
    w, h, d = 29, 13, 21
    r = Room(ST, "f1/champion", w, h, d, 1)
    r.shell()
    r.ribs(7, 4)
    p = r.p
    cx = w // 2
    r.door("south", "spine_in")
    r.door("north", "spine_out", pool=f"{RC}/descent1")
    for z in (2, 3):
        for x in (cx - 1, cx, cx + 1):
            for y in (1, 2, 3):
                p.set(x, y, z, BARS)
    p.marker(cx, 2, 2, "door", group="captain", message="The portcullis holds. Slay the Knight-Captain.")
    r.reserve(cx - 2, 0, cx + 2, 4)
    for x in range(cx - 5, cx + 6):                        # the dais: a raised round, a planted sword
        for z in range(7, 16):
            dist = math.hypot(x - cx, z - 11)
            if dist <= 4.6:
                p.set(x, 0, z, GILD if dist <= 1.8 else (CHSB if dist <= 3.2 else PA))
    r.reserve(cx - 5, 6, cx + 5, 16)
    for x in (cx - 6, cx + 6):
        for z in (7, 15):
            r.col(x, z, y1=h - 4, accent=GILD)
    for x, z in ((cx - 4, 5), (cx + 4, 5), (cx - 4, 17), (cx + 4, 17)):
        r.brazier(x, z)
    r.banners((6, 11, 16), y=7, color="red")
    for z in (6, 11, 16):
        r.hang(cx - 3, z, 3)
        r.hang(cx + 3, z, 3)
    r.chest(5, d - 3, "north", "rare")
    r.chest(w - 6, d - 3, "north", "rare")
    r.arrows(0, 3, 11, "east", radius=11, damage=14)
    r.arrows(w - 1, 3, 11, "west", radius=11, damage=14)
    r.spikes(cx - 8, 11, 1)
    r.spikes(cx + 8, 11, 1)
    r.spawn(cx, 18, [knight(4, L(1, 2)), archer(3, L(1, 2))], [guard(3, L(1, 3)), skel_knight(3, L(1, 3))], radius=9, spread=3)
    p.marker(cx, 2, 11, "boss", boss="skyrpg_world:knight_captain", radius=13, group="captain")
    return r.finish(pack)


# ---------------------------------------------------------------------------------------------------------------------
# Floor 3: the Hall of Banners (a portcullis puzzle) and the Throne Room (boss, key-door vault, hoard nook)
# ---------------------------------------------------------------------------------------------------------------------
def build_approach(pack):
    w, h, d = 25, 12, 23
    r = Room(ST, "f3/approach", w, h, d, 3)
    r.shell()
    r.ribs(6, 3)
    p = r.p
    cx = w // 2
    r.door("south", "spine_in")
    r.door("north", "spine_out", pool=f"{RC}/throne")
    r.lane("x")
    for z in (3, 4):
        for x in range(1, w - 1):
            for y in (1, 2, 3, 4):
                if x in (cx - 1, cx, cx + 1) or r.get(x, y, z) == AIR:
                    p.set(x, y, z, BARS if x in (cx - 1, cx, cx + 1) else r.pal.wall)
    p.marker(cx, 2, 3, "door", group="gate", message="The portcullis grinds up.")
    r.reserve(0, 2, w - 1, 5)
    for z, want in ((9, True), (12, False), (15, True)):
        r.lever(1, 2, z, "east", want, "gate")
    for z, col in ((9, "red"), (12, "white"), (15, "red")):
        r.banner(1, 5, z, "east", col)
        r.banner(w - 2, 5, z, "west", col)
    r.text(3, 3, 12, "Raised, lowered, raised. As the banners hang.", "gold")
    for x in (cx - 6, cx + 6):
        for z in (8, 11, 15, 18):
            r.col(x, z, y1=h - 4, accent=GILD)
    for z in (7, 13, 19):
        r.hang(cx - 3, z, 3)
        r.hang(cx + 3, z, 3)
    for x, z in ((cx - 3, 20), (cx + 3, 20)):
        r.brazier(x, z)
    r.chest(2, 20, "north", "rare")
    r.chest(w - 3, 20, "north", "rare")
    r.arrows(0, 3, 17, "east", radius=11, damage=15)
    r.arrows(w - 1, 3, 17, "west", radius=11, damage=15)
    r.spikes(cx - 8, 14, 1)
    r.spikes(cx + 8, 10, 1)
    r.spawn(cx, 14, [skel_knight(4, L(3, 0)), knight(3, L(3, 0), fl=2), archer(3, L(3, 1), fl=2)],
            [guard(3, L(3, 1)), skel_knight(3, L(3, 1), "Black Knight", True), crossbow(2, L(3, 1))], radius=10, spread=5)
    return r.finish(pack)


def build_throne(pack):
    w, d = 31, 31
    h = 21
    r = Room(ST, "boss/throne", w, h, d, 3)
    r.shell(inlay=False)
    p = r.p
    cx, cz = w // 2, 21
    r.door("south", "spine_in")
    r.reserve(cx - 1, 24, cx + 1, d - 1)
    top = h - 2
    for x in range(1, w - 1):                              # the partition row (z=7) and the alcove walls
        for y in range(1, top):
            if x <= 9 or x >= 21 or 12 <= x <= 18 and False:
                p.set(x, y, 7, PBB)
    for z in range(1, 8):
        for y in range(1, top):
            p.set(10, y, z, PBB)
            p.set(20, y, z, PBB)
    for x in (4, 5, 6):                                    # the vault door (key) and the hoard door (boss death)
        for y in (1, 2, 3):
            p.set(x, y, 7, "minecraft:crying_obsidian")
    p.marker(5, 2, 7, "key_door", key="skyrpg_world:citadel_crest_key", message="The vault is sealed. It needs the Citadel Crest Key.")
    for x in (24, 25, 26):
        for y in (1, 2, 3):
            p.set(x, y, 7, "minecraft:obsidian")
    p.marker(25, 2, 7, "door", group="knight", message="The Fallen Knight's hoard grinds open.")
    for xs in ((1, 9), (21, 29)):
        for x in range(xs[0], xs[1] + 1):
            for z in range(1, 7):
                for y in range(1, top):
                    p.set(x, y, z, AIR if y < 7 else PBB)
    p.chest(2, 1, 1, "south", "skyrpg_world:chests/ruined_citadel_vault", tier="vault")
    p.chest(8, 1, 1, "south", "skyrpg_world:chests/ruined_citadel_vault", tier="vault")
    p.chest(5, 1, 1, "south", "skyrpg_world:chests/ruined_citadel_rare", tier="rare")
    for pos, b in (((4, 1, 1), "minecraft:gold_block"), ((6, 1, 1), "minecraft:gold_block"), ((5, 5, 3), "minecraft:soul_lantern[hanging=true]")):
        p.set(*pos, b)
    p.chest(25, 1, 1, "south", "skyrpg_world:chests/ruined_citadel_secret", tier="secret")
    p.marker(25, 1, 2, "secret", name="The Fallen Knight's Hoard")
    for x in (23, 27):
        p.set(x, 1, 1, "minecraft:gold_block")
    p.set(25, 5, 3, "minecraft:soul_lantern[hanging=true]")
    r.sign(3, 2, 8, ["The vault is sealed", "behind a crest.", "Bring the Crest Key."], "south")
    # the throne: a stepped dais against the north wall between the two alcoves
    for x in range(11, 20):
        for z in range(1, 7):
            p.set(x, 0, z, GILD if (x + z) % 5 == 0 else PBB)
    for x in range(12, 19):
        p.set(x, 1, 4, PBB)
        p.set(x, 1, 5, "minecraft:polished_blackstone_brick_slab[type=bottom]")
    for y in range(1, 6):
        for x in (14, 15, 16):
            p.set(x, y, 1, PB if y < 4 else CHPB)
    for x in (14, 16):
        p.set(x, 5, 1, GILD)
    p.set(15, 2, 2, "minecraft:polished_blackstone_brick_stairs[facing=south]")
    for x in (12, 18):
        p.set(x, 2, 2, "minecraft:soul_campfire[facing=north,lit=true]")
        p.set(x, 1, 2, WALL_PBB)
    r.reserve(1, 1, w - 2, 7)
    r.banner(12, 8, 1, "south", "black")
    r.banner(18, 8, 1, "south", "black")
    for x in (3, 8, 23, 28):
        r.banner(x, 8, 8, "south", "red") if False else None
    # the arena: plain solid floor (the molten move only melts plain blocks); columns and braziers around the rim
    for x, z in ((6, 13), (24, 13), (6, 27), (24, 27), (9, 9), (21, 9)):
        r.col(x, z, y1=top - 1, accent=GILD)
    for x, z in ((cx - 7, cz), (cx + 7, cz), (cx, cz - 7), (cx, cz + 7), (9, 25), (21, 25), (9, 17), (21, 17)):
        r.hang(x, z, 4)
    for x, z in ((4, 21), (26, 21), (4, 15), (26, 15)):
        r.brazier(x, z)
    r.banners((11, 17, 23, 29), y=9, color="black")
    r.chest(4, 29, "north", "rare") if r.free(4, 29) else None
    r.arrows(5, 4, 7, "south", radius=15, damage=18) if False else None
    r.arrows(14, 4, 7, "south", radius=15, damage=18) if False else None
    r.spikes(9, 15, 1)
    r.spikes(21, 15, 1)
    r.rubble(8, 4, 10, w - 5, d - 3, [PBB, "minecraft:blackstone", "minecraft:cracked_polished_blackstone_bricks"])
    p.marker(cx, 2, cz, "boss", boss="skyrpg_world:fallen_knight", radius=18, group="knight")
    return r.finish(pack, exempt=("secret",))


# ---------------------------------------------------------------------------------------------------------------------
# Loot
# ---------------------------------------------------------------------------------------------------------------------
HELL_INGOT = M + "hellforged_ingot"
LUMEN, ATT_LUMEN, DUSK, PRISM_F, PRISM, HEART = (M + "lumenite_crystal", M + "attuned_lumenite", M + "duskquartz_shard",
                                                M + "prism_fragment", M + "prism", M + "heartstone")
DREAD = W + "dread_crystal"
EGG_RARE = [it("skyrpg_pets:pup_egg", 1, 1, 1), it("skyrpg_pets:mole_egg", 1, 1, 1)]
GOLDEN_EGG = "skyrpg_pets:golden_pet_egg"


def loot(pack):
    common = [it("minecraft:iron_ingot", 8, 4, 10), it("minecraft:gold_ingot", 7, 3, 8), it("minecraft:emerald", 6, 2, 5),
              it("minecraft:arrow", 6, 10, 24), it("minecraft:experience_bottle", 6, 3, 7), it("skyrpg_enchants:runic_dust", 6, 3, 6),
              it("minecraft:bread", 5, 4, 8), it(DUSK, 5, 2, 4), it(LUMEN, 4, 1, 3), it(M + "emberite_chunk", 4, 2, 4), book(3)]
    pack.loot_table("chests/ruined_citadel_common", table(
        lpool(rolls(4, 6), common),
        lpool(1, [it(C + "valor_mark", 3, 1, 1), it(C + "dread_fang", 3, 1, 1), it(DREAD, 1, 1, 1), it(PRISM_F, 2, 1, 1)], .24)))
    rare = [it("minecraft:diamond", 6, 2, 5), it("minecraft:emerald", 6, 4, 8), it("minecraft:gold_ingot", 6, 6, 12),
            it("minecraft:experience_bottle", 6, 5, 10), it("skyrpg_enchants:runic_dust", 8, 4, 9), it(DUSK, 7, 3, 6), it(LUMEN, 7, 2, 4),
            it(M + "emberite_ingot", 5, 2, 4), it(PRISM_F, 5, 1, 2), it(HELL_INGOT, 3, 1, 1), it(DREAD, 3, 1, 1), book(5)]
    pack.loot_table("chests/ruined_citadel_rare", table(
        lpool(rolls(5, 7), rare),
        lpool(1, [it(C + "elite_trophy", 3, 1, 1), it(C + "valor_mark", 4, 1, 2), it(C + "dread_fang", 3, 1, 2), it(HEART, 2, 1, 1), it(DREAD, 2, 1, 1)], .55),
        lpool(1, [it(C + "nightmare_core", 1, 1, 1)], .07),
        lpool(1, EGG_RARE, .035)))
    secret = [it("minecraft:diamond", 7, 3, 6), it("minecraft:emerald", 6, 5, 10), it("minecraft:gold_ingot", 7, 8, 16),
              it("skyrpg_enchants:runic_dust", 9, 5, 11), it(DUSK, 8, 4, 8), it(LUMEN, 8, 3, 5), it(ATT_LUMEN, 3, 1, 1), it(PRISM_F, 7, 1, 3),
              it(HELL_INGOT, 5, 1, 2), it(DREAD, 4, 1, 2), book(6)]
    pack.loot_table("chests/ruined_citadel_secret", table(
        lpool(rolls(5, 8), secret),
        lpool(1, [it(PRISM, 3, 1, 1), it(HEART, 3, 1, 2), it(C + "valor_seal", 3, 1, 1), it(C + "elite_trophy", 3, 1, 2)]),
        lpool(1, [it(C + "nightmare_core", 1, 1, 1)], .10),
        lpool(1, [it(GOLDEN_EGG, 1, 1, 1)], .016),
        lpool(1, EGG_RARE, .05)))
    vault = [it("minecraft:diamond", 8, 4, 8), it("minecraft:emerald", 7, 6, 12), it("minecraft:gold_ingot", 7, 10, 20),
             it("skyrpg_enchants:runic_dust", 9, 6, 14), it(DUSK, 8, 4, 8), it(LUMEN, 8, 3, 6), it(ATT_LUMEN, 4, 1, 2), it(PRISM_F, 8, 2, 4),
             it(HELL_INGOT, 6, 1, 3), it(DREAD, 5, 1, 2), it(C + "valor_seal", 4, 1, 2), it(C + "elite_trophy", 5, 1, 3), book(8)]
    pack.loot_table("chests/ruined_citadel_vault", table(
        lpool(rolls(6, 8), vault),
        lpool(1, [it(PRISM, 2, 1, 1), it(HEART, 3, 1, 2)], .6),
        lpool(1, [it(C + "nightmare_core", 1, 1, 1)], .15),
        lpool(1, [it(GOLDEN_EGG, 1, 1, 1)], .022),
        lpool(1, EGG_RARE, .06),
        lpool(1, [it("minecraft:enchanted_golden_apple", 1, 1, 1)], .15)))
    pack.loot_table("bosses/knight_captain", table(
        lpool(1, [it(W + "citadel_crest_key", 1)]),
        lpool(rolls(2, 4), [it("minecraft:diamond", 6, 2, 4), it("minecraft:emerald", 6, 4, 8), it(DUSK, 6, 3, 6), it(LUMEN, 6, 2, 4),
                            it("minecraft:experience_bottle", 6, 4, 9), it("skyrpg_enchants:runic_dust", 8, 5, 10), book(4)]),
        lpool(1, [it(PRISM_F, 1, 1, 2)], .45),
        lpool(1, [it(HELL_INGOT, 1, 1, 2)], .30),
        lpool(1, [it(DREAD, 1, 1, 1)], .25),
        lpool(1, [it(C + "elite_trophy", 1, 1, 1)], .35),
        lpool(1, [it(C + "valor_mark", 1, 1, 2)], .30),
        lpool(1, [it(C + "nightmare_core", 1, 1, 1)], .08),
        lpool(1, EGG_RARE, .04)))
    pack.loot_table("bosses/fallen_knight", table(
        lpool(1, [it(W + "fallen_crest", 1, 1, 1)]),                    # the trophy: always one ...
        lpool(1, [it(W + "fallen_crest", 1, 1, 1)], .25),               # ... and a quarter of the time a second
        lpool(1, [it(W + "citadel_crest_key", 1)]),
        lpool(1, [it(HELL_INGOT, 1, 2, 4)]),
        lpool(1, [it(PRISM_F, 1, 2, 4)]),
        lpool(1, [it(LUMEN, 1, 4, 8)]),
        lpool(1, [it(DUSK, 1, 6, 12)]),
        lpool(rolls(5, 7), [it("minecraft:diamond", 6, 4, 8), it("minecraft:emerald", 6, 8, 16), it("minecraft:gold_ingot", 6, 12, 24),
                            it("skyrpg_enchants:runic_dust", 9, 8, 16), it(DREAD, 5, 1, 3), it(ATT_LUMEN, 3, 1, 2),
                            it("minecraft:experience_bottle", 7, 8, 16), book(5)]),
        lpool(1, [it(PRISM, 1, 1, 1)], .30),
        lpool(1, [it(HEART, 1, 1, 2)], .35),
        lpool(1, [it(C + "valor_seal", 1, 1, 2)], .50),
        lpool(1, [it(C + "elite_trophy", 1, 1, 2)], .60),
        lpool(1, [it(C + "nightmare_core", 1, 1, 1)], .25),
        lpool(1, [it(GOLDEN_EGG, 1, 1, 1)], .04),
        lpool(1, EGG_RARE, .10)))
    pack.loot_table("rewards/fallen_knight", table(
        lpool(rolls(5, 7), [it("skyrpg_enchants:runic_dust", 9, 8, 16), it("minecraft:diamond", 7, 5, 10), it("minecraft:emerald", 7, 8, 16),
                            it("minecraft:experience_bottle", 7, 8, 16), it(DUSK, 8, 5, 10), it(LUMEN, 8, 4, 8), it(PRISM_F, 8, 2, 5),
                            it(HELL_INGOT, 6, 1, 3), it(DREAD, 5, 1, 3), it(C + "valor_seal", 5, 1, 2), it(C + "elite_trophy", 5, 1, 3),
                            it(HEART, 4, 1, 2), it(ATT_LUMEN, 3, 1, 2), book(6)]),
        lpool(1, [it(PRISM, 1, 1, 1)], .25),
        lpool(1, [it(C + "nightmare_core", 1, 1, 1)], .22),
        lpool(1, [it(GOLDEN_EGG, 1, 1, 1)], .045),
        lpool(1, EGG_RARE, .12)))


def tome_drops(pack):
    def drops(tag, entries):
        pack.json(f"skyrpg/tome_drops/{tag}.json", {
            "tag": f"skyrpg_boss=skyrpg_world:{tag}",
            "drops": [{"enchant": f"skyrpg_enchants:{e}", "level": lv, "chance": ch, "min_tier": 0} for e, lv, ch in entries]})
    drops("fallen_knight", [("titanbane", 4, 0.16), ("titanbane", 5, 0.06), ("final_stand", 4, 0.14), ("final_stand", 5, 0.05),
                            ("cleave", 6, 0.12), ("bloodthirst", 5, 0.10), ("reapers_mark", 5, 0.08), ("vitality", 7, 0.12),
                            ("hallowed_edge", 7, 0.10), ("aegis", 7, 0.10), ("deflection", 4, 0.10), ("soulrend", 5, 0.05)])
    drops("knight_captain", [("titanbane", 3, 0.12), ("final_stand", 3, 0.10), ("cleave", 4, 0.10), ("steadfast", 3, 0.08),
                             ("aegis", 5, 0.08), ("vitality", 5, 0.08)])


def bosses(pack):
    pack.json("skyrpg/dungeon_bosses/knight_captain.json", {
        "name": "The Knight-Captain", "entity": "minecraft:zombie", "dungeon": DUN, "tier": "champion", "level": 57,
        "health": 16000, "damage": 36, "speed": 0.27, "scale": 1.8, "knockback_resistance": 0.8, "bar_color": "white",
        "equipment": {"mainhand": "minecraft:diamond_sword", "head": "minecraft:diamond_helmet", "chest": "minecraft:diamond_chestplate",
                      "legs": "minecraft:iron_leggings", "feet": "minecraft:iron_boots"},
        "health_per_player": 0.6, "coins": [10000, 13500], "delving_xp": 3600,
        "intro": "The Knight-Captain raises a dented diamond blade: 'The Citadel does not fall. It only waits.'",
        "death": "The Captain kneels and the gate of the hall grinds open. A crest of black iron clatters across the stones.",
        "max_minions": 4, "loot": "skyrpg_world:bosses/knight_captain", "reset_ticks": 72000,
        "first_clear": {"coins": 11000, "xp": {"skyrpg_skills:combat": 5400}},
        "moves": [
            {"type": "slam", "every": 200, "radius": 6, "damage": 30, "knockup": 0.9, "windup": 22,
             "warn": "The Captain brings his blade down!"},
            {"type": "leap", "every": 280, "damage": 28, "radius": 4, "range": 18,
             "warn": "The Captain vaults across the hall!"},
            {"type": "volley", "every": 260, "projectile": "arrow", "count": 6, "damage": 12, "spread": 0.2,
             "warn": "The Captain's archers loose a volley!"},
            {"type": "ward", "every": 800, "below": 0.5, "duration": 100, "reduction": 0.75,
             "warn": "The Captain raises his tower shield!"},
            {"type": "summon", "every": 480, "max": 4,
             "mobs": [{"type": "minecraft:zombie", "count": 2, "level": 55, "name": "Citadel Knight",
                       "equipment": {"mainhand": "minecraft:iron_sword", "head": "minecraft:iron_helmet", "chest": "minecraft:iron_chestplate"}}],
             "warn": "The Captain calls his knights!"}]})
    pack.json("skyrpg/dungeon_bosses/fallen_knight.json", {
        "name": "The Fallen Knight", "entity": "minecraft:wither_skeleton", "dungeon": DUN, "level": 58, "health": 48000, "damage": 54,
        "speed": 0.29, "scale": 2.6, "knockback_resistance": 1.0, "bar_color": "purple", "health_per_player": 0.6,
        "equipment": {"mainhand": "minecraft:netherite_sword", "offhand": "minecraft:black_banner", "head": "minecraft:netherite_helmet",
                      "chest": "minecraft:netherite_chestplate", "legs": "minecraft:netherite_leggings", "feet": "minecraft:netherite_boots"},
        "coins": [44000, 58000], "delving_xp": 21000,
        "intro": "A giant in black plate rises from the throne, a ruined banner on his arm: 'I held this hall when kings were dust. Kneel, or break.'",
        "death": "The Fallen Knight sinks to one knee and his plate splits along every seam. His crest rolls free, and the hoard behind the throne unseals.",
        "max_minions": 8, "loot": "skyrpg_world:bosses/fallen_knight", "reward_loot": "skyrpg_world:rewards/fallen_knight",
        "first_clear": {"coins": 90000, "xp": {"skyrpg_skills:combat": 26000}},
        "reset_ticks": 72000,
        "phases": [{"below": 0.66, "message": "The Fallen Knight rips his banner free. The dead knights of the Citadel rise around him!"},
                   {"below": 0.33, "message": "His visor burns violet. The Knight swings with the whole weight of the fallen castle!"}],
        "moves": [
            {"type": "slam", "every": 200, "radius": 8, "damage": 42, "knockup": 1.0, "windup": 24, "particle": "minecraft:smoke",
             "warn": "The Knight raises his black blade!"},
            {"type": "leap", "every": 300, "damage": 38, "radius": 5, "range": 22,
             "warn": "The Knight launches himself across the hall!"},
            {"type": "volley", "every": 240, "projectile": "fireball", "count": 5, "damage": 18, "spread": 0.2,
             "warn": "The Knight hurls shadow-fire from his sword!"},
            {"type": "summon", "every": 460, "below": 0.66, "max": 6,
             "mobs": [{"type": "minecraft:wither_skeleton", "count": 2, "level": 56, "name": "Fallen Guard",
                       "equipment": {"mainhand": "minecraft:diamond_sword", "head": "minecraft:iron_helmet", "chest": "minecraft:iron_chestplate"}},
                      {"type": "minecraft:skeleton", "count": 3, "level": 56, "name": "Skeleton Knight",
                       "equipment": {"mainhand": "minecraft:bow", "head": "minecraft:iron_helmet"}}],
             "warn": "The dead knights of the Citadel rise!"},
            {"type": "blink", "every": 360, "below": 0.66, "warn": "The Knight vanishes in a swirl of black plate!"},
            {"type": "cloud", "every": 340, "below": 0.66, "effect": "minecraft:wither", "amplifier": 1, "duration": 100, "radius": 4.0,
             "on": "target", "warn": "A wither cloud rolls off his armour!"},
            {"type": "heal", "every": 560, "below": 0.5, "percent": 0.03, "warn": "The banner drinks the fallen's strength!"},
            {"type": "ward", "every": 900, "below": 0.5, "duration": 100, "reduction": 0.7, "warn": "Black plate slams shut over the Knight!"},
            {"type": "pull", "every": 400, "below": 0.33, "radius": 16, "strength": 1.2, "particle": "minecraft:soul",
             "warn": "The Knight drags you towards his blade!"},
            {"type": "molten", "every": 250, "below": 0.33, "count": 14, "radius": 5, "delay": 25, "duration": 120, "damage": 34,
             "warn": "The floor glows white-hot. Move!"},
            {"type": "shockwave", "every": 300, "below": 0.33, "radius": 11, "damage": 30, "push": 2.0, "particle": "minecraft:soul_fire_flame",
             "warn": "A shockwave rolls out from the Knight!"},
            {"type": "slam", "every": 170, "below": 0.33, "radius": 9, "damage": 48, "knockup": 1.1, "windup": 20, "particle": "minecraft:soul_fire_flame",
             "warn": "The throne room cracks under his blade!"},
            {"type": "enrage", "below": 0.33, "speed": 0.37, "damage": 0.4, "warn": "The Fallen Knight's visor burns violet!"}]})


# ---------------------------------------------------------------------------------------------------------------------
# The structure
# ---------------------------------------------------------------------------------------------------------------------
BIOMES = ["minecraft:frozen_peaks", "minecraft:jagged_peaks", "minecraft:stony_peaks", "minecraft:snowy_slopes", "minecraft:grove",
          "minecraft:windswept_hills", "minecraft:windswept_gravelly_hills"]


def build(pack):
    P = lambda s: f"{RC}/{s}"  # noqa: E731
    build_entrance(pack)
    for fl in (1, 2, 3):
        for kind in ("spine", "side", "tail"):
            cap_piece(pack, ST, kind, fl)
            pack.pool(P(f"caps_{kind}{fl}"), [(P(f"caps/{kind}{fl}"), 1)])
    for fl in (1, 2):
        nxt = {1: P(f"f{fl}_s2"), 2: P("captain") if fl == 1 else P("descent2")}
        for stage, specs in SPINE[fl].items():
            for kind, tag, w in specs:
                HALL_FNS[kind](pack, fl, tag, w, nxt[stage], SPINE_SIDES[fl])
            pack.pool(P(f"f{fl}_s{stage}"), [(P(f"f{fl}/{kind}_{tag}"), 3) for kind, tag, _ in specs], fallback=P(f"caps_spine{fl}"))
        pack.pool(P(f"f{fl}_rooms"), [(fn(pack, fl).path, wgt) for fn, wgt in ROOMS[fl]], fallback=P(f"caps_side{fl}"))
        pack.pool(P(f"f{fl}_leaves"), [(fn(pack, fl).path, 1) for fn in LEAVES], fallback=P(f"caps_tail{fl}"))
    build_champion(pack)
    d1, *_ = descent_piece(pack, ST, THEME1, 1, "descent1", 16, P("f2_s1"))
    d2, *_ = descent_piece(pack, ST, THEME2, 2, "descent2", 16, P("f3"))
    d1.spawn(5, 12, [knight(3, L(1, 3)), skel_knight(3, L(1, 3))], [archer(3, L(1, 3)), knight(2, L(1, 3), "Ward Sergeant", True)],
             radius=9, spread=2, y=16 - 8 + 1)
    d2.spawn(5, 12, [knight(3, L(2, 2), fl=2), skel_knight(3, L(2, 3))], [guard(3, L(2, 3)), archer(3, L(2, 3), fl=2)],
             radius=9, spread=2, y=16 - 8 + 1)
    for d_ in (d1, d2):
        d_.arrows(8, 16 - 3 + 2, 11, "north", radius=8, damage=13, every=50) if False else None
        d_.finish(pack)
    build_approach(pack)
    build_throne(pack)
    pack.pool(P("entrance"), [(P("entrance"), 1)])
    pack.pool(P("stairwell"), [(P("stairwell"), 1)])
    pack.pool(P("captain"), [(P("f1/champion"), 1)], fallback=P("caps_spine1"))
    pack.pool(P("descent1"), [(P("descent1"), 1)], fallback=P("caps_spine1"))
    pack.pool(P("descent2"), [(P("descent2"), 1)], fallback=P("caps_spine2"))
    pack.pool(P("f3"), [(P("f3/approach"), 1)], fallback=P("caps_spine2"))
    pack.pool(P("throne"), [(P("boss/throne"), 1)], fallback=P("caps_spine3"))
    pack.biome_tag(RC, BIOMES)
    pack.structure(RC, start_pool=P("entrance"), biomes=f"#skyrpg_world:has_structure/{RC}", size=16, max_distance=116,
                   step="underground_structures", adaptation="beard_thin", heightmap="WORLD_SURFACE_WG", start_height=0)
    pack.structure_set(RC, 56, 22, 26111205, exclusion=("dwarven_halls", 10))
    pack.structure_tag("on_citadel_maps", [RC])
    loot(pack)
    tome_drops(pack)
    pack.json("skyrpg/dungeons/ruined_citadel.json", {
        "key": "skyrpg_world:citadel_crest_key", "name": "The Ruined Citadel", "structure": DUN, "color": "#9AA3B5",
        "icon": "minecraft:netherite_sword", "level": 56, "boss": "skyrpg_world:fallen_knight", "reset_ticks": 72000,
        "description": "A castle ruin sunk into the mountain peaks. Fight down through the Outer Ward and the Keep to the Throne Room and the Fallen Knight."})
    bosses(pack)
    pack.json("skyrpg/items/citadel_crest_key.json", {
        "name": "Citadel Crest Key", "rarity": "rare", "type": "Dungeon Key",
        "description": "Opens the vault behind the Throne Room in the Ruined Citadel. Used up when you open it."})
    pack.json("skyrpg/items/fallen_crest.json", {
        "name": "Fallen Crest", "rarity": "epic", "type": "Boss Trophy",
        "description": "The black-iron crest of the Fallen Knight, dropped in the Ruined Citadel. One of the trophies for the Ender Wyrm altar."})


def group(path):
    for a, b in (("/f1/", "floor 1"), ("/f2/", "floor 2"), ("/f3/", "floor 3"), ("/boss/", "floor 3")):
        if a in path:
            return b
    if path.endswith("/descent1"):
        return "floor 1"
    if path.endswith("/descent2"):
        return "floor 2"
    return "gate"


if __name__ == "__main__":
    run_main(build, ST, f"{RC}/caps/", f"{RC}/entrance", group)
