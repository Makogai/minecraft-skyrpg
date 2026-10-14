package com.skyrpg.core.action;

import com.google.gson.JsonObject;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.server.level.ServerPlayer;

/**
 * Named things a click can do ("open_shop", "open_bank", "dialog"...). Mods register handlers and other
 * mods trigger them by name, so for example NPCs can open economy shops without the npcs and economy
 * mods depending on each other. Parameters come from data files, e.g. {@code {"shop": "skyrpg_economy:farmer"}}.
 */
public final class Actions {
    @FunctionalInterface
    public interface Handler {
        void run(ServerPlayer player, JsonObject params);
    }

    private static final Map<String, Handler> HANDLERS = new ConcurrentHashMap<>();

    private Actions() {
    }

    public static void register(String type, Handler handler) {
        HANDLERS.put(type, handler);
    }

    /** Runs the action. Returns false if no mod handles that type (e.g. its mod isn't installed). */
    public static boolean run(String type, ServerPlayer player, JsonObject params) {
        Handler handler = HANDLERS.get(type);
        if (handler == null) {
            return false;
        }
        handler.run(player, params == null ? new JsonObject() : params);
        return true;
    }

    public static Set<String> types() {
        return HANDLERS.keySet();
    }
}
