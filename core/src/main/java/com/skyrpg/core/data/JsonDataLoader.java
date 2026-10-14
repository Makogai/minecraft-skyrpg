package com.skyrpg.core.data;

import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.skyrpg.core.SkyRpgCore;
import java.io.IOException;
import java.io.Reader;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Consumer;
import net.fabricmc.fabric.api.resource.v1.ResourceLoader;
import net.fabricmc.fabric.api.resource.v1.reloader.SimpleReloadListener;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.PackType;

/**
 * Loads every JSON file in {@code data/<any namespace>/<directory>/} from all enabled datapacks
 * (mod jars count as datapacks) and hands them to a callback on world load and on every /reload.
 *
 * <p>This is how all SkyRPG balance values (prices, XP, levels) stay editable without recompiling:
 * drop a file with the same path into a datapack to override it, or a new file to add to it.
 *
 * <p>File {@code data/foo/skyrpg/skills/farming.json} under directory {@code skyrpg/skills}
 * is delivered with id {@code foo:farming}. Files arrive sorted by id.
 */
public final class JsonDataLoader extends SimpleReloadListener<Map<Identifier, JsonElement>> {
    private final String directory;
    private final Consumer<Map<Identifier, JsonElement>> onLoad;

    private JsonDataLoader(String directory, Consumer<Map<Identifier, JsonElement>> onLoad) {
        this.directory = directory;
        this.onLoad = onLoad;
    }

    public static void register(Identifier listenerId, String directory, Consumer<Map<Identifier, JsonElement>> onLoad) {
        ResourceLoader.get(PackType.SERVER_DATA).registerReloadListener(listenerId, new JsonDataLoader(directory, onLoad));
    }

    @Override
    protected Map<Identifier, JsonElement> prepare(SharedState state) {
        Map<Identifier, JsonElement> files = new TreeMap<>();
        int prefix = directory.length() + 1;
        state.resourceManager().listResources(directory, path -> path.getPath().endsWith(".json")).forEach((file, resource) -> {
            String path = file.getPath();
            Identifier id = Identifier.fromNamespaceAndPath(file.getNamespace(), path.substring(prefix, path.length() - ".json".length()));
            try (Reader reader = resource.openAsReader()) {
                files.put(id, JsonParser.parseReader(reader));
            } catch (IOException | JsonParseException e) {
                SkyRpgCore.LOGGER.error("Skipping unreadable data file {}", file, e);
            }
        });
        return files;
    }

    @Override
    protected void apply(Map<Identifier, JsonElement> files, SharedState state) {
        onLoad.accept(files);
    }
}
