package com.skyrpg.fishing;

import com.skyrpg.core.items.GearEffects;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;

/**
 * The fishing gear effects (see core's {@link GearEffects}): rods, sets, talents and baits grant them by data; what they
 * do is here and in {@link Catching}.
 */
public final class FishingEffects {
    /** Rare finds are this much more likely (1.0 = twice as likely). */
    public static final Identifier RARE_FINDS = SkyRpgFishing.id("rare_finds");
    /** Chance (0..1) that a catch comes up twice. */
    public static final Identifier DOUBLE_CATCH = SkyRpgFishing.id("double_catch");
    /** More Tidescales (0.1 = +10%). */
    public static final Identifier SCALE_BONUS = SkyRpgFishing.id("scale_bonus");
    /** Water Breathing and Dolphin's Grace while in water. */
    public static final Identifier TIDEBORN = SkyRpgFishing.id("tideborn");

    private FishingEffects() {
    }

    static double value(ServerPlayer player, Identifier effect) {
        return GearEffects.value(player, effect);
    }
}
