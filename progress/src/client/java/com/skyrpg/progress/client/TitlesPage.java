package com.skyrpg.progress.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.platform.InputConstants;
import com.skyrpg.core.client.menu.MenuPage;
import com.skyrpg.core.client.menu.PageContext;
import com.skyrpg.core.client.ui.Draw;
import com.skyrpg.core.client.ui.Theme;
import com.skyrpg.core.text.Msg;
import com.skyrpg.core.text.PlayerTitles;
import com.skyrpg.progress.SkyRpgProgress;
import com.skyrpg.progress.titles.TitlePayloads;
import com.skyrpg.progress.titles.TitleState;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * The K menu's Titles page: every title with its icon and text in its colour. Earned ones are clickable (click to wear, click
 * the worn one to take it off); locked ones are dimmed and say how to earn them, with a bar where the goal is a number.
 */
public final class TitlesPage implements MenuPage {
    private static final int TAB_H = 18;
    private static final int GAP = 6;
    private static final int ROW_H = 38;
    private static final String[] FILTERS = {"All", "Earned", "Locked"};

    /** One title as the server sent it. */
    private record View(String id, String text, String icon, int color, String rarity, String category, String description, boolean hidden) {
    }

    private static List<View> views = List.of();

    private int filter;
    private double scroll;
    private int contentHeight;
    private final List<int[]> tabs = new ArrayList<>();
    private final List<Object[]> rows = new ArrayList<>();
    private int clearX;
    private int clearY;
    private int clearW;

    static void setDefs(String json) {
        List<View> list = new ArrayList<>();
        try {
            JsonArray array = JsonParser.parseString(json).getAsJsonArray();
            for (JsonElement element : array) {
                JsonObject o = element.getAsJsonObject();
                list.add(new View(o.get("id").getAsString(), o.get("text").getAsString(), o.get("icon").getAsString(), o.get("color").getAsInt(),
                        o.get("rarity").getAsString(), o.get("category").getAsString(), o.get("description").getAsString(), o.get("hidden").getAsBoolean()));
            }
        } catch (RuntimeException e) {
            SkyRpgProgress.LOGGER.warn("Unreadable title list from the server: {}", e.getMessage());
        }
        views = List.copyOf(list);
    }

    @Override
    public Identifier id() {
        return SkyRpgProgress.id("titles");
    }

    @Override
    public Component title() {
        return Component.literal("Titles");
    }

    @Override
    public Component subtitle() {
        return Component.literal("Earn titles and wear one before your name");
    }

    @Override
    public ItemStack icon() {
        return new ItemStack(Items.NAME_TAG);
    }

    @Override
    public int order() {
        return 45;
    }

    @Override
    public void opened() {
        scroll = 0;
    }

    private static Component tag(View view, int color, boolean showIcon) {
        net.minecraft.network.chat.MutableComponent out = Component.empty();
        if (showIcon && !view.icon().isEmpty()) {
            out.append(PlayerTitles.iconComponent(view.icon(), color)).append(" ");
        }
        return out.append(Component.literal(view.text()).withStyle(Style.EMPTY.withColor(TextColor.fromRgb(color)).withBold(true)));
    }

    @Override
    public void render(PageContext ctx) {
        GuiGraphicsExtractor g = ctx.graphics();
        Font font = ctx.font();
        Minecraft minecraft = Minecraft.getInstance();
        TitleState state = TitleState.of(ctx.player());
        Map<String, Integer> live = ctx.player().getAttachedOrElse(TitleState.LIVE, Map.of());
        tabs.clear();
        rows.clear();
        clearW = 0;
        if (views.isEmpty()) {
            Draw.fit(g, font, Component.literal("No titles yet"), ctx.x() + ctx.width() / 2, ctx.y() + 30, ctx.width() - 8,
                    Draw.Align.CENTER, Theme.DIM);
            return;
        }
        long earned = views.stream().filter(view -> state.unlocked().contains(view.id())).count();
        int tx = ctx.x();
        for (int i = 0; i < FILTERS.length; i++) {
            String text = FILTERS[i] + (i == 0 ? " " + earned + "/" + views.size() : "");
            int w = font.width(text) + 16;
            boolean active = i == filter;
            boolean hovered = ctx.hovered(tx, ctx.y(), w, TAB_H);
            Draw.panel(g, tx, ctx.y(), w, TAB_H, active || hovered ? Theme.CARD_HOVER : Theme.CARD, active ? Theme.ACCENT : Theme.CARD_BORDER);
            Draw.text(g, font, text, tx + 8, ctx.y() + 5, active ? Theme.ACCENT : Theme.MUTED);
            tabs.add(new int[] {tx, ctx.y(), w, TAB_H, i});
            tx += w + 4;
        }
        if (!state.active().isEmpty()) {
            String text = "Take off title";
            clearW = font.width(text) + 16;
            clearX = ctx.x() + ctx.width() - clearW;
            clearY = ctx.y();
            boolean hovered = ctx.hovered(clearX, clearY, clearW, TAB_H);
            Draw.panel(g, clearX, clearY, clearW, TAB_H, hovered ? Theme.CARD_HOVER : Theme.CARD, hovered ? Theme.RED : Theme.CARD_BORDER);
            Draw.text(g, font, text, clearX + 8, clearY + 5, hovered ? Theme.RED : Theme.MUTED);
        }

        int areaY = ctx.y() + TAB_H + GAP;
        int areaH = ctx.height() - TAB_H - GAP;
        int areaX = ctx.x();
        int areaW = ctx.width();
        scroll = Mth.clamp(scroll, 0, Math.max(0, contentHeight - areaH));
        g.enableScissor(areaX, areaY, areaX + areaW, areaY + areaH);
        int y = areaY - (int) Math.round(scroll);
        int start = y;
        for (View view : views) {
            boolean owned = state.unlocked().contains(view.id());
            if (filter == 1 && !owned || filter == 2 && owned) {
                continue;
            }
            if (y + ROW_H >= areaY && y <= areaY + areaH) {
                boolean hovered = ctx.hovered(areaX, Math.max(y, areaY), areaW, ROW_H) && ctx.mouseY() >= areaY && ctx.mouseY() < areaY + areaH;
                drawRow(ctx, view, owned, view.id().equals(state.active()), live, areaX, y, areaW - 6, hovered);
                rows.add(new Object[] {areaX, Math.max(y, areaY), areaW, Math.min(ROW_H, areaY + areaH - Math.max(y, areaY)), view, owned});
            }
            y += ROW_H + 4;
        }
        contentHeight = y - start;
        g.disableScissor();
        if (contentHeight > areaH) {
            Draw.scrollbar(g, areaX + areaW - 3, areaY, areaH, contentHeight, scroll, Theme.DIM);
        }
    }

