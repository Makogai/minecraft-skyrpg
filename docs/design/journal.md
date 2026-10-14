# The Journal: questline, collections and SkyRPG Level

The game has lots of content, but no clear path through it. This adds a **main questline** that walks you through every
system in order, **collections** that reward gathering, and a **SkyRPG Level** that sums everything up. Decided
2026-10-13; the user delegates design calls.

Everything lives in a new `progress` module (mod id `skyrpg_progress`), with two packages:
- `com.skyrpg.progress.collections`, which also holds the SkyRPG Level
- `com.skyrpg.progress.quests`

## SkyRPG Level

- **Earning XP:** SkyRPG XP comes from many places:
  - quest rewards
  - collection tiers: 4 XP per tier reached
  - skill levels: 5 XP per level gained, any skill
  - first dungeon clears: 20 to 60 XP by dungeon tier
  - slayer levels: 10 XP each
  - first kills of each dungeon or world boss: 15 XP
- **Levels:** every 100 XP is one level, with no cap.
- **Where it shows:** stored per player (an attachment, synced to everyone so the chat badge and tab list can read it) and on the Profile page.
- **Chat badge:** the chat's level badge switches from its skill-based number to this level once the progress mod is present (chat reads the synced attachment by id, the same way it reads the active pet).
- **Rewards:** every 10 SkyRPG levels gives a coin reward and a "level up" notice with the `level_up` VFX.

## Collections

- **What counts:** gathered items in five categories:
  - **Mining:** stone, coal, iron, gold, diamond, Skyiron, Duskquartz, Emberite, Lumenite, Hellforged Scrap, Glimmer
  - **Farming:** each crop
  - **Foraging:** each wood
  - **Fishing:** fish, Tidescales, Magma Fish
  - **Combat:** rotten flesh, bone, string, ender pearl, slime, gunpowder, blaze rod, Valor Marks
  The exact list is data: `data/<ns>/skyrpg/collections/<id>.json`.
- **How it counts:** an item counts when you **gain it from the world**:
  - **Picking up an item entity:** only if no player threw or dropped it. Use ItemEntity's owner/thrower, and the existing placed-block anti-abuse.
  - **Items put straight into your inventory:** mining auto-pickup, fishing and lava fishing catches, farming harvest helpers. Those mods fire a core event, `ItemGains.GAINED` (a new Fabric event in core), that the progress mod listens to. Mods never call progress directly, so there are no circular dependencies.
- **Tiers:** I to IX at about 50 / 100 / 250 / 1k / 2.5k / 5k / 10k / 25k / 50k, tuned per item; rarer items have lower thresholds. Each tier gives:
  - SkyRPG XP
  - coins
  - sometimes a small permanent stat (e.g. +1 Mining Fortune at Coal V)
  - sometimes a **recipe unlock**: Artisan Bench recipes can require `"collection": {"id": ..., "tier": N}`, shown locked with "Requires Coal Collection V"
- **Interface:** a **Collections** page in the K menu shows each category with progress bars, the current tier, the next tier's rewards and the unlocked recipes. Reaching a new tier gives a chat notice (COLLECTION).

### Status: collections and the SkyRPG Level (built)

- **Code:** `progress/.../collections/` (`Collections`, `CollectionDef`, `SkyRpgLevel`, `CollectionPayloads`, `ClientCollections`), a mixin
  on `ItemEntity.playerTouch` for pickups, and client `CollectionsPage` / `LevelHeader` / `CollectionsClient`.
- **Core additions:** `event/ItemGains` (`GAINED`, plus `give` that fires it), `event/BossKills` (`KILLED`, fired by the world and
  combat mods for dungeon and world bosses), `progress/CollectionLevels` (tier lookup the bench uses), `Notice.Kind.COLLECTION`,
  and `ProfilePage.Header` (the SkyRPG Level strip).
- **Event sources wired:** vanilla pickups (mixin; skips items with a thrower), mining `AutoPickup.give`, fishing `Catching.give`
  (also lava catches), farming `Harvest.give`, foraging `Chopping.give`. Skill and slayer level-ups come from `SkillLevels.LEVEL_UP`.
- **Data:** 63 collections from `tools/builds/collections_data.py`. Counts are a persistent attachment (`skyrpg_progress:collections`),
  synced to the owner every second at most; definitions are synced on join and `/reload`. The level is `skyrpg_progress:level`
  (XP, synced to all; the chat badge reads it by id).
