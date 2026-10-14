package com.skyrpg.economy.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.skyrpg.core.client.ui.Draw;
import com.skyrpg.core.client.ui.Theme;
import com.skyrpg.core.stats.StatTypes;
import com.skyrpg.economy.bank.Bank;
import com.skyrpg.economy.net.EconomyPayloads;
import java.util.List;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.util.Util;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * The bank: a big balance card, Deposit and Withdraw cards (all / half), a typed amount ("2.5k" works)
 * with its own Deposit and Withdraw buttons, and the latest transactions down the right. The server
 * moves the coins; the balance and history arrive as synced attachments, so this only ever shows them.
 */
public final class BankScreen extends CounterScreen {
    private static final int HISTORY_WIDTH = 140;
    private static final int ENTRY_HEIGHT = 21;
    /** The deposit and withdraw cards need this much; below it the history column is left out. */
    private static final int MIN_MAIN_WIDTH = 220;
    /** Body height the roomy layout needs; shorter windows get the compact one. */
    private static final int FULL_HEIGHT = 172;

    private final String keeper;
    private final AmountField field = new AmountField(true);
    private long lastBalance = -1;
    private double shownBalance;
    private long balanceChangedAt;

    BankScreen(String keeper) {
        super(Component.literal("Bank"), Theme.ACCENT, new ItemStack(Items.GOLD_BLOCK));
        this.keeper = keeper;
    }

    @Override
    protected int maxWidth() {
        return 420;
    }

    @Override
    protected int maxHeight() {
        return 262;
    }

    @Override
    protected Component subtitle() {
        return Component.literal(keeper.isEmpty() ? "" : keeper + "  ·  ").append(Component.literal("Kept safe, even if you fall"));
    }

    @Override
    protected Component hints() {
        return Component.literal("Type an amount  ·  Enter deposit  ·  Shift+Enter withdraw");
    }

    private long balance() {
        return minecraft.player == null ? 0 : Bank.balance(minecraft.player);
    }

    @Override
    protected void drawBody(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        long now = Util.getMillis();
        long balance = balance();
        if (lastBalance >= 0 && balance != lastBalance) {
            balanceChangedAt = now;
        }
        if (lastBalance < 0) {
            shownBalance = balance;
        }
        lastBalance = balance;
        shownBalance = Mth.lerp(Math.min(1, frameSeconds * 9), shownBalance, balance);
        if (Math.abs(shownBalance - balance) < 0.5) {
            shownBalance = balance;
        }
        long purse = purse();

        int x = bodyX();
        int y = bodyY();
        int h = bodyHeight();
        // The history column needs the main column to keep enough room; on narrow windows it's left out.
        boolean history = bodyWidth() - HISTORY_WIDTH - 10 >= MIN_MAIN_WIDTH;
        int mainWidth = history ? bodyWidth() - HISTORY_WIDTH - 10 : bodyWidth();
        // Short windows get a tighter version of the same layout so it all fits above the footer.
        boolean compact = h < FULL_HEIGHT;
        int gap = compact ? 6 : 8;
        int balanceHeight = compact ? 40 : 54;
        int cardHeight = compact ? 60 : 74;

        drawBalance(g, x, y, mainWidth, balanceHeight, balance, purse, now, compact, mouseX, mouseY);
        int cardY = y + balanceHeight + gap;
        int cardWidth = (mainWidth - 8) / 2;
        drawActionCard(g, x, cardY, cardWidth, cardHeight, true, purse, mouseX, mouseY);
        drawActionCard(g, x + cardWidth + 8, cardY, mainWidth - cardWidth - 8, cardHeight, false, balance, mouseX, mouseY);
        drawCustomRow(g, x, cardY + cardHeight + gap, mainWidth, purse, balance, mouseX, mouseY);
        if (history) {
            drawHistory(g, x + mainWidth + 10, y, HISTORY_WIDTH, h, now, mouseX, mouseY);
        }
    }

