package com.skyrpg.npcs.mixin;

import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.decoration.Mannequin;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Mannequin keeps these setters private; NPCs need them to show their role line and stand still. */
@Mixin(Mannequin.class)
public interface MannequinAccess {
    @Invoker("setDescription")
    void skyrpg$setDescription(Component description);

    @Invoker("setHideDescription")
    void skyrpg$setHideDescription(boolean hide);

    @Invoker("setImmovable")
    void skyrpg$setImmovable(boolean immovable);
}
