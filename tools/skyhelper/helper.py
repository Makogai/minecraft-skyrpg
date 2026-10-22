#!/usr/bin/env python3
"""SkyHelper: the in-game assistant. A tiny HTTP service the skyrpg_helper mod asks; a local Ollama model answers from the wiki.

    python tools/skyhelper/helper.py                  # serve on 127.0.0.1:8770
    python tools/skyhelper/helper.py --ask "where can I sell dread fang?"   # one question, printed with timings

POST /ask {"player": "...", "question": "...", "public": true, "context": {...}} -> {"answer": "...", "page": "...", ...}
GET /health -> {"ok": true, "model": "...", "ollama": true}

The mod sends what it knows about the asker (held item, region, skills, coins...) in "context"; any keys go, they are
shown to the model as "key: value" lines. Questions and answers are logged to run/skyhelper/log.jsonl for tuning.
Only the Python standard library is used.
"""
import argparse
import json
import os
import re
import subprocess
import sys
import threading
import time
import urllib.error
import urllib.request
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from knowledge import Knowledge, ROOT, norm_name  # noqa: E402
from vanilla import Vanilla  # noqa: E402

OLLAMA = os.environ.get("SKYHELPER_OLLAMA", "http://127.0.0.1:11434")
MODEL = os.environ.get("SKYHELPER_MODEL", "qwen3.5:9b")
# When set, every request must carry it in the X-SkyHelper-Token header (needed once the service is reachable from the
# internet through a tunnel, see public.py).
TOKEN = os.environ.get("SKYHELPER_TOKEN", "")
THINK = os.environ.get("SKYHELPER_THINK", "") == "1"   # reasoning before the answer: slower, sometimes more careful
LOG = ROOT / "run" / "skyhelper" / "log.jsonl"
MAX_TOOL_ROUNDS = 3
HISTORY_TURNS = 3
HISTORY_SECONDS = 15 * 60
MAX_ANSWER = 560

RULES = """You are SkyHelper, the helpful assistant inside SkyRPG, a Hypixel SkyBlock-style RPG modpack for Minecraft Java Edition 26.3. Players on a small friends' server ask you questions in the game chat and you answer them there.

How to answer:
- Facts about SkyRPG (items, prices, recipes, drops, NPCs, shops, places, commands, keys, quests, mechanics, numbers) must come from the wiki excerpts in the message or from your tools. Never make up a name, number, recipe, NPC, place or command. If the wiki does not say, use a tool to look; if it still does not say, tell the player you are not sure and name the wiki page to check.
- SkyRPG changes vanilla Minecraft a lot (coins, custom skills, custom items, its own gear crafting at the Artisan Bench). The normal crafting table, furnace and so on still work for vanilla recipes: get those from vanilla_recipe, never from memory. Only for plain vanilla mechanics (redstone, brewing, mob behaviour, building) may you answer from your own knowledge.
- "this", "it", "my item" or "the thing I'm holding" mean the item in the player's hand, shown under Player.
- Use the player's situation (location, skills, gear, coins, quest) when it helps. For "what should I do next?" look at their quest, skills and gear and give one or two concrete next steps (the Skyhaven Chronicle quest in the journal guides new players).
- Reply in the language the player wrote in, but call tools with English names (ugalj is coal, mač is sword).
- This is Minecraft chat: keep it under 50 words (two or three short sentences) unless the player asks for details. If the full answer is a long list, name the few most useful and say the rest is on the wiki page. For a crafting grid, say what goes in each row (top, middle, bottom). Plain text only: no markdown, no headings, no tables, no bullet lists, no emojis. Use exact names and numbers. Write commands as they are typed, like /sell all.
- Be friendly and direct; answer the question first, no greetings or filler.
- You cannot run commands or change the game yourself. When a command would do what the player wants (invite someone to a party, sell items, travel, track a quest, pay someone, and for operators things like /time, /weather, /gamemode or /give), offer it as a button: write the exact, complete command in double braces, like {{/party invite PhilipSGamer}} or {{/time set day}}. The player clicks it and it runs as them, with their own permissions. Use real names and numbers, never placeholders like <player>; at most two buttons. Commands that need an operator (/time, /weather, /gamemode, /give, /tp and the like) only work for operators (see "operator" under Player): offer them to operators, and tell anyone else that only an operator can do that.
- When a wiki page helped, end your reply with its id in double brackets, like [[selling-and-shops]] or [[item-skyrpg_combat-dread_fang]]. Use only ids you have seen in excerpts or tool results."""

