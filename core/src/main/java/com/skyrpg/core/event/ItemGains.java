package com.skyrpg.core.event;

import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.EventFactory;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/**
 * A player gained items from the world: mods that put gathered items straight into the inventory (mining auto-pickup,
 * fishing catches, farming and foraging helpers) call {@link #give}, and the progress mod's collections listen to
 * {@link #GAINED} (along with normal item pickups). Core knows nothing about the listeners.
 */
public final class ItemGains {
    /** Where the items came from. */
    public enum Source {
        PICKUP, MINING, FARMING, FORAGING, FISHING, COMBAT, OTHER
    }

    @FunctionalInterface
    public interface Gained {
        /** {@code stack} is a copy holding exactly what the player received. */
        void onGained(ServerPlayer player, ItemStack stack, Source source);
    }

    public static final Event<Gained> GAINED = EventFactory.createArrayBacked(Gained.class, listeners -> (player, stack, source) -> {
        for (Gained listener : listeners) {
            listener.onGained(player, stack, source);
        }
    });

    private ItemGains() {
    }

    /**
     * Puts the stack into the inventory like {@code Inventory.add} (what didn't fit stays in {@code stack}) and fires
     * {@link #GAINED} for what went in.
     */
    public static void give(ServerPlayer player, ItemStack stack, Source source) {
        int before = stack.getCount();
        ItemStack copy = stack.copy();
        player.getInventory().add(stack);
        int taken = before - stack.getCount();
        if (taken > 0) {
            copy.setCount(taken);
            GAINED.invoker().onGained(player, copy, source);
        }
    }
}
