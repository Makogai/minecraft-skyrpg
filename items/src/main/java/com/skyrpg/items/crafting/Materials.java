package com.skyrpg.items.crafting;

import com.skyrpg.items.backpack.Backpacks;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemContainerContents;

/**
 * What the bench can use: the main inventory and hotbar, the backpacks in the backpack slots (as items, so a
 * worn backpack can be upgraded), and everything inside those backpacks. Taking goes in that order.
 */
final class Materials {
    /** Stacks taken, and the backpack slot the first one came from (-1 if none), so an upgraded pack goes back there. */
    record Taken(List<ItemStack> stacks, int backpackSlot) {
    }

    private Materials() {
    }

    static int count(Player player, Item item) {
        int count = 0;
        Inventory inventory = player.getInventory();
        for (int slot = 0; slot < Inventory.INVENTORY_SIZE; slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (stack.is(item)) {
                count += stack.getCount();
            }
        }
        for (ItemStack pack : Backpacks.carried(player)) {
            if (pack.is(item)) {
                count += pack.getCount();
            }
            for (ItemStack inside : contents(pack)) {
                if (inside.is(item)) {
                    count += inside.getCount();
                }
            }
        }
        return count;
    }

    /** Takes {@code amount} of the item (call {@link #count} first; takes what there is if short). */
    static Taken take(Player player, Item item, int amount) {
        List<ItemStack> taken = new ArrayList<>();
        int backpackSlot = -1;
        int left = amount;
        Inventory inventory = player.getInventory();
        for (int slot = 0; slot < Inventory.INVENTORY_SIZE && left > 0; slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (stack.is(item)) {
                int n = Math.min(left, stack.getCount());
                taken.add(stack.split(n));
                left -= n;
            }
        }
        for (int slot = 0; slot < Backpacks.SLOTS && left > 0; slot++) {
            ItemStack pack = Backpacks.get(player, slot);
            if (pack.is(item)) {
                taken.add(pack.copy());
                Backpacks.set(player, slot, ItemStack.EMPTY);
                left -= pack.getCount();
                if (backpackSlot < 0) {
                    backpackSlot = slot;
                }
            }
        }
        for (int slot = 0; slot < Backpacks.SLOTS && left > 0; slot++) {
            ItemStack pack = Backpacks.get(player, slot);
            if (pack.isEmpty() || !pack.has(DataComponents.CONTAINER)) {
                continue;
            }
            NonNullList<ItemStack> items = NonNullList.withSize(Math.max(Backpacks.capacity(pack), 1), ItemStack.EMPTY);
            pack.get(DataComponents.CONTAINER).copyInto(items);
            boolean changed = false;
            for (ItemStack inside : items) {
                if (left > 0 && inside.is(item)) {
                    int n = Math.min(left, inside.getCount());
                    taken.add(inside.split(n));
                    left -= n;
                    changed = true;
                }
            }
            if (changed) {
                ItemStack updated = pack.copy();
                updated.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(items));
                Backpacks.set(player, slot, updated);
            }
        }
        inventory.setChanged();
        return new Taken(taken, backpackSlot);
    }

    private static List<ItemStack> contents(ItemStack pack) {
        ItemContainerContents contents = pack.get(DataComponents.CONTAINER);
        return contents == null ? List.of() : contents.nonEmptyItemCopyStream().toList();
    }
}