TOOLS = [
    {"type": "function", "function": {
        "name": "search_wiki",
        "description": "Keyword search over the whole SkyRPG wiki. Returns the best matching sections. Use it for anything the excerpts do not answer.",
        "parameters": {"type": "object", "properties": {
            "query": {"type": "string", "description": "A few keywords, e.g. 'griffin taming stamina' or 'catacombs waystone'"}},
            "required": ["query"]}}},
    {"type": "function", "function": {
        "name": "lookup",
        "description": "Everything the wiki knows about one named thing: an item, NPC, mob, boss, dungeon, region or page. Gives its page plus every table row that mentions it (who sells or buys it and for how much, what drops it, recipes).",
        "parameters": {"type": "object", "properties": {
            "name": {"type": "string", "description": "The thing's name, e.g. 'Dread Fang', 'Brannoc', 'Tidemother', 'Spider Hollow'"}},
            "required": ["name"]}}},
    {"type": "function", "function": {
        "name": "vanilla_recipe",
        "description": "The exact vanilla Minecraft recipe (crafting table, furnace, stonecutter, smithing) for an item, from the game files.",
        "parameters": {"type": "object", "properties": {
            "item": {"type": "string", "description": "The vanilla item's English name, e.g. 'piston' or 'iron ingot'"}},
            "required": ["item"]}}},
    {"type": "function", "function": {
        "name": "read_page",
        "description": "Read a wiki page by id, or one section of it with 'page-id#section-anchor' (ids are shown in brackets in excerpts and in the page list).",
        "parameters": {"type": "object", "properties": {
            "page": {"type": "string", "description": "e.g. 'selling-and-shops' or 'npcs#the-town-npcs'"}},
            "required": ["page"]}}},
]

kb = Knowledge()
vanilla = Vanilla()
kb_lock = threading.Lock()
history = {}          # player -> [(time, question, answer)]
ollama_lock = threading.Lock()


# --------------------------------------------------------------------------- ollama
def ollama(path, body=None, timeout=120):
    req = urllib.request.Request(OLLAMA + path, data=json.dumps(body).encode() if body is not None else None,
                                 headers={"Content-Type": "application/json"})
    with urllib.request.urlopen(req, timeout=timeout) as r:
        return json.loads(r.read())


def ollama_up():
    try:
        ollama("/api/version", timeout=2)
        return True
    except (OSError, ValueError):
        return False


def ensure_ollama():
    """Starts 'ollama serve' when nothing answers (the Windows tray app usually runs it already)."""
    if ollama_up():
        return True
    exe = os.environ.get("SKYHELPER_OLLAMA_EXE") or "ollama"
    local = Path(os.environ.get("LOCALAPPDATA", "")) / "Programs" / "Ollama" / "ollama.exe"
    if exe == "ollama" and local.exists():
        exe = str(local)
    try:
        flags = getattr(subprocess, "CREATE_NO_WINDOW", 0) | getattr(subprocess, "DETACHED_PROCESS", 0)
        subprocess.Popen([exe, "serve"], stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL, creationflags=flags)
    except OSError as e:
        print("Couldn't start Ollama:", e, file=sys.stderr)
        return False
    for _ in range(30):
        time.sleep(0.5)
        if ollama_up():
            return True
    return False


def chat(messages, tools):
    body = {"model": MODEL, "messages": messages, "stream": False, "think": THINK, "keep_alive": "60m",
            "options": {"temperature": 0.15, "num_ctx": 16384}}
    if THINK:
        body["options"]["num_predict"] = 1500   # thinking counts too: a runaway train of thought stops here
    if tools:
        body["tools"] = tools
    with ollama_lock:   # one at a time: the GPU runs one answer at a time anyway, and this keeps the order fair
        out = ollama("/api/chat", body)
        msg = out.get("message", {})
        if THINK and not msg.get("content", "").strip() and not msg.get("tool_calls"):
            # it thought itself out of tokens: answer again without thinking
            body["think"] = False
            body["options"].pop("num_predict", None)
            out = ollama("/api/chat", body)
        return out


# --------------------------------------------------------------------------- prompt pieces
def system_prompt():
    return RULES + "\n\nThe wiki's pages (id: title - what it covers):\n" + kb.overview()


