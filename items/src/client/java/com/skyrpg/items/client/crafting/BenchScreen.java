package com.skyrpg.items.client.crafting;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.platform.cursor.CursorTypes;
import com.skyrpg.core.client.ui.Draw;
import com.skyrpg.core.client.ui.Theme;
import com.skyrpg.core.text.Msg;
import com.skyrpg.items.crafting.Bench;
import com.skyrpg.items.crafting.BenchPayloads;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextColor;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.util.Util;
import net.minecraft.world.item.ItemStack;

/**
 * The Artisan Bench: a tab per skill, a card per recipe with the result, its ingredients (red where you're
 * short) or the level it needs, and a footer with the last result. Click crafts one, shift-click as many as
 * your materials allow. The server sends a fresh view after every craft.
 */
public final class BenchScreen extends Screen {
    private static final int MAX_WIDTH = 440;
    private static final int MAX_HEIGHT = 280;
    private static final int HEADER = 40;
    private static final int TABS = 18;
    private static final int FOOTER = 16;
    private static final int PAD = 10;
    private static final int GAP = 6;
    private static final int CARD_MIN_WIDTH = 78;
    private static final int CARD_HEIGHT = 64;
    private static final String ALL = "all";
    private static final long NOTICE_MILLIS = 4000;

    private List<BenchPayloads.Entry> entries;
    private String tab = ALL;
    private double scroll;
    private int left;
    private int top;
    private int windowWidth;
    private int windowHeight;
    private Component notice = Component.empty();
    private boolean noticeGood;
    private long noticeAt;

    private BenchScreen(List<BenchPayloads.Entry> entries) {
        super(Component.literal("Artisan Bench"));
        this.entries = entries;
    }

