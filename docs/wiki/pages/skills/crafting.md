---
title: Crafting
category: skills
order: 7
icon: skyrpg_items:artisan_bench
summary: The Artisan Bench, how recipes unlock, the gear ladders, tool tiers and which blocks pay out.
---
Almost all SkyRPG gear is crafted at the **Artisan Bench**. Unlike the vanilla crafting grid, it asks for exact counts ("32 Skyiron Ingots"), locks recipes behind skill levels, and takes the old item as an ingredient so upgrades keep their enchantments. This page covers the bench, the gear ladders it builds, tool tiers and the placed-block rules.

## The Artisan Bench

{{item:skyrpg_items:artisan_bench}} is a block you can place anywhere. Get one by:

- Crafting it at a crafting table: a crafting table in the middle, 4 iron ingots on the edges and 4 Glimmer Crystals in the corners.
- Buying one from Dorrin Saltmarsh at Saltmarsh Provisions for 1,500 coins.

{{recipe:skyrpg_items:artisan_bench}}

Right-click it to open the crafting screen. Admins can open it anywhere with `/bench`, and NPCs can open it too.

Before you have your own, use the public bench in Skyhaven: it stands in the Emberforge yard, next to Brannoc Ashforge (by the smithing table). It's the only bench you can use in the hub.

### Using the screen

The screen has three columns:

- **Left: categories.** One for each kind of recipe (backpacks, combat, cooking, dungeon, farming, fishing, foraging, mining, region and slayer), with **★ Pinned**, **✔ Ready to craft** (everything you can make right now) and **All** on top. While you track a recipe there is also a **◆ Tracked** tab, and the bench opens on it with that recipe selected. Each shows how many recipes it has, in green when one of them is ready. On a small window only the icons show; hover one for its name.
- **Middle: the recipes**, grouped under Weapons, Tools, Armour, Accessories, Backpacks and Materials, with the ones you can make first. The chip on the right of each row says where you stand: **✔ 3** (you can craft it 3 times), **1/2** (you have enough of 1 of its 2 ingredients) or **✖ Mining 18** (still locked). A ★ marks a pinned recipe and a ◆ the tracked one.
- **Right: the selected recipe.** The result, big (hover it for its full tooltip), what it needs, and every ingredient with how many you have of how many you need and a bar. Under it are four buttons: **Craft**, **Craft ×N** (as many as you can afford, up to 64), **★ Pin** and **◆ Track**.

Controls:

- **Click** a recipe to select it, **double-click** to craft one, **Shift+double-click** to craft as many as you can.
- **↑ / ↓** move the selection, **Enter** crafts one and **Shift+Enter** as many as you can.
- **P** pins or unpins the selected recipe. Pinned recipes are kept on your computer and the bench opens on the Pinned tab when you have any.
- The **search box** at the top right finds recipes in every category by the name of the result or of any ingredient. Click it (or press **Ctrl+F**) and type; **Enter** or **Esc** leaves it.
- **Right-click** a recipe (or press **T**) to **track** it. A box on the right of your screen then lists its ingredients with how many you have of how many you need (backpacks count), updated every second, and says when it's ready to craft (or what it still needs to unlock). Do it again to stop. The box can be moved or hidden in the HUD editor ("Tracked Recipe").
- With **JEI** (part of the pack), every bench recipe also shows in JEI's "Artisan Bench" tab: press **R** on an item for its recipes or **U** for what it's used in.
- Ingredients come from your inventory and from inside your carried backpacks.
- Recipes unlock as your skill levels rise, and the level-up message lists the new ones ("NEW RECIPES at the Artisan Bench").

### Upgrades keep the item

Most gear recipes take the previous piece as their first ingredient. The result keeps the old item's **custom name, enchantments, lore and contents**, so a backpack keeps what is inside it and a pickaxe keeps its Prosperity (SkyRPG enchants and all). The new tool comes fully repaired, so upgrading also mends a worn one. Only SkyRPG tools, armour, accessories and backpacks count as the item being upgraded.

