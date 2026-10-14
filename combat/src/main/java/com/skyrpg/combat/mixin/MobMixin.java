package com.skyrpg.combat.mixin;

import com.skyrpg.combat.Elites;
import com.skyrpg.combat.MobLevels;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.level.ServerLevelAccessor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Every hostile spawn gets its level, and natural ones a chance to be an Elite (see {@link MobLevels}, {@link Elites}). */
@Mixin(Mob.class)
abstract class MobMixin {
    @Inject(method = "finalizeSpawn", at = @At("RETURN"))
    private void skyrpg$maybeElite(ServerLevelAccessor level, DifficultyInstance difficulty, EntitySpawnReason reason, SpawnGroupData data,
                                   CallbackInfoReturnable<SpawnGroupData> cir) {
        MobLevels.markFarmed((Mob) (Object) this, reason);
        MobLevels.assign((Mob) (Object) this, reason);
        Elites.maybeElite((Mob) (Object) this, reason);
    }
}
