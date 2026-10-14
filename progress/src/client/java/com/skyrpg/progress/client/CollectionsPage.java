package com.skyrpg.progress.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.skyrpg.core.client.menu.MenuPage;
import com.skyrpg.core.client.menu.PageContext;
import com.skyrpg.core.client.ui.Draw;
import com.skyrpg.core.client.ui.Theme;
import com.skyrpg.core.progress.CollectionLevels;
import com.skyrpg.core.text.Msg;
import com.skyrpg.progress.SkyRpgProgress;
import com.skyrpg.progress.collections.ClientCollections;
import com.skyrpg.progress.collections.CollectionPayloads;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * The K menu's Collections page: category tabs on top, then every collection of the tab with its icon, count, tier, a bar to
 * the next tier and what that tier gives. Hover a row for all nine tiers.
 */
public final class CollectionsPage implements MenuPage {
    private static final int TAB_H = 18;
    private static final int GAP = 6;
    private static final int ROW_H = 38;
    private static final Map<String, Integer> CATEGORY_COLORS = Map.of(
            "mining", 0xFF8FB4D9, "farming", 0xFF7BD35A, "foraging", 0xFF3FB56B, "fishing", 0xFF4FB8F0, "combat", 0xFFFF5555);
    private static final List<String> CATEGORY_ORDER = List.of("mining", "farming", "foraging", "fishing", "combat");

    private String selected = "";
    private double scroll;
    private int contentHeight;
    private final List<Tab> tabs = new ArrayList<>();

    private record Tab(String category, int x, int y, int w, int h) {
    }

    @Override
    public Identifier id() {
        return SkyRpgProgress.id("collections");
    }

    @Override
    public Component title() {
        return Component.literal("Collections");
    }

    @Override
    public Component subtitle() {
        return Component.literal("Gather items to unlock rewards and recipes");
    }

    @Override
    public ItemStack icon() {
        return new ItemStack(Items.BUNDLE);
    }

    @Override
    public int order() {
        return 40;
    }

    @Override
    public void opened() {
        scroll = 0;
    }

    private static List<String> categories(List<CollectionPayloads.View> views) {
        List<String> found = new ArrayList<>();
        for (String category : CATEGORY_ORDER) {
            if (views.stream().anyMatch(view -> view.category().equals(category))) {
                found.add(category);
            }
        }
        for (CollectionPayloads.View view : views) {
            if (!found.contains(view.category())) {
                found.add(view.category());
            }
        }
        return found;
    }

    private static String label(String category) {
        return category.isEmpty() ? category : Character.toUpperCase(category.charAt(0)) + category.substring(1);
    }

    @Override
    public void render(PageContext ctx) {
        GuiGraphicsExtractor g = ctx.graphics();
        Font font = ctx.font();
        List<CollectionPayloads.View> views = ClientCollections.views();
        tabs.clear();
        if (views.isEmpty()) {
            Draw.fit(g, font, Component.literal("No collections yet"), ctx.x() + ctx.width() / 2, ctx.y() + 30, ctx.width() - 8,
                    Draw.Align.CENTER, Theme.DIM);
            return;
        }
        List<String> categories = categories(views);
        if (!categories.contains(selected)) {
            selected = categories.get(0);
        }

        int tx = ctx.x();
        for (String category : categories) {
            long done = views.stream().filter(view -> view.category().equals(category)).filter(view -> ClientCollections.tier(view.id()) >= view.thresholds().size()).count();
            long total = views.stream().filter(view -> view.category().equals(category)).count();
            String text = label(category) + " " + done + "/" + total;
            int w = font.width(text) + 16;
            boolean active = category.equals(selected);
            boolean hovered = ctx.hovered(tx, ctx.y(), w, TAB_H);
            int color = CATEGORY_COLORS.getOrDefault(category, Theme.TEXT);
            Draw.panel(g, tx, ctx.y(), w, TAB_H, active ? Theme.CARD_HOVER : hovered ? Theme.CARD_HOVER : Theme.CARD,
                    active ? color : Theme.CARD_BORDER);
            Draw.text(g, font, text, tx + 8, ctx.y() + 5, active ? color : Theme.MUTED);
            tabs.add(new Tab(category, tx, ctx.y(), w, TAB_H));
            tx += w + 4;
        }

        int areaY = ctx.y() + TAB_H + GAP;
        int areaH = ctx.height() - TAB_H - GAP;
        int areaX = ctx.x();
        int areaW = ctx.width();
        double maxScroll = Math.max(0, contentHeight - areaH);
        scroll = Mth.clamp(scroll, 0, maxScroll);
        g.enableScissor(areaX, areaY, areaX + areaW, areaY + areaH);
        int y = areaY - (int) Math.round(scroll);
        int accent = CATEGORY_COLORS.getOrDefault(selected, Theme.ACCENT);
        int start = y;
        for (CollectionPayloads.View view : views) {
            if (!view.category().equals(selected)) {
                continue;
            }
            if (y + ROW_H >= areaY && y <= areaY + areaH) {
                drawRow(ctx, view, areaX, y, areaW - 6, accent, ctx.hovered(areaX, Math.max(y, areaY), areaW, ROW_H) && ctx.mouseY() >= areaY
                        && ctx.mouseY() < areaY + areaH);
            }
            y += ROW_H + 4;
        }
        contentHeight = y - start;
        g.disableScissor();
        if (contentHeight > areaH) {
            Draw.scrollbar(g, areaX + areaW - 3, areaY, areaH, contentHeight, scroll, Theme.DIM);
        }
    }

