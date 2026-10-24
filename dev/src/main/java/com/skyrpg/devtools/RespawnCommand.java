package com.skyrpg.devtools;

import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * What the camera client can't click, done as the player on the server:
 * {@code /devtools respawn <players>} (the death screen), {@code /devtools use <player> <pos>} (right-click the top of
 * a block with the held item) and {@code /devtools break <player> <pos>} (break a block, with all the break events).
 */
final class RespawnCommand {
    private RespawnCommand() {
    }

    static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("devtools")
                .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .then(Commands.literal("respawn").then(Commands.argument("players", EntityArgument.players()).executes(ctx -> {
                    int count = 0;
                    for (ServerPlayer player : EntityArgument.getPlayers(ctx, "players")) {
                        if (player.isDeadOrDying()) {
                            // As the death screen's button does: the connection gets the new player.
                            player.connection.player = ctx.getSource().getServer().getPlayerList().respawn(player, false, Entity.RemovalReason.KILLED);
                            player.connection.resetPosition();
                            count++;
                        }
                    }
                    int respawned = count;
                    ctx.getSource().sendSuccess(() -> Component.literal("Respawned " + respawned), false);
                    return respawned;
                })))
                .then(Commands.literal("use").then(Commands.argument("player", EntityArgument.player())
                        .then(Commands.argument("pos", BlockPosArgument.blockPos()).executes(ctx -> {
                            ServerPlayer player = EntityArgument.getPlayer(ctx, "player");
                            BlockPos pos = BlockPosArgument.getLoadedBlockPos(ctx, "pos");
                            BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(pos).add(0, 0.5, 0), Direction.UP, pos, false);
                            InteractionResult result = player.gameMode.useItemOn(player, player.level(), player.getMainHandItem(),
                                    InteractionHand.MAIN_HAND, hit);
                            ctx.getSource().sendSuccess(() -> Component.literal("Use: " + result), false);
                            return 1;
                        }))))
                .then(Commands.literal("useitem").then(Commands.argument("player", EntityArgument.player()).executes(ctx -> {
                    // Right-click in the air with the main-hand item (opens item menus for screenshots).
                    ServerPlayer player = EntityArgument.getPlayer(ctx, "player");
                    InteractionResult result = player.gameMode.useItem(player, player.level(), player.getMainHandItem(), InteractionHand.MAIN_HAND);
                    ctx.getSource().sendSuccess(() -> Component.literal("Use item: " + result), false);
                    return 1;
                })))
                .then(Commands.literal("click").then(Commands.argument("player", EntityArgument.player())
                        .then(Commands.argument("slot", com.mojang.brigadier.arguments.IntegerArgumentType.integer(-999))
                                .executes(ctx -> click(ctx.getSource(), EntityArgument.getPlayer(ctx, "player"),
                                        com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(ctx, "slot"), false))
                                .then(Commands.literal("shift").executes(ctx -> click(ctx.getSource(), EntityArgument.getPlayer(ctx, "player"),
                                        com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(ctx, "slot"), true))))))
                .then(Commands.literal("break").then(Commands.argument("player", EntityArgument.player())
                        .then(Commands.argument("pos", BlockPosArgument.blockPos()).executes(ctx -> {
                            ServerPlayer player = EntityArgument.getPlayer(ctx, "player");
                            BlockPos pos = BlockPosArgument.getLoadedBlockPos(ctx, "pos");
                            boolean broken = player.gameMode.destroyBlock(pos);
                            ctx.getSource().sendSuccess(() -> Component.literal("Break: " + broken), false);
                            return broken ? 1 : 0;
                        })))));
    }

    /** Clicks (or shift-clicks) a slot of the player's open menu, as if they had, for testing menus without a mouse. */
    private static int click(net.minecraft.commands.CommandSourceStack source, ServerPlayer player, int slot, boolean shift) {
        player.containerMenu.clicked(slot, 0, shift ? net.minecraft.world.inventory.ContainerInput.QUICK_MOVE
                : net.minecraft.world.inventory.ContainerInput.PICKUP, player);
        player.containerMenu.broadcastChanges();
        source.sendSuccess(() -> Component.literal("Clicked " + slot + " in " + player.containerMenu.getClass().getSimpleName()
                + ", carried: " + player.containerMenu.getCarried()), false);
        return 1;
    }
}
