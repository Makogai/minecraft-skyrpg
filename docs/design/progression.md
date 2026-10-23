# Progression plan: crafting and the skills

The plan for making SkyRPG nicer to play: crafting, and a full ladder for every skill. Decisions are made (section 12);
names, numbers and recipes can still change as we play. Everything stays data-driven (JSON), so balance changes
never need a recompile. Built for friends first, with the public-server version planned in section 13.

## 1. Where we are now

- **Gear is bought, not made.** The four starter sets have vanilla crafting-table recipes. Everything from the Mining
  update (pickaxes, nodes, the four new sets) can only be bought from Tamsin, and backpacks only from Dorrin.
- **Materials have one use each.** Glimmer, Raw Skyiron, Emberite Chunks, Lumenite Crystals, Prism Fragments and
  Heartstones are only shop currency. They can't be smelted, sold, or crafted into anything but crystals.
- **The new armour is thin:**
  - The four mining sets are recolours of the Prospector and Vanguard cuts, with no raised 3D detail.
  - Their set bonuses are only extra stats, nothing you'd notice while playing.
  - Deepwarden costs only crystals and skips the material ladder entirely.
  - Emberheart's bonus is called "Heart of the Mountain", which is a Hypixel name; we should rename it.
- **Levelling is far too slow for us.** Skills use Hypixel's curve (Mining 20 = 522k XP, Mining 30 = 8M), but our
  blocks give 1 XP (stone) to 25 XP (Lumenite). At a realistic 4–6k XP an hour, Mining 20 is about 100 hours.
- **Farming has no progression of its own:** vanilla crops, the Harvester set and hoe from Mabel, and stat rewards per level.

## 2. The loop every skill follows

```
gather  ->  refine  ->  craft  ->  better gear  ->  gather deeper / faster
   ^                                  |
   +----- skill levels unlock --------+
```

1. **Gather** raw materials in the world (main XP), from placeable nodes/planters (slow XP, no travel), and
   rare finds (chance drops that gate the top tiers).
2. **Refine** them: smelt, compact (9 -> 1) or attune at the Artisan Bench.
3. **Craft** gear at the Artisan Bench. Recipes unlock at skill levels; locked ones are visible with "Needs Mining 26".
4. **Better gear** lets you reach the next material. Mining uses pickaxe tiers (section 4.3); farming uses crops that
   only grow for higher-level farmers or need special conditions.

**Buying is a shortcut, not the main path.** Masters sell nodes/planters/stumps, consumables, and tier 1–2 gear.
Tier 3+ gear is crafted only. Masters also **buy** materials, so gathering earns coins as well.

### Tiers (shared by every skill)

| Tier | Skill level | Rarity | Made from |
|---|---|---|---|
| T1 | 0 | Uncommon | Vanilla materials, at a crafting table |
| T2 | 8 | Uncommon | The skill's currency (Glimmer, Dew) + vanilla materials |
| T3 | 18 | Rare | First custom material (Skyiron, Sunwheat) + a little rare find |
| T4 | 26 | Epic | Second custom material (Duskquartz, Frostberry) |
| T5 | 35 | Epic | Third custom material (Emberite, Emberpepper) + rare finds |
| T6 | 45 | Legendary | Top material (Lumenite, Moonmelon / Starbloom) + several rare finds |

### Pacing (fix the curve)

Skill JSON already supports a custom `levels` curve. Proposal: a **friends curve** for all skills, generated smoothly
from these targets. The XP per block stays as it is.

| Level | Total XP | Rough playtime |
|---|---|---|
| 10 | 4k | 1 hour |
| 20 | 50k | 8–10 hours |
| 30 | 250k | ~30 hours |
| 40 | 750k | ~60 hours |
| 50 | 2M | ~130 hours ("extremely hard") |

Before locking it in, we measure real XP per hour on staging (an hour of real mining and farming, or a Carpet bot).

## 3. Crafting: the Artisan Bench

Vanilla's crafting grid can't ask for "64 Glimmer Crystals", can't gate by skill level, and hides recipes. Our shop
screen already draws ingredient icons with counts, red when you're short, and "Needs Mining N" locks. The bench
reuses all of that.

- **Block:** the *Artisan Bench*. Crafted from a crafting table, 4 iron and 4 Glimmer Crystals; placeable anywhere.
  One stands next to each master in the hub.
- **Screen:**
  - **Tabs:** Mining, Farming, Foraging, Combat, General (backpacks, components). Within a tab, recipes are sorted by tier.
  - **Ingredients:** each recipe shows its ingredients with counts and an optional coin fee. Ingredients come from your
    inventory **and carried backpacks**.
  - **Crafting:** click crafts one; shift-click crafts as many as you can afford.
- **Upgrades keep the item.** Many recipes take the previous tier as an ingredient (Delver's Pickaxe -> Skyiron
  Pickaxe; Satchel -> Trail Pack). The new item keeps the old one's contents, enchantments and custom name.