    /** A view from the server: opens the bench, or updates the open one. */
    public static void receive(BenchPayloads.View view) {
        Minecraft minecraft = Minecraft.getInstance();
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

    private List<String> tabs() {
        Set<String> tabs = new LinkedHashSet<>();
        tabs.add(ALL);
        entries.forEach(entry -> tabs.add(entry.category()));
        return new ArrayList<>(tabs);
    }

    private List<BenchPayloads.Entry> shown() {
        return tab.equals(ALL) ? entries : entries.stream().filter(entry -> entry.category().equals(tab)).toList();
    }

    private static String label(String category) {
        return category.isEmpty() ? category : category.substring(0, 1).toUpperCase(Locale.ROOT) + category.substring(1).replace('_', ' ');
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(g, mouseX, mouseY, partialTick);
        Draw.panel(g, left, top, windowWidth, windowHeight, Theme.WINDOW, Theme.BORDER);
        g.fill(left + 1, top, left + windowWidth - 1, top + 2, Theme.ACCENT);

        // Header: the bench, a title and what to do.
        Draw.rounded(g, left + PAD, top + 8, 24, 24, Theme.CARD);
        Draw.item(g, new ItemStack(Bench.ITEM), left + PAD + 4, top + 12, 1f);
        Draw.text(g, font, "Artisan Bench", left + PAD + 32, top + 10, Theme.TEXT);
        Draw.text(g, font, "Craft gear from your materials (inventory and backpacks)", left + PAD + 32, top + 21, Theme.MUTED);

        // Tabs.
        int tx = left + PAD;
        int ty = top + HEADER;
        for (String name : tabs()) {
            String text = name.equals(ALL) ? "All" : label(name);
            int w = font.width(text) + 12;
            boolean selected = name.equals(tab);
            boolean hovered = inside(mouseX, mouseY, tx, ty, w, TABS - 4);
            Draw.rounded(g, tx, ty, w, TABS - 4, selected ? Draw.withAlpha(Theme.ACCENT, 0.18f) : hovered ? Theme.CARD_HOVER : Theme.CARD);
            Draw.text(g, font, text, tx + 6, ty + 3, selected ? Theme.ACCENT : hovered ? Theme.TEXT : Theme.MUTED);
            if (hovered) {
                g.requestCursor(CursorTypes.POINTING_HAND);
            }
            tx += w + 4;
        }

        drawCards(g, mouseX, mouseY);

        // Footer: the last result for a while, else how to use it.
        int fy = top + windowHeight - FOOTER + 2;
        if (Util.getMillis() - noticeAt < NOTICE_MILLIS && !notice.getString().isEmpty()) {
            Draw.fitText(g, font, notice, left + PAD, fy, windowWidth - PAD * 2, noticeGood ? Theme.GREEN : Theme.RED);
        } else {
            Draw.fitText(g, font, "Click to craft one  ·  Shift-click to craft as many as you can  ·  Esc to close",
                    left + PAD, fy, windowWidth - PAD * 2, Theme.DIM);
        }
        Draw.drawQueuedTooltip(g, font, mouseX, mouseY);
    }

    private int gridTop() {
        return top + HEADER + TABS;
    }

    private int gridHeight() {
        return windowHeight - HEADER - TABS - FOOTER - 4;
    }

    private int columns() {
        return Math.max(1, (windowWidth - PAD * 2 + GAP) / (CARD_MIN_WIDTH + GAP));
    }

    private int cardWidth() {
        int columns = columns();
        return (windowWidth - PAD * 2 - GAP * (columns - 1)) / columns;
    }

    private void drawCards(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        List<BenchPayloads.Entry> shown = shown();
        int columns = columns();
        int cardWidth = cardWidth();
        int y0 = gridTop();
        int h = gridHeight();
        int rows = (shown.size() + columns - 1) / columns;
        int content = rows * (CARD_HEIGHT + GAP) - GAP;
        scroll = Mth.clamp(scroll, 0, Math.max(0, content - h));
        if (shown.isEmpty()) {
            Draw.text(g, font, "No recipes yet.", left + PAD, y0 + 8, Theme.MUTED);
            return;
        }
        g.enableScissor(left + PAD - 2, y0, left + windowWidth - PAD + 2, y0 + h);
        for (int i = 0; i < shown.size(); i++) {
            int cx = left + PAD + (i % columns) * (cardWidth + GAP);
            int cy = y0 + (i / columns) * (CARD_HEIGHT + GAP) - (int) Math.round(scroll);
            if (cy + CARD_HEIGHT < y0 || cy > y0 + h) {
                continue;
            }
            boolean hovered = inside(mouseX, mouseY, cx, cy, cardWidth, CARD_HEIGHT) && mouseY >= y0 && mouseY < y0 + h;
            drawCard(g, shown.get(i), cx, cy, cardWidth, hovered);
            if (hovered) {
                Draw.queueTooltip(tooltip(shown.get(i)), nameColor(shown.get(i).result()));
                g.requestCursor(CursorTypes.POINTING_HAND);
            }
        }
        g.disableScissor();
        if (content > h) {
            Draw.scrollbar(g, left + windowWidth - PAD + 4, y0, h, content, scroll, Theme.DIM);
        }
    }

    private void drawCard(GuiGraphicsExtractor g, BenchPayloads.Entry entry, int x, int y, int w, boolean hovered) {
        ItemStack result = entry.result();
        int nameColor = nameColor(result);
        int craftable = entry.craftable();
        Draw.panel(g, x, y, w, CARD_HEIGHT, hovered ? Theme.CARD_HOVER : Theme.CARD,
                hovered ? Draw.mix(Theme.CARD_BORDER, Theme.ACCENT, 0.7f) : craftable > 0 ? Draw.mix(Theme.CARD_BORDER, Theme.GREEN, 0.35f) : Theme.CARD_BORDER);
        Draw.rounded(g, x + w / 2 - 15, y + 5, 30, 28, Draw.withAlpha(nameColor, hovered ? 0.18f : 0.09f));
        int size = 24;
        Draw.item(g, result, x + (w - size) / 2, y + 7, 1.5f);
        if (result.getCount() > 1) {
            Draw.rightText(g, font, "x" + result.getCount(), x + w - 4, y + 4, Theme.MUTED);
        }
        if (craftable > 0) {
            Draw.text(g, font, "✔", x + 4, y + 4, Theme.GREEN);
        }
        if (!entry.unlocked()) {
            Draw.rounded(g, x + 1, y + 1, w - 2, CARD_HEIGHT - 2, Draw.withAlpha(0xFF05070B, 0.5f));
        }
        Draw.fit(g, font, Component.literal(result.getHoverName().getString()), x + w / 2, y + 37, w - 8, Draw.Align.CENTER,
                entry.unlocked() ? nameColor : Theme.DIM);
        if (!entry.unlocked()) {
            Draw.fit(g, font, Component.literal("Needs " + entry.requirement()), x + w / 2, y + 50, w - 8, Draw.Align.CENTER, Theme.RED);
        } else {
            drawIngredients(g, entry, x + w / 2, y + 48, w - 8);
        }
    }

    /** Each ingredient as a small icon and its count, centred; red counts are ones you're short of. */
    private void drawIngredients(GuiGraphicsExtractor g, BenchPayloads.Entry entry, int centerX, int y, int maxWidth) {
        List<String> counts = new ArrayList<>();
        int width = 0;
        for (ItemStack ingredient : entry.ingredients()) {
            String text = Draw.compactNumbers(Msg.number(ingredient.getCount()));
            counts.add(text);
            width += 11 + font.width(text) + 4;
        }
        width -= 4;
        float scale = width > maxWidth ? Math.max(0.5f, maxWidth / (float) width) : 1f;
        g.pose().pushMatrix();
        g.pose().translate(centerX - width * scale / 2f, y);
        g.pose().scale(scale, scale);
        int x = 0;
        for (int i = 0; i < entry.ingredients().size(); i++) {
            ItemStack ingredient = entry.ingredients().get(i);
            Draw.item(g, ingredient, x, 0, 0.625f);
            boolean enough = entry.have().get(i) >= ingredient.getCount();
            g.text(font, counts.get(i), x + 11, 2, enough ? Theme.TEXT : Theme.RED, true);
            x += 11 + font.width(counts.get(i)) + 4;
        }
        g.pose().popMatrix();
    }

    private List<Component> tooltip(BenchPayloads.Entry entry) {
        List<Component> lines = new ArrayList<>(Screen.getTooltipFromItem(minecraft, entry.result()));
        lines.add(Component.empty());
        lines.add(Component.literal("Ingredients").withColor(Theme.MUTED));
        for (int i = 0; i < entry.ingredients().size(); i++) {
            ItemStack ingredient = entry.ingredients().get(i);
            int have = entry.have().get(i);
            lines.add(Component.literal("  " + Msg.number(ingredient.getCount()) + "× ").withColor(Theme.TEXT)
                    .append(ingredient.getHoverName().copy().withColor(nameColor(ingredient)))
                    .append(Component.literal("  (you have " + Msg.number(have) + ")").withColor(have >= ingredient.getCount() ? Theme.GREEN : Theme.RED)));
        }
        if (!entry.requirement().isEmpty()) {
            lines.add(Component.literal("Requires " + entry.requirement()).withColor(entry.unlocked() ? Theme.GREEN : Theme.RED));
        }
        int craftable = entry.craftable();
        lines.add(Component.empty());
        if (craftable > 0) {
            lines.add(Component.literal("Click").withColor(Theme.ACCENT).append(Component.literal("  craft one").withColor(Theme.MUTED)));
            if (craftable > 1) {
                lines.add(Component.literal("Shift-click").withColor(Theme.ACCENT)
                        .append(Component.literal("  craft " + Math.min(craftable, 64)).withColor(Theme.MUTED)));
            }
        } else if (entry.unlocked()) {
            lines.add(Component.literal("You don't have enough yet").withColor(Theme.RED));
        }
        return lines;
    }

    private static int nameColor(ItemStack stack) {
        TextColor color = stack.getHoverName().getStyle().getColor();
        return color != null ? 0xFF000000 | color.getValue() : Theme.TEXT;
    }

    private static boolean inside(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (event.button() != InputConstants.MOUSE_BUTTON_LEFT) {
            return super.mouseClicked(event, doubleClick);
        }
        int tx = left + PAD;
        int ty = top + HEADER;
        for (String name : tabs()) {
            int w = font.width(name.equals(ALL) ? "All" : label(name)) + 12;
            if (inside(event.x(), event.y(), tx, ty, w, TABS - 4)) {
                tab = name;
                scroll = 0;
                click(1.2f);
                return true;
            }
            tx += w + 4;
        }
        List<BenchPayloads.Entry> shown = shown();
        int columns = columns();
        int cardWidth = cardWidth();
        int y0 = gridTop();
        if (event.y() >= y0 && event.y() < y0 + gridHeight()) {
            for (int i = 0; i < shown.size(); i++) {
                int cx = left + PAD + (i % columns) * (cardWidth + GAP);
                int cy = y0 + (i / columns) * (CARD_HEIGHT + GAP) - (int) Math.round(scroll);
                if (inside(event.x(), event.y(), cx, cy, cardWidth, CARD_HEIGHT)) {
                    BenchPayloads.Entry entry = shown.get(i);
                    int times = event.hasShiftDown() ? Math.max(1, Math.min(entry.craftable(), 64)) : 1;
                    ClientPlayNetworking.send(new BenchPayloads.Craft(entry.id(), times));
                    click(1.0f);
                    return true;
                }
            }
        }
        return super.mouseClicked(event, doubleClick);
    }

    private void click(float pitch) {
        minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK.value(), pitch, 0.4f));
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        scroll -= scrollY * 24;
        return true;
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (minecraft.options.keyInventory.matches(event)) {
            onClose();
            return true;
        }
        return super.keyPressed(event);
    }
}
