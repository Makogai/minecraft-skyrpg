package com.skyrpg.mining.mixin;

import com.skyrpg.mining.AutoPickup;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerPlayerGameMode;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Marks who is breaking a block, so its drops go to them (see {@link AutoPickup}). */
@Mixin(ServerPlayerGameMode.class)
abstract class ServerPlayerGameModeMixin {
    @Shadow
    @Final
    protected ServerPlayer player;

    @Inject(method = "destroyBlock", at = @At("HEAD"))
    private void skyrpg$beginPickup(BlockPos pos, CallbackInfoReturnable<Boolean> cir) {
        AutoPickup.begin(player, pos);
    }

    @Inject(method = "destroyBlock", at = @At("RETURN"))
    private void skyrpg$endPickup(BlockPos pos, CallbackInfoReturnable<Boolean> cir) {
        AutoPickup.end();
    }
}
