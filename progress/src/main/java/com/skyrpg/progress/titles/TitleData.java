package com.skyrpg.progress.titles;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.skyrpg.progress.SkyRpgProgress;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.resources.Identifier;

/**
 * The title catalogue, loaded from {@code data/<ns>/skyrpg/titles/<id>.json}: text (max 12 characters), icon (a glyph of
 * the chat icon font, {@code } to {@code }, or a plain symbol), rarity (sets the colour; {@code color} as
 * "#RRGGBB" overrides it), category, description, order, optional {@code hidden} (shows as "???" until earned) and the
 * {@code condition} (types in {@link TitleConditions}).
 */
public final class TitleData {
    public static final String DIRECTORY = "skyrpg/titles";
    public static final int MAX_TEXT = 12;

    public static final Map<String, Integer> RARITY_COLORS = Map.of(
            "common", 0xB8C0CC, "uncommon", 0x55DD77, "rare", 0x55A8FF, "epic", 0xB06AE8, "legendary", 0xFFAA00, "mythic", 0xFF5577);

    public record Def(Identifier id, String text, String icon, int color, String rarity, String category, String description,
                      int order, boolean hidden, JsonObject condition) {
    }

    private static volatile Map<Identifier, Def> defs = Map.of();
    private static volatile String json = "[]";

    private TitleData() {
    }

    public static Map<Identifier, Def> all() {
        return defs;
    }

    public static Def get(String id) {
        Identifier parsed = Identifier.tryParse(id);
        return parsed == null ? null : defs.get(parsed);
    }

    /** A def by full id, or by its path alone ("deep_miner"). */
    public static Def find(String text) {
        Def exact = get(text);
        if (exact != null) {
            return exact;
        }
        for (Def def : defs.values()) {
            if (def.id().getPath().equals(text)) {
                return def;
            }
        }
        return null;
    }

    /** The catalogue as JSON for the client (no conditions). */
    public static String json() {
        return json;
    }

    static void load(Map<Identifier, JsonElement> files) {
        List<Def> list = new ArrayList<>();
        files.forEach((id, element) -> {
            try {
                JsonObject o = element.getAsJsonObject();
                String text = o.get("text").getAsString();
                if (text.isEmpty() || text.length() > MAX_TEXT) {
                    SkyRpgProgress.LOGGER.warn("Title {} has text of {} characters (1 to {} fit well)", id, text.length(), MAX_TEXT);
                }
                String rarity = o.has("rarity") ? o.get("rarity").getAsString() : "common";
                int color = RARITY_COLORS.getOrDefault(rarity, 0xB8C0CC);
                if (o.has("color")) {
                    color = Integer.parseInt(o.get("color").getAsString().replace("#", ""), 16);
                }
                list.add(new Def(id, text, o.has("icon") ? o.get("icon").getAsString() : "", color & 0xFFFFFF, rarity,
                        o.has("category") ? o.get("category").getAsString() : "Other", o.get("description").getAsString(),
                        o.has("order") ? o.get("order").getAsInt() : 100, o.has("hidden") && o.get("hidden").getAsBoolean(),
                        o.getAsJsonObject("condition")));
            } catch (RuntimeException e) {
                SkyRpgProgress.LOGGER.warn("Skipping title {}: {}", id, e.getMessage());
            }
        });
        list.sort(Comparator.comparingInt(Def::order).thenComparing(def -> def.id().toString()));
        Map<Identifier, Def> sorted = new LinkedHashMap<>();
        JsonArray array = new JsonArray();
        for (Def def : list) {
            sorted.put(def.id(), def);
            JsonObject o = new JsonObject();
            o.addProperty("id", def.id().toString());
            o.addProperty("text", def.text());
            o.addProperty("icon", def.icon());
            o.addProperty("color", def.color());
            o.addProperty("rarity", def.rarity());
            o.addProperty("category", def.category());
            o.addProperty("description", def.description());
            o.addProperty("hidden", def.hidden());
            array.add(o);
        }
        defs = sorted;
        json = array.toString();
        SkyRpgProgress.LOGGER.info("Loaded {} titles", sorted.size());
    }
}
