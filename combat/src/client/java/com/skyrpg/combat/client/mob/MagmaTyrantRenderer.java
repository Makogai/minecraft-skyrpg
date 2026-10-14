package com.skyrpg.combat.client.mob;

import com.skyrpg.combat.SkyRpgCombat;
import com.skyrpg.combat.mob.MagmaTyrantEntity;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.AABB;

/**
 * Draws the Magma Tyrant with its own model and an emissive layer: steady burning eyes and crown flames, and the molten
 * core, every crack, the vent tips and the lava pulsing together (see DwarvenMobRenderers.GlowLayer). No held-item
 * layer: anything the boss JSON puts in its hands isn't drawn.
 */
public final class MagmaTyrantRenderer extends MobRenderer<MagmaTyrantEntity, MagmaTyrantRenderState, MagmaTyrantModel> {
    public static final ModelLayerLocation LAYER = new ModelLayerLocation(SkyRpgCombat.id("magma_tyrant"), "main");
    private static final Identifier TEXTURE = DwarvenMobRenderers.texture("magma_tyrant");
    private static final Identifier GLOW = DwarvenMobRenderers.texture("magma_tyrant_glow");
    private static final Identifier PULSE = DwarvenMobRenderers.texture("magma_tyrant_pulse");

    public MagmaTyrantRenderer(EntityRendererProvider.Context context) {
        super(context, new MagmaTyrantModel(context.bakeLayer(LAYER)), 1.3f);
        addLayer(new DwarvenMobRenderers.GlowLayer<>(this, GLOW, PULSE, state -> 0.6f + 0.4f * MagmaTyrantModel.pulse(state.ageInTicks)));
    }

    @Override
    public MagmaTyrantRenderState createRenderState() {
        return new MagmaTyrantRenderState();
    }

    @Override
    public void extractRenderState(MagmaTyrantEntity entity, MagmaTyrantRenderState state, float partialTick) {
        super.extractRenderState(entity, state, partialTick);
        state.slamTicks = entity.getSlamTicks() > 0 ? Math.max(0.01f, entity.getSlamTicks() - partialTick) : 0f;
    }

    @Override
    public Identifier getTextureLocation(MagmaTyrantRenderState state) {
        return TEXTURE;
    }

    /** The fists reach well outside the hitbox (overhead, and on the ground in front when it slams). */
    @Override
    protected AABB getBoundingBoxForCulling(MagmaTyrantEntity entity, float partialTick) {
        return super.getBoundingBoxForCulling(entity, partialTick).inflate(2.5 * entity.getScale());
    }
}
