#!/usr/bin/env python3
"""Generates the first version of the hub: a floating island with a Hypixel-style village.

    python tools/builds/hub_village.py              # writes tools/builds/out/hub_village.mcfunction
    python tools/mc.py script --quiet tools/builds/out/hub_village.mcfunction

This builds the starting point only. Once the hub has been hand-edited in game and saved with
/hubadmin save, the saved structure file is the source of truth, not this script.

Layout (north is -z): plaza with fountain at 0,0 and four roads.
  North road: Bank (east), Farmer and fields (west)
  East road:  Blacksmith (north), market stalls (south)
  South road: Lumberjack (west), Enchanter (east), survival gate at the end
  West road:  Tavern (north), Builder (south)
"""
import math
import random
from pathlib import Path

DIM = "skyrpg_hub:hub"
GROUND = 64  # top of the grass; players stand at y=65
BOX_MIN, BOX_MAX = (-64, 16, -64), (63, 119, 63)
OUT = Path(__file__).resolve().parent / "out" / "hub_village.mcfunction"

rng = random.Random(1337)


def noise(x, z, scale=1.0):
    """Cheap smooth-ish noise in [-1, 1] from a few sine waves."""
    return (math.sin(x * 0.21 * scale + 1.3) * math.cos(z * 0.17 * scale + 0.4)
            + math.sin((x + z) * 0.11 * scale + 2.2) * 0.6
            + math.sin(x * 0.53 * scale - z * 0.47 * scale) * 0.3) / 1.9


def pick(weights, x, y, z):
    """Deterministic weighted choice per position."""
    h = (x * 73856093) ^ (y * 19349663) ^ (z * 83492791)
    r = (h & 0xFFFF) / 0xFFFF * sum(weights.values())
    for block, weight in weights.items():
        r -= weight
        if r <= 0:
            return block
    return next(iter(weights))


class Build:
    # Blocks that need support or a neighbour to exist, placed after everything else.
    LATE = ("lantern", "torch", "sign", "door", "short_grass", "fern", "poppy", "dandelion", "cornflower",
            "oxeye_daisy", "azure_bluet", "allium", "wheat", "carrots", "potatoes", "beetroots", "water",
            "campfire", "carpet", "pressure_plate", "flower_pot", "potted_", "vine", "lily_pad", "bell", "sugar_cane")

    def __init__(self):
        self.blocks = {}
        self.doors = []
        self.occupied = set()  # (x, z) columns used by buildings/roads, kept free of trees and flowers

    def set(self, x, y, z, block):
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

    # /fill and /setblock keep the exact state given, so fences, panes and walls must be told which
    # sides connect or they show up as lone posts (and fences would have gaps you can walk through).
    PANES = ("glass_pane", "iron_bars")
    NOT_SOLID = ("slab", "stairs", "lantern", "sign", "door", "carpet", "leaves", "water", "campfire", "pressure_plate",
                 "anvil", "grindstone", "enchanting_table", "cauldron", "composter", "flower", "farmland", "dirt_path")

    def connect(self):
        sides = {"north": (0, -1), "south": (0, 1), "west": (-1, 0), "east": (1, 0)}
        for (x, y, z), block in list(self.blocks.items()):
            if "[" in block:
                continue
            kind = "pane" if block.endswith(self.PANES) else "fence" if block.endswith("_fence") else "wall" if block.endswith("_wall") else None
            if kind is None:
                continue
            states = []
            for side, (dx, dz) in sides.items():
                other = self.blocks.get((x + dx, y, z + dz), "air").split("[")[0]
                if kind == "pane":
                    joins = other.endswith(self.PANES) or self.solid(other)
                elif kind == "fence":
                    joins = other.endswith(("_fence", "_fence_gate")) or self.solid(other)
                else:
                    joins = other.endswith(("_wall", "_fence_gate", "glass_pane", "iron_bars")) or self.solid(other)
                states.append(f"{side}={('low' if joins else 'none') if kind == 'wall' else str(joins).lower()}")
            self.blocks[(x, y, z)] = f"{block}[{','.join(states)}]"

    def solid(self, block):
        return (block != "air" and not self.is_late(block) and not block.endswith(("_fence", "_wall", "glass_pane", "iron_bars"))
                and not any(word in block for word in self.NOT_SOLID))

    def commands(self):
        self.connect()
        prefix = f"execute in {DIM} run "
        out = [prefix + f"forceload add {BOX_MIN[0]} {BOX_MIN[2]} {BOX_MAX[0]} {BOX_MAX[2]}"]
        for x in range(BOX_MIN[0], BOX_MAX[0] + 1, 32):
            for y in range(BOX_MIN[1], BOX_MAX[1] + 1, 32):
                for z in range(BOX_MIN[2], BOX_MAX[2] + 1, 32):
                    # strict = no block updates, so clearing never knocks plants loose as dropped items
                    out.append(prefix + f"fill {x} {y} {z} {min(x + 31, BOX_MAX[0])} {min(y + 31, BOX_MAX[1])} {min(z + 31, BOX_MAX[2])} air strict")
        rows = {}
        late = []
        for (x, y, z), block in self.blocks.items():
            if block == "air":
                continue
            if self.is_late(block):
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
                out.append(prefix + (f"setblock {start} {y} {z} {current}" if start == prev
                                     else f"fill {start} {y} {z} {prev} {y} {z} {current}"))
                if x is not None:
                    start, prev, current = x, x, block
        for y, x, z, block in sorted(late, key=lambda b: ("water" in b[3], b[0])):
            out.append(prefix + f"setblock {x} {y} {z} {block}")
        for x, y, z, door in self.doors:
            out.append(prefix + f"setblock {x} {y} {z} {door},half=lower]")
            out.append(prefix + f"setblock {x} {y + 1} {z} {door},half=upper]")
        # Anything that still popped off while building would otherwise lie around in the hub forever.
        out.append(prefix + "kill @e[type=item]")
        out.append(prefix + "forceload remove all")
        return out


