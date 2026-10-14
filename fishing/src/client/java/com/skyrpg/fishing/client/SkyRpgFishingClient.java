package com.skyrpg.fishing.client;

import com.skyrpg.core.client.hud.HudManager;
import com.skyrpg.fishing.FishingPayloads;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

/** The fishing HUD box: the bait in use and how many are left, and the hotspot the bobber is in. */
public class SkyRpgFishingClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        HudManager.register(new BaitWidget());
        ClientPlayNetworking.registerGlobalReceiver(FishingPayloads.Status.TYPE, (payload, context) -> BaitWidget.hotspot = payload.hotspot());
    }
}
