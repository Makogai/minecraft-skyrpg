package com.skyrpg.stats.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.food.FoodData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Natural regeneration costs hunger per heart healed, and the Health stat gives players many more hearts than vanilla's
 * 10, so the bar emptied after every fight. Healing now costs hunger as if the player had vanilla's 20 HP: a full heal
 * takes the same food however much Health they have.
 */
@Mixin(FoodData.class)
abstract class FoodDataMixin {
    private static final float VANILLA_MAX_HEALTH = 20;

    @WrapOperation(method = "tick", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/food/FoodData;addExhaustion(F)V"))
    private void skyrpg$regenCostLikeVanilla(FoodData food, float exhaustion, Operation<Void> original, @Local(argsOnly = true) ServerPlayer player) {
        float max = player.getMaxHealth();
        original.call(food, max > VANILLA_MAX_HEALTH ? exhaustion * VANILLA_MAX_HEALTH / max : exhaustion);
    }
}
