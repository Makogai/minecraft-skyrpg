package com.skyrpg.combat.client.mob;

import com.mojang.blaze3d.vertex.PoseStack;
import com.skyrpg.combat.SkyRpgCombat;
import com.skyrpg.combat.mob.EnderWyrmEntity;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.AABB;

/**
 * Draws the Ender Wyrm: the entity is its head, and the body segments are placed along the head's recent path (a trail
 * the entity records on the client, see EnderWyrmEntity.bodyPoints). The model is not rotated with the entity, and the
 * culling box is inflated to the whole body so it draws even with the head off screen. Emissive layers: the maw and
 * eyes (steady) and the void seams, which pulse and flare while it charges an attack.
 */
public final class EnderWyrmRenderer extends MobRenderer<EnderWyrmEntity, EnderWyrmRenderState, EnderWyrmModel> {
    public static final ModelLayerLocation LAYER = new ModelLayerLocation(SkyRpgCombat.id("ender_wyrm"), "main");
    private static final Identifier TEXTURE = DwarvenMobRenderers.texture("ender_wyrm");
    private static final Identifier GLOW = DwarvenMobRenderers.texture("ender_wyrm_glow");
    private static final Identifier PULSE = DwarvenMobRenderers.texture("ender_wyrm_pulse");

    public EnderWyrmRenderer(EntityRendererProvider.Context context) {
        super(context, new EnderWyrmModel(context.bakeLayer(LAYER)), 0f);
        addLayer(new DwarvenMobRenderers.GlowLayer<>(this, GLOW, PULSE, state -> {
            boolean charging = switch (state.mode) {
                case DIVE_WIND, BREATH_WIND, SUMMON_WIND, STORM_WIND -> true;
                default -> false;
            };
            return Math.min(1f, 0.55f + 0.45f * EnderWyrmModel.pulse(state.ageInTicks, state.phase) + (charging ? 0.35f : 0f));
        }));
    }

    @Override
    public EnderWyrmRenderState createRenderState() {
        return new EnderWyrmRenderState();
    }

    @Override
    public void extractRenderState(EnderWyrmEntity entity, EnderWyrmRenderState state, float partialTick) {
        super.extractRenderState(entity, state, partialTick);
        state.body = entity.bodyPoints(partialTick);
        state.mode = entity.getMode();
        state.phase = entity.getPhase();
    }

    @Override
    public Identifier getTextureLocation(EnderWyrmRenderState state) {
        return TEXTURE;
    }

    /** The parts carry their own world-aligned placement: no rotation with the entity (and no death flop). */
    @Override
    protected void setupRotations(EnderWyrmRenderState state, PoseStack poseStack, float bodyRot, float scale) {
    }

    /** The body trails up to about 20 blocks behind the head, in any direction. */
    @Override
    protected AABB getBoundingBoxForCulling(EnderWyrmEntity entity, float partialTick) {
        return super.getBoundingBoxForCulling(entity, partialTick).inflate(EnderWyrmEntity.BODY_LENGTH + 4);
    }
}
