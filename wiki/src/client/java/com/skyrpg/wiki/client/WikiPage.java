package com.skyrpg.wiki.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.platform.cursor.CursorTypes;
import com.skyrpg.core.client.menu.MenuPage;
import com.skyrpg.core.client.menu.PageContext;
import com.skyrpg.core.client.menu.SkyRpgMenu;
import com.skyrpg.core.client.ui.Draw;
import com.skyrpg.core.client.ui.Theme;
import com.skyrpg.wiki.client.WikiLayout.Env;
import com.skyrpg.wiki.client.WikiLayout.Hit;
import com.skyrpg.wiki.client.WikiLayout.Target;
import java.net.URI;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.ConfirmLinkScreen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * The K menu's Wiki page. Left: a search box, then the categories (collapsible) with their pages, or the search results.
 * Right: a toolbar (back, forward, home, where you are, Open on the web) and the page, drawn from its blocks by
 * {@link WikiLayout}, scrolling with a scrollbar. Links navigate (with a history), item spans show a tooltip.
 */
public final class WikiPage implements MenuPage {
    static final Identifier ID = Identifier.fromNamespaceAndPath("skyrpg_wiki", "wiki");
    private static final WikiPage INSTANCE = new WikiPage();

    private static String requestedPage;
    private static String requestedAnchor;
    private static boolean requested;

    /** Where the reader is: a page (null is the home view), an anchor to jump to, and the scroll to come back to. */
    private static final class Loc {
        final String page;
        final String anchor;
        double scroll = -1;

        Loc(String page, String anchor) {
            this.page = page;
            this.anchor = anchor;
        }
    }

    private record Rect(int x, int y, int w, int h) {
        boolean has(double px, double py) {
            return px >= x && px < x + w && py >= y && py < y + h;
        }
    }

    private enum Kind { CATEGORY, PAGE }

    private record Row(Rect rect, Kind kind, String id) {
    }

    private final WikiField field = new WikiField();
    private boolean focused;
    private final Set<String> collapsed = new HashSet<>();
    private final List<Loc> history = new ArrayList<>(List.of(new Loc(null, null)));
    private int at;
    private List<WikiData.Entry> results = List.of();
    private String resultsFor = "";

    private WikiLayout.Doc doc;
    private String docKey = "";
    private double scroll;
    private int contentHeight;
    private double leftScroll;
    private int leftHeight;
    private String pendingAnchor;
    private double pendingScroll = -1;
    private String highlight;
    private boolean dragging;

    private Rect fieldRect = new Rect(0, 0, 0, 0);
    private Rect listRect = new Rect(0, 0, 0, 0);
    private Rect backRect = new Rect(0, 0, 0, 0);
    private Rect forwardRect = new Rect(0, 0, 0, 0);
    private Rect homeRect = new Rect(0, 0, 0, 0);
    private Rect webRect;
    private Rect contentRect = new Rect(0, 0, 0, 0);
    private Rect barRect = new Rect(0, 0, 0, 0);
    private final List<Row> rows = new ArrayList<>();
    private List<Hit> hits = List.of();

    private WikiPage() {
    }

    static WikiPage instance() {
        return INSTANCE;
    }

    /** Asks the page to show {@code page} (null: where it was) at {@code anchor} the next time it's opened. */
    static void request(String page, String anchor) {
        requestedPage = page;
        requestedAnchor = anchor;
        requested = true;
    }

    // ------------------------------------------------------------------ MenuPage

    @Override
    public Identifier id() {
        return ID;
    }

    @Override
    public Component title() {
        return Component.literal("Wiki");
    }

    @Override
    public Component subtitle() {
        return Component.literal("Guides and reference for everything in SkyRPG");
    }

    @Override
    public ItemStack icon() {
        return new ItemStack(Items.KNOWLEDGE_BOOK);
    }

    @Override
    public int order() {
        return 95;
    }

    @Override
    public void opened() {
        if (requested) {
            requested = false;
            if (requestedPage != null) {
                navigate(requestedPage, requestedAnchor);
            }
        }
        focused = false;
    }

