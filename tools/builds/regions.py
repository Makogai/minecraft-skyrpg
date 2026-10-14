"""Writes the danger region data for the skyrpg_world mod: the four region types, the worldgen feature that corrupts
their land, and the region blocks' loot tables and tags. Run: python tools/builds/regions.py

Region type format (see docs/design/danger_regions.md):
  name, color (titles and map marker), biomes (ids or #tags at the region's centre), weight, level_bonus, danger (skulls),
  description, palette ({"block id or #tag": "block id"}, applied to every block from the treetops to just under the
  ground), decorations ([{type: scatter|spike|patch, block, chance per chunk, count, height/radius: [min, max], tip}]),
  mobs ([mob entry + weight + count: [min, max]], spawned around players inside), max_mobs, drops ([{item, chance,
  count: [min, max], rare}] from monsters killed inside), particle (ambient, around players inside).
  The Wild (docs/design/the_wild.md): boss (dungeon boss id of the region's heart boss), sigil (its Heart Sigil item),
  fog (colour the client blends the fog towards inside) and fog_density (0-1, how much), shrine (the heart's blocks:
  floor, accent, wall, pillar, light, heart), night_particle (drifts around players inside at night). Mob entries also
  take tags, speed (a multiplier), fireproof and rise (digs out of the ground). Extra decoration types: headstone,
  grave, islet (floating rock; top, height above the ground) and ore (replaces surface blocks).
"""
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
DATA = ROOT / "world" / "src" / "main" / "resources" / "data"

DREAD = {"item": "skyrpg_world:dread_crystal", "chance": 0.008, "rare": True}

