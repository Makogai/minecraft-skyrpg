package com.skyrpg.economy.shop;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Prediction;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/** Room checks and handing items over, for the main inventory and hotbar only (never armor or offhand). */
final class Inventories {
    private Inventories() {
    }

    /** How many more of this exact item (same components) fit. */
    static int room(Inventory inventory, ItemStack like) {
        int max = like.getMaxStackSize();
        int room = 0;
        for (int slot = 0; slot < Inventory.INVENTORY_SIZE; slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (stack.isEmpty()) {
                room += max;
            } else if (ItemStack.isSameItemSameComponents(stack, like)) {
                room += Math.max(0, max - stack.getCount());
            }
        }
        return room;
    }

    /** Whether all these stacks fit at once. */
    static boolean fits(Inventory inventory, List<ItemStack> stacks) {
        List<ItemStack> slots = new ArrayList<>();
        for (int slot = 0; slot < Inventory.INVENTORY_SIZE; slot++) {
            slots.add(inventory.getItem(slot).copy());
        }
        for (ItemStack incoming : stacks) {
            int left = incoming.getCount();
            for (ItemStack slot : slots) {
                if (left > 0 && !slot.isEmpty() && ItemStack.isSameItemSameComponents(slot, incoming)) {
                    int moved = Math.min(left, slot.getMaxStackSize() - slot.getCount());
                    if (moved > 0) {
                        slot.grow(moved);
                        left -= moved;
                    }
                }
            }
            for (int i = 0; i < slots.size() && left > 0; i++) {
                if (slots.get(i).isEmpty()) {
                    int moved = Math.min(left, incoming.getMaxStackSize());
                    slots.set(i, incoming.copyWithCount(moved));
                    left -= moved;
                }
            }
            if (left > 0) {
                return false;
            }
        }
        return true;
    }

    /** How many of this item (any components) the main inventory and hotbar hold. */
    static int count(Inventory inventory, Item item) {
        int count = 0;
        for (int slot = 0; slot < Inventory.INVENTORY_SIZE; slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (stack.is(item)) {
                count += stack.getCount();
            }
        }
        return count;
    }

    /** Takes {@code amount} of this item from the main inventory and hotbar (the caller checked there are enough). */
    static void remove(Inventory inventory, Item item, int amount) {
        for (int slot = 0; slot < Inventory.INVENTORY_SIZE && amount > 0; slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (stack.is(item)) {
                int taken = Math.min(amount, stack.getCount());
                stack.shrink(taken);
                amount -= taken;
            }
        }
        inventory.setChanged();
    }

    /** Gives {@code total} of this item in proper stacks; anything that somehow doesn't fit drops at the player's feet. */
    static void give(ServerPlayer player, ItemStack like, int total) {
        int max = like.getMaxStackSize();
        while (total > 0) {
            int count = Math.min(total, max);
            give(player, like.copyWithCount(count));
            total -= count;
        }
    }

    static void give(ServerPlayer player, ItemStack stack) {
        ItemStack copy = stack.copy();
        if (!player.getInventory().add(copy) && !copy.isEmpty()) {
            player.drop(copy, false, Prediction.SERVER_ONLY);
        }
    }
}
