package com.skyrpg.combat.slayer.boss;

import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * The Grave Tyrant: a slayer boss on a zombie's body (drawn as its own plague-giant model). It doesn't drown
 * into a husk or despawn. Its tier, Grave Slam wind-up and enrage are synced so the client model can pose itself.
 */
public class GraveTyrantEntity extends Zombie implements TieredBoss {
    /** Ticks the slam pose lasts: the 30 tick wind-up of Grave Slam (impact at tick 30), then a short recovery. */
    public static final int SLAM_POSE_TICKS = 40;

    private static final EntityDataAccessor<Integer> TIER = SynchedEntityData.defineId(GraveTyrantEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> SLAM_TICKS = SynchedEntityData.defineId(GraveTyrantEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> ENRAGED = SynchedEntityData.defineId(GraveTyrantEntity.class, EntityDataSerializers.BOOLEAN);

    public GraveTyrantEntity(EntityType<? extends Zombie> type, Level level) {
        super(type, level);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(TIER, 1);
        builder.define(SLAM_TICKS, 0);
        builder.define(ENRAGED, false);
    }

    @Override
    public int getTier() {
        return getEntityData().get(TIER);
    }

    @Override
    public void setTier(int tier) {
        getEntityData().set(TIER, tier);
    }

    /** Ticks left of the Grave Slam pose (0 when not slamming). */
    public int getSlamTicks() {
        return getEntityData().get(SLAM_TICKS);
    }

    public void setSlamTicks(int ticks) {
        getEntityData().set(SLAM_TICKS, ticks);
    }

    public boolean isEnraged() {
        return getEntityData().get(ENRAGED);
    }

    public void setEnraged(boolean enraged) {
        getEntityData().set(ENRAGED, enraged);
    }

    @Override
    public void tick() {
        super.tick();
        if (!level().isClientSide()) {
            int slam = getSlamTicks();
            if (slam > 0) {
                setSlamTicks(slam - 1);
            }
        }
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output);
        output.putInt("GraveTyrantTier", getTier());
        output.putBoolean("GraveTyrantEnraged", isEnraged());
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        setTier(input.getIntOr("GraveTyrantTier", 1));
        setEnraged(input.getBooleanOr("GraveTyrantEnraged", false));
    }

    @Override
    protected boolean convertsInWater() {
        return false;
    }

    @Override
    protected boolean isSunSensitive() {
        return false;
    }

    @Override
    public boolean removeWhenFarAway(double distanceSq) {
        return false;
    }
}
