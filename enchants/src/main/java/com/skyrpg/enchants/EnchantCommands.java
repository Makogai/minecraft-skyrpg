package com.skyrpg.enchants;

import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import java.util.Set;
import java.util.TreeSet;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/**
 * Admin commands (op level 2), mostly for testing and events:
 * <pre>
 * /enchants                                        open the enchanting screen (as at Seraphine's)
 * /enchants tome &lt;players&gt; &lt;enchant&gt; [level]         give a tome
 * /enchants add &lt;players&gt; &lt;enchant&gt; &lt;level&gt;          put an enchant on the held item, no cost
 * /enchants clear &lt;players&gt;                        remove every enchant from the held item
 * /enchants codex &lt;players&gt; all|reset              fill or empty the Codex
 * /enchants test &lt;player&gt; enchant &lt;slot&gt; &lt;enchant&gt; &lt;level&gt; [replace]   what the screen's buttons do,
 * /enchants test &lt;player&gt; tome &lt;tomeSlot&gt; &lt;itemSlot&gt; [replace]          for a player in front of a table
 * /enchants test &lt;player&gt; combine &lt;slot&gt; &lt;slot&gt;
 * /enchants test &lt;player&gt; salvage &lt;slot&gt;
 * /enchants test &lt;player&gt; tab &lt;0-2&gt;                        switch their open screen's tab
 * </pre>
 * Enchants are named by path ({@code keen}) or full id.
 */
final class EnchantCommands {
    private static final SuggestionProvider<CommandSourceStack> ENCHANTS = (context, builder) ->
            SharedSuggestionProvider.suggest(Enchants.all().stream().map(def -> def.id().getPath()), builder);

    private EnchantCommands() {
    }

