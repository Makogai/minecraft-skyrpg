package com.skyrpg.magic.client.mixin;

import com.skyrpg.magic.client.ClientMagic;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Screen shake for big spells: nudges the camera's yaw and pitch for a few ticks (the player's own aim is not touched). */
@Mixin(Camera.class)
public abstract class CameraMixin {
    @Shadow
    private float xRot;
    @Shadow
    private float yRot;

    @Shadow
    protected abstract void setRotation(float yRot, float xRot);

    @Inject(method = "update", at = @At("TAIL"))
    private void skyrpgMagic$shake(DeltaTracker deltaTracker, CallbackInfo ci) {
        float[] offset = ClientMagic.shakeOffset(deltaTracker.getGameTimeDeltaPartialTick(false));
        if (offset[0] != 0 || offset[1] != 0) {
            setRotation(yRot + offset[0], xRot + offset[1]);
        }
    }
}
