"""The Abandoned Mine Camp (skyrpg_world:mine_camp): a small camp in a cave room, common, between y 4 and 30.

One 27 x 11 x 27 template. It carves its own cave room (an ellipsoid dome, floor at local y=0, standing level y=1) and
lines it in a thin shell of stone, so it never floats as a block of rock and never sits as a bare box in solid stone;
two short rail tunnels run out of it east and west (they open into whatever cave is there, or dead-end in rock).
Contents: a tent with a per-player loot chest, a campfire, a crate pile (loot barrel), a rail line with a chest minecart,
lanterns on fence posts, torches, ore veins in the walls, and two packs of Lost Miners (spawner markers work without a
dungeon: they use the nearest entrance if there is one, otherwise a 128-block area for their run).

No project_start_to_heightmap: start_height is a uniform absolute y of 4..30 (template y=1 sits at that height).
The loot chest sometimes (15%) holds a treasure map to the Dwarven Halls (#skyrpg_world:on_dwarven_maps, written by
dwarven_halls.py).

Run:  python tools/builds/dungeons/mine_camp.py
"""
import math
import pathlib
import random
import sys

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parents[1]))
sys.path.insert(0, str(pathlib.Path(__file__).resolve().parent))
from dungeon_kit import Pack, Piece  # noqa: E402
import dwarven_halls as dh  # noqa: E402  (mobs, loot helpers, walk check, block check)

MC = "mine_camp"
AIR = "minecraft:air"
W = D = 27
H = 11
CX = CZ = 13
RNG = random.Random("mine_camp")

STONE = [("minecraft:stone", 50), ("minecraft:andesite", 10), ("minecraft:tuff", 14), ("minecraft:cobblestone", 8),
         ("minecraft:gravel", 3), ("minecraft:coal_ore", 4), ("minecraft:iron_ore", 4), ("minecraft:copper_ore", 3),
         ("skyrpg_mining:skyiron_ore", 2), ("minecraft:calcite", 2)]
ROCK = {b for b, _ in STONE}


def pick(table):
    return dh.wchoice(RNG, table)


def noise(x, y, z, t=(0.63, 0.41, 0.77, 0.52)):
    return (math.sin(0.55 * x + t[0]) + math.sin(0.47 * z + t[1]) + math.sin(0.43 * y + 0.31 * x + t[2]) +
            math.sin(0.39 * (x + z) + t[3])) / 4


def lost_miner(n, lvl, name="Lost Miner"):
    return dh.miner(n, lvl, name)


