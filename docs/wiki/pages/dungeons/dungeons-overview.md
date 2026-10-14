---
title: Dungeons overview
category: dungeons
order: 1
icon: minecraft:recovery_compass
summary: How SkyRPG dungeons work: finding them, mobs, traps, puzzles, keys, champions, bosses, per-player loot, resets and the Delving skill.
---
Dungeons are real structures in the world, generated from the seed like villages. You find one, walk in, fight through it, and it is still there tomorrow. There are eight, from the Forgotten Crypt at level 12 to the Astral Observatory at level 64. Each one has two or three floors, packs of mobs, traps, puzzles, a champion mini-boss and a final boss. Loot is per player, so friends never race each other for chests. Clearing dungeons is also how you level the **Delving** skill and get the gear that makes the next dungeon possible.

## The eight dungeons

| Dungeon | Where | Dungeon level | Mob levels | Champion | Boss |
| --- | --- | --- | --- | --- | --- |
| [Forgotten Crypt](forgotten-crypt) | forests, plains, meadows | 12 | 12 to 19 | The Bone Knight | The Crypt Warden |
| [Spider Hollow](spider-hollow) | dark forests, pale gardens | 22 | 19 to 26 | The Silk Matron | Queen Arachne |
| [Drowned Temple](drowned-temple) | oceans, on the sea floor | 28 | 26 to 34 | The Abyssal Herald | The Tidemother |
| [Dwarven Halls](dwarven-halls) | under mountains, hills, forests | 34 | 32 to 41 | The Forgeguard | The Iron Thane |
| [Infernal Citadel](infernal-citadel) | the Nether | 48 | 44 to 52 | The Infernal Knight | The Magma Tyrant |
| [Ruined Citadel](ruined-citadel) | mountain peaks | 56 | 54 to 58 | The Knight-Captain | The Fallen Knight |
| [Catacombs](catacombs) | deep underground, sealed | 62 | 59 to 62 | three Bone Wardens | The Bonelord |
| [Astral Observatory](astral-observatory) | the outer End islands | 64 | 60 to 65 | The Astral Sentinel | The Star Warden |

Nothing stops you entering early. The levels are a guide to how tough the mobs are. Compare them with the levels on your mobs' nameplates (see [Combat basics](combat-basics)) and with your [Stats](stats).

## Finding a dungeon

Each dungeon has a rarity. The spacing is the size, in chunks, of the grid cell that gets one attempt.

| Dungeon | Spacing (chunks) | Notes |
| --- | --- | --- |
| Astral Observatory | 26 | outer End islands only, 6 chunks from End cities |
| Forgotten Crypt | 28 | common |
| Drowned Temple | 32 | not in frozen oceans |
| Dwarven Halls | 40 | 8 chunks from Spider Hollows |
| Infernal Citadel | 44 | 8 chunks from vanilla fortresses and bastions |
| Spider Hollow | 18 | 10 chunks from Forgotten Crypts |
| Ruined Citadel | 56 | 10 chunks from Dwarven Halls |
| Catacombs | 64 | very rare, no surface part |

Ways to find them:

- **Treasure maps.** The Crypt Warden has a 40% chance of a Spider Hollow map, and Queen Arachne a 40% chance of a Drowned Temple map. Wayshrine chests can hold a Forgotten Crypt map (25%), and Abandoned Mine Camp chests a Dwarven Halls map (15%). Use them like vanilla explorer maps.
- **Waystones.** Every dungeon entrance except the Catacombs has a waystone, so once you attune to it you can travel back. See [Waystones and travel](waystones-and-travel).
- **The questline.** The Skyhaven Chronicle has a quest for most dungeons. Track the quest in your journal and a waypoint points at the nearest matching dungeon within 100 chunks (1,600 blocks). This is the way to find the Catacombs, which has no surface part. See [Quests and journal](quests-and-journal) and [Waypoints](waypoints).
- **Exploring.** The deeper dungeons have no map. Look for them in the places listed in the table above.

Walking within 20 blocks of an entrance **discovers** it: a banner, a sound, a marker on the map and minimap, and an entry in your journal. Discovery also gives Delving XP.

## Inside a dungeon

A dungeon is a chain of rooms built from a seed, so no two are the same. Every dungeon has:

- **Floors.** Most have two floors joined by a stairwell or descent. The Ruined Citadel has three, and the Catacombs is one huge labyrinth. Each floor is a spine of big halls with side rooms branching off them.
- **A champion arena** at the end of the first floor, whose gate stays shut until the champion falls. (The Catacombs have three Bone Warden chambers instead.)
- **A boss arena** at the end, about 31 by 15 by 31 blocks.
- **A vault** behind a locked door, with the best chests.
- **Side rooms:** libraries, barracks, shrines, treasuries, puzzle rooms, and small hidden caches.

### Mob packs

