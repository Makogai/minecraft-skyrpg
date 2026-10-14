---
title: Waypoints
category: interface
order: 4
icon: minecraft:recovery_compass
summary: Named, coloured points on your maps and in the world: how to make, track and share them.
---
A waypoint is a named, coloured point that you save. It shows on your minimap and world map, and as a marker in the world, so you can find your way home, back to a dungeon or to a friend's base. You can **track** one to get a compass at the top of the screen that points the way. Waypoints are kept on your own computer, per server and per dimension.

## Making a waypoint

### With a command

```
/waypoint add <name> [x y z]
```

`/wp` is a shortcut for `/waypoint`. Without coordinates the waypoint is placed where you stand. Put names with spaces in quotes, such as `/wp add "My Base"`.

If you add a waypoint with a name you already have, it replaces the old one and keeps its colour. New ones take the next colour in a cycle of eight: gold, aqua, purple, green, red, blue, orange and white.

### On the world map

Press **M**, then **right-click** an empty spot on the map. A small dialog asks for a name and one of the eight colours. Click or right-click any existing icon for a menu.

### By dying

When you die, a red waypoint called **Last death** is added where it happened. The next death replaces it.

## Managing waypoints

| Command | What it does |
| --- | --- |
| `/wp add <name> [x y z]` | Save a waypoint |
| `/wp remove <name>` | Delete one |
| `/wp list` | List the waypoints in this dimension, with distance and clickable **Track**, **Share** and **Remove** buttons |
| `/wp track <name>` | Track a waypoint |
| `/wp track <name> <x> <y> <z>` | Track any spot, without saving it |
| `/wp untrack` | Stop tracking |
| `/wp share <name>` | Post it in chat |

On the world map, click an icon to open a popup with **Track**, **Share in chat** and, for your own waypoints, **Remove**. This works for dungeon and waystone icons too.

## Seeing a waypoint

- **On the minimap:** a coloured marker. If a waypoint is off the edge, an arrow on the rim points to it. The tracked one is drawn larger.
- **In the world:** a diamond with the name and distance, such as "Base 142m". It fades in as you get closer, from faint at 2 blocks to solid at 16. When it is off-screen or behind you, an arrow at the screen edge points to it.
- **On the world map:** the same colour as everywhere else, with its name when you zoom in.

## Tracking

You can track **one** thing at a time. The tracked target:

- Always shows in the world, with a white ring round its diamond.
- Gets a **compass ribbon** at the top centre of the screen. It shows N, E, S and W, a diamond for the target, an arrow at the end when it is behind you, the name, the distance, and whether it is above or below you.
- Is cleared when you get within 3 blocks of it, with a message "You've arrived at...", or when you leave the server.
- Only shows in its own dimension.

The Journal's **Track** button uses the same system: it points your compass at the next place a quest needs you to go. See [Quests and the Journal](quests-and-journal).

## Sharing

`/wp share <name>`, or **Share in chat** on the map, posts this in chat:

`[waypoint: Name x y z]`

Players with the mod see clickable **Add** and **Track** buttons next to it. The coordinates are taken to be in the dimension the reader is in, so only share a waypoint from the dimension you are both in. Names are shortened to 32 characters.

## Settings

In **K, Settings, Map**, under Waypoints:

| Setting | Default | What it does |
| --- | --- | --- |
| Waypoints in the world | On | The diamond with the name and distance. The tracked one always shows. |
| Waypoints on the minimap | On | The markers and the edge arrows |
| Waypoint distance | 2,000 blocks | How far away a waypoint still shows in the world and as an arrow. 100 to 5,000. The top setting is Unlimited |
| Waypoint marker size | 10 px | How big the diamond in the world is, 6 to 24 |

**NPCs and waypoints**, under the same category, turns the icons on both maps on or off. See [Settings](settings).

> [!TIP]
> Make a waypoint at your base, at dungeon entrances and at anything rare you find. `/wp list` then works as a quick way back: click **Track** and follow the compass.

## See also

- [HUD and minimap](hud-and-minimap)
- [Settings](settings)
- [Waystones and travel](waystones-and-travel)
- [Controls and keys](controls-and-keys)
