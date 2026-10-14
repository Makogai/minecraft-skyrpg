package com.skyrpg.helper;

import com.google.gson.JsonObject;
import com.mojang.brigadier.arguments.StringArgumentType;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.message.v1.ServerMessageEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * SkyHelper, the in-game assistant. "@skyhelper where can I sell this?" in chat gets a public answer, "/ask ..." a private
 * one. The answer comes from tools/skyhelper/helper.py, a small local service that asks an Ollama model with the wiki as
 * its knowledge (see {@link HelperService} and docs/design/skyhelper.md). This mod only adds commands and chat lines, so
 * players don't need it on their client.
 */
public class SkyRpgHelper implements ModInitializer {
    public static final String MOD_ID = "skyrpg_helper";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
    /** "@skyhelper ..." or "@helper ...", anywhere at the start of a chat line. */
    private static final Pattern MENTION = Pattern.compile("^\\s*@(sky)?helper\\b[\\s,:]*(.*)$", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

    private static final Map<UUID, Long> LAST_ASKED = new HashMap<>();
    private static final Set<UUID> WAITING = new HashSet<>();

    @Override
    public void onInitialize() {
        HelperConfig.load();
        ServerLifecycleEvents.SERVER_STARTED.register(HelperService::start);
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> HelperService.stop());
        ServerMessageEvents.CHAT_MESSAGE.register((message, sender, bound) -> {
            Matcher m = MENTION.matcher(message.signedContent());
            if (m.matches()) {
                MinecraftServer server = sender.level().getServer();
                server.execute(() -> ask(sender, m.group(2).trim(), true));
            }
        });
        CommandRegistrationCallback.EVENT.register((dispatcher, context, selection) -> {
            dispatcher.register(Commands.literal("ask")
                    .executes(ctx -> {
                        usage(ctx.getSource().getPlayerOrException());
                        return 1;
                    })
                    .then(Commands.argument("question", StringArgumentType.greedyString())
                            .executes(ctx -> {
                                ask(ctx.getSource().getPlayerOrException(), StringArgumentType.getString(ctx, "question"), false);
                                return 1;
                            })));
            dispatcher.register(Commands.literal("skyhelper")
                    .executes(ctx -> {
                        usage(ctx.getSource().getPlayerOrException());
                        return 1;
                    })
                    .then(Commands.literal("status")
                            .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                            .executes(ctx -> {
                                HelperService.status(ctx.getSource());
                                return 1;
                            }))
                    .then(Commands.literal("start")
                            .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                            .executes(ctx -> {
                                HelperService.start(ctx.getSource().getServer());
                                ctx.getSource().sendSuccess(() -> Component.literal("Starting SkyHelper's service..."), false);
                                return 1;
                            })));
        });
    }

    private static void usage(ServerPlayer player) {
        player.sendSystemMessage(HelperLines.line(HelperLines.note(
                "Ask me anything about SkyRPG: type \"@skyhelper where can I sell this?\" in chat, or /ask <question> to ask privately."),
                player.getGameProfile().name()));
    }

    static void ask(ServerPlayer player, String question, boolean pub) {
        if (question.isEmpty()) {
            usage(player);
            return;
        }
        if (!HelperConfig.enabled) {
            player.sendSystemMessage(HelperLines.line(HelperLines.note("I'm switched off on this server."), player.getGameProfile().name()));
            return;
        }
        UUID id = player.getUUID();
        if (WAITING.contains(id)) {
            player.sendOverlayMessage(Component.literal("SkyHelper is still answering your last question...").withStyle(ChatFormatting.GRAY));
            return;
        }
        long now = System.currentTimeMillis();
        long wait = LAST_ASKED.getOrDefault(id, 0L) + HelperConfig.cooldownSeconds * 1000L - now;
        if (wait > 0) {
            player.sendOverlayMessage(Component.literal("Ask again in " + (wait / 1000 + 1) + "s").withStyle(ChatFormatting.GRAY));
            return;
        }
        LAST_ASKED.put(id, now);
        WAITING.add(id);
        player.sendOverlayMessage(Component.literal("✦ SkyHelper is thinking...").withStyle(Style.EMPTY.withColor(HelperLines.VIOLET)));
        MinecraftServer server = player.level().getServer();
        String name = player.getGameProfile().name();
        JsonObject body = new JsonObject();
        body.addProperty("player", name);
        body.addProperty("question", question.length() > 400 ? question.substring(0, 400) : question);
        body.addProperty("public", pub);
        body.add("context", HelperContext.of(player));
        HelperService.ask(body).whenComplete((reply, error) -> server.execute(() -> {
            WAITING.remove(id);
            ServerPlayer asker = server.getPlayerList().getPlayer(id);
            if (error != null || reply == null || !reply.has("answer")) {
                if (error != null) {
                    LOGGER.warn("SkyHelper couldn't answer {}: {}", name, error.toString());
                }
                if (asker != null) {
                    asker.sendSystemMessage(HelperLines.line(HelperLines.note("I'm asleep right now, sorry! Try the wiki meanwhile: /wiki"), name));
                    HelperService.start(server);   // wake it for next time
                }
                return;
            }
            // command buttons go only to the asker: nobody can get SkyHelper to put a button in front of someone else
            Component line = HelperLines.line(HelperLines.answer(name, reply, pub, false), name);
            Component withActions = HelperLines.line(HelperLines.answer(name, reply, pub, true), name);
            if (pub) {
                for (ServerPlayer online : server.getPlayerList().getPlayers()) {
                    online.sendSystemMessage(online == asker ? withActions : line);
                }
                server.sendSystemMessage(line);   // the server log, like a broadcast
            } else if (asker != null) {
                asker.sendSystemMessage(withActions);
            }
        }));
    }
}
