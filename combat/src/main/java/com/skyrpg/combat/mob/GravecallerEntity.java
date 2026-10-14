package com.skyrpg.combat.mob;

import com.skyrpg.core.world.MobTags;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.level.Level;

/**
 * Gravecaller: a necromancer of the dark forests at night. While fighting it raises two zombies every twelve seconds
 * (four at most); they're "farmed": no rewards and no slayer progress.
 */
public class GravecallerEntity extends Zombie {
    static final String RAISED_TAG = "skyrpg_raised";
    private static final int EVERY = 240;
    private static final int MAX_RAISED = 4;
    private int nextRaise = 40;

    public GravecallerEntity(EntityType<? extends Zombie> type, Level level) {
        super(type, level);
    }

    static AttributeSupplier.Builder attributes() {
        return Zombie.createAttributes().add(Attributes.MAX_HEALTH, 34).add(Attributes.MOVEMENT_SPEED, 0.21);
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
    protected void customServerAiStep(ServerLevel level) {
        super.customServerAiStep(level);
        LivingEntity target = getTarget();
        if (target == null || !target.isAlive() || distanceTo(target) > 16 || --nextRaise > 0) {
            return;
        }
        nextRaise = EVERY;
        int near = level.getEntitiesOfClass(Zombie.class, getBoundingBox().inflate(16), zombie -> zombie.entityTags().contains(RAISED_TAG)).size();
        int raised = 0;
        for (int i = near; i < Math.min(MAX_RAISED, near + 2); i++) {
            Zombie zombie = EntityTypes.ZOMBIE.create(level, EntitySpawnReason.MOB_SUMMONED);
            if (zombie == null) {
                return;
            }
            double angle = getRandom().nextDouble() * Math.PI * 2;
            zombie.snapTo(getX() + Math.cos(angle) * 2, getY(), getZ() + Math.sin(angle) * 2, getRandom().nextFloat() * 360, 0);
            if (!level.noCollision(zombie)) {
                continue;
            }
            zombie.finalizeSpawn(level, level.getCurrentDifficultyAt(zombie.blockPosition()), EntitySpawnReason.MOB_SUMMONED, null);
            zombie.setBaby(false);
            zombie.addTag(RAISED_TAG);
            zombie.addTag(MobTags.FARMED);
            zombie.setTarget(target);
            level.addFreshEntity(zombie);
            level.sendParticles(ParticleTypes.SOUL, zombie.getX(), zombie.getY() + 0.2, zombie.getZ(), 14, 0.4, 0.1, 0.4, 0.03);
            raised++;
        }
        if (raised > 0) {
            level.playSound(null, getX(), getY(), getZ(), SoundEvents.EVOKER_PREPARE_SUMMON, SoundSource.HOSTILE, 1.0f, 0.8f);
        }
    }
}
