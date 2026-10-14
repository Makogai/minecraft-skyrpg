# SkyHelper

The in-game assistant. Players ask `@skyhelper <question>` in chat (public answer) or `/ask <question>` (private), and a
local AI model answers from the wiki in a few seconds. Player page: `docs/wiki/pages/interface/skyhelper.md`.

## Pieces

| Piece | Where | Job |
| --- | --- | --- |
| `skyrpg_helper` mod | `helper/` | Catches `@skyhelper`/`@helper` chat and `/ask`, builds the player context, posts to the service, prints the answer with a `[Wiki]` button (`/wiki <page>#<anchor>`). Server side only; clients don't need it. |
| Helper service | `tools/skyhelper/helper.py` | HTTP on `127.0.0.1:8770` (`POST /ask`, `GET /health`). Standard library only. |
| Knowledge | `tools/skyhelper/knowledge.py` | Loads the in-game wiki bundle (`wiki/src/main/resources/assets/skyrpg_wiki/wiki/`), cuts pages into sections, BM25 search, a name index (page titles, h3 headings such as NPCs and bosses, NPC first names, first cells of table rows such as vanilla items on price lists), table-row mentions and sell prices. Reloads when `index.json` changes. |
| Vanilla recipes | `tools/skyhelper/vanilla.py` | Every vanilla recipe from the Minecraft client jar in the Gradle cache, spelled out row by row. |
| Model | Ollama, `qwen3.5:9b` | `SKYHELPER_MODEL` picks another; `SKYHELPER_THINK=1` turns reasoning on. |
| Test set | `tools/skyhelper/eval.py` | 27 questions with the facts each answer must contain. |
| `PlayerFacts` | `core/.../data/PlayerFacts.java` | Mods register short facts about a player (progress registers `quest`). The context sends them all. |

## How a question is answered

1. **Plan.** One short model call (JSON output, temperature 0) rewrites the question in plain English with "this"/"it"
   resolved from the held item and the last two exchanges, lists the named things it is about, and flags vanilla
   recipe questions. This makes retrieval work for other languages, typos and pronouns.
2. **Retrieve.** For each named thing (from the plan and from names found in the text): its page or section, every
   table row that mentions it, and its sell price. Vanilla recipes when flagged. Then BM25 on the question with the
   names taken out (what is wanted: sell, craft, find) and on the whole question. About 8,000 characters in all.
3. **Answer.** The system prompt (rules plus a one-line map of every written page) stays the same for every question, so
   Ollama reuses its cached prefix. The user message carries the player context, the question and the excerpts. The
   model may still call `search_wiki`, `lookup`, `vanilla_recipe` and `read_page` (three rounds at most).
4. **Clean.** Markdown is stripped, the answer is cut to 560 characters at a sentence end, and a trailing `[[page-id]]`
   becomes the `[Wiki]` button.

Each player's last three exchanges are kept for 15 minutes for follow-ups. Every question is logged to
`run/skyhelper/log.jsonl`.

## Decisions

- **No MCP server.** Ollama takes tool definitions directly, so the service runs the tool loop itself. The same
  functions could be exposed over MCP later.
- **Retrieval before the model asks.** A 9B model rarely calls tools on its own; putting the likely facts in front of
  it first made the difference (20/25 → 26/27 on the test set).
- **Thinking off by default.** With thinking, the test set passes 27/27 but answers take 5 to 10 seconds instead of
  2 to 4. It is a switch (`SKYHELPER_THINK=1`); thinking is capped at 1,500 tokens and falls back to a plain answer.
- **Server starts the service.** On server start the mod checks `/health` and launches `python tools/skyhelper/helper.py`
  (found by walking up from the server folder) when nothing answers; it stops the process it started on shutdown.
  `config/skyrpg/helper.json` turns this off or points elsewhere.
- **Context.** Held item (name, id, count), off hand, armour, inventory (30 lines), world, position, biome, danger
  region, health, mana, purse, combat tag, five skill levels, non-zero stats, registered facts (quest), day or night,
  Blood Moon and who is online.
- **Rate limits.** One question in flight per player and 8 seconds between questions; the service runs one model call
  at a time.

## Ideas

- Register more `PlayerFacts`: party members, pet, mount, collections close to a tier.
- Let answers carry a waypoint ("[Track]") when they name a place with coordinates.
- Grow `eval.py` from real questions in the log.
