package com.skyrpg.core.client.menu;

import com.mojang.blaze3d.platform.cursor.CursorTypes;
import com.skyrpg.core.client.config.Setting;
import com.skyrpg.core.client.ui.Draw;
import com.skyrpg.core.client.ui.Theme;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

/**
 * One setting's card on the Settings page: title, a description of up to two lines, and its control (a switch,
 * button, segmented choice, "‹ option ›" switcher or slider). It's measured for a width once ({@link #measure}),
 * then drawn and hit-tested with positions relative to the card's top-left corner. A control that would squeeze
 * the text too much goes on its own line under the description instead of on the right.
 */
final class SettingRow {
    static final int PAD = 8;
    private static final int CONTROL_HEIGHT = 14;
    private static final int SWITCH_WIDTH = 22;
    private static final int SWITCH_HEIGHT = 12;
    private static final int SEGMENT_PAD = 12;
    private static final int MAX_DESCRIPTION_LINES = 2;
    private static final int TITLE_Y = 7;
    private static final int DESCRIPTION_Y = 18;
    private static final int LINE = 10;
    /** Inline controls get at most this share of the card's inner width. */
    private static final float INLINE_SHARE = 0.5f;

    enum Control { SWITCH, BUTTON, SEGMENTS, CYCLER, SLIDER }

    enum HitKind { NONE, ROW, SEGMENT, PREVIOUS, NEXT, SLIDER }

    record Hit(HitKind kind, int index) {
        static final Hit NONE = new Hit(HitKind.NONE, -1);
    }

    /** Per-setting animation state, kept by the page between frames. */
    static final class Anim {
        float hover;
        float knob = -1;
        float segmentX = -1;
        float segmentWidth;
    }

    final Setting setting;
    final int width;
    final int height;
    final Control control;
    final boolean inline;
    final int textWidth;
    final int titleY;
    final List<String> description;
    final boolean descriptionCut;
    final int controlX;
    final int controlY;
    final int controlWidth;
    /** Segments: left edges and widths, relative to {@link #controlX}. */
    final int[] segmentX;
    final int[] segmentWidth;
    /** Slider: room for the value text, then the track (relative to {@link #controlX}). */
    final int valueWidth;
    final int trackX;
    final int trackWidth;

    private SettingRow(Setting setting, int width, int height, Control control, boolean inline, int textWidth, int titleY,
                       List<String> description, boolean descriptionCut, int controlX, int controlY, int controlWidth,
                       int[] segmentX, int[] segmentWidth, int valueWidth, int trackX, int trackWidth) {
        this.setting = setting;
        this.width = width;
        this.height = height;
        this.control = control;
        this.inline = inline;
        this.textWidth = textWidth;
        this.titleY = titleY;
        this.description = description;
        this.descriptionCut = descriptionCut;
        this.controlX = controlX;
        this.controlY = controlY;
        this.controlWidth = controlWidth;
        this.segmentX = segmentX;
        this.segmentWidth = segmentWidth;
        this.valueWidth = valueWidth;
        this.trackX = trackX;
        this.trackWidth = trackWidth;
    }

    // ------------------------------------------------------------------ measuring

