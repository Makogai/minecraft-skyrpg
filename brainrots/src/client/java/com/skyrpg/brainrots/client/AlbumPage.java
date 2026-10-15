package com.skyrpg.brainrots.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.skyrpg.brainrots.Album;
import com.skyrpg.brainrots.BrainrotEntity;
import com.skyrpg.brainrots.BrainrotKind;
import com.skyrpg.brainrots.SkyRpgBrainrots;
import com.skyrpg.core.client.menu.MenuPage;
import com.skyrpg.core.client.menu.PageContext;
import com.skyrpg.core.client.ui.Draw;
import com.skyrpg.core.client.ui.Theme;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * K menu > Brainrots (or J): the album. A card per brainrot, a turning 3D model once you've caught one ("?" until
 * then), its rarity colour around it and how many you've caught. Click a card to see it up close and hear the
 * narrator say its name.
 */
final class AlbumPage implements MenuPage {
    static final Identifier ID = SkyRpgBrainrots.id("album");
    private static final int COLUMNS = 5;
    private static final int GAP = 5;
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("d MMM yyyy").withZone(ZoneId.systemDefault());

    private BrainrotKind selected = BrainrotKind.TUNG_TUNG_SAHUR;
    /** Until you click a card, the detail panel shows your newest catch. */
    private boolean chosen;

    /** Where each card was drawn this frame, for clicks. */
    private final List<int[]> cards = new ArrayList<>();
    private int[] listen;

    @Override
    public Identifier id() {
        return ID;
    }

    @Override
    public Component title() {
        return Component.literal("Brainrots");
    }

    @Override
    public Component subtitle() {
        Album album = album();
        return Component.literal(album.discovered() + " of " + BrainrotKind.values().length + " caught. Find them out in the world and right-click to catch");
    }

    @Override
    public ItemStack icon() {
        return new ItemStack(Items.NOTE_BLOCK);
    }

    @Override
    public int order() {
        return 45;
    }

    private static Album album() {
        Minecraft minecraft = Minecraft.getInstance();
        return minecraft.player == null ? Album.EMPTY : minecraft.player.getAttachedOrElse(Album.ATTACHMENT, Album.EMPTY);
    }

    @Override
    public void render(PageContext ctx) {
        GuiGraphicsExtractor g = ctx.graphics();
        Font font = ctx.font();
        Album album = album();
        cards.clear();
        int total = BrainrotKind.values().length;
        int found = album.discovered();

        // Header: progress through the album.
        Draw.bar(g, ctx.x(), ctx.y() + 2, ctx.width(), 6, found / (float) total, found == total ? Theme.ACCENT : 0xFFFF7AD9);
        Draw.fit(g, font, Component.literal(found + " / " + total + " caught" + (found == total ? "  ·  BRAINROT MASTER!" : "")), ctx.x(), ctx.y() + 11,
                ctx.width() / 2, Draw.Align.LEFT, found == total ? Theme.ACCENT : Theme.MUTED);
        int caughtTotal = album.caught().values().stream().mapToInt(Integer::intValue).sum();
        Draw.fit(g, font, Component.literal(caughtTotal + " catches in all"), ctx.x() + ctx.width(), ctx.y() + 11, ctx.width() / 2,
                Draw.Align.RIGHT, Theme.DIM);

        if (!chosen) {
            album.caught().keySet().stream().map(BrainrotKind::byId).filter(java.util.Objects::nonNull)
                    .max(java.util.Comparator.comparingLong(album::firstCaught)).ifPresent(k -> selected = k);
        }
        int top = ctx.y() + 24;
        int height = ctx.height() - 24;
        int detailW = Math.min(170, ctx.width() / 3);
        int gridW = ctx.width() - detailW - 8;
        int rows = (total + COLUMNS - 1) / COLUMNS;
        int cardW = (gridW - GAP * (COLUMNS - 1)) / COLUMNS;
        int cardH = Math.min((height - GAP * (rows - 1)) / rows, (int) (cardW * 1.5f));
        for (BrainrotKind kind : BrainrotKind.values()) {
            int i = kind.ordinal();
            int x = ctx.x() + (i % COLUMNS) * (cardW + GAP);
            int y = top + (i / COLUMNS) * (cardH + GAP);
            drawCard(ctx, kind, album, x, y, cardW, cardH);
            cards.add(new int[]{x, y, cardW, cardH, i});
        }
        drawDetail(ctx, album, ctx.x() + gridW + 8, top, detailW, height);
    }

