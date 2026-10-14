package com.skyrpg.items.client.accessory.mixin;

import com.skyrpg.items.accessory.AccessoryMenuSlot;
import com.skyrpg.items.backpack.BackpackMenuSlot;
import com.skyrpg.items.client.accessory.AccessoryPanel;
import com.skyrpg.items.client.accessory.AccessoryScreens;
import com.skyrpg.items.client.backpack.BackpackPanel;
import com.skyrpg.items.client.backpack.BackpackScreens;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.CreativeModeTab;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * The accessory panel on the creative inventory's "Survival Inventory" tab, where creative players see
 * their inventory. Every other tab shows items to take instead, so neither the panel nor the slots are there.
 */
@Mixin(CreativeModeInventoryScreen.class)
abstract class CreativeModeInventoryScreenMixin extends AbstractContainerScreen<CreativeModeInventoryScreen.ItemPickerMenu> {
    @Shadow
    private boolean hasClickedOutside;

    private CreativeModeInventoryScreenMixin(CreativeModeInventoryScreen.ItemPickerMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
    }

    @Shadow
    public abstract boolean isInventoryOpen();

    /*
     * The inventory tab shows the player's inventory menu through wrappers placed at its own spots, and
     * anything it doesn't know lands on the hotbar row. The accessory slots go where the survival
     * inventory has them, left of the window.
     */
    @ModifyArg(method = "selectTab", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/screens/inventory/CreativeModeInventoryScreen$SlotWrapper;<init>(Lnet/minecraft/world/inventory/Slot;III)V"),
            index = 2)
    private int skyrpg$accessorySlotX(Slot target, int index, int x, int y) {
        return target instanceof AccessoryMenuSlot ? target.x : target instanceof BackpackMenuSlot ? BackpackPanel.CREATIVE_X : x;
    }

    @ModifyArg(method = "selectTab", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/screens/inventory/CreativeModeInventoryScreen$SlotWrapper;<init>(Lnet/minecraft/world/inventory/Slot;III)V"),
            index = 3)
    private int skyrpg$accessorySlotY(Slot target, int index, int x, int y) {
        return target instanceof AccessoryMenuSlot || target instanceof BackpackMenuSlot ? target.y : y;
    }

    /** The wrappers show and hide with their slot, which the survival inventory may have left hidden. */
    @Inject(method = "selectTab", at = @At("TAIL"))
    private void skyrpg$showAccessorySlots(CreativeModeTab tab, CallbackInfo ci) {
        AccessoryScreens.setHidden(minecraft.player.inventoryMenu, false);
        BackpackScreens.setHidden(minecraft.player.inventoryMenu, false);
    }

    /** Right before the window texture, which then covers the panel's tucked-in edge. */
    @Inject(method = "extractBackground", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;blit(Lcom/mojang/renderpearl/api/pipeline/RenderPipeline;Lnet/minecraft/resources/Identifier;IIFFIIII)V"))
    private void skyrpg$drawAccessoryPanel(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
        if (isInventoryOpen()) {
            AccessoryPanel.draw(g, leftPos, topPos, menu.getCarried());
            BackpackPanel.draw(g, leftPos, topPos, BackpackPanel.CREATIVE_X, menu.getCarried());
        }
    }

    @Inject(method = "extractRenderState(Lnet/minecraft/client/gui/GuiGraphicsExtractor;IIF)V", at = @At("TAIL"))
    private void skyrpg$accessoryTooltip(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
        if (isInventoryOpen()) {
            AccessoryPanel.tooltip(g, font, minecraft.player, menu.getCarried(), mouseX, mouseY, leftPos, topPos);
            BackpackPanel.tooltip(g, font, minecraft.player, menu.getCarried(), mouseX, mouseY, leftPos, topPos, BackpackPanel.CREATIVE_X);
        }
    }

    /** A click outside the window drops the carried item, even on a slot; the panel counts as inside. */
    @Inject(method = "hasClickedOutside", at = @At("HEAD"), cancellable = true)
    private void skyrpg$clickOnPanel(double mouseX, double mouseY, int left, int top, CallbackInfoReturnable<Boolean> cir) {
        if (isInventoryOpen() && (AccessoryPanel.contains(mouseX, mouseY, left, top)
                || BackpackPanel.contains(mouseX, mouseY, left, top, BackpackPanel.CREATIVE_X))) {
            // Vanilla remembers the answer for its slot clicks, so it has to be set here too.
            hasClickedOutside = false;
            cir.setReturnValue(false);
        }
    }
}
