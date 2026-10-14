package com.skyrpg.events.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.platform.cursor.CursorTypes;
import com.skyrpg.core.client.hud.HudAnchor;
import com.skyrpg.core.client.hud.HudWidget;
import com.skyrpg.core.client.menu.MenuPage;
import com.skyrpg.core.client.menu.PageContext;
import com.skyrpg.core.client.ui.Draw;
import com.skyrpg.core.client.ui.Theme;
import com.skyrpg.core.text.Msg;
import com.skyrpg.events.SkyRpgEvents;
import com.skyrpg.events.contract.ContractPayloads;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Util;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * The contracts board in the SkyRPG menu: today's three contracts, shared by everyone, with progress, rewards and the hand-in
 * and reroll buttons; plus a small HUD box with the progress of the unfinished ones.
 */
public final class ContractsPage implements MenuPage {
    private static final int CARD_HEIGHT = 62;
    private static final int GAP = 6;
    private static final int BUTTON_HEIGHT = 14;
    private static final long NOTICE_MILLIS = 4000;
    /** Colour per category. */
    private static final Map<String, Integer> COLORS = Map.of("farming", 0xFF9BE05A, "mining", 0xFF5CE1E6, "foraging", 0xFFC8913C,
            "combat", 0xFFFF6464);

    private static ContractPayloads.Board board;
    private static Component notice = Component.empty();
    private static boolean noticeGood;
    private static long noticeAt;

    /** A new board from the server. */
    public static void receive(ContractPayloads.Board payload) {
        board = payload;
        if (!payload.notice().getString().isEmpty()) {
            notice = payload.notice();
            noticeGood = payload.good();
            noticeAt = Util.getMillis();
        }
    }

    static void clear() {
        board = null;
    }

    private static int color(String category) {
        return COLORS.getOrDefault(category, Theme.ACCENT);
    }

    private static ItemStack icon(String category) {
        return switch (category) {
            case "farming" -> new ItemStack(Items.WHEAT);
            case "mining" -> new ItemStack(Items.IRON_PICKAXE);
            case "foraging" -> new ItemStack(Items.OAK_LOG);
            case "combat" -> new ItemStack(Items.IRON_SWORD);
            default -> new ItemStack(Items.PAPER);
        };
    }

    @Override
    public Identifier id() {
        return SkyRpgEvents.id("contracts");
    }

    @Override
    public Component title() {
        return Component.literal("Contracts");
    }

    @Override
    public Component subtitle() {
        return Component.literal("Three a day, for everyone: whoever helps gets the reward");
    }

    @Override
    public ItemStack icon() {
        return new ItemStack(Items.WRITABLE_BOOK);
    }

    @Override
    public int order() {
        return 12;
    }

    @Override
    public void render(PageContext ctx) {
        GuiGraphicsExtractor g = ctx.graphics();
        Font font = ctx.font();
        if (board == null || board.contracts().isEmpty()) {
            Draw.text(g, font, "No contracts yet. They're picked when someone's online.", ctx.x(), ctx.y() + 4, Theme.MUTED);
            return;
        }
        int y = ctx.y();
        List<ContractPayloads.Entry> contracts = board.contracts();
        for (int i = 0; i < contracts.size(); i++) {
            drawCard(ctx, contracts.get(i), ctx.x(), y, ctx.width());
            y += CARD_HEIGHT + GAP;
        }
        if (Util.getMillis() - noticeAt < NOTICE_MILLIS) {
            Draw.fitText(g, font, notice, ctx.x(), y + 2, ctx.width() - 90, noticeGood ? Theme.GREEN : Theme.RED);
        }
    }

