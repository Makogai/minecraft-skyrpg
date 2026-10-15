# SkyRPG Builder

The Builder Station (`skyrpg_builder:builder_station`, module `builder/`) builds a blueprint for you: pick one, place its
hologram, deliver the materials, press Build. Inspired by Printoria, all our own. Player-facing docs:
`docs/wiki/pages/world/builder-station.md`.

## Pieces

| Class | What it does |
| --- | --- |
| `Blueprint` | A box (≤ 128 per side) and its non-air blocks as two arrays sorted by local index `x + z·sx + y·sx·sz`, so `layerStart[y]` gives each layer. Kept block entity data only for signs, banners and skulls (never inventories). Saved as compressed NBT. |
| `Schematics` | Readers: `.litematic` (all regions merged; negative region sizes; Litematica's bit arrays span longs), Sponge `.schem` v1–3 (palette strings, varint data), vanilla `.nbt` (jigsaws become their `final_state`, structure voids skipped). Older files run through the DataFixers (block states and block entities). 26.x block states are `{id, properties}` in NBT; older files and tools use `{Name, Properties}`: both are read. |
| `Library` | Shared server library: uploads in `<world>/skyrpg_builder/blueprints/<id>.nbt` (40 per player, 500 in all; uploader or an op may delete) plus vanilla structures read live from the structure manager (village houses and town centres, igloo, outpost watchtower, ruined portals; villages listed first). Uploads arrive in 30 kB chunks (serverbound payloads are capped at 32 kB), 2 MB at most. |
| `Placement` | Mirror (front-back) then turn clockwise `turns` times, like vanilla structures, normalised so `origin` is the placed box's min corner. Heights never change, so layer y is `origin.y + y`. |
| `Materials` | Block state → items: own item, else the pick-block item; upper halves/bed heads free; double slab 2; pieces (candles, pickles, eggs, petals, snow layers); pots = pot + plant; candle cakes; water/lava = bucket (empty bucket back); grass/path/farmland/mycelium = dirt. `buildable`: vanilla only (plus tag `skyrpg_builder:buildable`), never spawners, bedrock, portals, command blocks etc. (and tag `skyrpg_builder:unbuildable`). |
| `Station` / `Stations` | SavedData on the overworld keyed `dimension|pos` (stations are not block entities, like waystones). Menu, actions, scan, build loop, hologram sync. |
| client `BuilderScreen`, `BlueprintScreen` | The station menu (3D / Top / Side / Layers views, "Move in world", materials, who delivered) and the picker (cards with turntable previews, or list). Picking a blueprint goes straight to placement mode. Build closes the menu. |
| client `PlacementMode` + `mixin/` | Placing in the world: the hologram follows the aim (near side's middle on the looked-at block, stretching away from the player), keys from a bottom strip (scroll/R turn, M mirror, arrows nudge relative to the view and lock, PgUp/PgDn or Shift+scroll raise, G lock, click/Enter place, Esc/Backspace/right-click cancel). `KeyboardHandler.keyPress`, `MouseHandler.onButton`/`onScroll` are intercepted only while placing with no screen open. Sends one "place" action (offsets from the station, "turns,mirrored"). |
| client `BuildOverlay` | The build panel for everyone within 64 blocks (`Progress` payload twice a second): a 3D picture cut at the current layer (built part coloured, the rest cyan), bar, layer; "FINISHED!" for 5 s. Bottom centre, above the bars (the top is the block info and boss bars). |
| client `Ghosts`, `GhostRenderer` | The in-world hologram: real block-model quads at 50% alpha, tinted cyan (red where something is in the way), faces between two hologram blocks culled, full-bright; outline box; a pulsing plane at the layer being built. Drawn on `LevelRenderEvents.COLLECT_SUBMITS` with `RenderTypes.translucentMovingBlock()`. Meshes rebuild every 10+ ticks (slower for big ones) to drop built blocks. |
| client `RealPreview` | The "Real" preview style: each block's actual model quads (positions, UVs, sprite) rasterised in software with their textures (read once from `SpriteContents.originalImage`, first frame), face culling against full neighbours, back-face culling, painter's order, alpha test and blending; water/lava as tinted surfaces. Over 60k faces falls back to the simple cubes. |
| client `BuilderSettings` | K menu → Settings → Builder: Preview style (Real/Simple) and Preview quality (Low 1×, Medium 1.5×, High 2× resolution, more angles; fewer kept in memory when sharper). |
| client `Previews` | Software-rendered isometric turntables (painter's order, map colours, face shading, dark outline) in one texture strip per blueprint and `Spec` (cards 112×80×20 frames, menu 232×176×36, overlay 96×72×20; `cut` = first layer drawn as cyan hologram), drawn off-thread, 40 kept. Local files are read client-side with the same `Schematics` code, so cards show errors before upload. |

## Flow and rules

- States: EMPTY → PLANNING (blueprint chosen) → BUILDING → DONE ("Build again" back to PLANNING). Stop = back to
  PLANNING; Build again resumes (already-correct blocks are skipped).
- Default placement: behind the station as its placer saw it, centred, on the station's level. Offsets clamp to ±64.
- Scan (cached 2 s): per buildable entry, the world block is compared by **block** (not state): same block = done,
  else its materials are needed; non-air non-replaceable = "in the way".
- Build pacing: the whole build takes `0.8 · (15 + 0.15·blocks + 2·layers)` s, clamped 16–192 s, minus an 8-tick
  rest after each layer; work comes from a credit that grows by the rate each tick (a block costs 1, a clear 1/3),
  at most 4096 checks a tick. `Progress` carries seconds left. Per layer: clears (if
  Clear site) → full-collision blocks → the rest, each sorted by distance from the station. Placement uses
  `UPDATE_CLIENTS | UPDATE_KNOWN_SHAPE` (no shape updates, so the result matches the blueprint exactly); clearing adds
  `UPDATE_SUPPRESS_DROPS | UPDATE_SKIP_BLOCK_ENTITY_SIDEEFFECTS` after emptying containers into the leftovers.
- Clearing drops only for blocks that don't need a tool (`!requiresCorrectToolForDrops`), so stone and ores vanish:
  an all-air blueprint is not a free quarry. Unbreakable blocks and other stations are never cleared.
- Players inside a target block pause the build ("Step out of the build"). Running out of a material pauses it with a
  notice to players within 48 blocks; delivering resumes it.
- Materials come from the inventory and backpacks through core's `MaterialStore` (same as the Artisan Bench).
  Creative: delivering is free.
- Holograms: every 20 ticks each player within 160 blocks (horizontal) gets `Ghost` updates for visible stations
  (planning with hologram on, or building); geometry is sent once per player per blueprint (`Geometry`, delta-varint
  positions, ≤ ~500 kB for 120k blocks, under the 1 MB clientbound cap). The picker asks for geometry of any card
  it shows ("geometry" action).
- Not in Skyhaven (`skyrpg_hub:hub`).
- "place" is accepted up to 128 blocks from the station (placing happens away from it); other actions need 8.
  Sneak + right-click a station (a `UseBlockCallback`, so it works with an item in hand, where vanilla skips the block)
  sends `Place` and the client starts placing, locked at the current spot. A confirmed placement updates the local
  hologram at once.
- Materials panel: list or grid; "you carry" counts come from the client's inventory (backpacks aren't counted there).
- Anyone may use any station (friends' server); `helpers` records who delivered how much per blueprint (reset on a
  new blueprint) and the menu shows the top four. Hologram updates go out every 5 ticks, but only changed ones.

## Gotchas found while building it

- Mouse buttons are 1-based in 26.3 (`InputConstants.MOUSE_BUTTON_LEFT` = 1).
- `NbtUtils.readBlockState` in 26.3 reads `{id, properties}`; feeding it `{Name, Properties}` silently gives air.
- `tools/tests/make_test_schematics.py` writes the same test hut as .litematic, .schem v2/v3 and .nbt (154 blocks,
  152 materials) for checking the readers.