b = Build()


# ---------------------------------------------------------------- island
def edge_radius(theta):
    return 55 + 3.5 * math.sin(3 * theta + 0.7) + 2.0 * math.sin(5 * theta + 2.1) + 1.2 * math.sin(9 * theta)


def on_island(x, z, inset=0):
    return math.hypot(x, z) <= edge_radius(math.atan2(z, x)) - inset


UNDERSIDE = {"stone": 50, "andesite": 18, "cobblestone": 10, "tuff": 8, "dirt": 6, "coal_ore": 4, "gravel": 3, "iron_ore": 1}
for x in range(-63, 64):
    for z in range(-63, 64):
        r = edge_radius(math.atan2(z, x))
        d = math.hypot(x, z)
        if d > r:
            continue
        t = d / r
        depth = int(3 + 37 * (1 - t ** 1.4) ** 0.8 + noise(x, z) * 4 + noise(z * 2.3, x * 1.7) * 3)
        bottom = max(GROUND - max(depth, 2), BOX_MIN[1] + 10)
        soil = 2 + round(noise(x * 1.9, z * 1.9) * 1.5 + 0.5)  # 1-4 blocks of dirt, so the edge isn't a flat band
        b.set(x, GROUND, z, "grass_block")
        for y in range(GROUND - soil, GROUND):
            b.set(x, y, z, pick({"dirt": 8, "coarse_dirt": 2, "rooted_dirt": 1}, x, y, z) if t > 0.9 else "dirt")
        for y in range(bottom, GROUND - soil):
            # Only the outer shell is seen, so keep the inside plain stone (fewer, longer fill runs).
            b.set(x, y, z, pick(UNDERSIDE, x, y, z) if y < bottom + 3 or t > 0.9 else "stone")

