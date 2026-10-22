package com.skyrpg.core;

import com.skyrpg.core.world.PlacedBlocks;
import net.fabricmc.api.ModInitializer;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Shared foundation for every SkyRPG mod. Holds no gameplay of its own; other modules build on
 * {@link com.skyrpg.core.data.JsonDataLoader} and {@link com.skyrpg.core.text.Msg}.
 */
public class SkyRpgCore implements ModInitializer {
    public static final String MOD_ID = "skyrpg_core";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        PlacedBlocks.init();
        com.skyrpg.core.combat.CombatTag.init();
        com.skyrpg.core.net.WaypointTarget.register();
        com.skyrpg.core.net.Ping.register();
    }

    public static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(MOD_ID, path);
    }
}
