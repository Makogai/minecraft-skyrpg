package com.skyrpg.core.stats;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.ToDoubleBiFunction;
import java.util.function.ToDoubleFunction;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;

/**
 * Multipliers on the damage something takes, for any mod: the combat mod makes its slayer bosses tougher than vanilla's
 * health cap allows (0.25 = takes a quarter of the damage), and slayer weapons hit their own kind harder. The stats mod
 * applies them to every hit.
 */
public final class IncomingDamage {
    private static final List<ToDoubleBiFunction<LivingEntity, DamageSource>> SOURCES = new CopyOnWriteArrayList<>();

    private IncomingDamage() {
    }

    /** A multiplier that depends only on who's hit. */
    public static void register(ToDoubleFunction<LivingEntity> source) {
        SOURCES.add((target, damage) -> source.applyAsDouble(target));
    }

    /** A multiplier that also depends on the hit (who dealt it, how). */
    public static void registerHit(ToDoubleBiFunction<LivingEntity, DamageSource> source) {
        SOURCES.add(source);
    }

    /** All multipliers for this hit, multiplied together (1 when none apply). */
    public static double multiplier(LivingEntity target, DamageSource damage) {
        double multiplier = 1;
        for (ToDoubleBiFunction<LivingEntity, DamageSource> source : SOURCES) {
            multiplier *= source.applyAsDouble(target, damage);
        }
        return multiplier;
    }
}
