package com.skyrpg.world;

import com.skyrpg.core.skills.SkillLevels;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;

/**
 * What a party has done in one dungeon since its boss last woke: mob packs cleared, secrets found, champions slain.
 * The boss's Reward Chest rolls once more for every few of those, so clearing the whole dungeon pays better than
 * running straight to the boss. Kept in memory (a run is one sitting); a dungeon is keyed by its structure start.
 */
final class DungeonRuns {
    static final Identifier DELVING = Identifier.fromNamespaceAndPath("skyrpg_skills", "delving");
    private static final int MAX_BONUS = 8;

    static final class Progress {
        int packs;
        int champions;
        final Set<UUID> secrets = new HashSet<>();

        int bonusRolls() {
            return Math.min(MAX_BONUS, packs / 3 + secrets.size() + 2 * champions);
        }

        String summary() {
            return packs + " pack" + (packs == 1 ? "" : "s") + " cleared · " + secrets.size() + " secret" + (secrets.size() == 1 ? "" : "s")
                    + " · " + champions + " champion" + (champions == 1 ? "" : "s");
        }
    }

    private static final Map<String, Progress> RUNS = new HashMap<>();

    private DungeonRuns() {
    }

    static Progress of(String instance) {
        return RUNS.computeIfAbsent(instance == null ? "" : instance, key -> new Progress());
    }

    static void reset(String instance) {
        RUNS.remove(instance == null ? "" : instance);
    }

    static void delving(ServerPlayer player, double xp) {
        if (xp > 0) {
            SkillLevels.giveXp(player, DELVING, xp);
        }
    }
}
