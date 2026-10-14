---
title: Combat basics
category: combat
order: 2
icon: minecraft:iron_sword
summary: Mob levels, nameplates, damage numbers, Elites, Valor Marks, Dread Fangs, Nightmare Cores, the Training Dummy and Combat XP.
---
Every hostile mob in the survival world has a level. The higher the level, the more health it has, the harder it hits and the more it pays when it dies. This page explains how levels are set, what you see in a fight, and what a kill rewards you with. Kestrel Vane, the weaponsmith in Skyhaven, is the Combat Master and buys most of what drops.

## Mob levels

A hostile mob gets its level when it spawns. Mobs in the hub never get one.

| Where | Level |
| --- | --- |
| Overworld surface | 1 at world spawn, +1 for every 120 blocks away, up to level 30 |
| Overworld, below y 0 | +5 |
| Overworld, below y -32 | +10 (instead of +5) |
| The Nether | 20, +1 for every 180 blocks from the world spawn |
| The End | 40, +1 for every 480 blocks from the world spawn |

The result is capped at 50 first. Then extra levels are added on top, past the cap:

- **Danger regions:** +12 to +20 depending on the region. See [Danger regions](danger-regions).
- **Deep layers:** +4 (Glimmer Caverns), +7 (Fungal Depths), +10 (Dwarven Halls layer) and +14 (Magma Rifts). See [The Deep](the-deep).
- **Blood Moon:** +10 everywhere in the overworld. See [Blood Moon](blood-moon).
- **Dungeons:** each dungeon sets its own levels. See [Dungeons overview](dungeons-overview).

Each level above 1 adds **8% health** and **5% damage** (multiplied, not stacked on the Elite bonus).

| Level | Health | Damage |
| --- | --- | --- |
| 1 | x1.00 | x1.00 |
| 10 | x1.72 | x1.45 |
| 20 | x2.52 | x1.95 |
| 30 | x3.32 | x2.45 |
| 50 | x4.92 | x3.45 |

> [!NOTE]
> Mobs you spawn with a spawn egg or a command are levelled too, so test mobs behave like real ones.

### Nameplates

When you can see a levelled mob and it is within 20 blocks, it shows a plate like `[Lv 12] Zombie 85/100`. The level is green below 10, yellow below 20, gold below 30, red below 40 and dark red from 40. The health is on the same scale as your own health bar (5 SkyRPG health is 1 vanilla point) and turns yellow below half and red below a quarter. Bosses stronger than vanilla health allows show their real health, not the capped number. You can switch plates off in Settings under HUD ("Mob nameplates").

### Damage numbers

Each hit you land pops a number up in front of the mob for under a second. Normal hits are grey. Critical hits are bold gold between two white sparkles. The number is the damage as the stats work it out: see [Stats](stats).

## Elites

Some naturally spawned hostile mobs are **Elites**. They never appear in the hub.

| Where | Chance |
| --- | --- |
| Surface, daytime | 3% |
| Surface, dark | 6% |
| Underground (below y 40 with no open sky) | 8% |

A Blood Moon makes Elites three times as likely everywhere. An Elite is three levels higher than it would have been, has **4 times the health**, **+50% damage**, is 10% faster and 15% bigger, shows a gold `Elite` nameplate and glitters. Elites in dungeons are placed on purpose and also carry the dungeon's vault key: see [Dungeons overview](dungeons-overview).

Killing an Elite pays:

- 50 to 200 coins and 40 Combat XP
- an {{item:skyrpg_combat:elite_trophy}} (Kestrel buys them for 250 coins)
- 3 extra Valor Marks, on top of the normal level rewards
- a 1 in 50 chance of a Lucky Clover or a Prism Fragment

The **Elite Hunt** contract asks for 3 to 6 Elites and pays 1,200 coins, 800 Combat XP and 2 Elite Trophies. See [Contracts](contracts).

## What a kill pays

Killing a levelled mob (with a melee hit, or an arrow you fired) gives:

- **Combat XP.** The base XP for that kind of mob (a zombie or skeleton is 6, an enderman 12, a wither skeleton 15) plus **1.5 per mob level**. Combat Insight raises it. See [Skills overview](skills-overview).
- **Valor Marks.** A chance of `25% + 1% per level` (capped at 100%) to drop `1 + 1 per 15 levels` {{item:skyrpg_combat:valor_mark|Valor Marks}}.
- **A Dread Fang or a Nightmare Core**, rarely (see below).
- The mob's normal drops.

| Mob level | Valor Mark chance | Marks dropped |
| --- | --- | --- |
| 1 | 26% | 1 |
| 15 | 40% | 2 |
| 30 | 55% | 3 |
| 45 | 70% | 4 |
| 60 | 85% | 5 |

Nine Valor Marks craft into one {{item:skyrpg_combat:valor_seal|Valor Seal}} (a 3 by 3 grid), and a seal can be split back into nine. Kestrel takes marks as payment for the Fangguard armour and a Pup Egg. Seals go into slayer gear and swords. See [Weapons](weapons) and [Armour sets](armour-sets).

### Rare drops

| Drop | Needs | Chance |
| --- | --- | --- |
| {{item:skyrpg_combat:dread_fang}} | level 10+ | 1 in 400 |
| {{item:skyrpg_combat:nightmare_core}} | level 30+ | 1 in 4,000 |

At most one of the two drops per kill, and a core is rolled first. **Magic Find** multiplies both chances by `1 + Magic Find / 100`. A message tells you when one drops. Kestrel buys fangs for 1,200 coins and cores for 15,000.

### Farmed mobs

Mobs from a spawner (including the spawners you buy from Morwen) and the helpers some mobs call in (Gravecaller zombies, Bog Hag slimes) are **farmed**. They drop their normal loot and give only a quarter of the level XP. They give no Valor Marks, no fangs or cores, never become Elites and do not count for slayer quests. See [Slayers](slayers).

## The Training Dummy

The {{item:skyrpg_combat:training_dummy}} is a mannequin you place to test your gear.

- Kestrel Vane sells it for 500 coins. Place it by right-clicking a block.
- Hit it and the action bar shows your **damage per second over the last 5 seconds** and your last hit.
- It never dies, and only players can hurt it.
- Each hit gives 0.5 Combat XP, up to 150 Combat XP per hour, so it cannot be left running.
- Sneak and hit it with an empty hand to pick it up again.

> [!TIP]
> Swap a piece of gear, hit the dummy for ten seconds, and compare the DPS. It is the quickest way to find out whether a reforge or an enchant is worth it.

## See also

- [Stats](stats): how damage and defence are worked out.
- [Mobs](mobs): every custom mob in the world.
- [Slayers](slayers) and [World bosses](world-bosses)
- [Weapons](weapons), [Armour sets](armour-sets)
- [Skills overview](skills-overview)
