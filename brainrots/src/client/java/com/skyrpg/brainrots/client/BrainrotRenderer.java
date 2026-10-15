package com.skyrpg.brainrots.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.skyrpg.brainrots.BrainrotEntity;
import com.skyrpg.brainrots.BrainrotKind;
import com.skyrpg.brainrots.SkyRpgBrainrots;
import com.skyrpg.brainrots.client.model.BrainrotLayouts;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.client.rendering.v1.ModelLayerRegistry;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;

/**
 * Draws a brainrot with its kind's model and texture (and its glow texture, fullbright, when it has one). Spinners
 * twirl as they go.
 */
public final class BrainrotRenderer extends MobRenderer<BrainrotEntity, BrainrotRenderState, BrainrotModel> {
    private static final Map<BrainrotKind, ModelLayerLocation> LAYERS = new EnumMap<>(BrainrotKind.class);

    /** One model layer per drawn kind (called once, at client start). */
    static void registerLayers() {
        for (BrainrotKind kind : BrainrotKind.values()) {
            BrainrotLayouts.Entry entry = BrainrotLayouts.get(kind.id);
            if (entry == null) {
                continue;
            }
            ModelLayerLocation location = new ModelLayerLocation(SkyRpgBrainrots.id(kind.id), "main");
            LAYERS.put(kind, location);
            ModelLayerRegistry.registerModelLayer(location, () -> BrainrotLayouts.get(kind.id).layer());
        }
    }

    public BrainrotRenderer(EntityRendererProvider.Context context) {
        super(context, model(context), 0.4f);
        addLayer(new GlowLayer(this));
    }

    private static BrainrotModel model(EntityRendererProvider.Context context) {
        Map<BrainrotKind, ModelPart> kinds = new EnumMap<>(BrainrotKind.class);
        Map<String, ModelPart> children = new HashMap<>();
        for (Map.Entry<BrainrotKind, ModelLayerLocation> entry : LAYERS.entrySet()) {
            ModelPart part = context.bakeLayer(entry.getValue());
            kinds.put(entry.getKey(), part);
            children.put(entry.getKey().id, part);
        }
        return new BrainrotModel(new ModelPart(List.of(), children), kinds);
    }

    @Override
    public BrainrotRenderState createRenderState() {
        return new BrainrotRenderState();
    }

    @Override
    public void extractRenderState(BrainrotEntity entity, BrainrotRenderState state, float partialTick) {
        super.extractRenderState(entity, state, partialTick);
        state.kind = entity.kind();
    }

    @Override
    public Identifier getTextureLocation(BrainrotRenderState state) {
        return texture(state.kind);
    }

    static Identifier texture(BrainrotKind kind) {
        return SkyRpgBrainrots.id("textures/entity/" + kind.id + ".png");
    }

    @Override
    protected void setupRotations(BrainrotRenderState state, PoseStack poseStack, float bodyRot, float scale) {
        super.setupRotations(state, poseStack, bodyRot, scale);
        if (state.kind.move == BrainrotKind.Move.SPIN) {
            poseStack.rotateDegrees(Axis.YP, state.ageInTicks * 9f);
        }
    }

    /** The fullbright pixels of the kind's glow texture, if it has one. */
    private static final class GlowLayer extends RenderLayer<BrainrotRenderState, BrainrotModel> {
        GlowLayer(RenderLayerParent<BrainrotRenderState, BrainrotModel> parent) {
            super(parent);
        }

        @Override
        public void submit(PoseStack poseStack, SubmitNodeCollector collector, int light, BrainrotRenderState state, float yRot, float xRot) {
            BrainrotLayouts.Entry entry = BrainrotLayouts.get(state.kind.id);
            if (entry == null || !entry.glow()) {
                return;
            }
            Identifier glow = SkyRpgBrainrots.id("textures/entity/" + state.kind.id + "_glow.png");
            collector.order(1).submitModel(getParentModel(), state, poseStack, RenderTypes.eyes(glow), light, OverlayTexture.NO_OVERLAY, -1, null,
                    state.outlineColor);
        }
    }
}
