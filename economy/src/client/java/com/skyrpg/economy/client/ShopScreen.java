package com.skyrpg.economy.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.skyrpg.core.client.ui.Draw;
import com.skyrpg.core.client.ui.Theme;
import com.skyrpg.core.skills.SkillLevels;
import com.skyrpg.core.text.Msg;
import com.skyrpg.economy.net.EconomyPayloads;
import com.skyrpg.economy.shop.Shops;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.TextColor;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.util.Util;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * A shop: a grid of offer cards on the left (click buys one, shift-click a stack, right-click picks an
 * amount) and, if the keeper buys things, a sell panel on the right listing what in your inventory they
 * want, with per-item Sell, Sell all and a buyback of your last sale. Arrow keys move a focus over the
 * cards and Enter buys. Nothing here trusts itself: every click is a request the server checks.
 */
public final class ShopScreen extends CounterScreen {
    private static final int CARD_MIN_WIDTH = 64;
    private static final int CARD_HEIGHT = 62;
    private static final int GAP = 6;
    private static final int SELL_WIDTH = 148;
    private static final int MIN_SELL_WIDTH = 112;
    private static final int ROW_HEIGHT = 22;
    private static final int[] PRESETS = {1, 8, 16, 32, 64};

    private record Sellable(Item item, ItemStack icon, int count, long unit) {
        long total() {
            return unit * count;
        }
    }

    private EconomyPayloads.Shop shop;
    private final Map<String, Long> pulses = new HashMap<>();
    private double scroll;
    private double scrollTarget;
    private double sellScroll;
    private double sellScrollTarget;
    private int columns = 1;
    private int focused = -1;
    private int pickerOffer = -1;
    private boolean pickerFresh;
    private long pickerOpenedAt;
    private final AmountField pickerField = new AmountField(false);
    // Where the two scrollable areas were last drawn, for the scroll wheel.
    private int gridLeft;
    private int gridRight;
    private int sellLeft;
    private int sellRight;

    ShopScreen(EconomyPayloads.Shop shop) {
        super(Component.literal(shop.title()), shop.accent(), shop.icon());
        this.shop = shop;
    }

    Identifier shopId() {
        return shop.shop();
    }

    /** New state from the server (after a sale or buyback); keeps scroll and animations. */
    void update(EconomyPayloads.Shop updated) {
        shop = updated;
        if (pickerOffer >= shop.offers().size()) {
            pickerOffer = -1;
        }
        if (focused >= shop.offers().size()) {
            focused = -1;
        }
    }

    @Override
    protected int maxWidth() {
        return shop.selling().buys() ? 476 : 436;
    }

    @Override
    protected int maxHeight() {
        return 282;
    }

    @Override
    protected Component subtitle() {
        MutableComponent line = Component.literal(shop.keeper().isEmpty() ? "Shop" : shop.keeper());
        return line.append(Component.literal("  ·  ").withColor(Theme.DIM))
                .append(Component.literal(shop.selling().buys() ? "Buys and sells" : "Sells"));
    }

    @Override
    protected Component hints() {
        return Component.literal(pickerOffer >= 0 ? "Type a number  ·  ↑↓ adjust  ·  Enter buy  ·  Esc back"
                : "Click buy  ·  Shift-click a stack  ·  Right-click choose amount");
    }

    private String keeperFirstName() {
        return shop.keeper().isEmpty() ? "" : shop.keeper().split(" ")[0];
    }

    // ------------------------------------------------------------------ drawing

    @Override
    protected void drawBody(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        int x = bodyX();
        int y = bodyY();
        int w = bodyWidth();
        int h = bodyHeight();
        boolean buys = shop.selling().buys();
        boolean modal = pickerOffer >= 0;
        int mx = modal ? Integer.MIN_VALUE / 2 : mouseX;
        int my = modal ? Integer.MIN_VALUE / 2 : mouseY;
        int sellWidth = Mth.clamp(w * 2 / 5, MIN_SELL_WIDTH, SELL_WIDTH);
        int gridWidth = buys ? w - sellWidth - 10 : w;
        drawOffers(g, x, y, gridWidth, h, mx, my);
        if (buys) {
            drawSellPanel(g, x + gridWidth + 10, y, sellWidth, h, mx, my);
        }
        if (modal) {
            // Clicking outside the picker closes it.
            hit(left, top, windowWidth, windowHeight, "picker_outside", (button, shift) -> {
                closePicker();
                return true;
            });
            drawPicker(g, mouseX, mouseY);
        }
    }

