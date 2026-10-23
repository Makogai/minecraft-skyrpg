package com.skyrpg.wiki.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.skyrpg.core.client.ui.Draw;
import com.skyrpg.core.client.ui.Theme;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Turns a page's blocks into boxes of a known width and draws them. Layout happens once per page and width; drawing only
 * touches the boxes on screen. Everything is in the menu's own units (the menu scales the window).
 */
final class WikiLayout {
    static final int LINE = 11;
    private static final int ICON = 10;
    private static final int WARNING = 0xFFFFA040;

    private WikiLayout() {
    }

    /** What a clickable or hoverable bit of text points at. */
    record Target(String link, String url, String item, String name, int color) {
    }

    record Hit(int x, int y, int w, int h, Target target) {
    }

    /** Per-frame drawing state: the graphics, the mouse, the visible band, and the hit boxes drawn so far. */
    static final class Env {
        final GuiGraphicsExtractor g;
        final Font font;
        final int mouseX;
        final int mouseY;
        final int clipTop;
        final int clipBottom;
        final String highlight;
        final List<Hit> hits = new ArrayList<>();
        Target hovered;

        Env(GuiGraphicsExtractor g, Font font, int mouseX, int mouseY, int clipTop, int clipBottom, String highlight) {
            this.g = g;
            this.font = font;
            this.mouseX = mouseX;
            this.mouseY = mouseY;
            this.clipTop = clipTop;
            this.clipBottom = clipBottom;
            this.highlight = highlight;
        }

        boolean over(int x, int y, int w, int h) {
            return mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h && mouseY >= clipTop && mouseY < clipBottom;
        }
    }

    // ------------------------------------------------------------------ spans and rich text

    record Span(String text, boolean bold, boolean italic, boolean code, int color, String link, String url, String item, String icon) {
        static Span plain(String text) {
            return new Span(text, false, false, false, 0, null, null, null, null);
        }

        Target target() {
            if (link == null && url == null && item == null) {
                return null;
            }
            return new Target(link, url, item, text, color);
        }
    }

    static List<Span> spans(JsonArray array) {
        List<Span> out = new ArrayList<>();
        if (array == null) {
            return out;
        }
        for (JsonElement e : array) {
            if (e.isJsonObject()) {
                JsonObject o = e.getAsJsonObject();
                out.add(new Span(WikiData.string(o, "x", ""), flag(o, "b"), flag(o, "i"), flag(o, "c"), color(WikiData.string(o, "color", "")),
                        opt(o, "link"), opt(o, "url"), opt(o, "item"), opt(o, "icon")));
            } else if (e.isJsonPrimitive()) {
                out.add(Span.plain(e.getAsString()));
            }
        }
        return out;
    }

    private static boolean flag(JsonObject o, String key) {
        return o.has(key) && o.get(key).isJsonPrimitive() && o.get(key).getAsBoolean();
    }

    private static String opt(JsonObject o, String key) {
        String value = WikiData.string(o, key, "");
        return value.isEmpty() ? null : value;
    }

