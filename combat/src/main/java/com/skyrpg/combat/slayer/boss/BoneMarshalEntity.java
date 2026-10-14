package com.skyrpg.combat.slayer.boss;

import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.skeleton.Skeleton;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * The Bone Marshal: a slayer boss on a skeleton's body (drawn as its own skeletal war general model), with a bow
 * whose arrows hit as hard as its tier. Its tier, greatbow draw, shield and banner state are synced so the client
 * model can pose itself.
 */
public class BoneMarshalEntity extends Skeleton implements TieredBoss {
    private static final EntityDataAccessor<Integer> TIER = SynchedEntityData.defineId(BoneMarshalEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DRAW_TICKS = SynchedEntityData.defineId(BoneMarshalEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DRAW_TOTAL = SynchedEntityData.defineId(BoneMarshalEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> SHIELDED = SynchedEntityData.defineId(BoneMarshalEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> BANNER_PLANTED = SynchedEntityData.defineId(BoneMarshalEntity.class, EntityDataSerializers.BOOLEAN);

    private double arrowDamage = 2;

    public BoneMarshalEntity(EntityType<? extends Skeleton> type, Level level) {
        super(type, level);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(TIER, 1);
        builder.define(DRAW_TICKS, 0);
        builder.define(DRAW_TOTAL, 1);
        builder.define(SHIELDED, false);
        builder.define(BANNER_PLANTED, false);
    }

    @Override
    public int getTier() {
        return getEntityData().get(TIER);
    }

    @Override
    public void setTier(int tier) {
        getEntityData().set(TIER, tier);
    }

    /** Ticks left of the charged greatbow draw (0 when not drawing); counts down every server tick. */
    public int getDrawTicks() {
        return getEntityData().get(DRAW_TICKS);
    }

    /** Starts (or sets) a charged draw; a longer value than what is left also becomes the draw's total length. */
    public void setDrawTicks(int ticks) {
        int clamped = Math.max(0, ticks);
        if (clamped > getDrawTicks()) {
            getEntityData().set(DRAW_TOTAL, clamped);
        }
        getEntityData().set(DRAW_TICKS, clamped);
    }

    /** How many ticks the current charged draw lasts in total (client pull-back ramp). */
    public int getDrawTotal() {
        return getEntityData().get(DRAW_TOTAL);
    }

    public boolean isShielded() {
        return getEntityData().get(SHIELDED);
    }

    public void setShielded(boolean shielded) {
        getEntityData().set(SHIELDED, shielded);
    }

    /** True while the war banner is planted in the ground (the back banner is then hidden). */
    public boolean isBannerPlanted() {
        return getEntityData().get(BANNER_PLANTED);
    }

    public void setBannerPlanted(boolean planted) {
        getEntityData().set(BANNER_PLANTED, planted);
    }

    @Override
    public void tick() {
        super.tick();
        if (!level().isClientSide()) {
            int draw = getDrawTicks();
            if (draw > 0) {
                getEntityData().set(DRAW_TICKS, draw - 1);
            }
        }
    }

    /** The base damage of its arrows (vanilla skeletons: 2). */
    public void setArrowDamage(double damage) {
        arrowDamage = damage;
    }

    @Override
    protected AbstractArrow getArrow(ItemStack arrow, float power, ItemStack bow) {
        AbstractArrow shot = super.getArrow(arrow, power, bow);
        shot.setBaseDamage(arrowDamage);
        return shot;
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output);
        output.putInt("BoneMarshalTier", getTier());
        output.putBoolean("BoneMarshalShielded", isShielded());
        output.putBoolean("BoneMarshalBannerPlanted", isBannerPlanted());
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        setTier(input.getIntOr("BoneMarshalTier", 1));
        setShielded(input.getBooleanOr("BoneMarshalShielded", false));
        setBannerPlanted(input.getBooleanOr("BoneMarshalBannerPlanted", false));
    }

    /** Powder snow doesn't turn it into a stray. */
    @Override
    public void startFreezeConversion(int time) {
    }

    @Override
    public boolean removeWhenFarAway(double distanceSq) {
        return false;
    }
}
