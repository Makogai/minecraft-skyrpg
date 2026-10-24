package com.skyrpg.items.mixin;

import com.skyrpg.core.event.ItemGains;
import com.skyrpg.items.compactor.Enchanted;
import com.skyrpg.items.compactor.PersonalTools;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Normal pickups go past Personal Deletors and Compactors too: a deleted item vanishes as it's touched (it still counts as
 * gathered), and before a compactor material is picked up the compactor packs what's there, so a full inventory has room.
 * Items a player threw or dropped are left alone.
 */
@Mixin(ItemEntity.class)
abstract class ItemEntityPickupMixin {
    @Inject(method = "playerTouch", at = @At("HEAD"), cancellable = true)
    private void skyrpg$personalTools(Player player, CallbackInfo ci) {
        ItemEntity self = (ItemEntity) (Object) this;
        if (!(player instanceof ServerPlayer serverPlayer) || self.hasPickUpDelay() || self.getOwner() != null || self.isRemoved()) {
            return;
        }
        ItemStack stack = self.getItem();
        if (PersonalTools.deletes(serverPlayer, stack)) {
            ItemGains.GAINED.invoker().onGained(serverPlayer, stack.copy(), ItemGains.Source.PICKUP);
            self.discard();
            ci.cancel();
        } else if (Enchanted.of(stack.getItem()) != null && serverPlayer.getInventory().getFreeSlot() < 0
                && serverPlayer.getInventory().getSlotWithRemainingSpace(stack) < 0) {
            PersonalTools.compact(serverPlayer);
        }
    }
}
