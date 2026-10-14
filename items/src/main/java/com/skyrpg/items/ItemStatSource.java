package com.skyrpg.items;

import com.skyrpg.core.stats.StatType;
import com.skyrpg.core.stats.StatTypes;
import com.skyrpg.core.stats.Stats;
import com.skyrpg.items.accessory.AccessorySlot;
import java.util.Map;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;

/**
 * Stats from what the player wears and holds: armor pieces in their slots, the held item when it isn't
 * armor or an accessory, and set bonuses once enough pieces of a set are worn. Worn accessories have their
 * own source ({@code AccessoryStatSource}).
 */
final class ItemStatSource implements Stats.Source {
    @Override
    public String name() {
        return "Items";
    }

    @Override
    public void contribute(ServerPlayer player, Stats.Contributor out) {
        for (EquipmentSlot slot : SkyItems.ARMOR_SLOTS) {
            ItemDefinition def = SkyItems.definition(player.getItemBySlot(slot));
            // Armour only counts in its own slot (a helmet held in the hand gives nothing).
            if (def != null && SkyItems.armorSlot(def.item()) == slot) {
                add(def.stats(), out);
            }
        }
        // Same for accessories: a pendant only counts in its accessory slot.
        ItemDefinition held = SkyItems.definition(player.getMainHandItem());
        if (held != null && SkyItems.armorSlot(held.item()) == null && AccessorySlot.of(player.getMainHandItem()) == null) {
            add(held.stats(), out);
        }
        SkyItems.wornPieces(player).forEach((setId, count) -> {
            ItemSet set = ItemRegistry.set(setId);
            if (set != null && count >= set.pieces()) {
                add(set.bonusStats(), out);
            }
        });
    }

    private static void add(Map<StatType, Double> stats, Stats.Contributor out) {
        stats.forEach((stat, value) -> {
            // Weapon damage is applied through the item's attack damage attribute, not as a player stat.
            if (StatTypes.isPlayerStat(stat)) {
                out.add(stat, value);
            }
        });
    }
}
