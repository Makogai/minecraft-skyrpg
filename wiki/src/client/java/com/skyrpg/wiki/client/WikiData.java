package com.skyrpg.wiki.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;

/**
 * The wiki bundle (index.json and pages/ID.json in assets/skyrpg_wiki/wiki), read from the client's resources. Everything is
 * tolerant: a missing or broken index gives an empty wiki, a missing or broken page gives null (the page shows a notice).
 * Pages are read on first use and kept until the resources reload.
 */
final class WikiData {
    record Category(String id, String name, String icon) {
    }

    record Entry(String id, String title, String category, int order, String icon, String summary, List<String> words) {
    }

    private static final String NAMESPACE = "skyrpg_wiki";
    private static boolean loaded;
    private static String webUrl = "";
    private static final List<Category> CATEGORIES = new ArrayList<>();
    private static final List<Entry> PAGES = new ArrayList<>();
    private static final Map<String, Entry> BY_ID = new HashMap<>();
    private static final Map<String, String> ITEMS = new HashMap<>();
    private static final Map<String, JsonObject> PAGE_CACHE = new HashMap<>();

    private WikiData() {
    }

    /** Forget everything; the next use reads the bundle again (resource reload). */
    static void invalidate() {
        loaded = false;
        PAGE_CACHE.clear();
    }

    private static void ensure() {
        if (loaded) {
            return;
        }
        loaded = true;
        webUrl = "";
        CATEGORIES.clear();
        PAGES.clear();
        BY_ID.clear();
        ITEMS.clear();
        JsonObject index = read("index.json");
        if (index == null) {
            return;
        }
        webUrl = string(index, "web_url", "");
        for (JsonElement e : array(index, "categories")) {
            if (e.isJsonObject()) {
                JsonObject c = e.getAsJsonObject();
                String id = string(c, "id", "");
                if (!id.isEmpty()) {
                    CATEGORIES.add(new Category(id, string(c, "name", id), string(c, "icon", "")));
                }
            }
        }
        for (JsonElement e : array(index, "pages")) {
            if (!e.isJsonObject()) {
                continue;
            }
            JsonObject p = e.getAsJsonObject();
            String id = string(p, "id", "");
            if (id.isEmpty() || BY_ID.containsKey(id)) {
                continue;
            }
            List<String> words = new ArrayList<>();
            for (JsonElement w : array(p, "words")) {
                if (w.isJsonPrimitive()) {
                    words.add(w.getAsString().toLowerCase(Locale.ROOT));
                }
            }
            int order = p.has("order") && p.get("order").isJsonPrimitive() ? p.get("order").getAsInt() : 100;
            Entry entry = new Entry(id, string(p, "title", id), string(p, "category", ""), order, string(p, "icon", ""),
                    string(p, "summary", ""), words);
            PAGES.add(entry);
            BY_ID.put(id, entry);
        }
        PAGES.sort(Comparator.comparingInt(Entry::order).thenComparing(Entry::title, String.CASE_INSENSITIVE_ORDER));
        if (index.has("items") && index.get("items").isJsonObject()) {
            for (Map.Entry<String, JsonElement> e : index.getAsJsonObject("items").entrySet()) {
                if (e.getValue().isJsonPrimitive()) {
                    ITEMS.put(e.getKey(), e.getValue().getAsString());
                }
            }
        }
    }

    static String webUrl() {
        ensure();
        return webUrl;
    }

    static List<Category> categories() {
        ensure();
        return CATEGORIES;
    }

    static List<Entry> pages() {
        ensure();
        return PAGES;
    }

    static Entry entry(String id) {
        ensure();
        return id == null ? null : BY_ID.get(id);
    }

    static Category category(String id) {
        ensure();
        for (Category c : CATEGORIES) {
            if (c.id().equals(id)) {
                return c;
            }
        }
        return null;
    }

    /** The page that holds an item's reference entry ("ns:path" to page id), or null. */
    static String pageForItem(String itemId) {
        ensure();
        return ITEMS.get(itemId);
    }

    /** A page's blocks, or null if it's missing or unreadable. */
    static JsonObject page(String id) {
        ensure();
        if (!BY_ID.containsKey(id)) {
            return null;
        }
        if (PAGE_CACHE.containsKey(id)) {
            return PAGE_CACHE.get(id);
        }
        JsonObject page = read("pages/" + id + ".json");
        PAGE_CACHE.put(id, page);
        return page;
    }

    private static JsonObject read(String path) {
        try {
            ResourceManager manager = Minecraft.getInstance().getResourceManager();
            Optional<Resource> resource = manager.getResource(Identifier.fromNamespaceAndPath(NAMESPACE, "wiki/" + path));
            if (resource.isEmpty()) {
                return null;
            }
            try (Reader reader = resource.get().openAsReader()) {
                JsonElement parsed = JsonParser.parseReader(reader);
                return parsed.isJsonObject() ? parsed.getAsJsonObject() : null;
            }
        } catch (Exception e) {
            SkyRpgWikiClient.LOGGER.warn("Could not read wiki file {}: {}", path, e.toString());
            return null;
        }
    }

    static String string(JsonObject o, String key, String fallback) {
        return o != null && o.has(key) && o.get(key).isJsonPrimitive() ? o.get(key).getAsString() : fallback;
    }

    static JsonArray array(JsonObject o, String key) {
        return o != null && o.has(key) && o.get(key).isJsonArray() ? o.getAsJsonArray(key) : new JsonArray();
    }

    /** Pages of a category, in order. */
    static List<Entry> pagesOf(String category) {
        List<Entry> out = new ArrayList<>();
        for (Entry e : pages()) {
            if (e.category().equals(category)) {
                out.add(e);
            }
        }
        return out;
    }

    /** Page ids for command suggestions. */
    static List<String> ids() {
        List<String> ids = new ArrayList<>();
        for (Entry e : pages()) {
            ids.add(e.id());
        }
        return ids;
    }

    static Map<String, String> items() {
        ensure();
        return new LinkedHashMap<>(ITEMS);
    }
}
