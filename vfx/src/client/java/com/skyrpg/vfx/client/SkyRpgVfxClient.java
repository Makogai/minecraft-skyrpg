package com.skyrpg.vfx.client;

import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.skyrpg.vfx.client.particle.VfxParticleProviders;
import com.skyrpg.vfx.data.VfxLibrary;
import com.skyrpg.vfx.net.VfxPayloads;
import java.util.HashMap;
import java.util.Map;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLevelEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;

/**
 * SkyRPG VFX on the client: the particle providers, the effect definitions the server sends, server play requests,
 * the per-tick runtime (running effects and bindings) and the Effects quality setting.
 */
public class SkyRpgVfxClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        VfxParticleProviders.register();
        VfxQuality.register();

        ClientPlayNetworking.registerGlobalReceiver(VfxPayloads.Sync.TYPE, (payload, context) -> {
            VfxLibrary library = VfxLibrary.build(parse(payload.effects()), parse(payload.bindings()));
            library.log("client");
            VfxRuntime.setLibrary(library);
        });
        ClientPlayNetworking.registerGlobalReceiver(VfxPayloads.Play.TYPE, (payload, context) -> {
            Minecraft minecraft = Minecraft.getInstance();
            Entity anchor = payload.entityId() >= 0 && minecraft.level != null ? minecraft.level.getEntity(payload.entityId()) : null;
            if (anchor != null) {
                VfxClient.play(payload.effect(), anchor);
            } else {
                VfxClient.play(payload.effect(), payload.x(), payload.y(), payload.z());
            }
        });

        ClientPlayNetworking.registerGlobalReceiver(VfxPayloads.Shake.TYPE, (payload, context) -> ScreenFx.shake(payload.strength(), payload.ticks()));
        ClientPlayNetworking.registerGlobalReceiver(VfxPayloads.Flash.TYPE, (payload, context) -> ScreenFx.flash(payload.argb(), payload.ticks()));
        ScreenFx.init();

        ClientTickEvents.END_CLIENT_TICK.register(minecraft -> {
            ScreenFx.tick();
            VfxRuntime.tick(minecraft);
        });
        ClientLevelEvents.AFTER_CLIENT_LEVEL_CHANGE.register((minecraft, level) -> {
            VfxRuntime.clear();
            ScreenFx.clear();
        });
        ClientPlayConnectionEvents.DISCONNECT.register((handler, minecraft) -> {
            VfxRuntime.reset();
            ScreenFx.clear();
        });
    }

    private static Map<Identifier, JsonElement> parse(Map<String, String> files) {
        Map<Identifier, JsonElement> parsed = new HashMap<>();
        files.forEach((id, json) -> {
            Identifier key = Identifier.tryParse(id);
            if (key == null) {
                return;
            }
            try {
                parsed.put(key, JsonParser.parseString(json));
            } catch (JsonParseException e) {
                // The server parsed it fine, so this can't really happen; skip it if it does.
            }
        });
        return parsed;
    }
}