    @Override
    public boolean showSubpage(String name) {
        if (name.equals("home")) {
            navigate(null, null);
            return true;
        }
        if (name.startsWith("search-")) {
            field.set(name.substring(7).replace('_', ' '));
            leftScroll = 0;
            return true;
        }
        String page = name;
        String anchor = null;
        int hash = name.indexOf('#');
        if (hash >= 0) {
            page = name.substring(0, hash);
            anchor = name.substring(hash + 1);
        }
        if (WikiData.entry(page) != null) {
            navigate(page, anchor);
            return true;
        }
        return false;
    }

    @Override
    public Component hint() {
        return Component.literal("[Alt+←] back  [Alt+→] forward  [Wheel] scroll");
    }

    @Override
    public boolean canGoBack() {
        return focused || !field.isEmpty();
    }

    @Override
    public boolean back(PageContext ctx) {
        if (focused || !field.isEmpty()) {
            field.set("");
            focused = false;
            leftScroll = 0;
            return true;
        }
        return false;
    }

    // ------------------------------------------------------------------ navigation

    private Loc here() {
        return history.get(at);
    }

    private void navigate(String page, String anchor) {
        if (page != null && WikiData.entry(page) == null) {
            return;
        }
        here().scroll = scroll;
        while (history.size() > at + 1) {
            history.removeLast();
        }
        Loc current = here();
        boolean same = java.util.Objects.equals(current.page, page) && java.util.Objects.equals(current.anchor, anchor);
        if (!same) {
            history.add(new Loc(page, anchor));
            at = history.size() - 1;
        }
        show(here(), true);
    }

    private void show(Loc loc, boolean jump) {
        pendingAnchor = null;
        pendingScroll = -1;
        if (!jump && loc.scroll >= 0) {
            pendingScroll = loc.scroll;
        } else if (loc.anchor != null) {
            pendingAnchor = loc.anchor;
        } else {
            pendingScroll = 0;
        }
        highlight = loc.anchor != null && loc.anchor.startsWith("item-") ? loc.anchor : null;
        doc = null;
        if (loc.page != null) {
            WikiData.Entry entry = WikiData.entry(loc.page);
            if (entry != null) {
                collapsed.remove(entry.category());
            }
        }
    }

    private void go(int delta) {
        int target = at + delta;
        if (target < 0 || target >= history.size()) {
            return;
        }
        here().scroll = scroll;
        at = target;
        show(here(), false);
    }

    /** Follows a link: "page", "page#anchor" or "#anchor". */
    private void follow(String link) {
        if (link.startsWith("#")) {
            navigate(here().page, link.substring(1));
            return;
        }
        int hash = link.indexOf('#');
        navigate(hash < 0 ? link : link.substring(0, hash), hash < 0 ? null : link.substring(hash + 1));
    }

    private void activate(Target target) {
        if (target.link() != null) {
            follow(target.link());
        } else if (target.item() != null) {
            String page = WikiData.pageForItem(target.item());
            if (page != null) {
                navigate(page, null);
            }
        } else if (target.url() != null) {
            openUrl(target.url());
        }
    }

    private static void openUrl(String url) {
        try {
            Minecraft minecraft = Minecraft.getInstance();
            ConfirmLinkScreen.confirmLinkNow(minecraft.gui.screen(), URI.create(url));
        } catch (IllegalArgumentException e) {
            SkyRpgWikiClient.LOGGER.warn("Bad wiki link {}", url);
        }
    }

    // ------------------------------------------------------------------ search

    private void refreshResults() {
        String query = field.text().trim().toLowerCase(Locale.ROOT);
        if (query.equals(resultsFor)) {
            return;
        }
        resultsFor = query;
        if (query.isEmpty()) {
            results = List.of();
            return;
        }
        String[] tokens = query.split("\\s+");
        List<WikiData.Entry> found = new ArrayList<>();
        List<Integer> scores = new ArrayList<>();
        for (WikiData.Entry e : WikiData.allPages()) {
            int score = score(e, query, tokens);
            if (score > 0) {
                int i = 0;
                while (i < scores.size() && scores.get(i) >= score) {
                    i++;
                }
                found.add(i, e);
                scores.add(i, score);
            }
        }
        results = found;
    }

