package com.skyrpg.combat.client.slayer;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.platform.cursor.CursorTypes;
import com.skyrpg.combat.slayer.SlayerPayloads;
import com.skyrpg.core.client.ui.Draw;
import com.skyrpg.core.client.ui.Theme;
import com.skyrpg.core.stats.StatTypes;
import com.skyrpg.core.text.Msg;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.util.Util;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Morwen Ashgrave, the Slayer Master. Two tabs:
 * <ul>
 *   <li>Slayer Quests: the lines down the left (your level and XP in each), the chosen line's tiers on the right as
 *   cards (fee, boss, kills needed, time, slayer XP; locked ones say the level they need). Click a tier to start it.
 *   A banner shows the quest you're on, with a cancel button (click twice).</li>
 *   <li>Spawners: the ladder as cards in order, each with its price, or the kills that unlock it and how far along you
 *   are. Click buys one, shift-click five.</li>
 * </ul>
 * The server checks everything again and sends a fresh view after every action.
 */
public final class SlayerScreen extends Screen {
    private static final int MAX_WIDTH = 460;
    private static final int MAX_HEIGHT = 292;
    private static final int HEADER = 40;
    private static final int TABS = 18;
    private static final int FOOTER = 16;
    private static final int PAD = 10;
    private static final int GAP = 6;
    private static final int LIST_WIDTH = 132;
    private static final int LINE_ROW = 38;
    private static final int BANNER = 26;
    private static final int TIER_HEIGHT = 96;
    private static final int SPAWNER_MIN_WIDTH = 96;
    private static final int SPAWNER_HEIGHT = 74;
    private static final int BULK = 5;
    private static final long NOTICE_MILLIS = 4000;
    private static final long CONFIRM_MILLIS = 3000;
    private static final String[] TAB_NAMES = {"Slayer Quests", "Spawners"};

    private SlayerPayloads.View view;
    private int tab;
    private String selected = "";
    private double scroll;
    private int left;
    private int top;
    private int windowWidth;
    private int windowHeight;
    private Component notice = Component.empty();
    private boolean noticeGood;
    private long noticeAt;
    private long cancelArmedAt;

    private SlayerScreen(SlayerPayloads.View view) {
        super(Component.literal("Slayer Master"));
        this.view = view;
        this.tab = Math.max(0, Math.min(TAB_NAMES.length - 1, view.tab()));
        pickSelected();
    }

