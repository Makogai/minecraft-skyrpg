"""Writes the data for phase 6, The Wild (docs/design/the_wild.md): the six region bosses (dungeon-boss format, tier "region"),
their loot tables, the Heart Sigil and Moonblood Charm recipes, the new items' definitions and sell prices.
Run: python tools/builds/wild.py   (run tools/builds/regions.py for the region types themselves)

Add-only: it writes new files and merges ids into the amulet accessory tag; existing data is never rewritten.
"""
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
WORLD = ROOT / "world" / "src" / "main" / "resources" / "data" / "skyrpg_world"
ITEMS = ROOT / "items" / "src" / "main" / "resources" / "data" / "skyrpg_items"


def write(path, data):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(data, indent=2) + "\n", encoding="utf-8")


def move(type_, every, **fields):
    return {"type": type_, "every": every, **fields}


# id -> boss. Health is in SkyRPG units; the level is fixed (region monsters are about level 25-50 plus the region's bonus).
BOSSES = {
    "blight_lord": {
        "name": "The Blight Lord", "entity": "minecraft:zombie", "level": 45, "health": 22000, "damage": 22, "speed": 0.3, "scale": 3.0,
        "knockback_resistance": 0.9, "bar_color": "red",
        "equipment": {"head": "minecraft:netherite_helmet", "chest": "minecraft:netherite_chestplate", "legs": "minecraft:netherite_leggings",
                      "feet": "minecraft:netherite_boots", "mainhand": "minecraft:netherite_axe"},
        "intro": "The ground cracks open and the rot rises to meet you.",
        "death": "The Blight Lord crumbles, and the grey creeps out of the land.",
        "max_minions": 8, "coins": [9000, 13000], "trophy": "blighted_heart", "essence": "blight_essence",
        "first": {"coins": 15000, "xp": 5000},
        "phases": [(0.66, "The Blight Lord calls the dead from the earth!"), (0.33, "The blight boils over. The Blight Lord is enraged!")],
        "moves": [
            move("cloud", 260, effect="minecraft:wither", amplifier=0, duration=140, radius=4, on="target", particle="minecraft:crimson_spore",
                 warn="Corruption seeps up from the ground!"),
            move("summon", 480, max=6, warn="The Blighted Dead answer the Blight Lord's call!", mobs=[
                {"type": "minecraft:zombie", "count": 3, "level": 38, "name": "Blighted Dead", "equipment": {"head": "minecraft:chainmail_helmet"}},
                {"type": "minecraft:skeleton", "count": 2, "level": 38, "name": "Blight Archer"}]),
            move("pull", 340, radius=14, strength=1.1, particle="minecraft:portal", warn="The blight drags you in!"),
            move("slam", 280, below=0.66, radius=6, damage=20, knockup=0.9, windup=24, warn="The Blight Lord raises his fist!"),
            {"type": "enrage", "below": 0.33, "speed": 0.35, "damage": 0.35, "warn": "The Blight Lord howls!"},
        ]},
    "ash_colossus": {
        "name": "The Ash Colossus", "entity": "minecraft:iron_golem", "level": 50, "health": 30000, "damage": 28, "speed": 0.26, "scale": 2.6,
        "knockback_resistance": 1.0, "bar_color": "yellow", "equipment": {},
        "intro": "A heap of cinders stands up and keeps standing.",
        "death": "The Ash Colossus collapses into a drift of grey ash.",
        "max_minions": 4, "coins": [11000, 15000], "trophy": "cinder_core", "essence": "cinder_essence",
        "first": {"coins": 18000, "xp": 6000},
        "phases": [(0.66, "Fire breaks through the Colossus's seams!"), (0.33, "The Ash Colossus burns white-hot!")],
        "moves": [
            move("slam", 240, radius=8, damage=26, knockup=1.0, windup=28, particle="minecraft:flame", warn="The Ash Colossus lifts both fists!"),
            move("molten", 400, count=12, radius=5, duration=100, delay=25, damage=14, warn="Fire geysers! Keep moving!"),
            move("cloud", 360, effect="minecraft:blindness", amplifier=0, duration=100, radius=5, on="target", particle="minecraft:white_ash",
                 warn="Ash fills the air!"),
            move("shockwave", 320, below=0.66, radius=9, damage=18, push=1.8, particle="minecraft:large_smoke", warn="The ground shudders!"),
            {"type": "enrage", "below": 0.33, "speed": 0.4, "damage": 0.4, "warn": "The Ash Colossus ignites!"},
        ]},
    "frost_revenant_king": {
        "name": "The Frost Revenant King", "entity": "minecraft:stray", "level": 50, "health": 28000, "damage": 20, "speed": 0.3, "scale": 2.6,
        "knockback_resistance": 0.9, "bar_color": "white",
        "equipment": {"head": "minecraft:golden_helmet", "chest": "minecraft:diamond_chestplate", "mainhand": "minecraft:bow"},
        "intro": "A crown of ice lifts from the snow, and there is a king under it.",
        "death": "The Frost Revenant King shatters like a dropped window.",
        "max_minions": 6, "coins": [11000, 15000], "trophy": "frozen_crown", "essence": "frost_essence",
        "first": {"coins": 18000, "xp": 6000},
        "phases": [(0.66, "The King calls his court from the snow!"), (0.33, "The cold deepens. The King is enraged!")],
        "moves": [
            move("spikes", 260, rings=3, radius=12, windup=20, damage=14, freeze=160, effect="minecraft:slowness", amplifier=1, effect_duration=80,
                 warn="Ice spikes! Get off the ground ring!"),
            move("volley", 150, projectile="arrow", count=6, damage=12, spread=0.15, warn="A freezing volley!"),
            move("summon", 520, max=6, warn="The Frost Revenants rise to serve!", mobs=[
                {"type": "skyrpg_combat:frost_revenant", "count": 2, "level": 42, "name": "Frost Revenant"}]),
            move("shockwave", 330, below=0.66, radius=9, damage=16, push=1.6, particle="minecraft:snowflake", warn="A wave of frost rolls out!"),
            {"type": "enrage", "below": 0.33, "speed": 0.38, "damage": 0.35, "warn": "The crown blazes white!"},
        ]},
    "bog_mother": {
        "name": "The Bog Mother", "entity": "minecraft:witch", "level": 36, "health": 16000, "damage": 16, "speed": 0.3, "scale": 2.6,
        "knockback_resistance": 0.8, "bar_color": "green", "equipment": {},
        "intro": "Something very large stirs in the reeds and starts to laugh.",
        "death": "The Bog Mother sinks into her own marsh, still laughing.",
        "max_minions": 8, "coins": [7000, 10000], "trophy": "bog_mothers_eye", "essence": "mire_essence",
        "first": {"coins": 12000, "xp": 4000},
        "phases": [(0.66, "The bog bubbles. Her children come crawling!"), (0.33, "The Bog Mother's hexes grow strong!")],
        "moves": [
            move("cloud", 230, effect="minecraft:poison", amplifier=1, duration=140, radius=4, on="target", particle="minecraft:spore_blossom_air",
                 warn="Poison pools in the mud!"),
            move("summon", 420, max=6, warn="The Bog Mother splits her slime!", mobs=[
                {"type": "minecraft:slime", "count": 3, "level": 30, "name": "Bog Slime"}]),
            move("cloud", 300, effect="minecraft:slowness", amplifier=2, duration=140, radius=3, on="target", particle="minecraft:witch",
                 warn="Hex! Your legs go heavy!"),
            move("slam", 320, below=0.66, radius=6, damage=14, knockup=0.8, windup=22, warn="She slams down a mud wave!"),
            move("heal", 520, below=0.5, percent=0.03, warn="The marsh knits her wounds!"),
            {"type": "enrage", "below": 0.33, "speed": 0.36, "damage": 0.3, "warn": "The Bog Mother shrieks!"},
        ]},
    "lantern_wraith": {
        "name": "The Lantern Wraith", "entity": "minecraft:vex", "level": 42, "health": 18000, "damage": 18, "speed": 0.9, "scale": 3.0,
        "knockback_resistance": 1.0, "bar_color": "purple", "equipment": {"mainhand": "minecraft:soul_lantern"},
        "tags": ["skyrpg_hallowed_only"],
        "intro": "A lantern swings in the fog, and the thing that carries it turns to look at you.",
        "death": "The Lantern Wraith goes out, and the fog begins to lift.",
        "max_minions": 8, "coins": [8000, 11000], "trophy": "wraith_lantern", "essence": "ectoplasm",
        "first": {"coins": 14000, "xp": 5000},
        "phases": [(0.66, "The lantern flares. The dead come to the light!"), (0.33, "The Lantern Wraith burns blue!")],
        "moves": [
            move("blink", 190, warn="The Wraith slips behind you!"),
            move("strike", 230, count=3, spread=5, blast=2.5, delay=30, damage=14, ignite=3, particle="minecraft:soul_fire_flame",
                 warn="Soul-fire lanterns! Move!"),
            move("summon", 470, max=8, warn="A ghost wave!", mobs=[
                {"type": "minecraft:vex", "count": 4, "level": 36, "name": "Spectral Ghost", "tags": ["skyrpg_hallowed_only"]}]),
            move("shockwave", 320, below=0.66, radius=8, damage=14, push=1.5, particle="minecraft:soul", warn="A cold wave rolls out!"),
            {"type": "enrage", "below": 0.33, "speed": 0.4, "damage": 0.4, "warn": "The lantern burns blue!"},
        ]},
    "storm_griffin": {
        "name": "The Storm Griffin", "entity": "skyrpg_mounts:griffin", "fallback_entity": "minecraft:phantom", "level": 55, "health": 34000,
        "damage": 26, "speed": 0.5, "scale": 2.5, "knockback_resistance": 1.0, "bar_color": "blue", "equipment": {},
        "tags": ["skyrpg_storm_griffin"],
        "intro": "The wind stops. Then something the size of a house drops out of the cloud.",
        "death": "The Storm Griffin falls, and the sky goes quiet.",
        "max_minions": 4, "coins": [14000, 19000], "trophy": "stormfeather", "essence": "gale_essence",
        "first": {"coins": 22000, "xp": 7000},
        "phases": [(0.66, "Thunder rolls. The Storm Griffin takes to the air!"), (0.33, "Lightning crowns the Storm Griffin!")],
        "moves": [
            move("leap", 210, range=26, radius=4, damage=22, warn="The Storm Griffin dives!"),
            move("shockwave", 270, radius=10, damage=16, push=2.4, particle="minecraft:cloud", warn="A gust slams outwards!"),
            move("strike", 250, count=4, spread=6, blast=2.2, delay=28, damage=20, lightning=True, particle="minecraft:electric_spark",
                 warn="Lightning feathers!"),
            {"type": "enrage", "below": 0.33, "speed": 0.45, "damage": 0.4, "warn": "The Storm Griffin screams!"},
        ]},
}