    private void drawBalance(GuiGraphicsExtractor g, int x, int y, int w, int h, long balance, long purse, long now, boolean compact,
                             int mouseX, int mouseY) {
        float glow = 1 - Mth.clamp((now - balanceChangedAt) / 700f, 0, 1);
        Draw.panel(g, x, y, w, h, Theme.CARD, Draw.mix(Draw.mix(Theme.CARD_BORDER, Theme.ACCENT, 0.35f), Theme.ACCENT, glow));
        g.fillGradient(x + 1, y + 1, x + w - 1, y + h - 1, Draw.withAlpha(Theme.ACCENT, 0.10f + 0.12f * glow), 0x00000000);
        g.fill(x + 1, y + 8, x + 3, y + h - 8, Theme.ACCENT);
        int labelY = y + (compact ? 7 : 9);
        Draw.text(g, font, "Bank balance", x + 10, labelY, Theme.MUTED);
        // A little pile of gold on the right; the balance (2x, shrinking for big numbers) stays clear of it.
        int pileX = x + w - 44;
        int pileY = y + (h - 54) / 2;
        Draw.item(g, new ItemStack(Items.GOLD_INGOT), pileX + 8, pileY + 22, 1.25f);
        Draw.item(g, new ItemStack(Items.GOLD_NUGGET), pileX, pileY + 30, 1f);
        Draw.item(g, new ItemStack(Items.GOLD_INGOT), pileX + 18, pileY + 12, 1f);
        int room = pileX - 4 - (x + 10);
        String shown = coins(Math.round(shownBalance));
        Component amount = Component.literal(font.width(shown) <= room ? shown : Draw.compactNumbers(shown));
        Draw.fit(g, font, amount, x + 10, y + (compact ? 20 : 24), room, compact ? 1.5f : 2f, 1f, Draw.Align.LEFT, Theme.ACCENT, false);
        if (!amount.getString().equals(shown)) {
            Draw.hoverTooltip(mouseX, mouseY, x, y, w, h, List.of(Component.literal("Bank balance  ").withColor(Theme.MUTED)
                    .append(Component.literal(coins(balance)).withColor(Theme.ACCENT))), Theme.ACCENT);
        }
        // "Total ⛁ ..." between the label and the gold, shortened, or left out if there's no room.
        int totalRight = pileX - 4;
        int totalRoom = totalRight - (x + 10 + font.width("Bank balance") + 12);
        String total = "Total " + coinsFor(saturatingAdd(balance, purse), totalRoom - font.width("Total "));
        if (font.width(total) <= totalRoom) {
            Draw.rightText(g, font, total, totalRight, labelY, Theme.DIM);
        }
    }

    private void drawActionCard(GuiGraphicsExtractor g, int x, int y, int w, int h, boolean deposit, long from, int mouseX, int mouseY) {
        int color = deposit ? Theme.GREEN : Theme.BLUE;
        boolean compact = h < 74;
        Draw.panel(g, x, y, w, h, Theme.CARD, Theme.CARD_BORDER);
        g.fill(x + 1, y + 7, x + 3, y + 19, Draw.withAlpha(color, 0.9f));
        Component title = Component.literal(deposit ? "↑ " : "↓ ").withColor(color).append(Component.literal(deposit ? "Deposit" : "Withdraw")
                .withColor(Theme.TEXT));
        Draw.fitText(g, font, title, x + 9, y + 8, w - 17, Theme.TEXT);
        // "from purse" / "to purse" only when it fits beside the title.
        String hint = deposit ? "from purse" : "to purse";
        if (font.width(title) + 8 + font.width(hint) <= w - 17) {
            Draw.rightText(g, font, hint, x + w - 8, y + 8, Theme.DIM);
        }
        String key = deposit ? "deposit" : "withdraw";
        int buttonHeight = compact ? 17 : 19;
        int firstY = y + (compact ? 22 : 26);
        actionButton(g, key + "_all", x + 6, firstY, w - 12, buttonHeight, "All", from, color, mouseX, mouseY, deposit, EconomyPayloads.BankAction.ALL);
        actionButton(g, key + "_half", x + 6, firstY + buttonHeight + (compact ? 3 : 4), w - 12, buttonHeight, "Half", from / 2, color, mouseX, mouseY,
                deposit, EconomyPayloads.BankAction.HALF);
    }