    private void drawOffers(GuiGraphicsExtractor g, int x, int y, int w, int h, int mouseX, int mouseY) {
        gridLeft = x;
        gridRight = x + w;
        List<EconomyPayloads.Offer> offers = shop.offers();
        if (offers.isEmpty()) {
            String text = "Nothing for sale right now.";
            Draw.text(g, font, text, x + (w - font.width(text)) / 2, y + h / 2 - 4, Theme.DIM);
            return;
        }
        columns = Math.max(1, (w + GAP) / (CARD_MIN_WIDTH + GAP));
        int rows = (offers.size() + columns - 1) / columns;
        int content = rows * (CARD_HEIGHT + GAP) - GAP;
        int maxScroll = Math.max(0, content - h);
        int cardsWidth = maxScroll > 0 ? w - 6 : w;
        int cardWidth = (cardsWidth - (columns - 1) * GAP) / columns;
        scrollTarget = Mth.clamp(scrollTarget, 0, maxScroll);
        scroll = Mth.lerp(Math.min(1, frameSeconds * 14), scroll, scrollTarget);

        long purse = purse();
        // Cards lift 2px on hover, so leave that much room above the first row.
        g.enableScissor(x - 1, y - 3, x + w + 1, y + h);
        for (int i = 0; i < offers.size(); i++) {
            int cx = x + (i % columns) * (cardWidth + GAP);
            int cy = y + (i / columns) * (CARD_HEIGHT + GAP) - (int) Math.round(scroll);
            if (cy + CARD_HEIGHT < y || cy > y + h) {
                continue;
            }
            boolean hovered = inside(mouseX, mouseY, cx, cy, cardWidth, CARD_HEIGHT) && mouseY >= y && mouseY < y + h;
            drawCard(g, i, offers.get(i), cx, cy, cardWidth, hovered, purse);
            int top = Math.max(cy, y);
            int bottom = Math.min(cy + CARD_HEIGHT, y + h);
            int index = i;
            hit(cx, top, cardWidth, bottom - top, "offer" + i, (button, shift) -> clickOffer(index, button, shift));
        }
        g.disableScissor();
        if (maxScroll > 0) {
            scrollbar(g, x + w - 3, y, h, content, maxScroll, scroll);
        }
    }

    private void drawCard(GuiGraphicsExtractor g, int index, EconomyPayloads.Offer offer, int x, int cardY, int w, boolean hovered, long purse) {
        ItemStack stack = offer.stack();
        boolean locked = locked(offer);
        boolean affordable = affordableUnits(offer) > 0;
        float lit = Math.max(hover(g, "offer" + index, hovered), index == focused ? 1 : 0);
        int y = cardY - Math.round(lit * 2);
        int nameColor = nameColor(stack);

        Draw.panel(g, x, y, w, CARD_HEIGHT, Draw.mix(Theme.CARD, Theme.CARD_HOVER, lit),
                Draw.mix(Theme.CARD_BORDER, Draw.mix(Theme.CARD_BORDER, accent, 0.85f), lit));
        if (index == focused) {
            g.fill(x + 3, y, x + w - 3, y + 1, accent);
        }
        // A soft glow in the item's rarity colour behind the icon.
        Draw.rounded(g, x + w / 2 - 15, y + 5, 30, 28, Draw.withAlpha(nameColor, 0.09f + 0.09f * lit));
        float scale = 1.5f + 0.12f * lit;
        int size = Math.round(16 * scale);
        Draw.item(g, stack, x + (w - size) / 2, y + 19 - size / 2, scale);
        if (stack.getCount() > 1) {
            Draw.rightText(g, font, "x" + stack.getCount(), x + w - 4, y + 4, Theme.MUTED);
        }
        if (locked) {
            Draw.rounded(g, x + 1, y + 1, w - 2, CARD_HEIGHT - 2, Draw.withAlpha(0xFF05070B, 0.5f));
        }
        Draw.fit(g, font, Component.literal(stack.getHoverName().getString()), x + w / 2, y + 37, w - 8, Draw.Align.CENTER,
                locked ? Theme.DIM : nameColor);
        if (locked) {
            Draw.fit(g, font, Component.literal("Needs " + requirement(offer)), x + w / 2, y + 49, w - 8, Draw.Align.CENTER, Theme.RED);
        } else if (!offer.costs().isEmpty()) {
            drawCosts(g, offer, x + w / 2, y + 47, w - 8);
        } else {
            Draw.fitNumber(g, font, coins(offer.price()), x + w / 2, y + 49, w - 8, Draw.Align.CENTER, affordable ? Theme.ACCENT : Theme.RED);
        }
        pulse(g, "offer" + index, x, y, w, CARD_HEIGHT, Theme.GREEN);
        if (hovered) {
            tooltip(offerTooltip(offer, affordable, purse), nameColor);
        }
    }

