#!/usr/bin/env python3
"""Every chat message from one player pops a SkyRPG banner on everyone's screen with a short note-block
fanfare. A joke for friends; runs while this script runs.

    python tools/fun/chat_fanfare.py --player PhilipSGamer [--cooldown 4]

Watches run/server/logs/latest.log (follows the file across server restarts) and sends /announce
(skyrpg_events) plus a few /playsound notes over RCON. Messages to @claude are left alone.
"""
import argparse
import json
import re
import sys
import time
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent.parent))
import mc  # noqa: E402

LOG = mc.SERVER_DIR / "logs" / "latest.log"
CHAT = re.compile(r"\(Minecraft\) (?:\[Not Secure\] )?<([^>]+)> (.*)$")
# A rising C-E-G-C arpeggio and a sparkle: (pitch, pause after)
FANFARE = [(1.0, 0.11), (1.26, 0.11), (1.5, 0.11), (2.0, 0.0)]


def fanfare(rcon, player, message):
    shown = message if len(message) <= 60 else message[:57] + "..."
    rcon.command("announce " + json.dumps({
        "label": "\U0001F4E3 " + player.upper() + " HAS SPOKEN",
        "title": "&6" + player,
        "subtitle": "&f" + shown,
        "color": "#FFB627",
        "icon": "minecraft:goat_horn",
    }, ensure_ascii=False))
    for pitch, pause in FANFARE:
        rcon.command(f"execute as @a at @s run playsound minecraft:block.note_block.bell master @s ~ ~ ~ 0.8 {pitch}")
        time.sleep(pause)
    rcon.command("execute as @a at @s run playsound minecraft:entity.firework_rocket.twinkle master @s ~ ~ ~ 0.6 1.2")


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--player", required=True)
    parser.add_argument("--cooldown", type=float, default=4.0)
    args = parser.parse_args()
    rcon, last = mc.connect(), 0.0
    offset, inode = LOG.stat().st_size, LOG.stat().st_ctime
    print(f"Fanfare for {args.player} is on.", flush=True)
    while True:
        time.sleep(0.25)
        try:
            stat = LOG.stat()
            if stat.st_size < offset or stat.st_ctime != inode:  # server restarted: new log
                offset, inode = 0, stat.st_ctime
                rcon.close()
                time.sleep(3)
                rcon = mc.connect()
            with open(LOG, encoding="utf-8", errors="replace") as f:
                f.seek(offset)
                lines = f.read().splitlines()
                offset = f.tell()
            for line in lines:
                match = CHAT.search(line)
                if not match:
                    continue
                name, message = match.group(1).split()[-1], match.group(2)
                if name != args.player or "@cla" in message.lower() or time.time() - last < args.cooldown:
                    continue
                last = time.time()
                fanfare(rcon, name, message)
        except (OSError, ConnectionError):
            time.sleep(2)
            try:
                rcon = mc.connect()
            except OSError:
                pass


if __name__ == "__main__":
    main()
