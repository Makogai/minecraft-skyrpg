package com.skyrpg.fishing.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.skyrpg.fishing.Bait;
import com.skyrpg.fishing.BaitHolder;
import com.skyrpg.fishing.BiteCues;
import com.skyrpg.fishing.Catching;
import com.skyrpg.fishing.FishingItems;
import com.skyrpg.fishing.Hotspots;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.world.item.FishingRodItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** SkyRPG rods count as rods; Fishing Speed shortens the wait; reeling in a bite can bring up a sea creature, and every catch pays (see {@link Catching}). */
@Mixin(FishingHook.class)
abstract class FishingHookMixin implements BaitHolder {
    @Shadow
    private int nibble;

    @Shadow
    private int timeUntilHooked;

    @Shadow
    private int timeUntilLured;

    @Shadow
    private Entity hookedIn;

    /** The bait this cast was made with (picked at casting; one is used up when a catch lands). */
    @Unique
    private FishingItems.BaitItem skyrpg$bait;

    /** Fishing Speed without any hotspot (the player's stat and the bait). */
    @Unique
    private double skyrpg$speed;

    /** The wait at the start of this tick, to spot when vanilla rolls a new one. */
    @Unique
    private int skyrpg$lastWait;

    @Unique
    private boolean skyrpg$ready;

    @Unique
    private int skyrpg$cue;

    @Unique
    private boolean skyrpg$biting;

    /** Vanilla only keeps the line out while you hold its own rod; SkyRPG's rods are fishing rods too. */
    @Redirect(method = "shouldStopFishing", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/ItemStack;is(Ljava/lang/Object;)Z"))
    private boolean skyrpg$anyRod(ItemStack held, Object item) {
        return held.getItem() == item || held.getItem() instanceof FishingRodItem;
    }

    @Inject(method = "<init>(Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/level/Level;II)V", at = @At("RETURN"))
    private void skyrpg$castSpeed(Player player, Level level, int luck, int lureSpeed, CallbackInfo ci) {
        if (player instanceof ServerPlayer serverPlayer) {
            skyrpg$bait = Bait.pick(serverPlayer);
            skyrpg$speed = Catching.speed(serverPlayer, skyrpg$bait == null ? 0 : skyrpg$bait.fishingSpeed());
            skyrpg$ready = true;
        }
    }

    @Override
    public FishingItems.BaitItem skyrpg$bait() {
        return skyrpg$bait;
    }

    @Inject(method = "catchingFish", at = @At("HEAD"))
    private void skyrpg$rememberWait(BlockPos pos, CallbackInfo ci) {
        skyrpg$lastWait = timeUntilLured;
    }

    /**
     * When vanilla rolls a new wait (100-600 ticks, minus Lure), Fishing Speed shortens it. A bobber inside a hotspot
     * adds the hotspot's Fishing Speed (checked where the bobber is when the wait is rolled).
     */
    @Inject(method = "catchingFish", at = @At("TAIL"))
    private void skyrpg$fishingSpeed(BlockPos pos, CallbackInfo ci) {
        if (skyrpg$ready && skyrpg$lastWait <= 0 && timeUntilLured > 0 && nibble <= 0 && timeUntilHooked <= 0) {
            double speed = skyrpg$speed + Hotspots.speedAt((FishingHook) (Object) this);
            timeUntilLured = Catching.fasterWait(timeUntilLured, speed);
        }
    }

    /** Bite cues (the "!" and "!!!" over the bobber and the ding) and the bait's particles. */
    @Inject(method = "catchingFish", at = @At("TAIL"))
    private void skyrpg$cues(BlockPos pos, CallbackInfo ci) {
        FishingHook hook = (FishingHook) (Object) this;
        int state = nibble > 0 ? BiteCues.BITE : timeUntilHooked > 0 ? BiteCues.APPROACH : BiteCues.NONE;
        if (state != skyrpg$cue) {
            skyrpg$cue = state;
            BiteCues.show(hook, state);
        }
        BiteCues.baitParticles(hook, skyrpg$bait);
    }

    /** Everything fishing gives (the fish, treasure or junk) goes straight into the inventory; the XP orb still comes. */
    @WrapOperation(method = "retrieve", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/Level;addFreshEntity(Lnet/minecraft/world/entity/Entity;)Z"))
    private boolean skyrpg$catchToHand(Level level, Entity entity, Operation<Boolean> original) {
        if (entity instanceof ItemEntity item && ((FishingHook) (Object) this).getPlayerOwner() instanceof ServerPlayer player) {
            Catching.give(player, item.getItem());
            return true;
        }
        return original.call(level, entity);
    }

    @Inject(method = "retrieve", at = @At("HEAD"), cancellable = true)
    private void skyrpg$beforeRetrieve(ItemStack rod, CallbackInfoReturnable<Integer> cir) {
        FishingHook hook = (FishingHook) (Object) this;
        skyrpg$biting = !hook.level().isClientSide() && hookedIn == null && nibble > 0 && hook.getPlayerOwner() instanceof ServerPlayer;
        if (skyrpg$biting && Catching.trySeaCreature(hook, (ServerPlayer) hook.getPlayerOwner())) {
            hook.discard();
            cir.setReturnValue(1);
        }
    }

    @Inject(method = "retrieve", at = @At("RETURN"))
    private void skyrpg$afterRetrieve(ItemStack rod, CallbackInfoReturnable<Integer> cir) {
        FishingHook hook = (FishingHook) (Object) this;
        if (skyrpg$biting && hook.getPlayerOwner() instanceof ServerPlayer player) {
            Catching.caught(hook, player, rod);
        }
        skyrpg$biting = false;
    }
}
