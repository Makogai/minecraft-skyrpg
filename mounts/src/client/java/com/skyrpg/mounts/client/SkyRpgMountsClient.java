package com.skyrpg.mounts.client;

import com.skyrpg.core.client.hud.HudManager;
import com.skyrpg.core.client.menu.SkyRpgMenu;
import com.skyrpg.mounts.MountEntities;
import com.skyrpg.mounts.client.mob.GriffinModel;
import com.skyrpg.mounts.client.mob.GriffinRenderer;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.ModelLayerRegistry;

/** Mounts on the client: the griffin's model and renderer, the flight stamina bar, and the K menu's Mounts page. */
public class SkyRpgMountsClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        ModelLayerRegistry.registerModelLayer(GriffinRenderer.LAYER, GriffinModel::createLayer);
        EntityRendererRegistry.register(MountEntities.GRIFFIN, GriffinRenderer::new);
        HudManager.register(new StaminaWidget());
        SkyRpgMenu.registerPage(new MountsPage());
    }
}
