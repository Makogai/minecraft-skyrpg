package com.skyrpg.stats.client;

import com.skyrpg.core.client.hud.HudAnchor;
import com.skyrpg.core.client.hud.HudWidget;
import com.skyrpg.core.client.ui.Draw;
import com.skyrpg.core.client.ui.Theme;
import com.skyrpg.core.stats.StatTypes;
import com.skyrpg.core.text.Msg;
import com.skyrpg.stats.PlayerStats;
import com.skyrpg.stats.SkyRpgStats;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Util;

/**
 * Health, mana and defense on the HUD. By default they sit where vanilla's hearts and armor row are:
 * the left half above the hotbar (the hotbar is 182 wide, so its left edge is 91 left of centre), clear
 * of the XP bar below and the food bar on the right half.
 */
final class VitalsWidgets {
    static final int BAR_WIDTH = 90;
    static final int BAR_HEIGHT = 10;
    /** Where the health and defense bars sit: high enough that the XP level number (drawn 35 px up) stays visible. */
    static final int HEALTH_ROW = -46;
    private static final int HEALTH_COLOR = 0xFFE5484D;
    private static final int ABSORPTION_COLOR = 0xFFF2B33D;
    private static final int MANA_COLOR = 0xFF3FB8E8;

    private VitalsWidgets() {
    }

    /** Only in modes where you can get hurt, like vanilla's hearts. */
    static boolean playerCanBeHurt() {
        Minecraft minecraft = Minecraft.getInstance();
        return minecraft.player != null && minecraft.gameMode != null && minecraft.gameMode.canHurtPlayer();
    }

    /** A rounded track with a coloured fill, an optional second segment and centred text. */
    private static void drawBar(GuiGraphicsExtractor g, Font font, float fill, float extra, int color, int extraColor, String text) {
        Draw.rounded(g, 0, 0, BAR_WIDTH, BAR_HEIGHT, 0xD00A0D12);
        int inner = BAR_WIDTH - 2;
        int filled = Math.round(inner * Math.max(0, Math.min(1, fill)));
        int extraWidth = Math.min(inner - filled, Math.round(inner * Math.max(0, extra)));
        if (filled > 0) {
            g.fillGradient(1, 1, 1 + filled, BAR_HEIGHT - 1, Draw.lighten(color, 0.2f), color);
            g.fill(1, 1, 1 + filled, 2, Draw.lighten(color, 0.45f));
        }
        if (extraWidth > 0) {
            g.fillGradient(1 + filled, 1, 1 + filled + extraWidth, BAR_HEIGHT - 1, Draw.lighten(extraColor, 0.2f), extraColor);
        }
        // Big numbers ("12,569/15,120 +2,000") shorten to "12.6k/15.1k +2,000", then shrink a little.
        String shown = Draw.numberFor(font, text, BAR_WIDTH - 4);
        Draw.fit(g, font, Component.literal(shown), BAR_WIDTH / 2, 1, BAR_WIDTH - 4, 1f, Draw.MIN_TEXT_SCALE, Draw.Align.CENTER, Theme.TEXT, true);
    }

    /** "❤ 140/150", red, replacing the vanilla hearts. Absorption shows as a gold extension. Pulses when low. */
    static final class Health implements HudWidget {
        @Override
        public Identifier id() {
            return SkyRpgStats.id("health_bar");
        }

        @Override
        public Component name() {
            return Component.literal("Health bar");
        }

        @Override
        public int width(Font font) {
            return BAR_WIDTH;
        }

        @Override
        public int height(Font font) {
            return BAR_HEIGHT;
        }

        @Override
        public HudAnchor defaultAnchor() {
            return HudAnchor.BOTTOM_CENTER;
        }

        @Override
        public int defaultX() {
            return -91 + BAR_WIDTH / 2; // left edge lines up with the hotbar's
        }

        @Override
        public int defaultY() {
            return HEALTH_ROW; // above the XP bar and its level number, where the hearts were
        }

        @Override
        public boolean visible() {
            return playerCanBeHurt();
        }

