---
title: The Deep
category: world
order: 6
icon: skyrpg_world:glimmer_cluster
summary: The four underground layers of the Overworld, with their blocks, ores, monsters, levels and drops, plus Abandoned Mine Camps and Runeforged armour.
---
The deeper you dig, the stranger the caves get. Underground, the Overworld is split into four **layers**, each with its own look, monsters, level bonus and drops. Going down is how you find the best ores, and the layers are where the Mining skill, Delving and Combat meet.

## The four layers

A layer applies only in **Overworld caves**, within its height band, and out of sight of the sky. Heights run from the lowest block of the band, so Glimmer Caverns covers y 0 to 39.

| Layer | Height | Danger | Monster levels | Look | Monsters | Drop |
| --- | --- | --- | --- | --- | --- | --- |
| Glimmer Caverns | y 0 to 40 | 1 skull | +4 | Glimmerstone, calcite, Glimmer Clusters, glow lichen | Glimmer Crawlers, Lost Miners, cave spiders | {{item:skyrpg_world:glimmer_geode}} |
| Fungal Depths | y -20 to 0 | 2 skulls | +7 | Mycelium and moss, glowcaps, giant mushrooms, Spore Puffballs | Sporelings, Spore Shamblers, cave spiders | {{item:skyrpg_world:glowcap_spores}} |
| Dwarven Halls | y -40 to -20 | 3 skulls | +10 | Deepslate brick and tile, broken pillars, chains and lanterns, runestones | Dwarven Constructs, Lost Miners, skeletons | {{item:skyrpg_world:dwarven_cog}} |
| Magma Rifts | y -64 to -40 | 4 skulls | +14 | Basalt, blackstone, magma, lava pools | Fire Beetles, magma cubes, blazes | {{item:skyrpg_world:rift_ember}} |

The **Dwarven Halls layer** is the cave style. The **Dwarven Halls dungeon** is a separate ruined city found under mountains, hills and forests. See [Dwarven Halls](dwarven-halls).

### How layers work

- **The land.** Cave surfaces (stone, deepslate, tuff, granite, diorite, andesite, dirt and gravel next to cave air) are restyled as chunks generate, about 60 to 75% of them depending on the layer. Edges between layers blur by about 3 blocks, so layers bleed together. Nothing within 3 blocks of the surface changes, water-filled caves stay as they were, and vanilla lush caves, dripstone caves and the deep dark are left alone. Chunks made before the update keep their old look, but the monsters, levels and drops still apply.
- **Levels.** A layer's bonus stacks with the usual underground levels (+5 below y 0, +10 below y -32). In the Magma Rifts that is +10 and +14, so **+24 on top of the surface level for that distance**.
- **Spawns.** Every 3 seconds, near each survival player in a layer, a group spawns 10 to 24 blocks away on solid ground in the dark (block light of at most 7 to 9, depending on the layer), up to 6 to 8 layer monsters within 40 blocks. They can despawn normally.
- **Titles.** Entering a layer shows its name, skulls and level bonus. You are only counted as in a new layer once you are 2 blocks past the edge, and the same layer's title is not repeated for a minute.
- **Drops.** Monsters killed by a player inside roll the layer's drop: Glimmer Geode 3%, Glowcap Spores 30% (1 to 2), Dwarven Cog 25%, Rift Ember 25% (1 to 2).
- `/layer` tells anyone which layer they are in.

## Blocks and materials

| Item | What it is |
| --- | --- |
| {{item:skyrpg_world:glimmerstone}} | Stone with glowing flecks. Drops itself. |
| {{item:skyrpg_world:glimmer_cluster}} | A glowing crystal cluster on floors, ceilings and walls. Mined, it breaks into 2 to 4 Glimmer Shards. Silk Touch keeps it. |
| {{item:skyrpg_world:glowcap}} | A small glowing mushroom that grows on solid floors. |
| {{item:skyrpg_world:glowcap_block}} | The cap of giant mushrooms. Drops 0 to 2 glowcaps. |
| {{item:skyrpg_world:spore_puffball}} | Bursts into a poison cloud (Poison II for 4 seconds) when you come within about 2 blocks. It regrows after 30 seconds. |
| {{item:skyrpg_world:dwarven_runestone}} | Deepslate bricks with a glowing rune. Drops itself. |
| {{item:skyrpg_world:glimmer_geode}} | Right-click to crack it open: 3 to 8 Glimmer Shards and a 10% chance of a Prism Fragment. |

