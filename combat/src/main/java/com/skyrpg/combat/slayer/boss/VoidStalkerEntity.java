package com.skyrpg.combat.slayer.boss;

import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Enderman;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * The Void Stalker: a slayer boss on an enderman's body (drawn as its own void wraith model). Rain and water don't
 * bother it, and it leaves blocks alone. Its tier, big-spell channel, phase-out and null shield are synced so the
 * client model can pose itself.
 */
public class VoidStalkerEntity extends Enderman implements TieredBoss {
    private static final EntityDataAccessor<Integer> TIER = SynchedEntityData.defineId(VoidStalkerEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> CHANNEL_TICKS = SynchedEntityData.defineId(VoidStalkerEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> PHASED = SynchedEntityData.defineId(VoidStalkerEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> SHIELDED = SynchedEntityData.defineId(VoidStalkerEntity.class, EntityDataSerializers.BOOLEAN);

    // client side only: eased 0..1 versions of the synced state, so poses and effects blend instead of snapping
    private float channelBlend;
    private float channelBlendO;
    private float phaseBlend;
    private float phaseBlendO;
    private float shieldBlend;
    private float shieldBlendO;

    public VoidStalkerEntity(EntityType<? extends Enderman> type, Level level) {
        super(type, level);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(TIER, 1);
        builder.define(CHANNEL_TICKS, 0);
        builder.define(PHASED, false);
        builder.define(SHIELDED, false);
    }

    @Override
    public int getTier() {
        return getEntityData().get(TIER);
    }

    @Override
    public void setTier(int tier) {
        getEntityData().set(TIER, tier);
    }

    /** Ticks left of the big spell the stalker is channelling (0 when not channelling). */
    public int getChannelTicks() {
        return getEntityData().get(CHANNEL_TICKS);
    }

    public void setChannelTicks(int ticks) {
        getEntityData().set(CHANNEL_TICKS, ticks);
    }

    /** Phased out (dodging): the body flickers and is drawn translucent. */
    public boolean isPhased() {
        return getEntityData().get(PHASED);
    }

    public void setPhased(boolean phased) {
        getEntityData().set(PHASED, phased);
    }

    /** The null shield is up: the shards close into a sphere round the body. */
    public boolean isShielded() {
        return getEntityData().get(SHIELDED);
    }

    public void setShielded(boolean shielded) {
        getEntityData().set(SHIELDED, shielded);
    }

    public float getChannelBlend(float partialTick) {
        return channelBlendO + (channelBlend - channelBlendO) * partialTick;
    }

    public float getPhaseBlend(float partialTick) {
        return phaseBlendO + (phaseBlend - phaseBlendO) * partialTick;
    }

    public float getShieldBlend(float partialTick) {
        return shieldBlendO + (shieldBlend - shieldBlendO) * partialTick;
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide()) {
            channelBlendO = channelBlend;
            phaseBlendO = phaseBlend;
            shieldBlendO = shieldBlend;
            channelBlend = ease(channelBlend, getChannelTicks() > 0 ? 1f : 0f, 0.25f);
            phaseBlend = ease(phaseBlend, isPhased() ? 1f : 0f, 0.35f);
            shieldBlend = ease(shieldBlend, isShielded() ? 1f : 0f, 0.18f);
        } else {
            int channel = getChannelTicks();
            if (channel > 0) {
                setChannelTicks(channel - 1);
            }
        }
    }

    private static float ease(float current, float target, float rate) {
        float next = current + (target - current) * rate;
        return Math.abs(target - next) < 0.01f ? target : next;
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output);
        output.putInt("VoidStalkerTier", getTier());
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        setTier(input.getIntOr("VoidStalkerTier", 1));
    }

    @Override
    protected void registerGoals() {
        super.registerGoals();
        // Enderman's EndermanTakeBlockGoal and EndermanLeaveBlockGoal: no griefing.
        goalSelector.removeAllGoals(goal -> goal.getClass().getSimpleName().contains("Block"));
    }

    @Override
    public boolean isSensitiveToWater() {
        return false;
    }

    @Override
    public boolean removeWhenFarAway(double distanceSq) {
        return false;
    }
}
