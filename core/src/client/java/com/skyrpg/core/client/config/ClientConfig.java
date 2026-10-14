package com.skyrpg.core.client.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.skyrpg.core.SkyRpgCore;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import net.fabricmc.loader.api.FabricLoader;

/**
 * Client-side SkyRPG preferences in {@code config/skyrpg/client.json}: settings toggles, HUD layout and
 * anything else a mod wants to remember per player. Saved immediately on every change.
 */
public final class ClientConfig {
    private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("skyrpg").resolve("client.json");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static JsonObject root = new JsonObject();

    private ClientConfig() {
    }

    public static void load() {
        if (!Files.exists(FILE)) {
            return;
        }
        try {
            JsonElement parsed = JsonParser.parseString(Files.readString(FILE));
            if (parsed.isJsonObject()) {
                root = parsed.getAsJsonObject();
            }
        } catch (IOException | RuntimeException e) {
            SkyRpgCore.LOGGER.warn("Couldn't read {}, using defaults", FILE, e);
        }
    }

    public static void save() {
        try {
            Files.createDirectories(FILE.getParent());
            Files.writeString(FILE, GSON.toJson(root));
        } catch (IOException e) {
            SkyRpgCore.LOGGER.warn("Couldn't save {}", FILE, e);
        }
    }

    /** A named object in the config (created if missing), e.g. "settings" or "hud". */
    public static JsonObject section(String name) {
        if (!root.has(name) || !root.get(name).isJsonObject()) {
            root.add(name, new JsonObject());
        }
        return root.getAsJsonObject(name);
    }
}