    private void drawCard(PageContext ctx, BrainrotKind kind, Album album, int x, int y, int w, int h) {
        GuiGraphicsExtractor g = ctx.graphics();
        Font font = ctx.font();
        boolean has = album.has(kind);
        boolean hover = ctx.hovered(x, y, w, h);
        boolean picked = kind == selected;
        int rarity = kind.rarity.color;
        int fill = has ? (hover ? Theme.CARD_HOVER : Theme.CARD) : (hover ? 0xFF141922 : 0xFF0E1218);
        int border = picked ? Theme.ACCENT : has ? Draw.withAlpha(rarity, hover ? 1f : 0.7f) : (hover ? Draw.withAlpha(rarity, 0.5f) : Theme.CARD_BORDER);
        Draw.panel(g, x, y, w, h, fill, border);
        // A rarity strip along the top.
        g.fill(x + 1, y + 1, x + w - 1, y + 3, Draw.withAlpha(rarity, has ? 0.85f : 0.25f));
        if (has) {
            // The name on two (or three) small lines under the model, how many you've caught in the corner.
            String[] lines = nameLines(font, kind.displayName, w - 4);
            float scale = 0.75f;
            for (String line : lines) {
                scale = Math.min(scale, Math.max(0.5f, (w - 4) / (float) Math.max(1, font.width(line))));
            }
            int lineH = Math.round(8 * scale) + 1;
            int pictureH = h - 6 - lines.length * lineH;
            BrainrotEntity puppet = Puppets.brainrot(kind);
            if (puppet != null) {
                int size = (int) Math.min(pictureH / Math.max(0.6f, kind.height) * 0.78f, w / Math.max(0.6f, kind.width) * 0.5f);
                Draw.entityFollowsMouse(g, x + 2, y + 5, x + w - 2, y + 3 + pictureH, Math.max(8, size), ctx.mouseX(), ctx.mouseY(), puppet);
            }
            Draw.fit(g, font, Component.literal("×" + album.count(kind)), x + w - 3, y + 5, w / 2, 0.75f, 0.6f, Draw.Align.RIGHT, rarity, false);
            for (int i = 0; i < lines.length; i++) {
                Draw.fit(g, font, Component.literal(lines[i]), x + w / 2, y + h - 3 - (lines.length - i) * lineH, w - 4, scale, scale,
                        Draw.Align.CENTER, Theme.TEXT, false);
            }
        } else {
            int pictureH = h - 12;
            Draw.scaledText(g, font, Component.literal("?"), x + w / 2 - 6, y + pictureH / 2 - 7, 2.4f, Draw.withAlpha(rarity, 0.5f));
            Draw.fit(g, font, Component.literal(kind.rarity.label), x + w / 2, y + h - 10, w - 4, 0.75f, 0.55f, Draw.Align.CENTER,
                    Draw.withAlpha(rarity, 0.65f), false);
        }
        if (hover) {
            List<Component> tip = new ArrayList<>();
            tip.add(has ? Component.literal(kind.displayName).withColor(rarity & 0xFFFFFF) : Component.literal("Not caught yet"));
            tip.add(Component.literal(kind.rarity.label).withColor(rarity & 0xFFFFFF));
            tip.add(Component.literal("Found: " + kind.habitat.label).withColor(Theme.MUTED));
            if (has) {
                tip.add(Component.literal("Click to hear it").withColor(0xFF7AD9));
            }
            ctx.tooltip(tip, rarity);
        }
    }

