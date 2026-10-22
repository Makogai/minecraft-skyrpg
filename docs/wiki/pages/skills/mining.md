---
title: Mining
category: skills
order: 3
icon: minecraft:iron_pickaxe
summary: Ores, nodes, Glimmer, rare finds, pickaxe tiers and armour for the Mining skill.
---
Mining is the skill of breaking stone and ore. It pays Mining XP, drops **Glimmer Shards** (the Mining Master's currency) on top of the normal drops, and gates the best pickaxes and armour in the game. Level 50 is the cap and the curve is steep (see [Skills overview](skills-overview)).

## Where Mining XP comes from

Every block in this table gives Mining XP when you break it. Blocks you placed yourself give nothing (the game remembers player-placed stone and ore per chunk), except nodes, which are placed on purpose. Vanilla ores of the same kind give the same XP.

| Block | Mining XP |
| --- | --- |
| Netherrack | 0.5 |
| Stone, cobblestone, deepslate, cobbled deepslate | 1 |
| End stone | 3 |
| Coal, copper, iron ore, nether quartz ore | 5 |
| Gold ore | 6 |
| Lapis, redstone ore | 7 |
| Skyiron ore | 8 |
| Emerald ore | 9 |
| Diamond ore | 10 |
| Duskquartz ore | 12 |
| Emberite ore | 16 |
| Obsidian | 20 |
| Hellforged ore (Nether) | 20 |
| Lumenite ore | 25 |
| Ancient debris | 30 |

Nodes give far less, from 1 XP for Coal and Iron up to 3.5 for Lumenite. They are a steady trickle, not a way to level. The **Mining Insight** stat adds 1% more Mining XP per point.

## The ores

SkyRPG adds four ores to the overworld and one to the Nether. Each overworld ore comes in a stone and a deepslate version, and only chunks generated after the ore was added contain it.

| Ore | Where | Pickaxe tier | Drops | Refining |
| --- | --- | --- | --- | --- |
| {{item:skyrpg_mining:skyiron_ore\|Skyiron Ore}} | y -24 to 64, common | II | {{item:skyrpg_mining:raw_skyiron}} | Furnace or blast furnace makes {{item:skyrpg_mining:skyiron_ingot}} |
| {{item:skyrpg_mining:duskquartz_ore\|Duskquartz Ore}} | y -48 to -8, in clusters | IV | {{item:skyrpg_mining:duskquartz_shard}} | None |
| {{item:skyrpg_mining:emberite_ore\|Emberite Ore}} | y -64 to -8 | V | {{item:skyrpg_mining:emberite_chunk}} | Blast furnace only, makes {{item:skyrpg_mining:emberite_ingot}} |
| {{item:skyrpg_mining:lumenite_ore\|Lumenite Ore}} | y -64 to -40, rare | VI | {{item:skyrpg_mining:lumenite_crystal}} | At the bench (Mining 40): 1 Lumenite Crystal and 4 Glimmer Crystals make {{item:skyrpg_mining:attuned_lumenite}} |
| {{item:skyrpg_mining:hellforged_ore\|Hellforged Ore}} | The Nether, y 10 to 117 (richer in some biomes) | V | {{item:skyrpg_mining:hellforged_scrap}} | Blast furnace makes {{item:skyrpg_mining:hellforged_ingot}} |

Ingots and Duskquartz Shards pack nine to a block and unpack again at a crafting table ({{item:skyrpg_mining:skyiron_block}}, {{item:skyrpg_mining:emberite_block}}, {{item:skyrpg_mining:duskquartz_block}}). At the Artisan Bench (Mining 18), four {{item:skyrpg_mining:prism_fragment|Prism Fragments}} make a {{item:skyrpg_mining:prism}}.

For the cave layers below the overworld (Glimmer Caverns, the Dwarven Halls, the Magma Rifts) see [The Deep](the-deep), and for the Nether see [The Nether](the-nether).

### Pickaxe tiers

Every pickaxe has a tier, shown in its tooltip. A block that needs a higher tier than your pickaxe does not crack at all, and the action bar and the look-at box tell you which tier you need.

| Tier | Pickaxes |
| --- | --- |
| I | Wooden, golden |
| II | Stone, copper, {{item:skyrpg_items:quarry_pick}} |
| III | Iron, {{item:skyrpg_items:prospector_pickaxe}}, {{item:skyrpg_items:delver_pickaxe}} |
| IV | Diamond, {{item:skyrpg_items:skyiron_pickaxe}} |
| V | Netherite, {{item:skyrpg_items:duskbore_pick}} |
| VI | {{item:skyrpg_items:emberheart_pickaxe}}, {{item:skyrpg_mining:hellforged_pickaxe}} |
| VII | {{item:skyrpg_items:lumen_breaker}} |

The gates are block tags, so a data pack can move an ore to another tier. [Tools](tools) lists every pickaxe with its stats.

## Glimmer

**{{item:skyrpg_mining:glimmer_shard|Glimmer Shards}}** drop from almost everything you mine:

- 2 to 4 from every ore, vanilla ores included.
- 1 from a node, half the time.
- 1 from stone-like blocks (stone, deepslate, tuff, andesite, netherrack, basalt and similar), 10% of the time.

Mining Fortune multiplies shards: every 100 Fortune doubles them. Nine shards pack into a {{item:skyrpg_mining:glimmer_crystal}} and back at a crafting table. Shards sell for 2 coins and crystals for 18, but the better use is Tamsin's shop and the bench.

## Mining Fortune and Mining Speed

- **Mining Fortune** drops the block's normal drops again: one extra copy for every full 100 Fortune, plus a chance of one more from the remainder (250 Fortune is two extra copies and a 50% chance of a third). It works on ores, nodes and the Nether ores, but not with a Silk Touch tool.
- **Mining Speed** makes you break blocks faster. 100 Mining Speed is twice as fast.

Both come from gear, talents, set bonuses, enchants and your Mining level (+4 Mining Fortune and +1 Defense per level).

## Nodes

Nodes are placeable ore blocks you buy from the Mining Master. Break one for its drop, Glimmer and a little XP. It then turns into a dark husk that cannot be mined until it regrows. Sneak and hit it with an empty hand to pick it up again.

| Node | Needs Mining | Cost at Tamsin | Regrows in |
| --- | --- | --- | --- |
| {{item:skyrpg_mining:coal_node\|Coal}} | 3 | 12 Glimmer Crystals | 20 s |
| {{item:skyrpg_mining:iron_node\|Iron}} | 6 | 20 Glimmer Crystals | 30 s |
| {{item:skyrpg_mining:gold_node\|Gold}} | 10 | 35 Glimmer Crystals | 45 s |
| {{item:skyrpg_mining:redstone_node\|Redstone}} | 14 | 45 Glimmer Crystals | 40 s |
| {{item:skyrpg_mining:skyiron_node\|Skyiron}} | 18 | 70 Glimmer Crystals, 16 Raw Skyiron | 60 s |
| {{item:skyrpg_mining:diamond_node\|Diamond}} | 22 | 120 Glimmer Crystals, 4 diamonds | 120 s |
| {{item:skyrpg_mining:duskquartz_node\|Duskquartz}} | 26 | 100 Glimmer Crystals, 16 Duskquartz Shards | 100 s |
| {{item:skyrpg_mining:emberite_node\|Emberite}} | 30 | 200 Glimmer Crystals, 24 Emberite Chunks | 150 s |
| {{item:skyrpg_mining:lumenite_node\|Lumenite}} | 40 | 400 Glimmer Crystals, 12 Lumenite Crystals | 300 s |

Skyiron, Duskquartz, Emberite and Lumenite nodes need the same pickaxe tier as the ores. The Node Keeper talent and the Mole pet make nodes regrow faster (never by more than half).

## Rare finds

Every ore and node you break rolls for two rare finds, announced with "RARE FIND!":

- A {{item:skyrpg_mining:prism_fragment}} with a 1 in 300 chance.
- A {{item:skyrpg_mining:heartstone}} with a 1 in 3,000 chance.

Nodes are three times rarer for both, so real progress means going out to mine. Gear, talents and enchants that say "rare finds 10% more likely" raise the odds (+100% means twice as likely). A fragment sells for 1,500 coins and a Heartstone for 15,000, but the best gear needs them.

## Auto-pickup

Everything you mine goes straight into your inventory, including Fortune's extra drops and Glimmer, and so do blocks that fall apart because of your break (the sugar cane or cactus above, a torch on the side). If your inventory is full the rest drops at your feet and the action bar tells you.

## The Mining Master

**Tamsin Deepholt** runs Deepholt Delving Co. in the hub. Her shop is paid in Glimmer, and each offer shows "Needs Mining N" while locked.

- {{item:skyrpg_items:quarry_pick}}: 32 Glimmer Shards, no level needed.
- {{item:skyrpg_items:delver_pickaxe}}: 15 Glimmer Crystals, Mining 8.
- The Delver armour set: 8, 14, 12 and 7 crystals for helmet, chestplate, leggings and boots, Mining 10.
- Every node in the table above.
- A {{item:skyrpg_pets:mole_egg}}: 48 Glimmer Crystals, Mining 10 (see [Pets](pets)).
- She buys every mining material, raw or refined: Raw Skyiron 8 coins, Skyiron Ingot 10, Duskquartz Shard 14, Emberite Chunk 20, Emberite Ingot 24, Lumenite Crystal 45, Attuned Lumenite 150.

> [!TIP]
> The Duskbore Pick's Echo Sense shows trails to the nearest ores within 12 blocks. It saves a lot of digging when you hunt Emberite or Lumenite.

## Pickaxe ladder

Each pickaxe is made at the Artisan Bench from the one before it, and the new pickaxe keeps the old one's name and enchantments. The Skyiron, Duskbore, Emberheart and Lumen Breaker steps also need Collection III of their ore (see [Crafting](crafting) and [Collections](collections)).

| Pickaxe | Needs | Ingredients besides the previous pickaxe |
| --- | --- | --- |
| Prospector | Crafting table | 3 iron blocks, 2 sticks |
| Delver's | Mining 8 | 12 Glimmer Crystals, 8 iron ingots |
| Skyiron | Mining 18 | 24 Skyiron Ingots, 2 Prism Fragments |
| Duskbore | Mining 26 | 32 Duskquartz Shards, 8 Skyiron Ingots |
| Emberheart | Mining 35 | 32 Emberite Ingots, 1 Heartstone |
| Hellforged | Mining 40 | 24 Hellforged Ingots, 8 Emberite Ingots (made from the Emberheart Pickaxe) |
| Lumen Breaker | Mining 45 | 16 Attuned Lumenite, 1 Prism, 1 Heartstone (made from the Emberheart or Hellforged Pickaxe) |

{{items:skyrpg_items:quarry_pick, skyrpg_items:prospector_pickaxe, skyrpg_items:delver_pickaxe, skyrpg_items:skyiron_pickaxe, skyrpg_items:duskbore_pick, skyrpg_items:emberheart_pickaxe, skyrpg_mining:hellforged_pickaxe, skyrpg_items:lumen_breaker}}

### Pickaxe abilities

Right-click with the pickaxe to use its ability.

| Pickaxe | Ability | Cooldown |
| --- | --- | --- |
| Skyiron | **Skyward Burst**: +150 Mining Speed for 10 seconds | 60 s |
| Duskbore | **Echo Sense**: coloured trails point at the nearest ores within 12 blocks for 8 seconds | 45 s |
| Emberheart | **Molten Core**: for 20 seconds what you mine comes out smelted, and +100 Mining Speed | 90 s |
| Hellforged | **Hellfire Vein**: for 15 seconds every ore also breaks a touching ore of the same kind, and +80 Mining Speed | 75 s |
| Lumen Breaker | **Lumen Pulse**: for 15 seconds every ore you mine drops twice | 120 s |

Smelting only applies to raw ores (raw iron, copper and gold, ancient debris, Raw Skyiron, Emberite Chunks and Hellforged Scrap), never to logs or sand. The Hellforged Pickaxe smelts permanently.

## Mining armour

Each set is crafted at the bench from that tier's material, and its full-set bonus does something you will notice. [Armour sets](armour-sets) covers every set in the game, and the Runeforged set from the Dwarven Halls also gives Mining Speed.

| Set | Where from | Rarity | Four pieces together | Full-set bonus |
| --- | --- | --- | --- | --- |
| {{item:skyrpg_items:prospector_chestplate\|Prospector}} | Crafting table (stone and an iron ingot per piece), or Brannoc Ashforge for 3,650 coins | Uncommon | 50 Mining Speed, 25 Mining Fortune, 70 Defense | **Lamplight Seams**: Your lamp finds the rich veins: dig faster and pull more ore. Bonus stats: 30 Mining Speed, 15 Mining Fortune. |
| {{item:skyrpg_items:delver_chestplate\|Delver}} | Tamsin Deepholt, Mining 10, 41 Glimmer Crystals for the set; Artisan Bench, Mining 8: 48 Glimmer Crystal, 24 Leather, 24 Iron Ingot | Uncommon | 70 Mining Speed, 35 Mining Fortune, 100 Defense | **Lantern Bearer**: Night Vision while you're below y 40. Bonus stats: 40 Mining Speed, 20 Mining Fortune. |
| {{item:skyrpg_items:skyforged_chestplate\|Skyforged}} | Artisan Bench, Mining 18: 48 Skyiron Ingot, 24 Glimmer Crystal, 4 Prism Fragment | Rare | 110 Mining Speed, 55 Mining Fortune, 150 Defense | **Skyward Strikes**: Every 50th ore you mine gives +200 Mining Speed for 10 seconds. Bonus stats: 60 Mining Speed, 30 Mining Fortune. |
| {{item:skyrpg_items:deepwarden_chestplate\|Deepwarden}} | Artisan Bench, Mining 26: 72 Duskquartz Shard, 24 Skyiron Ingot, 48 Glimmer Crystal | Epic | 160 Mining Speed, 80 Mining Fortune, 200 Defense | **Warden of the Deep**: Darkness and Mining Fatigue can't touch you, and the stone tells you where ore is once a minute. Bonus stats: 80 Mining Speed, 40 Mining Fortune. |
| {{item:skyrpg_items:emberheart_chestplate\|Emberheart}} | Artisan Bench, Mining 35: 48 Emberite Ingot, 72 Glimmer Crystal, 1 Heartstone | Epic | 220 Mining Speed, 110 Mining Fortune, 240 Defense | **Molten Heart**: What you mine comes out smelted, and fire and lava can't burn you. Bonus stats: 120 Mining Speed, 80 Mining Fortune. |
| {{item:skyrpg_items:lumenward_chestplate\|Lumenward}} | Artisan Bench, Mining 45: 24 Attuned Lumenite, 48 Emberite Ingot, 96 Glimmer Crystal, 4 Prism, 1 Heartstone | Legendary | 300 Mining Speed, 150 Mining Fortune, 280 Defense | **Lumenward's Light**: Rare finds are twice as likely, and soft motes of light follow you. Bonus stats: 160 Mining Speed, 120 Mining Fortune. |

{{items:skyrpg_items:prospector_chestplate, skyrpg_items:delver_chestplate, skyrpg_items:skyforged_chestplate, skyrpg_items:deepwarden_chestplate, skyrpg_items:emberheart_chestplate, skyrpg_items:lumenward_chestplate}}

## Talents

You get one talent point per Mining level (K, Talents). A reset is free once a day, then costs 2,000 coins.

| Talent | Ranks | Needs | Per rank |
| --- | --- | --- | --- |
| Glimmer Sense | 5 | Mining 0 | +8% Glimmer Shards |
| Steady Hands | 5 | Mining 0 | +8 Mining Speed |
| Deep Pockets | 5 | Mining 0 | +5 Mining Fortune |
| Node Keeper | 5 | Mining 5 | Nodes regrow 6% faster |
| Gem Eye | 5 | Mining 10 | Rare finds 10% more likely |
| Lamplighter | 1 | Mining 10 | Night Vision below y 40 |
| Vein Echo | 3 | Mining 15 | 4% chance to also mine a touching ore of the same kind |
| Deep Study | 1 | Mining 20 | +10 Mining Insight |
| Prospector's Luck | 1 | Mining 30 | 6% chance for ores to drop everything twice |

## Mining enchants

Pickaxe enchants are bound at an enchanting table or at Seraphine's (see [Enchanting](enchanting)). The effects below are at the highest level.

| Enchant | Rarity | Effect |
| --- | --- | --- |
| Prosperity | Common | +50 Mining Fortune while held |
| Swiftcut (any tool) | Common | +80 Mining Speed while held |
| Glimmering | Uncommon | +20% Glimmer Shards |
| Forgefire | Rare | What you mine comes out smelted |
| Rich Vein | Rare | 6% chance an ore drops everything twice |
| Veinseeker | Rare | 16% chance mining an ore also mines a touching ore of its kind |
| Gemcutter | Epic | Rare finds 55% more likely while mining |
| Motherlode | Ultimate | 0.8% chance per ore of +200 Mining Fortune for 15 seconds (tomes only) |

## Events and contracts

**Glimmer Rush** is a five-minute event. Coal, iron, copper, redstone and lapis ore score 1, gold, emerald, diamond, Skyiron and Duskquartz ore score 3, Emberite and Lumenite ore score 8, and nodes score 1. Mining also has daily contracts (see [Contracts](contracts) and [Events](events)).

## See also

- [Skills overview](skills-overview)
- [Crafting](crafting)
- [Tools](tools)
- [Armour sets](armour-sets)
- [Enchanting](enchanting)
- [Pets](pets)
- [Collections](collections)
