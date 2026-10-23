package com.skyrpg.mounts;

import java.util.EnumSet;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.PathfinderMob;
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
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;

/**
 * The griffin: an eagle's head and wings on a lion's body ({@code skyrpg_mounts:griffin}).
 * <p>
 * <b>Wild</b> griffins live around nests, are neutral, and fight back when hit with a claw swipe and a leaping dive.
 * Feeding one raw meat has a 1-in-5 chance to tame it, and a tamed griffin turns into a Griffin Whistle item.
 * <b>Storm</b> griffins (entity tag {@link #STORM_TAG}, the Shattered Highlands boss body) are hostile to the nearest survival
 * player, persistent, untameable and unrideable; the world mod drives their special attacks itself.
 * <b>Tame</b> griffins are summoned from the stable ({@link Mounts}); only their owner rides them. A rider walks (jump takes
 * off), flies with WASD, climbs with jump, descends with sneak and glides faster while sprinting; flight burns
 * <b>stamina</b> (a synced fraction, refilled on the ground). Riders take no fall damage, and a rider thrown off in mid-air
 * gets Slow Falling.
 * <p>
 * Movement follows vanilla's controllable mounts (the Happy Ghast): {@link #getRiddenInput}, {@link #tickRidden} and
 * {@link #travel}. These run on the riding player's client, which owns the movement; the server tracks stamina from the
 * rider's input.
 */
public class GriffinEntity extends PathfinderMob {
    /** Entity tag of the boss body: hostile, untameable, unrideable, persistent. */
    public static final String STORM_TAG = "skyrpg_storm_griffin";
    private static final ResourceKey<Level> HUB = ResourceKey.create(Registries.DIMENSION, Identifier.fromNamespaceAndPath("skyrpg_hub", "hub"));

    /** Seconds of flight a full stamina bar gives a griffin with no stamina bonus. */
    public static final float BASE_FLIGHT_SECONDS = 14f;
    private static final float REFILL_SECONDS = 6f;
    /** Below this a griffin can't take off. */
    public static final float TAKEOFF_STAMINA = 0.1f;
    private static final int AWAY_AFTER_DISMOUNT = 30 * 20;
    private static final double AWAY_DISTANCE = 16;

