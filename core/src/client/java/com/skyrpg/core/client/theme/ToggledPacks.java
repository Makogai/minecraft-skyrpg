package com.skyrpg.core.client.theme;

import com.skyrpg.core.SkyRpgCore;
import com.skyrpg.core.client.config.Setting;
import com.skyrpg.core.client.config.SkyRpgSettings;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.resource.v1.ResourceLoader;
import net.fabricmc.fabric.api.resource.v1.pack.PackActivationType;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.repository.PackRepository;

/**
 * Built-in resource packs (under core's {@code resourcepacks/}) that a SkyRPG setting switches on and off:
 * the theme and the classic stat symbols.
 *
 * <p>The setting is the source of truth. At startup, {@code OptionsMixin} adds or removes each pack in
 * options.txt's list just before Minecraft applies it, so no second resource reload is needed. Flipping a
 * toggle later changes the selection and reloads resources once.
 */
public final class ToggledPacks {
    private static final Map<String, Setting> PACKS = new LinkedHashMap<>();

    private ToggledPacks() {
    }

    /** Registers the pack {@code resourcepacks/<path>} and the Settings toggle that controls it. */
    public static Setting register(Identifier id, String name, String settingId, String category, String label,
                                   String description, boolean defaultValue) {
        ModContainer core = FabricLoader.getInstance().getModContainer(SkyRpgCore.MOD_ID).orElseThrow();
        ResourceLoader.registerBuiltinPack(id, core, Component.literal(name), PackActivationType.NORMAL);
        String packId = id.toString();
        Setting setting = SkyRpgSettings.toggle(settingId, category, label,
                description, defaultValue, enabled -> apply(packId, enabled));
        PACKS.put(packId, setting);
        return setting;
    }

    /** Runs just before options.txt's pack list is applied at startup: adds each pack whose setting is on, removes the rest. */
    public static void beforePacksLoaded(List<String> selected, PackRepository repository) {
        PACKS.forEach((packId, setting) -> {
            if (setting.get()) {
                if (repository.isAvailable(packId)) {
                    insert(selected, packId);
                }
            } else {
                selected.remove(packId);
            }
        });
    }

    private static void apply(String packId, boolean enabled) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.options == null) {
            return; // the startup sync above handles this case
        }
        PackRepository repository = minecraft.getResourcePackRepository();
        List<String> selected = new ArrayList<>(repository.getSelectedIds());
        boolean changed = enabled ? repository.isAvailable(packId) && insert(selected, packId) : selected.remove(packId);
        if (!changed) {
            return;
        }
        repository.setSelected(selected);
        minecraft.options.updateResourcePacks(repository);
        minecraft.reloadResourcePacks();
    }

    /**
     * Adds the pack above Minecraft's and the mods' built-in packs but below the player's own pack files,
     * so a resource pack the player picked still wins. Returns false if it was already there.
     */
    private static boolean insert(List<String> selected, String packId) {
        if (selected.contains(packId)) {
            return false;
        }
        int index = 0;
        for (int i = 0; i < selected.size(); i++) {
            if (!selected.get(i).startsWith("file/")) {
                index = i + 1;
            }
        }
        selected.add(index, packId);
        return true;
    }
}
