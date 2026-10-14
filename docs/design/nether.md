# The Nether rework (Adventure Plan v2, phase 4)

The Nether becomes a mid-game goal: three new biomes with their own monsters and materials, a fortress dungeon with
a real boss, a new ore for fire-proof gear, and fishing in lava. Decided 2026-10-13 (design calls are delegated; see
`docs/design/progression.md` "Decisions (made)").

## Biomes

Added to the Nether's biome source with Fabric's `NetherBiomes.addNetherBiome` (no dimension override, so it works on
existing worlds; new biomes only appear in newly generated Nether chunks). Biomes are a synced registry: every
player needs the new jars before the server restarts with them.

| Biome | Look | Fog / ambience | Monsters | Materials |
| --- | --- | --- | --- | --- |
| **Ember Forest** | Emberwood trees (foraging's wood) on nylium-like ash ground, glowing ember leaves, falling embers | warm orange fog, ember particles | Ember Imps, magma cubes, blazes on the ground | Emberwood, Hellforged ore (common here) |
| **Bone Sea** | rolling soul sand and bone-block dunes, giant ribcages and skulls half-buried | pale blue-grey fog, soul particles | **Bone Giants** (new), skeletons, ghasts | bone, soul soil, Hellforged ore |
| **Obsidian Spires** | tall obsidian and crying-obsidian pillars, blackstone flats, glowing void cracks | deep purple fog, reverse-portal particles | endermen, wither skeletons, Void-touched monsters | crying obsidian, Hellforged ore (rich veins) |

## Hellforged ore

`skyrpg_mining:hellforged_ore` in Nether stone (netherrack, blackstone, basalt), pick tier V. It drops Hellforged
Scrap; smelting it in a blast furnace makes a Hellforged Ingot. The ingots make the **Hellforged** fire-proof armour set
(lava and fire immunity as a set bonus, plus Defense and Strength) and a Hellforged pickaxe tier.

## The Infernal Citadel

A fortress dungeon in the Nether (any of the new biomes and the vanilla wastes and basalt deltas), built like the v2
dungeons: two floors, mob packs and waves (blazes, wither skeletons, Ember Imps, piglin brutes), lava traps, per-player
chests, a Reward Chest and Delving XP.
- The champion is the **Infernal Knight**, a giant wither skeleton in blackened armour. It drops the Citadel Key.
- The boss is the **Magma Tyrant**: a huge molten-rock titan with a crown of fire.
  - Phases: magma slams and fireball volleys, then calling magma cubes and Ember Imps, then a molten floor and an enrage.
- Its trophy is the **Tyrant's Core**.
- Level about 48.

## Lava fishing

A Tier V rod, the **Magmaline Rod** (Hellforged ingots + Tyrant's Core), lets you fish in lava in the Nether. Lava
fishing has its own catch table (Magma Fish, Ember Scales, Hellforged Scrap, rare Molten Pearls) and lava sea creatures:
Magma Slugs, Flame Serpents and, rarely, the Cinder Leviathan.

## Later

Piglin war camps, ruined portals with loot, Nether-only slayer drops.

## Status (2026-10-13)

Built: the three biomes (data in `world/.../worldgen/biome`, Java features in `NetherRealm`), modded spawns by id (Ember Imps in the Ember Forest, Bone Giants in the Bone Sea), Hellforged ore (`skyrpg_mining:hellforged_ore` / `blackstone_hellforged_ore`; veins everywhere in the Nether, more in the biome tag `skyrpg_mining:hellforged_rich`, big veins in `hellforged_veins`), Scrap, blast-furnace Ingot, the Hellforged Pickaxe (Tier VI, Artisan Bench), `skyrpg_world:citadel_key` and `skyrpg_world:tyrants_core`.
