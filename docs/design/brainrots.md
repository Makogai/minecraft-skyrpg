# SkyRPG Brainrots

Fifteen Italian-brainrot creatures (module `brainrots/`, mod id `skyrpg_brainrots`) that spawn on the overworld
surface, shout their name when you're near, and get caught with a right-click into a per-player album (J, or K menu →
Brainrots). A fun side collection; coins are the only reward. Player-facing docs: `docs/wiki/pages/world/brainrots.md`.

## Pieces

| Class | What it does |
| --- | --- |
| `BrainrotKind` | The fifteen, in album order: id, name, `Rarity` (spawn weight, colour, first-catch coins), `Move` (WALK, HOP, SPIN, FLY, FLOAT), `Habitat` (spawn weight × biome tag / night), hitbox size, album lore, and its `voice` SoundEvent. |
| `BrainrotEntity` | One entity type (`skyrpg_brainrots:brainrot`, a `PathfinderMob`) with the kind in synced data (saved as `Kind`, a string id; `/summon ... {Kind:"tung_tung_sahur"}` works). Dimensions per kind. Can't be hurt (only creative / `/kill`); a hit makes it hop. Lives 8 minutes (`Life`), despawns when far like animals. Says its name when a player is within 10 blocks (every 15–27 s). Walkers use a stroll goal; HOP jumps every 0.6–1.5 s; FLY circles its spawn point at radius 9, ground + 9, and every 20–35 s (twice as fast with a player within 16 blocks) swoops to ground + 1.2 for 11 s (dives at full speed, slows to 0.1 once low) so it can be caught; FLOAT drifts between nearby spots at ground + 1.4. |
| `Album` | Record `(caught: id → count, first: id → epoch ms)`; Fabric attachment `skyrpg_brainrots:album`, persistent, synced to its owner only, copied on death. |
| `Brainrots` | Catching (album, coins via `Wallet.give`, title on a first catch, notice, particles, BRAINROT MASTER broadcast at 15/15) and spawning (below). `/brainrots spawn <kind>` and `/brainrots near` for ops. |
| client `BrainrotModel` / `BrainrotRenderer` | One composite model holding every kind's baked `ModelPart` (each its own texture size) with only the current kind visible; animation by part name (legs, arms, wings, tails, trunks, ears, antennae, rings, propellers, wheels, head look, body bob per move style). SPIN turns the whole model. Optional emissive layer (`<kind>_glow.png`). |
| client `AlbumPage` | K-menu page (order 45, after Titles) and J key. 5×3 card grid with rarity strips; caught cards show a mouse-following puppet (client-only entity, id −20000 − ordinal, never added to the world), names on 2–3 lines at one size, count badge; detail panel with picture, rarity pill, lore (0.8×), habitat, catches and date, "▶ Hear it". Opens on the newest catch until you click. |

## Spawning

Every 200 ticks, for each overworld player (not spectating): 9% chance, skipped if 2+ brainrots are within 96 blocks
of them or 18 in the world. A spot 20–44 blocks away at `MOTION_BLOCKING_NO_LEAVES` height, solid ground under it,
two air blocks; up to 8 tries. Kind weighted by `rarity.weight × habitat.weight(biome, night)`, night =
`overworldClockTime % 24000` in 13000–23000. Fliers spawn 10 up, floaters 1.4 up.

Rough odds per spawn in a neutral biome: Common ~49%, Uncommon ~38%, Rare ~11%, Epic (Bombardiro) ~2.7%; at night
Cappuccino Assassino ×4 and La Vaca Saturno (Legendary, night only, weight × 2.5) ~2.6%. About 30 spawns an hour
out in the world, so roughly one Bombardiro an hour and one La Vaca every ~4 hours. Biome specialists are ×5–6 at
home and ×0.4–0.6 away.

## Rewards

First catch: 25 / 75 / 200 / 500 / 1,500 coins (Common → Legendary); repeats pay a tenth. Nothing else: no stats,
no items, so the collection can't unbalance progression.

## Art and sound (all made here, nothing downloaded)

- Models: `tools/art/brainrots/<kind>.py` (cube models on `tools/art/cube_model.py` + `brainrots/kit.py` painting
  helpers). `python tools/art/generate_brainrots.py [--only a,b] [--preview DIR]` writes the textures and the
  generated `client/model/<Camel>Layout.java` + `BrainrotLayouts.java` (part paths for animation, glow flag).
- Voices: `python tools/art/make_brainrot_voices.py` reads Italian-ish lines with Windows speech (an Italian voice
  if installed, else Microsoft David with phonetic spellings), lowers/echoes/normalises with ffmpeg, writes mono Ogg
  (`sounds/voice/<kind>.ogg`, positional) and `sounds.json`.
- Mod icon: `python tools/art/make_brainrots_icon.py` (Tung Tung Sahur in the Builder icon's frame), then
  `tools/art/install_icons.py`.

## Ideas for later

More kinds (add a model file, a `BrainrotKind`, a voice line), a "shiny" variant, album milestones giving titles,
and a brainrot event (most catches in 10 minutes).
