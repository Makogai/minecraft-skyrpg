"""Building blocks for the Skyhaven Chronicle generator (quests.py): objective builders and the dialogue DSL.

A page is a string, a (mood, text) tuple, or a dict {id, text, mood, choices}. Text takes &6-style colour codes and {player}.
See docs/design/journal.md.
"""
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
OUT = ROOT / "progress/src/main/resources/data/skyrpg_progress/skyrpg/quests"
DIALOGUE_OUT = ROOT / "progress/src/main/resources/data/skyrpg_progress/skyrpg/dialogue"
NS = "skyrpg_progress"
NPC_DIR = ROOT / "npcs/src/main/resources/data/skyrpg_npcs/skyrpg/npcs"
DUNGEON_DIR = ROOT / "world/src/main/resources/data/skyrpg_world/skyrpg/dungeons"
LAYER_DIR = ROOT / "world/src/main/resources/data/skyrpg_world/skyrpg/deep_layers"
COLLECTION_DIR = ROOT / "progress/src/main/resources/data/skyrpg_progress/skyrpg/collections"

NPCS = {p.stem: json.loads(p.read_text(encoding="utf-8")) for p in NPC_DIR.glob("*.json")}
DUNGEONS = {p.stem: json.loads(p.read_text(encoding="utf-8")) for p in DUNGEON_DIR.glob("*.json")}
LAYERS = {p.stem: json.loads(p.read_text(encoding="utf-8")) for p in LAYER_DIR.glob("*.json")}
COLLECTIONS = {p.stem: json.loads(p.read_text(encoding="utf-8")) for p in COLLECTION_DIR.glob("*.json")}

DUNGEON_DIMENSION = {"infernal_citadel": "minecraft:the_nether", "astral_observatory": "minecraft:the_end"}
SKILL_NAMES = {"mining": "Mining", "farming": "Farming", "foraging": "Foraging", "fishing": "Fishing", "combat": "Combat",
               "delving": "Delving"}
PET_ITEMS = [f"skyrpg_pets:{p}_pet" for p in ("mole", "bee", "owl", "pup", "otter")]


# ---------------------------------------------------------------- objective builders

def talk(npc):
    d = NPCS[npc]
    return {"type": "talk", "label": f"Talk to {d['name']}, the {d['role']}", "target": 1, "npc": f"skyrpg_npcs:{npc}",
            "track": {"pos": d["pos"], "dimension": d["dimension"], "name": d["name"]}}


def skill(name, level):
    ns = "skyrpg_skills"
    return {"type": "skill_level", "label": f"Reach {SKILL_NAMES[name]} level {level}", "target": level, "skill": f"{ns}:{name}"}


def collect(coll, count):
    assert coll in COLLECTIONS, f"unknown collection {coll}"
    return {"type": "collection", "label": f"Gather {count:,} {COLLECTIONS[coll]['name']}", "target": count, "id": f"{NS}:{coll}"}


def kill(entity, count, what):
    return {"type": "kill", "label": f"Defeat {count} {what}", "target": count, "entity": entity}


def dungeon(key, extra_label=None):
    keys = key if isinstance(key, list) else [key]
    ds = [DUNGEONS[k] for k in keys]
    names = " or ".join(d["name"].removeprefix("The ") for d in ds)
    o = {"type": "dungeon_clear", "label": extra_label or f"Clear {names}", "target": 1,
         "dungeon": ",".join(f"skyrpg_world:{k}" for k in keys), "bosses": [d["boss"] for d in ds],
         "structure": ",".join(d["structure"] for d in ds), "name": names}
    dim = DUNGEON_DIMENSION.get(keys[0])
    if dim:
        o["dimension"] = dim
    return o


def slayer(tier, started=False, line=""):
    verb = "Start a" if started else "Finish a"
    return {"type": "slayer_tier", "label": f"{verb} Slayer quest of tier {tier}" if tier > 1 or not started else "Start your first Slayer quest",
            "target": tier, "tier": tier, "started": started, "line": line}