    private List<Component> offerTooltip(EconomyPayloads.Offer offer, boolean affordable, long purse) {
        int count = offer.stack().getCount();
        List<Component> lines = new ArrayList<>(Screen.getTooltipFromItem(minecraft, offer.stack()));
        lines.add(Component.empty());
        if (offer.price() > 0) {
            MutableComponent price = Component.literal("Price  ").withColor(Theme.MUTED).append(Component.literal(coins(offer.price())).withColor(Theme.ACCENT));
            if (count > 1) {
                price.append(Component.literal("  for " + count).withColor(Theme.DIM));
            }
            lines.add(price);
            if (purse < offer.price()) {
                lines.add(Component.literal("You need " + coins(offer.price() - purse) + " more").withColor(Theme.RED));
            }
        }
        for (ItemStack cost : offer.costs()) {
            int have = have(cost.getItem());
            lines.add(Component.literal("Cost  ").withColor(Theme.MUTED)
                    .append(Component.literal(Msg.number(cost.getCount()) + "\u00d7 ").withColor(Theme.TEXT))
                    .append(cost.getHoverName().copy().withColor(nameColor(cost)))
                    .append(Component.literal("  (you have " + Msg.number(have) + ")").withColor(have >= cost.getCount() ? Theme.GREEN : Theme.RED)));
        }
        if (offer.skill().isPresent()) {
            lines.add(Component.literal("Requires " + requirement(offer)).withColor(locked(offer) ? Theme.RED : Theme.GREEN));
        }
        lines.add(Component.empty());
        lines.add(hintLine("Click", "buy " + count));
        int stackUnits = stackUnits(offer);
        if (stackUnits > 1) {
            lines.add(hintLine("Shift-click", "buy " + stackUnits * count + (offer.price() > 0 ? " for " + coins(saturatingTimes(offer.price(), stackUnits)) : "")));
        }
        lines.add(hintLine("Right-click", "choose how many"));
        return lines;
    }

    /** How many units of this offer the player can pay for right now: coins and every item cost. */
    private long affordableUnits(EconomyPayloads.Offer offer) {
        long units = offer.price() > 0 ? purse() / offer.price() : Long.MAX_VALUE;
        for (ItemStack cost : offer.costs()) {
            units = Math.min(units, have(cost.getItem()) / cost.getCount());
        }
        return units;
    }

    /** How many of this item the main inventory and hotbar hold (what the shop takes from). */
    private int have(Item item) {
        if (minecraft.player == null) {
            return 0;
        }
        Inventory inventory = minecraft.player.getInventory();
        int count = 0;
        for (int slot = 0; slot < Inventory.INVENTORY_SIZE; slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (stack.is(item)) {
                count += stack.getCount();
            }
        }
        return count;
    }

    /** Locked behind a skill level the player hasn't reached yet. */
    private boolean locked(EconomyPayloads.Offer offer) {
        return offer.skill().isPresent() && minecraft.player != null && SkillLevels.level(minecraft.player, offer.skill().get()) < offer.level();
    }

    private String requirement(EconomyPayloads.Offer offer) {
        return offer.skill().map(skill -> SkillLevels.displayName(skill) + " " + offer.level()).orElse("");
    }

    /** "3 Glimmer Crystal + 1 Prism Fragment": one unit's costs. */
    private String costSummary(EconomyPayloads.Offer offer) {
        List<String> parts = new ArrayList<>();
        if (offer.price() > 0) {
            parts.add(coins(offer.price()));
        }
        offer.costs().forEach(cost -> parts.add(Msg.number(cost.getCount()) + " " + cost.getHoverName().getString()));
        return String.join(" + ", parts);
    }

    private String costTotal(EconomyPayloads.Offer offer, long units) {
        List<String> parts = new ArrayList<>();
        if (offer.price() > 0) {
            parts.add(coins(saturatingTimes(offer.price(), units)));
        }
        offer.costs().forEach(cost -> parts.add(Msg.number(cost.getCount() * units) + " " + cost.getHoverName().getString()));
        return String.join(" + ", parts);
    }

