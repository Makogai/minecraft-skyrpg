#!/usr/bin/env python3
"""Generates everything data-like for the mining mod: textures (pixel art drawn here, nothing copied from
vanilla), blockstates and models, loot tables, tags, worldgen, recipes, skill XP and lang.

    python tools/art/generate_mining.py [--preview out.png]

Writes under mining/src/main/resources. The node and ore lists match MiningBlocks/MiningItems.
"""
import json
import random
import sys
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parent.parent.parent
RES = ROOT / "mining" / "src" / "main" / "resources"
NS = "skyrpg_mining"
ASSETS = RES / "assets" / NS
DATA = RES / "data" / NS

# ---------------------------------------------------------------- palettes
# Ore colours: (shadow, base, highlight, glint)
ORE = {
    "coal": ((30, 30, 36), (64, 64, 74), (112, 112, 128), (186, 186, 204)),
    "iron": ((140, 92, 66), (198, 150, 116), (226, 190, 160), (246, 226, 206)),
    "gold": ((178, 112, 18), (236, 178, 38), (252, 222, 92), (255, 248, 190)),
    "redstone": ((120, 8, 8), (196, 22, 22), (252, 60, 50), (255, 160, 140)),
    "skyiron": ((70, 104, 140), (120, 170, 214), (176, 214, 244), (236, 248, 255)),
    "diamond": ((20, 120, 124), (48, 196, 196), (124, 238, 226), (220, 255, 250)),
    "emberite": ((136, 40, 8), (226, 96, 20), (255, 160, 48), (255, 228, 140)),
    "lumenite": ((60, 120, 170), (120, 220, 240), (200, 250, 255), (255, 255, 255)),
    "duskquartz": ((52, 40, 72), (104, 86, 140), (164, 146, 204), (232, 222, 252)),
}
STONE = [(94, 94, 98), (112, 112, 116), (124, 124, 128), (138, 138, 142)]
DEEPSLATE = [(46, 46, 54), (58, 58, 66), (70, 70, 80), (84, 84, 94)]
FRAME = [(30, 28, 34), (44, 40, 48), (64, 58, 66)]
TRIM = [(140, 98, 34), (196, 150, 58), (240, 206, 110)]

NODES = ["coal", "iron", "gold", "redstone", "skyiron", "duskquartz", "diamond", "emberite", "lumenite"]
NODE_DROPS = {"coal": ("minecraft:coal", 1, 1), "iron": ("minecraft:raw_iron", 1, 1), "gold": ("minecraft:raw_gold", 1, 1),
              "redstone": ("minecraft:redstone", 2, 4), "skyiron": (f"{NS}:raw_skyiron", 1, 1),
              "duskquartz": (f"{NS}:duskquartz_shard", 1, 2),
              "diamond": ("minecraft:diamond", 1, 1), "emberite": (f"{NS}:emberite_chunk", 1, 1),
              "lumenite": (f"{NS}:lumenite_crystal", 1, 1)}
NODE_TOOL = {"coal": "stone", "iron": "stone", "gold": "iron", "redstone": "iron", "skyiron": "stone", "duskquartz": "iron",
             "diamond": "iron", "emberite": "iron", "lumenite": "diamond"}
# Mining XP: placed nodes give a fraction of what the real ore gives (world ores are the real progression).
NODE_XP = {"coal": 1, "iron": 1, "gold": 1.2, "redstone": 1.4, "skyiron": 1.6, "duskquartz": 2.2, "diamond": 2, "emberite": 2.8,
           "lumenite": 3.5}
NEW_ORES = {"skyiron": (f"{NS}:raw_skyiron", "stone", 8), "duskquartz": (f"{NS}:duskquartz_shard", "iron", 12),
            "emberite": (f"{NS}:emberite_chunk", "iron", 16),
            "lumenite": (f"{NS}:lumenite_crystal", "diamond", 25)}
MATERIALS = {
    "glimmer_shard": "Glimmer Shard", "glimmer_crystal": "Glimmer Crystal", "raw_skyiron": "Raw Skyiron",
    "emberite_chunk": "Emberite Chunk", "lumenite_crystal": "Lumenite Crystal", "prism_fragment": "Prism Fragment",
    "heartstone": "Heartstone", "skyiron_ingot": "Skyiron Ingot", "emberite_ingot": "Emberite Ingot",
    "duskquartz_shard": "Duskquartz Shard", "attuned_lumenite": "Attuned Lumenite", "prism": "Prism",
}
# Storage blocks (9 of the material), and what a pickaxe needs for each of our ores and nodes (see ToolTiers).
STORAGE = {"skyiron_block": ("Block of Skyiron", "skyiron"), "emberite_block": ("Block of Emberite", "emberite"),
           "duskquartz_block": ("Block of Duskquartz", "duskquartz")}
