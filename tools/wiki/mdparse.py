"""Parser for the wiki's Markdown subset (docs/design/wiki.md) into the page block/span model."""
import re

from model import (CATEGORY_IDS, ITEM_ID_RE, merge_spans, slug, span)

FRONT_KEYS = ["title", "category", "order", "icon", "summary"]
ITEM_RE = re.compile(r"\{\{item:([^|}\\]+?)(?:\\?\|([^}]*))?\}\}")
ITEMS_RE = re.compile(r"^\{\{items:([^}]*)\}\}\s*$")
IMG_RE = re.compile(r"^!\[([^\]]*)\]\(([^)]+)\)\s*$")
LIST_RE = re.compile(r"^(\s*)([-*]|\d+\.)\s+(.*)$")
CALLOUT_RE = re.compile(r"^>\s*\[!(TIP|NOTE|WARNING)\]\s*$", re.I)


class Parser:
    """ctx needs: item_span(id, text) -> span, known_item(id) -> bool, page_exists(id) -> bool (listed or present)."""

    def __init__(self, ctx, page_id, errs):
        self.ctx = ctx
        self.page_id = page_id
        self.errs = errs
        self.anchors = []   # (anchor, line)
        self.links = []     # (target page id, anchor or None, line)
        self.images = []    # (file, line)
        self.items_used = []

    def err(self, line, msg):
        self.errs.append((line, msg))

    # ------------------------------------------------------------ front matter
    @staticmethod
    def split_front(text):
        """Returns (front dict, body lines, first body line number, errors list)."""
        lines = text.replace("\r\n", "\n").replace("\r", "\n").split("\n")
        errs = []
        front = {}
        if not lines or lines[0].strip() != "---":
            errs.append((1, "missing front matter (file must start with ---)"))
            return front, lines, 1, errs
        end = None
        for i in range(1, len(lines)):
            if lines[i].strip() == "---":
                end = i
                break
            m = re.match(r"^([A-Za-z_]+):\s*(.*)$", lines[i])
            if m:
                front[m.group(1)] = m.group(2).strip().strip("\"'") if m.group(2).strip()[:1] in "\"'" else m.group(2).strip()
            elif lines[i].strip():
                errs.append((i + 1, "bad front matter line: %s" % lines[i].strip()[:40]))
        if end is None:
            errs.append((1, "front matter is not closed with ---"))
            return front, lines, 1, errs
        return front, lines[end + 1:], end + 2, errs

    # ------------------------------------------------------------ inline
    def inline(self, text, line, style=None, link=None):
        style = style or {}
        out = []
        i, n = 0, len(text)
        buf = []

        def flush():
            if buf:
                s = {"x": "".join(buf)}
                s.update(style)
                if link:
                    s.update(link)
                out.append(s)
                buf.clear()

        while i < n:
            c = text[i]
            if c == "\\" and i + 1 < n and text[i + 1] in "\\`*_{}[]()#+-.!|<>~":
                buf.append(text[i + 1])
                i += 2
                continue
            if c == "`":
                j = text.find("`", i + 1)
                if j > i:
                    flush()
                    s = {"x": text[i + 1:j]}
                    s.update(style)
                    s["c"] = True
                    if link:
                        s.update(link)
                    out.append(s)
                    i = j + 1
                    continue
            if text.startswith("{{item:", i):
                m = ITEM_RE.match(text, i)
                if m:
                    flush()
                    iid = m.group(1).strip()
                    label = m.group(2)
                    self.items_used.append((iid, line))
                    if not self.ctx.known_item(iid):
                        self.err(line, "unknown item id %s" % iid)
                    sp = self.ctx.item_span(iid, label.strip() if label else None)
                    sp = dict(sp)
                    for k in ("b", "i"):
                        if style.get(k):
                            sp[k] = True
                    out.append(sp)
                    i = m.end()
                    continue
            if text.startswith("**", i):
                j = text.find("**", i + 2)
                while j > 0 and text[j + 2:j + 3] == "*":
                    j += 1
                if j > i + 2:
                    flush()
                    st = dict(style)
                    st["b"] = True
                    out.extend(self.inline(text[i + 2:j], line, st, link))
                    i = j + 2
                    continue
            if c == "*" and not text.startswith("**", i):
                j = i + 1
                while True:
                    j = text.find("*", j)
                    if j == -1 or not text.startswith("**", j):
                        break
                    j += 2
                if j > i + 1 and text[i + 1:i + 2] not in (" ", ""):
                    flush()
                    st = dict(style)
                    st["i"] = True
                    out.extend(self.inline(text[i + 1:j], line, st, link))
                    i = j + 1
                    continue
            if c == "[":
                m = re.match(r"\[((?:[^\[\]\\]|\\.|\[[^\]]*\])*)\]\(([^)\s]+)\)", text[i:])
                if m:
                    flush()
                    lk = self.make_link(m.group(2), line)
                    out.extend(self.inline(m.group(1), line, style, lk))
                    i += m.end()
                    continue
            buf.append(c)
            i += 1
        flush()
        return out

    def make_link(self, target, line):
        if re.match(r"^https?://", target):
            return {"url": target}
        if target.startswith("#"):
            page, anchor = self.page_id, target[1:]
        elif "#" in target:
            page, anchor = target.split("#", 1)
        else:
            page, anchor = target, None
        self.links.append((page, anchor, line))
        return {"link": page + ("#" + anchor if anchor else "")}

    def spans(self, text, line):
        return merge_spans(self.inline(text.strip(), line))

    # ------------------------------------------------------------ blocks
    def parse(self, lines, first_line):
        return self.blocks(lines, first_line)

    def blocks(self, lines, base):
        out = []
        i, n = 0, len(lines)
        while i < n:
            raw = lines[i]
            ln = base + i
            s = raw.strip()
            if not s:
                i += 1
                continue
            if re.match(r"^-{3,}\s*$", s) or re.match(r"^\*{3,}\s*$", s):
                out.append({"t": "hr"})
                i += 1
                continue
            m = re.match(r"^(#{1,6})\s+(.*?)\s*#*\s*$", s)
            if m:
                level = len(m.group(1))
                if level == 1:
                    self.err(ln, "do not use # headings (the title comes from the front matter)")
                    level = 2
                elif level > 3:
                    self.err(ln, "only ## and ### headings are supported")
                    level = 3
                text = m.group(2)
                plaintext = "".join(sp["x"] for sp in self.inline(text, ln))
                anchor = slug(plaintext)
                out.append({"t": "h%d" % level, "text": plaintext, "anchor": anchor})
                self.anchors.append((anchor, ln))
                i += 1
                continue
            if s.startswith(">"):
                j = i
                inner = []
                while j < n and lines[j].strip().startswith(">"):
                    t = lines[j].strip()[1:]
                    if t.startswith(" "):
                        t = t[1:]
                    inner.append(t)
                    j += 1
                kind = "note"
                m2 = CALLOUT_RE.match(lines[i].strip())
                body_lines, body_base = inner, base + i
                if m2:
                    kind = m2.group(1).lower()
                    body_lines, body_base = inner[1:], base + i + 1
                else:
                    self.err(ln, "block quote without [!TIP], [!NOTE] or [!WARNING] on its first line")
                out.append({"t": "callout", "kind": kind, "b": self.blocks(body_lines, body_base)})
                i = j
                continue
            m = ITEMS_RE.match(s)
            if m:
                out.append(self.items_table(m.group(1), ln))
                i += 1
                continue
            m = IMG_RE.match(s)
            if m:
                self.images.append((m.group(2), ln))
                out.append({"t": "img", "caption": m.group(1), "file": m.group(2)})
                i += 1
                continue
            if s.startswith("|") and i + 1 < n and re.match(r"^\s*\|?\s*:?-+:?\s*(\|\s*:?-+:?\s*)*\|?\s*$", lines[i + 1]):
                j = i + 2
                while j < n and lines[j].strip().startswith("|"):
                    j += 1
                out.append(self.table(lines[i:j], base + i))
                i = j
                continue
            if LIST_RE.match(raw):
                j = i
                while j < n and (LIST_RE.match(lines[j]) or (lines[j].strip() and lines[j].startswith("  ")
                                                              and not lines[j].strip().startswith((">", "|", "#")))):
                    j += 1
                out.append(self.list_block(lines[i:j], base + i))
                i = j
                continue
            # paragraph
            j = i
            parts = []
            while j < n and lines[j].strip() and not self.starts_block(lines[j], lines[j + 1] if j + 1 < n else ""):
                parts.append(lines[j].strip())
                j += 1
            if j == i:
                parts.append(s)
                j = i + 1
            out.append({"t": "p", "s": self.spans(" ".join(parts), ln)})
            i = j
        return out

    @staticmethod
    def starts_block(line, nxt):
        s = line.strip()
        return bool(re.match(r"^#{1,6}\s", s) or s.startswith(">") or LIST_RE.match(line)
                    or re.match(r"^-{3,}\s*$", s) or ITEMS_RE.match(s) or IMG_RE.match(s)
                    or (s.startswith("|") and re.match(r"^\s*\|?\s*:?-+", nxt)))

    def split_row(self, row):
        row = row.strip()
        if row.startswith("|"):
            row = row[1:]
        if row.endswith("|") and not row.endswith("\\|"):
            row = row[:-1]
        cells, cur, i = [], [], 0
        in_code = False
        depth = 0
        while i < len(row):
            c = row[i]
            if row.startswith("{{", i):
                depth += 1
                cur.append("{{")
                i += 2
                continue
            if row.startswith("}}", i) and depth:
                depth -= 1
                cur.append("}}")
                i += 2
                continue
            if c == "\\" and i + 1 < len(row) and row[i + 1] == "|":
                cur.append("|")
                i += 2
                continue
            if c == "`":
                in_code = not in_code
            if c == "|" and not in_code and not depth:
                cells.append("".join(cur))
                cur = []
            else:
                cur.append(c)
            i += 1
        cells.append("".join(cur))
        return [c.strip() for c in cells]

    def table(self, rows, ln):
        head = self.split_row(rows[0])
        seps = self.split_row(rows[1])
        align = []
        for s in seps:
            if s.startswith(":") and s.endswith(":"):
                align.append("c")
            elif s.endswith(":"):
                align.append("r")
            else:
                align.append("l")
        ncols = len(head)
        body = []
        for k, r in enumerate(rows[2:]):
            cells = self.split_row(r)
            if len(cells) != ncols:
                self.err(ln + 2 + k, "table row has %d cells, header has %d" % (len(cells), ncols))
                cells = (cells + [""] * ncols)[:ncols]
            body.append([self.spans(c, ln + 2 + k) for c in cells])
        while len(align) < ncols:
            align.append("l")
        return {"t": "table", "head": [self.spans(h, ln) for h in head], "rows": body, "align": align[:ncols]}

    def list_block(self, lines, ln):
        first = LIST_RE.match(lines[0])
        ordered = first.group(2)[0].isdigit()
        base_indent = len(first.group(1).replace("\t", "  "))
        items = []
        cur = None
        for k, raw in enumerate(lines):
            m = LIST_RE.match(raw)
            if m:
                indent = len(m.group(1).replace("\t", "  "))
                text = m.group(3)
                if indent > base_indent and cur is not None:
                    cur.setdefault("sub", []).append({"s": self.spans(text, ln + k), "_raw": text})
                    if indent > base_indent + 4:
                        pass
                else:
                    cur = {"s": self.spans(text, ln + k), "_raw": text}
                    items.append(cur)
            elif cur is not None:
                extra = raw.strip()
                target = cur["sub"][-1] if cur.get("sub") and len(raw) - len(raw.lstrip()) > base_indent + 2 else cur
                target["_raw"] += " " + extra
                target["s"] = self.spans(target["_raw"], ln + k)
        for it in items:
            it.pop("_raw", None)
            for sub in it.get("sub", []):
                sub.pop("_raw", None)
        return {"t": "ol" if ordered else "ul", "items": items}

    def items_table(self, ids_text, ln):
        ids = [x.strip() for x in ids_text.split(",") if x.strip()]
        for iid in ids:
            self.items_used.append((iid, ln))
            if not self.ctx.known_item(iid):
                self.err(ln, "unknown item id %s" % iid)
        return {"t": "items", "ids": ids, "_line": ln}


def parse_page(ctx, page_id, text, errs_out):
    """Parse one page. Returns (front, blocks, parser) and appends (line, msg) errors to errs_out."""
    front, body, first, errs = Parser.split_front(text)
    errs_out.extend(errs)
    p = Parser(ctx, page_id, errs_out)
    blocks = p.parse(body, first)
    return front, blocks, p
