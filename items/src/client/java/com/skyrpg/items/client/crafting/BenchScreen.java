package com.skyrpg.items.client.crafting;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.platform.cursor.CursorTypes;
import com.skyrpg.core.client.menu.SearchField;
import com.skyrpg.core.client.ui.Draw;
import com.skyrpg.core.client.ui.Theme;
import com.skyrpg.core.text.Msg;
import com.skyrpg.items.ModItems;
import com.skyrpg.items.crafting.Bench;
import com.skyrpg.items.crafting.BenchPayloads;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextColor;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.util.Util;
import net.minecraft.world.item.ItemStack;

/**
 * The Artisan Bench in three columns: categories on the left (with Pinned, Ready to craft and All on top), the recipes of
 * the chosen one in the middle, grouped by what they make (weapons, tools, armour...) with a status chip on each row, and
 * the selected recipe on the right: the result, every ingredient with what you have of it, and Craft, Craft max, Pin and
 * Track buttons. Double-click a row crafts one (shift for as many as you can), right-click tracks it on the HUD
 * ({@link TrackedRecipeWidget}). Pins are kept on your computer ({@link BenchPins}). The server sends a fresh view after
 * every craft.
 */
public final class BenchScreen extends Screen {
    private static final int MAX_WIDTH = 520;
    private static final int MAX_HEIGHT = 300;
    private static final int HEADER = 38;
    private static final int FOOTER = 16;
    private static final int PAD = 8;
    private static final int GAP = 6;
    private static final int SIDEBAR = 104;
    /** The sidebar on a narrow window: icons only. */
    private static final int SIDEBAR_NARROW = 24;
    private static final int DETAIL = 158;
    private static final int SIDE_ROW = 18;
    private static final int ROW = 22;
    private static final int HEADING = 15;
    private static final int BUTTON = 16;
    private static final int INGREDIENT_ROW = 20;
    private static final String ALL = "all";
    private static final String PINNED = "*pinned";
    private static final String READY = "*ready";
    private static final long NOTICE_MILLIS = 4000;
    private static final int MAX_CRAFT = 64;

    /** The headings recipes are grouped under, in order, by the kind of item they make. */
    private enum Group {
        WEAPONS("Weapons"), TOOLS("Tools"), ARMOUR("Armour"), ACCESSORIES("Accessories"), BACKPACKS("Backpacks"), OTHER("Materials and more");

        final String label;

        Group(String label) {
            this.label = label;
        }

        static Group of(ItemStack stack) {
            ModItems.Kind kind = ModItems.kind(stack.getItem());
            if (kind == null) {
                return OTHER;
            }
            if (kind.isArmor()) {
                return ARMOUR;
            }
            return switch (kind) {
                case SWORD, BOW, WAND, SHIELD -> WEAPONS;
                case PICKAXE, AXE, HOE, ROD -> TOOLS;
                case AMULET, CLOAK, BELT, BRACERS -> ACCESSORIES;
                case BACKPACK -> BACKPACKS;
                default -> OTHER;
            };
        }
    }

    /** One line of the recipe list: a group heading (entry null) or a recipe, at {@code y} from the list's top. */
    private record Line(String heading, int count, BenchPayloads.Entry entry, int y) {
        int height() {
            return entry == null ? HEADING : ROW;
        }
    }

    private List<BenchPayloads.Entry> entries;
    private String tab = BenchPins.any() ? PINNED : ALL;
    private Identifier selected;
    private double scroll;
    private double sideScroll;
    private int left;
    private int top;
    private int windowWidth;
    private int windowHeight;
    private Component notice = Component.empty();
    private boolean noticeGood;
    private long noticeAt;
    private final SearchField search = new SearchField("Search recipes");
    private boolean searchFocused;

    private BenchScreen(List<BenchPayloads.Entry> entries) {
        super(Component.literal("Artisan Bench"));
        this.entries = entries;
        if (tab.equals(PINNED) && shown().isEmpty()) {
            tab = ALL;
        }
    }

