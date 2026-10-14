package com.skyrpg.world.client;

import com.skyrpg.core.client.hud.HudAnchor;
import com.skyrpg.core.client.hud.HudWidget;
import com.skyrpg.core.client.ui.Draw;
import com.skyrpg.core.client.ui.Theme;
import com.skyrpg.world.SkyRpgWorld;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Util;

/** "Blood Moon" with the time left until dawn, at the top centre while one is on (it pulses a little). */
final class BloodMoonWidget implements HudWidget {
    private static final int WIDTH = 112;
    private static final int HEIGHT = 24;
    private static final int RED = 0xFFC8102E;

    @Override
    public Identifier id() {
        return SkyRpgWorld.id("blood_moon");
    }

    @Override
    public Component name() {
        return Component.literal("Blood Moon");
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
        return HudAnchor.TOP_CENTER;
    }

    @Override
    public int defaultX() {
        return 0;
    }

    @Override
    public int defaultY() {
        return 4;
    }

    @Override
    public boolean visible() {
        return Atmosphere.bloodMoon() && Atmosphere.moonStrength() > 0.05f;
    }

    @Override
    public void render(GuiGraphicsExtractor g, Font font, boolean editing) {
        float alpha = editing ? 1 : Atmosphere.moonStrength();
        int ticks = editing ? 6200 : Atmosphere.ticksLeft();
        int seconds = ticks / 20;
        String time = seconds / 60 + ":" + (seconds % 60 < 10 ? "0" : "") + seconds % 60;
        float pulse = 0.75f + 0.25f * (float) Math.sin(Util.getMillis() / 500.0);
        Draw.panel(g, 0, 0, WIDTH, HEIGHT, Draw.withAlpha(0xE0180810, alpha), Draw.withAlpha(Draw.mix(Theme.BORDER, RED, 0.6f), alpha));
        g.fill(2, 0, WIDTH - 2, 1, Draw.withAlpha(RED, alpha * pulse));
        g.fillGradient(1, 1, WIDTH - 1, 14, Draw.withAlpha(RED, 0.18f * alpha * pulse), 0);
        Draw.text(g, font, Component.literal("Blood Moon"), 7, 4, Draw.withAlpha(Draw.lighten(RED, 0.3f), alpha));
        Draw.text(g, font, "Dawn in " + time, 7, 14, Draw.withAlpha(Theme.MUTED, alpha));
    }
}
