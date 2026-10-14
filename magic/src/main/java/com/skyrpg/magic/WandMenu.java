package com.skyrpg.magic;

import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * The wand's spell slots: a one-row container with one slot per spell slot of the wand (in the middle, the rest are locked
 * filler), where scrolls go in and come out. It looks like a chest to the client (vanilla's 9x1 menu); every change is written
 * straight back to the wand stack, and the menu closes if the wand leaves the main hand. Removing a scroll gives the scroll back.
 */
public final class WandMenu extends AbstractContainerMenu {
    private static final int COLUMNS = 9;

    private final ItemStack wand;
    private final WandDef def;
    private final Container container = new SimpleContainer(COLUMNS);
    private final int first;
    private final int count;
    private boolean loading = true;

    private WandMenu(int id, Inventory inventory, ItemStack wand, WandDef def) {
        super(MenuType.GENERIC_9x1, id);
        this.wand = wand;
        this.def = def;
        this.count = def.slots();
        this.first = (COLUMNS - count) / 2;
        WandData data = WandItem.data(wand);
        for (int i = 0; i < COLUMNS; i++) {
            if (i >= first && i < first + count) {
                String spell = data.spellAt(i - first);
                Item scroll = spell.isEmpty() ? null : MagicItems.scrollFor(spell);
                container.setItem(i, scroll == null ? ItemStack.EMPTY : new ItemStack(scroll));
                addSlot(new ScrollSlot(container, i, 8 + i * 18, 18));
            } else {
                container.setItem(i, locked());
                addSlot(new Slot(container, i, 8 + i * 18, 18) {
                    @Override
                    public boolean mayPlace(ItemStack stack) {
                        return false;
                    }

                    @Override
                    public boolean mayPickup(Player player) {
                        return false;
                    }
                });
            }
        }
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new HeldSlot(inventory, col + row * 9 + 9, 8 + col * 18, 49 + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            addSlot(new HeldSlot(inventory, col, 8 + col * 18, 107));
        }
        loading = false;
    }

    private static ItemStack locked() {
        ItemStack pane = new ItemStack(Items.STAINED_GLASS_PANE.gray());
        pane.set(DataComponents.ITEM_NAME, Component.literal("Locked").withStyle(ChatFormatting.DARK_GRAY));
        return pane;
    }

    /** Opens the held wand's slots for this player. */
    public static void open(ServerPlayer player) {
        ItemStack stack = player.getMainHandItem();
        WandDef def = WandItem.def(stack);
        if (def == null || player.isSpectator()) {
            return;
        }
        player.openMenu(new SimpleMenuProvider((id, inventory, p) -> new WandMenu(id, inventory, stack, def),
                Component.literal(stack.getHoverName().getString() + " - Spell Slots")));
    }

    private int spellSlot(Slot slot) {
        return slot.index - first;
    }

    private boolean holds(String spell, int exceptSlot) {
        for (int i = first; i < first + count; i++) {
            if (i != exceptSlot && MagicItems.spellOf(container.getItem(i)) != null && spell.equals(MagicItems.spellOf(container.getItem(i)))) {
                return true;
            }
        }
        return false;
    }

    /** Writes what the slots hold back to the wand. */
    private void sync() {
        if (loading) {
            return;
        }
        WandData data = WandItem.data(wand);
        for (int i = 0; i < count; i++) {
            String spell = MagicItems.spellOf(container.getItem(first + i));
            data = data.with(i, spell == null ? "" : spell);
        }
        WandItem.setData(wand, data);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem() || (index < COLUMNS && !(slot instanceof ScrollSlot))) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = slot.getItem();
        ItemStack before = stack.copy();
        if (index < COLUMNS) {
            if (!moveItemStackTo(stack, COLUMNS, slots.size(), true)) {
                return ItemStack.EMPTY;
            }
        } else {
            boolean moved = false;
            for (int i = first; i < first + count && !moved; i++) {
                Slot target = slots.get(i);
                if (!target.hasItem() && target.mayPlace(stack)) {
                    target.setByPlayer(stack.split(1));
                    moved = true;
                }
            }
            if (!moved) {
                return ItemStack.EMPTY;
            }
        }
        if (stack.isEmpty()) {
            slot.setByPlayer(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }
        return before;
    }

    @Override
    public boolean stillValid(Player player) {
        return player.getMainHandItem() == wand && player.isAlive();
    }

    /** One scroll, of a spell the wand does not already hold. */
    private final class ScrollSlot extends Slot {
        ScrollSlot(Container container, int index, int x, int y) {
            super(container, index, x, y);
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            String spell = MagicItems.spellOf(stack);
            return spell != null && !holds(spell, index);
        }

        @Override
        public int getMaxStackSize() {
            return 1;
        }

        @Override
        public void setChanged() {
            super.setChanged();
            sync();
        }
    }

    /** A slot of the player's inventory; the wand itself can't be picked up while its slots are open. */
    private final class HeldSlot extends Slot {
        HeldSlot(Container container, int index, int x, int y) {
            super(container, index, x, y);
        }

        @Override
        public boolean mayPickup(Player player) {
            return getItem() != wand;
        }
    }
}
