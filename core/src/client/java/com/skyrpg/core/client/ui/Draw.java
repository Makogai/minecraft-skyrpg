package com.skyrpg.core.client.ui;

import com.skyrpg.core.text.Msg;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.joml.Matrix3x2f;
import org.joml.Quaternionf;
import org.joml.Vector2f;
import org.joml.Vector3f;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

/**
 * Drawing helpers for the SkyRPG look: soft-cornered flat panels, bars and scaled text, no vanilla bevels.
 *
 * <p>Text that goes into a fixed box should use the {@code fit...} helpers: text that fits is drawn as
 * is, a little too long shrinks (down to {@link #MIN_TEXT_SCALE}), and anything longer is cut with "…".
 * They return true when the text was cut, so the caller can show the whole of it in a hover tooltip
 * ({@link #hoverTooltip}). {@link #compactNumbers} turns "1,234,567,890" into "1.23B" for numbers that
 * still don't fit.
 */
public final class Draw {
    /** Text never shrinks below this; past that it is cut with "…" instead. */
    public static final float MIN_TEXT_SCALE = 0.75f;
    public static final String ELLIPSIS = "…";
    /** Full numbers shrink only this far before {@link #fitNumber} switches to the short form. */
    private static final float NUMBER_MIN_SCALE = 0.85f;
    /** Glyphs are 8px tall (lineHeight 9 includes the gap below). */
    private static final int GLYPH_HEIGHT = 8;
    /** Made on first use: a static field added by hot-swapping never runs its initializer. */
    private static Pattern number;

    public enum Align { LEFT, CENTER, RIGHT }

    private static List<Component> queuedTooltip;
    private static int queuedAccent;

    private Draw() {
    }

    /** A filled rectangle with its corner pixels cut, which reads as rounded at Minecraft's pixel scale. */
    public static void rounded(GuiGraphicsExtractor g, int x, int y, int w, int h, int color) {
        if (w < 3 || h < 3) {
            g.fill(x, y, x + w, y + h, color);
            return;
        }
        g.fill(x + 1, y, x + w - 1, y + h, color);
        g.fill(x, y + 1, x + 1, y + h - 1, color);
        g.fill(x + w - 1, y + 1, x + w, y + h - 1, color);
    }

    /** A rounded panel with a 1px border. */
    public static void panel(GuiGraphicsExtractor g, int x, int y, int w, int h, int fill, int border) {
        rounded(g, x, y, w, h, border);
        rounded(g, x + 1, y + 1, w - 2, h - 2, fill);
    }

    /** Just the 1px border of a {@link #rounded} box, e.g. a focus ring. */
    public static void outline(GuiGraphicsExtractor g, int x, int y, int w, int h, int color) {
        if (w < 3 || h < 3) {
            g.fill(x, y, x + w, y + h, color);
            return;
        }
        g.fill(x + 1, y, x + w - 1, y + 1, color);
        g.fill(x + 1, y + h - 1, x + w - 1, y + h, color);
        g.fill(x, y + 1, x + 1, y + h - 1, color);
        g.fill(x + w - 1, y + 1, x + w, y + h - 1, color);
    }

    /** A soft shadow around a box: {@code size} rings fading out, strongest at the box's edge. */
    public static void shadow(GuiGraphicsExtractor g, int x, int y, int w, int h, int size, float strength) {
        for (int i = size; i >= 1; i--) {
            float fade = 1 - (i - 1) / (float) size;
            rounded(g, x - i, y - i + 1, w + i * 2, h + i * 2, withAlpha(0xFF000000, strength * fade * fade / size * 2));
        }
    }

    /** A progress bar: dark track, coloured fill with a lighter top edge. {@code progress} is 0..1. */
    public static void bar(GuiGraphicsExtractor g, int x, int y, int w, int h, float progress, int color) {
        rounded(g, x, y, w, h, Theme.TRACK);
        int filled = Math.round((w - 2) * Mth.clamp(progress, 0, 1));
        if (filled > 0) {
            g.fillGradient(x + 1, y + 1, x + 1 + filled, y + h - 1, lighten(color, 0.25f), color);
            g.fill(x + 1, y + 1, x + 1 + filled, y + 2, lighten(color, 0.5f));
        }
    }