Mobs wait in packs of 4 to 12, and many packs come in **waves**: the next wave spawns when the one before it is dead. A pack wakes when you come within about 10 blocks. Dungeon mobs never despawn. Packs are levelled by floor, and they pay the usual combat rewards: Combat XP, Valor Marks and the rest (see [Combat basics](combat-basics)).

**Elites** in packs are tougher and glow, and each has a **35% chance to drop the dungeon's vault key**. When every mob from a room's packs is dead, the room is cleared. Some gates only open then.

### Traps

Traps only work while a player is nearby.

| Trap | What it does |
| --- | --- |
| Arrows | An arrow slit in a wall shoots the nearest player it can see. |
| Spikes | Hurt anyone standing on or close to them. They are marked with crit particles. |
| Flames | A fire burst that sets players on fire. |
| Poison | A short poison cloud. |

Trap damage is in vanilla damage points (1 point is 5 Health) before your Defense.

> [!TIP]
> Arrow slits are iron bars set in a wall, with a trap behind them. Keep your shield up or sprint past, and do not stop in the line of fire.

### Puzzles and doors

| Part | How it works |
| --- | --- |
| Levers | A group of levers must all be in the right position. A sign or glowing text nearby gives the hint, for example "Wake the first and the last". |
| Pressure plates | Step on every plate in the room. Each lights up for good (until the dungeon resets). |
| Gates | Some doors open when you clear a room, or when a champion falls. |
| Key doors | A vault door opens when you right-click it holding the dungeon's key. One key is used up. |

Doors close again when the dungeon resets.

### Secrets

A hidden cache, a break-in wall or a sealed alcove is a **secret**. Walk within about 2.5 blocks the first time and you get "Secret found", **250 coins** and **150 Delving XP**, once per player per secret. Secrets also add to the Reward Chest (below).

### Keys and vaults

| Dungeon | Vault key | Where it comes from |
| --- | --- | --- |
| Forgotten Crypt | {{item:skyrpg_world:crypt_key}} | boss always, champion 35%, Elites 35% |
| Spider Hollow | {{item:skyrpg_world:hollow_key}} | boss always, Elites 35% |
| Drowned Temple | {{item:skyrpg_world:tide_key}} | boss always, champion 35%, Elites 35% |
| Dwarven Halls | {{item:skyrpg_world:thane_key}} | champion always, boss always, Elites 35% |
| Infernal Citadel | {{item:skyrpg_world:citadel_key}} | champion always, boss always, Elites 35% |
| Ruined Citadel | {{item:skyrpg_world:citadel_crest_key}} | champion always, boss always, Elites 35% |
| Catacombs | {{item:skyrpg_world:ossuary_key}} | three are needed: one from each Bone Warden, and Elites 35% |
| Astral Observatory | {{item:skyrpg_world:observatory_key}} | champion always, boss always, Elites 35% |

You can open the vault before or after the boss.

## Champions and bosses

A **champion** is a mini-boss on the first floor. It has a boss bar, its own moves, coins and Delving XP, a share of loot, and often the key. It does not leave a Reward Chest. A **boss** guards the last room and has several phases.

How the fight works:

- The boss wakes when you walk into its room. A boss nobody fights for 30 seconds goes back to sleep and returns at full health.
- It has more health the bigger the party: **+60% for each extra player** within 32 blocks when it wakes. The party size shows in chat.
- A boss that strays more than 28 blocks from its room is teleported home.
- Phases are announced at two thirds and one third of its health. New moves unlock with them, and at one third it enrages (faster and harder hitting).
- Bosses stronger than vanilla's health cap take less damage instead, and the bar shows the real health.
- Moves are announced in the action bar before they land (for example "The Warden raises his blade!"), and slams are marked with a ring on the floor.

| Move | What it does |
| --- | --- |
| Slam | A marked ring, then damage and a launch for everyone inside. |
| Leap | Jumps at a player and hits everyone near the landing spot. |
| Volley | Fires arrows, fireballs, web shots or tridents at its target. |
| Summon | Calls minions around it, up to a limit. |
| Pull | Drags every player in range towards the boss. |
| Shockwave | Knocks everyone in range away and hurts them. |
| Blink | Teleports behind its target. |
| Webs | Temporary cobwebs around each nearby player. |
| Molten | The floor around each player glows, then turns to magma and burns for a few seconds. Keep moving. |
| Cloud | A lingering poison, wither or blindness cloud. |
| Ward | The boss takes much less damage for a few seconds. Hold your big attacks. |
| Heal | The boss heals a share of its health. |
| Enrage | Once, below one third health: faster and stronger. |

Move damage in dungeon pages is in vanilla damage points (1 point is 5 Health on the stats scale), before Defense.

> [!TIP]
> When the boss warns "Fire geysers! Keep moving!" or "The floor glows red-hot", leave the marked spots at once. When it wards, back off and wait, because wards cut damage by 70% to 90%.