REGIONS = {
    "blightlands": {
        "name": "The Blightlands",
        "color": "#D0303A",
        "biomes": ["#minecraft:is_forest", "#minecraft:is_taiga"],
        "weight": 4,
        "level_bonus": 15,
        "danger": 3,
        "description": "A rot that spreads from something buried deep. The trees are dead, the ground is grey, and the dead walk in daylight.",
        "palette": {
            "minecraft:grass_block": "skyrpg_world:blightgrass",
            "minecraft:podzol": "skyrpg_world:blightgrass",
            "minecraft:dirt": "minecraft:coarse_dirt",
            "minecraft:short_grass": "minecraft:short_dry_grass",
            "minecraft:tall_grass": "minecraft:tall_dry_grass",
            "minecraft:fern": "minecraft:dead_bush",
            "minecraft:large_fern": "minecraft:tall_dry_grass",
            "minecraft:sweet_berry_bush": "minecraft:dead_bush",
            "#minecraft:small_flowers": "minecraft:wither_rose",
            "#minecraft:leaves": "minecraft:pale_oak_leaves",
            "#minecraft:logs": "minecraft:pale_oak_log",
            "minecraft:water": "minecraft:water",
        },
        "decorations": [
            {"type": "scatter", "block": "skyrpg_world:blight_crystal", "chance": 0.55, "count": 3},
            {"type": "spike", "block": "minecraft:bone_block", "chance": 0.12, "height": [3, 7]},
            {"type": "patch", "block": "minecraft:soul_soil", "chance": 0.18, "radius": [2, 3]},
            {"type": "scatter", "block": "minecraft:skeleton_skull", "chance": 0.08, "count": 1},
        ],
        "mobs": [
            {"type": "minecraft:zombie", "weight": 4, "count": [2, 3], "name": "Blighted Dead", "equipment": {"head": "minecraft:chainmail_helmet"}},
            {"type": "minecraft:skeleton", "weight": 3, "count": [1, 2], "name": "Blight Archer", "equipment": {"head": "minecraft:leather_helmet"}},
            {"type": "skyrpg_combat:gravecaller", "weight": 2, "count": [1, 1]},
            {"type": "skyrpg_combat:hollow_wisp", "weight": 2, "count": [1, 2]},
            {"type": "minecraft:witch", "weight": 1, "count": [1, 1], "name": "Blight Witch"},
        ],
        "max_mobs": 9,
        "drops": [{"item": "skyrpg_world:blight_essence", "chance": 0.35, "count": [1, 2]}, DREAD],
        "particle": "minecraft:crimson_spore",
    },
    "ashen_wastes": {
        "name": "The Ashen Wastes",
        "color": "#E0602A",
        "biomes": ["#minecraft:is_savanna", "#minecraft:is_badlands", "minecraft:desert"],
        "weight": 3,
        "level_bonus": 18,
        "danger": 4,
        "description": "Burnt flat by a fire that never quite went out. The ground smoulders and the things that walk here like it hot.",
        "palette": {
            "minecraft:grass_block": "skyrpg_world:ashen_soil",
            "minecraft:sand": "skyrpg_world:ashen_soil",
            "minecraft:red_sand": "skyrpg_world:ashen_soil",
            "minecraft:coarse_dirt": "skyrpg_world:ashen_soil",
            "minecraft:dirt": "minecraft:coarse_dirt",
            "minecraft:short_grass": "minecraft:dead_bush",
            "minecraft:tall_grass": "minecraft:air",
            "minecraft:cactus": "minecraft:basalt",
            "#minecraft:leaves": "minecraft:air",
            "#minecraft:logs": "minecraft:stripped_dark_oak_log",
        },
        "decorations": [
            {"type": "patch", "block": "minecraft:magma_block", "chance": 0.35, "radius": [1, 3]},
            {"type": "spike", "block": "minecraft:basalt", "tip": "minecraft:blackstone", "chance": 0.3, "height": [4, 10]},
            {"type": "scatter", "block": "minecraft:blackstone", "chance": 0.25, "count": 3},
            {"type": "patch", "block": "minecraft:smooth_basalt", "chance": 0.2, "radius": [2, 4]},
        ],
        "mobs": [
            {"type": "skyrpg_combat:ember_imp", "weight": 4, "count": [1, 3]},
            {"type": "minecraft:husk", "weight": 3, "count": [2, 3], "name": "Ash Walker"},
            {"type": "minecraft:blaze", "weight": 1, "count": [1, 1], "name": "Cinder Wraith"},
            {"type": "minecraft:wither_skeleton", "weight": 1, "count": [1, 1], "name": "Charred Revenant"},
        ],
        "max_mobs": 9,
        "drops": [{"item": "skyrpg_world:cinder_essence", "chance": 0.35, "count": [1, 2]}, DREAD],
        "particle": "minecraft:white_ash",
    },
    "frostgrave": {
        "name": "Frostgrave",
        "color": "#7FC8FF",
        "biomes": ["minecraft:snowy_plains", "minecraft:snowy_taiga", "minecraft:ice_spikes", "minecraft:grove", "minecraft:snowy_slopes"],
        "weight": 3,
        "level_bonus": 18,
        "danger": 4,
        "description": "A graveyard under the snow. The cold here is older than winter, and the frozen dead remember how to hunt.",
        "palette": {
            "minecraft:snow_block": "skyrpg_world:cursed_ice",
            "minecraft:grass_block": "minecraft:packed_ice",
            "minecraft:dirt": "minecraft:packed_ice",
            "#minecraft:leaves": "minecraft:pale_oak_leaves",
            "#minecraft:logs": "minecraft:stripped_birch_log",
            "minecraft:short_grass": "minecraft:air",
            "minecraft:fern": "minecraft:air",
        },
        "decorations": [
            {"type": "spike", "block": "minecraft:packed_ice", "tip": "minecraft:blue_ice", "chance": 0.35, "height": [5, 13]},
            {"type": "patch", "block": "minecraft:powder_snow", "chance": 0.2, "radius": [1, 2]},
            {"type": "scatter", "block": "minecraft:skeleton_skull", "chance": 0.12, "count": 1},
            {"type": "patch", "block": "skyrpg_world:cursed_ice", "chance": 0.25, "radius": [2, 4]},
        ],
        "mobs": [
            {"type": "skyrpg_combat:frost_revenant", "weight": 4, "count": [1, 2]},
            {"type": "minecraft:stray", "weight": 3, "count": [1, 3], "name": "Frostbound Archer"},
            {"type": "minecraft:zombie", "weight": 2, "count": [2, 2], "name": "Frozen Dead", "equipment": {"head": "minecraft:ice"}},
        ],
        "max_mobs": 9,
        "drops": [{"item": "skyrpg_world:frost_essence", "chance": 0.35, "count": [1, 2]}, DREAD],
        "particle": "minecraft:snowflake",
    },
    "mire": {
        "name": "The Mire",
        "color": "#6FA23A",
        "biomes": ["minecraft:swamp", "minecraft:mangrove_swamp"],
        "weight": 3,
        "level_bonus": 12,
        "danger": 2,
        "description": "A poisoned swamp where the water is thick enough to walk on, almost. Hags brew in the reeds.",
        "palette": {
            "minecraft:grass_block": "skyrpg_world:mire_mud",
            "minecraft:dirt": "minecraft:mud",
            "minecraft:short_grass": "minecraft:brown_mushroom",
            "#minecraft:small_flowers": "minecraft:red_mushroom",
        },
        "decorations": [
            {"type": "patch", "block": "skyrpg_world:mire_mud", "chance": 0.35, "radius": [2, 4]},
            {"type": "scatter", "block": "minecraft:brown_mushroom", "chance": 0.4, "count": 4},
            {"type": "spike", "block": "minecraft:mud_bricks", "tip": "minecraft:mossy_cobblestone_wall", "chance": 0.08, "height": [2, 4]},
            {"type": "patch", "block": "minecraft:moss_block", "chance": 0.25, "radius": [2, 3]},
        ],
        "mobs": [
            {"type": "skyrpg_combat:bog_hag", "weight": 3, "count": [1, 1]},
            {"type": "minecraft:witch", "weight": 2, "count": [1, 2], "name": "Mire Witch"},
            {"type": "minecraft:slime", "weight": 2, "count": [1, 3]},
            {"type": "minecraft:drowned", "weight": 2, "count": [1, 2], "name": "Mire Lurker"},
        ],
        "max_mobs": 8,
        "drops": [{"item": "skyrpg_world:mire_essence", "chance": 0.35, "count": [1, 2]}, DREAD],
        "particle": "minecraft:spore_blossom_air",
    },
    "haunted_moors": {
        "name": "The Haunted Moors",
        "color": "#9AA6B8",
        "biomes": ["minecraft:plains", "minecraft:sunflower_plains", "minecraft:meadow"],
        "weight": 4,
        "level_bonus": 16,
        "danger": 3,
        "description": "Grey fog over dead grass, crooked headstones and open graves. The lights out on the moor are not lanterns.",
        "palette": {
            "minecraft:grass_block": "minecraft:podzol",
            "minecraft:dirt": "minecraft:coarse_dirt",
            "minecraft:short_grass": "minecraft:short_dry_grass",
            "minecraft:tall_grass": "minecraft:tall_dry_grass",
            "#minecraft:small_flowers": "minecraft:dead_bush",
            "#minecraft:tall_flowers": "minecraft:tall_dry_grass",
            "minecraft:fern": "minecraft:dead_bush",
            "#minecraft:leaves": "minecraft:pale_oak_leaves",
            "#minecraft:logs": "minecraft:pale_oak_log",
        },
        "decorations": [
            {"type": "headstone", "block": "minecraft:stone_brick_wall", "chance": 0.5, "count": 2, "height": [2, 3]},
            {"type": "grave", "block": "minecraft:coarse_dirt", "chance": 0.4, "count": 1},
            {"type": "patch", "block": "minecraft:coarse_dirt", "chance": 0.3, "radius": [2, 4]},
            {"type": "patch", "block": "minecraft:podzol", "chance": 0.3, "radius": [2, 4]},
            {"type": "scatter", "block": "minecraft:dead_bush", "chance": 0.5, "count": 5},
        ],
        "mobs": [
            {"type": "minecraft:vex", "weight": 4, "count": [1, 2], "name": "Moor Ghost", "tags": ["skyrpg_hallowed_only"]},
            {"type": "skyrpg_combat:gravecaller", "weight": 2, "count": [1, 1]},
            {"type": "skyrpg_combat:hollow_wisp", "weight": 3, "count": [1, 2]},
            {"type": "minecraft:zombie", "weight": 4, "count": [2, 3], "name": "Restless Dead", "rise": True,
             "equipment": {"head": "minecraft:leather_helmet"}},
        ],
        "max_mobs": 9,
        "drops": [{"item": "skyrpg_world:ectoplasm", "chance": 0.35, "count": [1, 2]}, DREAD],
        "particle": "minecraft:white_ash",
        "night_particle": "minecraft:soul_fire_flame",
    },
    "shattered_highlands": {
        "name": "The Shattered Highlands",
        "color": "#8CC4F0",
        "biomes": ["minecraft:stony_peaks", "minecraft:jagged_peaks", "minecraft:frozen_peaks", "minecraft:windswept_hills",
                   "minecraft:windswept_gravelly_hills", "minecraft:windswept_forest"],
        "weight": 4,
        "level_bonus": 20,
        "danger": 4,
        "description": "The mountains split and drifted apart. Rock hangs in the sky, the wind has teeth, and something huge circles the high ledges.",
        "palette": {
            "minecraft:grass_block": "minecraft:gravel",
            "minecraft:dirt": "minecraft:coarse_dirt",
            "minecraft:stone": "minecraft:tuff",
            "minecraft:snow_block": "minecraft:calcite",
            "minecraft:packed_ice": "minecraft:calcite",
            "minecraft:short_grass": "minecraft:air",
            "minecraft:fern": "minecraft:air",
        },
        "decorations": [
            # about one islet in five chunks, in three looks: grassy stone with Skyiron, mossy tuff, pale calcite
            {"type": "islet", "block": "minecraft:stone", "top": "minecraft:grass_block", "mix": "minecraft:andesite", "mix_chance": 0.3,
             "vein": "skyrpg_mining:skyiron_ore", "vein_chance": 0.03, "chance": 0.1, "count": 1, "radius": [3, 6], "height": [16, 36]},
            {"type": "islet", "block": "minecraft:tuff", "top": "minecraft:moss_block", "mix": "minecraft:cobblestone", "mix_chance": 0.25,
             "chance": 0.07, "count": 1, "radius": [2, 5], "height": [12, 30]},
            {"type": "islet", "block": "minecraft:calcite", "top": "minecraft:gravel", "mix": "minecraft:diorite", "mix_chance": 0.3,
             "chance": 0.06, "count": 1, "radius": [2, 4], "height": [20, 40]},
            {"type": "spike", "block": "minecraft:calcite", "tip": "minecraft:cobblestone", "chance": 0.3, "height": [4, 10]},
            {"type": "patch", "block": "minecraft:gravel", "chance": 0.35, "radius": [2, 4]},
            {"type": "patch", "block": "minecraft:tuff", "chance": 0.3, "radius": [2, 4]},
            {"type": "ore", "block": "skyrpg_mining:skyiron_ore", "chance": 0.75, "count": 3},
        ],
        "mobs": [
            {"type": "minecraft:breeze", "weight": 3, "count": [1, 2], "name": "Windborn"},
            {"type": "minecraft:spider", "weight": 4, "count": [2, 3], "name": "Cliff Stalker", "speed": 1.35},
            {"type": "minecraft:phantom", "weight": 3, "count": [1, 2], "name": "Gale Harpy", "fireproof": True},
        ],
        "max_mobs": 9,
        "drops": [{"item": "skyrpg_world:gale_essence", "chance": 0.35, "count": [1, 2]}, DREAD],
        "particle": "minecraft:cloud",
    },
}

