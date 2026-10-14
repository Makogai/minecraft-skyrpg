package com.skyrpg.progress.mixin;

import net.minecraft.world.entity.player.Inventory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/**
 * Items a player drops on death keep the player as their thrower, so picking them back up isn't counted again as a
 * gathered item (see {@link ItemEntityMixin}). Vanilla drops them ownerless; the thrower only matters for statistics.
 */
@Mixin(Inventory.class)
abstract class InventoryMixin {
    @ModifyArg(method = "dropAll", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/player/Player;createItemStackToDrop(Lnet/minecraft/world/item/ItemStack;ZZ)Lnet/minecraft/world/entity/item/ItemEntity;"),
            index = 2)
    private boolean skyrpg$keepThrower(boolean retainOwnership) {
        return true;
    }
}
