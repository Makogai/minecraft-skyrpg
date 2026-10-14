package com.skyrpg.combat.client.slayer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.skyrpg.combat.SkyRpgCombat;
import com.skyrpg.combat.slayer.SlayerEntities;
import com.skyrpg.combat.slayer.boss.VoidStalkerEntity;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.ModelLayerRegistry;
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
import net.minecraft.util.Mth;

/**
 * Draws the Void Stalker with its own wraith model, one texture per tier, and an emissive layer for the slit, core,
 * cracks and shards. While phased out the whole body is drawn translucent and flickers. There is no carried-block
 * layer, so the enderman's block is never drawn.
 */
public final class VoidStalkerBossRenderer extends MobRenderer<VoidStalkerEntity, VoidStalkerRenderState, VoidStalkerModel> {
    public static final ModelLayerLocation LAYER = new ModelLayerLocation(SkyRpgCombat.id("void_stalker"), "main");

    private static final Identifier[] TEXTURES = new Identifier[4];
    private static final Identifier[] GLOWS = new Identifier[4];

    static {
        for (int i = 0; i < 4; i++) {
            TEXTURES[i] = texture("void_stalker_model_t" + (i + 1));
            GLOWS[i] = texture("void_stalker_model_t" + (i + 1) + "_glow");
        }
    }

    public VoidStalkerBossRenderer(EntityRendererProvider.Context context) {
        super(context, new VoidStalkerModel(context.bakeLayer(LAYER)), 0.5f);
        addLayer(new GlowLayer(this));
    }

    /** Registers the model layer and the renderer for the Void Stalker (called from the client initialiser). */
    public static void register() {
        ModelLayerRegistry.registerModelLayer(LAYER, VoidStalkerModel::createLayer);
        EntityRendererRegistry.register(SlayerEntities.VOID_STALKER, VoidStalkerBossRenderer::new);
    }

    private static Identifier texture(String name) {
        return SkyRpgCombat.id("textures/entity/slayer/" + name + ".png");
    }

    @Override
    public VoidStalkerRenderState createRenderState() {
        return new VoidStalkerRenderState();
    }

    @Override
    public void extractRenderState(VoidStalkerEntity entity, VoidStalkerRenderState state, float partialTick) {
        super.extractRenderState(entity, state, partialTick);
        state.tier = entity.getTier();
        state.channelTicks = entity.getChannelTicks() > 0 ? Math.max(0.01f, entity.getChannelTicks() - partialTick) : 0f;
        state.channel = entity.getChannelBlend(partialTick);
        state.phase = entity.getPhaseBlend(partialTick);
        state.shield = entity.getShieldBlend(partialTick);
        state.attackTime = entity.getSwingAnimation(partialTick);
        // phased out: a ghostly, flickering body
        float flicker = 0.5f + 0.5f * Mth.sin(state.ageInTicks * 1.3f) * Mth.sin(state.ageInTicks * 0.37f);
        state.bodyAlpha = 1f - state.phase * (0.5f + 0.2f * flicker);
    }

    private static int tierIndex(VoidStalkerRenderState state) {
        return Mth.clamp(state.tier, 1, 4) - 1;
    }

    @Override
    public Identifier getTextureLocation(VoidStalkerRenderState state) {
        return TEXTURES[tierIndex(state)];
    }

    @Override
    protected RenderType getRenderType(VoidStalkerRenderState state, boolean bodyVisible, boolean translucent, boolean glowing) {
        RenderType type = super.getRenderType(state, bodyVisible, translucent, glowing);
        if (type != null && state.bodyAlpha < 0.99f) {
            return RenderTypes.entityTranslucent(getTextureLocation(state));
        }
        return type;
    }

    @Override
    protected int getModelTint(VoidStalkerRenderState state) {
        if (state.bodyAlpha < 0.99f) {
            return (Mth.clamp((int) (state.bodyAlpha * 255f), 0, 255) << 24) | 0xFFFFFF;
        }
        return super.getModelTint(state);
    }

    /** Fullbright pixels of the glow texture, drawn with the same render type vanilla eyes use. */
    private static final class GlowLayer extends RenderLayer<VoidStalkerRenderState, VoidStalkerModel> {
        GlowLayer(RenderLayerParent<VoidStalkerRenderState, VoidStalkerModel> parent) {
            super(parent);
        }

        @Override
        public void submit(PoseStack poseStack, SubmitNodeCollector collector, int light, VoidStalkerRenderState state, float yRot, float xRot) {
            RenderType type = RenderTypes.eyes(GLOWS[tierIndex(state)]);
            if (state.bodyAlpha < 0.99f) {
                int tint = (Mth.clamp((int) (state.bodyAlpha * 255f), 0, 255) << 24) | 0xFFFFFF;
                collector.order(1).submitModel(getParentModel(), state, poseStack, type, light, OverlayTexture.NO_OVERLAY, tint, null, state.outlineColor);
            } else {
                collector.order(1).submitModel(getParentModel(), state, poseStack, type, light, OverlayTexture.NO_OVERLAY, state.outlineColor);
            }
        }
    }
}
