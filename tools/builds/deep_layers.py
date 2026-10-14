"""Writes the deep layer data for the skyrpg_world mod (The Deep, docs/design/the_deep.md): the four underground layers,
the worldgen feature that restyles their caves, the layer blocks' loot tables and tags, their Mining XP, and sell
prices for the layer drops. Run: python tools/builds/deep_layers.py
(The blocks' and items' art, models, lang and item definitions come from tools/art/generate_world.py.)

Layer format, data/<ns>/skyrpg/deep_layers/<id>.json:
  name, color (title colour), min_y (inclusive), max_y (exclusive), danger (skulls), level_bonus (monster levels on top
  of the usual depth levels), description,
  replace: blocks or #tags a cave surface may be made of to be restyled or decorated (default #base_stone_overworld),
  coverage: share of those surfaces restyled (0..1, default 0.6; a 3D noise picks which, so it comes in patches),
  palette: {"floor"|"wall"|"ceiling"|"any": [{block, weight}]}: what the surfaces become, by which way they face (floor:
           air above it, ceiling: air below, wall: air beside); materials are picked per few-block cell so they clump,
  decorations: [{type, on: floor|wall|ceiling, block, density (per 100 surfaces of that kind) or count: [min, max]
           (per chunk), chance (per chunk, default 1), ...}], types:
             place          the block in the air next to the surface (clusters face out, lanterns hang, lichen clings)
             replace        the surface block itself becomes the block (runestones, ores)
             column         a pillar from a floor (or spike from a ceiling): height [min, max], tip (last block)
             hanging        from a ceiling: chain [block] x height [min, max], then the block (a lantern)
             giant_mushroom on a floor: stem block, height [min, max], cap of the block with radius [min, max]
             pool           sunk into a floor where solid ground holds it in: radius [min, max], rim block
  mobs: [dungeon mob entry + weight + count: [min, max]] spawned near survival players in dark cave air (unknown
        types are skipped with one warning), max_mobs (nearby cap), max_light (block light allowed at the spot),
  drops: [{item, chance, count: [min, max], rare}] from monsters players kill inside,
  particle, particle_count (ambient, around players inside, every half second).
A player or monster is in a layer when it is in the overworld, inside the band and can't see the sky.
"""
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
DATA = ROOT / "world" / "src" / "main" / "resources" / "data"
W = "skyrpg_world:"
MINING = "skyrpg_mining:"

REPLACE = ["#minecraft:base_stone_overworld", "minecraft:dirt", "minecraft:gravel"]


def opts(*pairs):
    return [{"block": block, "weight": weight} for block, weight in pairs]


def lost_miner(weight, pick, helmet):
    return {"type": "minecraft:zombie", "weight": weight, "count": [1, 2], "name": "Lost Miner",
            "equipment": {"mainhand": pick, "head": helmet}}


