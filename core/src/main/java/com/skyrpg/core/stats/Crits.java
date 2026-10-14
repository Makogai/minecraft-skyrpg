package com.skyrpg.core.stats;

/**
 * Whether the hit being dealt right now was a critical hit: the stats mod marks it while working out a player's melee
 * damage, and other mods (damage numbers) read it later in the same hurt call, on the same thread.
 */
public final class Crits {
    private static final ThreadLocal<Boolean> CRIT = ThreadLocal.withInitial(() -> false);

    private Crits() {
    }

    public static void set(boolean crit) {
        CRIT.set(crit);
    }

    public static boolean wasCrit() {
        return CRIT.get();
    }
}
