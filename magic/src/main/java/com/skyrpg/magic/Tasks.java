package com.skyrpg.magic;

import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;

/** A tiny scheduler for things that live a few ticks: flying bolts, a falling meteor, a blizzard. Runs on the server thread. */
public final class Tasks {
    /** Ticked once per server tick; returns true when it is finished. */
    @FunctionalInterface
    public interface Task {
        boolean tick();
    }

    private static final List<Task> ACTIVE = new ArrayList<>();
    private static final List<Task> PENDING = new ArrayList<>();

    private Tasks() {
    }

    static void init() {
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            ACTIVE.addAll(PENDING);
            PENDING.clear();
            ACTIVE.removeIf(task -> {
                try {
                    return task.tick();
                } catch (RuntimeException e) {
                    SkyRpgMagic.LOGGER.error("A spell task failed and was dropped", e);
                    return true;
                }
            });
        });
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
            ACTIVE.clear();
            PENDING.clear();
        });
    }

    public static void add(Task task) {
        PENDING.add(task);
    }

    /** Runs this after {@code ticks} server ticks. */
    public static void later(int ticks, Runnable action) {
        int[] left = {ticks};
        add(() -> {
            if (--left[0] > 0) {
                return false;
            }
            action.run();
            return true;
        });
    }
}
