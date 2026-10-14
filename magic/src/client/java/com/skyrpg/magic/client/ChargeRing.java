package com.skyrpg.magic.client;

import com.skyrpg.core.client.ui.Draw;
import com.skyrpg.magic.SpellDef;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/** The ring around the crosshair that fills while a chargeable spell is charging, and pulses once it is full. */
final class ChargeRing {
    private static final int RADIUS = 15;
    private static final int POINTS = 56;

    private ChargeRing() {
    }

    static void init() {
        HudElementRegistry.attachElementAfter(VanillaHudElements.CROSSHAIR, com.skyrpg.magic.SkyRpgMagic.id("charge_ring"), ChargeRing::render);
    }

    private static void render(GuiGraphicsExtractor g, DeltaTracker deltaTracker) {
        SpellDef spell = ClientMagic.charging();
        Minecraft minecraft = Minecraft.getInstance();
        if (spell == null || minecraft.player == null || minecraft.gui.hud.isHidden()) {
            return;
        }
        float progress = ClientMagic.chargeProgress(deltaTracker.getGameTimeDeltaPartialTick(false));
        int cx = g.guiWidth() / 2;
        int cy = g.guiHeight() / 2;
        int color = spell.element().color();
        boolean full = progress >= 1f;
        float pulse = full ? 0.5f + 0.5f * (float) Math.sin((ClientMagic.now() + deltaTracker.getGameTimeDeltaPartialTick(false)) * 0.9) : 0;
        int radius = RADIUS + (full ? Math.round(pulse * 2) : 0);
        for (int i = 0; i < POINTS; i++) {
            double angle = -Math.PI / 2 + i * 2 * Math.PI / POINTS;
            int x = cx + (int) Math.round(Math.cos(angle) * radius);
            int y = cy + (int) Math.round(Math.sin(angle) * radius);
            boolean filled = i < progress * POINTS;
            int c = filled ? Draw.mix(color, 0xFFFFFFFF, full ? 0.4f + 0.4f * pulse : 0.15f) : 0x66000000;
            g.fill(x - 1, y - 1, x + 1, y + 1, c);
        }
        if (full) {
            // A soft glow inside the full ring.
            g.fill(cx - 2, cy - 2, cx + 2, cy + 2, Draw.withAlpha(color, 0.35f + 0.3f * pulse));
        }
    }
}
