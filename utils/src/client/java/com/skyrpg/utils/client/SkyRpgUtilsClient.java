package com.skyrpg.utils.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.skyrpg.core.client.SkyRpgCoreClient;
import com.skyrpg.core.client.hud.HudManager;
import com.skyrpg.utils.client.info.InfoWidget;
import com.skyrpg.utils.client.info.LookInfoWidget;
import com.skyrpg.utils.client.info.LookTarget;
import com.skyrpg.utils.client.map.CaveView;
import com.skyrpg.utils.client.map.MapCache;
import com.skyrpg.utils.client.map.MapScanner;
import com.skyrpg.utils.client.map.MinimapWidget;
import com.skyrpg.utils.client.map.Waypoints;
import com.skyrpg.utils.client.map.WorldMapScreen;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientChunkEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLevelEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * SkyRPG's player utilities, all client-side: the minimap and world map, look-at info and the info box.
 * Each is a HUD widget (move, resize and hide them in the HUD editor) with switches on the Settings page.
 */
public final class SkyRpgUtilsClient implements ClientModInitializer {
    public static final String MOD_ID = "skyrpg_utils";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    public static final KeyMapping MAP_KEY = KeyMappingHelper.registerKeyMapping(
            new KeyMapping("key.skyrpg_utils.map", InputConstants.Type.KEYBOARD, InputConstants.KEY_M, SkyRpgCoreClient.CATEGORY));
    public static final KeyMapping ZOOM_IN_KEY = KeyMappingHelper.registerKeyMapping(
            new KeyMapping("key.skyrpg_utils.zoom_in", InputConstants.Type.KEYBOARD, InputConstants.KEY_EQUALS, SkyRpgCoreClient.CATEGORY));
    public static final KeyMapping ZOOM_OUT_KEY = KeyMappingHelper.registerKeyMapping(
            new KeyMapping("key.skyrpg_utils.zoom_out", InputConstants.Type.KEYBOARD, InputConstants.KEY_MINUS, SkyRpgCoreClient.CATEGORY));

    public static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(MOD_ID, path);
    }

    @Override
    public void onInitializeClient() {
        UtilsSettings.init();
        Waypoints.init();
        HudManager.register(new MinimapWidget());
        HudManager.register(new com.skyrpg.utils.client.map.WaypointRibbon());
        HudManager.register(new InfoWidget());
        HudManager.register(new LookInfoWidget());

        ClientChunkEvents.CHUNK_LOAD.register((level, chunk) -> MapScanner.queue(chunk.getPos()));
        ClientLevelEvents.AFTER_CLIENT_LEVEL_CHANGE.register((minecraft, level) -> switchTo(minecraft, level));
        ClientPlayConnectionEvents.DISCONNECT.register((handler, minecraft) -> minecraft.execute(SkyRpgUtilsClient::leave));
        ClientTickEvents.END_CLIENT_TICK.register(SkyRpgUtilsClient::tick);
    }

    private static void switchTo(Minecraft minecraft, ClientLevel level) {
        MapScanner.clear();
        CaveView.reset();
        if (level != null) {
            MapCache.use(MapCache.worldKey(minecraft), level.dimension());
        }
    }

    private static void leave() {
        MapScanner.clear();
        CaveView.reset();
        MapCache.clear();
    }

    private static void tick(Minecraft minecraft) {
        if (minecraft.level != null && !MapCache.active()) {
            switchTo(minecraft, minecraft.level);
        }
        while (MAP_KEY.consumeClick()) {
            if (minecraft.player != null && minecraft.gui.screen() == null) {
                minecraft.gui.setScreen(new WorldMapScreen());
            }
        }
        while (ZOOM_IN_KEY.consumeClick()) {
            MinimapWidget.zoom(1);
        }
        while (ZOOM_OUT_KEY.consumeClick()) {
            MinimapWidget.zoom(-1);
        }
        MapScanner.tick(minecraft);
        CaveView.tick(minecraft, UtilsSettings.minimap().get() && UtilsSettings.caves().get());
        LookTarget.tick(minecraft);
        LookInfoWidget.tick();
    }
}
