# The Deep: underground layers and the Dwarven Halls (Adventure Plan v2, phase 3)

Mining becomes exploring: the deeper you dig, the stranger the caves get. Four layers, each with its own look, monsters,
level bonus and drops, and a big new dungeon (the Dwarven Halls) in the third one. Decided 2026-10-13 (the user
delegates design calls; see `docs/design/progression.md` "Decisions (made)").

## Layers

Overworld caves only. `DeepLayerFeature` (world module, `underground_decoration` step, every overworld biome) restyles
**cave surfaces** (solid blocks next to cave air) inside each layer's height band as chunks generate. A 3D noise keeps it
patchy (about 60% of cave surfaces in the band change), and the band edges blend over 6 blocks, so a layer feels like a
region you walk into rather than a stripe. Vanilla cave biomes (lush caves, dripstone caves, deep dark) are left alone so
ancient cities and lush caves still show up. Chunks generated before the update keep their look, but the layer's monsters,
levels and drops still apply.

`DeepLayers` (world module) is the server side, built like `DangerRegions`: arrival title, monster level bonus (core's
`MobLevelling` bonus hook), layer spawns near survival players in dark cave air, drops on player kills, and ambient
particles. Data: `data/<ns>/skyrpg/deep_layers/<id>.json`, written by `tools/builds/deep_layers.py`.

| Layer | Height | Danger | Levels | Surfaces and decoration | Monsters | Drop |
| --- | --- | --- | --- | --- | --- | --- |
| **Glimmer Caverns** | y 0 to 40 | ☠ | +4 | Glimmerstone patches, Glimmer Clusters on floors and ceilings (they glow), calcite veins, glow lichen | Glimmer Crawlers, Lost Miners (zombies with picks and helmets), cave spiders | Glimmer Geode (rare; right-click to crack: Glimmer, sometimes a Prism Fragment) |
| **Fungal Depths** | y -20 to 0 | ☠☠ | +7 | Mycelium and moss floors, Glowcaps, giant glowcap mushrooms, spore blossoms, **Spore Puffballs** (burst into a poison cloud when you walk near, regrow later) | **Sporelings** (new; burst into spores when killed), Spore Shamblers (mushroom-capped zombies), cave spiders | Glowcap Spores |
| **Dwarven Halls** | y -40 to -20 | ☠☠☠ | +10 | Deepslate brick and polished patches, broken pillars, chains and lanterns, glowing Dwarven Runestones | **Dwarven Constructs** (new), Lost Miners, skeletons | Dwarven Cog |
| **Magma Rifts** | y -64 to -40 | ☠☠☠☠ | +14 | Basalt, blackstone and magma surfaces, lava pools on floors, more exposed Emberite | **Fire Beetles** (new), magma cubes, blazes (rare) | Rift Ember |

### How the layers work (as built)

- **Bands:** `min_y` counts, `max_y` doesn't (Glimmer Caverns is y 0 to 39). In world generation each band's edge wobbles
  about 3 blocks either way (a smooth 3D noise plus a block of dither), so neighbouring layers bleed into each other.
- **What changes:** only stone, deepslate, tuff, granite, diorite, andesite, dirt and gravel that touch cave air (the
  `replace` list), and nothing within 3 blocks of the ground's surface (so open ravines and low valleys stay vanilla).
  Water-filled caves stay vanilla too (only air counts). The palette picks a block per few-block cell, so materials
  come in patches; floors, walls and ceilings have their own palettes (mycelium only on floors, bricks on walls).
- **Decorations** are scattered by density (per 100 surfaces of a kind), so big caves get more and solid rock gets
  none. They are skipped in chunks a structure other than a mineshaft reaches into (dungeons, strongholds, villages),
  so nothing grows inside the Dwarven Halls or a Mine Camp.
  - Glimmer Caverns: Glimmer Clusters on floors, ceilings and walls, glow lichen.
  - Fungal Depths: Glowcaps, Spore Puffballs, moss carpet, giant glowcaps (a mushroom stem under a dome of Glowcap
    Blocks; only in caves big enough), spore blossoms and hanging roots on ceilings.
  - Dwarven Halls: broken polished-deepslate pillars with a chiselled top, lanterns on chains, Dwarven Runestones and
    Duskquartz ore set into walls.
  - Magma Rifts: lava pools sunk into the floor where solid ground holds them in (ringed with magma), basalt spikes up
    from floors and down from ceilings, Emberite ore set into walls.
- **In a layer** (titles, spawns, levels, drops) means: the overworld, inside the band, and out of the sky's sight. The
  layer only changes once you are 2 blocks past its edge, and the same layer's title doesn't show again for a minute.
