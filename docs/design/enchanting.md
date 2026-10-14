# Enchanting (as built)

The `enchants` mod (`skyrpg_enchants`). Our own enchants, stored on items as a component (`skyrpg_enchants:enchants`),
bound at **Seraphine Vael** in the hub or at any **enchanting table**, found as **tomes**, levelled by the **Enchanting**
skill, collected in the **Codex**. The vanilla table's own enchanting is replaced; vanilla enchanted books from loot and
the anvil still work as before.

## 1. Where

- **Seraphine Vael** (the Enchanter's tower in the hub, action `open_enchanting`): her library counts as 15 bookshelves.
- **Any enchanting table**: right-click opens the same screen. The bookshelves around it (vanilla's rule: two blocks
  out, air between) are its **power**, 0-15. Levels need power: I 0, II 4, III 8, IV 12, V 15 (`rules.json` `shelves`).
  You must stay within 8 blocks of the table (12 of where you opened Seraphine's screen).

## 2. The screen (Arcane Enchanting)

A violet window with a shimmering title, twinkling stars and an **altar**: two rune rings turning against each other,
runes of the enchanting table's alphabet circling the item, motes rising off the ring (thicker while you hover a bind
button) and a burst of light when something is bound (gold for the table, cyan for tomes, pink for merges, violet for
salvage). Three tabs:

- **Enchant**: your enchantable gear on the left (worn armour first, then the held item, hotbar, bag, offhand), the item
  on the altar with what it already holds, and the enchants the table offers for it on the right (commonest first).
  Choose one, then a level: owned levels are ticked, locked ones say why (bookshelves or Enchanting level). The cost
  line goes red where you're short. **Bind** pays and enchants.
- **Tomes**: your tomes on the left (a strip in each one's rarity colour). For the chosen tome: **Combine** with an
  identical one, **Salvage** into Runic Dust (click twice), or **Apply** to any item you carry that takes it (each row
  shows the change, e.g. "II -> Keen III", and the cost).
- **Codex**: every enchant there is, filtered by All / Weapons / Bows / Armor / Tools / Fishing / Ultimate, with what it
  does at its highest level and where it comes from; hover for every level and conflicts. Tome-only enchants nobody has
  shown you yet are written in runes ("???").

Replacing something (a conflicting enchant, or another ultimate) needs a second click. The footer shows coins, XP levels,
Lapis, Runic Dust and your Enchanting level; the server's answer slides up as a toast.

## 3. Rules

- An enchant goes on items of its kind (`applies_to`): swords (`#minecraft:swords`), bows and crossbows, armour by
  slot, pickaxes, axes, hoes, shovels, "tool" (any of those), fishing rods, wands (`#skyrpg_enchants:enchantable/wand`).
  Any kind can take more items through the item tag `skyrpg_enchants:enchantable/<kind>`. Stacks never do.
- Armour enchants count in their own slot; everything else counts while held in the main hand.
- The table offers levels up to `table_max` (V at most), each for its own price (not the difference). Higher levels
  (up to `max_level`, often VII) only come from tomes.
- Conflicts: Keen, Hallowed Edge and Webcutter exclude each other. An item holds one **ultimate**.
- Tomes: applying a tome gives its level if that's higher; the same level as the item's raises it by one (up to the
  max). Two identical tomes combine into one a level higher. Salvaging gives Runic Dust.
- Every enchanted item glints (unless its data turns the glint off).

### Costs (`data/skyrpg_enchants/skyrpg/enchanting/rules.json`)

| Rarity | Coins I-V | XP levels I-V | Enchanting needed I-V | Materials |
|---|---|---|---|---|
| Common | 250 / 800 / 2k / 4.5k / 9k | 2 / 4 / 7 / 10 / 14 | 0 / 0 / 2 / 4 / 6 | Lapis 2-16 |
| Uncommon | 500 / 1.6k / 4k / 9k / 18k | 3 / 6 / 9 / 13 / 18 | 1 / 3 / 5 / 8 / 11 | Lapis 4-24 |
| Rare | 1.5k / 4.5k / 10k / 22k / 40k | 5 / 9 / 13 / 18 / 24 | 4 / 7 / 10 / 13 / 16 | Lapis 8-32, Runic Dust from III |
| Epic | 5k / 15k / 35k / 70k / 120k | 8 / 12 / 17 / 23 / 30 | 10 / 13 / 16 / 19 / 22 | Lapis 12-48, Runic Dust 2-32 |
| Legendary | 15k / 40k / 90k / 160k / 300k | 12 / 17 / 23 / 30 / 40 | 16 / 20 / 24 / 28 / 32 | Lapis, Runic Dust 4-64, Prism Fragments from III |

Reseeding has its own price (16 Dewdrops among it), as `progression.md` planned. Applying a tome costs coins (300
common to 25,000 ultimate, times the level it ends at) and 3 XP levels per level. Combining costs coins (150 to 10,000,
times the new level squared) and, from uncommon, Runic Dust. Salvaging gives 1 / 2 / 4 / 8 / 16 / 40 dust by rarity,
doubled for each level above I. Runic Dust sells for 80.

## 4. Enchants

| Enchant | Rarity | Table (max) | At its highest level |
|---|---|---|---|
| **Swords** | | | |
| Hallowed Edge | Common | V (tomes VII) | +80% damage to the undead: zombies, skeletons and their kin. |
| Keen | Common | V (tomes VII) | +40% melee damage. |
| Scavenger | Common | V | Kills pay 2 coins for every heart of the mob's health. |
| Webcutter | Common | V (tomes VII) | +80% damage to spiders and other crawling things. |
| Ambush | Uncommon | IV (tomes V) | +80% damage to mobs that haven't been hurt yet. |
| Battle Lore | Uncommon | IV (tomes V) | +16 Combat Insight while held: more Combat XP. |
| Cleave | Uncommon | V (tomes VI) | Your hits also strike enemies within 5 blocks for 30% of the damage. |
| Emberbrand | Uncommon | II (tomes III) | Sets what you hit on fire for 8 seconds. |
| Magnetism | Uncommon | I | Drops and experience from your kills fly straight into your bag. |
| Plunder | Uncommon | IV (tomes V) | +12 Magic Find while held: better odds for rare drops. |
| Precision | Uncommon | V (tomes VI) | +50 Crit Damage while held. |
| Bloodthirst | Rare | IV (tomes V) | Heals you for 6% of the melee damage you deal. |
| Frostbite | Rare | III (tomes IV) | Your hits freeze: Slowness II and creeping frost for 5 seconds. |
| Venom Coat | Rare | II (tomes III) | Your hits poison (Poison II) for 6 seconds. |
| Momentum | Epic | III (tomes V) | Each hit within 3 seconds of the last adds 6% damage, stacking up to 5 times. |
| Reaper's Mark | Epic | IV (tomes V) | +1% damage for every 1% of health your target is missing. |
| Soul Siphon | Epic | tome, up to IV | Each kill heals you for 10% of your max health. |
| Stormcaller | Epic | tome, up to V | Every third hit on the same foe calls down lightning for 80% more damage. |
| Titanbane | Legendary | III (tomes V) | +45% damage to anything with more max health than you. |
| Rift Echo | Ultimate | tome, up to V | Every fifth hit tears the air: a moment later the target is struck again for 80% of it. |
| Soulrend | Ultimate | tome, up to V | Each kill binds a soul to you for 20 seconds (up to 10); every soul adds 3% damage. |
| **Bows** | | | |
| Deadeye | Common | V (tomes VII) | +60% arrow damage. |
| Cinder Arrows | Uncommon | I | Your arrows burn. |
| Longshot | Uncommon | IV (tomes V) | Arrows deal 3% more damage for every block they flew past 5 (up to 25 more). |
| Quiversong | Uncommon | III | 45% chance that a shot costs no arrow. |
| Splitshot | Rare | II (tomes III) | 40% chance to loose two more arrows beside each shot. |
| Starshot | Ultimate | tome, up to V | Every fourth arrow becomes a falling star, bursting for 150% of its damage on everything within 4 blocks. |
| **Helmets** | | | |
| Nightsight | Uncommon | I | You see in the dark. |
| Scholar's Focus | Rare | IV (tomes V) | +6 Insight in every skill: more XP from all of them. |
| Tideborn | Rare | I | Water Breathing and Dolphin's Grace while in water. |
| **Chestplates** | | | |
| Ember Ward | Epic | I | Fire and lava can't hurt you. |
| Phoenix Ember | Ultimate | tome, up to V | Once every 180 seconds, a killing blow sets you alight instead: you rise again with 40% health. |
| **Leggings** | | | |
| Steadfast | Uncommon | III | +30% knockback resistance. |
| Evasion | Rare | IV (tomes V) | 5% chance to slip past a melee hit entirely. |
| **Boots** | | | |
| Featherstep | Common | IV | Falls 12 blocks longer than usual don't hurt. |
| Fleetfoot | Uncommon | IV (tomes V) | +16 Speed. |
| Gentle Step | Uncommon | I | Farmland doesn't trample under your feet. |
| **Any armour piece** | | | |
| Aegis | Common | V (tomes VII) | +32 Defense. |
| Vitality | Common | V (tomes VII) | +40 Health. |
| Arcane Weave | Uncommon | IV (tomes V) | +28 Intelligence. |
| Brambleguard | Uncommon | III (tomes IV) | Hands back 12% of the melee damage you take. |
| Deflection | Uncommon | IV | Take 12% less damage from arrows and other projectiles (all pieces add up, to 60% at most). |
| Rejuvenate | Rare | IV (tomes V) | Heals 1.2% of your max health every 3 seconds. |
| Final Stand | Ultimate | tome, up to V | Below 40% health, take 12% less damage (all pieces add up, to 60% at most). |
| **Pickaxes** | | | |
| Prosperity | Common | V (tomes VII) | +50 Mining Fortune while held. |
| Glimmering | Uncommon | V | +20% Glimmer Shards. |
| Forgefire | Rare | I | What you mine comes out smelted. |
| Rich Vein | Rare | IV (tomes V) | 6% chance that an ore drops everything twice. |
| Veinseeker | Rare | IV (tomes V) | 16% chance that mining an ore also mines a touching ore of its kind. |
| Gemcutter | Epic | III (tomes V) | Rare finds 55% more likely while mining. |
| Motherlode | Ultimate | tome, up to V | While mining, a 0.8% chance per ore to strike a Motherlode: +200 Mining Fortune for 15 seconds. |
| **Axes** | | | |
| Timberlore | Common | V (tomes VII) | +50 Foraging Fortune while held. |
| Splintering | Uncommon | V | +25% Heartwood Splinters. |
| Sylvan Grace | Rare | I | Treefeller replants the sapling and clears the leaves at once. |
| Twinbark | Rare | IV (tomes V) | 10% chance that a chopped log drops twice. |
| Woodland Luck | Epic | III (tomes V) | Rare finds 55% more likely while chopping. |
| Heartwood Surge | Ultimate | tome, up to V | While chopping, a 0.8% chance per log for a Heartwood Surge: +200 Foraging Fortune for 15 seconds. |
| **Hoes** | | | |
| Green Thumb | Common | V (tomes VII) | +50 Farming Fortune while held. |
| Dewcatcher | Uncommon | V | +10% Dewdrop chance. |
| Reseeding | Rare | I | Harvesting a grown crop replants it. |
| Twin Harvest | Rare | IV (tomes V) | 10% chance that a harvested crop drops everything twice. |
| Wide Swath | Epic | I (tomes II) | Harvesting a crop also reaps the grown crops up to 2 blocks around it. |
| Bountiful | Ultimate | tome, up to V | While farming, a 0.7% chance per crop for a Bountiful harvest: +200 Farming Fortune for 15 seconds. |
| **Any tool** | | | |
| Swiftcut | Common | V (tomes VII) | +80 Mining Speed while held. |
| **Fishing rods** | | | |
| Angler's Fortune | Common | V | +40 Fishing Fortune while held. |
| Quickbite | Common | V (tomes VII) | +40 Fishing Speed while held. |
| Deepcall | Uncommon | IV (tomes V) | +5% Sea Creature Chance while held. |
| Twin Catch | Rare | IV (tomes V) | 10% chance that a catch comes up twice. |
| Treasure Sense | Epic | III (tomes V) | Rare finds 55% more likely while fishing. |
| Leviathan's Favor | Ultimate | tome, up to V | Each catch has a 8% chance to win the Leviathan's favour: +20% Sea Creature Chance for 30 seconds. |
| **Wands** | | | |
| Spellweave | Uncommon | V | +55 Intelligence while held. |

What they do: stats (`"stats"`) count as the "Enchants" stat source; gear effects (`"effects"`) switch on other mods'
effects (life steal, thorns, dodge, reseed, auto-smelt, double drops, rare finds...), so most enchants are pure data.
The rest are coded in `EnchantEffects` and read their numbers from the data's `values`: Keen and its kin, Ambush,
Reaper's Mark, Titanbane, Momentum and Soulrend multiply melee hits; Deadeye and Longshot arrows (the bow is remembered on
the arrow); Cleave, Frostbite, Stormcaller (every third hit on one foe) and Rift Echo (every fifth hit, again 0.5 s
later) act on hits; Scavenger, Soul Siphon, Soulrend souls and Magnetism on kills; Splitshot, Quiversong, Cinder Arrows
and Starshot (every fourth arrow) when an arrow leaves the bow; Final Stand and Deflection cut damage you take,
Rejuvenate heals, Steadfast adds knockback resistance, Phoenix Ember cheats death (not the void or /kill). The tool
ultimates (Motherlode, Heartwood Surge, Bountiful, Leviathan's Favor) roll on each XP gain of their skill for a 10-30 s
boost of a stat, at most once every 30 s. Extra hits are magic damage from you and never set off enchants again.

## 5. Tomes: where they come from

- **Any hostile mob you kill**: 0.5% (a little more for tougher mobs, up to 2%), times Magic Find and Tome Hunter, for a
  table enchant of a combat kind, levels I/II/III weighted 70/25/5, rarities common 60 / uncommon 30 / rare 9 / epic 1.
  Spawner mobs never drop them.
- **Gathering** (each XP gain): mining 0.06% (pickaxe and tool enchants), farming 0.04% (hoe, tool, boots), foraging
  0.06% (axe, tool), fishing 1% per catch (rod, helmet). Very rarely the skill's ultimate (mining and foraging 0.003%,
  farming 0.002%, fishing 0.06% per catch).
- **Bosses** (`data/skyrpg_enchants/skyrpg/tome_drops/*.json`, by entity id or tag, with a slayer tier floor):

  | Boss | Drops (chance, from tier) |
  |---|---|
  | Grave Tyrant | Hallowed Edge III (25%, I), V (10%, III); Soul Siphon I (6%, II), II (5%, IV); **Soulrend** (1.5% III, +3% IV) |
  | Broodmother | Webcutter III (25%, I), V (10%, III); Venom Coat II (8%, II); **Final Stand** (1.5% III, +3% IV) |
  | Bone Marshal | Deadeye III (25%, I), V (10%, III); Splitshot II (8%, II); **Starshot** (1.5% III, +3% IV) |
  | Void Stalker | Keen III (25%, I), V (10%, III); Stormcaller I (5%, II), II (4%, IV); **Rift Echo** (1.5% III, +3% IV) |
  | World boss | Stormcaller I (15%), Momentum II (15%), Titanbane III (10%), **Phoenix Ember** (5%) |

  Slayer bosses carry a `skyrpg_slayer_boss_tier_<n>` tag for this. Epic and better finds are announced to everyone.

## 6. The Enchanting skill and the Codex

- **Enchanting** (`skyrpg_skills:enchanting`, data in the enchants mod): 50 levels on the fishing curve, +1 Intelligence
  and +0.2 Magic Find each. XP: 20 per XP level spent and 8 per 1,000 coins at the table, 60 per level of a tome
  applied, 80 per level combined, 10 per level salvaged, and a big bonus for each first discovery (150 common to 5,000
  ultimate). Talents: Arcane Mind (+4 Intelligence a rank), Tome Hunter (tomes 8% likelier a rank, from 3), Seeker's
  Eye (+1 Magic Find a rank, from 8).
- **The Codex** (a synced player attachment): every enchant you've had on an item in your bag or as a tome. The first
  time is a chat line, a "CODEX DISCOVERY" banner and the XP above. Bags are looked through every five seconds.

## 7. Data and code

- `data/<ns>/skyrpg/enchants/<id>.json`: one enchant (format in `Enchant.java`'s javadoc): name, rarity, applies_to,
  max_level, table_max, values, effects, stats, description with `{value}` / `{value%}`, conflicts, source, costs,
  surge. Adding an enchant that only uses stats or existing gear effects needs no code.
- `data/skyrpg_enchants/skyrpg/enchanting/rules.json`: costs, bookshelves, tome prices, salvage, Enchanting XP, drops.
- `data/<ns>/skyrpg/tome_drops/*.json`: boss tomes.
- Clients get the enchants and rules from the server on join and after `/reload` (tooltips, screen, Codex).
- Core gained `MaterialStore` (costs take from backpacks too, like the bench; the items mod installs it).
- Admin: `/enchants` (open the screen), `/enchants tome <players> <enchant> [level]`, `/enchants add <players> <enchant>
  <level>` (held item, free), `/enchants clear <players>`, `/enchants codex <players> all|reset`, and `/enchants test
  <player> enchant|tome|combine|salvage|tab ...`, which does what the screen's buttons do (for the camera client).

## 8. Later

- A Disenchant option (take one enchant off for a cost), enchant-specific hit particles, more ultimates per skill, and
  Codex milestones (rewards at 25 / 50 / all discovered).

## 9. Tool durability and the Unbreakable enchant

SkyRPG **gathering tools** (pickaxes, axes, hoes and scythes, shovels, fishing rods: `SkyItems.isTool`) wear down like vanilla tools; every
other SkyRPG item (weapons, armour, shields, bows, wands, backpacks, accessories) keeps `minecraft:unbreakable`.

- **Durability** is data: `"durability"` in the tool's item definition (250 for the first iron-tier tools up to 4,000 for the Lumen Breaker;
  rods 120 to 2,200). `SkyItems.normalize` sets `max_damage` from it and removes `unbreakable`, so tools players already own become
  breakable (and undamaged) the next time they are in an inventory, ender chest or the cursor. A tool with no value falls back to its item's
  registered durability. The tool items themselves are registered without `unbreakable` (rods get 64 and the `skyrpg_items:repairs_rods` tag).
- **Never destroyed.** `core`'s `ToolWear` is the one place that decides. A mixin on `ItemStack.processDurabilityChange` (every wear path, even
  `hurtWithoutBreaking`, goes through it) passes the amount through `ToolWear.adjust`: a protected tool takes 0; in a `ToolWear.bulk` run only every
  4th extra block counts; and it never takes more than `max - 1 - damage`, so the tool stops at 1 durability. The moment it gets there the player
  gets a red action-bar message and a sound; while it is held a broken tool repeats the message every 2 s.
