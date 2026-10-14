package com.skyrpg.npcs.mixin;

import com.skyrpg.npcs.Npcs;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * NPCs are never written to disk: data files are the source of truth and the NPC manager respawns them
 * whenever their chunk loads. That means no stale or duplicate NPCs, ever.
 */
@Mixin(Entity.class)
abstract class NpcSaveMixin {
    @Inject(method = "shouldBeSaved", at = @At("HEAD"), cancellable = true)
    private void skyrpg$neverSaveNpcs(CallbackInfoReturnable<Boolean> cir) {
        if (((Entity) (Object) this).entityTags().contains(Npcs.TAG)) {
            cir.setReturnValue(false);
        }
    }
}