    private void drawCard(PageContext ctx, ContractPayloads.Entry entry, int x, int y, int w) {
        GuiGraphicsExtractor g = ctx.graphics();
        Font font = ctx.font();
        int color = color(entry.category());
        Draw.panel(g, x, y, w, CARD_HEIGHT, Theme.CARD, entry.done() ? Draw.mix(Theme.CARD_BORDER, Theme.GREEN, 0.6f) : Theme.CARD_BORDER);
        g.fill(x + 1, y + 2, x + 3, y + CARD_HEIGHT - 2, color);
        Draw.rounded(g, x + 8, y + 7, 22, 22, Draw.withAlpha(color, 0.18f));
        Draw.item(g, entry.deliver().isEmpty() ? icon(entry.category()) : entry.deliver(), x + 11, y + 10, 1f);
        int textX = x + 38;
        Draw.text(g, font, Component.literal(entry.name()).withColor(entry.done() ? Theme.GREEN : Theme.TEXT), textX, y + 7, Theme.TEXT);
        String tag = entry.done() ? "✔ Complete" : entry.helped() ? "You're helping" : "";
        if (!tag.isEmpty()) {
            Draw.rightText(g, font, tag, x + w - 8, y + 7, entry.done() ? Theme.GREEN : Theme.ACCENT);
        }
        Draw.fitText(g, font, entry.description(), textX, y + 18, w - 38 - 8, Theme.MUTED);
        // Progress bar.
        int barWidth = Math.max(60, w / 3);
        float progress = entry.amount() == 0 ? 0 : entry.progress() / (float) entry.amount();
        Draw.bar(g, textX, y + 32, barWidth, 5, progress, entry.done() ? Theme.GREEN : color);
        Draw.text(g, font, Msg.number(entry.progress()) + " / " + Msg.number(entry.amount()), textX + barWidth + 6, y + 30, Theme.TEXT);
        // Rewards on one line.
        Component rewards = Component.empty();
        for (int i = 0; i < entry.rewards().size(); i++) {
            if (i > 0) {
                rewards = rewards.copy().append(Component.literal("  ·  ").withColor(Theme.DIM));
            }
            rewards = rewards.copy().append(entry.rewards().get(i));
        }
        Draw.fitText(g, font, rewards, textX, y + 45, w - 38 - 8 - buttonsWidth(font, entry), Theme.TEXT);
        // Buttons: hand in (deliveries) and reroll.
        int bx = x + w - 8;
        if (!entry.done() && entry.progress() == 0 && !entry.rerolled()) {
            bx = button(ctx, "Reroll", bx, y + CARD_HEIGHT - BUTTON_HEIGHT - 5, Theme.MUTED) - 4;
        }
        if (!entry.done() && !entry.deliver().isEmpty()) {
            button(ctx, "Hand in", bx, y + CARD_HEIGHT - BUTTON_HEIGHT - 5, Theme.GREEN);
        }
    }

    private static int buttonsWidth(Font font, ContractPayloads.Entry entry) {
        int width = 0;
        if (!entry.done() && entry.progress() == 0 && !entry.rerolled()) {
            width += font.width("Reroll") + 14 + 4;
        }
        if (!entry.done() && !entry.deliver().isEmpty()) {
            width += font.width("Hand in") + 14 + 4;
        }
        return width;
    }

    /** Draws a button ending at {@code right}; returns its left edge. */
    private static int button(PageContext ctx, String label, int right, int y, int color) {
        Font font = ctx.font();
        int w = font.width(label) + 14;
        int x = right - w;
        boolean hover = ctx.hovered(x, y, w, BUTTON_HEIGHT);
        Draw.panel(ctx.graphics(), x, y, w, BUTTON_HEIGHT, hover ? Draw.withAlpha(color, 0.18f) : Theme.TRACK, hover ? color : Theme.CARD_BORDER);
        Draw.text(ctx.graphics(), font, label, x + 7, y + 3, hover ? color : Theme.TEXT);
        if (hover) {
            ctx.graphics().requestCursor(CursorTypes.POINTING_HAND);
            ctx.tooltip(label.equals("Reroll")
                    ? List.of(Component.literal("Reroll").withColor(Theme.TEXT),
                            Component.literal("Swap for another contract: " + Msg.number(board.rerollCost()) + " coins,").withColor(Theme.MUTED),
                            Component.literal("once a day, before anyone has started it.").withColor(Theme.MUTED))
                    : List.of(Component.literal("Hand in").withColor(Theme.TEXT),
                            Component.literal("Gives what you carry towards this contract.").withColor(Theme.MUTED)), color);
        }
        return x;
    }

