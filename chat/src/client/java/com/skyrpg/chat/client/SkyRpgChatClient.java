package com.skyrpg.chat.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents;
import net.minecraft.client.gui.screens.ChatScreen;

/**
 * SkyRPG Chat on the client: the themed chat box (ChatComponent and ChatScreen mixins), kind stripes, timestamps,
 * compact repeats, mentions and pings, and tabs on the open chat. All of it is in Settings > Chat.
 */
public class SkyRpgChatClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        ChatSettings.register();
        ImageNet.init();
        ImageCommands.init();
        ImageCache.trimDisk();
        ClientReceiveMessageEvents.ALLOW_GAME.register(Mentions::allow);
        ClientReceiveMessageEvents.MODIFY_GAME.register(Mentions::modify);
        ScreenEvents.AFTER_INIT.register((minecraft, screen, width, height) -> {
            if (!(screen instanceof ChatScreen)) {
                return;
            }
            ScreenMouseEvents.allowMouseClick(screen).register((current, event) ->
                    event.button() != InputConstants.MOUSE_BUTTON_LEFT
                            || !(ChatLook.clickTabs(event.x(), event.y()) || ChatImageRender.click(event.x(), event.y())));
            ScreenEvents.remove(screen).register(current -> ChatTabs.reset());
        });
    }
}
