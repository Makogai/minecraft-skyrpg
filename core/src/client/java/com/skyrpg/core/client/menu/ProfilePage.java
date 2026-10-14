package com.skyrpg.core.client.menu;

import com.skyrpg.core.SkyRpgCore;
import com.skyrpg.core.client.ui.Draw;
import com.skyrpg.core.client.ui.Theme;
import java.util.List;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.util.Util;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * The first page: your character, and a tile for every stat other mods register. The tiles fill as many columns
 * as the window has room for (one to three) and scroll smoothly when there are more than fit; a value too long for
 * its tile is shortened ("1.23B") and shown in full on hover.
 */
public final class ProfilePage implements MenuPage {
    private static final int CHARACTER_MIN = 90;
    private static final int CHARACTER_MAX = 150;
    private static final int TILE_HEIGHT = 34;
    private static final int MIN_TILE_WIDTH = 104;
    private static final int MAX_COLUMNS = 3;
    private static final int GAP = 6;
    private static final int TEXT_X = 26;
    private static final int HEADING = 16;
    private static final int FADE = Theme.WINDOW;

    private double scroll;
    private double scrollTarget;
    private long lastFrame = Util.getMillis();

    @Override
    public Identifier id() {
        return SkyRpgCore.id("profile");
    }

    @Override
    public Component title() {
        return Component.literal("Profile");
    }

    @Override
    public Component subtitle() {
        return Component.literal("Your character at a glance");
    }

    @Override
    public ItemStack icon() {
        return new ItemStack(Items.PLAYER_HEAD);
    }

    @Override
    public int order() {
        return 0;
    }

    @Override
    public void opened() {
        scroll = scrollTarget = 0;
    }

    /** The card is about as wide as it is tall (room for a pet and a shield beside the player), 60-70% of the page. */
    private static int cardHeight(PageContext ctx) {
        return Mth.clamp(Math.round(ctx.height() * 0.68f), Math.min(ctx.height(), 96), ctx.height());
    }

    private static int characterWidth(PageContext ctx) {
        int byHeight = Math.round(cardHeight(ctx) * 0.95f);
        return Mth.clamp(byHeight, CHARACTER_MIN, Math.max(CHARACTER_MIN, Math.min(CHARACTER_MAX, ctx.width() * 2 / 5)));
    }

    /** A strip across the top of the page that a mod can add (the progress mod shows the SkyRPG Level and its XP bar). */
    public interface Header {
        int height();

        void draw(PageContext ctx);
    }

    private static Header header;

    public static void setHeader(Header header) {
        ProfilePage.header = header;
    }

