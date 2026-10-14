---
title: Slayers
category: combat
order: 4
icon: minecraft:bone
summary: Morwen Ashgrave's slayer quests, the four lines and their bosses, slayer levels, slayer gear and the spawner ladder.
---
Slayers are the combat endgame. **Morwen Ashgrave**, the Slayer Master, has a market stall in Skyhaven next to the Curio Dealer. She sells slayer quests: you pay a fee, hunt a kind of mob until a bar fills, and a boss rises to fight you. Beat it and you earn slayer XP, tokens and rare drops, which turn into the best combat gear before the dungeons. She also sells mob spawners. Talking to her opens a screen with two tabs, **Slayer Quests** and **Spawners**. The same quests appear on the Slayers page in the K menu.

## How a quest works

1. Pick a line and a tier on Morwen's screen and pay the fee.
2. Kill that line's mobs anywhere in the survival world. Each kill fills the quest bar by `1 + mob level / 5`, so higher level mobs fill it faster. Mobs from spawners do not count.
3. When the bar is full, the slayer boss rises next to you with a boss bar and a clock.
4. Kill it before time runs out. Friends can help.
5. If time runs out or you die, the quest fails and the fee is gone.

Your quest and its timer show on the HUD. The boss hunts the player who started the quest, and follows them: a boss more than 32 blocks away, or one that has not hit anything for 15 seconds, comes to them. If you log off, change dimension, go to the hub or enter spectator mode, the boss leaves, and it comes back with the time that was left.

