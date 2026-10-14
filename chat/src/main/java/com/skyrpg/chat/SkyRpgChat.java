package com.skyrpg.chat;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A themed chat: level badges, hover cards, item showcase, mentions, emotes, styled whispers and system lines, and a
 * styled chat box on the client. The server side works through system messages, so it needs nothing on the client to
 * look right; the client side adds the chat box look, stripes, timestamps, compact repeats, mentions and tabs.
 */
public class SkyRpgChat implements ModInitializer {
    public static final String MOD_ID = "skyrpg_chat";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        Titles.init();
        Profiles.init();
        Emotes.init();
        ServerChat.init();
        ChatImages.init();
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> SystemLines.onJoin(handler, server));
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            Whispers.forget(handler.player);
            ChatImages.forget(handler.player);
        });
        CommandRegistrationCallback.EVENT.register((dispatcher, context, selection) -> ChatCommands.register(dispatcher));
    }

    public static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(MOD_ID, path);
    }
}