    /** A view from the server: opens the screen, or updates the open one. */
    public static void receive(SlayerPayloads.View view) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.gui.screen() instanceof SlayerScreen open) {
            open.view = view;
            open.pickSelected();
            open.notify(view.notice(), view.good());
        } else if (!view.refresh()) {
            minecraft.gui.setScreen(new SlayerScreen(view));
        }
    }

    private void pickSelected() {
        boolean known = view.lines().stream().anyMatch(line -> line.id().equals(selected));
        if (!known) {
            selected = !view.activeLine().isEmpty() ? view.activeLine() : view.lines().isEmpty() ? "" : view.lines().getFirst().id();
        }
    }

    private void notify(Component text, boolean good) {
        if (!text.getString().isEmpty()) {
            notice = text;
            noticeGood = good;
            noticeAt = Util.getMillis();
        }
    }

    @Override
    protected void init() {
        windowWidth = Math.min(width - 24, MAX_WIDTH);
        windowHeight = Math.min(height - 24, MAX_HEIGHT);
        left = (width - windowWidth) / 2;
        top = (height - windowHeight) / 2;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        extractBlurredBackground(g);
        g.fill(0, 0, width, height, Theme.OVERLAY);
    }

    private SlayerPayloads.LineView selectedLine() {
        for (SlayerPayloads.LineView line : view.lines()) {
            if (line.id().equals(selected)) {
                return line;
            }
        }
        return null;
    }

    private boolean onQuest() {
        return !view.activeLine().isEmpty();
    }

    // ---------------------------------------------------------------- drawing

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(g, mouseX, mouseY, partialTick);
        Draw.panel(g, left, top, windowWidth, windowHeight, Theme.WINDOW, Theme.BORDER);
        g.fill(left + 1, top, left + windowWidth - 1, top + 2, Theme.PURPLE);

        Draw.rounded(g, left + PAD, top + 8, 24, 24, Theme.CARD);
        Draw.item(g, new ItemStack(Items.WITHER_SKELETON_SKULL), left + PAD + 4, top + 12, 1f);
        Draw.text(g, font, Component.literal("Morwen Ashgrave").withColor(Theme.TEXT)
                .append(Component.literal("  ·  Slayer Master").withColor(Theme.PURPLE)), left + PAD + 32, top + 10, Theme.TEXT);
        Draw.fitText(g, font, tab == 0 ? "Pay her fee, slay their kind, then beat the boss that rises for you"
                : "Each spawner unlocks once you've slain enough of the mob before it", left + PAD + 32, top + 21,
                windowWidth - PAD * 2 - 32, Theme.MUTED);

        int tx = left + PAD;
        int ty = top + HEADER;
        for (int i = 0; i < TAB_NAMES.length; i++) {
            int w = font.width(TAB_NAMES[i]) + 12;
            boolean chosen = i == tab;
            boolean hovered = inside(mouseX, mouseY, tx, ty, w, TABS - 4);
            Draw.rounded(g, tx, ty, w, TABS - 4, chosen ? Draw.withAlpha(Theme.PURPLE, 0.2f) : hovered ? Theme.CARD_HOVER : Theme.CARD);
            Draw.text(g, font, TAB_NAMES[i], tx + 6, ty + 3, chosen ? Theme.PURPLE : hovered ? Theme.TEXT : Theme.MUTED);
            if (hovered) {
                g.requestCursor(CursorTypes.POINTING_HAND);
            }
            tx += w + 4;
        }

        if (tab == 0) {
            drawQuests(g, mouseX, mouseY);
        } else {
            drawSpawners(g, mouseX, mouseY);
        }

        int fy = top + windowHeight - FOOTER + 2;
        if (Util.getMillis() - noticeAt < NOTICE_MILLIS && !notice.getString().isEmpty()) {
            Draw.fitText(g, font, notice, left + PAD, fy, windowWidth - PAD * 2, noticeGood ? Theme.GREEN : Theme.RED);
        } else {
            Draw.fitText(g, font, tab == 0 ? "Click a tier to start it  ·  Mobs from spawners don't count  ·  Esc to close"
                    : "Click to buy one  ·  Shift-click to buy " + BULK + "  ·  Esc to close", left + PAD, fy, windowWidth - PAD * 2, Theme.DIM);
        }
        Draw.drawQueuedTooltip(g, font, mouseX, mouseY);
    }

    private int contentTop() {
        return top + HEADER + TABS;
    }

    private int contentHeight() {
        return windowHeight - HEADER - TABS - FOOTER - 4;
    }

    // ---------------------------------------------------------------- quests tab

    private void drawQuests(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        int y0 = contentTop();
        if (view.lines().isEmpty()) {
            Draw.text(g, font, "No slayer quests yet.", left + PAD, y0 + 8, Theme.MUTED);
            return;
        }
        for (int i = 0; i < view.lines().size(); i++) {
            SlayerPayloads.LineView line = view.lines().get(i);
            int x = left + PAD;
            int y = y0 + i * (LINE_ROW + 4);
            boolean chosen = line.id().equals(selected);
            boolean hovered = inside(mouseX, mouseY, x, y, LIST_WIDTH, LINE_ROW);
            int color = 0xFF000000 | line.color();
            Draw.panel(g, x, y, LIST_WIDTH, LINE_ROW, chosen ? Draw.mix(Theme.CARD, color, 0.16f) : hovered ? Theme.CARD_HOVER : Theme.CARD,
                    chosen ? Draw.mix(Theme.CARD_BORDER, color, 0.7f) : Theme.CARD_BORDER);
            Draw.item(g, line.icon(), x + 5, y + 5, 1f);
            Draw.fitText(g, font, line.name(), x + 25, y + 5, LIST_WIDTH - 30, color);
            Draw.text(g, font, "Level " + line.level(), x + 25, y + 16, Theme.MUTED);
            if (line.id().equals(view.activeLine())) {
                boolean pulse = (Util.getMillis() / 500) % 2 == 0;
                Draw.rightText(g, font, "●", x + LIST_WIDTH - 5, y + 16, pulse ? color : Theme.DIM);
            }
            float progress = line.nextXp() < 0 ? 1f : line.xp() / (float) Math.max(1, line.nextXp());
            Draw.bar(g, x + 5, y + LINE_ROW - 9, LIST_WIDTH - 10, 5, progress, Theme.PURPLE);
            if (hovered) {
                g.requestCursor(CursorTypes.POINTING_HAND);
                Draw.queueTooltip(List.of(Component.literal(line.name() + " Slayer " + line.level()).withColor(color),
                        Component.literal(line.nextXp() < 0 ? "Max level!" : Msg.number(line.xp()) + " / " + Msg.number(line.nextXp()) + " XP to level "
                                + (line.level() + 1)).withColor(Theme.MUTED),
                        Component.literal(line.perLevel() + " per level").withColor(Theme.GREEN)), color);
            }
        }

        SlayerPayloads.LineView line = selectedLine();
        if (line == null) {
            return;
        }
        int x = left + PAD + LIST_WIDTH + GAP + 4;
        int w = left + windowWidth - PAD - x;
        int y = y0;
        if (onQuest()) {
            drawBanner(g, mouseX, mouseY, x, y, w);
            y += BANNER + 6;
        }
        int color = 0xFF000000 | line.color();
        Draw.scaledText(g, font, Component.literal(line.name()).withColor(color), x, y, 1.25f, color);
        String level = "Level " + line.level() + (line.nextXp() < 0 ? "  (max)" : "  ·  " + Msg.number(line.xp()) + " / " + Msg.number(line.nextXp()) + " XP");
        Draw.fitRight(g, font, level, x + w, y + 2, w / 2, Theme.MUTED);
        y += 15;
        Draw.fitText(g, font, Component.literal("Slay: ").withColor(Theme.MUTED).append(Component.literal(line.counts()).withColor(Theme.TEXT)),
                x, y, w, Theme.TEXT);
        y += 11;
        Draw.fitText(g, font, Component.literal("Each level: ").withColor(Theme.MUTED).append(Component.literal(line.perLevel()).withColor(Theme.GREEN)),
                x, y, w, Theme.TEXT);
        y += 15;

        int count = line.tiers().size();
        if (count == 0) {
            return;
        }
        int columns = Math.min(count, 4);
        int cardWidth = (w - GAP * (columns - 1)) / columns;
        for (int i = 0; i < count; i++) {
            int cx = x + (i % columns) * (cardWidth + GAP);
            int cy = y + (i / columns) * (TIER_HEIGHT + GAP);
            boolean hovered = inside(mouseX, mouseY, cx, cy, cardWidth, TIER_HEIGHT);
            drawTier(g, line, i, cx, cy, cardWidth, hovered);
        }
    }

    private void drawBanner(GuiGraphicsExtractor g, int mouseX, int mouseY, int x, int y, int w) {
        SlayerPayloads.Status status = SlayerWidget.status;
        int color = status.name().isEmpty() ? Theme.PURPLE : 0xFF000000 | status.color();
        Draw.panel(g, x, y, w, BANNER, Draw.mix(Theme.CARD, color, 0.12f), Draw.mix(Theme.CARD_BORDER, color, 0.6f));
        String what = status.name().isEmpty() ? "On a quest" : "On a quest: " + status.name() + " " + Msg.roman(status.tier());
        Draw.fitText(g, font, what, x + 6, y + 4, w - 90, color);
        String detail = status.bossUp() ? "The boss is up!  " + status.secondsLeft() / 60 + ":" + String.format("%02d", status.secondsLeft() % 60) + " left"
                : Msg.number(status.progress()) + " / " + Msg.number(status.needed()) + " kills";
        Draw.fitText(g, font, detail, x + 6, y + 14, w - 90, status.bossUp() ? Theme.RED : Theme.MUTED);
        boolean armed = Util.getMillis() - cancelArmedAt < CONFIRM_MILLIS;
        String label = armed ? "Sure? Click" : "Cancel quest";
        int bw = font.width(label) + 12;
        int bx = x + w - bw - 5;
        int by = y + 6;
        boolean hovered = inside(mouseX, mouseY, bx, by, bw, 14);
        Draw.rounded(g, bx, by, bw, 14, hovered || armed ? Draw.withAlpha(Theme.RED, 0.3f) : Draw.withAlpha(Theme.RED, 0.14f));
        Draw.text(g, font, label, bx + 6, by + 3, Theme.RED);
        if (hovered) {
            g.requestCursor(CursorTypes.POINTING_HAND);
            Draw.queueTooltip(List.of(Component.literal("Cancel this quest").withColor(Theme.RED),
                    Component.literal("The fee isn't refunded.").withColor(Theme.MUTED)), Theme.RED);
        }
    }

    private int[] cancelButton() {
        int x = left + PAD + LIST_WIDTH + GAP + 4;
        int w = left + windowWidth - PAD - x;
        boolean armed = Util.getMillis() - cancelArmedAt < CONFIRM_MILLIS;
        int bw = font.width(armed ? "Sure? Click" : "Cancel quest") + 12;
        return new int[] {x + w - bw - 5, contentTop() + 6, bw, 14};
    }

    private void drawTier(GuiGraphicsExtractor g, SlayerPayloads.LineView line, int index, int x, int y, int w, boolean hovered) {
        SlayerPayloads.TierView tier = line.tiers().get(index);
        int color = 0xFF000000 | line.color();
        int number = index + 1;
        boolean active = line.id().equals(view.activeLine()) && view.activeTier() == number;
        boolean usable = tier.unlocked() && !onQuest();
        Draw.panel(g, x, y, w, TIER_HEIGHT, hovered && usable ? Theme.CARD_HOVER : Theme.CARD,
                active ? color : hovered && usable ? Draw.mix(Theme.CARD_BORDER, color, 0.8f) : Theme.CARD_BORDER);
        Draw.rounded(g, x + 4, y + 4, w - 8, 16, Draw.withAlpha(color, tier.unlocked() ? 0.16f : 0.06f));
        Draw.fit(g, font, Component.literal("Tier " + Msg.roman(number)), x + w / 2, y + 8, w - 12, Draw.Align.CENTER,
                tier.unlocked() ? color : Theme.DIM);
        int ry = y + 25;
        row(g, x, ry, w, "Fee", StatTypes.COIN_ICON + " " + Draw.compactNumbers(Msg.number(tier.fee())), Theme.ACCENT, tier.unlocked());
        row(g, x, ry + 11, w, "Kills", Msg.number(tier.xp()), Theme.TEXT, tier.unlocked());
        row(g, x, ry + 22, w, "Boss", "♥ " + Draw.compactNumbers(Msg.number(tier.bossHealth())), Theme.RED, tier.unlocked());
        row(g, x, ry + 33, w, "Level", String.valueOf(tier.bossLevel()), Theme.TEXT, tier.unlocked());
        row(g, x, ry + 44, w, "Time", tier.minutes() + " min", Theme.TEXT, tier.unlocked());
        row(g, x, ry + 55, w, "XP", "+" + Msg.number(tier.slayerXp()), Theme.PURPLE, tier.unlocked());
        if (!tier.unlocked()) {
            Draw.rounded(g, x + 1, y + 1, w - 2, TIER_HEIGHT - 2, Draw.withAlpha(0xFF05070B, 0.45f));
            Draw.rounded(g, x + 4, y + TIER_HEIGHT / 2 - 7, w - 8, 14, Draw.withAlpha(0xFF05070B, 0.85f));
            Draw.fit(g, font, Component.literal("Needs Lv " + tier.requires()), x + w / 2, y + TIER_HEIGHT / 2 - 4, w - 12, Draw.Align.CENTER,
                    Theme.RED);
        } else if (active) {
            Draw.fit(g, font, Component.literal("● Active"), x + w - 4, y + TIER_HEIGHT - 11, w - 8, Draw.Align.RIGHT, color);
        }
        if (hovered) {
            if (usable) {
                g.requestCursor(CursorTypes.POINTING_HAND);
            }
            Draw.queueTooltip(tierTooltip(line, tier, number), color);
        }
    }

    private void row(GuiGraphicsExtractor g, int x, int y, int w, String label, String value, int color, boolean unlocked) {
        Draw.text(g, font, label, x + 6, y, unlocked ? Theme.MUTED : Theme.DIM);
        Draw.fitRight(g, font, value, x + w - 6, y, w - 12 - font.width(label) - 4, unlocked ? color : Theme.DIM);
    }

    private List<Component> tierTooltip(SlayerPayloads.LineView line, SlayerPayloads.TierView tier, int number) {
        int color = 0xFF000000 | line.color();
        List<Component> lines = new ArrayList<>();
        lines.add(Component.literal(line.name() + " " + Msg.roman(number)).withColor(color)
                .append(tier.title().isEmpty() ? Component.empty() : Component.literal(" · " + tier.title()).withColor(Theme.MUTED)));
        lines.add(Component.literal("Slay " + Msg.number(tier.xp()) + " worth of " + line.counts() + ",").withColor(Theme.MUTED));
        lines.add(Component.literal("then beat it within " + tier.minutes() + " minutes.").withColor(Theme.MUTED));
        lines.add(Component.empty());
        lines.add(Component.literal("Boss: Level " + tier.bossLevel() + ", " + Msg.number(tier.bossHealth()) + " health").withColor(Theme.RED));
        lines.add(Component.literal("Reward: +" + Msg.number(tier.slayerXp()) + " slayer XP, tokens" + (number >= 3 ? ", a rare drop chance" : ""))
                .withColor(Theme.PURPLE));
        if (!tier.abilities().isEmpty()) {
            lines.add(Component.empty());
            lines.add(Component.literal(number > 1 ? "New this tier (plus everything before):" : "Abilities:").withColor(Theme.TEXT));
            for (String ability : tier.abilities()) {
                String[] parts = ability.split("\\|", 3);
                if (parts.length < 3) {
                    continue;
                }
                boolean attack = parts[0].equals("A");
                lines.add(Component.literal(attack ? " ⚔ " : " ⛨ ").withColor(attack ? Theme.RED : Theme.BLUE)
                        .append(Component.literal(parts[1]).withColor(attack ? 0xFFFF8A80 : 0xFF8AC4FF))
                        .append(Component.literal(": " + parts[2]).withColor(Theme.MUTED)));
            }
        }
        lines.add(Component.literal("Dying or running out of time fails it.").withColor(Theme.DIM));
        lines.add(Component.empty());
        if (!tier.unlocked()) {
            lines.add(Component.literal("Needs " + line.name() + " Slayer " + tier.requires()).withColor(Theme.RED));
        } else if (onQuest()) {
            lines.add(Component.literal("Finish or cancel your quest first").withColor(Theme.RED));
        } else {
            lines.add(Component.literal("Click").withColor(Theme.PURPLE)
                    .append(Component.literal("  start it (" + Msg.number(tier.fee()) + " coins)").withColor(Theme.MUTED)));
        }
        return lines;
    }

    // ---------------------------------------------------------------- spawners tab

    private int spawnerColumns() {
        return Math.max(1, (windowWidth - PAD * 2 + GAP) / (SPAWNER_MIN_WIDTH + GAP));
    }

    private int spawnerWidth() {
        int columns = spawnerColumns();
        return (windowWidth - PAD * 2 - GAP * (columns - 1)) / columns;
    }

    private void drawSpawners(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        List<SlayerPayloads.SpawnerView> spawners = view.spawners();
        int y0 = contentTop();
        int h = contentHeight();
        if (spawners.isEmpty()) {
            Draw.text(g, font, "No spawners for sale.", left + PAD, y0 + 8, Theme.MUTED);
            return;
        }
        int columns = spawnerColumns();
        int cardWidth = spawnerWidth();
        int rows = (spawners.size() + columns - 1) / columns;
        int content = rows * (SPAWNER_HEIGHT + GAP) - GAP;
        scroll = Mth.clamp(scroll, 0, Math.max(0, content - h));
        g.enableScissor(left + PAD - 2, y0, left + windowWidth - PAD + 2, y0 + h);
        for (int i = 0; i < spawners.size(); i++) {
            int cx = left + PAD + (i % columns) * (cardWidth + GAP);
            int cy = y0 + (i / columns) * (SPAWNER_HEIGHT + GAP) - (int) Math.round(scroll);
            if (cy + SPAWNER_HEIGHT < y0 || cy > y0 + h) {
                continue;
            }
            boolean hovered = inside(mouseX, mouseY, cx, cy, cardWidth, SPAWNER_HEIGHT) && mouseY >= y0 && mouseY < y0 + h;
            drawSpawner(g, spawners.get(i), cx, cy, cardWidth, hovered);
        }
        g.disableScissor();
        if (content > h) {
            Draw.scrollbar(g, left + windowWidth - PAD + 4, y0, h, content, scroll, Theme.DIM);
        }
    }

    private void drawSpawner(GuiGraphicsExtractor g, SlayerPayloads.SpawnerView spawner, int x, int y, int w, boolean hovered) {
        boolean open = spawner.unlocked();
        Draw.panel(g, x, y, w, SPAWNER_HEIGHT, hovered && open ? Theme.CARD_HOVER : Theme.CARD,
                hovered && open ? Draw.mix(Theme.CARD_BORDER, Theme.ACCENT, 0.7f) : open ? Draw.mix(Theme.CARD_BORDER, Theme.GREEN, 0.3f) : Theme.CARD_BORDER);
        Draw.text(g, font, String.valueOf(spawner.index() + 1), x + 5, y + 4, Theme.DIM);
        Draw.rounded(g, x + w / 2 - 15, y + 5, 30, 28, Draw.withAlpha(open ? Theme.PURPLE : Theme.DIM, hovered ? 0.2f : 0.1f));
        Draw.item(g, spawner.spawner(), x + (w - 24) / 2, y + 7, 1.5f);
        Draw.fit(g, font, Component.literal(spawner.mob() + " Spawner"), x + w / 2, y + 37, w - 8, Draw.Align.CENTER, open ? Theme.TEXT : Theme.DIM);
        if (open) {
            Draw.fit(g, font, Component.literal(StatTypes.COIN_ICON + " " + Msg.number(spawner.price())), x + w / 2, y + 50, w - 8, Draw.Align.CENTER,
                    Theme.ACCENT);
        } else {
            Draw.fit(g, font, Component.literal(Draw.compactNumbers(Msg.number(spawner.kills())) + " / "
                    + Draw.compactNumbers(Msg.number(spawner.needed())) + " " + spawner.unlockMob()), x + w / 2, y + 49, w - 8, Draw.Align.CENTER,
                    Theme.RED);
            Draw.bar(g, x + 8, y + SPAWNER_HEIGHT - 12, w - 16, 5, spawner.needed() <= 0 ? 0 : spawner.kills() / (float) spawner.needed(), Theme.RED);
        }
        if (hovered) {
            if (open) {
                g.requestCursor(CursorTypes.POINTING_HAND);
            }
            Draw.queueTooltip(spawnerTooltip(spawner), open ? Theme.ACCENT : Theme.RED);
        }
    }

    private List<Component> spawnerTooltip(SlayerPayloads.SpawnerView spawner) {
        List<Component> lines = new ArrayList<>();
        lines.add(Component.literal(spawner.mob() + " Spawner").withColor(spawner.unlocked() ? Theme.PURPLE : Theme.DIM));
        lines.add(Component.literal("Spawns " + spawner.mob() + " mobs while you're near,").withColor(Theme.MUTED));
        lines.add(Component.literal("like any spawner. Break it to pick it up.").withColor(Theme.MUTED));
        lines.add(Component.literal("Its mobs give normal loot, a quarter of the XP,").withColor(Theme.DIM));
        lines.add(Component.literal("and no slayer progress or rare drops.").withColor(Theme.DIM));
        lines.add(Component.empty());
        if (spawner.unlocked()) {
            lines.add(Component.literal("Price: " + Msg.number(spawner.price()) + " coins").withColor(Theme.ACCENT));
            lines.add(Component.literal("Click").withColor(Theme.ACCENT).append(Component.literal("  buy one").withColor(Theme.MUTED)));
            lines.add(Component.literal("Shift-click").withColor(Theme.ACCENT).append(Component.literal("  buy " + BULK + " (" + Msg.number(spawner.price() * BULK)
                    + ")").withColor(Theme.MUTED)));
        } else if (spawner.kills() >= spawner.needed()) {
            lines.add(Component.literal("Unlock the spawner before it first").withColor(Theme.RED));
        } else {
            lines.add(Component.literal("Locked: kill " + Msg.number(spawner.needed()) + " " + spawner.unlockMob()).withColor(Theme.RED));
            lines.add(Component.literal("  " + Msg.number(spawner.kills()) + " so far (" + Msg.number(spawner.needed() - spawner.kills()) + " to go)")
                    .withColor(Theme.MUTED));
        }
        return lines;
    }

    // ---------------------------------------------------------------- input

    private static boolean inside(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (event.button() != InputConstants.MOUSE_BUTTON_LEFT) {
            return super.mouseClicked(event, doubleClick);
        }
        double mx = event.x();
        double my = event.y();
        int tx = left + PAD;
        for (int i = 0; i < TAB_NAMES.length; i++) {
            int w = font.width(TAB_NAMES[i]) + 12;
            if (inside(mx, my, tx, top + HEADER, w, TABS - 4)) {
                tab = i;
                scroll = 0;
                click(1.2f);
                return true;
            }
            tx += w + 4;
        }
        if (tab == 0 ? clickQuests(mx, my) : clickSpawners(mx, my, event.hasShiftDown())) {
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }

    private boolean clickQuests(double mx, double my) {
        int y0 = contentTop();
        for (int i = 0; i < view.lines().size(); i++) {
            if (inside(mx, my, left + PAD, y0 + i * (LINE_ROW + 4), LIST_WIDTH, LINE_ROW)) {
                selected = view.lines().get(i).id();
                click(1.1f);
                return true;
            }
        }
        if (onQuest()) {
            int[] b = cancelButton();
            if (inside(mx, my, b[0], b[1], b[2], b[3])) {
                if (Util.getMillis() - cancelArmedAt < CONFIRM_MILLIS) {
                    cancelArmedAt = 0;
                    ClientPlayNetworking.send(new SlayerPayloads.Cancel());
                } else {
                    cancelArmedAt = Util.getMillis();
                }
                click(0.8f);
                return true;
            }
        }
        SlayerPayloads.LineView line = selectedLine();
        if (line == null || line.tiers().isEmpty()) {
            return false;
        }
        int x = left + PAD + LIST_WIDTH + GAP + 4;
        int w = left + windowWidth - PAD - x;
        int y = y0 + (onQuest() ? BANNER + 6 : 0) + 41;
        int columns = Math.min(line.tiers().size(), 4);
        int cardWidth = (w - GAP * (columns - 1)) / columns;
        for (int i = 0; i < line.tiers().size(); i++) {
            int cx = x + (i % columns) * (cardWidth + GAP);
            int cy = y + (i / columns) * (TIER_HEIGHT + GAP);
            if (inside(mx, my, cx, cy, cardWidth, TIER_HEIGHT)) {
                if (line.tiers().get(i).unlocked() && !onQuest()) {
                    ClientPlayNetworking.send(new SlayerPayloads.Start(line.id(), i + 1));
                    click(1.0f);
                } else {
                    click(0.6f);
                }
                return true;
            }
        }
        return false;
    }

    private boolean clickSpawners(double mx, double my, boolean shift) {
        int y0 = contentTop();
        if (my < y0 || my >= y0 + contentHeight()) {
            return false;
        }
        int columns = spawnerColumns();
        int cardWidth = spawnerWidth();
        for (int i = 0; i < view.spawners().size(); i++) {
            int cx = left + PAD + (i % columns) * (cardWidth + GAP);
            int cy = y0 + (i / columns) * (SPAWNER_HEIGHT + GAP) - (int) Math.round(scroll);
            if (inside(mx, my, cx, cy, cardWidth, SPAWNER_HEIGHT)) {
                SlayerPayloads.SpawnerView spawner = view.spawners().get(i);
                if (spawner.unlocked()) {
                    ClientPlayNetworking.send(new SlayerPayloads.Buy(spawner.index(), shift ? BULK : 1));
                    click(1.0f);
                } else {
                    click(0.6f);
                }
                return true;
            }
        }
        return false;
    }

    private void click(float pitch) {
        minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK.value(), pitch, 0.4f));
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        scroll -= scrollY * 24;
        return true;
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (minecraft.options.keyInventory.matches(event)) {
            onClose();
            return true;
        }
        return super.keyPressed(event);
    }
}
