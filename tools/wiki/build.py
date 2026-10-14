#!/usr/bin/env python3
"""The SkyRPG wiki builder (see docs/design/wiki.md).

  python tools/wiki/build.py            build the in-game bundle and the website
  python tools/wiki/build.py --check    validate only (links, ids, front matter); exit 1 on problems

Reads docs/wiki/pages/**/*.md and the mods' game data; writes
  wiki/src/main/resources/assets/skyrpg_wiki/wiki/{index.json,pages/*.json}   (in-game, no icons)
  dist/wiki/  (the website: index.html, wiki.css, wiki.js, data/*.json, img/**)
"""
import argparse
import hashlib
import datetime
import glob
import json
import os
import re
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)

from gamedata import Game  # noqa: E402
from icons import AssetStore, IconResolver  # noqa: E402
from mdparse import parse_page, FRONT_KEYS  # noqa: E402
from model import (CATEGORIES, CATEGORY_IDS, GENERATED_IDS, ITEM_ID_RE, LISTED_IDS, PAGE_ID_RE, PAGE_LIST,  # noqa: E402
                   blocks_text, item_anchor, plain, search_words)
from refpages import Ref  # noqa: E402
from render3d import Renderer  # noqa: E402
import web  # noqa: E402

REPO = os.path.abspath(os.path.join(HERE, "..", ".."))
B = chr(92)


def rel(path):
    return os.path.relpath(path, REPO).replace(B, "/")


def atomic_write(path, data):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    tmp = path + ".tmp"
    mode = "wb" if isinstance(data, bytes) else "w"
    with open(tmp, mode, **({} if isinstance(data, bytes) else {"encoding": "utf-8", "newline": "\n"})) as f:
        f.write(data)
    os.replace(tmp, path)


def dedupe_anchors(blocks):
    seen = {}
    for b in blocks:
        if b.get("t") in ("h2", "h3"):
            a = b["anchor"]
            n = seen.get(a, 0)
            seen[a] = n + 1
            if n:
                b["anchor"] = "%s-%d" % (a, n + 1)


def collect_anchors(blocks, out):
    for b in blocks:
        t = b.get("t")
        if t in ("h2", "h3"):
            out.add(b["anchor"])
        elif t == "item":
            out.add(b["anchor"])
        elif t == "callout":
            collect_anchors(b["b"], out)


def expand_items(blocks, ref):
    for i, b in enumerate(blocks):
        if b.get("t") == "items":
            blocks[i] = ref.items_table(b["ids"])
        elif b.get("t") == "callout":
            expand_items(b["b"], ref)


def walk(node, fn):
    if isinstance(node, dict):
        fn(node)
        for v in node.values():
            walk(v, fn)
    elif isinstance(node, list):
        for v in node:
            walk(v, fn)


def add_backlinks(pages, meta):
    """Appends "Mentioned in" to every item page that hand-written pages refer to."""
    found = {}      # item id -> {(page id, anchor): (page title, heading text)}
    for pid in meta:
        p = pages[pid]
        state = {"anchor": None, "head": None}

        def spans(node):
            if isinstance(node, dict):
                if isinstance(node.get("item"), str):
                    found.setdefault(node["item"], {}).setdefault((pid, state["anchor"]), (p["title"], state["head"]))
                for v in node.values():
                    spans(v)
            elif isinstance(node, list):
                for v in node:
                    spans(v)

        def visit(blocks):
            for b in blocks:
                t = b.get("t")
                if t in ("h2", "h3"):
                    state["anchor"], state["head"] = b["anchor"], b["text"]
                elif t == "callout":
                    visit(b["b"])
                else:
                    spans(b)
        visit(p["blocks"])
    for iid, where in found.items():
        page = pages.get(item_anchor(iid))
        if not page or not page.get("item"):
            continue
        rows = []
        for (pid, anchor), (title, head) in sorted(where.items(), key=lambda kv: (kv[1][0].lower(), kv[1][1] or "")):
            link = pid + ("#" + anchor if anchor else "")
            s = [{"x": title, "link": link}]
            if head and head != title:
                s.append({"x": " (%s)" % head})
            rows.append({"s": s})
        page["blocks"].append({"t": "h2", "text": "Mentioned in", "anchor": "mentioned-in"})
        page["blocks"].append({"t": "ul", "items": rows})


