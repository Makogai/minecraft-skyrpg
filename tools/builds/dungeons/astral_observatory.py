"""The Astral Observatory (skyrpg_world:astral_observatory): a two-floor dungeon on the outer End islands (docs/design/end_game.md).
Mob levels 60-65, champion Lv 62 (the Astral Sentinel), boss Lv 64 (the Star Warden). Built like the Infernal Citadel
(tools/builds/dungeons/infernal_citadel.py, which this imports: the Room helper, loot helpers and the offline checks), with
its own palette, rooms, mobs and loot.

Layout (jigsaw, size 16, max_distance 116). In the End there is no surface worth projecting to, so the start piece is placed
at an absolute height (start_height 64) with no heightmap projection and no terrain adaptation. Every piece has its walkable
floor layer at local y = FD (4) and doorways 3x3 from y = FD + 1, so the floors line up; under a floor-1 piece are stilts
(purpur pillars) that stand on the island or hang in the void, under a floor-2 piece a thin hull (it floats).

  FLOOR 1  "The Lower Halls" (Lv 60-63), along axis A from the entrance:
           the Courtyard (start piece: a terrace on stilts with a waystone and the dungeon marker, a gate, an atrium under a
           glass roof) -> f1_s1 -> f1_s2 -> f1_s3 (spine halls, 17 deep: halls of charts with star-map murals, archives,
           glass galleries, sentinel quarters) -> the Orrery Hall (champion, bar plug) -> the Ascent (a tower with two
           switchback flights, 20 UP; exits on its EAST wall, a 90 degree turn).
  FLOOR 2  "The Star Deck" (Lv 63-65), perpendicular to floor 1, 20 above:
           f2_s1 -> f2_s2 -> f2_s3 (the same halls, floating) -> the Star Warden's Dome (35 x 28 x 35, boss, a key-door vault and
           a hoard nook, a great telescope).
  Side rooms hang off every spine hall (floor 1: both sides; floor 2: only the side that faces back towards the entrance,
  the far side only gets leaves, to stay inside the 116 block box): telescope rooms, map rooms, lens workshops, astronomers'
  quarters (with a hidden cache). Everything falls back to 1-thick caps.

Run:  python tools/builds/dungeons/astral_observatory.py     (add --show <piece path> <y> to print a slice of a piece)
Offline checks (all run on every build): jigsaw validation, every block id exists, every door and marker is reachable (walk
test with door markers opened), pack sizes, loot item ids, no corner pieces in spine pools, spine reach table (<= 110).
"""
import math
import pathlib
import random
import sys
from collections import Counter

HERE = pathlib.Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))
sys.path.insert(0, str(HERE.parent))
import infernal_citadel as ic  # noqa: E402
from dungeon_kit import Pack, Piece  # noqa: E402

CZ = "astral_observatory"
DUN = "skyrpg_world:astral_observatory"
AIR = "minecraft:air"
FD = ic.FD
G = ic.G
START_HEIGHT = 64
DROP = 20          # the ascent's rise

# -- blocks -------------------------------------------------------------------------------------------------------------
ESB, ES = "minecraft:end_stone_bricks", "minecraft:end_stone"
PUR, PILLAR = "minecraft:purpur_block", "minecraft:purpur_pillar[axis=y]"
QUARTZ_C = "minecraft:chiseled_quartz_block"
GLASS, PANE = "minecraft:glass", "minecraft:glass_pane"
ENDROD = "minecraft:end_rod[facing=up]"
LANTERN = "minecraft:sea_lantern"
ESB_WALL = "minecraft:end_stone_brick_wall"
COPPER = "minecraft:copper_block"
BLUE, WHITE = "minecraft:blue_concrete", "minecraft:white_concrete"
KEY_DOOR = "minecraft:obsidian"
NOOK_DOOR = "minecraft:crying_obsidian"

PAL = {
    1: ic.Pal(ESB, PUR, PILLAR, PILLAR, QUARTZ_C,
              {ESB: [(ES, .12), (PUR, .03)], PUR: [(ESB, .06)]},
              "minecraft:soul_lantern", "minecraft:warped_planks", "minecraft:purple_carpet"),
    2: ic.Pal(PUR, ESB, PILLAR, PILLAR, QUARTZ_C,
              {PUR: [(ESB, .10), (ES, .04)], ESB: [(ES, .10), (PUR, .04)]},
              "minecraft:soul_lantern", "minecraft:warped_planks", "minecraft:blue_carpet"),
}
DOORS = {"spine_in": ("obs_spine_in", "obs_spine_in", None, 0),
         "spine_out": ("obs_spine_out", "obs_spine_in", "pool", 10),
         "side_in": ("obs_side_in", "obs_side_in", None, 0),
         "side_out": ("obs_side_out", "obs_side_in", "side", 0),
         "tail_in": ("obs_tail_in", "obs_tail_in", None, 0),
         "leaf_out": ("obs_leaf_out", "obs_tail_in", "tail", 0),
         "tail_out": ("obs_tail_out", "obs_tail_in", "tail", 0)}

# the citadel module's helpers read these module globals
ic.CZ = CZ
ic.PAL = PAL
ic.DOORS = DOORS
ic.SIDE_POOL = {1: f"{CZ}/f1_rooms", 2: f"{CZ}/f2_rooms"}
ic.TAIL_POOL = {1: f"{CZ}/f1_leaves", 2: f"{CZ}/f2_leaves"}
ic.EXEMPT.clear()
EXEMPT = ic.EXEMPT


# ---------------------------------------------------------------------------------------------------------------------
# Mobs
# ---------------------------------------------------------------------------------------------------------------------
def Lv(fl, d=0):
    return (60 if fl == 1 else 63) + d


mob, total = ic.mob, ic.total


def acolyte(n, lvl, name="Astral Acolyte", elite=False):
    return mob("minecraft:enderman", n, lvl, name, elite)


def mite(n, lvl, name="Void Mite"):
    return mob("minecraft:endermite", n, lvl, name)


def sentinel(n, lvl, name="Star Sentinel"):
    return mob("minecraft:shulker", n, lvl, name)


def astronomer(n, lvl, name="Mad Astronomer", elite=False):
    return mob("minecraft:witch", n, lvl, name, elite)


def seer(n, lvl, name="Star Seer", elite=True):
    return mob("minecraft:evoker", n, lvl, name, elite)


