package com.skyrpg.items.backpack;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * The player's backpack slots as a {@link Container}, so they can be inventory-menu slots. A live view of
 * the attachment (the menu is built before attachments load); stacks are never changed in place.
 */
public final class BackpackContainer implements Container {
    private final Player owner;

    public BackpackContainer(Player owner) {
        this.owner = owner;
    }

    @Override
    public int getContainerSize() {
        return Backpacks.SLOTS;
    }

    @Override
    public boolean isEmpty() {
        return Backpacks.carried(owner).stream().allMatch(ItemStack::isEmpty);
    }

    @Override
    public ItemStack getItem(int slot) {
        return Backpacks.get(owner, slot);
    }

    @Override
    public ItemStack removeItem(int slot, int amount) {
        ItemStack stack = getItem(slot);
        if (stack.isEmpty() || amount <= 0) {
            return ItemStack.EMPTY;
        }
        Backpacks.set(owner, slot, ItemStack.EMPTY);
        return stack;
    }

    @Override
    public ItemStack removeItemNoUpdate(int slot) {
        ItemStack stack = getItem(slot);
        Backpacks.set(owner, slot, ItemStack.EMPTY);
        return stack;
    }

    @Override
    public void setItem(int slot, ItemStack stack) {
        Backpacks.set(owner, slot, stack);
    }

    @Override
    public int getMaxStackSize() {
        return 1;
    }

    @Override
    public boolean canPlaceItem(int slot, ItemStack stack) {
        return Backpacks.isBackpack(stack);
    }

    /** Vanilla menu code sometimes edits a slot's stack in place and then calls this, so store fresh copies. */
    @Override
    public void setChanged() {
        List<ItemStack> copies = new ArrayList<>(Backpacks.SLOTS);
        Backpacks.carried(owner).forEach(stack -> copies.add(stack.copy()));
        owner.setAttached(Backpacks.CARRIED, List.copyOf(copies));
    }

    @Override
    public boolean stillValid(Player player) {
        return true;
    }

    @Override
    public void clearContent() {
        owner.removeAttached(Backpacks.CARRIED);
    }
}
