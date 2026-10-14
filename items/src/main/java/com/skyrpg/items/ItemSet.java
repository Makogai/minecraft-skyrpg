package com.skyrpg.items;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.skyrpg.core.stats.StatType;
import com.skyrpg.core.stats.StatTypes;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.resources.Identifier;

/**
 * An armour set, loaded from {@code data/<namespace>/skyrpg/item_sets/<path>.json}:
 * <pre>
 * {
 *   "name": "Harvester Set",
 *   "pieces": 4,
 *   "bonus": {"name": "Sunlit Bounty", "description": "...", "stats": {"farming_fortune": 25}}
 * }
 * </pre>
 * Items join a set with {@code "set": "<set id>"}. Wearing {@code pieces} of them grants the bonus stats, and the
 * bonus's optional {@code "effects"} ({@code {"skyrpg_farming:no_trample": 1}}, see {@code GearEffects}).
 */
public record ItemSet(Identifier id, String name, int pieces, String bonusName, String bonusDescription,
                      Map<StatType, Double> bonusStats, Map<Identifier, Double> bonusEffects, JsonObject json) {

    public static ItemSet parse(Identifier id, JsonObject json) {
        JsonObject bonus = json.getAsJsonObject("bonus");
        Map<StatType, Double> stats = new LinkedHashMap<>();
        if (bonus.has("stats")) {
            for (Map.Entry<String, JsonElement> entry : bonus.getAsJsonObject("stats").entrySet()) {
                StatType stat = StatTypes.get(entry.getKey());
                if (stat == null) {
                    throw new IllegalArgumentException("unknown stat " + entry.getKey());
                }
                stats.put(stat, entry.getValue().getAsDouble());
            }
        }
        return new ItemSet(id, json.get("name").getAsString(), json.has("pieces") ? json.get("pieces").getAsInt() : 4,
                bonus.get("name").getAsString(), bonus.has("description") ? bonus.get("description").getAsString() : "",
                stats, ItemDefinition.effects(bonus.getAsJsonObject("effects")), json);
    }
}
