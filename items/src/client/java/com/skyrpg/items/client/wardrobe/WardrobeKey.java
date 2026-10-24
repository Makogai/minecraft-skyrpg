package com.skyrpg.items.client.wardrobe;

import com.mojang.blaze3d.platform.InputConstants;
import com.skyrpg.core.client.SkyRpgCoreClient;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;

/** O opens the wardrobe (it runs {@code /wardrobe}; the server opens the menu). */
public final class WardrobeKey {
    public static final KeyMapping OPEN_KEY = KeyMappingHelper.registerKeyMapping(
            new KeyMapping("key.skyrpg_items.wardrobe", InputConstants.Type.KEYBOARD, InputConstants.KEY_O, SkyRpgCoreClient.CATEGORY));

    private WardrobeKey() {
    }

    public static void init() {
        ClientTickEvents.END_CLIENT_TICK.register(minecraft -> {
            while (OPEN_KEY.consumeClick()) {
                if (minecraft.player != null && minecraft.gui.screen() == null) {
                    minecraft.player.connection.sendCommand("wardrobe");
                }
            }
        });
    }
}
