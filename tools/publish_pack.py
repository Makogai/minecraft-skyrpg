#!/usr/bin/env python3
"""Publishes the auto-updating SkyRPG pack (packwiz format) and builds the one-time Prism instance.

    python tools/publish_pack.py [--no-build] [--no-commit] [--no-instance]

1. builds the mod jars (shared logic with package_friend.py) unless --no-build,
2. writes dist/pack/: pack.toml, index.toml, mods/*.jar (ours) and mods/<slug>.pw.toml (Modrinth mods from
   tools/pack/modrinth.json, Fabric API first),
3. builds dist/SkyRPG-Prism-Instance.zip (import once in Prism Launcher; it runs packwiz-installer on every launch),
4. copies dist/pack/ to pack/ in the .gh-pages worktree and commits there ("Pack <version>") unless --no-commit.
Never pushes: `git push origin main gh-pages` is the owner's job. The wiki files on gh-pages are not touched.
A player's Prism instance fetches PACK_URL on each launch, so a push is all it takes to update everybody.
"""
import argparse
import hashlib
import json
import re
import shutil
import subprocess
import sys
import urllib.parse
import urllib.request
import zipfile
from datetime import date
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
sys.path.insert(0, str(Path(__file__).resolve().parent / "wiki"))
import package_friend as pf      # noqa: E402
import pack_jarcheck as jarcheck  # noqa: E402
import publish_pages as pages    # noqa: E402

ROOT = pf.ROOT
DIST = ROOT / "dist"
PACK_DIR = DIST / "pack"
PACK_URL = "https://makogai.github.io/minecraft-skyrpg/pack/pack.toml"
CONFIG = ROOT / "tools" / "pack" / "modrinth.json"
DEFAULTS = ROOT / "tools" / "pack" / "defaults"
PRESERVED_DIRS = {"config", "options.txt", "servers.dat"}    # default files a player may change
BOOTSTRAP_URL = "https://github.com/packwiz/packwiz-installer-bootstrap/releases/latest/download/packwiz-installer-bootstrap.jar"
BOOTSTRAP_CACHE = DIST / "cache" / "packwiz-installer-bootstrap.jar"
INSTANCE_ZIP = DIST / "SkyRPG-Prism-Instance.zip"
INSTANCE_URL = "https://makogai.github.io/minecraft-skyrpg/download/SkyRPG-Prism-Instance.zip"
UA = {"User-Agent": "Makogai/minecraft-skyrpg publish_pack (github.com/Makogai/minecraft-skyrpg)"}


def http_json(url):
    with urllib.request.urlopen(urllib.request.Request(url, headers=UA), timeout=60) as r:
        return json.load(r)


def digest(data, algo):
    return hashlib.new(algo, data).hexdigest()


def toml_str(value):
    return json.dumps(value, ensure_ascii=False)    # a JSON string is a valid TOML basic string


JARS = {}    # slug -> (jar bytes, file name) of every chosen mod, for the cross-mod dependency check


def download_cached(file):
    """The bytes of a Modrinth file, kept in dist/cache/jars by sha512 so reruns don't download again."""
    cache = DIST / "cache" / "jars" / file["hashes"]["sha512"][:32]
    if cache.exists():
        return cache.read_bytes()
    with urllib.request.urlopen(urllib.request.Request(file["url"], headers=UA), timeout=120) as r:
        data = r.read()
    if digest(data, "sha512") != file["hashes"]["sha512"]:
        sys.exit(f"{file['filename']}: downloaded file does not match Modrinth's sha512.")
    cache.parent.mkdir(parents=True, exist_ok=True)
    cache.write_bytes(data)
    return data


def modrinth_versions(slug, props, loader):
    mc = props["minecraft_version"]
    query = urllib.parse.urlencode({"game_versions": json.dumps([mc]), "loaders": json.dumps([loader])})
    return http_json(f"https://api.modrinth.com/v2/project/{slug}/version?{query}")


# Where each kind of Modrinth project goes in the pack, the loader Modrinth files it under and the packwiz side.
KINDS = {
    "mods": ("mods", "fabric"),
    "shaderpacks": ("shaderpacks", "iris"),         # Modrinth lists shader packs with the loader "iris"
    "resourcepacks": ("resourcepacks", "minecraft"),    # and resource packs with "minecraft"
}


