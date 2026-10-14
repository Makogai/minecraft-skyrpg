package com.skyrpg.stats;

import com.skyrpg.core.stats.Stats;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Adds up every stat source (armor, skills, enchants...) per player and makes the stats do things:
 * health, speed, mining speed, melee damage and crits, damage taken, fortune drops and mana.
 * Other mods only talk to {@link Stats} in core; this mod installs its backend.
 */
public class SkyRpgStats implements ModInitializer {
    public static final String MOD_ID = "skyrpg_stats";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        PlayerStats.init();
        Stats.install(new StatsBackend());

        ServerTickEvents.END_SERVER_TICK.register(StatEngine::tick);
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> StatEngine.recompute(handler.player));
        ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> {
            // A fresh player entity has no stat modifiers yet: apply them, then fill health and mana.
            StatEngine.recompute(newPlayer);
            newPlayer.setHealth(newPlayer.getMaxHealth());
            PlayerStats.setMana(newPlayer, PlayerStats.maxMana(newPlayer));
        });
        PlayerBlockBreakEvents.AFTER.register(Fortune::onBlockBroken);
        CommandRegistrationCallback.EVENT.register((dispatcher, context, selection) -> StatsCommand.register(dispatcher));
    }

    public static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(MOD_ID, path);
    }
}
