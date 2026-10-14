package com.skyrpg.combat.slayer.boss;

import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.spider.Spider;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * The Broodmother: a slayer boss on a spider's body (drawn as its own spider-queen model). Its tier, pounce
 * countdown, cocoon and spiderling shield are synced so the client model can pose itself. The hitbox stays the
 * vanilla spider's.
 */
public class BroodmotherEntity extends Spider implements TieredBoss {
    /**
     * Ticks the pounce pose lasts: set {@link #setPounceTicks} to this when a pounce starts. The model crouches for
     * the first 14 ticks, springs for the next 5 (launch the boss around tick 14) and recovers for the last 5.
     */
    public static final int POUNCE_POSE_TICKS = 24;

    private static final EntityDataAccessor<Integer> TIER = SynchedEntityData.defineId(BroodmotherEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> POUNCE_TICKS = SynchedEntityData.defineId(BroodmotherEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> COCOONED = SynchedEntityData.defineId(BroodmotherEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> SHIELDED = SynchedEntityData.defineId(BroodmotherEntity.class, EntityDataSerializers.BOOLEAN);

    // client-side easing of the cocoon / shield visuals (not synced, ticked on both sides, only read by the renderer)
    private float cocoonAnim;
    private float cocoonAnimO;
    private float shieldAnim;
    private float shieldAnimO;

    public BroodmotherEntity(EntityType<? extends Spider> type, Level level) {
        super(type, level);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(TIER, 1);
        builder.define(POUNCE_TICKS, 0);
        builder.define(COCOONED, false);
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

    /** Ticks left of the pounce pose (0 when not pouncing); counts down every server tick. */
    public int getPounceTicks() {
        return getEntityData().get(POUNCE_TICKS);
    }

    public void setPounceTicks(int ticks) {
        getEntityData().set(POUNCE_TICKS, ticks);
    }

    public boolean isCocooned() {
        return getEntityData().get(COCOONED);
    }

    public void setCocooned(boolean cocooned) {
        getEntityData().set(COCOONED, cocooned);
    }

    public boolean isShielded() {
        return getEntityData().get(SHIELDED);
    }

    public void setShielded(boolean shielded) {
        getEntityData().set(SHIELDED, shielded);
    }

    /** 0 to 1: how far the cocoon has closed around the boss (eased for rendering). */
    public float getCocoonProgress(float partialTick) {
        return Mth.lerp(partialTick, cocoonAnimO, cocoonAnim);
    }

    /** 0 to 1: how far the spiderling ring has faded in (eased for rendering). */
    public float getShieldProgress(float partialTick) {
        return Mth.lerp(partialTick, shieldAnimO, shieldAnim);
    }

    @Override
    public void tick() {
        super.tick();
        cocoonAnimO = cocoonAnim;
        shieldAnimO = shieldAnim;
        cocoonAnim = Mth.clamp(cocoonAnim + (isCocooned() ? 0.12f : -0.12f), 0f, 1f);
        shieldAnim = Mth.clamp(shieldAnim + (isShielded() ? 0.1f : -0.1f), 0f, 1f);
        if (!level().isClientSide()) {
            int pounce = getPounceTicks();
            if (pounce > 0) {
                setPounceTicks(pounce - 1);
            }
        }
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output);
        output.putInt("BroodmotherTier", getTier());
        output.putBoolean("BroodmotherCocooned", isCocooned());
        output.putBoolean("BroodmotherShielded", isShielded());
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        setTier(input.getIntOr("BroodmotherTier", 1));
        setCocooned(input.getBooleanOr("BroodmotherCocooned", false));
        setShielded(input.getBooleanOr("BroodmotherShielded", false));
    }

    @Override
    public boolean removeWhenFarAway(double distanceSq) {
        return false;
    }
}
