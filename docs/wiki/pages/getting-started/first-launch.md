---
title: First launch and setup
category: getting-started
order: 2
icon: minecraft:nether_star
summary: The SkyRPG title screen and the first-time setup: performance presets, shaders, resource packs, keys and the main settings.
---
The first time you start the pack, a short **setup** opens before the main menu. It takes about a minute and tunes the game for your computer. You have to go through every step: on the very first start there is no skip button and **Esc** does nothing, until you press **Finish** on the last step. Every step already has a sensible choice picked, so it is just **Next**, **Next**, and so on. After that you get the SkyRPG **title screen**, with a big Play button. Both can be opened again whenever you like, and then the setup can be closed at any time.

## The title screen

The title screen replaces Minecraft's. It shows the world's panorama under a navy tint with slow drifting stars, the SkyRPG name, and these buttons:

| Button | What it does |
| --- | --- |
| **Play SkyRPG** | Joins the SkyRPG server straight away. If no server address is set, it opens Multiplayer |
| **Singleplayer** and **Multiplayer** | The normal Minecraft screens |
| **Settings** | Minecraft's own options (video, sound, controls) |
| **SkyRPG Setup** | Opens the first-time setup again |
| **Wiki** | Opens this wiki inside the game. If the wiki mod is missing it opens the website instead |
| **Mods** | Mod Menu, when it is installed |
| **Quit Game** | Closes the game |

The footer shows the pack version and the Minecraft version. On wider windows a **What's new** box lists the latest changes. It is fetched from the website when you start the game, and simply does not appear when you are offline.

It works from a small window to a 4K monitor at any GUI scale. You can press **Tab** and the arrow keys to move between buttons and **Enter** to press one, and screen readers read every button.

### Joining with Play

**Play SkyRPG** needs the group's **Radmin VPN**. The server's address starts with 26 and only works while you are connected to the VPN network. If you are not, the game shows "Can't reach the SkyRPG server. Are you connected to the group's VPN (Radmin)?" with **Try again** and **Open Multiplayer** buttons.

The address lives in `config/skyrpg/welcome.json` in your game folder. Edit `serverAddress` to change where Play joins (for example `localhost` if you host the server yourself). Leave it empty and Play opens the Multiplayer screen.

If you prefer the plain Minecraft title screen, turn off **SkyRPG title screen** in K > Settings > Interface.

## The setup

The setup has six steps. Use **Next** and **Back** to move between them. Every choice is applied straight away and saved. When you open it again later you can also close it with **Close** or **Esc** at any time.

1. **Welcome.** What SkyRPG is, in two lines.
2. **Performance.** Pick a preset. The one that suits your computer (from its memory, processor threads and graphics card) is named under the cards, and on the first start it is already applied, so you can just continue. Hover a card to see exactly what it sets.
3. **Visuals.** Switch **shaders** on and choose a shader pack (needs the Iris mod and packs in the `shaderpacks` folder), and turn **resource packs** from the `resourcepacks` folder on or off. Scroll the lists with the mouse wheel. Resource packs reload once when you leave the step.
4. **Controls.** The keys that matter: the SkyRPG keys (K menu, map, backpack, wiki lookup, spell wheel), zoom when Zoomify is installed, and movement. Click a key, then press the new one. **Esc** cancels and **Backspace** clears. A key shown in red is used by something else too. **Open all key bindings** opens Minecraft's full Controls screen.
5. **Interface.** The settings that matter most: themed chat, message spacing, the HUD, the minimap, the tab list and the SkyRPG title screen. They are the same settings as K > Settings, so they stay in sync.
6. **Done.** A summary of what you changed and a **Play SkyRPG** button.

### The presets

| Preset | For | View distance | Simulation | Graphics | Particles | Clouds | Entity distance | FPS cap | VSync |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| **Potato** | Old laptops, integrated graphics | 6 chunks | 5 | Fast | Minimal | Off | 50% | 60 | On |
| **Balanced** | Most computers | 10 | 8 | Fancy | Decreased | Fast | 75% | 120 | On |
| **High** | Gaming PCs | 16 | 12 | Fancy | All | Fancy | 100% | 240 | Off |
| **Ultra** | Strong graphics cards | 24 | 16 | Fabulous | All | Fancy | 150% | Unlimited | Off |

Minecraft then shows the graphics mode as Custom, because the preset sets more than the Fast, Fancy and Fabulous buttons. Every preset also sets the biome blend (0, 2, 4 and 6), turns off ambient occlusion on Potato, turns off entity shadows on Potato, and lowers the frame rate when the game is in the background (Potato and Balanced also do that when you are away from the keyboard).

If these mods are installed, the setup also uses them:

- **Dynamic FPS** is switched on.
- **Dynamic lights** (LambDynamicLights) go to off on Potato, fast on Balanced and fancy above.
- **Sodium** and **Iris** are not changed beyond what the vanilla options already do (Sodium follows the view distance and the other options). Iris is driven for the shader choice.
- Without Iris the shader list is hidden.
- **Zoomify** adds its zoom key to the Controls step.
- **Mod Menu** adds the Mods button to the title screen.

Nothing needs them: without any of these mods the setup only changes Minecraft's own options.

## Opening the setup again

- On the title screen, press **SkyRPG Setup**.
- In the game, press **K**, open **Settings**, and use **Show the setup again** in the Interface category.
- Type `/welcome` in the chat.

The setup only opens by itself once. It is remembered in `config/skyrpg/welcome.json`, in the `setupVersion` entry. Delete that line to see it again at the next start.
