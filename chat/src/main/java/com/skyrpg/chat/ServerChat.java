package com.skyrpg.chat;

import java.time.Instant;
import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.message.v1.ServerMessageEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.ChatType;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.PlayerChatMessage;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.ChatVisiblity;

/**
 * Player chat and {@code /me}: the vanilla broadcast is cancelled and a styled system line goes out instead (no
 * "Not Secure" marks, no report buttons). The server log still gets vanilla's own line, written by vanilla's own
 * logger ({@code [Not Secure] <Name> message}), because Claude's tools and the "@claude" monitor read chat from it.
 *
 * <p>The listeners run in a late phase, so other mods' ALLOW_CHAT_MESSAGE listeners (the events mod's ".join") still
 * see every message first and can swallow it.
 */
public final class ServerChat {
    private static final Identifier LATE = SkyRpgChat.id("late");

    private ServerChat() {
    }

    static void init() {
        ServerMessageEvents.ALLOW_CHAT_MESSAGE.addPhaseOrdering(Event.DEFAULT_PHASE, LATE);
        ServerMessageEvents.ALLOW_CHAT_MESSAGE.register(LATE, ServerChat::onChat);
        ServerMessageEvents.ALLOW_COMMAND_MESSAGE.addPhaseOrdering(Event.DEFAULT_PHASE, LATE);
        ServerMessageEvents.ALLOW_COMMAND_MESSAGE.register(LATE, ServerChat::onCommandMessage);
        ServerMessageEvents.ALLOW_GAME_MESSAGE.addPhaseOrdering(Event.DEFAULT_PHASE, LATE);
        ServerMessageEvents.ALLOW_GAME_MESSAGE.register(LATE, SystemLines::onGameMessage);
    }

    /** Plain chat. Other chat types that reach this event (a player's /say, /me) are left alone here. */
    private static boolean onChat(PlayerChatMessage message, ServerPlayer sender, ChatType.Bound bound) {
        if (!bound.chatType().is(ChatType.CHAT)) {
            return true;
        }
        Component line;
        try {
            line = playerLine(sender, message.signedContent());
        } catch (RuntimeException e) {
            SkyRpgChat.LOGGER.error("Couldn't format a chat message; sending it the vanilla way", e);
            return true;
        }
        MinecraftServer server = sender.level().getServer();
        logLikeVanilla(server, message, bound);
        sendToChatters(server, line);
        ServerMessageEvents.CHAT_MESSAGE.invoker().onChatMessage(message, sender, bound);
        return false;
    }

    /** {@code /me} from a player becomes "✱ Name waves". The console's /say and /me stay vanilla. */
    private static boolean onCommandMessage(PlayerChatMessage message, CommandSourceStack source, ChatType.Bound bound) {
        ServerPlayer sender = source.getPlayer();
        if (sender == null || !bound.chatType().is(ChatType.EMOTE_COMMAND)) {
            return true;
        }
        Component line;
        try {
            line = emoteLine(sender, message.signedContent());
        } catch (RuntimeException e) {
            SkyRpgChat.LOGGER.error("Couldn't format /me; sending it the vanilla way", e);
            return true;
        }
        MinecraftServer server = source.getServer();
        logLikeVanilla(server, message, bound);
        sendToChatters(server, line);
        ServerMessageEvents.COMMAND_MESSAGE.invoker().onCommandMessage(message, source, bound);
        return false;
    }

    /**
     * Exactly what vanilla's PlayerList.broadcastChatMessage logs, through MinecraftServer's own logger: "<Name> text",
     * tagged "[Not Secure]" when the message isn't signed (always, on an offline-mode server).
     */
    static void logLikeVanilla(MinecraftServer server, PlayerChatMessage message, ChatType.Bound bound) {
        boolean trusted = message.hasSignature() && !message.hasExpiredServer(Instant.now());
        server.logChatMessage(message.decoratedContent(), bound, trusted ? null : "Not Secure");
    }

    /** To everyone whose chat setting shows player chat (not "Commands Only" or "Hidden"), like vanilla. */
    static void sendToChatters(MinecraftServer server, Component line) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (player.getChatVisibility() == ChatVisiblity.FULL) {
                player.sendSystemMessage(line);
            }
        }
    }

    /** "[42] [Founder] Name: message" */
    public static Component playerLine(ServerPlayer sender, String raw) {
        int level = Profiles.level(sender);
        MutableComponent header = Component.empty()
                .append(Profiles.badge(level)).append(Component.literal(" "))
                .append(Profiles.titlePrefix(sender))
                .append(Profiles.name(sender, level))
                .append(Component.literal(": ").withColor(ChatColors.MUTED));
        Component body = ChatBody.build(sender, raw, Style.EMPTY.withColor(ChatFormatting.WHITE));
        return ChatLines.line(ChatLines.PLAYER, header, body, sender.getGameProfile().name());
    }

    /** "✱ Name waves" */
    public static Component emoteLine(ServerPlayer sender, String raw) {
        MutableComponent header = Component.empty()
                .append(Component.literal("✱ ").withColor(ChatColors.AQUA))
                .append(Profiles.name(sender))
                .append(Component.literal(" "));
        Component body = ChatBody.build(sender, raw, Style.EMPTY.withColor(ChatColors.AQUA).withItalic(true));
        return ChatLines.line(ChatLines.EMOTE, header, body, sender.getGameProfile().name());
    }
}