    @Override
    public boolean mouseClicked(PageContext ctx, double mouseX, double mouseY, int button) {
        if (button != InputConstants.MOUSE_BUTTON_LEFT || board == null) {
            return false;
        }
        Font font = ctx.font();
        int y = ctx.y();
        for (int i = 0; i < board.contracts().size(); i++) {
            ContractPayloads.Entry entry = board.contracts().get(i);
            int by = y + CARD_HEIGHT - BUTTON_HEIGHT - 5;
            int right = ctx.x() + ctx.width() - 8;
            if (!entry.done() && entry.progress() == 0 && !entry.rerolled()) {
                int w = font.width("Reroll") + 14;
                if (inside(mouseX, mouseY, right - w, by, w, BUTTON_HEIGHT)) {
                    ClientPlayNetworking.send(new ContractPayloads.Action(i, true));
                    click();
                    return true;
                }
                right -= w + 4;
            }
            if (!entry.done() && !entry.deliver().isEmpty()) {
                int w = font.width("Hand in") + 14;
                if (inside(mouseX, mouseY, right - w, by, w, BUTTON_HEIGHT)) {
                    ClientPlayNetworking.send(new ContractPayloads.Action(i, false));
                    click();
                    return true;
                }
            }
            y += CARD_HEIGHT + GAP;
        }
        return false;
    }

    private static boolean inside(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    private static void click() {
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK.value(), 1.2f, 0.4f));
    }

    /** The HUD box: unfinished contracts with a small progress bar each. */
    public static final class Widget implements HudWidget {
        private static final int WIDTH = 150;
        private static final int LINE = 18;

        @Override
        public Identifier id() {
            return SkyRpgEvents.id("contracts_hud");
        }

        @Override
        public Component name() {
            return Component.literal("Contracts");
        }

        @Override
        public HudAnchor defaultAnchor() {
            return HudAnchor.MIDDLE_LEFT;
        }

        @Override
        public int defaultX() {
            return 4;
        }

        @Override
        public int defaultY() {
            return 0;
        }

        private static List<ContractPayloads.Entry> open(boolean editing) {
            if (board == null) {
                return List.of();
            }
            List<ContractPayloads.Entry> open = board.contracts().stream().filter(e -> !e.done()).toList();
            return open.isEmpty() && editing ? board.contracts() : open;
        }

        @Override
        public boolean visible() {
            return !open(false).isEmpty();
        }

        @Override
        public int width(Font font) {
            return WIDTH;
        }

        @Override
        public int height(Font font) {
            return 16 + Math.max(1, open(true).size()) * LINE;
        }

        @Override
        public void render(GuiGraphicsExtractor g, Font font, boolean editing) {
            List<ContractPayloads.Entry> open = open(editing);
            Draw.panel(g, 0, 0, WIDTH, height(font), 0xE0121620, Theme.BORDER);
            Draw.text(g, font, Component.literal("📋 Contracts").withColor(Theme.ACCENT), 6, 5, Theme.TEXT);
            if (open.isEmpty()) {
                Draw.text(g, font, "None today", 6, 18, Theme.MUTED);
                return;
            }
            int y = 17;
            for (ContractPayloads.Entry entry : open) {
                Draw.fitText(g, font, entry.name(), 6, y, WIDTH - 12, Theme.TEXT);
                float progress = entry.amount() == 0 ? 0 : entry.progress() / (float) entry.amount();
                Draw.bar(g, 6, y + 10, WIDTH - 50, 3, progress, color(entry.category()));
                Draw.rightText(g, font, Draw.compactNumbers(Msg.number(entry.progress()) + "/" + Msg.number(entry.amount())), WIDTH - 6, y + 7,
                        Theme.MUTED);
                y += LINE;
            }
        }
    }
}