# Everything the Wild adds to a region type: its heart boss and Heart Sigil, fog, and the shrine's blocks.
WILD = {
    "blightlands": {
        "boss": "skyrpg_world:blight_lord", "sigil": "skyrpg_world:blightlands_sigil", "fog": "#5A1418", "fog_density": 0.30,
        "shrine": {"floor": "minecraft:polished_blackstone_bricks", "accent": "minecraft:cracked_polished_blackstone_bricks",
                   "wall": "minecraft:blackstone", "pillar": "minecraft:polished_blackstone", "light": "minecraft:soul_lantern",
                   "heart": "skyrpg_world:blight_crystal"}},
    "ashen_wastes": {
        "boss": "skyrpg_world:ash_colossus", "sigil": "skyrpg_world:ashen_wastes_sigil", "fog": "#5E5855", "fog_density": 0.35,
        "shrine": {"floor": "minecraft:smooth_basalt", "accent": "minecraft:blackstone", "wall": "minecraft:basalt",
                   "pillar": "minecraft:polished_basalt", "light": "minecraft:lantern", "heart": "minecraft:magma_block"}},
    "frostgrave": {
        "boss": "skyrpg_world:frost_revenant_king", "sigil": "skyrpg_world:frostgrave_sigil", "fog": "#DCEEFF", "fog_density": 0.30,
        "shrine": {"floor": "minecraft:calcite", "accent": "minecraft:packed_ice", "wall": "minecraft:blue_ice",
                   "pillar": "minecraft:packed_ice", "light": "minecraft:sea_lantern", "heart": "minecraft:blue_ice"}},
    "mire": {
        "boss": "skyrpg_world:bog_mother", "sigil": "skyrpg_world:mire_sigil", "fog": "#4C6B2A", "fog_density": 0.40,
        "shrine": {"floor": "minecraft:mud_bricks", "accent": "minecraft:mossy_stone_bricks", "wall": "minecraft:mossy_cobblestone",
                   "pillar": "minecraft:mangrove_log", "light": "minecraft:lantern", "heart": "minecraft:verdant_froglight"}},
    "haunted_moors": {
        "boss": "skyrpg_world:lantern_wraith", "sigil": "skyrpg_world:haunted_moors_sigil", "fog": "#8E949B", "fog_density": 0.55,
        "shrine": {"floor": "minecraft:stone_bricks", "accent": "minecraft:mossy_stone_bricks", "wall": "minecraft:cobblestone",
                   "pillar": "minecraft:stone_bricks", "light": "minecraft:soul_lantern", "heart": "minecraft:crying_obsidian"}},
    "shattered_highlands": {
        "boss": "skyrpg_world:storm_griffin", "sigil": "skyrpg_world:shattered_highlands_sigil", "fog": "#B5D3F0", "fog_density": 0.25,
        "shrine": {"floor": "minecraft:tuff_bricks", "accent": "minecraft:cracked_stone_bricks", "wall": "minecraft:tuff",
                   "pillar": "minecraft:polished_tuff", "light": "minecraft:lantern", "heart": "minecraft:amethyst_block"}},
}
for _name, _extra in WILD.items():
    REGIONS[_name].update(_extra)