LAYERS = {
    "glimmer_caverns": {
        "name": "Glimmer Caverns",
        "color": "#B89CFF",
        "min_y": 0,
        "max_y": 40,
        "danger": 1,
        "level_bonus": 4,
        "description": "Caves where the stone itself glitters. Miners came down for the Glimmer, and some of them never left.",
        "replace": REPLACE,
        "coverage": 0.6,
        "palette": {
            "floor": opts((W + "glimmerstone", 3), ("minecraft:calcite", 1)),
            "wall": opts((W + "glimmerstone", 3), ("minecraft:calcite", 2)),
            "ceiling": opts((W + "glimmerstone", 3), ("minecraft:calcite", 1)),
        },
        "decorations": [
            {"type": "place", "on": "floor", "block": W + "glimmer_cluster", "density": 4},
            {"type": "place", "on": "ceiling", "block": W + "glimmer_cluster", "density": 2},
            {"type": "place", "on": "wall", "block": W + "glimmer_cluster", "density": 0.8},
            {"type": "place", "on": "wall", "block": "minecraft:glow_lichen", "density": 4},
            {"type": "place", "on": "ceiling", "block": "minecraft:glow_lichen", "density": 3},
        ],
        "mobs": [
            {"type": "skyrpg_combat:glimmer_crawler", "weight": 3, "count": [2, 3]},
            lost_miner(2, "minecraft:iron_pickaxe", "minecraft:iron_helmet"),
            lost_miner(2, "minecraft:stone_pickaxe", "minecraft:leather_helmet"),
            {"type": "minecraft:cave_spider", "weight": 2, "count": [1, 2]},
        ],
        "max_mobs": 6,
        "max_light": 8,
        "drops": [{"item": W + "glimmer_geode", "chance": 0.03, "rare": True}],
        "particle": "minecraft:end_rod",
        "particle_count": 2,
    },
    "fungal_depths": {
        "name": "Fungal Depths",
        "color": "#4FD8C0",
        "min_y": -20,
        "max_y": 0,
        "danger": 2,
        "level_bonus": 7,
        "description": "A forest of glowing mushrooms under the world. Mind the puffballs, and whatever is eating the spores.",
        "replace": REPLACE,
        "coverage": 0.75,
        "palette": {
            "floor": opts(("minecraft:mycelium", 4), ("minecraft:moss_block", 3), ("minecraft:rooted_dirt", 1)),
            "wall": opts(("minecraft:moss_block", 3), ("minecraft:rooted_dirt", 1)),
            "ceiling": opts(("minecraft:moss_block", 2), ("minecraft:rooted_dirt", 2)),
        },
        "decorations": [
            {"type": "place", "on": "floor", "block": W + "glowcap", "density": 12},
            {"type": "place", "on": "floor", "block": W + "spore_puffball", "density": 1.2},
            {"type": "place", "on": "floor", "block": "minecraft:moss_carpet", "density": 3},
            {"type": "giant_mushroom", "on": "floor", "block": W + "glowcap_block", "stem": "minecraft:mushroom_stem",
             "count": [4, 7], "chance": 0.9, "height": [3, 6], "radius": [1, 3]},
            {"type": "place", "on": "ceiling", "block": "minecraft:spore_blossom", "density": 0.5},
            {"type": "place", "on": "ceiling", "block": "minecraft:hanging_roots", "density": 3},
            {"type": "place", "on": "wall", "block": "minecraft:glow_lichen", "density": 1.5},
        ],
        "mobs": [
            {"type": "skyrpg_combat:sporeling", "weight": 4, "count": [2, 3]},
            {"type": "minecraft:zombie", "weight": 3, "count": [1, 2], "name": "Spore Shambler", "equipment": {"head": W + "glowcap_block"}},
            {"type": "minecraft:cave_spider", "weight": 2, "count": [1, 2]},
        ],
        "max_mobs": 8,
        "max_light": 9,
        "drops": [{"item": W + "glowcap_spores", "chance": 0.3, "count": [1, 2]}],
        "particle": "minecraft:spore_blossom_air",
        "particle_count": 8,
    },
    "dwarven_halls": {
        "name": "Dwarven Halls",
        "color": "#E0A040",
        "min_y": -40,
        "max_y": -20,
        "danger": 3,
        "level_bonus": 10,
        "description": "The dwarves dressed these caves in stone and runes, long ago. Their machines still keep the halls.",
        "replace": REPLACE,
        "coverage": 0.6,
        "palette": {
            "floor": opts(("minecraft:polished_deepslate", 3), ("minecraft:deepslate_tiles", 3), ("minecraft:cracked_deepslate_tiles", 1)),
            "wall": opts(("minecraft:deepslate_bricks", 4), ("minecraft:cracked_deepslate_bricks", 2), ("minecraft:polished_deepslate", 1)),
            "ceiling": opts(("minecraft:deepslate_tiles", 2), ("minecraft:deepslate_bricks", 2)),
        },
        "decorations": [
            {"type": "column", "on": "floor", "block": "minecraft:polished_deepslate", "tip": "minecraft:chiseled_deepslate",
             "count": [0, 2], "chance": 0.6, "height": [2, 5]},
            {"type": "hanging", "on": "ceiling", "block": "minecraft:lantern", "chain": "minecraft:iron_chain", "height": [1, 3], "density": 0.35},
            {"type": "replace", "on": "wall", "block": W + "dwarven_runestone", "density": 1.2},
            {"type": "replace", "on": "wall", "block": MINING + "deepslate_duskquartz_ore", "density": 0.5},
        ],
        "mobs": [
            {"type": "skyrpg_combat:dwarven_construct", "weight": 4, "count": [1, 1]},
            lost_miner(3, "minecraft:iron_pickaxe", "minecraft:iron_helmet"),
            {"type": "minecraft:skeleton", "weight": 2, "count": [1, 2]},
        ],
        "max_mobs": 8,
        "max_light": 7,
        "drops": [{"item": W + "dwarven_cog", "chance": 0.25, "count": [1, 1]}],
        "particle": "minecraft:white_ash",
        "particle_count": 6,
    },
    "magma_rifts": {
        "name": "Magma Rifts",
        "color": "#FF5A1F",
        "min_y": -64,
        "max_y": -40,
        "danger": 4,
        "level_bonus": 14,
        "description": "The bottom of the world, split open and glowing. Everything down here is on fire, or about to be.",
        "replace": REPLACE,
        "coverage": 0.6,
        "palette": {
            "floor": opts(("minecraft:basalt", 3), ("minecraft:blackstone", 3), ("minecraft:magma_block", 1)),
            "wall": opts(("minecraft:basalt", 3), ("minecraft:blackstone", 3), ("minecraft:smooth_basalt", 1)),
            "ceiling": opts(("minecraft:blackstone", 2), ("minecraft:basalt", 2), ("minecraft:smooth_basalt", 1)),
        },
        "decorations": [
            {"type": "pool", "on": "floor", "block": "minecraft:lava", "rim": "minecraft:magma_block", "count": [1, 3], "chance": 0.75,
             "radius": [1, 3]},
            {"type": "column", "on": "floor", "block": "minecraft:basalt", "density": 0.6, "height": [1, 4]},
            {"type": "column", "on": "ceiling", "block": "minecraft:basalt", "density": 0.6, "height": [1, 3]},
            {"type": "replace", "on": "wall", "block": MINING + "deepslate_emberite_ore", "density": 0.8},
        ],
        "mobs": [
            {"type": "skyrpg_combat:fire_beetle", "weight": 4, "count": [1, 2]},
            {"type": "minecraft:magma_cube", "weight": 3, "count": [1, 2]},
            {"type": "minecraft:blaze", "weight": 1, "count": [1, 1]},
        ],
        "max_mobs": 8,
        "max_light": 9,
        "drops": [{"item": W + "rift_ember", "chance": 0.25, "count": [1, 2]}],
        "particle": "minecraft:lava",
        "particle_count": 2,
    },
}