    /** Each item cost as a small icon and its count, centred under a card; red counts are ones you're short of. */
    private void drawCosts(GuiGraphicsExtractor g, EconomyPayloads.Offer offer, int centerX, int y, int maxWidth) {
        List<String> counts = new ArrayList<>();
        int width = 0;
        for (ItemStack cost : offer.costs()) {
            String text = Msg.number(cost.getCount());
            counts.add(text);
            width += 11 + font.width(text) + 4;
        }
        width -= 4;
        float scale = width > maxWidth ? Math.max(0.6f, maxWidth / (float) width) : 1f;
        g.pose().pushMatrix();
        g.pose().translate(centerX - width * scale / 2f, y);
        g.pose().scale(scale, scale);
        int x = 0;
        for (int i = 0; i < offer.costs().size(); i++) {
            ItemStack cost = offer.costs().get(i);
            Draw.item(g, cost, x, 0, 0.625f);
            boolean enough = have(cost.getItem()) >= cost.getCount();
            g.text(font, counts.get(i), x + 11, 2, enough ? Theme.TEXT : Theme.RED, true);
            x += 11 + font.width(counts.get(i)) + 4;
        }
        g.pose().popMatrix();
    }

    private Component hintLine(String key, String what) {
        return Component.literal(key).withColor(accent).append(Component.literal("  " + what).withColor(Theme.MUTED));
    }

    private void drawSellPanel(GuiGraphicsExtractor g, int x, int y, int w, int h, int mouseX, int mouseY) {
        sellLeft = x;
        sellRight = x + w;
        Draw.panel(g, x, y, w, h, Theme.CARD, Theme.CARD_BORDER);
        g.fill(x + 1, y + 6, x + 3, y + 16, Draw.withAlpha(Theme.GREEN, 0.9f));
        String first = keeperFirstName();
        List<Sellable> items = sellables();
        long total = 0;
        for (Sellable item : items) {
            total += item.total();
        }
        int totalWidth = 0;
        if (!items.isEmpty()) {
            String totalText = coinsFor(total, (w - 17) / 2);
            totalWidth = font.width(totalText) + 6;
            Draw.rightText(g, font, totalText, x + w - 8, y + 7, Theme.ACCENT);
        }
        Draw.fitText(g, font, first.isEmpty() ? "Sell" : "Sell to " + first, x + 9, y + 7, w - 17 - totalWidth, Theme.TEXT);
        String hint = shop.selling().hint().isEmpty() ? "Anything with a price" : shop.selling().hint();
        if (Draw.fitText(g, font, hint, x + 9, y + 18, w - 17, Theme.DIM)) {
            Draw.hoverTooltip(mouseX, mouseY, x + 9, y + 17, w - 17, 10, List.of(Component.literal(hint).withColor(Theme.MUTED)), Theme.GREEN);
        }
        int listTop = y + 31;
        g.fill(x + 6, listTop - 3, x + w - 6, listTop - 2, Theme.BORDER);

        boolean hasBuyback = !shop.selling().buyback().isEmpty();
        int sellAllY = y + h - 26;
        int listBottom = sellAllY - (hasBuyback ? 28 : 4);
        int listHeight = listBottom - listTop;

        if (items.isEmpty()) {
            String empty = "Nothing to sell";
            int midY = listTop + listHeight / 2 - 9;
            Draw.text(g, font, empty, x + (w - font.width(empty)) / 2, midY, Theme.MUTED);
            String sub = "What " + (first.isEmpty() ? "they" : first) + " buys shows up here";
            Draw.fit(g, font, Component.literal(sub), x + w / 2, midY + 12, w - 12, Draw.Align.CENTER, Theme.DIM);
        } else {
            int content = items.size() * ROW_HEIGHT;
            int maxScroll = Math.max(0, content - listHeight);
            sellScrollTarget = Mth.clamp(sellScrollTarget, 0, maxScroll);
            sellScroll = Mth.lerp(Math.min(1, frameSeconds * 14), sellScroll, sellScrollTarget);
            int rowWidth = w - 6 - (maxScroll > 0 ? 4 : 0);
            g.enableScissor(x + 1, listTop, x + w - 1, listBottom);
            for (int i = 0; i < items.size(); i++) {
                int ry = listTop + i * ROW_HEIGHT - (int) Math.round(sellScroll);
                if (ry + ROW_HEIGHT < listTop || ry > listBottom) {
                    continue;
                }
                drawSellRow(g, items.get(i), x + 3, ry, rowWidth, mouseX, mouseY, listTop, listBottom);
            }
            g.disableScissor();
            if (maxScroll > 0) {
                scrollbar(g, x + w - 5, listTop, listHeight, content, maxScroll, sellScroll);
            }
        }

        if (hasBuyback) {
            drawBuyback(g, x + 6, sellAllY - 24, w - 12, mouseX, mouseY);
        }
        MutableComponent label = Component.literal("Sell all");
        if (!items.isEmpty()) {
            label.append(Component.literal("  " + coinsFor(total, w - 24 - font.width("Sell all  "))).withColor(Theme.ACCENT));
        }
        button(g, "sell_all", x + 6, sellAllY, w - 12, 20, label, Theme.GREEN, !items.isEmpty(), mouseX, mouseY, () -> {
            ClientPlayNetworking.send(new EconomyPayloads.Sell(shop.shop(), Optional.empty()));
            pulses.put("sell_all", Util.getMillis());
        });
    }

