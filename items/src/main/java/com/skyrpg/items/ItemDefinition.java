package com.skyrpg.items;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.skyrpg.core.stats.StatType;
import com.skyrpg.core.stats.StatTypes;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;

/**
 * What one registered item does, loaded from {@code data/<namespace>/skyrpg/items/<path>.json}. The file's
 * id is the item's id ({@code skyrpg_items:harvester_helmet}), and the item itself ({@link ModItems})
 * decides what it is: armour slot, tool, look.
 * <pre>
 * {
 *   "name": "Harvester Hat",
 *   "rarity": "uncommon",
 *   "stats": {"health": 15, "farming_fortune": 10},
 *   "set": "skyrpg_items:harvester",             // optional
 *   "description": "...",                        // optional
 *   "glint": true,                               // optional
 *   "ability": {"type": "mend", "name": "Mend", "mana": 60, "cooldown": 5,
 *               "description": "...", "params": {"amount": 40}},  // optional
 *   "effects": {"skyrpg_farming:reseed": 1},     // optional: gear effects while held or worn (see GearEffects)
 *   "perks": ["Replants what you harvest"]       // optional: lines the tooltip shows for those effects
 * }
 * </pre>
 * Stats are keyed by {@link StatType} id. "damage" is weapon damage and sets the item's attack damage.
 *
 * @param json the definition as loaded, so it can be sent to clients unchanged
 */
public record ItemDefinition(Identifier id, Item item, String name, Rarity rarity, Map<StatType, Double> stats,
                             Identifier set, String description, boolean glint, Ability ability, Map<Identifier, Double> effects,
                             List<String> perks, JsonObject json) {

    /** An item's right-click ability. {@code params} holds type-specific settings like "distance". */
    public record Ability(String type, String name, double mana, double cooldownSeconds, String description, JsonObject params) {
        public double param(String key, double fallback) {
            return params.has(key) ? params.get(key).getAsDouble() : fallback;
        }
    }

    public double stat(StatType stat) {
        return stats.getOrDefault(stat, 0.0);
    }

    public static ItemDefinition parse(Identifier id, JsonObject json) {
        if (!BuiltInRegistries.ITEM.containsKey(id)) {
            throw new IllegalArgumentException("no registered item " + id);
        }
        Map<StatType, Double> stats = new LinkedHashMap<>();
        if (json.has("stats")) {
            for (Map.Entry<String, JsonElement> entry : json.getAsJsonObject("stats").entrySet()) {
                StatType stat = StatTypes.get(entry.getKey());
                if (stat == null) {
                    throw new IllegalArgumentException("unknown stat " + entry.getKey());
                }
                stats.put(stat, entry.getValue().getAsDouble());
            }
        }
        Ability ability = null;
        if (json.has("ability")) {
            JsonObject a = json.getAsJsonObject("ability");
            ability = new Ability(a.get("type").getAsString(), a.get("name").getAsString(),
                    a.has("mana") ? a.get("mana").getAsDouble() : 0,
                    a.has("cooldown") ? a.get("cooldown").getAsDouble() : 0,
                    a.has("description") ? a.get("description").getAsString() : "",
                    a.has("params") ? a.getAsJsonObject("params") : new JsonObject());
        }
        List<String> perks = new ArrayList<>();
        if (json.has("perks")) {
            json.getAsJsonArray("perks").forEach(line -> perks.add(line.getAsString()));
        }
        return new ItemDefinition(id, BuiltInRegistries.ITEM.getValue(id), json.get("name").getAsString(),
                json.has("rarity") ? Rarity.parse(json.get("rarity").getAsString()) : Rarity.COMMON,
                stats,
                json.has("set") ? Identifier.parse(json.get("set").getAsString()) : null,
                json.has("description") ? json.get("description").getAsString() : "",
                json.has("glint") && json.get("glint").getAsBoolean(),
                ability, effects(json.getAsJsonObject("effects")), List.copyOf(perks), json);
    }

    /** {@code {"skyrpg_farming:reseed": 1}} as a map (empty if absent). */
    static Map<Identifier, Double> effects(JsonObject json) {
        Map<Identifier, Double> effects = new LinkedHashMap<>();
        if (json != null) {
            for (Map.Entry<String, JsonElement> entry : json.entrySet()) {
                effects.put(Identifier.parse(entry.getKey()), entry.getValue().getAsDouble());
            }
        }
        return Map.copyOf(effects);
    }
}