def boss_json(bid, b):
    out = {"name": b["name"], "entity": b["entity"], "tier": "region", "level": b["level"], "health": b["health"], "damage": b["damage"],
           "speed": b["speed"], "scale": b["scale"], "knockback_resistance": b["knockback_resistance"], "bar_color": b["bar_color"],
           "equipment": b["equipment"], "intro": b["intro"], "death": b["death"], "max_minions": b["max_minions"],
           "health_per_player": 0.5, "loot": f"skyrpg_world:bosses/{bid}", "coins": b["coins"],
           "first_clear": {"coins": b["first"]["coins"], "xp": {"skyrpg_skills:combat": b["first"]["xp"]}},
           "phases": [{"below": below, "message": message} for below, message in b["phases"]], "moves": b["moves"]}
    if b.get("tags"):
        out["tags"] = b["tags"]
    return out


def loot(trophy, essence):
    def pool(item, lo, hi, chance=None):
        # This version's loot format: "modifier" on an entry, "condition" on a pool.
        pool = {"rolls": 1, "entries": [{"type": "minecraft:item", "name": item,
                                         "modifier": [{"type": "minecraft:set_count", "count": {"type": "minecraft:uniform", "min": lo, "max": hi}}]}]}
        if chance is not None:
            pool["condition"] = {"type": "minecraft:random_chance", "chance": chance}
        return pool
    return {"type": "minecraft:chest", "pools": [
        pool(f"skyrpg_world:{trophy}", 1, 1),
        pool(f"skyrpg_world:{essence}", 14, 24),
        pool("skyrpg_world:blood_shard", 3, 6),
        pool("skyrpg_world:dread_crystal", 1, 1, 0.5)]}


