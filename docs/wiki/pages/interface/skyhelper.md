---
title: SkyHelper
category: interface
order: 6
icon: minecraft:allay_spawn_egg
summary: The in-game assistant: ask it anything about SkyRPG in chat, and it answers from the wiki in a few seconds.
---
SkyHelper is SkyRPG's assistant. Ask it a question in chat and it answers within a few seconds, using what this wiki says: where to sell something, how to craft an item, where a dungeon is, what a stat does, what to do next. It runs on the server's own computer as a small AI model, so it costs nothing and nothing you ask leaves the server.

## Asking a question

| How | Who sees it |
| --- | --- |
| `@skyhelper <question>` in chat | Everyone: your question and its answer |
| `@helper <question>` in chat | The same, shorter |
| `/ask <question>` | Only you |

For example:

- `@skyhelper where can I sell this?`
- `@skyhelper how do I tame a griffin?`
- `/ask what should I do next?`
- `@skyhelper kako da napravim piston?` (it answers in the language you ask in)

While it works, "SkyHelper is thinking..." shows above your hotbar. The answer comes back as a line starting with **[SkyHelper]**, in light blue, with a **[Wiki]** button at the end. Click it to open the wiki page the answer came from. SkyHelper lines show in the **All** and **Chat** tabs, with a light-blue stripe (see [Chat](chat)).

## What it knows about you

SkyHelper sees what you are holding, what you carry and wear, where you are (the hub or survival world, the biome and any danger region), your health, mana, coins, skill levels, stats and the quest in your journal. So:

- "this", "it" and "my item" mean the item in your hand. Hold a Dread Fang and ask `@skyhelper how much is this worth?`.
- "What should I do next?" gets an answer for your levels, your gear and your quest.
- It remembers your last three questions for 15 minutes, so you can follow up: "and where do I find that?"

It only answers questions. It cannot give items, coins or run commands for you.

## What it is good at

- Prices, who buys and sells what, and the `/sell` commands.
- Recipes: SkyRPG gear at the Artisan Bench, and vanilla recipes (pistons, furnaces, anything) read from the game itself.
- Where things are: dungeons, bosses, NPCs, regions, waystones.
- How systems work: skills, stats, enchanting, pets, mounts, parties, wands and spells, Blood Moons.
- Plain Minecraft questions (redstone, brewing, mobs).

It is a small model, so it can still get something wrong. If an answer looks odd, click **[Wiki]** and check the page. The wiki is always the final word.

## Limits

- You can ask once every **8 seconds**, and one question at a time.
- Questions longer than 400 characters are cut.
- If SkyHelper is offline you get "I'm asleep right now". Try again in a minute; the server starts it again on its own.

## For the server owner

SkyHelper is the `skyrpg_helper` mod plus a small service, `tools/skyhelper/helper.py`, which asks a local [Ollama](https://ollama.com) model (`qwen3.5:9b` by default). The server starts the service when it starts and stops it when it stops. The service reads the in-game wiki files, so every wiki rebuild teaches SkyHelper the new content.

| Command | What it does |
| --- | --- |
| `/skyhelper` | How to ask |
| `/skyhelper status` | Whether the service and Ollama are running (operators) |
| `/skyhelper start` | Start the service if it is not running (operators) |

Settings are in `config/skyrpg/helper.json`: `enabled`, `url`, `cooldownSeconds`, `timeoutSeconds`, `autostart`, `python` and `script`. Every question and answer is logged to `run/skyhelper/log.jsonl`, to find answers worth improving. `python tools/skyhelper/eval.py` asks 27 test questions and checks the answers.
