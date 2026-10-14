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
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
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
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Bone Giant: a hunched giant skeleton of the Bone Sea (docs/design/nether.md), drawn by BoneGiantModel. Slow, tough and
 * fire-immune; it strides over soul sand without sinking in, and despawns like any monster.
 * <ul>
 *   <li>Its attack is a heavy swing of its femur club: it raises the club ({@link #getSwingTicks()}, synced for the pose)
 *   and brings it down {@link #SWING_IMPACT} ticks later on the ground in front of it, hitting the target if it's still
 *   there, with a lot of knockback and a lift.</li>
 *   <li>About every ten seconds, with its target close, it does a <b>bone quake</b>: it rears up roaring for a second (a
 *   synced pose, {@link #getQuakeTicks()}, while a ring of soul fire marks the reach on the ground), then stomps. Every
 *   player within {@link #QUAKE_RADIUS} blocks takes damage, is thrown up a little and is slowed.</li>
 * </ul>
 * Soul fire flickers out of the crack in its skull and souls drift out of its ribcage (client side).
 */
public class BoneGiantEntity extends Monster {
    /** Ticks the club swing pose lasts: raise 0-8, smash 8-11, hold to 13, recover to 20 (see BoneGiantModel). */
    public static final int SWING_TICKS = 20;
    /** Ticks after the swing starts that the club lands. */
    public static final int SWING_IMPACT = 11;
    /** Ticks the bone quake pose lasts: rear up 0-12, hold to 20 (the telegraph), stomp 20-23, hold to 26, recover to 34. */
    public static final int QUAKE_TICKS = 34;
    /** Ticks after the quake starts that the stomp lands. */
    public static final int QUAKE_IMPACT = 22;
    /** Blocks (times the giant's scale) the quake reaches. */
    public static final double QUAKE_RADIUS = 4.0;
    static final int QUAKE_COOLDOWN = 200;
    /** It only quakes when its target is this close (blocks, times its scale). */
    static final double QUAKE_TRIGGER_RANGE = 5.0;
    /** Quake damage, as a multiple of the giant's (levelled) attack damage. */
    static final float QUAKE_DAMAGE = 0.75f;
    static final int QUAKE_SLOW_TICKS = 60;
    /** Ticks from one club swing's start to the next. */
    static final int SWING_COOLDOWN = 32;
    /** How far in front of the giant (blocks, times its scale) the club lands. */
    private static final double SWING_REACH = 1.8;
    /** Extra melee reach the club gives (blocks). */
    private static final double CLUB_REACH = 0.9;

    private static final EntityDataAccessor<Integer> SWING = SynchedEntityData.defineId(BoneGiantEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> QUAKE = SynchedEntityData.defineId(BoneGiantEntity.class, EntityDataSerializers.INT);

    /** Server only: ticks until the club lands / the stomp lands (0 when not swinging / quaking). */
    private int swingImpactIn;
    private int quakeImpactIn;
    private int swingCooldown;
    private int quakeCooldown = 80;

    public BoneGiantEntity(EntityType<? extends Monster> type, Level level) {
        super(type, level);
        // Fire-immune: fire on the path is no obstacle. Lava still is (it would sink in).
        setPathfindingMalus(PathType.FIRE, 0f);
        setPathfindingMalus(PathType.FIRE_IN_NEIGHBOR, 0f);
        xpReward = 20;
    }

    static AttributeSupplier.Builder attributes() {
        return Monster.createMonsterAttributes().add(Attributes.MAX_HEALTH, 110).add(Attributes.ARMOR, 8).add(Attributes.ARMOR_TOUGHNESS, 2)
                .add(Attributes.MOVEMENT_SPEED, 0.2).add(Attributes.ATTACK_DAMAGE, 12).add(Attributes.ATTACK_KNOCKBACK, 1.8)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.8).add(Attributes.FOLLOW_RANGE, 28).add(Attributes.STEP_HEIGHT, 1.0);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(SWING, 0);
        builder.define(QUAKE, 0);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(1, new QuakeGoal(this));
        goalSelector.addGoal(2, new ClubAttackGoal(this));
        goalSelector.addGoal(6, new WaterAvoidingRandomStrollGoal(this, 0.7));
        goalSelector.addGoal(7, new LookAtPlayerGoal(this, Player.class, 12f));
        goalSelector.addGoal(8, new RandomLookAroundGoal(this));
        targetSelector.addGoal(1, new HurtByTargetGoal(this));
        targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
    }

    /** Ticks left of the club swing pose (0 when not swinging); the client model poses itself from it. */
    public int getSwingTicks() {
        return getEntityData().get(SWING);
    }

    /** Ticks left of the bone quake pose (0 when not quaking). */
    public int getQuakeTicks() {
        return getEntityData().get(QUAKE);
    }

    public boolean isBusy() {
        return getSwingTicks() > 0 || getQuakeTicks() > 0;
    }

    void startSwing() {
        getEntityData().set(SWING, SWING_TICKS);
        swingImpactIn = SWING_IMPACT;
        swingCooldown = SWING_COOLDOWN;
        playSound(SoundEvents.RAVAGER_ATTACK, 1.2f, 0.55f);
    }

    void startQuake() {
        getEntityData().set(QUAKE, QUAKE_TICKS);
        quakeImpactIn = QUAKE_IMPACT;
        quakeCooldown = QUAKE_COOLDOWN + getRandom().nextInt(40);
        playSound(SoundEvents.RAVAGER_ROAR, 1.6f, 0.65f);
        playSound(SoundEvents.SOUL_ESCAPE.value(), 2.0f, 0.5f);
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide()) {
            return;
        }
        ServerLevel level = (ServerLevel) level();
        int swing = getSwingTicks();
        if (swing > 0) {
            getEntityData().set(SWING, swing - 1);
        }
        int quake = getQuakeTicks();
        if (quake > 0) {
            int elapsed = QUAKE_TICKS - quake;
            if (elapsed < QUAKE_IMPACT) {
                telegraph(level, elapsed);
            }
            getEntityData().set(QUAKE, quake - 1);
        }
        if (swingImpactIn > 0 && --swingImpactIn == 0 && isAlive()) {
            clubImpact(level);
        }
        if (quakeImpactIn > 0 && --quakeImpactIn == 0 && isAlive()) {
            quakeImpact(level);
        }
        if (swingCooldown > 0) {
            swingCooldown--;
        }
        if (quakeCooldown > 0) {
            quakeCooldown--;
        }
    }

    /** Soul fire from the crack in its skull, souls drifting out of the ribcage. */
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
        if (random.nextInt(5) == 0) {
            double f = 0.8 * scale;
            level().addParticle(ParticleTypes.SOUL_FIRE_FLAME, getX() + fx * f + (random.nextDouble() - 0.5) * 0.3 * scale,
                    getY() + 3.45 * scale, getZ() + fz * f + (random.nextDouble() - 0.5) * 0.3 * scale, 0, 0.025, 0);
        }
        if (random.nextInt(12) == 0) {
            double f = 0.45 * scale;
            level().addParticle(ParticleTypes.SOUL, getX() + fx * f + (random.nextDouble() - 0.5) * 0.5 * scale, getY() + 2.1 * scale,
                    getZ() + fz * f + (random.nextDouble() - 0.5) * 0.5 * scale, 0, 0.03, 0);
        }
    }

    /** The quake's wind-up: a ring of soul fire where it will reach, the ground trembling inside it. */
    private void telegraph(ServerLevel level, int elapsed) {
        double radius = QUAKE_RADIUS * getScale();
        double x = getX();
        double y = getY() + 0.1;
        double z = getZ();
        if (elapsed % 2 == 0) {
            int points = 28;
            double spin = elapsed * 0.08;
            for (int i = 0; i < points; i++) {
                double a = spin + Math.PI * 2 * i / points;
                level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, x + Math.cos(a) * radius, y, z + Math.sin(a) * radius, 1, 0, 0.02, 0, 0.005);
            }
        }
        BlockState ground = getBlockStateOn();
        if (elapsed % 3 == 0 && !ground.isAir()) {
            level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, ground), x, y, z, 10, radius * 0.4, 0.05, radius * 0.4, 0.1);
        }
        if (elapsed == 12) {
            playSound(SoundEvents.BONE_BLOCK_BREAK, 1.5f, 0.5f);
        }
    }

    /** The stomp: a burst of dust and souls, and every player in reach is hurt, thrown up and slowed. */
    private void quakeImpact(ServerLevel level) {
        double radius = QUAKE_RADIUS * getScale();
        Vec3 c = position();
        BlockState ground = getBlockStateOn();
        if (!ground.isAir()) {
            for (double r = 1; r <= radius; r += 1) {
                int points = (int) (r * 6);
                for (int i = 0; i < points; i++) {
                    double a = Math.PI * 2 * i / points;
                    level.sendParticles(new BlockParticleOption(ParticleTypes.DUST_PILLAR, ground), c.x + Math.cos(a) * r, c.y + 0.1,
                            c.z + Math.sin(a) * r, 1, 0, 0, 0, 0);
                }
            }
        }
        level.sendParticles(ParticleTypes.EXPLOSION, c.x, c.y + 0.3, c.z, 1, 0, 0, 0, 0);
        level.sendParticles(ParticleTypes.SCULK_SOUL, c.x, c.y + 0.5, c.z, 24, radius * 0.4, 0.2, radius * 0.4, 0.05);
        level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, c.x, c.y + 0.2, c.z, 30, radius * 0.45, 0.1, radius * 0.45, 0.06);
        level.playSound(null, c.x, c.y, c.z, SoundEvents.MACE_SMASH_GROUND_HEAVY, SoundSource.HOSTILE, 2.0f, 0.55f);
        level.playSound(null, c.x, c.y, c.z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.HOSTILE, 0.9f, 0.5f);
        float damage = (float) getAttributeValue(Attributes.ATTACK_DAMAGE) * QUAKE_DAMAGE;
        DamageSource source = damageSources().mobAttack(this);
        AABB area = new AABB(c.x - radius, c.y - 2, c.z - radius, c.x + radius, c.y + 2.5, c.z + radius);
        for (Player player : level.getEntitiesOfClass(Player.class, area, p -> p.isAlive() && EntitySelector.NO_CREATIVE_OR_SPECTATOR.test(p))) {
            double dx = player.getX() - c.x;
            double dz = player.getZ() - c.z;
            double reach = radius + player.getBbWidth() * 0.5;
            if (dx * dx + dz * dz > reach * reach) {
                continue;
            }
            if (player.hurtServer(level, source, damage)) {
                double d = Math.max(0.5, Math.sqrt(dx * dx + dz * dz));
                player.push(dx / d * 0.5, 0.45, dz / d * 0.5);
                player.syncVelocity = true;
                setLastHurtMob(player);
            }
            player.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, QUAKE_SLOW_TICKS, 1), this);
        }
    }

    /** The club hits the ground in front: dust and a crack, and the target takes the blow if it's there. */
    private void clubImpact(ServerLevel level) {
        float yaw = yBodyRot * Mth.DEG_TO_RAD;
        double scale = getScale();
        Vec3 at = position().add(-Mth.sin(yaw) * SWING_REACH * scale, 0, Mth.cos(yaw) * SWING_REACH * scale);
        BlockState ground = level.getBlockState(BlockPos.containing(at.x, at.y - 0.5, at.z));
        if (!ground.isAir()) {
            level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, ground), at.x, at.y + 0.1, at.z, (int) (14 * scale),
                    0.4 * scale, 0.05, 0.4 * scale, 0.15);
        }
        level.sendParticles(ParticleTypes.SOUL, at.x, at.y + 0.3, at.z, 3, 0.3, 0.1, 0.3, 0.02);
        level.playSound(null, at.x, at.y, at.z, SoundEvents.MACE_SMASH_GROUND, SoundSource.HOSTILE, 1.3f, 0.6f);
        level.playSound(null, at.x, at.y, at.z, SoundEvents.BONE_BLOCK_BREAK, SoundSource.HOSTILE, 1.2f, 0.6f);
        LivingEntity target = getTarget();
        if (target != null && target.isAlive()) {
            double reach = 1.4 * scale + target.getBbWidth() * 0.5;
            double dx = target.getX() - at.x;
            double dz = target.getZ() - at.z;
            boolean underClub = dx * dx + dz * dz <= reach * reach && Math.abs(target.getY() - at.y) < 2.5 * scale;
            if (underClub || isWithinMeleeAttackRange(target)) {
                doHurtTarget(level, target);
            }
        }
    }

    /** The club's blow: knockback from the attribute, plus a lift. */
    @Override
    public boolean doHurtTarget(ServerLevel level, Entity target) {
        boolean hit = super.doHurtTarget(level, target);
        if (hit) {
            target.push(0, 0.3, 0);
            target.syncVelocity = true;
            level.sendParticles(ParticleTypes.CRIT, target.getX(), target.getY(0.5), target.getZ(), 10, 0.3, 0.3, 0.3, 0.2);
        }
        return hit;
    }

    /** The club reaches further than its arms. */
    @Override
    protected AABB getAttackBoundingBox(double reach) {
        return super.getAttackBoundingBox(reach + CLUB_REACH);
    }

    /** At home in the Bone Sea: soul sand and soul soil don't slow it down. */
    @Override
    protected float getBlockSpeedFactor() {
        if (getBlockStateOn().is(BlockTags.SOUL_SPEED_BLOCKS)) {
            return 1f;
        }
        return super.getBlockSpeedFactor();
    }

    /** A scaled-up giant takes longer strides: the walk cycle advances by distance over scale. */
    @Override
    protected void updateWalkAnimation(float distance) {
        super.updateWalkAnimation(distance / Math.max(0.5f, getScale()));
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return SoundEvents.SKELETON_AMBIENT;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.SKELETON_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.SKELETON_DEATH;
    }

    @Override
    protected void playStepSound(BlockPos pos, BlockState state) {
        playSound(SoundEvents.BONE_BLOCK_STEP, 1.0f, 0.5f);
        playSound(SoundEvents.SKELETON_STEP, 0.6f, 0.5f);
    }

    @Override
    public float getVoicePitch() {
        return super.getVoicePitch() * 0.5f;
    }

    /**
     * Vanilla melee chasing, but the attack is the club swing: in reach, with the last swing over, it starts a new one
     * (the blow lands from {@link #tick()}); while swinging or quaking it stands its ground facing the target.
     */
    static final class ClubAttackGoal extends MeleeAttackGoal {
        private final BoneGiantEntity giant;

        ClubAttackGoal(BoneGiantEntity giant) {
            super(giant, 1.0, true);
            this.giant = giant;
        }

        @Override
        public void tick() {
            LivingEntity target = giant.getTarget();
            if (giant.isBusy()) {
                giant.getNavigation().stop();
                if (target != null) {
                    giant.getLookControl().setLookAt(target, 30f, 30f);
                    if (giant.swingImpactIn > 0) {
                        turnTowards(giant, target, 10f);
                    }
                }
                return;
            }
            super.tick();
        }

        @Override
        protected void checkAndPerformAttack(LivingEntity target) {
            if (!giant.isBusy() && giant.swingCooldown == 0 && canPerformAttack(target)) {
                resetAttackCooldown();
                giant.startSwing();
            }
        }
    }

    /** The bone quake: with the target close and the cooldown over, it rears up, roars, and stomps (see {@link #tick()}). */
    static final class QuakeGoal extends Goal {
        private final BoneGiantEntity giant;

        QuakeGoal(BoneGiantEntity giant) {
            this.giant = giant;
            setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK, Goal.Flag.JUMP));
        }

        @Override
        public boolean canUse() {
            LivingEntity target = giant.getTarget();
            if (target == null || !target.isAlive() || giant.quakeCooldown > 0 || giant.isBusy() || !giant.onGround()
                    || !EntitySelector.NO_CREATIVE_OR_SPECTATOR.test(target)) {
                return false;
            }
            double range = QUAKE_TRIGGER_RANGE * giant.getScale();
            double dx = target.getX() - giant.getX();
            double dz = target.getZ() - giant.getZ();
            return dx * dx + dz * dz <= range * range && Math.abs(target.getY() - giant.getY()) < 3;
        }

        @Override
        public boolean canContinueToUse() {
            return giant.isAlive() && giant.getQuakeTicks() > 0;
        }

        @Override
        public boolean requiresUpdateEveryTick() {
            return true;
        }

        @Override
        public void start() {
            giant.getNavigation().stop();
            giant.startQuake();
        }

        @Override
        public void tick() {
            giant.getNavigation().stop();
            giant.setDeltaMovement(giant.getDeltaMovement().multiply(0.3, 1, 0.3));
            LivingEntity target = giant.getTarget();
            if (target != null && giant.quakeImpactIn > 0) {
                giant.getLookControl().setLookAt(target, 30f, 30f);
                turnTowards(giant, target, 8f);
            }
        }
    }

    /** Turns the whole body towards the target, at most {@code maxTurn} degrees this tick. */
    static void turnTowards(Monster mob, LivingEntity target, float maxTurn) {
        float want = (float) (Mth.atan2(target.getZ() - mob.getZ(), target.getX() - mob.getX()) * Mth.RAD_TO_DEG) - 90f;
        float yaw = Mth.approachDegrees(mob.getYRot(), want, maxTurn);
        mob.setYRot(yaw);
        mob.setYBodyRot(yaw);
    }
}
