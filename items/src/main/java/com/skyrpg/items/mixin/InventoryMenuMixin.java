package com.skyrpg.items.mixin;

import com.skyrpg.items.accessory.AccessoryContainer;
import com.skyrpg.items.accessory.AccessoryMenuSlot;
import com.skyrpg.items.accessory.AccessorySlot;
import com.skyrpg.items.backpack.BackpackContainer;
import com.skyrpg.items.backpack.BackpackMenuSlot;
import com.skyrpg.items.backpack.Backpacks;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractCraftingMenu;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Adds the accessory slots to every player's inventory menu, on both sides, after vanilla's slots
 * ({@link AccessoryMenuSlot#FIRST_INDEX}..), and lets shift-click put an accessory on. Shift-clicking one
 * back off needs nothing: vanilla's last case moves any other slot into the inventory.
 */
@Mixin(InventoryMenu.class)
abstract class InventoryMenuMixin extends AbstractCraftingMenu {
    private InventoryMenuMixin(MenuType<?> type, int containerId, int width, int height) {
        super(type, containerId, width, height);
    }

    @Inject(method = "<init>", at = @At("TAIL"))
    private void skyrpg$addAccessorySlots(Inventory inventory, boolean active, Player owner, CallbackInfo ci) {
        AccessoryContainer container = new AccessoryContainer(owner);
        for (AccessorySlot slot : AccessorySlot.values()) {
            addSlot(new AccessoryMenuSlot(container, slot, AccessoryMenuSlot.X, AccessoryMenuSlot.Y + AccessoryMenuSlot.SPACING * slot.ordinal()));
        }
        // The backpack slots come right after (BackpackMenuSlot.FIRST_INDEX..), right of the window.
        BackpackContainer backpacks = new BackpackContainer(owner);
        for (int i = 0; i < Backpacks.SLOTS; i++) {
            addSlot(new BackpackMenuSlot(backpacks, i, BackpackMenuSlot.X, BackpackMenuSlot.Y + BackpackMenuSlot.SPACING * i));
        }
    }

    /** Where shift-click puts an accessory (its empty slot) or a backpack (the first empty backpack slot), or -1. */
    private int skyrpg$targetSlot(ItemStack stack) {
        AccessorySlot type = AccessorySlot.of(stack);
        if (type != null) {
            return slots.get(type.menuIndex()).hasItem() ? -1 : type.menuIndex();
        }
        if (Backpacks.isBackpack(stack)) {
            for (int i = BackpackMenuSlot.FIRST_INDEX; i < BackpackMenuSlot.END_INDEX; i++) {
                if (!slots.get(i).hasItem()) {
                    return i;
                }
            }
        }
        return -1;
    }

    /** From the inventory or hotbar, an accessory or backpack goes into its slot if there's room; anything else is vanilla's. */
    @Inject(method = "quickMoveStack", at = @At("HEAD"), cancellable = true)
    private void skyrpg$quickMoveAccessory(Player player, int index, CallbackInfoReturnable<ItemStack> cir) {
        if (index < InventoryMenu.INV_SLOT_START || index >= InventoryMenu.USE_ROW_SLOT_END) {
            return;
        }
        Slot source = slots.get(index);
        ItemStack stack = source.getItem();
        int target = skyrpg$targetSlot(stack);
        if (target < 0) {
            return;
        }
        ItemStack moved = stack.copy();
        if (!moveItemStackTo(stack, target, target + 1, false)) {
            return;
        }
        // The same bookkeeping vanilla does after a move.
        if (stack.isEmpty()) {
            source.setByPlayer(ItemStack.EMPTY, moved);
        } else {
            source.setChanged();
        }
        if (stack.getCount() == moved.getCount()) {
            cir.setReturnValue(ItemStack.EMPTY);
            return;
        }
        source.onTake(player, stack);
        cir.setReturnValue(moved);
    }
}
