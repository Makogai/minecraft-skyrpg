package com.skyrpg.core.client.combat;

import com.skyrpg.core.SkyRpgCore;
import com.skyrpg.core.client.hud.HudAnchor;
import com.skyrpg.core.client.hud.HudManager;
import com.skyrpg.core.client.hud.HudWidget;
import com.skyrpg.core.client.ui.Draw;
import com.skyrpg.core.client.ui.Theme;
import com.skyrpg.core.combat.CombatTagPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Util;

/**
 * "In combat 7s" while the server says the player is combat-tagged (see CombatTag in core). The server sends the time
 * left whenever it changes; this counts down from there.
 */
public final class CombatTagWidget implements HudWidget {
    private static final int HEIGHT = 16;
    /** Millis (Util.getMillis) the tag runs out at; 0 = not in combat. */
    private static long deadline;

    public static void init() {
        ClientPlayNetworking.registerGlobalReceiver(CombatTagPayload.TYPE,
                (payload, context) -> deadline = payload.ticksLeft() <= 0 ? 0 : Util.getMillis() + payload.ticksLeft() * 50L);
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> deadline = 0);
        HudManager.register(new CombatTagWidget());
    }

    private static int secondsLeft() {
        long left = deadline - Util.getMillis();
        return left <= 0 ? 0 : (int) ((left + 999) / 1000);
    }

    private static String label(boolean editing) {
        return "In combat " + (editing ? 10 : secondsLeft()) + "s";
    }

    @Override
    public Identifier id() {
        return SkyRpgCore.id("combat_tag");
    }

    @Override
    public Component name() {
        return Component.literal("Combat tag");
    }

    @Override
    public int width(Font font) {
        return font.width(label(true)) + 16;
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
        return 32;
    }

    @Override
    public boolean visible() {
        return secondsLeft() > 0;
    }

    @Override
    public void render(GuiGraphicsExtractor g, Font font, boolean editing) {
        int seconds = editing && secondsLeft() == 0 ? 10 : secondsLeft();
        // Blinks once the end is near, so it is clear when it is safe to warp.
        boolean blink = seconds <= 3 && (Util.getMillis() / 250) % 2 == 0 && !editing;
        int accent = blink ? Draw.lighten(Theme.RED, 0.4f) : Theme.RED;
        int width = width(font);
        Draw.rounded(g, 0, 0, width, HEIGHT, 0xB00C0F16);
        g.fill(1, 1, 3, HEIGHT - 1, accent);
        Draw.text(g, font, "In combat " + seconds + "s", 9, 4, Theme.TEXT);
    }
}
