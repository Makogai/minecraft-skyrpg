package com.skyrpg.fishing.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.skyrpg.fishing.LavaFishing;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.FluidTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.storage.loot.LootTable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Lava fishing (see {@link LavaFishing}). Every change only applies to a lava hook (its owner holds a lava rod, in the
 * Nether); any other bobber behaves exactly as vanilla's.
 * <ul>
 *   <li>It can't burn: fire-immune, so lava neither sets it alight nor hurts it.</li>
 *   <li>In {@code tick}, lava counts as water: it bobs on the surface, nibbles and bites, and has no gravity there.</li>
 *   <li>In {@code catchingFish}, the Nether's missing sky doesn't halve the bite speed, and the fish's wake and the bite
 *       show as flames and lava pops over lava (with a pop on the bite).</li>
 *   <li>In {@code retrieve}, the catch comes from lava fishing's loot table (every catch goes straight to the player, see {@code FishingHookMixin}).</li>
 * </ul>
 */
@Mixin(FishingHook.class)
abstract class FishingHookLavaMixin extends Projectile {
    private FishingHookLavaMixin(EntityType<? extends Projectile> type, Level level) {
        super(type, level);
    }

    @Unique
    private boolean skyrpg$lavaHook() {
        return LavaFishing.isLavaHook((FishingHook) (Object) this);
    }

    @Unique
    private boolean skyrpg$inLava() {
        return LavaFishing.inLava((FishingHook) (Object) this);
    }

    /** A lava hook doesn't catch fire (so it also isn't hurt by lava, nor drawn burning). */
    @Override
    public boolean fireImmune() {
        return skyrpg$lavaHook() || super.fireImmune();
    }

    /** Both fluid checks in tick (bobbing, and no gravity while in it): lava is water for a lava hook. */
    @WrapOperation(method = "tick", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/material/FluidState;is(Lnet/minecraft/tags/TagKey;)Z"))
    private boolean skyrpg$lavaFloats(FluidState fluid, TagKey<Fluid> tag, Operation<Boolean> original) {
        return original.call(fluid, tag) || tag == FluidTags.WATER && fluid.is(FluidTags.LAVA) && skyrpg$lavaHook();
    }

    /** Vanilla bites half as fast without sky above; the Nether never has any, so a lava hook bites as under open sky. */
    @ModifyExpressionValue(method = "catchingFish", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/Level;canSeeSky(Lnet/minecraft/core/BlockPos;)Z"))
    private boolean skyrpg$skyOverLava(boolean canSeeSky) {
        return canSeeSky || skyrpg$lavaHook();
    }

    /** The approaching fish and the tease splashes only show over water; over lava too for a lava hook. */
    @WrapOperation(method = "catchingFish", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/block/state/BlockState;is(Ljava/lang/Object;)Z"))
    private boolean skyrpg$lavaSurface(BlockState state, Object block, Operation<Boolean> original) {
        return original.call(state, block) || block == Blocks.WATER && state.is(Blocks.LAVA) && skyrpg$lavaHook();
    }

    /** Bubbles, wakes and splashes as lava pops and flames. */
    @ModifyArg(method = "catchingFish", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/server/level/ServerLevel;sendParticles(Lnet/minecraft/core/particles/ParticleOptions;DDDIDDDD)I"),
            index = 0)
    private ParticleOptions skyrpg$lavaParticles(ParticleOptions particle) {
        return skyrpg$inLava() ? LavaFishing.particle(particle) : particle;
    }

    @Inject(method = "catchingFish", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/projectile/FishingHook;playSound(Lnet/minecraft/sounds/SoundEvent;FF)V"))
    private void skyrpg$lavaBite(BlockPos pos, CallbackInfo ci) {
        if (skyrpg$inLava()) {
            playSound(SoundEvents.LAVA_POP, 0.9f, 0.8f + random.nextFloat() * 0.4f);
        }
    }

    @ModifyArg(method = "retrieve", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/server/ReloadableServerRegistries$Holder;getLootTable(Lnet/minecraft/resources/ResourceKey;)Lnet/minecraft/world/level/storage/loot/LootTable;"))
    private ResourceKey<LootTable> skyrpg$lavaCatches(ResourceKey<LootTable> table) {
        return skyrpg$inLava() ? LavaFishing.CATCHES : table;
    }
}
