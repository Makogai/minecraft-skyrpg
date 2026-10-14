package com.skyrpg.fishing;

import com.skyrpg.fishing.FishingItems.BaitItem;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * Bait is used automatically while fishing. Which bait: the offhand's first (so you can pick one), then the hotbar left
 * to right, then the rest of the inventory. The bait in use when you cast improves that cast, and one is used up per
 * catch (a bite reeled in; not on casting, not reeling in empty).
 */
public final class Bait {
    private Bait() {
    }

    /** The bait item a cast by this player would use, or null. */
    public static BaitItem pick(Player player) {
        ItemStack stack = stack(player);
        return stack == null ? null : (BaitItem) stack.getItem();
    }

    /** The stack of the bait in use (what {@link #pick} found), or null. */
    public static ItemStack stack(Player player) {
        if (player.getOffhandItem().getItem() instanceof BaitItem) {
            return player.getOffhandItem();
        }
        Inventory inventory = player.getInventory();
        for (int slot = 0; slot < 36; slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (!stack.isEmpty() && stack.getItem() instanceof BaitItem) {
                return stack;
            }
        }
        return null;
    }

    /** How many of this bait the player carries. */
    public static int count(Player player, Item bait) {
        int total = 0;
        if (player.getOffhandItem().is(bait)) {
            total += player.getOffhandItem().getCount();
        }
        Inventory inventory = player.getInventory();
        for (int slot = 0; slot < 36; slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (stack.is(bait)) {
                total += stack.getCount();
            }
        }
        return total;
    }

    /** Uses up one of this bait (offhand first, like {@link #pick}). */
    public static void consume(ServerPlayer player, Item bait) {
        if (player.isCreative()) {
            return;
        }
        if (player.getOffhandItem().is(bait)) {
            player.getOffhandItem().shrink(1);
            return;
        }
        Inventory inventory = player.getInventory();
        for (int slot = 0; slot < 36; slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (stack.is(bait)) {
                stack.shrink(1);
                return;
            }
        }
    }
}