    private static int score(WikiData.Entry e, String query, String[] tokens) {
        String title = e.title().toLowerCase(Locale.ROOT);
        if (title.equals(query)) {
            return 1000;
        }
        if (title.startsWith(query)) {
            return 800;
        }
        if (title.contains(query)) {
            return 600;
        }
        if (all(title, tokens)) {
            return 500;
        }
        WikiData.Category category = WikiData.category(e.category());
        String summary = e.summary().toLowerCase(Locale.ROOT);
        if (all(summary, tokens)) {
            return 300;
        }
        int matched = 0;
        for (String token : tokens) {
            boolean hit = false;
            for (String word : e.words()) {
                if (word.startsWith(token)) {
                    hit = true;
                    break;
                }
            }
            if (!hit) {
                matched = -1;
                break;
            }
            matched++;
        }
        if (matched > 0) {
            return 200 + matched;
        }
        if (category != null && category.name().toLowerCase(Locale.ROOT).contains(query)) {
            return 150;
        }
        return 0;
    }

    private static boolean all(String text, String[] tokens) {
        for (String token : tokens) {
            if (!text.contains(token)) {
                return false;
            }
        }
        return true;
    }

    // ------------------------------------------------------------------ drawing

    @Override
    public void render(PageContext ctx) {
        GuiGraphicsExtractor g = ctx.graphics();
        Font font = ctx.font();
        int x = ctx.x();
        int y = ctx.y();
        int leftW = Mth.clamp(Math.round(ctx.width() * 0.3f), 112, 168);
        drawLeft(ctx, g, font, x, y, leftW);
        int rx = x + leftW + 10;
        drawRight(ctx, g, font, rx, y, ctx.width() - leftW - 10, ctx.height());
    }

    private void drawLeft(PageContext ctx, GuiGraphicsExtractor g, Font font, int x, int y, int w) {
        fieldRect = new Rect(x, y, w, WikiField.HEIGHT);
        field.draw(g, font, x, y, w, focused, fieldRect.has(ctx.mouseX(), ctx.mouseY()), ctx.mouseX());
        int listY = y + WikiField.HEIGHT + 4;
        int listH = ctx.height() - WikiField.HEIGHT - 4;
        listRect = new Rect(x, listY, w, listH);
        Draw.panel(g, x, listY, w, listH, Theme.SIDEBAR, Theme.CARD_BORDER);
        rows.clear();
        refreshResults();
        boolean searching = !resultsFor.isEmpty();
        String current = here().page;

        g.enableScissor(x + 1, listY + 1, x + w - 1, listY + listH - 1);
        int ry = listY + 3 - (int) Math.round(leftScroll);
        int startY = ry;
        if (searching) {
            if (results.isEmpty()) {
                Draw.fitText(g, font, "No pages match \"" + field.text().trim() + "\"", x + 8, ry + 6, w - 16, Theme.MUTED);
                ry += 22;
            }
            for (WikiData.Entry e : results) {
                Rect r = new Rect(x + 3, ry, w - 6, 22);
                rows.add(new Row(r, Kind.PAGE, e.id()));
                boolean hover = r.has(ctx.mouseX(), ctx.mouseY()) && listRect.has(ctx.mouseX(), ctx.mouseY());
                rowBackground(g, r, e.id().equals(current), hover);
                Draw.item(g, WikiLayout.stack(e.icon()), r.x() + 4, r.y() + 3, 1f);
                Draw.fitText(g, font, e.title(), r.x() + 24, r.y() + 3, r.w() - 28, Theme.TEXT);
                Draw.fitText(g, font, e.summary(), r.x() + 24, r.y() + 12, r.w() - 28, Theme.DIM);
                ry += 23;
            }
        } else {
            for (WikiData.Category category : WikiData.categories()) {
                List<WikiData.Entry> pages = WikiData.pagesOf(category.id());
                if (pages.isEmpty()) {
                    continue;
                }
                boolean open = !collapsed.contains(category.id());
                Rect r = new Rect(x + 3, ry, w - 6, 16);
                rows.add(new Row(r, Kind.CATEGORY, category.id()));
                boolean hover = r.has(ctx.mouseX(), ctx.mouseY()) && listRect.has(ctx.mouseX(), ctx.mouseY());
                if (hover) {
                    Draw.rounded(g, r.x(), r.y(), r.w(), r.h(), Theme.HOVER);
                }
                chevron(g, r.x() + 4, r.y() + 6, open, hover ? Theme.TEXT : Theme.MUTED);
                Draw.item(g, WikiLayout.stack(category.icon()), r.x() + 13, r.y() + 3, 0.625f);
                Draw.fitText(g, font, category.name().toUpperCase(Locale.ROOT), r.x() + 26, r.y() + 4, r.w() - 26 - 6, hover ? Theme.TEXT : Theme.MUTED);
                ry += 17;
                if (open) {
                    for (WikiData.Entry e : pages) {
                        Rect pr = new Rect(x + 3, ry, w - 6, 15);
                        rows.add(new Row(pr, Kind.PAGE, e.id()));
                        boolean pageHover = pr.has(ctx.mouseX(), ctx.mouseY()) && listRect.has(ctx.mouseX(), ctx.mouseY());
                        rowBackground(g, pr, e.id().equals(current), pageHover);
                        Draw.item(g, WikiLayout.stack(e.icon()), pr.x() + 12, pr.y() + 2, 0.7f);
                        Draw.fitText(g, font, e.title(), pr.x() + 26, pr.y() + 4, pr.w() - 30,
                                e.id().equals(current) ? Theme.TEXT : Draw.mix(Theme.MUTED, Theme.TEXT, pageHover ? 1f : 0.35f));
                        ry += 15;
                    }
                    ry += 2;
                }
            }
        }
        leftHeight = ry - startY + 6;
        g.disableScissor();
        double maxScroll = Math.max(0, leftHeight - (listH - 2));
        leftScroll = Mth.clamp(leftScroll, 0, maxScroll);
        if (maxScroll > 0) {
            Draw.scrollbar(g, x + w - 4, listY + 2, listH - 4, leftHeight, leftScroll, Theme.DIM);
        }
    }

