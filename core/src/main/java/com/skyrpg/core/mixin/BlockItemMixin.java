package com.skyrpg.core.mixin;

import com.skyrpg.core.world.PlacedBlocks;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Marks blocks players place (see {@link PlacedBlocks}). */
@Mixin(BlockItem.class)
abstract class BlockItemMixin {
    @Inject(method = "placeBlock", at = @At("RETURN"))
    private void skyrpg$markPlaced(BlockPlaceContext context, BlockState state, CallbackInfoReturnable<Boolean> cir) {
        if (cir.getReturnValueZ() && context.getPlayer() != null) {
            PlacedBlocks.mark(context.getLevel(), context.getClickedPos(), state);
        }
    }
}
