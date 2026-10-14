package com.skyrpg.core.data;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;
import net.minecraft.server.level.ServerPlayer;

/**
 * Short facts about a player that other mods want to share with helpers such as SkyHelper ("quest": "Gone Fishing:
 * catch 10 fish"). A mod registers a fact once at start-up; {@link #collect} reads them all on the server thread.
 * A fact returns null or an empty string when it has nothing to say.
 */
public final class PlayerFacts {
    private static final Map<String, Function<ServerPlayer, Object>> FACTS = new LinkedHashMap<>();

    private PlayerFacts() {
    }

    public static void register(String key, Function<ServerPlayer, Object> fact) {
        FACTS.put(key, fact);
    }

    public static Map<String, Object> collect(ServerPlayer player) {
        Map<String, Object> out = new LinkedHashMap<>();
        FACTS.forEach((key, fact) -> {
            try {
                Object value = fact.apply(player);
                if (value != null && !(value instanceof String s && s.isEmpty())) {
                    out.put(key, value);
                }
            } catch (RuntimeException e) {
                // one broken fact never stops the others
            }
        });
        return out;
    }
}
