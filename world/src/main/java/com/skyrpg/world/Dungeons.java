package com.skyrpg.world;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import net.minecraft.resources.Identifier;

/**
 * Dungeon and dungeon boss definitions, from data: {@code skyrpg/dungeons/*.json} and {@code skyrpg/dungeon_bosses/*.json}.
 * Formats in docs/design/dungeons.md.
 */
public final class Dungeons {
    public static final String DUNGEONS_DIRECTORY = "skyrpg/dungeons";
    public static final String BOSSES_DIRECTORY = "skyrpg/dungeon_bosses";
    public static final int DEFAULT_RESET_TICKS = 72_000;

    /** A dungeon: the structure it is, its name and colour for banners and maps, and its vault key. */
    public record Dungeon(Identifier id, String name, Identifier structure, int color, Identifier icon, int level, Identifier boss,
                          Identifier key, int resetTicks, String description) {
    }

    /** One boss move (see the doc for types and fields); {@code json} keeps the type's own fields. */
    public record Move(String type, int every, int delay, double below, String warn, JsonObject json) {
        double num(String key, double fallback) {
            return json.has(key) ? json.get(key).getAsDouble() : fallback;
        }

        String str(String key, String fallback) {
            return json.has(key) ? json.get(key).getAsString() : fallback;
        }
    }

    /** A health threshold the boss announces once ("The Warden calls the dead!"). */
    public record Phase(double below, String message) {
    }

    /**
     * A dungeon boss or champion (tier "champion": a mini-boss with smaller rewards and no Reward Chest). Every kill pays
     * each participant {@code coins} (a random amount in range) and {@code delvingXp}; the first kill also pays the
     * first-clear reward. {@code healthPerPlayer} adds health for each extra player in the room when it wakes.
     */
    public record Boss(Identifier id, String name, Identifier entity, Identifier dungeon, int level, double health, double damage, double speed,
                       double scale, double knockbackResistance, String barColor, Map<String, String> equipment, String intro, String death,
                       int maxMinions, Identifier loot, long firstClearCoins, Map<Identifier, Double> firstClearXp, int resetTicks,
                       List<Move> moves, boolean champion, long coinsMin, long coinsMax, double delvingXp, Identifier rewardLoot,
                       double healthPerPlayer, List<Phase> phases, boolean region, List<String> tags) {
        /** The same boss in another body (a region boss whose mod's entity isn't installed). */
        Boss withEntity(Identifier other) {
            return new Boss(id, name, other, dungeon, level, health, damage, speed, scale, knockbackResistance, barColor, equipment, intro, death,
                    maxMinions, loot, firstClearCoins, firstClearXp, resetTicks, moves, champion, coinsMin, coinsMax, delvingXp, rewardLoot,
                    healthPerPlayer, phases, region, tags);
        }
    }

    private static Map<Identifier, Dungeon> dungeons = Map.of();
    private static Map<Identifier, Boss> bosses = Map.of();

    private Dungeons() {
    }

    public static Dungeon dungeon(Identifier id) {
        return id == null ? null : dungeons.get(id);
    }

    public static Collection<Dungeon> dungeons() {
        return dungeons.values();
    }

    /** The dungeon that is this structure, or null. */
    public static Dungeon byStructure(Identifier structure) {
        for (Dungeon dungeon : dungeons.values()) {
            if (dungeon.structure().equals(structure)) {
                return dungeon;
            }
        }
        return null;
    }

    public static Boss boss(Identifier id) {
        return id == null ? null : bosses.get(id);
    }

    public static Collection<Boss> bosses() {
        return bosses.values();
    }

    static void loadDungeons(Map<Identifier, JsonElement> files) {
        Map<Identifier, Dungeon> loaded = new TreeMap<>();
        files.forEach((id, element) -> {
            try {
                JsonObject json = element.getAsJsonObject();
                String color = str(json, "color", "#B98CFF").replace("#", "");
                loaded.put(id, new Dungeon(id, str(json, "name", id.getPath()), Identifier.parse(str(json, "structure", id.toString())),
                        0xFF000000 | Integer.parseInt(color, 16), Identifier.parse(str(json, "icon", "minecraft:compass")), (int) num(json, "level", 1),
                        json.has("boss") ? Identifier.parse(json.get("boss").getAsString()) : null,
                        json.has("key") ? Identifier.parse(json.get("key").getAsString()) : null,
                        (int) num(json, "reset_ticks", DEFAULT_RESET_TICKS), str(json, "description", "")));
            } catch (RuntimeException e) {
                SkyRpgWorld.LOGGER.warn("Skipping dungeon {}: {}", id, e.getMessage());
            }
        });
        dungeons = loaded;
    }

