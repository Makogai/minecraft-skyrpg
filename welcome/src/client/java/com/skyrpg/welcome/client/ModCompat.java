package com.skyrpg.welcome.client;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import java.util.stream.Stream;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.InactivityFpsLimit;
import net.minecraft.client.Minecraft;

/**
 * Optional integrations. Nothing here is a hard dependency: every mod is looked up with
 * {@link FabricLoader#isModLoaded} and driven by reflection, so a missing mod (or an API that changed) only means that
 * the extra step is skipped, never a crash. Vanilla options always work.
 */
final class ModCompat {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private ModCompat() {
    }

    static boolean loaded(String id) {
        return FabricLoader.getInstance().isModLoaded(id);
    }

    // ------------------------------------------------------------------ performance

    /** The extras of a performance preset: the frame rate when idle, Dynamic FPS, Sodium's quality options. */
    static void applyPerformance(Preset preset) {
        Minecraft minecraft = Minecraft.getInstance();
        minecraft.options.inactivityFpsLimit().set(preset.saveWhenIdle ? InactivityFpsLimit.AFK : InactivityFpsLimit.MINIMIZED);
        if (loaded("dynamic_fps")) {
            // Dynamic FPS keeps its settings in config/dynamic_fps.json; only switch it on, leave its own tuning alone.
            editJson(FabricLoader.getInstance().getConfigDir().resolve("dynamic_fps.json"), json -> json.addProperty("enabled", true));
        }
        if (loaded("lambdynlights")) {
            // Dynamic lights: off on Potato, fast on Balanced, fancy above. Its config is a small TOML file with a mode line.
            setTomlString(FabricLoader.getInstance().getConfigDir().resolve("lambdynlights.toml"), "mode",
                    preset == Preset.POTATO ? "off" : preset == Preset.BALANCED ? "fast" : "fancy");
        }
        if (loaded("sodium")) {
            sodiumQuality(preset);
        }
    }

    /** Sodium reads its options at start-up; change the in-memory values where they are reachable, and the file too. */
    private static void sodiumQuality(Preset preset) {
        boolean cheap = preset == Preset.POTATO;
        try {
            Class<?> client = Class.forName("net.caffeinemc.mods.sodium.client.SodiumClientMod");
            Object options = client.getMethod("options").invoke(null);
            Object quality = field(options, "quality");
            if (quality != null) {
                setEnum(quality, "weatherQuality", cheap ? "FAST" : "DEFAULT");
                setEnum(quality, "leavesQuality", cheap ? "FAST" : "DEFAULT");
                setBoolean(quality, "enableVignette", !cheap);
            }
            Class<?> gameOptions = options.getClass();
            for (Method method : gameOptions.getMethods()) {
                if (method.getName().equals("writeToDisk") && method.getParameterCount() == 1) {
                    method.invoke(null, options);
                    break;
                }
            }
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            SkyRpgWelcomeClient.LOGGER.info("Couldn't change Sodium's options ({}); the vanilla options still apply.", e.toString());
        }
    }

