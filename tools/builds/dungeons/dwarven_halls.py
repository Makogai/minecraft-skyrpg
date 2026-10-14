"""The Dwarven Halls (skyrpg_world:dwarven_halls): a two-floor ruined dwarven city under mountains, hills and forests.
Mob levels 32-41, boss level 42 (the Iron Thane). Mirrors the v2 dungeons (see docs/design/dungeons.md).

Layout (jigsaw, size 16, max_distance 116). All floors lie at local y=0 of their pieces; every doorway is 3x3 at y=1.

  SURFACE  "entrance" (25 x 15 x 25, the start piece): the ruined Dwarven Gate (a carved deepslate doorway in a hillside,
           braziers, a collapsed statue, a waystone) and the top of a long square-spiral stairwell. It projects to
           WORLD_SURFACE_WG with start_height 0 (its y=0 is the top ground block). Under it, stacked on the same
           footprint and joined by a vertical jigsaw (down_north / up_north), the "stairwell" (25 x 76 x 25) holds the rest
           of the 76-block spiral (landings with packs, chests and arrow traps); its exit is on the EAST wall (z=20), at
           the bottom. Splitting it keeps beard_thin terrain adaptation working at the surface (a template's ground
           level is its local y=1, so a single 90-block piece would adapt to terrain 76 blocks down).
  FLOOR 1  "Upper Halls" (Lv 32-36), along axis A from the entrance:
           f1_s1 -> f1_s2 -> f1_s3 (spine halls, 19 deep: great hall, barracks, rail station, forge hall) ->
           Forgeguard's arena (champion, door plug) -> descent (switchback stairs, 20 down; exits on its EAST wall,
           a 90 degree turn).
  FLOOR 2  "The Deep Forge" (Lv 36-41), perpendicular to floor 1, 20 below:
           f2_s1 -> f2_s2 -> f2_s3 (forge halls, ore galleries, great halls) -> the Deep Forge (31 x 15 x 31, boss).
  Side rooms hang off every spine hall (floor 1: both sides; floor 2: only the side that faces back towards the
  entrance, because the far side would leave the 116-block box). Each room has a tail door into a leaf pool.
  Everything falls back to 1-thick caps.

Doors: entering doors are named dwarf_spine_in / dwarf_side_in / dwarf_tail_in; outgoing ones dwarf_spine_out /
dwarf_side_out / dwarf_tail_out target the matching _in. Spine doors have placement_priority 10, side doors 0.

Run:  python tools/builds/dungeons/dwarven_halls.py        (add --show <piece path> y to print a slice of a piece)
Offline checks (all run on every build): jigsaw validation, every block id exists, every door and marker is reachable
(walk test with door markers opened), pack sizes, spine reach table.
"""
import json
import math
import random
import sys
import pathlib
import zipfile
from collections import Counter

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parents[1]))
from dungeon_kit import Pack, Piece  # noqa: E402

DH = "dwarven_halls"
DUN = "skyrpg_world:dwarven_halls"
AIR = "minecraft:air"
RUNE = "skyrpg_world:dwarven_runestone"

# deepslate family
DB, DT, PD = "minecraft:deepslate_bricks", "minecraft:deepslate_tiles", "minecraft:polished_deepslate"
CDB, CDT, CHD = "minecraft:cracked_deepslate_bricks", "minecraft:cracked_deepslate_tiles", "minecraft:chiseled_deepslate"
COBD = "minecraft:cobbled_deepslate"
# blackstone family
PBB, PB, CPBB = "minecraft:polished_blackstone_bricks", "minecraft:polished_blackstone", "minecraft:cracked_polished_blackstone_bricks"
CHPB = "minecraft:chiseled_polished_blackstone"
# oxidised copper
COP, COPB, COPG = "minecraft:oxidized_cut_copper", "minecraft:oxidized_copper", "minecraft:oxidized_copper_grate"
COPC = "minecraft:oxidized_chiseled_copper"
DQ_ORE, DQ_ORE_D = "skyrpg_mining:duskquartz_ore", "skyrpg_mining:deepslate_duskquartz_ore"
CHAIN = "minecraft:iron_chain"

# (the surface gate is the start piece with start_height 0: its local y=0 is the top ground block, y=1 the air above)
DROP = 20         # descent: floor 1 -> floor 2
F_H = {1: 11, 2: 12}   # spine hall height
HALL_D = 19


class Pal:
    def __init__(self, wall, floor, inlay, trim, cap, weather, light):
        self.wall, self.floor, self.inlay, self.trim, self.cap, self.weather, self.light = wall, floor, inlay, trim, cap, weather, light


PAL = {
    1: Pal(DB, DT, PD, PD, CHD,
           {DB: [(CDB, .14), ("minecraft:mossy_cobblestone", .02), (COBD, .04)],
            DT: [(CDT, .16), (COBD, .03)], PD: [(DB, .06)]},
           "minecraft:lantern"),
    2: Pal(PBB, PB, DT, COP, CHPB,
           {PBB: [(CPBB, .13), ("minecraft:blackstone", .06), (DB, .03)],
            PB: [("minecraft:blackstone", .10), ("minecraft:smooth_basalt", .05)], DT: [(CDT, .12)],
            COP: [(COPB, .12)]},
           "minecraft:soul_lantern"),
}


# ---------------------------------------------------------------------------------------------------------------------
# Mobs. Floor 1: Lv 32-36, floor 2: Lv 36-41, champion 38, boss 42.
# ---------------------------------------------------------------------------------------------------------------------
def L(fl, d=0):
    return (32 if fl == 1 else 36) + d


def mob(type_, count, level, name, elite=False, **equipment):
    m = {"type": type_, "count": count, "level": level, "elite": elite, "name": name}
    if equipment:
        m["equipment"] = dict(equipment)
    return m


def miner(n, lvl, name="Lost Miner", elite=False, hat="minecraft:iron_helmet"):
    return mob("minecraft:zombie", n, lvl, name, elite, mainhand="minecraft:iron_pickaxe", head=hat)


def archer(n, lvl, name="Deepdelve Archer"):
    return mob("minecraft:skeleton", n, lvl, name, False, mainhand="minecraft:bow", head="minecraft:iron_helmet")


def construct(n, lvl, name="Dwarven Construct", elite=False):
    return mob("skyrpg_combat:dwarven_construct", n, lvl, name, elite)


def total(mobs):
    return sum(m["count"] for m in mobs)


def std_pack(fl):
    if fl == 1:
        return [miner(3, L(1, 0)), archer(2, L(1, 1)), construct(1, L(1, 2))]
    return [miner(2, L(2, 0)), archer(2, L(2, 2)), construct(2, L(2, 3))]


# ---------------------------------------------------------------------------------------------------------------------
# Room: a built (dry) room with shell, doors, decor and markers
# ---------------------------------------------------------------------------------------------------------------------
SIDE_POOL = {1: f"{DH}/f1_rooms", 2: f"{DH}/f2_rooms"}
TAIL_POOL = {1: f"{DH}/f1_leaves", 2: f"{DH}/f2_leaves"}
CAPS = {1: (f"{DH}/caps_spine1", f"{DH}/caps_side1", f"{DH}/caps_tail1"),
        2: (f"{DH}/caps_spine2", f"{DH}/caps_side2", f"{DH}/caps_tail2")}
DOORS = {"spine_in": ("dwarf_spine_in", "dwarf_spine_in", None, 0),
         "spine_out": ("dwarf_spine_out", "dwarf_spine_in", "pool", 10),
         "side_in": ("dwarf_side_in", "dwarf_side_in", None, 0),
         "side_out": ("dwarf_side_out", "dwarf_side_in", "side", 0),
         "tail_in": ("dwarf_tail_in", "dwarf_tail_in", None, 0),
         "leaf_out": ("dwarf_leaf_out", "dwarf_tail_in", "tail", 0),
         "tail_out": ("dwarf_tail_out", "dwarf_tail_in", "tail", 0)}


