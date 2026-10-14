# Wands and spells (`magic` mod, `skyrpg_magic`)

Decided and built 2026-10-14 (design calls are delegated, see `docs/design/progression.md` "Decisions (made)"). The owner asked for
"wands, a spell system, spell scrolls you install in wands, fire on left click or hold to charge, right click to select a spell on a circle
selector, so we can have a mage build", because mana had no nice way to be used. Wiki pages: `wands` (gear) and `spells` (combat).

## The idea

Seven **wands** give Intelligence (mana) and hold **spell scrolls** in slots. With a wand in the main hand the mouse is for spells:

| Input (wand in the main hand) | What it does |
|---|---|
| **Left click** | Cast the selected spell. No melee swing, no block breaking. |
| **Hold left click** | Charge a chargeable spell (Fireball, Meteor): up to 1.5 s, a ring fills round the crosshair, a rising chime plays, a full-charge ping; **release** to cast. |
| **Hold right click** (0.25 s) | The **spell wheel** opens on screen: the wand's spells on a ring, with icon, name, mana cost and cooldown. Move the mouse towards one and **release** to select. Clicking a spell also selects it. |
| **Sneak + right click** | Open the wand's **spell slots** to put scrolls in and take them out. |
| **Sneak + mouse wheel** | Cycle through the wand's spells. |

Right click still works on what has a use of its own: a chest, a door, an NPC, an animal, a lever (the wheel only opens when you aim at
nothing, a plain block, or a hostile mob). A shield in the off hand raises as usual (the wheel still opens if you keep holding).
Keys: **Spell wheel (hold)** defaults to right click and **Spell wheel (toggle)** is G (press to open, click a spell or press again),
both under Controls > SkyRPG. Left click is the vanilla attack key, so rebinding it moves casting too.

Casting is **server authoritative**: the client sends *press*, *release*, *select slot N* and *open slots* (`MagicPayloads`); the server checks the
wand, the selected spell, cooldown and mana, measures the charge itself in server ticks, runs the spell and charges mana. The server sends back
the definitions (on join and every `/reload`), cooldowns, a cancelled charge and screen shakes.

## Wands

Items in this mod (`skyrpg_magic:<id>`, class `WandItem`), with SkyRPG item definitions in `data/skyrpg_magic/skyrpg/items/` (so tooltips, rarity,
held stats, sell prices and the wiki treat them like any other item). Wand-specific numbers are in `data/skyrpg_magic/skyrpg/wands/<id>.json`.

| Wand | Rarity | Slots | Intelligence | Bonus |
|---|---|---|---|---|
| Apprentice Wand | common | 2 | 20 | |
| Gilded Wand | uncommon | 3 | 40 | |
| Skyiron Rod | rare | 3 | 60 | spells cost 10% less mana |
| Emberheart Staff | epic | 4 | 90 | fire spells +25% damage |
| Frostspire Staff | epic | 4 | 90 | frost spells +25% damage, slows last 50% longer |
| Tempest Scepter | legendary | 5 | 130 | Chain Lightning jumps one more time |
| Starweaver Staff | legendary (End tier) | 5 | 170 | spell cooldowns 20% shorter |

**Recipes** (Artisan Bench, category *Enchanting*, gated by the **Enchanting** skill; each upgrades the one before, so enchants and scrolls carry over;
`data/.../recipes/`): Apprentice from sticks, lapis and Runic Dust at Enchanting 3; Gilded 10; Skyiron Rod 18; the two epic staves 26 (both from the
Skyiron Rod); Tempest Scepter 32 (from the Skyiron Rod, with Stormfeathers, Gale Essence, Prism Fragments and a Nightmare Core); Starweaver
Staff 45 (from the Tempest Scepter, with Astral Crystals, Prisms, Nightmare Cores and a Star Warden Core). Sell prices 400 to 260,000
(`sell_prices/magic.json`).

**Where the scrolls live:** the wand's scrolls are its vanilla **container contents** (slot by slot) and the selected slot is a small component
(`skyrpg_magic:selected_spell`). Both are synced, so the wheel and HUD read them off the stack on the client; and because the bench's upgrade copies
`container`, an upgraded wand keeps its scrolls. Wand stack to spells: `WandItem.data` / `setData`.

**Intelligence and mana:** a held wand's stats count through the items mod (main-hand stats). Mana is the stats mod's: 100 + Intelligence, 2%
of the maximum regained each second. An Apprentice Wand gives 120 mana, a Starweaver Staff 270.

## Spells

Twelve, one scroll item each (`<spell>_scroll`). All numbers are data in `data/skyrpg_magic/skyrpg/spells/<id>.json` (`params`); the effects are code
(`spell/FireSpells`, `FrostSpells`, `LightningSpells`, `ArcaneSpells`, `NatureSpells`). Damage is
`base x (1 + Intelligence / 100) x (1 + the wand's element bonus)`, in SkyRPG damage units (5 per vanilla point, like weapons).

