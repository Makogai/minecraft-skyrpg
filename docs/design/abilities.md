# Item abilities (right-click)

Reworked 2026-10-14 after "skills on swords need to be reworked, make them poppier. Say Starfall: it only works on one enemy, no effects,
nothing: VFX, sounds etc." Wiki: `gear/weapons` (abilities section), `gear/tools`, `gear/shields`, `gear/bows`.

## The pipeline

`com.skyrpg.items.ability.Abilities` handles every right-click ability (`UseItemCallback` / `UseBlockCallback`, main hand only):

1. **Cooldown** check, then **mana** check. On failure nothing is spent and the player gets a red action-bar line ("Starfall is recharging: 12.3s",
   "Not enough mana for Starfall: 31 / 50") and a dull dispenser click (at most five times a second, so holding the button does not spam).
2. The handler runs (it only does the effect and returns false when it could not happen: no targets, a wall, full health: then no mana, no cooldown).
3. `AbilityFx.cast`: the item's data-driven **cast** plays on the caster (see below).
4. Mana is spent ("Starfall -50 mana" in aqua; abilities without mana say "Name!" in gold), the cooldown starts, and the stack gets vanilla's
   **cooldown sweep** (dark overlay on the hotbar slot).
5. A cooldown of 8 s or more ends with a chime and a green "Name ready" action bar line, if the item is still in the main hand.

