#!/usr/bin/env python3
"""Skyrunner Gauntlet: an obstacle-course race in the hub sky, east of Skyhaven (x 296..432, y 100).

    python tools/events/skyrunner.py --build            # (re)build the course, no event
    python tools/events/skyrunner.py [--join A B] [--lobby 20] [--seconds 180] [--first-coins 100000]

Sections, west to east: Hop Lanes (platforms over the void), the Sweeper (a bar spinning around a pivot
that you jump over), Crumble Field (tiles drop shortly after you step on them), the Wave Bridge (two sets
of segments blinking in turn), the Climb (scaffolding) and the Ice Slide down to the finish arch.

Runs on the skyrpg_events mod (lobby, .join, stashed inventories, start_at/return, podium, prizes); this
script builds the course, animates it, respawns fallen players at their last checkpoint, keeps everyone
in adventure mode during the race and scores: +10 per checkpoint, finishing +100/+70/+50/+30. The winner
also joins the "skyrunner" team, whose prefix is the gold [⚡ SKYRUNNER] title.
"""
import argparse
import json
import math
import sys
import time
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent.parent))
import mc  # noqa: E402

HUB = "skyrpg_hub:hub"
EVENT = "skyrunner"
TEAM = "skyrunner"
DECO = "skyrpg_race_deco"
FLOOR = 100            # every walkway's top block is at y=100, so standing players are at y=101.0
FALL_Y = 97
START = (304.5, 101, 0.5)
# Checkpoints: (name, x where the section starts, respawn x, y, z)
CHECKPOINTS = [("Sweeper", 337, 337.5, 101, -7.5), ("Crumble Field", 355, 355.5, 101, 0.5),
               ("Wave Bridge", 374, 374.5, 101, 0.5), ("The Climb", 398, 399.5, 101, 0.5)]
FINISH_X = 423.5
PIVOT = (345, 0)
SWEEP_RADIUS = 8
CRUMBLE = (357, -6, 8, 6)  # x0, z0, tiles along x, tiles along z (2x2 each)
TILE_COLORS = ("lime_terracotta", "cyan_terracotta")
BRIDGE_X0, BRIDGE_SEGMENTS = 376, 11
BRIDGE_COLORS = ("blue_concrete", "purple_concrete")
HOPS = [(314, -2, 100, "lime_concrete"), (319, 1, 100, "light_blue_concrete"), (324, -1, 101, "yellow_concrete"),
        (329, 2, 101, "pink_concrete"), (334, 0, 100, "orange_concrete")]
PLACE_BONUS = [100, 70, 50]
CHECKPOINT_POINTS = 10


def hub(rcon, command):
    return rcon.command(f"execute in {HUB} run {command}")


def fill(rcon, x1, y1, z1, x2, y2, z2, block, mode=""):
    return hub(rcon, f"fill {x1} {y1} {z1} {x2} {y2} {z2} {block} {mode}".strip())


# ---------------------------------------------------------------- the course
def gate(rcon, closed):
    fill(rcon, 310, 101, -5, 310, 103, 5, "red_stained_glass" if closed else "air")


def tiles_all(rcon):
    x0, z0, nx, nz = CRUMBLE
    for i in range(nx):
        for j in range(nz):
            x, z = x0 + 2 * i, z0 + 2 * j
            fill(rcon, x, FLOOR, z, x + 1, FLOOR, z + 1, TILE_COLORS[(i + j) % 2])


def bridge(rcon, group, on):
    for k in range(group, BRIDGE_SEGMENTS, 2):
        x = BRIDGE_X0 + 2 * k
        fill(rcon, x, FLOOR, -1, x + 1, FLOOR, 1, BRIDGE_COLORS[group] if on else "air")


