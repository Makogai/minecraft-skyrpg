---
title: Farming
category: skills
order: 2
icon: minecraft:golden_hoe
summary: Special crops and their growing conditions, planters, sprinklers, Dew, compost, meals, tools and sets.
---
Farming is the skill of harvesting fully grown crops. On top of vanilla crops, SkyRPG adds four **special crops** that only grow in the right conditions, **planters** that grow a crop on a timer, **sprinklers**, a currency called **Dew**, meals, and a ladder of scythes and armour. Level 50 is the cap (see [Skills overview](skills-overview)).

## Where Farming XP comes from

Crops only give XP when they are **fully grown**, and crops you plant yourself count. Melons, pumpkins, sugar cane, cactus and mushrooms count as soon as you break them, but ones you placed as blocks give nothing.

| Crop | Farming XP |
| --- | --- |
| Wheat, carrots, potatoes, beetroots, melon, sugar cane, cactus, cocoa, nether wart | 4 |
| Pumpkin | 4.5 |
| Red and brown mushrooms | 6 |
| Sunwheat | 8 |
| Frostberry | 12 |
| Emberpepper | 16 |
| Moonmelon | 22 |

Harvesting a planter gives XP too (from 1.5 to 14, see the planter table). The **Farming Insight** stat adds 1% more Farming XP per point, and each Farming level gives +4 Farming Fortune and +2 Health.

## Dew, Dewdrops and Dew Vials

Every fully grown crop you harvest has a 15% chance to drop a **{{item:skyrpg_farming:dewdrop}}** (40% from melons and pumpkins, 50% from planters). Farming Fortune multiplies the amount, and gear such as the Sunward set (+25% in daylight) and the Bumblebee pet (+15%) raise the chance. Nine Dewdrops make one **{{item:skyrpg_farming:dew_vial}}** at a crafting table, and back again.

Dew is what **Mabel Thornfield** (Thornfield Produce, the Farming Master) takes for seeds, planters, sprinklers, compost and Fieldhand gear as your Farming level unlocks them. Dewdrops sell for 3 coins and Dew Vials for 27, but they are not in "Sell all", so you cannot sell them by accident. Use `/sell` for them.

## Special crops

Each special crop grows from seeds on farmland, in four growth stages, but only grows while its condition holds. The seed tooltip says which. Vanilla bone meal does nothing to them.

| Crop | Grows | Seeds from | Harvest |
| --- | --- | --- | --- |
| Sunwheat | Under open sky, in daylight | 1 in 150 from fully grown wheat, or Mabel | {{item:skyrpg_farming:sunwheat_sheaf}} |
| Frostberry | In a cold biome, or within 2 blocks of snow or ice | 1 in 200 from fully grown Sunwheat, or Mabel | {{item:skyrpg_farming:frostberries}} |
| Emberpepper | Within 4 blocks of lava, or in the Nether | 1 in 50 from Emberite ore, or Mabel | {{item:skyrpg_farming:emberpepper}} |
| Moonmelon | Only at night | 1 in 300 from melons broken at night, or Mabel | {{item:skyrpg_farming:moonmelon_slice}} (2 per crop) |

Seeds: {{item:skyrpg_farming:sunwheat_seeds}}, {{item:skyrpg_farming:frostberry_seeds}}, {{item:skyrpg_farming:emberpepper_seeds}}, {{item:skyrpg_farming:moonmelon_seeds}}. Mabel sells four seeds at a time from Farming 8 (Sunwheat, 4 Dewdrops), 18 (Frostberry, 2 Vials), 26 (Emberpepper, 4 Vials) and 35 (Moonmelon, 8 Vials).

**{{item:skyrpg_farming:rich_compost}}** grows any crop one stage when you use it on it, special crops too, and ignores the condition. Mabel sells four for 6 Dewdrops from Farming 10, and the bench makes four from 4 Dewdrops, 2 bone meal and 1 Glimmer Shard.

**Starbloom** is different: it only grows in a Starbloom Planter. A {{item:skyrpg_farming:starbloom_seed}} (1 in 2,000 from fully grown Moonmelon) is needed to buy that planter.

