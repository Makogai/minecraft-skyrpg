package com.skyrpg.crates.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.skyrpg.core.client.ui.Draw;
import com.skyrpg.core.client.ui.Theme;
import com.skyrpg.crates.CratePayloads;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.util.Util;

/**
 * Opening a crate, like a CS2 case: first the contents (every prize, grouped by tier with its chance) and an Open
 * button that uses a key; then the reel, a long strip of prizes that whizzes past a gold marker, ticking as each card
 * goes by, slows down and stops on what you won. Gold ★ prizes stay hidden on the reel until one lands. The server
 * picks the prize before the reel starts and hands it over when it stops, so closing early loses nothing.
 */
public final class CrateScreen extends Screen {
    static final long ROLL_MS = 7000;
    private static final int GOLD = 0xFFFFD24A;

    private CratePayloads.Menu menu;
    private CratePayloads.Roll roll;
    private long openedAt = Util.getMillis();
    private long rollStart;
    private float landing;
    private int lastTick = Integer.MIN_VALUE;
    private boolean revealed;
    private boolean waiting;
    private final List<Btn> buttons = new ArrayList<>();

    private record Btn(int x, int y, int w, int h, boolean enabled, Runnable action) {
        boolean over(double mx, double my) {
            return mx >= x && mx < x + w && my >= y && my < y + h;
        }
    }

    private CrateScreen(CratePayloads.Menu menu) {
        super(Component.literal(menu.name()));
        this.menu = menu;
    }

