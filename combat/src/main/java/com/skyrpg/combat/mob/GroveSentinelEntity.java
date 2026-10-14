package com.skyrpg.combat.mob;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.level.Level;

/**
 * Grove Sentinel: a slow, tough guardian of the forests at night, on a zombie's body. Its hits root you in place (see
 * {@link WorldMobs}). It's wood, not undead: no babies, no reinforcements, no drowning into something else, no burning.
 */
public class GroveSentinelEntity extends Zombie {
    public GroveSentinelEntity(EntityType<? extends Zombie> type, Level level) {
        super(type, level);
    }

    static AttributeSupplier.Builder attributes() {
        return Zombie.createAttributes().add(Attributes.MAX_HEALTH, 44).add(Attributes.ARMOR, 6).add(Attributes.MOVEMENT_SPEED, 0.19)
                .add(Attributes.ATTACK_DAMAGE, 5).add(Attributes.KNOCKBACK_RESISTANCE, 0.6).add(Attributes.SCALE, 1.15);
    }

    @Override
    public void setBaby(boolean baby) {
    }

    @Override
    protected void randomizeReinforcementsChance() {
    }

    @Override
    protected boolean convertsInWater() {
        return false;
    }

    @Override
    protected boolean isSunSensitive() {
        return false;
    }
}