- **Recipe gating:** `"collection": {"id", "tier"}` in a bench recipe (`BenchRecipe`); ten mid-tier recipes are gated by the generator.
- **Stats:** tiers V and IX give +1 and +2 of a stat (a `Stats.Source` named "Collections").
- **Not yet:** a quest `collection` objective is the quest agent's (use `Collections.count/tier`); no tab list level yet.

## The questline: the Skyhaven Chronicle

- **Data:** quests are data in `data/<ns>/skyrpg/quests/<chapter>/<id>.json`. Chapters unlock in order, and inside a chapter quests are linear or have small branches.
- **Objective types:**
  - `skill_level`, `collection`, `kill` (an entity or tag, with a count)
  - `dungeon_clear`, `slayer_tier`, `craft` (at the Artisan Bench or vanilla), `obtain` (have an item)
  - `visit` (a biome, structure, danger region, deep layer or dimension), `talk` (to an NPC), `reach_level` (SkyRPG Level)
- **Rewards:** coins, items, SkyRPG XP, and sometimes a waystone hint.
- **Chapters:**
  1. **Welcome to Skyhaven:** meet the hub NPCs, open the K menu, gather your first materials, reach skill level 2.
  2. **First Steps:** attune a waystone, craft your first set at the Artisan Bench, reach Combat 5, hatch a pet.
  3. **Into the Dark:** clear the Forgotten Crypt, start your first slayer quest, enchant something.
  4. **The Red Lands:** enter a danger region, collect essences, craft region gear, clear Spider Hollow or the Drowned Temple.
  5. **The Deep:** reach the Magma Rifts, clear the Dwarven Halls.
  6. **Fire Below:** enter the Nether, mine Hellforged, clear the Infernal Citadel, try lava fishing.
  7. **Ancient Ruins:** clear the Ruined Citadel and the Catacombs.
  8. **The Void:** reach the outer End, clear the Astral Observatory, defeat the Ender Wyrm.
- **Journal page in the K menu:** the chapters with completion, the active quest's objectives with progress bars, the rewards, and a **Track** button.
  - For `visit` and `dungeon_clear`, Track finds the nearest matching structure or biome server-side, on demand, with a sensible search radius, and sends it through `com.skyrpg.core.net.WaypointTarget`, so the waypoint compass points there.
- **Quest tracker:** a HUD widget showing the active quest and its next objective with progress.
- **Notices:** chat notices use the kinds QUEST, QUEST COMPLETE and CHAPTER COMPLETE, with the `rare_drop` / `level_up` VFX on completion.

### Status: built (2026-10-13)

The Chronicle is in: 8 chapters and 59 quests (see the dialogue section below for how they play), generated by `python tools/builds/quests.py` into
`data/skyrpg_progress/skyrpg/quests/<chapter>/<id>.json` (each chapter folder also holds `_chapter.json` with title, order,
description and icon). Edit the generator, not the JSON.

- **Engine** (`com.skyrpg.progress.quests`): quests are data, loaded on reload and sent to every client as JSON. A player's
  progress is the `skyrpg_progress:quests` attachment (done quests, counters, tracked quest; saved, synced to the owner).
  A quest opens when its prerequisites are done (a step of parallel quests, then the next step), announces itself once, and
  completes the moment every objective is met. Rewards: coins (Wallet), SkyRPG XP, items, an optional hint line. Notices use
  `QUEST`, `QUEST_COMPLETE` and `CHAPTER_COMPLETE` kinds, banners show new and completed quests, `rare_drop` plays on a quest and
  `level_up` on a chapter.
- **Objective hooks:** `kill` counts through `ServerLivingEntityEvents.AFTER_DEATH` (only while the quest is open); `talk`, Artisan
  Bench `craft` and slayer start/finish use new core events in `ProgressEvents` (fired by npcs, items and combat); vanilla
  `craft` also reads the crafted stat. Polled once a second: `skill_level`, `collection`, `obtain` (inventory), `reach_level`,
  `dungeon_clear` (the world mod's Explorer boss-kill list), `visit` (biome, structure, danger region, deep layer with its y band,
  dimension, attuned waystones, discovered dungeon). `visit` and `obtain` stay done once met. There is also a `journal` objective
  (open the Journal page).