Types: `riftstep` and `mend` (items mod), `boost` (items, `GearEffectSources`), `skyrpg_combat:starfall` and `skyrpg_combat:thunderclap`
(`combat/.../ability/WeaponAbilities`), `skyrpg_mining:echo_sense`, `skyrpg_foraging:treefeller` (core's `AbilityHandlers`).

### Data: `ability` in `data/<ns>/skyrpg/items/<item>.json`

```json
"ability": {"type": "skyrpg_combat:starfall", "name": "Starfall", "mana": 50, "cooldown": 20, "description": "...",
  "params": {"radius": 6, "damage": 80, "stagger": 2, "max_targets": 14,
             "cast": {"fx": "skyrpg_abilities:starfall_cast",
                      "sounds": [{"sound": "minecraft:block.beacon.activate", "volume": 0.8, "pitch": 1.9}],
                      "shake": [0.3, 8], "flash": "#55FFF4D0"}}}
```

`mana` and `cooldown` (seconds) are shown by the tooltip and enforced by the pipeline. `params` numbers are read by the handler.
`params.cast` is optional and generic: `fx` (an effect id played anchored to the caster), `sounds` (layered; pitch gets +-6% random), `shake`
`[strength degrees, ticks]`, `flash` (`#AARRGGBB`, 6 ticks). Starfall and Thunderclap play their own choreography from code and use `cast` for nothing.

## Screen shake and flash (vfx mod API)

Server side, `com.skyrpg.vfx.Vfx` (call from the server thread; both are cheap one-int packets):

| Call | Meaning |
|---|---|
| `Vfx.shake(ServerPlayer, strength, ticks)` | Camera swing of up to `strength` degrees, fading out (quadratically) over `ticks`. 0.3 a rumble, 0.5 a light hit, 1.0 solid, 1.6 a big slam, 2.5 huge. A stronger shake replaces a weaker one; a weaker one never cuts a stronger one short. |
| `Vfx.shakeAround(level, pos, radius, strength, ticks)` | The same for every player within `radius`, a third as strong at the rim. |
| `Vfx.flash(ServerPlayer, 0xAARRGGBB, ticks)` | A colour wash over the screen, alpha is the peak, fades out (quadratically). |
| `Vfx.later(server, ticks, runnable)` | Run something on the server thread N ticks later (stagger, hit sequences). |
| `Vfx.sound(level, pos, "minecraft:entity.firework_rocket.blast", volume, pitch)` | Sound by id with +-6% pitch variation. Layer two or three. |
| `Vfx.play(level, id, pos)`, `Vfx.play(entity, id)` | Existing: a data-driven effect. |

Client: payloads `VfxPayloads.Shake` / `Flash`; `ScreenFx` holds the state, and a mixin (`CameraMixin`, tail of `Camera.update`) nudges the camera yaw
and pitch after the game has aimed it, so it never changes where you actually look or shoot. It is scaled by Options > Accessibility > Distortion Effects
and is off when the Effects quality setting is Off. Test with `/vfx shake <strength> <ticks>` and `/vfx flash <argb int> <ticks>`.

## VFX content

`tools/builds/vfx_abilities.py` writes `vfx/src/main/resources/data/skyrpg_abilities/skyrpg/vfx/*.json` (its own namespace, so
`tools/builds/vfx_content.py` does not wipe them). Edit and re-run. Effects: `starfall_{cast,marker,meteor,impact,finale}`, `thunder_{cast,strike}`,
`rift_{depart,arrive,trail,cut}`, `mend_cast`, `boost_{gust_warm,gust_cold,harvest_moon,molten,lumen,skyward,hellfire}`,
`treefeller_{arm,armed,fell}`, `echo_{pulse,ping}`, `shield_block[_ember|_frost|_storm|_astral]`, `shield_{bash,charge,reflect}`,
`shield_shockwave_{s,m,l}`, `bow_ready`, `arrow_{hit,hit_pierce,hit_star,hit_frost,burst,volley_shot,lightning_shot}`.

## The abilities

| Item | Ability | Mana | Cooldown | What it does now | What it did |
|---|---|---|---|---|---|
| Starfall Blade | Starfall | 50 | 20 s | Star circle and light column at the caster; a target ring on **every** hostile mob (and training dummy) within 6 blocks (max 14, nearest first); a star falls out of the sky onto each, one every 2 ticks, 8 ticks of fall with a light trail; on landing: flash, two rings, star burst, sparks, shards, dust, 80 damage (Strength and crit apply, damage numbers, kill credit), knock-up, a bell note per star climbing the scale, shake. 3+ stars end with a bloom, flash and bigger shake. | Damaged everything in range at once; a column of `end_rod` per mob and one firework twinkle sound. No mana (20 s cooldown only), no knockback, nothing from the sky, no feedback besides particles. |
| Stormedge | Thunderclap | 30 | 15 s | Shockwave ring and sparks around the caster, then a bolt on every hostile mob within 5 blocks (max 12) nearest first, 2 ticks apart; a spark arcs from each to the next; 40 damage, shock (Slowness IV 1.5 s), small shove, shake; 3+ ends with a flash and thunder. | Vanilla visual bolt on each target at once, nothing else, no sound of its own, no mana. |
| Riftblade | Riftstep | 50 | 4 s | Violet tear opens where you stood, a lingering cut hangs along the path, you arrive in a burst with a brief violet flash and shake; every hostile mob within 1.4 blocks of the path takes 40 damage (slashes land in sequence). Looking at the floor still dashes along it. Mobs do not stop the rift (you will not come out inside one); walls do: "Something solid is in the way", nothing spent. | A teleport with a few particles; **no cooldown at all** (could be spammed as fast as mana allowed). |
| Mending Wand | Mend | 60 | 5 s | Spiral of green-gold stars, rings, hearts and chimes; "Already at full health" and no cost when there is nothing to heal. | Plain particles; spent mana at full health. |
| Skyiron Pickaxe | Skyward Burst | - | 60 s | `boost`: wind streaks and blue-white sparks shoot up, rings, wind-burst sound, shake. | Only a chat notice. |
| Duskbore Pick | Echo Sense | - | 45 s | Sonar rings sweep out across the stone; each ore flares as the wave reaches it, with a chime that is higher the closer it is; trails as before. | Trails and one sound. |
| Emberheart Pickaxe | Molten Core | - | 90 s | `boost`: heat flash, embers, lava-light ring, smoke, orange screen wash, shake. | Chat notice only. |
| Hellforged Pickaxe | Hellfire Vein | - | 75 s | `boost`: flame ring, embers and black smoke, red wash, shake. | Chat notice only. |
| Lumen Breaker | Lumen Pulse | - | 120 s | `boost`: white-blue pulse, expanding ring and motes, light wash. | Chat notice only. |
| Cinder Scythe / Frostbite Scythe | Bountiful Gust | - | 45 s / 60 s | `boost`: a whirl of golden (or frosty blue) leaves and grain (or snow) around the harvester, wind sounds. | Chat notice only. |
| Moonlit Reaper | Harvest Moon | - | 120 s | `boost`: a pale moon-glow overhead, silver stars rising, chimes. | Chat notice only. |
| Five axes (Ironbark to Emberbrand) | Treefeller | - | 12 to 60 s | Leaf and chip whirl on cast; a few leaves keep circling while it is armed; the tree lands with a thump ring, bark, leaves, dust, three layered sounds and a shake that grows with the size of the tree. | An axe-strip sound and a chat line. |

### Shields

| Shield | Effect | Feedback now |
|---|---|---|
| All | block | Flash, ring and sparks at the shield face (themed per shield: embers, ice, lightning, violet stars), crunch sounds, shake scaled by the hit. Before: vanilla sound only. |
| Skyiron Bulwark | bash | Shove ring and dust on the attacker, knockback sound, shake. |
| Emberforged / Frostward | ignite / frost | Themed block burst (embers, ice and snow) on top of the existing flames and frost. |
| Thunderguard | charge, shockwave | Each charge: spark helix, chime that climbs with the charge, and a beacon ping when full. Release: ring of lightning sized to the radius, thunder layers, shake and flash, then a bolt, strike effect and damage per mob, nearest first, 2 ticks apart. Before: a handful of spark particles and one thunder sound. |
| Astral Aegis | reflect | Violet flash, rings and stars at the projectile, shake. |

### Bows

Full draw: glint at the arrow and a chime (once per draw), full-draw kick (shake 0.18). Hits: a flash, ring and sparks on every arrow; Ember Bow burst is a
fireball with embers and smoke and a shake, Frostbite shatters ice, Starfall Bow bursts into stars with an amethyst note, Longbow a white streak, Stormcaller's
bolt gets the lightning strike effect and a shake. Shots: the Starfall Bow's volley flares three stars and three chimes, the Stormcaller's charged shot
crackles with thunder. Numbers (damage, shares, radii) are unchanged and still data (`"bow"` objects).

## Numbers (all in the item JSON)

Starfall: radius 6, damage 80, stagger 2, max_targets 14. Thunderclap: radius 5, damage 40, stagger 2, max_targets 12, stun 1.5 s. Riftstep: distance 8,
damage 40, width 1.4. Mend: amount 40. Boosts and Echo/Treefeller as before.

## Tested on staging (2026-10-14)

SkyCam third-person screenshots (`run/client-cam/screenshots/`) with five NoAI zombies around the caster: Starfall (stars land on 5 of 5 mobs, gold crit numbers on each,
finale bloom), Thunderclap (bolts and damage on 3+ mobs), Riftstep (arrives in a violet ring, one zombie on the path loses 22 health, 115 crit number), Mend, Molten Core,
Echo Sense rings, Treefeller whirl, Thunderguard (blocks store charges with sparks, 5 charges released as a ring with 200-300 on each of three zombies), Ember Bow (burst
damages neighbours, zombies burn). Also: Not enough mana (`Not enough mana for Starfall: 18 / 50`), the recharging message, the green "ready" line, the vanilla cooldown sweep
on the hotbar slot, the screen flash (mean brightness 29 to 87) and the shake (a phase correlation of screenshots against a still one measured shifts of 25, 10, 8 px that
decay with the 4 degree / 100 tick shake). Not triggered in game: Treefeller's fell effect (creative-mode instant break did not start the chop; `/vfx play
skyrpg_abilities:treefeller_fell` was checked instead), the Astral Aegis reflect, the Frostbite and Stormcaller bow hits, the Starfall Bow volley. Effects were previewed with
`/vfx play skyrpg_abilities:<id>` and tuned with `/vfx reload` (data-only changes need no restart).

Gotchas: Fabric's `AFTER_DAMAGE` does not fire for a killing blow, so nothing here relies on it for ability hits; `Camera.update`'s tail is the right place for the shake in 26.3
(`setRotation` marks the matrices dirty); the `rune` particle is a square plate and looks like a tile when big (the star circle uses `star` with `orient: flat`);
`glow` is a low-resolution sprite, keep it under about 2 blocks or it shows its pixels.
