package com.skyrpg.items.client.accessory.mixin;

import com.skyrpg.items.backpack.BackpackMenuSlot;
import com.skyrpg.items.client.backpack.BackpackPanel;
import com.skyrpg.items.client.backpack.BackpackScreens;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.world.inventory.AbstractContainerMenu;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Right-clicking a carried backpack's slot (survival inventory or the creative inventory tab) opens it. */
@Mixin(AbstractContainerScreen.class)
abstract class AbstractContainerScreenMixin {
    @Shadow
    protected int leftPos;
    @Shadow
    protected int topPos;
    @Shadow
    protected AbstractContainerMenu menu;

    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
    private void skyrpg$openBackpack(MouseButtonEvent event, boolean doubleClick, CallbackInfoReturnable<Boolean> cir) {
        Object self = this;
        int slotX;
        if (self instanceof InventoryScreen inventory && inventory.getMenu().getSlot(BackpackMenuSlot.FIRST_INDEX).isActive()) {
            slotX = BackpackMenuSlot.X;
        } else if (self instanceof CreativeModeInventoryScreen creative && creative.isInventoryOpen()) {
            slotX = BackpackPanel.CREATIVE_X;
        } else {
            return;
        }
        int slot = BackpackPanel.slotAt(event.x(), event.y(), leftPos, topPos, slotX);
        if (BackpackScreens.clickSlot(slot, event.button(), !menu.getCarried().isEmpty())) {
            cir.setReturnValue(true);
        }
    }
}
