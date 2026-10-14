package com.skyrpg.core.mixin;

import com.skyrpg.core.world.ToolGates;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockBehaviour;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Blocks that need a better tool don't break at all with a weaker one (see {@link ToolGates}). */
@Mixin(BlockBehaviour.BlockStateBase.class)
abstract class BlockStateBaseMixin {
    @Inject(method = "getDestroyProgress", at = @At("RETURN"), cancellable = true)
    private void skyrpg$toolTier(Player player, BlockGetter level, BlockPos pos, CallbackInfoReturnable<Float> cir) {
        if (cir.getReturnValueF() > 0 && !ToolGates.canBreak(player, (BlockBehaviour.BlockStateBase) (Object) this)) {
            cir.setReturnValue(0.0f);
        }
    }
}
