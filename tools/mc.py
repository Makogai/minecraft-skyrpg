#!/usr/bin/env python3
"""Talk to the dev server over RCON so builds, tests and admin work can be scripted.

    python tools/mc.py setup                  # write run/server/server.properties + eula.txt (once)
    python tools/mc.py wait                   # block until the server accepts RCON
    python tools/mc.py cmd "time set day" "weather clear"
    python tools/mc.py script --quiet tools/builds/out/hub_village.mcfunction

Lines starting with # are skipped, and "sleep 1.5" pauses between commands.

Start the server itself with ./gradlew :dev:runServer. Settings are read from
run/server/server.properties, which is git-ignored because it holds the RCON password.
"""
import argparse
import itertools
import secrets
import socket
import struct
import sys
import time
from pathlib import Path

SERVER_DIR = Path(__file__).resolve().parent.parent / "run" / "server"
PROPERTIES = SERVER_DIR / "server.properties"

# Applied by `setup`. Anything not listed keeps Minecraft's default.
DEV_PROPERTIES = {
    "enable-rcon": "true",
    "rcon.port": "25575",
    "server-ip": "127.0.0.1",  # dev only: nothing (RCON included) is reachable from other machines
    "online-mode": "false",  # the dev client logs in without a Microsoft account
    "spawn-protection": "0",
    "enable-command-block": "true",
    "allow-flight": "true",
    "difficulty": "normal",
    "motd": "SkyRPG dev server",
}

LOGIN, COMMAND = 3, 2
MAX_REPLY_CHUNK = 4096


def read_properties():
    props = {}
    if PROPERTIES.exists():
        for line in PROPERTIES.read_text(encoding="utf-8").splitlines():
            if "=" in line and not line.lstrip().startswith("#"):
                key, value = line.split("=", 1)
                props[key.strip()] = value.strip()
    return props


def setup(lan=False):
    """lan=True listens on every network interface so friends on the LAN can join (RCON too, behind its password)."""
    SERVER_DIR.mkdir(parents=True, exist_ok=True)
    props = read_properties()
    props.update(DEV_PROPERTIES)
    if lan:
        props["server-ip"] = ""
    if not props.get("rcon.password"):
        props["rcon.password"] = secrets.token_urlsafe(18)
    PROPERTIES.write_text("".join(f"{k}={v}\n" for k, v in props.items()), encoding="utf-8")
    # Running a server requires accepting the Minecraft EULA: https://aka.ms/MinecraftEULA
    (SERVER_DIR / "eula.txt").write_text("eula=true\n", encoding="utf-8")
    where = "all interfaces (LAN)" if lan else "127.0.0.1 only"
    print(f"Wrote {PROPERTIES} (server and RCON on {where}, RCON port {props['rcon.port']})")


class Rcon:
    def __init__(self, host, port, password, timeout=10.0):
        self.ids = itertools.count(1)
        self.sock = socket.create_connection((host, port), timeout=timeout)
        request = next(self.ids)
        self._send(request, LOGIN, password)
        reply_id, _, _ = self._read()
        if reply_id == -1:
            raise PermissionError("RCON password rejected")

    def _send(self, request_id, kind, body):
        payload = struct.pack("<ii", request_id, kind) + body.encode("utf-8") + b"\x00\x00"
        self.sock.sendall(struct.pack("<i", len(payload)) + payload)

    def _recv_exact(self, n):
        data = b""
        while len(data) < n:
            chunk = self.sock.recv(n - len(data))
            if not chunk:
                raise ConnectionError("RCON connection closed")
            data += chunk
        return data

    def _read(self):
        (length,) = struct.unpack("<i", self._recv_exact(4))
        data = self._recv_exact(length)
        request_id, kind = struct.unpack("<ii", data[:8])
        return request_id, kind, data[8:-2].decode("utf-8", errors="replace")

    def command(self, command):
        self._send(next(self.ids), COMMAND, command)
        # Minecraft splits long replies into 4096-byte packets, so a full packet means more follow.
        # (The usual trick of sending a dummy packet as an end marker makes vanilla drop the connection.)
        parts = []
        while True:
            _, _, body = self._read()
            parts.append(body)
            if len(body.encode("utf-8")) < MAX_REPLY_CHUNK:
                return "".join(parts)

    def close(self):
        self.sock.close()


def connect(timeout=10.0):
    props = read_properties()
    if not props.get("rcon.password"):
        raise RuntimeError("No RCON password yet. Run: python tools/mc.py setup")
    host = props.get("server-ip") or "127.0.0.1"
    return Rcon(host, int(props.get("rcon.port", "25575")), props["rcon.password"], timeout)


# Replies that mean a command did not do what it was told.
ERROR_HINTS = ("Unknown", "Incorrect", "Expected", "Invalid", "not loaded", "Too many", "Malformed", "Could not", "error")


def clean(lines):
    """Drops blank lines and # comments, and leading slashes."""
    commands = [line.strip().removeprefix("/") for line in lines]
    return [c for c in commands if c and not c.startswith("#")]


def run_one(rcon, command):
    """Runs a command (or a local "sleep N") and returns (reply, failed)."""
    if command.startswith("sleep "):  # not a Minecraft command: lets scripts wait for ticks to pass
        time.sleep(float(command.split()[1]))
        return "", False
    reply = rcon.command(command)
    return reply, any(hint in reply for hint in ERROR_HINTS)


def run_commands(commands, quiet=False):
    commands = clean(commands)
    rcon = connect()
    errors = 0
    try:
        for i, command in enumerate(commands, 1):
            reply, failed = run_one(rcon, command)
            errors += failed
            if not quiet or failed:
                print(f"> {command}")
                if reply:
                    print(reply)
            if quiet and i % 2000 == 0:
                print(f"... {i}/{len(commands)} commands, {errors} errors", flush=True)
    finally:
        rcon.close()
    if quiet:
        print(f"Done: {len(commands)} commands, {errors} errors")


def wait(timeout):
    deadline = time.time() + timeout
    while time.time() < deadline:
        try:
            connect(timeout=3).close()
            print("Server is up")
            return
        except (OSError, ConnectionError):
            time.sleep(2)
    sys.exit(f"Server did not accept RCON within {timeout}s")


def main():
    # Replies can contain symbols (e.g. BlueMap's checkmarks) that the Windows console encoding can't print.
    sys.stdout.reconfigure(encoding="utf-8", errors="replace")
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    sub = parser.add_subparsers(dest="action", required=True)
    setup_parser = sub.add_parser("setup", help="write dev server.properties and eula.txt")
    setup_parser.add_argument("--lan", action="store_true", help="let other machines on the LAN join (restart the server after)")
    wait_parser = sub.add_parser("wait", help="wait until the server accepts RCON")
    wait_parser.add_argument("--timeout", type=int, default=300)
    cmd_parser = sub.add_parser("cmd", help="run one or more commands")
    cmd_parser.add_argument("commands", nargs="+")
    script_parser = sub.add_parser("script", help="run every line of a .mcfunction file")
    script_parser.add_argument("file", type=Path)
    script_parser.add_argument("--quiet", action="store_true", help="only print failures and progress")
    args = parser.parse_args()

    try:
        if args.action == "setup":
            setup(args.lan)
        elif args.action == "wait":
            wait(args.timeout)
        elif args.action == "cmd":
            run_commands(args.commands)
        elif args.action == "script":
            run_commands(args.file.read_text(encoding="utf-8").splitlines(), quiet=args.quiet)
    except RuntimeError as e:
        sys.exit(str(e))


if __name__ == "__main__":
    main()
