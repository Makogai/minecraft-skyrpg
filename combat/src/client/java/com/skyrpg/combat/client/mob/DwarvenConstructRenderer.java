package com.skyrpg.combat.client.mob;

import com.skyrpg.combat.SkyRpgCombat;
import com.skyrpg.combat.mob.DwarvenConstructEntity;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.Identifier;

/**
 * Draws the Dwarven Construct with its own model and an emissive layer: steady eye slits, runes and stack vents, and the
 * rune core pulsing (see DwarvenMobRenderers.GlowLayer). Scaled-up constructs (the Forgeguard) need nothing special: the
 * scale attribute scales the whole model, and the entity slows its walk cycle to match.
 */
public final class DwarvenConstructRenderer extends MobRenderer<DwarvenConstructEntity, DwarvenConstructRenderState, DwarvenConstructModel> {
    public static final ModelLayerLocation LAYER = new ModelLayerLocation(SkyRpgCombat.id("dwarven_construct"), "main");
    private static final Identifier TEXTURE = DwarvenMobRenderers.texture("dwarven_construct");
    private static final Identifier GLOW = DwarvenMobRenderers.texture("dwarven_construct_glow");
    private static final Identifier PULSE = DwarvenMobRenderers.texture("dwarven_construct_pulse");

    public DwarvenConstructRenderer(EntityRendererProvider.Context context) {
        super(context, new DwarvenConstructModel(context.bakeLayer(LAYER)), 0.6f);
        addLayer(new DwarvenMobRenderers.GlowLayer<>(this, GLOW, PULSE, state -> 0.55f + 0.45f * DwarvenConstructModel.pulse(state.ageInTicks)));
    }

    @Override
    public DwarvenConstructRenderState createRenderState() {
        return new DwarvenConstructRenderState();
    }

    @Override
    public void extractRenderState(DwarvenConstructEntity entity, DwarvenConstructRenderState state, float partialTick) {
        super.extractRenderState(entity, state, partialTick);
        state.attackTime = entity.getSwingAnimation(partialTick);
    }

    @Override
    public Identifier getTextureLocation(DwarvenConstructRenderState state) {
        return TEXTURE;
    }
}