- **Data:** `data/<ns>/skyrpg/recipes/*.json`:
  ```json
  {"category": "mining", "result": {"item": "skyrpg_items:skyiron_pickaxe"},
   "ingredients": [{"item": "skyrpg_items:delver_pickaxe", "count": 1},
                   {"item": "skyrpg_mining:skyiron_ingot", "count": 24},
                   {"item": "skyrpg_mining:prism_fragment", "count": 2}],
   "coins": 0, "requires": {"skill": "skyrpg_skills:mining", "level": 18}}
  ```
- **Unlock messages:** the level-up message lists new recipes ("New recipes: Skyiron Pickaxe, Skyforged Set").
- **The vanilla table stays** for the basics: 9 <-> 1 compaction, T1 gear, the bench itself. Those show in the recipe book.
- **Timed crafting** for T6 (put it in, collect it hours later) is for the public server only (section 13).

## 4. Mining

### 4.1 Ore ladder

| Ore | Where | Pick tier | XP | Drop -> refined | Status |
|---|---|---|---|---|---|
| Vanilla coal, copper, iron, gold, redstone, lapis, diamond | vanilla | vanilla | 5–10 | ingots, gems | exists |
| **Skyiron** | y -24..64, common | II | 8 | Raw Skyiron -> *Skyiron Ingot* (furnace) | exists, add smelting |
| **Duskquartz** (new) | y -48..-8, clusters in tuff and deepslate | IV | 12 | *Duskquartz Shard* (no refining) | new |
| **Emberite** | below y -8 | V | 16 | Emberite Chunk -> *Emberite Ingot* (blast furnace only) | exists, add smelting |
| **Lumenite** | below y -40, rare | VI | 25 | Lumenite Crystal + 4 Glimmer Crystal -> *Attuned Lumenite* (bench) | exists, add attuning |

Duskquartz fills the gap between Skyiron and Emberite, so the Deepwarden set gets its own material.

Rare finds stay as they are: Prism Fragment 1/300 and Heartstone 1/3000 from ores, three times rarer from nodes.
Add **Prism** (4 Fragments, bench) as the T6 ingredient, so fragments have a use at every stage.

### 4.2 Compaction and selling

- **Compaction:** 9 -> 1 at the crafting table for Glimmer (exists), Skyiron Ingot <-> Skyiron Block, Emberite Ingot <->
  Emberite Block and Duskquartz Shard -> Duskquartz Block. The blocks double as building blocks.
- **Sell prices** (Tamsin buys):

  | Material | Coins each |
  |---|---|
  | Glimmer Shard | 2 |
  | Raw Skyiron | 8 |
  | Duskquartz Shard | 14 |
  | Emberite Chunk | 20 |
  | Lumenite Crystal | 45 |
  | Prism Fragment | 1,500 |
  | Heartstone | 15,000 |

  Selling early is a real trade-off against crafting.

### 4.3 Pickaxe tiers (replace vanilla's tool gating for our ores)

- **Pick tier I–VII:** every pickaxe has one, shown in its tooltip ("Tier IV pickaxe"). An ore you can't break yet
  says "Needs a Tier V pickaxe" on the action bar instead of silently taking forever.
- **Vanilla pickaxes:** wood I, stone II, iron III, diamond IV, netherite V.
- **Ores:** set by block tags `skyrpg_mining:pick_tier/2..7`; vanilla ores keep vanilla rules.
- **Why:** it forces the ladder. Skyiron needs II, Duskquartz IV, Emberite V, Lumenite VI.

### 4.4 Pickaxes (each upgrades the previous one at the bench)

| Pickaxe | Tier | Level | Recipe | Mining Speed / Fortune | Ability (right-click) |
|---|---|---|---|---|---|
| Prospector Pickaxe | III | 0 | crafting table (exists) | 40 / 15 | — |
| Delver's Pickaxe | III | 8 | Prospector Pickaxe + 12 Glimmer Crystal + 8 iron | 70 / 25 | — |
| Skyiron Pickaxe | IV | 18 | Delver's + 24 Skyiron Ingot + 2 Prism Fragment | 120 / 40 | **Skyward Burst**: +150 Mining Speed for 10s (60s cooldown) |
| **Duskbore Pick** (new) | V | 26 | Skyiron Pickaxe + 32 Duskquartz + 8 Skyiron Ingot | 170 / 60 | **Echo Sense**: highlights our ores within 12 blocks for 8s (45s cooldown) |
| Emberheart Pickaxe | VI | 35 | Duskbore + 32 Emberite Ingot + 1 Heartstone | 220 / 80 | **Molten Core**: 20s of auto-smelted drops and +100 Mining Speed (90s cooldown) |
| Lumen Breaker | VII | 45 | Emberheart + 16 Attuned Lumenite + 1 Prism + 1 Heartstone | 320 / 120 | **Lumen Pulse**: every ore drops double for 15s (120s cooldown) |

