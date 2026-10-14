package com.skyrpg.combat.mob;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Blaze;
import net.minecraft.world.level.Level;

/** Ember Imp: a small, quick blaze of the badlands and the Nether. */
public class EmberImpEntity extends Blaze {
    public EmberImpEntity(EntityType<? extends Blaze> type, Level level) {
        super(type, level);
    }

    static AttributeSupplier.Builder attributes() {
        return Blaze.createAttributes().add(Attributes.MAX_HEALTH, 14).add(Attributes.MOVEMENT_SPEED, 0.32).add(Attributes.SCALE, 0.65);
    }
}
