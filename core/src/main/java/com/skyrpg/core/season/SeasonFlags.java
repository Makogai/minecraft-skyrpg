package com.skyrpg.core.season;

import java.util.Set;

/**
 * Switches a running season turns on for other mods (the events mod owns the seasons; the crates mod reads
 * {@link #DOUBLE_CRATES}). Empty until a season starts, and again after it ends.
 */
public final class SeasonFlags {
    /** Crate drop chances are doubled. */
    public static final String DOUBLE_CRATES = "double_crates";

    private static volatile Set<String> on = Set.of();

    private SeasonFlags() {
    }

    public static boolean on(String flag) {
        return on.contains(flag);
    }

    public static void set(Set<String> flags) {
        on = Set.copyOf(flags);
    }
}
