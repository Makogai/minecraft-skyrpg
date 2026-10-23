package com.skyrpg.events;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.brigadier.CommandDispatcher;
import com.skyrpg.core.data.JsonDataLoader;
import com.skyrpg.core.event.TitleFlags;
import com.skyrpg.core.season.SeasonFlags;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.BossEvent;
import net.minecraft.world.level.storage.LevelResource;

/**
 * Seasons: dated server events (Hallow's Eve, ...) from {@code data/<ns>/skyrpg/seasons/<id>.json}. Between {@code start}
 * and {@code end} (instants, e.g. {@code "2026-10-30T17:00:00Z"}, so the server's time zone doesn't matter) a season shows a
 * boss bar with the time left, turns on its {@link SeasonFlags}, swaps the auto event rotation, welcomes every player once
 * (banner, commands, a title flag) and runs its {@code nights} rules at dusk in the overworld. Banners and commands run
 * when it starts and ends. A season the server missed entirely (it was down) is skipped quietly; one that started while
 * the server was down starts late. What already happened is saved in {@code skyrpg/seasons.json} in the world folder.
 *
 * <p>{@code /season} shows what's on; ops: {@code /season start|end <id>} to force one now (for tests).
 */
public final class Seasons {
    static final String DIRECTORY = "skyrpg/seasons";
    private static final long DAY = 24000;
    private static final long DUSK = 13000;
    private static final long MIDNIGHT = 18000;

    /** A {@code nights} rule: commands at dusk, every {@code every} nights of the season, or once after {@code after}. */
    record Night(int every, Instant after, boolean once, List<String> commands) {
    }

    record Season(String id, String name, int color, BossEvent.BossBarColor barColor, Instant start, Instant end, Set<String> flags,
                  List<String> rotation, int[] gapMinutes, JsonObject startBanner, JsonObject endBanner, List<String> startCommands,
                  List<String> endCommands, JsonObject welcomeBanner, List<String> welcomeCommands, String welcomeFlag, List<Night> nights) {
        boolean live(Instant now) {
            return !now.isBefore(start) && now.isBefore(end);
        }
    }

    /** What a season has done so far (saved). */
    private static final class State {
        boolean started;
        boolean ended;
        long lastNight = -1;
        int nights;
        final Set<Integer> onceDone = new HashSet<>();
        final Set<UUID> welcomed = new HashSet<>();
        /** Forced from the command: live regardless of the dates until ended. */
        boolean forced;
    }

    private static Map<String, Season> seasons = Map.of();
    private static final Map<String, State> STATES = new HashMap<>();
    private static final Map<String, ServerBossEvent> BARS = new HashMap<>();
    private static MinecraftServer server;
    private static boolean dirty;

    private Seasons() {
    }

    static void init() {
        JsonDataLoader.register(SkyRpgEvents.id("seasons"), DIRECTORY, Seasons::load);
        ServerLifecycleEvents.SERVER_STARTED.register(started -> {
            server = started;
            read();
        });
        ServerLifecycleEvents.SERVER_STOPPING.register(stopping -> {
            write();
            BARS.values().forEach(ServerBossEvent::removeAllPlayers);
            BARS.clear();
            SeasonFlags.set(Set.of());
            server = null;
        });
        ServerTickEvents.END_SERVER_TICK.register(ticking -> {
            if (ticking.getTickCount() % 20 == 0) {
                try {
                    tick(ticking);
                } catch (RuntimeException e) {
                    SkyRpgEvents.LOGGER.error("Season tick failed", e);
                }
            }
        });
        ServerPlayConnectionEvents.JOIN.register((handler, sender, s) -> s.execute(() -> {
            for (Season season : seasons.values()) {
                try {
                    if (live(season, Instant.now())) {
                        welcome(season, handler.player);
                    }
                } catch (RuntimeException e) {
                    SkyRpgEvents.LOGGER.error("Season welcome failed", e);
                }
            }
        }));
    }

    // ---------------------------------------------------------------- data