- **Broken** (`ToolWear.isBroken`: damage >= max - 1): mixins make `getDestroySpeed` 1.0, `isCorrectToolForDrops` false and `use`/`useOn` pass
  (no tilling, stripping or casting, and chests still open); `ToolTiers.tier` is 0 (so SkyRPG's tiered ores and woods don't crack); the held-item stat
  source, gear effects (perks), SkyRPG enchants (`EnchantSources`) and item abilities (`Abilities.tryUse`) skip it; `ToolCare` refreshes the player's
  stats when the held tool becomes broken or is mended.
- **Big abilities** wrap their extra `gameMode.destroyBlock` calls in `ToolWear.bulk` (Treefeller, scythe sweeps, vein/Hellfire Vein echoes) and stop when the
  tool is broken. Crops are soft (vanilla charges nothing), so `Harvest` charges the held hoe one point per grown crop.
- **Repair:** vanilla anvil with the tool's material (the `repairable` component that the tool material helpers add: iron ingot, diamond, netherite ingot;
  rods string or tidescale), or **Brannoc**: right-click him holding the damaged tool for a quote, again to pay `ceil(damage x rate)` coins (rate 0.4 /
  0.6 / 0.8 / 1.0 / 1.2 per point for common to legendary; sneak opens his shop). The Artisan Bench result is a fresh stack, so an upgrade is a full repair.
  The Bench now also keeps the SkyRPG enchants component (it only kept vanilla enchantments before).
- **Unbreakable** (`skyrpg_enchants:unbreakable`, legendary, one level, tools and rods): registered as a `ToolWear` protector, so the tool takes no
  damage at all. Table: Enchanting 22, 150,000 coins, 30 XP, 48 Lapis, 32 Runic Dust. Tomes: world boss 4%, each slayer boss 1.5% at tier III and 3% at
  tier IV.
