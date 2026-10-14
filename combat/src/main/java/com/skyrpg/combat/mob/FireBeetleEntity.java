package com.skyrpg.combat.mob;

import java.util.EnumSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySelector;
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
import net.minecraft.world.level.pathfinder.PathType;
import net.minecraft.world.phys.Vec3;

/**
 * Fire Beetle: a big six-legged beetle of the Magma Rifts with a magma-cracked shell. Fire-immune and tough, its bites
 * set you alight. About every eight seconds, when its target is 4 to 12 blocks away and in sight, it charges: it digs in
 * for a second (a synced wind-up pose, its wing cases lift, flames and dust at its feet), then dashes in a straight line
 * and slams whoever it runs into for heavy damage, knockback and fire. A wall or a hit ends the dash early.
 * <p>
 * The charge is one synced countdown ({@link #getChargeTicks}) the client model poses from: wind-up while above
 * {@code DASH_TICKS + RECOVER_TICKS}, dash while above {@code RECOVER_TICKS}, then the recovery.
 */
public class FireBeetleEntity extends Monster {
    public static final int WINDUP_TICKS = 20;
    public static final int DASH_TICKS = 14;
    public static final int RECOVER_TICKS = 10;
    /** The whole charge, from the first wind-up tick to the end of the recovery. */
    public static final int CHARGE_POSE_TICKS = WINDUP_TICKS + DASH_TICKS + RECOVER_TICKS;
    static final int CHARGE_COOLDOWN = 150;
    static final double CHARGE_MIN_RANGE = 4;
    static final double CHARGE_MAX_RANGE = 12;
    /** Blocks per tick while dashing (about 12 blocks over the whole dash). */
    static final double DASH_SPEED = 0.85;
    /** Charge damage, as a multiple of the beetle's (levelled) attack damage, and its knockback strength. */
    static final float CHARGE_DAMAGE = 1.8f;
    static final double CHARGE_KNOCKBACK = 1.5;
    static final float BITE_BURN_SECONDS = 4f;
    static final float CHARGE_BURN_SECONDS = 5f;

    private static final EntityDataAccessor<Integer> CHARGE = SynchedEntityData.defineId(FireBeetleEntity.class, EntityDataSerializers.INT);

    private int chargeCooldown = 60;

    public FireBeetleEntity(EntityType<? extends Monster> type, Level level) {
        super(type, level);
        // At home on magma and around fire; it may wade through lava but prefers not to.
        setPathfindingMalus(PathType.LAVA, 8f);
        setPathfindingMalus(PathType.FIRE, 0f);
        setPathfindingMalus(PathType.FIRE_IN_NEIGHBOR, 0f);
        xpReward = 10;
    }