    private static void load(Map<Identifier, JsonElement> files) {
        Map<String, Season> loaded = new HashMap<>();
        files.forEach((id, element) -> {
            try {
                JsonObject json = element.getAsJsonObject();
                List<Night> nights = new ArrayList<>();
                if (json.has("nights")) {
                    for (JsonElement night : json.getAsJsonArray("nights")) {
                        JsonObject n = night.getAsJsonObject();
                        nights.add(new Night(Json.integer(n, 1, "every"), n.has("after") ? Instant.parse(n.get("after").getAsString()) : null,
                                Json.bool(n, false, "once"), strings(n, "commands")));
                    }
                }
                JsonObject welcome = json.has("welcome") ? json.getAsJsonObject("welcome") : new JsonObject();
                JsonArray gap = json.has("event_gap_minutes") ? json.getAsJsonArray("event_gap_minutes") : null;
                loaded.put(id.getPath(), new Season(id.getPath(), Json.string(json, id.getPath(), "name"),
                        Json.color(Json.get(json, "color"), Json.GOLD) & 0xFFFFFF,
                        BossEvent.BossBarColor.valueOf(Json.string(json, "purple", "bar_color").toUpperCase(Locale.ROOT)),
                        Instant.parse(json.get("start").getAsString()), Instant.parse(json.get("end").getAsString()),
                        Set.copyOf(strings(json, "flags")), strings(json, "rotation"),
                        gap == null ? null : new int[] {gap.get(0).getAsInt(), gap.get(gap.size() - 1).getAsInt()},
                        object(json, "start_banner"), object(json, "end_banner"), strings(json, "start_commands"), strings(json, "end_commands"),
                        object(welcome, "banner"), strings(welcome, "commands"), Json.string(welcome, "", "flag"), List.copyOf(nights)));
            } catch (RuntimeException e) {
                SkyRpgEvents.LOGGER.warn("Skipping season {}: {}", id, e.getMessage());
            }
        });
        seasons = Map.copyOf(loaded);
    }

    private static List<String> strings(JsonObject json, String key) {
        List<String> list = new ArrayList<>();
        if (json.has(key)) {
            json.getAsJsonArray(key).forEach(e -> list.add(e.getAsString()));
        }
        return List.copyOf(list);
    }

    private static JsonObject object(JsonObject json, String key) {
        return json.has(key) ? json.getAsJsonObject(key) : null;
    }

    // ---------------------------------------------------------------- what the other parts ask

    private static State state(Season season) {
        return STATES.computeIfAbsent(season.id(), id -> new State());
    }

    private static boolean live(Season season, Instant now) {
        State state = state(season);
        return state.started && !state.ended && (state.forced || season.live(now));
    }

    /** The auto event rotation of a live season, or null for the normal one. */
    static List<String> rotation() {
        Instant now = Instant.now();
        return seasons.values().stream().filter(s -> live(s, now) && !s.rotation().isEmpty()).map(Season::rotation).findFirst().orElse(null);
    }

    /** The auto event gap of a live season in minutes [min, max], or null for the normal one. */
    static int[] gapMinutes() {
        Instant now = Instant.now();
        return seasons.values().stream().filter(s -> live(s, now) && s.gapMinutes() != null).map(Season::gapMinutes).findFirst().orElse(null);
    }

    // ---------------------------------------------------------------- the clock

    private static void tick(MinecraftServer s) {
        Instant now = Instant.now();
        Set<String> flags = new HashSet<>();
        for (Season season : seasons.values()) {
            State state = state(season);
            if (!state.started && !state.ended) {
                if (!now.isBefore(season.end())) {
                    // Missed it entirely while the server was down.
                    state.started = true;
                    state.ended = true;
                    dirty = true;
                } else if (season.live(now)) {
                    begin(s, season);
                }
            }
            if (state.started && !state.ended && !state.forced && !season.live(now)) {
                finish(s, season);
            }
            if (live(season, now)) {
                flags.addAll(season.flags());
                bar(s, season, now);
                night(s, season, state, now);
            }
        }
        SeasonFlags.set(flags);
        if (dirty && s.getTickCount() % 200 == 0) {
            write();
        }
    }

    private static void begin(MinecraftServer s, Season season) {
        State state = state(season);
        state.started = true;
        dirty = true;
        SkyRpgEvents.LOGGER.info("Season {} started", season.id());
        if (season.startBanner() != null) {
            EventManager.announce(s.getPlayerList().getPlayers(), Announcement.parse(season.startBanner(), s.registryAccess()));
        }
        season.startCommands().forEach(command -> run(s, command, null));
        for (ServerPlayer player : s.getPlayerList().getPlayers()) {
            // The start banner already said hello; the gift and title still come.
            welcome(season, player, false);
        }
    }

    private static void finish(MinecraftServer s, Season season) {
        State state = state(season);
        state.ended = true;
        state.forced = false;
        dirty = true;
        SkyRpgEvents.LOGGER.info("Season {} ended", season.id());
        ServerBossEvent bar = BARS.remove(season.id());
        if (bar != null) {
            bar.removeAllPlayers();
        }
        if (season.endBanner() != null) {
            EventManager.announce(s.getPlayerList().getPlayers(), Announcement.parse(season.endBanner(), s.registryAccess()));
        }
        season.endCommands().forEach(command -> run(s, command, null));
        write();
    }