> [!TIP]
> Crops in the wrong conditions just stop growing. A Frostberry field near a snow block, or an Emberpepper field next to a lava pool, works anywhere in the world.

## Planters

A planter is a box of soil that grows its crop on a timer, wherever you put it and whatever the light, biome or water. When it is ripe, right-click or hit it to harvest 1 to 2 of the crop (times your Farming Fortune), with Dew and rare-find rolls. It then starts growing again. Sneak and hit it with an empty hand to pick it up. Place planters anywhere outside the hub.

| Planter | Grows | Time | Farming XP | Mabel sells it at | Cost |
| --- | --- | --- | --- | --- | --- |
| {{item:skyrpg_farming:wheat_planter\|Wheat}} | Wheat | 45 s | 1.5 | Farming 3 | 12 Dewdrops |
| {{item:skyrpg_farming:carrot_planter\|Carrot}} | Carrots | 50 s | 1.5 | Farming 5 | 16 Dewdrops |
| {{item:skyrpg_farming:potato_planter\|Potato}} | Potatoes | 50 s | 1.5 | Farming 5 | 16 Dewdrops |
| {{item:skyrpg_farming:sunwheat_planter\|Sunwheat}} | Sunwheat Sheaves | 90 s | 3 | Farming 12 | 3 Dew Vials |
| {{item:skyrpg_farming:frostberry_planter\|Frostberry}} | Frostberries | 120 s | 4 | Farming 22 | 6 Dew Vials |
| {{item:skyrpg_farming:emberpepper_planter\|Emberpepper}} | Emberpepper | 180 s | 6 | Farming 30 | 10 Dew Vials |
| {{item:skyrpg_farming:moonmelon_planter\|Moonmelon}} | Moonmelon Slices | 240 s | 8 | Farming 38 | 16 Dew Vials |
| {{item:skyrpg_farming:starbloom_planter\|Starbloom}} | {{item:skyrpg_farming:starbloom_petal}} | 300 s | 14 | Farming 45 | 24 Dew Vials and a Starbloom Seed |

The Planter Tender talent shortens regrowth by 6% per rank (never by more than half).

## Sprinklers

A sprinkler gives every crop around it an extra growth tick every 5 seconds and keeps the farmland under them wet. Special crops still need their condition. Bigger sprinklers reach further, and each is made from the one before.

| Sprinkler | Reach | How to get it |
| --- | --- | --- |
| {{item:skyrpg_farming:copper_sprinkler}} | 2 blocks | Mabel, Farming 5: 8 Dewdrops and 4 copper ingots |
| {{item:skyrpg_farming:skyiron_sprinkler}} | 3 blocks | Bench, Farming 18: a Copper Sprinkler, 8 Raw Skyiron, 4 Dew Vials |
| {{item:skyrpg_farming:emberite_sprinkler}} | 4 blocks | Bench, Farming 30: a Skyiron Sprinkler, 8 Emberite Chunks, 8 Dew Vials |

## Rare finds

Every fully grown crop you harvest can drop a {{item:skyrpg_farming:lucky_clover}} (1 in 400) or a {{item:skyrpg_farming:verdant_heart}} (1 in 4,000). Planters are three times rarer. The best gear and recipes need them (Sunreaper and Moonharvest, for example). Talents, gear and enchants that say "rare finds 10% more likely" raise the odds, and +100% means twice as likely. Clovers sell for 800 coins and Hearts for 8,000.

## Meals

Meals are cooked at the Artisan Bench (the Cooking tab). Eating one gives a 10-minute boost, and a new meal replaces the last one.

