package com.skyrpg.crates;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.skyrpg.core.items.Boosts;
import com.skyrpg.core.text.Notice;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * Crate candy: eaten for a short boost of stats and gear effects, from {@code data/<ns>/skyrpg/candy/<item>.json}
 * ({@code {"stats": {...}, "effects": {...}, "minutes": 3}}). Candy shares one boost slot, so a new candy replaces the
 * last one, but it doesn't touch meal boosts.
 */
public final class Candy {
    static final String DIRECTORY = "skyrpg/candy";
    private static final Map<Item, Boost> BOOSTS = new HashMap<>();

    record Boost(Map<String, Double> stats, Map<Identifier, Double> effects, int minutes) {
    }

    private Candy() {
    }

    static void load(Map<Identifier, JsonElement> files) {
        BOOSTS.clear();
        files.forEach((id, json) -> {
            Item item = BuiltInRegistries.ITEM.getValue(id);
            JsonObject object = json.getAsJsonObject();
            Map<String, Double> stats = new HashMap<>();
            Map<Identifier, Double> effects = new HashMap<>();
            if (object.has("stats")) {
                object.getAsJsonObject("stats").entrySet().forEach(e -> stats.put(e.getKey(), e.getValue().getAsDouble()));
            }
            if (object.has("effects")) {
                object.getAsJsonObject("effects").entrySet().forEach(e -> effects.put(Identifier.parse(e.getKey()), e.getValue().getAsDouble()));
            }
            BOOSTS.put(item, new Boost(stats, effects, object.has("minutes") ? object.get("minutes").getAsInt() : 3));
        });
    }

    static void eat(ServerPlayer player, ItemStack stack) {
        Boost boost = BOOSTS.get(stack.getItem());
        if (boost == null) {
            return;
        }
        Component name = stack.getHoverName();
        Boosts.give(player, "candy", name, boost.stats(), boost.effects(), boost.minutes() * 60 * 20);
        Notice.send(player, Notice.Kind.CRATE, Component.literal("Sweet! ").append(name.copy().withColor(0xFF9A3C))
                .append(Component.literal(" boosts you for " + boost.minutes() + " minutes.").withColor(0xB4BCCB)));
    }

    /** Candy you eat (always, even when full) for its boost. */
    static final class CandyItem extends Item {
        CandyItem(Properties properties) {
            super(properties);
        }

        @Override
        public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity entity) {
            if (entity instanceof ServerPlayer player) {
                eat(player, stack);
            }
            return super.finishUsingItem(stack, level, entity);
        }
    }
}
