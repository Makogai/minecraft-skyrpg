"""The website shell (index.html, wiki.css, wiki.js) from the CSS/JS in tools/wiki/web/; the data files come from build.py."""
import hashlib
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




def shell_files():
    """The site's fixed files: {published path: bytes}. The CSS and JS carry a content hash so a new build is never served stale."""
    css = (read("wiki.css") + SKIP_CSS).encode("utf-8")
    js = read("wiki.js").encode("utf-8")
    v = hashlib.sha1(css + js).hexdigest()[:8]
    nl = chr(10)
    head = nl.join([
        '<!doctype html>',
        '<html lang="en">',
        '<head>',
        '<meta charset="utf-8">',
        '<meta name="viewport" content="width=device-width, initial-scale=1">',
        '<meta name="description" content="The SkyRPG wiki: guides and a page for every item of the SkyRPG Minecraft modpack.">',
        '<title>SkyRPG Wiki</title>',
        '<link rel="icon" href="data:,">',
        '<link rel="stylesheet" href="wiki.css?v=%s">' % v,
        '</head>',
        '<body>', ''])
    html = head + BODY + '<script src="wiki.js?v=%s"></script>' % v + nl + '</body>' + nl + '</html>' + nl
    return {"index.html": html.encode("utf-8"), "wiki.css": css, "wiki.js": js, ".nojekyll": b""}
