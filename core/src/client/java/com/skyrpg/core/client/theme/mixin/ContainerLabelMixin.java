package com.skyrpg.core.client.theme.mixin;

import com.skyrpg.core.client.theme.ThemePack;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.gui.screens.inventory.MerchantScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/**
 * Container titles ("Inventory", "Chest", "Crafting"...) are drawn in dark grey, which can't be read on
 * the theme's dark panels. These are the vanilla screens that draw labels in that grey themselves.
 */
@Mixin({AbstractContainerScreen.class, InventoryScreen.class, CreativeModeInventoryScreen.class, MerchantScreen.class})
public abstract class ContainerLabelMixin {
    @ModifyArg(method = "extractLabels", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;text(Lnet/minecraft/client/gui/Font;Lnet/minecraft/network/chat/Component;IIIZ)V"),
            index = 4)
    private int skyrpg$themedLabelColor(int color) {
        return ThemePack.labelColor(color);
    }
}
