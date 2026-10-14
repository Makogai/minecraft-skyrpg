package com.skyrpg.mining;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The Mining skill's world: Glimmer Shards (the currency the Mining Master trades in) and rare finds from
 * mining, placeable ore nodes that regrow, three new ores in the survival world, and blocks you break going
 * straight into your inventory.
 */
public final class SkyRpgMining implements ModInitializer {
    public static final String MOD_ID = "skyrpg_mining";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    public static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(MOD_ID, path);
    }

    @Override
    public void onInitialize() {
        MiningBlocks.init();
        MiningItems.init();
        MiningDrops.init();
        MiningWorldgen.init();
        MiningEffects.init();
        AutoPickup.init();
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (server.getTickCount() % 10 == 0) {
                long tick = server.getTickCount();
                server.getPlayerList().getPlayers().forEach(player -> MiningEffects.tick(player, tick));
            }
        });
    }
}
