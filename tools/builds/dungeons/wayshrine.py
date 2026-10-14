"""The Wayshrine (skyrpg_world:wayshrine): a small mossy ruined shrine around a waystone block.

One 9x9x9 template; the shrine occupies x/z 1..7.  Rows y=0..1 are foundation, y=1 is ground level (start_height -2
puts template y=2 on the first air block above the heightmap), the dais is y=2 and the waystone y=3.
"""
import sys, pathlib, random, json
sys.path.insert(0, str(pathlib.Path(__file__).resolve().parents[1]))
from dungeon_kit import Pack, Piece

SB = "minecraft:stone_bricks"
MSB = "minecraft:mossy_stone_bricks"
CSB = "minecraft:cracked_stone_bricks"
MC = "minecraft:mossy_cobblestone"
CHISEL = "minecraft:chiseled_stone_bricks"
AIR = "minecraft:air"


def add_pool(pack, name, elements, fallback="minecraft:empty"):
    pack.pool(name, elements, fallback=fallback)


def shrine():
    rng = random.Random("wayshrine")
    p = Piece("wayshrine/shrine", 9, 9, 9)
    # foundation + ground ring
    p.fill(1, 0, 1, 7, 0, 7, "minecraft:cobblestone")
    for x in range(1, 8):
        for z in range(1, 8):
            p.set(x, 1, z, rng.choice([SB, MSB, MSB, CSB, MC, "minecraft:cobblestone"]))
    # chip the corners so it reads as a ruin
    for x, z in ((1, 1), (7, 1), (1, 7), (7, 7)):
        p.set(x, 1, z, "minecraft:mossy_cobblestone")
    # dais
    p.fill(3, 2, 3, 5, 2, 5, "minecraft:polished_andesite")
    p.set(4, 2, 4, CHISEL)
    p.set(4, 3, 4, "skyrpg_world:waystone")
    for pos, facing in (((4, 2, 2), "south"), ((4, 2, 6), "north"), ((2, 2, 4), "east"), ((6, 2, 4), "west")):
        p.set(*pos, f"minecraft:stone_brick_stairs[facing={facing},half=bottom]")
    for x, z in ((3, 3), (5, 3), (3, 5), (5, 5)):
        p.set(x, 3, z, f"minecraft:candle[candles={rng.randint(1, 3)},lit=true]")
    # broken pillars: (x, z, height above ground)
    for x, z, h, lit in ((1, 1, 5, True), (7, 1, 3, False), (1, 7, 2, False), (7, 7, 5, True), (1, 4, 3, False),
                         (7, 4, 4, False)):
        for y in range(2, 2 + h):
            p.set(x, y, z, rng.choice([SB, MSB, MSB, CSB]))
        p.set(x, 2, z, MSB)
        if h >= 4:
            p.set(x, 1 + h, z, CHISEL)
            if lit:
                p.set(x, 2 + h, z, "minecraft:lantern[hanging=false]")
        else:
            p.set(x, 1 + h, z, "minecraft:mossy_stone_brick_slab[type=bottom]" if h % 2 else
                  "minecraft:stone_brick_stairs[facing=north,half=bottom]")
    # broken arch over the north entry
    p.set(3, 2, 1, "minecraft:mossy_stone_brick_wall")
    p.set(5, 2, 1, "minecraft:mossy_stone_brick_wall")
    p.set(3, 3, 1, "minecraft:mossy_stone_brick_wall")
    p.set(4, 4, 1, "minecraft:stone_brick_slab[type=top]")
    p.set(3, 4, 1, "minecraft:stone_brick_stairs[facing=east,half=top]")
    # chest, a hanging lantern, moss and webs
    p.chest(6, 2, 6, "north", "chests/wayshrine")
    p.set(6, 2, 5, "minecraft:lantern[hanging=false]")
    p.set(2, 2, 6, "minecraft:mossy_cobblestone_wall")
    p.set(2, 3, 6, "minecraft:lantern[hanging=false]")
    for _ in range(9):
        x, z = rng.randint(1, 7), rng.randint(1, 7)
        if p.get(x, 2, z) is None:
            p.set(x, 2, z, "minecraft:moss_carpet")
    p.weather("w", {SB: [(CSB, 0.15), (MSB, 0.25)]})
    return p


def item(name, weight, lo=None, hi=None, funcs=None):
    e = {"type": "minecraft:item", "name": name, "weight": weight}
    f = list(funcs or [])
    if lo is not None:
        f.append({"function": "minecraft:set_count",
                  "count": {"type": "minecraft:uniform", "min": lo, "max": hi if hi is not None else lo}})
    if f:
        e["functions"] = f
    return e


def build(pack):
    pack.add(shrine())
    add_pool(pack, "wayshrine/start", [("wayshrine/shrine", 1)])
    pack.structure("wayshrine", start_pool="wayshrine/start", biomes="#skyrpg_world:has_structure/wayshrine",
                   size=1, max_distance=80, step="surface_structures", adaptation="beard_thin",
                   heightmap="WORLD_SURFACE_WG", start_height=-2)
    pack.structure_set("wayshrine", spacing=24, separation=8, salt=61201714)
    pack.biome_tag("wayshrine", ["#minecraft:is_forest", "#minecraft:is_taiga", "#minecraft:is_savanna",
                                 "minecraft:plains", "minecraft:sunflower_plains", "minecraft:meadow",
                                 "minecraft:snowy_plains", "minecraft:desert", "minecraft:birch_forest",
                                 "minecraft:old_growth_birch_forest", "minecraft:cherry_grove"])
    pack.loot_table("chests/wayshrine", {"type": "minecraft:chest", "pools": [
        {"rolls": {"type": "minecraft:uniform", "min": 2, "max": 4}, "bonus_rolls": 0.0, "entries": [
            item("minecraft:bread", 12, 1, 3), item("minecraft:apple", 10, 1, 3),
            item("minecraft:cooked_beef", 8, 1, 2), item("minecraft:carrot", 8, 2, 4),
            item("minecraft:torch", 14, 3, 8), item("minecraft:arrow", 8, 4, 10),
            item("minecraft:string", 6, 1, 3), item("minecraft:stick", 6, 2, 6),
            item("minecraft:iron_nugget", 5, 2, 6)]},
        {"rolls": 1, "bonus_rolls": 0.0, "conditions": [{"condition": "minecraft:random_chance", "chance": 0.35}],
         "entries": [item("skyrpg_enchants:runic_dust", 1, 1, 2)]},
        {"rolls": 1, "bonus_rolls": 0.0, "conditions": [{"condition": "minecraft:random_chance", "chance": 0.25}],
         "entries": [item("minecraft:map", 1, funcs=[
             {"function": "minecraft:exploration_map", "destination": "skyrpg_world:on_crypt_maps",
              "decoration": "minecraft:red_x", "zoom": 2, "skip_existing_chunks": False},
             {"function": "minecraft:set_name", "target": "item_name", "name": "Forgotten Crypt Map"}])]},
    ]})


if __name__ == "__main__":
    pack = Pack()
    build(pack)
    pack.save(clean=["wayshrine"])