- **Tracking:** the Journal's Track button and `/quest track` find the target on demand and send it with `WaypointTarget`:
  nearest structure (up to 100 chunks, uses the discovered dungeon's position when known), nearest biome (2400 blocks), nearest
  danger region (4800 blocks, sampled through `DangerRegions.at`), a deep layer (your x/z at the layer's middle y) and an NPC (its
  hub position). Targets in another dimension tell you where to go instead.
- **Client:** the K menu Journal page (chapters left, quest right) and the Quest Tracker HUD widget (middle left, under Contracts).
- **Commands:** `/quest` (list), `/quest track [id]`, `/quest abandon <id>` (side quests); ops: `/quest complete|set|reset <players> <id|all>`.
  `set` marks everything before the quest done (no rewards) so it becomes the open quest.

### Dialogue and the RPG questline (built 2026-10-13)

The user found "go talk to X and poof, done" quests weird. Quests are now **conversations**: an NPC offers, explains in
character, you accept, you do the work, and you **return to hand in** (rewards are paid at hand-in).

**Quest flow.** A quest is *locked* (prerequisites not done), *offered* (open, waiting for its giver NPC; gold ! over them),
*active* (accepted, grey ? over the giver), *ready* (all objectives met, waiting at the turn-in NPC; gold ? over them) or *done*.
- Giver: `giver.npc` (an NPC id, or `narrator`/absent = the quest starts by itself with a journal-entry dialogue, shown once the
  player is out of a fight). Turn-in: `turn_in.npc` (default: the giver; `none` = completes as soon as its objectives are met).
- A quest with **no objectives** is an *errand*: accepting it makes it ready at once ("go and see Dorrin").
- `talk` objectives are satisfied only by **finishing that NPC's `talk_<n>` dialogue** (`n` = the objective's index). Nothing
  completes on a bare click any more. The old instant `NPC_TALKED` counter is gone (the event still fires when any NPC dialogue ends).
- Counters (see `QuestState`): `a:<quest>` accepted, `r:<quest>` ready, `t:<quest>#<n>` talk finished, `qv` migrated.
- NPC click priority: hand in (ready) > talk objective > offer > reminder. With none of those, the NPC does its normal thing
  (greeting + shop/bank/enchanting). Offer and reminder conversations of shopkeepers add a "Let me see your wares." choice, and a
  hand-in can end in "show me your wares" (hands in first), so a quest never blocks a shop.
- The quest JSON gained `turn_in` (`{npc,name}`), `dialogue` (the kinds it has: `offer`, `progress`, `complete`, `talk_<n>`) and
  `replaces` (old quest ids, for migration).

**Dialogue data**: `data/<ns>/skyrpg/dialogue/<quest id>/<kind>.json`, found by convention from the quest id
(`skyrpg_progress:01_welcome/your_journal/offer`); any other id works for `/quest dialogue <id>`. Server-only (clients get one
page at a time).
```json
{ "speaker": "skyrpg_npcs:tavern_keeper",
  "pages": [
    { "id": "intro", "mood": "happy", "text": "Welcome, &6{player}&r! Sit down.",
      "choices": [ { "label": "Tell me more.", "action": "goto", "page": "more" },
                   { "label": "I'll take it.", "action": "accept" } ] },
    { "id": "more", "text": "..." } ] }
```
- `speaker`: an NPC id (face, name, colour from the NPC) or `narrator` ("Journal", a book icon).
- `text`: legacy `&` colour codes (`&6` gold, `&l` bold, `&o` italic, `&r` reset), `{player}`; keep it under about 300 characters
  (five lines). `mood` is an emote shown beside the name: `happy laugh thinking serious excited worried angry proud sad surprised wink whisper`.
- A page with no `choices` is "click to continue"; running past the last page **finishes** the conversation.
- Choice `action`: `next` (default), `goto` (+`page` id), `accept` (offer: take the quest; ends), `turn_in` (hand-in: pay out;
  ends), `end` (finish), `close` (cancel, nothing happens), `shop` (run the NPC's own action: shop, bank, enchanting, slayers;
  finishes a hand-in or talk first), `run` with `do`: `waypoint` (`npc`) or `track` (`quest`), then continues (or goes to `page`).
- What finishing does depends on why it was opened: offer = accept, hand-in = turn in, talk = tick the talk objective. Esc,
  `close`, walking away (over 10 blocks from the NPC) or logging off cancel it with no effect.

**Server** (`progress/.../dialogue`): `DialogueData` (loader), `Dialogues` (which conversation an NPC click opens, one session per
player, choice handling, NPC markers), `DialoguePayloads` (`Show` page + choices, `Close`, `Choose` serial + index, `Markers`).
The server sends one page at a time, remembers it (serial number), and accepts only a choice index it offered for that page,
from a player still near the NPC. Conversations can only be opened by the server. Core additions: `NpcInteractions`
(`INTERACT` event the npcs mod calls before its normal behaviour, a public NPC directory, `runAction`) and client `NpcMarkers`.

