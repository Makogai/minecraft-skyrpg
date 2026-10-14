# The Wild (Adventure Plan v2, phase 6)

The overworld gets more dangerous and more alive: Blood Moon nights that play out differently in every danger region, a
boss at the heart of each region, two new regions (the Shattered Highlands and the Haunted Moors), and the pack's first
mount, the griffin. Decided 2026-10-13 after Philip asked for "the blood moon events per zone" and "the biomes with
griffins" (design calls are delegated; see `docs/design/progression.md` "Decisions (made)").

Part 1 (this update) is everything below. Part 2 comes later: the six surface biomes (Crystal Fields, Starfall Basin,
Mistwood, Bloomlands, Saltmarsh Coast, Duskwillow Bayou), meteor showers, caravans, roaming bosses, and the Wyrmscale gear
with the Ender Wyrm altar.

## Blood Moon (`world`, package `com.skyrpg.world.bloodmoon`)

- **When:** at dusk in the overworld, about one night in eight (12% a night), never twice in a row, and at least once every
  twelve nights. It needs a player online. It lasts from dusk to dawn. `/bloodmoon start|stop|status` for ops.
- **Warning:** at sunset a red Notice ("The moon rises red...") and a banner, with a low horn sound. At dawn a
  "The Blood Moon sets" Notice with each player's tally.
- **Look:** clients turn the sky, the moon and the fog red (fades in over about 10 seconds), and a HUD widget shows
  "Blood Moon" with the time left. A sync payload carries `active` and `endsAt` (the day time it ends).
- **Everywhere in the overworld:** hostile mobs are +10 levels; more of them (an extra Bloodbound horde of zombies,
  skeletons, spiders and creepers spawns 20-40 blocks from each player in waves, capped per player); elites three times as
  likely; **double drops** from player-killed hostile mobs; and **Blood Shards**, a new rare material (about 4% from
  each kill, more from elites and bosses).
- **Per zone:** inside a danger region the horde is that region's own monsters (twice its usual cap) and the region's
  heart awakens (below). Players in a region during the Blood Moon get a title naming its boss and a waypoint to the heart.
- **Blood Shards** sell for coins and craft the **Moonblood Charm** accessory (+Strength and +Crit Damage at night, more
  during a Blood Moon) at the Artisan Bench.

## Region hearts

Every danger region has a **heart** at its centre: a ruined shrine (the region's palette blocks, an altar with a heart
crystal) generated with the region's land. The heart's boss can be fought two ways:
- **During a Blood Moon** it awakens on its own the first time a player comes within 64 blocks of the heart (once per
  region per Blood Moon).
- **Any other time** with a **Heart Sigil** for that region: crafted from 32 of the region's essence plus 4 Blood Shards,
  used on the altar.

Bosses use vanilla bodies, scaled up and dressed, with their own attacks, a boss bar, minions and a loot table. Each drops
the region's trophy (sells well, used later in region gear) plus essences, Blood Shards and coins. Kills fire
`BossKills.KILLED` with a new `REGION_BOSS` kind, so the Journal and SkyRPG Level count them.

| Region | Boss | Body | Level | Moves | Trophy |
| --- | --- | --- | --- | --- | --- |
| The Blightlands | **The Blight Lord** | giant zombie in blighted plate | +15 over the region | corruption pools, raises Blighted Dead, a pull | Blighted Heart |
| The Ashen Wastes | **The Ash Colossus** | giant charred iron golem | +18 | ground slam, fire geysers, ash cloud (blindness) | Cinder Core |
| Frostgrave | **The Frost Revenant King** | giant stray with a crown | +18 | ice spike rings, freezing volley, summons Frost Revenants | Frozen Crown |
| The Mire | **The Bog Mother** | giant witch | +12 | poison pools, splits Bog slimes, hex (slowness) | Bog Mother's Eye |
| The Haunted Moors | **The Lantern Wraith** | giant vex with a lantern | +16 | blinks, soul-fire lanterns, ghost wave; only Hallowed Edge weapons hit it fully | Wraith Lantern |
| The Shattered Highlands | **The Storm Griffin** | a griffin, scaled 2.5 | +20 | wind dive, gust knockback, lightning feathers | Stormfeather |