    private static void rowBackground(GuiGraphicsExtractor g, Rect r, boolean selected, boolean hover) {
        if (selected) {
            Draw.rounded(g, r.x(), r.y(), r.w(), r.h(), Theme.ACCENT_SOFT);
            g.fill(r.x(), r.y() + 3, r.x() + 2, r.y() + r.h() - 3, Theme.ACCENT);
        } else if (hover) {
            Draw.rounded(g, r.x(), r.y(), r.w(), r.h(), Theme.HOVER);
        }
    }

    private static void chevron(GuiGraphicsExtractor g, int x, int y, boolean open, int color) {
        if (open) {
            g.fill(x, y, x + 5, y + 1, color);
            g.fill(x + 1, y + 1, x + 4, y + 2, color);
            g.fill(x + 2, y + 2, x + 3, y + 3, color);
        } else {
            g.fill(x + 1, y - 2, x + 2, y + 3, color);
            g.fill(x + 2, y - 1, x + 3, y + 2, color);
            g.fill(x + 3, y, x + 4, y + 1, color);
        }
    }

    private void button(PageContext ctx, GuiGraphicsExtractor g, Font font, Rect r, String label, boolean enabled) {
        boolean hover = enabled && r.has(ctx.mouseX(), ctx.mouseY());
        Draw.panel(g, r.x(), r.y(), r.w(), r.h(), hover ? Theme.CARD_HOVER : Theme.CARD, hover ? Theme.ACCENT : Theme.CARD_BORDER);
        Draw.text(g, font, label, r.x() + (r.w() - font.width(label)) / 2, r.y() + (r.h() - 8) / 2 + 1,
                enabled ? (hover ? Theme.TEXT : Theme.MUTED) : Theme.DIM);
        if (hover) {
            g.requestCursor(CursorTypes.POINTING_HAND);
        }
    }

