package com.skyrpg.npcs.client.mixin;

import com.skyrpg.core.client.NpcMarkers;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.Mannequin;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Quest markers (! and ?) around an NPC's name tag, see {@link NpcMarkers}. */
@Mixin(EntityRenderer.class)
abstract class NpcMarkerMixin<T extends Entity, S extends EntityRenderState> {
    @Inject(method = "extractNameTags(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/client/renderer/entity/state/EntityRenderState;FDD)V",
            at = @At("TAIL"))
    private void skyrpg$marker(T entity, S state, float partialTicks, double nameTagDistance, double belowNameDistance, CallbackInfo ci) {
        if (entity instanceof Mannequin && state.nameTag != null) {
            state.nameTag = NpcMarkers.decorate(entity, state.nameTag);
        }
    }
}
