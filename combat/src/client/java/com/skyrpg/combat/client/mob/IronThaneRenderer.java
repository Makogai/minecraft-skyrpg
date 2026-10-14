package com.skyrpg.combat.client.mob;

import com.skyrpg.combat.SkyRpgCombat;
import com.skyrpg.combat.mob.IronThaneEntity;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.AABB;

/**
 * Draws the Iron Thane with its own model and an emissive layer: steady eyes and runes, and the forge core, crown gems,
 * stack embers and the hammer's hot faces pulsing together (see DwarvenMobRenderers.GlowLayer). No held-item layer: the
 * hammer is part of the model, so anything the boss JSON puts in its hands isn't drawn.
 */
public final class IronThaneRenderer extends MobRenderer<IronThaneEntity, IronThaneRenderState, IronThaneModel> {
    public static final ModelLayerLocation LAYER = new ModelLayerLocation(SkyRpgCombat.id("iron_thane"), "main");
    private static final Identifier TEXTURE = DwarvenMobRenderers.texture("iron_thane");
    private static final Identifier GLOW = DwarvenMobRenderers.texture("iron_thane_glow");
    private static final Identifier PULSE = DwarvenMobRenderers.texture("iron_thane_pulse");

    public IronThaneRenderer(EntityRendererProvider.Context context) {
        super(context, new IronThaneModel(context.bakeLayer(LAYER)), 1.1f);
        addLayer(new DwarvenMobRenderers.GlowLayer<>(this, GLOW, PULSE, state -> 0.6f + 0.4f * IronThaneModel.pulse(state.ageInTicks)));
    }

    @Override
    public IronThaneRenderState createRenderState() {
        return new IronThaneRenderState();
    }

    @Override
    public void extractRenderState(IronThaneEntity entity, IronThaneRenderState state, float partialTick) {
        super.extractRenderState(entity, state, partialTick);
        state.slamTicks = entity.getSlamTicks() > 0 ? Math.max(0.01f, entity.getSlamTicks() - partialTick) : 0f;
    }

    @Override
    public Identifier getTextureLocation(IronThaneRenderState state) {
        return TEXTURE;
    }

    /** The hammer reaches well outside the hitbox (overhead, and on the ground in front when it slams). */
    @Override
    protected AABB getBoundingBoxForCulling(IronThaneEntity entity, float partialTick) {
        return super.getBoundingBoxForCulling(entity, partialTick).inflate(2.5 * entity.getScale());
    }
}
