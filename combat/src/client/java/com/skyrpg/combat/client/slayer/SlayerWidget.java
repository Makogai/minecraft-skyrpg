package com.skyrpg.combat.client.slayer;

import com.skyrpg.combat.SkyRpgCombat;
import com.skyrpg.combat.slayer.SlayerPayloads;
import com.skyrpg.core.client.hud.HudAnchor;
import com.skyrpg.core.client.hud.HudWidget;
import com.skyrpg.core.client.ui.Draw;
import com.skyrpg.core.client.ui.Theme;
import com.skyrpg.core.text.Msg;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Util;

/**
 * The slayer quest on the HUD: the line and tier, then the kill bar filling, or (with the boss up) "Slay the boss!" and
 * a clock that turns red in the last thirty seconds.
 */
final class SlayerWidget implements HudWidget {
    private static final int WIDTH = 132;
    private static final int HEIGHT = 34;
    private static final SlayerPayloads.Status SAMPLE = new SlayerPayloads.Status("Grave Tyrant", 0xFF7FBF5F, 2, 240, 400, false, 0);

    static SlayerPayloads.Status status = SlayerPayloads.Status.NONE;

    @Override
    public Identifier id() {
        return SkyRpgCombat.id("slayer_quest");
    }

    @Override
    public Component name() {
        return Component.literal("Slayer Quest");
    }

    @Override
    public int width(Font font) {
        return WIDTH;
    }

    @Override
    public int height(Font font) {
        return HEIGHT;
    }

    @Override
    public HudAnchor defaultAnchor() {
        return HudAnchor.TOP_LEFT;
    }

    @Override
    public int defaultX() {
        return 4;
    }

    @Override
    public int defaultY() {
        return 64;
    }

    @Override
    public boolean visible() {
        return !status.name().isEmpty();
    }

    @Override
    public void render(GuiGraphicsExtractor g, Font font, boolean editing) {
        SlayerPayloads.Status shown = status.name().isEmpty() ? SAMPLE : status;
        int color = 0xFF000000 | shown.color();
        Draw.rounded(g, 0, 0, WIDTH, HEIGHT, 0xE0121620);
        g.fill(0, 2, 2, HEIGHT - 2, color);
        Draw.fitText(g, font, "☠ " + shown.name() + " " + Msg.roman(shown.tier()), 7, 4, WIDTH - 12, color);
        if (shown.bossUp()) {
            int seconds = shown.secondsLeft();
            boolean hurry = seconds <= 30;
            boolean blink = hurry && (Util.getMillis() / 400) % 2 == 0;
            Draw.text(g, font, "Slay the boss!", 7, 16, Theme.RED);
            Draw.rightText(g, font, seconds / 60 + ":" + String.format("%02d", seconds % 60), WIDTH - 5, 16,
                    blink ? Theme.TEXT : hurry ? Theme.RED : Theme.ACCENT);
            Draw.bar(g, 7, 26, WIDTH - 12, 5, 1f, Theme.RED);
        } else {
            float progress = shown.needed() <= 0 ? 0 : Math.min(1f, shown.progress() / (float) shown.needed());
            Draw.text(g, font, "Kills", 7, 16, Theme.MUTED);
            Draw.rightText(g, font, Msg.number(shown.progress()) + " / " + Msg.number(shown.needed()), WIDTH - 5, 16, Theme.TEXT);
            Draw.bar(g, 7, 26, WIDTH - 12, 5, progress, color);
        }
    }
}
