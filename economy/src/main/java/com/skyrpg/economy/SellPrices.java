package com.skyrpg.economy;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * What NPCs pay per item, loaded from {@code data/<namespace>/skyrpg/sell_prices/*.json}.
 * Each file is a flat object: {@code {"minecraft:wheat": 6, "minecraft:carrot": 3}}.
 * Items without a price can't be sold.
 */
public final class SellPrices {
    public static final String DIRECTORY = "skyrpg/sell_prices";

    private static Map<Item, Long> prices = Map.of();

    private SellPrices() {
    }

    /** Coins for one of this item, or 0 if NPCs won't buy it. */
    public static long priceOf(Item item) {
        return prices.getOrDefault(item, 0L);
    }

    /** Every item NPCs buy, with its price. */
    public static Map<Item, Long> all() {
        return prices;
    }

    /** Coins for the whole stack, or 0 if NPCs won't buy it. */
    public static long priceOf(ItemStack stack) {
        return stack.isEmpty() ? 0 : priceOf(stack.getItem()) * stack.getCount();
    }

    static void load(Map<Identifier, JsonElement> files) {
        Map<Item, Long> loaded = new HashMap<>();
        files.forEach((file, json) -> {
            if (!(json instanceof JsonObject object)) {
                SkyRpgEconomy.LOGGER.error("Sell price file {} must be a JSON object", file);
                return;
            }
            object.entrySet().forEach(entry -> {
                Identifier itemId = Identifier.tryParse(entry.getKey());
                if (itemId == null || !BuiltInRegistries.ITEM.containsKey(itemId)) {
                    SkyRpgEconomy.LOGGER.warn("Sell price file {} lists unknown item {}", file, entry.getKey());
                    return;
                }
                loaded.put(BuiltInRegistries.ITEM.getValue(itemId), entry.getValue().getAsLong());
            });
        });
        prices = Map.copyOf(loaded);
        SkyRpgEconomy.LOGGER.info("Loaded {} sell prices", prices.size());
    }
}
