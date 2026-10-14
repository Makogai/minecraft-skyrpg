package com.skyrpg.helper;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import net.fabricmc.loader.api.FabricLoader;

/** config/skyrpg/helper.json: where the helper service runs and how often a player may ask. Written with defaults once. */
final class HelperConfig {
    private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("skyrpg").resolve("helper.json");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    static boolean enabled = true;
    static String url = "http://127.0.0.1:8770";
    static int cooldownSeconds = 8;
    static int timeoutSeconds = 90;
    /** Start tools/skyhelper/helper.py when nothing answers at {@link #url}, and stop it with the server. */
    static boolean autostart = true;
    static String python = "python";
    /** The service script; empty means look for tools/skyhelper/helper.py above the server's folder. */
    static String script = "";

    private HelperConfig() {
    }

    static void load() {
        try {
            if (Files.exists(FILE)) {
                JsonObject json = GSON.fromJson(Files.readString(FILE, StandardCharsets.UTF_8), JsonObject.class);
                enabled = json.has("enabled") ? json.get("enabled").getAsBoolean() : enabled;
                url = json.has("url") ? json.get("url").getAsString() : url;
                cooldownSeconds = json.has("cooldownSeconds") ? json.get("cooldownSeconds").getAsInt() : cooldownSeconds;
                timeoutSeconds = json.has("timeoutSeconds") ? json.get("timeoutSeconds").getAsInt() : timeoutSeconds;
                autostart = json.has("autostart") ? json.get("autostart").getAsBoolean() : autostart;
                python = json.has("python") ? json.get("python").getAsString() : python;
                script = json.has("script") ? json.get("script").getAsString() : script;
                return;
            }
            JsonObject json = new JsonObject();
            json.addProperty("enabled", enabled);
            json.addProperty("url", url);
            json.addProperty("cooldownSeconds", cooldownSeconds);
            json.addProperty("timeoutSeconds", timeoutSeconds);
            json.addProperty("autostart", autostart);
            json.addProperty("python", python);
            json.addProperty("script", script);
            Files.createDirectories(FILE.getParent());
            Files.writeString(FILE, GSON.toJson(json), StandardCharsets.UTF_8);
        } catch (IOException | RuntimeException e) {
            SkyRpgHelper.LOGGER.warn("Couldn't read {}, using defaults: {}", FILE, e.toString());
        }
    }
}
