package com.skyrpg.items.crafting;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.skyrpg.core.progress.CollectionLevels;
import com.skyrpg.core.skills.SkillLevels;
import com.skyrpg.items.ModItems;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;

/**
 * One Artisan Bench recipe, from {@code data/<ns>/skyrpg/recipes/<name>.json}:
 * <pre>
 * {"category": "mining", "result": "skyrpg_items:skyiron_pickaxe", "count": 1,
 *  "ingredients": {"skyrpg_items:delver_pickaxe": 1, "skyrpg_mining:raw_skyiron": 32},
 *  "requires": {"skill": "skyrpg_skills:mining", "level": 18},
 *  "collection": {"id": "skyrpg_progress:coal", "tier": 3}}
 * </pre>
 * {@code count} defaults to 1, {@code requires} to nothing, and {@code category} to the required skill's name
 * (or "general"). The first ingredient that's a SkyRPG tool, armour piece, accessory or backpack is the one being
 * upgraded: the result keeps its name, enchantments and contents ({@code "upgrade": false} turns that off).
 */
public record BenchRecipe(Identifier id, String category, Item result, int count, List<Ingredient> ingredients,
                          Identifier skill, int level, Item upgrade, Identifier collection, int collectionTier) {
    public record Ingredient(Item item, int count) {
    }

    public boolean unlocked(Player player) {
        return (skill == null || SkillLevels.level(player, skill) >= level)
                && (collection == null || CollectionLevels.tier(player, collection) >= collectionTier);
    }

    /** "Mining 18", "Coal Collection V" or both with a comma, or "" if anyone can craft it. */
    public String requirement() {
        String skillPart = skill == null ? "" : SkillLevels.displayName(skill) + " " + level;
        String collectionPart = collection == null ? "" : CollectionLevels.requirement(collection, collectionTier);
        return skillPart.isEmpty() ? collectionPart : collectionPart.isEmpty() ? skillPart : skillPart + ", " + collectionPart;
    }

    static BenchRecipe parse(Identifier id, JsonObject json) {
        Item result = item(json.get("result").getAsString());
        int count = json.has("count") ? Math.max(1, json.get("count").getAsInt()) : 1;
        List<Ingredient> ingredients = new ArrayList<>();
        for (Map.Entry<String, JsonElement> entry : json.getAsJsonObject("ingredients").entrySet()) {
            ingredients.add(new Ingredient(item(entry.getKey()), Math.max(1, entry.getValue().getAsInt())));
        }
        if (ingredients.isEmpty()) {
            throw new IllegalArgumentException("no ingredients");
        }
        Identifier skill = null;
        int level = 0;
        if (json.has("requires")) {
            JsonObject requires = json.getAsJsonObject("requires");
            skill = Identifier.parse(requires.get("skill").getAsString());
            level = requires.get("level").getAsInt();
        }
        Identifier collection = null;
        int collectionTier = 0;
        if (json.has("collection")) {
            JsonObject required = json.getAsJsonObject("collection");
            collection = Identifier.parse(required.get("id").getAsString());
            collectionTier = required.get("tier").getAsInt();
        }
        String category = json.has("category") ? json.get("category").getAsString()
                : skill != null ? skill.getPath() : "general";
        Item upgrade = null;
        if (!json.has("upgrade") || json.get("upgrade").getAsBoolean()) {
            for (Ingredient ingredient : ingredients) {
                if (ModItems.kind(ingredient.item()) != null) {
                    upgrade = ingredient.item();
                    break;
                }
            }
        }
        return new BenchRecipe(id, category, result, count, List.copyOf(ingredients), skill, level, upgrade, collection, collectionTier);
    }

    private static Item item(String id) {
        Identifier key = Identifier.parse(id);
        return BuiltInRegistries.ITEM.getOptional(key).orElseThrow(() -> new IllegalArgumentException("unknown item " + id));
    }
}
