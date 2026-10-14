# Fun pass: SkyRPG as advanced survival for two

A look at everything built so far, played the way we actually play: two friends in one survival world, a few
evenings a week. What's missing isn't more items, it's **reasons to log in, choices, danger and doing things
together**.

## What works

- **Skills with a real curve:** about 8 to 10 hours to Mining 20 with the friends curve, a long tail to 50.
- **Crafting ladders:** each pickaxe is made from the one before it; backpacks upgrade and keep their contents.
- **Special crops** that need the right place to grow, nodes and planters for steady income, and rare-find moments.
- **Quality of life:** minimap and world map, look-at info, backpacks, the bench reading from backpacks.

## What isn't fun yet (and the fix)

| Problem | Why it matters | Fix |
|---|---|---|
| Levelling only adds fixed stats | No choices: two players on the same level are identical | **Talents**: a point per skill level, spent on perks with real effects, so each of us builds differently |
| Nothing says "do this today" | Without goals, sessions drift | **Contracts board**: three daily co-op contracts (deliver, harvest, mine, hunt) with good rewards for everyone who helped |
| SkyRPG stats make vanilla mobs trivial; Combat has no content | No danger, so no tension or loot | **Elite mobs**: a few hostile spawns come back stronger, glowing, with coins, Combat XP and trophies |
| Events only start when someone asks | The fun moments are rare | **Auto events**: when two or more players are online, a random event about once an hour |
| Coins mostly come from selling | Thin economy, few sinks | Contracts, elites, skill milestones and bank interest pay coins; talent resets and contracts' rerolls cost them |
| Rare-find bonuses only take the best source | Stacking gear didn't feel rewarding | Gear effects now add up (two sources of double crops stack) |

## Talents (in the skills mod)

- **Points:** each skill level gives one talent point for that skill (50 at max). Spend them on that skill's talents in the
  SkyRPG menu (K -> Talents).
- **Talents:** most have several ranks; the strongest unlock at a skill level. Each rank gives stats and/or gear effects,
  so talents use the same machinery as gear.
- **Resetting:** free once a day; after that it costs coins.
- **Data:** `data/<ns>/skyrpg/talents/<skill>.json`, so numbers can change without a recompile.

**Mining:**

| Talent | Ranks | Per rank | Needs |
|---|---|---|---|
| Glimmer Sense | 5 | +8% Glimmer Shards | — |
| Steady Hands | 5 | +8 Mining Speed | — |
| Deep Pockets | 5 | +5 Mining Fortune | — |
| Node Keeper | 5 | nodes regrow 6% faster | Mining 5 |
| Gem Eye | 5 | +10% rare finds | Mining 10 |
| Vein Echo | 3 | 4% chance to also mine a touching ore of the same kind | Mining 15 |
| Lamplighter | 1 | Night Vision below y 40 | Mining 10 |
| Prospector's Luck | 1 | 6% chance for double ore drops | Mining 30 |

**Farming:**

| Talent | Ranks | Per rank | Needs |
|---|---|---|---|
| Dew Collector | 5 | +8% Dewdrops | — |
| Green Fingers | 5 | +5 Farming Fortune | — |
| Planter Tender | 5 | planters grow 6% faster | Farming 5 |
| Lucky Soil | 5 | +10% rare finds | Farming 10 |
| Seed Saver | 3 | next-crop seeds 25% more likely | Farming 8 |
| Light Step | 1 | no trampling | Farming 5 |
| Sower | 1 | Reseed with any tool | Farming 20 |
| Bumper Crop | 1 | 6% double crops | Farming 30 |

**Foraging and Combat:** stat talents now, effect talents when their content arrives. Combat's capstone, **Second Wind**,
survives a killing blow at 1 health once every 5 minutes.

## Contracts board (in the events mod)

- **The board:** three contracts a day, shared by the whole server: everyone's progress counts towards the same goal. Open
  it in the menu (K -> Contracts); a HUD box shows progress.
- **Kinds:** deliver items (hand them in on the board), harvest grown crops, mine blocks, kill mobs, gain skill XP. Contracts
  come from data templates with amounts and rewards scaled by tier.
- **Rewards:** coins, skill XP and currency (Dew, Glimmer), with a chance of a rare find. Everyone who contributed gets the full
  reward when the contract completes.
- **Reroll:** each contract can be rerolled once a day for coins.

## Elite mobs (new small `combat` mod, before the full combat plan)

- **Spawning:** 3% of naturally spawned hostile mobs (6% at night, 8% underground) come back as **Elites**:
  - a coloured name ("✦ Elite Zombie");
  - 4x health, +50% damage, a little faster;
  - a faint particle aura.
- **Loot:** coins (50-200), 5x Combat XP and an **Elite Trophy** (the first combat material; Combat recipes come later), plus a 1 in
  50 chance of a Lucky Clover or a Prism Fragment.

## Small additions

- **Skill milestones:** every 5 levels pays coins (level x 100), announced in the level-up message.
- **Bank interest:** 1% a day, up to 5,000 coins, paid on your first login of the day.
- **Auto events:**
  - Which: a random event (Timber Rush, Monster Hunt, Harvest Rush, Glimmer Rush) about every 60 to 90 minutes.
  - When: only while two or more players are online and no event is running.
  - Settings: `/event auto on|off`.
- **Glimmer Rush:** a new mining event; points for ores and nodes.

## Later (bigger systems, not in this pass)

- **Fishing:** sea creatures, treasure.
- **Pets:** levelling companions with buffs.
- **A world boss:** a co-op event.
- **Custom enchanting** (built: `enchanting.md`).
- **Collections:** milestones per item.
- **Dungeons.**
