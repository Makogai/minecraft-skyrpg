package com.skyrpg.core.world;

import net.minecraft.server.level.ServerLevel;

/**
 * Which danger region covers an overworld spot, for mods that can't depend on the world mod (the mounts mod's griffin
 * nests, for one). The world mod installs the lookup at startup; without it every spot is outside all regions.
 */
public final class Regions {
    /** The region type id at x, z ("blightlands", "shattered_highlands"...), or null. Safe on world generation threads. */
    public interface Lookup {
        String typeAt(ServerLevel level, int x, int z);
    }

    private static volatile Lookup lookup = (level, x, z) -> null;

    private Regions() {
    }

    public static void install(Lookup lookup) {
        Regions.lookup = lookup;
    }

    public static String typeAt(ServerLevel level, int x, int z) {
        return lookup.typeAt(level, x, z);
    }

    public static boolean in(ServerLevel level, int x, int z, String type) {
        return type.equals(typeAt(level, x, z));
    }
}
