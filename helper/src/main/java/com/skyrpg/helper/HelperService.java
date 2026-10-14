package com.skyrpg.helper;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;

/**
 * Talks to the helper service (tools/skyhelper/helper.py) over HTTP on this machine. Every call is asynchronous, so a slow
 * answer never holds up a server tick. When the service isn't running it is started, if the config allows it.
 */
final class HelperService {
    private static final HttpClient CLIENT = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();
    private static Process process;
    private static long lastLaunch;

    private HelperService() {
    }

    static CompletableFuture<JsonObject> ask(JsonObject body) {
        HttpRequest request = HttpRequest.newBuilder(URI.create(HelperConfig.url + "/ask"))
                .timeout(Duration.ofSeconds(HelperConfig.timeoutSeconds))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body.toString(), StandardCharsets.UTF_8))
                .build();
        return CLIENT.sendAsync(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)).thenApply(response -> {
            if (response.statusCode() != 200) {
                throw new CompletionException(new IOException("HTTP " + response.statusCode() + ": " + response.body()));
            }
            return JsonParser.parseString(response.body()).getAsJsonObject();
        });
    }

    private static CompletableFuture<JsonObject> health() {
        HttpRequest request = HttpRequest.newBuilder(URI.create(HelperConfig.url + "/health")).timeout(Duration.ofSeconds(3)).GET().build();
        return CLIENT.sendAsync(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8))
                .thenApply(response -> response.statusCode() == 200 ? JsonParser.parseString(response.body()).getAsJsonObject() : null)
                .exceptionally(error -> null);
    }

    /** Makes sure the service runs: checks it, and launches it when nothing answers. */
    static void start(MinecraftServer server) {
        if (!HelperConfig.enabled || !HelperConfig.autostart) {
            return;
        }
        health().thenAccept(status -> {
            if (status == null) {
                launch();
            }
        });
    }

    private static synchronized void launch() {
        if (process != null && process.isAlive() || System.currentTimeMillis() - lastLaunch < 60_000) {
            return;   // already starting (loading the model takes a while the first time)
        }
        Path script = findScript();
        if (script == null) {
            SkyRpgHelper.LOGGER.warn("SkyHelper's service isn't running and tools/skyhelper/helper.py wasn't found; set \"script\" in config/skyrpg/helper.json");
            return;
        }
        lastLaunch = System.currentTimeMillis();
        try {
            Path log = Path.of("logs", "skyhelper.log").toAbsolutePath();
            Files.createDirectories(log.getParent());
            process = new ProcessBuilder(HelperConfig.python, script.toString())
                    .directory(script.getParent().toFile())
                    .redirectErrorStream(true)
                    .redirectOutput(ProcessBuilder.Redirect.appendTo(log.toFile()))
                    .start();
            SkyRpgHelper.LOGGER.info("Started SkyHelper's service: {} (output in {})", script, log);
        } catch (IOException e) {
            SkyRpgHelper.LOGGER.warn("Couldn't start SkyHelper's service: {}", e.toString());
        }
    }

    private static Path findScript() {
        if (!HelperConfig.script.isEmpty()) {
            Path path = Path.of(HelperConfig.script);
            return Files.exists(path) ? path.toAbsolutePath() : null;
        }
        Path dir = Path.of("").toAbsolutePath();
        for (int i = 0; i < 6 && dir != null; i++, dir = dir.getParent()) {
            Path candidate = dir.resolve("tools").resolve("skyhelper").resolve("helper.py");
            if (Files.exists(candidate)) {
                return candidate;
            }
        }
        return null;
    }

    /** Stops the service if this server started it; one somebody else started keeps running. */
    static synchronized void stop() {
        if (process != null && process.isAlive()) {
            process.descendants().forEach(ProcessHandle::destroy);
            process.destroy();
        }
        process = null;
    }

    static void status(CommandSourceStack source) {
        MinecraftServer server = source.getServer();
        health().thenAccept(status -> server.execute(() -> {
            if (status == null) {
                source.sendFailure(Component.literal("SkyHelper's service isn't answering at " + HelperConfig.url
                        + (process != null && process.isAlive() ? " (it is starting)" : "") + ". /skyhelper start launches it."));
            } else {
                source.sendSuccess(() -> Component.literal("SkyHelper is up: model " + status.get("model").getAsString()
                        + ", Ollama " + (status.get("ollama").getAsBoolean() ? "running" : "NOT running")
                        + ", " + status.get("pages").getAsInt() + " wiki pages."), false);
            }
        }));
    }
}
