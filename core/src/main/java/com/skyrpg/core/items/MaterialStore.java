package com.skyrpg.core.items;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * Materials a player can pay with, for any mod: the {@code skyrpg_items} mod installs the real store (the inventory
 * plus worn backpacks and what's in them, like the Artisan Bench); without it, only the main inventory counts.
 * <pre>
 * if (MaterialStore.count(player, Items.LAPIS_LAZULI) >= 8) { MaterialStore.take(player, Items.LAPIS_LAZULI, 8); }
 * </pre>
 */
public final class MaterialStore {
    public interface Backend {
        int count(ServerPlayer player, Item item);

        /** Takes up to {@code amount} (call {@link #count} first). */
        void take(ServerPlayer player, Item item, int amount);
    }

    private static Backend backend = new Backend() {
        @Override
        public int count(ServerPlayer player, Item item) {
            int count = 0;
            Inventory inventory = player.getInventory();
            for (int slot = 0; slot < Inventory.INVENTORY_SIZE; slot++) {
                ItemStack stack = inventory.getItem(slot);
                if (stack.is(item)) {
                    count += stack.getCount();
                }
            }
            return count;
        }

        @Override
        public void take(ServerPlayer player, Item item, int amount) {
            int left = amount;
            Inventory inventory = player.getInventory();
            for (int slot = 0; slot < Inventory.INVENTORY_SIZE && left > 0; slot++) {
                ItemStack stack = inventory.getItem(slot);
                if (stack.is(item)) {
                    int n = Math.min(left, stack.getCount());
                    stack.shrink(n);
                    left -= n;
                }
            }
            inventory.setChanged();
        }
    };

    private MaterialStore() {
    }

    public static void install(Backend installed) {
        backend = installed;
    }

    public static int count(ServerPlayer player, Item item) {
        return backend.count(player, item);
    }

    public static void take(ServerPlayer player, Item item, int amount) {
        if (amount > 0) {
            backend.take(player, item, amount);
        }
    }
}
