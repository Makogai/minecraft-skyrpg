package com.skyrpg.progress.client;

import com.skyrpg.core.client.menu.SkyRpgMenu;
import com.skyrpg.progress.titles.TitlePayloads;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

/** The Titles page on the K menu and the synced title catalogue. */
public final class TitlesClient {
    private TitlesClient() {
    }

    public static void init() {
        ClientPlayNetworking.registerGlobalReceiver(TitlePayloads.Defs.TYPE,
                (payload, context) -> context.client().execute(() -> TitlesPage.setDefs(payload.json())));
        SkyRpgMenu.registerPage(new TitlesPage());
    }
}
