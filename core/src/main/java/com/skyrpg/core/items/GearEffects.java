package com.skyrpg.core.items;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;

/**
 * Effects a player has, for any mod: named switches with a strength, like {@code skyrpg_farming:reseed} or
 * {@code skyrpg_mining:shard_bonus} (+0.08 = 8% more shards). Sources register here: the {@code skyrpg_items} mod adds
 * gear (full sets, held items, accessories) and timed boosts, the skills mod adds talents. Strengths from every source
 * add up; each skill's mod decides what its own effects do.
 * <pre>
 * if (GearEffects.has(player, RESEED)) { replant(); }
 * double bonus = GearEffects.value(player, SHARD_BONUS); // 0 if none
 * </pre>
 */
public final class GearEffects {
    private static final List<Function<ServerPlayer, Map<Identifier, Double>>> SOURCES = new ArrayList<>();

    private GearEffects() {
    }

    /** Adds a source of effects (gear, boosts, talents...). */
    public static void addSource(Function<ServerPlayer, Map<Identifier, Double>> source) {
        SOURCES.add(source);
    }

    /** The effect's total strength for this player, 0 if they don't have it. */
    public static double value(ServerPlayer player, Identifier effect) {
        double total = 0;
        for (Function<ServerPlayer, Map<Identifier, Double>> source : SOURCES) {
            total += source.apply(player).getOrDefault(effect, 0.0);
        }
        return total;
    }

    public static boolean has(ServerPlayer player, Identifier effect) {
        return value(player, effect) > 0;
    }

    /** Every effect the player has right now, with its total strength. */
    public static Map<Identifier, Double> all(ServerPlayer player) {
        Map<Identifier, Double> all = new LinkedHashMap<>();
        for (Function<ServerPlayer, Map<Identifier, Double>> source : SOURCES) {
            source.apply(player).forEach((id, value) -> all.merge(id, value, Double::sum));
        }
        return all;
    }
}