| Spell | Element | Rarity | Mana | Cooldown | What it does |
|---|---|---|---|---|---|
| Firebolt | fire | common | 8 | 0.5 s | A fast bolt, 35 damage, burns for 3 s (8 per second). |
| Fireball | fire | uncommon | 24 | 3 s | Chargeable. Explodes on impact: 70 damage in a 3 block radius, falling off to 55% at the edge, burns 4 s. Fully charged: 5.5 blocks and double damage. No block damage, no fires. |
| Meteor | fire | legendary | 60 | 12 s | Chargeable. A meteor falls on the spot you aim at after 1.3 s (a ring marks it): 170 damage in 5 blocks, burns 5 s; charged: 8 blocks and double damage. |
| Frost Shard | frost | common | 7 | 0.6 s | A fast shard that pierces 3 enemies, 32 damage each, Slowness III for 2.5 s. |
| Blizzard | frost | rare | 50 | 14 s | A snowstorm on the spot you aim at, 5 block radius for 6 s: 24 damage twice a second and a slow. |
| Chain Lightning | lightning | rare | 22 | 2.5 s | 52 damage to the target you aim at, then it leaps to the nearest enemy within 7 blocks: up to 4 enemies (5 with the Tempest Scepter). Nothing to hit: it fizzles and costs nothing. |
| Thunderclap | lightning | epic | 38 | 8 s | 62 damage in a 6 block circle around you, knocks back and stuns for 1.4 s. Visual-only lightning on up to four targets. |
| Arcane Missiles | arcane | uncommon | 26 | 4 s | Three homing missiles fan out 3 ticks apart, 30 damage each. |
| Blink | arcane | uncommon | 16 | 6 s | Teleport up to 8 blocks along where you look, as far as there is room and a safe floor (never into lava or a wall). No room: nothing is spent. |
| Mana Shield | arcane | rare | 30 | 20 s | For 8 s damage you take is paid from your mana instead, 0.5 mana per point; when the mana runs out the shield breaks and the hit lands. |
| Healing Pulse | nature | common | 30 | 6 s | Heals you and party members within 8 blocks for 60 (x Intelligence scaling). |
| Entangle | nature | uncommon | 25 | 9 s | Roots enemies in a 4 block circle where you aim for 4 s and hurts them 14 every second. |

**Who spells hit** (`Targeting`): not yourself, party members (the party mod's rule, plus its `ALLOW_DAMAGE` hook which also covers spells), your pets,
villagers and NPCs, armour stands, things that cannot be hurt; other players only where `canHarmPlayer` allows pvp. Projectiles hit what they
touch; area spells, chains and homing missiles go for **hostile** things (hostile mobs, mobs with a target, training dummies, pvp targets).

**Damage pipeline.** A spell hit is a normal `indirect_magic` hit with the caster as its cause and as the direct entity (`SpellDamage`), so damage
numbers, combat XP, Valor Marks and loot, slayer progress, kill credit and the party rule already count the caster, and the stats mod applies
slayer-boss multipliers and a player's Defense. It is not a `PLAYER_ATTACK`, so **Strength and melee crits do not apply**: spells scale with
Intelligence. Vanilla's invulnerability frames are cleared before each spell hit (an accessor mixin), so three missiles or a blizzard tick are not
swallowed. Knockback is applied by hand. Burning is the mod's own damage-over-time (`Statuses`), not vanilla fire, so a burning kill still credits the
caster. Slowness is the vanilla effect; roots hold a mob's movement and navigation each tick.

## Obtaining scrolls

- **Seraphine Vael** (Arcane Library) sells the **common** scrolls (Firebolt, Frost Shard, Healing Pulse, 500 to 700 coins). Talk to her **while
  sneaking** for the shop (normal talk is still Arcane Enchanting; `Seraphine` wraps the `open_enchanting` action once the server starts, and a
  greeting tells you).
- The **Artisan Bench** crafts the uncommon and rare ones (Enchanting 8 to 22): paper, Runic Dust and an element's materials (`SCROLL_RECIPES` in
  the generator).
- **Rare, epic and legendary** scrolls also **drop** at a low chance (3 to 12%, Meteor 1.5 to 5%) from bosses and the hardest dungeon rewards,
  added to their loot tables through Fabric's `LootTableEvents.MODIFY` (`MagicLoot`: Magma Tyrant, Iron Thane, Frost Revenant, Gravecaller, Ender
  Wyrm, Grove Sentinel and the dungeon bosses). **Thunderclap and Meteor** only drop. Under a **Blood Moon**, hostile mobs you kill drop
  a scroll (uncommon to epic, never Meteor) with a 0.4% chance, better with Magic Find.

## Art and effects

- `tools/art/generate_wands.py`: wand models (7 to 40 cuboids standing along y, glowing parts with `light_emission`, display transforms are vanilla's
  handheld ones with a 45 degree turn folded in) and the twelve scroll icons (rolled parchment, element-coloured border, a glyph per spell); it
  writes only into `magic/src/main/resources/assets/skyrpg_magic/`. `--preview DIR` writes offline renders.