def craft(items, label, count=1):
    o = {"type": "craft", "label": label, "target": count, "bench": True}
    o["items" if isinstance(items, list) else "item"] = items
    return o


def obtain(items, label, count=1, enchanted=False):
    o = {"type": "obtain", "label": label, "target": count, "sticky": True}
    if enchanted:
        o["enchanted"] = True
    else:
        o["items" if isinstance(items, list) else "item"] = items
    return o


def visit(kind, ident, label, **extra):
    return {"type": "visit", "label": label, "target": 1, "kind": kind, "id": ident, **extra}


def layer(key):
    d = LAYERS[key]
    return visit("layer", f"skyrpg_world:{key}", f"Descend into the {d['name']}", min_y=d["min_y"], max_y=d["max_y"], dimension="minecraft:overworld")


def found(key):
    d = DUNGEONS[key]
    name = d["name"].removeprefix("The ")
    return visit("dungeon_found", f"skyrpg_world:{key}", f"Find the entrance to {name}", structure=d["structure"], name=name,
                 dimension=DUNGEON_DIMENSION.get(key, "minecraft:overworld"))


def level(n):
    return {"type": "reach_level", "label": f"Reach SkyRPG Level {n}", "target": n}


def journal():
    return {"type": "journal", "label": "Open the Journal in the K menu", "target": 1}




def collect_tier(coll, tier):
    assert coll in COLLECTIONS, f"unknown collection {coll}"
    return {"type": "collection", "label": f"Reach {COLLECTIONS[coll]['name']} collection tier {tier}", "target": tier, "id": f"{NS}:{coll}", "tier": True}


def wildgate():
    return {"type": "visit", "label": "Step through the Wildgate into the survival world", "target": 1, "kind": "dimension", "id": "minecraft:overworld",
            "track": {"pos": [0.5, 69, 77.5], "dimension": "skyrpg_hub:hub", "name": "The Wildgate"}}


# ---------------------------------------------------------------- the dialogue DSL

def npc_id(key):
    assert key in NPCS, f"unknown npc {key}"
    return f"skyrpg_npcs:{key}"


def pages(items):
    out = []
    for item in items:
        if isinstance(item, str):
            out.append({"text": item})
        elif isinstance(item, tuple):
            out.append({"mood": item[0], "text": item[1]})
        else:
            out.append(dict(item))
    return out


def conv(speaker, items, choices_last=None):
    """A conversation file. {choices_last} are added to the last page when it has none."""
    ps = pages(items)
    if choices_last and "choices" not in ps[-1]:
        ps[-1]["choices"] = choices_last
    assert all(len(p["text"]) <= 420 for p in ps), [p["text"][:40] for p in ps if len(p["text"]) > 420]
    return {"speaker": npc_id(speaker) if speaker else "narrator", "pages": ps}


def topic(pid, text, back="menu", mood=None):
    """A page that returns to the topic menu."""
    d = {"id": pid, "text": text, "choices": [{"label": "Tell me more.", "action": "goto", "page": back}]}
    if mood:
        d["mood"] = mood
    return d


def menu(text, topics, done_label, done="bye", pid="menu"):
    return {"id": pid, "text": text,
            "choices": [{"label": label, "action": "goto", "page": target} for label, target in topics]
            + [{"label": done_label, "action": "goto", "page": done}]}


def Q(qid, title, giver, desc, objectives, coins, xp, items=(), hint="", offer=(), complete=(), tip="", turn_in="same", talk=None,
      replaces=(), accept="I'll take care of it.", decline="Not right now.", shop=False, side=False, thanks="Thank you."):
    return dict(id=qid, title=title, giver=giver, desc=desc, objectives=objectives, coins=coins, xp=xp, items=list(items), hint=hint,
                offer=list(offer), complete=list(complete), tip=tip, turn_in=giver if turn_in == "same" else turn_in, talk=talk or {},
                replaces=list(replaces), accept=accept, decline=decline, shop=shop, side=side, thanks=thanks)