    private void actionButton(GuiGraphicsExtractor g, String key, int x, int y, int w, int h, String label, long amount, int color, int mouseX,
                              int mouseY, boolean deposit, int mode) {
        // "All  ⛁ 1,234,567,890", or "All  ⛁ 1.23B" on a narrow button.
        String coins = coinsFor(amount, w - 12 - font.width(label + "  "));
        Component text = Component.literal(label).append(Component.literal("  " + coins).withColor(amount > 0 ? Theme.ACCENT : Theme.DIM));
        button(g, key, x, y, w, h, text, color, amount > 0, mouseX, mouseY,
                () -> ClientPlayNetworking.send(new EconomyPayloads.BankAction(deposit, mode, 0)));
        if (amount > 0 && !coins.equals(coins(amount))) {
            Draw.hoverTooltip(mouseX, mouseY, x, y, w, h, List.of(Component.literal(label + "  ").withColor(Theme.TEXT)
                    .append(Component.literal(coins(amount)).withColor(Theme.ACCENT))), color);
        }
    }

    private void drawCustomRow(GuiGraphicsExtractor g, int x, int y, int w, long purse, long balance, int mouseX, int mouseY) {
        int buttonWidth = Math.min(62, (w - 8) / 4);
        int fieldWidth = w - buttonWidth * 2 - 8;
        long amount = field.value();
        boolean invalid = amount < 0;
        int border = invalid ? Theme.RED : Draw.mix(Theme.CARD_BORDER, Theme.ACCENT, 0.55f);
        Draw.panel(g, x, y, fieldWidth, 20, Theme.TRACK, border);
        Draw.text(g, font, StatTypes.COIN_ICON, x + 6, y + 6, Theme.ACCENT);
        int textX = x + 6 + font.width(StatTypes.COIN_ICON) + 4;
        int room = fieldWidth - (textX - x) - 8;
        if (field.isEmpty()) {
            Draw.fitText(g, font, Draw.firstFitting(font, room, "Amount, e.g. 2.5k", "Amount", ""), textX, y + 6, room, Theme.DIM);
        } else {
            String typed = fit(field.text(), room);
            Draw.text(g, font, typed, textX, y + 6, invalid ? Theme.RED : Theme.TEXT);
            textX += font.width(typed);
            String equals = "= " + coins(amount);
            if (!invalid && field.usesShorthand() && font.width(typed) + 8 + font.width(equals) <= room) {
                Draw.rightText(g, font, equals, x + fieldWidth - 6, y + 6, Theme.DIM);
            }
        }
        if ((Util.getMillis() / 500) % 2 == 0) {
            g.fill(textX + 1, y + 5, textX + 2, y + 15, Theme.ACCENT);
        }
        boolean valid = amount > 0;
        button(g, "custom_deposit", x + fieldWidth + 4, y, buttonWidth, 20, Component.literal("Deposit"), Theme.GREEN,
                valid && amount <= purse, mouseX, mouseY, () -> sendCustom(true));
        button(g, "custom_withdraw", x + fieldWidth + 8 + buttonWidth, y, buttonWidth, 20, Component.literal("Withdraw"), Theme.BLUE,
                valid && amount <= balance, mouseX, mouseY, () -> sendCustom(false));
    }

