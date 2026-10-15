#!/usr/bin/env python3
"""Generates Skyhaven, the SkyRPG hub: a floating town above the clouds.

    python tools/builds/hub_skyhaven.py --apply

--apply updates the live hub three-way: it changes only cells where the world still matches the build last placed
(out/hub_skyhaven_world.txt), skips and lists any cell a player changed since, then records what it placed.
hub_skyhaven.mcfunction is the full build: it clears the whole box first, so only use it when nobody is in the hub.

Writes tools/builds/out/hub_skyhaven.mcfunction (the build), tools/builds/out/hub_skyhaven_config.json
(spawn, portal and decor for hub.json) and tools/builds/hub_spots.json (where each NPC stands).

Layout, north is -z, centred on the Skyheart plaza at 0,64,0:
  Plaza        golden beacon fountain under the floating Skyheart crystal, runestones, cherry planters
  North        the raised Crown Terrace: Skyhaven Bank (copper dome), Emberforge, Mason's Guild, windmill
  West         Greenhollow farm and fields, the Mistfall waterfall pouring onto a pond islet below
  South-west   Timberline Lodge among great trees
  South-east   The Gilded Gull tavern, beer garden and gazebo
  East         the tree-lined Bazaar Promenade, the koi garden and brook, and at its end the Bazaar Gate
  Islets       The Grand Bazaar (east: bell tower plaza and six merchant pavilions, bridge from the Bazaar Gate),
               Wildgate (survival portal, south), Arcane Library (enchanter, south-east), Starwatch
               (north-east), Mistfall (west), loose rocks
Display entities (crystal, windmill sails, sky lanterns, portal veil) are tagged skyrpg_hub_decor; the hub
mod spawns and animates them from hub.json "decor", so they are not part of the saved structure.
After hand-editing in game, /hubadmin save is the source of truth, not this script.
"""
import json
import math
import random
import re
from pathlib import Path

DIM = "skyrpg_hub:hub"
BOX_MIN, BOX_MAX = (-96, 16, -96), (95, 119, 95)   # y stays 16..119: the Sky Rings event arena sits above
G = 64            # grass level of the main island; players stand at 65
TERRACE = 68      # the Crown Terrace in the north
HERE = Path(__file__).resolve().parent
OUT = HERE / "out" / "hub_skyhaven.mcfunction"
UPDATE = HERE / "out" / "hub_skyhaven_update.mcfunction"   # only the changes since the build in the world
BLOCKS = HERE / "out" / "hub_skyhaven_blocks.txt"          # this build, block by block
WORLD = HERE / "out" / "hub_skyhaven_world.txt"            # the build last applied to the world
SPOTS = HERE / "hub_spots.json"

rng = random.Random(20261005)


# ---------------------------------------------------------------- helpers
def noise(x, z, s=1.0):
    return (math.sin(x * 0.21 * s + 1.3) * math.cos(z * 0.17 * s + 0.4)
            + math.sin((x + z) * 0.11 * s + 2.2) * 0.6
            + math.sin(x * 0.53 * s - z * 0.47 * s) * 0.3) / 1.9


def hash01(x, y, z):
    h = ((x * 73856093) ^ (y * 19349663) ^ (z * 83492791) ^ 0x5bd1e995) & 0xFFFFFFFF
    h = ((h ^ (h >> 13)) * 0x5bd1e995) & 0xFFFFFFFF
    return ((h ^ (h >> 15)) & 0xFFFF) / 0xFFFF


def pick(weights, x, y, z):
    r = hash01(x, y, z) * sum(weights.values())
    for block, weight in weights.items():
        r -= weight
        if r <= 0:
            return block
    return next(iter(weights))


def yaw_towards(x, z, tx, tz):
    """Minecraft yaw (0 = south, 90 = west, 180 = north) for looking from x,z to tx,tz, rounded to 15 degrees."""
    yaw = math.degrees(math.atan2(-(tx - x), tz - z))
    return float(round(yaw / 15) * 15 % 360)


OPPOSITE = {"north": "south", "south": "north", "east": "west", "west": "east"}
DIRS = {"north": (0, -1), "south": (0, 1), "west": (-1, 0), "east": (1, 0)}


class Build:
    LATE = ("lantern", "torch", "sign", "door", "short_grass", "fern", "poppy", "dandelion", "cornflower", "tulip",
            "oxeye_daisy", "azure_bluet", "allium", "lily_of_the_valley", "blue_orchid", "wheat", "carrots", "potatoes",
            "beetroots", "water", "campfire", "carpet", "pressure_plate", "potted_", "vine", "lily_pad", "bell", "petals",
            "wildflowers", "bush", "leaf_litter", "glow_lichen", "spore_blossom", "hanging_roots", "pointed_dripstone",
            "banner", "chain", "button", "torchflower", "rose_bush", "lilac", "peony", "amethyst_cluster", "candle",
            "end_rod", "lever", "ladder", "trapdoor", "pickle", "flowering_azalea[", "azalea[", "brewing_stand", "cake")
    HANGING = ("hanging=true", "vertical_direction=down", "cave_vines", "spore_blossom", "hanging_roots", "glow_lichen[up=true",
               "chain")
    PANES = ("glass_pane", "iron_bars", "copper_bars")
    NOT_SOLID = ("slab", "stairs", "lantern", "sign", "door", "carpet", "leaves", "water", "campfire", "pressure_plate",
                 "anvil", "grindstone", "enchanting_table", "cauldron", "composter", "farmland", "dirt_path", "trapdoor",
                 "chain", "banner", "scaffolding", "lectern", "bell", "end_rod", "candle", "button", "rod", "pot", "cake",
                 "brewing_stand", "bulb", "head", "cluster")

    def __init__(self):
        self.blocks = {}
        self.doors = []
        self.entities = []
        self.occupied = set()

    def set(self, x, y, z, block):
        self.blocks[(x, y, z)] = block

    def setif(self, x, y, z, block):
        if (x, y, z) not in self.blocks:
            self.blocks[(x, y, z)] = block

    def fill(self, x1, y1, z1, x2, y2, z2, block):
        for x in range(min(x1, x2), max(x1, x2) + 1):
            for y in range(min(y1, y2), max(y1, y2) + 1):
                for z in range(min(z1, z2), max(z1, z2) + 1):
                    self.blocks[(x, y, z)] = block

    def get(self, x, y, z):
        return self.blocks.get((x, y, z), "air")

    def claim(self, x1, z1, x2, z2, margin=0):
        for x in range(min(x1, x2) - margin, max(x1, x2) + margin + 1):
            for z in range(min(z1, z2) - margin, max(z1, z2) + margin + 1):
                self.occupied.add((x, z))

    def door(self, x, y, z, facing, wood="spruce", hinge="left"):
        self.doors.append((x, y, z, f"{wood}_door[facing={facing},hinge={hinge}"))
        self.blocks.pop((x, y, z), None)
        self.blocks.pop((x, y + 1, z), None)

    def is_late(self, block):
        return any(word in block for word in self.LATE)

    def solid(self, block):
        return (block not in ("air", "light") and not self.is_late(block)
                and not block.endswith(("_fence", "_wall", "glass_pane", "iron_bars", "copper_bars"))
                and not any(word in block for word in self.NOT_SOLID))

    def connect(self):
        for (x, y, z), block in list(self.blocks.items()):
            if "[" in block:
                continue
            kind = ("pane" if block.endswith(self.PANES) else "fence" if block.endswith("_fence")
                    else "wall" if block.endswith("_wall") else None)
            if kind is None:
                continue
            states = []
            for side, (dx, dz) in DIRS.items():
                other = self.blocks.get((x + dx, y, z + dz), "air").split("[")[0].split("{")[0]
                if kind == "pane":
                    joins = other.endswith(self.PANES) or self.solid(other)
                elif kind == "fence":
                    joins = other.endswith(("_fence", "_fence_gate")) or self.solid(other)
                else:
                    joins = other.endswith(("_wall", "_fence_gate", "glass_pane", "iron_bars")) or self.solid(other)
                states.append(f"{side}={('low' if joins else 'none') if kind == 'wall' else str(joins).lower()}")
            self.blocks[(x, y, z)] = f"{block}[{','.join(states)}]"

    KILL_ITEMS = (f"kill @e[type=item,x={BOX_MIN[0]},y={BOX_MIN[1]},z={BOX_MIN[2]},dx={BOX_MAX[0] - BOX_MIN[0]},"
                  f"dy={BOX_MAX[1] - BOX_MIN[1]},dz={BOX_MAX[2] - BOX_MIN[2]}]")

    def final_blocks(self):
        """Every block of the build with its final state, doors included."""
        outside = [pos for pos in self.blocks if not all(BOX_MIN[i] <= pos[i] <= BOX_MAX[i] for i in range(3))]
        if outside:
            raise SystemExit(f"{len(outside)} blocks outside the build box, e.g. {outside[:5]}")
        self.connect()
        blocks = dict(self.blocks)
        for x, y, z, door in self.doors:
            blocks[(x, y, z)] = f"{door},half=lower]"
            blocks[(x, y + 1, z)] = f"{door},half=upper]"
        return blocks

    def commands(self, previous=None, skip=frozenset()):
        """The full build (clears the box first), or with `previous` (the build last placed) only the changes,
        so nothing anyone is standing on disappears while it runs. Cells in `skip` are left alone."""
        blocks = {pos: block for pos, block in self.final_blocks().items() if pos not in skip}
        if previous is not None:
            previous = {pos: block for pos, block in previous.items() if pos not in skip}
        p = f"execute in {DIM} run "
        box = f"{BOX_MIN[0]} {BOX_MIN[2]} {BOX_MAX[0]} {BOX_MAX[2]}"
        out = [p + f"forceload add {box}"]
        if previous is None:
            # The hub mod respawns and animates decor from hub.json; a full build places it fresh too.
            out.append(p + self.KILL_ITEMS.replace("type=item", "tag=skyrpg_hub_decor"))
            for x in range(BOX_MIN[0], BOX_MAX[0] + 1, 32):
                for y in range(BOX_MIN[1], BOX_MAX[1] + 1, 32):
                    for z in range(BOX_MIN[2], BOX_MAX[2] + 1, 32):
                        out.append(p + f"fill {x} {y} {z} {min(x + 31, BOX_MAX[0])} {min(y + 31, BOX_MAX[1])} "
                                       f"{min(z + 31, BOX_MAX[2])} air strict")
            changed = blocks
        else:
            # Gone blocks: attached things first (top-down), then the rest from the top, so nothing drops.
            gone = sorted((pos for pos in previous if pos not in blocks),
                          key=lambda pos: (not self.is_late(previous[pos]), -pos[1]))
            out += [p + f"setblock {x} {y} {z} air" for x, y, z in gone]
            changed = {pos: block for pos, block in blocks.items() if previous.get(pos) != block}
        out.append(p + self.KILL_ITEMS)
        rows, late, doors = {}, [], []
        for (x, y, z), block in changed.items():
            if block == "air":
                continue
            if "_door[" in block:
                doors.append((block.endswith("half=upper]"), x, y, z, block))
            elif self.is_late(block):
                late.append((y, x, z, block))
            else:
                rows.setdefault((y, z), []).append((x, block))
        for (y, z) in sorted(rows):
            run = sorted(rows[(y, z)])
            start, prev, current = run[0][0], run[0][0], run[0][1]
            for x, block in run[1:] + [(None, None)]:
                if x is not None and x == prev + 1 and block == current:
                    prev = x
                    continue
                out.append(p + (f"setblock {start} {y} {z} {current}" if start == prev
                                else f"fill {start} {y} {z} {prev} {y} {z} {current}"))
                if x is not None:
                    start, prev, current = x, x, block

        # Standing things bottom-up, hanging things top-down (so each finds its support), water last.
        def order(entry):
            y, _, _, block = entry
            hanging = any(word in block for word in self.HANGING)
            return ("water" in block and "waterlogged" not in block, hanging, -y if hanging else y)
        for y, x, z, block in sorted(late, key=order):
            out.append(p + f"setblock {x} {y} {z} {block}")
        for _, x, y, z, door in sorted(doors):
            out.append(p + f"setblock {x} {y} {z} {door}")
        if previous is None:
            out += [p + e for e in self.entities]
        out.append(p + self.KILL_ITEMS)
        out.append(p + f"forceload remove {box}")
        return out


b = Build()
spots = {}
warnings = []


def spot(role, x, z, y, look_at=(0, 0), yaw=None):
    """An NPC's standing cell (block coords); faces look_at unless yaw is given. Keeps the cells around it clear,
    so nothing (not even grass) gets between a player and the NPC they click."""
    b.claim(x - 1, z - 1, x + 1, z + 1)
    spots[role] = {"x": x + 0.5, "y": float(y), "z": z + 0.5,
                   "yaw": yaw if yaw is not None else yaw_towards(x, z, *look_at)}


# ---------------------------------------------------------------- the main island
def edge_radius(theta):
    return 61 + 2.5 * math.sin(3 * theta + 0.7) + 1.5 * math.sin(5 * theta + 2.1) + 1.0 * math.sin(9 * theta)


def on_main(x, z, inset=0):
    return math.hypot(x, z) <= edge_radius(math.atan2(z, x)) - inset


def check(name, x0, z0, x1, z1, need=1.5):
    """Warn when a footprint comes closer than `need` blocks to the island's edge."""
    worst = min(edge_radius(math.atan2(z, x)) - math.hypot(x, z) for x in (x0, x1) for z in (z0, z1))
    if worst < need:
        warnings.append(f"{name}: {worst:.1f} blocks from the edge")


def terrace_z(x):
    """The Crown Terrace covers everything north of (and including) this z."""
    return -25 - round(2 * (x / 58) ** 2)


def ground(x, z):
    return TERRACE if z <= terrace_z(x) else G


STRATA = [("stone", 5), ("andesite", 3), ("stone", 2), ("tuff", 3), ("calcite", 1), ("stone", 4),
          ("deepslate", 3), ("tuff", 2), ("stone", 3), ("dripstone_block", 2), ("andesite", 2)]
STRATA_CYCLE = sum(n for _, n in STRATA)


def stratum(x, y, z):
    v = (y + int(noise(x * 0.6, z * 0.6) * 3)) % STRATA_CYCLE
    for block, n in STRATA:
        if v < n:
            if block == "stone" and hash01(x, y, z) < 0.04:
                return pick({"coal_ore": 3, "copper_ore": 3, "iron_ore": 2, "emerald_ore": 1, "amethyst_block": 1}, x, y, z)
            if y < 26 and block in ("stone", "andesite"):
                return pick({"deepslate": 6, "cobbled_deepslate": 2, "tuff": 1}, x, y, z)
            return block
        v -= n
    return "stone"


def column(x, z, top, bottom, edge):
    """Grass on top, a few blocks of soil, then rock; only the visible shell gets the coloured bands."""
    soil = 2 + round(noise(x * 1.9, z * 1.9) * 1.5 + 0.5)
    b.set(x, top, z, "grass_block")
    for y in range(top - soil, top):
        b.set(x, y, z, pick({"dirt": 7, "coarse_dirt": 2, "rooted_dirt": 1}, x, y, z) if edge else "dirt")
    for y in range(bottom, top - soil):
        b.set(x, y, z, stratum(x, y, z) if edge or y < bottom + 3 else "stone")


def underside(x, z, bottom, chance=1.0, rim=False):
    """Life hanging from the bottom of an island: cave vines at the rim, roots, lichen, blossoms, dripstone."""
    r = hash01(x, bottom, z)
    if rim and r < 0.22 * chance:
        n = 2 + int(hash01(z, bottom, x) * 6)
        for i in range(n):
            tip = i == n - 1
            berries = "true" if hash01(x, bottom - i, z) < 0.3 else "false"
            b.set(x, bottom - 1 - i, z, f"cave_vines[age=25,berries={berries}]" if tip else f"cave_vines_plant[berries={berries}]")
    elif r < 0.05 * chance:
        b.set(x, bottom - 1, z, "spore_blossom")
    elif r < 0.075 * chance:
        b.set(x, bottom, z, pick({"ochre_froglight": 2, "verdant_froglight": 2, "pearlescent_froglight": 1, "shroomlight": 2},
                                 x, bottom, z))
        b.set(x, bottom - 1, z, "glow_lichen[up=true]")
    elif r < 0.14 * chance:
        b.set(x, bottom - 1, z, "glow_lichen[up=true]")
    elif r < 0.16 * chance:
        b.set(x, bottom - 1, z, "hanging_roots")
    elif r < 0.19 * chance:
        n = 2 + int(hash01(z, x, bottom) * 3)
        parts = ["base", "middle", "frustum", "tip"] if n >= 4 else ["base", "frustum", "tip"] if n == 3 else ["frustum", "tip"]
        for i, part in enumerate(parts):
            b.set(x, bottom - 1 - i, z, f"pointed_dripstone[vertical_direction=down,thickness={part}]")


tops = {}
bottoms = {}
for x in range(-70, 71):
    for z in range(-70, 71):
        r = edge_radius(math.atan2(z, x))
        d = math.hypot(x, z)
        if d > r:
            continue
        t = d / r
        top = ground(x, z)
        depth = int(6 + 34 * (1 - t ** 1.3) ** 0.8 + noise(x, z) * 4 + noise(z * 2.1, x * 1.6) * 3)
        bottom = max(top - max(depth, 4), BOX_MIN[1] + 10)
        column(x, z, top, bottom, t > 0.86 or top == TERRACE and z > terrace_z(x) - 2)
        tops[(x, z)] = top
        bottoms[(x, z)] = bottom
for (x, z), bottom in bottoms.items():
    rim = any((x + dx, z + dz) not in tops for dx, dz in DIRS.values())
    underside(x, z, bottom, 1.0, rim)

