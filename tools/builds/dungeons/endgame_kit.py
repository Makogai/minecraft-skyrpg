"""Shared helpers for the two end-game dungeons (ruined_citadel.py, catacombs.py), built on dungeon_kit.

What is in here (everything the two scripts have in common, so each stays a list of rooms):
  - Style / Pal / Room: a built room with shell, jigsaw doors, decor and markers (the same shape as the Dwarven Halls' Room;
    the floor layer is local y=0, doorways are 3x3 from y=1)
  - autoconnect: bars, panes, walls and fences get their connection states from their neighbours
  - Shaft / Split / descent_piece: rock-filled pieces with carved stairs lined in brick (stairwells and descents)
  - loot helpers (the old function/condition style that Pack.loot_table converts to 26.3's format)
  - offline checks: block ids, loot ids, mod items, walk test (every door and marker reachable, door markers opened),
    pack sizes, spine rules, and a Monte-Carlo of the jigsaw tree that prints the reach table (limit 110 blocks)
"""
import math
import random
import sys
import pathlib
import zipfile
from collections import Counter

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parents[1]))
from dungeon_kit import Pack, Piece  # noqa: E402,F401

AIR = "minecraft:air"
CHAIN = "minecraft:iron_chain"
BARS = "minecraft:iron_bars"


class Pal:
    def __init__(self, wall, floor, inlay, trim, cap, weather, light):
        self.wall, self.floor, self.inlay, self.trim, self.cap, self.weather, self.light = wall, floor, inlay, trim, cap, weather, light


class Style:
    """ns: piece path prefix; dp: door prefix; pals: floor -> Pal; side/tail: floor -> pool path; loot: chest table prefix."""

    def __init__(self, ns, dp, pals, side, tail, loot, dmg, sign="dark_oak", brazier=None):
        self.ns, self.dp, self.pals, self.side, self.tail, self.loot, self.dmg, self.sign = ns, dp, pals, side, tail, loot, dmg, sign
        self.brazier = brazier or "minecraft:soul_campfire[facing=north,lit=true]"
        d = dp
        self.doors = {"spine_in": (f"{d}_spine_in", f"{d}_spine_in", None, 0),
                      "spine_out": (f"{d}_spine_out", f"{d}_spine_in", "pool", 10),
                      "side_in": (f"{d}_side_in", f"{d}_side_in", None, 0),
                      "side_out": (f"{d}_side_out", f"{d}_side_in", "side", 0),
                      "tail_in": (f"{d}_tail_in", f"{d}_tail_in", None, 0),
                      "leaf_out": (f"{d}_leaf_out", f"{d}_tail_in", "tail", 0),
                      "tail_out": (f"{d}_tail_out", f"{d}_tail_in", "tail", 0)}
        self.exempt = {}      # piece path -> marker kinds allowed to be unreachable (hidden caches)


def total(mobs):
    return sum(m["count"] for m in mobs)


def mob(type_, count, level, name, elite=False, **equipment):
    m = {"type": type_, "count": count, "level": level, "elite": elite, "name": name}
    if equipment:
        m["equipment"] = dict(equipment)
    return m


def wchoice(rng, table):
    tw = sum(wt for _, wt in table)
    r = rng.uniform(0, tw)
    for b, wt in table:
        r -= wt
        if r <= 0:
            return b
    return table[-1][0]


