# Welcome (`welcome`, mod id `skyrpg_welcome`)

The SkyRPG title screen and the first-time setup wizard. Client only (`environment: client`), ships in the pack. Depends on `skyrpg_core` (theme, drawing, settings) and nothing else. Decided 2026-10-14 (design calls are delegated).

## Decisions

- **Title screen replaced by a mixin on `Gui.setScreen`.** When the game opens a screen whose class is exactly vanilla's `TitleScreen`, or opens `null` with no level loaded (which vanilla turns into the title screen), `SkyTitleScreen` opens instead. Controlled by the setting `welcome.title_screen` ("SkyRPG title screen", Interface, default on). It extends `Screen`, not `TitleScreen`, so none of vanilla's realms or friends code runs.
- **Icons are not items.** Item stacks cannot be made before a world is joined (their components are not bound yet; it throws "Components not bound yet"), so the title and the wizard use text, colour stripes and drawn shapes only.
- **Own widgets.** `SkyButton` (primary, normal, ghost, preset card) and `SkyRow` (switch, cycler, slider, key cap) extend `AbstractWidget`, so Tab and arrow navigation, Enter, focus rings and narration work as in vanilla. Positions are in GUI units, laid out from the window size (a block of logo, Play, a two-column grid, Quit; the News box only from 540 GUI units wide).
- **Look.** Panorama under a navy gradient, a vignette and 46 drifting stars (`Backdrop`), the Theme colours, glow on hover, a sheen on the Play button, a gold rule under the wordmark with a travelling light. Steps slide 16 px and fade in (240 ms).
- **Play.** `config/skyrpg/welcome.json` (`WelcomeConfig`): `serverName` "SkyRPG", `serverAddress` default `26.194.174.124` (a Radmin VPN address), `newsUrl`, `wikiUrl`, `setupVersion`. Empty address opens Multiplayer. `ConnectScreen.startConnecting` joins. A `connect.failed` disconnect screen within 90 s of pressing Play gets a hint box ("Can't reach the SkyRPG server. Are you connected to the group's VPN (Radmin)?") with Try again and Open Multiplayer (Fabric `ScreenEvents.afterExtract` plus `allowMouseClick` on the disconnect screen).
- **What's new.** `NewsFeed` fetches `pack/news.json` once per session in the background and shows nothing offline. Format: `{"latest": "<pack version>", "items": [{"date": "2026-10-14", "title": "...", "text": "..."}]}`, newest first, 3 shown. `latest` different from the installed pack version shows "update available". The pack version comes from a `pack.toml` next to the game folder, else the mod version.
- **First run is mandatory.** Until `setupVersion` in `welcome.json` reaches `SETUP_VERSION` (1), the first title screen is the wizard, opened from `replaceTitle`. In that mode there is no Skip or Close, Esc is ignored, and only **Finish** on the last step leaves it (Back between steps is fine). The Performance step applies the suggested preset on first entry so pressing Next all the way through still tunes the game. Opened later (title button, K > Settings > "Show the setup again", `/welcome`) it can be closed any time.
- **Presets** (`Preset`): see the table on the wiki page. Each applies `GraphicsPreset` first, then the individual vanilla options (the graphics mode then reads Custom, which is how vanilla treats edited options), the inactivity FPS limit (AFK for Potato and Balanced, else Minimized), and `ModCompat.applyPerformance`. The suggestion uses RAM, processor threads and the GPU name from `RenderSystem.getDevice().getDeviceInfo()`.
- **Mods are optional and reflective** (`ModCompat`): Iris (`IrisApi.getConfig().setShadersEnabledAndApply`, `Iris.getIrisConfig().setShaderPackName`; falls back to editing `iris.properties`), Dynamic FPS (`enabled: true` in `dynamic_fps.json`), LambDynamicLights (`mode` line in `lambdynlights.toml`), Sodium (best effort, in-memory options by reflection, skipped quietly if the fields differ), Mod Menu (`ModsScreen`), Zoomify (its key is found by name), the wiki (`SkyRpgMenu.open("wiki")`, else the website). Nothing is a compile or runtime dependency.
- **Resource packs** use the vanilla `PackRepository`: only packs from the `resourcepacks` folder (`file/...`) are listed, toggled with `addPack`/`removePack`, and committed once with `Options.updateResourcePacks` (which reloads) when the Visuals step is left or the wizard closes.
- **Keys** are rebound with `KeyMapping.setKey`, `KeyMapping.resetMapping()` and `Options.save()`. Conflicts (other than the debug keys) are shown in red.
- **Settings stay in sync:** the Interface step builds its rows from `SkyRpgSettings.find(id)`, so it is the same state as K > Settings; ids that no mod registered are skipped.

## Dev capture

`SKYRPG_WELCOME_CAPTURE=news ./gradlew :dev:runClient -Pcam` (set `SKYRPG_WELCOME_SCALES=2,3`) screenshots the title screen and every wizard step to `run/client-cam/screenshots/welcome-*.png`. With `act` in the value it also drives the wizard (presets, shaders, a failed Play) and logs `[welcome-test]` lines; with `act-join` it also joins `localhost:25566` (staging). `DevCapture` does nothing unless the variable is set.

## Not done

- A real GUI test of a mouse drag on the sliders and of keyboard rebinding was not automated; they use the same code paths as the screenshots.
- Sodium's own quality options are not set (its option names change between versions).