    static void loadBosses(Map<Identifier, JsonElement> files) {
        Map<Identifier, Boss> loaded = new TreeMap<>();
        files.forEach((id, element) -> {
            try {
                JsonObject json = element.getAsJsonObject();
                Map<String, String> equipment = new TreeMap<>();
                if (json.has("equipment")) {
                    json.getAsJsonObject("equipment").entrySet().forEach(e -> equipment.put(e.getKey(), e.getValue().getAsString()));
                }
                Map<Identifier, Double> xp = new TreeMap<>();
                long coins = 0;
                if (json.has("first_clear")) {
                    JsonObject first = json.getAsJsonObject("first_clear");
                    coins = (long) num(first, "coins", 0);
                    if (first.has("xp")) {
                        first.getAsJsonObject("xp").entrySet().forEach(e -> xp.put(Identifier.parse(e.getKey()), e.getValue().getAsDouble()));
                    }
                }
                List<Move> moves = new ArrayList<>();
                JsonArray moveArray = json.has("moves") ? json.getAsJsonArray("moves") : new JsonArray();
                for (JsonElement m : moveArray) {
                    JsonObject move = m.getAsJsonObject();
                    int every = (int) num(move, "every", 200);
                    moves.add(new Move(str(move, "type", "slam"), Math.max(20, every), (int) num(move, "delay", every / 2.0),
                            num(move, "below", 1.0), str(move, "warn", ""), move));
                }
                long coinsMin = 0;
                long coinsMax = 0;
                if (json.has("coins") && json.get("coins").isJsonArray() && json.getAsJsonArray("coins").size() == 2) {
                    coinsMin = json.getAsJsonArray("coins").get(0).getAsLong();
                    coinsMax = Math.max(coinsMin, json.getAsJsonArray("coins").get(1).getAsLong());
                } else if (json.has("coins")) {
                    coinsMin = coinsMax = (long) num(json, "coins", 0);
                }
                List<Phase> phases = new ArrayList<>();
                if (json.has("phases") && json.get("phases").isJsonArray()) {
                    for (JsonElement p : json.getAsJsonArray("phases")) {
                        JsonObject phase = p.getAsJsonObject();
                        phases.add(new Phase(num(phase, "below", 0.5), str(phase, "message", "")));
                    }
                }
                loaded.put(id, new Boss(id, str(json, "name", id.getPath()), Identifier.parse(str(json, "entity", "minecraft:zombie")),
                        json.has("dungeon") ? Identifier.parse(json.get("dungeon").getAsString()) : null, (int) num(json, "level", 10),
                        num(json, "health", 1000), num(json, "damage", 8), num(json, "speed", 0.28), num(json, "scale", 1.4),
                        num(json, "knockback_resistance", 0.7), str(json, "bar_color", "red"), equipment, str(json, "intro", ""),
                        str(json, "death", ""), (int) num(json, "max_minions", 6),
                        json.has("loot") ? Identifier.parse(json.get("loot").getAsString()) : null, coins, xp,
                        (int) num(json, "reset_ticks", DEFAULT_RESET_TICKS), List.copyOf(moves),
                        str(json, "tier", "boss").equals("champion"), coinsMin, coinsMax, num(json, "delving_xp", 0),
                        json.has("reward_loot") ? Identifier.parse(json.get("reward_loot").getAsString()) : null,
                        num(json, "health_per_player", 0.6), List.copyOf(phases), str(json, "tier", "boss").equals("region"), tags(json)));
            } catch (RuntimeException e) {
                SkyRpgWorld.LOGGER.warn("Skipping dungeon boss {}: {}", id, e.getMessage());
            }
        });
        bosses = loaded;
    }

    /** The strings in a JSON array field, or none. */
    static List<String> tags(JsonObject json) {
        List<String> tags = new ArrayList<>();
        if (json.has("tags") && json.get("tags").isJsonArray()) {
            json.getAsJsonArray("tags").forEach(tag -> tags.add(tag.getAsString()));
        }
        return List.copyOf(tags);
    }

    static String str(JsonObject json, String key, String fallback) {
        return json.has(key) && json.get(key).isJsonPrimitive() ? json.get(key).getAsString() : fallback;
    }

    static double num(JsonObject json, String key, double fallback) {
        return json.has(key) && json.get(key).isJsonPrimitive() ? json.get(key).getAsDouble() : fallback;
    }

    /** True for JSON true or a non-zero number (marker data arrives through NBT, where booleans are bytes). */
    static boolean bool(JsonObject json, String key, boolean fallback) {
        if (!json.has(key) || !json.get(key).isJsonPrimitive()) {
            return fallback;
        }
        var value = json.get(key).getAsJsonPrimitive();
        return value.isBoolean() ? value.getAsBoolean() : value.isNumber() ? value.getAsDouble() != 0 : Boolean.parseBoolean(value.getAsString());
    }
}