    /** "#RRGGBB" to opaque ARGB, or 0 for none. */
    static int color(String hex) {
        if (hex == null || hex.length() < 7 || hex.charAt(0) != '#') {
            return 0;
        }
        try {
            return 0xFF000000 | Integer.parseInt(hex.substring(1, 7), 16);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static final Map<String, ItemStack> STACKS = new HashMap<>();

    /** A stack drawn with the given item model: no registry lookup of the item itself is needed. */
    static ItemStack stack(String model) {
        return STACKS.computeIfAbsent(model, m -> {
            ItemStack stack = new ItemStack(Items.STICK);
            Identifier id = Identifier.tryParse(m);
            if (id != null) {
                stack.set(DataComponents.ITEM_MODEL, id);
            }
            return stack;
        });
    }

    /** One word (or icon plus word) placed on a line. */
    private record Run(int x, int w, Component text, Span span, boolean icon, int color) {
    }

    private record Line(List<Run> runs, int width) {
    }

    /** Wrapped rich text: build it for a width, then draw it. */
    static final class RichText {
        private final List<Line> lines = new ArrayList<>();
        final int width;
        private final char align;

        private RichText(int width, char align) {
            this.width = width;
            this.align = align;
        }

        int height() {
            return Math.max(1, lines.size()) * LINE;
        }

        static Style style(Span s, boolean forceBold, int color) {
            Style style = Style.EMPTY.withBold(s.bold() || forceBold).withItalic(s.italic());
            return color != 0 ? style.withColor(color & 0xFFFFFF) : style;
        }

        static int baseColor(Span s, int base) {
            if (s.color() != 0) {
                return s.color();
            }
            if (s.code()) {
                return Theme.AQUA;
            }
            if (s.link() != null || s.url() != null) {
                return Theme.BLUE;
            }
            return base;
        }

        /** The width the text takes on one line (no wrapping), and the widest single word. */
        static int[] measure(Font font, List<Span> spans, boolean forceBold) {
            int total = 0;
            int widest = 0;
            for (Span s : spans) {
                if (s.icon() != null && !s.text().isEmpty()) {
                    total += ICON + 2;
                    widest = Math.max(widest, ICON + 2);
                }
                for (String token : tokens(s.text())) {
                    total += font.width(Component.literal(token).withStyle(style(s, forceBold, 1)));
                    widest = Math.max(widest, font.width(Component.literal(token.stripTrailing()).withStyle(style(s, forceBold, 1))));
                }
            }
            return new int[] {total, widest};
        }

        static List<String> tokens(String text) {
            List<String> out = new ArrayList<>();
            int start = 0;
            for (int i = 0; i < text.length(); i++) {
                if (text.charAt(i) == ' ') {
                    out.add(text.substring(start, i + 1));
                    start = i + 1;
                }
            }
            if (start < text.length()) {
                out.add(text.substring(start));
            }
            return out;
        }

        static RichText layout(Font font, List<Span> spans, int maxWidth, int base, boolean forceBold, char align) {
            RichText rt = new RichText(maxWidth, align);
            List<Run> runs = new ArrayList<>();
            int x = 0;
            for (Span s : spans) {
                int color = baseColor(s, base);
                boolean icon = s.icon() != null && !s.text().isEmpty();
                for (String token : tokens(s.text())) {
                    String visible = token.stripTrailing();
                    Component text = Component.literal(visible).withStyle(style(s, forceBold, color));
                    int textW = font.width(text);
                    int visibleW = textW + (icon ? ICON + 2 : 0) + (s.code() ? 2 : 0);
                    int advance = visibleW + font.width(Component.literal(token.substring(visible.length())).withStyle(style(s, forceBold, color)));
                    if (visible.isEmpty()) {
                        if (!runs.isEmpty()) {
                            x += advance;
                        }
                        continue;
                    }
                    if (x > 0 && x + visibleW > maxWidth) {
                        rt.lines.add(new Line(runs, lineWidth(runs)));
                        runs = new ArrayList<>();
                        x = 0;
                    }
                    runs.add(new Run(x, visibleW, text, s, icon, color));
                    x += advance;
                    icon = false;
                }
            }
            if (!runs.isEmpty() || rt.lines.isEmpty()) {
                rt.lines.add(new Line(runs, lineWidth(runs)));
            }
            return rt;
        }

        private static int lineWidth(List<Run> runs) {
            return runs.isEmpty() ? 0 : runs.getLast().x() + runs.getLast().w();
        }

        void draw(Env e, int x, int y) {
            int ly = y;
            for (Line line : lines) {
                if (ly + LINE >= e.clipTop && ly <= e.clipBottom) {
                    int dx = switch (align) {
                        case 'r' -> Math.max(0, width - line.width());
                        case 'c' -> Math.max(0, (width - line.width()) / 2);
                        default -> 0;
                    };
                    for (Run run : line.runs()) {
                        drawRun(e, run, x + dx + run.x(), ly);
                    }
                }
                ly += LINE;
            }
        }

        private static void drawRun(Env e, Run run, int x, int y) {
            Span s = run.span();
            Target target = s.target();
            boolean hover = target != null && e.over(x, y, run.w(), LINE);
            int tx = x;
            if (target != null) {
                e.hits.add(new Hit(x, y, run.w(), LINE, target));
                if (hover) {
                    e.hovered = target;
                }
            }
            if (s.code()) {
                Draw.rounded(e.g, x, y - 1, run.w(), LINE - 1, Theme.INPUT);
                tx += 1;
            }
            if (run.icon()) {
                Draw.item(e.g, stack(s.icon()), x, y - 1, ICON / 16f);
                tx += ICON + 2;
            }
            Component text = run.text();
            int color = run.color();
            if (hover) {
                color = Draw.lighten(color, 0.35f);
                text = text.copy().withStyle(text.getStyle().withColor(color & 0xFFFFFF));
            }
            e.g.text(e.font, text, tx, y, color, false);
            if (target != null && (s.link() != null || s.url() != null)) {
                int textW = e.font.width(run.text());
                e.g.fill(tx, y + 9, tx + textW, y + 10, hover ? color : Draw.withAlpha(color, 0.55f));
            }
        }
    }

    // ------------------------------------------------------------------ boxes

    abstract static class Box {
        int height;
        /** Space below. */
        int gap = 5;

        abstract void draw(Env e, int x, int y);

        final void render(Env e, int x, int y) {
            if (y + height >= e.clipTop && y <= e.clipBottom) {
                draw(e, x, y);
            }
        }
    }

    /** A finished page: boxes stacked top to bottom, with the anchors' offsets. */
    record Doc(List<Box> boxes, List<Integer> offsets, int height, Map<String, Integer> anchors) {
        void draw(Env e, int x, int y) {
            for (int i = 0; i < boxes.size(); i++) {
                boxes.get(i).render(e, x, y + offsets.get(i));
            }
        }
    }

    private static final class Text extends Box {
        final RichText text;

        Text(RichText text) {
            this.text = text;
            this.height = text.height();
        }

        @Override
        void draw(Env e, int x, int y) {
            text.draw(e, x, y);
        }
    }

    private static final class Heading extends Box {
        final RichText text;
        final float scale;
        final boolean rule;

        Heading(RichText text, float scale, boolean rule) {
            this.text = text;
            this.scale = scale;
            this.rule = rule;
            this.height = Math.round(text.height() * scale) + (rule ? 4 : 0) + 4;
            this.gap = 4;
        }

        @Override
        void draw(Env e, int x, int y) {
            e.g.pose().pushMatrix();
            e.g.pose().translate(x, y + 4);
            e.g.pose().scale(scale, scale);
            text.draw(new Env(e.g, e.font, -1000, -1000, -100000, 100000, null), 0, 0);
            e.g.pose().popMatrix();
            if (rule) {
                int ry = y + 4 + Math.round(text.height() * scale) + 1;
                e.g.fill(x, ry, x + Math.round(text.width * scale), ry + 1, Theme.BORDER);
                e.g.fill(x, ry, x + 24, ry + 1, Theme.ACCENT);
            }
        }
    }

    private static final class Rule extends Box {
        final int width;

        Rule(int width) {
            this.width = width;
            this.height = 1;
            this.gap = 7;
        }

        @Override
        void draw(Env e, int x, int y) {
            e.g.fill(x, y, x + width, y + 1, Theme.BORDER);
        }
    }

    private static final class Bullets extends Box {
        final List<Integer> ys = new ArrayList<>();
        final List<RichText> texts = new ArrayList<>();
        final List<Integer> indents = new ArrayList<>();
        final List<String> marks = new ArrayList<>();

        void add(RichText text, int indent, String mark, int y) {
            texts.add(text);
            indents.add(indent);
            marks.add(mark);
            ys.add(y);
        }

        @Override
        void draw(Env e, int x, int y) {
            for (int i = 0; i < texts.size(); i++) {
                int iy = y + ys.get(i);
                if (iy + texts.get(i).height() < e.clipTop || iy > e.clipBottom) {
                    continue;
                }
                int ix = x + indents.get(i);
                e.g.text(e.font, marks.get(i), ix - e.font.width(marks.get(i)) - 3, iy, Theme.ACCENT, false);
                texts.get(i).draw(e, ix, iy);
            }
        }
    }

    private static final class Table extends Box {
        final int[] widths;
        final List<List<RichText>> cells = new ArrayList<>();
        final List<Integer> rowHeights = new ArrayList<>();
        final int total;

        Table(int[] widths, int total) {
            this.widths = widths;
            this.total = total;
            this.gap = 7;
        }

        @Override
        void draw(Env e, int x, int y) {
            GuiGraphicsExtractor g = e.g;
            int ry = y;
            for (int r = 0; r < cells.size(); r++) {
                int rh = rowHeights.get(r);
                if (ry + rh >= e.clipTop && ry <= e.clipBottom) {
                    if (r == 0) {
                        g.fill(x, ry, x + total, ry + rh, Draw.mix(Theme.CARD, Theme.ACCENT, 0.12f));
                        g.fill(x, ry + rh - 1, x + total, ry + rh, Draw.withAlpha(Theme.ACCENT, 0.6f));
                    } else {
                        g.fill(x, ry, x + total, ry + rh, r % 2 == 0 ? Theme.CARD : Draw.withAlpha(Theme.CARD, 0.45f));
                    }
                    int cx = x;
                    for (int c = 0; c < widths.length; c++) {
                        if (c < cells.get(r).size()) {
                            cells.get(r).get(c).draw(e, cx + 3, ry + 3);
                        }
                        cx += widths[c];
                    }
                }
                ry += rh;
            }
            Draw.outline(g, x, y, total, height, Theme.CARD_BORDER);
        }
    }

    private static final class Callout extends Box {
        final int color;
        final String label;
        final List<Box> children = new ArrayList<>();
        final List<Integer> offsets = new ArrayList<>();
        final int width;

        Callout(int color, String label, int width) {
            this.color = color;
            this.label = label;
            this.width = width;
            this.gap = 7;
        }

        @Override
        void draw(Env e, int x, int y) {
            GuiGraphicsExtractor g = e.g;
            Draw.panel(g, x, y, width, height, Draw.mix(Theme.WINDOW | 0xFF000000, color, 0.10f), Draw.mix(Theme.WINDOW | 0xFF000000, color, 0.40f));
            g.fill(x, y + 1, x + 3, y + height - 1, color);
            g.text(e.font, Component.literal(label).withStyle(Style.EMPTY.withBold(true)), x + 10, y + 5, color, false);
            for (int i = 0; i < children.size(); i++) {
                children.get(i).render(e, x + 10, y + 17 + offsets.get(i));
            }
        }
    }

    private static final class Card extends Box {
        final int width;
        final String model;
        final String name;
        final String rarity;
        final int color;
        final String anchor;
        final List<RichText> lines = new ArrayList<>();
        final List<Integer> offsets = new ArrayList<>();
        /** A bare card is just the tooltip lines (an item page already shows the name and picture in its hero). */
        boolean bare;
        /** The item's own page, when the card is a listing entry. */
        String link;

        Card(int width, String model, String name, String rarity, int color, String anchor) {
            this.width = width;
            this.model = model;
            this.name = name;
            this.rarity = rarity;
            this.color = color;
            this.anchor = anchor;
            this.gap = 6;
        }

        @Override
        void draw(Env e, int x, int y) {
            GuiGraphicsExtractor g = e.g;
            boolean marked = anchor != null && anchor.equals(e.highlight);
            boolean over = link != null && e.over(x, y, width, height);
            Draw.panel(g, x, y, width, height, 0xF70D1017, marked || over ? Theme.ACCENT : Draw.mix(Theme.BORDER, color, 0.5f));
            g.fill(x + 2, y, x + width - 2, y + 1, Draw.withAlpha(color, 0.9f));
            if (marked) {
                Draw.outline(g, x - 1, y - 1, width + 2, height + 2, Draw.withAlpha(Theme.ACCENT, 0.5f));
            }
            int tx = x + 38;
            if (bare) {
                tx = x + 8;
            } else {
                Draw.panel(g, x + 6, y + 7, 26, 26, Draw.mix(Theme.INPUT, color, 0.12f), Draw.mix(Theme.CARD_BORDER, color, 0.4f));
                if (!model.isEmpty()) {
                    Draw.item(g, stack(model), x + 9, y + 10, 1.25f);
                }
            }
            Component title = Component.literal(name).withStyle(Style.EMPTY.withBold(true).withColor(color & 0xFFFFFF));
            if (!bare) {
                g.text(e.font, title, tx, y + 7, color, false);
            }
            if (!bare && rarity != null && !rarity.isEmpty()) {
                String label = rarity.toUpperCase(Locale.ROOT);
                int pill = e.font.width(label) + 8;
                int px = x + width - pill - 6;
                if (px > tx + e.font.width(title) + 4) {
                    Draw.rounded(g, px, y + 6, pill, 11, Draw.withAlpha(color, 0.22f));
                    g.text(e.font, label, px + 4, y + 8, color, false);
                } else {
                    g.text(e.font, label, tx, y + 18, color, false);
                }
            }
            for (int i = 0; i < lines.size(); i++) {
                lines.get(i).draw(e, tx, y + offsets.get(i));
            }
            if (link != null) {
                // added after the lines so links inside the card win the click
                Target target = new Target(link, null, null, name, color);
                e.hits.add(new Hit(x, y, width, height, target));
                if (over && e.hovered == null) {
                    e.hovered = target;
                }
            }
        }
    }

    /** The top of an item page: the item drawn big (so 3D models show in 3D), its name in the rarity colour, rarity and kind. */
    /**
     * A recipe: the station's name, a slot per ingredient (icon and count), an arrow and the result. Slots open the item's
     * page. With {@code grid} (crafting table recipes) the slots are that many to a row, and null ones are empty slots.
     */
    private static final class Recipe extends Box {
        static final int SLOT = 22;
        static final int OUT = 28;
        final String station;
        final List<Span> in;
        final List<Integer> counts;
        final Span out;
        final int outCount;
        final int perRow;
        final int width;

        Recipe(String station, List<Span> in, List<Integer> counts, Span out, int outCount, int grid, int width) {
            this.station = station;
            this.in = in;
            this.counts = counts;
            this.out = out;
            this.outCount = outCount;
            this.perRow = grid > 0 ? grid : Math.max(1, Math.min(6, (width - 24 - OUT - 30) / (SLOT + 2)));
            int rows = Math.max(1, (in.size() + perRow - 1) / perRow);
            this.width = 12 + perRow * (SLOT + 2) + 30 + OUT + 12;
            this.height = 14 + Math.max(OUT, rows * (SLOT + 2)) + 12;
            this.gap = 8;
        }

        @Override
        void draw(Env e, int x, int y) {
            GuiGraphicsExtractor g = e.g;
            Draw.panel(g, x, y, width, height, 0xF70D1017, Theme.BORDER);
            g.text(e.font, station, x + 10, y + 5, Theme.MUTED, false);
            int top = y + 16;
            int rows = Math.max(1, (in.size() + perRow - 1) / perRow);
            int gridHeight = rows * (SLOT + 2);
            for (int i = 0; i < in.size(); i++) {
                slot(e, in.get(i), counts.get(i), x + 10 + (i % perRow) * (SLOT + 2), top + (i / perRow) * (SLOT + 2), SLOT);
            }
            int ax = x + 10 + perRow * (SLOT + 2) + 8;
            int mid = top + Math.max(gridHeight, OUT) / 2;
            g.text(e.font, "→", ax + 2, mid - 4, Theme.MUTED, false);
            slot(e, out, outCount, ax + 20, mid - OUT / 2, OUT);
        }

        private static void slot(Env e, Span item, int count, int x, int y, int size) {
            if (item == null) {
                Draw.rounded(e.g, x, y, size, size, Draw.withAlpha(Theme.INPUT, 0.5f));
                return;
            }
            Target target = item.target();
            boolean hover = target != null && e.over(x, y, size, size);
            Draw.rounded(e.g, x, y, size, size, hover ? Theme.CARD_HOVER : Theme.INPUT);
            if (item.icon() != null) {
                float scale = (size - 6) / 16f;
                Draw.item(e.g, stack(item.icon()), x + 3, y + 3, scale);
            }
            if (count != 1) {
                String text = Integer.toString(count);
                e.g.text(e.font, text, x + size - 1 - e.font.width(text), y + size - 9, Theme.TEXT, true);
            }
            if (target != null) {
                e.hits.add(new Hit(x, y, size, size, target));
                if (hover) {
                    e.hovered = target;
                }
            }
        }
    }

    private static Box recipe(JsonObject block, int width) {
        List<Span> in = new ArrayList<>();
        List<Integer> counts = new ArrayList<>();
        for (JsonElement element : WikiData.array(block, "in")) {
            if (element.isJsonObject() && !element.getAsJsonObject().has("item")) {
                in.add(null);
                counts.add(1);
            } else if (element.isJsonObject()) {
                in.addAll(spans(single(element)));
                counts.add(element.getAsJsonObject().has("n") ? element.getAsJsonObject().get("n").getAsInt() : 1);
            }
        }
        JsonObject out = block.getAsJsonObject("out");
        return new Recipe(WikiData.string(block, "station", "Recipe"), in, counts, spans(single(out)).getFirst(),
                out.has("n") ? out.get("n").getAsInt() : 1, block.has("grid") ? block.get("grid").getAsInt() : 0, width);
    }

    private static JsonArray single(JsonElement element) {
        JsonArray array = new JsonArray();
        array.add(element);
        return array;
    }

    private static final class Hero extends Box {
        static final int ART = 80;
        static final float NAME_SCALE = 1.5f;
        final int width;
        final String model;
        final RichText name;
        final String rarity;
        final String kind;
        final int color;

        Hero(int width, String model, RichText name, String rarity, String kind, int color) {
            this.width = width;
            this.model = model;
            this.name = name;
            this.rarity = rarity;
            this.kind = kind;
            this.color = color;
            this.gap = 8;
            this.height = Math.max(ART + 16, 14 + Math.round(name.height() * NAME_SCALE) + 8 + 13 + 10);
        }

        @Override
        void draw(Env e, int x, int y) {
            GuiGraphicsExtractor g = e.g;
            Draw.panel(g, x, y, width, height, 0xF70D1017, Draw.mix(Theme.BORDER, color, 0.5f));
            g.fill(x + 2, y, x + width - 2, y + 1, Draw.withAlpha(color, 0.9f));
            int ax = x + 8;
            int ay = y + (height - ART) / 2;
            // a soft glow in the rarity colour: rounded squares, each a little smaller and stronger
            for (int i = 0; i < 7; i++) {
                int inset = i * 5;
                Draw.rounded(g, ax + inset / 2, ay + inset / 2, ART - inset, ART - inset, Draw.withAlpha(color, 0.02f + 0.017f * i));
            }
            if (!model.isEmpty()) {
                Draw.item(g, stack(model), ax + (ART - 64) / 2, ay + (ART - 64) / 2, 4f);
            }
            int tx = x + ART + 20;
            int ty = y + 12;
            g.pose().pushMatrix();
            g.pose().translate(tx, ty);
            g.pose().scale(NAME_SCALE, NAME_SCALE);
            name.draw(new Env(g, e.font, -1000, -1000, -100000, 100000, null), 0, 0);
            g.pose().popMatrix();
            int py = ty + Math.round(name.height() * NAME_SCALE) + 6;
            int px = tx;
            if (rarity != null && !rarity.isEmpty()) {
                String label = rarity.toUpperCase(Locale.ROOT);
                int pill = e.font.width(label) + 10;
                Draw.rounded(g, px, py, pill, 12, Draw.withAlpha(color, 0.22f));
                g.text(e.font, Component.literal(label).withStyle(Style.EMPTY.withBold(true)), px + 5, py + 2, color, false);
                px += pill + 5;
            }
            if (kind != null && !kind.isEmpty()) {
                int pill = e.font.width(kind) + 10;
                Draw.rounded(g, px, py, pill, 12, Theme.INPUT);
                g.text(e.font, kind, px + 5, py + 2, Theme.MUTED, false);
            }
        }
    }

    private static final class Caption extends Box {
        final RichText text;

        Caption(RichText text) {
            this.text = text;
            this.height = text.height() + 6;
        }

        @Override
        void draw(Env e, int x, int y) {
            Draw.panel(e.g, x, y, text.width + 12, height, Theme.INPUT, Theme.CARD_BORDER);
            text.draw(e, x + 6, y + 3);
        }
    }

    // ------------------------------------------------------------------ building

    static Doc build(Font font, JsonObject page, int width) {
        return buildBlocks(font, WikiData.array(page, "blocks"), width);
    }

    /** A doc from blocks (a page's, or ones built in code for the home page and notices). */
    static Doc buildBlocks(Font font, JsonArray blocks, int width) {
        List<Box> boxes = new ArrayList<>();
        List<String> anchors = new ArrayList<>();
        addBlocks(font, blocks, width, boxes, anchors);
        List<Integer> offsets = new ArrayList<>();
        Map<String, Integer> anchorMap = new HashMap<>();
        int y = 0;
        for (int i = 0; i < boxes.size(); i++) {
            offsets.add(y);
            if (anchors.get(i) != null) {
                anchorMap.putIfAbsent(anchors.get(i), y);
            }
            y += boxes.get(i).height + boxes.get(i).gap;
        }
        return new Doc(boxes, offsets, y, anchorMap);
    }

    private static void addBlocks(Font font, JsonArray blocks, int width, List<Box> out, List<String> anchors) {
        for (JsonElement element : blocks) {
            if (!element.isJsonObject()) {
                continue;
            }
            JsonObject block = element.getAsJsonObject();
            Box box;
            String anchor = null;
            try {
                switch (WikiData.string(block, "t", "")) {
                    case "h1" -> {
                        box = new Heading(RichText.layout(font, List.of(Span.plain(WikiData.string(block, "text", ""))), Math.round(width / 1.6f),
                                Theme.TEXT, true, 'l'), 1.6f, true);
                        box.gap = 6;
                    }
                    case "h2" -> {
                        box = new Heading(RichText.layout(font, List.of(Span.plain(WikiData.string(block, "text", ""))), Math.round(width / 1.25f),
                                Theme.TEXT, true, 'l'), 1.25f, true);
                        box.gap = 6;
                        anchor = WikiData.string(block, "anchor", null);
                    }
                    case "h3" -> {
                        box = new Heading(RichText.layout(font, List.of(Span.plain(WikiData.string(block, "text", ""))), width, Theme.ACCENT, true, 'l'),
                                1f, false);
                        anchor = WikiData.string(block, "anchor", null);
                    }
                    case "p" -> box = new Text(RichText.layout(font, spans(WikiData.array(block, "s")), width, Theme.TEXT, false, 'l'));
                    case "ul", "ol" -> box = list(font, block, width);
                    case "table" -> box = table(font, block, width);
                    case "callout" -> box = callout(font, block, width);
                    case "hero" -> box = hero(font, block, width);
                    case "recipe" -> box = recipe(block, width);
                    case "item" -> {
                        box = card(font, block, width);
                        anchor = WikiData.string(block, "anchor", null);
                    }
                    case "hr" -> box = new Rule(width);
                    case "img" -> box = new Caption(RichText.layout(font,
                            List.of(new Span("Image: " + WikiData.string(block, "caption", ""), false, true, false, Theme.MUTED, null, null, null, null)),
                            Math.max(40, width - 12), Theme.MUTED, false, 'l'));
                    default -> {
                        continue;
                    }
                }
            } catch (RuntimeException ex) {
                SkyRpgWikiClient.LOGGER.warn("Skipped a broken wiki block: {}", ex.toString());
                continue;
            }
            out.add(box);
            anchors.add(anchor);
        }
    }

    private static Box list(Font font, JsonObject block, int width) {
        boolean ordered = "ol".equals(WikiData.string(block, "t", ""));
        Bullets bullets = new Bullets();
        int y = 0;
        int n = 1;
        for (JsonElement item : WikiData.array(block, "items")) {
            if (!item.isJsonObject()) {
                continue;
            }
            JsonObject o = item.getAsJsonObject();
            RichText text = RichText.layout(font, spans(WikiData.array(o, "s")), width - 14, Theme.TEXT, false, 'l');
            bullets.add(text, 14, ordered ? (n++) + "." : "•", y);
            y += text.height() + 2;
            int sub = 0;
            for (JsonElement s : WikiData.array(o, "sub")) {
                if (s.isJsonObject()) {
                    RichText st = RichText.layout(font, spans(WikiData.array(s.getAsJsonObject(), "s")), width - 28, Theme.TEXT, false, 'l');
                    bullets.add(st, 28, ordered ? (char) ('a' + sub % 26) + "." : "–", y);
                    sub++;
                    y += st.height() + 2;
                }
            }
        }
        bullets.height = Math.max(0, y - 2);
        return bullets;
    }

    private static Box table(Font font, JsonObject block, int width) {
        List<List<List<Span>>> rows = new ArrayList<>();
        List<List<Span>> head = new ArrayList<>();
        for (JsonElement cell : WikiData.array(block, "head")) {
            head.add(spans(cell.isJsonArray() ? cell.getAsJsonArray() : null));
        }
        rows.add(head);
        for (JsonElement row : WikiData.array(block, "rows")) {
            List<List<Span>> r = new ArrayList<>();
            if (row.isJsonArray()) {
                for (JsonElement cell : row.getAsJsonArray()) {
                    r.add(spans(cell.isJsonArray() ? cell.getAsJsonArray() : null));
                }
            }
            rows.add(r);
        }
        int cols = 0;
        for (List<List<Span>> r : rows) {
            cols = Math.max(cols, r.size());
        }
        if (cols == 0) {
            return new Rule(0);
        }
        char[] align = new char[cols];
        JsonArray al = WikiData.array(block, "align");
        for (int c = 0; c < cols; c++) {
            String a = c < al.size() && al.get(c).isJsonPrimitive() ? al.get(c).getAsString() : "l";
            align[c] = a.isEmpty() ? 'l' : a.charAt(0);
        }
        final int pad = 6;
        int[] natural = new int[cols];
        int[] min = new int[cols];
        for (int r = 0; r < rows.size(); r++) {
            for (int c = 0; c < rows.get(r).size(); c++) {
                int[] m = RichText.measure(font, rows.get(r).get(c), r == 0);
                natural[c] = Math.max(natural[c], m[0]);
                min[c] = Math.max(min[c], m[1]);
            }
        }
        int sumNatural = 0;
        int sumMin = 0;
        for (int c = 0; c < cols; c++) {
            min[c] = Math.min(min[c], natural[c]);
            sumNatural += natural[c] + pad;
            sumMin += min[c] + pad;
        }
        int[] widths = new int[cols];
        if (sumNatural <= width) {
            int extra = width - sumNatural;
            int sumN = Math.max(1, sumNatural - pad * cols);
            int used = 0;
            for (int c = 0; c < cols; c++) {
                widths[c] = natural[c] + pad + extra * natural[c] / sumN;
                used += widths[c];
            }
            widths[cols - 1] += width - used;
        } else if (sumMin >= width) {
            int used = 0;
            for (int c = 0; c < cols; c++) {
                widths[c] = Math.max(24, (min[c] + pad) * width / sumMin);
                used += widths[c];
            }
            widths[cols - 1] = Math.max(24, widths[cols - 1] + width - used);
        } else {
            int extra = width - sumMin;
            int want = 0;
            for (int c = 0; c < cols; c++) {
                want += natural[c] - min[c];
            }
            int used = 0;
            for (int c = 0; c < cols; c++) {
                widths[c] = min[c] + pad + (want == 0 ? 0 : extra * (natural[c] - min[c]) / want);
                used += widths[c];
            }
            widths[cols - 1] += width - used;
        }
        int total = 0;
        for (int w : widths) {
            total += w;
        }
        Table table = new Table(widths, total);
        int height = 0;
        for (int r = 0; r < rows.size(); r++) {
            List<RichText> laid = new ArrayList<>();
            int rh = 0;
            for (int c = 0; c < rows.get(r).size(); c++) {
                RichText rt = RichText.layout(font, rows.get(r).get(c), widths[c] - pad, Theme.TEXT, r == 0, align[c]);
                laid.add(rt);
                rh = Math.max(rh, rt.height());
            }
            rh += 6;
            table.cells.add(laid);
            table.rowHeights.add(rh);
            height += rh;
        }
        table.height = height;
        return table;
    }

    private static Box callout(Font font, JsonObject block, int width) {
        String kind = WikiData.string(block, "kind", "note");
        int color = switch (kind) {
            case "tip" -> Theme.GREEN;
            case "warning" -> WARNING;
            default -> Theme.BLUE;
        };
        Callout callout = new Callout(color, kind.toUpperCase(Locale.ROOT), width);
        List<Box> inner = new ArrayList<>();
        addBlocks(font, WikiData.array(block, "b"), width - 20, inner, new ArrayList<>());
        int y = 0;
        for (int i = 0; i < inner.size(); i++) {
            Box box = inner.get(i);
            callout.children.add(box);
            callout.offsets.add(y);
            y += box.height + (i == inner.size() - 1 ? 0 : Math.min(box.gap, 4));
        }
        callout.height = 17 + y + 7;
        return callout;
    }

    private static Box hero(Font font, JsonObject block, int width) {
        int color = color(WikiData.string(block, "color", ""));
        if (color == 0) {
            color = Theme.MUTED;
        }
        int textWidth = Math.max(40, Math.round((width - Hero.ART - 32) / Hero.NAME_SCALE));
        RichText name = RichText.layout(font, List.of(new Span(WikiData.string(block, "name", WikiData.string(block, "id", "?")), true, false, false, color,
                null, null, null, null)), textWidth, color, true, 'l');
        return new Hero(width, WikiData.string(block, "icon", ""), name, WikiData.string(block, "rarity", ""), WikiData.string(block, "kind", ""), color);
    }

    private static Box card(Font font, JsonObject block, int width) {
        int cardW = Math.min(width, 320);
        int color = color(WikiData.string(block, "color", ""));
        if (color == 0) {
            color = Theme.MUTED;
        }
        Card card = new Card(cardW, WikiData.string(block, "icon", ""), WikiData.string(block, "name", WikiData.string(block, "id", "?")),
                WikiData.string(block, "rarity", ""), color, WikiData.string(block, "anchor", null));
        String link = WikiData.string(block, "link", "");
        card.link = link.isEmpty() ? null : link;
        card.bare = block.has("bare") && block.get("bare").isJsonPrimitive() && block.get("bare").getAsBoolean();
        int y = card.bare ? 6 : 21;
        int textW = card.bare ? cardW - 16 : cardW - 38 - 8;
        for (JsonElement line : WikiData.array(block, "lines")) {
            if (!line.isJsonArray()) {
                continue;
            }
            RichText rt = RichText.layout(font, spans(line.getAsJsonArray()), textW, Theme.MUTED, false, 'l');
            card.lines.add(rt);
            card.offsets.add(y);
            y += rt.height() + 1;
        }
        card.height = Math.max(card.bare ? 20 : 40, y + 6);
        return card;
    }
}
