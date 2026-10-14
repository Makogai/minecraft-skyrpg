"""The Infernal Citadel (skyrpg_world:infernal_citadel): a two-floor Nether fortress dungeon on the lava sea and in the cliffs.
Mob levels 44-52, champion Lv 50 (the Infernal Knight), boss Lv 52 (the Magma Tyrant). Built like the other v2 dungeons
(see docs/design/dungeons.md and docs/design/nether.md).

Layout (jigsaw, size 16, max_distance 116). In the Nether there is no surface, so the start piece is placed at an absolute
height (start_height 62: its local y=1 sits at y=62) with no heightmap projection and no terrain adaptation. Every piece
has its walkable floor layer at local y = FD (4) and doorways 3x3 from y = FD + 1; under the floor are pilings (stilts) that
stand on the lava sea or merge into a cliff. Doorways are always at the same height, so the floors line up.

  FLOOR 1  "The Burning Bastion" (Lv 44-48), along axis A from the entrance:
           gatehouse (the start piece: a forecourt on the lava with a portcullis gate and two towers, a gate hall with
           lava moats) -> f1_s1 -> f1_s2 -> f1_s3 (spine halls, 17 deep: banner halls, barracks, war forges, bridges over
           lava, prison blocks, a chapel of flame) -> the Knight's Hall (champion, bar plug) -> descent (switchback stairs
           carved in the cliff, 20 down; exits on its EAST wall, a 90 degree turn).
  FLOOR 2  "The Molten Throne" (Lv 48-52), perpendicular to floor 1, 20 below:
           f2_s1 -> f2_s2 -> f2_s3 (bridges, magma foundries, obsidian colonnades, ember chapels, imp warrens)
           -> the Molten Throne (35 x 23 x 35, boss, a key-door vault and a hoard nook).
  Side rooms hang off every spine hall (floor 1: both sides; floor 2: only the side that faces back towards the entrance,
  the far side only gets leaves, to stay inside the 116 block box). Everything falls back to 1-thick caps.

Doors: entering doors are named cit_spine_in / cit_side_in / cit_tail_in; outgoing ones target the matching _in.
Spine doors have placement_priority 10, side doors 0.

Run:  python tools/builds/dungeons/infernal_citadel.py     (add --show <piece path> <y> to print a slice of a piece)
Offline checks (all run on every build): jigsaw validation, every block id exists, every door and marker is reachable
(walk test with door markers opened, lava is not floor), pack sizes, loot item ids, spine reach table.
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

CZ = "infernal_citadel"
DUN = "skyrpg_world:infernal_citadel"
AIR = "minecraft:air"
LAVA = "minecraft:lava"
FD = 4            # floor layer in every piece (pilings below)
G = FD + 1        # first walkable air layer
START_HEIGHT = 62

PBB, PB, CPBB, CHPB = "minecraft:polished_blackstone_bricks", "minecraft:polished_blackstone", \
    "minecraft:cracked_polished_blackstone_bricks", "minecraft:chiseled_polished_blackstone"
BS, GILD = "minecraft:blackstone", "minecraft:gilded_blackstone"
NB, RNB, CNB, CHNB = "minecraft:nether_bricks", "minecraft:red_nether_bricks", "minecraft:cracked_nether_bricks", "minecraft:chiseled_nether_bricks"
MAGMA = "minecraft:magma_block"
CHAIN = "minecraft:iron_chain"
BARS = "minecraft:iron_bars"
WALL_PBB = "minecraft:polished_blackstone_brick_wall"
SOUL_FIRE = "minecraft:soul_campfire[facing=north,lit=true]"


class Pal:
    def __init__(self, wall, floor, inlay, trim, cap, weather, light, accent, carpet):
        self.wall, self.floor, self.inlay, self.trim, self.cap, self.weather, self.light = wall, floor, inlay, trim, cap, weather, light
        self.accent, self.carpet = accent, carpet


PAL = {
    1: Pal(PBB, PB, NB, BS, CHPB,
           {PBB: [(CPBB, .14), (BS, .05), (NB, .02)], PB: [(BS, .12)], NB: [(CNB, .16)], BS: [(PB, .05)]},
           "minecraft:soul_lantern", "minecraft:crimson_planks", "minecraft:red_carpet"),
    2: Pal(RNB, PBB, CHNB, PBB, CHPB,
           {RNB: [(NB, .10), (CNB, .08), (BS, .05), (MAGMA, .015)], PBB: [(CPBB, .12), (BS, .06)], CHNB: [(CNB, .1)]},
           "minecraft:lantern", "minecraft:warped_planks", "minecraft:black_carpet"),
}


# ---------------------------------------------------------------------------------------------------------------------
# Mobs. Floor 1: Lv 44-48, champion 50, floor 2: Lv 48-52, boss 52.
# ---------------------------------------------------------------------------------------------------------------------
def L(fl, d=0):
    return (44 if fl == 1 else 48) + d


def mob(type_, count, level, name, elite=False, **equipment):
    m = {"type": type_, "count": count, "level": level, "elite": elite, "name": name}
    if equipment:
        m["equipment"] = dict(equipment)
    return m


def guard(n, lvl, name="Citadel Guard", elite=False):
    return mob("minecraft:wither_skeleton", n, lvl, name, elite, mainhand="minecraft:stone_sword", head="minecraft:iron_helmet")


def brute(n, lvl, name="Citadel Brute", elite=False):
    return mob("minecraft:piglin_brute", n, lvl, name, elite, mainhand="minecraft:golden_axe")


def blaze(n, lvl, name="Furnace Blaze"):
    return mob("minecraft:blaze", n, lvl, name)


def imp(n, lvl, name="Ember Imp", elite=False):
    return mob("skyrpg_combat:ember_imp", n, lvl, name, elite)


def cube(n, lvl, name="Magma Cube"):
    return mob("minecraft:magma_cube", n, lvl, name)


def total(mobs):
    return sum(m["count"] for m in mobs)


# ---------------------------------------------------------------------------------------------------------------------
# Room: a built room with shell, doors, decor and markers. y=FD is the floor layer, G the first air layer.
# ---------------------------------------------------------------------------------------------------------------------
SIDE_POOL = {1: f"{CZ}/f1_rooms", 2: f"{CZ}/f2_rooms"}
TAIL_POOL = {1: f"{CZ}/f1_leaves", 2: f"{CZ}/f2_leaves"}
DOORS = {"spine_in": ("cit_spine_in", "cit_spine_in", None, 0),
         "spine_out": ("cit_spine_out", "cit_spine_in", "pool", 10),
         "side_in": ("cit_side_in", "cit_side_in", None, 0),
         "side_out": ("cit_side_out", "cit_side_in", "side", 0),
         "tail_in": ("cit_tail_in", "cit_tail_in", None, 0),
         "leaf_out": ("cit_leaf_out", "cit_tail_in", "tail", 0),
         "tail_out": ("cit_tail_out", "cit_tail_in", "tail", 0)}
EXEMPT = {}      # piece path -> kinds of markers allowed to be unreachable (hidden caches)


class Room:
    def __init__(self, path, w, h, d, fl=1):
        self.p = Piece(f"{CZ}/{path}", w, h, d)
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

    def free(self, x, z, y=G):
        return (x, z) not in self.keep and self.p.inside(x, y, z) and self.get(x, y, z) == AIR

    def shell(self, x1=0, z1=0, x2=None, z2=None, top=None, pilings=True):
        """Walls, floor (with an inlay border), pilasters, a trim band under the roof and pilings under the floor."""
        p, pal = self.p, self.pal
        x2 = self.w - 1 if x2 is None else x2
        z2 = self.d - 1 if z2 is None else z2
        top = self.h - 1 if top is None else top
        p.fill(x1, FD, z1, x2, top, z2, pal.wall)
        p.fill(x1 + 1, G, z1 + 1, x2 - 1, top - 1, z2 - 1, AIR)
        for x in range(x1, x2 + 1):
            for z in range(z1, z2 + 1):
                inlay = x in (x1 + 1, x2 - 1) and z1 + 1 <= z <= z2 - 1 or z in (z1 + 1, z2 - 1) and x1 + 1 <= x <= x2 - 1
                p.set(x, FD, z, pal.inlay if inlay else pal.floor)
        for z in range(z1 + 4, z2 - 2, 5):             # pilasters, flush with the wall
            for x in (x1, x2):
                for y in range(G, top):
                    p.set(x, y, z, pal.trim)
                p.set(x, G, z, pal.cap)
                p.set(x, top - 1, z, pal.cap)
        for x in range(x1, x2 + 1):                    # a trim band round the top of the walls
            for z in (z1, z2):
                p.set(x, top - 2, z, pal.trim)
        for z in range(z1, z2 + 1):
            for x in (x1, x2):
                p.set(x, top - 2, z, pal.trim)
        if pilings:
            self.pilings(x1, z1, x2, z2)

    def pilings(self, x1, z1, x2, z2):
        """Stilts: 3x3 columns under the corners and the middle of the long sides (they stand in the lava sea or merge with rock)."""
        zs = (z1, (z1 + z2) // 2 - 1, z2 - 2)
        for xa in (x1, x2 - 2):
            for za in zs:
                for x in range(xa, xa + 3):
                    for z in range(za, za + 3):
                        for y in range(0, FD):
                            self.p.set(x, y, z, BS if (x + y + z) % 5 else PBB)
        cx = (x1 + x2) // 2
        for za in (z1, z2 - 2):
            for x in range(cx - 1, cx + 2):
                for z in range(za, za + 3):
                    for y in range(0, FD):
                        self.p.set(x, y, z, BS)

    def ribs(self, every=6, start=3):
        """Ceiling ribs across the room (one block lower than the ceiling)."""
        for z in range(start, self.d - 2, every):
            for x in range(1, self.w - 1):
                if self.get(x, self.h - 2, z) == AIR:
                    self.set(x, self.h - 2, z, self.pal.trim)

    def door(self, side, kind, pool=None, at=None, y=G):
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
        if y == G:
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
        self.p.weather("a", {k: list(v) for k, v in self.pal.weather.items()})

    def finish(self, pack, exempt=None):
        self.weather()
        autoconnect(self.p)
        pack.add(self.p)
        if exempt:
            EXEMPT[self.p.path] = exempt
        return self.p

    # -- decor ---------------------------------------------------------------------------------------------------
    def col(self, x, z, y1=None):
        top = (self.h - 3) if y1 is None else y1
        for y in range(G, top + 1):
            self.set(x, y, z, self.pal.trim)
        self.set(x, G, z, self.pal.cap)
        self.set(x, top, z, self.pal.cap)
        if top >= G + 5:
            self.set(x, G + 3, z, GILD)
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
        self.set(x, G, z, WALL_PBB)
        self.set(x, G + 1, z, SOUL_FIRE)
        self.keep.add((x, z))

    def banner(self, x, y, z, facing, color="red"):
        """A wall banner at (x, y, z), hanging on the wall behind it (opposite of `facing`)."""
        back = {"east": (-1, 0), "west": (1, 0), "south": (0, -1), "north": (0, 1)}[facing]
        if self.get(x, y, z) == AIR and self.get(x + back[0], y, z + back[1]) not in (None, AIR):
            self.set(x, y, z, f"minecraft:{color}_wall_banner[facing={facing}]")

    def banners(self, zs, y=None, color="red"):
        y = G + 4 if y is None else y
        for z in zs:
            self.banner(1, y, z, "east", color)
            self.banner(self.w - 2, y, z, "west", color)

    def chest(self, x, z, facing, tier="common", y=G, block="minecraft:chest"):
        loot = f"skyrpg_world:chests/citadel_{tier}"
        self.p.chest(x, y, z, facing, loot, block=block, tier=tier)
        self.keep.add((x, z))

    def spawn(self, x, z, *waves, radius=9, spread=4, group=None, y=G):
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
        self.p.marker(x, y, z, "trap", type="arrows", radius=radius, every=every, damage=damage or (9 if self.fl == 1 else 11))

    def flames(self, cx, cz, r=1, damage=None):
        """Floor vents: magma blocks in the floor and a flame burst when somebody walks near."""
        for x in range(cx - r, cx + r + 1):
            for z in range(cz - r, cz + r + 1):
                if self.get(x, FD, z) not in (None, AIR, LAVA) and (x + z) % 2 == (cx + cz) % 2:
                    self.set(x, FD, z, MAGMA)
        self.p.marker(cx, G, cz, "trap", type="flames", radius=r + 2, every=70, damage=damage or (7 if self.fl == 1 else 9))

    def lava_fall(self, x1, z1, x2, z2, front, top=None):
        """A sealed column of lava from floor to ceiling (interior x1..x2, z1..z2) behind iron bars on the `front` side.
        Lava can't flow into bars, and every other side is stone, so it stays put."""
        p = self.p
        top = self.h - 3 if top is None else top
        wall = self.pal.wall
        for x in range(x1 - 1, x2 + 2):
            for z in range(z1 - 1, z2 + 2):
                inside = x1 <= x <= x2 and z1 <= z <= z2
                p.set(x, FD - 1, z, wall)
                p.set(x, top + 1, z, wall)
                if inside:
                    for y in range(FD, top + 1):
                        p.set(x, y, z, LAVA)
                    continue
                on_front = ((front == "east" and x == x2 + 1 and z1 <= z <= z2) or (front == "west" and x == x1 - 1 and z1 <= z <= z2) or
                            (front == "south" and z == z2 + 1 and x1 <= x <= x2) or (front == "north" and z == z1 - 1 and x1 <= x <= x2))
                for y in range(G, top + 1):
                    p.set(x, y, z, BARS if on_front else wall)
                self.keep.add((x, z))
            for z in range(z1, z2 + 1):
                self.keep.add((x, z))

    def pit(self, x1, z1, x2, z2, decks=()):
        """A sunken lava pit (interior x1..x2, z1..z2) whose surface lies one below the floor. `decks`: (x1, z1, x2, z2) boxes
        that stay floor, on piers (bridges)."""
        p = self.p
        for x in range(x1 - 1, x2 + 2):
            for z in range(z1 - 1, z2 + 2):
                if x1 <= x <= x2 and z1 <= z <= z2:
                    p.set(x, 0, z, self.pal.wall)
                    for y in range(1, FD):
                        p.set(x, y, z, LAVA)
                    p.set(x, FD, z, AIR)
                else:
                    for y in range(0, FD):
                        p.set(x, y, z, self.pal.wall)
        for (dx1, dz1, dx2, dz2) in decks:
            for x in range(dx1, dx2 + 1):
                for z in range(dz1, dz2 + 1):
                    for y in range(1, FD):
                        p.set(x, y, z, self.pal.wall if (x + z) % 3 else BS)
                    p.set(x, FD, z, self.pal.floor if (x + z) % 2 else self.pal.inlay)

    def anvil(self, x, z, facing="east"):
        if self.free(x, z):
            kind = self.rng.choice(["anvil", "anvil", "chipped_anvil", "damaged_anvil"])
            self.set(x, G, z, f"minecraft:{kind}[facing={facing}]")
            self.keep.add((x, z))

    def furnace(self, x, z, facing, kind="blast_furnace"):
        if self.free(x, z):
            self.set(x, G, z, f"minecraft:{kind}[facing={facing},lit=true]")
            self.keep.add((x, z))

    def rubble(self, n, x1, z1, x2, z2):
        blocks = [BS, CPBB, "minecraft:blackstone_slab[type=bottom]", "minecraft:netherrack", "minecraft:blackstone_stairs[facing=north]", CNB]
        for _ in range(n):
            x, z = self.rng.randint(x1, x2), self.rng.randint(z1, z2)
            if self.free(x, z):
                self.set(x, G, z, self.rng.choice(blocks))

    def sign(self, x, y, z, lines, facing):
        self.p.sign(x, y, z, lines, block=f"minecraft:crimson_wall_sign[facing={facing}]", color="white", glow=True)


