---
title: Ender Wyrm
category: world
order: 9
icon: skyrpg_combat:wyrm_scale
summary: The final boss. The Void Sigil that summons it, its three phases and every attack, Void Echoes, and the Wyrmscale armour and Voidreaver sword it pays for.
---
The **Ender Wyrm** is SkyRPG's final boss: a long serpentine dragon that circles above its arena, dives at you, breathes void fire, calls Void Echoes and, in its last phase, brings down a storm of void bolts. It is the hardest fight in the game, and the most generous.

## How it is summoned

You call the Wyrm with a **{{item:skyrpg_combat:void_sigil}}**. Anyone can do it, no admin needed.

**Crafting.** At the Artisan Bench (Combat 40), one craft turns **16 {{item:skyrpg_world:astral_shard|Astral Shards}} and one of each dungeon trophy** into **3 Void Sigils**:

| Trophy | From |
| --- | --- |
| {{item:skyrpg_world:warden_sigil}} | Forgotten Crypt |
| {{item:skyrpg_world:arachne_eye}} | Spider Hollow |
| {{item:skyrpg_world:tide_pearl}} | Drowned Temple |
| {{item:skyrpg_world:thanes_rune}} | Dwarven Halls |
| {{item:skyrpg_world:tyrants_core}} | Infernal Citadel |
| {{item:skyrpg_world:fallen_crest}} | Ruined Citadel |
| {{item:skyrpg_world:bonelord_phylactery}} | Catacombs |
| {{item:skyrpg_world:star_warden_core}} | Astral Observatory |

So one full set of trophies pays for three fights. Each fight spends one Sigil.

**Using it.** Right-click it **in the End**, once the **Ender Dragon is dead** and **no Ender Wyrm (or summoning) is already running in the End**. Otherwise it tells you why not. You can use it anywhere in the End, but remember that the Wyrm flies through blocks and its attacks need open ground: the main island is the natural arena, but keep away from the exit portal in the middle (stepping into it sends you home). The spot where you stand becomes the **arena centre**.

**The ritual.** The Sigil is spent at once and the void answers for about 6.5 seconds: a ring of light closes in on you on the ground, two spirals of void light climb into the sky, the ground rumbles harder with every pulse, the screen flashes violet, lightning cracks around you, and then the sky tears open and the Wyrm appears 28 blocks away and 18 up, circling the arena. Hold your ground. If everyone has walked away before it ends, the ritual fizzles and you get your Sigil back.

**Fighting together.** It is built for a party of two to four. Everyone is credited, see [Rewards](#rewards). Only one Wyrm can be alive in a dimension at a time.

**If it all goes wrong.** If every player in the arena is dead (or has left, or is in creative or spectator mode) for 30 seconds, the Wyrm loses interest and sinks back into the void, its Echoes vanish, and the player who used the Sigil gets it back (if they are online). So a wipe costs you time, not trophies. (Staff can also use `/wyrm summon` and `/wyrm kill`.)

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

The Wyrm flies through blocks, so it cannot be hidden from by building (but it will not dive below the ground: it skims the highest block under it). For the first 2 seconds after it appears it cannot be hurt. Only its **head** can be hurt: hit the head, not the body. It only targets non-creative, non-spectator players within 100 blocks of the arena centre, and its boss bar shows to everyone within 160 blocks.

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

**Everyone who fought it is paid in full**, not just the player who lands the last hit: anyone who damaged the Wyrm, plus anyone who stood in the arena (within 64 blocks of the centre) for at least 15 seconds. Rewards go straight into your inventory (or to your feet if it is full), so nothing falls into the void.

| Reward | Who gets it |
| --- | --- |
| 12 to 18 **Wyrm Scales** | Every participant |
| 1 **Void Heart**, and a 35% chance of a second | Every participant |
| 10,000 coins and 8,000 XP | Every participant |
| A 5% chance of a Thunderclap scroll and a 2% chance of a Meteor scroll | Rolled separately for every participant |
| 30,000 Combat XP | The player who dealt the most damage |
| 20,000 Combat XP | Every other participant |
| A boss kill for your journal, SkyRPG XP (15 the first time) and the Ender Wyrm quest | Every participant |
| A server-wide announcement | The first time the Wyrm is slain on this server, and a smaller one for each kill |

When it dies, each participant gets a ranking ("#1 ... damage to the Ender Wyrm") with their damage.

{{items:skyrpg_combat:wyrm_scale, skyrpg_combat:void_heart}}

### The gear

Wyrm Scales and Void Hearts are the materials for the best gear in the game, made at the Artisan Bench (Combat 50) by upgrading your best piece of the previous tier. Your enchantments carry over.

| Piece | Cost |
| --- | --- |
| {{item:skyrpg_items:wyrmscale_helmet}} | 1 Starforged Crown, 10 Wyrm Scale, 1 Void Heart |
| {{item:skyrpg_items:wyrmscale_chestplate}} | 1 Starforged Aegis, 16 Wyrm Scale, 2 Void Heart |
| {{item:skyrpg_items:wyrmscale_leggings}} | 1 Starforged Greaves, 14 Wyrm Scale, 1 Void Heart |
| {{item:skyrpg_items:wyrmscale_boots}} | 1 Starforged Treads, 8 Wyrm Scale, 1 Void Heart |
| {{item:skyrpg_items:voidreaver}} | 1 Starfall Blade, 24 Wyrm Scale, 3 Void Heart, 8 Astral Shard |

A full set plus the sword is **72 Wyrm Scales and 8 Void Hearts**, about five fights with a party. See [Armour Sets](armour-sets) and [Weapons](weapons) for the stats and the set bonus.

{{items:skyrpg_items:wyrmscale_helmet, skyrpg_items:wyrmscale_chestplate, skyrpg_items:wyrmscale_leggings, skyrpg_items:wyrmscale_boots, skyrpg_items:voidreaver}}

Wyrm Scales sell for 1,200 coins and Void Hearts for 20,000, but you will want to keep them.

## See also

- [The End](the-end)
- [Armour Sets](armour-sets)
- [Weapons](weapons)
- [Astral Observatory](astral-observatory)
- [Boss List](boss-list)
- [Dungeons Overview](dungeons-overview)
- [Combat Basics](combat-basics)
