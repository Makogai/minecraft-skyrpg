---
title: Hallow's Eve
category: skyhaven
order: 5
icon: minecraft:jack_o_lantern
summary: The Halloween 2026 event, 30 October to 1 November: the Headless Horseman, a Halloween Blood Moon, double crate drops, Halloween events and a free key and title.
---
**Hallow's Eve** is the Halloween event of 2026. It runs from **Friday 30 October at 18:00** to **midnight on Sunday 1 November** (Central European Time). It starts and ends by itself, so you don't need to do anything to take part. While it is on, a purple bar at the top of the screen shows how long is left, and `/season` tells you the same in chat.

The weekend's [2x Skill XP](skills-overview) runs at the same time, so Saturday and Sunday are the best days of the year to level.

When you join in the week before it starts, and again once it's on, a full-screen splash shows what's coming with a countdown. It shows once a day; click or press any key to close it, and type `/season splash` to see it again. The event's pictures also hang on the billboards at spawn.

## What's on

| | |
| --- | --- |
| **A gift for everyone** | The first time you log in during the event you get a {{item:skyrpg_crates:hallows_key}}, 8 {{item:skyrpg_crates:candy_corn}} and the **Trick or Treat** title. |
| **Double crate drops** | Every chance to get a {{item:skyrpg_crates:hallows_crate}} from a monster is doubled. See [Crates](crates). |
| **The Headless Horseman** | A new world boss that only rides during Hallow's Eve. |
| **Halloween Blood Moon** | The moon rises red on Halloween night (Saturday 31 October), guaranteed. See [Blood Moon](blood-moon). |
| **Halloween events** | Monster Mash and Pumpkin Panic replace the usual events, and they come more often: every 35 to 50 minutes instead of 60 to 90. |

## The Headless Horseman

A giant pumpkin-headed vindicator with a netherite axe. It rises like the other [world bosses](world-bosses) (a warning, then it rises a minute later near a player in the overworld), but only at **dusk**, and only during Hallow's Eve:

- on the **first night** of the event, and every third night after that (a Minecraft day is 20 minutes, so about once an hour while people play);
- on **Halloween night**, together with the Blood Moon.

Someone has to be in the overworld at dusk for it to come.

| | The Headless Horseman |
| --- | --- |
| Body | giant vindicator wearing a jack o'lantern |
| Level | 45 |
| Health | 5,120 |
| Size | 2.3 times |
| Speed | 1.15 times normal |
| Damage | 2.8 times the body's normal attack |
| Minions | 5 pumpkin-headed zombies (level 28) |

It fights like the other world bosses: a ground slam every 8 seconds, minions at two thirds and one third health, and it enrages at the end. Its minions wear pumpkins, so they don't burn at sunrise.

**Rewards** are shared by everyone who damaged it, and the top damager gets half again:

| | |
| --- | --- |
| Coins | 1,500 to 3,000 |
| Valor Seals | 3 to 6 |
| Combat XP | 900 |
| Rare drop | {{item:skyrpg_crates:hallows_key}}, 45% (top damager 67%) |
| Bonus drop | {{item:skyrpg_crates:hallows_crate}}, 30% |

Defeating it gives the **Horseman's Bane** title. It is a boss, so the player who lands the last hit also has a 50% chance at a crate drop during the event.

## Halloween events

During Hallow's Eve the automatic [events](events) are these two:

### Monster Mash

10 minutes. Slay the most monsters. Zombies, husks, drowned, spiders and silverfish are worth 1 point; skeletons, creepers, phantoms and slimes 2; Endermen, pillagers and vindicators 3; witches and evokers 5.

| Place | Prize |
| --- | --- |
| 1st | 500 coins and 2 {{item:skyrpg_crates:hallows_key|Hallow's Keys}} |
| 2nd | 250 coins and a {{item:skyrpg_crates:hallows_key}} |
| 3rd | 120 coins and a {{item:skyrpg_crates:hallows_crate}} |
| Everyone who scored | 4 {{item:skyrpg_crates:candy_corn}} |

### Pumpkin Panic

6 minutes. Harvest the most crops. A pumpkin is worth 4 points, a melon 2, and any other fully grown crop 1.

| Place | Prize |
| --- | --- |
| 1st | 400 coins, a {{item:skyrpg_crates:hallows_key}} and 4 {{item:skyrpg_crates:toffee_apple|Toffee Apples}} |
| 2nd | 200 coins and a {{item:skyrpg_crates:hallows_crate}} |
| 3rd | 100 coins and 4 {{item:skyrpg_crates:toffee_apple|Toffee Apples}} |
| Everyone who scored | 4 {{item:skyrpg_crates:candy_corn}} |

> [!TIP]
> Plant a pumpkin patch before Friday. Pumpkins are worth four times a wheat crop in Pumpkin Panic, and a big patch that has been growing all week can win it on its own.

## Titles

| Title | Rarity | How to earn it |
| --- | --- | --- |
| **Trick or Treat** | Epic | Log in during Hallow's Eve 2026. You can only get it this once. |
| **Horseman's Bane** | Legendary | Defeat the Headless Horseman. |

See [Titles](titles).

## After the event

The Horseman leaves, the events go back to normal, and crate drops go back to their usual chances. The Hallow's Crate keeps dropping until **8 November**, and keys stay on sale until then.

## For admins

**Billboards** are big 16:9 screens showing pictures from web links, for everyone with the SkyRPG mods: a dark cabinet with a glowing trim, chasing marquee bulbs and two spotlights on top. Type `/billboard add <id> <width> <link>` (width in blocks; the height follows). Looking at a wall, it is mounted there, centred where you look; looking at open ground, it stands on steel legs a few blocks in front of you, facing you. Pictures are cropped to fill the screen. `/billboard style <id> stand|wall` switches between the two. `/billboard image <id> <link>` adds another picture (they take turns, every 8 seconds by default; `/billboard seconds <id> <n>`), `/billboard clear <id> <link>` replaces them all with one, and `size <id> <width>`, `nudge <id> <x> <y> <z>`, `move` (to where you look), `frame <id> <#colour|none>`, `remove` and `list` do the rest. From the console use `execute as <player> at @s run billboard ...`. The event's pictures are at `https://makogai.github.io/minecraft-skyrpg/showcase/hallows-eve/` (`poster.png`, `teaser.png`, `bloodmoon.png`, `launch.png`, `splash.png`).


Seasons are data files (`data/<ns>/skyrpg/seasons/<id>.json`) with a start and end, a boss bar, flags (such as double crate drops), an event rotation, a welcome gift and dusk rules. `/season` shows what's on; `/season start <id>` runs one now for a test, and `/season end <id>` before its real start date resets it so it still starts on time.

## See also

- [Crates](crates)
- [World bosses](world-bosses)
- [Blood Moon](blood-moon)
- [Events](events)
- [Titles](titles)