BLOCK_LOOT = {
    "blightgrass": "minecraft:coarse_dirt",
    "ashen_soil": "skyrpg_world:ashen_soil",
    "mire_mud": "skyrpg_world:mire_mud",
}


def write(path, data):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(data, indent=2) + "\n", encoding="utf-8")


def merge_tag(path, values):
    """Adds values to a tag file, keeping any already there."""
    existing = json.loads(path.read_text(encoding="utf-8")) if path.exists() else {}
    merged = list(existing.get("values", []))
    merged += [v for v in values if v not in merged]
    write(path, {**({"replace": existing["replace"]} if "replace" in existing else {}), "values": merged})


def main():
    for name, region in REGIONS.items():
        write(DATA / "skyrpg_world" / "skyrpg" / "danger_regions" / f"{name}.json", region)
    write(DATA / "skyrpg_world" / "worldgen" / "feature" / "danger_region.json", {"type": "skyrpg_world:danger_region"})
    write(DATA / "skyrpg_world" / "worldgen" / "placed_feature" / "danger_region.json", {"feature": "skyrpg_world:danger_region", "placement": []})
    for block, drop in BLOCK_LOOT.items():
        write(DATA / "skyrpg_world" / "loot_table" / "blocks" / f"{block}.json", {
            "type": "minecraft:block",
            "pools": [{"rolls": 1, "entries": [{"type": "minecraft:item", "name": drop}],
                       "condition": {"type": "minecraft:survives_explosion"}}],
            "random_sequence": f"skyrpg_world:blocks/{block}"})
    # Crystals break into essence (Fortune would be nice later); cursed ice drops nothing, like ice.
    write(DATA / "skyrpg_world" / "loot_table" / "blocks" / "blight_crystal.json", {
        "type": "minecraft:block",
        "pools": [{"rolls": 1, "entries": [{"type": "minecraft:item", "name": "skyrpg_world:blight_essence",
                                            "modifier": {"type": "minecraft:set_count", "count": {"type": "minecraft:uniform", "min": 1, "max": 2}}}],
                   "condition": {"type": "minecraft:survives_explosion"}}],
        "random_sequence": "skyrpg_world:blocks/blight_crystal"})
    tags = DATA / "minecraft" / "tags" / "block"
    # Merged, not overwritten: tools/builds/deep_layers.py adds its blocks to the same tags.
    merge_tag(tags / "mineable" / "shovel.json", ["skyrpg_world:blightgrass", "skyrpg_world:ashen_soil", "skyrpg_world:mire_mud"])
    merge_tag(tags / "mineable" / "pickaxe.json", ["skyrpg_world:blight_crystal", "skyrpg_world:cursed_ice"])
    # Plants (dry grass, dead bushes, saplings) can stand on the corrupted ground.
    merge_tag(tags / "dirt.json", ["skyrpg_world:blightgrass", "skyrpg_world:ashen_soil", "skyrpg_world:mire_mud"])
    print(f"wrote {len(REGIONS)} region types, the feature, loot tables and tags")


if __name__ == "__main__":
    main()