    private void drawRight(PageContext ctx, GuiGraphicsExtractor g, Font font, int rx, int y, int rw, int height) {
        // toolbar
        int bh = 14;
        backRect = new Rect(rx, y, 16, bh);
        forwardRect = new Rect(rx + 19, y, 16, bh);
        homeRect = new Rect(rx + 38, y, font.width("Home") + 12, bh);
        button(ctx, g, font, backRect, "<", at > 0);
        button(ctx, g, font, forwardRect, ">", at < history.size() - 1);
        button(ctx, g, font, homeRect, "Home", true);
        int right = rx + rw;
        String webUrl = WikiData.webUrl();
        if (!webUrl.isBlank()) {
            String label = "Open on the web";
            webRect = new Rect(right - font.width(label) - 14, y, font.width(label) + 14, bh);
            button(ctx, g, font, webRect, label, true);
            right = webRect.x() - 6;
        } else {
            webRect = null;
        }
        int crumbX = homeRect.x() + homeRect.w() + 8;
        int crumbRoom = Math.max(10, right - crumbX);
        WikiData.Entry shown = WikiData.entry(here().page);
        String crumb = shown == null ? "" : Draw.firstFitting(font, crumbRoom, breadcrumb(), shown.title());
        Draw.fitText(g, font, crumb, crumbX, y + 3, crumbRoom, Theme.MUTED);

        // the page
        int top = y + bh + 6;
        int h = height - bh - 6;
        contentRect = new Rect(rx, top, rw, h);
        int docW = rw - 10;
        String key = here().page + "|" + docW;
        if (doc == null || !key.equals(docKey)) {
            doc = buildDoc(font, docW);
            docKey = key;
            contentHeight = doc.height();
        }
        if (pendingAnchor != null) {
            Integer off = doc.anchors().get(pendingAnchor);
            scroll = off == null ? 0 : Math.max(0, off - 2);
            pendingAnchor = null;
        } else if (pendingScroll >= 0) {
            scroll = pendingScroll;
            pendingScroll = -1;
        }
        double maxScroll = Math.max(0, contentHeight - h);
        scroll = Mth.clamp(scroll, 0, maxScroll);

        Env env = new Env(g, font, ctx.mouseX(), ctx.mouseY(), top, top + h, highlight);
        g.enableScissor(rx, top, rx + rw, top + h);
        doc.draw(env, rx, top - (int) Math.round(scroll));
        g.disableScissor();
        hits = env.hits;
        if (maxScroll > 0) {
            barRect = new Rect(rx + rw - 8, top, 8, h);
            Draw.scrollbar(g, rx + rw - 3, top, h, contentHeight, scroll, dragging ? Theme.ACCENT : Theme.DIM);
        } else {
            barRect = new Rect(0, 0, 0, 0);
        }
        if (env.hovered != null && contentRect.has(ctx.mouseX(), ctx.mouseY())) {
            g.requestCursor(CursorTypes.POINTING_HAND);
            ctx.tooltip(tooltip(env.hovered), env.hovered.color() != 0 ? env.hovered.color() : Theme.ACCENT);
        }
    }

    private String breadcrumb() {
        Loc loc = here();
        WikiData.Entry entry = WikiData.entry(loc.page);
        if (entry == null) {
            return "Home";
        }
        WikiData.Category category = WikiData.category(entry.category());
        return (category == null ? "" : category.name() + "  ›  ") + entry.title();
    }

    private List<Component> tooltip(Target target) {
        List<Component> lines = new ArrayList<>();
        int color = target.color() != 0 ? target.color() : Theme.TEXT;
        String pageId = target.link();
        if (target.item() != null) {
            lines.add(Component.literal(target.name()).withColor(color & 0xFFFFFF));
            lines.add(Component.literal(target.item()).withColor(Theme.DIM & 0xFFFFFF));
            if (pageId == null) {
                String page = WikiData.pageForItem(target.item());
                if (page != null) {
                    pageId = page;
                }
            }
        }
        if (pageId != null) {
            String id = pageId.startsWith("#") ? here().page : pageId.contains("#") ? pageId.substring(0, pageId.indexOf('#')) : pageId;
            WikiData.Entry entry = WikiData.entry(id);
            if (entry != null) {
                if (target.item() == null) {
                    lines.add(Component.literal(entry.title()).withColor(Theme.TEXT & 0xFFFFFF));
                    if (!entry.summary().isEmpty()) {
                        lines.add(Component.literal(entry.summary()).withColor(Theme.MUTED & 0xFFFFFF));
                    }
                } else {
                    lines.add(Component.literal("Click: open in " + entry.title()).withColor(Theme.MUTED & 0xFFFFFF));
                }
            }
        }
        if (target.url() != null) {
            lines.add(Component.literal("Opens in your browser").withColor(Theme.TEXT & 0xFFFFFF));
            lines.add(Component.literal(target.url()).withColor(Theme.DIM & 0xFFFFFF));
        }
        if (lines.isEmpty()) {
            lines.add(Component.literal(target.name()).withColor(Theme.TEXT & 0xFFFFFF));
        }
        return lines;
    }

