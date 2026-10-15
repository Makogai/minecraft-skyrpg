package com.skyrpg.hub.mixin;

import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.decoration.Mannequin;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Mannequin keeps these setters private; the podium statues need them to stand still and show their place. */
@Mixin(Mannequin.class)
public interface PodiumMannequinAccess {
    @Invoker("setDescription")
    void skyrpg$podiumDescription(Component description);

    @Invoker("setHideDescription")
    void skyrpg$podiumHideDescription(boolean hide);

    @Invoker("setImmovable")
    void skyrpg$podiumImmovable(boolean immovable);
}