# ---------------------------------------------------------------------------------------------------------------------
# The room helper: the citadel's Room with the observatory's stilts, lights and decor
# ---------------------------------------------------------------------------------------------------------------------
class R(ic.Room):
    def pilings(self, x1, z1, x2, z2):
        """Floor 1: stilts of purpur pillars under the corners and the middle of the long sides. Floor 2 floats on a thin hull."""
        p = self.p
        if self.fl == 2:
            for x in range(x1, x2 + 1):
                for z in (z1, z2):
                    p.set(x, FD - 1, z, self.pal.trim)
            for z in range(z1, z2 + 1):
                for x in (x1, x2):
                    p.set(x, FD - 1, z, self.pal.trim)
            for x, z in ((x1, z1), (x2, z1), (x1, z2), (x2, z2)):
                p.set(x, FD - 2, z, self.pal.cap)
                p.set(x, FD - 3, z, ENDROD.replace("facing=up", "facing=down"))
            return
        zs = (z1, (z1 + z2) // 2 - 1, z2 - 2)
        for xa in (x1, x2 - 2):
            for za in zs:
                for x in range(xa, xa + 3):
                    for z in range(za, za + 3):
                        for y in range(0, FD):
                            p.set(x, y, z, ESB if (x + y + z) % 5 else PILLAR)
        cx = (x1 + x2) // 2
        for za in (z1, z2 - 2):
            for x in range(cx - 1, cx + 2):
                for z in range(za, za + 3):
                    for y in range(0, FD):
                        p.set(x, y, z, ESB)

    def chest(self, x, z, facing, tier="common", y=G, block="minecraft:chest"):
        self.p.chest(x, y, z, facing, f"skyrpg_world:chests/observatory_{tier}", block=block, tier=tier)
        self.keep.add((x, z))

    def col(self, x, z, y1=None):
        top = (self.h - 3) if y1 is None else y1
        for y in range(G, top + 1):
            self.set(x, y, z, self.pal.trim)
        self.set(x, G, z, self.pal.cap)
        self.set(x, top, z, self.pal.cap)
        if top >= G + 5:
            self.set(x, G + 3, z, LANTERN)
        self.keep.add((x, z))

    def brazier(self, x, z):
        """An end rod on a purpur pillar."""
        if not self.free(x, z):
            return
        self.set(x, G, z, PILLAR)
        self.set(x, G + 1, z, ENDROD)
        self.keep.add((x, z))

    def sign(self, x, y, z, lines, facing):
        self.p.sign(x, y, z, lines, block=f"minecraft:warped_wall_sign[facing={facing}]", color="white", glow=True)

    def mural(self, x, z1, z2, y1=None, y2=None):
        """A star map on the wall: blue concrete sky, white and sea-lantern stars (laid against the wall at x)."""
        y1 = G + 1 if y1 is None else y1
        y2 = G + 4 if y2 is None else y2
        for z in range(z1, z2 + 1):
            for y in range(y1, y2 + 1):
                if (x, z) in self.keep or self.get(x, y, z) != AIR:
                    continue
                roll = self.rng.random()
                self.set(x, y, z, LANTERN if roll < .07 else WHITE if roll < .22 else BLUE)
        self.keep |= {(x, z) for z in range(z1, z2 + 1)}

    def skylight(self, x1, z1, x2, z2):
        """Glass in the roof (the topmost layer)."""
        for x in range(x1, x2 + 1):
            for z in range(z1, z2 + 1):
                if self.get(x, self.h - 1, z) not in (None, AIR):
                    self.set(x, self.h - 1, z, GLASS)

    def window(self, side, c1, c2, y1, y2):
        """Glass panes in a wall (between the pilasters), side = north/south/west/east, c1..c2 along it."""
        for c in range(c1, c2 + 1):
            for y in range(y1, y2 + 1):
                x, z = {"west": (0, c), "east": (self.w - 1, c), "north": (c, 0), "south": (c, self.d - 1)}[side]
                if self.get(x, y, z) == self.pal.wall:
                    self.set(x, y, z, PANE)

    def desk(self, x, z):
        if self.free(x, z):
            self.set(x, G, z, "minecraft:cartography_table")
            self.keep.add((x, z))

    def lectern(self, x, z, facing):
        if self.free(x, z):
            self.set(x, G, z, f"minecraft:lectern[facing={facing}]")
            self.keep.add((x, z))

    def shelves(self, x, z1, z2, height=4):
        for z in range(z1, z2 + 1):
            for y in range(G, G + height):
                if (x, z) not in self.keep and self.get(x, y, z) == AIR:
                    self.set(x, y, z, "minecraft:bookshelf")
        self.keep |= {(x, z) for z in range(z1, z2 + 1)}

    def stars(self, n, x1, z1, x2, z2):
        """End rods stuck in the floor here and there (small lights)."""
        for _ in range(n):
            x, z = self.rng.randint(x1, x2), self.rng.randint(z1, z2)
            if self.free(x, z):
                self.set(x, G, z, ENDROD)

    def rubble(self, n, x1, z1, x2, z2):
        blocks = [ES, ESB, "minecraft:end_stone_brick_slab[type=bottom]", "minecraft:purpur_slab[type=bottom]",
                  "minecraft:purpur_stairs[facing=north]", "minecraft:amethyst_block"]
        for _ in range(n):
            x, z = self.rng.randint(x1, x2), self.rng.randint(z1, z2)
            if self.free(x, z):
                self.set(x, G, z, self.rng.choice(blocks))

    def chandelier(self, x, z, n=3):
        """A sea lantern on a chain (the citadel's hang() with a brighter light)."""
        top = self.h - 2
        if self.get(x, top, z) != AIR or self.get(x, top - n, z) != AIR:
            return
        for y in range(top, top - n, -1):
            self.set(x, y, z, ic.CHAIN + "[axis=y]")
        self.set(x, top - n, z, LANTERN)

    def spawn(self, x, z, *waves, radius=9, spread=4, group=None, y=G):
        super().spawn(x, z, *waves, radius=radius, spread=spread, group=group, y=y)

    def arrows(self, x, y, z, facing, radius=10, damage=None, every=45):
        super().arrows(x, y, z, facing, radius, damage or (13 if self.fl == 1 else 16), every)


def new_hall(pack, fl, kind, tag, w, out_pool, sides):
    r = R(f"f{fl}/{kind}_{tag}", w, ic.F_H[fl], ic.HALL_D, fl)
    r.shell()
    r.ribs(6, 3)
    ic.spine(r, out_pool, sides)
    return r


# ---------------------------------------------------------------------------------------------------------------------
# Spine halls (17 deep; door in on the south wall, out on the north wall, side doors in the middle of the long walls)
# ---------------------------------------------------------------------------------------------------------------------
def hall_chart(pack, fl, tag, w, out_pool, sides):
    """The hall of charts: a carpet runner, star-map murals down both walls, cartography tables, a glass roof over the runner."""
    r = new_hall(pack, fl, "chart", tag, w, out_pool, sides)
    cx = w // 2
    ic.carpet_runner(r)
    for x in (1, w - 2):
        r.mural(x, 2, 5)
        r.mural(x, 11, 14)
    for x in (3, w - 4):
        for z in (3, 13):
            r.desk(x, z)
    r.skylight(cx - 1, 2, cx + 1, r.d - 3)
    for z in (3, 8, 13):
        r.chandelier(cx - 4, z)
        r.chandelier(cx + 4, z)
    r.chest(2, 7, "east", "common" if fl == 1 else "rare") if r.free(2, 7) else None
    r.chest(w - 3, r.d - 3, "north", "common")
    r.rubble(5, 2, 2, w - 3, r.d - 3)
    r.arrows(0, G + 2, 5, "east")
    r.arrows(w - 1, G + 2, 12, "west")
    waves = ([[astronomer(2, Lv(fl, 0)), acolyte(3, Lv(fl, 0)), mite(2, Lv(fl, 0))], [sentinel(2, Lv(fl, 1)), astronomer(3, Lv(fl, 1))]] if fl == 1 else
             [[astronomer(3, Lv(fl, 1)), acolyte(3, Lv(fl, 1)), sentinel(1, Lv(fl, 1))], [mite(4, Lv(fl, 2)), sentinel(2, Lv(fl, 2)), astronomer(2, Lv(fl, 2))]])
    r.spawn(cx, 8, *waves, radius=10, spread=5)
    return r.finish(pack)


def hall_archive(pack, fl, tag, w, out_pool, sides):
    """The archive: tall bookshelves along both walls, lecterns, a glass roof strip."""
    r = new_hall(pack, fl, "archive", tag, w, out_pool, sides)
    cx = w // 2
    for x in (1, w - 2):
        r.shelves(x, 2, 5)
        r.shelves(x, 11, 14)
    for z in (3, 13):
        r.lectern(3, z, "east")
        r.lectern(w - 4, z, "west")
    for z in (2, 5, 11, 14):
        r.brazier(cx - 3, z)
        r.brazier(cx + 3, z)
    r.skylight(cx - 2, 3, cx + 2, 5)
    r.skylight(cx - 2, 11, cx + 2, 13)
    for z in (4, 8, 12):
        r.chandelier(cx - 3, z, 2)
        r.chandelier(cx + 3, z, 2)
    r.chest(3, 8, "east", "common") if r.free(3, 8) else None
    r.chest(w - 3, 3, "south", "common" if fl == 1 else "rare")
    r.rubble(4, 2, 2, w - 3, r.d - 3)
    r.arrows(0, G + 2, 12, "east")
    waves = ([[astronomer(3, Lv(fl, 1)), mite(3, Lv(fl, 1))], [acolyte(3, Lv(fl, 2)), astronomer(2, Lv(fl, 2)), mite(2, Lv(fl, 2))]] if fl == 1 else
             [[astronomer(3, Lv(fl, 2)), acolyte(3, Lv(fl, 2))], [seer(1, Lv(fl, 3), "Star Seer"), astronomer(3, Lv(fl, 2)), mite(3, Lv(fl, 2))]])
    r.spawn(cx, 8, *waves, radius=10, spread=5)
    return r.finish(pack)


def hall_gallery(pack, fl, tag, w, out_pool, sides):
    """The glass gallery: rows of pillars with sea lanterns, banners, a roof of glass between the ribs, windows on the void."""
    r = new_hall(pack, fl, "gallery", tag, w, out_pool, sides)
    cx = w // 2
    for x in [cx - 5, cx + 5] + ([cx - 9, cx + 9] if w >= 21 else []):
        for z in (3, 6, 10, 13):
            r.col(x, z)
    ic.carpet_runner(r, "minecraft:light_blue_carpet")
    r.banners((2, 6, 10, 14), color="light_blue" if fl == 1 else "purple")
    r.skylight(2, 2, w - 3, r.d - 3)
    for side in ("west", "east"):
        for z0 in (2, 11):
            r.window(side, z0 + 1, z0 + 2, G + 4, G + 7)
    for x in (cx - 3, cx + 3):
        for z in (2, r.d - 3):
            r.brazier(x, z)
    r.chest(2, 2, "south", "common" if fl == 1 else "rare") if r.free(2, 2) else None
    r.chest(w - 3, r.d - 3, "north", "common")
    r.rubble(5, 2, 2, w - 3, r.d - 3)
    r.arrows(w - 1, G + 2, 4, "west")
    r.arrows(0, G + 2, 12, "east")
    waves = ([[acolyte(3, Lv(fl, 0)), mite(3, Lv(fl, 0)), sentinel(1, Lv(fl, 1))], [astronomer(3, Lv(fl, 1)), sentinel(2, Lv(fl, 1)), acolyte(2, Lv(fl, 1))]] if fl == 1 else
             [[acolyte(4, Lv(fl, 1)), sentinel(2, Lv(fl, 1))], [seer(1, Lv(fl, 3)), acolyte(3, Lv(fl, 2)), mite(3, Lv(fl, 2))]])
    r.spawn(cx, 8, *waves, radius=10, spread=5)
    return r.finish(pack)


def hall_quarters(pack, fl, tag, w, out_pool, sides):
    """The sentinels' quarters: bunks and shulker boxes along both walls, a long table, a lens rack."""
    r = new_hall(pack, fl, "quarters", tag, w, out_pool, sides)
    cx = w // 2
    for z in (3, 5, 11, 13):
        r.set(1, G, z, "minecraft:purple_bed[facing=west,part=head]")
        r.set(2, G, z, "minecraft:purple_bed[facing=west,part=foot]")
        r.set(w - 2, G, z, "minecraft:purple_bed[facing=east,part=head]")
        r.set(w - 3, G, z, "minecraft:purple_bed[facing=east,part=foot]")
        r.reserve(1, z, 2, z)
        r.reserve(w - 3, z, w - 2, z)
    for z in (4, 12):
        for x in (3, w - 4):
            r.set(x, G, z, "minecraft:purple_shulker_box[facing=up]")
            r.keep.add((x, z))
    for x0 in (cx - 6, cx + 4):
        for x in range(x0, x0 + 3):
            r.set(x, G, 7, "minecraft:purpur_stairs[facing=south]")
            r.set(x, G, 8, "minecraft:purpur_slab[type=bottom]")
            r.set(x, G, 9, "minecraft:purpur_stairs[facing=north]")
            for z in (7, 8, 9):
                r.keep.add((x, z))
    r.chest(2, r.d - 3, "north", "common")
    r.chest(w - 3, 2, "south", "common" if fl == 1 else "rare")
    for z in (3, 8, 13):
        r.chandelier(cx - 4, z, 2)
        r.chandelier(cx + 4, z, 2)
    r.banners((8,), y=G + 3, color="purple")
    r.rubble(4, 4, 2, w - 5, r.d - 3)
    r.arrows(w - 1, G + 2, 4, "west")
    r.arrows(0, G + 2, 12, "east")
    waves = ([[sentinel(3, Lv(fl, 1)), acolyte(3, Lv(fl, 1))], [seer(1, Lv(fl, 3), "Star Seer"), sentinel(2, Lv(fl, 2)), mite(3, Lv(fl, 2))]] if fl == 1 else
             [[sentinel(3, Lv(fl, 2)), acolyte(3, Lv(fl, 2)), mite(2, Lv(fl, 2))], [sentinel(2, Lv(fl, 3)), seer(1, Lv(fl, 4)), astronomer(3, Lv(fl, 3))]])
    r.spawn(cx, 8, *waves, radius=10, spread=5)
    return r.finish(pack)


HALL_FNS = {"chart": hall_chart, "archive": hall_archive, "gallery": hall_gallery, "quarters": hall_quarters}
SPINE = {
    1: {1: [("chart", "a", 19), ("archive", "a", 17), ("gallery", "a", 21)],
        2: [("archive", "b", 19), ("quarters", "a", 17), ("gallery", "b", 23)],
        3: [("chart", "b", 17), ("quarters", "b", 21), ("gallery", "c", 19)]},
    2: {1: [("gallery", "a", 19), ("archive", "a", 21), ("chart", "a", 17)],
        2: [("chart", "b", 21), ("quarters", "a", 19), ("archive", "b", 17)],
        3: [("gallery", "b", 17), ("quarters", "b", 21), ("archive", "c", 19)]},
}
SPINE_SIDES = {1: ("east", "west"), 2: ("east", "west")}


# ---------------------------------------------------------------------------------------------------------------------
# Side rooms and leaves
# ---------------------------------------------------------------------------------------------------------------------
def room(path, w, h, d, fl, tail=True):
    r = R(path, w, h, d, fl)
    r.shell()
    r.door("south", "side_in")
    if tail:
        r.door("north", "tail_out")
    r.lane("x")
    return r


def telescope(r, cx, z, length=7):
    """A big brass telescope on a purpur tripod, aimed up and north at the roof."""
    p = r.p
    for dx in (-1, 1):
        p.set(cx + dx, G, z + 1, PILLAR)
    p.set(cx, G, z - 1, PILLAR)
    p.set(cx, G, z, PILLAR)
    for i in range(length):
        p.set(cx, G + 1 + i, z - i, COPPER)
        if i % 3 == 2:
            p.set(cx, G + 2 + i, z - i, "minecraft:copper_block")
    p.set(cx, G + 1 + length, z - length, "minecraft:tinted_glass")
    r.keep |= {(cx + dx, z + dz) for dx in (-1, 0, 1) for dz in (-1, 0, 1)}


def room_telescope(pack, fl):
    """The telescope room: a great brass telescope on a dais under an open glass roof, star maps on the walls."""
    r = room(f"f{fl}/telescope", 17, FD + 16, 19, fl, tail=False)
    p = r.p
    cx = 8
    for x in range(cx - 3, cx + 4):
        for z in range(9, 16):
            if math.hypot(x - cx, z - 12) <= 3.4:
                p.set(x, FD, z, QUARTZ_C if math.hypot(x - cx, z - 12) <= 1.6 else PUR)
    r.reserve(cx - 4, 8, cx + 4, 16)
    telescope(r, cx, 13, 7)
    r.skylight(3, 3, 13, 14)
    r.mural(1, 3, 6)
    r.mural(1, 12, 15)
    r.mural(15, 3, 6)
    r.mural(15, 12, 15)
    r.col(3, 3, y1=r.h - 4)
    r.col(13, 3, y1=r.h - 4)
    r.chest(2, 16, "north", "rare")
    r.chest(14, 16, "north", "common")
    r.chandelier(cx - 5, 8, 2)
    r.chandelier(cx + 5, 8, 2)
    r.arrows(0, G + 2, 10, "east", radius=9)
    r.arrows(16, G + 2, 10, "west", radius=9)
    r.spawn(cx, 6, [acolyte(3, Lv(fl, 1)), astronomer(3, Lv(fl, 1)), sentinel(1, Lv(fl, 1))],
            [acolyte(4, Lv(fl, 2)), mite(3, Lv(fl, 2))], radius=9, spread=3)
    return r.finish(pack)


def room_chart_room(pack, fl):
    """The map room: cartography tables in rows, star maps floor to ceiling, a lectern with the master chart."""
    r = room(f"f{fl}/map_room", 15, FD + 9, 15, fl)
    for x in (1, 13):
        r.mural(x, 2, 5)
        r.mural(x, 9, 12)
    for x in (3, 11):
        for z in (3, 5, 9, 11):
            r.desk(x, z)
    r.lectern(7, 2, "south") if r.free(7, 2) else None
    r.chest(2, 7, "east", "common" if fl == 1 else "rare") if r.free(2, 7) else None
    r.chest(12, 7, "west", "common")
    r.chandelier(5, 7, 2)
    r.chandelier(9, 7, 2)
    r.arrows(7, G + 2, 14, "north", radius=9) if False else None
    r.spawn(7, 8, [astronomer(4, Lv(fl, 1)), mite(3, Lv(fl, 1))], [acolyte(3, Lv(fl, 2)), astronomer(3, Lv(fl, 2))], radius=8, spread=3)
    return r.finish(pack)


def room_workshop(pack, fl):
    """The lens workshop: grindstones, anvils, glass stacked on racks, a lit blast furnace."""
    r = room(f"f{fl}/workshop", 13, FD + 8, 15, fl)
    for z in (3, 5, 9, 11):
        r.set(1, G, z, "minecraft:grindstone[face=floor,facing=east]")
        r.keep.add((1, z))
    r.set(11, G, 3, "minecraft:blast_furnace[facing=west,lit=true]")
    r.set(11, G, 4, "minecraft:blast_furnace[facing=west,lit=true]")
    r.keep |= {(11, 3), (11, 4)}
    for x in (10, 11):
        for z in (9, 10, 11):
            for y in (G, G + 1):
                r.set(x, y, z, "minecraft:glass")
            r.keep.add((x, z))
    r.set(10, G, 6, "minecraft:anvil[facing=east]")
    r.keep.add((10, 6))
    r.set(2, G, 13, "minecraft:barrel[facing=up]")
    r.keep.add((2, 13))
    r.chest(10, 13, "north", "rare" if fl == 2 else "common")
    r.chandelier(6, 5, 2)
    r.chandelier(6, 10, 2)
    r.arrows(12, G + 2, 7, "west", radius=8)
    r.spawn(6, 8, [astronomer(4, Lv(fl, 1)), sentinel(2, Lv(fl, 1))], [mite(3, Lv(fl, 2)), astronomer(2, Lv(fl, 2), "Mad Astronomer", True)], radius=8, spread=3)
    return r.finish(pack)


def room_quarters(pack, fl):
    """An astronomer's quarters: a bed, a desk of charts, shelves, and a hidden cache behind a loose wall."""
    r = room(f"f{fl}/astronomer", 13, FD + 8, 15, fl)
    r.shelves(11, 6, 12, 3)
    for z in (3, 5):
        r.set(1, G, z, "minecraft:purple_bed[facing=west,part=head]")
        r.set(2, G, z, "minecraft:purple_bed[facing=west,part=foot]")
        r.keep |= {(1, z), (2, z)}
    r.desk(9, 3)
    r.lectern(10, 3, "west")
    ic.cache(r, 1, 9, 4, 13, (2, 11), "east", ES, "The Astronomer's Cache")
    r.chest(10, 13, "north", "common" if fl == 1 else "rare") if r.free(10, 13) else None
    r.chandelier(6, 6, 2)
    r.chandelier(6, 11, 2)
    r.spawn(7, 8, [acolyte(3, Lv(fl, 1)), astronomer(3, Lv(fl, 1))], [sentinel(2, Lv(fl, 2)), mite(4, Lv(fl, 2))], radius=8, spread=3)
    return r.finish(pack)


ROOMS = {1: [(room_telescope, 2), (room_chart_room, 3), (room_workshop, 3), (room_quarters, 3)],
         2: [(room_telescope, 3), (room_chart_room, 2), (room_workshop, 2), (room_quarters, 3)]}


def leaf(path, w, d, fl):
    r = R(path, w, FD + 6, d, fl)
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
    r.chandelier(4, 4, 1)
    r.spawn(4, 5, [sentinel(2, Lv(fl, 1)), mite(3, Lv(fl, 1))], radius=7, spread=2)
    return r.finish(pack)


def leaf_nook(pack, fl):
    r = leaf(f"f{fl}/leaf_nook", 9, 11, fl)
    ic.cache(r, 1, 1, 4, 4, (2, 2), "south", ES, "Stargazer's Nook")
    r.set(7, G, 2, "minecraft:barrel[facing=up]")
    r.keep.add((7, 2))
    r.chandelier(4, 6, 1)
    r.spawn(4, 6, [acolyte(2, Lv(fl, 1)), mite(3, Lv(fl, 1))], radius=7, spread=2)
    return r.finish(pack)


def leaf_guard(pack, fl):
    r = leaf(f"f{fl}/leaf_guard", 11, 9, fl)
    r.arrows(10, G + 2, 4, "west", radius=8)
    r.chest(2, 1, "south", "common" if fl == 1 else "rare")
    r.set(8, G, 2, "minecraft:cartography_table")
    r.keep.add((8, 2))
    r.chandelier(5, 4, 1)
    r.spawn(5, 4, [sentinel(2, Lv(fl, 2)), acolyte(2, Lv(fl, 2)), astronomer(2, Lv(fl, 2))], radius=7, spread=2)
    return r.finish(pack)


LEAVES = [leaf_closet, leaf_nook, leaf_guard]


def cap(pack, kind, fl):
    wall = PAL[fl].wall
    p = Piece(f"{CZ}/caps/{kind}{fl}", 3, FD + 4, 1)
    p.fill(0, 0, 0, 2, FD + 3, 0, wall)
    p.set(1, FD + 2, 0, PAL[fl].trim)
    p.jigsaw(1, G, 0, "north_up", f"obs_{kind}_in", f"obs_{kind}_in", "minecraft:empty", final_state=wall)
    pack.add(p)
    return p


# ---------------------------------------------------------------------------------------------------------------------
# The Courtyard (the start piece, 31 x 26 x 27): a terrace on stilts with the waystone, a gate, and the atrium behind it
# ---------------------------------------------------------------------------------------------------------------------
EW, ED = 31, 27
GATE_Z = 18           # the gate wall's plane (atrium's south wall)


def build_entrance(pack):
    r = R("entrance", EW, FD + 22, ED, 1)
    p = r.p
    pal = r.pal
    cx = EW // 2
    r.shell(0, 0, EW - 1, GATE_Z, top=FD + 20, pilings=True)
    r.ribs(6, 3)
    r.door("north", "spine_out", pool=f"{CZ}/f1_s1")
    # the gate: a 7 wide arch in the south wall
    for x in range(cx - 3, cx + 4):
        for y in range(G, G + 6):
            p.set(x, y, GATE_Z, AIR)
    for y in range(G, G + 7):
        for x in (cx - 4, cx + 4):
            p.set(x, y, GATE_Z, pal.trim)
    for x in range(cx - 4, cx + 5):
        p.set(x, G + 6, GATE_Z, pal.cap)
    # the atrium: columns, a carpet runner, a glass roof, star-map murals
    ic.carpet_runner(r, "minecraft:purple_carpet")
    for x in (cx - 6, cx + 6, cx - 11, cx + 11):
        for z in (3, 7, 11, 15):
            r.col(x, z)
    r.reserve(cx - 3, 0, cx + 3, GATE_Z)
    for x in (1, EW - 2):
        r.mural(x, 2, 5)
    r.skylight(2, 2, EW - 3, GATE_Z - 2)
    r.banners((5, 9, 13), y=G + 6, color="purple")
    for x in (cx - 8, cx + 8):
        for z in (2, 16):
            r.brazier(x, z)
    for z in (4, 9, 14):
        r.chandelier(cx - 3, z, 3)
        r.chandelier(cx + 3, z, 3)
    r.chest(3, 16, "north", "common")
    r.chest(EW - 4, 16, "north", "common")
    r.rubble(6, 2, 2, EW - 3, GATE_Z - 2)
    r.arrows(0, G + 2, 9, "east", radius=11)
    r.arrows(EW - 1, G + 2, 9, "west", radius=11)
    # the forecourt on stilts, in front of the gate (z 19..26): a terrace with a parapet, the waystone, a telescope
    for x in range(5, 26):
        for z in range(GATE_Z + 1, ED):
            for y in range(G, G + 10):
                p.set(x, y, z, AIR)
            p.set(x, FD, z, pal.floor if (x + z) % 3 else pal.inlay)
    for x in range(cx - 3, cx + 4):
        for z in range(GATE_Z + 1, ED):
            p.set(x, FD, z, QUARTZ_C if (x + z) % 6 == 0 else PUR)
    r.pilings(5, GATE_Z + 1, 25, ED - 1)
    for x in range(5, 26):
        if not cx - 3 <= x <= cx + 3:
            p.set(x, G, ED - 1, ESB_WALL)
    for z in range(GATE_Z + 1, ED):
        for x in (5, 25):
            p.set(x, G, z, ESB_WALL)
    for x in (cx - 3, cx + 3):
        for z in (ED - 3, ED - 1):
            for y in range(G, G + 3):
                p.set(x, y, z, PILLAR)
            p.set(x, G + 3, z, ENDROD)
    for z in range(GATE_Z + 1, GATE_Z + 3):
        for x in (5, 25):
            for y in range(G, G + 4):
                p.set(x, y, z, PILLAR)
            p.set(x, G + 4, z, LANTERN)
    telescope(r, 21, ED - 5, 5)
    p.set(8, G, GATE_Z + 6, "skyrpg_world:waystone")
    p.marker(cx, G, GATE_Z + 6, "dungeon", dungeon=DUN)
    for x in (9, 22):
        for y in (G + 7, G + 9):
            p.set(x, y, GATE_Z, "minecraft:purple_wall_banner[facing=south]") if p.get(x, y, GATE_Z) == pal.wall else None
    r.spawn(cx, 10, [acolyte(3, Lv(1, 0)), astronomer(2, Lv(1, 0)), mite(3, Lv(1, 0))], [sentinel(2, Lv(1, 1)), astronomer(3, Lv(1, 1))], radius=11, spread=3)
    r.weather()
    ic.autoconnect(p)
    pack.add(p)
    return p


# ---------------------------------------------------------------------------------------------------------------------
# The Ascent (floor 1 -> floor 2): a tower with two switchback flights, 20 up. In on the south wall at the bottom, out on the
# east wall at the top (floor 2 runs east: the 90 degree turn).
# ---------------------------------------------------------------------------------------------------------------------
def build_ascent(pack):
    W = D = 19
    H = FD + DROP + 10
    r = R("ascent", W, H, D, 1)
    p = r.p
    pal = r.pal
    r.shell(top=H - 1)
    # the first flight: the west side, north from z=13 to z=4 (10 steps), then the middle landing (z 1..3)
    for i in range(10):
        for x in (2, 3, 4):
            p.set(x, FD + 1 + i, 13 - i, "minecraft:purpur_stairs[facing=north,half=bottom]")
    for x in range(2, 17):
        for z in range(1, 4):
            p.set(x, FD + 10, z, pal.floor if (x + z) % 2 else pal.inlay)
    # the second flight: the east side, south from z=4 to z=13 (10 steps), then the top landing (z 14..16)
    for i in range(10):
        for x in (14, 15, 16):
            p.set(x, FD + 11 + i, 4 + i, "minecraft:purpur_stairs[facing=south,half=bottom]")
    for x in range(1, 18):
        for z in range(14, 17):
            p.set(x, FD + DROP, z, pal.floor if (x + z) % 2 else pal.inlay)
    # under the flights: solid pillars of purpur every few steps, so the stairs aren't floating
    for i in range(0, 10, 3):
        for x in (2, 4):
            for y in range(FD, FD + 1 + i):
                p.set(x, y, 13 - i, PILLAR)
        for x in (14, 16):
            for y in range(FD + 10, FD + 11 + i):
                p.set(x, y, 4 + i, PILLAR)
    # railings (glass) along the open sides of the landings and flights
    for i in range(10):
        p.set(5, FD + 2 + i, 13 - i, PANE)
        p.set(13, FD + 12 + i, 4 + i, PANE)
    for z in range(1, 4):
        p.set(16, FD + 11, z, PANE) if False else None
    for y in (G + 4, G + 11, G + 18):
        for x in (4, 9, 14):
            p.set(x, y, 0, QUARTZ_C)
    for i in (1, 5, 9):
        r.chandelier(9, 8 + (i // 4), 6) if False else None
    for y, x, z in ((FD + 14, 9, 9), (FD + 20, 9, 12), (FD + 8, 9, 6)):
        for yy in range(y, FD + DROP + 8):
            if p.get(x, yy, z) == AIR:
                p.set(x, yy, z, ic.CHAIN + "[axis=y]")
        p.set(x, y, z, LANTERN)
    r.door("south", "spine_in", at=9)
    r.door("east", "spine_out", pool=f"{CZ}/f2_s1", at=15, y=G + DROP)
    r.arrows(0, FD + 6, 10, "east", radius=8, damage=14, every=50)
    r.arrows(18, FD + 16, 8, "west", radius=8, damage=14, every=50)
    r.spawn(9, 2, [acolyte(3, Lv(1, 2)), sentinel(2, Lv(1, 2)), mite(3, Lv(1, 2))], radius=9, spread=2, y=FD + 11)
    for x, z in ((2, 1), (16, 1)):
        p.set(x, FD + 11, z, PILLAR)
        p.set(x, FD + 12, z, ENDROD)
    r.weather()
    ic.autoconnect(p)
    pack.add(p)
    return p


# ---------------------------------------------------------------------------------------------------------------------
# The Orrery Hall (floor 1 champion): 27 x 20 x 21, the north door is shut with a bar plug until the Sentinel falls
# ---------------------------------------------------------------------------------------------------------------------
def build_champion(pack):
    w, h, d = 27, FD + 16, 21
    r = R("f1/champion", w, h, d, 1)
    r.shell()
    r.ribs(7, 4)
    p = r.p
    cx = w // 2
    r.door("south", "spine_in")
    r.door("north", "spine_out", pool=f"{CZ}/ascent")
    for z in (2, 3):
        for x in (cx - 1, cx, cx + 1):
            for y in (G, G + 1, G + 2):
                p.set(x, y, z, ic.BARS)
    for x in (cx - 1, cx, cx + 1):
        p.set(x, G + 3, 2, ic.CHAIN + "[axis=y]")
    p.marker(cx, G + 1, 2, "door", group="champion", message="The way up is sealed. Slay the Astral Sentinel.")
    r.reserve(cx - 2, 0, cx + 2, 4)
    # the orrery: a dais with rings of end rods and glass hanging round the middle, a bright core
    for x in range(cx - 6, cx + 7):
        for z in range(5, 18):
            dist = math.hypot(x - cx, z - 11)
            if dist <= 5.6:
                p.set(x, FD, z, QUARTZ_C if dist <= 1.8 else (PUR if dist <= 3.4 else ESB))
    r.reserve(cx - 6, 5, cx + 6, 17)
    for ring, (rad, y) in enumerate(((3.2, G + 6), (4.4, G + 8), (5.2, G + 10))):
        for k in range(24):
            ang = k / 24 * math.tau + ring
            x, z = round(cx + math.cos(ang) * rad), round(11 + math.sin(ang) * rad)
            if p.get(x, y, z) == AIR:
                p.set(x, y, z, ENDROD if k % 3 == 0 else GLASS)
    p.set(cx, G + 8, 11, LANTERN)
    p.set(cx, G + 9, 11, ic.CHAIN + "[axis=y]")
    p.set(cx, G + 10, 11, ic.CHAIN + "[axis=y]")
    for x in (cx - 8, cx + 8):
        for z in (6, 16):
            r.col(x, z, y1=h - 4)
    for x, z in ((cx - 5, 3), (cx + 5, 3), (cx - 5, 19), (cx + 5, 19)):
        r.brazier(x, z)
    r.skylight(3, 3, w - 4, d - 4)
    r.banners((6, 11, 16), y=G + 6, color="purple")
    r.chest(4, d - 3, "north", "rare")
    r.chest(w - 5, d - 3, "north", "rare")
    r.arrows(0, G + 2, 11, "east", radius=11, damage=16)
    r.arrows(w - 1, G + 2, 11, "west", radius=11, damage=16)
    r.spawn(cx, 18, [acolyte(4, Lv(1, 3)), sentinel(3, Lv(1, 3))], [seer(1, Lv(1, 4)), astronomer(3, Lv(1, 3)), mite(4, Lv(1, 3))], radius=9, spread=3)
    p.marker(cx, G + 1, 11, "boss", boss="skyrpg_world:astral_sentinel", radius=13, group="champion")
    r.rubble(6, 2, 4, w - 3, d - 3)
    return r.finish(pack)


# ---------------------------------------------------------------------------------------------------------------------
# The Star Warden's Dome (floor 2 boss arena): 35 x 28 x 35
# ---------------------------------------------------------------------------------------------------------------------
def build_dome(pack):
    w, d = 35, 35
    h = FD + 24
    r = R("boss/dome", w, h, d, 2)
    r.shell()
    p = r.p
    cx = w // 2
    cz = 22
    r.door("south", "spine_in")
    r.reserve(cx - 1, 24, cx + 1, d - 1)
    top = h - 2
    # the observatory alcove (x 14..20, z 1..6) flanked by two annexes behind partitions (the row z=7 and the walls x=13, x=21)
    for x in range(1, w - 1):
        for y in range(G, top):
            if x <= 13 or x >= 21:
                p.set(x, y, 7, ESB)
    for z in range(1, 8):
        for y in range(G, top):
            p.set(13, y, z, ESB)
            p.set(21, y, z, ESB)
    for x in (6, 7, 8):                                      # the vault door (key) and the hoard door (boss death)
        for y in (G, G + 1, G + 2):
            p.set(x, y, 7, KEY_DOOR)
    p.marker(7, G + 1, 7, "key_door", key="skyrpg_world:observatory_key", message="The vault is sealed. It needs the Observatory Key.")
    for x in (26, 27, 28):
        for y in (G, G + 1, G + 2):
            p.set(x, y, 7, NOOK_DOOR)
    p.marker(27, G + 1, 7, "door", group="warden", message="The Warden's hoard slides open.")
    for xs in ((2, 12), (22, 32)):
        for x in range(xs[0], xs[1] + 1):
            for z in range(1, 7):
                for y in range(G, top):
                    p.set(x, y, z, AIR if y < G + 5 else ESB)
    p.chest(4, G, 1, "south", "skyrpg_world:chests/observatory_vault", tier="vault")
    p.chest(10, G, 1, "south", "skyrpg_world:chests/observatory_vault", tier="vault")
    p.chest(7, G, 1, "south", "skyrpg_world:chests/observatory_rare", tier="rare")
    for pos, b in (((6, G, 1), "minecraft:amethyst_block"), ((8, G, 1), "minecraft:amethyst_block"), ((3, G, 1), QUARTZ_C), ((11, G, 1), QUARTZ_C),
                   ((7, G + 4, 3), LANTERN), ((4, G + 3, 3), LANTERN), ((10, G + 3, 3), LANTERN)):
        p.set(*pos, b)
    p.chest(27, G, 1, "south", "skyrpg_world:chests/observatory_secret", tier="secret")
    p.marker(27, G, 2, "secret", name="The Warden's Hoard")
    for x in (25, 29):
        p.set(x, G, 1, "minecraft:amethyst_block")
    p.set(27, G + 4, 3, LANTERN)
    r.sign(4, G + 1, 8, ["The vault is sealed", "under the stars.", "Bring the Observatory Key."], "south")
    # the great telescope: a tripod and a long brass tube aimed through a slot in the roof
    for x in range(14, 21):
        for z in (4, 5, 6):
            p.set(x, FD, z, PUR)
    for x in range(15, 20):
        p.set(x, G, 4, ESB)
    for dx in (-1, 1):
        p.set(17 + dx, G, 5, PILLAR)
    p.set(17, G, 5, PILLAR)
    for i in range(8):
        p.set(17, G + 1 + i, 5 - min(i, 4), COPPER)
        p.set(17, G + 2 + i, 5 - min(i, 4), COPPER) if i >= 4 else None
    p.set(17, G + 9, 1, "minecraft:tinted_glass")
    for x in (14, 20):
        p.set(x, G, 5, ENDROD)
    for x in (14, 20):
        r.keep |= {(x, z) for z in range(1, 7)}
    r.reserve(1, 1, w - 2, 7)
    # the dome: the whole roof is glass between ribs, with windows round the upper walls
    r.skylight(2, 2, w - 3, d - 3)
    for z in range(3, d - 3, 6):
        for x in range(1, w - 1):
            if p.get(x, top, z) == AIR:
                p.set(x, top, z, PILLAR)
    for side in ("west", "east"):
        for z0 in (10, 16, 22, 28):
            r.window(side, z0, z0 + 2, G + 8, G + 14)
    r.window("south", 4, 8, G + 8, G + 14)
    r.window("south", 26, 30, G + 8, G + 14)
    # the arena: a star chart inlaid in the floor (rings and spokes), plain solid floor elsewhere
    for x in range(1, w - 1):
        for z in range(9, d - 1):
            dist = math.hypot(x - cx, z - cz)
            if dist <= 10.6:
                ang = math.atan2(z - cz, x - cx)
                spoke = min(abs(((ang * 8 / math.tau) % 1.0) - 0.5), 0.5) > 0.44 and dist > 2.0
                if 8.4 <= dist <= 9.6 or 4.4 <= dist <= 5.2:
                    p.set(x, FD, z, QUARTZ_C)
                elif spoke:
                    p.set(x, FD, z, "minecraft:amethyst_block")
                elif dist <= 3.2:
                    p.set(x, FD, z, PUR if dist > 1.5 else QUARTZ_C)
    for x, z in ((8, 12), (26, 12), (8, 32), (26, 32), (11, 9), (23, 9)):
        r.col(x, z, y1=top - 1)
    for x, z in ((cx - 7, cz), (cx + 7, cz), (cx, cz - 7), (cx, cz + 7), (10, 26), (24, 26), (10, 18), (24, 18)):
        r.chandelier(x, z, 4)
    for x, z in ((6, 22), (28, 22), (6, 16), (28, 16)):
        r.brazier(x, z)
    r.chest(8, 33, "north", "rare") if r.free(8, 33) else None
    r.arrows(5, G + 2, 7, "south", radius=15, damage=18)
    r.arrows(29, G + 2, 7, "south", radius=15, damage=18)
    r.rubble(8, 6, 10, w - 7, d - 3)
    p.marker(cx, G + 1, cz, "boss", boss="skyrpg_world:star_warden", radius=18, group="warden")
    EXEMPT[p.path] = ("secret",)
    return r.finish(pack)


# ---------------------------------------------------------------------------------------------------------------------
# Loot (old function/condition style; Pack.loot_table converts it to 26.3's modifier/condition format)
# ---------------------------------------------------------------------------------------------------------------------
it, lpool, table, rolls, book = ic.it, ic.lpool, ic.table, ic.rolls, ic.book
W, C, M = "skyrpg_world:", "skyrpg_combat:", "skyrpg_mining:"
SHARD = W + "astral_shard"
EGG_RARE = ic.EGG_RARE


def loot(pack):
    common = [it("minecraft:ender_pearl", 8, 2, 5), it("minecraft:chorus_fruit", 7, 3, 8), it("minecraft:end_rod", 6, 3, 8),
              it("minecraft:amethyst_shard", 6, 4, 10), it("minecraft:purpur_block", 4, 6, 16), it("minecraft:gold_ingot", 6, 4, 9),
              it("minecraft:experience_bottle", 5, 3, 6), it("skyrpg_enchants:runic_dust", 6, 3, 6), it("minecraft:lapis_lazuli", 5, 6, 14),
              it("minecraft:glowstone_dust", 4, 4, 9), it(SHARD, 5, 1, 3), it("minecraft:spyglass", 1, 1, 1), book(3)]
    pack.loot_table("chests/observatory_common", table(
        lpool(rolls(4, 6), common),
        lpool(1, [it(C + "valor_mark", 3, 1, 1), it(C + "dread_fang", 2, 1, 1), it(W + "dread_crystal", 1, 1, 1), it(SHARD, 3, 1, 2)], .24)))
    rare = [it("minecraft:diamond", 5, 2, 5), it("minecraft:ender_pearl", 7, 3, 7), it("minecraft:ender_eye", 5, 1, 3), it("minecraft:end_rod", 6, 4, 10),
            it("minecraft:shulker_shell", 4, 1, 2), it("minecraft:echo_shard", 2, 1, 1), it("minecraft:experience_bottle", 6, 5, 10),
            it("skyrpg_enchants:runic_dust", 8, 5, 10), it(SHARD, 8, 2, 4), it(W + "dread_crystal", 2, 1, 1), book(5)]
    pack.loot_table("chests/observatory_rare", table(
        lpool(rolls(5, 7), rare),
        lpool(1, [it(C + "elite_trophy", 3, 1, 1), it(C + "valor_mark", 4, 1, 2), it(C + "dread_fang", 3, 1, 2),
                  it(M + "heartstone", 2, 1, 1), it(W + "dread_crystal", 2, 1, 1)], .55),
        lpool(1, [it(C + "nightmare_core", 1, 1, 1)], .07),
        lpool(1, EGG_RARE, .035)))
    secret = [it("minecraft:diamond", 7, 3, 7), it("minecraft:ender_eye", 6, 2, 4), it("minecraft:shulker_shell", 5, 1, 3),
              it("minecraft:echo_shard", 3, 1, 2), it("minecraft:experience_bottle", 6, 6, 12), it("skyrpg_enchants:runic_dust", 9, 6, 12),
              it(SHARD, 9, 3, 6), it(W + "dread_crystal", 4, 1, 2), book(6)]
    pack.loot_table("chests/observatory_secret", table(
        lpool(rolls(5, 8), secret),
        lpool(1, [it(M + "prism", 3, 1, 1), it(M + "heartstone", 3, 1, 2), it(C + "valor_seal", 3, 1, 1), it(C + "elite_trophy", 3, 1, 2)]),
        lpool(1, [it(C + "nightmare_core", 1, 1, 1)], .10),
        lpool(1, [it("skyrpg_pets:golden_pet_egg", 1, 1, 1)], .014),
        lpool(1, EGG_RARE, .05)))
    vault = [it("minecraft:diamond", 8, 5, 9), it("minecraft:ender_eye", 6, 3, 6), it("minecraft:shulker_shell", 6, 2, 4),
             it("minecraft:echo_shard", 4, 1, 2), it("minecraft:experience_bottle", 6, 8, 14), it("skyrpg_enchants:runic_dust", 9, 8, 16),
             it(SHARD, 10, 4, 8), it(W + "dread_crystal", 5, 1, 2), it(C + "valor_seal", 4, 1, 2), it(C + "elite_trophy", 5, 1, 3),
             it(C + "dread_fang", 4, 1, 2), book(8)]
    pack.loot_table("chests/observatory_vault", table(
        lpool(rolls(6, 8), vault),
        lpool(1, [it(SHARD, 1, 6, 10)]),
        lpool(1, [it("minecraft:elytra", 1, 1, 1)], .02),
        lpool(1, [it(C + "nightmare_core", 1, 1, 1)], .15),
        lpool(1, [it("skyrpg_pets:golden_pet_egg", 1, 1, 1)], .02),
        lpool(1, EGG_RARE, .06),
        lpool(1, [it("minecraft:enchanted_golden_apple", 1, 1, 1)], .15)))
    pack.loot_table("bosses/astral_sentinel", table(
        lpool(1, [it(W + "observatory_key", 1)]),
        lpool(1, [it(SHARD, 1, 3, 6)]),
        lpool(rolls(2, 4), [it("minecraft:ender_pearl", 6, 4, 8), it("minecraft:ender_eye", 6, 2, 4), it("minecraft:shulker_shell", 5, 1, 3),
                            it("minecraft:experience_bottle", 6, 5, 10), it("skyrpg_enchants:runic_dust", 8, 6, 12), book(4)]),
        lpool(1, [it(W + "dread_crystal", 1, 1, 1)], .25),
        lpool(1, [it(C + "elite_trophy", 1, 1, 1)], .35),
        lpool(1, [it(C + "valor_mark", 1, 1, 2)], .30),
        lpool(1, [it(C + "dread_fang", 1, 1, 1)], .15),
        lpool(1, [it(C + "nightmare_core", 1, 1, 1)], .08),
        lpool(1, EGG_RARE, .04)))
    pack.loot_table("bosses/star_warden", table(
        lpool(1, [it(W + "star_warden_core", 1, 1, 1)]),               # the trophy: always one ...
        lpool(1, [it(W + "star_warden_core", 1, 1, 1)], .25),          # ... and a quarter of the time a second
        lpool(1, [it(W + "observatory_key", 1)]),
        lpool(1, [it(SHARD, 1, 8, 14)]),
        lpool(rolls(5, 7), [it("minecraft:diamond", 6, 4, 8), it("minecraft:ender_eye", 6, 3, 6), it("minecraft:shulker_shell", 6, 2, 5),
                            it("minecraft:echo_shard", 4, 1, 3), it("skyrpg_enchants:runic_dust", 9, 8, 16), it(W + "dread_crystal", 4, 1, 3),
                            it("minecraft:experience_bottle", 7, 8, 16), book(5)]),
        lpool(1, [it(C + "valor_seal", 1, 1, 2)], .50),
        lpool(1, [it(C + "elite_trophy", 1, 1, 2)], .60),
        lpool(1, [it(C + "nightmare_core", 1, 1, 1)], .25),
        lpool(1, [it("skyrpg_pets:golden_pet_egg", 1, 1, 1)], .035),
        lpool(1, EGG_RARE, .10)))
    pack.loot_table("rewards/star_warden", table(
        lpool(rolls(5, 7), [it("skyrpg_enchants:runic_dust", 9, 8, 16), it("minecraft:diamond", 7, 5, 10), it("minecraft:experience_bottle", 7, 8, 16),
                            it("minecraft:ender_eye", 7, 3, 6), it("minecraft:shulker_shell", 6, 2, 5), it("minecraft:echo_shard", 4, 1, 3),
                            it(SHARD, 9, 4, 8), it(W + "dread_crystal", 5, 1, 3), it(C + "valor_seal", 5, 1, 2), it(C + "elite_trophy", 5, 1, 3),
                            it(M + "heartstone", 4, 1, 2), book(6)]),
        lpool(1, [it(C + "nightmare_core", 1, 1, 1)], .22),
        lpool(1, [it(C + "dread_fang", 1, 1, 2)], .35),
        lpool(1, [it("skyrpg_pets:golden_pet_egg", 1, 1, 1)], .04),
        lpool(1, EGG_RARE, .12)))


def tome_drops(pack):
    def drops(tag, entries):
        pack.json(f"skyrpg/tome_drops/{tag}.json", {
            "tag": f"skyrpg_boss=skyrpg_world:{tag}",
            "drops": [{"enchant": f"skyrpg_enchants:{e}", "level": lv, "chance": ch, "min_tier": 0} for e, lv, ch in entries]})
    drops("star_warden", [("reapers_mark", 4, 0.12), ("reapers_mark", 5, 0.05), ("bloodthirst", 4, 0.12), ("bloodthirst", 5, 0.05),
                          ("cleave", 5, 0.15), ("vitality", 6, 0.15), ("soulrend", 3, 0.04)])
    drops("astral_sentinel", [("cleave", 3, 0.08), ("bloodthirst", 3, 0.08), ("steadfast", 3, 0.06), ("vitality", 4, 0.08)])


def bosses(pack):
    pack.json("skyrpg/dungeon_bosses/astral_sentinel.json", {
        "name": "The Astral Sentinel", "entity": "minecraft:enderman", "dungeon": DUN, "tier": "champion", "level": 62,
        "health": 24000, "damage": 40, "speed": 0.3, "scale": 1.8, "knockback_resistance": 0.85, "bar_color": "blue",
        "health_per_player": 0.6, "coins": [14000, 18000], "delving_xp": 6000,
        "intro": "Something tall unfolds beneath the orrery, starlight pooling in its eyes: 'The sky is closed to the unlettered.'",
        "death": "The Sentinel folds in on itself like a collapsing star. The bars lift, and a key of cold steel rolls across the floor.",
        "max_minions": 4, "loot": "skyrpg_world:bosses/astral_sentinel", "reset_ticks": 72000,
        "first_clear": {"coins": 16000, "xp": {"skyrpg_skills:combat": 7000}},
        "moves": [
            {"type": "blink", "every": 160, "warn": "The Sentinel flickers out of sight!"},
            {"type": "slam", "every": 230, "radius": 6, "damage": 38, "knockup": 0.9, "windup": 20, "particle": "minecraft:portal",
             "warn": "The Sentinel brings a long arm down!"},
            {"type": "volley", "every": 260, "projectile": "arrow", "count": 5, "damage": 18, "spread": 0.16,
             "warn": "The Sentinel looses a flurry of star-bolts!"},
            {"type": "ward", "every": 800, "below": 0.5, "duration": 100, "reduction": 0.75,
             "warn": "The Sentinel wraps itself in a shell of light!"},
            {"type": "summon", "every": 480, "max": 4,
             "mobs": [{"type": "minecraft:endermite", "count": 3, "level": 62, "name": "Void Mite"}],
             "warn": "Void Mites spill out of the Sentinel's shadow!"}]})
    pack.json("skyrpg/dungeon_bosses/star_warden.json", {
        "name": "The Star Warden", "entity": "minecraft:enderman", "dungeon": DUN, "level": 64, "health": 60000, "damage": 56,
        "speed": 0.3, "scale": 2.5, "knockback_resistance": 1.0, "bar_color": "purple", "health_per_player": 0.6,
        "coins": [60000, 80000], "delving_xp": 28000,
        "intro": "The great lens swings round and the dome goes dark. 'I keep the sky. I have kept it since before your world was lit. You are a smudge on the glass.'",
        "death": "The Warden's light goes out one star at a time. The dome brightens with the real sky, and his heart drops to the floor, still turning.",
        "max_minions": 8, "loot": "skyrpg_world:bosses/star_warden", "reward_loot": "skyrpg_world:rewards/star_warden",
        "first_clear": {"coins": 110000, "xp": {"skyrpg_skills:combat": 36000}},
        "reset_ticks": 72000,
        "phases": [{"below": 0.66, "message": "The lenses align! The Warden calls his acolytes out of the dark!"},
                   {"below": 0.33, "message": "The dome cracks and starlight pours in. The Warden fights without restraint!"}],
        "moves": [
            {"type": "blink", "every": 140, "warn": "The Warden steps between the stars and appears behind you!"},
            {"type": "volley", "every": 210, "projectile": "arrow", "count": 6, "damage": 22, "spread": 0.2,
             "warn": "The Warden fires a volley of starlight!"},
            {"type": "slam", "every": 230, "radius": 8, "damage": 52, "knockup": 1.0, "windup": 24, "particle": "minecraft:reverse_portal",
             "warn": "The Warden brings a fist of collapsed stars down!"},
            {"type": "pull", "every": 360, "below": 0.66, "radius": 18, "strength": 1.4, "particle": "minecraft:portal",
             "warn": "A black hole opens in the Warden's palm and drags you in!"},
            {"type": "summon", "every": 460, "below": 0.66, "max": 6,
             "mobs": [{"type": "minecraft:enderman", "count": 2, "level": 62, "name": "Astral Acolyte"},
                      {"type": "minecraft:endermite", "count": 3, "level": 62, "name": "Void Mite"}],
             "warn": "Acolytes and Void Mites step out of the dark!"},
            {"type": "ward", "every": 900, "below": 0.5, "duration": 120, "reduction": 0.8,
             "warn": "The Warden draws a shell of starlight round himself!"},
            {"type": "shockwave", "every": 300, "below": 0.33, "radius": 12, "damage": 40, "push": 2.2, "particle": "minecraft:end_rod",
             "warn": "A ring of starlight blasts out from the Warden!"},
            {"type": "enrage", "below": 0.33, "speed": 0.38, "damage": 0.4,
             "warn": "The Star Warden's eyes burn white!"}]})


# ---------------------------------------------------------------------------------------------------------------------
# The structure
# ---------------------------------------------------------------------------------------------------------------------
BIOMES = ["minecraft:end_highlands", "minecraft:end_midlands", "minecraft:end_barrens",
          {"id": "skyrpg_world:void_gardens", "required": False}, {"id": "skyrpg_world:shattered_isles", "required": False},
          {"id": "skyrpg_world:astral_fields", "required": False}]


def build(pack):
    P = lambda s: f"{CZ}/{s}"  # noqa: E731
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
            rooms.append((fn(pack, fl).path, wgt))
        pack.pool(P(f"f{fl}_rooms"), rooms, fallback=caps["side"])
        pack.pool(P(f"f{fl}_leaves"), [(fn(pack, fl).path, 1) for fn in LEAVES], fallback=caps["tail"])
    build_champion(pack)
    build_ascent(pack)
    build_dome(pack)
    pack.pool(P("entrance"), [(P("entrance"), 1)])
    pack.pool(P("champion"), [(P("f1/champion"), 1)], fallback=P("caps_spine1"))
    pack.pool(P("ascent"), [(P("ascent"), 1)], fallback=P("caps_spine1"))
    pack.pool(P("boss"), [(P("boss/dome"), 1)], fallback=P("caps_spine2"))
    pack.biome_tag(CZ, BIOMES)
    pack.structure(CZ, start_pool=P("entrance"), biomes=f"#skyrpg_world:has_structure/{CZ}", size=16, max_distance=116,
                   step="underground_structures", adaptation="none", heightmap=None, start_height=START_HEIGHT)
    pack.structure_set(CZ, 26, 8, 64151013, exclusion=("minecraft:end_cities", 6))
    loot(pack)
    tome_drops(pack)
    pack.json("skyrpg/dungeons/astral_observatory.json", {
        "key": "skyrpg_world:observatory_key", "name": "The Astral Observatory", "structure": DUN, "color": "#8FB8FF",
        "icon": "minecraft:spyglass", "level": 64, "boss": "skyrpg_world:star_warden", "reset_ticks": 72000,
        "description": "A purpur and glass observatory on the outer End islands. Climb through the Lower Halls and the Astral Sentinel to the Star Deck and the Star Warden."})
    bosses(pack)


# ---------------------------------------------------------------------------------------------------------------------
# Offline checks (the citadel's, adapted)
# ---------------------------------------------------------------------------------------------------------------------
def check_jigsaw_rules(pack):
    """Spine pools hold no corner pieces: every piece in a spine pool has exactly a south 'in' and a north 'out' jigsaw."""
    problems = []
    for pool_id, pool in pack.pools.items():
        name = pool_id.split(":")[1]
        if not name.startswith(f"{CZ}/f") or "_s" not in name:
            continue
        for e in pool["elements"]:
            el = e["element"]
            if el["element_type"] != "minecraft:single_pool_element":
                continue
            pc = pack.pieces[el["location"].split(":", 1)[1]]
            spine_doors = {j[1].split("_")[0] for j in pc.jigsaws if j[2].startswith("skyrpg_world:obs_spine")}
            if not {"north", "south"} <= spine_doors:
                problems.append(f"{pc.path}: spine piece without a straight north/south pair")
            if "east" in spine_doors or "west" in spine_doors:
                problems.append(f"{pc.path}: a corner piece in a spine pool")
    return problems


def reach_table(pack):
    """Worst-case distances from the entrance centre along the spine axes (jigsaw max_distance is 116; keep <= 110)."""
    ent = pack.pieces[f"{CZ}/entrance"].size
    a = ent[2] // 2 + 1
    print("reach table (worst case straight chain, blocks from the entrance centre; limit 110 per axis, 116 hard):")
    print(f"  courtyard north door plane            A = {a:5.1f}")
    worst = 0
    for st in ("f1_s1", "f1_s2", "f1_s3", "champion", "ascent"):
        dep = max(s[2] for s in ic.stage_sizes(pack, st))
        a += dep
        print(f"  after {st:9s} (max depth {dep:2d})        A = {a:5.1f}")
        worst = max(worst, a)
    a_axis = a - pack.pieces[f"{CZ}/ascent"].size[2] + (pack.pieces[f"{CZ}/ascent"].size[2] - 1 - 15) + 1
    print(f"  floor 2 runs along B at A = {a_axis:5.1f}")
    b = 1 + 1
    wid_max = 0
    for st in ("f2_s1", "f2_s2", "f2_s3", "boss"):
        dep = max(s[2] for s in ic.stage_sizes(pack, st))
        wid = max(s[0] for s in ic.stage_sizes(pack, st))
        wid_max = max(wid_max, wid)
        b += dep
        print(f"  after {st:6s} (depth {dep:2d}, widest {wid:2d})   B = {b:5.1f}   A extent to {a_axis + wid / 2:5.1f}")
    # the ascent's own width is on B before floor 2 starts: its east door is at x = 18
    b += pack.pieces[f"{CZ}/ascent"].size[0] - 8
    a_far = a_axis + wid_max / 2
    print(f"  worst: A {max(a, a_far):5.1f}  B {b:5.1f}")
    return max(a, a_far, b)


def check_ids(pack):
    return ic.check_blocks(pack) + ic.check_items(pack)


if __name__ == "__main__":
    ic.MOD_BLOCKS = {"skyrpg_world:waystone"}
    pack = Pack()
    build(pack)
    if len(sys.argv) > 3 and sys.argv[1] == "--show":
        ic.show(pack, sys.argv[2], int(sys.argv[3]))
        sys.exit(0)
    problems = ic.walk_check(pack) + ic.check_packs(pack) + check_jigsaw_rules(pack)
    n_bad = check_ids(pack)
    ic.check_mod_items(pack)
    for pr in problems:
        print("PROBLEM:", pr)
    ic.report(pack)
    reach = reach_table(pack)
    if reach > 110:
        problems.append(f"reach {reach} exceeds 110")
        print("PROBLEM: reach exceeds 110")
    avg, capavg = ic.expected_pieces(pack)
    print("expected pieces per floor (Monte-Carlo, collisions ignored), without caps:", {k: round(v, 1) for k, v in avg.items()},
          "caps:", {k: round(v, 1) for k, v in capavg.items()})
    if problems or n_bad:
        raise SystemExit("offline checks failed")
    pack.save(clean=[CZ])