    private static void welcome(Season season, ServerPlayer player) {
        welcome(season, player, true);
    }

    private static void welcome(Season season, ServerPlayer player, boolean banner) {
        State state = state(season);
        if (!state.welcomed.add(player.getUUID())) {
            return;
        }
        dirty = true;
        if (banner && season.welcomeBanner() != null) {
            EventManager.announce(List.of(player), Announcement.parse(season.welcomeBanner(), player.level().getServer().registryAccess()));
        }
        season.welcomeCommands().forEach(command -> run(player.level().getServer(), command, player));
        if (!season.welcomeFlag().isEmpty()) {
            TitleFlags.grant(player, season.welcomeFlag());
        }
    }

    private static void bar(MinecraftServer s, Season season, Instant now) {
        ServerBossEvent bar = BARS.computeIfAbsent(season.id(), id -> new ServerBossEvent(UUID.randomUUID(), Component.empty(),
                season.barColor(), BossEvent.BossBarOverlay.NOTCHED_10));
        State state = state(season);
        long total = Duration.between(season.start(), season.end()).toMillis();
        long left = state.forced && !season.live(now) ? total : Math.max(0, Duration.between(now, season.end()).toMillis());
        bar.setName(Component.literal(season.name()).withStyle(style -> style.withColor(season.color()).withBold(true))
                .append(Component.literal("  " + duration(left) + " left").withStyle(ChatFormatting.GRAY)));
        bar.setProgress((float) Math.max(0, Math.min(1, left / (double) Math.max(1, total))));
        s.getPlayerList().getPlayers().forEach(bar::addPlayer);
    }

    /** At dusk in the overworld (once per night, with a player there), the season's {@code nights} rules. */
    private static void night(MinecraftServer s, Season season, State state, Instant now) {
        if (season.nights().isEmpty()) {
            return;
        }
        ServerLevel overworld = s.overworld();
        long clock = overworld.getOverworldClockTime();
        long day = Math.floorDiv(clock, DAY);
        long time = Math.floorMod(clock, DAY);
        if (time < DUSK || time >= MIDNIGHT || day == state.lastNight
                || overworld.players().stream().noneMatch(p -> !p.isSpectator())) {
            return;
        }
        state.lastNight = day;
        state.nights++;
        dirty = true;
        for (int i = 0; i < season.nights().size(); i++) {
            Night night = season.nights().get(i);
            if (night.after() != null && now.isBefore(night.after()) || night.once() && state.onceDone.contains(i)
                    || !night.once() && (state.nights - 1) % Math.max(1, night.every()) != 0) {
                continue;
            }
            if (night.once()) {
                state.onceDone.add(i);
            }
            night.commands().forEach(command -> run(s, command, null));
        }
    }

    private static void run(MinecraftServer s, String command, ServerPlayer player) {
        String line = player == null ? command : command.replace("{player}", player.getGameProfile().name());
        try {
            s.getCommands().performPrefixedCommand(s.createCommandSourceStack().withSuppressedOutput(), line);
        } catch (RuntimeException e) {
            SkyRpgEvents.LOGGER.warn("Season command '{}' failed: {}", line, e.getMessage());
        }
    }

    private static String duration(long millis) {
        long minutes = Math.max(1, millis / 60_000);
        long days = minutes / 1440;
        long hours = minutes % 1440 / 60;
        return days > 0 ? days + "d " + hours + "h" : hours > 0 ? hours + "h " + minutes % 60 + "m" : minutes + "m";
    }

    // ---------------------------------------------------------------- commands

