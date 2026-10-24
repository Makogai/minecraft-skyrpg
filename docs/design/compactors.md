# Enchanted materials, Personal Compactors and Deletors

Hypixel-style inventory relief for long gathering trips. All of it lives in the items mod (`com.skyrpg.items.compactor`).

## Enchanted materials

- `skyrpg_items:enchanted_<material>`: 160 of a bulk material in one item, for 104 materials (vanilla blocks, ores, crops,
  logs, mob drops, fish, and the SkyRPG gathering materials). Plain items with the glint, a green name and a "160 X, packed
  into one" lore line; stack to 64.
- The table lives in `Enchanted.MATERIALS`. Everything else is generated from the same list by
  `tools/gen_enchanted.py`: the item model files (copies of the material's own `items/<id>.json`, so they look identical), the
  names in `assets/skyrpg_items/lang/en_us.json`, one Artisan Bench recipe each (`recipes/enchanted/`, 160 → 1) and the
  sell prices (`sell_prices/enchanted.json`, 160 × the material's price, only where the material has one).
- One tier only. A second tier (160 enchanted → one "enchanted block") can be added later as more rows.
- No unpacking, as on Hypixel.

## Personal tools

- `personal_compactor_<4000|5000|6000|7000>` and `personal_deletor_<...>`: 3 / 5 / 7 / 12 material slots,
  uncommon to legendary. Bench recipes in `recipes/personal/`, each tier upgrading the one before; `Bench` copies the filter
  onto the new tier (`PersonalTools.carryOver`).
- The filter is a comma-separated id list in custom data (`skyrpg_filter`); the lore is rewritten whenever it changes (and
  when the menu opens).
- They work from the 36 main inventory slots and from inside the three carried backpacks (the tool, that is: only
  materials in the main inventory are compacted). Several carried tools add up.
- **Compacting** happens on every gain of a compactor material (`ItemGains.GAINED`), when an auto-pickup doesn't fit
  (`ItemGains.MAKE_ROOM`, then the add is retried) and before a normal pickup of a compactor material into a full inventory
  (`ItemEntityPickupMixin`). Only plain stacks (no component changes) are counted or taken.
- **Deleting** happens before an item reaches the inventory: `ItemGains.DELETE` for mining/farming/foraging/fishing/minion
  gains, and `ItemEntityPickupMixin` for pickups of ownerless item entities (never items a player dropped). Deleted items
  still fire `GAINED`, so collections count them. Only stackable plain items can be set, so gear is never deleted.
- The settings menu (`FilterMenu`) is a vanilla 9x1/9x2 chest menu: no client code. Clicks never move items; the server
  answers each click with the real contents.

# Wardrobe

`com.skyrpg.items.wardrobe`: 18 stored armour sets (two pages of nine) in a persistent, copy-on-death attachment
(`skyrpg_items:wardrobe`: 72 pieces + the worn set's index). Opened with `/wardrobe`, `/wd` or O. A six-row vanilla chest
menu: rows 1-4 are real slots (only the matching equippable slot, one item each), row 5 the equip buttons, row 6 the
page arrows. Equipping moves the set onto the player and empties its slot; the previously worn set goes back to its slot,
or to the inventory if it wasn't a wardrobe set (refused if it won't fit). The worn set's column shows the worn pieces and
is locked. Tested on staging with `devtools click` (no duplication through store, swap and take off).

# Server status posts

`com.skyrpg.core.discord.ServerStatusHook`: posts "online" (SERVER_STARTED, off-thread) and "offline" (SERVER_STOPPING, on
the server thread so it finishes before the JVM exits; uptime and who was online) to the webhook in the server's
`config/skyrpg/discord.json` (`{"webhook": "...", "name": "..."}`). No file, no posts. A `discord_running.marker` in that
folder while running flags the next start when the last run didn't stop cleanly.