    /** A thin vertical scrollbar: track plus a thumb sized to the visible part. */
    public static void scrollbar(GuiGraphicsExtractor g, int x, int y, int h, int content, double scrolled, int thumbColor) {
        int maxScroll = Math.max(1, content - h);
        int thumb = Mth.clamp(h * h / Math.max(1, content), 14, h);
        int thumbY = y + (int) Math.round((h - thumb) * Mth.clamp(scrolled / maxScroll, 0, 1));
        g.fill(x, y, x + 2, y + h, Theme.TRACK);
        g.fill(x, thumbY, x + 2, thumbY + thumb, thumbColor);
    }

    public static void text(GuiGraphicsExtractor g, Font font, Component text, int x, int y, int color) {
        g.text(font, text, x, y, color, false);
    }

    public static void text(GuiGraphicsExtractor g, Font font, String text, int x, int y, int color) {
        g.text(font, text, x, y, color, false);
    }

    public static void rightText(GuiGraphicsExtractor g, Font font, String text, int right, int y, int color) {
        g.text(font, text, right - font.width(text), y, color, false);
    }

    /** Text drawn {@code scale} times larger, with (x, y) as its top-left corner. */
    public static void scaledText(GuiGraphicsExtractor g, Font font, Component text, int x, int y, float scale, int color) {
        g.pose().pushMatrix();
        g.pose().translate(x, y);
        g.pose().scale(scale, scale);
        g.text(font, text, 0, 0, color, false);
        g.pose().popMatrix();
    }

    // ------------------------------------------------------------------ fitting text into boxes

    /** {@code text} cut to {@code maxWidth} pixels, ending in "…" if it had to be shortened. */
    public static String ellipsize(Font font, String text, int maxWidth) {
        if (font.width(text) <= maxWidth) {
            return text;
        }
        String cut = font.plainSubstrByWidth(text, Math.max(0, maxWidth - font.width(ELLIPSIS))).stripTrailing();
        return cut + ELLIPSIS;
    }

    /** Styled text cut to {@code maxWidth} pixels; the "…" takes the style of the text it follows. */
    public static FormattedCharSequence ellipsize(Font font, Component text, int maxWidth) {
        if (font.width(text) <= maxWidth) {
            return text.getVisualOrderText();
        }
        FormattedText cut = font.substrByWidth(text, Math.max(0, maxWidth - font.width(ELLIPSIS)));
        Style[] last = {text.getStyle()};
        cut.visit((style, part) -> {
            if (!part.isEmpty()) {
                last[0] = style;
            }
            return Optional.empty();
        }, Style.EMPTY);
        return Language.getInstance().getVisualOrder(FormattedText.composite(cut, FormattedText.of(ELLIPSIS, last[0])));
    }

    /** Word-wraps text to {@code width} pixels. */
    public static List<FormattedCharSequence> wrap(Font font, Component text, int width) {
        return text.getString().isEmpty() ? List.of(FormattedCharSequence.EMPTY) : font.split(text, Math.max(10, width));
    }

    /** The first option that fits {@code maxWidth} unscaled, else the last one (usually the shortest). */
    public static String firstFitting(Font font, int maxWidth, String... options) {
        for (String option : options) {
            if (font.width(option) <= maxWidth) {
                return option;
            }
        }
        return options[options.length - 1];
    }

    /** Every number of 10,000 or more in {@code text} in its short form: "⛁ 1,234,567,890" -> "⛁ 1.23B". */
    public static String compactNumbers(String text) {
        if (number == null) {
            number = Pattern.compile("\\d{1,3}(?:,\\d{3})+(?:\\.\\d+)?|\\d{5,}(?:\\.\\d+)?");
        }
        Matcher matcher = number.matcher(text);
        StringBuilder out = new StringBuilder();
        while (matcher.find()) {
            double value = Double.parseDouble(matcher.group().replace(",", ""));
            matcher.appendReplacement(out, Matcher.quoteReplacement(value >= 10_000 ? Msg.compact(value) : matcher.group()));
        }
        matcher.appendTail(out);
        return out.toString();
    }

    /** The scale {@code text} will be drawn at by {@link #fit}: {@code scale}, or less (not below {@code minScale}) to fit. */
    public static float fitScale(Font font, Component text, int maxWidth, float scale, float minScale) {
        int width = font.width(text);
        if (width * scale <= maxWidth || width == 0) {
            return scale;
        }
        float shrunk = maxWidth / (float) width;
        return shrunk >= minScale ? shrunk : scale;
    }

