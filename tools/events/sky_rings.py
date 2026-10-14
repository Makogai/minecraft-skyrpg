#!/usr/bin/env python3
"""Sky Rings: fly through gold rings above the hub; each ring you pass is a point, then it vanishes and a
new one appears somewhere else.

    python tools/events/sky_rings.py --join-seconds 20 --seconds 60 --prize 100 [--players Drogashh]

The skyrpg_events mod runs the event itself (data/skyrpg_events/skyrpg/events/sky_rings.json): ".join" in
chat during the join window, each player's inventory put away on the server (on disk, given back at the
end or when they next join), the elytra and rockets, auto-gliding, the start teleport and the trip back,
the clock, live leaderboard, prizes and winner board. This script only builds the barrier arena and the
rings, and scores with /event points when someone flies through one. Needs a server with skyrpg_events;
tools/events/legacy/sky_rings_rcon.py is the old RCON-only version.
"""
import argparse
import json
import math
import random
import re
import sys
import time
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent.parent))
import mc  # noqa: E402

HUB = "skyrpg_hub:hub"
RADIUS = 4
RING_COUNT = 6
BOX = (-50, 121, -50, 50, 170, 50)  # x1 y1 z1 x2 y2 z2, above the hub build (which ends at y 119)
START = "0.5 160 30.5 180 15"  # matches start_at in sky_rings.json
FORCELOAD = "forceload add -64 -64 48 48"


class Ring:
    def __init__(self, x, y, z, axis):
        self.x, self.y, self.z, self.axis = x, y, z, axis

    def blocks(self):
        seen = set()
        for step in range(48):
            a = step / 48 * math.tau
            u, v = round(math.cos(a) * RADIUS), round(math.sin(a) * RADIUS)
            block = (self.x, self.y + v, self.z + u) if self.axis == "x" else (self.x + u, self.y + v, self.z)
            if block not in seen:
                seen.add(block)
                yield block

    def crossed(self, before, after):
        """Did the move from before to after go through the ring's opening?"""
        i = 0 if self.axis == "x" else 2
        plane = (self.x if self.axis == "x" else self.z) + 0.5
        a, b = before[i] - plane, after[i] - plane
        if a == b or a * b > 0:
            return False
        t = a / (a - b)
        point = [before[k] + (after[k] - before[k]) * t for k in range(3)]
        side = point[2] - (self.z + 0.5) if self.axis == "x" else point[0] - (self.x + 0.5)
        return math.hypot(side, point[1] - (self.y + 0.5)) < RADIUS - 0.4


def run(rcon, command):
    return rcon.command(f"execute in {HUB} run {command}")


def new_ring(rings):
    for _ in range(50):
        ring = Ring(random.randint(-38, 38), random.randint(127, 160), random.randint(-38, 38), random.choice("xz"))
        if all(math.dist((ring.x, ring.y, ring.z), (r.x, r.y, r.z)) > 14 for r in rings):
            return ring
    return ring


def place(rcon, ring, block):
    for x, y, z in ring.blocks():
        run(rcon, f"setblock {x} {y} {z} {block}")


def arena(rcon, block, replace):
    x1, y1, z1, x2, y2, z2 = BOX
    for box in ((x1, y1, z1, x2, y1, z2), (x1, y2, z1, x2, y2, z2),  # floor, ceiling
                (x1, y1, z1, x1, y2, z2), (x2, y1, z1, x2, y2, z2),  # west, east walls
                (x1, y1, z1, x2, y2, z1), (x1, y1, z2, x2, y2, z2)):  # north, south walls
        run(rcon, "fill {} {} {} {} {} {} {} replace {}".format(*box, block, replace))


def position(rcon, player):
    reply = rcon.command(f"data get entity {player} Pos")
    numbers = re.findall(r"-?\d+(?:\.\d+)?", reply.split(":", 1)[-1])
    dim = rcon.command(f"data get entity {player} Dimension")
    return tuple(float(n) for n in numbers[:3]) if len(numbers) >= 3 and HUB in dim else None


def status(rcon, event_id):
    """The mod's /event status JSON: phase is lobby, running or none (over)."""
    try:
        return json.loads(rcon.command(f"event status {event_id}"))
    except json.JSONDecodeError:
        return {"phase": "none"}


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--join-seconds", type=int, default=20)
    parser.add_argument("--seconds", type=int, default=60)
    parser.add_argument("--prize", type=int, default=100)
    parser.add_argument("--players", nargs="*", default=[], help="join these players right away (e.g. said .join before the script started)")
    args = parser.parse_args()
    rcon = mc.connect()

    run(rcon, FORCELOAD)
    arena(rcon, "minecraft:barrier", "air")
    spec = {"from": "sky_rings", "lobby": max(1, args.join_seconds), "duration": args.seconds,
            "prizes": [{"place": 1, "coins": args.prize}] if args.prize > 0 else []}
    reply = rcon.command("event run " + json.dumps(spec))
    started = re.search(r"\(id (\S+)\)", reply)
    if not started:
        print(f"Couldn't start Sky Rings: {reply}", flush=True)
        arena(rcon, "minecraft:air", "barrier")
        run(rcon, FORCELOAD.replace("add", "remove"))
        return
    event_id = started.group(1)
    for player in args.players:
        rcon.command(f"event points {player} add 0 {event_id}")  # joins them
    print(f"Sky Rings is open for joining ({event_id})", flush=True)

    state = status(rcon, event_id)
    while state["phase"] == "lobby":
        time.sleep(0.5)
        state = status(rcon, event_id)

    rings = []
    if state["phase"] == "running":
        for _ in range(RING_COUNT):
            rings.append(new_ring(rings))
            place(rcon, rings[-1], "minecraft:gold_block")
        last = {}
        scores = {}
        tick = 0
        while state["phase"] == "running":
            scores = state.get("scores", scores)
            for player in state.get("players", []):
                now = position(rcon, player)
                before = last.get(player)
                last[player] = now
                if not now or not before:
                    continue
                if now[1] < BOX[1] + 2.5:  # landed on the arena floor: back to the start
                    run(rcon, f"tp {player} {START}")
                    last[player] = None
                    continue
                for i, ring in enumerate(rings):
                    if ring.crossed(before, now):
                        rcon.command(f"event points {player} add 1 {event_id}")
                        rcon.command(f"execute as {player} at @s run playsound minecraft:entity.player.levelup master @s ~ ~ ~ 0.7 1.8")
                        run(rcon, f"particle minecraft:totem_of_undying {ring.x + 0.5} {ring.y + 0.5} {ring.z + 0.5} 2 2 2 0.3 60 force")
                        place(rcon, ring, "minecraft:air")
                        rings[i] = new_ring(rings)
                        place(rcon, rings[i], "minecraft:gold_block")
                        break
            if tick % 3 == 0:
                for ring in rings:
                    spread = "0.1 1.5 1.5" if ring.axis == "x" else "1.5 1.5 0.1"
                    run(rcon, f"particle minecraft:end_rod {ring.x + 0.5} {ring.y + 0.5} {ring.z + 0.5} {spread} 0.01 6 force")
                state = status(rcon, event_id)
            tick += 1
            time.sleep(0.15)
        print(f"Finished: {scores}", flush=True)
    else:
        print("Sky Rings didn't start (nobody joined, or it was stopped)", flush=True)

    for ring in rings:
        place(rcon, ring, "minecraft:air")
    arena(rcon, "minecraft:air", "barrier")
    run(rcon, FORCELOAD.replace("add", "remove"))
    rcon.close()


if __name__ == "__main__":
    main()