    static AttributeSupplier.Builder attributes() {
        return Monster.createMonsterAttributes().add(Attributes.MAX_HEALTH, 40).add(Attributes.ARMOR, 8).add(Attributes.ARMOR_TOUGHNESS, 2)
                .add(Attributes.ATTACK_DAMAGE, 6).add(Attributes.MOVEMENT_SPEED, 0.23).add(Attributes.KNOCKBACK_RESISTANCE, 0.75)
                .add(Attributes.FOLLOW_RANGE, 24);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(1, new FloatGoal(this));
        goalSelector.addGoal(2, new ChargeGoal(this));
        goalSelector.addGoal(3, new MeleeAttackGoal(this, 1.0, true));
        goalSelector.addGoal(5, new WaterAvoidingRandomStrollGoal(this, 0.8));
        goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 10f));
        goalSelector.addGoal(7, new RandomLookAroundGoal(this));
        targetSelector.addGoal(1, new HurtByTargetGoal(this));
        targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(CHARGE, 0);
    }

    /** Ticks left of the charge (0 when not charging); counts down every server tick while the charge runs. */
    public int getChargeTicks() {
        return getEntityData().get(CHARGE);
    }

    void setChargeTicks(int ticks) {
        getEntityData().set(CHARGE, ticks);
    }

    public boolean isWindingUp() {
        return getChargeTicks() > DASH_TICKS + RECOVER_TICKS;
    }

    public boolean isDashing() {
        int t = getChargeTicks();
        return t > RECOVER_TICKS && t <= DASH_TICKS + RECOVER_TICKS;
    }

    @Override
    protected void customServerAiStep(ServerLevel level) {
        super.customServerAiStep(level);
        if (chargeCooldown > 0) {
            chargeCooldown--;
        }
    }

    @Override
    public void aiStep() {
        super.aiStep();
        // embers rise from the cracks now and then
        if (level().isClientSide() && getRandom().nextInt(7) == 0) {
            level().addParticle(ParticleTypes.SMALL_FLAME, getRandomX(0.5), getY() + 0.55 + getRandom().nextDouble() * 0.25, getRandomZ(0.5),
                    0, 0.015, 0);
        }
    }

    /** Bites set the target alight. */
    @Override
    public boolean doHurtTarget(ServerLevel level, Entity target) {
        boolean hit = super.doHurtTarget(level, target);
        if (hit) {
            target.igniteForSeconds(BITE_BURN_SECONDS);
        }
        return hit;
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return SoundEvents.SPIDER_AMBIENT;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.ARMADILLO_HURT_REDUCED;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.SPIDER_DEATH;
    }

    @Override
    protected void playStepSound(BlockPos pos, BlockState state) {
        playSound(SoundEvents.SPIDER_STEP, 0.2f, 0.6f);
    }

    @Override
    public float getVoicePitch() {
        return 0.6f + (getRandom().nextFloat() - 0.5f) * 0.15f;
    }

    /** The charge: wind-up, dash, recovery, then the cooldown. */
    static final class ChargeGoal extends Goal {
        private final FireBeetleEntity beetle;
        private Vec3 dir = Vec3.ZERO;

        ChargeGoal(FireBeetleEntity beetle) {
            this.beetle = beetle;
            setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK, Goal.Flag.JUMP));
        }

        @Override
        public boolean canUse() {
            LivingEntity target = beetle.getTarget();
            if (target == null || !target.isAlive() || beetle.chargeCooldown > 0 || !beetle.onGround() || beetle.isPassenger()
                    || !EntitySelector.NO_CREATIVE_OR_SPECTATOR.test(target)) {
                return false;
            }
            double d = beetle.distanceToSqr(target);
            return d >= CHARGE_MIN_RANGE * CHARGE_MIN_RANGE && d <= CHARGE_MAX_RANGE * CHARGE_MAX_RANGE
                    && Math.abs(target.getY() - beetle.getY()) < 3 && beetle.hasLineOfSight(target);
        }

        @Override
        public boolean canContinueToUse() {
            return beetle.isAlive() && beetle.getChargeTicks() > 0;
        }

        @Override
        public boolean requiresUpdateEveryTick() {
            return true;
        }

        @Override
        public void start() {
            beetle.setChargeTicks(CHARGE_POSE_TICKS);
            beetle.getNavigation().stop();
            dir = Vec3.ZERO;
            beetle.playSound(SoundEvents.GOAT_PREPARE_RAM, 1.4f, 0.55f);
        }

        @Override
        public void stop() {
            beetle.setChargeTicks(0);
            beetle.chargeCooldown = CHARGE_COOLDOWN + beetle.getRandom().nextInt(40);
        }

        @Override
        public void tick() {
            ServerLevel level = getServerLevel(beetle);
            LivingEntity target = beetle.getTarget();
            int t = beetle.getChargeTicks();
            if (t > DASH_TICKS + RECOVER_TICKS) {
                windUp(level, target, t);
            } else if (t > RECOVER_TICKS) {
                if (dash(level, t)) {
                    beetle.setChargeTicks(RECOVER_TICKS);
                    return;
                }
            } else {
                beetle.getNavigation().stop();
                beetle.setDeltaMovement(beetle.getDeltaMovement().multiply(0.6, 1, 0.6));
            }
            beetle.setChargeTicks(t - 1);
        }

        /** Digs in facing the target, flames and dust at its feet; on the last tick the line of the dash is locked. */
        private void windUp(ServerLevel level, LivingEntity target, int t) {
            beetle.getNavigation().stop();
            beetle.setDeltaMovement(beetle.getDeltaMovement().multiply(0.4, 1, 0.4));
            if (target != null) {
                beetle.getLookControl().setLookAt(target, 30f, 30f);
                face(target.getX() - beetle.getX(), target.getZ() - beetle.getZ(), 18f);
            }
            double x = beetle.getX();
            double y = beetle.getY();
            double z = beetle.getZ();
            level.sendParticles(ParticleTypes.FLAME, x, y + 0.3, z, 3, 0.55, 0.2, 0.55, 0.01);
            level.sendParticles(ParticleTypes.SMOKE, x, y + 0.5, z, 2, 0.5, 0.2, 0.5, 0.01);
            BlockState floor = beetle.getBlockStateOn();
            if (!floor.isAir()) {
                level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, floor), x, y + 0.05, z, 4, 0.6, 0.05, 0.6, 0.1);
            }
            if (t % 5 == 0) {
                level.sendParticles(ParticleTypes.LAVA, x, y + 0.6, z, 2, 0.3, 0.1, 0.3, 0);
            }
            if (t == DASH_TICKS + RECOVER_TICKS + 1) {
                Vec3 to = target != null ? target.position().subtract(beetle.position()) : Vec3.ZERO;
                dir = new Vec3(to.x, 0, to.z);
                if (dir.lengthSqr() < 1.0E-4) {
                    float yaw = beetle.getYRot() * Mth.DEG_TO_RAD;
                    dir = new Vec3(-Mth.sin(yaw), 0, Mth.cos(yaw));
                }
                dir = dir.normalize();
                beetle.playSound(SoundEvents.FIRECHARGE_USE, 1.3f, 0.6f);
            }
        }

        /** One tick of the dash; true when it ends early (it hit someone, or a wall). */
        private boolean dash(ServerLevel level, int t) {
            if (dir.lengthSqr() < 1.0E-4) {
                float yaw = beetle.getYRot() * Mth.DEG_TO_RAD;
                dir = new Vec3(-Mth.sin(yaw), 0, Mth.cos(yaw));
            }
            Vec3 v = beetle.getDeltaMovement();
            beetle.setDeltaMovement(dir.x * DASH_SPEED, v.y, dir.z * DASH_SPEED);
            face(dir.x, dir.z, 360f);
            double x = beetle.getX();
            double y = beetle.getY();
            double z = beetle.getZ();
            level.sendParticles(ParticleTypes.FLAME, x, y + 0.2, z, 4, 0.5, 0.15, 0.5, 0.02);
            level.sendParticles(ParticleTypes.LARGE_SMOKE, x - dir.x * 0.8, y + 0.4, z - dir.z * 0.8, 1, 0.3, 0.1, 0.3, 0.01);
            BlockState floor = beetle.getBlockStateOn();
            if (!floor.isAir()) {
                level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, floor), x, y + 0.05, z, 5, 0.6, 0.05, 0.6, 0.15);
            }
            boolean hit = false;
            for (LivingEntity victim : level.getEntitiesOfClass(LivingEntity.class, beetle.getBoundingBox().inflate(0.35), this::canHit)) {
                impact(level, victim);
                hit = true;
            }
            if (hit) {
                beetle.setDeltaMovement(beetle.getDeltaMovement().multiply(0.15, 1, 0.15));
                return true;
            }
            if (beetle.horizontalCollision && t < DASH_TICKS + RECOVER_TICKS) {
                // slammed into a wall: a jolt, and it stops
                beetle.setDeltaMovement(0, v.y, 0);
                level.sendParticles(ParticleTypes.POOF, x + dir.x, y + 0.5, z + dir.z, 10, 0.3, 0.3, 0.3, 0.05);
                level.sendParticles(ParticleTypes.LAVA, x + dir.x, y + 0.5, z + dir.z, 4, 0.2, 0.2, 0.2, 0);
                beetle.playSound(SoundEvents.GOAT_RAM_IMPACT, 1.0f, 0.6f);
                return true;
            }
            return false;
        }

        private boolean canHit(LivingEntity entity) {
            return entity != beetle && entity.isAlive() && !(entity instanceof FireBeetleEntity) && EntitySelector.NO_CREATIVE_OR_SPECTATOR.test(entity)
                    && (entity == beetle.getTarget() || entity instanceof Player);
        }

        /** Heavy damage, a big shove along the dash, and fire. */
        private void impact(ServerLevel level, LivingEntity victim) {
            float damage = (float) beetle.getAttributeValue(Attributes.ATTACK_DAMAGE) * CHARGE_DAMAGE;
            DamageSource source = beetle.damageSources().mobAttack(beetle);
            if (victim.hurtServer(level, source, damage)) {
                victim.knockback(CHARGE_KNOCKBACK, -dir.x, -dir.z, source, damage);
                victim.igniteForSeconds(CHARGE_BURN_SECONDS);
                beetle.setLastHurtMob(victim);
            }
            level.sendParticles(ParticleTypes.FLAME, victim.getX(), victim.getY(0.5), victim.getZ(), 24, 0.4, 0.4, 0.4, 0.08);
            level.sendParticles(ParticleTypes.LAVA, victim.getX(), victim.getY(0.5), victim.getZ(), 6, 0.3, 0.3, 0.3, 0);
            beetle.playSound(SoundEvents.GOAT_RAM_IMPACT, 1.3f, 0.75f);
        }

        /** Turns body and head towards a horizontal direction, at most {@code maxTurn} degrees this tick. */
        private void face(double dx, double dz, float maxTurn) {
            if (dx * dx + dz * dz < 1.0E-6) {
                return;
            }
            float yaw = (float) (Mth.atan2(dz, dx) * Mth.RAD_TO_DEG) - 90f;
            float turned = Mth.approachDegrees(beetle.getYRot(), yaw, maxTurn);
            beetle.setYRot(turned);
            beetle.setYBodyRot(turned);
            beetle.setYHeadRot(turned);
        }
    }
}
