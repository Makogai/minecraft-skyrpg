package com.skyrpg.hub;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.skyrpg.core.combat.Warmups;
import java.nio.file.Path;
import java.util.Locale;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.LevelData;

/** /hub, /survival, and admin /hubadmin save|rebuild|setspawn. */
final class HubCommands {
    private HubCommands() {
    }

    static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("hub").executes(ctx -> {
            ServerPlayer player = ctx.getSource().getPlayerOrException();
            // Refused in combat, otherwise a stand-still warm-up (see Warmups in core); /hub again cancels it.
            return Warmups.request(player, "the Hub", true, () -> {
                player.sendSystemMessage(Component.literal("Warping you to the Hub...").withStyle(ChatFormatting.GRAY));
                Hub.sendToHub(player);
            }) ? 1 : 0;
        }));

        dispatcher.register(Commands.literal("survival").executes(ctx -> {
            ServerPlayer player = ctx.getSource().getPlayerOrException();
            if (!Hub.isHub(player.level())) {
                ctx.getSource().sendFailure(Component.literal("You're already in the survival world. Use /hub to go back."));
                return 0;
            }
            return Warmups.request(player, "the survival world", true, () -> {
                player.sendSystemMessage(Component.literal("Sending you to the survival world...").withStyle(ChatFormatting.GRAY));
                Hub.sendToSurvival(player);
            }) ? 1 : 0;
        }));

        dispatcher.register(Commands.literal("hubadmin")
                .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .then(Commands.literal("save").executes(ctx -> {
                    Path file = HubBuild.save(ctx.getSource().getServer(), hubLevel(ctx.getSource()));
                    ctx.getSource().sendSuccess(() -> Component.literal("Saved hub build to " + file.toAbsolutePath()), true);
                    return 1;
                }))
                .then(Commands.literal("rebuild").executes(ctx -> {
                    ServerLevel hub = hubLevel(ctx.getSource());
                    if (!HubBuild.place(ctx.getSource().getServer(), hub, true)) {
                        ctx.getSource().sendFailure(Component.literal("There is no saved hub build yet."));
                        return 0;
                    }
                    hub.setAttached(HubBuild.PLACED_VERSION, HubConfig.get().buildVersion());
                    ctx.getSource().sendSuccess(() -> Component.literal("Rebuilt the hub"), true);
                    return 1;
                }))
                .then(Commands.literal("setspawn").executes(ctx -> setSpawn(ctx.getSource()))));
    }

    private static ServerLevel hubLevel(CommandSourceStack source) throws CommandSyntaxException {
        ServerLevel hub = Hub.level(source.getServer());
        if (hub == null) {
            throw new CommandSyntaxException(null, Component.literal("The hub dimension is missing"));
        }
        return hub;
    }

    /** Moves the hub spawn here for this session and prints the hub.json line that makes it permanent. */
    private static int setSpawn(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        if (!Hub.isHub(player.level())) {
            source.sendFailure(Component.literal("Stand in the hub to set its spawn."));
            return 0;
        }
        source.getServer().setRespawnData(LevelData.RespawnData.of(Hub.LEVEL, player.blockPosition(), player.getYRot(), 0));
        String json = String.format(Locale.ROOT, "\"spawn\": { \"x\": %.1f, \"y\": %.1f, \"z\": %.1f, \"yaw\": %.1f, \"pitch\": 0.0 }",
                player.getX(), player.getY(), player.getZ(), player.getYRot());
        source.sendSuccess(() -> Component.literal("Hub spawn set until restart. Put this in hub.json to keep it: " + json), true);
        return 1;
    }
}