# New items: id -> (name, rarity, type, description, sell price)
ITEM_DEFS = {
    "blood_shard": ("Blood Shard", "rare", "Blood Moon Material",
                    "A sliver of red moonlight gone solid. Dropped by monsters under a Blood Moon. Crafts Heart Sigils and the Moonblood Charm.", 150),
    "gale_essence": ("Gale Essence", "uncommon", "Region Material", "Dropped by monsters in the Shattered Highlands. It never quite stops moving.", 60),
    "ectoplasm": ("Ectoplasm", "uncommon", "Region Material", "Dropped by monsters in the Haunted Moors. Cold, and slightly too heavy.", 60),
    "blighted_heart": ("Blighted Heart", "epic", "Boss Trophy", "The Blight Lord's heart, still black. Dropped by the Blight Lord.", 20000),
    "cinder_core": ("Cinder Core", "epic", "Boss Trophy", "The glowing core of the Ash Colossus. Dropped by the Ash Colossus.", 24000),
    "frozen_crown": ("Frozen Crown", "epic", "Boss Trophy", "A crown of ice that will not melt. Dropped by the Frost Revenant King.", 24000),
    "bog_mothers_eye": ("Bog Mother's Eye", "epic", "Boss Trophy", "It is still watching you. Dropped by the Bog Mother.", 14000),
    "wraith_lantern": ("Wraith Lantern", "epic", "Boss Trophy", "The Lantern Wraith's soul-fire lantern. Dropped by the Lantern Wraith.", 18000),
    "stormfeather": ("Stormfeather", "epic", "Boss Trophy", "A feather as long as a spear, crackling. Dropped by the Storm Griffin.", 30000),
}
SIGILS = {
    "blightlands": ("Blightlands", "blight_essence"),
    "ashen_wastes": ("Ashen Wastes", "cinder_essence"),
    "frostgrave": ("Frostgrave", "frost_essence"),
    "mire": ("Mire", "mire_essence"),
    "haunted_moors": ("Haunted Moors", "ectoplasm"),
    "shattered_highlands": ("Shattered Highlands", "gale_essence"),
}


