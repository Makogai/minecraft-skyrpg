package com.skyrpg.mounts.client.mob;

import com.skyrpg.mounts.GriffinColor;
import com.skyrpg.mounts.GriffinEntity;
import com.skyrpg.mounts.SkyRpgMounts;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;

/** Draws the griffin with its own model and one of ten feather-colour textures, tinted and glowing for the rarer ones. */
public final class GriffinRenderer extends MobRenderer<GriffinEntity, GriffinRenderState, GriffinModel> {
    public static final ModelLayerLocation LAYER = new ModelLayerLocation(SkyRpgMounts.id("griffin"), "main");
    private static final Identifier[] TEXTURES = new Identifier[GriffinColor.values().length];

    static {
        for (GriffinColor color : GriffinColor.values()) {
            TEXTURES[color.ordinal()] = texture(color);
        }
    }

    public GriffinRenderer(EntityRendererProvider.Context context) {
        super(context, new GriffinModel(context.bakeLayer(LAYER)), 0.8f);
        addLayer(new GriffinGlowLayer(this));
    }

    /** The glow texture of a rarer colour: layer 0 (eyes, some stars) or 1 (wing tips, trim, other stars). */
    public static Identifier glowTexture(GriffinColor color, int layer) {
        return SkyRpgMounts.id("textures/entity/griffin/griffin_" + color.id() + (layer == 0 ? "_glow" : "_glow2") + ".png");
    }

    @Override
    protected int getModelTint(GriffinRenderState state) {
        return GriffinFx.tint(state.color, state.fxTicks);
    }

    public static Identifier texture(GriffinColor color) {
        return SkyRpgMounts.id("textures/entity/griffin/griffin_" + color.id() + ".png");
    }

    @Override
    public GriffinRenderState createRenderState() {
        return new GriffinRenderState();
    }

    @Override
    public void extractRenderState(GriffinEntity entity, GriffinRenderState state, float partialTick) {
        super.extractRenderState(entity, state, partialTick);
        state.color = entity.getColor();
        state.fly = Mth.lerp(partialTick, entity.flyAmountO, entity.flyAmount);
        state.glide = Mth.lerp(partialTick, entity.glideAmountO, entity.glideAmount);
        state.ridden = entity.isVehicle();
        state.fxTicks = state.ageInTicks + (entity.getId() % 97) * 31f;
    }

    @Override
    public Identifier getTextureLocation(GriffinRenderState state) {
        return TEXTURES[state.color.ordinal()];
    }
}
