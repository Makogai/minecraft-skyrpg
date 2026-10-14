package com.skyrpg.items;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.BiFunction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;

/**
 * The item and set definitions currently loaded. On the server they come from data packs; on a
 * client they're what the server sent ({@link ItemDefinitionsPayload}), parsed by the same code.
 */
public final class ItemRegistry {
    public static final String ITEMS_DIRECTORY = "skyrpg/items";
    public static final String SETS_DIRECTORY = "skyrpg/item_sets";

    private static Map<Identifier, ItemDefinition> items = Map.of();
    private static Map<Identifier, ItemSet> sets = Map.of();

    private ItemRegistry() {
    }

    public static ItemDefinition item(Identifier id) {
        return items.get(id);
    }

    /** The definition of this registered item, or null if it has none. */
    public static ItemDefinition item(Item item) {
        return items.get(BuiltInRegistries.ITEM.getKey(item));
    }

    /** Matches "skyrpg_items:harvester_helmet" exactly, or just "harvester_helmet" by path. */
    public static ItemDefinition find(Identifier id) {
        ItemDefinition exact = items.get(id);
        if (exact != null) {
            return exact;
        }
        return items.values().stream().filter(def -> def.id().getPath().equals(id.getPath())).findFirst().orElse(null);
    }

    public static ItemSet set(Identifier id) {
        return id == null ? null : sets.get(id);
    }

    public static Collection<ItemDefinition> items() {
        return items.values();
    }

    public static Collection<ItemSet> sets() {
        return sets.values();
    }

    static void loadItems(Map<Identifier, JsonElement> files) {
        items = parseAll(files, ItemDefinition::parse, "item");
        SkyRpgItems.LOGGER.info("Loaded {} items", items.size());
    }

    static void loadSets(Map<Identifier, JsonElement> files) {
        sets = parseAll(files, ItemSet::parse, "item set");
        SkyRpgItems.LOGGER.info("Loaded {} item sets", sets.size());
    }

    /** Replaces everything with definitions sent by the server (client side). */
    public static void replace(Map<Identifier, JsonElement> itemFiles, Map<Identifier, JsonElement> setFiles) {
        items = parseAll(itemFiles, ItemDefinition::parse, "item");
        sets = parseAll(setFiles, ItemSet::parse, "item set");
    }

    private static <T> Map<Identifier, T> parseAll(Map<Identifier, JsonElement> files,
                                                   BiFunction<Identifier, JsonObject, T> parser, String kind) {
        Map<Identifier, T> parsed = new LinkedHashMap<>();
        files.forEach((id, json) -> {
            try {
                parsed.put(id, parser.apply(id, json.getAsJsonObject()));
            } catch (RuntimeException e) {
                SkyRpgItems.LOGGER.error("Skipping invalid {} {}: {}", kind, id, e.getMessage());
            }
        });
        return Collections.unmodifiableMap(parsed);
    }
}
