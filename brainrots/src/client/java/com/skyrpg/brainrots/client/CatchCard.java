package com.skyrpg.brainrots.client;

import com.skyrpg.brainrots.BrainrotEntity;
import com.skyrpg.brainrots.BrainrotKind;
import com.skyrpg.brainrots.CatchNews;
import com.skyrpg.brainrots.SkyRpgBrainrots;
import com.skyrpg.core.client.config.Setting;
import com.skyrpg.core.client.config.SkyRpgSettings;
import com.skyrpg.core.client.hud.HudAnchor;
import com.skyrpg.core.client.hud.HudWidget;
import com.skyrpg.core.client.ui.Draw;
import com.skyrpg.core.client.ui.Theme;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.util.Util;
import net.minecraft.world.entity.player.Player;

/**
 * When anyone on the server catches a brainrot, a card slides in for everyone: the catcher and the brainrot side by
 * side, both turning in 3D, who caught what (and whether it was their first), and the brainrot says its name. Cards
 * queue up if several come at once. A HUD widget, so it can be moved or hidden in the HUD editor; Settings >
 * Brainrots chooses card and voice, card only, or nothing.
 */
final class CatchCard implements HudWidget {
    static final String CATEGORY = "Brainrots";
    static final String CARD_AND_VOICE = "Card and voice";
    static final String CARD_ONLY = "Card only";
    static final String OFF = "Off";

    private static final Identifier ID = SkyRpgBrainrots.id("catch_card");
    private static final int WIDTH = 204;
    private static final int HEIGHT = 66;
    private static final long SHOW_MS = 7000;
    private static final long SLIDE_MS = 320;
    private static final int PINK = 0xFFFF7AD9;

    private static final Deque<CatchNews> QUEUE = new ArrayDeque<>();
    private static CatchNews current;
    private static long since;
    private static Setting mode;

    static void registerSettings() {
        SkyRpgSettings.category(CATEGORY).description("Brainrots: the cards when someone catches one");
        mode = SkyRpgSettings.choice("brainrots.catch_cards", CATEGORY, "Catch announcements",
                "When anyone on the server catches a brainrot: a card with them and the brainrot in 3D, and its voice. "
                        + "Move or resize the card in the HUD editor",
                List.of(CARD_AND_VOICE, CARD_ONLY, OFF), CARD_AND_VOICE, value -> {
                    if (OFF.equals(value)) {
                        QUEUE.clear();
                        current = null;
                    }
                }).section("Announcements").keywords("catch", "toast", "popup", "sound", "announce");
    }

    static void receive(CatchNews news) {
        if (mode != null && mode.is(OFF) || BrainrotKind.byOrdinal(news.kind()) == null) {
            return;
        }
        if (QUEUE.size() < 6) {
            QUEUE.add(news);
        }
    }

    /** Moves the queue along: the next card starts when the last one has gone. */
    static void tick(Minecraft minecraft) {
        long now = Util.getMillis();
        if (current != null && now - since > SHOW_MS) {
            current = null;
        }
        if (current != null || QUEUE.isEmpty() || minecraft.player == null) {
            return;
        }
        current = QUEUE.poll();
        since = now;
        Puppets.player(current.player(), current.name());
        BrainrotKind kind = BrainrotKind.byOrdinal(current.kind());
        // The catcher already heard it shout as they caught it.
        boolean mine = current.player().equals(minecraft.player.getUUID());
        if (!mine && kind.voice != null && (mode == null || mode.is(CARD_AND_VOICE))) {
            minecraft.getSoundManager().play(SimpleSoundInstance.forUI(kind.voice, 1.0f, 0.75f));
        }
    }

    @Override
    public Identifier id() {
        return ID;
    }

    @Override
    public Component name() {
        return Component.literal("Brainrot catches");
    }

    @Override
    public int width(Font font) {
        return WIDTH;
    }

    @Override
    public int height(Font font) {
        return HEIGHT;
    }

    @Override
    public HudAnchor defaultAnchor() {
        return HudAnchor.TOP_LEFT;
    }

    @Override
    public int defaultX() {
        return 4;
    }

    @Override
    public int defaultY() {
        return 4;
    }

    @Override
    public boolean visible() {
        return current != null;
    }

