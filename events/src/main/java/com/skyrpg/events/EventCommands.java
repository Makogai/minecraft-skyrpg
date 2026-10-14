package com.skyrpg.events;

import com.google.gson.JsonObject;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.skyrpg.core.text.Msg;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.Function;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;

/**
 * <pre>
 * /event list                                   running events and the ones defined in data files
 * /event status [id]                            JSON for scripts: phase (lobby/running/none), clock, players, scores
 * /event join [id]  /  /event leave [id]        opt in or out of an opt-in event
 * /event start &lt;id&gt; [duration]                  (op) start a data-file event, e.g. /event start timber_rush 2m
 * /event run &lt;json&gt;                             (op) start an event described in JSON, see {@link EventSpec}
 * /event stop [id]                              (op) cancel, no prizes
 * /event end [id]                               (op) finish now and hand out prizes
 * /event time &lt;set|add&gt; &lt;duration&gt; [id]        (op) change the time left
 * /event points &lt;players&gt; &lt;add|set&gt; &lt;n&gt; [id]    (op) change scores (joins opt-in events)
 * /announce [to &lt;players&gt;] &lt;json&gt;              (op) a banner, see {@link Announcement}
 * /board show [to &lt;players&gt;] &lt;json&gt;            (op) a custom sidebar, see {@link Board}
 * /board hide [players]                         (op)
 * </pre>
 * {@code [id]} can be left out when only one event is running. Durations are seconds or "5m", "1m30s".
 * Replies go to whoever ran the command (so RCON sees them too).
 */
final class EventCommands {
    private static final SuggestionProvider<CommandSourceStack> RUNNING = (ctx, builder) ->
            SharedSuggestionProvider.suggest(EventManager.running().stream().map(ActiveEvent::id).toList(), builder);
    private static final SuggestionProvider<CommandSourceStack> DEFINED = (ctx, builder) ->
            SharedSuggestionProvider.suggest(EventManager.definedIds(), builder);
    private static final SuggestionProvider<CommandSourceStack> DURATIONS = (ctx, builder) ->
            SharedSuggestionProvider.suggest(List.of("30s", "1m", "2m", "5m", "10m"), builder);

    private EventCommands() {
    }

    /** A command body that may throw {@link IllegalArgumentException} or {@link IllegalStateException} with a message for chat. */
    @FunctionalInterface
    private interface Action {
        int run(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException;
    }

    private static com.mojang.brigadier.Command<CommandSourceStack> safe(Action action) {
        return ctx -> {
            try {
                return action.run(ctx);
            } catch (IllegalArgumentException | IllegalStateException e) {
                ctx.getSource().sendFailure(Component.literal(e.getMessage()));
                return 0;
            }
        };
    }

    static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("event")
                .executes(safe(ctx -> list(ctx.getSource())))
                .then(Commands.literal("list").executes(safe(ctx -> list(ctx.getSource()))))
                // /event auto on|off|now: random events every hour or so while 2+ players are on.
                .then(admin("auto")
                        .executes(ctx -> {
                            ctx.getSource().sendSuccess(() -> Component.literal("Auto events are " + (AutoEvents.enabled() ? "on" : "off")), false);
                            return 1;
                        })
                        .then(Commands.literal("on").executes(ctx -> {
                            AutoEvents.setEnabled(true);
                            ctx.getSource().sendSuccess(() -> Component.literal("Auto events on: one every 60-90 minutes while 2+ players are online"), true);
                            return 1;
                        }))
                        .then(Commands.literal("off").executes(ctx -> {
                            AutoEvents.setEnabled(false);
                            ctx.getSource().sendSuccess(() -> Component.literal("Auto events off"), true);
                            return 1;
                        }))
                        .then(Commands.literal("now").executes(ctx -> {
                            String started = AutoEvents.startRandom();
                            ctx.getSource().sendSuccess(() -> Component.literal(started == null ? "No event to start" : "Started " + started), true);
                            return started == null ? 0 : 1;
                        })))
                .then(Commands.literal("status")
                        .executes(safe(ctx -> status(ctx.getSource(), null)))
                        .then(Commands.argument("id", StringArgumentType.word()).suggests(RUNNING)
                                .executes(safe(ctx -> status(ctx.getSource(), StringArgumentType.getString(ctx, "id"))))))
                .then(withOptionalId(Commands.literal("join"), (ctx, id) -> join(ctx.getSource(), id, true)))
                .then(withOptionalId(Commands.literal("leave"), (ctx, id) -> join(ctx.getSource(), id, false)))
                .then(admin("start")
                        .then(Commands.argument("id", StringArgumentType.word()).suggests(DEFINED)
                                .executes(safe(ctx -> start(ctx.getSource(), StringArgumentType.getString(ctx, "id"), null)))
                                .then(Commands.argument("duration", StringArgumentType.word()).suggests(DURATIONS)
                                        .executes(safe(ctx -> start(ctx.getSource(), StringArgumentType.getString(ctx, "id"),
                                                StringArgumentType.getString(ctx, "duration")))))))
                .then(admin("run")
                        .then(Commands.argument("json", StringArgumentType.greedyString())
                                .executes(safe(ctx -> run(ctx.getSource(), StringArgumentType.getString(ctx, "json"))))))
                .then(withOptionalId(admin("stop"), (ctx, id) -> finish(ctx.getSource(), id, false)))
                .then(withOptionalId(admin("end"), (ctx, id) -> finish(ctx.getSource(), id, true)))
                .then(admin("time")
                        .then(timeCommand("set", false))
                        .then(timeCommand("add", true)))
                .then(admin("points")
                        .then(Commands.argument("players", EntityArgument.players())
                                .then(pointsCommand("add", false))
                                .then(pointsCommand("set", true)))));

