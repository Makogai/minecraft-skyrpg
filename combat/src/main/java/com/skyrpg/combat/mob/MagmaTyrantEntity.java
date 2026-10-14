package com.skyrpg.combat.mob;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.pathfinder.PathType;
import net.minecraft.world.phys.Vec3;

/**
 * The Magma Tyrant: the Infernal Citadel's boss (docs/design/nether.md), a huge molten-rock titan with a crown of fire
 * (drawn by MagmaTyrantModel). Like the Iron Thane, the world module's dungeon boss system (DungeonBosses) spawns it by
 * id, sets its health, damage, speed, scale, knockback resistance, follow range and step height, keeps it on a leash,
 * picks its target and runs the moves from its boss JSON; this class only gives it a body that cooperates with that:
 * <ul>
 *   <li>plain melee goals and no stroll, so the boss system's target, leash teleports, leaps and slams aren't fought
 *   over; it never despawns, takes no fall damage, is fire-immune (the entity type) and drops no vanilla experience;</li>
 *   <li>its own attack is a telegraphed double-fist slam: when a target is in reach it raises both fists overhead (the
 *   slam pose, synced to clients as {@link #getSlamTicks()}), and they smash into the ground in front of it
 *   {@link #IMPACT_TICK} ticks later in a burst of magma, hitting and igniting the target if it's still there;</li>
 *   <li>its walk cycle slows with its scale, so a scaled-up Tyrant strides instead of shuffling;</li>
 *   <li>lava drips from its fists, flames lick up from its crown and its back vents smoke (client side).</li>
 * </ul>
 */
public class MagmaTyrantEntity extends Monster {
    /** Ticks the slam pose lasts: raise 0-12, smash 12-15, hold to 18, recover to 28 (see MagmaTyrantModel). */
    public static final int SLAM_POSE_TICKS = 28;
    /** Ticks after the slam starts that the fists land. */
    public static final int IMPACT_TICK = 15;
    /** How far in front of the Tyrant (blocks, times its scale) the fists land. */
    private static final double IMPACT_REACH = 1.5;
    /** Seconds a slam sets its target alight. */
    private static final float IMPACT_BURN_SECONDS = 4f;

    private static final EntityDataAccessor<Integer> SLAM_TICKS = SynchedEntityData.defineId(MagmaTyrantEntity.class, EntityDataSerializers.INT);

    /** Server only: ticks until the fists land (0 when not slamming). */
    private int impactIn;

    public MagmaTyrantEntity(EntityType<? extends Monster> type, Level level) {
        super(type, level);
        // The boss system drops its own experience; no vanilla orbs on top.
        xpReward = 0;
        // Fire is nothing to it; it will wade through lava but prefers solid ground.
        setPathfindingMalus(PathType.FIRE, 0f);
        setPathfindingMalus(PathType.FIRE_IN_NEIGHBOR, 0f);
        setPathfindingMalus(PathType.LAVA, 8f);
    }

