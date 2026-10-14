package com.skyrpg.core.combat;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.skyrpg.core.SkyRpgCore;
import java.util.Map;
import net.minecraft.resources.Identifier;

/**
 * The combat tag and warm-up numbers, from {@code data/<namespace>/skyrpg/combat/*.json} (the last file by id wins; a
 * datapack can override them and /reload applies them).
 *
 * @param tagTicks      how long a player stays in combat after the last hit dealt or taken
 * @param warmupTicks   how long a player has to stand still before a teleport goes through
 * @param moveTolerance blocks a player may drift during a warm-up before it is cancelled
 */
public record CombatRules(int tagTicks, int warmupTicks, double moveTolerance) {
    public static final String DIRECTORY = "skyrpg/combat";
    public static final CombatRules DEFAULT = new CombatRules(200, 100, 0.3);

    private static CombatRules current = DEFAULT;

    public static CombatRules get() {
        return current;
    }

    static void load(Map<Identifier, JsonElement> files) {
        files.forEach((id, json) -> {
            try {
                JsonObject object = json.getAsJsonObject();
                current = new CombatRules(
                        Math.max(0, Math.round(object.get("tag_seconds").getAsFloat() * 20)),
                        Math.max(0, Math.round(object.get("warmup_seconds").getAsFloat() * 20)),
                        Math.max(0.0, object.get("move_tolerance").getAsDouble()));
            } catch (RuntimeException e) {
                SkyRpgCore.LOGGER.error("Skipping invalid combat rules {}: {}", id, e.getMessage());
            }
        });
    }
}
