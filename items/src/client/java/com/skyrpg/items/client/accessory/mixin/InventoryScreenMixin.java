package com.skyrpg.items.client.accessory.mixin;

import com.skyrpg.items.backpack.BackpackMenuSlot;
import com.skyrpg.items.client.accessory.AccessoryPanel;
import com.skyrpg.items.client.accessory.AccessoryScreens;
import com.skyrpg.items.client.backpack.BackpackPanel;
import com.skyrpg.items.client.backpack.BackpackScreens;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractRecipeBookScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.gui.screens.recipebook.RecipeBookComponent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.InventoryMenu;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The accessory panel on the survival inventory (E). The recipe book opens where the panel is, so while
 * it's open the panel isn't drawn and its slots are hidden; either way they move with the window, as the
 * slots are placed relative to it.
 */
@Mixin(InventoryScreen.class)
abstract class InventoryScreenMixin extends AbstractRecipeBookScreen<InventoryMenu> {
    private InventoryScreenMixin(InventoryMenu menu, RecipeBookComponent<?> recipeBook, Inventory inventory, Component title) {
        super(menu, recipeBook, inventory, title);
    }

    /** Right before the window texture, which then covers the panel's tucked-in edge. Runs every frame, so the slots follow the book. */
    @Inject(method = "extractBackground", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;blit(Lcom/mojang/renderpearl/api/pipeline/RenderPipeline;Lnet/minecraft/resources/Identifier;IIFFIIII)V"))
    private void skyrpg$drawAccessoryPanel(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
        boolean shown = skyrpg$accessoriesShown();
        AccessoryScreens.setHidden(menu, !shown);
        BackpackScreens.setHidden(menu, !shown);
        if (shown) {
            AccessoryPanel.draw(g, leftPos, topPos, menu.getCarried());
            BackpackPanel.draw(g, leftPos, topPos, BackpackMenuSlot.X, menu.getCarried());
        }
    }

    @Inject(method = "extractRenderState(Lnet/minecraft/client/gui/GuiGraphicsExtractor;IIF)V", at = @At("TAIL"))
    private void skyrpg$accessoryTooltip(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
        if (skyrpg$accessoriesShown()) {
            AccessoryPanel.tooltip(g, font, minecraft.player, menu.getCarried(), mouseX, mouseY, leftPos, topPos);
            BackpackPanel.tooltip(g, font, minecraft.player, menu.getCarried(), mouseX, mouseY, leftPos, topPos, BackpackMenuSlot.X);
        }
    }

    /** A click outside the window drops the carried item, even on a slot; the panel counts as inside. */
    @Override
    protected boolean hasClickedOutside(double mouseX, double mouseY, int left, int top) {
        return super.hasClickedOutside(mouseX, mouseY, left, top)
                && !(skyrpg$accessoriesShown() && (AccessoryPanel.contains(mouseX, mouseY, left, top)
                        || BackpackPanel.contains(mouseX, mouseY, left, top, BackpackMenuSlot.X)));
    }

    private boolean skyrpg$accessoriesShown() {
        return !((AbstractRecipeBookScreenAccess) (Object) this).skyrpg$recipeBook().isVisible();
    }
}
