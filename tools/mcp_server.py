#!/usr/bin/env python3
"""MCP server that gives Claude tools for the SkyRPG dev Minecraft server.

Registered for this project in .mcp.json, so every Claude Code session here can run commands,
build, look at blocks, find players and read chat. Standard library only: it speaks the Model
Context Protocol (JSON-RPC 2.0, one message per line) over stdin/stdout and talks to the server
over RCON through mc.py. Logs go to stderr; stdout is reserved for the protocol.
"""
import json
import math
import re
import socket
import subprocess
import sys
import time
import traceback
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import mc  # noqa: E402

ROOT = Path(__file__).resolve().parent.parent
LATEST_LOG = mc.SERVER_DIR / "logs" / "latest.log"
CONSOLE_LOG = ROOT / "run" / "server-console.log"
SCREENSHOTS = ROOT / "run" / "client" / "screenshots"
HUB = "skyrpg_hub:hub"
MAX_OUTPUT = 30_000
SHOW_EACH_REPLY_UP_TO = 60

CHAT_LINE = re.compile(r"^\[(\d\d:\d\d:\d\d)\] \[Server thread/INFO\] \(Minecraft\) (?:\[Not Secure\] )?<([^>]+)> (.*)$")
NUMBER = re.compile(r"-?\d+(?:\.\d+)?(?:E-?\d+)?")
GAME_MODES = {0: "survival", 1: "creative", 2: "adventure", 3: "spectator"}

INSTRUCTIONS = f"""Tools for the SkyRPG dev Minecraft server (Fabric 26.3) running on this machine.
- Dimensions: minecraft:overworld (survival world) and {HUB} (the hub everyone spawns in; the village is
  around 0,64,0 with grass at y=64 and players standing at y=65).
- Pass `dimension` instead of writing "execute in ..." yourself. Building in chunks no player is near needs
  `load_area` [x1, z1, x2, z2], or fill/setblock fail with "That position is not loaded".
- To "see": scan_area for blocks as text, get_player for where someone is standing and looking,
  refresh_map then the browser at http://127.0.0.1:8100 for a 3D view, and client_screenshot to see a
  player's game window (e.g. menus you built), which you then open with the Read tool. A screenshot with a
  page opens that menu on their screen, so ask first if they're playing.
- Players may be online. Never stop the server while they are without asking them first.
- After changing the hub build, /hubadmin save and copy the file into the mod (see README "The hub").
- Events players ask for ("most wood in 5 minutes wins 100 coins"): start_event / event_status / end_event, with
  announce and board for banners and sidebars. Inventory stashing, kits, auto-glide and .join chat opt-in are
  event JSON options (tools/events/README.md); never keep players' items only in world blocks."""


class ToolError(Exception):
    pass


# ---------------------------------------------------------------- server access
def rcon():
    try:
        return mc.connect(timeout=5)
    except (OSError, ConnectionError):
        raise ToolError("The dev server isn't running (RCON refused). Start it with start_server.")
    except RuntimeError as e:
        raise ToolError(str(e))


def server_up():
    try:
        mc.connect(timeout=2).close()
        return True
    except (OSError, ConnectionError, RuntimeError):
        return False


def in_dimension(command, dimension):
    if not dimension or command.startswith("execute in ") or command.startswith("sleep "):
        return command
    return f"execute in {dimension} run {command}"


def players(conn):
    reply = conn.command("list")
    names = reply.split(":", 1)[1].strip() if ":" in reply else ""
    return [n.strip() for n in names.split(",") if n.strip()]


def entity_data(conn, name, path):
    reply = conn.command(f"data get entity {name} {path}")
    if "has the following entity data:" not in reply:
        return None
    return reply.split("has the following entity data:", 1)[1].strip()


def numbers(text):
    return [float(n) for n in NUMBER.findall(text or "")]


def facing(yaw):
    return ["south", "west", "north", "east"][round((yaw % 360) / 90) % 4]


