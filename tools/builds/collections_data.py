#!/usr/bin/env python3
"""Generates the collection definitions (progress/.../data/skyrpg_progress/skyrpg/collections/<id>.json) and gates
a few Artisan Bench recipes behind them (adds a "collection" requirement to the recipe JSON, idempotently).

Run from anywhere:  python tools/builds/collections_data.py

Thresholds are the base ladder (50 / 100 / 250 / 1k / 2.5k / 5k / 10k / 25k / 50k) times a per-collection scale (rarer
items have lower thresholds). Every tier gives coins and 4 SkyRPG XP (the default); tiers V and IX give a small permanent stat.
"""
import sys

# This file is named like the stdlib "collections" module, so keep its own folder off the import path.
sys.path.pop(0)

import glob
import json
import os

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))
OUT = os.path.join(ROOT, "progress", "src", "main", "resources", "data", "skyrpg_progress", "skyrpg", "collections")

BASE = [50, 100, 250, 1000, 2500, 5000, 10000, 25000, 50000]
COINS = [100, 250, 500, 1000, 2000, 4000, 8000, 15000, 30000]

# (id, item id, category, name, scale, stat id, coin multiplier)
C = [
    # mining
    ("stone", "minecraft:cobblestone", "mining", "Stone", 4, "mining_fortune", 0.5),
    ("coal", "minecraft:coal", "mining", "Coal", 2, "mining_fortune", 1),
    ("iron", "minecraft:raw_iron", "mining", "Iron", 1.5, "mining_fortune", 1),
    ("gold", "minecraft:raw_gold", "mining", "Gold", 1, "mining_fortune", 1.5),
    ("diamond", "minecraft:diamond", "mining", "Diamond", 0.5, "mining_fortune", 2),
    ("skyiron", "skyrpg_mining:raw_skyiron", "mining", "Skyiron", 1, "mining_fortune", 1.5),
    ("duskquartz", "skyrpg_mining:duskquartz_shard", "mining", "Duskquartz", 0.5, "mining_fortune", 2),
    ("emberite", "skyrpg_mining:emberite_chunk", "mining", "Emberite", 0.5, "mining_fortune", 2),
    ("lumenite", "skyrpg_mining:lumenite_crystal", "mining", "Lumenite", 0.3, "mining_fortune", 3),
    ("hellforged_scrap", "skyrpg_mining:hellforged_scrap", "mining", "Hellforged Scrap", 0.3, "mining_fortune", 3),
    ("glimmer", "skyrpg_mining:glimmer_shard", "mining", "Glimmer", 0.6, "mining_fortune", 2),
    # farming
    ("wheat", "minecraft:wheat", "farming", "Wheat", 2, "farming_fortune", 1),
    ("carrot", "minecraft:carrot", "farming", "Carrot", 2, "farming_fortune", 1),
    ("potato", "minecraft:potato", "farming", "Potato", 2, "farming_fortune", 1),
    ("beetroot", "minecraft:beetroot", "farming", "Beetroot", 1.5, "farming_fortune", 1),
    ("melon", "minecraft:melon_slice", "farming", "Melon", 3, "farming_fortune", 0.7),
    ("pumpkin", "minecraft:pumpkin", "farming", "Pumpkin", 1.5, "farming_fortune", 1),
    ("sugar_cane", "minecraft:sugar_cane", "farming", "Sugar Cane", 2, "farming_fortune", 1),
    ("cactus", "minecraft:cactus", "farming", "Cactus", 2, "farming_fortune", 1),
    ("nether_wart", "minecraft:nether_wart", "farming", "Nether Wart", 1.5, "farming_fortune", 1.5),
    ("cocoa_beans", "minecraft:cocoa_beans", "farming", "Cocoa Beans", 1.5, "farming_fortune", 1),
    ("sweet_berries", "minecraft:sweet_berries", "farming", "Sweet Berries", 1.5, "farming_fortune", 1),
    ("sunwheat", "skyrpg_farming:sunwheat_sheaf", "farming", "Sunwheat", 0.8, "farming_fortune", 1.5),
    ("frostberry", "skyrpg_farming:frostberries", "farming", "Frostberry", 0.8, "farming_fortune", 1.5),
    ("emberpepper", "skyrpg_farming:emberpepper", "farming", "Emberpepper", 0.8, "farming_fortune", 1.5),
    ("moonmelon", "skyrpg_farming:moonmelon_slice", "farming", "Moonmelon", 0.8, "farming_fortune", 1.5),
    ("starbloom", "skyrpg_farming:starbloom_petal", "farming", "Starbloom", 0.6, "farming_fortune", 2),
    # foraging
    ("oak", "minecraft:oak_log", "foraging", "Oak", 2, "foraging_fortune", 1),
    ("spruce", "minecraft:spruce_log", "foraging", "Spruce", 2, "foraging_fortune", 1),
    ("birch", "minecraft:birch_log", "foraging", "Birch", 2, "foraging_fortune", 1),
    ("jungle", "minecraft:jungle_log", "foraging", "Jungle", 1.5, "foraging_fortune", 1),
    ("acacia", "minecraft:acacia_log", "foraging", "Acacia", 1.5, "foraging_fortune", 1),
    ("dark_oak", "minecraft:dark_oak_log", "foraging", "Dark Oak", 1.5, "foraging_fortune", 1),
    ("cherry", "minecraft:cherry_log", "foraging", "Cherry", 1.5, "foraging_fortune", 1),
    ("mangrove", "minecraft:mangrove_log", "foraging", "Mangrove", 1.5, "foraging_fortune", 1),
    ("pale_oak", "minecraft:pale_oak_log", "foraging", "Pale Oak", 1.5, "foraging_fortune", 1),
    ("crimson", "minecraft:crimson_stem", "foraging", "Crimson Stem", 1.5, "foraging_fortune", 1),
    ("warped", "minecraft:warped_stem", "foraging", "Warped Stem", 1.5, "foraging_fortune", 1),
    ("ironbark", "skyrpg_foraging:ironbark_log", "foraging", "Ironbark", 1, "foraging_fortune", 1.5),
    ("mistpine", "skyrpg_foraging:mistpine_log", "foraging", "Mistpine", 0.8, "foraging_fortune", 1.5),
    ("duskwillow", "skyrpg_foraging:duskwillow_log", "foraging", "Duskwillow", 0.6, "foraging_fortune", 2),
    ("emberwood", "skyrpg_foraging:emberwood_log", "foraging", "Emberwood", 0.6, "foraging_fortune", 2),
    ("starwood", "skyrpg_foraging:starwood_log", "foraging", "Starwood", 0.4, "foraging_fortune", 3),
    ("heartwood_splinter", "skyrpg_foraging:heartwood_splinter", "foraging", "Heartwood Splinter", 0.3, "foraging_fortune", 3),
    # fishing
    ("cod", "minecraft:cod", "fishing", "Cod", 1.5, "fishing_fortune", 1),
    ("salmon", "minecraft:salmon", "fishing", "Salmon", 1.5, "fishing_fortune", 1),
    ("pufferfish", "minecraft:pufferfish", "fishing", "Pufferfish", 0.8, "fishing_fortune", 1.5),
    ("tropical_fish", "minecraft:tropical_fish", "fishing", "Tropical Fish", 0.8, "fishing_fortune", 1.5),
    ("tidescale", "skyrpg_fishing:tidescale", "fishing", "Tidescale", 1, "fishing_fortune", 1.5),
    ("magma_fish", "skyrpg_fishing:magma_fish", "fishing", "Magma Fish", 0.8, "fishing_fortune", 2),
    ("ember_scale", "skyrpg_fishing:ember_scale", "fishing", "Ember Scale", 0.4, "fishing_fortune", 2),
    ("abyssal_pearl", "skyrpg_fishing:abyssal_pearl", "fishing", "Abyssal Pearl", 0.1, "fishing_fortune", 3),
    ("leviathan_scale", "skyrpg_fishing:leviathan_scale", "fishing", "Leviathan Scale", 0.05, "fishing_fortune", 4),
    ("molten_pearl", "skyrpg_fishing:molten_pearl", "fishing", "Molten Pearl", 0.05, "fishing_fortune", 4),
    # combat
    ("rotten_flesh", "minecraft:rotten_flesh", "combat", "Rotten Flesh", 2, "health", 1),
    ("bone", "minecraft:bone", "combat", "Bone", 2, "defense", 1),
    ("string", "minecraft:string", "combat", "String", 1.5, "speed", 1),
    ("spider_eye", "minecraft:spider_eye", "combat", "Spider Eye", 1, "crit_damage", 1),
    ("ender_pearl", "minecraft:ender_pearl", "combat", "Ender Pearl", 0.6, "intelligence", 2),
    ("slime_ball", "minecraft:slime_ball", "combat", "Slime", 1, "health", 1),
    ("gunpowder", "minecraft:gunpowder", "combat", "Gunpowder", 1.5, "strength", 1),
    ("blaze_rod", "minecraft:blaze_rod", "combat", "Blaze Rod", 0.8, "strength", 1.5),
    ("valor_mark", "skyrpg_combat:valor_mark", "combat", "Valor Mark", 0.5, "magic_find", 2),
]

