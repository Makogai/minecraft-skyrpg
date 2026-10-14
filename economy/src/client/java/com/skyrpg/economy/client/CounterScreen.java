package com.skyrpg.economy.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.platform.cursor.CursorTypes;
import com.skyrpg.core.client.ui.Draw;
import com.skyrpg.core.client.ui.Theme;
import com.skyrpg.core.stats.StatTypes;
import com.skyrpg.core.text.Msg;
import com.skyrpg.economy.Purse;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.util.Util;
import net.minecraft.world.item.ItemStack;

/**
 * The frame shared by shop and bank screens, in the SkyRPG menu's look: a dark window with an accent
 * stripe, a header with an icon, title and the purse (which counts up and down and shows +/- floaters
 * when coins move), the screen's body, and a footer with key hints or the last result from the server.
 * Bodies draw immediate-mode: anything clickable registers a hit area while it is drawn.
 */
abstract class CounterScreen extends Screen {
    protected static final int HEADER = 48;
    protected static final int FOOTER = 16;
    protected static final int PAD = 12;
    private static final long NOTICE_MILLIS = 3200;
    private static final int MINUS = 0xFFFF8A65;

    @FunctionalInterface
    protected interface ClickHandler {
        /** {@code button} is an {@link InputConstants} mouse button. Returns true if it used the click. */
        boolean click(int button, boolean shift);
    }

    private record Hit(int x, int y, int w, int h, String key, ClickHandler handler) {
    }

    private record Floater(long amount, long at) {
    }

    protected final int accent;
    private final ItemStack icon;
    private final long openedAt = Util.getMillis();
    private final List<Hit> hits = new ArrayList<>();
    private final Map<String, Float> hover = new HashMap<>();
    private final List<Floater> floaters = new ArrayList<>();
    private long lastFrame = openedAt;
    protected float frameSeconds;
    protected int left;
    protected int top;
    protected int windowWidth;
    protected int windowHeight;
    private int slide;
    private String flashKey;
    private long flashAt;
    private Component notice;
    private boolean noticeGood;
    private long noticeAt;
    private long lastPurse = -1;
    private double shownPurse;
    private List<Component> tooltip;
    private int tooltipAccent;

    protected CounterScreen(Component title, int accent, ItemStack icon) {
        super(title);
        this.accent = accent;
        this.icon = icon;
    }

    protected abstract int maxWidth();

    protected abstract int maxHeight();

    protected abstract Component subtitle();

    protected abstract Component hints();

    /** Draws the body in window space; {@code mouseY} is already corrected for the intro slide. */
    protected abstract void drawBody(GuiGraphicsExtractor g, int mouseX, int mouseY);

    @Override
    protected void init() {
        windowWidth = Math.min(width - 24, maxWidth());
        windowHeight = Math.min(height - 24, maxHeight());
        left = (width - windowWidth) / 2;
        top = (height - windowHeight) / 2;
    }