    @Override
    public void render(PageContext full) {
        PageContext ctx = full;
        if (header != null) {
            int headerHeight = header.height();
            header.draw(new PageContext(full.graphics(), full.font(), full.player(), full.x(), full.y(), full.width(), headerHeight,
                    full.mouseX(), full.mouseY(), full.openedAtMillis()));
            ctx = new PageContext(full.graphics(), full.font(), full.player(), full.x(), full.y() + headerHeight + GAP, full.width(),
                    Math.max(40, full.height() - headerHeight - GAP), full.mouseX(), full.mouseY(), full.openedAtMillis());
        }
        GuiGraphicsExtractor g = ctx.graphics();
        long now = Util.getMillis();
        float frameSeconds = Math.min((now - lastFrame) / 1000f, 0.1f);
        lastFrame = now;
        int x = ctx.x();
        int y = ctx.y();
        int characterWidth = characterWidth(ctx);
        int cardHeight = cardHeight(ctx);

        // Character card with a live model that looks at the mouse. The model is sized from the card so the
        // player (about 1.8 blocks tall), a pet floating at the shoulder and a shield all fit with margins.
        Draw.panel(g, x, y, characterWidth, cardHeight, Theme.CARD, Theme.CARD_BORDER);
        g.fill(x + 1, y + 1, x + characterWidth - 1, y + 2, Draw.withAlpha(Theme.ACCENT, 0.6f));
        int boxW = characterWidth - 8;
        int boxH = cardHeight - 14;
        int size = Math.max(12, Math.min(Math.round(boxH / 2.3f), Math.round(boxW / 2.6f)));
        Draw.entityFollowsMouse(g, x + 4, y + 8, x + characterWidth - 4, y + cardHeight - 6, size, ctx.mouseX(), ctx.mouseY(), ctx.player());

        // Under the card: the name and a few of the stats, so the column isn't an empty pillar.
        int textY = y + cardHeight + 6;
        int textX = x + 4;
        int textWidth = characterWidth - 8;
        if (textY + 9 <= y + ctx.height()) {
            Draw.fit(g, ctx.font(), ctx.player().getName(), x + characterWidth / 2, textY, textWidth, Draw.Align.CENTER, Theme.TEXT);
            List<ProfileStat> summary = SkyRpgMenu.profileStats();
            int lineY = textY + 13;
            for (int i = 0; i < summary.size() && lineY + 9 <= y + ctx.height(); i++, lineY += 11) {
                ProfileStat stat = summary.get(i);
                String value = stat.value().apply(ctx.player());
                int valueWidth = Math.min(ctx.font().width(value), textWidth / 2);
                Draw.fitText(g, ctx.font(), stat.symbol() + " " + stat.label(), textX, lineY, textWidth - valueWidth - 4, Theme.MUTED);
                Draw.fitNumber(g, ctx.font(), value, textX + textWidth - valueWidth, lineY, valueWidth, Draw.Align.RIGHT, stat.color());
            }
        }

        // Stat tiles under a small heading, as many columns as fit, sliding in one after another; they scroll if needed.
        List<ProfileStat> stats = SkyRpgMenu.profileStats();
        int gridX = x + characterWidth + GAP + 4;
        int gridWidth = ctx.width() - characterWidth - GAP - 4;
        Draw.text(g, ctx.font(), "STATS", gridX + 2, y + 4, Theme.DIM);
        int lineX = gridX + 2 + ctx.font().width("STATS") + 6;
        g.fill(lineX, y + 8, gridX + gridWidth, y + 9, Theme.BORDER);

        int areaY = y + HEADING;
        int areaHeight = ctx.height() - HEADING;
        int columns = Mth.clamp((gridWidth + GAP) / (MIN_TILE_WIDTH + GAP), 1, MAX_COLUMNS);
        int tileWidth = (gridWidth - GAP * (columns - 1)) / columns;
        int rows = (stats.size() + columns - 1) / columns;
        int content = rows * (TILE_HEIGHT + GAP) - GAP;
        double maxScroll = Math.max(0, content - areaHeight);
        scrollTarget = Mth.clamp(scrollTarget, 0, maxScroll);
        scroll = scroll + (scrollTarget - scroll) * Math.min(1, frameSeconds * 16);
        if (Math.abs(scrollTarget - scroll) < 0.3) {
            scroll = scrollTarget;
        }
        boolean mouseInArea = ctx.mouseY() >= areaY && ctx.mouseY() < areaY + areaHeight;

        g.enableScissor(gridX, areaY, gridX + gridWidth, areaY + areaHeight);
        for (int i = 0; i < stats.size(); i++) {
            ProfileStat stat = stats.get(i);
            float appear = Draw.easeOut((ctx.age() - i * 0.04f) / 0.25f);
            int tx = gridX + (i % columns) * (tileWidth + GAP);
            int ty = areaY + (i / columns) * (TILE_HEIGHT + GAP) - (int) Math.round(scroll) + Math.round((1 - appear) * 5);
            if (ty + TILE_HEIGHT < areaY || ty > areaY + areaHeight) {
                continue;
            }
            boolean hovered = mouseInArea && ctx.hovered(tx, ty, tileWidth, TILE_HEIGHT);
            drawTile(ctx, stat, tx, ty, tileWidth, hovered);
        }
        g.disableScissor();
        if (scroll > 0.5) {
            g.fillGradient(gridX, areaY, gridX + gridWidth, areaY + 8, FADE, FADE & 0x00FFFFFF);
        }
        if (scroll < maxScroll - 0.5) {
            g.fillGradient(gridX, areaY + areaHeight - 8, gridX + gridWidth, areaY + areaHeight, FADE & 0x00FFFFFF, FADE);
        }
        if (content > areaHeight) {
            Draw.scrollbar(g, ctx.x() + ctx.width() + 4, areaY, areaHeight, content, scroll, Theme.DIM);
        }
        if (stats.isEmpty()) {
            Draw.fit(g, ctx.font(), Component.literal("No stats yet"), gridX + gridWidth / 2, areaY + 30, gridWidth - 8,
                    Draw.Align.CENTER, Theme.DIM);
        }
    }

    private static void drawTile(PageContext ctx, ProfileStat stat, int tx, int ty, int tileWidth, boolean hovered) {
        GuiGraphicsExtractor g = ctx.graphics();
        Draw.panel(g, tx, ty, tileWidth, TILE_HEIGHT, hovered ? Theme.CARD_HOVER : Theme.CARD,
                hovered ? Draw.mix(Theme.CARD_BORDER, stat.color(), 0.6f) : Theme.CARD_BORDER);
        g.fill(tx + 1, ty + 6, tx + 3, ty + TILE_HEIGHT - 6, Draw.withAlpha(stat.color(), 0.9f));
        Draw.scaledText(g, ctx.font(), Component.literal(stat.symbol()), tx + 9, ty + 10, 1.5f, stat.color());
        int room = tileWidth - TEXT_X - 6;
        String value = stat.value().apply(ctx.player());
        boolean cut = Draw.fitText(g, ctx.font(), stat.label(), tx + TEXT_X, ty + 7, room, Theme.MUTED);
        cut |= Draw.fitNumber(g, ctx.font(), value, tx + TEXT_X, ty + 19, room, Draw.Align.LEFT, Theme.TEXT);
        if (hovered && cut) {
            ctx.tooltip(List.of(Component.literal(stat.symbol() + " " + stat.label()).withColor(stat.color()),
                    Component.literal(value).withColor(Theme.TEXT)), stat.color());
        }
    }

    @Override
    public boolean mouseScrolled(PageContext ctx, double x, double y, double amount) {
        scrollTarget -= amount * 32;
        return true;
    }
}
