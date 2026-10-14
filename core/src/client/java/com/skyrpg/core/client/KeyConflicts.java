package com.skyrpg.core.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.skyrpg.core.SkyRpgCore;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import net.fabricmc.api.EnvType;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;

/**
 * Untangles default keys that clash once the pack's mods are installed. Each fix runs once per install (its own marker
 * file), so the player's own choice afterwards (even the clashing key again) is left alone. Needs no dependency on those
 * mods: a key is found by its translation key.
 * <ul>
 * <li>v1: Iris binds "toggle shaders" to K, the SkyRPG menu key; it moves to U.</li>
 * <li>v2: Zoomify zooms on C, which vanilla also uses for "save hotbar activator" (hardly anyone uses it); that one is
 * unbound, so zooming never saves a hotbar and the key shows no conflict.</li>
 * </ul>
 */
final class KeyConflicts {
    private static final String IRIS_MARKER = "key-conflicts-v1";
    private static final String ZOOM_MARKER = "key-conflicts-v2";

    private KeyConflicts() {
    }

    static void init() {
        if (FabricLoader.getInstance().getEnvironmentType() != EnvType.CLIENT) {
            return;
        }
        ClientLifecycleEvents.CLIENT_STARTED.register(KeyConflicts::fix);
    }

    private static void fix(Minecraft minecraft) {
        fixIris(minecraft);
        fixZoom(minecraft);
    }

    private static void fixIris(Minecraft minecraft) {
        Path marker = marker(IRIS_MARKER);
        if (Files.exists(marker)) {
            return;
        }
        boolean changed = false;
        for (KeyMapping mapping : minecraft.options.keyMappings) {
            if (mapping.getName().equals("iris.keybind.toggleShaders") && mapping.saveString().equals(SkyRpgCoreClient.MENU_KEY.saveString())) {
                mapping.setKey(InputConstants.Type.KEYBOARD.getOrCreate(InputConstants.KEY_U));
                changed = true;
            }
        }
        if (changed) {
            KeyMapping.resetMapping();
            minecraft.options.save();
        }
        writeMarker(marker, "Iris's toggle-shaders key was moved off K once (SkyRPG menu).");
    }

    private static void fixZoom(Minecraft minecraft) {
        Path marker = marker(ZOOM_MARKER);
        if (Files.exists(marker) || !FabricLoader.getInstance().isModLoaded("zoomify")) {
            return;
        }
        KeyMapping zoom = null;
        for (KeyMapping mapping : minecraft.options.keyMappings) {
            if (mapping.getName().equals("zoomify.key.zoom")) {
                zoom = mapping;
            }
        }
        KeyMapping save = minecraft.options.keySaveHotbarActivator;
        if (zoom != null && !zoom.isUnbound() && zoom.saveString().equals(save.saveString())) {
            save.setKey(InputConstants.UNKNOWN);
            KeyMapping.resetMapping();
            minecraft.options.save();
        }
        writeMarker(marker, "Vanilla's save-hotbar key was unbound once, since Zoomify zooms on the same key.");
    }

    private static Path marker(String name) {
        return FabricLoader.getInstance().getConfigDir().resolve("skyrpg").resolve(name);
    }

    private static void writeMarker(Path marker, String text) {
        try {
            Files.createDirectories(marker.getParent());
            Files.writeString(marker, text + System.lineSeparator());
        } catch (IOException e) {
            SkyRpgCore.LOGGER.debug("Couldn't write {}", marker, e);
        }
    }
}
