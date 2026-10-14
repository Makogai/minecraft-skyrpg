package com.skyrpg.combat.mixin;

import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.decoration.Mannequin;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Mannequin keeps these setters private; the Training Dummy needs them to stand still and show its hint line. */
@Mixin(Mannequin.class)
public interface DummyAccess {
    @Invoker("setDescription")
    void skyrpg$setDescription(Component description);

    @Invoker("setHideDescription")
    void skyrpg$setHideDescription(boolean hide);

    @Invoker("setImmovable")
    void skyrpg$setImmovable(boolean immovable);
}
