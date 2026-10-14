package com.skyrpg.core.event;

import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.EventFactory;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;

/**
 * Things players do that the questline (progress mod) cares about, fired by the owning mods so they never call progress
 * directly: talking to an NPC, crafting at the Artisan Bench, starting and finishing slayer quests.
 */
public final class ProgressEvents {
    @FunctionalInterface
    public interface NpcTalked {
        void onTalked(ServerPlayer player, Identifier npc);
    }

    @FunctionalInterface
    public interface BenchCrafted {
        /** {@code item} is the crafted item's id, {@code count} how many items came out. */
        void onCrafted(ServerPlayer player, Identifier item, int count);
    }

    @FunctionalInterface
    public interface SlayerQuest {
        /** {@code line} is the slayer line id (e.g. {@code bone_marshal}); {@code completed} false = just started. */
        void onSlayer(ServerPlayer player, String line, int tier, boolean completed);
    }

    public static final Event<NpcTalked> NPC_TALKED = EventFactory.createArrayBacked(NpcTalked.class, listeners -> (player, npc) -> {
        for (NpcTalked listener : listeners) {
            listener.onTalked(player, npc);
        }
    });

    public static final Event<BenchCrafted> BENCH_CRAFTED = EventFactory.createArrayBacked(BenchCrafted.class, listeners -> (player, item, count) -> {
        for (BenchCrafted listener : listeners) {
            listener.onCrafted(player, item, count);
        }
    });

    public static final Event<SlayerQuest> SLAYER_QUEST = EventFactory.createArrayBacked(SlayerQuest.class, listeners -> (player, line, tier, completed) -> {
        for (SlayerQuest listener : listeners) {
            listener.onSlayer(player, line, tier, completed);
        }
    });

    private ProgressEvents() {
    }
}