def build(pack):
    p = Piece(f"{MC}/camp", W, H, D)
    air = set()

    def carve(x, y, z):
        p.set(x, y, z, AIR)
        air.add((x, y, z))

    # the cave room: an ellipsoid dome, plus two 3x3 tunnels along z=18
    for x in range(1, W - 1):
        for z in range(1, D - 1):
            for y in range(1, H - 1):
                v = ((x - CX) / 11.8) ** 2 + ((z - CZ) / 11.8) ** 2 + ((y - 1) / 6.2) ** 2
                if v < 1 + 0.22 * noise(x, y, z):
                    carve(x, y, z)
    for x in list(range(0, 7)) + list(range(20, W)):
        for z in (17, 18, 19):
            for y in (1, 2, 3):
                carve(x, y, z)
    # floor: a solid layer under everything carved
    for (x, y, z) in list(air):
        if y == 1:
            p.set(x, 0, z, pick([("minecraft:stone", 40), ("minecraft:cobblestone", 20), ("minecraft:gravel", 10), ("minecraft:coarse_dirt", 12),
                                 ("minecraft:tuff", 18)]))
    # a thin shell round the whole thing
    for (x, y, z) in sorted(air):
        for dx, dy, dz in ((1, 0, 0), (-1, 0, 0), (0, 1, 0), (0, -1, 0), (0, 0, 1), (0, 0, -1)):
            n = (x + dx, y + dy, z + dz)
            if n not in air and p.inside(*n) and p.get(*n) is None:
                p.set(*n, pick(STONE))
    # the tunnel mouths stay open to the piece edge; the floor row under them already exists
    # -- rails along z=18, with a chest minecart and an empty one
    for x in range(0, W):
        if (x, 1, 18) in air:
            p.set(x, 1, 18, "minecraft:powered_rail[powered=false,shape=east_west]" if x % 6 == 3 else "minecraft:rail[shape=east_west]")
    p.entity(16, 1, 18, {"id": "minecraft:chest_minecart", "LootTable": "skyrpg_world:chests/mine_camp_cart"})
    p.entity(8, 1, 18, {"id": "minecraft:minecart"})
    # -- the tent (x 6..10, z 7..13, ridge at x=8; open at the south end), a per-player loot chest inside
    for z in range(7, 14):
        p.set(8, 4, z, "minecraft:stripped_spruce_log[axis=z]")
        for x in (7, 9):
            p.set(x, 3, z, "minecraft:brown_wool")
        for x in (6, 10):
            for y in (1, 2):
                p.set(x, y, z, "minecraft:brown_wool")
        for x in (7, 8, 9):
            for y in (1, 2):
                carve(x, y, z)
        carve(8, 3, z)
    for x in (7, 8, 9):                    # back wall of the tent
        for y in (1, 2):
            p.set(x, y, 7, "minecraft:brown_wool")
    p.set(8, 3, 7, "minecraft:brown_wool")
    for x, z in ((6, 7), (10, 7), (6, 13), (10, 13)):
        p.set(x, 3, z, "minecraft:spruce_fence")
    p.set(8, 3, 11, "minecraft:lantern[hanging=true]")
    p.set(8, 1, 9, "minecraft:brown_bed[facing=north,part=head]")
    p.set(8, 1, 10, "minecraft:brown_bed[facing=north,part=foot]")
    p.chest(9, 1, 8, "west", "skyrpg_world:chests/mine_camp", tier="common")
    p.set(7, 1, 8, "minecraft:crafting_table")
    p.set(7, 1, 9, "minecraft:barrel[facing=up]")
    # -- campfire corner
    p.set(13, 1, 12, "minecraft:campfire[facing=north,lit=true]")
    for x, z, fc in ((13, 10, "south"), (15, 12, "west"), (11, 12, "east")):
        p.set(x, 1, z, f"minecraft:spruce_stairs[facing={fc},half=bottom]")
    p.set(15, 1, 10, "minecraft:cauldron")
    p.set(11, 1, 10, "minecraft:hay_block[axis=y]")
    # -- the crate pile (a loot barrel in it)
    for x, z in ((18, 7), (19, 7), (20, 7), (18, 8), (19, 8)):
        p.set(x, 1, z, "minecraft:barrel[facing=up]")
    p.set(20, 1, 8, "minecraft:oak_planks")
    for x, z in ((18, 7), (19, 7), (18, 8)):
        p.set(x, 2, z, "minecraft:oak_planks" if (x + z) % 2 else "minecraft:barrel[facing=up]")
    p.set(19, 3, 7, "minecraft:oak_planks")
    p.barrel(20, 1, 9, "west", "skyrpg_world:chests/mine_camp_crate", tier="common")
    p.set(21, 1, 9, "minecraft:raw_iron_block")
    # -- lanterns on fence posts, torches, a mining spot with ore blocks
    for x, z in ((3, 15), (23, 15), (10, 21), (17, 21), (13, 4)):
        if (x, 1, z) in air:
            p.set(x, 1, z, "minecraft:spruce_fence")
            p.set(x, 2, z, "minecraft:lantern[hanging=false]")
    cells = [c for c in sorted(air) if c[1] == 1 and p.get(*c) == AIR and p.get(c[0], 0, c[2]) is not None]
    RNG.shuffle(cells)
    for (x, y, z) in cells[:14]:
        if any(abs(x - tx) <= 1 and abs(z - tz) <= 1 for tx, tz in ((18, 7), (8, 10), (13, 12))):
            continue
        if z not in (17, 18, 19):
            p.set(x, 1, z, "minecraft:torch")
    for (x, y, z) in sorted(air):
        if y >= 5 and (x, y + 1, z) not in air and RNG.random() < .03:
            p.set(x, y, z, "minecraft:lantern[hanging=true]")
    for x, z in ((11, 3), (15, 3), (4, 14), (22, 12)):          # a mined-out vein: ore left in the wall, a broken pick (iron nugget pile)
        for y in (1, 2, 3):
            n = (x, y, z)
            if p.get(*n) in ROCK:
                p.set(*n, "skyrpg_mining:skyiron_ore" if y == 2 else "minecraft:iron_ore")
    # -- two packs of Lost Miners guard it (spawner markers work outside dungeons)
    p.marker(9, 1, 20, "spawner", radius=11, spread=3,
             waves=[[lost_miner(3, 14)], [lost_miner(2, 15), dh.archer(1, 15, "Miner's Lookout")]])
    p.marker(19, 1, 12, "spawner", radius=10, spread=3, mobs=[lost_miner(3, 14), dh.archer(1, 15, "Miner's Lookout")])
    dh.autoconnect(p)
    pack.add(p)
    pack.pool(f"{MC}/camp", [(f"{MC}/camp", 1)])
    pack.biome_tag(MC, ["#minecraft:is_overworld"])
    pack.structure(MC, start_pool=f"{MC}/camp", biomes=f"#skyrpg_world:has_structure/{MC}", size=1, max_distance=24,
                   step="underground_structures", adaptation="none", heightmap=None, start_height=0)
    pack.files["data/skyrpg_world/worldgen/structure/mine_camp.json"]["start_height"] = {
        "type": "minecraft:uniform", "min_inclusive": {"absolute": 4}, "max_inclusive": {"absolute": 30}}
    pack.structure_set(MC, 12, 4, 61201716)
    loot(pack)
    return p


