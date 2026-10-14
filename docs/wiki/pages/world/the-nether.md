---
title: The Nether
category: world
order: 7
icon: skyrpg_mining:hellforged_ingot
summary: The new Nether biomes, Hellforged ore and armour, the Infernal Citadel, lava fishing, and the Bone Giant and Magma Tyrant.
---
The Nether is a mid-game goal. Three new biomes bring their own monsters and materials, **Hellforged** ore makes fire-proof gear, a fortress dungeon holds a real boss, and with the right rod you can fish in lava. Monsters here start at **level 20**, well above the surface near spawn.

> [!NOTE]
> The new biomes only appear in **newly generated** Nether chunks, so areas you have already explored look the same as before. Every player needs the new mod files before the server restarts with them, because biomes are shared between server and client.

## The new biomes

About a tenth of the Nether each is one of the new biomes. Find them with `/locate biome skyrpg_world:ember_forest` (or `bone_sea`, `obsidian_spires`).

| Biome | Look | Fog | Monsters | Materials |
| --- | --- | --- | --- | --- |
| Ember Forest | Emberwood trees on smouldering ash, falling embers | Orange | Ember Imps (common), magma cubes, blazes, ghasts | Emberwood, Hellforged ore |
| Bone Sea | Soul sand and bone-block dunes, giant ribcages and skulls half-buried | Pale blue-grey | Skeletons, ghasts, **Bone Giants** (rare) | Bone, soul soil, Hellforged ore |
| Obsidian Spires | Tall obsidian and crying obsidian pillars, blackstone flats, glowing void cracks | Deep purple | Endermen, wither skeletons, ghasts, magma cubes | Crying obsidian, **rich** Hellforged veins |

Striders live in all three, as in the rest of the Nether.

### Bone Giants

A hunched giant skeleton of the Bone Sea. It is slow, tough (110 base health, heavy knockback resistance) and fire-immune, and it strides over soul sand without sinking in.

- **Club swing:** it raises its femur club, then smashes the ground in front of it. Step aside during the wind-up.
- **Bone quake:** about every 10 seconds, when you are within 5 blocks, it rears up for a second with a ring of soul fire on the ground, then stomps. Everyone within 4 blocks is damaged, thrown up and slowed.
- **Drops:** bones, sometimes bone blocks and soul soil, and when a player kills one there is a 12% chance of a Hellforged Scrap.

## Hellforged ore

{{item:skyrpg_mining:hellforged_ore}} generates in Nether stone (netherrack, blackstone, basalt) at y 10 to 117 and needs a **Tier 5 pickaxe**.

| Where | Veins |
| --- | --- |
| Anywhere in the Nether | Small veins, 2 per chunk |
| Ember Forest, Bone Sea, Obsidian Spires | Richer veins, 8 per chunk |
| Obsidian Spires only | Big veins of 10, 3 per chunk |

Mining it drops {{item:skyrpg_mining:hellforged_scrap}}. Smelt the scrap in a **blast furnace** (10 seconds) to make a {{item:skyrpg_mining:hellforged_ingot}}. Scrap sells for 28 coins and an ingot for 32.

### Hellforged gear

The {{item:skyrpg_mining:hellforged_pickaxe}} (Tier VI, Mining level 40) is upgraded from the Emberheart Pickaxe with 24 Hellforged Ingots and 8 Emberite Ingots. It has 260 Mining Speed and 100 Mining Fortune, smelts what you mine automatically, and its right-click **Hellfire Vein** makes every ore you mine also break a touching ore of the same kind for 15 seconds.

The **Hellforged armour set** is crafted in the dungeon category with **Delving level 30**:

| Piece | Ingots | Magma Cream | Stats |
| --- | --- | --- | --- |
| {{item:skyrpg_items:hellforged_helmet}} | 12 | 6 | 78 Health, 70 Defense, 22 Strength |
| {{item:skyrpg_items:hellforged_chestplate}} | 20 | 10 | 128 Health, 112 Defense, 34 Strength, plus a {{item:skyrpg_world:tyrants_core}} |
| {{item:skyrpg_items:hellforged_leggings}} | 16 | 8 | 106 Health, 94 Defense, 28 Strength |
| {{item:skyrpg_items:hellforged_boots}} | 10 | 6 | 70 Health, 60 Defense, 18 Strength |

