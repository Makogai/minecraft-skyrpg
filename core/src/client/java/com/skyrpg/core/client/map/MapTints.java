package com.skyrpg.core.client.map;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import net.minecraft.resources.Identifier;

/**
 * Colour washes over SkyRPG's maps, from any mod: the world mod tints its danger regions red. The map scanner (the
 * {@code skyrpg_utils} mod) asks every source for each pixel it draws, so a source must be cheap and must answer from
 * what it already knows.
 */
public final class MapTints {
    @FunctionalInterface
    public interface Source {
        /** An ARGB tint for the block column at x/z, where alpha is how strongly it shows (0 for none). */
        int tint(Identifier dimension, int x, int z);
    }

    private static final List<Source> SOURCES = new CopyOnWriteArrayList<>();

    private MapTints() {
    }

    public static void register(Source source) {
        SOURCES.add(source);
    }

    /** {@code argb} with every source's tint blended over it. */
    public static int apply(Identifier dimension, int x, int z, int argb) {
        if (argb == 0) {
            return 0;
        }
        for (Source source : SOURCES) {
            int tint = source.tint(dimension, x, z);
            int alpha = tint >>> 24;
            if (alpha == 0) {
                continue;
            }
            float a = alpha / 255f;
            int r = Math.round(((argb >> 16) & 0xFF) * (1 - a) + ((tint >> 16) & 0xFF) * a);
            int g = Math.round(((argb >> 8) & 0xFF) * (1 - a) + ((tint >> 8) & 0xFF) * a);
            int b = Math.round((argb & 0xFF) * (1 - a) + (tint & 0xFF) * a);
            argb = (argb & 0xFF000000) | r << 16 | g << 8 | b;
        }
        return argb;
    }
}
