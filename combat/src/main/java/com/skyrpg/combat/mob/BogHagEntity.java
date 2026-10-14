package com.skyrpg.combat.mob;

import com.skyrpg.combat.MobLevels;
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
import net.minecraft.world.entity.monster.Witch;
import net.minecraft.world.entity.monster.cubemob.Slime;
import net.minecraft.world.level.Level;

/**
 * Bog Hag: a swamp witch that calls bog slimes to her side (two every 15 seconds while she's fighting, four at most).
 * Her slimes are "farmed": no rewards for them.
 */
public class BogHagEntity extends Witch {
    static final String SLIME_TAG = "skyrpg_bog_slime";
    private static final int EVERY = 300;
    private static final int MAX_SLIMES = 4;
    private int nextCall = 60;

    public BogHagEntity(EntityType<? extends Witch> type, Level level) {
        super(type, level);
    }

    static AttributeSupplier.Builder attributes() {
        return Witch.createAttributes().add(Attributes.MAX_HEALTH, 40).add(Attributes.MOVEMENT_SPEED, 0.27);
    }

    @Override
    protected void customServerAiStep(ServerLevel level) {
        super.customServerAiStep(level);
        LivingEntity target = getTarget();
        if (target == null || !target.isAlive() || distanceTo(target) > 16 || --nextCall > 0) {
            return;
        }
        nextCall = EVERY;
        int near = level.getEntitiesOfClass(Slime.class, getBoundingBox().inflate(16), slime -> slime.entityTags().contains(SLIME_TAG)).size();
        for (int i = near; i < Math.min(MAX_SLIMES, near + 2); i++) {
            Slime slime = EntityTypes.SLIME.create(level, EntitySpawnReason.MOB_SUMMONED);
            if (slime == null) {
                return;
            }
            double angle = getRandom().nextDouble() * Math.PI * 2;
            slime.snapTo(getX() + Math.cos(angle) * 1.5, getY(), getZ() + Math.sin(angle) * 1.5, getRandom().nextFloat() * 360, 0);
            slime.setSize(1, true);
            slime.addTag(SLIME_TAG);
            slime.addTag(MobTags.FARMED);
            MobLevels.set(slime, new MobLevels.MobInfo(Math.max(1, MobLevels.level(this)), false));
            slime.setTarget(target);
            level.addFreshEntity(slime);
            level.sendParticles(ParticleTypes.ITEM_SLIME, slime.getX(), slime.getY() + 0.3, slime.getZ(), 12, 0.3, 0.3, 0.3, 0.05);
        }
        level.playSound(null, getX(), getY(), getZ(), SoundEvents.WITCH_CELEBRATE, SoundSource.HOSTILE, 1.0f, 0.7f);
    }
}
