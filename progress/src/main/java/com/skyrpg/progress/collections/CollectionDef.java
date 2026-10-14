package com.skyrpg.progress.collections;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.skyrpg.core.stats.StatType;
import com.skyrpg.core.stats.StatTypes;
import com.skyrpg.core.text.Msg;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * One collection, from {@code data/<ns>/skyrpg/collections/<id>.json}:
 * <pre>
 * {"category": "mining", "name": "Coal", "icon": "minecraft:coal", "items": ["minecraft:coal"],   (or "tag": "minecraft:logs")
 *  "thresholds": [50, 100, 250, 1000, 2500, 5000, 10000, 25000, 50000],
 *  "tiers": [{"coins": 100, "xp": 4, "stats": {"mining_fortune": 1}, "recipes": ["Skyiron Pickaxe"], "message": "..."}, ...]}
 * </pre>
 * {@code xp} defaults to 4 per tier. {@code tiers} may be shorter than {@code thresholds}.
 */
public record CollectionDef(Identifier id, String category, String name, Identifier icon, List<Identifier> items, TagKey<Item> tag,
                            long[] thresholds, List<Tier> tiers) {
    public static final int XP_PER_TIER = 4;

    /** What reaching one tier gives. */
    public record Tier(long coins, int xp, Map<String, Double> stats, List<String> recipes, String message) {
        /** "+250 coins, +4 SkyRPG XP, +1 Mining Fortune, unlocks Skyiron Pickaxe". */
        public String describe() {
            List<String> parts = new ArrayList<>();
            if (coins > 0) {
                parts.add("+" + Msg.number(coins) + " coins");
            }
            if (xp > 0) {
                parts.add("+" + xp + " SkyRPG XP");
            }
            stats.forEach((id, value) -> {
                StatType type = StatTypes.get(id);
                if (type != null) {
                    parts.add(type.formatBonus(value) + " " + type.name());
                }
            });
            if (!recipes.isEmpty()) {
                parts.add("unlocks " + String.join(", ", recipes));
            }
            return String.join(", ", parts);
        }
    }

    public int tierCount() {
        return thresholds.length;
    }

    /** The tier (0..9) a count reaches. */
    public int tierFor(long count) {
        int tier = 0;
        while (tier < thresholds.length && count >= thresholds[tier]) {
            tier++;
        }
        return tier;
    }

    public Tier tier(int tier) {
        return tier >= 1 && tier <= tiers.size() ? tiers.get(tier - 1) : new Tier(0, XP_PER_TIER, Map.of(), List.of(), "");
    }

    public boolean matches(ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        if (tag != null && stack.is(tag)) {
            return true;
        }
        return !items.isEmpty() && items.contains(BuiltInRegistries.ITEM.getKey(stack.getItem()));
    }

    static CollectionDef parse(Identifier id, JsonObject json) {
        List<Identifier> items = new ArrayList<>();
        if (json.has("items")) {
            for (JsonElement element : json.getAsJsonArray("items")) {
                items.add(Identifier.parse(element.getAsString()));
            }
        }
        TagKey<Item> tag = json.has("tag") ? TagKey.create(Registries.ITEM, Identifier.parse(json.get("tag").getAsString())) : null;
        if (items.isEmpty() && tag == null) {
            throw new IllegalArgumentException("no items or tag");
        }
        JsonArray thresholdArray = json.getAsJsonArray("thresholds");
        long[] thresholds = new long[thresholdArray.size()];
        for (int i = 0; i < thresholds.length; i++) {
            thresholds[i] = thresholdArray.get(i).getAsLong();
        }
        List<Tier> tiers = new ArrayList<>();
        if (json.has("tiers")) {
            for (JsonElement element : json.getAsJsonArray("tiers")) {
                JsonObject tier = element.getAsJsonObject();
                Map<String, Double> stats = new LinkedHashMap<>();
                if (tier.has("stats")) {
                    tier.getAsJsonObject("stats").entrySet().forEach(entry -> stats.put(entry.getKey(), entry.getValue().getAsDouble()));
                }
                List<String> recipes = new ArrayList<>();
                if (tier.has("recipes")) {
                    tier.getAsJsonArray("recipes").forEach(recipe -> recipes.add(recipe.getAsString()));
                }
                tiers.add(new Tier(tier.has("coins") ? tier.get("coins").getAsLong() : 0, tier.has("xp") ? tier.get("xp").getAsInt() : XP_PER_TIER,
                        Map.copyOf(stats), List.copyOf(recipes), tier.has("message") ? tier.get("message").getAsString() : ""));
            }
        }
        String name = json.has("name") ? json.get("name").getAsString() : id.getPath();
        Identifier icon = json.has("icon") ? Identifier.parse(json.get("icon").getAsString()) : items.isEmpty() ? null : items.get(0);
        String category = json.has("category") ? json.get("category").getAsString() : "other";
        return new CollectionDef(id, category, name, icon, List.copyOf(items), tag, thresholds, List.copyOf(tiers));
    }
}