def block_name(conn, dimension, x, y, z):
    reply = conn.command(in_dimension(f"devtools scan {x} {y} {z} {x} {y} {z} summary", dimension))
    match = re.search(r"Blocks: \S=(\S+)", reply)
    return match.group(1) if match else "air"


def looking_at(conn, dimension, pos, yaw, pitch, reach=8.0):
    """Walks along the player's view ray and returns the first non-air block, as (x, y, z)."""
    yaw_r, pitch_r = math.radians(yaw), math.radians(pitch)
    direction = (-math.sin(yaw_r) * math.cos(pitch_r), -math.sin(pitch_r), math.cos(yaw_r) * math.cos(pitch_r))
    eye = (pos[0], pos[1] + 1.62, pos[2])
    seen = set()
    for i in range(1, int(reach * 4) + 1):
        point = tuple(math.floor(eye[k] + direction[k] * i / 4) for k in range(3))
        if point in seen:
            continue
        seen.add(point)
        reply = conn.command(in_dimension(f"execute if block {point[0]} {point[1]} {point[2]} #minecraft:air", dimension))
        if "Test failed" in reply:
            return point
        if "Test passed" not in reply:
            return None
    return None


# ---------------------------------------------------------------- tools
def server_status(args):
    if not server_up():
        return "The dev server is not running. Use start_server to start it."
    conn = rcon()
    try:
        online = players(conn)
        lines = [f"Server is running. {len(online)} player(s) online."]
        for name in online:
            dim = (entity_data(conn, name, "Dimension") or "?").strip('"')
            pos = numbers(entity_data(conn, name, "Pos"))
            lines.append(f"- {name} in {dim} at " + (" ".join(f"{p:.1f}" for p in pos) if pos else "?"))
        return "\n".join(lines)
    finally:
        conn.close()


def get_player(args):
    name = args["name"]
    conn = rcon()
    try:
        dim = entity_data(conn, name, "Dimension")
        if dim is None:
            raise ToolError(f"{name} is not online. Online: {', '.join(players(conn)) or 'nobody'}")
        dim = dim.strip('"')
        pos = numbers(entity_data(conn, name, "Pos"))
        yaw, pitch = numbers(entity_data(conn, name, "Rotation"))[:2]
        mode = numbers(entity_data(conn, name, "playerGameType"))
        health = numbers(entity_data(conn, name, "Health"))
        held = entity_data(conn, name, "SelectedItem") or entity_data(conn, name, "equipment.mainhand")
        block = [math.floor(pos[0]), math.floor(pos[1]), math.floor(pos[2])]
        lines = [
            f"{name} is in {dim} at {pos[0]:.2f} {pos[1]:.2f} {pos[2]:.2f} (standing in block {block[0]} {block[1]} {block[2]}, "
            f"on top of y={block[1] - 1})",
            f"Facing {facing(yaw)} (yaw {yaw:.1f}, pitch {pitch:.1f}; yaw 0=south/+z, 90=west/-x, 180=north/-z, -90=east/+x)",
            f"Game mode: {GAME_MODES.get(int(mode[0]), '?') if mode else '?'}, health: {health[0] if health else '?'}",
            f"Holding: {held or 'nothing'}",
        ]
        target = looking_at(conn, dim, pos, yaw, pitch)
        if target:
            lines.append(f"Looking at {block_name(conn, dim, *target)} at {target[0]} {target[1]} {target[2]}")
        else:
            lines.append("Not looking at a block within 8 blocks")
        return "\n".join(lines)
    finally:
        conn.close()