## Rewards

### Chests

**Every chest is per player.** Each player rolls their own loot from every chest, so nothing is stolen, and what you leave in your copy stays there until the dungeon resets. Each chest also pays coins and Delving XP by its tier and the dungeon's level.

| Tier | Coins per dungeon level | Delving XP per dungeon level | In a level 12 dungeon |
| --- | --- | --- | --- |
| Common | 15 | 3.5 | about 180 coins, 42 XP |
| Rare | 30 | 7 | about 360 coins, 84 XP |
| Secret | 45 | 10 | about 540 coins, 120 XP |
| Vault | 100 | 25 | about 1,200 coins, 300 XP |

Coins vary by up to 20% either way.

### Boss and champion kills

Everyone who hit the boss and is within 64 blocks gets:

- coins and Delving XP (each boss's page lists them),
- their own roll of the boss's loot table,
- the **first clear** bonus, once per boss (most bosses and champions have one): coins, Combat XP and a "FIRST CLEAR" banner.

Bosses leave a **Reward Chest** where they fall: a per-player chest, only the party that won can open it. It rolls its table once, plus **bonus rolls** for how thoroughly you cleared the dungeon: one bonus per 3 packs cleared, one per secret found and two per champion slain, up to 8. It disappears when the dungeon resets. Run through to the boss and you get one roll. Clear the place and you get several.

Bosses also drop **enchant tomes**, and champions drop lower level ones. Boss tokens are guaranteed.

### Resets

A used-up dungeon part comes back **72,000 ticks (one real hour, three in-game days)** after it was used, but only when no player is within 32 blocks. Bosses, packs, chests and puzzle doors all return. First clears and secrets are per player and stay done.

## Dungeon gear and Delving

Bosses drop **boss tokens**, which you take to the Artisan Bench to craft dungeon gear with no luck needed. The bench's "dungeon" recipes need a Delving level.

| Item | Delving | Made from |
| --- | --- | --- |
| {{item:skyrpg_items:cryptbreaker}} | 5 | Warden's Sigil, iron, Marrow Shards, Dread Fangs |
| {{item:skyrpg_items:wardens_shroud_chestplate|Warden's Shroud}} set | 5 | Warden's Sigil, bone, iron, Marrow Shards |
| {{item:skyrpg_items:arachne_fang}}, {{item:skyrpg_items:widowsilk_cloak}} | 12 | Arachne's Eye, string, Brood Silk |
| Carapace of Arachne set | 12 | Arachne's Eye, string, spider eyes |
| {{item:skyrpg_items:tidecaller_blade}}, {{item:skyrpg_items:tidemother_pendant}} | 20 | Tide Pearls, prismarine, Tidescales |
| Tidemother Regalia set | 20 | Tide Pearls, prismarine, gold |
| {{item:skyrpg_items:thanes_hammer}}, Runeforged set | 20 | Thane's Runes, Dwarven Cogs, Duskquartz |
| Hellforged armour | 30 | Hellforged Ingots, magma cream (the chestplate also needs a Tyrant's Core) |

Each dungeon page lists the exact recipes. The later trophies (the Fallen Crest, the Bonelord's Phylactery and the Star Warden's Core) are not crafted into gear. They are kept for the Ender Wyrm altar. See [Ender Wyrm](ender-wyrm).

**Delving** (see [Delving](delving) and [Skills overview](skills-overview)) has 50 levels. Each level gives **+2 Health and +0.25 Magic Find**. Its XP comes from dungeon chests, secrets, champions, bosses and discovering dungeons (100 plus 5 per dungeon level). Its talents are Delver's Vigor (+5 Health a rank), Delver's Luck (+1 Magic Find) and Crypt Hardened (+3 Defense).

## Tips

> [!TIP]
> Bring more than you think. Food, a good ranged weapon, a shield for arrow traps, Water Breathing for the Drowned Temple, fire protection for the Nether, and friends. Boss health goes up by 60% for each extra player, so a party needs more damage, not just more bodies.

- Clear packs and find secrets before the boss. The Reward Chest rolls more often.
- Kill Elites for the key early, so the vault is ready when you want it.
- Do not run at full speed into a room you have not looked at. Packs wake as you come close, and arrow slits cover the corridors.
- Dungeons are the main source of dungeon enchant tomes. See [Enchanting](enchanting).

## See also

- [Forgotten Crypt](forgotten-crypt), [Spider Hollow](spider-hollow), [Drowned Temple](drowned-temple), [Dwarven Halls](dwarven-halls)
- [Infernal Citadel](infernal-citadel), [Ruined Citadel](ruined-citadel), [Catacombs](catacombs), [Astral Observatory](astral-observatory)
- [Delving](delving)
- [Stats](stats) and [Combat basics](combat-basics)
- [Boss list](boss-list)