    private void drawDetail(PageContext ctx, Album album, int x, int y, int w, int h) {
        GuiGraphicsExtractor g = ctx.graphics();
        Font font = ctx.font();
        BrainrotKind kind = selected;
        boolean has = album.has(kind);
        int rarity = kind.rarity.color;
        Draw.panel(g, x, y, w, h, Theme.CARD, Draw.withAlpha(rarity, has ? 0.8f : 0.35f));
        // The lore is drawn a little small so it all fits; the picture gets whatever room is left.
        float loreScale = 0.8f;
        String text = has ? kind.lore : "You haven't caught this one yet. Keep exploring!";
        List<FormattedCharSequence> lore = Draw.wrap(font, Component.literal(text), (int) ((w - 16) / loreScale));
        int loreLineH = 8;
        int below = 10 + 12 + 17 + lore.size() * loreLineH + 4 + 11 + (has ? 11 + 22 : 4);
        int pictureH = Math.max(44, Math.min(120, h - 4 - below));
        Draw.rounded(g, x + 4, y + 4, w - 8, pictureH, 0xFF0B1220);
        if (has) {
            BrainrotEntity puppet = Puppets.brainrot(kind);
            if (puppet != null) {
                int size = (int) Math.min(pictureH / Math.max(0.6f, kind.height) * 0.75f, (w - 8) / Math.max(0.6f, kind.width) * 0.45f);
                Draw.entityFollowsMouse(g, x + 4, y + 6, x + w - 4, y + 2 + pictureH, Math.max(10, size), ctx.mouseX(), ctx.mouseY(), puppet);
            }
        } else {
            Draw.scaledText(g, font, Component.literal("?"), x + w / 2 - 10, y + pictureH / 2 - 12, 4f, Draw.withAlpha(rarity, 0.45f));
        }
        int ty = y + pictureH + 10;
        Draw.fit(g, font, Component.literal(has ? kind.displayName : "???"), x + 8, ty, w - 16, Draw.Align.LEFT, has ? rarity : Theme.MUTED);
        ty += 12;
        String label = kind.rarity.label.toUpperCase(java.util.Locale.ROOT);
        int pillW = font.width(label) + 10;
        Draw.rounded(g, x + 8, ty, pillW, 12, Draw.withAlpha(rarity, 0.18f));
        Draw.fit(g, font, Component.literal(label), x + 8 + pillW / 2, ty + 2, pillW, Draw.Align.CENTER, rarity);
        ty += 17;
        int loreEnd = y + h - (has ? 11 + 11 + 22 : 11 + 4);
        for (FormattedCharSequence line : lore) {
            if (ty + loreLineH > loreEnd) {
                break;
            }
            g.pose().pushMatrix();
            g.pose().translate(x + 8, ty);
            g.pose().scale(loreScale, loreScale);
            g.text(font, line, 0, 0, Theme.TEXT, false);
            g.pose().popMatrix();
            ty += loreLineH;
        }
        ty += 4;
        Draw.fit(g, font, Component.literal("Found: " + kind.habitat.label), x + 8, ty, w - 16, Draw.Align.LEFT, Theme.MUTED);
        ty += 11;
        if (has) {
            String when = DATE.format(Instant.ofEpochMilli(album.firstCaught(kind)));
            Component caught = Component.literal("Caught ×" + album.count(kind));
            Draw.fit(g, font, caught, x + 8, ty, w - 16, Draw.Align.LEFT, Theme.MUTED);
            Draw.fit(g, font, Component.literal(when), x + w - 8, ty, w - 20 - font.width(caught), Draw.Align.RIGHT, Theme.DIM);
            // "Hear it" button.
            int bw = w - 16;
            int by = y + h - 22;
            boolean over = ctx.hovered(x + 8, by, bw, 16);
            Draw.panel(g, x + 8, by, bw, 16, over ? 0xFF3A1B33 : 0xFF2A1426, 0xFFFF7AD9);
            Draw.fit(g, font, Component.literal("▶ Hear it"), x + 8 + bw / 2, by + 4, bw, Draw.Align.CENTER, 0xFFFF9AE2);
            listen = new int[]{x + 8, by, bw, 16};
        } else {
            listen = null;
        }
    }

    /**
     * A name split into two lines of about the same width ("Tung Tung" / "Tung Sahur"), or three when two would have
     * to be shrunk below readable in {@code width} ("La Vaca" / "Saturno" / "Saturnita").
     */
    static String[] nameLines(Font font, String name, int width) {
        String[] words = name.split(" ");
        String[] two = split(font, words, 2);
        if (words.length < 3 || widest(font, two) * 0.6f <= width) {
            return two;
        }
        return split(font, words, 3);
    }

    /** The words in {@code count} lines (fewer if there aren't enough words), the widest line as narrow as it can be. */
    private static String[] split(Font font, String[] words, int count) {
        count = Math.min(count, words.length);
        if (count <= 1) {
            return new String[]{String.join(" ", words)};
        }
        String[] best = null;
        int n = words.length;
        for (int a = 1; a < n; a++) {
            if (count == 2) {
                best = narrower(font, best, new String[]{join(words, 0, a), join(words, a, n)});
                continue;
            }
            for (int b = a + 1; b < n; b++) {
                best = narrower(font, best, new String[]{join(words, 0, a), join(words, a, b), join(words, b, n)});
            }
        }
        return best;
    }

    private static String[] narrower(Font font, String[] best, String[] lines) {
        return best == null || widest(font, lines) < widest(font, best) ? lines : best;
    }

    private static String join(String[] words, int from, int to) {
        return String.join(" ", java.util.Arrays.copyOfRange(words, from, to));
    }

    private static int widest(Font font, String[] lines) {
        int widest = 0;
        for (String line : lines) {
            widest = Math.max(widest, font.width(line));
        }
        return widest;
    }

    private void play(BrainrotKind kind) {
        if (kind.voice != null) {
            Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(kind.voice, 1.0f, 1.0f));
        }
    }

    @Override
    public boolean mouseClicked(PageContext ctx, double mx, double my, int button) {
        if (button != InputConstants.MOUSE_BUTTON_LEFT) {
            return false;
        }
        if (listen != null && mx >= listen[0] && mx < listen[0] + listen[2] && my >= listen[1] && my < listen[1] + listen[3]) {
            play(selected);
            return true;
        }
        for (int[] card : cards) {
            if (mx >= card[0] && mx < card[0] + card[2] && my >= card[1] && my < card[1] + card[3]) {
                selected = BrainrotKind.byOrdinal(card[4]);
                chosen = true;
                if (album().has(selected)) {
                    play(selected);
                } else {
                    Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0f));
                }
                return true;
            }
        }
        return false;
    }
}
