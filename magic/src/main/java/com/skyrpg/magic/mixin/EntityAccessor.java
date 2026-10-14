package com.skyrpg.magic.mixin;

import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Lets spells land a hit on something that was just hit: vanilla's invulnerability frames would swallow it. */
@Mixin(Entity.class)
public interface EntityAccessor {
    @Accessor("invulnerableTime")
    void skyrpgMagic$setInvulnerableTime(int ticks);
}
