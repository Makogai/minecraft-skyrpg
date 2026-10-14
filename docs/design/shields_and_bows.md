# Shields and bows

Decided and built 2026-10-14 (design calls are delegated, see `docs/design/progression.md` "Decisions (made)"). The owner asked for
"new shields, progressively looking bigger and nicer, all 3D-ish, with different effects" and "bows, we have no ranged weapons".
Wiki pages: `shields` and `bows` (`docs/wiki/pages/gear/`).

## Shields

Seven, one per stage. They are plain registered items (`ModItems.shield(...)`, kind `SHIELD`) with vanilla's **`minecraft:blocks_attacks`**
component, so they block like a shield without any shield class: 90 degrees either side of where you look, a 0.25 s raise delay, the vanilla
"bypassed by" tag (fire, starving, magic...), vanilla sounds, and axes disabling them. What differs is the damage reduction: `factor` is the
share blocked (0.5 to 0.9), `base` 0. Every SkyRPG item is unbreakable, so the item-damage function never matters. They carry
`equippable` for the off-hand slot (unswappable), so shift-click puts them there.

| # | Shield | Rarity | Blocks | Defense | Health | Effect (gear effect in the definition) |
|---|---|---|---|---|---|---|
| 1 | Oak Buckler | common | 50% | 10 | 8 | none |
| 2 | Iron Kite Shield | uncommon | 65% | 22 | 20 | none |
| 3 | Skyiron Bulwark | rare | 75% | 36 | 30 | `skyrpg_items:shield_bash` 1.2: knocks a blocked melee attacker back |
| 4 | Emberforged Aegis | epic | 80% | 50 | 45 | `shield_ignite` 4: blocked attackers burn for 4 s; ember particles |
| 5 | Frostward Barrier | epic | 80% | 50 | 45 | `shield_frost` 3: blocked attackers slowed (Slowness V) and frozen 3 s; frost particles |
| 6 | Thunderguard | legendary | 85% | 65 (+3% crit chance) | 60 | `shield_charge` 5 and `shield_shockwave` 40: each block stores a charge (max 5), sneak + use releases them as lightning, 40 damage per charge, radius 3 + charges |
| 7 | Astral Aegis | legendary | 90% | 85 (+25 intelligence) | 80 | `shield_reflect` 1: projectiles that hit it from the front fly back at the shooter and become yours |

Numbers follow the armour ladder: a shield is worth roughly 40% of a chestplate's Defense and Health at its tier.

**Stats and effects count from the off hand** (`SkyItems.offhandShield`, used by `ItemStatSource` and `GearEffectSources`; items otherwise only
count in the main hand). If the main hand also holds a shield, only that one counts.

**Where the code is:** `com.skyrpg.items.shield.Shields`.
- `ServerLivingEntityEvents.AFTER_DAMAGE` with `blocked == true`: bash (a push on the attacker, `syncVelocity`), ignite, frost (`Bows.freeze`),
  and the Thunderguard's charge (a synced `Integer` attachment `skyrpg_items:shield_charges`, so the client knows whether sneak + use does something).
- `ALLOW_DAMAGE` for the reflect: a projectile that would hit a *blocking* player with the effect, from the front, is turned round (towards its shooter's
  eyes), re-owned by the player and, if it is an arrow, tagged with SkyRPG damage (1.5 x what it would have done, at least 60) so it counts for
  kills, loot and slayers. The damage is cancelled.
- `UseItemCallback`: sneak + use with a shield that has `shield_shockwave` and at least one charge releases them instead of raising the shield.
  Damage uses `playerAttack(player)`, like Thunderclap and Starfall, so Strength and crits apply.
- Held particles (`held_particles` in the definition) now also work for a shield in the off hand (`HeldParticles`, client).