    @Override
    public void added() {
        super.added();
        if (minecraft.player != null) {
            lastPurse = Purse.get(minecraft.player);
            shownPurse = lastPurse;
        }
        minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.BOOK_PAGE_TURN, 1.3f, 0.6f));
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    /** Shows a result from the server in the footer. */
    void notice(Component text, boolean good) {
        notice = text;
        noticeGood = good;
        noticeAt = Util.getMillis();
    }

    // ------------------------------------------------------------------ geometry

    protected int bodyX() {
        return left + PAD;
    }

    protected int bodyY() {
        return top + HEADER + 8;
    }

    protected int bodyWidth() {
        return windowWidth - PAD * 2;
    }

    protected int bodyHeight() {
        return windowHeight - HEADER - 8 - FOOTER - 6;
    }

    protected long purse() {
        return minecraft.player == null ? 0 : Purse.get(minecraft.player);
    }

    private float intro() {
        return Draw.easeOut((Util.getMillis() - openedAt) / 220f);
    }

    // ------------------------------------------------------------------ frame

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        extractBlurredBackground(g);
        g.fill(0, 0, width, height, Draw.withAlpha(Theme.OVERLAY, intro()));
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        long now = Util.getMillis();
        frameSeconds = Math.min((now - lastFrame) / 1000f, 0.1f);
        lastFrame = now;
        trackPurse(now);
        hits.clear();
        tooltip = null;
        slide = Math.round((1 - intro()) * 10);
        int my = mouseY - slide;

        g.pose().pushMatrix();
        g.pose().translate(0, slide);
        Draw.panel(g, left, top, windowWidth, windowHeight, Theme.WINDOW, Theme.BORDER);
        g.fill(left + 2, top, left + windowWidth - 2, top + 1, Draw.withAlpha(accent, 0.9f));
        // A faint wash of the accent colour behind the header.
        g.fillGradient(left + 1, top + 1, left + windowWidth - 1, top + HEADER, Draw.withAlpha(accent, 0.07f), 0x00000000);
        Draw.clearQueuedTooltip();
        drawHeader(g, now, mouseX, my);
        g.fill(left + PAD, top + HEADER, left + windowWidth - PAD, top + HEADER + 1, Theme.BORDER);
        drawBody(g, mouseX, my);
        drawFooter(g, now);
        g.pose().popMatrix();
        super.extractRenderState(g, mouseX, mouseY, partialTick);
        if (tooltip != null) {
            Draw.queueTooltip(tooltip, tooltipAccent);
        }
        Draw.drawQueuedTooltip(g, font, mouseX, mouseY);
    }

    private void drawHeader(GuiGraphicsExtractor g, long now, int mouseX, int mouseY) {
        int x = left + PAD;
        int y = top + 10;
        Draw.panel(g, x, y, 28, 28, Draw.mix(Theme.CARD, accent, 0.14f), Draw.mix(Theme.CARD_BORDER, accent, 0.55f));
        g.item(icon, x + 6, y + 6);

        // Purse, top right, at 1.5x; on narrow windows it shrinks (and shortens to "1.23B") before the title gives way.
        int right = left + windowWidth - PAD;
        Draw.rightText(g, font, "Purse", right, y + 1, Theme.DIM);
        int purseRoom = Math.max(60, (windowWidth - PAD * 2 - 38) * 2 / 5);
        String full = coins(Math.round(shownPurse));
        Component amount = Component.literal(font.width(full) <= purseRoom ? full : Draw.compactNumbers(full));
        int amountWidth = Draw.fittedWidth(font, amount, purseRoom, 1.5f, 1f);
        Draw.fit(g, font, amount, right, y + 13, purseRoom, 1.5f, 1f, Draw.Align.RIGHT, Theme.ACCENT, false);
        floaters.removeIf(floater -> now - floater.at() > 1100);
        for (Floater floater : floaters) {
            float t = (now - floater.at()) / 1100f;
            String text = (floater.amount() > 0 ? "+" : "−") + coins(Math.abs(floater.amount()));
            int alpha = Math.round(255 * (1 - t * t));
            if (alpha > 8) {
                int color = (alpha << 24) | ((floater.amount() > 0 ? Theme.GREEN : MINUS) & 0xFFFFFF);
                Draw.rightText(g, font, Draw.compactNumbers(text), right - amountWidth - 6, y + 15 - Math.round(Draw.easeOut(t) * 12), color);
            }
        }

        // Title and subtitle take the rest; cut ones show in full on hover.
        int textX = x + 38;
        int room = right - amountWidth - 10 - textX;
        boolean cut = Draw.fit(g, font, title, textX, y + 3, room, 1.5f, 1f, Draw.Align.LEFT, Theme.TEXT, false);
        cut |= Draw.fitText(g, font, subtitle(), textX, y + 18, room, Theme.MUTED);
        if (cut) {
            Draw.hoverTooltip(mouseX, mouseY, textX, y, room, 28, List.of(title.copy().withColor(Theme.TEXT), subtitle().copy().withColor(Theme.MUTED)), accent);
        }
        if (!amount.getString().equals(full)) {
            Draw.hoverTooltip(mouseX, mouseY, right - amountWidth, y, amountWidth, 26,
                    List.of(Component.literal("Purse  ").withColor(Theme.MUTED).append(Component.literal(full).withColor(Theme.ACCENT))), Theme.ACCENT);
        }
    }

    private void trackPurse(long now) {
        long purse = purse();
        if (lastPurse >= 0 && purse != lastPurse) {
            floaters.add(new Floater(purse - lastPurse, now));
        }
        lastPurse = purse;
        shownPurse = Mth.lerp(Math.min(1, frameSeconds * 9), shownPurse, purse);
        if (Math.abs(shownPurse - purse) < 0.5) {
            shownPurse = purse;
        }
    }

    private void drawFooter(GuiGraphicsExtractor g, long now) {
        int y = top + windowHeight - FOOTER + 1;
        int x = left + PAD;
        int room = windowWidth - PAD * 2 - font.width("ESC to close") - 10;
        long age = now - noticeAt;
        if (notice != null && age < NOTICE_MILLIS) {
            float in = Draw.easeOut(age / 160f);
            float out = Mth.clamp((NOTICE_MILLIS - age) / 400f, 0, 1);
            float alpha = Math.min(in, out);
            int color = noticeGood ? Theme.GREEN : Theme.RED;
            int shift = Math.round((1 - in) * 6);
            Draw.rounded(g, x, y + 1, 3, 6, Draw.withAlpha(color, alpha));
            Draw.fitText(g, font, notice, x + 7 + shift, y, room - 7 - shift, Draw.withAlpha(Theme.TEXT, Math.max(alpha, 0.05f)));
        } else {
            Draw.fitText(g, font, hints(), x, y, room, Theme.DIM);
        }
        Draw.rightText(g, font, "ESC to close", left + windowWidth - PAD, y, Theme.DIM);
    }

    // ------------------------------------------------------------------ helpers for bodies

    /** "⛁ 1,234" with the SkyRPG coin icon. */
    protected static String coins(long amount) {
        return StatTypes.COIN_ICON + " " + Msg.number(amount);
    }

    protected boolean inside(int mouseX, int mouseY, int x, int y, int w, int h) {
        return mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;
    }

    /**
     * Eases a hover value towards 1 while {@code hovered}, so highlights fade instead of snapping, and
     * shows the pointing hand over anything hovered.
     */
    protected float hover(GuiGraphicsExtractor g, String key, boolean hovered) {
        float value = Mth.lerp(Math.min(1, frameSeconds * 16), hover.getOrDefault(key, 0f), hovered ? 1 : 0);
        hover.put(key, value);
        if (hovered) {
            g.requestCursor(CursorTypes.POINTING_HAND);
        }
        return value;
    }

    /** 0..1 while the white "pressed" flash on {@code key} fades, else 0. */
    protected float flash(String key) {
        if (!key.equals(flashKey)) {
            return 0;
        }
        float t = (Util.getMillis() - flashAt) / 220f;
        return t >= 1 ? 0 : 1 - t;
    }

    protected void flash(String key, boolean sound) {
        flashKey = key;
        flashAt = Util.getMillis();
        if (sound) {
            minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK.value(), 1.5f, 0.35f));
        }
    }

    /** Registers a clickable area for this frame, in window space. */
    protected void hit(int x, int y, int w, int h, String key, ClickHandler handler) {
        if (w > 0 && h > 0) {
            hits.add(new Hit(x, y, w, h, key, handler));
        }
    }

    protected void tooltip(List<Component> lines, int color) {
        tooltip = lines;
        tooltipAccent = color;
    }

    /**
     * A flat SkyRPG button tinted with {@code color}. Disabled buttons go grey and ignore clicks. Left
     * click runs {@code action} with the usual click sound and a short flash.
     */
    protected void button(GuiGraphicsExtractor g, String key, int x, int y, int w, int h, Component label, int color, boolean enabled,
                          int mouseX, int mouseY, Runnable action) {
        boolean hovered = enabled && inside(mouseX, mouseY, x, y, w, h);
        float hv = hover(g, key, hovered);
        int fill = enabled ? Draw.mix(Theme.CARD, color, 0.16f + 0.16f * hv) : Theme.CARD;
        int border = enabled ? Draw.mix(Theme.CARD_BORDER, color, 0.45f + 0.4f * hv) : Theme.CARD_BORDER;
        Draw.panel(g, x, y, w, h, fill, border);
        if (enabled) {
            g.fill(x + 2, y + 1, x + w - 2, y + 2, Draw.withAlpha(Draw.lighten(color, 0.4f), 0.16f + 0.2f * hv));
        }
        float pressed = flash(key);
        if (pressed > 0) {
            Draw.rounded(g, x + 1, y + 1, w - 2, h - 2, Draw.withAlpha(0xFFFFFFFF, 0.28f * pressed));
        }
        Draw.fit(g, font, label, x + w / 2, y + (h - 8) / 2, w - 6, Draw.Align.CENTER, enabled ? Theme.TEXT : Theme.DIM);
        if (enabled) {
            hit(x, y, w, h, key, (button, shift) -> {
                if (button != InputConstants.MOUSE_BUTTON_LEFT) {
                    return false;
                }
                flash(key, true);
                action.run();
                return true;
            });
        }
    }

    /** Cuts a string to {@code width} pixels, ending in "…" if it had to be shortened. */
    protected String fit(String text, int width) {
        return Draw.ellipsize(font, text, width);
    }

    /** "⛁ 1,234,567" if it fits in {@code width}, else "⛁ 1.23M". */
    protected String coinsFor(long amount, int width) {
        return Draw.numberFor(font, coins(amount), width);
    }

    // ------------------------------------------------------------------ input

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        double x = event.x();
        double y = event.y() - slide;
        for (int i = hits.size() - 1; i >= 0; i--) {
            Hit hit = hits.get(i);
            if (x >= hit.x() && x < hit.x() + hit.w() && y >= hit.y() && y < hit.y() + hit.h()
                    && hit.handler().click(event.button(), event.hasShiftDown())) {
                return true;
            }
        }
        return super.mouseClicked(event, doubleClick);
    }

    /** The mouse position in window space, for scroll handlers. */
    protected double windowY(double screenY) {
        return screenY - slide;
    }
}
