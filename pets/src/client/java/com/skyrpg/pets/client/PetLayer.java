package com.skyrpg.pets.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.skyrpg.pets.PetRarity;
import com.skyrpg.pets.PetType;
import com.skyrpg.pets.client.model.PetModel;
import net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.Mth;

/**
 * Draws a player's active pet as a real 3D model floating beside their right shoulder. The pet trails behind when the
 * player moves or turns (smoothed per player in {@link PetTrack}), its head follows where the player looks, and it
 * animates (wings, tail, ears, blinking, idle bob; see {@link PetModel}). Works on any player's render state, so other
 * players, third person and the inventory/menu previews all draw it; the avatar renderer mixin fills in the {@link PetView}.
 */
public final class PetLayer extends RenderLayer<AvatarRenderState, PlayerModel> {
    /** What to draw for this player, or null for no pet. */
    public static final RenderStateDataKey<PetView> PET = RenderStateDataKey.create(() -> "skyrpg_pets:pet");

    /** A player's pet on their render state. {@code preview}: a menu preview (no trailing). */
    public record PetView(PetType type, PetRarity rarity, int owner, boolean preview) {
    }

    PetLayer(RenderLayerParent<AvatarRenderState, PlayerModel> renderer) {
        super(renderer);
    }

    @Override
    public void submit(PoseStack poseStack, SubmitNodeCollector collector, int light, AvatarRenderState state, float yRot, float xRot) {
        PetView view = state.getData(PET);
        if (view == null || state.isInvisible) {
            return;
        }
        // previews get their own track so they never fight with the in-world pet over its smoothed position
        PetTrack track = PetTrack.get(view.preview() ? -view.owner() - 1 : view.owner(), PetEffects.clientTick());
        track.update(state.x, state.y, state.z, state.bodyRot, state.ageScale > 0f ? state.ageScale : 1f, state.ageInTicks,
                state.yRot, state.xRot, !view.preview());

        PetModel model = PetModel.of(view.type());
        float seed = (view.owner() * 0.61803f) % 1f;
        float bob = Mth.sin(state.ageInTicks * 0.1f + seed * 6.28f) * 0.04f;
        float headYaw = Mth.clamp(track.headYaw * Mth.DEG_TO_RAD * 0.8f, -1.1f, 1.1f);
        float headPitch = Mth.clamp(track.headPitch * Mth.DEG_TO_RAD * 0.6f, -0.6f, 0.6f);
        PetModel.Anim anim = new PetModel.Anim(view.rarity(), state.ageInTicks, seed, headYaw, headPitch, track.lagX, track.lagY, track.lagZ);

        poseStack.pushPose();
        // Model space: y points down and the player's right is -x, so this is above and beside the right shoulder.
        poseStack.translate(track.px, track.py + bob, track.pz);
        // the pet's body turns a little with the head
        poseStack.rotate(Axis.YP.rotation(headYaw * 0.35f));
        poseStack.scale(model.scale(), model.scale(), model.scale());
        collector.submitModel(model, anim, poseStack, model.renderType(view.rarity()), light, OverlayTexture.NO_OVERLAY, state.outlineColor);
        RenderType glow = model.glowType(view.rarity());
        if (glow != null) {
            collector.submitModel(model, anim, poseStack, glow, 0xF000F0, OverlayTexture.NO_OVERLAY, 0);
        }
        poseStack.popPose();
    }
}