    // ------------------------------------------------------------------ the page's blocks

    private WikiLayout.Doc buildDoc(Font font, int width) {
        Loc loc = here();
        WikiData.Entry entry = WikiData.entry(loc.page);
        if (entry == null) {
            return WikiLayout.buildBlocks(font, home(), width);
        }
        JsonObject page = WikiData.page(entry.id());
        JsonArray blocks = new JsonArray();
        // an item page starts with its hero, which already has the name: no title or summary above it
        boolean itemPage = page != null && !WikiData.array(page, "blocks").isEmpty()
                && WikiData.array(page, "blocks").get(0).isJsonObject()
                && "hero".equals(WikiData.string(WikiData.array(page, "blocks").get(0).getAsJsonObject(), "t", ""));
        if (!itemPage) {
            blocks.add(block("h1", "text", entry.title()));
            if (!entry.summary().isEmpty()) {
                blocks.add(paragraph(entry.summary(), "#8D96A8", true));
            }
        }
        if (page == null) {
            blocks.add(callout("warning", "This page is missing from the wiki data. The wiki may be out of date."));
        } else {
            blocks.addAll(WikiData.array(page, "blocks"));
        }
        return WikiLayout.buildBlocks(font, blocks, width);
    }

    private static JsonArray home() {
        JsonArray blocks = new JsonArray();
        blocks.add(block("h1", "text", "SkyRPG Wiki"));
        List<WikiData.Entry> pages = WikiData.pages();
        if (pages.isEmpty()) {
            blocks.add(callout("warning", "The wiki has no pages. Its data is missing from this install."));
            return blocks;
        }
        blocks.add(paragraph("Everything about SkyRPG in one place. Pick a page on the left, search above, or press the Look it up key (H) over any item.", "#8D96A8", true));
        blocks.add(callout("tip", "Click any link to jump to it. Use the arrow buttons or Alt+Left and Alt+Right to go back and forward."));
        for (WikiData.Category category : WikiData.categories()) {
            List<WikiData.Entry> inCategory = WikiData.pagesOf(category.id());
            if (inCategory.isEmpty()) {
                continue;
            }
            blocks.add(block("h2", "text", category.name()));
            JsonObject list = new JsonObject();
            list.addProperty("t", "ul");
            JsonArray items = new JsonArray();
            for (WikiData.Entry e : inCategory) {
                JsonObject item = new JsonObject();
                JsonArray spans = new JsonArray();
                JsonObject name = new JsonObject();
                name.addProperty("x", e.title());
                name.addProperty("link", e.id());
                if (!e.icon().isEmpty()) {
                    name.addProperty("icon", e.icon());
                }
                spans.add(name);
                if (!e.summary().isEmpty()) {
                    JsonObject summary = new JsonObject();
                    summary.addProperty("x", " – " + e.summary());
                    summary.addProperty("color", "#8D96A8");
                    spans.add(summary);
                }
                item.add("s", spans);
                items.add(item);
            }
            list.add("items", items);
            blocks.add(list);
        }
        return blocks;
    }

    private static JsonObject block(String type, String key, String value) {
        JsonObject o = new JsonObject();
        o.addProperty("t", type);
        o.addProperty(key, value);
        return o;
    }

    private static JsonObject paragraph(String text, String color, boolean italic) {
        JsonObject o = new JsonObject();
        o.addProperty("t", "p");
        JsonArray spans = new JsonArray();
        JsonObject span = new JsonObject();
        span.addProperty("x", text);
        span.addProperty("color", color);
        if (italic) {
            span.addProperty("i", true);
        }
        spans.add(span);
        o.add("s", spans);
        return o;
    }

    private static JsonObject callout(String kind, String text) {
        JsonObject o = new JsonObject();
        o.addProperty("t", "callout");
        o.addProperty("kind", kind);
        JsonArray inner = new JsonArray();
        JsonObject p = new JsonObject();
        p.addProperty("t", "p");
        JsonArray spans = new JsonArray();
        JsonObject span = new JsonObject();
        span.addProperty("x", text);
        spans.add(span);
        p.add("s", spans);
        inner.add(p);
        o.add("b", inner);
        return o;
    }

    // ------------------------------------------------------------------ input

