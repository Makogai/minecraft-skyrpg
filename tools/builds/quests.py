#!/usr/bin/env python3
"""Generates the Skyhaven Chronicle: quests (data/skyrpg_progress/skyrpg/quests/<chapter>/<id>.json, plus _chapter.json) and
their conversations (data/skyrpg_progress/skyrpg/dialogue/<chapter>/<quest>/<offer|progress|complete|talk_N>.json).

Run with:  python tools/builds/quests.py
It deletes and rewrites both folders, so edit the story files, not the JSON:
  quests_dsl.py       objective builders and the dialogue page format
  quests_ch1_2.py     the guided introduction (Skyhaven, the Masters, the Wildgate, first steps)
  quests_ch3_8.py     dungeons, slayers, enchanting, the Deep, the Nether, ruins and the End
Ids, NPC positions, dungeon bosses/structures and deep-layer heights are read from the mods' data files.
See docs/design/journal.md for the design.
"""
import json
import re
import shutil
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))

from quests_ch1_2 import CHAPTERS_A
from quests_ch3_8 import CHAPTERS_B
from quests_dsl import DIALOGUE_OUT, NPCS, NS, OUT, conv, npc_id

CHAPTERS = CHAPTERS_A + CHAPTERS_B

SHOP_LABELS = {"banker": "Open my account.", "enchanter": "Show me the enchanting.", "slayer_master": "Show me the contracts."}


def write(path, data):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(data, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")


def plain(text):
    return re.sub(r"&[0-9a-fk-or]", "", text)


def progress_pages(quest):
    labels = "; ".join(o["label"] for o in quest["objectives"])
    text = f"&7Still working on &6{quest['title']}&7? What's left: &f{labels}&7. &r{quest['tip']}".strip()
    return [text]


def main():
    for folder in (OUT, DIALOGUE_OUT):
        if folder.exists():
            shutil.rmtree(folder)
    previous = []
    count = 0
    pages = 0
    for order, (folder, title, icon, blurb, steps) in enumerate(CHAPTERS, 1):
        write(OUT / folder / "_chapter.json", {"title": title, "order": order, "description": blurb, "icon": icon})
        for number, step in enumerate(steps):
            for q in step:
                giver, turn_in = q["giver"], q["turn_in"]
                kinds = []
                base = DIALOGUE_OUT / folder / q["id"]
                if q["offer"]:
                    choices = None
                    if giver:
                        choices = [{"label": q["accept"], "action": "accept"}, {"label": q["decline"], "action": "close"}]
                    c = conv(giver, q["offer"], choices)
                    write(base / "offer.json", c)
                    kinds.append("offer")
                    pages += len(c["pages"])
                if giver and q["objectives"]:
                    c = conv(giver, progress_pages(q), [{"label": "I'll get back to it.", "action": "end"}])
                    write(base / "progress.json", c)
                    kinds.append("progress")
                    pages += len(c["pages"])
                if turn_in:
                    assert q["complete"], f"{q['id']} is handed in to {turn_in} but has no hand-in dialogue"
                    choices = [{"label": q["thanks"], "action": "turn_in"}]
                    if q["shop"]:
                        choices.append({"label": SHOP_LABELS.get(turn_in, "Show me your wares."), "action": "shop"})
                    c = conv(turn_in, q["complete"], choices)
                    write(base / "complete.json", c)
                    kinds.append("complete")
                    pages += len(c["pages"])
                for index, items in q["talk"].items():
                    npc = q["objectives"][index]["npc"].split(":")[1]
                    c = conv(npc, items)
                    write(base / f"talk_{index}.json", c)
                    kinds.append(f"talk_{index}")
                    pages += len(c["pages"])
                first = q["offer"][0] if q["offer"] else ""
                first = first[1] if isinstance(first, tuple) else first.get("text", "") if isinstance(first, dict) else first
                data = {
                    "title": q["title"],
                    "description": q["desc"],
                    "giver": {"npc": npc_id(giver) if giver else "narrator", "name": NPCS[giver]["name"] if giver else "Journal",
                              "text": plain(first)},
                    "turn_in": {"npc": npc_id(turn_in) if turn_in else "none", "name": NPCS[turn_in]["name"] if turn_in else ""},
                    "objectives": q["objectives"],
                    "rewards": {"coins": q["coins"], "xp": q["xp"], "items": [{"id": i, "count": c} for i, c in q["items"]]},
                    "prerequisites": [f"{NS}:{p}" for p in previous],
                    "order": number,
                    "dialogue": kinds,
                }
                if q["replaces"]:
                    data["replaces"] = q["replaces"]
                if q["hint"]:
                    data["rewards"]["hint"] = q["hint"]
                if q["side"]:
                    data["side"] = True
                write(OUT / folder / f"{q['id']}.json", data)
                count += 1
            previous = [f"{folder}/{q['id']}" for q in step]
    print(f"Wrote {len(CHAPTERS)} chapters, {count} quests and {pages} dialogue pages")


if __name__ == "__main__":
    main()
