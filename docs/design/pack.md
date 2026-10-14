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

## Adding a Modrinth mod

Add `{ "slug": "sodium" }` to `tools/pack/modrinth.json` (slug = the part after modrinth.com/mod/) and publish. The newest
Fabric build for the Minecraft version is used; Fabric API is pinned to `fabric_api_version`. Optional per entry: `side`
("client", "server" or "both") and `version` (a Modrinth `version_number`). Dependencies of a mod must be added by hand.
