package com.skyrpg.core.mixin;

import com.skyrpg.core.items.ToolWear;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * SkyRPG tools wear but never break (see {@link ToolWear}): every durability change goes through
 * {@link ToolWear#adjust}, and a broken tool mines at hand speed and drops nothing that needs a tool.
 */
@Mixin(ItemStack.class)
abstract class ItemStackMixin {
    @Inject(method = "processDurabilityChange", at = @At("RETURN"), cancellable = true)
    private void skyrpg$wear(int amount, ServerLevel level, ServerPlayer player, CallbackInfoReturnable<Integer> cir) {
        int adjusted = ToolWear.adjust((ItemStack) (Object) this, cir.getReturnValueI(), player);
        if (adjusted != cir.getReturnValueI()) {
            cir.setReturnValue(adjusted);
        }
    }

    @Inject(method = "getDestroySpeed", at = @At("RETURN"), cancellable = true)
    private void skyrpg$brokenSpeed(BlockState state, CallbackInfoReturnable<Float> cir) {
        if (cir.getReturnValueF() > 1.0f && ToolWear.isBroken((ItemStack) (Object) this)) {
            cir.setReturnValue(1.0f);
        }
    }

    /** A broken hoe doesn't till, a broken axe doesn't strip, a broken rod doesn't cast: the click falls through to whatever else it does. */
    @Inject(method = "useOn", at = @At("HEAD"), cancellable = true)
    private void skyrpg$brokenUseOn(UseOnContext context, CallbackInfoReturnable<InteractionResult> cir) {
        if (ToolWear.isBroken((ItemStack) (Object) this)) {
            cir.setReturnValue(InteractionResult.PASS);
        }
    }

    @Inject(method = "use", at = @At("HEAD"), cancellable = true)
    private void skyrpg$brokenUse(Level level, Player player, InteractionHand hand, CallbackInfoReturnable<InteractionResult> cir) {
        if (ToolWear.isBroken((ItemStack) (Object) this)) {
            cir.setReturnValue(InteractionResult.PASS);
        }
    }

    @Inject(method = "isCorrectToolForDrops", at = @At("HEAD"), cancellable = true)
    private void skyrpg$brokenDrops(BlockState state, CallbackInfoReturnable<Boolean> cir) {
        if (ToolWear.isBroken((ItemStack) (Object) this)) {
            cir.setReturnValue(false);
        }
    }
}
