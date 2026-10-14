package com.skyrpg.core.event;

import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.EventFactory;
import net.minecraft.server.level.ServerPlayer;

/**
 * A player did something only the owning mod can see (tamed a legendary griffin, joined a party): that mod calls
 * {@link #grant} with a flag name, and the progress mod saves it and checks the earnable titles whose condition is that flag.
 * Nothing happens when the progress mod isn't there.
 */
public final class TitleFlags {
    @FunctionalInterface
    public interface Granted {
        void onGranted(ServerPlayer player, String flag);
    }

    public static final Event<Granted> GRANTED = EventFactory.createArrayBacked(Granted.class, listeners -> (player, flag) -> {
        for (Granted listener : listeners) {
            listener.onGranted(player, flag);
        }
    });

    private TitleFlags() {
    }

    public static void grant(ServerPlayer player, String flag) {
        GRANTED.invoker().onGranted(player, flag);
    }
}