| Meal | Needs | Ingredients | Boost |
| --- | --- | --- | --- |
| {{item:skyrpg_farming:sunbread}} (makes 2) | Farming 8 | 3 Sunwheat Sheaves, 1 wheat | +25 Farming Fortune |
| {{item:skyrpg_farming:frostberry_tart}} (makes 2) | Farming 18 | 4 Frostberries, 1 sugar, 1 egg | +10 Speed |
| {{item:skyrpg_farming:miners_chili}} (makes 2) | Farming 26 | 2 Emberpepper, 1 potato, 2 Glimmer Shards, 1 bowl | +40 Mining Speed |
| {{item:skyrpg_farming:moonmelon_juice}} | Farming 35 | 4 Moonmelon Slices, 1 glass bottle | +60 Intelligence |
| {{item:skyrpg_farming:starbloom_tea}} | Farming 45 | 1 Starbloom Petal, 2 Dewdrops, 1 glass bottle | +40 Health, +2 Farming Insight |

## Farming tools

Hoes and scythes are made from the one before, at the bench, and the new tool keeps the old one's name and enchantments. Scythes also need Collection III of their crop (Sunwheat for the Sunreaper, Frostberry for the Frostbite). See [Tools](tools) for the full stats.

| Tool | Needs | Ingredients besides the previous tool | What it does |
| --- | --- | --- | --- |
| {{item:skyrpg_items:harvester_hoe}} | Crafting table, or Mabel for 1,400 coins | 2 hay blocks, 2 sticks | +25 Farming Fortune |
| {{item:skyrpg_items:thornfield_sickle}} | Farming 8 | 4 Dew Vials, 8 iron ingots | Replants what you harvest |
| {{item:skyrpg_items:sunreaper_scythe}} | Farming 18 | 32 Sunwheat Sheaves, 2 Lucky Clovers, 6 Dew Vials | Replants, harvests a 3x3 |
| {{item:skyrpg_items:frostbite_scythe}} | Farming 26 | 32 Frostberries, 8 Raw Skyiron, 10 Dew Vials | Replants, 3x3, Bountiful Gust |
| {{item:skyrpg_items:cinder_scythe}} | Farming 35 | 32 Emberpepper, 16 Emberite Chunks, 1 Verdant Heart, 16 Dew Vials | Replants, 3x3, 10% double crops, Bountiful Gust |
| {{item:skyrpg_items:moonlit_reaper}} | Farming 45 | 48 Moonmelon Slices, 8 Starbloom Petals, 2 Verdant Hearts, 24 Dew Vials | Replants, 5x5, 15% double crops, Harvest Moon |

Right-click abilities: **Bountiful Gust** gives +100 Farming Fortune for 10 seconds (cooldown 60 s on the Frostbite, 45 s on the Cinder), and **Harvest Moon** makes every crop you harvest drop twice for 15 seconds (cooldown 120 s).

{{items:skyrpg_items:harvester_hoe, skyrpg_items:thornfield_sickle, skyrpg_items:sunreaper_scythe, skyrpg_items:frostbite_scythe, skyrpg_items:cinder_scythe, skyrpg_items:moonlit_reaper}}

## Farming armour

Fieldhand and the later sets are crafted from Dew Vials and your special crops. [Armour sets](armour-sets) covers every set.

