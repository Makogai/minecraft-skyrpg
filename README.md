# SkyRPG

A Hypixel SkyBlock-style RPG modpack for Minecraft **26.3 (Fabric)**. All gameplay is our own mods; existing mods are only used for utilities (JEI, maps, performance).

## Layout

Each system is a separate mod with its own jar, so it can be tweaked, disabled or reused on its own.

| Project    | Mod id           | What it does |
|------------|------------------|--------------|
| `core`     | `skyrpg_core`    | Shared foundation: JSON data loading, message formatting. No gameplay. |
| `economy`  | `skyrpg_economy` | Purse (coins), NPC sell prices, `/sell`, `/pay`, `/coins`, purse HUD. |
| `skills`   | `skyrpg_skills`  | Farming, Mining, Foraging, Combat. XP from playing, levels, `/skills`. |
| `stats`    | `skyrpg_stats`   | Makes stats work: health, defense, strength and crits, speed, mining speed, fortunes, mana. Health/mana bars, Stats page, `/stats`. |
| `items`    | `skyrpg_items`   | Custom items and armor sets: rarity, generated tooltips, abilities, set bonuses, recipes, accessories, backpacks, 3D armour detail, `/items`. |
| `mining`   | `skyrpg_mining`  | The Mining skill's content: placeable respawning nodes, new ores and materials, rare finds, auto-pickup, the Mining Master's shop. |
| `farming`  | `skyrpg_farming` | The Farming skill's content: special crops with growing conditions, planters, sprinklers, Dew and rare finds, Rich Compost, meals, what farming gear effects do. |
| `combat`   | `skyrpg_combat`  | Danger and loot ahead of the full combat plan: Elite mobs, Elite Trophies, `/elite`. |
| `enchants` | `skyrpg_enchants` | Arcane enchanting: 71 custom enchants, the enchanting screen (Seraphine, enchanting tables), tomes, the Enchanting skill, the Codex. |
| `world`    | `skyrpg_world`   | The survival world comes alive: dungeons that generate from the seed (bosses, mob packs, puzzles, key vaults, treasure maps), Wayshrines, waystones and travel. |
| `utils`    | `skyrpg_utils`   | Client-only player utilities: minimap (with cave view), full world map, look-at info, coordinates/biome/time box. |
| `vfx`      | `skyrpg_vfx`     | Data-driven particle effects: armour-set auras, held-item and mob effects, pet auras, one-shot abilities. `/vfx`, Effects quality setting. |
| `mounts`   | `skyrpg_mounts`  | Griffins: wild ones nesting in the Shattered Highlands, taming, the Griffin Whistle, a mount stable, riding and flight on stamina, the Mounts page. |
| `party`    | `skyrpg_party`   | Persistent parties (up to 8, saved in the world): `/party`, party chat and the chat Party tab, a member overlay with heads and health, the Party page, no friendly fire. |
| `wiki`     | `skyrpg_wiki`    | Client-only, optional: the in-game wiki (K menu Wiki page, `/wiki`, the Look it up key). |
| `hub`      | `skyrpg_hub`     | The hub world everyone spawns in: village build, protection, `/hub`, `/survival`. |
| `dev`      | `skyrpg_devtools` (dev only) | Runs all mods together, plus Carpet (fake-player testing), BlueMap (3D web map) and the devtools used by the MCP tools. |
| `tools/`   |                  | `mcp_server.py` (Claude's Minecraft tools), `mc.py` RCON bridge, build generators and test scripts. |

## Running

```sh
./gradlew build             # jars in <module>/build/libs
./gradlew :dev:runServer    # dev server in run/server
./gradlew :dev:runClient -Pjoin=localhost   # dev client (offline name "Drogashh") joining the dev server
```

`-Pjoin=localhost` makes the client join the dev server straight away. Adding `-Pcam` starts a second, camera client instead ("SkyCam", run/client-cam, debug port 5007, muted, keeps rendering in the background, `-PcamSize=1280x720`) that Claude screenshots UI with while you play; its title bar says so. `/devtools guiscale <player> <n>` and `/devtools window <player> <w> <h>` change a dev client's GUI scale and window size. Dev runs use the mods' class folders (not jars) on the JetBrains Runtime, so you can rebuild while playing and push code changes into a running game:

```sh
python tools/hotswap.py     # compile, then hot-swap changed classes into the running client and server
```

Changed method bodies and added methods or fields go live; the script says when something still needs a restart (new mixins, startup code). Commands registered at startup need `/reload` after swapping. Data JSON: `/reload`. Textures and lang: F3+T. Gradle needs Java 25. It uses an installed JDK 25 if it finds one, or downloads one (see `gradle/gradle-daemon-jvm.properties`).

## Playing with friends (LAN)

1. `python tools/mc.py setup --lan` (the server then listens on the LAN, not only 127.0.0.1) and restart the server. Windows asks once to let Java through the firewall: allow it on Private networks.
2. Whitelist them. The dev server runs in offline mode, so `/whitelist add` would store their Mojang UUID instead of the offline one the server uses; add `{"uuid": <offline uuid>, "name": ...}` to run/server/whitelist.json and run `/whitelist reload` (offline UUID = UUID v3 of `OfflinePlayer:<name>`).
3. `python tools/package_friend.py` builds dist/SkyRPG-<version>.zip: the SkyRPG mod jars (not devtools) and an install guide for Prism Launcher or the official launcher. During development friends keep Fabric API themselves; `--with-fabric-api` puts it in the zip too. Send them a new zip after every change.

Offline mode means anyone on the network can join under any name (including an op's), so keep it LAN-only and never port-forward it.

**Posting builds to Discord:** `python tools/discord_release.py -m "what changed" -i screenshot.png` posts the newest zip (or `--package` to build one first), the changelog and screenshots through a webhook. Put the webhook URL in `tools/.discord_webhook` (gitignored; `--set-webhook` asks for it hidden) or `DISCORD_WEBHOOK_URL`. Discord allows 10 MB per post on unboosted servers; `--dry-run` shows what would be sent.

## Talking to the server (RCON bridge)

```sh
python tools/mc.py setup                       # once: RCON on 127.0.0.1, offline mode, EULA
python tools/mc.py wait                        # wait for the server to come up
python tools/mc.py cmd "time set day" "coins Drogashh"
python tools/mc.py script tools/tests/smoke.mcfunction
```

`tools/tests/smoke.mcfunction` checks selling, coins and skill XP end to end with a Carpet fake player.
`tools/tests/hub.mcfunction` checks /hub, /survival, the survival gate, void rescue and hub protection.

While the dev server runs, BlueMap shows the hub as a 3D map at http://127.0.0.1:8100 (after `save-all`, `bluemap force-update world_skyrpg_hub_hub`).

## The hub

A separate void dimension (`skyrpg_hub:hub`) holding Skyhaven, a floating town above the clouds. The mod makes it the world spawn, so new players and anyone who dies without a bed appear there. Walking through the Wildgate ring on the south islet (or `/survival`) goes to the survival world, back to where you left it. Outside creative mode nobody can break, place, fight or take damage in the hub, fields can't be trampled, and falling off the island returns you to spawn.

The build ships as a structure file (`hub/src/main/resources/data/skyrpg_hub/structure/hub.nbt`) and is placed automatically into every world. To change it:

1. Edit it in the dev world, in creative mode or with `tools/builds/hub_skyhaven.py` (the generator; `--apply` updates the live hub in place, skipping anything players changed). `hub_village.py` made the first, simpler village.
2. `/hubadmin save`, then copy `run/server/world/generated/skyrpg_hub/structure/hub.nbt` over the file above.
3. Bump `build.version` in `hub/src/main/resources/data/skyrpg_hub/skyrpg/hub/hub.json`. Worlds with an older version rebuild their hub on next start.

Spawn point, gate area and build box (x/z -96..95, y 16..119; the sky above is kept free for events) are all in that `hub.json`. Never change the build box without re-saving the build, because the file is placed at the box's corner. The same file lists the hub's `decor` (floating item displays: the Skyheart crystal, windmill sails, sky lanterns, the Wildgate veil) and particle `emitters`. Decor isn't saved in the structure; `HubDecor` spawns whatever is missing and animates it. NPC spots come from `tools/builds/hub_spots.json` via `tools/builds/sync_npc_spots.py`.

## The SkyRPG menu

Press **K** in game (rebindable under Controls > SkyRPG). One window for everything the pack adds, with a sidebar of pages and a dark, custom look instead of vanilla screens. Pages: **Profile** (your character plus stat tiles), **Skills** (a card per skill with level and progress) and **Settings**. Arrow keys or 1-9 switch pages.

Each mod adds its own pages and Profile tiles from its client code:

```java
SkyRpgMenu.registerPage(new SkillsPage());                       // a MenuPage
SkyRpgMenu.registerProfileStat(new ProfileStat("Purse", "⛁", Theme.ACCENT, 0, player -> ...));
```

Shared look: `core/src/client/.../ui/Theme.java` (colours) and `Draw.java` (panels, bars, tooltips).

Stat, mana, coin and level icons are private-use characters (`StatTypes`, U+E100–E113) drawn by core's font from `assets/skyrpg_core/textures/font/stat_icons.png`. Settings > Interface > **Classic stat symbols** switches to the original ❤ ❈ ❁ look. It's a built-in pack (`resourcepacks/skyrpg_classic_icons`, made by `tools/theme/generate_classic_icons.py` from Minecraft's own Unifont) that swaps only that image, so it's a resource reload and needs no restart. The SkyRPG theme for vanilla screens is a pack toggled the same way (`ToggledPacks`).

The **Settings** page lists every `SkyRpgSettings.toggle(...)` / `action(...)` any mod registers, grouped by category. Values live in `config/skyrpg/client.json`.

### HUD widgets

Info boxes like the purse and skill progress are `HudWidget`s registered with `HudManager.register(...)`. Settings > HUD > Edit HUD layout opens the editor: drag to move, scroll to resize, right-click to hide, R to reset. Widgets pin to the nearest screen third, so layouts survive window resizes.

### Banners

`Banners.show(new Banners.Banner(icon, "SKILL LEVEL UP", title, subtitle, color))` slides a banner in at the top of the screen (queued, can be turned off in Settings). Skill level ups use it; collections, slayers and bosses will too.

### Tab list

Holding Tab shows the SkyRPG player list: players with heads and ping on the left, info panels on the right. Mods add panels with `SkyRpgTab.registerPanel(new TabPanel(...))`. It can be switched back to vanilla in Settings.

## Claude in the game (MCP)

`.mcp.json` registers `tools/mcp_server.py`, which gives Claude Code tools for the dev server: `server_status`, `start_server`, `stop_server`, `run_commands`, `run_script`, `scan_area` (blocks as text), `get_player` (position, facing, held item, block looked at), `read_chat`, `server_log`, `refresh_map` (BlueMap), `client_screenshot` (captures a player's dev client window, optionally after opening a menu page), and for the events mod `start_event`, `event_status`, `end_event`, `announce` and `board` (see tools/events/README.md). It needs only Python's standard library and talks to the server over RCON.

The first time, approve the `minecraft` server when Claude Code asks (or via `/mcp`). To ask Claude for things from inside the game, type in chat (for example `@claude build a tower here`) and have a session watching it: `/loop check Minecraft chat for messages to @claude and do them`.

`scan_area` relies on the dev-only `skyrpg_devtools` mod in the `dev` project (`/devtools scan`), which is never shipped in the pack.

## Balancing without recompiling

All numbers are JSON in the mods' built-in datapacks. To change them, override the same path in a datapack, or add a new file next to it, then run `/reload`.

| What | Path |
|------|------|
| Sell prices | `data/<ns>/skyrpg/sell_prices/*.json`: `{"minecraft:wheat": 6}` |
| Skills | `data/<ns>/skyrpg/skills/<name>.json`: name, color, icon, description, order, optional `levels` curve, `rewards` (stats per level, e.g. `{"farming_fortune": 4, "health": 2}`) |
| Items | `data/<ns>/skyrpg/items/*.json`: base item, name, rarity, dye colour, stats (incl. weapon `damage`), set, description, ability |
| Item sets | `data/<ns>/skyrpg/item_sets/*.json`: name, pieces, bonus name/description/stats |
| Accessory slots | `data/skyrpg_items/tags/item/accessory/{amulet,cloak,belt,bracers}.json`: which items go in each slot |
| Fortune blocks | `data/skyrpg_stats/tags/block/{mining,farming,foraging}_fortune_blocks.json` |
| Skill XP sources | `data/<ns>/skyrpg/skill_xp/*.json`: blocks (ids or `#tags`) and entities per skill |
| Meals | `data/<ns>/skyrpg/meals/<item>.json`: `stats`, `effects`, `minutes` |
| Bonus drops | `data/<ns>/skyrpg/bonus_drops/*.json`: `block`, `item`, `chance`, `grown_only`, `night_only` |
| Bench recipes | `data/<ns>/skyrpg/recipes/*.json`: `category`, `result`, `count`, `ingredients` (`{"item": count}`), `requires` (`{"skill", "level"}`); the first SkyRPG gear ingredient is the one being upgraded |
| Shops | `data/<ns>/skyrpg/shops/*.json`: title, keeper, accent, offers. An offer has `item`, `count`, `price` in coins (optional when it has `costs`), `costs` (`[{"item", "count"}]` taken per unit) and `requires` (`{"skill": "skyrpg_skills:mining", "level": 10}`) |
| Ores | `data/skyrpg_mining/worldgen/{feature,placed_feature}/*.json`: vein size, count and height of Skyiron, Duskquartz, Emberite and Lumenite; `data/skyrpg_core/tags/block/tool_tier/<n>.json`: the tool tier each needs |

## Stats

Every stat (Health, Defense, Strength, Crit, Speed, Intelligence, Mining Speed, Fortunes, Wisdom, Magic Find) is defined in `core` (`StatTypes`). Mods add to a player's stats through `Stats.registerSource(...)`: skill levels via their `rewards`, items via equipment and set bonuses. They read totals with `Stats.get(player, StatTypes.X)`. The `stats` mod adds it all up and makes stats take effect. Wisdom stats (e.g. Farming Wisdom) give that much % more skill XP.

## Accessories

Four slots in a strip left of the armour hold an **Amulet**, **Cloak**, **Belt** and **Bracers**: in E, and on the creative inventory's Survival Inventory tab. The strip hides while the recipe book is open, because the book sits in the same place.

- **Getting them:** Odessa Quill's shop (Quill's Curiosities) in the hub sells all eight, for 800 to 24,000 coins. They're also in the SkyRPG creative tab, and admins can use `/items give <player> <id>` with `emberstone_pendant`, `starglass_amulet`, `wayfarer_mantle`, `duskwing_cloak`, `sunwoven_sash`, `tinker_belt`, `ironbark_bracers` or `duelist_vambraces`.
- **Wearing them:** right-click one to put it on. Like armour, it swaps with whatever was in the slot. You can also shift-click it in the inventory, or drop it on its slot, which lights up gold while you carry something that fits. They're stored with the player, not in the inventory, so they're kept on death and `/clear` doesn't touch them.
- **Stats:** a worn accessory gives its item stats through the "Accessories" source on the Stats page. Holding one in your hand gives nothing. The item tags `skyrpg_items:accessory/{amulet,cloak,belt,bracers}` decide which slot an item fits, so a data pack can add more.
- **On the player:** accessories are 3D models that everyone nearby sees. A cord and pendant; a cape that swings as you move (it replaces your own cape and hides under an elytra); a belt with a buckle and pouch; cuffs with guard plates, which also show on your hands in first person. Over chest armour each one switches to a wider fit that sits on top of the armour. They're hidden while you're invisible. An accessory without a worn texture (`textures/entity/accessory/<id>.png`) still gives stats but isn't drawn.
- **Armour depth:** worn Harvester, Prospector, Woodsman and Vanguard armour gets raised 3D detail that matches the icons: brims, crests, pauldrons, belts and cuffs, over re-shaded worn layers. It shows on players and mannequins and is client-side only.
- **Art:** `tools/art/generate_accessories.py` makes the icons and worn textures; its cube table must match `AccessoryModel.java`. `generate_armor_depth.py` makes the detail textures and the generated `ArmorDetailMeshes.java`, and must run after `generate_items.py`. `generate_accessory_slots.py` makes the empty-slot icons. All three take `--preview DIR`.
- **Shipping changes:** new accessory items and mixins need new jars on every client (`tools/package_friend.py`) and a server restart; a hot-swap won't pick them up.

Design notes: `docs/design/accessories.md`.

## Mining

Tamsin Deepholt, the **Mining Master**, stands in the hub (Deepholt Delving Co.). Her shop is paid in **Glimmer Shards** and **Glimmer Crystals** (9 shards), which every mined ore and node drops in large amounts, plus the new materials. Each pickaxe, node and armour set unlocks at a Mining level; locked offers show "Needs Mining N". The level cap is 50 and the curve is steep.

- **Nodes:** placeable ore blocks (Coal, Iron, Gold, Redstone, Skyiron, Duskquartz, Diamond, Emberite, Lumenite) bought from Tamsin. Mining one gives a little XP and its drop, then it goes dark and regrows after 20 seconds (Coal) up to 5 minutes (Lumenite). Depleted nodes can't be broken. Sneak and break with an empty hand to pick a node back up. They're a steady trickle; real levelling comes from ores in the world.
- **New ores:** Skyiron (y -24 to 64), Duskquartz (clusters, y -48 to -8), Emberite (deep, below y -8) and Lumenite (rare, below y -40), each in stone and deepslate. They give far more XP than nodes. Only chunks generated after an ore was added have it.
- **Pickaxe tiers:** Skyiron needs a Tier II pickaxe, Duskquartz IV, Emberite V and Lumenite VI (ores and nodes alike). With a weaker one the block doesn't crack at all and the action bar says what you need; the look-at box says it too. Vanilla pickaxes: wood/gold I, stone/copper II, iron III, diamond IV, netherite V. Every pickaxe's tooltip shows its tier. The gates are core block tags, `data/skyrpg_core/tags/block/tool_tier/<n>.json` (axes work the same way for hard woods).
- **Refining:** Raw Skyiron smelts into Skyiron Ingots (furnace or blast furnace), Emberite Chunks into Emberite Ingots (blast furnace only). Ingots and Duskquartz Shards pack 9 into a block and back at the crafting table. At the bench: Attuned Lumenite (1 Lumenite Crystal + 4 Glimmer Crystal) and a Prism (4 Prism Fragments).
- **Rare finds:** any ore or node can drop a **Prism Fragment** (1 in 300) or a **Heartstone** (1 in 3,000; three times rarer from nodes), announced with "✦ RARE FIND!". Mining Fortune multiplies shards: +100% per 100 fortune.
- **Auto-pickup:** blocks and ores you mine go straight into your inventory. If it's full, the rest drops at your feet and the action bar says so.
- **Pickaxes** (each made at the bench from the one before it, keeping its name and enchantments):

  | Pickaxe | Tier | Mining | Ability (right-click) |
  |---|---|---|---|
  | Quarry Pick | II | — | — |
  | Prospector / Delver's | III | 0 / 8 | — |
  | Skyiron | IV | 18 | Skyward Burst: +150 Mining Speed for 10s |
  | Duskbore Pick | V | 26 | Echo Sense: coloured trails point at the nearest ores within 12 blocks for 8s |
  | Emberheart | VI | 35 | Molten Core: 20s of smelted drops and +100 Mining Speed |
  | Lumen Breaker | VII | 45 | Lumen Pulse: every ore drops twice for 15s |
- **Armour sets** (4-piece bonuses do something, not just stats):

  | Set | Mining | Made from (per unit; helmet 5, chest 8, legs 7, boots 4 units) | Bonus |
  |---|---|---|---|
  | Delver | 8 | 2 Glimmer Crystal, leather, iron | Night Vision below y 40 |
  | Skyforged | 18 | 2 Skyiron Ingot, Glimmer Crystal (+1 Prism Fragment a piece) | every 50th ore: +200 Mining Speed for 10s |
  | Deepwarden | 26 | 3 Duskquartz, Skyiron Ingot, 2 Glimmer Crystal | immune to Darkness and Mining Fatigue; a free Echo Sense every minute |
  | Emberheart | 35 | 2 Emberite Ingot, 3 Glimmer Crystal (+1 Heartstone in the coat) | Molten Heart: smelted drops, fire immunity |
  | Lumenward | 45 | Attuned Lumenite, 2 Emberite Ingot, 4 Glimmer Crystal (+1 Prism a piece, +1 Heartstone in the plate) | rare finds twice as likely, light motes |

  "Smelted drops" (auto-smelt) only smelts raw ores (`data/skyrpg_mining/tags/item/auto_smelt.json`), not logs or sand.
- **Tamsin** sells the Quarry Pick, the Delver gear and the nodes (Duskquartz Node at 26), and buys every mining material, raw or refined.
- **Art:** `tools/art/generate_mining.py` writes the node and ore textures, blockstates, loot tables, tags, worldgen, recipes and skill XP. The gear is drawn by `generate_items.py`.

## Artisan Bench

SkyRPG gear is crafted at the **Artisan Bench**: craft one from a crafting table, 4 iron and 4 Glimmer Crystals, or buy one from
Saltmarsh Provisions (1,500). Right-click it (admins: `/bench`) for the crafting screen: a tab per skill, every recipe with its
ingredients (red where you're short) or the level it needs. Click crafts one, shift-click as many as you can afford.

- **Ingredients** come from your inventory and from inside your carried backpacks.
- **Upgrades:** many recipes take the previous tier (Delver's Pickaxe -> Skyiron Pickaxe, Satchel -> Trail Pack). The result
  keeps the old item's name, enchantments and contents.
- **Unlocks:** recipes unlock with skill levels; the level-up message lists the new ones.
- The plan for every skill's recipes is in `docs/design/progression.md`.

## Farming

**Mabel Thornfield** (Thornfield Produce) is the Farming Master: next to her vanilla stock she sells seeds, planters, a sprinkler,
compost and the Fieldhand gear as Farming levels unlock them, paid in **Dew**. Every fully grown crop you harvest has a 15% chance
to drop a **Dewdrop** (40% from melons and pumpkins); Farming Fortune multiplies it. 9 Dewdrops make a **Dew Vial**.

- **Special crops:** each grows only in the right conditions (the seeds' tooltip says which):

  | Crop | Grows | Seeds |
  |---|---|---|
  | Sunwheat | under open sky by day | 1 in 150 from wheat, or Mabel |
  | Frostberry | in the cold, or next to snow or ice | 1 in 200 from Sunwheat, or Mabel |
  | Emberpepper | near lava, or in the Nether | 1 in 50 from Emberite ore, or Mabel |
  | Moonmelon | only at night | 1 in 300 from melons at night, or Mabel |

  Vanilla bone meal doesn't work on them; **Rich Compost** does (and ignores the condition).
- **Starbloom** only grows in a Starbloom Planter, which needs a Starbloom Seed (1 in 2000 from Moonmelon).
- **Planters:** place anywhere outside the hub. Each grows its crop on a timer, from 45s (wheat) to 5 min (Starbloom). Right-click
  or hit a ripe one to harvest it. Sneak and hit with an empty hand to pick it up.
- **Sprinklers:** every 5 seconds the crops around a sprinkler grow a little and their farmland stays wet. Copper reaches 2
  blocks, Skyiron 3, Emberite 4.
- **Rare finds:** Lucky Clover (1 in 400) and Verdant Heart (1 in 4000) from grown crops, three times rarer from planters.
- **Tools:** Thornfield Sickle (replants what you harvest), then the Sunreaper, Frostbite and Cinder Scythes and the Moonlit
  Reaper. They harvest 3x3 or 5x5, can double crops, and have right-click boosts (Bountiful Gust: +100 Farming Fortune for 10s;
  Harvest Moon: double crops for 15s).
- **Armour:**

  | Set | Bonus |
  |---|---|
  | Fieldhand | no trampling |
  | Sunward | +25% Dew in daylight |
  | Frostgrove | speed on farmland, double crops |
  | Cinderfield | replant with any tool, fire immunity |
  | Moonharvest | growth aura, rare finds twice as likely |

- **Meals:** cooked at the Artisan Bench (Cooking tab). Each gives a 10-minute boost; a new meal replaces the last one.

  | Meal | Boost |
  |---|---|
  | Sunbread | Farming Fortune |
  | Frostberry Tart | Speed |
  | Miner's Chili | Mining Speed |
  | Moonmelon Juice | Intelligence |
  | Starbloom Tea | Health |

- **Selling:** Mabel buys the new crops and seeds. Dew and rare finds aren't in "Sell all", so they can't be sold by accident;
  use `/sell` for them.
- **Event:** Harvest Rush (`/event start harvest_rush`) gives points for grown crops, more for the special ones.
- **Art:** `tools/art/generate_farming.py` (crops, planters, sprinklers, items, loot tables, tags). The gear is drawn by `generate_items.py`.

## Foraging

Oswin Fernhollow, the lumberjack in the hub, is the **Foraging Master** (the `foraging` mod, `skyrpg_foraging`). His shop takes **Heartwood Splinters** (10% from any log you chop, 50% from a stump, times 1 + Foraging Fortune / 100) and **Heartwood Bundles** (9 splinters, crafting table) as well as coins.

- **Five new woods**, each with logs, planks (they work in every vanilla wood recipe), leaves and a sapling that only grows in its place. Bone meal doesn't work on them.

  | Wood | Axe tier | XP | Grows only... | Sapling from |
  |---|---|---|---|---|
  | Ironbark | III | 10 | anywhere with light | oak logs (1 in 200), Oswin (Foraging 8) |
  | Mistpine | IV | 14 | in a cold biome, or above y 120 | spruce logs in mountains and taigas, Oswin (18) |
  | Duskwillow | V | 18 | near water, at night | mangrove logs, oak logs in swamps, Oswin (26) |
  | Emberwood | VI | 24 | near lava, or in the Nether | crimson stems, ripe Emberpepper, Oswin (35) |
  | Starwood | VII | 40 | under open sky, at night | the bench: 2 Golden Acorns + 4 Heartwood Bundles (45) |

  Hard logs need an axe of that tier, like ores and pickaxes (vanilla iron axe is III, diamond IV, netherite V). Sapling sources are data: `data/<ns>/skyrpg/forage_drops/*.json`.
- **Wild trees:** all five also grow naturally, in chunks generated after this update: Ironbark in forests, Mistpine in snowy taigas, groves, snowy slopes and windswept hills, Duskwillow in swamps, Emberwood in badlands and savanna plateaus (and crimson forests and nether wastes), and the rare Starwood in cherry groves, meadows and flower forests. Which biomes is data (biome tags `skyrpg_foraging:has_tree/<wood>`), and how rare in `worldgen/placed_feature/<wood>_tree.json`.
- **Stumps** (the foraging nodes): Oak (3), Birch and Spruce (6), Ironbark (14), Mistpine (22), Duskwillow (30), Emberwood (38) from Oswin. Chop one for its log, a little XP and a 50% Splinter chance; it regrows in 30 s to 3 min. Sneak and hit it with an empty hand to pick it up.
- **Rare finds** from logs: a **Golden Acorn** (1 in 500) and **Elder Heartwood** (1 in 5,000), three times rarer from stumps.
- **Axes** (each made at the bench from the one before): Ironbark Hatchet (IV), Mistpine Splitter (V), Willowcleaver (VI), Emberbrand Axe (VII), Starwood Greataxe (VII). All have **Treefeller** (right-click: for 10 s the next log you chop fells the whole tree, every log counting as chopped), with a shorter recharge on each, and the better ones drop the leaves at once, replant the sapling and double logs.
- **Armour sets** (bonus): Barkguard (8; falls up to 6 blocks don't hurt), Mistwarden (18; Speed in forests), Willowshade (26; +30% Splinters at night), Emberbark (35; 15% double logs, fire immunity), Starwood (45; rare finds twice as likely). Oswin sells Barkguard; the rest are made at the bench.
- Logs a player placed give nothing extra; Treefeller never fells placed logs (your log cabin is safe).

## Fishing

Marina Saltwick, by the pond east of the hub square, is the **Fishing Master** (the `fishing` mod, `skyrpg_fishing`; Fishing is the fifth skill). Her shop takes **Tidescales** (about one catch in ten, times 1 + Fishing Fortune / 100; 9 make a **Tidescale Plate**) as well as coins.

- **New stats:** Fishing Speed (every 25 is one more level of Lure, up to four), Sea Creature Chance (5% to start), Fishing Fortune and Fishing Insight.
- **Every catch** gives Fishing XP, and can bring up an **Abyssal Pearl** (1 in 300) or a **Leviathan Scale** (1 in 3,000).
- **Sea creatures:** sometimes the bite is a creature, pulled out of the water at you: Drowned Sailors from the start, then Brine Slimes (Fishing 4), Reef Guardians (10), Riptide Raiders (15), Night Skimmers (20) and the rare Abyss Warden (30). Killing one pays Fishing XP and Tidescales. They leave after two minutes. The list is data: `data/<ns>/skyrpg/sea_creatures/*.json`.
- **Baits** (use one; ten minutes, one at a time): Worm Bait (+20 Fishing Speed; Marina sells it), Glowworm Bait (+40 speed, +2% sea creatures), Abyssal Bait (+6% sea creatures, 10% Double Catch). The better two are made at the bench.
- **Rods** (vanilla rods underneath, so Lure and Luck of the Sea work; each made from the one before): Driftwood Rod (sold), Reef Rod (8), Stormcaller Rod (18), Abyssal Rod (26), Leviathan Rod (35). The better ones add Double Catch and rare-find luck.
- **Armour sets** (bonus): Angler (sold; +10 Fishing Speed), Reefwalker (10; sold for Tidescales; breathe and swim easily underwater), Abyssal (26; more sea creatures, +25% Tidescales), Leviathan (40; rare finds twice as likely, 10% Double Catch).
- Catches count for events (the **Fishing Frenzy** auto event; scoring type `catch`, with `sea_creatures_only`) and contracts (goal type `catch`).

## Pets

The `pets` mod (`skyrpg_pets`): one pet per skill, which levels up with you.

| Pet | Skill | Stats (per level, Common) | Level 25 perk | Level 50 perk |
|---|---|---|---|---|
| Mole | Mining | +2 Mining Speed, +0.8 Mining Fortune | nodes regrow 15% faster | a free Echo Sense every minute |
| Bumblebee | Farming | +1.6 Farming Fortune, +0.1 Farming Insight | 15% more Dewdrops | crops within 4 blocks grow faster |
| Owl | Foraging | +1.6 Foraging Fortune, +0.2 Speed | falls 3 blocks further don't hurt | rare finds from logs 50% likelier |
| Wolf Pup | Combat | +0.8 Strength, +0.6 Crit Damage | 2% life steal | crits heal 3% |
| Otter | Fishing | +0.8 Fishing Speed, +0.08% Sea Creature Chance | 5% Double Catch | breathe and swim underwater |

- **Rarity** (Common, Uncommon, Rare, Epic, Legendary) multiplies the stats by 1 to 2. It's rolled when the egg hatches.
- **Eggs:** each master sells their skill's egg from level 10 (Tamsin for Glimmer Crystals, Mabel for Dew Vials, Oswin for Heartwood Bundles, Kestrel for Valor Marks, Marina for Tidescale Plates). The **Golden Pet Egg** (a random kind, Rare or better) is a world-boss drop.
- **Pet storage:** right-clicking a pet item moves it into your **pet storage** (a saved list, default 30 pets, `-Dskyrpg.petStorage=N`; synced to the owner only). If no pet is out it is also equipped. Eggs hatch straight into storage (a full storage refuses to hatch the egg, and a full one keeps the pet item in your hand). The equipped pet floats over your shoulder for everyone to see (it is the synced `skyrpg_pets:active` attachment; storage is `skyrpg_pets:storage`). It earns 25% of the XP you earn in its skill (5% from other skills) up to level 50; only the active pet earns XP and gives stats, and stored pets keep their level and XP. Pet items already in inventories stay items until right-clicked.
- **Commands:** `/pet` shows the active pet, `/pet away` unequips it into storage, `/pet storage` lists the stored pets, and ops get `/pet give <player> <type> <rarity>` (adds a pet to their storage).
- **Pets page (K menu):** a live 3D preview of the selected pet (slowly turning, leaning towards the mouse) with rarity badge, name, level and XP bar; on the right its skill, stats and perks, then **Pet storage** with a count like `12 / 30`: a grid of the active pet (green mark) and every stored pet with rarity border, icon, name and level. Click one to select it, then **Equip** (the old pet takes its slot in storage), **Unequip**, or **Take out** (moves it into your inventory as an item to trade or give away; blocked when the inventory is full). The page sends `skyrpg_pets:equip` (storage index), `skyrpg_pets:unequip` and `skyrpg_pets:take_out` (storage index), and the server re-checks everything. Page name for dev tools and screenshots: `menu-pets`.
- **Chat:** pet messages use the shared notice look (`Notice.Kind.PET`): the rarity gem and a rarity-coloured tag (`PET STORED`, `PET OUT`, `PET SWAPPED`, `PET AWAY`, `PET TAKEN OUT`, `STORAGE FULL`, `EGG HATCHED`, `PET LEVEL UP`, `YOUR PET`), the coloured name with a hover card of the full pet tooltip, and the level; Epic and Legendary hatches and the level 25/50 perks get an extra line.
- **3D pets:** the pet is drawn as a real 3D model (not its item) beside your right shoulder, for everyone, in third person and in the inventory and K-menu previews (never in first person). Each kind has its own look: the Mole wears a miner's helmet with a lamp, the Bumblebee has fuzzy translucent wings, the Owl big eyes and ear tufts, the Wolf Pup floppy ears and a wagging tail, the Otter holds a pebble or a fish. **Rarity forms:** Common is plain, Uncommon and Rare change colours and add small accessories (flower, bandana, sprig, pickaxe, goggles), Epic adds a bigger accessory, Legendary adds a crown or halo and a glow layer that is drawn fullbright. Animations: wing flaps, tail wag, ear twitch, blinking, a head that follows where you look (lagged), idle bob. The pet trails slightly behind when you move or turn (smoothed on each client, per player) and leans into the movement. Item icons are unchanged.
- **Rarity aura:** every ~4 ticks a client plays `skyrpg_vfx:pet_aura_<rarity>` at the pet's position for players within 32 blocks (needs the effect to exist in the vfx mod; missing effects are skipped).
- **Art pipeline:** `tools/art/generate_pets.py` holds each pet's cube table and paints the textures (`textures/entity/pet/<pet>_<rarity>.png`, plus `_glow.png`) and generates `pets/src/client/java/com/skyrpg/pets/client/model/*PetLayout.java` and the item icons from the same table. Edit the table there, never the generated Java. Code: `PetLayer` (render layer), `PetTrack` (smoothing), `PetModel` (parts and animation), `PetEffects` (aura).

## Mounts

The `mounts` mod (`skyrpg_mounts`): griffins, the pack's first mount. Design: `docs/design/the_wild.md`. Art: `tools/art/generate_griffin.py`.

- **The griffin** (`skyrpg_mounts:griffin`, spawn egg in the Spawn Eggs tab): an eagle's head, ruff and wings on a lion's body, in ten feather colours in four rarities (see the table below). Own 3D model (walking legs, wings folded in a zig-zag at rest, flapping in flight, spread and still when gliding). Entity height 1.9, 40 health, 7 attack damage.
- **Colours and rarity:** each new griffin (wild, and the Storm Griffin boss) rolls a colour by weight (98 in all). The whistle's name takes the rarity colour (Common keeps its feather colour), its tooltip starts with the rarity, and the Mounts page and `/mounts` show it. Rare and Legendary **wild** griffins give off sparkles (more for Legendary), and taming one announces it to everyone (Notice, "Drogashh tamed a LEGENDARY Prismatic griffin!"). The saved names and order of the first three colours never change (the entity stores the order, whistles the name), so old whistles and griffins still load.

  | Rarity (name colour) | Colour | Weight each | Chance each | Look |
  | --- | --- | --- | --- | --- |
  | Common (white) | Golden, Storm, Snow, Russet | 20 | 20.4% | Russet: red-brown hawk front, cream face, rusty lion body |
  | Uncommon (green) | Midnight, Verdant | 6 | 6.1% | Midnight: near-black body, blue-violet feathers, silver eyes; Verdant: teal-green feathers, mossy olive body |
  | Rare (blue) | Ember, Frost | 2.5 | 2.55% | Ember: charcoal body, flame-orange feathers, glowing wing tips and pulsing eyes; Frost: pale ice-blue, glowing crystal-blue wing tips |
  | Legendary (gold) | Prismatic, Astral | 0.5 | 0.51% | Prismatic: pearl white that cycles through the rainbow (6 s a loop) with glowing trim; Astral: deep navy with twinkling star specks and faint constellation lines |

  Animation is done at render time (entity textures can't use `.mcmeta`): `GriffinFx` (client) gives Prismatic's hue tint (`getModelTint` in `GriffinRenderer`) and the brightness of two emissive textures per glowing colour (`griffin_<color>_glow.png` and `_glow2.png`, drawn full-bright by `GriffinGlowLayer`: eyes and half the stars / wing tips, trim and the other stars). The K menu composes the same tint and glow into a dynamic texture (`GriffinShowcase`). The textures stay 128x128; the generator writes all of them (`tools/art/generate_griffin.py`, palettes `PALS`).
- **Wild griffins and nests:** the `griffin_nest` block (sticks, hay and a feather; also in the Mounts creative tab) is placed by the worldgen feature `skyrpg_mounts:griffin_nest` (placed feature of the same name: 1 in 3 per chunk, added to stony, jagged and frozen peaks and the windswept hills biomes). The feature only builds a nest (a plus of five nest blocks) where core's `Regions.typeAt(level, x, z)` is `"shattered_highlands"`; the world mod installs that lookup, so without it there are no nests. A nest keeps 1 to 3 wild griffins (fixed per nest) around it while a player is within 64 blocks: a random tick starts a scheduled-tick check every 5 to 8 seconds that spawns one when there are fewer than the cap within 40 blocks. They wander within 14 blocks of the nest, are neutral, and when hit they fight back with a claw and a leaping dive (5 to 18 blocks away, 1.5 times damage, with a glide). Wild griffins despawn when no player is near.
- **Taming:** feed a wild griffin raw beef, mutton, chicken, porkchop or rabbit. Each feeding has a 1 in 5 chance: hearts and the griffin turns into a **Griffin Whistle** in your inventory (smoke and a message when it fails; the meat is used either way). The whistle holds the name, colour, owner and two small random stats, **Speed** (+0 to +15%) and **Flight Stamina** (+0 to +25%, more seconds of flight), shown in its tooltip; its icon has the feather colour of the griffin.
- **The stable:** a list of whistles saved on the player (`skyrpg_mounts:storage`, default 20 mounts, `-Dskyrpg.mountStable=N`, synced to the owner only) plus the one that is out (`skyrpg_mounts:active`). Right-click a whistle to add it to your stable (whoever uses a whistle becomes its owner, so whistles can be traded) and, if no mount is out, call it. **Only one mount is out at a time**; calling another puts the first back. A mount goes back to the stable when you put it away, log off, leave its dimension, are more than 16 blocks from it without riding it, or 30 seconds after you get off. If it would die it is knocked out and flies home instead (health refilled), so a mount is never lost. It can't be hurt at all while ridden in the hub dimension.
- **Mounts page (K menu):** left a live 3D preview of the selected griffin in its colour (it spreads its wings when the mouse is over it) with colour, name, Speed and Flight Stamina; right the stat bars with the seconds of flight, then **Your stable** (`4 / 20`): a grid of mounts (the one out first, green mark). Select one, then **Call** (stands next to you; the one that was out goes back), **Put away**, or **Take out** (moves it into your inventory as a whistle to trade). The page sends `skyrpg_mounts:summon` (stable index, -1 for the active one), `skyrpg_mounts:away` and `skyrpg_mounts:take_out` (stable index); the server re-checks everything. Page name for dev tools and screenshots: `menu-mounts`.
- **Riding:** right-click your own griffin to mount (nobody else can: `/ride` also checks the owner). On the ground WASD walks and **jump takes off**. In the air WASD flies in the direction you face, **jump climbs**, **sneak descends** (sneak does not dismount in mid-air; on the ground it does), **sprint glides faster** (1.75 times). The riding player's client simulates the movement like vanilla's controllable mounts (`getRiddenInput`, `tickRidden`, `travel`; `isFlyingVehicle` is true so the server does not kick for floating); flight is held at hover when you let go. Riders take no fall damage and neither does the griffin; getting off in mid-air gives Slow Falling for 8 seconds.
- **Stamina:** a fraction synced on the griffin (14 seconds of flight at full, more with the Flight Stamina stat; climbing costs 1.5 times, gliding 1.3, descending 0.35, hovering 0.7). It refills on the ground in about 6 seconds. At zero the griffin glides down slowly at half speed and can't take off or climb until it is above 10%. The **Flight stamina** HUD widget (bottom centre above the hotbar, movable in the HUD editor) shows the bar while you ride.
- **Storm Griffin hook (world mod):** a griffin with the entity tag `skyrpg_storm_griffin` is the boss body: hostile (targets the nearest survival player, melee and dive), a weighted random colour like wild griffins, untameable, unrideable, persistent, and never turns into a whistle. The tag is checked live, so it can be added after spawning; scale it with the `minecraft:scale` attribute (the world mod uses 2.5) and drive any special attacks from outside.
- **Commands:** `/mounts` lists your stable, `/mounts call` calls your active (or first) mount, `/mounts away` puts it away. Ops: `/mounts nest` (a nest at your feet and a wild griffin), `/mounts griffin [color]` (a wild one; any of the ten colours, tab-completed), `/mounts claim` (the held whistle, like right-clicking it, for testing), `/mounts tame` (tames the nearest wild griffin within 16 blocks, for testing), `/mounts storm` (a test Storm Griffin: tag, scale 2.5, 400 health, a random colour like any griffin), `/mounts give <player> griffin [color]` (a whistle).
- **Code:** `GriffinEntity` (AI, taming, riding, stamina), `Mounts` (whistle data, stable, summon and put away), `GriffinNestBlock` and `NestFeature`, `MountPayloads`, and a mixin that stops sneak from dismounting a flying rider. Client: `GriffinModel` / `GriffinRenderer` (the cubes are the generated `GriffinLayout`), `MountsPage`, `StaminaWidget`.

## Party

The `party` mod (`skyrpg_party`): persistent parties. Design: `docs/design/party.md`. Wiki page: `party`.

- **Saved:** parties live in the world save (`PartyData`, a SavedData keyed by player UUID), so they survive logouts and restarts; offline members keep their seat and show as Offline with their last health and place. Up to 8 members, one leader; if the leader leaves the longest-standing member leads; a party that would shrink to one is disbanded.
- **Commands:** `/party` (`/p`) `invite <player>`, `accept [player]`, `deny [player]`, `leave`, `kick <player>`, `promote <player>`, `disband`, `list`, `chat [message]` (no message toggles your chat into party chat), and `/pc <message>`. Invites last 60 s and carry clickable [Accept] [Deny].
- **Chat:** party lines are `skyrpg_chat.party` components (header, body, sender) in the pack's Notice style; the chat mod files them under `ChatKind.PARTY` and the **Party** tab (All / Chat / Whispers / Party / SkyRPG). The party mod does not depend on `chat`.
- **Overlay** (`PartyWidget`, a core HudWidget, in the HUD editor): a row per other member with skin head, name, crown for the leader, SkyRPG health bar with numbers (red and pulsing under 25%), a dimension tag (Hub, Nether, The End) and a greyed "Offline". The server sends `skyrpg_party:state` twice a second while a party (or an invite) exists.
- **Party page** (K menu, `menu-party`): member cards (click one for a waypoint through `WaypointTarget`), Promote / Kick / Leave / Disband, an invite field, pending invites. The tab list marks party members (core `SkyRpgTab.registerMark`) and has a Party panel.
- **No friendly fire:** `ALLOW_DAMAGE` cancels damage between party members, resolving projectile and pet owners (`Parties.isFriendlyFire`, `Parties.sameParty` for other mods).

## Wiki

The wiki is in the game and on a website, both built from the same pages (`docs/wiki/pages/`, see `docs/design/wiki.md`). Every change to the game also changes its wiki page; after editing run `python tools/wiki/build.py`, which rewrites the in-game bundle in `wiki/src/main/resources/assets/skyrpg_wiki/wiki/` (never edit that by hand) and the website in `dist/wiki/` (a small multi-file site: `index.html`, `wiki.css`, `wiki.js`, `data/*.json`, `img/**`; every item also gets its own page with a big picture, 3D items rendered offline). Then `python tools/wiki/publish_pages.py` puts the whole site on GitHub Pages: https://makogai.github.io/minecraft-skyrpg/. To look at it locally, run `python -m http.server` in `dist/wiki`.

- **Open it:** press `K` and pick **Wiki**, or type `/wiki` (or `/wiki fishing`, with page names suggested as you type; `/wiki item-skyrpg_items-voidrender` opens an item's page). Page name for dev tools and screenshots: `menu-wiki`, `menu-wiki-fishing`, `menu-wiki-item-skyrpg_items-oak_buckler`, `menu-wiki-fishing#catches`, `menu-wiki-search-tide`.
- **Reading:** categories on the left (click a name to fold it), the page on the right with a scrollbar and the mouse wheel. Links jump to pages and parts of pages; items show their icon and a tooltip. The arrow buttons, `Alt+Left`, `Alt+Right` and the mouse's back and forward buttons walk through where you have been; Home goes to the start.
- **Search:** type in the box at the top left (or just start typing). Titles match first, then page summaries, then every word on the page. Enter opens the first result, Esc clears the search.
- **Look it up:** hover an item in any inventory or chest and press `H` (rebind it under Controls, SkyRPG, "Look up in Wiki") to open that item's page. Every item has one: a big picture of the item (in 3D for shields, bows and blocks), its stats, recipe, what it is used in, how to get it and its set.
- **Website:** the **Open on the web** button (shown when the bundle has a `web_url`) opens the website through Minecraft's link confirmation.
- **Optional:** the mod is client side only and registers nothing the server syncs (no blocks, items, components or packets), so players without it can still join. Code: `WikiPage` (the menu page), `WikiLayout` (blocks to boxes), `WikiData` (the bundle), `SkyRpgWikiClient` (command, key, reload).

## Chat

The `chat` mod (`skyrpg_chat`) restyles chat; details in `docs/design/chat.md`.

- Messages show a level badge (`[42]`, colour by tier) and a name with a hover card (level, skills, purse, slayers, pet, held item); click a name to start a `/msg`.
- Type `[item]`, `[offhand]`, `[armor]` or `[pet]` to show it with its tooltip; emotes like `:heart:`, `<3`, `:skull:`, `o/`, `:shrug:` (`/emotes` lists them).
- `/msg`, `/tell`, `/w`, `/r` and `/me` are styled; joins, leaves, deaths and the first-join welcome have themed lines.
- Styled lines are system messages, so there are no "Not Secure" marks. The server log still gets `[Not Secure] <Name> message`, which the Claude tools read. Console `say` and `tellraw` stay plain.
- Real pictures in chat: type `[s:gg]` for a sticker (`/stickers` lists the 15), paste a link to a PNG/JPEG/GIF on imgur, Discord, Reddit, Tenor or GitHub, or run `/share screenshot [file]` to show your newest screenshot to everyone (one per 30 s). Click a picture to open it full size, Esc closes. Claude can post them from `tellraw` with `{"text":"[sticker:gg]"}` or a link; see "Images in chat" in `docs/design/chat.md`. Stickers are generated by `python tools/art/generate_stickers.py`.
- Client settings (SkyRPG Settings, Chat): themed box, stripes, timestamps, compact repeats, mention highlight and ding, tabs, and Images (show images, images from links: known sites / any site / off, max image height, show stickers).

## Talents

Every skill level gives a **talent point** for that skill. Spend points in the SkyRPG menu (K -> Talents) on that skill's
perks: more shards or Dew, faster nodes and planters, better rare-find luck, Vein Echo, double ore, Night Vision deep down,
no trampling, replanting with any tool, Second Wind (survive a killing blow, once every 5 minutes)... Each skill has 5 to 8
talents; the strongest unlock at a skill level. Talents give stats and gear effects, so they stack with gear. A reset gives
the skill's points back: free once a day, then 2,000 coins. Trees are data: `data/<ns>/skyrpg/talents/<skill>.json`.

Every 5th skill level also pays coins (level x 100).

## Collections and the SkyRPG Level

The `progress` mod (`skyrpg_progress`, design in `docs/design/journal.md`). **Collections** count items you gain from the
world: normal pickups (not items a player threw or dropped), plus mining auto-pickup, fishing and lava-fishing catches and
farming and foraging helpers, which fire core's `ItemGains.GAINED` event. Tiers I to IX pay coins and 4 SkyRPG XP, some give a
small permanent stat (Mining Fortune, Defense...), and some unlock Artisan Bench recipes: a recipe can require
`"collection": {"id": "skyrpg_progress:coal", "tier": 3}` and shows "Requires Coal Collection III" while locked. K -> Collections
has a tab per category (Mining, Farming, Foraging, Fishing, Combat) with a progress bar per item. Collections are data
(`data/skyrpg_progress/skyrpg/collections/<id>.json`), generated by `python tools/builds/collections_data.py` (which also adds
the collection requirement to a handful of mid-tier recipes).

The **SkyRPG Level** is every 100 SkyRPG XP, with no cap: 4 XP per collection tier, 5 per skill level, 10 per slayer level,
20 to 60 for each dungeon's first champion kill, 15 for each other boss's first kill, plus quest rewards. Every 10th level pays
coins and plays the `level_up` effect. It is synced to everyone, shown in the Profile page header and used for the chat badge.
Test commands (op): `/collection add <player> <id> <amount>`, `/collection reset <player>`, `/skyrpglevel add|set <player> <xp>`.

## The Skyhaven Chronicle (questline)

K -> Journal is the main questline of the `progress` mod: 8 chapters and 59 quests that teach you the game like an RPG, from
saying hello to Hollis in the hub to the Ender Wyrm. **Every quest is a conversation.** An NPC with something for you has a
floating marker: a gold **!** (a quest to pick up), a grey **?** (you're working on theirs), a gold **?** (ready to hand in).
Right-click them and a dialogue screen opens (their face, typed-out text, choices as buttons: click, Space or the number keys;
Esc closes). Accept the quest, do the objectives, then **return to the NPC** to hand it in and get paid. The first chapter
walks you through the K menu, the minimap and map, coins and the bank, each trade's Master, the Artisan Bench and the Wildgate
to the survival world; later chapters introduce pets, collections, slayers, enchanting, dungeons, danger regions, the Deep,
the Nether, the ruins and the End. Quests with no giver (some late ones out in the world) start by themselves with a
"journal entry" dialogue that pops up when you are out of a fight.

The Journal's left side lists chapters with completion; the right side shows the quest's story, who gave it, the "Return to X"
step, objectives with progress bars, rewards, a **Track** button (points the compass at the next place, or at the NPC to see)
and **Re-read** buttons to read its dialogues again. A **Quest Tracker** HUD box shows the tracked quest.
Quests and dialogues are data, generated by `python tools/builds/quests.py` from `tools/builds/quests_*.py`
(`data/skyrpg_progress/skyrpg/quests/...` and `.../dialogue/...`). Objective types: `skill_level`, `collection`, `kill`,
`dungeon_clear`, `slayer_tier`, `craft`, `obtain`, `visit`, `talk` (finish that NPC's dialogue), `reach_level`, `journal`.
Commands: `/quest`, `/quest track [id]`, `/quest abandon <id>`; ops `/quest complete|set|reset|accept <players> <id|all>`,
`/quest dialogue <conversation id>` (read any conversation; `/quest set <you> skyrpg_progress:05_the_deep/down_we_go` jumps you
to a quest for testing). See `docs/design/journal.md` for the dialogue format.

## Contracts

Three contracts a day for the whole server (K -> Contracts, and a HUD box with progress): harvest, mine, chop, hunt Elites,
or deliver items (hand them in on the page). Everyone's progress counts together, and everyone who helped gets the full
reward: coins, skill XP, and sometimes Dew, Glimmer or trophies. Players who were offline get theirs when they next join.
An untouched contract can be rerolled once a day for 500 coins. Contracts are picked from templates among those someone
online has the level for. Templates are data (`data/<ns>/skyrpg/contracts/*.json`), and `/contracts new` deals a fresh set.

## Combat

Kestrel Vane, the weaponsmith in the hub, is the **Combat Master** (the `combat` mod, `skyrpg_combat`).

- **Mob levels:** every hostile mob in the survival world gets a level when it spawns: 1 near world spawn, one more per 120 blocks out (up to 30), +5 below y 0 and +10 below y -32; the Nether starts at 20 and the End at 40 (max 50). Each level above 1 adds 8% health and 5% damage. The numbers are data: `data/skyrpg_combat/skyrpg/mob_levels/default.json`. Mobs from spawn eggs and commands have no level.
- **Combat tag and warm-ups** (`core`, `com.skyrpg.core.combat`): dealing damage to, or taking it from, a hostile mob (`Enemy`) or another player tags you for 10 s (projectiles and abilities count for their owner; environmental damage, passive animals, creative and spectator players don't). `CombatTag.tag/isTagged/secondsLeft/clear` plus `onTagged`/`onUntagged` listeners are the API; death and logout clear it; the time is synced by `CombatTagPayload` to the "In combat" HUD widget (`CombatTagWidget`, registered with `HudManager`). Player-facing warps go through `Warmups.request(player, "the Hub", toggle, action)`: refused in combat, otherwise 5 s standing still (cancel on moving 0.3 blocks, damage, getting tagged, death, logout, dimension change; creative and spectator skip), countdown in the action bar, a particle ring and a sound per second. `/hub`, `/survival` and waystone travel use it (a waystone trip to the hub wraps `/hub` in `Warmups.runBypassing`). Physical portals (Wildgate), the hub void rescue and event teleports are left alone. Numbers: `data/skyrpg_core/skyrpg/combat/rules.json` (`tag_seconds`, `warmup_seconds`, `move_tolerance`). Design: `docs/design/combat_tag.md`.
- **Nameplates:** within 20 blocks, when you can see it, a levelled mob shows `[Lv 12] Zombie 85/100❤` (health in the same units as your health bar). Switch them off in Settings > HUD ("Mob nameplates").
- **Damage numbers:** each hit pops up in front of the mob for a moment; crits are gold with sparkles.
- **Rewards** on top of vanilla drops: 1.5 Combat XP per level; **Valor Marks** (25% + 1% per level, one more per 15 levels; 9 make a **Valor Seal**); a **Dread Fang** (1 in 400 from level 10+) or a **Nightmare Core** (1 in 4,000 from level 30+). Magic Find makes all of these likelier. Kestrel buys fangs and cores.
- **Elites:** naturally spawned hostile mobs are sometimes Elites (3% by day, 6% at night, 8% underground; never in the hub): three levels up, a gold "✦ Elite" nameplate and a glittering aura, 4x health, +50% damage, a little faster and bigger. Killing one also pays 50-200 coins, 40 Combat XP, three Valor Marks and an **Elite Trophy**, with a 1 in 50 chance of a Lucky Clover or a Prism Fragment. `/elite <mobs>` makes Elites on purpose. Elites have no custom name, so they still despawn.
- **World bosses:** every two to three hours with two or more players on (or `/worldboss start [id]`), a boss rises in the survival world about 20 blocks from a random player, announced a minute ahead with coordinates: **The Hollow King** (a giant wither skeleton) or the **Mother of Thorns** (a giant spider). Boss bar, ground slams, minions at two-thirds and one-third health, enraged at the end. Everyone who hurt it gets coins, Valor Seals and Combat XP (the top damager half again) and a shot at a Nightmare Core or Dread Fang; it leaves after 15 minutes. `/worldboss stop`, `/worldboss auto on|off`. Bosses are data: `data/skyrpg_combat/skyrpg/world_bosses/*.json`.
- **Swords** (each made at the bench from the one before): Vanguard Blade, Fangsteel Sabre (10; life steal), Stormedge (18; **Thunderclap**: lightning on every hostile mob within 5 blocks), Riftblade (26; Riftstep), Cinderfang (35; sets mobs on fire, life steal), Starfall Blade (45; **Starfall**: stars crash down on every hostile mob within 6 blocks).
- **Armour sets** (bonus): Vanguard (crit chance), Fangguard (10; 4% life steal; Kestrel sells it for Valor Marks), Stormplate (18; a shield after a big hit), Riftweave (26; 15% chance to dodge a melee hit), Cinderguard (35; thorns, burning hits, fire immunity), Starforged (45; crits heal you).
- **Shields** (`items`, `com.skyrpg.items.shield.Shields`; design `docs/design/shields_and_bows.md`, wiki page `shields`): seven, each made at the bench from the one before (Combat 3, 10, 18, 26, 30, 35, 45), built on vanilla's `blocks_attacks` component so they block like a shield (off hand, stats and effects count from there): Oak Buckler (50% blocked), Iron Kite Shield (65%), Skyiron Bulwark (75%; **Bash** knocks a blocked melee attacker back), Emberforged Aegis (80%; attackers catch fire), Frostward Barrier (80%; attackers slowed and frozen), Thunderguard (85%; each block stores a charge, sneak + use releases a lightning shockwave), Astral Aegis (90%; reflects projectiles). Effects are gear effects (`skyrpg_items:shield_bash`, `shield_ignite`, `shield_frost`, `shield_charge`, `shield_shockwave`, `shield_reflect`) in the definitions.
- **Bows** (`SkyBowItem`, `Bows`; wiki page `bows`): seven, the first ranged weapons, vanilla draw and arrows as ammo, but the arrows deal SkyRPG damage: the bow's `damage` stat scaled by draw, then the shooter's Strength and Crit Chance / Crit Damage in `CombatStats`, via `SkyArrows` (core). Damage numbers, Combat XP, loot, slayer progress and kills all credit the shooter. Hunter's Bow, Recurve Bow (draws 30% faster), Skyiron Longbow (pierces one extra mob), Ember Bow (fire arrows that burst), Frostbite Bow (slows and freezes), Stormcaller Bow (every third full-draw shot calls lightning), Starfall Bow (three homing star arrows). Each has a `"bow"` object in its definition (draw_speed, pierce, arrows, burst, freeze, lightning, trail...). Bow enchants (Deadeye, Longshot...) apply to them.
- **3D art:** `tools/art/generate_shields_bows.py` (with `sb_common.py`) writes the fourteen items' definitions, models (cuboid `elements`, `<id>_blocking`, `<id>_pulling_0..2`) and textures into `items/.../assets/skyrpg_items/`, and nothing else; `--preview DIR` renders them offline. Glow layers use `light_emission`; the Astral Aegis glow is animated.
- **World mobs** of our own, each a new kind on a vanilla body with its own look, tricks and drops. They get levels
  and can be Elites like any natural spawn, and several count for slayer quests.

  | Mob | Where | What it does | Drops | Counts for |
  |---|---|---|---|---|
  | Glimmer Crawler | caves below y 0 | a quick spider with glowing crystals | Glimmer Shards, rare Prism Fragment | Broodmother |
  | Bog Hag | swamps at night | a witch that calls bog slimes | Hag's Charm, sometimes a Duskwillow sapling | |
  | Frost Revenant | snowy biomes | a stray whose arrows freeze you | Rime Shards | Bone Marshal |
  | Ember Imp | badlands, the Nether | a small, quick blaze | Ember Core, sometimes an Emberwood sapling | |
  | Grove Sentinel | forests at night | slow and tough; its hits root you | Heartwood Splinters, rare Golden Acorn | |
  | Tide Lurker | rivers and beaches at night | hauls you towards it with a tide hook | Tidescales | Grave Tyrant |
  | Hollow Wisp | plains and meadows at night | flits through walls, fades at dawn | Wisp Essence | Void Stalker |
  | Gravecaller | dark forests at night | raises zombies | sometimes Tyrant's Ash | Grave Tyrant |

  Spawns are in `WorldMobs.java`, drops are loot tables (`data/skyrpg_combat/loot_table/entities/*.json`). The slimes
  and zombies they call are farmed (no rewards).

## Slayers and spawners

**Morwen Ashgrave**, the **Slayer Master**, has the market stall next to the Curio Dealer (the `combat` mod). Her screen
has two tabs.

- **Slayer quests** (like Maddox on Hypixel): pick a line and a tier and pay the fee, then kill that line's mobs anywhere
  in the survival world. Each kill fills the quest bar by 1 + level / 5. When it's full, the boss rises next to you, with
  a boss bar and a clock: beat it in time (friends can help) for slayer XP, Combat XP, tokens and, from tier III, a
  chance at a rare drop. Tier IV has a tiny chance at a Golden Pet Egg. Dying or running out of time fails the quest,
  and the fee is gone. The quest and its timer show on the HUD ("Slayer Quest" in the HUD editor). If you log off,
  change dimension or go to the hub, the boss leaves and comes back with the time that was left.

  | Line | Kills | Token | Rare (III+) | Each level |
  |---|---|---|---|---|
  | Grave Tyrant | zombies, husks, drowned, zombie villagers | Tyrant's Ash | Plague Heart | +3 Health |
  | Broodmother | spiders, cave spiders | Brood Silk | Venom Gland | +0.5% Crit Chance |
  | Bone Marshal | skeletons, strays, bogged, wither skeletons | Marrow Shard | Marshal's Sigil | +1 Strength |
  | Void Stalker | endermen, endermites | Void Fragment | Null Core | +2 Intelligence |

  **Every tier is a new form of the boss** with its own 3D model (each tier visibly bigger, more armoured and glowing),
  and adds one attack and one defence on top of the tiers below. The slayer menu lists them per tier:

  | | Grave Tyrant | Broodmother | Bone Marshal | Void Stalker |
  |---|---|---|---|---|
  | I | Risen Tyrant: Grave Slam / Rotting Hide (projectiles do half) | Brood Mother: Web Shot / Chitin Plates (-20% melee) | Bone Sergeant: Volley / Bone Plating (no knockback) | Void Wanderer: Void Bolt and blink / Phase Step (dodges 1 in 5) |
  | II | Plague Tyrant: Raise the Dead / Life Drain, enrage | Venom Matron: Pounce / Brood Shield (-70% with 3+ brood) | Bone Captain: Arrow Rain / Tactical Retreat | Void Seeker: Void Grasp (pull) / Null Shield |
  | III | Dread Tyrant: Gravestones / Bone Armour after slams | Silk Empress: Egg Sacs and Brood / Silk Cocoon (heals; cut her out) | Bone Commander: Piercing Shot (telegraphed) / War Banner | Void Reaver: Void Beacon / Mirror Images (echoes) |
  | IV | Tyrant King: Plague Aura and Tomb Prison / Undying (rises once) | Brood Queen Arachnia: Silk Prison and Venom Bite / Venom Blood | Bone Marshal Supreme: Legion / Bone Shield | Void Sovereign: Rifts and Void Collapse / Void Siphon |

  Tiers I-IV: fees 1k / 5k / 20k / 60k, quest bar 100 / 400 / 1,200 / 3,000, boss health 2,000 / 6,000 / 18,000 / 50,000,
  3 / 3 / 4 / 5 minutes, 5 / 25 / 100 / 500 slayer XP, slayer level 0 / 2 / 4 / 6 needed. Slayer levels 1-9 at
  5 / 15 / 50 / 120 / 300 / 700 / 1,500 / 3,000 / 6,000 XP. Bosses above 5,120 health take less damage instead (their
  nameplate and damage numbers show the real numbers). Lines are data: `data/skyrpg_combat/skyrpg/slayers/*.json`.
- **Slayer gear** (the bench's **Slayer** tab): at slayer level 3 an accessory from 32 tokens (Tyrant's Amulet, Broodweave
  Cloak, Marshal's Belt, Voidbound Bracers); at level 6 a legendary sword from the rare drop, 128 tokens, Valor Seals and
  Dread Fangs (Gravebane, Broodfang, Marrowblade, Voidrender), each +50-75% damage against its own line.
- **Slayer armour** (the bench's Slayer tab, one 4-piece set per line, like Hypixel's): **Gravewarden** (Grave Tyrant),
  **Silkstalker** (Broodmother), **Marshal's Bonemail** (Bone Marshal) and **Voidwalker** (Void Stalker). Boots / helm /
  leggings / chestplate unlock at slayer level 2 / 4 / 5 / 7, from 40 / 64 / 96 / 160 of the line's tokens plus Valor Seals
  and a vanilla filler; the chestplate also takes one rare drop (Plague Heart, Venom Gland, Marshal's Sigil, Null Core).
  The full set takes 30% less damage from that line's mobs and boss, deals +10% to them (`bane`) and earns +25% slayer XP
  for the line. All four have raised 3D detail with glowing parts and a VFX aura; Voidwalker's void shards orbit you.
- **Spawners** (Philip's ladder, the way Cosmic does it): Rabbit -> Chicken -> Pig -> Sheep -> Cow -> Zombie -> Spider ->
  Skeleton -> Enderman -> Blaze, 5,000 to 400,000 coins. Each one unlocks after 5,000 lifetime kills of the mob before
  it (the game's own statistics), in order. A bought spawner places like a block; breaking one you placed gives it back
  (no XP). Mobs from spawners are **farmed**: normal loot and a quarter of the Combat XP, but no Valor Marks, rare drops,
  Elites or slayer progress. The ladder is data: `data/skyrpg_combat/skyrpg/spawners/ladder.json`.
- Admin: `/slayer` (open the screen), `/slayer spawners`, `/slayer start <players> <line> <tier>` (free),
  `/slayer fill <players>` (summon the boss now), `/slayer clear <players>`, `/slayer xp <players> <line> <amount>`.

## Enchanting

**Seraphine Vael**, the **Enchanter** (the tower in the hub), and every **enchanting table** open **Arcane Enchanting**
(the `enchants` mod). Full design: `docs/design/enchanting.md`.

- **Enchant tab:** choose a piece of gear, an enchant and a level, then **Bind** (coins, XP levels, Lapis; the rarer ones
  Runic Dust and Prism Fragments). A table offers levels by the bookshelves around it (I 0, II 4, III 8, IV 12, V 15);
  Seraphine's library counts as 15. Higher enchant levels also need your **Enchanting** level.
- **71 enchants** for swords, bows, armour (and per slot), pickaxes, axes, hoes, any tool, rods and wands: stats (Keen,
  Aegis, Vitality, Prosperity...), other mods' effects (Bloodthirst, Brambleguard, Evasion, Reseeding, Forgefire,
  Twinbark...) and their own (Cleave, Frostbite, Stormcaller lightning, Momentum, Reaper's Mark, Splitshot, Starshot...).
- **Ultimates** (one per item, tomes only): Soulrend (Grave Tyrant), Final Stand (Broodmother), Starshot (Bone Marshal),
  Rift Echo (Void Stalker), Phoenix Ember (world boss; cheats death), and the gathering ones Motherlode, Heartwood
  Surge, Bountiful and Leviathan's Favor (very rare finds while mining, foraging, farming and fishing).
- **Tomes** drop from monsters (rarely), slayer bosses and the world boss, and turn up while gathering. In the **Tomes**
  tab: apply one, **combine** two identical ones into a higher level (the only way past the table's V), or **salvage** it
  into Runic Dust.
- **Enchanting skill** (Intelligence and Magic Find per level, three talents) and the **Codex** (every enchant you've
  found; first discoveries pay Enchanting XP and show a banner).
- Data: `data/skyrpg_enchants/skyrpg/enchants/*.json`, `.../enchanting/rules.json`, `.../tome_drops/*.json`.
- Admin: `/enchants`, `/enchants tome <players> <enchant> [level]`, `/enchants add <players> <enchant> <level>`,
  `/enchants clear <players>`, `/enchants codex <players> all|reset`, `/enchants test <player> ...`.

## World dungeons and waystones

The `world` mod. Full design and every marker kind: `docs/design/dungeons.md`.

- **Dungeons generate in the survival world** from the seed, two floors each, joined by a stairwell:
  - **Forgotten Crypt** (forests and plains, Lv 12-19): the Bone Knight champion, then **the Crypt Warden**.
  - **Spider Hollow** (under dark forests and pale gardens, Lv 19-26): the Silk Matron, then **Queen Arachne**.
  - **Drowned Temple** (oceans, Lv 26-33): the Abyssal Herald, then **the Tidemother**.
  - **The Dwarven Halls** (rare, under mountains, hills and forests, Lv 32-41): a ruined dwarven city under a surface gate. The
    Forgeguard, then **the Iron Thane**. Its Abandoned Mine Camps (common, in caves) sometimes hold a map to it.
  - **The Infernal Citadel** (rare, in the Nether, Lv 44-52): a blackstone fortress on the lava sea, built over lava bridges and into cliffs. The
    Infernal Knight, then **the Magma Tyrant** on the Molten Throne. Drops Hellforged scrap and the Tyrant's Core.
  - **The Ruined Citadel** (rare, in mountain and peak biomes, Lv 54-58): a three-floor castle ruin sunk into the mountain: the Outer Ward, the Keep and the Throne
    Room, stacked by stairwells. Armoured knights and archers, castle guards, the Knight-Captain, then **the Fallen Knight**. Drops the Fallen Crest and a Citadel Crest Key for the vault.
  - **The Catacombs** (very rare, deep underground at y -60 to -40, Lv 59-62): a labyrinth of mazes, crypts, ossuaries, lever and plate puzzles and traps, reached by a sealed stair
    found in the Magma Rifts. Three Bone Wardens each drop an Ossuary Key for the three seals of the tomb, then **the Bonelord**. Drops the Bonelord's Phylactery.
  - **The Astral Observatory** (rare, on the outer End islands, Lv 60-65): a purpur and glass observatory on stilts, with star-map halls, archives and
    telescope rooms. The Astral Sentinel, a climb up the Ascent, then **the Star Warden** in his glass dome. Drops Astral Shards and the Star Warden's Core.
- **The Nether biomes** (new Nether chunks only): the **Ember Forest** (Emberwood trees on smouldering ash, Ember Imps), the **Bone Sea** (soul sand and bone dunes with half-buried ribcages, Bone Giants) and the **Obsidian Spires** (obsidian pillars, glowing void cracks, rich Hellforged veins). **Hellforged ore** (netherrack, blackstone, basalt; Tier V pick) drops Hellforged Scrap; blast it into Hellforged Ingots for the Hellforged armour and the Hellforged Pickaxe (Tier VI, Artisan Bench). `/locate biome skyrpg_world:ember_forest` (or `bone_sea`, `obsidian_spires`).
- **The End biomes** (new outer-End chunks only): the **Void Gardens** (purple void trees with glowing caps, glowing Void Blooms), the **Shattered Isles** (small floating rubble islands of end stone and amethyst with gaps between them) and the **Astral Fields** (Starstone, end stone with stars in it, and Astral Crystal clusters that drop **Astral Shards**, the material for the Ender Wyrm altar). `/locate biome skyrpg_world:void_gardens` (or `shattered_isles`, `astral_fields`).
- **Inside:** dozens of rooms per dungeon, mob packs in waves, Elites, traps (arrows, spikes, flames, poison), lever
  and pressure-plate puzzles, secrets behind cracked walls, and a locked **vault**. Vault keys drop from Elites and
  the boss.
- **Rewards:**
  - **Every chest is per player:** each player rolls their own loot, plus coins and **Delving** XP (a new skill).
  - **Bosses:** a champion or boss pays coins and Delving XP to everyone who fought it on every kill, plus a
    first-clear bonus.
  - **Reward Chest:** each boss leaves one that rolls once more for every few packs, secrets and champions the party
    cleared this run.
  - **Gear and tomes:** dungeon gear (Cryptbreaker, Warden's Shroud, Arachne's Fang, Widowsilk Cloak, Tidecaller Blade,
    Tidemother Pendant) drops rarely and is craftable from boss tokens at the Artisan Bench (Delving 5, 12 and 20).
    Bosses also drop enchant tomes and treasure maps to the next dungeon.
- **Scaling and resets:** boss health scales with the party. Dungeons reset three in-game days after they're used up,
  and only when nobody is nearby.
- **Discovery:** walking up to an entrance adds it to your map.
- **Waystones** stand at every dungeon entrance, in **Wayshrines** and next to every village bell. Right-click to attune,
  right-click again to travel to any attuned waystone or Skyhaven (1 XP level per 1,000 blocks, max 5).
- Building: `python tools/builds/dungeons/<name>.py` (`tools/builds/dungeon_kit.py` is the builder). Admin:
  `/dungeon info|reset [radius]`, `/dungeon spawnboss <id>`, `/dungeon fights`, `/dungeon explorer reset <player>`,
  `/waystone list`, and vanilla `/locate structure skyrpg_world:...`.

## Danger regions

Red on the map: the Blightlands, the Ashen Wastes, Frostgrave, the Mire, the Haunted Moors and the Shattered Highlands,
placed from the seed about every 1,600 blocks by biome. Monsters inside are +12 to +20 levels, the land is corrupted,
regional monsters roam, each type has its own fog colour, and kills drop essences and the rare Dread Crystal for region
gear. `/region` lists the nearby ones. Design: `docs/design/danger_regions.md`; data from `tools/builds/regions.py`.

**Hearts and the Blood Moon (The Wild, `docs/design/the_wild.md`):** every region has a shrine at its centre (built with
the land) and a boss: the Blight Lord, Ash Colossus, Frost Revenant King, Bog Mother, Lantern Wraith and Storm Griffin.
Wake one with a Heart Sigil (32 of the region's essence + 4 Blood Shards, Artisan Bench) used at the shrine; any number of
regions can have a boss up at once. About one night in eight (12%, never twice in a row, at least every twelve nights, a
player online) the overworld has a **Blood Moon**: red sky and a HUD timer, hostile mobs +10 levels, Bloodbound hordes
(a region's own monsters, double the cap, inside a region), Elites three times as likely, double drops, Blood Shards, and
each heart wakes its boss when someone comes within 64 blocks. Ops: `/bloodmoon start|stop|status`,
`/regionboss spawn|awaken|list`, `/region nearest <type>`. The Moonblood Charm (Blood Shards) is stronger at night.
Data: `tools/builds/wild.py` (bosses, loot, recipes, sell prices), art `tools/art/generate_wild.py`; code in the world
mod (`bloodmoon` package, `RegionBosses`, `HeartShrine`).

**Layers (The Deep):** the overworld's caves get stranger with depth. The **Glimmer Caverns** (y 0 to 40, +4 levels),
**Fungal Depths** (-20 to 0, +7), **Dwarven Halls** (-40 to -20, +10) and **Magma Rifts** (-64 to -40, +14) each restyle
the cave surfaces in newly generated chunks (glimmerstone and glowing clusters; mycelium, glowcaps, giant mushrooms and
poison Spore Puffballs; deepslate bricks, pillars, lanterns and runestones; basalt, magma and lava pools). Underground in
a layer you get an arrival title, the layer's monsters (Lost Miners, Sporelings, Dwarven Constructs, Fire Beetles...)
spawn in dark spots nearby, and kills drop Glimmer Geodes, Glowcap Spores, Dwarven Cogs and Rift Embers. `/layer` says
where you are. Design: `docs/design/the_deep.md`; data from `tools/builds/deep_layers.py`, art from
`tools/art/generate_world.py`.

## Auto events and the bank

- **Auto events:** while two or more players are online and nothing is running, a random event (Timber Rush, Monster Hunt,
  Harvest Rush, Glimmer Rush) starts about every 60 to 90 minutes. Turn it off with `/event auto off` (`/event auto now`
  starts one).
- **Glimmer Rush:** a mining event. Ores and nodes score points, and rarer ores score more.
- **Bank interest:** your first login each day pays 1% of your bank balance (up to 5,000 coins) into the bank.

## Weapon art

`tools/art/generate_weapon_art.py` draws 32px swords, pickaxes, spears and bows (vanilla's and SkyRPG's) and a 128px shield
into core's built-in pack `resourcepacks/skyrpg_weapon_art` (Settings > Interface > **Detailed weapon art**, on by default;
off brings back the 16px look). The strongest SkyRPG weapons and tools are animated (sheens, lightning, embers, stars,
runes, pulsing gems) and the biggest blades are held larger. Item definitions can add **held particles**
(`"held_particles": [{"particle": "minecraft:electric_spark", "chance": 0.4}]`), shown around the hand of anyone holding
them (Settings > Gameplay > Weapon particles). The combat mod adds **spawn eggs** for its eight world mobs (creative Spawn
Eggs tab); mobs from eggs and commands get a level like natural ones.

## Gear effects and boosts

Set bonuses, items and timed boosts can grant **effects** as well as stats (core's `GearEffects`). For example, a set's
`"effects": {"skyrpg_farming:no_trample": 1}`, or an item's `"effects"` plus `"perks"` lines for its tooltip. The items mod works
out who has what, and each skill's mod makes its own effects work. The **boost** ability type and meals give stats and effects
for a while; they're saved with the player.

## Visual effects

The `vfx` mod draws particle effects from JSON: `data/<ns>/skyrpg/vfx/<id>.json` (what to spawn) and
`data/<ns>/skyrpg/vfx_bindings/<id>.json` (when: on mobs, full armour sets, held items). Content comes from
`tools/builds/vfx_content.py`. Try them with `/vfx play skyrpg_vfx:level_up`, `/vfx list`, `/vfx reload`. Players turn it down in
Settings > Effects > Effects quality. Mods play effects with `VfxClient.play(id, x, y, z)` / `Vfx.play(...)`. Formats and how to add
VFX: [docs/design/vfx.md](docs/design/vfx.md).

## Backpacks

Three backpack slots sit in a strip right of the inventory, in E and on the creative inventory's Survival Inventory tab. Like the accessory strip, it hides while the recipe book is open.

- **Sizes:** Satchel (9 stacks), Trail Pack (18), Ranger's Pack (27), Voyager's Pack (36) and Colossal Pack (54). Saltmarsh Provisions sells the Satchel (500) and Trail Pack (2,500), and the Satchel has a crafting-table recipe. The bigger packs are upgrades at the Artisan Bench, and keep what's inside (even while worn in a backpack slot).
- **Using them:** put a backpack in a slot (click, or shift-click it from the inventory). Right-click the slot to open it, or press **B** to open the first one (rebindable under SkyRPG in Controls). Backpacks can't go inside backpacks.
- **Storage:** contents are stored on the backpack item, so a pack keeps them wherever it goes. Carried backpacks are stored with the player, so they're kept on death. The tooltip shows how full a pack is.

## Utilities (minimap, map, look-at info)

The `utils` mod is client-only: friends need its jar, the server doesn't. Everything below is a HUD box you can
move, resize or hide in the HUD editor (Settings -> Edit HUD layout), with switches under Map and Info on the Settings page.

- **Minimap** (top right): the area around you, with other players as faces and you as an arrow. Zoom with **=** and **-**.
  It can turn with you ("Turn the minimap with you"). Underground and in the Nether it switches to **cave view**, the floor
  around you, darker the further down.
- **World map** (**M**): everything you've explored in this dimension. Drag to move, scroll to zoom towards the mouse,
  **C** to follow yourself. Explored map is saved per server and dimension in `skyrpg/maps/` in the game folder.
  Hover an icon to see what it is; zoomed in, names show under the icons. **Right-click** the map to drop a waypoint
  (name and colour); click or right-click any icon (waypoint, dungeon, waystone) for **Track**, **Share in chat** and,
  for your waypoints, **Remove**.
- **Waypoints:** named, coloured points per dimension, saved per server. Make them with `/waypoint add <name> [x y z]`
  (alias `/wp`), the world map, or just die ("Last death" is added for you). `/waypoint remove|list|track|untrack|share`.
  They show on the minimap (with an arrow on its edge when off the map), as a diamond with name and distance in the world
  (an arrow on the screen edge when off-screen), and the one you **track** also gets a compass ribbon at the top centre.
  **Share** posts `[waypoint: Name x y z]` in chat; players with the mod get clickable Add / Track buttons. Settings are
  under Map -> Waypoints. Servers can point you somewhere with core's `WaypointTarget` payload. See `docs/design/waypoints.md`.
- **Colours and icons:** blocks are drawn in their own texture's colour (tinted like in game), so paths, roofs and flowers show.
  Every NPC is on both maps with their face, name and role, even when they're too far away to be loaded. The hub opens
  zoomed in. Mods add their own icons through core's `MapMarkers` (a provider hands out markers per dimension: position,
  item / face / dot icon, name, tooltip lines, colour); NPCs and waypoints use it.
- **Look-at info** (top centre): what you're looking at, up to 12 blocks away. For blocks: name, mod, crop growth, whether
  a mining node has regrown, and whether your tool can harvest it. For mobs and players: health (and a player's ping). For NPCs:
  their role.
- **Info box** (under the minimap): coordinates and facing, biome, day and time, and FPS (off by default).

## Commands

- `/coins [player]`, `/pay <player> <amount>` (or the **Pay** page in the SkyRPG menu: pick someone online, type an amount like `2.5k`, Enter; big payments ask to confirm)
- `/sell` (held stack), `/sell all` (all of the held item), `/sell inventory` (everything sellable), `/worth`
- `/skills [player]`
- `/hub`, `/survival` (5 s warm-up, none in combat), `/stats [player]`, `/items list`, `/quest` (see The Skyhaven Chronicle)
- Admin: `/slayer ...` (see Slayers), `/enchants ...` (see Enchanting), `/coins give|take|set <players> <amount>`, `/skills give <players> <skill> <xp>`, `/skills reset <players>`, `/hubadmin save|rebuild|setspawn`, `/items give <players> <id> [count]`, `/skills set <players> <skill> <xp>`