# Artisan Bench recipes locked behind a collection tier: (recipe file name, collection id, tier, display name).
GATES = [
    ("skyiron_pickaxe", "skyiron", 3, "Skyiron Pickaxe"),
    ("duskbore_pick", "duskquartz", 3, "Duskbore Pick"),
    ("emberheart_pickaxe", "emberite", 3, "Emberheart Pickaxe"),
    ("lumen_breaker", "lumenite", 3, "Lumen Breaker"),
    ("sunreaper_scythe", "sunwheat", 3, "Sunreaper Scythe"),
    ("frostbite_scythe", "frostberry", 3, "Frostbite Scythe"),
    ("ironbark_hatchet", "ironbark", 2, "Ironbark Hatchet"),
    ("mistpine_splitter", "mistpine", 3, "Mistpine Splitter"),
    ("abyssal_rod", "tidescale", 4, "Abyssal Rod"),
    ("magmaline_rod", "magma_fish", 3, "Magmaline Rod"),
]

STAT_AMOUNTS = {5: 1, 9: 2}
ROMAN = ["I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX"]


def nice(value):
    """Round to a tidy number: multiples of 5 below 100, 10 below 1k, 50 below 10k, else 100/500."""
    value = max(5, value)
    step = 5 if value < 100 else 10 if value < 1000 else 50 if value < 10000 else 500
    return int(round(value / step)) * step or step


