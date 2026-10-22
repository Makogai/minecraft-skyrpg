---
title: Stats
category: combat
order: 1
icon: minecraft:iron_chestplate
summary: Every stat in SkyRPG, what it does, where it comes from and the formulas for damage, crits, defence and mana.
---
Stats are the numbers that make your character stronger. Gear, skill levels, pets, talents and slayer levels all add to them, and the game adds them up twice a second. This page lists every stat, what it does in the game, and the maths behind combat. Open the **Stats** page in the K menu to see your own totals; hover a stat to see how much each source adds.

## How stats work

- Every player starts with a base value for each stat (the table below). Sources add to it.
- Stats come from your armour, weapon, accessories and set bonuses, from your skill levels, from pets, from talents, and from slayer levels. See [Skills overview](skills-overview), [Armour sets](armour-sets), [Accessories](accessories) and [Pets](pets).
- Most stats are plain numbers. **Crit Chance** and **Sea Creature Chance** are percentages.
- Health and damage use SkyRPG's own scale: **5 SkyRPG health is 1 vanilla health point**. 100 Health is 20 vanilla points, which is 10 hearts. Damage numbers, mob nameplates and boss bars all use the same scale.
- More Health doesn't make you hungrier. Healing back to full costs the same hunger as it does in vanilla with 10 hearts, however much Health you have.

## Combat stats

| Stat | Starts at | What it does |
| --- | --- | --- |
| Health | 100 | Your maximum health. 100 Health is 10 hearts. |
| Defense | 0 | Cuts the damage you take. 100 Defense halves it. |
| Strength | 0 | Each point adds 1% to your melee damage. |
| Crit Chance | 30% | The chance that a melee hit is a critical hit. |
| Crit Damage | 50% | The extra damage a critical hit does. |
| Speed | 100 | How fast you walk. 100 is normal. |
| Intelligence | 0 | Each point adds 1 to your maximum mana and 1% to your spell damage. Abilities and spells cost mana. |
| Damage | none | Only weapons have it. It is the weapon's base damage. |

### Damage and crits

Only your own melee hits use Strength and crits. Arrows, thrown things and item abilities do not.

1. Start with the weapon's **Damage** stat. A weapon with 120 Damage deals 120 on a fully charged swing (internally, 5 Damage is 1 vanilla damage point). If you swing before your weapon has recharged, the hit is weaker, as in vanilla.
2. Multiply by `1 + Strength / 100`. Strength 100 doubles the hit.
3. Roll for a crit. If a random number from 0 to 100 is below your Crit Chance, multiply by `1 + Crit Damage / 100`. Crits show a gold, bold number between two sparkles and play a crit sound.
4. Other multipliers apply on top, multiplied together: damage bonuses against certain mobs (slayer weapons and the Cryptbreaker), and the damage reductions bosses have (wards, tougher-than-vanilla health). See [Slayers](slayers) and [Dungeons overview](dungeons-overview).

Worked example: a 120 Damage weapon, 100 Strength, 30% Crit Chance and 50% Crit Damage.

| Hit | Damage |
| --- | --- |
| Normal | 120 x 2.0 = 240 |
| Critical | 240 x 1.5 = 360 |
| Average over many hits | 240 x (1 + 0.30 x 0.50) = 276 |

Crit Chance above 100% is wasted: you already crit every time. Crit Damage is then worth more than more Crit Chance.

> [!TIP]
> Crit Chance and Crit Damage multiply each other, so the lower one is worth more per point. A point of Crit Chance adds 1% of your Crit Damage to your average hit, and a point of Crit Damage adds 1% of your Crit Chance. With the starting 30% and 50%, a point of Crit Chance is the better buy.

### Defense

Every time something hurts you, the damage is multiplied by `100 / (100 + Defense)`.

| Defense | Damage you take | Same as having |
| --- | --- | --- |
| 0 | 100% | your Health |
| 50 | 67% | 1.5 x your Health |
| 100 | 50% | 2 x your Health |
| 200 | 33% | 3 x your Health |
| 300 | 25% | 4 x your Health |

