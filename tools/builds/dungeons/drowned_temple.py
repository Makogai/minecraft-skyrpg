"""The Drowned Temple (skyrpg_world:drowned_temple), v2: a two-floor sunken temple on the ocean floor.

Layout (jigsaw):
  Floor 1 "Outer Sanctum":  entrance -> f1a -> f1b -> f1c -> champion hall -> descent (turns 90 degrees)
  Floor 2 "Abyssal Vaults": descent -> f2a -> f2b -> f2c -> Tidemother's sanctum (runs perpendicular to floor 1)
Every spine hall has side doors into `rooms1` / `rooms2`; those rooms chain one more step into `leaf1` / `leaf2`.
Spine doors use placement_priority 10, side doors 0. Every pool falls back to a 1-thick cap. All doorways are 3x3 and
flooded. Floor 2 pieces are at most 10 tall (except side rooms and the sanctum) so they sit under floor 1.
The descent piece enters high (local y=11, north wall, the upper floor) and leaves low (local y=1, EAST wall, the
lower floor, 10 below), turning the spine 90 degrees so floor 2 runs beside floor 1.
Regenerate with:  python tools/builds/dungeons/drowned_temple.py
"""
import math
import random
import sys
import pathlib

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parents[1]))
from dungeon_kit import Pack, Piece  # noqa: E402

DT = "drowned_temple"
SPINE_IN, SPINE_OUT, SIDE_IN, SIDE_OUT = "dt_spine_in", "dt_spine_out", "dt_side", "dt_side_out"
WATER = "minecraft:water"
AIR = "minecraft:air"
BRICKS = "minecraft:prismarine_bricks"
SAND = "minecraft:cut_sandstone"
BLACK = "minecraft:polished_blackstone_bricks"
BLACK_BAND = "minecraft:blackstone"
TURTLE = "minecraft:turtle_helmet"

WATERLOGGABLE_SUFFIX = ("_stairs", "_slab", "_wall", "_fence", "_coral", "_coral_fan", "_coral_wall_fan", "_sign",
                        "_trapdoor", "_pane", "_lantern", "_sensor")
WATERLOGGABLE = {"sea_pickle", "chest", "ladder", "conduit", "iron_bars", "lantern", "mangrove_roots",
                 "sculk_shrieker", "pointed_dripstone"}
CORALS = ["tube", "brain", "bubble", "fire", "horn"]

# per-floor pools
F1_SIDE, F1_LEAF = f"{DT}/rooms1", f"{DT}/leaf1"
F2_SIDE, F2_LEAF = f"{DT}/rooms2", f"{DT}/leaf2"
CAPS = {1: (f"{DT}/caps_spine", f"{DT}/caps_side"), 2: (f"{DT}/caps_spine2", f"{DT}/caps_side2")}


def waterloggable(block):
    name = block.split("[")[0].split(":")[-1]
    return name in WATERLOGGABLE or name.endswith(WATERLOGGABLE_SUFFIX)


class Pal:
    def __init__(self, wall, band, col, cap, light, weather, accent):
        self.wall, self.band, self.col, self.cap, self.light = wall, band, col, cap, light
        self.weather, self.accent = weather, accent


PAL = {
    1: Pal(BRICKS, SAND, SAND, "minecraft:chiseled_sandstone", "minecraft:sea_lantern",
           {BRICKS: [("minecraft:prismarine", 0.10), ("minecraft:mossy_stone_bricks", 0.06)],
            SAND: [("minecraft:sandstone", 0.10), ("minecraft:smooth_sandstone", 0.10)]},
           "minecraft:smooth_sandstone"),
    2: Pal(BLACK, BLACK_BAND, "minecraft:polished_blackstone", "minecraft:gilded_blackstone", "minecraft:sea_lantern",
           {BLACK: [("minecraft:cracked_polished_blackstone_bricks", 0.12), ("minecraft:blackstone", 0.08),
                    ("minecraft:sculk", 0.03)],
            BLACK_BAND: [("minecraft:polished_blackstone", 0.12)]},
           "minecraft:smooth_basalt"),
}


# ---------------------------------------------------------------------------------------------------------------------
# Mobs. Floor 1 is level 26-30, floor 2 is level 29-33 (L(fl, 0..4)).
# ---------------------------------------------------------------------------------------------------------------------
def L(fl, d):
    return (26 if fl == 1 else 29) + d


def mob(type_, count, level, name=None, elite=False, **equipment):
    m = {"type": type_, "count": count, "level": level, "elite": elite}
    if name:
        m["name"] = name
    if equipment:
        m["equipment"] = dict(equipment)
    return m


def drowned(count, level, name="Drowned Acolyte", elite=False, mainhand="minecraft:trident", head=None):
    eq = {"mainhand": mainhand}
    if head:
        eq["head"] = head
    return mob("minecraft:drowned", count, level, name, elite, **eq)


def acolyte(n, lvl):
    return drowned(n, lvl, "Drowned Acolyte", head=TURTLE)


def lancer(n, lvl):
    return drowned(n, lvl, "Drowned Lancer")


def sentinel(n, lvl, elite=True):
    return drowned(n, lvl, "Tidebound Sentinel", elite, head=TURTLE)


def shellbearer(n, lvl, name="Drowned Archivist"):
    return drowned(n, lvl, name, mainhand="minecraft:nautilus_shell", head=TURTLE)


def sentry(n, lvl):
    return mob("minecraft:stray", n, lvl, "Drowned Sentry", False, mainhand="minecraft:bow", head="minecraft:iron_helmet")


def lurker(n, lvl, name="Tide Lurker", elite=False):
    return mob("skyrpg_combat:tide_lurker", n, lvl, name, elite)


def guardian(n, lvl, name="Reef Guardian", elite=False):
    return mob("minecraft:guardian", n, lvl, name, elite)


def total(mobs):
    return sum(m["count"] for m in mobs)


def std_pack(fl):
    if fl == 1:
        return [acolyte(3, L(1, 0)), lancer(1, L(1, 2)), lurker(1, L(1, 1))]
    return [acolyte(2, L(2, 1)), lancer(2, L(2, 3)), lurker(1, L(2, 2)), sentry(1, L(2, 2))]