    private static Object field(Object owner, String name) throws ReflectiveOperationException {
        Field field = owner.getClass().getField(name);
        return field.get(owner);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void setEnum(Object owner, String name, String constant) {
        try {
            Field field = owner.getClass().getField(name);
            Class<?> type = field.getType();
            if (type.isEnum()) {
                field.set(owner, Enum.valueOf((Class<Enum>) type, constant));
            }
        } catch (ReflectiveOperationException | IllegalArgumentException e) {
            // option not there in this Sodium version
        }
    }

    private static void setBoolean(Object owner, String name, boolean value) {
        try {
            owner.getClass().getField(name).setBoolean(owner, value);
        } catch (ReflectiveOperationException | IllegalArgumentException e) {
            // option not there in this Sodium version
        }
    }

    private static void setTomlString(Path file, String key, String value) {
        if (!Files.exists(file)) {
            return;
        }
        try {
            List<String> lines = Files.readAllLines(file);
            boolean changed = false;
            for (int i = 0; i < lines.size(); i++) {
                String line = lines.get(i);
                if (line.trim().startsWith(key) && line.contains("=") && line.substring(0, line.indexOf('=')).trim().equals(key)) {
                    lines.set(i, line.substring(0, line.indexOf('=') + 1) + " \"" + value + "\"");
                    changed = true;
                }
            }
            if (changed) {
                Files.write(file, lines);
            }
        } catch (IOException e) {
            SkyRpgWelcomeClient.LOGGER.info("Couldn't edit {}: {}", file.getFileName(), e.toString());
        }
    }

    private static void editJson(Path file, java.util.function.Consumer<JsonObject> edit) {
        if (!Files.exists(file)) {
            return;
        }
        try {
            JsonElement parsed = JsonParser.parseString(Files.readString(file));
            if (parsed.isJsonObject()) {
                edit.accept(parsed.getAsJsonObject());
                Files.writeString(file, GSON.toJson(parsed));
            }
        } catch (IOException | RuntimeException e) {
            SkyRpgWelcomeClient.LOGGER.info("Couldn't edit {}: {}", file.getFileName(), e.toString());
        }
    }

    // ------------------------------------------------------------------ shaders (Iris)

    static boolean irisLoaded() {
        return loaded("iris");
    }

    private static Path shaderFolder() {
        return Minecraft.getInstance().gameDirectory.toPath().resolve("shaderpacks");
    }

    /** Shader packs in the shaderpacks folder (zips and unpacked folders), alphabetical. */
    static List<String> shaderPacks() {
        List<String> packs = new ArrayList<>();
        Path folder = shaderFolder();
        if (!Files.isDirectory(folder)) {
            return packs;
        }
        try (Stream<Path> files = Files.list(folder)) {
            files.forEach(path -> {
                String name = path.getFileName().toString();
                if (Files.isDirectory(path) ? Files.isDirectory(path.resolve("shaders")) : name.toLowerCase(Locale.ROOT).endsWith(".zip")) {
                    packs.add(name);
                }
            });
        } catch (IOException e) {
            // no list
        }
        packs.sort(Comparator.comparing(name -> name.toLowerCase(Locale.ROOT)));
        return packs;
    }

    static String prettyShaderName(String file) {
        String name = file.endsWith(".zip") ? file.substring(0, file.length() - 4) : file;
        return name.replace('_', ' ');
    }

    private static Properties irisProperties() {
        Properties props = new Properties();
        Path file = FabricLoader.getInstance().getConfigDir().resolve("iris.properties");
        if (Files.exists(file)) {
            try (var in = Files.newInputStream(file)) {
                props.load(in);
            } catch (IOException e) {
                // empty
            }
        }
        return props;
    }

    static boolean shadersEnabled() {
        try {
            Object api = Class.forName("net.irisshaders.iris.api.v0.IrisApi").getMethod("getInstance").invoke(null);
            Object config = api.getClass().getMethod("getConfig").invoke(api);
            return (boolean) config.getClass().getMethod("areShadersEnabled").invoke(config);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            return Boolean.parseBoolean(irisProperties().getProperty("enableShaders", "false"));
        }
    }

    /** The selected shader pack's file name, or "" for none. */
    static String shaderPack() {
        return irisProperties().getProperty("shaderPack", "");
    }

    /**
     * Picks a shader pack (or none) and switches shaders on or off, live through Iris. Returns false if Iris could
     * not be driven and only its config file was edited (the change then needs a restart).
     */
    static boolean setShaders(boolean enabled, String pack) {
        try {
            Class<?> iris = Class.forName("net.irisshaders.iris.Iris");
            if (pack != null && !pack.isEmpty()) {
                Object config = iris.getMethod("getIrisConfig").invoke(null);
                config.getClass().getMethod("setShaderPackName", String.class).invoke(config, pack);
            }
            Object api = Class.forName("net.irisshaders.iris.api.v0.IrisApi").getMethod("getInstance").invoke(null);
            Object config = api.getClass().getMethod("getConfig").invoke(api);
            config.getClass().getMethod("setShadersEnabledAndApply", boolean.class).invoke(config, enabled);
            return true;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            SkyRpgWelcomeClient.LOGGER.info("Couldn't drive Iris ({}); writing iris.properties instead.", e.toString());
            Properties props = irisProperties();
            props.setProperty("enableShaders", Boolean.toString(enabled));
            if (pack != null && !pack.isEmpty()) {
                props.setProperty("shaderPack", pack);
            }
            try (var out = Files.newOutputStream(FabricLoader.getInstance().getConfigDir().resolve("iris.properties"))) {
                props.store(out, "Iris Configuration (edited by the SkyRPG setup)");
            } catch (IOException ioe) {
                // nothing more to try
            }
            return false;
        }
    }
}