# ---------------------------------------------------------------------------------------------------------------------
# Connecting blocks (bars, panes, walls, fences) get their connection states from their neighbours
# ---------------------------------------------------------------------------------------------------------------------
THIN = ("iron_bars", "glass_pane")
NONSOLID = {AIR, LAVA, "minecraft:lantern", "minecraft:soul_lantern", "minecraft:torch", "minecraft:wall_torch", "minecraft:soul_torch",
            "minecraft:soul_campfire", "minecraft:campfire", "minecraft:cobweb", "minecraft:iron_chain", "minecraft:ladder",
            "minecraft:stone_pressure_plate", "minecraft:polished_blackstone_pressure_plate", "minecraft:lever"}


def _is_thin(i):
    return i.split(":")[-1] in THIN or i.endswith("_bars")


def _is_wall(i):
    return i.endswith("_wall")


def _solid(i):
    return i is not None and i not in NONSOLID and not i.endswith(("_carpet", "_sign", "_button", "_plate", "_bed", "_banner")) \
        and not i.endswith(("_slab", "_stairs", "lantern"))


def autoconnect(p):
    DIRS = {"north": (0, -1), "south": (0, 1), "west": (-1, 0), "east": (1, 0)}
    updates = {}
    for (x, y, z), (entry, data) in p.cells.items():
        i = entry["id"]
        if not (_is_thin(i) or _is_wall(i) or i.endswith("_fence")):
            continue
        props = dict(entry.get("properties", {}))
        for name, (dx, dz) in DIRS.items():
            n = p.cells.get((x + dx, y, z + dz))
            ni = n[0]["id"] if n else None
            if _is_thin(i):
                props[name] = "true" if (ni and (_is_thin(ni) or _solid(ni))) else "false"
            elif _is_wall(i):
                props[name] = "low" if ni and (_is_wall(ni) or _solid(ni)) else "none"
            else:
                props[name] = "true" if ni and (ni == i or _solid(ni)) else "false"
        if _is_wall(i):
            props["up"] = "true"
        updates[(x, y, z)] = {"id": i, "properties": dict(sorted(props.items()))}
    for pos, entry in updates.items():
        p.cells[pos] = (entry, p.cells[pos][1])


# ---------------------------------------------------------------------------------------------------------------------
# Spine halls (17 deep; door in on the south wall, out on the north wall, side doors in the middle of the long walls)
# ---------------------------------------------------------------------------------------------------------------------
HALL_D = 17
F_H = {1: FD + 12, 2: FD + 13}


