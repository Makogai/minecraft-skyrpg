package com.skyrpg.chat;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.ChatVisiblity;

/**
 * Styled {@code /msg}, {@code /tell} and {@code /w} (all three run vanilla's MsgCommand, whose sending this mod
 * replaces), plus {@code /r}: "From Name: hi" for the receiver, "To Name: hi" for the sender, in a colour of their
 * own. Emotes, links and item showcases work in whispers too. Like vanilla, whispers aren't written to the log.
 */
public final class Whispers {
    /** Who /r answers: the last player you whispered with, either way. */
    private static final Map<UUID, UUID> LAST_PARTNER = new ConcurrentHashMap<>();

    private Whispers() {
    }

    /** Sends a whisper; false if it couldn't be styled, so vanilla sends it instead. */
    public static boolean send(CommandSourceStack source, Collection<ServerPlayer> targets, String raw) {
        try {
            ServerPlayer from = source.getPlayer();
            String fromName = from != null ? from.getGameProfile().name() : source.getTextName();
            Component fromComponent = from != null ? Profiles.name(from) : Component.literal(fromName).withColor(ChatColors.SOFT);
            Component body = ChatBody.build(from, raw, Style.EMPTY.withColor(ChatColors.SOFT));
            for (ServerPlayer target : targets) {
                String targetName = target.getGameProfile().name();
                Component out = ChatLines.line(ChatLines.WHISPER_OUT, header("To ", Profiles.name(target)), body, targetName);
                Component in = ChatLines.line(ChatLines.WHISPER_IN, header("From ", fromComponent), body, fromName);
                source.sendSystemMessage(out);
                if (target.getChatVisibility() == ChatVisiblity.FULL) {
                    target.sendSystemMessage(in);
                }
                if (from != null) {
                    LAST_PARTNER.put(from.getUUID(), target.getUUID());
                    LAST_PARTNER.put(target.getUUID(), from.getUUID());
                }
            }
            return true;
        } catch (RuntimeException e) {
            SkyRpgChat.LOGGER.error("Couldn't style a whisper; sending it the vanilla way", e);
            return false;
        }
    }

    private static Component header(String direction, Component who) {
        MutableComponent header = Component.empty()
                .append(ChatIcons.lead(ChatIcons.ENVELOPE))
                .append(Component.literal(direction).withColor(ChatColors.WHISPER))
                .append(who)
                .append(Component.literal(": ").withColor(ChatColors.MUTED));
        return header;
    }

    /** The player /r would answer, or null. */
    public static UUID partner(ServerPlayer player) {
        return LAST_PARTNER.get(player.getUUID());
    }

    static void forget(ServerPlayer player) {
        LAST_PARTNER.remove(player.getUUID());
    }
}
