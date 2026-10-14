package com.skyrpg.economy;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.skyrpg.core.text.Msg;
import com.skyrpg.economy.bank.Bank;
import com.skyrpg.economy.shop.Shops;
import java.util.Collection;
import java.util.function.BiConsumer;
import java.util.function.Predicate;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.IdentifierArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * /coins, /pay, /sell and /worth, plus /shop and /bank for admins (players use the NPCs). Feedback goes through the command source, so it also shows up
 * for /execute and RCON.
 */
final class EconomyCommands {
    private EconomyCommands() {
    }

    static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("coins")
                .executes(ctx -> showCoins(ctx.getSource(), ctx.getSource().getPlayerOrException()))
                .then(Commands.argument("player", EntityArgument.player())
                        .executes(ctx -> showCoins(ctx.getSource(), EntityArgument.getPlayer(ctx, "player"))))
                .then(adminCommand("give", Purse::add))
                .then(adminCommand("take", (player, amount) -> Purse.set(player, Purse.get(player) - amount)))
                .then(adminCommand("set", Purse::set)));

        dispatcher.register(Commands.literal("pay")
                .then(Commands.argument("player", EntityArgument.player())
                        .then(Commands.argument("amount", LongArgumentType.longArg(1))
                                .executes(EconomyCommands::pay))));

        dispatcher.register(Commands.literal("sell")
                .executes(ctx -> sellHand(ctx.getSource()))
                .then(Commands.literal("hand").executes(ctx -> sellHand(ctx.getSource())))
                .then(Commands.literal("all").executes(ctx -> sellAllOfHeld(ctx.getSource())))
                .then(Commands.literal("inventory").executes(ctx -> sellMatching(ctx.getSource(), stack -> true))));

        dispatcher.register(Commands.literal("worth").executes(ctx -> worth(ctx.getSource())));

        dispatcher.register(Commands.literal("shop")
                .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .then(Commands.argument("shop", IdentifierArgument.id())
                        .suggests((ctx, builder) -> SharedSuggestionProvider.suggestResource(Shops.all().keySet(), builder))
                        .executes(ctx -> {
                            if (!Shops.open(ctx.getSource().getPlayerOrException(), IdentifierArgument.getId(ctx, "shop"))) {
                                ctx.getSource().sendFailure(Component.literal("No such shop."));
                                return 0;
                            }
                            return 1;
                        })));

        dispatcher.register(Commands.literal("bank")
                .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .executes(ctx -> {
                    Bank.open(ctx.getSource().getPlayerOrException(), "");
                    return 1;
                }));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> adminCommand(String name, BiConsumer<ServerPlayer, Long> change) {
        return Commands.literal(name)
                .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .then(Commands.argument("players", EntityArgument.players())
                        .then(Commands.argument("amount", LongArgumentType.longArg(0))
                                .executes(ctx -> {
                                    Collection<ServerPlayer> players = EntityArgument.getPlayers(ctx, "players");
                                    long amount = LongArgumentType.getLong(ctx, "amount");
                                    for (ServerPlayer player : players) {
                                        change.accept(player, amount);
                                        ctx.getSource().sendSuccess(() -> player.getDisplayName().copy()
                                                .append(Msg.text(" now has ", ChatFormatting.GRAY))
                                                .append(Coins.text(Purse.get(player))), true);
                                    }
                                    return players.size();
                                })));
    }

    private static int showCoins(CommandSourceStack source, ServerPlayer player) {
        long coins = Purse.get(player);
        source.sendSuccess(() -> Msg.text("Purse: ", ChatFormatting.GRAY).append(Coins.text(coins))
                .append(Msg.text("   Bank: ", ChatFormatting.GRAY)).append(Coins.text(Bank.balance(player)))
                .append(Msg.text(" (" + player.getScoreboardName() + ")", ChatFormatting.DARK_GRAY)), false);
        return (int) Math.min(coins, Integer.MAX_VALUE);
    }

    /** The checks, messages and sounds live in {@link Payments#transfer}, shared with the menu's Pay page. */
    private static int pay(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer from = ctx.getSource().getPlayerOrException();
        ServerPlayer to = EntityArgument.getPlayer(ctx, "player");
        Payments.Result result = Payments.transfer(from, to, LongArgumentType.getLong(ctx, "amount"));
        if (!result.success()) {
            ctx.getSource().sendFailure(result.message());
            return 0;
        }
        ctx.getSource().sendSuccess(result::message, false);
        return 1;
    }

    private static int sellHand(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        Inventory inventory = player.getInventory();
        ItemStack held = inventory.getSelectedItem();
        if (held.isEmpty()) {
            source.sendFailure(Component.literal("Hold the item you want to sell."));
            return 0;
        }
        long price = SellPrices.priceOf(held);
        if (price <= 0) {
            source.sendFailure(Component.literal("NPCs won't buy ").append(held.getHoverName()).append("."));
            return 0;
        }
        int count = held.getCount();
        Item item = held.getItem();
        inventory.setItem(inventory.getSelectedSlot(), ItemStack.EMPTY);
        return finishSale(source, player, item, count, price);
    }

    private static int sellAllOfHeld(CommandSourceStack source) throws CommandSyntaxException {
        ItemStack held = source.getPlayerOrException().getInventory().getSelectedItem();
        if (held.isEmpty()) {
            source.sendFailure(Component.literal("Hold the item you want to sell."));
            return 0;
        }
        Item item = held.getItem();
        return sellMatching(source, stack -> stack.getItem() == item);
    }

    /** Sells every sellable stack in the main inventory and hotbar that passes the filter. Armor and offhand are never touched. */
    private static int sellMatching(CommandSourceStack source, Predicate<ItemStack> filter) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        Inventory inventory = player.getInventory();
        long total = 0;
        int sold = 0;
        Item onlyItem = null;
        boolean mixed = false;
        for (int slot = 0; slot < Inventory.INVENTORY_SIZE; slot++) {
            ItemStack stack = inventory.getItem(slot);
            long price = SellPrices.priceOf(stack);
            if (price <= 0 || !filter.test(stack)) {
                continue;
            }
            mixed |= onlyItem != null && onlyItem != stack.getItem();
            onlyItem = stack.getItem();
            total += price;
            sold += stack.getCount();
            inventory.setItem(slot, ItemStack.EMPTY);
        }
        if (sold == 0) {
            source.sendFailure(Component.literal("You have nothing NPCs will buy."));
            return 0;
        }
        if (!mixed) {
            return finishSale(source, player, onlyItem, sold, total);
        }
        Purse.add(player, total);
        int items = sold;
        long coins = total;
        source.sendSuccess(() -> Msg.text("You sold " + Msg.number(items) + " items for ", ChatFormatting.GREEN).append(Coins.text(coins))
                .append(Msg.text("!", ChatFormatting.GREEN)), false);
        Msg.playSound(player, SoundEvents.EXPERIENCE_ORB_PICKUP, 0.6f, 1.2f);
        return items;
    }

    private static int finishSale(CommandSourceStack source, ServerPlayer player, Item item, int count, long total) {
        Purse.add(player, total);
        Component name = item.getDefaultInstance().getHoverName();
        source.sendSuccess(() -> Msg.text("You sold ", ChatFormatting.GREEN).append(name.copy().withStyle(ChatFormatting.WHITE))
                .append(Msg.text(" x" + count, ChatFormatting.DARK_GRAY)).append(Msg.text(" for ", ChatFormatting.GREEN))
                .append(Coins.text(total)).append(Msg.text("!", ChatFormatting.GREEN)), false);
        Msg.playSound(player, SoundEvents.EXPERIENCE_ORB_PICKUP, 0.6f, 1.2f);
        return count;
    }

    private static int worth(CommandSourceStack source) throws CommandSyntaxException {
        ItemStack held = source.getPlayerOrException().getInventory().getSelectedItem();
        long each = held.isEmpty() ? 0 : SellPrices.priceOf(held.getItem());
        if (each <= 0) {
            source.sendFailure(Component.literal("NPCs won't buy that."));
            return 0;
        }
        source.sendSuccess(() -> held.getHoverName().copy().append(Msg.text(" sells for ", ChatFormatting.GRAY)).append(Coins.text(each))
                .append(Msg.text(" each (", ChatFormatting.GRAY)).append(Coins.text(each * held.getCount()))
                .append(Msg.text(" for this stack)", ChatFormatting.GRAY)), false);
        return (int) Math.min(each, Integer.MAX_VALUE);
    }
}