    /** A view from the server: opens the bench, or updates the open one. */
    public static void receive(BenchPayloads.View view) {
        Minecraft minecraft = Minecraft.getInstance();
        BenchCatalog.update(view.entries());
        if (minecraft.gui.screen() instanceof BenchScreen open) {
            open.entries = view.entries();
            open.notify(view.notice(), view.good());
        } else if (!view.refresh()) {
            minecraft.gui.setScreen(new BenchScreen(view.entries()));
        }
    }

    private void notify(Component text, boolean good) {
        if (!text.getString().isEmpty()) {
            notice = text;
            noticeGood = good;
            noticeAt = Util.getMillis();
        }
    }

    @Override
    protected void init() {
        windowWidth = Math.min(width - 24, MAX_WIDTH);
        windowHeight = Math.min(height - 24, MAX_HEIGHT);
        left = (width - windowWidth) / 2;
        top = (height - windowHeight) / 2;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        extractBlurredBackground(g);
        g.fill(0, 0, width, height, Theme.OVERLAY);
    }

    // ---------------------------------------------------------------- what's shown

    /** Pinned, Ready and All, then each category in the order the server lists them. */
    private List<String> tabs() {
        Set<String> tabs = new LinkedHashSet<>();
        tabs.add(PINNED);
        tabs.add(READY);
        tabs.add(ALL);
        entries.forEach(entry -> tabs.add(entry.category()));
        return new ArrayList<>(tabs);
    }

    private boolean inTab(BenchPayloads.Entry entry, String name) {
        return switch (name) {
            case ALL -> true;
            case PINNED -> BenchPins.pinned(entry.id());
            case READY -> entry.craftable() > 0;
            default -> entry.category().equals(name);
        };
    }

    private int count(String name) {
        int count = 0;
        for (BenchPayloads.Entry entry : entries) {
            if (inTab(entry, name)) {
                count++;
            }
        }
        return count;
    }

    /** The chosen tab's recipes, or with a search, matching recipes from every tab. */
    private List<BenchPayloads.Entry> shown() {
        String query = search.text().trim().toLowerCase(Locale.ROOT);
        return entries.stream()
                .filter(entry -> query.isEmpty() ? inTab(entry, tab) : matches(entry, query))
                .toList();
    }

    /** The result's name or any ingredient's name contains the search text. */
    private static boolean matches(BenchPayloads.Entry entry, String query) {
        if (entry.result().getHoverName().getString().toLowerCase(Locale.ROOT).contains(query)) {
            return true;
        }
        for (ItemStack ingredient : entry.ingredients()) {
            if (ingredient.getHoverName().getString().toLowerCase(Locale.ROOT).contains(query)) {
                return true;
            }
        }
        return false;
    }

    /** The list's lines: per group a heading, then its recipes, the ones you can make first, otherwise in server order. */
    private List<Line> lines() {
        Map<Group, List<BenchPayloads.Entry>> groups = new LinkedHashMap<>();
        for (Group group : Group.values()) {
            groups.put(group, new ArrayList<>());
        }
        for (BenchPayloads.Entry entry : shown()) {
            groups.get(Group.of(entry.result())).add(entry);
        }
        List<Line> lines = new ArrayList<>();
        int y = 0;
        for (Map.Entry<Group, List<BenchPayloads.Entry>> group : groups.entrySet()) {
            List<BenchPayloads.Entry> list = new ArrayList<>(group.getValue());
            if (list.isEmpty()) {
                continue;
            }
            list.sort(Comparator.comparingInt(entry -> !entry.unlocked() ? 2 : entry.craftable() > 0 ? 0 : 1));
            lines.add(new Line(group.getKey().label, list.size(), null, y));
            y += HEADING;
            for (BenchPayloads.Entry entry : list) {
                lines.add(new Line(null, 0, entry, y));
                y += ROW;
            }
        }
        return lines;
    }

