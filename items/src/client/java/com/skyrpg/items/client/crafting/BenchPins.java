package com.skyrpg.items.client.crafting;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.skyrpg.items.SkyRpgItems;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Set;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.resources.Identifier;

/** Artisan Bench recipes you pinned, kept on your computer in {@code config/skyrpg/bench_pins.json}. */
final class BenchPins {
    private static final Gson GSON = new Gson();
    private static Set<Identifier> pins;

    private BenchPins() {
    }

    static boolean pinned(Identifier id) {
        return pins().contains(id);
    }

    static boolean any() {
        return !pins().isEmpty();
    }

    static void toggle(Identifier id) {
        if (!pins().remove(id)) {
            pins.add(id);
        }
        save();
    }

    private static Set<Identifier> pins() {
        if (pins == null) {
            pins = new LinkedHashSet<>();
            try {
                if (Files.isRegularFile(file())) {
                    for (JsonElement element : GSON.fromJson(Files.readString(file()), JsonArray.class)) {
                        Identifier id = Identifier.tryParse(element.getAsString());
                        if (id != null) {
                            pins.add(id);
                        }
                    }
                }
            } catch (IOException | RuntimeException e) {
                SkyRpgItems.LOGGER.warn("Couldn't read bench pins", e);
            }
        }
        return pins;
    }

    private static void save() {
        JsonArray array = new JsonArray();
        pins.forEach(id -> array.add(id.toString()));
        try {
            Files.createDirectories(file().getParent());
            Files.writeString(file(), GSON.toJson(array));
        } catch (IOException e) {
            SkyRpgItems.LOGGER.warn("Couldn't save bench pins", e);
        }
    }

    private static Path file() {
        return FabricLoader.getInstance().getConfigDir().resolve("skyrpg").resolve("bench_pins.json");
    }
}
