package com.skyrpg.items.backpack;

import net.minecraft.core.NonNullList;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.core.component.DataComponents;

/**
 * An open backpack: a plain chest menu (so the client shows the usual, themed container screen) whose
 * storage writes every change straight back onto the backpack item in its slot. Its slots refuse
 * backpacks. The menu closes if that slot stops holding the backpack it opened.
 */
public final class BackpackMenu extends ChestMenu {
    private final ServerPlayer owner;
    private final int backpackSlot;
    private final ItemStack opened;

    private BackpackMenu(MenuType<?> type, int id, Inventory inventory, Storage storage, int rows, ServerPlayer owner, int backpackSlot) {
        super(type, id, inventory, storage, rows);
        this.owner = owner;
        this.backpackSlot = backpackSlot;
        this.opened = Backpacks.get(owner, backpackSlot);
        for (int i = 0; i < rows * 9; i++) {
            Slot plain = slots.get(i);
            Slot slot = new Slot(storage, plain.getContainerSlot(), plain.x, plain.y) {
                @Override
                public boolean mayPlace(ItemStack stack) {
                    return !Backpacks.isBackpack(stack);
                }
            };
            slot.index = i;
            slots.set(i, slot);
        }
    }

    static BackpackMenu create(int id, Inventory inventory, ServerPlayer owner, int backpackSlot) {
        ItemStack backpack = Backpacks.get(owner, backpackSlot);
        int rows = Math.max(1, Math.min(6, Backpacks.capacity(backpack) / 9));
        Storage storage = new Storage(owner, backpackSlot, rows * 9);
        return new BackpackMenu(type(rows), id, inventory, storage, rows, owner, backpackSlot);
    }

    private static MenuType<?> type(int rows) {
        return switch (rows) {
            case 1 -> MenuType.GENERIC_9x1;
            case 2 -> MenuType.GENERIC_9x2;
            case 3 -> MenuType.GENERIC_9x3;
            case 4 -> MenuType.GENERIC_9x4;
            case 5 -> MenuType.GENERIC_9x5;
            default -> MenuType.GENERIC_9x6;
        };
    }

    @Override
    public boolean stillValid(Player player) {
        return player == owner && Backpacks.get(owner, backpackSlot).is(opened.getItem());
    }

    /** The backpack's contents, saved onto the item in its slot after every change. */
    private static final class Storage extends SimpleContainer {
        private final ServerPlayer owner;
        private final int backpackSlot;
        private boolean loading;

        Storage(ServerPlayer owner, int backpackSlot, int size) {
            super(size);
            this.owner = owner;
            this.backpackSlot = backpackSlot;
            loading = true;
            NonNullList<ItemStack> items = NonNullList.withSize(size, ItemStack.EMPTY);
            Backpacks.get(owner, backpackSlot).getOrDefault(DataComponents.CONTAINER, ItemContainerContents.EMPTY).copyInto(items);
            for (int i = 0; i < size; i++) {
                setItem(i, items.get(i));
            }
            loading = false;
        }

        @Override
        public void setChanged() {
            super.setChanged();
            if (loading) {
                return;
            }
            ItemStack backpack = Backpacks.get(owner, backpackSlot);
            if (!Backpacks.isBackpack(backpack)) {
                return;
            }
            ItemStack updated = backpack.copy();
            updated.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(getItems()));
            Backpacks.set(owner, backpackSlot, updated);
        }
    }
}
