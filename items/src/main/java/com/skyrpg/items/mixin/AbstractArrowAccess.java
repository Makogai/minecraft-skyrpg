package com.skyrpg.items.mixin;

import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** The pierce level has no public setter (vanilla sets it from the Piercing enchantment); the Skyiron Longbow sets its own. */
@Mixin(AbstractArrow.class)
public interface AbstractArrowAccess {
    @Invoker("setPierceLevel")
    void skyrpg$setPierceLevel(byte level);
}
