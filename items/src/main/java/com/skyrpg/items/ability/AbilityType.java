package com.skyrpg.items.ability;

import com.skyrpg.items.ItemDefinition;
import net.minecraft.server.level.ServerPlayer;

/**
 * What an item ability does when used. Registered by type id in {@link Abilities}; an item's definition
 * picks the type and sets its name, mana cost, cooldown and params.
 */
@FunctionalInterface
public interface AbilityType {
    /** Runs the ability. Return false if it couldn't happen (then no mana is spent and no cooldown starts). */
    boolean use(ServerPlayer player, ItemDefinition.Ability ability);
}