def spine(r, out_pool, sides):
    r.door("south", "spine_in")
    r.door("north", "spine_out", pool=out_pool)
    for s in sides:
        # floor 2 runs perpendicular to floor 1: its "west" side faces away from the entrance, where only small leaves fit
        r.door(s, "leaf_out" if (r.fl == 2 and s == "west") else "side_out")
    r.lane("x")
    if sides:
        r.reserve(0, r.d // 2 - 1, r.w - 1, r.d // 2 + 1)


def new_hall(pack, fl, kind, tag, w, out_pool, sides):
    r = Room(f"f{fl}/{kind}_{tag}", w, F_H[fl], HALL_D, fl)
    r.shell()
    r.ribs(6, 3)
    spine(r, out_pool, sides)
    return r


def carpet_runner(r, block=None):
    cx = r.w // 2
    block = block or r.pal.carpet
    for z in range(1, r.d - 1):
        for x in (cx - 1, cx, cx + 1):
            if r.get(x, G, z) == AIR:
                r.set(x, G, z, block)


def hall_muster(pack, fl, tag, w, out_pool, sides):
    """The muster hall: rows of gilded pillars, a carpet runner, banners and braziers."""
    r = new_hall(pack, fl, "muster", tag, w, out_pool, sides)
    cx = w // 2
    for x in [cx - 5, cx + 5] + ([cx - 9, cx + 9] if w >= 25 else []):
        for z in (3, 6, 10, 13):
            r.col(x, z)
    carpet_runner(r)
    r.banners((2, 6, 10, 14), color="red" if fl == 1 else "black")
    for x in (cx - 3, cx + 3):
        for z in (2, r.d - 3):
            r.brazier(x, z)
    for z in (3, 8, 13):
        r.hang(cx - 3, z)
        r.hang(cx + 3, z)
    r.chest(2, 2, "south", "common" if fl == 1 else "rare")
    r.chest(w - 3, r.d - 3, "north", "common")
    r.rubble(6, 2, 2, w - 3, r.d - 3)
    r.arrows(0, G + 2, 5, "east")
    r.arrows(w - 1, G + 2, 12, "west")
    r.flames(cx - 7, 11, 1)
    r.flames(cx + 7, 5, 1)
    waves = ([[guard(4, L(fl, 0)), brute(2, L(fl, 1))], [blaze(3, L(fl, 1)), guard(3, L(fl, 2))]] if fl == 1 else
             [[brute(3, L(fl, 1)), imp(3, L(fl, 1))], [guard(3, L(fl, 3)), blaze(3, L(fl, 2)), imp(2, L(fl, 2))]])
    r.spawn(cx, 8, *waves, radius=10, spread=5)
    return r.finish(pack)


def hall_barracks(pack, fl, tag, w, out_pool, sides):
    """Bunks along both walls (floor 2: an imp warren), two long tables with benches, barrels and a smoker."""
    r = new_hall(pack, fl, "barracks" if fl == 1 else "warren", tag, w, out_pool, sides)
    cx = w // 2
    color = "red" if fl == 1 else "black"
    for z in (3, 5, 11, 13):
        r.set(1, G, z, f"minecraft:{color}_bed[facing=west,part=head]")
        r.set(2, G, z, f"minecraft:{color}_bed[facing=west,part=foot]")
        r.set(w - 2, G, z, f"minecraft:{color}_bed[facing=east,part=head]")
        r.set(w - 3, G, z, f"minecraft:{color}_bed[facing=east,part=foot]")
        r.reserve(1, z, 2, z)
        r.reserve(w - 3, z, w - 2, z)
    for z in (4, 12):
        for x in (3, w - 4):
            r.set(x, G, z, "minecraft:barrel[facing=up]")
            r.keep.add((x, z))
    pw = r.pal.accent
    for x0 in (cx - 6, cx + 4):
        for x in range(x0, x0 + 3):
            r.set(x, G, 7, f"minecraft:{pw.split(':')[1][:-7]}_stairs[facing=south]")
            r.set(x, G, 8, pw)
            r.set(x, G, 9, f"minecraft:{pw.split(':')[1][:-7]}_stairs[facing=north]")
            for z in (7, 8, 9):
                r.keep.add((x, z))
    if fl == 2:
        for x, z in ((4, 8), (w - 5, 8), (cx - 3, 3), (cx + 3, 13)):
            if r.free(x, z):
                r.set(x, G, z, "minecraft:soul_sand")
                r.set(x, G + 1, z, "minecraft:soul_fire")
                r.keep.add((x, z))
    r.furnace(w - 3, 7, "west", "smoker")
    r.chest(2, r.d - 3, "north", "common")
    r.chest(w - 3, 2, "south", "common" if fl == 1 else "rare")
    for z in (3, 8, 13):
        r.hang(cx - 4, z)
        r.hang(cx + 4, z)
    r.banners((8,), y=G + 3, color="red" if fl == 1 else "black")
    r.rubble(4, 4, 2, w - 5, r.d - 3)
    r.arrows(w - 1, G + 2, 4, "west")
    r.arrows(0, G + 2, 12, "east")
    waves = ([[guard(5, L(fl, 0)), brute(2, L(fl, 1))], [brute(3, L(fl, 2), "Citadel Captain", elite=True), guard(2, L(fl, 2))]] if fl == 1 else
             [[imp(4, L(fl, 1)), brute(3, L(fl, 1))], [imp(3, L(fl, 3)), guard(2, L(fl, 3), "Citadel Captain", elite=True), cube(3, L(fl, 2))]])
    r.spawn(cx, 8, *waves, radius=10, spread=5)
    return r.finish(pack)


def hall_forge(pack, fl, tag, w, out_pool, sides):
    """A war forge: lava falls behind bars along both walls, anvils and blast furnaces, flame vents."""
    r = new_hall(pack, fl, "forge", tag, w, out_pool, sides)
    cx = w // 2
    for z1, z2 in ((2, 5), (11, 14)):
        r.lava_fall(1, z1, 3, z2, "east")
        r.lava_fall(w - 4, z1, w - 2, z2, "west")
    for x, side in ((cx - 4, "west"), (cx + 4, "east")):
        for z in (3, 13):
            r.anvil(x, z, "north")
            r.furnace(x + (-1 if side == "west" else 1), z, "east" if side == "west" else "west")
    for x in (cx - 3, cx + 3):
        for z in (8,):
            r.set(x, G, z, "minecraft:smithing_table")
            r.keep.add((x, z))
    r.flames(cx, 5, 1)
    r.flames(cx, 12, 1)
    for z in (3, 8, 13):
        r.hang(cx - 2, z)
        r.hang(cx + 2, z)
    for x in (cx - 6, cx + 6):
        r.col(x, 8)
    r.chest(cx - 5, 1, "south", "common" if fl == 1 else "rare")
    r.chest(cx + 5, r.d - 2, "north", "common")
    r.rubble(3, 5, 2, w - 6, r.d - 3)
    waves = ([[guard(3, L(fl, 1)), blaze(4, L(fl, 1))], [brute(3, L(fl, 3)), blaze(3, L(fl, 2)), guard(2, L(fl, 2))]] if fl == 1 else
             [[blaze(4, L(fl, 2)), cube(3, L(fl, 1))], [brute(2, L(fl, 4), "Forge Warlord", elite=True), blaze(3, L(fl, 3)), imp(3, L(fl, 3))]])
    r.spawn(cx, 8, *waves, radius=10, spread=4)
    return r.finish(pack)


def hall_bridge(pack, fl, tag, w, out_pool, sides):
    """A bridge hall: a causeway on piers over a sunken lava sea, chain-hung lanterns, cross bridges to the side doors."""
    r = new_hall(pack, fl, "bridge", tag, w, out_pool, sides)
    cx, zc = w // 2, r.d // 2
    decks = [(cx - 2, 0, cx + 2, r.d - 1)]
    if sides:
        decks += [(1, zc - 1, cx - 3, zc + 1), (cx + 3, zc - 1, w - 2, zc + 1)]
    r.pit(1, 1, w - 2, r.d - 2, decks)
    r.fill(0, 1, 0, w - 1, FD - 1, 0, r.pal.wall)       # keep the lava off the doorway planes
    r.fill(0, 1, r.d - 1, w - 1, FD - 1, r.d - 1, r.pal.wall)
    fence = "minecraft:nether_brick_fence"
    for x in (cx - 2, cx + 2):
        for z in range(1, r.d - 1):
            if sides and zc - 1 <= z <= zc + 1:
                continue
            r.set(x, G, z, fence)
        for z in (2, 6, 10, 14):
            if r.get(x, G, z) == fence:
                r.set(x, G + 1, z, r.pal.light + "[hanging=false]")
    r.keep |= {(x, z) for x in range(1, w - 1) for z in range(1, r.d - 1) if not (cx - 1 <= x <= cx + 1)}
    for x in (cx - 6, cx + 6):
        for z in (4, 12):
            r.hang(x, z, 3)
    r.chest(cx + 2, 12, "west", "common" if fl == 1 else "rare")
    r.flames(cx, 4, 1)
    r.spawn(cx, 8, *([[blaze(5, L(fl, 1)), guard(3, L(fl, 1))], [blaze(4, L(fl, 2)), brute(2, L(fl, 2))]] if fl == 1 else
                     [[blaze(5, L(fl, 2)), cube(3, L(fl, 1))], [blaze(4, L(fl, 3)), imp(3, L(fl, 2))]]), radius=10, spread=2)
    return r.finish(pack)


def hall_chapel(pack, fl, tag, w, out_pool, sides):
    """The chapel of flame: pews either side of the aisle, flame altars, lava falls in the walls, crimson banners."""
    r = new_hall(pack, fl, "chapel", tag, w, out_pool, sides)
    cx = w // 2
    for side in (-1, 1):
        for z in (3, 5, 11, 13):
            for dx in (4, 5, 6):
                x = cx + side * dx
                r.set(x, G, z, f"minecraft:crimson_stairs[facing={'south' if z < 8 else 'north'},half=bottom]")
                r.keep.add((x, z))
    for x in (cx - 3, cx + 3):
        for z in (4, 12):
            r.set(x, G, z, GILD)
            r.set(x, G + 1, z, SOUL_FIRE)
            r.keep.add((x, z))
    r.lava_fall(1, 2, 2, 4, "east")
    r.lava_fall(w - 3, 2, w - 2, 4, "west")
    r.lava_fall(1, 12, 2, 14, "east")
    r.lava_fall(w - 3, 12, w - 2, 14, "west")
    carpet_runner(r, "minecraft:red_carpet" if fl == 1 else "minecraft:black_carpet")
    for x in (cx - 8, cx + 8):
        if 2 <= x <= w - 3:
            r.col(x, 8, y1=G + 6)
    r.banners((2, 8, 14), y=G + 5, color="crimson" if False else "red")
    for z in (3, 8, 13):
        r.hang(cx - 2, z)
        r.hang(cx + 2, z)
    r.chest(2, 8, "east", "rare") if r.free(2, 8) else r.chest(3, 7, "east", "rare")
    r.chest(w - 3, 6, "west", "common")
    r.flames(cx, 8, 1)
    waves = ([[guard(4, L(fl, 1)), blaze(3, L(fl, 2))], [guard(3, L(fl, 3), "Flame Acolyte"), brute(2, L(fl, 3), "Citadel Captain", elite=True), blaze(2, L(fl, 3))]] if fl == 1 else
             [[imp(4, L(fl, 2)), blaze(3, L(fl, 2))], [imp(3, L(fl, 3)), guard(2, L(fl, 3), "Flame Acolyte"), blaze(3, L(fl, 3))]])
    r.spawn(cx, 8, *waves, radius=10, spread=4)
    return r.finish(pack)


def hall_prison(pack, fl, tag, w, out_pool, sides):
    """A prison block: iron-barred cells along both walls with a skull and a chest each, an aisle of guards between."""
    r = new_hall(pack, fl, "prison", tag, w, out_pool, sides)
    cx = w // 2
    for z1, z2 in ((2, 4), (12, 14)):
        for side in (-1, 1):
            xs = (1, 3) if side == -1 else (w - 4, w - 2)
            front = 4 if side == -1 else w - 5
            for z in range(z1 - 1, z2 + 2):
                for y in range(G, F_H[fl] - 3):
                    if z in (z1 - 1, z2 + 1):
                        for x in range(min(xs[0], front), max(xs[1], front) + 1):
                            r.set(x, y, z, r.pal.wall)
            for z in range(z1, z2 + 1):
                for y in range(G, G + 4):
                    if z != (z1 + z2) // 2:
                        r.set(front, y, z, BARS)
                    r.keep.add((front, z))
            for x in range(xs[0], xs[1] + 1):
                for z in range(z1, z2 + 1):
                    r.keep.add((x, z))
            mid = (z1 + z2) // 2
            sx = xs[0] if side == -1 else xs[1]
            r.set(sx, G, mid - 1, "minecraft:skeleton_skull[rotation=4]")
            r.chest(sx, mid + 1, "east" if side == -1 else "west", "rare" if (z1 == 2) == (side == -1) else "common")
    for z in (3, 8, 13):
        r.hang(cx - 2, z)
        r.hang(cx + 2, z)
    for x, z in ((cx - 3, 7), (cx + 3, 9)):
        r.brazier(x, z)
    r.sign(cx - 3, G + 2, 1, ["PRISONERS OF", "THE CITADEL", "no bail"], "south")
    r.arrows(0 if False else w - 1, G + 2, 8, "west") if False else None
    r.flames(cx, 8, 1)
    waves = ([[guard(5, L(fl, 1)), brute(3, L(fl, 1), "Citadel Jailer")], [guard(3, L(fl, 3)), blaze(3, L(fl, 2)), brute(2, L(fl, 3), "Citadel Jailer", elite=True)]] if fl == 1 else
             [[guard(4, L(fl, 2)), imp(4, L(fl, 2))], [brute(3, L(fl, 3), "Citadel Jailer"), guard(3, L(fl, 3))]])
    r.spawn(cx, 8, *waves, radius=10, spread=4)
    return r.finish(pack)


def hall_colonnade(pack, fl, tag, w, out_pool, sides):
    """An obsidian colonnade (floor 2): crying obsidian pillars, lava falls and soul fire along the aisle."""
    r = new_hall(pack, fl, "colonnade", tag, w, out_pool, sides)
    cx = w // 2
    for x in [cx - 5, cx + 5] + ([cx - 9, cx + 9] if w >= 25 else []):
        for z in (3, 6, 10, 13):
            for y in range(G, r.h - 2):
                r.set(x, y, z, "minecraft:obsidian")
            r.set(x, G + 2, z, "minecraft:crying_obsidian")
            r.set(x, r.h - 3, z, "minecraft:crying_obsidian")
            r.keep.add((x, z))
    r.lava_fall(1, 2, 2, 5, "east")
    r.lava_fall(w - 3, 2, w - 2, 5, "west")
    r.lava_fall(1, 11, 2, 14, "east")
    r.lava_fall(w - 3, 11, w - 2, 14, "west")
    for x in (cx - 3, cx + 3):
        for z in (2, 14):
            r.brazier(x, z)
    carpet_runner(r)
    for z in (3, 8, 13):
        r.hang(cx - 3, z, 3)
        r.hang(cx + 3, z, 3)
    r.chest(cx - 6, 15, "north", "rare")
    r.chest(cx + 6, 15, "north", "rare")
    r.arrows(0, G + 2, 6, "east")
    r.arrows(w - 1, G + 2, 11, "west")
    r.flames(cx, 8, 1)
    r.spawn(cx, 8, [imp(4, L(fl, 2)), cube(3, L(fl, 1)), blaze(2, L(fl, 2))], [brute(3, L(fl, 3)), imp(3, L(fl, 3)), blaze(3, L(fl, 3))],
            radius=10, spread=5)
    return r.finish(pack)


HALL_FNS = {"muster": hall_muster, "barracks": hall_barracks, "forge": hall_forge, "bridge": hall_bridge, "chapel": hall_chapel,
            "prison": hall_prison, "colonnade": hall_colonnade}
# floor -> stage -> [(kind, tag, width)]
SPINE = {
    1: {1: [("muster", "a", 27), ("barracks", "a", 23), ("bridge", "a", 25)],
        2: [("forge", "a", 25), ("prison", "a", 23), ("bridge", "b", 23)],
        3: [("chapel", "a", 25), ("forge", "b", 23), ("muster", "b", 25)]},
    2: {1: [("bridge", "a", 25), ("forge", "a", 25), ("colonnade", "a", 23)],
        2: [("colonnade", "b", 27), ("barracks", "a", 23), ("bridge", "b", 23)],
        3: [("forge", "b", 25), ("chapel", "a", 23), ("colonnade", "c", 25)]},
}
SPINE_SIDES = {1: ("west", "east"), 2: ("east", "west")}


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
            for y in (G, G + 1, G + 2):
                p.set(x, y, z, wall if edge else AIR)
    r.chest(chest_at[0], chest_at[1], facing, "secret")
    p.marker((x1 + x2) // 2, G, (z1 + z2) // 2, "secret", name=name)
    r.reserve(x1, z1, x2, z2)
    EXEMPT[p.path] = tuple(EXEMPT.get(p.path, ())) + ("secret",)


def room_armoury(pack, fl):
    """The armoury vault: weapon racks and a sealed inner door that sinks away when three pressure plates are held."""
    r = room(f"f{fl}/armoury", 15, FD + 9, 17, fl, tail=False)
    p = r.p
    for x in range(1, 14):
        for y in range(G, FD + 8):
            p.set(x, y, 5, r.pal.wall)
    for x in (6, 7, 8):
        for y in (G, G + 1, G + 2):
            p.set(x, y, 5, "minecraft:crying_obsidian")
    p.marker(7, G + 1, 5, "door", group="plates", message="The armoury door grinds into the floor.")
    for x, z in ((2, 12), (12, 12), (7, 8)):
        r.fill(x - 1, FD, z - 1, x + 1, FD, z + 1, r.pal.inlay)
        p.set(x, G, z, "minecraft:polished_blackstone_pressure_plate")
        p.marker(x, G, z, "plate", group="plates")
        r.keep.add((x, z))
    r.sign(5, G + 1, 6, ["Hold all three.", "The war-store", "opens for no one."], "south")
    for x in (2, 4, 10, 12):
        r.set(x, G, 1, "minecraft:grindstone[face=floor,facing=east]" if x % 4 == 0 else "minecraft:smithing_table")
        r.keep.add((x, 1))
    for z in (2, 3):
        r.set(1, G + 1, z, "minecraft:iron_bars")
        r.set(13, G + 1, z, "minecraft:iron_bars")
    r.chest(2, 3, "east", "vault", y=G) if r.free(2, 3) else None
    r.chest(12, 3, "west", "vault") if r.free(12, 3) else None
    r.chest(5, 2, "south", "rare")
    r.chest(9, 2, "south", "rare")
    r.set(7, G, 2, "minecraft:gold_block")
    p.marker(7, G, 3, "secret", name="The War-Store")
    r.keep |= {(7, 2), (7, 3)}
    for x in (5, 9):
        p.set(x, FD + 6, 3, r.pal.light + "[hanging=true]")
    r.arrows(0, G + 2, 10, "east")
    r.arrows(14, G + 2, 10, "west")
    r.spawn(7, 13, [guard(3, L(fl, 2)), brute(2, L(fl, 2)), blaze(2, L(fl, 2))], radius=8, spread=3)
    return r.finish(pack)


def room_prison_cells(pack, fl):
    r = room(f"f{fl}/cell_block", 17, FD + 9, 15, fl)
    for x0 in (2, 10):
        for z0 in (4, 8):
            for x in range(x0 - 1, x0 + 5):
                for z in range(z0 - 1, z0 + 3):
                    edge = x in (x0 - 1, x0 + 4) or z in (z0 - 1, z0 + 2)
                    if edge:
                        for y in (G, G + 1, G + 2):
                            r.set(x, y, z, BARS if (x, z) != (x0 - 1 if x0 == 10 else x0 + 4, z0) else AIR)
            r.set(x0 + 1, G, z0, "minecraft:skeleton_skull[rotation=2]")
            r.keep |= {(x, z) for x in range(x0 - 1, x0 + 5) for z in range(z0 - 1, z0 + 3)}
    r.chest(3, 10, "east", "rare") if r.free(3, 10) else None
    r.chest(14, 12, "west", "common")
    r.chest(2, 1, "south", "common")
    for z in (3, 9):
        r.hang(8, z)
    r.brazier(6, 2)
    r.brazier(10, 2)
    r.flames(8, 7, 1)
    r.spawn(8, 12, [guard(4, L(fl, 2)), brute(2, L(fl, 2), "Citadel Jailer")], [guard(3, L(fl, 3)), blaze(3, L(fl, 3))], radius=9, spread=3)
    return r.finish(pack)


def room_shrine(pack, fl):
    r = room(f"f{fl}/flame_shrine", 15, FD + 10, 15, fl)
    cx = cz = 7
    ring = [(round(cx + 3 * math.cos(math.radians(a))), round(cz + 3 * math.sin(math.radians(a)))) for a in range(0, 360, 45)]
    for x, z in ring:
        r.set(x, FD, z, GILD)
        r.keep.add((x, z))
    r.set(cx, G, cz, CHNB)
    r.set(cx, G + 1, cz, SOUL_FIRE)
    r.keep.add((cx, cz))
    for x, z in ((3, 3), (11, 3), (3, 11), (11, 11)):
        r.col(x, z, y1=G + 6)
    for x in (1, 13):
        for y in (G, G + 1, G + 2):
            r.set(x, y, 7, CHPB)
        r.set(x, G + 3, 7, "minecraft:wither_skeleton_skull[rotation=0]")
        r.keep.add((x, 7))
    r.banners((3, 11), y=G + 4)
    r.chest(2, 2, "south", "rare")
    r.chest(12, 12, "north", "rare")
    for z in (4, 10):
        r.hang(5, z)
        r.hang(9, z)
    r.flames(7, 11, 1)
    r.spawn(cx, cz + 4, [guard(3, L(fl, 2), "Flame Acolyte"), blaze(3, L(fl, 2))], [brute(3, L(fl, 3)), blaze(3, L(fl, 3))] if fl == 1 else
            [imp(3, L(fl, 3)), blaze(3, L(fl, 3))], radius=8, spread=3)
    return r.finish(pack)


def room_war_room(pack, fl):
    r = room(f"f{fl}/war_room", 17, FD + 9, 15, fl)
    acc = r.pal.accent
    for x in range(5, 12):
        for z in range(5, 9):
            r.set(x, G, z, acc)
            r.keep.add((x, z))
    for x in (4, 12):
        for z in (6, 8):
            r.set(x, G, z, "minecraft:crimson_stairs[facing=east,half=bottom]" if x == 4 else "minecraft:crimson_stairs[facing=west,half=bottom]")
            r.keep.add((x, z))
    r.set(8, G + 1, 6, "minecraft:red_banner[rotation=0]")
    r.set(1, G, 3, "minecraft:lectern[facing=east,has_book=false,powered=false]")
    r.set(15, G, 11, "minecraft:lectern[facing=west,has_book=false,powered=false]")
    r.keep |= {(1, 3), (15, 11)}
    r.banners((3, 7, 11), y=G + 4)
    cache(r, 1, 9, 4, 12, (2, 11), "east", CPBB, "Warlord's Strongbox")
    r.chest(14, 2, "south", "rare")
    r.chest(2, 13, "north", "common")
    for z in (4, 10):
        r.hang(4, z)
        r.hang(12, z)
    r.arrows(16, G + 2, 7, "west")
    r.spawn(8, 11, [guard(4, L(fl, 2)), brute(3, L(fl, 2))], [guard(3, L(fl, 3), "Citadel Captain", elite=True), brute(2, L(fl, 3)), blaze(2, L(fl, 3))],
            radius=9, spread=4)
    return r.finish(pack)


def room_mess(pack, fl):
    r = room(f"f{fl}/mess_hall", 17, FD + 8, 15, fl)
    acc = r.pal.accent
    for x0, bench_in in ((3, "east"), (12, "west")):
        for z in range(4, 11):
            for x in (x0, x0 + 1):
                r.set(x, G, z, acc)
                r.keep.add((x, z))
            bx = x0 - 1 if x0 == 3 else x0 + 2
            r.set(bx, G, z, f"minecraft:crimson_stairs[facing={bench_in},half=bottom]")
            r.keep.add((bx, z))
    for x, z in ((2, 2), (3, 2), (14, 2), (15, 2)):
        r.set(x, G, z, "minecraft:barrel[facing=up]")
        r.keep.add((x, z))
    r.furnace(1, 12, "east", "smoker")
    r.set(2, G, 12, "minecraft:cauldron")
    r.set(14, G, 12, "minecraft:cauldron")
    r.furnace(15, 12, "west", "smoker")
    r.keep |= {(1, 12), (2, 12), (14, 12), (15, 12)}
    r.chest(2, 13, "north", "common")
    r.chest(14, 13, "north", "common")
    for z in (4, 9):
        r.hang(3, z)
        r.hang(13, z)
    r.spawn(8, 8, [guard(5, L(fl, 0)), brute(3, L(fl, 1))], radius=10, spread=4)
    return r.finish(pack)


def room_guard(pack, fl):
    r = room(f"f{fl}/guard_post", 13, FD + 8, 13, fl)
    for x in (1, 11):
        for z in range(3, 10):
            r.set(x, G + 1, z, BARS)
    r.set(6, G, 6, GILD)
    r.set(6, G + 1, 6, SOUL_FIRE)
    r.keep.add((6, 6))
    for x, z in ((3, 3), (9, 3), (3, 9), (9, 9)):
        r.set(x, G, z, "minecraft:barrel[facing=up]")
        r.keep.add((x, z))
    r.chest(2, 2, "south", "rare" if fl == 2 else "common")
    r.chest(10, 11, "north", "common")
    r.hang(4, 6, 2)
    r.hang(8, 6, 2)
    r.arrows(0, G + 2, 6, "east", radius=9)
    r.arrows(12, G + 2, 6, "west", radius=9)
    r.spawn(6, 8, [guard(4, L(fl, 1)), brute(2, L(fl, 2)), blaze(2, L(fl, 1))] if fl == 1 else [guard(3, L(fl, 2)), imp(3, L(fl, 2)), brute(2, L(fl, 2))],
            radius=8, spread=3)
    return r.finish(pack)


def room_overlook(pack, fl):
    """An overlook: one wall is a huge lava fall behind bars, with a gilded balcony rail."""
    r = room(f"f{fl}/lava_overlook", 17, FD + 11, 15, fl, tail=False)
    r.lava_fall(4, 1, 12, 3, "south", top=FD + 8)
    for x in range(4, 13):
        r.set(x, G, 5, "minecraft:nether_brick_fence")
        r.keep.add((x, 5))
    r.set(4, G + 1, 5, r.pal.light + "[hanging=false]")
    r.set(12, G + 1, 5, r.pal.light + "[hanging=false]")
    for x, z in ((2, 7), (14, 7), (2, 12), (14, 12)):
        r.brazier(x, z)
    r.banners((7, 11), y=G + 4)
    r.chest(2, 3, "east", "rare")
    r.chest(14, 3, "west", "rare") if r.free(14, 3) else r.chest(15, 3, "west", "rare")
    r.flames(8, 9, 1)
    r.spawn(8, 8, [blaze(5, L(fl, 2)), guard(2, L(fl, 2))], [blaze(4, L(fl, 3)), brute(3, L(fl, 3))] if fl == 1 else
            [blaze(4, L(fl, 3)), cube(3, L(fl, 2))], radius=9, spread=4)
    return r.finish(pack)


def room_annex(pack, fl):
    r = room(f"f{fl}/forge_annex", 15, FD + 9, 17, fl)
    r.lava_fall(1, 3, 3, 7, "east")
    r.lava_fall(11, 3, 13, 7, "west")
    for x, side in ((5, "west"), (9, "east")):
        r.anvil(x, 11, "north")
        r.furnace(x + (-1 if side == "west" else 1), 11, "east" if side == "west" else "west")
    r.flames(7, 8, 1)
    r.chest(2, 14, "east", "rare")
    r.chest(12, 14, "west", "common")
    for z in (5, 11):
        r.hang(5, z)
        r.hang(9, z)
    r.spawn(7, 12, [guard(3, L(fl, 2)), blaze(3, L(fl, 2))], [brute(3, L(fl, 3)), blaze(3, L(fl, 3))] if fl == 1 else
            [cube(3, L(fl, 2)), imp(3, L(fl, 3))], radius=9, spread=3)
    return r.finish(pack)


def room_imp_den(pack, fl):
    r = room(f"f{fl}/imp_den", 17, FD + 8, 15, fl)
    for x, z in ((3, 4), (4, 4), (3, 5), (13, 4), (12, 4), (13, 5), (3, 10), (13, 10), (4, 11), (12, 11)):
        r.set(x, G, z, "minecraft:soul_sand")
        r.keep.add((x, z))
    for x, z in ((3, 4), (13, 10), (12, 4), (4, 11)):
        r.set(x, G + 1, z, "minecraft:soul_fire")
    for x, z in ((6, 3), (10, 12), (8, 6)):
        r.set(x, G, z, "minecraft:bone_block[axis=y]")
        r.set(x, G + 1, z, "minecraft:skeleton_skull[rotation=8]")
        r.keep.add((x, z))
    r.chest(2, 2, "south", "rare")
    r.chest(14, 12, "north", "rare")
    r.chest(2, 12, "north", "common")
    for z in (4, 10):
        r.hang(6, z)
        r.hang(10, z)
    r.flames(8, 8, 1)
    r.spawn(8, 8, [imp(5, L(fl, 2)), cube(3, L(fl, 1))], [imp(4, L(fl, 3)), imp(2, L(fl, 4), "Imp Matriarch", elite=True), cube(3, L(fl, 2))], radius=9, spread=4)
    return r.finish(pack)


def room_cistern(pack, fl):
    """A lava cistern: a pit with an island and a chest, reached over a narrow causeway."""
    r = room(f"f{fl}/magma_cistern", 17, FD + 9, 17, fl)
    r.pit(2, 4, 14, 14, [(7, 4, 9, 15), (7, 8, 12, 10)])
    r.fill(0, 1, 0, 16, FD - 1, 0, r.pal.wall)
    r.fill(0, 1, 16, 16, FD - 1, 16, r.pal.wall)
    for z in range(1, 4):
        for x in range(1, 16):
            if r.get(x, FD, z) in (None,) or True:
                pass
    r.set(12, G, 9, "minecraft:nether_brick_fence")
    for x, z in ((8, 4), (8, 14)):
        r.hang(x, z, 3)
    r.chest(11, 9, "west", "rare")
    r.chest(2, 2, "south", "common")
    r.chest(14, 2, "south", "common")
    for x, z in ((3, 2), (13, 2)):
        r.brazier(x, z)
    r.keep |= {(x, z) for x in range(1, 16) for z in range(4, 15) if not (7 <= x <= 9)}
    r.spawn(8, 2, [cube(4, L(fl, 2)), blaze(3, L(fl, 2))], [blaze(4, L(fl, 3)), imp(3, L(fl, 3))], radius=9, spread=3)
    return r.finish(pack)


def room_crypt(pack, fl):
    r = room(f"f{fl}/obsidian_crypt", 15, FD + 9, 15, fl)
    for x in (3, 11):
        for z in (4, 7, 10):
            for y in range(G, G + 4):
                r.set(x, y, z, "minecraft:obsidian")
            r.set(x, G + 4, z, "minecraft:crying_obsidian")
            r.keep.add((x, z))
    r.set(7, G, 11, CHNB)
    r.set(7, G + 1, 11, "minecraft:wither_skeleton_skull[rotation=8]")
    r.keep.add((7, 11))
    cache(r, 1, 1, 4, 4, (2, 2), "south", CPBB, "The Tyrant's Tomb")
    r.chest(12, 2, "south", "rare")
    r.chest(12, 12, "north", "rare")
    for z in (5, 9):
        r.hang(7, z)
    r.flames(7, 6, 1)
    r.spawn(7, 8, [guard(3, L(fl, 3), "Tomb Warden"), imp(3, L(fl, 2))], [brute(3, L(fl, 3)), imp(3, L(fl, 3))], radius=8, spread=3)
    return r.finish(pack)


def room_soul_well(pack, fl):
    r = room(f"f{fl}/soul_well", 15, FD + 10, 15, fl)
    for x in range(5, 10):
        for z in range(5, 10):
            r.set(x, G, z, "minecraft:soul_soil" if (x, z) != (7, 7) else "minecraft:soul_sand")
            r.keep.add((x, z))
    for x in range(5, 10):
        for z in range(5, 10):
            if (x in (5, 9)) or (z in (5, 9)):
                r.set(x, G + 1, z, "minecraft:soul_fire")
    for x, z in ((3, 3), (11, 3), (3, 11), (11, 11)):
        r.col(x, z, y1=G + 5)
    r.chest(2, 7, "east", "rare")
    r.chest(12, 7, "west", "rare")
    for z in (4, 10):
        r.hang(7, z, 2)
    r.arrows(0, G + 2, 11, "east")
    r.arrows(14, G + 2, 11, "west")
    r.spawn(7, 12, [guard(3, L(fl, 3), "Tomb Warden"), blaze(3, L(fl, 2))], [imp(3, L(fl, 3)), brute(3, L(fl, 3))], radius=8, spread=3)
    return r.finish(pack)


ROOMS = {
    1: [(room_armoury, 1), (room_prison_cells, 3), (room_shrine, 3), (room_war_room, 3), (room_mess, 3), (room_guard, 3),
        (room_overlook, 3), (room_annex, 3)],
    2: [(room_armoury, 1), (room_imp_den, 3), (room_cistern, 3), (room_crypt, 2), (room_war_room, 2), (room_shrine, 3),
        (room_annex, 3), (room_soul_well, 2), (room_overlook, 2)],
}


def leaf(path, w, d, fl):
    r = Room(path, w, FD + 6, d, fl)
    r.shell()
    r.door("south", "tail_in")
    r.lane("x")
    return r


def leaf_closet(pack, fl):
    r = leaf(f"f{fl}/leaf_closet", 9, 9, fl)
    r.set(2, G, 2, "minecraft:barrel[facing=up]")
    r.set(6, G, 2, "minecraft:barrel[facing=up]")
    r.keep |= {(2, 2), (6, 2)}
    r.chest(4, 1, "south", "common" if fl == 1 else "rare")
    r.hang(4, 4, 1)
    r.spawn(4, 5, [guard(4, L(fl, 1))], radius=7, spread=2)
    return r.finish(pack)


def leaf_nook(pack, fl):
    r = leaf(f"f{fl}/leaf_nook", 9, 11, fl)
    cache(r, 1, 1, 4, 4, (2, 2), "south", CPBB, "Smuggler's Nook")
    r.set(7, G, 2, "minecraft:barrel[facing=up]")
    r.keep.add((7, 2))
    r.hang(4, 6, 1)
    r.flames(6, 6, 1)
    r.spawn(4, 6, [blaze(3, L(fl, 1)), guard(2, L(fl, 1))], radius=7, spread=2)
    return r.finish(pack)


def leaf_guard(pack, fl):
    r = leaf(f"f{fl}/leaf_guard", 11, 9, fl)
    r.arrows(10, G + 2, 4, "west", radius=8)
    r.chest(2, 1, "south", "common" if fl == 1 else "rare")
    r.set(8, G, 2, "minecraft:anvil[facing=east]")
    r.keep.add((8, 2))
    r.hang(5, 4, 1)
    r.spawn(5, 4, [guard(3, L(fl, 2)), brute(2, L(fl, 2)), blaze(1, L(fl, 2))], radius=7, spread=2)
    return r.finish(pack)


def leaf_vent(pack, fl):
    r = leaf(f"f{fl}/leaf_vent", 9, 9, fl)
    r.lava_fall(1, 2, 2, 5, "east", top=FD + 4)
    r.flames(5, 4, 1)
    r.chest(7, 7, "north", "common")
    r.hang(4, 3, 1)
    return r.finish(pack)


LEAVES = [leaf_closet, leaf_nook, leaf_guard, leaf_vent]


def cap(pack, kind, fl):
    wall = PAL[fl].wall
    p = Piece(f"{CZ}/caps/{kind}{fl}", 3, FD + 4, 1)
    p.fill(0, 0, 0, 2, FD + 3, 0, wall)
    p.set(1, FD + 2, 0, PAL[fl].trim)
    p.jigsaw(1, G, 0, "north_up", f"cit_{kind}_in", f"cit_{kind}_in", "minecraft:empty", final_state=wall)
    pack.add(p)
    return p


# ---------------------------------------------------------------------------------------------------------------------
# The Gatehouse (the start piece, 37 x 32 x 29): a forecourt on stilts over the lava, a portcullis gate between two towers, and
# behind it the gate hall: a causeway between two lava moats, columns, banners. The spine leaves by the north wall.
# ---------------------------------------------------------------------------------------------------------------------
GW, GD, GH = 37, 29, FD + 28
GATE_Z = 20           # the gate wall's plane (hall's south wall); the facade is z 20..22


def build_gatehouse(pack):
    r = Room("entrance", GW, GH, GD, 1)
    p = r.p
    pal = r.pal
    cx = GW // 2
    roof = FD + 14
    r.shell(0, 0, GW - 1, GATE_Z, top=roof, pilings=True)
    r.ribs(7, 3)
    r.door("north", "spine_out", pool=f"{CZ}/f1_s1")
    # the moats and the causeway
    r.pit(5, 3, 14, 18, [(15, 0, 21, GATE_Z + 2)])
    r.pit(22, 3, 31, 18)
    p.fill(15, 1, 0, 21, FD - 1, 0, BS)
    for x in range(14, 23):
        for z in range(0, 3):
            pass
    # the causeway is 7 wide; pit() puts the deck on piers, so make the pit edge at x 14/22 solid pier too
    for z in range(2, 19):
        for x in (14, 22):
            for y in range(1, FD):
                p.set(x, y, z, pal.wall)
            p.set(x, FD, z, pal.floor)
    # re-seal the pit walls under the wall planes
    p.fill(0, 1, 0, GW - 1, FD - 1, 0, pal.wall)
    # columns along the causeway with gilded bands, banners hanging between them
    for x in (16, 20):
        for z in (4, 8, 12, 16):
            r.col(x, z, y1=roof - 2)
    r.keep |= {(x, z) for x in range(5, 32) for z in range(2, 19) if not 15 <= x <= 21}
    r.keep |= {(x, z) for x in (14, 22) for z in range(0, GATE_Z + 3)}
    for z in (6, 14):
        for x in (15, 21):
            r.banner(x, G + 6, z, "east" if x == 15 else "west", "red") if False else None
    # ledges either side: braziers, banners, weapon racks, chests, arrow slits
    for x in (2, 34):
        for z in (4, 9, 14):
            r.brazier(x, z)
    r.banners((4, 9, 14), y=G + 6)
    r.set(2, G, 17, "minecraft:grindstone[face=floor,facing=east]")
    r.set(34, G, 17, "minecraft:smithing_table")
    r.keep |= {(2, 17), (34, 17)}
    r.chest(2, 2, "south", "common")
    r.chest(34, 2, "south", "common")
    r.chest(3, 12, "east", "rare") if r.free(3, 12) else None
    r.arrows(0, G + 3, 7, "east", radius=12)
    r.arrows(GW - 1, G + 3, 13, "west", radius=12)
    r.arrows(0, G + 3, 16, "east", radius=12)
    r.flames(cx, 6, 1)
    r.flames(cx, 14, 1)
    for z in (4, 8, 12, 16):
        r.hang(cx - 3, z, 3)
        r.hang(cx + 3, z, 3)
    # lava falls in the north wall, beside the door
    r.lava_fall(6, 1, 9, 1, "south", top=roof - 3)
    r.lava_fall(27, 1, 30, 1, "south", top=roof - 3)
    # the gate: facade z 20..22, an arch 7 wide x 8 high, a raised portcullis, chains
    for x in range(9, 28):
        for y in range(G, FD + 17):
            for z in range(GATE_Z, GATE_Z + 3):
                p.set(x, y, z, pal.wall)
    for x in range(15, 22):
        for z in range(GATE_Z, GATE_Z + 3):
            for y in range(G, G + 8):
                p.set(x, y, z, AIR)
            p.set(x, G + 8, z, CHPB)
    for x in (14, 22):
        for y in range(G, G + 9):
            p.set(x, y, GATE_Z + 2, CHPB if y in (G + 3, G + 8) else GILD if y == G + 5 else PB)
    for x in range(15, 22):
        for y in (G + 6, G + 7):
            p.set(x, y, GATE_Z + 1, BARS)
    for x in (16, 18, 20):
        p.set(x, G + 5, GATE_Z + 1, CHAIN + "[axis=y]")
    for x in range(9, 28):                                           # crenellations
        if x % 2 == 0:
            p.set(x, FD + 17, GATE_Z + 2, pal.cap)
            p.set(x, FD + 17, GATE_Z, pal.cap)
    # the towers (solid, with arrow slits and fire basins on top)
    for x0 in (0, 28):
        for x in range(x0, x0 + 9):
            for z in range(GATE_Z, GATE_Z + 5):
                for y in range(FD, FD + 25):
                    p.set(x, y, z, pal.wall if (x, z) not in ((x0 + 4, GATE_Z + 4),) else pal.trim)
        for x in range(x0, x0 + 9):
            for z in range(GATE_Z, GATE_Z + 5):
                if x in (x0, x0 + 8) or z in (GATE_Z, GATE_Z + 4):
                    p.set(x, FD + 24, z, pal.cap if (x + z) % 2 == 0 else pal.wall)
                    p.set(x, FD + 25, z, WALL_PBB if (x + z) % 2 == 0 else AIR)
                else:
                    p.set(x, FD + 24, z, GILD)
        for y in (G + 8, G + 14):
            p.set(x0 + 4, y, GATE_Z + 4, BARS)
            p.set(x0 + 4, y + 1, GATE_Z + 4, BARS)
        p.set(x0 + 4, FD + 25, GATE_Z + 2, SOUL_FIRE)
        p.set(x0 + 4, FD + 24, GATE_Z + 2, GILD)
    # banners down the tower fronts and the facade (they hang on the south faces, z = 25 / 23)
    for x in (2, 6, 30, 34):
        for y in (G + 10, G + 16):
            p.set(x, y, GATE_Z + 5, "minecraft:red_wall_banner[facing=south]")
    for x in (11, 13, 23, 25):
        p.set(x, G + 9, GATE_Z + 3, "minecraft:black_wall_banner[facing=south]")
    # forecourt on stilts, in front of the gate (z 23..28), and the causeway that leaves the piece
    for x in range(9, 28):
        for z in range(GATE_Z + 3, GD):
            for y in range(G, G + 10):
                p.set(x, y, z, AIR)
            p.set(x, FD, z, pal.floor if (x + z) % 3 else pal.inlay)
    for x in range(15, 22):
        for z in range(GATE_Z + 3, GD):
            p.set(x, FD, z, GILD if (x + z) % 6 == 0 else PBB)
    r.pilings(9, GATE_Z + 3, 27, GD - 1)
    for x in range(9, 28):                                              # parapet round the forecourt
        for z in (GD - 1,):
            if not 15 <= x <= 21:
                p.set(x, G, z, WALL_PBB)
    for z in range(GATE_Z + 3, GD):
        for x in (9, 27):
            p.set(x, G, z, WALL_PBB)
    for x in (15, 21):
        for z in (GD - 3, GD - 1):
            p.set(x, G, z, WALL_PBB)
            p.set(x, G + 1, z, WALL_PBB)
            p.set(x, G + 2, z, SOUL_FIRE)
    p.set(11, G, GATE_Z + 6, "skyrpg_world:waystone")
    p.marker(cx, G, GATE_Z + 6, "dungeon", dungeon=DUN)
    p.sign(cx - 2, G + 1, GATE_Z + 3, ["THE INFERNAL", "CITADEL", "turn back"], block="minecraft:crimson_sign[rotation=8]", color="white", glow=True) if False else None
    r.hang(cx - 3, 10, 2)
    # the pack: waves in the gate hall
    r.spawn(cx, 10, [guard(4, L(1, 0)), brute(2, L(1, 0)), blaze(2, L(1, 0))], [blaze(4, L(1, 1)), guard(3, L(1, 1))], radius=11, spread=3)
    r.weather()
    autoconnect(p)
    pack.add(p)
    return p


# ---------------------------------------------------------------------------------------------------------------------
# Cliff stairwell (the descent): solid rock carved with stairs, lined in citadel brick. 20 down; enters on the south wall
# (x=15) at the top, leaves on the east wall (z=15) at the bottom.
# ---------------------------------------------------------------------------------------------------------------------
DROP = 20
ROCK = [(BS, 40), ("minecraft:basalt[axis=y]", 18), ("minecraft:smooth_basalt", 8), ("minecraft:netherrack", 14), (MAGMA, 3),
        ("minecraft:nether_gold_ore", 2), (GILD, 3), ("minecraft:polished_basalt[axis=y]", 6)]
ROCK_IDS = {b.split("[")[0] for b, _ in ROCK}
LINING = [(PBB, 50), (CPBB, 16), (NB, 12), (BS, 10), (PB, 8), (MAGMA, 3), (GILD, 1)]
OPP = {"north": "south", "south": "north", "west": "east", "east": "west"}
DIRV = {(1, 0): "east", (-1, 0): "west", (0, 1): "south", (0, -1): "north"}


def wchoice(rng, table):
    total_w = sum(wt for _, wt in table)
    x = rng.uniform(0, total_w)
    for b, wt in table:
        x -= wt
        if x <= 0:
            return b
    return table[-1][0]


class Shaft:
    def __init__(self, room):
        self.r = room
        self.p = room.p
        self.rng = room.rng
        self.air = set()

    def fill_rock(self):
        for x in range(self.r.w):
            for z in range(self.r.d):
                for y in range(self.r.h):
                    self.p.set(x, y, z, wchoice(self.rng, ROCK))

    def carve(self, x, y, z):
        self.p.set(x, y, z, AIR)
        self.air.add((x, y, z))

    def landing(self, x1, z1, x2, z2, y):
        for x in range(x1, x2 + 1):
            for z in range(z1, z2 + 1):
                self.p.set(x, y, z, PB if (x + z) % 3 else NB)
                for yy in range(y + 1, y + 5):
                    self.carve(x, yy, z)

    def run(self, x, z, dx, dz, n, y_start):
        uphill = OPP[DIRV[(dx, dz)]]
        cells = []
        for i in range(n):
            cx, cz, yf = x + dx * i, z + dz * i, y_start - i
            lateral = [(cx, cz + o) for o in (-1, 0, 1)] if dx else [(cx + o, cz) for o in (-1, 0, 1)]
            for lx, lz in lateral:
                block = wchoice(self.rng, [("minecraft:polished_blackstone_brick_stairs", 80), ("minecraft:nether_brick_stairs", 12),
                                           ("minecraft:blackstone_stairs", 8)])
                self.p.set(lx, yf, lz, f"{block}[facing={uphill},half=bottom]")
                for yy in range(yf + 1, yf + 5):
                    self.carve(lx, yy, lz)
            cells.append((cx, cz, yf))
        return cells

    def line(self):
        todo = set()
        for (x, y, z) in self.air:
            for dx, dy, dz in ((1, 0, 0), (-1, 0, 0), (0, 1, 0), (0, -1, 0), (0, 0, 1), (0, 0, -1)):
                n = (x + dx, y + dy, z + dz)
                if n not in self.air and self.p.inside(*n) and self.p.get(*n) in ROCK_IDS:
                    todo.add(n)
        for pos in sorted(todo):
            self.p.set(*pos, wchoice(self.rng, LINING))


def build_descent(pack):
    W = D = 19
    H = DROP + FD + 8
    r = Room("descent", W, H, D, 1)
    p = r.p
    sh = Shaft(r)
    sh.fill_rock()
    y0 = DROP + FD
    for x in (14, 15, 16):                                    # entry corridor and the top landing
        for z in range(11, 18):
            p.set(x, y0, z, PB)
            for y in range(y0 + 1, y0 + 5):
                sh.carve(x, y, z)
    sh.landing(14, 8, 16, 10, y0)
    cells1 = sh.run(13, 9, -1, 0, 9, y0 - 1)                 # flight 1 west along z=9
    sh.landing(2, 8, 4, 16, y0 - 10)                          # the middle landing
    cells2 = sh.run(5, 15, 1, 0, 9, y0 - 11)                  # flight 2 east along z=15
    sh.landing(14, 14, 16, 16, FD)                            # the bottom landing
    for x in (17, 18):
        for z in (14, 15, 16):
            p.set(x, FD, z, PB)
            for y in range(FD + 1, FD + 5):
                sh.carve(x, y, z)
    for cells in (cells1, cells2):
        for i, (cx, cz, yf) in enumerate(cells):
            if i % 4 == 2:
                p.set(cx, yf + 4, cz, "minecraft:soul_lantern[hanging=true]")
    sh.line()
    r.door("south", "spine_in", at=15, y=y0 + 1)
    r.door("east", "spine_out", pool=f"{CZ}/f2_s1", at=15, y=G)
    x, yf = 8, cells1[5][2]
    r.arrows(x, yf + 2, 11, "north", radius=8, damage=10, every=50)
    x, yf = 10, cells2[5][2]
    r.arrows(x, yf + 2, 13, "south", radius=8, damage=10, every=50)
    r.spawn(3, 12, [guard(3, L(1, 3)), brute(3, L(1, 2))], [blaze(3, L(1, 3)), brute(2, L(1, 3), "Citadel Captain", elite=True)],
            radius=9, spread=2, y=y0 - 10 + 1)
    for x, z in ((2, 8), (4, 16)):
        p.set(x, y0 - 10 + 1, z, WALL_PBB)
        p.set(x, y0 - 10 + 2, z, SOUL_FIRE)
    r.weather()
    autoconnect(p)
    pack.add(p)
    return p


# ---------------------------------------------------------------------------------------------------------------------
# The Knight's Hall (floor 1 champion): 29 x 19 x 21, the north door is shut with a bar plug until he falls
# ---------------------------------------------------------------------------------------------------------------------
def build_champion(pack):
    w, h, d = 29, FD + 15, 21
    r = Room("f1/champion", w, h, d, 1)
    r.shell()
    r.ribs(7, 4)
    p = r.p
    cx = w // 2
    r.door("south", "spine_in")
    r.door("north", "spine_out", pool=f"{CZ}/descent")
    for z in (2, 3):
        for x in (cx - 1, cx, cx + 1):
            for y in (G, G + 1, G + 2):
                p.set(x, y, z, BARS)
    for x in (cx - 1, cx, cx + 1):
        p.set(x, G + 3, 2, CHAIN + "[axis=y]")
    p.marker(cx, G + 1, 2, "door", group="champion", message="The portcullis holds. Slay the Infernal Knight.")
    r.reserve(cx - 2, 0, cx + 2, 4)
    for z1, z2 in ((5, 9), (13, 17)):
        r.lava_fall(1, z1, 3, z2, "east")
        r.lava_fall(w - 4, z1, w - 2, z2, "west")
    # the dais: a raised round of gilded blackstone and nether brick, with a planted greatsword (a column of blackstone)
    for x in range(cx - 5, cx + 6):
        for z in range(7, 16):
            dist = math.hypot(x - cx, z - 11)
            if dist <= 4.6:
                p.set(x, FD, z, GILD if dist <= 1.8 else (CHNB if dist <= 3.2 else NB))
    r.reserve(cx - 5, 6, cx + 5, 16)
    for x in (cx - 6, cx + 6):
        for z in (7, 15):
            r.col(x, z, y1=h - 4)
    for x, z in ((cx - 4, 5), (cx + 4, 5), (cx - 4, 17), (cx + 4, 17)):
        r.brazier(x, z)
    r.banners((6, 11, 16), y=G + 6, color="black")
    for z in (6, 11, 16):
        r.hang(cx - 3, z, 3)
        r.hang(cx + 3, z, 3)
    r.chest(5, d - 3, "north", "rare")
    r.chest(w - 6, d - 3, "north", "rare")
    r.arrows(0, G + 2, 11, "east", radius=11, damage=12)
    r.arrows(w - 1, G + 2, 11, "west", radius=11, damage=12)
    r.flames(cx - 8, 11, 1)
    r.flames(cx + 8, 11, 1)
    r.spawn(cx, 18, [guard(4, L(1, 3)), blaze(3, L(1, 3))], [brute(3, L(1, 4)), guard(3, L(1, 3))], radius=9, spread=3)
    p.marker(cx, G + 1, 11, "boss", boss="skyrpg_world:infernal_knight", radius=13, group="champion")
    return r.finish(pack)


# ---------------------------------------------------------------------------------------------------------------------
# The Molten Throne (floor 2 boss arena): 35 x 23 x 35
# ---------------------------------------------------------------------------------------------------------------------
KEY_DOOR = "minecraft:obsidian"
NOOK_DOOR = "minecraft:crying_obsidian"


def build_throne(pack):
    w, d = 35, 35
    h = FD + 19
    r = Room("boss/throne", w, h, d, 2)
    r.shell()
    p = r.p
    cx = w // 2
    cz = 22
    r.door("south", "spine_in")
    r.reserve(cx - 1, 24, cx + 1, d - 1)
    top = h - 2
    # the throne alcove (x 14..20, z 1..6) flanked by two annexes behind partitions (the row z=7 and the walls x=13 and x=21)
    for x in range(1, w - 1):
        for y in range(G, top):
            if x <= 13 or x >= 21:
                p.set(x, y, 7, PBB)
    for z in range(1, 8):
        for y in range(G, top):
            p.set(13, y, z, PBB)
            p.set(21, y, z, PBB)
    for x in (6, 7, 8):                                      # the vault door (key) and the hoard door (boss death)
        for y in (G, G + 1, G + 2):
            p.set(x, y, 7, KEY_DOOR)
    p.marker(7, G + 1, 7, "key_door", key="skyrpg_world:citadel_key", message="The vault is sealed. It needs the Citadel Key.")
    for x in (26, 27, 28):
        for y in (G, G + 1, G + 2):
            p.set(x, y, 7, NOOK_DOOR)
    p.marker(27, G + 1, 7, "door", group="tyrant", message="The Tyrant's hoard grinds open.")
    for xs in ((2, 12), (22, 32)):
        for x in range(xs[0], xs[1] + 1):
            for z in range(1, 7):
                for y in range(G, top):
                    p.set(x, y, z, AIR if y < G + 5 else PBB)
    p.chest(4, G, 1, "south", "skyrpg_world:chests/citadel_vault", tier="vault")
    p.chest(10, G, 1, "south", "skyrpg_world:chests/citadel_vault", tier="vault")
    p.chest(7, G, 1, "south", "skyrpg_world:chests/citadel_rare", tier="rare")
    for pos, b in (((6, G, 1), "minecraft:gold_block"), ((8, G, 1), "minecraft:gold_block"), ((3, G, 1), GILD), ((11, G, 1), GILD),
                   ((7, G + 4, 3), "minecraft:lantern[hanging=true]"), ((4, G + 3, 3), "minecraft:lantern[hanging=true]"),
                   ((10, G + 3, 3), "minecraft:lantern[hanging=true]")):
        p.set(*pos, b)
    p.chest(27, G, 1, "south", "skyrpg_world:chests/citadel_secret", tier="secret")
    p.marker(27, G, 2, "secret", name="The Tyrant's Hoard")
    for x in (25, 29):
        p.set(x, G, 1, "minecraft:gold_block")
    p.set(27, G + 4, 3, "minecraft:lantern[hanging=true]")
    r.sign(4, G + 1, 8, ["The vault is sealed", "in obsidian and fire.", "Bring the Citadel Key."], "south")
    # the magma heart: a lava fall behind bars in the back of the alcove, and the throne on a stepped dais in front of it
    r.lava_fall(15, 1, 19, 2, "south", top=top - 1)
    for x in range(14, 21):
        for z in (4, 5, 6):
            p.set(x, FD, z, NB)
    for x in range(15, 20):
        p.set(x, G, 4, NB)
    p.set(17, G, 5, "minecraft:polished_blackstone_brick_stairs[facing=south]")
    p.set(17, G + 1, 4, CHPB)
    p.set(17, G + 2, 4, GILD)
    for x in (14, 20):
        p.set(x, G, 5, SOUL_FIRE)
    for x in (15, 19):
        p.set(x, G + 1, 4, SOUL_FIRE)
    for x in (14, 20):
        r.keep |= {(x, z) for z in range(1, 7)}
    r.reserve(1, 1, w - 2, 7)
    # lava falls down both long walls (bars at x=4 / x=w-5)
    r.lava_fall(1, 10, 3, 31, "east")
    r.lava_fall(w - 4, 10, w - 2, 31, "west")
    # the arena: an inlaid ring, plain solid floor (the molten floor only melts plain blocks)
    for x in range(cx - 10, cx + 11):
        for z in range(cz - 10, cz + 11):
            dist = math.hypot(x - cx, z - cz)
            if 1 <= x <= w - 2 and 9 <= z <= d - 2 and 8.4 <= dist <= 9.6:
                p.set(x, FD, z, CHNB)
            elif 1 <= x <= w - 2 and 9 <= z <= d - 2 and dist <= 3.2:
                p.set(x, FD, z, NB if dist > 1.5 else CHNB)
    for x, z in ((8, 12), (26, 12), (8, 32), (26, 32), (11, 9), (23, 9)):
        r.col(x, z, y1=top - 1)
        r.keep.add((x, z))
    for x, z in ((cx - 7, cz), (cx + 7, cz), (cx, cz - 7), (cx, cz + 7), (10, 26), (24, 26), (10, 18), (24, 18)):
        r.hang(x, z, 3)
    for x, z in ((6, 22), (28, 22), (6, 16), (28, 16)):
        r.brazier(x, z)
    r.chest(8, 33, "north", "rare") if r.free(8, 33) else None
    r.arrows(5, G + 2, 7, "south", radius=15, damage=13)
    r.arrows(29, G + 2, 7, "south", radius=15, damage=13)
    r.flames(10, 16, 1)
    r.flames(24, 16, 1)
    r.rubble(8, 6, 10, w - 7, d - 3)
    p.marker(cx, G + 1, cz, "boss", boss="skyrpg_world:magma_tyrant", radius=18, group="tyrant")
    EXEMPT[p.path] = ("secret",)
    return r.finish(pack)


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
C = "skyrpg_combat:"
HELL_SCRAP, HELL_INGOT = M + "hellforged_scrap", M + "hellforged_ingot"
EGG_RARE = [it("skyrpg_pets:pup_egg", 1, 1, 1), it("skyrpg_pets:mole_egg", 1, 1, 1)]


def loot(pack):
    common = [it("minecraft:gold_ingot", 8, 3, 8), it("minecraft:gold_nugget", 6, 6, 16), it("minecraft:blaze_rod", 7, 1, 3),
              it("minecraft:magma_cream", 7, 2, 5), it("minecraft:quartz", 6, 3, 8), it("minecraft:coal", 5, 4, 10),
              it("minecraft:cooked_porkchop", 5, 3, 6), it("minecraft:experience_bottle", 5, 2, 5),
              it("skyrpg_enchants:runic_dust", 6, 2, 5), it(M + "emberite_chunk", 6, 2, 4), it(W + "rift_ember", 6, 1, 3),
              it(HELL_SCRAP, 4, 1, 2), it("minecraft:netherite_scrap", 1, 1, 1), it("minecraft:glowstone_dust", 5, 3, 8), book(3)]
    pack.loot_table("chests/citadel_common", table(
        lpool(rolls(4, 6), common),
        lpool(1, [it(C + "valor_mark", 3, 1, 1), it(C + "dread_fang", 2, 1, 1), it(W + "dread_crystal", 1, 1, 1),
                  it(W + "rift_ember", 3, 1, 2)], .24)))
    rare = [it("minecraft:diamond", 5, 2, 4), it("minecraft:gold_ingot", 7, 6, 14), it("minecraft:blaze_rod", 7, 2, 5),
            it("minecraft:magma_cream", 7, 3, 7), it("minecraft:experience_bottle", 6, 4, 9), it("skyrpg_enchants:runic_dust", 8, 4, 9),
            it(M + "emberite_ingot", 7, 2, 4), it(M + "emberite_chunk", 6, 3, 6), it(W + "rift_ember", 7, 2, 4),
            it(HELL_SCRAP, 7, 1, 3), it(HELL_INGOT, 3, 1, 1), it("minecraft:netherite_scrap", 2, 1, 2), it(W + "dread_crystal", 2, 1, 1), book(5)]
    pack.loot_table("chests/citadel_rare", table(
        lpool(rolls(5, 7), rare),
        lpool(1, [it(C + "elite_trophy", 3, 1, 1), it(C + "valor_mark", 4, 1, 2), it(C + "dread_fang", 3, 1, 2),
                  it(M + "heartstone", 2, 1, 1), it(W + "dread_crystal", 2, 1, 1)], .55),
        lpool(1, [it(C + "nightmare_core", 1, 1, 1)], .07),
        lpool(1, EGG_RARE, .035)))
    secret = [it("minecraft:diamond", 7, 3, 6), it("minecraft:gold_ingot", 8, 8, 18), it("minecraft:blaze_rod", 7, 3, 6),
              it("minecraft:magma_cream", 7, 4, 8), it("minecraft:experience_bottle", 6, 5, 10), it("skyrpg_enchants:runic_dust", 9, 5, 11),
              it(M + "emberite_ingot", 8, 3, 6), it(W + "rift_ember", 8, 3, 5), it(HELL_SCRAP, 8, 2, 4), it(HELL_INGOT, 5, 1, 2),
              it("minecraft:netherite_scrap", 3, 1, 2), it(W + "dread_crystal", 4, 1, 2), book(6)]
    pack.loot_table("chests/citadel_secret", table(
        lpool(rolls(5, 8), secret),
        lpool(1, [it(M + "prism", 3, 1, 1), it(M + "heartstone", 3, 1, 2), it(C + "valor_seal", 3, 1, 1), it(C + "elite_trophy", 3, 1, 2)]),
        lpool(1, [it(C + "nightmare_core", 1, 1, 1)], .10),
        lpool(1, [it("skyrpg_pets:golden_pet_egg", 1, 1, 1)], .014),
        lpool(1, EGG_RARE, .05)))
    vault = [it("minecraft:diamond", 8, 4, 8), it("minecraft:gold_ingot", 8, 10, 22), it("minecraft:blaze_rod", 7, 4, 8),
             it("minecraft:magma_cream", 7, 5, 10), it("minecraft:experience_bottle", 6, 6, 12), it("skyrpg_enchants:runic_dust", 9, 6, 14),
             it(M + "emberite_ingot", 8, 4, 8), it(W + "rift_ember", 8, 3, 6), it(HELL_SCRAP, 8, 3, 6), it(HELL_INGOT, 6, 1, 3),
             it("minecraft:netherite_scrap", 4, 1, 3), it(W + "dread_crystal", 5, 1, 2), it(C + "valor_seal", 4, 1, 2),
             it(C + "elite_trophy", 5, 1, 3), it(C + "dread_fang", 4, 1, 2), book(8)]
    pack.loot_table("chests/citadel_vault", table(
        lpool(rolls(6, 8), vault),
        lpool(1, [it(HELL_SCRAP, 1, 4, 8)]),
        lpool(1, [it("minecraft:netherite_scrap", 1, 1, 2)], .5),
        lpool(1, [it(C + "nightmare_core", 1, 1, 1)], .15),
        lpool(1, [it("skyrpg_pets:golden_pet_egg", 1, 1, 1)], .02),
        lpool(1, EGG_RARE, .06),
        lpool(1, [it("minecraft:enchanted_golden_apple", 1, 1, 1)], .15)))
    pack.loot_table("bosses/infernal_knight", table(
        lpool(1, [it(W + "citadel_key", 1)]),
        lpool(1, [it(HELL_SCRAP, 1, 3, 6)]),
        lpool(rolls(2, 4), [it("minecraft:blaze_rod", 6, 3, 6), it("minecraft:magma_cream", 6, 3, 7), it("minecraft:gold_ingot", 7, 8, 16),
                            it(M + "emberite_ingot", 5, 2, 4), it(W + "rift_ember", 6, 2, 4), it("minecraft:experience_bottle", 6, 4, 9),
                            it("skyrpg_enchants:runic_dust", 8, 5, 10), book(4)]),
        lpool(1, [it(HELL_INGOT, 1, 1, 2)], .35),
        lpool(1, [it("minecraft:netherite_scrap", 1, 1, 2)], .30),
        lpool(1, [it(W + "dread_crystal", 1, 1, 1)], .25),
        lpool(1, [it(C + "elite_trophy", 1, 1, 1)], .35),
        lpool(1, [it(C + "valor_mark", 1, 1, 2)], .30),
        lpool(1, [it(C + "dread_fang", 1, 1, 1)], .15),
        lpool(1, [it(C + "nightmare_core", 1, 1, 1)], .08),
        lpool(1, EGG_RARE, .04)))
    pack.loot_table("bosses/magma_tyrant", table(
        lpool(1, [it(W + "tyrants_core", 1, 1, 1)]),                 # the trophy: always one ...
        lpool(1, [it(W + "tyrants_core", 1, 1, 1)], .25),            # ... and a quarter of the time a second
        lpool(1, [it(W + "citadel_key", 1)]),
        lpool(1, [it(HELL_SCRAP, 1, 8, 14)]),
        lpool(1, [it(HELL_INGOT, 1, 2, 4)]),
        lpool(1, [it("minecraft:netherite_scrap", 1, 2, 4)]),
        lpool(1, [it(M + "emberite_ingot", 1, 5, 9)]),
        lpool(rolls(5, 7), [it("minecraft:diamond", 6, 3, 7), it("minecraft:gold_ingot", 8, 12, 28), it("minecraft:blaze_rod", 7, 6, 12),
                            it("minecraft:magma_cream", 7, 6, 14), it("skyrpg_enchants:runic_dust", 9, 8, 16), it(W + "rift_ember", 8, 4, 8),
                            it(W + "dread_crystal", 4, 1, 3), it("minecraft:experience_bottle", 7, 8, 16), book(5)]),
        lpool(1, [it(C + "valor_seal", 1, 1, 2)], .50),
        lpool(1, [it(C + "elite_trophy", 1, 1, 2)], .60),
        lpool(1, [it(C + "nightmare_core", 1, 1, 1)], .25),
        lpool(1, [it("skyrpg_pets:golden_pet_egg", 1, 1, 1)], .035),
        lpool(1, EGG_RARE, .10)))
    pack.loot_table("rewards/magma_tyrant", table(
        lpool(rolls(5, 7), [it("skyrpg_enchants:runic_dust", 9, 8, 16), it("minecraft:diamond", 7, 4, 9), it("minecraft:gold_ingot", 8, 12, 28),
                            it("minecraft:experience_bottle", 7, 8, 16), it(M + "emberite_ingot", 8, 4, 8), it(W + "rift_ember", 8, 4, 8),
                            it(HELL_SCRAP, 9, 4, 8), it(HELL_INGOT, 6, 1, 3), it("minecraft:netherite_scrap", 4, 1, 3),
                            it("minecraft:blaze_rod", 7, 5, 10), it("minecraft:magma_cream", 7, 5, 12), it(W + "dread_crystal", 5, 1, 3),
                            it(C + "valor_seal", 5, 1, 2), it(C + "elite_trophy", 5, 1, 3), it(M + "heartstone", 4, 1, 2), book(6)]),
        lpool(1, [it(C + "nightmare_core", 1, 1, 1)], .22),
        lpool(1, [it(C + "dread_fang", 1, 1, 2)], .35),
        lpool(1, [it("skyrpg_pets:golden_pet_egg", 1, 1, 1)], .04),
        lpool(1, EGG_RARE, .12)))


def tome_drops(pack):
    def drops(tag, entries):
        pack.json(f"skyrpg/tome_drops/{tag}.json", {
            "tag": f"skyrpg_boss=skyrpg_world:{tag}",
            "drops": [{"enchant": f"skyrpg_enchants:{e}", "level": lv, "chance": ch, "min_tier": 0} for e, lv, ch in entries]})
    drops("magma_tyrant", [("emberbrand", 3, 0.20), ("ember_ward", 1, 0.12), ("reapers_mark", 4, 0.12), ("reapers_mark", 5, 0.05),
                           ("bloodthirst", 4, 0.12), ("bloodthirst", 5, 0.05), ("cleave", 5, 0.15), ("vitality", 6, 0.15),
                           ("soulrend", 3, 0.04)])
    drops("infernal_knight", [("emberbrand", 2, 0.12), ("cleave", 3, 0.08), ("bloodthirst", 3, 0.08), ("steadfast", 3, 0.06),
                              ("vitality", 4, 0.08)])


def bosses(pack):
    pack.json("skyrpg/dungeon_bosses/infernal_knight.json", {
        "name": "The Infernal Knight", "entity": "minecraft:wither_skeleton", "dungeon": DUN, "tier": "champion", "level": 50,
        "health": 14000, "damage": 32, "speed": 0.28, "scale": 2.2, "knockback_resistance": 0.85, "bar_color": "purple",
        "equipment": {"mainhand": "minecraft:netherite_sword", "head": "minecraft:netherite_helmet", "chest": "minecraft:netherite_chestplate",
                      "legs": "minecraft:netherite_leggings", "feet": "minecraft:netherite_boots"},
        "health_per_player": 0.6, "coins": [8500, 11000], "delving_xp": 3000,
        "intro": "The Infernal Knight rises in blackened plate, flame running down his blade: 'No one breaches the Burning Bastion.'",
        "death": "The Knight kneels, his armour split and glowing. The portcullis grinds open and a key of black iron drops.",
        "max_minions": 4, "loot": "skyrpg_world:bosses/infernal_knight", "reset_ticks": 72000,
        "first_clear": {"coins": 9500, "xp": {"skyrpg_skills:combat": 4800}},
        "moves": [
            {"type": "slam", "every": 210, "radius": 6, "damage": 26, "knockup": 0.9, "windup": 22, "particle": "minecraft:flame",
             "warn": "The Knight raises his burning blade!"},
            {"type": "leap", "every": 290, "damage": 24, "radius": 4, "range": 18,
             "warn": "The Knight vaults at you in a shower of sparks!"},
            {"type": "volley", "every": 270, "projectile": "fireball", "count": 4, "damage": 14, "spread": 0.18,
             "warn": "The Knight hurls fire from his sword!"},
            {"type": "ward", "every": 800, "below": 0.5, "duration": 100, "reduction": 0.75,
             "warn": "Black plate slams shut over the Knight!"},
            {"type": "summon", "every": 480, "max": 4,
             "mobs": [{"type": "minecraft:wither_skeleton", "count": 2, "level": 48, "name": "Citadel Guard",
                       "equipment": {"mainhand": "minecraft:stone_sword", "head": "minecraft:iron_helmet"}}],
             "warn": "The Knight calls his guard from the dark!"}]})
    pack.json("skyrpg/dungeon_bosses/magma_tyrant.json", {
        "name": "The Magma Tyrant", "entity": "skyrpg_combat:magma_tyrant", "dungeon": DUN, "level": 52, "health": 36000, "damage": 44,
        "speed": 0.27, "scale": 1.0, "knockback_resistance": 1.0, "bar_color": "red", "health_per_player": 0.6,
        "coins": [36000, 48000], "delving_xp": 16000,
        "intro": "The throne cracks open and the molten heart beats. 'Little cinders. I am the fire under the world, and I am hungry.'",
        "death": "The Magma Tyrant splits along every seam and the heart-fire gutters out. His burning core rolls free and the throne cools.",
        "max_minions": 8, "loot": "skyrpg_world:bosses/magma_tyrant", "reward_loot": "skyrpg_world:rewards/magma_tyrant",
        "first_clear": {"coins": 70000, "xp": {"skyrpg_skills:combat": 20000}},
        "reset_ticks": 72000,
        "phases": [{"below": 0.66, "message": "The Tyrant roars. Magma cubes boil out of the walls and Ember Imps pour from the heart!"},
                   {"below": 0.33, "message": "The Tyrant's crown ignites. The floor runs molten, and he fights without mercy!"}],
        "moves": [
            {"type": "slam", "every": 210, "radius": 8, "damage": 36, "knockup": 1.0, "windup": 24, "particle": "minecraft:lava",
             "warn": "The Tyrant raises a fist of molten rock!"},
            {"type": "volley", "every": 200, "projectile": "fireball", "count": 5, "damage": 16, "spread": 0.2,
             "warn": "The Tyrant spits fireballs!"},
            {"type": "leap", "every": 310, "damage": 32, "radius": 5, "range": 22,
             "warn": "The Tyrant leaps, shaking the whole hall!"},
            {"type": "summon", "every": 470, "below": 0.66, "max": 6,
             "mobs": [{"type": "minecraft:magma_cube", "count": 2, "level": 50, "name": "Magma Cube"},
                      {"type": "skyrpg_combat:ember_imp", "count": 3, "level": 50, "name": "Ember Imp"}],
             "warn": "Magma cubes and Ember Imps pour from the heart!"},
            {"type": "pull", "every": 380, "below": 0.66, "radius": 16, "strength": 1.2, "particle": "minecraft:smoke",
             "warn": "The Tyrant drags you towards the molten heart!"},
            {"type": "heal", "every": 540, "below": 0.5, "percent": 0.03,
             "warn": "The heart-fire mends the Tyrant!"},
            {"type": "molten", "every": 240, "below": 0.33, "count": 14, "radius": 5, "delay": 25, "duration": 120, "damage": 30,
             "warn": "The floor glows white-hot. Move!"},
            {"type": "shockwave", "every": 300, "below": 0.33, "radius": 11, "damage": 26, "push": 2.0, "particle": "minecraft:flame",
             "warn": "A shockwave of fire rolls out from the Tyrant!"},
            {"type": "slam", "every": 170, "below": 0.33, "radius": 9, "damage": 40, "knockup": 1.1, "windup": 20, "particle": "minecraft:lava",
             "warn": "The throne room erupts under his fist!"},
            {"type": "enrage", "below": 0.33, "speed": 0.35, "damage": 0.4,
             "warn": "The Magma Tyrant's crown blazes white!"}]})


# ---------------------------------------------------------------------------------------------------------------------
# The structure
# ---------------------------------------------------------------------------------------------------------------------
BIOMES = ["minecraft:nether_wastes", "minecraft:basalt_deltas", "minecraft:soul_sand_valley",
          {"id": "skyrpg_world:ember_forest", "required": False}, {"id": "skyrpg_world:bone_sea", "required": False},
          {"id": "skyrpg_world:obsidian_spires", "required": False}]


def build(pack):
    P = lambda s: f"{CZ}/{s}"  # noqa: E731
    build_gatehouse(pack)
    for fl in (1, 2):
        caps = {k: P(f"caps_{k}{fl}") for k in ("spine", "side", "tail")}
        for kind in ("spine", "side", "tail"):
            cap(pack, kind, fl)
            pack.pool(caps[kind], [(P(f"caps/{kind}{fl}"), 1)])
        nxt = {1: P(f"f{fl}_s2"), 2: P(f"f{fl}_s3"), 3: P("champion") if fl == 1 else P("boss")}
        for stage, specs in SPINE[fl].items():
            for kind, tag, w in specs:
                HALL_FNS[kind](pack, fl, tag, w, nxt[stage], SPINE_SIDES[fl])
            pack.pool(P(f"f{fl}_s{stage}"), [(P(f"f{fl}/{'warren' if (kind == 'barracks' and fl == 2) else kind}_{tag}"), 3) for kind, tag, _ in specs],
                      fallback=caps["spine"])
        rooms = []
        for fn, wgt in ROOMS[fl]:
            piece = fn(pack, fl)
            rooms.append((piece.path, wgt))
        pack.pool(P(f"f{fl}_rooms"), rooms, fallback=caps["side"])
        leaves = [(fn(pack, fl).path, 1) for fn in LEAVES]
        pack.pool(P(f"f{fl}_leaves"), leaves, fallback=caps["tail"])
    build_champion(pack)
    build_descent(pack)
    build_throne(pack)
    pack.pool(P("entrance"), [(P("entrance"), 1)])
    pack.pool(P("champion"), [(P("f1/champion"), 1)], fallback=P("caps_spine1"))
    pack.pool(P("descent"), [(P("descent"), 1)], fallback=P("caps_spine1"))
    pack.pool(P("boss"), [(P("boss/throne"), 1)], fallback=P("caps_spine2"))
    pack.biome_tag(CZ, BIOMES)
    pack.structure(CZ, start_pool=P("entrance"), biomes=f"#skyrpg_world:has_structure/{CZ}", size=16, max_distance=116,
                   step="underground_structures", adaptation="none", heightmap=None, start_height=START_HEIGHT)
    pack.structure_set(CZ, 44, 16, 26101318, exclusion=("minecraft:nether_complexes", 8))
    loot(pack)
    tome_drops(pack)
    pack.json("skyrpg/dungeons/infernal_citadel.json", {
        "key": "skyrpg_world:citadel_key", "name": "The Infernal Citadel", "structure": DUN, "color": "#FF6A1F",
        "icon": "minecraft:blaze_rod", "level": 48, "boss": "skyrpg_world:magma_tyrant", "reset_ticks": 72000,
        "description": "A blackstone fortress on the Nether's lava sea. Fight through the Burning Bastion and the Infernal Knight to the Molten Throne and the Magma Tyrant."})
    bosses(pack)
    pack.json("skyrpg/items/citadel_key.json", {
        "name": "Citadel Key", "rarity": "rare", "type": "Dungeon Key",
        "description": "Opens the vault behind the Molten Throne in the Infernal Citadel. Used up when you open it."})


# ---------------------------------------------------------------------------------------------------------------------
# Offline checks
# ---------------------------------------------------------------------------------------------------------------------
CLIENT_JAR = pathlib.Path.home() / ".gradle/caches/fabric-loom/26.3/minecraft-client.jar"
MOD_BLOCKS = {"skyrpg_world:waystone"}


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


def check_mod_items(pack):
    """Mod item ids in the loot tables that no registered item or JSON in the repo mentions (listed, not fatal: some are being added)."""
    root = pathlib.Path(__file__).resolve().parents[3]
    names = set()

    def walk(node):
        if isinstance(node, dict):
            n = str(node.get("name", ""))
            if node.get("type") == "minecraft:item" and n.startswith("skyrpg_"):
                names.add(n)
            for v in node.values():
                walk(v)
        elif isinstance(node, list):
            for v in node:
                walk(v)
    for path, data in pack.files.items():
        if "loot_table" in path:
            walk(data)
    text = ""
    for sub in ("world", "mining", "combat", "items", "pets", "enchants"):
        for f in (root / sub / "src").rglob("*.java"):
            try:
                text += f.read_text(encoding="utf-8", errors="ignore")
            except OSError:
                pass
    missing = sorted(n for n in names if f'"{n.split(":")[1]}"' not in text)
    if missing:
        print("mod items not registered yet (need other agents' Java):", ", ".join(missing))
    return missing


PASS_SUFFIX = ("_carpet", "_sign", "_button", "_plate", "rail", "_torch", "_lantern", "_bed", "_banner", "_skull")
PASS_IDS = {AIR, "minecraft:cobweb", "minecraft:lever", "minecraft:iron_chain", "minecraft:torch", "minecraft:soul_torch",
            "minecraft:pointed_dripstone", "minecraft:lantern", "minecraft:soul_lantern", "minecraft:ladder", "minecraft:soul_fire",
            "minecraft:fire", "minecraft:wall_torch"}


def passable(i):
    return i in PASS_IDS or i.endswith(PASS_SUFFIX)


def walk_check(pack):
    """Every doorway and every marker (bosses, spawners, chests, plates, levers) must be reachable from the first doorway.
    'door'/'key_door' markers count as open; hidden caches (secret chests) are exempt. Lava is neither floor nor air."""
    problems = []
    for pc in pack.pieces.values():
        if pc.path.startswith(f"{CZ}/caps/"):
            continue
        cells = pc.cells
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
            return None if c is None else c[0]["id"]

        def free(x, y, z):
            if not pc.inside(x, y, z):
                return False
            b = blk(x, y, z)
            return b is not None and passable(b)

        def ground(x, y, z):
            b = blk(x, y, z)
            return b is not None and not passable(b) and b != LAVA

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
            if kind == "chest":
                ok = any((bx + dx, by, bz + dz) in seen for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1)))
            else:
                ok = any((bx, by + dy, bz) in seen for dy in (0, -1, 1))
            if not ok:
                problems.append(f"{pc.path}: {kind} marker at {(bx, by, bz)} is unreachable")
            if kind == "chest" and data.get("tier") != "secret":
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


