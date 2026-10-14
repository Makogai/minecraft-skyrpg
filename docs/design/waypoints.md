# Waypoints (utils)

Client-side, in `utils/.../map/`. Nothing here is server state except the optional target payload.

## Data
- `Waypoints` keeps `waypoints.json` next to the map tiles (per server / world, per dimension): `[{name,x,y,z,color}]`
  (ARGB). Same name replaces. Loaded when `MapCache.use(...)` switches world/dimension.
- One **tracked target** in memory (`Target(dimension|null, name, x, y, z, color)`), only shown in its dimension and
  cleared on disconnect or on arriving (within 3 blocks).
- Death: the first tick the player is dead adds/replaces "Last death" (red).

## Making them
- `/waypoint add <name> [x y z]`, `remove <name>`, `list` (with Track/Share/Remove buttons), `track <name> | <name> x y z`,
  `untrack`, `share <name>`. `/wp` is an alias. Names with spaces in quotes.
- World map: right-click empty map -> `WaypointDialog` (name + 8 colour presets). Click/right-click a marker -> popup with
  Track / Stop tracking, Share in chat, Remove (own waypoints). Works for dungeon and waystone markers too.
- Share: sends `[waypoint: Name x y z]` as an ordinary chat message. Every client with the mod listens to chat/game
  messages for that pattern and prints a local line with clickable `[Add]` / `[Track]` (run client commands).
  The coordinates are taken as being in the receiver's current dimension.

## Showing them
- Maps: own waypoints (and a server target) are `MapMarkers` markers (`skyrpg_utils:waypoint/<i>`, `skyrpg_utils:target`).
- Minimap: markers in colour; `WaypointDraw.edgeArrows` draws a coloured arrow on the rim for anything outside the
  view (waypoints inside the distance setting, plus the tracked target, drawn larger).
- World: `WaypointHud` is a Fabric HUD element. It builds the view basis from `Camera` (forward, rotation, FOV) and
  projects by hand. Diamond + "Name · 142m", alpha ramps from 0.15 (2 blocks) to 1 (16 blocks). Off-screen (or behind you)
  it becomes an arrow on the screen edge. The tracked target always shows, with a white ring; others obey the settings.
- Ribbon: `WaypointRibbon` is a normal HUD widget (movable in the HUD editor), top centre under the look-at box.
  1 px per degree over +-88 degrees, N/E/S/W, diamond, arrow at the end when more than ~90 degrees away, name, distance,
  above/below.
- Light beam: not done. Fabric's rendering module in 26.3 has no world-render event that is simple to use, and the HUD
  projection covers the use case. Left out rather than faked.

## Settings (Map -> Waypoints)
Waypoints in the world, Waypoints on the minimap, Waypoint distance (100-5000, top = unlimited), Waypoint marker size.

## Server API
`com.skyrpg.core.net.WaypointTarget` (core, so any server mod can use it):
```java
WaypointTarget.send(player, "Buried treasure", pos, 0xFFF2B33D);              // current dimension
WaypointTarget.send(player, "Boss", pos, 0xFFFF6464, Identifier.parse("minecraft:the_nether")); // given dimension
WaypointTarget.clear(player);
```
Client code: `Waypoints.add(name,x,y,z,color)`, `track(name,x,y,z,color)`, `track(name)`, `untrack()`, `tracked()`.
