#!/usr/bin/env python3
"""Star Hunt: glowing stars appear on the ground and rooftops around the hub; touch one to collect it.
White stars are 1 point, rare gold stars 3. Runs on the skyrpg_events mod (lobby, .join, live
leaderboard, podium, prizes); this script only places the stars and scores pickups.

    python tools/events/star_hunt.py [--seconds 90] [--lobby 15] [--stars 14] [--join Drogashh]
        [--first-coins 300] [--first "skyrpg_items:harvester_hoe=Harvester Hoe" "minecraft:diamond 5=5 diamonds"]
"""
import argparse
import json
import random
import sys
import time
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent.parent))
import mc  # noqa: E402

HUB = "skyrpg_hub:hub"
EVENT = "star_hunt"
TAG, GOLD, NEW = "skyrpg_star", "skyrpg_star_gold", "skyrpg_star_new"
RANGE = 55  # stars land within this many blocks of the hub centre, on the highest block below MAX_Y
MAX_Y = 110
GOLD_CHANCE = 0.18


# 1st place gets coins plus every item here: (give arguments, label).
FIRST_ITEMS = [("skyrpg_items:prospector_pickaxe", "Prospector Pickaxe"), ("minecraft:diamond 3", "3 diamonds")]


def parse_item(text):
    """'minecraft:diamond 3=3 diamonds' -> ('minecraft:diamond 3', '3 diamonds'); the label defaults to the id."""
    give, _, label = text.partition("=")
    return give.strip(), label.strip() or give.split(":")[-1].replace("_", " ")


def spec(seconds, lobby, first_coins=250, first_items=FIRST_ITEMS):
    first_label = " + ".join([f"{first_coins} coins"] + [label for _, label in first_items])
    return {
        "id": EVENT, "name": "Star Hunt", "description": "Touch the glowing stars around the hub! Gold ones are worth 3.",
        "color": "#FFD34D", "icon": "minecraft:nether_star", "duration": f"{seconds}s", "unit": "stars",
        "participation": "opt_in", "lobby": f"{lobby}s", "chat_join": True,
        "scoring": [{"type": "custom"}],
        "prizes": [
            {"place": 1, "coins": first_coins, "commands": [f"give {{player}} {give}" for give, _ in first_items], "label": first_label},
            {"place": 2, "coins": 120, "commands": ["give {player} minecraft:golden_carrot 8", "give {player} minecraft:emerald 4"],
             "label": "120 coins + 8 golden carrots + 4 emeralds"},
            {"place": 3, "coins": 60, "commands": ["give {player} minecraft:cookie 16"], "label": "60 coins + 16 cookies"},
            {"place": "all", "commands": ["xp add {player} 5 levels", "give {player} minecraft:firework_rocket 8"],
             "label": "5 XP levels + 8 rockets"},
        ],
        "announcements": [30, 10],
    }


def hub(rcon, command):
    return rcon.command(f"execute in {HUB} run {command}")


def status(rcon):
    reply = rcon.command(f"event status {EVENT}")
    try:
        return json.loads(reply[reply.index("{"):])
    except ValueError:
        return {"phase": "none"}


def spawn_star(rcon):
    gold = random.random() < GOLD_CHANCE
    item = "minecraft:gold_block" if gold else "minecraft:nether_star"
    tags = f'["{TAG}","{NEW}"' + (f',"{GOLD}"' if gold else "") + "]"
    glow = 16766541 if gold else 15658751
    hub(rcon, f'summon minecraft:item_display 0 100 0 {{Tags:{tags},item:{{id:"{item}",count:1}},billboard:"vertical",'
              f'Glowing:1b,glow_color_override:{glow},brightness:{{sky:15,block:15}},'
              f'transformation:{{scale:[0.7f,0.7f,0.7f],translation:[0f,0f,0f],left_rotation:[0f,0f,0f,1f],right_rotation:[0f,0f,0f,1f]}}}}')
    hub(rcon, f"spreadplayers 0 0 6 {RANGE} under {MAX_Y} false @e[tag={NEW}]")
    hub(rcon, f"execute as @e[tag={NEW}] at @s run tp @s ~ ~1.1 ~")
    hub(rcon, f"kill @e[tag={NEW},y=-64,dy=110]")  # landed in the void below the island: drop it
    hub(rcon, f"tag @e[tag={NEW}] remove {NEW}")


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--seconds", type=int, default=90)
    parser.add_argument("--lobby", type=int, default=15)
    parser.add_argument("--stars", type=int, default=14)
    parser.add_argument("--first-coins", type=int, default=250)
    parser.add_argument("--first", nargs="*", help="1st place items as 'give args=label', e.g. 'minecraft:diamond 3=3 diamonds'")
    parser.add_argument("--join", nargs="*", default=[], help="players to join right away")
    args = parser.parse_args()
    rcon = mc.connect()
    hub(rcon, f"kill @e[tag={TAG}]")
    command = "event run " + json.dumps(spec(args.seconds, args.lobby, args.first_coins,
                                          [parse_item(t) for t in args.first] if args.first else FIRST_ITEMS), separators=(",", ":"))
    reply = rcon.command(command)
    print(reply, flush=True)
    if "Unknown" in reply or "rror" in reply:
        return
    for player in args.join:
        print(rcon.command(f"event points {player} add 0 {EVENT}"), flush=True)

    while status(rcon)["phase"] == "lobby":
        time.sleep(0.5)
    tick = 0
    while (state := status(rcon))["phase"] == "running":
        alive = rcon.command(f"execute in {HUB} if entity @e[tag={TAG}]")
        count = int(alive.split(":")[-1]) if "count:" in alive.lower() else 0
        for _ in range(max(0, args.stars - count)):
            spawn_star(rcon)
        for player in state.get("players", []):
            for tag, points in ((GOLD, 3), (TAG, 1)):
                got = rcon.command(f"execute as {player} at @s run kill @e[tag={tag},distance=..1.9,limit=1]")
                if got.startswith("Killed"):
                    rcon.command(f"event points {player} add {points} {EVENT}")
                    rcon.command(f"execute as {player} at @s run playsound minecraft:entity.experience_orb.pickup master @s ~ ~ ~ 1 {1.6 if points == 1 else 0.8}")
                    rcon.command(f"execute at {player} run particle minecraft:{'totem_of_undying' if points == 3 else 'end_rod'} ~ ~1 ~ 0.4 0.6 0.4 0.15 30")
                    break
        if tick % 4 == 0:
            hub(rcon, f"execute at @e[tag={TAG}] run particle minecraft:end_rod ~ ~0.3 ~ 0.2 0.3 0.2 0.01 2")
            hub(rcon, f"execute as @e[tag={TAG}] at @s run tp @s ~ ~ ~ ~12 ~")
        tick += 1
        time.sleep(0.2)
    hub(rcon, f"kill @e[tag={TAG}]")
    print("Star Hunt finished:", state, flush=True)
    rcon.close()


if __name__ == "__main__":
    main()
