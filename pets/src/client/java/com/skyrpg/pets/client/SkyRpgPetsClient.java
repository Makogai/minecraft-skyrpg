package com.skyrpg.pets.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.LivingEntityRenderLayerRegistrationCallback;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.world.entity.EntityTypes;

/** Pets on the client: every player's active pet floats at their shoulder as a 3D model (see {@link PetLayer}) with a rarity aura. */
public class SkyRpgPetsClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        LivingEntityRenderLayerRegistrationCallback.EVENT.register((type, renderer, helper, context) -> {
            if (type == EntityTypes.PLAYER && renderer instanceof AvatarRenderer<?> avatar) {
                helper.register(new PetLayer(avatar));
            }
        });
        PetEffects.init();
        com.skyrpg.core.client.menu.SkyRpgMenu.registerPage(new PetsPage());
    }
}
