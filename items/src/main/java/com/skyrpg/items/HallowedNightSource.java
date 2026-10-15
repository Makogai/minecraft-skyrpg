package com.skyrpg.items;

import com.skyrpg.core.stats.StatTypes;
import com.skyrpg.core.stats.Stats;
import com.skyrpg.core.world.BloodMoon;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;

/**
 * The Hallowed Harvest set's Witching Hour: with all four pieces on, after dark in the overworld it adds more Farming
 * Fortune, Strength and Crit Damage, twice as much under a Blood Moon. (The always-on part is in the set's definition.)
 */
final class HallowedNightSource implements Stats.Source {
    private static final Identifier SET = SkyRpgItems.id("hallowed");
    private static final double FORTUNE = 60;
    private static final double STRENGTH = 40;
    private static final double CRIT_DAMAGE = 30;

    @Override
    public String name() {
        return "Witching Hour";
    }

    @Override
    public void contribute(ServerPlayer player, Stats.Contributor out) {
        if (player.level().dimension() != Level.OVERWORLD || SkyItems.wornPieces(player).getOrDefault(SET, 0) < 4) {
            return;
        }
        double scale = BloodMoon.active() ? 2 : player.level().isDarkOutside() ? 1 : 0;
        if (scale > 0) {
            out.add(StatTypes.FARMING_FORTUNE, FORTUNE * scale);
            out.add(StatTypes.STRENGTH, STRENGTH * scale);
            out.add(StatTypes.CRIT_DAMAGE, CRIT_DAMAGE * scale);
        }
    }
}