def execute_batch(commands, dimension=None, load_area=None):
    commands = [in_dimension(c, dimension) for c in mc.clean(commands)]
    conn = rcon()
    out, errors = [], []
    try:
        if load_area:
            x1, z1, x2, z2 = load_area
            conn.command(in_dimension(f"forceload add {x1} {z1} {x2} {z2}", dimension))
        try:
            for command in commands:
                reply, failed = mc.run_one(conn, command)
                if failed:
                    errors.append(f"> {command}\n{reply}")
                if len(commands) <= SHOW_EACH_REPLY_UP_TO:
                    out.append(f"> {command}\n{reply}" if reply else f"> {command}")
        finally:
            if load_area:
                conn.command(in_dimension(f"forceload remove {x1} {z1} {x2} {z2}", dimension))
    finally:
        conn.close()
    if len(commands) > SHOW_EACH_REPLY_UP_TO:
        out = [f"Ran {len(commands)} commands, {len(errors)} failed."] + errors[:40]
        if len(errors) > 40:
            out.append(f"... and {len(errors) - 40} more failures")
    text = "\n".join(out)
    return text[:MAX_OUTPUT] + ("\n... (output truncated)" if len(text) > MAX_OUTPUT else "")


def run_commands(args):
    return execute_batch(args["commands"], args.get("dimension"), args.get("load_area"))


def run_script(args):
    path = (ROOT / args["path"]).resolve()
    if not path.is_file():
        raise ToolError(f"No such file: {path}")
    return execute_batch(path.read_text(encoding="utf-8").splitlines(), args.get("dimension"), args.get("load_area"))


def scan_area(args):
    (x1, y1, z1), (x2, y2, z2) = args["from"], args["to"]
    mode = args.get("mode", "top")
    dimension = args.get("dimension", "minecraft:overworld")
    conn = rcon()
    try:
        reply = conn.command(in_dimension(f"devtools scan {x1} {y1} {z1} {x2} {y2} {z2} {mode}", dimension))
    finally:
        conn.close()
    if reply.startswith("Unknown or incomplete command"):
        raise ToolError("The devtools mod isn't loaded. Restart the server with ./gradlew :dev:runServer.")
    return reply[:MAX_OUTPUT * 3]


def read_chat(args):
    limit = int(args.get("limit", 20))
    contains = (args.get("contains") or "").lower()
    if not LATEST_LOG.exists():
        raise ToolError("No server log yet.")
    messages = []
    for line in LATEST_LOG.read_text(encoding="utf-8", errors="replace").splitlines():
        match = CHAT_LINE.match(line)
        if match and contains in match.group(3).lower():
            messages.append(f"[{match.group(1)}] <{match.group(2)}> {match.group(3)}")
    return "\n".join(messages[-limit:]) or "No chat messages in the current server log."


def server_log(args):
    lines = int(args.get("lines", 60))
    pattern = args.get("pattern")
    if not LATEST_LOG.exists():
        raise ToolError("No server log yet.")
    log = LATEST_LOG.read_text(encoding="utf-8", errors="replace").splitlines()
    if pattern:
        regex = re.compile(pattern, re.IGNORECASE)
        log = [line for line in log if regex.search(line)]
    return "\n".join(log[-lines:])[:MAX_OUTPUT] or "Nothing matched."


def start_server(args):
    if server_up():
        return "The server is already running."
    wait_seconds = int(args.get("wait_seconds", 150))
    gradlew = ROOT / ("gradlew.bat" if sys.platform == "win32" else "gradlew")
    CONSOLE_LOG.parent.mkdir(parents=True, exist_ok=True)
    flags = subprocess.CREATE_NEW_PROCESS_GROUP | subprocess.DETACHED_PROCESS if sys.platform == "win32" else 0
    with open(CONSOLE_LOG, "w", encoding="utf-8") as console:
        subprocess.Popen([str(gradlew), ":dev:runServer", "--console=plain"], cwd=ROOT, stdin=subprocess.DEVNULL,
                         stdout=console, stderr=subprocess.STDOUT, creationflags=flags,
                         start_new_session=sys.platform != "win32")
    deadline = time.time() + wait_seconds
    while time.time() < deadline:
        time.sleep(3)
        if server_up():
            return "Server started."
    return f"Still starting after {wait_seconds}s (Gradle may be building). Check server_status soon; console: {CONSOLE_LOG}"


