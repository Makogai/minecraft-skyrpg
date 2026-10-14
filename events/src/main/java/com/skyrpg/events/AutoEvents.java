package com.skyrpg.events;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.storage.LevelResource;

/**
 * Events that start on their own: while at least {@link #MIN_PLAYERS} players are online and nothing is running, a random
 * event from {@link #ROTATION} starts every 60 to 90 minutes. On by default; {@code /event auto on|off|now}. The switch is
 * saved in the world folder ({@code skyrpg/auto_events.json}).
 */
public final class AutoEvents {
    static final List<String> ROTATION = List.of("timber_rush", "monster_hunt", "harvest_rush", "glimmer_rush", "fishing_frenzy");
    private static final int MIN_PLAYERS = 2;
    private static final int MIN_GAP_TICKS = 60 * 60 * 20;
    private static final int GAP_SPREAD_TICKS = 30 * 60 * 20;

    private static boolean enabled = true;
    private static long nextTick = -1;
    private static MinecraftServer server;

    private AutoEvents() {
    }

    static void init() {
        ServerLifecycleEvents.SERVER_STARTED.register(started -> {
            server = started;
            read();
        });
        ServerLifecycleEvents.SERVER_STOPPING.register(stopping -> server = null);
        ServerTickEvents.END_SERVER_TICK.register(ticking -> {
            if (ticking.getTickCount() % 20 != 0 || !enabled) {
                return;
            }
            long now = ticking.getTickCount();
            if (nextTick < 0) {
                schedule(now);
            }
            if (now >= nextTick && ticking.getPlayerList().getPlayerCount() >= MIN_PLAYERS && EventManager.running().isEmpty()) {
                startRandom();
                schedule(now);
            }
        });
    }

    private static void schedule(long now) {
        nextTick = now + MIN_GAP_TICKS + RandomSource.create().nextInt(GAP_SPREAD_TICKS);
    }

    /** Starts a random event from the rotation now; returns its id, or null if none is defined. */
    static String startRandom() {
        List<String> defined = new ArrayList<>();
        for (String id : ROTATION) {
            if (EventManager.defined(id) != null) {
                defined.add(id);
            }
        }
        if (defined.isEmpty()) {
            return null;
        }
        String id = defined.get(RandomSource.create().nextInt(defined.size()));
        try {
            EventManager.start(EventManager.defined(id));
            return id;
        } catch (RuntimeException e) {
            SkyRpgEvents.LOGGER.warn("Auto event {} didn't start: {}", id, e.getMessage());
            return null;
        }
    }

    static boolean enabled() {
        return enabled;
    }

    static void setEnabled(boolean on) {
        enabled = on;
        nextTick = -1;
        write();
    }

    private static Path file() {
        return server.getWorldPath(LevelResource.ROOT).resolve("skyrpg").resolve("auto_events.json");
    }

    private static void read() {
        try {
            Path file = file();
            if (Files.isRegularFile(file)) {
                enabled = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject().get("enabled").getAsBoolean();
            }
        } catch (IOException | RuntimeException e) {
            SkyRpgEvents.LOGGER.warn("Couldn't read the auto events switch", e);
        }
    }

    private static void write() {
        if (server == null) {
            return;
        }
        try {
            JsonObject json = new JsonObject();
            json.addProperty("enabled", enabled);
            Path file = file();
            Files.createDirectories(file.getParent());
            Files.writeString(file, json.toString(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            SkyRpgEvents.LOGGER.warn("Couldn't save the auto events switch", e);
        }
    }
}