def describe(value, indent=""):
    if isinstance(value, dict):
        return "\n".join("%s%s: %s" % (indent, k, describe(v, indent + "  ") if not isinstance(v, (dict, list)) else
                                       "\n" + describe(v, indent + "  ")) for k, v in value.items() if v not in (None, "", [], {}))
    if isinstance(value, list):
        if all(not isinstance(v, (dict, list)) for v in value):
            return ", ".join(str(v) for v in value)
        return "\n".join(indent + "- " + (describe(v) if not isinstance(v, list) else ", ".join(map(str, v))) for v in value)
    return str(value)


def held_name(context):
    held = (context or {}).get("held") or {}
    return held.get("name") if isinstance(held, dict) else None


def held_page(context):
    held = (context or {}).get("held") or {}
    if isinstance(held, dict) and held.get("id"):
        return kb.item_page(held["id"])
    return None


# words that name the whole game rather than a thing in it ("in SkyRPG" would look up an emote pack of that name)
GENERIC_NAMES = {"skyrpg", "minecraft", "vanilla", "game", "the game", "server", "wiki", "skyrpg wiki"}

PLAN_SCHEMA = {"type": "object", "required": ["english", "things", "vanilla_recipe"], "properties": {
    "english": {"type": "string"},
    "things": {"type": "array", "items": {"type": "string"}},
    "vanilla_recipe": {"type": "boolean"}}}


def plan(question, context, past):
    """Step one: the question in plain English with 'this'/'it' resolved, and the named things it is about.

    Retrieval runs on this, not on the raw question, so a Serbian question, a typo or "how much is this?" still finds
    the right pages. It shares the system prompt with the answer, so the model's cached prefix is reused."""
    lines = ["TASK: do not answer yet. Prepare a wiki search for the player's question."]
    hn = held_name(context)
    if hn:
        lines.append("Item in the player's hand: " + hn)
    if past:
        lines.append("Recent conversation:")
        for _, q, a in past[-2:]:
            lines.append("Player: " + q)
            lines.append("You: " + a)
    lines.append("Question: " + question)
    lines.append('Reply with JSON: "english" is the question rewritten in plain English with words like this, it, my '
                 'item replaced by what they mean; "things" lists the specific items, NPCs, mobs, bosses, places, skills '
                 'or game features it is about, with their English names (at most 3, empty if none); "vanilla_recipe" is '
                 'true only if it asks how to craft or smelt a normal Minecraft item.')
    body = {"model": MODEL, "stream": False, "think": False, "keep_alive": "60m", "format": PLAN_SCHEMA,
            "options": {"temperature": 0, "num_predict": 120, "num_ctx": 16384},
            "messages": [{"role": "system", "content": system_prompt()}, {"role": "user", "content": "\n".join(lines)}]}
    try:
        with ollama_lock:
            out = json.loads(ollama("/api/chat", body)["message"]["content"])
        things = [str(t) for t in out.get("things", []) if str(t).strip()][:3]
        return {"english": str(out.get("english") or question), "things": things,
                "vanilla_recipe": bool(out.get("vanilla_recipe"))}
    except (ValueError, KeyError, TypeError):
        return {"english": question, "things": [], "vanilla_recipe": False}


