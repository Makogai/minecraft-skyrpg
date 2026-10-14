package com.skyrpg.combat.client.mob;

import com.skyrpg.combat.mob.DeepMobs;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.ModelLayerRegistry;

/** The monsters of The Deep on their own 3D models (see {@link SporelingModel} and {@link FireBeetleModel}). */
public final class DeepMobRenderers {
    private DeepMobRenderers() {
    }

    public static void init() {
        ModelLayerRegistry.registerModelLayer(SporelingRenderer.LAYER, SporelingModel::createLayer);
        ModelLayerRegistry.registerModelLayer(FireBeetleRenderer.LAYER, FireBeetleModel::createLayer);
        EntityRendererRegistry.register(DeepMobs.SPORELING, SporelingRenderer::new);
        EntityRendererRegistry.register(DeepMobs.FIRE_BEETLE, FireBeetleRenderer::new);
    }
}
