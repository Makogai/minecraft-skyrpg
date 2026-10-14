"""Spider Hollow v2 (skyrpg_world:spider_hollow): a two-floor spider nest under dark forests. Mob levels 19-26.

Layout (jigsaw, size 16, max_distance 116). The spine is straight; the only turn is inside the shaft piece:

  FLOOR 1 "Web Tunnels" (Lv 19-23), running along axis A from the entrance:
    entrance (sinkhole, waystone) -> f1_s1 -> f1_s2 -> f1_s3 (spine halls, 21 deep) -> champion nest (door plug) ->
    shaft (descends DROP blocks in a spiral; exit is on its WEST wall, because the 116-block radius cannot fit two
    floors end to end)
  FLOOR 2 "Brood Depths" (Lv 22-26), perpendicular to floor 1, under it:
    f2_s1 -> f2_s2 -> f2_s3 (spine halls) -> Queen's Lair (31x16x31)
  Every hall has side doors -> fN_chambers pool -> (chamber tail door) -> fN_tails pool. Everything falls back to `caps`.

Doorway convention: floor y=0, jigsaw at y=1 in the middle of the wall, 3x3 opening. Rooms that sit lower than the
door (silk bridges, lake, shaft) use door(y=...) so the template sits below the connecting tunnel's floor.
Entering doors are named hollow_in; outgoing doors are hollow_out and target hollow_in.
"""
import sys, pathlib, math, random
sys.path.insert(0, str(pathlib.Path(__file__).resolve().parents[1]))
from dungeon_kit import Pack, Piece

AIR = "minecraft:air"
DUN = "skyrpg_world:spider_hollow"
P = "spider_hollow"
K = 15          # entrance depth
DROP = 14       # shaft descent
PK = None       # the Pack being built
PROBLEMS = []

ROCK1 = [("minecraft:stone", 38), ("minecraft:tuff", 24), ("minecraft:deepslate", 14),
         ("minecraft:cobbled_deepslate", 8), ("minecraft:moss_block", 5), ("minecraft:calcite", 3),
         ("minecraft:dripstone_block", 4), ("minecraft:andesite", 4)]
ROCK2 = [("minecraft:deepslate", 34), ("minecraft:cobbled_deepslate", 22), ("minecraft:tuff", 10),
         ("minecraft:blackstone", 8), ("minecraft:smooth_basalt", 6), ("minecraft:basalt", 4),
         ("minecraft:calcite", 2), ("minecraft:sculk", 5)]
ROCK_IDS = {b for b, _ in ROCK1 + ROCK2} | {"minecraft:coarse_dirt", "minecraft:rooted_dirt", "minecraft:dirt",
                                            "minecraft:podzol"}
D6 = {"north": (0, 0, -1), "south": (0, 0, 1), "west": (-1, 0, 0), "east": (1, 0, 0),
      "up": (0, 1, 0), "down": (0, -1, 0)}
OPP = {"north": "south", "south": "north", "west": "east", "east": "west", "up": "down", "down": "up"}
HORIZ = ("north", "south", "west", "east")
PASS = {"minecraft:air", "minecraft:cobweb", "minecraft:hanging_roots", "minecraft:jigsaw", "minecraft:water",
        "minecraft:pointed_dripstone", "minecraft:red_mushroom", "minecraft:brown_mushroom", "minecraft:sculk_vein",
        "minecraft:skeleton_skull", "minecraft:turtle_egg", "minecraft:lantern", "minecraft:soul_lantern",
        "minecraft:glow_lichen", "minecraft:vine", "minecraft:oak_boat"}


def passable(i):
    return i in PASS or i.endswith("_carpet") or i.endswith("rail") or "vine" in i or "bud" in i or "cluster" in i


class Theme:
    def __init__(self, n, name, lo, rock, floor, carpet, lights, accent, ores):
        self.n, self.name, self.lo, self.rock, self.floor = n, name, lo, rock, floor
        self.carpet, self.lights, self.accent, self.ores = carpet, lights, accent, ores


T1 = Theme(1, "Web Tunnels", 19, ROCK1, [("minecraft:moss_block", 1)], "minecraft:moss_carpet",
           [("minecraft:shroomlight", 1)],
           [("minecraft:mossy_stone_bricks", 3), ("minecraft:cracked_stone_bricks", 1)],
           [("minecraft:iron_ore", 30), ("minecraft:coal_ore", 25), ("minecraft:copper_ore", 20),
            ("minecraft:gold_ore", 10), ("minecraft:redstone_ore", 8), ("minecraft:lapis_ore", 6),
            ("minecraft:diamond_ore", 3), ("minecraft:emerald_ore", 2)])
T2 = Theme(2, "Brood Depths", 22, ROCK2,
           [("minecraft:sculk", 5), ("minecraft:cobbled_deepslate", 3), ("minecraft:moss_block", 1)],
           "minecraft:gray_carpet",
           [("minecraft:shroomlight", 3), ("minecraft:ochre_froglight", 2)],
           [("minecraft:crying_obsidian", 4), ("minecraft:sculk", 2), ("minecraft:polished_blackstone", 2)],
           [("minecraft:deepslate_iron_ore", 30), ("minecraft:deepslate_coal_ore", 20),
            ("minecraft:deepslate_copper_ore", 20), ("minecraft:deepslate_gold_ore", 12),
            ("minecraft:deepslate_redstone_ore", 8), ("minecraft:deepslate_lapis_ore", 6),
            ("minecraft:deepslate_diamond_ore", 4), ("minecraft:deepslate_emerald_ore", 3)])
CUR = T1


def use(t):
    global CUR
    CUR = t


def L(d):
    return CUR.lo + d


def wchoice(rng, table):
    total = sum(w for _, w in table)
    r = rng.uniform(0, total)
    for b, w in table:
        r -= w
        if r <= 0:
            return b
    return table[-1][0]


class Noise:
    def __init__(self, rng):
        self.t = [(rng.uniform(.25, .8), rng.uniform(.25, .8), rng.uniform(.25, .8), rng.uniform(0, 6.28))
                  for _ in range(4)]

    def __call__(self, x, y, z):
        return sum(math.sin(a * x + b * y + c * z + p) for a, b, c, p in self.t) / 4


