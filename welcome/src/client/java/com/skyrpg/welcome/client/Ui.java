package com.skyrpg.welcome.client;

import com.skyrpg.core.client.ui.Draw;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.util.Util;

/** Small drawing and animation helpers shared by the title screen and the setup wizard. */
final class Ui {
    private Ui() {
    }

    /** A soft halo of {@code color} around a box, {@code strength} 0..1. */
    static void glow(GuiGraphicsExtractor g, int x, int y, int w, int h, int color, float strength) {
        if (strength <= 0.01f) {
            return;
        }
        for (int i = 4; i >= 1; i--) {
            Draw.rounded(g, x - i, y - i, w + i * 2, h + i * 2, Draw.withAlpha(color, strength * 0.16f / i));
        }
    }

    /** Moves {@code current} towards {@code target} at a speed that does not depend on the frame rate. */
    static float approach(float current, float target, float seconds, float speed) {
        return Mth.lerp(Math.min(1, seconds * speed), current, target);
    }

    static void click(float pitch) {
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK.value(), pitch, 0.5f));
    }

    static float seconds(long since) {
        return (Util.getMillis() - since) / 1000f;
    }

    /** Opens a folder in the system file manager. */
    static void openFolder(java.nio.file.Path folder) {
        String os = System.getProperty("os.name", "").toLowerCase(java.util.Locale.ROOT);
        try {
            String command = os.contains("win") ? "explorer" : os.contains("mac") ? "open" : "xdg-open";
            new ProcessBuilder(command, folder.toString()).start();
        } catch (java.io.IOException e) {
            SkyRpgWelcomeClient.LOGGER.warn("Couldn't open {}", folder, e);
        }
    }
}
