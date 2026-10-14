package com.skyrpg.economy;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * Which shop or bank each player has open, and where they stood when it opened. Requests from the
 * client only count while the player is still there, so a screen kept open (or a forged packet) can't
 * trade from across the map.
 */
public final class Sessions {
    private static final double RANGE = 10;

    private record Session(Identifier counter, ResourceKey<Level> dimension, Vec3 pos) {
    }

    private static final Map<UUID, Session> OPEN = new ConcurrentHashMap<>();

    private Sessions() {
    }

    public static void open(ServerPlayer player, Identifier counter) {
        OPEN.put(player.getUUID(), new Session(counter, player.level().dimension(), player.position()));
    }

    /** True if this player opened this counter and hasn't walked away from it. */
    public static boolean isAt(ServerPlayer player, Identifier counter) {
        Session session = OPEN.get(player.getUUID());
        return session != null && session.counter().equals(counter) && session.dimension() == player.level().dimension()
                && session.pos().closerThan(player.position(), RANGE);
    }

    public static void forget(UUID player) {
        OPEN.remove(player);
    }
}
