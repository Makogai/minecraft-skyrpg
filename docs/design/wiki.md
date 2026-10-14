# The SkyRPG Wiki

One wiki, two places: a **website** and an **in-game wiki** (K menu → Wiki, `/wiki`). Both are built from the same source by
one script, so they always say the same thing. Decided 2026-10-14 (design calls are delegated).

**Rule:** every change or addition to the game also updates the wiki, in the same piece of work: edit or add the page in
`docs/wiki/pages/`, run `python tools/wiki/build.py`, check the output, and republish the website with
`python tools/wiki/publish_pages.py` (GitHub Pages: https://makogai.github.io/minecraft-skyrpg/). Reference pages (items,
enchants, mobs, pets...) and the page of every item are generated from the game's data, so they update when the data
changes, but they still need a rebuild.

## Layout

| Path | What |
| --- | --- |
| `docs/wiki/pages/<folder>/<id>.md` | Hand-written pages (guides). Folders only organise files; the page's category comes from its front matter. |
| `docs/wiki/images/` | Screenshots used by pages (website only). |
| `tools/wiki/build.py` | The builder: reads the pages and the game data, writes both outputs. `--check` validates links and ids without writing. Helpers: `refpages.py` (reference pages), `itempages.py` (one page per item), `render3d.py` (offline renderer of item models), `icons.py` (textures), `web.py` and `web/` (the site's shell, CSS and JS). |
| `wiki/` | The in-game wiki mod (`skyrpg_wiki`, client side). |
| `wiki/src/main/resources/assets/skyrpg_wiki/wiki/` | Generated in-game bundle: `index.json` and `pages/<id>.json`. Never hand edit. |
| `dist/wiki/` | Generated website, a small multi-file static site (see "The website"). Never hand edit. |
| `tools/wiki/.cache/` | Build cache (vanilla assets, 3D renders keyed by a hash of the model and its textures). Gitignored. |

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
(`items-weapons` and the other `items-*` listings: one card per item with its stats, perks, ability, description, rarity,
sell price, recipe and known sources; the card keeps the anchor `item-<namespace>-<path>` and links to the item's own
page), enchants, mobs and bosses, pets, mounts, collections, quests, sell prices and danger regions.

### Item pages

Every item that has a reference entry (about 400) also gets **its own page**, id `item-<namespace>-<path>` (`:` and `/`
become `-`, so `skyrpg_items:voidrender` is `item-skyrpg_items-voidrender`), category `reference`, written by
`tools/wiki/itempages.py`. They are not listed one by one in the sidebar or the in-game menu (the website's Reference
category page groups them by kind, and search finds them). Top to bottom:

1. **hero**: the item picture big, the name in the rarity colour, rarity and kind labels;
2. the tooltip lines (stats, perks, ability, description) as a bare item card;
3. **Recipe**: ingredients as item links with counts, the Artisan Bench tab, the skill level and collection tier needed;
4. **Used in**: every recipe that takes this item (links to the results), and shops that take it as payment;
5. **How to get**: shops with prices, mob, boss and chest drops, quests, regions;
6. **Sell price**;
7. **Set**: the set bonus and the other pieces, if the item belongs to a set;
8. **Mentioned in**: the hand-written pages (and their sections) that refer to the item.

Sections with nothing to say are left out. `{{item:...}}` and `{{items:...}}` links, the in-game `items` map and the Look it
up key all go to the item page.

### Big pictures

- **3D items** (every item whose model, through its parents, has `elements`: shields, bows, block items such as sprinklers,
  planters, stumps and ores) are rendered by `tools/wiki/render3d.py`, a small Python renderer of block/item model JSON
  (elements, face UVs, textures, element rotations, `display.gui` view, per-face shading and emission). It draws 256 px
  transparent PNGs at 4x and averages them down, and caches them in `tools/wiki/.cache/renders/` keyed by a hash of the
  model, its display transform and the texture files, so rebuilds are fast. Crossed-plane models (saplings) use the flat
  sprite instead.
- **Flat items** (everything with a plain `item/generated` or `handheld` model, and vanilla items) show their texture big,
  with `image-rendering: pixelated`; no extra file.

## In-game bundle format

`index.json`:

```json
{"version": 1, "generated": "2026-10-14T12:00:00Z", "web_url": "https://...",
 "categories": [{"id": "skills", "name": "Skills", "icon": "minecraft:iron_pickaxe"}],
 "pages": [{"id": "fishing", "title": "Fishing", "category": "skills", "order": 5, "icon": "minecraft:fishing_rod",
            "summary": "...", "words": ["fishing", "tidescale", "..."]}]}
```

`words` is the search index for the page (lowercase, deduplicated, from its title, headings and text). Item pages have
`"hidden": true`: they are left out of the menu and the page lists but still searchable and linkable. The index also has
`"items": {"ns:path": "page-id"}`, the item's own page (`item-<namespace>-<path>`), so the client can jump from an item,
or from the Look it up key, to it.

`pages/<id>.json`: `{"id", "title", "category", "icon", "summary", "blocks": [...]}`. Blocks:

| Block | Fields |
| --- | --- |
| heading | `{"t": "h2" or "h3", "text": "...", "anchor": "..."}` |
| paragraph | `{"t": "p", "s": [spans]}` |
| list | `{"t": "ul" or "ol", "items": [{"s": [spans], "sub": [{"s": [spans]}]}]}` |
| table | `{"t": "table", "head": [[spans], ...], "rows": [[[spans], ...], ...], "align": ["l", "r", "c", ...]}` |
| callout | `{"t": "callout", "kind": "tip" or "note" or "warning", "b": [blocks]}` |
| item card | `{"t": "item", "id": "ns:path", "icon": "model id", "name": "...", "rarity": "legendary", "color": "#RRGGBB", "anchor": "item-ns-path", "lines": [[spans], ...], "tip": 12, "link": "item-ns-path", "bare": true}`. `tip` is how many of the lines are the tooltip proper (the rest is sell price, recipe and sources). `link` (optional) is the item's own page: the card's name links to it (in game the whole card does). `bare` (optional) means lines only, no icon or name: used on item pages under the hero. |
| hero | `{"t": "hero", "id": "ns:path", "icon": "model id", "name": "...", "color": "#RRGGBB", "rarity": "legendary", "kind": "Sword", "big": "img/b/item-ns-path.png"}`. The top of an item page. `big` is the website's 3D render (left out of the in-game bundle; items without one show their icon big). In game the item is drawn at 4x with the vanilla item renderer, so 3D models show in 3D. |
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
- **Look it up:** hover an item in any inventory and press the wiki key (default `H`) to open the item's page.
- Links between pages work, item icons draw inline, and hovering an item span shows its name and rarity. Clicking an item
  (a link, a listing card) opens its page, which starts with the item drawn big.

## The website

A small multi-file static site, built into `dist/wiki/` and hosted on **GitHub Pages** at
https://makogai.github.io/minecraft-skyrpg/ (repo `Makogai/minecraft-skyrpg`, public, `gh-pages` branch). It lives under
`/minecraft-skyrpg/`, so every URL in it is relative.

| File | What |
| --- | --- |
| `index.html` | The shell: header, sidebar, search box. Links the CSS and JS with a content hash (`?v=`). |
| `wiki.css`, `wiki.js` | Design tokens (light and dark), layout, item cards and the item hero; routing, rendering, search. |
| `data/meta.json` | Categories, the page list (id, title, category, icon, summary; item pages also have `item`, `group`, `color`, `rarity`), the icon map (`item id` to `img/i/<hash>.png`), rarity colours, and the `itemPage` map. Loaded at start. |
| `data/pages/<id>.json` | One file per page (`{"id", "title", "category", "icon", "summary", "blocks"}`, the same blocks as the in-game bundle plus `big` on heroes). Fetched when the page is opened and kept. |
| `data/search.json` | The search index (`[kind, page id, anchor, title, text, icon, where]`), fetched the first time the search box is used. |
| `img/i/` | Item icons (the textures), shared by hash. Loaded lazily. |
| `img/b/` | The 3D renders for item heroes. |
| `img/p/` | Screenshots from `docs/wiki/images/`. |
| `.nojekyll` | Tells GitHub Pages not to run Jekyll. |

Hash routing: `#fishing`, `#fishing--sea-creatures` (page and section), `#cat.reference` (a category),
`#item-skyrpg_items-voidrender` (an item page). Old links to a card (`#items-weapons--item-skyrpg_items-voidrender`) still
work. Because pages are fetched, the site needs HTTP: to look at it locally run `python -m http.server` in `dist/wiki`.

`build.py` rewrites `dist/wiki/` completely (and deletes files that are no longer part of the site).
`tools/wiki/publish_pages.py` builds it and mirrors the whole tree into the `gh-pages` worktree (`.gh-pages/`), removing
files that no longer exist, keeping `.nojekyll`, committing and pushing. It is built locally rather than by a GitHub action
because the icons come from the Minecraft jar in the local Gradle cache. The website has a sidebar of categories, search
(pages, sections, items and text), item cards styled like SkyRPG's in-game tooltips with rarity colours, light and dark
themes, and phone layouts. The item page hero is the big picture on a soft glow in the rarity colour, the name in Pixelify
Sans. There is no longer a single-file `skyrpg-wiki.html` or claude.ai artifact copy: GitHub Pages is the home.

## Page list (2026-10-14)

Ids are fixed so pages can link to each other before they exist. Reference ids are generated (see above).

| Category | Pages |
| --- | --- |
| getting-started | `welcome`, `first-launch`, `first-steps`, `controls-and-keys`, `mods-and-shaders`, `progression-guide` |
| skyhaven | `skyhaven`, `npcs`, `quests-and-journal`, `events` |
| economy | `coins-and-bank`, `selling-and-shops`, `contracts`, `collections` |
| skills | `skills-overview`, `mining`, `farming`, `foraging`, `fishing`, `enchanting`, `crafting`, `delving` |
| gear | `weapons`, `armour-sets`, `tools`, `accessories`, `backpacks`, `shields`, `bows`, `wands` |
| combat | `stats`, `combat-basics`, `spells`, `mobs`, `slayers`, `world-bosses` |
| dungeons | `dungeons-overview`, `forgotten-crypt`, `spider-hollow`, `drowned-temple`, `dwarven-halls`, `infernal-citadel`, `ruined-citadel`, `catacombs`, `astral-observatory` |
| world | `survival-world`, `waystones-and-travel`, `danger-regions`, `region-hearts`, `blood-moon`, `the-deep`, `the-nether`, `the-end`, `ender-wyrm` |
| companions | `pets`, `mounts` |
| interface | `k-menu`, `chat`, `hud-and-minimap`, `waypoints`, `party`, `skyhelper`, `titles`, `settings` |
| reference | `items-weapons`, `items-armour`, `items-tools`, `items-accessories`, `items-materials`, `items-consumables`, `items-other`, `enchant-list`, `mob-list`, `boss-list`, `pet-list`, `mount-list`, `collection-list`, `quest-list`, `sell-prices`, `region-list`, `skyrpg-level-max` |
