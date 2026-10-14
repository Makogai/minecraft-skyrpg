package com.skyrpg.progress.client;

import com.skyrpg.core.client.menu.PageContext;
import com.skyrpg.core.client.menu.ProfilePage;
import com.skyrpg.core.client.ui.Draw;
import com.skyrpg.core.client.ui.Theme;
import com.skyrpg.core.stats.StatTypes;
import com.skyrpg.core.text.Msg;
import com.skyrpg.progress.collections.SkyRpgLevel;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/** The strip on top of the Profile page: "SkyRPG Level 12" and the XP bar to the next level. */
final class LevelHeader implements ProfilePage.Header {
    private static final int HEIGHT = 20;

    @Override
    public int height() {
        return HEIGHT;
    }

    @Override
    public void draw(PageContext ctx) {
        GuiGraphicsExtractor g = ctx.graphics();
        int x = ctx.x();
        int y = ctx.y();
        int w = ctx.width();
        Draw.panel(g, x, y, w, HEIGHT, Theme.CARD, Theme.CARD_BORDER);
        int level = SkyRpgLevel.level(ctx.player());
        int into = SkyRpgLevel.xpIntoLevel(ctx.player());
        String title = StatTypes.LEVEL_ICON + " SkyRPG Level " + level;
        int titleWidth = ctx.font().width(title);
        Draw.text(g, ctx.font(), title, x + 8, y + 6, Theme.AQUA);
        String xp = Msg.number(into) + " / " + SkyRpgLevel.XP_PER_LEVEL + " XP";
        int xpWidth = ctx.font().width(xp);
        Draw.rightText(g, ctx.font(), xp, x + w - 8, y + 6, Theme.MUTED);
        int barX = x + 8 + titleWidth + 10;
        int barW = w - 8 - xpWidth - 10 - (barX - x) ;
        if (barW > 20) {
            Draw.bar(g, barX, y + 7, barW, 6, into / (float) SkyRpgLevel.XP_PER_LEVEL, Theme.AQUA);
        }
    }
}
