package com.skyrpg.fishing;

import com.skyrpg.core.data.JsonDataLoader;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The Fishing skill: Fishing Speed shortens the wait, catches pay Fishing XP and Tidescales (the Fishing Master's
 * currency) with rare finds, some bites are sea creatures that bite back, and baits give ten-minute boosts.
 */
public class SkyRpgFishing implements ModInitializer {
    public static final String MOD_ID = "skyrpg_fishing";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        FishingItems.init();
        SeaCreatures.init();
        JsonDataLoader.register(id("sea_creatures"), SeaCreatures.DIRECTORY, SeaCreatures::load);
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (server.getTickCount() % 20 == 0) {
                server.getPlayerList().getPlayers().forEach(SkyRpgFishing::tick);
            }
        });
    }

    /** Every second: Tideborn (breathe and swim easily underwater). */
    private static void tick(ServerPlayer player) {
        if (FishingEffects.value(player, FishingEffects.TIDEBORN) > 0 && player.isInWater()) {
            player.addEffect(new MobEffectInstance(MobEffects.WATER_BREATHING, 60, 0, true, false, true));
            player.addEffect(new MobEffectInstance(MobEffects.DOLPHINS_GRACE, 60, 0, true, false, true));
        }
    }

    public static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(MOD_ID, path);
    }
}
