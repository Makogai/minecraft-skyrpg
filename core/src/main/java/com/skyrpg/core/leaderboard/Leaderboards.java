package com.skyrpg.core.leaderboard;

import com.mojang.authlib.properties.Property;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * Server-side leaderboards: mods register a {@link Board} that ranks players (the brainrot collectors, ...) and the
 * hub shows them (the podium in Skyhaven). Call {@link #changed} when a board's ranking may have moved, so displays
 * refresh.
 */
public final class Leaderboards {
    /**
     * One player on a board: who, their skin (the "textures" property, so a mannequin can wear it even when they're
     * offline; empty if unknown) and the lines shown under them, best first.
     */
    public record Entry(UUID id, String name, String skin, String skinSignature, List<Component> lines) {
    }

    public interface Board {
        Identifier id();

        /** Shown above the podium. */
        Component title();

        /** A line under the title (what the ranking is by). */
        Component subtitle();

        /** The best {@code count} players, best first. */
        List<Entry> top(MinecraftServer server, int count);
    }

    private static final Map<Identifier, Board> BOARDS = new LinkedHashMap<>();
    private static final Map<Identifier, Integer> VERSIONS = new HashMap<>();

    private Leaderboards() {
    }

    public static void register(Board board) {
        BOARDS.put(board.id(), board);
    }

    public static Board get(Identifier id) {
        return BOARDS.get(id);
    }

    public static Collection<Board> all() {
        return BOARDS.values();
    }

    public static void changed(Identifier id) {
        VERSIONS.merge(id, 1, Integer::sum);
    }

    /** Goes up every time {@link #changed} is called for this board. */
    public static int version(Identifier id) {
        return VERSIONS.getOrDefault(id, 0);
    }

    /** The player's skin as {value, signature} ("" for each if they have none). */
    public static String[] skinOf(ServerPlayer player) {
        for (Property property : player.getGameProfile().properties().get("textures")) {
            return new String[]{property.value(), property.signature() == null ? "" : property.signature()};
        }
        return new String[]{"", ""};
    }
}
