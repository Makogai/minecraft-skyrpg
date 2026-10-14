package com.skyrpg.combat.client.mob;

import com.mojang.blaze3d.vertex.PoseStack;
import com.skyrpg.combat.SkyRpgCombat;
import com.skyrpg.combat.mob.FireBeetleEntity;
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

/**
 * Draws the Fire Beetle with its own model and an emissive layer for the magma cracks, eyes and hot mandible tips.
 * The cracks smoulder (a slow throb) and flare up during the charge wind-up.
 */
public final class FireBeetleRenderer extends MobRenderer<FireBeetleEntity, FireBeetleRenderState, FireBeetleModel> {
    public static final ModelLayerLocation LAYER = new ModelLayerLocation(SkyRpgCombat.id("fire_beetle"), "main");
    private static final Identifier TEXTURE = SkyRpgCombat.id("textures/entity/mob/fire_beetle.png");
    private static final RenderType GLOW = RenderTypes.eyes(SkyRpgCombat.id("textures/entity/mob/fire_beetle_glow.png"));
    private static final float WINDUP = FireBeetleEntity.WINDUP_TICKS;
    private static final float DASH = FireBeetleEntity.DASH_TICKS;
    private static final float RECOVER = FireBeetleEntity.RECOVER_TICKS;
    private static final float TOTAL = FireBeetleEntity.CHARGE_POSE_TICKS;

    public FireBeetleRenderer(EntityRendererProvider.Context context) {
        super(context, new FireBeetleModel(context.bakeLayer(LAYER)), 0.9f);
        addLayer(new GlowLayer(this));
    }

    @Override
    public FireBeetleRenderState createRenderState() {
        return new FireBeetleRenderState();
    }

    @Override
    public void extractRenderState(FireBeetleEntity entity, FireBeetleRenderState state, float partialTick) {
        super.extractRenderState(entity, state, partialTick);
        state.attackTime = entity.getSwingAnimation(partialTick);
        // The charge countdown (synced) as pose weights: ease into the wind-up, cross-fade into the dash, fade out in
        // the recovery. A dash cut short (a hit, a wall) jumps the countdown to the recovery, which starts from full.
        int ticks = entity.getChargeTicks();
        float windup = 0f;
        float dash = 0f;
        if (ticks > 0) {
            float elapsed = TOTAL - Math.max(0.01f, ticks - partialTick);
            if (elapsed < WINDUP) {
                windup = FireBeetleModel.smooth(elapsed / 5f);
            } else if (elapsed < WINDUP + DASH) {
                float d = elapsed - WINDUP;
                windup = 1f - FireBeetleModel.smooth(d / 3f);
                dash = FireBeetleModel.smooth(d / 2f);
            } else {
                dash = 1f - FireBeetleModel.smooth((elapsed - WINDUP - DASH) / RECOVER);
            }
        }
        state.windup = windup;
        state.dash = dash;
    }

    @Override
    public Identifier getTextureLocation(FireBeetleRenderState state) {
        return TEXTURE;
    }

    @Override
    protected float getFlipDegrees() {
        return 180f; // like a spider: it rolls onto its back when it dies
    }

    /** Fullbright pixels of the glow texture (vanilla's eyes render type): smouldering, flaring in the wind-up. */
    private static final class GlowLayer extends RenderLayer<FireBeetleRenderState, FireBeetleModel> {
        GlowLayer(RenderLayerParent<FireBeetleRenderState, FireBeetleModel> parent) {
            super(parent);
        }

        @Override
        public void submit(PoseStack poseStack, SubmitNodeCollector collector, int light, FireBeetleRenderState state, float yRot, float xRot) {
            float t = state.ageInTicks;
            float smoulder = 0.8f + 0.1f * Mth.sin(t * 0.13f) + 0.04f * Mth.sin(t * 0.71f);
            float flare = Math.max(state.windup, state.dash * 0.7f);
            float alpha = Mth.clamp(Mth.lerp(flare, smoulder, 0.94f + 0.06f * Mth.sin(t * 2.3f)), 0f, 1f);
            collector.order(1).submitModel(getParentModel(), state, poseStack, GLOW, light, OverlayTexture.NO_OVERLAY, ARGB.white(alpha), null,
                    state.outlineColor);
        }
    }
}
