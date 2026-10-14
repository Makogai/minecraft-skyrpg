package com.skyrpg.chat;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.stats.Stats;

/**
 * Themed system lines: "» Name joined Skyhaven", "« Name left Skyhaven", a welcome for first-timers, "☠" before death
 * messages and an icon before advancements. Vanilla's text is kept for deaths and advancements. The server log keeps
 * vanilla's original line ("System chat: Name joined the game"), so anything reading the log sees no change.
 */
public final class SystemLines {
    private static final String REALM = "Skyhaven";

    private SystemLines() {
    }

    /** ALLOW_GAME_MESSAGE: swaps vanilla's join/leave/death/advancement broadcasts for styled ones. */
    static boolean onGameMessage(MinecraftServer server, Component message, boolean overlay) {
        if (overlay || !(message.getContents() instanceof TranslatableContents translatable)) {
            return true;
        }
        String key = translatable.getKey();
        try {
            if (key.equals("multiplayer.player.joined") || key.equals("multiplayer.player.joined.renamed")) {
                // The styled line goes out from the JOIN event, once the player is in the player list.
                server.sendSystemMessage(message);
                return false;
            }
            if (key.equals("multiplayer.player.left")) {
                ServerPlayer player = playerIn(server, translatable);
                Component line = player != null ? leaveLine(player) : null;
                if (line == null) {
                    return true;
                }
                server.sendSystemMessage(message);
                broadcast(server, ChatLines.single(ChatLines.SOCIAL, line, player.getGameProfile().name()));
                return false;
            }
            if (key.startsWith("death.")) {
                ServerPlayer player = playerIn(server, translatable);
                MutableComponent line = Component.empty()
                        .append(ChatIcons.lead(ChatIcons.SKULL))
                        .append(Component.empty().withColor(ChatColors.SOFT).append(message));
                server.sendSystemMessage(message);
                broadcast(server, ChatLines.single(ChatLines.DEATH, line, player == null ? null : player.getGameProfile().name()));
                return false;
            }
            if (key.startsWith("chat.type.advancement.")) {
                ServerPlayer player = playerIn(server, translatable);
                String kind = key.substring("chat.type.advancement.".length());
                String icon = switch (kind) {
                    case "challenge" -> "✯ ";
                    case "goal" -> "✪ ";
                    default -> "✦ ";
                };
                int color = switch (kind) {
                    case "challenge" -> ChatColors.PURPLE;
                    case "goal" -> ChatColors.AQUA;
                    default -> ChatColors.GREEN;
                };
                MutableComponent line = Component.empty()
                        .append(Component.literal(icon).withColor(color))
                        .append(Component.empty().withColor(ChatColors.SOFT).append(message));
                server.sendSystemMessage(message);
                broadcast(server, ChatLines.single(ChatLines.ADVANCEMENT, line, player == null ? null : player.getGameProfile().name()));
                return false;
            }
        } catch (RuntimeException e) {
            SkyRpgChat.LOGGER.error("Couldn't style a system message; sending it the vanilla way", e);
            return true;
        }
        return true;
    }

    /** JOIN: the styled join line (or the first-join welcome) to everyone, now that the player is listed. */
    static void onJoin(ServerGamePacketListenerImpl handler, MinecraftServer server) {
        ServerPlayer player = handler.player;
        try {
            boolean firstJoin = player.getStats().getValue(Stats.CUSTOM.get(Stats.LEAVE_GAME)) == 0;
            String name = player.getGameProfile().name();
            if (firstJoin) {
                MutableComponent line = Component.empty()
                        .append(ChatIcons.lead(ChatIcons.WELCOME))
                        .append(Component.literal("Welcome ").withColor(ChatColors.ACCENT))
                        .append(Profiles.name(player))
                        .append(Component.literal(" to " + REALM + "!").withColor(ChatColors.ACCENT))
                        .append(Component.literal(" First visit").withColor(ChatColors.DIM));
                broadcast(server, ChatLines.single(ChatLines.SOCIAL, line, name));
                player.sendSystemMessage(welcomeCard(name));
            } else {
                MutableComponent line = Component.empty()
                        .append(ChatIcons.lead(ChatIcons.JOIN))
                        .append(Profiles.name(player))
                        .append(Component.literal(" joined " + REALM).withColor(ChatColors.MUTED));
                broadcast(server, ChatLines.single(ChatLines.SOCIAL, line, name));
            }
        } catch (RuntimeException e) {
            SkyRpgChat.LOGGER.error("Couldn't send the join line for {}", player.getGameProfile().name(), e);
            broadcast(server, Component.translatable("multiplayer.player.joined", player.getDisplayName())
                    .withStyle(net.minecraft.ChatFormatting.YELLOW));
        }
    }

    private static Component leaveLine(ServerPlayer player) {
        return Component.empty()
                .append(ChatIcons.lead(ChatIcons.LEAVE))
                .append(Profiles.name(player))
                .append(Component.literal(" left " + REALM).withColor(ChatColors.MUTED));
    }

    private static Component welcomeCard(String name) {
        return Component.empty()
                .append(Component.literal("✦ Welcome to " + REALM + ", " + name + "!").withColor(ChatColors.ACCENT))
                .append(Component.literal("\n  Press ").withColor(ChatColors.MUTED))
                .append(Component.literal("K").withColor(ChatColors.TEXT))
                .append(Component.literal(" for the SkyRPG menu. Show off gear with ").withColor(ChatColors.MUTED))
                .append(Component.literal("[item]").withColor(ChatColors.TEXT))
                .append(Component.literal(" in chat; ").withColor(ChatColors.MUTED))
                .append(Component.literal("/emotes").withColor(ChatColors.TEXT))
                .append(Component.literal(" lists the rest.").withColor(ChatColors.MUTED));
    }

    /**
     * The player a vanilla message is about: its first argument is their display name, whose insertion (shift-click
     * text) is their exact name.
     */
    private static ServerPlayer playerIn(MinecraftServer server, TranslatableContents translatable) {
        Object[] args = translatable.getArgs();
        if (args.length == 0 || !(args[0] instanceof Component name)) {
            return null;
        }
        String insertion = name.getStyle().getInsertion();
        ServerPlayer player = insertion != null ? server.getPlayerList().getPlayerByName(insertion) : null;
        return player != null ? player : server.getPlayerList().getPlayerByName(name.getString());
    }

    private static void broadcast(MinecraftServer server, Component line) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            player.sendSystemMessage(line);
        }
    }
}