    private void scrollTo(double target) {
        scroll = Mth.clamp(target, 0, Math.max(0, contentHeight - contentRect.h()));
    }

    @Override
    public boolean mouseClicked(PageContext ctx, double x, double y, int button) {
        if (button == InputConstants.MOUSE_BUTTON_4) {
            go(-1);
            return true;
        }
        if (button == InputConstants.MOUSE_BUTTON_5) {
            go(1);
            return true;
        }
        if (button != InputConstants.MOUSE_BUTTON_LEFT) {
            return false;
        }
        if (fieldRect.has(x, y)) {
            if (!field.isEmpty() && WikiField.overClear(fieldRect.x(), fieldRect.w(), x)) {
                field.set("");
                leftScroll = 0;
            } else {
                focused = true;
            }
            return true;
        }
        focused = false;
        if (listRect.has(x, y)) {
            for (Row row : rows) {
                if (row.rect().has(x, y)) {
                    if (row.kind() == Kind.CATEGORY) {
                        if (!collapsed.remove(row.id())) {
                            collapsed.add(row.id());
                        }
                    } else {
                        navigate(row.id(), null);
                    }
                    return true;
                }
            }
            return true;
        }
        if (backRect.has(x, y)) {
            go(-1);
            return true;
        }
        if (forwardRect.has(x, y)) {
            go(1);
            return true;
        }
        if (homeRect.has(x, y)) {
            navigate(null, null);
            return true;
        }
        if (webRect != null && webRect.has(x, y)) {
            openUrl(WikiData.webUrl());
            return true;
        }
        if (barRect.has(x, y)) {
            dragging = true;
            dragTo(y);
            return true;
        }
        if (contentRect.has(x, y)) {
            for (Hit hit : hits) {
                if (x >= hit.x() && x < hit.x() + hit.w() && y >= hit.y() && y < hit.y() + hit.h()) {
                    activate(hit.target());
                    return true;
                }
            }
        }
        return false;
    }

    private void dragTo(double y) {
        int h = contentRect.h();
        int thumb = Mth.clamp(h * h / Math.max(1, contentHeight), 14, h);
        double ratio = (y - contentRect.y() - thumb / 2.0) / Math.max(1, h - thumb);
        scrollTo(ratio * Math.max(0, contentHeight - h));
    }

    @Override
    public boolean mouseDragged(PageContext ctx, double x, double y, int button) {
        if (dragging) {
            dragTo(y);
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseReleased(PageContext ctx, double x, double y, int button) {
        boolean was = dragging;
        dragging = false;
        return was;
    }

    @Override
    public boolean mouseScrolled(PageContext ctx, double x, double y, double amount) {
        if (x < contentRect.x() - 5) {
            leftScroll = Mth.clamp(leftScroll - amount * 18, 0, Math.max(0, leftHeight - listRect.h() + 2));
        } else {
            scrollTo(scroll - amount * 26);
        }
        return true;
    }

    @Override
    public boolean keyPressed(PageContext ctx, KeyEvent event) {
        int key = event.key();
        if (event.hasAltDown() && key == InputConstants.KEY_LEFT) {
            go(-1);
            return true;
        }
        if (event.hasAltDown() && key == InputConstants.KEY_RIGHT) {
            go(1);
            return true;
        }
        if (focused) {
            if (key == InputConstants.KEY_RETURN || key == InputConstants.KEY_NUMPADENTER) {
                refreshResults();
                if (!results.isEmpty()) {
                    navigate(results.getFirst().id(), null);
                }
                return true;
            }
            if (field.keyPressed(event)) {
                leftScroll = 0;
            }
            return true;
        }
        int page = Math.max(20, contentRect.h() - 20);
        switch (key) {
            case InputConstants.KEY_PAGEDOWN -> scrollTo(scroll + page);
            case InputConstants.KEY_PAGEUP -> scrollTo(scroll - page);
            case InputConstants.KEY_HOME -> scrollTo(0);
            case InputConstants.KEY_END -> scrollTo(contentHeight);
            default -> {
                return false;
            }
        }
        return true;
    }

    @Override
    public boolean charTyped(PageContext ctx, CharacterEvent event) {
        if (field.charTyped(event)) {
            focused = true;
            leftScroll = 0;
            return true;
        }
        return false;
    }
}