TIERS = {"skyiron": 2, "duskquartz": 4, "emberite": 5, "lumenite": 6}

# The Nether's ore, Hellforged: in netherrack (hellforged_ore) and a blackstone variant (blackstone_hellforged_ore, which
# also replaces basalt). ore: (drop, Mining XP, pick tier). Its colours join ORE so Echo Sense and the art agree.
ORE["hellforged"] = ((70, 8, 10), (176, 28, 22), (246, 92, 40), (255, 226, 160))
NETHER_ORES = {"hellforged": (f"{NS}:hellforged_scrap", 20, 5)}
NETHERRACK = [(62, 18, 20), (92, 30, 30), (112, 40, 38), (132, 54, 50)]
BLACKSTONE = [(24, 20, 26), (36, 32, 40), (48, 44, 52), (62, 56, 66)]
MATERIALS["hellforged_scrap"] = "Hellforged Scrap"
MATERIALS["hellforged_ingot"] = "Hellforged Ingot"
TIERS_EXTRA = {5: [f"{NS}:{p}{o}_ore" for o in NETHER_ORES for p in ("", "blackstone_")]}


def write_json(path, value):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(value, indent=2) + "\n", encoding="utf-8")


# ---------------------------------------------------------------- pixel art helpers
def base(palette, seed, streaks=False):
    rnd = random.Random(seed)
    img = Image.new("RGBA", (16, 16))
    for y in range(16):
        for x in range(16):
            v = rnd.random()
            if streaks and (y % 5 == 2):
                v *= 0.55
            idx = 0 if v < 0.18 else 1 if v < 0.6 else 2 if v < 0.9 else 3
            img.putpixel((x, y), palette[idx] + (255,))
    return img


def cluster(img, rnd, colours, cx, cy, size):
    """An ore fleck: a small blob with shadow under it and a glint on top."""
    shadow, body, light, glint = colours
    cells = {(cx, cy)}
    for _ in range(size):
        x, y = rnd.choice(sorted(cells))
        dx, dy = rnd.choice(((1, 0), (-1, 0), (0, 1), (0, -1)))
        if 0 <= x + dx < 16 and 0 <= y + dy < 16:
            cells.add((x + dx, y + dy))
    for x, y in cells:
        if (x, y + 1) not in cells and y + 1 < 16:
            img.putpixel((x, y + 1), shadow + (255,))
    for x, y in cells:
        img.putpixel((x, y), body + (255,))
    for x, y in cells:
        if (x, y - 1) not in cells or (x - 1, y) not in cells:
            img.putpixel((x, y), light + (255,))
    top = min(cells, key=lambda c: (c[1], c[0]))
    img.putpixel(top, glint + (255,))


def ore_texture(ore, deepslate, seed):
    rnd = random.Random(seed)
    img = base(DEEPSLATE if deepslate else STONE, seed, streaks=deepslate)
    spots = [(3, 3), (11, 2), (6, 8), (12, 10), (2, 12), (8, 13)]
    for i, (x, y) in enumerate(spots):
        if ore == "lumenite" and i % 2:
            continue  # lumenite is sparse: a few bright crystals
        cluster(img, rnd, ORE[ore], x + rnd.randint(-1, 1), y + rnd.randint(-1, 1), 3 + rnd.randint(0, 2))
    return img


def nether_ore_texture(ore, blackstone, seed):
    """A Nether ore: netherrack (or blackstone) with molten flecks, each with a dark rim so it reads on red stone."""
    rnd = random.Random(seed)
    img = base(BLACKSTONE if blackstone else NETHERRACK, seed, streaks=blackstone)
    rim = (20, 6, 8) if not blackstone else (12, 8, 10)
    spots = [(3, 3), (11, 3), (7, 8), (12, 11), (3, 12), (9, 13)]
    for x, y in spots:
        cx, cy = x + rnd.randint(-1, 1), y + rnd.randint(-1, 1)
        for dx, dy in ((-1, 0), (1, 0), (0, -1), (0, 1), (1, 1), (-1, 1)):
            if 0 <= cx + dx < 16 and 0 <= cy + dy < 16:
                img.putpixel((cx + dx, cy + dy), rim + (255,))
        cluster(img, rnd, ORE[ore], cx, cy, 3 + rnd.randint(0, 2))
    return img