# Big hanging spires under the middle.
for _ in range(20):
    a, dist = rng.uniform(0, math.tau), rng.uniform(4, 44)
    x, z = round(math.cos(a) * dist), round(math.sin(a) * dist)
    if (x, z) not in bottoms:
        continue
    y = bottoms[(x, z)]
    length = min(rng.randint(5, 14), y - BOX_MIN[1] - 9)
    for i in range(length):
        w = max(0, (length - i) // 4)
        for dx in range(-w, w + 1):
            for dz in range(-w, w + 1):
                if dx * dx + dz * dz <= w * w + 1:
                    b.blocks.pop((x + dx, y - 2 - i, z + dz), None)
                    b.set(x + dx, y - 1 - i, z + dz, stratum(x + dx, y - 1 - i, z + dz))
    underside(x, z, y - length, 2.5)

# The terrace's retaining wall: a stone face with a balustrade and lanterns on top.
for x in range(-70, 71):
    tz = terrace_z(x)
    if (x, tz) in tops and (x, tz + 1) in tops:
        for y in range(G - 2, TERRACE + 1):
            b.set(x, y, tz, pick({"stone_bricks": 6, "mossy_stone_bricks": 3, "cracked_stone_bricks": 1}, x, y, tz))
        b.set(x, TERRACE, tz, "polished_blackstone_bricks")
        b.set(x, TERRACE + 1, tz, "polished_blackstone_brick_wall")
        if x % 8 == 4:
            b.set(x, TERRACE + 1, tz, "polished_blackstone_bricks")
            b.set(x, TERRACE + 2, tz, "lantern[hanging=false]")
        if hash01(x, 3, tz) < 0.35:
            b.set(x, G + 1, tz + 1, "moss_carpet")
        b.occupied.add((x, tz))


# ---------------------------------------------------------------- plaza
PLAZA_R = 20
for x in range(-PLAZA_R - 1, PLAZA_R + 2):
    for z in range(-PLAZA_R - 1, PLAZA_R + 2):
        d = math.hypot(x, z)
        if d >= PLAZA_R:
            continue
        ang = (math.degrees(math.atan2(z, x)) + 360) % 45
        radial = d > 8 and (ang < 2.2 or ang > 42.8)
        if d >= PLAZA_R - 1:
            block = "polished_blackstone_bricks"
        elif d >= PLAZA_R - 2.5:
            block = "calcite"
        elif abs(d - 12.5) < 0.55:
            block = "polished_deepslate"
        elif radial:
            block = "gilded_blackstone" if d < 11.5 else "polished_blackstone"
        elif d < 8:
            block = pick({"smooth_quartz": 3, "calcite": 2}, x, 0, z)
        else:
            block = pick({"polished_andesite": 7, "polished_diorite": 1, "smooth_stone": 2}, x, 0, z)
        b.set(x, G, z, block)
        b.occupied.add((x, z))

# The Skyheart fountain: a beacon beam rises through a two-tier fountain whose top basin spills over.
for x in range(-8, 9):
    for z in range(-8, 9):
        d = math.hypot(x, z)
        if 6.5 <= d < 7.6:
            b.set(x, G + 1, z, "deepslate_bricks" if (x + z) % 4 else "gold_block")
            b.set(x, G + 2, z, "polished_blackstone_slab[type=bottom]")
        elif d < 6.5:
            b.set(x, G - 1, z, "sea_lantern" if (x % 3 == 0 and z % 3 == 0) else "dark_prismarine")
            b.set(x, G, z, "water")
            b.set(x, G + 1, z, "water")
        if d < 3.3:
            b.set(x, G + 2, z, "polished_deepslate")
            b.set(x, G + 3, z, "deepslate_bricks" if d >= 2.3 else "chiseled_polished_blackstone")
            if d < 2.3:
                b.set(x, G + 4, z, "water")
for y in range(G - 3, G + 4):
    b.set(0, y, 0, "yellow_stained_glass")
b.set(0, G - 4, 0, "beacon")
b.fill(-1, G - 5, -1, 1, G - 5, 1, "iron_block")
for x, z in ((-1, -1), (-1, 1), (1, -1), (1, 1)):
    b.set(x, G - 1, z, "dark_prismarine")
# Hidden lights in a ring over the fountain basin (players found it too dark at night).
for x, z in ((5, 0), (-5, 0), (0, 5), (0, -5), (4, 4), (4, -4), (-4, 4), (-4, -4), (6, 3), (6, -3), (-6, 3), (-6, -3),
             (3, 6), (3, -6), (-3, 6), (-3, -6), (8, 0), (-8, 0), (0, 8), (0, -8), (6, 6), (6, -6), (-6, 6), (-6, -6),
             (8, 3), (8, -3), (-8, 3), (-8, -3), (3, 8), (3, -8), (-3, 8), (-3, -8)):
    b.set(x, G + 3, z, "light[level=14]")
for x, z in ((3, 0), (-3, 0), (0, 3), (0, -3), (2, 2), (2, -2), (-2, 2), (-2, -2)):
    b.set(x, G + 6, z, "light[level=14]")

BANNER = ('{patterns:[{pattern:"minecraft:border",color:"yellow"},{pattern:"minecraft:rhombus",color:"yellow"},'
          '{pattern:"minecraft:circle",color:"black"}]}')
# Eight runestones around the plaza, between the avenues, each with a gold-on-black banner.
for i in range(8):
    a = math.radians(22.5 + 45 * i)
    x, z = round(math.cos(a) * 17.3), round(math.sin(a) * 17.3)
    b.set(x, G + 1, z, "chiseled_polished_blackstone")
    b.fill(x, G + 2, z, x, G + 4, z, "polished_blackstone")
    b.set(x, G + 5, z, "gilded_blackstone")
    b.set(x, G + 6, z, "lantern[hanging=false]")
    face = max(DIRS, key=lambda f: DIRS[f][0] * math.cos(a) + DIRS[f][1] * math.sin(a))
    dx, dz = DIRS[face]
    b.set(x + dx, G + 4, z + dz, f"black_wall_banner[facing={face}]{BANNER}")


def cherry_tree(x, y, z, height=5):
    b.fill(x, y + 1, z, x, y + height, z, "cherry_log")
    for bx, bz in ((1, 0), (-1, 0), (0, 1), (0, -1)):
        if hash01(x + bx, y, z + bz) < 0.6:
            axis = "x" if bx else "z"
            b.set(x + bx, y + height - 1, z + bz, f"cherry_log[axis={axis}]")
            b.set(x + bx * 2, y + height - 1, z + bz * 2, f"cherry_log[axis={axis}]")
    for dx in range(-3, 4):
        for dy in range(-1, 3):
            for dz in range(-3, 4):
                if dx * dx / 9 + (dy - 0.5) ** 2 / 3.2 + dz * dz / 9 <= 1.0 and hash01(x + dx, y + dy, z + dz) > 0.08:
                    b.setif(x + dx, y + height + dy, z + dz, "cherry_leaves[persistent=true]")
    for dx in range(-3, 4):
        for dz in range(-3, 4):
            if dx * dx + dz * dz <= 9 and hash01(x + dx, 5, z + dz) < 0.35 and b.get(x + dx, y + 1, z + dz) == "air" \
                    and b.get(x + dx, y, z + dz) == "grass_block":
                b.set(x + dx, y + 1, z + dz, "pink_petals[flower_amount=3,facing=north]")


# Cherry planters on the diagonals.
for sx in (-1, 1):
    for sz in (-1, 1):
        cx, cz = 11 * sx, 11 * sz
        for x in range(cx - 2, cx + 3):
            for z in range(cz - 2, cz + 3):
                edge = abs(x - cx) == 2 or abs(z - cz) == 2
                b.set(x, G, z, "polished_blackstone_bricks" if edge else "grass_block")
                if edge:
                    b.set(x, G + 1, z, "polished_blackstone_brick_slab[type=bottom]")
                elif (x, z) != (cx, cz):
                    b.set(x, G + 1, z, pick({"pink_petals[flower_amount=4,facing=north]": 3, "lily_of_the_valley": 1,
                                             "allium": 1}, x, 1, z))
        cherry_tree(cx, G, cz, 5)
        # Hidden lights under the canopy: it was too dark beneath the trees.
        for dx, dz in ((1, 1), (1, -1), (-1, 1), (-1, -1), (0, 2), (0, -2), (2, 0), (-2, 0)):
            b.set(cx + dx, G + 3, cz + dz, "light[level=14]")

# Benches facing the fountain (stairs with their backs away from it, trapdoor arms).
for bx, bz, facing in ((-4, 15, "south"), (4, 15, "south"), (-4, -15, "north"), (4, -15, "north"),
                       (15, -4, "east"), (15, 4, "east"), (-15, -4, "west"), (-15, 4, "west")):
    if facing in ("north", "south"):
        for dx in (-1, 0, 1):
            b.set(bx + dx, G + 1, bz, f"dark_oak_stairs[facing={facing}]")
        b.set(bx - 2, G + 1, bz, "dark_oak_trapdoor[facing=west,open=true]")
        b.set(bx + 2, G + 1, bz, "dark_oak_trapdoor[facing=east,open=true]")
    else:
        for dz in (-1, 0, 1):
            b.set(bx, G + 1, bz + dz, f"dark_oak_stairs[facing={facing}]")
        b.set(bx, G + 1, bz - 2, "dark_oak_trapdoor[facing=north,open=true]")
        b.set(bx, G + 1, bz + 2, "dark_oak_trapdoor[facing=south,open=true]")


# ---------------------------------------------------------------- avenues and lamps
ROAD = {"cobbled_deepslate": 30, "deepslate_tiles": 22, "polished_deepslate": 14, "tuff_bricks": 18,
        "mossy_cobblestone": 6, "deepslate_bricks": 10}
PATH = {"packed_mud": 2, "coarse_dirt": 2, "gravel": 1, "dirt_path": 5, "mossy_cobblestone": 1}


def street_lamp(x, z, y, arm):
    """A dark post with a lantern hanging from an arm pointing `arm`."""
    b.set(x, y + 1, z, "polished_blackstone")
    b.fill(x, y + 2, z, x, y + 4, z, "polished_blackstone_wall")
    b.set(x, y + 5, z, "polished_blackstone")
    dx, dz = DIRS[arm]
    b.set(x + dx, y + 5, z + dz, "dark_oak_fence")
    b.set(x + dx, y + 4, z + dz, "lantern[hanging=true]")
    b.occupied.add((x, z))


def avenue(dx, dz, start, end, lamps=True):
    for s in range(start, end + 1):
        for w in range(-2, 3):
            x, z = dx * s + (w if dz else 0), dz * s + (w if dx else 0)
            if (x, z) not in tops:
                continue
            b.set(x, tops[(x, z)], z, "polished_blackstone_bricks" if abs(w) == 2 else pick(ROAD, x, 0, z))
            b.occupied.add((x, z))
        if lamps and s % 8 == 4:
            for side in (-3, 3):
                x, z = dx * s + (side if dz else 0), dz * s + (side if dx else 0)
                if (x, z) in tops and on_main(x, z, 2):
                    outward = ("north" if side < 0 else "south") if dx else ("west" if side < 0 else "east")
                    street_lamp(x, z, tops[(x, z)], OPPOSITE[outward])


def road_end(dx, dz):
    s = PLAZA_R
    while on_main(dx * (s + 1), dz * (s + 1), 3):
        s += 1
    return s


END = {name: road_end(*d) for name, d in DIRS.items()}
avenue(1, 0, PLAZA_R - 1, END["east"])
avenue(-1, 0, PLAZA_R - 1, END["west"])
avenue(0, 1, PLAZA_R - 1, END["south"])
avenue(0, -1, PLAZA_R - 1, 24, lamps=False)
avenue(0, -1, 25, 34)


def path(points):
    """A 3-wide dirt path through a list of points (axis-aligned legs). Skips anything already built on."""
    for (x0, z0), (x1, z1) in zip(points, points[1:]):
        steps = max(abs(x1 - x0), abs(z1 - z0))
        for i in range(steps + 1):
            x = x0 + (x1 - x0) * i // max(steps, 1)
            z = z0 + (z1 - z0) * i // max(steps, 1)
            for w in (-1, 0, 1):
                px, pz = (x + w, z) if abs(x1 - x0) < abs(z1 - z0) else (x, z + w)
                if (px, pz) in tops and (px, pz) not in b.occupied:
                    b.set(px, tops[(px, pz)], pz, pick(PATH, px, 0, pz))
                    b.occupied.add((px, pz))


# Grand stairs up to the Crown Terrace, flanked by lantern pillars.
for i, z in enumerate(range(-PLAZA_R - 1, -26, -1)):
    level = min(G + 1 + i, TERRACE)
    for x in range(-5, 6):
        b.fill(x, G - 1, z, x, level - 1, z, "stone_bricks")
        if abs(x) == 5:
            b.set(x, level, z, "polished_blackstone_bricks")
            b.set(x, level + 1, z, "polished_blackstone_brick_wall")
        elif level < TERRACE:
            b.set(x, level, z, "deepslate_tile_stairs[facing=north]")
        else:
            b.set(x, level, z, "polished_blackstone_bricks" if abs(x) == 4 else "deepslate_tiles")
            b.blocks.pop((x, level + 1, z), None)
        b.occupied.add((x, z))
for x in range(-4, 5):
    b.blocks.pop((x, TERRACE + 1, terrace_z(x)), None)
    b.blocks.pop((x, TERRACE + 2, terrace_z(x)), None)
for x in (-6, 6):
    b.fill(x, G + 1, -20, x, G + 5, -20, "polished_blackstone")
    b.set(x, G + 6, -20, "gilded_blackstone")
    b.set(x, G + 7, -20, "lantern[hanging=false]")
    b.occupied.add((x, -20))
# Hidden lights over the stairs and their foot (players found them too dark at night).
for x in (-4, 0, 4, 8):
    b.set(x, G + 6, -23, "light[level=15]")
    b.set(x, G + 7, -27, "light[level=15]")
for x in (-4, 0, 4):
    b.set(x, G + 5, -19, "light[level=15]")
for x in range(-9, 10, 3):
    for z in (-22, -19, -16):
        b.set(x, G + 4, z, "light[level=15]")
for x, y, z in ((4, G + 3, -22), (2, G + 3, -21), (4, G + 3, -20), (2, G + 3, -19), (4, G + 3, -18), (3, G + 2, -17)):
    b.set(x, y, z, "light[level=15]")


# ---------------------------------------------------------------- building kit
def sign_nbt(*lines, color="yellow"):
    msgs = ",".join(f'"{line}"' for line in (list(lines) + ["", "", "", ""])[:4])
    return f'{{front_text:{{color:"{color}",has_glowing_text:1b,messages:[{msgs}]}},is_waxed:1b}}'


def gable_roof(x0, z0, x1, z1, y, stairs, slab, gable, along_x, overhang=1, eave=None):
    lo, hi = (z0 - overhang, z1 + overhang) if along_x else (x0 - overhang, x1 + overhang)
    a0, a1 = (x0 - overhang, x1 + overhang) if along_x else (z0 - overhang, z1 + overhang)

    def put(a, c, yy, block):
        if along_x:
            b.set(a, yy, c, block)
        else:
            b.set(c, yy, a, block)
    i = 0
    while lo + i <= hi - i:
        yy = y + i
        for a in range(a0, a1 + 1):
            if lo + i == hi - i:
                put(a, lo + i, yy, slab)
            else:
                put(a, lo + i, yy, f"{stairs}[facing={'south' if along_x else 'east'},half=bottom]")
                put(a, hi - i, yy, f"{stairs}[facing={'north' if along_x else 'west'},half=bottom]")
                if i == 0 and eave:
                    put(a, lo, yy - 1, f"{eave}[facing={'north' if along_x else 'west'},half=top]")
                    put(a, hi, yy - 1, f"{eave}[facing={'south' if along_x else 'east'},half=top]")
        for c in range(lo + i + 1, hi - i):
            for end in ((x0, x1) if along_x else (z0, z1)):
                put(end, c, yy, gable)
        i += 1
    return y + i


FLOWER_POTS = {"potted_red_tulip": 1, "potted_allium": 1, "potted_azure_bluet": 1, "potted_cornflower": 1,
               "potted_lily_of_the_valley": 1, "potted_orange_tulip": 1, "potted_pink_tulip": 1}


def window(x, y, z, face, pane="glass_pane", shutters="spruce_trapdoor", flowers=True):
    """A 2-tall window with open shutters and a flower box outside."""
    b.set(x, y, z, pane)
    b.set(x, y + 1, z, pane)
    dx, dz = DIRS[face]
    if shutters:
        for s in (-1, 1):
            sx, sz = (x + dx, z + s) if dx else (x + s, z + dz)
            b.set(sx, y, sz, f"{shutters}[facing={face},open=true,half=bottom]")
            b.set(sx, y + 1, sz, f"{shutters}[facing={face},open=true,half=top]")
    if flowers:
        b.set(x + dx, y - 1, z + dz, f"spruce_trapdoor[facing={face},half=top,open=false]")
        b.set(x + dx, y, z + dz, pick(FLOWER_POTS, x, y, z))


def building(name, x0, z0, x1, z1, y, door_side, p, wall_h=5, sign=None, chimney=None, double_door=False):
    """Half-timbered building on a stone plinth with windows, eaves and a chimney. Returns the door's outside cell."""
    check(name, x0 - 1, z0 - 1, x1 + 1, z1 + 1)
    b.claim(x0, z0, x1, z1, margin=1)
    top = y + wall_h
    b.fill(x0 - 1, y - 3, z0 - 1, x1 + 1, y - 1, z1 + 1, "stone_bricks")
    b.fill(x0 - 1, y, z0 - 1, x1 + 1, y, z1 + 1, p["plinth"])
    b.fill(x0 + 1, y, z0 + 1, x1 - 1, y, z1 - 1, p["floor"])
    for x in range(x0, x1 + 1):
        for z in range(z0, z1 + 1):
            if x not in (x0, x1) and z not in (z0, z1):
                continue
            corner = x in (x0, x1) and z in (z0, z1)
            along = (x - x0) if z in (z0, z1) else (z - z0)
            length = (x1 - x0) if z in (z0, z1) else (z1 - z0)
            pillar = corner or along % 4 == 0 or along == length
            face = "north" if z == z0 else "south" if z == z1 else "west" if x == x0 else "east"
            for yy in range(y + 1, top + 1):
                if yy <= y + 1:
                    block = p["base"]
                elif pillar:
                    block = p["frame"]
                elif yy == top or (wall_h >= 8 and yy == y + 5):
                    block = p["beam"]
                else:
                    block = p["wall"]
                b.set(x, yy, z, block)
            if not pillar and along % 4 == 2 and 0 < along < length - 1:
                window(x, y + 2, z, face, p["window"], p.get("shutters"), p.get("flowers", True))
                if wall_h >= 8:
                    window(x, y + 6, z, face, p["window"], p.get("shutters"), False)
    along_x = (x1 - x0) >= (z1 - z0)
    ridge = gable_roof(x0, z0, x1, z1, top + 1, p["stairs"], p["slab"], p["gable"], along_x, 1, p.get("eave"))
    if chimney:
        cx, cz = chimney
        b.fill(cx, y + 1, cz, cx, ridge + 1, cz, "bricks")
        b.set(cx, ridge + 2, cz, "campfire[lit=true,signal_fire=false]")
    cx, cz = (x0 + x1) // 2, (z0 + z1) // 2
    dx, dz = {"north": (cx, z0), "south": (cx, z1), "west": (x0, cz), "east": (x1, cz)}[door_side]
    out = DIRS[door_side]
    wide = 1 if double_door else 0
    if door_side in ("north", "south"):
        b.fill(dx - 1, y + 1, dz, dx + 1 + wide, y + 3, dz, p["frame"])
        b.door(dx, y + 1, dz, door_side, p["door"], "left")
        if double_door:
            b.door(dx + 1, y + 1, dz, door_side, p["door"], "right")
    else:
        b.fill(dx, y + 1, dz - 1, dx, y + 3, dz + 1 + wide, p["frame"])
        b.door(dx, y + 1, dz, door_side, p["door"], "left")
        if double_door:
            b.door(dx, y + 1, dz + 1, door_side, p["door"], "right")
    ox, oz = dx + out[0], dz + out[1]
    if sign:
        b.set(ox, y + 4, oz, f"dark_oak_wall_sign[facing={door_side}]" + sign_nbt(*sign))
    # Porch lanterns either side of the door.
    for side in (-2, 2 + wide):
        lx, lz = (ox + side, oz) if door_side in ("north", "south") else (ox, oz + side)
        if (lx, lz) in tops:
            b.set(lx, y + 1, lz, "dark_oak_fence")
            b.set(lx, y + 2, lz, "lantern[hanging=false]")
            b.occupied.add((lx, lz))
    for c in range(-1, 2 + wide):
        cx2, cz2 = (ox + c, oz) if door_side in ("north", "south") else (ox, oz + c)
        b.occupied.add((cx2, cz2))
        b.set(cx2, y, cz2, p["plinth"])
    return ox, oz


HALF_TIMBER = {"plinth": "stone_bricks", "base": "cobblestone", "floor": "spruce_planks", "frame": "spruce_log",
               "beam": "stripped_spruce_log", "wall": "calcite", "window": "glass_pane", "shutters": "spruce_trapdoor",
               "stairs": "deepslate_tile_stairs", "slab": "deepslate_tile_slab[type=bottom]", "gable": "spruce_planks",
               "eave": "spruce_stairs", "door": "spruce"}
FARMHOUSE = dict(HALF_TIMBER, wall="stripped_birch_log", frame="dark_oak_log", beam="stripped_dark_oak_log",
                 stairs="mangrove_stairs", slab="mangrove_slab[type=bottom]", gable="mangrove_planks", eave="dark_oak_stairs",
                 base="mossy_cobblestone", door="dark_oak", shutters="dark_oak_trapdoor")
LODGE = dict(HALF_TIMBER, wall="spruce_log", frame="stripped_spruce_log", beam="stripped_spruce_log", base="cobblestone",
             stairs="spruce_stairs", slab="spruce_slab[type=bottom]", gable="spruce_planks", eave="dark_oak_stairs",
             floor="dark_oak_planks", door="spruce", shutters="spruce_trapdoor")
TAVERN = dict(HALF_TIMBER, wall="white_terracotta", frame="dark_oak_log", beam="stripped_dark_oak_log",
              stairs="dark_oak_stairs", slab="dark_oak_slab[type=bottom]", gable="dark_oak_planks", eave="spruce_stairs",
              base="stone_bricks", floor="dark_oak_planks", door="dark_oak", shutters="dark_oak_trapdoor")
GUILD = dict(HALF_TIMBER, wall="mud_bricks", frame="stripped_oak_log", beam="oak_log", base="tuff_bricks",
             stairs="waxed_cut_copper_stairs", slab="waxed_cut_copper_slab[type=bottom]", gable="oak_planks",
             eave="oak_stairs", floor="oak_planks", door="oak", shutters="oak_trapdoor")
FORGE = dict(HALF_TIMBER, wall="polished_blackstone_bricks", frame="blackstone", beam="polished_basalt", base="blackstone",
             window="iron_bars", shutters=None, flowers=False, stairs="brick_stairs",
             slab="brick_slab[type=bottom]", gable="bricks", eave="polished_blackstone_brick_stairs",
             floor="polished_deepslate", door="iron", plinth="polished_blackstone_bricks")


# Interior furniture. Everything is a block (block entities keep their items in the saved structure); no item frames
# or armor stands, since entities are not saved with the hub.
def room(x0, z0, y):
    """put(c, r, dy, block) relative to a building's inside: column c from its west wall, row r from its north wall,
    dy above the floor."""
    return lambda c, r, dy, block: b.set(x0 + 1 + c, y + 1 + dy, z0 + 1 + r, block)


def shelf(items, facing, wood="oak"):
    """A wall shelf showing up to three items (None leaves a slot empty)."""
    slots = ",".join(f'{{Slot:{i}b,id:"minecraft:{item}",count:1}}' for i, item in enumerate(items) if item)
    return f"{wood}_shelf[facing={facing}]{{Items:[{slots}]}}"


def bookshelf(facing, slots):
    """A chiseled bookshelf holding books in the given slots (0-2 top row, 3-5 bottom)."""
    state = ",".join(f"slot_{i}_occupied={str(i in slots).lower()}" for i in range(6))
    books = ",".join(f'{{Slot:{i}b,id:"minecraft:book",count:1}}' for i in slots)
    return f"chiseled_bookshelf[facing={facing},{state}]{{Items:[{books}]}}"


def deco_pot(facing, back, left, right, front):
    # 26.x stores the four faces as a map, not the old [back, left, right, front] list.
    sherds = ",".join(f'{side}:"minecraft:{s}"' for side, s in zip(("back", "left", "right", "front"), (back, left, right, front)))
    return f"decorated_pot[facing={facing}]{{sherds:{{{sherds}}}}}"


def bed(put, c, r, dy, facing, color):
    """A bed with its foot at c, r and its head one block towards `facing`."""
    dc, dr = DIRS[facing]
    put(c, r, dy, f"{color}_bed[facing={facing},part=foot]")
    put(c + dc, r + dr, dy, f"{color}_bed[facing={facing},part=head]")


def banner(*patterns):
    """Banner block-entity data; the base colour is the block's (blue_wall_banner...)."""
    return "{patterns:[" + ",".join(f'{{pattern:"minecraft:{p}",color:"{c}"}}' for p, c in patterns) + "]}"


BLUEPRINT = banner(("small_stripes", "light_blue"), ("stripe_middle", "white"), ("border", "white"))
BLUEPRINT_PLAN = banner(("small_stripes", "light_blue"), ("straight_cross", "white"), ("border", "white"))
PLAID = banner(("small_stripes", "black"), ("stripe_top", "black"), ("stripe_middle", "black"), ("stripe_bottom", "black"))


def dome(cx, cy, cz, r, shell="waxed_oxidized_copper", cap=True):
    for dx in range(-r, r + 1):
        for dy in range(0, r + 1):
            for dz in range(-r, r + 1):
                d = math.sqrt(dx * dx + dy * dy * 1.15 + dz * dz)
                if r - 1.1 < d <= r:
                    b.set(cx + dx, cy + dy, cz + dz, shell)
    if cap:
        b.set(cx, cy + r, cz, "gold_block")
        b.set(cx, cy + r + 1, cz, "waxed_lightning_rod")


# ---------------------------------------------------------------- the Crown Terrace: bank, forge, guild, windmill
def bank():
    x0, z0, x1, z1, y = -12, -54, 12, -39, TERRACE
    check("bank", x0 - 1, z0 - 1, x1 + 1, z1 + 5)
    b.claim(x0 - 1, z0 - 1, x1 + 1, z1 + 5)
    b.fill(x0 - 1, y - 3, z0 - 1, x1 + 1, y - 1, z1 + 4, "stone_bricks")
    b.fill(x0 - 1, y, z0 - 1, x1 + 1, y, z1 + 4, "polished_blackstone_bricks")
    for x in range(x0 + 1, x1):
        for z in range(z0 + 1, z1):
            b.set(x, y, z, "calcite" if (x + z) % 2 else "polished_deepslate")
    for x in range(x0, x1 + 1):
        for z in range(z0, z1 + 1):
            if x not in (x0, x1) and z not in (z0, z1):
                continue
            along = (x - x0) if z in (z0, z1) else (z - z0)
            for yy in range(y + 1, y + 11):
                if yy == y + 1:
                    block = "polished_blackstone_bricks"
                elif yy == y + 8:
                    block = "gold_block" if along % 2 == 0 else "polished_blackstone_bricks"
                elif x in (x0, x1) and z in (z0, z1) or along % 4 == 0:
                    block = "quartz_pillar"
                elif y + 3 <= yy <= y + 6 and along % 2 == 0:
                    block = "glass_pane"
                else:
                    block = "calcite"
                b.set(x, yy, z, block)
    b.fill(x0, y + 11, z0, x1, y + 11, z1, "polished_deepslate")
    for x in range(x0, x1 + 1):
        for z in range(z0, z1 + 1):
            if x in (x0, x1) or z in (z0, z1):
                b.set(x, y + 12, z, "polished_blackstone_brick_wall")
    for x, z in ((x0, z0), (x1, z0), (x0, z1), (x1, z1)):
        b.set(x, y + 12, z, "chiseled_quartz_block")
        b.set(x, y + 13, z, "lantern[hanging=false]")
    dome(0, y + 12, (z0 + z1) // 2, 7)
    # Colonnade porch across the front.
    for x in range(x0 + 1, x1, 4):
        b.set(x, y + 1, z1 + 3, "chiseled_quartz_block")
        b.fill(x, y + 2, z1 + 3, x, y + 8, z1 + 3, "quartz_pillar")
        b.set(x, y + 9, z1 + 3, "chiseled_quartz_block")
    b.fill(x0, y + 10, z1 + 1, x1, y + 10, z1 + 3, "polished_blackstone_bricks")
    for x in range(x0, x1 + 1):
        b.set(x, y + 10, z1 + 4, "polished_blackstone_brick_stairs[facing=north,half=top]")
        b.set(x, y + 11, z1 + 3, "gold_block" if x % 4 == 0 else "polished_blackstone_brick_slab[type=bottom]")
        b.set(x, y, z1 + 4, "polished_blackstone_brick_stairs[facing=north]")
    # A tall open archway, framed in quartz and gold.
    b.fill(-2, y + 1, z1, 2, y + 5, z1, "air")
    b.fill(-3, y + 1, z1, -3, y + 6, z1, "chiseled_quartz_block")
    b.fill(3, y + 1, z1, 3, y + 6, z1, "chiseled_quartz_block")
    b.fill(-2, y + 6, z1, 2, y + 6, z1, "gold_block")
    # No sign: a floating "Skyhaven Bank" text display hangs in the porch instead (tagged skyrpg_bank_text).
    for x in (-7, 7):
        b.set(x, y + 7, z1 + 1, f"black_wall_banner[facing=south]{BANNER}")
    # Inside: a teller counter with bars, gold stacked in the vault behind, chandeliers.
    cz = z0 + 6
    b.fill(x0 + 2, y + 1, cz, x1 - 2, y + 1, cz, "polished_blackstone_bricks")
    b.fill(x0 + 2, y + 2, cz, x1 - 2, y + 2, cz, "dark_oak_slab[type=bottom]")
    b.fill(-1, y + 1, cz, 1, y + 2, cz, "air")
    for x in (-2, 2):
        b.set(x, y + 3, cz, "lantern[hanging=false]")
    for x in range(x0 + 2, x1 - 1, 3):
        if abs(x) > 2:
            b.set(x, y + 3, cz, "iron_bars")
            b.set(x, y + 4, cz, "iron_bars")
    b.fill(x0 + 1, y + 1, z0 + 1, x1 - 1, y + 2, z0 + 1, "gold_block")
    b.fill(-2, y + 1, z0 + 1, 2, y + 4, z0 + 1, "iron_block")
    for x, z in ((-7, z0 + 10), (7, z0 + 10), (0, z0 + 12), (0, z0 + 3)):
        b.fill(x, y + 8, z, x, y + 10, z, "iron_chain")
        b.set(x, y + 7, z, "lantern[hanging=true]")
    # A bright hall: froglight panels across the ceiling, plus hidden light blocks above head height (above the bars).
    for x in range(x0 + 2, x1 - 1, 4):
        for z in range(z0 + 2, z1 - 1, 4):
            b.set(x, y + 11, z, "pearlescent_froglight")
            b.set(x, y + 5, z, "light[level=15]")
    spot("banker", 0, cz, y + 1, yaw=0.0)
    b.claim(x0, z0, x1, z1)


def emberforge():
    x0, z0, x1, z1, y = 19, -44, 31, -35, TERRACE
    building("emberforge", x0, z0, x1, z1, y, "south", FORGE, wall_h=6, sign=("", "EMBERFORGE"), chimney=(29, -41))
    yard = (z1 + 1, z1 + 4)
    b.fill(x0 - 1, y, yard[0], x1 + 1, y, yard[1], "polished_blackstone")
    for x, block in ((20, "anvil[facing=east]"), (21, "blast_furnace[facing=south,lit=true]"), (22, "lava_cauldron"),
                     (23, "lava_cauldron"), (28, "skyrpg_items:artisan_bench[facing=south]"), (29, "smithing_table"),
                     (30, "grindstone[face=floor,facing=north]")):  # Brannoc's Artisan Bench (the welcome quest sends you to it)
        b.set(x, y + 1, z1 + 3, block)
    for x in range(x0, x1 + 1):
        b.set(x, y + 4, yard[1], "deepslate_brick_slab[type=top]")
    for x in (x0, x1):
        b.fill(x, y + 1, yard[1], x, y + 3, yard[1], "polished_blackstone_wall")
    # The hearth glows through the barred windows.
    b.fill(x0 + 1, y, z0 + 1, x1 - 1, y, z0 + 2, "magma_block")
    b.fill(x0 + 2, y + 1, z0 + 1, x0 + 4, y + 1, z0 + 1, "blast_furnace[facing=south,lit=true]")
    b.claim(x0, yard[0], x1, yard[1])
    spot("blacksmith", 26, z1 + 2, y + 1)


def masons_guild():
    x0, z0, x1, z1, y = -31, -44, -19, -35, TERRACE
    building("mason's guild", x0, z0, x1, z1, y, "south", GUILD, wall_h=5, sign=("MASON'S", "GUILD"), chimney=(-21, -41))
    for i, block in enumerate(("stone_bricks", "polished_andesite", "bricks")):
        x = -31 + i * 3
        b.fill(x, y + 1, z1 + 3, x + 1, y + 1 + (i % 2), z1 + 4, block)
    b.claim(-31, z1 + 3, -22, z1 + 4)
    # A scaffolding tower with a little crane lifting a block.
    tx, tz = -34, -38
    check("scaffold", tx - 1, tz - 5, tx + 1, tz + 1)
    b.fill(tx, y + 1, tz, tx, y + 8, tz, "scaffolding[distance=0,bottom=false]")
    b.fill(tx, y + 9, tz - 4, tx, y + 9, tz, "oak_fence")
    b.fill(tx, y + 6, tz - 4, tx, y + 8, tz - 4, "iron_chain")
    b.set(tx, y + 5, tz - 4, "chiseled_tuff_bricks")
    b.claim(tx - 1, tz - 5, tx + 1, tz + 1)
    spot("builder", -23, z1 + 2, y + 1)
    guild_interior(room(x0, z0, y))


def guild_interior(put):
    """The guild hall, 11 x 8 inside (c 0-10 west to east, r 0-7 north to south; door at c5 on the south wall, the
    chimney at c9 r2). Windows: north c1/c5/c9, south c1/c9, west and east r1/r5; cells in front of them stay low."""
    # Workbenches along the north wall, tool racks above them.
    put(0, 0, 0, "barrel[facing=up]")
    put(0, 0, 1, "potted_cactus")
    put(1, 0, 0, "stonecutter[facing=south]")
    put(2, 0, 0, "smithing_table")
    put(3, 0, 0, "grindstone[face=floor,facing=south]")
    put(4, 0, 0, "stonecutter[facing=south]")
    put(2, 0, 2, shelf(("iron_pickaxe", "brush", "stone_pickaxe"), "south"))
    put(4, 0, 2, shelf(("iron_shovel", "string", "compass"), "south"))
    # Pattern books, a lectern with the plans, and the blueprints on the wall.
    for c in (6, 8):
        put(c, 0, 0, bookshelf("south", (0, 2, 3, 4)))
        put(c, 0, 1, bookshelf("south", (1, 3, 5) if c == 6 else (0, 1, 4)))
        put(c, 0, 2, deco_pot("south", "brick", "miner_pottery_sherd", "shelter_pottery_sherd", "brick") if c == 6
            else deco_pot("south", "brick", "heart_pottery_sherd", "plenty_pottery_sherd", "brick"))
    put(7, 0, 0, 'lectern[facing=south,has_book=true]{Book:{id:"minecraft:writable_book",count:1}}')
    for c, r, facing, pattern in ((3, 0, "south", BLUEPRINT), (7, 0, "south", BLUEPRINT_PLAN),
                                  (3, 7, "north", BLUEPRINT_PLAN), (7, 7, "north", BLUEPRINT)):
        put(c, r, 3, f"blue_wall_banner[facing={facing}]{pattern}")
    # The brick kiln: the chimney's base fires bricks, stacked in the corner beside it.
    put(9, 2, 0, "furnace[facing=west,lit=true]")
    put(9, 0, 0, "brick_slab[type=bottom]")
    put(10, 0, 0, "bricks")
    put(10, 0, 1, "brick_slab[type=bottom]")
    put(10, 1, 0, "mud_brick_slab[type=bottom]")
    put(10, 2, 0, deco_pot("west", "brick", "brick", "brick", "brick"))
    # Stone samples on the west wall: signature blocks below, shelves of samples above.
    for r, block in ((2, "chiseled_tuff_bricks"), (3, "chiseled_stone_bricks"), (4, "chiseled_deepslate")):
        put(0, r, 0, block)
    for r, low, high in ((2, ("granite", "diorite", "andesite"), ("stone_bricks", "mossy_stone_bricks", "cracked_stone_bricks")),
                         (3, ("calcite", "tuff", "dripstone_block"), ("polished_tuff", "smooth_stone", "polished_andesite")),
                         (4, ("cinnabar", "sulfur", "resin_bricks"), ("deepslate_bricks", "deepslate_tiles", "polished_deepslate"))):
        put(0, r, 1, shelf(low, "east"))
        put(0, r, 2, shelf(high, "east"))
    # Pattern shelves on the east wall, a copper golem statue on a plinth in the corner.
    for r in (3, 4):
        put(10, r, 0, bookshelf("west", (0, 1, 3, 5) if r == 3 else (1, 2, 4)))
        put(10, r, 1, bookshelf("west", (2, 3, 4) if r == 3 else (0, 3, 5)))
    put(10, 3, 2, deco_pot("west", "brick", "archer_pottery_sherd", "prize_pottery_sherd", "brick"))
    put(10, 6, 0, deco_pot("west", "brick", "brick", "brick", "brick"))
    put(10, 7, 0, "chiseled_tuff")
    put(10, 7, 1, "waxed_copper_golem_statue[facing=west,copper_golem_pose=star]")
    # The drafting table on a tuff inlay in the middle of the hall.
    for c in range(3, 8):
        for r in range(2, 6):
            put(c, r, -1, "polished_tuff" if c in (3, 7) or r in (2, 5) else "tuff_bricks")
    put(4, 3, 0, "oak_slab[type=top]")
    put(5, 3, 0, "cartography_table")
    put(6, 3, 0, "oak_slab[type=top]")
    put(4, 3, 1, "candle[candles=3,lit=true]")
    put(6, 3, 1, "light_blue_candle[candles=2,lit=true]")
    put(4, 4, 0, "oak_stairs[facing=south]")
    put(6, 4, 0, "oak_stairs[facing=south]")
    put(5, 2, 0, "oak_stairs[facing=north]")
    # The crane corner (south-west): a mast and jib with a pulley lowering a block over the stone pile.
    put(0, 5, 0, "polished_andesite")
    for dy in range(6):
        put(0, 6, dy, "stripped_oak_log")
    put(1, 6, 5, "stripped_oak_log[axis=x]")
    put(2, 6, 5, "stripped_oak_log[axis=x]")
    put(1, 6, 4, "oak_stairs[facing=west,half=top]")
    put(2, 6, 4, "grindstone[face=ceiling,facing=east]")
    put(2, 6, 3, "iron_chain[axis=y]")
    put(2, 6, 2, "chiseled_tuff_bricks")
    put(0, 7, 0, "stone_bricks")
    put(0, 7, 1, "stone_bricks")
    put(0, 7, 2, "stone_brick_slab[type=bottom]")
    put(1, 6, 0, "smooth_stone")
    put(1, 6, 1, "smooth_stone_slab[type=bottom]")
    put(1, 7, 0, "tuff_bricks")
    put(2, 7, 0, "cobblestone_slab[type=bottom]")
    # By the door: plants, a guest book, a mat.
    put(4, 7, 0, "potted_azalea_bush")
    put(6, 7, 0, "potted_azalea_bush")
    put(8, 7, 0, 'lectern[facing=north,has_book=true]{Book:{id:"minecraft:writable_book",count:1}}')
    put(9, 7, 0, deco_pot("north", "brick", "brick", "brick", "brick"))
    put(5, 7, 0, "brown_carpet")
    # Tie beams with lanterns, plus hidden lights so the hall is bright.
    for c in (3, 7):
        for r in range(8):
            put(c, r, 4, "oak_log[axis=z]")
        for r in (2, 5):
            put(c, r, 3, "lantern[hanging=true]")
    for c, r in ((1, 3), (9, 5), (5, 5)):
        put(c, r, 3, "light[level=14]")


def windmill(cx, cz):
    y = tops[(cx, cz)]
    check("windmill", cx - 4, cz - 4, cx + 4, cz + 4)
    b.claim(cx - 4, cz - 4, cx + 4, cz + 4)
    b.fill(cx - 3, y - 2, cz - 3, cx + 3, y, cz + 3, "stone_bricks")
    for dx in range(-3, 4):
        for dz in range(-3, 4):
            d = math.hypot(dx, dz)
            for yy in range(y + 1, y + 14):
                taper = 3.3 - (yy - y) * 0.065
                if taper - 1.05 <= d <= taper:
                    b.set(cx + dx, yy, cz + dz, "stone_bricks" if yy < y + 3 else
                          "calcite" if (yy - y) % 4 else "stripped_birch_log")
    for dx in range(-3, 4):
        for dz in range(-3, 4):
            for yy in range(0, 5):
                if math.hypot(dx, dz) <= 3.0 - yy * 0.68:
                    b.set(cx + dx, y + 14 + yy, cz + dz, "spruce_planks" if yy < 3 else "dark_oak_planks")
    b.set(cx, y + 19, cz, "waxed_lightning_rod")
    b.set(cx + 3, y + 10, cz, "stripped_dark_oak_log[axis=x]")
    b.set(cx + 4, y + 10, cz, "stripped_dark_oak_log[axis=x]")
    b.door(cx, y + 1, cz + 3, "south", "spruce", "left")
    b.set(cx, y + 7, cz + 3, "glass_pane")
    return cx + 4.5, y + 10.5, cz + 0.5


bank()
emberforge()
masons_guild()
WINDMILL_HUB = windmill(-43, -31)

# Flower beds along the terrace avenue.
for z in range(-34, -26):
    for x in (-5, -4, -3, 3, 4, 5):
        if (x, z) not in b.occupied:
            b.set(x, TERRACE, z, "grass_block")
            b.set(x, TERRACE + 1, z, "azalea_leaves[persistent=true]" if abs(x) == 5 and z % 3 else
                  "flowering_azalea_leaves[persistent=true]" if abs(x) == 5 else
                  pick({"allium": 1, "lily_of_the_valley": 1, "cornflower": 1,
                        "wildflowers[flower_amount=4,facing=east]": 2}, x, 7, z))
            b.occupied.add((x, z))


# ---------------------------------------------------------------- Greenhollow (west): farm, fields, waterfall
def farmhouse_interior(put):
    """The farmer's home, 13 x 9 inside (c 0-12 west to east, r 0-8 north to south; door at c6 on the south wall, the
    chimney at c11 r2). Windows: north c1/c5/c9, south c1/c9, west and east r1/r5; cells in front of them stay low."""
    # Bed nook (north-west): a double bed, a nightstand with a candle, a chest and a rug.
    for c in (0, 1):
        bed(put, c, 1, 0, "north", "green")
    put(2, 0, 0, "barrel[facing=up]")
    put(2, 0, 1, "candle[candles=3,lit=true]")
    put(3, 0, 0, "potted_flowering_azalea_bush")
    put(0, 2, 0, "chest[facing=east]")
    for c in (1, 2, 3):
        for r in (2, 3):
            put(c, r, 0, "yellow_carpet" if (c + r) % 2 else "white_carpet")
    # Kitchen (north-east): counter with a sink under the window, a stove in the chimney's base, shelves of food.
    put(8, 0, 0, "barrel[facing=up]")
    put(8, 0, 1, "potted_red_tulip")
    put(9, 0, 0, "water_cauldron[level=3]")
    put(10, 0, 0, "stripped_dark_oak_wood")
    put(10, 0, 1, "spruce_pressure_plate")
    put(11, 0, 0, "stripped_dark_oak_wood")
    put(11, 0, 1, "lantern[hanging=false]")
    put(12, 0, 0, "barrel[facing=up]")
    put(11, 1, 0, "stripped_dark_oak_wood")
    put(12, 1, 0, "barrel[facing=west]")
    put(11, 2, 0, "smoker[facing=west,lit=true]")
    put(12, 2, 0, "stripped_dark_oak_wood")
    put(12, 2, 1, "potted_orange_tulip")
    put(12, 3, 0, "composter[level=6]")
    put(10, 0, 2, shelf(("bread", "bowl", "honey_bottle"), "south", "spruce"))
    put(11, 0, 2, shelf(("apple", "pumpkin_pie", "cookie"), "south", "spruce"))
    put(12, 0, 2, shelf(("milk_bucket", "egg", "sugar"), "south", "spruce"))
    # Dining table in the middle.
    for c in (5, 6, 7):
        put(c, 4, 0, "dark_oak_slab[type=top]")
        put(c, 3, 0, "spruce_stairs[facing=north]")
        put(c, 5, 0, "spruce_stairs[facing=south]")
    put(4, 4, 0, "spruce_stairs[facing=west]")
    put(8, 4, 0, "spruce_stairs[facing=east]")
    put(5, 4, 1, "candle[candles=2,lit=true]")
    put(6, 4, 1, "potted_cornflower")
    put(7, 4, 1, "cake")
    # Produce along the walls: hay, pumpkins, melons, barrels and shelves of crops and seeds.
    put(0, 5, 0, "hay_block")
    put(0, 6, 0, "melon")
    put(0, 6, 1, shelf(("carrot", "potato", "beetroot"), "east", "spruce"))
    put(0, 6, 2, shelf(("wheat_seeds", "pumpkin_seeds", "melon_seeds"), "east", "spruce"))
    put(0, 7, 0, "barrel[facing=up]")
    put(0, 7, 1, shelf(("wheat", "apple", "sweet_berries"), "east", "spruce"))
    put(0, 8, 0, "hay_block")
    put(0, 8, 1, "hay_block")
    put(1, 8, 0, "pumpkin")
    put(2, 8, 0, "barrel[facing=up]")
    put(2, 8, 1, "potted_dandelion")
    put(12, 5, 0, "melon")
    put(12, 6, 0, "barrel[facing=west]")
    put(12, 6, 1, shelf(("glow_berries", "honeycomb", "cocoa_beans"), "west", "spruce"))
    put(12, 7, 0, "hay_block")
    put(12, 7, 1, "hay_block")
    put(12, 8, 0, "barrel[facing=up]")
    put(12, 8, 1, "barrel[facing=up]")
    put(11, 8, 0, "pumpkin")
    put(10, 8, 0, "composter[level=7]")
    put(9, 8, 0, "carved_pumpkin[facing=north]")
    # Plants either side of the door, a mat, another under the north window.
    put(4, 8, 0, "potted_fern")
    put(8, 8, 0, "potted_azalea_bush")
    put(6, 8, 0, "brown_carpet")
    put(5, 0, 0, "potted_azalea_bush")
    # Tie beams carry lanterns and bunches of drying herbs (roots, glow berries).
    for c in (3, 7, 11):
        for r in range(9):
            if (c, r) != (11, 2):
                put(c, r, 4, "stripped_dark_oak_log[axis=z]")
    for c, r in ((3, 2), (7, 4), (11, 6)):
        put(c, r, 3, "lantern[hanging=true]")
    for c, r, herb in ((3, 0, "hanging_roots"), (3, 4, "cave_vines[age=25,berries=true]"), (3, 6, "hanging_roots"),
                       (7, 1, "cave_vines[age=25,berries=true]"), (7, 6, "hanging_roots"), (7, 8, "hanging_roots"),
                       (11, 0, "hanging_roots"), (11, 4, "cave_vines[age=25,berries=true]"), (11, 8, "hanging_roots"),
                       (3, 8, "cave_vines[age=25,berries=true]")):
        put(c, r, 3, herb)
    for c, r in ((1, 6), (10, 6)):
        put(c, r, 3, "light[level=13]")


def greenhollow():
    y = G
    ox, oz = building("farmhouse", -45, -21, -31, -11, y, "south", FARMHOUSE, wall_h=5, sign=("GREENHOLLOW", "FARM"),
                      chimney=(-33, -18))
    spot("farmer", -35, -8, y + 1)
    farmhouse_interior(room(-45, -21, y))
    path([(ox, oz + 1), (ox, -3)])
    fx0, fz0, fx1, fz1 = -50, 7, -24, 25
    check("fields", fx0, fz0, fx1, fz1)
    b.claim(fx0, fz0, fx1, fz1, 1)
    crops = ["wheat[age=7]", "carrots[age=7]", "potatoes[age=7]", "beetroots[age=3]"]
    for x in range(fx0, fx1 + 1):
        for z in range(fz0, fz1 + 1):
            if (x, z) not in tops:
                continue
            if x in (fx0, fx1) or z in (fz0, fz1):
                b.set(x, y, z, "grass_block")
                b.set(x, y + 1, z, "oak_fence" if (x - fx0) % 6 == 0 and (z - fz0) % 6 == 0 else "oak_leaves[persistent=true]")
            elif (z - fz0) % 5 == 0:
                b.set(x, y, z, "water")
                if hash01(x, 2, z) < 0.15:
                    b.set(x, y + 1, z, "lily_pad")
            else:
                b.set(x, y, z, "farmland[moisture=7]")
                b.set(x, y + 1, z, crops[((z - fz0) // 5) % len(crops)])
    for x in (-38, -36):
        b.set(x, y + 1, fz0, "spruce_fence_gate[facing=north,open=false]")
    for x, z in ((-44, 15), (-30, 20)):
        b.set(x, y + 1, z, "oak_fence")
        b.set(x, y + 2, z, "hay_block")
        b.set(x, y + 3, z, "carved_pumpkin[facing=north]")
    for x, z, block in ((-46, -9, "hay_block"), (-46, -8, "hay_block"), (-45, -8, "pumpkin"), (-30, -9, "composter"),
                        (-29, -9, "barrel[facing=up]"), (-28, -9, "melon"), (-46, -10, "hay_block"),
                        (-47, -16, "bee_nest[facing=west]"), (-47, -13, "beehive[facing=west]")):
        if (x, z) in tops and on_main(x, z, 1):
            b.set(x, y + 1, z, block)
            b.occupied.add((x, z))


greenhollow()

# The Mistfall: the west avenue ends in a stone channel that pours off the edge onto a pond islet below.
edge_x = -END["west"]
while on_main(edge_x - 1, 0, 0):
    edge_x -= 1
for x in range(edge_x, -END["west"] + 2):
    for z in (-1, 1):
        b.set(x, G, z, "polished_blackstone_bricks")
        b.set(x, G + 1, z, "polished_blackstone_brick_wall")
        b.occupied.add((x, z))
    b.set(x, G, 0, "water")
    b.set(x, G - 1, 0, "polished_blackstone_bricks")
    b.occupied.add((x, 0))
for z in (-1, 1):
    b.set(-END["west"] + 2, G + 1, z, "polished_blackstone_bricks")
    b.set(-END["west"] + 2, G + 2, z, "lantern[hanging=false]")


def islet(cx, cy, cz, r, top_block="grass_block"):
    """A small floating island with a rocky cone underneath. Returns {(x, z): top}."""
    cells = {}
    for x in range(cx - r - 2, cx + r + 3):
        for z in range(cz - r - 2, cz + r + 3):
            d = math.hypot(x - cx, z - cz) / (r + noise(x, z) * 1.2)
            if d > 1:
                continue
            depth = int(3 + (r * 1.7) * (1 - d ** 1.4) + noise(z, x) * 2)
            b.set(x, cy, z, top_block)
            for y in range(cy - depth, cy):
                b.set(x, y, z, "dirt" if y >= cy - 2 else stratum(x, y, z))
            cells[(x, z)] = (cy, cy - depth)
    for (x, z), (top, bottom) in cells.items():
        rim = any((x + dx, z + dz) not in cells for dx, dz in DIRS.values())
        underside(x, z, bottom, 1.5, rim)
    return {k: v[0] for k, v in cells.items()}


def flora(cells, chance=0.3):
    for (x, z), top in cells.items():
        if b.get(x, top, z) == "grass_block" and b.get(x, top + 1, z) == "air" and hash01(x, 5, z) < chance:
            b.set(x, top + 1, z, pick({"short_grass": 4, "fern": 2, "blue_orchid": 1, "lily_of_the_valley": 1,
                                       "firefly_bush": 1}, x, 2, z))


fall_x = edge_x - 1
mist = islet(fall_x - 3, 46, 0, 7)
for (x, z) in list(mist):
    if math.hypot(x - (fall_x - 1), z) < 2.7:
        b.set(x, 46, z, "water")
        b.set(x, 45, z, "water")
        b.set(x, 44, z, "clay")
        if hash01(x, 9, z) < 0.25 and (x, z) != (fall_x, 0):
            b.set(x, 47, z, "lily_pad")
flora(mist)


# ---------------------------------------------------------------- Timberline (south-west): lodge and great trees
def great_tree(x, z, kind="oak", height=None, y=None):
    y = tops.get((x, z)) if y is None else y
    if y is None:
        return
    log, leaves = {"oak": ("oak_log", "oak_leaves"), "birch": ("birch_log", "birch_leaves"),
                   "spruce": ("spruce_log", "spruce_leaves"), "dark": ("dark_oak_log", "dark_oak_leaves"),
                   "azalea": ("oak_log", "flowering_azalea_leaves")}[kind]
    h = height or rng.randint(8, 12)
    thick = kind in ("oak", "dark") and h >= 10
    for yy in range(y + 1, y + h + 1):
        for dx in ((0, 1) if thick else (0,)):
            for dz in ((0, 1) if thick else (0,)):
                b.set(x + dx, yy, z + dz, log)
    for dx, dz in ((-1, 0), (2 if thick else 1, 0), (0, -1), (0, 2 if thick else 1)):
        if hash01(x + dx, y, z + dz) < 0.7:
            b.set(x + dx, y + 1, z + dz, log)
    if kind == "spruce":
        for yy in range(3, h + 3):
            r = max(1, round((h + 3 - yy) * 0.45)) if yy < h + 2 else 0
            for dx in range(-r, r + 1):
                for dz in range(-r, r + 1):
                    if dx * dx + dz * dz <= r * r + (yy % 2) and hash01(x + dx, yy, z + dz) > 0.06:
                        b.setif(x + dx, y + yy, z + dz, f"{leaves}[persistent=true]")
        b.set(x, y + h + 2, z, f"{leaves}[persistent=true]")
        b.claim(x - 2, z - 2, x + 2, z + 2)
        return
    clusters = [(0, h + 1, 0, 4 if thick else 3)]
    for i in range(3 if thick else 2):
        a = rng.uniform(0, math.tau)
        bl = rng.randint(3, 4)
        by = h - rng.randint(1, 3)
        for s in range(1, bl + 1):
            bx, bz = x + round(math.cos(a) * s), z + round(math.sin(a) * s)
            axis = "x" if abs(math.cos(a)) > abs(math.sin(a)) else "z"
            b.set(bx, y + by + s // 2, bz, f"{log}[axis={axis}]")
        clusters.append((round(math.cos(a) * bl), by + bl // 2 + 1, round(math.sin(a) * bl), 3))
    for cx, cy, cz, r in clusters:
        for dx in range(-r, r + 1):
            for dy in range(-2, 3):
                for dz in range(-r, r + 1):
                    if (dx * dx + dz * dz) / (r * r) + (dy * dy) / 4.5 <= 1 and \
                            hash01(x + cx + dx, cy + dy, z + cz + dz) > 0.07:
                        b.setif(x + cx + dx, y + cy + dy, z + cz + dz, f"{leaves}[persistent=true]")
    b.claim(x - 2, z - 2, x + 3, z + 3)


def lodge_interior(put):
    """The lumberjack's hunting lodge, 11 x 9 inside (c 0-10 west to east, r 0-8 north to south; door at c5 on the north
    wall, the chimney at c1 r6). Windows: north c1/c9, south c1/c5/c9, west and east r1/r5; cells in front stay low."""
    # Fireplace: a fieldstone chimney breast round the chimney, a campfire in its mouth, a log mantel with candles
    # and a boar's head (a piglin head) above.
    def fieldstone(c, r, dy):
        return pick({"cobblestone": 3, "mossy_cobblestone": 2, "stone": 1}, c, dy, r)
    for r in (5, 6, 7):
        for dy in range(5):
            put(1, r, dy, fieldstone(1, r, dy))
    for dy in range(5):
        put(0, 6, dy, fieldstone(0, 6, dy))
    put(1, 6, 0, "campfire[lit=true,signal_fire=false,facing=east]")
    for r in (5, 6, 7):
        put(2, r, 2, "stripped_spruce_log[axis=z]")
    put(2, 5, 3, "candle[candles=3,lit=true]")
    put(2, 7, 3, "candle[candles=2,lit=true]")
    put(2, 6, 3, "piglin_wall_head[facing=east]")
    # Firewood stacked beside it.
    put(0, 5, 0, "stripped_spruce_log[axis=x]")
    for r in (7, 8):
        put(0, r, 0, "spruce_log[axis=x]")
        put(0, r, 1, "oak_log[axis=x]" if r == 7 else "spruce_log[axis=x]")
    put(1, 8, 0, "spruce_log[axis=x]")
    # A plaid rug before the fire and a red log-framed couch facing it.
    for c in (2, 3):
        for r in (5, 6, 7):
            put(c, r, 0, "red_carpet" if (c + r) % 2 else "black_carpet")
    for r in (5, 6, 7):
        put(4, r, 0, "red_wool_stairs[facing=east]")
    for r in (4, 8):
        put(4, r, 0, "stripped_spruce_log")
    put(4, 4, 1, "lantern[hanging=false]")
    put(4, 8, 1, "potted_fern")
    # Bunk beds against the south wall, a barrel nightstand between them.
    for c, low, high in ((6, "red", "green"), (8, "green", "red")):
        bed(put, c, 7, 0, "south", low)
        put(c, 7, 1, "spruce_slab[type=top]")
        put(c, 8, 1, "spruce_slab[type=top]")
        bed(put, c, 7, 2, "south", high)
    put(7, 8, 0, "barrel[facing=up]")
    put(7, 8, 1, "lantern[hanging=false]")
    for c in (6, 7, 8):
        put(c, 6, 0, "brown_carpet")
    # The map table: a cartography table between slab ends, log benches either side, maps on the wall.
    put(6, 3, 0, "spruce_slab[type=top]")
    put(7, 3, 0, "cartography_table")
    put(8, 3, 0, "spruce_slab[type=top]")
    put(6, 3, 1, "candle[candles=4,lit=true]")
    put(8, 3, 1, "potted_spruce_sapling")
    for c in (6, 7, 8):
        put(c, 2, 0, "stripped_spruce_log[axis=x]")
        put(c, 4, 0, "stripped_spruce_log[axis=x]")
    put(8, 0, 2, shelf(("map", "compass", "spyglass"), "south", "spruce"))
    # Trophy wall (east): a bench under shelves of prize axes and hunting gear.
    for r in (2, 3, 4):
        put(10, r, 0, "spruce_stairs[facing=east]")
    put(10, 2, 3, shelf(("stone_axe", "iron_axe", "copper_axe"), "west", "spruce"))
    put(10, 3, 3, shelf((None, "netherite_axe", None), "west", "spruce"))
    put(10, 4, 3, shelf(("golden_axe", "diamond_axe", "wooden_axe"), "west", "spruce"))
    put(10, 2, 2, shelf(("bow", "arrow", "crossbow"), "west", "spruce"))
    put(10, 3, 2, shelf(("goat_horn", "rabbit_hide", "feather"), "west", "spruce"))
    put(10, 4, 2, shelf(("leather", "rabbit_foot", "lead"), "west", "spruce"))
    # Corners: stores, a fletching table, a grindstone for the axes.
    put(0, 0, 0, "barrel[facing=up]")
    put(0, 0, 1, "barrel[facing=up]")
    put(1, 0, 0, "chest[facing=south]")
    put(0, 1, 0, "potted_fern")
    put(10, 0, 0, "fletching_table")
    put(9, 0, 0, "barrel[facing=north]")
    put(10, 5, 0, "potted_spruce_sapling")
    put(10, 6, 0, "grindstone[face=floor,facing=west]")
    put(10, 7, 0, "chest[facing=west]")
    put(10, 8, 0, "barrel[facing=up]")
    put(10, 8, 1, "barrel[facing=up]")
    put(9, 8, 0, "barrel[facing=north]")
    # Plaid banners either side of the door, a runner down the hall.
    for c in (4, 6):
        put(c, 0, 2, f"red_wall_banner[facing=south]{PLAID}")
    put(5, 0, 0, "brown_carpet")
    for r in range(1, 8):
        put(5, r, 0, "green_carpet")
    # Tie beams with lanterns, hidden lights in the far corners.
    for c in (3, 7):
        for r in range(9):
            put(c, r, 4, "stripped_spruce_log[axis=z]")
    for c, r in ((3, 2), (3, 6), (7, 3), (7, 6)):
        put(c, r, 3, "lantern[hanging=true]")
    for c, r in ((1, 2), (9, 6)):
        put(c, r, 3, "light[level=13]")


def timberline():
    y = G
    ox, oz = building("lodge", -38, 30, -26, 40, y, "north", LODGE, wall_h=5, sign=("TIMBERLINE", "LODGE"),
                      chimney=(-36, 37))
    spot("lumberjack", -29, 28, y + 1)
    lodge_interior(room(-38, 30, y))
    for z in range(32, 39):
        b.fill(-24, y + 1, z, -23, y + 1 + (z % 2 == 0), z, "stripped_spruce_log[axis=x]" if z % 3 else "spruce_log[axis=x]")
    b.set(-23, y + 1, 30, "oak_log")
    b.set(-23, y + 2, 30, "oak_pressure_plate")
    b.claim(-24, 30, -22, 39)
    path([(ox, oz - 1), (ox, 27), (-20, 27), (-20, 3)])


timberline()


# ---------------------------------------------------------------- The Gilded Gull (south-east): tavern, garden, gazebo
def gilded_gull():
    y = G
    x0, z0, x1, z1 = 21, 16, 39, 29
    ox, oz = building("tavern", x0, z0, x1, z1, y, "north", TAVERN, wall_h=9, sign=("THE GILDED", "GULL"),
                      chimney=(37, 27), double_door=True)
    # A balcony across the front at the upper floor.
    for x in range(x0, x1 + 1):
        b.set(x, y + 5, z0 - 1, "dark_oak_slab[type=top]")
        b.set(x, y + 6, z0 - 2, "dark_oak_fence")
    for x in (x0, x1):
        b.set(x, y + 6, z0 - 1, "dark_oak_fence")
    # Inside: a bar with barrels behind it, tables, chandeliers on tie beams.
    for x in range(x0 + 3, x1 - 2):
        if x != 30:
            b.set(x, y + 1, 23, "stripped_dark_oak_log[axis=x]")
            b.set(x, y + 2, 23, "dark_oak_slab[type=bottom]")
    b.set(27, y + 2, 23, "brewing_stand")
    b.set(33, y + 2, 23, "cake")
    for x in range(x0 + 1, x1):
        if x != 37:
            b.set(x, y + 1, z1 - 1, "barrel[facing=north]")
            if x % 2:
                b.set(x, y + 2, z1 - 1, "barrel[facing=north]")
    for tx, tz in ((25, 19), (35, 19), (30, 18)):
        b.set(tx, y + 1, tz, "dark_oak_fence")
        b.set(tx, y + 2, tz, "dark_oak_pressure_plate")
        for dx, facing in ((1, "east"), (-1, "west")):
            b.set(tx + dx, y + 1, tz, f"spruce_stairs[facing={facing}]")
    for z in (20, 25):
        b.fill(x0 + 1, y + 8, z, x1 - 1, y + 8, z, "stripped_dark_oak_log[axis=x]")
        for x in (25, 30, 35):
            b.set(x, y + 7, z, "lantern[hanging=true]")
    spot("tavern", 30, 23, y + 1, yaw=180.0)
    path([(30, oz - 1), (30, 3)])
    path([(31, oz - 1), (31, 3)])
    # Beer garden to the east: tables under string lights.
    gx0, gz0, gx1, gz1 = 42, 15, 48, 28
    check("beer garden", gx0, gz0, gx1, gz1)
    b.claim(gx0, gz0, gx1, gz1)
    for x in range(gx0, gx1 + 1):
        for z in range(gz0, gz1 + 1):
            b.set(x, y, z, pick({"moss_block": 2, "grass_block": 3, "mossy_cobblestone": 1}, x, 3, z))
    for tx, tz in ((45, 18), (45, 22), (45, 26)):
        b.set(tx, y + 1, tz, "dark_oak_fence")
        b.set(tx, y + 2, tz, "dark_oak_pressure_plate")
        b.set(tx + 1, y + 1, tz, "spruce_stairs[facing=east]")
        b.set(tx - 1, y + 1, tz, "spruce_stairs[facing=west]")
    for px in (gx0, gx1):
        for pz in (gz0, gz1):
            b.fill(px, y + 1, pz, px, y + 5, pz, "dark_oak_fence")
            b.set(px, y + 6, pz, "lantern[hanging=false]")
    for x in range(gx0 + 1, gx1):
        for z in (gz0, gz1):
            b.set(x, y + 5, z, "iron_chain[axis=x]")
            if x % 2:
                b.set(x, y + 4, z, "lantern[hanging=true]")
    for z in range(gz0 + 1, gz1):
        for x in (gx0, gx1):
            b.set(x, y + 5, z, "iron_chain[axis=z]")
            if z % 3 == 0:
                b.set(x, y + 4, z, "lantern[hanging=true]")


def gazebo(cx, cz):
    y = tops[(cx, cz)]
    check("gazebo", cx - 5, cz - 5, cx + 5, cz + 5)
    b.claim(cx - 5, cz - 5, cx + 5, cz + 5)
    for dx in range(-5, 6):
        for dz in range(-5, 6):
            d = math.hypot(dx, dz)
            if d <= 4.3:
                b.set(cx + dx, y, cz + dz, "gold_block" if d < 0.5 else "polished_andesite" if d < 3.4 else "polished_diorite")
            if d <= 5.2:
                layer = 0 if d > 3.6 else 1 if d > 2.2 else 2 if d > 0.9 else 3
                b.set(cx + dx, y + 5 + layer, cz + dz, "waxed_weathered_cut_copper_slab[type=bottom]" if layer < 3 else
                      "waxed_weathered_cut_copper")
    for i in range(8):
        a = math.radians(i * 45)
        px, pz = cx + round(math.cos(a) * 3.6), cz + round(math.sin(a) * 3.6)
        b.fill(px, y + 1, pz, px, y + 4, pz, "stripped_birch_log")
    b.set(cx, y + 9, cz, "waxed_lightning_rod")
    b.fill(cx, y + 3, cz, cx, y + 4, cz, "iron_chain")
    b.set(cx, y + 2, cz, "lantern[hanging=true]")
    for dx, dz, facing in ((2, 0, "east"), (-2, 0, "west"), (0, 2, "south"), (0, -2, "north")):
        b.set(cx + dx, y + 1, cz + dz, f"birch_stairs[facing={facing}]")


gilded_gull()
gazebo(12, 36)
path([(3, 36), (7, 36)])


# ---------------------------------------------------------------- leaderboard podiums
# The blocks of a podium; the hub mod's Podiums puts the statues of the top three, their name plates and the title on
# it, from hub.json "podiums" (written below from PODIUMS). Its offsets match Podiums.SIDE / HEIGHT.
PODIUMS = []
PODIUM_BANNER = banner(("gradient_up", "magenta"), ("border", "yellow"), ("circle", "yellow"))


def podium(board, cx, cz):
    """Three pedestals facing north (towards the plaza): #1 in the middle (3 high, gold top), #2 on the visitor's left
    (east, 2 high, iron top), #3 on their right (west, 1 high, copper top), all 3x3 of smooth quartz, on a dark dais
    with a low stepped backdrop behind, lantern posts at the front corners and banners on the back wall."""
    y = tops[(cx, cz)]
    check("podium", cx - 7, cz - 3, cx + 7, cz + 4)
    b.claim(cx - 7, cz - 4, cx + 7, cz + 5)
    for x in range(cx - 6, cx + 7):
        for z in range(cz - 2, cz + 4):
            edge = x in (cx - 6, cx + 6) or z == cz - 2
            b.set(x, y, z, "polished_blackstone" if edge else "polished_blackstone_bricks")
    for side, height, top in ((0, 3, "gold_block"), (4, 2, "iron_block"), (-4, 1, "waxed_copper_block")):
        for dx in (-1, 0, 1):
            for dz in (-1, 0, 1):
                for h in range(1, height):
                    b.set(cx + side + dx, y + h, cz + dz, "smooth_quartz")
                b.set(cx + side + dx, y + height, cz + dz, top)
    # The backdrop: low at the sides, rising behind #1, with a gilded top and banners.
    for x in range(cx - 6, cx + 7):
        d = abs(x - cx)
        h = 5 if d <= 2 else 4 if d <= 4 else 3
        b.fill(x, y + 1, cz + 3, x, y + h - 1, cz + 3, "polished_blackstone_bricks")
        b.set(x, y + h, cz + 3, "gilded_blackstone" if d in (0, 3, 5) else "chiseled_polished_blackstone")
    for x in (cx - 4, cx + 4):
        b.set(x, y + 3, cz + 2, f"magenta_wall_banner[facing=north]{PODIUM_BANNER}")
    for x in (cx - 6, cx + 6):
        b.fill(x, y + 1, cz - 2, x, y + 2, cz - 2, "polished_blackstone_wall")
        b.set(x, y + 3, cz - 2, "lantern[hanging=false]")
    for x, z in ((cx - 2, cz - 1), (cx + 2, cz - 1), (cx, cz + 2)):
        b.set(x, y + 1, z, "light[level=13]")
    PODIUMS.append({"board": board, "center": [cx, y, cz], "facing": "north"})


podium("skyrpg_brainrots:collectors", 12, 25)
path([(3, 21), (12, 21)])


# ---------------------------------------------------------------- the Bazaar Promenade (east avenue) and the koi garden
# The avenue east of the plaza is a tree-lined promenade: cherry trees, benches, flower planters and banner posts, ending
# at the Bazaar Gate, a great arch over the road with a hanging "GRAND BAZAAR" sign, right before the bridge to the Grand
# Bazaar islet (built further down, with the bridges and islets).
BZ_BANNER = banner(("rhombus", "yellow"), ("border", "yellow"))
PROM_X0, PROM_X1 = 21, 56
b.claim(PROM_X0, -9, 61, 9)


def sign_nbt_both(*lines, color="yellow"):
    """A sign (or hanging sign) with the same text on both sides, so it reads from either way."""
    t = sign_nbt(*lines, color=color)
    front = t[len("{front_text:"):t.index("},is_waxed") + 1]
    return "{front_text:" + front + ",back_text:" + front + ",is_waxed:1b}"


for x in range(24, 49, 8):                                 # 24, 32, 40, 48
    for sz in (-1, 1):
        cherry_tree(x, G, 8 * sz, 5)
        for dx, dz in ((1, 1), (1, -1), (-1, 1), (-1, -1), (0, 2), (0, -2), (2, 0), (-2, 0)):
            b.set(x + dx, G + 3, 8 * sz + dz, "light[level=14]")
for x in range(24, 49, 8):
    for sz in (-1, 1):
        # a bench facing the avenue, with trapdoor arms
        facing = "south" if sz < 0 else "north"
        for dx in (-1, 0, 1):
            b.set(x + dx, G + 1, 5 * sz, f"dark_oak_stairs[facing={facing}]")
        b.set(x - 2, G + 1, 5 * sz, "dark_oak_trapdoor[facing=west,open=true]")
        b.set(x + 2, G + 1, 5 * sz, "dark_oak_trapdoor[facing=east,open=true]")
        # a banner post at the avenue's edge
        b.fill(x, G + 1, 3 * sz, x, G + 5, 3 * sz, "stripped_dark_oak_log")
        b.set(x, G + 6, 3 * sz, "gilded_blackstone")
        b.set(x, G + 7, 3 * sz, "lantern[hanging=false]")
        b.set(x + 1, G + 4, 3 * sz, f"red_wall_banner[facing=east]{BZ_BANNER}")
        b.set(x - 1, G + 4, 3 * sz, f"red_wall_banner[facing=west]{BZ_BANNER}")
for x in range(28, PROM_X1, 8):                           # 28, 36, 44, 52: planters between the benches
    for sz in (-1, 1):
        for dx in (-1, 0, 1):
            b.set(x + dx, G + 1, 5 * sz, "grass_block")
            b.set(x + dx, G + 2, 5 * sz, pick({"pink_tulip": 2, "allium": 2, "cornflower": 2, "azure_bluet": 1,
                                               "lily_of_the_valley": 1, "red_tulip": 1, "orange_tulip": 1}, x + dx, 31, sz))
        for dx in (-2, 2):
            b.set(x + dx, G + 1, 5 * sz, "polished_blackstone_brick_wall")

# The Bazaar Gate over the road at x 57..59: two stone towers with a lintel, banners and a hanging sign.
GATE_X0 = 57
for sz in (-1, 1):
    for x in range(GATE_X0, GATE_X0 + 3):
        for zz in (4, 5):
            z = sz * zz
            b.fill(x, G + 1, z, x, G + 7, z, "polished_blackstone_bricks")
            b.set(x, G + 3, z, "gilded_blackstone" if x == GATE_X0 + 1 else "chiseled_polished_blackstone")
            b.set(x, G + 6, z, "chiseled_polished_blackstone")
            b.set(x, G + 8, z, "polished_blackstone_brick_slab[type=bottom]")
            b.occupied.add((x, z))
    for x in range(GATE_X0, GATE_X0 + 3, 2):
        b.set(x, G + 4, 3 * sz, f"red_wall_banner[facing={'south' if sz < 0 else 'north'}]{BZ_BANNER}")
    b.fill(GATE_X0 + 1, G + 8, 5 * sz, GATE_X0 + 1, G + 9, 5 * sz, "polished_blackstone")
    b.set(GATE_X0 + 1, G + 10, 5 * sz, "lantern[hanging=false]")
for z in range(-5, 6):
    for x in range(GATE_X0, GATE_X0 + 3):
        b.set(x, G + 7, z, "polished_blackstone_bricks" if abs(z) > 3 else "deepslate_tiles")
        if abs(z) <= 3:
            b.set(x, G + 8, z, "polished_blackstone_brick_slab[type=bottom]")
        b.occupied.add((x, z))
for x in range(GATE_X0, GATE_X0 + 3):
    for sz in (-1, 1):
        b.set(x, G + 6, 3 * sz, f"polished_blackstone_brick_stairs[facing={'north' if sz < 0 else 'south'},half=top]")
b.set(GATE_X0 + 1, G + 9, 0, "gold_block")
b.set(GATE_X0 + 1, G + 9, 3, "gilded_blackstone")
b.set(GATE_X0 + 1, G + 9, -3, "gilded_blackstone")
b.set(GATE_X0 + 1, G + 6, 0, "dark_oak_hanging_sign[rotation=4,attached=false]" + sign_nbt_both("GRAND", "BAZAAR"))
b.set(GATE_X0 + 1, G + 6, -2, "dark_oak_hanging_sign[rotation=4,attached=false]"
      + sign_nbt_both("Weapons", "Curios", "Flowers", color="white"))
b.set(GATE_X0 + 1, G + 6, 2, "dark_oak_hanging_sign[rotation=4,attached=false]"
      + sign_nbt_both("Provisions", "Fishing", "Slayers", color="white"))
for z in (-1, 1):
    b.set(GATE_X0 + 1, G + 6, z, "lantern[hanging=true]")


def koi_pond(cx, cz, r):
    check("koi pond", cx - r - 2, cz - r - 2, cx + r + 2, cz + r + 2)
    b.claim(round(cx - r - 1), round(cz - r - 1), round(cx + r + 1), round(cz + r + 1))
    for x in range(round(cx - r - 2), round(cx + r + 3)):
        for z in range(round(cz - r - 2), round(cz + r + 3)):
            d = math.hypot(x - cx, z - cz) / (1 + 0.15 * math.sin(math.atan2(z - cz, x - cx) * 3))
            if d < r:
                b.set(x, G - 2, z, "mud" if hash01(x, 1, z) < 0.7 else "clay")
                b.set(x, G - 1, z, "water" if d < r - 1.5 else "mud")
                b.set(x, G, z, "water")
                if hash01(x, 4, z) < 0.12:
                    b.set(x, G + 1, z, "lily_pad")
                elif d < r - 1.5 and hash01(x, 6, z) < 0.08:
                    b.set(x, G - 1, z, "sea_pickle[pickles=3,waterlogged=true]")
            elif d < r + 1.2:
                b.set(x, G, z, pick({"mossy_cobblestone": 3, "moss_block": 2, "stone": 1}, x, 5, z))
                if hash01(x, 7, z) < 0.3:
                    b.set(x, G + 1, z, pick({"fern": 2, "blue_orchid": 1, "moss_carpet": 2, "lily_of_the_valley": 1}, x, 8, z))
    lx, lz = round(cx + r + 1), round(cz)
    b.set(lx, G + 1, lz, "stone_brick_wall")
    b.set(lx, G + 2, lz, "chiseled_stone_bricks")
    b.set(lx, G + 3, lz, "lantern[hanging=false]")


def overlook(x, z, facing):
    """A railed balcony at the island's rim with benches looking out over the void."""
    dx, dz = DIRS[facing]
    sx, sz = (0, 1) if dx else (1, 0)
    y = G
    for depth in range(0, 4):
        for w in range(-4, 5):
            cx, cz = x + dx * depth + sx * w, z + dz * depth + sz * w
            if abs(w) <= 3 - max(0, depth - 2):
                b.set(cx, y, cz, "polished_blackstone_bricks" if abs(w) == 3 or depth == 3 else pick(ROAD, cx, 0, cz))
                b.fill(cx, y - 3, cz, cx, y - 1, cz, "stone_bricks")
                b.occupied.add((cx, cz))
    for w in range(-3, 4):
        b.set(x + dx * 3 + sx * w, y + 1, z + dz * 3 + sz * w, "polished_blackstone_brick_wall")
    for w in (-3, 3):
        for depth in (1, 2):
            b.set(x + dx * depth + sx * w, y + 1, z + dz * depth + sz * w, "polished_blackstone_brick_wall")
        b.set(x + dx * 3 + sx * w, y + 1, z + dz * 3 + sz * w, "polished_blackstone")
        b.set(x + dx * 3 + sx * w, y + 2, z + dz * 3 + sz * w, "lantern[hanging=false]")
    for w in (-2, -1, 1, 2):
        b.set(x + dx + sx * w, y + 1, z + dz + sz * w, f"dark_oak_stairs[facing={OPPOSITE[facing]}]")


koi_pond(36, -17, 5.5)
cherry_tree(46, G, -23, 6)
b.claim(43, -26, 49, -20)


def catch_pool(x, z, y, r=4):
    """A small islet whose pond catches a waterfall at x, z, so no water falls out of the build box."""
    cells = islet(x + 2, y, z, r)
    for (cx, cz) in cells:
        if math.hypot(cx - x, cz - z) < 1.9 and all((cx + dx, cz + dz) in cells for dx, dz in DIRS.values()):
            b.set(cx, y, cz, "water")
            b.set(cx, y - 1, cz, "water")
            b.set(cx, y - 2, cz, "clay")
    flora(cells, 0.3)
    return cells


# A brook runs from the koi pond east and tumbles off the edge onto a catch islet below.
brook = []
x = 41
while True:
    zc = -17 + round(1.6 * math.sin((min(x, 51) - 40) / 3.5))   # straight for the last stretch, so it only spills at the end
    if (x, zc) not in tops or (x, zc + 1) not in tops:
        break
    brook.append((x, zc))
    x += 1
for x, zc in brook:
    for z in (zc, zc + 1):
        b.set(x, G - 2, z, "clay")
        b.set(x, G - 1, z, pick({"gravel": 2, "clay": 1, "mud": 1}, x, 9, z))
        b.set(x, G, z, "water")
        b.blocks.pop((x, G + 1, z), None)
        b.occupied.add((x, z))
    for z in (zc - 1, zc + 2):
        if (x, z) in tops and (x, z) not in b.occupied:
            b.set(x, G, z, pick({"moss_block": 3, "mossy_cobblestone": 2, "grass_block": 2}, x, 10, z))
            if hash01(x, 11, z) < 0.4:
                b.set(x, G + 1, z, pick({"fern": 2, "blue_orchid": 1, "moss_carpet": 1, "lily_of_the_valley": 1}, x, 12, z))
            b.occupied.add((x, z))
BROOK_FALL = (brook[-1][0] + 1, brook[-1][1])
catch_pool(BROOK_FALL[0], BROOK_FALL[1], 36)


# ---------------------------------------------------------------- bridges and islets
def bridge(along, fixed, a0, a1, s0, s1, width=3, wood="spruce", sag=0.0, lamp_every=5):
    """A plank bridge along x or z at `fixed`, from a0 to a1, its walking surface going from height s0 to s1."""
    n = abs(a1 - a0)
    step = 1 if a1 > a0 else -1
    half = width // 2
    for i in range(n + 1):
        a = a0 + i * step
        t = i / n
        surface = round((s0 + (s1 - s0) * t - sag * math.sin(math.pi * t)) * 2) / 2
        half_step = surface % 1 != 0
        deck_y = math.floor(surface) if half_step else int(surface) - 1
        deck = f"{wood}_slab[type=bottom]" if half_step else f"{wood}_planks"
        rail_y = math.ceil(surface)
        for w in range(-half - 1, half + 2):
            x, z = (a, fixed + w) if along == "x" else (fixed + w, a)
            for yy in range(rail_y, rail_y + 3):
                b.blocks.pop((x, yy, z), None)
            if abs(w) <= half:
                b.set(x, deck_y, z, deck)
            else:
                b.set(x, rail_y - 1, z, f"stripped_{wood}_log[axis={along}]")
                b.set(x, rail_y, z, f"{wood}_fence")
                if i % lamp_every == lamp_every // 2:
                    b.set(x, rail_y + 1, z, f"{wood}_fence")
                    b.set(x, rail_y + 2, z, "lantern[hanging=false]")
            b.occupied.add((x, z))


def bridge_ends(along, fixed, start, direction, target_cells):
    """The last cell on the main island and the first on the target islet, walking along a line."""
    point = (lambda v: (v, fixed)) if along == "x" else (lambda v: (fixed, v))
    a = start
    while point(a + direction) in tops:
        a += direction
    end = a + direction
    while point(end) not in target_cells:
        end += direction
    return a, end


# Wildgate: the survival portal, a great ring on its own islet to the south.
GATE = (0, 77)
gate_cells = islet(GATE[0], G, GATE[1], 10)
flora(gate_cells, 0.25)
a, e = bridge_ends("z", 0, END["south"], 1, gate_cells)
bridge("z", 0, a, e + 1, G + 1, G + 1, width=5, wood="dark_oak", sag=1.0, lamp_every=6)
GATE_Z = GATE[1]
RING_C = G + 5          # centre height of the ring; its opening reaches the ground so players walk through
R_IN, R_OUT = 5.0, 7.4
for x in range(-8, 9):
    for y in range(G + 1, G + 14):
        d = math.hypot(x, y - RING_C)
        for z in (GATE_Z, GATE_Z + 1):
            if R_IN <= d <= R_OUT:
                inner_band = d - R_IN < 0.8
                b.set(x, y, z, ("gilded_blackstone" if (x + y) % 3 == 0 else "chiseled_polished_blackstone") if inner_band
                      else "polished_blackstone_bricks")
for x in range(-4, 5):
    for z in range(GATE_Z - 2, GATE_Z + 4):
        b.set(x, G, z, "crying_obsidian" if GATE_Z <= z <= GATE_Z + 1 and abs(x) <= 3 else "polished_blackstone_bricks")
for x, y in ((-6, RING_C + 3), (6, RING_C + 3), (0, RING_C + 7)):
    for z, facing in ((GATE_Z - 1, "north"), (GATE_Z + 2, "south")):
        b.set(x, y, z, f"amethyst_cluster[facing={facing}]")
b.set(0, RING_C + 8, GATE_Z - 1, "dark_oak_wall_sign[facing=north]" + sign_nbt("WILDGATE", "", "to the", "Survival World"))
for x in (-8, 8):
    for z in (GATE_Z - 3, GATE_Z + 3):
        if (x, z) in gate_cells:
            b.fill(x, G + 1, z, x, G + 3, z, "polished_blackstone_wall")
            b.set(x, G + 4, z, "soul_lantern[hanging=false]")
b.claim(-9, GATE_Z - 4, 9, GATE_Z + 4)
PORTAL = {"from": [-3, G + 1, GATE_Z], "to": [3, G + 7, GATE_Z + 1]}

# The Arcane Library: Seraphine's enchanting hall on an islet to the south-east, reached by a bridge from the beer garden.
# Built as a giant enchanting table: dark blackstone and obsidian walls with glowing purple seams and bookshelf alcoves, a
# red cloth "tabletop" roof with gold-trimmed drapes, glowing cyan crystal cubes on its corners, and (as decor, further
# down) a huge open book floating and turning above it under a spinning crystal, with enchanted books circling the hall.
SPIRE = (44, 70, 71)
sx, sy, sz = SPIRE
spire_cells = islet(sx, sy, sz, 13)
flora(spire_cells, 0.15)
LIB_FLOOR = sy + 1                 # the plinth's top layer; people stand on it at LIB_FLOOR + 1
LW0, LW1 = sx - 7, sx + 7          # outer wall planes (x), 15 wide
LN0, LN1 = sz - 7, sz + 7          # outer wall planes (z)
LTOP = LIB_FLOOR + 11              # the "tabletop" roof layer
WALL = {"polished_blackstone_bricks": 58, "cracked_polished_blackstone_bricks": 14, "blackstone": 10, "obsidian": 12,
        "crying_obsidian": 6}

# The plinth, a little wider than the hall, with rubble and amethyst around its foot.
for x in range(LW0 - 1, LW1 + 2):
    for z in range(LN0 - 1, LN1 + 2):
        edge = x in (LW0 - 1, LW1 + 1) or z in (LN0 - 1, LN1 + 1)
        b.set(x, sy, z, "polished_blackstone")
        b.set(x, LIB_FLOOR, z, "polished_blackstone_bricks" if edge else
              ("polished_blackstone" if (x + z) % 2 else "polished_deepslate"))
for (x, z), top in spire_cells.items():
    d = max(abs(x - sx), abs(z - sz))
    if d in (9, 10) and hash01(x, 5, z) < 0.35:
        b.set(x, top + 1, z, pick({"cobbled_deepslate": 3, "mossy_cobblestone": 1, "blackstone": 2}, x, top, z))
    elif d in (9, 10, 11) and hash01(x, 9, z) < 0.12:
        b.set(x, top + 1, z, "amethyst_cluster[facing=up]")
    elif d in (9, 10, 11) and hash01(x, 13, z) < 0.12:
        b.set(x, top + 1, z, "large_amethyst_bud[facing=up]")
# Grand steps up to the door on the north side.
for x in range(sx - 3, sx + 4):
    b.set(x, sy + 1, LN0 - 2, "polished_blackstone_brick_stairs[facing=south]")
for x in (sx - 4, sx + 4):
    b.set(x, sy + 1, LN0 - 2, "polished_blackstone_bricks")
    b.set(x, sy + 2, LN0 - 2, "polished_blackstone_brick_wall")
    b.set(x, sy + 3, LN0 - 2, "soul_lantern[hanging=false]")


def lib_face(i, j, y):
    """One wall cell, i along the face (0..14), y the height above the floor (1..10). None: leave it open."""
    level = y
    if i <= 2 or i >= 12:                      # corner towers
        if level in (1, 6):
            return "chiseled_polished_blackstone"
        return "polished_blackstone_bricks" if (i + level) % 5 else "gilded_blackstone"
    if i == 7:                                 # the glowing seam down the middle of the face
        return "crying_obsidian" if level % 3 == 0 else "amethyst_block"
    if level == 10:
        return "gilded_blackstone" if i % 2 else "chiseled_polished_blackstone"
    return None


# Walls, faces first: the four sides share one pattern; alcoves and the door are carved out after.
faces = {
    "north": [(x, LN0) for x in range(LW0, LW1 + 1)],
    "south": [(x, LN1) for x in range(LW1, LW0 - 1, -1)],
    "west": [(LW0, z) for z in range(LN1, LN0 - 1, -1)],
    "east": [(LW1, z) for z in range(LN0, LN1 + 1)],
}
inward = {"north": (0, 1), "south": (0, -1), "west": (1, 0), "east": (-1, 0)}
outward_facing = {"north": "north", "south": "south", "west": "west", "east": "east"}
for face, cells in faces.items():
    ix, iz = inward[face]
    for i, (x, z) in enumerate(cells):
        for level in range(1, 11):
            y = LIB_FLOOR + level
            block = lib_face(i, 0, level) or pick(WALL, x, y, z)
            b.set(x, y, z, block)
            if i <= 2 or i >= 12:
                # Corner towers are three deep.
                for depth in (1, 2):
                    b.set(x + ix * depth, y, z + iz * depth, lib_face(i, 0, level) or "polished_blackstone_bricks")
    # Two arched alcoves per face, filled with books and a lantern (the north face's middle is the door).
    for a0 in (3, 9):
        for k in range(3):
            x, z = cells[a0 + k]
            for level in range(2, 7):
                b.set(x, LIB_FLOOR + level, z, "air")
                b.set(x + ix, LIB_FLOOR + level, z + iz, "bookshelf" if level < 6 else "chiseled_bookshelf[facing=" + outward_facing[face] + "]")
            b.set(x, LIB_FLOOR + 1, z, f"polished_blackstone_brick_stairs[facing={OPPOSITE[face]},half=top]")
            if k != 1:
                b.set(x, LIB_FLOOR + 6, z, f"polished_blackstone_brick_stairs[facing={face},half=top]")
        mx, mz = cells[a0 + 1]
        b.set(mx, LIB_FLOOR + 6, mz, "soul_lantern[hanging=true]")
        b.set(mx, LIB_FLOOR + 2, mz, "purple_candle[candles=3,lit=true]")
    # Purple lanterns either side of the seam, on little brackets.
    for i in (6, 8):
        x, z = cells[i]
        ox, oz = -ix, -iz
        b.set(x + ox, LIB_FLOOR + 8, z + oz, "polished_blackstone_brick_wall")
        b.set(x + ox, LIB_FLOOR + 7, z + oz, "soul_lantern[hanging=true]")
# The door: a tall arch in the middle of the north face, framed in gold.
for x in range(sx - 1, sx + 2):
    for level in range(1, 7):
        b.set(x, LIB_FLOOR + level, LN0, "air")
        b.set(x, LIB_FLOOR + level, LN0 + 1, "air")
b.set(sx - 1, LIB_FLOOR + 6, LN0, "polished_blackstone_brick_stairs[facing=east,half=top]")
b.set(sx + 1, LIB_FLOOR + 6, LN0, "polished_blackstone_brick_stairs[facing=west,half=top]")
b.set(sx, LIB_FLOOR + 7, LN0, "gold_block")
for x in (sx - 2, sx + 2):
    for level in range(1, 7):
        b.set(x, LIB_FLOOR + level, LN0, "polished_blackstone_bricks" if level % 2 else "gilded_blackstone")
b.set(sx, LIB_FLOOR + 8, LN0 - 1, "dark_oak_wall_sign[facing=north]" + sign_nbt("ARCANE", "LIBRARY", color="purple"))

# Interior: floor, bookshelf walls, a dark oak ceiling with soul-lantern chandeliers, and a working enchanting table in a
# full ring of bookshelves (it counts all fifteen).
for x in range(LW0 + 1, LW1):
    for z in range(LN0 + 1, LN1):
        if b.get(x, LIB_FLOOR + 1, z) in ("polished_blackstone_bricks", "chiseled_polished_blackstone", "gilded_blackstone"):
            continue
        for level in range(1, 10):
            if (x, LIB_FLOOR + level, z) in b.blocks and level > 1:
                continue
            b.set(x, LIB_FLOOR + level, z, "air")
        b.set(x, LIB_FLOOR + 10, z, "dark_oak_planks")
for x in range(LW0 + 1, LW1):
    for z in range(LN0 + 1, LN1):
        inner = (LW0 + 3 <= x <= LW1 - 3) and (LN0 + 3 <= z <= LN1 - 3)
        if not inner and b.get(x, LIB_FLOOR + 1, z) == "air":
            for level in range(1, 4):
                b.set(x, LIB_FLOOR + level, z, "bookshelf")
            b.set(x, LIB_FLOOR + 4, z, "purple_candle[candles=2,lit=true]" if hash01(x, 1, z) < 0.25 else "air")
# A red runner from the door to the table.
for z in range(LN0 + 1, sz - 2):
    for x in range(sx - 1, sx + 2):
        b.set(x, LIB_FLOOR + 1, z, "red_carpet")
b.set(sx, LIB_FLOOR + 1, sz, "enchanting_table")
for dx in range(-2, 3):
    for dz in range(-2, 3):
        if max(abs(dx), abs(dz)) == 2 and not (dz == -2 and abs(dx) <= 1):
            b.fill(sx + dx, LIB_FLOOR + 1, sz + dz, sx + dx, LIB_FLOOR + 2, sz + dz, "bookshelf")
        elif max(abs(dx), abs(dz)) == 1:
            b.set(sx + dx, LIB_FLOOR + 1, sz + dz, "purple_carpet")
for dx, dz in ((-2, -2), (2, -2), (-2, 2), (2, 2)):
    b.set(sx + dx, LIB_FLOOR + 3, sz + dz, "amethyst_cluster[facing=up]")
for cx, cz in ((sx - 3, sz - 3), (sx + 3, sz - 3), (sx - 3, sz + 3), (sx + 3, sz + 3)):
    b.fill(cx, LIB_FLOOR + 8, cz, cx, LIB_FLOOR + 9, cz, "iron_chain")
    b.set(cx, LIB_FLOOR + 7, cz, "soul_lantern[hanging=true]")

# The tabletop: red cloth over the whole hall, a dark diamond inlaid in the middle where the book floats, gold-trimmed
# drapes hanging over each face, and a glowing cyan crystal cube on each corner.
for x in range(LW0 - 1, LW1 + 2):
    for z in range(LN0 - 1, LN1 + 2):
        b.set(x, LTOP - 1, z, "gilded_blackstone" if (x in (LW0 - 1, LW1 + 1) or z in (LN0 - 1, LN1 + 1)) else "polished_blackstone")
        d = abs(x - sx) + abs(z - sz)
        top = "red_wool"
        if d <= 4:
            top = "crying_obsidian" if d <= 1 else "obsidian" if d <= 3 else "gold_block"
        elif x in (LW0 - 1, LW1 + 1) or z in (LN0 - 1, LN1 + 1):
            top = "red_terracotta"
        b.set(x, LTOP, z, top)
for x, z in ((sx, sz - 4), (sx, sz + 4), (sx - 4, sz), (sx + 4, sz)):
    b.set(x, LTOP + 1, z, "amethyst_cluster[facing=up]")
for face, cells in faces.items():
    ix, iz = inward[face]
    for a0 in (3, 9):
        for k in range(3):
            x, z = cells[a0 + k]
            ox, oz = x - ix, z - iz            # one block out from the wall, under the cloth's overhang
            for level in (8, 9, 10):
                b.set(ox, LIB_FLOOR + level, oz, "red_wool")
            b.set(ox, LIB_FLOOR + 7, oz, "gold_block" if k == 1 else "red_terracotta")
for cx in (LW0, LW1):
    for cz in (LN0, LN1):
        for dx in (-1, 0, 1):
            for dz in (-1, 0, 1):
                for dy in (0, 1, 2):
                    glow = dx == 0 and dz == 0 and dy == 2
                    b.set(cx + dx, LTOP + dy, cz + dz, "sea_lantern" if glow else "diamond_block")
                b.set(cx + dx, LTOP - 1, cz + dz, "gold_block" if (dx, dz) == (0, 0) else "chiseled_polished_blackstone")

spot("enchanter", sx, LN0 + 3, LIB_FLOOR + 1, yaw=180.0)
LIBRARY_BOOK = (sx + 0.5, LTOP + 4.6, sz + 0.5)
a, e = bridge_ends("z", sx, 30, 1, spire_cells)
bridge("z", sx, a, e + 1, G + 1, sy + 1, width=3, wood="spruce", lamp_every=5)
path([(45, 29), (sx, 29), (sx, a)])

# Starwatch: a little observatory islet to the north-east, reached from the terrace.
STAR = (38, 77, -78)
star_cells = islet(STAR[0], STAR[1], STAR[2], 7)
flora(star_cells, 0.25)
for dx in range(-4, 5):
    for dz in range(-4, 5):
        if math.hypot(dx, dz) <= 4.2:
            b.set(STAR[0] + dx, STAR[1], STAR[2] + dz, "polished_deepslate" if math.hypot(dx, dz) > 1 else "gold_block")
for dx, dz in ((-3, -3), (3, -3), (-3, 3), (3, 3)):
    b.fill(STAR[0] + dx, STAR[1] + 1, STAR[2] + dz, STAR[0] + dx, STAR[1] + 4, STAR[2] + dz, "polished_blackstone_wall")
    b.set(STAR[0] + dx, STAR[1] + 5, STAR[2] + dz, "polished_blackstone")
dome(STAR[0], STAR[1] + 6, STAR[2], 4, "waxed_oxidized_cut_copper")
for dx in range(-3, 4):
    for dz in range(-3, 4):
        if math.hypot(dx, dz) < 2.6:
            b.blocks.pop((STAR[0] + dx, STAR[1] + 9, STAR[2] + dz), None)
b.set(STAR[0], STAR[1] + 1, STAR[2], "lodestone")
a, e = bridge_ends("z", STAR[0], -40, -1, star_cells)
bridge("z", STAR[0], a, e - 1, TERRACE + 1, STAR[1] + 1, width=3, wood="spruce", lamp_every=5)
path([(STAR[0], -34), (STAR[0], a)])
# Water spills off Starwatch's far side into the void.
wfx, wfz = STAR[0] + 2, STAR[2] - 1
while (wfx + 1, wfz) in star_cells:
    wfx += 1
b.set(wfx, STAR[1], wfz, "water")
b.set(wfx - 1, STAR[1], wfz, "water")
catch_pool(wfx + 1, wfz, 44)

# ---------------------------------------------------------------- the Grand Bazaar: a market islet east of the island
# Where the east avenue ends, the Bazaar Gate opens onto a wide dark-oak bridge to the Grand Bazaar: a big islet (about
# x 64..94, z -41..41) with a bell tower fountain in an open 19x19 plaza and six merchant pavilions around it
# (sync_npc_spots.py moves the NPCs there). North is -z:
#
#                  Slayer Lodge (79,-33) faces south
#   Armoury (70,-19) faces south     Petals (88,-19) faces south
#   forecourt apron z -14..-10, 5 deep, x 66..92
#   bridge --> plaza x 70..88, z -9..9, bell tower (79,0) --> east lookout       the street x 75..83 runs north-south
#   forecourt apron z 10..14
#   Curiosities (70,19) faces north  Pantry (88,19) faces north
#                  The Angler (79,33) faces north
# Pavilions are 9x9; the gap between neighbours is 9 (the street) or 30 blocks (north to south), the streets are 9 and 7 wide.
BZ = (79, 0)
BZ_RX, BZ_RZ = 15.3, 42.0
BZ_XMAX = 94
baz = {}
for x in range(BZ[0] - 17, BZ_XMAX + 1):
    for z in range(-46, 47):
        d = ((abs(x - BZ[0]) / BZ_RX) ** 4 + (abs(z - BZ[1]) / BZ_RZ) ** 4) ** 0.25
        d /= 1 + noise(x * 1.3, z * 1.3) * 0.04
        if d > 1:
            continue
        if math.hypot(x, z) < edge_radius(math.atan2(z, x)) + 3:        # keep clear of the main island...
            continue
        if x < 65 and abs(z - BROOK_FALL[1]) < 9:                       # ...and of the brook's waterfall and catch pool
            continue
        depth = int(4 + 26 * (1 - d ** 2) ** 0.9 + noise(z, x) * 2.5)
        bottom = max(G - depth, BOX_MIN[1] + 10)
        column(x, z, G, bottom, d > 0.8)
        baz[(x, z)] = (G, bottom)
for (x, z), (top, bottom) in baz.items():
    rim = any((x + dx, z + dz) not in baz for dx, dz in DIRS.values())
    underside(x, z, bottom, 1.5, rim)
baz_cells = {k: v[0] for k, v in baz.items()}


def in_baz(x0, z0, x1, z1, margin=1):
    ring = [(x, z) for x in range(x0 - margin, x1 + margin + 1) for z in (z0 - margin, z1 + margin)]
    ring += [(x, z) for z in range(z0 - margin, z1 + margin + 1) for x in (x0 - margin, x1 + margin)]
    return all(cell in baz for cell in ring)


def pave(x, z, block):
    if (x, z) in baz:
        b.set(x, G, z, block)
        b.occupied.add((x, z))


def hip_roof(cx, cz, y0, half, stairs, apex):
    """A hollow pyramid roof: rings of stairs from half-size `half` at y0 up to a single block at y0 + half."""
    corner = {(-1, -1): ("south", "outer_left"), (1, -1): ("south", "outer_right"),
              (-1, 1): ("north", "outer_right"), (1, 1): ("north", "outer_left")}
    for h in range(half, 0, -1):
        y = y0 + half - h
        for dx in range(-h, h + 1):
            for dz in range(-h, h + 1):
                if max(abs(dx), abs(dz)) != h:
                    continue
                if abs(dx) == h and abs(dz) == h:
                    facing, shape = corner[(dx // h, dz // h)]
                elif abs(dz) == h:
                    facing, shape = ("south" if dz < 0 else "north"), "straight"
                else:
                    facing, shape = ("east" if dx < 0 else "west"), "straight"
                b.set(cx + dx, y, cz + dz, f"{stairs}[facing={facing},half=bottom,shape={shape}]")
    b.set(cx, y0 + half, cz, apex)


COMPASS = {(0, -1): "north", (0, 1): "south", (1, 0): "east", (-1, 0): "west"}
YAW = {"south": 0.0, "west": 90.0, "north": 180.0, "east": 270.0}
ROTATION = {"south": 0, "west": 4, "north": 8, "east": 12}


def pavilion(name, cx, cz, facing, pal, sign, goods, role):
    """A 9x9 merchant pavilion (hip roof on pillars, back wall, counter). The merchant stands in the middle facing
    `facing`, the way the plaza lies. goods(P, F, B, L, R) dresses it; P(u, d, dy, block) places a block u to the merchant's
    right, d towards the plaza and dy above the floor."""
    fx, fz = DIRS[facing]
    rx, rz = -fz, fx
    F, B, R = facing, OPPOSITE[facing], COMPASS[(rx, rz)]
    L = OPPOSITE[R]
    if not in_baz(cx - 4, cz - 4, cx + 4, cz + 4, 0):
        warnings.append(f"{name}: not fully on the bazaar islet")
    b.claim(cx - 4, cz - 4, cx + 4, cz + 4, 1)

    def P(u, d, dy, block):
        b.set(cx + fx * d + rx * u, G + dy, cz + fz * d + rz * u, block)

    u_axis, d_axis = ("x" if rx else "z"), ("x" if fx else "z")
    for u in range(-4, 5):
        for d in range(-4, 5):
            edge = max(abs(u), abs(d)) == 4
            P(u, d, 0, pal["trim"] if edge else pick(pal["floor"], cx + u, 1, cz + d))
    for u in (-3, 3):
        for d in (-3, 3):
            for dy in range(1, 5):
                P(u, d, dy, pal["pillar"])
    for u in range(-3, 4):
        for d in range(-3, 4):
            if max(abs(u), abs(d)) == 3:
                axis = u_axis if abs(d) == 3 else d_axis
                P(u, d, 5, f"{pal['beam']}[axis={axis}]")
    hip_roof(cx, cz, G + 5, 4, pal["stairs"], pal["apex"])
    b.set(cx, G + 10, cz, "waxed_lightning_rod")
    b.fill(cx, G + 7, cz, cx, G + 8, cz, "iron_chain[axis=y]")
    b.set(cx, G + 6, cz, "lantern[hanging=true]")
    P(0, 0, 4, "light[level=15]")
    # back wall, side rails, counter
    for u in range(-2, 3):
        for dy in range(1, 5):
            P(u, -3, dy, pal["wall"])
    for d in range(-2, 3):
        for u in (-3, 3):
            P(u, d, 1, pal["fence"])
    for u in range(-2, 3):
        P(u, 2, 1, pal["counter"])
    # striped valance under the beam, a hanging sign over the middle of the front
    for u in range(-2, 3):
        if u != 0:
            P(u, 3, 4, pal["valance"][u % 2])
    for d in range(-2, 3):
        for u in (-3, 3):
            P(u, d, 4, pal.get("side") or pal["valance"][d % 2])
    P(0, 3, 4, f"dark_oak_hanging_sign[rotation={ROTATION[F]},attached=false]" + sign_nbt_both(*sign))
    # banners on the front pillars, flowers on the corners of the plinth
    base, patterns = pal["banner"]
    for u in (-3, 3):
        P(u, 4, 3, f"{base}_wall_banner[facing={F}]" + banner(*patterns))
    for u in (-4, 4):
        for d in (-4, 4):
            P(u, d, 1, pick(FLOWER_POTS, cx + u, 2, cz + d))
    goods(P, F, B, L, R)
    spot(role, cx, cz, G + 1, yaw=YAW[F])


def shelf_row(P, dy, rows, F, wood):
    for u, items in zip(range(-2, 3), rows):
        if items:
            P(u, -2, dy, shelf(items, F, wood))


def armoury_goods(P, F, B, L, R):
    for u, block in zip(range(-2, 3), (f"anvil[facing={R}]", "smithing_table", f"blast_furnace[facing={F}]",
                                         f"grindstone[face=floor,facing={F}]", f"anvil[facing={R}]")):
        P(u, -2, 1, block)
    shelf_row(P, 2, (("iron_sword", "diamond_sword", "netherite_sword"), ("bow", "arrow", "crossbow"),
                     ("shield", "iron_axe", "trident"), ("golden_sword", "stone_sword", "mace"),
                     ("diamond_axe", "wooden_sword", "copper_sword")), F, "dark_oak")
    shelf_row(P, 3, (("iron_helmet", "iron_chestplate", None), ("diamond_helmet", "diamond_boots", None),
                     ("chainmail_helmet", "chainmail_leggings", "chainmail_boots"), ("golden_helmet", "golden_boots", None),
                     ("netherite_helmet", "netherite_chestplate", None)), F, "dark_oak")
    for u in (-2, 2):
        P(u, 2, 2, "lantern[hanging=false]")
        # a training dummy at each side of the stall
        P(u, 0, 1, "dark_oak_fence")
        P(u, 0, 2, "hay_block[axis=y]")
        P(u, 0, 3, f"carved_pumpkin[facing={F}]")


def curio_goods(P, F, B, L, R):
    sherds = (("brick", "miner_pottery_sherd", "shelter_pottery_sherd", "brick"),
              ("brick", "heart_pottery_sherd", "plenty_pottery_sherd", "brick"),
              ("brick", "archer_pottery_sherd", "prize_pottery_sherd", "brick"),
              ("brick", "angler_pottery_sherd", "arms_up_pottery_sherd", "brick"),
              ("brick", "blade_pottery_sherd", "snort_pottery_sherd", "brick"))
    for u, s in zip(range(-2, 3), sherds):
        P(u, -2, 1, deco_pot(F, *s))
    shelf_row(P, 2, (("amethyst_shard", "spyglass", "compass"), ("echo_shard", "recovery_compass", "clock"),
                     ("name_tag", "lead", "goat_horn"), ("ender_pearl", "ender_eye", "nautilus_shell"),
                     ("totem_of_undying", "experience_bottle", "emerald")), F, "birch")
    for u in range(-2, 3):
        P(u, -2, 3, f"{'amethyst_cluster' if u % 2 == 0 else 'medium_amethyst_bud'}[facing={F}]")
    P(-2, 2, 2, "amethyst_cluster[facing=up]")
    P(2, 2, 2, "amethyst_cluster[facing=up]")
    P(-1, 2, 2, "purple_candle[candles=3,lit=true]")
    P(1, 2, 2, "cyan_candle[candles=3,lit=true]")
    for u in (-2, 2):
        P(u, 0, 1, "lodestone")
        P(u, 0, 2, "amethyst_cluster[facing=up]")


def florist_goods(P, F, B, L, R):
    tall = {-2: "peony", 0: "lilac", 2: "rose_bush"}
    for u in range(-2, 3):
        P(u, -2, 1, "grass_block")
        if u in tall:
            P(u, -2, 2, f"{tall[u]}[half=lower]")
            P(u, -2, 3, f"{tall[u]}[half=upper]")
        else:
            P(u, -2, 2, "pink_tulip" if u < 0 else "allium")
    for u in (-2, 2):
        for d in (-1, 0):
            P(u, d, 1, "grass_block")
            P(u, d, 2, pick({"cornflower": 2, "azure_bluet": 1, "oxeye_daisy": 2, "poppy": 1, "lily_of_the_valley": 1},
                            u, d, 5))
    for u in (-2, -1, 1, 2):
        P(u, 2, 2, pick(FLOWER_POTS, u, 7, 3))
    P(0, 2, 2, "potted_flowering_azalea_bush")


def provisioner_goods(P, F, B, L, R):
    for u in range(-2, 3):
        P(u, -2, 1, "barrel[facing=up]" if u % 2 == 0 else "hay_block[axis=y]")
    shelf_row(P, 2, (("bread", "apple", "cookie"), ("cooked_beef", "cooked_porkchop", "cooked_chicken"),
                     ("carrot", "potato", "beetroot"), ("pumpkin_pie", "honey_bottle", "milk_bucket"),
                     ("golden_apple", "melon_slice", "sweet_berries")), F, "spruce")
    shelf_row(P, 3, (("wheat", "sugar", "egg"), ("cod", "salmon", "dried_kelp"), ("baked_potato", "mushroom_stew", "bowl"),
                     ("glow_berries", "honeycomb", "cocoa_beans"), ("cake", "rabbit_stew", "beetroot_soup")), F, "spruce")
    P(-2, 2, 2, "melon")
    P(2, 2, 2, "pumpkin")
    P(-1, 2, 2, "cake[bites=0]")
    P(1, 2, 2, "cake[bites=0]")
    P(-2, -1, 1, f"smoker[facing={F}]")
    P(2, -1, 1, "composter[level=5]")
    P(-2, 0, 1, "hay_block[axis=y]")
    P(2, 0, 1, "barrel[facing=up]")


def angler_goods(P, F, B, L, R):
    for u in range(-2, 3):
        P(u, -2, 0, "water")
    for u in (-1, 1):
        P(u, -2, 1, "lily_pad")
    shelf_row(P, 2, (("cod", "salmon", "tropical_fish"), ("pufferfish", "fishing_rod", "bucket"),
                     ("nautilus_shell", "heart_of_the_sea", "prismarine_shard"), ("string", "ink_sac", "lily_pad"),
                     ("kelp", "dried_kelp", "sea_pickle")), F, "mangrove")
    shelf_row(P, 3, (("fishing_rod", "carrot_on_a_stick", None), ("cooked_cod", "cooked_salmon", None),
                     ("prismarine_crystals", "glow_ink_sac", "tropical_fish_bucket"), ("cooked_cod", "cooked_salmon", None),
                     ("fishing_rod", "carrot_on_a_stick", None)), F, "mangrove")
    for u in (-2, 2):
        P(u, -1, 1, "barrel[facing=up]")
        P(u, 0, 1, "water_cauldron[level=3]")
        P(u, 2, 2, "sea_pickle[pickles=4,waterlogged=false]")
    for u in (-1, 1):
        P(u, 2, 2, "lantern[hanging=false]")
    for d in range(-2, 3):          # fishing nets between the side pillars
        for u in (-3, 3):
            P(u, d, 3, "cobweb")


def slayer_goods(P, F, B, L, R):
    heads = ("zombie_wall_head", "skeleton_wall_skull", "wither_skeleton_wall_skull", "creeper_wall_head", "piglin_wall_head")
    for u, head in zip(range(-2, 3), heads):
        P(u, -2, 3, f"{head}[facing={F}]")
    shelf_row(P, 2, (("bone", "rotten_flesh", "gunpowder"), ("spider_eye", "string", "slime_ball"),
                     ("blaze_rod", "ghast_tear", "ender_pearl"), ("nether_star", "wither_rose", "netherite_scrap"),
                     ("iron_sword", "bow", "shield")), F, "crimson")
    for u in range(-2, 3):
        P(u, -2, 1, "gilded_blackstone" if u == 0 else "polished_blackstone")
    for u in (-2, 2):
        P(u, -1, 1, "polished_blackstone")
        P(u, -1, 2, "soul_lantern[hanging=false]")
    P(-2, 2, 2, "wither_skeleton_skull[rotation=0]")
    P(2, 2, 2, "zombie_head[rotation=0]")
    for u in (-1, 1):
        P(u, 2, 2, "red_candle[candles=3,lit=true]")


PAV = {
    "armoury": dict(floor={"deepslate_tiles": 5, "polished_deepslate": 3, "cracked_deepslate_tiles": 1},
                    trim="polished_blackstone_bricks", pillar="stripped_dark_oak_log", beam="stripped_dark_oak_log",
                    wall="deepslate_bricks", counter="polished_blackstone_bricks", fence="dark_oak_fence",
                    stairs="deepslate_tile_stairs", apex="deepslate_tiles", valance=("red_wool", "black_wool"),
                    banner=("black", [("cross", "red"), ("border", "gray")])),
    "curios": dict(floor={"smooth_quartz": 4, "calcite": 3, "polished_diorite": 1}, trim="waxed_oxidized_cut_copper",
                   pillar="stripped_warped_stem", beam="stripped_warped_stem", wall="purple_terracotta",
                   counter="waxed_oxidized_copper", fence="warped_fence", stairs="waxed_oxidized_cut_copper_stairs",
                   apex="waxed_oxidized_cut_copper", valance=("purple_wool", "cyan_wool"),
                   banner=("purple", [("rhombus", "cyan"), ("border", "white")])),
    "florist": dict(floor={"cherry_planks": 1}, trim="stripped_cherry_wood", pillar="stripped_cherry_log",
                    beam="stripped_cherry_log", wall="cherry_planks", counter="cherry_planks", fence="cherry_fence",
                    stairs="cherry_stairs", apex="cherry_planks", valance=("pink_wool", "white_wool"),
                    banner=("pink", [("flower", "white"), ("border", "green")])),
    "pantry": dict(floor={"dark_oak_planks": 3, "spruce_planks": 2}, trim="bricks", pillar="stripped_spruce_log",
                   beam="stripped_spruce_log", wall="spruce_planks", counter="stripped_spruce_wood", fence="spruce_fence",
                   stairs="brick_stairs", apex="bricks", valance=("orange_wool", "yellow_wool"),
                   banner=("orange", [("rhombus", "yellow"), ("border", "brown")])),
    "angler": dict(floor={"spruce_planks": 3, "prismarine_bricks": 1}, trim="dark_prismarine",
                   pillar="stripped_mangrove_log", beam="stripped_mangrove_log", wall="mangrove_planks",
                   counter="mangrove_planks", fence="mangrove_fence", stairs="prismarine_brick_stairs",
                   apex="prismarine_bricks", valance=("blue_wool", "white_wool"), side="cobweb",
                   banner=("blue", [("stripe_middle", "white"), ("border", "cyan")])),
    "slayer": dict(floor={"blackstone": 3, "polished_blackstone": 2, "cracked_polished_blackstone_bricks": 1},
                   trim="polished_blackstone_bricks", pillar="stripped_crimson_stem", beam="stripped_crimson_stem",
                   wall="red_nether_bricks", counter="polished_blackstone", fence="nether_brick_fence",
                   stairs="red_nether_brick_stairs", apex="red_nether_bricks", valance=("black_wool", "red_wool"),
                   banner=("black", [("skull", "red"), ("border", "red")])),
}

# --- paving: plaza, forecourts, streets, the road from the bridge and the one to the lookout
CX, CZ = BZ
PLAZA_STONE = {"polished_andesite": 7, "polished_diorite": 1, "smooth_stone": 2}
for x in range(CX - 9, CX + 10):                                  # the plaza: 19x19, open to the sky
    for z in range(CZ - 9, CZ + 10):
        r = max(abs(x - CX), abs(z - CZ))
        d = math.hypot(x - CX, z - CZ)
        ang = (math.degrees(math.atan2(z - CZ, x - CX)) + 360) % 45
        if r == 9:
            block = "polished_blackstone_bricks"
        elif r == 8:
            block = pick({"cobbled_deepslate": 5, "mossy_cobblestone": 1, "tuff_bricks": 2}, x, 3, z)   # the path ring
        elif abs(d - 6.9) < 0.55:
            block = "polished_deepslate"
        elif d < 6.2 and (ang < 2.2 or ang > 42.8):
            block = "gilded_blackstone" if d < 5.6 else "polished_blackstone"
        else:
            block = pick(PLAZA_STONE, x, 0, z)
        pave(x, z, block)
for side in (-1, 1):                                              # forecourts in front of the four side pavilions
    for z in range(side * 10, side * 15, side):
        for x in range(66, 93):
            outer = abs(z) == 14
            pave(x, z, "polished_blackstone_bricks" if outer else pick(PLAZA_STONE, x, 0, z))
    for z in range(side * 15, side * 29, side):                   # the street to the end pavilions: 9 wide, x 75..83
        for x in range(CX - 4, CX + 5):
            pave(x, z, "polished_blackstone_bricks" if abs(x - CX) == 4 else pick(ROAD, x, 0, z))
for x0, x1 in ((66, CX - 10), (CX + 10, 93)):                     # bridge road in, lookout road out: 7 wide
    for x in range(x0, x1 + 1):
        for z in range(-3, 4):
            pave(x, z, "polished_blackstone_bricks" if abs(z) == 3 else pick(ROAD, x, 0, z))

# --- the fountain around a bell tower
for x in range(CX - 6, CX + 7):
    for z in range(CZ - 6, CZ + 7):
        d = math.hypot(x - CX, z - CZ)
        if 3.9 <= d < 4.9:
            b.set(x, G, z, "deepslate_bricks")
            b.set(x, G + 1, z, "polished_blackstone_brick_slab[type=bottom]")
        elif d < 3.9:
            b.set(x, G - 1, z, "sea_lantern" if (x - CX) % 3 == 0 and (z - CZ) % 3 == 0 else "dark_prismarine")
            if d >= 2.3:
                b.set(x, G, z, "water")
            else:
                b.set(x, G, z, "stone_bricks")
                if d >= 1.8:
                    b.set(x, G + 1, z, "polished_blackstone_brick_slab[type=bottom]")
        else:
            continue
        b.occupied.add((x, z))
for dx in (-1, 0, 1):
    for dz in (-1, 0, 1):
        corner = dx != 0 and dz != 0
        for y in range(G + 1, G + 8):
            b.set(CX + dx, y, CZ + dz, "polished_blackstone_bricks" if corner else "stone_bricks")
        b.set(CX + dx, G + 8, CZ + dz, "gilded_blackstone")
for dx, dz, ax in ((2, 0, "x"), (-2, 0, "x"), (0, 2, "z"), (0, -2, "z")):   # clock faces
    for w in (-1, 0, 1):
        for y in (G + 5, G + 6, G + 7):
            x, z = (CX + dx, CZ + w) if ax == "x" else (CX + w, CZ + dz)
            hand = (w == 0 and y >= G + 6) or (w == 1 and y == G + 6)
            b.set(x, y, z, "black_concrete" if hand else "white_concrete")
for dx in (-1, 1):                                                # open belfry with the bell
    for dz in (-1, 1):
        for y in range(G + 9, G + 12):
            b.set(CX + dx, y, CZ + dz, "polished_blackstone")
for dx in range(-1, 2):
    for dz in range(-1, 2):
        b.set(CX + dx, G + 12, CZ + dz, "polished_blackstone_bricks")
b.set(CX, G + 11, CZ, "iron_chain[axis=y]")
b.set(CX, G + 10, CZ, "bell[attachment=ceiling,facing=north]")
hip_roof(CX, CZ, G + 12, 2, "waxed_weathered_cut_copper_stairs", "waxed_weathered_cut_copper")
b.set(CX, G + 15, CZ, "gold_block")
b.set(CX, G + 16, CZ, "waxed_lightning_rod")
for x, z in ((CX + 5, CZ + 5), (CX - 5, CZ + 5), (CX + 5, CZ - 5), (CX - 5, CZ - 5), (CX + 6, CZ), (CX - 6, CZ),
             (CX, CZ + 6), (CX, CZ - 6)):
    b.set(x, G + 3, z, "light[level=15]")
b.set(CX, G + 9, CZ, "light[level=15]")

# --- the six pavilions
pavilion("armoury", 70, -19, "south", PAV["armoury"], ("THE ARMOURY", "Kestrel Vane", "Combat Master"), armoury_goods, "stall_1")
pavilion("curios", 70, 19, "north", PAV["curios"], ("CURIOSITIES", "Odessa Quill", "Curio Dealer"), curio_goods, "stall_2")
pavilion("florist", 88, -19, "south", PAV["florist"], ("PETALS & BLOOMS", "Pip Bramblewick", "Florist"), florist_goods, "stall_3")
pavilion("pantry", 88, 19, "north", PAV["pantry"], ("THE PANTRY", "Dorrin Saltmarsh", "Provisioner"), provisioner_goods, "stall_4")
pavilion("angler", 79, 33, "north", PAV["angler"], ("THE ANGLER", "Marina Saltwick", "Fishing Master"), angler_goods, "fisher")
pavilion("slayer", 79, -33, "south", PAV["slayer"], ("SLAYER LODGE", "Morwen Ashgrave", "Slayer Master"), slayer_goods,
         "slayer_master")


def bench(bx, bz, facing):
    if facing in ("north", "south"):
        for dx in (-1, 0, 1):
            b.set(bx + dx, G + 1, bz, f"dark_oak_stairs[facing={facing}]")
        b.set(bx - 2, G + 1, bz, "dark_oak_trapdoor[facing=west,open=true]")
        b.set(bx + 2, G + 1, bz, "dark_oak_trapdoor[facing=east,open=true]")
    else:
        for dz in (-1, 0, 1):
            b.set(bx, G + 1, bz + dz, f"dark_oak_stairs[facing={facing}]")
        b.set(bx, G + 1, bz - 2, "dark_oak_trapdoor[facing=north,open=true]")
        b.set(bx, G + 1, bz + 2, "dark_oak_trapdoor[facing=south,open=true]")


def flower_planter(x, z, sx=3, sz=1):
    for dx in range(-(sx // 2), sx // 2 + 1):
        for dz in range(-(sz // 2), sz // 2 + 1):
            b.set(x + dx, G + 1, z + dz, "grass_block")
            b.set(x + dx, G + 2, z + dz, pick({"pink_tulip": 2, "allium": 2, "cornflower": 2, "azure_bluet": 1,
                                               "lily_of_the_valley": 1, "red_tulip": 1, "orange_tulip": 1}, x + dx, 41, z + dz))
    b.claim(x - sx // 2 - 1, z - sz // 2 - 1, x + sx // 2 + 1, z + sz // 2 + 1)


# --- lamps, benches, planters and trees: the plaza, forecourts and streets stay open to the sky
for x, z, arm in ([(CX - 9, CZ - 9, "east"), (CX + 9, CZ - 9, "west"), (CX - 9, CZ + 9, "east"), (CX + 9, CZ + 9, "west"),
                   (CX - 4, -14, "east"), (CX + 4, -14, "west"), (CX - 4, 14, "east"), (CX + 4, 14, "west"),
                   (CX, CZ - 9, "east"), (CX, CZ + 9, "east"), (CX + 9, 0, "north")]
                  + [(CX + s * 4, z, "west" if s > 0 else "east") for s in (-1, 1) for z in (-20, -26, 20, 26)]
                  + [(66, -14, "east"), (92, -14, "west"), (66, 14, "east"), (92, 14, "west")]):
    if (x, z) in baz:
        street_lamp(x, z, G, arm)
for bx, bz, facing in ((CX, CZ - 7, "north"), (CX, CZ + 7, "south"), (CX + 7, CZ, "east"), (CX - 7, CZ - 5, "west"),
                       (CX - 7, CZ + 5, "west"), (CX + 7, CZ - 5, "east"), (CX + 7, CZ + 5, "east")):
    bench(bx, bz, facing)
for px, pz in ((CX - 7, CZ - 8), (CX + 7, CZ - 8), (CX - 7, CZ + 8), (CX + 7, CZ + 8)):
    flower_planter(px, pz, 3, 1)
for px, pz in ((72, -12), (86, -12), (72, 12), (86, 12), (79, -12), (79, 12)):
    flower_planter(px, pz, 3, 1)
for tx, tz in ((67, -7), (67, 7), (91, -7), (91, 7), (68, -26), (90, -26), (68, 26), (90, 26)):
    if (tx, tz) in baz:
        cherry_tree(tx, G, tz, 5)
        b.claim(tx - 3, tz - 3, tx + 3, tz + 3)
# a quest board for the Slayer Master, west of her lodge
for z in (-34, -30):
    b.fill(68, G + 1, z, 68, G + 4, z, "dark_oak_log")
for z in range(-33, -30):
    for y in (G + 2, G + 3, G + 4):
        b.set(68, y, z, "dark_oak_planks")
for z, lines in ((-33, ("WANTED", "Zombies", "50 coins")), (-32, ("WANTED", "Spiders", "120 coins")),
                 (-31, ("WANTED", "Wolves", "300 coins"))):
    b.set(69, G + 3, z, "dark_oak_wall_sign[facing=east]" + sign_nbt(*lines, color="white"))
b.set(69, G + 2, -32, "lectern[facing=west]")
b.set(69, G + 4, -32, "dark_oak_wall_sign[facing=east]" + sign_nbt("SLAYER", "CONTRACTS", color="red"))
b.set(68, G + 5, -34, "lantern[hanging=false]")
b.set(68, G + 5, -30, "lantern[hanging=false]")
for x in range(69, 72):
    for z in range(-34, -29):
        pave(x, z, "polished_blackstone_bricks" if x == 71 else pick(ROAD, x, 0, z))
b.claim(67, -35, 72, -29)
# a training yard east of the lodge
for x in range(86, 92):
    for z in range(-37, -29):
        pave(x, z, pick({"coarse_dirt": 3, "packed_mud": 2, "gravel": 2, "dirt_path": 1}, x, 6, z))
for dx, dz in ((88, -33), (90, -31)):
    b.fill(dx, G + 1, dz, dx, G + 2, dz, "dark_oak_fence")
    b.set(dx, G + 3, dz, "hay_block[axis=y]")
    b.set(dx, G + 4, dz, "carved_pumpkin[facing=west]")
    b.set(dx - 1, G + 3, dz, "dark_oak_fence")
    b.set(dx + 1, G + 3, dz, "dark_oak_fence")
for tx, tz in ((87, -35), (89, -35)):
    b.set(tx, G + 1, tz, "dark_oak_fence")
    b.set(tx, G + 2, tz, "target")
b.claim(85, -38, 92, -29)
# the Angler's pond and dock east of her shop, and a cherry tree west of it
POND = (88.5, 33.0, 2.5)
for x in range(85, 95):
    for z in range(28, 39):
        if (x, z) not in baz:
            continue
        d = math.hypot(x - POND[0], z - POND[1]) / (1 + 0.12 * math.sin(math.atan2(z - POND[1], x - POND[0]) * 3))
        if d < POND[2]:
            b.set(x, G - 2, z, "clay")
            b.set(x, G - 1, z, "water")
            b.set(x, G, z, "water")
            if hash01(x, 4, z) < 0.15:
                b.set(x, G + 1, z, "lily_pad")
        elif d < POND[2] + 1.3:
            b.set(x, G, z, pick({"mossy_cobblestone": 3, "moss_block": 2, "stone": 1}, x, 5, z))
            if hash01(x, 7, z) < 0.3:
                b.set(x, G + 1, z, pick({"fern": 2, "blue_orchid": 1, "moss_carpet": 2}, x, 8, z))
        b.occupied.add((x, z))
for x in range(86, 89):
    b.set(x, G, 33, "spruce_planks")
b.set(88, G + 1, 32, "spruce_fence")
b.set(88, G + 2, 32, "lantern[hanging=false]")
b.set(85, G + 1, 35, "barrel[facing=up]")
flower_planter(70, 33, 3, 1)
cherry_tree(71, G, 36, 6)
# a lookout at the east rim
overlook(91, 0, "east")
b.claim(65, 28, 95, 40)

# --- the bridge: wide, dark oak, with lamp posts and banner pillars at both heads
a, e = bridge_ends("x", 0, END["east"], 1, baz_cells)
for x in range(END["east"] + 1, a):
    for w in range(-2, 3):
        if (x, w) in tops:
            b.set(x, tops[(x, w)], w, "polished_blackstone_bricks" if abs(w) == 2 else pick(ROAD, x, 0, w))
            b.occupied.add((x, w))
bridge("x", 0, a, e + 1, G + 1, G + 1, width=5, wood="dark_oak", sag=0.0, lamp_every=4)
for x in (a + 1, e):
    for z in (-3, 3):
        b.fill(x, G + 1, z, x, G + 5, z, "stripped_dark_oak_log")
        b.set(x, G + 6, z, "lantern[hanging=false]")
        b.set(x + 1, G + 4, z, f"red_wall_banner[facing=east]{BZ_BANNER}")
        b.set(x - 1, G + 4, z, f"red_wall_banner[facing=west]{BZ_BANNER}")
BRIDGE_X = (a, e + 1)

# flora on the rest of the islet
flora({cell: top for cell, top in baz_cells.items() if cell not in b.occupied}, 0.3)
BAZAAR_BOUNDS = (min(x for x, _ in baz), max(x for x, _ in baz), min(z for _, z in baz), max(z for _, z in baz))


# Loose floating rocks. The library's entry has z 1, not sz: the rocks were laid out back when the bazaar loop above
# overwrote sz with 1, and fixing it would move all 17 rocks in the live hub. One (48, 66, a tree rock at y 55) floats under
# the library islet.
TAKEN = [(0, 0, 66), (GATE[0], GATE[1], 13), (sx, 1, 12), (STAR[0], STAR[2], 10), (fall_x - 3, 0, 10),
         (BROOK_FALL[0] + 2, BROOK_FALL[1], 7), (wfx + 3, wfz, 7), (0, 64, 6), (sx, 50, 5), (STAR[0], -60, 5)]
ROCK_KINDS = ["tree", "ruin", "crystal", "bush", "tree", "shrine"]
rocks = []
for attempt in range(600):
    if len(rocks) >= 17:
        break
    a, dist = rng.uniform(0, math.tau), rng.uniform(70, 90)
    cx, cz = round(math.cos(a) * dist), round(math.sin(a) * dist)
    r = rng.choice((2, 3, 3, 4, 4, 5, 6))
    if abs(cx) + r > 91 or abs(cz) + r > 91:
        continue
    if any(math.hypot(cx - tx, cz - tz) < tr + r + 5 for tx, tz, tr in TAKEN + rocks):
        continue
    rocks.append((cx, cz, r))
for i, (cx, cz, r) in enumerate(rocks):
    # A rock that would float inside the Grand Bazaar is still drawn (so the rest keep their random shapes), then dropped.
    on_bazaar = BZ[0] - BZ_RX - r - 3 < cx < BZ_XMAX + r + 3 and abs(cz) < BZ_RZ + r + 3
    real_blocks = b.blocks
    if on_bazaar:
        b.blocks = {}
    cy = rng.randint(48, 100)
    cells = islet(cx, cy, cz, r)
    kind = ROCK_KINDS[i % len(ROCK_KINDS)]
    if kind == "tree" and r >= 3:
        great_tree(cx, cz, rng.choice(("azalea", "oak", "birch")), rng.randint(5, 8), y=cy)
    elif kind == "ruin":
        for dx, dz in ((-1, -1), (1, -1), (1, 1)):
            for yy in range(1, rng.randint(2, 5)):
                b.set(cx + dx, cy + yy, cz + dz, pick({"mossy_stone_bricks": 2, "cracked_stone_bricks": 1, "stone_bricks": 1},
                                                      cx + dx, yy, cz + dz))
        if b.get(cx + 1, cy + 4, cz - 1) != "air":
            b.set(cx + 1, cy + 5, cz - 1, "lantern[hanging=false]")
    elif kind == "crystal":
        b.set(cx, cy + 1, cz, "amethyst_block")
        b.set(cx, cy + 2, cz, "amethyst_cluster[facing=up]")
        for dx, dz in ((1, 0), (-1, 0), (0, 1)):
            b.set(cx + dx, cy, cz + dz, "amethyst_block")
            b.set(cx + dx, cy + 1, cz + dz, "large_amethyst_bud[facing=up]")
    elif kind == "shrine":
        b.set(cx, cy + 1, cz, "chiseled_stone_bricks")
        b.set(cx, cy + 2, cz, "stone_brick_wall")
        b.set(cx, cy + 3, cz, "soul_lantern[hanging=false]")
    else:
        b.set(cx, cy + 1, cz, "firefly_bush")
    flora(cells, 0.3)
    if on_bazaar:
        b.blocks = real_blocks


# ---------------------------------------------------------------- relief: gentle hills on open ground
HILLS = [(-30, 52, 6, 4), (-50, 44, 5, 3), (26, 50, 6, 4), (53, 24, 5, 3), (54, -6, 4, 2), (-55, -22, 5, 3),
         (46, -40, 5, 3), (-48, -46, 5, 3), (8, 54, 4, 2), (-16, 48, 5, 3), (-56, 30, 4, 2), (24, -54, 4, 2)]
free_dist = {}
frontier = [cell for cell in tops if cell in b.occupied]
for cell in frontier:
    free_dist[cell] = 0
while frontier:
    nxt = []
    for x, z in frontier:
        for dx, dz in DIRS.values():
            n = (x + dx, z + dz)
            if n in tops and n not in free_dist:
                free_dist[n] = free_dist[(x, z)] + 1
                nxt.append(n)
    frontier = nxt
for (x, z), top in list(tops.items()):
    if (x, z) in b.occupied or b.get(x, top, z) != "grass_block":
        continue
    h = sum(a * math.exp(-((x - hx) ** 2 + (z - hz) ** 2) / (2 * s * s)) for hx, hz, s, a in HILLS)
    h += max(0.0, noise(x * 0.45, z * 0.45)) * 1.6
    rim = edge_radius(math.atan2(z, x)) - math.hypot(x, z)
    h = int(min(h * max(0.0, min(1.0, (rim - 1) / 5)), (free_dist.get((x, z), 9) - 1) * 0.7))
    if h <= 0:
        continue
    b.set(x, top, z, "dirt")
    for y in range(top + 1, top + h):
        b.set(x, y, z, "dirt")
    b.set(x, top + h, z, "grass_block")
    tops[(x, z)] = top + h
# Boulders on the hills.
for (x, z), top in list(tops.items()):
    if (x, z) not in b.occupied and top > G + 1 and top != TERRACE and hash01(x, 21, z) < 0.012:
        for dx in (0, 1):
            for dz in (0, 1):
                cell = (x + dx, z + dz)
                if cell in tops and cell not in b.occupied and hash01(x + dx, 22, z + dz) < 0.8:
                    b.set(cell[0], tops[cell] + 1, cell[1], pick({"mossy_cobblestone": 2, "stone": 2, "andesite": 1, "tuff": 1},
                                                                 x + dx, 23, z + dz))
                    b.occupied.add(cell)


def elder_bloom(x, z):
    """The Elder Bloom: a giant flowering tree, the landmark of the south-west grove."""
    y = tops[(x, z)]
    trunk_top = y + 22
    for yy in range(y - 2, trunk_top + 1):
        for dx in range(-1, 2):
            for dz in range(-1, 2):
                if abs(dx) + abs(dz) < 2 or yy < y + 14:
                    b.set(x + dx, yy, z + dz, "cherry_wood" if abs(dx) + abs(dz) == 2 else "cherry_log")
    # Roots buttress the trunk.
    for dx, dz in ((2, 0), (-2, 0), (0, 2), (0, -2), (2, 2), (-2, -2), (2, -2), (-2, 2)):
        for i in range(1, 4):
            rx, rz = x + dx * i // 2, z + dz * i // 2
            ry = tops.get((rx, rz), y)
            for yy in range(ry - 1, ry + max(0, 4 - i) + 1):
                b.set(rx, yy, rz, "cherry_wood")
    # Branches fan out to canopy clusters.
    clusters = [(0, 24, 0, 6)]
    for i in range(7):
        a = i * math.tau / 7 + 0.4
        length = 6 + (i % 3)
        start = y + 13 + (i % 4) * 2
        for s in range(1, length + 1):
            b.set(x + round(math.cos(a) * s), start + s // 2, z + round(math.sin(a) * s), "cherry_wood")
        clusters.append((round(math.cos(a) * length), start + length // 2 + 1 - y, round(math.sin(a) * length), 4))
    for cx, cy, cz, r in clusters:
        for dx in range(-r, r + 1):
            for dy in range(-2, 3):
                for dz in range(-r, r + 1):
                    if (dx * dx + dz * dz) / (r * r) + dy * dy / 5.0 <= 1 and hash01(x + cx + dx, cy + dy, z + cz + dz) > 0.06:
                        leaf = "cherry_leaves" if hash01(x + cx + dx, cy + dy + 1, z + cz + dz) < 0.7 else "flowering_azalea_leaves"
                        b.setif(x + cx + dx, y + cy + dy, z + cz + dz, f"{leaf}[persistent=true]")
        # A lantern hangs under each cluster.
        lx, lz, ly = x + cx, z + cz, y + cy - 3
        if b.get(lx, ly + 1, lz).startswith(("cherry_leaves", "flowering")) and b.get(lx, ly, lz) == "air":
            b.set(lx, ly, lz, "lantern[hanging=true]")
    facings = ("north", "east", "south", "west")
    for dx in range(-9, 10):
        for dz in range(-9, 10):
            cell = (x + dx, z + dz)
            if cell in tops and dx * dx + dz * dz <= 81 and cell not in b.occupied and hash01(cell[0], 31, cell[1]) < 0.45:
                top = tops[cell]
                if b.get(cell[0], top, cell[1]) == "grass_block" and b.get(cell[0], top + 1, cell[1]) == "air":
                    b.set(cell[0], top + 1, cell[1], f"pink_petals[flower_amount=4,facing={facings[(dx + dz) % 4]}]")
    b.claim(x - 9, z - 9, x + 9, z + 9)


elder_bloom(-16, 47)


# ---------------------------------------------------------------- trees, wildflowers
TREES = [(-46, 32, "dark"), (-52, 20, "oak"), (-44, 48, "oak"), (-28, 48, "birch"), (-16, 42, "oak"), (-52, 36, "spruce"),
         (-36, 52, "birch"), (-16, 54, "dark"), (8, 46, "oak"), (16, 54, "birch"), (-8, 50, "spruce"), (28, 44, "oak"),
         (52, 6, "birch"), (52, -10, "oak"), (-12, -14, "azalea"), (14, -14, "azalea"), (-14, 24, "birch"),
         (20, 26, "oak"), (-52, -36, "spruce"), (48, -30, "dark"), (-6, -58, "spruce"), (50, -42, "spruce"),
         (-44, -50, "birch"), (-50, 4, "spruce"), (-56, -14, "birch"), (54, 24, "birch"), (34, 54, "spruce"),
         (-24, 56, "oak"), (6, 28, "birch"), (-8, 32, "oak"), (22, -56, "spruce"), (-24, -54, "oak"), (-38, -56, "spruce"),
         (56, -20, "spruce"), (-58, 12, "oak"), (-20, 14, "azalea"), (14, 12, "azalea")]
for x, z, kind in TREES:
    if (x, z) in tops and on_main(x, z, 4) and not any((x + dx, z + dz) in b.occupied
                                                       for dx in range(-2, 4) for dz in range(-2, 4)):
        great_tree(x, z, kind)

FLORA = {"short_grass": 40, "fern": 6, "wildflowers[flower_amount=3,facing=north]": 7,
         "pink_petals[flower_amount=2,facing=east]": 2, "poppy": 2, "dandelion": 2, "cornflower": 2, "oxeye_daisy": 2,
         "azure_bluet": 2, "bush": 3, "firefly_bush": 2, "leaf_litter[segment_amount=3,facing=south]": 3}
for (x, z), top in tops.items():
    if (x, z) in b.occupied or b.get(x, top, z) != "grass_block" or b.get(x, top + 1, z) != "air":
        continue
    if hash01(x, 11, z) < 0.26:
        b.set(x, top + 1, z, pick(FLORA, x, 12, z))
    elif hash01(x, 13, z) < 0.05:
        b.set(x, top, z, pick({"moss_block": 2, "podzol": 1, "coarse_dirt": 1}, x, 14, z))


# ---------------------------------------------------------------- display decor (spawned and animated by the hub mod)
DECOR_TAG = "skyrpg_hub_decor"
decor = []


def qmul(p, q):
    px, py, pz, pw = p
    qx, qy, qz, qw = q
    return (pw * qx + px * qw + py * qz - pz * qy, pw * qy - px * qz + py * qw + pz * qx,
            pw * qz + px * qy - py * qx + pz * qw, pw * qw - px * qx - py * qy - pz * qz)


def qaxis(axis, deg):
    h = math.radians(deg) / 2
    v = {"x": (1, 0, 0), "y": (0, 1, 0), "z": (0, 0, 1)}[axis]
    return (v[0] * math.sin(h), v[1] * math.sin(h), v[2] * math.sin(h), math.cos(h))


def qrot(q, v):
    x, y, z, _ = qmul(qmul(q, (v[0], v[1], v[2], 0.0)), (-q[0], -q[1], -q[2], q[3]))
    return (x, y, z)


CORNER = qmul(qaxis("z", math.degrees(math.atan(1 / math.sqrt(2)))), qaxis("x", 45))


def add_decor(decor_id, item, pos, scale, spin=0.0, axis="y", phase=0.0, lift=(0.0, 0.0, 0.0), tilt=(0.0, 0.0, 0.0, 1.0),
              bob=0.0, bob_period=4.0, glow=True, model=None):
    piece = {"id": decor_id, "item": item}
    if model:
        piece["model"] = model
    decor.append(piece)
    piece.update({"pos": [round(v, 3) for v in pos],
                  "scale": [round(v, 3) for v in (scale if isinstance(scale, (list, tuple)) else (scale,) * 3)],
                  "spin": spin, "axis": axis, "phase": phase, "lift": [round(v, 3) for v in lift],
                  "tilt": [round(v, 6) for v in tilt], "bob": bob, "bob_period": bob_period, "glow": glow})


def pose(d, t=0.0):
    """The transformation at time t (seconds). HubDecor.java does the same maths."""
    turn = qaxis(d["axis"], d["phase"] + d["spin"] * t)
    lift = qrot(turn, d["lift"])
    bob = d["bob"] * math.sin(2 * math.pi * t / d["bob_period"] + math.radians(d["phase"]))
    return qmul(turn, tuple(d["tilt"])), (lift[0], lift[1] + bob, lift[2]), d["scale"]


CRYSTAL = (0.5, G + 16.5, 0.5)
add_decor("skyheart", "minecraft:amethyst_block", CRYSTAL, 3.2, spin=24, tilt=CORNER, bob=0.35, bob_period=6)
add_decor("skyheart_core", "minecraft:pearlescent_froglight", CRYSTAL, 1.7, spin=-50, tilt=CORNER, bob=0.35, bob_period=6)
for i in range(5):
    add_decor(f"skyheart_shard_{i}", "minecraft:purple_stained_glass", CRYSTAL, 0.75, spin=36, phase=i * 72,
              lift=(4.2, 0.0, 0.0), tilt=CORNER, bob=0.7, bob_period=5)
for i in range(4):
    add_decor(f"windmill_sail_{i}", "minecraft:white_wool", WINDMILL_HUB, (0.08, 6.5, 1.8), spin=22, axis="x", phase=i * 90,
              lift=(0.0, 3.9, 0.95), glow=False)
    add_decor(f"windmill_spar_{i}", "minecraft:stripped_dark_oak_log", WINDMILL_HUB, (0.22, 7.6, 0.22), spin=22, axis="x",
              phase=i * 90, lift=(0.05, 3.9, 0.0), glow=False)
add_decor("windmill_hub", "minecraft:dark_oak_planks", WINDMILL_HUB, 0.7, spin=22, axis="x", glow=False)
# One still, upright veil filling the gate's 9x9 opening; its corners hide inside the ring's stone. The particle
# emitters below do the swirling.
add_decor("wildgate_veil_0", "minecraft:purple_stained_glass", (0.5, RING_C + 0.5, GATE_Z + 1.0), (9.6, 9.6, 0.04), axis="z")
SKY_LANTERNS = [(-18, 79, 8), (16, 82, -6), (-6, 86, -32), (26, 76, 30), (-34, 74, -2), (40, 80, -22), (-26, 84, 36),
                (4, 90, 44), (54, 76, 12), (-48, 82, -40), (10, 74, -44), (-10, 72, 64), (32, 86, 62), (-56, 64, 22)]
for i, (x, y, z) in enumerate(SKY_LANTERNS):
    # A lantern's item icon is flat; the sky_lantern model shows the 3D lantern block instead.
    add_decor(f"sky_lantern_{i}", "minecraft:lantern", (x + 0.5, y, z + 0.5), 1.6, spin=9 * (1 if i % 2 else -1),
              phase=i * 47 % 360, bob=0.5, bob_period=5 + i % 4, model="skyrpg_hub:sky_lantern")
# The Arcane Library's book: two cream pages and red covers opening in a shallow V, gold corners, lines of text, all
# bobbing and slowly turning together; a spinning crystal above it; enchanted books circling the hall.
BOOK_SPIN, BOOK_BOB, BOOK_PERIOD = 7.0, 0.35, 5.0
for side in (-1, 1):
    tilt = qaxis("z", 16 * side)
    page = (side * 1.75, 0.45, 0.0)
    add_decor(f"library_page_{side}", "minecraft:smooth_sandstone", LIBRARY_BOOK, (3.3, 0.32, 4.8), spin=BOOK_SPIN, lift=page, tilt=tilt,
              bob=BOOK_BOB, bob_period=BOOK_PERIOD)
    cover = (side * 1.85, 0.12, 0.0)
    add_decor(f"library_cover_{side}", "minecraft:red_wool", LIBRARY_BOOK, (3.7, 0.22, 5.3), spin=BOOK_SPIN, lift=cover, tilt=tilt,
              bob=BOOK_BOB, bob_period=BOOK_PERIOD)
    for end_z in (-1, 1):
        corner = (side * 3.55, 0.75 + 0.05, end_z * 2.5)
        add_decor(f"library_corner_{side}_{end_z}", "minecraft:gold_block", LIBRARY_BOOK, 0.45, spin=BOOK_SPIN, lift=corner, tilt=tilt,
                  bob=BOOK_BOB, bob_period=BOOK_PERIOD)
    up = qrot(tilt, (0.0, 0.17, 0.0))
    for line in range(5):
        z = -1.7 + line * 0.85
        length = 2.4 if line % 2 else 1.9
        pos = (page[0] + up[0], page[1] + up[1], z)
        add_decor(f"library_text_{side}_{line}", "minecraft:black_concrete", LIBRARY_BOOK, (length, 0.04, 0.14), spin=BOOK_SPIN, lift=pos,
                  tilt=tilt, bob=BOOK_BOB, bob_period=BOOK_PERIOD, glow=False)
add_decor("library_spine", "minecraft:red_wool", LIBRARY_BOOK, (0.5, 0.5, 5.3), spin=BOOK_SPIN, lift=(0.0, 0.0, 0.0), bob=BOOK_BOB,
          bob_period=BOOK_PERIOD)
LIBRARY_CRYSTAL = (LIBRARY_BOOK[0], LIBRARY_BOOK[1] + 6.5, LIBRARY_BOOK[2])
add_decor("library_crystal", "minecraft:amethyst_block", LIBRARY_CRYSTAL, 1.7, spin=30, tilt=CORNER, bob=0.3, bob_period=4)
add_decor("library_crystal_core", "minecraft:pearlescent_froglight", LIBRARY_CRYSTAL, 0.95, spin=-55, tilt=CORNER, bob=0.3, bob_period=4)
for i in range(4):
    add_decor(f"library_crystal_shard_{i}", "minecraft:purple_stained_glass", LIBRARY_CRYSTAL, 0.45, spin=48, phase=i * 90,
              lift=(2.2, 0.0, 0.0), tilt=CORNER, bob=0.5, bob_period=3.5)
for i in range(6):
    add_decor(f"library_tome_{i}", "minecraft:enchanted_book", (SPIRE[0] + 0.5, LIB_FLOOR + 7.5, SPIRE[2] + 0.5), 1.6, spin=11, phase=i * 60,
              lift=(11.5, 0.0, 0.0), bob=0.6, bob_period=4 + i % 3)
add_decor("starwatch_telescope", "minecraft:spyglass", (STAR[0] + 0.5, STAR[1] + 2.6, STAR[2] + 0.5), 3.0,
          tilt=qmul(qaxis("y", 35), qaxis("x", -55)), glow=False)


def summon(d):
    rotation, translation, scale = pose(d)
    t = (f"transformation:{{left_rotation:[{rotation[0]:.5f}f,{rotation[1]:.5f}f,{rotation[2]:.5f}f,{rotation[3]:.5f}f],"
         f"right_rotation:[0f,0f,0f,1f],translation:[{translation[0]:.3f}f,{translation[1]:.3f}f,{translation[2]:.3f}f],"
         f"scale:[{scale[0]}f,{scale[1]}f,{scale[2]}f]}}")
    bright = ",brightness:{sky:15,block:15}" if d["glow"] else ""
    components = f',components:{{"minecraft:item_model":"{d["model"]}"}}' if d.get("model") else ""
    nbt = (f'{{item:{{id:"{d["item"]}",count:1{components}}},item_display:"none",{t}{bright},view_range:2f,'
           f'Tags:["{DECOR_TAG}","{DECOR_TAG}.{d["id"]}"]}}')
    x, y, z = d["pos"]
    return f"summon item_display {x} {y} {z} {nbt}"


b.entities = [summon(d) for d in decor]


def emitter(particle, pos, spread=(0.0, 0.0, 0.0), count=1, speed=0.0, interval=10, far=False):
    return {"particle": f"minecraft:{particle}", "pos": [round(v, 2) for v in pos], "spread": list(spread), "count": count,
            "speed": speed, "interval": interval, "far": far}


EMITTERS = [
    emitter("end_rod", CRYSTAL, (2.2, 2.2, 2.2), 1, 0.01, 4, far=True),
    emitter("wax_on", CRYSTAL, (3.5, 3.0, 3.5), 2, 0.0, 6, far=True),
    emitter("splash", (0.5, G + 2.2, 0.5), (3.0, 0.1, 3.0), 3, 0.0, 4),
    emitter("reverse_portal", (0.5, RING_C + 0.5, GATE_Z + 1.0), (2.4, 2.4, 0.2), 8, 0.02, 2),
    emitter("portal", (0.5, RING_C + 0.5, GATE_Z + 1.0), (2.6, 2.6, 0.3), 12, 0.4, 2),
    emitter("end_rod", (0.5, RING_C + 0.5, GATE_Z + 1.0), (2.8, 2.8, 0.4), 2, 0.01, 4),
    emitter("enchant", LIBRARY_BOOK, (3.0, 1.6, 3.0), 10, 0.8, 2, far=True),
    emitter("end_rod", LIBRARY_CRYSTAL, (1.2, 1.2, 1.2), 1, 0.01, 6, far=True),
    emitter("witch", LIBRARY_CRYSTAL, (1.5, 1.0, 1.5), 1, 0.0, 10, far=True),
    emitter("falling_obsidian_tear", (sx + 0.5, LTOP - 0.6, LN0 - 0.6), (6.5, 0.0, 0.1), 1, 0.0, 6),
    emitter("falling_obsidian_tear", (sx + 0.5, LTOP - 0.6, LN1 + 1.6), (6.5, 0.0, 0.1), 1, 0.0, 6),
    emitter("falling_obsidian_tear", (LW0 - 0.6, LTOP - 0.6, sz + 0.5), (0.1, 0.0, 6.5), 1, 0.0, 6),
    emitter("falling_obsidian_tear", (LW1 + 1.6, LTOP - 0.6, sz + 0.5), (0.1, 0.0, 6.5), 1, 0.0, 6),
    emitter("portal", (sx + 0.5, LIB_FLOOR + 3.5, LN0 - 0.5), (1.2, 1.5, 0.3), 4, 0.3, 4),
    emitter("enchant", (sx + 0.5, LIB_FLOOR + 2.5, sz + 0.5), (2.5, 1.0, 2.5), 4, 0.6, 4),
    emitter("lava", (22.5, TERRACE + 1.9, -31.5), (0.6, 0.0, 0.2), 1, 0.0, 25),
    emitter("cloud", (fall_x + 0.5, 47.3, 0.5), (1.0, 0.3, 1.0), 2, 0.02, 5),
    emitter("cloud", (BROOK_FALL[0] + 0.5, 37.3, BROOK_FALL[1] + 1.0), (1.0, 0.3, 1.0), 2, 0.02, 5),
    emitter("cloud", (wfx + 1.5, 45.3, wfz + 0.5), (1.0, 0.3, 1.0), 2, 0.02, 5),
    emitter("end_rod", (STAR[0] + 0.5, STAR[1] + 12.0, STAR[2] + 0.5), (0.6, 0.4, 0.6), 1, 0.005, 12),
]

# The Skyhaven waystone, just left of spawn: travel out to any waystone you've attuned in the world (the world mod).
b.set(-6, G + 1, 18, "skyrpg_world:waystone[lit=true]")

# ---------------------------------------------------------------- outputs
SPAWN = {"x": 0.5, "y": float(G + 1), "z": 16.5, "yaw": 180.0, "pitch": -6.0}
commands = b.commands()
OUT.parent.mkdir(parents=True, exist_ok=True)
OUT.write_text("\n".join(commands) + "\n", encoding="utf-8")


def write_map(path, blocks):
    path.write_text("".join(f"{x} {y} {z} {block}\n" for (x, y, z), block in blocks.items()), encoding="utf-8")


def read_map(path):
    blocks = {}
    for line in path.read_text(encoding="utf-8").splitlines():
        x, y, z, block = line.split(" ", 3)
        blocks[(int(x), int(y), int(z))] = block
    return blocks


def check_spills(blocks):
    """Every place water can run off an edge must land in water inside the box, or it would pour out forever.
    Waterlogged blocks count as water, including sea pickles, conduits and corals, which are waterlogged unless told not to be."""
    for (x, y, z), block in blocks.items():
        default_wet = block.split("[")[0] in ("sea_pickle", "conduit") or "coral" in block.split("[")[0]
        if not (block.startswith("water") or "waterlogged=true" in block
                or (default_wet and "waterlogged=false" not in block)):
            continue
        for dx, dz in DIRS.values():
            nx, nz = x + dx, z + dz
            if (nx, y, nz) in blocks or (nx, y - 1, nz) in blocks:
                continue
            ly = y - 1
            while ly >= BOX_MIN[1] and (nx, ly, nz) not in blocks:
                ly -= 1
            landing = blocks.get((nx, ly, nz), "the void")
            if not landing.startswith("water"):
                warnings.append(f"water at {x} {y} {z} spills over {nx} {nz} onto {landing} at y {ly}")


check_spills(b.final_blocks())
for w in warnings[-10:]:
    if w.startswith("water"):
        print("WARNING", w)
write_map(BLOCKS, b.final_blocks())
if WORLD.exists():
    update = b.commands(read_map(WORLD))
    UPDATE.write_text("\n".join(update) + "\n", encoding="utf-8")
    print(f"update: {len(update)} commands")

AREAS = [("plaza", 0, 0), ("bank", 0, -46), ("emberforge", 25, -38), ("mason's guild", -25, -38), ("windmill", -43, -31),
         ("greenhollow", -38, -10), ("fields", -37, 16), ("mistfall", -60, 0), ("timberline", -32, 35),
         ("elder bloom", -16, 47), ("gilded gull", 30, 22), ("beer garden", 45, 21), ("gazebo", 12, 36),
         ("bazaar promenade", 33, 0), ("grand bazaar", 81, 0), ("koi garden", 40, -17), ("wildgate", 0, 77), ("arcane library", 44, 71),
         ("starwatch", 38, -78)]


def area_of(x, z):
    name, ax, az = min(AREAS, key=lambda a: (a[1] - x) ** 2 + (a[2] - z) ** 2)
    return name if math.hypot(ax - x, az - z) < 30 else "outskirts"


def apply_three_way(area=None):
    """Applies this build over the last one placed, skipping every cell a player changed since (world != last build).
    With ``area`` (x0, z0, x1, z1) only cells inside it are applied; the rest stay pending in the record for later."""
    import sys
    sys.path.insert(0, str(HERE.parent))
    import mc
    previous, new = read_map(WORLD), read_map(BLOCKS)
    touched = [pos for pos in set(previous) | set(new) if previous.get(pos) != new.get(pos)]
    outside = []
    if area:
        x0, z0, x1, z1 = area
        outside = [pos for pos in touched if not (x0 <= pos[0] <= x1 and z0 <= pos[2] <= z1)]
        touched = [pos for pos in touched if x0 <= pos[0] <= x1 and z0 <= pos[2] <= z1]
    rcon = mc.connect()
    p = f"execute in {DIM} run "
    box = f"{BOX_MIN[0]} {BOX_MIN[2]} {BOX_MAX[0]} {BOX_MAX[2]}"
    skipped, already = [], []
    try:
        rcon.command(p + f"forceload add {box}")
        for x, y, z in touched:
            old = previous.get((x, y, z))
            # Compare block types only: the game itself adjusts states (stair corners, fence links, water levels).
            expect = [old.split("[")[0].split("{")[0]] if old else ["air", "water"]
            if any("passed" in rcon.command(f"execute in {DIM} if block {x} {y} {z} {block}") for block in expect):
                continue
            # Someone already placed what this build wants (e.g. by hand, then added it here): just record it.
            want = new[(x, y, z)].split("[")[0].split("{")[0] if (x, y, z) in new else "air"
            if "passed" in rcon.command(f"execute in {DIM} if block {x} {y} {z} {want}"):
                already.append((x, y, z))
            else:
                skipped.append((x, y, z))
    finally:
        rcon.close()
    commands = b.commands(previous, skip=set(skipped) | set(already) | set(outside))
    mc.run_commands(commands, quiet=True)
    applied = dict(new)
    for pos in skipped + outside:
        if pos in previous:
            applied[pos] = previous[pos]
        else:
            applied.pop(pos, None)
    write_map(WORLD, applied)
    print(f"three-way update: {len(touched)} cells differ from the last build, {len(skipped)} skipped as player edits, "
          f"{len(already)} already in place" + (f", {len(outside)} outside the area left for later" if outside else ""))
    by_area = {}
    for x, y, z in skipped:
        by_area.setdefault(area_of(x, z), []).append((x, y, z))
    for name, cells in sorted(by_area.items(), key=lambda item: -len(item[1])):
        print(f"  {name}: {len(cells)} e.g. {cells[:6]}")
    (OUT.parent / "hub_skyhaven_skipped.txt").write_text("".join(f"{x} {y} {z}\n" for x, y, z in skipped), encoding="utf-8")


# --apply [--area x0,z0,x1,z1]: update the running server's hub (only inside the area, if given).
_argv = __import__("sys").argv
if "--apply" in _argv:
    apply_three_way([int(v) for v in _argv[_argv.index("--area") + 1].split(",")] if "--area" in _argv else None)
SPOTS.write_text(json.dumps(spots, indent=2) + "\n", encoding="utf-8")
(OUT.parent / "hub_skyhaven_config.json").write_text(json.dumps({
    "spawn": SPAWN, "survival_portals": [PORTAL], "decor": decor, "emitters": EMITTERS,
    "build": {"origin": list(BOX_MIN), "size": [BOX_MAX[i] - BOX_MIN[i] + 1 for i in range(3)]}}, indent=2) + "\n",
    encoding="utf-8")
# hub.json takes the spawn, gate, box, decor, emitters and podiums from here; void_y, structure and version stay as they are.
HUB_JSON = HERE.parent.parent / "hub" / "src" / "main" / "resources" / "data" / "skyrpg_hub" / "skyrpg" / "hub" / "hub.json"
hub_json = json.loads(HUB_JSON.read_text(encoding="utf-8"))
hub_json.update({"spawn": SPAWN, "survival_portals": [PORTAL], "decor": decor, "emitters": EMITTERS, "podiums": PODIUMS})
hub_json["build"].update({"origin": list(BOX_MIN), "size": [BOX_MAX[i] - BOX_MIN[i] + 1 for i in range(3)]})
HUB_JSON.write_text(re.sub(r"\[\s*((?:-?[\d.e]+,\s*)*-?[\d.e]+)\s*\]", lambda m: "[" + re.sub(r",\s*", ", ", m.group(1)) + "]",
                           json.dumps(hub_json, indent=2)) + "\n", encoding="utf-8")
print(f"{len(b.blocks)} blocks, {len(decor)} decor -> {len(commands)} commands")
print(f"road ends {END}, mistfall edge x={edge_x}, gate z={GATE_Z}, portal {PORTAL}")
for w in warnings:
    print("WARNING", w)