    private void drawSellRow(GuiGraphicsExtractor g, Sellable item, int x, int y, int w, int mouseX, int mouseY, int clipTop, int clipBottom) {
        String key = "sell:" + BuiltInRegistries.ITEM.getKey(item.item());
        boolean hovered = inside(mouseX, mouseY, x, y, w, ROW_HEIGHT) && mouseY >= clipTop && mouseY < clipBottom;
        float hv = hover(g, key, hovered);
        if (hv > 0.02f) {
            Draw.rounded(g, x, y, w, ROW_HEIGHT, Draw.withAlpha(Theme.GREEN, 0.10f * hv));
        }
        g.item(item.icon(), x + 3, y + 3);
        String total = coinsFor(item.total(), (w - 27) / 2);
        int right = x + w - 4;
        int nameWidth = w - 23 - 4 - Math.max(font.width(total), 30) - 6;
        Draw.fitText(g, font, item.icon().getHoverName().getString(), x + 23, y + 3, nameWidth, nameColor(item.icon()));
        Draw.fitNumber(g, font, Msg.number(item.count()) + " × " + coins(item.unit()), x + 23, y + 12, nameWidth, Draw.Align.LEFT, Theme.DIM);
        if (hv > 0.5f) {
            Draw.rounded(g, right - 30, y + 4, 30, 14, Draw.mix(Theme.CARD, Theme.GREEN, 0.4f));
            Draw.rounded(g, right - 29, y + 5, 28, 1, Draw.withAlpha(Draw.lighten(Theme.GREEN, 0.5f), 0.4f));
            g.text(font, "Sell", right - 15 - font.width("Sell") / 2, y + 7, Theme.TEXT, false);
        } else {
            Draw.rightText(g, font, total, right, y + 7, Theme.ACCENT);
        }
        pulse(g, key, x, y, w, ROW_HEIGHT, Theme.GREEN);
        if (hovered) {
            tooltip(List.of(item.icon().getHoverName().copy().withColor(nameColor(item.icon())),
                    Component.literal("Sells for " + coins(item.unit()) + " each").withColor(Theme.MUTED),
                    Component.empty(),
                    Component.literal("Click").withColor(Theme.GREEN).append(Component.literal("  sell all " + Msg.number(item.count())
                            + " for " + coins(item.total())).withColor(Theme.MUTED))), Theme.GREEN);
        }
        int top = Math.max(y, clipTop);
        int bottom = Math.min(y + ROW_HEIGHT, clipBottom);
        hit(x, top, w, bottom - top, key, (button, shift) -> {
            if (button != InputConstants.MOUSE_BUTTON_LEFT) {
                return false;
            }
            flash(key, true);
            pulses.put(key, Util.getMillis());
            ClientPlayNetworking.send(new EconomyPayloads.Sell(shop.shop(), Optional.of(BuiltInRegistries.ITEM.getKey(item.item()))));
            return true;
        });
    }

    private void drawBuyback(GuiGraphicsExtractor g, int x, int y, int w, int mouseX, int mouseY) {
        List<ItemStack> stacks = shop.selling().buyback();
        long price = shop.selling().buybackPrice();
        boolean affordable = purse() >= price;
        MutableComponent label = Component.literal("Buy back").append(Component.literal("  " + coinsFor(price, w - 40 - font.width("Buy back  ")))
                .withColor(affordable ? Theme.ACCENT : Theme.RED));
        button(g, "buyback", x, y, w, 20, label, Theme.BLUE, true, mouseX, mouseY,
                () -> ClientPlayNetworking.send(new EconomyPayloads.Buyback(shop.shop())));
        Draw.item(g, stacks.getFirst(), x + 4, y + 3, 0.875f);
        if (inside(mouseX, mouseY, x, y, w, 20)) {
            List<Component> lines = new ArrayList<>();
            lines.add(Component.literal("Your last sale here").withColor(Theme.BLUE));
            Map<Item, Integer> counts = new LinkedHashMap<>();
            Map<Item, ItemStack> samples = new LinkedHashMap<>();
            for (ItemStack stack : stacks) {
                counts.merge(stack.getItem(), stack.getCount(), Integer::sum);
                samples.putIfAbsent(stack.getItem(), stack);
            }
            int shown = 0;
            for (Map.Entry<Item, Integer> entry : counts.entrySet()) {
                if (shown++ == 6) {
                    lines.add(Component.literal("…and " + (counts.size() - 6) + " more").withColor(Theme.DIM));
                    break;
                }
                lines.add(Component.literal(Msg.number(entry.getValue()) + "x ").withColor(Theme.MUTED)
                        .append(samples.get(entry.getKey()).getHoverName().copy().withColor(nameColor(samples.get(entry.getKey())))));
            }
            lines.add(Component.empty());
            lines.add(Component.literal("Click").withColor(Theme.BLUE)
                    .append(Component.literal("  buy it all back for " + coins(price)).withColor(Theme.MUTED)));
            tooltip(lines, Theme.BLUE);
        }
    }

