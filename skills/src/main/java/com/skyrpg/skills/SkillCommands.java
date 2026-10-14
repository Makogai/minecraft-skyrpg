package com.skyrpg.skills;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.skyrpg.core.text.Msg;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;

/** /skills [player], plus admin /skills give and /skills reset. */
final class SkillCommands {
    private static final int BAR_LENGTH = 20;

    private SkillCommands() {
    }

    static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("skills")
                .executes(ctx -> show(ctx.getSource(), ctx.getSource().getPlayerOrException()))
                .then(Commands.argument("player", EntityArgument.player())
                        .executes(ctx -> show(ctx.getSource(), EntityArgument.getPlayer(ctx, "player"))))
                .then(Commands.literal("give")
                        .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                        .then(Commands.argument("players", EntityArgument.players())
                                .then(Commands.argument("skill", StringArgumentType.word())
                                        .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(skillNames(), builder))
                                        .then(Commands.argument("xp", DoubleArgumentType.doubleArg(0))
                                                .executes(SkillCommands::give)))))
                .then(Commands.literal("set")
                        .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                        .then(Commands.argument("players", EntityArgument.players())
                                .then(Commands.argument("skill", StringArgumentType.word())
                                        .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(skillNames(), builder))
                                        .then(Commands.argument("xp", DoubleArgumentType.doubleArg(0))
                                                .executes(SkillCommands::set)))))
                .then(Commands.literal("reset")
                        .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                        .then(Commands.argument("players", EntityArgument.players())
                                .executes(ctx -> {
                                    Collection<ServerPlayer> players = EntityArgument.getPlayers(ctx, "players");
                                    players.forEach(PlayerSkills::reset);
                                    ctx.getSource().sendSuccess(() -> Component.literal("Reset skills for " + players.size() + " player(s)"), true);
                                    return players.size();
                                }))));
    }

    private static List<String> skillNames() {
        List<String> names = new ArrayList<>();
        Skills.all().forEach(skill -> names.add(skill.id().getPath()));
        return names;
    }

    private static int give(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        Skill skill = Skills.find(StringArgumentType.getString(ctx, "skill"));
        if (skill == null) {
            ctx.getSource().sendFailure(Component.literal("Unknown skill. Try one of: " + String.join(", ", skillNames())));
            return 0;
        }
        double xp = DoubleArgumentType.getDouble(ctx, "xp");
        Collection<ServerPlayer> players = EntityArgument.getPlayers(ctx, "players");
        for (ServerPlayer player : players) {
            PlayerSkills.addXp(player, skill, xp);
        }
        ctx.getSource().sendSuccess(() -> Component.literal("Gave " + Msg.number(xp) + " ").append(skill.displayName())
                .append(" XP to " + players.size() + " player(s)"), true);
        return players.size();
    }

    private static int set(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        Skill skill = Skills.find(StringArgumentType.getString(ctx, "skill"));
        if (skill == null) {
            ctx.getSource().sendFailure(Component.literal("Unknown skill. Try one of: " + String.join(", ", skillNames())));
            return 0;
        }
        double xp = DoubleArgumentType.getDouble(ctx, "xp");
        Collection<ServerPlayer> players = EntityArgument.getPlayers(ctx, "players");
        players.forEach(player -> PlayerSkills.setXp(player, skill, xp));
        ctx.getSource().sendSuccess(() -> Component.literal("Set ").append(skill.displayName())
                .append(" XP to " + Msg.number(xp) + " for " + players.size() + " player(s)"), true);
        return players.size();
    }

    private static int show(CommandSourceStack source, ServerPlayer player) {
        boolean self = player == source.getEntity();
        source.sendSuccess(() -> Msg.text(self ? "Your Skills" : player.getScoreboardName() + "'s Skills", ChatFormatting.GOLD, ChatFormatting.BOLD), false);
        int total = 0;
        for (Skill skill : Skills.all()) {
            double xp = PlayerSkills.getXp(player, skill);
            int level = skill.levelFor(xp);
            total += level;
            MutableComponent line = Component.literal(" ").append(skill.displayName()).append(" " + Msg.roman(level) + " ");
            if (level >= skill.maxLevel()) {
                line.append(Msg.text("MAX ", ChatFormatting.GOLD)).append(Msg.text(Msg.number(xp) + " XP", ChatFormatting.GRAY));
            } else {
                double into = skill.xpIntoLevel(xp);
                long needed = skill.xpForNextLevel(level);
                int filled = (int) Math.floor(BAR_LENGTH * into / needed);
                line.append(Msg.text("-".repeat(filled), ChatFormatting.DARK_GREEN, ChatFormatting.STRIKETHROUGH))
                        .append(Msg.text("-".repeat(BAR_LENGTH - filled), ChatFormatting.WHITE, ChatFormatting.STRIKETHROUGH))
                        .append(Msg.text(" " + Msg.number(into) + "/" + Msg.number(needed), ChatFormatting.GRAY));
            }
            source.sendSuccess(() -> line, false);
        }
        return total;
    }
}
