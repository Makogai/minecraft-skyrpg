package com.skyrpg.magic.client.mixin;

import com.skyrpg.magic.client.MagicInput;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Sneak + mouse wheel with a wand in hand cycles the spell instead of the hotbar slot. */
@Mixin(MouseHandler.class)
public abstract class MouseHandlerMixin {
    @Inject(method = "onScroll", at = @At("HEAD"), cancellable = true)
    private void skyrpgMagic$cycle(long window, double xOffset, double yOffset, CallbackInfo ci) {
        if (MagicInput.scroll(Minecraft.getInstance(), yOffset)) {
            ci.cancel();
        }
    }
}