    /** The selected recipe if it's still shown, else the first shown one (and it becomes the selection). */
    private BenchPayloads.Entry selection(List<Line> lines) {
        BenchPayloads.Entry first = null;
        for (Line line : lines) {
            if (line.entry() != null) {
                if (line.entry().id().equals(selected)) {
                    return line.entry();
                }
                if (first == null) {
                    first = line.entry();
                }
            }
        }
        selected = first == null ? null : first.id();
        return first;
    }

    // ---------------------------------------------------------------- layout

    private boolean narrow() {
        return windowWidth < 470;
    }

    private int sidebarWidth() {
        return narrow() ? SIDEBAR_NARROW : SIDEBAR;
    }

    private int bodyTop() {
        return top + HEADER;
    }

    private int bodyHeight() {
        return windowHeight - HEADER - FOOTER - 4;
    }

    private int sideX() {
        return left + PAD;
    }

    private int listX() {
        return sideX() + sidebarWidth() + GAP;
    }

    private int detailX() {
        return left + windowWidth - PAD - DETAIL;
    }

    private int listWidth() {
        return detailX() - GAP - listX() - 4;
    }

    private int searchWidth() {
        return Math.min(150, windowWidth / 3);
    }

    private int searchX() {
        return left + windowWidth - PAD - searchWidth();
    }

    private int searchY() {
        return top + 11;
    }

    private static String label(String category) {
        return switch (category) {
            case ALL -> "All";
            case PINNED -> "Pinned";
            case READY -> "Ready to craft";
            default -> category.isEmpty() ? category : category.substring(0, 1).toUpperCase(Locale.ROOT) + category.substring(1).replace('_', ' ');
        };
    }

    // ---------------------------------------------------------------- drawing

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(g, mouseX, mouseY, partialTick);
        Draw.panel(g, left, top, windowWidth, windowHeight, Theme.WINDOW, Theme.BORDER);
        g.fill(left + 1, top, left + windowWidth - 1, top + 2, Theme.ACCENT);

        // Header: the bench, a title and how much is ready.
        Draw.rounded(g, left + PAD, top + 7, 24, 24, Theme.CARD);
        Draw.item(g, new ItemStack(Bench.ITEM), left + PAD + 4, top + 11, 1f);
        int titleRoom = searchX() - (left + PAD + 32) - 8;
        Draw.fitText(g, font, "Artisan Bench", left + PAD + 32, top + 9, titleRoom, Theme.TEXT);
        int ready = count(READY);
        Draw.fitText(g, font, Component.literal(entries.size() + " recipes  ·  ").withColor(Theme.MUTED)
                .append(Component.literal(ready + " ready to craft").withColor(ready > 0 ? Theme.GREEN : Theme.MUTED)),
                left + PAD + 32, top + 20, titleRoom, Theme.MUTED);
        search.draw(g, font, searchX(), searchY(), searchWidth(), searchFocused,
                inside(mouseX, mouseY, searchX(), searchY(), searchWidth(), SearchField.HEIGHT), mouseX);

        List<Line> lines = lines();
        BenchPayloads.Entry chosen = selection(lines);
        drawSidebar(g, mouseX, mouseY);
        drawList(g, lines, chosen, mouseX, mouseY);
        drawDetail(g, chosen, mouseX, mouseY);

