package com.skyrpg.items.accessory;

import com.skyrpg.core.stats.StatTypes;
import com.skyrpg.core.stats.Stats;
import com.skyrpg.items.ItemDefinition;
import com.skyrpg.items.SkyItems;
import net.minecraft.server.level.ServerPlayer;

/** Stats from worn accessories: each one's item stats, like armour in its slot. */
final class AccessoryStatSource implements Stats.Source {
    @Override
    public String name() {
        return "Accessories";
    }

    @Override
    public void contribute(ServerPlayer player, Stats.Contributor out) {
        for (AccessorySlot slot : AccessorySlot.values()) {
            ItemDefinition def = SkyItems.definition(Accessories.worn(player, slot));
            if (def != null) {
                def.stats().forEach((stat, value) -> {
                    if (StatTypes.isPlayerStat(stat)) {
                        out.add(stat, value);
                    }
                });
            }
        }
    }
}
