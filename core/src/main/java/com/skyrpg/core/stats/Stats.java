package com.skyrpg.core.stats;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

/**
 * The stats API every mod uses. Mods add stats through {@link Source}s (armor, skill levels, enchants...)
 * and read totals with {@link #get}. The {@code skyrpg_stats} mod installs the {@link Backend} that adds
 * everything up and makes stats do things (health, damage, speed, fortune, mana). Without it, {@link #get}
 * just returns base values, so no mod has to depend on the stats mod directly.
 */
public final class Stats {
    /** Receives a source's contributions. */
    @FunctionalInterface
    public interface Contributor {
        void add(StatType stat, double value);
    }

    /** Something that gives a player stats, e.g. "Armor" or "Skills". Called on the server whenever stats are recomputed. */
    public interface Source {
        /** Shown in stat breakdowns: "Armor +40". */
        String name();

        void contribute(ServerPlayer player, Contributor out);
    }

    /** Implemented by the stats mod. Works on both sides (players' stats are synced to their client). */
    public interface Backend {
        double get(Player player, StatType stat);

        double mana(Player player);

        boolean spendMana(ServerPlayer player, double amount);

        /** Recompute now instead of on the next periodic update, e.g. right after equipping something. */
        void refresh(ServerPlayer player);
    }

    private static final List<Source> SOURCES = new CopyOnWriteArrayList<>();
    private static Backend backend;

    private Stats() {
    }

    public static void registerSource(Source source) {
        SOURCES.add(source);
    }

    public static List<Source> sources() {
        return SOURCES;
    }

    public static void install(Backend installed) {
        backend = installed;
    }

    /** Whether the stats mod is loaded (stats actually do something). */
    public static boolean available() {
        return backend != null;
    }

    /** The player's total for this stat, including its base value. */
    public static double get(Player player, StatType stat) {
        return backend == null ? stat.base() : backend.get(player, stat);
    }

    public static double mana(Player player) {
        return backend == null ? 0 : backend.mana(player);
    }

    /** Takes mana if the player has enough. Without the stats mod, abilities are free. */
    public static boolean spendMana(ServerPlayer player, double amount) {
        return backend == null || backend.spendMana(player, amount);
    }

    public static void refresh(ServerPlayer player) {
        if (backend != null) {
            backend.refresh(player);
        }
    }
}
