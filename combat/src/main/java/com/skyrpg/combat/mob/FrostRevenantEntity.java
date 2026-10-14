package com.skyrpg.combat.mob;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.skeleton.AbstractSkeleton;
import net.minecraft.world.entity.monster.skeleton.Stray;
import net.minecraft.world.level.Level;

/** Frost Revenant: a stray of the snowy biomes whose arrows freeze you (see {@link WorldMobs}). */
public class FrostRevenantEntity extends Stray {
    public FrostRevenantEntity(EntityType<? extends Stray> type, Level level) {
        super(type, level);
    }

    static AttributeSupplier.Builder attributes() {
        return AbstractSkeleton.createAttributes().add(Attributes.MAX_HEALTH, 30);
    }
}
