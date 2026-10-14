package com.skyrpg.core.progress;

import java.util.function.Function;
import java.util.function.ToIntBiFunction;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Player;

/**
 * Collection tiers for any mod, without depending on the progress mod: the Artisan Bench locks recipes behind them
 * ({@code "collection": {"id": "skyrpg_progress:coal", "tier": 3}}). The progress mod installs the lookups (on the
 * server from the player's saved counts, on the client from the synced copy); without it every tier is 0.
 */
public final class CollectionLevels {
    private static ToIntBiFunction<Player, Identifier> lookup = (player, id) -> 0;
    private static Function<Identifier, String> names = id -> id.getPath().isEmpty() ? ""
            : Character.toUpperCase(id.getPath().charAt(0)) + id.getPath().substring(1).replace('_', ' ');

    private CollectionLevels() {
    }

    public static void install(ToIntBiFunction<Player, Identifier> installed) {
        lookup = installed;
    }

    public static void installNames(Function<Identifier, String> installed) {
        names = installed;
    }

    /** The player's tier (0 for none) in that collection. */
    public static int tier(Player player, Identifier collection) {
        return lookup.applyAsInt(player, collection);
    }

    /** "Coal" from {@code skyrpg_progress:coal}. */
    public static String displayName(Identifier collection) {
        return names.apply(collection);
    }

    /** 1 to "I", 4 to "IV"; the number itself above 3999. */
    public static String roman(int n) {
        if (n <= 0 || n >= 4000) {
            return String.valueOf(n);
        }
        int[] values = {1000, 900, 500, 400, 100, 90, 50, 40, 10, 9, 5, 4, 1};
        String[] symbols = {"M", "CM", "D", "CD", "C", "XC", "L", "XL", "X", "IX", "V", "IV", "I"};
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < values.length; i++) {
            while (n >= values[i]) {
                out.append(symbols[i]);
                n -= values[i];
            }
        }
        return out.toString();
    }

    /** "Requires Coal Collection V" body: "Coal Collection V". */
    public static String requirement(Identifier collection, int tier) {
        return displayName(collection) + " Collection " + roman(tier);
    }
}