class Cave:
    """A piece that starts as solid rock; carve organic air, build features, decorate, then add doors."""

    def __init__(self, path, w, h, d, seed, fill=True, floor=0):
        self.p = Piece(path, w, h, d)
        self.w, self.h, self.d = w, h, d
        self.rng = random.Random(seed)
        self.noise = Noise(self.rng)
        self.T = CUR
        self.f1 = floor + 1          # standing level of the main floor
        self.ymin = floor + 1        # ordinary carving never goes below this
        self.fill = fill
        self.air, self.reserved, self.used, self.nocarve = set(), set(), set(), set()
        self.doors, self.dpos = [], []
        if fill:
            for x in range(w):
                for y in range(h):
                    for z in range(d):
                        self.p.set(x, y, z, wchoice(self.rng, self.T.rock))

    # -- carving ------------------------------------------------------------------------------------------
    def carve(self, x, y, z, force=False):
        if not self.p.inside(x, y, z) or y < 1:
            return
        if not force:
            if (x, y, z) in self.nocarve or y < self.ymin:
                return
            if not (1 <= x <= self.w - 2 and 1 <= y <= self.h - 2 and 1 <= z <= self.d - 2):
                return
        self.p.set(x, y, z, AIR)
        self.air.add((x, y, z))

    def blob(self, cx, cy, cz, rx, ry, rz, wob=0.3):
        for x in range(int(cx - rx - 2), int(cx + rx + 3)):
            for y in range(max(self.ymin, int(cy - ry - 2)), int(cy + ry + 3)):
                for z in range(int(cz - rz - 2), int(cz + rz + 3)):
                    v = ((x - cx) / rx) ** 2 + ((y - cy) / ry) ** 2 + ((z - cz) / rz) ** 2
                    if v < 1 + wob * self.noise(x, y, z) * 1.6 - 0.1 * wob:
                        self.carve(x, y, z)

    def lobes(self, cx, cz, rx, rz, ry, n, lr=(3, 4.5)):
        for _ in range(n):
            a = self.rng.uniform(0, 6.283)
            k = self.rng.uniform(.5, .8)
            self.blob(cx + math.cos(a) * rx * k, self.f1, cz + math.sin(a) * rz * k,
                      self.rng.uniform(*lr), self.rng.uniform(ry * .55, ry * .85), self.rng.uniform(*lr))

    def corridor(self, side, length, at=None, half=1, tall=3, y0=1):
        w, d = self.w, self.d
        if side in ("north", "south"):
            x0 = w // 2 if at is None else at
            z0 = 0 if side == "north" else d - 1
            dz = 1 if side == "north" else -1
            for i in range(length):
                for x in range(x0 - half, x0 + half + 1):
                    for y in range(y0, y0 + tall):
                        self.carve(x, y, z0 + dz * i, force=True)
                        if i < 4:
                            self.reserved.add((x, y, z0 + dz * i))
        else:
            z0 = d // 2 if at is None else at
            x0 = 0 if side == "west" else w - 1
            dx = 1 if side == "west" else -1
            for i in range(length):
                for z in range(z0 - half, z0 + half + 1):
                    for y in range(y0, y0 + tall):
                        self.carve(x0 + dx * i, y, z, force=True)
                        if i < 4:
                            self.reserved.add((x0 + dx * i, y, z))

    def door(self, side, name, target=None, pool="minecraft:empty", priority=0, length=None, y=1, at=None):
        if length is None:
            length = self.d // 2 if side in ("north", "south") else self.w // 2
        self.corridor(side, length, at=at, y0=y)
        self.doors.append((side, name, target, pool, priority, y, at))

    def reserve_lanes(self, h=4):
        cx, cz = self.w // 2, self.d // 2
        for side, _n, _t, _p, _pr, y, at in self.doors:
            if side in ("north", "south"):
                x0 = cx if at is None else at
                for x in range(x0 - 1, x0 + 2):
                    for z in range(self.d):
                        for yy in range(y, y + h):
                            self.reserved.add((x, yy, z))
            else:
                z0 = cz if at is None else at
                for z in range(z0 - 1, z0 + 2):
                    for x in range(self.w):
                        for yy in range(y, y + h):
                            self.reserved.add((x, yy, z))

    def tunnel_to_center(self, side, r=(1.6, 2.4)):
        w, d = self.w, self.d
        cx, cz = w // 2, d // 2
        if side in ("north", "south"):
            z0 = 0 if side == "north" else d - 1
            for i in range(2, abs(cz - z0), 2):
                z = z0 + (i if side == "north" else -i)
                self.blob(cx + self.rng.uniform(-1, 1), 2, z, self.rng.uniform(*r), self.rng.uniform(1.7, 3),
                          self.rng.uniform(1.5, 2.5))
        else:
            x0 = 0 if side == "west" else w - 1
            for i in range(2, abs(cx - x0), 2):
                x = x0 + (i if side == "west" else -i)
                self.blob(x, 2, cz + self.rng.uniform(-1, 1), self.rng.uniform(1.5, 2.5), self.rng.uniform(1.7, 3),
                          self.rng.uniform(*r))

    # -- helpers ------------------------------------------------------------------------------------------
    def solid(self, x, y, z):
        return self.p.inside(x, y, z) and (x, y, z) not in self.air

    def solidify(self, x, y, z, block, force=False):
        pos = (x, y, z)
        if not self.p.inside(x, y, z) or y < 1:
            return False
        if not force and (pos in self.reserved or pos in self.used):
            return False
        self.p.set(x, y, z, block)
        self.air.discard(pos)
        return True

    def place(self, x, y, z, block, force=False):
        pos = (x, y, z)
        if pos not in self.air:
            return False
        if not force and (pos in self.reserved or pos in self.used):
            return False
        self.p.set(x, y, z, block)
        self.used.add(pos)
        return True

    def free(self, pos):
        return pos in self.air and pos not in self.reserved and pos not in self.used

    def zone(self, x, z, r=2, y=None, h=3):
        y = self.f1 if y is None else y
        for xx in range(x - r, x + r + 1):
            for zz in range(z - r, z + r + 1):
                for yy in range(y, y + h):
                    self.reserved.add((xx, yy, zz))
                    self.carve(xx, yy, zz)

    def spot(self, near=None, avoid=()):
        """a floor cell against a wall with open space in front: for a chest. Reserves it."""
        f = self.f1
        cands = []
        for (x, y, z) in sorted(self.air):
            if y != f or not self.free((x, y, z)) or not self.free((x, y + 1, z)):
                continue
            for fc in HORIZ:
                dx, _, dz = D6[fc]
                front = (x + dx, f, z + dz)
                back = (x - dx, f, z - dz)
                if self.free(front) and self.free((front[0], f + 1, front[2])) and self.solid(*back) and \
                        all(abs(x - ax) + abs(z - az) > 3 for ax, az in avoid):
                    cands.append((x, y, z, fc))
        if not cands:
            raise SystemExit(f"{self.p.path}: no chest spot")
        if near:
            cands.sort(key=lambda c: abs(c[0] - near[0]) + abs(c[2] - near[1]) + self.rng.uniform(0, 3))
            pick = cands[0]
        else:
            pick = self.rng.choice(cands)
        x, y, z, fc = pick
        dx, _, dz = D6[fc]
        for pos in ((x, f, z), (x, f + 1, z), (x + dx, f, z + dz), (x + dx, f + 1, z + dz)):
            self.reserved.add(pos)
        return pick

    def mounds(self, n):
        cols = {(x, z) for (x, y, z) in self.reserved}
        f = self.f1
        for _ in range(n):
            cells = [c for c in sorted(self.air) if c[1] == f and c not in self.reserved]
            if not cells:
                return
            x, _, z = self.rng.choice(cells)
            r = self.rng.choice((1, 1, 2))
            if any(abs(x - cx) <= r + 2 and abs(z - cz) <= r + 2 for cx, cz in cols):
                continue
            for xx in range(x - r, x + r + 1):
                for zz in range(z - r, z + r + 1):
                    dd = math.hypot(xx - x, zz - z)
                    if dd <= r + .3 and (xx, f, zz) in self.air and (xx, f + 2, zz) in self.air:
                        self.solidify(xx, f, zz, wchoice(self.rng, self.T.rock[:4] + [("minecraft:moss_block", 6)]))
                        if dd < 1 and (xx, f + 3, zz) in self.air:
                            self.solidify(xx, f + 1, zz, wchoice(self.rng, self.T.rock[:3]))

    def boundary(self):
        out = set()
        for (x, y, z) in self.air:
            for dx, dy, dz in D6.values():
                n = (x + dx, y + dy, z + dz)
                if n not in self.air and 1 <= n[0] <= self.w - 2 and 1 <= n[1] <= self.h - 2 and \
                        1 <= n[2] <= self.d - 2 and self.p.get(*n) in ROCK_IDS:
                    out.add(n)
        return sorted(out)

    def line(self, table):
        for pos in self.boundary():
            r = self.rng.random()
            for block, ch in table:
                if r < ch:
                    self.p.set(*pos, block)
                    break
                r -= ch

    def lights(self, n, ymin=3):
        cands = [p for p in self.boundary() if p[1] >= ymin]
        self.rng.shuffle(cands)
        for pos in cands[:n]:
            self.p.set(*pos, wchoice(self.rng, self.T.lights))

    def accent(self, n):
        cands = self.boundary()
        self.rng.shuffle(cands)
        for pos in cands[:n]:
            self.p.set(*pos, wchoice(self.rng, self.T.accent))

    def ores(self, n, ylo=None, yhi=None):
        ylo = self.f1 if ylo is None else ylo
        yhi = ylo + 4 if yhi is None else yhi
        cands = [p for p in self.boundary() if ylo <= p[1] <= yhi]
        self.rng.shuffle(cands)
        for pos in cands[:n]:
            self.p.set(*pos, wchoice(self.rng, self.T.ores))

    def arrow_slot(self, ylo=None, yhi=None, radius=9, near=None):
        ylo = self.f1 + 1 if ylo is None else ylo
        yhi = ylo + 1 if yhi is None else yhi
        cands = []
        for (x, y, z) in self.boundary():
            if not (ylo <= y <= yhi) or (x, y, z) in self.used:
                continue
            for fc in HORIZ:
                dx, _, dz = D6[fc]
                n = (x + dx, y, z + dz)
                b = (x - dx, y, z - dz)
                if n in self.air and n not in self.reserved and n not in self.used and self.p.inside(*b) and \
                        b not in self.air and 1 <= b[0] <= self.w - 2 and 1 <= b[2] <= self.d - 2:
                    cands.append((x, y, z))
                    break
        if not cands:
            return None
        if near:
            cands.sort(key=lambda c: abs(c[0] - near[0]) + abs(c[2] - near[1]) + self.rng.uniform(0, 4))
            x, y, z = cands[0]
        else:
            x, y, z = self.rng.choice(cands)
        self.p.set(x, y, z, AIR)
        self.used.add((x, y, z))
        self.p.marker(x, y, z, "trap", type="arrows", radius=radius, every=50, damage=4 if self.T.n == 1 else 6)
        return (x, y, z)

    def spike_patch(self, x, z, r=1, y=None):
        y = self.f1 if y is None else y
        for xx in range(x - r, x + r + 1):
            for zz in range(z - r, z + r + 1):
                if math.hypot(xx - x, zz - z) <= r + .4 and (xx, y, zz) in self.air and (xx, y, zz) not in self.reserved:
                    self.place(xx, y, zz, "minecraft:pointed_dripstone[thickness=tip,vertical_direction=up]")
                    self.place(xx, y + 1, zz, "minecraft:cobweb")
        self.p.marker(x, y, z, "trap", type="spikes", radius=r + 2, every=40, damage=3 if self.T.n == 1 else 5)

    def sac(self, cx, cy, cz, r):
        for x in range(cx - r - 1, cx + r + 2):
            for y in range(cy, cy + r * 2 + 2):
                for z in range(cz - r - 1, cz + r + 2):
                    v = ((x - cx) / r) ** 2 + ((y - cy - r * .7) / (r * 1.35)) ** 2 + ((z - cz) / r) ** 2
                    if v < 1 + .25 * self.noise(x, y, z) and (x, y, z) in self.air:
                        self.solidify(x, y, z, wchoice(self.rng, [("minecraft:white_wool", 55), ("minecraft:calcite", 15),
                                                                  ("minecraft:mushroom_stem", 12)]))
        for x in range(cx - r - 2, cx + r + 3):
            for z in range(cz - r - 2, cz + r + 3):
                if self.rng.random() < .25 and (x, cy, z) in self.air and self.free((x, cy, z)):
                    self.place(x, cy, z, f"minecraft:turtle_egg[eggs={self.rng.randint(1, 4)},hatch=0]")
                elif self.rng.random() < .2:
                    self.place(x, cy + self.rng.randint(0, 1), z, "minecraft:cobweb")

    def cocoon(self, x, z, ytop, length=3):
        if not self.solid(x, ytop + 1, z):
            return
        cells = [(x, ytop - i, z) for i in range(length + 1)]
        if not all(self.free(c) for c in cells):
            return
        self.place(*cells[0], "minecraft:cobweb")
        for c in cells[1:]:
            self.place(*c, "minecraft:white_wool")
        for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            if self.rng.random() < .5:
                self.place(x + dx, ytop - 2, z + dz, "minecraft:cobweb")

    def cocoons(self, n, lo=None):
        lo = self.h - 7 if lo is None else lo
        cells = [p for p in sorted(self.air) if p[1] >= lo and p[1] < self.h - 1 and self.solid(p[0], p[1] + 1, p[2])]
        for _ in range(n):
            if cells:
                x, y, z = self.rng.choice(cells)
                self.cocoon(x, z, y, self.rng.randint(2, 4))

    def column(self, x, z, block="minecraft:tuff"):
        y = 1
        while (x, y, z) in self.air and y < self.h - 1:
            if y <= 2:
                for dx in (-1, 0, 1):
                    for dz in (-1, 0, 1):
                        if abs(dx) + abs(dz) < 2 or y == 1:
                            self.solidify(x + dx, y, z + dz, block) if (x + dx, y, z + dz) in self.air else None
            else:
                self.solidify(x, y, z, "minecraft:calcite" if y % 5 == 0 else block)
            y += 1

    # -- decoration ---------------------------------------------------------------------------------------
    def decorate(self, web=.03, carpet=.10, lichen=.04, roots=.05, vines=.025, drip=.012, bones=.008,
                 floor_moss=.3, wall_vines=0.0, ymax=999, canopy=0.0):
        rng = self.rng
        T = self.T
        cells = sorted(self.air)
        for (x, y, z) in cells:
            if (x, y, z) in self.reserved or y > ymax:
                continue
            if self.solid(x, y - 1, z) and self.p.get(x, y - 1, z) in ROCK_IDS:
                r = rng.random()
                if r < floor_moss:
                    self.p.set(x, y - 1, z, wchoice(rng, T.floor))
                elif T.n == 1 and r < floor_moss + .08:
                    self.p.set(x, y - 1, z, "minecraft:rooted_dirt")
                elif T.n == 1 and r < floor_moss + .14:
                    self.p.set(x, y - 1, z, "minecraft:coarse_dirt")
        for pos in cells:
            x, y, z = pos
            if pos in self.reserved or pos in self.used or y > ymax or pos not in self.air:
                continue
            below, above = self.solid(x, y - 1, z), self.solid(x, y + 1, z)
            r = rng.random()
            if below:
                if r < carpet:
                    self.place(*pos, T.carpet)
                elif r < carpet + web:
                    self.place(*pos, "minecraft:cobweb")
                elif r < carpet + web + bones:
                    self.place(*pos, "minecraft:bone_block")
                    if rng.random() < .5:
                        self.place(x + rng.choice((-1, 1)), y, z, "minecraft:bone_block")
                elif r < carpet + web + bones + drip:
                    self.place(*pos, "minecraft:pointed_dripstone[thickness=tip,vertical_direction=up]")
            elif above:
                if r < roots:
                    self.place(*pos, "minecraft:hanging_roots")
                elif r < roots + vines:
                    n = rng.randint(2, 5)
                    chain = [(x, y - i, z) for i in range(n) if self.free((x, y - i, z))]
                    if chain:
                        for i, c in enumerate(chain):
                            berry = "true" if rng.random() < .6 else "false"
                            if i == len(chain) - 1:
                                self.place(*c, f"minecraft:cave_vines[age={rng.randint(1, 25)},berries={berry}]")
                            else:
                                self.place(*c, f"minecraft:cave_vines_plant[berries={berry}]")
                elif r < roots + vines + web + canopy:
                    self.place(*pos, "minecraft:cobweb")
                elif r < roots + vines + web + canopy + drip:
                    self.place(*pos, "minecraft:pointed_dripstone[thickness=tip,vertical_direction=down]")
            if pos in self.used:
                continue
            if rng.random() < lichen or (wall_vines and rng.random() < wall_vines):
                faces = [f for f in D6 if self.solid(x + D6[f][0], y + D6[f][1], z + D6[f][2])]
                if faces:
                    f = rng.choice(faces)
                    if wall_vines and f in HORIZ and rng.random() < .5:
                        self.place(*pos, f"minecraft:vine[{f}=true]")
                    else:
                        self.place(*pos, f"minecraft:glow_lichen[{f}=true]")

    def dress(self, canopy=0.0, lights=None, accent=None, sculk=None, **kw):
        if self.T.n == 1:
            params = dict(web=.05, carpet=.10, lichen=.05, roots=.06, vines=.03, drip=.012, bones=.01)
        else:
            params = dict(web=.07, carpet=.08, lichen=.02, roots=.03, vines=.012, drip=.015, bones=.02, floor_moss=.38)
        params.update(kw)
        self.decorate(canopy=canopy, **params)
        self.lights(lights if lights is not None else (3 if self.T.n == 1 else 9))
        self.accent(accent if accent is not None else (4 if self.T.n == 1 else 14))
        sculk = sculk if sculk is not None else (1 if self.T.n == 1 else 3)
        if sculk:
            self.sculk_patches(sculk)

    def sculk_patches(self, n):
        f = self.f1
        for _ in range(n):
            cells = [c for c in sorted(self.air) if c[1] == f and c not in self.reserved]
            if not cells:
                return
            x, _, z = self.rng.choice(cells)
            for xx in range(x - 2, x + 3):
                for zz in range(z - 2, z + 3):
                    if math.hypot(xx - x, zz - z) < 2 + self.rng.uniform(-.5, .5) and (xx, f, zz) in self.air \
                            and (xx, f, zz) not in self.reserved and self.p.get(xx, f - 1, zz) in ROCK_IDS:
                        self.p.set(xx, f - 1, zz, "minecraft:sculk")
                        if self.rng.random() < .3 and self.free((xx, f, zz)):
                            self.place(xx, f, zz, "minecraft:sculk_vein[down=true]")

    # -- checks / output ------------------------------------------------------------------------------------
    def verify(self):
        if not self.fill or not self.dpos:
            return
        cells = self.p.cells
        gate = set()
        for e in self.p.entities:
            data = e["nbt"].get("data", {})
            if data.get("skyrpg") in ("door", "key_door"):
                pos = tuple(e["blockPos"])
                bid = self.p.get(*pos)
                seen, stack = {pos}, [pos]
                while stack and len(seen) <= 64:
                    cur = stack.pop()
                    for dx, dy, dz in D6.values():
                        n = (cur[0] + dx, cur[1] + dy, cur[2] + dz)
                        if n not in seen and self.p.get(*n) == bid:
                            seen.add(n)
                            stack.append(n)
                gate |= seen

        def ok(pos):
            cell = cells.get(pos)
            return pos in gate or (cell is not None and passable(cell[0]["id"]))
        start = self.dpos[0]
        reach, stack = {start}, [start]
        while stack:
            cur = stack.pop()
            for dx, dy, dz in D6.values():
                n = (cur[0] + dx, cur[1] + dy, cur[2] + dz)
                if n not in reach and ok(n):
                    reach.add(n)
                    stack.append(n)
        for pos in self.dpos[1:]:
            if pos not in reach:
                PROBLEMS.append(f"{self.p.path}: door at {pos} unreachable from the entry")
        for e in self.p.entities:
            data = e["nbt"].get("data", {})
            pos = tuple(e["blockPos"])
            kind = data.get("skyrpg")
            if kind in ("spawner", "boss") and pos not in reach:
                PROBLEMS.append(f"{self.p.path}: {kind} marker at {pos} not in the open cave")
            if kind == "chest":
                if not any((pos[0] + dx, pos[1] + dy, pos[2] + dz) in reach for dx, dy, dz in D6.values()):
                    PROBLEMS.append(f"{self.p.path}: chest at {pos} is walled in")

    def finish(self):
        for side, name, target, pool, pri, y, at in self.doors:
            self.dpos.append(self.p.door(side, name, target=target, pool=pool, placement_priority=pri, y=y, at=at))
        self.verify()
        return PK.add(self.p)


