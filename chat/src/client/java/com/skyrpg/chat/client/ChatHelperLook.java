package com.skyrpg.chat.client;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.multiplayer.chat.GuiMessage;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.FormattedCharSink;
import net.minecraft.util.Mth;

/**
 * SkyHelper's answers stand out as the AI talking: a violet-to-teal glow behind every line of the message, a bright
 * edge on top, a shimmer that sweeps across now and then, and a few sparkles that twinkle. The lines of a helper
 * message are wrapped in {@link HelperLine} when the message is laid out; the chat drawing mixins paint the glow before
 * the text of each such line. Line boxes come from vanilla's own background fills (all drawn before any text), so the
 * glow fits them exactly.
 */
public final class ChatHelperLook {
    private static final int VIOLET = 0x6A3FD6;
    private static final int TEAL = 0x1A8FB0;
    private static final int[] EDGE = {0x7FE7FF, 0xB58CFF, 0xFF9BE6};
    private static final int[] SPARKS = {0xFFFFFF, 0x9BF0FF, 0xFFB8F0, 0xD6BFFF};
    private static final int STRIPS = 16;
    private static final long SHIMMER_MS = 3600;

    /** This frame's line backgrounds as vanilla filled them: x0, y0, x1, y1 each. */
    private static final int MAX_BOXES = 128;
    private static final int[] BOXES = new int[MAX_BOXES * 4];
    private static int boxes;

    /** One laid-out line of a helper message: draws as its text, carries where it sits in the message. */
    public record HelperLine(FormattedCharSequence inner, int index, int count, int seed) implements FormattedCharSequence {
        @Override
        public boolean accept(FormattedCharSink sink) {
            return inner.accept(sink);
        }
    }

    private ChatHelperLook() {
    }

    /** Wraps a helper message's lines (others pass through). */
    public static List<FormattedCharSequence> lines(GuiMessage message, List<FormattedCharSequence> lines) {
        if (!ChatLook.themed() || !com.skyrpg.chat.ChatLines.HELPER.equals(ChatHooks.marker(message.content()))) {
            return lines;
        }
        int seed = message.content().getString().hashCode() ^ (int) message.addedTime();
        List<FormattedCharSequence> out = new ArrayList<>(lines.size());
        for (int i = 0; i < lines.size(); i++) {
            out.add(new HelperLine(lines.get(i), i, lines.size(), seed));
        }
        return out;
    }

    static void beginFrame() {
        boxes = 0;
    }

    /** Every chat fill passes here; the wide, one-line-tall ones are line backgrounds. */
    public static void recordFill(int x0, int y0, int x1, int y1) {
        if (boxes < MAX_BOXES && x1 - x0 > 40 && y1 - y0 > 4 && y1 - y0 < 30) {
            BOXES[boxes * 4] = x0;
            BOXES[boxes * 4 + 1] = y0;
            BOXES[boxes * 4 + 2] = x1;
            BOXES[boxes * 4 + 3] = y1;
            boxes++;
        }
    }

