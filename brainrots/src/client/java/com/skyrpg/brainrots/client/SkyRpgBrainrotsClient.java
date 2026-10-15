package com.skyrpg.brainrots.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.skyrpg.brainrots.CatchNews;
import com.skyrpg.brainrots.SkyRpgBrainrots;
import com.skyrpg.core.client.SkyRpgCoreClient;
import com.skyrpg.core.client.hud.HudManager;
import com.skyrpg.core.client.menu.SkyRpgMenu;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.minecraft.client.KeyMapping;

/** The brainrots on the client: their models, the album (K menu > Brainrots, or J) and the cards when someone catches one. */
public final class SkyRpgBrainrotsClient implements ClientModInitializer {
    public static final KeyMapping ALBUM_KEY = KeyMappingHelper.registerKeyMapping(
            new KeyMapping("key.skyrpg_brainrots.album", InputConstants.Type.KEYBOARD, InputConstants.KEY_J, SkyRpgCoreClient.CATEGORY));

    @Override
    public void onInitializeClient() {
        BrainrotRenderer.registerLayers();
        EntityRendererRegistry.register(SkyRpgBrainrots.BRAINROT, BrainrotRenderer::new);
        SkyRpgMenu.registerPage(new AlbumPage());
        CatchCard.registerSettings();
        HudManager.register(new CatchCard());
        ClientPlayNetworking.registerGlobalReceiver(CatchNews.TYPE, (payload, context) -> CatchCard.receive(payload));
        ClientTickEvents.END_CLIENT_TICK.register(CatchCard::tick);
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (ALBUM_KEY.consumeClick()) {
                if (client.player != null && client.gui.screen() == null) {
                    SkyRpgMenu.open(AlbumPage.ID);
                }
            }
        });
    }
}