def search_index(ordered, group_titles):
    """The website's search index: [kind, page id, anchor, title, text, icon, where] per entry (kinds p, h, i, t)."""
    out = []
    for p in ordered:
        cat_name = next((n for c, n, i in CATEGORIES if c == p["category"]), "")
        if p.get("item"):
            hero = p["blocks"][0]
            card = p["blocks"][1] if len(p["blocks"]) > 1 and p["blocks"][1].get("t") == "item" else None
            text = " ".join(plain(ln) for ln in card["lines"]) if card else ""
            out.append(["i", p["id"], None, p["title"], (hero["rarity"] + " " + hero["kind"] + " " + text).strip(), p["icon"],
                        group_titles.get(p["group"], "Items")])
            continue
        out.append(["p", p["id"], None, p["title"], p["summary"], p["icon"], cat_name])
        anchor, head = None, p["title"]
        for b in p["blocks"]:
            t = b.get("t")
            if t in ("h2", "h3"):
                anchor, head = b["anchor"], b["text"]
                out.append(["h", p["id"], b["anchor"], b["text"], "", "", p["title"]])
            elif t == "item":
                continue        # the item has its own page and entry
            elif t == "table":
                for r in b["rows"]:
                    out.append(["t", p["id"], anchor, head, " | ".join(plain(c) for c in r), "", p["title"]])
            else:
                text = blocks_text([b])
                if text:
                    out.append(["t", p["id"], anchor, head, text, "", p["title"]])
    return out


class Ctx:
    def __init__(self, game, ref):
        self.game, self.ref = game, ref

    def known_item(self, iid):
        return bool(ITEM_ID_RE.match(iid)) and self.game.known_item(iid)

    def item_span(self, iid, text):
        if not self.game.known_item(iid):
            return {"x": text or iid, "item": iid, "icon": iid}
        return self.ref.ispan(iid, text)