    private static final EntityDataAccessor<Integer> COLOR = SynchedEntityData.defineId(GriffinEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<String> OWNER = SynchedEntityData.defineId(GriffinEntity.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<String> MOUNT_ID = SynchedEntityData.defineId(GriffinEntity.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<Float> STAMINA = SynchedEntityData.defineId(GriffinEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Boolean> GLIDING = SynchedEntityData.defineId(GriffinEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Integer> SPEED_BONUS = SynchedEntityData.defineId(GriffinEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> STAMINA_BONUS = SynchedEntityData.defineId(GriffinEntity.class, EntityDataSerializers.INT);

    /** Consecutive ticks off the ground; flight mode starts a few ticks in so steps and slopes don't count. */
    private int airTicks;
    private int takeoffGrace;
    /** Tick count when the owner last got off, or -1 while never ridden or being ridden. */
    private int dismountedAt = -1;
    private int flapSoundCooldown;
    private int diveCooldown = 60;
    /** Client animation: 0 folded, 1 flying; and 0 flapping, 1 gliding. Eased every tick. */
    public float flyAmount;
    public float flyAmountO;
    public float glideAmount;
    public float glideAmountO;

    public GriffinEntity(EntityType<? extends GriffinEntity> type, Level level) {
        super(type, level);
        xpReward = 15;
        if (!level.isClientSide()) {
            // every new griffin (wild, the Storm Griffin boss...) rolls a colour by rarity; callers and saves overwrite it
            getEntityData().set(COLOR, GriffinColor.random(random).ordinal());
        }
    }

    public static AttributeSupplier.Builder attributes() {
        // scale 1.3: bigger than a horse, so a rider sits on it rather than hiding it (hitbox and seat scale too)
        return PathfinderMob.createMobAttributes().add(Attributes.SCALE, 1.3).add(Attributes.MAX_HEALTH, 40).add(Attributes.ATTACK_DAMAGE, 7)
                .add(Attributes.MOVEMENT_SPEED, 0.24).add(Attributes.FLYING_SPEED, 0.5).add(Attributes.FOLLOW_RANGE, 28)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.4).add(Attributes.STEP_HEIGHT, 1.1);
    }

    // ---------------------------------------------------------------- data

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(COLOR, 0);
        builder.define(OWNER, "");
        builder.define(MOUNT_ID, "");
        builder.define(STAMINA, 1f);
        builder.define(GLIDING, false);
        builder.define(SPEED_BONUS, 0);
        builder.define(STAMINA_BONUS, 0);
    }

    public GriffinColor getColor() {
        return GriffinColor.byOrdinal(getEntityData().get(COLOR));
    }

    public void setColor(GriffinColor color) {
        getEntityData().set(COLOR, color.ordinal());
    }

    /** The owner's UUID as text, or "" for a wild griffin. */
    public String getOwnerId() {
        return getEntityData().get(OWNER);
    }

    public boolean isTame() {
        return !getOwnerId().isEmpty();
    }

    public boolean isOwner(Entity entity) {
        return entity != null && isTame() && getOwnerId().equals(entity.getStringUUID());
    }

    public String getMountId() {
        return getEntityData().get(MOUNT_ID);
    }

    /** Makes this a tamed (summoned) griffin of a player, carrying a whistle's identity and stats. */
    public void setTamed(UUID owner, String mountId, int speedBonus, int staminaBonus) {
        getEntityData().set(OWNER, owner.toString());
        getEntityData().set(MOUNT_ID, mountId);
        getEntityData().set(SPEED_BONUS, speedBonus);
        getEntityData().set(STAMINA_BONUS, staminaBonus);
        setPersistenceRequired();
    }

    /** Stamina as a fraction, 0 to 1. */
    public float getStamina() {
        return getEntityData().get(STAMINA);
    }

    public int getSpeedBonus() {
        return getEntityData().get(SPEED_BONUS);
    }

    public int getStaminaBonus() {
        return getEntityData().get(STAMINA_BONUS);
    }

    public boolean isGliding() {
        return getEntityData().get(GLIDING);
    }

    public boolean isStorm() {
        return entityTags().contains(STORM_TAG);
    }

    /** Whether it is up in the air for real (not a step or a slope): the rider can't dismount by sneaking, sneak means descend. */
    public boolean isFlyingNow() {
        return airTicks > 3 && !isInWater();
    }

    @Override
    public void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output);
        output.putInt("Color", getEntityData().get(COLOR));
        output.putString("Owner", getOwnerId());
        output.putString("MountId", getMountId());
        output.putInt("SpeedBonus", getSpeedBonus());
        output.putInt("StaminaBonus", getStaminaBonus());
    }

    @Override
    public void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        getEntityData().set(COLOR, input.getIntOr("Color", getEntityData().get(COLOR)));
        getEntityData().set(OWNER, input.getStringOr("Owner", ""));
        getEntityData().set(MOUNT_ID, input.getStringOr("MountId", ""));
        getEntityData().set(SPEED_BONUS, input.getIntOr("SpeedBonus", 0));
        getEntityData().set(STAMINA_BONUS, input.getIntOr("StaminaBonus", 0));
    }

    // ---------------------------------------------------------------- goals

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(1, new FloatGoal(this));
        goalSelector.addGoal(2, new DiveGoal(this));
        goalSelector.addGoal(3, new MeleeAttackGoal(this, 1.2, true));
        goalSelector.addGoal(4, new FollowOwnerGoal(this));
        goalSelector.addGoal(5, new WildStrollGoal(this));
        goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 10f));
        goalSelector.addGoal(7, new RandomLookAroundGoal(this));
        targetSelector.addGoal(1, new WildRetaliateGoal(this));
        // storm griffins hunt the nearest survival player; the tag is checked as the target is picked (the world mod adds it after spawning)
        targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, 0, true, false,
                (target, level) -> isStorm() && EntitySelector.NO_CREATIVE_OR_SPECTATOR.test(target)));
    }

    /** Wild griffins wander near their nest (restricted to it); tame ones stay put. */
    private static final class WildStrollGoal extends WaterAvoidingRandomStrollGoal {
        private final GriffinEntity griffin;

        WildStrollGoal(GriffinEntity griffin) {
            super(griffin, 0.8);
            this.griffin = griffin;
        }

        @Override
        public boolean canUse() {
            return !griffin.isTame() && !griffin.isStorm() && super.canUse();
        }

        /** Stays within the nest's radius: a griffin that is outside it heads back, and picks that wander off are re-rolled. */
        @Override
        protected Vec3 getPosition() {
            if (!griffin.hasHome()) {
                return super.getPosition();
            }
            if (!griffin.isWithinHome()) {
                Vec3 back = net.minecraft.world.entity.ai.util.DefaultRandomPos.getPosTowards(griffin, 12, 6,
                        Vec3.atBottomCenterOf(griffin.getHomePosition()), 1.2);
                if (back != null) {
                    return back;
                }
            }
            for (int i = 0; i < 6; i++) {
                Vec3 pick = super.getPosition();
                if (pick == null || griffin.isWithinHome(BlockPos.containing(pick))) {
                    return pick;
                }
            }
            return null;
        }
    }

    /** A wild griffin that was hit hits back; a tamed one never fights. */
    private static final class WildRetaliateGoal extends HurtByTargetGoal {
        private final GriffinEntity griffin;

        WildRetaliateGoal(GriffinEntity griffin) {
            super(griffin);
            this.griffin = griffin;
        }

        @Override
        public boolean canUse() {
            return !griffin.isTame() && super.canUse();
        }
    }

    /** A summoned griffin that is not being ridden walks over to its owner when they are a few blocks away. */
    private static final class FollowOwnerGoal extends Goal {
        private final GriffinEntity griffin;
        private ServerPlayer owner;

        FollowOwnerGoal(GriffinEntity griffin) {
            this.griffin = griffin;
            setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            if (!griffin.isTame() || griffin.isVehicle() || !(griffin.level() instanceof ServerLevel level)) {
                return false;
            }
            owner = level.getServer().getPlayerList().getPlayer(UUID.fromString(griffin.getOwnerId()));
            return owner != null && owner.level() == level && griffin.distanceToSqr(owner) > 6 * 6;
        }

        @Override
        public boolean canContinueToUse() {
            return owner != null && owner.isAlive() && !griffin.isVehicle() && griffin.distanceToSqr(owner) > 3 * 3;
        }

        @Override
        public void tick() {
            griffin.getLookControl().setLookAt(owner, 20f, 20f);
            if (griffin.tickCount % 10 == 0) {
                griffin.getNavigation().moveTo(owner, 1.2);
            }
        }

        @Override
        public void stop() {
            griffin.getNavigation().stop();
        }
    }

    /**
     * The dive: from 5 to 18 blocks away a hostile griffin (wild and hit, or a storm griffin) leaps at its target with its
     * wings out and rakes it on the way past, then glides down.
     */
    private static final class DiveGoal extends Goal {
        private final GriffinEntity griffin;
        private int ticks;
        private boolean hit;

        DiveGoal(GriffinEntity griffin) {
            this.griffin = griffin;
            setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK, Flag.JUMP));
        }

        @Override
        public boolean canUse() {
            LivingEntity target = griffin.getTarget();
            if (target == null || !target.isAlive() || griffin.isTame() || griffin.isVehicle() || griffin.diveCooldown > 0 || !griffin.onGround()) {
                return false;
            }
            double d = griffin.distanceToSqr(target);
            return d >= 25 && d <= 18 * 18 && griffin.hasLineOfSight(target);
        }

        @Override
        public boolean canContinueToUse() {
            return ticks < 40 && !hit && griffin.getTarget() != null && griffin.getTarget().isAlive() && !(ticks > 6 && griffin.onGround());
        }

        @Override
        public boolean requiresUpdateEveryTick() {
            return true;
        }

        @Override
        public void start() {
            ticks = 0;
            hit = false;
            LivingEntity target = griffin.getTarget();
            Vec3 to = target.position().add(0, target.getBbHeight() * 0.5, 0).subtract(griffin.position());
            Vec3 flat = new Vec3(to.x, 0, to.z);
            double len = Math.max(0.5, flat.length());
            Vec3 dir = flat.scale(1.0 / len);
            double lift = 0.5 + Mth.clamp(to.y * 0.03, 0, 0.3) + len * 0.012;
            griffin.setDeltaMovement(dir.x * 0.85, lift, dir.z * 0.85);
            griffin.getEntityData().set(GLIDING, true);
            griffin.playSound(SoundEvents.PHANTOM_SWOOP, 1.2f, 1.1f);
            griffin.getNavigation().stop();
        }

        @Override
        public void tick() {
            ticks++;
            LivingEntity target = griffin.getTarget();
            if (target == null) {
                return;
            }
            griffin.getLookControl().setLookAt(target, 40f, 40f);
            Vec3 v = griffin.getDeltaMovement();
            Vec3 to = target.position().subtract(griffin.position());
            // steer a little towards the target while in the air
            griffin.setDeltaMovement(v.x + Mth.clamp(to.x * 0.01, -0.03, 0.03), v.y, v.z + Mth.clamp(to.z * 0.01, -0.03, 0.03));
            if (griffin.level() instanceof ServerLevel level && griffin.getBoundingBox().inflate(0.6).intersects(target.getBoundingBox())) {
                hit = true;
                float damage = (float) griffin.getAttributeValue(Attributes.ATTACK_DAMAGE) * 1.5f;
                DamageSource source = griffin.damageSources().mobAttack(griffin);
                if (target.hurtServer(level, source, damage)) {
                    Vec3 away = target.position().subtract(griffin.position());
                    target.knockback(0.9, -away.x, -away.z, source, damage);
                    griffin.setLastHurtMob(target);
                }
                level.sendParticles(ParticleTypes.CRIT, target.getX(), target.getY(0.6), target.getZ(), 12, 0.3, 0.3, 0.3, 0.2);
                griffin.playSound(SoundEvents.PLAYER_ATTACK_SWEEP, 1.0f, 0.8f);
            }
        }

        @Override
        public void stop() {
            griffin.getEntityData().set(GLIDING, false);
            griffin.diveCooldown = 80 + griffin.getRandom().nextInt(60);
        }
    }

    // ---------------------------------------------------------------- taming

    private static boolean isRawMeat(ItemStack stack) {
        Item item = stack.getItem();
        return item == Items.BEEF || item == Items.MUTTON || item == Items.CHICKEN || item == Items.PORKCHOP || item == Items.RABBIT;
    }

    @Override
    protected InteractionResult mobInteract(Player player, InteractionHand hand) {
        ItemStack held = player.getItemInHand(hand);
        if (isStorm()) {
            return InteractionResult.PASS;
        }
        if (isTame()) {
            if (!isOwner(player)) {
                if (!level().isClientSide() && player instanceof ServerPlayer sp) {
                    Mounts.say(sp, "This griffin belongs to someone else.");
                }
                return InteractionResult.SUCCESS;
            }
            if (!level().isClientSide() && !isVehicle()) {
                player.setYRot(getYRot());
                player.setXRot(0);
                player.startRiding(this);
            }
            return InteractionResult.SUCCESS;
        }
        if (isRawMeat(held)) {
            if (level() instanceof ServerLevel level && player instanceof ServerPlayer sp) {
                if (!sp.isCreative()) {
                    held.shrink(1);
                }
                playSound(SoundEvents.GENERIC_EAT.value(), 0.8f, 1f);
                if (getRandom().nextInt(5) == 0) {
                    level.sendParticles(ParticleTypes.HEART, getX(), getY(0.9), getZ(), 9, 0.5, 0.5, 0.5, 0.05);
                    Mounts.tame(sp, this);
                } else {
                    level.sendParticles(ParticleTypes.SMOKE, getX(), getY(0.8), getZ(), 8, 0.4, 0.4, 0.4, 0.02);
                    Mounts.say(sp, "The griffin eats the meat and eyes you. It is not won over yet.");
                }
            }
            return InteractionResult.SUCCESS;
        }
        return InteractionResult.PASS;
    }

    /** Rare and legendary wild griffins give off a few sparkles (more for legendary) so players can spot them from afar. */
    private void sparkle() {
        GriffinRarity rarity = getColor().rarity();
        if (rarity.ordinal() < GriffinRarity.RARE.ordinal() || !(level() instanceof net.minecraft.server.level.ServerLevel server)) {
            return;
        }
        boolean legendary = rarity == GriffinRarity.LEGENDARY;
        server.sendParticles(net.minecraft.core.particles.ParticleTypes.END_ROD, getX(), getY(0.6), getZ(), legendary ? 3 : 1, 0.6, 0.7, 0.6, 0.01);
        if (legendary) {
            server.sendParticles(net.minecraft.core.particles.ParticleTypes.GLOW, getX(), getY(0.5), getZ(), 2, 0.7, 0.7, 0.7, 0.02);
        }
    }

    // ---------------------------------------------------------------- the tick

    @Override
    public void tick() {
        super.tick();
        if (!level().isClientSide() && tickCount % 8 == 0 && (!isTame() || isStorm())) {
            sparkle();
        }
        if (!onGround()) {
            airTicks++;
        } else {
            airTicks = 0;
        }
        if (takeoffGrace > 0) {
            takeoffGrace--;
        }
        flyAmountO = flyAmount;
        glideAmountO = glideAmount;
        boolean flying = isFlyingNow();
        flyAmount += ((flying ? 1f : 0f) - flyAmount) * 0.22f;
        glideAmount += ((isGliding() && flying ? 1f : 0f) - glideAmount) * 0.18f;
        if (diveCooldown > 0) {
            diveCooldown--;
        }
        if (level() instanceof ServerLevel level) {
            serverTick(level, flying);
        }
    }

    private void serverTick(ServerLevel level, boolean flying) {
        LivingEntity controller = getControllingPassenger();
        ServerPlayer rider = controller instanceof ServerPlayer sp ? sp : null;
        float stamina = getStamina();
        float drain = 1f / (BASE_FLIGHT_SECONDS * 20f * (1f + getStaminaBonus() / 100f));
        float refill = 1f / (REFILL_SECONDS * 20f);
        if (rider != null) {
            Input in = rider.getLastClientInput();
            if (flying) {
                float cost = in.jump() ? 1.5f : in.sprint() ? 1.3f : in.shift() ? 0.35f : (in.forward() || in.backward() || in.left() || in.right()) ? 1f : 0.7f;
                stamina = Math.max(0f, stamina - drain * cost);
            } else {
                stamina = Math.min(1f, stamina + refill);
            }
            boolean glide = flying && in.sprint() && in.forward() && stamina > 0f;
            if (glide != isGliding()) {
                getEntityData().set(GLIDING, glide);
            }
            rider.fallDistance = 0;
            fallDistance = 0;
            dismountedAt = -1;
            double dx = getX() - xo, dy = getY() - yo, dz = getZ() - zo;
            int travelled = (int) Math.round(Math.sqrt(dx * dx + dy * dy + dz * dz) * 100);
            if (travelled > 0 && travelled < 10_000) {
                rider.awardStat(SkyRpgMounts.GRIFFIN_ONE_CM, travelled);
            }
        } else if (!isTame()) {
            // wild griffins glide down slowly; the dive goal owns the flag while it runs
            stamina = 1f;
        } else if (!flying) {
            stamina = Math.min(1f, stamina + refill);
            if (isGliding()) {
                getEntityData().set(GLIDING, false);
            }
        }
        if (Math.abs(stamina - getStamina()) > 0.0005f) {
            getEntityData().set(STAMINA, stamina);
        }
        if (flying && flapSoundCooldown-- <= 0 && !isGliding()) {
            flapSoundCooldown = 16;
            playSound(SoundEvents.PHANTOM_FLAP, 0.5f, 1.3f);
        }
        if (isStorm() && tickCount % 20 == 0) {
            setPersistenceRequired();
        }
        if (isTame() && tickCount % 10 == 0) {
            checkOwner(level, rider);
        }
    }

    /** A summoned griffin goes back into the stable when its owner leaves, strays, or has walked away from it for 30 seconds. */
    private void checkOwner(ServerLevel level, ServerPlayer rider) {
        ServerPlayer owner = level.getServer().getPlayerList().getPlayer(UUID.fromString(getOwnerId()));
        if (owner == null || Mounts.out(owner) != this) {
            discard(); // an orphan from before a restart, or its owner is gone
            return;
        }
        if (rider != null && rider == owner) {
            return;
        }
        if (owner.level() != level || distanceToSqr(owner) > AWAY_DISTANCE * AWAY_DISTANCE
                || (dismountedAt >= 0 && tickCount - dismountedAt > AWAY_AFTER_DISMOUNT)) {
            Mounts.away(owner, true);
        }
    }

    @Override
    protected void removePassenger(Entity passenger) {
        super.removePassenger(passenger);
        if (!level().isClientSide() && passenger instanceof ServerPlayer player) {
            dismountedAt = tickCount;
            player.fallDistance = 0;
            if (isFlyingNow() || !onGround()) {
                player.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 8 * 20, 0, false, true, true));
            }
        }
    }

    // ---------------------------------------------------------------- damage

    @Override
    public boolean causeFallDamage(double distance, float damageMultiplier, DamageSource source) {
        return false;
    }

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
        if (isTame()) {
            if (source.is(DamageTypes.FALL) || source.is(DamageTypes.FLY_INTO_WALL) || isOwner(source.getEntity())) {
                return false;
            }
            // never killed while ridden in the hub
            if (isVehicle() && level.dimension() == HUB) {
                return false;
            }
            if (getHealth() - amount <= 0.5f) {
                // knocked out instead of killed: it flies home to the stable
                setHealth(getMaxHealth());
                ServerPlayer owner = level.getServer().getPlayerList().getPlayer(UUID.fromString(getOwnerId()));
                if (owner != null) {
                    Mounts.say(owner, "Your griffin was knocked out and flew back to the stable.");
                    Mounts.away(owner, true);
                } else {
                    discard();
                }
                return false;
            }
        }
        return super.hurtServer(level, source, amount);
    }

    @Override
    public boolean removeWhenFarAway(double distanceToClosestPlayer) {
        return !isTame() && !isStorm() && !isPersistenceRequired();
    }

    @Override
    public boolean requiresCustomPersistence() {
        return isTame() || isStorm() || super.requiresCustomPersistence();
    }

    @Override
    public boolean canBeLeashed() {
        return false;
    }

    // ---------------------------------------------------------------- riding

    @Override
    public LivingEntity getControllingPassenger() {
        Entity first = getFirstPassenger();
        return first instanceof Player player && isOwner(player) ? player : null;
    }

    @Override
    protected boolean canAddPassenger(Entity passenger) {
        return getPassengers().isEmpty() && !isStorm() && isOwner(passenger);
    }

    @Override
    public boolean isFlyingVehicle() {
        return true;
    }

    @Override
    public boolean canSprint() {
        return true;
    }

    @Override
    public boolean isPushable() {
        return !isVehicle() && super.isPushable();
    }

    @Override
    protected Vec3 getRiddenInput(Player player, Vec3 travelVector) {
        float strafe = player.xxa * 0.6f;
        float forward = player.zza;
        if (forward < 0) {
            forward *= 0.4f;
        }
        float vertical = 0f;
        if (isFlyingNow() || takeoffGrace > 0) {
            if (player.isJumping()) {
                vertical = 1f;
            } else if (player.isShiftKeyDown()) {
                vertical = -1f;
            }
        }
        return new Vec3(strafe, vertical, forward);
    }

    @Override
    protected float getRiddenSpeed(Player player) {
        float base = (float) getAttributeValue(Attributes.MOVEMENT_SPEED) * (1f + getSpeedBonus() / 100f);
        return player.isSprinting() ? base * 1.45f : base;
    }

    protected Vec2 getRiddenRotation(LivingEntity rider) {
        return new Vec2(rider.getXRot() * 0.5f, rider.getYRot());
    }

    @Override
    protected void tickRidden(Player player, Vec3 riddenInput) {
        super.tickRidden(player, riddenInput);
        Vec2 rot = getRiddenRotation(player);
        float yaw = getYRot();
        float diff = Mth.wrapDegrees(rot.y - yaw);
        // the body turns smoothly in the air, instantly on the ground
        yaw = isFlyingNow() ? yaw + diff * 0.35f : rot.y;
        setRot(yaw, flightPitch(rot.x));
        yRotO = yaw;
        yBodyRot = yaw;
        yHeadRot = yaw;
        if (canSimulateMovement() && onGround() && player.isJumping() && getStamina() >= TAKEOFF_STAMINA && !isInWater()) {
            Vec3 v = getDeltaMovement();
            setDeltaMovement(v.x, 0.62, v.z);
            needsSync = true;
            takeoffGrace = 8;
            playSound(SoundEvents.PHANTOM_FLAP, 0.8f, 0.9f);
        }
    }

    private float flightPitch(float lookPitch) {
        return Mth.clamp(lookPitch * 0.5f, -20f, 25f);
    }

    @Override
    public void travel(Vec3 input) {
        LivingEntity controller = getControllingPassenger();
        boolean flight = controller instanceof Player && (airTicks >= 4 || takeoffGrace > 0) && !isInWater() && !isInLava();
        if (flight && canSimulateMovement()) {
            flyTravel((Player) controller, input);
            return;
        }
        super.travel(input);
        if (controller == null && !onGround() && !isInWater()) {
            // wild and idle griffins drift down on spread wings
            Vec3 v = getDeltaMovement();
            if (v.y < -0.1) {
                setDeltaMovement(v.x, -0.1, v.z);
            }
        }
    }

    private void flyTravel(Player player, Vec3 input) {
        boolean tired = getStamina() <= 0.01f;
        double speed = 0.5 * (1.0 + getSpeedBonus() / 100.0);
        if (player.isSprinting() && input.z > 0 && !tired) {
            speed *= 1.75;
        }
        if (tired) {
            speed *= 0.5;
        }
        Vec3 want = getInputVector(new Vec3(input.x, 0, input.z), 1f, getYRot());
        if (want.lengthSqr() > 1.0) {
            want = want.normalize();
        }
        want = want.scale(speed);
        double climb = tired ? -0.12 : input.y > 0 ? 0.42 : input.y < 0 ? -0.5 : 0.0;
        Vec3 v = getDeltaMovement();
        double nx = v.x + (want.x - v.x) * 0.1;
        double nz = v.z + (want.z - v.z) * 0.1;
        double ny = v.y + (climb - v.y) * 0.18;
        setDeltaMovement(nx, ny, nz);
        move(MoverType.SELF, getDeltaMovement());
        fallDistance = 0;
    }

    // ---------------------------------------------------------------- sounds

    @Override
    protected SoundEvent getAmbientSound() {
        return isTame() ? null : SoundEvents.PARROT_AMBIENT;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.PHANTOM_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.PHANTOM_DEATH;
    }

    @Override
    protected void playStepSound(BlockPos pos, BlockState state) {
        playSound(SoundEvents.WOLF_STEP.value(), 0.25f, 0.8f);
    }

    @Override
    public float getVoicePitch() {
        return 0.55f + (getRandom().nextFloat() - 0.5f) * 0.12f;
    }

    @Override
    public int getAmbientSoundInterval() {
        return 200;
    }
}