# ============================================================================================================
# Mobs
# ============================================================================================================
SPIDER, CAVE, SKEL, WITCH = "minecraft:spider", "minecraft:cave_spider", "minecraft:skeleton", "minecraft:witch"
CRAWLER, HAG = "skyrpg_combat:glimmer_crawler", "skyrpg_combat:bog_hag"


def m(type_, count, d, name, elite=False, **kw):
    e = {"type": type_, "count": count, "level": L(d), "elite": elite, "name": name}
    e.update(kw)
    return e


def webbed(count, d=1):
    return m(SKEL, count, d, "Webbed Corpse", equipment={"mainhand": "minecraft:bow"})


def spawn(c, x, z, *waves, radius=10, spread=3, y=None, group=None):
    """A spawner marker with 4-12 mobs in total (one list = one wave)."""
    y = c.f1 if y is None else y
    n = sum(e["count"] for w in waves for e in w)
    assert 4 <= n <= 12, (c.p.path, n)
    c.zone(x, z, 2, y=y)
    data = dict(radius=radius, spread=spread)
    if group:
        data["group"] = group
    if len(waves) == 1:
        data["mobs"] = waves[0]
    else:
        data["waves"] = list(waves)
    c.p.marker(x, y, z, "spawner", **data)


def rpack(c):
    options = [
        lambda: [[m(SPIDER, 3, 1, "Hollow Weaver"), m(CAVE, 4, 0, "Broodling")]],
        lambda: [[m(CAVE, 5, 0, "Broodling")], [m(SPIDER, 3, 2, "Silk Stalker")]],
        lambda: [[webbed(4)], [m(SPIDER, 2, 1, "Hollow Weaver"), m(CAVE, 3, 0, "Broodling")]],
        lambda: [[m(CRAWLER, 3, 2, "Glimmer Crawler"), m(CAVE, 3, 0, "Broodling")]],
        lambda: [[m(WITCH, 2, 2, "Venom Witch"), m(CAVE, 4, 0, "Broodling"), webbed(2)]],
        lambda: [[m(SPIDER, 2, 2, "Brood Guard", True), m(CAVE, 5, 1, "Broodling")]],
        lambda: [[m(HAG, 1, 3, "Hag of the Hollow"), m(SPIDER, 3, 1, "Hollow Weaver"), m(CAVE, 3, 0, "Broodling")]],
        lambda: [[m(CAVE, 4, 0, "Broodling"), webbed(2)], [m(SPIDER, 2, 2, "Silk Stalker"), m(CRAWLER, 2, 2, "Glimmer Crawler")]],
    ]
    return c.rng.choice(options)()


def chest(c, near=None, loot="common", avoid=(), kind="chest"):
    x, y, z, f = c.spot(near=near, avoid=avoid)
    path = {"common": "hollow_common", "rare": "hollow_rare", "secret": "hollow_secret"}[loot]
    if kind == "barrel":
        c.p.barrel(x, y, z, f, f"skyrpg_world:chests/{path}")
    else:
        c.p.chest(x, y, z, f, f"skyrpg_world:chests/{path}")
    return x, y, z, f


def seed_of(path):
    return sum(ord(ch) * (i + 3) for i, ch in enumerate(path)) % 1000003


# ============================================================================================================
# Entrance
# ============================================================================================================
def build_entrance():
    use(T1)
    W = D = 25
    H = K + 8
    cx = cz = 12
    c = Cave(f"{P}/entrance", W, H, D, 6120, fill=False)
    rng = c.rng
    R_TOP = 7.5
    for x in range(W):
        for z in range(D):
            r = math.hypot(x - cx, z - cz)
            for y in range(K):
                if y >= K - 2 and r > 10.5:
                    continue
                if y == K - 1:
                    b = wchoice(rng, [("minecraft:moss_block", 40), ("minecraft:podzol", 30), ("minecraft:coarse_dirt", 15),
                                      ("minecraft:rooted_dirt", 15)])
                elif y == K - 2:
                    b = wchoice(rng, [("minecraft:dirt", 50), ("minecraft:coarse_dirt", 20), ("minecraft:rooted_dirt", 20),
                                      ("minecraft:stone", 10)])
                elif y >= K - 4:
                    b = wchoice(rng, [("minecraft:dirt", 30), ("minecraft:stone", 40), ("minecraft:tuff", 20),
                                      ("minecraft:coarse_dirt", 10)])
                else:
                    b = wchoice(rng, ROCK1)
                c.p.set(x, y, z, b)

    def Rf(y):
        t = max(0.0, min(1.0, (y - 2) / (K - 3)))
        return 4.2 + 3.3 * t * t

    for y in range(2, K + 7):
        for x in range(2, W - 2):
            for z in range(2, D - 2):
                r = math.hypot(x - cx, z - cz)
                R = Rf(min(y, K - 1)) * (1 + .14 * c.noise(x, y * .5, z))
                if y >= K:
                    R = R_TOP * (1 + .1 * c.noise(x, 3, z)) - (y - K) * .15
                if r < R:
                    c.carve(x, y, z)
    c.blob(12, 2, 13, 7.5, 4.6, 7, .35)
    c.lobes(12, 13, 7, 7, 4.5, 4)
    c.door("south", "hollow_out", "hollow_in", f"{P}/f1_s1", 10, length=11)
    c.tunnel_to_center("south")
    for idx, y in enumerate(range(K - 2, 0, -1)):
        th0 = idx * 0.6
        Ry = Rf(y)
        for x in range(2, W - 2):
            for z in range(2, D - 2):
                dx, dz = x - cx, z - cz
                r = math.hypot(dx, dz)
                if Ry - 2.6 <= r <= Ry - 0.6:
                    da = (math.atan2(dz, dx) - th0) % (2 * math.pi)
                    if da < 0.8 and (x, y, z) in c.air:
                        c.solidify(x, y, z, "minecraft:dark_oak_planks", force=True)
        if idx % 3 == 0:
            a = th0 + .4
            px, pz = round(cx + math.cos(a) * (Ry - 1.2)), round(cz + math.sin(a) * (Ry - 1.2))
            yy = y - 1
            while (px, yy, pz) in c.air and yy >= 1:
                c.solidify(px, yy, pz, "minecraft:dark_oak_log[axis=y]", force=True)
                yy -= 1
    # a welcome party and a chest in the bottom room
    spawn(c, 15, 17, [m(CAVE, 5, 0, "Broodling")], [m(SPIDER, 3, 1, "Hollow Weaver")], radius=9, spread=3)
    try:
        chest(c, near=(18, 15), loot="common")
    except SystemExit:
        pass
    c.mounds(3)
    c.decorate(web=.03, carpet=.12, lichen=.06, roots=.10, vines=.05, drip=0.01, bones=.01, floor_moss=.35,
               wall_vines=.12, ymax=K - 1)
    for x in range(W):
        for z in range(D):
            r = math.hypot(x - cx, z - cz)
            if R_TOP + .4 < r < 10.4:
                rr = rng.random()
                if rr < .35:
                    c.p.set(x, K, z, "minecraft:moss_carpet")
                elif rr < .43:
                    c.p.set(x, K, z, "minecraft:mangrove_roots")
                elif rr < .50:
                    c.p.set(x, K, z, "minecraft:cobweb")
                elif rr < .53:
                    c.p.set(x, K, z, "minecraft:bone_block")
                elif rr < .56:
                    c.p.set(x, K, z, "minecraft:dark_oak_log[axis=" + rng.choice("xz") + "]")
    for x in range(19, 24):
        for z in range(10, 15):
            if math.hypot(x - cx, z - cz) < 10.4:
                c.p.set(x, K - 1, z, wchoice(rng, [("minecraft:mossy_stone_bricks", 60), ("minecraft:mossy_cobblestone", 25),
                                                   ("minecraft:cracked_stone_bricks", 15)]))
                c.p.set(x, K, z, AIR)
    c.p.set(21, K, 12, "skyrpg_world:waystone")
    for z in (10, 14):
        c.p.set(22, K, z, "minecraft:dark_oak_fence")
        c.p.set(22, K + 1, z, "minecraft:lantern")
    c.p.set(20, K, 12, "minecraft:mossy_stone_brick_stairs[facing=east]")
    for z in range(3, 22):
        c.p.set(12, K + 4, z, "minecraft:dark_oak_log[axis=z]")
        if 5 <= z <= 19 and rng.random() < .5 and (12, K + 3, z) in c.air:
            c.p.set(12, K + 3, z, rng.choice(["minecraft:hanging_roots", "minecraft:cobweb", "minecraft:cobweb"]))
            if rng.random() < .4:
                c.p.set(12, K + 2, z, "minecraft:cobweb")
    for z in (3, 21):
        for y in range(K, K + 4):
            c.p.set(12, y, z, "minecraft:dark_oak_log[axis=y]")
    for x in (11, 13):
        for z in (4, 20):
            c.p.set(x, K + 3, z, "minecraft:dark_oak_log[axis=y]")
    c.p.marker(12, K + 1, 12, "dungeon", dungeon=DUN)
    c.finish()


# ============================================================================================================
# Spine halls
# ============================================================================================================
def hall(tag, w, kind, sides, fwd, h):
    n = CUR.n
    path = f"{P}/f{n}/{tag}"
    d = 21
    c = Cave(path, w, h, d, seed_of(path), floor=0)
    cx, cz = w // 2, d // 2
    rx, rz = w / 2 - 4, d / 2 - 3.2
    c.blob(cx, 1, cz, rx, h - 3, rz, .3)
    c.lobes(cx, cz, rx, rz, h - 3, 5)
    c.door("south", "hollow_in", "hollow_in", length=7)
    c.door("north", "hollow_out", "hollow_in", fwd, 10, length=7)
    sl = int(w / 2 - rx) + 5
    for s in sides:
        c.door(s, "hollow_out", "hollow_in", f"{P}/f{n}_chambers", 0, length=sl)
    c.reserve_lanes()
    spawn(c, cx + 4, cz - 4, *rpack(c), radius=11, spread=4)
    if kind == "pillars":
        for dx, dz in ((-5, -5), (5, 5), (-5, 5), (5, -5), (-7, 0)):
            c.column(cx + dx, cz + dz)
        c.mounds(2)
        c.spike_patch(cx - 5, cz + 1 if "west" not in sides else cz + 5, 1)
    elif kind == "sacs":
        c.sac(cx - 6, 1, cz + 5, 2)
        c.sac(cx + 6, 1, cz + 6, 2)
        c.mounds(1)
        c.cocoons(8)
    elif kind == "ruins":
        for _ in range(4):
            x0 = c.rng.randint(3, w - 7)
            z0 = c.rng.randint(3, d - 4)
            horiz = c.rng.random() < .5
            for i in range(c.rng.randint(3, 5)):
                x, z = (x0 + i, z0) if horiz else (x0, z0 + i)
                for y in range(1, c.rng.randint(2, 4)):
                    if (x, y, z) in c.air and c.free((x, y, z)):
                        c.solidify(x, y, z, c.rng.choice(["minecraft:tuff_bricks", "minecraft:cracked_stone_bricks",
                                                          "minecraft:mossy_stone_bricks"]))
        c.mounds(2)
    else:
        c.mounds(5)
    chest(c, near=(cx - 5, cz + 4), loot="common")
    if kind == "sacs":
        chest(c, near=(cx + 6, cz - 5), loot="rare" if n == 2 else "common")
    c.arrow_slot(near=(cx - 2, cz))
    c.dress(canopy=.12 if kind == "sacs" else .05)
    c.finish()


HALLS = {
    1: {1: [("a", 23, "pillars", ("west", "east")), ("b", 27, "sacs", ("east",)), ("c", 25, "ruins", ("west", "east"))],
        2: [("a", 25, "mounds", ("west", "east")), ("b", 23, "pillars", ("west",)), ("c", 27, "ruins", ("west", "east"))],
        3: [("a", 25, "sacs", ("west", "east")), ("b", 23, "ruins", ("west", "east")), ("c", 27, "pillars", ("west", "east"))]},
    2: {1: [("a", 25, "pillars", ("west",)), ("b", 23, "sacs", ("west",)), ("c", 27, "ruins", ("west",))],
        2: [("a", 27, "mounds", ("west",)), ("b", 25, "pillars", ("west",)), ("c", 23, "sacs", ("west",))],
        3: [("a", 25, "ruins", ("west",)), ("b", 27, "sacs", ("west",)), ("c", 23, "pillars", ("west",))]},
}


