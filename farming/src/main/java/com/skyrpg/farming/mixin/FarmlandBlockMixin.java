package com.skyrpg.farming.mixin;

import com.skyrpg.core.items.GearEffects;
import com.skyrpg.farming.FarmingEffects;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.FarmlandBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Players with no-trample gear don't turn farmland back into dirt by landing on it. */
@Mixin(FarmlandBlock.class)
abstract class FarmlandBlockMixin {
    @Inject(method = "turnToBaseBlock", at = @At("HEAD"), cancellable = true)
    private void skyrpg$noTrample(Entity entity, BlockState state, Level level, BlockPos pos, CallbackInfo ci) {
        if (entity instanceof ServerPlayer player && GearEffects.has(player, FarmingEffects.NO_TRAMPLE)) {
            ci.cancel();
        }
    }
}
