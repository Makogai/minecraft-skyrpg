# SkyRPG Crates

CS2-style case opening (module `crates/`, mod id `skyrpg_crates`): crates drop from monsters while in season, a key
opens one, and a reel of prizes spins past a marker and stops on the prize. The first crate is the Hallow's Crate
(Halloween 2026). Player docs: `docs/wiki/pages/world/crates.md`. Decision log: `progression.md` §12 #18.

## Pieces

| Class | What it does |
| --- | --- |
| `Crate` | A crate from `data/<ns>/skyrpg/crates/<id>.json`: name, subtitle, crate and key items, `key_hint`, `until` (season end, ISO date; drops stop after it, opening still works), `drops` per kill (`day`, `night` (also Nether/End), `blood_moon`, `elite`, `boss`), `announce_from` (tier index from which the server hears about it) and `tiers` (`name`, `color`, `weight`, `mystery` = hidden ★ on the reel, `rewards`). A reward is `item`+`count`, `set` (several items, shown with `icon`/`name`), `pouch` (coins in a Pumpkin Pouch) or `pet`+`rarity`; each can carry a `weight`. |
| `Crates` | Loading, the contents screen data, opening (takes a crate and a key, rolls the tier by weight and a reward in it, builds a 60-card reel with the prize 5–8 cards from the end, fillers drawn with weights^0.65 so rare cards show up more for drama), delivery `ROLL_TICKS` (144) later or at once on logout, the personal notice and the server-wide announcement (to everyone but the opener). `rig` makes a player's next roll land on a tier (op `/crates rig`, for tests and screenshots). |
| `CrateDrops` | `AFTER_DEATH`: a hostile (`Enemy`) killed by a player, not from a spawner (`MobTags.FARMED`): chance by boss tags (`skyrpg_dungeon_boss`, `skyrpg_world_boss`, `skyrpg_region_boss=`), `skyrpg_elite`, Blood Moon (`skyrpg_bloodbound` or `BloodMoon.active()` in the overworld), night/other dimensions, day; × (1 + Magic Find/100). Rare-drop notice. First crate of each kind also gives a key (attachment `skyrpg_crates:free_keys`, persistent, kept on death). |
| `CrateItems` | Hallow's Crate and Key (right-click: contents screen), Candy Corn and Toffee Apple (`Candy`), Pumpkin Pouch (coins in `CUSTOM_DATA` "Coins", lore line; right-click → `Wallet.give`). Creative tab "SkyRPG Crates". Tooltips come from item definitions in `data/skyrpg_crates/skyrpg/items/`. |
| `Candy` | Eaten for a boost from `data/<ns>/skyrpg/candy/<item>.json` (`stats`, `effects`, `minutes`), via `Boosts.give(player, "candy", ...)`: one candy slot, separate from meals. |
| `ScytheAbilities` | Ability handlers `skyrpg_crates:reap` and `skyrpg_crates:soul_sweep` (params: `radius`, `damage`, `max_targets`, `harvest` square radius, `fire` seconds, `heal` fraction per hit): hit every `Enemy` in the radius with knockback, optional fire and healing, a sweep / soul-fire ring of particles, then break every grown crop in the square with `gameMode.destroyBlock` so the farming mod's harvest (fortune, reseed, area) applies. |
| client `CrateScreen` | Contents (legend with each tier's chance, cards by tier, item tooltips, Open / key count / hint) and the reel: cards scroll under a gold marker with `1 - (1 - t)^3` easing over 7 s, landing anywhere on the prize card (±40%), a note-block tick per card, gold cards drawn as "★ Rare Special Item" until won, then "YOU UNBOXED" with a tier fanfare and turning light rays for the two rarest tiers. Enter/Space opens. |

## The Hallow's Crate

Season until 2026-11-08. Key: 2,500 coins from Odessa Quill (`curios` shop). Tiers:

| Tier | Weight | Rewards |
| --- | --- | --- |
| Treat (#4B69FF) | 64 | 8 Candy Corn, 4 Toffee Apples, a 1,000-coin pouch, (½ weight) a 2,500-coin pouch |
| Trick (#8847FF) | 25 | one Hallowed Harvest piece |
| Cursed (#D32CE6) | 8 | Reaper's Scythe, Jack o'Lantern (Epic) |
| Haunted (#EB4B4B) | 2.5 | the full Hallowed Harvest set |
| ★ Hallowed (#FFD24A, mystery) | 0.5 | ★ Soulreaper, Jack o'Lantern (Legendary) |

Rough rate: a player killing ~100 monsters a night gets about one crate a night (more in a Blood Moon).

## The rewards (other modules)

- **Hallowed Harvest** (`items`: `armor("hallowed", ...)`, definitions `hallowed_*.json`, set `item_sets/hallowed.json`):
  Legendary Farming + Combat hybrid. Always-on bonus 40 Farming Fortune, 20 Strength, rare finds +50%;
  `HallowedNightSource` adds 60 FF / 40 Strength / 30 Crit Damage after dark in the overworld, ×2 in a Blood Moon.
  Art: `hallowed_layers` in `tools/art/generate_items.py`, `hallowed()` 3D detail (pumpkin head with a glowing
  jack-o'-lantern face) in `tools/art/generate_armor_depth.py`, `HALLOWED` in `ArmorDepthRendering.DetailSet`.
  Run `generate_items.py` then `generate_armor_depth.py`; other sets come out byte-identical.
- **Reaper's Scythe** (Epic) and **★ Soulreaper** (Mythic): `items` hoes with `damage` stats, reseed + harvest area
  effects, and the abilities above.
- **Jack o'Lantern** (`pets`: `PetType.JACK`, `hatches = false` so no egg and never from a Golden Egg): Farming,
  +1.4 FF / +0.6 Strength per level; perks rare finds +25% (25), life steal 2% (50). Model `jack()` in
  `tools/art/generate_pets.py`.
- Item art for the crate items: `tools/art/generate_crates.py`. Mod icon: `tools/art/placeholder_icon.py`.

## Adding a crate

A new JSON in `data/<ns>/skyrpg/crates/` plus its crate and key items (register them like `CrateItems`), a key offer
in a shop, and the rewards. Next season: change `until`, the subtitle and the rewards.