    private void drawRow(PageContext ctx, CollectionPayloads.View view, int x, int y, int w, int accent, boolean hovered) {
        GuiGraphicsExtractor g = ctx.graphics();
        Font font = ctx.font();
        long count = ClientCollections.count(view.id());
        int tier = ClientCollections.tier(view.id());
        int max = view.thresholds().size();
        Draw.panel(g, x, y, w, ROW_H, hovered ? Theme.CARD_HOVER : Theme.CARD, hovered ? Draw.mix(Theme.CARD_BORDER, accent, 0.6f) : Theme.CARD_BORDER);
        g.fill(x + 1, y + 5, x + 3, y + ROW_H - 5, Draw.withAlpha(accent, 0.9f));
        Item icon = iconItem(view.icon());
        Draw.item(g, new ItemStack(icon), x + 8, y + 10, 1.0f);

        int textX = x + 30;
        String tierText = tier >= max ? "MAX " + CollectionLevels.roman(tier) : tier == 0 ? "Not started" : "Tier " + CollectionLevels.roman(tier);
        int tierW = font.width(tierText);
        Draw.fitText(g, font, view.name(), textX, y + 5, w - 30 - tierW - 16, Theme.TEXT);
        Draw.text(g, font, tierText, x + w - 6 - tierW, y + 5, tier >= max ? Theme.ACCENT : tier == 0 ? Theme.DIM : accent);

        long floor = tier == 0 ? 0 : view.thresholds().get(tier - 1);
        long next = tier >= max ? floor : view.thresholds().get(tier);
        float progress = tier >= max ? 1f : (count - floor) / (float) Math.max(1, next - floor);
        int barW = Math.max(40, Math.min(w - 30 - 90, 140));
        Draw.bar(g, textX, y + 17, barW, 6, progress, accent);
        String countText = tier >= max ? Msg.number(count) + " gathered" : Msg.number(count) + " / " + Msg.number(next);
        Draw.fitText(g, font, countText, textX + barW + 6, y + 16, x + w - 6 - (textX + barW + 6), Theme.MUTED);
        String reward = tier >= max ? "Collection complete" : "Next: " + view.rewards().get(tier);
        Draw.fitText(g, font, reward, textX, y + 27, w - 30 - 8, tier >= max ? Theme.ACCENT : Theme.DIM);

        if (hovered) {
            List<Component> lines = new ArrayList<>();
            lines.add(Component.literal(view.name() + " Collection").withColor(accent));
            lines.add(Component.literal(Msg.number(count) + " gathered").withColor(Theme.MUTED));
            for (int i = 0; i < max; i++) {
                boolean done = i < tier;
                lines.add(Component.literal((done ? "✔ " : "  ") + CollectionLevels.roman(i + 1) + "  " + Msg.number(view.thresholds().get(i))
                        + (view.rewards().get(i).isEmpty() ? "" : "  -  " + view.rewards().get(i))).withColor(done ? Theme.GREEN : Theme.MUTED));
            }
            ctx.tooltip(lines, accent);
        }
    }

    private static Item iconItem(Identifier id) {
        return BuiltInRegistries.ITEM.getOptional(id).orElse(Items.BARRIER);
    }

    @Override
    public boolean mouseClicked(PageContext ctx, double x, double y, int button) {
        if (button != InputConstants.MOUSE_BUTTON_LEFT) {
            return false;
        }
        for (Tab tab : tabs) {
            if (x >= tab.x() && x < tab.x() + tab.w() && y >= tab.y() && y < tab.y() + tab.h()) {
                selected = tab.category();
                scroll = 0;
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(PageContext ctx, double x, double y, double amount) {
        scroll -= amount * 24;
        return true;
    }
}
