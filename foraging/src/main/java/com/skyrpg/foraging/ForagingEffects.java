package com.skyrpg.foraging;

import com.skyrpg.core.items.GearEffects;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;

/**
 * The foraging gear effects (see core's {@link GearEffects}): which axes, sets, talents and boosts grant them is data;
 * what they do is here, in {@link Chopping} and in {@link Treefeller}.
 */
public final class ForagingEffects {
    /** More Heartwood Splinters (0.1 = +10%). */
    public static final Identifier SPLINTER_BONUS = SkyRpgForaging.id("splinter_bonus");
    /** More Heartwood Splinters at night only (0.3 = +30%). */
    public static final Identifier NIGHT_SPLINTERS = SkyRpgForaging.id("night_splinters");
    /** Rare finds are this much more likely (1.0 = twice as likely). */
    public static final Identifier RARE_FINDS = SkyRpgForaging.id("rare_finds");
    /** Chance (0..1) that a chopped log drops twice. */
    public static final Identifier DOUBLE_LOGS = SkyRpgForaging.id("double_logs");
    /** Felling a tree with Treefeller replants its sapling. */
    public static final Identifier REPLANT = SkyRpgForaging.id("replant");
    /** Felling a tree with Treefeller drops its leaves straight away (saplings and apples into your bag). */
    public static final Identifier LEAF_CLEAR = SkyRpgForaging.id("leaf_clear");
    /** Falls this many blocks further than usual don't hurt. */
    public static final Identifier SOFT_LANDING = SkyRpgForaging.id("soft_landing");
    /** Speed in forests, taigas and jungles. */
    public static final Identifier FOREST_SPEED = SkyRpgForaging.id("forest_speed");
    /** Stumps you chop regrow this much faster (0.06 = 6% shorter, up to half). */
    public static final Identifier STUMP_SPEED = SkyRpgForaging.id("stump_speed");
    /** Drifting star motes around you. */
    public static final Identifier STAR_MOTES = SkyRpgForaging.id("star_motes");

    private ForagingEffects() {
    }

    static double value(ServerPlayer player, Identifier effect) {
        return GearEffects.value(player, effect);
    }
}
