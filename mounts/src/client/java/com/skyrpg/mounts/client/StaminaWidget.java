package com.skyrpg.mounts.client;

import com.skyrpg.core.client.hud.HudAnchor;
import com.skyrpg.core.client.hud.HudWidget;
import com.skyrpg.core.client.ui.Draw;
import com.skyrpg.core.client.ui.Theme;
import com.skyrpg.mounts.GriffinEntity;
import com.skyrpg.mounts.SkyRpgMounts;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;

/**
 * The flight stamina bar above the hotbar, shown while riding a griffin. The fill is the stamina the server syncs on the
 * griffin; it goes green to gold to red as it runs low, and flashes at the bottom.
 */
public final class StaminaWidget implements HudWidget {
    private static final int WIDTH = 120;
    private static final int HEIGHT = 18;

    private static GriffinEntity mount() {
        Minecraft minecraft = Minecraft.getInstance();
        return minecraft.player != null && minecraft.player.getVehicle() instanceof GriffinEntity griffin ? griffin : null;
    }

    @Override
    public Identifier id() {
        return SkyRpgMounts.id("stamina");
    }

    @Override
    public Component name() {
        return Component.literal("Flight stamina");
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
        return HudAnchor.BOTTOM_CENTER;
    }

    @Override
    public int defaultX() {
        return 91 + 3 + WIDTH / 2; // right of the hotbar, clear of the health and mana bars and the chat
    }

    @Override
    public int defaultY() {
        return -46;
    }

    @Override
    public boolean visible() {
        return mount() != null;
    }

    @Override
    public void render(GuiGraphicsExtractor g, Font font, boolean editing) {
        GriffinEntity griffin = mount();
        float stamina = griffin == null ? 0.7f : griffin.getStamina();
        int color = stamina > 0.5f ? Theme.GREEN : stamina > 0.2f ? Theme.ACCENT : Theme.RED;
        if (stamina < 0.1f && ((net.minecraft.util.Util.getMillis() / 250) % 2 == 0)) {
            color = Draw.lighten(Theme.RED, 0.4f);
        }
        Draw.rounded(g, 0, 0, WIDTH, HEIGHT, 0xB00C0F16);
        String label = "Stamina " + Mth.clamp(Math.round(stamina * 100f), 0, 100) + "%";
        Draw.text(g, font, label, 6, 2, Theme.TEXT);
        Draw.bar(g, 6, 12, WIDTH - 12, 4, stamina, color);
    }
}
