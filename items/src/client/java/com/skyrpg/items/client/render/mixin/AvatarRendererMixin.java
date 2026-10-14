package com.skyrpg.items.client.render.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.skyrpg.items.accessory.Accessories;
import com.skyrpg.items.accessory.AccessoryLoadout;
import com.skyrpg.items.client.render.AccessoryRenderState;
import com.skyrpg.items.client.render.AccessoryRendering;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Avatar;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Puts each player's accessories on their render state (Fabric has no extraction event), which also covers
 * the player in the inventory screen, and draws your bracers on your own hands in first person.
 */
@Mixin(AvatarRenderer.class)
abstract class AvatarRendererMixin {
    /** Set every time, so a reused state never keeps someone else's; mannequins share this renderer and wear none. */
    @Inject(method = "extractRenderState(Lnet/minecraft/world/entity/Avatar;Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;F)V",
            at = @At("TAIL"))
    private void skyrpg$extractAccessories(Avatar entity, AvatarRenderState state, float partialTicks, CallbackInfo ci) {
        state.setData(AccessoryRenderState.LOADOUT, entity instanceof Player player ? Accessories.loadout(player) : AccessoryLoadout.EMPTY);
    }

    @Inject(method = "renderRightHand", at = @At("TAIL"))
    private void skyrpg$rightBracer(PoseStack poseStack, SubmitNodeCollector collector, int light, Identifier skin, boolean hasSleeve, CallbackInfo ci) {
        AccessoryRendering.submitFirstPersonBracer(poseStack, collector, light, true);
    }

    @Inject(method = "renderLeftHand", at = @At("TAIL"))
    private void skyrpg$leftBracer(PoseStack poseStack, SubmitNodeCollector collector, int light, Identifier skin, boolean hasSleeve, CallbackInfo ci) {
        AccessoryRendering.submitFirstPersonBracer(poseStack, collector, light, false);
    }
}