**Recipes** (combat, bench, each upgrades the one before so enchants carry over): Combat 3, 10, 18, 26, 30, 35, 45; Oak Buckler from oak logs,
iron and Valor Marks; the rest add Valor Seals, Dread Fangs and a tier material (Skyiron, Ember Cores and Blaze Rods, Blue Ice and Rime Shards,
copper and lightning rods with a Nightmare Core, Astral Crystals and a Prism). Sell prices 300 to 220,000 (`combat/.../sell_prices/shields_bows.json`).

## Bows

Seven, the first ranged weapons. `SkyBowItem extends BowItem`: vanilla draw, vanilla arrows as ammo (`ARROW_ONLY`), infinity and other vanilla rules
unchanged. Each bow definition has a `"bow"` object (see `Bows`), and its `damage` stat is the damage of a full-draw arrow.

**Damage pipeline.** `SkyBowItem.shootProjectile` tags each arrow with `SkyArrows.tag(arrow, bowDamage * draw * share)` (`core/.../stats/SkyArrows`, a
Fabric attachment). `CombatStats.modifyDamage` (the hurt wrapper that already does melee) sees an arrow with that tag and a player owner, throws away
vanilla's speed-based amount and uses the tag, then applies **Strength and the Crit Chance / Crit Damage roll** exactly like melee. Everything after is
unchanged (other multipliers such as slayer bonuses and Deadeye, defense for players), and because the damage source is a normal arrow with the player as
owner, `DamageNumbers`, combat XP and loot (`CombatRewards`), slayer progress and kill credit all already count the shooter. The bow's attack-damage
attribute is not set (hitting with the bow is a poke); its Strength and crit stats count while it is held.

