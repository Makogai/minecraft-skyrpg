package com.skyrpg.stats;

import com.skyrpg.core.stats.StatType;
import com.skyrpg.core.stats.Stats;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.server.level.ServerPlayer;

/**
 * Stat bonuses handed out by admins with /stats bonus, shown as the "Admin" source. Kept in memory only,
 * so they're gone after a server restart (meant for testing and events, not progression).
 */
final class AdminBonuses {
    private static final Map<UUID, Map<StatType, Double>> BONUSES = new HashMap<>();
    private static boolean registered;

    private AdminBonuses() {
    }

    static void register() {
        if (registered) {
            return;
        }
        registered = true;
        Stats.registerSource(new Stats.Source() {
            @Override
            public String name() {
                return "Admin";
            }

            @Override
            public void contribute(ServerPlayer player, Stats.Contributor out) {
                BONUSES.getOrDefault(player.getUUID(), Map.of()).forEach(out::add);
            }
        });
    }

    static void set(ServerPlayer player, StatType stat, double amount) {
        register();
        Map<StatType, Double> bonuses = BONUSES.computeIfAbsent(player.getUUID(), id -> new HashMap<>());
        if (amount == 0) {
            bonuses.remove(stat);
        } else {
            bonuses.put(stat, amount);
        }
        StatEngine.recompute(player);
    }

    static void clear(ServerPlayer player) {
        BONUSES.remove(player.getUUID());
        StatEngine.recompute(player);
    }
}