    private void drawPicker(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        EconomyPayloads.Offer offer = shop.offers().get(pickerOffer);
        ItemStack stack = offer.stack();
        int count = stack.getCount();
        int maxUnits = Shops.maxUnits(stack.getMaxStackSize(), count);
        long units = Math.min(Math.max(pickerField.value(), 0), maxUnits);
        long total = saturatingTimes(offer.price(), units);
        long purse = purse();
        int nameColor = nameColor(stack);

        float appear = Draw.easeOut((Util.getMillis() - pickerOpenedAt) / 160f);
        g.fill(left + 1, top + 1, left + windowWidth - 1, top + windowHeight - 1, Draw.withAlpha(0xFF05070B, 0.6f * appear));
        int pw = Math.min(220, windowWidth - 16);
        int ph = Math.min(140, windowHeight - 16);
        int px = left + (windowWidth - pw) / 2;
        int py = top + (windowHeight - ph) / 2 + Math.round((1 - appear) * 6);
        hit(px, py, pw, ph, "picker", (button, shift) -> true);
        Draw.panel(g, px, py, pw, ph, Theme.WINDOW, Draw.mix(Theme.BORDER, accent, 0.6f));
        g.fill(px + 2, py, px + pw - 2, py + 1, accent);

        Draw.panel(g, px + 10, py + 10, 24, 24, Draw.mix(Theme.CARD, nameColor, 0.15f), Draw.mix(Theme.CARD_BORDER, nameColor, 0.5f));
        g.item(stack, px + 14, py + 14);
        g.text(font, fit(stack.getHoverName().getString(), pw - 52), px + 40, py + 11, nameColor, false);
        String each = offer.costs().isEmpty() ? coins(offer.price()) : costSummary(offer);
        Draw.fitNumber(g, font, each + (count > 1 ? " per " + count : " each"), px + 40, py + 23, pw - 52, Draw.Align.LEFT, Theme.MUTED);

        int fy = py + 42;
        button(g, "picker_minus", px + 10, fy, 22, 22, Component.literal("−"), accent, units > 1, mouseX, mouseY, () -> adjust(-1));
        button(g, "picker_plus", px + pw - 32, fy, 22, 22, Component.literal("+"), accent, units < maxUnits, mouseX, mouseY, () -> adjust(1));
        int fieldX = px + 36;
        int fieldWidth = pw - 72;
        Draw.panel(g, fieldX, fy, fieldWidth, 22, Theme.TRACK, Draw.mix(Theme.CARD_BORDER, accent, 0.7f));
        String amount = pickerField.isEmpty() ? "0" : pickerField.text();
        int amountWidth = Math.round(font.width(amount) * 1.5f);
        int amountX = fieldX + (fieldWidth - amountWidth) / 2;
        Draw.scaledText(g, font, Component.literal(amount), amountX, fy + 6, 1.5f, pickerFresh ? Theme.MUTED : Theme.TEXT);
        if ((Util.getMillis() / 500) % 2 == 0) {
            g.fill(amountX + amountWidth + 1, fy + 5, amountX + amountWidth + 2, fy + 17, accent);
        }

        int chipY = fy + 28;
        List<Integer> presets = new ArrayList<>();
        for (int preset : PRESETS) {
            if (preset <= maxUnits) {
                presets.add(preset);
            }
        }
        int max = (int) Math.max(1, Math.min(maxUnits, affordableUnits(offer)));
        int chips = presets.size() + 1;
        int chipWidth = (pw - 20 - (chips - 1) * 4) / chips;
        for (int i = 0; i < chips; i++) {
            int value = i < presets.size() ? presets.get(i) : max;
            String text = i < presets.size() ? String.valueOf(value) : "Max";
            int chipColor = value == units ? accent : Theme.MUTED;
            button(g, "picker_chip" + i, px + 10 + i * (chipWidth + 4), chipY, chipWidth, 16, Component.literal(text), chipColor, true,
                    mouseX, mouseY, () -> {
                        pickerField.set(value);
                        pickerFresh = false;
                    });
        }

        int ty = chipY + 24;
        Draw.text(g, font, "Total", px + 10, ty, Theme.MUTED);
        String totalText = offer.costs().isEmpty() ? coinsFor(total, pw - 20 - font.width("Total") - 8) : costTotal(offer, units);
        Draw.rightText(g, font, totalText, px + pw - 10, ty, units > affordableUnits(offer) ? Theme.RED : Theme.ACCENT);
        if (count > 1) {
            Draw.fitText(g, font, Msg.number(units * count) + " items", px + 14 + font.width("Total"), ty,
                    pw - 24 - font.width("Total") - font.width(totalText) - 6, Theme.DIM);
        }

        int by = py + ph - 26;
        int half = (pw - 24) / 2;
        button(g, "picker_cancel", px + 10, by, half, 18, Component.literal("Cancel"), Theme.MUTED, true, mouseX, mouseY, this::closePicker);
        button(g, "picker_buy", px + 14 + half, by, half, 18, Component.literal(units > 0 ? "Buy " + Msg.number(units * count) : "Buy"),
                Theme.GREEN, units > 0 && units <= affordableUnits(offer) && !locked(offer), mouseX, mouseY, this::confirmPicker);
    }