| # | Bow | Rarity | Damage | Stats | Effect (`"bow"` object) |
|---|---|---|---|---|---|
| 1 | Hunter's Bow | common | 30 | 10 str | trail: crit sparks |
| 2 | Recurve Bow | uncommon | 50 | 18 str, 10 crit dmg | `draw_speed` 1.3 (the item model's pull stages scale with it) |
| 3 | Skyiron Longbow | rare | 70 | 30 str, 20 crit dmg | `pierce` 1 (an invoker mixin on `AbstractArrow.setPierceLevel`) |
| 4 | Ember Bow | epic | 90 | 45 str, 4 cc, 30 cd | `fire`, `burst` radius 2.5, share 0.6 (explosion damage source with the shooter as cause: no block damage, no fire spread) |
| 5 | Frostbite Bow | epic | 95 | 50 str, 6 cc, 35 cd | `freeze` 3 s (Slowness V and frozen ticks) |
| 6 | Stormcaller Bow | legendary | 125 | 70 str, 8 cc, 45 cd | `lightning` every 3rd full-draw shot, share 0.8 (visual-only bolt + magic damage) |
| 7 | Starfall Bow | legendary | 100 per arrow | 90 str, 10 cc, 60 cd | `arrows` 3, `arrow_share` 0.55, `spread` 4, `homing` 0.12 |

The Starfall Bow's extra arrows cost nothing, can't be picked up and vanish where they land; with three hits it deals 165% of its Damage. Homing
bends the arrow towards the best enemy in a 14 block box in front of it, each tick. Trails are server particles every tick of flight
(`"trail": [{"particle": ..., "count", "chance", "spread", "speed"}]`).

**Enchants.** The enchants mod already has a `bow` category and recognises `BowItem`, so Deadeye, Longshot, Quiversong, Splitshot, Starshot and
Cinder Arrows apply to every SkyRPG bow; nothing was needed. (Other categories, like `sword`, are unchanged.)

**Recipes:** the bow line is Foraging-fed as the progression plan said ("Bows: from Mistpine and Ironbark"): Ironbark, Mistpine, Duskwillow,
Emberwood and Starwood logs with the combat materials, at Combat 3, 10, 18, 26, 30, 35, 45, each upgrading the previous bow.

## Art (3D)

`tools/art/generate_shields_bows.py` (with `sb_common.py`: ramps, noise, the model builder and an offline renderer) writes the item definitions, models
and textures of the fourteen items into `items/src/main/resources/assets/skyrpg_items/` and touches nothing else. `--preview DIR` writes offline renders.

- **Shields** are height-fields: a 24 x 24 grid whose cells are 0.52 (Buckler) to 0.86 (Astral Aegis) model units, so the shields grow from 9 to 21 units
  wide. A tier's outline, rims, ridges and bosses are layers; each run of equal-layer cells is one cuboid whose front face shows its slice of a painted
  96 x 96 front texture (with bevel shading from the layers), the sides use the run's average colour, the back is a plain painted plate with a grip and
  straps. Glow (ember cracks, storm veins, the Astral stars) is a second transparent texture on flat quads just in front of the cuboids with
  `light_emission: 15`; the Astral Aegis' is an 8-frame animation (stars twinkle, a nebula shimmer drifts), the Thunderguard's and Emberforged
  Aegis' pulse over 4 frames. Models: `<id>` and `<id>_blocking`; the item definition switches on `using_item`.
- **Bows** are stacked 1-unit boxes along a curved limb profile (taper, recurve), plus per-bow ornaments (bands, caps, crystals, wings, flames), a
  string and, while drawing, a nocked arrow; the string is a staircase of boxes pulled back 1.2, 3.6 and 6.4 units in `_pulling_0/1/2`, with the
  limbs flexing. The item definition is `condition(using_item) -> range_dispatch(use_duration)` like vanilla's bow.
- Each model has `layer0` pointing at a rendered icon, which is what the wiki uses.
- Display transforms (first and third person, GUI, ground, frame) were tuned against SkyCam screenshots.

## Tuning notes

- Shields: `Shields.blocksAttacks`, per-shield blocked share in `ModItems`, effect numbers in the definitions.
- Bows: damage and the `"bow"` objects in the definitions; `Bows` for the effects.
- Everything is data except the effect code.

## Tested on staging (2026-10-14)

SkyCam, offline renders in `tools/art/generate_shields_bows.py --preview`, and Carpet-free command tests:
- All seven shields in hand and raised, all seven bows drawn (first person). Display transforms are in `shield_display` and `DISPLAY_BOW` in the
  generator. Findings: the left-hand blocking pose is turned by the engine, so the raised transform is not the idle one plus an offset
  (idle `[-2, 6, -2]`, raised rotation `[0, -75, -68]` and translation `[13, 17, 8]`); the first-person scale shrinks with shield size
  (`0.9 * (9.6 / extent) ** 0.9`) so the biggest ones fit the screen; bows need rotation y 135 to point the arrow away.
- Bow arrows deal SkyRPG damage (an Elite zombie lost 433 SkyRPG damage in one Stormcaller arrow with a crit), kills credit the shooter (Elite
  Trophy and Valor Marks dropped), the Ember Bow's burst kills the zombie next to the target, the Starfall Bow fires 3 arrows that vanish where
  they land.
- Shields: Bash pushes a blocking zombie out 3 blocks; Emberforged Aegis sets the attacker on fire; Frostward Barrier gives Slowness V and frozen
  ticks; Thunderguard shows "Storm charge 2/5" and sneak + use deals 80 (2 x 40) to everything near; Astral Aegis turns an arrow round
  (Motion z -0.9 becomes +1.16).
- Fixed on the way: bursts, frost and lightning also trigger on a killing arrow (Fabric's AFTER_DAMAGE skips dying targets, so `Bows` also
  listens to AFTER_DEATH); reflect decides "from the front" from the projectile's flight, not its position (which can already be behind
  you at the hit), and sets the velocity again on the next tick because vanilla bounces a projectile that failed to hurt.
- Not tested in game: Longbow pierce, Frostbite Bow freeze, the Stormcaller lightning proc, homing strength, Recurve draw speed.
- Damage fully absorbed by Defense (or Resistance) is not a hit for Fabric's events, so it stores no storm charge.
