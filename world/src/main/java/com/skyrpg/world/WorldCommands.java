package com.skyrpg.world;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.skyrpg.world.bloodmoon.BloodMoons;
import java.util.List;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.IdentifierArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;

/**
 * Admin and testing commands (ops only):
 * <pre>
 * /dungeon info [radius]          the dungeon markers around you and their state
 * /dungeon reset [radius]         bring back packs, bosses, closed doors and full chests around you
 * /dungeon spawnboss &lt;id&gt;         a dungeon boss right here (no room, no reset)
 * /dungeon fights                 boss fights in progress
 * /dungeon explorer reset &lt;player&gt;  forget their discoveries, clears, secrets and waystones
 * /waystone list                  every waystone the server knows
 * /layer spawn                    one of your deep layer's monster groups, near you now
 * </pre>
 * Anyone may use {@code /region} and {@code /layer} (where the danger is, which deep layer you're in).
 */
final class WorldCommands {
    private WorldCommands() {
    }

    static void init() {
        CommandRegistrationCallback.EVENT.register((dispatcher, context, selection) -> {
            dispatcher.register(Commands.literal("dungeon").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                    .then(Commands.literal("info")
                            .executes(ctx -> info(ctx, 48))
                            .then(Commands.argument("radius", IntegerArgumentType.integer(1, 256)).executes(ctx -> info(ctx, IntegerArgumentType.getInteger(ctx, "radius")))))
                    .then(Commands.literal("reset")
                            .executes(ctx -> reset(ctx, 96))
                            .then(Commands.argument("radius", IntegerArgumentType.integer(1, 256)).executes(ctx -> reset(ctx, IntegerArgumentType.getInteger(ctx, "radius")))))
                    .then(Commands.literal("spawnboss").then(Commands.argument("id", IdentifierArgument.id())
                            .suggests((ctx, builder) -> SharedSuggestionProvider.suggestResource(Dungeons.bosses().stream().map(Dungeons.Boss::id), builder))
                            .executes(WorldCommands::spawnBoss)))
                    .then(Commands.literal("fights").executes(ctx -> lines(ctx, DungeonBosses.fights(), "No boss fights right now.")))
                    .then(Commands.literal("pieces").executes(ctx -> lines(ctx, pieces(ctx.getSource()), "No dungeon here.")))
                    .then(Commands.literal("explorer").then(Commands.literal("reset").then(Commands.argument("player", EntityArgument.player())
                            .executes(ctx -> {
                                ServerPlayer player = EntityArgument.getPlayer(ctx, "player");
                                Explorer.reset(player);
                                ctx.getSource().sendSuccess(() -> Component.literal("Reset " + player.getName().getString() + "'s explorer data"), true);
                                return 1;
                            })))));
            // /region: anyone may ask where the danger is; /region list looks further (ops).
            dispatcher.register(Commands.literal("region")
                    .executes(ctx -> lines(ctx, DangerRegions.describe(ctx.getSource().getServer().overworld(),
                            (int) ctx.getSource().getPosition().x, (int) ctx.getSource().getPosition().z, 1), "No danger regions nearby."))
                    .then(Commands.literal("list").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                            .executes(ctx -> lines(ctx, DangerRegions.describe(ctx.getSource().getServer().overworld(),
                                    (int) ctx.getSource().getPosition().x, (int) ctx.getSource().getPosition().z, 4),
                                    "No danger regions within 3,000 blocks."))));
            // /layer: anyone may ask which deep layer they're in; /layer spawn (ops) spawns one of its groups nearby now.
            dispatcher.register(Commands.literal("layer")
                    .executes(ctx -> lines(ctx, DeepLayers.describe(ctx.getSource().getLevel(), net.minecraft.core.BlockPos.containing(ctx.getSource().getPosition())),
                            "No deep layers loaded."))
                    .then(Commands.literal("spawn").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                            .executes(ctx -> {
                                ServerPlayer player = ctx.getSource().getPlayerOrException();
                                DeepLayers.Layer layer = DeepLayers.underground(player.level(), player.blockPosition());
                                if (layer == null) {
                                    ctx.getSource().sendFailure(Component.literal("You're not in a deep layer (underground, y "
                                            + DeepLayers.lowestY() + " to " + DeepLayers.highestY() + ")."));
                                    return 0;
                                }
                                boolean spawned = DeepLayers.spawn(player.level(), player, layer);
                                ctx.getSource().sendSuccess(() -> Component.literal(spawned ? "Spawned a " + layer.name() + " group nearby"
                                        : "No group spawned (cap reached, or no dark standing room 10-24 blocks away)"), false);
                                return spawned ? 1 : 0;
                            })));
            RegionBosses.commands(dispatcher);
            BloodMoons.commands(dispatcher);
            dispatcher.register(Commands.literal("waystone").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                    .then(Commands.literal("list").executes(ctx -> lines(ctx, Waystones.list(ctx.getSource().getServer()), "No waystones yet."))));
        });
    }

    private static int info(CommandContext<CommandSourceStack> ctx, int radius) {
        return lines(ctx, DungeonMarkers.describe(ctx.getSource().getLevel(), ctx.getSource().getPosition(), radius), "No dungeon markers within " + radius + " blocks.");
    }

    private static int reset(CommandContext<CommandSourceStack> ctx, int radius) {
        int count = DungeonMarkers.reset(ctx.getSource().getLevel(), ctx.getSource().getPosition(), radius);
        ctx.getSource().sendSuccess(() -> Component.literal("Reset " + count + " dungeon markers"), true);
        return count;
    }

    private static int spawnBoss(CommandContext<CommandSourceStack> ctx) {
        Identifier id = IdentifierArgument.getId(ctx, "id");
        Dungeons.Boss boss = Dungeons.boss(id);
        if (boss == null) {
            ctx.getSource().sendFailure(Component.literal("No dungeon boss " + id));
            return 0;
        }
        return DungeonBosses.spawn(ctx.getSource().getLevel(), boss, ctx.getSource().getPosition(), null) == null ? 0 : 1;
    }

    /** Every piece of the dungeon you're standing in (for checking that the jigsaw built all of it). */
    private static List<String> pieces(CommandSourceStack source) {
        var level = source.getLevel();
        var start = level.structureManager().getStructureWithPieceAt(net.minecraft.core.BlockPos.containing(source.getPosition()),
                holder -> holder.unwrapKey().map(key -> Dungeons.byStructure(key.identifier()) != null).orElse(false));
        List<String> lines = new java.util.ArrayList<>();
        if (!start.isValid()) {
            return lines;
        }
        lines.add(start.getPieces().size() + " pieces");
        for (var piece : start.getPieces()) {
            String name = piece instanceof net.minecraft.world.level.levelgen.structure.PoolElementStructurePiece pool
                    ? pool.getElement().toString() : piece.getClass().getSimpleName();
            var box = piece.getBoundingBox();
            lines.add(name + " " + box.minX() + "," + box.minY() + "," + box.minZ() + " .. " + box.maxX() + "," + box.maxY() + "," + box.maxZ());
        }
        return lines;
    }

    private static int lines(CommandContext<CommandSourceStack> ctx, List<String> lines, String empty) {
        if (lines.isEmpty()) {
            ctx.getSource().sendSuccess(() -> Component.literal(empty), false);
        }
        lines.forEach(line -> ctx.getSource().sendSuccess(() -> Component.literal(line), false));
        return lines.size();
    }
}
