#!/usr/bin/env python3
"""A throwaway second dev server for testing builds that need new jars (new items, mixins, payloads) without
kicking the players off the live dev server.

    python tools/staging.py prepare        # copy run/server -> run/server-staging (world saved first)
    ./gradlew :dev:runServer -Pstaging     # start it (port 25566, RCON 25576, debug 5008)
    python tools/staging.py cmd "list" "give SkyCam ..."
    ./gradlew :dev:runClient -Pcam -Pjoin=localhost:25566   # the camera client joins it

The copy listens on 127.0.0.1 only, has BlueMap's web server off (the live one owns port 8100) and gets a fresh
RCON password. Nothing done there touches the live world.
"""
import secrets
import shutil
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import mc  # noqa: E402

LIVE = mc.SERVER_DIR
STAGING = LIVE.parent / "server-staging"
SKIP = {"logs", "crash-reports", "session.lock"}
SKIP_TOP = {"bluemap"}  # BlueMap's render data (its config folder is kept)
OVERRIDES = {"server-port": "25566", "rcon.port": "25576", "server-ip": "127.0.0.1", "motd": "SkyRPG staging (test copy)"}


def use_staging():
    """Points mc.py's RCON helpers at the staging server."""
    mc.SERVER_DIR = STAGING
    mc.PROPERTIES = STAGING / "server.properties"


def prepare():
    try:
        live = mc.connect()
        live.command("save-all flush")
        live.close()
    except OSError:
        print("Live server isn't running; copying the world as it is on disk.")
    shutil.rmtree(STAGING, ignore_errors=True)
    shutil.copytree(LIVE, STAGING, ignore=lambda folder, names: [n for n in names if n in SKIP
                                                                 or (Path(folder) == LIVE and n in SKIP_TOP)])
    use_staging()
    props = mc.read_properties()
    props.update(OVERRIDES)
    props["rcon.password"] = secrets.token_urlsafe(18)
    mc.PROPERTIES.write_text("".join(f"{k}={v}\n" for k, v in props.items()), encoding="utf-8")
    webserver = STAGING / "config" / "bluemap" / "webserver.conf"
    if webserver.exists():
        text = webserver.read_text(encoding="utf-8")
        webserver.write_text(text.replace("enabled: true", "enabled: false"), encoding="utf-8")
    print(f"Staging copy ready in {STAGING} (port 25566, RCON 25576). Start it with ./gradlew :dev:runServer -Pstaging")


def main():
    if len(sys.argv) < 2 or sys.argv[1] not in ("prepare", "cmd", "wait"):
        sys.exit(__doc__)
    if sys.argv[1] == "prepare":
        prepare()
        return
    use_staging()
    if sys.argv[1] == "wait":
        sys.argv = [sys.argv[0], "wait"] + sys.argv[2:]
    else:
        sys.argv = [sys.argv[0], "cmd"] + sys.argv[2:]
    mc.main()


if __name__ == "__main__":
    main()
