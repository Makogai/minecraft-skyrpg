---
title: Ender Wyrm
category: world
order: 9
icon: skyrpg_combat:wyrm_scale
summary: The final boss. How it is summoned today, its three phases and every attack, Void Echoes, its rewards and what is still to come.
---
The **Ender Wyrm** is SkyRPG's final boss: a long serpentine dragon that circles above its arena, dives at you, breathes void fire, calls Void Echoes and, in its last phase, brings down a storm of void bolts. It is the hardest fight in the game, and the most generous.

> [!WARNING]
> **Not fully in the game yet.** The Wyrm itself is finished, but the **altar** that players would build to summon it is not. Right now it can only be started by an admin (`/wyrm summon`) or with a spawn egg in creative mode. The Wyrmscale armour and the Voidreaver sword it is meant to drop are not made yet.

## How it is summoned

The plan is an **Ender Wyrm Altar** in the End, built from {{item:skyrpg_world:astral_shard}} and one of each dungeon trophy, after the dragon is dead. None of that is in the game yet. Until then:

- An op uses `/wyrm summon`, and the Wyrm appears 28 blocks away and 18 blocks up, circling the spot where the command was used. That spot becomes the **arena centre**.
- Only one Wyrm can be alive in a dimension at a time. `/wyrm kill` removes any.

The Ender Wyrm quest, from Hollis Barrowmead in Skyhaven, asks you to "Defeat 1 Ender Wyrm" and pays 25,000 coins, 2,000 XP and 16 golden apples. It unlocks after the Astral Observatory quest.

## Stats

| | |
| --- | --- |
| Level | 100 |
| Health | 1,024 base, with half damage taken beyond that, so about 2,000 effective |
| Armour | 20, with toughness 10 |
| Attack | 24 |
| Knockback | Immune |
| Body | A long head and 12 body segments, about 18 blocks end to end |

The Wyrm flies through blocks, so it cannot be hidden from by building. For the first 2 seconds after it appears it cannot be hurt. It only targets non-creative, non-spectator players within 100 blocks of the arena centre, and its boss bar shows to everyone within 160 blocks.

## The fight

The Wyrm is always in one of a few states, and every attack is **telegraphed** by particles and a sound before it lands. Between attacks it circles the arena at about 18 blocks above the centre at a radius of 28 blocks (24 in phase 3). When its circling wait is up (6 seconds in phase 1, 4.5 in phase 2, 3.5 in phase 3), it picks the next attack.

### Phases

| Phase | Health | What changes |
| --- | --- | --- |
| I | above 66% | Single dives, 3 Void Echoes. |
| II | 66% to 33% | Faster. Dive chains of 2, 4 Echoes, a shorter wait. |
| III | below 33% | The fastest. Dive chains of 3, shorter wind-ups and the **void storm**. |

At each phase change it roars, sends out rings, and **summons Void Echoes immediately**.

### Attacks

| Attack | What you see | What it does |
| --- | --- | --- |
| **Dive** | A line of soul fire from its mouth to you, and a ring on the ground around you. It rears for 1.5 seconds (1.1 in phase 3), then dives along the line. | Hits for its full attack and throws you up. Hits each player once per dive. Chains 1, 2 or 3 dives by phase. |
| **Void breath** | It hovers 17 blocks away, opens its jaws and gathers void light for 2.1 seconds (1.5 in phase 3) while the cone's edges are drawn in soul fire. | A cone of void breath 26 blocks long and 34 degrees wide, which slowly tracks you for 2.75 seconds (3.5 in phase 3). Each pulse (every quarter second) deals 30% of its attack and applies Wither II. Clouds linger where it hits the ground. |
| **Summon Void Echoes** | It rises over the arena while rings grow on the ground for 2.5 seconds. | 3 Echoes (4 from phase 2) step out of the rings, 6 to 12 blocks from you. |
| **Void storm** (phase 3) | A warning in chat ("The sky above the arena begins to split...") and 3 seconds of rising rings. | For about 8.5 seconds, void bolts fall at each player's position every 0.4 seconds, plus random bolts around the arena. Each is marked with a ring on the ground for a second before it strikes. |

The attack choice at each decision is random, weighted by what is off cooldown:

- In phase 3, if the storm is ready, there is a 50% chance of it.
- If a summon is ready and few Echoes are alive, a 50% chance of that.
- Otherwise 40% breath, 60% a dive chain.

Echo summons have a cooldown of 25, 20 and 16 seconds by phase, and the storm has a cooldown of about 50 seconds.

### Void Echoes

Void Echoes are named, purple endermen:

- 100 health, 12 damage, level 70.
- They are persistent and hunt the player the Wyrm picked.
- The Wyrm only summons more when few are left: none alive in phase 1, at most 1 in phases 2 and 3.
- They disappear when the Wyrm dies.

> [!TIP]
> Clear the Echoes quickly: more than one at a time cuts into the space you have to dodge dives, and the breath cone punishes standing still. Stay away from the soul-fire line the moment you see it.

> [!TIP]
> The void storm's bolts are marked on the ground first. Keep moving sideways, and do not stand in a ring.

## Rewards

| Reward | Who gets it |
| --- | --- |
| 8 to 14 **Wyrm Scales** and 1 **Void Heart** | Dropped on death |
| 12,000 XP | Experience orbs on death |
| 30,000 Combat XP | The player who dealt the most damage |
| 20,000 Combat XP | Every other player who damaged it |
| A server-wide announcement | The first time the Wyrm is slain on this server, and a smaller one for each kill |

When it dies, each player who hurt it gets a ranking ("#1 ... damage to the Ender Wyrm") with their damage.

{{items:skyrpg_combat:wyrm_scale, skyrpg_combat:void_heart}}

Wyrm Scales and Void Hearts have **no recipes or sell prices yet**. They are the materials for the planned end-game gear.

## Still to come

- The Ender Wyrm Altar and its recipe.
- The **Wyrmscale** armour set and the **Voidreaver** sword, made from the Wyrm's drops.

Until then, ask the staff if you want to try the fight.

## See also

- [The End](the-end)
- [Astral Observatory](astral-observatory)
- [Boss List](boss-list)
- [Dungeons Overview](dungeons-overview)
- [Combat Basics](combat-basics)