        @Override
        public void render(GuiGraphicsExtractor g, Font font, boolean editing) {
            LocalPlayer player = Minecraft.getInstance().player;
            if (player == null) {
                return;
            }
            double health = player.getHealth() * StatTypes.HEALTH_SCALE;
            double max = Math.max(1, player.getMaxHealth() * StatTypes.HEALTH_SCALE);
            double absorption = player.getAbsorptionAmount() * StatTypes.HEALTH_SCALE;
            int color = HEALTH_COLOR;
            if (health / max < 0.25) {
                float pulse = 0.5f + 0.5f * (float) Math.sin(Util.getMillis() / 120.0);
                color = Draw.mix(HEALTH_COLOR, 0xFFFF9A9A, pulse * 0.6f);
            }
            String text = "❤ " + Msg.number(Math.ceil(health)) + "/" + Msg.number(Math.round(max))
                    + (absorption > 0 ? " +" + Msg.number(Math.ceil(absorption)) : "");
            drawBar(g, font, (float) (health / max), (float) (absorption / max), color, ABSORPTION_COLOR, text);
        }
    }

    /** Mana "80/100", aqua, just above the health bar. */
    static final class Mana implements HudWidget {
        @Override
        public Identifier id() {
            return SkyRpgStats.id("mana_bar");
        }

        @Override
        public Component name() {
            return Component.literal("Mana bar");
        }

        @Override
        public int width(Font font) {
            return BAR_WIDTH;
        }

        @Override
        public int height(Font font) {
            return BAR_HEIGHT;
        }

        @Override
        public HudAnchor defaultAnchor() {
            return HudAnchor.BOTTOM_CENTER;
        }

        @Override
        public int defaultX() {
            return -91 + BAR_WIDTH / 2;
        }

        @Override
        public int defaultY() {
            return HEALTH_ROW - BAR_HEIGHT - 1; // vanilla's armor row, which the health bar replaces
        }

        @Override
        public boolean visible() {
            return playerCanBeHurt();
        }

        @Override
        public void render(GuiGraphicsExtractor g, Font font, boolean editing) {
            LocalPlayer player = Minecraft.getInstance().player;
            if (player == null) {
                return;
            }
            double mana = PlayerStats.mana(player);
            double max = Math.max(1, PlayerStats.maxMana(player));
            drawBar(g, font, (float) (mana / max), 0, MANA_COLOR, MANA_COLOR,
                    StatTypes.MANA_ICON + " " + Msg.number(Math.floor(mana)) + "/" + Msg.number(Math.round(max)));
        }
    }

    /** "❈ 25", a small green pill left of the health bar (above the offhand slot). */
    static final class Defense implements HudWidget {
        private static final int WIDTH = 44;
        private static final int HEIGHT = 10;

        @Override
        public Identifier id() {
            return SkyRpgStats.id("defense");
        }

        @Override
        public Component name() {
            return Component.literal("Defense");
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
            return -91 - 3 - WIDTH / 2; // right edge 3px left of the hotbar
        }

        @Override
        public int defaultY() {
            return HEALTH_ROW;
        }

        @Override
        public boolean visible() {
            return playerCanBeHurt();
        }

        @Override
        public void render(GuiGraphicsExtractor g, Font font, boolean editing) {
            LocalPlayer player = Minecraft.getInstance().player;
            if (player == null) {
                return;
            }
            Draw.rounded(g, 0, 0, WIDTH, HEIGHT, 0xD00A0D12);
            String value = Msg.number(Math.round(PlayerStats.total(player, StatTypes.DEFENSE)));
            g.text(font, StatTypes.DEFENSE.symbol(), 4, 1, StatTypes.DEFENSE.color(), true);
            // Right of the icon; "23,506" becomes "23.5k" rather than running into it.
            int room = WIDTH - 4 - (4 + font.width(StatTypes.DEFENSE.symbol()) + 2);
            Draw.fit(g, font, Component.literal(Draw.numberFor(font, value, room)), WIDTH - 4, 1, room, 1f, Draw.MIN_TEXT_SCALE,
                    Draw.Align.RIGHT, Theme.TEXT, true);
        }
    }
}
