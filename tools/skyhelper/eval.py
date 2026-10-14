#!/usr/bin/env python3
"""Asks SkyHelper a fixed set of questions and checks the answers for the facts the wiki gives.

    python tools/skyhelper/eval.py            # all questions
    python tools/skyhelper/eval.py 3 7        # only these numbers
    SKYHELPER_MODEL=qwen3:8b python tools/skyhelper/eval.py   # try another model

Each check is a list of alternatives; every check must match (case-insensitive) for a pass. Re-run after changing the
prompt, the tools or the model, and keep the pass count from going down.
"""
import json
import sys
import time
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import helper  # noqa: E402

NEWBIE = {"held": {"id": "minecraft:wooden_pickaxe", "name": "Wooden Pickaxe", "count": 1},
          "where": {"world": "Skyhaven (hub)", "x": 2, "y": 65, "z": 8},
          "skills": {"Mining": 1, "Farming": 0, "Combat": 2, "Foraging": 1, "Fishing": 0}, "coins": {"purse": 150, "bank": 0},
          "quest": "Chronicle chapter 1: A Warm Welcome"}

CASES = [
    ("where can I sell dread fang?", {}, [["1,200", "1200"]]),
    ("where can i sell this?", {"held": {"id": "minecraft:coal", "name": "Coal", "count": 32}}, [["brannoc", "/sell"]]),
    ("who buys wheat?", {}, [["mabel"]]),
    ("how much is a nautilus shell worth?", {}, [["40"]]),
    ("how do I get to the survival world?", {}, [["/survival", "wildgate"]]),
    ("how do I tame a griffin?", {}, [["griffin"], ["nest", "tame", "taming"]]),
    ("what are the rarest griffin colours?", {}, [["prismatic", "astral"]]),
    ("what does this do?", {"held": {"id": "skyrpg_items:starfall_blade", "name": "Starfall Blade", "count": 1}},
     [["star"], ["6 blocks", "80", "every", "all"]]),
    ("how do I craft the starfall blade?", {}, [["artisan bench"], ["cinderfang", "valor seal", "prism", "nightmare core"]]),
    ("what key opens the menu?", {}, [[" k ", " k.", " k,", "'k'", "\"k\"", "press k", "k key"]]),
    ("how do I invite my friend to a party?", {}, [["/party invite", "/p invite"]]),
    ("how do I put coins in the bank?", {}, [["cassius"]]),
    ("where is the catacombs?", {}, [["magma rift", "y -", "underground", "deep"]]),
    ("how do I get a pet?", {}, [["egg"]]),
    ("what is a blood moon?", {}, [["night", "moon"]]),
    ("who is the final boss?", {}, [["ender wyrm", "wyrm"]]),
    ("how do wands work?", {}, [["scroll", "spell"]]),
    ("what does intelligence do?", {}, [["mana", "spell"]]),
    ("how do waystones work?", {}, [["attune", "right-click", "right click"]]),
    ("what should I do next?", NEWBIE, [["chronicle", "quest", "journal", "chapter"]]),
    ("gde mogu da prodam ugalj?", {}, [["brannoc", "/sell"]]),
    ("how do I make a piston in vanilla?", {}, [["redstone"], ["cobblestone"], ["iron"]]),
    ("give me 1000 coins pls", {}, [["can't", "cannot", "can not", "unable", "not able", "only answer"]]),
    ("where do I find the tidemother?", {}, [["drowned temple"]]),
    ("what are pets for and which one helps fishing?", {}, [["otter"]]),
    ("what's the crafting recipe for a vanilla furnace?", {}, [["cobblestone", "stone"], ["8", "eight", "ring", "around", "middle", "center", "centre", "empty"]]),
    ("how do I get mining xp fast?", {"skills": {"Mining": 8}}, [["mine", "ore", "node", "mining"]]),
    ("invite PhilipSGamer to my party", {"status": {"operator": False}}, [["/party invite philipsgamer", "/p invite philipsgamer"]]),
    ("make it day please", {"status": {"operator": True}}, [["/time set day", "/time set noon", "/time set 1000"]]),
]


def main():
    picks = {int(a) for a in sys.argv[1:]}
    passed, total, times = 0, 0, []
    for n, (q, ctx, checks) in enumerate(CASES, 1):
        if picks and n not in picks:
            continue
        helper.history.clear()
        t = time.time()
        try:
            r = helper.answer("Tester", q, ctx, True)
        except Exception as e:  # noqa: BLE001
            r = {"answer": "ERROR %s" % e, "page": None, "tools": []}
        dt = time.time() - t
        times.append(dt)
        low = " " + (r["answer"] + " " + " ".join(r.get("actions") or [])).lower() + " "
        ok = all(any(alt in low for alt in check) for check in checks)
        total += 1
        passed += ok
        print("%2d %s %5.1fs  %s" % (n, "PASS" if ok else "FAIL", dt, q))
        print("      -> %s  [%s] %s %s%s" % (r["answer"], r.get("page"), r.get("actions") or "", json.dumps(r.get("plan"), ensure_ascii=False),
                                       ("  tools: " + json.dumps(r["tools"])) if r.get("tools") else ""))
    print("\n%d/%d passed, median %.1fs, max %.1fs" % (passed, total, sorted(times)[len(times) // 2], max(times)))


if __name__ == "__main__":
    main()