    /** The server sent the contents: show them (or refresh the counts on the screen already open). */
    static void show(CratePayloads.Menu menu) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.gui.screen() instanceof CrateScreen screen && screen.menu.crate().equals(menu.crate())) {
            screen.menu = menu;
            screen.waiting = false;
            return;
        }
        minecraft.gui.setScreen(new CrateScreen(menu));
    }

    /** The server rolled: spin the reel (opening the screen again if it was closed in the meantime). */
    static void roll(CratePayloads.Roll roll) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.gui.screen() instanceof CrateScreen screen && screen.menu.crate().equals(roll.crate())) {
            screen.start(roll);
        }
    }

    private void start(CratePayloads.Roll roll) {
        this.roll = roll;
        this.menu = new CratePayloads.Menu(menu.crate(), menu.name(), menu.subtitle(), menu.icon(), menu.keyIcon(), menu.keyName(),
                menu.keyHint(), roll.crates(), roll.keys(), menu.tiers());
        rollStart = Util.getMillis();
        // Like CS2, the marker stops anywhere on the winning card, not always dead centre.
        landing = (float) (Math.random() * 0.8 - 0.4);
        lastTick = Integer.MIN_VALUE;
        revealed = false;
        waiting = false;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        extractBlurredBackground(g);
        g.fill(0, 0, width, height, 0xB8070910);
        // A dusky Halloween glow from the top and bottom.
        g.fillGradient(0, 0, width, height / 3, 0x40FF7A1A, 0x00FF7A1A);
        g.fillGradient(0, height * 2 / 3, width, height, 0x007A2BD6, 0x407A2BD6);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(g, mouseX, mouseY, partialTick);
        buttons.clear();
        drawHeader(g);
        if (roll == null) {
            drawContents(g, mouseX, mouseY);
        } else {
            drawReel(g);
        }
        drawFooter(g, mouseX, mouseY);
    }

    // ------------------------------------------------------------------ header and footer

    private void drawHeader(GuiGraphicsExtractor g) {
        int cx = width / 2;
        Draw.item(g, menu.icon(), cx - 20, 8, 2.5f);
        float wobble = Mth.sin((Util.getMillis() - openedAt) / 300f) * 0.04f;
        Draw.fit(g, font, Component.literal(menu.name().toUpperCase(Locale.ROOT)), cx, 52, width - 40, 2.0f + wobble, 1.2f, Draw.Align.CENTER, 0xFFFF9A3C, true);
        Draw.fit(g, font, Component.literal(menu.subtitle()), cx, 72, width - 40, Draw.Align.CENTER, Theme.MUTED);
    }

    private void drawFooter(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        int y = height - 34;
        String have = menu.crates() + (menu.crates() == 1 ? " crate" : " crates") + "  ·  " + menu.keys() + " " + menu.keyName()
                + (menu.keys() == 1 ? "" : "s");
        Draw.fit(g, font, Component.literal(have), 12, y + 9, width / 3, Draw.Align.LEFT, Theme.MUTED);
        boolean rolling = roll != null && !revealed;
        boolean canOpen = menu.crates() > 0 && menu.keys() > 0 && !rolling && !waiting;
        String label = roll == null ? "OPEN CRATE" : "OPEN ANOTHER";
        int bw = 150;
        int bx = width / 2 - bw / 2;
        if (!rolling) {
            button(g, bx, y, bw, 24, label, canOpen, 0xFFFF7A1A, mouseX, mouseY, this::open);
            if (!canOpen && !waiting) {
                String why = menu.crates() == 0 ? "No more crates" : menu.keyHint();
                Draw.fit(g, font, Component.literal(why), width / 2, y - 12, width - 40, Draw.Align.CENTER, 0xFFFF8A8A);
            } else if (canOpen) {
                Draw.item(g, menu.keyIcon(), bx + bw + 6, y + 4, 1f);
                Draw.fit(g, font, Component.literal("uses 1 " + menu.keyName()), bx + bw + 26, y + 8, width - bx - bw - 30,
                        Draw.Align.LEFT, Theme.DIM);
            }
        }
        button(g, width - 82, y, 70, 24, "Close", true, Theme.BORDER, mouseX, mouseY, this::onClose);
    }

    private void button(GuiGraphicsExtractor g, int x, int y, int w, int h, String text, boolean enabled, int color, int mouseX, int mouseY,
                        Runnable action) {
        boolean over = enabled && mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;
        int fill = !enabled ? 0xFF1A1E27 : over ? Draw.lighten(color, 0.15f) : color;
        Draw.panel(g, x, y, w, h, enabled ? Draw.withAlpha(fill, 0.9f) : fill, enabled ? Draw.lighten(color, 0.3f) : Theme.BORDER);
        Draw.fit(g, font, Component.literal(text), x + w / 2, y + (h - 8) / 2, w - 8, Draw.Align.CENTER, enabled ? 0xFFFFFFFF : Theme.DIM);
        buttons.add(new Btn(x, y, w, h, enabled, action));
    }

    private void open() {
        waiting = true;
        ClientPlayNetworking.send(new CratePayloads.Open(menu.crate()));
    }

    // ------------------------------------------------------------------ contents

    private void drawContents(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        int panelW = Math.min(width - 24, 600);
        int x0 = (width - panelW) / 2;
        int y0 = 90;
        // The tiers and their chances in one line.
        List<CratePayloads.Card> cards = new ArrayList<>();
        int legendW = 0;
        for (CratePayloads.Tier tier : menu.tiers()) {
            legendW += font.width(legend(tier)) + 18;
            cards.addAll(tier.cards());
        }
        int lx = width / 2 - legendW / 2;
        for (CratePayloads.Tier tier : menu.tiers()) {
            g.fill(lx, y0 + 1, lx + 7, y0 + 8, tier.color());
            g.text(font, legend(tier), lx + 10, y0, tier.color(), false);
            lx += font.width(legend(tier)) + 18;
        }
        int cardW = Mth.clamp((panelW - 8) / 7 - 6, 52, 78);
        int cardH = cardW + 14;
        int perRow = Math.max(1, (panelW + 6) / (cardW + 6));
        int rows = (cards.size() + perRow - 1) / perRow;
        int top = y0 + 16;
        int space = height - 54 - top;
        if (rows * (cardH + 6) > space) {
            cardH = Math.max(40, space / rows - 6);
            cardW = Math.max(40, cardH - 14);
            perRow = Math.max(1, (panelW + 6) / (cardW + 6));
            rows = (cards.size() + perRow - 1) / perRow;
        }
        CratePayloads.Card hovered = null;
        for (int i = 0; i < cards.size(); i++) {
            int row = i / perRow;
            int inRow = Math.min(perRow, cards.size() - row * perRow);
            int rowX = width / 2 - (inRow * (cardW + 6) - 6) / 2;
            int x = rowX + (i % perRow) * (cardW + 6);
            int y = top + row * (cardH + 6);
            boolean over = mouseX >= x && mouseX < x + cardW && mouseY >= y && mouseY < y + cardH;
            drawCard(g, cards.get(i), x, y, cardW, cardH, over ? 1f : 0f, false);
            if (over) {
                hovered = cards.get(i);
            }
        }
        if (hovered != null && !hovered.icon().isEmpty()) {
            g.setTooltipForNextFrame(font, hovered.icon(), mouseX, mouseY);
        }
    }

    private static String legend(CratePayloads.Tier tier) {
        double percent = tier.chance() * 100;
        return tier.name() + " " + (percent >= 10 ? String.format(Locale.ROOT, "%.0f%%", percent) : String.format(Locale.ROOT, "%.1f%%", percent));
    }

    // ------------------------------------------------------------------ the reel

    private void drawReel(GuiGraphicsExtractor g) {
        long elapsed = Util.getMillis() - rollStart;
        float t = Mth.clamp(elapsed / (float) ROLL_MS, 0f, 1f);
        int cardW = Mth.clamp(width / 7, 64, 104);
        int cardH = cardW + 16;
        int pitch = cardW + 6;
        int left = 14;
        int right = width - 14;
        int markerX = width / 2;
        int top = Math.max(96, height / 2 - cardH / 2 - 10);
        // Where the strip has moved to: fast at first, then a long slow stop on the winning card.
        float end = roll.winner() * pitch + cardW / 2f + landing * cardW - (markerX - left);
        float eased = 1 - (float) Math.pow(1 - t, 3.0);
        float offset = end * eased;
        // A tick each time a card's edge passes the marker.
        int under = (int) Math.floor((offset + markerX - left) / pitch);
        if (under != lastTick && t < 1f) {
            if (lastTick != Integer.MIN_VALUE) {
                sound(SoundEvents.NOTE_BLOCK_HAT.value(), 1.5f + (float) Math.random() * 0.2f, 0.5f);
            }
            lastTick = under;
        }
        if (t >= 1f && !revealed) {
            revealed = true;
            reveal();
        }

        if (revealed && rank() >= menu.tiers().size() - 2) {
            // Behind everything: light rays for the two rarest tiers.
            rays(g, width / 2, top + cardH + 46, roll.prize().color(), Draw.easeOut(Mth.clamp((elapsed - ROLL_MS) / 400f, 0f, 1f)), elapsed - ROLL_MS);
        }
        Draw.panel(g, left - 4, top - 6, right - left + 8, cardH + 12, 0xF00A0D14, Theme.BORDER);
        g.enableScissor(left, top - 4, right, top + cardH + 4);
        int first = Math.max(0, (int) ((offset) / pitch) - 1);
        for (int i = first; i < roll.reel().size(); i++) {
            int x = Math.round(left + i * pitch - offset);
            if (x > right) {
                break;
            }
            float glow = revealed && i == roll.winner() ? 0.6f + 0.4f * Mth.sin(elapsed / 120f) : 0f;
            CratePayloads.Card card = revealed && i == roll.winner() ? roll.prize() : roll.reel().get(i);
            drawCard(g, card, x, top, cardW, cardH, glow, !revealed || i != roll.winner());
        }
        // Fade the ends into the panel.
        for (int i = 0; i < 40; i += 2) {
            int alpha = (int) (200 * (1 - i / 40f));
            g.fill(left + i, top - 4, left + i + 2, top + cardH + 4, (alpha << 24) | 0x0A0D14);
            g.fill(right - i - 2, top - 4, right - i, top + cardH + 4, (alpha << 24) | 0x0A0D14);
        }
        g.disableScissor();
        // The marker.
        g.fill(markerX - 1, top - 10, markerX + 1, top + cardH + 10, GOLD);
        for (int i = 0; i < 5; i++) {
            g.fill(markerX - 5 + i, top - 14 + i, markerX + 5 - i, top - 13 + i, GOLD);
            g.fill(markerX - 5 + i, top + cardH + 13 - i, markerX + 5 - i, top + cardH + 14 - i, GOLD);
        }
        if (revealed) {
            drawPrize(g, top + cardH + 22, elapsed - ROLL_MS);
        }
    }

    private void drawPrize(GuiGraphicsExtractor g, int y, long since) {
        CratePayloads.Card prize = roll.prize();
        float in = Draw.easeOut(Mth.clamp(since / 400f, 0f, 1f));
        int cx = width / 2;
        int alpha = (int) (255 * in);
        if (alpha < 8) {
            return;
        }
        Draw.fit(g, font, Component.literal("YOU UNBOXED"), cx, y, width - 40, Draw.Align.CENTER, (alpha << 24) | 0xB4BCCB);
        Draw.fit(g, font, Component.literal(prize.name()), cx, y + 12, width - 40, 1.6f, 1f, Draw.Align.CENTER, (alpha << 24) | (prize.color() & 0xFFFFFF), true);
        Draw.fit(g, font, Component.literal(roll.tierName().toUpperCase(Locale.ROOT)), cx, y + 30, width - 40, Draw.Align.CENTER,
                (alpha << 24) | (Draw.lighten(prize.color(), 0.25f) & 0xFFFFFF));
    }

    /** Light rays turning slowly behind the prize's name, for the two rarest tiers. */
    private void rays(GuiGraphicsExtractor g, int cx, int cy, int color, float in, long since) {
        g.pose().pushMatrix();
        g.pose().translate(cx, cy);
        g.pose().rotate(since / 2400f);
        int length = (int) (170 * in);
        for (int i = 0; i < 12; i++) {
            g.pose().rotate(Mth.TWO_PI / 12);
            g.fillGradient(-5, 0, 5, length, Draw.withAlpha(color, 0.35f), Draw.withAlpha(color, 0f));
        }
        g.pose().popMatrix();
    }

    /** Where the prize's tier is in the crate, 0 = commonest. */
    private int rank() {
        int rank = 0;
        for (int i = 0; i < menu.tiers().size(); i++) {
            if (menu.tiers().get(i).name().equals(roll.tierName())) {
                rank = i;
            }
        }
        return rank;
    }

    /** A bigger fanfare the rarer the tier (counted down from the crate's rarest). */
    private void reveal() {
        int fromTop = menu.tiers().size() - 1 - rank();
        switch (fromTop) {
            case 0 -> {
                sound(SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1f);
                sound(SoundEvents.TOTEM_USE, 1.2f, 0.6f);
            }
            case 1 -> sound(SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, 1.1f, 0.9f);
            case 2 -> sound(SoundEvents.PLAYER_LEVELUP, 1.0f, 0.9f);
            case 3 -> sound(SoundEvents.PLAYER_LEVELUP, 1.4f, 0.7f);
            default -> sound(SoundEvents.EXPERIENCE_ORB_PICKUP, 1.0f, 0.8f);
        }
    }

    /** One prize card: dark tile, tier colour fading up from the bottom, the item big in the middle, its name. */
    private void drawCard(GuiGraphicsExtractor g, CratePayloads.Card card, int x, int y, int w, int h, float glow, boolean hideGold) {
        int color = card.color();
        Draw.panel(g, x, y, w, h, 0xFF141924, glow > 0 ? Draw.mix(color, 0xFFFFFFFF, glow * 0.6f) : Draw.withAlpha(color, 0.55f));
        g.fillGradient(x + 1, y + h / 3, x + w - 1, y + h - 1, Draw.withAlpha(color, 0f), Draw.withAlpha(color, 0.38f + glow * 0.2f));
        g.fill(x + 1, y + h - 4, x + w - 1, y + h - 1, color);
        int iconSize = Math.max(16, (int) (w * 0.5f));
        float scale = iconSize / 16f;
        int iconY = y + (h - 14 - iconSize) / 2;
        if (card.mystery() && hideGold) {
            Draw.fit(g, font, Component.literal("★"), x + w / 2, iconY + iconSize / 2 - 18, w, 5f, 1f, Draw.Align.CENTER, GOLD, true);
            Draw.fit(g, font, Component.literal("Rare Special Item"), x + w / 2, y + h - 15, w - 6, 0.7f, 0.5f, Draw.Align.CENTER, GOLD, false);
            return;
        }
        Draw.item(g, card.icon(), x + w / 2 - iconSize / 2, iconY, scale);
        Draw.fit(g, font, Component.literal(card.name()), x + w / 2, y + h - 15, w - 6, 0.75f, 0.5f, Draw.Align.CENTER, Theme.TEXT, false);
    }

    private void sound(SoundEvent sound, float pitch, float volume) {
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(sound, pitch, volume));
    }

    // ------------------------------------------------------------------ input

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (event.button() == InputConstants.MOUSE_BUTTON_LEFT) {
            for (Btn button : buttons) {
                if (button.enabled() && button.over(event.x(), event.y())) {
                    sound(SoundEvents.UI_BUTTON_CLICK.value(), 1f, 1f);
                    button.action().run();
                    return true;
                }
            }
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        boolean rolling = roll != null && !revealed;
        if (!rolling && (event.key() == InputConstants.KEY_RETURN || event.key() == InputConstants.KEY_SPACE)
                && menu.crates() > 0 && menu.keys() > 0 && !waiting) {
            open();
            return true;
        }
        return super.keyPressed(event);
    }
}
