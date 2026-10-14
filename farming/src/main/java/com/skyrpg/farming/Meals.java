package com.skyrpg.farming;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.skyrpg.core.items.Boosts;
import com.skyrpg.core.stats.StatType;
import com.skyrpg.core.stats.StatTypes;
import com.skyrpg.core.text.Msg;
import com.skyrpg.core.text.Notice;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * What each meal does, from {@code data/<ns>/skyrpg/meals/<item>.json} (the file's id is the meal's item id):
 * <pre>
 * {"stats": {"farming_fortune": 25}, "effects": {}, "minutes": 10}
 * </pre>
 * Eating one gives that boost; meals share one boost slot, so a new meal replaces the last.
 */
public final class Meals {
    public static final String DIRECTORY = "skyrpg/meals";

    private record Meal(Map<String, Double> stats, Map<Identifier, Double> effects, int minutes) {
    }

    private static Map<Identifier, Meal> meals = Map.of();

    private Meals() {
    }

    static void load(Map<Identifier, JsonElement> files) {
        Map<Identifier, Meal> loaded = new LinkedHashMap<>();
        files.forEach((id, element) -> {
            try {
                JsonObject json = element.getAsJsonObject();
                Map<String, Double> stats = new LinkedHashMap<>();
                if (json.has("stats")) {
                    json.getAsJsonObject("stats").entrySet().forEach(e -> stats.put(e.getKey(), e.getValue().getAsDouble()));
                }
                Map<Identifier, Double> effects = new HashMap<>();
                if (json.has("effects")) {
                    json.getAsJsonObject("effects").entrySet().forEach(e -> effects.put(Identifier.parse(e.getKey()), e.getValue().getAsDouble()));
                }
                loaded.put(id, new Meal(stats, effects, json.has("minutes") ? json.get("minutes").getAsInt() : 10));
            } catch (RuntimeException e) {
                SkyRpgFarming.LOGGER.warn("Skipping meal {}: {}", id, e.getMessage());
            }
        });
        meals = loaded;
    }

    static void eat(ServerPlayer player, Item item) {
        Meal meal = meals.get(BuiltInRegistries.ITEM.getKey(item));
        if (meal == null) {
            return;
        }
        Component name = new ItemStack(item).getHoverName();
        Boosts.give(player, "meal", name, meal.stats(), meal.effects(), meal.minutes() * 1200);
        StringBuilder gains = new StringBuilder();
        meal.stats().forEach((id, amount) -> {
            StatType stat = StatTypes.get(id);
            if (stat != null) {
                gains.append(gains.isEmpty() ? "" : ", ").append(stat.formatBonus(amount)).append(' ').append(stat.name());
            }
        });
        Notice.of(Notice.Kind.FARMING).tag("WELL FED").send(player, Msg.text("", ChatFormatting.GOLD)
                .append(Msg.text(gains + " for " + meal.minutes() + " minutes", ChatFormatting.YELLOW)));
        Msg.playSound(player, SoundEvents.PLAYER_BURP, 0.6f, 1.2f);
    }
}
