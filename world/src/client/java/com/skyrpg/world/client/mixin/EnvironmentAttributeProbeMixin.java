package com.skyrpg.world.client.mixin;

import com.skyrpg.world.client.Atmosphere;
import net.minecraft.world.attribute.EnvironmentAttribute;
import net.minecraft.world.attribute.EnvironmentAttributeProbe;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * The camera's view of the environment (sky colour, fog colour and distances, the moon...) goes through this probe, so it
 * is where the Blood Moon's red sky and each danger region's fog are painted on: see {@link Atmosphere}.
 */
@Mixin(EnvironmentAttributeProbe.class)
abstract class EnvironmentAttributeProbeMixin {
    @Inject(method = "getValue", at = @At("RETURN"), cancellable = true)
    private <Value> void skyrpg$tint(EnvironmentAttribute<Value> attribute, float partialTick, CallbackInfoReturnable<Value> cir) {
        cir.setReturnValue(Atmosphere.tint(attribute, cir.getReturnValue()));
    }
}