def excerpts(question, context, p):
    """What to put in front of the model before it even asks: the named things, the held item, and the best sections."""
    parts, refs, budget = [], [], 8000
    english = p["english"]
    hp, hn = held_page(context), held_name(context)
    # (label for lookup, page id or None): what the plan picked first, then pages and headings named in the text, then
    # names that only appear in some table
    targets, found = [], kb.find_names(question + " \n " + english)
    for thing in p["things"]:
        if norm_name(thing) in GENERIC_NAMES:
            continue
        if hn and norm_name(thing) == norm_name(hn) and hp:
            targets.insert(0, (kb.pages[hp]["title"], hp))
            continue
        hit = kb.resolve(thing)
        if hit:
            targets.append((kb.pages[hit[1]]["title"] if hit[0] == "page" else thing, hit[1]))
        elif not p["vanilla_recipe"]:
            targets.append((thing, None))
    for rows_too in (False, True):
        for name, kind, pid, anchor in found:
            if name not in GENERIC_NAMES and (kind == "row") == rows_too:
                targets.append((kb.pages[pid]["title"] if kind == "page" else name, pid))
    seen_t, budget_items = set(), 2
    for label, pid in targets:
        key = norm_name(label)
        if key in seen_t or budget_items == 0:
            continue
        seen_t.add(key)
        budget_items -= 1
        parts.append(kb.lookup(label)[:3000])
        if pid:
            refs.append(pid)
            p.setdefault("named", []).append(pid)   # a page the question is about: the [Wiki] link when the model gives none
    if p["vanilla_recipe"]:
        wanted = list(p["things"]) + vanilla.names_in(english)
        for thing in list(dict.fromkeys(wanted))[:2]:
            recipe = vanilla.recipe(thing)
            if not recipe.startswith("No vanilla"):
                parts.append("Vanilla recipe (from the game files):\n" + recipe)
    used = sum(len(x) for x in parts)
    # the question without the names in it says what is wanted (sell, craft, find...); search on that and on the whole
    bare = " " + norm_name(english) + " "
    for label, _ in targets:
        bare = bare.replace(" " + norm_name(label) + " ", " ")
    seen = set()
    for q in (bare.strip(), english):
        if not q.strip():
            continue
        for sec in kb.search(q, k=3, items=False):
            if sec.ref() in seen or (sec.page in refs and sec.anchor is None):
                continue
            seen.add(sec.ref())
            text = sec.show(1400)
            if used + len(text) > budget:
                break
            parts.append(text)
            refs.append(sec.page)
            used += len(text)
    return parts, refs


def user_message(player, question, context, public, past):
    p = plan(question, context, past)
    lines = ["Player: %s (asking in %s)" % (player, "public chat" if public else "a private /ask")]
    if context:
        lines.append(describe(context))
    parts, refs = excerpts(question, context, p)
    lines.append("")
    lines.append("Question: " + question.strip())
    if p["english"].strip().lower() != question.strip().lower():
        lines.append("(In plain English: %s)" % p["english"])
    if parts:
        lines.append("")
        lines.append("Wiki excerpts found for this question (if they don't answer it, use a tool):")
        lines.append("\n\n".join(parts))
    return "\n".join(lines), refs, p


# --------------------------------------------------------------------------- tools
def run_tool(name, args):
    if isinstance(args, str):
        try:
            args = json.loads(args)
        except ValueError:
            args = {"query": args}
    args = args or {}
    if name == "search_wiki":
        found = kb.search(str(args.get("query", "")), k=5)
        return "\n\n".join(s.show(1300) for s in found) or "No wiki section matches that."
    if name == "lookup":
        return kb.lookup(str(args.get("name", "")))[:4500]
    if name == "vanilla_recipe":
        return vanilla.recipe(str(args.get("item", "")))
    if name == "read_page":
        ref = str(args.get("page", "")).strip().strip("[]")
        pid, _, anchor = ref.partition("#")
        text = kb.page_text(pid, anchor or None)
        if text is None:
            hit = kb.resolve(pid.replace("-", " "))
            if hit:
                text = kb.page_text(hit[1], hit[2])
        return text or "No page called '%s'. Ids are listed in the page list." % ref
    return "Unknown tool " + name


# --------------------------------------------------------------------------- items in answers
# vanilla names that are also everyday words, or blocks nobody holds
VANILLA_SKIP = {"Air", "Light", "Fire", "Soul Fire", "Water", "Lava", "Barrier", "Structure Void", "Jigsaw Block",
                "Moving Piston", "Piston Head", "Test Block", "Cave Air", "Void Air"}
_item_patterns = None


def item_patterns():
    """(regex, id) for every item name, longest first: SkyRPG names in any case, vanilla names as the game writes them."""
    global _item_patterns
    if _item_patterns is None or _item_patterns[0] != kb.stamp:
        pats = []
        for iid, pid in kb.item_pages.items():
            title = kb.pages.get(pid, {}).get("title")
            if title and len(title) >= 3:
                pats.append((title, iid, re.I))
        sky = {p[0].lower() for p in pats}
        for name, iid in vanilla.item_names().items():
            if len(name) >= 3 and name not in VANILLA_SKIP and name.lower() not in sky:
                pats.append((name, iid, 0))
        pats.sort(key=lambda p: -len(p[0]))
        compiled = [(re.compile(r"(?<![\w\[])" + re.escape(n) + r"(?:e?s)?(?![\w\]])", flags), iid) for n, iid, flags in pats]
        _item_patterns = (kb.stamp, compiled)
    return _item_patterns[1]