    /** Defaults for a Tyrant outside the Citadel (/summon); the boss system replaces them from the boss JSON. */
    static AttributeSupplier.Builder attributes() {
        return Monster.createMonsterAttributes().add(Attributes.MAX_HEALTH, 500).add(Attributes.ARMOR, 16).add(Attributes.ARMOR_TOUGHNESS, 8)
                .add(Attributes.MOVEMENT_SPEED, 0.2).add(Attributes.ATTACK_DAMAGE, 16).add(Attributes.ATTACK_KNOCKBACK, 2.2)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0).add(Attributes.FOLLOW_RANGE, 40).add(Attributes.STEP_HEIGHT, 1.1);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(SLAM_TICKS, 0);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(2, new SlamAttackGoal(this));
        goalSelector.addGoal(7, new LookAtPlayerGoal(this, Player.class, 16f));
        goalSelector.addGoal(8, new RandomLookAroundGoal(this));
        targetSelector.addGoal(1, new HurtByTargetGoal(this));
        targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
    }

    /** Ticks left of the slam pose (0 when not slamming); the client model poses itself from it. */
    public int getSlamTicks() {
        return getEntityData().get(SLAM_TICKS);
    }

    public boolean isSlamming() {
        return getSlamTicks() > 0;
    }

    /** Starts the slam: the pose plays on every client and the fists land IMPACT_TICK ticks later. */
    public void startSlam() {
        getEntityData().set(SLAM_TICKS, SLAM_POSE_TICKS);
        impactIn = IMPACT_TICK;
        playSound(SoundEvents.RAVAGER_ROAR, 1.4f, 0.5f);
        playSound(SoundEvents.FIRECHARGE_USE, 1.2f, 0.5f);
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide()) {
            return;
        }
        int slam = getSlamTicks();
        if (slam > 0) {
            getEntityData().set(SLAM_TICKS, slam - 1);
            if (impactIn > 0 && impactIn % 3 == 0) {
                // embers gather around the raised fists
                ((ServerLevel) level()).sendParticles(ParticleTypes.FLAME, getX(), getY() + getBbHeight() * 1.05, getZ(), 6,
                        0.6 * getScale(), 0.4 * getScale(), 0.6 * getScale(), 0.02);
            }
        }
        if (impactIn > 0 && --impactIn == 0 && isAlive()) {
            fistImpact((ServerLevel) level());
        }
    }

    /** The fists hit the ground in front: magma bursts up, and the target takes the blow (and catches fire) if it's there. */
    private void fistImpact(ServerLevel level) {
        float yaw = yBodyRot * Mth.DEG_TO_RAD;
        double scale = getScale();
        Vec3 at = position().add(-Mth.sin(yaw) * IMPACT_REACH * scale, 0, Mth.cos(yaw) * IMPACT_REACH * scale);
        BlockState ground = level.getBlockState(BlockPos.containing(at.x, at.y - 0.5, at.z));
        if (!ground.isAir()) {
            level.sendParticles(new BlockParticleOption(ParticleTypes.DUST_PILLAR, ground), at.x, at.y + 0.1, at.z, (int) (30 * scale),
                    0.8 * scale, 0.1, 0.8 * scale, 0.15);
        }
        level.sendParticles(ParticleTypes.EXPLOSION, at.x, at.y + 0.3, at.z, 1, 0, 0, 0, 0);
        level.sendParticles(ParticleTypes.LAVA, at.x, at.y + 0.3, at.z, (int) (12 * scale), 0.6 * scale, 0.1, 0.6 * scale, 0.1);
        level.sendParticles(ParticleTypes.FLAME, at.x, at.y + 0.2, at.z, (int) (24 * scale), 0.9 * scale, 0.1, 0.9 * scale, 0.08);
        level.sendParticles(ParticleTypes.LARGE_SMOKE, at.x, at.y + 0.3, at.z, 10, 0.7 * scale, 0.2, 0.7 * scale, 0.03);
        level.playSound(null, at.x, at.y, at.z, SoundEvents.MACE_SMASH_GROUND_HEAVY, SoundSource.HOSTILE, 1.8f, 0.55f);
        level.playSound(null, at.x, at.y, at.z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.HOSTILE, 1.0f, 0.6f);
        level.playSound(null, at.x, at.y, at.z, SoundEvents.LAVA_POP, SoundSource.HOSTILE, 1.5f, 0.6f);
        LivingEntity target = getTarget();
        if (target != null && target.isAlive()) {
            double reach = 1.8 * scale + target.getBbWidth() * 0.5;
            boolean underFists = target.position().distanceToSqr(at) <= reach * reach && Math.abs(target.getY() - at.y) < 2.5 * scale;
            if (underFists || isWithinMeleeAttackRange(target)) {
                doHurtTarget(level, target);
            }
        }
    }

    /** The slam's blow: knockback from the attribute, a lift, and fire. */
    @Override
    public boolean doHurtTarget(ServerLevel level, Entity target) {
        boolean hit = super.doHurtTarget(level, target);
        if (hit) {
            target.push(0, 0.4, 0);
            target.syncVelocity = true;
            target.igniteForSeconds(IMPACT_BURN_SECONDS);
        }
        return hit;
    }

    /** Lava drips from the fists, flames lick up from the crown, the back vents smoke, the core spits now and then. */
    @Override
    public void aiStep() {
        super.aiStep();
        if (!level().isClientSide()) {
            return;
        }
        RandomSource random = getRandom();
        double scale = getScale();
        float yaw = yBodyRot * Mth.DEG_TO_RAD;
        double fx = -Mth.sin(yaw);
        double fz = Mth.cos(yaw);
        double rx = -Mth.cos(yaw);
        double rz = -Mth.sin(yaw);
        if (!isSlamming()) {
            // the fists hang at about 1.33 blocks to either side, 0.48 up, a little forward
            for (int side = -1; side <= 1; side += 2) {
                if (random.nextInt(3) == 0) {
                    double s = 1.33 * side * scale;
                    double f = 0.23 * scale;
                    level().addParticle(ParticleTypes.DRIPPING_LAVA, getX() + rx * s + fx * f + (random.nextDouble() - 0.5) * 0.5 * scale,
                            getY() + 0.45 * scale, getZ() + rz * s + fz * f + (random.nextDouble() - 0.5) * 0.5 * scale, 0, 0, 0);
                }
            }
        }
        if (random.nextInt(2) == 0) {
            double f = 0.55 * scale;
            level().addParticle(random.nextInt(3) == 0 ? ParticleTypes.SMALL_FLAME : ParticleTypes.FLAME,
                    getX() + fx * f + (random.nextDouble() - 0.5) * 0.6 * scale, getY() + 3.3 * scale,
                    getZ() + fz * f + (random.nextDouble() - 0.5) * 0.6 * scale, 0, 0.04, 0);
        }
        if (random.nextInt(5) == 0) {
            double b = -0.7 * scale;
            double s = (random.nextDouble() - 0.5) * 0.5 * scale;
            level().addParticle(ParticleTypes.LARGE_SMOKE, getX() + fx * b + rx * s, getY() + 3.2 * scale, getZ() + fz * b + rz * s, 0, 0.05, 0);
        }
        if (random.nextInt(40) == 0) {
            double f = 0.6 * scale;
            level().addParticle(ParticleTypes.LAVA, getX() + fx * f, getY() + 2.3 * scale, getZ() + fz * f, 0, 0, 0);
        }
    }

    @Override
    protected void updateWalkAnimation(float distance) {
        super.updateWalkAnimation(distance / Math.max(0.5f, getScale()));
    }

    @Override
    public boolean removeWhenFarAway(double distanceSq) {
        return false;
    }

    @Override
    public boolean causeFallDamage(double fallDistance, float multiplier, DamageSource source) {
        return false;
    }

    @Override
    public boolean canBreatheUnderwater() {
        return true;
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return SoundEvents.LAVA_AMBIENT;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.BASALT_BREAK;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.IRON_GOLEM_DEATH;
    }

    @Override
    protected void playStepSound(BlockPos pos, BlockState state) {
        playSound(SoundEvents.IRON_GOLEM_STEP, 1.3f, 0.45f);
        playSound(SoundEvents.BASALT_STEP, 1.0f, 0.6f);
    }

    @Override
    public float getVoicePitch() {
        return super.getVoicePitch() * 0.55f;
    }

    /**
     * Vanilla melee chasing, but the attack is the telegraphed slam: in reach, with the last slam finished, it starts a
     * new one (the fists land from {@link #tick()}); while slamming it stands its ground facing the target.
     */
    static final class SlamAttackGoal extends MeleeAttackGoal {
        private final MagmaTyrantEntity tyrant;

        SlamAttackGoal(MagmaTyrantEntity tyrant) {
            super(tyrant, 1.0, true);
            this.tyrant = tyrant;
        }

        @Override
        public void tick() {
            LivingEntity target = tyrant.getTarget();
            if (tyrant.isSlamming()) {
                tyrant.getNavigation().stop();
                if (target != null) {
                    tyrant.getLookControl().setLookAt(target, 30f, 30f);
                    if (tyrant.impactIn > 0) {
                        // turn the whole body to the target while the fists are up, so the blow lands where it's aimed
                        BoneGiantEntity.turnTowards(tyrant, target, 10f);
                    }
                }
                return;
            }
            super.tick();
        }

        @Override
        protected void checkAndPerformAttack(LivingEntity target) {
            if (!tyrant.isSlamming() && canPerformAttack(target)) {
                resetAttackCooldown();
                tyrant.startSlam();
            }
        }
    }
}