    private void drawRow(PageContext ctx, View view, boolean owned, boolean worn, Map<String, Integer> live, int x, int y, int w, boolean hovered) {
        GuiGraphicsExtractor g = ctx.graphics();
        Font font = ctx.font();
        int accent = 0xFF000000 | view.color();
        int border = worn ? Theme.GREEN : hovered ? Draw.mix(Theme.CARD_BORDER, accent, 0.6f) : Theme.CARD_BORDER;
        Draw.panel(g, x, y, w, ROW_H, hovered && owned ? Theme.CARD_HOVER : Theme.CARD, border);
        g.fill(x + 1, y + 5, x + 3, y + ROW_H - 5, Draw.withAlpha(accent, owned ? 0.9f : 0.3f));

        boolean secret = view.hidden() && !owned;
        int textX = x + 10;
        Component shown = secret ? Component.literal("???").withStyle(Style.EMPTY.withColor(TextColor.fromRgb(0x586174)).withBold(true))
                : tag(view, owned ? view.color() : 0x7A8296, true);
        String status = worn ? "WEARING" : owned ? "Click to wear" : capital(view.rarity());
        int statusColor = worn ? Theme.GREEN : owned ? Theme.ACCENT : Draw.withAlpha(accent, 0.7f);
        int statusW = font.width(status);
        Draw.fitText(g, font, shown, textX, y + 6, w - 20 - statusW, Theme.TEXT);
        Draw.text(g, font, status, x + w - 8 - statusW, y + 6, statusColor);

        String desc = secret ? "A hidden title. Keep playing to find out." : view.description();
        Draw.fitText(g, font, desc, textX, y + 18, w - 20, owned ? Theme.MUTED : Theme.DIM);
        Integer current = live.get(view.id());
        Integer max = live.get(view.id() + "#");
        if (!owned && !secret && current != null && max != null && max > 1) {
            int barW = Math.max(40, Math.min(w - 20 - 90, 160));
            Draw.bar(g, textX, y + 29, barW, 5, current / (float) max, accent);
            Draw.fitText(g, font, Msg.number(current) + " / " + Msg.number(max), textX + barW + 6, y + 28, w - 20 - barW - 6, Theme.MUTED);
        } else if (owned) {
            Draw.text(g, font, "Earned", textX, y + 28, Theme.GREEN);
        }
        if (hovered) {
            List<Component> lines = new ArrayList<>();
            lines.add(shown);
            lines.add(Component.literal(capital(view.rarity()) + " - " + view.category()).withColor(accent));
            for (var line : Draw.wrap(font, Component.literal(desc), 220)) {
                lines.add(Component.literal(toText(line)).withColor(Theme.MUTED));
            }
            ctx.tooltip(lines, accent);
        }
    }

    private static String toText(net.minecraft.util.FormattedCharSequence sequence) {
        StringBuilder out = new StringBuilder();
        sequence.accept((index, style, codePoint) -> {
            out.appendCodePoint(codePoint);
            return true;
        });
        return out.toString();
    }

    private static String capital(String text) {
        return text.isEmpty() ? text : Character.toUpperCase(text.charAt(0)) + text.substring(1);
    }

    private static void send(String id) {
        ClientPlayNetworking.send(new TitlePayloads.Select(id));
    }

    @Override
    public boolean mouseClicked(PageContext ctx, double x, double y, int button) {
        if (button != InputConstants.MOUSE_BUTTON_LEFT) {
            return false;
        }
        for (int[] tab : tabs) {
            if (x >= tab[0] && x < tab[0] + tab[2] && y >= tab[1] && y < tab[1] + tab[3]) {
                filter = tab[4];
                scroll = 0;
                return true;
            }
        }
        if (clearW > 0 && x >= clearX && x < clearX + clearW && y >= clearY && y < clearY + TAB_H) {
            send("");
            return true;
        }
        for (Object[] row : rows) {
            int rx = (Integer) row[0];
            int ry = (Integer) row[1];
            int rw = (Integer) row[2];
            int rh = (Integer) row[3];
            if (x >= rx && x < rx + rw && y >= ry && y < ry + rh) {
                View view = (View) row[4];
                if ((Boolean) row[5]) {
                    send(view.id().equals(TitleState.of(ctx.player()).active()) ? "" : view.id());
                }
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
