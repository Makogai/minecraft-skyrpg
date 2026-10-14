package com.skyrpg.devtools;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/** Dev-only commands that let Claude's MCP tools inspect the world. Only loaded in ./gradlew :dev runs. */
public class SkyRpgDevtools implements ModInitializer {
    public static final String MOD_ID = "skyrpg_devtools";

    @Override
    public void onInitialize() {
        PayloadTypeRegistry.clientboundPlay().register(ScreenshotPayload.TYPE, ScreenshotPayload.CODEC);
        CommandRegistrationCallback.EVENT.register((dispatcher, context, selection) -> {
            ScanCommand.register(dispatcher);
            RespawnCommand.register(dispatcher);
            // /devtools screenshot <player> [page]: page is a SkyRPG menu page ("profile", "skills"), or "close". The page is the
            // rest of the line, so "set-chat-hello [item] <3" can carry spaces and brackets.
            dispatcher.register(Commands.literal("devtools")
                    .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                    .then(Commands.literal("screenshot")
                            .then(Commands.argument("player", EntityArgument.player())
                                    .executes(ctx -> screenshot(ctx.getSource(), EntityArgument.getPlayer(ctx, "player"), ""))
                                    .then(Commands.argument("page", StringArgumentType.greedyString())
                                            .executes(ctx -> screenshot(ctx.getSource(), EntityArgument.getPlayer(ctx, "player"),
                                                    StringArgumentType.getString(ctx, "page"))))))
                    // /devtools guiscale <player> <n>: the dev client's GUI scale (0 = auto), for checking UI at every scale.
                    .then(Commands.literal("guiscale")
                            .then(Commands.argument("player", EntityArgument.player())
                                    .then(Commands.argument("scale", IntegerArgumentType.integer(0, 8))
                                            .executes(ctx -> screenshot(ctx.getSource(), EntityArgument.getPlayer(ctx, "player"),
                                                    "set:guiscale:" + IntegerArgumentType.getInteger(ctx, "scale"))))))
                    // /devtools window <player> <width> <height>: resizes the dev client's window.
                    .then(Commands.literal("window")
                            .then(Commands.argument("player", EntityArgument.player())
                                    .then(Commands.argument("width", IntegerArgumentType.integer(320, 7680))
                                            .then(Commands.argument("height", IntegerArgumentType.integer(240, 4320))
                                                    .executes(ctx -> screenshot(ctx.getSource(), EntityArgument.getPlayer(ctx, "player"),
                                                            "set:window:" + IntegerArgumentType.getInteger(ctx, "width") + "x"
                                                                    + IntegerArgumentType.getInteger(ctx, "height"))))))));
        });
    }

    private static int screenshot(net.minecraft.commands.CommandSourceStack source, ServerPlayer player, String page) {
        if (!ServerPlayNetworking.canSend(player, ScreenshotPayload.TYPE)) {
            source.sendFailure(Component.literal(player.getScoreboardName() + " isn't running the dev client"));
            return 0;
        }
        ServerPlayNetworking.send(player, new ScreenshotPayload(page));
        String what = page.startsWith("set:") ? "to set " + page.substring(4) : "for a screenshot";
        source.sendSuccess(() -> Component.literal("Asked " + player.getScoreboardName() + " " + what), false);
        return 1;
    }
}
