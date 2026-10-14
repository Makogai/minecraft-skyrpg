package com.skyrpg.core.client.menu;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.platform.cursor.CursorTypes;
import com.skyrpg.core.SkyRpgCore;
import com.skyrpg.core.client.config.Setting;
import com.skyrpg.core.client.config.SettingCategory;
import com.skyrpg.core.client.config.SkyRpgSettings;
import com.skyrpg.core.client.ui.Draw;
import com.skyrpg.core.client.ui.Theme;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.util.Util;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Every registered {@link Setting}: a sidebar of categories (each with its icon) with a search box on top, and the
 * picked category's settings on the right as cards, under section headings where settings name one. Only the right
 * pane scrolls (smoothly). Typing in the search box looks through every category at once.
 *
 * <p>Keyboard: Tab moves between the search box, the categories and the settings (Shift+Tab back); the arrows
 * move inside the focused pane, Enter/Space change a setting and Left/Right adjust choices and sliders; "/" or
 * Ctrl+F jumps to the search; Esc steps back (clears the search, leaves the pane) before it closes the menu.
 */
public final class SettingsPage implements MenuPage {
    private static final int SIDE_MIN = 96;
    private static final int SIDE_MAX = 150;
    private static final int GAP = 10;
    private static final int CATEGORY_ROW = 20;
    private static final int CATEGORY_ROW_MIN = 16;
    private static final int CATEGORY_GAP = 1;
    /** The right pane's title line (icon, name, count) above the list. */
    private static final int PANE_HEADER = 22;
    private static final int ROW_GAP = 4;
    private static final int SECTION_HEADER = 16;
    private static final int SECTION_GAP = 6;
    private static final int GROUP_HEADER = 22;
    /** Room on the right of the list for its scrollbar. */
    private static final int SCROLL_GUTTER = 7;
    private static final float WHEEL_STEP = 32;
    private static final long RESET_CONFIRM_MILLIS = 3000;
    private static final int COLUMN_FILL = 0x66080B11;
    private static final int FADE = Theme.WINDOW;

    enum Focus { NONE, SEARCH, CATEGORIES, SETTINGS }

    /** A line of the right pane: a heading (section, or category in search results) or a setting's card. */
    private record Line(int y, int height, SettingRow row, String heading, SettingCategory group) {
        boolean isRow() {
            return row != null;
        }
    }

    private record Geometry(int sideX, int sideWidth, int searchY, int categoryTop, int categoryBottom,
                            int paneX, int paneWidth, int headerY, int listTop, int listBottom) {
        int rowWidth() {
            return paneWidth - SCROLL_GUTTER;
        }

        int listHeight() {
            return listBottom - listTop;
        }
    }

    private final SearchField search = new SearchField("Search settings");
    private final Map<String, SettingRow.Anim> rowAnims = new HashMap<>();
    private final Map<String, Float> categoryHover = new HashMap<>();
    private String selected;
    private Focus focus = Focus.NONE;
    private String focusedSetting;
    private double scroll;
    private double scrollTarget;
    private double categoryScroll;
    private double categoryScrollTarget;
    private Setting draggingSlider;
    private boolean draggingScrollbar;
    private double scrollbarGrab;
    private String resetArmedFor;
    private long resetArmedAt;
    private boolean swallowSlash;
    private long lastFrame = Util.getMillis();

    private List<Line> cachedLines = List.of();
    private String cachedKey = "";

    @Override
    public Identifier id() {
        return SkyRpgCore.id("settings");
    }

    @Override
    public Component title() {
        return Component.literal("Settings");
    }

    @Override
    public Component subtitle() {
        if (searching()) {
            return Component.literal("Searching every category");
        }
        String current = current();
        Component description = current == null ? Component.empty() : SkyRpgSettings.category(current).description();
        return description.getString().isEmpty() ? Component.literal("Make SkyRPG yours") : description;
    }

    @Override
    public ItemStack icon() {
        return new ItemStack(Items.COMPARATOR);
    }

    @Override
    public int order() {
        return 1000;
    }

    @Override
    public void opened() {
        search.clear();
        focus = Focus.NONE;
        focusedSetting = null;
        scroll = scrollTarget = 0;
        draggingSlider = null;
        draggingScrollbar = false;
        resetArmedFor = null;
    }

