package com.skyrpg.welcome.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.skyrpg.core.client.ui.Draw;
import com.skyrpg.core.client.ui.Theme;
import java.util.List;
import java.util.function.BooleanSupplier;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.MouseButtonInfo;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Util;

/**
 * A button in the SkyRPG look. Real widgets, so keyboard navigation (Tab, arrows, Enter) and screen reader
 * narration work like the vanilla ones. Styles: a gold primary button, a normal card button, a quiet text button and
 * a selectable card with a description (the preset cards).
 */
final class SkyButton extends AbstractWidget {
    enum Style { PRIMARY, NORMAL, GHOST, CARD }

    private final Style style;
    private final Runnable action;
    private final Font font;
    private Component description = Component.empty();
    private BooleanSupplier selected = () -> false;
    private int accent = Theme.ACCENT;
    private float hover;
    private float pulse;
    private long lastFrame = Util.getMillis();
    private final long createdAt = Util.getMillis();

    SkyButton(Font font, Style style, int x, int y, int w, int h, Component label, Runnable action) {
        super(x, y, w, h, label);
        this.font = font;
        this.style = style;
        this.action = action;
    }

    SkyButton description(Component text) {
        this.description = text;
        return this;
    }

    SkyButton selected(BooleanSupplier selected) {
        this.selected = selected;
        return this;
    }

    SkyButton accent(int color) {
        this.accent = color;
        return this;
    }

    @Override
    protected boolean isValidClickButton(MouseButtonInfo info) {
        return info.button() == 0;
    }

    @Override
    public void onClick(MouseButtonEvent event, boolean doubleClick) {
        press();
    }

    void press() {
        if (active) {
            Ui.click(style == Style.PRIMARY ? 1.3f : 1.1f);
            action.run();
        }
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (active && visible && isFocused()
                && (event.key() == InputConstants.KEY_RETURN || event.key() == InputConstants.KEY_SPACE || event.key() == InputConstants.KEY_NUMPADENTER)) {
            press();
            return true;
        }
        return false;
    }

