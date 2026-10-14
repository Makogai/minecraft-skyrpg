package com.skyrpg.welcome.client;

import com.skyrpg.core.client.ui.Draw;
import com.skyrpg.core.client.ui.Theme;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.util.Util;

/** The look behind the welcome screens: a navy tint over the panorama (or the blurred world), a vignette and drifting stars. */
final class Backdrop {
    private static final int STARS = 46;

    private Backdrop() {
    }

    /** Everything that goes on top of the panorama. {@code dark} 0..1 is how much of the picture is hidden. */
    static void draw(GuiGraphicsExtractor g, int width, int height, float dark) {
        // Navy wash, darker at the bottom, and a slight gold haze at the very top.
        g.fillGradient(0, 0, width, height, Draw.withAlpha(0xFF0A1020, 0.50f * dark), Draw.withAlpha(0xFF04060C, 0.86f * dark));
        g.fillGradient(0, 0, width, Math.max(1, height / 4), Draw.withAlpha(Theme.ACCENT, 0.05f * dark), 0x00000000);
        // Vignette: darker edges.
        int edge = Math.max(8, width / 8);
        for (int i = 0; i < edge; i += 2) {
            float a = (1 - i / (float) edge);
            int c = Draw.withAlpha(0xFF02040A, a * a * 0.5f * dark);
            g.fill(i, 0, i + 2, height, c);
            g.fill(width - i - 2, 0, width - i, height, c);
        }
        stars(g, width, height, dark);
    }

    private static void stars(GuiGraphicsExtractor g, int width, int height, float dark) {
        double t = Util.getMillis() / 1000.0;
        for (int i = 0; i < STARS; i++) {
            long h = hash(i);
            float x0 = (h & 0xFFFF) / 65535f;
            float y0 = ((h >> 16) & 0xFFFF) / 65535f;
            float speed = 2.5f + ((h >> 32) & 0xFF) / 255f * 7f;
            int size = (h >> 40 & 7) == 0 ? 2 : 1;
            boolean gold = (h >> 44 & 3) == 0;
            double phase = ((h >> 48) & 0xFF) / 255.0 * Math.PI * 2;
            float y = (float) ((y0 * height - t * speed) % height);
            if (y < 0) {
                y += height;
            }
            float x = (float) ((x0 * width + Math.sin(t * 0.25 + phase) * 6) % width);
            float twinkle = 0.35f + 0.65f * (float) (0.5 + 0.5 * Math.sin(t * (0.8 + speed * 0.12) + phase));
            // Fade out near the top and bottom so stars never pop at the edges.
            float edge = Math.min(1, Math.min(y, height - y) / 24f);
            int color = Draw.withAlpha(gold ? Theme.ACCENT : 0xFFDDE6FF, twinkle * edge * 0.75f * dark);
            g.fill((int) x, (int) y, (int) x + size, (int) y + size, color);
            if (size == 2 && twinkle > 0.8f) {
                int soft = Draw.withAlpha(color, 0.35f);
                g.fill((int) x - 1, (int) y, (int) x, (int) y + 2, soft);
                g.fill((int) x + 2, (int) y, (int) x + 3, (int) y + 2, soft);
                g.fill((int) x, (int) y - 1, (int) x + 2, (int) y, soft);
                g.fill((int) x, (int) y + 2, (int) x + 2, (int) y + 3, soft);
            }
        }
    }

    private static long hash(long i) {
        long z = i * 0x9E3779B97F4A7C15L + 0x1234567L;
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        return z ^ (z >>> 31);
    }
}
