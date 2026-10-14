package com.skyrpg.core.world;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.ToIntBiFunction;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;

/**
 * Mob levels for any mod. The combat mod owns levels (and installs the backend); others, like the world mod's
 * dungeons, use this to give the mobs they spawn a set level. Does nothing until the combat mod is loaded.
 */
public final class MobLevelling {
    public interface Backend {
        /** Sets a mob's level and Elite flag, and scales its health and damage to match, like a world mob. */
        void level(Mob mob, int level, boolean elite);

        /**
         * Sets a boss's level without scaling it (the caller set its health), and its toughness: it takes
         * 1/toughness damage and its health shows that many times bigger.
         */
        void boss(Mob mob, int level, float toughness);

        int levelOf(Entity entity);
    }

    private static Backend backend;
    private static final List<ToIntBiFunction<ServerLevel, BlockPos>> BONUSES = new CopyOnWriteArrayList<>();

    private MobLevelling() {
    }

    public static void install(Backend installed) {
        backend = installed;
    }

    public static void level(Mob mob, int level, boolean elite) {
        if (backend != null) {
            backend.level(mob, level, elite);
        }
    }

    public static void boss(Mob mob, int level, float toughness) {
        if (backend != null) {
            backend.boss(mob, level, toughness);
        }
    }

    public static int levelOf(Entity entity) {
        return backend == null ? 0 : backend.levelOf(entity);
    }

    /** Extra levels for mobs spawning somewhere (the world mod's danger regions), on top of distance and depth. */
    public static void registerBonus(ToIntBiFunction<ServerLevel, BlockPos> bonus) {
        BONUSES.add(bonus);
    }

    /** The extra levels every registered bonus gives a mob spawning at {@code pos}. */
    public static int bonusAt(ServerLevel level, BlockPos pos) {
        int total = 0;
        for (ToIntBiFunction<ServerLevel, BlockPos> bonus : BONUSES) {
            total += bonus.applyAsInt(level, pos);
        }
        return total;
    }
}
