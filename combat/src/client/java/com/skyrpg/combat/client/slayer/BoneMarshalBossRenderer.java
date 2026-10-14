package com.skyrpg.combat.client.slayer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.skyrpg.combat.SkyRpgCombat;
import com.skyrpg.combat.slayer.SlayerEntities;
import com.skyrpg.combat.slayer.boss.BoneMarshalEntity;
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
import net.minecraft.util.Mth;

/**
 * Draws the Bone Marshal with its own skeletal war general model, one texture per tier and an emissive layer for
 * eye sockets, chest fire, runes and the bowstring. No held-item layer is added: the greatbow is part of the model,
 * so the vanilla bow in its hand is never drawn.
 * <p>
 * Call {@link #register()} from the client init (SlayerRenderers) in place of the vanilla skeleton renderer.
 */
public final class BoneMarshalBossRenderer extends MobRenderer<BoneMarshalEntity, BoneMarshalRenderState, BoneMarshalModel> {
    public static final ModelLayerLocation LAYER = new ModelLayerLocation(SkyRpgCombat.id("bone_marshal"), "main");

    private static final Identifier[] TEXTURES = new Identifier[4];
    private static final Identifier[] GLOWS = new Identifier[4];

    static {
        for (int i = 0; i < 4; i++) {
            TEXTURES[i] = texture("bone_marshal_model_t" + (i + 1));
            GLOWS[i] = texture("bone_marshal_model_t" + (i + 1) + "_glow");
        }
    }

    /** Registers the model layer and the renderer of the Bone Marshal. */
    public static void register() {
        ModelLayerRegistry.registerModelLayer(LAYER, BoneMarshalModel::createLayer);
        EntityRendererRegistry.register(SlayerEntities.BONE_MARSHAL, BoneMarshalBossRenderer::new);
    }

    public BoneMarshalBossRenderer(EntityRendererProvider.Context context) {
        super(context, new BoneMarshalModel(context.bakeLayer(LAYER)), 0.6f);
        addLayer(new GlowLayer(this));
    }

    private static Identifier texture(String name) {
        return SkyRpgCombat.id("textures/entity/slayer/" + name + ".png");
    }

    private static int index(BoneMarshalRenderState state) {
        return Mth.clamp(state.tier, 1, 4) - 1;
    }

    @Override
    public BoneMarshalRenderState createRenderState() {
        return new BoneMarshalRenderState();
    }

    @Override
    public void extractRenderState(BoneMarshalEntity entity, BoneMarshalRenderState state, float partialTick) {
        super.extractRenderState(entity, state, partialTick);
        state.tier = entity.getTier();
        state.drawTicks = entity.getDrawTicks() > 0 ? Math.max(0.01f, entity.getDrawTicks() - partialTick) : 0f;
        state.drawTotal = Math.max(1, entity.getDrawTotal());
        state.shielded = entity.isShielded();
        state.bannerPlanted = entity.isBannerPlanted();
        state.aiming = entity.isAggressive();
        state.usingItem = entity.isUsingItem();
        state.ticksUsingItem = entity.isUsingItem() ? entity.getTicksUsingItem(partialTick) : 0f;
        state.attackTime = entity.getSwingAnimation(partialTick);
    }

    @Override
    public Identifier getTextureLocation(BoneMarshalRenderState state) {
        return TEXTURES[index(state)];
    }

    @Override
    protected void scale(BoneMarshalRenderState state, PoseStack poseStack) {
        super.scale(state, poseStack);
        poseStack.scale(1.2f, 1.2f, 1.2f);
    }

    /** Fullbright pixels of the glow texture, drawn with the render type vanilla eyes use. */
    private static final class GlowLayer extends RenderLayer<BoneMarshalRenderState, BoneMarshalModel> {
        GlowLayer(RenderLayerParent<BoneMarshalRenderState, BoneMarshalModel> parent) {
            super(parent);
        }

        @Override
        public void submit(PoseStack poseStack, SubmitNodeCollector collector, int light, BoneMarshalRenderState state, float yRot, float xRot) {
            collector.order(1).submitModel(getParentModel(), state, poseStack, RenderTypes.eyes(GLOWS[index(state)]),
                    light, OverlayTexture.NO_OVERLAY, state.outlineColor);
        }
    }
}
