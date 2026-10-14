---
title: Settings
category: interface
order: 5
icon: minecraft:comparator
summary: The Settings page: every category, every option and its default, and how to search and reset.
---
The **Settings** page in the K menu holds every option the pack adds, in one place. Options are grouped into categories with a search box on top, and changes take effect straight away. Settings are saved on your own computer, in `config/skyrpg/client.json` in your game folder, so they belong to you, not the server.

Open it with **K**, then **Settings**. It is the last page in the sidebar.

## Using the page

- **Categories** are listed down the left, each with an icon. Click one to see its options on the right.
- **Search.** Type in the search box at the top of the left side, and the page searches every category at once. It matches names, descriptions and keywords, so "ding" finds the mention sound.
- **Toggles** switch on click. **Choices** step through their options. **Sliders** can be dragged. Buttons, such as Edit HUD layout, run an action.
- **Reset to defaults** is a button above a category's list. It appears once you have changed something in that category, asks for a second click within 3 seconds, then resets the category.
- Some options have sections, such as Look or Mentions in Chat, to keep long categories readable.

### Keyboard

| Key | What it does |
| --- | --- |
| **Tab** and **Shift+Tab** | Move between the search box, the categories and the settings |
| Arrow keys | Move inside the pane that has focus |
| **Enter** or **Space** | Change a setting |
| Left and Right arrows | Adjust a choice or a slider |
| **/** or **Ctrl+F** | Jump to the search box |
| **Esc** | Step back: clear the search, leave a pane, then close the menu |

## Categories

The page shows **Interface**, **HUD** and **Gameplay** first. The other categories follow: Chat, Map, Info and Effects.

### Interface

| Setting | Default | What it does |
| --- | --- | --- |
| Menu size | Auto | How big the K menu is. Auto fits it to your monitor. Compact and Large are the other choices |
| SkyRPG tab list | On | Players and your profile info when you hold Tab. Off brings back vanilla's list |
| SkyRPG sidebar | On | Draws the scoreboard sidebar in the SkyRPG look |
| SkyRPG theme for all screens | On | Restyles Minecraft's own menus, buttons and inventories to match SkyRPG |
| Classic stat symbols | Off | Shows stats with the original heart, star and similar symbols instead of SkyRPG's pixel icons |
| Detailed weapon art | On | Detailed, animated art for swords, pickaxes, spears, bows and shields, vanilla and SkyRPG. Off brings back the plain 16-pixel look |

The theme, symbol and weapon art options swap resource packs, so the game reloads its resources when you change them. It does not restart.

### HUD

| Setting | Default | What it does |
| --- | --- | --- |
| SkyRPG HUD | On | Master switch for the info boxes |
| Edit HUD layout | Button | Opens the HUD editor: drag, resize and hide every box |
| One switch per box | On | Show or hide each box. The boxes are listed on [HUD and minimap](hud-and-minimap) |
| Big moment banners | On | A banner when you level up a skill and at other milestones |
| Mob nameplates | On | The level and health over hostile mobs near you |
| Hide skill XP text | On | The Skill progress box replaces the "+4 Farming" text above the hotbar |
| Replace hearts with health bar | On | Hides vanilla's hearts and armour row. The health bar and the Defense box show them instead |
| Event winner board | On | The podium with confetti when an event ends |

### Gameplay

| Setting | Default | What it does |
| --- | --- | --- |
| Weapon particles | On | Sparks, embers and motes around the strongest weapons and tools in players' hands |

### Chat

Twelve options for the look of chat, mentions, tabs and pictures. They are explained on [Chat](chat).

| Setting | Default |
| --- | --- |
| Themed chat box | On |
| Message stripes | On |
| Timestamps | Off |
| Compact repeats | On |
| Highlight mentions | On |
| Ping volume | 70% |
| Ding on whispers | On |
| Chat tabs | On |
| Show images | On |
| Images from links | Known sites |
| Max image height | 7 lines |
| Show stickers | On |

### Map

| Setting | Default | What it does |
| --- | --- | --- |
| Minimap | On | The small map in the top right |
| Turn the minimap with you | Off | The way you face is up, instead of north |
| Cave view | On | Underground, the minimap shows the floor around you |
| Players on the map | On | Other players' faces on both maps |
| NPCs and waypoints | On | Icons for NPCs, your waypoints and more on both maps |
| World map | Button | Opens the world map |
| Waypoints in the world | On | A diamond with the name and distance where each waypoint is |
| Waypoints on the minimap | On | Their markers, and an arrow on the rim when they are off the map |
| Waypoint distance | 2,000 blocks | How far away a waypoint still shows. 100 to 5,000, the top is Unlimited |
| Waypoint marker size | 10 px | How big the diamond in the world is. 6 to 24 |

The last four are in a **Waypoints** section. See [Waypoints](waypoints).

### Info

| Setting | Default | What it does |
| --- | --- | --- |
| Look-at info | On | What you are looking at, top centre |
| Creatures and players too | On | Mobs, NPCs and players, with their health |
| Coordinates | On | Your position and the way you face |
| Biome | On | The biome you are in |
| Time of day | On | The day number and the time on a 24-hour clock |
| FPS | Off | Frames per second |

### Effects

| Setting | Default | What it does |
| --- | --- | --- |
| Effects quality | High | Particle effects on gear, mobs, pets and abilities. Choose Off, Low or High. Off saves the most frames |

## Settings worth changing

> [!TIP]
> On a slow computer, set **Effects quality** to Low or Off first. It is the biggest gain for the least loss. Then try turning off **Weapon particles**.

> [!TIP]
> If the chat feels busy, turn off **Message stripes** and **Show images**. To stop the game loading pictures from outside sites at all, set **Images from links** to Off.

> [!TIP]
> If your window is small, set **Menu size** to Compact. On a big monitor, Large gives the K menu more room.

> [!NOTE]
> Where a setting says "Known sites", those are imgur, Discord, Reddit, Tenor, Giphy and GitHub.

## See also

- [K menu](k-menu)
- [HUD and minimap](hud-and-minimap)
- [Chat](chat)
- [Waypoints](waypoints)
