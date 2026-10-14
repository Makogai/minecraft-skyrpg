package com.skyrpg.core.client.hud;

import com.skyrpg.core.SkyRpgCore;
import com.skyrpg.core.client.config.Setting;
import com.skyrpg.core.client.config.SkyRpgSettings;
import com.skyrpg.core.client.ui.Draw;
import com.skyrpg.core.client.ui.Theme;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;
import net.minecraft.world.item.ItemStack;

/**
 * Big moments as a banner that slides in at the top of the screen (skill level ups now; collections,
 * slayers and bosses later). Banners queue, so two level ups in a row both get their turn. A banner
 * grows to fit its text up to the screen width (longer text shrinks a little, then ends in "…"), and
 * drops below boss bars and HUD widgets when they're in the way and there's room.
 */
public final class Banners {
    /**
     * @param label    small heading in {@code color}, e.g. "SKILL LEVEL UP"
     * @param title    the big line, e.g. "Farming IV → V"
     * @param subtitle one quieter line under it (may be empty)
     */
    public record Banner(ItemStack icon, String label, Component title, Component subtitle, int color) {
    }

    private static final long IN_MS = 300;
    private static final long HOLD_MS = 3200;
    private static final long OUT_MS = 450;
    /** Banners grow to fit their text, but never get narrower than this. */
    private static final int MIN_WIDTH = 230;
    private static final int HEIGHT = 46;
    private static final int TOP = 22;
    private static final float TITLE_SCALE = 1.25f;
    /** Where the text starts, right of the icon. */
    private static final int TEXT_X = 46;
    private static final Deque<Banner> QUEUE = new ArrayDeque<>();
    private static Banner current;
    private static long shownAt;
    /** Where the current banner settled, so it doesn't jump if a widget appears while it's up. */
    private static int currentY = -1;
    private static Setting enabled;

    private Banners() {
    }

    public static void init() {
        enabled = SkyRpgSettings.toggle("hud.banners", SkyRpgSettings.HUD, "Big moment banners",
                "Show a banner when you level up a skill (and other milestones)", true, null);
        HudElementRegistry.attachElementAfter(VanillaHudElements.TITLE_AND_SUBTITLE, SkyRpgCore.id("banners"), (g, deltaTracker) -> {
            // HudManager draws banners after the widgets (renderOnTop), so they sit on top of them.
        });
    }

    public static void show(Banner banner) {
        if (enabled == null || enabled.get()) {
            QUEUE.addLast(banner);
        }
    }

    /** Called by HudManager once the widgets are drawn. */
    static void renderOnTop(GuiGraphicsExtractor g, Font font) {
        long now = Util.getMillis();
        if (current != null && now - shownAt > IN_MS + HOLD_MS + OUT_MS) {
            current = null;
        }
        if (current == null) {
            current = QUEUE.pollFirst();
            shownAt = now;
            currentY = -1;
            if (current == null) {
                return;
            }
        }
        long age = now - shownAt;
        float in = Draw.easeOut(age / (float) IN_MS);
        float out = age > IN_MS + HOLD_MS ? Draw.easeOut((age - IN_MS - HOLD_MS) / (float) OUT_MS) : 0;
        float alpha = Math.min(in, 1 - out);
        if (alpha <= 0.01f) {
            return;
        }
        int textWidth = Math.max(font.width(current.label()),
                Math.max(Math.round(font.width(current.title()) * TITLE_SCALE), font.width(current.subtitle())));
        int width = Math.min(g.guiWidth() - 8, Math.max(MIN_WIDTH, TEXT_X + textWidth + 12));
        int x = (g.guiWidth() - width) / 2;
        if (currentY < 0) {
            currentY = settle(font, x, width, g.guiWidth(), g.guiHeight());
        }
        int y = currentY + Math.round((1 - in) * -18) - Math.round(out * 10);
        int color = current.color();

        Draw.panel(g, x, y, width, HEIGHT, Draw.withAlpha(0xF2101420, alpha), Draw.withAlpha(Draw.mix(Theme.BORDER, color, 0.5f), alpha));
        g.fill(x + 2, y, x + width - 2, y + 1, Draw.withAlpha(color, alpha));
        // A soft glow under the icon, pulsing gently while the banner is up.
        float pulse = 0.18f + 0.08f * (float) Math.sin(age / 220.0);
        Draw.rounded(g, x + 9, y + 9, 28, 28, Draw.withAlpha(color, pulse * alpha));
        if (alpha > 0.6f) {
            Draw.item(g, current.icon(), x + 11, y + 11, 1.5f);
        }
        int room = width - TEXT_X - 10;
        Draw.fit(g, font, Component.literal(current.label()), x + TEXT_X, y + 8, room, Draw.Align.LEFT, Draw.withAlpha(color, alpha));
        Draw.fit(g, font, current.title(), x + TEXT_X, y + 19, room, TITLE_SCALE, 0.85f, Draw.Align.LEFT, Draw.withAlpha(Theme.TEXT, alpha), false);
        Draw.fit(g, font, current.subtitle(), x + TEXT_X, y + 33, room, Draw.Align.LEFT, Draw.withAlpha(Theme.MUTED, alpha));
    }

    /**
     * The banner's resting height: just under the top edge, or below boss bars and widgets that
     * overlap its span when that keeps it in the top half of the screen.
     */
    private static int settle(Font font, int x, int width, int screenWidth, int screenHeight) {
        List<HudManager.Box> inTheWay = new ArrayList<>();
        for (HudManager.Box box : HudManager.occupied(font, screenWidth, screenHeight)) {
            if (box.x() < x + width && x < box.x() + box.width() && box.y() < screenHeight / 2) {
                inTheWay.add(box);
            }
        }
        List<Integer> candidates = new ArrayList<>();
        candidates.add(TOP);
        for (HudManager.Box box : inTheWay) {
            candidates.add(box.y() + box.height() + 4);
        }
        candidates.sort(Integer::compare);
        for (int y : candidates) {
            if (y < TOP || y + HEIGHT > screenHeight / 2 + 20) {
                continue;
            }
            boolean clear = true;
            for (HudManager.Box box : inTheWay) {
                if (box.y() < y + HEIGHT && y < box.y() + box.height()) {
                    clear = false;
                    break;
                }
            }
            if (clear) {
                return y;
            }
        }
        // No room anywhere: keep the usual spot and draw over whatever is there.
        return TOP;
    }
}