def stop_server(args):
    conn = rcon()
    try:
        online = players(conn)
        if online and not args.get("force"):
            return f"Not stopping: {', '.join(online)} online. Ask them first, then call again with force=true."
        conn.command("save-all flush")
        conn.command("stop")
    finally:
        conn.close()
    for _ in range(30):
        time.sleep(2)
        if not server_up():
            return "Server stopped."
    return "Asked the server to stop; it is still shutting down."


def client_screenshot(args):
    name, page = args["player"], args.get("page", "")
    started = time.time()
    conn = rcon()
    try:
        reply = conn.command(f"devtools screenshot {name} {page}".strip())
    finally:
        conn.close()
    if "Asked" not in reply:
        raise ToolError(reply or "Screenshot request failed")
    while time.time() < started + 15:
        time.sleep(0.5)
        shots = [p for p in SCREENSHOTS.glob("*.png") if p.stat().st_mtime >= started - 1] if SCREENSHOTS.exists() else []
        if shots:
            time.sleep(0.5)  # let the file finish writing
            newest = max(shots, key=lambda p: p.stat().st_mtime)
            return f"Screenshot saved: {newest}\nOpen it with the Read tool to look at it."
    raise ToolError("No screenshot appeared within 15s. Is that player on the dev client (./gradlew :dev:runClient)?")


def hot_reload(args):
    """Compiles and hot-swaps changed classes into the running dev client and server (tools/hotswap.py)."""
    result = subprocess.run([sys.executable, str(ROOT / "tools" / "hotswap.py")], cwd=ROOT, capture_output=True, text=True,
                            encoding="utf-8", errors="replace", timeout=600)
    return (result.stdout + result.stderr).strip()[-MAX_OUTPUT:] or "Done."


def refresh_map(args):
    map_id = args.get("map", "world_skyrpg_hub_hub")
    conn = rcon()
    try:
        conn.command("save-all flush")
        reply = conn.command(f"bluemap force-update {map_id}")
    finally:
        conn.close()
    return (f"{reply.strip()}\nRendering takes ~30-60s. Then open "
            f"http://127.0.0.1:8100/#{map_id}:0:64:0:120:0.5:1.0:0:0:perspective "
            "(hash is map:x:y:z:distance:rotation:angle:0:0:perspective, or ...:0:0:0:1:flat for top-down).")


# ---------------------------------------------------------------- events (skyrpg_events mod, see tools/events/README.md)
MAX_RCON_COMMAND = 1400  # vanilla RCON drops commands longer than about 1446 bytes


def compact(value):
    return json.dumps(value, separators=(",", ":"), ensure_ascii=False)


def mod_command(command):
    if len(command.encode("utf-8")) > MAX_RCON_COMMAND:
        raise ToolError(f"That command is {len(command)} characters, too long for RCON (~1400). Shorten it, e.g. start "
                        "from a shipped event with from:'timber_rush' and only override what changes.")
    conn = rcon()
    try:
        reply = conn.command(command)
    finally:
        conn.close()
    if reply.startswith("Unknown or incomplete command"):
        raise ToolError("The skyrpg_events mod isn't loaded on the server (it needs a server restart after being added).")
    return reply.strip() or "Done."


def target(args):
    players = args.get("players")
    return f" to {players}" if players else ""


def start_event(args):
    if args.get("spec"):
        return mod_command("event run " + compact(args["spec"]))
    if args.get("id"):
        return mod_command(f"event start {args['id']}" + (f" {args['duration']}" if args.get("duration") else ""))
    raise ToolError("Pass spec (an event JSON object) or id (a shipped event like timber_rush).")


