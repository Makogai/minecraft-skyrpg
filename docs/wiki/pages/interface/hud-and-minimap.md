---
title: HUD and minimap
category: interface
order: 3
icon: minecraft:filled_map
summary: Every HUD box, the HUD editor, the minimap, the world map and the look-at and info boxes.
---
The SkyRPG HUD is a set of small boxes drawn over the game: your purse, your skill progress, a health bar, the minimap and more. Every box can be moved, resized or hidden, and each shows only when it has something to say. This page lists them all, explains the HUD editor, and covers the minimap, the world map and the info boxes from the Utilities mod.

## The HUD boxes

| Box | Default place | What it shows |
| --- | --- | --- |
| Purse | Top left | The coin icon and your purse |
| Skill progress | Top left | Appears for about 8 seconds after you gain skill XP: the skill, the XP gained, your progress to the next level and the XP rate |
| Health bar | Bottom centre | Your health, in SkyRPG's units |
| Mana bar | Bottom centre | Your mana |
| Defense | Bottom centre | Your Defense |
| Minimap | Top right | The area around you. See below |
| Info (coordinates, biome, time) | Top right, under the minimap | Position, biome, day and time. See below |
| Look-at info | Top centre | What you are looking at. See below |
| Waypoint compass | Top centre | A compass ribbon pointing to your tracked waypoint. Only shows while you track one. See [Waypoints](waypoints) |
| Events | Top right | Running events with a countdown bar and the top five. Only shows during an event. See [Events](events) |
| Sidebar | Middle right | The scoreboard sidebar, including boards from events and admins, in SkyRPG's look |
| Contracts | Middle left | Progress of today's unfinished contracts. See [Contracts](contracts) |
| Quest Tracker | Middle left | The quest you track in the Journal. See [Quests and the Journal](quests-and-journal) |
| Slayer Quest | Top left | Your running slayer quest and its timer. See [Slayers](slayers) |
| Fishing Bait | Bottom centre, above the hotbar | While you hold a fishing rod: the bait that would be used with how many are left (or "No bait"), and the hotspot your bobber is in. See [Fishing](fishing) |
| Party | Middle left | A row for each other party member: face, name, health bar, a dimension tag, and Offline when they are logged off. Only shows while you are in a party. See [Party](party) |
| Flight stamina | Bottom centre | A bar while you ride a griffin. See [Mounts](mounts) |
| Blood Moon | Top centre | A timer while a Blood Moon is up. See [Blood Moon](blood-moon) |
| Brainrot catches | Top left | A card for a few seconds when anyone on the server catches a brainrot: the catcher and the brainrot turning in 3D. See [Brainrots](brainrots#everyone-sees-your-catch) |
| Combat tag | Top centre, under the other boxes | A red "In combat" box with the seconds left (it blinks in the last 3), only while you are in combat. See [Combat basics](combat-basics#combat-tag) |

All of them are on by default. Boxes with nothing to show stay hidden even when switched on. The editor always shows them so you can place them.

### Other HUD parts

- **Banners** slide in at the top of the screen for big moments, such as a skill level up. They queue up.
- **Mob nameplates** show the level and health of hostile mobs near you.
- **The winner board** shows a podium with confetti when an event ends.
- The vanilla hearts and armour row are hidden while the health bar is on screen. Switch it off to bring them back.
- The XP text that normally appears above the hotbar ("+4 Farming") is replaced by the Skill progress box.

All of these have switches in [Settings](settings) under **HUD**.

## The HUD editor

Open **K, Settings, HUD, Edit HUD layout**.

| Input | What it does |
| --- | --- |
| Drag a box | Move it |
| Scroll over a box | Resize it, from half size to double size, in steps of 0.1 |
| Right-click a box | Hide it, or show it again |
| **R** with the mouse over a box | Reset that box |
| **R** with the mouse over nothing | Reset every box |
| **Esc** | Finish. Your layout is saved. |

Boxes pin to the nearest third of the screen, so your layout keeps its shape when you resize the window. Each box also has its own on and off switch in Settings under HUD.

## The minimap

The minimap is a small map of your surroundings in the top right.

- It shows the blocks around you, in the colour of their own textures, so paths, roofs and flowers are visible.
- Other players are shown as their faces and you are an arrow.
- NPCs and your waypoints appear as icons.
- **Zoom** with **=** and **-**. There are five levels, from a quarter of a pixel per block to four pixels per block. The zoom is remembered for each dimension. In the hub it starts zoomed in.
- By default north is up. **Turn the minimap with you** makes the way you face point up.
- **Cave view**: when you are underground, and in the Nether, the minimap shows the floor around you instead of the surface, and gets darker the deeper you go.
- Waypoints outside the view show as an arrow on the rim.

The minimap can be switched off in Settings under Map.

## The world map

Press **M** to open the map of everything explored in this dimension, by you and by everyone else on the server.

| Input | What it does |
| --- | --- |
| Drag | Move the map |
| Scroll | Zoom towards the mouse |
| **C** | Follow yourself |
| Hover an icon | See what it is |
| Right-click the map | Drop a waypoint |
| Click or right-click an icon | Track, share in chat, or remove it if it is your waypoint |

Zoomed in, names show under the icons. Every NPC is on the map with their face, name and role, even when they are too far away to load. Dungeons, waystones and danger regions show up as you discover them. Walking up to a dungeon entrance adds it to your map.

**The map is shared.** What you explore goes up to the server a few times a second, and the parts of the map on your screen are refreshed from the server every 30 seconds, so land another player has explored fills in on your minimap and world map. Each dimension has its own shared map, and the server keeps it in the world folder. Your own copy is also saved on your computer in the `skyrpg/maps/` folder of your game folder, for each server and dimension, so the map still works offline in single player. See [Waypoints](waypoints).

## Look-at info

A box at the top centre shows what you are looking at, up to 12 blocks away.

- **Blocks:** the name, which mod it comes from, how grown a crop is, whether a mining node has regrown, and whether your tool can harvest it.
- **Mobs and players:** their health, and a player's ping.
- **NPCs:** their role.

## Info box

Under the minimap. It shows your coordinates and the way you are facing, the biome, the day and the time on a 24-hour clock, and optionally the frames per second. Each line has its own switch in Settings under Info, and **FPS** is off by default.

## The Tab list

Hold **Tab** for the SkyRPG player list: heads and ping on the left, info panels on the right, such as running events. Every player's **SkyRPG Level badge** sits before their name, in the same tier colour as in chat, for everyone online, near or far. Names themselves are plain white. Switch the list back to vanilla in Settings under Interface.

> [!TIP]
> If the HUD feels crowded, hide boxes you do not use in the editor rather than turning the whole HUD off. Everything you hide can be brought back there.

> [!NOTE]
> The whole HUD can be turned off with **SkyRPG HUD** in Settings under HUD.

## See also

- [Settings](settings)
- [Waypoints](waypoints)
- [K menu](k-menu)
- [Controls and keys](controls-and-keys)