    private void scrollbar(GuiGraphicsExtractor g, int x, int y, int h, int content, int maxScroll, double scrolled) {
        Draw.rounded(g, x, y, 3, h, Theme.TRACK);
        int thumb = Math.max(14, h * h / content);
        int thumbY = y + (int) Math.round((h - thumb) * scrolled / maxScroll);
        Draw.rounded(g, x, thumbY, 3, thumb, Draw.withAlpha(accent, 0.75f));
    }

    /** A short coloured glow after something was clicked, so a purchase or sale feels like it landed. */
    private void pulse(GuiGraphicsExtractor g, String key, int x, int y, int w, int h, int color) {
        Long at = pulses.get(key);
        if (at == null) {
            return;
        }
        float t = (Util.getMillis() - at) / 380f;
        if (t >= 1) {
            pulses.remove(key);
            return;
        }
        Draw.rounded(g, x + 1, y + 1, w - 2, h - 2, Draw.withAlpha(color, 0.24f * (1 - t)));
    }

    // ------------------------------------------------------------------ data

    /** What in the inventory this keeper buys, one entry per item, most valuable first. */
    private List<Sellable> sellables() {
        Map<Identifier, Long> prices = shop.selling().prices();
        Map<Item, Sellable> found = new LinkedHashMap<>();
        if (minecraft.player == null || prices.isEmpty()) {
            return List.of();
        }
        Inventory inventory = minecraft.player.getInventory();
        for (int slot = 0; slot < Inventory.INVENTORY_SIZE; slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (stack.isEmpty()) {
                continue;
            }
            Long price = prices.get(BuiltInRegistries.ITEM.getKey(stack.getItem()));
            if (price == null) {
                continue;
            }
            found.merge(stack.getItem(), new Sellable(stack.getItem(), stack.copyWithCount(1), stack.getCount(), price),
                    (a, b) -> new Sellable(a.item(), a.icon(), a.count() + b.count(), a.unit()));
        }
        List<Sellable> list = new ArrayList<>(found.values());
        list.sort((a, b) -> Long.compare(b.total(), a.total()));
        return list;
    }

    private static int nameColor(ItemStack stack) {
        TextColor color = stack.getStyledHoverName().getStyle().getColor();
        return color == null ? Theme.TEXT : 0xFF000000 | color.getValue();
    }

    private static int stackUnits(EconomyPayloads.Offer offer) {
        return Math.max(1, offer.stack().getMaxStackSize() / offer.stack().getCount());
    }

    private static long saturatingTimes(long a, long b) {
        return b != 0 && a > Long.MAX_VALUE / b ? Long.MAX_VALUE : a * b;
    }

    // ------------------------------------------------------------------ actions

