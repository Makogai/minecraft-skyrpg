package com.skyrpg.pets.client.mixin;

import com.skyrpg.pets.PetRarity;
import com.skyrpg.pets.PetType;
import com.skyrpg.pets.Pets;
import com.skyrpg.pets.client.PetLayer;
import com.skyrpg.pets.client.PetPreview;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.world.entity.Avatar;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Puts each player's active pet (synced to everyone) on their render state as a {@link PetLayer.PetView}. */
@Mixin(AvatarRenderer.class)
abstract class AvatarRendererMixin {
    @Inject(method = "extractRenderState(Lnet/minecraft/world/entity/Avatar;Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;F)V",
            at = @At("TAIL"))
    private void skyrpg$extractPet(Avatar entity, AvatarRenderState state, float partialTicks, CallbackInfo ci) {
        ItemStack pet = entity instanceof Player player ? player.getAttachedOrElse(Pets.ACTIVE, ItemStack.EMPTY) : ItemStack.EMPTY;
        PetType type = pet.isEmpty() ? null : Pets.type(pet);
        if (type == null) {
            state.setData(PetLayer.PET, null);
            return;
        }
        boolean preview = PetPreview.active;
        Minecraft mc = Minecraft.getInstance();
        // never in first person (the player's own body isn't drawn there either)
        if (!preview && entity == mc.getCameraEntity() && mc.options.getCameraType().isFirstPerson()) {
            state.setData(PetLayer.PET, null);
            return;
        }
        PetRarity rarity = Pets.rarity(pet);
        state.setData(PetLayer.PET, new PetLayer.PetView(type, rarity == null ? PetRarity.COMMON : rarity, entity.getId(), preview));
    }
}
