#!/usr/bin/env python3
"""Makes the giant player statue above the hub plaza (a Mannequin tagged skyrpg_statue) dance: it bobs,
crouches on the beat and spins, glowing gold with sparkles around it.

    python tools/fun/statue_dance.py           # dance until stopped (Ctrl+C or kill)
    python tools/fun/statue_dance.py --stop    # back to a still, unlit statue

"Lit up" is a gold glow outline (it joins the skyrunner team for the colour) plus hidden light blocks
around its body so it stays bright at night.
"""
import argparse
import math
import sys
import time
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent.parent))
import mc  # noqa: E402

STATUE = "@e[type=minecraft:mannequin,tag=skyrpg_statue,limit=1]"
X, Y, Z = 0.5, 88.0, 0.5
BEAT = 0.35
# Light rings around the statue's body (it is ~22 blocks tall at scale 12).
LIGHTS = [(round(X + 4 * math.cos(a)), y, round(Z + 4 * math.sin(a)))
          for y in (90, 96, 102, 108) for a in [i * math.pi / 3 for i in range(6)]]


def hub(rcon, command):
    return rcon.command(f"execute in skyrpg_hub:hub run {command}")


def light_up(rcon, on):
    for x, y, z in LIGHTS:
        if on:
            hub(rcon, f"setblock {x} {y} {z} minecraft:light[level=15] keep")
        else:
            hub(rcon, f"fill {x} {y} {z} {x} {y} {z} air replace minecraft:light")
    hub(rcon, f"data merge entity {STATUE} {{Glowing:{'1b' if on else '0b'}}}")
    hub(rcon, f"team join skyrunner {STATUE}" if on else f"team leave {STATUE}")


def stop(rcon):
    light_up(rcon, False)
    hub(rcon, f"tp {STATUE} {X} {Y} {Z} 0 0")
    hub(rcon, f'data merge entity {STATUE} {{pose:"standing"}}')


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--stop", action="store_true")
    args = parser.parse_args()
    rcon = mc.connect()
    if "No entity" in hub(rcon, f"data get entity {STATUE} Pos"):
        sys.exit("No statue (Mannequin tagged skyrpg_statue) in the hub.")
    if args.stop:
        stop(rcon)
        print("Statue is still again.")
        return
    light_up(rcon, True)
    print("Dancing. Stop with --stop (after killing this process).", flush=True)
    beat, yaw = 0, 0.0
    try:
        while True:
            beat += 1
            yaw = (yaw + 24) % 360
            bob = 0.6 * math.sin(beat * math.pi / 2)
            pose = "crouching" if beat % 2 else "standing"
            if beat % 16 in (14, 15):
                pose = "fall_flying"  # a little superhero flourish every few bars
            hub(rcon, f"tp {STATUE} {X} {Y + bob:.2f} {Z} {yaw:.0f} 0")
            hub(rcon, f'data merge entity {STATUE} {{pose:"{pose}"}}')
            hub(rcon, f"particle minecraft:end_rod {X} {Y + 11} {Z} 4 8 4 0.05 25 force")
            if beat % 4 == 0:
                hub(rcon, f"particle minecraft:firework {X} {Y + 22} {Z} 3 1 3 0.15 40 force")
            time.sleep(BEAT)
    except KeyboardInterrupt:
        stop(rcon)


if __name__ == "__main__":
    main()
