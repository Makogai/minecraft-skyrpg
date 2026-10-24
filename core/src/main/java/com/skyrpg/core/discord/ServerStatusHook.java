package com.skyrpg.core.discord;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.skyrpg.core.SkyRpgCore;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.stream.Collectors;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * Tells a Discord channel when the server comes up and goes down, through a webhook set in
 * {@code config/skyrpg/discord.json} on the server: {@code {"webhook": "https://discord.com/api/webhooks/...",
 * "name": "SkyRPG Survival"}}. Without that file (dev, staging, players' own worlds) it does nothing. A marker file kept
 * while the server runs shows, at the next start, when the last run ended without a clean stop (a crash, or the host
 * killing it).
 */
public final class ServerStatusHook {
    private static final Path DIR = FabricLoader.getInstance().getConfigDir().resolve("skyrpg");
    private static final Path CONFIG = DIR.resolve("discord.json");
    private static final Path RUNNING = DIR.resolve("discord_running.marker");
    private static final int GREEN = 0x2ECC71;
    private static final int RED = 0xE74C3C;
    private static final int ORANGE = 0xF39C12;

    private static String webhook;
    private static String name = "SkyRPG";
    private static Instant started;

    private ServerStatusHook() {
    }

    public static void init() {
        if (!load()) {
            return;
        }
        ServerLifecycleEvents.SERVER_STARTED.register(ServerStatusHook::started);
        ServerLifecycleEvents.SERVER_STOPPING.register(ServerStatusHook::stopping);
    }

    private static boolean load() {
        if (!Files.isRegularFile(CONFIG)) {
            return false;
        }
        try {
            JsonObject json = JsonParser.parseString(Files.readString(CONFIG, StandardCharsets.UTF_8)).getAsJsonObject();
            String url = json.has("webhook") ? json.get("webhook").getAsString().trim() : "";
            if (!url.startsWith("https://discord.com/api/webhooks/") && !url.startsWith("https://discordapp.com/api/webhooks/")) {
                SkyRpgCore.LOGGER.warn("{} has no Discord webhook URL; server status posts are off", CONFIG);
                return false;
            }
            webhook = url;
            if (json.has("name")) {
                name = json.get("name").getAsString();
            }
            return true;
        } catch (IOException | RuntimeException e) {
            SkyRpgCore.LOGGER.warn("Can't read {}: {}", CONFIG, e.getMessage());
            return false;
        }
    }

    private static void started(MinecraftServer server) {
        started = Instant.now();
        boolean unclean = Files.exists(RUNNING);
        try {
            Files.createDirectories(DIR);
            Files.writeString(RUNNING, started.toString());
        } catch (IOException e) {
            SkyRpgCore.LOGGER.warn("Can't write {}: {}", RUNNING, e.getMessage());
        }
        String description = "The server is up. Hop in!";
        if (unclean) {
            description += "\n\n⚠️ The last run ended without a clean stop (a crash, or the host stopped it).";
        }
        JsonObject embed = embed("🟢 " + name + " is online", description, unclean ? ORANGE : GREEN);
        // Off the server thread: a slow Discord shouldn't hold up the first ticks.
        Thread thread = new Thread(() -> post(embed), "skyrpg-discord-status");
        thread.setDaemon(true);
        thread.start();
    }

    private static void stopping(MinecraftServer server) {
        String players = server.getPlayerList().getPlayers().stream().map(ServerPlayer::getPlainTextName).collect(Collectors.joining(", "));
        StringBuilder description = new StringBuilder("The server is going down.");
        if (started != null) {
            Duration up = Duration.between(started, Instant.now());
            description.append("\nUp for ").append(up.toHours()).append("h ").append(up.toMinutesPart()).append("m.");
        }
        if (!players.isEmpty()) {
            description.append("\nOnline: ").append(players);
        }
        // On the server thread: the JVM may exit right after this, so the post has to finish first (it times out quickly).
        post(embed("🔴 " + name + " is offline", description.toString(), RED));
        try {
            Files.deleteIfExists(RUNNING);
        } catch (IOException e) {
            SkyRpgCore.LOGGER.warn("Can't remove {}: {}", RUNNING, e.getMessage());
        }
    }

    private static JsonObject embed(String title, String description, int color) {
        JsonObject embed = new JsonObject();
        embed.addProperty("title", title);
        embed.addProperty("description", description);
        embed.addProperty("color", color);
        embed.addProperty("timestamp", Instant.now().toString());
        return embed;
    }

    private static void post(JsonObject embed) {
        JsonObject payload = new JsonObject();
        payload.addProperty("username", name);
        JsonArray embeds = new JsonArray();
        embeds.add(embed);
        payload.add("embeds", embeds);
        try {
            HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(4)).build();
            HttpRequest request = HttpRequest.newBuilder(URI.create(webhook)).timeout(Duration.ofSeconds(6))
                    .header("Content-Type", "application/json").header("User-Agent", "SkyRPG-server-status")
                    .POST(HttpRequest.BodyPublishers.ofString(payload.toString(), StandardCharsets.UTF_8)).build();
            HttpResponse<Void> response = client.send(request, HttpResponse.BodyHandlers.discarding());
            if (response.statusCode() >= 300) {
                SkyRpgCore.LOGGER.warn("Discord refused the server status post (HTTP {})", response.statusCode());
            }
        } catch (IOException e) {
            SkyRpgCore.LOGGER.warn("Couldn't post the server status to Discord: {}", e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