The full set grants **Forged in Fire**: fire and lava cannot hurt you or set you alight, you see clearly through lava, and you gain +60 Defense and +40 Strength.

## The Infernal Citadel

A blackstone fortress on the Nether's lava sea, built over lava bridges and into cliffs. It is a two-floor dungeon at level 48 with blazes, wither skeletons, Ember Imps and piglin brutes, lava traps and per-player chests. See [Infernal Citadel](infernal-citadel) for the full walk-through.

- **Champion:** The Infernal Knight (level 50, 14,000 health), a giant wither skeleton in blackened plate. It drops the Citadel Key.
- **Boss:** The Magma Tyrant (level 52, 36,000 health).
- **Reset:** the dungeon resets three in-game days after it is used up, when nobody is nearby.

### The Magma Tyrant

A huge molten-rock titan with a crown of fire. Each extra player adds 60% to its health. The moves repeat on timers:

| Move | Every | Starts |
| --- | --- | --- |
| Magma slam (radius 8) | 10.5 s | Start |
| Fireball volley (5 fireballs) | 10 s | Start |
| Leap (up to 22 blocks) | 15.5 s | Start |
| Magma Cubes and Ember Imps summon (2 and 3, up to 6) | 23.5 s | Below 66% |
| Pull towards the heart (radius 16) | 19 s | Below 66% |
| Heal 3% of health | 27 s | Below 50% |
| Molten floor (14 spots, radius 5) | 12 s | Below 33% |
| Shockwave (radius 11) | 15 s | Below 33% |
| Bigger slam (radius 9) | 8.5 s | Below 33% |

Below 33% it also enrages: faster and harder-hitting.

**Rewards:** 36,000 to 48,000 coins each, and 70,000 coins, 20,000 Combat XP and 16,000 Delving XP on a first clear. Loot includes a {{item:skyrpg_world:tyrants_core}} (25% for a second), a Citadel Key, 8 to 14 Hellforged Scrap, 2 to 4 Hellforged Ingots, 2 to 4 Netherite Scrap and 5 to 9 Emberite Ingots, plus a Reward Chest. The Tyrant's Core sells for 6,200 coins.

## Lava fishing

The {{item:skyrpg_items:magmaline_rod}} lets you fish in **lava** in the Nether. Its bobber floats on lava, cannot burn, and bites like normal, and your catch goes straight to your inventory.

Crafting: a Leviathan Rod, 8 Hellforged Ingots and a Tyrant's Core, at Fishing level 40. It has 90 Fishing Speed, 85 Fishing Fortune and 7 Sea Creature Chance, with a 14% chance of a double catch.

| Lava catch | Chance |
| --- | --- |
| {{item:skyrpg_fishing:magma_fish}} | 52% |
| {{item:skyrpg_fishing:ember_scale}} (1 to 2) | 18% |
| Magma cream (1 to 2) | 12% |
| Blaze powder (1 to 3) | 12% |
| Nothing | 6% |

On top of that, about 1 in 250 lava catches is a {{item:skyrpg_fishing:molten_pearl}} (sells for 2,500).

**Lava sea creatures** can come up instead: Magma Slugs (level 40, common), Flame Serpents (level 42, rare) and very rarely the **Cinder Leviathan** (level 45, with a boss bar). See [Fishing](fishing).

> [!TIP]
> Stock up on Hellforged ingots before you start: the Hellforged set gives lava immunity, which is useful when you are standing on a lava lake with a fishing rod.

## See also

- [Infernal Citadel](infernal-citadel)
- [Fishing](fishing)
- [Mining](mining)
- [Armour Sets](armour-sets)
- [Delving](delving)
- [Boss List](boss-list)
