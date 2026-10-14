package com.skyrpg.enchants;

import com.skyrpg.core.items.GearEffects;
import com.skyrpg.core.stats.Stats;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.BiConsumer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * Where enchants count: armour in its own slot, and the item in the main hand when it isn't armour (so a sword's
 * enchants work while you hold it). Their stats are the "Enchants" stat source; their gear effects add to everything
 * else's (see core's {@link GearEffects}), so an enchant can switch on any mod's effect by data.
 */
public final class EnchantSources {
    private static final EquipmentSlot[] ARMOR = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};

    private EnchantSources() {
    }

    static void init() {
        Stats.registerSource(new Stats.Source() {
            @Override
            public String name() {
                return "Enchants";
            }

            @Override
            public void contribute(ServerPlayer player, Stats.Contributor out) {
                forEachActive(player, (def, level) -> def.stats().keySet().forEach(stat -> out.add(stat, def.stat(stat, level))));
            }
        });
        GearEffects.addSource(player -> {
            Map<Identifier, Double> effects = new LinkedHashMap<>();
            forEachActive(player, (def, level) -> def.effects().keySet().forEach(effect -> effects.merge(effect, def.effect(effect, level), Double::sum)));
            return effects;
        });
        // Stats change the moment enchanted gear goes on or off (or into the hand), not on the next periodic update.
        ServerEntityEvents.EQUIPMENT_CHANGE.register((entity, slot, from, to) -> {
            if (entity instanceof ServerPlayer player && (from.has(EnchantContent.ENCHANTS) || to.has(EnchantContent.ENCHANTS))) {
                Stats.refresh(player);
            }
        });
    }

    /** Every enchant that counts right now, with its level (an item's enchants only where they belong on it). */
    public static void forEachActive(Player player, BiConsumer<Enchant, Integer> action) {
        for (EquipmentSlot slot : ARMOR) {
            ItemStack stack = player.getItemBySlot(slot);
            if (Category.armorSlot(stack) == slot) {
                forEach(stack, action);
            }
        }
        ItemStack held = player.getMainHandItem();
        if (Category.armorSlot(held) == null) {
            forEach(held, action);
        }
    }

    private static void forEach(ItemStack stack, BiConsumer<Enchant, Integer> action) {
        if (stack.isEmpty() || EnchantContent.isTome(stack)) {
            return;
        }
        EnchantContent.enchants(stack).levels().forEach((id, level) -> {
            Enchant def = Enchants.get(id);
            if (def != null && def.appliesTo(stack)) {
                action.accept(def, Math.min(level, def.maxLevel()));
            }
        });
    }

    /** The held item's level of an enchant (0 if it isn't a held weapon or tool with it). */
    public static int held(Player player, Identifier enchant) {
        ItemStack held = player.getMainHandItem();
        return Category.armorSlot(held) != null ? 0 : activeLevel(held, enchant);
    }

    /** This item's level of an enchant, if it belongs on it (0 otherwise, and for tomes). */
    public static int activeLevel(ItemStack stack, Identifier enchant) {
        if (stack.isEmpty() || EnchantContent.isTome(stack)) {
            return 0;
        }
        int level = EnchantContent.enchants(stack).level(enchant);
        Enchant def = level > 0 ? Enchants.get(enchant) : null;
        return def != null && def.appliesTo(stack) ? Math.min(level, def.maxLevel()) : 0;
    }

    /** The armour piece in this slot's level of an enchant. */
    public static int worn(Player player, EquipmentSlot slot, Identifier enchant) {
        ItemStack stack = player.getItemBySlot(slot);
        return Category.armorSlot(stack) == slot ? activeLevel(stack, enchant) : 0;
    }

    /** The enchant's named value summed over every armour piece that has it (each at its own level). */
    public static double wornTotal(Player player, Identifier enchant, String value) {
        Enchant def = Enchants.get(enchant);
        if (def == null) {
            return 0;
        }
        double total = 0;
        for (EquipmentSlot slot : ARMOR) {
            total += def.value(value, worn(player, slot, enchant));
        }
        return total;
    }
}
