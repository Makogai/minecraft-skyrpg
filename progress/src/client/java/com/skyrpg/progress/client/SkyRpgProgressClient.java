package com.skyrpg.progress.client;

import net.fabricmc.api.ClientModInitializer;

/** The Journal on the client: the Collections and Journal pages, the quest tracker. */
public class SkyRpgProgressClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        CollectionsClient.init();
        QuestsClient.init();
    }
}
