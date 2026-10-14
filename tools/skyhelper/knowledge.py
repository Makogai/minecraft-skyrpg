"""SkyHelper's knowledge: the in-game wiki bundle, cut into sections, with a keyword search and a name lookup.

The bundle (wiki/src/main/resources/assets/skyrpg_wiki/wiki/) is what tools/wiki/build.py writes for the in-game wiki,
so SkyHelper knows exactly what the wiki says and picks up every rebuild on its own (see Knowledge.fresh()).
"""
import json
import math
import re
from collections import Counter, defaultdict
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent.parent
BUNDLE = ROOT / "wiki" / "src" / "main" / "resources" / "assets" / "skyrpg_wiki" / "wiki"

STOP = set("a an the and or of to in on at for by with from is are be was were it its this that these those you your "
           "i me my we our can do does how what where when which who why get got i'm im there here not no as if "
           "into out up so than then also all any some more most just only each per".split())

SECTION_CHARS = 1600     # a long section is cut into pieces about this big
ITEM_PAGE_CHARS = 2600   # item pages stay one piece, cut to this


def words(text):
    out = []
    for w in re.findall(r"[a-z0-9]+", text.lower()):
        if w in STOP or len(w) < 2:
            continue
        out.append(stem(w))
    return out


def stem(w):
    if len(w) > 4 and w.endswith("ies"):
        return w[:-3] + "y"
    if len(w) > 3 and w.endswith("es") and w[-3] in "sxz":
        return w[:-2]
    if len(w) > 3 and w.endswith("s") and not w.endswith("ss"):
        return w[:-1]
    return w


def norm_name(text):
    return " ".join(re.findall(r"[a-z0-9']+", text.lower().replace("’", "'")))


def plain(spans):
    return "".join(s.get("x", "") for s in spans)


def render(blocks, out, depth=0):
    """Wiki blocks as compact text lines an LLM reads well. h2/h3 come out as ('#', level, text, anchor) markers."""
    for b in blocks:
        t = b["t"]
        if t in ("h2", "h3"):
            out.append(("#", 2 if t == "h2" else 3, b["text"], b.get("anchor")))
        elif t == "p":
            out.append(plain(b["s"]))
        elif t in ("ul", "ol"):
            for n, it in enumerate(b["items"], 1):
                out.append(("- " if t == "ul" else "%d. " % n) + plain(it["s"]))
                for sub in it.get("sub", []):
                    out.append("  - " + plain(sub["s"]))
        elif t == "table":
            out.append(" | ".join(plain(c) for c in b["head"]))
            for row in b["rows"]:
                out.append(" | ".join(plain(c) for c in row))
        elif t == "callout":
            inner = []
            render(b["b"], inner, depth + 1)
            out.append("Note: " + " ".join(x for x in inner if isinstance(x, str)))
        elif t == "item":
            lines = [plain(l).strip() for l in b["lines"]]
            lines = [l for l in lines if l]
            out.append("%s (%s): %s" % (b["name"], b.get("rarity", ""), "; ".join(lines)))
        elif t == "hero":
            out.append("%s, %s %s" % (b["name"], b.get("rarity", ""), b.get("kind", "")))
    return out


class Section:
    __slots__ = ("page", "title", "heading", "anchor", "text", "terms", "length", "item")

    def __init__(self, page, title, heading, anchor, text, item=False):
        self.page, self.title, self.heading, self.anchor, self.text, self.item = page, title, heading, anchor, text, item
        # the page title and heading count three times: a hit there matters more than one in the body
        head = (title + " " + (heading or "") + " ") * 3
        self.terms = Counter(words(head + text))
        self.length = sum(self.terms.values())

    def ref(self):
        return self.page + ("#" + self.anchor if self.anchor else "")

    def label(self):
        return self.title + (" > " + self.heading if self.heading else "")

    def show(self, limit=SECTION_CHARS):
        body = self.text if len(self.text) <= limit else self.text[:limit].rsplit("\n", 1)[0] + "\n..."
        return "[%s] %s\n%s" % (self.ref(), self.label(), body)


