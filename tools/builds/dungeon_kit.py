"""Builds world-generated structures (dungeons, shrines...) for the skyrpg_world mod from Python.

A structure is a set of template pieces (.nbt files, like the ones a structure block saves) joined by jigsaw blocks,
plus the worldgen JSON that says where it generates. Vanilla's jigsaw generator does the rest: it picks pieces from
pools at random, from the world seed, so every dungeon comes out a little different.

    from dungeon_kit import Pack, Piece

    pack = Pack()                                     # writes into world/src/main/resources
    room = Piece("forgotten_crypt/rooms/ossuary", 13, 7, 13)
    room.box(0, 0, 0, 12, 6, 12, "minecraft:stone_bricks", inside="minecraft:air")
    room.door("north", "skyrpg_world:crypt_hall", pool="skyrpg_world:forgotten_crypt/halls")
    room.marker(6, 1, 6, "spawner", mobs=[{"type": "minecraft:skeleton", "count": 3, "level": 12}], radius=9)
    pack.add(room)
    pack.pool("forgotten_crypt/rooms", [("forgotten_crypt/rooms/ossuary", 2)], fallback="forgotten_crypt/caps")
    pack.structure("forgotten_crypt", start_pool="forgotten_crypt/entrance", ...)
    pack.save()                                       # validates the jigsaw links, then writes every file

Coordinates inside a piece run from (0, 0, 0) to (width-1, height-1, depth-1); +x is east, +z is south.
Cells you never set are left as they are in the world (like structure void), so set "minecraft:air" wherever a room
must be hollow, or the terrain stays inside it.

Jigsaw rules (vanilla's): a jigsaw block sits on the outer face of its piece and faces out. A parent's jigsaw picks a
piece from its `pool` that has a jigsaw whose `name` equals the parent's `target`, rotates it so the two face each
other, and puts the child's jigsaw block in the cell right in front of the parent's. So the two pieces touch, and
each one's jigsaw cell becomes `final_state` (air, for a doorway). Use `Piece.door()` to make that easy.

The markers (invisible marker entities with data) are what the skyrpg_world mod reads at runtime: bosses, mob packs,
puzzle doors, levers, loot chests that refill... See docs/design/dungeons.md for every kind and its fields.
"""
import json
import random
import re
from pathlib import Path

import nbt

ROOT = Path(__file__).resolve().parents[2]
RESOURCES = ROOT / "world" / "src" / "main" / "resources"
NAMESPACE = "skyrpg_world"
DATA_VERSION = 5023  # Minecraft 26.3, read from a structure the game saved
MARKER_TAG = "skyrpg_dungeon"

HORIZONTAL = ("north", "south", "east", "west")
_STATE = re.compile(r"^([a-z0-9_.\-:/]+)(?:\[(.*)\])?$")


def state(text):
    """'minecraft:oak_stairs[facing=east,half=top]' -> a palette entry."""
    if ":" not in text.split("[")[0]:
        text = "minecraft:" + text
    m = _STATE.match(text.strip())
    if not m:
        raise ValueError(f"bad block state {text!r}")
    entry = {"id": m.group(1)}
    if m.group(2):
        props = {}
        for part in m.group(2).split(","):
            key, value = part.split("=")
            props[key.strip()] = value.strip()
        entry["properties"] = dict(sorted(props.items()))
    return entry


def ns(name):
    return name if ":" in name else f"{NAMESPACE}:{name}"


def _nbt_value(value):
    """JSON-ish Python -> NBT-ready Python (whole floats stay doubles; ints stay ints)."""
    if isinstance(value, dict):
        return {k: _nbt_value(v) for k, v in value.items()}
    if isinstance(value, (list, tuple)):
        return [_nbt_value(v) for v in value]
    if isinstance(value, bool):
        return nbt.Byte(1 if value else 0)
    return value



def _tier_of(loot):
    """A chest's reward tier from its loot table's name (crypt_vault -> vault)."""
    for tier in ("vault", "secret", "rare", "reward", "wayshrine"):
        if tier in loot:
            return tier
    return "common"