    /** How wide {@link #fit} will draw {@code text}. */
    public static int fittedWidth(Font font, Component text, int maxWidth, float scale, float minScale) {
        float s = fitScale(font, text, maxWidth, scale, minScale);
        return Math.min(maxWidth, (int) Math.ceil(font.width(text) * s));
    }

    /**
     * Draws one line of text into {@code maxWidth} pixels: at {@code scale} if it fits, shrunk towards
     * {@code minScale} if that's enough, otherwise cut with "…" (at {@code scale}, or at {@code minScale} for
     * big text whose minimum is normal size or more). {@code x} is the left
     * edge, the centre or the right edge depending on {@code align}; shrunk text stays vertically
     * centred on the line. Returns true if the text was cut.
     */
    public static boolean fit(GuiGraphicsExtractor g, Font font, Component text, int x, int y, int maxWidth, float scale, float minScale,
                              Align align, int color, boolean shadow) {
        if (maxWidth <= 0) {
            return !text.getString().isEmpty();
        }
        int width = font.width(text);
        float s = fitScale(font, text, maxWidth, scale, minScale);
        boolean cut = width * s > maxWidth + 0.01f;
        if (cut && minScale >= 1f) {
            // Big text (a 1.5x title) that won't fit even at normal size is cut at normal size, showing more of it.
            s = minScale;
        }
        FormattedCharSequence line = cut ? ellipsize(font, text, (int) Math.floor(maxWidth / s)) : text.getVisualOrderText();
        float drawn = font.width(line) * s;
        float left = switch (align) {
            case LEFT -> x;
            case CENTER -> x - drawn / 2f;
            case RIGHT -> x - drawn;
        };
        float top = y + (scale - s) * GLYPH_HEIGHT / 2f;
        if (s == 1f && left == Math.round(left) && top == Math.round(top)) {
            g.text(font, line, (int) left, (int) top, color, shadow);
        } else {
            g.pose().pushMatrix();
            g.pose().translate(left, top);
            g.pose().scale(s, s);
            g.text(font, line, 0, 0, color, shadow);
            g.pose().popMatrix();
        }
        return cut;
    }

    public static boolean fit(GuiGraphicsExtractor g, Font font, Component text, int x, int y, int maxWidth, Align align, int color) {
        return fit(g, font, text, x, y, maxWidth, 1f, MIN_TEXT_SCALE, align, color, false);
    }

    /** Left-aligned {@link #fit} at normal size. */
    public static boolean fitText(GuiGraphicsExtractor g, Font font, Component text, int x, int y, int maxWidth, int color) {
        return fit(g, font, text, x, y, maxWidth, 1f, MIN_TEXT_SCALE, Align.LEFT, color, false);
    }

    public static boolean fitText(GuiGraphicsExtractor g, Font font, String text, int x, int y, int maxWidth, int color) {
        return fitText(g, font, Component.literal(text), x, y, maxWidth, color);
    }

    /** {@link #fit} with {@code right} as the right edge. */
    public static boolean fitRight(GuiGraphicsExtractor g, Font font, String text, int right, int y, int maxWidth, int color) {
        return fit(g, font, Component.literal(text), right, y, maxWidth, 1f, MIN_TEXT_SCALE, Align.RIGHT, color, false);
    }

    /**
     * For values like "⛁ 1,234,567,890": the full text if it fits, else with its numbers shortened
     * ("⛁ 1.23B"), shrunk or cut as a last resort. Returns true if what's shown isn't the full text.
     */
    public static boolean fitNumber(GuiGraphicsExtractor g, Font font, String text, int x, int y, int maxWidth, Align align, int color) {
        // A little smaller is better than shorter; much smaller isn't, so then it's "1.23B".
        String shown = font.width(text) * NUMBER_MIN_SCALE <= maxWidth ? text : compactNumbers(text);
        float minScale = shown.equals(text) ? NUMBER_MIN_SCALE : MIN_TEXT_SCALE;
        fit(g, font, Component.literal(shown), x, y, maxWidth, 1f, minScale, align, color, false);
        return !shown.equals(text) || font.width(shown) * minScale > maxWidth;
    }

    /** The text {@link #fitNumber} would choose before any shrinking. */
    public static String numberFor(Font font, String text, int maxWidth) {
        return font.width(text) <= maxWidth ? text : compactNumbers(text);
    }

