package com.skyrpg.combat.client.mob;

import com.skyrpg.combat.SkyRpgCombat;
import com.skyrpg.combat.mob.BoneGiantEntity;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.AABB;

/**
 * Draws the Bone Giant with its own model and an emissive layer: steady soul-blue eyes, the glowing crack in its skull and
 * its spine's joints, and the soul fire in its ribcage flickering (see DwarvenMobRenderers.GlowLayer).
 */
public final class BoneGiantRenderer extends MobRenderer<BoneGiantEntity, BoneGiantRenderState, BoneGiantModel> {
    public static final ModelLayerLocation LAYER = new ModelLayerLocation(SkyRpgCombat.id("bone_giant"), "main");
    private static final Identifier TEXTURE = DwarvenMobRenderers.texture("bone_giant");
    private static final Identifier GLOW = DwarvenMobRenderers.texture("bone_giant_glow");
    private static final Identifier PULSE = DwarvenMobRenderers.texture("bone_giant_pulse");

    public BoneGiantRenderer(EntityRendererProvider.Context context) {
        super(context, new BoneGiantModel(context.bakeLayer(LAYER)), 1.0f);
        addLayer(new DwarvenMobRenderers.GlowLayer<>(this, GLOW, PULSE, state -> 0.55f + 0.45f * BoneGiantModel.flicker(state.ageInTicks)));
    }

    @Override
    public BoneGiantRenderState createRenderState() {
        return new BoneGiantRenderState();
    }

    @Override
    public void extractRenderState(BoneGiantEntity entity, BoneGiantRenderState state, float partialTick) {
        super.extractRenderState(entity, state, partialTick);
        state.swingTicks = entity.getSwingTicks() > 0 ? Math.max(0.01f, entity.getSwingTicks() - partialTick) : 0f;
        state.quakeTicks = entity.getQuakeTicks() > 0 ? Math.max(0.01f, entity.getQuakeTicks() - partialTick) : 0f;
    }

    @Override
    public Identifier getTextureLocation(BoneGiantRenderState state) {
        return TEXTURE;
    }

    /** The club trails well outside the hitbox, and goes high overhead when it swings. */
    @Override
    protected AABB getBoundingBoxForCulling(BoneGiantEntity entity, float partialTick) {
        return super.getBoundingBoxForCulling(entity, partialTick).inflate(1.8 * entity.getScale());
    }
}
