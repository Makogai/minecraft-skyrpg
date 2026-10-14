---
title: Blood Moon
category: world
order: 5
icon: skyrpg_world:blood_shard
summary: The red-moon nights that make the Overworld deadlier and richer, with hordes, double drops, Blood Shards and awakened region hearts.
---
Now and then the moon rises red over the Overworld. Everything hostile gets stronger, hordes hunt you, and the bosses at the centre of every [danger region](danger-regions) wake up. It is risky, but kills drop twice as much, and it is the only source of **Blood Shards**, the material for Heart Sigils and the Moonblood Charm.

## When it happens

- At dusk each night (when the day clock reaches 13,000) the game rolls for a Blood Moon in the Overworld. The chance is **12%**.
- It **never happens two nights in a row**, and it is **guaranteed on the twelfth night** without one.
- There must be at least one non-spectator player in the Overworld for it to be rolled.
- It lasts from dusk to dawn, ending at time 23,000. Sleeping through the night ends it too.
- It is saved with the world, so restarting the server does not end it.

At sunset you get a red notice ("The moon rises red...") and a **BLOOD MOON** banner, with a low horn. The sky, the clouds and the fog turn red over about ten seconds, the moon is full, and a **Blood Moon** timer appears on your HUD showing how long is left.

## What changes everywhere in the Overworld

| What | Effect |
| --- | --- |
| Monster levels | **+10** to every hostile mob (on top of everything else) |
| Bloodbound hordes | Extra zombies, skeletons, spiders and creepers hunt you (see below) |
| Elites | **Three times** as likely to appear |
| Drops | **Double**: player kills of hostile mobs roll their loot a second time |
| Blood Shards | A new rare material drops from kills |
| Region hearts | Every heart wakes when you come near |

Farmed mobs and bosses do not roll double loot. Region drops (essences and Dread Crystals) are rolled twice instead.

> [!NOTE]
> The Blood Moon only affects the Overworld. The Nether, the End and the hub are unchanged.

### Bloodbound hordes

On the surface, outside danger regions, a wave spawns around every non-creative player every 5 seconds:

- 2 to 4 monsters at a time, 20 to 40 blocks away: zombies (40%), skeletons (25%), spiders (20%) and creepers (15%).
- Each is named "Bloodbound <mob>" in red and goes straight for you.
- No more than 10 at a time within 56 blocks of you.
- Waves do not spawn if you are deep underground (more than 8 blocks below the surface with no sky above you).

All Bloodbound monsters are removed at dawn, so there is nothing to clean up.

### Blood Shards

| Source | Blood Shards |
| --- | --- |
| Hostile mob killed by a player | 4% chance of 1 |
| Elite | 25% chance of 1 to 2 |
| Dungeon boss or world boss | 2 to 4 |
| Region boss | 3 to 6 in its loot |

A glowing shard drops and "+1 Blood Shard" appears over your hotbar. {{item:skyrpg_world:blood_shard}} sells for 150 coins.

## In a danger region

Inside a region the horde is the **region's own monsters**, not generic zombies:

- Region monsters spawn every 1.5 seconds instead of every 3, with twice the usual cap.
- Region drops are rolled twice.
- Region monsters are Bloodbound too, and vanish at dawn.

### Hearts awaken

The first time you walk into a region during a Blood Moon, you get a title: "**<Boss> stirs**" with the line "The Blood Moon calls it from the heart of <Region>", and a **waypoint** to the heart appears on your screen. When any player comes within **64 blocks** of the heart, the boss wakes at the altar, and everyone within 160 blocks hears about it. Each heart wakes **once per moon**, and the waypoint clears when you get close.

See [Region Hearts](region-hearts) for the bosses.

> [!TIP]
> A Blood Moon is the cheapest way to fight a region boss, because you do not need a Heart Sigil. It is also a way to collect enough Blood Shards to craft sigils for later.

## The Moonblood Charm

The {{item:skyrpg_items:moonblood_charm}} is an Amulet crafted at the Artisan Bench (Combat 12) from **16 Blood Shards**, 8 gold ingots, 8 amethyst shards and 2 crying obsidian. It sells for 2,500 coins.

| When | Bonus (in the Overworld) |
| --- | --- |
| Always | +40 Health, +8 Strength, +10 Crit Damage |
| At night | +12 Strength, +15 Crit Damage |
| During a Blood Moon | +30 Strength, +40 Crit Damage |

The night and Blood Moon bonuses replace one another rather than stacking, and only work in the Overworld.

## The dawn tally

When the moon sets, each player gets a message with their own tally: "The Blood Moon sets. You slew N monsters and found M Blood Shards." If you did not fight, it just says you saw it out.

## For ops

| Command | What it does |
| --- | --- |
| `/bloodmoon start` | Raises a Blood Moon now, lasting until the next dawn. |
| `/bloodmoon stop` | Ends it now. |
| `/bloodmoon status` | Shows whether it is on, time left and hearts woken. When off, it shows nights since the last one and whether the next roll is guaranteed. |

## See also

- [Region Hearts](region-hearts)
- [Danger Regions](danger-regions)
- [The Survival World](survival-world)
- [Mobs](mobs)
- [Combat Basics](combat-basics)
- [Accessories](accessories)
