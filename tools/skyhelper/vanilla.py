"""Vanilla Minecraft recipes, read from the client jar Loom keeps in the Gradle cache, so SkyHelper never guesses one."""
import json
import os
import re
import zipfile

JARS = ["~/.gradle/caches/fabric-loom/26.3/minecraft-client.jar",
        "~/.gradle/caches/fabric-loom/26.3/minecraft-merged.jar"]
KINDS = {"minecraft:smelting": "Smelt in a furnace", "minecraft:blasting": "Blast furnace",
         "minecraft:smoking": "Smoker", "minecraft:campfire_cooking": "Campfire",
         "minecraft:stonecutting": "Stonecutter"}


class Vanilla:
    def __init__(self):
        self.lang, self.recipes = {}, {}   # recipes: result item id -> [text]
        path = next((os.path.expanduser(j) for j in JARS if os.path.exists(os.path.expanduser(j))), None)
        if not path:
            return
        with zipfile.ZipFile(path) as z:
            try:
                self.lang = json.loads(z.read("assets/minecraft/lang/en_us.json"))
            except KeyError:
                pass
            for n in z.namelist():
                if n.startswith("data/minecraft/recipe/") and n.endswith(".json"):
                    try:
                        r = json.loads(z.read(n))
                    except ValueError:
                        continue
                    out = (r.get("result") or {}).get("id") if isinstance(r.get("result"), dict) else r.get("result")
                    if not out:
                        continue
                    text = self.describe(r)
                    if text:
                        self.recipes.setdefault(out, []).append(text)
        self.by_name = {self.name(i).lower(): i for i in self.recipes}

    def name(self, ident):
        if ident.startswith("#"):
            return "any " + ident[1:].split(":")[-1].replace("_", " ")
        ns, _, path = ident.partition(":")
        return self.lang.get("item.%s.%s" % (ns, path)) or self.lang.get("block.%s.%s" % (ns, path)) or path.replace("_", " ")

    def ingredient(self, ing):
        if isinstance(ing, list):
            return " or ".join(self.ingredient(i) for i in ing)
        if isinstance(ing, dict):
            ing = ing.get("item") or ("#" + ing["tag"] if "tag" in ing else "?")
        return self.name(ing)

    def describe(self, r):
        kind = r.get("type", "")
        res = r.get("result") or {}
        count = res.get("count", 1) if isinstance(res, dict) else 1
        made = "%s%s" % (self.name(res["id"] if isinstance(res, dict) else res), " x%d" % count if count > 1 else "")
        if kind == "minecraft:crafting_shaped":
            # spelled out row by row: a small model mangles a letter grid with a legend
            key = {k: self.ingredient(v) for k, v in r.get("key", {}).items()}
            pattern = r.get("pattern", [])
            labels = {1: ["Only row"], 2: ["Top row", "Bottom row"], 3: ["Top row", "Middle row", "Bottom row"]}[len(pattern)]
            rows = ["  %s: %s" % (label, ", ".join(key.get(c, "empty") if c != " " else "empty" for c in row))
                    for label, row in zip(labels, pattern)]
            small = max(len(x) for x in pattern) <= 2 and len(pattern) <= 2
            return "%s: shaped recipe, %s:\n%s" % (
                made, "fits the 2x2 grid in your inventory" if small else "needs a crafting table", "\n".join(rows))
        if kind == "minecraft:crafting_shapeless":
            return "%s: shapeless recipe, any arrangement: %s" % (made, ", ".join(self.ingredient(i) for i in r.get("ingredients", [])))
        if kind in KINDS:
            return "%s: %s: %s" % (made, KINDS[kind], self.ingredient(r.get("ingredient")))
        if kind == "minecraft:smithing_transform":
            return "%s: smithing table: %s + %s + %s" % (made, self.ingredient(r.get("template")), self.ingredient(r.get("base")),
                                                        self.ingredient(r.get("addition")))
        return None

    def item_names(self):
        """Every vanilla item and block name (as the game writes it) -> its id. Blocks without an item are weeded out by the mod."""
        out = {}
        for key, name in self.lang.items():
            parts = key.split(".")
            if len(parts) == 3 and parts[0] in ("item", "block") and parts[1] == "minecraft":
                out.setdefault(name, "minecraft:" + parts[2])
        return out

    def names_in(self, text):
        """Vanilla item names that appear in a text, longest first (only items that have a recipe)."""
        t = " " + re.sub(r"[^a-z0-9 ]+", " ", text.lower()) + " "
        hits = sorted((n for n in self.by_name if len(n) >= 3 and (" " + n + " ") in t or (" " + n + "s ") in t),
                      key=len, reverse=True)
        kept = []
        for h in hits:
            if not any(h in k for k in kept):
                kept.append(h)
        return kept

    def recipe(self, name):
        n = re.sub(r"\s+", " ", name.lower().strip())
        n = re.sub(r"^(a|an|the) ", "", n)
        ident = self.by_name.get(n) or self.by_name.get(n.rstrip("s")) or (n if n in self.recipes else None)
        if not ident:
            hits = sorted((k for k in self.by_name if n in k), key=len)
            ident = self.by_name[hits[0]] if hits else None
        if not ident:
            return "No vanilla recipe makes \"%s\"." % name
        order = ("shaped", "any arrangement", "Smelt", "Stonecutter", "smithing", "Blast", "Smoker", "Campfire")
        found = sorted(self.recipes[ident], key=lambda t: next((i for i, k in enumerate(order) if k in t), 9))
        return "\n".join(found[:4])


if __name__ == "__main__":
    import sys
    v = Vanilla()
    print(len(v.recipes), "vanilla recipes")
    print(v.recipe(" ".join(sys.argv[1:]) or "piston"))
