# SkyRPG SkyBlock: the plan (2026-10-16)

Turn SkyRPG into a **1:1 Hypixel SkyBlock clone**, built entirely as our own Fabric mods: the same game loop, systems,
progression and numbers, with our own menus (custom screens, never chest GUIs) and our own item art. Clone first, then
add our own twists. Owners: Drogashh (Marko) and PhilipSGamer.

## What "1:1" means here

| Part | Same as Hypixel | Ours |
| --- | --- | --- |
| Systems and numbers | skill XP tables and level rewards, collection tiers and unlocks, minion rates, slayer and dungeon structure, Bazaar and Auction House rules, stat formulas, drop chances | |
| Menus | what each menu contains and does | the screens themselves (the K menu becomes the SkyBlock Menu) |
| Items | each item's role, stats, ability, recipe, name and SkyBlock ID | looks: **FurfSky Reborn** textures (see below); our own art only as the fallback and for our extras |
| Maps | each island's purpose, zones and layout idea | the builds |

The numbers come from the Hypixel SkyBlock wiki (game facts). Its text and maps are Hypixel's, so we write and build our
own.

## Item looks: FurfSky Reborn

Every item and armour piece should look the way **FurfSky Reborn** draws it (the owners' choice, 2026-10-16).

- **How it works.** FurfSky Reborn (Modrinth `furfsky-reborn`, by Tylerrrr; v2.0-pre.5 for 26.1–26.2) is a pack for
  the **Catharsis** mod (Modrinth `catharsis`, MIT, has a 26.3 build, needs Fabric Language Kotlin, which the pack
  already has). Catharsis picks a texture from data stored on the item, the same fields Hypixel puts there: the
  SkyBlock ID (`ASPECT_OF_THE_END`...), reforge, dye, skin. Island- and area-specific textures come from Hypixel's mod
  API location packets.
- **What we do.** Every SkyRPG item carries Hypixel's item data (SkyBlock ID, uuid, modifier and the rest) in its
  `custom_data`, so the installed pack textures it. The server sends the location packets with our island and area
  ids, so island-specific textures work too. Our generated art stays as the fallback when the pack isn't installed.
- **Names follow.** FurfSky textures by Hypixel's item IDs, so items use Hypixel's IDs and names (decision 2 below is
  settled by this).
- **Licence.** FurfSky's terms allow personal use (editing included) but not redistributing it or using its textures
  in another publicly distributed pack. So we never commit or host its files: the local copy lives in `furf/`
  (gitignored) for development, and the pack lists Catharsis and FurfSky as Modrinth downloads that each player's
  launcher fetches from Modrinth itself. Asking the FurfSky team (their Discord) that this is fine is on the Phase 0
  list.
- **First check (Phase 0).** Load Catharsis and FurfSky on staging, give an item Hypixel's data and confirm the
  texture appears off-Hypixel; then the location packets for island textures.

## Where we stand

✅ have it · 🟡 have a base to extend · 🔄 have something different to rework · ❌ missing

| Hypixel system | SkyRPG today | |
| --- | --- | --- |
| Stats (Health, Defense, Strength, Crit, Speed, Intelligence, fortunes, wisdoms, Magic Find, SCC...) | `stats`: 21 stats, sources, recompute | ✅ add ~20 (Ferocity, Attack Speed, Ability Damage, True Defense, Pet Luck, Mining Spread, Pristine, Vitality, Mending, Health Regen, Heat/Cold Resistance, Swing Range, Rift stats) |
| 12 skills (Farming, Mining, Combat, Foraging, Fishing, Enchanting, Alchemy, Carpentry, Taming, Runecrafting, Social, Hunting) + Catacombs | `skills`: 5 skills | 🟡 7 to add; XP tables and rewards to Hypixel's (50/60 caps) |
| Collections (~70) unlocking recipes | `progress`: collections, 9 tiers | 🟡 data, and tiers unlock recipes |
| Recipe Book, enchanted materials (160x) | Artisan Bench | 🟡 recipes in the crafting grid, unlocked by collections |
| Private Island, Minions | none | ❌ the biggest gap |
| Hub and public islands | Skyhaven + one survival world | 🔄 rebuild as islands |
| NPC shops, selling, Bank with interest, `/pay` | `economy` | ✅ |
| Bazaar, Auction House, trading | none | ❌ |
| Weapons, armour, tools with abilities and set bonuses | `items` (~250 items), `combat` | ✅ engine; content to replace and extend (hundreds more) |
| Reforges and stones, Recombobulator, Hot Potato/Fuming, gemstone slots, stars | none | ❌ |
| Enchanting (table with XP levels, books, anvil, ultimate enchants, Experimentation Table) | `enchants`: custom enchants, tomes, Codex | 🟡 engine reuse |
| Accessories (Accessory Bag, ~200 talismans, Magical Power, Power Stones, enrichments) | 4 accessory slots, 8 accessories | 🔄 replace with the bag |
| Pets (~80, level 100, pet items, Kat upgrades) | `pets`: 6 pets, level 50 | 🟡 engine reuse |
| Slayers (Revenant, Tarantula, Sven, Voidgloom, Inferno, Riftstalker; tiers I–V) | `combat`: 4 slayer lines | 🟡 |
| Dungeons (Catacombs F0–F7, M1–M7, classes, generated layouts, score, chests, essence) | `world`: 8 handmade world dungeons | 🔄 new instanced system |
| Kuudra (5 tiers) | none | ❌ |
| Dwarven Mines (commissions, Heart of the Mountain, powder, Forge), Crystal Hollows, Glacite Tunnels, Mineshafts, fossils | `mining`: ore nodes, Glimmer, Echo Sense; the Deep | 🟡 |
| The Barn, Mushroom Desert, Garden (plots, visitors, milestones, composter, pests), Jacob's contests | `farming`: crops, planters, sprinklers, meals | 🟡 |
| Fishing (sea creatures, trophy fish, lava fishing, Backwater Bayou) | `fishing`: bite cues, baits, hotspots, sea creatures | 🟡 good base |
| The Park (revamped), Galatea / Moonglade Marsh, Hunting skill, attributes and shards | `foraging`: trees, stumps, Treefeller | 🟡 |
| The End (Zealots, dragon fights), Crimson Isle (factions, Dojo), The Rift (motes) | `world`: End rework, Ender Wyrm, Nether rework | 🔄 |
| Calendar (124-hour SkyBlock year) and events (Spooky Festival, Season of Jerry, New Year, Traveling Zoo, Dark Auction, Mining Fiesta, Fishing Festival), Mayors and elections | `events`: timed events; Blood Moon | 🟡 |
| SkyBlock Level, Fairy Souls, Museum, Bestiary, Booster Cookie, Sacks, Storage pages, Wardrobe, Quiver, Abiphone | `progress`: SkyRPG Level, titles; backpacks | 🟡 |
| Parties, chat, guides | `party`, `chat`, `wiki`, `helper` | ✅ |
| (not in Hypixel) | magic wands and spell wheel, griffin mounts, brainrots, crates, Builder, minimap | extras: see decision 3 |

## The big change: islands instead of a survival world

- **Profiles and co-ops.** Every player has several profiles (Hypixel: up to 5). All progress (today's player
  attachments) moves under the active profile; switching profiles swaps it. A co-op shares one island, bank and minions.
  Everything else builds on this.
- **Private Island.** One per profile, in an `islands` dimension laid out on a grid (one island every ~2,000 blocks)
  rather than a dimension per player: simpler and fine for hundreds of profiles. Placed from the starter template
  (with the Builder's schematic code). Owners and co-op members build; `/visit` with guest permissions; island border.
- **Public islands.** Each island is its own handmade dimension, like Skyhaven: no building, ores, logs and crops
  regrow, monsters spawn only in their zones (custom spawn tables, not vanilla spawning), NPCs, portals and `/warp`.
- **Instances.** Dungeons and Kuudra get a fresh copy per party: an `instances` dimension where a generated map is
  placed for the run and cleared after.
- **Offline progress.** Minions and other timers on unloaded islands are worked out from elapsed time when the island
  loads (as Hypixel does), so a few hundred islands cost nothing while nobody is there.

## Phases

Each phase ends playable. Sizes are rough, in long working sessions like the ones that built Brainrots or Crates.

### Phase 0: Foundation (3–5 sessions)

Profiles and co-ops (data moved under profiles, a migration tool), the `islands` dimension with the starter island,
permissions and `/visit`; the SkyBlock Menu (the K menu reorganised: Profile and Stats, Skills, Collections, Recipe
Book, SkyBlock Leveling, Quest Log, Calendar, Storage, Wardrobe, Pets, Fast Travel, Settings); SkyBlock time and the
calendar; item upgrades (Hypixel item data on every stack, unique ids, rarity upgrades, reforge, stars and gemstone
slots, soulbound); Catharsis + FurfSky Reborn in the pack, working on our server; block regrowth
and mob spawn zones for public islands; Fast Travel and `/warp`; Storage (ender chest pages, backpacks) and Wardrobe.

### Phase 1: Day One (8–12 sessions) — the first playable SkyBlock

- **Minions**: every Hypixel type, tiers I–XI (XII), fuel, upgrades (compactors, auto smelter...), storage, unique
  crafts that unlock minion slots, offline progress.
- **Collections** 1:1 with the **Recipe Book** and enchanted materials.
- **Skills** on Hypixel's XP tables and level rewards.
- **Islands**: the Hub (village with shops, Bank, Bazaar and Auction House, the Farm, Forest, Graveyard, Coal Mine,
  Wilderness, Colosseum, Mountain), the Farming Islands (Barn, Mushroom Desert), The Park, Gold Mine, Deep Caverns,
  Spider's Den.
- **Bazaar** (instant buy/sell, buy orders and sell offers, tax) and **Auction House** (BIN and bids, fees).
- **Slayers**: Revenant Horror, Tarantula Broodfather, Sven Packmaster.
- Fairy Souls and SkyBlock XP.

Milestone: start a fresh SkyBlock profile and it plays like Hypixel's first weeks.

### Phase 2: Mid game (8–12 sessions)

The End (Zealots, summoning eyes and dragon fights), Dwarven Mines (commissions, Heart of the Mountain, Mithril
Powder, the Forge), Crystal Hollows, Glacite Tunnels and Mineshafts; Voidgloom Seraph; reforges and reforge stones,
Recombobulator, Hot Potato and Fuming books; Hypixel enchanting (table, anvil, ultimates, Experimentation Table);
Accessory Bag with Magical Power and Power Stones; pets 1:1 with pet items, Taming and Kat; Alchemy, Carpentry,
Social, Runecrafting; Bestiary, Museum, Booster Cookie, Sacks, Quiver.

### Phase 3: Dungeons (6–10 sessions)

The Dungeon Hub; the Catacombs as instances with a room-by-room layout generator (a library of handmade rooms), the
five classes (Healer, Mage, Berserk, Archer, Tank), blessings, secrets, score and reward chests; floors F0–F7 with
their bosses, then Master Mode; dungeon items with stars and essence; the Catacombs skill.

### Phase 4: Late game (8–12 sessions)

Crimson Isle (factions, quests, Dojo, lava fishing, Inferno Demonlord, Kuudra T1–T5), the Garden (plots, visitors,
crop milestones, composter, pests, Jacob's Farming Contests), Galatea / Moonglade Marsh with the Hunting skill
(attributes and shards), Backwater Bayou, gemstones across the game, The Rift (motes, Riftstalker Bloodfiend).

### Phase 5: Live service (3–5 sessions)

Calendar events (Spooky Festival, Season of Jerry and Jerry's Workshop, New Year Celebration, Traveling Zoo, Dark
Auction, Mining Fiesta, Fishing Festival), Mayors and elections with perks, leaderboards. Then our own twists.

Roughly 40–55 sessions for the whole clone; Phase 0 + 1 (first playable) about 12–15.

## How today's mods carry over

| Mod | Becomes |
| --- | --- |
| core | SkyBlock Menu, HUD, notices, profiles (or a new `profiles` mod) |
| stats, skills | Hypixel's stats and 12 skills |
| items, enchants | the item engine (+ reforges, gemstones, stars) and Hypixel enchanting |
| pets | the 1:1 pets |
| combat | mob levels, slayers, world bosses |
| mining, farming, foraging, fishing | each island's gathering mechanics |
| progress | collections, SkyBlock Level, Fairy Souls, Museum, Bestiary |
| economy | Bank, NPC shops (Bazaar and Auction House as new mods) |
| hub | the Hub and the public-island framework |
| world | The End, Crimson Isle content; its dungeon code feeds the Catacombs instancer |
| events | calendar events and Mayors |
| party | dungeon parties |
| builder | building the islands (schematic placement) |
| wiki, helper | the SkyBlock wiki and assistant |
| brainrots, crates | seasonal extras |

New mods: `islands` (with profiles), `minions`, `bazaar`, `auctions`, `catacombs`, `garden`, `calendar`, `rift`,
`hunting`, and one per new island as needed.

## How the content gets made

- **Items**: thousands in Hypixel. Definitions come from data tables in our JSON format; art from our generators
  (`tools/art`) one family at a time (armour sets, weapons, talismans, minions).
- **Islands**: generator scripts like `tools/builds/hub_skyhaven.py` plus the Builder. The Hub, Dwarven Mines and
  Crystal Hollows are the biggest builds.
- **NPCs**: data JSON plus skins (`tools/npc_art`).
- **Numbers**: from the Hypixel SkyBlock wiki into our data files, so they're easy to tune later.

## Risks

- **Scope.** Hypixel is years of a team's work. The systems clone fast; items and maps take the longest. Each phase
  ships content in batches.
- **Performance.** Offline progress for minions; instances cleared after each run; spawn zones instead of vanilla
  spawning keep public islands light.
- **Look and IP.** Mirroring mechanics and numbers is fine. FurfSky's textures are theirs: we fetch the pack from
  Modrinth, never host it, and ask them first. Catharsis is open source (MIT).
- **FurfSky off-Hypixel.** If Catharsis turned out to only run on Hypixel, its MIT licence lets us ship a small patched
  build, or our own mod can read the same pack format.
- **The live survival world** (decision 1).

## Decisions for the owners

1. **The survival playthrough.** (A, recommended) keep playing it while SkyBlock is built; start fresh SkyBlock
   profiles once Phase 1 is playable; the survival world stays reachable as an archive. (B) carry coins and skill
   levels into the first SkyBlock profile. (C) switch now.
2. **Names.** Settled: Hypixel's exact names and SkyBlock IDs, because FurfSky textures items by those IDs.
3. **Our extras.** (A, recommended) keep brainrots, crates, the Builder, SkyHelper and the minimap; switch the spell
   wheel and griffin mounts off in SkyBlock mode (Hypixel has neither) but keep the code for later twists. (B) keep
   everything on.