## Two new regions

Both are data in `tools/builds/regions.py`, plus code for their special decorations.

- **The Shattered Highlands** (stony, jagged and frozen peaks, windswept hills; ☠☠☠☠, +20): broken stone and gravel, cracked
  calcite and tuff, **floating rock islets** above the slopes, Skyiron ore on the surface. Monsters: Windborn (breezes),
  Cliff Stalkers (fast spiders), Gale Harpies (phantoms that fly by day too). **Griffin nests** on high ledges, with wild
  griffins circling. Drops **Gale Essence**.
- **The Haunted Moors** (plains, sunflower plains, meadows; it takes over plains from the Blightlands, which keeps the
  forests; ☠☠☠, +16): dead grass, coarse dirt and podzol, dead bushes, crooked headstones and open graves, grey fog, and
  will-o'-wisps (soul-fire lights) at night. Monsters: Moor Ghosts (vexes that take 75% less damage unless your weapon has
  Hallowed Edge), Gravecallers, Hollow Wisps, Restless Dead (zombies that rise from graves). Drops **Ectoplasm**.
- **Region fog:** each region type gets a fog colour (the Moors grey, the Blightlands red, the Mire green, Frostgrave white,
  the Ashen Wastes ash grey, the Highlands pale blue), blended in by the client when inside, using the same sky/fog hook
  as the Blood Moon.

## Griffins and mounts (new `mounts` mod)

- **Wild griffins** live around nests in the Shattered Highlands: an eagle's head and wings on a lion's body, in ten
  feather colours in four rarities (weights per colour; each new griffin rolls one, the Storm Griffin boss too). They're
  neutral, and fight back if hit.
  - Common (20 each, 20.4%): Golden, Storm grey, Snow white, Russet (red-brown hawk, cream face, rusty body).
  - Uncommon (6 each, 6.1%): Midnight (near-black body, blue-violet feathers, silver eyes), Verdant (teal-green, mossy olive).
  - Rare (2.5 each, 2.55%): Ember (charcoal body, flame-orange feathers, glowing wing tips, pulsing eyes), Frost (pale
    ice-blue, glowing crystal-blue wing tips).
  - Legendary (0.5 each, 0.51%): Prismatic (pearl white cycling through the rainbow every 6 s, glowing trim), Astral (deep
    navy, glowing twinkling stars, faint constellation lines).
  Rare and legendary wild griffins sparkle so they can be spotted; taming one is announced to the whole server. The whistle
  name and the Mounts page show the rarity (white, green, blue, gold, as for pets).
- **Taming:** feed one raw meat (beef, mutton, chicken, porkchop, rabbit). Each feeding has a 1-in-5 chance, with hearts
  when it works. A tamed griffin turns into a **Griffin Whistle** item that holds it (name, colour, owner, stats).
- **Mounts storage:** whistles go into a mount stable kept on the player, like pet storage, with a **Mounts page** in the
  K menu (summon, put away, take out to trade).
- **Riding:** use the whistle (or the K page) to summon your griffin next to you, right-click to mount. WASD to move, jump
  to take off and climb, sneak to dive, sprint for a faster glide. Flying uses **stamina** (a bar above the hotbar) that
  refills on the ground. Dismounting and walking away puts it back in the whistle after 30 seconds. It can't be hurt by
  falling, takes no damage while ridden in the hub, and isn't allowed in dungeons.
- Mount stats (Speed, Flight Stamina) differ a little per griffin, so better ones are worth taming.

## Order

1. Blood Moon: schedule, client sky/fog and HUD, horde spawns, level bonus, double drops, Blood Shards.
2. Region hearts: shrine generation, the six bosses, Heart Sigils, Blood Moon awakening.
3. The Shattered Highlands and the Haunted Moors, and region fog.
4. The `mounts` mod: the griffin (model, wild spawns at nests, taming, whistle, stable, K page, riding and flight).

## Status (2026-10-13)

Steps 1 to 3 are built and tested on a staging server; step 4 (the `mounts` mod) is built separately.