def event_status(args):
    return mod_command("event status" + (f" {args['id']}" if args.get("id") else ""))


def end_event(args):
    verb = "end" if args.get("award", True) else "stop"
    return mod_command(f"event {verb}" + (f" {args['id']}" if args.get("id") else ""))


def announce(args):
    return mod_command("announce" + target(args) + " " + compact(args["announcement"]))


def board(args):
    if args.get("hide"):
        return mod_command("board hide" + (f" {args['players']}" if args.get("players") else ""))
    return mod_command("board show" + target(args) + " " + compact(args["board"]))


XYZ = {"type": "array", "items": {"type": "integer"}, "minItems": 3, "maxItems": 3}
AREA = {"type": "array", "items": {"type": "integer"}, "minItems": 4, "maxItems": 4,
        "description": "[x1, z1, x2, z2]: force-load these chunks while running, then unload them"}
DIMENSION = {"type": "string", "description": f"Run in this dimension, e.g. {HUB} or minecraft:overworld"}

TOOLS = [
    ("server_status", "Is the dev server running, and who is online where.", {}, [], server_status),
    ("get_player", "Where a player is, which way they face, what they hold and which block they're looking at. "
                   "Use this to build 'here' or 'in front of me'.",
     {"name": {"type": "string"}}, ["name"], get_player),
    ("run_commands", "Run Minecraft commands as the server console (fill, setblock, give, tp, summon, tellraw, ...). "
                     "'sleep 1.5' lines pause. Replies aren't echoed into players' chat (devtools).",
     {"commands": {"type": "array", "items": {"type": "string"}}, "dimension": DIMENSION, "load_area": AREA},
     ["commands"], run_commands),
    ("run_script", "Run every line of a .mcfunction file (path relative to the project), e.g. a generated build. "
                   "Returns a summary and any failures.",
     {"path": {"type": "string"}, "dimension": DIMENSION, "load_area": AREA}, ["path"], run_script),
    ("scan_area", "Read the blocks in a box as text. mode 'summary' = counts, 'top' (default) = top-down map + heights, "
                  "'layers' = a grid per y level (max 32768 blocks). x goes right, z goes down (north up).",
     {"from": XYZ, "to": XYZ, "dimension": DIMENSION, "mode": {"type": "string", "enum": ["summary", "top", "layers"]}},
     ["from", "to"], scan_area),
    ("read_chat", "Recent player chat from the server log, e.g. to see requests players typed in game.",
     {"limit": {"type": "integer"}, "contains": {"type": "string", "description": "only messages containing this"}},
     [], read_chat),
    ("server_log", "Tail of the server log, optionally filtered by a regex (e.g. 'ERROR|Exception').",
     {"lines": {"type": "integer"}, "pattern": {"type": "string"}}, [], server_log),
    ("start_server", "Start the dev server (./gradlew :dev:runServer) in the background and wait until it's up.",
     {"wait_seconds": {"type": "integer"}}, [], start_server),
    ("stop_server", "Save and stop the dev server. Refuses while players are online unless force=true.",
     {"force": {"type": "boolean"}}, [], stop_server),
    ("client_screenshot", "Screenshot a player's dev client window (only the game, saved to run/client/screenshots). "
                          "Optional page opens that SkyRPG menu page first ('profile', 'skills'), or 'close' closes screens.",
     {"player": {"type": "string"}, "page": {"type": "string"}}, ["player"], client_screenshot),
    ("hot_reload", "Compile and push changed code into the running dev client and server without restarting "
                   "(method-body changes; it says if something needs a restart). Data JSON needs /reload instead.",
     {}, [], hot_reload),
    ("start_event", "Start a SkyRPG event: live leaderboard on everyone's HUD, warnings, prizes and a winner board. "
                    "Give spec (event JSON, see tools/events/README.md; from:'timber_rush' reuses a shipped one) or id "
                    "(timber_rush, monster_hunt, sky_rings) with an optional duration like '2m'. Opt-in events with a lobby "
                    "let players type .join in chat.",
     {"spec": {"type": "object"}, "id": {"type": "string"}, "duration": {"type": "string"}}, [], start_event),
    ("event_status", "Running events as JSON: phase (lobby/running/none), seconds left, players and scores.",
     {"id": {"type": "string"}}, [], event_status),
    ("end_event", "Finish an event now and hand out prizes (award=false cancels it without prizes).",
     {"id": {"type": "string"}, "award": {"type": "boolean"}}, [], end_event),
    ("announce", "Show a SkyRPG banner at the top of players' screens: {title, subtitle, label, color, icon, sound, chat}. "
                 "Text accepts &6 colour codes.",
     {"announcement": {"type": "object"}, "players": {"type": "string", "description": "selector, default everyone"}},
     ["announcement"], announce),
    ("board", "Show a custom sidebar {title, color, lines, footer, duration} (lines: strings, [label, value] or "
              "{label, value, color}; {player} = viewer's name), or hide=true to remove it.",
     {"board": {"type": "object"}, "players": {"type": "string"}, "hide": {"type": "boolean"}}, [], board),
    ("refresh_map", "Save the world and re-render a BlueMap map, then view it in the browser.",
     {"map": {"type": "string", "description": "BlueMap map id, default world_skyrpg_hub_hub"}}, [], refresh_map),
]
HANDLERS = {name: handler for name, _, _, _, handler in TOOLS}


