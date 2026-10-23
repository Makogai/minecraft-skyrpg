package com.skyrpg.events;

import com.skyrpg.events.contract.Contracts;
import com.skyrpg.events.net.EventPayloads;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Timed server events with live leaderboards, prizes and a winner board, plus banners and custom
 * sidebars. This class is the public API; call it on the server thread.
 *
 * <pre>{@code
 * EventSpec spec = EventSpec.builder("pumpkin_panic").name("Pumpkin Panic").color(0xE67E22)
 *         .icon(Identifier.withDefaultNamespace("carved_pumpkin")).duration(180).unit("pumpkins")
 *         .rule(ScoringRule.breakBlocks(1, "minecraft:pumpkin", "minecraft:carved_pumpkin"))
 *         .prize(Prize.coins(1, 250)).prize(Prize.coins(2, 100))
 *         .build();
 * SkyRpgEvents.start(spec);
 *
 * // An event with a custom scoring rule gets its points from your code:
 * SkyRpgEvents.addPoints(player, "fishing_frenzy", 3);
 * }</pre>
 * Starting, ending and scores are also on {@code /event}; see {@link EventCommands}.
 */
public class SkyRpgEvents implements ModInitializer {
    public static final String MOD_ID = "skyrpg_events";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
    /**
     * Players with this entity tag start gliding as soon as they're in the air (with an elytra on), so
     * scripts can turn it on with {@code /tag <player> add skyrpg.glide}. Events do it with {@code auto_glide}.
     */
    public static final String GLIDE_TAG = "skyrpg.glide";

    /** Hooks for mods that want to react to events. Every method is optional. */
    public interface Listener {
        default void started(ActiveEvent event) {
        }

        /** {@code delta} may be negative (points taken away or set lower). */
        default void scored(ActiveEvent event, ServerPlayer player, int delta, int total) {
        }

        /** @param awarded false if it was cancelled with /event stop */
        default void ended(ActiveEvent event, List<ActiveEvent.Standing> standings, boolean awarded) {
        }
    }

    @Override
    public void onInitialize() {
        EventPayloads.register();
        EventManager.init();
        AutoEvents.init();
        Seasons.init();
        Billboards.init();
        Contracts.init();
        CommandRegistrationCallback.EVENT.register((dispatcher, context, selection) -> {
            EventCommands.register(dispatcher);
            Seasons.register(dispatcher);
            Billboards.register(dispatcher);
            // /contracts: today's board; /contracts new: a fresh set now (admin).
            dispatcher.register(Commands.literal("contracts")
                    .executes(ctx -> {
                        ctx.getSource().sendSuccess(() -> Component.literal("Today's contracts: " + String.join(", ", Contracts.names())), false);
                        return 1;
                    })
                    .then(Commands.literal("new").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS)).executes(ctx -> {
                        Contracts.newDay();
                        ctx.getSource().sendSuccess(() -> Component.literal("New contracts: " + String.join(", ", Contracts.names())), true);
                        return 1;
                    })));
        });
    }

    public static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(MOD_ID, path);
    }

    // ---------------------------------------------------------------- events

    /**
     * Starts an event now and tells everyone.
     *
     * @throws IllegalStateException if one with the same id is running (see {@link #freeId})
     */
    public static ActiveEvent start(EventSpec spec) {
        return EventManager.start(spec);
    }

    /** Starts an event from a data file ({@code data/<ns>/skyrpg/events/<id>.json}). */
    public static ActiveEvent start(String definedId) {
        return EventManager.start(EventManager.defined(definedId));
    }

    /** The spec in a data file, to tweak before starting (e.g. {@code .withDuration(60)}). */
    public static EventSpec defined(String definedId) {
        return EventManager.defined(definedId);
    }

    /** Ids of every event defined in data files. */
    public static Set<String> definedIds() {
        return EventManager.definedIds();
    }

    /** {@code id}, or {@code id_2}, {@code id_3}... if an event with that id is running. */
    public static String freeId(String id) {
        return EventManager.freeId(id);
    }

    /** Running events, oldest first. */
    public static Collection<ActiveEvent> active() {
        return EventManager.running();
    }

    public static Optional<ActiveEvent> get(String eventId) {
        return EventManager.get(eventId);
    }

    /** Ends it now and hands out prizes, as if time ran out. @return false if it isn't running */
    public static boolean end(String eventId) {
        return get(eventId).map(event -> {
            EventManager.finish(event, true, null);
            return true;
        }).orElse(false);
    }

    /** Cancels it: no prizes, no winner board. @return false if it isn't running */
    public static boolean stop(String eventId) {
        return get(eventId).map(event -> {
            EventManager.finish(event, false, null);
            return true;
        }).orElse(false);
    }

    /** Makes it end {@code seconds} from now. @return false if it isn't running */
    public static boolean setTimeLeft(String eventId, int seconds) {
        return get(eventId).map(event -> {
            EventManager.setTimeLeft(event, Math.max(1, seconds));
            return true;
        }).orElse(false);
    }

    // ---------------------------------------------------------------- scores

    /**
     * Gives points (negative takes them away). Only counts if the player takes part (always for
     * "everyone" events, after /event join for opt-in ones) and the join window is over.
     *
     * @return false if the event isn't running yet or at all, or the player isn't in it
     */
    public static boolean addPoints(ServerPlayer player, String eventId, int points) {
        Optional<ActiveEvent> event = get(eventId);
        if (event.isEmpty() || event.get().inLobby() || !event.get().participates(player)) {
            return false;
        }
        EventManager.addPoints(event.get(), player, points);
        return true;
    }

    /** Sets the score outright, joining them to an opt-in event if needed. @return false if it isn't running */
    public static boolean setPoints(ServerPlayer player, String eventId, int points) {
        Optional<ActiveEvent> event = get(eventId);
        if (event.isEmpty()) {
            return false;
        }
        if (!event.get().participates(player)) {
            EventManager.join(event.get(), player);
        }
        EventManager.setPoints(event.get(), player, points);
        return true;
    }

    /** 0 if they haven't scored or the event isn't running. */
    public static int points(ServerPlayer player, String eventId) {
        return get(eventId).map(event -> event.score(player.getUUID())).orElse(0);
    }

    /**
     * Opts a player in (and, once it's begun, stashes their inventory, gives the kit and so on).
     *
     * @return false if the event isn't running, they were already in, or their inventory couldn't be stashed
     */
    public static boolean join(ServerPlayer player, String eventId) {
        return get(eventId).map(event -> EventManager.join(event, player)).orElse(false);
    }

    public static boolean leave(ServerPlayer player, String eventId) {
        return get(eventId).map(event -> EventManager.leave(event, player)).orElse(false);
    }

    public static void listen(Listener listener) {
        EventManager.LISTENERS.add(listener);
    }

    // ---------------------------------------------------------------- banners and boards

    /** Shows a banner (and plays its sound) to these players. */
    public static void announce(Collection<ServerPlayer> players, Announcement announcement) {
        EventManager.announce(players, announcement);
    }

    public static void announceAll(MinecraftServer server, Announcement announcement) {
        EventManager.announce(server.getPlayerList().getPlayers(), announcement);
    }

    /**
     * Shows a custom sidebar in place of the scoreboard one.
     *
     * @param players null = everyone, including players who join later
     */
    public static void showBoard(MinecraftServer server, Collection<ServerPlayer> players, Board board) {
        Boards.show(server, players, viewer -> board, board.seconds());
    }

    /** @param players null = take every board down */
    public static void hideBoard(MinecraftServer server, Collection<ServerPlayer> players) {
        Boards.hide(server, players);
    }
}
