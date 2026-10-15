package com.skyrpg.brainrots;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;

/**
 * A wandering brainrot (one entity type, fifteen kinds: {@link BrainrotKind}). It can't be hurt; right-click it to
 * catch it for your album ({@link Brainrots#capture}). When someone comes close it says its name. Walkers stroll,
 * hoppers bounce along, spinners twirl (drawn by the client), fliers circle high above where they appeared, and
 * floaters drift a little above the ground. It wanders off after {@link #LIFETIME} ticks if nobody catches it.
 */
public class BrainrotEntity extends PathfinderMob {
    private static final EntityDataAccessor<Integer> KIND = SynchedEntityData.defineId(BrainrotEntity.class, EntityDataSerializers.INT);
    static final int LIFETIME = 20 * 60 * 6;
    private static final double HEAR_RANGE = 10;
    private static final int SWOOP_TICKS = 220;

    /** The kind once known (null while the entity is being built, before its data exists). */
    private BrainrotKind kindCache;
    private int life;
    private int voiceCooldown = 40;
    private int hopCooldown;
    private Vec3 home;
    private double orbit;
    private Vec3 drift;
    /** Fliers come down to head height now and then (that's when you catch them): ticks left low, ticks until next. */
    private int swoop;
    private int swoopCooldown = 300;
    boolean caught;

    public BrainrotEntity(EntityType<? extends PathfinderMob> type, Level level) {
        super(type, level);
        orbit = random.nextDouble() * Math.PI * 2;
    }

    static AttributeSupplier.Builder attributes() {
        return Mob.createMobAttributes().add(Attributes.MAX_HEALTH, 20).add(Attributes.MOVEMENT_SPEED, 0.24).add(Attributes.FOLLOW_RANGE, 16);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(KIND, 0);
    }

    public BrainrotKind kind() {
        return BrainrotKind.byOrdinal(entityData.get(KIND));
    }

    /** Sets the kind; {@code named} shows its name above it (the album's pictures leave it off). */
    public void setKind(BrainrotKind kind, boolean named) {
        entityData.set(KIND, kind.ordinal());
        kindCache = kind;
        setNoGravity(kind.move == BrainrotKind.Move.FLY || kind.move == BrainrotKind.Move.FLOAT);
        if (named) {
            setCustomName(Component.literal(kind.displayName).withColor(kind.rarity.color & 0xFFFFFF));
            setCustomNameVisible(true);
        }
        refreshDimensions();
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> accessor) {
        super.onSyncedDataUpdated(accessor);
        if (KIND.equals(accessor)) {
            kindCache = kind();
            refreshDimensions();
        }
    }

    @Override
    protected EntityDimensions getDefaultDimensions(Pose pose) {
        return kindCache == null ? super.getDefaultDimensions(pose) : EntityDimensions.scalable(kindCache.width, kindCache.height);
    }

