#!/usr/bin/env python3
"""The RCON-only Sky Rings, kept for servers without the skyrpg_events mod; tools/events/sky_rings.py is the
current one. A flying mini-event in the hub sky. Players type ".join" in chat, get an elytra and rockets,
and fly through gold rings; each ring you pass is a point, then it vanishes and a new one appears.

    python tools/events/legacy/sky_rings_rcon.py --join-seconds 20 --seconds 60 --prize 100 [--players Drogashh]
    python tools/events/legacy/sky_rings_rcon.py --restore            # give back inventories left over from a crash

Vanilla mechanics over RCON plus one hub hook: players tagged skyrpg.glide start gliding as soon as they
are in the air (the events mod does that now; it was HubProtection), so nobody has to jump. The arena is a barrier box above the hub build, so
nobody falls into the hub; landing on its floor lifts you back to the start. While playing, each player's
whole inventory is saved item by item (SNBT) in sky_rings_stash.json, which is only deleted once every
slot is verified back; --restore retries it (e.g. after a crash or for someone who logged off).
"""
import argparse
import json
import math
import random
import re
import sys
import time
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[2]))
import mc  # noqa: E402

HUB = "skyrpg_hub:hub"
LOG = mc.SERVER_DIR / "logs" / "latest.log"
STASH_FILE = Path(__file__).with_name("sky_rings_stash.json")
CHAT = re.compile(r"\(Minecraft\) (?:\[Not Secure\] )?<([^>]+)> (.*)$")
OBJECTIVE = "sr_rings"
BOSSBAR = "skyrpg:rings"
GLIDE_TAG = "skyrpg.glide"
RADIUS = 4
RING_COUNT = 6
BOX = (-50, 121, -50, 50, 170, 50)  # x1 y1 z1 x2 y2 z2, above the hub build (which ends at y 119)
START = "0 160 30 180 15"
FORCELOAD = "forceload add -64 -64 48 48"
# Every player slot an event takes over.
PLAYER_SLOTS = [f"container.{i}" for i in range(36)] + ["weapon.offhand", "armor.head", "armor.chest", "armor.legs", "armor.feet"]


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


def online(rcon, player):
    return "No entity" not in rcon.command(f"data get entity {player} Pos")


def position(rcon, player):
    reply = rcon.command(f"data get entity {player} Pos")
    numbers = re.findall(r"-?\d+(?:\.\d+)?", reply.split(":", 1)[-1])
    dim = rcon.command(f"data get entity {player} Dimension")
    return tuple(float(n) for n in numbers[:3]) if len(numbers) >= 3 and HUB in dim else None


def new_chat(offset):
    """Chat lines appended to the server log since offset."""
    with open(LOG, encoding="utf-8", errors="replace") as f:
        f.seek(offset)
        lines = f.read().splitlines()
        return [m.groups() for m in map(CHAT.search, lines) if m], f.tell()


def load_stash():
    return json.loads(STASH_FILE.read_text(encoding="utf-8")) if STASH_FILE.exists() else {}


def save_stash(stash):
    if stash:
        STASH_FILE.write_text(json.dumps(stash, indent=2), encoding="utf-8")
    else:
        STASH_FILE.unlink(missing_ok=True)


TEMP_BARREL = (304, 64, 304)  # far outside the hub build; only exists for a moment while restoring
MAX_ITEM_SNBT = 1300  # RCON drops commands longer than about 1446 bytes


def stash_inventory(rcon, player, stash):
    """Saves every item (as SNBT) to sky_rings_stash.json, and only then clears the inventory."""
    items = {}
    for slot in PLAYER_SLOTS:
        path = f"Inventory[{{Slot:{slot.split('.')[1]}b}}]" if slot.startswith("container.") else "equipment." + {
            "weapon.offhand": "offhand", "armor.head": "head", "armor.chest": "chest", "armor.legs": "legs", "armor.feet": "feet"}[slot]
        reply = rcon.command(f"data get entity {player} {path}")
        if "has the following entity data: " in reply:
            items[slot] = reply.split("has the following entity data: ", 1)[1].strip()
    if any(len(snbt) > MAX_ITEM_SNBT for snbt in items.values()):
        return False
    stash[player] = items
    save_stash(stash)
    rcon.command(f"clear {player}")
    return True