class Knowledge:
    def __init__(self, bundle=BUNDLE):
        self.bundle = Path(bundle)
        self.stamp = None
        self.load()

    # ----------------------------------------------------------------- loading
    def fresh(self):
        """Reloads when the wiki was rebuilt since the last load."""
        stamp = (self.bundle / "index.json").stat().st_mtime
        if stamp != self.stamp:
            self.load()

    def load(self):
        index = json.loads((self.bundle / "index.json").read_text(encoding="utf-8"))
        self.stamp = (self.bundle / "index.json").stat().st_mtime
        self.web_url = index.get("web_url", "")
        self.categories = {c["id"]: c["name"] for c in index["categories"]}
        self.pages = {}       # id -> dict(title, category, summary, hidden, lines)
        self.sections = []
        self.names = {}       # normalised name -> (kind, page id, anchor)
        self.item_pages = dict(index.get("items", {}))   # item id -> page id
        for meta in index["pages"]:
            pid = meta["id"]
            data = json.loads((self.bundle / "pages" / (pid + ".json")).read_text(encoding="utf-8"))
            lines = render(data["blocks"], [])
            self.pages[pid] = dict(title=meta["title"], category=meta.get("category", ""), summary=meta.get("summary", ""),
                                   hidden=meta.get("hidden", False), lines=lines)
            self.cut(pid, meta["title"], lines, item=pid.startswith("item-"))
            self.names.setdefault(norm_name(meta["title"]), ("page", pid, None))
        aliases = {}   # first word -> {full name -> [(page, anchor)]}
        for pid, page in self.pages.items():
            anchor, head = None, False
            for line in page["lines"]:
                if isinstance(line, tuple):
                    anchor, head = line[3], True
                    if line[1] == 3:
                        # h3s name things: NPCs, mobs, bosses, dungeon rooms. "Brannoc Ashforge, Blacksmith" also
                        # answers to "Brannoc Ashforge" and, when no one else shares it, to "Brannoc"
                        self.names.setdefault(norm_name(line[2]), ("heading", pid, line[3]))
                        who = line[2].split(",")[0].split(" (")[0]
                        if who != line[2]:
                            self.names.setdefault(norm_name(who), ("heading", pid, line[3]))
                        first = norm_name(who).split(" ")[0] if " " in who else None
                        if first and len(first) >= 5 and first not in STOP and first not in ("the", "your"):
                            aliases.setdefault(first, {}).setdefault(norm_name(who), []).append((pid, line[3]))
        for first, people in aliases.items():
            if len(people) == 1:     # only one "Brannoc ..." anywhere; the NPCs page is the best place for a person
                places = next(iter(people.values()))
                pid, anchor = next((p for p in places if p[0] == "npcs"), places[0])
                self.names.setdefault(first, ("heading", pid, anchor))
        for pid, page in self.pages.items():
            if pid.startswith("item-"):
                continue
            anchor, in_table = None, False
            for line in page["lines"]:
                if isinstance(line, tuple):
                    anchor, in_table = line[3], False
                elif " | " in line:
                    if in_table:    # the first row of a table is its header; the rest name things in their first cell
                        first = line.split(" | ")[0].strip()
                        cell = norm_name(first)
                        if 4 <= len(cell) <= 40 and not cell[0].isdigit() and not first.startswith(("/", "<")):
                            self.names.setdefault(cell, ("row", pid, anchor))
                    in_table = True
                else:
                    in_table = False
        self.rows = self.table_rows()
        n = len(self.sections)
        self.avg_len = sum(s.length for s in self.sections) / max(1, n)
        df = Counter()
        for s in self.sections:
            df.update(s.terms.keys())
        self.idf = {t: math.log(1 + (n - f + 0.5) / (f + 0.5)) for t, f in df.items()}

    def cut(self, pid, title, lines, item):
        if item:
            text = "\n".join(l if isinstance(l, str) else "%s:" % l[2] for l in lines)
            if len(text) > ITEM_PAGE_CHARS:
                text = text[:ITEM_PAGE_CHARS].rsplit("\n", 1)[0] + "\n..."
            self.sections.append(Section(pid, title, None, None, text, item=True))
            return
        heading, anchor, buf = None, None, []

        def flush():
            body = "\n".join(buf).strip()
            if not body:
                return
            piece, size = [], 0
            for line in body.split("\n"):
                if size + len(line) > SECTION_CHARS and piece:
                    self.sections.append(Section(pid, title, heading, anchor, "\n".join(piece)))
                    piece, size = [], 0
                piece.append(line)
                size += len(line) + 1
            if piece:
                self.sections.append(Section(pid, title, heading, anchor, "\n".join(piece)))

        for line in lines:
            if isinstance(line, tuple):
                if line[1] == 2:
                    flush()
                    heading, anchor, buf = line[2], line[3], []
                else:
                    buf.append("### " + line[2])
            else:
                buf.append(line)
        flush()

    def table_rows(self):
        """Every table row and list line on the written pages, with where it sits: 'who sells / buys / drops what'."""
        rows = []
        for pid, page in self.pages.items():
            if pid.startswith("item-"):
                continue
            h2 = h3 = None
            for line in page["lines"]:
                if isinstance(line, tuple):
                    if line[1] == 2:
                        h2, h3 = line[2], None
                    else:
                        h3 = line[2]
                elif " | " in line or line.startswith("- "):
                    where = " > ".join(x for x in (page["title"], h2, h3) if x)
                    rows.append((norm_name(line), line, where, pid))
        return rows

    # ----------------------------------------------------------------- search
    def search(self, query, k=5, items=True):
        q = words(query)
        if not q:
            return []
        scored = []
        k1, b = 1.4, 0.7
        for s in self.sections:
            if not items and s.item:
                continue
            score = 0.0
            for t in q:
                f = s.terms.get(t)
                if f:
                    score += self.idf[t] * f * (k1 + 1) / (f + k1 * (1 - b + b * s.length / self.avg_len))
            if score > 0:
                scored.append((score, s))
        scored.sort(key=lambda x: -x[0])
        out, seen = [], Counter()
        for score, s in scored:
            if seen[s.page] >= 2:      # at most two pieces of one page, so one long page cannot crowd the rest out
                continue
            seen[s.page] += 1
            out.append(s)
            if len(out) == k:
                break
        return out

    # ----------------------------------------------------------------- names
    def find_names(self, text):
        """Item, NPC, mob and page names that appear in a question, longest first, as (name, kind, page, anchor)."""
        t = " " + norm_name(text) + " "
        hits = []
        for name, (kind, pid, anchor) in self.names.items():
            if len(name) >= 4 and (" " + name + " ") in t:
                hits.append((name, kind, pid, anchor))
        hits.sort(key=lambda h: -len(h[0]))
        kept = []
        for h in hits:
            if not any(h[0] in k[0] for k in kept):
                kept.append(h)
        return kept

    def resolve(self, name):
        """The best match for a name: exact, then contained either way, then by shared words."""
        n = norm_name(name)
        if not n:
            return None
        if n in self.names:
            return self.names[n]
        if n.endswith("s") and n[:-1] in self.names:
            return self.names[n[:-1]]
        best, best_score = None, 0.0
        qw = set(n.split())
        for name, hit in self.names.items():
            if n in name or name in n:
                score = 0.9 - abs(len(name) - len(n)) / 100
            else:
                nw = set(name.split())
                common = len(qw & nw)
                score = common / max(len(qw | nw), 1) if common else 0
            if hit[0] == "page" and hit[1].startswith("item-"):
                score += 0.05
            if score > best_score:
                best, best_score = hit, score
        return best if best_score >= 0.34 else None

    def mentions(self, name, limit=14):
        """Table rows and list lines that name the thing, with the page section they are in."""
        n = norm_name(name)
        if len(n) < 3:
            return []
        out = []
        for low, line, where, pid in self.rows:
            if (" " + n + " ") in (" " + low + " "):
                out.append("%s  (%s)" % (line, where))
                if len(out) == limit:
                    break
        return out

    def page_text(self, pid, anchor=None, limit=5000):
        page = self.pages.get(pid)
        if not page:
            return None
        lines, on = [], anchor is None
        for line in page["lines"]:
            if isinstance(line, tuple):
                if anchor is not None:
                    if line[3] == anchor:
                        on = True
                    elif on and line[1] <= 2 and lines:
                        break
                if on:
                    lines.append(("## " if line[1] == 2 else "### ") + line[2])
            elif on:
                lines.append(line)
        text = "\n".join(lines)
        if len(text) > limit:
            text = text[:limit].rsplit("\n", 1)[0] + "\n... (more on the page; ask for a section)"
        return "[%s] %s\n%s" % (pid, page["title"], text)

    def sell_price(self, name):
        n = norm_name(name)
        for low, line, where, pid in self.rows:
            if pid == "sell-prices" and low.startswith(n + " ") and " | " in line:
                cells = [c.strip() for c in line.split(" | ")]
                if norm_name(cells[0]) == n and len(cells) > 1:
                    return "%s sells for %s coins each (%s). Sell it with /sell anywhere, or to the keeper who buys it." % (
                        cells[0], cells[-1], where)
        return None

    def lookup(self, name):
        hit = self.resolve(name)
        parts = []
        if hit and hit[0] == "row":
            # a name only found in a table: no page of its own, so its rows elsewhere are the facts
            hit = None
            name = name
        if hit:
            kind, pid, anchor = hit
            if kind == "page":
                parts.append(self.page_text(pid, None, ITEM_PAGE_CHARS if pid.startswith("item-") else 3000))
            else:
                parts.append(self.page_text(pid, anchor, 2500))
            title = self.pages[pid]["title"] if kind == "page" else name
        else:
            title = name
        rows = self.mentions(title)
        if not rows and hit is None:
            rows = self.mentions(name)
        price = self.sell_price(title)
        if price:
            parts.insert(0, price)
        if rows:
            parts.append("Mentioned in tables and lists:\n" + "\n".join(rows))
        if not parts:
            near = self.search(name, k=3)
            if near:
                return "Nothing is called \"%s\". Closest wiki sections:\n\n%s" % (
                    name, "\n\n".join(s.show(900) for s in near))
            return "Nothing in the wiki matches \"%s\"." % name
        return "\n\n".join(p for p in parts if p)

    def item_page(self, item_id):
        return self.item_pages.get(item_id)

    def overview(self):
        """One line per written page: the map of what the wiki covers, for the system prompt."""
        by_cat = defaultdict(list)
        for pid, p in self.pages.items():
            if not p["hidden"] and not pid.startswith("item-"):
                by_cat[p["category"]].append("- %s: %s %s" % (pid, p["title"], "- " + p["summary"] if p["summary"] else ""))
        out = []
        for cid, name in self.categories.items():
            if by_cat.get(cid):
                out.append(name + ":")
                out.extend(by_cat[cid])
        return "\n".join(out)


if __name__ == "__main__":
    import sys
    kb = Knowledge()
    print(len(kb.pages), "pages,", len(kb.sections), "sections,", len(kb.names), "names,", len(kb.rows), "rows")
    q = " ".join(sys.argv[1:]) or "where can I sell dread fang"
    print("names:", kb.find_names(q))
    for s in kb.search(q, 4):
        print("---", s.ref(), s.label())