def build_halls(floor):
    use(T1 if floor == 1 else T2)
    h = 11 if floor == 1 else 10
    nxt = {1: f"{P}/f{floor}_s2", 2: f"{P}/f{floor}_s3", 3: f"{P}/champion" if floor == 1 else f"{P}/lair"}
    for step, specs in HALLS[floor].items():
        for letter, w, kind, sides in specs:
            hall(f"s{step}{letter}", w, kind, sides, nxt[step], h)
        PK.pool(f"{P}/f{floor}_s{step}", [(f"{P}/f{floor}/s{step}{letter}", 3) for letter, *_ in specs],
                fallback=f"{P}/caps")


# ============================================================================================================
# Champion nest, shaft
# ============================================================================================================
def champion_nest():
    use(T1)
    W, H, D = 21, 11, 19
    c = Cave(f"{P}/champion", W, H, D, 7301)
    cx = 10
    for x in range(7, 14):
        for z in range(1, 6):
            for y in range(1, H - 1):
                c.nocarve.add((x, y, z))
    c.blob(cx, 1, 11, 8.5, H - 3, 7.5, .3)
    c.lobes(cx, 11, 8.5, 7.5, H - 3, 6, lr=(2.5, 4))
    c.door("south", "hollow_in", "hollow_in", length=6)
    c.door("north", "hollow_out", "hollow_in", f"{P}/shaft", 10, length=6)
    c.reserve_lanes()
    # the plug: mossy cobblestone (z 2-3) and cobweb (z 4-5), 3x3, both open when the champion dies
    for z, block in ((2, "minecraft:mossy_cobblestone"), (3, "minecraft:mossy_cobblestone"),
                     (4, "minecraft:cobweb"), (5, "minecraft:cobweb")):
        for x in range(9, 12):
            for y in range(1, 4):
                c.p.set(x, y, z, block)
                c.air.discard((x, y, z))
                c.used.add((x, y, z))
    c.p.marker(cx, 2, 2, "door", group="champion", message="The silk plug holds. Slay the Silk Matron.")
    c.p.marker(cx, 2, 4, "door", group="champion", message="The silk plug holds. Slay the Silk Matron.")
    c.zone(cx, 11, 4, h=4)
    for px, pz in ((4, 8), (16, 8), (4, 14), (16, 14)):
        c.column(px, pz)
    c.sac(3, 1, 7, 2)
    c.sac(17, 1, 8, 2)
    spawn(c, cx, 15, [m(CAVE, 5, 0, "Broodling")], [m(SPIDER, 3, 2, "Silk Stalker")], radius=9, spread=3)
    c.spike_patch(5, 12, 1)
    c.spike_patch(15, 11, 1)
    c.arrow_slot(near=(cx, 8), radius=11)
    c.arrow_slot(near=(3, 12), radius=11)
    chest(c, near=(17, 13), loot="rare")
    chest(c, near=(3, 15), loot="common")
    c.cocoons(10)
    c.dress(canopy=.25)
    # ceiling web canopy over the boss
    for pos in sorted(c.air):
        x, y, z = pos
        if y >= H - 5 and pos not in c.used and pos not in c.reserved and c.solid(x, y + 1, z) and c.rng.random() < .35:
            c.place(x, y, z, "minecraft:cobweb")
    c.p.marker(cx, 1, 11, "boss", boss="skyrpg_world:hollow_champion", radius=12, group="champion")
    c.finish()


def shaft():
    use(T1)
    W = D = 19
    H = DROP + 11
    c = Cave(f"{P}/shaft", W, H, D, 7771)
    cx = cz = 9
    for y in range(1, DROP + 10):
        for x in range(1, 18):
            for z in range(1, 18):
                R = 7.4 + .5 * c.noise(x, y, z)
                if y > DROP + 6:
                    R *= math.sqrt(max(.05, 1 - ((y - DROP - 6) / 4.0) ** 2))
                if math.hypot(x - cx, z - cz) < R:
                    c.carve(x, y, z)
    # darker rock below
    for (x, y, z), (entry, data) in list(c.p.cells.items()):
        if data is None and entry["id"] in ROCK_IDS and (x, y, z) not in c.air:
            thr = 1.0 if y <= 6 else (.5 if y <= 11 else 0)
            if thr and c.rng.random() < thr:
                c.p.set(x, y, z, wchoice(c.rng, ROCK2))
    c.door("south", "hollow_in", "hollow_in", length=5, y=DROP + 1)
    c.door("west", "hollow_out", "hollow_in", f"{P}/f2_s1", 10, length=9, y=1, at=16)
    # spiral ledges, clockwise from the entry platform (south) round to the exit side
    ledge = {}
    for x in range(1, 18):
        for z in range(1, 18):
            dx, dz = x - cx, z - cz
            r = math.hypot(dx, dz)
            if not (4.6 <= r <= 7.6):
                continue
            delta = (90 - math.degrees(math.atan2(dz, dx))) % 360
            if delta >= 335:
                ys = DROP
            elif delta <= 300:
                ys = DROP - round(DROP * delta / 300)
            else:
                continue
            if ys < 1 or (x, ys + 1, z) not in c.air:
                continue
            for y in range(1, ys):
                c.solidify(x, y, z, "minecraft:cobbled_deepslate", force=True)
            c.solidify(x, ys, z, "minecraft:dark_oak_planks", force=True)
            ledge[(x, z)] = (ys, r)
    for (x, z), (ys, r) in sorted(ledge.items()):
        if r < 5.5 and c.rng.random() < .6 and (x, ys + 1, z) in c.air:
            c.place(x, ys + 1, z, "minecraft:dark_oak_fence", force=True)
            if c.rng.random() < .22:
                c.place(x, ys + 2, z, "minecraft:lantern", force=True)
    # climbable vines down two walls
    for ang in (-40, 28):
        for y in range(1, DROP + 3):
            best = None
            for x in range(1, 18):
                for z in range(1, 18):
                    if (x, y, z) not in c.air or (x, y, z) in c.used or (x, y, z) in c.reserved:
                        continue
                    a = math.degrees(math.atan2(z - cz, x - cx))
                    if abs((a - ang + 180) % 360 - 180) < 6:
                        for f in HORIZ:
                            dxx, _, dzz = D6[f]
                            if (x + dxx, y, z + dzz) not in c.air and c.p.inside(x + dxx, y, z + dzz) and \
                                    (best is None or math.hypot(x - cx, z - cz) > best[0]):
                                best = (math.hypot(x - cx, z - cz), x, z, f)
            if best:
                c.place(best[1], y, best[2], f"minecraft:vine[{best[3]}=true]", force=True)
    # cobweb nets to catch jumpers
    for y in (DROP - 4, 5):
        for x in range(1, 18):
            for z in range(1, 18):
                if math.hypot(x - cx, z - cz) < 4.4 and c.rng.random() < .8:
                    c.place(x, y, z, "minecraft:cobweb")
    use(T2)
    spawn(c, 8, 10, [m(CAVE, 5, 1, "Broodling")], [m(SPIDER, 3, 2, "Silk Stalker")], radius=9, spread=3, y=1)
    c.spike_patch(12, 7, 1)
    c.spike_patch(12, 12, 1)
    c.arrow_slot(ylo=9, yhi=10, radius=10)
    c.arrow_slot(ylo=3, yhi=4, radius=10)
    c.decorate(web=.04, carpet=.06, lichen=.03, roots=.03, vines=.01, drip=.01, bones=.02, ymax=6, floor_moss=.3)
    c.lights(8, ymin=3)
    c.accent(8)
    c.finish()
    # walk test: entry platform -> exit
    cells = c.p.cells

    def stand(x, y, z):
        a, b, g = cells.get((x, y, z)), cells.get((x, y + 1, z)), cells.get((x, y - 1, z))
        return a and b and g and passable(a[0]["id"]) and passable(b[0]["id"]) and not passable(g[0]["id"])
    start, goal = (9, DROP + 1, 17), (1, 1, 16)
    if not stand(*start):
        PROBLEMS.append(f"shaft: entry platform {start} is not standable")
    seen, stack = {start}, [start]
    while stack:
        x, y, z = stack.pop()
        for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            for dy in (1, 0, -1, -2, -3):
                n = (x + dx, y + dy, z + dz)
                if n not in seen and c.p.inside(*n) and stand(*n):
                    seen.add(n)
                    stack.append(n)
    if goal not in seen:
        PROBLEMS.append("shaft: no walkable route from the entry platform to the exit")


# ============================================================================================================
# Chambers
# ============================================================================================================
def chamber(name, w, h, d, rx, rz, nl=5, tail=True, floor=0):
    n = CUR.n
    path = f"{P}/f{n}/{name}" + ("" if tail else "_end")
    c = Cave(path, w, h, d, seed_of(path), floor=floor)
    cx, cz = w // 2, d // 2
    ry = h - 3 - floor
    c.blob(cx, c.f1, cz, rx, ry, rz, .3)
    c.lobes(cx, cz, rx, rz, ry, nl)
    ln = max(5, int(d / 2 - rz) + 5)
    c.door("south", "hollow_in", "hollow_in", length=ln, y=floor + 1)
    if tail:
        c.door("north", "hollow_out", "hollow_in", f"{P}/f{n}_tails", 0, length=ln, y=floor + 1)
    c.reserve_lanes()
    return c


def egg_chamber(tail=True):
    c = chamber("egg_chamber", 21, 10, 21, 8, 8, tail=tail)
    cx = cz = 10
    spawn(c, cx, cz, [m(CAVE, 4, 0, "Broodling"), m(SPIDER, 1, 3, "Brood Mother", True)],
          [m(SPIDER, 3, 1, "Hollow Weaver"), m(CAVE, 2, 0, "Broodling")], radius=11, spread=4)
    for dx, dz, r in ((-6, -5, 2), (5, -6, 2), (6, 5, 2), (-6, 5, 2), (0, -8, 2)):
        c.sac(cx + dx, 1, cz + dz, r)
    c.sculk_patches(2)
    c.mounds(2)
    chest(c, near=(cx + 6, cz - 6), loot="common")
    if CUR.n == 2:
        chest(c, near=(cx - 6, cz - 6), loot="rare")
    c.dress(canopy=.1)
    c.finish()


def glimmer_grotto(tail=True):
    c = chamber("glimmer_grotto", 21, 11, 21, 8, 8, tail=tail)
    cx = cz = 10
    spawn(c, cx, cz, [m(CRAWLER, 4, 2, "Glimmer Crawler")],
          [m(CRAWLER, 2, 3, "Prism Crawler"), m(SPIDER, 2, 1, "Hollow Weaver")], radius=11, spread=4)
    c.mounds(2)
    chest(c, loot="common")
    if CUR.n == 2:
        chest(c, loot="rare")
    c.line([("minecraft:amethyst_block", .42), ("minecraft:budding_amethyst", .07), ("minecraft:calcite", .12)])
    for _ in range(5):
        cells = [p for p in sorted(c.air) if p[1] == 1 and c.free(p) and 4 < p[0] < 16 and 4 < p[2] < 16]
        if cells:
            x, _, z = c.rng.choice(cells)
            for y in range(1, c.rng.randint(3, 5)):
                c.solidify(x, y, z, "minecraft:amethyst_block" if y % 3 else "minecraft:budding_amethyst")
    for pos in sorted(c.air):
        if not c.free(pos):
            continue
        for f, (dx, dy, dz) in D6.items():
            nb = (pos[0] + dx, pos[1] + dy, pos[2] + dz)
            if c.p.inside(*nb) and nb not in c.air and c.p.get(*nb) in ("minecraft:amethyst_block", "minecraft:budding_amethyst") \
                    and c.rng.random() < .22:
                c.place(*pos, "minecraft:" + c.rng.choice(["amethyst_cluster", "large_amethyst_bud", "medium_amethyst_bud",
                                                           "small_amethyst_bud"]) + f"[facing={OPP[f]}]")
                break
    c.dress(web=.015, carpet=.05, lichen=.16, roots=.04, vines=.07, drip=.01, bones=.004, floor_moss=.15,
            lights=1, accent=0)
    c.finish()


def brood_nest(tail=True):
    c = chamber("brood_nest", 21, 11, 25, 8.5, 10.5, nl=6, tail=tail)
    cx, cz = 10, 12
    spawn(c, cx, cz, [m(SPIDER, 2, 3, "Brood Guard", True), m(CAVE, 4, 1, "Broodling")],
          [m(HAG, 1, 4, "Hag of the Hollow", True), m(SPIDER, 2, 2, "Silk Stalker")], radius=12, spread=4)
    c.sac(5, 1, 5, 2)
    c.sac(16, 1, 6, 2)
    c.sculk_patches(1)
    c.mounds(3)
    chest(c, near=(cx, 4), loot="rare")
    chest(c, near=(4, 18), loot="common")
    c.cocoons(8)
    for _ in range(5):
        cells = [p for p in sorted(c.air) if p[1] == 1 and c.free(p)]
        x, _, z = c.rng.choice(cells)
        c.place(x, 1, z, "minecraft:bone_block")
        c.place(x, 2, z, "minecraft:skeleton_skull[rotation=%d]" % c.rng.randrange(16))
    c.dress(web=.06, carpet=.08, lichen=.03, roots=.05, vines=.02, drip=.01, bones=.03, canopy=.2)
    c.finish()


