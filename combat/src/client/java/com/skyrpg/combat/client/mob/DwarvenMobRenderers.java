package com.skyrpg.combat.client.mob;

import com.mojang.blaze3d.vertex.PoseStack;
import com.skyrpg.combat.SkyRpgCombat;
import com.skyrpg.combat.mob.DwarvenMobs;
import java.util.function.ToDoubleFunction;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.ModelLayerRegistry;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;

/**
 * The Dwarven Halls' monsters on their own 3D models (see DwarvenMobs): the Dwarven Construct and the Iron Thane. Each
 * model's cubes and textures come from a generator in tools/art (generate_dwarven_construct.py, generate_iron_thane.py).
 */
public final class DwarvenMobRenderers {
    private DwarvenMobRenderers() {
    }

    public static void init() {
        ModelLayerRegistry.registerModelLayer(DwarvenConstructRenderer.LAYER, DwarvenConstructModel::createLayer);
        EntityRendererRegistry.register(DwarvenMobs.DWARVEN_CONSTRUCT, DwarvenConstructRenderer::new);
        ModelLayerRegistry.registerModelLayer(IronThaneRenderer.LAYER, IronThaneModel::createLayer);
        EntityRendererRegistry.register(DwarvenMobs.IRON_THANE, IronThaneRenderer::new);
    }

    static Identifier texture(String name) {
        return SkyRpgCombat.id("textures/entity/mob/" + name + ".png");
    }

    /**
     * The emissive layer of a dwarven mob, drawn fullbright with the render type vanilla eyes use: a steady glow texture
     * (eyes, runes) and a pulse texture (cores, gems, embers) whose brightness follows {@code pulse} (0 to 1).
     */
    static final class GlowLayer<S extends LivingEntityRenderState, M extends EntityModel<? super S>> extends RenderLayer<S, M> {
        private final RenderType glow;
        private final RenderType pulse;
        private final ToDoubleFunction<S> brightness;

        GlowLayer(RenderLayerParent<S, M> parent, Identifier glow, Identifier pulse, ToDoubleFunction<S> brightness) {
            super(parent);
            this.glow = RenderTypes.eyes(glow);
            this.pulse = RenderTypes.eyes(pulse);
            this.brightness = brightness;
        }

        @Override
        public void submit(PoseStack poseStack, SubmitNodeCollector collector, int light, S state, float yRot, float xRot) {
            collector.order(1).submitModel(getParentModel(), state, poseStack, glow, light, OverlayTexture.NO_OVERLAY, state.outlineColor);
            int k = Mth.clamp((int) (255 * brightness.applyAsDouble(state)), 0, 255);
            int color = 0xFF000000 | k << 16 | k << 8 | k;
            collector.order(1).submitModel(getParentModel(), state, poseStack, pulse, light, OverlayTexture.NO_OVERLAY, color, null,
                    state.outlineColor);
        }
    }
}
