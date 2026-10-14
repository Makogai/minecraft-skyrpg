package com.skyrpg.chat.client;

import com.skyrpg.chat.LevelSync;
import com.skyrpg.chat.Profiles;
import com.skyrpg.core.client.tab.SkyRpgTab;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

/** Every online player's SkyRPG Level as the server sends it (see LevelSync): the badge before each name in the tab list. */
final class ChatLevels {
    private static final Map<UUID, Integer> LEVELS = new HashMap<>();

    private ChatLevels() {
    }

    static void init() {
        ClientPlayNetworking.registerGlobalReceiver(LevelSync.Levels.TYPE, (payload, context) -> {
            if (payload.full()) {
                LEVELS.clear();
            }
            payload.levels().forEach((id, level) -> {
                if (level < 0) {
                    LEVELS.remove(id);
                } else {
                    LEVELS.put(id, level);
                }
            });
        });
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> client.execute(LEVELS::clear));
        // the same "(* 42)" badge as in chat, in the level's tier colour
        SkyRpgTab.registerNamePrefix(id -> {
            Integer level = LEVELS.get(id);
            return level == null ? null : Profiles.badge(level);
        });
    }
}