    static SettingRow measure(Font font, Setting setting, int width) {
        int inner = width - PAD * 2;
        Control control;
        int controlWidth;
        boolean inline;
        int[] segmentX = new int[0];
        int[] segmentWidth = new int[0];
        int valueWidth = 0;
        int trackWidth = 0;
        switch (setting.kind()) {
            case TOGGLE -> {
                control = Control.SWITCH;
                controlWidth = SWITCH_WIDTH;
                inline = true;
            }
            case ACTION -> {
                control = Control.BUTTON;
                controlWidth = Math.min(inner, Math.max(40, font.width(setting.buttonLabel()) + 16));
                inline = controlWidth <= inner * INLINE_SHARE;
            }
            case CHOICE -> {
                List<String> options = setting.options();
                segmentX = new int[options.size()];
                segmentWidth = new int[options.size()];
                int total = 0;
                for (int i = 0; i < options.size(); i++) {
                    segmentX[i] = total;
                    segmentWidth[i] = font.width(options.get(i)) + SEGMENT_PAD;
                    total += segmentWidth[i] + (i < options.size() - 1 ? 1 : 0);
                }
                if (options.size() <= 5 && total <= inner) {
                    control = Control.SEGMENTS;
                    controlWidth = total;
                    inline = total <= inner * INLINE_SHARE;
                } else {
                    control = Control.CYCLER;
                    int widest = 0;
                    for (String option : options) {
                        widest = Math.max(widest, font.width(option));
                    }
                    controlWidth = Math.min(inner, Math.max(56, widest + 28));
                    inline = controlWidth <= inner * INLINE_SHARE;
                }
            }
            default -> {
                control = Control.SLIDER;
                valueWidth = Math.max(font.width(setting.formatted(setting.min())),
                        Math.max(font.width(setting.formatted(setting.max())), font.width(setting.formatted(setting.defaultNumber()))));
                trackWidth = Mth.clamp(Math.round(inner * 0.3f), 60, 120);
                controlWidth = valueWidth + 6 + trackWidth;
                inline = controlWidth <= inner * 0.55f;
                if (!inline) {
                    trackWidth = Math.max(40, inner - valueWidth - 6);
                    controlWidth = valueWidth + 6 + trackWidth;
                }
            }
        }

        int textWidth = inline ? inner - controlWidth - 10 : inner;
        List<String> description = new ArrayList<>();
        boolean cut = wrap(font, setting.description().getString(), textWidth, MAX_DESCRIPTION_LINES, description);
        int lines = description.size();
        int textBottom = lines == 0 ? TITLE_Y + 8 : DESCRIPTION_Y + 8 + LINE * (lines - 1);
        int controlHeight = control == Control.SWITCH ? SWITCH_HEIGHT : CONTROL_HEIGHT;
        int height;
        int controlY;
        int controlX;
        if (inline) {
            height = Math.max(26, textBottom + 7);
            controlY = (height - controlHeight) / 2;
            controlX = width - PAD - controlWidth;
        } else {
            controlY = textBottom + 6;
            height = controlY + controlHeight + 8;
            controlX = PAD;
        }
        int titleY = lines == 0 && inline ? (height - 8) / 2 : TITLE_Y;
        int trackX = valueWidth + 6;
        return new SettingRow(setting, width, height, control, inline, textWidth, titleY, description, cut, controlX, controlY,
                controlWidth, segmentX, segmentWidth, valueWidth, trackX, trackWidth);
    }

    /** Word-wraps plain text into at most {@code maxLines}; returns true if some was cut (the last line then ends in "…"). */
    private static boolean wrap(Font font, String text, int width, int maxLines, List<String> out) {
        if (text.isBlank()) {
            return false;
        }
        String[] words = text.trim().split("\\s+");
        StringBuilder line = new StringBuilder();
        int i = 0;
        while (i < words.length) {
            String candidate = line.isEmpty() ? words[i] : line + " " + words[i];
            if (font.width(candidate) <= width || line.isEmpty()) {
                line.setLength(0);
                line.append(candidate);
                i++;
                continue;
            }
            out.add(line.toString());
            line.setLength(0);
            if (out.size() == maxLines) {
                break;
            }
        }
        if (out.size() < maxLines && !line.isEmpty()) {
            out.add(line.toString());
            line.setLength(0);
        }
        boolean cut = i < words.length || !line.isEmpty();
        // A single word longer than the line, or text left over: end the last line in "…".
        int last = out.size() - 1;
        if (cut) {
            StringBuilder rest = new StringBuilder(out.get(last));
            for (int w = i; w < words.length; w++) {
                rest.append(' ').append(words[w]);
            }
            out.set(last, Draw.ellipsize(font, rest.toString(), width));
        } else if (font.width(out.get(last)) > width) {
            out.set(last, Draw.ellipsize(font, out.get(last), width));
            cut = true;
        }
        for (int l = 0; l < last; l++) {
            if (font.width(out.get(l)) > width) {
                out.set(l, Draw.ellipsize(font, out.get(l), width));
                cut = true;
            }
        }
        return cut;
    }

