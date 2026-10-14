#!/usr/bin/env python3
"""Moves the hub NPCs to the spots the hub build reserves for them.

    python tools/builds/sync_npc_spots.py          # then /reload in game

The hub generator writes tools/builds/hub_spots.json (spot name -> x, y, z, yaw where an NPC stands);
this copies each spot into the matching NPC file under npcs/.../skyrpg/npcs/. Spots and NPCs without a
partner are listed so nothing silently stays in the old place.
"""
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent.parent
SPOTS = Path(__file__).with_name("hub_spots.json")
NPCS = ROOT / "npcs" / "src" / "main" / "resources" / "data" / "skyrpg_npcs" / "skyrpg" / "npcs"
# Spot name -> NPC file name, where they differ. The Grand Bazaar pavilions: stall_1..stall_4 are the weaponsmith, curio
# dealer, florist and provisioner; the spots "fisher" (Marina) and "slayer_master" (Morwen) are named like their NPC files.
NPC_FOR_SPOT = {
    "tavern": "tavern_keeper",
    "stall_1": "weaponsmith",
    "stall_2": "curio_dealer",
    "stall_3": "florist",
    "stall_4": "provisioner",
}


def main():
    spots = json.loads(SPOTS.read_text(encoding="utf-8"))
    used = set()
    for spot, where in spots.items():
        path = NPCS / f"{NPC_FOR_SPOT.get(spot, spot)}.json"
        if not path.exists():
            print(f"spot {spot}: no NPC file {path.name}")
            continue
        npc = json.loads(path.read_text(encoding="utf-8"))
        old = (npc["pos"], npc["yaw"])
        npc["pos"] = [where["x"], where["y"], where["z"]]
        npc["yaw"] = where["yaw"]
        path.write_text(json.dumps(npc, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
        used.add(path.name)
        print(f"{path.stem}: {old[0]} yaw {old[1]} -> {npc['pos']} yaw {npc['yaw']}")
    for path in sorted(NPCS.glob("*.json")):
        if path.name not in used:
            print(f"{path.stem}: no spot in hub_spots.json, left where it was")


if __name__ == "__main__":
    main()
