package com.skyrpg.fishing;

import com.skyrpg.fishing.FishingItems.BaitItem;
import net.minecraft.world.entity.projectile.FishingHook;

/**
 * What a cast gets on top of the player's stats: its bait and the hotspot the bobber floats in.
 * Speed, fortune and sea creature chance are stat points; the others are fractions (0.1 = +10%).
 */
public record CastBonus(double speed, double fortune, double seaCreature, double doubleCatch, double rareFinds) {
    public static final CastBonus NONE = new CastBonus(0, 0, 0, 0, 0);

    public static CastBonus bait(BaitItem bait) {
        return bait == null ? NONE : new CastBonus(bait.fishingSpeed(), 0, bait.seaCreatureChance(), bait.doubleCatch(), 0);
    }

    public CastBonus plus(CastBonus other) {
        return new CastBonus(speed + other.speed, fortune + other.fortune, seaCreature + other.seaCreature,
                doubleCatch + other.doubleCatch, rareFinds + other.rareFinds);
    }

    /** Bait plus hotspot, for this hook right now. */
    public static CastBonus of(FishingHook hook) {
        CastBonus total = hook instanceof BaitHolder holder ? bait(holder.skyrpg$bait()) : NONE;
        return total.plus(Hotspots.bonusAt(hook));
    }
}
