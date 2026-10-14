package com.skyrpg.farming;

import com.skyrpg.core.items.GearEffects;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;

/**
 * The farming gear effects (see core's {@link GearEffects}): which tools, sets and boosts grant them is data
 * (item definitions and item sets); what they do is here and in {@link Harvest}.
 */
public final class FarmingEffects {
    /** Harvesting a grown crop replants it. */
    public static final Identifier RESEED = SkyRpgFarming.id("reseed");
    /** Harvesting a grown crop also harvests the grown crops around it, this many blocks out (1 = 3x3). */
    public static final Identifier HARVEST_AREA = SkyRpgFarming.id("harvest_area");
    /** Chance (0..1) that a harvested crop drops everything twice. */
    public static final Identifier DOUBLE_CROPS = SkyRpgFarming.id("double_crops");
    /** Farmland doesn't turn back to dirt when you land on it. */
    public static final Identifier NO_TRAMPLE = SkyRpgFarming.id("no_trample");
    /** Speed while standing on farmland. */
    public static final Identifier FIELD_SPEED = SkyRpgFarming.id("field_speed");
    /** Extra Dewdrop chance (0.25 = +25%) in daylight. */
    public static final Identifier SUNLIT = SkyRpgFarming.id("sunlit");
    /** Crops within this many blocks of you grow faster. */
    public static final Identifier GROWTH_AURA = SkyRpgFarming.id("growth_aura");
    /** Rare finds are this much more likely (1.0 = twice as likely). */
    public static final Identifier RARE_FINDS = SkyRpgFarming.id("rare_finds");
    /** Seeds from other blocks (bonus drops) are this much more likely. */
    public static final Identifier SEED_LUCK = SkyRpgFarming.id("seed_luck");
    /** Extra Dewdrop chance at any time of day (0.08 = +8%). */
    public static final Identifier DEW_BONUS = SkyRpgFarming.id("dew_bonus");
    /** Planters grow this much faster (0.06 = 6% shorter timer). */
    public static final Identifier PLANTER_SPEED = SkyRpgFarming.id("planter_speed");

    private FarmingEffects() {
    }

    static double value(ServerPlayer player, Identifier effect) {
        return GearEffects.value(player, effect);
    }
}