    // ------------------------------------------------------------------ drawing

    /**
     * Draws the card at (x, y). {@code highlight} (lower case, may be empty) is the search word to colour in the
     * title. Returns true if the title or description was cut, so the caller can show them whole in a tooltip.
     */
    boolean draw(GuiGraphicsExtractor g, Font font, int x, int y, int mouseX, int mouseY, boolean hovered, boolean focused,
                 boolean dragging, Anim anim, float frameSeconds, String highlight) {
        anim.hover = Mth.lerp(Math.min(1, frameSeconds * 14), anim.hover, hovered ? 1 : 0);
        int border = focused ? Theme.ACCENT : Draw.mix(Theme.CARD_BORDER, Draw.mix(Theme.CARD_BORDER, Theme.ACCENT, 0.4f), anim.hover);
        Draw.panel(g, x, y, width, height, Draw.mix(Theme.CARD, Theme.CARD_HOVER, anim.hover), border);

        Component title = highlighted(setting.label().getString(), highlight);
        boolean cut = Draw.fitText(g, font, title, x + PAD, y + titleY, textWidth, Theme.TEXT);
        if (!setting.isAction() && !setting.isDefault()) {
            // A small gold dot: changed from the default.
            int dotX = x + PAD + Draw.fittedWidth(font, title, textWidth, 1f, Draw.MIN_TEXT_SCALE) + 4;
            if (dotX + 3 <= x + PAD + textWidth + 6) {
                Draw.rounded(g, dotX, y + titleY + 2, 3, 3, Draw.withAlpha(Theme.ACCENT, 0.85f));
            }
        }
        for (int i = 0; i < description.size(); i++) {
            Draw.text(g, font, description.get(i), x + PAD, y + DESCRIPTION_Y + i * LINE, Theme.MUTED);
        }
        cut |= descriptionCut;

        int cx = x + controlX;
        int cy = y + controlY;
        int relX = mouseX - x;
        int relY = mouseY - y;
        Hit hit = hovered ? hit(relX, relY) : Hit.NONE;
        switch (control) {
            case SWITCH -> drawSwitch(g, cx, cy, anim, frameSeconds);
            case BUTTON -> drawButton(g, font, cx, cy, hovered);
            case SEGMENTS -> drawSegments(g, font, cx, cy, hit, anim, frameSeconds);
            case CYCLER -> drawCycler(g, font, cx, cy, hovered, hit);
            case SLIDER -> drawSlider(g, font, cx, cy, hit.kind() == HitKind.SLIDER || dragging, dragging);
        }
        if (hovered && hit.kind() != HitKind.NONE) {
            g.requestCursor(control == Control.SLIDER && hit.kind() == HitKind.SLIDER ? CursorTypes.RESIZE_EW : CursorTypes.POINTING_HAND);
        }
        return cut;
    }

    /** The title with the first match of {@code word} in gold. */
    static Component highlighted(String text, String word) {
        if (word == null || word.isEmpty()) {
            return Component.literal(text);
        }
        int at = text.toLowerCase(Locale.ROOT).indexOf(word);
        if (at < 0 || at + word.length() > text.length()) {
            return Component.literal(text);
        }
        return Component.literal(text.substring(0, at))
                .append(Component.literal(text.substring(at, at + word.length())).withColor(Theme.ACCENT))
                .append(Component.literal(text.substring(at + word.length())));
    }

    private void drawSwitch(GuiGraphicsExtractor g, int sx, int sy, Anim anim, float frameSeconds) {
        float target = setting.get() ? 1 : 0;
        anim.knob = anim.knob < 0 ? target : Mth.lerp(Math.min(1, frameSeconds * 16), anim.knob, target);
        float knob = anim.knob;
        Draw.rounded(g, sx, sy, SWITCH_WIDTH, SWITCH_HEIGHT, Draw.mix(Theme.TRACK, Theme.ACCENT, knob));
        if (knob < 0.5f) {
            Draw.rounded(g, sx + 1, sy + 1, SWITCH_WIDTH - 2, SWITCH_HEIGHT - 2, Draw.mix(0xFF141922, Theme.ACCENT, knob));
        }
        int kx = sx + 2 + Math.round((SWITCH_WIDTH - 12) * knob);
        Draw.rounded(g, kx, sy + 2, 8, 8, Draw.mix(Theme.MUTED, 0xFFFFFFFF, knob));
    }

