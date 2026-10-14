# Party (`party`, mod id `skyrpg_party`)

Persistent parties for the pack. Depends on `skyrpg_core` only (chat integration is a message key, see below). Decided 2026-10-14 (design calls are delegated).

Owner brief: a party that is persistent (log out, come back tomorrow, still in it, the overlay shows you offline), with a party tab, an always-on overlay with head and health, and no PvP inside a party.

## Decisions

- **Stored in the world save.** `PartyData` (a `SavedData` in the overworld's data storage, `data/skyrpg_party_parties.dat`) holds every party: id, leader UUID and the members in the order they joined. A member keeps their seat while offline; on logout (and while online, every sync) their last name, health, max health, dimension and block position are written to their seat, so offline members still show on the overlay, the page and the waypoint. Nothing is cleared on restart.
- **Up to 8 members, one leader.** If the leader leaves, the member who joined earliest becomes leader (the first in the saved list, "longest-standing"). A party that would be left with fewer than 2 members is disbanded, so there are no parties of one. Offline members count: a party of an online leader and one offline friend stays.
- **Invites.** Any member can invite (the leader or not), the invited player must not be in a party. An invite lasts 60 s (kept in memory, so a restart drops it), one per inviter and target. If the inviter is not in a party yet, the party is made when the invite is accepted (the inviter becomes leader). `accept` and `deny` take an optional inviter name (the newest invite when empty). The invite line has clickable **[Accept]** and **[Deny]** that run `/party accept <name>` and `/party deny <name>`.
- **Only the leader** can kick, promote and disband. Anyone can leave.
- **Chat.** `/party chat <message>` and `/pc <message>` send to online members; `/party chat` with no message toggles the sender's normal chat into party chat (stored in memory, cleared on logout, leaving the party, or when the party goes). The toggle is handled in the default phase of `ALLOW_CHAT_MESSAGE`, before the chat mod's late phase, so the public chat never sees it. Party chat is logged as `[Party] <Name> text`.
- **The Party tab (chat mod).** Every party line (chat, joins, leaves, invites, announcements) is a translatable component with the key `skyrpg_chat.party` and the fallback `%1$s%2$s` (header, body, sender name), exactly the pattern in `ChatLines`. The party mod only builds that key and does not depend on `chat` (without the chat mod the fallback renders the same line). The chat mod gained `ChatLines.PARTY`, `ChatKind.PARTY` (green-teal 0x4FD6A0, stripe "Party") and `ChatTab.PARTY`; the tab sits between Whispers and SkyRPG. Party lines are in All and Party only. A dot marks the tab when a party line arrived while another tab was showing.
- **Style.** Party lines use the pack's `Notice`: the shield icon, a bold `PARTY` tag in 0x4FD6A0, then the body. Chat lines are `PARTY  <crown> Name: message` (the crown only for the leader).
- **Health is SkyRPG health:** the player's vanilla health times `StatTypes.HEALTH_SCALE` (5), the same numbers the health bar shows.
- **Sync.** `skyrpg_party:state` (S2C) goes to every online member every 10 ticks (twice a second) while they have a party or a pending invite, and once more, empty, when they stop having either. It carries the leader, the size limit, whether party chat is on for the receiver, each member (UUID, name, online, hp, max hp, dimension, x, y, z) and the receiver's pending invites (inviter, seconds left). One payload for the overlay, the page and the tab list.
- **Actions.** The K menu page sends `skyrpg_party:action` (action name and a player name: invite, accept, deny, kick, promote, leave, disband, chat, track). The server runs the same code as the commands and checks everything again.
- **No friendly fire.** `ServerLivingEntityEvents.ALLOW_DAMAGE` returns false when the victim is a player and the cause is a player in the same party. The cause is `DamageSource.getEntity()`, else the direct entity, then resolved to its owner: a projectile's owner (arrows from bows, tridents, snowballs, thrown potions), and a tamed animal's owner. Every SkyRPG ability and AoE ends in `hurtServer`, so they are covered: the pack's melee abilities, enchant procs, thorns reflected back at a party member, harming potions (indirect magic with the thrower as cause). Self damage is untouched. Not covered: effects that tick without a cause (poison and wither from a thrown potion, lingering clouds' DoT after the first tick), fire from a flint and steel, TNT. Knockback is not applied because the hit is cancelled before it. Other mods can call `Parties.sameParty(a, b)` and `Parties.isFriendlyFire(victim, source)`.
- **Waypoint.** Clicking a member on the page sends `track`; the server calls `WaypointTarget.send` (`com.skyrpg.core.net`) with the member's current position (or last known position when offline) and their dimension, in the party colour. It is a one-shot target, not a follow.
- **Marks.** Core's `SkyRpgTab` got `registerMark(Function<UUID, Integer>)`: a coloured 2 px bar at the left edge of a player's row. The party mod marks members green-teal and adds a **Party** panel to the tab list (name and health per member, "offline" in grey; "Not in a party" otherwise). Not done: a marker or outline over party members' heads in the world (a per-viewer glow needs packet-level entity flags; skipped).

## The overlay (`PartyWidget`, a core `HudWidget`, id `skyrpg_party:party`)

- Registered with `HudManager`, so it is in the HUD editor (drag, scale, hide). Default: middle left, 48 px above the middle. `HudManager` nudges it off anything it would cover.
- 132 px wide: a header (`PARTY`, `members/8`) and one 22 px row per **other** member:
  - face (the skin head from the tab list's `PlayerInfo`; cached per UUID so an offline member keeps their face while this client runs; the default skin otherwise), dimmed when offline;
  - name, with a crown before the leader's;
  - a small aqua dimension tag at the right of the name line when they are not in your dimension (Hub, Nether, The End, Overworld);
  - a health bar with `hp/max` inside (long numbers shorten, `Draw.numberFor`): green above half, gold above a quarter, **red and pulsing** under a quarter. The fill eases to the new value;
  - offline: a grey bar that says `Offline`, a dim name.
- Hidden when you are not in a party. In the HUD editor with no party it shows three sample members.

## The Party page (`PartyPage`, K menu)

Page id `skyrpg_party:party` (dev tools name: `menu-party`), order 38 (after Mounts). A card per member (you too): face, crown, name (`(you)`), a status line (online: dimension, coordinates and distance when in the same dimension; offline: `Offline  last seen Overworld x, y, z`), a health bar with numbers. Click a card to set a waypoint to that member. The leader gets **Promote** and **Kick** on each card (kick asks for a second click). Buttons: **Party chat: ON/off**, **Leave** and, for the leader, **Disband** (both ask for a second click). Under the members: **Invite a player** with a name field (Enter or the button). **Pending invites** (with seconds left, **Accept** and **Deny**) show on top. Without a party it shows an explanation and the invite field.

## Files

Server: `SkyRpgParty` (entrypoint), `Parties` (all rules, messages, sync, friendly fire, public API), `PartyData` (saved data), `PartyCommands` (`/party`, `/p`, `/pc`), `PartyPayloads` (`State` S2C, `Action` C2S). Client: `SkyRpgPartyClient`, `PartyClient` (state, smoothed health, skin cache), `PartyWidget`, `PartyPage`. Changed elsewhere: chat (`ChatLines`, `ChatKind`, `ChatTab`, `ChatTabs` comment), core (`SkyRpgTab.registerMark`).

## Testing notes

Staging only. Carpet fake players act as the other members: `player Bot spawn`, `execute as Bot run party accept`, `player Bot attack` for the friendly-fire test. A party is persistent, so stopping and restarting staging keeps it (the `.dat` file is written when the server saves).
