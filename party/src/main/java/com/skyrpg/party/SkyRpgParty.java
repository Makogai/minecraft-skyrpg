package com.skyrpg.party;

import net.fabricmc.api.ModInitializer;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Parties: up to 8 players, saved in the world so they survive logouts and restarts. Party chat, a member overlay, a K menu
 * page and no friendly fire. See {@link Parties} and docs/design/party.md.
 */
public class SkyRpgParty implements ModInitializer {
    public static final String MOD_ID = "skyrpg_party";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        PartyPayloads.register();
        Parties.init();
        PartyCommands.init();
    }

    public static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(MOD_ID, path);
    }
}
