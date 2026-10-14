package com.skyrpg.magic.client.mixin;

import com.skyrpg.magic.client.MagicInput;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** With a wand in hand the mouse is for spells: no melee swing, no block breaking, and use goes to the wheel unless you aim at something with a use of its own. */
@Mixin(Minecraft.class)
public abstract class MinecraftMixin {
    @Inject(method = "startAttack", at = @At("HEAD"), cancellable = true)
    private void skyrpgMagic$noSwing(CallbackInfoReturnable<Boolean> cir) {
        if (MagicInput.active((Minecraft) (Object) this)) {
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "continueAttack", at = @At("HEAD"), cancellable = true)
    private void skyrpgMagic$noBreaking(boolean down, CallbackInfo ci) {
        if (MagicInput.active((Minecraft) (Object) this)) {
            ci.cancel();
        }
    }

    @Inject(method = "startUseItem", at = @At("HEAD"), cancellable = true)
    private void skyrpgMagic$noUse(CallbackInfo ci) {
        if (MagicInput.cancelsUse((Minecraft) (Object) this)) {
            ci.cancel();
        }
    }
}
