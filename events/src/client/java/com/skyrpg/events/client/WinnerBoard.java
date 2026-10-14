package com.skyrpg.events.client;

import com.skyrpg.core.client.config.Setting;
import com.skyrpg.core.client.config.SkyRpgSettings;
import com.skyrpg.core.client.ui.Draw;
import com.skyrpg.core.client.ui.Theme;
import com.skyrpg.core.text.Msg;
import com.skyrpg.events.Json;
import com.skyrpg.events.SkyRpgEvents;
import com.skyrpg.events.net.EventPayloads;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Random;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Util;

/**
 * The end-of-event podium, shown for about eight seconds: the pedestals rise third, second, first,
 * the winners' faces pop on top, confetti bursts when first place lands and a shimmer runs over the
 * gold pedestal. Your own result sits underneath. It's an overlay, so play carries on under it.
 */
final class WinnerBoard {
    private static final long TOTAL_MS = 8600;
    private static final long FADE_OUT_MS = 700;
    private static final int WIDTH = 290;
    private static final int HEIGHT = 208;
    private static final int COLUMN = 80;
    /** Per place 1, 2, 3: pedestal height and when it starts rising. */
    private static final int[] PEDESTAL = {0, 54, 40, 30};
    private static final int[] RISE_AT = {0, 950, 650, 350};
    private static final int RISE_MS = 500;
    private static final long CONFETTI_AT = RISE_AT[1] + RISE_MS;
    private static final int[] CONFETTI_COLORS = {Ui.GOLD, 0xFFFFFFFF, Theme.AQUA, 0xFFE84A5F, Theme.GREEN, Theme.PURPLE, 0xFFFF9F43};
    private static final Deque<EventPayloads.Results> QUEUE = new ArrayDeque<>();
    private static final List<Confetti> CONFETTI = new ArrayList<>();
    private static final Random RANDOM = new Random();
    private static EventPayloads.Results current;
    private static long shownAt;
    private static long lastFrame;
    private static long lastRain;
    /** Timeline moments already played (sounds, bursts), as bits. */
    private static int fired;
    private static Setting enabled;
    /** The current board's centre and fit-to-screen scale, for placing confetti. */
    private static float boardCenterX;
    private static float boardCenterY;
    private static float boardFit = 1;

    private static final class Confetti {
        float x;
        float y;
        float vx;
        float vy;
        float spin;
        float spinSpeed;
        float life;
        final float maxLife;
        final int color;
        final int w;
        final int h;

        Confetti(float x, float y, float vx, float vy, int color) {
            this.x = x;
            this.y = y;
            this.vx = vx;
            this.vy = vy;
            this.color = color;
            this.spin = RANDOM.nextFloat() * 6.28f;
            this.spinSpeed = (RANDOM.nextFloat() - 0.5f) * 14;
            this.maxLife = 2.2f + RANDOM.nextFloat() * 1.6f;
            this.life = maxLife;
            this.w = 2 + RANDOM.nextInt(2);
            this.h = 3 + RANDOM.nextInt(3);
        }
    }

    private WinnerBoard() {
    }