def build(entry):
    cid, item, category, name, scale, stat, coin_mult = entry
    thresholds = []
    for base in BASE:
        t = nice(base * scale)
        if thresholds and t <= thresholds[-1]:
            t = thresholds[-1] + 5
        thresholds.append(t)
    recipes = {}
    for _, gid, tier, label in GATES:
        if gid == cid:
            recipes.setdefault(tier, []).append(label)
    tiers = []
    for i in range(9):
        tier = {"coins": int(COINS[i] * coin_mult), "xp": 4}
        if i + 1 in STAT_AMOUNTS:
            tier["stats"] = {stat: STAT_AMOUNTS[i + 1]}
        if i + 1 in recipes:
            tier["recipes"] = recipes[i + 1]
        tiers.append(tier)
    return {"category": category, "name": name, "icon": item, "items": [item], "thresholds": thresholds, "tiers": tiers}


def gate_recipes():
    for recipe, cid, tier, _ in GATES:
        matches = glob.glob(os.path.join(ROOT, "*", "src", "main", "resources", "data", "*", "skyrpg", "recipes", recipe + ".json"))
        if len(matches) != 1:
            print("!! recipe %s: %d files" % (recipe, len(matches)))
            continue
        path = matches[0]
        with open(path, encoding="utf-8") as f:
            data = json.load(f)
        data["collection"] = {"id": "skyrpg_progress:" + cid, "tier": tier}
        with open(path, "w", encoding="utf-8", newline="\n") as f:
            json.dump(data, f, indent=2)
            f.write("\n")
        print("gated %s -> %s %s" % (recipe, cid, ROMAN[tier - 1]))


def main():
    os.makedirs(OUT, exist_ok=True)
    ids = {entry[0] for entry in C}
    for _, gid, _, _ in GATES:
        assert gid in ids, gid
    for entry in C:
        with open(os.path.join(OUT, entry[0] + ".json"), "w", encoding="utf-8", newline="\n") as f:
            json.dump(build(entry), f, indent=2)
            f.write("\n")
    print("wrote %d collections to %s" % (len(C), OUT))
    gate_recipes()


if __name__ == "__main__":
    main()
