package com.skyrpg.economy.shop;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.skyrpg.core.skills.SkillLevels;
import com.skyrpg.economy.SkyRpgEconomy;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.TextColor;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

/**
 * A shop, loaded from {@code data/<ns>/skyrpg/shops/<id>.json}:
 * <pre>
 * {
 *   "title": "Thornfield Produce", "keeper": "Mabel Thornfield", "accent": "#9BE05A", "icon": "minecraft:wheat",
 *   "offers": [
 *     {"item": "skyrpg_items:harvester_helmet", "price": 650},
 *     {"item": "minecraft:wheat_seeds", "count": 16, "price": 40}
 *   ],
 *   "buys": ["minecraft:wheat", "minecraft:carrot", "#minecraft:logs"],
 *   "buys_hint": "Crops, seeds and produce"
 * }
 * </pre>
 * {@code price} is for {@code count} items (default 1). An offer may cost items instead of (or as well as) coins:
 * {@code "costs": [{"item": "skyrpg_mining:glimmer_crystal", "count": 3}]}, and be locked behind a skill level:
 * {@code "requires": {"skill": "skyrpg_skills:mining", "level": 10}}. {@code buys} lists items and #tags the keeper
 * buys back at their {@code sell_prices} price; {@code "all"} buys anything that has a price, and
 * leaving it out makes a shop that only sells. Offers naming an unknown item are skipped.
 */
public record ShopDefinition(Identifier id, String title, String keeper, int accent, Item icon, List<Offer> offers,
                             boolean buysAnything, Set<Item> buysItems, List<TagKey<Item>> buysTags, String buysHint) {

    /**
     * One thing for sale: {@code count} items for {@code price} coins and/or {@code costs} (other items, e.g.
     * a mining shop's Glimmer Crystals), optionally only for players with a skill level ({@code requires}).
     */
    public record Offer(Item item, int count, long price, List<Cost> costs, Identifier skill, int level) {
        /** Clamped here, not while parsing: item components (max stack size) aren't bound during the first data load. */
        @Override
        public int count() {
            return Math.min(count, item.getDefaultMaxStackSize());
        }

        /** Whether this player may buy it: no requirement, or their level in the skill is high enough. */
        public boolean unlocked(Player player) {
            return skill == null || SkillLevels.level(player, skill) >= level;
        }
    }

    /** Items paid per unit bought. */
    public record Cost(Item item, int count) {
    }

    /** Whether the keeper buys this item at all (it also needs a sell price). The enchanted form of an item it buys counts too. */
    public boolean buys(Item item) {
        if (buysAnything || buysItems.contains(item)) {
            return true;
        }
        Item material = com.skyrpg.core.items.Compacted.material(item);
        if (material != null && buys(material)) {
            return true;
        }
        for (TagKey<Item> tag : buysTags) {
            if (BuiltInRegistries.ITEM.wrapAsHolder(item).is(tag)) {
                return true;
            }
        }
        return false;
    }

    public boolean buysSomething() {
        return buysAnything || !buysItems.isEmpty() || !buysTags.isEmpty();
    }

    static ShopDefinition parse(Identifier id, JsonObject json) {
        List<Offer> offers = new ArrayList<>();
        if (json.has("offers")) {
            for (JsonElement element : json.getAsJsonArray("offers")) {
                JsonObject offer = element.getAsJsonObject();
                Item item = item(id, offer.get("item").getAsString());
                long price = offer.has("price") ? offer.get("price").getAsLong() : 0;
                int count = offer.has("count") ? offer.get("count").getAsInt() : 1;
                List<Cost> costs = new ArrayList<>();
                if (offer.has("costs")) {
                    for (JsonElement cost : offer.getAsJsonArray("costs")) {
                        JsonObject costJson = cost.getAsJsonObject();
                        Item costItem = item(id, costJson.get("item").getAsString());
                        int costCount = costJson.has("count") ? costJson.get("count").getAsInt() : 1;
                        if (costItem != null && costCount > 0) {
                            costs.add(new Cost(costItem, costCount));
                        }
                    }
                }
                Identifier skill = null;
                int level = 0;
                if (offer.has("requires")) {
                    JsonObject requires = offer.getAsJsonObject("requires");
                    skill = Identifier.parse(requires.get("skill").getAsString());
                    level = requires.get("level").getAsInt();
                }
                if (item != null && (price > 0 || !costs.isEmpty()) && count > 0) {
                    offers.add(new Offer(item, count, price, List.copyOf(costs), skill, level));
                }
            }
        }
        boolean anything = false;
        Set<Item> items = new HashSet<>();
        List<TagKey<Item>> tags = new ArrayList<>();
        JsonElement buys = json.get("buys");
        if (buys != null && buys.isJsonPrimitive()) {
            anything = buys.getAsString().equals("all");
        } else if (buys != null) {
            for (JsonElement entry : buys.getAsJsonArray()) {
                String name = entry.getAsString();
                if (name.startsWith("#")) {
                    tags.add(TagKey.create(Registries.ITEM, Identifier.parse(name.substring(1))));
                } else {
                    Item item = item(id, name);
                    if (item != null) {
                        items.add(item);
                    }
                }
            }
        }
        Item icon = json.has("icon") ? item(id, json.get("icon").getAsString()) : null;
        if (icon == null) {
            icon = offers.isEmpty() ? Items.EMERALD : offers.getFirst().item();
        }
        return new ShopDefinition(id,
                json.get("title").getAsString(),
                json.has("keeper") ? json.get("keeper").getAsString() : "",
                json.has("accent") ? TextColor.parseColor(json.get("accent").getAsString()).getOrThrow().getValue() | 0xFF000000 : 0xFFF2B33D,
                icon, List.copyOf(offers), anything, Set.copyOf(items), List.copyOf(tags),
                json.has("buys_hint") ? json.get("buys_hint").getAsString() : "");
    }

    private static Item item(Identifier shop, String name) {
        Identifier itemId = Identifier.tryParse(name);
        if (itemId == null || !BuiltInRegistries.ITEM.containsKey(itemId)) {
            SkyRpgEconomy.LOGGER.warn("Shop {} lists unknown item {}", shop, name);
            return null;
        }
        return BuiltInRegistries.ITEM.getValue(itemId);
    }
}
