package com.skyrpg.combat.client.mob;

import com.skyrpg.combat.mob.NetherMobs;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.ModelLayerRegistry;

/**
 * The Nether's monsters on their own 3D models (see NetherMobs): the Bone Giant and the Magma Tyrant. Each model's cubes
 * and textures come from a generator in tools/art (generate_bone_giant.py, generate_magma_tyrant.py); their emissive
 * layers use DwarvenMobRenderers.GlowLayer.
 */
public final class NetherMobRenderers {
    private NetherMobRenderers() {
    }

    public static void init() {
        ModelLayerRegistry.registerModelLayer(BoneGiantRenderer.LAYER, BoneGiantModel::createLayer);
        EntityRendererRegistry.register(NetherMobs.BONE_GIANT, BoneGiantRenderer::new);
        ModelLayerRegistry.registerModelLayer(MagmaTyrantRenderer.LAYER, MagmaTyrantModel::createLayer);
        EntityRendererRegistry.register(NetherMobs.MAGMA_TYRANT, MagmaTyrantRenderer::new);
    }
}