    private void drawHistory(GuiGraphicsExtractor g, int x, int y, int w, int h, long now, int mouseX, int mouseY) {
        Draw.panel(g, x, y, w, h, Theme.CARD, Theme.CARD_BORDER);
        g.fill(x + 1, y + 6, x + 3, y + 16, Draw.withAlpha(Theme.ACCENT, 0.9f));
        Draw.text(g, font, "Recent", x + 9, y + 7, Theme.TEXT);
        g.fill(x + 6, y + 20, x + w - 6, y + 21, Theme.BORDER);
        List<Bank.Entry> entries = minecraft.player == null ? List.of() : Bank.history(minecraft.player);
        if (entries.isEmpty()) {
            Draw.fit(g, font, Component.literal("No transactions yet"), x + w / 2, y + h / 2 - 4, w - 12, Draw.Align.CENTER, Theme.DIM);
            return;
        }
        int rows = (h - 26) / ENTRY_HEIGHT;
        int shown = Math.min(rows, entries.size());
        if (shown < entries.size()) {
            // Leave a line for "+N older".
            shown = Math.max(1, Math.min(shown, (h - 26 - 10) / ENTRY_HEIGHT));
        }
        for (int i = 0; i < shown; i++) {
            Bank.Entry entry = entries.get(i);
            int ry = y + 25 + i * ENTRY_HEIGHT;
            float appear = Draw.easeOut((now - entry.time()) / 400f);
            int color = entry.deposit() ? Theme.GREEN : Theme.BLUE;
            int shift = i == 0 ? Math.round((1 - appear) * 6) : 0;
            Draw.rounded(g, x + 6, ry + 1, 14, 14, Draw.withAlpha(color, 0.16f));
            Draw.text(g, font, entry.deposit() ? "↑" : "↓", x + 10, ry + 4, color);
            // The amount keeps its room (shortened when it's big); the label and time take the rest.
            String amount = coinsFor(entry.amount(), w - 25 - 7 - 46);
            int amountWidth = font.width(amount);
            Draw.rightText(g, font, amount, x + w - 7, ry + 4, Theme.ACCENT);
            int room = w - 25 - shift - amountWidth - 7 - 6;
            Draw.fitText(g, font, entry.deposit() ? "Deposit" : "Withdrawal", x + 25 + shift, ry, room, Theme.TEXT);
            Draw.fitText(g, font, ago(System.currentTimeMillis() - entry.time()), x + 25 + shift, ry + 9, room, Theme.DIM);
            if (!amount.equals(coins(entry.amount()))) {
                Draw.hoverTooltip(mouseX, mouseY, x + 3, ry - 1, w - 6, ENTRY_HEIGHT, List.of(
                        Component.literal(entry.deposit() ? "Deposit  " : "Withdrawal  ").withColor(color)
                                .append(Component.literal(coins(entry.amount())).withColor(Theme.ACCENT))), color);
            }
        }
        if (entries.size() > shown) {
            Draw.fit(g, font, Component.literal("+" + (entries.size() - shown) + " older"), x + w / 2, y + 25 + shown * ENTRY_HEIGHT + 1,
                    w - 12, Draw.Align.CENTER, Theme.DIM);
        }
    }

    private static String ago(long millis) {
        long seconds = Math.max(0, millis / 1000);
        if (seconds < 45) {
            return "just now";
        }
        if (seconds < 3600) {
            return Math.max(1, seconds / 60) + "m ago";
        }
        if (seconds < 86400) {
            return seconds / 3600 + "h ago";
        }
        return seconds / 86400 + "d ago";
    }

    private static long saturatingAdd(long a, long b) {
        long sum = a + b;
        return sum < 0 ? Long.MAX_VALUE : sum;
    }

    private void sendCustom(boolean deposit) {
        long amount = field.value();
        if (amount > 0) {
            ClientPlayNetworking.send(new EconomyPayloads.BankAction(deposit, EconomyPayloads.BankAction.EXACT, amount));
            field.clear();
        }
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        int key = event.key();
        if (key == InputConstants.KEY_RETURN || key == InputConstants.KEY_NUMPADENTER) {
            boolean deposit = !event.hasShiftDown();
            long amount = field.value();
            if (amount > 0 && amount <= (deposit ? purse() : balance())) {
                flash(deposit ? "custom_deposit" : "custom_withdraw", true);
                sendCustom(deposit);
            } else if (amount > 0) {
                // Let the server say why (not enough coins), with its sound.
                sendCustom(deposit);
            }
            return true;
        }
        if (key == InputConstants.KEY_BACKSPACE) {
            field.backspace(event.hasControlDown());
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public boolean charTyped(CharacterEvent event) {
        if (Character.isBmpCodePoint(event.codepoint()) && field.type((char) event.codepoint())) {
            return true;
        }
        return super.charTyped(event);
    }
}
