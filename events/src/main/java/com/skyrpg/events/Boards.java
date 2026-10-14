package com.skyrpg.events;

import com.skyrpg.events.net.EventPayloads;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * The /board sidebars that are up. A board for everyone is remembered and shown to players who join
 * later; a board for chosen players sits on top of it until it's hidden again.
 */
final class Boards {
    /** {@code board} builds the board per viewer, so text can include their name. */
    private record Shown(Function<ServerPlayer, Board> board, long hideAt) {
        static Shown of(Function<ServerPlayer, Board> board, int seconds) {
            return new Shown(board, seconds > 0 ? System.currentTimeMillis() + seconds * 1000L : Long.MAX_VALUE);
        }
    }

    private static Shown everyone;
    private static final Map<UUID, Shown> PERSONAL = new HashMap<>();
    private static MinecraftServer server;

    private Boards() {
    }

    /** @param players null = everyone, now and later */
    static void show(MinecraftServer onServer, Collection<ServerPlayer> players, Function<ServerPlayer, Board> board, int seconds) {
        server = onServer;
        Shown shown = Shown.of(board, seconds);
        if (players == null) {
            everyone = shown;
            PERSONAL.clear();
            onServer.getPlayerList().getPlayers().forEach(Boards::send);
        } else {
            for (ServerPlayer player : players) {
                PERSONAL.put(player.getUUID(), shown);
                send(player);
            }
        }
    }

    /** @param players null = hide every board for everyone; otherwise those players fall back to the everyone-board, if any */
    static void hide(MinecraftServer onServer, Collection<ServerPlayer> players) {
        server = onServer;
        if (players == null) {
            everyone = null;
            PERSONAL.clear();
            onServer.getPlayerList().getPlayers().forEach(Boards::send);
        } else {
            for (ServerPlayer player : players) {
                PERSONAL.remove(player.getUUID());
                send(player);
            }
        }
    }

    static void onJoin(ServerPlayer player) {
        if (everyone != null || PERSONAL.containsKey(player.getUUID())) {
            send(player);
        }
    }

    static void tick(long now) {
        if (server == null) {
            return;
        }
        if (everyone != null && now >= everyone.hideAt()) {
            everyone = null;
            server.getPlayerList().getPlayers().stream().filter(player -> !PERSONAL.containsKey(player.getUUID())).forEach(Boards::send);
        }
        List<UUID> expired = PERSONAL.entrySet().stream().filter(entry -> now >= entry.getValue().hideAt()).map(Map.Entry::getKey).toList();
        for (UUID id : expired) {
            PERSONAL.remove(id);
            ServerPlayer player = server.getPlayerList().getPlayer(id);
            if (player != null) {
                send(player);
            }
        }
    }

    static void clear() {
        everyone = null;
        PERSONAL.clear();
        server = null;
    }

    private static void send(ServerPlayer player) {
        Shown shown = PERSONAL.getOrDefault(player.getUUID(), everyone);
        if (shown == null) {
            EventManager.send(player, EventPayloads.ShowBoard.hidden());
            return;
        }
        Board board;
        try {
            board = shown.board().apply(player);
        } catch (RuntimeException e) {
            SkyRpgEvents.LOGGER.warn("Couldn't build the board for {}: {}", player.getScoreboardName(), e.getMessage());
            return;
        }
        EventManager.send(player, new EventPayloads.ShowBoard(true, board.title(), board.color(),
                board.lines().stream().map(line -> new EventPayloads.BoardLine(line.left(), line.right())).toList(), board.footer()));
    }
}