        dispatcher.register(Commands.literal("announce")
                .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .then(Commands.literal("to")
                        .then(Commands.argument("targets", EntityArgument.players())
                                .then(Commands.argument("json", StringArgumentType.greedyString())
                                        .executes(safe(ctx -> announce(ctx.getSource(), EntityArgument.getPlayers(ctx, "targets"),
                                                StringArgumentType.getString(ctx, "json")))))))
                .then(Commands.argument("json", StringArgumentType.greedyString())
                        .executes(safe(ctx -> announce(ctx.getSource(), null, StringArgumentType.getString(ctx, "json"))))));

        dispatcher.register(Commands.literal("board")
                .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .then(Commands.literal("show")
                        .then(Commands.literal("to")
                                .then(Commands.argument("targets", EntityArgument.players())
                                        .then(Commands.argument("json", StringArgumentType.greedyString())
                                                .executes(safe(ctx -> showBoard(ctx.getSource(), EntityArgument.getPlayers(ctx, "targets"),
                                                        StringArgumentType.getString(ctx, "json")))))))
                        .then(Commands.argument("json", StringArgumentType.greedyString())
                                .executes(safe(ctx -> showBoard(ctx.getSource(), null, StringArgumentType.getString(ctx, "json"))))))
                .then(Commands.literal("hide")
                        .executes(safe(ctx -> hideBoard(ctx.getSource(), null)))
                        .then(Commands.argument("targets", EntityArgument.players())
                                .executes(safe(ctx -> hideBoard(ctx.getSource(), EntityArgument.getPlayers(ctx, "targets")))))));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> admin(String name) {
        return Commands.literal(name).requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS));
    }

    @FunctionalInterface
    private interface IdAction {
        int run(CommandContext<CommandSourceStack> ctx, String id) throws CommandSyntaxException;
    }

    /** {@code node} runs with no id, or with a running event's id after it. */
    private static <T extends ArgumentBuilder<CommandSourceStack, T>> T withOptionalId(T node, IdAction action) {
        return node.executes(safe(ctx -> action.run(ctx, null)))
                .then(Commands.argument("id", StringArgumentType.word()).suggests(RUNNING)
                        .executes(safe(ctx -> action.run(ctx, StringArgumentType.getString(ctx, "id")))));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> timeCommand(String name, boolean add) {
        return Commands.literal(name).then(withOptionalId(Commands.argument("duration", StringArgumentType.word()).suggests(DURATIONS),
                (ctx, id) -> {
                    ActiveEvent event = pick(id);
                    int seconds = Json.seconds(StringArgumentType.getString(ctx, "duration"));
                    int left = add ? event.secondsLeft(System.currentTimeMillis()) + seconds : seconds;
                    if (left <= 0) {
                        throw new IllegalArgumentException("That would leave no time; use /event end instead");
                    }
                    SkyRpgEvents.setTimeLeft(event.id(), left);
                    ctx.getSource().sendSuccess(() -> Msg.text(event.spec().name() + " now ends in " + Json.clock(left), ChatFormatting.GREEN), false);
                    return left;
                }));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> pointsCommand(String name, boolean set) {
        return Commands.literal(name).then(withOptionalId(Commands.argument("amount", IntegerArgumentType.integer()), (ctx, id) -> {
            ActiveEvent event = pick(id);
            Collection<ServerPlayer> players = EntityArgument.getPlayers(ctx, "players");
            int amount = IntegerArgumentType.getInteger(ctx, "amount");
            for (ServerPlayer player : players) {
                if (!event.participates(player)) {
                    EventManager.join(event, player);
                }
                if (set) {
                    EventManager.setPoints(event, player, amount);
                } else {
                    EventManager.addPoints(event, player, amount);
                }
                int total = event.score(player.getUUID());
                ctx.getSource().sendSuccess(() -> Msg.text(player.getScoreboardName() + " now has " + Msg.number(total) + " " + event.spec().unit()
                        + " in " + event.spec().name(), ChatFormatting.GREEN), false);
            }
            return players.size();
        }));
    }

    /** The event called {@code id}, or the only one running if {@code id} is null. */
    private static ActiveEvent pick(String id) {
        Collection<ActiveEvent> running = EventManager.running();
        if (id != null) {
            return EventManager.get(id).orElseThrow(() -> new IllegalArgumentException("No running event called '" + id + "'"
                    + (running.isEmpty() ? "" : " (running: " + ids(running) + ")")));
        }
        if (running.isEmpty()) {
            throw new IllegalArgumentException("No event is running");
        }
        if (running.size() > 1) {
            throw new IllegalArgumentException("Several events are running, say which: " + ids(running));
        }
        return running.iterator().next();
    }

    private static String ids(Collection<ActiveEvent> events) {
        return String.join(", ", events.stream().map(ActiveEvent::id).toList());
    }

    private static int list(CommandSourceStack source) {
        Collection<ActiveEvent> running = EventManager.running();
        long now = System.currentTimeMillis();
        List<Component> lines = new ArrayList<>();
        if (running.isEmpty()) {
            lines.add(Msg.text("No events running.", ChatFormatting.GRAY));
        } else {
            lines.add(Msg.text("Running events:", ChatFormatting.GOLD));
            for (ActiveEvent event : running) {
                List<ActiveEvent.Standing> standings = event.standings();
                MutableComponent line = Msg.text("  " + event.spec().name(), ChatFormatting.WHITE)
                        .append(Msg.text(" (" + event.id() + ")", ChatFormatting.DARK_GRAY))
                        .append(Msg.text("  " + Json.clock(event.secondsLeft(now)) + " left · " + event.participantCount() + " playing", ChatFormatting.GRAY));
                if (!standings.isEmpty() && standings.getFirst().place() == 1) {
                    line.append(Msg.text(" · leader " + standings.getFirst().name() + " " + Msg.number(standings.getFirst().score()), ChatFormatting.YELLOW));
                }
                if (event.spec().participation() == EventSpec.Participation.OPT_IN) {
                    line.append(Msg.text(" · opt-in", ChatFormatting.AQUA));
                }
                lines.add(line);
            }
        }
        if (!EventManager.definedIds().isEmpty() && Commands.<CommandSourceStack>hasPermission(Commands.LEVEL_GAMEMASTERS).test(source)) {
            lines.add(Msg.text("Defined: ", ChatFormatting.GOLD).append(Msg.text(String.join(", ", EventManager.definedIds()), ChatFormatting.GRAY)));
        }
        lines.forEach(line -> source.sendSuccess(() -> line, false));
        return running.size();
    }

    /** JSON for scripts: one event's status, {"id":..,"phase":"none"} if it isn't running, or an array of all. */
    private static int status(CommandSourceStack source, String id) {
        String json;
        int result;
        if (id != null) {
            ActiveEvent event = EventManager.get(id).orElse(null);
            JsonObject none = new JsonObject();
            none.addProperty("id", id);
            none.addProperty("phase", "none");
            json = (event == null ? none : EventManager.status(event)).toString();
            result = event == null ? 0 : 1;
        } else {
            com.google.gson.JsonArray all = new com.google.gson.JsonArray();
            EventManager.running().forEach(event -> all.add(EventManager.status(event)));
            json = all.toString();
            result = all.size();
        }
        source.sendSuccess(() -> Component.literal(json), false);
        return result;
    }

    private static int join(CommandSourceStack source, String id, boolean joining) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        ActiveEvent event;
        if (id == null) {
            List<ActiveEvent> optIn = EventManager.running().stream()
                    .filter(e -> e.spec().participation() == EventSpec.Participation.OPT_IN).toList();
            if (optIn.size() == 1) {
                event = optIn.getFirst();
            } else if (optIn.isEmpty() && !EventManager.running().isEmpty()) {
                throw new IllegalArgumentException(joining ? "Everyone's already in the running events!" : "You can't leave an event everyone is in");
            } else {
                event = pick(null);
            }
        } else {
            event = pick(id);
        }
        if (event.spec().participation() == EventSpec.Participation.EVERYONE) {
            throw new IllegalArgumentException(joining ? "Everyone's already in " + event.spec().name() + "!" : "You can't leave an event everyone is in");
        }
        boolean already = event.hasJoined(player.getUUID());
        boolean changed = joining ? EventManager.join(event, player) : EventManager.leave(event, player);
        if (joining && !changed && !already) {
            return 0; // equipping failed and they were told why
        }
        String message = joining
                ? changed ? "You joined " + event.spec().name() + ". Good luck!" : "You're already in " + event.spec().name()
                : changed ? "You left " + event.spec().name() + "." : "You weren't in " + event.spec().name();
        source.sendSuccess(() -> Msg.text(message, changed ? ChatFormatting.GREEN : ChatFormatting.GRAY), false);
        return changed ? 1 : 0;
    }

    private static int start(CommandSourceStack source, String id, String duration) {
        EventSpec spec = EventManager.defined(id);
        if (duration != null) {
            spec = spec.withDuration(Json.seconds(duration));
        }
        return started(source, SkyRpgEvents.start(spec));
    }

    private static int run(CommandSourceStack source, String json) {
        EventSpec spec = EventManager.parse(Json.parseObject(json));
        spec = spec.withId(EventManager.freeId(spec.id()));
        return started(source, SkyRpgEvents.start(spec));
    }

    private static int started(CommandSourceStack source, ActiveEvent event) {
        source.sendSuccess(() -> Msg.text("Started " + event.spec().name() + " (id " + event.id() + ") for "
                + Json.clock(event.spec().durationSeconds()), ChatFormatting.GREEN), false);
        return 1;
    }

    private static int finish(CommandSourceStack source, String id, boolean award) {
        ActiveEvent event = pick(id);
        EventManager.finish(event, award, null);
        source.sendSuccess(() -> Msg.text((award ? "Ended " : "Cancelled ") + event.spec().name()
                + (award ? " and handed out prizes" : ""), ChatFormatting.GREEN), false);
        return 1;
    }

    // ---------------------------------------------------------------- /announce and /board

    /** Builds per viewer when the JSON mentions {player}, otherwise once. */
    private static <T> Function<ServerPlayer, T> perViewer(String json, Function<JsonObject, T> parse) {
        T once = parse.apply(Json.parseObject(json.replace("{player}", "Player")));
        if (!json.contains("{player}")) {
            return viewer -> once;
        }
        return viewer -> parse.apply(Json.parseObject(json.replace("{player}", viewer.getScoreboardName())));
    }

    private static int announce(CommandSourceStack source, Collection<ServerPlayer> targets, String json) {
        Function<ServerPlayer, Announcement> build = perViewer(json, object -> Announcement.parse(object, source.registryAccess()));
        Collection<ServerPlayer> players = targets == null ? source.getServer().getPlayerList().getPlayers() : targets;
        for (ServerPlayer player : players) {
            EventManager.announce(List.of(player), build.apply(player));
        }
        source.sendSuccess(() -> Msg.text("Announced to " + players.size() + (players.size() == 1 ? " player" : " players"), ChatFormatting.GREEN), false);
        return players.size();
    }

    private static int showBoard(CommandSourceStack source, Collection<ServerPlayer> targets, String json) {
        Function<ServerPlayer, Board> build = perViewer(json, object -> Board.parse(object, source.registryAccess()));
        int seconds = Board.parse(Json.parseObject(json), source.registryAccess()).seconds();
        Boards.show(source.getServer(), targets, build, seconds);
        String who = targets == null ? "everyone" : targets.size() + (targets.size() == 1 ? " player" : " players");
        source.sendSuccess(() -> Msg.text("Showing the board to " + who + (seconds > 0 ? " for " + Json.clock(seconds) : ""), ChatFormatting.GREEN), false);
        return 1;
    }

    private static int hideBoard(CommandSourceStack source, Collection<ServerPlayer> targets) {
        Boards.hide(source.getServer(), targets);
        source.sendSuccess(() -> Msg.text(targets == null ? "Took every board down" : "Hid the board for " + targets.size()
                + (targets.size() == 1 ? " player" : " players"), ChatFormatting.GREEN), false);
        return 1;
    }
}