def node_texture(ore, depleted, seed):
    """A node: a dark polished frame with gold corner studs, ore packed inside (or a cracked, spent core)."""
    rnd = random.Random(seed)
    img = base(DEEPSLATE, seed + 1)
    for i in range(16):
        for edge in ((i, 0), (i, 15), (0, i), (15, i)):
            img.putpixel(edge, FRAME[0] + (255,))
        for edge in ((i, 1), (i, 14), (1, i), (14, i)):
            if 1 <= i <= 14:
                img.putpixel(edge, FRAME[2 if edge[1] == 1 or edge[0] == 1 else 1] + (255,))
    for cx, cy in ((1, 1), (14, 1), (1, 14), (14, 14)):
        img.putpixel((cx, cy), TRIM[1] + (255,))
        img.putpixel((cx + (1 if cx == 1 else -1), cy), TRIM[0] + (255,))
        img.putpixel((cx, cy + (1 if cy == 1 else -1)), TRIM[0] + (255,))
    if depleted:
        dull = tuple(tuple(int(c * 0.45) for c in col) for col in ORE[ore])
        for _ in range(3):
            cluster(img, rnd, dull, rnd.randint(4, 11), rnd.randint(4, 11), 1)
        x, y = rnd.randint(4, 6), 3
        while y < 13:  # a crack running down the spent core
            img.putpixel((x, y), (20, 20, 24, 255))
            x = max(3, min(12, x + rnd.choice((-1, 0, 1))))
            y += 1
    else:
        for x, y in ((5, 5), (10, 5), (7, 8), (4, 10), (11, 10), (8, 12), (6, 4), (10, 9)):
            cluster(img, rnd, ORE[ore], x, y, 3 + rnd.randint(0, 3))
    return img


def outline(img, colour):
    out = img.copy()
    for y in range(16):
        for x in range(16):
            if img.getpixel((x, y))[3] == 0 and any(
                    0 <= x + dx < 16 and 0 <= y + dy < 16 and img.getpixel((x + dx, y + dy))[3] > 0
                    for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1))):
                out.putpixel((x, y), colour + (255,))
    return out


def gem(mask, colours, rim):
    """A faceted gem from a text mask: '#' body, '+' light facet, '*' glint, '-' shadow facet."""
    shadow, body, light, glint = colours
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    for y, row in enumerate(mask):
        for x, ch in enumerate(row):
            colour = {"#": body, "+": light, "*": glint, "-": shadow}.get(ch)
            if colour:
                img.putpixel((x, y), colour + (255,))
    return outline(img, rim)


SHARD = ["................", "................", "........*.......", ".......*+.......", ".......++#......",
         "......+###......", "......####-.....", ".....+###--.....", ".....###--......", "....+##--.......",
         "....##--........", "....#--.........", "....--..........", "................", "................",
         "................"]
CRYSTAL = ["................", "......*++.......", ".....*+++#......", "....*+++###.....", "...+++####--....",
           "...++####---....", "...+####----....", "...####-----....", "...###------....", "....#-----......",
           ".....---........", "................", "................", "................", "................",
           "................"]
CHUNK = ["................", "................", ".....+++........", "....+####+......", "...+######+.....",
         "...#######-+....", "..+###*##---....", "..####+###--....", "..-#######--....", "...-######-.....",
         "....--####-.....", "......---.......", "................", "................", "................",
         "................"]
PRISM = ["................", "........*.......", ".......*+#......", "......*++##.....", "......+++###....",
         ".....+++####....", ".....++####-....", "....+++###--....", "....++###---....", "...+++##----....",
         "...++##-----....", "...#####------..", "................", "................", "................",
         "................"]
INGOT = ["................", "................", "................", "................", "....*+++++++....",
         "...*++++++++#...", "..*+++++++++##..", "..++++++++++##..", "..#++++++++###..", "...#########....",
         "................", "................", "................", "................", "................", "................"]
HEART = ["................", "................", "...+++...+++....", "..+*++#.+++##...", "..+++####+###...",
         "..++#########...", "..+##########-..", "...#########-...", "....#######-....", ".....#####-.....",
         "......###-......", ".......#-.......", "................", "................", "................",
         "................"]


