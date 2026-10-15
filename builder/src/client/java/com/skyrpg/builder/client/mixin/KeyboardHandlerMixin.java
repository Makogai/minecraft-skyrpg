package com.skyrpg.builder.client.mixin;

import com.skyrpg.builder.client.PlacementMode;
import net.minecraft.client.KeyboardHandler;
import net.minecraft.client.input.KeyEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** While a blueprint is being placed, its keys (R, M, G, arrows, Page Up/Down, Enter, Esc) go to the placement only. */
@Mixin(KeyboardHandler.class)
public abstract class KeyboardHandlerMixin {
    @Inject(method = "keyPress", at = @At("HEAD"), cancellable = true)
    private void skyrpgBuilder$placement(long window, int action, KeyEvent event, CallbackInfo ci) {
        if (PlacementMode.key(event, action)) {
            ci.cancel();
        }
    }
}