**Client** (`progress/.../client/dialogue`): `DialogueScreen` is a bottom panel like the K menu's look (own units scaled to the
window, so it keeps its size at any GUI scale): the speaker's skin face (a book for the narrator), name and emote, text that types
itself out (click or Space skips, then continues), choices as buttons above the panel once the text is done (click or number key),
Esc closes. A page that arrives while another screen is open waits until it closes. The **markers** ride in the NPC's name tag
(an `EntityRenderer.extractNameTags` mixin in the npcs client, `NpcMarkers.decorate`), which is robust: no world-space drawing and
it follows the name tag's own visibility.

**Journal**: shows the giver, "Speak to X to begin" / "Return to X to hand in" boxes, and **Re-read** buttons (the intro once
accepted, the hand-in once done). Track points at the giver NPC when offered and at the turn-in NPC when ready. The tracker
widget shows "Speak to X" / "Return to X".

**Migration** (once per player, the `qv` counter): quests the player had already been told about become *accepted* (their progress
carries on); one whose old id is listed in `replaces` and was done is done; a quest whose objectives are already met is marked
done without ceremony; and everything before any done quest is done too (so nobody is stuck behind new early quests). Quest ids
were kept wherever the quest survived; moved ones (the old chapter 1 gathering tasks, now chapter 2) list their old id in `replaces`.

**Admin/testing**: `/quest accept|complete|reset|set <players> <id|all>`, `/quest dialogue <conversation id>` (read-only preview).
`/quest set <you> <id>` makes a quest *offered* (marker over its NPC); `accept` takes it on.

### The quests (59)
1. **Welcome to Skyhaven** (11): A Warm Welcome (narrator; Hollis explains the town, markers, trades, the world); Your Journal (Hollis, K menu); Find Your Way (errand to Dorrin: minimap, M map, waypoints); Safe and Sound (errand to Cassius: purse, bank, interest, /sell, /pay); Meet the Masters (Tamsin: Mining), Timber Talk (Oswin: Foraging), Fields of Gold (Mabel: Farming), Patience, Love (Marina: Fishing), Steel and Nerve (Kestrel: Combat), each with a starter tool; The Artisan Bench (Brannoc); Through the Wildgate (Hollis; the survival world, /hub, waystones).
2. **First Steps** (12): A Stone to Stand On (narrator; waystones); then a task from each Master: Stone and Coal, Timber!, First Harvest, A Good Catch, First Blood; Learning the Ropes (Hollis; skills); Bench Work; Fighter's Resolve; A Friend for the Road and Hatch a Pet (Odessa; pets); Rising Star (Hollis; collections, SkyRPG Level, Contracts).
3. **Into the Dark** (7): A Door in the Woods, Into the Crypt (Kestrel; dungeons, Delving); The Slayer Master, Hunter's Mark (Morwen; slayers); A Touch of Magic (Seraphine; enchanting); Crypt Spoils (Brannoc); Ready for More (Hollis).
4. **The Red Lands** (6): Danger on the Horizon (danger regions), Essence of the Land (Odessa), Regional Pride (Brannoc), Webs and Water, A Bigger Beast (Morwen), Scarlet Scholar (Hollis).
5. **The Deep** (6): Down We Go (Tamsin; the layers), Fungus Among Us (narrator), The Dwarven Halls, Skyiron Smith, Delver's Creed, The Magma Rifts (narrator).
6. **Fire Below** (7): Through the Portal (Kestrel), The Ember Forest (narrator), Hellforged (Tamsin), Lava Angler (Marina), The Infernal Citadel, Forged in Fire (Brannoc), Fireproof (Hollis).
7. **Ancient Ruins** (5): Whispers of Stone (Odessa), The Ruined Citadel, The Catacombs (Kestrel), Veteran of the Hunt (Morwen), Pearls Before the Void (Odessa).
8. **The Void** (5): Edge of the World (Kestrel), The Outer Islands (narrator), The Astral Observatory (Kestrel), The Ender Wyrm and The Chronicle, So Far (Hollis).

Content lives in `tools/builds/quests_ch1_2.py` and `quests_ch3_8.py` (252 dialogue pages); `quests_dsl.py` has the page format.

## Later

Side quests from NPCs, daily quests (Contracts already cover dailies), and minions.
