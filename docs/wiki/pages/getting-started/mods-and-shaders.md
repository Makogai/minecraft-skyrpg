---
title: Mods and shaders
category: getting-started
order: 5
icon: minecraft:spyglass
summary: The extra mods the pack installs for speed and comfort, the zoom key, how to turn shaders on, and performance tips.
---
Besides the SkyRPG mods, the pack installs a set of well-known Fabric mods. They make the game faster, add a few comfort features and let you use shader packs. All of them are client-side: they change how your own game looks and runs, nothing about the server. The pack keeps them up to date the same way it keeps SkyRPG up to date.

SkyRPG was tested together with every mod on this page (chat, K menu, HUD and minimap, tab list, effects, 3D item models, the Ender Wyrm and the griffins, the wiki), also with a shader pack switched on.

## Faster game

| Mod | What it does for you |
| --- | --- |
| **Sodium** | A much faster renderer. More frames per second and smoother chunk loading. |
| **Sodium Extra** and **Reese's Sodium Options** | More video settings (clouds, animations, fog, FPS counter) in one tidy screen. Find it under Options, Video Settings. |
| **Lithium** | Faster game logic, which helps the built-in server and big crowds of mobs. |
| **FerriteCore** | Uses less memory. |
| **Entity Culling** and **More Culling** | Skip drawing mobs and blocks you cannot see. A big help in the busy hub. |
| **ImmediatelyFast** | Faster drawing of the HUD, menus and text. |
| **BadOptimizations** | Many small speedups. |
| **Dynamic FPS** | Lowers the frame rate while the window is in the background, so the game stops heating your computer when you tab away. |

## Comfort

| Mod | What it does for you |
| --- | --- |
| **Zoomify** | Hold **C** to zoom in like a spyglass. Scroll while holding C to zoom further or less. |
| **Mod Menu** | A list of the installed mods, with a settings button for those that have one. Open it with the **Mods** button on the title screen, or the small icon in the game menu. |
| **AppleSkin** | Shows how much hunger and saturation food gives, in its tooltip and on the hunger bar. |
| **Mouse Tweaks** | Faster inventory handling. Drag with the left or right button to spread or collect items, scroll over a slot to move items, and hold the button to move whole stacks. |
| **Controlling** | A search box in the Controls screen, so you can find a key quickly. |
| **Shulker Box Tooltip** | Hover a shulker box to see what is inside. |
| **LambDynamicLights** | A torch or lava bucket in your hand, and lit mobs and dropped items, light up their surroundings. |
| **3D Skin Layers** | Your skin's hat and jacket layers get real depth. |
| **Continuity** | Connected glass and other connected textures, for resource packs that use them. |
| **Iris** | Shader pack support. See below. |

The pack also installs the libraries these mods need (Fabric API, Fabric Language Kotlin, YetAnotherConfigLib, Cloth Config, Placeholder API and Searchables). You never use them directly.

## Zoom

Hold **C**. The view zooms in smoothly, scrolling changes how far, and letting go zooms back out. Zoomify has its own settings in Mod Menu (change the zoom time, switch between hold and toggle, make zoom move the mouse more slowly). Change the key under Options, Controls, **Zoomify**. A second key, **F6**, gives a stronger zoom that also hides the HUD.

Zoom works with a spyglass in your hand too. If you want zoom on the mouse wheel only, set the zoom key in Controls to **Unknown**.

## Shaders

Shaders add realistic light, shadows, water and clouds. They look beautiful and they cost frames, so **they are off by default**. The first-time setup (see [First launch and setup](first-launch)) can switch them on for you. Three shader packs come with the pack:

| Shader pack | Feel | Cost |
| --- | --- | --- |
| **Complementary Reimagined** | Natural and close to vanilla, with good shadows, water and sky. The one we recommend. | Medium |
| **MakeUp Ultra Fast** | Soft light and nicer water for weaker computers. | Light |
| **BSL** | Bright, colourful and dramatic. | Heavy |

To turn them on in the game:

1. Press **I** (Iris: shader pack selection), or open Options, Video Settings, **Shader Packs**.
2. Click a shader pack in the list and press **Apply**.
3. Press **U** at any time to switch shaders off and on again without leaving the game. **R** reloads them.

Notes:

- On the first start the shader takes a few seconds to compile. The game can freeze for a moment.
- The Shader Packs screen has **Shader Pack Settings** for each pack. Lowering the shadow distance and quality is the biggest saving.
- Our effects (spell and boss particles, the screen flash and shake) still show with shaders on, and the HUD, the K menu and the minimap are not touched.
- Shaders ask a lot of your graphics card. If the frame rate drops, choose MakeUp Ultra Fast or switch them off.
- More shader packs: drop a `.zip` into the `shaderpacks` folder of your game folder (Prism: Folder, Shader Packs) and pick it in the list.

> [!NOTE]
> Iris binds "toggle shaders" to **K** by default, which is the SkyRPG menu key. SkyRPG moves Iris's key to **U** the first time you start the game with both, so the menu and shaders do not fight. You can change it back in Controls.

## Resource packs

Two small optional packs sit in your `resourcepacks` folder. They are **not** turned on until you choose them in Options, Resource Packs (or in the first-time setup):

| Resource pack | What it does |
| --- | --- |
| **Low On Fire** | The on-fire overlay sits low on the screen instead of covering it. |
| **Fast Better Grass** | Grass blocks show their top texture on the sides, so grass looks fuller. |

SkyRPG's own look (menus, icons, weapon art) comes from its mods and still works with these packs.

## Performance tips

- Use the **Balanced** preset in the setup. Open it again with `/welcome` or K, Settings.
- Lower **Render Distance** first. It is the biggest saving. 10 to 12 chunks is plenty.
- Switch **Graphics** to Fast and set **Clouds** to Off or Fast on a weak computer.
- Give the game memory: 4 GB is the default of the pack and enough for most people. 6 GB helps with shaders. In Prism: Edit, Settings, Java, Memory.
- Keep **VSync** on if you see tearing, off if you want the most frames.
- Dynamic FPS only helps while the window is not in front. Alt-tab away when you are idle.
- If shaders stutter, lower the shader's shadow distance and use Complementary Reimagined or MakeUp Ultra Fast.
- Fancy extras you can turn off in Video Settings (Sodium Extra): animations and particles, and the LambDynamicLights settings.

## Where the settings live

All of these are files in the `config` folder of your game folder, and each mod also has a screen in Mod Menu or in the Video Settings:

| What | File |
| --- | --- |
| Sodium | `config/sodium-options.json` |
| Sodium Extra | `config/sodium-extra-options.json` |
| Iris and the chosen shader pack | `config/iris.properties` (`enableShaders`, `shaderPack`) |
| Zoomify | `config/zoomify.json` |
| Dynamic FPS | `config/dynamic_fps.json` |
| Mod Menu | `config/modmenu.json` |
| AppleSkin | `config/appleskin.json5` |
| Mouse Tweaks | `config/MouseTweaks.cfg` |
| LambDynamicLights | `config/lambdynlights.toml` |
| Entity Culling | `config/entityculling.json` |

The pack only puts its default settings in place the first time. After that your own changes are never overwritten by an update.

## See also

- [First launch and setup](first-launch)
- [Controls and keys](controls-and-keys)
- [Settings](settings)
