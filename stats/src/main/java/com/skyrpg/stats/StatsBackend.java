package com.skyrpg.stats;

import com.skyrpg.core.stats.StatType;
import com.skyrpg.core.stats.Stats;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

/** Core's {@link Stats} calls end up here. Reads synced data, so it works on the client too. */
final class StatsBackend implements Stats.Backend {
    @Override
    public double get(Player player, StatType stat) {
        return PlayerStats.total(player, stat);
    }

    @Override
    public double mana(Player player) {
        return PlayerStats.mana(player);
    }

    @Override
    public boolean spendMana(ServerPlayer player, double amount) {
        double mana = PlayerStats.mana(player);
        if (amount > mana) {
            return false;
        }
        PlayerStats.setMana(player, mana - amount);
        return true;
    }

    @Override
    public void refresh(ServerPlayer player) {
        StatEngine.recompute(player);
    }
}
