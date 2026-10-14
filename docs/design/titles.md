# Earnable titles

A title is a small prefix (an icon and up to about 12 letters, in a colour) that a player earns and wears before their name.
Decided 2026-10-14 (design calls are delegated).

## Pieces

- **Catalogue** (progress mod): `data/<ns>/skyrpg/titles/<id>.json`, loaded with `JsonDataLoader`, reloads with `/reload`. Fields:
  `text`, `icon`, `rarity` (colour), optional `color`, `category`, `description`, `order`, optional `hidden`, `condition`.
  `icon` is a chat icon-font glyph (`` to ``, drawn as the pixel icon) or a plain unicode symbol (drawn in the title colour).
- **Conditions** (`TitleConditions`): `skill_level`, `any_skill_level`, `all_skills`, `skyrpg_level`, `slayer_level`, `boss_kill`, `kind_kills`,
  `collections`, `coins`, `chronicle`, `stat` (vanilla statistic), `counter`, `flag`. They are checked by polling the player's state every
  2 seconds (and right after a boss kill or a granted flag), so every title is **retroactive**: it is awarded the first time the state is
  seen. Unlocks are permanent (saved in the `skyrpg_progress:titles` attachment).
- **Hooks for what state cannot show:** `BossKills.KILLED` (kill counters per boss and per kind; seeded on join from the SkyRPG Level's
  first-kill list), Blood Moon seconds and kills (counters), and the core event `TitleFlags.GRANTED` which other mods fire
  (mounts: `griffin_tamed`, `griffin_legendary`, also granted on join for whistles already owned; party: `party_joined`, `party_leader4`).
- **Boss ids** in `boss_kill` may be a full id or just a path: `ender_wyrm` matches whichever mod owns the Ender Wyrm, so the Wyrmslayer
  title works without touching combat code.
- **Choosing:** one title at a time. K menu page "Titles" (`TitlesPage`), `/title`, `/title locked`, `/title set <id>`, `/title clear`.
  Admins: `/title grant|revoke <player> <id|all>`. The first title earned is worn automatically.
- **Unlock notice:** a `TITLE UNLOCKED` chat notice with an `[Equip]` button, an action-bar line and a toast sound. More than three at
  once (the retroactive first run) collapse into one `TITLES UNLOCKED` line.

## Showing it

- Core `PlayerTitles` is the bridge: the progress mod installs "the player's equipped title", the chat mod reads it (chat never depends on progress).
- Chat: `Titles.effective(player)` = the admin title (`/chat title`) if set, else the equipped earned one. It draws as the pill before the name
  and on the hover card. **A staff title replaces the earned one while set**; nothing is lost. Names are never recoloured by titles.
- Tab list: `TitleSync` (chat mod) sends every online player's shown title to every client (full list on join, then changes every 2 seconds,
  like `LevelSync`); `SkyRpgTab.registerTitle` draws it as the pill after the name, icon included, cut with "..." when the row is short.
  It replaces the pill taken from a scoreboard team prefix when a player has one.
- Not done: a nameplate above the head.

## Catalogue (32)

Skills (Mining, Farming, Foraging, Fishing, Combat 25; all five 25; all five 50), SkyRPG Level 10 / 50 / 100 / 200, dungeon bosses (any, four,
all eight), Hollow King, Mother of Thorns, Ender Wyrm, slayer level 5 and all four at 9, collections (five and all complete), griffin
(any, Legendary), Blood Moon (ten minutes, 100 kills), coins (100k, 2M), party (join, lead four or more), the Chronicle, and two fun
ones (Phoenix: die 25 times, hidden; Old Timer: 100 hours). The full list with ids is on the wiki page `interface/titles.md`; keep it in
step with the JSON files.