So "effective health" is `Health x (1 + Defense / 100)`. Defense applies to mobs, bosses, traps, fire and falling. It does not apply to damage that ignores invulnerability, such as the void. Armour pieces give Defense directly (they have no vanilla armour points). Slayer armour wards and similar reductions apply before Defense.

### Speed

Speed 100 is normal walking. Walking speed is multiplied by `Speed / 100`, so 150 Speed walks 50% faster.

### Intelligence and mana

You have `100 + Intelligence` maximum mana. Mana regenerates by **2% of your maximum every second**, so an empty bar takes about 50 seconds to refill whatever its size. Item abilities and [spells](spells) spend mana when you use them. A mage's [wand](wands) gives Intelligence while held, and each point also adds **1% to the damage of your spells** (Strength and crits do not apply to spells). If your maximum drops (you take off a piece of gear), your current mana is cut to fit.

## Gathering and fishing stats

| Stat | Starts at | What it does |
| --- | --- | --- |
| Mining Speed | 0 | How fast you break blocks. 100 makes it twice as fast. |
| Mining Fortune | 0 | Extra drops from ores. |
| Farming Fortune | 0 | Extra drops from crops. |
| Foraging Fortune | 0 | Extra drops from logs. |
| Fishing Speed | 0 | Fish bite sooner. Every 25 is one more level of Lure, up to four. |
| Sea Creature Chance | 5% | The chance a catch is a sea creature instead of a fish. |
| Fishing Fortune | 0 | More Tidescales from catches. Every 100 is one more. |

### Fortune

When you break a block that counts (ores, grown crops, logs), it drops its normal drops again once for every full 100 Fortune, plus a chance for one more from the remainder. With 250 Fortune you get 2 extra copies and a 50% chance of a third. Crops only count when fully grown, and ores do not give extra drops if you mine them with Silk Touch. See [Mining](mining), [Farming](farming) and [Foraging](foraging).

## Insight and Magic Find

| Stat | What it does |
| --- | --- |
| Farming Insight | Each point is 1% more Farming XP. |
| Mining Insight | Each point is 1% more Mining XP. |
| Foraging Insight | Each point is 1% more Foraging XP. |
| Combat Insight | Each point is 1% more Combat XP. |
| Fishing Insight | Each point is 1% more Fishing XP. |
| Magic Find | Better odds for rare drops from mobs and bosses. |

Insight multiplies the XP you earn playing by `1 + Insight / 100`. Enchanting and Delving have no Insight stat.

**Magic Find** multiplies the chance of rare drops by `1 + Magic Find / 100`. 50 Magic Find makes a 1 in 400 drop 1.5 times as likely. It helps with Valor Marks, {{item:skyrpg_combat:dread_fang}}, {{item:skyrpg_combat:nightmare_core}}, slayer rare drops and Golden Pet Eggs, and with enchant tome drops. It does not change the world bosses' own rare drop chance. See [Combat basics](combat-basics).

## Where the stats come from

Skills give a fixed amount per level (50 levels each). Delving has talents too.

| Skill | Per level |
| --- | --- |
| Combat | +1 Health, +0.5% Crit Chance |
| Mining | +4 Mining Fortune, +1 Defense |
| Farming | +4 Farming Fortune, +2 Health |
| Foraging | +4 Foraging Fortune, +1 Strength |
| Fishing | +1 Fishing Speed, +1 Health |
| Enchanting | +1 Intelligence, +0.2 Magic Find |
| Delving | +2 Health, +0.25 Magic Find |

Slayer levels add a small stat per level too: Grave Tyrant +3 Health, Broodmother +0.5% Crit Chance, Bone Marshal +1 Strength, Void Stalker +2 Intelligence. See [Slayers](slayers).

> [!NOTE]
> Weapon Damage is not a player stat. Only the weapon you hold carries it, and nothing can add to it.

## See also

- [Combat basics](combat-basics): levels, damage numbers, rewards.
- [Slayers](slayers): slayer levels and gear.
- [Skills overview](skills-overview)
- [Weapons](weapons) and [Armour sets](armour-sets)
- [Accessories](accessories)
