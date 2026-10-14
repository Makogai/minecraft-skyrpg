package com.skyrpg.combat.mob;

import java.util.EnumSet;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.AreaEffectCloud;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

/**
 * Sporeling: a knee-high mushroom creature of the Fungal Depths. Weak and quick, it keeps with its group (strays walk
 * back to the others, and when one finds or is hit by a target the whole group joins in), and when it dies it bursts
 * into a cloud of poison spores. Its own spores do not poison it or its kin.
 */
public class SporelingEntity extends Monster {
    /** How far a Sporeling looks for its group, and calls it to a fight. */
    static final double GROUP_RANGE = 12;
    /** The spore cloud: radius in blocks, how long it lingers, and the Poison it gives (ticks, amplifier). */
    static final float CLOUD_RADIUS = 3.0f;
    static final int CLOUD_TICKS = 80;
    static final int POISON_TICKS = 100;
    static final int POISON_AMPLIFIER = 0;

    public SporelingEntity(EntityType<? extends Monster> type, Level level) {
        super(type, level);
    }

    static AttributeSupplier.Builder attributes() {
        return Monster.createMonsterAttributes().add(Attributes.MAX_HEALTH, 12).add(Attributes.ATTACK_DAMAGE, 3)
                .add(Attributes.MOVEMENT_SPEED, 0.32).add(Attributes.FOLLOW_RANGE, 24);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(1, new FloatGoal(this));
        goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.15, false));
        goalSelector.addGoal(4, new StayWithGroupGoal(this));
        goalSelector.addGoal(5, new WaterAvoidingRandomStrollGoal(this, 0.9));
        goalSelector.addGoal(7, new LookAtPlayerGoal(this, Player.class, 8f));
        goalSelector.addGoal(8, new RandomLookAroundGoal(this));
        targetSelector.addGoal(1, new HurtByTargetGoal(this).setAlertOthers());
        targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
    }

    /** When this one picks a fight, the rest of its group (those without a target) joins in. */
    @Override
    public void setTarget(@Nullable LivingEntity target) {
        boolean fresh = target != null && getTarget() == null;
        super.setTarget(target);
        if (fresh && level() instanceof ServerLevel && !(target instanceof SporelingEntity)) {
            for (SporelingEntity other : level().getEntitiesOfClass(SporelingEntity.class, getBoundingBox().inflate(GROUP_RANGE, 4, GROUP_RANGE),
                    o -> o != this && o.isAlive() && o.getTarget() == null)) {
                other.joinFight(target);
            }
        }
    }

    private void joinFight(LivingEntity target) {
        super.setTarget(target);
    }

    @Override
    public boolean canBeAffected(MobEffectInstance effect) {
        return !effect.is(MobEffects.POISON) && super.canBeAffected(effect);
    }

    @Override
    public void die(DamageSource source) {
        boolean first = !isRemoved() && !dead;
        super.die(source);
        if (first && level() instanceof ServerLevel level) {
            burst(level);
        }
    }

    /** The spore burst: a lingering poison cloud where it fell, a puff of spores and a pop. */
    private void burst(ServerLevel level) {
        AreaEffectCloud cloud = new AreaEffectCloud(level, getX(), getY(), getZ());
        cloud.setRadius(CLOUD_RADIUS);
        cloud.setRadiusOnUse(0);
        cloud.setRadiusPerTick(-CLOUD_RADIUS * 0.4f / CLOUD_TICKS); // shrinks a little as it thins out
        cloud.setWaitTime(4);
        cloud.setDuration(CLOUD_TICKS);
        cloud.addEffect(new MobEffectInstance(MobEffects.POISON, POISON_TICKS, POISON_AMPLIFIER));
        level.addFreshEntity(cloud);
        double y = getY(0.6);
        level.sendParticles(ParticleTypes.SNEEZE, getX(), y, getZ(), 40, 0.9, 0.5, 0.9, 0.03);
        level.sendParticles(ParticleTypes.SPORE_BLOSSOM_AIR, getX(), y + 0.2, getZ(), 30, 1.3, 0.7, 1.3, 0.01);
        level.sendParticles(new DustParticleOptions(0xB6FF5C, 1.4f), getX(), y, getZ(), 24, 0.7, 0.4, 0.7, 0);
        level.sendParticles(ParticleTypes.CRIMSON_SPORE, getX(), y, getZ(), 12, 0.6, 0.4, 0.6, 0.02);
        level.playSound(null, getX(), getY(), getZ(), SoundEvents.PUFFER_FISH_BLOW_OUT, getSoundSource(), 1.0f, 0.7f);
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return SoundEvents.FUNGUS_PLACE;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.SLIME_HURT_SMALL;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.FUNGUS_BREAK;
    }

    @Override
    protected void playStepSound(BlockPos pos, BlockState state) {
        playSound(SoundEvents.FUNGUS_STEP, 0.3f, 1.4f);
    }

    @Override
    public float getVoicePitch() {
        return 1.5f + (getRandom().nextFloat() - 0.5f) * 0.3f;
    }

    /** Keeps a wandering Sporeling near its group: when it strays from the middle of the others, it walks back. */
    static final class StayWithGroupGoal extends Goal {
        private static final double STRAY = 5;
        private final SporelingEntity mob;
        private int cooldown;
        private double tx;
        private double ty;
        private double tz;

        StayWithGroupGoal(SporelingEntity mob) {
            this.mob = mob;
            setFlags(EnumSet.of(Goal.Flag.MOVE));
        }

        @Override
        public boolean canUse() {
            if (mob.getTarget() != null || --cooldown > 0) {
                return false;
            }
            cooldown = reducedTickDelay(20 + mob.getRandom().nextInt(20));
            List<SporelingEntity> group = mob.level().getEntitiesOfClass(SporelingEntity.class,
                    mob.getBoundingBox().inflate(GROUP_RANGE, 4, GROUP_RANGE), o -> o != mob && o.isAlive());
            if (group.isEmpty()) {
                return false;
            }
            double x = 0;
            double y = 0;
            double z = 0;
            for (SporelingEntity other : group) {
                x += other.getX();
                y += other.getY();
                z += other.getZ();
            }
            x /= group.size();
            y /= group.size();
            z /= group.size();
            if (mob.distanceToSqr(x, y, z) < STRAY * STRAY) {
                return false;
            }
            tx = x;
            ty = y;
            tz = z;
            return true;
        }

        @Override
        public boolean canContinueToUse() {
            return mob.getTarget() == null && !mob.getNavigation().isDone() && mob.distanceToSqr(tx, ty, tz) > 4;
        }

        @Override
        public void start() {
            mob.getNavigation().moveTo(tx, ty, tz, 1.0);
        }

        @Override
        public void stop() {
            mob.getNavigation().stop();
        }
    }
}
