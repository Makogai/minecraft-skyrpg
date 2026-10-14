package com.skyrpg.enchants.mixin;

import com.skyrpg.enchants.EnchantContent;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Items with SkyRPG enchants shimmer like vanilla enchanted ones, unless their glint is switched off on purpose. */
@Mixin(ItemStack.class)
abstract class ItemStackMixin {
    @Inject(method = "hasFoil", at = @At("RETURN"), cancellable = true)
    private void skyrpg$enchantedGlint(CallbackInfoReturnable<Boolean> cir) {
        if (!cir.getReturnValueZ()) {
            ItemStack self = (ItemStack) (Object) this;
            if (self.has(EnchantContent.ENCHANTS) && self.get(DataComponents.ENCHANTMENT_GLINT_OVERRIDE) == null) {
                cir.setReturnValue(true);
            }
        }
    }
}