    static void init() {
        enabled = SkyRpgSettings.toggle("hud.winner_board", SkyRpgSettings.HUD, "Event winner board",
                "Show the podium with confetti when an event ends", true, null);
        HudElementRegistry.addLast(SkyRpgEvents.id("winner_board"), (g, deltaTracker) -> {
            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft.player != null && !minecraft.gui.hud.isHidden()) {
                render(g, minecraft.font);
            }
        });
    }

    static void show(EventPayloads.Results results) {
        if (enabled == null || enabled.get()) {
            QUEUE.addLast(results);
        }
    }

    static void clear() {
        QUEUE.clear();
        current = null;
        CONFETTI.clear();
    }

    /** True once per showing when the timeline passes {@code at}. */
    private static boolean once(int bit, long age, long at) {
        if (age < at || (fired & (1 << bit)) != 0) {
            return false;
        }
        fired |= 1 << bit;
        return true;
    }

    private static void render(GuiGraphicsExtractor g, Font font) {
        long now = Util.getMillis();
        if (current != null && now - shownAt > TOTAL_MS) {
            current = null;
            CONFETTI.clear();
        }
        if (current == null) {
            current = QUEUE.pollFirst();
            if (current == null) {
                return;
            }
            shownAt = now;
            lastFrame = now;
            fired = 0;
        }
        long age = now - shownAt;
        float dt = Math.min(0.05f, (now - lastFrame) / 1000f);
        lastFrame = now;
        float in = Draw.easeOut(age / 350f);
        float out = age > TOTAL_MS - FADE_OUT_MS ? Draw.easeOut((age - (TOTAL_MS - FADE_OUT_MS)) / (float) FADE_OUT_MS) : 0;
        float alpha = Math.min(in, 1 - out);
        EventPayloads.Results results = current;
        int color = results.color();

        // The board shrinks to fit small screens; everything below is laid out at full size around its centre.
        float fit = Math.min(1f, Math.min((g.guiWidth() - 12) / (float) WIDTH, (g.guiHeight() - 12) / (float) HEIGHT));
        float centerY = Math.max(6 + HEIGHT * fit / 2f, g.guiHeight() / 2f - 14);
        int x = (g.guiWidth() - WIDTH) / 2;
        int y = Math.round(centerY - HEIGHT / 2f);
        int cx = x + WIDTH / 2;
        int base = y + HEIGHT - 26;
        boolean won = results.selfPlace() == 1;
        boardCenterX = cx;
        boardCenterY = y + HEIGHT / 2f;
        boardFit = fit;

        if (once(0, age, 0)) {
            Ui.play(SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, 1, 0.8f);
        }
        if (once(1, age, RISE_AT[3] + RISE_MS - 80)) {
            Ui.play(SoundEvents.NOTE_BLOCK_BASEDRUM.value(), 0.8f, 0.6f);
        }
        if (once(2, age, RISE_AT[2] + RISE_MS - 80)) {
            Ui.play(SoundEvents.NOTE_BLOCK_BASEDRUM.value(), 1.0f, 0.6f);
        }
        if (once(3, age, CONFETTI_AT)) {
            Ui.play(SoundEvents.FIREWORK_ROCKET_LARGE_BLAST, 1, 0.7f);
            burst(onScreenX(cx), onScreenY(base - PEDESTAL[1] - 30), 90, 160, -330, -140);
            cannon(onScreenX(x + 8), onScreenY(base), 1);
            cannon(onScreenX(x + WIDTH - 8), onScreenY(base), -1);
            if (won) {
                Ui.play(SoundEvents.PLAYER_LEVELUP, 1.2f, 0.6f);
            }
        }
        if (once(4, age, CONFETTI_AT + 300)) {
            Ui.play(SoundEvents.FIREWORK_ROCKET_TWINKLE, 1, 0.5f);
        }
        if (once(5, age, CONFETTI_AT + 900)) {
            Ui.play(SoundEvents.FIREWORK_ROCKET_TWINKLE, 1.25f, 0.4f);
        }
        for (int place = 2; place <= 3; place++) {
            if (once(5 + place, age, RISE_AT[place] + RISE_MS) && !placings(results, place).isEmpty()) {
                burst(onScreenX(cx + columnOffset(place)), onScreenY(base - PEDESTAL[place] - 20), 14, 60, -150, -60);
            }
        }
        // A gentle confetti rain over the board after the burst.
        if (age > CONFETTI_AT && age < TOTAL_MS - 1800 && now - lastRain > 45) {
            lastRain = now;
            CONFETTI.add(new Confetti(onScreenX(x + RANDOM.nextFloat() * WIDTH), onScreenY(y - 6), (RANDOM.nextFloat() - 0.5f) * 30,
                    20 + RANDOM.nextFloat() * 40, CONFETTI_COLORS[RANDOM.nextInt(CONFETTI_COLORS.length)]));
        }

        // Dim the game behind it, with a soft glow of the event's colour around the board.
        g.fill(0, 0, g.guiWidth(), g.guiHeight(), Draw.withAlpha(0x9A05070C, alpha));

        float scale = (0.92f + 0.08f * in) * fit;
        g.pose().pushMatrix();
        g.pose().translate(cx, y + HEIGHT / 2f);
        g.pose().scale(scale, scale);
        g.pose().translate(-cx, -(y + HEIGHT / 2f));
        Draw.rounded(g, x - 6, y - 6, WIDTH + 12, HEIGHT + 12, Draw.withAlpha(color, 0.07f * alpha));
        Draw.rounded(g, x - 3, y - 3, WIDTH + 6, HEIGHT + 6, Draw.withAlpha(color, 0.10f * alpha));

        Draw.panel(g, x, y, WIDTH, HEIGHT, Draw.withAlpha(0xF5101420, alpha), Draw.withAlpha(Draw.mix(Theme.BORDER, color, 0.55f), alpha));
        g.fill(x + 2, y, x + WIDTH - 2, y + 1, Draw.withAlpha(color, alpha));
        g.fillGradient(x + 1, y + 1, x + WIDTH - 1, y + 44, Draw.withAlpha(color, 0.18f * alpha), 0);

        // Header
        if (alpha > 0.6f) {
            Draw.item(g, Ui.icon(results.icon()), x + 12, y + 12, 1.25f);
            Draw.item(g, Ui.icon(results.icon()), x + WIDTH - 32, y + 12, 1.25f);
        }
        Ui.centered(g, font, Component.literal("EVENT COMPLETE"), cx, y + 8, Draw.withAlpha(color, alpha));
        Draw.fit(g, font, Component.literal(results.name()), cx, y + 19, WIDTH - 2 * 38, 1.5f, 1f, Draw.Align.CENTER, Draw.withAlpha(Theme.TEXT, alpha), false);
        int players = results.participants();
        Ui.centered(g, font, Component.literal(players + (players == 1 ? " player" : " players") + " took part"), cx, y + 35, Draw.withAlpha(Theme.MUTED, alpha));

        // Podium: second on the left, first in the middle, third on the right.
        for (int place : new int[] {2, 3, 1}) {
            drawColumn(g, font, results, place, cx + columnOffset(place), base, age, alpha);
        }
        g.fill(x + 14, base, x + WIDTH - 14, base + 1, Draw.withAlpha(Theme.BORDER, alpha));

        // Your result: one line when it fits, otherwise placing and prize on two lines, shrunk to fit if needed.
        int room = WIDTH - 16;
        if (results.selfPlace() > 0) {
            String text = "You placed " + Json.ordinal(results.selfPlace()) + " with " + Msg.number(results.selfScore()) + " " + results.unit();
            int selfColor = Draw.withAlpha(results.selfPlace() <= 3 ? Draw.lighten(Ui.medal(results.selfPlace()), 0.2f) : Theme.TEXT, alpha);
            Component placing = Component.literal(text);
            Component prize = Component.literal(results.selfPrize().isEmpty() ? "" : "+" + results.selfPrize());
            Component both = Component.literal(text + (results.selfPrize().isEmpty() ? "" : "  ·  +" + results.selfPrize()));
            if (font.width(both) <= room) {
                Ui.centered(g, font, both, cx, y + HEIGHT - 16, selfColor);
            } else {
                Draw.fit(g, font, placing, cx, y + HEIGHT - 22, room, Draw.Align.CENTER, selfColor);
                Draw.fit(g, font, prize, cx, y + HEIGHT - 12, room, Draw.Align.CENTER, selfColor);
            }
        } else {
            Draw.fit(g, font, Component.literal("You didn't score this time. Next one's yours!"), cx, y + HEIGHT - 16, room, Draw.Align.CENTER,
                    Draw.withAlpha(Theme.MUTED, alpha));
        }
        g.pose().popMatrix();

        updateAndDrawConfetti(g, dt, alpha);
    }

    private static int columnOffset(int place) {
        return switch (place) {
            case 2 -> -(COLUMN + 10);
            case 3 -> COLUMN + 10;
            default -> 0;
        };
    }

    private static List<EventPayloads.Placing> placings(EventPayloads.Results results, int place) {
        return results.podium().stream().filter(p -> p.place() == place).limit(3).toList();
    }

    private static void drawColumn(GuiGraphicsExtractor g, Font font, EventPayloads.Results results, int place, int center, int base,
                                   long age, float alpha) {
        float rise = Ui.easeOutBack((age - RISE_AT[place]) / (float) RISE_MS);
        int height = Math.round(PEDESTAL[place] * rise);
        if (age < RISE_AT[place] || height <= 1) {
            return;
        }
        List<EventPayloads.Placing> winners = placings(results, place);
        int medal = Ui.medal(place);
        int left = center - COLUMN / 2;
        int top = base - height;
        boolean filled = !winners.isEmpty();

        // Pedestal
        Draw.panel(g, left, top, COLUMN, height + 1, Draw.withAlpha(Draw.mix(Theme.CARD, medal, filled ? 0.26f : 0.07f), alpha),
                Draw.withAlpha(filled ? medal : Draw.mix(Theme.BORDER, medal, 0.3f), alpha));
        g.fillGradient(left + 1, top + 1, left + COLUMN - 1, base, Draw.withAlpha(Draw.lighten(medal, 0.3f), (filled ? 0.32f : 0.08f) * alpha), 0);
        g.fill(left + 2, top + 1, left + COLUMN - 2, top + 2, Draw.withAlpha(Draw.lighten(medal, 0.5f), (filled ? 0.9f : 0.3f) * alpha));
        if (height >= 22) {
            Ui.centeredScaled(g, font, Component.literal(Integer.toString(place)), center, top + 5, 2f,
                    Draw.withAlpha(filled ? Draw.lighten(medal, 0.25f) : Theme.DIM, alpha));
        }
        String prize = winners.isEmpty() ? "" : winners.getFirst().prize();
        if (!prize.isEmpty()) {
            drawPrize(g, font, prize, center, top + (height >= 22 ? 22 : 2), base - 1, alpha);
        }
        if (place == 1 && filled && age > CONFETTI_AT) {
            shimmer(g, left, top, base, age - CONFETTI_AT, alpha);
        }

        // Winners on top: faces pop in once the pedestal lands.
        long landed = RISE_AT[place] + RISE_MS;
        float pop = Ui.easeOutBack((age - landed + 80) / 320f);
        if (age < landed - 80 || pop <= 0.01f) {
            return;
        }
        if (winners.isEmpty()) {
            Ui.centered(g, font, Component.literal("—"), center, top - 12, Draw.withAlpha(Theme.DIM, alpha));
            return;
        }
        int size = winners.size() == 1 ? 24 : 16;
        int facesWidth = winners.size() * size + (winners.size() - 1) * 4;
        int facesTop = top - 24 - size;
        g.pose().pushMatrix();
        g.pose().translate(center, facesTop + size / 2f);
        g.pose().scale(pop, pop);
        g.pose().translate(-center, -(facesTop + size / 2f));
        if (place == 1) {
            float glow = 0.2f + 0.1f * (float) Math.sin(age / 260.0);
            Draw.rounded(g, center - facesWidth / 2 - 5, facesTop - 5, facesWidth + 10, size + 10, Draw.withAlpha(medal, glow * alpha));
            crown(g, center, facesTop - 9, alpha);
        }
        for (int i = 0; i < winners.size(); i++) {
            int fx = center - facesWidth / 2 + i * (size + 4);
            Draw.rounded(g, fx - 1, facesTop - 1, size + 2, size + 2, Draw.withAlpha(medal, alpha));
            Ui.face(g, winners.get(i).player(), fx, facesTop, size, Draw.withAlpha(0xFFFFFFFF, alpha));
        }
        g.pose().popMatrix();

        String names = String.join(", ", winners.stream().map(EventPayloads.Placing::name).toList());
        boolean self = winners.stream().anyMatch(p -> Ui.isSelf(p.player()));
        Draw.fit(g, font, Component.literal(names), center, top - 21, COLUMN + 8, Draw.Align.CENTER,
                Draw.withAlpha(self ? Theme.ACCENT : Theme.TEXT, alpha * Math.min(1, pop)));
        Draw.fit(g, font, Component.literal(Msg.number(winners.getFirst().score()) + " " + results.unit()), center, top - 11, COLUMN + 8,
                Draw.Align.CENTER, Draw.withAlpha(Theme.MUTED, alpha * Math.min(1, pop)));
    }

    /**
     * The prize label ("200 coins + 2 diamonds") inside a pedestal between {@code areaTop} and
     * {@code areaBottom}: one line per prize, word-wrapped, at normal size if it fits, else smaller;
     * whatever still doesn't fit ends in "…" (the full prize is in your own line and in chat).
     */
    private static void drawPrize(GuiGraphicsExtractor g, Font font, String prize, int center, int areaTop, int areaBottom, float alpha) {
        String[] parts = prize.split(" \\+ ");
        for (float scale : new float[] {1f, Draw.MIN_TEXT_SCALE}) {
            int lineHeight = scale == 1f ? 9 : 7;
            int maxLines = (areaBottom - areaTop + (lineHeight - Math.round(8 * scale))) / lineHeight;
            if (maxLines < 1) {
                continue;
            }
            int maxWidth = Math.round((COLUMN - 8) / scale);
            List<String> lines = new ArrayList<>();
            for (String part : parts) {
                String text = "+" + part.trim();
                // Short prizes share a line: "+50 coins +4 steak".
                if (!lines.isEmpty() && lines.getLast().startsWith("+") && font.width(lines.getLast() + " " + text) <= maxWidth) {
                    lines.set(lines.size() - 1, lines.getLast() + " " + text);
                    continue;
                }
                // (No lambdas in here: a new one would renumber the confetti one below and break hot-swapping.)
                for (FormattedText wrapped : font.splitIgnoringLanguage(Component.literal(text), maxWidth)) {
                    lines.add(wrapped.getString());
                }
            }
            boolean last = scale != 1f;
            if (lines.size() > maxLines && !last) {
                continue;
            }
            if (lines.size() > maxLines) {
                lines = new ArrayList<>(lines.subList(0, maxLines));
                String cut = lines.get(maxLines - 1);
                lines.set(maxLines - 1, font.width(cut + Draw.ELLIPSIS) <= maxWidth ? cut + Draw.ELLIPSIS
                        : Draw.ellipsize(font, cut, font.width(cut) - 1));
            }
            int blockHeight = lines.size() * lineHeight - (lineHeight - Math.round(8 * scale));
            int y = areaTop + (areaBottom - areaTop - blockHeight) / 2;
            for (int i = 0; i < lines.size(); i++) {
                Draw.fit(g, font, Component.literal(lines.get(i)), center, y + i * lineHeight, COLUMN - 6, scale, scale, Draw.Align.CENTER,
                        Draw.withAlpha(Theme.GREEN, alpha), false);
            }
            return;
        }
    }

    /** Where a point of the full-size board ends up on screen once the board is shrunk to fit. */
    private static float onScreenX(float boardX) {
        return boardCenterX + (boardX - boardCenterX) * boardFit;
    }

    private static float onScreenY(float boardY) {
        return boardCenterY + (boardY - boardCenterY) * boardFit;
    }

    /** A little pixel crown over first place. */
    private static void crown(GuiGraphicsExtractor g, int center, int y, float alpha) {
        int gold = Draw.withAlpha(Ui.GOLD, alpha);
        int light = Draw.withAlpha(Draw.lighten(Ui.GOLD, 0.45f), alpha);
        g.fill(center - 7, y + 4, center + 7, y + 7, gold);
        g.fill(center - 7, y, center - 5, y + 4, gold);
        g.fill(center - 1, y - 1, center + 1, y + 4, gold);
        g.fill(center + 5, y, center + 7, y + 4, gold);
        g.fill(center - 7, y + 4, center + 7, y + 5, light);
        g.fill(center - 1, y + 5, center + 1, y + 6, Draw.withAlpha(0xFFE84A5F, alpha));
    }

    /** A slanted band of light sweeping over the gold pedestal every couple of seconds. */
    private static void shimmer(GuiGraphicsExtractor g, int left, int top, int base, long since, float alpha) {
        float phase = (since % 2400) / 1000f;
        if (phase > 1) {
            return;
        }
        int bandX = left - 20 + Math.round(phase * (COLUMN + 40));
        g.enableScissor(left + 1, top + 1, left + COLUMN - 1, base);
        g.pose().pushMatrix();
        g.pose().translate(bandX, (top + base) / 2f);
        g.pose().rotate(0.45f);
        for (int i = -5; i <= 5; i++) {
            float strength = (1 - Math.abs(i) / 6f) * 0.32f * alpha;
            g.fill(i, -70, i + 1, 70, Draw.withAlpha(0xFFFFFFFF, strength));
        }
        g.pose().popMatrix();
        g.disableScissor();
    }

    private static void burst(float x, float y, int count, float spreadX, float upMin, float upMax) {
        for (int i = 0; i < count; i++) {
            CONFETTI.add(new Confetti(x + (RANDOM.nextFloat() - 0.5f) * 16, y, (RANDOM.nextFloat() - 0.5f) * 2 * spreadX,
                    upMin + RANDOM.nextFloat() * (upMax - upMin), CONFETTI_COLORS[RANDOM.nextInt(CONFETTI_COLORS.length)]));
        }
    }

    /** Confetti shot up and inwards from a bottom corner; {@code direction} 1 = to the right. */
    private static void cannon(float x, float y, int direction) {
        for (int i = 0; i < 36; i++) {
            CONFETTI.add(new Confetti(x, y, direction * (60 + RANDOM.nextFloat() * 150), -220 - RANDOM.nextFloat() * 170,
                    CONFETTI_COLORS[RANDOM.nextInt(CONFETTI_COLORS.length)]));
        }
    }

    private static void updateAndDrawConfetti(GuiGraphicsExtractor g, float dt, float alpha) {
        if (CONFETTI.size() > 500) {
            CONFETTI.subList(0, CONFETTI.size() - 500).clear();
        }
        CONFETTI.removeIf(piece -> {
            piece.vy = Math.min(piece.vy + 300 * dt, 90);
            piece.vx *= 1 - 0.9f * dt;
            piece.x += piece.vx * dt;
            piece.y += piece.vy * dt;
            piece.spin += piece.spinSpeed * dt;
            piece.life -= dt;
            return piece.life <= 0;
        });
        for (Confetti piece : CONFETTI) {
            float fade = Math.min(1, piece.life / 0.6f) * alpha;
            // Squash it as it spins, so it reads as a paper flake tumbling.
            float flip = Math.max(0.2f, Math.abs((float) Math.cos(piece.spin * 1.7f)));
            g.pose().pushMatrix();
            g.pose().translate(piece.x, piece.y);
            g.pose().rotate(piece.spin);
            g.pose().scale(flip, 1);
            g.fill(-piece.w, -piece.h / 2 - 1, piece.w, piece.h / 2 + 1, Draw.withAlpha(piece.color, fade));
            g.pose().popMatrix();
        }
    }
}
