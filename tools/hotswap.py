#!/usr/bin/env python3
"""Push changed code into the running dev client and server without restarting them.

    python tools/hotswap.py              # every mod
    python tools/hotswap.py core skills  # only these (e.g. while another mod is mid-edit)
    python tools/hotswap.py brainrots --to staging,cam   # only these games (client, server, cam, staging)

Compiles all mods, finds the class files whose contents changed, and redefines them in every running
dev game (client debug port 5005, server 5006, camera client 5007, see dev/build.gradle) in one step
through JDI (tools/AtomicRedefine.java), falling back to the JDK's jdb to name classes that can't be swapped.

The dev runs use the JetBrains Runtime with enhanced redefinition, so changed method bodies and added
methods or fields both go live. Some changes still need a restart (a different superclass or
interfaces, mixins, anything in a mod's startup code); the output says which classes those were. New
classes need no swap: the game loads them from the class folders when first used. Resources don't go
through here: data (JSON) -> /reload, textures and lang -> F3+T.
"""
import glob
import hashlib
import os
import socket
import subprocess
import sys
import tempfile
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
MODULES = ["core", "economy", "skills", "hub", "stats", "items", "npcs", "events", "mining", "farming", "foraging", "fishing", "pets", "combat", "enchants", "world", "utils", "vfx", "chat", "progress", "mounts", "party", "magic", "helper", "wiki", "welcome", "builder", "brainrots", "dev"]
PORTS = {"client": 5005, "server": 5006, "cam": 5007, "staging": 5008}


def class_files(modules):
    files = {}
    for module in modules:
        for source_set in ("main", "client"):
            base = ROOT / module / "build" / "classes" / "java" / source_set
            for path in base.rglob("*.class"):
                name = path.relative_to(base).with_suffix("").as_posix().replace("/", ".")
                files[name] = (path, hashlib.sha1(path.read_bytes()).hexdigest())
    return files


def jdb_path():
    candidates = glob.glob(str(Path.home() / ".jdks" / "*25*" / "bin" / "jdb*"))
    if os.environ.get("JAVA_HOME"):
        candidates.append(str(Path(os.environ["JAVA_HOME"]) / "bin" / ("jdb.exe" if os.name == "nt" else "jdb")))
    for candidate in candidates:
        if Path(candidate).is_file():
            return candidate
    return "jdb"


def port_open(port):
    with socket.socket() as s:
        s.settimeout(0.5)
        return s.connect_ex(("127.0.0.1", port)) == 0


def compile_all(modules):
    gradlew = ROOT / ("gradlew.bat" if os.name == "nt" else "gradlew")
    tasks = [f":{m}:compileJava" for m in modules] + [f":{m}:compileClientJava" for m in modules]
    # --continue: a mod that doesn't compile (e.g. mid-edit) doesn't stop the others from being swapped.
    result = subprocess.run([str(gradlew), *tasks, "--continue", "--console=plain", "-q"], cwd=ROOT, capture_output=True, text=True)
    return result.returncode == 0, (result.stdout + result.stderr).strip()


def java_path():
    jdb = Path(jdb_path())
    java = jdb.with_name("java.exe" if os.name == "nt" else "java")
    return str(java) if java.is_file() else "java"


def redefine(port, changed):
    """Returns (swapped, rejected, not_loaded). Raises RuntimeError if the game can't be reached.

    Redefines every class in one step (tools/AtomicRedefine.java, JDI's redefineClasses), so the game
    never runs a new class against an old one: jdb swaps one class at a time, and a new caller swapped
    in before the method it calls exists crashes the game with NoSuchMethodError. If the JVM refuses
    the batch (a change that needs a restart), jdb goes class by class to say which ones those are.
    """
    with tempfile.NamedTemporaryFile("w", suffix=".txt", delete=False, encoding="utf-8") as listing:
        listing.write("\n".join(f"{name} {path}" for name, path in changed))
    try:
        result = subprocess.run([java_path(), str(ROOT / "tools" / "AtomicRedefine.java"), str(port), listing.name],
                                capture_output=True, text=True, timeout=180)
    finally:
        os.unlink(listing.name)
    output = result.stdout + result.stderr
    if "ConnectException" in output or "Connection refused" in output:
        raise RuntimeError(f"couldn't attach to port {port}")
    if any(line.startswith("redefined ") for line in output.splitlines()):
        not_loaded = [line[len("not loaded "):].strip() for line in output.splitlines() if line.startswith("not loaded ")]
        return [name for name, _ in changed if name not in not_loaded], [], not_loaded
    return redefine_one_by_one(port, changed)