def main():
    ap = argparse.ArgumentParser(description="Build the SkyRPG wiki.")
    ap.add_argument("--check", action="store_true", help="validate only, write nothing")
    ap.add_argument("--pages", action="append", help="page source folder (default docs/wiki/pages); may be repeated")
    ap.add_argument("--images", default=os.path.join(REPO, "docs", "wiki", "images"))
    ap.add_argument("--site", default=os.path.join(REPO, "docs", "wiki", "site.json"))
    ap.add_argument("--bundle", default=os.path.join(REPO, "wiki", "src", "main", "resources", "assets", "skyrpg_wiki", "wiki"))
    ap.add_argument("--dist", default=os.path.join(REPO, "dist", "wiki"))
    ap.add_argument("--quiet", action="store_true")
    args = ap.parse_args()
    page_dirs = [os.path.abspath(p) for p in (args.pages or [os.path.join(REPO, "docs", "wiki", "pages")])]

    # ---- site.json
    web_url = ""
    if not os.path.exists(args.site):
        if not args.check:
            atomic_write(args.site, json.dumps({"web_url": ""}) + "\n")
    else:
        try:
            with open(args.site, encoding="utf-8") as f:
                web_url = json.load(f).get("web_url", "")
        except ValueError:
            print("%s: not valid JSON" % rel(args.site))

    cache = os.path.join(HERE, ".cache")
    assets = AssetStore(REPO, cache)
    if assets.jar is None:
        print("warning: Minecraft client jar not found; vanilla names and icons will be missing")
    game = Game(REPO, assets.vanilla_lang())
    ref = Ref(game)
    ctx = Ctx(game, ref)

    problems = []   # (file, line, message)
    warnings = []

    # ---- hand-written pages
    files = []
    for d in page_dirs:
        files.extend(sorted(glob.glob(os.path.join(d, "**", "*.md"), recursive=True)))
    pages = {}      # id -> page dict
    meta = {}       # id -> dict(file, parser)
    for f in files:
        name = os.path.basename(f)[:-3]
        r = rel(f) if f.startswith(REPO) else f
        errs = []
        try:
            with open(f, encoding="utf-8") as fh:
                text = fh.read()
        except OSError as e:
            problems.append((r, 0, "cannot read: %s" % e))
            continue
        if not PAGE_ID_RE.match(name):
            problems.append((r, 1, "bad page id %r (lowercase words joined by -)" % name))
        if name in pages or name in ref.pages:
            other = meta[name]["file"] if name in meta else "a generated reference page"
            problems.append((r, 1, "duplicate page id %r (also %s)" % (name, other)))
            continue
        front, blocks, parser = parse_page(ctx, name, text, errs)
        for ln, msg in errs:
            problems.append((r, ln, msg))
        for k in FRONT_KEYS:
            if not front.get(k):
                problems.append((r, 1, "front matter is missing %r" % k))
        cat = front.get("category", "")
        if cat and cat not in CATEGORY_IDS:
            problems.append((r, 1, "unknown category %r (use one of: %s)" % (cat, ", ".join(CATEGORY_IDS))))
        order = 0
        if front.get("order"):
            try:
                order = int(front["order"])
            except ValueError:
                problems.append((r, 1, "order must be a whole number, got %r" % front["order"]))
        icon = front.get("icon", "")
        if icon and not (ITEM_ID_RE.match(icon) and game.known_item(icon)):
            problems.append((r, 1, "unknown icon item %r" % icon))
        if name in LISTED_IDS and cat and LISTED_IDS[name] != cat:
            warnings.append("%s: page %r is listed under %r in the spec but its category is %r" % (r, name, LISTED_IDS[name], cat))
        if name not in LISTED_IDS:
            warnings.append("%s: page id %r is not in the spec's page list" % (r, name))
        expand_items(blocks, ref)
        dedupe_anchors(blocks)
        # images
        for b in blocks:
            if b.get("t") == "img":
                path = os.path.join(args.images, b["file"])
                if not os.path.exists(path):
                    ln = next((l for (fn, l) in parser.images if fn == b["file"]), 1)
                    problems.append((r, ln, "image %r not found in docs/wiki/images" % b["file"]))
        pages[name] = {"id": name, "title": front.get("title", name), "category": cat, "order": order, "icon": icon,
                       "summary": front.get("summary", ""), "blocks": blocks}
        meta[name] = {"file": r, "parser": parser}

    # ---- generated pages join the set
    for pid, p in ref.pages.items():
        if pid in pages:
            continue
        dedupe_anchors(p["blocks"])
        pages[pid] = p

    add_backlinks(pages, meta)

    # ---- link validation
    anchors = {}
    for pid, p in pages.items():
        s = set()
        collect_anchors(p["blocks"], s)
        anchors[pid] = s
    for pid, m in meta.items():
        for target, anchor, ln in m["parser"].links:
            if target not in pages and target not in LISTED_IDS:
                problems.append((m["file"], ln, "link to unknown page %r" % target))
            elif target in pages and anchor and anchor not in anchors[target]:
                problems.append((m["file"], ln, "link to missing anchor #%s on page %r" % (anchor, target)))
    missing = [pid for cat in CATEGORY_IDS for pid in PAGE_LIST[cat] if pid not in pages]
    if missing:
        warnings.append("%d pages in the spec's page list are not written yet: %s" % (len(missing), ", ".join(missing)))

    for r, ln, msg in sorted(problems, key=lambda x: (x[0], x[1])):
        print("%s:%s: %s" % (r, ln, msg))
    for w in warnings:
        print("warning: " + w)
    if args.check:
        print("%d problem(s), %d warning(s); %d pages (%d written, %d generated)" %
              (len(problems), len(warnings), len(pages), len(meta), len(pages) - len(meta)))
        sys.exit(1 if problems else 0)

    # ---- order
    cat_pos = {c: i for i, c in enumerate(CATEGORY_IDS)}
    ordered = sorted((p for p in pages.values() if p["category"] in cat_pos),
                     key=lambda p: (cat_pos[p["category"]], p["order"], p["title"].lower()))
    now = datetime.datetime.now(datetime.timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ")

    # ---- big pictures: 3D renders of item models (flat items use their texture, shown big by the page)
    renderer = Renderer(assets, cache)
    big_files = {}      # published path -> png bytes
    renders, flat_items, failed = 0, 0, []
    for p in ordered:
        if not p.get("item"):
            continue
        hero = p["blocks"][0]
        try:
            png = renderer.render_item(p["item"])
        except Exception as e:  # a broken model must not break the build
            png = None
            failed.append("%s (%s)" % (p["item"], e))
        if png:
            path = "img/b/%s.png" % p["id"]
            big_files[path] = png
            hero["big"] = path
            renders += 1
        else:
            flat_items += 1

    # ---- in-game bundle
    def clean(blocks):
        out = []
        for b in blocks:
            b = {k: v for k, v in b.items() if k not in ("file", "_line", "src", "generated", "big")}
            if b.get("t") == "callout":
                b["b"] = clean(b["b"])
            out.append(b)
        return out

    items_map = dict(sorted(ref.item_detail.items()))
    index = {"version": 1, "generated": now, "web_url": web_url,
             "categories": [{"id": c, "name": n, "icon": i} for c, n, i in CATEGORIES],
             "pages": [], "items": items_map}
    bundle_pages = {}
    for p in ordered:
        blocks = clean(p["blocks"])
        entry = {"id": p["id"], "title": p["title"], "category": p["category"], "order": p["order"],
                 "icon": p["icon"], "summary": p["summary"]}
        if p.get("item"):
            entry["hidden"] = True
        entry["words"] = search_words(p["title"], blocks)
        index["pages"].append(entry)
        bundle_pages[p["id"]] = {"id": p["id"], "title": p["title"], "category": p["category"], "icon": p["icon"],
                                 "summary": p["summary"], "blocks": blocks}

    def dumps(o):
        return json.dumps(o, ensure_ascii=False, separators=(",", ":"))
    bundle_bytes = 0
    pdir = os.path.join(args.bundle, "pages")
    os.makedirs(pdir, exist_ok=True)
    keep = set()
    for pid, obj in bundle_pages.items():
        data = dumps(obj).encode("utf-8")
        bundle_bytes += len(data)
        atomic_write(os.path.join(pdir, pid + ".json"), data)
        keep.add(pid + ".json")
    for stale in glob.glob(os.path.join(pdir, "*.json")):
        if os.path.basename(stale) not in keep:
            os.remove(stale)
    idx_bytes = dumps(index).encode("utf-8")
    bundle_bytes += len(idx_bytes)
    atomic_write(os.path.join(args.bundle, "index.json"), idx_bytes)

    # ---- website: a small multi-file site (see docs/design/wiki.md)
    site = {}           # published path -> bytes

    wanted = set()
    for c in CATEGORIES:
        wanted.add(c[2])
    for p in ordered:
        if p["icon"]:
            wanted.add(p["icon"])
        walk(p["blocks"], lambda d: wanted.add(d["icon"]) if isinstance(d.get("icon"), str) else None)
    resolver = IconResolver(assets)
    icons, fallback = {}, []
    for iid in sorted(wanted):
        png = resolver.resolve(iid)
        if png:
            path = "img/i/%s.png" % hashlib.sha1(png).hexdigest()[:10]
            site[path] = png
            icons[iid] = path
        else:
            fallback.append(iid)
    site.update(big_files)

    def attach(d):
        if d.get("t") == "img" and d.get("file"):
            path = os.path.join(args.images, d["file"])
            if os.path.exists(path):
                with open(path, "rb") as fh:
                    site["img/p/" + d["file"]] = fh.read()
                d["src"] = "img/p/" + d["file"]

    web_pages, meta_pages = {}, []
    group_titles = {pid: pages[pid]["title"] for pid in PAGE_LIST["reference"] if pid.startswith("items-") and pid in pages}
    for p in ordered:
        walk(p["blocks"], attach)
        blocks = [{k: v for k, v in b.items() if k not in ("file", "_line", "generated")} for b in p["blocks"]]
        web_pages[p["id"]] = {"id": p["id"], "title": p["title"], "category": p["category"], "icon": p["icon"],
                              "summary": p["summary"], "blocks": blocks}
        m = {"id": p["id"], "title": p["title"], "category": p["category"], "icon": p["icon"], "summary": p["summary"]}
        if p.get("item"):
            hero = blocks[0]
            m.update({"item": p["item"], "group": p["group"], "color": hero["color"], "rarity": hero["rarity"]})
        meta_pages.append(m)
    colors = {}
    for name, hexv in game.rarity_colors.items():
        colors[hexv.upper()] = name
    enchant_map = {"common": "common", "uncommon": "uncommon", "rare": "rare", "epic": "epic", "legendary": "legendary", "ultimate": "mythic"}
    for name, hexv in game.enchant_rarity_colors.items():
        colors.setdefault(hexv.upper(), enchant_map.get(name, "common"))
    meta_json = {"generated": now, "webUrl": web_url,
                 "categories": [{"id": c, "name": n, "icon": i} for c, n, i in CATEGORIES],
                 "pages": meta_pages, "icons": icons, "itemPage": items_map, "colors": colors,
                 "groups": group_titles}
    site["data/meta.json"] = dumps(meta_json).encode("utf-8")
    site["data/search.json"] = dumps(search_index(ordered, group_titles)).encode("utf-8")
    for pid, obj in web_pages.items():
        site["data/pages/%s.json" % pid] = dumps(obj).encode("utf-8")
    for name, data in web.shell_files().items():
        site[name] = data

    removed = 0
    for root, dirs, files_ in os.walk(args.dist):
        for fn in files_:
            full = os.path.join(root, fn)
            if os.path.relpath(full, args.dist).replace(B, "/") not in site:
                os.remove(full)
                removed += 1
    for path, data in site.items():
        full = os.path.join(args.dist, *path.split("/"))
        if os.path.exists(full):
            with open(full, "rb") as fh:
                if fh.read() == data:
                    continue
        atomic_write(full, data)
    for root, dirs, files_ in os.walk(args.dist, topdown=False):
        if not os.listdir(root) and root != args.dist:
            os.rmdir(root)

    words = sum(len(index_page["words"]) for index_page in index["pages"])
    n_items = len(items_map)
    total = sum(len(d) for d in site.values())
    print("Pages: %d (%d written, %d generated, of which %d item pages); search words: %d" %
          (len(ordered), len(meta), len(ordered) - len(meta), n_items, words))
    print("Items: %d with entries; %d spans/cards reference icons" % (n_items, len(wanted)))
    print("Icons: %d files, %d fell back to the placeholder%s" % (len(set(icons.values())), len(fallback),
          (" (" + ", ".join(fallback[:12]) + (" ..." if len(fallback) > 12 else "") + ")") if fallback else ""))
    print("Big pictures: %d 3D renders, %d flat items use their texture%s" % (renders, flat_items,
          ("; %d render failures: %s" % (len(failed), ", ".join(failed[:5]))) if failed else ""))
    print("Bundle: %s (%d files, %.0f KB)" % (rel(args.bundle) if args.bundle.startswith(REPO) else args.bundle,
                                              len(bundle_pages) + 1, bundle_bytes / 1024.0))
    print("Website: %s, %d files, %.1f MB (search index %.0f KB, %d stale files removed)" %
          (rel(args.dist) if args.dist.startswith(REPO) else args.dist, len(site), total / 1048576.0,
           len(site["data/search.json"]) / 1024.0, removed))
    if problems:
        print("%d problem(s) listed above" % len(problems))
        sys.exit(1)


if __name__ == "__main__":
    main()