def mushroom_grotto(tail=True):
    c = chamber("mushroom_grotto", 21, 12, 25, 8.5, 10.5, tail=tail)
    cx, cz = 10, 12
    spawn(c, cx + 5, cz + 2, [m(WITCH, 2, 2, "Spore Witch"), m(CAVE, 4, 0, "Broodling")],
          [m(HAG, 1, 3, "Mycelial Hag", True), m(SPIDER, 2, 1, "Hollow Weaver")], radius=11, spread=4)
    placed = []
    for _ in range(120):
        if len(placed) >= 8:
            break
        x, z = c.rng.randint(3, 17), c.rng.randint(3, 21)
        hh, r = c.rng.randint(4, 6), c.rng.choice((2, 3))
        if not c.free((x, 1, z)) or any(math.hypot(x - a, z - b) < 6 for a, b in placed):
            continue
        if not all((x, y, z) in c.air and (x, y, z) not in c.reserved for y in range(1, hh + 3)):
            continue
        block = c.rng.choice(["minecraft:red_mushroom_block", "minecraft:brown_mushroom_block"])
        for y in range(1, hh + 1):
            c.solidify(x, y, z, "minecraft:mushroom_stem")
        for dy, rr in ((0, r), (1, r - 1)):
            for xx in range(x - rr, x + rr + 1):
                for zz in range(z - rr, z + rr + 1):
                    if math.hypot(xx - x, zz - z) <= rr + .3 and (xx, hh + 1 + dy, zz) in c.air:
                        c.solidify(xx, hh + 1 + dy, zz, block)
        if (x + r - 1, hh, z) in c.air:
            c.solidify(x + r - 1, hh, z, "minecraft:shroomlight")
        placed.append((x, z))
    for (x, y, z) in sorted(c.air):
        if y == 1 and (x, 0, z) and c.p.get(x, 0, z) in ROCK_IDS and c.rng.random() < .6:
            c.p.set(x, 0, z, "minecraft:mycelium")
            if c.free((x, 1, z)) and c.rng.random() < .12:
                c.place(x, 1, z, c.rng.choice(["minecraft:red_mushroom", "minecraft:brown_mushroom"]))
    chest(c, near=(4, 18), loot="common")
    chest(c, near=(16, 5), loot="rare")
    c.dress(web=.02, carpet=0.0, lichen=.08, roots=.03, vines=.05, floor_moss=0.0, lights=4, accent=2)
    c.finish()


def underground_lake(tail=True):
    fl = 3
    c = chamber("underground_lake", 21, 13, 27, 8.5, 11, nl=4, tail=tail, floor=fl)
    cx, cz = 10, 13
    for x in range(1, 20):
        for z in range(1, 26):
            if ((x - cx) / 5.8) ** 2 + ((z - cz) / 8) ** 2 < 1 + .12 * c.noise(x, 3, z):
                for y in range(1, fl + 1):
                    c.carve(x, y, z, force=True)
    for pos in sorted(c.air):
        x, y, z = pos
        if y <= fl - 1 and ((x - cx) / 5.8) ** 2 + ((z - cz) / 8) ** 2 < 1 + .12 * c.noise(x, 3, z):
            c.solidify(x, y, z, "minecraft:water", force=True)
            c.p.set(x, 0, z, "minecraft:clay" if (x + z) % 3 else "minecraft:gravel")
    # islet with the cache (secret on this floor)
    ix, iz = cx, 7
    for x in range(ix - 2, ix + 3):
        for z in range(iz - 2, iz + 3):
            if math.hypot(x - ix, z - iz) < 2.2:
                for y in range(1, fl + 1):
                    c.solidify(x, y, z, "minecraft:mossy_cobblestone" if y == fl else "minecraft:stone", force=True)
    c.p.chest(ix, fl + 1, iz, "south", "skyrpg_world:chests/hollow_secret")
    c.p.marker(ix, fl + 1, iz + 1, "secret", name="Drowned Cache")
    for dz in (0, 1):
        c.reserved.add((ix, fl + 1, iz + dz))
        c.reserved.add((ix, fl + 2, iz + dz))
    c.p.entity(cx + 2, fl, cz + 4, {"id": "minecraft:oak_boat", "Rotation": [90.0, 0.0]})
    spawn(c, 8, 22, [m(CAVE, 4, 0, "Broodling")], [m(SPIDER, 3, 1, "Hollow Weaver"), webbed(2)],
          radius=11, spread=4, y=fl + 1)
    chest(c, near=(4, 20), loot="common")
    c.arrow_slot(ylo=fl + 2, yhi=fl + 3, radius=11, near=(4, 12))
    c.dress(web=.03, carpet=.05, lichen=.1, roots=.04, vines=.05, lights=5, accent=2, sculk=0)
    c.finish()


def silk_bridges(tail=True):
    fl = 4
    c = chamber("silk_bridges", 21, 15, 25, 8.5, 10.5, nl=4, tail=tail, floor=fl)
    cx = 10
    chasm = lambda x, z: ((x - cx) / 7.6) ** 2 + ((z - 12) / 5.8) ** 2 < 1
    for x in range(1, 20):
        for z in range(1, 24):
            if chasm(x, z):
                for y in range(1, fl + 1):
                    c.carve(x, y, z, force=True)
    # strands over the chasm, an island with the cache, a ramp out of the pit on the west side
    for bx in (6, 10, 14):
        for z in range(5, 20):
            if (bx, fl, z) in c.air or chasm(bx, z):
                c.solidify(bx, fl, z, "minecraft:white_wool", force=True)
                for sx in (bx - 1, bx + 1):
                    if (sx, fl + 1, z) in c.air and z % 2 == 0:
                        c.place(sx, fl + 1, z, "minecraft:cobweb", force=True)
    for x in range(cx - 2, cx + 3):
        for z in range(10, 15):
            if math.hypot(x - cx, z - 12) < 2.6:
                for y in range(1, fl + 1):
                    c.solidify(x, y, z, "minecraft:mossy_stone_bricks" if y == fl else "minecraft:stone", force=True)
    for z in range(8, 18):
        hh = 1 + round((z - 8) * 3 / 9)
        for x in (3, 4):
            if (x, 1, z) in c.air:
                for y in range(1, hh + 1):
                    c.solidify(x, y, z, "minecraft:tuff_bricks", force=True)
    if CUR.n == 2:
        c.p.chest(cx, fl + 1, 12, "south", "skyrpg_world:chests/hollow_secret")
        c.p.marker(cx, fl + 1, 13, "secret", name="Island of Silk")
    else:
        c.p.chest(cx, fl + 1, 12, "south", "skyrpg_world:chests/hollow_rare")
    c.reserved |= {(cx, fl + 1, 12), (cx, fl + 2, 12), (cx, fl + 1, 13)}
    for (x, z) in ((6, 9), (14, 15), (8, 15)):
        c.p.marker(x, 1, z, "trap", type="spikes", radius=3, every=40, damage=4 if CUR.n == 1 else 6)
    for (x, z) in sorted((x, z) for (x, y, z) in c.air if y == 1 and chasm(x, z)):
        r = c.rng.random()
        if r < .22:
            c.place(x, 1, z, "minecraft:pointed_dripstone[thickness=tip,vertical_direction=up]")
        elif r < .4:
            c.place(x, 2, z, "minecraft:cobweb")
    spawn(c, cx, 21, [m(CAVE, 5, 0, "Broodling")], [m(SPIDER, 3, 2, "Silk Stalker"), webbed(2, 1)],
          radius=11, spread=4, y=fl + 1)
    spawn(c, 15, 12, [m(CRAWLER, 3, 2, "Pit Crawler"), m(SPIDER, 2, 2, "Silk Stalker")], radius=7, spread=3, y=1)
    chest(c, near=(5, 21), loot="common")
    c.dress(web=.04, canopy=.12, lights=5, accent=4, sculk=0)
    c.finish()


def cocoon_gallery(tail=True):
    c = chamber("cocoon_gallery", 21, 11, 27, 8.5, 11, nl=3, tail=tail)
    cx, cz = 10, 13
    spawn(c, cx, cz, [webbed(4, 1)], [webbed(2, 2), m(SPIDER, 2, 2, "Silk Stalker")], radius=12, spread=5)
    for z in range(4, 24, 3):
        for x in (4, 16):
            ytop = None
            for y in range(c.h - 2, 1, -1):
                if (x, y, z) in c.air:
                    ytop = y
                    break
            if ytop:
                c.cocoon(x, z, ytop, 4)
    x, y, z, f = chest(c, near=(cx, 3), loot="rare")
    dx, _, dz = D6[f]
    for yy in (1, 2):
        c.place(x + dx, yy, z + dz, "minecraft:cobweb", force=True)
    chest(c, near=(4, 22), loot="common")
    c.arrow_slot(near=(0, 9), radius=11)
    c.arrow_slot(near=(20, 17), radius=11)
    c.sculk_patches(1)
    c.dress(web=.07, canopy=.15, lichen=.03)
    c.finish()


def venom_pools(tail=True):
    c = chamber("venom_pools", 21, 10, 27, 8.5, 11, tail=tail)
    cx, cz = 10, 13
    spawn(c, cx, cz + 6, [m(WITCH, 2, 2, "Venom Witch"), m(CAVE, 4, 1, "Broodling")],
          [m(HAG, 1, 3, "Hag of the Hollow", True), m(SPIDER, 3, 2, "Silk Stalker")], radius=12, spread=4)
    for (px, pz) in ((5, 7), (16, 8), (5, 19), (16, 18)):
        for x in range(px - 4, px + 5):
            for z in range(pz - 4, pz + 5):
                r = math.hypot(x - px, z - pz)
                if (x, 1, z) not in c.air or (x, 1, z) in c.reserved:
                    continue
                if r <= 2.7:
                    c.p.set(x, 0, z, "minecraft:lime_concrete" if (x + z) % 2 else "minecraft:green_concrete")
                    c.used.add((x, 1, z))
                elif r <= 3.7:
                    c.solidify(x, 1, z, "minecraft:moss_block")
        c.p.marker(px, 1, pz, "trap", type="poison", radius=4, every=60, damage=2)
        if (px, 4, pz) in c.air:
            c.place(px, 4, pz, "minecraft:spore_blossom", force=True) if (px, 5, pz) not in c.air else None
    chest(c, near=(cx, 3), loot="rare")
    chest(c, near=(3, 13), loot="common")
    if CUR.n == 2:
        x, y, z, f = chest(c, near=(18, 13), loot="secret")
        dx, _, dz = D6[f]
        c.p.marker(x + dx, y, z + dz, "secret", name="Venom Hollow")
        for yy in (1, 2):
            c.place(x + dx, yy, z + dz, "minecraft:cobweb", force=True) if yy == 2 else None
    c.dress(web=.04, lichen=.07, vines=.05, canopy=.05)
    c.finish()


