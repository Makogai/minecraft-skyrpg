"""One generated page per item: hero, tooltip lines, recipe, used in, how to get, sell price and set (see docs/design/wiki.md).

The reverse indexes (what each item is an ingredient of, what shops take it as payment, which items share a set) are built
here from the game data; the backlinks from hand-written pages ("Mentioned in") are added by build.py once those exist.
"""
from gamedata import fmt_num, humanize
from model import item_anchor, merge_spans, span

GREY = "#AAAAAA"
GOLD = "#FFAA00"
ROMAN = ["", "I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X"]
GROUP_KIND = {"items-materials": "Material", "items-consumables": "Consumable", "items-other": "Item"}
SLOT_ORDER = {"HELMET": 0, "CHESTPLATE": 1, "LEGGINGS": 2, "BOOTS": 3}
RARITY_RANK = {"common": 0, "uncommon": 1, "rare": 2, "epic": 3, "legendary": 4, "mythic": 5, "special": 6}


def page_id(item_id):
    return item_anchor(item_id)


def kind_label(g, it, group):
    if it.get("kind") or it.get("type"):
        return g.type_label(it)
    return GROUP_KIND.get(group, "Item")


def build(ref):
    """Adds the item pages to ref.pages; returns {item id: page id}."""
    g = ref.g
    used_in = {}      # ingredient id -> [(result id, count, recipe)]
    for rid, recs in g.recipes.items():
        for r in recs:
            for ing, n in r["ingredients"].items():
                used_in.setdefault(ing, []).append((rid, n, r))
    paid_with = {}    # currency item id -> [(shop title, offered item id, count of currency, offer)]
    for offered, lst in g.shop_offers.items():
        for title, offer, count in lst:
            for c in offer.get("costs", []) or []:
                paid_with.setdefault(c["item"], []).append((title, offered, c.get("count", 1), offer))
    sets = {}
    for it in g.items.values():
        if it["set"]:
            sets.setdefault(it["set"], []).append(it)

    mapping = {}
    for iid, it in g.items.items():
        group = ref.item_page.get(iid)
        if not group:
            continue
        pid = page_id(iid)
        mapping[iid] = pid
        ref.pages[pid] = make_page(ref, it, pid, group, used_in.get(iid, []), paid_with.get(iid, []), sets)
    return mapping


def link_item(ref, iid, n=None):
    out = []
    if n and n != 1:
        out.append(span("%sx " % fmt_num(n)))
    out.append(ref.ispan(iid))
    return out


def make_page(ref, it, pid, group, uses, payments, sets):
    g = ref.g
    iid = it["id"]
    color = g.rarity_color(it["rarity"])
    kind = kind_label(g, it, group)
    blocks = [{"t": "hero", "id": iid, "icon": iid, "name": it["name"], "color": color, "rarity": it["rarity"], "kind": kind}]
    ref.used_items.add(iid)

    # tooltip lines (without the rarity line: the hero shows it)
    card = ref.item_card(it)
    lines = card["lines"][:card["tip"]][:-1]
    while lines and not "".join(s.get("x", "") for s in lines[-1]).strip():
        lines.pop()
    if lines:
        blocks.append({"t": "item", "id": iid, "icon": iid, "name": it["name"], "rarity": it["rarity"], "color": color,
                       "anchor": pid, "lines": lines, "tip": len(lines), "bare": True})

    h2 = ref.h2
    # ---- recipe
    recipes = g.recipes.get(iid, [])
    if recipes:
        blocks.append(h2("Recipe"))
        for i, r in enumerate(recipes):
            if len(recipes) > 1:
                blocks.append(ref.p(span("Recipe %d" % (i + 1), b=True)))
            cat = humanize(r.get("category", ""))
            head = "Crafted at the Artisan Bench"
            if cat:
                head += " (%s tab)" % cat
            if r.get("count", 1) != 1:
                head += ". Makes %d" % r["count"]
            blocks.append(ref.p(span(head + ".")))
            blocks.append({"t": "ul", "items": [{"s": merge_spans(link_item(ref, ing, n))} for ing, n in r["ingredients"].items()]})
            req = r.get("requires")
            need = []
            if req and req.get("skill"):
                need.append("%s level %s" % (g.skill_name(req["skill"]), req.get("level", 1)))
            col = r.get("collection")
            if col:
                tier = col.get("tier", 1)
                need.append("the %s collection, tier %s" % (g.name_of(col["id"]) or humanize(col["id"].split(":")[1]),
                                                           ROMAN[tier] if 0 < tier < len(ROMAN) else tier))
            if need:
                blocks.append(ref.p(span("Requires ", color=None), span(" and ".join(need) + ".", b=True)))

    # ---- used in
    rows = []
    seen = set()
    for rid, n, r in sorted(uses, key=lambda u: (RARITY_RANK.get(g.items[u[0]]["rarity"], 9) if u[0] in g.items else 9, g.name_of(u[0]) or u[0])):
        if (rid, n) in seen:
            continue
        seen.add((rid, n))
        rows.append(merge_spans(ref_span_for(ref, rid) + [span(" (needs %s)" % fmt_num(n))]))
    for title, offered, n, offer in sorted(payments, key=lambda p: (p[0], p[1])):
        rows.append(merge_spans([span("Paid to %s: %s for " % (title, "%sx" % fmt_num(n))), ] + ref_span_for(ref, offered)))
    if rows:
        blocks.append(h2("Used in"))
        blocks.append({"t": "ul", "items": [{"s": r} for r in rows]})

    # ---- how to get
    src = ref.sources(it)
    if src:
        blocks.append(h2("How to get"))
        blocks.append(ref.ul([[span(s)] for s in src]))

    # ---- sell price
    price = g.prices.get(iid)
    if price is not None:
        blocks.append(h2("Sell price"))
        blocks.append(ref.p(span("Sells to merchants for "), span("{:,} coin{}".format(int(price), "" if price == 1 else "s"), color=GOLD, b=True), span(".")))

    # ---- set
    if it["set"]:
        sd = g.item_sets.get(it["set"], {})
        sname = sd.get("name") or humanize(it["set"].split(":")[1])
        blocks.append(h2("Set"))
        bonus = sd.get("bonus")
        text = "Part of the %s set." % sname
        if bonus:
            text += " Wearing %d pieces gives %s: %s" % (sd.get("pieces", 4), bonus.get("name", "a set bonus"), bonus.get("description", ""))
        blocks.append(ref.p(span(text.strip())))
        pcs = sorted(sets.get(it["set"], []), key=lambda p: (SLOT_ORDER.get(p["kind"], 9), p["name"]))
        rows = []
        for p in pcs:
            r = ref.ispan(p["id"])
            rows.append([r] + ([span(" (this piece)", i=True)] if p["id"] == iid else []))
        if rows:
            blocks.append(ref.ul(rows))

    stats = sorted(it["stats"].keys(), key=ref.stat_rank)[:3]
    bits = [" ".join(span_text(ref.stat_line(s, it["stats"][s])).split()) for s in stats]
    summary = "%s %s." % (it["rarity"].capitalize(), kind.lower())
    if bits:
        summary += " " + ", ".join(bits) + "."
    elif it["description"]:
        summary += " " + it["description"]
    return {"id": pid, "title": it["name"], "category": "reference", "order": 1000, "icon": iid, "summary": summary,
            "blocks": blocks, "generated": True, "item": iid, "group": group}


def span_text(spans):
    return "".join(s.get("x", "") for s in spans)


def ref_span_for(ref, iid):
    return [ref.ispan(iid)]
