package com.skyrpg.skills.talent;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.resources.Identifier;

/**
 * One talent in a skill's tree, from {@code data/<ns>/skyrpg/talents/<name>.json}:
 * <pre>
 * {"skill": "skyrpg_skills:mining", "talents": [
 *   {"id": "glimmer_sense", "name": "Glimmer Sense", "icon": "skyrpg_mining:glimmer_shard", "ranks": 5, "level": 0,
 *    "description": "+8% Glimmer Shards per rank",
 *    "per_rank": {"stats": {"mining_speed": 8}, "effects": {"skyrpg_mining:shard_bonus": 0.08}}}
 * ]}
 * </pre>
 * Every rank adds {@code per_rank} again. {@code level} is the skill level needed for the first rank.
 */
public record Talent(String id, String name, Identifier icon, int ranks, int level, String description,
                     Map<String, Double> stats, Map<Identifier, Double> effects) {

    /** Every talent in a file: its skill and the talents in order. */
    public record Tree(Identifier skill, List<Talent> talents) {
    }

    static Tree parseTree(JsonObject json) {
        Identifier skill = Identifier.parse(json.get("skill").getAsString());
        List<Talent> talents = new ArrayList<>();
        for (JsonElement element : json.getAsJsonArray("talents")) {
            talents.add(parse(element.getAsJsonObject()));
        }
        return new Tree(skill, List.copyOf(talents));
    }

    private static Talent parse(JsonObject json) {
        Map<String, Double> stats = new LinkedHashMap<>();
        Map<Identifier, Double> effects = new LinkedHashMap<>();
        if (json.has("per_rank")) {
            JsonObject perRank = json.getAsJsonObject("per_rank");
            if (perRank.has("stats")) {
                perRank.getAsJsonObject("stats").entrySet().forEach(e -> stats.put(e.getKey(), e.getValue().getAsDouble()));
            }
            if (perRank.has("effects")) {
                perRank.getAsJsonObject("effects").entrySet().forEach(e -> effects.put(Identifier.parse(e.getKey()), e.getValue().getAsDouble()));
            }
        }
        return new Talent(json.get("id").getAsString(), json.get("name").getAsString(),
                Identifier.parse(json.has("icon") ? json.get("icon").getAsString() : "minecraft:book"),
                json.has("ranks") ? Math.max(1, json.get("ranks").getAsInt()) : 1,
                json.has("level") ? json.get("level").getAsInt() : 0,
                json.has("description") ? json.get("description").getAsString() : "",
                Map.copyOf(stats), Map.copyOf(effects));
    }
}
