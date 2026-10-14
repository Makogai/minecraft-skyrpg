package com.skyrpg.skills;

import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.EventFactory;
import net.minecraft.server.level.ServerPlayer;

/** Hooks for other mods, e.g. stat bonuses or coin rewards per skill level. */
public final class SkillEvents {
    public static final Event<XpGained> XP_GAINED = EventFactory.createArrayBacked(XpGained.class, listeners -> (player, skill, amount, totalXp) -> {
        for (XpGained listener : listeners) {
            listener.onXpGained(player, skill, amount, totalXp);
        }
    });

    public static final Event<LevelUp> LEVEL_UP = EventFactory.createArrayBacked(LevelUp.class, listeners -> (player, skill, oldLevel, newLevel) -> {
        for (LevelUp listener : listeners) {
            listener.onLevelUp(player, skill, oldLevel, newLevel);
        }
    });

    private SkillEvents() {
    }

    @FunctionalInterface
    public interface XpGained {
        void onXpGained(ServerPlayer player, Skill skill, double amount, double totalXp);
    }

    @FunctionalInterface
    public interface LevelUp {
        void onLevelUp(ServerPlayer player, Skill skill, int oldLevel, int newLevel);
    }
}