class Room:
    def __init__(self, path, w, h, d, fl=1):
        self.p = Piece(f"{DH}/{path}", w, h, d)
        self.w, self.h, self.d, self.fl = w, h, d, fl
        self.pal = PAL[fl]
        self.rng = random.Random(path)
        self.keep = set()        # (x, z) columns decor must leave alone
        self.doors = []

    # -- basics --------------------------------------------------------------------------------------------------
    def set(self, x, y, z, block):
        self.p.set(x, y, z, block)

    def fill(self, x1, y1, z1, x2, y2, z2, block):
        self.p.fill(x1, y1, z1, x2, y2, z2, block)

    def get(self, x, y, z):
        return self.p.get(x, y, z)

    def reserve(self, x1, z1, x2, z2):
        self.keep |= {(x, z) for x in range(min(x1, x2), max(x1, x2) + 1) for z in range(min(z1, z2), max(z1, z2) + 1)}

    def free(self, x, z, y=1):
        return (x, z) not in self.keep and self.p.inside(x, y, z) and self.get(x, y, z) == AIR

    def shell(self):
        p, w, h, d, pal = self.p, self.w, self.h, self.d, self.pal
        p.fill(0, 0, 0, w - 1, h - 1, d - 1, pal.wall)
        p.fill(1, 1, 1, w - 2, h - 2, d - 2, AIR)
        for x in range(w):
            for z in range(d):
                inlay = x in (1, w - 2) and 1 <= z <= d - 2 or z in (1, d - 2) and 1 <= x <= w - 2
                p.set(x, 0, z, pal.inlay if inlay else pal.floor)
        for z in range(4, d - 3, 5):             # pilasters, flush with the wall
            for x in (0, w - 1):
                for y in range(1, h - 1):
                    p.set(x, y, z, pal.trim)
                p.set(x, 1, z, pal.cap)
                p.set(x, h - 2, z, pal.cap)
        for x in range(w):                         # a trim band round the top of the walls
            for z in (0, d - 1):
                p.set(x, h - 3, z, pal.trim)
        for z in range(d):
            for x in (0, w - 1):
                p.set(x, h - 3, z, pal.trim)

    def ribs(self, every=6, start=3):
        """Ceiling ribs across the room (one block lower than the ceiling)."""
        for z in range(start, self.d - 2, every):
            for x in range(1, self.w - 1):
                if self.get(x, self.h - 2, z) == AIR:
                    self.set(x, self.h - 2, z, self.pal.trim)

    def door(self, side, kind, pool=None, at=None, y=1):
        p, w, d = self.p, self.w, self.d
        name, target, ptype, prio = DOORS[kind]
        if ptype == "pool":
            pl = pool
        elif ptype == "side":
            pl = pool or SIDE_POOL[self.fl]
        elif ptype == "tail":
            pl = pool or TAIL_POOL[self.fl]
        else:
            pl = "minecraft:empty"
        ns_side = side in ("north", "south")
        c = (w // 2 if ns_side else d // 2) if at is None else at
        plane = {"north": 0, "south": d - 1, "west": 0, "east": w - 1}[side]
        inward = 1 if side in ("north", "west") else -1
        for i in range(-1, 2):
            for yy in (y, y + 1, y + 2):
                x, z = (c + i, plane) if ns_side else (plane, c + i)
                p.set(x, yy, z, AIR)
        for off in (-2, 2):
            for yy in (y, y + 1, y + 2):
                x, z = (c + off, plane) if ns_side else (plane, c + off)
                p.set(x, yy, z, self.pal.trim)
        for off in range(-2, 3):
            x, z = (c + off, plane) if ns_side else (plane, c + off)
            p.set(x, y + 3, z, self.pal.cap)
        if y == 1:
            for depth in range(1, 4):
                for i in range(-1, 2):
                    x, z = (c + i, plane + inward * depth) if ns_side else (plane + inward * depth, c + i)
                    self.keep.add((x, z))
        p.door(side, name, target, pl, at=at, y=y, final_state=AIR, placement_priority=prio)
        self.doors.append((side, kind))

    def lane(self, side="x"):
        """Keeps the middle strip clear: x = along the z axis (spine), 'z' = along the x axis."""
        if side == "x":
            self.reserve(self.w // 2 - 1, 0, self.w // 2 + 1, self.d - 1)
        else:
            self.reserve(0, self.d // 2 - 1, self.w - 1, self.d // 2 + 1)

    def weather(self):
        ch = {k: list(v) for k, v in self.pal.weather.items()}
        self.p.weather("a", ch)

    def finish(self, pack, exempt=None):
        self.weather()
        autoconnect(self.p)
        pack.add(self.p)
        if exempt:
            EXEMPT[self.p.path] = exempt
        return self.p

    # -- decor ---------------------------------------------------------------------------------------------------
    def col(self, x, z, rune=True, y1=None):
        top = (self.h - 2) if y1 is None else y1
        for y in range(1, top + 1):
            self.set(x, y, z, self.pal.trim)
        self.set(x, 1, z, self.pal.cap)
        self.set(x, top, z, self.pal.cap)
        if rune and top >= 5:
            self.set(x, 4, z, RUNE)
        self.keep.add((x, z))

    def hang(self, x, z, n=2):
        """A lantern on a chain from the ceiling (or from a ceiling rib)."""
        top = self.h - 2
        if self.get(x, top, z) != AIR:
            if self.get(x, top, z) == self.pal.trim and self.get(x, top - 1, z) == AIR:
                top -= 1
            else:
                return
        if self.get(x, top - n, z) != AIR:
            return
        for y in range(top, top - n, -1):
            self.set(x, y, z, CHAIN + "[axis=y]")
        self.set(x, top - n, z, self.pal.light + "[hanging=true]")

    def brazier(self, x, z):
        if not self.free(x, z):
            return
        wall = "minecraft:polished_blackstone_brick_wall"
        self.set(x, 1, z, wall)
        self.set(x, 2, z, "minecraft:soul_campfire[facing=north,lit=true]")
        self.keep.add((x, z))

    def chest(self, x, z, facing, tier="common", y=1, block="minecraft:chest"):
        loot = f"skyrpg_world:chests/dwarven_{tier}"
        self.p.chest(x, y, z, facing, loot, block=block, tier=tier)
        self.keep.add((x, z))

    def spawn(self, x, z, *waves, radius=9, spread=4, group=None, y=1):
        for wv in waves:
            assert 4 <= total(wv) <= 10, f"{self.p.path}: wave of {total(wv)}"
        data = dict(radius=radius, spread=spread)
        if group:
            data["group"] = group
        if len(waves) == 1:
            data["mobs"] = waves[0]
        else:
            data["waves"] = list(waves)
        self.p.marker(x, y, z, "spawner", **data)

    def arrows(self, x, y, z, facing, radius=10, damage=None, every=45):
        self.set(x, y, z, f"minecraft:dispenser[facing={facing}]")
        self.p.marker(x, y, z, "trap", type="arrows", radius=radius, every=every, damage=damage or (6 if self.fl == 1 else 8))

    def spikes(self, cx, cz, r=1, y=1, damage=None):
        for x in range(cx - r, cx + r + 1):
            for z in range(cz - r, cz + r + 1):
                if (x + z) % 2 == (cx + cz) % 2 and self.free(x, z, y):
                    self.set(x, y, z, "minecraft:pointed_dripstone[thickness=tip,vertical_direction=up]")
                    self.keep.add((x, z))
        self.p.marker(cx, y, cz, "trap", type="spikes", radius=r + 2, every=35, damage=damage or (5 if self.fl == 1 else 7))

    def flames(self, cx, cz, r=1, damage=None):
        """Floor vents: magma blocks in the floor and a flame burst when somebody walks near."""
        for x in range(cx - r, cx + r + 1):
            for z in range(cz - r, cz + r + 1):
                if self.get(x, 0, z) != "minecraft:lava" and (x + z) % 2 == (cx + cz) % 2:
                    self.set(x, 0, z, "minecraft:magma_block")
        self.p.marker(cx, 1, cz, "trap", type="flames", radius=r + 2, every=70, damage=damage or (4 if self.fl == 1 else 6))

    def lava_bay(self, x1, z1, x2, z2, window):
        """A sealed lava channel (interior x1..x2, z1..z2) with a glass window on the `window` side."""
        p = self.p
        lo_x, hi_x, lo_z, hi_z = x1 - 1, x2 + 1, z1 - 1, z2 + 1
        for x in range(lo_x, hi_x + 1):
            for z in range(lo_z, hi_z + 1):
                edge = x in (lo_x, hi_x) or z in (lo_z, hi_z)
                if edge:
                    glass = (window == "east" and x == hi_x or window == "west" and x == lo_x or
                             window == "north" and z == lo_z or window == "south" and z == hi_z) and \
                        (lo_x < x < hi_x or lo_z < z < hi_z)
                    for y in (1, 2):
                        if self.get(x, y, z) in (AIR, None, "minecraft:lava") or self.get(x, y, z) in (self.pal.wall, self.pal.trim):
                            p.set(x, y, z, "minecraft:glass" if glass else COP)
                    p.set(x, 3, z, COPG)
                else:
                    p.set(x, 0, z, "minecraft:magma_block")
                    p.set(x, 1, z, "minecraft:lava")
                    p.set(x, 2, z, "minecraft:lava")
                    p.set(x, 3, z, COPG)
                self.keep.add((x, z))

    def anvil(self, x, z, facing="east"):
        if self.free(x, z):
            kind = self.rng.choice(["anvil", "anvil", "chipped_anvil", "damaged_anvil"])
            self.set(x, 1, z, f"minecraft:{kind}[facing={facing}]")
            self.keep.add((x, z))

    def furnace(self, x, z, facing, kind="blast_furnace"):
        if self.free(x, z):
            self.set(x, 1, z, f"minecraft:{kind}[facing={facing},lit=true]")
            self.keep.add((x, z))

    def rubble(self, n, x1, z1, x2, z2):
        blocks = [COBD, CDB, "minecraft:cobbled_deepslate_slab[type=bottom]", "minecraft:gravel", "minecraft:cobbled_deepslate_stairs[facing=north]"]
        for _ in range(n):
            x, z = self.rng.randint(x1, x2), self.rng.randint(z1, z2)
            if self.free(x, z):
                self.set(x, 1, z, self.rng.choice(blocks))

    def runes(self, cells):
        for x, z in cells:
            if self.get(x, 0, z) not in (None, RUNE):
                self.set(x, 0, z, RUNE)

    def sign(self, x, y, z, lines, facing):
        self.p.sign(x, y, z, lines, block=f"minecraft:dark_oak_wall_sign[facing={facing}]", color="white", glow=True)

    def ore_wall(self, x1, y1, z1, x2, y2, z2, weights=None):
        """Fills a wall slab with ore: mostly Duskquartz, some Emberite/Skyiron, the rest brick."""
        table = weights or [(DQ_ORE_D, 6), ("skyrpg_mining:deepslate_skyiron_ore", 1), ("skyrpg_mining:deepslate_emberite_ore", 1), (self.pal.wall, 2)]
        tot = sum(wt for _, wt in table)
        for x in range(min(x1, x2), max(x1, x2) + 1):
            for y in range(min(y1, y2), max(y1, y2) + 1):
                for z in range(min(z1, z2), max(z1, z2) + 1):
                    r = self.rng.uniform(0, tot)
                    for b, wt in table:
                        r -= wt
                        if r <= 0:
                            self.set(x, y, z, b)
                            break


EXEMPT = {}      # piece path -> predicate on marker positions that are allowed to be unreachable (hidden caches)


# ---------------------------------------------------------------------------------------------------------------------
# Connecting blocks (bars, panes, walls, fences) get their connection states from their neighbours
# ---------------------------------------------------------------------------------------------------------------------
THIN = ("iron_bars", "glass_pane", "copper_bars")
NONSOLID = {AIR, "minecraft:lantern", "minecraft:soul_lantern", "minecraft:torch", "minecraft:wall_torch", "minecraft:soul_torch",
            "minecraft:soul_campfire", "minecraft:campfire", "minecraft:cobweb", "minecraft:rail", "minecraft:powered_rail",
            "minecraft:stone_pressure_plate", "minecraft:lever", "minecraft:iron_chain", "minecraft:pointed_dripstone",
            "minecraft:ladder", "minecraft:vine", "minecraft:dispenser_dummy"}


def _is_thin(i):
    return i.split(":")[-1] in THIN or i.endswith("_bars")


def _is_wall(i):
    return i.endswith("_wall")


def _solid(i):
    return i is not None and i not in NONSOLID and not i.endswith(("_carpet", "_sign", "_button", "_plate", "_bed")) \
        and not i.endswith(("_slab", "_stairs", "lantern"))


def autoconnect(p):
    DIRS = {"north": (0, -1), "south": (0, 1), "west": (-1, 0), "east": (1, 0)}
    updates = {}
    for (x, y, z), (entry, data) in p.cells.items():
        i = entry["id"]
        if not (_is_thin(i) or _is_wall(i) or i.endswith("_fence")):
            continue
        props = dict(entry.get("properties", {}))
        up = False
        for name, (dx, dz) in DIRS.items():
            n = p.cells.get((x + dx, y, z + dz))
            ni = n[0]["id"] if n else None
            if _is_thin(i):
                props[name] = "true" if (ni and (_is_thin(ni) or _solid(ni) and "glass" not in ni.split(":")[-1] or ni.endswith("glass"))) else "false"
            elif _is_wall(i):
                if ni and (_is_wall(ni) or _solid(ni)):
                    props[name] = "low"
                    up = True
                else:
                    props[name] = "none"
            else:
                props[name] = "true" if ni and (ni == i or _solid(ni)) else "false"
        if _is_wall(i):
            props["up"] = "true"
        updates[(x, y, z)] = {"id": i, "properties": dict(sorted(props.items()))}
    for pos, entry in updates.items():
        p.cells[pos] = (entry, p.cells[pos][1])


# ---------------------------------------------------------------------------------------------------------------------
# Spine halls (19 deep; door in on the south wall, out on the north wall, side doors in the middle of the long walls)
# ---------------------------------------------------------------------------------------------------------------------
def spine(r, out_pool, sides):
    r.door("south", "spine_in")
    r.door("north", "spine_out", pool=out_pool)
    for s in sides:
        # floor 2 runs perpendicular to floor 1: its "west" side faces away from the entrance, where only small leaves fit
        r.door(s, "leaf_out" if (r.fl == 2 and s == "west") else "side_out")
    r.lane("x")
    if sides:
        zc = r.d // 2
        r.reserve(0, zc - 1, r.w - 1, zc + 1)


def hall_great(pack, fl, tag, w, out_pool, sides):
    """The great hall: two or four rows of runed pillars, a carpet runner, braziers."""
    r = Room(f"f{fl}/great_{tag}", w, F_H[fl], HALL_D, fl)
    r.shell()
    r.ribs(6, 3)
    spine(r, out_pool, sides)
    cx = w // 2
    rows = [cx - 5, cx + 5] + ([cx - 9, cx + 9] if w >= 25 else [])
    for x in rows:
        for z in (3, 6, 12, 15):
            r.col(x, z, rune=z in (6, 12))
    carpet = "minecraft:gray_carpet" if fl == 2 else "minecraft:red_carpet"
    for z in range(1, r.d - 1):
        for x in (cx - 1, cx, cx + 1):
            if r.get(x, 1, z) == AIR:
                r.set(x, 1, z, carpet)
    r.runes([(cx + s * 2, z) for z in range(2, r.d - 2, 4) for s in (-1, 1)])
    for x in (cx - 3, cx + 3):
        for z in (2, r.d - 3):
            r.brazier(x, z)
    for z in (3, 9, 15):
        r.hang(cx - 3, z)
        r.hang(cx + 3, z)
    r.chest(2, 2, "south", "common" if fl == 1 else "rare")
    r.chest(w - 3, r.d - 3, "north", "common")
    if fl == 2:
        r.chest(w - 3, 2, "south", "rare")
    r.rubble(6, 2, 2, w - 3, r.d - 3)
    r.arrows(0, 3, 5, "east")
    r.arrows(w - 1, 3, 13, "west")
    r.spikes(cx - 7, 11, 1)
    r.spikes(cx + 7, 7, 1)
    waves = ([[miner(4, L(fl, 0)), archer(2, L(fl, 1))], [construct(2, L(fl, 2)), miner(3, L(fl, 1))]] if fl == 1 else
             [[construct(2, L(fl, 1)), archer(3, L(fl, 1))], [construct(3, L(fl, 3)), miner(3, L(fl, 2))]])
    r.spawn(cx, 9, *waves, radius=10, spread=5)
    return r.finish(pack)


def hall_barracks(pack, fl, tag, w, out_pool, sides):
    """Bunks along both walls, two stone tables with benches, barrels and a smoker."""
    r = Room(f"f{fl}/barracks_{tag}", w, F_H[fl], HALL_D, fl)
    r.shell()
    r.ribs(6, 4)
    spine(r, out_pool, sides)
    cx = w // 2
    color = "red" if fl == 1 else "gray"
    for z in (3, 5, 13, 15):
        r.set(1, 1, z, f"minecraft:{color}_bed[facing=west,part=head]")
        r.set(2, 1, z, f"minecraft:{color}_bed[facing=west,part=foot]")
        r.set(w - 2, 1, z, f"minecraft:{color}_bed[facing=east,part=head]")
        r.set(w - 3, 1, z, f"minecraft:{color}_bed[facing=east,part=foot]")
        r.reserve(1, z, 2, z)
        r.reserve(w - 3, z, w - 2, z)
    for z in (4, 14):
        for x in (3, w - 4):
            r.set(x, 1, z, "minecraft:barrel[facing=up]")
            r.keep.add((x, z))
    for x0 in (cx - 5, cx + 3):
        for x in range(x0, x0 + 3):
            r.set(x, 1, 8, "minecraft:deepslate_brick_stairs[facing=south]")
            r.set(x, 1, 9, "minecraft:polished_deepslate_slab[type=top]")
            r.set(x, 1, 10, "minecraft:deepslate_brick_stairs[facing=north]")
            for z in (8, 9, 10):
                r.keep.add((x, z))
    r.furnace(w - 3, 7, "west", "smoker")
    r.set(3, 1, 7, "minecraft:grindstone[face=floor,facing=east]")
    r.keep.add((3, 7))
    r.chest(2, r.d - 3, "north", "common")
    r.chest(w - 3, 2, "south", "common" if fl == 1 else "rare")
    for z in (3, 9, 15):
        r.hang(cx - 4, z)
        r.hang(cx + 4, z)
    r.rubble(4, 4, 2, w - 5, r.d - 3)
    r.arrows(w - 1, 3, 4, "west")
    r.arrows(0, 3, 14, "east")
    waves = ([[miner(5, L(fl, 0)), archer(2, L(fl, 1))], [miner(3, L(fl, 2), "Lost Foreman", elite=True), construct(2, L(fl, 2))]] if fl == 1 else
             [[construct(3, L(fl, 2)), miner(3, L(fl, 1))], [construct(2, L(fl, 3), "Runeguard", elite=True), archer(3, L(fl, 2))]])
    r.spawn(cx, 9, *waves, radius=10, spread=5)
    return r.finish(pack)


def hall_rail(pack, fl, tag, w, out_pool, sides):
    """A rail station: the line runs down the middle, platforms either side, minecarts and a chest minecart."""
    r = Room(f"f{fl}/rail_{tag}", w, F_H[fl], HALL_D, fl)
    r.shell()
    r.ribs(6, 3)
    spine(r, out_pool, sides)
    cx = w // 2
    p = r.p
    for z in range(1, r.d - 1):
        powered = z % 5 == 3
        p.set(cx, 1, z, "minecraft:powered_rail[powered=true,shape=north_south]" if powered else "minecraft:rail[shape=north_south]")
        if powered:
            p.set(cx, 0, z, "minecraft:redstone_block")
    p.entity(cx, 1, 4, {"id": "minecraft:minecart"})
    p.entity(cx, 1, 13, {"id": "minecraft:chest_minecart", "LootTable": "skyrpg_world:chests/dwarven_cart"})
    for x in (cx - 4, cx + 4):
        for z in range(2, r.d - 2):
            if abs(z - 9) > 1:
                r.set(x, 1, z, "minecraft:polished_deepslate_slab[type=bottom]")
                r.keep.add((x, z))
    for x in (cx - 6, cx + 6):
        for z in (3, 15):
            r.col(x, z, rune=True)
    r.sign(cx - 6, 2, 4, ["DEEPDELVE LINE", "Halls Station", "mind the cart"], "south")
    r.sign(cx + 6, 2, 14, ["DEEPDELVE LINE", "to the Forge", "(collapsed)"], "north")
    for x, z in ((2, 2), (w - 3, 2), (2, r.d - 3), (w - 3, r.d - 3)):
        if r.free(x, z):
            r.set(x, 1, z, "minecraft:raw_iron_block")
            if r.free(x, z, 2):
                r.set(x, 2, z, "minecraft:raw_copper_block")
            r.keep.add((x, z))
    for z in (3, 9, 15):
        r.hang(cx - 3, z)
        r.hang(cx + 3, z)
    r.chest(3, 3, "south", "common")
    r.chest(w - 4, r.d - 3, "north", "common" if fl == 1 else "rare")
    r.rubble(5, 2, 2, w - 3, r.d - 3)
    r.arrows(0, 3, 6, "east")
    r.arrows(w - 1, 3, 12, "west")
    r.spikes(cx - 8, 12, 1)
    waves = ([[miner(5, L(fl, 1)), archer(3, L(fl, 1))], [construct(2, L(fl, 2)), archer(2, L(fl, 2)), miner(2, L(fl, 2))]] if fl == 1 else
             [[miner(4, L(fl, 1)), construct(2, L(fl, 2))], [construct(3, L(fl, 3)), archer(3, L(fl, 3))]])
    r.spawn(cx, 9, *waves, radius=10, spread=5)
    return r.finish(pack)


def hall_forge(pack, fl, tag, w, out_pool, sides):
    """A forge hall: lava channels behind glass along both walls, anvils and blast furnaces, flame vents."""
    r = Room(f"f{fl}/forge_{tag}", w, F_H[fl], HALL_D, fl)
    r.shell()
    r.ribs(6, 3)
    spine(r, out_pool, sides)
    cx = w // 2
    r.lava_bay(1, 2, 3, 6, "east")
    r.lava_bay(1, 12, 3, 16, "east")
    r.lava_bay(w - 4, 2, w - 2, 6, "west")
    r.lava_bay(w - 4, 12, w - 2, 16, "west")
    for x, side in ((cx - 4, "west"), (cx + 4, "east")):
        for z in (4, 14):
            r.anvil(x, z, "north")
            r.furnace(x + (-1 if side == "west" else 1), z, "east" if side == "west" else "west")
    r.flames(cx, 5, 1)
    r.flames(cx, 13, 1)
    for z in (3, 9, 15):
        r.hang(cx - 2, z)
        r.hang(cx + 2, z)
    for x in (cx - 6, cx + 6):
        r.col(x, 9, rune=True)
    r.chest(cx - 5, 1, "south", "common" if fl == 1 else "rare")
    r.chest(cx + 5, r.d - 2, "north", "common")
    r.rubble(3, 5, 2, w - 6, r.d - 3)
    waves = ([[construct(2, L(fl, 1)), miner(4, L(fl, 1))], [construct(3, L(fl, 3)), archer(3, L(fl, 2))]] if fl == 1 else
             [[construct(3, L(fl, 2)), miner(3, L(fl, 1))], [construct(2, L(fl, 4), "Runeguard", elite=True), construct(2, L(fl, 3)), archer(2, L(fl, 3))]])
    r.spawn(cx, 9, *waves, radius=10, spread=4)
    return r.finish(pack)


def hall_gallery(pack, fl, tag, w, out_pool, sides):
    """A Duskquartz gallery: the long walls are ore (mine it), amethyst pylons down the middle."""
    r = Room(f"f{fl}/gallery_{tag}", w, F_H[fl], HALL_D, fl)
    r.shell()
    r.ribs(6, 3)
    spine(r, out_pool, sides)
    cx = w // 2
    for x in (1, w - 2):
        for z in range(2, r.d - 2):
            if abs(z - r.d // 2) <= 1 and sides:
                continue
            r.ore_wall(x, 1, z, x, 6, z)
    for x in (cx - 4, cx + 4):
        for z in (4, 14):
            for y in range(1, 5):
                r.set(x, y, z, "minecraft:amethyst_block" if y in (2, 3) else r.pal.trim)
            r.set(x, 5, z, "minecraft:amethyst_cluster[facing=up]")
            r.keep.add((x, z))
    for z in (3, 9, 15):
        r.hang(cx - 2, z)
        r.hang(cx + 2, z)
    r.runes([(cx - 2, z) for z in range(2, r.d - 2, 3)])
    r.chest(3, 2, "south", "rare")
    r.chest(w - 4, r.d - 3, "north", "common")
    r.rubble(5, 3, 2, w - 4, r.d - 3)
    r.arrows(w - 1, 3, 6, "west")
    r.spikes(cx + 7, 13, 1)
    r.spikes(cx - 7, 5, 1)
    waves = ([[miner(5, L(fl, 1)), construct(2, L(fl, 2))], [archer(3, L(fl, 2)), construct(2, L(fl, 3))]] if fl == 1 else
             [[miner(4, L(fl, 2)), construct(3, L(fl, 3))], [archer(3, L(fl, 3)), construct(3, L(fl, 3))]])
    r.spawn(cx, 9, *waves, radius=10, spread=5)
    return r.finish(pack)


HALL_FNS = {"great": hall_great, "barracks": hall_barracks, "rail": hall_rail, "forge": hall_forge, "gallery": hall_gallery}
# floor -> stage -> [(kind, tag, width)]
SPINE = {
    1: {1: [("great", "a", 27), ("barracks", "a", 23), ("rail", "a", 25)],
        2: [("forge", "a", 25), ("great", "b", 25), ("barracks", "b", 23)],
        3: [("rail", "b", 25), ("forge", "b", 25), ("great", "c", 27)]},
    2: {1: [("forge", "a", 25), ("gallery", "a", 23), ("great", "a", 25)],
        2: [("great", "b", 27), ("gallery", "b", 23), ("rail", "a", 25)],
        3: [("forge", "b", 25), ("barracks", "a", 23), ("gallery", "c", 25)]},
}
SPINE_SIDES = {1: ("west", "east"), 2: ("east", "west")}


# ---------------------------------------------------------------------------------------------------------------------
# Rock-lined stairwells (the surface gate and the descent): solid rock, carved stairs, lined in dwarven brick
# ---------------------------------------------------------------------------------------------------------------------
ROCK_TOP = [("minecraft:stone", 40), ("minecraft:andesite", 10), ("minecraft:tuff", 10), ("minecraft:dirt", 12),
            ("minecraft:coarse_dirt", 6), ("minecraft:cobblestone", 5), ("minecraft:gravel", 3), ("minecraft:granite", 6),
            ("minecraft:coal_ore", 3), ("minecraft:iron_ore", 3), ("minecraft:copper_ore", 2)]
ROCK_MID = [("minecraft:stone", 32), ("minecraft:tuff", 16), ("minecraft:deepslate", 22), ("minecraft:andesite", 8),
            (COBD, 8), ("minecraft:calcite", 2), ("minecraft:iron_ore", 3), ("minecraft:coal_ore", 3),
            ("minecraft:copper_ore", 2), ("minecraft:deepslate_iron_ore", 2), ("skyrpg_mining:skyiron_ore", 2)]
ROCK_DEEP = [("minecraft:deepslate", 56), (COBD, 18), ("minecraft:tuff", 8), ("minecraft:smooth_basalt", 4),
             ("minecraft:blackstone", 3), ("minecraft:calcite", 1), (DQ_ORE_D, 3), ("minecraft:deepslate_iron_ore", 3),
             ("minecraft:deepslate_copper_ore", 2), ("minecraft:deepslate_coal_ore", 2), ("skyrpg_mining:deepslate_skyiron_ore", 1)]
ROCK_IDS = {b for t in (ROCK_TOP, ROCK_MID, ROCK_DEEP) for b, _ in t} | {"minecraft:grass_block", "minecraft:podzol", "minecraft:moss_block"}
LINING = [(DB, 52), (CDB, 16), (DT, 12), (COBD, 10), (PD, 6), (CDT, 4)]
OPP = {"north": "south", "south": "north", "west": "east", "east": "west"}
DIRV = {(1, 0): "east", (-1, 0): "west", (0, 1): "south", (0, -1): "north"}


def wchoice(rng, table):
    total_w = sum(wt for _, wt in table)
    r = rng.uniform(0, total_w)
    for b, wt in table:
        r -= wt
        if r <= 0:
            return b
    return table[-1][0]


class Shaft:
    """A piece that starts as solid rock; carve stairs and landings, then line everything in brick."""

    def __init__(self, room, top_rock_depth=22, p=None):
        self.r = room
        self.p = p or room.p
        self.rng = room.rng
        self.air = set()
        self.w, self.h, self.d = room.w, room.h, room.d
        self.top = top_rock_depth

    def fill_rock(self, y_top):
        """Solid rock for every y <= y_top (relative to the top the rock gets lighter)."""
        for x in range(self.w):
            for z in range(self.d):
                for y in range(0, y_top + 1):
                    depth = y_top - y
                    table = ROCK_DEEP if self.top <= 0 else ROCK_TOP if depth < 8 else ROCK_MID if depth < self.top else ROCK_DEEP
                    self.r.p.set(x, y, z, wchoice(self.rng, table))

    def carve(self, x, y, z):
        self.p.set(x, y, z, AIR)
        self.air.add((x, y, z))

    def landing(self, x1, z1, x2, z2, y, rune=None):
        for x in range(x1, x2 + 1):
            for z in range(z1, z2 + 1):
                self.p.set(x, y, z, DT if (x + z) % 3 else PD)
                for yy in range(y + 1, y + 5):
                    self.carve(x, yy, z)
        if rune:
            self.p.set(rune[0], y, rune[1], RUNE)

    def run(self, x, z, dx, dz, n, y_start):
        """n stair cells from (x, z) in direction (dx, dz); the first at floor y_start, then one lower each. 3 wide."""
        uphill = OPP[DIRV[(dx, dz)]]
        cells = []
        for i in range(n):
            cx, cz, yf = x + dx * i, z + dz * i, y_start - i
            lateral = [(cx, cz + o) for o in (-1, 0, 1)] if dx else [(cx + o, cz) for o in (-1, 0, 1)]
            for lx, lz in lateral:
                block = wchoice(self.rng, [("minecraft:deepslate_tile_stairs", 80), ("minecraft:deepslate_brick_stairs", 12),
                                           ("minecraft:cobbled_deepslate_stairs", 8)])
                self.p.set(lx, yf, lz, f"{block}[facing={uphill},half=bottom]")
                for yy in range(yf + 1, yf + 5):
                    self.carve(lx, yy, lz)
            cells.append((cx, cz, yf))
        return cells

    def helix(self, waypoints, y0, hang_every=5):
        """Square-spiral legs between waypoints (landings are 3x3). Returns (landings, legs)."""
        y = y0
        ax, az = waypoints[0]
        landings = [(ax, az, y)]
        legs = []
        self.landing(ax - 1, az - 1, ax + 1, az + 1, y)
        for bx, bz in waypoints[1:]:
            dx = (bx > ax) - (bx < ax)
            dz = (bz > az) - (bz < az)
            dist = abs(bx - ax) + abs(bz - az)
            n = dist - 3
            cells = self.run(ax + dx * 2, az + dz * 2, dx, dz, n, y - 1)
            legs.append(((dx, dz), cells))
            for i, (cx, cz, yf) in enumerate(cells):
                if i % hang_every == 2:
                    self.p.set(cx, yf + 4, cz, "minecraft:lantern[hanging=true]")
            y -= n + 1
            self.landing(bx - 1, bz - 1, bx + 1, bz + 1, y)
            landings.append((bx, bz, y))
            ax, az = bx, bz
        return landings, legs

    def line(self):
        """Replaces rock touching the carved air with dwarven brick."""
        todo = set()
        for (x, y, z) in self.air:
            for dx, dy, dz in ((1, 0, 0), (-1, 0, 0), (0, 1, 0), (0, -1, 0), (0, 0, 1), (0, 0, -1)):
                n = (x + dx, y + dy, z + dz)
                if n not in self.air and self.p.inside(*n) and self.p.get(*n) in ROCK_IDS:
                    todo.add(n)
        for pos in sorted(todo):
            self.p.set(*pos, wchoice(self.rng, LINING))
        return todo

    def wall_slot(self, x, y, z):
        return self.p.inside(x, y, z) and (x, y, z) not in self.air


# ---------------------------------------------------------------------------------------------------------------------
# The surface: the ruined Dwarven Gate (piece "entrance", the start piece) and the 76-block spiral stairwell under it
# (piece "stairwell"). The two are stacked on the same 25 x 25 footprint and joined by a vertical jigsaw. A start piece's
# local y=1 sits at (first free height + start_height), so with start_height 0 the gate's floor layer (y=0) is the top
# ground block and y=1 the air above it; terrain adaptation (beard_thin) then works at the surface, where it matters.
# The stairs are laid out in "global" coordinates (y=0 = the gate's floor, negative below) by Split, which routes every
# block to the right piece: the first stairs straddle the boundary.
# ---------------------------------------------------------------------------------------------------------------------
STAIR_H = 76                      # the stairwell piece is 76 tall: its y=0 is the bottom landing, local y' = global y + 76
GATE_H = 15


class Split:
    """Routes blocks and markers in global coordinates (y >= 0 gate piece, y < 0 stairwell piece)."""

    def __init__(self, upper, lower, lower_h):
        self.u, self.l, self.lh = upper, lower, lower_h

    def _at(self, y):
        return (self.u, y) if y >= 0 else (self.l, y + self.lh)

    def set(self, x, y, z, block, nbt_data=None):
        p, yy = self._at(y)
        p.set(x, yy, z, block, nbt_data)

    def get(self, x, y, z):
        p, yy = self._at(y)
        return p.get(x, yy, z) if p.inside(x, yy, z) else None

    def inside(self, x, y, z):
        p, yy = self._at(y)
        return p.inside(x, yy, z)

    def marker(self, x, y, z, kind, **data):
        p, yy = self._at(y)
        p.marker(x, yy, z, kind, **data)


def hill_height(x, z):
    if z > 12 or math.hypot(x - 12, z - 12) > 11.4:
        return 0
    return max(0, round(9 - 1.3 * max(0, abs(x - 12) - 4) - 1.0 * max(0, 3 - z)))


def build_entrance(pack):
    W = D = 25
    gate = Room("entrance", W, GATE_H, D, 1)
    stair = Room("stairwell", W, STAIR_H, D, 1)
    g, st = gate.p, stair.p
    rng = gate.rng
    split = Split(g, st, STAIR_H)
    sh = Shaft(stair, p=split)
    sh.fill_rock(STAIR_H - 1)                     # solid rock under the whole footprint (global y -76 .. -1)
    # the gate's floor layer (y=0) inside a circle: paving in front of the door, ground on the hill; the rest stays natural
    for x in range(W):
        for z in range(D):
            rr = math.hypot(x - 12, z - 12)
            if rr > 11.4:
                continue
            if z >= 13 and rr <= 9.6:
                g.set(x, 0, z, rng.choice([DT, DT, DT, CDT, PD, COBD]))
            else:
                g.set(x, 0, z, wchoice(rng, [("minecraft:grass_block", 55), ("minecraft:coarse_dirt", 15), ("minecraft:podzol", 10),
                                             ("minecraft:stone", 12), ("minecraft:moss_block", 8)]))
            hh = hill_height(x, z)
            for i in range(hh):
                y = 1 + i
                if i == hh - 1:
                    b = wchoice(rng, [("minecraft:grass_block", 70), ("minecraft:moss_block", 15), ("minecraft:stone", 15)])
                elif i == hh - 2:
                    b = wchoice(rng, [("minecraft:dirt", 60), ("minecraft:stone", 40)])
                else:
                    b = wchoice(rng, [("minecraft:stone", 50), ("minecraft:andesite", 18), ("minecraft:tuff", 12), (COBD, 12), ("minecraft:cobblestone", 8)])
                g.set(x, y, z, b)
    # clear the air over the plaza (up to y=10), so it stays open on a slope
    for x in range(W):
        for z in range(13, D):
            if math.hypot(x - 12, z - 12) <= 9.6:
                for y in range(1, 11):
                    if g.get(x, y, z) is None:
                        g.set(x, y, z, AIR)
    # the stairwell: entry tunnel (x 11..13, z 6..12) at y=0, then the helix
    for z in range(6, 13):
        for x in (11, 12, 13):
            g.set(x, 0, z, DT if (x + z) % 4 else PD)
            for y in range(1, 5):
                sh.carve(x, y, z)
    waypoints = [(12, 4), (20, 4), (20, 20), (4, 20), (4, 4), (20, 4), (20, 20)]
    landings, legs = sh.helix(waypoints, 0)
    assert landings[-1][2] == -(STAIR_H), f"stairwell bottom is at {landings[-1][2]}, expected {-STAIR_H}"
    for x in (22, 23, 24):                          # exit corridor east of the bottom landing (local y' = 0 is the floor)
        for z in (19, 20, 21):
            split.set(x, -STAIR_H, z, DT)
            for y in range(1, 5):
                sh.carve(x, -STAIR_H + y, z)
    sh.line()
    # the Dwarven Gate (facade at z=12, x 7..17)
    K0 = 1                                         # the first air layer
    for x in range(7, 18):
        for y in range(1, 9):
            g.set(x, y, 12, wchoice(rng, [(DB, 60), (CDB, 22), (COBD, 8), ("minecraft:mossy_cobblestone", 4), (DT, 6)]))
    for y in range(1, 5):
        for x in (11, 12, 13):
            g.set(x, y, 12, AIR)
    for x in (10, 14):
        for y in range(1, 5):
            g.set(x, y, 12, PD)
        g.set(x, 1, 12, CHD)
        g.set(x, 4, 12, CHD)
    for x in range(10, 15):
        g.set(x, 5, 12, CHD)
        g.set(x, 6, 12, PD)
    g.set(12, 5, 12, RUNE)
    g.set(11, 6, 12, RUNE)
    g.set(13, 6, 12, RUNE)
    for x in (9, 15):
        for y in range(1, 7):
            g.set(x, y, 12, PD if y % 2 == 0 else DB)
    # ruin: a collapsed corner of the arch and the odd missing block
    g.set(14, 4, 12, AIR)
    g.set(14, 5, 12, AIR)
    g.set(13, 6, 12, CDB)
    for x, y in ((8, 7), (16, 7), (9, 8), (15, 8), (7, 6), (17, 5)):
        if g.get(x, y, 12) is not None:
            g.set(x, y, 12, AIR)
    for pos, b in (((14, 1, 13), COBD), ((15, 1, 13), CDB), ((14, 1, 14), "minecraft:cobbled_deepslate_slab[type=bottom]"),
                   ((15, 1, 14), CHD), ((13, 1, 14), "minecraft:deepslate_brick_stairs[facing=west]"), ((16, 1, 13), DB)):
        g.set(*pos, b)
    # braziers either side of the doorway
    for x in (9, 15):
        g.set(x, 1, 14, "minecraft:polished_blackstone_brick_wall")
        g.set(x, 2, 14, "minecraft:polished_blackstone_brick_wall")
        g.set(x, 3, 14, "minecraft:soul_campfire[facing=north,lit=true]")
    g.set(10, 1, 13, "minecraft:lantern[hanging=false]")
    # tunnel dressing: lanterns, a few runes in the floor
    for z in (7, 10):
        g.set(11, 4, z, "minecraft:lantern[hanging=true]")
        g.set(13, 4, z, "minecraft:lantern[hanging=true]")
    g.set(12, 0, 8, RUNE)
    g.set(12, 0, 11, RUNE)
    # plaza: the collapsed statue (a dwarf king: plinth, one boot, the torso on its side) and fallen paving
    for x in range(17, 20):
        for z in range(16, 19):
            g.set(x, 1, z, PD)
    g.set(18, 2, 17, "minecraft:polished_deepslate_wall")
    g.set(18, 3, 17, "minecraft:polished_deepslate_wall")
    g.set(18, 4, 17, "minecraft:cracked_deepslate_bricks")
    g.set(17, 2, 17, "minecraft:deepslate_brick_stairs[facing=east,half=bottom]")
    for x, z, b in ((14, 18, CHD), (15, 18, "minecraft:polished_deepslate_stairs[facing=east]"), (14, 19, "minecraft:deepslate_brick_slab[type=bottom]"),
                    (15, 19, DB), (16, 20, "minecraft:cobbled_deepslate_slab[type=bottom]"), (13, 20, COBD), (20, 20, CDB)):
        g.set(x, 1, z, b)
    g.set(15, 2, 18, "minecraft:skeleton_skull[rotation=4]")
    g.set(7, 1, 17, "skyrpg_world:waystone")
    for x, z in ((8, 20), (16, 16)):
        g.set(x, 1, z, "minecraft:polished_blackstone_brick_wall")
        g.set(x, 2, z, "minecraft:lantern[hanging=false]")
    for z in range(14, 21, 2):
        for x in (4, 20):
            if math.hypot(x - 12, z - 12) <= 9 and rng.random() < .6:
                g.set(x, 1, z, rng.choice([COBD, CDB, "minecraft:deepslate_brick_slab[type=bottom]"]))
    g.marker(12, 1, 16, "dungeon", dungeon=DUN)
    # landings: spawners, chest, traps, braziers, runes (global y of each landing's floor; stairwell-local y' = y + 76)
    (_, _, y1), (x2, z2, y2), (x3, z3, y3), (x4, z4, y4), (x5, z5, y5), (x6, z6, y6) = landings[1:]
    lo = lambda y: y + STAIR_H  # noqa: E731
    stair.spawn(x2, z2, [miner(4, L(1, 0)), archer(2, L(1, 1))], [construct(2, L(1, 2)), miner(3, L(1, 1))], radius=9, spread=2, y=lo(y2) + 1)
    st.chest(3, lo(y3) + 1, 21, "north", "skyrpg_world:chests/dwarven_common", tier="common")
    stair.spawn(x4, z4, [construct(2, L(1, 1)), miner(4, L(1, 1))], [miner(3, L(1, 2), "Lost Foreman", True), archer(3, L(1, 2))], radius=9, spread=2, y=lo(y4) + 1)
    stair.spawn(x5, z5, [archer(4, L(1, 1)), miner(3, L(1, 1))], radius=9, spread=2, y=lo(y5) + 1)
    st.chest(21, lo(y6) + 1, 21, "north", "skyrpg_world:chests/dwarven_common", tier="common")
    for (ox, oz, by) in ((21, 3, y1), (3, 3, y4), (5, 21, y3), (21, 19, y2)):
        split.set(ox, by + 1, oz, "minecraft:polished_blackstone_brick_wall")
        split.set(ox, by + 2, oz, "minecraft:soul_campfire[facing=north,lit=true]")
    for (bx, bz, by) in landings[1:]:
        split.set(bx, by, bz, RUNE)
    # arrow traps in the walls of three legs (south leg, west leg, north leg)
    for leg_i in (1, 2, 3):
        (dx, dz), cells = legs[leg_i]
        cx, cz, yf = cells[len(cells) // 2]
        if dx:
            sx, sz, fc = cx, (cz + 2 if cz > 12 else cz - 2), ("north" if cz > 12 else "south")
        else:
            sx, sz, fc = (cx + 2 if cx > 12 else cx - 2), cz, ("west" if cx > 12 else "east")
        assert (sx, yf + 2, sz) not in sh.air and yf + 2 < 0
        stair.arrows(sx, lo(yf + 2), sz, fc, radius=8, damage=7, every=50)
    # the bottom door (frame; jigsaw at (24, 1, 20) of the stairwell)
    stair.door("east", "spine_out", pool=f"{DH}/f1_s1", at=20, y=1)
    # the vertical join: a 'down' jigsaw in the gate's floor (in the paving, 12,0,14) and an 'up' one on top of the stairwell
    g.jigsaw(12, 0, 14, "down_north", "dwarf_stair_out", "dwarf_stair_in", f"{DH}/stairwell", final_state=DT)
    st.jigsaw(12, STAIR_H - 1, 14, "up_north", "dwarf_stair_in", "dwarf_stair_in", "minecraft:empty", final_state=DB)
    for room_ in (gate, stair):
        room_.weather()
        autoconnect(room_.p)
        pack.add(room_.p)
    return gate.p, stair.p


# ---------------------------------------------------------------------------------------------------------------------
# The descent: a switchback stairwell, 20 down. Enters (south wall, x=15) at y=21, leaves (east wall, z=15) at y=1.
# ---------------------------------------------------------------------------------------------------------------------
def build_descent(pack):
    W = D = 19
    H = DROP + 8
    r = Room("descent", W, H, D, 1)
    p = r.p
    sh = Shaft(r, top_rock_depth=0)
    sh.fill_rock(H - 1)
    y0 = DROP
    # entry corridor (x 14..16, z 11..17) and the top landing (x 14..16, z 8..10)
    for x in (14, 15, 16):
        for z in range(11, 18):
            p.set(x, y0, z, DT)
            for y in range(y0 + 1, y0 + 5):
                sh.carve(x, y, z)
    sh.landing(14, 8, 16, 10, y0)
    cells1 = sh.run(13, 9, -1, 0, 9, y0 - 1)                 # flight 1 west along z=9: floors 19 .. 11
    sh.landing(2, 8, 4, 16, y0 - 10)                          # the middle landing (floor 10)
    cells2 = sh.run(5, 15, 1, 0, 9, y0 - 11)                  # flight 2 east along z=15: floors 9 .. 1
    sh.landing(14, 14, 16, 16, 0, rune=(15, 15))              # the bottom landing
    for x in (17, 18):
        for z in (14, 15, 16):
            p.set(x, 0, z, DT)
            for y in range(1, 5):
                sh.carve(x, y, z)
    for cells in (cells1, cells2):
        for i, (cx, cz, yf) in enumerate(cells):
            if i % 4 == 2:
                p.set(cx, yf + 4, cz, "minecraft:lantern[hanging=true]")
    sh.line()
    r.door("south", "spine_in", at=15, y=DROP + 1)
    r.door("east", "spine_out", pool=f"{DH}/f2_s1", at=15, y=1)
    x, yf = 8, cells1[5][2]
    r.arrows(x, yf + 2, 11, "north", radius=8, damage=7, every=50)
    x, yf = 10, cells2[5][2]
    r.arrows(x, yf + 2, 13, "south", radius=8, damage=7, every=50)
    r.spawn(3, 12, [construct(3, L(1, 3)), miner(3, L(1, 2))], [archer(3, L(1, 3)), miner(2, L(1, 2), "Lost Foreman", True)],
            radius=9, spread=2, y=DROP - 10 + 1)
    p.set(15, 0, 15, RUNE)
    for x, z in ((2, 8), (4, 16)):
        p.set(x, DROP - 10 + 1, z, "minecraft:polished_blackstone_brick_wall")
        p.set(x, DROP - 10 + 2, z, "minecraft:soul_campfire[facing=north,lit=true]")
    r.weather()
    autoconnect(p)
    pack.add(p)
    return p


# ---------------------------------------------------------------------------------------------------------------------
# Forgeguard's arena (floor 1 champion): 25 x 12 x 19, the north door is shut with a bar plug until it falls
# ---------------------------------------------------------------------------------------------------------------------
def build_champion(pack):
    w, h, d = 25, 12, 19
    r = Room("f1/champion", w, h, d, 1)
    r.shell()
    r.ribs(7, 4)
    p = r.p
    cx = w // 2
    r.door("south", "spine_in")
    r.door("north", "spine_out", pool=f"{DH}/descent")
    for z in (2, 3):
        for x in (cx - 1, cx, cx + 1):
            for y in (1, 2, 3):
                p.set(x, y, z, "minecraft:iron_bars")
    p.marker(cx, 2, 2, "door", group="champion", message="The portcullis holds. Slay the Forgeguard.")
    r.reserve(cx - 2, 0, cx + 2, 4)
    r.lava_bay(1, 3, 3, 7, "east")
    r.lava_bay(w - 4, 3, w - 2, 7, "west")
    # the forge dais: a copper plinth with anvils and blast furnaces, runes round it
    r.fill(cx - 3, 1, 8, cx + 3, 1, 12, COP)
    for x, z, a in ((cx - 3, 8, "east"), (cx + 3, 8, "west"), (cx - 3, 12, "east"), (cx + 3, 12, "west")):
        p.set(x, 2, z, f"minecraft:anvil[facing={'south' if a == 'east' else 'north'}]")
    for x in (cx - 1, cx + 1):
        p.set(x, 2, 12, "minecraft:blast_furnace[facing=north,lit=true]")
    p.set(cx, 2, 12, "minecraft:smithing_table")
    r.reserve(cx - 3, 8, cx + 3, 12)
    r.runes([(cx + dx, 7) for dx in range(-4, 5, 2)] + [(cx + dx, 13) for dx in range(-4, 5, 2)] + [(cx - 4, z) for z in (9, 10, 11)] + [(cx + 4, z) for z in (9, 10, 11)])
    for x in (4, w - 5):
        for z in (4, 10, 14):
            r.col(x, z, rune=True)
    for x in (cx - 4, cx + 4):
        for z in (4, 14):
            r.brazier(x, z)
    for z in (5, 10, 15):
        r.hang(cx - 3, z)
        r.hang(cx + 3, z)
    r.chest(2, d - 3, "north", "rare")
    r.chest(w - 3, d - 3, "north", "rare")
    r.chest(w - 3, 9, "west", "common") if r.free(w - 3, 9) else None
    r.arrows(0, 3, 10, "east", radius=11, damage=8)
    r.arrows(w - 1, 3, 12, "west", radius=11, damage=8)
    r.spikes(6, 14, 1)
    r.spikes(w - 7, 14, 1)
    r.flames(cx - 6, 11, 1)
    r.flames(cx + 6, 11, 1)
    r.spawn(cx, 14, [miner(3, L(1, 3)), archer(3, L(1, 3))], [construct(3, L(1, 4)), miner(2, L(1, 3))], radius=9, spread=3)
    p.marker(cx, 2, 10, "boss", boss="skyrpg_world:dwarven_champion", radius=12, group="champion")
    return r.finish(pack)


# ---------------------------------------------------------------------------------------------------------------------
# The Deep Forge (floor 2 boss arena): 31 x 15 x 31
# ---------------------------------------------------------------------------------------------------------------------
KEY_DOOR = "minecraft:reinforced_deepslate"
NOOK_DOOR = "minecraft:gilded_blackstone"


def build_deep_forge(pack):
    w, h, d = 31, 15, 31
    r = Room("boss/deep_forge", w, h, d, 2)
    r.shell()
    p = r.p
    cx = 15
    r.door("south", "spine_in")
    r.reserve(cx - 1, 20, cx + 1, d - 1)
    # the throne alcove, north (x 12..18), flanked by the two annexes behind partition walls at z=6
    for x in range(1, 30):
        for y in range(1, h - 1):
            if x <= 11 or x >= 19:
                p.set(x, y, 6, PBB)
    for z in range(1, 7):
        for y in range(1, h - 1):
            p.set(11, y, z, PBB)
            p.set(19, y, z, PBB)
    # vault (west, key door at x 5..7) and the hoard nook (east, boss-death door at x 23..25)
    for x in (5, 6, 7):
        for y in (1, 2, 3):
            p.set(x, y, 6, KEY_DOOR)
    p.marker(6, 2, 6, "key_door", key="skyrpg_world:thane_key", message="The vault is sealed. It needs the Thane's Key.")
    for x in (23, 24, 25):
        for y in (1, 2, 3):
            p.set(x, y, 6, NOOK_DOOR)
    p.marker(24, 2, 6, "door", group="thane", message="The Thane's hoard grinds open.")
    for x in range(2, 11):
        for z in range(1, 6):
            for y in range(1, h - 1):
                p.set(x, y, z, AIR if y < 6 else PBB)
    for x in range(20, 29):
        for z in range(1, 6):
            for y in range(1, h - 1):
                p.set(x, y, z, AIR if y < 6 else PBB)
    p.chest(4, 1, 1, "south", "skyrpg_world:chests/dwarven_vault", tier="vault")
    p.chest(8, 1, 1, "south", "skyrpg_world:chests/dwarven_vault", tier="vault")
    p.chest(6, 1, 1, "south", "skyrpg_world:chests/dwarven_rare", tier="rare")
    for pos, b in (((5, 1, 1), "minecraft:gold_block"), ((7, 1, 1), "minecraft:gold_block"), ((3, 1, 1), "minecraft:amethyst_cluster[facing=up]"),
                   ((9, 1, 1), "minecraft:amethyst_cluster[facing=up]"), ((6, 5, 3), "minecraft:soul_lantern[hanging=true]"),
                   ((3, 4, 3), "minecraft:soul_lantern[hanging=true]"), ((9, 4, 3), "minecraft:soul_lantern[hanging=true]")):
        p.set(*pos, b)
    p.chest(24, 1, 1, "south", "skyrpg_world:chests/dwarven_secret", tier="secret")
    p.marker(24, 1, 2, "secret", name="The Thane's Hoard")
    for x in (22, 26):
        p.set(x, 1, 1, "minecraft:gold_block")
    p.set(24, 5, 3, "minecraft:soul_lantern[hanging=true]")
    r.sign(4, 2, 7, ["The vault is sealed", "behind iron and rune.", "Bring the Thane's Key."], "south")
    # the throne: a stepped dais against the north wall
    for x in range(13, 18):
        p.set(x, 1, 1, COP)
        p.set(x, 1, 2, COP)
    p.set(15, 2, 1, "minecraft:polished_blackstone_stairs[facing=south]")
    p.set(15, 3, 1, CHPB)
    p.set(14, 2, 1, PBB)
    p.set(16, 2, 1, PBB)
    for x in (13, 17):
        p.set(x, 2, 1, "minecraft:soul_campfire[facing=north,lit=true]")
    for x in (12, 18):
        r.keep |= {(x, z) for z in range(1, 7)}
    r.reserve(1, 1, 29, 6)
    # lava bays down both long walls (glass windows), 3 deep
    r.lava_bay(1, 9, 3, 28, "east")
    r.lava_bay(w - 4, 9, w - 2, 28, "west")
    # the dais: copper, anvils, blast furnaces; the Thane wakes on it
    cz = 18
    for x in range(cx - 4, cx + 5):
        for z in range(cz - 4, cz + 5):
            if math.hypot(x - cx, z - cz) <= 4.3:
                p.set(x, 0, z, COPC if (x + z) % 2 else COP)
    for dx, dz in ((-3, -2), (3, -2), (-3, 2), (3, 2)):
        p.set(cx + dx, 1, cz + dz, f"minecraft:anvil[facing={'east' if dx < 0 else 'west'}]")
    for dx in (-1, 1):
        p.set(cx + dx, 1, cz - 3, "minecraft:blast_furnace[facing=south,lit=true]")
    ring = []
    for i in range(24):
        a = math.radians(15 * i)
        x, z = round(cx + 8 * math.cos(a)), round(cz + 8 * math.sin(a))
        if 1 <= x <= w - 2 and 7 <= z <= d - 2 and (x, z) not in ring:
            ring.append((x, z))
    r.runes(ring)
    for x, z in ((7, 12), (23, 12), (7, 26), (23, 26), (10, 8), (20, 8)):
        r.col(x, z, rune=True, y1=h - 3)
        r.set(x, 6, z, COPC)
        r.keep.add((x, z))
    for x, z in ((cx - 6, cz), (cx + 6, cz), (cx, cz - 6), (cx, cz + 6), (11, 22), (19, 22), (11, 14), (19, 14)):
        r.hang(x, z, 3)
    for x, z in ((6, 20), (24, 20), (6, 15), (24, 15)):
        r.brazier(x, z)
    r.chest(8, 29, "north", "rare") if r.free(8, 29) else None
    r.arrows(3, 3, 6, "south", radius=14, damage=9)
    r.arrows(27, 3, 6, "south", radius=14, damage=9)
    r.flames(9, 14, 1)
    r.flames(21, 14, 1)
    r.rubble(8, 5, 10, w - 6, d - 3)
    p.marker(cx, 2, cz, "boss", boss="skyrpg_world:iron_thane", radius=16, group="thane")
    EXEMPT[p.path] = ("secret",)
    return r.finish(pack)


# ---------------------------------------------------------------------------------------------------------------------
# Side rooms (door in on the south wall, optional tail door on the north wall) and leaves
# ---------------------------------------------------------------------------------------------------------------------
def room(path, w, h, d, fl, tail=True):
    r = Room(path, w, h, d, fl)
    r.shell()
    r.door("south", "side_in")
    if tail:
        r.door("north", "tail_out")
    r.lane("x")
    return r


def cache(r, x1, z1, x2, z2, chest_at, facing, wall, name):
    """A hidden pocket closed by `wall` (break in): a secret-tier chest and a 'secret' marker."""
    p = r.p
    for x in range(x1, x2 + 1):
        for z in range(z1, z2 + 1):
            edge = x in (x1, x2) or z in (z1, z2)
            for y in (1, 2, 3):
                p.set(x, y, z, wall if edge else AIR)
    r.chest(chest_at[0], chest_at[1], facing, "secret")
    mx, mz = (x1 + x2) // 2, (z1 + z2) // 2
    p.marker(mx, 1, mz, "secret", name=name)
    r.reserve(x1, z1, x2, z2)
    EXEMPT.setdefault(p.path, ())
    EXEMPT[p.path] = tuple(EXEMPT[p.path]) + ("secret",)


def room_vault(pack, fl):
    r = room(f"f{fl}/vault", 13, 8, 13, fl)
    table = None if fl == 1 else [(DQ_ORE_D, 4), ("skyrpg_mining:deepslate_emberite_ore", 3), ("skyrpg_mining:deepslate_skyiron_ore", 1), (r.pal.wall, 2)]
    for x in (1, 11):
        r.ore_wall(x, 1, 2, x, 5, 10, table)
    for x0, x1 in ((2, 4), (8, 10)):
        r.ore_wall(x0, 1, 1, x1, 5, 1, table)
        r.ore_wall(x0, 1, 11, x1, 5, 11, table)
    r.chest(2, 5, "east", "rare")
    r.chest(10, 5, "west", "rare")
    for x in (2, 10):
        r.set(x, 1, 6, "minecraft:gold_block")
        r.set(x, 1, 4, "minecraft:amethyst_cluster[facing=up]")
        r.keep |= {(x, 6), (x, 4)}
    for z in (3, 9):
        r.hang(4, z)
        r.hang(8, z)
    r.spikes(4, 8, 1)
    r.spikes(8, 3, 1)
    r.spawn(6, 6, [miner(4, L(fl, 2)), construct(2, L(fl, 3))] if fl == 1 else [construct(3, L(fl, 3)), miner(3, L(fl, 3))], radius=8, spread=3)
    return r.finish(pack)


def room_library(pack, fl):
    r = room(f"f{fl}/library", 17, 9, 15, fl)
    for x in (1, 15):
        for z in range(5, 13):
            for y in range(1, 5):
                r.set(x, y, z, "minecraft:bookshelf")
    r.set(1, 2, 8, "minecraft:chiseled_bookshelf[facing=east]")
    r.set(15, 2, 8, "minecraft:chiseled_bookshelf[facing=west]")
    for x in (4, 12):
        for z in (4, 5, 10, 11):
            for y in range(1, 4):
                r.set(x, y, z, "minecraft:bookshelf")
            r.keep.add((x, z))
    r.set(6, 1, 7, "minecraft:lectern[facing=south,has_book=false,powered=false]")
    r.set(10, 1, 7, "minecraft:lectern[facing=south,has_book=false,powered=false]")
    r.keep |= {(6, 7), (10, 7)}
    cache(r, 1, 1, 4, 4, (2, 2), "south", "minecraft:bookshelf", "Archivist's Alcove")
    r.chest(14, 2, "south", "rare")
    for z in (3, 8, 12):
        r.hang(5, z)
        r.hang(11, z)
    r.runes([(7, 3), (9, 3), (7, 12), (9, 12)])
    r.spawn(8, 8, [archer(5, L(fl, 1)), miner(2, L(fl, 1))], [construct(2, L(fl, 2)), archer(3, L(fl, 2))], radius=9, spread=4)
    return r.finish(pack)


def room_collapsed(pack, fl):
    r = room(f"f{fl}/collapsed_tunnel", 13, 9, 23, fl)
    p = r.p
    for z in range(1, 22):
        if z % 7:
            p.set(6, 1, z, "minecraft:rail[shape=north_south]")
    for z in (4, 10, 16):
        for x in (3, 9):
            for y in (1, 2, 3):
                p.set(x, y, z, "minecraft:dark_oak_log[axis=y]")
            r.keep.add((x, z))
        for x in range(3, 10):
            p.set(x, 4, z, "minecraft:dark_oak_log[axis=x]")
    for zc in (7, 13):
        for x in list(range(1, 4)) + list(range(9, 12)):
            for z in range(zc - 1, zc + 2):
                hgt = r.rng.randint(1, 3)
                for y in range(1, hgt + 1):
                    p.set(x, y, z, r.rng.choice([COBD, CDB, "minecraft:gravel", COBD, "minecraft:coarse_dirt"]))
                r.keep.add((x, z))
    p.set(8, 1, 12, "minecraft:cobweb")
    p.set(4, 1, 19, "minecraft:cobweb")
    p.barrel(10, 1, 19, "west", "skyrpg_world:chests/dwarven_common", tier="common")
    r.keep.add((10, 19))
    r.chest(2, 20, "east", "rare" if fl == 2 else "common")
    r.hang(6, 3)
    r.hang(6, 12)
    r.hang(6, 19)
    r.spawn(6, 10, [miner(6, L(fl, 0))], [miner(3, L(fl, 1)), construct(2, L(fl, 2))], radius=10, spread=4)
    return r.finish(pack)


def room_armory(pack, fl):
    r = room(f"f{fl}/armory", 15, 9, 15, fl)
    r.set(2, 1, 3, "minecraft:smithing_table")
    r.anvil(2, 5, "north")
    r.set(2, 1, 7, "minecraft:grindstone[face=floor,facing=east]")
    r.set(2, 1, 9, "minecraft:fletching_table")
    r.furnace(2, 11, "east")
    r.set(12, 1, 3, "minecraft:grindstone[face=floor,facing=west]")
    r.anvil(12, 5, "south")
    r.furnace(12, 7, "west")
    r.set(12, 1, 9, "minecraft:smithing_table")
    r.furnace(12, 11, "west")
    for x in (2, 12):
        for z in (3, 7, 9):
            r.keep.add((x, z))
    for z in range(2, 13, 2):
        r.set(1, 2, z, "minecraft:iron_bars")
        r.set(13, 2, z, "minecraft:iron_bars")
    r.chest(4, 2, "south", "rare")
    r.chest(10, 2, "south", "rare")
    r.chest(4, 12, "north", "common")
    for z in (4, 10):
        r.hang(5, z)
        r.hang(9, z)
    r.arrows(14, 3, 8, "west")
    r.spawn(7, 8, [construct(3, L(fl, 2)), archer(3, L(fl, 2))], [construct(2, L(fl, 3)), miner(3, L(fl, 3), "Armsmaster", elite=True)], radius=9, spread=4)
    return r.finish(pack)


def room_mess(pack, fl):
    r = room(f"f{fl}/mess_hall", 17, 8, 15, fl)
    for x0, bench_in, bench_out in ((3, "east", "west"), (12, "west", "east")):
        for z in range(4, 11):
            for x in (x0, x0 + 1):
                r.set(x, 1, z, "minecraft:dark_oak_planks")
                r.keep.add((x, z))
            bx = x0 - 1 if x0 == 3 else x0 + 2
            r.set(bx, 1, z, f"minecraft:dark_oak_stairs[facing={bench_in},half=bottom]")
            r.keep.add((bx, z))
    for x, z in ((2, 2), (3, 2), (14, 2), (15, 2)):
        r.set(x, 1, z, "minecraft:barrel[facing=up]")
        r.keep.add((x, z))
    r.furnace(1, 12, "east", "smoker")
    r.set(2, 1, 12, "minecraft:cauldron")
    r.set(14, 1, 12, "minecraft:cauldron")
    r.furnace(15, 12, "west", "smoker")
    r.keep |= {(1, 12), (2, 12), (14, 12), (15, 12)}
    r.chest(2, 13, "north", "common")
    r.chest(14, 13, "north", "common")
    for z in (4, 9):
        r.hang(3, z)
        r.hang(13, z)
    r.spawn(8, 8, [miner(6, L(fl, 0)), archer(2, L(fl, 1))], radius=10, spread=4)
    return r.finish(pack)


def room_forge(pack, fl):
    r = room(f"f{fl}/forge_room", 15, 9, 17, fl)
    r.lava_bay(1, 3, 3, 7, "east")
    r.lava_bay(11, 3, 13, 7, "west")
    for x, side in ((5, "west"), (9, "east")):
        r.anvil(x, 11, "north")
        r.furnace(x + (-1 if side == "west" else 1), 11, "east" if side == "west" else "west")
    r.flames(7, 8, 1)
    r.chest(2, 14, "east", "rare")
    r.chest(12, 14, "west", "common")
    for z in (5, 11):
        r.hang(5, z)
        r.hang(9, z)
    r.spawn(7, 12, [construct(3, L(fl, 2)), miner(3, L(fl, 2))], [construct(2, L(fl, 3)), archer(3, L(fl, 3))], radius=9, spread=3)
    return r.finish(pack)


def room_cart_depot(pack, fl):
    r = room(f"f{fl}/cart_depot", 17, 9, 19, fl)
    p = r.p
    for z in range(1, 18):
        p.set(8, 1, z, "minecraft:rail[shape=north_south]")
    for z in range(3, 16):
        p.set(13, 1, z, "minecraft:rail[shape=north_south]")
        r.keep.add((13, z))
    for x in (9, 10, 11, 12):                     # a siding that joins the main line at z=14
        p.set(x, 1, 14, "minecraft:rail[shape=east_west]")
        r.keep.add((x, 14))
    p.entity(8, 1, 5, {"id": "minecraft:minecart"})
    p.entity(13, 1, 5, {"id": "minecraft:minecart"})
    p.entity(13, 1, 9, {"id": "minecraft:chest_minecart", "LootTable": "skyrpg_world:chests/dwarven_cart"})
    for x, z in ((2, 2), (3, 2), (2, 3), (14, 16), (15, 16), (15, 15)):
        r.set(x, 1, z, "minecraft:raw_iron_block" if (x + z) % 2 else "minecraft:raw_copper_block")
        r.keep.add((x, z))
    r.chest(3, 10, "east", "common")
    r.chest(15, 3, "west", "rare" if fl == 2 else "common")
    for z in (4, 9, 14):
        r.hang(6, z)
        r.hang(11, z)
    r.arrows(0, 3, 8, "east")
    r.spawn(8, 9, [miner(5, L(fl, 1)), archer(3, L(fl, 1))], radius=10, spread=4)
    return r.finish(pack)


def room_shrine(pack, fl):
    r = room(f"f{fl}/rune_shrine", 15, 10, 15, fl)
    cx = cz = 7
    ring = [(round(cx + 3 * math.cos(math.radians(a))), round(cz + 3 * math.sin(math.radians(a)))) for a in range(0, 360, 30)]
    r.runes(ring)
    r.set(cx, 1, cz, CHD)
    r.set(cx, 2, cz, "minecraft:soul_lantern[hanging=false]")
    r.keep.add((cx, cz))
    for x, z in ((3, 3), (11, 3), (3, 11), (11, 11)):
        r.col(x, z, rune=True)
    for x in (1, 13):
        for y in (1, 2, 3):
            r.set(x, y, 7, CHD)
        r.set(x, 4, 7, "minecraft:skeleton_skull[rotation=0]")
        r.keep.add((x, 7))
    r.chest(2, 2, "south", "rare")
    r.chest(12, 12, "north", "rare")
    for z in (4, 10):
        r.hang(5, z)
        r.hang(9, z)
    r.spawn(cx, cz + 1, [construct(3, L(fl, 2)), archer(2, L(fl, 2))], [construct(3, L(fl, 3)), miner(3, L(fl, 2))], radius=8, spread=3)
    return r.finish(pack)


def room_plate_vault(pack, fl):
    r = room(f"f{fl}/plate_vault", 15, 9, 17, fl, tail=False)
    p = r.p
    door_block = "minecraft:polished_blackstone_bricks" if fl == 1 else "minecraft:reinforced_deepslate"
    for x in range(1, 14):
        for y in range(1, 8):
            p.set(x, y, 5, r.pal.wall)
    for x in (6, 7, 8):
        for y in (1, 2, 3):
            p.set(x, y, 5, door_block)
    p.marker(7, 2, 5, "door", group="plates", message="The sealed door sinks into the floor.")
    for x, z in ((2, 12), (12, 12), (7, 8)):
        r.fill(x - 1, 0, z - 1, x + 1, 0, z + 1, r.pal.inlay)
        p.set(x, 1, z, "minecraft:stone_pressure_plate")
        p.marker(x, 1, z, "plate", group="plates")
        r.keep.add((x, z))
    r.runes([(x, z) for x, z in ((2, 11), (12, 11), (6, 8), (8, 8))])
    r.sign(5, 2, 6, ["Three plates.", "One door.", "The ancestors", "keep the hoard."], "south")
    r.chest(3, 2, "south", "rare")
    r.chest(11, 2, "south", "rare")
    r.set(7, 1, 2, "minecraft:gold_block")
    p.marker(7, 1, 3, "secret", name="Ancestors' Treasury")
    r.keep |= {(7, 2), (7, 3)}
    for x in (5, 9):
        p.set(x, 5, 3, "minecraft:soul_lantern[hanging=true]")
    r.arrows(0, 3, 10, "east")
    r.arrows(14, 3, 10, "west")
    r.spawn(7, 13, [miner(3, L(fl, 1)), archer(2, L(fl, 1)), construct(1, L(fl, 2))], radius=8, spread=3)
    return r.finish(pack)


# -- floor 2 rooms ----------------------------------------------------------------------------------------------------
def room_forge_works(pack, fl):
    r = room(f"f{fl}/forge_works", 19, 12, 19, fl)
    r.lava_bay(1, 2, 3, 8, "east")
    r.lava_bay(1, 11, 3, 16, "east")
    r.lava_bay(15, 2, 17, 8, "west")
    r.lava_bay(15, 11, 17, 16, "west")
    for z in (5, 13):
        for x, ax in ((6, "north"), (12, "north")):
            r.anvil(x, z, ax)
        r.furnace(5, z, "east")
        r.furnace(13, z, "west")
    for x, z in ((7, 9), (11, 9), (7, 15), (11, 15)):
        r.col(x, z, rune=True)
    r.flames(9, 6, 1)
    r.flames(9, 12, 1)
    r.chest(5, 17, "north", "rare")
    r.chest(13, 17, "north", "rare")
    for z in (4, 9, 14):
        r.hang(7, z, 3)
        r.hang(11, z, 3)
    r.spawn(9, 9, [construct(4, L(fl, 3)), miner(2, L(fl, 2))], [construct(3, L(fl, 4)), construct(1, L(fl, 5), "Runeguard", elite=True), archer(3, L(fl, 3))],
            radius=10, spread=4)
    return r.finish(pack)


def room_lava_cistern(pack, fl):
    r = room(f"f{fl}/lava_cistern", 19, 11, 21, fl)
    p = r.p
    lava = {(x, z) for x in range(3, 16) for z in range(4, 17)} - {(x, z) for x in (8, 9, 10) for z in range(4, 17)}
    bridge = {(x, z) for x in (8, 9, 10) for z in range(4, 17)}
    for x, z in lava:
        p.set(x, 0, z, "minecraft:magma_block")
        p.set(x, 1, z, "minecraft:lava")
        r.keep.add((x, z))
    for x, z in bridge:
        p.set(x, 1, z, "minecraft:oxidized_cut_copper_slab[type=bottom]")
    rim = set()
    for x, z in lava | bridge:
        for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            n = (x + dx, z + dz)
            if n not in lava and n not in bridge and 1 <= n[0] <= 17 and 1 <= n[1] <= 19:
                rim.add(n)
    for x, z in rim:
        if not (8 <= x <= 10):
            p.set(x, 1, z, "minecraft:polished_blackstone_brick_wall")
            r.keep.add((x, z))
    for x in (7, 11):                              # lanterns on posts along the bridge
        for z in (6, 10, 14):
            p.set(x, 1, z, "minecraft:polished_blackstone_brick_wall")
            p.set(x, 2, z, "minecraft:soul_lantern[hanging=false]")
    r.chest(2, 2, "south", "rare")
    r.chest(16, 2, "south", "rare")
    r.chest(2, 18, "north", "common")
    r.runes([(9, 3), (9, 17)])
    for z in (3, 10, 17):
        r.hang(4, z, 3)
        r.hang(14, z, 3)
    r.arrows(0, 3, 10, "east", radius=12)
    r.arrows(18, 3, 10, "west", radius=12)
    r.spawn(9, 18, [construct(3, L(fl, 3)), archer(4, L(fl, 3))], [construct(3, L(fl, 4)), miner(3, L(fl, 3))], radius=10, spread=3)
    return r.finish(pack)


def room_tomb(pack, fl):
    r = room(f"f{fl}/kings_tomb", 17, 10, 19, fl)
    p = r.p
    for z in (4, 9, 14):
        for x0 in (2, 12):
            for dx in range(3):
                p.set(x0 + dx, 1, z, "minecraft:polished_blackstone_slab[type=bottom]")
                p.set(x0 + dx, 1, z + 1, "minecraft:polished_blackstone_slab[type=bottom]")
            p.set(x0 + (0 if x0 == 2 else 2), 2, z, "minecraft:skeleton_skull[rotation=%d]" % (4 if x0 == 2 else 12))
            r.keep |= {(x0 + dx, zz) for dx in range(3) for zz in (z, z + 1)}
    for x, z in ((5, 3), (11, 3), (5, 16), (11, 16)):
        r.col(x, z, rune=True)
    cache(r, 1, 1, 4, 3, (2, 2), "south", CPBB, "Sealed Sarcophagus")
    r.chest(15, 17, "north", "rare")
    for z in (3, 9, 15):
        r.hang(6, z, 3)
        r.hang(10, z, 3)
    r.spawn(8, 9, [archer(5, L(fl, 3), "Ancestor's Archer"), construct(2, L(fl, 3))], [miner(4, L(fl, 3), "Restless Delver"), construct(3, L(fl, 4))], radius=10, spread=4)
    return r.finish(pack)


def room_foundry(pack, fl):
    r = room(f"f{fl}/foundry", 17, 10, 17, fl)
    p = r.p
    for z in (4, 8, 12):
        for x in (3, 13):
            p.set(x, 1, z, "minecraft:iron_block")
            p.set(x, 2, z, "minecraft:iron_bars")
            p.set(x, 3, z, "minecraft:iron_bars")
            r.furnace(x + (1 if x == 3 else -1), z, "west" if x == 3 else "east")
            r.keep |= {(x, z), (x + (1 if x == 3 else -1), z)}
    r.flames(8, 5, 1)
    r.flames(8, 12, 1)
    for x, z in ((6, 3), (10, 3), (6, 14), (10, 14)):
        r.col(x, z, rune=True)
    r.chest(2, 14, "east", "rare")
    r.chest(14, 14, "west", "rare")
    for z in (4, 8, 12):
        r.hang(8, z, 3)
    r.spawn(8, 8, [construct(4, L(fl, 3)), miner(2, L(fl, 3))], [construct(4, L(fl, 4)), archer(2, L(fl, 3))], radius=10, spread=3)
    return r.finish(pack)


def room_ore_gallery(pack, fl):
    r = room(f"f{fl}/ore_gallery", 19, 11, 19, fl)
    p = r.p
    table = [(DQ_ORE_D, 5), ("skyrpg_mining:deepslate_emberite_ore", 2), ("skyrpg_mining:deepslate_skyiron_ore", 2), (r.pal.wall, 1)]
    for x in (1, 17):
        r.ore_wall(x, 1, 3, x, 6, 15, table)
    cx = cz = 9
    for dx in range(-2, 3):
        for dz in range(-2, 3):
            if abs(dx) + abs(dz) <= 3:
                hh = 4 - (abs(dx) + abs(dz))
                for y in range(1, hh + 1):
                    p.set(cx + dx, y, cz + dz, "minecraft:calcite" if y == 1 else "minecraft:amethyst_block")
                p.set(cx + dx, hh + 1, cz + dz, "minecraft:amethyst_cluster[facing=up]")
                r.keep.add((cx + dx, cz + dz))
    r.reserve(8, 0, 10, 18)
    r.chest(3, 2, "south", "rare")
    r.chest(15, 16, "north", "rare")
    cache(r, 1, 15, 4, 18, (2, 17), "north", CPBB, "Prospector's Cache")
    for z in (4, 9, 14):
        r.hang(5, z, 3)
        r.hang(13, z, 3)
    r.spikes(5, 9, 1)
    r.spikes(13, 9, 1)
    r.spawn(9, 6, [miner(5, L(fl, 3)), construct(3, L(fl, 3))], [archer(3, L(fl, 3)), construct(3, L(fl, 4))], radius=10, spread=4)
    return r.finish(pack)


def room_rail_junction(pack, fl):
    r = room(f"f{fl}/rail_junction", 17, 9, 17, fl)
    p = r.p
    for z in range(1, 16):
        p.set(8, 1, z, "minecraft:rail[shape=north_south]")
    for x in list(range(1, 8)) + list(range(9, 16)):
        if abs(x - 8) > 1:
            p.set(x, 1, 8, "minecraft:rail[shape=east_west]")
            r.keep.add((x, 8))
    p.entity(4, 1, 8, {"id": "minecraft:minecart"})
    p.entity(12, 1, 8, {"id": "minecraft:chest_minecart", "LootTable": "skyrpg_world:chests/dwarven_cart"})
    for x, z in ((2, 2), (14, 2), (2, 14), (14, 14)):
        p.set(x, 1, z, "minecraft:raw_iron_block")
        p.set(x, 2, z, "minecraft:raw_gold_block")
        r.keep.add((x, z))
    r.chest(3, 4, "east", "rare")
    r.chest(13, 12, "west", "common")
    for x, z in ((4, 4), (12, 4), (4, 12), (12, 12)):
        r.hang(x, z, 3)
    r.arrows(0, 3, 12, "east")
    r.arrows(16, 3, 4, "west")
    r.spawn(8, 6, [miner(4, L(fl, 3)), archer(3, L(fl, 3)), construct(2, L(fl, 3))], radius=10, spread=4)
    return r.finish(pack)


def room_rune_archive(pack, fl):
    r = room(f"f{fl}/rune_archive", 15, 10, 17, fl, tail=False)
    p = r.p
    door_block = "minecraft:reinforced_deepslate"
    for x in range(1, 14):
        for y in range(1, 9):
            p.set(x, y, 5, r.pal.wall)
    for x in (6, 7, 8):
        for y in (1, 2, 3):
            p.set(x, y, 5, door_block)
    p.marker(7, 2, 5, "door", group="archive", message="The rune door unseals.")
    # three levers on the west wall: down, up, down (the sign on the east wall says so)
    for z, want in ((8, False), (10, True), (12, False)):
        p.set(1, 2, z, "minecraft:lever[face=wall,facing=east,powered=false]")
        p.marker(1, 2, z, "lever", group="archive", want=want)
        r.keep.add((1, z))
    r.sign(13, 2, 10, ["Down. Up. Down.", "The runes obey", "the old order."], "west")
    for x in (3, 11):
        for z in (8, 9, 12, 13):
            for y in range(1, 4):
                p.set(x, y, z, "minecraft:bookshelf")
            r.keep.add((x, z))
    r.runes([(7, 9), (7, 11), (6, 10), (8, 10)])
    r.chest(3, 2, "south", "rare")
    r.chest(11, 2, "south", "rare")
    p.marker(7, 1, 3, "secret", name="The Rune Archive")
    r.set(7, 1, 2, "minecraft:gold_block")
    r.keep |= {(7, 2), (7, 3)}
    for x in (5, 9):
        p.set(x, 6, 3, "minecraft:soul_lantern[hanging=true]")
    for z in (8, 14):
        r.hang(7, z, 3)
    r.arrows(0, 3, 14, "east")
    r.arrows(14, 3, 14, "west")
    r.spawn(7, 14, [construct(3, L(fl, 3)), archer(3, L(fl, 3))], radius=8, spread=3)
    return r.finish(pack)


ROOMS = {
    1: [(room_vault, 2), (room_library, 3), (room_collapsed, 3), (room_armory, 3), (room_mess, 3), (room_forge, 3),
        (room_cart_depot, 3), (room_shrine, 2), (room_plate_vault, 1)],
    2: [(room_vault, 2), (room_forge_works, 3), (room_lava_cistern, 3), (room_tomb, 3), (room_foundry, 3), (room_ore_gallery, 3),
        (room_rail_junction, 2), (room_rune_archive, 1), (room_shrine, 2)],
}


# -- leaves ----------------------------------------------------------------------------------------------------------
def leaf(path, w, d, fl):
    r = Room(path, w, 6, d, fl)
    r.shell()
    r.door("south", "tail_in")
    r.lane("x")
    return r


def leaf_closet(pack, fl):
    r = leaf(f"f{fl}/leaf_closet", 9, 9, fl)
    r.set(2, 1, 2, "minecraft:barrel[facing=up]")
    r.set(6, 1, 2, "minecraft:barrel[facing=up]")
    r.keep |= {(2, 2), (6, 2)}
    r.chest(4, 1, "south", "common" if fl == 1 else "rare")
    r.hang(4, 4, 1)
    r.spawn(4, 5, [miner(4, L(fl, 1))], radius=7, spread=2)
    return r.finish(pack)


def leaf_nook(pack, fl):
    r = leaf(f"f{fl}/leaf_nook", 9, 11, fl)
    cache(r, 1, 1, 4, 4, (2, 2), "south", CDB if fl == 1 else CPBB, "Miner's Nook")
    r.set(7, 1, 2, "minecraft:barrel[facing=up]")
    r.keep.add((7, 2))
    r.hang(4, 6, 1)
    r.spikes(6, 6, 1)
    r.spawn(4, 6, [archer(3, L(fl, 1)), miner(2, L(fl, 1))], radius=7, spread=2)
    return r.finish(pack)


def leaf_guard(pack, fl):
    r = leaf(f"f{fl}/leaf_guard", 11, 9, fl)
    r.arrows(10, 3, 4, "west", radius=8)
    r.chest(2, 1, "south", "common" if fl == 1 else "rare")
    r.set(8, 1, 2, "minecraft:anvil[facing=east]")
    r.keep.add((8, 2))
    r.hang(5, 4, 1)
    r.spawn(5, 4, [archer(3, L(fl, 2)), miner(2, L(fl, 2)), construct(1, L(fl, 3))], radius=7, spread=2)
    return r.finish(pack)


def leaf_ore(pack, fl):
    r = leaf(f"f{fl}/leaf_ore", 9, 9, fl)
    for x in (1, 7):
        r.ore_wall(x, 1, 2, x, 3, 6)
    r.ore_wall(2, 1, 1, 6, 3, 1)
    r.spikes(4, 4, 1)
    r.chest(2, 7, "east", "common")
    r.hang(4, 3, 1)
    return r.finish(pack)


LEAVES = [leaf_closet, leaf_nook, leaf_guard, leaf_ore]


def cap(pack, kind, fl):
    wall = PAL[fl].wall
    p = Piece(f"{DH}/caps/{kind}{fl}", 3, 4, 1)
    p.fill(0, 0, 0, 2, 3, 0, wall)
    p.set(1, 2, 0, PAL[fl].trim)
    p.jigsaw(1, 1, 0, "north_up", f"dwarf_{kind}_in", f"dwarf_{kind}_in", "minecraft:empty", final_state=wall)
    pack.add(p)
    return p


# ---------------------------------------------------------------------------------------------------------------------
# Loot (written in the old function/condition style; Pack.loot_table converts it to 26.3's modifier/condition format)
# ---------------------------------------------------------------------------------------------------------------------
def it(name, w, lo=None, hi=None, fn=None):
    e = {"type": "minecraft:item", "name": name, "weight": w}
    fs = []
    if lo is not None:
        fs.append({"function": "minecraft:set_count",
                   "count": lo if hi in (None, lo) else {"type": "minecraft:uniform", "min": lo, "max": hi}})
    if fn:
        fs += fn
    if fs:
        e["functions"] = fs
    return e


def book(w):
    return it("minecraft:book", w, fn=[{"function": "minecraft:enchant_randomly"}])


def rolls(a, b=None):
    return a if b is None else {"type": "minecraft:uniform", "min": a, "max": b}


def chance(p):
    return [{"condition": "minecraft:random_chance", "chance": p}]


def table(*pools):
    return {"type": "minecraft:chest", "pools": list(pools)}


def lpool(r, entries, p=None):
    d = {"rolls": r, "entries": entries}
    if p is not None:
        d["conditions"] = chance(p)
    return d


M = "skyrpg_mining:"
W = "skyrpg_world:"


def loot(pack):
    common = [it("minecraft:iron_ingot", 8, 3, 8), it("minecraft:coal", 8, 4, 10), it("minecraft:gold_ingot", 5, 2, 5),
              it("minecraft:bread", 5, 3, 6), it("minecraft:cooked_beef", 4, 2, 4), it("minecraft:torch", 5, 6, 14),
              it("minecraft:emerald", 4, 2, 5), it("minecraft:experience_bottle", 5, 2, 5),
              it("skyrpg_enchants:runic_dust", 8, 2, 5), it(M + "skyiron_ingot", 6, 2, 4), it(M + "glimmer_shard", 7, 3, 6),
              it(M + "glimmer_crystal", 3, 1, 2), it(M + "duskquartz_shard", 6, 2, 5), it(M + "emberite_chunk", 3, 1, 2),
              it(W + "dwarven_cog", 5, 1, 2), it(W + "glimmer_geode", 3, 1, 1), it("minecraft:rail", 4, 6, 16), book(3)]
    pack.loot_table("chests/dwarven_common", table(
        lpool(rolls(4, 6), common),
        lpool(1, [it("skyrpg_combat:valor_mark", 3, 1, 1), it("skyrpg_combat:dread_fang", 2, 1, 1),
                  it(W + "rift_ember", 3, 1, 1), it("skyrpg_farming:lucky_clover", 1, 1, 1)], .22)))
    rare = [it("minecraft:diamond", 5, 2, 4), it("minecraft:emerald", 6, 4, 9), it("minecraft:gold_ingot", 7, 5, 12),
            it("minecraft:experience_bottle", 6, 4, 9), it("skyrpg_enchants:runic_dust", 10, 4, 9),
            it(M + "skyiron_ingot", 8, 3, 7), it(M + "glimmer_crystal", 5, 1, 3), it(M + "duskquartz_shard", 8, 4, 9),
            it(M + "emberite_ingot", 5, 1, 3), it(M + "emberite_chunk", 5, 2, 5), it(M + "prism_fragment", 3, 1, 2),
            it(W + "dwarven_cog", 7, 2, 4), it(W + "glimmer_geode", 5, 1, 2), it(W + "rift_ember", 4, 1, 2), book(5)]
    pack.loot_table("chests/dwarven_rare", table(
        lpool(rolls(5, 7), rare),
        lpool(1, [it("skyrpg_combat:elite_trophy", 3, 1, 1), it("skyrpg_combat:valor_mark", 4, 1, 2),
                  it("skyrpg_combat:dread_fang", 3, 1, 2), it(M + "heartstone", 2, 1, 1), it(W + "rift_ember", 3, 1, 2)], .55),
        lpool(1, [it("skyrpg_combat:nightmare_core", 1, 1, 1)], .06),
        lpool(1, [it("skyrpg_pets:owl_egg", 1, 1, 1), it("skyrpg_pets:bee_egg", 1, 1, 1)], .03)))
    secret = [it("minecraft:diamond", 7, 3, 6), it("minecraft:emerald", 8, 5, 12), it("minecraft:gold_ingot", 8, 6, 14),
              it("minecraft:experience_bottle", 6, 5, 10), it("skyrpg_enchants:runic_dust", 10, 5, 11),
              it(M + "duskquartz_shard", 8, 6, 12), it(M + "emberite_ingot", 6, 2, 4), it(M + "skyiron_ingot", 6, 4, 8),
              it(M + "glimmer_crystal", 6, 2, 4), it(M + "prism_fragment", 5, 2, 4), it(W + "dwarven_cog", 8, 3, 5),
              it(W + "glimmer_geode", 6, 1, 3), it(W + "rift_ember", 6, 2, 3), book(6)]
    pack.loot_table("chests/dwarven_secret", table(
        lpool(rolls(5, 8), secret),
        lpool(1, [it(M + "prism", 3, 1, 1), it(M + "heartstone", 3, 1, 2), it(M + "attuned_lumenite", 3, 1, 2),
                  it(M + "lumenite_crystal", 3, 1, 2), it("skyrpg_combat:valor_seal", 3, 1, 1),
                  it("skyrpg_combat:elite_trophy", 3, 1, 2)]),
        lpool(1, [it("skyrpg_combat:nightmare_core", 1, 1, 1)], .09),
        lpool(1, [it("skyrpg_pets:golden_pet_egg", 1, 1, 1)], .012)))
    vault = [it("minecraft:diamond", 8, 4, 8), it("minecraft:emerald", 8, 8, 16), it("minecraft:gold_ingot", 8, 8, 18),
             it("minecraft:experience_bottle", 6, 6, 12), it("skyrpg_enchants:runic_dust", 10, 6, 14),
             it(M + "duskquartz_shard", 9, 8, 16), it(M + "emberite_ingot", 8, 3, 6), it(M + "skyiron_ingot", 7, 5, 10),
             it(M + "glimmer_crystal", 7, 3, 6), it(M + "prism", 4, 1, 2), it(M + "heartstone", 4, 1, 2),
             it(M + "attuned_lumenite", 3, 1, 2), it(W + "dwarven_cog", 8, 3, 6), it(W + "glimmer_geode", 6, 2, 3),
             it(W + "rift_ember", 7, 2, 4), it("skyrpg_combat:valor_seal", 4, 1, 2), it("skyrpg_combat:elite_trophy", 5, 1, 3),
             it("skyrpg_combat:dread_fang", 4, 1, 2), book(8)]
    pack.loot_table("chests/dwarven_vault", table(
        lpool(rolls(6, 8), vault),
        lpool(1, [it(W + "dwarven_cog", 1, 3, 5)]),
        lpool(1, [it(M + "duskquartz_shard", 1, 5, 9)]),
        lpool(1, [it("skyrpg_combat:nightmare_core", 1, 1, 1)], .14),
        lpool(1, [it("skyrpg_pets:golden_pet_egg", 1, 1, 1)], .02),
        lpool(1, [it("minecraft:enchanted_golden_apple", 1, 1, 1)], .15)))
    pack.loot_table("chests/dwarven_cart", table(
        lpool(rolls(2, 4), [it(M + "skyiron_ingot", 5, 1, 3), it("minecraft:coal", 8, 3, 8), it(M + "glimmer_shard", 6, 2, 5),
                            it("minecraft:iron_nugget", 8, 6, 14), it("minecraft:rail", 7, 6, 14),
                            it("minecraft:powered_rail", 3, 2, 5), it(M + "duskquartz_shard", 3, 1, 3), it("minecraft:torch", 5, 4, 10)])))
    pack.loot_table("bosses/dwarven_champion", table(
        lpool(1, [it(W + "thane_key", 1)]),
        lpool(1, [it(W + "dwarven_cog", 1, 2, 4)]),
        lpool(rolls(2, 4), [it("minecraft:diamond", 4, 1, 3), it("skyrpg_enchants:runic_dust", 9, 4, 9),
                            it(M + "duskquartz_shard", 8, 4, 9), it(M + "skyiron_ingot", 7, 3, 7), it(M + "emberite_chunk", 5, 2, 5),
                            it(M + "glimmer_crystal", 5, 1, 3), it(W + "glimmer_geode", 4, 1, 2), it("minecraft:experience_bottle", 6, 4, 9),
                            book(4)]),
        lpool(1, [it(W + "rift_ember", 1, 1, 2)], .45),
        lpool(1, [it(M + "emberite_ingot", 1, 1, 3)], .35),
        lpool(1, [it("skyrpg_combat:elite_trophy", 1, 1, 1)], .35),
        lpool(1, [it("skyrpg_combat:valor_mark", 1, 1, 2)], .30),
        lpool(1, [it("skyrpg_combat:dread_fang", 1, 1, 1)], .15),
        lpool(1, [it("skyrpg_combat:nightmare_core", 1, 1, 1)], .07),
        lpool(1, [it("skyrpg_pets:owl_egg", 1, 1, 1), it("skyrpg_pets:bee_egg", 1, 1, 1)], .04)))
    pack.loot_table("bosses/iron_thane", table(
        lpool(1, [it(W + "thanes_rune", 1, 1, 1)]),                  # the trophy: always one ...
        lpool(1, [it(W + "thanes_rune", 1, 1, 1)], .25),             # ... and a quarter of the time a second
        lpool(1, [it(W + "thane_key", 1)]),
        lpool(1, [it(W + "dwarven_cog", 1, 4, 8)]),
        lpool(1, [it(M + "duskquartz_shard", 1, 10, 20)]),
        lpool(1, [it(M + "emberite_ingot", 1, 4, 8)]),
        lpool(rolls(5, 7), [it("minecraft:diamond", 6, 3, 7), it("minecraft:emerald", 8, 8, 18), it("skyrpg_enchants:runic_dust", 10, 8, 16),
                            it(M + "skyiron_ingot", 8, 6, 14), it(M + "glimmer_crystal", 6, 3, 6), it(M + "prism_fragment", 5, 2, 5),
                            it(W + "glimmer_geode", 6, 2, 4), it(W + "rift_ember", 6, 2, 5), it("minecraft:experience_bottle", 7, 8, 16),
                            book(5)]),
        lpool(1, [it("skyrpg_combat:valor_seal", 1, 1, 2)], .50),
        lpool(1, [it("skyrpg_combat:elite_trophy", 1, 1, 2)], .60),
        lpool(1, [it("skyrpg_combat:nightmare_core", 1, 1, 1)], .25),
        lpool(1, [it("skyrpg_pets:golden_pet_egg", 1, 1, 1)], .03)))
    pack.loot_table("rewards/iron_thane", table(
        lpool(rolls(5, 7), [it("skyrpg_enchants:runic_dust", 10, 8, 16), it("minecraft:diamond", 7, 4, 9), it("minecraft:emerald", 8, 8, 18),
                            it("minecraft:experience_bottle", 7, 8, 16), it(M + "duskquartz_shard", 9, 8, 16),
                            it(M + "emberite_ingot", 8, 3, 7), it(M + "skyiron_ingot", 7, 5, 10), it(M + "glimmer_crystal", 6, 3, 6),
                            it(M + "prism", 4, 1, 2), it(M + "heartstone", 4, 1, 2), it(M + "attuned_lumenite", 4, 1, 3),
                            it(W + "dwarven_cog", 9, 3, 6), it(W + "glimmer_geode", 6, 2, 3), it(W + "rift_ember", 7, 2, 4),
                            it("skyrpg_combat:valor_seal", 5, 1, 2), it("skyrpg_combat:elite_trophy", 5, 1, 3), book(6)]),
        lpool(1, [it("skyrpg_combat:nightmare_core", 1, 1, 1)], .20),
        lpool(1, [it("skyrpg_combat:dread_fang", 1, 1, 2)], .35),
        lpool(1, [it("skyrpg_pets:golden_pet_egg", 1, 1, 1)], .04),
        lpool(1, [it("skyrpg_pets:owl_egg", 1, 1, 1), it("skyrpg_pets:bee_egg", 1, 1, 1)], .10)))


def tome_drops(pack):
    def drops(tag, entries):
        pack.json(f"skyrpg/tome_drops/{tag}.json", {
            "tag": f"skyrpg_boss=skyrpg_world:{tag}",
            "drops": [{"enchant": f"skyrpg_enchants:{e}", "level": lv, "chance": ch, "min_tier": 0} for e, lv, ch in entries]})
    drops("iron_thane", [("forgefire", 1, 0.12), ("rich_vein", 3, 0.25), ("rich_vein", 4, 0.08), ("veinseeker", 3, 0.15),
                         ("gemcutter", 3, 0.12), ("steadfast", 3, 0.15), ("titanbane", 3, 0.08)])
    drops("dwarven_champion", [("rich_vein", 2, 0.12), ("veinseeker", 2, 0.08), ("steadfast", 2, 0.08), ("forgefire", 1, 0.04)])


def bosses(pack):
    pack.json("skyrpg/dungeon_bosses/dwarven_champion.json", {
        "name": "The Forgeguard", "entity": "skyrpg_combat:dwarven_construct", "dungeon": DUN, "tier": "champion", "level": 38,
        "health": 6500, "damage": 21, "speed": 0.27, "scale": 1.7, "knockback_resistance": 0.8, "bar_color": "yellow",
        "health_per_player": 0.6, "coins": [3800, 5000], "delving_xp": 1300,
        "intro": "The Forgeguard grinds awake. Its rune-core flares: no trespasser passes the Thane's forge.",
        "death": "The Forgeguard crashes down in a spray of cogs. The portcullis grinds open.",
        "max_minions": 4, "loot": "skyrpg_world:bosses/dwarven_champion", "reset_ticks": 72000,
        "first_clear": {"coins": 4500, "xp": {"skyrpg_skills:combat": 2200}},
        "moves": [
            {"type": "slam", "every": 230, "radius": 5.5, "damage": 18, "knockup": 0.9, "windup": 24,
             "warn": "The Forgeguard raises its hammer-fist!"},
            {"type": "shockwave", "every": 330, "radius": 8, "damage": 14, "push": 1.6, "particle": "minecraft:flame",
             "warn": "Its rune-core pulses, get clear!"},
            {"type": "summon", "every": 520, "max": 4,
             "mobs": [{"type": "skyrpg_combat:dwarven_construct", "count": 2, "level": 36, "name": "Dwarven Construct"}],
             "warn": "Smaller constructs clank out of the walls!"},
            {"type": "ward", "every": 900, "below": 0.5, "duration": 100, "reduction": 0.75,
             "warn": "Brass plates slide over the Forgeguard's core!"}]})
    pack.json("skyrpg/dungeon_bosses/iron_thane.json", {
        "name": "The Iron Thane", "entity": "skyrpg_combat:iron_thane", "dungeon": DUN, "level": 42, "health": 24000, "damage": 28,
        "speed": 0.27, "scale": 1.0, "knockback_resistance": 1.0, "bar_color": "red", "health_per_player": 0.6,
        "coins": [16000, 22000], "delving_xp": 7000,
        "intro": "You walk the Thane's halls with living feet. Kneel, or be forged anew in my fire.",
        "death": "The Iron Thane topples, and the Deep Forge goes dark. The old crown rolls to your feet.",
        "max_minions": 8, "loot": "skyrpg_world:bosses/iron_thane", "reward_loot": "skyrpg_world:rewards/iron_thane",
        "first_clear": {"coins": 30000, "xp": {"skyrpg_skills:combat": 9000}},
        "reset_ticks": 72000,
        "phases": [{"below": 0.66, "message": "The Thane stamps his hammer: Forgeguard, to me! The Deep Forge answers!"},
                   {"below": 0.33, "message": "The Thane's crown ignites. The forge floor runs molten, and he fights without mercy!"}],
        "moves": [
            {"type": "slam", "every": 210, "radius": 7, "damage": 24, "knockup": 1.0, "windup": 24,
             "warn": "The Iron Thane raises his forge hammer!"},
            {"type": "leap", "every": 300, "damage": 22, "radius": 4, "range": 20,
             "warn": "The Thane vaults across the forge!"},
            {"type": "shockwave", "every": 340, "radius": 10, "damage": 18, "push": 1.8, "particle": "minecraft:flame",
             "warn": "Embers burst from the anvil, back away!"},
            {"type": "ward", "every": 900, "duration": 120, "reduction": 0.8,
             "warn": "Rune-plate slides over the Thane's chest!"},
            {"type": "summon", "every": 470, "below": 0.66, "max": 6,
             "mobs": [{"type": "skyrpg_combat:dwarven_construct", "count": 2, "level": 40, "name": "Forgeguard"},
                      {"type": "minecraft:zombie", "count": 2, "level": 38, "name": "Lost Miner",
                       "equipment": {"mainhand": "minecraft:iron_pickaxe", "head": "minecraft:iron_helmet"}}],
             "warn": "The Thane calls his Forgeguard!"},
            {"type": "pull", "every": 380, "below": 0.66, "radius": 14, "strength": 1.1, "particle": "minecraft:smoke",
             "warn": "The Thane drags you to his anvil!"},
            {"type": "heal", "every": 520, "below": 0.5, "percent": 0.03,
             "warn": "The forge-fire mends the Thane!"},
            {"type": "molten", "every": 240, "below": 0.33, "count": 12, "radius": 4.5, "delay": 25, "duration": 120, "damage": 22,
             "warn": "The floor glows red-hot. Molten metal is welling up, move!"},
            {"type": "volley", "every": 190, "below": 0.33, "projectile": "fireball", "count": 5, "damage": 12, "spread": 0.2,
             "warn": "The Thane hurls slag!"},
            {"type": "slam", "every": 170, "below": 0.33, "radius": 8, "damage": 26, "knockup": 1.1, "windup": 20,
             "warn": "The forge floor erupts under the hammer!"},
            {"type": "enrage", "below": 0.33, "speed": 0.36, "damage": 0.4,
             "warn": "The Iron Thane's crown ignites!"}]})


# ---------------------------------------------------------------------------------------------------------------------
# The structure
# ---------------------------------------------------------------------------------------------------------------------
def build(pack):
    P = lambda s: f"{DH}/{s}"  # noqa: E731
    build_entrance(pack)
    for fl in (1, 2):
        caps = {k: P(f"caps_{k}{fl}") for k in ("spine", "side", "tail")}
        for kind in ("spine", "side", "tail"):
            cap(pack, kind, fl)
            pack.pool(caps[kind], [(P(f"caps/{kind}{fl}"), 1)])
        nxt = {1: P(f"f{fl}_s2"), 2: P(f"f{fl}_s3"), 3: P("champion") if fl == 1 else P("boss")}
        for stage, specs in SPINE[fl].items():
            for kind, tag, w in specs:
                HALL_FNS[kind](pack, fl, tag, w, nxt[stage], SPINE_SIDES[fl])
            pack.pool(P(f"f{fl}_s{stage}"), [(P(f"f{fl}/{kind}_{tag}"), 3) for kind, tag, _ in specs], fallback=caps["spine"])
        rooms = []
        for fn, wgt in ROOMS[fl]:
            piece = fn(pack, fl)
            rooms.append((piece.path, wgt))
        pack.pool(P(f"f{fl}_rooms"), rooms, fallback=caps["side"])
        leaves = [(fn(pack, fl).path, 1) for fn in LEAVES]
        pack.pool(P(f"f{fl}_leaves"), leaves, fallback=caps["tail"])
    build_champion(pack)
    build_descent(pack)
    build_deep_forge(pack)
    pack.pool(P("entrance"), [(P("entrance"), 1)])
    pack.pool(P("stairwell"), [(P("stairwell"), 1)])
    pack.pool(P("champion"), [(P("f1/champion"), 1)], fallback=P("caps_spine1"))
    pack.pool(P("descent"), [(P("descent"), 1)], fallback=P("caps_spine1"))
    pack.pool(P("boss"), [(P("boss/deep_forge"), 1)], fallback=P("caps_spine2"))
    pack.biome_tag("dwarven_halls", ["#minecraft:is_mountain", "#minecraft:is_hill", "#minecraft:is_forest",
                                     "minecraft:windswept_hills", "minecraft:windswept_gravelly_hills", "minecraft:windswept_forest",
                                     "minecraft:stony_peaks"])
    pack.structure("dwarven_halls", start_pool=P("entrance"), biomes="#skyrpg_world:has_structure/dwarven_halls",
                   size=16, max_distance=116, step="underground_structures", adaptation="beard_thin",
                   heightmap="WORLD_SURFACE_WG", start_height=0)
    pack.structure_set("dwarven_halls", 40, 14, 61201715, exclusion=("spider_hollow", 8))
    pack.structure_tag("on_dwarven_maps", ["dwarven_halls"])
    loot(pack)
    tome_drops(pack)
    pack.json("skyrpg/dungeons/dwarven_halls.json", {
        "key": "skyrpg_world:thane_key", "name": "The Dwarven Halls", "structure": DUN, "color": "#D08A3C",
        "icon": "minecraft:anvil", "level": 34, "boss": "skyrpg_world:iron_thane", "reset_ticks": 72000,
        "description": "A ruined dwarven city under the mountains, reached by a long stairwell. Forge halls, vaults and the Iron Thane."})
    bosses(pack)


# ---------------------------------------------------------------------------------------------------------------------
# Offline checks
# ---------------------------------------------------------------------------------------------------------------------
CLIENT_JAR = pathlib.Path.home() / ".gradle/caches/fabric-loom/26.3/minecraft-client.jar"
MOD_BLOCKS = {"skyrpg_world:dwarven_runestone", "skyrpg_world:waystone", "skyrpg_mining:duskquartz_ore", "skyrpg_mining:deepslate_duskquartz_ore",
              "skyrpg_mining:skyiron_ore", "skyrpg_mining:deepslate_skyiron_ore", "skyrpg_mining:emberite_ore",
              "skyrpg_mining:deepslate_emberite_ore"}


def vanilla_ids(kind):
    if not CLIENT_JAR.exists():
        return None
    prefix = {"block": "assets/minecraft/blockstates/", "item": "assets/minecraft/items/"}[kind]
    with zipfile.ZipFile(CLIENT_JAR) as z:
        return {"minecraft:" + n[len(prefix):-5] for n in z.namelist() if n.startswith(prefix) and n.endswith(".json")}


def check_blocks(pack):
    blocks = vanilla_ids("block")
    if blocks is None:
        print("(client jar not found: skipping the block id check)")
        return 0
    bad = Counter()
    for pc in pack.pieces.values():
        for (entry, data) in pc.cells.values():
            i = entry["id"]
            if i.startswith("minecraft:") and i not in blocks and i not in ("minecraft:jigsaw", "minecraft:chest", "minecraft:barrel"):
                bad[(pc.path, i)] += 1
            elif not i.startswith("minecraft:") and i not in MOD_BLOCKS:
                bad[(pc.path, i)] += 1
    for (path, i), n in sorted(bad.items()):
        print(f"UNKNOWN BLOCK {i} x{n} in {path}")
    return len(bad)


def check_items(pack):
    items = vanilla_ids("item")
    if items is None:
        return 0
    bad = set()

    def walk(node):
        if isinstance(node, dict):
            if node.get("type") == "minecraft:item" and str(node.get("name", "")).startswith("minecraft:") and node["name"] not in items:
                bad.add(node["name"])
            for v in node.values():
                walk(v)
        elif isinstance(node, list):
            for v in node:
                walk(v)
    for path, data in pack.files.items():
        if "loot_table" in path:
            walk(data)
    for i in sorted(bad):
        print("UNKNOWN ITEM", i)
    return len(bad)


PASS_SUFFIX = ("_carpet", "_sign", "_button", "_plate", "rail", "_torch", "_lantern", "_bed")
PASS_IDS = {AIR, "minecraft:cobweb", "minecraft:lever", "minecraft:iron_chain", "minecraft:torch", "minecraft:soul_torch",
            "minecraft:pointed_dripstone", "minecraft:skeleton_skull", "minecraft:lantern", "minecraft:soul_lantern", "minecraft:ladder",
            "minecraft:vine", "minecraft:amethyst_cluster", "minecraft:redstone_wire", "minecraft:wall_torch"}


def passable(i):
    return i in PASS_IDS or i.endswith(PASS_SUFFIX)


UNSET_AIR_FROM = {}      # piece path -> first local y from which unset cells count as open air (the surface gate)


def check_pieces(pack):
    """Every piece, except that the gate and the stairwell are checked as one stacked piece (they join in a vertical jigsaw)."""
    out = [pc for pc in pack.pieces.values() if not pc.path.endswith(("/entrance", "/stairwell"))]
    gate, stair = pack.pieces[f"{DH}/entrance"], pack.pieces[f"{DH}/stairwell"]
    m = Piece(f"{DH}/entrance+stairwell", gate.size[0], GATE_H + STAIR_H, gate.size[2])
    m.cells.update(stair.cells)
    for (x, y, z), v in gate.cells.items():
        m.cells[(x, y + STAIR_H, z)] = v
    m.entities = list(stair.entities)
    for e in gate.entities:
        bx, by, bz = e["blockPos"]
        m.entities.append({"pos": e["pos"], "blockPos": [bx, by + STAIR_H, bz], "nbt": e["nbt"]})
    m.jigsaws = list(stair.jigsaws)
    UNSET_AIR_FROM[m.path] = STAIR_H + 1
    return out + [m]


def walk_check(pack):
    """Every doorway and every marker (bosses, spawners, chests, plates, levers, traps' targets) must be reachable from the
    first doorway. 'door'/'key_door' markers count as open; hidden caches (secret chests) are exempt."""
    problems = []
    for pc in check_pieces(pack):
        if pc.path.startswith(f"{DH}/caps/"):
            continue
        cells = pc.cells
        w, h, d = pc.size
        opened = set()
        for e in pc.entities:
            data = e["nbt"].get("data", {})
            if data.get("skyrpg") in ("door", "key_door"):
                bx, by, bz = e["blockPos"]
                block = pc.get(bx, by, bz)
                stack, seen = [(bx, by, bz)], {(bx, by, bz)}
                while stack and len(seen) <= 64:
                    cx, cy, cz = stack.pop()
                    for dx, dy, dz in ((1, 0, 0), (-1, 0, 0), (0, 1, 0), (0, -1, 0), (0, 0, 1), (0, 0, -1)):
                        n = (cx + dx, cy + dy, cz + dz)
                        if n not in seen and pc.get(*n) == block:
                            seen.add(n)
                            stack.append(n)
                opened |= seen

        def blk(x, y, z):
            if (x, y, z) in opened:
                return AIR
            c = cells.get((x, y, z))
            if c is None:
                return AIR if y >= UNSET_AIR_FROM.get(pc.path, 10 ** 9) else None
            return c[0]["id"]

        def free(x, y, z):
            if not pc.inside(x, y, z):
                return False
            b = blk(x, y, z)
            return b is not None and passable(b)

        def ground(x, y, z):
            b = blk(x, y, z)
            return b is not None and not passable(b)

        def stand(x, y, z):
            return free(x, y, z) and free(x, y + 1, z) and ground(x, y - 1, z)

        starts = []
        for (pos, orientation, name, target, pool) in pc.jigsaws:
            if orientation.split("_")[0] in ("up", "down"):
                continue
            x, y, z = pos
            dxz = {"north": (0, 1), "south": (0, -1), "west": (1, 0), "east": (-1, 0)}[orientation.split("_")[0]]
            starts.append((x + dxz[0], y, z + dxz[1], pos))
        if not starts:
            continue
        sx, sy, sz, _ = starts[0]
        if not stand(sx, sy, sz):
            problems.append(f"{pc.path}: the first doorway {starts[0][3]} has no floor to stand on")
            continue
        seen, stack = {(sx, sy, sz)}, [(sx, sy, sz)]
        while stack:
            x, y, z = stack.pop()
            for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                for dy in (1, 0, -1, -2, -3):
                    n = (x + dx, y + dy, z + dz)
                    if n in seen or not stand(*n):
                        continue
                    if dy == 1 and not free(x, y + 2, z):
                        continue
                    seen.add(n)
                    stack.append(n)
        for (x, y, z, pos) in starts[1:]:
            if (x, y, z) not in seen:
                problems.append(f"{pc.path}: doorway {pos} is not reachable from the first one")
        exempt = EXEMPT.get(pc.path, ())
        for e in pc.entities:
            data = e["nbt"].get("data", {})
            kind = data.get("skyrpg")
            if kind not in ("boss", "spawner", "chest", "plate", "lever", "secret", "dungeon"):
                continue
            bx, by, bz = e["blockPos"]
            if "secret" in exempt and (kind == "secret" or data.get("tier") == "secret"):
                continue
            if kind == "lever":
                ok = any((bx + dx, by + dy, bz + dz) in seen for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1)) for dy in (0, -1))
            elif kind == "chest":
                ok = any((bx + dx, by, bz + dz) in seen for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1)))
            else:
                ok = any((bx, by + dy, bz) in seen for dy in (0, -1, 1))
            if not ok:
                problems.append(f"{pc.path}: {kind} marker at {(bx, by, bz)} is unreachable")
            if kind == "chest" and "secret" not in exempt or kind == "chest" and data.get("tier") != "secret":
                above = blk(bx, by + 1, bz)
                if above is None or not passable(above):
                    problems.append(f"{pc.path}: chest at {(bx, by, bz)} has a block on top ({above})")
    return problems


def check_packs(pack):
    problems = []
    for pc in pack.pieces.values():
        for e in pc.entities:
            d = e["nbt"].get("data", {})
            mobs = d.get("mobs") or []
            for wv in ([mobs] if mobs else []) + list(d.get("waves") or []):
                n = sum(m["count"] for m in wv)
                if not 4 <= n <= 10:
                    problems.append(f"{pc.path}: pack of {n}")
    return problems


def stage_sizes(pack, pool_name):
    sizes = []
    for e in pack.pools[f"skyrpg_world:{DH}/{pool_name}"]["elements"]:
        if e["element"]["element_type"] != "minecraft:single_pool_element":
            continue
        sizes.append(pack.pieces[e["element"]["location"].split(":", 1)[1]].size)
    return sizes


def reach_table(pack):
    """Worst-case distances from the entrance centre along the spine axes (jigsaw max_distance is 116)."""
    ent = pack.pieces[f"{DH}/entrance"].size
    a = ent[0] - 12                    # centre -> exit door plane
    print("reach table (worst case straight chain, blocks from the entrance centre; limit 116 per axis):")
    print(f"  entrance exit door plane             A = {a:5.1f}   (B = +8, door at z=20)")
    for st in ("f1_s1", "f1_s2", "f1_s3", "champion", "descent"):
        dep = max(s[2] for s in stage_sizes(pack, st))
        a += dep
        print(f"  after {st:9s} (max depth {dep:2d})        A = {a:5.1f}")
    dw, dh, dd = pack.pieces[f"{DH}/descent"].size
    a_axis = a - dd + (dd - 1 - 15)    # floor 2's spine axis A coordinate: the exit door sits at z=15 of the descent
    print(f"  floor 2 runs along B at A = {a_axis:5.1f}")
    b = 8 + (dw - 1 - 15) + 1          # B of the descent's east wall (door at x=15 -> 18)
    for st in ("f2_s1", "f2_s2", "f2_s3", "boss"):
        dep = max(s[2] for s in stage_sizes(pack, st))
        b += dep
        wid = max(s[0] for s in stage_sizes(pack, st))
        print(f"  after {st:6s} (depth {dep:2d}, widest {wid:2d})   B = {b:5.1f}   A extent to {a_axis + wid / 2:5.1f}")
    print(f"  worst: A {max(a, a_axis + 15.5):5.1f}  B {b:5.1f}")


def expected_pieces(pack, trials=400):
    """Monte-Carlo of the jigsaw tree (no collisions): how many pieces per floor, side rooms and tails included."""
    rng = random.Random(7)
    pools = pack.pools

    def pick(pool_id):
        pool = pools[pool_id]
        els = [e for e in pool["elements"] if e["element"]["element_type"] == "minecraft:single_pool_element"]
        wts = [e["weight"] for e in els]
        return rng.choices(els, wts)[0]["element"]["location"].split(":", 1)[1], pool["fallback"]

    totals = {"surface": 0, 1: 0, 2: 0}
    caps = {"surface": 0, 1: 0, 2: 0}

    def floor_of(path, parent):
        if path.endswith("/entrance") or path.endswith("/stairwell"):
            return "surface"
        if "/f1/" in path or path.endswith("/descent"):
            return 1
        if "/f2/" in path or "/boss/" in path:
            return 2
        return parent

    for _ in range(trials):
        queue = [(f"{DH}/entrance", 0, "surface")]
        while queue:
            path, depth, floor = queue.pop()
            if "/caps/" in path:
                caps[floor] += 1
            else:
                totals[floor] += 1
            for (pos, orientation, name, target, pool) in pack.pieces[path].jigsaws:
                if pool == "minecraft:empty":
                    continue
                if depth + 1 >= 16:
                    child = pools[pools[pool]["fallback"]]["elements"][0]["element"]["location"].split(":", 1)[1]
                else:
                    child, _fb = pick(pool)
                queue.append((child, depth + 1, floor_of(child, floor)))
    return {k: v / trials for k, v in totals.items()}, {k: v / trials for k, v in caps.items()}


def report(pack):
    kinds = Counter()
    for pc in pack.pieces.values():
        for e in pc.entities:
            if "data" in e["nbt"]:
                kinds[e["nbt"]["data"]["skyrpg"]] += 1
    print(f"pieces ({len(pack.pieces)}):")
    for path, pc in sorted(pack.pieces.items()):
        c = Counter(e["nbt"]["data"]["skyrpg"] for e in pc.entities if "data" in e["nbt"])
        print(f"  {path:40s} {pc.size[0]:2d}x{pc.size[1]:2d}x{pc.size[2]:2d}  " + " ".join(f"{k}={v}" for k, v in sorted(c.items())))
    print("marker counts:", dict(kinds))


def show(pack, path, y):
    pc = pack.pieces[f"{DH}/{path}"]
    w, h, d = pc.size
    sym = {AIR: " ", "minecraft:lava": "L", "minecraft:glass": "g", RUNE: "R", "minecraft:iron_bars": "#"}
    for z in range(d):
        row = ""
        for x in range(w):
            c = pc.cells.get((x, y, z))
            if c is None:
                row += "?"
            else:
                i = c[0]["id"]
                row += sym.get(i, "." if passable(i) else ("c" if "chest" in i else "X"))
        print(f"{z:2d} {row}")


if __name__ == "__main__":
    pack = Pack()
    build(pack)
    if len(sys.argv) > 3 and sys.argv[1] == "--show":
        show(pack, sys.argv[2], int(sys.argv[3]))
        sys.exit(0)
    problems = walk_check(pack) + check_packs(pack)
    n_bad = check_blocks(pack) + check_items(pack)
    for pr in problems:
        print("PROBLEM:", pr)
    report(pack)
    reach_table(pack)
    avg, capavg = expected_pieces(pack)
    print("expected pieces per floor (Monte-Carlo, collisions ignored), without caps:", {k: round(v, 1) for k, v in avg.items()},
          "caps:", {k: round(v, 1) for k, v in capavg.items()})
    if problems or n_bad:
        raise SystemExit("offline checks failed")
    pack.save(clean=["dwarven_halls"])
