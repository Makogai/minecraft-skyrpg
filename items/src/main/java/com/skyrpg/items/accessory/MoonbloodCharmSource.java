package com.skyrpg.items.accessory;

import com.skyrpg.core.stats.StatTypes;
import com.skyrpg.core.stats.Stats;
import com.skyrpg.core.world.BloodMoon;
import com.skyrpg.items.ModItems;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;

/**
 * The Moonblood Charm wakes after dark: extra Strength and Crit Damage at night in the overworld, a lot more during a Blood
 * Moon. (Its base stats are in its item definition and always count.)
 */
final class MoonbloodCharmSource implements Stats.Source {
    private static final double NIGHT_STRENGTH = 12;
    private static final double NIGHT_CRIT_DAMAGE = 15;
    private static final double MOON_STRENGTH = 30;
    private static final double MOON_CRIT_DAMAGE = 40;

    @Override
    public String name() {
        return "Moonblood Charm";
    }

    @Override
    public void contribute(ServerPlayer player, Stats.Contributor out) {
        if (!Accessories.worn(player, AccessorySlot.AMULET).is(ModItems.MOONBLOOD_CHARM) || player.level().dimension() != Level.OVERWORLD) {
            return;
        }
        if (BloodMoon.active()) {
            out.add(StatTypes.STRENGTH, MOON_STRENGTH);
            out.add(StatTypes.CRIT_DAMAGE, MOON_CRIT_DAMAGE);
        } else if (player.level().isDarkOutside()) {
            out.add(StatTypes.STRENGTH, NIGHT_STRENGTH);
            out.add(StatTypes.CRIT_DAMAGE, NIGHT_CRIT_DAMAGE);
        }
    }
}