def safe_filename(name):
    return re.sub(r"[^A-Za-z0-9._+-]", "_", name)


def resolve_modrinth(entry, props, kind="mods"):
    """Returns (project, version, text of <slug>.pw.toml) for one modrinth.json entry."""
    slug = entry["slug"]
    folder, loader = KINDS[kind]
    project = http_json(f"https://api.modrinth.com/v2/project/{slug}")
    versions = modrinth_versions(slug, props, loader)
    if not versions:
        sys.exit(f"{slug}: no {loader} build for Minecraft {props['minecraft_version']} on Modrinth.")
    wanted = entry.get("version") or (props["fabric_api_version"] if slug == "fabric-api" else None)
    ordered = sorted(versions, key=lambda v: v["date_published"], reverse=True)
    if entry.get("version_id"):    # a dependency that names the exact version it was built against
        ordered = [v for v in ordered if v["id"] == entry["version_id"]] or ordered
    elif wanted:
        ordered = [v for v in ordered if v["version_number"] == wanted] or ordered
        if ordered[0]["version_number"] != wanted:
            print(f"  {slug}: {wanted} not on Modrinth, using the newest")
    chosen = None
    for candidate in ordered:
        if kind != "mods":
            chosen = candidate
            break
        # Modrinth's game_versions tags can be wrong: read the jar's own fabric.mod.json before trusting a build.
        file = next((f for f in candidate["files"] if f.get("primary")), candidate["files"][0])
        data = download_cached(file)
        problems = jarcheck.minecraft_problems(data, props["minecraft_version"], file["filename"])
        if problems:
            print(f"  {slug} {candidate['version_number']}: rejected, {'; '.join(problems)}")
            continue
        JARS[slug] = (data, file["filename"])
        chosen = candidate
        break
    if not chosen:
        sys.exit(f"{slug}: no build on Modrinth really supports Minecraft {props['minecraft_version']} "
                 f"(tagged ones are rejected above). Remove it from tools/pack/modrinth.json.")
    file = next((f for f in chosen["files"] if f.get("primary")), chosen["files"][0])
    side = entry.get("side")
    if not side and kind != "mods":
        side = "client"
    if not side:
        client, server = project["client_side"], project["server_side"]
        side = "client" if server == "unsupported" else "server" if client == "unsupported" else "both"
    # "filename" pins a stable name (shader packs: Iris stores the file name in iris.properties, so a version in it
    # would break the player's selection on every update); otherwise Modrinth's, cleaned of odd characters.
    filename = entry.get("filename") or safe_filename(file["filename"])
    print(f"  {slug} {chosen['version_number']} ({side}) {filename}")
    text = (
        f"name = {toml_str(project['title'])}\n"
        f"filename = {toml_str(filename)}\n"
        f"side = {toml_str(side)}\n\n"
        "[download]\n"
        f"url = {toml_str(file['url'])}\n"
        'hash-format = "sha512"\n'
        f"hash = {toml_str(file['hashes']['sha512'])}\n\n"
        "[update]\n[update.modrinth]\n"
        f"mod-id = {toml_str(chosen['project_id'])}\n"
        f"version = {toml_str(chosen['id'])}\n"
    )
    return project, chosen, text


def write_modrinth_files(config, props):
    """Writes every Modrinth mod, shader pack and resource pack, plus the required dependencies of the mods."""
    skip = set(config.get("skip_dependencies", []))
    have = {e["slug"] for e in config["mods"]}
    queue = []
    for kind in KINDS:
        print(f"Modrinth {kind}:")
        for entry in config.get(kind, []):
            project, version, text = resolve_modrinth(entry, props, kind)
            write(PACK_DIR / KINDS[kind][0] / f"{entry['slug']}.pw.toml", text)
            if kind == "mods":
                queue += [(entry["slug"], d) for d in version["dependencies"] if d["dependency_type"] == "required"]
    print("Required dependencies:")
    while queue:
        parent, link = queue.pop()
        dep = http_json(f"https://api.modrinth.com/v2/project/{link['project_id']}")
        if dep["slug"] in skip or dep["slug"] in have:
            continue
        have.add(dep["slug"])
        print(f"  (needed by {parent})", end="")
        project, version, text = resolve_modrinth({"slug": dep["slug"], "version_id": link.get("version_id")}, props, "mods")
        write(PACK_DIR / "mods" / f"{dep['slug']}.pw.toml", text)
        queue += [(dep["slug"], d) for d in version["dependencies"] if d["dependency_type"] == "required"]
    check_cross_dependencies()


