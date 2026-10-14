package com.skyrpg.items.accessory;

import com.skyrpg.core.stats.Stats;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * The player's accessories as a {@link Container}, so they can be menu slots. It is a live view of the
 * attachment: the menu is built in the player's constructor, before attachments load, so every call reads
 * the attachment again. Stacks are never changed in place; removing one hands out the stored stack and
 * stores EMPTY.
 */
public final class AccessoryContainer implements Container {
    private final Player owner;

    public AccessoryContainer(Player owner) {
        this.owner = owner;
    }

    public Player owner() {
        return owner;
    }

    @Override
    public int getContainerSize() {
        return AccessorySlot.COUNT;
    }

    @Override
    public boolean isEmpty() {
        return Accessories.loadout(owner).isEmpty();
    }

    @Override
    public ItemStack getItem(int slot) {
        return Accessories.get(owner, type(slot));
    }

    @Override
    public ItemStack removeItem(int slot, int amount) {
        ItemStack stack = getItem(slot);
        if (stack.isEmpty() || amount <= 0) {
            return ItemStack.EMPTY;
        }
        if (amount >= stack.getCount()) {
            store(slot, ItemStack.EMPTY);
            return stack;
        }
        store(slot, stack.copyWithCount(stack.getCount() - amount));
        return stack.copyWithCount(amount);
    }

    @Override
    public ItemStack removeItemNoUpdate(int slot) {
        ItemStack stack = getItem(slot);
        store(slot, ItemStack.EMPTY);
        return stack;
    }

    @Override
    public void setItem(int slot, ItemStack stack) {
        store(slot, stack);
    }

    @Override
    public int getMaxStackSize() {
        return 1;
    }

    @Override
    public boolean canPlaceItem(int slot, ItemStack stack) {
        return type(slot).fits(stack);
    }

    /**
     * Vanilla menu code sometimes edits a slot's stack in place and then calls this, so it stores fresh
     * copies (which always syncs) and updates stats.
     */
    @Override
    public void setChanged() {
        List<ItemStack> copies = new ArrayList<>(AccessorySlot.COUNT);
        Accessories.loadout(owner).stacks().forEach(stack -> copies.add(stack.copy()));
        owner.setAttached(Accessories.LOADOUT, new AccessoryLoadout(copies));
        if (owner instanceof ServerPlayer player) {
            Stats.refresh(player);
        }
    }

    @Override
    public boolean stillValid(Player player) {
        return true;
    }

    @Override
    public void clearContent() {
        owner.setAttached(Accessories.LOADOUT, AccessoryLoadout.EMPTY);
    }

    private void store(int slot, ItemStack stack) {
        owner.setAttached(Accessories.LOADOUT, Accessories.loadout(owner).with(type(slot), stack));
    }

    private static AccessorySlot type(int slot) {
        return AccessorySlot.values()[slot];
    }
}
