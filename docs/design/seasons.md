# Seasons

Dated server events (module `events`, class `Seasons`), first used for Hallow's Eve 2026. Decision log: `progression.md` §12 #19.
Player docs: `docs/wiki/pages/skyhaven/hallows-eve.md`.

## A season file

`data/<ns>/skyrpg/seasons/<id>.json`:

| Field | Meaning |
| --- | --- |
| `name`, `color`, `bar_color` | Boss bar text and colour (`purple`, `red`, ...); the bar shows the time left while the season is on. |
| `start`, `end` | ISO instants (`2026-10-30T17:00:00Z`). UTC on purpose: the host's time zone can't move the event. |
| `flags` | Turned on in core `SeasonFlags` while live. `double_crates` doubles crate drop chances (crates mod). |
| `rotation`, `event_gap_minutes` | Replace `AutoEvents`' rotation and its 60 to 90 minute gap while live. |
| `start_banner`, `end_banner` | `/announce`-style banners for everyone when it starts and ends. |
| `start_commands`, `end_commands` | Console commands at start and end. |
| `welcome` | Once per player per season: `banner`, `commands` (`{player}` filled in), `flag` (a `TitleFlags` flag for a title). Players online at the start get it then, without the banner. |
| `nights` | Rules run at overworld dusk (13000 to 18000, once per night, someone in the overworld): `every` N nights (from the first night), or `once` after an instant (`after`). |

State (started, ended, nights counted, who was welcomed, `once` rules done) is saved in `<world>/skyrpg/seasons.json`. A season the
server missed entirely is marked done without running; one that began while the server was down starts late.

| `splash` | The full-screen splash on joining (`image` link, `subtitle`, `lines`, `sound`, `pitch`, `color`, `announce_days`): sent 4 s after join while the season is live, or before it within `announce_days`. The client shows it once a day per season and state, unless forced by `/season splash`. |

## Showcase (splash and billboards)

- `ShowcasePayloads` (events): `Splash`, JSON, server to client.
- Pictures are web links loaded by core client `RemoteImages` (worker thread, disk cache in `cache/skyrpg/remote`, refetched once a session so
  a changed picture shows, https/http, no private addresses, 8 MB, at most 2048 px). Event art lives in `docs/showcase/`, which
  `tools/wiki/publish_pages.py` mirrors to `<site>/showcase/`, so new art needs no pack update.
- `SplashScreen` (client): picture fading in with a slow zoom behind turning rays, embers, a LIVE / COMING SOON pill, the title slamming in,
  the lines sliding in (two columns when there's room), a countdown, any key or click to close. A failure closes it rather than crash.
- Billboards are entities (`events/billboard`): `BillboardEntity` (synced facing, width 3-16 at 16:9, stand or wall, theme, seconds,
  links; owner; hitbox = cabinet and posts, collidable; owner/op right-click edits, hit breaks into the item; invisible `light` blocks
  in front, removed with it), `BillboardItem` (side of a block: on the wall; top: on posts facing you; needs room), `BillboardTheme`
  (vanilla block textures per look), the `Edit`/`Configure` payloads, a shaped recipe. Client: `BillboardEntityRenderer` builds the
  cabinet, frame, trim, truss, cap, chasing redstone-lamp bulbs, sea-lantern spotlights with a light fan and posts from block-atlas
  sprites tiled per block (`entitySolid`, world light), the picture full bright (`text`, cover-cropped); `BillboardScreen` is the editor.
  This replaced a first command-placed, client-drawn version the owner found flat. Tested on staging (2026-10-23).

## Commands

`/season` (anyone): what's on and the time left, or the next one. Ops: `/season start <id>` forces one now (a test); `/season end <id>`
ends it, and if that was a forced run before the real start, the state is forgotten so the real one still starts on time.

## Cross-mod hooks

Seasons only use commands and core: `worldboss start <id>` (combat), `bloodmoon start` (world), `give`, and `SeasonFlags` / `TitleFlags`.
World bosses gained `head`, `mainhand`, `minion_head` (never dropped) and `auto` (false = only by id).