def material_textures():
    rainbow = ((90, 60, 160), (90, 170, 230), (240, 220, 120), (255, 255, 255))
    return {
        "glimmer_shard": gem(SHARD, ((150, 120, 200), (206, 184, 240), (240, 228, 255), (255, 255, 255)), (70, 50, 110)),
        "glimmer_crystal": gem(CRYSTAL, ((130, 96, 200), (190, 160, 244), (232, 216, 255), (255, 255, 255)), (60, 40, 110)),
        "raw_skyiron": gem(CHUNK, ORE["skyiron"], (40, 60, 90)),
        "emberite_chunk": gem(CHUNK, ORE["emberite"], (90, 24, 4)),
        "lumenite_crystal": gem(CRYSTAL, ORE["lumenite"], (30, 80, 120)),
        "prism_fragment": gem(PRISM, rainbow, (40, 30, 80)),
        "heartstone": gem(HEART, ((140, 20, 60), (220, 50, 100), (255, 120, 160), (255, 220, 235)), (80, 10, 30)),
        "skyiron_ingot": gem(INGOT, ORE["skyiron"], (40, 60, 90)),
        "emberite_ingot": gem(INGOT, ((150, 50, 10), (230, 110, 30), (255, 180, 70), (255, 236, 170)), (90, 24, 4)),
        "duskquartz_shard": gem(SHARD, ORE["duskquartz"], (34, 24, 52)),
        "attuned_lumenite": gem(CRYSTAL, ((70, 150, 200), (150, 236, 250), (230, 255, 255), (255, 255, 255)), (200, 160, 60)),
        "prism": gem(CRYSTAL, rainbow, (60, 40, 110)),
        "hellforged_scrap": gem(SCRAP, ((40, 14, 14), (96, 30, 26), (176, 44, 30), (255, 140, 60)), (24, 6, 6)),
        "hellforged_ingot": gem(INGOT, ((58, 8, 10), (128, 20, 20), (204, 52, 30), (255, 170, 90)), (30, 4, 6)),
    }


# Hellforged Scrap: a jagged, scorched lump of metal with molten seams ('*' are the hot seams).
SCRAP = ["................", "................", "......++........", "....+++##+......", "...++#*###+.....",
         "...+##*####-....", "..+###**###-....", "..##*###*##--...", "..-##*####*#-...", "...-####**#-....",
         "....--###*--....", "......----......", "................", "................", "................",
         "................"]


def hellforged_pickaxe():
    """The Hellforged Pickaxe: the pickaxe silhouette of generate_items.py, in dark red-hot metal on an obsidian haft."""
    sys.dont_write_bytecode = True  # importing generate_items shouldn't leave a __pycache__ behind
    sys.path.insert(0, str(Path(__file__).resolve().parent))
    import generate_items as gi
    metal = gi.Material(["2a0606", "5e1210", "a42418", "e85a28", "ffc078"], texture=gi.sheen)
    mask, _, tweak = gi.ICONS["prospector_pickaxe"]
    return gi.render(mask, {"T": metal.shifted(texture=gi.flat), "C": gi.OBSIDIAN.shifted(texture=gi.flat), "A": gi.EMBER,
                            "K": gi.OBSIDIAN}, tweak)


def storage_texture(ore, seed):
    """A block of the refined material: bevelled plates with rivets in the ore's colours."""
    shadow, base_c, high, glint = ORE[ore]
    img = Image.new("RGBA", (16, 16), base_c + (255,))
    rnd = random.Random(seed)
    for y in range(16):
        for x in range(16):
            c = base_c
            if x in (0, 8) or y in (0, 8):
                c = high
            elif x in (7, 15) or y in (7, 15):
                c = shadow
            elif rnd.random() < 0.08:
                c = glint if rnd.random() < 0.4 else high
            img.putpixel((x, y), c + (255,))
    for x, y in ((2, 2), (13, 2), (2, 13), (13, 13), (5, 10), (10, 5)):
        img.putpixel((x, y), glint + (255,))
    return img


# ---------------------------------------------------------------- data
def loot_node(ore):
    item, low, high = NODE_DROPS[ore]
    entry = {"type": "minecraft:item", "name": item}
    if high > low:
        entry["modifier"] = [{"type": "minecraft:set_count", "count": {"type": "minecraft:uniform", "min": low, "max": high}}]
    return {"type": "minecraft:block", "pools": [{"rolls": 1, "entries": [entry]}], "random_sequence": f"{NS}:blocks/{ore}_node"}


