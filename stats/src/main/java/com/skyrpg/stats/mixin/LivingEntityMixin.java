package com.skyrpg.stats.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.skyrpg.stats.CombatStats;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;

/** Lets {@link CombatStats} scale every hit before vanilla handles it (armor, invulnerability frames, death). */
@Mixin(LivingEntity.class)
abstract class LivingEntityMixin {
    @WrapMethod(method = "hurtServer")
    private boolean skyrpg$applyCombatStats(ServerLevel level, DamageSource source, float amount, Operation<Boolean> original) {
        return original.call(level, source, CombatStats.modifyDamage((LivingEntity) (Object) this, level, source, amount));
    }
}
