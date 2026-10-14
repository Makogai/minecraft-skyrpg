# Slayers, spawners and new mobs

The combat endgame: a Slayer Master in the hub who gives slayer quests (like Maddox on Hypixel) and sells spawners,
unlocked one by one by killing the previous mob (Philip's idea, the way Cosmic does it). Plus new mobs of our own in
the world. Everything is ours (no mob mods): our mobs reuse vanilla AI and models with their own looks, attacks and
drops, and plug into mob levels, nameplates and damage numbers.

## 1. The Slayer Master

**Morwen Ashgrave**, by the hub gate. Talking to her opens her screen, with two tabs:

- **Slayer Quests:** the four slayer lines, your level in each, and the tiers you can start.
- **Spawners:** the spawner ladder, with your kill progress towards the next one.

## 2. Slayer quests

1. Pick a line and a tier and pay the fee.
2. Kill that kind of mob anywhere (real mobs, not ones from spawners). Each kill fills the quest bar by
   1 + level / 5 points, so harder mobs fill it faster.
3. When the bar is full, the slayer boss rises next to you. You have a time limit to beat it (friends may help).
4. Beat it for slayer XP, tokens and a chance at a rare drop. Fail (time runs out, or you die) and the fee is gone.

| Line | Counts | Boss | Its tricks (from tier) |
|---|---|---|---|
| Grave Tyrant | zombies, husks, drowned, zombie villagers (not zombified piglins: no gold-farm shortcut) | a hulking plague zombie | II life drain, III raises zombies, IV plague aura |
| Broodmother | spiders, cave spiders, our crawlers | a giant brood spider | II web shots, III spiderlings, IV venom |
| Bone Marshal | skeletons, strays, bogged, wither skeletons, our revenants | a skeleton general with a bow | II arrow volleys, III raises archers, IV bone shield |
| Void Stalker | endermen, endermites | a void-touched enderman | II blinding hits, III null shield, IV void rifts |

| Tier | Fee | Bar | Boss health | Time | Slayer XP | Needs slayer level |
|---|---|---|---|---|---|---|
| I | 1,000 | 100 | 2,000 | 3 min | 5 | 0 |
| II | 5,000 | 400 | 6,000 | 3 min | 25 | 2 |
| III | 20,000 | 1,200 | 18,000 | 4 min | 100 | 4 |
| IV | 60,000 | 3,000 | 50,000 | 5 min | 500 | 6 |

Bosses above 5,120 health (vanilla's cap) take proportionally less damage instead, and their nameplate shows the real
number.

**Slayer levels** (1 to 9 per line) at 5, 15, 50, 120, 300, 700, 1,500, 3,000 and 6,000 XP. Each level gives a small
stat for good: Grave Tyrant +3 Health, Broodmother +0.5% Crit Chance, Bone Marshal +1 Strength, Void Stalker
+2 Intelligence. Levels also unlock slayer gear at the Artisan Bench (a "Slayer" tab).

**Drops:** each line has a token (Tyrant's Ash, Brood Silk, Marrow Shard, Void Fragment), more per tier, and from
tier III a rare drop (Plague Heart, Venom Gland, Marshal's Sigil, Null Core). Tier IV bosses have a small chance at a
Golden Pet Egg.

**Slayer gear (bench):**

| Line | Accessory (level 3) | Weapon (level 6, needs the rare drop) |
|---|---|---|
| Grave Tyrant | Tyrant's Amulet: Health, life steal | Gravebane: heals on every hit |
| Broodmother | Broodweave Cloak: Crit Chance, dodge | Broodfang: hits poison |
| Bone Marshal | Marshal's Belt: Strength, Defense | Marrowblade: big crits |
| Void Stalker | Voidbound Bracers: Intelligence, Crit Damage | Voidrender: ignores some defense, extra damage to endermen |

**Slayer armour (bench, 2026-10-13):** one 4-piece set per line, the way Hypixel's slayer armour works. Pieces unlock at
slayer level 2 / 4 / 5 / 7 (boots, helm, leggings, chestplate) and are crafted from the line's token plus Valor Seals and
a vanilla filler; the chestplate also takes one rare drop. Recipes are `data/skyrpg_combat/skyrpg/recipes/<set>_<piece>.json`
(`category: slayer`, `requires: {skill: skyrpg_combat:slayer/<line>, level: N}`).

| Set | Line | Look | Token (boots 40 / helm 64 / legs 96 / chest 160) | Filler | Chest rare drop |
|---|---|---|---|---|---|
| Gravewarden | Grave Tyrant | rotting plate, gravestone pauldrons and crest, plague-green glow | Tyrant's Ash | iron ingot 8 / 12 / 16 / 24 | Plague Heart |
| Silkstalker | Broodmother | chitin and silk, spider-leg spikes, venom-green eyes | Brood Silk | string 16 / 24 / 32 / 48 | Venom Gland |
| Marshal's Bonemail | Bone Marshal | bone plate, skull helm with a red crest, banner tassets | Marrow Shard | bone 16 / 24 / 32 / 48 | Marshal's Sigil |
| Voidwalker | Void Stalker | obsidian-purple robes, violet seams, floating void shards (end-game look) | Void Fragment | obsidian 4 / 6 / 8 / 12 | Null Core |

Valor Seals are 4 / 6 / 8 / 12 (boots / helm / legs / chest). Stats are about a notch under the slayer swords: chest 56-84
Health, 40-70 Defense (epic pieces, legendary chestplate) plus the line's flavour: Gravewarden Health, Defense and
Strength; Silkstalker Crit Chance, Crit Damage and Speed; Bonemail Strength and Defense; Voidwalker Intelligence and Crit
Damage. **Set bonus** (all four pieces, also a little stats: Gravewarden +40 Health +20 Defense, Silkstalker +5% Crit
Chance +25% Crit Damage, Bonemail +30 Strength +20 Defense, Voidwalker +60 Intelligence +30% Crit Damage):

- `skyrpg_combat:ward/<line>` 0.3: take 30% less damage from that line's mobs and its boss (anything whose direct or
  owning attacker the line counts, or the boss entity; arrows count through their shooter). Applied before Defense.
- `skyrpg_combat:bane/<line>` 0.1: +10% damage to them (the same effect the swords use, so they stack multiplicatively).
- `skyrpg_combat:slayer_xp/<line>` 0.25: +25% slayer XP when you beat that line's boss (not from `/slayer xp`).

The effects live in `SlayerGear.java` next to `bane`. Voidwalker also has a slow orbit of void shards, purely visual (the
`aura_voidwalker` VFX effect, bound to the full set like the other armour auras). Art: worn layers and icons in
`generate_items.py`, raised 3D detail in `generate_armor_depth.py` (glowing plague-green, venom-green, ember-red and
violet parts).

## 3. Spawners (Philip's ladder)

Morwen sells spawners for coins, but each one unlocks only after you've killed enough of the mob before it on the
ladder (your lifetime kills count, from the game's own statistics):

Rabbit -> Chicken -> Pig -> Sheep -> Cow -> Zombie -> Spider -> Skeleton -> Enderman -> Blaze

Each step needs 5,000 kills of the previous mob (Philip's number); prices rise from 5,000 to 400,000 coins. The ladder
is one data file (`data/skyrpg_combat/skyrpg/spawners/ladder.json`), so steps, counts and prices are easy to change.
The ladder is in order: a spawner also needs the one before it unlocked, so 5,000 zombie kills from normal play don't skip
you past chickens.

- A bought spawner places like any block and spins up that mob. Breaking a spawner you placed gives it back.
  Spawners found in dungeons stay vanilla (they drop nothing).
- **Mobs from spawners are "farmed":** they drop their normal loot (sell it to the masters) and count for the ladder,
  but give only a quarter of the Combat XP, no Valor Marks, no rare drops, never become Elites, and don't count for
  slayer quests. That keeps slayers and the economy from being farmed away by an AFK spawner.

## 3.1 As built

- **Bosses** are our own entity types on vanilla bodies (`skyrpg_combat:grave_tyrant`, `broodmother`, `bone_marshal`,
  `void_stalker`) with their own textures. They hunt their owner, follow them (a boss more than 32 blocks away, or one
  that hasn't landed a hit in 15 seconds, comes to them), and leave if the owner logs off, changes dimension, goes to
  the hub or is in spectator; they come back with the time that was left.
- **Damage:** the tier's `damage` multiplies the body's vanilla attack (Bone Marshal: its arrows). Tier I-IV:
  Grave Tyrant x2.5 / 4 / 6.5 / 10, Broodmother x3 / 5 / 8 / 12, Bone Marshal x2.5 / 4 / 6 / 9, Void Stalker
  x1.5 / 2.2 / 3.2 / 4.5 (an enderman already hits for 7). Plague aura and void rifts hurt by a share of your max health
  (4% a second, 25% a rift), so they matter at any gear level.
- **Rewards:** tokens 1-2 / 3-5 / 8-12 / 20-30; rare drop 2% at III, 6% at IV; Golden Pet Egg 0.4% at IV (announced to
  everyone); Combat XP a twentieth of the boss's health. Magic Find raises the rare and egg chances. The level-10+
  rewards for any levelled mob (Valor Marks, fangs, cores) apply on top.
- **Gear:**

  | Item | Stats | Effects |
  |---|---|---|
  | Tyrant's Amulet | +50 Health, +10 Defense | 2% life steal |
  | Broodweave Cloak | +4% Crit Chance, +5 Speed | 5% dodge |
  | Marshal's Belt | +20 Strength, +20 Defense | |
  | Voidbound Bracers | +40 Intelligence, +20% Crit Damage | |
  | Gravebane | 120 Damage, 70 Strength, 60 Health, 40% Crit Damage | 5% life steal, +50% vs its line |
  | Broodfang | 115 Damage, 65 Strength, 8% Crit Chance, 45% Crit Damage | hits poison 3 s, +50% vs its line |
  | Marrowblade | 125 Damage, 80 Strength, 5% Crit Chance, 90% Crit Damage | +50% vs its line |
  | Voidrender | 120 Damage, 60 Strength, 60 Intelligence, 50% Crit Damage | +75% vs its line |

  The "vs its line" effect is `skyrpg_combat:bane/<line>` (the line's mobs and its boss); poison is
  `skyrpg_combat:venom`. Spiders and the undead shrug off poison, as in vanilla.
- **Testing:** `/slayer start`, `/slayer fill` and `/slayer xp` (see the README).

**Boss health raised (2026-10-12):** Drogashh two-shot a tier I boss with mid gear and a combat pet (Strength 193, Crit
Damage 145%), so health went from 600 / 2,500 / 8,000 / 25,000 to 2,000 / 6,000 / 18,000 / 50,000.

**Signature moves (2026-10-12, Philip: "not just tanky mobs"):** see `BossMoves.java`. Every move is announced (action bar)
and marked on the ground before it lands. Grave Tyrant: Grave Slam every 12 s, Enrage below half (+35% speed, +25% damage),
IV gravestones on three marked circles. Broodmother: Pounce every 9 s from 4-18 blocks, web shots from I, II+ egg sacs at
two-thirds and one-third health (3-4 sacs, 2 hits each, hatch two spiderlings each after 6 s). Bone Marshal: volleys from I
(3 arrows, 5 from II), II+ Arrow Rain every 14 s on a circle under you, III+ War Banner below half (half damage taken and
1% healing a second until it's torn down: 3 hits, 5 at IV). Void Stalker: Void Bolts every 8 s (6-14% health and
Darkness), null shield from II, III+ Void Beacons every 20 s (3-4 hits, or half your health to everyone within 8 blocks).
Things to break are an item display plus an invisible interaction hitbox; hits count through Fabric's attack callback.

**Tier forms and abilities (2026-10-13, Philip and Drogashh: "each tier needs unique attack/defence skills", "new
models, clear progression from weaker T1 to strong T4"):** every boss has its own 3D model (`*Model`, `*BossRenderer`,
generated `*Layout` from `tools/art/generate_tyrant.py`, `generate_broodmother.py`, `generate_bone_marshal.py`,
`generate_void_stalker.py`) with parts and textures per tier. Their size grows with the tier (scale x0.88 + 0.12 per tier),
and synced flags drive the poses (slam, pounce, cocoon, brood shield, charged draw, bone shield, channel, phase).
`SlayerAbilities.java` holds the catalogue the menu shows and every tier's added attack and defence (table in the README).
Moved to make room: Raise the Dead and Enrage from I/III to II, Gravestones from IV to III, Pounce from I to II, egg sacs
from II to III, and the Bone Marshal's archers into the IV Legion.

## 4. New mobs (built)

Our own mobs, each a new kind built on a vanilla body with its own look, attacks and drops:

| Mob | Where | Body | What it does | Drops |
|---|---|---|---|---|
| Glimmer Crawler | caves below y 0 | spider | glowing crystal back, fast | Glimmer Shards, rare Prism Fragment |
| Bog Hag | swamps, at night | witch | faster potions, calls bog slimes | Hag's Charm, Duskwillow sapling |
| Frost Revenant | snowy biomes | stray | freezing arrows | Rime Shard |
| Ember Imp | badlands, near lava, Nether | small blaze | quick fireballs | Ember Core, Emberwood sapling |
| Grove Sentinel | forests at night | zombie | slow, tanky, roots you | Heartwood, rare Golden Acorn |
| Tide Lurker | rivers and beaches at night | drowned | pulls you towards the water | Tidescales |
| Hollow Wisp | plains and meadows at night | vex | flits through walls | Wisp Essence |
| Gravecaller | dark forests at night | zombie | raises the dead | Tyrant's Ash |

They count for the matching slayer line: crawlers for the Broodmother, revenants for the Bone Marshal, Tide Lurkers
and Gravecallers for the Grave Tyrant, wisps for the Void Stalker. Bog Hags, Ember Imps and Grove Sentinels count for
none.

**As built:** spawn weights next to vanilla's monsters (zombies 95): Glimmer Crawler 40 (overworld, kept below y 0 by
its spawn rule), Bog Hag 25 (swamp, mangrove swamp), Frost Revenant 40 (`#c:is_snowy`), Ember Imp 30 (badlands) and 10
(Nether), Grove Sentinel 30 (forests but not dark forests), Tide Lurker 35 (rivers, beaches; on the banks, not in the
water), Hollow Wisp 15 (plains, sunflower plains, meadow), Gravecaller 35 (dark forest, pale garden). All use the
monster darkness rule, so the surface ones come out at night. Frost Revenants, Tide Lurkers, Gravecallers and wisps burn
at dawn; the Grove Sentinel is wood and doesn't. Tricks: Bog Hag two slimes every 15 s while fighting (four at most);
Frost Revenant arrows add 6 s of freezing; Grove Sentinel hits give Slowness IV for 2 s; Tide Lurker hauls a player
4-12 blocks away towards it every 6 s; Gravecaller two zombies every 12 s (four at most). The new materials (Hag's Charm,
Rime Shard, Ember Core, Wisp Essence) sell to the masters for now; recipes can use them later.

## The Slayers page (K menu)

A "Slayers" page (combat client, order 12, next to Skills): a card per line (level, XP bar, boss kills per tier, running
quest); click one for the level track (stat per level and the bench recipes each level unlocks, read from
`skyrpg/recipes` entries that require `skyrpg_combat:slayer/<line>`, so new slayer gear appears on its own), the tiers
(boss, cost, rewards, attacks and defences) and your stats. `SlayerState` also tracks kills and fastest kill time per
line and tier (`line:tier`) and boss tokens per line; the page's static data comes in `SlayerPayloads.PageData`
(requested when the page opens), your own numbers from the synced state.