{{items:skyrpg_world:glowcap_spores, skyrpg_world:dwarven_cog, skyrpg_world:rift_ember}}

The Mining Master buys the Geode, Spores, Cogs and Embers (150, 45, 70 and 90 coins).

> [!WARNING]
> Spore Puffballs in the Fungal Depths look harmless until you step next to one. Walk around them, or break them from a distance.

## Ores

Ores are not tied to the layers, but they sit at similar depths:

| Ore | Heights | Needs |
| --- | --- | --- |
| {{item:skyrpg_mining:skyiron_ore}} | y -24 to 64, most at the middle | Tier 2 pickaxe |
| {{item:skyrpg_mining:duskquartz_ore}} | y -48 to -8 | Tier 4 pickaxe |
| {{item:skyrpg_mining:emberite_ore}} | y -64 to -8 | Tier 5 pickaxe |
| {{item:skyrpg_mining:lumenite_ore}} | y -64 to -40 (very rare) | Tier 6 pickaxe |

Dwarven Halls walls also hold extra Duskquartz, and Magma Rifts walls extra Emberite. See [Mining](mining).

## Abandoned Mine Camps

A common cave structure in the Glimmer Caverns band: a small camp with a tent, rails, a minecart, lanterns and a loot chest, guarded by Lost Miners. Camps spawn at y 4 to 30, in any Overworld biome, roughly one per 12-chunk square.

- **Loot chest:** 4 to 6 stacks from Skyiron ingots, Glimmer Shards, Glimmer Crystals, torches, food, coal, iron, rails, Runic Dust and XP bottles, plus 1 to 3 Duskquartz Shards.
- **Cart and crate** have smaller loot.
- Sometimes the chest holds a **treasure map to the Dwarven Halls** dungeon.

## Monsters

| Monster | Layer | Notes |
| --- | --- | --- |
| Glimmer Crawler | Glimmer Caverns | A quick spider with glowing crystals. |
| Lost Miner | Glimmer Caverns, Dwarven Halls | A zombie with a pickaxe and helmet. |
| Sporeling | Fungal Depths | A knee-high mushroom creature. Bursts into a poison cloud when it dies. |
| Spore Shambler | Fungal Depths | A zombie with a glowing cap. |
| Dwarven Construct | Dwarven Halls | Stone-and-brass automaton. Slow and armoured, hits hard. |
| Fire Beetle | Magma Rifts | Fire-immune. Charges and sets you alight. |

## Runeforged armour

The **Runeforged** set is made at the Artisan Bench in the **dungeon** category with **Delving level 20**. Each piece needs a {{item:skyrpg_world:thanes_rune}} (the Iron Thane's trophy), Dwarven Cogs and Duskquartz Shards:

| Piece | Cogs | Duskquartz Shards |
| --- | --- | --- |
| {{item:skyrpg_items:runeforged_helmet}} | 12 | 10 |
| {{item:skyrpg_items:runeforged_chestplate}} | 20 | 16 |
| {{item:skyrpg_items:runeforged_leggings}} | 16 | 14 |
| {{item:skyrpg_items:runeforged_boots}} | 10 | 8 |

The full set gives **Rune-Lit Halls**: Night Vision below y 40, +40 Defense, +60 Mining Speed and +20 Mining Fortune on top of the pieces' own stats.

The Thane's Rune also makes **Thane's Hammer** (with Rift Embers).

> [!TIP]
> Bring a few stacks of torches and a way home. Cave layers are dark, the monster spawns need darkness, and your Waypoints let you mark the way back. See [Waystones and Travel](waystones-and-travel).

## See also

- [Mining](mining)
- [Delving](delving)
- [Dwarven Halls](dwarven-halls)
- [Catacombs](catacombs)
- [Armour Sets](armour-sets)
- [Mob List](mob-list)