def loot_ore(block, drop):
    return {"type": "minecraft:block", "pools": [{"rolls": 1, "entries": [{"type": "minecraft:alternatives", "children": [
        {"type": "minecraft:item", "condition": "minecraft:tool/can_silk_touch", "name": f"{NS}:{block}"},
        {"type": "minecraft:item", "name": drop, "modifier": [
            {"type": "minecraft:apply_bonus", "enchantment": "minecraft:fortune", "formula": "minecraft:ore_drops"},
            {"type": "minecraft:explosion_decay"}]}]}]}], "random_sequence": f"{NS}:blocks/{block}"}


def ore_feature(ore, size, discard):
    return {"type": "minecraft:ore", "discard_chance_on_air_exposure": discard, "size": size, "targets": [
        {"state": f"{NS}:{ore}_ore", "target": {"predicate_type": "minecraft:tag_match", "tag": "minecraft:stone_ore_replaceables"}},
        {"state": f"{NS}:deepslate_{ore}_ore", "target": {"predicate_type": "minecraft:tag_match", "tag": "minecraft:deepslate_ore_replaceables"}}]}


def nether_ore_feature(ore, size, discard):
    return {"type": "minecraft:ore", "discard_chance_on_air_exposure": discard, "size": size, "targets": [
        {"state": f"{NS}:{ore}_ore", "target": {"predicate_type": "minecraft:block_match", "block": "minecraft:netherrack"}},
        {"state": f"{NS}:blackstone_{ore}_ore", "target": {"predicate_type": "minecraft:block_match", "block": "minecraft:blackstone"}},
        {"state": f"{NS}:blackstone_{ore}_ore", "target": {"predicate_type": "minecraft:block_match", "block": "minecraft:basalt"}}]}


def nether_placed(name, count):
    return {"feature": f"{NS}:{name}", "placement": [
        {"type": "minecraft:count", "count": count}, {"type": "minecraft:in_square"},
        {"type": "minecraft:height_range", "height": {"type": "minecraft:uniform", "min_inclusive": {"absolute": 10}, "max_inclusive": {"absolute": 117}}},
        {"type": "minecraft:biome"}]}


def placed(ore, count, low, high, shape):
    return {"feature": f"{NS}:ore_{ore}", "placement": [
        {"type": "minecraft:count", "count": count}, {"type": "minecraft:in_square"},
        {"type": "minecraft:height_range", "height": {"type": f"minecraft:{shape}", "min_inclusive": {"absolute": low}, "max_inclusive": {"absolute": high}}},
        {"type": "minecraft:biome"}]}


