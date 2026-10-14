package com.skyrpg.party;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.server.level.ServerPlayer;

/** /party (and /p) with its subcommands, and /pc for a quick party message. */
final class PartyCommands {
    private PartyCommands() {
    }

    private interface PlayerAction {
        void run(ServerPlayer player);
    }

    private interface NameAction {
        void run(ServerPlayer player, String name);
    }

    static void init() {
        CommandRegistrationCallback.EVENT.register((dispatcher, context, selection) -> {
            dispatcher.register(root("party"));
            dispatcher.register(root("p"));
            dispatcher.register(Commands.literal("pc").then(message((player, text) -> Parties.chat(player, text))));
        });
    }

    private static LiteralArgumentBuilder<CommandSourceStack> root(String name) {
        return Commands.literal(name)
                .executes(run(Parties::list))
                .then(Commands.literal("invite").then(Commands.argument("player", EntityArgument.player()).executes(ctx -> {
                    Parties.invite(ctx.getSource().getPlayerOrException(), EntityArgument.getPlayer(ctx, "player"));
                    return 1;
                })))
                .then(Commands.literal("accept").executes(run(player -> Parties.accept(player, null)))
                        .then(inviteArgument(Parties::accept)))
                .then(Commands.literal("deny").executes(run(player -> Parties.deny(player, null)))
                        .then(inviteArgument(Parties::deny)))
                .then(Commands.literal("leave").executes(run(Parties::leave)))
                .then(Commands.literal("kick").then(memberArgument(Parties::kick)))
                .then(Commands.literal("promote").then(memberArgument(Parties::promote)))
                .then(Commands.literal("disband").executes(run(Parties::disband)))
                .then(Commands.literal("list").executes(run(Parties::list)))
                .then(Commands.literal("admin").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                        .then(Commands.literal("list").executes(ctx -> Parties.adminList(ctx.getSource()))))
                .then(Commands.literal("chat").executes(run(Parties::toggleChat))
                        .then(message((player, text) -> Parties.chat(player, text))));
    }

    private static com.mojang.brigadier.Command<CommandSourceStack> run(PlayerAction action) {
        return ctx -> {
            action.run(ctx.getSource().getPlayerOrException());
            return 1;
        };
    }

    private static RequiredArgumentBuilder<CommandSourceStack, String> message(NameAction action) {
        return Commands.argument("message", StringArgumentType.greedyString()).executes(ctx -> {
            action.run(ctx.getSource().getPlayerOrException(), StringArgumentType.getString(ctx, "message"));
            return 1;
        });
    }

    private static RequiredArgumentBuilder<CommandSourceStack, String> inviteArgument(NameAction action) {
        return Commands.argument("player", StringArgumentType.word())
                .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(Parties.inviteNames(ctx.getSource().getPlayerOrException()), builder))
                .executes(ctx -> {
                    action.run(ctx.getSource().getPlayerOrException(), StringArgumentType.getString(ctx, "player"));
                    return 1;
                });
    }

    private static RequiredArgumentBuilder<CommandSourceStack, String> memberArgument(NameAction action) {
        return Commands.argument("player", StringArgumentType.word())
                .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(Parties.memberNames(ctx.getSource().getPlayerOrException()), builder))
                .executes(ctx -> {
                    action.run(ctx.getSource().getPlayerOrException(), StringArgumentType.getString(ctx, "player"));
                    return 1;
                });
    }
}