    /**
     * {@link InventoryScreen#extractEntityInInventoryFollowsMouse} that works inside a scaled or moved pose (like the
     * SkyRPG menu's): the live model is drawn as a separate picture that ignores the pose, so the box, size and mouse
     * are turned into GUI coordinates first. All arguments are in the current pose's units.
     */
    public static void entityFollowsMouse(GuiGraphicsExtractor g, int x0, int y0, int x1, int y1, int size, float mouseX, float mouseY,
                                          LivingEntity entity) {
        Matrix3x2f pose = new Matrix3x2f(g.pose());
        Vector2f topLeft = pose.transformPosition(new Vector2f(x0, y0));
        Vector2f bottomRight = pose.transformPosition(new Vector2f(x1, y1));
        Vector2f mouse = pose.transformPosition(new Vector2f(mouseX, mouseY));
        g.pose().pushMatrix();
        g.pose().identity();
        InventoryScreen.extractEntityInInventoryFollowsMouse(g, Math.round(topLeft.x), Math.round(topLeft.y),
                Math.round(bottomRight.x), Math.round(bottomRight.y), Math.max(1, Math.round(size * pose.m00())), 0.0625f,
                mouse.x, mouse.y, entity);
        g.pose().popMatrix();
    }

    /**
     * A live model turned to {@code yaw} degrees (0 = facing you; keep adding to make it spin), seen slightly from
     * above, in the box {@code x0,y0 - x1,y1} at {@code size} pixels per block, its feet near the bottom. Unlike
     * {@link #entityFollowsMouse} it can go all the way round, works on the HUD, and shows no name tag.
     * {@code ageInTicks} drives idle animations (pass a clock for entities that aren't in the world; below 0 keeps
     * the entity's own). Arguments are in the current pose's units.
     */
    public static void entityTurning(GuiGraphicsExtractor g, int x0, int y0, int x1, int y1, int size, float yaw, float ageInTicks,
                                     LivingEntity entity) {
        Matrix3x2f pose = new Matrix3x2f(g.pose());
        Vector2f topLeft = pose.transformPosition(new Vector2f(x0, y0));
        Vector2f bottomRight = pose.transformPosition(new Vector2f(x1, y1));
        EntityRenderState state = Minecraft.getInstance().getEntityRenderDispatcher().getRenderer(entity).createRenderState(entity, 1f);
        state.shadowPieces.clear();
        state.outlineColor = 0;
        state.nameTag = null;
        if (ageInTicks >= 0) {
            state.ageInTicks = ageInTicks;
        }
        if (state instanceof LivingEntityRenderState living) {
            living.bodyRot = 180f + yaw;
            living.yRot = 0f;
            living.xRot = 0f;
            living.boundingBoxWidth /= living.scale;
            living.boundingBoxHeight /= living.scale;
            living.scale = 1f;
        }
        Quaternionf rotation = new Quaternionf().rotateZ(Mth.PI);
        Quaternionf camera = new Quaternionf().rotateX(-12f * Mth.DEG_TO_RAD);
        rotation.mul(camera);
        Vector3f translation = new Vector3f(0, state.boundingBoxHeight / 2f + 0.0625f, 0);
        g.pose().pushMatrix();
        g.pose().identity();
        g.entity(state, Math.max(1, Math.round(size * pose.m00())), translation, rotation, camera, Math.round(topLeft.x), Math.round(topLeft.y),
                Math.round(bottomRight.x), Math.round(bottomRight.y));
        g.pose().popMatrix();
    }

    /** An item icon drawn {@code scale} times the usual 16px. */
    public static void item(GuiGraphicsExtractor g, ItemStack stack, int x, int y, float scale) {
        g.pose().pushMatrix();
        g.pose().translate(x, y);
        g.pose().scale(scale, scale);
        g.item(stack, 0, 0);
        g.pose().popMatrix();
    }

    // ------------------------------------------------------------------ tooltips

    /**
     * A SkyRPG-styled tooltip next to the mouse, drawn above everything else. Long lines wrap; the top
     * edge takes the {@code accent} colour. It flips to the other side of the mouse and stays on screen.
     */
    public static void tooltip(GuiGraphicsExtractor g, Font font, List<Component> lines, int mouseX, int mouseY, int accent) {
        tooltip(g, font, lines, mouseX, mouseY, accent, g.guiWidth(), g.guiHeight());
    }