    @Override
    public void render(GuiGraphicsExtractor g, Font font, boolean editing) {
        Minecraft minecraft = Minecraft.getInstance();
        CatchNews news = current;
        long elapsed = Util.getMillis() - since;
        if (news == null) {
            if (!editing || minecraft.player == null) {
                return;
            }
            // The HUD editor: a sample card with you and Tung Tung, standing still.
            news = new CatchNews(minecraft.player.getUUID(), minecraft.player.getGameProfile().name(), 0, true, 1);
            elapsed = SHOW_MS / 2;
        }
        BrainrotKind kind = BrainrotKind.byOrdinal(news.kind());
        float in = Draw.easeOut(Mth.clamp(elapsed / (float) SLIDE_MS, 0f, 1f));
        float out = Mth.clamp((elapsed - (SHOW_MS - SLIDE_MS)) / (float) SLIDE_MS, 0f, 1f);
        int slide = Math.round((WIDTH + 10) * ((1 - in) + out * out));
        g.pose().pushMatrix();
        g.pose().translate(-slide, 0);

        int rarity = kind.rarity.color;
        int border = rarity;
        if (kind.rarity == BrainrotKind.Rarity.LEGENDARY) {
            border = Draw.mix(rarity, 0xFFFFFFFF, 0.5f + 0.5f * Mth.sin(elapsed / 160f));
        }
        Draw.shadow(g, 0, 0, WIDTH, HEIGHT, 6, 0.6f);
        Draw.panel(g, 0, 0, WIDTH, HEIGHT, 0xF00E1320, border);
        g.fill(1, 1, WIDTH - 1, 3, Draw.withAlpha(rarity, 0.85f));

        // The two of them, turning (the catcher one way, the brainrot the other), with their idle animations.
        float age = (Util.getMillis() % 3_600_000L) / 50f;
        float turn = elapsed / 1000f;
        Player player = Puppets.player(news.player(), news.name());
        if (player != null) {
            Draw.entityTurning(g, 2, 6, 48, HEIGHT - 3, 25, -25f - turn * 55f, age, player);
        }
        BrainrotEntity brainrot = Puppets.brainrot(kind);
        if (brainrot != null) {
            int boxW = 58;
            int size = (int) Math.min((HEIGHT - 14) / Math.max(0.5f, kind.height), boxW / Math.max(0.5f, kind.width) * 0.62f);
            Draw.entityTurning(g, WIDTH - boxW - 2, 6, WIDTH - 2, HEIGHT - 3, Math.max(8, size), 25f + turn * 80f, age, brainrot);
        }

        // The words between them.
        int x = 50;
        int w = WIDTH - 50 - 62;
        int mid = x + w / 2;
        Draw.fit(g, font, Component.literal(news.first() ? "✦ NEW BRAINROT!" : "✦ BRAINROT CAUGHT"), mid, 6, w, 0.75f, 0.6f,
                Draw.Align.CENTER, PINK, false);
        Draw.fit(g, font, Component.literal(news.name()), mid, 15, w, 1f, 0.7f, Draw.Align.CENTER, Theme.TEXT, true);
        String caught = news.first() ? "caught" : "caught again (×" + news.count() + ")";
        Draw.fit(g, font, Component.literal(caught), mid, 25, w, 0.75f, 0.6f, Draw.Align.CENTER, Theme.MUTED, false);
        String[] lines = AlbumPage.nameLines(font, kind.displayName, w);
        float scale = 1f;
        for (String line : lines) {
            scale = Math.min(scale, Math.max(0.6f, w / (float) Math.max(1, font.width(line))));
        }
        int lineH = Math.round(8 * scale) + 2;
        int y = lines.length > 2 ? 32 : 35;
        for (String line : lines) {
            Draw.fit(g, font, Component.literal(line), mid, y, w, scale, scale, Draw.Align.CENTER, rarity, true);
            y += lineH;
        }
        Draw.fit(g, font, Component.literal(kind.rarity.label.toUpperCase(Locale.ROOT)), mid, HEIGHT - 10, w, 0.7f, 0.6f,
                Draw.Align.CENTER, Draw.withAlpha(rarity, 0.8f), false);
        g.pose().popMatrix();
    }
}
