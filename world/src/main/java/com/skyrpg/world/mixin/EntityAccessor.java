package com.skyrpg.world.mixin;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.component.CustomData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** An entity's custom data (its "data" tag), where dungeon markers keep their settings and state. */
@Mixin(Entity.class)
public interface EntityAccessor {
    @Accessor("customData")
    CustomData skyrpg$getCustomData();

    @Accessor("customData")
    void skyrpg$setCustomData(CustomData data);
}
