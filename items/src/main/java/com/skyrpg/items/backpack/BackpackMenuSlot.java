package com.skyrpg.items.backpack;

import com.skyrpg.items.SkyRpgItems;
import net.minecraft.resources.Identifier;
import net.minecraft.world.Container;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * One backpack slot in the player's {@code InventoryMenu}, after the accessory slots: indices
 * {@link #FIRST_INDEX}..{@code END_INDEX - 1}, in a column right of the window. Takes one backpack.
 */
public final class BackpackMenuSlot extends Slot {
    /** InventoryMenu slots 50..52, right after the accessories (46..49). */
    public static final int FIRST_INDEX = 50;
    public static final int END_INDEX = FIRST_INDEX + Backpacks.SLOTS;
    /** Item positions in menu coordinates (relative to the window's corner): right of the 176px survival window, lined up with the armour rows. */
    public static final int X = 181;
    public static final int Y = 8;
    public static final int SPACING = 18;
    public static final Identifier EMPTY_ICON = SkyRpgItems.id("container/slot/backpack");

    private boolean hidden;

    public BackpackMenuSlot(Container container, int index, int x, int y) {
        super(container, index, x, y);
    }

    /** Client UI only: hides the slot, e.g. while the recipe book covers it. */
    public void setHidden(boolean hidden) {
        this.hidden = hidden;
    }

    @Override
    public boolean isActive() {
        return !hidden;
    }

    @Override
    public boolean mayPlace(ItemStack stack) {
        return Backpacks.isBackpack(stack);
    }

    @Override
    public int getMaxStackSize() {
        return 1;
    }

    @Override
    public Identifier getNoItemIcon() {
        return EMPTY_ICON;
    }
}