def restore_inventory(rcon, player, stash):
    """Puts the saved items back through a temporary barrel. Keeps the JSON entry if anything is off."""
    items = stash.get(player)
    if items is None or not online(rcon, player):
        return False
    x, y, z = TEMP_BARREL
    run(rcon, f"forceload add {x} {z}")
    rcon.command(f"clear {player}")
    restored = 0
    for slot, snbt in items.items():
        run(rcon, f"setblock {x} {y} {z} minecraft:barrel")
        body = re.sub(r"\bSlot: -?\d+b, |, Slot: -?\d+b\b", "", snbt)  # inventory items carry their slot, armor doesn't
        run(rcon, f"data modify block {x} {y} {z} Items set value [{{Slot: 0b, {body[1:]}]")
        run(rcon, f"item replace entity {player} {slot} from block {x} {y} {z} container.0")
        run(rcon, f"data modify block {x} {y} {z} Items set value []")
        if "passed" in rcon.command(f"execute if items entity {player} {slot} *"):
            restored += 1
    run(rcon, f"setblock {x} {y} {z} minecraft:air")
    run(rcon, f"forceload remove {x} {z}")
    if restored != len(items):
        print(f"WARNING: {player} got {restored}/{len(items)} slots back; their items stay in {STASH_FILE.name}", flush=True)
        return False
    del stash[player]
    save_stash(stash)
    return True


def equip(rcon, player, stash):
    if player not in stash and not stash_inventory(rcon, player, stash):
        rcon.command(f'tellraw {player} {{text:"Couldn\'t store your inventory safely, so you can\'t join this time.",color:"red"}}')
        return False
    rcon.command(f"item replace entity {player} armor.chest with minecraft:elytra[minecraft:unbreakable={{}}]")
    rcon.command(f"item replace entity {player} hotbar.0 with minecraft:firework_rocket 64")
    rcon.command(f"tag {player} add {GLIDE_TAG}")
    rcon.command(f"execute in {HUB} run tp {player} {START}")
    rcon.command(f'title {player} subtitle {{text:"Right-click rockets to boost, fly through the gold rings!",color:"yellow"}}')
    rcon.command(f'title {player} title {{text:"Sky Rings",color:"gold",bold:true}}')
    return True


