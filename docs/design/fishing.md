# Fishing feel (cues, bait, hotspots)

Owner request: a ding when something bites, an exclamation mark when a fish approaches, bait you can see and keep track of,
and bait that is used per catch instead of for a period.

## Bite cues
- A big text display over the bobber (a name tag was too small at a distance): yellow `!` while `timeUntilHooked > 0`
  (fish swimming in), bold red `!!!` while `nibble > 0` (the bite). Server-side, no client code, works in lava. Set from a
  `catchingFish` TAIL injection (`FishingHookMixin`, `BiteCues`) when the state changes; removed with the bobber.
- The bite plays a pling and a bell to the fisher only (`Msg.playSound`). A landed catch plays an orb pickup and a chime.
- No client toggle: the marks are an entity name tag, the sounds are the fisher's own. (Add one in Settings if it is wanted.)
- Sea creature: chosen at the moment of reeling in (`trySeaCreature`), so it can only be signalled then: title, subtitle,
  action bar, splash, sounds; rare ones (weight <= 3 or boss bar) stronger plus a chat line for players within 48 blocks.

## Bait: one per catch
- `Bait.pick`: offhand, then hotbar left to right, then the inventory. Picked at casting (`BaitHolder` on the hook).
- Applied as a `CastBonus` (speed in the lure wait, sea creature chance, double catch). Consumed in `Catching.landed`
  for every catch, fish or sea creature, never on casting or an empty reel. Creative keeps its bait.
- Right-click only explains. The old 10-minute boost is gone (old `Boosts` expire by themselves).
- Looping bobber particles per bait (dirt crumbs, glow, squid ink), `BiteCues.baitParticles`.
- HUD: `BaitWidget` (client) is a normal `HudWidget` so it can be moved/hidden in the HUD editor. Bait is read from the
  client inventory; the hotspot line comes from `FishingPayloads.Status`.

## Catches go to the inventory
`retrieve` wraps every `addFreshEntity` of an `ItemEntity` and hands the stack to `Catching.give` (ItemGains FISHING, once;
pickup sound; leftovers at the player's feet). The XP orb is untouched. Double Catch gives its extra loot the same way.

## Hotspots
`Hotspots`, data in `skyrpg/hotspots/hotspots.json`. Each second a player who holds a rod may roll a spawn (per-player
cooldown, at most `max_near` within `near_range`, never in the hub, lava only for lava rods in the Nether). The site is a
circle of fluid at least `min_depth` deep under `min_fill` of its sample points with air above. Particles go out to players
within 128 blocks (a turning ring and a pillar); the label is a text display (tagged, tracked and discarded on stop; strays
are swept) updated each second via `data merge`. The bobber's position decides the buff (`CastBonus.of`), including the
Fishing Speed (the hook's lure wait is recomputed every tick). `/hotspot <pos> <stat> <value>` (op) spawns a test one.

## Sea creature XP
Fishing XP goes to the fisher (stored in `Live`), loot to the killer, Combat XP: the body's normal kill XP (skill_xp table) plus half the creature's Fishing XP for the killer (`COMBAT_SHARE`).
