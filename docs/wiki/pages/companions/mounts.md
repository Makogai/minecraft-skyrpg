---
title: Mounts
category: companions
order: 2
icon: skyrpg_mounts:griffin_whistle
summary: Griffins: finding nests, taming, the stable and Mounts page, riding and flight, stamina and the ten colours.
---
Griffins are SkyRPG's first mount: an eagle's head, ruff and wings on a lion's body. You tame one in the wild, keep it in a stable as a **Griffin Whistle**, call it when you need it, and ride it across the sky. There are ten colours in four rarities, and the rarest are worth hunting for.

## The griffin

A griffin has 40 health and 7 attack damage and is a little bigger than a horse. It walks on the ground, flaps its wings in flight and spreads them to glide. You can also get a spawn egg for creative play (the Spawn Eggs tab).

## Colours and rarity

Each new wild griffin rolls a colour by weight (98 in all). The colour sets the rarity, and the rarity decides how often you meet it. The name of its whistle takes the rarity colour (Common keeps its feather colour), and the Mounts page shows the rarity.

| Rarity | Colours | Weight each | Chance each | Look |
| --- | --- | --- | --- | --- |
| Common | Golden, Storm, Snow, Russet | 20 | 20.4% | Russet is a red-brown hawk front with a cream face and a rusty lion body |
| Uncommon | Midnight, Verdant | 6 | 6.1% | Midnight is near-black with blue-violet feathers and silver eyes. Verdant has teal-green feathers and a mossy olive body |
| Rare | Ember, Frost | 2.5 | 2.55% | Ember has a charcoal body, flame-orange feathers, glowing wing tips and pulsing eyes. Frost is pale ice-blue with glowing crystal-blue wing tips |
| Legendary | Prismatic, Astral | 0.5 | 0.51% | Prismatic is pearl white and cycles through the rainbow every 6 seconds with glowing trim. Astral is deep navy with twinkling star specks and faint constellation lines |

Rare and Legendary **wild** griffins give off sparkles (more for Legendary), and taming one announces it to everyone on the server.

## Finding a griffin

Wild griffins live around **nests**, which generate in the mountain biomes (stony, jagged and frozen peaks, windswept hills) but only where the area is part of the Shattered Highlands danger region (see [Danger regions](danger-regions)). A nest is a plus of five {{item:skyrpg_mounts:griffin_nest}} blocks.

- A nest keeps 1 to 3 wild griffins (fixed per nest) around it while a player is within 64 blocks. It checks every 5 to 8 seconds and spawns one when there are fewer than that number within 40 blocks.
- Wild griffins wander within 14 blocks of the nest and are neutral. If you hit one it fights back with a claw and a leaping dive, from 5 to 18 blocks away, for 1.5 times its damage.
- Wild griffins despawn when no player is near.

## Taming

Feed a wild griffin raw beef, mutton, chicken, porkchop or rabbit. Each feeding has a 1 in 5 chance to work (the meat is used either way). When it works, hearts appear and the griffin turns into a **{{item:skyrpg_mounts:griffin_whistle}}** in your inventory. A failed feeding shows smoke and a message.

The whistle holds the griffin's name, colour, owner and two small random stats, shown in its tooltip:

- **Speed:** +0 to +15%.
- **Flight Stamina:** +0 to +25%, which means more seconds of flight.

Both are the average of two random rolls, so middling values are the most common and the maximum is rare. The icon has the feather colour of the griffin.

The distance you ride on griffins is counted as **Griffin flight** on the [Players](players) page of the K menu.

> [!TIP]
> Whoever uses a whistle becomes its owner, so whistles can be traded or given away. Take your whistle out of the stable to hand it over.

## The stable

Right-click a whistle to add it to your **stable**, a saved list of up to 20 mounts (the server can change the number). If no mount is out, the right-click also calls it.

- **Only one mount is out at a time.** Calling another puts the first back.
- A mount goes back to the stable when you put it away, log off, leave its dimension, get more than 16 blocks from it without riding it, or 30 seconds after you get off.
- If it would die, it is knocked out and flies home instead with its health refilled, so a mount is never lost.
- It cannot be hurt at all while you ride it in the hub dimension.

### The Mounts page

Open it from the SkyRPG menu (K, Mounts). On the left is a live 3D preview of the selected griffin in its colour (it spreads its wings when the mouse is over it), with its colour, name, Speed and Flight Stamina. On the right are stat bars with the seconds of flight, then **Your stable** with a count like `4 / 20`: a grid of mounts, the one that is out first with a green mark.

Select a mount, then:

- **Call:** it stands next to you (the one that was out goes back).
- **Put away:** back to the stable.
- **Take out:** moves it into your inventory as a whistle, to trade.

### Commands

| Command | What it does |
| --- | --- |
| `/mounts` | Lists your stable |
| `/mounts call` | Calls your active (or first) mount |
| `/mounts away` | Puts it away |
| `/mounts nest`, `/mounts griffin [color]`, `/mounts tame`, `/mounts claim`, `/mounts storm`, `/mounts give <player> griffin [color]` | Operators only: test and admin tools |

## Riding

Right-click your own griffin to mount it. Nobody else can ride it.

| Where | Input | Does |
| --- | --- | --- |
| Ground | WASD | Walks |
| Ground | Jump | Takes off |
| Air | WASD | Flies in the direction you face |
| Air | Jump | Climbs |
| Air | Sneak | Descends (it does not dismount you in mid-air; on the ground it does) |
| Air | Sprint | Glides faster (1.75 times) |

Flight holds at a hover when you let go. Riders take no fall damage, and neither does the griffin. If you get off in mid-air you get Slow Falling for 8 seconds.

## Stamina

Flight uses a stamina bar. At full stamina a griffin with no Flight Stamina bonus flies for **14 seconds**, and the bonus adds to that (a +25% griffin flies for 17.5 seconds). The bar drains at different speeds:

| Action | Drain compared with normal flight |
| --- | --- |
| Climbing | 1.5 times |
| Gliding | 1.3 times |
| Normal flying | 1 times |
| Hovering | 0.7 times |
| Descending | 0.35 times |

Stamina refills on the ground in about 6 seconds. At zero the griffin glides down slowly at half speed and cannot take off or climb until it is above 10%. A **Flight stamina** bar above the hotbar shows your stamina while you ride (you can move it in the HUD editor, see [HUD and minimap](hud-and-minimap)).

## The Storm Griffin

Not every griffin is friendly. The Storm Griffin is the boss of the Shattered Highlands: a much bigger hostile griffin that attacks the nearest player. It cannot be tamed or ridden and never becomes a whistle. See [Danger regions](danger-regions).

## See also

- [Pets](pets)
- [Danger regions](danger-regions)
- [Waystones and travel](waystones-and-travel)
- [Mount list](mount-list)
- [K menu](k-menu)
