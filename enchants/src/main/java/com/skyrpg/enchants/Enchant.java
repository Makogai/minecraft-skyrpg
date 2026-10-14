package com.skyrpg.enchants;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.skyrpg.core.stats.StatType;
import com.skyrpg.core.stats.StatTypes;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

/**
 * One enchant, loaded from {@code data/<namespace>/skyrpg/enchants/<path>.json}:
 * <pre>
 * {
 *   "name": "Cleave",
 *   "rarity": "uncommon",                     // common, uncommon, rare, epic, legendary, ultimate
 *   "applies_to": ["sword"],                  // see Category
 *   "max_level": 6,                           // highest level at all (tomes and combining go past the table)
 *   "table_max": 5,                           // highest level the table offers; 0 = tome only
 *   "values": {"share": [0.08, 0.12, 0.16, 0.2, 0.25, 0.3], "radius": [2.5, 3, 3.5, 4, 4.5, 5]},
 *   "effects": {"skyrpg_enchants:cleave": "share"},   // gear effects: a value's name, a number (x level) or a list
 *   "stats": {"crit_damage": [8, 16, 24]},           // stats: the same
 *   "description": "Hits also strike enemies within {radius} blocks for {share%} of the damage.",
 *   "conflicts": ["skyrpg_enchants:keen"],    // optional; conflicts work both ways
 *   "source": "Rare drop from the Grave Tyrant (tier III+)",  // optional: where the Codex says to find it
 *   "costs": [{"coins": 500, "xp": 3, "requires": 0, "materials": {"minecraft:lapis_lazuli": 4}}]  // optional: replaces rules.json's table costs
 * }
 * </pre>
 * In descriptions, {@code {name}} is a value at the shown level and {@code {name%}} the same as a percentage.
 */
