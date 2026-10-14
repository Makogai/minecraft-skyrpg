package com.skyrpg.events;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;

/**
 * How an event changes the game for the players in it: a join window before it starts, joining by
 * typing {@code .join}, putting their inventory away safely, a kit, gliding and where they play. All
 * optional; an event without any of it just runs where everyone already is.
 *
 * <p>JSON fields on the event:
 * <pre>{@code
 * lobby: "20s",              // join window before the clock starts (players opt in during it)
 * chat_join: true,           // ".join" / ".leave" in chat work for opt-in events (default true)
 * stash_inventory: true,     // put everyone's whole inventory away while they play, give it back after
 * kit: ["minecraft:elytra[minecraft:unbreakable={}] 1 chest", {item: "minecraft:firework_rocket", count: 64, slot: "hotbar.0"}],
 * auto_glide: true,          // start gliding as soon as they're in the air (needs an elytra on)
 * start_at: "skyrpg_hub:hub 0 160 30 180 15",   // or {dimension, x, y, z, yaw, pitch}
 * return: true               // send them back where they were afterwards (default true with start_at)
 * }</pre>
 * Stashed inventories are saved to {@code <world>/skyrpg_events/stash/<uuid>.dat} before anything is
 * cleared, so a crash can't lose them: whoever still has one when they next join gets it back.
 */
public record EventSetup(int lobbySeconds, boolean chatJoin, boolean stashInventory, List<KitItem> kit, boolean autoGlide,
                         Location startAt, boolean returnAfter) {
    public static final EventSetup NONE = new EventSetup(0, true, false, List.of(), false, null, true);

    /** A place to stand. */
    public record Location(Identifier dimension, double x, double y, double z, float yaw, float pitch) {
        static Location parse(JsonElement json) {
            if (json == null) {
                return null;
            }
            if (json.isJsonPrimitive()) {
                String[] parts = json.getAsString().trim().split("\\s+");
                if (parts.length < 4) {
                    throw new IllegalArgumentException("start_at should look like \"skyrpg_hub:hub 0 160 30 [yaw] [pitch]\"");
                }
                try {
                    return new Location(Json.id(parts[0], "dimension"), Double.parseDouble(parts[1]), Double.parseDouble(parts[2]), Double.parseDouble(parts[3]),
                            parts.length > 4 ? Float.parseFloat(parts[4]) : 0, parts.length > 5 ? Float.parseFloat(parts[5]) : 0);
                } catch (NumberFormatException e) {
                    throw new IllegalArgumentException("start_at has a bad number: " + json.getAsString());
                }
            }
            JsonObject object = json.getAsJsonObject();
            return new Location(Json.id(Json.string(object, "minecraft:overworld", "dimension", "world"), "dimension"),
                    Json.decimal(object, 0, "x"), Json.decimal(object, 64, "y"), Json.decimal(object, 0, "z"),
                    Json.decimal(object, 0, "yaw"), Json.decimal(object, 0, "pitch"));
        }
    }

    /**
     * An item handed out when someone starts playing. {@code item} uses /give syntax, components
     * included. {@code slot}: {@code head}, {@code chest}, {@code legs}, {@code feet}, {@code offhand},
     * {@code hotbar.0}-{@code hotbar.8}, {@code inventory.0}-{@code inventory.26}, or empty for the first free slot.
     */
    public record KitItem(String item, int count, String slot) {
        private static final Pattern TEXT = Pattern.compile("^(\\S+?(?:\\[.*])?)(?:\\s+(\\d+))?(?:\\s+(\\S+))?$");

        static KitItem parse(JsonElement json) {
            KitItem kit;
            if (json.isJsonPrimitive()) {
                Matcher matcher = TEXT.matcher(json.getAsString().trim());
                if (!matcher.matches()) {
                    throw new IllegalArgumentException("Kit item should look like \"minecraft:firework_rocket 64 hotbar.0\"");
                }
                kit = new KitItem(matcher.group(1), matcher.group(2) == null ? 1 : Integer.parseInt(matcher.group(2)),
                        matcher.group(3) == null ? "" : matcher.group(3));
            } else {
                JsonObject object = json.getAsJsonObject();
                kit = new KitItem(Json.string(object, "", "item", "id"), Json.integer(object, 1, "count"), Json.string(object, "", "slot"));
            }
            String id = kit.item.contains("[") ? kit.item.substring(0, kit.item.indexOf('[')) : kit.item;
            if (!BuiltInRegistries.ITEM.containsKey(Json.id(id, "item"))) {
                throw new IllegalArgumentException("Unknown kit item '" + id + "'");
            }
            return kit;
        }
    }

    static EventSetup parse(JsonObject json) {
        List<KitItem> kit = new ArrayList<>();
        for (JsonElement item : Json.array(json, "kit", "items")) {
            kit.add(KitItem.parse(item));
        }
        Location startAt = Location.parse(Json.get(json, "start_at", "teleport", "arena"));
        return new EventSetup(
                Json.seconds(Json.get(json, "lobby", "join_time", "starts_in"), 0),
                Json.bool(json, true, "chat_join"),
                Json.bool(json, false, "stash_inventory", "stash"),
                List.copyOf(kit),
                Json.bool(json, false, "auto_glide", "glide"),
                startAt,
                Json.bool(json, true, "return"));
    }

    /** Whether starting to play changes anything about the player. */
    public boolean equips() {
        return stashInventory || !kit.isEmpty() || startAt != null;
    }
}
