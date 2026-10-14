package com.skyrpg.pets.client.mixin;

import com.skyrpg.pets.client.PetPreview;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Marks the live player previews (the inventory's and, through core's {@code Draw.entityFollowsMouse}, the SkyRPG menu's):
 * their render state is extracted inside this call, so the avatar renderer mixin can tell a preview from the world.
 */
@Mixin(InventoryScreen.class)
abstract class InventoryScreenMixin {

    @Inject(method = "extractEntityInInventoryFollowsMouse", at = @At("HEAD"))
    private static void skyrpg$previewStart(CallbackInfo ci) {
        PetPreview.active = true;
    }

    @Inject(method = "extractEntityInInventoryFollowsMouse", at = @At("RETURN"))
    private static void skyrpg$previewEnd(CallbackInfo ci) {
        PetPreview.active = false;
    }
}