def modern_loot(node):
    """Loot tables in Minecraft 26.3's format. Older-style tables (functions/conditions lists with "function" and
    "condition" keys) still parse in 26.3 but silently lose every count, chance and name, so convert them:
    functions -> modifier, conditions -> condition (all_of for several), function/condition -> type. Explorer maps
    get a '#' tag destination, a wider search, and are discarded if no structure was found (no blank maps)."""
    if isinstance(node, list):
        return [modern_loot(item) for item in node]
    if not isinstance(node, dict):
        return node
    out = {}
    for key, value in node.items():
        if key == "function":
            out["type"] = value
        elif key == "condition" and isinstance(value, str) and "type" not in node:
            # Old-style condition object; with a "type" already, a string condition is a predicate reference.
            out["type"] = value
        elif key == "functions":
            out["modifier"] = modern_loot(value)
        elif key == "conditions":
            terms = modern_loot(value)
            out["condition"] = terms[0] if len(terms) == 1 else {"type": "minecraft:all_of", "terms": terms}
        else:
            out[key] = modern_loot(value)
    if out.get("type") == "minecraft:exploration_map":
        destination = out.get("destination", "")
        if destination and not destination.startswith("#"):
            out["destination"] = "#" + destination
        out.setdefault("search_radius", 100)
    if out.get("type") == "minecraft:enchant_randomly":
        out.setdefault("options", "#minecraft:on_random_loot")
    modifiers = out.get("modifier")
    if isinstance(modifiers, list) and any(isinstance(m, dict) and m.get("type") == "minecraft:exploration_map" for m in modifiers)             and not any(isinstance(m, dict) and m.get("type") == "minecraft:filtered" for m in modifiers):
        modifiers.append({"type": "minecraft:filtered", "item_filter": {"predicates": {"minecraft:map_id": {}}},
                          "on_fail": {"type": "minecraft:discard"}})
    return out