    private void drawButton(GuiGraphicsExtractor g, Font font, int bx, int by, boolean hovered) {
        Draw.panel(g, bx, by, controlWidth, CONTROL_HEIGHT, hovered ? Draw.withAlpha(Theme.ACCENT, 0.18f) : Theme.TRACK,
                hovered ? Theme.ACCENT : Theme.CARD_BORDER);
        Draw.fit(g, font, setting.buttonLabel(), bx + controlWidth / 2, by + 3, controlWidth - 6, Draw.Align.CENTER,
                hovered ? Theme.ACCENT : Theme.TEXT);
    }

    private void drawSegments(GuiGraphicsExtractor g, Font font, int cx, int cy, Hit hit, Anim anim, float frameSeconds) {
        Draw.panel(g, cx, cy, controlWidth, CONTROL_HEIGHT, Theme.TRACK, Theme.CARD_BORDER);
        int selected = setting.choiceIndex();
        // The highlight slides to the picked option.
        float targetX = segmentX[selected];
        float targetW = segmentWidth[selected];
        float t = Math.min(1, frameSeconds * 18);
        anim.segmentX = anim.segmentX < 0 ? targetX : Mth.lerp(t, anim.segmentX, targetX);
        anim.segmentWidth = anim.segmentWidth <= 0 ? targetW : Mth.lerp(t, anim.segmentWidth, targetW);
        int hx = cx + Math.round(anim.segmentX);
        int hw = Math.round(anim.segmentWidth);
        int hovered = hit.kind() == HitKind.SEGMENT ? hit.index() : -1;
        if (hovered >= 0 && hovered != selected) {
            Draw.rounded(g, cx + segmentX[hovered] + 1, cy + 1, segmentWidth[hovered] - 1, CONTROL_HEIGHT - 2, Theme.HOVER);
        }
        Draw.rounded(g, hx + 1, cy + 1, hw - 1, CONTROL_HEIGHT - 2, Draw.withAlpha(Theme.ACCENT, 0.2f));
        g.fill(hx + 2, cy + CONTROL_HEIGHT - 2, hx + hw - 1, cy + CONTROL_HEIGHT - 1, Theme.ACCENT);
        List<String> options = setting.options();
        for (int i = 0; i < options.size(); i++) {
            int sx = cx + segmentX[i];
            if (i > 0 && i != selected && i - 1 != selected) {
                g.fill(sx - 1, cy + 3, sx, cy + CONTROL_HEIGHT - 3, Theme.CARD_BORDER);
            }
            int color = i == selected ? Theme.ACCENT : i == hovered ? Theme.TEXT : Theme.MUTED;
            Draw.text(g, font, options.get(i), sx + SEGMENT_PAD / 2, cy + 3, color);
        }
    }

    private void drawCycler(GuiGraphicsExtractor g, Font font, int cx, int cy, boolean hovered, Hit hit) {
        Draw.panel(g, cx, cy, controlWidth, CONTROL_HEIGHT, Theme.TRACK,
                hovered ? Draw.mix(Theme.CARD_BORDER, Theme.ACCENT, 0.5f) : Theme.CARD_BORDER);
        boolean previous = hit.kind() == HitKind.PREVIOUS;
        boolean next = hit.kind() == HitKind.NEXT;
        Draw.text(g, font, "‹", cx + 5, cy + 3, previous ? Theme.ACCENT : Theme.MUTED);
        Draw.rightText(g, font, "›", cx + controlWidth - 5, cy + 3, next ? Theme.ACCENT : Theme.MUTED);
        Draw.fit(g, font, Component.literal(setting.choice()), cx + controlWidth / 2, cy + 3, controlWidth - 24, Draw.Align.CENTER, Theme.TEXT);
        // Which of the options this is, as small dots under the value.
        int count = setting.options().size();
        if (count <= 12) {
            int dotsX = cx + controlWidth / 2 - (count * 3 - 1) / 2;
            for (int i = 0; i < count; i++) {
                g.fill(dotsX + i * 3, cy + CONTROL_HEIGHT - 2, dotsX + i * 3 + 2, cy + CONTROL_HEIGHT - 1,
                        i == setting.choiceIndex() ? Theme.ACCENT : Theme.CARD_BORDER);
            }
        }
    }