### Collection requirements

A handful of recipes also need a collection tier (see [Collections](collections)). The recipe shows "Requires Skyiron Collection III" while it is locked.

| Recipe | Collection needed |
| --- | --- |
| Skyiron Pickaxe | Skyiron III |
| Duskbore Pick | Duskquartz III |
| Emberheart Pickaxe | Emberite III |
| Lumen Breaker | Lumenite III |
| Ironbark Hatchet | Ironbark II |
| Mistpine Splitter | Mistpine III |
| Sunreaper Scythe | Sunwheat III |
| Frostbite Scythe | Frostberry III |
| Abyssal Rod | Tidescale IV |
| Magmaline Rod | Magma Fish III |

## The vanilla crafting table

Some things stay at the normal crafting table and show in the recipe book (each item's wiki page draws its 3x3 grid):

- The Artisan Bench, the {{item:skyrpg_items:satchel}} (string, leather and an iron ingot) and the starter gear: the Prospector set and pickaxe (stone, iron), the Harvester set and hoe (hay blocks), the Woodsman set and axe (logs) and the Vanguard set (iron and redstone blocks). [Armour sets](armour-sets) has the details.
- Compacting: 9 Glimmer Shards make a Glimmer Crystal, 9 Dewdrops a Dew Vial, 9 Tidescales a Tidescale Plate, 9 Heartwood Splinters a Heartwood Bundle, 9 Valor Marks a Valor Seal, and each reverses. Skyiron Ingots, Emberite Ingots and Duskquartz Shards pack into blocks.
- Smelting: Raw Skyiron in a furnace or blast furnace, Emberite Chunks and Hellforged Scrap in a blast furnace only.
- Planks of the five new woods, which work in every vanilla wood recipe.

A few of them:

{{recipe:skyrpg_items:satchel}}

{{recipe:skyrpg_items:prospector_pickaxe}}

{{recipe:skyrpg_items:woodsman_chestplate}}

{{recipe:skyrpg_mining:glimmer_crystal}}

{{recipe:skyrpg_mining:skyiron_ingot}}

## The gear ladders

Each skill has a ladder of gear. You make each step from the one before it, and every step is unlocked by a skill level and the material of that tier. The ladders follow a rough pattern (the exact levels are in the next table):

| Step | Typical level | Rarity | Made from |
| --- | --- | --- | --- |
| 1 | 0 | Uncommon | Vanilla materials, at a crafting table |
| 2 | 8 | Uncommon | The skill's currency and vanilla materials |
| 3 | 18 | Rare | The first custom material and a little rare find |
| 4 | 26 | Epic | The second custom material |
| 5 | 35 | Epic | The third custom material and rare finds |
| 6 | 45 | Legendary | The top material and several rare finds |

| Skill | Tool or weapon ladder | Armour ladder |
| --- | --- | --- |
| Mining | Prospector, Delver's (8), Skyiron (18), Duskbore (26), Emberheart (35), Hellforged (40), Lumen Breaker (45) | Prospector, Delver (8), Skyforged (18), Deepwarden (26), Emberheart (35), Lumenward (45) |
| Farming | Harvester Hoe, Thornfield Sickle (8), Sunreaper (18), Frostbite (26), Cinder (35), Moonlit Reaper (45) | Harvester, Fieldhand (8), Sunward (18), Frostgrove (26), Cinderfield (35), Moonharvest (45) |
| Foraging | Woodsman Axe, Ironbark Hatchet (8), Mistpine Splitter (18), Willowcleaver (26), Emberbrand (35), Starwood Greataxe (45) | Woodsman, Barkguard (8), Mistwarden (18), Willowshade (26), Emberbark (35), Starwood (45) |
| Fishing | Driftwood Rod, Reef (8), Stormcaller (18), Abyssal (26), Leviathan (35), Magmaline (40) | Angler, Reefwalker (10), Abyssal (26), Leviathan (40) |
| Combat | Vanguard Blade, Fangsteel Sabre (10), Stormedge (18), Riftblade (26), Cinderfang (35), Starfall Blade (45) | Vanguard, Fangguard (10), Stormplate (18), Riftweave (26), Cinderguard (35), Starforged (45) |
| Backpacks | Satchel, Trail Pack, Ranger's Pack, Voyager's Pack, Colossal Pack | |

Details for each skill's recipes are on its page: [Mining](mining), [Farming](farming), [Foraging](foraging) and [Fishing](fishing). Combat gear is on [Weapons](weapons) and [Armour sets](armour-sets), and backpacks are on [Backpacks](backpacks).

### Combat gear

Combat recipes are paid in **Valor Marks** and **Valor Seals** (nine Marks make a Seal), with Dread Fangs and Nightmare Cores from monsters as the rare ingredient. The Fangguard set and Fangsteel Sabre need Combat 10, Stormedge and Stormplate 18, Riftblade and Riftweave 26, Cinderfang and Cinderguard 35, and Starfall Blade and Starforged 45.

### Dungeon, slayer and region recipes

Three more groups of recipes use their own unlocks:

- **Dungeon** recipes need Delving levels and boss tokens (Warden Sigils, Arachne Eyes, Tide Pearls, Dwarven Cogs and so on). See [Delving](delving).
- **Slayer** recipes need a slayer level for that boss and its tokens. Each piece of a set unlocks at a different slayer level (boots at 2, helmet at 4, leggings at 5, chestplate at 7, and the weapon at 6). See [Slayers](slayers).
- **Region** recipes need Combat levels and the essences from a danger region's monsters. See [Danger regions](danger-regions).

## Tool tiers

Pickaxes and axes have a **tier** from I to VII, and SkyRPG's tougher ores and woods need a minimum tier. A block that needs a higher tier does not crack at all, and the action bar and look-at box say what you need.

| Tier | Vanilla pickaxe or axe | SkyRPG tools |
| --- | --- | --- |
| I | Wooden, golden | None |
| II | Stone, copper | Quarry Pick, Woodsman Axe |
| III | Iron | Prospector and Delver's Pickaxes |
| IV | Diamond | Skyiron Pickaxe, Ironbark Hatchet |
| V | Netherite | Duskbore Pick, Mistpine Splitter |
| VI | None | Emberheart and Hellforged Pickaxes, Willowcleaver |
| VII | None | Lumen Breaker, Emberbrand Axe, Starwood Greataxe |

Mining gates: Skyiron needs II, Duskquartz IV, Emberite and Hellforged V, Lumenite VI. Foraging gates: Ironbark III, Mistpine IV, Duskwillow V, Emberwood VI, Starwood VII. See [Tools](tools) for every tool.

## Placed blocks pay nothing

You cannot farm XP by placing blocks and breaking them again. The game remembers every block a player places, and a placed block gives **no skill XP, no Glimmer Shards, no rare finds, no event points and no contract progress** when broken.

Some things pay out even when you place them, because placing them is how you use them: crops and saplings, sugar cane, cactus, cocoa, nether wart, bamboo, sweet berry bushes, melon and pumpkin stems, kelp, mushrooms, mining nodes, foraging stumps and planters. (A melon or pumpkin block you placed still gives nothing.) Treefeller also never fells logs you placed.

> [!TIP]
> Nodes, stumps and planters are the safe way to get a steady income of Glimmer, Heartwood and Dew while you are away from the big world. They are cheaper XP than ores, so mix them with real mining, chopping and farming.

## See also

- [Skills overview](skills-overview)
- [Mining](mining), [Farming](farming), [Foraging](foraging), [Fishing](fishing)
- [Tools](tools)
- [Armour sets](armour-sets)
- [Backpacks](backpacks)
- [Collections](collections)