SURVIVES = {"type": "minecraft:survives_explosion"}


def block_loot(block, entry, condition=SURVIVES):
    return {"type": "minecraft:block",
            "pools": [{"rolls": 1, "entries": [entry], "condition": condition}],
            "random_sequence": f"{W}blocks/{block}"}


def count(lo, hi):
    return {"type": "minecraft:set_count", "count": {"type": "minecraft:uniform", "min": lo, "max": hi}}


def silk_or(block, entry):
    """The block itself with Silk Touch, otherwise the entry (like vanilla's amethyst clusters and mushroom blocks)."""
    return {"type": "minecraft:alternatives", "children": [
        {"type": "minecraft:item", "condition": "minecraft:tool/can_silk_touch", "name": W + block}, entry]}


BLOCK_LOOT = {
    "glimmerstone": block_loot("glimmerstone", {"type": "minecraft:item", "name": W + "glimmerstone"}),
    "glimmer_cluster": block_loot("glimmer_cluster", silk_or("glimmer_cluster", {"type": "minecraft:item", "name": MINING + "glimmer_shard",
                                                                              "modifier": count(2, 4)})),
    "glowcap": block_loot("glowcap", {"type": "minecraft:item", "name": W + "glowcap"}),
    "glowcap_block": block_loot("glowcap_block", silk_or("glowcap_block", {"type": "minecraft:item", "name": W + "glowcap", "modifier": count(0, 2)})),
    # A spent (burst) puffball drops nothing.
    "spore_puffball": block_loot("spore_puffball", {"type": "minecraft:item", "name": W + "spore_puffball"},
                                 {"type": "minecraft:all_of", "terms": [
                                     SURVIVES,
                                     {"type": "minecraft:match_block", "blocks": W + "spore_puffball", "state": {"burst": "false"}}]}),
    "dwarven_runestone": block_loot("dwarven_runestone", {"type": "minecraft:item", "name": W + "dwarven_runestone"}),
}

TAGS = {
    ("minecraft", "mineable/pickaxe"): [W + "glimmerstone", W + "glimmer_cluster", W + "dwarven_runestone"],
    ("minecraft", "mineable/axe"): [W + "glowcap_block"],
    # The mining mod's Glimmer Shard chance from stone-like blocks.
    ("skyrpg_mining", "shard_stone"): [W + "glimmerstone", W + "dwarven_runestone"],
}

MINING_XP = {W + "glimmerstone": 2, W + "glimmer_cluster": 3, W + "dwarven_runestone": 4}

SELL_PRICES = {
    W + "glowcap_spores": 45,
    W + "dwarven_cog": 70,
    W + "rift_ember": 90,
    W + "glimmer_geode": 150,
    W + "thanes_rune": 2200,
}


def write(path, data):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(data, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")


def merge_tag(path, values):
    """Adds values to a tag file, keeping what's there (regions.py writes to the same mineable tags)."""
    existing = json.loads(path.read_text(encoding="utf-8")) if path.exists() else {}
    merged = list(existing.get("values", []))
    merged += [v for v in values if v not in merged]
    data = {"replace": existing["replace"]} if "replace" in existing else {}
    data["values"] = merged
    write(path, data)


def main():
    for name, layer in LAYERS.items():
        write(DATA / "skyrpg_world" / "skyrpg" / "deep_layers" / f"{name}.json", layer)
    write(DATA / "skyrpg_world" / "worldgen" / "feature" / "deep_layer.json", {"type": "skyrpg_world:deep_layer"})
    write(DATA / "skyrpg_world" / "worldgen" / "placed_feature" / "deep_layer.json", {"feature": "skyrpg_world:deep_layer", "placement": []})
    for block, table in BLOCK_LOOT.items():
        write(DATA / "skyrpg_world" / "loot_table" / "blocks" / f"{block}.json", table)
    for (namespace, tag), values in TAGS.items():
        path = DATA / namespace / "tags" / "block" / f"{tag}.json"
        if namespace != "minecraft" and not path.exists():
            write(path, {"replace": False, "values": []})
        merge_tag(path, values)
    write(DATA / "skyrpg_world" / "skyrpg" / "skill_xp" / "deep_layers.json", {"skill": "skyrpg_skills:mining", "blocks": MINING_XP})
    write(DATA / "skyrpg_world" / "skyrpg" / "sell_prices" / "deep_layers.json", SELL_PRICES)
    print(f"wrote {len(LAYERS)} deep layers, the feature, {len(BLOCK_LOOT)} loot tables, tags, Mining XP and sell prices")


if __name__ == "__main__":
    main()