def main():
    skills = "skyrpg_skills:combat"
    for bid, b in BOSSES.items():
        write(WORLD / "skyrpg" / "dungeon_bosses" / f"{bid}.json", boss_json(bid, b))
        write(WORLD / "loot_table" / "bosses" / f"{bid}.json", loot(b["trophy"], b["essence"]))
    for sid, (title, essence) in SIGILS.items():
        item = f"{sid}_sigil"
        ITEM_DEFS[item] = (f"{title} Heart Sigil", "epic", "Region Sigil",
                           f"Use it at the heart of the {title} to wake its boss. Consumed on use. Under a Blood Moon the heart wakes by itself.", 3000)
        write(WORLD / "skyrpg" / "recipes" / f"region_{item}.json", {
            "category": "region", "result": f"skyrpg_world:{item}",
            "ingredients": {f"skyrpg_world:{essence}": 32, "skyrpg_world:blood_shard": 4},
            "requires": {"skill": skills, "level": 10}})
    write(WORLD / "skyrpg" / "recipes" / "region_moonblood_charm.json", {
        "category": "region", "result": "skyrpg_items:moonblood_charm",
        "ingredients": {"skyrpg_world:blood_shard": 16, "minecraft:gold_ingot": 8, "minecraft:amethyst_shard": 8, "minecraft:crying_obsidian": 2},
        "requires": {"skill": skills, "level": 12}})
    prices = {}
    for item, (name, rarity, kind, description, price) in ITEM_DEFS.items():
        write(WORLD / "skyrpg" / "items" / f"{item}.json", {"name": name, "rarity": rarity, "type": kind, "description": description})
        prices[f"skyrpg_world:{item}"] = price
    prices["skyrpg_items:moonblood_charm"] = 2500
    write(WORLD / "skyrpg" / "sell_prices" / "wild.json", prices)
    write(ITEMS / "skyrpg" / "items" / "moonblood_charm.json", {
        "name": "Moonblood Charm", "rarity": "epic", "stats": {"health": 40, "strength": 8, "crit_damage": 10},
        "perks": ["At night: +12 Strength and +15 Crit Damage", "During a Blood Moon: +30 Strength and +40 Crit Damage"],
        "description": "A red moon in a silver clasp. It only really wakes after dark."})
    tag = ITEMS / "tags" / "item" / "accessory" / "amulet.json"
    data = json.loads(tag.read_text(encoding="utf-8"))
    if "skyrpg_items:moonblood_charm" not in data["values"]:
        data["values"].append("skyrpg_items:moonblood_charm")
        write(tag, data)
    print(f"wrote {len(BOSSES)} region bosses, {len(ITEM_DEFS)} item definitions, {len(SIGILS)} sigil recipes")


if __name__ == "__main__":
    main()
