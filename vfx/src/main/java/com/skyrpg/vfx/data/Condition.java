package com.skyrpg.vfx.data;

import java.util.Locale;

/**
 * When an emitter (or a binding) is allowed to run, as bits in a mask. JSON: {@code "conditions": ["moving", "!sneaking"]};
 * all must hold, {@code !} negates. Effects played at a plain position have no entity: they count as still and on the
 * ground, and only the world conditions (night, day, raining) can differ.
 */
public enum Condition {
    MOVING, STILL, SNEAKING, SPRINTING, IN_WATER, ON_GROUND, AIRBORNE, NIGHT, DAY, RAINING;

    private final String id = name().toLowerCase(Locale.ROOT);

    public int bit() {
        return 1 << ordinal();
    }

    public String id() {
        return id;
    }

    public static Condition parse(String name) {
        for (Condition condition : values()) {
            if (condition.id.equals(name.toLowerCase(Locale.ROOT))) {
                return condition;
            }
        }
        return null;
    }

    /** True when every required bit is set and no forbidden one is. */
    public static boolean test(int state, int required, int forbidden) {
        return (state & required) == required && (state & forbidden) == 0;
    }
}