    private void drawSlider(GuiGraphicsExtractor g, Font font, int cx, int cy, boolean active, boolean dragging) {
        Draw.rightText(g, font, setting.formatted(), cx + valueWidth, cy + 3, dragging ? Theme.ACCENT : Theme.TEXT);
        int tx = cx + trackX;
        int ty = cy + 5;
        Draw.panel(g, tx, ty, trackWidth, 5, Theme.TRACK, Theme.CARD_BORDER);
        float progress = (float) setting.progress();
        int knobX = tx + Math.round(progress * (trackWidth - 6));
        if (knobX + 3 > tx + 1) {
            g.fill(tx + 1, ty + 1, knobX + 3, ty + 4, Theme.ACCENT);
            g.fill(tx + 1, ty + 1, knobX + 3, ty + 2, Draw.lighten(Theme.ACCENT, 0.35f));
        }
        // Tick marks for sliders with only a few steps.
        if (setting.step() > 0) {
            double steps = (setting.max() - setting.min()) / setting.step();
            if (steps >= 2 && steps <= 10) {
                for (int i = 1; i < (int) Math.round(steps); i++) {
                    int px = tx + 3 + (int) Math.round(i / steps * (trackWidth - 6));
                    g.fill(px, ty + 2, px + 1, ty + 3, px <= knobX + 3 ? Draw.withAlpha(0xFF000000, 0.35f) : Theme.DIM);
                }
            }
        }
        int knobFill = active ? 0xFFFFFFFF : 0xFFD5DBE5;
        Draw.panel(g, knobX, cy + 1, 6, 12, knobFill, active ? Theme.ACCENT : 0xFF3A4252);
    }

    // ------------------------------------------------------------------ input

    /** What's under a point relative to the card's top-left corner. */
    Hit hit(double relX, double relY) {
        if (relX < 0 || relY < 0 || relX >= width || relY >= height) {
            return Hit.NONE;
        }
        boolean inControlRow = relY >= controlY - 3 && relY < controlY + CONTROL_HEIGHT + 3;
        switch (control) {
            case SEGMENTS -> {
                if (inControlRow && relX >= controlX && relX < controlX + controlWidth) {
                    for (int i = segmentX.length - 1; i >= 0; i--) {
                        if (relX >= controlX + segmentX[i]) {
                            return new Hit(HitKind.SEGMENT, i);
                        }
                    }
                }
                return new Hit(HitKind.ROW, -1);
            }
            case CYCLER -> {
                if (inControlRow && relX >= controlX && relX < controlX + controlWidth) {
                    return new Hit(relX < controlX + controlWidth / 2.0 ? HitKind.PREVIOUS : HitKind.NEXT, -1);
                }
                return new Hit(HitKind.ROW, -1);
            }
            case SLIDER -> {
                if (inControlRow && relX >= controlX + trackX - 4 && relX < controlX + trackX + trackWidth + 4) {
                    return new Hit(HitKind.SLIDER, -1);
                }
                // The rest of a slider's card does nothing on click (it's easy to brush past).
                return Hit.NONE;
            }
            default -> {
                return new Hit(HitKind.ROW, -1);
            }
        }
    }

    /** The slider value under {@code relX} (relative to the card), for dragging. */
    double sliderValueAt(double relX) {
        double start = controlX + trackX + 3;
        double progress = Mth.clamp((relX - start) / Math.max(1, trackWidth - 6), 0, 1);
        return setting.min() + progress * (setting.max() - setting.min());
    }
}
