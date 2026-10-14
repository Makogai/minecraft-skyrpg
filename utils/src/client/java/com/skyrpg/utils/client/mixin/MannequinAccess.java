package com.skyrpg.utils.client.mixin;

import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.decoration.Mannequin;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** The line under a mannequin's name; NPCs use it for their role ("Mining Master · Shop"). */
@Mixin(Mannequin.class)
public interface MannequinAccess {
    @Invoker("getDescription")
    Component skyrpg$description();
}
