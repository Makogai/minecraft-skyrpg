package com.skyrpg.combat.mob;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
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

/**
 * Dwarven Construct: a stone-and-brass automaton of the Dwarven Halls (see docs/design/the_deep.md). Slow and heavily
 * armoured: projectiles glance off for half damage, and its heavy punch knocks you flying. Drawn with its own model
 * (DwarvenConstructModel). The dungeon system also spawns it scaled up as the Forgeguard champion; its walk cycle
 * slows with its scale so a big one strides instead of shuffling.
 */
public class DwarvenConstructEntity extends Monster {
    /** What projectile damage is multiplied by. */
    public static final float PROJECTILE_FACTOR = 0.5f;

    public DwarvenConstructEntity(EntityType<? extends Monster> type, Level level) {
        super(type, level);
    }

    static AttributeSupplier.Builder attributes() {
        return Monster.createMonsterAttributes().add(Attributes.MAX_HEALTH, 60).add(Attributes.ARMOR, 10).add(Attributes.ARMOR_TOUGHNESS, 4)
                .add(Attributes.MOVEMENT_SPEED, 0.18).add(Attributes.ATTACK_DAMAGE, 8).add(Attributes.ATTACK_KNOCKBACK, 1.6)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.75).add(Attributes.FOLLOW_RANGE, 24);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.0, false));
        goalSelector.addGoal(6, new WaterAvoidingRandomStrollGoal(this, 0.8));
        goalSelector.addGoal(7, new LookAtPlayerGoal(this, Player.class, 8f));
        goalSelector.addGoal(8, new RandomLookAroundGoal(this));
        targetSelector.addGoal(1, new HurtByTargetGoal(this));
        targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
    }

    /** Arrows and other projectiles glance off the armour: half damage, with a spark and a clang. */
    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
        if (source.is(DamageTypeTags.IS_PROJECTILE) && amount > 0) {
            amount *= PROJECTILE_FACTOR;
            level.sendParticles(ParticleTypes.ELECTRIC_SPARK, getX(), getY(0.6), getZ(), 6, 0.25, 0.3, 0.25, 0.08);
            playSound(SoundEvents.SHIELD_BLOCK.value(), 0.7f, 0.6f + getRandom().nextFloat() * 0.2f);
        }
        return super.hurtServer(level, source, amount);
    }

    /** The heavy punch: on top of its knockback attribute it lifts you off your feet. */
    @Override
    public boolean doHurtTarget(ServerLevel level, Entity target) {
        boolean hit = super.doHurtTarget(level, target);
        if (hit) {
            target.push(0, 0.22, 0);
            target.syncVelocity = true;
            playSound(SoundEvents.IRON_GOLEM_ATTACK, 1.0f, 0.7f);
            level.sendParticles(ParticleTypes.CRIT, target.getX(), target.getY(0.5), target.getZ(), 10, 0.3, 0.3, 0.3, 0.2);
        }
        return hit;
    }

    /** A scaled-up construct (the Forgeguard) takes longer strides: the walk cycle advances by distance over scale. */
    @Override
    protected void updateWalkAnimation(float distance) {
        super.updateWalkAnimation(distance / Math.max(0.5f, getScale()));
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
        playSound(SoundEvents.IRON_GOLEM_STEP, 0.6f, 0.8f);
    }

    @Override
    public float getVoicePitch() {
        return super.getVoicePitch() * 0.8f;
    }
}
