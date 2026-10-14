package com.skyrpg.progress.mixin;

import com.skyrpg.core.event.ItemGains;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Normal pickups count as {@link ItemGains.Source#PICKUP}: whatever of the stack the player took, unless a player threw or
 * dropped it (those entities have a thrower set).
 */
@Mixin(ItemEntity.class)
abstract class ItemEntityMixin {
    @Unique
    private ItemStack skyrpg$before = ItemStack.EMPTY;

    @Inject(method = "playerTouch", at = @At("HEAD"))
    private void skyrpg$beforeTouch(Player player, CallbackInfo ci) {
        ItemEntity self = (ItemEntity) (Object) this;
        skyrpg$before = player instanceof ServerPlayer && self.getOwner() == null ? self.getItem().copy() : ItemStack.EMPTY;
    }

    @Inject(method = "playerTouch", at = @At("RETURN"))
    private void skyrpg$afterTouch(Player player, CallbackInfo ci) {
        ItemStack before = skyrpg$before;
        skyrpg$before = ItemStack.EMPTY;
        if (before.isEmpty() || !(player instanceof ServerPlayer serverPlayer)) {
            return;
        }
        ItemEntity self = (ItemEntity) (Object) this;
        int taken = before.getCount() - (self.isRemoved() ? 0 : self.getItem().getCount());
        if (taken > 0) {
            before.setCount(taken);
            ItemGains.GAINED.invoker().onGained(serverPlayer, before, ItemGains.Source.PICKUP);
        }
    }
}
