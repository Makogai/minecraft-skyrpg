package com.skyrpg.builder.client;

import com.skyrpg.builder.BuilderPayloads;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;

/** The Builder on the client: the station menu, the blueprint picker and the holograms in the world. */
public final class SkyRpgBuilderClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        ClientPlayNetworking.registerGlobalReceiver(BuilderPayloads.Menu.TYPE, (payload, context) -> {
            Minecraft minecraft = context.client();
            Screen screen = minecraft.gui.screen();
            if (screen instanceof BuilderScreen builder && builder.station().equals(payload.pos())) {
                builder.update(payload);
            } else if (screen instanceof BlueprintScreen picker && picker.station().equals(payload.pos())) {
                picker.parentUpdate(payload);
            } else if (payload.open()) {
                minecraft.gui.setScreen(new BuilderScreen(payload));
            }
        });
        ClientPlayNetworking.registerGlobalReceiver(BuilderPayloads.LibraryList.TYPE, (payload, context) -> {
            if (context.client().gui.screen() instanceof BlueprintScreen picker) {
                picker.library(payload);
            }
        });
        ClientPlayNetworking.registerGlobalReceiver(BuilderPayloads.UploadResult.TYPE, (payload, context) -> {
            if (context.client().gui.screen() instanceof BlueprintScreen picker) {
                picker.uploaded(payload);
            }
        });
        ClientPlayNetworking.registerGlobalReceiver(BuilderPayloads.Geometry.TYPE, (payload, context) -> Ghosts.geometry(payload.blueprint()));
        ClientPlayNetworking.registerGlobalReceiver(BuilderPayloads.Ghost.TYPE, (payload, context) -> Ghosts.update(payload));
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> client.execute(() -> {
            Ghosts.clear();
            Previews.clear();
        }));
        GhostRenderer.register();
    }
}