- `tools/builds/magic_content.py` writes the data: spells, wands, item definitions, recipes, sell prices, Seraphine's shop, language file and **37 VFX
  effects** and 5 bindings (`vfx/`, `vfx_bindings/`) in this mod's data. Edit the tables at the top of that file and re-run it.
- Casting: a flash at the wand (`cast_<element>`), per-tick trails (`*_trail` effects played at the projectile), impacts and rings, lightning arcs as
  jagged particle lines, and vanilla sounds per element. Meteor and Thunderclap, a charged Fireball and Blizzard's start shake the screen: the
  server sends `Shake`, and the client nudges the **camera** (a mixin on `Camera.update`; your aim is not touched).
- Held wands glow at the tip through VFX bindings (`held_*`, for other players: effects at your own hand sit nowhere near the first-person model, so
  the emissive tips of the model itself are your glow) and, in third person, through the item definitions' `held_particles`. A charge's particles are
  sent to everyone but the caster (right in front of your own camera they fill the screen; the ring is your cue).

## Client

`com.skyrpg.magic.client`: `MagicInput` (the keys and mixins' decisions), `SpellWheelScreen` (the wheel: pops open with an overshoot in 6 ticks,
shrinks away in 4; a precomputed ring map drawn as runs of fills; open and close chimes), `SpellWidget` (a HUD box, movable in the HUD editor:
icon with the cooldown sweeping over it, name, mana cost, "Not enough mana" flash, charge bar, a pip per slot), `ChargeRing` (round the
crosshair), `MagicTooltips` (scrolls and wands get element, mana, recharge and the slot list in a late tooltip phase), `ClientMagic` (state).
Mixins: `MinecraftMixin` (no melee, block breaking or use while a wand is held), `MouseHandlerMixin` (sneak + scroll), `CameraMixin` (shake).

The slots are a vanilla one-row container menu (`WandMenu`): the wand's slots in the middle, the rest filler panes; only scrolls (each spell once) can
go in; every change is written to the wand; the wand cannot be moved while it is open and the menu closes if it leaves the hand. Removing a scroll gives the
real scroll item back.

## Commands (ops)

`/magic install <spell>` puts a spell in the held wand's first free slot, `/magic clear` empties it, `/magic cast <spell> [charge]` casts as yourself
(mana and cooldown still apply). `/items give` gives wands and scrolls. In Devtools, `set:holdkey:attack:N` holds left click and `set:holdkey:use:N`
right click.

## Not done

- A mage armour set and Intelligence accessories (the wands are the build for now: an Astral Aegis adds 25 Intelligence in the off hand).
- Scroll recipes for the epic and legendary spells, spell upgrades and a wand skin.
- The wheel only opens with the wand's own right click or the toggle key; a controller binding is not offered.

## Tested on staging (2026-10-14)

SkyCam on the staging server, through real input (`set:holdkey:attack:N`, `set:holduse:N`, `set:holdkey:sneak:N`, `set:click`/`set:mouse` on the wheel):
- Left click with the Gilded Wand casts the selected spell: mana spent (145 to 137 for a Firebolt), the HUD box shows the cooldown sweep, two Firebolts kill a
  100 health zombie, and the **kill credits the caster**: the Combat XP toast, "In combat" tag, damage numbers over the target (a 52 Chain Lightning showed
  75 with 40 Intelligence).
- Hold left click charges a Fireball: the ring fills and the HUD bar shows it; a full charge exploded for 203 and threw the zombies; Meteor (charged, 463 on a
  500 health zombie with burn ticks after), Blizzard (killed two zombies in 6 s), Chain Lightning, Thunderclap, Arcane Missiles, Frost Shard (pierce and kill),
  Entangle, Healing Pulse (11.2 to 23.2 health, capped), Mana Shield (a hit of 8 cost 20 mana and no health), Blink (stops short of mobs in the way) all
  cast and show their effects.
- Hold right click: the wheel opens centred with the wand's spells; hover and click select (HUD changes to Fireball); the screen shows empty slots as "Empty".
- Sneak + right click opens the slots screen; clicking a scroll out of a slot put the real scroll back in the inventory and the wand's container lost it.
- "Not enough mana" shows in the overlay and in the HUD box; the camera shakes for the big spells.
- Tooltips of a wand and of a scroll show the element, mana, recharge and the slot list.
- Fixed on the way: an unbound key mapping with value -1 crashed `KeyMapping.setAll` (the toggle key defaults to G); vanilla `flame` particles in the explosion effects
  were screen-filling (removed); the held glow and cast flash drew on top of the first-person view (turned off for yourself); the wheel's description overlapped HUD boxes.
- Not exercised in game: Seraphine's sneak shop (compiled and wired; the shop data loads), the bench recipes beyond loading (245 recipes load with no
  errors), loot-table additions, Blood Moon drops, pvp targets, party healing.
