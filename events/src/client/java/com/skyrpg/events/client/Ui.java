package com.skyrpg.events.client;

import com.skyrpg.core.client.ui.Draw;
import com.skyrpg.core.client.ui.Theme;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.PlayerFaceExtractor;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.player.PlayerSkin;
import net.minecraft.world.item.ItemStack;

/** Small drawing and sound helpers shared by the event widgets and overlays. */
final class Ui {
    static final int GOLD = Theme.ACCENT;
    static final int SILVER = 0xFFC3CCD8;
    static final int BRONZE = 0xFFD0874F;
    /** Item stacks can't be made while mods initialise, so icons are made on first draw and kept. */
    private static final Map<Identifier, ItemStack> ICONS = new HashMap<>();

    private Ui() {
    }

    static int medal(int place) {
        return switch (place) {
            case 1 -> GOLD;
            case 2 -> SILVER;
            case 3 -> BRONZE;
            default -> Theme.MUTED;
        };
    }

    static ItemStack icon(Identifier item) {
        return ICONS.computeIfAbsent(item, id -> new ItemStack(BuiltInRegistries.ITEM.getValue(id)));
    }

    /** A player's face, from the tab list if they're online, otherwise the default skin for their UUID. */
    static void face(GuiGraphicsExtractor g, UUID player, int x, int y, int size, int tint) {
        PlayerSkin skin = null;
        if (Minecraft.getInstance().getConnection() != null) {
            PlayerInfo info = Minecraft.getInstance().getConnection().getPlayerInfo(player);
            if (info != null) {
                skin = info.getSkin();
            }
        }
        PlayerFaceExtractor.extractRenderState(g, skin != null ? skin : DefaultPlayerSkin.get(player), x, y, size, tint);
    }

    static boolean isSelf(UUID player) {
        Minecraft minecraft = Minecraft.getInstance();
        return minecraft.player != null && minecraft.player.getUUID().equals(player);
    }

    /** Cuts text to fit {@code width}, ending in "…" if it had to. */
    static String fit(Font font, String text, int width) {
        return Draw.ellipsize(font, text, width);
    }

    static void centered(GuiGraphicsExtractor g, Font font, Component text, int centerX, int y, int color) {
        g.text(font, text, centerX - font.width(text) / 2, y, color, false);
    }

    static void centeredScaled(GuiGraphicsExtractor g, Font font, Component text, int centerX, int y, float scale, int color) {
        g.pose().pushMatrix();
        g.pose().translate(centerX - font.width(text) * scale / 2f, y);
        g.pose().scale(scale, scale);
        g.text(font, text, 0, 0, color, false);
        g.pose().popMatrix();
    }

    /** "2:05" */
    static String clock(long millis) {
        long seconds = (millis + 999) / 1000;
        return seconds >= 3600
                ? String.format("%d:%02d:%02d", seconds / 3600, seconds / 60 % 60, seconds % 60)
                : String.format("%d:%02d", seconds / 60, seconds % 60);
    }

    /** Ease-out with a little overshoot, for things that pop or rise into place. */
    static float easeOutBack(float t) {
        float c = Math.max(0, Math.min(1, t)) - 1;
        return 1 + 2.70158f * c * c * c + 1.70158f * c * c;
    }

    static void play(SoundEvent sound, float pitch, float volume) {
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(sound, pitch, volume));
    }

    /** Plays a sound by id; ids that aren't registered (resource pack sounds) still work. */
    static void play(Identifier sound, float pitch, float volume) {
        play(BuiltInRegistries.SOUND_EVENT.getOptional(sound).orElseGet(() -> SoundEvent.createVariableRangeEvent(sound)), pitch, volume);
    }
}
