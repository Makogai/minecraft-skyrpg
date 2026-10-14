# Combat tag and teleport warm-ups

Decided 2026-10-14 (design calls are delegated). The owners: "you can't just tp to hub whenever, you need to sit still, and
can't do it in combat".

- **Tag:** 10 s after dealing damage to or taking damage from a hostile mob (`Enemy`) or another player. Projectiles and
  abilities count for their owner (`DamageSource.getEntity()`); anything without an attacker (fall, fire, drowning, void)
  does not. Each hit restarts the timer. Death and logout clear it. Creative and spectator players are never tagged.
- **Warm-up:** 5 s standing still for `/hub`, `/survival` and waystone travel. Refused outright while tagged. Cancelled by
  moving more than 0.3 blocks, taking damage, getting tagged, dying, logging out or changing dimension. Running the command
  again cancels. Creative and spectator players skip it. Waystone XP is charged on arrival.
- **Not covered:** walking through the Wildgate portal, the hub void rescue, event arena teleports, admin commands.
- **Numbers:** `data/skyrpg_core/skyrpg/combat/rules.json`. Code: `core` `com.skyrpg.core.combat` (`CombatTag`, `Warmups`,
  `CombatRules`, `CombatTagPayload`) and the client `CombatTagWidget`.