The Quarry Pick stays a cheap starter (32 shards) for players who skip the crafting table.

### 4.5 Armour sets: the rework

What changes for every set:
1. **Own look:** a new icon mask and worn layers per set (no more recolours), plus raised 3D detail from
   `generate_armor_depth.py` like the starter sets.
2. **A real effect:** not just stats. Set bonuses get an `effects` list implemented in Java:
   `night_vision_below`, `ore_sense`, `auto_smelt`, `fire_immunity`, `rare_find_bonus`, `ambient_particles`, ...
3. **Crafted** from that tier's material.
4. **Smoother stats** along one curve (numbers below are full-set totals).

**Recipe "units":** each set has a *unit* of ingredients, and pieces cost helmet 5, chest 8, legs 7, boots 4 units
(vanilla's counts). That keeps 24 armour recipes readable.

| Set | Level | Unit | Look | Set MS / MF / Def | 4-piece bonus |
|---|---|---|---|---|---|
| Prospector (T1) | 0 | crafting table (exists) | slate and copper, lamp hat | 50 / 25 / 70 | +30 MS, +15 MF |
| **Delver** (T2) | 8 | 2 Glimmer Crystal + 1 leather + 1 iron | patched leather jerkin, brass miner's helmet with a 3D lamp, knee pads | 70 / 35 / 100 | +40 MS, +20 MF, **Night Vision below y 40** |
| **Skyforged** (T3) | 18 | 2 Skyiron Ingot + 1 Glimmer Crystal (+1 Prism Fragment per piece) | sky-blue plate with gold trim, winged crest, layered pauldrons | 110 / 55 / 150 | +60 MS, +30 MF, **Skyward Strikes**: every 50th ore gives +200 MS for 10s |
| **Deepwarden** (T4) | 26 | 3 Duskquartz + 1 Skyiron Ingot + 2 Glimmer Crystal | heavy blue-black plate, glowing teal runes, hooded helm with a visor slit | 160 / 80 / 200 | +80 MS, +40 MF, **immune to Darkness and Mining Fatigue**, passive Echo Sense every 60s |
| **Emberheart** (T5) | 35 | 2 Emberite Ingot + 3 Glimmer Crystal (+1 Heartstone in the chest only) | obsidian plates with ember-orange cracks, heartstone gem in the chest | 220 / 110 / 240 | +120 MS, +80 MF, **Molten Heart** (renamed): auto-smelt ores, fire immunity, ember particles |
| **Lumenward** (T6, new) | 45 | 1 Attuned Lumenite + 2 Emberite Ingot + 4 Glimmer Crystal (+1 Prism per piece, +1 Heartstone in the chest) | pale crystal plate, crystal spikes on the shoulders, soft light motes | 300 / 150 / 280 | +160 MS, +120 MF, **rare finds twice as likely** |

### 4.6 Nodes and Tamsin's shop after this

- **Nodes stay shop-only:** they're the shards sink, and the reason to visit Tamsin. Add a **Duskquartz Node** (L26).
- **Tamsin's stock:** nodes, Quarry Pick, T1–T2 gear (Delver at a premium), the Artisan Bench, and consumables:
  - *Glimmer Lantern*: a bright light block;
  - *Delver's Tonic*: +50 MS for 10 min;
  - *Node Polish*: the next 20 node regrows are twice as fast.
- **Selling to Tamsin:** she buys every mining material (4.2).
- **Accessories:** the Tinker Belt and future mining accessories get bench recipes too.

## 5. Farming (revised, being built)

Same shape as mining, in its own mod (`farming`, `skyrpg_farming`). The farmer **Mabel Thornfield** becomes the
**Farming Master**: her shop keeps its vanilla stock and gains a level-gated section.

**Changes from the first draft:**
- **More:**
  - **Sprinklers** to grow farms faster, made from mining metals.
  - **Rich Compost** that grows the special crops.
  - A **Harvest Rush** event.
  - A shared **gear effects** system, so set bonuses, tool perks and timed boosts do real things for every skill, not just
    farming.
- **Less:**
  - Five new crops instead of six: one per tier. Glowcap moves to Alchemy later.
  - No compacted crop crates for now (recipes use moderate counts).
  - **Cooking** becomes a tab on the Artisan Bench instead of a separate Cookpot block.

### 5.1 Currency and rare finds

- **Dew:** every fully grown crop you harvest (vanilla or ours) has a 15% chance to drop a **Dewdrop**; a planter has
  50%. Farming Fortune multiplies them. 9 Dewdrops make a **Dew Vial** (crafting table).
- **Rare finds** (any grown crop; three times rarer from planters), announced like the mining finds:

  | Find | Chance |
  |---|---|
  | **Lucky Clover** | 1/400 |
  | **Verdant Heart** | 1/4000 |

### 5.2 Crops (one per tier)

Planted on farmland like wheat, 4 growth stages. Each grows only under its condition and stalls otherwise; vanilla bone
meal doesn't work on them, **Rich Compost** does (and ignores the condition).

| Crop | Level | Grows only... | XP | Product | Seeds from |
|---|---|---|---|---|---|
| **Sunwheat** | 8 | under open sky, in daylight | 8 | Sunwheat Sheaf | 1/150 from wheat; Mabel |
| **Frostberry** | 18 | in a cold biome, or within 2 blocks of snow or ice | 12 | Frostberries | 1/200 from Sunwheat; Mabel |
| **Emberpepper** | 26 | within 4 blocks of lava, or in the Nether | 16 | Emberpepper | 1/50 from Emberite ore (mining); Mabel |
| **Moonmelon** | 35 | at night | 22 | Moonmelon Slice | 1/300 from melons harvested at night; Mabel |
| **Starbloom** | 45 | only in a Starbloom Planter | 40 | Starbloom Petal | rare find from any T5 crop (1/2000) |

Seed drops from other mods' blocks are data (`skyrpg/bonus_drops/*.json`), so farming doesn't depend on mining.

### 5.3 Planters (the farming version of nodes)

- **Using them:** place anywhere outside the hub. The crop grows on a timer (ignoring light, biome and water), you
  right-click or break it to harvest, and it starts over. Sneak with an empty hand to pick the planter up.
- **Unlocks and timers:**

  | Planter | Level | Grows in |
  |---|---|---|
  | Wheat | 3 | 45s |
  | Carrot, Potato | 5 | 50s |
  | Sunwheat | 12 | 90s |
  | Frostberry | 22 | 2 min |
  | Emberpepper | 30 | 3 min |
  | Moonmelon | 38 | 4 min |
  | Starbloom | 45 | 5 min |

### 5.4 Sprinklers (new)

A small block that waters and speeds up crops around it: every 5 seconds each crop in range gets an extra growth tick,
and farmland stays wet.

| Sprinkler | Range | Level | Made from |
|---|---|---|---|
| Copper Sprinkler | 5x5 | 5 | Mabel (Dew) |
| Skyiron Sprinkler | 7x7 | 18 | bench: copper + Skyiron |
| Emberite Sprinkler | 9x9 | 30 | bench: Skyiron Sprinkler + Emberite |

### 5.5 Tools (each upgrades the previous one at the bench)

| Tool | Level | Perks (passive) | Ability (right-click) |
|---|---|---|---|
| Harvester Hoe | 0 | — | — |
| **Thornfield Sickle** | 8 | Reseed: replants what you harvest | — |
| **Sunreaper Scythe** | 18 | Reseed, harvests a 3x3 | — |
| **Frostbite Scythe** | 26 | Reseed, 3x3 | **Bountiful Gust**: +100 Farming Fortune for 10s (60s cooldown) |
| **Cinder Scythe** | 35 | Reseed, 3x3, +10% double crops | Bountiful Gust (45s) |
| **Moonlit Reaper** | 45 | Reseed, 5x5, +15% double crops | **Harvest Moon**: double crops for 15s (120s cooldown) |

### 5.6 Armour sets (same rules as mining: own look, 5/8/7/4 units, a real effect)

| Set | Level | Made from | Fortune / Health | 4-piece bonus |
|---|---|---|---|---|
| Harvester (T1) | 0 | crafting table (exists) | 50 / 70 | +25 FF, +10 Farming Wisdom |
| **Fieldhand** (T2) | 8 | Mabel (Dew Vials), or bench | 70 / 90 | +35 FF, **no trampling** |
| **Sunward** (T3) | 18 | Sunwheat + Dew + Lucky Clover | 100 / 120 | +50 FF, **Sunlit**: +25% Dewdrops in daylight |
| **Frostgrove** (T4) | 26 | Frostberries + Dew + wool | 140 / 150 | +70 FF, Speed on farmland, +10% double crops |
| **Cinderfield** (T5) | 35 | Emberpepper + Emberite + Dew | 190 / 190 | +100 FF, Reseed with any tool, fire immunity |
| **Moonharvest** (T6) | 45 | Moonmelon + Starbloom + Verdant Heart | 250 / 230 | +140 FF, **crops within 8 blocks grow faster**, rare finds twice as likely |

### 5.7 Gear effects (new, shared by every skill)

Set bonuses, items and timed boosts can now grant **effects** as well as stats: named switches with a strength, like
`skyrpg_farming:reseed` or `skyrpg_items:fire_immunity`. The items mod works out who has which (from full sets, the held
item, accessories, and active boosts); each skill's mod decides what its effects do. A new ability type, **boost**,
gives stats and effects for a few seconds (Bountiful Gust, Harvest Moon, and the mining abilities later). Meals use the same
boosts.

### 5.8 Cooking (a bench tab)

Meals are crafted at the Artisan Bench (Cooking tab). Eating one gives a 10-minute boost; a new meal replaces the old one.

| Meal | Made from | Boost |
|---|---|---|
| Sunbread | Sunwheat Sheaf + wheat | +25 Farming Fortune |
| Miner's Chili | Emberpepper + potato + Glimmer Shard | +40 Mining Speed |
| Frostberry Tart | Frostberries + sugar + egg | +10 Speed |
| Moonmelon Juice | Moonmelon Slices + glass bottle | +60 Intelligence |
| Starbloom Tea | Starbloom Petal + Dewdrop | +40 Health and +2 Farming Wisdom |

### 5.9 Rich Compost, selling and the event

- **Rich Compost** (bench): 4 Dewdrops + 2 bone meal + 1 Glimmer Shard -> 4. Grows any crop one stage, conditions or
  not.
- **Sell prices:** Mabel buys the new crops and Dew, priced by tier.
- **Harvest Rush:** a 5-minute event; points for fully grown crops, more for the special ones.

## 6. Backpacks: crafted upgrades

Each pack upgrades the previous one at the bench and **keeps its contents**.

| Pack | Recipe | Shop |
|---|---|---|
| Satchel | crafting table: 6 leather + 2 string + 1 iron | Dorrin, 500 |
| Trail Pack | Satchel + 12 leather + 8 string + 2 Glimmer Crystal | Dorrin, 2,500 |
| Ranger's Pack | Trail Pack + 16 leather + 8 Skyiron Ingot + 4 Dew Vial | bench only |
| Voyager's Pack | Ranger's Pack + 8 wool + 16 gold + 2 Lucky Clover | bench only |
| Colossal Pack | Voyager's Pack + 4 Prism Fragment + 1 Heartstone + 1 Verdant Heart | bench only |

The Colossal Pack needs both a mining and a farming rare find, so it's the shared trophy.

## 7. Foraging

Same template. The lumberjack **Oswin Fernhollow** becomes the **Foraging Master**.

### 7.1 Currency, woods and rare finds

- **Currency:** chopping a log has a 10% chance to drop a **Heartwood Splinter**; 9 make a **Heartwood Bundle**.
  Foraging Fortune multiplies them.
- **Rare finds** (any log):

  | Find | Chance |
  |---|---|
  | **Golden Acorn** | 1/500 |
  | **Elder Heartwood** | 1/5000 |

- **New trees,** grown from saplings, each with a condition like the farming crops:

  | Wood | Level | Sapling from | Grows only... | XP | Notes |
  |---|---|---|---|---|---|
  | Vanilla woods | 0 | vanilla | vanilla | 6 | |
  | **Ironbark** | 8 | 1/200 from oak logs, or Oswin | anywhere | 10 | hard logs: needs a Tier III axe |
  | **Mistpine** | 18 | snowy and high places | above y 120, or in cold biomes | 14 | |
  | **Duskwillow** | 26 | swamps | in swamps, by water, growing only at night | 18 | |
  | **Emberwood** | 35 | rare find from Emberpepper plants (farming link) | within 6 blocks of lava, or in the Nether | 24 | |
  | **Starwood** | 45 | Golden Acorn planted in a Starwood Pot | only in the pot | 40 | |

- **Stumps** (the foraging version of nodes): a placeable stump grows one log on a timer. Chop it for the log, a
  little XP and a 50% chance of a Splinter; sneak with an empty hand to pick it up. Unlocks:

  | Stump | Level |
  |---|---|
  | Oak | 3 |
  | Birch, Spruce | 6 |
  | Ironbark | 14 |
  | Mistpine | 22 |
  | Duskwillow | 30 |
  | Emberwood | 38 |

### 7.2 Axes

Each axe upgrades the previous one. Tiers work like pickaxe tiers, for hard woods.

| Axe | Level | Ability / perk |
|---|---|---|
| Woodsman Axe | 0 (exists) | — |
| Ironbark Hatchet | 8 | Tier III; leaves drop straight away when their tree is felled |
| Mistpine Splitter | 18 | **Treefeller**: right-click, and the next log you hit fells the whole tree (60s cooldown) |
| Willowcleaver | 26 | Treefeller every 40s; replants the sapling |
| Emberbrand Axe | 35 | Treefeller every 25s; logs drop straight to you as charcoal or planks (switch with sneak + right-click) |
| Starwood Greataxe | 45 | Treefeller every 15s; double log chance +25% |

### 7.3 Armour sets (same rules and 5/8/7/4 units)

| Set | Level | Made from | 4-piece bonus |
|---|---|---|---|
| Woodsman (T1) | 0 | crafting table (exists) | +20 Foraging Fortune, +5 Foraging Wisdom |
| **Barkguard** (T2) | 8 | Ironbark + Heartwood Bundles | no fall damage from 6 blocks or less (climbing trees) |
| **Mistwarden** (T3) | 18 | Mistpine + Golden Acorn per piece | Speed I in forests, leaves don't slow you |
| **Willowshade** (T4) | 26 | Duskwillow + wool | +30% Splinters at night |
| **Emberbark** (T5) | 35 | Emberwood + Emberite Ingot (mining link) | Treefeller cooldown halved, fire immunity |
| **Starwood** (T6) | 45 | Starwood + Elder Heartwood | rare finds twice as likely, glowing leaves particles |

### 7.4 Uses elsewhere

- **Tool handles:** Ironbark for T3+ pickaxes and hoes.
- **Planters and stumps:** their frames.
- **Bows:** for combat.
- **Backpack frames:** for the Ranger's Pack.

## 8. Combat

Needs the `combat` mod first (levelled mobs, health bars, damage numbers; see `docs/PLAN.md` phase 5). The
weaponsmith **Kestrel Vane** becomes the **Combat Master**.

- **Mob levels:** overworld mobs get a level from how far they are from spawn and how deep or how dark it is.
  Higher levels have more health and damage, and give more XP and drops. Later, areas get their own custom mobs.
- **Currency:** levelled mobs drop **Valor Marks**; 9 make a **Valor Seal**. Combat Fortune (Magic Find) raises
  drop chances.
- **Rare drops:**

  | Drop | Chance | Used in |
  |---|---|---|
  | **Dread Fang** | 1/400 from level 10+ mobs | T3+ weapons |
  | **Nightmare Core** | 1/4000 from level 30+ mobs | T5–T6 |

- **Training Dummy** (the combat version of nodes): placeable, shows damage numbers and your DPS, gives a trickle of
  XP (capped per hour so it can't be AFK-farmed).
- **Weapons:** three lines, each upgrading at the bench:
  - **Swords:** Vanguard Blade -> Fangsteel Sabre -> Stormedge -> Riftblade -> Cinderfang -> Starfall Blade.
  - **Bows:** from Mistpine and Ironbark (foraging link).
  - **Wands:** Intelligence and mana, at T3 and T5 (the Mending Wand stays the T3 support wand).
- **Armour sets** (Vanguard is T1, five more): bonuses like life steal on crits, a short shield after taking a big hit,
  thorns, and mana regeneration for the mage set.
- **Bosses and slayers** come after, as in `docs/PLAN.md`.

## 9. Later skills (outline only)

| Skill | Gather | Master | Ties in |
|---|---|---|---|
| **Fishing** | water, lava (with a Tier V rod), sea creatures that bite back | a dock NPC | baits from farming, rods from foraging, sea creatures are combat |
| **Alchemy** | brewing potions at a Brewing Bench | a hub alchemist | herbs from farming, Glowcap, Golden Acorn. Potions are short and strong; meals are long and mild |
| **Enchanting** | custom enchants (see `enchants` in `docs/PLAN.md`) | Odessa Quill | Lapis and Prism Fragments from mining, Dew for Reseed books |

### 9.1 Fishing (the next skill, as decided)

- **Master:** a new hub NPC by the water, **Marina Saltwick** (Fishing Master).
- **Currency:** each catch has a 10% chance of a **Tidescale** (times 1 + Fishing Fortune / 100); 9 make a **Tidescale
  Plate**.
- **Rare finds:** an **Abyssal Pearl** (1/300) and a **Leviathan Scale** (1/3,000).
- **Sea creatures:** a chance (5% + Sea Creature Chance) that the line pulls up a mob instead of a fish. Which one depends
  on your Fishing level (data): Drowned and Squid early, then Guardians and stranger things. They pay Fishing XP and
  Tidescales, and despawn on their own after two minutes so nobody keeps them as pets.
- **New stats:** Fishing Speed (each 25 points = one Lure level, up to +4) and Sea Creature Chance (%).
- **Rods:** Driftwood Rod (T1, sold), Reef Rod (8), Stormcaller Rod (18), Abyssal Rod (26), Leviathan Rod (35). Each is
  made from the one before. Sets: Angler (T1, sold), Reefwalker (10), Abyssal (26), Leviathan (40).
- **Not yet:** lava fishing. It needs fishing-hook physics in lava, which is a big change; it comes later with the Nether
  content.

### 9.2 Pets (next, as decided)

- **One pet per skill:** Mole (mining), Bumblebee (farming), Owl (foraging), Wolf Pup (combat), Otter (fishing). Each
  comes in Common, Uncommon, Rare, Epic and Legendary; a better rarity gives more per level.
- **Levels 1 to 50:** the active pet earns a share of the XP you earn in its own skill (25%, a little in other skills),
  on the pets' own curve.
- **Stats:** the pet's two stats grow with its level (Mole: Mining Speed and Mining Fortune, and so on). At level 25 and
  50 it unlocks a perk, which is a gear effect: the Mole's Echo Sense every minute, the Bee's growth aura, the Owl's soft
  landing, the Pup's life steal, the Otter's Double Catch.
- **One active pet:** right-click a pet item to make it active (it moves into your pet slot, which is saved with you),
  and pick it or another one in the Pets menu page. It floats at your shoulder as a small model that everyone sees.
- **Where from:** eggs. Common eggs come from each master's shop for currency; better ones from rare finds, the world
  boss and contracts. You can also hatch an egg up a rarity at the bench with that skill's rare finds.

## 10. Cross-skill links

| Skill | Needs from mining | Needs from farming | Needs from foraging | Needs from combat |
|---|---|---|---|---|
| Mining | — | meals (Miner's Chili), leather for Delver | Ironbark handles for T3+ picks | — |
| Farming | iron and Skyiron for tool heads, Emberpepper seeds | — | wood for planters | — |
| Foraging | Emberite for Emberbark, axe heads | Emberwood saplings come from Emberpepper | — | — |
| Combat | weapon metals (Skyiron, Emberite) | meals and potions | bows and arrows | — |
| Backpacks | Glimmer, Skyiron, Prism, Heartstone | Dew, Lucky Clover, Verdant Heart | Ironbark frames | leather |

## 11. Build order

Each step is tested on staging with SkyCam before it ships, like the Mining update.

| Step | What | Size |
|---|---|---|
| 0. Utilities (done) | `utils` mod: minimap with cave view, world map, look-at info, info box, settings | M |
| 1. Foundation | Artisan Bench (block, screen, recipe data, backpack-aware ingredient counting, upgrade-keeps-item), smelting recipes, sell prices for mining materials, pick tiers, set-bonus `effects`, recipe-unlock messages, the friends XP curve | L |
| 2. Mining pass | Duskquartz ore + node, refined items (ingots, blocks, Attuned Lumenite, Prism), all mining recipes, Duskbore Pick and Lumenward set, pickaxe abilities, armour rework (art, 3D detail, effects, stats), rename "Heart of the Mountain", Tamsin's new stock | L |
| 3. Backpacks | bench recipes and upgrades | S |
| 4. Farming (now) | Dew, five crops with growth conditions and art, planters, sprinklers, compost, gear effects, tools (Reseed, area harvest, boosts), five new sets, rare finds, Mabel's shop, sell prices, compaction | L |
| 5. Cooking | meals at the bench's Cooking tab, boosts (built with farming) | S |
| 6. Foraging | Splinters, five woods, stumps, axes with Treefeller, five sets | L |
| 7. Combat | after the `combat` mod: Valor Marks, dummies, weapon lines, sets | L |

## 12. Decisions (made)

1. **Crafting UI:** the Artisan Bench screen. The vanilla table keeps compaction, T1 gear and the bench itself.
2. **Buy vs craft (friends):** masters sell T1–T2 gear, nodes/planters/stumps and consumables. T3+ is crafted only.
   When the bench exists, the T3+ offers in Tamsin's shop move to recipes.
3. **Pick tiers (and axe tiers):** yes, for our ores and hard woods; vanilla blocks keep vanilla rules.
4. **Set bonuses:** 4-piece only.
5. **XP curve:** the friends curve, shipped as data. A public server swaps in a steeper curve with a datapack.
6. **Timed crafting:** not for friends; planned for the public server (section 13).
7. **Names:** as written. Easy to change later: they're lang and JSON.
8. **Heartstones (mining pass):** only one per top-tier item (Emberheart Pickaxe, Emberheart coat, Lumenward plate, Lumen
   Breaker), not one per piece: at 1 in 3,000 ores, four is already a long goal for a few friends.
9. **Auto-smelt** only touches raw ores (an item tag), so a set bonus never turns your logs into charcoal or sand into glass.
10. **Anti-abuse:** blocks a player placed (ores, crops, logs) give no skill XP, no bonus drops, no event or contract
    progress (core `PlacedBlocks`, kept per chunk). Nodes, planters and saplings are exempt because placing them is the point.
11. **Foraging (as built):** every axe from the Ironbark Hatchet up has Treefeller (arm it, then chop), with the
    recharge dropping 60 -> 12 s up the ladder, instead of Treefeller starting at the second axe. Starwood saplings come
    from the bench (2 Golden Acorns + 4 Bundles) and grow only under open sky at night, instead of needing a pot. Golden
    Acorns go on the Mistwarden chest and every Starwood piece, Elder Heartwood only on the Starwood chest and Greataxe.
    Saplings don't take bone meal, so the growing conditions matter.

12. **Slayers (as built, see `slayers.md`):** four lines at once instead of "first slayer (zombie)", in the combat mod
    (it owns levels, nameplates and rewards) instead of a separate `slayers` mod. Spawners are sold by the Slayer Master
    on a kill ladder (Philip's idea), and mobs from spawners are "farmed" (core `MobTags.FARMED`): a quarter of the XP,
    nothing else, so AFK spawner farms can't carry skills, slayers or the economy.
13. **Enchanting (as built, see `enchanting.md`):** our own enchants on a data component, not vanilla's enchantment
    registry (one data file per enchant, balance reloads with /reload, any mod's gear effect can be an enchant). The
    vanilla table opens our screen and its bookshelves set the highest level; Seraphine Vael (the Enchanter already in
    the hub) has the full library, instead of Odessa Quill, who stays the Curio Dealer. Tomes only come from drops and
    finds (never from the table), so combining is the one way past level V and can't be farmed with coins.

14. **World dungeons (as built, see `dungeons.md`):** dungeons are vanilla jigsaw structures in the survival world, not
    instanced hub dungeons, so finding one is part of exploring. Everything that acts is a marker entity inside the
    template, with its state saved on the marker, so dungeons survive restarts and need no global registry. Puzzle groups
    are scoped to their structure piece. Used-up parts reset after three in-game days, and only with nobody nearby.
    Boss loot is rolled per player, so friends never fight over drops. Waystones cost XP levels (not coins), so travel
    doesn't drain the economy.

15. **Danger regions (as built, see `danger_regions.md`):** regions are a logical layer from the seed, not new biomes,
    because Fabric can't add overworld biomes without TerraBlender. A worldgen feature re-skins their land, and the
    danger works through the existing mob-level system (a bonus hook in core), so vanilla spawns inside get tougher
    too. Region materials only drop from kills inside, and their gear needs a Dread Crystal, so the best stuff means
    going in.
16. **Dungeon rewards (v2):** every chest is per player, and bosses pay coins and Delving XP on every kill, not just
    the first. The Reward Chest grows with how much of the dungeon the party cleared, so full clears pay better than
    boss rushes. Dungeon gear is both a rare drop and a token craft, so bad luck never walls anyone.
17. **Builder Station (as built, see `builder.md`):** a Foraging 5 bench craft, so it comes early but after the first
    trees. It saves time, never materials: every block is paid for, double slabs cost two, still water costs a bucket.
    Clearing the site only gives back what you could break by hand (stone and ores vanish), so a blueprint can't be
    used as a free quarry, and stations don't work in Skyhaven. Uploaded schematics are shared with the whole server.

18. **Crates (as built, see `crates.md`):** CS2-style crates (Philip's idea), all decided here: a seasonal Hallow's
    Crate that drops from monsters until 2026-11-08 (0.25% by day, 1% at night/Nether/End, 3% Blood Moon, 10% elites,
    25% bosses, Magic Find helps, spawner mobs never), opened with a key bought from Odessa Quill for 2,500 coins (a
    coin sink; your first crate brings a free key). Five tiers 64/25/8/2.5/0.5%: candy and coin pouches, a random
    Hallowed Harvest piece, the Reaper's Scythe or an Epic Jack o'Lantern, the full set, then the Soulreaper or a
    Legendary Jack. Crate-only gear is a Farming + Combat hybrid that peaks at night, so it complements rather than
    replaces the crafted ladders.
19. **Seasons and Hallow's Eve (as built, see `seasons.md`):** dated server events are data (`skyrpg/seasons/*.json`,
    events mod) with instants in UTC so the host's time zone can't shift them. Hallow's Eve 2026 runs Fri 30 Oct 18:00
    to Sun 1 Nov midnight CET, over the automatic 2x XP weekend on purpose. It brings no new gear (the Hallow's Crate already
    is the Halloween loot): instead double crate drops, a free key + candy + a once-only title for showing up, a seasonal
    world boss (the Headless Horseman, kept out of the normal rotation with `"auto": false`, rising at dusk on the first
    night and every third night, about hourly while people play), a guaranteed Blood Moon on Halloween night, and two
    Halloween auto events that pay in keys and crates every 35 to 50 minutes. Everything points back at the crate, so
    the event feeds the existing loot loop rather than adding a parallel one.

## 13. Friends now, public server later

Everything above is built for two friends first. These are the switches and additions planned for a big server, so the
friends version doesn't paint us into a corner:

| Area | Friends (now) | Public server (later) |
|---|---|---|
| Gear | T1–T2 buyable from masters, T3+ crafted | only T1 buyable; everything else crafted or bought from other players. NPC prices are coin sinks |
| Trading | none; coins only move through NPCs and `/pay` | a bazaar for materials and an auction house for gear (own mod), with listing fees as coin sinks |
| Levelling | friends curve | steeper curve (datapack), plus Wisdom stats and boosters to compensate |
| Top-tier crafting | instant | timed crafting for T6 (hours), so supply comes in slowly |
| Nodes, planters, stumps | placed anywhere in the shared world | placed only on your own island or plot (a personal-island mod), capped per player |
| Rare finds | announced to everyone | announced to you, with a server-wide shout only for the rarest |
| Anti-abuse | trust | no XP from re-placed blocks (already planned), dummies' XP capped, AFK checks, rate limits on shop and bench actions |
| Data | player attachments in the world save | the same records in a database, per profile (players can have several) |
| Items | plain stacks | unique ids on T4+ gear to trace duplicates; optional soulbound for event rewards |

Rule for now: every number lives in data, and every server-only rule (caps, fees, timers) goes behind a config value
with the friends default. Going public is then a data and config change plus the new mods, not a rewrite.
