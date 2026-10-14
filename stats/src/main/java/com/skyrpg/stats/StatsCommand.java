package com.skyrpg.stats;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.skyrpg.core.stats.StatType;
import com.skyrpg.core.stats.StatTypes;
import com.skyrpg.core.text.Msg;
import java.util.Map;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;

/** /stats [player]: every stat with its total and where the bonuses come from. */
final class StatsCommand {
    private StatsCommand() {
    }

    static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("stats")
                .executes(ctx -> show(ctx.getSource(), ctx.getSource().getPlayerOrException()))
                .then(Commands.argument("player", EntityArgument.player())
                        .executes(ctx -> show(ctx.getSource(), EntityArgument.getPlayer(ctx, "player"))))
                // Admin: /stats bonus <players> <stat> <amount> (0 removes it), /stats bonus <players> clear
                .then(Commands.literal("bonus")
                        .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                        .then(Commands.argument("players", EntityArgument.players())
                                .then(Commands.literal("clear").executes(ctx -> {
                                    var players = EntityArgument.getPlayers(ctx, "players");
                                    players.forEach(AdminBonuses::clear);
                                    ctx.getSource().sendSuccess(() -> Component.literal("Cleared admin stat bonuses for " + players.size() + " player(s)"), true);
                                    return players.size();
                                }))
                                .then(Commands.argument("stat", StringArgumentType.word())
                                        .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(
                                                StatTypes.all().stream().filter(StatTypes::isPlayerStat).map(StatType::id).toList(), builder))
                                        .then(Commands.argument("amount", DoubleArgumentType.doubleArg())
                                                .executes(StatsCommand::bonus)))))
                // Admin: /stats mana <players> <amount> sets current mana (capped at max)
                .then(Commands.literal("mana")
                        .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                        .then(Commands.argument("players", EntityArgument.players())
                                .then(Commands.argument("amount", DoubleArgumentType.doubleArg(0))
                                        .executes(ctx -> {
                                            var players = EntityArgument.getPlayers(ctx, "players");
                                            double amount = DoubleArgumentType.getDouble(ctx, "amount");
                                            players.forEach(player -> PlayerStats.setMana(player, Math.min(amount, PlayerStats.maxMana(player))));
                                            ctx.getSource().sendSuccess(() -> Component.literal("Set mana for " + players.size() + " player(s)"), true);
                                            return players.size();
                                        })))));
    }

    private static int bonus(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        StatType stat = StatTypes.get(StringArgumentType.getString(ctx, "stat"));
        if (stat == null || !StatTypes.isPlayerStat(stat)) {
            ctx.getSource().sendFailure(Component.literal("Unknown stat"));
            return 0;
        }
        double amount = DoubleArgumentType.getDouble(ctx, "amount");
        var players = EntityArgument.getPlayers(ctx, "players");
        players.forEach(player -> AdminBonuses.set(player, stat, amount));
        ctx.getSource().sendSuccess(() -> Component.literal("Admin bonus " + stat.formatBonus(amount) + " " + stat.name() + " for " + players.size() + " player(s)"), true);
        return players.size();
    }

    private static int show(CommandSourceStack source, ServerPlayer player) {
        // Make sure the numbers reflect what the player is wearing right now.
        StatEngine.recompute(player);
        boolean self = player == source.getEntity();
        MutableComponent out = Msg.text(self ? "Your Stats" : player.getScoreboardName() + "'s Stats", ChatFormatting.GOLD, ChatFormatting.BOLD);
        for (StatType stat : StatTypes.all()) {
            if (!StatTypes.isPlayerStat(stat)) {
                continue;
            }
            out.append("\n ").append(Component.literal(stat.symbol() + " " + stat.name() + " ").withColor(stat.color()))
                    .append(Msg.text(stat.format(PlayerStats.total(player, stat)), ChatFormatting.WHITE));
            Map<String, Double> sources = PlayerStats.breakdown(player, stat);
            if (!sources.isEmpty()) {
                StringBuilder detail = new StringBuilder("  (base " + stat.format(stat.base()));
                sources.forEach((name, value) -> detail.append(", ").append(name).append(' ').append(stat.formatBonus(value)));
                out.append(Msg.text(detail.append(')').toString(), ChatFormatting.DARK_GRAY));
            }
        }
        out.append("\n ").append(Msg.text(StatTypes.MANA_ICON + " Mana ", ChatFormatting.AQUA))
                .append(Msg.text(Msg.number(Math.floor(PlayerStats.mana(player))) + " / " + Msg.number(PlayerStats.maxMana(player)), ChatFormatting.WHITE));
        source.sendSuccess(() -> out, false);
        return 1;
    }
}
