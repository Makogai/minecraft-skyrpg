package com.skyrpg.items;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import java.util.Collection;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.IdentifierArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/** /items list, and admin /items give &lt;players&gt; &lt;item&gt; [count]. */
final class ItemCommands {
    private ItemCommands() {
    }

    static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("items")
                .then(Commands.literal("list").executes(ctx -> list(ctx.getSource())))
                .then(Commands.literal("give")
                        .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                        .then(Commands.argument("players", EntityArgument.players())
                                .then(Commands.argument("item", IdentifierArgument.id())
                                        .suggests((ctx, builder) -> SharedSuggestionProvider.suggestResource(
                                                ItemRegistry.items().stream().map(ItemDefinition::id).toList(), builder))
                                        .executes(ctx -> give(ctx, 1))
                                        .then(Commands.argument("count", IntegerArgumentType.integer(1, 64))
                                                .executes(ctx -> give(ctx, IntegerArgumentType.getInteger(ctx, "count"))))))));
    }

    private static int give(CommandContext<CommandSourceStack> ctx, int count) throws CommandSyntaxException {
        Identifier id = IdentifierArgument.getId(ctx, "item");
        ItemDefinition def = ItemRegistry.find(id);
        if (def == null) {
            ctx.getSource().sendFailure(Component.literal("Unknown item " + id + ". See /items list."));
            return 0;
        }
        Collection<ServerPlayer> players = EntityArgument.getPlayers(ctx, "players");
        for (ServerPlayer player : players) {
            ItemStack stack = SkyItems.create(def.id(), count);
            if (!player.getInventory().add(stack)) {
                player.spawnAtLocation(player.level(), stack);
            }
        }
        ctx.getSource().sendSuccess(() -> Component.literal("Gave " + count + "x ").append(name(def))
                .append(" to " + players.size() + " player(s)"), true);
        return players.size();
    }

    private static int list(CommandSourceStack source) {
        MutableComponent text = Component.literal("SkyRPG items (" + ItemRegistry.items().size() + "):").withStyle(ChatFormatting.GOLD);
        for (ItemDefinition def : ItemRegistry.items()) {
            text.append("\n ").append(name(def)).append(Component.literal("  " + def.id()).withStyle(ChatFormatting.DARK_GRAY));
        }
        source.sendSuccess(() -> text, false);
        return ItemRegistry.items().size();
    }

    private static Component name(ItemDefinition def) {
        return Component.literal(def.name()).withColor(def.rarity().color());
    }
}
