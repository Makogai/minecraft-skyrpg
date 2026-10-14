"""Assembles the single-file website from the page data, the icons and the CSS/JS in tools/wiki/web/."""
import json
import os

HERE = os.path.dirname(os.path.abspath(__file__))

BODY = """<a class="skip" href="#main">Skip to the page</a>
<div id="app">
<header class="top"><div class="top-in">
<button id="menu" class="icon-btn" type="button" aria-label="Open the section menu" aria-expanded="false" aria-controls="side"><span class="bars"></span></button>
<a class="logo" href="#">Sky<span>RPG</span><span class="lw"> Wiki</span></a>
<div class="search"><input id="q" type="search" placeholder="Search the wiki" aria-label="Search the wiki" autocomplete="off" role="combobox" aria-expanded="false" aria-controls="results" aria-autocomplete="list"><div id="results" class="results" role="listbox" hidden></div></div>
<button id="theme" class="icon-btn" type="button">Auto</button>
</div></header>
<div class="shell">
<nav id="side" class="side" aria-label="Wiki sections"></nav>
<main id="main" tabindex="-1"><noscript>The wiki needs JavaScript to show its pages.</noscript></main>
</div>
<div id="scrim" class="scrim"></div>
<div id="tip" class="tt tt-float" role="tooltip" hidden></div>
</div>
"""

SKIP_CSS = """
.skip { position: absolute; left: -999px; top: 0; background: var(--accent); color: var(--on-accent); padding: 8px 12px; z-index: 100; border-radius: 0 0 8px 0; }
.skip:focus { left: 0; }
"""


def read(name):
    with open(os.path.join(HERE, "web", name), encoding="utf-8") as f:
        return f.read()


def json_for_html(obj):
    s = json.dumps(obj, ensure_ascii=True, separators=(",", ":"))
    return s.replace("<", "\\u003c").replace(">", "\\u003e").replace("&", "\\u0026")


def build_parts(data):
    css = read("wiki.css") + SKIP_CSS
    js = read("wiki.js")
    head = "<title>SkyRPG Wiki</title>\n<style>\n" + css + "\n</style>\n"
    body = BODY + '<script type="application/json" id="wiki-data">' + json_for_html(data) + "</script>\n<script>\n" + js + "\n</script>\n"
    return head, body


def artifact_html(data):
    head, body = build_parts(data)
    return head + body


def standalone_html(data):
    head, body = build_parts(data)
    return ('<!doctype html>\n<html lang="en">\n<head>\n<meta charset="utf-8">\n'
            '<meta name="viewport" content="width=device-width, initial-scale=1">\n' + head + "</head>\n<body>\n" + body + "</body>\n</html>\n")