def unequip(rcon, player, stash):
    rcon.command(f"tag {player} remove {GLIDE_TAG}")
    restore_inventory(rcon, player, stash)
    rcon.command(f"execute in {HUB} run tp {player} 0.5 65 10.5 180 0")


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--join-seconds", type=int, default=20)
    parser.add_argument("--seconds", type=int, default=60)
    parser.add_argument("--prize", type=int, default=100)
    parser.add_argument("--players", nargs="*", default=[], help="already joined (e.g. said .join before the script started)")
    parser.add_argument("--restore", action="store_true", help="only give back stashed inventories")
    args = parser.parse_args()
    rcon = mc.connect()
    stash = load_stash()
    run(rcon, FORCELOAD)
    for player in list(stash):  # leftovers from a run that didn't finish
        rcon.command(f"tag {player} remove {GLIDE_TAG}")
        print(f"Restored {player}" if restore_inventory(rcon, player, stash) else f"{player} is offline, still stashed", flush=True)
    if args.restore:
        run(rcon, FORCELOAD.replace("add", "remove"))
        return

    offset = LOG.stat().st_size
    players, scores = [], {}
    rcon.command(f'scoreboard objectives add {OBJECTIVE} dummy {{text:"Sky Rings",color:"gold",bold:true}}')
    rcon.command(f"scoreboard objectives setdisplay sidebar {OBJECTIVE}")
    rcon.command(f'bossbar add {BOSSBAR} {{text:"Sky Rings: type .join"}}')
    rcon.command(f"bossbar set {BOSSBAR} color purple")
    rcon.command(f"bossbar set {BOSSBAR} players @a")
    arena(rcon, "minecraft:barrier", "air")

    def add(name):
        players.append(name)
        scores[name] = 0
        rcon.command(f"scoreboard players set {name} {OBJECTIVE} 0")

    def drop(name):
        players.remove(name)
        scores.pop(name, None)
        rcon.command(f"scoreboard players reset {name} {OBJECTIVE}")

    def handle_chat():
        nonlocal offset
        lines, offset = new_chat(offset)
        for name, message in lines:
            name = name.split()[-1]  # drop team prefixes like [⚡ SKYRUNNER]
            if message.strip().lower() in (".join", "join", "!join") and name not in players:
                add(name)
                rcon.command(f'tellraw @a [{{text:"{name}",color:"gold"}},{{text:" joined Sky Rings!",color:"gray"}}]')
                rcon.command(f"execute as {name} at @s run playsound minecraft:entity.experience_orb.pickup master @s ~ ~ ~ 1 1.4")
                if started and not equip(rcon, name, stash):
                    drop(name)

    started = False
    for name in args.players:
        add(name)
        rcon.command(f'tellraw {name} {{text:"You\'re in Sky Rings.",color:"gold"}}')
    join_end = time.time() + args.join_seconds
    while (left := int(join_end - time.time())) > 0:
        handle_chat()
        rcon.command(f'bossbar set {BOSSBAR} name {{text:"Sky Rings starts in {left}s: type .join in chat",color:"light_purple"}}')
        if left in (15, 10, 5):
            rcon.command(f'tellraw @a {{text:"Sky Rings starts in {left}s. Type .join to play!",color:"light_purple"}}')
        time.sleep(0.5)

    rings = []
    if not players:
        rcon.command('tellraw @a {text:"Nobody joined Sky Rings, maybe next time!",color:"gray"}')
        cleanup(rcon, [], rings, stash)
        return
    for _ in range(RING_COUNT):
        rings.append(new_ring(rings))
        place(rcon, rings[-1], "minecraft:gold_block")
    started = True
    for player in list(players):
        if not equip(rcon, player, stash):
            drop(player)
    rcon.command(f"bossbar set {BOSSBAR} color yellow")
    rcon.command(f"bossbar set {BOSSBAR} max {args.seconds}")
    last = {p: position(rcon, p) for p in players}
    end = time.time() + args.seconds
    tick = 0
    while (left := end - time.time()) > 0:
        handle_chat()
        for player in list(players):
            now = position(rcon, player)
            before = last.get(player)
            last[player] = now
            if not now or not before:
                continue
            if now[1] < BOX[1] + 2.5:  # landed on the arena floor: back to the start
                rcon.command(f"execute in {HUB} run tp {player} {START}")
                last[player] = None
                continue
            for i, ring in enumerate(rings):
                if ring.crossed(before, now):
                    scores[player] += 1
                    rcon.command(f"scoreboard players add {player} {OBJECTIVE} 1")
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
            seconds = math.ceil(left)
            rcon.command(f"bossbar set {BOSSBAR} value {seconds}")
            rcon.command(f'bossbar set {BOSSBAR} name {{text:"Sky Rings  0:{seconds:02d}",color:"gold"}}')
        tick += 1
        time.sleep(0.15)

    best = max(scores.values(), default=0)
    winners = [p for p, s in scores.items() if s == best and best > 0]
    ranking = ", ".join(f"{p} {s}" for p, s in sorted(scores.items(), key=lambda kv: -kv[1]))
    for winner in winners:
        rcon.command(f"coins give {winner} {args.prize}")
    if winners:
        names = " & ".join(winners)
        rcon.command(f'title @a subtitle {{text:"{best} rings, +{args.prize} coins",color:"yellow"}}')
        rcon.command(f'title @a title {{text:"{names} wins!",color:"gold",bold:true}}')
        rcon.command(f'tellraw @a [{{text:"Sky Rings is over! ",color:"gold"}},{{text:"{names}",color:"white"}},{{text:" wins {args.prize} coins with {best} rings. ({ranking})",color:"gray"}}]')
    else:
        rcon.command('tellraw @a {text:"Sky Rings is over. Nobody hit a ring!",color:"gray"}')
    rcon.command("execute as @a at @s run playsound minecraft:ui.toast.challenge_complete master @s ~ ~ ~ 1 1")
    cleanup(rcon, players, rings, stash)
    print(f"Finished: {ranking}; winners {winners}", flush=True)


def cleanup(rcon, players, rings, stash):
    for ring in rings:
        place(rcon, ring, "minecraft:air")
    for player in players:
        unequip(rcon, player, stash)
    arena(rcon, "minecraft:air", "barrier")
    time.sleep(4)
    rcon.command(f"bossbar remove {BOSSBAR}")
    rcon.command(f"scoreboard objectives remove {OBJECTIVE}")
    run(rcon, FORCELOAD.replace("add", "remove"))
    if stash:
        print(f"Still stashed (offline?): {', '.join(stash)}. Run with --restore when they're back.", flush=True)
    rcon.close()


if __name__ == "__main__":
    main()
