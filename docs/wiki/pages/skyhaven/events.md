---
title: Events
category: skyhaven
order: 4
icon: minecraft:clock
summary: Timed competitions with a live leaderboard and coin prizes: how they start, how points work, and each event.
---
Events are short competitions that run for everyone on the server. A live leaderboard shows on your screen, the best players win coins and items, and the winners get a podium with confetti when it ends. They are a fast way to earn coins and a reason to try a skill you do not usually play.

## How events start

- **Automatically.** While two or more players are online and nothing else is running, a random event starts about every 60 to 90 minutes. The pool is Timber Rush, Monster Hunt, Harvest Rush, Glimmer Rush and Fishing Frenzy.
- **During a season**, such as [Hallow's Eve](hallows-eve), the pool is that season's own events and they come more often.
- **By an administrator**, at any time. Admins can also start Sky Rings, which is never in the random pool.

When one starts you get a banner and a chat line that names the event and its length.

## Taking part

| Kind | Who scores |
| --- | --- |
| **Everyone** | Every player on the server. Just do the thing the event asks for. |
| **Opt-in** | Only players who join. Type `.join` in chat, or `/event join`. Leave with `.leave` or `/event leave`. |

Five of the six events are open to everyone. Only **Sky Rings** is opt-in.

Things to know:

- Players in creative mode do not score, and neither do spectators.
- Blocks you placed yourself do not score. Break natural ones.
- You cannot break blocks in Skyhaven, so go to the survival world to score in the block-based events.
- Players who join late are in, from that moment on.

## Scoring and prizes

- Each event has scoring rules. Every rule that matches an action adds its points, so one action can match more than one rule. A sea creature scores 1 for the catch and 2 more for being a sea creature, for 3 in all.
- Your score is shown on the HUD. Players at the top of the board win prizes.
- **Ties share a place.** Two players tied for first are both first, and the next is third.
- Only players with at least 1 point can place or win anything.
- Prizes are handed out the moment the event ends. Coin prizes go straight into your purse.
- Some events also give a small prize to everyone who scored. Monster Hunt does.

### What you see

- A **card** on the HUD (top right by default) for each running event, up to three. It shows the countdown bar, which turns gold in the last minute and red in the last ten seconds, the top five players and your own row. Your row flashes and shows a "+N" whenever you score.
- Chat lines and action-bar countdowns warn you before the end.
- When the event ends, a **winner board** shows the podium with confetti, your place and your prize. You can turn it off in [Settings](settings) under HUD. See [HUD and minimap](hud-and-minimap).
- The Tab list also shows running events.

## The events

### Timber Rush

Chop the most logs in **5 minutes**. Every log of any kind scores 1 point.

| Place | Prize |
| --- | --- |
| 1st | 100 coins |
| 2nd | 50 coins |
| 3rd | 25 coins |

Warnings come at 2 minutes, 1 minute, 30 seconds and 10 seconds.

### Monster Hunt

Slay hostile mobs in **10 minutes**.

| Mob | Points |
| --- | --- |
| Zombie, husk, drowned, spider, cave spider | 1 |
| Skeletons, creeper, witch | 2 |
| Enderman | 3 |

| Place | Prize |
| --- | --- |
| 1st | 250 coins and 2 {{item:minecraft:diamond|diamonds}} |
| 2nd | 120 coins |
| 3rd | 60 coins |
| Everyone who scored | 4 {{item:minecraft:cooked_beef|steak}} |

Warnings come at 5 minutes, 1 minute and 10 seconds, with a banner at 2 minutes.

### Harvest Rush

Harvest the most fully grown crops in **5 minutes**.

| Crop | Points |
| --- | --- |
| Wheat, carrots, potatoes, beetroot, nether wart, cocoa (fully grown) | 1 |
| Melon, pumpkin | 2 |
| Sunwheat, Frostberry (fully grown) | 3 |
| Emberpepper, Moonmelon (fully grown) | 5 |

| Place | Prize |
| --- | --- |
| 1st | 150 coins |
| 2nd | 75 coins |
| 3rd | 40 coins |

### Glimmer Rush

Mine the most ore in **5 minutes**. Rarer ores and nodes score more.

| Block | Points |
| --- | --- |
| Coal, iron, copper, redstone, lapis ore | 1 |
| Gold, emerald, diamond, Skyiron and Duskquartz ore | 3 |
| Emberite and Lumenite ore | 8 |
| Ore nodes | 1 |

The prizes are the same as Harvest Rush: 150, 75 and 40 coins.

### Fishing Frenzy

Catch the most in **5 minutes**. Every catch scores 1 point, and a sea creature scores 3 in all. Prizes: 150, 75 and 40 coins.

### Sky Rings

Fly through gold rings above Skyhaven for **60 seconds**. It is the only opt-in event.

- Type `.join` in chat or `/event join` during the **20-second lobby**.
- Your inventory is saved safely on the server and cleared. You get an elytra and 64 firework rockets, and start gliding as soon as you are in the air.
- Each ring you fly through is one point.
- Your whole inventory comes back when the event ends or when you leave, or the next time you log in after a crash.
- First place wins 100 coins.

Warnings come at 30 and 10 seconds.

## Tips

> [!TIP]
> The prizes are modest, but the skill XP and materials you gather during the event are yours to keep. Start with the skill you were going to play anyway.

> [!TIP]
> Join Sky Rings the moment the lobby opens. The 20 seconds go quickly.

## For admins

Admins can start any event with `/event start <id> [duration]`, stop one, change the clock or scores, and turn the automatic schedule off with `/event auto off`. These are the same commands that run the shipped events.

## See also

- [Contracts](contracts)
- [Coins and the bank](coins-and-bank)
- [HUD and minimap](hud-and-minimap)
- [Skyhaven](skyhaven)
