package com.skyrpg.magic;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.IdentifierArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/** Admin tools for testing and support: {@code /magic install <spell>}, {@code /magic clear}, {@code /magic cast <spell> [charge]}. */
final class MagicCommands {
    private MagicCommands() {
    }

    static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("magic")
                .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .then(Commands.literal("install")
                        .then(Commands.argument("spell", IdentifierArgument.id())
                                .suggests((ctx, builder) -> SharedSuggestionProvider.suggestResource(Magic.spells().stream().map(SpellDef::id).toList(), builder))
                                .executes(MagicCommands::install)))
                .then(Commands.literal("clear").executes(MagicCommands::clear))
                .then(Commands.literal("cast")
                        .then(Commands.argument("spell", IdentifierArgument.id())
                                .suggests((ctx, builder) -> SharedSuggestionProvider.suggestResource(Magic.spells().stream().map(SpellDef::id).toList(), builder))
                                .executes(ctx -> cast(ctx, 0))
                                .then(Commands.argument("charge", DoubleArgumentType.doubleArg(0, 1))
                                        .executes(ctx -> cast(ctx, DoubleArgumentType.getDouble(ctx, "charge")))))));
    }

    private static int install(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        Identifier id = IdentifierArgument.getId(ctx, "spell");
        SpellDef spell = Magic.spell(id);
        ItemStack wand = player.getMainHandItem();
        if (spell == null || !WandItem.isWand(wand)) {
            ctx.getSource().sendFailure(Component.literal(spell == null ? "Unknown spell " + id : "Hold a wand first"));
            return 0;
        }
        WandData data = WandItem.data(wand);
        for (int i = 0; i < data.slots().size(); i++) {
            if (data.spellAt(i).isEmpty()) {
                WandItem.setData(wand, data.with(i, spell.id().toString()));
                ctx.getSource().sendSuccess(() -> Component.literal("Installed " + spell.name()), true);
                return 1;
            }
        }
        ctx.getSource().sendFailure(Component.literal("No free slot"));
        return 0;
    }

    private static int clear(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        ItemStack wand = player.getMainHandItem();
        if (!WandItem.isWand(wand)) {
            ctx.getSource().sendFailure(Component.literal("Hold a wand first"));
            return 0;
        }
        WandItem.setData(wand, WandData.empty(WandItem.data(wand).slots().size()));
        ctx.getSource().sendSuccess(() -> Component.literal("Emptied the wand's slots"), true);
        return 1;
    }

    private static int cast(CommandContext<CommandSourceStack> ctx, double charge) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        SpellDef spell = Magic.spell(IdentifierArgument.getId(ctx, "spell"));
        if (spell == null) {
            ctx.getSource().sendFailure(Component.literal("Unknown spell"));
            return 0;
        }
        return Casting.cast(player, spell, WandItem.def(player.getMainHandItem()), charge) ? 1 : 0;
    }
}