    /** Paints the glow of one line; {@code textY} is where vanilla draws its text. */
    public static void draw(GuiGraphicsExtractor g, HelperLine line, int textY, float opacity) {
        int box = -1;
        for (int i = boxes - 1; i >= 0; i--) {
            if (textY >= BOXES[i * 4 + 1] && textY < BOXES[i * 4 + 3]) {
                box = i;
                break;
            }
        }
        if (opacity <= 0.01f || box < 0) {
            return;
        }
        int x0 = BOXES[box * 4] + 2;   // the kind stripe keeps the first two pixels
        int x1 = BOXES[box * 4 + 2];
        int y0 = BOXES[box * 4 + 1];
        int y1 = BOXES[box * 4 + 3];
        int width = x1 - x0;
        if (width < 8) {
            return;
        }
        long now = System.currentTimeMillis();

        // the glow: violet on the left fading into teal, strip by strip
        for (int i = 0; i < STRIPS; i++) {
            float t = i / (float) (STRIPS - 1);
            int sx0 = x0 + width * i / STRIPS;
            int sx1 = x0 + width * (i + 1) / STRIPS;
            float alpha = Mth.lerp(t, 0.62f, 0.28f) * opacity;
            g.fill(sx0, y0, sx1, y1, argb(alpha, lerpRgb(VIOLET, TEAL, t)));
        }

        // a bright edge over the first line and a softer one under the last
        if (line.index() == 0) {
            edge(g, x0, x1, y0, 0.95f * opacity);
        }
        if (line.index() == line.count() - 1) {
            edge(g, x0, x1, y1 - 1, 0.4f * opacity);
        }

        // the shimmer: a soft band of light that sweeps across the whole message, all lines together
        long cycle = (now + (line.seed() & 0xFFFF)) % (SHIMMER_MS * 2);
        if (cycle < SHIMMER_MS) {
            float p = cycle / (float) SHIMMER_MS;
            int center = x0 - 30 + Math.round(p * (width + 60)) + (line.index() * 3);   // a slight slant down the lines
            for (int k = -3; k <= 3; k++) {
                float a = (1f - Math.abs(k) / 4f) * 0.16f * opacity;
                int bx = center + k * 4;
                int bx0 = Math.max(x0, bx);
                int bx1 = Math.min(x1, bx + 4);
                if (bx1 > bx0) {
                    g.fill(bx0, y0, bx1, y1, argb(a, 0xFFFFFF));
                }
            }
        }

        // sparkles: three per line, each twinkling on its own beat
        for (int s = 0; s < 3; s++) {
            int h = mix(line.seed(), line.index() * 7 + s);
            int sx = x0 + 24 + Math.floorMod(h, Math.max(1, width - 30));
            int sy = y0 + 2 + Math.floorMod(h >>> 8, Math.max(1, y1 - y0 - 4));
            double beat = 1400 + Math.floorMod(h >>> 16, 900);
            double phase = Math.floorMod(h >>> 4, 1000) / 1000.0;
            float twinkle = (float) Math.sin(((now / beat) + phase) * Math.PI * 2);
            if (twinkle <= 0) {
                continue;
            }
            float a = twinkle * twinkle * opacity;
            int color = SPARKS[Math.floorMod(h >>> 12, SPARKS.length)];
            g.fill(sx, sy, sx + 1, sy + 1, argb(a, color));
            g.fill(sx - 1, sy, sx, sy + 1, argb(a * 0.6f, color));
            g.fill(sx + 1, sy, sx + 2, sy + 1, argb(a * 0.6f, color));
            g.fill(sx, sy - 1, sx + 1, sy, argb(a * 0.6f, color));
            g.fill(sx, sy + 1, sx + 1, sy + 2, argb(a * 0.6f, color));
            if (twinkle > 0.75f) {
                g.fill(sx - 2, sy, sx - 1, sy + 1, argb(a * 0.25f, color));
                g.fill(sx + 2, sy, sx + 3, sy + 1, argb(a * 0.25f, color));
                g.fill(sx, sy - 2, sx + 1, sy - 1, argb(a * 0.25f, color));
                g.fill(sx, sy + 2, sx + 1, sy + 3, argb(a * 0.25f, color));
            }
        }
    }

    /** A 1px line that runs cyan, violet, pink. */
    private static void edge(GuiGraphicsExtractor g, int x0, int x1, int y, float alpha) {
        int width = x1 - x0;
        for (int i = 0; i < STRIPS; i++) {
            float t = i / (float) (STRIPS - 1) * (EDGE.length - 1);
            int a = Math.min(EDGE.length - 2, (int) t);
            int rgb = lerpRgb(EDGE[a], EDGE[a + 1], t - a);
            g.fill(x0 + width * i / STRIPS, y, x0 + width * (i + 1) / STRIPS, y + 1, argb(alpha, rgb));
        }
    }

    private static int mix(int seed, int n) {
        int h = seed * 0x9E3779B1 + n * 0x85EBCA6B;
        h ^= h >>> 15;
        h *= 0x2C1B3C6D;
        h ^= h >>> 12;
        return h;
    }

    private static int lerpRgb(int a, int b, float t) {
        int r = Math.round(Mth.lerp(t, a >> 16 & 0xFF, b >> 16 & 0xFF));
        int gr = Math.round(Mth.lerp(t, a >> 8 & 0xFF, b >> 8 & 0xFF));
        int bl = Math.round(Mth.lerp(t, a & 0xFF, b & 0xFF));
        return r << 16 | gr << 8 | bl;
    }

    private static int argb(float alpha, int rgb) {
        return (Math.round(Mth.clamp(alpha, 0f, 1f) * 255) << 24) | (rgb & 0xFFFFFF);
    }
}
