package com.skyrpg.vfx.data;

import java.util.random.RandomGenerator;

/**
 * Where a particle's colour comes from:
 * <ul>
 *   <li>{@code "#FF8A2A"}: always that colour;</li>
 *   <li>{@code ["#FF8A2A", "#FFC46B", "#FFE9A8"]}: a palette, each particle picks one;</li>
 *   <li>{@code {"gradient": ["#FFE9A8", "#FF8A2A", "#B02000"]}}: each particle picks a point anywhere along the
 *   blend of these stops.</li>
 * </ul>
 * Colours are {@code #RRGGBB} or {@code #AARRGGBB}.
 */
public final class ColorSpec {
    public static final ColorSpec WHITE = new ColorSpec(new int[]{0xFFFFFFFF}, false);

    private final int[] colors;
    private final boolean gradient;

    public ColorSpec(int[] colors, boolean gradient) {
        if (colors.length == 0) {
            throw new IllegalArgumentException("no colours");
        }
        this.colors = colors.clone();
        this.gradient = gradient;
    }

    public int sample(RandomGenerator random) {
        if (colors.length == 1) {
            return colors[0];
        }
        if (!gradient) {
            return colors[random.nextInt(colors.length)];
        }
        float t = random.nextFloat() * (colors.length - 1);
        int index = Math.min((int) t, colors.length - 2);
        return lerp(colors[index], colors[index + 1], t - index);
    }

    /** The first colour (for previews and /vfx list). */
    public int first() {
        return colors[0];
    }

    /** ARGB blend, channel by channel. */
    public static int lerp(int a, int b, float t) {
        if (t <= 0) {
            return a;
        }
        if (t >= 1) {
            return b;
        }
        int aa = a >>> 24, ar = (a >> 16) & 0xFF, ag = (a >> 8) & 0xFF, ab = a & 0xFF;
        int ba = b >>> 24, br = (b >> 16) & 0xFF, bg = (b >> 8) & 0xFF, bb = b & 0xFF;
        return ((int) (aa + (ba - aa) * t) << 24) | ((int) (ar + (br - ar) * t) << 16) | ((int) (ag + (bg - ag) * t) << 8) | (int) (ab + (bb - ab) * t);
    }

    /** The same colour with its alpha multiplied by {@code alpha} (0-1). */
    public static int withAlpha(int argb, float alpha) {
        int a = Math.round((argb >>> 24) * Math.max(0, Math.min(1, alpha)));
        return (a << 24) | (argb & 0xFFFFFF);
    }
}