def tool_list():
    return [{"name": name, "description": description,
             "inputSchema": {"type": "object", "properties": properties, "required": required}}
            for name, description, properties, required, _ in TOOLS]


# ---------------------------------------------------------------- protocol
def send(message):
    sys.stdout.buffer.write(json.dumps(message).encode("utf-8") + b"\n")
    sys.stdout.buffer.flush()


def handle(message):
    method, request_id = message.get("method"), message.get("id")
    if request_id is None:
        return  # notifications (initialized, cancelled, ...) need no reply
    params = message.get("params") or {}
    if method == "initialize":
        result = {"protocolVersion": params.get("protocolVersion", "2025-06-18"),
                  "capabilities": {"tools": {"listChanged": False}},
                  "serverInfo": {"name": "skyrpg-minecraft", "version": "0.1.0"},
                  "instructions": INSTRUCTIONS}
    elif method == "tools/list":
        result = {"tools": tool_list()}
    elif method == "tools/call":
        handler = HANDLERS.get(params.get("name"))
        if handler is None:
            send({"jsonrpc": "2.0", "id": request_id, "error": {"code": -32602, "message": f"Unknown tool {params.get('name')}"}})
            return
        try:
            result = {"content": [{"type": "text", "text": handler(params.get("arguments") or {})}], "isError": False}
        except ToolError as e:
            result = {"content": [{"type": "text", "text": str(e)}], "isError": True}
        except (socket.timeout, ConnectionError, OSError) as e:
            result = {"content": [{"type": "text", "text": f"Lost the connection to the server: {e}"}], "isError": True}
        except Exception:
            traceback.print_exc(file=sys.stderr)
            result = {"content": [{"type": "text", "text": traceback.format_exc(limit=3)}], "isError": True}
    elif method == "ping":
        result = {}
    else:
        send({"jsonrpc": "2.0", "id": request_id, "error": {"code": -32601, "message": f"Method not found: {method}"}})
        return
    send({"jsonrpc": "2.0", "id": request_id, "result": result})


def main():
    for raw in sys.stdin.buffer:
        line = raw.decode("utf-8", errors="replace").strip()
        if not line:
            continue
        try:
            message = json.loads(line)
        except json.JSONDecodeError:
            send({"jsonrpc": "2.0", "id": None, "error": {"code": -32700, "message": "Parse error"}})
            continue
        handle(message)


if __name__ == "__main__":
    main()