def collapsed_mine(tail=True):
    c = chamber("collapsed_mine", 21, 10, 27, 8.5, 11, nl=3, tail=tail)
    cx, cz = 10, 13
    spawn(c, cx + 5, cz - 3, [webbed(4, 1)], [m(SPIDER, 3, 2, "Silk Stalker"), m(CAVE, 3, 0, "Broodling")],
          radius=12, spread=4)
    # rails down the middle with a break, supports every 5 blocks, a lonely minecart at the end
    for z in range(23, 5, -1):
        if z in (13, 14) and c.rng.random() < 2:
            continue
        c.solidify(cx, 1, z, "minecraft:rail[shape=north_south]", force=True) if (cx, 1, z) in c.air else None
    c.p.entity(cx, 1, 8, {"id": "minecraft:minecart"})
    for z in range(21, 5, -5):
        for x in (cx - 3, cx + 3):
            for y in range(1, 6):
                c.place(x, y, z, "minecraft:stripped_oak_log[axis=y]" if y < 5 else "minecraft:oak_planks", force=True)
        for x in range(cx - 2, cx + 3):
            c.place(x, 5, z, "minecraft:oak_planks", force=True)
        c.place(cx + 2, 4, z, "minecraft:lantern[hanging=true]", force=True)
    for (px, pz) in ((4, 8), (16, 21), (15, 6)):
        for x in range(px - 2, px + 3):
            for z in range(pz - 2, pz + 3):
                hh = 2 - int(math.hypot(x - px, z - pz) // 1.2)
                for y in range(1, hh + 1):
                    if (x, y, z) in c.air and (x, y, z) not in c.reserved:
                        c.solidify(x, y, z, c.rng.choice(["minecraft:gravel", "minecraft:cobblestone", "minecraft:gravel"]))
    c.ores(18)
    chest(c, near=(4, 20), loot="common", kind="barrel")
    chest(c, near=(16, 5), loot="rare")
    c.arrow_slot(near=(0, 13), radius=11)
    c.dress(web=.06, carpet=.04, lichen=.02, canopy=.1)
    c.finish()


def bone_pit(tail=True):
    c = chamber("bone_pit", 21, 12, 25, 8.5, 10.5, nl=4, tail=tail)
    cx, cz = 10, 11
    spawn(c, cx, cz + 6, [m(CAVE, 4, 0, "Broodling")], [webbed(3, 1), m(SPIDER, 2, 2, "Silk Stalker")],
          [m(SPIDER, 1, 4, "Bone Matriarch", True), m(CRAWLER, 2, 3, "Glimmer Crawler")], radius=10, spread=4)
    for x in range(cx - 6, cx + 7):
        for z in range(cz - 6, cz + 7):
            r = math.hypot(x - cx, z - cz)
            hh = round(3 * (1 - r / 5.8))
            for y in range(1, hh + 1):
                if (x, y, z) in c.air:
                    c.solidify(x, y, z, "minecraft:bone_block" if c.rng.random() < .85 else "minecraft:coarse_dirt", force=True)
            if hh >= 0 and r < 6.2 and (x, hh + 1, z) in c.air and c.rng.random() < .16 and (x, z) != (cx, cz):
                c.place(x, hh + 1, z, "minecraft:skeleton_skull[rotation=%d]" % c.rng.randrange(16), force=True)
    c.p.chest(cx, 4, cz, "south", "skyrpg_world:chests/hollow_rare")
    c.reserved |= {(cx, 4, cz), (cx, 5, cz), (cx, 4, cz + 1)}
    chest(c, near=(4, 18), loot="common")
    c.arrow_slot(near=(0, 11), radius=10)
    c.dress(web=.05, carpet=.03, lichen=.02, bones=.06)
    c.finish()


def plate_puzzle():
    n = CUR.n
    path = f"{P}/f{n}/plate_puzzle"
    c = Cave(path, 17, 8, 19, seed_of(path))
    c.blob(8, 1, 12, 7, 5, 6, .3)
    c.lobes(8, 12, 7, 6, 5, 3)
    c.door("south", "hollow_in", "hollow_in")
    for x in range(5, 12):
        for z in range(5, 8):
            for y in range(1, 5):
                c.carve(x, y, z)
        for z in range(1, 4):
            for y in range(1, 5):
                c.carve(x, y, z)
    for x in range(5, 12):
        for y in range(1, 5):
            c.p.set(x, y, 4, "minecraft:mossy_cobblestone" if 6 <= x <= 10 else "minecraft:stone_bricks")
            c.air.discard((x, y, 4))
            c.reserved.add((x, y, 4))
    c.p.marker(8, 2, 4, "door", group="plates", message="The mossy gate grinds open.")
    c.p.sign(5, 2, 5, ["Three plates", "sleep in the", "webs. Wake", "them all."])
    c.air.discard((5, 2, 5))
    c.reserved.add((5, 2, 5))
    c.p.chest(8, 1, 2, "south", "skyrpg_world:chests/hollow_secret")
    c.p.marker(8, 1, 3, "secret", name="Sleeping Cache")
    c.reserved |= {(8, 1, 2), (8, 2, 2), (8, 1, 3)}
    for pos, b in (((6, 1, 2), "minecraft:gold_block"), ((10, 1, 2), "minecraft:gold_block"),
                   ((6, 2, 2), "minecraft:amethyst_cluster[facing=up]"), ((10, 2, 2), "minecraft:amethyst_cluster[facing=up]"),
                   ((8, 4, 2), "minecraft:soul_lantern[hanging=true]")):
        c.p.set(*pos, b)
        c.reserved.add(pos)
        c.air.discard(pos)
    for (px, pz) in ((2, 15), (14, 15), (14, 7)):
        c.blob(px, 1, pz, 1.8, 1.8, 1.8, .2)
    for (px, pz, sx, sz) in ((2, 15, 1, 0), (14, 15, -1, 0), (14, 7, -1, 0)):
        c.carve(px, 1, pz)
        c.carve(px, 2, pz)
        c.p.set(px, 0, pz, "minecraft:moss_block")
        c.p.set(px, 1, pz, "minecraft:stone_pressure_plate")
        c.air.discard((px, 1, pz))
        c.p.marker(px, 1, pz, "plate", group="plates")
        c.reserved.add((px, 1, pz))
        c.p.set(px, 2, pz, "minecraft:cobweb") if (px, 2, pz) in c.air else None
        c.reserved.add((px, 2, pz))
        for off in (-1, 0, 1):
            xx, zz = px + sx, pz + off
            if (xx, 1, zz) in c.air and c.rng.random() < .8:
                c.p.set(xx, 1, zz, "minecraft:cobweb")
                c.reserved.add((xx, 1, zz))
    c.mounds(1)
    spawn(c, 8, 11, [webbed(3, 1), m(CAVE, 3, 0, "Broodling")], [m(SPIDER, 2, 2, "Silk Stalker")], radius=9, spread=3)
    c.arrow_slot(ylo=2, yhi=3, radius=9, near=(8, 9))
    c.dress(web=.05, lights=2, accent=2, sculk=0)
    c.finish()


def web_maze():
    n = CUR.n
    W = D = 21
    H = 9
    path = f"{P}/f{n}/web_maze"
    c = Cave(path, W, H, D, seed_of(path))
    rng = c.rng
    N = 6
    ceil = {}
    for x in range(2, 19):
        for z in range(2, 20):
            nv = c.noise(x, 0, z)
            ceil[(x, z)] = 5 + (nv > .25) + (nv > .6)
            for y in range(1, ceil[(x, z)] + 1):
                c.carve(x, y, z)
    c.door("south", "hollow_in", "hollow_in")
    open_e = set()
    seen = {(rng.randrange(N), N - 1)}
    stack = [next(iter(seen))]
    while stack:
        i, j = stack[-1]
        nb = [(i + a, j + b) for a, b in ((1, 0), (-1, 0), (0, 1), (0, -1))
              if 0 <= i + a < N and 0 <= j + b < N and (i + a, j + b) not in seen]
        if not nb:
            stack.pop()
            continue
        q = rng.choice(nb)
        seen.add(q)
        open_e.add(frozenset(((i, j), q)))
        stack.append(q)
    for _ in range(5):
        i, j = rng.randrange(N - 1), rng.randrange(N)
        open_e.add(frozenset(((i, j), (i + 1, j))))
    stone = [("minecraft:tuff_bricks", 60), ("minecraft:polished_tuff", 20), ("minecraft:cracked_stone_bricks", 20)]

    def wall(x, z, stoneish):
        top = ceil[(x, z)]
        for y in range(1, top + 1):
            if stoneish:
                c.solidify(x, y, z, wchoice(rng, stone), force=True)
            else:
                c.place(x, y, z, "minecraft:cobweb", force=True)

    for i in range(N):
        for j in range(N):
            if i < N - 1 and frozenset(((i, j), (i + 1, j))) not in open_e:
                s = rng.random() < .22
                for z in (2 + 3 * j, 3 + 3 * j):
                    wall(4 + 3 * i, z, s)
            if j < N - 1 and frozenset(((i, j), (i, j + 1))) not in open_e:
                s = rng.random() < .22
                for x in (2 + 3 * i, 3 + 3 * i):
                    wall(x, 4 + 3 * j, s)
    for i in range(N - 1):
        for j in range(N - 1):
            wall(4 + 3 * i, 4 + 3 * j, True)
    for i in range(N - 1):
        x = 4 + 3 * i
        if abs(x - 10) > 1:
            wall(x, 19, True)
    for x in range(2, 19):
        if abs(x - 10) > 1 and x not in (4, 7, 13, 16) and rng.random() < .55:
            for y in range(1, 4):
                c.place(x, y, 19, "minecraft:cobweb", force=True)
    adj = {}
    for e in open_e:
        a, b = tuple(e)
        adj.setdefault(a, []).append(b)
        adj.setdefault(b, []).append(a)
    dist = {(i, N - 1): 0 for i in range(N)}
    queue = list(dist)
    while queue:
        cur = queue.pop(0)
        for nb in adj.get(cur, []):
            if nb not in dist:
                dist[nb] = dist[cur] + 1
                queue.append(nb)
    leaves = [cl for cl in dist if len(adj.get(cl, [])) == 1 and cl[1] <= 3]
    leaves.sort(key=lambda cl: (-dist[cl], cl))

    def leaf_cache(leaf, loot, secret=None):
        li, lj = leaf
        ni, nj = adj[(li, lj)][0]
        bx, bz = 2 + 3 * li, 2 + 3 * lj
        face = {(0, -1): "north", (0, 1): "south", (-1, 0): "west", (1, 0): "east"}[(ni - li, nj - lj)]
        if face == "north":
            chx, chz, other = bx, bz + 1, (bx + 1, 1, bz + 1)
        elif face == "south":
            chx, chz, other = bx, bz, (bx + 1, 1, bz)
        elif face == "west":
            chx, chz, other = bx + 1, bz, (bx + 1, 1, bz + 1)
        else:
            chx, chz, other = bx, bz, (bx, 1, bz + 1)
        c.air.discard((chx, 1, chz))
        c.p.chest(chx, 1, chz, face, f"skyrpg_world:chests/{loot}")
        c.reserved |= {(chx, 1, chz), (chx, 2, chz), other}
        if secret:
            c.p.marker(other[0], 1, other[2], "secret", name=secret)

    leaf_cache(leaves[0], "hollow_secret", "Silk-Wrapped Cache")
    if len(leaves) > 1:
        leaf_cache(leaves[1], "hollow_common")
    # spikes under the webs in two open cells
    spots = [(2 + 3 * i, 2 + 3 * j) for (i, j) in sorted(dist) if 1 <= j <= 3 and len(adj.get((i, j), [])) >= 2]
    rng.shuffle(spots)
    for (sx, sz) in spots[:2]:
        if (sx, 1, sz) in c.air and (sx, 1, sz) not in c.reserved:
            c.place(sx, 1, sz, "minecraft:pointed_dripstone[thickness=tip,vertical_direction=up]", force=True)
            c.place(sx, 2, sz, "minecraft:cobweb", force=True)
            c.p.marker(sx, 1, sz, "trap", type="spikes", radius=3, every=40, damage=3)
    c.dress(web=.02, carpet=.12, lichen=.03, roots=.05, vines=.02, drip=.01, bones=.012, lights=2, accent=2, sculk=0)
    spawn(c, 10, 18, [webbed(2, 1), m(CAVE, 4, 0, "Broodling")], [m(SPIDER, 2, 1, "Hollow Weaver"), m(CAVE, 2, 0, "Broodling")],
          radius=9, spread=3)
    c.finish()


# ---- narrow passages (the bends live in side branches) --------------------------------------------------------
def passage(name, outs, size):
    n = CUR.n
    w, h, d = size
    path = f"{P}/f{n}/{name}"
    c = Cave(path, w, h, d, seed_of(path))
    cx, cz = w // 2, d // 2
    c.blob(cx, 2, cz, 3.8, 3.4, 3.8, .35)
    c.door("south", "hollow_in", "hollow_in", length=d // 2 + 1)
    c.tunnel_to_center("south")
    for side in outs:
        c.door(side, "hollow_out", "hollow_in", f"{P}/f{n}_tails", 0)
        c.tunnel_to_center(side)
    for _ in range(2):
        c.blob(cx + c.rng.uniform(-3, 3), 1, cz + c.rng.uniform(-3, 3), c.rng.uniform(2, 3), 2.5, c.rng.uniform(2, 3))
    c.reserve_lanes(3)
    spawn(c, cx, cz, *rpack(c), radius=9, spread=3)
    c.arrow_slot(radius=8)
    try:
        chest(c, loot="common")
    except SystemExit:
        pass
    c.dress(web=.05, lights=2, accent=2, sculk=0)
    c.finish()


# ============================================================================================================
# The Queen's Lair
# ============================================================================================================
def lair():
    use(T2)
    W = D = 31
    H = 16
    c = Cave(f"{P}/lair", W, H, D, 6125)
    rng = c.rng
    NOOK, VAULT = (22, 26), (4, 8)
    for x0, x1 in ((20, 28), (2, 10)):
        for x in range(x0, x1 + 1):
            for z in range(1, 6):
                for y in range(1, H - 1):
                    c.nocarve.add((x, y, z))
    c.blob(15, 1, 15, 13.5, H - 3, 12.5, .22)
    c.lobes(15, 15, 10, 10, 8, 5, lr=(2.5, 3.5))
    c.door("south", "hollow_in", "hollow_in", length=8)
    c.reserve_lanes()
    for x0, x1 in (NOOK, VAULT):
        for x in range(x0, x1 + 1):
            for z in range(2, 5):
                for y in range(1, 5):
                    c.carve(x, y, z, force=True)
            for z in range(6, 9):
                for y in range(1, 5):
                    c.carve(x, y, z)
    for x in range(22, 27):
        for y in range(1, 5):
            c.p.set(x, y, 5, "minecraft:mossy_stone_bricks" if 23 <= x <= 25 and y <= 3 else wchoice(rng, ROCK2[:3]))
            c.air.discard((x, y, 5))
            c.reserved.add((x, y, 5))
    for x in range(4, 9):
        for y in range(1, 5):
            c.p.set(x, y, 5, "minecraft:chiseled_tuff" if 5 <= x <= 7 and y <= 3 else wchoice(rng, ROCK2[:3]))
            c.air.discard((x, y, 5))
            c.reserved.add((x, y, 5))
    c.p.marker(24, 2, 5, "door", group="queen", message="The silk wall unravels.")
    c.p.marker(6, 2, 5, "key_door", key="skyrpg_world:hollow_key", message="The vault is sealed. It needs a Hollow Key.")
    # vault lining + treasure
    for x in range(3, 10):
        for z in range(1, 5):
            for y in range(0, 6):
                edge = x in (3, 9) or z == 1 or y in (0, 5)
                if edge and c.p.get(x, y, z) in ROCK_IDS | {"minecraft:sculk"}:
                    c.p.set(x, y, z, "minecraft:polished_deepslate")
    c.p.chest(6, 1, 2, "south", "skyrpg_world:chests/hollow_vault")
    for pos, b in (((5, 1, 2), "minecraft:gold_block"), ((7, 1, 2), "minecraft:gold_block"),
                   ((4, 1, 2), "minecraft:amethyst_cluster[facing=up]"), ((8, 1, 2), "minecraft:amethyst_cluster[facing=up]"),
                   ((6, 4, 2), "minecraft:soul_lantern[hanging=true]"), ((5, 2, 2), "minecraft:amethyst_cluster[facing=up]"),
                   ((7, 2, 2), "minecraft:amethyst_cluster[facing=up]")):
        c.p.set(*pos, b)
    c.p.chest(24, 1, 2, "south", "skyrpg_world:chests/hollow_secret")
    c.p.marker(24, 1, 3, "secret", name="Queen's Larder")
    c.p.set(22, 1, 2, "minecraft:white_wool")
    c.p.set(26, 1, 2, "minecraft:white_wool")
    c.p.set(24, 4, 2, "minecraft:soul_lantern[hanging=true]")
    for x0, x1 in (NOOK, VAULT):
        for x in range(x0, x1 + 1):
            for z in range(2, 5):
                for y in range(1, 5):
                    c.reserved.add((x, y, z))
                    c.air.discard((x, y, z))
    # features
    for x in range(10, 21):
        for z in range(10, 21):
            for y in range(1, 4):
                c.reserved.add((x, y, z))
    for k in range(8):
        a = k * math.pi / 4 + .3
        c.column(round(15 + math.cos(a) * 10), round(15 + math.sin(a) * 10))
    for cx_, cz_, r in ((11, 8, 2), (19, 8, 2), (15, 7, 2), (6, 22, 2), (25, 22, 2)):
        c.sac(cx_, 1, cz_, r)
    c.cocoons(18, lo=7)
    c.spike_patch(9, 14, 1)
    c.spike_patch(22, 17, 1)
    c.arrow_slot(near=(3, 15), radius=14)
    c.arrow_slot(near=(27, 15), radius=14)
    c.dress(canopy=.28, lights=16, accent=26, sculk=6, roots=.06, vines=.015)
    for pos in sorted(c.air):
        x, y, z = pos
        if pos in c.used or pos in c.reserved or y < 6:
            continue
        up = 0
        while (x, y + up + 1, z) in c.air:
            up += 1
        if up <= 2 and c.solid(x, y + up + 1, z) and rng.random() < (.6, .4, .22)[up]:
            c.place(x, y, z, "minecraft:cobweb")
    c.p.marker(15, 1, 15, "boss", boss="skyrpg_world:queen_arachne", radius=15, group="queen")
    c.finish()


def cap():
    p = Piece(f"{P}/cap", 3, 4, 1)
    p.fill(0, 0, 0, 2, 3, 0, "minecraft:stone")
    p.jigsaw(1, 1, 0, "north_up", "hollow_in", "hollow_in", "minecraft:empty", final_state="minecraft:stone")
    PK.add(p)


# ============================================================================================================
# Loot
# ============================================================================================================
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


def potion(w, pid):
    return it("minecraft:potion", w, fn=[{"function": "minecraft:set_potion", "id": pid}])


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


def loot():
    S = "skyrpg_"
    GEAR = [it("skyrpg_items:arachne_fang", 1), it("skyrpg_items:widowsilk_cloak", 1)]
    common = [it("minecraft:string", 9, 3, 8), it("minecraft:spider_eye", 7, 2, 4), it("minecraft:cobweb", 5, 3, 6),
              it("minecraft:gold_ingot", 7, 2, 6), it("minecraft:emerald", 6, 2, 5), it("minecraft:diamond", 2, 1, 2),
              it("minecraft:amethyst_shard", 5, 3, 8), it("minecraft:experience_bottle", 5, 2, 5),
              it("minecraft:iron_ingot", 4, 3, 7), it("minecraft:golden_carrot", 5, 3, 6),
              it("skyrpg_enchants:runic_dust", 8, 2, 5), it("skyrpg_combat:brood_silk", 6, 2, 4),
              it("skyrpg_combat:venom_gland", 3, 1, 2), it("skyrpg_mining:glimmer_shard", 6, 2, 5),
              it("skyrpg_mining:glimmer_crystal", 2, 1, 1), it("skyrpg_mining:skyiron_ingot", 3, 1, 3),
              it("skyrpg_combat:wisp_essence", 3, 1, 2), it("skyrpg_combat:hags_charm", 1, 1, 1),
              book(3), potion(2, "minecraft:poison"), potion(2, "minecraft:regeneration")]
    PK.loot_table("chests/hollow_common", table(
        lpool(rolls(4, 6), common),
        lpool(1, [it("skyrpg_combat:valor_mark", 3, 1, 1), it("skyrpg_combat:dread_fang", 2, 1, 1),
                  it("skyrpg_farming:lucky_clover", 1, 1, 1)], .22)))
    rare = [it("minecraft:diamond", 6, 2, 4), it("minecraft:emerald", 7, 4, 9), it("minecraft:gold_ingot", 8, 5, 12),
            it("minecraft:experience_bottle", 6, 4, 9), it("minecraft:amethyst_shard", 4, 6, 12),
            it("skyrpg_enchants:runic_dust", 10, 4, 9), it("skyrpg_combat:brood_silk", 7, 3, 6),
            it("skyrpg_combat:venom_gland", 5, 1, 3), it("skyrpg_combat:wisp_essence", 5, 2, 4),
            it("skyrpg_mining:glimmer_shard", 7, 4, 9), it("skyrpg_mining:glimmer_crystal", 5, 1, 3),
            it("skyrpg_mining:prism_fragment", 4, 1, 3), it("skyrpg_mining:skyiron_ingot", 5, 2, 5),
            it("skyrpg_combat:hags_charm", 2, 1, 2), book(5), potion(3, "minecraft:strong_healing")]
    PK.loot_table("chests/hollow_rare", table(
        lpool(rolls(5, 7), rare),
        lpool(1, [it("skyrpg_combat:elite_trophy", 3, 1, 1), it("skyrpg_combat:valor_mark", 4, 1, 2),
                  it("skyrpg_combat:dread_fang", 3, 1, 2), it("skyrpg_mining:heartstone", 2, 1, 1),
                  it("skyrpg_mining:emberite_ingot", 2, 1, 2)], .55),
        lpool(1, [it("skyrpg_combat:nightmare_core", 1, 1, 1)], .05),
        lpool(1, [it("skyrpg_pets:owl_egg", 1, 1, 1), it("skyrpg_pets:bee_egg", 1, 1, 1)], .03)))
    secret = [it("minecraft:diamond", 7, 2, 5), it("minecraft:emerald", 8, 5, 12), it("minecraft:gold_ingot", 8, 6, 14),
              it("minecraft:experience_bottle", 6, 5, 10), it("skyrpg_enchants:runic_dust", 10, 5, 11),
              it("skyrpg_combat:brood_silk", 6, 4, 8), it("skyrpg_combat:venom_gland", 5, 2, 4),
              it("skyrpg_mining:glimmer_crystal", 6, 2, 4), it("skyrpg_mining:prism_fragment", 6, 2, 4),
              it("skyrpg_mining:emberite_ingot", 4, 1, 3), it("skyrpg_mining:skyiron_ingot", 5, 3, 6),
              it("skyrpg_combat:wisp_essence", 5, 3, 6), book(6), potion(3, "minecraft:regeneration")]
    PK.loot_table("chests/hollow_secret", table(
        lpool(rolls(5, 8), secret),
        lpool(1, [it("skyrpg_mining:prism", 3, 1, 1), it("skyrpg_mining:heartstone", 3, 1, 2),
                  it("skyrpg_mining:attuned_lumenite", 3, 1, 2), it("skyrpg_mining:lumenite_crystal", 3, 1, 2),
                  it("skyrpg_foraging:golden_acorn", 3, 1, 2), it("skyrpg_farming:lucky_clover", 3, 1, 2),
                  it("skyrpg_farming:verdant_heart", 2, 1, 1), it("skyrpg_combat:valor_seal", 3, 1, 1),
                  it("skyrpg_combat:elite_trophy", 3, 1, 2)]),
        lpool(1, [it("skyrpg_combat:nightmare_core", 1, 1, 1)], .08),
        lpool(1, [it("skyrpg_pets:owl_egg", 1, 1, 1), it("skyrpg_pets:bee_egg", 1, 1, 1)], .06),
        lpool(1, [it("skyrpg_pets:golden_pet_egg", 1, 1, 1)], .01)))
    vault = [it("minecraft:diamond", 8, 3, 7), it("minecraft:emerald", 8, 8, 16), it("minecraft:gold_ingot", 8, 8, 18),
             it("minecraft:experience_bottle", 6, 6, 12), it("minecraft:amethyst_shard", 5, 8, 16),
             it("skyrpg_enchants:runic_dust", 10, 6, 14), it("skyrpg_combat:brood_silk", 8, 5, 10),
             it("skyrpg_combat:wisp_essence", 6, 4, 8), it("skyrpg_mining:glimmer_crystal", 7, 3, 6),
             it("skyrpg_mining:prism_fragment", 7, 3, 6), it("skyrpg_mining:prism", 4, 1, 2),
             it("skyrpg_mining:skyiron_ingot", 6, 4, 8), it("skyrpg_mining:emberite_ingot", 5, 2, 4),
             it("skyrpg_mining:heartstone", 4, 1, 2), it("skyrpg_mining:attuned_lumenite", 3, 1, 2),
             it("skyrpg_mining:lumenite_crystal", 3, 1, 2), it("skyrpg_combat:valor_seal", 4, 1, 2),
             it("skyrpg_combat:elite_trophy", 5, 1, 3), it("skyrpg_combat:dread_fang", 4, 1, 2),
             it("skyrpg_foraging:golden_acorn", 3, 1, 2), it("skyrpg_farming:verdant_heart", 2, 1, 1), book(8),
             potion(4, "minecraft:strong_regeneration")]
    PK.loot_table("chests/hollow_vault", table(
        lpool(rolls(6, 8), vault),
        lpool(1, [it("skyrpg_world:arachne_eye", 1, 2, 3)]),
        lpool(1, [it("skyrpg_combat:venom_gland", 1, 2, 4)]),
        lpool(1, [it("skyrpg_combat:nightmare_core", 1, 1, 1)], .12),
        lpool(1, [it("skyrpg_pets:golden_pet_egg", 1, 1, 1)], .02),
        lpool(1, GEAR, .20),
        lpool(1, [it("minecraft:enchanted_golden_apple", 1, 1, 1)], .15)))
    PK.loot_table("bosses/hollow_champion", table(
        lpool(1, [it("skyrpg_combat:brood_silk", 1, 3, 6)]),
        lpool(1, [it("skyrpg_combat:venom_gland", 1, 1, 2)], .8),
        lpool(rolls(2, 4), [it("minecraft:gold_ingot", 8, 4, 10), it("minecraft:emerald", 8, 3, 8),
                            it("minecraft:diamond", 4, 1, 3), it("skyrpg_enchants:runic_dust", 9, 3, 7),
                            it("skyrpg_mining:glimmer_crystal", 5, 1, 3), it("skyrpg_mining:glimmer_shard", 6, 3, 8),
                            it("skyrpg_combat:wisp_essence", 4, 2, 4), it("minecraft:experience_bottle", 6, 4, 9),
                            book(4)]),
        lpool(1, [it("skyrpg_combat:elite_trophy", 1, 1, 1)], .35),
        lpool(1, [it("skyrpg_combat:valor_mark", 1, 1, 2)], .30),
        lpool(1, [it("skyrpg_combat:dread_fang", 1, 1, 1)], .15),
        lpool(1, [it("skyrpg_mining:prism_fragment", 1, 1, 2)], .20),
        lpool(1, [it("skyrpg_combat:nightmare_core", 1, 1, 1)], .06),
        lpool(1, [it("skyrpg_pets:owl_egg", 1, 1, 1), it("skyrpg_pets:bee_egg", 1, 1, 1)], .04)))
    map_fn = [{"function": "minecraft:exploration_map", "destination": "skyrpg_world:on_temple_maps",
               "decoration": "minecraft:red_x", "zoom": 2, "skip_existing_chunks": False},
              {"function": "minecraft:set_name", "target": "item_name", "name": "Drowned Temple Map"}]
    PK.loot_table("bosses/queen_arachne", table(
        lpool(1, [it("skyrpg_world:arachne_eye", 1, 2, 4)]),
        lpool(1, [it("skyrpg_world:hollow_key", 1)]),
        lpool(1, [it("skyrpg_items:arachne_fang", 1)], .15),
        lpool(1, [it("skyrpg_items:widowsilk_cloak", 1)], .15),
        lpool(1, [it("minecraft:map", 1, fn=map_fn)], .40),
        lpool(rolls(5, 7), [it("minecraft:gold_ingot", 8, 8, 18), it("minecraft:emerald", 8, 6, 14),
                            it("minecraft:diamond", 5, 2, 6), it("skyrpg_enchants:runic_dust", 9, 5, 12),
                            it("skyrpg_combat:brood_silk", 8, 4, 9), it("skyrpg_combat:venom_gland", 6, 2, 4),
                            it("skyrpg_combat:wisp_essence", 6, 3, 7), it("skyrpg_mining:glimmer_crystal", 6, 2, 5),
                            it("skyrpg_mining:prism_fragment", 6, 2, 5), it("skyrpg_mining:skyiron_ingot", 5, 3, 7),
                            it("skyrpg_mining:emberite_ingot", 4, 2, 4), it("minecraft:experience_bottle", 7, 8, 16),
                            book(5)]),
        lpool(1, [it("skyrpg_combat:valor_seal", 1, 1, 2)], .50),
        lpool(1, [it("skyrpg_combat:elite_trophy", 1, 1, 2)], .60),
        lpool(1, [it("skyrpg_combat:nightmare_core", 1, 1, 1)], .25),
        lpool(1, [it("skyrpg_pets:golden_pet_egg", 1, 1, 1)], .03)))
    PK.loot_table("rewards/queen_arachne", table(
        lpool(rolls(5, 7), [it("skyrpg_enchants:runic_dust", 10, 6, 14), it("minecraft:diamond", 7, 3, 8),
                            it("minecraft:emerald", 8, 8, 18), it("minecraft:experience_bottle", 7, 8, 16),
                            it("skyrpg_mining:glimmer_crystal", 7, 3, 6), it("skyrpg_mining:prism_fragment", 7, 3, 6),
                            it("skyrpg_mining:prism", 4, 1, 2), it("skyrpg_mining:skyiron_ingot", 6, 4, 9),
                            it("skyrpg_mining:emberite_ingot", 6, 2, 5), it("skyrpg_mining:heartstone", 4, 1, 2),
                            it("skyrpg_mining:attuned_lumenite", 4, 1, 3), it("skyrpg_mining:lumenite_crystal", 4, 1, 3),
                            it("skyrpg_combat:wisp_essence", 6, 4, 8), it("skyrpg_combat:venom_gland", 5, 2, 5),
                            it("skyrpg_combat:valor_seal", 5, 1, 2), it("skyrpg_combat:elite_trophy", 5, 1, 3),
                            it("skyrpg_foraging:golden_acorn", 4, 1, 3), it("skyrpg_farming:lucky_clover", 4, 1, 3),
                            it("skyrpg_farming:verdant_heart", 3, 1, 2), book(6)]),
        lpool(1, [it("skyrpg_combat:nightmare_core", 1, 1, 1)], .20),
        lpool(1, [it("skyrpg_combat:dread_fang", 1, 1, 2)], .35),
        lpool(1, [it("skyrpg_pets:golden_pet_egg", 1, 1, 1)], .04),
        lpool(1, [it("skyrpg_pets:owl_egg", 1, 1, 1), it("skyrpg_pets:bee_egg", 1, 1, 1)], .10),
        lpool(1, GEAR, .06)))


def bosses():
    PK.json("skyrpg/dungeon_bosses/hollow_champion.json", {
        "name": "The Silk Matron", "entity": "minecraft:spider", "dungeon": DUN, "tier": "champion", "level": 23,
        "health": 2400, "damage": 12, "speed": 0.33, "scale": 1.7, "knockback_resistance": 0.4,
        "bar_color": "green", "health_per_player": 0.6, "coins": [1500, 2200], "delving_xp": 650,
        "intro": "The Silk Matron unfurls from the dark, her brood chittering.",
        "death": "The Matron shudders and the silk plug unravels.",
        "max_minions": 6, "loot": "skyrpg_world:bosses/hollow_champion", "reset_ticks": 72000,
        "moves": [
            {"type": "webs", "every": 200, "radius": 4, "count": 8, "duration": 100,
             "warn": "Silk bursts from the dark around you!"},
            {"type": "leap", "every": 250, "damage": 12, "radius": 3.2, "range": 16, "warn": "The Matron coils to pounce!"},
            {"type": "summon", "every": 380, "max": 6,
             "mobs": [{"type": "minecraft:cave_spider", "count": 3, "level": 21, "name": "Broodling"}],
             "warn": "Broodlings pour from her abdomen!"}]})
    PK.json("skyrpg/dungeon_bosses/queen_arachne.json", {
        "name": "Queen Arachne", "entity": "minecraft:spider", "dungeon": DUN, "level": 26, "health": 8000, "damage": 18,
        "speed": 0.32, "scale": 2.4, "knockback_resistance": 0.6, "bar_color": "green", "health_per_player": 0.6,
        "coins": [6000, 8000], "delving_xp": 2800,
        "intro": "You dare tread upon my silk? Feed, my children!",
        "death": "The Queen collapses, her web-song falling silent.",
        "max_minions": 10, "loot": "skyrpg_world:bosses/queen_arachne",
        "reward_loot": "skyrpg_world:rewards/queen_arachne",
        "first_clear": {"coins": 9000, "xp": {"skyrpg_skills:combat": 3000}},
        "reset_ticks": 72000,
        "phases": [{"below": 0.66, "message": "The Queen screeches and the hollow shakes. Her silk-sisters answer!"},
                   {"below": 0.33, "message": "The Queen's eyes blaze. She fights with nothing left to lose!"}],
        "moves": [
            {"type": "webs", "every": 200, "radius": 4, "count": 8, "duration": 100,
             "warn": "Silk bursts from the dark around you!"},
            {"type": "volley", "every": 170, "projectile": "web", "count": 4, "damage": 8, "spread": 0.18,
             "warn": "Queen Arachne rears back to spit webs!"},
            {"type": "leap", "every": 260, "damage": 15, "radius": 3.5, "range": 18,
             "warn": "The Queen coils to pounce!"},
            {"type": "summon", "every": 420, "max": 10,
             "mobs": [{"type": "minecraft:cave_spider", "count": 4, "level": 23, "name": "Broodling"},
                      {"type": "minecraft:spider", "count": 1, "level": 24, "name": "Hollow Weaver"}],
             "warn": "The cocoons burst open!"},
            {"type": "cloud", "every": 300, "effect": "minecraft:poison", "amplifier": 1, "duration": 120, "radius": 3.5,
             "on": "target", "warn": "Venom pools beneath you!"},
            {"type": "pull", "every": 360, "below": 0.66, "radius": 14, "strength": 1.0,
             "warn": "The Queen drags you into her web!"},
            {"type": "shockwave", "every": 300, "below": 0.33, "radius": 8, "damage": 12, "push": 1.6,
             "warn": "The Queen stomps, the cavern quakes!"},
            {"type": "enrage", "below": 0.33, "speed": 0.42, "damage": 0.4,
             "warn": "Queen Arachne screeches, her eyes blazing!"}]})


# ============================================================================================================
def build_chambers(floor):
    use(T1 if floor == 1 else T2)
    n = floor
    full, ends = [], []
    if floor == 1:
        specs = [("egg_chamber", egg_chamber, 2, True), ("glimmer_grotto", glimmer_grotto, 2, True),
                 ("brood_nest", brood_nest, 2, True), ("mushroom_grotto", mushroom_grotto, 3, True),
                 ("underground_lake", underground_lake, 3, True), ("silk_bridges", silk_bridges, 3, True),
                 ("cocoon_gallery", cocoon_gallery, 3, True), ("venom_pools", venom_pools, 3, True),
                 ("collapsed_mine", collapsed_mine, 3, True), ("bone_pit", bone_pit, 3, True)]
    else:
        specs = [("egg_chamber", egg_chamber, 3, True), ("glimmer_grotto", glimmer_grotto, 2, True),
                 ("brood_nest", brood_nest, 3, True), ("silk_bridges", silk_bridges, 3, True),
                 ("cocoon_gallery", cocoon_gallery, 3, True), ("venom_pools", venom_pools, 3, True),
                 ("collapsed_mine", collapsed_mine, 3, True), ("bone_pit", bone_pit, 3, True)]
    for name, fn, wgt, _ in specs:
        fn(tail=True)
        full.append((f"{P}/f{n}/{name}", wgt))
        fn(tail=False)
        ends.append((f"{P}/f{n}/{name}_end", 1))
    if floor == 1:
        web_maze()
        plate_puzzle()
        full += [(f"{P}/f1/web_maze", 3), (f"{P}/f1/plate_puzzle", 3)]
    passage("passage_straight", ("north",), (9, 7, 15))
    passage("passage_bend_w", ("west",), (11, 7, 11))
    passage("passage_bend_e", ("east",), (11, 7, 11))
    full += [(f"{P}/f{n}/passage_straight", 2), (f"{P}/f{n}/passage_bend_w", 2), (f"{P}/f{n}/passage_bend_e", 2)]
    caps = f"{P}/caps"
    PK.pool(f"{P}/f{n}_chambers", full, fallback=caps)
    PK.pool(f"{P}/f{n}_tails", ends + [(f"{P}/cap", 2)], fallback=caps)


def build(pack):
    global PK
    PK = pack
    build_entrance()
    build_halls(1)
    champion_nest()
    shaft()
    build_chambers(1)
    build_halls(2)
    lair()
    build_chambers(2)
    cap()
    caps = f"{P}/caps"
    pack.pool(f"{P}/entrance", [(f"{P}/entrance", 1)], fallback=caps)
    pack.pool(f"{P}/champion", [(f"{P}/champion", 1)], fallback=caps)
    pack.pool(f"{P}/shaft", [(f"{P}/shaft", 1)], fallback=caps)
    pack.pool(f"{P}/lair", [(f"{P}/lair", 1)], fallback=caps)
    pack.pool(caps, [(f"{P}/cap", 1)])
    pack.biome_tag("spider_hollow", ["minecraft:dark_forest", "minecraft:pale_garden"])
    pack.structure("spider_hollow", start_pool=f"{P}/entrance", biomes="#skyrpg_world:has_structure/spider_hollow",
                   size=16, max_distance=116, step="underground_structures", adaptation="beard_thin",
                   heightmap="WORLD_SURFACE_WG", start_height=-K)
    pack.structure_set("spider_hollow", 18, 6, 61201712, exclusion=("forgotten_crypt", 10))
    pack.structure_tag("on_hollow_maps", ["spider_hollow"])
    loot()
    pack.json("skyrpg/dungeons/spider_hollow.json", {
        "key": "skyrpg_world:hollow_key",
        "name": "Spider Hollow", "structure": DUN, "color": "#5FBF4A", "icon": "minecraft:cobweb", "level": 22,
        "boss": "skyrpg_world:queen_arachne", "reset_ticks": 72000,
        "description": "A two-floor web-choked nest beneath the dark forest, ruled by Queen Arachne."})
    bosses()
    if PROBLEMS:
        raise SystemExit("Cave problems:\n  " + "\n  ".join(PROBLEMS))


def report(pack):
    from collections import Counter
    print("\npieces:")
    kinds = {"f1": Counter(), "f2": Counter(), "other": Counter()}
    for path, pc in sorted(pack.pieces.items()):
        fl = "f1" if "/f1/" in path or path.endswith(("entrance", "champion", "shaft")) else \
            "f2" if "/f2/" in path or path.endswith("lair") else "other"
        wv = 0
        for e in pc.entities:
            d = e["nbt"].get("data", {})
            kinds[fl][d.get("skyrpg")] += 1
            if d.get("skyrpg") == "spawner" and "waves" in d:
                wv += 1
        print(f"  {path:48s} {pc.size[0]}x{pc.size[1]}x{pc.size[2]}  waves={wv}")
    print("\nmarkers by kind per floor:")
    for fl, cn in kinds.items():
        print(" ", fl, dict(cn))
    print("pools:")
    for pid, pool in sorted(pack.pools.items()):
        print(f"  {pid}: {len(pool['elements'])} elements, fallback {pool['fallback']}")


if __name__ == "__main__":
    pack = Pack()
    build(pack)
    pack.save(clean=["spider_hollow"])
    report(pack)