# ---------------------------------------------------------------------------------------------------------------------
# Room
# ---------------------------------------------------------------------------------------------------------------------
class Room:
    def __init__(self, st, path, w, h, d, fl=1):
        self.st = st
        self.p = Piece(f"{st.ns}/{path}", w, h, d)
        self.w, self.h, self.d, self.fl = w, h, d, fl
        self.pal = st.pals[fl]
        self.rng = random.Random(path)
        self.keep = set()        # (x, z) columns decor must leave alone
        self.doors = []

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

    def shell(self, pilasters=True, inlay=True):
        p, w, h, d, pal = self.p, self.w, self.h, self.d, self.pal
        p.fill(0, 0, 0, w - 1, h - 1, d - 1, pal.wall)
        p.fill(1, 1, 1, w - 2, h - 2, d - 2, AIR)
        for x in range(w):
            for z in range(d):
                il = inlay and (x in (1, w - 2) and 1 <= z <= d - 2 or z in (1, d - 2) and 1 <= x <= w - 2)
                p.set(x, 0, z, pal.inlay if il else pal.floor)
        if pilasters:
            for z in range(4, d - 3, 5):
                for x in (0, w - 1):
                    for y in range(1, h - 1):
                        p.set(x, y, z, pal.trim)
                    p.set(x, 1, z, pal.cap)
                    p.set(x, h - 2, z, pal.cap)
        if h >= 8:
            for x in range(w):
                for z in (0, d - 1):
                    p.set(x, h - 3, z, pal.trim)
            for z in range(d):
                for x in (0, w - 1):
                    p.set(x, h - 3, z, pal.trim)

    def ribs(self, every=6, start=3):
        for z in range(start, self.d - 2, every):
            for x in range(1, self.w - 1):
                if self.get(x, self.h - 2, z) == AIR:
                    self.set(x, self.h - 2, z, self.pal.trim)

    def door(self, side, kind, pool=None, at=None, y=1):
        p, w, d, st = self.p, self.w, self.d, self.st
        name, target, ptype, prio = st.doors[kind]
        if ptype == "pool":
            pl = pool
        elif ptype == "side":
            pl = pool or st.side[self.fl]
        elif ptype == "tail":
            pl = pool or st.tail[self.fl]
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
        if side == "x":
            self.reserve(self.w // 2 - 1, 0, self.w // 2 + 1, self.d - 1)
        else:
            self.reserve(0, self.d // 2 - 1, self.w - 1, self.d // 2 + 1)

    def finish(self, pack, exempt=None):
        self.p.weather("a", {k: list(v) for k, v in self.pal.weather.items()})
        autoconnect(self.p)
        pack.add(self.p)
        if exempt:
            self.st.exempt[self.p.path] = tuple(exempt)
        return self.p

    # -- decor ---------------------------------------------------------------------------------------------------
    def col(self, x, z, y1=None, accent=None):
        top = (self.h - 2) if y1 is None else y1
        for y in range(1, top + 1):
            self.set(x, y, z, self.pal.trim)
        self.set(x, 1, z, self.pal.cap)
        self.set(x, top, z, self.pal.cap)
        if accent and top >= 5:
            self.set(x, 4, z, accent)
        self.keep.add((x, z))

    def hang(self, x, z, n=2):
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
        self.set(x, 1, z, "minecraft:polished_blackstone_brick_wall")
        self.set(x, 2, z, self.st.brazier)
        self.keep.add((x, z))

    def banner(self, x, y, z, facing, color="red"):
        back = {"east": (-1, 0), "west": (1, 0), "south": (0, -1), "north": (0, 1)}[facing]
        if self.get(x, y, z) == AIR and self.get(x + back[0], y, z + back[1]) not in (None, AIR):
            self.set(x, y, z, f"minecraft:{color}_wall_banner[facing={facing}]")

    def banners(self, zs, y=5, color="red"):
        for z in zs:
            self.banner(1, y, z, "east", color)
            self.banner(self.w - 2, y, z, "west", color)

    def chest(self, x, z, facing, tier="common", y=1, block="minecraft:chest"):
        self.p.chest(x, y, z, facing, f"skyrpg_world:chests/{self.st.loot}_{tier}", block=block, tier=tier)
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
        self.p.marker(x, y, z, "trap", type="arrows", radius=radius, every=every, damage=damage or self.st.dmg[self.fl])

    def spikes(self, cx, cz, r=1, y=1, damage=None):
        for x in range(cx - r, cx + r + 1):
            for z in range(cz - r, cz + r + 1):
                if (x + z) % 2 == (cx + cz) % 2 and self.free(x, z, y):
                    self.set(x, y, z, "minecraft:pointed_dripstone[thickness=tip,vertical_direction=up]")
                    self.keep.add((x, z))
        self.p.marker(cx, y, cz, "trap", type="spikes", radius=r + 2, every=35, damage=damage or self.st.dmg[self.fl] - 3)

    def flames(self, cx, cz, r=1, damage=None):
        for x in range(cx - r, cx + r + 1):
            for z in range(cz - r, cz + r + 1):
                if self.get(x, 0, z) not in (None, AIR, "minecraft:lava") and (x + z) % 2 == (cx + cz) % 2:
                    self.set(x, 0, z, "minecraft:magma_block")
        self.p.marker(cx, 1, cz, "trap", type="flames", radius=r + 2, every=70, damage=damage or self.st.dmg[self.fl] - 2)

    def poison(self, cx, cz, r=2, damage=None):
        self.p.marker(cx, 1, cz, "trap", type="poison", radius=r + 2, every=80, damage=damage or self.st.dmg[self.fl] - 4)

    def rubble(self, n, x1, z1, x2, z2, blocks=None):
        blocks = blocks or ["minecraft:cobblestone", "minecraft:gravel", "minecraft:cobblestone_slab[type=bottom]",
                            "minecraft:stone_brick_slab[type=bottom]", "minecraft:cracked_stone_bricks"]
        for _ in range(n):
            x, z = self.rng.randint(x1, x2), self.rng.randint(z1, z2)
            if self.free(x, z):
                self.set(x, 1, z, self.rng.choice(blocks))

    def sign(self, x, y, z, lines, facing):
        self.p.sign(x, y, z, lines, block=f"minecraft:{self.st.sign}_wall_sign[facing={facing}]", color="white", glow=True)

    def text(self, x, y, z, text, color="gold"):
        """A floating hint (a text display)."""
        self.p.entity(x, y, z, {"id": "minecraft:text_display", "text": {"text": text, "color": color},
                                "billboard": "center", "see_through": 0})

    def lever(self, x, y, z, facing, want, group):
        self.set(x, y, z, f"minecraft:lever[face=wall,facing={facing},powered=false]")
        self.p.marker(x, y, z, "lever", group=group, want=want)

    def plate(self, x, z, group, block="minecraft:stone_pressure_plate"):
        self.set(x, 1, z, block)
        self.p.marker(x, 1, z, "plate", group=group)
        self.keep.add((x, z))

    def plug(self, x1, x2, z, block, group, message, y1=1, y2=3):
        """A door plug across x1..x2 at row z (every block of the same type connected to the marker vanishes)."""
        for x in range(x1, x2 + 1):
            for y in range(y1, y2 + 1):
                self.set(x, y, z, block)
        self.p.marker((x1 + x2) // 2, y1 + 1, z, "door", group=group, message=message)

    def cache(self, x1, z1, x2, z2, chest_at, facing, wall, name):
        """A hidden pocket closed by `wall` (break in): a secret-tier chest and a 'secret' marker."""
        p = self.p
        for x in range(x1, x2 + 1):
            for z in range(z1, z2 + 1):
                edge = x in (x1, x2) or z in (z1, z2)
                for y in (1, 2, 3):
                    p.set(x, y, z, wall if edge else AIR)
        self.chest(chest_at[0], chest_at[1], facing, "secret")
        p.marker((x1 + x2) // 2, 1, (z1 + z2) // 2, "secret", name=name)
        self.reserve(x1, z1, x2, z2)
        self.st.exempt[p.path] = tuple(self.st.exempt.get(p.path, ())) + ("secret",)


# ---------------------------------------------------------------------------------------------------------------------
# Connecting blocks
# ---------------------------------------------------------------------------------------------------------------------
NONSOLID = {AIR, "minecraft:lantern", "minecraft:soul_lantern", "minecraft:torch", "minecraft:wall_torch", "minecraft:soul_torch",
            "minecraft:soul_campfire", "minecraft:campfire", "minecraft:cobweb", "minecraft:iron_chain", "minecraft:pointed_dripstone",
            "minecraft:ladder", "minecraft:vine", "minecraft:lever", "minecraft:stone_pressure_plate", "minecraft:candle"}


def _is_thin(i):
    return i.endswith("_bars") or i.endswith("glass_pane")


def _is_wall(i):
    return i.endswith("_wall")


def _solid(i):
    return i is not None and i not in NONSOLID and not i.endswith(("_carpet", "_sign", "_button", "_plate", "_bed", "_banner", "_skull",
                                                                     "_slab", "_stairs", "lantern", "_candle", "_head"))


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


def cap_piece(pack, st, kind, fl):
    """A 3x4x1 plug that seals a doorway nobody joined."""
    wall = st.pals[fl].wall
    p = Piece(f"{st.ns}/caps/{kind}{fl}", 3, 4, 1)
    p.fill(0, 0, 0, 2, 3, 0, wall)
    p.set(1, 2, 0, st.pals[fl].trim)
    p.jigsaw(1, 1, 0, "north_up", f"{st.dp}_{kind}_in", f"{st.dp}_{kind}_in", "minecraft:empty", final_state=wall)
    pack.add(p)
    return p


# ---------------------------------------------------------------------------------------------------------------------
# Rock-filled pieces: stairwells and descents
# ---------------------------------------------------------------------------------------------------------------------
OPP = {"north": "south", "south": "north", "west": "east", "east": "west"}
DIRV = {(1, 0): "east", (-1, 0): "west", (0, 1): "south", (0, -1): "north"}


class Theme:
    def __init__(self, rock_top, rock_mid, rock_deep, lining, stairs, landing, rune=None, light="minecraft:lantern[hanging=true]"):
        self.rock_top, self.rock_mid, self.rock_deep, self.lining, self.stairs = rock_top, rock_mid, rock_deep, lining, stairs
        self.landing, self.rune, self.light = landing, rune, light
        self.rock_ids = {b for t in (rock_top, rock_mid, rock_deep) for b, _ in t}


class Split:
    """Routes blocks in global coordinates (y >= 0 upper piece, y < 0 lower piece)."""

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


class Shaft:
    def __init__(self, room, theme, p=None, deep=False):
        self.r, self.t = room, theme
        self.p = p or room.p
        self.rng = room.rng
        self.air = set()
        self.w, self.h, self.d = room.w, room.h, room.d
        self.deep = deep

    def fill_rock(self, y_top):
        for x in range(self.w):
            for z in range(self.d):
                for y in range(0, y_top + 1):
                    depth = y_top - y
                    t = self.t
                    table = t.rock_deep if self.deep else t.rock_top if depth < 8 else t.rock_mid if depth < 22 else t.rock_deep
                    self.r.p.set(x, y, z, wchoice(self.rng, table))

    def carve(self, x, y, z):
        self.p.set(x, y, z, AIR)
        self.air.add((x, y, z))

    def landing(self, x1, z1, x2, z2, y, rune=None):
        for x in range(x1, x2 + 1):
            for z in range(z1, z2 + 1):
                self.p.set(x, y, z, self.t.landing[0] if (x + z) % 3 else self.t.landing[1])
                for yy in range(y + 1, y + 5):
                    self.carve(x, yy, z)
        if rune and self.t.rune:
            self.p.set(rune[0], y, rune[1], self.t.rune)

    def run(self, x, z, dx, dz, n, y_start):
        uphill = OPP[DIRV[(dx, dz)]]
        cells = []
        for i in range(n):
            cx, cz, yf = x + dx * i, z + dz * i, y_start - i
            lateral = [(cx, cz + o) for o in (-1, 0, 1)] if dx else [(cx + o, cz) for o in (-1, 0, 1)]
            for lx, lz in lateral:
                block = wchoice(self.rng, self.t.stairs)
                self.p.set(lx, yf, lz, f"{block}[facing={uphill},half=bottom]")
                for yy in range(yf + 1, yf + 5):
                    self.carve(lx, yy, lz)
            cells.append((cx, cz, yf))
        return cells

    def helix(self, waypoints, y0, hang_every=5):
        y = y0
        ax, az = waypoints[0]
        landings = [(ax, az, y)]
        legs = []
        self.landing(ax - 1, az - 1, ax + 1, az + 1, y)
        for bx, bz in waypoints[1:]:
            dx = (bx > ax) - (bx < ax)
            dz = (bz > az) - (bz < az)
            n = abs(bx - ax) + abs(bz - az) - 3
            cells = self.run(ax + dx * 2, az + dz * 2, dx, dz, n, y - 1)
            legs.append(((dx, dz), cells))
            for i, (cx, cz, yf) in enumerate(cells):
                if i % hang_every == 2:
                    self.p.set(cx, yf + 4, cz, self.t.light)
            y -= n + 1
            self.landing(bx - 1, bz - 1, bx + 1, bz + 1, y)
            landings.append((bx, bz, y))
            ax, az = bx, bz
        return landings, legs

    def line(self):
        todo = set()
        for (x, y, z) in self.air:
            for dx, dy, dz in ((1, 0, 0), (-1, 0, 0), (0, 1, 0), (0, -1, 0), (0, 0, 1), (0, 0, -1)):
                n = (x + dx, y + dy, z + dz)
                if n not in self.air and self.p.inside(*n) and self.p.get(*n) in self.t.rock_ids:
                    todo.add(n)
        for pos in sorted(todo):
            self.p.set(*pos, wchoice(self.rng, self.t.lining))


def descent_piece(pack, st, theme, fl, name, drop, out_pool, deep=False):
    """A 19 x (drop+8) x 19 switchback stairwell in solid rock: enters on the south wall (x=15) at y=drop+1, leaves on the
    east wall (z=15) at y=1 (a 90 degree turn). drop must be even and <= 20. Returns (room, shaft, flights)."""
    W = D = 19
    H = drop + 8
    r = Room(st, name, W, H, D, fl)
    p = r.p
    n = drop // 2 - 1
    assert drop % 2 == 0 and 2 * n + 2 == drop and n <= 9
    sh = Shaft(r, theme, deep=deep)
    sh.fill_rock(H - 1)
    y0 = drop
    for x in (14, 15, 16):
        for z in range(11, 18):
            p.set(x, y0, z, theme.landing[0])
            for y in range(y0 + 1, y0 + 5):
                sh.carve(x, y, z)
    sh.landing(14, 8, 16, 10, y0)
    cells1 = sh.run(13, 9, -1, 0, n, y0 - 1)
    lx = 13 - n
    sh.landing(lx - 2, 8, lx, 16, y0 - n - 1)
    cells2 = sh.run(lx + 1, 15, 1, 0, n, y0 - n - 2)
    sh.landing(14, 14, 16, 16, 0, rune=(15, 15))
    for x in (17, 18):
        for z in (14, 15, 16):
            p.set(x, 0, z, theme.landing[0])
            for y in range(1, 5):
                sh.carve(x, y, z)
    for cells in (cells1, cells2):
        for i, (cx, cz, yf) in enumerate(cells):
            if i % 4 == 2:
                p.set(cx, yf + 4, cz, theme.light)
    sh.line()
    r.door("south", "spine_in", at=15, y=drop + 1)
    r.door("east", "spine_out", pool=out_pool, at=15, y=1)
    return r, sh, (cells1, cells2, y0 - n - 1)


# ---------------------------------------------------------------------------------------------------------------------
# Loot helpers (old function/condition style; Pack.loot_table converts to 26.3's modifier/condition format)
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


# ---------------------------------------------------------------------------------------------------------------------
# Offline checks
# ---------------------------------------------------------------------------------------------------------------------
CLIENT_JAR = pathlib.Path.home() / ".gradle/caches/fabric-loom/26.3/minecraft-client.jar"
MOD_BLOCKS = {"skyrpg_world:waystone"}
PASS_SUFFIX = ("_carpet", "_sign", "_button", "_plate", "rail", "_torch", "_lantern", "_bed", "_banner", "_skull", "_candle", "_head")
PASS_IDS = {AIR, "minecraft:cobweb", "minecraft:lever", "minecraft:iron_chain", "minecraft:torch", "minecraft:soul_torch",
            "minecraft:pointed_dripstone", "minecraft:lantern", "minecraft:soul_lantern", "minecraft:ladder", "minecraft:soul_fire",
            "minecraft:fire", "minecraft:wall_torch", "minecraft:candle", "minecraft:short_grass", "minecraft:fern", "minecraft:dead_bush"}


def passable(i):
    return i in PASS_IDS or i.endswith(PASS_SUFFIX)


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


def _loot_walk(pack, fn):
    def walk(node):
        if isinstance(node, dict):
            fn(node)
            for v in node.values():
                walk(v)
        elif isinstance(node, list):
            for v in node:
                walk(v)
    for path, data in pack.files.items():
        if "loot_table" in path:
            walk(data)


def check_items(pack):
    items = vanilla_ids("item")
    if items is None:
        return 0
    bad = set()

    def f(node):
        n = str(node.get("name", ""))
        if node.get("type") == "minecraft:item" and n.startswith("minecraft:") and n not in items:
            bad.add(n)
    _loot_walk(pack, f)
    for i in sorted(bad):
        print("UNKNOWN ITEM", i)
    return len(bad)


def check_mod_items(pack):
    """Mod item ids in the loot tables that no Java source mentions (listed, not fatal: some are being added)."""
    root = pathlib.Path(__file__).resolve().parents[3]
    names = set()

    def f(node):
        n = str(node.get("name", ""))
        if node.get("type") == "minecraft:item" and n.startswith("skyrpg_"):
            names.add(n)
    _loot_walk(pack, f)
    text = ""
    for sub in ("world", "mining", "combat", "items", "pets", "enchants"):
        for fl in (root / sub / "src").rglob("*.java"):
            try:
                text += fl.read_text(encoding="utf-8", errors="ignore")
            except OSError:
                pass
    missing = sorted(n for n in names if f'"{n.split(":")[1]}"' not in text)
    if missing:
        print("mod items not registered in Java yet (the lead adds them):", ", ".join(missing))
    return missing


def walk_check(pack, st, skip_prefix, liquid="minecraft:lava"):
    """Every doorway and every marker (bosses, spawners, chests, plates, levers) must be reachable from the first doorway.
    'door'/'key_door' markers count as open; hidden caches (secret chests) are exempt."""
    problems = []
    for pc in pack.pieces.values():
        if pc.path.startswith(skip_prefix):
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
            return b is not None and not passable(b) and b != liquid

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
        exempt = st.exempt.get(pc.path, ())
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
    """Lava must never touch an unset cell or the piece boundary."""
    problems = []
    for pc in pack.pieces.values():
        for (x, y, z), (entry, _) in pc.cells.items():
            if entry["id"] != "minecraft:lava":
                continue
            for dx, dy, dz in ((1, 0, 0), (-1, 0, 0), (0, 0, 1), (0, 0, -1), (0, -1, 0), (0, 1, 0)):
                c = pc.cells.get((x + dx, y + dy, z + dz))
                if c is None or c[0]["id"] == AIR:
                    problems.append(f"{pc.path}: lava at {(x, y, z)} is not enclosed")
                    break
    return problems


def check_spine_rules(pack, st):
    """Spine pools hold no corner pieces: every piece in a spine pool has a south 'in' and a north 'out' jigsaw, and no east/west spine door."""
    problems = []
    for pool_id, pool in pack.pools.items():
        nm = pool_id.split(":")[1]
        if not nm.startswith(f"{st.ns}/s") or not nm[len(st.ns) + 2:].isdigit() and "_s" not in nm:
            continue
        for e in pool["elements"]:
            el = e["element"]
            if el["element_type"] != "minecraft:single_pool_element":
                continue
            pc = pack.pieces[el["location"].split(":", 1)[1]]
            spine = {(j[1].split("_")[0]) for j in pc.jigsaws if j[2].startswith(f"skyrpg_world:{st.dp}_spine")}
            if not {"north", "south"} <= spine:
                problems.append(f"{pc.path}: spine piece without a straight north/south pair")
            if "east" in spine or "west" in spine:
                problems.append(f"{pc.path}: a corner piece in a spine pool")
    return problems


# -- Monte-Carlo of the jigsaw tree: pieces per group and the real reach (rotation-exact, collisions ignored) ----------
_ORDER = ["north", "east", "south", "west"]
_VEC = {"north": (0, -1), "east": (1, 0), "south": (0, 1), "west": (-1, 0)}


def _rv(x, z, k):
    for _ in range(k % 4):
        x, z = -z, x
    return x, z


def simulate(pack, start_path, size=16, trials=600, seed=7, group=lambda path: "all"):
    rng = random.Random(seed)
    pools = pack.pools

    def pick(pool_id, depth):
        pool = pools[pool_id]
        els = [e for e in pool["elements"] if e["element"]["element_type"] == "minecraft:single_pool_element"]
        if depth >= size or not els:
            fb = pool["fallback"]
            if fb == "minecraft:empty":
                return None
            return pick(fb, 0)
        return rng.choices(els, [e["weight"] for e in els])[0]["element"]["location"].split(":", 1)[1]

    counts, capcounts = Counter(), Counter()
    reach = {"north": (0, ""), "south": (0, ""), "east": (0, ""), "west": (0, ""), "down": (0, ""), "up": (0, "")}
    per_piece = {}
    for _ in range(trials):
        sp = pack.pieces[start_path]
        w, h, d = sp.size
        queue = [(start_path, 0, (-(w // 2), -1, -(d // 2)), 0, 0)]
        while queue:
            path, depth, o, k, _par = queue.pop()
            pc = pack.pieces[path]
            (capcounts if "/caps/" in path else counts)[group(path)] += 1
            w, h, d = pc.size
            xs = [o[0] + _rv(cx, cz, k)[0] for cx in (0, w - 1) for cz in (0, d - 1)]
            zs = [o[0 + 2] + _rv(cx, cz, k)[1] for cx in (0, w - 1) for cz in (0, d - 1)]
            for key, val in (("east", max(xs) + 1), ("west", -min(xs)), ("south", max(zs) + 1), ("north", -min(zs)),
                             ("up", o[1] + h - 1), ("down", -o[1])):
                if val > reach[key][0]:
                    reach[key] = (val, path)
                per_piece[path] = max(per_piece.get(path, 0), val if key in ("east", "west", "north", "south") else 0)
            for (pos, orientation, name, target, pool) in pc.jigsaws:
                if pool == "minecraft:empty":
                    continue
                front = orientation.split("_")[0]
                child_path = pick(pool, depth + 1)
                if child_path is None:
                    continue
                child = pack.pieces[child_path]
                cand = [j for j in child.jigsaws if j[2] == target]
                if not cand:
                    continue
                cj = rng.choice(cand)
                cfront = cj[1].split("_")[0]
                px, py, pz = pos
                rx, rz = _rv(px, pz, k)
                wx, wy, wz = o[0] + rx, o[1] + py, o[2] + rz
                if front in ("up", "down"):
                    kc = k
                    cx_, cy_, cz_ = wx, wy + (1 if front == "up" else -1), wz
                else:
                    F = _ORDER[(_ORDER.index(front) + k) % 4]
                    kc = (_ORDER.index(OPP[F]) - _ORDER.index(cfront)) % 4
                    cx_, cy_, cz_ = wx + _VEC[F][0], wy, wz + _VEC[F][1]
                lx, ly, lz = cj[0]
                crx, crz = _rv(lx, lz, kc)
                queue.append((child_path, depth + 1, (cx_ - crx, cy_ - ly, cz_ - crz), kc, 0))
    return ({g: v / trials for g, v in counts.items()}, {g: v / trials for g, v in capcounts.items()}, reach, per_piece)


def reach_report(pack, start_path, group, limit=110):
    counts, caps, reach, per_piece = simulate(pack, start_path, group=group)
    print("reach table (Monte-Carlo of the jigsaw tree, rotation-exact, blocks from the start piece centre; limit 110, hard 116):")
    for key in ("north", "south", "east", "west", "down", "up"):
        v, path = reach[key]
        print(f"  {key:5s} {v:6.1f}   (by {path})")
    worst = max(reach[k][0] for k in ("north", "south", "east", "west"))
    top = sorted(per_piece.items(), key=lambda kv: -kv[1])[:6]
    print("  farthest pieces:", ", ".join(f"{p.split('/', 1)[1]}={v}" for p, v in top))
    print("expected pieces per group, without caps:", {k: round(v, 1) for k, v in sorted(counts.items())},
          "caps:", {k: round(v, 1) for k, v in sorted(caps.items())},
          "total ~", round(sum(counts.values()) + sum(caps.values()), 1))
    return worst, counts, caps


def report(pack):
    kinds = Counter()
    for pc in pack.pieces.values():
        for e in pc.entities:
            if "data" in e["nbt"]:
                kinds[e["nbt"]["data"]["skyrpg"]] += 1
    print(f"pieces ({len(pack.pieces)}):")
    for path, pc in sorted(pack.pieces.items()):
        c = Counter(e["nbt"]["data"]["skyrpg"] for e in pc.entities if "data" in e["nbt"])
        print(f"  {path:46s} {pc.size[0]:2d}x{pc.size[1]:2d}x{pc.size[2]:2d}  " + " ".join(f"{k}={v}" for k, v in sorted(c.items())))
    print("marker counts:", dict(kinds))


def show(pack, st, path, y):
    pc = pack.pieces[f"{st.ns}/{path}"]
    w, h, d = pc.size
    for z in range(d):
        row = ""
        for x in range(w):
            c = pc.cells.get((x, y, z))
            if c is None:
                row += "?"
            else:
                i = c[0]["id"]
                row += " " if i == AIR else "." if passable(i) else ("c" if "chest" in i else "X")
        print(f"{z:2d} {row}")


def run_main(build, st, skip_prefix, start_path, group, liquid="minecraft:lava"):
    pack = Pack()
    build(pack)
    if len(sys.argv) > 3 and sys.argv[1] == "--show":
        show(pack, st, sys.argv[2], int(sys.argv[3]))
        sys.exit(0)
    problems = walk_check(pack, st, skip_prefix, liquid) + check_packs(pack) + check_lava(pack) + check_spine_rules(pack, st)
    n_bad = check_blocks(pack) + check_items(pack)
    check_mod_items(pack)
    for pr in problems:
        print("PROBLEM:", pr)
    report(pack)
    worst, _, _ = reach_report(pack, start_path, group)
    if worst > 110:
        problems.append(f"reach {worst} exceeds 110")
        print("PROBLEM: reach exceeds 110")
    if problems or n_bad:
        raise SystemExit("offline checks failed")
    pack.save()
    print("offline checks passed; wrote the files")
