package com.skyrpg.vfx.client.mixin;

import com.skyrpg.vfx.client.ScreenFx;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Screen shake: nudges the finished camera rotation (never the player's own aim); see {@link ScreenFx}. */
@Mixin(Camera.class)
abstract class CameraMixin {
    @Unique
    private static final float[] SKYRPG_SHAKE = new float[2];

    @Shadow
    protected abstract void setRotation(float yRot, float xRot);

    @Shadow
    public abstract float yRot();

    @Shadow
    public abstract float xRot();

    @Inject(method = "update(Lnet/minecraft/client/DeltaTracker;)V", at = @At("TAIL"))
    private void skyrpg$shake(DeltaTracker deltaTracker, CallbackInfo ci) {
        ScreenFx.offset(deltaTracker.getGameTimeDeltaPartialTick(false), SKYRPG_SHAKE);
        if (SKYRPG_SHAKE[0] != 0 || SKYRPG_SHAKE[1] != 0) {
            setRotation(yRot() + SKYRPG_SHAKE[0], Math.max(-90, Math.min(90, xRot() + SKYRPG_SHAKE[1])));
        }
    }
}