    static void init() {
        CommandRegistrationCallback.EVENT.register((dispatcher, context, selection) -> dispatcher.register(Commands.literal("enchants")
                .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .executes(ctx -> {
                    Enchanting.open(ctx.getSource().getPlayerOrException(), null);
                    return 1;
                })
                .then(Commands.literal("tome").then(Commands.argument("players", EntityArgument.players())
                        .then(Commands.argument("enchant", StringArgumentType.word()).suggests(ENCHANTS)
                                .executes(ctx -> tome(ctx, -1))
                                .then(Commands.argument("level", IntegerArgumentType.integer(1, 20))
                                        .executes(ctx -> tome(ctx, IntegerArgumentType.getInteger(ctx, "level")))))))
                .then(Commands.literal("add").then(Commands.argument("players", EntityArgument.players())
                        .then(Commands.argument("enchant", StringArgumentType.word()).suggests(ENCHANTS)
                                .then(Commands.argument("level", IntegerArgumentType.integer(1, 20)).executes(ctx -> {
                                    Enchant def = enchant(ctx);
                                    int level = Math.min(def.maxLevel(), IntegerArgumentType.getInteger(ctx, "level"));
                                    int count = 0;
                                    for (ServerPlayer player : EntityArgument.getPlayers(ctx, "players")) {
                                        ItemStack held = player.getMainHandItem();
                                        if (!held.isEmpty() && def.appliesTo(held)) {
                                            EnchantContent.setEnchants(held, EnchantContent.enchants(held).with(def.id(), level));
                                            Codex.discover(player, def.id());
                                            count++;
                                        }
                                    }
                                    return done(ctx.getSource(), "Added " + def.title(level) + " to " + count + " held item(s)");
                                })))))
                .then(Commands.literal("clear").then(Commands.argument("players", EntityArgument.players()).executes(ctx -> {
                    for (ServerPlayer player : EntityArgument.getPlayers(ctx, "players")) {
                        EnchantContent.setEnchants(player.getMainHandItem(), EnchantList.EMPTY);
                    }
                    return done(ctx.getSource(), "Cleared held items' enchants");
                })))
                .then(Commands.literal("codex").then(Commands.argument("players", EntityArgument.players())
                        .then(Commands.literal("all").executes(ctx -> {
                            for (ServerPlayer player : EntityArgument.getPlayers(ctx, "players")) {
                                Set<String> all = new TreeSet<>();
                                Enchants.all().forEach(def -> all.add(def.id().toString()));
                                player.setAttached(Codex.STATE, new Codex.State(Set.copyOf(all)));
                            }
                            return done(ctx.getSource(), "Filled the Codex");
                        }))
                        .then(Commands.literal("reset").executes(ctx -> {
                            for (ServerPlayer player : EntityArgument.getPlayers(ctx, "players")) {
                                player.setAttached(Codex.STATE, Codex.State.EMPTY);
                            }
                            return done(ctx.getSource(), "Emptied the Codex");
                        }))))
                .then(test())));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> test() {
        return Commands.literal("test").then(Commands.argument("player", EntityArgument.player())
                .then(Commands.literal("tab").then(Commands.argument("tab", IntegerArgumentType.integer(0, 2)).executes(ctx -> {
                    Enchanting.showTab(EntityArgument.getPlayer(ctx, "player"), IntegerArgumentType.getInteger(ctx, "tab"));
                    return 1;
                })))
                .then(Commands.literal("enchant").then(Commands.argument("slot", IntegerArgumentType.integer(0, 40))
                        .then(Commands.argument("enchant", StringArgumentType.word()).suggests(ENCHANTS)
                                .then(Commands.argument("level", IntegerArgumentType.integer(1, 20))
                                        .executes(ctx -> testEnchant(ctx, false))
                                        .then(Commands.argument("replace", BoolArgumentType.bool())
                                                .executes(ctx -> testEnchant(ctx, BoolArgumentType.getBool(ctx, "replace"))))))))
                .then(Commands.literal("tome").then(Commands.argument("tome", IntegerArgumentType.integer(0, 40))
                        .then(Commands.argument("item", IntegerArgumentType.integer(0, 40))
                                .executes(ctx -> testTome(ctx, false))
                                .then(Commands.argument("replace", BoolArgumentType.bool())
                                        .executes(ctx -> testTome(ctx, BoolArgumentType.getBool(ctx, "replace")))))))
                .then(Commands.literal("combine").then(Commands.argument("first", IntegerArgumentType.integer(0, 40))
                        .then(Commands.argument("second", IntegerArgumentType.integer(0, 40)).executes(ctx -> {
                            ServerPlayer player = EntityArgument.getPlayer(ctx, "player");
                            return report(ctx, player, Enchanting.combine(player, IntegerArgumentType.getInteger(ctx, "first"),
                                    IntegerArgumentType.getInteger(ctx, "second")), EnchantPayloads.FLASH_COMBINE);
                        }))))
                .then(Commands.literal("salvage").then(Commands.argument("slot", IntegerArgumentType.integer(0, 40)).executes(ctx -> {
                    ServerPlayer player = EntityArgument.getPlayer(ctx, "player");
                    return report(ctx, player, Enchanting.salvage(player, IntegerArgumentType.getInteger(ctx, "slot")), EnchantPayloads.FLASH_SALVAGE);
                }))));
    }

    private static int testEnchant(CommandContext<CommandSourceStack> ctx, boolean replace) throws CommandSyntaxException {
        ServerPlayer player = EntityArgument.getPlayer(ctx, "player");
        Enchant def = enchant(ctx);
        return report(ctx, player, Enchanting.enchant(player, IntegerArgumentType.getInteger(ctx, "slot"), def.id(),
                IntegerArgumentType.getInteger(ctx, "level"), replace), EnchantPayloads.FLASH_ENCHANT);
    }

    private static int testTome(CommandContext<CommandSourceStack> ctx, boolean replace) throws CommandSyntaxException {
        ServerPlayer player = EntityArgument.getPlayer(ctx, "player");
        return report(ctx, player, Enchanting.applyTome(player, IntegerArgumentType.getInteger(ctx, "tome"),
                IntegerArgumentType.getInteger(ctx, "item"), replace), EnchantPayloads.FLASH_TOME);
    }

    private static int report(CommandContext<CommandSourceStack> ctx, ServerPlayer player, Enchanting.Result result, int flash) {
        Enchanting.answer(player, result, flash);
        if (result.ok()) {
            return done(ctx.getSource(), "OK: " + result.notice().getString());
        }
        ctx.getSource().sendFailure(Component.literal("Refused: " + result.notice().getString()));
        return 0;
    }

    private static int tome(CommandContext<CommandSourceStack> ctx, int level) throws CommandSyntaxException {
        Enchant def = enchant(ctx);
        int actual = level < 0 ? def.maxLevel() : Math.min(level, def.maxLevel());
        for (ServerPlayer player : EntityArgument.getPlayers(ctx, "players")) {
            Enchanting.give(player, EnchantContent.tome(def, actual));
            Codex.discover(player, def.id());
        }
        return done(ctx.getSource(), "Gave Tome of " + def.title(actual));
    }

    private static Enchant enchant(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        String name = StringArgumentType.getString(ctx, "enchant");
        Identifier id = name.contains(":") ? Identifier.tryParse(name) : SkyRpgEnchants.id(name);
        Enchant def = id == null ? null : Enchants.get(id);
        if (def == null) {
            def = Enchants.all().stream().filter(e -> e.id().getPath().equals(name)).findFirst().orElse(null);
        }
        if (def == null) {
            throw new com.mojang.brigadier.exceptions.SimpleCommandExceptionType(Component.literal("No enchant named " + name)).create();
        }
        return def;
    }

    private static int done(CommandSourceStack source, String message) {
        source.sendSuccess(() -> Component.literal(message), true);
        return 1;
    }
}