def redefine_one_by_one(port, changed):
    """The jdb way, one class at a time; used to find out which classes can't be swapped."""
    commands = "".join(f"redefine {name} {path}\n" for name, path in changed) + "exit\n"
    # On Windows a bare "-attach host:port" means shared memory; the games listen on a socket.
    connector = f"com.sun.jdi.SocketAttach:hostname=127.0.0.1,port={port}"
    result = subprocess.run([jdb_path(), "-connect", connector], input=commands, capture_output=True, text=True, timeout=120)
    output = result.stdout + result.stderr
    if "Unable to attach" in output or "Fatal error" in output:
        raise RuntimeError(f"couldn't attach to port {port}: {output.strip().splitlines()[-1]}")
    rejected, not_loaded = [], []
    for name, _ in changed:
        # jdb prints nothing on success, "No class named 'X'" for classes that game hasn't loaded,
        # and "Error redefining X to <file> - <reason>" when the change can't be applied live.
        if f"No class named '{name}'" in output:
            not_loaded.append(name)
            continue
        for line in output.splitlines():
            if f"Error redefining {name} " in line:
                rejected.append((name, line.split(" - ", 1)[-1].strip()))
                break
    rejected_names = {name for name, _ in rejected}
    swapped = [name for name, _ in changed if name not in not_loaded and name not in rejected_names]
    return swapped, rejected, not_loaded


def short(name):
    return name.rsplit(".", 1)[-1]


def main():
    sys.stdout.reconfigure(encoding="utf-8", errors="replace")
    modules = [m for m in sys.argv[1:] if m in MODULES] or MODULES
    targets = set(sys.argv[sys.argv.index("--to") + 1].split(",")) if "--to" in sys.argv else set(PORTS)
    running = {side: port for side, port in PORTS.items() if side in targets and port_open(port)}
    if not running:
        print("No dev client or server is running with a debug port; nothing to swap. (Start them with ./gradlew :dev:runClient / :dev:runServer.)")
    before = class_files(modules)
    ok, output = compile_all(modules)
    if not ok:
        print("Compile failed, nothing swapped:\n" + output[-4000:])
        sys.exit(1)
    after = class_files(modules)
    changed = [(name, str(path)) for name, (path, digest) in after.items() if name in before and before[name][1] != digest]
    # Classes without a before are usually new, but a failed compile also deletes class files, so after one
    # every class looks new. Offer them all to the game: the ones it has loaded get swapped, the rest are skipped.
    added = [(name, str(path)) for name, (path, _) in after.items() if name not in before]
    if not changed and not added:
        print("No changed classes.")
        return
    if changed:
        print(f"Changed: {', '.join(short(n) for n, _ in changed)}")
    needs_restart = set()
    unused = {name for name, _ in added}
    for side, port in running.items():
        try:
            swapped, rejected, not_loaded = redefine(port, changed + added)
        except RuntimeError as e:
            print(f"{side}: {e}")
            continue
        unused &= set(not_loaded)
        print(f"{side}: swapped {len(swapped)}" + (f", {len(not_loaded)} not loaded there (fine)" if not_loaded else ""))
        for name, reason in rejected:
            needs_restart.add(side)
            print(f"  NEEDS RESTART {short(name)}: {reason}")
    if unused:
        print(f"New classes (load on first use): {', '.join(sorted(map(short, unused)))}")
    if needs_restart:
        print(f"Restart the {' and '.join(sorted(needs_restart))} to apply the rest.")


if __name__ == "__main__":
    main()