def item_spans(text, limit=5):
    """The items an answer names, in order, as {"id", "text"}: the mod shows each like a chat [item]. First mention only."""
    taken, found, seen = [], [], set()
    for pattern, iid in item_patterns():
        if iid in seen:
            continue
        for m in pattern.finditer(text):
            if not any(m.start() < e and s < m.end() for s, e in taken):
                taken.append((m.start(), m.end()))
                found.append((m.start(), {"id": iid, "text": m.group(0)}))
                seen.add(iid)
                break
    found.sort(key=lambda f: f[0])
    return [f[1] for f in found[:limit]]


# --------------------------------------------------------------------------- answering
LINK = re.compile(r"\[\[([a-z0-9_\-]+)(?:#([a-z0-9_\-]+))?\]\]")


def clean(text):
    text = re.sub(r"<think>.*?</think>", "", text, flags=re.S)
    text = text.replace("§", "")
    text = re.sub(r"\*\*(.+?)\*\*", r"\1", text)
    text = re.sub(r"(?<!\w)\*(.+?)\*(?!\w)", r"\1", text)
    text = re.sub(r"`([^`]+)`", r"\1", text)
    text = re.sub(r"^#+\s*", "", text, flags=re.M)
    text = re.sub(r"^\s*[-*]\s+", "", text, flags=re.M)
    text = re.sub(r"\n{2,}", "\n", text).strip()
    if len(text) > MAX_ANSWER:
        cut = text[:MAX_ANSWER]
        end = max(cut.rfind(". "), cut.rfind("! "), cut.rfind("? "))
        text = cut[:end + 1] if end > MAX_ANSWER // 2 else cut.rstrip() + "..."
    return text


def answer(player, question, context=None, public=True):
    t0 = time.time()
    with kb_lock:
        kb.fresh()
    now = time.time()
    past = [h for h in history.get(player, []) if now - h[0] < HISTORY_SECONDS][-HISTORY_TURNS:]
    messages = [{"role": "system", "content": system_prompt()}]
    for _, q, a in past:
        messages.append({"role": "user", "content": q})
        messages.append({"role": "assistant", "content": a})
    content, refs, p = user_message(player, question, context, public, past)
    messages.append({"role": "user", "content": content})
    calls, reply = [], {}
    for round_no in range(MAX_TOOL_ROUNDS + 1):
        reply = chat(messages, TOOLS if round_no < MAX_TOOL_ROUNDS else None)["message"]
        tool_calls = reply.get("tool_calls") or []
        if not tool_calls:
            break
        messages.append({"role": "assistant", "content": reply.get("content", ""), "tool_calls": tool_calls})
        for call in tool_calls:
            fn = call.get("function", {})
            result = run_tool(fn.get("name", ""), fn.get("arguments"))
            calls.append({"tool": fn.get("name"), "args": fn.get("arguments")})
            messages.append({"role": "tool", "tool_name": fn.get("name", ""), "content": result})
            refs.extend(m.group(0)[1:-1].split("#")[0] for m in re.finditer(r"\[[a-z0-9_\-#]+\]", result[:200]))
    raw = reply.get("content", "") or ""
    page, anchor = None, None
    for m in LINK.finditer(raw):
        if m.group(1) in kb.pages:
            page, anchor = m.group(1), m.group(2)
            break
    # a link at the end goes (the mod shows it as a button); one in the middle of a sentence becomes the page title
    body = raw.rstrip()
    while True:
        m = re.search(r"\s*\[\[[^\]]*\]\][\s.]*$", body)
        if not m:
            break
        body = body[:m.start()].rstrip()
    def inline(m):
        before = body[:m.start()].rstrip().rsplit(" ", 1)[-1].lower()
        if before in ("the", "see", "check", "read", "on", "page") and m.group(1) in kb.pages:
            return kb.pages[m.group(1)]["title"]
        return ""   # a citation after a word: drop it
    body = re.sub(r"\s+([.,!?])", r"\1", LINK.sub(inline, body))
    # {{/command}}: a button the asker can click to run it as themselves; the text keeps the command
    actions = []

    def action(m):
        command = " ".join(m.group(1).split())
        if (len(command) <= 120 and "§" not in command and "<" not in command and command not in actions
                and len(actions) < 2):
            actions.append(command)
        return command
    body = re.sub(r"\{\{\s*(/[^{}\n]+?)\s*\}\}", action, body)
    text = clean(body)
    if not page and p.get("named"):
        page = next((r for r in p["named"] if r in kb.pages), None)
    if not text:
        text = "Sorry, I couldn't work that one out. Try the wiki: /wiki"
    history.setdefault(player, []).append((now, question, text))
    history[player] = history[player][-HISTORY_TURNS:]
    result = {"answer": text, "page": page, "anchor": anchor,
              "title": kb.pages[page]["title"] if page else None,
              "url": (kb.web_url + "#" + page) if page and kb.web_url else None,
              "items": item_spans(text),
              "actions": actions,
              "seconds": round(time.time() - t0, 2), "tools": calls, "plan": p}
    log(player, question, context, public, result)
    return result