def check_cross_dependencies():
    """Every mod's fabric.mod.json `depends` on another mod in the pack must accept the version that is in the pack."""
    installed = {}
    for data, _ in JARS.values():
        ids, version = jarcheck.ids_and_version(data)
        installed.update({i: version for i in ids if i})
    problems = []
    for slug, (data, name) in JARS.items():
        problems += jarcheck.dependency_problems(data, name, installed)
    if problems:
        sys.exit("Mods in the pack disagree about versions (pin one in tools/pack/modrinth.json):\n  " + "\n  ".join(problems))
    print(f"Checked {len(JARS)} mod jars: every one accepts Minecraft and the other pack mods' versions.")


def write(path, text):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(text, encoding="utf-8", newline="\n")


def copy_defaults():
    """tools/pack/defaults/** goes into the pack at the same path, flagged preserve (see write_pack)."""
    if not DEFAULTS.exists():
        return
    for src in sorted(p for p in DEFAULTS.rglob("*") if p.is_file()):
        dst = PACK_DIR / src.relative_to(DEFAULTS)
        dst.parent.mkdir(parents=True, exist_ok=True)
        shutil.copyfile(src, dst)


def git_short():
    r = subprocess.run(["git", "rev-parse", "--short", "HEAD"], cwd=ROOT, capture_output=True, text=True)
    return r.stdout.strip() or "unknown"


def write_pack(props, version):
    shutil.rmtree(PACK_DIR, ignore_errors=True)
    mods = PACK_DIR / "mods"
    mods.mkdir(parents=True)
    mod_version = f"{props['mod_version']}+{props['minecraft_version']}"
    for jar in pf.mod_jars(mod_version):
        shutil.copy2(jar, mods / jar.name)
    write_modrinth_files(json.loads(CONFIG.read_text(encoding="utf-8")), props)
    copy_defaults()

    lines = ['hash-format = "sha256"', ""]
    for path in sorted(p for p in PACK_DIR.rglob("*") if p.is_file()):
        rel = path.relative_to(PACK_DIR).as_posix()
        lines += ["[[files]]", f"file = {toml_str(rel)}", f'hash = "{digest(path.read_bytes(), "sha256")}"']
        if rel.endswith(".pw.toml"):
            lines.append("metafile = true")
        elif rel.split("/")[0] in PRESERVED_DIRS:
            lines.append("preserve = true")    # installed once; the player's own edits are never overwritten
        lines.append("")
    index = "\n".join(lines).encode("utf-8")
    (PACK_DIR / "index.toml").write_bytes(index)
    (PACK_DIR / "pack.toml").write_text(
        'name = "SkyRPG"\n'
        f'version = "{version}"\n'
        'pack-format = "packwiz:1.1.0"\n\n'
        "[index]\n"
        'file = "index.toml"\n'
        'hash-format = "sha256"\n'
        f'hash = "{digest(index, "sha256")}"\n\n'
        "[versions]\n"
        f'fabric = "{props["loader_version"]}"\n'
        f'minecraft = "{props["minecraft_version"]}"\n', encoding="utf-8", newline="\n")
    # The title screen's "What's new" box (welcome mod): served next to pack.toml, written after the index so the
    # installer never downloads it into game folders. "latest" is this version, so older installs show "update available".
    news_src = ROOT / "tools" / "pack" / "news.json"
    if news_src.exists():
        news = json.loads(news_src.read_text(encoding="utf-8"))
        news["latest"] = version
        (PACK_DIR / "news.json").write_text(json.dumps(news, indent=2, ensure_ascii=False) + "\n", encoding="utf-8", newline="\n")