    private boolean clickOffer(int index, int button, boolean shift) {
        if (button == InputConstants.MOUSE_BUTTON_LEFT) {
            buy(index, shift ? stackUnits(shop.offers().get(index)) : 1);
            return true;
        }
        if (button == InputConstants.MOUSE_BUTTON_RIGHT) {
            openPicker(index);
            return true;
        }
        return false;
    }

    private void buy(int index, int units) {
        flash("offer" + index, true);
        pulses.put("offer" + index, Util.getMillis());
        ClientPlayNetworking.send(new EconomyPayloads.Buy(shop.shop(), index, units));
    }

    private void openPicker(int index) {
        pickerOffer = index;
        pickerField.set(1);
        pickerFresh = true;
        pickerOpenedAt = Util.getMillis();
        flash("picker", true);
    }

    private void closePicker() {
        pickerOffer = -1;
    }

    private void confirmPicker() {
        EconomyPayloads.Offer offer = shop.offers().get(pickerOffer);
        long units = Math.min(pickerField.value(), Shops.maxUnits(offer.stack().getMaxStackSize(), offer.stack().getCount()));
        if (units > 0) {
            buy(pickerOffer, (int) units);
            closePicker();
        }
    }

    private void adjust(int delta) {
        EconomyPayloads.Offer offer = shop.offers().get(pickerOffer);
        int maxUnits = Shops.maxUnits(offer.stack().getMaxStackSize(), offer.stack().getCount());
        long value = Math.max(pickerField.value(), 0);
        pickerField.set(Mth.clamp(value + delta, 1, maxUnits));
        pickerFresh = false;
    }

    private void moveFocus(int delta) {
        int size = shop.offers().size();
        if (size == 0) {
            return;
        }
        focused = focused < 0 ? 0 : Mth.clamp(focused + delta, 0, size - 1);
        // Keep the focused card in view.
        int row = focused / columns;
        int cardTop = row * (CARD_HEIGHT + GAP);
        if (cardTop < scrollTarget) {
            scrollTarget = cardTop;
        } else if (cardTop + CARD_HEIGHT > scrollTarget + bodyHeight()) {
            scrollTarget = cardTop + CARD_HEIGHT - bodyHeight();
        }
    }

    // ------------------------------------------------------------------ input

    @Override
    public boolean keyPressed(KeyEvent event) {
        int key = event.key();
        boolean enter = key == InputConstants.KEY_RETURN || key == InputConstants.KEY_NUMPADENTER;
        if (pickerOffer >= 0) {
            if (key == InputConstants.KEY_ESCAPE) {
                closePicker();
                return true;
            }
            if (enter) {
                confirmPicker();
                return true;
            }
            if (key == InputConstants.KEY_BACKSPACE) {
                if (pickerFresh) {
                    pickerField.clear();
                    pickerFresh = false;
                } else {
                    pickerField.backspace(event.hasControlDown());
                }
                return true;
            }
            if (key == InputConstants.KEY_UP || key == InputConstants.KEY_DOWN) {
                int step = event.hasShiftDown() ? 8 : 1;
                adjust(key == InputConstants.KEY_UP ? step : -step);
                return true;
            }
            return super.keyPressed(event);
        }
        if (key == InputConstants.KEY_LEFT || key == InputConstants.KEY_RIGHT) {
            moveFocus(key == InputConstants.KEY_LEFT ? -1 : 1);
            return true;
        }
        if (key == InputConstants.KEY_UP || key == InputConstants.KEY_DOWN) {
            moveFocus(key == InputConstants.KEY_UP ? -columns : columns);
            return true;
        }
        if (enter && focused >= 0) {
            buy(focused, event.hasShiftDown() ? stackUnits(shop.offers().get(focused)) : 1);
            return true;
        }
        if (key == InputConstants.KEY_SPACE && focused >= 0) {
            openPicker(focused);
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public boolean charTyped(CharacterEvent event) {
        if (pickerOffer >= 0 && Character.isBmpCodePoint(event.codepoint())) {
            char c = (char) event.codepoint();
            if (Character.isDigit(c)) {
                if (pickerFresh) {
                    pickerField.clear();
                    pickerFresh = false;
                }
                pickerField.type(c);
            }
            return true;
        }
        return super.charTyped(event);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (pickerOffer >= 0) {
            adjust(scrollY > 0 ? 1 : -1);
            return true;
        }
        if (shop.selling().buys() && mouseX >= sellLeft && mouseX < sellRight) {
            sellScrollTarget -= scrollY * ROW_HEIGHT;
            return true;
        }
        if (mouseX >= gridLeft && mouseX < gridRight) {
            scrollTarget -= scrollY * (CARD_HEIGHT + GAP) / 2.0;
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }
}
