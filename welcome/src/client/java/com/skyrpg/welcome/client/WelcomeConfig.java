package com.skyrpg.welcome.client;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import net.fabricmc.loader.api.FabricLoader;

/**
 * {@code config/skyrpg/welcome.json}: the server the Play button joins, whether the first-time setup was done, and
 * where the "What's new" box reads from. Written with defaults on first start so players can edit it.
 */
public final class WelcomeConfig {
    public static final String DEFAULT_ADDRESS = "26.194.174.124";
    public static final String DEFAULT_SITE = "https://makogai.github.io/minecraft-skyrpg/";
    public static final String DEFAULT_NEWS = DEFAULT_SITE + "pack/news.json";
    /** Bump to show the wizard again to everyone after a big change to it. */
    public static final int SETUP_VERSION = 1;

    private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("skyrpg").resolve("welcome.json");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static String serverName = "SkyRPG";
    private static String serverAddress = DEFAULT_ADDRESS;
    private static String newsUrl = DEFAULT_NEWS;
    private static String wikiUrl = DEFAULT_SITE;
    private static int setupDone;
    private static boolean loaded;

    private WelcomeConfig() {
    }

    public static synchronized void load() {
        if (loaded) {
            return;
        }
        loaded = true;
        if (!Files.exists(FILE)) {
            save();
            return;
        }
        try {
            JsonObject json = JsonParser.parseString(Files.readString(FILE)).getAsJsonObject();
            serverName = string(json, "serverName", serverName);
            serverAddress = string(json, "serverAddress", serverAddress);
            newsUrl = string(json, "newsUrl", newsUrl);
            wikiUrl = string(json, "wikiUrl", wikiUrl);
            setupDone = json.has("setupVersion") ? json.get("setupVersion").getAsInt() : 0;
        } catch (IOException | RuntimeException e) {
            SkyRpgWelcomeClient.LOGGER.warn("Couldn't read {}, using defaults", FILE, e);
        }
    }

    private static String string(JsonObject json, String key, String fallback) {
        return json.has(key) && json.get(key).isJsonPrimitive() ? json.get(key).getAsString() : fallback;
    }

    public static synchronized void save() {
        JsonObject json = new JsonObject();
        json.addProperty("_comment", "serverAddress is what 'Play SkyRPG' joins (host or host:port); empty opens the Multiplayer screen instead. setupVersion is set once the first-time setup was finished.");
        json.addProperty("serverName", serverName);
        json.addProperty("serverAddress", serverAddress);
        json.addProperty("newsUrl", newsUrl);
        json.addProperty("wikiUrl", wikiUrl);
        json.addProperty("setupVersion", setupDone);
        try {
            Files.createDirectories(FILE.getParent());
            Files.writeString(FILE, GSON.toJson(json));
        } catch (IOException e) {
            SkyRpgWelcomeClient.LOGGER.warn("Couldn't save {}", FILE, e);
        }
    }

    public static String serverName() {
        return serverName;
    }

    public static String serverAddress() {
        return serverAddress.trim();
    }

    public static String newsUrl() {
        return newsUrl;
    }

    public static String wikiUrl() {
        return wikiUrl;
    }

    public static boolean setupDone() {
        return setupDone >= SETUP_VERSION;
    }

    public static void setupDone(boolean done) {
        setupDone = done ? SETUP_VERSION : 0;
        save();
    }
}
