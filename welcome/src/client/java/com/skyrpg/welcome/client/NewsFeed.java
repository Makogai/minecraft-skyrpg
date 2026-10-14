package com.skyrpg.welcome.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.loader.api.FabricLoader;

/**
 * The "What's new" box. Fetches {@code pack/news.json} from the website once per game session, in the background,
 * and silently shows nothing when offline or when the file is missing or broken.
 *
 * <pre>
 * {
 *   "latest": "2026.10.20-abc1234",           // optional: newest pack version, shown as "update available"
 *   "items": [                                 // newest first, the box shows up to 3
 *     { "date": "2026-10-14", "title": "Fishing rework", "text": "Hotspots, baits and sea creatures." }
 *   ]
 * }
 * </pre>
 */
final class NewsFeed {
    record Item(String date, String title, String text) {
    }

    private static volatile List<Item> items = List.of();
    private static volatile String latest = "";
    private static volatile boolean started;

    private NewsFeed() {
    }

    static List<Item> items() {
        return items;
    }

    static String latest() {
        return latest;
    }

    /** Starts the download the first time it is called; later calls do nothing. */
    static synchronized void fetchOnce() {
        if (started) {
            return;
        }
        started = true;
        String url = WelcomeConfig.newsUrl();
        if (url == null || url.isBlank()) {
            return;
        }
        try {
            HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(4)).build();
            HttpRequest request = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(5))
                    .header("User-Agent", "SkyRPG-Welcome/" + PackInfo.modVersion()).GET().build();
            client.sendAsync(request, HttpResponse.BodyHandlers.ofString()).thenAccept(response -> {
                if (response.statusCode() == 200) {
                    parse(response.body());
                }
            }).exceptionally(e -> null);
        } catch (RuntimeException e) {
            // bad URL: no news
        }
    }

    /** Public for the dev capture, which feeds sample news so screenshots do not depend on the network. */
    static void parse(String body) {
        try {
            JsonObject json = JsonParser.parseString(body).getAsJsonObject();
            List<Item> parsed = new ArrayList<>();
            JsonArray array = json.has("items") ? json.getAsJsonArray("items") : new JsonArray();
            for (JsonElement element : array) {
                JsonObject o = element.getAsJsonObject();
                parsed.add(new Item(text(o, "date"), text(o, "title"), text(o, "text")));
                if (parsed.size() >= 3) {
                    break;
                }
            }
            latest = text(json, "latest");
            items = List.copyOf(parsed);
        } catch (RuntimeException e) {
            SkyRpgWelcomeClient.LOGGER.debug("Ignoring a malformed news.json", e);
        }
    }

    private static String text(JsonObject o, String key) {
        return o.has(key) && o.get(key).isJsonPrimitive() ? o.get(key).getAsString() : "";
    }

    /** True if the news file names a newer pack than this one. */
    static boolean updateAvailable() {
        String current = PackInfo.packVersion();
        return !latest.isEmpty() && !current.isEmpty() && !latest.equals(current) && !current.equals(PackInfo.modVersion());
    }

    static String describe() {
        return FabricLoader.getInstance().isDevelopmentEnvironment() ? "dev" : "release";
    }
}
