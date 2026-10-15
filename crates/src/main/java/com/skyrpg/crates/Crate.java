package com.skyrpg.crates;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.skyrpg.core.event.ItemGains;
import com.skyrpg.items.SkyItems;
import com.skyrpg.pets.PetRarity;
import com.skyrpg.pets.PetType;
import com.skyrpg.pets.Pets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Prediction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * A crate, from {@code data/<ns>/skyrpg/crates/<id>.json}: its item and key, the season it drops in, how often it
 * drops, and its tiers (blue to gold, each with a weight and its rewards). See {@code docs/design/crates.md}.
 */
public record Crate(Identifier id, String name, String subtitle, Item item, Item key, String keyHint, LocalDate until, Drops drops,
                    int announceFrom, List<Tier> tiers) {

    /** Drop chances per kill (before Magic Find): by day, at night (and in the Nether and End), Blood Moon, elites, bosses. */
    public record Drops(double day, double night, double bloodMoon, double elite, double boss) {
    }

    public record Tier(String name, int color, double weight, boolean mystery, List<Reward> rewards) {
        Reward pick(RandomSource random) {
            double total = rewards.stream().mapToDouble(Reward::weight).sum();
            double roll = random.nextDouble() * total;
            for (Reward reward : rewards) {
                roll -= reward.weight();
                if (roll <= 0) {
                    return reward;
                }
            }
            return rewards.getLast();
        }
    }

    /**
     * One prize: an item ({@code item}, {@code count}), several at once ({@code set}, shown with {@code icon} and
     * {@code name}), a Pumpkin Pouch of coins ({@code pouch}), or a pet ({@code pet}, {@code rarity}).
     */
    public record Reward(List<Identifier> items, int count, long coins, String pet, String petRarity, String name, Identifier icon,
                         double weight) {
        /** What it looks like on the reel and in the contents. */
        ItemStack display() {
            if (pet != null) {
                return Pets.create(typeOf(), rarityOf());
            }
            if (coins > 0) {
                return CrateItems.pouch(coins);
            }
            Identifier shown = icon != null ? icon : items.getFirst();
            ItemStack stack = stack(shown, items.size() == 1 ? count : 1);
            return stack;
        }

        String displayName() {
            if (name != null) {
                return name;
            }
            if (coins > 0) {
                return String.format("%,d Coins", coins);
            }
            if (pet != null) {
                return typeOf().displayName() + " Pet (" + capital(rarityOf().name()) + ")";
            }
            String base = display().getHoverName().getString();
            return count > 1 ? base + " ×" + count : base;
        }

        void give(ServerPlayer player) {
            List<ItemStack> stacks = new ArrayList<>();
            if (pet != null) {
                stacks.add(Pets.create(typeOf(), rarityOf()));
            } else if (coins > 0) {
                stacks.add(CrateItems.pouch(coins));
            } else {
                for (Identifier id : items) {
                    stacks.add(stack(id, items.size() == 1 ? count : 1));
                }
            }
            for (ItemStack stack : stacks) {
                ItemGains.give(player, stack, ItemGains.Source.OTHER);
                if (!stack.isEmpty()) {
                    player.drop(stack, false, Prediction.SERVER_ONLY);
                }
            }
        }

        private PetType typeOf() {
            for (PetType type : PetType.values()) {
                if (type.id().equals(pet)) {
                    return type;
                }
            }
            return PetType.values()[0];
        }

        private PetRarity rarityOf() {
            try {
                return PetRarity.valueOf(petRarity.toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException | NullPointerException e) {
                return PetRarity.COMMON;
            }
        }

        private static ItemStack stack(Identifier id, int count) {
            ItemStack sky = SkyItems.create(id, count);
            if (!sky.isEmpty()) {
                return sky;
            }
            Item item = BuiltInRegistries.ITEM.getValue(id);
            return new ItemStack(item, count);
        }

        private static String capital(String text) {
            return text.charAt(0) + text.substring(1).toLowerCase(Locale.ROOT);
        }
    }

    double totalWeight() {
        return tiers.stream().mapToDouble(Tier::weight).sum();
    }

    /** A tier picked by weight (the {@code bend} power evens the odds out for show: 1 = true odds). */
    int pickTier(RandomSource random, double bend) {
        double total = tiers.stream().mapToDouble(t -> Math.pow(t.weight(), bend)).sum();
        double roll = random.nextDouble() * total;
        for (int i = 0; i < tiers.size(); i++) {
            roll -= Math.pow(tiers.get(i).weight(), bend);
            if (roll <= 0) {
                return i;
            }
        }
        return tiers.size() - 1;
    }

    boolean inSeason() {
        return until == null || !LocalDate.now().isAfter(until);
    }

    static Crate parse(Identifier id, JsonObject json) {
        JsonObject drops = json.getAsJsonObject("drops");
        List<Tier> tiers = new ArrayList<>();
        for (JsonElement element : json.getAsJsonArray("tiers")) {
            JsonObject tier = element.getAsJsonObject();
            List<Reward> rewards = new ArrayList<>();
            for (JsonElement r : tier.getAsJsonArray("rewards")) {
                rewards.add(reward(r.getAsJsonObject()));
            }
            tiers.add(new Tier(tier.get("name").getAsString(), 0xFF000000 | Integer.parseInt(tier.get("color").getAsString().substring(1), 16),
                    tier.get("weight").getAsDouble(), tier.has("mystery") && tier.get("mystery").getAsBoolean(), List.copyOf(rewards)));
        }
        return new Crate(id, json.get("name").getAsString(), json.has("subtitle") ? json.get("subtitle").getAsString() : "",
                BuiltInRegistries.ITEM.getValue(Identifier.parse(json.get("item").getAsString())),
                BuiltInRegistries.ITEM.getValue(Identifier.parse(json.get("key").getAsString())),
                json.has("key_hint") ? json.get("key_hint").getAsString() : "You need a key to open this crate",
                json.has("until") ? LocalDate.parse(json.get("until").getAsString()) : null,
                new Drops(number(drops, "day"), number(drops, "night"), number(drops, "blood_moon"), number(drops, "elite"), number(drops, "boss")),
                json.has("announce_from") ? json.get("announce_from").getAsInt() : 2, List.copyOf(tiers));
    }

    private static Reward reward(JsonObject json) {
        List<Identifier> items = new ArrayList<>();
        if (json.has("item")) {
            items.add(Identifier.parse(json.get("item").getAsString()));
        }
        if (json.has("set")) {
            JsonArray set = json.getAsJsonArray("set");
            set.forEach(e -> items.add(Identifier.parse(e.getAsString())));
        }
        return new Reward(List.copyOf(items), json.has("count") ? json.get("count").getAsInt() : 1,
                json.has("pouch") ? json.get("pouch").getAsLong() : 0,
                json.has("pet") ? json.get("pet").getAsString() : null,
                json.has("rarity") ? json.get("rarity").getAsString() : null,
                json.has("name") ? json.get("name").getAsString() : null,
                json.has("icon") ? Identifier.parse(json.get("icon").getAsString()) : null,
                json.has("weight") ? json.get("weight").getAsDouble() : 1);
    }

    private static double number(JsonObject json, String key) {
        return json != null && json.has(key) ? json.get(key).getAsDouble() : 0;
    }
}
