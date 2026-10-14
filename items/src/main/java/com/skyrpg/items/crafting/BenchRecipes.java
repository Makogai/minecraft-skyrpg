package com.skyrpg.items.crafting;

import com.google.gson.JsonElement;
import com.skyrpg.items.ModItems;
import com.skyrpg.items.SkyRpgItems;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;

/**
 * Every Artisan Bench recipe, loaded from {@code data/<ns>/skyrpg/recipes/} on start and on /reload. A recipe
 * whose items don't exist (say its mod isn't installed) is skipped with a warning. Kept in a stable order:
 * by category ("general" last), then level, then along upgrade chains, armour helmet to boots, then id.
 */
public final class BenchRecipes {
    public static final String DIRECTORY = "skyrpg/recipes";

    private static Map<Identifier, BenchRecipe> recipes = Map.of();

    private BenchRecipes() {
    }

    public static void load(Map<Identifier, JsonElement> files) {
        List<BenchRecipe> loaded = new ArrayList<>();
        files.forEach((id, json) -> {
            try {
                loaded.add(BenchRecipe.parse(id, json.getAsJsonObject()));
            } catch (RuntimeException e) {
                SkyRpgItems.LOGGER.warn("Skipping bench recipe {}: {}", id, e.getMessage());
            }
        });
        // Upgrade chains in order (Satchel before Trail Pack), armour helmet to boots.
        Map<Item, BenchRecipe> byResult = new HashMap<>();
        loaded.forEach(recipe -> byResult.putIfAbsent(recipe.result(), recipe));
        Map<BenchRecipe, Integer> depth = new HashMap<>();
        loaded.forEach(recipe -> depth.put(recipe, depth(recipe, byResult)));
        loaded.sort(Comparator.comparing((BenchRecipe r) -> r.category().equals("general"))
                .thenComparing(BenchRecipe::category)
                .thenComparingInt(BenchRecipe::level)
                .thenComparingInt(depth::get)
                .thenComparingInt(r -> ModItems.kind(r.result()) == null ? Integer.MAX_VALUE : ModItems.kind(r.result()).ordinal())
                .thenComparing(r -> r.id().toString()));
        Map<Identifier, BenchRecipe> map = new LinkedHashMap<>();
        loaded.forEach(recipe -> map.put(recipe.id(), recipe));
        recipes = map;
        SkyRpgItems.LOGGER.info("Loaded {} bench recipes", map.size());
    }

    /** How many upgrades lead to this recipe's input (0 if it doesn't upgrade another recipe's result). */
    private static int depth(BenchRecipe recipe, Map<Item, BenchRecipe> byResult) {
        int depth = 0;
        for (BenchRecipe at = recipe; at.upgrade() != null && byResult.containsKey(at.upgrade()) && depth < 16; depth++) {
            at = byResult.get(at.upgrade());
        }
        return depth;
    }

    public static Map<Identifier, BenchRecipe> all() {
        return recipes;
    }

    public static BenchRecipe get(Identifier id) {
        return recipes.get(id);
    }
}