def build(rcon):
    hub(rcon, "forceload add 296 -12 432 12")
    for x in range(296, 433, 40):
        fill(rcon, x, 96, -12, min(x + 39, 432), 115, 12, "air")
    # Start pad, glass rails and the gate.
    fill(rcon, 300, FLOOR, -5, 310, FLOOR, 5, "white_concrete")
    fill(rcon, 309, FLOOR, -5, 309, FLOOR, 5, "yellow_concrete")
    fill(rcon, 310, FLOOR, -5, 310, FLOOR, 5, "gold_block")
    for z in (-6, 6):
        fill(rcon, 299, 101, z, 310, 102, z, "white_stained_glass")
    fill(rcon, 299, 101, -6, 299, 102, 6, "white_stained_glass")
    gate(rcon, True)
    # Hop Lanes.
    for x, z, y, block in HOPS:
        fill(rcon, x - 1, y, z - 1, x + 1, y, z + 1, block)
    # The Sweeper's arena and pivot.
    fill(rcon, 337, FLOOR, -8, 353, FLOOR, 8, "light_gray_concrete")
    fill(rcon, 337, FLOOR, -8, 337, FLOOR, 8, "gold_block")
    hub(rcon, f"setblock {PIVOT[0]} 101 {PIVOT[1]} polished_blackstone")
    hub(rcon, f"setblock {PIVOT[0]} 102 {PIVOT[1]} end_rod")
    # Crumble Field: a safe strip, then the tiles.
    fill(rcon, 355, FLOOR, -6, 356, FLOOR, 5, "white_concrete")
    fill(rcon, 355, FLOOR, -6, 355, FLOOR, 5, "gold_block")
    tiles_all(rcon)
    # Wave Bridge.
    fill(rcon, 374, FLOOR, -3, 375, FLOOR, 3, "white_concrete")
    fill(rcon, 374, FLOOR, -3, 374, FLOOR, 3, "gold_block")
    bridge(rcon, 0, True)
    bridge(rcon, 1, True)
    # The Climb: landing, a wall, scaffolding up to the top deck.
    fill(rcon, 398, FLOOR, -3, 401, FLOOR, 3, "white_concrete")
    fill(rcon, 398, FLOOR, -3, 398, FLOOR, 3, "gold_block")
    fill(rcon, 402, 101, -3, 402, 106, 3, "quartz_bricks")
    fill(rcon, 402, 107, -3, 405, 107, 3, "smooth_quartz")
    for z in (-2, 0, 2):
        fill(rcon, 401, 101, z, 401, 107, z, "scaffolding")
    # Ice Slide: blue ice steps down to the finish, with glass rails.
    for s in range(7):
        x, y = 406 + 2 * s, 106 - s
        fill(rcon, x, y, -2, x + 1, y, 2, "blue_ice")
        for z in (-3, 3):
            fill(rcon, x, y + 1, z, x + 1, y + 2, z, "light_blue_stained_glass")
    # Finish: floor, checkered line, arch.
    fill(rcon, 420, FLOOR, -4, 430, FLOOR, 4, "yellow_concrete")
    for z in range(-4, 5):
        hub(rcon, f"setblock 423 {FLOOR} {z} {'black_concrete' if z % 2 else 'white_concrete'}")
    for z in (-5, 5):
        fill(rcon, 423, 101, z, 423, 105, z, "gold_block")
    fill(rcon, 423, 106, -5, 423, 106, 5, "gold_block")
    # Signs in the sky.
    hub(rcon, f"kill @e[tag={DECO}]")
    hub(rcon, f'summon text_display 305 106 0.5 {{Tags:["{DECO}"],billboard:"center",text:{{text:"SKYRUNNER GAUNTLET",color:"gold",bold:true}},'
              f'transformation:{{scale:[3f,3f,3f],translation:[0f,0f,0f],left_rotation:[0f,0f,0f,1f],right_rotation:[0f,0f,0f,1f]}}}}')
    hub(rcon, f'summon text_display 423 107.5 0.5 {{Tags:["{DECO}"],billboard:"center",text:{{text:"FINISH",color:"yellow",bold:true}},'
              f'transformation:{{scale:[3f,3f,3f],translation:[0f,0f,0f],left_rotation:[0f,0f,0f,1f],right_rotation:[0f,0f,0f,1f]}}}}')
    # The winner's title.
    rcon.command(f"team add {TEAM}")
    rcon.command(f'team modify {TEAM} prefix [{{text:"[",color:"dark_gray"}},{{text:"⚡ SKYRUNNER",color:"gold",bold:true}},{{text:"] ",color:"dark_gray"}}]')
    rcon.command(f"team modify {TEAM} color yellow")


# ---------------------------------------------------------------- moving parts
class Sweeper:
    def __init__(self):
        self.angle, self.blocks = 0.0, set()

    def step(self, rcon, degrees):
        self.angle = (self.angle + degrees) % 360
        a = math.radians(self.angle)
        new = set()
        for r in range(1, SWEEP_RADIUS + 1):
            for sign in (-1, 1):
                new.add((PIVOT[0] + round(sign * r * math.cos(a)), PIVOT[1] + round(sign * r * math.sin(a))))
        for x, z in self.blocks - new:
            hub(rcon, f"setblock {x} 101 {z} air")
        for x, z in new - self.blocks:
            hub(rcon, f"setblock {x} 101 {z} red_concrete")
        self.blocks = new

    def clear(self, rcon):
        for x, z in self.blocks:
            hub(rcon, f"setblock {x} 101 {z} air")
        self.blocks = set()