def check_lava(pack):
    """Lava must never touch an unset cell or the piece boundary, or it would flow out of the template into the world."""
    problems = []
    for pc in pack.pieces.values():
        for (x, y, z), (entry, _) in pc.cells.items():
            if entry["id"] != LAVA:
                continue
            for dx, dy, dz in ((1, 0, 0), (-1, 0, 0), (0, 0, 1), (0, 0, -1), (0, -1, 0)):
                n = (x + dx, y + dy, z + dz)
                c = pc.cells.get(n)
                if c is None or c[0]["id"] in (AIR, BARS):
                    # air beside lava above the surface line is fine for pits (the surface is open); flag other leaks
                    if c is None or (dy == -1) or (c[0]["id"] == AIR and y >= FD and (dx or dz)):
                        problems.append(f"{pc.path}: lava at {(x, y, z)} next to {n} ({'unset' if c is None else 'air'})")
                        break
    return problems


def check_jigsaw_rules(pack):
    """Spine pools hold no corner pieces: every piece in a spine pool has exactly a south 'in' and a north 'out' jigsaw."""
    problems = []
    for pool_id, pool in pack.pools.items():
        if not pool_id.split(":")[1].startswith(f"{CZ}/f") or "_s" not in pool_id:
            continue
        for e in pool["elements"]:
            el = e["element"]
            if el["element_type"] != "minecraft:single_pool_element":
                continue
            pc = pack.pieces[el["location"].split(":", 1)[1]]
            spine_doors = {(j[1].split("_")[0]) for j in pc.jigsaws if j[2].startswith("skyrpg_world:cit_spine")}
            if not {"north", "south"} <= spine_doors:
                problems.append(f"{pc.path}: spine piece without a straight north/south pair")
            if "east" in spine_doors or "west" in spine_doors:
                problems.append(f"{pc.path}: a corner piece in a spine pool")
    return problems


