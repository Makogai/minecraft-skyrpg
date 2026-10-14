package com.skyrpg.fishing.mixin;

import com.skyrpg.fishing.Catching;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.world.item.FishingRodItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** SkyRPG rods count as rods; Fishing Speed shortens the wait; reeling in a bite can bring up a sea creature, and every catch pays (see {@link Catching}). */
@Mixin(FishingHook.class)
abstract class FishingHookMixin {
    @Shadow
    @Final
    @Mutable
    private int lureSpeed;

    @Shadow
    private int nibble;

    @Shadow
    private Entity hookedIn;

    @Unique
    private boolean skyrpg$biting;

    /** Vanilla only keeps the line out while you hold its own rod; SkyRPG's rods are fishing rods too. */
    @Redirect(method = "shouldStopFishing", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/ItemStack;is(Ljava/lang/Object;)Z"))
    private boolean skyrpg$anyRod(ItemStack held, Object item) {
        return held.getItem() == item || held.getItem() instanceof FishingRodItem;
    }

    @Inject(method = "<init>(Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/level/Level;II)V", at = @At("RETURN"))
    private void skyrpg$fishingSpeed(Player player, Level level, int luck, int lureSpeed, CallbackInfo ci) {
        if (player instanceof ServerPlayer serverPlayer) {
            this.lureSpeed = lureSpeed + Catching.extraLureTicks(serverPlayer);
        }
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