    @Override
    protected void extractWidgetRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        long now = Util.getMillis();
        float dt = Math.min((now - lastFrame) / 1000f, 0.1f);
        lastFrame = now;
        boolean hot = active && isHoveredOrFocused();
        hover = Ui.approach(hover, hot ? 1 : 0, dt, 14);
        boolean on = selected.getAsBoolean();
        pulse = Ui.approach(pulse, on ? 1 : 0, dt, 10);
        float appear = Draw.easeOut((now - createdAt) / 260f);
        int x = getX();
        int y = getY();
        int w = getWidth();
        int h = getHeight();
        if (style == Style.CARD) {
            drawCard(g, x, y, w, h, on);
        } else if (style == Style.PRIMARY) {
            drawPrimary(g, x, y, w, h);
        } else if (style == Style.NORMAL) {
            drawNormal(g, x, y, w, h);
        } else {
            int color = active ? Draw.mix(Theme.MUTED, Theme.TEXT, hover) : Theme.DIM;
            Draw.fit(g, font, message, x + w / 2, y + (h - 8) / 2, w, Draw.Align.CENTER, color);
        }
        if (isFocused()) {
            Draw.outline(g, x - 1, y - 1, w + 2, h + 2, Theme.ACCENT);
        }
        if (appear < 1 && style != Style.GHOST) {
            g.fill(x, y, x + w, y + h, Draw.withAlpha(0xFF0A0D14, (1 - appear) * 0.6f));
        }
    }

    private void drawPrimary(GuiGraphicsExtractor g, int x, int y, int w, int h) {
        Ui.glow(g, x, y, w, h, Theme.ACCENT, 0.5f + hover);
        Draw.rounded(g, x, y, w, h, active ? Draw.lighten(Theme.ACCENT, 0.35f * hover) : Theme.DIM);
        int top = Draw.lighten(Theme.ACCENT, 0.18f + 0.2f * hover);
        int bottom = Draw.mix(Theme.ACCENT, 0xFFB5791A, 0.55f - 0.25f * hover);
        g.fillGradient(x + 1, y + 1, x + w - 1, y + h - 1, active ? top : Theme.DIM, active ? bottom : Theme.DIM);
        // A sheen sweeping across every few seconds.
        float sweep = (Util.getMillis() % 4200) / 4200f;
        int sx = x + 1 + Math.round(sweep * 1.6f * w) - w / 3;
        int from = Math.max(x + 1, sx);
        int to = Math.min(x + w - 1, sx + 10);
        if (to > from) {
            g.fill(from, y + 1, to, y + h - 1, 0x22FFFFFF);
        }
        int textColor = 0xFF1A1305;
        if (h >= 22) {
            float scale = 1.25f;
            int tw = Math.min(Math.round(font.width(message) * scale), w - 8);
            Draw.fit(g, font, message, x + (w - tw) / 2, y + (h - 10) / 2, tw, scale, 1f, Draw.Align.LEFT, textColor, false);
        } else {
            Draw.fit(g, font, message, x + w / 2, y + (h - 8) / 2, w - 8, Draw.Align.CENTER, textColor);
        }
    }

    private void drawNormal(GuiGraphicsExtractor g, int x, int y, int w, int h) {
        Ui.glow(g, x, y, w, h, Theme.ACCENT, hover * 0.5f);
        boolean on = selected.getAsBoolean();
        int border = on ? Theme.ACCENT : Draw.mix(Theme.CARD_BORDER, Theme.ACCENT, hover * 0.7f);
        int fill = active ? Draw.mix(Theme.CARD, Theme.CARD_HOVER, hover) : Theme.TRACK;
        Draw.panel(g, x, y, w, h, on ? Draw.mix(fill, Theme.ACCENT, 0.12f) : fill, border);
        if (on) {
            g.fill(x + 1, y + 4, x + 3, y + h - 4, Theme.ACCENT);
        }
        int textColor = active ? Draw.mix(Theme.TEXT, 0xFFFFFFFF, hover * 0.4f) : Theme.DIM;
        int textY = y + (h - 8) / 2;
        Draw.fit(g, font, message, x + w / 2, textY, w - 12, Draw.Align.CENTER, textColor);
    }

    private void drawCard(GuiGraphicsExtractor g, int x, int y, int w, int h, boolean on) {
        int border = on ? accent : Draw.mix(Theme.CARD_BORDER, accent, hover * 0.6f);
        Ui.glow(g, x, y, w, h, accent, on ? 0.7f : hover * 0.4f);
        int fill = Draw.mix(Draw.mix(Theme.CARD, Theme.CARD_HOVER, hover), Draw.mix(Theme.CARD, accent, 0.14f), pulse);
        Draw.panel(g, x, y, w, h, fill, border);
        g.fill(x + 2, y, x + w - 2, y + 1, Draw.withAlpha(accent, on ? 1f : 0.45f + 0.4f * hover));
        int pad = 7;
        int ty = y + pad;
        Draw.fit(g, font, message.copy().withColor(on ? accent : Theme.TEXT), x + pad, ty, w - pad * 2 - (on ? 10 : 0), Draw.Align.LEFT, Theme.TEXT);
        if (on) {
            int cx = x + w - pad - 8;
            int cy = y + pad;
            g.fill(cx, cy + 3, cx + 2, cy + 5, accent);
            g.fill(cx + 2, cy + 5, cx + 4, cy + 7, accent);
            g.fill(cx + 4, cy + 3, cx + 6, cy + 5, accent);
            g.fill(cx + 6, cy + 1, cx + 8, cy + 3, accent);
        }
        if (!description.getString().isEmpty()) {
            List<FormattedCharSequence> lines = Draw.wrap(font, description, w - pad * 2);
            int ly = ty + 13;
            for (FormattedCharSequence line : lines) {
                if (ly + 8 > y + h - 3) {
                    break;
                }
                g.text(font, line, x + pad, ly, Theme.MUTED, false);
                ly += 10;
            }
        }
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        defaultButtonNarrationText(output);
        if (!description.getString().isEmpty()) {
            output.add(NarratedElementType.HINT, description);
        }
    }
}