| Set | Where from | Rarity | Four pieces together | Full-set bonus |
| --- | --- | --- | --- | --- |
| {{item:skyrpg_items:harvester_chestplate\|Harvester}} | Crafting table (hay blocks), or Mabel Thornfield for 3,150 coins | Uncommon | 70 Health, 50 Farming Fortune | **Sunlit Bounty**: The fields remember you: richer harvests and quicker lessons. Bonus stats: 25 Farming Fortune. |
| {{item:skyrpg_items:fieldhand_chestplate\|Fieldhand}} | Mabel Thornfield, Farming 8, 14 Dew Vials and 28 leather for the set; Artisan Bench, Farming 8: 24 Dew Vial, 24 Leather, 24 Hay Block | Uncommon | 70 Farming Fortune, 90 Health | **Steady Footing**: Your boots know the furrows: farmland never turns to dirt under you. Bonus stats: 35 Farming Fortune. |
| {{item:skyrpg_items:sunward_chestplate\|Sunward}} | Artisan Bench, Farming 18: 48 Sunwheat Sheaf, 24 Dew Vial, 4 Lucky Clover | Rare | 100 Farming Fortune, 120 Health | **Sunlit**: In daylight, crops drop 25% more Dewdrops. Bonus stats: 50 Farming Fortune. |
| {{item:skyrpg_items:frostgrove_chestplate\|Frostgrove}} | Artisan Bench, Farming 26: 72 Frostberries, 24 Dew Vial, 24 White Wool | Rare | 140 Farming Fortune, 150 Health | **Frost-Swift**: Speed while you're on farmland, and a 10% chance to harvest everything twice. Bonus stats: 70 Farming Fortune. |
| {{item:skyrpg_items:cinderfield_chestplate\|Cinderfield}} | Artisan Bench, Farming 35: 48 Emberpepper, 24 Emberite Chunk, 48 Dew Vial | Epic | 190 Farming Fortune, 190 Health | **Ashen Hands**: Everything you harvest replants itself, whatever you hold, and fire can't touch you. Bonus stats: 100 Farming Fortune. |
| {{item:skyrpg_items:moonharvest_chestplate\|Moonharvest}} | Artisan Bench, Farming 45: 48 Moonmelon Slice, 24 Starbloom Petal, 72 Dew Vial, 4 Verdant Heart | Legendary | 250 Farming Fortune, 230 Health | **Moon's Grace**: Crops within 8 blocks of you grow faster, and rare finds are twice as likely. Bonus stats: 140 Farming Fortune. |

{{items:skyrpg_items:harvester_chestplate, skyrpg_items:fieldhand_chestplate, skyrpg_items:sunward_chestplate, skyrpg_items:frostgrove_chestplate, skyrpg_items:cinderfield_chestplate, skyrpg_items:moonharvest_chestplate}}

## Talents

You get one talent point per Farming level (K, Talents). A reset is free once a day, then costs 2,000 coins.

| Talent | Ranks | Needs | Per rank |
| --- | --- | --- | --- |
| Dew Collector | 5 | Farming 0 | +8% Dewdrops |
| Green Fingers | 5 | Farming 0 | +5 Farming Fortune |
| Planter Tender | 5 | Farming 5 | Planters regrow 6% faster |
| Light Step | 1 | Farming 5 | Farmland never turns to dirt under you |
| Seed Saver | 3 | Farming 8 | Seeds of the next crop 25% more likely |
| Lucky Soil | 5 | Farming 10 | Rare finds 10% more likely |
| Sower | 1 | Farming 20 | Crops you harvest replant themselves, whatever you hold |
| Farmer's Almanac | 1 | Farming 20 | +5 Farming Insight |
| Bumper Crop | 1 | Farming 30 | 6% chance to harvest everything twice |

## Farming enchants

Hoe and boot enchants are bound at an enchanting table or Seraphine (see [Enchanting](enchanting)). The effects are at the highest level.

| Enchant | Rarity | Effect |
| --- | --- | --- |
| Green Thumb | Common | +50 Farming Fortune while held |
| Dewcatcher | Uncommon | +10% Dewdrop chance |
| Gentle Step (boots) | Uncommon | Farmland does not trample |
| Reseeding | Rare | Harvesting a grown crop replants it |
| Twin Harvest | Rare | 10% chance a harvested crop drops everything twice |
| Wide Swath | Epic | Also reaps the grown crops up to 2 blocks around |
| Bountiful | Ultimate | 0.7% chance per crop of +200 Farming Fortune for 15 seconds (tomes only) |

## Events and contracts

**Harvest Rush** is a five-minute event. Grown wheat, carrots, potatoes, beetroots, nether wart and cocoa score 1, melons and pumpkins 2, Sunwheat and Frostberry 3, Emberpepper and Moonmelon 5. Farming also has daily contracts (see [Contracts](contracts) and [Events](events)).

Mabel also buys crops, seeds and produce, and sells an egg for the {{item:skyrpg_pets:bee_pet|Bumblebee}} pet (6 Dew Vials, Farming 10, see [Pets](pets)).

## See also

- [Skills overview](skills-overview)
- [Crafting](crafting)
- [Tools](tools)
- [Armour sets](armour-sets)
- [Enchanting](enchanting)
- [Pets](pets)
- [Selling and shops](selling-and-shops)