    /**
     * {@link #tooltip} kept inside a {@code screenWidth} x {@code screenHeight} area, for screens that draw scaled
     * (the SkyRPG menu passes its own size, in its own units).
     */
    public static void tooltip(GuiGraphicsExtractor g, Font font, List<Component> lines, int mouseX, int mouseY, int accent,
                               int screenWidth, int screenHeight) {
        int wrapWidth = Mth.clamp(screenWidth - 22, 80, 190);
        List<FormattedCharSequence> wrapped = new ArrayList<>();
        for (Component line : lines) {
            wrapped.addAll(wrap(font, line, wrapWidth));
        }
        int width = 0;
        for (FormattedCharSequence line : wrapped) {
            width = Math.max(width, font.width(line));
        }
        width += 14;
        int height = wrapped.size() * 10 + 9;
        int x = mouseX + 12;
        int y = mouseY - 6;
        if (x + width > screenWidth - 4) {
            x = mouseX - 12 - width;
        }
        x = Mth.clamp(x, 4, Math.max(4, screenWidth - 4 - width));
        y = Mth.clamp(y, 4, Math.max(4, screenHeight - 4 - height));

        g.nextStratum();
        panel(g, x, y, width, height, 0xF70D1017, mix(Theme.BORDER, accent, 0.5f));
        g.fill(x + 2, y, x + width - 2, y + 1, withAlpha(accent, 0.9f));
        for (int i = 0; i < wrapped.size(); i++) {
            g.text(font, wrapped.get(i), x + 7, y + 5 + i * 10, Theme.TEXT, false);
        }
    }

    /**
     * Asks for a tooltip this frame; the screen draws it last, on top of everything, with
     * {@link #drawQueuedTooltip}. The last request in a frame wins.
     */
    public static void queueTooltip(List<Component> lines, int accent) {
        queuedTooltip = lines;
        queuedAccent = accent;
    }

    /** Queues {@code full} as a tooltip while the mouse is over the box; for text that was cut to fit. */
    public static void hoverTooltip(int mouseX, int mouseY, int x, int y, int w, int h, List<Component> full, int accent) {
        if (mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h) {
            queueTooltip(full, accent);
        }
    }

    public static void drawQueuedTooltip(GuiGraphicsExtractor g, Font font, int mouseX, int mouseY) {
        if (queuedTooltip != null) {
            tooltip(g, font, queuedTooltip, mouseX, mouseY, queuedAccent);
            queuedTooltip = null;
        }
    }

    /** {@link #drawQueuedTooltip} kept inside a {@code screenWidth} x {@code screenHeight} area (see the bounded {@link #tooltip}). */
    public static void drawQueuedTooltip(GuiGraphicsExtractor g, Font font, int mouseX, int mouseY, int screenWidth, int screenHeight) {
        if (queuedTooltip != null) {
            tooltip(g, font, queuedTooltip, mouseX, mouseY, queuedAccent, screenWidth, screenHeight);
            queuedTooltip = null;
        }
    }

    public static void clearQueuedTooltip() {
        queuedTooltip = null;
    }

    // ------------------------------------------------------------------ colours and easing

    public static int withAlpha(int color, float alpha) {
        int a = Math.round(((color >>> 24) & 0xFF) * Mth.clamp(alpha, 0, 1));
        return (a << 24) | (color & 0xFFFFFF);
    }

    /** Blends towards white by {@code amount} (0..1). */
    public static int lighten(int color, float amount) {
        return mix(color, 0xFFFFFFFF, amount);
    }

    public static int mix(int a, int b, float t) {
        int r = Math.round(Mth.lerp(t, (a >> 16) & 0xFF, (b >> 16) & 0xFF));
        int gr = Math.round(Mth.lerp(t, (a >> 8) & 0xFF, (b >> 8) & 0xFF));
        int bl = Math.round(Mth.lerp(t, a & 0xFF, b & 0xFF));
        int al = Math.round(Mth.lerp(t, (a >>> 24) & 0xFF, (b >>> 24) & 0xFF));
        return (al << 24) | (r << 16) | (gr << 8) | bl;
    }

    /** Cubic ease-out for animations: fast start, soft landing. */
    public static float easeOut(float t) {
        float clamped = Mth.clamp(t, 0, 1);
        return 1 - (1 - clamped) * (1 - clamped) * (1 - clamped);
    }
}
