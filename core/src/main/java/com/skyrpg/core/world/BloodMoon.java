package com.skyrpg.core.world;

/**
 * Whether a Blood Moon is on, for mods that can't depend on the world mod (the combat mod makes Elites three times as
 * likely, the items mod's Moonblood Charm gets stronger). The world mod sets it; false until then.
 */
public final class BloodMoon {
    private static volatile boolean active;

    private BloodMoon() {
    }

    public static boolean active() {
        return active;
    }

    public static void set(boolean on) {
        active = on;
    }
}