def stage_sizes(pack, pool_name):
    sizes = []
    for e in pack.pools[f"skyrpg_world:{CZ}/{pool_name}"]["elements"]:
        if e["element"]["element_type"] != "minecraft:single_pool_element":
            continue
        sizes.append(pack.pieces[e["element"]["location"].split(":", 1)[1]].size)
    return sizes


def reach_table(pack):
    """Worst-case distances from the entrance centre along the spine axes (jigsaw max_distance is 116; keep <= 110)."""
    ent = pack.pieces[f"{CZ}/entrance"].size
    a = ent[2] // 2 + 1
    worst = 0
    print("reach table (worst case straight chain, blocks from the entrance centre; limit 110 per axis, 116 hard):")
    print(f"  gatehouse north door plane            A = {a:5.1f}")
    for st in ("f1_s1", "f1_s2", "f1_s3", "champion", "descent"):
        dep = max(s[2] for s in stage_sizes(pack, st))
        if st == "descent":
            a_axis = a + 3                     # floor 2's spine axis: the descent's exit door is 3 from its south wall
        a += dep
        print(f"  after {st:9s} (max depth {dep:2d})        A = {a:5.1f}")
        worst = max(worst, a)
    dw = pack.pieces[f"{CZ}/descent"].size[0]
    print(f"  floor 2 runs along B at A = {a_axis:5.1f}")
    b = (dw - 1 - 15) + 1
    wid_max = 0
    for st in ("f2_s1", "f2_s2", "f2_s3", "boss"):
        dep = max(s[2] for s in stage_sizes(pack, st))
        wid = max(s[0] for s in stage_sizes(pack, st))
        wid_max = max(wid_max, wid)
        b += dep
        print(f"  after {st:6s} (depth {dep:2d}, widest {wid:2d})   B = {b:5.1f}   A extent to {a_axis + wid / 2:5.1f}")
    a_far = a_axis + wid_max / 2
    print(f"  worst: A {max(a, a_far):5.1f}  B {b:5.1f}")
    return max(a, a_far, b)


