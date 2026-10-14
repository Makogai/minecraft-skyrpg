package com.skyrpg.core.items;

import java.util.Map;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;

/**
 * Timed boosts for any mod: extra stats and {@link GearEffects} for a while, like an ability's burst or a meal. A boost
 * with the same key replaces the old one (so meals don't stack: they share a key). The {@code skyrpg_items} mod keeps
 * them (saved with the player) and counts them in stats and effects; without it, boosts do nothing.
 */
public final class Boosts {
    /** Gives the boost; {@code stats} are stat ids ("farming_fortune") to amounts. */
    @FunctionalInterface
    public interface Backend {
        void give(ServerPlayer player, String key, Component name, Map<String, Double> stats, Map<Identifier, Double> effects, int ticks);
    }

    private static Backend backend = (player, key, name, stats, effects, ticks) -> {
    };

    private Boosts() {
    }

    public static void install(Backend installed) {
        backend = installed;
    }

    public static void give(ServerPlayer player, String key, Component name, Map<String, Double> stats, Map<Identifier, Double> effects, int ticks) {
        backend.give(player, key, name, stats, effects, ticks);
    }
}
