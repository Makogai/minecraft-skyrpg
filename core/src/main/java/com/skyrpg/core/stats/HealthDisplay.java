package com.skyrpg.core.stats;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.ToDoubleFunction;
import net.minecraft.world.entity.Entity;

/**
 * How much health an entity really has per point it shows, for health readouts (nameplates, the look-at box): the
 * combat mod's slayer bosses are tougher than vanilla's health cap allows, so their 1,024 vanilla health stands for more.
 * Works on both sides when the registering mod syncs what it needs.
 */
public final class HealthDisplay {
    private static final List<ToDoubleFunction<Entity>> SOURCES = new CopyOnWriteArrayList<>();

    private HealthDisplay() {
    }

    public static void register(ToDoubleFunction<Entity> source) {
        SOURCES.add(source);
    }

    /** All multipliers for this entity, multiplied together (1 when none apply). */
    public static double multiplier(Entity entity) {
        double multiplier = 1;
        for (ToDoubleFunction<Entity> source : SOURCES) {
            multiplier *= source.applyAsDouble(entity);
        }
        return multiplier;
    }
}
