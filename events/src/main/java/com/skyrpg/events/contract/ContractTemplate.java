package com.skyrpg.events.contract;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.skyrpg.events.ScoringRule;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;

/**
 * A kind of contract the board can offer, from {@code data/<ns>/skyrpg/contracts/<id>.json}:
 * <pre>
 * {"name": "Wheat Harvest", "description": "Harvest grown wheat", "category": "farming",
 *  "goal": {"type": "break_blocks", "blocks": ["minecraft:wheat"], "grown_only": true},
 *  "amount": [128, 256],
 *  "requires": {"skill": "skyrpg_skills:farming", "level": 0},
 *  "rewards": {"coins": 600, "xp": {"skyrpg_skills:farming": 400}, "items": {"skyrpg_farming:dewdrop": 6}}}
 * </pre>
 * Goals: {@code break_blocks} and {@code kill_mobs} (like event scoring rules), {@code kill_elites}, and {@code deliver}
 * ({@code "item": "minecraft:carrot"}, handed in on the board). Coins and XP are for the smallest amount and grow with a
 * bigger one; items are fixed. A template whose items don't exist (their mod isn't installed) is skipped.
 */
public record ContractTemplate(Identifier id, String name, String description, String category, Goal goal, int minAmount,
                               int maxAmount, Identifier skill, int level, long coins, Map<Identifier, Double> xp,
                               Map<Item, Integer> items) {
    public enum GoalType {
        BREAK_BLOCKS, KILL_MOBS, KILL_ELITES, CATCH, DELIVER
    }

    /** What counts: a scoring rule for breaking and killing, or the item to deliver. */
    public record Goal(GoalType type, ScoringRule rule, Item item) {
    }

    static ContractTemplate parse(Identifier id, JsonObject json) {
        JsonObject goalJson = json.getAsJsonObject("goal");
        String type = goalJson.get("type").getAsString();
        Goal goal = switch (type) {
            case "break_blocks" -> new Goal(GoalType.BREAK_BLOCKS, ScoringRule.parse(goalJson), null);
            case "kill_mobs" -> new Goal(GoalType.KILL_MOBS, ScoringRule.parse(goalJson), null);
            case "kill_elites" -> new Goal(GoalType.KILL_ELITES, null, null);
            case "catch" -> new Goal(GoalType.CATCH, ScoringRule.parse(goalJson), null);
            case "deliver" -> new Goal(GoalType.DELIVER, null, item(goalJson.get("item").getAsString()));
            default -> throw new IllegalArgumentException("unknown goal type " + type);
        };
        JsonArray amount = json.getAsJsonArray("amount");
        int min = amount.get(0).getAsInt();
        int max = amount.size() > 1 ? amount.get(1).getAsInt() : min;
        Identifier skill = null;
        int level = 0;
        if (json.has("requires")) {
            JsonObject requires = json.getAsJsonObject("requires");
            skill = Identifier.parse(requires.get("skill").getAsString());
            level = requires.get("level").getAsInt();
        }
        JsonObject rewards = json.getAsJsonObject("rewards");
        Map<Identifier, Double> xp = new LinkedHashMap<>();
        if (rewards.has("xp")) {
            rewards.getAsJsonObject("xp").entrySet().forEach(e -> xp.put(Identifier.parse(e.getKey()), e.getValue().getAsDouble()));
        }
        Map<Item, Integer> items = new LinkedHashMap<>();
        if (rewards.has("items")) {
            rewards.getAsJsonObject("items").entrySet().forEach(e -> items.put(item(e.getKey()), e.getValue().getAsInt()));
        }
        return new ContractTemplate(id, json.get("name").getAsString(), json.has("description") ? json.get("description").getAsString() : "",
                json.has("category") ? json.get("category").getAsString() : "general", goal, min, Math.max(min, max), skill, level,
                rewards.has("coins") ? rewards.get("coins").getAsLong() : 0, Map.copyOf(xp), Map.copyOf(items));
    }

    private static Item item(String id) {
        Identifier key = Identifier.parse(id);
        if (!BuiltInRegistries.ITEM.containsKey(key)) {
            throw new IllegalArgumentException("unknown item " + id);
        }
        return BuiltInRegistries.ITEM.getValue(key);
    }
}
