package com.skyrpg.combat.mixin;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.SpawnPlacementType;
import net.minecraft.world.entity.SpawnPlacements;
import net.minecraft.world.level.levelgen.Heightmap;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Vanilla's (private) spawn placement registration, for our world mobs (Fabric API has no hook for it). */
@Mixin(SpawnPlacements.class)
public interface SpawnPlacementsInvoker {
    @Invoker("register")
    static <T extends Mob> void skyrpg$register(EntityType<T> type, SpawnPlacementType placement, Heightmap.Types heightmap,
                                                SpawnPlacements.SpawnPredicate<T> predicate) {
        throw new AssertionError();
    }
}