    private boolean walks() {
        return kindCache == null || (kindCache.move != BrainrotKind.Move.FLY && kindCache.move != BrainrotKind.Move.FLOAT);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(5, new WaterAvoidingRandomStrollGoal(this, 0.9) {
            @Override
            public boolean canUse() {
                return walks() && super.canUse();
            }
        });
        goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 10f));
        goalSelector.addGoal(7, new RandomLookAroundGoal(this));
    }

    // ------------------------------------------------------------------ life

    @Override
    public void tick() {
        super.tick();
        if (!(level() instanceof ServerLevel level) || kindCache == null) {
            return;
        }
        if (++life > LIFETIME) {
            level.sendParticles(ParticleTypes.POOF, getX(), getY() + getBbHeight() / 2, getZ(), 20, 0.3, 0.3, 0.3, 0.02);
            discard();
            return;
        }
        if (voiceCooldown > 0) {
            voiceCooldown--;
        } else if (tickCount % 10 == 0) {
            Player near = level.getNearestPlayer(this, HEAR_RANGE);
            if (near != null && !near.isSpectator()) {
                sayName(1.0f);
            }
        }
        switch (kindCache.move) {
            case HOP -> hop();
            case FLY -> fly(level);
            case FLOAT -> drift(level);
            default -> {
            }
        }
    }

    /** The narrator says its name, with a few music notes. */
    void sayName(float volume) {
        if (kindCache == null || kindCache.voice == null || !(level() instanceof ServerLevel level)) {
            return;
        }
        level.playSound(null, getX(), getY(), getZ(), kindCache.voice, SoundSource.NEUTRAL, volume * 1.4f, 1f);
        level.sendParticles(ParticleTypes.NOTE, getX(), getY() + getBbHeight() + 0.4, getZ(), 4, 0.4, 0.2, 0.4, 1);
        voiceCooldown = 300 + random.nextInt(240);
    }

    private void hop() {
        if (hopCooldown > 0) {
            hopCooldown--;
        } else if (onGround()) {
            getJumpControl().jump();
            hopCooldown = 12 + random.nextInt(18);
        }
    }

    /**
     * Circles high above the spot it appeared at, banking as it goes. Every 20-35 seconds (twice as often with a
     * player close) it swoops down to head height and circles slowly for a while: the moment to catch it.
     */
    private void fly(ServerLevel level) {
        if (home == null) {
            home = position();
        }
        boolean low = swoop > 0;
        if (low) {
            swoop--;
        } else {
            swoopCooldown -= level.getNearestPlayer(this, 16) != null ? 2 : 1;
            if (swoopCooldown <= 0) {
                swoop = SWOOP_TICKS;
                swoopCooldown = 400 + random.nextInt(300);
            }
        }
        orbit += low ? 0.009 : 0.025;
        double radius = 9;
        double x = home.x + Math.cos(orbit) * radius;
        double z = home.z + Math.sin(orbit) * radius;
        int ground = level.getHeight(Heightmap.Types.MOTION_BLOCKING, Mth.floor(getX()), Mth.floor(getZ()));
        double y = low ? ground + 1.2 + Math.sin(tickCount * 0.1) * 0.2 : Math.max(ground + 9, home.y) + Math.sin(tickCount * 0.04) * 1.2;
        // Dives (and climbs) at full speed; once down at head height it slows right down.
        steer(new Vec3(x, y, z), low && getY() - y < 1.5 ? 0.1 : 0.32);
    }

    /** Hovers a little above the ground, drifting slowly between nearby spots. */
    private void drift(ServerLevel level) {
        if (home == null) {
            home = position();
        }
        if (drift == null || tickCount % 140 == 0 || position().distanceToSqr(drift) < 1) {
            drift = home.add(random.nextDouble() * 12 - 6, 0, random.nextDouble() * 12 - 6);
        }
        int ground = level.getHeight(Heightmap.Types.MOTION_BLOCKING, Mth.floor(getX()), Mth.floor(getZ()));
        double y = ground + 1.4 + Math.sin(tickCount * 0.05) * 0.35;
        steer(new Vec3(drift.x, y, drift.z), 0.07);
    }

    private void steer(Vec3 target, double maxSpeed) {
        Vec3 to = target.subtract(position());
        double length = to.length();
        Vec3 want = length > maxSpeed ? to.scale(maxSpeed / length) : to;
        Vec3 motion = getDeltaMovement().lerp(want, 0.08);
        setDeltaMovement(motion);
        double horizontal = motion.horizontalDistance();
        if (horizontal > 0.01) {
            float yaw = (float) (Mth.atan2(motion.z, motion.x) * Mth.RAD_TO_DEG) - 90f;
            setYRot(yaw);
            yBodyRot = yaw;
            yHeadRot = yaw;
        }
    }

    // ------------------------------------------------------------------ catching, and nothing hurts it

    @Override
    protected InteractionResult mobInteract(Player player, InteractionHand hand) {
        if (hand != InteractionHand.MAIN_HAND) {
            return InteractionResult.PASS;
        }
        if (player instanceof ServerPlayer serverPlayer) {
            Brainrots.capture(serverPlayer, this);
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
        if (source.isCreativePlayer() || source.is(net.minecraft.tags.DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            return super.hurtServer(level, source, amount);
        }
        // Hitting one just makes it hop and shout its name.
        if (source.getEntity() instanceof Player && onGround()) {
            getJumpControl().jump();
            if (voiceCooldown > 60) {
                voiceCooldown = 60;
            }
        }
        return false;
    }

    @Override
    public boolean causeFallDamage(double distance, float multiplier, DamageSource source) {
        return false;
    }

    @Override
    public boolean canBeLeashed() {
        return false;
    }

    @Override
    public boolean removeWhenFarAway(double distance) {
        return true;
    }

    // ------------------------------------------------------------------ saving

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output);
        output.putString("Kind", kind().id);
        output.putInt("Life", life);
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        BrainrotKind kind = BrainrotKind.byId(input.getStringOr("Kind", BrainrotKind.TUNG_TUNG_SAHUR.id));
        setKind(kind == null ? BrainrotKind.TUNG_TUNG_SAHUR : kind, true);
        life = input.getIntOr("Life", 0);
    }

    static boolean isSolidGround(Level level, BlockPos below) {
        return level.getBlockState(below).isFaceSturdy(level, below, net.minecraft.core.Direction.UP) && level.getFluidState(below.above()).isEmpty();
    }
}