class Crumble:
    CRACK, DROP, BACK = 0.45, 0.45, 3.0  # seconds: cracking, then gone, then back

    def __init__(self):
        self.tiles = {}  # (i, j) -> (state, since)

    def step_on(self, rcon, x, z, now):
        x0, z0, nx, nz = CRUMBLE
        i, j = (math.floor(x) - x0) // 2, (math.floor(z) - z0) // 2
        if 0 <= i < nx and 0 <= j < nz and (i, j) not in self.tiles:
            self.tiles[(i, j)] = ("crack", now)
            self.paint(rcon, i, j, "yellow_terracotta")

    def tick(self, rcon, now):
        for (i, j), (state, since) in list(self.tiles.items()):
            if state == "crack" and now - since >= self.CRACK:
                self.tiles[(i, j)] = ("gone", now)
                self.paint(rcon, i, j, "air")
            elif state == "gone" and now - since >= self.BACK:
                del self.tiles[(i, j)]
                self.paint(rcon, i, j, TILE_COLORS[(i + j) % 2])

    @staticmethod
    def paint(rcon, i, j, block):
        x, z = CRUMBLE[0] + 2 * i, CRUMBLE[1] + 2 * j
        fill(rcon, x, FLOOR, z, x + 1, FLOOR, z + 1, block)


class WaveBridge:
    CYCLE = 2.4

    def __init__(self):
        self.on = [True, True]

    def tick(self, rcon, now):
        t = now % self.CYCLE
        want = [t < 1.4, t >= 1.2 or t < 0.2]
        for group in (0, 1):
            if want[group] != self.on[group]:
                bridge(rcon, group, want[group])
                self.on[group] = want[group]


def reset(rcon, sweeper):
    sweeper.clear(rcon)
    tiles_all(rcon)
    bridge(rcon, 0, True)
    bridge(rcon, 1, True)
    gate(rcon, True)


# ---------------------------------------------------------------- the race
def spec(seconds, lobby, first_coins):
    return {
        "id": EVENT, "name": "Skyrunner Gauntlet", "description": "Race the obstacle course! First across the finish line wins.",
        "color": "#FFB627", "icon": "minecraft:feather", "duration": f"{seconds}s", "unit": "pts",
        "participation": "opt_in", "lobby": f"{lobby}s", "chat_join": True, "stash_inventory": True,
        "start_at": f"{HUB} {START[0]} {START[1]} {START[2]} -90 0", "return": True,
        "scoring": [{"type": "custom"}],
        "prizes": [
            {"place": 1, "coins": first_coins, "commands": [f"team join {TEAM} {{player}}"],
             "label": f"{first_coins:,} coins + ⚡ SKYRUNNER title"},
            {"place": 2, "coins": 25000}, {"place": 3, "coins": 10000}, {"place": "all", "coins": 2500},
        ],
        "announcements": [60, 30, 10],
    }


def status(rcon):
    reply = rcon.command(f"event status {EVENT}")
    try:
        return json.loads(reply[reply.index("{"):])
    except ValueError:
        return {"phase": "none"}


def position(rcon, player):
    reply = rcon.command(f"data get entity {player} Pos")
    if "has the following" not in reply:
        return None
    nums = reply.split(": ", 1)[1].strip("[]\n ").replace("d", "").split(",")
    dim = rcon.command(f"data get entity {player} Dimension")
    return tuple(float(n) for n in nums[:3]) if HUB in dim else None


def say_title(rcon, who, title, subtitle="", color="gold"):
    rcon.command(f'title {who} times 0 25 8')
    rcon.command(f'title {who} subtitle {{text:"{subtitle}",color:"yellow"}}')
    rcon.command(f'title {who} title {{text:"{title}",color:"{color}",bold:true}}')


