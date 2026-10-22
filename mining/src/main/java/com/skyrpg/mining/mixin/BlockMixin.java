package com.skyrpg.mining.mixin;

import com.skyrpg.mining.AutoPickup;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Hands a broken block's drops to the player breaking it instead of spawning them (see {@link AutoPickup}). */
@Mixin(Block.class)
abstract class BlockMixin {
    @Inject(method = "popResource(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/item/ItemStack;)V",
            at = @At("HEAD"), cancellable = true)
    private static void skyrpg$pickUp(Level level, BlockPos pos, ItemStack stack, CallbackInfo ci) {
        if (!level.isClientSide() && AutoPickup.take(level, pos, stack)) {
            ci.cancel();
        }
    }

    @Inject(method = "popResourceFromFace", at = @At("HEAD"), cancellable = true)
    private static void skyrpg$pickUpFromFace(Level level, BlockPos pos, Direction face, ItemStack stack, CallbackInfo ci) {
        if (!level.isClientSide() && AutoPickup.take(level, pos, stack)) {
            ci.cancel();
        }
    }
}