- **Blood Moon** (`world`, `com.skyrpg.world.bloodmoon`): `BloodMoons` rolls at dusk (12%, never twice in a row, forced on the
  twelfth night without one, only with a non-spectator player online) and keeps its state in the overworld's saved data, so a
  restart doesn't end it. It ends when the overworld clock reaches 23000 (sleeping through the night ends it too).
  `/bloodmoon start|stop|status`. Core's `BloodMoon.active()` carries the flag to other mods: combat's Elites roll three times
  as often (hordes spawn as events, which now count for Elites while a moon is on), and items' Moonblood Charm
  (`MoonbloodCharmSource`: +12 Strength and +15 Crit Damage at night, +30 and +40 under a Blood Moon, on top of its base
  stats) reads it. +10 levels through `MobLevelling.registerBonus`. Bloodbound waves: every 5 seconds per surface player
  outside regions, 2 to 4 zombies, skeletons, spiders and creepers 20 to 40 blocks away, up to 10 near the player; inside a
  region `DangerRegions` spawns the region's own monsters twice as often with twice the cap. Both are tagged
  `skyrpg_bloodbound` and removed at dawn. Drops: player kills of hostile mobs (not farmed ones, not bosses) roll the loot
  table a second time and region drops are rolled twice; Blood Shards drop 4% of the time, 25% from Elites, 2 to 4 from
  dungeon and world bosses. Notice (`Notice.Kind.BLOOD_MOON`), banner and horn at dusk, a per-player tally at dawn
  ("You slew N monsters and found M Blood Shards"). `BloodMoonSync` (active, ticks left) drives the client: `Atmosphere`
  fades the sky, clouds, horizon and fog red over about ten seconds (and makes the moon full) through a mixin on the camera's
  `EnvironmentAttributeProbe`, and `BloodMoonWidget` is the HUD timer.
- **Region hearts**: `HeartShrine` builds the shrine in the region's centre chunks (a round platform, a ruined wall, four
  pillars with lights, an altar with the heart block; per-type blocks in the region data's `shrine`). `RegionBosses` wakes the
  boss; the fights are `DungeonBosses` fights (data in `dungeon_bosses/`, tier `region`, so boss bar, moves, minions, loot
  table, coins and first-clear reward, many at once). New moves for them: `strike` (marked spots that blast, with lightning,
  fire or an effect) and `spikes` (expanding rings that damage and freeze). Loot is the trophy, 14 to 24 essence, 3 to 6
  Blood Shards and a 50% Dread Crystal; kills fire `BossKills.KILLED` with `REGION_BOSS`. Heart Sigils are used within 24
  blocks of the altar. During a Blood Moon each heart wakes once per moon when a player comes within 64 blocks, with a title
  and a waypoint for everyone in the region. The Lantern Wraith and the Moors' ghosts (tag `skyrpg_hallowed_only`) take 25%
  of the damage unless the attacker's main-hand item has Hallowed Edge (read through the `skyrpg_enchants:enchants` component
  by id). The Storm Griffin uses `skyrpg_mounts:griffin` when it exists (tag `skyrpg_storm_griffin`) and a phantom otherwise.
  `/regionboss spawn <type>|awaken|list`, `/region nearest <type>`.
- **Regions**: `shattered_highlands` and `haunted_moors` are in `tools/builds/regions.py` (the Blightlands now keep only
  forests and taiga). New decoration types: `islet` (floating rock), `ore` (surface Skyiron), `headstone`, `grave`. Mob entries
  take `tags`, `speed`, `fireproof` (Gale Harpies fly in daylight) and `rise` (Restless Dead dig out of the ground); Moors get
  will-o'-wisp particles at night. Region fog: the type's `fog` colour and `fog_density` travel in `RegionShape`, and
  `Atmosphere` blends the fog colour and pulls the fog in over about three seconds while you're inside (it fades over the
  last 30 blocks of the edge), through the same hook as the Blood Moon.
- Data and art: `tools/builds/wild.py`, `tools/art/generate_wild.py`. New items: Blood Shard, Gale Essence, Ectoplasm, six
  trophies, six Heart Sigils (world), Moonblood Charm (items).
