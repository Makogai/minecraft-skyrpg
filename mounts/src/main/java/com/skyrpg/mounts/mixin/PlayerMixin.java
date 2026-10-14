package com.skyrpg.mounts.mixin;

import com.skyrpg.mounts.GriffinEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Sneaking dismounts a rider, except in mid-air on a griffin, where sneak means "descend". */
@Mixin(Player.class)
public abstract class PlayerMixin {
    @Inject(method = "wantsToStopRiding", at = @At("HEAD"), cancellable = true)
    private void skyrpg$sneakDescendsInFlight(CallbackInfoReturnable<Boolean> cir) {
        if (((Player) (Object) this).getVehicle() instanceof GriffinEntity griffin && griffin.isFlyingNow()) {
            cir.setReturnValue(false);
        }
    }
}