def race(rcon, args):
    print(rcon.command("event run " + json.dumps(spec(args.seconds, args.lobby, args.first_coins), separators=(",", ":"))), flush=True)
    for player in args.join:
        print(rcon.command(f"event points {player} add 0 {EVENT}"), flush=True)
    while status(rcon)["phase"] == "lobby":
        time.sleep(0.5)
    state = status(rcon)
    if state["phase"] != "running":
        print("Event didn't start:", state)
        return
    players = list(state["players"])
    modes = {}
    for p in players:
        reply = rcon.command(f"data get entity {p} playerGameType")
        modes[p] = {"0": "survival", "1": "creative", "2": "adventure", "3": "spectator"}.get(reply.strip()[-1:], "survival")
        rcon.command(f"gamemode adventure {p}")
        rcon.command(f"effect clear {p}")
    sweeper, crumble, wave = Sweeper(), Crumble(), WaveBridge()
    reset(rcon, sweeper)
    for n in ("3", "2", "1"):
        say_title(rcon, "@a", n, "Get ready...")
        rcon.command("execute as @a at @s run playsound minecraft:block.note_block.pling master @s ~ ~ ~ 1 1")
        for _ in range(5):
            sweeper.step(rcon, 12)
            time.sleep(0.2)
    gate(rcon, False)
    say_title(rcon, "@a", "GO!", "First to the finish wins!", "green")
    rcon.command("execute as @a at @s run playsound minecraft:block.note_block.pling master @s ~ ~ ~ 1 2")

    checkpoint = {p: 0 for p in players}  # index into [start] + CHECKPOINTS
    finished = []
    began = time.time()
    while True:
        state = status(rcon)
        if state["phase"] != "running":
            break
        for p in state.get("players", []):
            if p not in checkpoint:  # joined late
                checkpoint[p] = 0
                modes.setdefault(p, "survival")
                rcon.command(f"gamemode adventure {p}")
                hub(rcon, f"tp {p} {START[0]} {START[1]} {START[2]} -90 0")
        now = time.time()
        for p in list(checkpoint):
            if p in finished:
                continue
            pos = position(rcon, p)
            if pos is None:
                continue
            x, y, z = pos
            if y < FALL_Y:
                spot = START if checkpoint[p] == 0 else CHECKPOINTS[checkpoint[p] - 1][2:]
                hub(rcon, f"tp {p} {spot[0]} {spot[1]} {spot[2]} -90 0")
                rcon.command(f"execute as {p} at @s run playsound minecraft:entity.chicken.egg master @s ~ ~ ~ 1 0.6")
                continue
            standing = abs(y - 101.0) < 0.05
            while standing and checkpoint[p] < len(CHECKPOINTS) and x >= CHECKPOINTS[checkpoint[p]][1]:
                name = CHECKPOINTS[checkpoint[p]][0]
                checkpoint[p] += 1
                rcon.command(f"event points {p} add {CHECKPOINT_POINTS} {EVENT}")
                rcon.command(f'title {p} actionbar {{text:"Checkpoint: {name}",color:"aqua"}}')
                rcon.command(f"execute as {p} at @s run playsound minecraft:entity.experience_orb.pickup master @s ~ ~ ~ 1 1.2")
            if standing and CRUMBLE[0] <= x < CRUMBLE[0] + 2 * CRUMBLE[2]:
                crumble.step_on(rcon, x, z, now)
            if x >= FINISH_X and y >= 99:
                finished.append(p)
                place = len(finished)
                rcon.command(f"event points {p} add {PLACE_BONUS[place - 1] if place <= 3 else 30} {EVENT}")
                ordinal = {1: "1st", 2: "2nd", 3: "3rd"}.get(place, f"{place}th")
                rcon.command(f'tellraw @a [{{text:"⚑ ",color:"gold"}},{{text:"{p}",color:"white"}},{{text:" finished {ordinal}! ({now - began:.1f}s)",color:"yellow"}}]')
                say_title(rcon, p, f"{ordinal}!", f"Finished in {now - began:.1f}s")
                hub(rcon, f"particle minecraft:firework {x} {y + 1} {z} 1 1 1 0.2 80 force")
                rcon.command(f"execute as {p} at @s run playsound minecraft:ui.toast.challenge_complete master @s ~ ~ ~ 1 1")
        sweeper.step(rcon, 12)
        crumble.tick(rcon, now)
        wave.tick(rcon, now)
        if finished and len(finished) >= len(checkpoint):
            time.sleep(2)
            rcon.command(f"event end {EVENT}")
            break
        time.sleep(0.15)

    for p, mode in modes.items():
        rcon.command(f"gamemode {mode} {p}")
    reset(rcon, sweeper)
    print("Skyrunner finished:", finished, status(rcon), flush=True)


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--build", action="store_true", help="only (re)build the course")
    parser.add_argument("--seconds", type=int, default=180)
    parser.add_argument("--lobby", type=int, default=20)
    parser.add_argument("--first-coins", type=int, default=100000)
    parser.add_argument("--join", nargs="*", default=[])
    args = parser.parse_args()
    rcon = mc.connect()
    if args.build:
        build(rcon)
        print("Course built.")
        return
    hub(rcon, "forceload add 296 -12 432 12")
    try:
        race(rcon, args)
    finally:
        hub(rcon, "forceload remove 296 -12 432 12")
        rcon.close()


if __name__ == "__main__":
    main()
