---
title: The Survival World
category: world
order: 1
icon: minecraft:grass_block
summary: The survival world versus the Skyhaven hub, the Wildgate, /survival and /hub, what is protected where, and what happens when you die.
---
SkyRPG has two places to be. **Skyhaven** is the safe hub where everyone spawns, shops and talks to NPCs. The **survival world** is the normal Minecraft world (Overworld, Nether and End) where you mine, fight, farm and explore. Knowing how the two fit together tells you where you are safe, where you are not, and what you keep when things go wrong.

## Two worlds

| | Skyhaven (the hub) | Survival world |
| --- | --- | --- |
| What it is | A floating town in its own void dimension | The Overworld, the Nether and the End |
| Spawn | You arrive at the hub on first join | Reached through the Wildgate or `/survival` |
| Building and breaking | Not allowed (creative mode excepted) | Allowed, as in normal Minecraft |
| Fighting and damage | Nothing can hurt you, and you cannot hit anything | Everything can hurt you |
| Hunger | Your food bar stays full | Normal |
| Mobs | None | Levelled monsters, Elites, bosses, danger regions |

The hub holds the trade Masters, the shops, the Artisan Bench and the bank. See [Skyhaven](skyhaven) for the town itself.

## The Wildgate

The **Wildgate** is the glowing ring on the south islet of Skyhaven. Walk into it and you are sent to the survival world. There is no button to press and no cost.

- You return to **exactly where you left**: the same dimension, coordinates and facing. If you left from the Nether, you come back to the Nether.
- If you have never left the hub before, you arrive at the survival world's spawn, on the surface.

You can also skip the walk with commands:

| Command | What it does |
| --- | --- |
| `/hub` | Takes you to Skyhaven from anywhere. Your survival position is remembered. |
| `/survival` | Takes you back to where you left the survival world. Only works while you are in the hub. |

### Warm-ups

`/hub` and `/survival` do not work instantly:

- **In combat** (see [Combat basics](combat-basics#combat-tag)) they are refused, and the message shows how many seconds are left.
- Otherwise you must **stand still for 5 seconds**. The action bar counts down, a ring of sparkles closes in around your feet and a tick sounds each second, then you go.
- The warm-up is **cancelled** if you move more than about a third of a block, take damage, get into a fight or change dimension. Dying or logging out cancels it too.
- Type the command again during the warm-up to cancel it yourself.
- **Creative and spectator** players skip the wait, so builders can move freely.

Walking through the Wildgate has no warm-up.

> [!TIP]
> `/hub` does not cost anything and works from the Nether and End too. If you are low on health and a long way from a waystone, it is the quickest way out. `/survival` puts you straight back where you stood.

Using a waystone to return to Skyhaven does the same thing as `/hub`. See [Waystones and Travel](waystones-and-travel).

## What is protected where

### In the hub

The hub is kept exactly as built. Unless you are in creative mode:

- You cannot break or place blocks, use buckets, or spawn mobs with eggs.
- You cannot attack anything, and nothing takes damage (the void is the one exception).
- Item frames, paintings and armour stands cannot be changed.
- Blocks cannot be used unless they are on the hub's "usable" list. Doors, buttons and similar things work.
- Farmland cannot be trampled.
- Your hunger and saturation are topped up while you stand there.
- If you fall off the island below the void line (y 0), you are put back at the spawn point.

### In the survival world

There is no land protection. You can build, dig and place planters anywhere outside the hub, and so can the monsters. A few things are deliberately safe:

- **Waystones** survive explosions, and breaking one with a pickaxe gives you the waystone as an item to place somewhere else (everyone attuned follows it). Skyhaven's and the dungeon entrances' waystones are fixed in place. See [Waystones and Travel](waystones-and-travel).
- **Chests inside dungeons** are per player, so nobody can steal your loot.
- **Mounts** that would die are knocked out and fly home instead. See [Mounts](mounts).

## How dangerous is it?

Every hostile mob in the survival world gets a level when it spawns:

| Where | Level |
| --- | --- |
| Surface | 1 near world spawn, plus 1 for every 120 blocks you travel out, up to 30 |
| Underground | +5 below y 0, +10 below y -32 |
| The Nether | Starts at 20 |
| The End | Starts at 40 |
| Cap | 50 for these base levels |

Each level above 1 adds 8% health and 5% damage. [Danger regions](danger-regions), the [Deep](the-deep) and the [Blood Moon](blood-moon) then add extra levels **on top of** the cap, so a Shattered Highlands monster can reach level 50 on the surface alone and level 60 under a Blood Moon.

Mobs you spawn with eggs or commands have no level.

> [!WARNING]
> Distance from the world spawn is the main thing that makes the surface harder. A trip of 3,600 blocks puts you at level 30 on ordinary ground, before any region bonus.

## Death and respawn

The world spawn point is **Skyhaven**. If you die and have no bed or respawn anchor set, you wake up in the hub. If you slept in a bed in the survival world, you respawn there as in vanilla Minecraft.

What you keep when you die (it is stored on you, not in your inventory):

- Your **coin purse** and your **bank**
- Your **accessories** (the Amulet, Cloak, Belt and Bracers slots) and your **backpacks**
- Your **mounts** and their stable
- Your slayer progress, titles and enchanting codex

Everything else in your main inventory and armour follows the server's normal death rules. I could not check the server's keepInventory setting from the game's files, so ask the staff if you need to know.

A red **"Last death"** waypoint is added to your map where you died, so you can walk back and collect what dropped. See [Waypoints](waypoints) and [HUD and Minimap](hud-and-minimap).

## See also

- [Waystones and Travel](waystones-and-travel)
- [Danger Regions](danger-regions)
- [The Deep](the-deep)
- [Brainrots](brainrots), creatures to catch out in the world
- [Skyhaven](skyhaven)
- [Combat Basics](combat-basics)
- [Welcome to SkyRPG](welcome)