> [!NOTE]
> Bosses with more than 5,120 health (vanilla's cap) are made tougher another way: they take proportionally less damage. Their nameplate and your damage numbers show the real values.

## The four lines

| Line | Mobs that fill the bar | Token | Rare drop (tier III and IV) | Each slayer level |
| --- | --- | --- | --- | --- |
| Grave Tyrant | zombies, husks, drowned, zombie villagers, Tide Lurkers and Gravecallers | {{item:skyrpg_combat:tyrants_ash}} | {{item:skyrpg_combat:plague_heart}} | +3 Health |
| Broodmother | spiders, cave spiders and Glimmer Crawlers | {{item:skyrpg_combat:brood_silk}} | {{item:skyrpg_combat:venom_gland}} | +0.5% Crit Chance |
| Bone Marshal | skeletons, strays, bogged, wither skeletons and Frost Revenants | {{item:skyrpg_combat:marrow_shard}} | {{item:skyrpg_combat:marshals_sigil}} | +1 Strength |
| Void Stalker | endermen, endermites and Hollow Wisps | {{item:skyrpg_combat:void_fragment}} | {{item:skyrpg_combat:null_core}} | +2 Intelligence |

Zombified piglins do not count for the Grave Tyrant, so gold farms do not help. See [Mobs](mobs) for the custom mobs.

## Tiers

| Tier | Fee | Quest bar | Boss health | Time | Nameplate level | Slayer XP | Needs slayer level |
| --- | --- | --- | --- | --- | --- | --- | --- |
| I | 1,000 | 100 | 2,000 | 3 min | 10 | 5 | 0 |
| II | 5,000 | 400 | 6,000 | 3 min | 25 | 25 | 2 |
| III | 20,000 | 1,200 | 18,000 | 4 min | 40 | 100 | 4 |
| IV | 60,000 | 3,000 | 50,000 | 5 min | 60 | 500 | 6 |

Each boss's size grows with the tier, and every tier is a new, more armoured form with its own model.

### Boss rewards

| Tier | Tokens | Rare drop | Golden Pet Egg | Combat XP |
| --- | --- | --- | --- | --- |
| I | 1 to 2 | none | none | 100 |
| II | 3 to 5 | none | none | 300 |
| III | 8 to 12 | 2% | none | 900 |
| IV | 20 to 30 | 6% | 0.4% (announced to everyone) | 2,500 |

Combat XP is a twentieth of the boss's health. The rare drop and egg chances are raised by Magic Find. The usual rewards for killing a high-level mob (Valor Marks, fangs, cores) apply on top. Each kill also gives the slayer XP from the tier table, which is what levels the line.

### Boss damage

The tier sets a multiplier on the boss's normal attack. Many signature moves (Grave Slam, Pounce, Void Bolt, the plague aura, rifts) instead hurt by a share of your **maximum health**, so they matter at any gear level. Defense still reduces the result.

| Tier | Grave Tyrant | Broodmother | Bone Marshal | Void Stalker |
| --- | --- | --- | --- | --- |
| I | x2.5 | x3 | x2.5 | x1.5 |
| II | x4 | x5 | x4 | x2.2 |
| III | x6.5 | x8 | x6 | x3.2 |
| IV | x10 | x12 | x9 | x4.5 |

## Slayer levels

Every line has its own level, 1 to 9, from the slayer XP you earn on it.

| Level | 1 | 2 | 3 | 4 | 5 | 6 | 7 | 8 | 9 |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| Total XP | 5 | 15 | 50 | 120 | 300 | 700 | 1,500 | 3,000 | 6,000 |

Each level gives its line's stat from the table above, permanently. Levels also unlock things:

| Level | Unlocks |
| --- | --- |
| 2 | Tier II quests, and the slayer boots |
| 3 | The line's accessory |
| 4 | Tier III quests, and the slayer helmet |
| 5 | The slayer leggings |
| 6 | Tier IV quests, and the line's legendary weapon |
| 7 | The slayer chestplate |

## The bosses

Every tier adds one attack and one defence on top of the tiers below, so a tier IV fight uses everything the line has. Every attack is announced in the action bar and marked on the ground before it lands.

| | Grave Tyrant | Broodmother | Bone Marshal | Void Stalker |
| --- | --- | --- | --- | --- |
| I | Risen Tyrant: Grave Slam. Rotting Hide (projectiles do half) | Brood Mother: Web Shot. Chitin Plates (melee does 20% less) | Bone Sergeant: Volley. Bone Plating (no knockback) | Void Wanderer: Void Bolt, blinks behind you. Phase Step (dodges 1 hit in 5) |
| II | Plague Tyrant: Raise the Dead. Life Drain (heals 2% per hit, enrages below half) | Venom Matron: Pounce. Brood Shield (takes 70% less while 3 or more brood live) | Bone Captain: Arrow Rain, bigger volleys. Tactical Retreat | Void Seeker: Void Grasp (pulls you in, blinding hits). Null Shield |
| III | Dread Tyrant: Gravestones. Bone Armour (60% less damage for 3 seconds after each slam) | Silk Empress: Egg Sacs and Brood. Silk Cocoon (heals at half health) | Bone Commander: Piercing Shot. War Banner | Void Reaver: Void Beacon. Mirror Images |
| IV | Tyrant King: Plague Aura and Tomb Prison. Undying (rises once) | Brood Queen Arachnia: Silk Prison and Venom Bite. Venom Blood | Bone Marshal Supreme: Legion. Bone Shield | Void Sovereign: Rifts and Void Collapse. Void Siphon |

### Grave Tyrant

- **Grave Slam** every 12 seconds: a roar, a leap and a shockwave on a marked circle. It takes 13% of your maximum health at tier I and 22% at tier IV.
- **Enrage** below half health from tier II: faster and harder hitting.
- **Raise the Dead** every 15 seconds from tier II: 2 zombies (3 at tier IV).
- **Gravestones** every 15 seconds from tier III: tombstones fall on marked circles. Break them before they burst.
- **Plague Aura** (tier IV): every second, everyone near it loses 4% of their maximum health. **Tomb Prison** seals you in stone. **Undying:** the first time it dies it gets back up with 35% of its health.

> [!TIP]
> The Tyrant King's aura takes a share of your health every second, so a long fight hurts. Bring a weapon with strong damage against the line ({{item:skyrpg_items:gravebane}}) or the Gravewarden set, and kill the zombies it raises quickly.

### Broodmother

- **Web Shot** every 8 seconds slows you to a crawl.
- **Pounce** every 9 seconds from tier II, from 4 to 18 blocks away. It hits for 12% to 16% of your maximum health and slows you.
- **Egg Sacs** at two thirds and one third health from tier III: 3 or 4 sacs that hatch two spiderlings each after 6 seconds unless you hit them twice. She also hatches cave spiders (3, or 4 at tier IV) every 15 seconds.
- **Silk Cocoon** (tier III and up): below half health she cocoons and heals 3% of her health a second, taking almost no damage. Cut her out with 6 hits (8 at tier IV).
- **Silk Prison** (tier IV) webs you in place, then she pounces. **Venom Blood:** hitting her from within 5 blocks poisons you.

> [!TIP]
> Kill the brood first. While three or more spiders live, the Venom Matron takes 70% less damage.

### Bone Marshal

- **Volley** every 10 seconds: 3 arrows, 5 from tier II.
- **Arrow Rain** every 14 seconds from tier II on a marked circle under you.
- **Tactical Retreat** from tier II: it leaps away when you get too close.
- **Piercing Shot** from tier III: it draws its greatbow first. Break line of sight.
- **War Banner** below half health from tier III: it takes half damage and heals 1% a second until you tear the banner down (3 hits, 5 at tier IV).
- **Legion** and **Bone Shield** (tier IV): it calls archers and a bone knight, and every 20 seconds raises a shield that stops most damage for 4 seconds.

### Void Stalker

- **Void Bolt** every 8 seconds: 8% (tier I) to 14% (tier IV) of your maximum health, plus Darkness. It also blinks behind you every 10 seconds.
- **Null Shield** from tier II, at two thirds and one third health: you must land 5 hits (8 at tier III, 12 at tier IV) to break it.
- **Void Beacon** every 20 seconds from tier III: a beacon that detonates for half your health unless you break it in 6 seconds.
- **Mirror Images** (tier III) split off Void Echoes, and it takes half damage while they live.
- **Rifts** (tier IV): rifts burst under you for a quarter of your maximum health. At a quarter of its health it triggers **Void Collapse**, and **Void Siphon** heals it whenever rifts or the collapse hurt someone.

## Slayer gear

Gear is made at the Artisan Bench, on its **Slayer** tab, from the line's token, Valor Seals and a filler. See [Crafting](crafting).

### Accessories (slayer level 3)

Each costs 32 tokens, 4 Valor Seals and a filler.

{{items:skyrpg_items:tyrants_amulet, skyrpg_items:broodweave_cloak, skyrpg_items:marshals_belt, skyrpg_items:voidbound_bracers}}

| Accessory | Filler |
| --- | --- |
| Tyrant's Amulet | 16 iron ingots |
| Broodweave Cloak | 64 string |
| Marshal's Belt | 24 leather |
| Voidbound Bracers | 16 ender pearls |

### Weapons (slayer level 6)

Each costs the line's rare drop, 128 tokens, 16 Valor Seals and 4 {{item:skyrpg_combat:dread_fang|Dread Fangs}}. Each does extra damage to its own line's mobs and boss.

{{items:skyrpg_items:gravebane, skyrpg_items:broodfang, skyrpg_items:marrowblade, skyrpg_items:voidrender}}

### Armour

Four sets, one per line. Boots, helmet, leggings and chestplate unlock at slayer level 2, 4, 5 and 7. The chestplate also takes one rare drop.

| Piece | Tokens | Valor Seals |
| --- | --- | --- |
| Boots | 40 | 4 |
| Helmet | 64 | 6 |
| Leggings | 96 | 8 |
| Chestplate | 160 | 12 |

| Set | Line | Filler (boots / helmet / leggings / chestplate) |
| --- | --- | --- |
| Gravewarden | Grave Tyrant | iron ingot 24 / 8 / 16 / 12 |
| Silkstalker | Broodmother | string 48 / 16 / 32 / 24 |
| Marshal's Bonemail | Bone Marshal | bone 48 / 16 / 32 / 24 |
| Voidwalker | Void Stalker | obsidian 12 / 4 / 8 / 6 |

{{items:skyrpg_items:gravewarden_helmet, skyrpg_items:gravewarden_chestplate, skyrpg_items:gravewarden_leggings, skyrpg_items:gravewarden_boots}}

{{items:skyrpg_items:silkstalker_helmet, skyrpg_items:silkstalker_chestplate, skyrpg_items:silkstalker_leggings, skyrpg_items:silkstalker_boots}}

{{items:skyrpg_items:marshals_bonemail_helmet, skyrpg_items:marshals_bonemail_chestplate, skyrpg_items:marshals_bonemail_leggings, skyrpg_items:marshals_bonemail_boots}}

{{items:skyrpg_items:voidwalker_helmet, skyrpg_items:voidwalker_chestplate, skyrpg_items:voidwalker_leggings, skyrpg_items:voidwalker_boots}}

**Set bonus** (all four pieces): you take **30% less** damage from that line's mobs and its boss, deal **10% more** to them, and earn **25% more** slayer XP from that line's bosses. The reduction applies before Defense. The set also gives stats:

| Set | Bonus stats |
| --- | --- |
| Gravewarden | +40 Health, +20 Defense |
| Silkstalker | +5% Crit Chance, +25% Crit Damage |
| Marshal's Bonemail | +30 Strength, +20 Defense |
| Voidwalker | +60 Intelligence, +30% Crit Damage |

The Voidwalker set also has a slow orbit of void shards around you. See [Armour sets](armour-sets) and [Weapons](weapons).

## Spawners

Morwen sells spawners in a fixed order, the **spawner ladder**. The first is open to everyone. Each next one unlocks once you have killed **5,000** of the mob before it (your lifetime kills count).

| Step | Spawner | Price |
| --- | --- | --- |
| 1 | Rabbit | 5,000 |
| 2 | Chicken | 10,000 |
| 3 | Pig | 20,000 |
| 4 | Sheep | 30,000 |
| 5 | Cow | 50,000 |
| 6 | Zombie | 75,000 |
| 7 | Spider | 100,000 |
| 8 | Skeleton | 150,000 |
| 9 | Enderman | 250,000 |
| 10 | Blaze | 400,000 |

Steps unlock in order, so 5,000 zombie kills from normal play do not skip you past the chickens. A bought {{item:skyrpg_combat:mob_spawner|spawner}} places like a block. Breaking one you placed gives it back, with no XP. Spawners found in dungeons stay vanilla and drop nothing.

> [!WARNING]
> Mobs from spawners are **farmed**: normal loot, a quarter of the level XP, and no Valor Marks, rare drops or Elites. They do not count for slayer quests. A spawner is for materials, not for levelling.

## See also

- [Mobs](mobs): the custom mobs that count for each line.
- [Combat basics](combat-basics): levels, Valor Marks and Elites.
- [Stats](stats)
- [Weapons](weapons) and [Armour sets](armour-sets)
- [World bosses](world-bosses)
- [Crafting](crafting)
