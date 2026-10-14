# The SkyRPG Wiki

One wiki, two places: a **website** and an **in-game wiki** (K menu → Wiki, `/wiki`). Both are built from the same source by
one script, so they always say the same thing. Decided 2026-10-14 (design calls are delegated).

**Rule:** every change or addition to the game also updates the wiki, in the same piece of work: edit or add the page in
`docs/wiki/pages/`, run `python tools/wiki/build.py`, check the output, and republish the website with
`python tools/wiki/publish_pages.py` (GitHub Pages: https://makogai.github.io/minecraft-skyrpg/). Reference pages (items,
enchants, mobs, pets...) are generated from the game's data, so they update when the data changes, but they still need a
rebuild.

## Layout

| Path | What |
| --- | --- |
| `docs/wiki/pages/<folder>/<id>.md` | Hand-written pages (guides). Folders only organise files; the page's category comes from its front matter. |
| `docs/wiki/images/` | Screenshots used by pages (website only). |
| `tools/wiki/build.py` | The builder: reads the pages and the game data, writes both outputs. `--check` validates links and ids without writing. |
| `wiki/` | The in-game wiki mod (`skyrpg_wiki`, client side). |
| `wiki/src/main/resources/assets/skyrpg_wiki/wiki/` | Generated in-game bundle: `index.json` and `pages/<id>.json`. Never hand edit. |
| `dist/wiki/` | Generated website: `skyrpg-wiki.html` (the page body, for the claude.ai artifact) and `index.html` (a complete standalone file for any static host). |

## Page source

Front matter, then Markdown:

```markdown
---
title: Fishing
category: skills
order: 5
icon: minecraft:fishing_rod
summary: Catch fish, Tidescales and sea creatures, and level the Fishing skill.
---
Text...
```

- **id** = the file name without `.md`: lowercase, words joined by `-`, unique across all folders.
- **category** is one of the ids below; **order** sorts pages inside it; **icon** is an item id; **summary** is one line
  (search results, category lists, link previews).

Categories, in order: `getting-started` Getting Started, `skyhaven` Skyhaven, `economy` Economy, `skills` Skills,
`gear` Gear, `combat` Combat, `dungeons` Dungeons, `world` The World, `companions` Pets & Mounts, `interface`
Interface, `reference` Reference.

Markdown that both outputs support (nothing else):
- `##` and `###` headings (the page title is the front matter `title`; never use `#`). Each heading gets an anchor: its
  text lowercased, non-alphanumerics turned into `-`.
- Paragraphs, `**bold**`, `*italic*`, `` `code` `` (commands, keys).
- Lists: `- ` or `1. `, one nested level (two spaces).
- Tables (GitHub pipe tables, header row required).
- Callouts: `> [!TIP]`, `> [!NOTE]` or `> [!WARNING]` on the first line, then `> text` lines.
- Links: `[text](page-id)`, `[text](page-id#anchor)`, `[text](#anchor)` (same page), `[text](https://...)` (website only;
  in game the text shows without the link).
- `---` for a divider. `![caption](image.png)` from `docs/wiki/images/` (website only; in game the caption shows).
- **Items:** `{{item:namespace:path}}` inline (icon + the item's name in its rarity colour, linking to its reference
  entry), `{{item:namespace:path|other text}}` with custom text, and `{{items:id1, id2, ...}}` on its own line for a
  table of item cards (icon, name, rarity, key stats, sell price, how to get).

## Reference pages (generated)

`build.py` writes these from the mods' data into the same page model (they never exist as Markdown): all items by kind
(each item has the anchor `item-<namespace>-<path>`: stats, perks, ability, description, rarity, sell price, recipe,
known sources), enchants, mobs and bosses, pets, mounts, collections, quests, sell prices and danger regions.

## In-game bundle format

`index.json`:

```json
{"version": 1, "generated": "2026-10-14T12:00:00Z", "web_url": "https://...",
 "categories": [{"id": "skills", "name": "Skills", "icon": "minecraft:iron_pickaxe"}],
 "pages": [{"id": "fishing", "title": "Fishing", "category": "skills", "order": 5, "icon": "minecraft:fishing_rod",
            "summary": "...", "words": ["fishing", "tidescale", "..."]}]}
```

`words` is the search index for the page (lowercase, deduplicated, from its title, headings and text). The index also has
`"items": {"ns:path": "page-id"}`, the reference page that holds each item's entry, so the client can jump from an
item to its anchor `item-<namespace>-<path>`.

