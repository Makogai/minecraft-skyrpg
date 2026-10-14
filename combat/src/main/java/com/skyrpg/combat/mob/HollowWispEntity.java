package com.skyrpg.combat.mob;

import net.minecraft.util.RandomSource;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Vex;
import net.minecraft.world.level.Level;

/** Hollow Wisp: a lone spirit of the plains and meadows at night. It flits through walls, unarmed, and fades at dawn. */
public class HollowWispEntity extends Vex {
    public HollowWispEntity(EntityType<? extends Vex> type, Level level) {
        super(type, level);
    }

    static AttributeSupplier.Builder attributes() {
        return Vex.createAttributes().add(Attributes.MAX_HEALTH, 16).add(Attributes.ATTACK_DAMAGE, 3);
    }

    /** No sword. */
    @Override
    protected void populateDefaultEquipmentSlots(RandomSource random, DifficultyInstance difficulty) {
    }
}
