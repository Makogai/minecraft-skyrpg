"""Generates the reference pages (items, enchants, mobs, bosses, pets, mounts, collections, quests, prices, regions)."""
import glob
import os
import re

from gamedata import fmt_num, humanize, read_text, jload
import itempages
from model import (LISTED_IDS, PAGE_LIST, item_anchor, plain, span, merge_spans)

GREY = "#AAAAAA"
GOLD = "#FFAA00"
GREEN = "#55FF55"
CYAN = "#55FFFF"
RARITY_ORDER = ["common", "uncommon", "rare", "epic", "legendary", "mythic", "special"]
ORDINAL = {c: i for i, c in enumerate(RARITY_ORDER)}


def roman(n):
    vals = [(10, "X"), (9, "IX"), (5, "V"), (4, "IV"), (1, "I")]
    out = ""
    for v, s in vals:
        while n >= v:
            out += s
            n -= v
    return out


def coins(n):
    return "{:,}".format(int(n)) + " coins" if n != 1 else "1 coin"


class Ref:
    def __init__(self, game):
        self.g = game
        self.pages = {}
        self.item_page = {}   # item id -> reference page id
        self.used_items = set()
        self.boss_names = {}
        self.build_all()

    # ------------------------------------------------------------------ helpers
    def ispan(self, item_id, text=None, **extra):
        """An item span with the item's name in its rarity colour."""
        g = self.g
        it = g.items.get(item_id)
        name = text or g.name_of(item_id) or item_id
        s = {"x": name, "item": item_id, "icon": item_id}
        if it:
            s["color"] = g.rarity_color(it["rarity"])
        self.used_items.add(item_id)
        s.update(extra)
        return s

    def item_list_spans(self, entries, sep=", "):
        """entries: list of (item_id, count) -> spans like '4x Prism Fragment, Heartstone'."""
        out = []
        for i, (iid, n) in enumerate(entries):
            if i:
                out.append(span(sep))
            if n and n != 1:
                out.append(span("%sx " % fmt_num(n)))
            out.append(self.ispan(iid))
        return out

    def add_page(self, pid, title, summary, icon, blocks, order=None):
        cat = "reference"
        if order is None:
            order = PAGE_LIST["reference"].index(pid) + 1
        self.pages[pid] = {"id": pid, "title": title, "category": cat, "order": order, "icon": icon,
                           "summary": summary, "blocks": blocks, "generated": True}

    @staticmethod
    def h2(text, anchor=None):
        from model import slug
        return {"t": "h2", "text": text, "anchor": anchor or slug(text)}

    @staticmethod
    def h3(text, anchor=None):
        from model import slug
        return {"t": "h3", "text": text, "anchor": anchor or slug(text)}

    @staticmethod
    def p(*spans):
        return {"t": "p", "s": merge_spans(list(spans))}

    @staticmethod
    def ul(rows):
        return {"t": "ul", "items": [{"s": merge_spans(r)} for r in rows]}

    @staticmethod
    def table(head, rows, align=None):
        return {"t": "table", "head": [[span(h)] if isinstance(h, str) else h for h in head],
                "rows": rows, "align": align or ["l"] * len(head)}

    def uniq_anchors(self, blocks):
        seen = {}
        for b in blocks:
            if b.get("t") in ("h2", "h3"):
                a = b["anchor"]
                n = seen.get(a, 0)
                seen[a] = n + 1
                if n:
                    b["anchor"] = "%s-%d" % (a, n + 1)

    # ------------------------------------------------------------------ item cards
    def stat_rank(self, sid):
        """Weapon damage first, then the stats in the game's display order."""
        if sid == "damage":
            return -1
        order = list(self.g.stats.keys())
        return order.index(sid) if sid in order else 99

    def stat_line(self, sid, value):
        st = self.g.stats.get(sid)
        if not st:
            return [span(humanize(sid) + ": ", color=GREY), span("+" + fmt_num(value), color=GREY)]
        sign = "+" if value >= 0 else "-"
        txt = sign + fmt_num(abs(value)) + ("%" if st["percent"] else "")
        return [span(st["name"] + ": ", color=GREY), span(txt, color=st["color"])]

    def ability_lines(self, ab):
        lines = []
        head = [span("Ability: ", color=GOLD), span(ab.get("name", ""), color=GOLD, b=True)]
        lines.append(head)
        if ab.get("description"):
            lines.append([span(ab["description"], color=GREY)])
        meta = []
        if ab.get("mana"):
            meta.append(span("Mana cost: ", color=GREY))
            meta.append(span(fmt_num(ab["mana"]), color=CYAN))
        if ab.get("cooldown"):
            if meta:
                meta.append(span("   "))
            meta.append(span("Cooldown: ", color=GREY))
            meta.append(span(fmt_num(ab["cooldown"]) + "s", color=GREEN))
        if meta:
            lines.append(meta)
        return lines

    def sources(self, it):
        g = self.g
        iid = it["id"]
        out = []
        for title, offer, count in g.shop_offers.get(iid, []):
            out.append("Sold by %s for %s%s%s" % (title, self.offer_price(offer), "" if count == 1 else " (%d)" % count,
                                                  self.offer_req(offer)))
        for group, ns, key in g.loot_sources.get(iid, []):
            ref = "%s:%s" % (ns, key)
            if group == "bosses":
                nm = self.boss_names.get(ref)
                out.append("Dropped by %s" % nm if nm else "Boss drop")
            elif group == "rewards":
                nm = self.boss_names.get(ref)
                out.append("Reward for clearing %s" % nm if nm else "Dungeon reward")
            elif group == "entities":
                out.append("Dropped by %s" % g.entity_name(ref))
            elif group == "blocks":
                if key == it["path"]:
                    continue
                nm = g.lang.get("block.%s.%s" % (ns, key))
                if nm:
                    out.append("Mined from %s" % nm)
            elif group == "chests":
                out.append("Found in %s chests" % humanize(key.replace("/", " ")).lower())
        for key, wb in self.world_bosses.items():
            if wb.get("rare") == iid:
                out.append("Rare drop from %s (%s%%)" % (wb["name"], fmt_num(wb.get("rare_chance", 0) * 100)))
            if wb.get("bonus") == iid:
                out.append("Bonus drop from %s (%s%%)" % (wb["name"], fmt_num(wb.get("bonus_chance", 0) * 100)))
        for key, sl in self.slayers.items():
            nm = sl.get("name")
            if sl.get("token") == iid:
                out.append("Slayer token from %s" % nm)
            if sl.get("rare") == iid:
                out.append("Rare drop from %s" % nm)
        for key, rg in self.regions.items():
            for dr in rg.get("drops", []):
                if dr.get("item") == iid:
                    out.append("Dropped by monsters in %s" % rg["name"])
        for title, q in self.quest_rewards.get(iid, []):
            out.append("Quest reward: %s" % title)
        seen, uniq = set(), []
        for s in out:
            if s not in seen:
                seen.add(s)
                uniq.append(s)
        return uniq

    def offer_price(self, offer):
        if offer.get("price") is not None:
            return coins(offer["price"])
        parts = ["%sx %s" % (c.get("count", 1), self.g.name_of(c["item"]) or c["item"]) for c in offer.get("costs", [])]
        return " + ".join(parts) or "free"

    def offer_req(self, offer):
        req = offer.get("requires")
        if req and req.get("skill"):
            return " (needs %s level %s)" % (self.g.skill_name(req["skill"]), req.get("level", 1))
        return ""

    def recipe_lines(self, it):
        g = self.g
        lines = []
        for r in g.recipes.get(it["id"], [])[:2]:
            ing = list(r["ingredients"].items())
            head = [span("Recipe: ", color=GREY)]
            if r.get("count", 1) != 1:
                head.insert(0, span("Makes %d. " % r["count"], color=GREY))
            lines.append(head + self.item_list_spans(ing))
            req = r.get("requires")
            if req and req.get("skill"):
                lines.append([span("Requires %s level %s" % (g.skill_name(req["skill"]), req.get("level", 1)), color=GREY)])
        return lines

    def item_card(self, it):
        g = self.g
        tip = []
        stat_ids = sorted(it["stats"].keys(), key=self.stat_rank)
        for sid in stat_ids:
            tip.append(self.stat_line(sid, it["stats"][sid]))
        if it["perks"]:
            if tip:
                tip.append([span(" ")])
            for pk in it["perks"]:
                tip.append([span(pk, color=GREEN)])
        if it["ability"]:
            if tip:
                tip.append([span(" ")])
            tip.extend(self.ability_lines(it["ability"]))
        if it["set"]:
            sd = g.item_sets.get(it["set"])
            if sd:
                if tip:
                    tip.append([span(" ")])
                tip.append([span("Set: ", color=GREY), span(sd.get("name", ""), color=GOLD)])
        if it["description"]:
            if tip:
                tip.append([span(" ")])
            tip.append([span(it["description"], color=GREY, i=True)])
        if tip:
            tip.append([span(" ")])
        label = "%s %s" % (it["rarity"].upper(), g.type_label(it).upper()) if it.get("kind") or it.get("type") else it["rarity"].upper()
        color = g.rarity_color(it["rarity"])
        tip.append([span(label, color=color, b=True)])
        lines = list(tip)
        info = []
        price = g.prices.get(it["id"])
        if price is not None:
            info.append([span("Sells for ", color=GREY), span(coins(price), color=GOLD)])
        info.extend(self.recipe_lines(it))
        src = self.sources(it)
        if src:
            info.append([span("How to get: ", color=GREY), span("; ".join(src[:5]) + ("; and more" if len(src) > 5 else ""))])
        card = {"t": "item", "id": it["id"], "icon": it["id"], "name": it["name"], "rarity": it["rarity"], "color": color,
                "anchor": item_anchor(it["id"]), "link": item_anchor(it["id"]), "lines": lines + info, "tip": len(lines)}
        self.used_items.add(it["id"])
        return card


    # ------------------------------------------------------------------ {{items:...}} tables for hand-written pages
    def items_table(self, ids):
        g = self.g
        rows = []
        for iid in ids:
            it = g.items.get(iid)
            row = [[self.ispan(iid)]]
            if it:
                row.append([span(it["rarity"].capitalize(), color=g.rarity_color(it["rarity"]))])
                sids = sorted(it["stats"].keys(), key=self.stat_rank)[:4]
                txt = ", ".join(" ".join(plain(self.stat_line(sk, it["stats"][sk])).split()) for sk in sids)
                row.append([span(txt)])
            else:
                row.append([span("")])
                row.append([span("")])
            price = g.prices.get(iid)
            row.append([span(coins(price) if price is not None else "")])
            src = self.sources(it) if it else ["Sold by %s for %s" % (t, self.offer_price(o)) for t, o, c in g.shop_offers.get(iid, [])]
            row.append([span("; ".join(src[:2]))])
            rows.append(row)
        return self.table(["Item", "Rarity", "Key stats", "Sells for", "How to get"], rows, ["l"] * 5)

    # ------------------------------------------------------------------ build everything
    def build_all(self):
        g = self.g
        self.world_bosses = g.read_dir("world_bosses")
        self.world_bosses = {k.split(":", 1)[1]: v for k, v in self.world_bosses.items()}
        self.dungeon_bosses = g.read_dir("dungeon_bosses")
        self.dungeons = g.read_dir("dungeons")
        self.slayers = {k.split(":", 1)[1]: v for k, v in g.read_dir("slayers").items()}
        self.regions = {k: v for k, v in g.read_dir("danger_regions").items()}
        self.regions = {k.split(":", 1)[1]: v for k, v in self.regions.items()}
        for k, v in self.dungeon_bosses.items():
            self.boss_names[k] = v.get("name", humanize(k.split(":")[1]))
        for k, v in self.world_bosses.items():
            self.boss_names["skyrpg_combat:" + k] = v.get("name", humanize(k))
        self.quests = g.read_dir("quests", recursive=True)
        self.quest_rewards = {}
        for k, q in self.quests.items():
            for r in (q.get("rewards") or {}).get("items", []) or []:
                iid = r.get("item") if isinstance(r, dict) else r
                if isinstance(iid, str):
                    self.quest_rewards.setdefault(iid, []).append((q.get("title", k), q))
        self.apply_item_overrides()
        self.build_item_pages()
        self.item_detail = itempages.build(self)   # item id -> its own page id
        self.build_enchants()
        self.build_mobs()
        self.build_bosses()
        self.build_pets()
        self.build_mounts()
        self.build_collections()
        self.build_quests()
        self.build_prices()
        self.build_regions()
        for p in self.pages.values():
            self.uniq_anchors(p["blocks"])

    def apply_item_overrides(self):
        for iid, it in self.g.items.items():
            if not it["json"]:
                if it["path"].endswith("_pet") or it["path"].endswith("_egg"):
                    it["rarity"] = "epic" if it["path"] == "golden_pet_egg" else "uncommon"
                    if it["path"].endswith("_pet"):
                        it["type"] = "Pet"
                    else:
                        it["type"] = "Pet egg"
                elif it["path"] == "griffin_whistle":
                    it["type"] = "Mount"
                elif it["path"] == "arcane_tome":
                    it["type"] = "Consumable"
                elif it["ns"] == "skyrpg_mining":
                    it["type"] = "Material"

    # ------------------------------------------------------------------ item pages
    def sort_key(self, it):
        dmg = it["stats"].get("damage", 0)
        return (ORDINAL.get(it["rarity"], 9), dmg, it["tier"] or 0, it["name"])

    def build_item_pages(self):
        g = self.g
        by_page = {}
        for it in g.items.values():
            by_page.setdefault(g.item_page(it), []).append(it)
        for pid, items in by_page.items():
            for it in items:
                self.item_page[it["id"]] = pid
        self.page_weapons(by_page.get("items-weapons", []))
        self.page_armour(by_page.get("items-armour", []))
        self.page_tools(by_page.get("items-tools", []))
        self.page_accessories(by_page.get("items-accessories", []))
        self.page_grouped("items-materials", "Materials", "Crafting materials, drops, ores, trophies and currencies.",
                          "minecraft:iron_ingot", by_page.get("items-materials", []),
                          "What you gather, mine, catch and take from mobs and bosses, and what you craft from it.")
        self.page_grouped("items-consumables", "Consumables", "Meals, bait, tomes and other items you use up.",
                          "minecraft:bread", by_page.get("items-consumables", []),
                          "Items that are used up when you eat, apply or cast them.")
        self.page_grouped("items-other", "Other items", "Keys, seeds, saplings, backpacks, spawners and the rest.",
                          "minecraft:barrel", by_page.get("items-other", []),
                          "Items that are not gear, materials or consumables.")

    def cards(self, items):
        return [self.item_card(it) for it in items]

    def page_weapons(self, items):
        blocks = [self.p(span("Every weapon in the game, from the weakest to the strongest. Damage is the weapon's base damage "
                              "before Strength and critical hits."))]
        groups = {}
        for it in items:
            groups.setdefault(it["rarity"], []).append(it)
        for r in RARITY_ORDER:
            if r in groups:
                blocks.append(self.h2("%s weapons" % r.capitalize()))
                blocks.extend(self.cards(sorted(groups[r], key=self.sort_key)))
        self.add_page("items-weapons", "Weapons", "Every sword and wand with its damage, stats and abilities.",
                      "minecraft:iron_sword", blocks)

    def page_armour(self, items):
        g = self.g
        blocks = [self.p(span("Armour comes in sets of four pieces. Wearing all four gives the set bonus. "
                              "Each set is listed with its bonus, then its pieces from helmet to boots."))]
        sets, loose = {}, []
        for it in items:
            if it["set"]:
                sets.setdefault(it["set"], []).append(it)
            else:
                loose.append(it)
        slot_order = {"HELMET": 0, "CHESTPLATE": 1, "LEGGINGS": 2, "BOOTS": 3}

        def set_key(sid):
            pcs = sets[sid]
            return (max(ORDINAL.get(p["rarity"], 9) for p in pcs), sum(p["stats"].get("defense", 0) for p in pcs), sid)

        for sid in sorted(sets, key=set_key):
            sd = g.item_sets.get(sid, {})
            blocks.append(self.h2(sd.get("name") or humanize(sid.split(":")[1])))
            bonus = sd.get("bonus")
            if bonus:
                desc = bonus.get("description", "")
                stats = []
                for sk, sv in (bonus.get("stats") or {}).items():
                    st = g.stats.get(sk, {"name": humanize(sk), "percent": False})
                    if st["name"].lower() not in desc.lower():
                        stats.append("+%s%s %s" % (fmt_num(sv), "%" if st["percent"] else "", st["name"]))
                text = "Set bonus (%d pieces): %s. %s%s" % (sd.get("pieces", 4), bonus.get("name", ""), desc,
                                                          (" " + ", ".join(stats) + ".") if stats else "")
                blocks.append(self.p(span(text.replace("..", "."), i=True)))
            blocks.extend(self.cards(sorted(sets[sid], key=lambda p: slot_order.get(p["kind"], 9))))
        if loose:
            blocks.append(self.h2("Other armour"))
            blocks.extend(self.cards(sorted(loose, key=self.sort_key)))
        self.add_page("items-armour", "Armour", "Every armour set with its pieces, stats and set bonus.",
                      "minecraft:iron_chestplate", blocks)

    def page_tools(self, items):
        def group(it):
            k, t = it.get("kind"), it.get("type") or ""
            if k == "PICKAXE" or t == "Pickaxe":
                return "Pickaxes"
            if k == "AXE" or t == "Axe":
                return "Axes"
            if k == "HOE" or t in ("Scythe", "Sickle", "Hoe"):
                return "Hoes, sickles and scythes"
            if k == "ROD" or t == "Fishing Rod":
                return "Fishing rods"
            return "Other tools"
        order = ["Pickaxes", "Axes", "Hoes, sickles and scythes", "Fishing rods", "Other tools"]
        groups = {}
        for it in items:
            groups.setdefault(group(it), []).append(it)
        blocks = [self.p(span("Mining pickaxes, felling axes, farming tools and fishing rods, from the first tier to the last."))]
        for name in order:
            if name in groups:
                blocks.append(self.h2(name))
                blocks.extend(self.cards(sorted(groups[name], key=self.sort_key)))
        self.add_page("items-tools", "Tools", "Pickaxes, axes, scythes and fishing rods by tier.", "minecraft:iron_pickaxe", blocks)

    def page_accessories(self, items):
        order = ["AMULET", "CLOAK", "BELT", "BRACERS"]
        names = {"AMULET": "Amulets", "CLOAK": "Cloaks", "BELT": "Belts", "BRACERS": "Bracers"}
        blocks = [self.p(span("Accessories go in their own slots: one each of amulet, cloak, belt and bracers."))]
        for k in order:
            grp = [it for it in items if it.get("kind") == k]
            if grp:
                blocks.append(self.h2(names[k]))
                blocks.extend(self.cards(sorted(grp, key=self.sort_key)))
        self.add_page("items-accessories", "Accessories", "Amulets, cloaks, belts and bracers.", "minecraft:amethyst_shard", blocks)

    def page_grouped(self, pid, title, summary, icon, items, intro):
        blocks = [self.p(span(intro))]
        groups = {}
        for it in items:
            label = self.g.type_label(it) if (it.get("type") or it.get("kind")) else "Materials"
            if it.get("kind") == "BACKPACK":
                label = "Backpacks"
            groups.setdefault(label, []).append(it)
        for label in sorted(groups, key=lambda s: (-len(groups[s]) < -3, s)):
            pl = label if label.endswith("s") else label + "s"
            blocks.append(self.h2(pl))
            blocks.extend(self.cards(sorted(groups[label], key=lambda it: (ORDINAL.get(it["rarity"], 9), it["name"]))))
        self.add_page(pid, title, summary, icon, blocks)

    # ------------------------------------------------------------------ enchants
    def build_enchants(self):
        g = self.g
        ench = g.read_dir("enchants")
        rules = g.read_dir("enchanting").get("skyrpg_enchants:rules", {})
        groups = {}
        for k, e in ench.items():
            groups.setdefault(e.get("rarity", "common"), []).append(e)
        blocks = [self.p(span("Enchantments come from the enchanting table (up to the table level shown) or as tomes. "
                              "Ultimate enchants are tome-only and an item can hold just one. The effect column shows the maximum level."))]

        def fill(e, level):
            values = e.get("values", {})

            def rep(m):
                name, pct = m.group(1), m.group(2)
                arr = values.get(name)
                if not arr:
                    return m.group(0)
                v = arr[min(level, len(arr)) - 1]
                return fmt_num(v * 100) + "%" if pct else fmt_num(v)
            return re.sub(r"\{(\w+)(%?)\}", rep, e.get("description", ""))

        for r in ["ultimate", "legendary", "epic", "rare", "uncommon", "common"]:
            if r not in groups:
                continue
            blocks.append(self.h2("%s enchants" % r.capitalize()))
            rows = []
            color = g.enchant_rarity_colors.get(r, "#FFFFFF")
            for e in sorted(groups[r], key=lambda x: x["name"]):
                ml = e.get("max_level", 1)
                applies = ", ".join(a.capitalize() for a in e.get("applies_to", []))
                eff = [span(fill(e, ml))]
                if e.get("source"):
                    eff.append(span(" " + e["source"] + ".", i=True))
                elif not e.get("table_max") and r != "ultimate":
                    pass
                tm = e.get("table_max")
                rows.append([[span(e["name"], color=color, b=True)], [span(applies)],
                             [span(roman(ml) if ml > 1 else "I")], eff])
            blocks.append(self.table(["Enchant", "Applies to", "Max level", "Effect at max level"], rows, ["l", "l", "c", "l"]))
        self.add_page("enchant-list", "Enchantments", "Every enchantment, what it applies to and what it does at its top level.",
                      "minecraft:enchanted_book", blocks)

    # ------------------------------------------------------------------ mobs
    def java_mob_docs(self):
        docs = {}
        for f in glob.glob(os.path.join(self.g.repo, "combat/src/main/java/com/skyrpg/combat/mob/*Mobs.java")):
            text = read_text(f)
            head = text.split("public final class")[0]
            for m in re.finditer(r"<li>(.*?)</li>", head, re.S):
                raw = re.sub(r"\s*\n\s*\*\s*", " ", m.group(1))
                raw = re.sub(r"\{@code ([^}]*)\}", r"\1", raw)
                raw = re.sub(r"\([^()]*\)", "", raw)   # drop "(see Foo)" and id notes
                raw = re.sub(r"\s+", " ", raw).strip()
                name, _, rest = raw.partition(":")
                sentences = re.split(r"(?<=\.)\s+", rest.strip())
                keep = [s for s in sentences if not re.search(r"world module|spawn egg|creative|No spawn egg|Spawn egg", s, re.I)]
                docs[name.replace("The ", "").strip().lower()] = " ".join(keep).strip()
        return docs

    def entity_health(self, key):
        cls = "".join(w.capitalize() for w in key.split("_")) + "Entity.java"
        for f in glob.glob(os.path.join(self.g.repo, "combat/src/main/java/com/skyrpg/combat/mob", cls)):
            m = re.search(r"MAX_HEALTH,\s*(\d+)", read_text(f))
            if m:
                return int(m.group(1))
        return None

    def entity_drops(self, ns, key):
        out = []
        for iid, srcs in self.g.loot_sources.items():
            if ("entities", ns, key) in srcs and self.g.known_item(iid):
                out.append(iid)
        return out

    def build_mobs(self):
        g = self.g
        docs = self.java_mob_docs()
        boss_keys = {"grave_tyrant", "broodmother", "bone_marshal", "void_stalker", "iron_thane", "magma_tyrant", "ender_wyrm"}
        mobs = []
        for k, v in g.lang.items():
            if k.startswith("entity.skyrpg_combat."):
                key = k.split(".", 2)[2]
                if key not in boss_keys:
                    mobs.append((key, v))
        blocks = [self.p(span("Monsters that belong to SkyRPG, on top of the vanilla ones. Every mob has a level that depends on how far "
                              "you are from spawn and how deep you are, and more levels mean more health and damage. "
                              "Bosses are on the "), span("Bosses", link="boss-list"), span(" page."))]
        for key, name in mobs:
            blocks.append(self.h3(name))
            desc = docs.get(name.lower(), "")
            if desc:
                blocks.append(self.p(span(desc)))
            facts = []
            hp = self.entity_health(key)
            if hp:
                facts.append([span("Base health: "), span("%d" % hp, color=None), span(" (vanilla points, before level scaling)")])
            drops = self.entity_drops("skyrpg_combat", key)
            if drops:
                facts.append([span("Drops: ")] + self.item_list_spans([(d, 1) for d in drops]))
            if facts:
                blocks.append(self.ul(facts))
        sea = g.read_dir("sea_creatures")
        if sea:
            blocks.append(self.h2("Sea creatures"))
            blocks.append(self.p(span("Fishing sometimes pulls up a sea creature instead of a fish. Sea Creature Chance sets how often. "
                                      "Each one drops Tidescales and gives Fishing XP when it is killed. Lava creatures bite in the Nether's lava, "
                                      "with a Magmaline Rod."), span("")))
            rows = []
            for k, c in sorted(sea.items(), key=lambda kv: (kv[1].get("level", 0), kv[0])):
                rows.append([[span(c.get("name", humanize(k.split(":")[1])), b=True)], [span(str(c.get("level", 0)))],
                             [span("Lava" if c.get("fluid") == "lava" else "Water")], [span(g.entity_name(c.get("entity", "minecraft:zombie")))],
                             [span("{:,}".format(c.get("xp", 0)))], [span(str(c.get("scales", 1)))]])
            blocks.append(self.table(["Creature", "Level", "Fished from", "Looks like", "Fishing XP", "Tidescales"], rows,
                                     ["l", "r", "l", "l", "r", "r"]))
        self.add_page("mob-list", "Mobs", "Every SkyRPG monster, where it lives and what it drops.", "minecraft:bone", blocks)

    # ------------------------------------------------------------------ bosses
    def move_names(self, moves):
        seen = []
        for m in moves:
            t = m.get("type")
            if t and t not in seen:
                seen.append(t)
        return ", ".join(humanize(t) for t in seen)

    def boss_loot(self, ns, key):
        out = []
        for iid, srcs in self.g.loot_sources.items():
            if ("bosses", ns, key) in srcs or ("rewards", ns, key) in srcs:
                if self.g.known_item(iid):
                    out.append(iid)
        return out

    def build_bosses(self):
        g = self.g
        blocks = [self.p(span("Dungeon bosses, world bosses and slayer bosses. Health and damage here are the boss's own base values; "
                              "more players in a dungeon make its health grow."))]
        # dungeon bosses
        dpage = {}
        for k, d in self.dungeons.items():
            dpage[k] = k.split(":", 1)[1].replace("_", "-")
        by_dungeon = []
        for k, b in sorted(self.dungeon_bosses.items(), key=lambda kv: (kv[1].get("level", 0), kv[0])):
            by_dungeon.append((k, b))
        blocks.append(self.h2("Dungeon bosses"))
        for k, b in by_dungeon:
            ns, key = k.split(":", 1)
            blocks.append(self.h3(b.get("name", humanize(key))))
            facts = []
            dg = b.get("dungeon")
            if dg and dg in self.dungeons:
                dname = self.dungeons[dg].get("name", humanize(dg))
                pid = dpage[dg]
                facts.append([span("Dungeon: "), span(dname, link=pid if pid in LISTED_IDS else None)])
            if b.get("level"):
                facts.append([span("Level %s" % b["level"])])
            if b.get("health"):
                facts.append([span("Health: %s, damage: %s" % ("{:,}".format(b["health"]), fmt_num(b.get("damage", 0))))])
            if b.get("coins"):
                c = b["coins"]
                facts.append([span("Coins: %s to %s" % ("{:,}".format(c[0]), "{:,}".format(c[-1])))])
            if b.get("first_clear"):
                fc = b["first_clear"]
                facts.append([span("First clear: %s" % coins(fc.get("coins", 0)))])
            if b.get("moves"):
                facts.append([span("Moves: " + self.move_names(b["moves"]))])
            drops = self.boss_loot(ns, key)
            if drops:
                facts.append([span("Loot: ")] + self.item_list_spans([(d, 1) for d in drops]))
            blocks.append(self.ul(facts))
        # world bosses
        blocks.append(self.h2("World bosses"))
        for key, b in sorted(self.world_bosses.items(), key=lambda kv: kv[1].get("level", 0)):
            blocks.append(self.h3(b.get("name", humanize(key))))
            facts = [[span("Level %s, health %s, damage %s" % (b.get("level", "?"), "{:,}".format(b.get("health", 0)),
                                                              fmt_num(b.get("damage", 0))))]]
            if b.get("coins"):
                facts.append([span("Coins: %s to %s" % ("{:,}".format(b["coins"][0]), "{:,}".format(b["coins"][-1])))])
            if b.get("combat_xp"):
                facts.append([span("Combat XP: %s" % b["combat_xp"])])
            if b.get("valor_seals"):
                facts.append([span("Valor Seals: %d to %d" % (b["valor_seals"][0], b["valor_seals"][-1]))])
            if b.get("rare"):
                facts.append([span("Rare drop (%s%%): " % fmt_num(b.get("rare_chance", 0) * 100)), self.ispan(b["rare"])])
            if b.get("bonus"):
                facts.append([span("Bonus drop (%s%%): " % fmt_num(b.get("bonus_chance", 0) * 100)), self.ispan(b["bonus"])])
            blocks.append(self.ul(facts))
        # slayers
        blocks.append(self.h2("Slayer bosses"))
        blocks.append(self.p(span("Slayer quests are bought from the Slayer Master. Kill enough of the listed mobs, then fight the boss. "
                                  "Higher tiers need a higher slayer level.")))
        for key, sl in sorted(self.slayers.items(), key=lambda kv: kv[1].get("order", 0)):
            blocks.append(self.h3(sl.get("name", humanize(key))))
            if sl.get("counts_text"):
                blocks.append(self.p(span("Counts kills of: " + sl["counts_text"] + ".")))
            rows = []
            for i, t in enumerate(sl.get("tiers", [])):
                rows.append([[span(roman(i + 1))], [span("{:,}".format(t.get("fee", 0)))], [span("{:,}".format(t.get("xp", 0)))],
                             [span("{:,}".format(t.get("health", 0)))], [span(str(t.get("level", "")))],
                             [span(str(t.get("slayer_xp", "")))], [span(str(t.get("requires", 0)))]])
            blocks.append(self.table(["Tier", "Fee (coins)", "Kills XP", "Boss health", "Level", "Slayer XP", "Needs slayer level"],
                                     rows, ["c", "r", "r", "r", "r", "r", "r"]))
            facts = []
            if sl.get("token"):
                facts.append([span("Token: "), self.ispan(sl["token"])])
            if sl.get("rare"):
                facts.append([span("Rare drop: "), self.ispan(sl["rare"])])
            if facts:
                blocks.append(self.ul(facts))
        # other
        blocks.append(self.h2("Other bosses"))
        blocks.append(self.ul([[span("Ender Wyrm: the final boss, a flying serpent in the End. Base health 1,024 (vanilla points). "
                                     "See "), span("The Ender Wyrm", link="ender-wyrm"), span(".")]]))
        self.add_page("boss-list", "Bosses", "Dungeon, world and slayer bosses with levels, health, moves and loot.",
                      "minecraft:netherite_sword", blocks)

    # ------------------------------------------------------------------ pets
    def build_pets(self):
        g = self.g
        src = read_text(os.path.join(g.repo, "pets/src/main/java/com/skyrpg/pets/PetType.java"))
        # simpler, robust parse of the five constants
        entries = re.findall(r'(MOLE|BEE|OWL|PUP|OTTER)\("([^"]+)",\s*SkillLevels\.(\w+),\s*StatTypes\.(\w+),\s*([\d.]+),\s*StatTypes\.(\w+),\s*([\d.]+),', src)
        texts = re.findall(r'\),\s*"([^"]+)",\s*\n\s*Map\.of\([^\n]*\),\s*"([^"]+)"', src)
        rsrc = read_text(os.path.join(g.repo, "pets/src/main/java/com/skyrpg/pets/PetRarity.java"))
        rar = re.findall(r"(COMMON|UNCOMMON|RARE|EPIC|LEGENDARY)\(([\d.]+),\s*ChatFormatting\.\w+,\s*(\d+)\)", rsrc)
        blocks = [self.p(span("Each skill has one pet. A pet gains XP from its skill, and its stats grow with its level. "
                              "Eggs hatch into a pet of random rarity, and a better rarity gives more stats per level. "
                              "Pets unlock a perk at level 25 and a second at level 50."))]
        blocks.append(self.h2("Pets"))
        rows = []
        for i, (cid, name, skill, s1, v1, s2, v2) in enumerate(entries):
            sid1, sid2 = s1.lower(), s2.lower()
            nm1 = g.stats.get(sid1, {}).get("name", humanize(sid1))
            nm2 = g.stats.get(sid2, {}).get("name", humanize(sid2))
            pet_item = "skyrpg_pets:%s_pet" % cid.lower()
            p25, p50 = texts[i] if i < len(texts) else ("", "")
            rows.append([[self.ispan(pet_item, name)], [span(skill.capitalize())],
                         [span("%s +%s, %s +%s" % (nm1, v1.rstrip("0").rstrip(".") if "." in v1 else v1, nm2,
                                                  v2.rstrip("0").rstrip(".") if "." in v2 else v2))],
                         [span(p25)], [span(p50)]])
        blocks.append(self.table(["Pet", "Skill", "Stats per level (Common)", "Level 25 perk", "Level 50 perk"], rows, ["l"] * 5))
        blocks.append(self.h2("Rarities"))
        total = sum(int(w) for _, _, w in rar) or 1
        rrows = []
        for name, mult, w in rar:
            color = g.rarity_color(name.lower())
            rrows.append([[span(name.capitalize(), color=color, b=True)], [span("x" + mult)], [span("%s%%" % fmt_num(int(w) * 100.0 / total))]])
        blocks.append(self.table(["Rarity", "Stat multiplier", "Hatch chance"], rrows, ["l", "r", "r"]))
        blocks.append(self.p(span("Golden Pet Eggs skip Common and Uncommon. Hatch chances are for ordinary eggs.", i=True)))
        eggs = [i for i in g.items.values() if i["path"].endswith("_egg")]
        if eggs:
            blocks.append(self.h2("Eggs"))
            blocks.append(self.p(*sum([[self.ispan(e["id"]), span("   ")] for e in sorted(eggs, key=lambda e: e["name"])], [])))
        self.add_page("pet-list", "Pets", "The five pets, their stats per level, perks and rarities.", "skyrpg_pets:mole_pet", blocks)

    # ------------------------------------------------------------------ mounts
    def build_mounts(self):
        g = self.g
        src = read_text(os.path.join(g.repo, "mounts/src/main/java/com/skyrpg/mounts/GriffinColor.java"))
        rsrc = read_text(os.path.join(g.repo, "mounts/src/main/java/com/skyrpg/mounts/GriffinRarity.java"))
        weights = {m.group(1).lower(): float(m.group(2)) for m in re.finditer(r"(COMMON|UNCOMMON|RARE|LEGENDARY)\(ChatFormatting\.\w+,\s*([\d.]+)\)", rsrc)}
        colors = re.findall(r'(\w+)\("(\w+)",\s*0x([0-9A-Fa-f]{6}),\s*GriffinRarity\.(\w+)\)', src)
        total = sum(weights[c[3].lower()] for c in colors) or 1
        blocks = [self.p(span("The only mount is the griffin. Wild griffins nest on the floating islands; tame one and it becomes a "
                              "Griffin Whistle in your mount stable. A griffin runs on the ground and flies when you jump and keep "
                              "moving. Each has a speed bonus and a flight stamina bonus."))]
        blocks.append(self.h2("Griffin colours"))
        rows = []
        for cid, label, rgb, rar in colors:
            r = rar.lower()
            rows.append([[span(label, color=g.rarity_color(r) if r != "common" else "#" + rgb.upper(), b=True)],
                         [span(r.capitalize(), color=g.rarity_color(r))],
                         [span("%s%%" % fmt_num(weights[r] * 100.0 / total))]])
        blocks.append(self.table(["Colour", "Rarity", "Chance for a wild griffin"], rows, ["l", "l", "r"]))
        blocks.append(self.p(span("Speed bonus goes up to +15% and flight stamina bonus up to +25%.")))
        blocks.append(self.p(self.ispan("skyrpg_mounts:griffin_whistle")))
        self.add_page("mount-list", "Mounts", "Griffin colours, rarities and their wild spawn chances.", "skyrpg_mounts:griffin_whistle", blocks)

    # ------------------------------------------------------------------ collections
    def build_collections(self):
        g = self.g
        cols = g.read_dir("collections")
        cats = {}
        for k, c in cols.items():
            cats.setdefault(c.get("category", "other"), []).append((k, c))
        blocks = [self.p(span("A collection counts everything of one kind you gather. Each tier gives coins and XP, and some give stats. "
                              "Amounts are the totals needed for each tier."))]
        for cat in sorted(cats):
            blocks.append(self.h2(humanize(cat)))
            rows = []
            for k, c in sorted(cats[cat], key=lambda kv: kv[1].get("name", "")):
                items = c.get("items", [])
                icon = c.get("icon") or (items[0] if items else None)
                name = c.get("name", humanize(k.split(":")[1]))
                first = [self.ispan(icon, name)] if icon and g.known_item(icon) else [span(name)]
                tiers = c.get("tiers", [])
                total_coins = sum(t.get("coins", 0) for t in tiers)
                stats = {}
                for t in tiers:
                    for sk, sv in (t.get("stats") or {}).items():
                        stats[sk] = stats.get(sk, 0) + sv
                stat_txt = ", ".join("%s +%s" % (g.stats.get(sk, {}).get("name", humanize(sk)), fmt_num(sv)) for sk, sv in stats.items())
                rewards = "%s" % coins(total_coins)
                if stat_txt:
                    rewards += "; " + stat_txt
                rows.append([first, [span(", ".join("{:,}".format(x) for x in c.get("thresholds", [])))], [span(rewards)]])
            blocks.append(self.table(["Collection", "Tier amounts", "Rewards for all tiers"], rows, ["l", "l", "l"]))
        self.add_page("collection-list", "Collections", "Every collection with its tier amounts and rewards.",
                      "minecraft:writable_book", blocks)

    # ------------------------------------------------------------------ quests
    def build_quests(self):
        g = self.g
        chapters = {}
        quests = {}
        for k, q in self.quests.items():
            rel = k.split(":", 1)[1]
            chap, _, name = rel.partition("/")
            if name == "_chapter":
                chapters[chap] = q
            else:
                quests.setdefault(chap, []).append((name, q))
        blocks = [self.p(span("The story quests, in chapter order. Open the quest journal with "), span("J", c=True),
                         span(" in game (or from the K menu) to follow them."))]
        for chap in sorted(quests):
            ch = chapters.get(chap, {})
            blocks.append(self.h2(ch.get("title", humanize(chap.split("_", 1)[-1]))))
            if ch.get("description"):
                blocks.append(self.p(span(ch["description"])))
            rows = []
            for name, q in sorted(quests[chap], key=lambda nq: (nq[1].get("order", 0), nq[0])):
                giver = (q.get("giver") or {}).get("name", "")
                objs = "; ".join(o.get("label", o.get("type", "")) for o in q.get("objectives", []))
                rw = q.get("rewards") or {}
                parts = []
                if rw.get("coins"):
                    parts.append(coins(rw["coins"]))
                if rw.get("xp"):
                    parts.append("%s XP" % rw["xp"])
                rcell = [span(", ".join(parts))]
                its = []
                for r in rw.get("items", []) or []:
                    iid = r.get("item") if isinstance(r, dict) else r
                    n = r.get("count", 1) if isinstance(r, dict) else 1
                    if isinstance(iid, str) and g.known_item(iid):
                        its.append((iid, n))
                if its:
                    rcell.append(span("; " if parts else ""))
                    rcell.extend(self.item_list_spans(its))
                rows.append([[span(q.get("title", name), b=True)], [span(giver)], [span(objs or q.get("description", ""))], rcell])
            blocks.append(self.table(["Quest", "Given by", "What to do", "Rewards"], rows, ["l"] * 4))
        self.add_page("quest-list", "Quests", "Every story quest by chapter, with objectives and rewards.", "minecraft:map", blocks)

    # ------------------------------------------------------------------ sell prices
    def build_prices(self):
        g = self.g
        custom, vanilla = [], []
        for iid, price in g.prices.items():
            (vanilla if iid.startswith("minecraft:") else custom).append((iid, price))
        blocks = [self.p(span("What the shops pay for each item. Prices are per item; selling a stack pays for every item in it. "
                              "Some shopkeepers only buy certain goods, see "), span("Selling and shops", link="selling-and-shops"), span("."))]
        for title, lst in (("SkyRPG items", custom), ("Vanilla items", vanilla)):
            rows = []
            for iid, price in sorted(lst, key=lambda x: ((g.name_of(x[0]) or x[0]).lower())):
                if not g.known_item(iid):
                    continue
                rows.append([[self.ispan(iid)], [span("{:,}".format(price), color=None)]])
            if rows:
                blocks.append(self.h2(title))
                blocks.append(self.table(["Item", "Sells for (coins)"], rows, ["l", "r"]))
        self.add_page("sell-prices", "Sell prices", "What shops pay for every item, SkyRPG and vanilla.", "minecraft:gold_nugget", blocks)

    # ------------------------------------------------------------------ danger regions
    def build_regions(self):
        g = self.g
        blocks = [self.p(span("Danger regions are patches of the survival world that spawn tougher mobs and drop special loot. "
                              "Each has a boss and a sigil. Danger runs from 1 (mild) to 5."))]
        for key, rg in sorted(self.regions.items(), key=lambda kv: (kv[1].get("danger", 0), kv[0])):
            blocks.append(self.h2(rg.get("name", humanize(key))))
            if rg.get("description"):
                blocks.append(self.p(span(rg["description"])))
            facts = [[span("Danger: %s, mob level bonus: +%s" % (rg.get("danger", "?"), rg.get("level_bonus", 0)))]]
            mobs = []
            for m in rg.get("mobs", []):
                nm = m.get("name") or g.entity_name(m["type"])
                if nm not in mobs:
                    mobs.append(nm)
            if mobs:
                facts.append([span("Mobs: " + ", ".join(mobs))])
            drops = rg.get("drops", [])
            dl = []
            for d in drops:
                if g.known_item(d["item"]):
                    dl.append((d["item"], 1))
            if dl:
                facts.append([span("Drops: ")] + self.item_list_spans(dl))
            if rg.get("boss") in self.boss_names:
                facts.append([span("Boss: %s" % self.boss_names[rg["boss"]])])
            if rg.get("sigil") and g.known_item(rg["sigil"]):
                facts.append([span("Sigil: "), self.ispan(rg["sigil"])])
            blocks.append(self.ul(facts))
        self.add_page("region-list", "Danger regions", "The six danger regions, their mobs, drops and bosses.", "minecraft:wither_rose", blocks)
