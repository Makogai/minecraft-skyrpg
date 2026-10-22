(function () {
  "use strict";
  // The site is index.html + wiki.css + wiki.js, data/meta.json (page list, icons, colours), data/pages/<id>.json fetched
  // when a page is opened, data/search.json (loaded on first search) and images under img/. Everything is relative.
  var D = null, PAGES = [], BYID = {}, CATS = [], CATBYID = {};
  var $ = function (id) { return document.getElementById(id); };
  var main = $("main"), side = $("side"), qbox = $("q"), resBox = $("results"), tipBox = $("tip");
  var PH = "ph";

  function h(tag, props) {
    var el = document.createElement(tag);
    if (props) {
      for (var k in props) {
        if (!Object.prototype.hasOwnProperty.call(props, k) || props[k] == null || props[k] === false) continue;
        if (k === "class") el.className = props[k];
        else if (k === "text") el.textContent = props[k];
        else el.setAttribute(k, props[k] === true ? "" : props[k]);
      }
    }
    for (var i = 2; i < arguments.length; i++) add(el, arguments[i]);
    return el;
  }
  function add(el, kid) {
    if (kid == null || kid === false) return;
    if (Array.isArray(kid)) { kid.forEach(function (k) { add(el, k); }); return; }
    el.appendChild(typeof kid === "string" ? document.createTextNode(kid) : kid);
  }
  function clear(el) { while (el.firstChild) el.removeChild(el.firstChild); }
  function store(key, val) { try { if (val === undefined) return localStorage.getItem(key); localStorage.setItem(key, val); } catch (e) { return null; } return null; }

  // ---------------------------------------------------------------- data loading
  var inflight = {}, loaded = {};
  function getJSON(url) {
    return fetch(url).then(function (r) { if (!r.ok) throw new Error(url + ": " + r.status); return r.json(); });
  }
  function loadPage(id) {
    if (loaded[id]) return Promise.resolve(loaded[id]);
    if (!/^[A-Za-z0-9_.\-]+$/.test(id)) return Promise.reject(new Error("bad page id"));
    if (!inflight[id]) {
      inflight[id] = getJSON("data/pages/" + id + ".json").then(function (p) { loaded[id] = p; delete inflight[id]; return p; },
        function (e) { delete inflight[id]; throw e; });
    }
    return inflight[id];
  }

  // ---------------------------------------------------------------- icons, colours, links
  function icon(id) {
    var src = id && D.icons[id];
    if (src) return h("img", { "class": "ic", src: src, alt: "", width: 16, height: 16, loading: "lazy" });
    return h("span", { "class": "ic " + PH, "aria-hidden": "true" });
  }
  function colorStyle(hex, card) {
    if (!hex) return null;
    var key = hex.toUpperCase(), name = D.colors[key];
    if (card) return name === "rare" || name === "epic" ? "var(--tt-" + name + ")" : hex;
    if (name) return "var(--r-" + name + ")";
    return "color-mix(in srgb, " + hex + " 62%, var(--text))";
  }
  function pageHash(link) {
    var i = link.indexOf("#");
    return i < 0 ? link : link.slice(0, i) + "--" + link.slice(i + 1);
  }
  function itemHref(id) {
    var pg = D.itemPage[id];
    return pg ? "#" + pg : null;
  }

  function spanNode(s, card) {
    var text = s.x, node;
    var style = colorStyle(s.color, card);
    if (s.item) {
      var href = itemHref(s.item);
      node = h(href ? "a" : "span", { "class": "isp", href: href, "data-item": s.item }, icon(s.icon || s.item), text);
      if (style) node.style.color = style;
      return node;
    }
    node = document.createTextNode(text);
    if (s.c) node = h("code", null, node);
    if (s.i) node = h("em", null, node);
    if (s.b) node = h("b", null, node);
    if (s.link) {
      node = h("a", { href: "#" + pageHash(s.link) }, node);
    } else if (s.url) {
      node = h("a", { href: s.url, target: "_blank", rel: "noopener noreferrer" }, node);
    }
    if (style) {
      if (node.nodeType === 3) node = h("span", null, node);
      node.style.color = style;
    }
    if (card && /^[+\-]?[\d,.]+%?$/.test(text.trim()) && node.nodeType === 1) node.classList.add("n");
    return node;
  }
  function spansNodes(spans, card) { return (spans || []).map(function (s) { return spanNode(s, card); }); }
  function spansText(spans) { return (spans || []).map(function (s) { return s.x; }).join(""); }

  // ---------------------------------------------------------------- blocks
  var headings;
  function lineNode(spans, extra) {
    var blank = !spansText(spans).trim();
    return h("div", { "class": "tt-line" + (blank ? " gap" : "") + (extra ? " " + extra : "") }, blank ? null : spansNodes(spans, true));
  }
  function cardNode(b) {
    var name = b.link && D.itemPage[b.id] ? h("a", { href: "#" + b.link }, b.name) : b.name;
    var kids = b.bare ? [] : [h("div", { "class": "tt-name", style: "color:" + colorStyle(b.color, true) }, icon(b.icon), name)];
    var tip = b.tip == null ? b.lines.length : b.tip;
    b.lines.forEach(function (ln, i) {
      var node = lineNode(ln, i === tip ? "tt-info" : "");
      if (i === tip) node.classList.add("tt-info");
      kids.push(node);
    });
    return h("article", { "class": "card" + (b.bare ? " bare" : ""), id: "a-" + b.anchor }, kids);
  }
  function heroNode(b) {
    var rc = colorStyle(b.color, false) || "var(--accent)";
    var big = b.big, src = big || D.icons[b.icon], pic;
    if (src) pic = h("img", { "class": "hero-pic " + (big ? "big" : "flat"), src: src, alt: "", width: big ? 256 : 160, height: big ? 256 : 160 });
    else pic = h("span", { "class": "hero-pic ph", "aria-hidden": "true" });
    var h1 = h("h1", { "class": "hero-name" }, b.name);
    return h("section", { "class": "itemhero", style: "--rc:" + rc },
      h("div", { "class": "hero-art" }, pic),
      h("div", { "class": "hero-text" }, h1,
        h("div", { "class": "chips" },
          h("span", { "class": "chip rar" }, b.rarity.toUpperCase()),
          b.kind ? h("span", { "class": "chip" }, b.kind) : null),
        h("code", { "class": "hero-id" }, b.id)));
  }
  function tableNode(b) {
    var head = h("tr", null, b.head.map(function (c, i) {
      return h("th", { "class": b.align[i] === "r" ? "r" : b.align[i] === "c" ? "c" : null, scope: "col" }, spansNodes(c));
    }));
    var rows = b.rows.map(function (r) {
      return h("tr", null, r.map(function (c, i) {
        var cls = b.align[i] === "r" ? "r" : b.align[i] === "c" ? "c" : "";
        if (/^[\d,.%+\-x\s]+$/.test(spansText(c)) && spansText(c).trim()) cls += " num";
        return h("td", { "class": cls.trim() || null }, spansNodes(c));
      }));
    });
    return h("div", { "class": "tablewrap", tabindex: "0", role: "region", "aria-label": "Table" },
      h("table", null, h("thead", null, head), h("tbody", null, rows)));
  }
  function recipeSlot(s, out) {
    var href = itemHref(s.item), src = D.icons[s.icon || s.item];
    var pic = src ? h("img", { src: src, alt: "", width: 32, height: 32, loading: "lazy" }) : h("span", { "class": "ic " + PH, "aria-hidden": "true" });
    var node = h(href ? "a" : "span", { "class": "rslot" + (out ? " out" : ""), href: href, "data-item": s.item, title: s.x }, pic,
      s.n && s.n !== 1 ? h("span", { "class": "rn" }, String(s.n)) : null);
    return node;
  }
  function recipeNode(b) {
    return h("div", { "class": "recipe", role: "group", "aria-label": "Recipe at the " + b.station },
      h("div", { "class": "rstation" }, b.station),
      h("div", { "class": "rrow" },
        h("div", { "class": "rgrid" }, b["in"].map(function (s) { return recipeSlot(s, false); })),
        h("span", { "class": "rarrow", "aria-hidden": "true" }, "\u2192"),
        recipeSlot(b.out, true)));
  }
  function listNode(b) {
    return h(b.t, null, b.items.map(function (it) {
      var li = h("li", null, spansNodes(it.s));
      if (it.sub && it.sub.length) li.appendChild(h(b.t, null, it.sub.map(function (s) { return h("li", null, spansNodes(s.s)); })));
      return li;
    }));
  }
  function blocksNode(blocks, pageId, top) {
    var frag = document.createDocumentFragment(), grid = null;
    blocks.forEach(function (b) {
      if (b.t === "item") {
        if (!grid) { grid = h("div", { "class": "cardgrid" }); frag.appendChild(grid); }
        grid.appendChild(cardNode(b));
        return;
      }
      grid = null;
      var node = null;
      switch (b.t) {
        case "hero": node = heroNode(b); break;
        case "h2": case "h3":
          node = h(b.t, { id: "a-" + b.anchor }, b.text, h("a", { "class": "hl", href: "#" + pageId + "--" + b.anchor, "aria-label": "Link to this section" }, "#"));
          if (top) headings.push({ level: b.t, text: b.text, anchor: b.anchor });
          break;
        case "p": node = h("p", null, spansNodes(b.s)); break;
        case "ul": case "ol": node = listNode(b); break;
        case "recipe": node = recipeNode(b); break;
        case "table": node = tableNode(b); break;
        case "callout":
          node = h("div", { "class": "callout " + b.kind, role: "note" },
            h("b", { "class": "lab" }, b.kind === "tip" ? "Tip" : b.kind === "warning" ? "Warning" : "Note"), blocksNode(b.b, pageId, false));
          break;
        case "hr": node = h("hr"); break;
        case "img":
          node = h("figure", null, b.src ? h("img", { src: b.src, alt: b.caption || "", loading: "lazy" }) : null, b.caption ? h("figcaption", null, b.caption) : null);
          break;
      }
      if (node) frag.appendChild(node);
    });
    return frag;
  }

  // ---------------------------------------------------------------- sidebar
  var open = {};
  function buildSide() {
    clear(side);
    side.appendChild(h("a", { "class": "homelink", href: "#", id: "nav-home" }, "Home"));
    var ul = h("ul");
    CATS.forEach(function (c) {
      var pages = PAGES.filter(function (p) { return p.category === c.id && !p.item; });
      if (!pages.length) return;
      var list = h("ul", { id: "grp-" + c.id, hidden: true }, pages.map(function (p) {
        return h("li", null, h("a", { href: "#" + p.id, "data-page": p.id }, icon(p.icon), p.title));
      }));
      var btn = h("button", { type: "button", "aria-expanded": "false", "aria-controls": "grp-" + c.id, "aria-label": "Show pages in " + c.name }, h("span", { "class": "chev" }));
      btn.addEventListener("click", function () { setOpen(c.id, !open[c.id]); });
      ul.appendChild(h("li", null, h("div", { "class": "grp-row" }, h("a", { href: "#cat." + c.id, "data-cat": c.id }, icon(c.icon), c.name), btn), list));
    });
    side.appendChild(ul);
  }
  function setOpen(cid, on) {
    open[cid] = on;
    var list = $("grp-" + cid);
    if (!list) return;
    list.hidden = !on;
    var btn = list.parentNode.querySelector("button");
    btn.setAttribute("aria-expanded", on ? "true" : "false");
  }
  function markSide(pageId, catId) {
    var links = side.querySelectorAll("a");
    for (var i = 0; i < links.length; i++) {
      var a = links[i], cur = (pageId && a.getAttribute("data-page") === pageId) || (!pageId && catId && a.getAttribute("data-cat") === catId) || (!pageId && !catId && a.id === "nav-home");
      if (cur) a.setAttribute("aria-current", "page"); else a.removeAttribute("aria-current");
    }
    if (catId) setOpen(catId, true);
  }

  // ---------------------------------------------------------------- routes
  var current = { key: null }, token = 0;
  function parseHash() {
    var raw = location.hash.replace(/^#/, "");
    try { raw = decodeURIComponent(raw); } catch (e) { /* keep raw */ }
    if (!raw) return { kind: "home" };
    if (raw.indexOf("cat.") === 0) return { kind: "cat", id: raw.slice(4) };
    var i = raw.indexOf("--");
    return i < 0 ? { kind: "page", id: raw, anchor: null } : { kind: "page", id: raw.slice(0, i), anchor: raw.slice(i + 2) };
  }
  function setTitle(t) { document.title = t ? t + " - SkyRPG Wiki" : "SkyRPG Wiki"; }
  function stampLine() {
    var items = Object.keys(D.itemPage).length, guides = PAGES.filter(function (p) { return !p.item; }).length;
    return "Built " + D.generated.slice(0, 10) + " from the game data: " + guides + " pages and " + items + " item pages.";
  }

  var observer = null;
  function route() {
    if (!D) return;
    var r = parseHash();
    closeDrawer();
    hideResults();
    var key = r.kind + ":" + (r.id || "");
    if (r.kind === "page" && current.key === key && loaded[r.id]) { scrollToAnchor(r.anchor); return; }
    current.key = key;
    var my = ++token;
    if (observer) { observer.disconnect(); observer = null; }
    headings = [];
    clear(main);
    if (r.kind === "home") renderHome();
    else if (r.kind === "cat" && CATBYID[r.id]) renderCat(CATBYID[r.id]);
    else if (r.kind === "page" && BYID[r.id]) {
      var meta = BYID[r.id];
      if (loaded[r.id]) { renderPage(loaded[r.id], r.anchor); return; }
      setTitle(meta.title);
      markSide(meta.item ? null : meta.id, meta.category);
      main.appendChild(h("div", { "class": "page" }, h("p", { "class": "crumb" }, "Loading"), h("h1", null, meta.title), h("p", { "class": "lede", role: "status" }, "Loading the page...")));
      loadPage(r.id).then(function (p) {
        if (my !== token) return;
        clear(main);
        renderPage(p, r.anchor);
      }, function () {
        if (my !== token) return;
        clear(main);
        renderFailed(meta);
      });
    } else renderMissing(r.kind === "page" ? r.id : null);
  }
  function crumb(parts) {
    var n = h("p", { "class": "crumb" });
    parts.forEach(function (p, i) {
      if (i) n.appendChild(document.createTextNode(" / "));
      n.appendChild(p.href ? h("a", { href: p.href }, p.text) : document.createTextNode(p.text));
    });
    return n;
  }
  function finish(h1) {
    if (h1) { h1.tabIndex = -1; try { h1.focus({ preventScroll: true }); } catch (e) { /* ignore */ } }
  }
  function renderHome() {
    setTitle("");
    markSide(null, null);
    var tiles = h("ul", { "class": "tiles" }, CATS.map(function (c) {
      var n = PAGES.filter(function (p) { return p.category === c.id && !p.item; }).length;
      if (!n) return null;
      return h("li", { "class": "tile" }, h("a", { href: "#cat." + c.id }, icon(c.icon), h("b", null, c.name), h("span", null, n + (n === 1 ? " page" : " pages"))));
    }));
    var h1 = h("h1", null, "SkyRPG Wiki");
    main.appendChild(h("div", { "class": "hero" }, h1,
      h("p", { "class": "lede" }, "Everything about SkyRPG, a Minecraft modpack that turns survival into an RPG."),
      h("p", null, "You start in Skyhaven, a town floating in the sky, and go down to the survival world to mine, farm, forage, fish and fight. " +
        "Your gear, skills, pets and enchantments decide how far you get. The guides explain how each part works, and the reference pages list every item, enchantment, mob and boss. " +
        "Every item has its own page with its recipe, where it is used and how to get it."),
      BYID.welcome ? h("p", null, "New here? Start with ", h("a", { href: "#welcome" }, "Welcome"), ".") : null,
      h("p", null, "Press ", h("code", null, "/"), " to search."),
      tiles, h("p", { "class": "stamp" }, stampLine())));
    window.scrollTo(0, 0);
    finish(h1);
  }
  function renderCat(c) {
    setTitle(c.name);
    markSide(null, c.id);
    var pages = PAGES.filter(function (p) { return p.category === c.id && !p.item; });
    var items = PAGES.filter(function (p) { return p.category === c.id && p.item; });
    var h1 = h("h1", null, c.name);
    var kids = [crumb([{ text: "Home", href: "#" }]), h1,
      pages.length ? h("ul", { "class": "plist" }, pages.map(function (p) {
        return h("li", null, h("a", { href: "#" + p.id }, icon(p.icon), h("div", null, h("b", null, p.title), h("span", null, p.summary || ""))));
      })) : h("p", null, "No pages in this section yet.")];
    if (items.length) {
      kids.push(h("h2", { "class": "allitems" }, "Every item"));
      kids.push(h("p", null, "Each item has its own page with its recipe, where it is used and how to get it."));
      Object.keys(D.groups).forEach(function (gid) {
        var list = items.filter(function (p) { return p.group === gid; });
        if (!list.length) return;
        kids.push(h("h3", { "class": "groupname" }, h("a", { href: "#" + gid }, D.groups[gid]), " ", h("span", { "class": "cnt" }, String(list.length))));
        kids.push(h("ul", { "class": "chiplist" }, list.map(function (p) {
          var a = h("a", { href: "#" + p.id, "data-item": p.item }, icon(p.icon), p.title);
          a.style.color = colorStyle(p.color, false);
          return h("li", null, a);
        })));
      });
    }
    main.appendChild(h("div", { "class": "page wide" }, kids));
    window.scrollTo(0, 0);
    finish(h1);
  }
  function renderMissing(id) {
    setTitle("Not found");
    markSide(null, null);
    var h1 = h("h1", null, "Page not found");
    main.appendChild(h("div", { "class": "page" }, h1,
      h("p", null, id ? "There is no page called " : "That address does not match a page.", id ? h("code", null, id) : null, id ? ". It may not be written yet." : ""),
      h("p", null, h("a", { href: "#" }, "Back to the home page"))));
    window.scrollTo(0, 0);
    finish(h1);
  }
  function renderFailed(meta) {
    var h1 = h("h1", null, meta.title);
    main.appendChild(h("div", { "class": "page" }, h1,
      h("p", null, "The page could not be loaded. Check your connection and ", h("a", { href: "", onclick: "location.reload();return false;" }, "try again"), "."),
      h("p", null, h("a", { href: "#" }, "Back to the home page"))));
    finish(h1);
  }
  function renderPage(p, anchor) {
    var meta = BYID[p.id] || p;
    setTitle(p.title);
    markSide(meta.item ? null : p.id, p.category);
    var cat = CATBYID[p.category];
    var isItem = !!meta.item;
    var h1 = h("h1", null, p.title);
    var body = blocksNode(p.blocks, p.id, true);
    var toc = h("nav", { "class": "tocbox", "aria-label": "On this page" });
    if (headings.length > 1) {
      toc.appendChild(h("h2", null, "On this page"));
      toc.appendChild(h("ul", null, headings.map(function (x) {
        return h("li", { "class": x.level }, h("a", { href: "#" + p.id + "--" + x.anchor, "data-a": x.anchor }, x.text));
      })));
    }
    var guides = PAGES.filter(function (q) { return !q.item; });
    var idx = guides.indexOf(meta), prev = isItem ? null : guides[idx - 1], next = isItem ? null : guides[idx + 1];
    var pn = isItem ? null : h("nav", { "class": "pn", "aria-label": "Previous and next page" },
      prev ? h("a", { href: "#" + prev.id, rel: "prev" }, h("small", null, "Previous"), prev.title) : h("span"),
      next ? h("a", { href: "#" + next.id, rel: "next", "class": "nx" }, h("small", null, "Next"), next.title) : null);
    var trail = [{ text: "Home", href: "#" }, { text: cat ? cat.name : "", href: cat ? "#cat." + cat.id : null }];
    if (isItem && D.groups[meta.group]) trail.push({ text: D.groups[meta.group], href: "#" + meta.group });
    var page = h("article", { "class": "page" + (isItem ? " itempage" : "") }, crumb(trail), isItem ? null : h1,
      !isItem && p.summary ? h("p", { "class": "lede" }, p.summary) : null, body);
    main.appendChild(h("div", { "class": "cols" }, h("div", { style: "min-width:0" }, page, pn), toc));
    scrollToAnchor(anchor, true);
    if (!anchor) finish(isItem ? main.querySelector(".hero-name") : h1);
    watchToc();
  }
  function scrollToAnchor(anchor) {
    if (!anchor) { window.scrollTo(0, 0); return; }
    var el = $("a-" + anchor);
    if (!el) { window.scrollTo(0, 0); return; }
    el.scrollIntoView({ block: "start" });
    if (el.classList.contains("card")) { el.classList.add("flash"); setTimeout(function () { el.classList.remove("flash"); }, 2200); }
  }
  function watchToc() {
    var links = main.querySelectorAll(".tocbox a[data-a]");
    if (!links.length || !("IntersectionObserver" in window)) return;
    var map = {};
    for (var i = 0; i < links.length; i++) map[links[i].getAttribute("data-a")] = links[i];
    observer = new IntersectionObserver(function (entries) {
      entries.forEach(function (e) {
        if (!e.isIntersecting) return;
        var a = e.target.id.slice(2);
        for (var k in map) map[k].classList.remove("on");
        if (map[a]) map[a].classList.add("on");
      });
    }, { rootMargin: "-80px 0px -70% 0px" });
    Object.keys(map).forEach(function (a) { var el = $("a-" + a); if (el) observer.observe(el); });
  }

  // ---------------------------------------------------------------- tooltips on item links
  var tipFor = null;
  function fillTip(a, id, p) {
    clear(tipBox);
    var hero = p && p.blocks[0] && p.blocks[0].t === "hero" ? p.blocks[0] : null;
    if (hero) {
      tipBox.appendChild(h("div", { "class": "tt-name", style: "color:" + colorStyle(hero.color, true) }, icon(hero.icon), hero.name));
      var card = null;
      p.blocks.forEach(function (b) { if (!card && b.t === "item") card = b; });
      if (card) {
        card.lines.forEach(function (ln) { tipBox.appendChild(lineNode(ln)); });
        tipBox.appendChild(lineNode([{ x: " " }]));
      }
      tipBox.appendChild(lineNode([{ x: (hero.rarity + " " + (hero.kind || "")).trim().toUpperCase(), color: hero.color, b: true }]));
    } else {
      tipBox.appendChild(h("div", { "class": "tt-name", style: "color:var(--tt-white)" }, icon(id), a.textContent));
      tipBox.appendChild(h("div", { "class": "tt-line" }, D.itemPage[id] ? "Loading..." : "Minecraft item"));
    }
  }
  function showTip(a, x, y) {
    var id = a.getAttribute("data-item"), pid = D.itemPage[id];
    tipFor = a;
    fillTip(a, id, pid ? loaded[pid] : null);
    tipBox.hidden = false;
    placeTip(x, y, a);
    if (pid && !loaded[pid]) {
      loadPage(pid).then(function (p) {
        if (tipFor !== a || tipBox.hidden) return;
        fillTip(a, id, p);
        placeTip(x, y, a);
      }, function () { /* the tooltip just stays short */ });
    }
  }
  function placeTip(x, y, a) {
    var w = tipBox.offsetWidth, hh = tipBox.offsetHeight, vw = window.innerWidth, vh = window.innerHeight;
    if (x == null) { var r = a.getBoundingClientRect(); x = r.left; y = r.bottom; }
    var left = x + 14, top = y + 16;
    if (left + w > vw - 8) left = Math.max(8, vw - w - 8);
    if (top + hh > vh - 8) top = Math.max(8, y - hh - 12);
    tipBox.style.left = left + "px";
    tipBox.style.top = top + "px";
  }
  function hideTip() { tipBox.hidden = true; tipFor = null; }
  function itemLink(t) { return t && t.closest ? t.closest("[data-item]") : null; }
  document.addEventListener("pointerover", function (e) { var a = itemLink(e.target); if (a) showTip(a, e.clientX, e.clientY); });
  document.addEventListener("pointermove", function (e) { var a = itemLink(e.target); if (!a) return; if (tipBox.hidden) showTip(a, e.clientX, e.clientY); else placeTip(e.clientX, e.clientY, a); });
  document.addEventListener("pointerout", function (e) { if (itemLink(e.target)) hideTip(); });
  document.addEventListener("focusin", function (e) { var a = itemLink(e.target); if (a) showTip(a, null, null); });
  document.addEventListener("focusout", function (e) { if (itemLink(e.target)) hideTip(); });
  window.addEventListener("scroll", hideTip, { passive: true });

  // ---------------------------------------------------------------- search (the index is fetched on first use)
  var INDEX = null, indexLoading = null, KINDS = { p: "page", h: "heading", i: "item", t: "text" };
  function loadIndex() {
    if (INDEX) return Promise.resolve(INDEX);
    if (!indexLoading) {
      indexLoading = getJSON("data/search.json").then(function (rows) {
        INDEX = rows.map(function (r) {
          var e = { k: KINDS[r[0]], pid: r[1], anchor: r[2], title: r[3], text: r[4], icon: r[5] || (BYID[r[1]] || {}).icon, where: r[6] };
          e.tl = e.title.toLowerCase(); e.xl = e.text.toLowerCase();
          return e;
        });
        return INDEX;
      }, function (e) { indexLoading = null; throw e; });
    }
    return indexLoading;
  }
  function search(q) {
    var terms = q.toLowerCase().split(/\s+/).filter(Boolean);
    if (!terms.length) return [];
    var out = [], seen = {};
    INDEX.forEach(function (e) {
      var all = e.tl + " " + e.xl, ok = true;
      for (var i = 0; i < terms.length; i++) if (all.indexOf(terms[i]) < 0) { ok = false; break; }
      if (!ok) return;
      var phrase = terms.join(" "), score = 0;
      var inTitle = terms.every(function (t) { return e.tl.indexOf(t) >= 0; });
      if (e.k === "page") score = e.tl === phrase ? 120 : e.tl.indexOf(phrase) === 0 ? 100 : inTitle ? 80 : 30;
      else if (e.k === "item") score = e.tl === phrase ? 110 : e.tl.indexOf(phrase) === 0 ? 95 : inTitle ? 75 : 8;
      else if (e.k === "heading") score = e.tl === phrase ? 70 : inTitle ? 55 : 20;
      else score = inTitle ? 18 : 10;
      var key = e.pid + "#" + (e.anchor || "") + e.k;
      if (e.k === "text") { if (seen[key]) return; }
      seen[key] = true;
      out.push({ e: e, score: score });
    });
    out.sort(function (a, b) { return b.score - a.score; });
    return out.slice(0, 30).map(function (x) { return x.e; });
  }
  function snippet(e, terms) {
    var text = e.k === "page" || e.k === "item" || e.k === "text" ? e.text : "";
    if (e.k === "heading") text = "In " + e.where;
    if (!text) return null;
    var low = text.toLowerCase(), at = -1;
    for (var i = 0; i < terms.length && at < 0; i++) at = low.indexOf(terms[i]);
    var start = Math.max(0, at - 40), s = text.slice(start, start + 140);
    if (start > 0) s = "..." + s;
    if (start + 140 < text.length) s += "...";
    var re = new RegExp("(" + terms.map(function (t) { return t.replace(/[.*+?^${}()|[\]\\]/g, "\\$&"); }).join("|") + ")", "ig");
    var span = h("span", { "class": "rs" });
    s.split(re).forEach(function (part, i) { span.appendChild(i % 2 ? h("mark", null, part) : document.createTextNode(part)); });
    return span;
  }
  var LABEL = { page: "Page", heading: "Section", item: "Item", text: "Text" };
  var sel = -1, shown = [];
  function showResults() {
    var q = qbox.value.trim();
    if (!q) { hideResults(); return; }
    if (!INDEX) {
      clear(resBox);
      resBox.appendChild(h("div", { "class": "res-empty" }, "Loading the search index..."));
      resBox.hidden = false;
      qbox.setAttribute("aria-expanded", "true");
      loadIndex().then(function () { if (qbox.value.trim() === q) showResults(); }, function () {
        clear(resBox);
        resBox.appendChild(h("div", { "class": "res-empty" }, "The search index could not be loaded."));
      });
      return;
    }
    var terms = q.toLowerCase().split(/\s+/).filter(Boolean);
    shown = search(q);
    clear(resBox);
    sel = -1;
    if (!shown.length) resBox.appendChild(h("div", { "class": "res-empty" }, "Nothing found for \"" + q + "\"."));
    shown.slice(0, 12).forEach(function (e, i) {
      var href = "#" + e.pid + (e.anchor ? "--" + e.anchor : "");
      resBox.appendChild(h("a", { "class": "res", href: href, role: "option", id: "res-" + i },
        h("span", { "class": "rt" }, e.icon ? icon(e.icon) : null, e.title, h("span", { "class": "rk" }, LABEL[e.k] + (e.k === "page" ? "" : " in " + e.where))),
        snippet(e, terms)));
    });
    resBox.hidden = false;
    qbox.setAttribute("aria-expanded", "true");
  }
  function hideResults() { resBox.hidden = true; qbox.setAttribute("aria-expanded", "false"); sel = -1; }
  function moveSel(d) {
    var els = resBox.querySelectorAll(".res");
    if (!els.length) return;
    if (sel >= 0) els[sel].classList.remove("sel");
    sel = (sel + d + els.length) % els.length;
    els[sel].classList.add("sel");
    els[sel].scrollIntoView({ block: "nearest" });
  }
  qbox.addEventListener("input", showResults);
  qbox.addEventListener("focus", function () { if (qbox.value.trim()) showResults(); else loadIndex().catch(function () { /* retried on typing */ }); });
  qbox.addEventListener("keydown", function (e) {
    if (e.key === "ArrowDown") { e.preventDefault(); moveSel(1); }
    else if (e.key === "ArrowUp") { e.preventDefault(); moveSel(-1); }
    else if (e.key === "Enter") {
      var els = resBox.querySelectorAll(".res"), el = els[sel >= 0 ? sel : 0];
      if (el) { e.preventDefault(); location.hash = el.getAttribute("href"); qbox.blur(); }
    } else if (e.key === "Escape") { hideResults(); qbox.blur(); }
  });
  resBox.addEventListener("click", function (e) { if (e.target.closest && e.target.closest(".res")) { setTimeout(function () { qbox.value = ""; hideResults(); }, 0); } });
  document.addEventListener("click", function (e) { if (!e.target.closest || !e.target.closest(".search")) hideResults(); });
  document.addEventListener("keydown", function (e) {
    if (e.key === "/" && !/^(INPUT|TEXTAREA|SELECT)$/.test((document.activeElement || {}).tagName || "") && !e.ctrlKey && !e.metaKey && !e.altKey) {
      e.preventDefault(); qbox.focus(); qbox.select();
    } else if (e.key === "Escape") closeDrawer();
  });

  // ---------------------------------------------------------------- drawer and theme
  var menu = $("menu");
  function closeDrawer() { document.body.classList.remove("drawer"); menu.setAttribute("aria-expanded", "false"); }
  menu.addEventListener("click", function () {
    var on = !document.body.classList.contains("drawer");
    document.body.classList.toggle("drawer", on);
    menu.setAttribute("aria-expanded", on ? "true" : "false");
    if (on) { var a = side.querySelector("a"); if (a) a.focus(); }
  });
  $("scrim").addEventListener("click", closeDrawer);
  side.addEventListener("click", function (e) { if (e.target.closest && e.target.closest("a")) closeDrawer(); });

  var themeBtn = $("theme"), mode = "auto";
  function applyTheme() {
    if (mode === "auto") document.documentElement.removeAttribute("data-theme"); else document.documentElement.setAttribute("data-theme", mode);
    themeBtn.textContent = mode.charAt(0).toUpperCase() + mode.slice(1);
    themeBtn.setAttribute("aria-label", "Colour theme: " + mode + ". Activate to change.");
    themeBtn.title = "Colour theme: " + mode;
  }
  var saved = store("skyrpg-wiki-theme");
  if (saved === "light" || saved === "dark") mode = saved;
  applyTheme();
  themeBtn.addEventListener("click", function () {
    mode = mode === "auto" ? "light" : mode === "light" ? "dark" : "auto";
    store("skyrpg-wiki-theme", mode);
    applyTheme();
  });

  // ---------------------------------------------------------------- start
  window.addEventListener("hashchange", route);
  getJSON("data/meta.json").then(function (meta) {
    D = meta;
    PAGES = D.pages; CATS = D.categories;
    PAGES.forEach(function (p) { BYID[p.id] = p; });
    CATS.forEach(function (c) { CATBYID[c.id] = c; });
    buildSide();
    route();
  }, function () {
    clear(main);
    main.appendChild(h("div", { "class": "page" }, h("h1", null, "SkyRPG Wiki"),
      h("p", null, "The wiki data could not be loaded. If you opened this file straight from disk, serve the folder over HTTP (for example ", h("code", null, "python -m http.server"), "), or visit the website.")));
  });
})();
