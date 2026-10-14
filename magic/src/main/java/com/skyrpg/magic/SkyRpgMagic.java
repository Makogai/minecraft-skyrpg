package com.skyrpg.magic;

import com.skyrpg.core.data.JsonDataLoader;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Wands and spells. Seven wands hold spell scrolls in slots; the held wand's selected spell is cast with the mouse (client input,
 * server-authoritative casting through payloads). Spells, wands and their numbers are data (skyrpg/spells, skyrpg/wands); the
 * effects are code ({@link spell.SpellRunner}). See docs/design/magic.md.
 */
public class SkyRpgMagic implements ModInitializer {
    public static final String MOD_ID = "skyrpg_magic";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        MagicItems.init();
        JsonDataLoader.register(id("spells"), Magic.SPELLS_DIRECTORY, Magic::loadSpells);
        JsonDataLoader.register(id("wands"), Magic.WANDS_DIRECTORY, Magic::loadWands);
        MagicPayloads.register();
        Casting.init();
        Tasks.init();
        Statuses.init();
        ManaShield.init();
        MagicLoot.init();
        Seraphine.init();
        CommandRegistrationCallback.EVENT.register((dispatcher, context, selection) -> MagicCommands.register(dispatcher));
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> MagicPayloads.sendDefinitions(handler.player));
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> Casting.forget(handler.player.getUUID()));
        ServerLifecycleEvents.END_DATA_PACK_RELOAD.register((server, resources, success) ->
                server.getPlayerList().getPlayers().forEach(MagicPayloads::sendDefinitions));
    }

    public static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(MOD_ID, path);
    }
}
