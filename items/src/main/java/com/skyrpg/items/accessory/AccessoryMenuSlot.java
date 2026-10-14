package com.skyrpg.items.accessory;

import net.minecraft.resources.Identifier;
import net.minecraft.world.Container;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * One accessory slot in the player's {@code InventoryMenu}, added after vanilla's slots (see
 * {@code InventoryMenuMixin}): indices {@link #FIRST_INDEX}..{@code END_INDEX - 1}, in {@link AccessorySlot}
 * order, in a column left of the window. Takes one item from its slot's tag.
 */
public final class AccessoryMenuSlot extends Slot {
    /** InventoryMenu slots 46..49; vanilla's end at 45 (offhand). */
    public static final int FIRST_INDEX = 46;
    public static final int END_INDEX = FIRST_INDEX + AccessorySlot.COUNT;
    /** Item positions in menu coordinates (relative to the window's corner), lined up with the armour rows. */
    public static final int X = -21;
    public static final int Y = 8;
    public static final int SPACING = 18;

    private final AccessorySlot type;
    private boolean hidden;

    public AccessoryMenuSlot(Container container, AccessorySlot type, int x, int y) {
        super(container, type.ordinal(), x, y);
        this.type = type;
    }

    public AccessorySlot type() {
        return type;
    }

    /** Client UI only: hides the slot (no drawing, hovering or clicking), e.g. while the recipe book covers it. */
    public void setHidden(boolean hidden) {
        this.hidden = hidden;
    }

    @Override
    public boolean isActive() {
        return !hidden;
    }

    @Override
    public boolean mayPlace(ItemStack stack) {
        return type.fits(stack);
    }

    @Override
    public int getMaxStackSize() {
        return 1;
    }

    @Override
    public Identifier getNoItemIcon() {
        return type.emptyIcon();
    }

    @Override
    public void setByPlayer(ItemStack stack, ItemStack previous) {
        super.setByPlayer(stack, previous);
        if (container instanceof AccessoryContainer accessories && !stack.isEmpty() && !ItemStack.isSameItemSameComponents(stack, previous)) {
            Accessories.playEquipSound(accessories.owner(), type);
        }
    }
}