        // Footer: the last result for a while, else how to use it.
        int fy = top + windowHeight - FOOTER + 2;
        g.fill(left + 1, fy - 4, left + windowWidth - 1, top + windowHeight - 1, Theme.FOOTER);
        if (Util.getMillis() - noticeAt < NOTICE_MILLIS && !notice.getString().isEmpty()) {
            Draw.fitText(g, font, notice, left + PAD, fy, windowWidth - PAD * 2, noticeGood ? Theme.GREEN : Theme.RED);
        } else {
            Draw.fitText(g, font, "Double-click to craft (Shift: as many as you can)  ·  Right-click to track  ·  P to pin  ·  Ctrl+F to search",
                    left + PAD, fy, windowWidth - PAD * 2, Theme.DIM);
        }
        Draw.drawQueuedTooltip(g, font, mouseX, mouseY);
    }

    private void drawSidebar(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        int x = sideX();
        int y0 = bodyTop();
        int w = sidebarWidth();
        int h = bodyHeight();
        Draw.rounded(g, x, y0, w, h, Draw.withAlpha(Theme.CARD, 0.6f));
        List<String> names = tabs();
        int content = names.size() * SIDE_ROW + 4;
        sideScroll = Mth.clamp(sideScroll, 0, Math.max(0, content - h));
        g.enableScissor(x, y0, x + w, y0 + h);
        for (int i = 0; i < names.size(); i++) {
            String name = names.get(i);
            int ry = y0 + 2 + i * SIDE_ROW - (int) Math.round(sideScroll);
            boolean selected = name.equals(tab) && search.isEmpty();
            boolean hovered = inside(mouseX, mouseY, x, ry, w, SIDE_ROW) && mouseY >= y0 && mouseY < y0 + h;
            if (selected || hovered) {
                Draw.rounded(g, x + 2, ry, w - 4, SIDE_ROW - 1, selected ? Draw.withAlpha(Theme.ACCENT, 0.16f) : Theme.CARD_HOVER);
            }
            if (selected) {
                g.fill(x + 2, ry + 3, x + 4, ry + SIDE_ROW - 4, Theme.ACCENT);
            }
            drawTabIcon(g, name, x + 5, ry + 1);
            int count = count(name);
            if (!narrow()) {
                Draw.fitText(g, font, label(name), x + 24, ry + 5, w - 24 - 20, selected ? Theme.ACCENT : hovered ? Theme.TEXT : Theme.MUTED);
                boolean anyReady = !name.equals(READY) && entries.stream().anyMatch(entry -> inTab(entry, name) && entry.craftable() > 0);
                Draw.rightText(g, font, Integer.toString(count), x + w - 5, ry + 5, anyReady ? Theme.GREEN : Theme.DIM);
            }
            if (hovered) {
                g.requestCursor(CursorTypes.POINTING_HAND);
                if (narrow()) {
                    Draw.queueTooltip(List.of(Component.literal(label(name)), Component.literal(count + " recipes").withColor(Theme.MUTED)), Theme.ACCENT);
                }
            }
        }
        g.disableScissor();
    }

    private void drawTabIcon(GuiGraphicsExtractor g, String name, int x, int y) {
        switch (name) {
            case PINNED -> Draw.text(g, font, "★", x + 4, y + 4, Theme.ACCENT);
            case READY -> Draw.text(g, font, "✔", x + 4, y + 4, Theme.GREEN);
            case ALL -> Draw.item(g, new ItemStack(Bench.ITEM), x, y, 1f);
            default -> entries.stream().filter(entry -> entry.category().equals(name)).findFirst()
                    .ifPresent(entry -> Draw.item(g, entry.result(), x, y, 1f));
        }
    }

    private void drawList(GuiGraphicsExtractor g, List<Line> lines, BenchPayloads.Entry chosen, int mouseX, int mouseY) {
        int x = listX();
        int y0 = bodyTop();
        int w = listWidth();
        int h = bodyHeight();
        int content = lines.isEmpty() ? 0 : lines.getLast().y() + lines.getLast().height();
        scroll = Mth.clamp(scroll, 0, Math.max(0, content - h));
        if (lines.isEmpty()) {
            String text = !search.isEmpty() ? "No recipes match \"" + search.text() + "\"."
                    : tab.equals(PINNED) ? "Nothing pinned yet. Select a recipe and press P (or the Pin button)."
                    : tab.equals(READY) ? "Nothing you can craft right now." : "No recipes yet.";
            for (var line : font.split(Component.literal(text), w - 8)) {
                g.text(font, line, x + 4, y0 + 8, Theme.MUTED, false);
                y0 += 10;
            }
            return;
        }
        g.enableScissor(x, bodyTop(), x + w, bodyTop() + h);
        for (Line line : lines) {
            int ly = y0 + line.y() - (int) Math.round(scroll);
            if (ly + line.height() < y0 || ly > y0 + h) {
                continue;
            }
            if (line.entry() == null) {
                String text = line.heading().toUpperCase(Locale.ROOT);
                Draw.text(g, font, text, x + 2, ly + 4, Theme.DIM);
                int after = x + 2 + font.width(text) + 4;
                Draw.text(g, font, Integer.toString(line.count()), after, ly + 4, Theme.DIM);
                int lineX = after + font.width(Integer.toString(line.count())) + 5;
                if (lineX < x + w - 2) {
                    g.fill(lineX, ly + 8, x + w - 2, ly + 9, Theme.CARD_BORDER);
                }
                continue;
            }
            boolean hovered = inside(mouseX, mouseY, x, ly, w, ROW) && mouseY >= y0 && mouseY < y0 + h;
            drawRow(g, line.entry(), x, ly, w, line.entry() == chosen, hovered);
            if (hovered) {
                g.requestCursor(CursorTypes.POINTING_HAND);
            }
        }
        g.disableScissor();
        if (content > h) {
            Draw.scrollbar(g, x + w + 1, y0, h, content, scroll, Theme.DIM);
        }
    }

    private void drawRow(GuiGraphicsExtractor g, BenchPayloads.Entry entry, int x, int y, int w, boolean selected, boolean hovered) {
        if (selected) {
            Draw.rounded(g, x, y, w, ROW - 2, Draw.withAlpha(Theme.ACCENT, 0.14f));
            g.fill(x, y + 3, x + 2, y + ROW - 5, Theme.ACCENT);
        } else if (hovered) {
            Draw.rounded(g, x, y, w, ROW - 2, Theme.CARD_HOVER);
        }
        Draw.item(g, entry.result(), x + 5, y + 2, 1f);
        if (entry.result().getCount() > 1) {
            g.text(font, Integer.toString(entry.result().getCount()), x + 22 - font.width(Integer.toString(entry.result().getCount())), y + 11, Theme.TEXT, true);
        }
        // The status chip on the right.
        String chip;
        int chipColor;
        if (!entry.unlocked()) {
            chip = "✖ " + entry.requirement();
            chipColor = Theme.RED;
        } else if (entry.craftable() > 0) {
            chip = "✔ " + (entry.craftable() > 99 ? "99+" : Integer.toString(entry.craftable()));
            chipColor = Theme.GREEN;
        } else {
            chip = enough(entry) + "/" + entry.ingredients().size();
            chipColor = Theme.ACCENT;
        }
        int chipWidth = Math.min(font.width(chip) + 8, w / 2);
        int chipX = x + w - chipWidth - 4;
        Draw.rounded(g, chipX, y + 4, chipWidth, 12, Draw.withAlpha(chipColor, 0.14f));
        Draw.fitText(g, font, chip, chipX + 4, y + 6, chipWidth - 6, chipColor);

        int nameX = x + 26;
        if (BenchPins.pinned(entry.id())) {
            Draw.text(g, font, "★", nameX, y + 6, Theme.ACCENT);
            nameX += 9;
        }
        if (entry.id().equals(TrackedRecipeWidget.trackedId())) {
            Draw.text(g, font, "◆", nameX, y + 6, Theme.AQUA);
            nameX += 9;
        }
        Draw.fitText(g, font, entry.result().getHoverName().getString(), nameX, y + 6, chipX - nameX - 4,
                entry.unlocked() ? nameColor(entry.result()) : Theme.DIM);
    }

    /** How many of the recipe's ingredients you have enough of. */
    private static int enough(BenchPayloads.Entry entry) {
        int enough = 0;
        for (int i = 0; i < entry.ingredients().size(); i++) {
            if (entry.have().get(i) >= entry.ingredients().get(i).getCount()) {
                enough++;
            }
        }
        return enough;
    }

    private void drawDetail(GuiGraphicsExtractor g, BenchPayloads.Entry entry, int mouseX, int mouseY) {
        int x = detailX();
        int y = bodyTop();
        int w = DETAIL;
        int h = bodyHeight();
        Draw.panel(g, x, y, w, h, Theme.CARD, Theme.CARD_BORDER);
        if (entry == null) {
            Draw.fit(g, font, Component.literal("Pick a recipe"), x + w / 2, y + h / 2 - 4, w - 12, Draw.Align.CENTER, Theme.DIM);
            return;
        }
        ItemStack result = entry.result();
        int color = nameColor(result);

        // The result, big, with its tooltip on hover.
        int box = 40;
        int bx = x + (w - box) / 2;
        int by = y + 8;
        boolean overResult = inside(mouseX, mouseY, bx, by, box, box);
        Draw.rounded(g, bx, by, box, box, Draw.withAlpha(color, overResult ? 0.22f : 0.12f));
        Draw.item(g, result, bx + 4, by + 4, 2f);
        if (result.getCount() > 1) {
            Draw.rightText(g, font, "×" + result.getCount(), bx + box - 2, by + box - 10, Theme.TEXT);
        }
        if (overResult) {
            Draw.queueTooltip(Screen.getTooltipFromItem(minecraft, result), color);
        }
        int ty = by + box + 5;
        Draw.fit(g, font, Component.literal(result.getHoverName().getString()), x + w / 2, ty, w - 10, Draw.Align.CENTER, color);
        ty += 11;
        String sub = label(entry.category()) + (result.getCount() > 1 ? "  ·  makes " + result.getCount() : "");
        Draw.fit(g, font, Component.literal(sub), x + w / 2, ty, w - 10, Draw.Align.CENTER, Theme.MUTED);
        ty += 11;
        if (!entry.requirement().isEmpty()) {
            Draw.fit(g, font, Component.literal((entry.unlocked() ? "✔ " : "✖ Needs ") + entry.requirement()), x + w / 2, ty, w - 10,
                    Draw.Align.CENTER, entry.unlocked() ? Theme.GREEN : Theme.RED);
            ty += 11;
        }

        // Ingredients, each with what you have of it.
        ty += 3;
        Draw.text(g, font, "INGREDIENTS", x + 6, ty, Theme.DIM);
        ty += 11;
        int buttonsTop = y + h - 6 - BUTTON * 2 - 4;
        g.enableScissor(x, ty, x + w, buttonsTop - 2);
        for (int i = 0; i < entry.ingredients().size(); i++) {
            ItemStack ingredient = entry.ingredients().get(i);
            int need = ingredient.getCount();
            int have = entry.have().get(i);
            boolean ok = have >= need;
            int ry = ty + i * INGREDIENT_ROW;
            Draw.item(g, ingredient, x + 6, ry, 1f);
            String amount = Draw.compactNumbers(Msg.number(Math.min(have, 99_999))) + "/" + Draw.compactNumbers(Msg.number(need));
            Draw.rightText(g, font, amount, x + w - 6, ry + 1, ok ? Theme.GREEN : Theme.RED);
            Draw.fitText(g, font, ingredient.getHoverName().getString(), x + 25, ry + 1, w - 25 - font.width(amount) - 10, nameColor(ingredient));
            Draw.bar(g, x + 25, ry + 12, w - 31, 2, Math.min(1f, have / (float) Math.max(1, need)), ok ? Theme.GREEN : Theme.ACCENT);
            if (inside(mouseX, mouseY, x + 4, ry - 1, w - 8, INGREDIENT_ROW - 2) && mouseY < buttonsTop - 2) {
                List<Component> tip = new ArrayList<>(Screen.getTooltipFromItem(minecraft, ingredient));
                tip.add(Component.literal("You have " + Msg.number(have) + " of " + Msg.number(need)).withColor(ok ? Theme.GREEN : Theme.RED));
                Draw.queueTooltip(tip, nameColor(ingredient));
            }
        }
        g.disableScissor();

        // Buttons: Craft, Craft max; Pin, Track.
        int craftable = entry.craftable();
        int half = (w - 12 - 4) / 2;
        int b1 = buttonsTop;
        int b2 = buttonsTop + BUTTON + 4;
        button(g, "Craft", x + 6, b1, half, craftable > 0, Theme.GREEN, mouseX, mouseY);
        button(g, craftable > 1 ? "Craft ×" + Math.min(craftable, MAX_CRAFT) : "Craft max", x + 6 + half + 4, b1, half, craftable > 1, Theme.GREEN, mouseX, mouseY);
        boolean pinned = BenchPins.pinned(entry.id());
        boolean tracked = entry.id().equals(TrackedRecipeWidget.trackedId());
        button(g, pinned ? "★ Unpin" : "★ Pin", x + 6, b2, half, true, Theme.ACCENT, mouseX, mouseY);
        button(g, tracked ? "◆ Untrack" : "◆ Track", x + 6 + half + 4, b2, half, true, Theme.AQUA, mouseX, mouseY);
    }

    private void button(GuiGraphicsExtractor g, String text, int x, int y, int w, boolean enabled, int color, int mouseX, int mouseY) {
        boolean hovered = enabled && inside(mouseX, mouseY, x, y, w, BUTTON);
        Draw.panel(g, x, y, w, BUTTON, !enabled ? Theme.INPUT : Draw.withAlpha(color, hovered ? 0.30f : 0.16f),
                !enabled ? Theme.CARD_BORDER : Draw.withAlpha(color, hovered ? 0.9f : 0.5f));
        Draw.fit(g, font, Component.literal(text), x + w / 2, y + 4, w - 6, Draw.Align.CENTER, enabled ? (hovered ? Theme.TEXT : color) : Theme.DIM);
        if (hovered) {
            g.requestCursor(CursorTypes.POINTING_HAND);
        }
    }

    private static int nameColor(ItemStack stack) {
        TextColor color = stack.getHoverName().getStyle().getColor();
        return color != null ? 0xFF000000 | color.getValue() : Theme.TEXT;
    }

    private static boolean inside(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    // ---------------------------------------------------------------- input

    private void craft(BenchPayloads.Entry entry, boolean max) {
        if (entry == null) {
            return;
        }
        int times = max ? Math.max(1, Math.min(entry.craftable(), MAX_CRAFT)) : 1;
        ClientPlayNetworking.send(new BenchPayloads.Craft(entry.id(), times));
        click(1.0f);
    }

    private void track(BenchPayloads.Entry entry) {
        if (entry == null) {
            return;
        }
        boolean tracking = entry.id().equals(TrackedRecipeWidget.trackedId());
        ClientPlayNetworking.send(new BenchPayloads.Track(tracking ? "" : entry.id().toString()));
        click(tracking ? 0.8f : 1.4f);
    }

    private void pin(BenchPayloads.Entry entry) {
        if (entry == null) {
            return;
        }
        BenchPins.toggle(entry.id());
        click(BenchPins.pinned(entry.id()) ? 1.4f : 0.8f);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        boolean right = event.button() == InputConstants.MOUSE_BUTTON_RIGHT;
        if (event.button() != InputConstants.MOUSE_BUTTON_LEFT && !right) {
            return super.mouseClicked(event, doubleClick);
        }
        double mx = event.x();
        double my = event.y();
        boolean onSearch = inside(mx, my, searchX(), searchY(), searchWidth(), SearchField.HEIGHT);
        if (onSearch && !right) {
            if (!search.isEmpty() && SearchField.overClear(searchX(), searchWidth(), mx)) {
                search.clear();
                scroll = 0;
            }
            searchFocused = true;
            return true;
        }
        searchFocused = false;
        int y0 = bodyTop();
        int h = bodyHeight();
        if (my < y0 || my >= y0 + h) {
            return super.mouseClicked(event, doubleClick);
        }

        // Sidebar.
        if (!right && inside(mx, my, sideX(), y0, sidebarWidth(), h)) {
            int index = (int) ((my - y0 - 2 + sideScroll) / SIDE_ROW);
            List<String> names = tabs();
            if (index >= 0 && index < names.size()) {
                tab = names.get(index);
                search.clear();
                scroll = 0;
                click(1.2f);
            }
            return true;
        }

        // Recipe list.
        List<Line> lines = lines();
        if (inside(mx, my, listX(), y0, listWidth(), h)) {
            for (Line line : lines) {
                int ly = y0 + line.y() - (int) Math.round(scroll);
                if (line.entry() != null && my >= ly && my < ly + ROW) {
                    if (right) {
                        track(line.entry());
                    } else if (doubleClick && line.entry().id().equals(selected)) {
                        craft(line.entry(), event.hasShiftDown());
                    } else {
                        selected = line.entry().id();
                        click(1.6f);
                    }
                    return true;
                }
            }
            return true;
        }

        // Detail buttons.
        BenchPayloads.Entry entry = selection(lines);
        if (entry != null && !right) {
            int x = detailX();
            int half = (DETAIL - 12 - 4) / 2;
            int b1 = y0 + h - 6 - BUTTON * 2 - 4;
            int b2 = b1 + BUTTON + 4;
            if (inside(mx, my, x + 6, b1, half, BUTTON) && entry.craftable() > 0) {
                craft(entry, false);
            } else if (inside(mx, my, x + 6 + half + 4, b1, half, BUTTON) && entry.craftable() > 1) {
                craft(entry, true);
            } else if (inside(mx, my, x + 6, b2, half, BUTTON)) {
                pin(entry);
            } else if (inside(mx, my, x + 6 + half + 4, b2, half, BUTTON)) {
                track(entry);
            }
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }

    private void click(float pitch) {
        minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK.value(), pitch, 0.4f));
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (mouseX < listX()) {
            sideScroll -= scrollY * SIDE_ROW;
        } else {
            scroll -= scrollY * ROW;
        }
        return true;
    }

    /** Moves the selection up or down the list, scrolling to keep it in view. */
    private void move(int step) {
        List<Line> lines = lines();
        List<Line> rows = lines.stream().filter(line -> line.entry() != null).toList();
        if (rows.isEmpty()) {
            return;
        }
        BenchPayloads.Entry current = selection(lines);
        int index = 0;
        for (int i = 0; i < rows.size(); i++) {
            if (rows.get(i).entry() == current) {
                index = i;
            }
        }
        Line next = rows.get(Mth.clamp(index + step, 0, rows.size() - 1));
        selected = next.entry().id();
        if (next.y() < scroll) {
            scroll = Math.max(0, next.y() - HEADING);
        } else if (next.y() + ROW > scroll + bodyHeight()) {
            scroll = next.y() + ROW - bodyHeight();
        }
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (searchFocused) {
            if (event.key() == InputConstants.KEY_ESCAPE || event.key() == InputConstants.KEY_RETURN) {
                searchFocused = false;
                return true;
            }
            if (search.keyPressed(event)) {
                scroll = 0;
            }
            return true;
        }
        if (event.hasControlDown() && event.key() == InputConstants.KEY_F) {
            searchFocused = true;
            return true;
        }
        switch (event.key()) {
            case InputConstants.KEY_UP -> {
                move(-1);
                return true;
            }
            case InputConstants.KEY_DOWN -> {
                move(1);
                return true;
            }
            case InputConstants.KEY_RETURN -> {
                BenchPayloads.Entry entry = selection(lines());
                if (entry != null && entry.craftable() > 0) {
                    craft(entry, event.hasShiftDown());
                }
                return true;
            }
            case InputConstants.KEY_P -> {
                pin(selection(lines()));
                return true;
            }
            case InputConstants.KEY_T -> {
                track(selection(lines()));
                return true;
            }
            default -> {
            }
        }
        if (minecraft.options.keyInventory.matches(event)) {
            onClose();
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public boolean charTyped(CharacterEvent event) {
        if (searchFocused && search.charTyped(event)) {
            scroll = 0;
            return true;
        }
        return super.charTyped(event);
    }
}
