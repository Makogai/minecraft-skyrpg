# SkyRPG VFX (`vfx` mod)

Data-driven particle effects. An **effect** is a list of **emitters** (what to spawn, where, how it moves). A **binding**
says when an effect plays by itself: on a mob type, on someone wearing a full armour set, on someone holding an item.
Everything is JSON, reloaded with `/vfx reload` (also on `/reload`), and a bad file is skipped with a message rather than
breaking the rest. Unknown keys are ignored with a warning.

Content is written by `tools/builds/vfx_content.py` (edit it and re-run; it rewrites both folders). You can also hand-write
files, in any namespace:

```
data/<ns>/skyrpg/vfx/<id>.json            effect   -> id <ns>:<id>
data/<ns>/skyrpg/vfx_bindings/<id>.json   binding
```

Particles are our own (`skyrpg_vfx:spark glow ember rune shard star wisp bubble leaf snowflake smoke_puff ring`; white
textures tinted by the emitter's colour) or any vanilla/mod particle (`"particle": "minecraft:flame"`).

## Effect file

| Field | Meaning |
|---|---|
| `emitters` | Required list of emitters (below). |
| `range` | Blocks from the camera it is still drawn (default 64). |
| `description` | Shown in `/vfx list`. `comment` / `_comment` are ignored. |

## Emitter fields

A number marked *range* can be `3` or `[2, 5]` (random in between). *pair* can be `1` or `[start, end]` over the particle's life.

What to spawn (one of):

| Field | Meaning |
|---|---|
| `particle` | Particle id. Ours take no `options`. Vanilla simple particles just work; `dust` and the colour-only ones use `color`/`size`; others need `options` (the particle's JSON codec). |
| `effect` | Instead of a particle: play another effect at each spawn point (chain effects). |

Look (our particles; vanilla ones only use what they support):

| Field | Default | Meaning |
|---|---|---|
| `color` | white | `"#RRGGBB"` / `"#AARRGGBB"`; a list = each particle picks one; `{"gradient": [...]}` = a random point along the blend; `{"palette": [...]}` = list. |
| `color_end` | none | Colour the particle fades to over its life. |
| `alpha` | `[1, 0]` | *pair*, opacity. |
| `size` | 0.15 | *pair*, in blocks. `size_jitter`: scales each by a random 1 +- this. |
| `lifetime` | 20 | *range*, ticks (min 1). |
| `gravity` | 0 | *range*; 1 falls like a normal item, negative floats up. Use `rise` for steady upward drift. |
| `drag` | 0.02 | Fraction of speed lost each tick (0-1). |
| `spin` | 0 | *range*, degrees per tick. |
| `sway` | 0 | Sideways wobble strength (0.5 gentle, 1 drifty). |
| `fade_in` | 0 | Ticks to fade in. |
| `fullbright` | glows for spark/glow/ember/rune/star/wisp | Ignore world light. |
| `collide` | false | Stop at blocks. |
| `orient` | auto | `camera`, `vertical` (upright), `flat` (lies on the ground; for shockwaves), `velocity`. |

Spawning:

| Field | Default | Meaning |
|---|---|---|
| `count` | 1 (0 if `rate` set) | *range*, particles at the start (and every `every` ticks while running). |
| `rate` | 0 | Particles per tick while running (with `duration`). |
| `duration` | 0 | Ticks it keeps spawning (0 = one tick). |
| `delay` | 0 | Ticks before it starts. |
| `every` | 0 | Repeat the `count` burst every N ticks during `duration`. |
| `chance` | 1 | Chance this emitter runs at all each time the effect plays. |

Where:

| Field | Default | Meaning |
|---|---|---|
| `shape` | point | A name (`"sphere"`) or an object (below). |
| `anchor` | center | On an entity: `feet center head eyes right_hand left_hand main_hand off_hand hand back`. `hand` = the hand holding the matched item. Plain positions ignore it. |
| `offset` | [0,0,0] | Added in the anchor's frame: x right, y up, z forward. |
| `first_person` | false | Emitters on face-level anchors (head, eyes, hands) are hidden for yourself in first person unless true. |

Shape types and their fields: `point`; `sphere` (`radius`, `surface`); `ring` (`radius`, `arc` degrees, `even`, `phase`);
`disc` (`radius`); `helix` (`radius`, `height`, `turns`, `even`); `line` (`from`, `to`); `cone` (`direction`, `angle`, `length`);
`box` (`size` [x,y,z]); `orbit` (a ring whose particles circle the anchor; `radius`). Common: `jitter` (random offset),
`fit` (sizes count in entity widths/heights, so one effect fits any mob), `even` (spread by index instead of random),
`phase` (start angle, degrees; random if absent).

Motion (blocks per tick; shape-outward direction for `speed`):

| Field | Meaning |
|---|---|
| `velocity` | [x,y,z] in the anchor's frame. |
| `speed` | *range*, along the shape's outward direction (sphere: away from centre, ring: outwards). |
| `spread` | Random extra velocity on every axis. |
| `rise` | *range*, extra upward velocity. |
| `orbit_speed` | Degrees per tick around the anchor (default 3 for `orbit`). |
| `inherit` | Fraction of the entity's own movement added to the particles. |
| `follow` | Particles keep moving with the entity for their life. |

`conditions`: list of `moving still sneaking sprinting in_water on_ground airborne night day raining`; `!` negates
(`["moving", "!sneaking"]`). All must hold.

Example, rising embers:

```json
{
  "description": "Cinderplate: embers rising off the wearer",
  "emitters": [{
    "particle": "skyrpg_vfx:ember", "count": [1, 2],
    "color": {"gradient": ["#FFE9A8", "#FF8A2A", "#B02000"]},
    "size": [0.07, 0.02], "lifetime": [28, 50], "rise": [0.02, 0.045], "sway": 0.5,
    "shape": {"type": "box", "size": [0.8, 1.4, 0.8]}, "offset": [0, -0.2, 0]
  }]
}
```

## Binding file

Every filter present must match. At least one of `entity`, `armor`, `held` is required.

| Field | Default | Meaning |
|---|---|---|
| `effect` | required | Effect id to play. |
| `rate` | 0.25 | Expected plays per tick (0.25 = every ~4 ticks). Each play of an ambient effect should spawn only 1-3 particles. |
| `range` | 32 | Only entities this close to the camera. |
| `self` | true | Also on the local player. |
| `conditions` | none | As for emitters. |
| `entity` | | Entity type id, tag (`#ns:tag`) or a list. |
| `armor` | | `{"asset": "skyrpg_items:cinderplate", "pieces": 4}`; `asset` is the equipment asset id (items/.../equipment/*.json), string or list; `pieces` 1-4 worn (default 4). A bare string is the asset. |
| `held` | | `{"item": id or "items": [...], "rarity": [...], "hand": "main" / "off" / "either", "except": [...]}`. Needs `item(s)` or `rarity`; `#tags` allowed. Rarity is the SkyRPG item rarity (`legendary`...). |
| `description` | | Shown in `/vfx list bindings`. |

```json
{"effect": "skyrpg_vfx:aura_cinderplate", "rate": 0.35, "range": 24,
 "armor": {"asset": "skyrpg_items:cinderplate", "pieces": 4}}
```

## Adding VFX

- **Mob:** write an effect with box/`fit` shapes (`mobbox` in the generator) and a binding `{"entity": "skyrpg_combat:my_mob"}`.
  Bosses: use a larger `range` (48) and a higher rate.
- **Armour set:** binding `armor.asset` = the set's equipment asset id; effect around `box` size ~[0.8, 1.5, 0.8].
- **Tool or weapon:** binding `held.item` (or `held.rarity`); anchor the emitters to `hand` with an `offset` out to the head.
- **Pet:** make `pet_aura_<rarity>` (the pets mod plays it every ~4 ticks at the pet).
- **From code:** client `VfxClient.play(id, x, y, z)` or `VfxClient.play(id, entity)` (cheap, no-ops when Effects quality is Off;
  check `VfxClient.enabled()` first); server `Vfx.play(level, id, pos)` or `Vfx.play(entity, id)`, which send a payload to nearby players.

## Screen shake, flash, delays and sounds (server API, `com.skyrpg.vfx.Vfx`)

`Vfx.shake(player, strength, ticks)` swings the camera by up to `strength` degrees (0.3 rumble, 0.6 light hit, 1.0 solid, 1.6 slam), fading out over `ticks`;
`Vfx.shakeAround(level, pos, radius, strength, ticks)` does it for everyone near a point; `Vfx.flash(player, 0xAARRGGBB, ticks)` washes the screen;
`Vfx.later(server, ticks, runnable)` staggers things; `Vfx.sound(level, pos, "minecraft:...", volume, pitch)` plays a sound with +-6% pitch variation.
Shake respects Options > Accessibility > Distortion Effects and Effects quality Off. Test: `/vfx shake <strength> <ticks>`, `/vfx flash <argb int> <ticks>`.
Ability effects live in their own namespace (`skyrpg_abilities`, written by `tools/builds/vfx_abilities.py`); see abilities.md.

## Quality and testing

Settings > Effects > Effects quality (Off / Low / High): scales counts and binding rates, caps particles per tick and alive
particles. Vanilla's Particles option scales on top.

Commands (op): `/vfx play <id> [pos | entities]`, `/vfx list [effects|bindings]`, `/vfx reload` (prints parse problems).
