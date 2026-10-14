package com.skyrpg.combat.client.slayer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.skyrpg.combat.SkyRpgCombat;
import com.skyrpg.combat.slayer.boss.GraveTyrantEntity;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;

/** Draws the Grave Tyrant with its own model, one skin per tier (I-IV), and an emissive layer for eyes, heart and runes. */
public final class GraveTyrantRenderer extends MobRenderer<GraveTyrantEntity, GraveTyrantRenderState, GraveTyrantModel> {
    public static final ModelLayerLocation LAYER = new ModelLayerLocation(SkyRpgCombat.id("grave_tyrant"), "main");

    /** Index 0..3 = tier I..IV: skin texture and its emissive layer. */
    private static final Identifier[] TEXTURES = {
            texture("grave_tyrant_model_t1"), texture("grave_tyrant_model_t2"),
            texture("grave_tyrant_model_t3"), texture("grave_tyrant_model_t4")};
    private static final Identifier[] GLOWS = {
            texture("grave_tyrant_model_t1_glow"), texture("grave_tyrant_model_t2_glow"),
            texture("grave_tyrant_model_t3_glow"), texture("grave_tyrant_model_t4_glow")};

    public GraveTyrantRenderer(EntityRendererProvider.Context context) {
        super(context, new GraveTyrantModel(context.bakeLayer(LAYER)), 1.5f);
        addLayer(new GlowLayer(this));
    }

    private static int tierIndex(int tier) {
        return Math.max(1, Math.min(4, tier)) - 1;
    }

    private static Identifier texture(String name) {
        return SkyRpgCombat.id("textures/entity/slayer/" + name + ".png");
    }

    @Override
    public GraveTyrantRenderState createRenderState() {
        return new GraveTyrantRenderState();
    }

    @Override
    public void extractRenderState(GraveTyrantEntity entity, GraveTyrantRenderState state, float partialTick) {
        super.extractRenderState(entity, state, partialTick);
        state.tier = entity.getTier();
        state.slamTicks = entity.getSlamTicks() > 0 ? Math.max(0.01f, entity.getSlamTicks() - partialTick) : 0f;
        state.enraged = entity.isEnraged();
        state.attackTime = entity.getSwingAnimation(partialTick);
    }

    @Override
    public Identifier getTextureLocation(GraveTyrantRenderState state) {
        return TEXTURES[tierIndex(state.tier)];
    }

    @Override
    protected void scale(GraveTyrantRenderState state, PoseStack poseStack) {
        super.scale(state, poseStack);
        poseStack.scale(1.15f, 1.15f, 1.15f);
    }

    /** Fullbright pixels of the glow texture, drawn with the same render type vanilla eyes use. */
    private static final class GlowLayer extends RenderLayer<GraveTyrantRenderState, GraveTyrantModel> {
        GlowLayer(RenderLayerParent<GraveTyrantRenderState, GraveTyrantModel> parent) {
            super(parent);
        }

        @Override
        public void submit(PoseStack poseStack, SubmitNodeCollector collector, int light, GraveTyrantRenderState state, float yRot, float xRot) {
            collector.order(1).submitModel(getParentModel(), state, poseStack, RenderTypes.eyes(GLOWS[tierIndex(state.tier)]),
                    light, OverlayTexture.NO_OVERLAY, state.outlineColor);
        }
    }
}