    static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("season")
                .executes(ctx -> {
                    Instant now = Instant.now();
                    List<Season> on = seasons.values().stream().filter(s -> live(s, now)).toList();
                    if (on.isEmpty()) {
                        Season next = seasons.values().stream().filter(s -> now.isBefore(s.start()))
                                .min((a, b) -> a.start().compareTo(b.start())).orElse(null);
                        ctx.getSource().sendSuccess(() -> Component.literal(next == null ? "No season is on."
                                : "No season is on. Next: " + next.name() + " in " + duration(Duration.between(now, next.start()).toMillis())), false);
                        return 0;
                    }
                    for (Season season : on) {
                        ctx.getSource().sendSuccess(() -> Component.literal(season.name()).withStyle(style -> style.withColor(season.color()).withBold(true))
                                .append(Component.literal(" is on, " + duration(Math.max(0, Duration.between(now, season.end()).toMillis())) + " left")
                                        .withStyle(ChatFormatting.GRAY)), false);
                    }
                    return on.size();
                })
                .then(Commands.literal("start").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                        .then(Commands.argument("id", com.mojang.brigadier.arguments.StringArgumentType.word())
                                .suggests((c, b) -> net.minecraft.commands.SharedSuggestionProvider.suggest(seasons.keySet(), b))
                                .executes(ctx -> {
                                    Season season = seasons.get(com.mojang.brigadier.arguments.StringArgumentType.getString(ctx, "id"));
                                    if (season == null) {
                                        ctx.getSource().sendFailure(Component.literal("No such season"));
                                        return 0;
                                    }
                                    State state = state(season);
                                    state.started = false;
                                    state.ended = false;
                                    state.welcomed.clear();
                                    state.onceDone.clear();
                                    state.nights = 0;
                                    state.lastNight = -1;
                                    state.forced = true;
                                    begin(ctx.getSource().getServer(), season);
                                    ctx.getSource().sendSuccess(() -> Component.literal("Started " + season.name()), true);
                                    return 1;
                                })))
                .then(Commands.literal("end").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                        .then(Commands.argument("id", com.mojang.brigadier.arguments.StringArgumentType.word())
                                .suggests((c, b) -> net.minecraft.commands.SharedSuggestionProvider.suggest(seasons.keySet(), b))
                                .executes(ctx -> {
                                    Season season = seasons.get(com.mojang.brigadier.arguments.StringArgumentType.getString(ctx, "id"));
                                    if (season == null || !live(season, Instant.now())) {
                                        ctx.getSource().sendFailure(Component.literal("That season isn't on"));
                                        return 0;
                                    }
                                    boolean test = state(season).forced && Instant.now().isBefore(season.start());
                                    finish(ctx.getSource().getServer(), season);
                                    if (test) {
                                        // A test run before the real dates: forget it, so the season still starts on time.
                                        STATES.put(season.id(), new State());
                                        write();
                                    }
                                    ctx.getSource().sendSuccess(() -> Component.literal("Ended " + season.name() + (test ? " (test run; it still starts on "
                                            + season.start() + ")" : "")), true);
                                    return 1;
                                }))));
    }

    // ---------------------------------------------------------------- saving

    private static Path file() {
        return server.getWorldPath(LevelResource.ROOT).resolve("skyrpg").resolve("seasons.json");
    }

    private static void read() {
        STATES.clear();
        try {
            Path file = file();
            if (!Files.isRegularFile(file)) {
                return;
            }
            JsonObject root = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject();
            for (String id : root.keySet()) {
                JsonObject json = root.getAsJsonObject(id);
                State state = new State();
                state.started = Json.bool(json, false, "started");
                state.ended = Json.bool(json, false, "ended");
                state.forced = Json.bool(json, false, "forced");
                state.lastNight = json.has("last_night") ? json.get("last_night").getAsLong() : -1;
                state.nights = Json.integer(json, 0, "nights");
                if (json.has("once_done")) {
                    json.getAsJsonArray("once_done").forEach(e -> state.onceDone.add(e.getAsInt()));
                }
                if (json.has("welcomed")) {
                    json.getAsJsonArray("welcomed").forEach(e -> state.welcomed.add(UUID.fromString(e.getAsString())));
                }
                STATES.put(id, state);
            }
        } catch (IOException | RuntimeException e) {
            SkyRpgEvents.LOGGER.warn("Couldn't read the seasons file", e);
        }
    }

    private static void write() {
        if (server == null) {
            return;
        }
        JsonObject root = new JsonObject();
        STATES.forEach((id, state) -> {
            JsonObject json = new JsonObject();
            json.addProperty("started", state.started);
            json.addProperty("ended", state.ended);
            json.addProperty("forced", state.forced);
            json.addProperty("last_night", state.lastNight);
            json.addProperty("nights", state.nights);
            JsonArray once = new JsonArray();
            state.onceDone.forEach(once::add);
            json.add("once_done", once);
            JsonArray welcomed = new JsonArray();
            state.welcomed.forEach(uuid -> welcomed.add(uuid.toString()));
            json.add("welcomed", welcomed);
            root.add(id, json);
        });
        try {
            Path file = file();
            Files.createDirectories(file.getParent());
            Path temp = file.resolveSibling("seasons.json.tmp");
            Files.writeString(temp, root.toString(), StandardCharsets.UTF_8);
            Files.move(temp, file, java.nio.file.StandardCopyOption.REPLACE_EXISTING, java.nio.file.StandardCopyOption.ATOMIC_MOVE);
            dirty = false;
        } catch (IOException e) {
            SkyRpgEvents.LOGGER.warn("Couldn't save the seasons file", e);
        }
    }
}