def main():
    blocks_tex = ASSETS / "textures" / "block"
    items_tex = ASSETS / "textures" / "item"
    blocks_tex.mkdir(parents=True, exist_ok=True)
    items_tex.mkdir(parents=True, exist_ok=True)
    lang = {"itemGroup.skyrpg_mining": "SkyRPG Mining"}
    previews = []

    # New ores.
    for i, (ore, (drop, tool, xp)) in enumerate(NEW_ORES.items()):
        for deep in (False, True):
            block = f"{'deepslate_' if deep else ''}{ore}_ore"
            tex = ore_texture(ore, deep, 100 + i * 7 + deep)
            tex.save(blocks_tex / f"{block}.png")
            previews.append(tex)
            write_json(ASSETS / "blockstates" / f"{block}.json", {"variants": {"": {"model": f"{NS}:block/{block}"}}})
            write_json(ASSETS / "models" / "block" / f"{block}.json", {"parent": "minecraft:block/cube_all", "textures": {"all": f"{NS}:block/{block}"}})
            write_json(ASSETS / "items" / f"{block}.json", {"model": {"type": "minecraft:model", "model": f"{NS}:block/{block}"}})
            write_json(DATA / "loot_table" / "blocks" / f"{block}.json", loot_ore(block, drop))
            lang[f"block.{NS}.{block}"] = ("Deepslate " if deep else "") + ore.capitalize() + " Ore"

    # Nether ores: netherrack and blackstone variants.
    for i, (ore, (drop, _, _)) in enumerate(NETHER_ORES.items()):
        for blackstone in (False, True):
            block = f"{'blackstone_' if blackstone else ''}{ore}_ore"
            tex = nether_ore_texture(ore, blackstone, 400 + i * 7 + blackstone)
            tex.save(blocks_tex / f"{block}.png")
            previews.append(tex)
            write_json(ASSETS / "blockstates" / f"{block}.json", {"variants": {"": {"model": f"{NS}:block/{block}"}}})
            write_json(ASSETS / "models" / "block" / f"{block}.json", {"parent": "minecraft:block/cube_all", "textures": {"all": f"{NS}:block/{block}"}})
            write_json(ASSETS / "items" / f"{block}.json", {"model": {"type": "minecraft:model", "model": f"{NS}:block/{block}"}})
            write_json(DATA / "loot_table" / "blocks" / f"{block}.json", loot_ore(block, drop))
            lang[f"block.{NS}.{block}"] = ("Blackstone " if blackstone else "") + ore.capitalize() + " Ore"

    # Nodes.
    for i, ore in enumerate(NODES):
        block = f"{ore}_node"
        for depleted in (False, True):
            name = block + ("_spent" if depleted else "")
            tex = node_texture(ore, depleted, 200 + i * 11 + depleted)
            tex.save(blocks_tex / f"{name}.png")
            previews.append(tex)
            write_json(ASSETS / "models" / "block" / f"{name}.json", {"parent": "minecraft:block/cube_all", "textures": {"all": f"{NS}:block/{name}"}})
        write_json(ASSETS / "blockstates" / f"{block}.json", {"variants": {
            "depleted=false": {"model": f"{NS}:block/{block}"}, "depleted=true": {"model": f"{NS}:block/{block}_spent"}}})
        write_json(ASSETS / "items" / f"{block}.json", {"model": {"type": "minecraft:model", "model": f"{NS}:block/{block}"}})
        write_json(DATA / "loot_table" / "blocks" / f"{block}.json", loot_node(ore))
        lang[f"block.{NS}.{block}"] = ore.capitalize() + " Node"

    # Storage blocks.
    for i, (block, (label, ore)) in enumerate(STORAGE.items()):
        tex = storage_texture(ore, 300 + i)
        tex.save(blocks_tex / f"{block}.png")
        previews.append(tex)
        write_json(ASSETS / "blockstates" / f"{block}.json", {"variants": {"": {"model": f"{NS}:block/{block}"}}})
        write_json(ASSETS / "models" / "block" / f"{block}.json", {"parent": "minecraft:block/cube_all", "textures": {"all": f"{NS}:block/{block}"}})
        write_json(ASSETS / "items" / f"{block}.json", {"model": {"type": "minecraft:model", "model": f"{NS}:block/{block}"}})
        write_json(DATA / "loot_table" / "blocks" / f"{block}.json", {"type": "minecraft:block", "pools": [{"rolls": 1,
            "entries": [{"type": "minecraft:item", "name": f"{NS}:{block}"}], "conditions": [{"condition": "minecraft:survives_explosion"}]}],
            "random_sequence": f"{NS}:blocks/{block}"})
        lang[f"block.{NS}.{block}"] = label
    lang[f"tag.item.{NS}.auto_smelt"] = "Auto-smelted ores"

    # Materials.
    for name, tex in material_textures().items():
        tex.save(items_tex / f"{name}.png")
        previews.append(tex)
        write_json(ASSETS / "models" / "item" / f"{name}.json", {"parent": "minecraft:item/generated", "textures": {"layer0": f"{NS}:item/{name}"}})
        write_json(ASSETS / "items" / f"{name}.json", {"model": {"type": "minecraft:model", "model": f"{NS}:item/{name}"}})
        lang[f"item.{NS}.{name}"] = MATERIALS[name]
    # The Hellforged Pickaxe (a held tool: handheld model). Its name, stats and tier are its item definition, in
    # data/skyrpg_mining/skyrpg/items/hellforged_pickaxe.json (hand-written, like the items mod's).
    pick = hellforged_pickaxe()
    pick.save(items_tex / "hellforged_pickaxe.png")
    previews.append(pick)
    write_json(ASSETS / "models" / "item" / "hellforged_pickaxe.json", {"parent": "minecraft:item/handheld", "textures": {"layer0": f"{NS}:item/hellforged_pickaxe"}})
    write_json(ASSETS / "items" / "hellforged_pickaxe.json", {"model": {"type": "minecraft:model", "model": f"{NS}:item/hellforged_pickaxe"}})
    lang[f"item.{NS}.hellforged_pickaxe"] = "Hellforged Pickaxe"
    write_json(ASSETS / "lang" / "en_us.json", lang)

    # Tags.
    nodes = [f"{NS}:{o}_node" for o in NODES]
    ores = [f"{NS}:{p}{o}_ore" for o in NEW_ORES for p in ("", "deepslate_")]
    ores += [f"{NS}:{p}{o}_ore" for o in NETHER_ORES for p in ("", "blackstone_")]
    write_json(DATA / "tags" / "block" / "nodes.json", {"values": nodes})
    write_json(DATA / "tags" / "block" / "shard_stone.json", {"values": [f"minecraft:{b}" for b in (
        "stone", "deepslate", "cobblestone", "cobbled_deepslate", "tuff", "andesite", "diorite", "granite", "calcite",
        "netherrack", "blackstone", "basalt", "smooth_basalt", "end_stone")]})
    write_json(DATA / "tags" / "block" / "shard_ores.json", {"values": [f"#minecraft:{t}" for t in (
        "coal_ores", "iron_ores", "copper_ores", "gold_ores", "redstone_ores", "lapis_ores", "emerald_ores", "diamond_ores")]
        + ["minecraft:nether_quartz_ore", "minecraft:nether_gold_ore", "minecraft:ancient_debris"] + ores})
    mc_tags = RES / "data" / "minecraft" / "tags" / "block"
    write_json(mc_tags / "mineable" / "pickaxe.json", {"replace": False, "values": nodes + ores + [f"{NS}:{b}" for b in STORAGE]})
    # Pickaxe tiers: core's tool_tier/<n> lists what needs at least a tier-n tool (see ToolGates).
    for ore, tier in TIERS.items():
        write_json(DATA.parent / "skyrpg_core" / "tags" / "block" / "tool_tier" / f"{tier}.json",
                   {"replace": False, "values": [f"{NS}:{ore}_ore", f"{NS}:deepslate_{ore}_ore", f"{NS}:{ore}_node"]
                    + TIERS_EXTRA.get(tier, [])})
    for tier in ("stone", "iron", "diamond"):
        values = [f"{NS}:{o}_node" for o in NODES if NODE_TOOL[o] == tier]
        values += [f"{NS}:{p}{o}_ore" for o, (_, t, _) in NEW_ORES.items() if t == tier for p in ("", "deepslate_")]
        if tier == "diamond":  # Nether ores need a diamond-class tool, like ancient debris
            values += [f"{NS}:{p}{o}_ore" for o in NETHER_ORES for p in ("", "blackstone_")]
        write_json(mc_tags / f"needs_{tier}_tool.json", {"replace": False, "values": values})
    write_json(RES / "data" / "skyrpg_stats" / "tags" / "block" / "mining_fortune_blocks.json", {"replace": False, "values": nodes + ores})

    # Worldgen: Skyiron common mid-depth, Emberite small deep veins, Lumenite rare single crystals near bedrock.
    write_json(DATA / "worldgen" / "feature" / "ore_skyiron.json", ore_feature("skyiron", 7, 0.0))
    write_json(DATA / "worldgen" / "feature" / "ore_emberite.json", ore_feature("emberite", 5, 0.3))
    write_json(DATA / "worldgen" / "feature" / "ore_lumenite.json", ore_feature("lumenite", 3, 0.5))
    write_json(DATA / "worldgen" / "feature" / "ore_duskquartz.json", ore_feature("duskquartz", 6, 0.2))
    write_json(DATA / "worldgen" / "placed_feature" / "ore_duskquartz.json", placed("duskquartz", 5, -48, -8, "trapezoid"))
    write_json(DATA / "worldgen" / "placed_feature" / "ore_skyiron.json", placed("skyiron", 7, -24, 64, "trapezoid"))
    write_json(DATA / "worldgen" / "placed_feature" / "ore_emberite.json", placed("emberite", 4, -64, -8, "trapezoid"))
    write_json(DATA / "worldgen" / "placed_feature" / "ore_lumenite.json", placed("lumenite", 2, -64, -40, "uniform"))
    # Hellforged in the Nether (y 10 to 117): a few small veins anywhere, many in the biome tag hellforged_rich (the Ember
    # Forest, Bone Sea and Obsidian Spires), big veins in hellforged_veins (the Obsidian Spires). See MiningWorldgen.
    for name, size, discard, count in (("ore_hellforged", 3, 0.4, 2), ("ore_hellforged_rich", 6, 0.1, 8), ("ore_hellforged_veins", 10, 0.0, 3)):
        write_json(DATA / "worldgen" / "feature" / f"{name}.json", nether_ore_feature("hellforged", size, discard))
        write_json(DATA / "worldgen" / "placed_feature" / f"{name}.json", nether_placed(name, count))
    for tag in ("hellforged_rich", "hellforged_veins"):
        biomes = ["skyrpg_world:ember_forest", "skyrpg_world:bone_sea", "skyrpg_world:obsidian_spires"] if tag == "hellforged_rich" \
            else ["skyrpg_world:obsidian_spires"]
        write_json(DATA / "tags" / "worldgen" / "biome" / f"{tag}.json", {"values": [{"id": b, "required": False} for b in biomes]})

    # Recipes: 9 shards <-> 1 crystal.
    write_json(DATA / "recipe" / "glimmer_crystal.json", {"type": "minecraft:crafting_shaped", "category": "misc",
        "key": {"#": f"{NS}:glimmer_shard"}, "pattern": ["###", "###", "###"], "result": {"id": f"{NS}:glimmer_crystal"}})
    write_json(DATA / "recipe" / "glimmer_shard_from_crystal.json", {"type": "minecraft:crafting_shapeless", "category": "misc",
        "ingredients": [f"{NS}:glimmer_crystal"], "result": {"count": 9, "id": f"{NS}:glimmer_shard"}})

    # Refining: smelt Raw Skyiron (furnace or blast furnace) and Emberite Chunks (blast furnace only); 9 <-> 1 blocks.
    for raw, ingot, furnace in (("raw_skyiron", "skyiron_ingot", True), ("emberite_chunk", "emberite_ingot", False),
                                ("hellforged_scrap", "hellforged_ingot", False)):
        kinds = (("smelting", 200), ("blasting", 100)) if furnace else (("blasting", 200),)
        for kind, time in kinds:
            write_json(DATA / "recipe" / f"{ingot}_from_{kind}.json", {"type": f"minecraft:{kind}", "cookingtime": time, "experience": 0.9,
                "group": ingot, "ingredient": f"{NS}:{raw}", "result": {"id": f"{NS}:{ingot}"}})
    for block, material in (("skyiron_block", "skyiron_ingot"), ("emberite_block", "emberite_ingot"), ("duskquartz_block", "duskquartz_shard")):
        write_json(DATA / "recipe" / f"{block}.json", {"type": "minecraft:crafting_shaped", "category": "building",
            "key": {"#": f"{NS}:{material}"}, "pattern": ["###", "###", "###"], "result": {"id": f"{NS}:{block}"}})
        write_json(DATA / "recipe" / f"{material}_from_{block}.json", {"type": "minecraft:crafting_shapeless", "category": "misc",
            "ingredients": [f"{NS}:{block}"], "result": {"count": 9, "id": f"{NS}:{material}"}})

    # Mining XP for the new ores (real progression) and the placed nodes (a fraction of it).
    xp = {f"{NS}:{o}_node": NODE_XP[o] for o in NODES}
    for o, (_, _, ore_xp) in NEW_ORES.items():
        xp[f"{NS}:{o}_ore"] = ore_xp
        xp[f"{NS}:deepslate_{o}_ore"] = ore_xp
    for o, (_, ore_xp, _) in NETHER_ORES.items():
        xp[f"{NS}:{o}_ore"] = ore_xp
        xp[f"{NS}:blackstone_{o}_ore"] = ore_xp
    write_json(DATA / "skyrpg" / "skill_xp" / "mining.json", {"skill": "skyrpg_skills:mining", "blocks": xp})

    if "--preview" in sys.argv:
        out = Path(sys.argv[sys.argv.index("--preview") + 1])
        sheet = Image.new("RGBA", (len(previews) * 18 * 6, 16 * 6 + 12), (24, 26, 34, 255))
        for i, tex in enumerate(previews):
            sheet.alpha_composite(tex.resize((96, 96), Image.NEAREST), (i * 108 + 6, 6))
        sheet.save(out)
        print("preview:", out)
    print(f"Wrote {len(NODES)} nodes, {len(NEW_ORES) * 2} ores, {len(MATERIALS)} materials")


if __name__ == "__main__":
    main()
