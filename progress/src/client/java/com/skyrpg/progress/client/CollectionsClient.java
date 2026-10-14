package com.skyrpg.progress.client;

import com.skyrpg.core.client.menu.ProfilePage;
import com.skyrpg.core.client.menu.ProfileStat;
import com.skyrpg.core.client.menu.SkyRpgMenu;
import com.skyrpg.core.client.ui.Theme;
import com.skyrpg.core.stats.StatTypes;
import com.skyrpg.progress.collections.ClientCollections;
import com.skyrpg.progress.collections.CollectionPayloads;
import com.skyrpg.progress.collections.SkyRpgLevel;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

/** Collections page, SkyRPG Level on the Profile page, and the synced collection data on the client. */
public final class CollectionsClient {
    private CollectionsClient() {
    }

    public static void init() {
        ClientPlayNetworking.registerGlobalReceiver(CollectionPayloads.Defs.TYPE,
                (payload, context) -> context.client().execute(() -> ClientCollections.setDefs(payload.views())));
        ClientPlayNetworking.registerGlobalReceiver(CollectionPayloads.Counts.TYPE,
                (payload, context) -> context.client().execute(() -> ClientCollections.setCounts(payload.counts())));
        SkyRpgMenu.registerPage(new CollectionsPage());
        ProfilePage.setHeader(new LevelHeader());
        SkyRpgMenu.registerProfileStat(new ProfileStat("SkyRPG Level", StatTypes.LEVEL_ICON, Theme.AQUA, -1,
                player -> String.valueOf(SkyRpgLevel.level(player))));
    }
}
