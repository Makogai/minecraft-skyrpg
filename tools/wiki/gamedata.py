"""Reads the mods' data (JSON under data/*/skyrpg, lang files, a few Java enums) into plain Python structures."""
import glob
import json
import os
import re

B = chr(92)


def norm(p):
    return p.replace(B, "/")


def jload(path):
    try:
        with open(path, encoding="utf-8") as f:
            return json.load(f)
    except (OSError, ValueError):
        return None


def fmt_num(v):
    """Like the game's Enchant.format: no trailing zeros."""
    if abs(v - round(v)) < 1e-6:
        return str(int(round(v)))
    t = "%.2f" % v
    t = t.rstrip("0")
    return t.rstrip(".")


def humanize(s):
    return " ".join(w.capitalize() for w in re.split(r"[_/ ]+", s) if w)


def read_text(path):
    try:
        with open(path, encoding="utf-8") as f:
            return f.read()
    except OSError:
        return ""


class Game:
    def __init__(self, repo, vanilla_lang):
        self.repo = repo
        self.vanilla_lang = vanilla_lang
        self.lang = {}
        self.load_lang()
        self.load_stats()
        self.load_rarities()
        self.load_items()
        self.load_prices()
        self.load_recipes()
        self.load_crafting()
        self.load_shops()
        self.load_skills()
        self.load_loot()

    # ----------------------------------------------------------------- helpers
    def data_files(self, sub, recursive=False):
        """Yield (namespace, relative path without .json, file path) for data/<ns>/skyrpg/<sub>/**.json."""
        pat = os.path.join(self.repo, "*", "src", "main", "resources", "data", "*", "skyrpg", sub,
                           "**" if recursive else "*.json")
        if recursive:
            pat = os.path.join(pat, "*.json")
        for f in sorted(glob.glob(pat, recursive=recursive)):
            f = norm(f)
            ns = f.split("/data/")[1].split("/")[0]
            rel = f.split("/skyrpg/%s/" % sub)[1][:-5]
            yield ns, rel, f

    def load_lang(self):
        for f in glob.glob(os.path.join(self.repo, "*", "src", "main", "resources", "assets", "*", "lang", "en_us.json")):
            d = jload(f) or {}
            self.lang.update(d)

    # ----------------------------------------------------------------- stats and rarities
    def load_stats(self):
        src = read_text(os.path.join(self.repo, "core/src/main/java/com/skyrpg/core/stats/StatTypes.java"))
        self.stats = {}   # id -> dict(name, color, percent); insertion order is display order
        for m in re.finditer(r'add\("(\w+)",\s*"([^"]+)",\s*"[^"]*",\s*0x[0-9A-Fa-f]{2}([0-9A-Fa-f]{6}),\s*[\d.]+,\s*(true|false)', src):
            self.stats[m.group(1)] = {"name": m.group(2), "color": "#" + m.group(3).upper(), "percent": m.group(4) == "true"}
        if not self.stats:
            raise SystemExit("could not read stats from StatTypes.java")

    def load_rarities(self):
        src = read_text(os.path.join(self.repo, "items/src/main/java/com/skyrpg/items/Rarity.java"))
        self.rarity_colors = {}
        for m in re.finditer(r"\b([A-Z]+)\(0x[0-9A-Fa-f]{2}([0-9A-Fa-f]{6})\)", src):
            self.rarity_colors[m.group(1).lower()] = "#" + m.group(2).upper()
        if "common" not in self.rarity_colors:
            raise SystemExit("could not read rarities from Rarity.java")
        src = read_text(os.path.join(self.repo, "enchants/src/main/java/com/skyrpg/enchants/EnchantRarity.java"))
        self.enchant_rarity_colors = {}
        for m in re.finditer(r"\b([A-Z]+)\(0x[0-9A-Fa-f]{2}([0-9A-Fa-f]{6})\)", src):
            self.enchant_rarity_colors[m.group(1).lower()] = "#" + m.group(2).upper()

    # ----------------------------------------------------------------- items
    def name_of(self, item_id):
        it = self.items.get(item_id)
        if it:
            return it["name"]
        ns, path = item_id.split(":", 1)
        for pre in ("item", "block"):
            v = self.lang.get("%s.%s.%s" % (pre, ns, path.replace("/", ".")))
            if v:
                return v
        if ns == "minecraft":
            for pre in ("item", "block"):
                v = self.vanilla_lang.get("%s.minecraft.%s" % (pre, path))
                if v:
                    return v
        return None

    def known_item(self, item_id):
        return self.name_of(item_id) is not None

    def entity_name(self, eid):
        ns, path = eid.split(":", 1)
        v = self.lang.get("entity.%s.%s" % (ns, path))
        if v:
            return v
        v = self.vanilla_lang.get("entity.%s.%s" % (ns, path))
        return v or humanize(path)

    def rarity_color(self, rarity):
        return self.rarity_colors.get(rarity, self.rarity_colors["common"])

    def load_items(self):
        # kinds registered in ModItems
        src = read_text(os.path.join(self.repo, "items/src/main/java/com/skyrpg/items/ModItems.java"))
        kinds = {}
        for m in re.finditer(r'register\("(\w+)",\s*Kind\.(\w+)', src):
            kinds[m.group(1)] = m.group(2)
        for m in re.finditer(r'armor\("(\w+)",\s*ArmorType\.(\w+)', src):
            slot = m.group(2).lower()
            kinds["%s_%s" % (m.group(1), slot)] = slot.upper()
        for m in re.finditer(r'registerRod\("(\w+)"', src):
            kinds[m.group(1)] = "ROD"
        self.kinds = kinds
        self.items = {}
        for ns, rel, f in self.data_files("items"):
            d = jload(f)
            if not d:
                continue
            iid = "%s:%s" % (ns, rel)
            kind = kinds.get(rel) if ns == "skyrpg_items" else None
            if kind is None and "bow" in d:
                kind = "BOW"
            if kind is None and self.asset_exists(ns, "models/item/%s_blocking.json" % rel):
                kind = "SHIELD"
            self.items[iid] = {
                "id": iid, "ns": ns, "path": rel, "name": d.get("name", humanize(rel)),
                "rarity": d.get("rarity", "common"), "stats": d.get("stats", {}), "perks": d.get("perks", []),
                "ability": d.get("ability"), "description": d.get("description", ""), "set": d.get("set"),
                "type": d.get("type"), "kind": kind, "tier": d.get("tier"), "durability": d.get("durability"), "json": True,
            }
        # items that only exist in code: names from lang files, rarity from the registering Java line
        java = {}
        for jf in glob.glob(os.path.join(self.repo, "*", "src", "main", "java", "**", "*.java"), recursive=True):
            java[jf] = read_text(jf)
        for k, v in sorted(self.lang.items()):
            if not k.startswith("item."):
                continue
            _, ns, path = k.split(".", 2)
            iid = "%s:%s" % (ns, path)
            if iid in self.items or path.endswith("_spawn_egg"):
                continue
            rarity = "common"
            base = path
            pat = re.compile(r'"%s"[^;\n]*?Rarity\.(\w+)' % re.escape(base))
            for text in java.values():
                m = pat.search(text)
                if m:
                    rarity = m.group(1).lower()
                    break
            if rarity not in self.rarity_colors:
                rarity = "common"
            self.items[iid] = {
                "id": iid, "ns": ns, "path": path, "name": v, "rarity": rarity, "stats": {}, "perks": [],
                "ability": None, "description": "", "set": None, "type": None, "kind": None, "tier": None, "json": False,
            }
        self.item_sets = {}
        for ns, rel, f in self.data_files("item_sets"):
            d = jload(f)
            if d:
                self.item_sets["%s:%s" % (ns, rel)] = d

    def asset_exists(self, ns, rel):
        return bool(glob.glob(os.path.join(self.repo, "*", "src", "main", "resources", "assets", ns, *rel.split("/"))))

    def type_label(self, it):
        if it.get("type"):
            return it["type"]
        if it.get("kind"):
            return it["kind"].capitalize()
        return "Item"

    def item_page(self, it):
        """Which reference page lists this item."""
        kind, typ, path = it.get("kind"), (it.get("type") or ""), it["path"]
        if kind in ("SWORD", "WAND", "BOW"):
            return "items-weapons"
        if kind in ("HELMET", "CHESTPLATE", "LEGGINGS", "BOOTS", "SHIELD"):
            return "items-armour"
        if kind in ("PICKAXE", "AXE", "HOE", "ROD") or typ in ("Pickaxe", "Scythe", "Sickle", "Fishing Rod", "Axe", "Hoe"):
            return "items-tools"
        if kind in ("AMULET", "CLOAK", "BELT", "BRACERS"):
            return "items-accessories"
        if kind == "BACKPACK":
            return "items-other"
        if typ in ("Consumable", "Meal", "Bait") or path == "arcane_tome":
            return "items-consumables"
        if path.endswith("_pet") or path.endswith("_egg") or path == "griffin_whistle":
            return "items-other"
        if typ in ("Dungeon Key", "Spawner", "Sapling", "Seeds", "Planter", "Sprinkler", "Stump", "Placeable", "Block",
                   "Region Sigil"):
            return "items-other"
        return "items-materials"

    # ----------------------------------------------------------------- sell prices, recipes, shops
    def load_prices(self):
        self.prices = {}
        for ns, rel, f in self.data_files("sell_prices"):
            d = jload(f) or {}
            for k, v in d.items():
                if isinstance(v, (int, float)) and ":" in k:
                    self.prices[k] = v

    def load_recipes(self):
        self.recipes = {}   # result id -> list of recipe dicts
        for ns, rel, f in self.data_files("recipes"):
            d = jload(f)
            if not d or "result" not in d:
                continue
            d["_file"] = "%s:%s" % (ns, rel)
            self.recipes.setdefault(d["result"], []).append(d)

    # Common tags drawn as one representative item.
    TAG_ICONS = {"minecraft:planks": "minecraft:oak_planks", "minecraft:logs": "minecraft:oak_log",
                 "minecraft:wool": "minecraft:white_wool", "minecraft:stone_crafting_materials": "minecraft:cobblestone",
                 "minecraft:coals": "minecraft:coal", "minecraft:wooden_slabs": "minecraft:oak_slab"}
    STATIONS = {"crafting_shaped": "Crafting Table", "crafting_shapeless": "Crafting Table", "smelting": "Furnace",
                "blasting": "Blast Furnace", "smoking": "Smoker", "campfire_cooking": "Campfire"}

    def crafting_ingredient(self, value):
        """An ingredient in vanilla recipe JSON (an id, "#tag", {"item": ..}, {"tag": ..} or a list of those) as one item id."""
        if isinstance(value, list):
            return self.crafting_ingredient(value[0]) if value else None
        if isinstance(value, dict):
            value = value.get("item") or ("#" + value["tag"] if "tag" in value else None)
        if not isinstance(value, str):
            return None
        if value.startswith("#"):
            return self.TAG_ICONS.get(value[1:], value[1:])
        return value

    def load_crafting(self):
        """Vanilla-format recipes in data/<ns>/recipe/ (crafting table and furnaces): result id -> list of
        {"station", "grid": [9 ids or None] for shaped/shapeless, "in": id for cooking, "count"}."""
        self.crafting = {}
        pat = os.path.join(self.repo, "*", "src", "main", "resources", "data", "*", "recipe", "*.json")
        for f in sorted(glob.glob(pat)):
            d = jload(norm(f))
            if not d or "result" not in d:
                continue
            kind = d.get("type", "").split(":")[-1]
            if kind not in self.STATIONS:
                continue
            res = d["result"]
            rid = res if isinstance(res, str) else res.get("id") or res.get("item")
            count = 1 if isinstance(res, str) else res.get("count", 1)
            r = {"station": self.STATIONS[kind], "count": count}
            if kind == "crafting_shaped":
                key = {k: self.crafting_ingredient(v) for k, v in d.get("key", {}).items()}
                grid = [None] * 9
                for y, row in enumerate(d.get("pattern", [])[:3]):
                    for x, ch in enumerate(row[:3]):
                        grid[y * 3 + x] = key.get(ch)
                r["grid"] = grid
            elif kind == "crafting_shapeless":
                items = [self.crafting_ingredient(v) for v in d.get("ingredients", [])][:9]
                r["grid"] = items + [None] * (9 - len(items))
                r["shapeless"] = True
            else:
                r["in"] = self.crafting_ingredient(d.get("ingredient"))
                if not r["in"]:
                    continue
            self.crafting.setdefault(rid, []).append(r)

    def load_shops(self):
        self.shop_offers = {}  # item id -> list of (shop title, price, count)
        self.shops = {}
        for ns, rel, f in self.data_files("shops"):
            d = jload(f)
            if not d:
                continue
            self.shops[rel] = d
            for o in d.get("offers", []):
                self.shop_offers.setdefault(o["item"], []).append((d.get("title", humanize(rel)), o, o.get("count", 1)))

    def load_skills(self):
        self.skills = {}
        for ns, rel, f in self.data_files("skills"):
            d = jload(f)
            if d:
                self.skills["%s:%s" % (ns, rel)] = d

    def skill_name(self, sid):
        d = self.skills.get(sid)
        if d:
            return d.get("name", humanize(sid.split(":")[1]))
        if "/" in sid:
            line = sid.split("/", 1)[1]
            return "%s slayer" % self.lang.get("entity.skyrpg_combat." + line, humanize(line))
        return humanize(sid.split(":")[1])

    # ----------------------------------------------------------------- loot tables (where things drop)
    def load_loot(self):
        """item id -> list of (kind, source key). kinds: boss, reward, entity, block, chest."""
        self.loot_sources = {}
        pat = os.path.join(self.repo, "*", "src", "main", "resources", "data", "*", "loot_table", "**", "*.json")
        for f in sorted(glob.glob(pat, recursive=True)):
            f = norm(f)
            ns = f.split("/data/")[1].split("/")[0]
            rel = f.split("/loot_table/")[1][:-5]
            group, _, key = rel.partition("/")
            if group not in ("bosses", "rewards", "entities", "blocks", "chests"):
                continue
            d = jload(f)
            if not d:
                continue
            found = []
            self._collect_items(d, found)
            for iid in found:
                self.loot_sources.setdefault(iid, [])
                entry = (group, ns, key)
                if entry not in self.loot_sources[iid]:
                    self.loot_sources[iid].append(entry)

    def _collect_items(self, node, out):
        if isinstance(node, dict):
            if node.get("type") == "minecraft:item" and isinstance(node.get("name"), str):
                out.append(node["name"])
            for v in node.values():
                self._collect_items(v, out)
        elif isinstance(node, list):
            for v in node:
                self._collect_items(v, out)

    # ----------------------------------------------------------------- the other data sets
    def read_dir(self, sub, recursive=False):
        out = {}
        for ns, rel, f in self.data_files(sub, recursive):
            d = jload(f)
            if d is not None:
                out["%s:%s" % (ns, rel)] = d
        return out
