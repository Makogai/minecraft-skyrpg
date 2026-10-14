package com.skyrpg.mounts.client.mob;

import com.mojang.blaze3d.vertex.PoseStack;
import com.skyrpg.mounts.GriffinColor;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.Mth;

/** Draws the rarer griffins' glow textures fullbright (eyes, wing tips, trim, stars), their brightness following {@link GriffinFx}. */
final class GriffinGlowLayer extends RenderLayer<GriffinRenderState, GriffinModel> {
    private static final RenderType[][] TYPES = new RenderType[GriffinColor.values().length][2];

    static {
        for (GriffinColor color : GriffinColor.values()) {
            if (GriffinFx.glows(color)) {
                TYPES[color.ordinal()][0] = RenderTypes.eyes(GriffinRenderer.glowTexture(color, 0));
                TYPES[color.ordinal()][1] = RenderTypes.eyes(GriffinRenderer.glowTexture(color, 1));
            }
        }
    }

    GriffinGlowLayer(RenderLayerParent<GriffinRenderState, GriffinModel> parent) {
        super(parent);
    }

    @Override
    public void submit(PoseStack poseStack, SubmitNodeCollector collector, int light, GriffinRenderState state, float yRot, float xRot) {
        if (state.isInvisible || !GriffinFx.glows(state.color)) {
            return;
        }
        int tint = GriffinFx.glowTint(state.color, state.fxTicks);
        for (int layer = 0; layer < 2; layer++) {
            float b = GriffinFx.brightness(state.color, layer, state.fxTicks);
            int r = Mth.clamp((int) ((tint >> 16 & 255) * b), 0, 255);
            int g = Mth.clamp((int) ((tint >> 8 & 255) * b), 0, 255);
            int bl = Mth.clamp((int) ((tint & 255) * b), 0, 255);
            collector.order(1).submitModel(getParentModel(), state, poseStack, TYPES[state.color.ordinal()][layer], light, OverlayTexture.NO_OVERLAY,
                    0xFF000000 | r << 16 | g << 8 | bl, null, state.outlineColor);
        }
    }
}