def expected_pieces(pack, trials=400):
    """Monte-Carlo of the jigsaw tree (no collisions): how many pieces per floor, side rooms and tails included."""
    rng = random.Random(7)
    pools = pack.pools

    def pick(pool_id):
        pool = pools[pool_id]
        els = [e for e in pool["elements"] if e["element"]["element_type"] == "minecraft:single_pool_element"]
        return rng.choices(els, [e["weight"] for e in els])[0]["element"]["location"].split(":", 1)[1]

    totals = {"gate": 0, 1: 0, 2: 0}
    caps = {"gate": 0, 1: 0, 2: 0}

    def floor_of(path, parent):
        if path.endswith("/entrance"):
            return "gate"
        if "/f1/" in path or path.endswith("/descent"):
            return 1
        if "/f2/" in path or "/boss/" in path:
            return 2
        return parent

    for _ in range(trials):
        queue = [(f"{CZ}/entrance", 0, "gate")]
        while queue:
            path, depth, floor = queue.pop()
            (caps if "/caps/" in path else totals)[floor] += 1
            for (pos, orientation, name, target, pool) in pack.pieces[path].jigsaws:
                if pool == "minecraft:empty":
                    continue
                if depth + 1 >= 16:
                    child = pools[pools[pool]["fallback"]]["elements"][0]["element"]["location"].split(":", 1)[1]
                else:
                    child = pick(pool)
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
        print(f"  {path:42s} {pc.size[0]:2d}x{pc.size[1]:2d}x{pc.size[2]:2d}  " + " ".join(f"{k}={v}" for k, v in sorted(c.items())))
    print("marker counts:", dict(kinds))


def show(pack, path, y):
    pc = pack.pieces[f"{CZ}/{path}"]
    w, h, d = pc.size
    sym = {AIR: " ", LAVA: "L", BARS: "#"}
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
    problems = walk_check(pack) + check_packs(pack) + check_lava(pack) + check_jigsaw_rules(pack)
    n_bad = check_blocks(pack) + check_items(pack)
    check_mod_items(pack)
    for pr in problems:
        print("PROBLEM:", pr)
    report(pack)
    reach = reach_table(pack)
    if reach > 110:
        problems.append(f"reach {reach} exceeds 110")
        print("PROBLEM: reach exceeds 110")
    avg, capavg = expected_pieces(pack)
    print("expected pieces per floor (Monte-Carlo, collisions ignored), without caps:", {k: round(v, 1) for k, v in avg.items()},
          "caps:", {k: round(v, 1) for k, v in capavg.items()})
    if problems or n_bad:
        raise SystemExit("offline checks failed")
    pack.save(clean=[CZ])