def log(player, question, context, public, result):
    try:
        LOG.parent.mkdir(parents=True, exist_ok=True)
        with LOG.open("a", encoding="utf-8") as f:
            f.write(json.dumps({"time": time.strftime("%Y-%m-%d %H:%M:%S"), "player": player, "public": public,
                                "question": question, "context": context, **result}, ensure_ascii=False) + "\n")
    except OSError:
        pass


# --------------------------------------------------------------------------- http
class Handler(BaseHTTPRequestHandler):
    def send(self, code, body):
        data = json.dumps(body, ensure_ascii=False).encode("utf-8")
        self.send_response(code)
        self.send_header("Content-Type", "application/json; charset=utf-8")
        self.send_header("Content-Length", str(len(data)))
        self.end_headers()
        self.wfile.write(data)

    def allowed(self):
        if TOKEN and self.headers.get("X-SkyHelper-Token", "") != TOKEN:
            self.send(401, {"error": "bad token"})
            return False
        return True

    def do_GET(self):
        if not self.allowed():
            return
        if self.path == "/health":
            self.send(200, {"ok": True, "model": MODEL, "ollama": ollama_up(), "pages": len(kb.pages)})
        else:
            self.send(404, {"error": "not found"})

    def do_POST(self):
        if not self.allowed():
            return
        if self.path != "/ask":
            self.send(404, {"error": "not found"})
            return
        try:
            body = json.loads(self.rfile.read(int(self.headers.get("Content-Length", 0))) or b"{}")
            question = str(body.get("question", "")).strip()[:500]
            if not question:
                self.send(400, {"error": "no question"})
                return
            player = str(body.get("player", "player"))[:32]
            self.send(200, answer(player, question, body.get("context") or {}, bool(body.get("public", True))))
        except (urllib.error.URLError, OSError, TimeoutError) as e:
            ensure_ollama()
            self.send(503, {"error": "model unavailable: %s" % e})
        except Exception as e:   # one bad question never takes the service down
            self.send(500, {"error": "%s: %s" % (type(e).__name__, e)})

    def log_message(self, fmt, *args):
        pass


def warm_up():
    """Loads the model into memory so the first real question is quick."""
    try:
        ollama("/api/chat", {"model": MODEL, "messages": [{"role": "user", "content": "hi"}], "stream": False,
                             "think": False, "keep_alive": "60m", "options": {"num_predict": 1}}, timeout=300)
    except (OSError, ValueError) as e:
        print("Warm-up failed:", e, file=sys.stderr)


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--port", type=int, default=int(os.environ.get("SKYHELPER_PORT", 8770)))
    parser.add_argument("--ask")
    parser.add_argument("--player", default="Drogashh")
    parser.add_argument("--context", help="JSON context, as the mod would send it")
    args = parser.parse_args()
    if not ensure_ollama():
        sys.exit("Ollama isn't running and couldn't be started.")
    if args.ask:
        result = answer(args.player, args.ask, json.loads(args.context) if args.context else {}, True)
        print(json.dumps(result, ensure_ascii=False, indent=1))
        return
    threading.Thread(target=warm_up, daemon=True).start()
    server = ThreadingHTTPServer(("127.0.0.1", args.port), Handler)
    print("SkyHelper on http://127.0.0.1:%d with %s (%d wiki pages)" % (args.port, MODEL, len(kb.pages)), flush=True)
    server.serve_forever()


if __name__ == "__main__":
    main()
