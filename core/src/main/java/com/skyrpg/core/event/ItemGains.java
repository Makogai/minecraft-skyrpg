package com.skyrpg.core.event;

import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.EventFactory;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/**
 * A player gained items from the world: mods that put gathered items straight into the inventory (mining auto-pickup,
 * fishing catches, farming and foraging helpers) call {@link #give}, and the progress mod's collections listen to
 * {@link #GAINED} (along with normal item pickups). {@link #DELETE} and {@link #MAKE_ROOM} let the items mod's Personal
 * Deletor and Compactor act on what comes in. Core knows nothing about the listeners.
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

    /** Says whether an incoming stack is thrown away instead (a Personal Deletor set to it). */
    @FunctionalInterface
    public interface Deleting {
        boolean deletes(ServerPlayer player, ItemStack stack);
    }

    /** Frees room in a full inventory (a Personal Compactor packing materials into their enchanted forms). */
    @FunctionalInterface
    public interface MakingRoom {
        void makeRoom(ServerPlayer player);
    }

    public static final Event<Deleting> DELETE = EventFactory.createArrayBacked(Deleting.class, listeners -> (player, stack) -> {
        for (Deleting listener : listeners) {
            if (listener.deletes(player, stack)) {
                return true;
            }
        }
        return false;
    });

    public static final Event<MakingRoom> MAKE_ROOM = EventFactory.createArrayBacked(MakingRoom.class, listeners -> player -> {
        for (MakingRoom listener : listeners) {
            listener.makeRoom(player);
        }
    });

    private ItemGains() {
    }

    /**
     * Puts the stack into the inventory like {@code Inventory.add} (what didn't fit stays in {@code stack}) and fires
     * {@link #GAINED} for what went in.
     */
    public static void give(ServerPlayer player, ItemStack stack, Source source) {
        if (stack.isEmpty()) {
            return;
        }
        int before = stack.getCount();
        ItemStack copy = stack.copy();
        // Deleted items still count as gathered (collections), they just never reach the inventory.
        if (DELETE.invoker().deletes(player, stack)) {
            stack.setCount(0);
            GAINED.invoker().onGained(player, copy, source);
            return;
        }
        player.getInventory().add(stack);
        if (!stack.isEmpty()) {
            MAKE_ROOM.invoker().makeRoom(player);
            player.getInventory().add(stack);
        }
        int taken = before - stack.getCount();
        if (taken > 0) {
            copy.setCount(taken);
            GAINED.invoker().onGained(player, copy, source);
        }
    }
}
