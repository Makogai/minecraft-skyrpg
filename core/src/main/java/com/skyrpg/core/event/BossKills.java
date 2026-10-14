package com.skyrpg.core.event;

import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.EventFactory;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;

/**
 * A player took part in killing a dungeon or world boss (every time, not just the first): the world and combat mods fire
 * it so the progress mod can reward first kills and dungeon clears without those mods depending on it.
 */
public final class BossKills {
    public enum Kind {
        DUNGEON_BOSS, DUNGEON_CHAMPION, WORLD_BOSS, REGION_BOSS
    }

    @FunctionalInterface
    public interface Killed {
        void onKilled(ServerPlayer player, Identifier boss, int level, Kind kind);
    }

    public static final Event<Killed> KILLED = EventFactory.createArrayBacked(Killed.class, listeners -> (player, boss, level, kind) -> {
        for (Killed listener : listeners) {
            listener.onKilled(player, boss, level, kind);
        }
    });

    private BossKills() {
    }
}
