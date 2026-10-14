package com.skyrpg.enchants;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.resources.Identifier;

/**
 * Enchanting's balance, from {@code data/skyrpg_enchants/skyrpg/enchanting/rules.json}: table costs per rarity and
 * level, bookshelves per level, what tomes cost to apply and combine, salvage yields, Enchanting XP, and where tomes
 * drop. Every number has a default here, so a missing file still makes a playable (if odd) game.
 */
public record EnchantRules(Map<EnchantRarity, List<Enchant.LevelCost>> table, int[] shelves, Map<EnchantRarity, Long> tomeCoins,
                           int tomeXpPerLevel, Map<EnchantRarity, Long> combineCoins, Map<EnchantRarity, Integer> combineDust,
                           Map<EnchantRarity, Integer> salvageDust, SkillXp skillXp, MobTomes mobTomes, Map<Identifier, SkillFind> skillFinds,
                           JsonObject json) {
    public static final EnchantRules DEFAULT = parse(new JsonObject());

    /** Enchanting XP for each kind of work. */
    public record SkillXp(double perXpLevel, double per1000Coins, double tomeApply, double combine, double salvage,
                          Map<EnchantRarity, Double> discover) {
    }

    /** Tomes from mobs a player kills: a small chance, a little better for tougher mobs. */
    public record MobTomes(double chance, double perHeart, double maxChance, int[] levelWeights, Map<EnchantRarity, Integer> rarityWeights,
                           List<Category> categories) {
    }

    /** Tomes found while earning a skill's XP: its tools' enchants, and very rarely its ultimate. */
    public record SkillFind(double chance, List<Category> categories, Identifier ultimate, double ultimateChance) {
    }

    public Enchant.LevelCost tableCost(EnchantRarity rarity, int level) {
        List<Enchant.LevelCost> costs = table.getOrDefault(rarity, table.get(EnchantRarity.COMMON));
        if (costs == null || costs.isEmpty()) {
            return new Enchant.LevelCost(1000L * level * level, 5 * level, 0, Map.of());
        }
        return costs.get(Math.min(Math.max(level, 1), costs.size()) - 1);
    }

    /** Bookshelves around a table needed to offer this level. */
    public int shelves(int level) {
        return shelves.length == 0 ? 0 : shelves[Math.min(Math.max(level, 1), shelves.length) - 1];
    }

    public long tomeCoins(EnchantRarity rarity, int level) {
        return tomeCoins.getOrDefault(rarity, 500L) * level;
    }

    public int tomeXp(int level) {
        return tomeXpPerLevel * level;
    }

    /** Combining two tomes into one of {@code result} level. */
    public long combineCoins(EnchantRarity rarity, int result) {
        return combineCoins.getOrDefault(rarity, 500L) * result * result;
    }

    public int combineDust(EnchantRarity rarity, int result) {
        return combineDust.getOrDefault(rarity, 0) * result;
    }

    public int salvageDust(EnchantRarity rarity, int level) {
        return salvageDust.getOrDefault(rarity, 1) * (1 << Math.min(Math.max(level, 1) - 1, 10));
    }

    public static EnchantRules parse(JsonObject json) {
        Map<EnchantRarity, List<Enchant.LevelCost>> table = new EnumMap<>(EnchantRarity.class);
        JsonObject tableJson = object(json, "table");
        for (EnchantRarity rarity : EnchantRarity.values()) {
            if (tableJson.has(rarity.key())) {
                List<Enchant.LevelCost> costs = new ArrayList<>();
                tableJson.getAsJsonArray(rarity.key()).forEach(e -> costs.add(Enchant.LevelCost.parse(e.getAsJsonObject())));
                table.put(rarity, List.copyOf(costs));
            }
        }
        int[] shelves = json.has("shelves") ? ints(json.getAsJsonArray("shelves")) : new int[]{0, 4, 8, 12, 15};
        JsonObject tome = object(json, "tome_apply");
        JsonObject combine = object(json, "combine");
        JsonObject xp = object(json, "skill_xp");
        JsonObject mob = object(json, "mob_tomes");
        Map<Identifier, SkillFind> finds = new LinkedHashMap<>();
        object(json, "skill_finds").entrySet().forEach(e -> {
            JsonObject find = e.getValue().getAsJsonObject();
            finds.put(Identifier.parse(e.getKey()), new SkillFind(number(find, "chance", 0), categories(find),
                    find.has("ultimate") ? Identifier.parse(find.get("ultimate").getAsString()) : null, number(find, "ultimate_chance", 0)));
        });
        return new EnchantRules(Map.copyOf(table), shelves, longs(object(tome, "coins")), (int) number(tome, "xp_per_level", 2),
                longs(object(combine, "coins")), integers(object(combine, "dust")), integers(object(json, "salvage")),
                new SkillXp(number(xp, "per_xp_level", 25), number(xp, "per_1000_coins", 10), number(xp, "tome_apply", 40),
                        number(xp, "combine", 60), number(xp, "salvage", 8), doubles(object(xp, "discover"))),
                new MobTomes(number(mob, "chance", 0.002), number(mob, "per_heart", 0), number(mob, "max_chance", 0.02),
                        mob.has("level_weights") ? ints(mob.getAsJsonArray("level_weights")) : new int[]{70, 25, 5},
                        integers(object(mob, "rarity_weights")), categories(mob)),
                Map.copyOf(finds), json);
    }

    private static JsonObject object(JsonObject json, String key) {
        return json.has(key) && json.get(key).isJsonObject() ? json.getAsJsonObject(key) : new JsonObject();
    }

    private static double number(JsonObject json, String key, double fallback) {
        return json.has(key) ? json.get(key).getAsDouble() : fallback;
    }

    private static int[] ints(JsonArray array) {
        int[] out = new int[array.size()];
        for (int i = 0; i < out.length; i++) {
            out[i] = array.get(i).getAsInt();
        }
        return out;
    }

    private static List<Category> categories(JsonObject json) {
        List<Category> out = new ArrayList<>();
        if (json.has("categories")) {
            json.getAsJsonArray("categories").forEach(e -> out.add(Category.parse(e.getAsString())));
        }
        return List.copyOf(out);
    }

    private static Map<EnchantRarity, Long> longs(JsonObject json) {
        Map<EnchantRarity, Long> out = new EnumMap<>(EnchantRarity.class);
        for (Map.Entry<String, JsonElement> e : json.entrySet()) {
            out.put(EnchantRarity.parse(e.getKey()), e.getValue().getAsLong());
        }
        return out;
    }

    private static Map<EnchantRarity, Integer> integers(JsonObject json) {
        Map<EnchantRarity, Integer> out = new EnumMap<>(EnchantRarity.class);
        for (Map.Entry<String, JsonElement> e : json.entrySet()) {
            out.put(EnchantRarity.parse(e.getKey()), e.getValue().getAsInt());
        }
        return out;
    }

    private static Map<EnchantRarity, Double> doubles(JsonObject json) {
        Map<EnchantRarity, Double> out = new EnumMap<>(EnchantRarity.class);
        for (Map.Entry<String, JsonElement> e : json.entrySet()) {
            out.put(EnchantRarity.parse(e.getKey()), e.getValue().getAsDouble());
        }
        return out;
    }
}
