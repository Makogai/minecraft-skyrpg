# The auto-updating pack

Players no longer download zips from Discord. They import one Prism Launcher instance once; every launch it runs
packwiz-installer, which syncs the mods with the pack published on GitHub Pages. Decided 2026-10-14.

## Flow

1. `python tools/publish_pack.py` builds the client mod jars (same list as `tools/package_friend.py`, `MODS`), writes
   `dist/pack/` and builds `dist/SkyRPG-Prism-Instance.zip`.
2. It copies `dist/pack/` to `pack/` in the `.gh-pages` worktree and commits "Pack <version>" there (`--no-commit` to skip,
   `--no-build` to reuse built jars, `--no-instance` to skip the zip). It never pushes.
3. The owner pushes: `git push origin main gh-pages`. GitHub Pages serves https://makogai.github.io/minecraft-skyrpg/pack/pack.toml.
4. On launch the instance's pre-launch command downloads the new index, fetches every file whose hash changed and deletes
   files that left the pack (packwiz-installer keeps its own list in `packwiz.json`). Offline, the launch continues with what
   is installed.

`tools/wiki/publish_pages.py` leaves the top-level `pack/` folder alone and `publish_pack.py` only touches `pack/`, so the
wiki and the pack publish independently.

## Format (packwiz 1.1.0)

- `pack.toml`: name "SkyRPG", `version` = date + git short hash, `[index]` (file, `hash-format = "sha256"`, hash of
  index.toml) and `[versions]` (minecraft, fabric loader from `gradle.properties`). In Prism, packwiz-installer also reads the
  instance's `mmc-pack.json` and moves the Minecraft and Fabric versions to match, so a version bump needs no new instance.
- `index.toml`: one `[[files]]` per file with a forward-slash path relative to pack.toml and its sha256. Files ending in
  `.pw.toml` carry `metafile = true`.
- `mods/skyrpg-*.jar`: our jars as plain files, downloaded from the pack's own URL.
- `mods/<slug>.pw.toml`: Modrinth mods. Holds name, filename, `side`, `[download]` (Modrinth CDN url, sha512) and
  `[update.modrinth]`. The side comes from the Modrinth project (client/both) unless set in the config.

## The Prism instance

A folder `SkyRPG/` in the zip with `instance.cfg` (name, 4 GB max memory, `OverrideCommands=true` and
`PreLaunchCommand="\"$INST_JAVA\" -jar packwiz-installer-bootstrap.jar <pack url>"`, the whole value quoted with its inner quotes escaped, since instance.cfg is a Qt INI file; unwrapped, Prism glues the Java path to `-jar` and the command fails), `mmc-pack.json` (components Minecraft,
Intermediary Mappings and Fabric Loader at the versions in gradle.properties) and `minecraft/packwiz-installer-bootstrap.jar`
(cached in `dist/cache/`). The command runs in the instance's `minecraft` folder, with the Java Prism picked for the game.
The zip contains no account or personal data. Rebuild and resend it only if the pack URL, memory or components change.

## Adding a Modrinth mod, shader pack or resource pack

`tools/pack/modrinth.json` has three lists: `mods`, `shaderpacks`, `resourcepacks`. Add `{ "slug": "sodium" }` (slug = the part
after modrinth.com/mod/) and publish. Optional per entry: `side` ("client", "server" or "both"; shader and resource packs are
always "client"), `version` (a Modrinth `version_number`), `filename` (a stable file name: shader packs use one so that
Iris's `shaderPack=` in `config/iris.properties` keeps pointing at the file after an update) and `why` (a note, ignored).
Shader packs are looked up with the Modrinth loader "iris", resource packs with "minecraft", mods with "fabric".

- **Dependencies** of a mod (its Modrinth `required` ones) are added automatically as `mods/<slug>.pw.toml`, at the version the
  mod names if it names one. `skip_dependencies` lists projects to leave out (kotlin-lang-forge: Forge-only).
- **Modrinth's `game_versions` tag is not trusted.** Several mods tag old builds as 26.3. For every candidate version the script
  downloads the jar (cached in `dist/cache/jars/`), reads `fabric.mod.json` (and the jars nested in it) and rejects the build
  unless `depends.minecraft` accepts the pack's Minecraft version (`tools/pack_jarcheck.py`); it then tries the next older
  build, and stops with an error if none passes. After resolving everything it also checks each mod's `depends` on the other
  pack mods against the versions in the pack (Reese's Sodium Options needs exactly Sodium 0.9.2, so Sodium is pinned with
  `version` in the config).
- **Default config files.** Everything under `tools/pack/defaults/` is copied into the pack at the same path (`config/...`)
  and its index entry gets `preserve = true`. packwiz-installer installs a `preserve` file only when it is not on the player's
  disk yet, so the player's own changes (and the changes the SkyRPG setup screen makes) are never overwritten.
  `config/`, `options.txt` and `servers.dat` are the preserved places (`PRESERVED_DIRS` in publish_pack.py).

## The extra mods (2026-10-14)

Performance: sodium 0.9.2, lithium, ferrite-core, moreculling, immediatelyfast, dynamic-fps, sodium-extra,
reeses-sodium-options, entityculling, badoptimizations. Shaders: iris. Comfort: zoomify, modmenu, appleskin, mouse-tweaks,
controlling, shulkerboxtooltip, lambdynamiclights, 3dskinlayers, continuity. Libraries (automatic): yacl,
fabric-language-kotlin, cloth-config, placeholder-api, searchables. Not shipped: krypton (server networking, no gain for a
client), fresh-animations/emf/etf, indium, noisium (no 26.3 build). Shader packs: Complementary Reimagined (recommended),
MakeUp Ultra Fast (light), BSL (heavy), all off until chosen. Resource packs (optional, not enabled): Low On Fire, Fast Better
Grass. Why each is there: the `why` fields in modrinth.json and the wiki page `mods-and-shaders`.

### Default configs shipped (`tools/pack/defaults/config/`)

| File | Decision |
| --- | --- |
| `iris.properties` | `enableShaders=false`, `shaderPack=` (empty). Iris's own default is shaders on. `disableUpdateMessage=true` |
| `modmenu.json` | `modify_title_screen=false` (the SkyRPG title screen draws its own Mods button), update checker off, libraries hidden |
| `appleskin.json5` | `showFoodHealthHudOverlay=false`: SkyRPG's HUD has its own health bar. Hunger/saturation tooltip and bar overlays stay |

Everything else uses each mod's own defaults (Sodium, Sodium Extra, Dynamic FPS, Zoomify, Mouse Tweaks and the rest are
already balanced: Dynamic FPS lowers the frame rate in the background, Zoomify's key is C, hold). The welcome module's presets
edit these files through reflection/JSON (`config/dynamic_fps.json` `enabled`, Sodium's quality options, Iris through its API).

### Keys

Zoomify C (hold), F6 (secondary zoom); Iris I (shader list), U (toggle), R (reload). Iris's toggle defaults to K, the SkyRPG
menu key: `core/.../client/KeyConflicts.java` moves it to U once per install (marker `config/skyrpg/key-conflicts-v1`).

### Dev client

`./gradlew :dev:runClient -Pextras` (usually with `-Pcam`) adds the client mods to the dev client from the Modrinth maven by
version id (never by number: one number can name builds for several Minecraft versions). Not applied to `runServer`.