class Piece:
    """One template: a box of blocks, block entities, jigsaws and markers."""

    def __init__(self, path, width, height, depth):
        self.path = path              # e.g. "forgotten_crypt/rooms/ossuary" (under data/skyrpg_world/structure/)
        self.size = (width, height, depth)
        self.cells = {}               # (x, y, z) -> (palette entry json, block entity nbt or None)
        self.entities = []
        self.jigsaws = []             # (pos, facing/orientation, name, target, pool)

    # -- blocks -----------------------------------------------------------------------------------------------
    def inside(self, x, y, z):
        w, h, d = self.size
        return 0 <= x < w and 0 <= y < h and 0 <= z < d

    def set(self, x, y, z, block, nbt_data=None):
        if not self.inside(x, y, z):
            raise ValueError(f"{self.path}: ({x}, {y}, {z}) is outside the piece {self.size}")
        self.cells[(x, y, z)] = (state(block) if isinstance(block, str) else block, nbt_data)

    def get(self, x, y, z):
        cell = self.cells.get((x, y, z))
        return None if cell is None else cell[0]["id"]

    def fill(self, x1, y1, z1, x2, y2, z2, block):
        for x in range(min(x1, x2), max(x1, x2) + 1):
            for y in range(min(y1, y2), max(y1, y2) + 1):
                for z in range(min(z1, z2), max(z1, z2) + 1):
                    self.set(x, y, z, block)

    def box(self, x1, y1, z1, x2, y2, z2, wall, inside=None, floor=None, ceiling=None):
        """A room: walls of `wall`, optionally a different floor/ceiling, and `inside` (usually air) filling it."""
        self.fill(x1, y1, z1, x2, y2, z2, wall)
        if inside is not None:
            self.fill(x1 + 1, y1 + 1, z1 + 1, x2 - 1, y2 - 1, z2 - 1, inside)
        if floor is not None:
            self.fill(x1, y1, z1, x2, y1, z2, floor)
        if ceiling is not None:
            self.fill(x1, y2, z1, x2, y2, z2, ceiling)

    def replace(self, find, block, x1=None, y1=None, z1=None, x2=None, y2=None, z2=None):
        w, h, d = self.size
        x1, y1, z1 = x1 or 0, y1 or 0, z1 or 0
        x2 = w - 1 if x2 is None else x2
        y2 = h - 1 if y2 is None else y2
        z2 = d - 1 if z2 is None else z2
        find = ns(find) if ":" in find else "minecraft:" + find
        for (x, y, z), (entry, _) in list(self.cells.items()):
            if entry["id"] == find and x1 <= x <= x2 and y1 <= y <= y2 and z1 <= z <= z2:
                self.set(x, y, z, block)

    def weather(self, seed, chances):
        """Ages a build: chances = {"minecraft:stone_bricks": [("minecraft:cracked_stone_bricks", 0.15), ...]}."""
        rng = random.Random(f"{self.path}:{seed}")
        for pos, (entry, data) in sorted(self.cells.items()):
            options = chances.get(entry["id"])
            if not options or data is not None:
                continue
            roll = rng.random()
            for block, chance in options:
                if roll < chance:
                    keep = entry.get("properties")
                    new = state(block)
                    if keep and "properties" not in new:
                        new["properties"] = dict(keep)
                    self.cells[pos] = (new, None)
                    break
                roll -= chance

    # -- block entities -----------------------------------------------------------------------------------------
    def chest(self, x, y, z, facing, loot, refill=True, block="minecraft:chest", tier=None):
        """A loot chest. With refill (the default), a 'chest' marker makes it per-player (everyone rolls their own loot,
        plus coins and Delving XP by tier) and restocks it when the dungeon resets."""
        self.set(x, y, z, f"{block}[facing={facing}]", {"id": block.split("[")[0], "LootTable": ns(loot)})
        if refill:
            self.marker(x, y, z, "chest", loot=ns(loot), tier=tier or _tier_of(loot))

    def barrel(self, x, y, z, facing, loot, refill=True, tier=None):
        self.set(x, y, z, f"minecraft:barrel[facing={facing}]", {"id": "minecraft:barrel", "LootTable": ns(loot)})
        if refill:
            self.marker(x, y, z, "chest", loot=ns(loot), tier=tier or _tier_of(loot))

    def sign(self, x, y, z, lines, block="minecraft:dark_oak_wall_sign[facing=south]", color="black", glow=False):
        """A sign with up to 4 lines of plain text on the front."""
        lines = (list(lines) + ["", "", "", ""])[:4]
        text = {"messages": lines, "color": color,
                "has_glowing_text": nbt.Byte(1 if glow else 0)}
        self.set(x, y, z, block, {"id": "minecraft:sign", "front_text": text,
                                  "back_text": {"messages": ["", "", "", ""], "color": "black", "has_glowing_text": nbt.Byte(0)},
                                  "is_waxed": nbt.Byte(1)})

    def spawner(self, x, y, z, entity):
        """A plain vanilla mob spawner (mobs from it count as farmed: less loot). Prefer 'spawner' markers."""
        self.set(x, y, z, "minecraft:spawner", {"id": "minecraft:mob_spawner", "SpawnData": {"entity": {"id": entity}}})

    # -- jigsaws ------------------------------------------------------------------------------------------------
    def jigsaw(self, x, y, z, orientation, name, target, pool, final_state="minecraft:air", joint=None,
               placement_priority=0, selection_priority=0):
        """A raw jigsaw block. orientation: 'north_up' (horizontal, faces north) or 'down_north'/'up_north' (vertical)."""
        front = orientation.split("_")[0]
        if joint is None:
            joint = "aligned" if front in ("up", "down") else "rollable"
        self.set(x, y, z, f"minecraft:jigsaw[orientation={orientation}]", {
            "id": "minecraft:jigsaw", "name": ns(name) if name != "minecraft:empty" else name,
            "target": ns(target) if target != "minecraft:empty" else target,
            "pool": ns(pool) if pool != "minecraft:empty" else pool,
            "final_state": final_state, "joint": joint,
            "placement_priority": placement_priority, "selection_priority": selection_priority})
        self.jigsaws.append(((x, y, z), orientation, ns(name), ns(target) if target != "minecraft:empty" else target,
                             ns(pool) if pool != "minecraft:empty" else pool))

    def door(self, side, name, target=None, pool="minecraft:empty", at=None, y=1, final_state="minecraft:air",
             placement_priority=0):
        """A jigsaw in the middle of one wall (or at `at` along it), at height `y`, facing out of that wall.

        side: north (z=0), south (z=depth-1), west (x=0), east (x=width-1).
        Every connector both offers itself (name) and asks for a partner (target, from pool); for a doorway that only
        receives (the child side), leave pool as minecraft:empty. target defaults to name, so a 'crypt_hall' door
        joins another 'crypt_hall' door.
        """
        w, h, d = self.size
        if side == "north":
            pos = ((w // 2) if at is None else at, y, 0)
        elif side == "south":
            pos = ((w // 2) if at is None else at, y, d - 1)
        elif side == "west":
            pos = (0, y, (d // 2) if at is None else at)
        elif side == "east":
            pos = (w - 1, y, (d // 2) if at is None else at)
        else:
            raise ValueError(side)
        self.jigsaw(*pos, f"{side}_up", name, target or name, pool, final_state=final_state,
                    placement_priority=placement_priority)
        return pos

    # -- markers ------------------------------------------------------------------------------------------------
    def marker(self, x, y, z, kind, **data):
        """An invisible marker the skyrpg_world mod acts on (see docs/design/dungeons.md)."""
        if not self.inside(x, y, z):
            raise ValueError(f"{self.path}: marker at ({x}, {y}, {z}) is outside the piece")
        payload = {"skyrpg": kind}
        payload.update(data)
        pos = [x + 0.5, float(y), z + 0.5]
        self.entities.append({
            "pos": pos, "blockPos": [x, y, z],
            "nbt": {"id": "minecraft:marker", "Pos": list(pos), "Tags": [MARKER_TAG], "data": _nbt_value(payload)},
        })

    def entity(self, x, y, z, entity_nbt):
        """Any other entity stored in the template (an armour stand, an item frame...)."""
        pos = [x + 0.5, float(y), z + 0.5]
        tag = dict(entity_nbt)
        tag["Pos"] = list(pos)
        self.entities.append({"pos": pos, "blockPos": [x, y, z], "nbt": _nbt_value(tag)})

    # -- output -------------------------------------------------------------------------------------------------
    def to_nbt(self):
        palette, index, blocks = [], {}, []
        # Plain blocks first, then the ones with block entities, like the game saves them.
        ordered = sorted(self.cells.items(), key=lambda kv: (kv[1][1] is not None, kv[0][1], kv[0][2], kv[0][0]))
        for (x, y, z), (entry, data) in ordered:
            key = json.dumps(entry, sort_keys=True)
            if key not in index:
                index[key] = len(palette)
                palette.append(_nbt_value(entry))
            block = {"pos": [x, y, z], "state": index[key]}
            if data is not None:
                block["nbt"] = _nbt_value(data)
            blocks.append(block)
        return {"DataVersion": DATA_VERSION, "size": list(self.size), "palette": palette, "blocks": blocks,
                "entities": self.entities}


class Pack:
    """Every file for the skyrpg_world mod's structures: templates, pools, structures, sets, tags, loot tables."""

    def __init__(self, resources=RESOURCES):
        self.resources = Path(resources)
        self.pieces = {}
        self.pools = {}
        self.files = {}

    def add(self, piece):
        if piece.path in self.pieces:
            raise ValueError(f"two pieces are called {piece.path}")
        self.pieces[piece.path] = piece
        return piece

    def pool(self, path, elements, fallback="minecraft:empty", processors=None, projection="rigid"):
        """elements: [(piece path, weight)] or [(piece path, weight, processors)]. The fallback pool is used when no
        piece fits (give dead ends a 'cap' piece that seals the doorway)."""
        entries = []
        for item in elements:
            location, weight = item[0], item[1]
            procs = item[2] if len(item) > 2 else processors
            if location == "minecraft:empty":
                entries.append({"element": {"element_type": "minecraft:empty_pool_element"}, "weight": weight})
                continue
            entries.append({"element": {"element_type": "minecraft:single_pool_element", "location": ns(location),
                                        "processors": ns(procs) if procs else {"processors": []},
                                        "projection": projection}, "weight": weight})
        self.pools[ns(path)] = {"fallback": ns(fallback) if fallback != "minecraft:empty" else fallback, "elements": entries}

    def structure(self, name, start_pool, biomes, size=7, max_distance=80, step="underground_structures",
                  adaptation="beard_thin", heightmap="WORLD_SURFACE_WG", start_height=0, start_jigsaw=None,
                  spawn_overrides=None, liquid_settings=None):
        data = {"type": "minecraft:jigsaw", "biomes": biomes, "max_distance_from_center": max_distance,
                "size": size, "spawn_overrides": spawn_overrides or {}, "start_height": {"absolute": start_height},
                "start_pool": ns(start_pool), "step": step, "terrain_adaptation": adaptation, "use_expansion_hack": False}
        if heightmap:
            data["project_start_to_heightmap"] = heightmap
        if start_jigsaw:
            data["start_jigsaw_name"] = ns(start_jigsaw)
        if liquid_settings:
            data["liquid_settings"] = liquid_settings
        self.json(f"worldgen/structure/{name}.json", data)

    def structure_set(self, name, spacing, separation, salt, structures=None, exclusion=None):
        """exclusion: (other structure set, chunks) keeps this set's structures that many chunks away from the other's."""
        structures = structures or [(name, 1)]
        placement = {"type": "minecraft:random_spread", "salt": salt, "separation": separation, "spacing": spacing}
        if exclusion:
            placement["exclusion_zone"] = {"other_set": ns(exclusion[0]), "chunk_count": exclusion[1]}
        self.json(f"worldgen/structure_set/{name}.json", {
            "placement": placement,
            "structures": [{"structure": ns(s), "weight": w} for s, w in structures]})

    def biome_tag(self, name, values):
        """data/skyrpg_world/tags/worldgen/biome/has_structure/<name>.json, for structure(biomes='#skyrpg_world:has_structure/<name>')."""
        self.json(f"tags/worldgen/biome/has_structure/{name}.json", {"values": values})

    def structure_tag(self, name, values):
        """A structure tag, e.g. for explorer maps that point at a dungeon."""
        self.json(f"tags/worldgen/structure/{name}.json", {"values": [ns(v) for v in values]})

    def loot_table(self, path, table):
        self.json(f"loot_table/{path}.json", modern_loot(table))

    def json(self, path, data, namespace=NAMESPACE):
        self.files[f"data/{namespace}/{path}"] = data

    # -- checks -------------------------------------------------------------------------------------------------
    def validate(self):
        problems = []
        names_in_pool = {}
        for pool_id, pool in self.pools.items():
            names = set()
            for entry in pool["elements"]:
                element = entry["element"]
                if element["element_type"] != "minecraft:single_pool_element":
                    continue
                path = element["location"].split(":", 1)[1]
                piece = self.pieces.get(path)
                if piece is None:
                    problems.append(f"pool {pool_id} lists {element['location']}, which isn't a piece")
                    continue
                names |= {j[2] for j in piece.jigsaws}
            names_in_pool[pool_id] = names
            fallback = pool["fallback"]
            if fallback != "minecraft:empty" and fallback not in self.pools:
                problems.append(f"pool {pool_id} falls back to {fallback}, which doesn't exist")
        for piece in self.pieces.values():
            w, h, d = piece.size
            for (x, y, z), orientation, name, target, pool in piece.jigsaws:
                front = orientation.split("_")[0]
                on_face = {"north": z == 0, "south": z == d - 1, "west": x == 0, "east": x == w - 1,
                           "down": y == 0, "up": y == h - 1}[front]
                if not on_face:
                    problems.append(f"{piece.path}: jigsaw at {(x, y, z)} faces {front} but isn't on that face")
                if pool == "minecraft:empty":
                    continue
                if pool not in self.pools:
                    problems.append(f"{piece.path}: jigsaw at {(x, y, z)} uses pool {pool}, which doesn't exist")
                elif target not in names_in_pool[pool]:
                    problems.append(f"{piece.path}: jigsaw at {(x, y, z)} targets {target}, but no piece in {pool} has a jigsaw called that")
        for path, data in self.files.items():
            if path.startswith(f"data/{NAMESPACE}/worldgen/structure/"):
                start = data["start_pool"]
                if start not in self.pools:
                    problems.append(f"{path}: start pool {start} doesn't exist")
        return problems

    def save(self, clean=None):
        """Validates, then writes everything. clean: folders (under data/skyrpg_world/structure/) to empty first."""
        problems = self.validate()
        if problems:
            raise SystemExit("Structure problems:\n  " + "\n  ".join(problems))
        base = self.resources / "data" / NAMESPACE
        for folder in clean or []:
            target = base / "structure" / folder
            if target.exists():
                for old in target.rglob("*.nbt"):
                    old.unlink()
        for piece in self.pieces.values():
            out = base / "structure" / f"{piece.path}.nbt"
            out.parent.mkdir(parents=True, exist_ok=True)
            nbt.write_file(out, piece.to_nbt())
        for pool_id, pool in self.pools.items():
            self.json(f"worldgen/template_pool/{pool_id.split(':', 1)[1]}.json", pool)
        for path, data in self.files.items():
            out = self.resources / path
            out.parent.mkdir(parents=True, exist_ok=True)
            out.write_text(json.dumps(data, indent=2) + "\n", encoding="utf-8")
        print(f"wrote {len(self.pieces)} pieces, {len(self.pools)} pools, {len(self.files) - len(self.pools)} other files")
