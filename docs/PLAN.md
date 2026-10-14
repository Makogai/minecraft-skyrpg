# SkyRPG plan

A Hypixel SkyBlock-style RPG for Minecraft 26.3 (Fabric). It's built for friends first, and designed so it can grow into one big public server later without a rewrite.

## Principles

1. **Many mods, one pack.** Each gameplay system is its own mod (own jar, own mod id), built from this one repo and released together with matching versions. Any system can be switched off, tested alone or reused.
2. **Three foundation mods, everything else plugs in.** `core`, `stats` and `items` are the base. Feature mods depend on them and never on each other. Where two feature mods need to cooperate, they do it through core's events and registries.
3. **Data-driven content.** Prices, XP, stats, items, enchants, shops, NPCs, mobs and bosses live in JSON datapacks, and textures and models in resource packs. Balancing never needs a recompile.
4. **The server decides, the client shows.** Every rule and number lives on the server. Client code only draws data the server syncs, which is what lets this scale to a public server.
5. **One look everywhere.** Every screen, HUD element and tooltip uses core's UI kit (`Theme`, `Draw`, the menu, HUD widgets), so twenty mods still feel like one game.

## Mods

| Mod | What it owns | Depends on |
|---|---|---|
| `core` | Data loading, messages, events between mods, the UI kit (menu, pages, HUD widgets and editor, tab list, container screens, theme), client settings | Fabric API |
| `stats` | The stat system: Health ❤, Defense ❈, Strength ❁, Crit Chance ☣, Crit Damage ☠, Speed ✦, Intelligence ✎ (mana), Mining Speed, Fortunes, Magic Find. Stat sources, the damage formula, mana | core |
| `items` | Custom items: rarity, stats, auto-generated lore, abilities (mana and cooldown), armor set bonuses, reforges, upgrades, recipes. Accessory slots (amulet, cloak, belt, bracers) and how gear looks when worn (accessory models, raised armour detail) | core, stats |
| `economy` | Purse, bank, NPC sell prices, NPC shops (buy and sell screens), coin sinks | core |
| `npcs` | NPC entities with skins, dialog and actions ("open shop", "start quest", "open menu"), placed by data | core |
| `skills` | Farming, Mining, Foraging, Combat (later Fishing, Enchanting, Alchemy...): XP, levels, stat rewards per level | core (stats optional) |
| `collections` | Item collection milestones (Wheat I–IX) that unlock recipes and rewards | core, items |
| `enchants` | Custom enchantments, the enchanting screen (costs coins and XP levels), books, combining, ultimate enchants | core, stats, items |
| `combat` | Custom mobs with levels, health bars, damage numbers, coin and loot drops, spawn areas | core, stats |
| `bosses` | Boss framework: phases, attacks, arenas, summoning, rarity loot with a pity meter | core, stats, combat |
| `slayers` | Slayer quests: earn combat XP, then summon the slayer boss. Tiers, slayer levels, slayer gear | core, combat, bosses |
| `mounts` | Griffins: wild griffins at nests, taming, the Griffin Whistle, the mount stable and K page, riding and flight with stamina. See `docs/design/the_wild.md` | core |
| `hub` | The hub world, spawning, protection, warps | core |
| `areas` | Special zones: regenerating mines, farming islands, boss arenas, area names on the HUD | core |
| `quests` | Guided progression for new players ("Talk to the Farmer", "Reach Farming V") | core |
| later | `pets`, `minions`, `fishing`, `dungeons`, `bazaar` / `auction` (player trading, only for the big server) | |
| dev only | `skyrpg_devtools` (Claude's tools), Carpet, BlueMap | |

## How mods talk to each other

- **Events in core** (Fabric events): `MOB_KILLED`, `ITEM_PICKED_UP`, `BLOCK_HARVESTED`, `COINS_CHANGED`, `LEVEL_UP`, and so on. Skills, collections and slayers all listen to `MOB_KILLED` without knowing about each other.
- **Registries in the foundation mods.** Other mods add to them:
  - Stat sources (armor, skill levels, enchants, potions) register with `stats`.
  - Menu pages, profile tiles, HUD widgets and settings register with `core`.
  - NPC actions register with `npcs`. `economy` adds "open shop", `slayers` adds "start slayer quest".
- **Optional integration.** A mod can check `FabricLoader.isModLoaded(...)` before it hooks into another one. For example, skills only gives stat rewards when `stats` is loaded.

## Player data

- Each mod keeps its own player data in Fabric data attachments, which are saved with the player and synced to that player's client where the UI needs it.
- Every attachment is a small, versioned record, so it can later move into a database and into per-player profiles (Hypixel-style multiple profiles) without touching gameplay code.

## UI

- **SkyRPG Menu (K):** pages from every mod. Profile, Skills and Settings come first, then Stats, Collections, Enchants, Slayers and so on.
- **HUD widgets plus an editor**, in the spirit of NEU and Skytils: purse, bank, health/mana/defense bar (replacing hearts), skill progress with XP per hour, pickup log, area name, slayer quest progress, boss health, ability cooldowns. Players drag, scale and toggle every widget, and positions are anchored so they work at any resolution.
- **Custom tab list:** players on the left, info panels on the right (profile, skills, stats, area, events).
- **Container screens** in the same style (shops, enchanting, upgrades), built on one base class so every mod's screens match.
- **Inventory (E):** an accessory strip left of the armour, in E and on the creative inventory tab. It's drawn in the window's own style (themed or vanilla), and its empty slots have outline icons like the armour slots.
- **SkyRPG theme for vanilla screens** (on/off in Settings): a built-in resource pack that re-skins every vanilla widget (buttons, sliders, text fields, tabs, scroll bars, tooltips) and recolours container backgrounds (inventory, chests, furnaces...). A small code tweak keeps container titles readable on dark panels. Custom title and pause screens come later.
- **Settings page:** Interface (theme, animations, UI sounds), HUD (editor, widget toggles), Gameplay (damage numbers, pickup log, chat filters). Saved to `config/skyrpg/client.json`.

## Economy (friends version)

- **No trading between players yet.** Coins come from and go to NPCs and the game:
  - **Sources:** selling to NPCs, mob coin drops, skill level rewards, boss loot, quests.
  - **Sinks:** NPC shops, enchanting, reforging, upgrades, the bank's limit upgrades.
- The bank holds coins safely. The purse is what you carry, so the pack can have an optional death penalty, off by default for friends.
- Player trading (bazaar, auction house) is a separate later mod for the big server, so the NPC-only economy never depends on it.

## Getting ready for a big server

- All rules run on the server, with per-player data and permission levels for admin commands.
- Performance budget: no per-tick world scans, cached lookups, and data loaded once per `/reload`.
- Exploit guards: tracking player-placed blocks (no XP from re-placed logs or ores), limits on AFK farms.
- Admin tools: resetting or wiping players, inspecting their data, live reload of balance data.

## Roadmap

| Phase | Delivers |
|---|---|
| **1. UI everywhere** (done) | Settings page and client config, the SkyRPG theme for vanilla screens, the HUD widget framework and editor (purse, skill progress), custom tab list |
| **2. Stats and items** (done) | `stats` (health/mana bar replaces hearts), `items` (rarity, lore, abilities, set bonuses), first armor sets: Farmer, Miner, Lumberjack, Warrior |
| **Accessories and armour depth** (built; in-game test pending) | In `items`: four accessory slots beside the armour (E and creative), eight accessories sold by Quill's Curiosities, 3D accessory models everyone sees, raised 3D detail on the four armour sets. See `docs/design/accessories.md` |
| **Mining and backpacks** (built) | `mining`: placeable nodes, Skyiron/Emberite/Lumenite ores, Glimmer currency, rare finds, auto-pickup, the Mining Master's level-gated shop. Backpacks: three slots right of the inventory, five sizes |
| **Farming** (built) | `farming`: special crops with growing conditions, planters, sprinklers, Dew, rare finds, compost, meals; farming tools and five sets; gear effects and boosts for every skill; Harvest Rush |
| **Fun pass** (built) | Talents for every skill, daily co-op contracts, Elite mobs (`combat`), auto events and Glimmer Rush, skill milestone coins, bank interest. See `docs/design/fun-pass.md` |
| **Crafting and skills** (built) | The Artisan Bench, crafted gear ladders, tool tiers (pickaxes and axes), the mining pass (Duskquartz, ingots, Echo Sense, auto-smelt, set effects, Lumenward), unique looks and 3D detail for every set, placed-block anti-abuse. See `docs/design/progression.md` |
| **Foraging** (built) | `foraging`: five woods with growing conditions and their own trees, stumps, Heartwood Splinters, rare finds, Treefeller axes, five sets |
| **Combat** (built) | `combat`: mob levels with nameplates, damage numbers, Valor Marks, Dread Fangs and Nightmare Cores, the sword line, five sets, the Training Dummy |
| **Slayers and spawners** (built) | `combat`: Morwen Ashgrave, four slayer lines with tiers I-IV and their own bosses, slayer levels and gear, the spawner ladder (Philip's idea), farmed spawner mobs. See `docs/design/slayers.md` |
| **Enchanting** (built) | `enchants`: 71 enchants, the Arcane Enchanting screen (Seraphine, enchanting tables with bookshelf power), tomes (drops, combining, salvage, Runic Dust), ultimates, the Enchanting skill and the Codex. See `docs/design/enchanting.md` |
| **World mobs** (built) | `combat`: eight new mobs of our own (Glimmer Crawler, Bog Hag, Frost Revenant, Ember Imp, Grove Sentinel, Tide Lurker, Hollow Wisp, Gravecaller) with spawns, tricks, drops and slayer counting |
| **Fishing** (built) | `fishing`: Fishing Speed and Sea Creature Chance, Tidescales, rare finds, sea creatures, baits, five rods, four sets, Marina the Fishing Master, Fishing Frenzy |
| **World dungeons and waystones** (built; Adventure Plan v2 phase 1) | `world`: Forgotten Crypt, Spider Hollow and Drowned Temple generate from the seed with bosses, mob packs, puzzles, vaults and treasure maps; Wayshrines, village waystones and waystone travel; dungeon gear. See `docs/design/dungeons.md` |
| **Dungeons v2 and danger regions** (built; phase 2) | Two-floor dungeons with champions, waves, traps, per-player chests, Reward Chests, the Delving skill and craftable dungeon gear; danger regions (Blightlands, Ashen Wastes, Frostgrave, Mire) shown red on the map, with corrupted land, stronger monsters, essences and region gear. See `docs/design/danger_regions.md` |
| **The Deep** (built; phase 3) | Underground layers (Glimmer Caverns, Fungal Depths, Dwarven Halls, Magma Rifts) with their own blocks, monsters, levels and drops; the Dwarven Halls dungeon with the Iron Thane; Abandoned Mine Camps; Runeforged armour; 3D detail for every set. See `docs/design/the_deep.md` |
| **Chat, VFX, pets, K menu, the Nether** (built; phase 4) | New `chat` mod (badges, icons, showcase, mentions, notices), new `vfx` particle system, 3D pets and a Pets page, the K menu redesign, waypoints; Ember Forest, Bone Sea, Obsidian Spires, Hellforged, the Infernal Citadel, lava fishing. See `docs/design/nether.md`, `chat.md`, `vfx.md`, `waypoints.md` |
| **The end game** (built; phase 5) | End biomes (Void Gardens, Shattered Isles, Astral Fields), the Astral Observatory, Ruined Citadel and Catacombs dungeons, the Ender Wyrm final boss. Wyrmscale gear and the altar still to come. See `docs/design/end_game.md` |
| **The Wild** (part 1 built; phase 6) | Blood Moon nights that play out per danger region, region heart bosses, the Shattered Highlands (griffin nests) and the Haunted Moors, region fog, and the new `mounts` mod with tameable, rideable griffins in ten colours (rare ones glow, legendary ones animate). Part 2 still to come: surface biomes, meteors, caravans, roaming bosses, Wyrmscale. See `docs/design/the_wild.md` |
| **The Journal** (in progress) | New `progress` mod: the Skyhaven Chronicle questline (8 chapters, Journal page, quest tracker, waypoint tracking), collections with tiers and recipe unlocks, the SkyRPG Level. See `docs/design/journal.md` |
| **Utilities** (built) | `utils`: minimap with cave view, world map (M), look-at info, info box, all in the HUD editor and Settings |
| **3. NPCs and shops** | `npcs`, NPC buy/sell shop screens, bank, hub NPCs (Farmer, Miner, Lumberjack, Blacksmith, Banker). The `/sell` command becomes a fallback |
| **4. Enchanting** (done) | `enchants`: the enchanting screen with coin and level costs, 71 enchants, tomes, ultimates, the Enchanting skill and the Codex (see `docs/design/enchanting.md`) |
| **5. Bosses** | `bosses` with the first boss and its arena (levelled mobs, damage numbers and health bars are in `combat`) |
| **6. Slayers** | `slayers`: first slayer (zombie), tiers I–IV, slayer levels and gear |
| **7. Depth** | collections, quests, more accessories, pets, minions, areas |