def build_instance(props):
    """dist/SkyRPG-Prism-Instance.zip: a Prism instance whose pre-launch command runs packwiz-installer."""
    if not BOOTSTRAP_CACHE.exists():
        BOOTSTRAP_CACHE.parent.mkdir(parents=True, exist_ok=True)
        with urllib.request.urlopen(urllib.request.Request(BOOTSTRAP_URL, headers=UA), timeout=120) as r:
            BOOTSTRAP_CACHE.write_bytes(r.read())
    mc, loader = props["minecraft_version"], props["loader_version"]
    cfg = (
        "[General]\n"
        "ConfigVersion=1.2\n"
        "InstanceType=OneSix\n"
        "iconKey=default\n"
        "name=SkyRPG\n"
        "notes=Updates itself from GitHub every time you launch (packwiz).\n"
        "OverrideMemory=true\n"
        "MinMemAlloc=1024\n"
        "MaxMemAlloc=4096\n"
        "OverrideCommands=true\n"
        # Qt INI value: the whole command in quotes, its own quotes escaped. Unwrapped, Prism reads
        # "$INST_JAVA" -jar as one quoted string glued to the rest ("javaw.exe-jar ...") and the command fails.
        f'PreLaunchCommand="\\"$INST_JAVA\\" -jar packwiz-installer-bootstrap.jar {PACK_URL}"\n'
    )
    mmc = {"formatVersion": 1, "components": [
        {"cachedName": "Minecraft", "cachedVersion": mc, "important": True, "uid": "net.minecraft", "version": mc},
        {"cachedName": "Intermediary Mappings", "cachedRequires": [{"equals": mc, "uid": "net.minecraft"}],
         "cachedVersion": mc, "cachedVolatile": True, "dependencyOnly": True,
         "uid": "net.fabricmc.intermediary", "version": mc},
        {"cachedName": "Fabric Loader", "cachedRequires": [{"uid": "net.fabricmc.intermediary"}],
         "cachedVersion": loader, "uid": "net.fabricmc.fabric-loader", "version": loader},
    ]}
    INSTANCE_ZIP.parent.mkdir(exist_ok=True)
    with zipfile.ZipFile(INSTANCE_ZIP, "w", zipfile.ZIP_DEFLATED) as zf:
        zf.writestr("SkyRPG/instance.cfg", cfg)
        zf.writestr("SkyRPG/mmc-pack.json", json.dumps(mmc, indent=4) + "\n")
        zf.write(BOOTSTRAP_CACHE, "SkyRPG/minecraft/packwiz-installer-bootstrap.jar")
    print(f"Prism instance: {INSTANCE_ZIP} ({INSTANCE_ZIP.stat().st_size // 1024} KB)")


def publish_to_worktree(version, commit):
    pages.ensure_worktree()
    target = pages.WORKTREE / "pack"
    shutil.rmtree(target, ignore_errors=True)    # drops files that are no longer in the pack
    shutil.copytree(PACK_DIR, target)
    paths = ["pack"]
    if INSTANCE_ZIP.exists():
        # the one-time install players download: a fixed address, linked from the wiki's Welcome page
        download = pages.WORKTREE / "download"
        download.mkdir(exist_ok=True)
        shutil.copy2(INSTANCE_ZIP, download / INSTANCE_ZIP.name)
        paths.append("download")
    pages.git("add", "--all", *paths, cwd=pages.WORKTREE)
    if pages.git("diff", "--cached", "--quiet", "--", *paths, cwd=pages.WORKTREE, check=False).returncode == 0:
        print("The published pack is already up to date.")
    elif commit:
        pages.git("commit", "-m", f"Pack {version}", "--", *paths, cwd=pages.WORKTREE)
        print(f"Committed the pack to {pages.BRANCH} (not pushed: git push origin main gh-pages).")
    else:
        print(f"Copied to {target} and staged; not committed (--no-commit).")


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--no-build", action="store_true", help="use the jars already built")
    parser.add_argument("--no-commit", action="store_true", help="don't commit to the gh-pages worktree")
    parser.add_argument("--no-instance", action="store_true", help="skip dist/SkyRPG-Prism-Instance.zip")
    args = parser.parse_args()
    props = pf.properties()
    version = f"{date.today():%Y.%m.%d}-{git_short()}"
    if not args.no_build:
        pf.build_mods()
    write_pack(props, version)
    print(f"Pack {version}: {PACK_DIR}")
    if not args.no_instance:
        build_instance(props)
    publish_to_worktree(version, not args.no_commit)
    print(f"Pack URL: {PACK_URL}")
    print(f"Instance download: {INSTANCE_URL}")


if __name__ == "__main__":
    main()
