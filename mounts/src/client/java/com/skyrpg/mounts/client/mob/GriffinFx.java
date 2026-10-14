package com.skyrpg.mounts.client.mob;

import com.skyrpg.mounts.GriffinColor;
import net.minecraft.util.Mth;

/**
 * The animated looks of the rarer griffins, as functions of time (game ticks, 20 a second): Prismatic's rainbow tint (a loop
 * every 6 seconds) and the brightness of the two glow textures (griffin_&lt;color&gt;_glow / _glow2) of Ember (pulsing eyes and
 * wing tips), Frost (steady eyes, shimmering tips), Prismatic (trim) and Astral (stars twinkling in two groups). Shared by
 * the world renderer and the K menu preview so they look the same.
 */
public final class GriffinFx {
    /** Ticks for Prismatic to go once round the rainbow (6 seconds). */
    public static final float RAINBOW_TICKS = 120f;

    private GriffinFx() {
    }

    public static boolean glows(GriffinColor color) {
        return switch (color) {
            case EMBER, FROST, PRISMATIC, ASTRAL -> true;
            default -> false;
        };
    }

    /** Whether the look changes over time (so the K menu has to redraw its texture). */
    public static boolean animated(GriffinColor color) {
        return glows(color);
    }

    /** The multiplier on the base texture, 0xAARRGGBB: white except for Prismatic, which cycles through the hues. */
    public static int tint(GriffinColor color, float ticks) {
        if (color != GriffinColor.PRISMATIC) {
            return 0xFFFFFFFF;
        }
        return 0xFF000000 | hsv(ticks / RAINBOW_TICKS, 0.5f, 1f);
    }

    /** Brightness 0 to 1 of the first and second glow texture. */
    public static float brightness(GriffinColor color, int layer, float t) {
        return switch (color) {
            case EMBER -> layer == 0 ? 0.65f + 0.35f * Mth.sin(t * 0.16f) : 0.5f + 0.5f * Mth.sin(t * 0.13f + 1.5f);
            case FROST -> layer == 0 ? 0.9f : 0.7f + 0.3f * Mth.sin(t * 0.07f);
            case PRISMATIC -> layer == 0 ? 0.85f + 0.15f * Mth.sin(t * 0.2f) : 0.7f + 0.3f * Mth.sin(t * 0.15f + 2f);
            case ASTRAL -> {
                float s = Mth.sin(t * (layer == 0 ? 0.07f : 0.095f) + (layer == 0 ? 0f : 1.7f));
                yield 0.08f + 0.92f * s * s;
            }
            default -> 0f;
        };
    }

    /** The colour glow textures are multiplied with (before brightness), 0xRRGGBB: paler rainbow for Prismatic. */
    public static int glowTint(GriffinColor color, float ticks) {
        return color == GriffinColor.PRISMATIC ? hsv(ticks / RAINBOW_TICKS, 0.3f, 1f) : 0xFFFFFF;
    }

    private static int hsv(float h, float s, float v) {
        return Mth.hsvToRgb(h - Mth.floor(h), s, v) & 0xFFFFFF;
    }
}
