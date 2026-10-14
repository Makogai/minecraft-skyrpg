package com.skyrpg.core.items;

import com.google.gson.JsonObject;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.server.level.ServerPlayer;

/**
 * Right-click ability types from any mod: an item definition's {@code "ability": {"type": "skyrpg_mining:echo_sense", ...}}
 * runs the handler registered under that type, with the ability's {@code params}. The {@code skyrpg_items} mod handles
 * the rest (mana, cooldowns, messages); the handler only does the effect and returns whether it worked.
 */
public final class AbilityHandlers {
    @FunctionalInterface
    public interface Handler {
        boolean use(ServerPlayer player, JsonObject params);
    }

    private static final Map<String, Handler> HANDLERS = new ConcurrentHashMap<>();

    private AbilityHandlers() {
    }

    public static void register(String type, Handler handler) {
        HANDLERS.put(type, handler);
    }

    public static Handler get(String type) {
        return HANDLERS.get(type);
    }
}
