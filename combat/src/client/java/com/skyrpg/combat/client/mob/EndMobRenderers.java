package com.skyrpg.combat.client.mob;

import com.skyrpg.combat.mob.EndMobs;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.ModelLayerRegistry;

/** The End's monsters on their own 3D models (see EndMobs): the Ender Wyrm. Cubes and textures come from tools/art/generate_ender_wyrm.py. */
public final class EndMobRenderers {
    private EndMobRenderers() {
    }

    public static void init() {
        ModelLayerRegistry.registerModelLayer(EnderWyrmRenderer.LAYER, EnderWyrmModel::createLayer);
        EntityRendererRegistry.register(EndMobs.ENDER_WYRM, EnderWyrmRenderer::new);
    }
}
