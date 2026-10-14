package com.skyrpg.combat.client.slayer;

import com.skyrpg.combat.slayer.SlayerEntities;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.ModelLayerRegistry;

/** The slayer bosses' own 3D models, one per line, each showing its tier (see the four *Model classes). */
final class SlayerRenderers {
    private SlayerRenderers() {
    }

    static void init() {
        ModelLayerRegistry.registerModelLayer(GraveTyrantRenderer.LAYER, GraveTyrantModel::createLayer);
        EntityRendererRegistry.register(SlayerEntities.GRAVE_TYRANT, GraveTyrantRenderer::new);
        BroodmotherBossRenderer.register();
        BoneMarshalBossRenderer.register();
        VoidStalkerBossRenderer.register();
    }
}
