package com.skyrpg.combat.client.slayer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.skyrpg.combat.SkyRpgCombat;
import com.skyrpg.combat.slayer.SlayerEntities;
import com.skyrpg.combat.slayer.boss.BroodmotherEntity;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.ModelLayerRegistry;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;

/**
 * Draws the Broodmother with its own spider-queen model: one texture per tier and an emissive layer for the eyes,
 * venom and markings. Call {@link #register()} from the client init (SlayerRenderers.init()).
 */
public final class BroodmotherBossRenderer extends MobRenderer<BroodmotherEntity, BroodmotherRenderState, BroodmotherModel> {
    public static final ModelLayerLocation LAYER = new ModelLayerLocation(SkyRpgCombat.id("broodmother"), "main");

    private static final Identifier[] TEXTURES = new Identifier[4];
    private static final Identifier[] GLOWS = new Identifier[4];

    static {
        for (int i = 0; i < 4; i++) {
            TEXTURES[i] = texture("broodmother_model_t" + (i + 1));
            GLOWS[i] = texture("broodmother_model_t" + (i + 1) + "_glow");
        }
    }

    public BroodmotherBossRenderer(EntityRendererProvider.Context context) {
        super(context, new BroodmotherModel(context.bakeLayer(LAYER)), 1.1f);
        addLayer(new GlowLayer(this));
    }

    /** Registers the model layer and the renderer for {@link SlayerEntities#BROODMOTHER}. */
    public static void register() {
        ModelLayerRegistry.registerModelLayer(LAYER, BroodmotherModel::createLayer);
        EntityRendererRegistry.register(SlayerEntities.BROODMOTHER, BroodmotherBossRenderer::new);
    }

    private static Identifier texture(String name) {
        return SkyRpgCombat.id("textures/entity/slayer/" + name + ".png");
    }

    private static int index(BroodmotherRenderState state) {
        return Math.max(0, Math.min(3, state.tier - 1));
    }

    @Override
    public BroodmotherRenderState createRenderState() {
        return new BroodmotherRenderState();
    }

    @Override
    public void extractRenderState(BroodmotherEntity entity, BroodmotherRenderState state, float partialTick) {
        super.extractRenderState(entity, state, partialTick);
        state.tier = entity.getTier();
        state.pounceTicks = entity.getPounceTicks() > 0 ? Math.max(0.01f, entity.getPounceTicks() - partialTick) : 0f;
        state.cocoon = entity.getCocoonProgress(partialTick);
        state.shield = entity.getShieldProgress(partialTick);
        state.attackTime = entity.getSwingAnimation(partialTick);
    }

    @Override
    public Identifier getTextureLocation(BroodmotherRenderState state) {
        return TEXTURES[index(state)];
    }

    @Override
    protected float getFlipDegrees() {
        return 180f; // like the vanilla spider: it rolls onto its back when it dies
    }

    @Override
    protected void scale(BroodmotherRenderState state, PoseStack poseStack) {
        super.scale(state, poseStack);
        poseStack.scale(1.25f, 1.25f, 1.25f); // a bit larger than a vanilla spider; the server scales further by tier
    }

    /** Fullbright pixels of the glow texture, drawn with the same render type vanilla eyes use. */
    private static final class GlowLayer extends RenderLayer<BroodmotherRenderState, BroodmotherModel> {
        GlowLayer(RenderLayerParent<BroodmotherRenderState, BroodmotherModel> parent) {
            super(parent);
        }

        @Override
        public void submit(PoseStack poseStack, SubmitNodeCollector collector, int light, BroodmotherRenderState state, float yRot, float xRot) {
            collector.order(1).submitModel(getParentModel(), state, poseStack, RenderTypes.eyes(GLOWS[index(state)]),
                    light, OverlayTexture.NO_OVERLAY, state.outlineColor);
        }
    }
}
