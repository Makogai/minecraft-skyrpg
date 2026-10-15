package com.skyrpg.builder.client.mixin;

import com.skyrpg.builder.client.PlacementMode;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.input.MouseButtonInfo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** While a blueprint is being placed, clicks place or cancel it and the wheel turns it, instead of mining or switching slots. */
@Mixin(MouseHandler.class)
public abstract class MouseHandlerMixin {
    @Inject(method = "onButton", at = @At("HEAD"), cancellable = true)
    private void skyrpgBuilder$placementClick(long window, MouseButtonInfo info, int action, CallbackInfo ci) {
        if (PlacementMode.mouse(info.button(), action)) {
            ci.cancel();
        }
    }

    @Inject(method = "onScroll", at = @At("HEAD"), cancellable = true)
    private void skyrpgBuilder$placementScroll(long window, double xOffset, double yOffset, CallbackInfo ci) {
        if (PlacementMode.scroll(yOffset)) {
            ci.cancel();
        }
    }
}
