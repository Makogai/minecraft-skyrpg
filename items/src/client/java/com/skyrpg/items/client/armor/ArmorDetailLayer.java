package com.skyrpg.items.client.armor;

import com.mojang.blaze3d.vertex.PoseStack;
import com.skyrpg.items.client.armor.ArmorDepthRendering.DetailSet;
import com.skyrpg.items.client.armor.ArmorDetailModel.Piece;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;

/**
 * Draws the raised detail of worn SkyRPG armour on players and mannequins: for each armour slot holding a
 * piece of a set with a detail model, that piece's {@link ArmorDetailModel}, over the flat armour layer.
 * Like the armour itself it stays visible on invisible players and glints when the piece is enchanted.
 */
final class ArmorDetailLayer extends RenderLayer<AvatarRenderState, PlayerModel> {
    private final Map<DetailSet, Map<Piece, ArmorDetailModel>> models = new EnumMap<>(DetailSet.class);
    /** Sets that have a glow texture (lamp lenses and the like, drawn at full brightness). */
    private final Set<DetailSet> glowing = EnumSet.noneOf(DetailSet.class);

    ArmorDetailLayer(RenderLayerParent<AvatarRenderState, PlayerModel> renderer, EntityRendererProvider.Context context) {
        super(renderer);
        for (DetailSet set : DetailSet.values()) {
            Map<Piece, ArmorDetailModel> pieces = new EnumMap<>(Piece.class);
            for (Piece piece : Piece.values()) {
                pieces.put(piece, new ArmorDetailModel(context.bakeLayer(set.layer), piece));
            }
            models.put(set, pieces);
            // Renderers are rebuilt on every resource reload, so this follows resource packs.
            if (context.getResourceManager().getResource(set.glowTexture).isPresent()) {
                glowing.add(set);
            }
        }
    }

    @Override
    public void submit(PoseStack poseStack, SubmitNodeCollector collector, int light, AvatarRenderState state, float yRot, float xRot) {
        submitPiece(poseStack, collector, light, state, state.headEquipment, Piece.HELMET);
        submitPiece(poseStack, collector, light, state, state.chestEquipment, Piece.CHEST);
        submitPiece(poseStack, collector, light, state, state.legsEquipment, Piece.LEGS);
        submitPiece(poseStack, collector, light, state, state.feetEquipment, Piece.BOOTS);
    }

    private void submitPiece(PoseStack poseStack, SubmitNodeCollector collector, int light, AvatarRenderState state, ItemStack stack, Piece piece) {
        // The same test the armour layer uses, so detail only shows where the armour does.
        if (!HumanoidArmorLayer.shouldRender(stack, piece.slot)) {
            return;
        }
        DetailSet set = DetailSet.of(stack.get(DataComponents.EQUIPPABLE).assetId().orElseThrow());
        if (set == null) {
            return;
        }
        ArmorDetailModel model = models.get(set).get(piece);
        RenderType type = stack.hasFoil() ? RenderTypes.armorCutoutNoCullGlint(set.texture) : RenderTypes.armorCutoutNoCull(set.texture);
        collector.submitModel(model, state, poseStack, type, light, OverlayTexture.NO_OVERLAY, state.outlineColor);
        if (glowing.contains(set)) {
            collector.order(1).submitModel(model, state, poseStack, RenderTypes.eyes(set.glowTexture), light, OverlayTexture.NO_OVERLAY,
                    state.outlineColor);
        }
    }
}