def loot(pack):
    it, lpool, table, rolls, M = dh.it, dh.lpool, dh.table, dh.rolls, dh.M
    map_fn = [{"function": "minecraft:exploration_map", "destination": "skyrpg_world:on_dwarven_maps",
               "decoration": "minecraft:red_x", "zoom": 2, "skip_existing_chunks": False},
              {"function": "minecraft:set_name", "target": "item_name", "name": "Dwarven Halls Map"}]
    pack.loot_table("chests/mine_camp", table(
        lpool(rolls(4, 6), [it(M + "skyiron_ingot", 8, 1, 4), it(M + "glimmer_shard", 9, 2, 6), it(M + "glimmer_crystal", 3, 1, 2),
                            it("minecraft:torch", 10, 4, 12), it("minecraft:bread", 8, 2, 5), it("minecraft:cooked_beef", 5, 1, 3),
                            it("minecraft:baked_potato", 6, 2, 5), it("minecraft:apple", 5, 1, 3), it("minecraft:coal", 8, 3, 8),
                            it("minecraft:iron_ingot", 6, 2, 5), it("minecraft:iron_nugget", 5, 3, 9), it("minecraft:rail", 5, 4, 10),
                            it("skyrpg_enchants:runic_dust", 2, 1, 2), it("minecraft:experience_bottle", 3, 1, 3)]),
        lpool(1, [it(M + "duskquartz_shard", 1, 1, 3)], .25),
        lpool(1, [it("minecraft:map", 1, fn=map_fn)], .15)))
    pack.loot_table("chests/mine_camp_crate", table(
        lpool(rolls(2, 4), [it("minecraft:torch", 10, 4, 10), it("minecraft:bread", 8, 2, 4), it("minecraft:cooked_porkchop", 5, 1, 3),
                            it("minecraft:carrot", 5, 2, 4), it("minecraft:coal", 8, 2, 6), it(M + "glimmer_shard", 6, 1, 4),
                            it(M + "skyiron_ingot", 4, 1, 2), it("minecraft:arrow", 5, 4, 10), it("minecraft:iron_nugget", 5, 3, 8)])))
    pack.loot_table("chests/mine_camp_cart", table(
        lpool(rolls(2, 3), [it(M + "skyiron_ingot", 5, 1, 2), it("minecraft:coal", 8, 3, 8), it(M + "glimmer_shard", 6, 2, 4),
                            it("minecraft:iron_nugget", 8, 4, 10), it("minecraft:rail", 7, 4, 10), it("minecraft:torch", 5, 3, 8)])))


if __name__ == "__main__":
    pack = Pack()
    camp = build(pack)
    problems = pack.validate()
    # walk test: the tunnels' open ends stand in for the doors
    from collections import Counter
    markers = Counter(e["nbt"]["data"]["skyrpg"] for e in camp.entities if "data" in e["nbt"])
    bad_blocks = dh.check_blocks(pack) + dh.check_items(pack)
    air_cells = sum(1 for c in camp.cells.values() if c[0]["id"] == AIR)
    print(f"camp: {camp.size}, {len(camp.cells)} cells set, {air_cells} air, markers {dict(markers)}")
    if problems or bad_blocks:
        raise SystemExit("problems: " + "; ".join(problems))
    pack.save(clean=["mine_camp"])
