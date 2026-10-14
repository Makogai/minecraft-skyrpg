package com.skyrpg.combat.slayer;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.TextColor;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

/**
 * One slayer line, from {@code data/<ns>/skyrpg/slayers/<id>.json}:
 * <pre>
 * {"name": "Grave Tyrant", "color": "#7FBF5F", "icon": "minecraft:rotten_flesh", "boss": "skyrpg_combat:grave_tyrant",
 *  "kind": "grave_tyrant", "counts": ["#minecraft:zombies", "skyrpg_combat:tide_lurker"], "counts_text": "zombies",
 *  "order": 1,
 *  "token": "skyrpg_combat:tyrants_ash", "rare": "skyrpg_combat:plague_heart", "level_stat": {"health": 3},
 *  "levels": [5, 15, 50, ...],
 *  "tiers": [{"fee": 1000, "xp": 100, "health": 600, "damage": 1.0, "level": 10, "minutes": 3, "slayer_xp": 5,
 *             "requires": 0, "tokens": [1, 2], "rare_chance": 0, "egg_chance": 0}, ...]}
 * </pre>
 * {@code kind} picks the boss's tricks (see {@link SlayerBosses}); {@code counts} are the mobs whose kills fill the quest
 * bar ({@code #} marks a tag).
 */
public record SlayerLine(String id, String name, int color, Item icon, Identifier boss, String kind, List<Identifier> countTypes,
                         List<TagKey<EntityType<?>>> countTags, String countsText, Item token, Item rare, Map<String, Double> levelStat,
                         List<Integer> levels, List<Tier> tiers) {
    public static final List<Integer> DEFAULT_LEVELS = List.of(5, 15, 50, 120, 300, 700, 1500, 3000, 6000);

    /** One tier of the line's quest. */
    public record Tier(long fee, int xp, double health, double damage, int level, int minutes, int slayerXp, int requires,
                       int tokensMin, int tokensMax, double rareChance, double eggChance) {
    }

    /** Whether killing this entity fills this line's quest bar. */
    public boolean counts(Entity entity) {
        if (countTypes.contains(BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()))) {
            return true;
        }
        for (TagKey<EntityType<?>> tag : countTags) {
            if (entity.typeHolder().is(tag)) {
                return true;
            }
        }
        return false;
    }

    /** The slayer level for this much XP (0 to the number of thresholds). */
    public int level(double xp) {
        int level = 0;
        for (int threshold : levels) {
            if (xp >= threshold) {
                level++;
            }
        }
        return level;
    }

    /** XP needed for the next level, or -1 at the top. */
    public int nextLevelXp(double xp) {
        for (int threshold : levels) {
            if (xp < threshold) {
                return threshold;
            }
        }
        return -1;
    }

    public Tier tier(int tier) {
        return tier >= 1 && tier <= tiers.size() ? tiers.get(tier - 1) : null;
    }

    static SlayerLine parse(Identifier id, JsonObject json) {
        List<Identifier> types = new ArrayList<>();
        List<TagKey<EntityType<?>>> tags = new ArrayList<>();
        for (JsonElement element : json.getAsJsonArray("counts")) {
            String entry = element.getAsString();
            if (entry.startsWith("#")) {
                tags.add(TagKey.create(Registries.ENTITY_TYPE, Identifier.parse(entry.substring(1))));
            } else {
                types.add(Identifier.parse(entry));
            }
        }
        Map<String, Double> levelStat = new LinkedHashMap<>();
        if (json.has("level_stat")) {
            json.getAsJsonObject("level_stat").entrySet().forEach(e -> levelStat.put(e.getKey(), e.getValue().getAsDouble()));
        }
        List<Integer> levels = new ArrayList<>();
        if (json.has("levels")) {
            json.getAsJsonArray("levels").forEach(e -> levels.add(e.getAsInt()));
        }
        List<Tier> tiers = new ArrayList<>();
        for (JsonElement element : json.getAsJsonArray("tiers")) {
            JsonObject t = element.getAsJsonObject();
            JsonArray tokens = t.has("tokens") ? t.getAsJsonArray("tokens") : null;
            tiers.add(new Tier(t.get("fee").getAsLong(), t.get("xp").getAsInt(), t.get("health").getAsDouble(),
                    t.has("damage") ? t.get("damage").getAsDouble() : 1, t.has("level") ? t.get("level").getAsInt() : 10,
                    t.has("minutes") ? t.get("minutes").getAsInt() : 3, t.get("slayer_xp").getAsInt(),
                    t.has("requires") ? t.get("requires").getAsInt() : 0,
                    tokens == null ? 0 : tokens.get(0).getAsInt(), tokens == null ? 0 : tokens.get(tokens.size() - 1).getAsInt(),
                    t.has("rare_chance") ? t.get("rare_chance").getAsDouble() : 0, t.has("egg_chance") ? t.get("egg_chance").getAsDouble() : 0));
        }
        return new SlayerLine(id.getPath(), json.get("name").getAsString(),
                json.has("color") ? TextColor.parseColor(json.get("color").getAsString()).getOrThrow().getValue() | 0xFF000000 : 0xFFE5534B,
                item(json, "icon", Items.ROTTEN_FLESH), Identifier.parse(json.get("boss").getAsString()),
                json.has("kind") ? json.get("kind").getAsString() : id.getPath(), List.copyOf(types), List.copyOf(tags),
                json.has("counts_text") ? json.get("counts_text").getAsString() : "its kind of mob",
                item(json, "token", Items.AIR), item(json, "rare", Items.AIR), Map.copyOf(levelStat),
                levels.isEmpty() ? DEFAULT_LEVELS : List.copyOf(levels), List.copyOf(tiers));
    }

    private static Item item(JsonObject json, String key, Item fallback) {
        return json.has(key) ? BuiltInRegistries.ITEM.getOptional(Identifier.parse(json.get(key).getAsString())).orElse(fallback) : fallback;
    }
}
