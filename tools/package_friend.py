#!/usr/bin/env python3
"""Packs everything a friend needs to join the SkyRPG server into one folder and one zip.

    python tools/package_friend.py [--server skyrpgsurvival.falixsrv.me]

Builds the SkyRPG mod jars (not the dev-only devtools), adds the matching Fabric API jar from Gradle's
cache, and writes dist/SkyRPG-<version>/mods/*.jar, an install guide and dist/SkyRPG-<version>.zip.
Send the zip again after every change: client and server must run the same SkyRPG code.
"""
import argparse
import os
import shutil
import subprocess
import sys
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
MODS = ["core", "stats", "skills", "items", "economy", "npcs", "events", "hub", "mining", "farming", "foraging", "fishing", "pets", "combat", "enchants", "world", "utils", "vfx", "chat", "progress", "mounts", "party", "magic", "wiki", "welcome", "builder", "brainrots", "crates", "helper"]


def properties():
    values = {}
    for line in (ROOT / "gradle.properties").read_text(encoding="utf-8").splitlines():
        key, sep, value = line.partition("=")
        if sep and not key.startswith("#"):
            values[key.strip()] = value.strip()
    return values


def fabric_api_jar(version):
    cache = Path.home() / ".gradle" / "caches" / "modules-2" / "files-2.1" / "net.fabricmc.fabric-api" / "fabric-api" / version
    jars = [p for p in cache.rglob(f"fabric-api-{version}.jar")]
    if not jars:
        sys.exit(f"Fabric API {version} isn't in Gradle's cache; run ./gradlew build once.")
    return jars[0]


def build_mods():
    """Builds every SkyRPG client mod jar with Gradle (shared with publish_pack.py)."""
    gradlew = ROOT / ("gradlew.bat" if os.name == "nt" else "gradlew")
    tasks = [f":{m}:build" for m in MODS]
    if subprocess.run([str(gradlew), *tasks, "-x", "test", "--console=plain", "-q"], cwd=ROOT).returncode != 0:
        sys.exit("Build failed.")


def mod_jars(version):
    """The built jar of every module in MODS, in order; exits if one is missing."""
    jars = []
    for module in MODS:
        jar = ROOT / module / "build" / "libs" / f"skyrpg-{module}-{version}.jar"
        if not jar.exists():
            sys.exit(f"Missing {jar}")
        jars.append(jar)
    return jars


GUIDE = """SkyRPG {version} for Minecraft {minecraft}
=====================================

You need Minecraft Java Edition {minecraft} with Fabric Loader {loader} (or newer), Fabric API {fabric_api}
(not in this zip during development: keep the one you have, or get it from https://modrinth.com/mod/fabric-api),
and every jar in the "mods" folder next to this file.

Best: the auto-updating pack. Install Prism Launcher (https://prismlauncher.org), then "Add Instance" >
"Import" and pick SkyRPG-Prism-Instance.zip. It brings Fabric API and every SkyRPG mod, and updates itself from
GitHub each time you launch. Nothing below is needed then; this zip is the manual fallback.

Manual: Prism Launcher (https://prismlauncher.org)
  1. Add Instance > Minecraft {minecraft}, Mod loader: Fabric ({loader} or newer) > OK.
  2. Right-click the instance > Edit > Mods > "Add file", pick all jars from "mods". (Or "View
     Folder" and copy them in.)
  3. Launch.

Official launcher instead:
  1. Run the Fabric installer (https://fabricmc.net/use/installer), pick Minecraft {minecraft}.
  2. Copy all jars from "mods" into %APPDATA%\\.minecraft\\mods (make the folder if it's missing).
  3. Start the "fabric-loader-{minecraft}" profile.

Joining: Multiplayer > Add Server > address {server}
(Ask the host for a new zip whenever SkyRPG is updated: old mods can't join a newer server.)
"""


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--server", default="skyrpgsurvival.falixsrv.me", help="address friends type in Multiplayer")
    parser.add_argument("--skip-build", action="store_true")
    # During development friends keep Fabric API themselves; the final modpack will bundle it (and more).
    parser.add_argument("--with-fabric-api", action="store_true", help="also put Fabric API in the zip")
    args = parser.parse_args()
    props = properties()
    version = f"{props['mod_version']}+{props['minecraft_version']}"
    if not args.skip_build:
        build_mods()

    out = ROOT / "dist" / f"SkyRPG-{version}"
    shutil.rmtree(out, ignore_errors=True)
    (out / "mods").mkdir(parents=True)
    for jar in mod_jars(version):
        shutil.copy2(jar, out / "mods" / jar.name)
    if args.with_fabric_api:
        api = fabric_api_jar(props["fabric_api_version"])
        shutil.copy2(api, out / "mods" / api.name)
    (out / "HOW-TO-INSTALL.txt").write_text(GUIDE.format(
        version=version, minecraft=props["minecraft_version"], loader=props["loader_version"],
        fabric_api=props["fabric_api_version"], server=args.server), encoding="utf-8")

    archive = out.parent / f"{out.name}.zip"
    with zipfile.ZipFile(archive, "w", zipfile.ZIP_DEFLATED, compresslevel=9) as zf:
        for path in sorted(out.rglob("*")):
            if path.is_file():
                zf.write(path, path.relative_to(out.parent))
    print(f"Folder: {out}")
    print(f"Zip:    {archive} ({archive.stat().st_size // 1024} KB)")
    for jar in sorted((out / "mods").iterdir()):
        print(f"  mods/{jar.name}")


if __name__ == "__main__":
    main()