- **Monsters:** Glimmer Crawlers are named silverfish; Lost Miners are zombies with iron or stone pickaxes and iron or
  leather helmets; Spore Shamblers are zombies wearing a Glowcap Block. Every 3 seconds, near each survival player in
  a layer, one group spawns 10-24 blocks away on solid ground in dark air (block light at most 7-9, per layer), up to
  6-8 layer monsters within 40 blocks. They aren't persistent. Monster ids that don't exist yet (Sporelings, Dwarven
  Constructs, Fire Beetles) are skipped with one warning in the log.
- **Levels:** `level_bonus` adds to every monster that spawns in the layer, on top of the usual depth levels (natural
  spawns, layer spawns, spawners). Mobs spawned while chunks generate count by height alone.
- **Drops** (monsters killed by a player in the layer): Glimmer Geode 3% (glowing), Glowcap Spores 30% (1-2), Dwarven
  Cog 25%, Rift Ember 25% (1-2).
- **Ambience:** end-rod glints, drifting spores, white ash, lava sparks.

### The layer blocks and items (`skyrpg_world`)

| Id | What it is |
| --- | --- |
| `glimmerstone` | Stone with glowing Glimmer flecks (light 5). Drops itself; 2 Mining XP and the stone Glimmer Shard chance. |
| `glimmer_cluster` | Amethyst-like cluster on any face (light 10). Mined with a pickaxe it breaks into 2-4 Glimmer Shards (Silk Touch keeps it); 3 Mining XP. |
| `glowcap` | Small glowing mushroom (light 9); grows on any solid floor. |
| `glowcap_block` | Giant glowcap cap (light 13). Drops 0-2 Glowcaps (Silk Touch keeps it). |
| `spore_puffball` | When a survival player comes within about 2 blocks it bursts: a spore cloud and Poison II for 4 s to players within 2.75 blocks. It goes flat (`burst=true`, drops nothing) and regrows after 30 s. |
| `dwarven_runestone` | Deepslate bricks with a glowing rune (light 7). Drops itself; 4 Mining XP. |
| `glimmer_geode` | Right-click to crack: 3-8 Glimmer Shards and a 10% chance of a Prism Fragment. Sells for 150. |
| `glowcap_spores`, `dwarven_cog`, `rift_ember` | Layer materials; sell for 45, 70 and 90. |
| `thane_key` | Thane's Key, the Dwarven Halls vault key (like the Crypt Key). |
| `thanes_rune` | Thane's Rune, the Iron Thane's trophy (epic; sells for 2,200). |

`/layer` tells anyone which layer they're in; `/layer spawn` (ops) spawns one of the layer's groups nearby right away.

## Structures

- **Abandoned Mine Camp** (Glimmer Caverns, common): a small camp in a cave, with a tent, rails and a minecart,
  lanterns and a loot chest (Skyiron, Glimmer, torches, a treasure map to the Dwarven Halls sometimes). Lost Miners guard it.
- **The Dwarven Halls** (rare, found under mountains, hills and forests): a ruined underground city on two floors,
  reached from a ruined **Dwarven Gate** on the surface by a long stairwell.
  - **Upper Halls:** the great hall with pillars, barracks, a rail station, forge rooms (lava channels and anvils), and
    vaults whose walls are Duskquartz ore. The champion is the **Forgeguard**, a big construct that drops the Thane's Key.
  - **The Deep Forge:** the boss floor, with the **Iron Thane**, a crowned dwarf-king construct with a forge hammer.
    Its phases: hammer slams, then calling constructs, then a molten floor.
  - It has per-player chests, a Reward Chest and Delving XP like every v2 dungeon. Its trophy is the **Thane's Rune**.
- **Catacombs entrances** in the Magma Rifts come with phase 5.

## New monsters (combat module, own 3D models)

| Monster | Where | Look | Trick |
| --- | --- | --- | --- |
| Sporeling | Fungal Depths | knee-high mushroom creature with a glowing cap | walks in groups; killing it bursts a poison spore cloud |
| Dwarven Construct | Dwarven Halls | stone-and-brass automaton, glowing rune core | slow and armoured (arrows do half), heavy knockback punch |
| Fire Beetle | Magma Rifts | big six-legged beetle, magma-cracked shell that glows | fire-immune; charges and sets you alight |
| Iron Thane (boss) | Dwarven Halls | huge crowned construct with a forge hammer, beard of iron | the dungeon boss (moves from its boss JSON; the entity gives it a model and a slam pose) |

## Gear

- **Runeforged armour** (the Dwarven set, 3D detail like every set): Dwarven Cogs, Duskquartz and a Thane's Rune at
  the Artisan Bench (Delving 20).
- **Thane's Hammer**: a heavy weapon crafted from Thane's Runes and Rift Embers.
- Glowcap Spores, Rift Embers and Glimmer Geodes feed existing recipes and sell to the Mining Master.

## Later (not in this update)

The Mycelid trader camp in the Fungal Depths, the Catacombs (phase 5), and showing the layer on the minimap.