`pages/<id>.json`: `{"id", "title", "category", "icon", "summary", "blocks": [...]}`. Blocks:

| Block | Fields |
| --- | --- |
| heading | `{"t": "h2" or "h3", "text": "...", "anchor": "..."}` |
| paragraph | `{"t": "p", "s": [spans]}` |
| list | `{"t": "ul" or "ol", "items": [{"s": [spans], "sub": [{"s": [spans]}]}]}` |
| table | `{"t": "table", "head": [[spans], ...], "rows": [[[spans], ...], ...], "align": ["l", "r", "c", ...]}` |
| callout | `{"t": "callout", "kind": "tip" or "note" or "warning", "b": [blocks]}` |
| item card | `{"t": "item", "id": "ns:path", "icon": "model id", "name": "...", "rarity": "legendary", "color": "#RRGGBB", "anchor": "item-ns-path", "lines": [[spans], ...]}` |
| divider | `{"t": "hr"}` |
| image | `{"t": "img", "caption": "..."}` |

Spans: `{"x": "text"}` plus any of `"b": true` (bold), `"i": true` (italic), `"c": true` (code), `"color": "#RRGGBB"`,
`"link": "page-id#anchor"` (internal), `"url": "https://..."` (external), `"item": "ns:path"` with `"icon": "model id"`
(draw the item's icon before the text). Icons are item **model** ids: the client draws them by setting the
`minecraft:item_model` component on a stack, so it needs no access to the server's item registry.

## The in-game wiki (`skyrpg_wiki`)

- **K menu → Wiki:** categories and pages on the left with a search box, the page on the right (scrolling), back and
  forward, and an "Open on the web" button that copies or opens `web_url`.
- **`/wiki [page]`** opens it, at a page if given.
- **Look it up:** hover an item in any inventory and press the wiki key (default `H`) to open its reference entry.
- Links between pages work, item icons draw inline, and hovering an item span shows its name and rarity.

## The website

A single HTML file with every page embedded (JSON) and icons inlined as `data:` URIs, so it can be put on any static host.
It's hosted on **GitHub Pages** at https://makogai.github.io/minecraft-skyrpg/ (repo `Makogai/minecraft-skyrpg`, public):
`tools/wiki/publish_pages.py` builds it and pushes `dist/wiki/index.html` to the `gh-pages` branch (a worktree in
`.gh-pages/`). It's built locally rather than by a GitHub action because the icons come from the Minecraft jar in the local
Gradle cache. A copy is also published as a claude.ai artifact (`skyrpg-wiki.html`). It has a sidebar of categories, search, item cards styled like SkyRPG's
in-game tooltips with rarity colours, deep links (`#fishing`, `#fishing--sea-creatures`), and light and dark themes.

## Page list (2026-10-14)

Ids are fixed so pages can link to each other before they exist. Reference ids are generated (see above).

| Category | Pages |
| --- | --- |
| getting-started | `welcome`, `first-steps`, `controls-and-keys`, `progression-guide` |
| skyhaven | `skyhaven`, `npcs`, `quests-and-journal`, `events` |
| economy | `coins-and-bank`, `selling-and-shops`, `contracts`, `collections` |
| skills | `skills-overview`, `mining`, `farming`, `foraging`, `fishing`, `enchanting`, `crafting`, `delving` |
| gear | `weapons`, `armour-sets`, `tools`, `accessories`, `backpacks` |
| combat | `stats`, `combat-basics`, `mobs`, `slayers`, `world-bosses` |
| dungeons | `dungeons-overview`, `forgotten-crypt`, `spider-hollow`, `drowned-temple`, `dwarven-halls`, `infernal-citadel`, `ruined-citadel`, `catacombs`, `astral-observatory` |
| world | `survival-world`, `waystones-and-travel`, `danger-regions`, `region-hearts`, `blood-moon`, `the-deep`, `the-nether`, `the-end`, `ender-wyrm` |
| companions | `pets`, `mounts` |
| interface | `k-menu`, `chat`, `hud-and-minimap`, `waypoints`, `settings` |
| reference | `items-weapons`, `items-armour`, `items-tools`, `items-accessories`, `items-materials`, `items-consumables`, `items-other`, `enchant-list`, `mob-list`, `boss-list`, `pet-list`, `mount-list`, `collection-list`, `quest-list`, `sell-prices`, `region-list` |
