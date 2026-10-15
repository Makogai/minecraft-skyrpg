package com.skyrpg.crates.client;

import com.skyrpg.crates.CratePayloads;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

/** The crate screen: contents and odds, then the reel. */
public final class SkyRpgCratesClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        ClientPlayNetworking.registerGlobalReceiver(CratePayloads.Menu.TYPE, (payload, context) -> CrateScreen.show(payload));
        ClientPlayNetworking.registerGlobalReceiver(CratePayloads.Roll.TYPE, (payload, context) -> CrateScreen.roll(payload));
    }
}
