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
import net.minecraft.world.phys.Vec3;

/**
 * The Iron Thane: the Dwarven Halls boss, a huge crowned dwarf-king construct with a forge hammer (drawn by
 * IronThaneModel). The world module's dungeon boss system (DungeonBosses) spawns it by id, sets its health, damage,
 * speed, scale and knockback resistance, keeps it on a leash, picks its target and runs the moves from its boss JSON;
 * this class only gives it a body that cooperates with that:
 * <ul>
 *   <li>plain melee goals and no custom movement, so the boss system's target, leash teleports, leaps and slams aren't
 *   fought over; it never despawns and takes no fall damage;</li>
 *   <li>its own attack is a telegraphed hammer slam: when a target is in reach it raises the hammer overhead (the slam
 *   pose, synced to clients as {@link #getSlamTicks()}), and the blow lands {@link #IMPACT_TICK} ticks later on the
 *   ground in front of it, hitting the target if it's still there;</li>
 *   <li>its walk cycle slows with its scale, so a scaled-up Thane strides instead of shuffling.</li>
 * </ul>
 */
public class IronThaneEntity extends Monster {
    /** Ticks the slam pose lasts: raise 0-10, smash 10-13, hold to 16, recover to 24 (see IronThaneModel). */
    public static final int SLAM_POSE_TICKS = 24;
    /** Ticks after the slam starts that the hammer lands. */
    public static final int IMPACT_TICK = 13;
    /** How far in front of the Thane (blocks, times its scale) the hammer head lands. */
    private static final double IMPACT_REACH = 2.2;

    private static final EntityDataAccessor<Integer> SLAM_TICKS = SynchedEntityData.defineId(IronThaneEntity.class, EntityDataSerializers.INT);

    /** Server only: ticks until the hammer lands (0 when not slamming). */
    private int impactIn;

    public IronThaneEntity(EntityType<? extends Monster> type, Level level) {
        super(type, level);
        // The boss system drops its own experience; no vanilla orbs on top.
        xpReward = 0;
    }

    /** Defaults for a Thane outside a dungeon (/summon); the boss system replaces them from the boss JSON. */
    static AttributeSupplier.Builder attributes() {
        return Monster.createMonsterAttributes().add(Attributes.MAX_HEALTH, 400).add(Attributes.ARMOR, 14).add(Attributes.ARMOR_TOUGHNESS, 6)
                .add(Attributes.MOVEMENT_SPEED, 0.2).add(Attributes.ATTACK_DAMAGE, 14).add(Attributes.ATTACK_KNOCKBACK, 2.0)
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
        goalSelector.addGoal(2, new HammerAttackGoal(this));
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

    /** Starts the hammer slam: the pose plays on every client and the blow lands IMPACT_TICK ticks later. */
    public void startSlam() {
        getEntityData().set(SLAM_TICKS, SLAM_POSE_TICKS);
        impactIn = IMPACT_TICK;
        playSound(SoundEvents.IRON_GOLEM_ATTACK, 1.6f, 0.5f);
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
        }
        if (impactIn > 0 && --impactIn == 0 && isAlive()) {
            hammerImpact((ServerLevel) level());
        }
    }

    /** The hammer hits the ground in front: dust, sparks and a boom, and the target takes the blow if it's there. */
    private void hammerImpact(ServerLevel level) {
        float yaw = yBodyRot * Mth.DEG_TO_RAD;
        double scale = getScale();
        Vec3 at = position().add(-Mth.sin(yaw) * IMPACT_REACH * scale, 0, Mth.cos(yaw) * IMPACT_REACH * scale);
        BlockState ground = level.getBlockState(BlockPos.containing(at.x, at.y - 0.5, at.z));
        if (!ground.isAir()) {
            level.sendParticles(new BlockParticleOption(ParticleTypes.DUST_PILLAR, ground), at.x, at.y + 0.1, at.z, (int) (30 * scale),
                    0.7 * scale, 0.1, 0.7 * scale, 0.15);
        }
        level.sendParticles(ParticleTypes.EXPLOSION, at.x, at.y + 0.3, at.z, 1, 0, 0, 0, 0);
        level.sendParticles(ParticleTypes.LAVA, at.x, at.y + 0.3, at.z, 6, 0.4 * scale, 0.1, 0.4 * scale, 0.1);
        level.sendParticles(ParticleTypes.LARGE_SMOKE, at.x, at.y + 0.3, at.z, 8, 0.6 * scale, 0.2, 0.6 * scale, 0.02);
        level.playSound(null, at.x, at.y, at.z, SoundEvents.MACE_SMASH_GROUND_HEAVY, SoundSource.HOSTILE, 1.6f, 0.7f);
        level.playSound(null, at.x, at.y, at.z, SoundEvents.ANVIL_LAND, SoundSource.HOSTILE, 0.8f, 0.5f);
        LivingEntity target = getTarget();
        if (target != null && target.isAlive()) {
            double reach = 1.7 * scale + target.getBbWidth() * 0.5;
            boolean underHammer = target.position().distanceToSqr(at) <= reach * reach && Math.abs(target.getY() - at.y) < 2.5 * scale;
            if (underHammer || isWithinMeleeAttackRange(target)) {
                doHurtTarget(level, target);
            }
        }
    }

    /** The hammer's blow: knockback from the attribute, plus a lift. */
    @Override
    public boolean doHurtTarget(ServerLevel level, Entity target) {
        boolean hit = super.doHurtTarget(level, target);
        if (hit) {
            target.push(0, 0.35, 0);
            target.syncVelocity = true;
        }
        return hit;
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
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.IRON_GOLEM_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.IRON_GOLEM_DEATH;
    }

    @Override
    protected void playStepSound(BlockPos pos, BlockState state) {
        playSound(SoundEvents.IRON_GOLEM_STEP, 1.2f, 0.55f);
    }

    @Override
    public float getVoicePitch() {
        return super.getVoicePitch() * 0.6f;
    }

    /**
     * Vanilla melee chasing, but the attack is the telegraphed slam: in reach, with the last slam finished, it starts a
     * new one (the blow lands from {@link #tick()}); while slamming it stands its ground facing the target.
     */
    static final class HammerAttackGoal extends MeleeAttackGoal {
        private final IronThaneEntity thane;

        HammerAttackGoal(IronThaneEntity thane) {
            super(thane, 1.0, true);
            this.thane = thane;
        }

        @Override
        public void tick() {
            LivingEntity target = thane.getTarget();
            if (thane.isSlamming()) {
                thane.getNavigation().stop();
                if (target != null) {
                    thane.getLookControl().setLookAt(target, 30f, 30f);
                    if (thane.impactIn > 0) {
                        // turn the whole body to the target while the hammer is up, so the blow lands where it's aimed
                        float want = (float) (Mth.atan2(target.getZ() - thane.getZ(), target.getX() - thane.getX()) * Mth.RAD_TO_DEG) - 90f;
                        float yaw = Mth.approachDegrees(thane.getYRot(), want, 12f);
                        thane.setYRot(yaw);
                        thane.setYBodyRot(yaw);
                    }
                }
                return;
            }
            super.tick();
        }

        @Override
        protected void checkAndPerformAttack(LivingEntity target) {
            if (!thane.isSlamming() && canPerformAttack(target)) {
                resetAttackCooldown();
                thane.startSlam();
            }
        }
    }
}
