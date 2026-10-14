package com.skyrpg.chat.client;

import com.skyrpg.chat.TitleSync;
import com.skyrpg.core.client.tab.SkyRpgTab;
import com.skyrpg.core.text.PlayerTitles;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

/** Every online player's shown title as the server sends it (see TitleSync): the pill after each name in the tab list. */
final class ChatTitles {
    private static final Map<UUID, TitleSync.Entry> TITLES = new HashMap<>();

    private ChatTitles() {
    }

    static void init() {
        ClientPlayNetworking.registerGlobalReceiver(TitleSync.Shown.TYPE, (payload, context) -> {
            if (payload.full()) {
                TITLES.clear();
            }
            payload.titles().forEach((id, entry) -> {
                if (entry.text().isEmpty()) {
                    TITLES.remove(id);
                } else {
                    TITLES.put(id, entry);
                }
            });
        });
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> client.execute(TITLES::clear));
        SkyRpgTab.registerTitle(id -> {
            TitleSync.Entry entry = TITLES.get(id);
            return entry == null ? null
                    : new SkyRpgTab.TabTitle(entry.text(), entry.icon().isEmpty() ? null : PlayerTitles.iconComponent(entry.icon(), entry.color()), entry.color());
        });
    }
}
