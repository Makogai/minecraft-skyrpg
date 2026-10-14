package com.skyrpg.core.action;

import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.EventFactory;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/**
 * Something reeled in with a fishing rod: fired by the fishing mod for every catch (a fish, junk, treasure or a sea
 * creature, which comes with an empty stack), so other mods (events, contracts) can count catches without depending on
 * it.
 */
public final class Catches {
    public interface Caught {
        void onCaught(ServerPlayer player, ItemStack catchStack, boolean seaCreature);
    }

    public static final Event<Caught> CAUGHT = EventFactory.createArrayBacked(Caught.class, listeners -> (player, stack, creature) -> {
        for (Caught listener : listeners) {
            listener.onCaught(player, stack, creature);
        }
    });

    private Catches() {
    }
}