# A few hanging stone spikes under the island.
for _ in range(14):
    a, d = rng.uniform(0, math.tau), rng.uniform(5, 40)
    x, z = int(math.cos(a) * d), int(math.sin(a) * d)
    if b.get(x, GROUND, z) == "air":
        continue
    y = next(yy for yy in range(BOX_MIN[1], GROUND + 1) if b.get(x, yy, z) != "air")
    length = rng.randint(3, 7)
    for i in range(length):
        width = max(0, (length - i) // 3)
        for dx in range(-width, width + 1):
            for dz in range(-width, width + 1):
                if y - 1 - i > BOX_MIN[1]:
                    b.set(x + dx, y - 1 - i, z + dz, pick({"stone": 3, "andesite": 1, "tuff": 1}, x + dx, i, z + dz))

# Vines hanging down the island's sides.
SIDES = {"north": (0, -1), "south": (0, 1), "west": (-1, 0), "east": (1, 0)}
for x in range(-63, 64):
    for z in range(-63, 64):
        if b.get(x, GROUND, z) != "grass_block":
            continue
        for face, (dx, dz) in SIDES.items():
            # The vine hangs in the air next to the island, stuck to the island block (on its "face" side).
            ax, az = x - dx, z - dz
            if b.get(ax, GROUND, az) != "air" or rng.random() > 0.12:
                continue
            for i in range(rng.randint(2, 9)):
                y = GROUND - 1 - i
                if b.get(ax, y, az) != "air":
                    break
                b.set(ax, y, az, f"vine[{face}=true]")

# ---------------------------------------------------------------- plaza and fountain
PLAZA = 15.0
for x in range(-16, 17):
    for z in range(-16, 17):
        d = math.hypot(x, z)
        if d >= PLAZA:
            continue
        if d >= PLAZA - 1.5:
            block = "stone_bricks"
        elif (x == 0 or z == 0) and d >= 6:
            block = "stone_bricks"
        elif abs(d - 9.5) < 0.6:
            block = "chiseled_stone_bricks" if (x + z) % 4 == 0 else "stone_bricks"
        else:
            block = pick({"polished_andesite": 80, "andesite": 12, "cracked_stone_bricks": 4, "stone_bricks": 4}, x, 0, z)
        b.set(x, GROUND, z, block)
        b.occupied.add((x, z))

# Basin: rim at radius 5, two-deep water inside, lights under the water.
for x in range(-6, 7):
    for z in range(-6, 7):
        d = math.hypot(x, z)
        if 4.5 <= d < 5.5:
            b.set(x, GROUND + 1, z, "stone_bricks")
            b.set(x, GROUND + 2, z, "stone_brick_slab[type=bottom]")
        elif d < 4.5:
            b.set(x, GROUND - 1, z, "sea_lantern" if (x % 3 == 0 and z % 3 == 0) else "stone_bricks")
            b.set(x, GROUND, z, "water")
            b.set(x, GROUND + 1, z, "water")
# Centre column with a bowl on top; water spills over the bowl into the basin.
b.fill(0, GROUND - 1, 0, 0, GROUND + 3, 0, "chiseled_stone_bricks")
for dx, dz, facing in [(1, 0, "west"), (-1, 0, "east"), (0, 1, "north"), (0, -1, "south")]:
    b.set(dx, GROUND + 3, dz, f"stone_brick_stairs[facing={facing},half=top]")
b.set(0, GROUND + 4, 0, "chiseled_stone_bricks")
for dx in (-1, 0, 1):
    for dz in (-1, 0, 1):
        if dx or dz:
            b.set(dx, GROUND + 4, dz, "stone_brick_slab[type=top]")
b.set(0, GROUND + 5, 0, "water")

# Flower beds on the plaza diagonals.
for sx in (-1, 1):
    for sz in (-1, 1):
        cx, cz = 9 * sx, 9 * sz
        for x in range(cx - 2, cx + 3):
            for z in range(cz - 2, cz + 3):
                edge = abs(x - cx) == 2 or abs(z - cz) == 2
                b.set(x, GROUND, z, "grass_block")
                if edge:
                    b.set(x, GROUND + 1, z, "stone_brick_slab[type=bottom]")
                else:
                    b.set(x, GROUND + 1, z, pick({"poppy": 2, "dandelion": 2, "cornflower": 2, "azure_bluet": 1, "allium": 1}, x, 1, z))
        b.fill(cx, GROUND + 1, cz, cx, GROUND + 2, cz, "oak_log")
        for dx in range(-1, 2):
            for dz in range(-1, 2):
                b.set(cx + dx, GROUND + 3, cz + dz, "oak_leaves[persistent=true]")
        b.set(cx, GROUND + 4, cz, "oak_leaves[persistent=true]")


def lamp(x, z, base="stone_brick_wall"):
    b.set(x, GROUND + 1, z, base)
    b.fill(x, GROUND + 2, z, x, GROUND + 3, z, "spruce_fence")
    b.set(x, GROUND + 4, z, "lantern[hanging=false]")
    b.occupied.add((x, z))


for i in range(8):
    a = math.radians(22.5 + 45 * i)
    lamp(round(math.cos(a) * 16.5), round(math.sin(a) * 16.5))

# ---------------------------------------------------------------- roads
ROAD = {"cobblestone": 30, "andesite": 25, "stone": 15, "gravel": 10, "mossy_cobblestone": 8, "stone_bricks": 7, "cracked_stone_bricks": 5}
ROAD_END = {}
for name, (dx, dz) in {"north": (0, -1), "south": (0, 1), "east": (1, 0), "west": (-1, 0)}.items():
    i = int(PLAZA) - 1
    while on_island(dx * (i + 1), dz * (i + 1), inset=3):
        i += 1
    ROAD_END[name] = i
    for s in range(int(PLAZA) - 1, i + 1):
        for w in range(-2, 3):
            x, z = dx * s + (w if dz else 0), dz * s + (w if dx else 0)
            b.set(x, GROUND, z, pick(ROAD, x, 0, z))
            b.occupied.add((x, z))
        if s > 20 and s % 10 == 0:
            for side in (-3, 3):
                lamp(dx * s + (side if dz else 0), dz * s + (side if dx else 0), base="cobblestone_wall")


# ---------------------------------------------------------------- buildings
def gable_roof(x0, z0, x1, z1, y, stairs, slab, gable, along_x):
    """Roof over the box x0..x1 / z0..z1 starting at height y, with a 1-block overhang."""
    if along_x:
        lo, hi, a0, a1 = z0 - 1, z1 + 1, x0 - 1, x1 + 1
    else:
        lo, hi, a0, a1 = x0 - 1, x1 + 1, z0 - 1, z1 + 1
    i = 0
    while lo + i <= hi - i:
        yy = y + i
        for a in range(a0, a1 + 1):
            def put(c, block):
                if along_x:
                    b.set(a, yy, c, block)
                else:
                    b.set(c, yy, a, block)
            if lo + i == hi - i:
                put(lo + i, slab)
            else:
                put(lo + i, f"{stairs}[facing={'south' if along_x else 'east'},half=bottom]")
                put(hi - i, f"{stairs}[facing={'north' if along_x else 'west'},half=bottom]")
        # Fill the triangular gable walls under this roof row.
        for c in range(lo + i + 1, hi - i):
            for end in ((x0, x1) if along_x else (z0, z1)):
                if along_x:
                    b.set(end, yy, c, gable)
                else:
                    b.set(c, yy, end, gable)
        i += 1
    return y + i


def building(x0, z0, x1, z1, door_side, p, wall_h=4, sign=None):
    """A medieval house: stone base, framed walls, windows, gable roof. Returns the door position."""
    b.claim(x0, z0, x1, z1, margin=2)
    top = GROUND + wall_h
    b.fill(x0, GROUND, z0, x1, GROUND, z1, p["base"])
    b.fill(x0 + 1, GROUND, z0 + 1, x1 - 1, GROUND, z1 - 1, p["floor"])
    for x in range(x0, x1 + 1):
        for z in range(z0, z1 + 1):
            if x not in (x0, x1) and z not in (z0, z1):
                continue
            corner = x in (x0, x1) and z in (z0, z1)
            along = (x - x0) if z in (z0, z1) else (z - z0)
            pillar = corner or along % 4 == 0
            for y in range(GROUND + 1, top + 1):
                if y == GROUND + 1:
                    block = p["base"]
                elif pillar:
                    block = p["frame"]
                elif y == top:
                    block = p["beam"]
                elif y in (GROUND + 2, GROUND + 3) and along % 2 == 0 and wall_h >= 4:
                    block = p["window"]
                else:
                    block = p["wall"]
                b.set(x, y, z, block)
    along_x = (x1 - x0) >= (z1 - z0)
    gable_roof(x0, z0, x1, z1, top + 1, p["stairs"], p["slab"], p["gable"], along_x)
    cx, cz = (x0 + x1) // 2, (z0 + z1) // 2
    door = {"north": (cx, z0, "north"), "south": (cx, z1, "south"), "west": (x0, cz, "west"), "east": (x1, cz, "east")}[door_side]
    dx, dz, facing = door
    if door_side in ("north", "south"):
        b.fill(dx - 1, GROUND + 1, dz, dx + 1, GROUND + 3, dz, p["frame"])
        b.door(dx, GROUND + 1, dz, facing, p["door"])
        out = (0, -1) if door_side == "north" else (0, 1)
    else:
        b.fill(dx, GROUND + 1, dz - 1, dx, GROUND + 3, dz + 1, p["frame"])
        b.door(dx, GROUND + 1, dz, facing, p["door"])
        out = (-1, 0) if door_side == "west" else (1, 0)
    # Lanterns hanging beside the door, a path out to the road and a hanging lantern inside.
    for side in (-1, 1):
        lx = dx + (side if door_side in ("north", "south") else 0) + out[0]
        lz = dz + (side if door_side in ("west", "east") else 0) + out[1]
        b.set(lx, GROUND + 1, lz, "spruce_fence")
        b.set(lx, GROUND + 2, lz, "lantern[hanging=false]")
    b.set(x0 + 1, GROUND + 1, z0 + 1, "barrel[facing=up]")
    b.set(x0 + 1, GROUND + 2, z0 + 1, "lantern[hanging=false]")
    if sign:
        # On the wall above the door, facing out.
        b.set(dx + out[0], GROUND + 3, dz + out[1], f"spruce_wall_sign[facing={door_side}]" + sign_text(sign))
    return dx + out[0], dz + out[1]


def sign_text(text):
    return '{front_text:{messages:["","%s","",""]},is_waxed:1b}' % text


def path_to_road(x, z, out_dir, length):
    for i in range(length):
        px, pz = x + out_dir[0] * i, z + out_dir[1] * i
        for w in (-1, 0, 1):
            wx, wz = (px + w, pz) if out_dir[0] == 0 else (px, pz + w)
            b.set(wx, GROUND, wz, pick({"dirt_path": 6, "coarse_dirt": 1, "gravel": 1}, wx, 0, wz))
            b.occupied.add((wx, wz))


HOUSE = {"base": "cobblestone", "floor": "spruce_planks", "frame": "spruce_log", "beam": "stripped_spruce_log",
         "wall": "calcite", "window": "glass_pane", "stairs": "dark_oak_stairs", "slab": "dark_oak_slab[type=bottom]",
         "gable": "spruce_planks", "door": "spruce"}
TAVERN = dict(HOUSE, wall="spruce_planks", frame="dark_oak_log", beam="stripped_dark_oak_log", stairs="spruce_stairs",
              slab="spruce_slab[type=bottom]", gable="dark_oak_planks", door="dark_oak")
BANK = {"base": "stone_bricks", "floor": "polished_andesite", "frame": "chiseled_stone_bricks", "beam": "gold_block",
        "wall": "stone_bricks", "window": "iron_bars", "stairs": "deepslate_tile_stairs", "slab": "deepslate_tile_slab[type=bottom]",
        "gable": "deepslate_tiles", "door": "dark_oak"}
SMITH = dict(HOUSE, base="cobblestone", wall="stone_bricks", frame="cobblestone", beam="polished_andesite",
             stairs="deepslate_tile_stairs", slab="deepslate_tile_slab[type=bottom]", gable="cobblestone", window="iron_bars")
CABIN = dict(HOUSE, wall="oak_log[axis=y]", frame="stripped_oak_log", beam="stripped_oak_log", stairs="spruce_stairs",
             slab="spruce_slab[type=bottom]", gable="spruce_planks", window="glass_pane", door="oak")
MAGE = dict(HOUSE, wall="smooth_quartz", frame="birch_log", beam="stripped_birch_log", stairs="purpur_stairs",
            slab="purpur_slab[type=bottom]", gable="purpur_block", window="purple_stained_glass_pane", door="birch")
BUILDER = dict(HOUSE, wall="bricks", frame="oak_log", beam="stripped_oak_log", stairs="stone_brick_stairs",
               slab="stone_brick_slab[type=bottom]", gable="oak_planks", door="oak")

# North road (x = -2..2)
bank_door = building(6, -40, 20, -26, "west", BANK, wall_h=6, sign="Bank")
path_to_road(bank_door[0], bank_door[1], (-1, 0), 4)
for z in range(-38, -27, 2):
    b.set(18, GROUND + 1, z, "gold_block")
    b.set(18, GROUND + 2, z, "barrel[facing=up]")
b.fill(14, GROUND + 1, -38, 14, GROUND + 2, -28, "iron_bars")
farmer_door = building(-16, -34, -6, -25, "east", HOUSE, sign="Farmer")
path_to_road(farmer_door[0], farmer_door[1], (1, 0), 4)
for z in (-33, -32, -26):
    b.set(-15, GROUND + 1, z, "barrel[facing=up]")
b.set(-15, GROUND + 1, -27, "composter")
b.set(-7, GROUND + 1, -33, "hay_block")

# Fields west of the farmer: rows of crops with water channels, fenced so nobody tramples them.
FX0, FZ0, FX1, FZ1 = -34, -38, -21, -22
b.claim(FX0, FZ0, FX1, FZ1, margin=1)
crops = ["wheat[age=7]", "carrots[age=7]", "potatoes[age=7]", "beetroots[age=3]"]
for x in range(FX0, FX1 + 1):
    for z in range(FZ0, FZ1 + 1):
        if x in (FX0, FX1) or z in (FZ0, FZ1):
            b.set(x, GROUND, z, "grass_block")
            b.set(x, GROUND + 1, z, "oak_fence")
        elif (z - FZ0) % 5 == 0:
            b.set(x, GROUND, z, "water")
        else:
            b.set(x, GROUND, z, "farmland[moisture=7]")
            b.set(x, GROUND + 1, z, crops[((z - FZ0) // 5) % len(crops)])
for x in (FX0, FX1):
    for z in (FZ0, FZ1):
        b.set(x, GROUND + 2, z, "lantern[hanging=false]")
# Scarecrow
b.set(-30, GROUND + 1, -21, "oak_fence")
b.set(-30, GROUND + 2, -21, "hay_block")
b.set(-30, GROUND + 3, -21, "carved_pumpkin[facing=south]")
b.claim(-31, -21, -29, -21)

# East road (z = -2..2)
smith_door = building(22, -17, 34, -7, "south", SMITH, sign="Blacksmith")
path_to_road(smith_door[0], smith_door[1], (0, 1), 4)
for x, block in [(24, "anvil[facing=north]"), (26, "blast_furnace[facing=north]"), (27, "furnace[facing=north]"),
                 (29, "smithing_table"), (31, "grindstone[face=floor,facing=north]"), (33, "water_cauldron[level=3]")]:
    b.set(x, GROUND + 1, -15, block)
b.fill(33, GROUND + 1, -16, 34, GROUND + 12, -16, "cobblestone")  # chimney
b.set(33, GROUND + 12, -16, "hay_block")
b.set(33, GROUND + 13, -16, "campfire[lit=true,signal_fire=true]")

# Market stalls south of the east road, facing it.
CANOPIES = [("red_wool", "white_wool"), ("blue_wool", "white_wool"), ("lime_wool", "yellow_wool"), ("orange_wool", "white_wool")]
for i, x0 in enumerate(range(18, 46, 7)):
    if not on_island(x0 + 4, 8, inset=3):
        continue
    x1, z0, z1 = x0 + 4, 5, 8
    b.claim(x0, z0, x1, z1, margin=1)
    for x in range(x0, x1 + 1):
        b.set(x, GROUND + 1, z0, "spruce_planks" if x in (x0, x1) else "barrel[facing=north]")
        for z in range(z0, z1 + 1):
            b.set(x, GROUND, z, "spruce_planks")
            b.set(x, GROUND + 4, z, CANOPIES[i % 4][x % 2])
    for x in (x0, x1):
        for z in (z0, z1):
            b.fill(x, GROUND + 1, z, x, GROUND + 3, z, "spruce_fence")
    b.set((x0 + x1) // 2, GROUND + 2, z0, "lantern[hanging=false]")

# South road (x = -2..2)
lumber_door = building(-16, 22, -6, 31, "east", CABIN, sign="Lumberjack")
path_to_road(lumber_door[0], lumber_door[1], (1, 0), 4)
for z in range(23, 31, 1):
    b.fill(-20, GROUND + 1, z, -18, GROUND + 1, z, "oak_log[axis=x]")
    if z % 2:
        b.fill(-20, GROUND + 2, z, -18, GROUND + 2, z, "spruce_log[axis=x]")
b.claim(-21, 22, -17, 31)
mage_door = building(6, 22, 16, 32, "west", MAGE, sign="Enchanter")
path_to_road(mage_door[0], mage_door[1], (-1, 0), 4)
b.set(11, GROUND + 1, 27, "enchanting_table")
for z in range(23, 32):
    b.set(15, GROUND + 1, z, "bookshelf")
    b.set(15, GROUND + 2, z, "bookshelf")

# Survival gate across the end of the south road.
GATE_Z = ROAD_END["south"] - 2
for gx in (-4, -3, 3, 4):
    b.fill(gx, GROUND + 1, GATE_Z, gx, GROUND + 6, GATE_Z + 1, "stone_bricks")
    b.set(gx, GROUND + 1, GATE_Z, "chiseled_stone_bricks")
b.fill(-4, GROUND + 7, GATE_Z, 4, GROUND + 7, GATE_Z + 1, "stone_bricks")
b.fill(-3, GROUND + 6, GATE_Z, 3, GROUND + 6, GATE_Z + 1, "stone_brick_slab[type=top]")
b.fill(-2, GROUND + 6, GATE_Z, 2, GROUND + 6, GATE_Z + 1, "amethyst_block")
b.fill(-5, GROUND + 8, GATE_Z, 5, GROUND + 8, GATE_Z + 1, "stone_brick_slab[type=bottom]")
b.fill(-2, GROUND, GATE_Z, 2, GROUND, GATE_Z + 1, "crying_obsidian")
for gx in (-4, 4):
    b.set(gx, GROUND + 8, GATE_Z, "stone_bricks")
    b.set(gx, GROUND + 9, GATE_Z, "lantern[hanging=false]")
b.set(0, GROUND + 7, GATE_Z - 1, "spruce_wall_sign[facing=north]" + sign_text("Survival World"))
b.claim(-5, GATE_Z, 5, GATE_Z + 1)

# West road (z = -2..2)
tavern_door = building(-38, -18, -22, -7, "south", TAVERN, wall_h=5, sign="Tavern")
path_to_road(tavern_door[0], tavern_door[1], (0, 1), 4)
for x in range(-36, -23, 3):
    b.set(x, GROUND + 1, -13, "spruce_fence")
    b.set(x, GROUND + 2, -13, "spruce_pressure_plate")
for x in range(-37, -23, 2):
    b.set(x, GROUND + 1, -17, "barrel[facing=south]")
builder_door = building(-34, 7, -22, 16, "north", BUILDER, sign="Builder")
path_to_road(builder_door[0], builder_door[1], (0, -1), 4)
b.set(-24, GROUND + 1, 15, "crafting_table")
b.set(-25, GROUND + 1, 15, "stonecutter[facing=north]")
b.set(-26, GROUND + 1, 15, "loom[facing=north]")

# A well on the south-east lawn.
WX, WZ = 30, 30
b.claim(WX - 3, WZ - 3, WX + 3, WZ + 3)
for x in range(WX - 2, WX + 3):
    for z in range(WZ - 2, WZ + 3):
        b.set(x, GROUND, z, pick({"cobblestone": 3, "mossy_cobblestone": 1}, x, 0, z))
        edge = abs(x - WX) == 2 or abs(z - WZ) == 2
        if edge:
            b.set(x, GROUND + 1, z, "cobblestone")
        else:
            b.fill(x, GROUND - 3, z, x, GROUND - 1, z, "air")
            b.set(x, GROUND - 4, z, "cobblestone")
            b.fill(x, GROUND - 3, z, x, GROUND, z, "water")
for x, z in ((WX - 2, WZ - 2), (WX + 2, WZ - 2), (WX - 2, WZ + 2), (WX + 2, WZ + 2)):
    b.fill(x, GROUND + 2, z, x, GROUND + 3, z, "spruce_fence")
gable_roof(WX - 2, WZ - 2, WX + 2, WZ + 2, GROUND + 4, "spruce_stairs", "spruce_slab[type=bottom]", "air", True)
b.set(WX, GROUND + 5, WZ - 1, "spruce_planks")
b.set(WX, GROUND + 4, WZ, "lantern[hanging=true]")

# A pond with lily pads and reeds on the south-west lawn.
PX, PZ = -32, 36
b.claim(PX - 6, PZ - 5, PX + 6, PZ + 5)
for x in range(PX - 6, PX + 7):
    for z in range(PZ - 5, PZ + 6):
        d = math.hypot((x - PX) / 6, (z - PZ) / 4.5)
        if d < 0.75:
            b.fill(x, GROUND - 2, z, x, GROUND, z, "water")
            b.set(x, GROUND - 3, z, pick({"clay": 2, "sand": 2, "gravel": 1}, x, 0, z))
            if rng.random() < 0.12:
                b.set(x, GROUND + 1, z, "lily_pad")
        elif d < 1.0:
            b.set(x, GROUND, z, "sand" if d < 0.88 else "grass_block")
            if d < 0.88 and rng.random() < 0.3:  # sugar cane needs water right next to it
                b.fill(x, GROUND + 1, z, x, GROUND + rng.randint(1, 3), z, "sugar_cane")

# ---------------------------------------------------------------- trees and flowers
def tree(x, z, log, leaves, height):
    b.fill(x, GROUND + 1, z, x, GROUND + height, z, log)
    for y in range(GROUND + height - 2, GROUND + height + 2):
        radius = 2 if y < GROUND + height else 1
        for dx in range(-radius, radius + 1):
            for dz in range(-radius, radius + 1):
                corner = abs(dx) == radius and abs(dz) == radius
                if (dx or dz or y > GROUND + height) and not (corner and rng.random() < 0.6):
                    if b.get(x + dx, y, z + dz) == "air":
                        b.set(x + dx, y, z + dz, f"{leaves}[persistent=true]")


trees = []
for _ in range(900):
    a, d = rng.uniform(0, math.tau), rng.uniform(19, 54)
    x, z = round(math.cos(a) * d), round(math.sin(a) * d)
    if not on_island(x, z, inset=4) or any((x + dx, z + dz) in b.occupied for dx in range(-3, 4) for dz in range(-3, 4)):
        continue
    if any(math.hypot(x - tx, z - tz) < 7 for tx, tz in trees):
        continue
    trees.append((x, z))
    birch = rng.random() < 0.3
    tree(x, z, "birch_log" if birch else "oak_log", "birch_leaves" if birch else "oak_leaves", rng.randint(4, 6))
    b.claim(x, z, x, z, margin=2)

FLOWERS = {"short_grass": 60, "poppy": 5, "dandelion": 5, "cornflower": 4, "oxeye_daisy": 4, "azure_bluet": 3, "fern": 6}
for x in range(-60, 61):
    for z in range(-60, 61):
        if b.get(x, GROUND, z) == "grass_block" and b.get(x, GROUND + 1, z) == "air" and (x, z) not in b.occupied:
            if rng.random() < 0.18:
                b.set(x, GROUND + 1, z, pick(FLOWERS, x, 2, z))

# ---------------------------------------------------------------- output
for name, (x0, z0, x1, z1) in {"bank": (6, -40, 20, -26), "farmer": (-16, -34, -6, -25), "fields": (FX0, FZ0, FX1, FZ1),
                               "smith": (22, -17, 34, -7), "lumber": (-16, 22, -6, 31), "mage": (6, 22, 16, 32),
                               "tavern": (-38, -18, -22, -7), "builder": (-34, 7, -22, 16)}.items():
    for cx, cz in ((x0, z0), (x0, z1), (x1, z0), (x1, z1)):
        if not on_island(cx, cz, inset=2):
            print(f"WARNING: {name} corner {cx},{cz} is too close to the island edge")
commands = b.commands()
OUT.parent.mkdir(parents=True, exist_ok=True)
OUT.write_text("\n".join(commands) + "\n", encoding="utf-8")
print(f"{len(b.blocks)} blocks -> {len(commands)} commands in {OUT}")
print(f"Road ends: {ROAD_END}; survival gate at z={GATE_Z}..{GATE_Z + 1}")
