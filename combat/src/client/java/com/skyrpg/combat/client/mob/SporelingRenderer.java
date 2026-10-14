package com.skyrpg.combat.client.mob;

import com.mojang.blaze3d.vertex.PoseStack;
import com.skyrpg.combat.SkyRpgCombat;
import com.skyrpg.combat.mob.SporelingEntity;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;

/** Draws the Sporeling with its own model and an emissive layer for the cap spots, gills and eyes, which pulse softly. */
public final class SporelingRenderer extends MobRenderer<SporelingEntity, SporelingRenderState, SporelingModel> {
    public static final ModelLayerLocation LAYER = new ModelLayerLocation(SkyRpgCombat.id("sporeling"), "main");
    private static final Identifier TEXTURE = SkyRpgCombat.id("textures/entity/mob/sporeling.png");
    private static final RenderType GLOW = RenderTypes.eyes(SkyRpgCombat.id("textures/entity/mob/sporeling_glow.png"));

    public SporelingRenderer(EntityRendererProvider.Context context) {
        super(context, new SporelingModel(context.bakeLayer(LAYER)), 0.4f);
        addLayer(new GlowLayer(this));
    }

    @Override
    public SporelingRenderState createRenderState() {
        return new SporelingRenderState();
    }

    @Override
    public void extractRenderState(SporelingEntity entity, SporelingRenderState state, float partialTick) {
        super.extractRenderState(entity, state, partialTick);
        state.attackTime = entity.getSwingAnimation(partialTick);
    }

    @Override
    public Identifier getTextureLocation(SporelingRenderState state) {
        return TEXTURE;
    }

    /** Fullbright pixels of the glow texture (vanilla's eyes render type), breathing a little. */
    private static final class GlowLayer extends RenderLayer<SporelingRenderState, SporelingModel> {
        GlowLayer(RenderLayerParent<SporelingRenderState, SporelingModel> parent) {
            super(parent);
        }

        @Override
        public void submit(PoseStack poseStack, SubmitNodeCollector collector, int light, SporelingRenderState state, float yRot, float xRot) {
            float pulse = 0.86f + 0.14f * Mth.sin(state.ageInTicks * 0.09f);
            collector.order(1).submitModel(getParentModel(), state, poseStack, GLOW, light, OverlayTexture.NO_OVERLAY, ARGB.white(pulse), null,
                    state.outlineColor);
        }
    }
}
