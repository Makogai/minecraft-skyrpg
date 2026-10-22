#!/usr/bin/env python3
"""Runs SkyHelper for a server hosted elsewhere (Falix): helper.py (which starts Ollama) on this PC, reachable from
the internet through an ngrok tunnel on your fixed free domain, guarded by a secret token.

    python tools/skyhelper/public.py --domain skyrpg.ngrok-free.app   # first time: remembers the domain
    python tools/skyhelper/public.py                                   # after that

The token is made once and kept in run/skyhelper/token.txt (never committed). The server's config/skyrpg/helper.json
must have the same url and token, and "autostart": false; the file to upload is written to
run/skyhelper/server_helper.json. Ctrl+C stops the helper and the tunnel. While this isn't running, SkyHelper on
the server says it's asleep.
"""
import argparse
import json
import os
import secrets
import shutil
import subprocess
import sys
import time
import urllib.request
from pathlib import Path

HERE = Path(__file__).resolve().parent
ROOT = HERE.parent.parent
STATE = ROOT / "run" / "skyhelper"
PORT = 8770


def remembered(name, value=None):
    """Reads run/skyhelper/<name>, or stores value there first."""
    path = STATE / name
    if value:
        STATE.mkdir(parents=True, exist_ok=True)
        path.write_text(value, encoding="utf-8")
    return path.read_text(encoding="utf-8").strip() if path.exists() else None


def healthy(url, token, timeout=3):
    request = urllib.request.Request(url + "/health", headers={"X-SkyHelper-Token": token, "ngrok-skip-browser-warning": "1"})
    try:
        with urllib.request.urlopen(request, timeout=timeout) as response:
            return json.loads(response.read()).get("ok", False)
    except (OSError, ValueError):
        return False


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--domain", help="your ngrok domain, e.g. skyrpg.ngrok-free.app (remembered)")
    args = parser.parse_args()
    domain = remembered("ngrok_domain.txt", args.domain)
    if not domain:
        sys.exit("Give your ngrok domain once: python tools/skyhelper/public.py --domain <name>.ngrok-free.app")
    token = remembered("token.txt") or remembered("token.txt", secrets.token_urlsafe(24))
    ngrok = shutil.which("ngrok")
    if not ngrok:
        sys.exit("ngrok isn't installed or not on PATH (https://ngrok.com/download, then: ngrok config add-authtoken <yours>)")

    url = "https://" + domain
    (STATE / "server_helper.json").write_text(json.dumps({
        "enabled": True, "url": url, "token": token, "cooldownSeconds": 8, "timeoutSeconds": 90,
        "autostart": False, "python": "python", "script": "",
    }, indent=2), encoding="utf-8")

    env = dict(os.environ, SKYHELPER_TOKEN=token)
    helper = subprocess.Popen([sys.executable, str(HERE / "helper.py"), "--port", str(PORT)], env=env)
    tunnel = None
    try:
        for _ in range(120):
            if healthy("http://127.0.0.1:%d" % PORT, token):
                break
            if helper.poll() is not None:
                sys.exit("helper.py stopped; see its output above")
            time.sleep(1)
        tunnel = subprocess.Popen([ngrok, "http", "--url=" + domain, str(PORT), "--log", "stdout", "--log-level", "warn"])
        for _ in range(30):
            if healthy(url, token, timeout=5):
                break
            time.sleep(1)
        else:
            print("The tunnel isn't answering yet at", url, "(check ngrok's output)", flush=True)
        print("\nSkyHelper is public at %s (token in run/skyhelper/token.txt)." % url)
        print("Server config to upload as config/skyrpg/helper.json: run/skyhelper/server_helper.json")
        print("Ctrl+C to stop.", flush=True)
        while helper.poll() is None and tunnel.poll() is None:
            time.sleep(1)
    except KeyboardInterrupt:
        pass
    finally:
        for process in (tunnel, helper):
            if process and process.poll() is None:
                process.terminate()


if __name__ == "__main__":
    main()