public record Enchant(Identifier id, String name, EnchantRarity rarity, List<Category> appliesTo, int maxLevel, int tableMax,
                      List<Identifier> conflicts, Map<String, double[]> values, Map<Identifier, double[]> effects,
                      Map<StatType, double[]> stats, String description, String source, List<LevelCost> costs, JsonObject json) {
    private static final Pattern PLACEHOLDER = Pattern.compile("\\{([a-z0-9_]+)(%?)}");

    /** What one level costs at the table (or overrides rules.json for this enchant). */
    public record LevelCost(long coins, int xp, int requires, Map<Identifier, Integer> materials) {
        static LevelCost parse(JsonObject json) {
            Map<Identifier, Integer> materials = new LinkedHashMap<>();
            if (json.has("materials")) {
                json.getAsJsonObject("materials").entrySet().forEach(e -> materials.put(Identifier.parse(e.getKey()), e.getValue().getAsInt()));
            }
            return new LevelCost(json.has("coins") ? json.get("coins").getAsLong() : 0, json.has("xp") ? json.get("xp").getAsInt() : 0,
                    json.has("requires") ? json.get("requires").getAsInt() : 0, Map.copyOf(materials));
        }
    }

    public boolean ultimate() {
        return rarity == EnchantRarity.ULTIMATE;
    }

    /** Offered at the enchanting table (otherwise only tomes bring it). */
    public boolean tableable() {
        return tableMax > 0;
    }

    public boolean appliesTo(ItemStack stack) {
        for (Category category : appliesTo) {
            if (category.matches(stack)) {
                return true;
            }
        }
        return false;
    }

    public boolean conflictsWith(Identifier other) {
        if (other.equals(id)) {
            return false;
        }
        if (conflicts.contains(other)) {
            return true;
        }
        Enchant def = Enchants.get(other);
        return def != null && def.conflicts.contains(id);
    }

    /** A named value at this level (the last one past the end of its list), 0 if there's no such value. */
    public double value(String key, int level) {
        return at(values.get(key), level);
    }

    public double effect(Identifier effect, int level) {
        return at(effects.get(effect), level);
    }

    public double stat(StatType stat, int level) {
        return at(stats.get(stat), level);
    }

    private static double at(double[] list, int level) {
        if (list == null || list.length == 0 || level <= 0) {
            return 0;
        }
        return list[Math.min(level, list.length) - 1];
    }

    /** The description at this level, placeholders filled in. */
    public String describe(int level) {
        Matcher matcher = PLACEHOLDER.matcher(description);
        StringBuilder out = new StringBuilder();
        while (matcher.find()) {
            double value = value(matcher.group(1), level);
            String text = matcher.group(2).isEmpty() ? format(value) : format(value * 100) + "%";
            matcher.appendReplacement(out, Matcher.quoteReplacement(text));
        }
        matcher.appendTail(out);
        return out.toString();
    }

    /** "5", "2.5", "0.25": no trailing zeros. */
    public static String format(double value) {
        if (Math.abs(value - Math.rint(value)) < 1e-6) {
            return Long.toString(Math.round(value));
        }
        String text = String.format(java.util.Locale.ROOT, "%.2f", value);
        while (text.endsWith("0")) {
            text = text.substring(0, text.length() - 1);
        }
        return text.endsWith(".") ? text.substring(0, text.length() - 1) : text;
    }

    /** The table cost of a level: this enchant's own, or the rarity's from rules.json. */
    public LevelCost cost(int level) {
        if (!costs.isEmpty()) {
            return costs.get(Math.min(level, costs.size()) - 1);
        }
        return Enchants.rules().tableCost(rarity, level);
    }

    /** What the Codex says about where it comes from. */
    public String sourceText() {
        if (!source.isEmpty()) {
            return source;
        }
        return tableable() ? "The enchanting table, up to " + roman(tableMax) + (maxLevel > tableMax ? "; combine tomes for more" : "")
                : "Found as a tome";
    }

    public static String roman(int level) {
        return com.skyrpg.core.text.Msg.roman(level);
    }

    /** "Keen V". */
    public String title(int level) {
        return maxLevel == 1 ? name : name + " " + roman(level);
    }

    public static Enchant parse(Identifier id, JsonObject json) {
        EnchantRarity rarity = EnchantRarity.parse(json.get("rarity").getAsString());
        int maxLevel = json.has("max_level") ? json.get("max_level").getAsInt() : 1;
        int tableMax = rarity == EnchantRarity.ULTIMATE ? 0 : json.has("table_max") ? json.get("table_max").getAsInt() : maxLevel;
        if (maxLevel < 1 || tableMax < 0 || tableMax > maxLevel || tableMax > 5) {
            throw new IllegalArgumentException("levels must be 1 <= table_max <= max_level, table_max <= 5");
        }
        List<Category> appliesTo = new ArrayList<>();
        json.getAsJsonArray("applies_to").forEach(e -> appliesTo.add(Category.parse(e.getAsString())));
        if (appliesTo.isEmpty()) {
            throw new IllegalArgumentException("applies_to is empty");
        }
        List<Identifier> conflicts = new ArrayList<>();
        if (json.has("conflicts")) {
            json.getAsJsonArray("conflicts").forEach(e -> conflicts.add(Identifier.parse(e.getAsString())));
        }
        Map<String, double[]> values = new LinkedHashMap<>();
        if (json.has("values")) {
            json.getAsJsonObject("values").entrySet().forEach(e -> values.put(e.getKey(), levels(e.getValue(), maxLevel, values)));
        }
        Map<Identifier, double[]> effects = new LinkedHashMap<>();
        if (json.has("effects")) {
            json.getAsJsonObject("effects").entrySet().forEach(e -> effects.put(Identifier.parse(e.getKey()), levels(e.getValue(), maxLevel, values)));
        }
        Map<StatType, double[]> stats = new LinkedHashMap<>();
        if (json.has("stats")) {
            for (Map.Entry<String, JsonElement> e : json.getAsJsonObject("stats").entrySet()) {
                StatType stat = StatTypes.get(e.getKey());
                if (stat == null || !StatTypes.isPlayerStat(stat)) {
                    throw new IllegalArgumentException("unknown stat " + e.getKey());
                }
                stats.put(stat, levels(e.getValue(), maxLevel, values));
            }
        }
        List<LevelCost> costs = new ArrayList<>();
        if (json.has("costs")) {
            json.getAsJsonArray("costs").forEach(e -> costs.add(LevelCost.parse(e.getAsJsonObject())));
        }
        return new Enchant(id, json.get("name").getAsString(), rarity, List.copyOf(appliesTo), maxLevel, tableMax, List.copyOf(conflicts),
                values, effects, stats, json.has("description") ? json.get("description").getAsString() : "",
                json.has("source") ? json.get("source").getAsString() : "", List.copyOf(costs), json);
    }

    /** A list per level from a value's name, a number (times the level) or a list. */
    private static double[] levels(JsonElement element, int maxLevel, Map<String, double[]> values) {
        if (element.isJsonArray()) {
            JsonArray array = element.getAsJsonArray();
            double[] out = new double[array.size()];
            for (int i = 0; i < out.length; i++) {
                out[i] = array.get(i).getAsDouble();
            }
            return out;
        }
        if (element.getAsJsonPrimitive().isString()) {
            double[] named = values.get(element.getAsString());
            if (named == null) {
                throw new IllegalArgumentException("no value named " + element.getAsString());
            }
            return named;
        }
        double perLevel = element.getAsDouble();
        double[] out = new double[maxLevel];
        for (int i = 0; i < maxLevel; i++) {
            out[i] = perLevel * (i + 1);
        }
        return out;
    }
}