# ---------------------------------------------------------------------------------------------------------------------
# Room: a flooded room with markers and decor helpers
# ---------------------------------------------------------------------------------------------------------------------
class Room:
    """A flooded room: shell, water up to `water` (inclusive), air above (an air pocket), doors, decor."""

    def __init__(self, path, w, h, d, fl=1, water=None, roof=True, dark=True):
        self.p = Piece(f"{DT}/{path}", w, h, d)
        self.w, self.h, self.d = w, h, d
        self.fl = fl
        self.pal = PAL[fl]
        self.roof = roof
        self.water = (h - 2 if roof else h - 1) if water is None else water
        self.dark = dark
        self.keepout = set()
        self.rng = random.Random(path)
        self.side_pool = F1_SIDE if fl == 1 else F2_SIDE
        self.wave_rooms = 0

    # -- blocks -------------------------------------------------------------------------------------------------
    def put(self, x, y, z, block):
        """Sets a block, waterlogging it where it sits under the water line and can hold water."""
        if y <= self.water and waterloggable(block) and "waterlogged" not in block:
            block = block[:-1] + ",waterlogged=true]" if block.endswith("]") else block + "[waterlogged=true]"
        self.p.set(x, y, z, block)

    def fill(self, x1, y1, z1, x2, y2, z2, block):
        for x in range(min(x1, x2), max(x1, x2) + 1):
            for y in range(min(y1, y2), max(y1, y2) + 1):
                for z in range(min(z1, z2), max(z1, z2) + 1):
                    self.put(x, y, z, block)

    def shell(self):
        p, w, h, d, pal = self.p, self.w, self.h, self.d, self.pal
        p.fill(0, 0, 0, w - 1, h - 1, d - 1, pal.wall)
        for y in range(1, h - 1):
            p.fill(1, y, 1, w - 2, y, d - 2, WATER if y <= self.water else AIR)
        if not self.roof:  # open top: flooded all the way, battlements on the wall tops
            p.fill(1, h - 1, 1, w - 2, h - 1, d - 2, WATER)
            for x in range(w):
                for z in range(d):
                    if (x in (0, w - 1) or z in (0, d - 1)) and (x + z) % 2:
                        p.set(x, h - 1, z, WATER)
        for x in range(w):
            for z in range(d):
                if x in (0, w - 1) or z in (0, d - 1):
                    p.set(x, 4, z, pal.band)

    def lights(self, y=5, step=None):
        p, w, d = self.p, self.w, self.d
        step = step or (4 if self.fl == 1 else 7)
        cells = [(x, 0) for x in range(2, w - 2, step)] + [(x, d - 1) for x in range(2, w - 2, step)]
        cells += [(0, z) for z in range(2, d - 2, step)] + [(w - 1, z) for z in range(2, d - 2, step)]
        for x, z in cells:
            if abs(x - w // 2) <= 2 and z in (0, d - 1):
                continue
            if abs(z - d // 2) <= 2 and x in (0, w - 1):
                continue
            if p.get(x, y, z) == self.pal.wall:
                p.set(x, y, z, self.pal.light)

    def weather(self):
        ch = {k: list(v) for k, v in self.pal.weather.items()}
        if self.dark:
            ch[self.pal.wall].append(("minecraft:dark_prismarine", 0.06 if self.fl == 1 else 0.10))
        self.p.weather("a", ch)

    # -- doors --------------------------------------------------------------------------------------------------
    def door(self, side, kind, pool=None, at=None, y=1):
        p, w, d = self.p, self.w, self.d
        names = {"spine_in": (SPINE_IN, SPINE_IN, "minecraft:empty", 0),
                 "spine_out": (SPINE_OUT, SPINE_IN, pool, 10),
                 "side_in": (SIDE_IN, SIDE_IN, "minecraft:empty", 0),
                 "side_out": (SIDE_OUT, SIDE_IN, pool or self.side_pool, 0)}
        name, target, pl, prio = names[kind]
        ns_side = side in ("north", "south")
        c = (w // 2 if ns_side else d // 2) if at is None else at
        plane = {"north": 0, "south": d - 1, "west": 0, "east": w - 1}[side]
        inward = 1 if side in ("north", "west") else -1
        for i in range(-1, 2):
            for yy in (y, y + 1, y + 2):
                x, z = (c + i, plane) if ns_side else (plane, c + i)
                p.set(x, yy, z, WATER)
        for off in (-2, 2):
            for yy in (y, y + 1, y + 2):
                x, z = (c + off, plane) if ns_side else (plane, c + off)
                p.set(x, yy, z, self.pal.band)
        if y == 1:
            for depth in range(1, 5):
                for i in range(-2, 3):
                    x, z = (c + i, plane + inward * depth) if ns_side else (plane + inward * depth, c + i)
                    self.keepout.add((x, z))
        p.door(side, name, target, pl, at=at, y=y, final_state=WATER, placement_priority=prio)

    def keep(self, x1, z1, x2, z2):
        self.keepout |= {(x, z) for x in range(min(x1, x2), max(x1, x2) + 1) for z in range(min(z1, z2), max(z1, z2) + 1)}

    # -- decor --------------------------------------------------------------------------------------------------
    def col(self, x, z, y0, y1, cap=None):
        cap = cap or self.pal.cap
        for y in range(y0, y1 + 1):
            self.p.set(x, y, z, self.pal.col)
        self.p.set(x, y0, z, cap)
        self.p.set(x, y1, z, cap)
        self.keepout.add((x, z))

    def kelp(self, x, z, height, y0=1):
        height = min(height, self.water - y0 + 1)
        for i in range(height):
            self.p.set(x, y0 + i, z, "minecraft:kelp[age=25]" if i == height - 1 else "minecraft:kelp_plant")

    def coral_mound(self, x, z, y=1):
        dead = "dead_" if self.fl == 2 else ""
        c = self.rng.choice(CORALS)
        self.p.set(x, y, z, f"minecraft:{dead}{c}_coral_block")
        roll = self.rng.random()
        if y + 1 <= self.water:
            if roll < 0.5:
                self.put(x, y + 1, z, f"minecraft:{dead}{self.rng.choice(CORALS)}_coral_fan")
            elif roll < 0.85:
                self.put(x, y + 1, z, f"minecraft:{dead}{self.rng.choice(CORALS)}_coral")

    def pickles(self, x, z, y=1):
        self.p.set(x, y, z, f"minecraft:sea_pickle[pickles={self.rng.randint(1, 4)},waterlogged=true]")

    def scatter(self, n, x1, z1, x2, z2, kinds=None, kelp_h=(2, 5)):
        kinds = kinds or (("kelp", "kelp", "coral", "pickle") if self.fl == 1 else ("kelp", "coral", "coral", "coral"))
        tries = 0
        while n > 0 and tries < 600:
            tries += 1
            x, z = self.rng.randint(x1, x2), self.rng.randint(z1, z2)
            if (x, z) in self.keepout or self.p.get(x, 1, z) != WATER or self.p.get(x, 2, z) != WATER:
                continue
            kind = self.rng.choice(kinds)
            if kind == "kelp":
                self.kelp(x, z, self.rng.randint(*kelp_h))
            elif kind == "coral":
                self.coral_mound(x, z)
            else:
                self.pickles(x, z)
            n -= 1

    def floor_patch(self, n, block, x1, z1, x2, z2, spread=2):
        """Dark patches on the floor (sculk, basalt, mud)."""
        for _ in range(n):
            cx, cz = self.rng.randint(x1, x2), self.rng.randint(z1, z2)
            for x in range(cx - spread, cx + spread + 1):
                for z in range(cz - spread, cz + spread + 1):
                    if x1 <= x <= x2 and z1 <= z <= z2 and (x - cx) ** 2 + (z - cz) ** 2 <= spread * spread + 1 \
                            and self.p.get(x, 0, z) == self.pal.wall:
                        self.p.set(x, 0, z, block)

    # -- markers ------------------------------------------------------------------------------------------------
    def chest(self, x, y, z, facing, tier="common"):
        wl = "true" if y <= self.water else "false"
        loot = f"skyrpg_world:chests/temple_{tier}"
        self.p.set(x, y, z, f"minecraft:chest[facing={facing},waterlogged={wl}]", {"id": "minecraft:chest", "LootTable": loot})
        self.p.marker(x, y, z, "chest", loot=loot, tier=tier)
        self.keepout.add((x, z))

    def spawner(self, x, y, z, mobs=None, waves=None, radius=9, spread=4, group=None):
        data = {"radius": radius, "spread": spread}
        packs = waves if waves else [mobs]
        for pk in packs:
            assert 4 <= total(pk) <= 10, f"{self.p.path}: pack of {total(pk)}"
        if waves:
            data["waves"] = waves
            self.wave_rooms += 1
        else:
            data["mobs"] = mobs
        if group:
            data["group"] = group
        self.p.marker(x, y, z, "spawner", **data)

    def arrows(self, x, y, z, facing, radius=10, damage=5, every=40):
        """A dispenser-looking slot in a wall that shoots at players in line of sight."""
        self.p.set(x, y, z, f"minecraft:dispenser[facing={facing}]")
        self.p.marker(x, y, z, "trap", type="arrows", radius=radius, every=every, damage=damage)

    def spikes(self, cx, cz, r=1, y=1, damage=5, every=30):
        for x in range(cx - r, cx + r + 1):
            for z in range(cz - r, cz + r + 1):
                if (x + z) % 2 == (cx + cz) % 2 and (x, z) not in self.keepout and self.p.get(x, y, z) == WATER and self.p.get(x, y - 1, z) not in (None, WATER):
                    self.p.set(x, y, z, "minecraft:pointed_dripstone[thickness=tip,vertical_direction=up,waterlogged=true]")
                    self.keepout.add((x, z))
        self.p.marker(cx, y, cz, "trap", type="spikes", radius=r + 2, every=every, damage=damage)

    def poison(self, x1, z1, x2, z2, y=1, damage=2, every=60):
        """A stagnant pool: mud floor, mangrove roots, a poison cloud."""
        for x in range(x1, x2 + 1):
            for z in range(z1, z2 + 1):
                self.p.set(x, y - 1, z, "minecraft:mud")
                if self.p.get(x, y, z) == WATER and (x + z) % 3 == 0:
                    self.p.set(x, y, z, "minecraft:mangrove_roots[waterlogged=true]")
                self.keepout.add((x, z))
        cx, cz = (x1 + x2) // 2, (z1 + z2) // 2
        self.p.marker(cx, y, cz, "trap", type="poison", radius=max(x2 - x1, z2 - z1) // 2 + 2, every=every, damage=damage)

    def vent(self, x, z, top=4):
        """A magma vent with a bubble column pulling players down."""
        self.p.set(x, 0, z, "minecraft:magma_block")
        for y in range(1, min(self.water, top) + 1):
            self.p.set(x, y, z, "minecraft:bubble_column[drag=true]")
        self.keepout.add((x, z))

    def lift(self, x, z, top):
        """A soul sand bubble column that lifts players up."""
        self.p.set(x, 0, z, "minecraft:soul_sand")
        for y in range(1, min(self.water, top) + 1):
            self.p.set(x, y, z, "minecraft:bubble_column[drag=false]")
        self.keepout.add((x, z))

    def cache(self, x1, z1, x2, z2, chest_at, facing, name, block="minecraft:sandstone"):
        """A hidden pocket closed by plain sandstone (break in), with a secret-tier chest and a 'secret' marker."""
        p = self.p
        self.fill(x1, 1, z1, x2, 3, z2, block)
        for x in range(x1 + 1, x2):
            for z in range(z1 + 1, z2):
                for y in (1, 2):
                    p.set(x, y, z, WATER)
        self.chest(chest_at[0], 1, chest_at[1], facing, "secret")
        mx, mz = (x1 + x2) // 2, (z1 + z2) // 2
        if (mx, mz) == chest_at:
            mx = x1 + 1 if chest_at[0] != x1 + 1 else x2 - 1
        p.marker(mx, 1, mz, "secret", name=name)
        self.keep(x1, z1, x2, z2)

    def finish(self, pack):
        self.weather()
        pack.add(self.p)
        return self.p


def set_cap(pack, name, jig, wall):
    p = Piece(f"{DT}/caps/{name}", 3, 5, 1)
    p.fill(0, 0, 0, 2, 4, 0, wall)
    p.set(1, 3, 0, "minecraft:sea_lantern")
    p.door("north", jig, jig, "minecraft:empty", at=1, final_state=wall)
    pack.add(p)
    return p


# =====================================================================================================================
# FLOOR 1 pieces
# =====================================================================================================================
def entrance(pack):
    r = Room("entrance", 19, 12, 17, roof=False)
    r.shell()
    p = r.p
    r.fill(5, 1, 1, 13, 1, 4, "minecraft:smooth_sandstone")
    for x in range(5, 14):
        r.put(x, 1, 5, "minecraft:sandstone_stairs[facing=north,half=bottom]")
    r.fill(7, 2, 1, 11, 2, 2, "minecraft:cut_sandstone")
    p.set(9, 3, 1, "skyrpg_world:waystone")
    for x in (7, 11):
        p.set(x, 3, 1, "minecraft:sea_lantern")
    for x in (6, 12):
        r.put(x, 2, 3, "minecraft:cut_sandstone_slab[type=bottom]")
    for x in (4, 14):
        r.col(x, 2, 1, 9)
        p.set(x, 9, 2, "minecraft:gold_block")
        p.set(x, 8, 2, "minecraft:sea_lantern")
    for z in (7, 10, 13):
        for x in (3, 15):
            r.col(x, z, 1, 10)
    r.keep(5, 1, 13, 5)
    r.door("south", "spine_out", pool=f"{DT}/f1a")
    r.keep(7, 6, 11, 15)
    r.scatter(14, 1, 5, 17, 15)
    for x, z in ((1, 15), (17, 15), (1, 6), (17, 6)):
        r.coral_mound(x, z)
    r.lights(y=6)
    p.marker(9, 2, 8, "dungeon", dungeon="skyrpg_world:drowned_temple")
    r.spawner(9, 1, 10, [acolyte(3, 26), lurker(2, 26)], radius=8)
    return r.finish(pack)


def hall_colonnade(pack, tag, out):
    r = Room(f"halls/colonnade_{tag}", 15, 10, 17)
    r.shell()
    p = r.p
    r.door("north", "spine_in")
    r.door("south", "spine_out", pool=out)
    r.door("west", "side_out")
    r.door("east", "side_out")
    for z in (3, 6, 10, 13):
        for x in (4, 10):
            r.col(x, z, 1, 8)
            p.set(x, 5, z, "minecraft:sea_lantern")
    for z in (3, 13):
        for x in (4, 10):
            r.kelp(x - 1, z, 5)
            r.kelp(x + 1, z, 4)
    p.fill(6, 0, 1, 8, 0, 15, "minecraft:smooth_sandstone")
    r.chest(1, 1, 14, "east")
    r.chest(13, 1, 2, "west")
    r.arrows(0, 2, 5, "east")
    r.arrows(14, 2, 11, "west", damage=6)
    r.spikes(7, 9)
    r.scatter(16, 1, 1, 13, 15)
    r.lights(y=6)
    r.spawner(7, 2, 8, waves=[[acolyte(3, 26), lurker(2, 26)], [lancer(3, 27), lurker(2, 28), sentinel(1, 28)]],
              radius=9, spread=5)
    return r.finish(pack)


def hall_straight(pack, tag, out, fl=1):
    r = Room(f"halls/straight{'' if fl == 1 else '2'}_{tag}", 11, 8, 15, fl=fl)
    r.shell()
    r.door("north", "spine_in")
    r.door("south", "spine_out", pool=out)
    r.door("west", "side_out")
    r.door("east", "side_out")
    for z in (3, 11):
        for x in (2, 8):
            r.col(x, z, 1, 6)
    for z in (5, 9):
        for x in (2, 8):
            r.put(x, 1, z, "minecraft:prismarine_brick_slab[type=bottom]" if fl == 1 else "minecraft:polished_blackstone_slab[type=bottom]")
            r.p.set(x, 2, z, "minecraft:sea_lantern" if fl == 1 else "minecraft:soul_lantern[hanging=false,waterlogged=true]")
    r.arrows(0, 2, 4, "east")
    r.arrows(10, 2, 10, "west")
    r.spikes(5, 7, 2)
    if fl == 2:
        r.floor_patch(3, "minecraft:smooth_basalt", 1, 1, 9, 13, 1)
    r.scatter(8, 1, 1, 9, 13)
    r.lights()
    r.spawner(5, 1, 8, std_pack(fl), radius=8)
    return r.finish(pack)


def hall_corner(pack, tag, out, fl=1):
    r = Room(f"halls/corner{'' if fl == 1 else '2'}_{tag}", 13, 8, 13, fl=fl)
    r.shell()
    r.door("north", "spine_in")
    r.door("east", "spine_out", pool=out)
    r.door("south", "side_out")
    r.door("west", "side_out")
    r.fill(5, 1, 5, 7, 1, 7, r.pal.accent)
    r.p.set(6, 2, 6, "minecraft:gold_block")
    r.p.set(6, 3, 6, "minecraft:sea_lantern")
    for dx, dz in ((-1, -1), (1, 1), (-1, 1), (1, -1)):
        r.put(6 + dx, 2, 6 + dz, "minecraft:prismarine_brick_stairs[facing=north,half=bottom]" if fl == 1
              else "minecraft:polished_blackstone_brick_stairs[facing=north,half=bottom]")
    r.keepout.add((6, 6))
    r.arrows(0, 2, 3, "east")
    r.spikes(3, 3)
    if fl == 2:
        r.vent(10, 10)
        r.vent(2, 10)
    r.scatter(9, 1, 1, 11, 11)
    r.lights()
    pk = [lurker(2, L(fl, 1)), acolyte(3, L(fl, 0))] + ([sentry(1, L(fl, 2))] if fl == 2 else [])
    r.spawner(6, 1, 9, pk, radius=8)
    return r.finish(pack)


def hall_shrine(pack, tag, out, fl=1):
    """Air pocket shrine: tall room, a dry ledge with a rare chest."""
    h = 12 if fl == 1 else 10
    wl = 6 if fl == 1 else 5
    r = Room(f"halls/shrine{'' if fl == 1 else '2'}_{tag}", 15, h, 15, fl=fl, water=wl)
    r.shell()
    p = r.p
    r.door("north", "spine_in")
    r.door("south", "spine_out", pool=out)
    r.door("west", "side_out")
    r.door("east", "side_out")
    r.fill(11, 1, 1, 13, wl - 1, 3, "minecraft:cut_sandstone" if fl == 1 else "minecraft:polished_blackstone")
    r.fill(11, wl, 1, 13, wl, 3, r.pal.accent)
    r.put(10, wl, 2, "minecraft:sandstone_stairs[facing=east,half=bottom]" if fl == 1 else "minecraft:polished_blackstone_stairs[facing=east,half=bottom]")
    r.put(10, wl - 1, 2, "minecraft:sandstone_stairs[facing=east,half=bottom]" if fl == 1 else "minecraft:polished_blackstone_stairs[facing=east,half=bottom]")
    r.chest(13, wl + 1, 2, "west", "rare")
    for x, z in ((11, 1), (13, 3)):
        p.set(x, wl + 1, z, "minecraft:sea_lantern")
    r.keep(10, 1, 13, 3)
    for x, z in ((4, 4), (10, 10), (4, 10)):
        r.col(x, z, 1, h - 2)
    r.fill(6, 1, 6, 8, wl, 8, "minecraft:cut_sandstone" if fl == 1 else "minecraft:polished_blackstone")
    p.set(7, wl, 7, "minecraft:gold_block")
    p.set(7, wl + 1, 7, "minecraft:sea_lantern")
    r.keep(6, 6, 8, 8)
    for x in range(2, 14, 4):
        for z in range(2, 14, 4):
            p.set(x, h - 1, z, "minecraft:sea_lantern")
    r.arrows(0, 2, 3, "east")
    r.arrows(14, 2, 11, "west")
    r.spikes(3, 11)
    r.scatter(12, 1, 1, 13, 13)
    r.lights(y=5)
    r.spawner(7, 2, 10, std_pack(fl) if fl == 2 else [acolyte(3, 26), lurker(1, 27), sentry(1, 27)], radius=9)
    return r.finish(pack)


def hall_guardian(pack, tag, out, fl=1):
    """Elite hall: Tidebound Sentinels and guardians. The south gate is barred until the pack is dead."""
    r = Room(f"halls/guardian{'' if fl == 1 else '2'}_{tag}", 17, 10, 17, fl=fl)
    r.shell()
    p = r.p
    r.door("north", "spine_in")
    r.door("south", "spine_out", pool=out)
    for x in (7, 8, 9):
        for y in (1, 2, 3):
            r.put(x, y, 15, "minecraft:iron_bars")
    p.marker(8, 2, 15, "door", group="guard", message="The gate grinds open.")
    r.keep(6, 13, 10, 15)
    r.fill(6, 1, 6, 10, 1, 10, r.pal.accent)
    r.fill(7, 2, 7, 9, 2, 9, r.pal.band if fl == 1 else "minecraft:polished_blackstone")
    p.set(8, 3, 8, "minecraft:gold_block")
    r.keep(6, 6, 10, 10)
    for z in (3, 5, 11, 13):
        for x in (2, 14):
            r.col(x, z, 1, 8)
            p.set(x, 6, z, "minecraft:sea_lantern")
    r.chest(1, 1, 8, "east", "rare")
    r.chest(15, 1, 8, "west", "rare")
    r.arrows(0, 2, 4, "east", damage=6)
    r.arrows(16, 2, 12, "west", damage=6)
    r.spikes(8, 5, 1)
    r.scatter(10, 1, 1, 15, 15, kinds=("kelp", "coral", "pickle", "coral") if fl == 1 else ("coral", "coral", "kelp"))
    r.lights(y=6)
    lv = lambda d: L(fl, d)  # noqa: E731
    r.spawner(8, 2, 11, waves=[
        [lancer(3, lv(1)), guardian(2, lv(2))] + ([sentry(1, lv(2))] if fl == 2 else []),
        [sentinel(2, lv(4)), lancer(3, lv(2)), guardian(1, lv(4), "Elder Reef Warden", elite=True)]],
        radius=11, spread=5, group="guard")
    return r.finish(pack)


def hall_cross(pack, tag, out):
    r = Room(f"halls/cross_{tag}", 15, 9, 15)
    r.shell()
    p = r.p
    r.door("north", "spine_in")
    r.door("south", "spine_out", pool=out)
    r.door("west", "side_out")
    r.door("east", "side_out")
    r.fill(5, 1, 5, 9, 1, 9, "minecraft:smooth_sandstone")
    for x in range(6, 9):
        for z in range(6, 9):
            r.coral_mound(x, z, y=2)
    p.set(7, 4, 7, "minecraft:sea_lantern")
    r.keep(5, 5, 9, 9)
    for x, z in ((2, 2), (12, 2), (2, 12), (12, 12)):
        r.col(x, z, 1, 7)
    r.cache(11, 1, 14, 3, (13, 2), "west", "The Coral Hierophant's Niche")
    r.arrows(0, 2, 4, "east")
    r.arrows(14, 2, 11, "west")
    r.spikes(4, 11)
    r.scatter(12, 1, 1, 13, 13)
    r.lights()
    r.spawner(7, 1, 4, [lurker(2, 28), lancer(2, 27), sentinel(1, 29)], radius=9)
    return r.finish(pack)


def room_nave(pack, fl=1):
    """A grand nave: two rows of columns, a dais and a lot of company."""
    if fl == 1:
        w, h, d, wl = 25, 13, 31, 9
    else:
        w, h, d, wl = 27, 10, 31, None
    r = Room(f"rooms/{'grand_nave' if fl == 1 else 'abyss_nave'}", w, h, d, fl=fl, water=wl)
    r.shell()
    p = r.p
    cx = w // 2
    leaf = F1_LEAF if fl == 1 else F2_LEAF
    r.door("north", "side_in")
    r.door("south", "side_out", pool=leaf)
    r.door("west", "side_out", at=10, pool=leaf)
    r.door("east", "side_out", at=20, pool=leaf)
    top = h - 3
    for z in (5, 10, 15, 20, 25):
        for x in (cx - 6, cx + 6):
            r.col(x, z, 1, top)
            p.set(x, 6, z, "minecraft:sea_lantern" if fl == 1 else "minecraft:soul_lantern[hanging=false,waterlogged=true]")
    # central dais
    r.fill(cx - 2, 1, 13, cx + 2, 1, 17, r.pal.accent)
    r.fill(cx - 1, 2, 14, cx + 1, 2, 16, r.pal.band if fl == 1 else "minecraft:polished_blackstone")
    p.set(cx, 3, 15, "minecraft:gold_block")
    r.chest(cx, 4, 15, "north", "rare")
    r.keep(cx - 2, 13, cx + 2, 17)
    r.keep(cx - 2, 1, cx + 2, 9)
    r.keep(cx - 2, 21, cx + 2, 29)
    if fl == 1:
        r.cache(1, 1, 4, 3, (2, 2), "east", "The Priest-King's Alcove")
        for x in range(4, w - 4, 4):
            for z in (6, 16, 26):
                p.set(x, h - 1, z, "minecraft:sea_lantern")
        r.chest(w - 2, 1, d - 2, "west")
        r.chest(1, 1, d - 2, "east")
    else:
        r.cache(w - 5, 1, w - 2, 3, (w - 3, 2), "west", "The Sunken Choirmaster's Vault")
        r.chest(w - 2, 1, d - 2, "west")
        r.chest(1, 1, d - 2, "east")
        for z in (8, 14, 20):
            r.vent(4, z)
            r.vent(w - 5, z)
        for x in range(4, w - 4, 6):
            p.set(x, h - 1, 15, "minecraft:sea_lantern")
    r.arrows(0, 2, 6, "east", damage=6)
    r.arrows(w - 1, 2, 25, "west", damage=6)
    r.arrows(0, 2, 27, "east", damage=6)
    r.spikes(cx, 7, 1)
    r.spikes(cx, 24, 1)
    r.scatter(24, 1, 1, w - 2, d - 2)
    r.lights(y=6)
    lv = lambda d_: L(fl, d_)  # noqa: E731
    if fl == 1:
        waves = [[acolyte(4, lv(0)), lurker(2, lv(1))], [lancer(3, lv(2)), sentinel(2, lv(4)), guardian(2, lv(3))]]
    else:
        waves = [[acolyte(3, lv(1)), sentry(3, lv(2))], [lancer(3, lv(3)), lurker(3, lv(3)), sentinel(2, lv(4))]]
    r.spawner(cx, 2, 15, waves=waves, radius=12, spread=6)
    return r.finish(pack)


def hall_champion(pack):
    """Champion hall: a flooded 21x12x21 arena with an air pocket; the south portcullis opens when the champion falls."""
    w, h, d, wl = 21, 12, 21, 7
    r = Room("halls/champion", w, h, d, water=wl, dark=False)
    r.shell()
    p = r.p
    r.door("north", "spine_in")
    r.door("south", "spine_out", pool=f"{DT}/descent")
    for x in (9, 10, 11):
        for y in (1, 2, 3):
            p.set(x, y, 19, "minecraft:dark_prismarine")
    p.marker(10, 2, 19, "door", group="champion", message="The portcullis rises.")
    r.keep(8, 16, 12, 19)
    # four corner ledges (tops at the water line), two with rare chests
    for (x1, z1, ch) in ((1, 1, True), (17, 1, True), (1, 17, False), (17, 17, False)):
        r.fill(x1, 1, z1, x1 + 2, wl - 1, z1 + 2, "minecraft:cut_sandstone")
        r.fill(x1, wl, z1, x1 + 2, wl, z1 + 2, "minecraft:smooth_sandstone")
        r.keep(x1, z1, x1 + 2, z1 + 2)
        p.set(x1 + 1, wl + 1, z1 + 1, "minecraft:sea_lantern")
    r.chest(2, wl + 1, 1, "south", "rare")
    r.chest(18, wl + 1, 1, "south", "rare")
    # central dais and a ring of pillars
    r.fill(7, 1, 7, 13, 1, 13, "minecraft:smooth_sandstone")
    r.fill(8, 2, 8, 12, 2, 12, "minecraft:cut_sandstone")
    p.set(10, 3, 10, "minecraft:gold_block")
    r.keep(7, 7, 13, 13)
    for x, z in ((5, 5), (15, 5), (5, 15), (15, 15), (5, 10), (15, 10)):
        r.col(x, z, 1, 10)
        p.set(x, 6, z, "minecraft:sea_lantern")
    for x in (4, 10, 16):
        p.set(x, h - 1, 4, "minecraft:sea_lantern")
        p.set(x, h - 1, 16, "minecraft:sea_lantern")
    r.arrows(0, 2, 6, "east", damage=7)
    r.arrows(20, 2, 14, "west", damage=7)
    r.spikes(5, 8)
    r.spikes(15, 12)
    r.scatter(14, 1, 1, 19, 19, kinds=("kelp", "coral", "pickle"))
    r.lights(y=6)
    p.marker(10, 2, 11, "boss", boss="skyrpg_world:temple_champion", radius=14, group="champion")
    return r.finish(pack)


def descent(pack):
    """A flooded ramp down 10 blocks: enters high (local y=11) through the north wall, leaves low (local y=1)
    through the EAST wall, so floor 2 runs perpendicular to floor 1."""
    w, h, d = 23, 19, 15
    r = Room("descent", w, h, d, dark=False)
    r.shell()
    p = r.p
    for x in range(w):
        for z in range(d):
            if x in (0, w - 1) or z in (0, d - 1):
                p.set(x, 14, z, r.pal.band)
    r.door("north", "spine_in", at=3, y=11)
    r.door("east", "spine_out", pool=f"{DT}/f2a", at=7)

    def top(x):
        return 10 if x <= 7 else max(0, 10 - ((x - 7) * 10) // 11)

    for x in range(1, w - 1):
        t = top(x)
        if t >= 1:
            p.fill(x, 1, 1, x, t, d - 2, "minecraft:smooth_sandstone" if x % 2 else "minecraft:cut_sandstone")
    for x in (10, 14):
        for z in (2, 12):
            r.col(x, z, top(x) + 1, top(x) + 8)
            p.set(x, top(x) + 4, z, "minecraft:sea_lantern")
    for x in range(9, 19, 3):
        p.set(x, top(x) + 1, 1, "minecraft:sea_lantern")
        p.set(x, top(x) + 1, d - 2, "minecraft:sea_lantern")
    for y in (14, 15):
        p.set(3, y, 1, "minecraft:gold_block")
    r.chest(20, 1, 12, "west")
    r.arrows(11, top(11) + 3, 0, "south", damage=6)
    r.arrows(15, top(15) + 3, 14, "north", damage=6)
    r.spikes(14, 7, 1, y=top(14) + 1)
    r.lights(y=8, step=6)
    r.keepout |= {(x, z) for x in range(1, 22) for z in range(5, 10)}
    r.spawner(12, top(12) + 1, 7, [lurker(3, 29), sentinel(1, 29), acolyte(2, 29)], radius=10, spread=3)
    return r.finish(pack)


# -- floor 1 side rooms -----------------------------------------------------------------------------------------------
def room_pressure(pack):
    """Three pressure plates open a dark prismarine door into a treasury."""
    r = Room("rooms/pressure_room", 15, 9, 15, dark=False)
    r.shell()
    p = r.p
    r.door("north", "side_in")
    r.fill(4, 1, 10, 10, 6, 13, SAND)
    r.fill(5, 1, 11, 9, 6, 13, WATER)
    for x in (6, 7, 8):
        for y in (1, 2, 3):
            p.set(x, y, 10, "minecraft:dark_prismarine")
    p.marker(7, 2, 10, "door", group="pressure", message="The sealed door sinks into the floor.")
    r.chest(6, 1, 13, "north", "rare")
    r.chest(8, 1, 13, "north", "rare")
    p.set(5, 1, 13, "minecraft:gold_block")
    p.set(9, 1, 13, "minecraft:gold_block")
    p.set(7, 6, 12, "minecraft:sea_lantern")
    for x, z in ((2, 4), (12, 4), (7, 7)):
        r.fill(x - 1, 0, z - 1, x + 1, 0, z + 1, SAND)
        p.set(x, 1, z, "minecraft:heavy_weighted_pressure_plate")
        p.marker(x, 1, z, "plate", group="pressure")
        for dx, dz in ((-1, -1), (1, 1), (-1, 1), (1, -1)):
            r.pickles(x + dx, z + dz)
    p.sign(5, 2, 9, ["Three plates.", "One door.", "The tide holds", "the hoard."],
           block="minecraft:dark_oak_wall_sign[facing=north,waterlogged=true]", glow=True)
    r.keep(1, 9, 13, 13)
    r.arrows(0, 2, 8, "east")
    r.arrows(14, 2, 8, "west")
    r.scatter(8, 1, 1, 13, 8, kinds=("kelp", "coral"))
    r.lights()
    r.spawner(7, 2, 4, [acolyte(3, 26), lurker(1, 26), sentry(1, 27)], radius=8)
    return r.finish(pack)


def room_archive(pack):
    r = Room("rooms/archive", 17, 9, 15)
    r.shell()
    p = r.p
    rng = r.rng
    r.door("north", "side_in")
    r.door("south", "side_out", pool=F1_LEAF)
    for z in (4, 7, 10):
        for x0 in (2, 11):
            for x in range(x0, x0 + 4):
                r.put(x, 1, z, SAND)
                for y in (2, 3):
                    if rng.random() > 0.28:
                        p.set(x, y, z, "minecraft:bookshelf")
                if rng.random() > 0.35:
                    r.put(x, 4, z, "minecraft:sandstone_slab[type=bottom]")
    for x, z in ((6, 5), (10, 8), (5, 8), (11, 3), (4, 12)):
        r.put(x, 1, z, rng.choice(["minecraft:sandstone_stairs[facing=east,half=bottom]",
                                   "minecraft:cut_sandstone_slab[type=bottom]", "minecraft:sandstone"]))
    r.cache(12, 10, 15, 13, (14, 12), "west", "The Drowned Scribe's Cache")
    r.keep(7, 1, 9, 13)
    r.arrows(0, 2, 6, "east")
    r.arrows(16, 2, 4, "west")
    r.scatter(8, 1, 1, 15, 13, kinds=("kelp", "pickle", "coral"))
    r.lights()
    r.spawner(8, 1, 8, [shellbearer(3, 27), lurker(1, 27), sentry(1, 27)], radius=9)
    return r.finish(pack)


def room_side_hall(pack, fl=1):
    r = Room(f"rooms/side_hall{'' if fl == 1 else '2'}", 11, 7, 13, fl=fl)
    r.shell()
    r.door("north", "side_in")
    r.door("south", "side_out", pool=F1_LEAF if fl == 1 else F2_LEAF)
    r.door("east", "side_out", pool=F1_LEAF if fl == 1 else F2_LEAF)
    for z in (3, 9):
        for x in (2, 8):
            r.col(x, z, 1, 5)
    r.arrows(0, 2, 6, "east")
    r.scatter(7, 1, 1, 9, 11)
    r.lights(y=5)
    r.spawner(5, 1, 6, [acolyte(3, L(fl, 0)), lurker(1, L(fl, 0))] + ([sentry(1, L(fl, 1))] if fl == 2 else []), radius=7)
    return r.finish(pack)


def room_kelp_garden(pack):
    """Kelp Forest Garden: a drowned grove, thick with kelp. Lurkers ambush from it."""
    r = Room("rooms/kelp_garden", 21, 10, 21)
    r.shell()
    p = r.p
    r.door("north", "side_in")
    r.door("west", "side_out", pool=F1_LEAF)
    r.door("east", "side_out", pool=F1_LEAF)
    r.fill(1, 0, 1, 19, 0, 19, "minecraft:smooth_sandstone")
    # a sunken shrine ring in the middle
    r.fill(8, 1, 8, 12, 1, 12, "minecraft:cut_sandstone")
    r.fill(9, 2, 9, 11, 2, 11, "minecraft:smooth_sandstone")
    p.set(10, 3, 10, "minecraft:gold_block")
    p.set(10, 4, 10, "minecraft:sea_lantern")
    r.keep(8, 8, 12, 12)
    r.keep(8, 1, 12, 7)     # north lane
    r.keep(1, 8, 8, 12)
    r.keep(12, 8, 19, 12)
    r.chest(2, 1, 18, "east")
    r.chest(18, 1, 17, "west", "rare")
    r.spikes(5, 15, 2)
    r.spikes(15, 14, 1)
    r.poison(14, 15, 17, 18)
    r.arrows(0, 2, 5, "east")
    r.arrows(20, 2, 4, "west")
    r.arrows(10, 2, 20, "north")
    for x, z in ((1, 1), (19, 1), (1, 19), (19, 19)):
        r.coral_mound(x, z)
    r.scatter(110, 1, 1, 19, 19, kinds=("kelp", "kelp", "kelp", "kelp", "pickle", "coral"), kelp_h=(4, 8))
    r.lights(y=6)
    r.spawner(10, 1, 14, waves=[[lurker(4, 28, "Kelp Lurker")], [acolyte(3, 27), lancer(2, 28), sentinel(1, 29)]],
              radius=10, spread=5)
    return r.finish(pack)


def room_shipwreck(pack):
    """Shipwreck Hold: a whole sunken ship's hull inside the temple. Chests in the hold, the captain's on the stern."""
    r = Room("rooms/shipwreck_hold", 21, 11, 21)
    r.shell()
    p = r.p
    r.door("north", "side_in")
    r.door("south", "side_out", pool=F1_LEAF)
    wood, log = "minecraft:dark_oak_planks", "minecraft:dark_oak_log[axis=y]"
    r.keep(8, 1, 12, 4)
    r.keep(8, 16, 12, 19)
    # keel and hull (x 5..15, z 6..15)
    z0, z1 = 6, 15
    r.fill(6, 1, z0, 14, 1, z1, wood)
    for z in range(z0, z1 + 1):
        for y in range(2, 7):
            for x in (5, 15):
                if z0 + 4 <= z <= z0 + 6 and y <= 4:
                    continue          # gaps to get in
                p.set(x, y, z, log if z in (z0, z0 + 3, z0 + 7, z1) else wood)
    for x in range(5, 16):
        for y in (2, 3, 4, 5):
            p.set(x, y, z0, wood)
            p.set(x, y, z1, wood)
    for y in range(2, 5):       # the prow is a hole in the planks
        p.set(10, y, z0, WATER)
    # decks fore and aft, mast and sail
    for z in list(range(z0, z0 + 3)) + list(range(z1 - 2, z1 + 1)):
        for x in range(6, 15):
            p.set(x, 6, z, wood)
    for y in range(2, 10):
        r.put(10, y, z0 + 4, "minecraft:dark_oak_fence")
    for x in range(8, 13):
        for y in range(5, 9):
            if x != 10:
                p.set(x, y, z0 + 5, "minecraft:white_wool")
    for y in (3, 4):
        r.put(7, y, z0 + 4, "minecraft:dark_oak_slab[type=bottom]")
    r.chest(7, 2, z1 - 2, "east")
    r.chest(13, 2, z0 + 2, "west")
    r.chest(10, 7, z1 - 1, "north", "rare")
    r.keep(5, z0, 15, z1)
    r.cache(16, 15, 19, 18, (18, 17), "west", "The Smuggler's Hidden Hold")
    r.arrows(0, 2, 9, "east")
    r.arrows(20, 2, 9, "west")
    r.spikes(3, 16)
    r.spikes(17, 6)
    r.scatter(14, 1, 1, 19, 19, kinds=("kelp", "coral", "pickle"))
    r.lights(y=6)
    r.spawner(10, 2, 10, waves=[[drowned(4, 27, "Drowned Deckhand", mainhand="minecraft:iron_axe"), lancer(2, 27)],
                                [drowned(1, 29, "Drowned Captain", elite=True, mainhand="minecraft:trident", head="minecraft:golden_helmet"),
                                 lurker(3, 28)]], radius=10, spread=3)
    return r.finish(pack)


def room_reliquary(pack):
    """Coral Reliquary: a coral altar with a rare chest, guarded in two waves; a secret behind coral."""
    r = Room("rooms/coral_reliquary", 15, 10, 17)
    r.shell()
    p = r.p
    r.door("north", "side_in")
    r.door("south", "side_out", pool=F1_LEAF)
    r.fill(5, 1, 6, 9, 1, 8, "minecraft:smooth_sandstone")
    r.fill(6, 2, 6, 8, 2, 8, "minecraft:cut_sandstone")
    p.set(7, 3, 7, "minecraft:gold_block")
    r.chest(7, 4, 7, "north", "rare")
    for dx, dz in ((-1, -1), (1, 1), (-1, 1), (1, -1)):
        r.put(7 + dx, 3, 7 + dz, "minecraft:sea_lantern")
    r.keep(5, 6, 9, 8)
    for x, z in ((3, 4), (11, 4), (3, 9), (11, 9)):
        for i, c in enumerate(CORALS[:4]):
            p.set(x, 1 + i, z, f"minecraft:{c}_coral_block")
        p.set(x, 5, z, "minecraft:sea_lantern")
        r.keepout.add((x, z))
    r.cache(1, 12, 4, 14, (2, 13), "east", "The Reliquarian's Reef-Hoard", block="minecraft:brain_coral_block")
    r.arrows(0, 2, 10, "east")
    r.arrows(14, 2, 5, "west")
    r.spikes(3, 6)
    r.scatter(12, 1, 1, 13, 15, kinds=("coral", "coral", "kelp", "pickle"))
    r.lights()
    r.spawner(7, 1, 11, waves=[[shellbearer(3, 28, "Drowned Reliquarian"), lurker(2, 27)],
                               [sentinel(1, 30), guardian(3, 29)]], radius=9, spread=4)
    return r.finish(pack)


def room_barracks(pack):
    """Drowned Barracks: bunks, a long table, three waves."""
    r = Room("rooms/drowned_barracks", 19, 9, 15)
    r.shell()
    p = r.p
    r.door("north", "side_in")
    r.door("south", "side_out", pool=F1_LEAF)
    for z in (3, 5, 7, 9, 11):
        for x0 in (1, 16):
            r.fill(x0, 1, z, x0 + 1, 1, z, "minecraft:dark_oak_slab[type=bottom]")
            r.fill(x0, 3, z, x0 + 1, 3, z, "minecraft:dark_oak_slab[type=bottom]")
            r.put(x0, 2, z, "minecraft:dark_oak_planks")
    r.fill(8, 1, 6, 10, 1, 8, "minecraft:spruce_slab[type=bottom]")
    r.keep(1, 3, 2, 11)
    r.keep(16, 3, 17, 11)
    r.keep(8, 6, 10, 8)
    r.chest(17, 1, 13, "west")
    r.chest(1, 1, 13, "east", "rare")
    r.arrows(3, 2, 0, "south")
    r.arrows(15, 2, 0, "south")
    r.arrows(5, 2, 14, "north", damage=6)
    r.spikes(6, 7, 1)
    r.spikes(12, 7, 1)
    r.scatter(8, 3, 1, 15, 13, kinds=("kelp", "pickle", "coral"))
    r.lights()
    r.spawner(9, 1, 5, waves=[[acolyte(4, 27)], [lancer(3, 28), sentry(2, 28)], [sentinel(2, 30), lurker(3, 29)]],
              radius=10, spread=4)
    return r.finish(pack)


def room_tide_gauge(pack):
    """Tide Gauge: three tide pillars show High / Low / High; match the levers to open the barred treasury."""
    r = Room("rooms/tide_gauge", 17, 10, 15, dark=False)
    r.shell()
    p = r.p
    r.door("north", "side_in")
    # treasury behind iron bars
    r.fill(6, 1, 11, 10, 4, 13, SAND)
    r.fill(7, 1, 12, 9, 3, 13, WATER)
    for x in (7, 8, 9):
        for y in (1, 2, 3):
            r.put(x, y, 11, "minecraft:iron_bars")
    p.marker(8, 2, 11, "door", group="gauge", message="The bars sink into the deep.")
    r.chest(7, 1, 13, "north", "rare")
    r.chest(9, 1, 13, "north", "rare")
    p.set(8, 1, 13, "minecraft:gold_block")
    r.keep(5, 10, 11, 13)
    wants = (True, False, True)
    for x, want in zip((4, 8, 12), wants):
        for y in range(1, 8):
            p.set(x, y, 5, "minecraft:prismarine_bricks")
        p.set(x, 7 if want else 3, 5, "minecraft:sea_lantern")
        p.set(x, 8, 5, "minecraft:gold_block")
        p.set(x, 1, 8, "minecraft:prismarine_bricks")
        p.set(x, 2, 8, "minecraft:lever[face=floor,facing=north,powered=false]")
        p.marker(x, 2, 8, "lever", group="gauge", want=want)
        r.keepout |= {(x, 5), (x, 8)}
    p.sign(14, 2, 1, ["Tide gauges:", "High, low, high.", "Lever up = high."],
           block="minecraft:dark_oak_wall_sign[facing=south,waterlogged=true]", glow=True)
    r.arrows(0, 2, 8, "east")
    r.arrows(16, 2, 6, "west")
    r.spikes(3, 11, 1)
    r.spikes(13, 11, 1)
    r.scatter(8, 1, 1, 15, 9, kinds=("kelp", "coral"))
    r.lights()
    r.spawner(8, 1, 6, [acolyte(3, 27), lurker(2, 27)], radius=8)
    return r.finish(pack)


# -- floor 1 leaf rooms -----------------------------------------------------------------------------------------------
def leaf_priest_cell(pack):
    r = Room("leaf/priest_cell", 9, 6, 9)
    r.shell()
    r.door("north", "side_in")
    r.col(2, 3, 1, 4)
    r.col(6, 3, 1, 4)
    r.chest(4, 1, 7, "north")
    r.arrows(0, 2, 4, "east")
    r.scatter(3, 1, 1, 7, 7, kinds=("coral", "pickle"))
    r.lights(y=4, step=4)
    r.spawner(4, 1, 4, [acolyte(4, 27)], radius=6, spread=2)
    return r.finish(pack)


def leaf_closet(pack):
    r = Room("leaf/reliquary_closet", 9, 7, 11)
    r.shell()
    r.door("north", "side_in")
    r.fill(3, 1, 8, 5, 1, 9, "minecraft:smooth_sandstone")
    r.chest(4, 2, 9, "north", "rare")
    r.keep(3, 8, 5, 9)
    r.arrows(8, 2, 6, "west", damage=6)
    r.spikes(4, 5, 1)
    r.lights(y=5)
    r.spawner(4, 1, 5, [lurker(2, 28), lancer(2, 28)], radius=7, spread=2)
    return r.finish(pack)


def leaf_sentry_post(pack):
    r = Room("leaf/sentry_post", 11, 7, 9)
    r.shell()
    r.door("north", "side_in")
    r.col(2, 4, 1, 5)
    r.col(8, 4, 1, 5)
    r.chest(5, 1, 7, "north")
    r.arrows(10, 2, 3, "west")
    r.scatter(4, 1, 1, 9, 7, kinds=("kelp", "coral"))
    r.lights(y=5)
    r.spawner(5, 1, 4, [sentry(3, 28), acolyte(2, 27)], radius=7, spread=2)
    return r.finish(pack)


# =====================================================================================================================
# FLOOR 2 pieces
# =====================================================================================================================
def hall_vent_gallery(pack, tag, out):
    r = Room(f"halls/vent_gallery_{tag}", 17, 9, 17, fl=2)
    r.shell()
    p = r.p
    r.door("north", "spine_in")
    r.door("south", "spine_out", pool=out)
    r.door("west", "side_out")
    r.door("east", "side_out")
    p.fill(7, 0, 1, 9, 0, 15, "minecraft:smooth_basalt")
    r.keep(7, 1, 9, 15)
    for x, z in ((3, 4), (13, 4), (3, 12), (13, 12)):
        r.vent(x, z)
    for x, z in ((5, 6), (11, 6), (5, 10), (11, 10)):
        r.col(x, z, 1, 7)
    r.chest(1, 1, 15, "east")
    r.chest(15, 1, 1, "west", "rare")
    r.arrows(0, 2, 5, "east", damage=6)
    r.arrows(16, 2, 12, "west", damage=6)
    r.spikes(8, 6, 1)
    r.spikes(8, 11, 1)
    r.scatter(10, 1, 1, 15, 15)
    r.lights(y=5)
    r.spawner(8, 1, 8, waves=[[sentry(2, 30), acolyte(3, 30)], [lurker(3, 31), sentinel(2, 33)]], radius=10, spread=5)
    return r.finish(pack)


def hall_sculk(pack, tag, out):
    r = Room(f"halls/sculk_hall_{tag}", 15, 9, 17, fl=2)
    r.shell()
    p = r.p
    r.door("north", "spine_in")
    r.door("south", "spine_out", pool=out)
    r.door("west", "side_out")
    r.door("east", "side_out")
    r.floor_patch(9, "minecraft:sculk", 1, 1, 13, 15, 2)
    for x, z in ((3, 5), (11, 5), (3, 12), (11, 12), (7, 8)):
        p.set(x, 1, z, "minecraft:sculk_catalyst")
        r.keepout.add((x, z))
    for x, z in ((3, 3), (11, 3), (3, 14), (11, 14), (5, 7), (9, 10)):
        r.put(x, 1, z, "minecraft:sculk_sensor")
        r.keepout.add((x, z))
    for z in (4, 11):
        for x in (3, 11):
            r.col(x, z, 1, 6)
    for x, z in ((6, 6), (8, 10)):
        r.put(x, 7, z, "minecraft:soul_lantern[hanging=true]")
    r.cache(1, 1, 4, 3, (2, 2), "east", "The Sculk-Choked Ossuary")
    r.chest(13, 1, 15, "west")
    r.chest(1, 1, 15, "east")
    r.poison(10, 11, 13, 14)
    r.arrows(0, 2, 6, "east", damage=6)
    r.arrows(14, 2, 10, "west", damage=6)
    r.spikes(7, 6, 1)
    r.scatter(8, 1, 1, 13, 15, kinds=("coral", "coral", "kelp"))
    r.lights(y=5)
    r.spawner(7, 1, 8, [sentry(3, 31), acolyte(2, 30), lurker(2, 31)], radius=9, spread=4)
    return r.finish(pack)


def room_eel_tunnels(pack):
    """Eel Tunnels: a serpentine of low tunnels; arrow slots and spikes at the bends, a rare chest at the end."""
    r = Room("rooms/eel_tunnels", 21, 8, 21, fl=2)
    r.shell()
    p = r.p
    r.door("north", "side_in")
    wall = "minecraft:blackstone"
    r.keep(8, 1, 12, 4)
    for x in range(1, 16):               # baffle A: gap east
        for y in range(1, 7):
            p.set(x, y, 5, wall)
    for x in range(5, 20):               # baffle B: gap west
        for y in range(1, 7):
            p.set(x, y, 10, wall)
    for x in range(1, 16):               # baffle C: gap east
        for y in range(1, 7):
            p.set(x, y, 15, wall)
    p.marker(15, 2, 5, "trap", type="arrows", radius=9, every=40, damage=6)
    p.marker(5, 2, 10, "trap", type="arrows", radius=9, every=40, damage=6)
    p.marker(15, 2, 15, "trap", type="arrows", radius=9, every=40, damage=6)
    p.set(15, 2, 5, "minecraft:dispenser[facing=east]")
    p.set(5, 2, 10, "minecraft:dispenser[facing=west]")
    p.set(15, 2, 15, "minecraft:dispenser[facing=east]")
    r.spikes(18, 7, 1)
    r.spikes(3, 12, 1)
    r.poison(2, 17, 5, 19)
    r.chest(18, 1, 19, "west", "rare")
    r.chest(19, 1, 17, "west", "rare")
    r.chest(2, 1, 8, "east")
    r.vent(10, 18)
    r.vent(12, 18)
    r.lights(y=5, step=6)
    for x, z in ((3, 2), (17, 2), (10, 12), (10, 7)):
        p.set(x, 6, z, "minecraft:sea_lantern") if p.get(x, 6, z) in (WATER, wall) else None
    r.spawner(10, 1, 7, waves=[[lurker(4, 31, "Abyssal Eel")], [lurker(3, 32, "Abyssal Eel"), sentinel(1, 33), sentry(2, 32)]],
              radius=9, spread=4)
    return r.finish(pack)


def room_bubble_elevator(pack):
    """Bubble Elevator: ride the soul-sand column up into the air pocket to reach a rare chest."""
    r = Room("rooms/bubble_elevator", 13, 16, 13, fl=2, water=12)
    r.shell()
    p = r.p
    r.door("north", "side_in")
    for x in range(5, 8):
        for z in range(5, 8):
            r.lift(x, z, 12)
    r.fill(9, 1, 5, 11, 11, 7, "minecraft:polished_blackstone")
    r.fill(9, 12, 5, 11, 12, 7, "minecraft:gilded_blackstone")
    r.chest(10, 13, 6, "west", "rare")
    p.set(11, 13, 5, "minecraft:sea_lantern")
    p.set(11, 13, 7, "minecraft:sea_lantern")
    r.keep(5, 5, 11, 7)
    r.keep(4, 1, 8, 4)
    for x, z in ((2, 9), (10, 10), (2, 3), (10, 2)):
        r.col(x, z, 1, 13)
    for x in (3, 9):
        for z in (3, 9):
            p.set(x, 14, z, "minecraft:sea_lantern")
    r.arrows(0, 2, 6, "east", damage=6)
    r.arrows(12, 2, 10, "west", damage=6)
    r.spikes(3, 8, 1)
    r.spikes(9, 10, 1)
    r.scatter(10, 1, 1, 11, 11)
    r.lights(y=6, step=6)
    r.spawner(6, 1, 9, [sentinel(2, 32), lurker(2, 31), sentry(2, 31)], radius=9, spread=3)
    return r.finish(pack)


def room_pearl_treasury(pack):
    """Pearl Treasury: pull the three levers in the right order of states to open the barred pearl vault."""
    r = Room("rooms/pearl_treasury", 17, 9, 17, fl=2)
    r.shell()
    p = r.p
    r.door("north", "side_in")
    r.fill(5, 1, 12, 11, 4, 15, SAND)
    r.fill(6, 1, 13, 10, 3, 15, WATER)
    for x in (7, 8, 9):
        for y in (1, 2, 3):
            r.put(x, y, 12, "minecraft:iron_bars")
    p.marker(8, 2, 12, "door", group="pearls", message="The pearl-vault bars slide away.")
    r.chest(6, 1, 15, "north", "rare")
    r.chest(8, 1, 15, "north", "rare")
    r.chest(10, 1, 15, "north", "rare")
    for x in (7, 9):
        p.set(x, 1, 15, "minecraft:gold_block")
    p.set(8, 3, 14, "minecraft:sea_lantern")
    r.keep(5, 11, 11, 15)
    for z, want in zip((4, 6, 8), (False, True, True)):
        p.set(1, 2, z, "minecraft:lever[face=wall,facing=east,powered=false]")
        p.marker(1, 2, z, "lever", group="pearls", want=want)
        r.keepout.add((1, z))
    p.sign(1, 4, 6, ["Out. In. In.", "Lever up = in."], block="minecraft:dark_oak_wall_sign[facing=east,waterlogged=true]", glow=True)
    for x, z in ((13, 5), (13, 9), (4, 10)):
        r.col(x, z, 1, 6)
    r.arrows(16, 2, 4, "west", damage=6)
    r.arrows(16, 2, 9, "west", damage=6)
    r.spikes(9, 8, 1)
    r.spikes(13, 12, 1)
    r.scatter(6, 1, 1, 15, 9)
    r.lights(y=5)
    r.spawner(10, 1, 6, [sentry(3, 32), sentinel(2, 33), lurker(2, 32)], radius=9, spread=3)
    return r.finish(pack)


def room_vent_chamber(pack):
    r = Room("rooms/vent_chamber", 19, 9, 19, fl=2)
    r.shell()
    p = r.p
    r.door("north", "side_in")
    r.door("south", "side_out", pool=F2_LEAF)
    r.door("east", "side_out", pool=F2_LEAF)
    for x, z in ((6, 6), (12, 6), (6, 12), (12, 12), (9, 9)):
        r.vent(x, z, top=5)
        for dx, dz in ((-1, 0), (1, 0), (0, -1), (0, 1)):
            if p.get(x + dx, 0, z + dz) == r.pal.wall:
                p.set(x + dx, 0, z + dz, "minecraft:smooth_basalt")
    r.keep(8, 1, 10, 4)
    r.chest(1, 1, 17, "east", "rare")
    r.chest(17, 1, 1, "west")
    r.chest(1, 1, 1, "east")
    r.arrows(0, 2, 6, "east", damage=6)
    r.arrows(18, 2, 12, "west", damage=6)
    r.spikes(3, 9, 1)
    r.spikes(15, 9, 1)
    r.scatter(8, 1, 1, 17, 17)
    r.lights(y=5)
    r.spawner(9, 2, 13, waves=[[sentry(3, 31), acolyte(3, 31)], [lancer(3, 32), sentinel(2, 33), lurker(2, 32)]],
              radius=10, spread=4)
    return r.finish(pack)


def room_sentinel_barracks(pack):
    r = Room("rooms/sentinel_barracks", 21, 9, 17, fl=2)
    r.shell()
    p = r.p
    r.door("north", "side_in")
    r.door("south", "side_out", pool=F2_LEAF)
    for z in (3, 5, 7, 9, 11, 13):
        for x0 in (1, 18):
            r.fill(x0, 1, z, x0 + 1, 1, z, "minecraft:polished_blackstone_slab[type=bottom]")
            r.fill(x0, 3, z, x0 + 1, 3, z, "minecraft:polished_blackstone_slab[type=bottom]")
            r.put(x0, 2, z, "minecraft:blackstone")
    r.fill(8, 1, 7, 12, 1, 9, "minecraft:polished_blackstone_slab[type=bottom]")
    r.keep(1, 3, 2, 13)
    r.keep(18, 3, 19, 13)
    r.keep(8, 7, 12, 9)
    r.chest(19, 1, 15, "west", "rare")
    r.chest(1, 1, 15, "east", "rare")
    r.chest(5, 1, 1, "south")
    r.arrows(3, 2, 0, "south", damage=6)
    r.arrows(17, 2, 0, "south", damage=6)
    r.arrows(4, 2, 16, "north", damage=6)
    r.spikes(6, 8, 1)
    r.spikes(14, 8, 1)
    r.scatter(8, 3, 1, 17, 15)
    r.lights(y=5)
    r.spawner(10, 1, 5, waves=[[acolyte(4, 30)], [sentry(3, 31), lancer(3, 31)], [sentinel(3, 33), lurker(3, 32)]],
              radius=11, spread=5)
    return r.finish(pack)


def room_ossuary(pack):
    r = Room("rooms/sculk_ossuary", 17, 9, 15, fl=2)
    r.shell()
    p = r.p
    r.door("north", "side_in")
    r.door("east", "side_out", pool=F2_LEAF)
    r.floor_patch(6, "minecraft:sculk", 1, 1, 15, 13, 2)
    for z in (4, 7, 10):
        for x in (3, 5, 11, 13):
            if z == 7 and x >= 11:
                continue
            for y in range(1, 4):
                p.set(x, y, z, "minecraft:bone_block[axis=y]")
            r.keepout.add((x, z))
    for x, z in ((8, 11), (2, 7), (15, 3)):
        p.set(x, 1, z, "minecraft:sculk_catalyst")
        r.keepout.add((x, z))
    r.cache(12, 11, 15, 13, (14, 12), "west", "The Bone-Warden's Reliquary")
    r.chest(1, 1, 13, "east")
    r.chest(5, 1, 13, "north")
    r.poison(2, 10, 5, 12)
    r.arrows(0, 2, 5, "east", damage=6)
    r.arrows(16, 2, 4, "west", damage=6)
    r.spikes(8, 7, 1)
    r.keep(7, 1, 9, 13)
    r.scatter(6, 1, 1, 15, 13, kinds=("coral", "coral", "kelp"))
    r.lights(y=5)
    r.spawner(8, 1, 6, [sentry(3, 31), acolyte(2, 31), lurker(1, 31), sentinel(1, 32)], radius=9, spread=3)
    return r.finish(pack)


# -- floor 2 leaf rooms -----------------------------------------------------------------------------------------------
def leaf_bone_cell(pack):
    r = Room("leaf/bone_cell", 9, 6, 9, fl=2)
    r.shell()
    r.door("north", "side_in")
    for x, z in ((2, 3), (6, 3), (2, 6), (6, 6)):
        for y in (1, 2):
            r.p.set(x, y, z, "minecraft:bone_block[axis=y]")
        r.keepout.add((x, z))
    r.chest(4, 1, 7, "north")
    r.arrows(0, 2, 4, "east", damage=6)
    r.lights(y=4, step=4)
    r.spawner(4, 1, 4, [acolyte(4, 31)], radius=6, spread=2)
    return r.finish(pack)


def leaf_pearl_cache(pack):
    r = Room("leaf/pearl_cache", 9, 7, 11, fl=2)
    r.shell()
    r.door("north", "side_in")
    r.fill(3, 1, 8, 5, 1, 9, "minecraft:gilded_blackstone")
    r.chest(4, 2, 9, "north", "rare")
    r.keep(3, 8, 5, 9)
    r.arrows(8, 2, 6, "west", damage=7)
    r.spikes(4, 5, 1)
    r.lights(y=5, step=4)
    r.spawner(4, 1, 5, [sentinel(1, 33), lurker(3, 32)], radius=7, spread=2)
    return r.finish(pack)


def leaf_guardroom(pack):
    r = Room("leaf/guardroom", 11, 7, 9, fl=2)
    r.shell()
    r.door("north", "side_in")
    r.col(2, 4, 1, 5)
    r.col(8, 4, 1, 5)
    r.chest(5, 1, 7, "north")
    r.arrows(10, 2, 3, "west", damage=6)
    r.lights(y=5, step=4)
    r.spawner(5, 1, 4, [guardian(2, 31), sentry(2, 31)], radius=7, spread=2)
    return r.finish(pack)


# =====================================================================================================================
# The Tidemother's Sanctum
# =====================================================================================================================
def sanctum(pack):
    """Domed round chamber (r 15, 16 tall, 31 deep) with an air pocket. The treasure room is a side annex to the west
    (behind the Tidemother's door), the key-locked vault a side annex to the east."""
    w, h, d = 45, 16, 31
    p = Piece(f"{DT}/boss/sanctum", w, h, d)
    rng = random.Random("sanctum")
    cx, cz = 22, 15
    r_in, r_out, k, cyl, wt = 14.4, 15.4, 2.5, 9, 11

    def within(r, y, rad):
        if y <= cyl:
            return r <= rad
        return r * r + ((y - cyl) * k) ** 2 <= rad * rad

    for x in range(w):
        for z in range(d):
            r = math.hypot(x - cx, z - cz)
            for y in range(h):
                if y == 0:
                    if r <= r_out:
                        p.set(x, y, z, BRICKS)
                elif within(r, y, r_in):
                    p.set(x, y, z, WATER if y <= wt else AIR)
                elif within(r, y, r_out):
                    p.set(x, y, z, BRICKS)

    def free(x, z):
        return p.get(x, 1, z) == WATER

    # entrance doorway through the north wall (jigsaw at 22,1,0)
    for x in range(21, 24):
        for y in (1, 2, 3):
            p.set(x, y, 0, WATER)
    for x in (20, 24):
        for y in (1, 2, 3):
            p.set(x, y, 0, SAND)
    p.door("north", SPINE_IN, SPINE_IN, "minecraft:empty", at=22, final_state=WATER)
    # west annex: treasure room, behind the Tidemother's door (dark prismarine, opens when she falls)
    p.fill(0, 0, 9, 6, 7, 21, BRICKS)
    p.fill(1, 1, 10, 5, 6, 20, WATER)
    for z in (14, 15, 16):
        for y in (1, 2, 3):
            p.set(7, y, z, "minecraft:dark_prismarine")
            p.set(6, y, z, WATER)
    p.marker(7, 2, 15, "door", group="tidemother", message="The tidal door sinks away.")
    # east annex: the vault, behind a key door
    p.fill(38, 0, 9, 44, 7, 21, BRICKS)
    p.fill(39, 1, 10, 43, 6, 20, WATER)
    for z in (14, 15, 16):
        for y in (1, 2, 3):
            p.set(37, y, z, "minecraft:chiseled_sandstone")
            p.set(38, y, z, WATER)
    p.marker(37, 2, 15, "key_door", key="skyrpg_world:tide_key", message="The vault is sealed. It needs the Tide Key.")
    for y in (0, 4):
        for z in (14, 15, 16):
            p.set(37, y, z, "minecraft:gold_block" if z == 15 else SAND)

    def chest(x, y, z, facing, tier):
        loot = f"skyrpg_world:chests/temple_{tier}"
        p.set(x, y, z, f"minecraft:chest[facing={facing},waterlogged=true]", {"id": "minecraft:chest", "LootTable": loot})
        p.marker(x, y, z, "chest", loot=loot, tier=tier)

    chest(1, 1, 11, "east", "rare")
    chest(1, 1, 19, "east", "rare")
    chest(3, 1, 20, "north", "common")
    chest(43, 1, 12, "west", "vault")
    chest(43, 1, 18, "west", "vault")
    chest(43, 1, 15, "west", "rare")
    for x, z in ((1, 15), (5, 11), (5, 19), (39, 11), (39, 19), (41, 15)):
        p.set(x, 1, z, "minecraft:gold_block")
    for x, z in ((3, 12), (3, 18), (41, 13), (41, 17)):
        p.set(x, 6, z, "minecraft:sea_lantern")
    p.sign(36, 2, 13, ["The Tidemother's", "hoard lies beyond", "the sealed gate.", "Bring the Tide Key."],
           block="minecraft:dark_oak_wall_sign[facing=west,waterlogged=true]", glow=True)
    # conduit pedestal at the back of the chamber
    p.fill(21, 1, 23, 23, 1, 25, BRICKS)
    p.set(22, 2, 24, "minecraft:gold_block")
    p.set(22, 3, 24, "minecraft:conduit[waterlogged=true]")
    for dx, dz in ((-1, -1), (1, 1), (-1, 1), (1, -1)):
        p.set(22 + dx, 2, 24 + dz, "minecraft:sea_lantern")
    # ring of twelve columns
    col_cells = []
    for i in range(12):
        a = math.radians(15 + 30 * i)
        x, z = round(cx + 11 * math.cos(a)), round(cz + 11 * math.sin(a))
        if 20 <= x <= 24 and z <= 5:
            continue
        col_cells.append((x, z))
        for y in range(1, wt + 1):
            p.set(x, y, z, "minecraft:cut_sandstone")
        for y in (1, wt):
            p.set(x, y, z, "minecraft:smooth_sandstone")
        p.set(x, 7, z, "minecraft:sea_lantern")
    vents = []
    for i in range(6):
        a = math.radians(30 + 60 * i)
        x, z = round(cx + 6.5 * math.cos(a)), round(cz + 6.5 * math.sin(a))
        vents.append((x, z))
        p.set(x, 0, z, "minecraft:magma_block")
        for y in range(1, 5):
            p.set(x, y, z, "minecraft:bubble_column[drag=true]")
    corals = ["tube", "brain", "bubble", "fire", "horn"]
    tries = placed = 0
    while placed < 110 and tries < 1500:
        tries += 1
        x, z = rng.randint(8, 36), rng.randint(1, 29)
        r = math.hypot(x - cx, z - cz)
        if r > 13.3 or r < 2.5 or not free(x, z) or p.get(x, 2, z) != WATER:
            continue
        if (20 <= x <= 24 and (z <= 6 or z >= 21)) or any(abs(x - a) + abs(z - b) <= 1 for a, b in col_cells + vents):
            continue
        kind = rng.choice(["kelp", "coral", "coral", "pickle"])
        if kind == "kelp":
            n = rng.randint(3, 9)
            for i in range(n):
                p.set(x, 1 + i, z, "minecraft:kelp[age=25]" if i == n - 1 else "minecraft:kelp_plant")
        elif kind == "coral":
            c = rng.choice(corals)
            p.set(x, 1, z, f"minecraft:{c}_coral_block")
            if rng.random() < 0.8:
                p.set(x, 2, z, f"minecraft:{rng.choice(corals)}_coral{'_fan' if rng.random() < 0.5 else ''}[waterlogged=true]")
        else:
            p.set(x, 1, z, f"minecraft:sea_pickle[pickles={rng.randint(2, 4)},waterlogged=true]")
        placed += 1
    for i in range(16):
        a = math.radians(22.5 * i + 8)
        x, z = round(cx + 15 * math.cos(a)), round(cz + 15 * math.sin(a))
        if p.get(x, 5, z) == BRICKS and 0 <= z < 30:
            p.set(x, 5, z, "minecraft:sea_lantern")
    for i in range(8):
        a = math.radians(45 * i)
        x, z = round(cx + 8 * math.cos(a)), round(cz + 8 * math.sin(a))
        if p.get(x, 13, z) == BRICKS:
            p.set(x, 13, z, "minecraft:sea_lantern")
    p.set(22, 15, 15, "minecraft:sea_lantern")
    p.marker(22, 3, 12, "boss", boss="skyrpg_world:tidemother", radius=16, group="tidemother")
    # weather: no dark_prismarine here (the door is dark prismarine and must stay a separate block)
    p.weather("a", {BRICKS: [("minecraft:prismarine", 0.10), ("minecraft:mossy_stone_bricks", 0.06)],
                    SAND: [("minecraft:sandstone", 0.10)]})
    # fully defined: whatever is left of the box (outside the dome) is solid
    for x in range(w):
        for y in range(h):
            for z in range(d):
                if (x, y, z) not in p.cells:
                    p.set(x, y, z, "minecraft:deepslate")
    pack.add(p)
    return p


# =====================================================================================================================
# Loot
# =====================================================================================================================
def entry(name, weight=1, count=None, mods=None):
    e = {"type": "minecraft:item", "name": name}
    if weight != 1:
        e["weight"] = weight
    m = list(mods or [])
    if count:
        lo, hi = count if isinstance(count, tuple) else (count, count)
        m.insert(0, {"type": "minecraft:set_count", "count": {"type": "minecraft:uniform", "min": lo, "max": hi}})
    if m:
        e["modifier"] = m[0] if len(m) == 1 else m
    return e


def pool(entries, rolls=(1, 1), chance=None):
    pl = {"rolls": {"type": "minecraft:uniform", "min": rolls[0], "max": rolls[1]}, "entries": entries}
    if chance is not None:
        pl["conditions"] = [{"condition": "minecraft:random_chance", "chance": chance}]
    return pl


def chance_pool(name, chance, count=None):
    return pool([entry(name, 1, count)], chance=chance)


BOOK = entry("minecraft:book", 4, mods=[{"type": "minecraft:enchant_randomly", "options": "#minecraft:on_random_loot"}])
POTION = entry("minecraft:potion", 5, mods=[{"type": "minecraft:set_potion", "id": "minecraft:water_breathing"}])
GEAR = pool([entry("skyrpg_items:tidecaller_blade"), entry("skyrpg_items:tidemother_pendant")])


def gear_pool(chance):
    g = dict(GEAR)
    g["conditions"] = [{"condition": "minecraft:random_chance", "chance": chance}]
    return g


def loot(pack):
    S = "skyrpg_"
    pack.loot_table("chests/temple_common", {"type": "minecraft:chest", "pools": [
        pool([entry("minecraft:prismarine_shard", 20, (2, 6)), entry("minecraft:prismarine_crystals", 14, (1, 4)),
              entry("minecraft:gold_nugget", 14, (3, 9)), entry("minecraft:gold_ingot", 8, (1, 4)),
              entry("minecraft:turtle_scute", 8, (1, 3)), entry("minecraft:nautilus_shell", 4, (1, 2)),
              entry("minecraft:emerald", 8, (1, 4)), entry("minecraft:kelp", 4, (3, 8)),
              entry("minecraft:diamond", 3, (1, 2)), POTION, BOOK,
              entry(S + "enchants:runic_dust", 12, (2, 6)), entry(S + "mining:glimmer_shard", 8, (1, 4)),
              entry(S + "mining:prism_fragment", 7, (1, 3)), entry(S + "fishing:tidescale", 9, (1, 3)),
              entry(S + "fishing:abyssal_bait", 6, (2, 6)), entry(S + "combat:wisp_essence", 4, (1, 3))],
             rolls=(4, 7)),
        pool([entry(S + "fishing:abyssal_pearl", 3), entry(S + "farming:lucky_clover", 2),
              entry(S + "mining:skyiron_ingot", 3, (1, 3)), entry(S + "combat:valor_mark", 1),
              entry("minecraft:trident", 1), entry("minecraft:sea_lantern", 4, (2, 5)),
              {"type": "minecraft:empty", "weight": 12}], rolls=(1, 2)),
    ]})
    pack.loot_table("chests/temple_rare", {"type": "minecraft:chest", "pools": [
        pool([entry("minecraft:diamond", 10, (2, 4)), entry("minecraft:emerald", 10, (3, 7)),
              entry("minecraft:gold_ingot", 12, (4, 9)), entry("minecraft:nautilus_shell", 8, (1, 3)),
              entry("minecraft:turtle_scute", 7, (2, 5)), entry("minecraft:prismarine_crystals", 8, (3, 8)),
              POTION, BOOK,
              entry(S + "enchants:runic_dust", 14, (4, 9)), entry(S + "mining:glimmer_shard", 8, (2, 6)),
              entry(S + "mining:glimmer_crystal", 6, (1, 2)), entry(S + "mining:prism_fragment", 8, (2, 5)),
              entry(S + "mining:skyiron_ingot", 6, (2, 5)), entry(S + "fishing:tidescale", 10, (2, 5)),
              entry(S + "fishing:tidescale_plate", 4, (1, 2)), entry(S + "fishing:abyssal_bait", 6, (4, 10)),
              entry(S + "combat:wisp_essence", 5, (2, 5))], rolls=(5, 8)),
        pool([entry(S + "fishing:abyssal_pearl", 5, (1, 2)), entry(S + "mining:prism", 3),
              entry(S + "mining:heartstone", 2), entry(S + "mining:lumenite_crystal", 2),
              entry(S + "combat:valor_mark", 3), entry(S + "combat:dread_fang", 3),
              entry(S + "farming:lucky_clover", 3), entry("minecraft:trident", 2),
              {"type": "minecraft:empty", "weight": 8}], rolls=(1, 2)),
        chance_pool(S + "combat:elite_trophy", 0.12),
        chance_pool(S + "pets:otter_egg", 0.04),
    ]})
    pack.loot_table("chests/temple_secret", {"type": "minecraft:chest", "pools": [
        pool([entry("minecraft:diamond", 12, (3, 6)), entry("minecraft:emerald", 12, (4, 9)),
              entry("minecraft:gold_ingot", 14, (5, 10)), entry("minecraft:nautilus_shell", 10, (2, 4)),
              entry("minecraft:prismarine_crystals", 10, (4, 9)), entry("minecraft:turtle_scute", 8, (3, 5)),
              POTION, BOOK,
              entry(S + "enchants:runic_dust", 14, (6, 12)), entry(S + "mining:glimmer_crystal", 8, (1, 3)),
              entry(S + "mining:prism_fragment", 8, (3, 6)), entry(S + "mining:skyiron_ingot", 8, (3, 6)),
              entry(S + "fishing:tidescale_plate", 6, (1, 3)), entry(S + "fishing:abyssal_pearl", 6, (1, 2)),
              entry(S + "combat:wisp_essence", 6, (3, 6))], rolls=(5, 8)),
        pool([entry(S + "mining:prism", 4), entry(S + "mining:heartstone", 3), entry(S + "mining:attuned_lumenite", 3),
              entry(S + "mining:emberite_ingot", 3, (1, 3)), entry(S + "mining:lumenite_crystal", 3),
              entry(S + "fishing:abyssal_pearl", 4, (2, 3)), entry(S + "combat:valor_mark", 3),
              entry(S + "combat:dread_fang", 3)], rolls=(1, 2)),
        chance_pool("minecraft:heart_of_the_sea", 0.15),
        chance_pool(S + "pets:otter_egg", 0.05),
        chance_pool(S + "pets:golden_pet_egg", 0.015),
        chance_pool("minecraft:trident", 0.20),
    ]})
    pack.loot_table("chests/temple_vault", {"type": "minecraft:chest", "pools": [
        pool([entry(S + "world:tide_pearl")], rolls=(2, 3)),
        pool([entry("minecraft:diamond", 14, (4, 9)), entry("minecraft:emerald", 12, (5, 12)),
              entry("minecraft:gold_ingot", 14, (6, 14)), entry("minecraft:gold_block", 4, (1, 3)),
              entry("minecraft:nautilus_shell", 10, (2, 5)), entry("minecraft:prismarine_crystals", 8, (5, 12)),
              entry("minecraft:turtle_scute", 8, (3, 7)), POTION, BOOK,
              entry(S + "enchants:runic_dust", 14, (8, 16)), entry(S + "mining:glimmer_crystal", 8, (2, 4)),
              entry(S + "mining:prism", 5, (1, 2)), entry(S + "mining:skyiron_ingot", 8, (4, 8)),
              entry(S + "fishing:tidescale_plate", 8, (2, 4)), entry(S + "fishing:abyssal_pearl", 8, (2, 4)),
              entry(S + "fishing:leviathan_scale", 4, (1, 2)), entry(S + "combat:wisp_essence", 6, (4, 8))],
             rolls=(7, 11)),
        pool([entry(S + "mining:heartstone", 4), entry(S + "mining:attuned_lumenite", 4),
              entry(S + "mining:emberite_ingot", 4, (2, 4)), entry(S + "mining:lumenite_crystal", 3, (1, 2)),
              entry(S + "combat:valor_mark", 4, (1, 2)), entry(S + "combat:valor_seal", 3),
              entry(S + "combat:dread_fang", 4), entry(S + "combat:elite_trophy", 3),
              entry(S + "combat:null_core", 2), entry(S + "combat:void_fragment", 2)], rolls=(2, 3)),
        chance_pool("minecraft:heart_of_the_sea", 0.35),
        chance_pool(S + "combat:nightmare_core", 0.15),
        chance_pool(S + "pets:golden_pet_egg", 0.03),
        chance_pool(S + "pets:otter_egg", 0.08),
        gear_pool(0.20),
    ]})
    pack.loot_table("bosses/temple_champion", {"type": "minecraft:chest", "pools": [
        pool([entry(S + "enchants:runic_dust", 12, (8, 16)), entry(S + "fishing:tidescale_plate", 8, (2, 3)),
              entry(S + "mining:glimmer_crystal", 8, (2, 4)), entry(S + "mining:prism_fragment", 8, (3, 6)),
              entry(S + "mining:skyiron_ingot", 8, (3, 6)), entry("minecraft:diamond", 8, (3, 6)),
              entry("minecraft:nautilus_shell", 6, (2, 4)), entry("minecraft:prismarine_crystals", 8, (6, 12)),
              entry("minecraft:gold_ingot", 8, (6, 12)), BOOK], rolls=(4, 6)),
        pool([entry(S + "fishing:abyssal_pearl", 6, (1, 2)), entry(S + "combat:valor_mark", 4),
              entry(S + "combat:elite_trophy", 3), entry(S + "combat:dread_fang", 3),
              entry(S + "mining:heartstone", 2), entry(S + "mining:attuned_lumenite", 2)], rolls=(1, 2)),
        chance_pool(S + "world:tide_pearl", 0.50),
        chance_pool(S + "world:tide_key", 0.35),
        chance_pool("minecraft:heart_of_the_sea", 0.15),
        chance_pool(S + "pets:otter_egg", 0.05),
        gear_pool(0.04),
    ]})
    pack.loot_table("bosses/tidemother", {"type": "minecraft:chest", "pools": [
        pool([entry(S + "world:tide_pearl")], rolls=(2, 4)),
        pool([entry(S + "world:tide_key")]),
        chance_pool("skyrpg_items:tidecaller_blade", 0.12),
        chance_pool("skyrpg_items:tidemother_pendant", 0.12),
        pool([entry("minecraft:diamond", 8, (6, 12)), entry("minecraft:nautilus_shell", 6, (3, 6)),
              entry("minecraft:prismarine_crystals", 10, (10, 20)), entry("minecraft:gold_ingot", 8, (12, 24)),
              entry("minecraft:turtle_scute", 6, (4, 8)), BOOK,
              entry(S + "enchants:runic_dust", 12, (14, 28)), entry(S + "mining:glimmer_crystal", 8, (3, 6)),
              entry(S + "mining:prism", 6, (1, 3)), entry(S + "mining:skyiron_ingot", 8, (6, 12)),
              entry(S + "fishing:tidescale_plate", 8, (3, 6)), entry(S + "fishing:abyssal_pearl", 8, (3, 5)),
              entry(S + "combat:wisp_essence", 6, (6, 12))], rolls=(6, 9)),
        pool([entry(S + "mining:heartstone", 5), entry(S + "mining:attuned_lumenite", 5),
              entry(S + "mining:emberite_ingot", 5, (3, 6)), entry(S + "mining:lumenite_crystal", 4, (1, 3)),
              entry(S + "fishing:leviathan_scale", 5, (1, 3)), entry(S + "combat:valor_seal", 4),
              entry(S + "combat:dread_fang", 4), entry(S + "combat:null_core", 3),
              entry(S + "combat:elite_trophy", 3)], rolls=(2, 3)),
        chance_pool("minecraft:heart_of_the_sea", 0.40),
        chance_pool(S + "combat:nightmare_core", 0.25),
        chance_pool(S + "combat:void_fragment", 0.20),
        chance_pool(S + "pets:golden_pet_egg", 0.06),
        chance_pool(S + "pets:otter_egg", 0.12),
    ]})
    pack.loot_table("rewards/tidemother", {"type": "minecraft:chest", "pools": [
        pool([entry(S + "fishing:abyssal_pearl", 10, (2, 4)), entry(S + "fishing:leviathan_scale", 6, (1, 3)),
              entry(S + "fishing:tidescale_plate", 8, (3, 6)), entry(S + "mining:prism", 8, (1, 3)),
              entry(S + "mining:heartstone", 6), entry(S + "mining:attuned_lumenite", 6),
              entry(S + "mining:emberite_ingot", 8, (3, 6)), entry(S + "mining:skyiron_ingot", 8, (6, 12)),
              entry(S + "mining:lumenite_crystal", 5, (1, 3)), entry(S + "enchants:runic_dust", 12, (16, 32)),
              entry(S + "combat:valor_seal", 5), entry(S + "combat:null_core", 4), entry(S + "combat:void_fragment", 3),
              entry("minecraft:diamond", 8, (6, 12)), entry("minecraft:emerald", 8, (8, 16))], rolls=(4, 6)),
        chance_pool(S + "world:tide_pearl", 0.50, (1, 2)),
        chance_pool("minecraft:heart_of_the_sea", 0.30),
        chance_pool(S + "combat:nightmare_core", 0.20),
        chance_pool(S + "pets:golden_pet_egg", 0.05),
        gear_pool(0.10),
    ]})


# =====================================================================================================================
# Bosses + dungeon definition
# =====================================================================================================================
def boss(pack):
    pack.json("skyrpg/dungeon_bosses/temple_champion.json", {
        "name": "The Abyssal Herald",
        "entity": "minecraft:drowned",
        "dungeon": "skyrpg_world:drowned_temple",
        "tier": "champion",
        "level": 30,
        "health": 4000,
        "damage": 15,
        "speed": 0.3,
        "scale": 1.6,
        "knockback_resistance": 0.7,
        "bar_color": "purple",
        "equipment": {"mainhand": "minecraft:trident", "head": TURTLE},
        "intro": "The Herald of the deep rises to bar your way.",
        "death": "The Herald dissolves into foam. The portcullis rises.",
        "max_minions": 4,
        "health_per_player": 0.6,
        "loot": "skyrpg_world:bosses/temple_champion",
        "coins": [2500, 3500],
        "delving_xp": 900,
        "first_clear": {"coins": 3000, "xp": {"skyrpg_skills:combat": 1500}},
        "reset_ticks": 72000,
        "moves": [
            {"type": "volley", "every": 150, "projectile": "trident", "count": 3, "damage": 9, "spread": 0.1,
             "warn": "The Herald hurls tridents!"},
            {"type": "pull", "every": 300, "radius": 12, "strength": 1.1, "particle": "minecraft:bubble",
             "warn": "The undertow drags you in!"},
            {"type": "shockwave", "every": 240, "radius": 7, "damage": 11, "push": 1.6, "particle": "minecraft:splash",
             "warn": "A tidal wave rises! Get clear!"},
            {"type": "summon", "every": 480, "max": 4,
             "mobs": [{"type": "minecraft:drowned", "count": 2, "level": 28, "name": "Drowned Acolyte",
                       "equipment": {"mainhand": "minecraft:trident"}}],
             "warn": "The drowned heed the Herald!"},
        ],
    })
    pack.json("skyrpg/dungeon_bosses/tidemother.json", {
        "name": "The Tidemother",
        "entity": "minecraft:drowned",
        "dungeon": "skyrpg_world:drowned_temple",
        "level": 34,
        "health": 14000,
        "damage": 20,
        "speed": 0.3,
        "scale": 2.2,
        "knockback_resistance": 0.9,
        "bar_color": "blue",
        "equipment": {"mainhand": "minecraft:trident", "head": TURTLE},
        "intro": "You walk the drowned halls of a dead sea-god. Now drown with them.",
        "death": "The Tidemother sinks, and the tide drains out of the world.",
        "max_minions": 8,
        "health_per_player": 0.6,
        "loot": "skyrpg_world:bosses/tidemother",
        "reward_loot": "skyrpg_world:rewards/tidemother",
        "coins": [10000, 14000],
        "delving_xp": 4500,
        "first_clear": {"coins": 20000, "xp": {"skyrpg_skills:combat": 6000}},
        "reset_ticks": 72000,
        "phases": [
            {"below": 0.66, "message": "The Tidemother calls the deep: the sea itself answers!"},
            {"below": 0.33, "message": "The conduit overloads. The Tidemother's rage floods the sanctum!"},
        ],
        "moves": [
            {"type": "volley", "every": 170, "projectile": "trident", "count": 4, "damage": 12, "spread": 0.12,
             "warn": "The Tidemother hurls her tridents!"},
            {"type": "pull", "every": 320, "radius": 15, "strength": 1.2, "particle": "minecraft:bubble",
             "warn": "The undertow drags you in!"},
            {"type": "shockwave", "every": 260, "radius": 10, "damage": 14, "push": 1.8, "particle": "minecraft:splash",
             "warn": "A tidal wave rises! Get clear!"},
            {"type": "summon", "every": 520, "max": 6,
             "mobs": [{"type": "skyrpg_combat:tide_lurker", "count": 2, "level": 31, "name": "Tide Lurker"},
                      {"type": "minecraft:drowned", "count": 3, "level": 30, "name": "Drowned Acolyte",
                       "equipment": {"mainhand": "minecraft:trident"}}],
             "warn": "The drowned answer the Tidemother's call!"},
            {"type": "ward", "every": 900, "duration": 120, "reduction": 0.85,
             "warn": "Coral armour sheathes the Tidemother!"},
            {"type": "slam", "every": 300, "below": 0.66, "radius": 7, "damage": 16, "knockup": 1.0, "windup": 24,
             "warn": "The Tidemother raises her trident to crash down!"},
            {"type": "cloud", "every": 420, "below": 0.66, "effect": "minecraft:poison", "amplifier": 1,
             "duration": 120, "radius": 4, "on": "target", "warn": "Poisoned brine seeps from the vents!"},
            {"type": "heal", "every": 500, "below": 0.5, "percent": 0.03,
             "warn": "The conduit pulses and she mends!"},
            {"type": "volley", "every": 130, "below": 0.33, "projectile": "trident", "count": 7, "damage": 12,
             "spread": 0.2, "warn": "A storm of tridents!"},
            {"type": "enrage", "below": 0.33, "speed": 0.38, "damage": 0.4,
             "warn": "The Tidemother's eyes blaze sea-green!"},
        ],
    })
    pack.json("skyrpg/dungeons/drowned_temple.json", {
        "key": "skyrpg_world:tide_key",
        "name": "Drowned Temple",
        "structure": "skyrpg_world:drowned_temple",
        "color": "#3FB6C8",
        "icon": "minecraft:trident",
        "level": 28,
        "boss": "skyrpg_world:tidemother",
        "reset_ticks": 72000,
        "description": "A vast sunken temple of an older sea-faith in two floors, flooded to the rafters. Bring water breathing.",
    })


# =====================================================================================================================
def build(pack):
    P = lambda s: f"{DT}/{s}"  # noqa: E731
    entrance(pack)
    # floor 1 spine: f1a -> f1b -> f1c -> (f1d) -> champion
    # floor 1 spine: f1a -> f1b -> f1c -> champion
    hall_colonnade(pack, "a", P("f1b"))
    hall_straight(pack, "a", P("f1b"))
    hall_corner(pack, "a", P("f1b"))
    hall_shrine(pack, "a", P("f1b"))
    hall_guardian(pack, "b", P("f1c"))
    hall_cross(pack, "b", P("f1c"))
    hall_colonnade(pack, "b", P("f1c"))
    hall_corner(pack, "b", P("f1c"))
    hall_shrine(pack, "c", P("champ"))
    hall_cross(pack, "c", P("champ"))
    hall_straight(pack, "c", P("champ"))
    hall_colonnade(pack, "c", P("champ"))
    hall_champion(pack)
    descent(pack)
    # floor 2 spine: f2a -> f2b -> f2c -> sanctum
    hall_vent_gallery(pack, "a", P("f2b"))
    hall_sculk(pack, "a", P("f2b"))
    hall_straight(pack, "a", P("f2b"), fl=2)
    hall_corner(pack, "a", P("f2b"), fl=2)
    hall_guardian(pack, "b", P("f2c"), fl=2)
    hall_vent_gallery(pack, "b", P("f2c"))
    hall_shrine(pack, "b", P("f2c"), fl=2)
    hall_sculk(pack, "b", P("f2c"))
    hall_guardian(pack, "c", P("boss"), fl=2)
    hall_sculk(pack, "c", P("boss"))
    hall_straight(pack, "c", P("boss"), fl=2)
    hall_vent_gallery(pack, "c", P("boss"))
    sanctum(pack)
    # side rooms
    room_nave(pack, 1)
    room_nave(pack, 2)
    room_archive(pack)
    room_side_hall(pack, 1)
    room_pressure(pack)
    room_kelp_garden(pack)
    room_shipwreck(pack)
    room_reliquary(pack)
    room_barracks(pack)
    room_tide_gauge(pack)
    leaf_priest_cell(pack)
    leaf_closet(pack)
    leaf_sentry_post(pack)
    room_eel_tunnels(pack)
    room_bubble_elevator(pack)
    room_pearl_treasury(pack)
    room_vent_chamber(pack)
    room_sentinel_barracks(pack)
    room_ossuary(pack)
    room_side_hall(pack, 2)
    leaf_bone_cell(pack)
    leaf_pearl_cache(pack)
    leaf_guardroom(pack)
    set_cap(pack, "cap_spine", SPINE_IN, BRICKS)
    set_cap(pack, "cap_side", SIDE_IN, BRICKS)
    set_cap(pack, "cap_spine2", SPINE_IN, BLACK)
    set_cap(pack, "cap_side2", SIDE_IN, BLACK)

    cs1, cd1, cs2, cd2 = f"{DT}/caps_spine", f"{DT}/caps_side", f"{DT}/caps_spine2", f"{DT}/caps_side2"
    pack.pool(P("entrance"), [(P("entrance"), 1)], fallback=cs1)

    def hp(stage, entries, fb):
        pack.pool(P(stage), [(P(f"halls/{n}"), wgt) for n, wgt in entries], fallback=fb)

    hp("f1a", [("colonnade_a", 3), ("straight_a", 2), ("shrine_a", 2)], cs1)
    hp("f1b", [("guardian_b", 3), ("cross_b", 3), ("colonnade_b", 2)], cs1)
    hp("f1c", [("shrine_c", 2), ("cross_c", 2), ("straight_c", 2), ("colonnade_c", 2)], cs1)
    hp("champ", [("champion", 1)], cs1)
    pack.pool(P("descent"), [(P("descent"), 1)], fallback=cs1)
    hp("f2a", [("vent_gallery_a", 3), ("sculk_hall_a", 3), ("straight2_a", 2)], cs2)
    hp("f2b", [("guardian2_b", 3), ("vent_gallery_b", 2), ("shrine2_b", 2), ("sculk_hall_b", 2)], cs2)
    hp("f2c", [("guardian2_c", 3), ("sculk_hall_c", 2), ("straight2_c", 1), ("vent_gallery_c", 2)], cs2)
    pack.pool(P("boss"), [(P("boss/sanctum"), 1)], fallback=cs2)
    pack.pool(F1_SIDE, [(P("rooms/grand_nave"), 2), (P("rooms/archive"), 3), (P("rooms/side_hall"), 2), (P("rooms/kelp_garden"), 3),
                        (P("rooms/shipwreck_hold"), 3), (P("rooms/coral_reliquary"), 3), (P("rooms/drowned_barracks"), 3),
                        (P("rooms/tide_gauge"), 1), (P("rooms/pressure_room"), 1)], fallback=cd1)
    pack.pool(F1_LEAF, [(P("leaf/priest_cell"), 1), (P("leaf/reliquary_closet"), 1), (P("leaf/sentry_post"), 1)], fallback=cd1)
    pack.pool(F2_SIDE, [(P("rooms/abyss_nave"), 2), (P("rooms/eel_tunnels"), 3), (P("rooms/bubble_elevator"), 2), (P("rooms/pearl_treasury"), 1),
                        (P("rooms/vent_chamber"), 3), (P("rooms/sentinel_barracks"), 3), (P("rooms/sculk_ossuary"), 3),
                        (P("rooms/side_hall2"), 2)], fallback=cd2)
    pack.pool(F2_LEAF, [(P("leaf/bone_cell"), 1), (P("leaf/pearl_cache"), 1), (P("leaf/guardroom"), 1)], fallback=cd2)
    pack.pool(cs1, [(P("caps/cap_spine"), 1)])
    pack.pool(cd1, [(P("caps/cap_side"), 1)])
    pack.pool(cs2, [(P("caps/cap_spine2"), 1)])
    pack.pool(cd2, [(P("caps/cap_side2"), 1)])

    pack.structure(DT, start_pool=P("entrance"), biomes="#skyrpg_world:has_structure/drowned_temple", size=16,
                   max_distance=128, step="surface_structures", heightmap="OCEAN_FLOOR_WG", start_height=-3,
                   adaptation="none")
    pack.structure_set(DT, spacing=32, separation=12, salt=61201713)
    pack.biome_tag(DT, [f"minecraft:{b}" for b in ("ocean", "deep_ocean", "lukewarm_ocean", "deep_lukewarm_ocean",
                                                    "warm_ocean", "cold_ocean", "deep_cold_ocean")])
    pack.structure_tag("on_temple_maps", [DT])
    loot(pack)
    boss(pack)


def report(pack):
    from collections import Counter
    kinds = Counter()
    for pc in pack.pieces.values():
        for e in pc.entities:
            kinds[e["nbt"]["data"]["skyrpg"]] += 1
    print("pieces:")
    for path, pc in sorted(pack.pieces.items()):
        c = Counter(e["nbt"]["data"]["skyrpg"] for e in pc.entities)
        print(f"  {path:42s} {pc.size[0]:2d}x{pc.size[1]:2d}x{pc.size[2]:2d}  " + " ".join(f"{k}={v}" for k, v in sorted(c.items())))
    print("marker counts:", dict(kinds))


def reach_table(pack):
    """Worst-case cumulative distance along each floor's spine axis (blocks from the entrance centre)."""
    def stage(pool_name):
        sizes = []
        for e in pack.pools[f"skyrpg_world:{DT}/{pool_name}"]["elements"]:
            path = e["element"]["location"].split(":", 1)[1]
            sizes.append(pack.pieces[path].size)
        return sizes

    ent = pack.pieces[f"{DT}/entrance"].size
    z = ent[2] / 2
    print("reach table (worst case, straight chain; max_distance 116 per axis):")
    print(f"  entrance centre -> exit door         z = {z:5.1f}")
    for st in ("f1a", "f1b", "f1c", "champ"):
        z += max(s_[2] for s_ in stage(st))
        print(f"  after {st:6s} (max depth {max(s_[2] for s_ in stage(st)):2d})    z = {z:5.1f}")
    dw, dh, dd = pack.pieces[f"{DT}/descent"].size
    zl = z + 7  # floor 2 runs along x at this z (the exit door is 7 into the descent's depth)
    print(f"  descent entry at z = {z:5.1f}; its far wall z = {z + dd:5.1f}; floor 2 axis lies at z = {zl:5.1f}")
    x = dw - 3  # entry door at x=3, exit at the east wall
    print(f"  floor 2 starts x = {x:5.1f}")
    for st in ("f2a", "f2b", "f2c"):
        dep = max(s_[2] for s_ in stage(st))
        x += dep
        print(f"  after {st:6s} (max depth {dep:2d})    x = {x:5.1f}")
    sw, sh, sd = pack.pieces[f"{DT}/boss/sanctum"].size
    print(f"  sanctum ({sw}x{sh}x{sd}): far wall x = {x + sd:5.1f}; z span {zl - sw / 2:5.1f} .. {zl + sw / 2:5.1f} (lateral width {sw})")
    print(f"  worst axis value: {max(z + dd, x + sd, zl + sw / 2):5.1f}")
    tall = [pc.path for path, pc in pack.pieces.items() if any(k in path for k in ("halls/",)) and ("2_" in path or "vent_gallery" in path or "sculk_hall" in path) and pc.size[1] > 10]
    print("  floor-2 spine pieces taller than 10:", tall or "none")


def water_check(pack):
    """Air must never sit directly under water (air only above water surfaces)."""
    bad = 0
    for pc in pack.pieces.values():
        for (x, y, z), (entry, _) in pc.cells.items():
            if entry["id"] == AIR:
                above = pc.cells.get((x, y + 1, z))
                if above and above[0]["id"] == "minecraft:water":
                    bad += 1
                    print("air under water:", pc.path, (x, y, z))
    return bad


if __name__ == "__main__":
    pack = Pack()
    build(pack)
    water_check(pack)
    report(pack)
    reach_table(pack)
    pack.save(clean=["drowned_temple"])
