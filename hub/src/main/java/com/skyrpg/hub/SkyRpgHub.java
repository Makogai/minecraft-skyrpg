package com.skyrpg.hub;

import com.skyrpg.core.data.JsonDataLoader;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** The hub world everyone spawns in, with /hub and /survival to move between it and the survival world. */
public class SkyRpgHub implements ModInitializer {
    public static final String MOD_ID = "skyrpg_hub";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        Hub.init();
        HubBuild.init();
        JsonDataLoader.register(id("hub"), HubConfig.DIRECTORY, HubConfig::load);
        CommandRegistrationCallback.EVENT.register((dispatcher, context, selection) -> HubCommands.register(dispatcher));
        ServerLifecycleEvents.SERVER_STARTED.register(HubBuild::onServerStarted);
        HubProtection.register();
        HubDecor.register();
    }

    public static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(MOD_ID, path);
    }
}
