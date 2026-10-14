#!/usr/bin/env python3
"""Starts Timber Rush on the dev server: whoever chops the most logs wins coins.

    python tools/events/gather_rush.py                      # 5 minutes of logs, 100/50/25 coins
    python tools/events/gather_rush.py --minutes 10 --prize 250
    python tools/events/gather_rush.py --finish-in 10      # cut a running Timber Rush down to 10 seconds

The skyrpg_events mod does the rest (live leaderboard, warnings, prizes, winner board), so this only
sends one /event run with the event as JSON. See tools/events/README.md for more events.
"""
import argparse
import json
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent.parent))
import mc  # noqa: E402


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--minutes", type=float, default=5)
    parser.add_argument("--prize", type=int, default=100, help="coins for 1st; 2nd and 3rd get half and a quarter")
    parser.add_argument("--name", default="Timber Rush")
    parser.add_argument("--finish-in", type=int, help="make the running event end in N seconds instead")
    parser.add_argument("--id", default="timber_rush", help="which running event --finish-in changes")
    args = parser.parse_args()
    rcon = mc.connect()

    if args.finish_in is not None:
        print(rcon.command(f"event time set {args.finish_in} {args.id}"), flush=True)
        return

    event = {
        "id": "timber_rush",
        "name": args.name,
        "description": "Chop the most logs before time runs out!",
        "color": "#C8913C",
        "icon": "minecraft:oak_log",
        "duration": round(args.minutes * 60),
        "unit": "logs",
        "scoring": [{"type": "break_blocks", "blocks": ["#minecraft:logs"], "points": 1}],
        "prizes": [{"place": 1, "coins": args.prize}, {"place": 2, "coins": args.prize // 2}, {"place": 3, "coins": args.prize // 4}],
    }
    print(rcon.command("event run " + json.dumps(event)), flush=True)
    rcon.close()


if __name__ == "__main__":
    main()