    @Override
    public boolean showSubpage(String name) {
        String request = name.trim();
        String lower = request.toLowerCase(Locale.ROOT);
        if (lower.startsWith("search-") || lower.startsWith("search:")) {
            search.set(request.substring(7).replace('_', ' ').replace('-', ' '));
            focus = Focus.NONE;
            scroll = scrollTarget = 0;
            return true;
        }
        String slug = SettingCategory.slug(request);
        for (SettingCategory category : SkyRpgSettings.categories()) {
            if (category.slug().equals(slug) || category.name().equalsIgnoreCase(request)) {
                selectCategory(category.name(), false);
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean canGoBack() {
        return focus != Focus.NONE || searching();
    }

    @Override
    public Component hint() {
        return Component.literal(switch (focus) {
            case NONE -> "[Tab] keyboard  [/] search";
            case SEARCH -> "[Enter] results  [Tab] categories";
            case CATEGORIES -> "[↑↓] category  [Tab] settings";
            case SETTINGS -> "[↑↓] setting  [Enter] change  [←→] adjust";
        });
    }

    // ------------------------------------------------------------------ state

    private boolean searching() {
        return !search.isEmpty();
    }

    private String[] queryWords() {
        return search.text().toLowerCase(Locale.ROOT).trim().split("\\s+");
    }

    /** The selected category, or the first one if none is (or it's gone). */
    private String current() {
        Map<String, List<Setting>> grouped = SkyRpgSettings.byCategory();
        if (selected == null || !grouped.containsKey(selected)) {
            selected = grouped.isEmpty() ? null : grouped.keySet().iterator().next();
        }
        return selected;
    }

    private void selectCategory(String name, boolean sound) {
        boolean changed = !name.equals(selected) || searching();
        selected = name;
        search.clear();
        if (changed) {
            scroll = scrollTarget = 0;
            focusedSetting = null;
            resetArmedFor = null;
        }
        if (sound && changed) {
            click(1.5f);
        }
    }

    private static boolean matches(Setting setting, String[] words) {
        String haystack = (setting.label().getString() + " " + setting.description().getString() + " " + setting.category() + " "
                + (setting.section() == null ? "" : setting.section()) + " " + String.join(" ", setting.options()) + " "
                + String.join(" ", setting.keywords()) + " "
                + (setting.buttonLabel() == null ? "" : setting.buttonLabel().getString())).toLowerCase(Locale.ROOT);
        for (String word : words) {
            if (!haystack.contains(word)) {
                return false;
            }
        }
        return true;
    }

    private int matchCount(List<Setting> settings, String[] words) {
        int count = 0;
        for (Setting setting : settings) {
            if (matches(setting, words)) {
                count++;
            }
        }
        return count;
    }

    // ------------------------------------------------------------------ layout

    private static Geometry geometry(PageContext ctx) {
        int sideWidth = Mth.clamp(Math.round(ctx.width() * 0.28f), SIDE_MIN, SIDE_MAX);
        int paneX = ctx.x() + sideWidth + GAP;
        return new Geometry(ctx.x(), sideWidth, ctx.y(), ctx.y() + PANE_HEADER, ctx.y() + ctx.height(),
                paneX, ctx.width() - sideWidth - GAP, ctx.y(), ctx.y() + PANE_HEADER, ctx.y() + ctx.height());
    }

    /** The right pane's lines for the current category or search, measured for {@code rowWidth} (cached). */
    private List<Line> lines(Font font, int rowWidth) {
        Map<String, List<Setting>> grouped = SkyRpgSettings.byCategory();
        int total = 0;
        for (List<Setting> settings : grouped.values()) {
            total += settings.size();
        }
        String key = rowWidth + "|" + total + "|" + (searching() ? "?" + search.text().toLowerCase(Locale.ROOT).trim() : current());
        if (key.equals(cachedKey)) {
            return cachedLines;
        }
        List<Line> lines = new ArrayList<>();
        int y = 0;
        if (searching()) {
            String[] words = queryWords();
            for (Map.Entry<String, List<Setting>> entry : grouped.entrySet()) {
                List<Setting> found = entry.getValue().stream().filter(s -> matches(s, words)).toList();
                if (found.isEmpty()) {
                    continue;
                }
                lines.add(new Line(y, GROUP_HEADER, null, entry.getKey(), SkyRpgSettings.category(entry.getKey())));
                y += GROUP_HEADER;
                for (Setting setting : found) {
                    SettingRow row = SettingRow.measure(font, setting, rowWidth);
                    lines.add(new Line(y, row.height, row, null, null));
                    y += row.height + ROW_GAP;
                }
                y += SECTION_GAP;
            }
        } else if (current() != null) {
            // Settings without a section first, then each section in the order it was first used.
            Map<String, List<Setting>> sections = new LinkedHashMap<>();
            sections.put("", new ArrayList<>());
            for (Setting setting : grouped.get(current())) {
                sections.computeIfAbsent(setting.section() == null ? "" : setting.section(), s -> new ArrayList<>()).add(setting);
            }
            for (Map.Entry<String, List<Setting>> section : sections.entrySet()) {
                if (section.getValue().isEmpty()) {
                    continue;
                }
                if (!section.getKey().isEmpty()) {
                    if (y > 0) {
                        y += SECTION_GAP;
                    }
                    lines.add(new Line(y, SECTION_HEADER, null, section.getKey(), null));
                    y += SECTION_HEADER;
                }
                for (Setting setting : section.getValue()) {
                    SettingRow row = SettingRow.measure(font, setting, rowWidth);
                    lines.add(new Line(y, row.height, row, null, null));
                    y += row.height + ROW_GAP;
                }
            }
        }
        cachedKey = key;
        cachedLines = lines;
        return lines;
    }

    private static int contentHeight(List<Line> lines) {
        if (lines.isEmpty()) {
            return 0;
        }
        Line last = lines.getLast();
        return last.y() + last.height();
    }

    private List<Setting> rows(List<Line> lines) {
        List<Setting> rows = new ArrayList<>();
        for (Line line : lines) {
            if (line.isRow()) {
                rows.add(line.row().setting);
            }
        }
        return rows;
    }

    private int categoryRowHeight(Geometry geo, int count) {
        int room = geo.categoryBottom() - geo.categoryTop() - 6;
        return Mth.clamp(room / Math.max(1, count) - CATEGORY_GAP, CATEGORY_ROW_MIN, CATEGORY_ROW);
    }

    private double maxScroll(Geometry geo, List<Line> lines) {
        return Math.max(0, contentHeight(lines) - geo.listHeight());
    }

    // ------------------------------------------------------------------ drawing

    @Override
    public void render(PageContext ctx) {
        GuiGraphicsExtractor g = ctx.graphics();
        Font font = ctx.font();
        long now = Util.getMillis();
        float frameSeconds = Math.min((now - lastFrame) / 1000f, 0.1f);
        lastFrame = now;
        if (resetArmedFor != null && now - resetArmedAt > RESET_CONFIRM_MILLIS) {
            resetArmedFor = null;
        }

        Geometry geo = geometry(ctx);
        List<Line> lines = lines(font, geo.rowWidth());
        double max = maxScroll(geo, lines);
        scrollTarget = Mth.clamp(scrollTarget, 0, max);
        scroll = approach(scroll, scrollTarget, frameSeconds);

        boolean searchHovered = ctx.hovered(geo.sideX(), geo.searchY(), geo.sideWidth(), SearchField.HEIGHT);
        search.draw(g, font, geo.sideX(), geo.searchY(), geo.sideWidth(), focus == Focus.SEARCH, searchHovered, ctx.mouseX());
        if (searchHovered) {
            g.requestCursor(!search.isEmpty() && SearchField.overClear(geo.sideX(), geo.sideWidth(), ctx.mouseX())
                    ? CursorTypes.POINTING_HAND : CursorTypes.IBEAM);
        }
        drawCategories(ctx, geo, frameSeconds);
        drawPaneHeader(ctx, geo, lines);
        drawList(ctx, geo, lines, max, frameSeconds);
    }

    private static double approach(double value, double target, float frameSeconds) {
        double next = value + (target - value) * Math.min(1, frameSeconds * 16);
        return Math.abs(target - next) < 0.3 ? target : next;
    }

    private void drawCategories(PageContext ctx, Geometry geo, float frameSeconds) {
        GuiGraphicsExtractor g = ctx.graphics();
        Font font = ctx.font();
        Map<String, List<Setting>> grouped = SkyRpgSettings.byCategory();
        List<SettingCategory> categories = SkyRpgSettings.categories();
        int top = geo.categoryTop();
        int bottom = geo.categoryBottom();
        Draw.panel(g, geo.sideX(), top, geo.sideWidth(), bottom - top, COLUMN_FILL, Theme.CARD_BORDER);

        int rowHeight = categoryRowHeight(geo, categories.size());
        int content = categories.size() * (rowHeight + CATEGORY_GAP) - CATEGORY_GAP;
        int room = bottom - top - 6;
        categoryScrollTarget = Mth.clamp(categoryScrollTarget, 0, Math.max(0, content - room));
        categoryScroll = approach(categoryScroll, categoryScrollTarget, frameSeconds);
        boolean searching = searching();
        String[] words = searching ? queryWords() : null;
        String current = current();
        boolean mouseInColumn = ctx.hovered(geo.sideX(), top + 3, geo.sideWidth(), room);

        g.enableScissor(geo.sideX() + 1, top + 1, geo.sideX() + geo.sideWidth() - 1, bottom - 1);
        int y = top + 3 - (int) Math.round(categoryScroll);
        for (SettingCategory category : categories) {
            int x = geo.sideX() + 3;
            int w = geo.sideWidth() - 6;
            boolean active = !searching && category.name().equals(current);
            boolean hovered = mouseInColumn && ctx.hovered(x, y, w, rowHeight);
            float hover = categoryHover.getOrDefault(category.name(), 0f);
            hover = Mth.lerp(Math.min(1, frameSeconds * 14), hover, hovered ? 1 : 0);
            categoryHover.put(category.name(), hover);
            List<Setting> settings = grouped.getOrDefault(category.name(), List.of());
            int found = searching ? matchCount(settings, words) : -1;

            if (active) {
                Draw.rounded(g, x, y, w, rowHeight, Theme.ACCENT_SOFT);
                g.fill(x, y + 4, x + 2, y + rowHeight - 4, Theme.ACCENT);
            } else if (hover > 0.02f) {
                Draw.rounded(g, x, y, w, rowHeight, Draw.withAlpha(Theme.HOVER, hover));
            }
            if (focus == Focus.CATEGORIES && active) {
                Draw.outline(g, x, y, w, rowHeight, Theme.ACCENT);
            }
            if (hovered && !active) {
                g.requestCursor(CursorTypes.POINTING_HAND);
            }
            int nudge = Math.round(hover * 1.5f);
            drawCategoryIcon(g, font, category, x + 4 + nudge, y + (rowHeight - 16) / 2);

            String count = searching ? (found > 0 ? Integer.toString(found) : "") : Integer.toString(settings.size());
            int countWidth = count.isEmpty() ? 0 : font.width(count) + 6;
            int color = active ? Theme.TEXT : searching && found == 0 ? Theme.DIM : Draw.mix(Theme.MUTED, Theme.TEXT, hover);
            if (Draw.fitText(g, font, category.name(), x + 24 + nudge, y + (rowHeight - 8) / 2, w - 24 - 4 - countWidth - nudge, color)
                    && hovered) {
                Draw.queueTooltip(List.of(Component.literal(category.name()).withColor(Theme.TEXT)), Theme.ACCENT);
            }
            if (!count.isEmpty()) {
                int countColor = searching ? Theme.ACCENT : Draw.withAlpha(Theme.DIM, active ? 1f : 0.4f + 0.6f * hover);
                Draw.rightText(g, font, count, x + w - 4, y + (rowHeight - 8) / 2, countColor);
            }
            y += rowHeight + CATEGORY_GAP;
        }
        g.disableScissor();
        if (content > room) {
            Draw.scrollbar(g, geo.sideX() + geo.sideWidth() - 3, top + 3, room, content, categoryScroll, Theme.DIM);
        }
    }

    /** A category's icon: its item, its texture, or a badge with its first letter in a colour from its name. */
    static void drawCategoryIcon(GuiGraphicsExtractor g, Font font, SettingCategory category, int x, int y) {
        ItemStack stack = category.icon();
        if (stack != null && !stack.isEmpty()) {
            g.item(stack, x, y);
        } else if (category.texture() != null) {
            g.blit(RenderPipelines.GUI_TEXTURED, category.texture(), x, y, 0, 0, 16, 16, 16, 16);
        } else {
            int[] palette = {Theme.ACCENT, Theme.AQUA, Theme.GREEN, Theme.PURPLE, Theme.BLUE, Theme.RED};
            int color = palette[Math.floorMod(category.name().hashCode(), palette.length)];
            Draw.panel(g, x + 1, y + 1, 14, 14, Draw.withAlpha(color, 0.18f), Draw.withAlpha(color, 0.7f));
            String letter = category.name().isEmpty() ? "?" : category.name().substring(0, 1).toUpperCase(Locale.ROOT);
            Draw.text(g, font, letter, x + 8 - font.width(letter) / 2, y + 4, color);
        }
    }

    private void drawPaneHeader(PageContext ctx, Geometry geo, List<Line> lines) {
        GuiGraphicsExtractor g = ctx.graphics();
        Font font = ctx.font();
        int x = geo.paneX();
        int y = geo.headerY();
        int right = x + geo.rowWidth();
        int textY = y + 4;
        if (searching()) {
            int found = rows(lines).size();
            String count = found == 1 ? "1 result" : found + " results";
            int countWidth = font.width(count);
            Draw.rightText(g, font, count, right, textY, found == 0 ? Theme.DIM : Theme.MUTED);
            Component title = Component.literal("Results for ").withColor(Theme.MUTED)
                    .append(Component.literal("\"" + search.text().trim() + "\"").withColor(Theme.ACCENT));
            Draw.fitText(g, font, title, x, textY, geo.rowWidth() - countWidth - 8, Theme.TEXT);
        } else if (current() != null) {
            SettingCategory category = SkyRpgSettings.category(current());
            drawCategoryIcon(g, font, category, x, y);
            List<Setting> settings = SkyRpgSettings.byCategory().get(current());
            int controlsWidth;
            if (anyChanged(settings)) {
                boolean armed = current().equals(resetArmedFor);
                String label = armed ? "Click again to reset" : "Reset to defaults";
                int w = font.width(label) + 10;
                int bx = right - w;
                boolean hovered = ctx.hovered(bx, y + 1, w, 14);
                Draw.panel(g, bx, y + 1, w, 14, armed ? Draw.withAlpha(Theme.RED, 0.16f) : hovered ? Theme.CARD_HOVER : Theme.TRACK,
                        armed ? Theme.RED : hovered ? Draw.mix(Theme.CARD_BORDER, Theme.ACCENT, 0.5f) : Theme.CARD_BORDER);
                Draw.text(g, font, label, bx + 5, y + 4, armed ? Theme.RED : hovered ? Theme.TEXT : Theme.MUTED);
                if (hovered) {
                    g.requestCursor(CursorTypes.POINTING_HAND);
                    if (!armed) {
                        Draw.queueTooltip(List.of(Component.literal("Put every " + current() + " setting back to its default")
                                .withColor(Theme.TEXT)), Theme.ACCENT);
                    }
                }
                controlsWidth = w + 8;
            } else {
                String count = settings.size() == 1 ? "1 setting" : settings.size() + " settings";
                Draw.rightText(g, font, count, right, textY, Theme.DIM);
                controlsWidth = font.width(count) + 8;
            }
            Draw.fitText(g, font, Component.literal(current()), x + 21, textY, geo.rowWidth() - 21 - controlsWidth, Theme.TEXT);
        }
        g.fill(x, geo.listTop() - 4, right, geo.listTop() - 3, Theme.BORDER);
    }

    private static boolean anyChanged(List<Setting> settings) {
        for (Setting setting : settings) {
            if (!setting.isDefault()) {
                return true;
            }
        }
        return false;
    }

    /** The width of the "Reset to defaults" button (right-aligned over the list), or -1 if it isn't shown. */
    private int resetButtonWidth(Font font) {
        if (searching() || current() == null || !anyChanged(SkyRpgSettings.byCategory().get(current()))) {
            return -1;
        }
        String label = current().equals(resetArmedFor) ? "Click again to reset" : "Reset to defaults";
        return font.width(label) + 10;
    }

    private void drawList(PageContext ctx, Geometry geo, List<Line> lines, double maxScroll, float frameSeconds) {
        GuiGraphicsExtractor g = ctx.graphics();
        Font font = ctx.font();
        int x = geo.paneX();
        int top = geo.listTop();
        int bottom = geo.listBottom();
        int rowWidth = geo.rowWidth();
        boolean mouseInList = ctx.mouseY() >= top && ctx.mouseY() < bottom && !draggingScrollbar;
        String highlight = searching() ? queryWords()[0] : "";

        g.enableScissor(x, top, x + geo.paneWidth(), bottom);
        int base = top - (int) Math.round(scroll);
        for (Line line : lines) {
            int y = base + line.y();
            if (y + line.height() < top || y > bottom) {
                continue;
            }
            if (!line.isRow()) {
                drawHeading(g, font, line, x, y, rowWidth);
                continue;
            }
            SettingRow row = line.row();
            Setting setting = row.setting;
            boolean dragging = draggingSlider == setting;
            boolean hovered = dragging || (draggingSlider == null && mouseInList && ctx.hovered(x, y, rowWidth, row.height));
            boolean focused = focus == Focus.SETTINGS && setting.id().equals(focusedSetting);
            SettingRow.Anim anim = rowAnims.computeIfAbsent(setting.id(), id -> new SettingRow.Anim());
            boolean cut = row.draw(g, font, x, y, ctx.mouseX(), ctx.mouseY(), hovered, focused, dragging, anim, frameSeconds, highlight);
            if (hovered && !dragging) {
                List<Component> tip = tooltip(setting, cut, ctx.mouseX() - x, ctx.mouseY() - y, row);
                if (!tip.isEmpty()) {
                    ctx.tooltip(tip, Theme.ACCENT);
                }
            }
        }
        g.disableScissor();

        // Soft edges where more of the list is hidden.
        if (scroll > 0.5) {
            g.fillGradient(x, top, x + rowWidth, top + 8, FADE, FADE & 0x00FFFFFF);
        }
        if (scroll < maxScroll - 0.5) {
            g.fillGradient(x, bottom - 8, x + rowWidth, bottom, FADE & 0x00FFFFFF, FADE);
        }
        int content = contentHeight(lines);
        if (content > geo.listHeight()) {
            drawScrollbar(ctx, geo, content);
        }
        if (lines.isEmpty()) {
            int cx = x + rowWidth / 2;
            int cy = top + Math.min(60, geo.listHeight() / 3);
            if (searching()) {
                Draw.fit(g, font, Component.literal("No settings match \"" + search.text().trim() + "\""), cx, cy, rowWidth - 8,
                        Draw.Align.CENTER, Theme.MUTED);
                Draw.fit(g, font, Component.literal("Try another word, or Esc to clear"), cx, cy + 12, rowWidth - 8, Draw.Align.CENTER, Theme.DIM);
            } else {
                Draw.fit(g, font, Component.literal("Nothing to set here yet"), cx, cy, rowWidth - 8, Draw.Align.CENTER, Theme.DIM);
            }
        }
    }

    private static List<Component> tooltip(Setting setting, boolean cut, int relX, int relY, SettingRow row) {
        if (cut) {
            List<Component> lines = new ArrayList<>();
            lines.add(setting.label().copy().withColor(Theme.TEXT));
            if (!setting.description().getString().isEmpty()) {
                lines.add(setting.description().copy().withColor(Theme.MUTED));
            }
            return lines;
        }
        if (setting.isSlider() && row.hit(relX, relY).kind() == SettingRow.HitKind.SLIDER) {
            return List.of(Component.literal(setting.formatted(setting.min()) + " to " + setting.formatted(setting.max())
                    + "  ·  default " + setting.formatted(setting.defaultNumber())).withColor(Theme.MUTED));
        }
        return List.of();
    }

    private void drawHeading(GuiGraphicsExtractor g, Font font, Line line, int x, int y, int rowWidth) {
        if (line.group() != null) {
            drawCategoryIcon(g, font, line.group(), x, y + 2);
            String name = line.group().name();
            Draw.text(g, font, name, x + 21, y + 6, Theme.TEXT);
            int lineX = x + 21 + font.width(name) + 6;
            if (lineX < x + rowWidth) {
                g.fill(lineX, y + 10, x + rowWidth, y + 11, Theme.BORDER);
            }
        } else {
            String text = line.heading().toUpperCase(Locale.ROOT);
            Draw.fitText(g, font, text, x + 2, y + 4, rowWidth - 30, Theme.DIM);
            int lineX = x + 2 + Math.min(font.width(text), rowWidth - 30) + 6;
            if (lineX < x + rowWidth) {
                g.fill(lineX, y + 8, x + rowWidth, y + 9, Theme.BORDER);
            }
        }
    }

    private int[] thumb(Geometry geo, int content) {
        int h = geo.listHeight();
        int thumb = Mth.clamp(h * h / Math.max(1, content), 16, h);
        double max = Math.max(1, content - h);
        int thumbY = geo.listTop() + (int) Math.round((h - thumb) * Mth.clamp(scroll / max, 0, 1));
        return new int[] {thumbY, thumb};
    }

    private void drawScrollbar(PageContext ctx, Geometry geo, int content) {
        GuiGraphicsExtractor g = ctx.graphics();
        int x = geo.paneX() + geo.paneWidth() - 3;
        int[] thumb = thumb(geo, content);
        boolean hovered = draggingScrollbar || ctx.hovered(x - 3, geo.listTop(), 9, geo.listHeight());
        g.fill(x, geo.listTop(), x + 3, geo.listBottom(), Theme.TRACK);
        Draw.rounded(g, x, thumb[0], 3, thumb[1], hovered ? Theme.MUTED : Theme.DIM);
        if (hovered) {
            g.requestCursor(CursorTypes.RESIZE_NS);
        }
    }

    // ------------------------------------------------------------------ mouse

    @Override
    public boolean mouseClicked(PageContext ctx, double mouseX, double mouseY, int button) {
        // 26.3 numbers mouse buttons from 1 (left), not 0.
        if (button != InputConstants.MOUSE_BUTTON_LEFT) {
            return false;
        }
        Font font = ctx.font();
        Geometry geo = geometry(ctx);
        List<Line> lines = lines(font, geo.rowWidth());

        // Search box.
        if (inside(mouseX, mouseY, geo.sideX(), geo.searchY(), geo.sideWidth(), SearchField.HEIGHT)) {
            if (!search.isEmpty() && SearchField.overClear(geo.sideX(), geo.sideWidth(), mouseX)) {
                search.clear();
                scroll = scrollTarget = 0;
                click(0.9f);
            }
            focus = Focus.SEARCH;
            return true;
        }
        if (focus == Focus.SEARCH) {
            focus = Focus.NONE;
        }

        // Categories.
        List<SettingCategory> categories = SkyRpgSettings.categories();
        int rowHeight = categoryRowHeight(geo, categories.size());
        int top = geo.categoryTop();
        int room = geo.categoryBottom() - top - 6;
        if (inside(mouseX, mouseY, geo.sideX(), top + 3, geo.sideWidth(), room)) {
            int y = top + 3 - (int) Math.round(categoryScroll);
            for (SettingCategory category : categories) {
                if (mouseY >= y && mouseY < y + rowHeight) {
                    focus = Focus.NONE;
                    selectCategory(category.name(), true);
                    return true;
                }
                y += rowHeight + CATEGORY_GAP;
            }
            return true;
        }

        // Scrollbar.
        int content = contentHeight(lines);
        int barX = geo.paneX() + geo.paneWidth() - 3;
        if (content > geo.listHeight() && inside(mouseX, mouseY, barX - 3, geo.listTop(), 9, geo.listHeight())) {
            int[] thumb = thumb(geo, content);
            draggingScrollbar = true;
            scrollbarGrab = mouseY >= thumb[0] && mouseY < thumb[0] + thumb[1] ? mouseY - thumb[0] : thumb[1] / 2.0;
            dragScrollbar(geo, content, mouseY);
            return true;
        }

        // "Reset to defaults".
        int resetWidth = resetButtonWidth(font);
        if (resetWidth > 0 && inside(mouseX, mouseY, geo.paneX() + geo.rowWidth() - resetWidth, geo.headerY() + 1, resetWidth, 14)) {
            if (current().equals(resetArmedFor)) {
                for (Setting setting : SkyRpgSettings.byCategory().get(current())) {
                    setting.reset();
                }
                resetArmedFor = null;
                click(0.7f);
            } else {
                resetArmedFor = current();
                resetArmedAt = Util.getMillis();
                click(1.1f);
            }
            return true;
        }

        // Setting cards.
        if (mouseY < geo.listTop() || mouseY >= geo.listBottom()) {
            return false;
        }
        int base = geo.listTop() - (int) Math.round(scroll);
        for (Line line : lines) {
            int y = base + line.y();
            if (!line.isRow() || mouseY < y || mouseY >= y + line.height() || mouseX < geo.paneX() || mouseX >= geo.paneX() + geo.rowWidth()) {
                continue;
            }
            SettingRow row = line.row();
            focus = Focus.NONE;
            focusedSetting = row.setting.id();
            SettingRow.Hit hit = row.hit(mouseX - geo.paneX(), mouseY - y);
            return use(row, hit, mouseX - geo.paneX());
        }
        return false;
    }

    /** Does what a click on a card does. */
    private boolean use(SettingRow row, SettingRow.Hit hit, double relX) {
        Setting setting = row.setting;
        switch (hit.kind()) {
            case NONE -> {
                return false;
            }
            case SEGMENT -> {
                if (hit.index() != setting.choiceIndex()) {
                    setting.setChoice(setting.options().get(hit.index()));
                    click(1.3f);
                }
            }
            case PREVIOUS -> {
                setting.cycle(-1);
                click(1.1f);
            }
            case NEXT -> {
                setting.cycle(1);
                click(1.3f);
            }
            case SLIDER -> {
                draggingSlider = setting;
                setting.previewNumber(row.sliderValueAt(relX));
            }
            case ROW -> {
                if (setting.isSlider()) {
                    return false;
                }
                click(setting.isToggle() ? (setting.get() ? 0.9f : 1.3f) : 1.2f);
                setting.activate();
            }
        }
        return true;
    }

    @Override
    public boolean mouseDragged(PageContext ctx, double mouseX, double mouseY, int button) {
        Geometry geo = geometry(ctx);
        if (draggingScrollbar) {
            dragScrollbar(geo, contentHeight(lines(ctx.font(), geo.rowWidth())), mouseY);
            return true;
        }
        if (draggingSlider != null) {
            for (Line line : lines(ctx.font(), geo.rowWidth())) {
                if (line.isRow() && line.row().setting == draggingSlider) {
                    draggingSlider.previewNumber(line.row().sliderValueAt(mouseX - geo.paneX()));
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    public boolean mouseReleased(PageContext ctx, double mouseX, double mouseY, int button) {
        boolean used = draggingSlider != null || draggingScrollbar;
        if (draggingSlider != null) {
            // Saved once, when it's let go.
            draggingSlider.setNumber(draggingSlider.number());
            click(1.6f);
        }
        draggingSlider = null;
        draggingScrollbar = false;
        return used;
    }

    private void dragScrollbar(Geometry geo, int content, double mouseY) {
        int h = geo.listHeight();
        int thumb = Mth.clamp(h * h / Math.max(1, content), 16, h);
        double progress = (mouseY - scrollbarGrab - geo.listTop()) / Math.max(1, h - thumb);
        scrollTarget = scroll = Mth.clamp(progress, 0, 1) * Math.max(0, content - h);
    }

    @Override
    public boolean mouseScrolled(PageContext ctx, double mouseX, double mouseY, double amount) {
        Geometry geo = geometry(ctx);
        if (inside(mouseX, mouseY, geo.sideX(), geo.categoryTop(), geo.sideWidth(), geo.categoryBottom() - geo.categoryTop())) {
            categoryScrollTarget -= amount * WHEEL_STEP;
            return true;
        }
        double max = maxScroll(geo, lines(ctx.font(), geo.rowWidth()));
        scrollTarget = Mth.clamp(scrollTarget - amount * WHEEL_STEP, 0, max);
        return true;
    }

    private static boolean inside(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    // ------------------------------------------------------------------ keyboard

    @Override
    public boolean keyPressed(PageContext ctx, KeyEvent event) {
        int key = event.key();
        boolean shift = event.hasShiftDown();
        Geometry geo = geometry(ctx);
        List<Line> lines = lines(ctx.font(), geo.rowWidth());
        List<Setting> rows = rows(lines);

        if (key == InputConstants.KEY_TAB) {
            Focus[] order = {Focus.SEARCH, Focus.CATEGORIES, Focus.SETTINGS};
            // From no focus, Tab goes to the categories (and Shift+Tab to the settings), as if from the search box.
            int at = switch (focus) {
                case NONE, SEARCH -> 0;
                case CATEGORIES -> 1;
                case SETTINGS -> 2;
            };
            Focus next = order[Math.floorMod(at + (shift ? -1 : 1), order.length)];
            if (next == Focus.SETTINGS && rows.isEmpty()) {
                next = order[Math.floorMod(at + (shift ? -2 : 2), order.length)];
            }
            setFocus(next, rows, geo, lines);
            return true;
        }
        if (focus == Focus.SEARCH) {
            if ((key == InputConstants.KEY_RETURN || key == InputConstants.KEY_NUMPADENTER || key == InputConstants.KEY_DOWN) && !rows.isEmpty()) {
                setFocus(Focus.SETTINGS, rows, geo, lines);
                return true;
            }
            if (search.keyPressed(event)) {
                scroll = scrollTarget = 0;
            }
            // Every other key stays in the box, so typing never switches pages or closes the menu.
            return true;
        }
        if (key == InputConstants.KEY_SLASH || (key == InputConstants.KEY_F && event.hasControlDown())) {
            focus = Focus.SEARCH;
            // The "/" also arrives as a typed character right after; it shouldn't land in the box.
            swallowSlash = key == InputConstants.KEY_SLASH;
            return true;
        }
        if (focus == Focus.CATEGORIES) {
            List<SettingCategory> categories = SkyRpgSettings.categories();
            int index = 0;
            for (int i = 0; i < categories.size(); i++) {
                if (categories.get(i).name().equals(current())) {
                    index = i;
                }
            }
            if (key == InputConstants.KEY_UP || key == InputConstants.KEY_DOWN) {
                int next = Mth.clamp(index + (key == InputConstants.KEY_DOWN ? 1 : -1), 0, categories.size() - 1);
                selectCategory(categories.get(next).name(), true);
                revealCategory(geo, next, categories.size());
                return true;
            }
            if (key == InputConstants.KEY_RIGHT || key == InputConstants.KEY_RETURN || key == InputConstants.KEY_NUMPADENTER
                    || key == InputConstants.KEY_SPACE) {
                List<Line> fresh = lines(ctx.font(), geo.rowWidth());
                setFocus(Focus.SETTINGS, rows(fresh), geo, fresh);
                return true;
            }
            return false;
        }
        if (focus == Focus.SETTINGS) {
            int index = Math.max(0, indexOf(rows, focusedSetting));
            Setting setting = rows.isEmpty() ? null : rows.get(index);
            switch (key) {
                case InputConstants.KEY_UP, InputConstants.KEY_DOWN, InputConstants.KEY_PAGEUP, InputConstants.KEY_PAGEDOWN,
                     InputConstants.KEY_HOME, InputConstants.KEY_END -> {
                    if (rows.isEmpty()) {
                        return true;
                    }
                    int next = switch (key) {
                        case InputConstants.KEY_UP -> index - 1;
                        case InputConstants.KEY_DOWN -> index + 1;
                        case InputConstants.KEY_PAGEUP -> index - 5;
                        case InputConstants.KEY_PAGEDOWN -> index + 5;
                        case InputConstants.KEY_HOME -> 0;
                        default -> rows.size() - 1;
                    };
                    focusedSetting = rows.get(Mth.clamp(next, 0, rows.size() - 1)).id();
                    reveal(geo, lines);
                    return true;
                }
                case InputConstants.KEY_RETURN, InputConstants.KEY_NUMPADENTER, InputConstants.KEY_SPACE -> {
                    if (setting != null && !setting.isSlider()) {
                        click(setting.isToggle() ? (setting.get() ? 0.9f : 1.3f) : 1.2f);
                        setting.activate();
                    }
                    return true;
                }
                case InputConstants.KEY_LEFT, InputConstants.KEY_RIGHT -> {
                    int direction = key == InputConstants.KEY_RIGHT ? 1 : -1;
                    if (setting != null && setting.isChoice()) {
                        setting.cycle(direction);
                        click(direction > 0 ? 1.3f : 1.1f);
                    } else if (setting != null && setting.isSlider()) {
                        double step = setting.step() > 0 ? setting.step() : (setting.max() - setting.min()) / 20;
                        setting.setNumber(setting.number() + direction * step * (shift ? 10 : 1));
                    } else if (direction < 0) {
                        focus = Focus.CATEGORIES;
                    }
                    return true;
                }
                default -> {
                    return false;
                }
            }
        }
        return false;
    }

    @Override
    public boolean charTyped(PageContext ctx, CharacterEvent event) {
        if (focus != Focus.SEARCH) {
            return false;
        }
        if (swallowSlash) {
            swallowSlash = false;
            if (event.codepoint() == '/') {
                return true;
            }
        }
        if (search.charTyped(event)) {
            scroll = scrollTarget = 0;
        }
        return true;
    }

    @Override
    public boolean back(PageContext ctx) {
        if (focus == Focus.SEARCH) {
            if (!search.isEmpty()) {
                search.clear();
                scroll = scrollTarget = 0;
            } else {
                focus = Focus.NONE;
            }
            return true;
        }
        if (searching()) {
            search.clear();
            scroll = scrollTarget = 0;
            focus = Focus.NONE;
            return true;
        }
        if (focus == Focus.SETTINGS) {
            focus = Focus.CATEGORIES;
            return true;
        }
        if (focus == Focus.CATEGORIES) {
            focus = Focus.NONE;
            return true;
        }
        return false;
    }

    private void setFocus(Focus next, List<Setting> rows, Geometry geo, List<Line> lines) {
        focus = next;
        if (next == Focus.SETTINGS && indexOf(rows, focusedSetting) < 0) {
            focusedSetting = firstVisible(geo, lines);
        }
        if (next == Focus.SETTINGS) {
            reveal(geo, lines);
        }
    }

    private static int indexOf(List<Setting> rows, String id) {
        for (int i = 0; i < rows.size(); i++) {
            if (rows.get(i).id().equals(id)) {
                return i;
            }
        }
        return -1;
    }

    /** The first card at least partly in view, to start keyboard focus where the player is looking. */
    private String firstVisible(Geometry geo, List<Line> lines) {
        for (Line line : lines) {
            if (line.isRow() && line.y() + line.height() > scrollTarget + 4) {
                return line.row().setting.id();
            }
        }
        return null;
    }

    /** Scrolls so the focused card is fully in view (with its section heading, if it's the first under one). */
    private void reveal(Geometry geo, List<Line> lines) {
        for (int i = 0; i < lines.size(); i++) {
            Line line = lines.get(i);
            if (line.isRow() && line.row().setting.id().equals(focusedSetting)) {
                int top = i > 0 && !lines.get(i - 1).isRow() ? lines.get(i - 1).y() : line.y();
                int bottom = line.y() + line.height();
                if (top < scrollTarget) {
                    scrollTarget = top;
                } else if (bottom > scrollTarget + geo.listHeight()) {
                    scrollTarget = bottom - geo.listHeight();
                }
                return;
            }
        }
    }

    private void revealCategory(Geometry geo, int index, int count) {
        int rowHeight = categoryRowHeight(geo, count);
        int room = geo.categoryBottom() - geo.categoryTop() - 6;
        int top = index * (rowHeight + CATEGORY_GAP);
        if (top < categoryScrollTarget) {
            categoryScrollTarget = top;
        } else if (top + rowHeight > categoryScrollTarget + room) {
            categoryScrollTarget = top + rowHeight - room;
        }
    }

    private static void click(float pitch) {
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK.value(), pitch, 0.4f));
    }
}
