package com.skyrpg.events;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.skyrpg.core.data.JsonDataLoader;
import com.skyrpg.core.text.Msg;
import com.skyrpg.core.text.Notice;
import com.skyrpg.core.action.Catches;
import com.skyrpg.core.world.PlacedBlocks;
import com.skyrpg.events.net.EventPayloads;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Predicate;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSource;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.HolderLookup;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;

/** Runs events: the clock, scoring hooks, prizes and keeping clients in sync. Use it through {@link SkyRpgEvents}. */
final class EventManager {
    static final String DIRECTORY = "skyrpg/events";
    private static final int SYNC_EVERY_TICKS = 20;
    /** Score changes are sent this soon at the earliest, so a chain of kills doesn't send a packet each. */
    private static final int MIN_SYNC_TICKS = 3;
    private static final int TOP = 10;
    /** Join-window reminders, in seconds before the start. */
    private static final int[] LOBBY_REMINDERS = {30, 15, 10};
    private static final Map<String, JsonObject> DEFINED = new TreeMap<>();
    private static final Map<String, ActiveEvent> RUNNING = new LinkedHashMap<>();
    static final List<SkyRpgEvents.Listener> LISTENERS = new CopyOnWriteArrayList<>();
    /** Players who just connected; handled on the next tick, once they're fully in the world. */
    private static final Set<UUID> JUST_JOINED = new HashSet<>();
    /** Prize commands report here instead of in chat. */
    private static final CommandSource PRIZE_LOG = new CommandSource() {
        @Override
        public void sendSystemMessage(Component message) {
            SkyRpgEvents.LOGGER.info("[prize] {}", message.getString());
        }

        @Override
        public boolean acceptsSuccess() {
            return true;
        }

        @Override
        public boolean acceptsFailure() {
            return true;
        }

        @Override
        public boolean shouldInformAdmins() {
            return false;
        }
    };
    private static MinecraftServer server;
    private static boolean dirty;
    private static int ticksSinceSync;

    private EventManager() {
    }

    static void init() {
        JsonDataLoader.register(SkyRpgEvents.id("events"), DIRECTORY, EventManager::load);
        ServerLifecycleEvents.SERVER_STARTED.register(started -> server = started);
        ServerLifecycleEvents.SERVER_STOPPING.register(stopping -> {
            // Stashed inventories stay on disk and come back when their owners next join.
            RUNNING.clear();
            JUST_JOINED.clear();
            Boards.clear();
            server = null;
        });
        ServerTickEvents.END_SERVER_TICK.register(EventManager::tick);
        ServerPlayConnectionEvents.JOIN.register((handler, sender, joined) -> {
            server = joined;
            JUST_JOINED.add(handler.player.getUUID());
        });
        Players.init();

        PlayerBlockBreakEvents.AFTER.register((level, player, pos, state, blockEntity) -> {
            if (player instanceof ServerPlayer serverPlayer && !PlacedBlocks.isPlaced(level, pos)) {
                score(serverPlayer, rule -> rule.matches(state));
            }
        });
        ServerLivingEntityEvents.AFTER_DEATH.register((entity, damageSource) -> {
            if (damageSource.getEntity() instanceof ServerPlayer killer && entity != killer) {
                score(killer, rule -> rule.matches(entity));
            }
        });
        Catches.CAUGHT.register((player, stack, seaCreature) -> score(player, rule -> rule.matchesCatch(seaCreature)));
    }

    static MinecraftServer server() {
        return server;
    }

    // ---------------------------------------------------------------- data files

    private static void load(Map<Identifier, JsonElement> files) {
        DEFINED.clear();
        files.forEach((file, json) -> {
            String id = Json.slug(file.getPath());
            try {
                JsonObject object = json.getAsJsonObject();
                EventSpec.parse(object, id, null);
                if (DEFINED.put(id, object) != null) {
                    SkyRpgEvents.LOGGER.warn("Two event files are called '{}'; using {}", id, file);
                }
            } catch (RuntimeException e) {
                SkyRpgEvents.LOGGER.error("Skipping event file {}: {}", file, e.getMessage());
            }
        });
        SkyRpgEvents.LOGGER.info("Loaded {} event definitions", DEFINED.size());
    }

    static Set<String> definedIds() {
        return DEFINED.keySet();
    }

    static EventSpec defined(String id) {
        JsonObject json = DEFINED.get(id);
        if (json == null) {
            throw new IllegalArgumentException("No event called '" + id + "' (known: " + String.join(", ", DEFINED.keySet()) + ")");
        }
        return EventSpec.parse(json, id, registries());
    }

    /**
     * Reads ad hoc JSON. {@code from: "timber_rush"} starts from a data file's spec and overrides only
     * the fields given.
     */
    static EventSpec parse(JsonObject json) {
        JsonElement from = Json.get(json, "from", "extends", "base");
        if (from == null) {
            return EventSpec.parse(json, null, registries());
        }
        String baseId = Json.slug(from.getAsString());
        JsonObject base = DEFINED.get(baseId);
        if (base == null) {
            throw new IllegalArgumentException("No event called '" + baseId + "' to start from");
        }
        JsonObject merged = base.deepCopy();
        json.entrySet().forEach(entry -> merged.add(entry.getKey(), entry.getValue()));
        merged.remove("from");
        merged.remove("extends");
        merged.remove("base");
        return EventSpec.parse(merged, json.has("id") ? null : baseId, registries());
    }

    private static HolderLookup.Provider registries() {
        return server == null ? null : server.registryAccess();
    }

    // ---------------------------------------------------------------- running

    static Collection<ActiveEvent> running() {
        return RUNNING.values();
    }

    static Optional<ActiveEvent> get(String id) {
        return Optional.ofNullable(RUNNING.get(id));
    }

    /** {@code id}, or {@code id_2}, {@code id_3}... if that's taken. */
    static String freeId(String id) {
        if (!RUNNING.containsKey(id)) {
            return id;
        }
        int n = 2;
        while (RUNNING.containsKey(id + "_" + n)) {
            n++;
        }
        return id + "_" + n;
    }

    static ActiveEvent start(EventSpec spec) {
        if (server == null) {
            throw new IllegalStateException("The server isn't running");
        }
        if (RUNNING.containsKey(spec.id())) {
            throw new IllegalStateException("'" + spec.id() + "' is already running");
        }
        ActiveEvent event = new ActiveEvent(spec.id(), spec, System.currentTimeMillis());
        RUNNING.put(event.id(), event);
        announceStart(event);
        LISTENERS.forEach(listener -> listener.started(event));
        if (spec.setup().lobbySeconds() <= 0) {
            begin(event, false);
        }
        sync();
        SkyRpgEvents.LOGGER.info("Event {} started for {}s (join window {}s)", event.id(), spec.durationSeconds(), spec.setup().lobbySeconds());
        return event;
    }

    /** The join window is over (or there was none): the clock runs and players get their setup. */
    private static void begin(ActiveEvent event, boolean afterLobby) {
        event.begun = true;
        event.lastCountdown = Integer.MAX_VALUE;
        EventSpec spec = event.spec();
        if (afterLobby && spec.participation() == EventSpec.Participation.OPT_IN && event.participantCount() == 0) {
            finish(event, false, "Nobody joined " + spec.name() + ". Maybe next time!");
            return;
        }
        for (ServerPlayer player : audience(event)) {
            if (!Players.equip(server, event, player)) {
                event.leave(player);
            }
        }
        dirty = true;
        if (afterLobby) {
            Announcement go = new Announcement("GO!", Component.literal(spec.name()),
                    Component.literal(spec.description().isEmpty() ? describeRules(spec) : spec.description())
                            .append(Msg.text("  ·  " + Json.clock(spec.durationSeconds()), ChatFormatting.DARK_GRAY)),
                    spec.color(), spec.icon(), Identifier.withDefaultNamespace("entity.firework_rocket.launch"), 1, 1, false);
            announce(audience(event), go);
            Component line = Notice.of(Notice.Kind.EVENT).tag("STARTED").color(rgb(spec.color())).build(Component.literal(spec.name()).withColor(rgb(spec.color()))
                    .append(Msg.text(" has started! " + Json.clock(spec.durationSeconds()) + " on the clock.", ChatFormatting.GRAY)));
            server.getPlayerList().getPlayers().forEach(player -> player.sendSystemMessage(line));
        }
    }

    static boolean join(ActiveEvent event, ServerPlayer player) {
        if (!event.join(player)) {
            return false;
        }
        if (event.begun && !Players.equip(server, event, player)) {
            event.leave(player);
            return false;
        }
        dirty = true;
        Msg.playSound(player, SoundEvents.EXPERIENCE_ORB_PICKUP, 0.6f, 1.4f);
        Component line = Players.joinedLine(event, player);
        server.getPlayerList().getPlayers().stream().filter(other -> other != player).forEach(other -> other.sendSystemMessage(line));
        return true;
    }

    static boolean leave(ActiveEvent event, ServerPlayer player) {
        boolean left = event.leave(player);
        if (left) {
            Players.unequip(server, event, player);
            dirty = true;
        }
        return left;
    }

    /** @return the new score */
    static int addPoints(ActiveEvent event, ServerPlayer player, int points) {
        int total = event.add(player, points);
        dirty = true;
        LISTENERS.forEach(listener -> listener.scored(event, player, points, total));
        return total;
    }

    static void setPoints(ActiveEvent event, ServerPlayer player, int points) {
        int before = event.score(player.getUUID());
        event.set(player, points);
        dirty = true;
        LISTENERS.forEach(listener -> listener.scored(event, player, points - before, points));
    }

    static void setTimeLeft(ActiveEvent event, int seconds) {
        if (event.inLobby()) {
            throw new IllegalStateException(event.spec().name() + " is still in its join window");
        }
        event.setEndsAt(System.currentTimeMillis() + seconds * 1000L);
        dirty = true;
        Component line = Notice.of(Notice.Kind.EVENT).tag("TIME").build(Component.literal(event.spec().name()).withColor(rgb(event.spec().color()))
                .append(Msg.text(" now ends in " + Json.clock(seconds) + ".", ChatFormatting.GRAY)));
        audience(event).forEach(player -> player.sendSystemMessage(line));
    }

    private static void score(ServerPlayer player, Predicate<ScoringRule> matches) {
        if (RUNNING.isEmpty() || player.isSpectator()) {
            return;
        }
        for (ActiveEvent event : List.copyOf(RUNNING.values())) {
            if (event.inLobby() || !event.participates(player) || (player.isCreative() && !event.spec().allowCreative())) {
                continue;
            }
            int points = 0;
            for (ScoringRule rule : event.spec().rules()) {
                if (matches.test(rule)) {
                    points += rule.points();
                }
            }
            if (points != 0) {
                addPoints(event, player, points);
            }
        }
    }

    private static void tick(MinecraftServer ticking) {
        server = ticking;
        long now = System.currentTimeMillis();
        if (!JUST_JOINED.isEmpty()) {
            for (UUID id : List.copyOf(JUST_JOINED)) {
                ServerPlayer player = server.getPlayerList().getPlayer(id);
                if (player != null) {
                    Players.onJoin(server, player, RUNNING.values());
                    send(player, state(List.of(player)).get(id));
                    Boards.onJoin(player);
                }
            }
            JUST_JOINED.clear();
        }
        for (ActiveEvent event : List.copyOf(RUNNING.values())) {
            if (event.inLobby()) {
                lobbyTick(event, now);
                continue;
            }
            int left = event.secondsLeft(now);
            List<EventSpec.Cue> cues = event.spec().cues();
            for (int i = 0; i < cues.size(); i++) {
                if (cues.get(i).secondsLeft() >= left && event.cuesDone.add(i)) {
                    say(event, cues.get(i), left);
                }
            }
            if (event.spec().countdown() && left >= 1 && left <= 5 && left < event.lastCountdown) {
                event.lastCountdown = left;
                countdown(event, event.spec().name() + " ends in ", left, audience(event));
            }
            if (now >= event.endsAt()) {
                finish(event, true, null);
            }
        }
        Players.glide(server, RUNNING.values());
        Boards.tick(now);
        ticksSinceSync++;
        if ((!RUNNING.isEmpty() && ticksSinceSync >= SYNC_EVERY_TICKS) || (dirty && ticksSinceSync >= MIN_SYNC_TICKS)) {
            sync();
        }
    }

    private static void lobbyTick(ActiveEvent event, long now) {
        long untilStart = Math.max(0, event.beginsAt() - now);
        int seconds = (int) ((untilStart + 999) / 1000);
        if (untilStart == 0) {
            begin(event, true);
            return;
        }
        for (int reminder : LOBBY_REMINDERS) {
            if (seconds <= reminder && reminder < event.spec().setup().lobbySeconds() && event.lobbyCuesDone.add(reminder)) {
                MutableComponent line = Notice.of(Notice.Kind.EVENT).tag("STARTING").build(Component.literal(event.spec().name()).withColor(rgb(event.spec().color()))
                        .append(Msg.text(" starts in " + seconds + "s. ", ChatFormatting.LIGHT_PURPLE)));
                if (event.spec().participation() == EventSpec.Participation.OPT_IN) {
                    line.append(joinButton(event));
                }
                server.getPlayerList().getPlayers().forEach(player -> player.sendSystemMessage(line));
            }
        }
        if (seconds <= 5 && seconds < event.lastCountdown) {
            event.lastCountdown = seconds;
            countdown(event, event.spec().name() + " starts in ", seconds, audience(event));
        }
    }

    /**
     * Ends an event. With {@code award}, prizes are handed out and everyone sees the winner board;
     * without, it's cancelled ({@code reason} replaces the usual "was cancelled" line).
     */
    static void finish(ActiveEvent event, boolean award, String reason) {
        if (RUNNING.remove(event.id()) == null) {
            return;
        }
        dirty = true;
        EventSpec spec = event.spec();
        List<ActiveEvent.Standing> standings = event.standings();
        List<ServerPlayer> online = server == null ? List.of() : server.getPlayerList().getPlayers();
        // Inventories back first; anyone offline gets theirs when they next join.
        for (UUID id : List.copyOf(event.equipped)) {
            ServerPlayer player = server == null ? null : server.getPlayerList().getPlayer(id);
            if (player != null) {
                Players.unequip(server, event, player);
            }
        }
        if (!award) {
            Component line = Notice.of(Notice.Kind.EVENT).icon(Notice.CROSS).tag("CANCELLED").color(0xFF5555).build(reason != null ? Msg.text(reason, ChatFormatting.GRAY)
                    : Component.literal(spec.name()).withColor(rgb(spec.color()))
                            .append(Msg.text(" was cancelled. No prizes this time.", ChatFormatting.GRAY)));
            online.forEach(player -> {
                player.sendSystemMessage(line);
                Msg.playSound(player, SoundEvents.NOTE_BLOCK_BASS.value(), 0.8f, 0.6f);
            });
            LISTENERS.forEach(listener -> listener.ended(event, standings, false));
            SkyRpgEvents.LOGGER.info("Event {} cancelled", event.id());
            sync();
            return;
        }

        Map<UUID, String> won = new HashMap<>();
        for (ActiveEvent.Standing standing : standings) {
            if (standing.place() <= 0) {
                continue;
            }
            List<Prize> prizes = new ArrayList<>(spec.prizesFor(standing.place()));
            prizes.addAll(spec.prizesFor(Prize.EVERYONE));
            for (Prize prize : prizes) {
                for (String command : prize.commands()) {
                    runCommand(command.replace("{player}", standing.name())
                            .replace("{place}", Integer.toString(standing.place()))
                            .replace("{score}", Integer.toString(standing.score()))
                            .replace("{event}", event.id()));
                }
            }
            String label = spec.prizeLabel(standing.place());
            if (!label.isEmpty()) {
                won.put(standing.player(), label);
            }
        }

        List<EventPayloads.Placing> podium = standings.stream()
                .filter(standing -> standing.place() >= 1 && standing.place() <= 3)
                .limit(9)
                .map(standing -> new EventPayloads.Placing(standing.place(), standing.player(), standing.name(), standing.score(),
                        won.getOrDefault(standing.player(), "")))
                .toList();
        List<Component> summary = summary(event, standings, won);
        for (ServerPlayer player : online) {
            ActiveEvent.Standing self = standings.stream().filter(s -> s.player().equals(player.getUUID())).findFirst().orElse(null);
            int place = self == null ? 0 : self.place();
            int score = self == null ? 0 : self.score();
            String prize = won.getOrDefault(player.getUUID(), "");
            send(player, new EventPayloads.Results(event.id(), spec.name(), spec.color(), spec.icon(), spec.unit(), event.participantCount(),
                    podium, place, score, prize));
            summary.forEach(player::sendSystemMessage);
            player.sendSystemMessage(selfLine(spec, self, prize));
        }
        LISTENERS.forEach(listener -> listener.ended(event, standings, true));
        SkyRpgEvents.LOGGER.info("Event {} ended: {}", event.id(), standings.stream().limit(5)
                .map(s -> s.name() + " " + s.score()).toList());
        sync();
    }

    private static void runCommand(String command) {
        if (server == null) {
            return;
        }
        CommandSourceStack source = server.createCommandSourceStack().withSource(PRIZE_LOG);
        server.getCommands().performPrefixedCommand(source, command);
    }

    // ---------------------------------------------------------------- what players see and hear

    /** Who hears about an event while it runs: everyone, or only those who joined. */
    private static List<ServerPlayer> audience(ActiveEvent event) {
        if (server == null) {
            return List.of();
        }
        return server.getPlayerList().getPlayers().stream().filter(event::participates).toList();
    }

    private static Component joinButton(ActiveEvent event) {
        String hint = event.spec().setup().chatJoin() ? "Type .join or click: " : "";
        return Msg.text(hint, ChatFormatting.GRAY).append(Msg.text("[Join]", ChatFormatting.GREEN, ChatFormatting.BOLD)
                .withStyle(style -> style.withClickEvent(new ClickEvent.RunCommand("/event join " + event.id()))
                        .withHoverEvent(new HoverEvent.ShowText(Msg.text("Join " + event.spec().name(), ChatFormatting.GREEN)))));
    }

    private static void announceStart(ActiveEvent event) {
        EventSpec spec = event.spec();
        int lobby = spec.setup().lobbySeconds();
        boolean optIn = spec.participation() == EventSpec.Participation.OPT_IN;
        String rules = describeRules(spec);
        MutableComponent subtitle = Component.literal(lobby > 0 && optIn
                ? (spec.setup().chatJoin() ? "Type .join to play" : "Type /event join to play") + " · starts in " + Json.clock(lobby)
                : spec.description().isEmpty() ? rules : spec.description());
        subtitle.append(Msg.text("  ·  " + Json.clock(spec.durationSeconds()), ChatFormatting.DARK_GRAY));
        String label = lobby > 0 ? (optIn ? "EVENT · JOIN NOW" : "EVENT STARTING SOON") : optIn ? "EVENT STARTED · /event join" : "EVENT STARTED";
        Announcement banner = new Announcement(label, Component.literal(spec.name()), subtitle, spec.color(), spec.icon(),
                Identifier.withDefaultNamespace("block.bell.use"), 0.7f, 1.2f, false);

        List<Component> chat = new ArrayList<>();
        chat.add(Notice.of(Notice.Kind.EVENT).tag(lobby > 0 ? "STARTING SOON" : "STARTED").color(rgb(spec.color()))
                .build(Component.literal(spec.name()).withStyle(ChatFormatting.WHITE, ChatFormatting.BOLD)
                .append(Msg.text("  " + Json.clock(spec.durationSeconds()) + (lobby > 0 ? ", starts in " + Json.clock(lobby) : ""), ChatFormatting.DARK_GRAY))));
        if (!spec.description().isEmpty()) {
            chat.add(Msg.text("  " + spec.description(), ChatFormatting.GRAY));
        }
        if (!rules.isEmpty()) {
            chat.add(Msg.text("  " + rules, ChatFormatting.DARK_GRAY));
        }
        Component prizes = prizesLine(spec);
        if (prizes != null) {
            chat.add(prizes);
        }
        if (spec.setup().stashInventory()) {
            chat.add(Msg.text("  Your inventory is put away safely while you play and given back after.", ChatFormatting.DARK_GRAY));
        }
        if (optIn) {
            chat.add(Msg.text("  ", ChatFormatting.GRAY).append(joinButton(event)));
        }
        chat.add(Component.empty());

        List<ServerPlayer> players = server.getPlayerList().getPlayers();
        announce(players, banner);
        players.forEach(player -> chat.forEach(player::sendSystemMessage));
    }

    private static String describeRules(EventSpec spec) {
        List<String> parts = new ArrayList<>();
        for (ScoringRule rule : spec.rules()) {
            String text = rule.describe();
            if (!text.isEmpty()) {
                parts.add(text);
            }
        }
        return String.join(" · ", parts);
    }

    private static Component prizesLine(EventSpec spec) {
        if (spec.prizes().isEmpty()) {
            return null;
        }
        MutableComponent line = Msg.text("  Prizes: ", ChatFormatting.GRAY);
        List<Prize> sorted = spec.prizes().stream()
                .sorted(Comparator.comparingInt(prize -> prize.place() == Prize.EVERYONE ? Integer.MAX_VALUE : prize.place())).toList();
        for (int i = 0; i < sorted.size(); i++) {
            Prize prize = sorted.get(i);
            if (i > 0) {
                line.append(Msg.text(" · ", ChatFormatting.DARK_GRAY));
            }
            line.append(Msg.text(prize.placeName() + " ", ChatFormatting.WHITE))
                    .append(Msg.text(prize.label().isEmpty() ? "a surprise" : prize.label(), ChatFormatting.GOLD));
        }
        return line;
    }

    private static void say(ActiveEvent event, EventSpec.Cue cue, int left) {
        List<ServerPlayer> players = audience(event);
        if (cue.announcement() != null) {
            announce(players, cue.announcement());
            return;
        }
        String when = left >= 60 && left % 60 == 0 ? (left / 60) + (left == 60 ? " minute" : " minutes") : left + (left == 1 ? " second" : " seconds");
        Component line = Notice.of(Notice.Kind.EVENT).tag("TIME").build(Component.literal(event.spec().name()).withColor(rgb(event.spec().color()))
                .append(Msg.text(": " + when + " left!", ChatFormatting.YELLOW)));
        float pitch = left <= 10 ? 1.4f : left <= 30 ? 1.2f : 1.0f;
        for (ServerPlayer player : players) {
            player.sendSystemMessage(line);
            player.sendOverlayMessage(line);
            Msg.playSound(player, SoundEvents.NOTE_BLOCK_BELL.value(), 0.9f, pitch);
        }
    }

    private static void countdown(ActiveEvent event, String text, int left, List<ServerPlayer> players) {
        Component line = Component.literal(text).withStyle(ChatFormatting.GRAY)
                .append(Component.literal(Integer.toString(left)).withStyle(left <= 3 ? ChatFormatting.RED : ChatFormatting.GOLD, ChatFormatting.BOLD));
        for (ServerPlayer player : players) {
            player.sendOverlayMessage(line);
            Msg.playSound(player, SoundEvents.NOTE_BLOCK_HAT.value(), 0.7f, 0.9f + (5 - left) * 0.12f);
        }
    }

    private static List<Component> summary(ActiveEvent event, List<ActiveEvent.Standing> standings, Map<UUID, String> won) {
        EventSpec spec = event.spec();
        List<Component> lines = new ArrayList<>();
        lines.add(Notice.of(Notice.Kind.EVENT).tag("RESULTS").color(rgb(spec.color()))
                .build(Component.literal(spec.name()).withStyle(ChatFormatting.BOLD).withColor(rgb(spec.color()))
                .append(Msg.text(" is over", ChatFormatting.WHITE))));
        int shown = 0;
        for (ActiveEvent.Standing standing : standings) {
            if (standing.place() <= 0 || standing.place() > 3) {
                continue;
            }
            ChatFormatting medal = switch (standing.place()) {
                case 1 -> ChatFormatting.GOLD;
                case 2 -> ChatFormatting.GRAY;
                default -> ChatFormatting.RED;
            };
            MutableComponent line = Msg.text("  " + Json.ordinal(standing.place()) + "  ", medal, ChatFormatting.BOLD)
                    .append(Msg.text(standing.name(), ChatFormatting.WHITE))
                    .append(Msg.text("  " + Msg.number(standing.score()) + " " + spec.unit(), ChatFormatting.GRAY));
            String prize = won.get(standing.player());
            if (prize != null) {
                line.append(Msg.text("  +" + prize, ChatFormatting.GREEN));
            }
            lines.add(line);
            shown++;
        }
        if (shown == 0) {
            lines.add(Msg.text("  Nobody scored, so nobody wins.", ChatFormatting.GRAY));
        }
        return lines;
    }

    private static Component selfLine(EventSpec spec, ActiveEvent.Standing self, String prize) {
        MutableComponent line;
        if (self == null || self.place() <= 0) {
            line = Msg.text("  You didn't score this time.", ChatFormatting.DARK_GRAY);
        } else {
            line = Msg.text("  You placed ", ChatFormatting.GRAY).append(Msg.text(Json.ordinal(self.place()), ChatFormatting.YELLOW))
                    .append(Msg.text(" with " + Msg.number(self.score()) + " " + spec.unit() + ".", ChatFormatting.GRAY));
            if (!prize.isEmpty()) {
                line.append(Msg.text(" You won " + prize + "!", ChatFormatting.GREEN));
            }
        }
        return line;
    }

    static void announce(Collection<ServerPlayer> players, Announcement announcement) {
        EventPayloads.Announce payload = new EventPayloads.Announce(announcement.label(), announcement.title(), announcement.subtitle(),
                announcement.color(), announcement.icon(), Optional.ofNullable(announcement.sound()), announcement.volume(), announcement.pitch());
        MutableComponent chat = null;
        if (announcement.chat()) {
            // Siblings of an empty root, so the label's bold colour doesn't spill into the title.
            MutableComponent body = Component.empty().append(Component.empty().withStyle(ChatFormatting.WHITE).append(announcement.title()));
            if (!announcement.subtitle().getString().isEmpty()) {
                body.append(Component.empty().withStyle(ChatFormatting.GRAY).append("  ").append(announcement.subtitle()));
            }
            Notice notice = Notice.of(Notice.Kind.EVENT).color(rgb(announcement.color()));
            if (!announcement.label().isEmpty()) {
                notice.tag(announcement.label());
            }
            chat = notice.build(body);
        }
        for (ServerPlayer player : players) {
            send(player, payload);
            if (chat != null) {
                player.sendSystemMessage(chat);
            }
        }
    }

    // ---------------------------------------------------------------- status for scripts

    /** One line of JSON about an event, for scripts and tools: phase, clock, players and scores. */
    static JsonObject status(ActiveEvent event) {
        long now = System.currentTimeMillis();
        JsonObject json = new JsonObject();
        json.addProperty("id", event.id());
        json.addProperty("name", event.spec().name());
        json.addProperty("phase", event.inLobby() ? "lobby" : "running");
        json.addProperty("starts_in", event.inLobby() ? (int) ((Math.max(0, event.beginsAt() - now) + 999) / 1000) : 0);
        json.addProperty("seconds_left", event.secondsLeft(now));
        json.addProperty("opt_in", event.spec().participation() == EventSpec.Participation.OPT_IN);
        JsonArray players = new JsonArray();
        event.participantNames().forEach(players::add);
        if (event.spec().participation() == EventSpec.Participation.EVERYONE && server != null) {
            Set<String> listed = new HashSet<>(event.participantNames());
            server.getPlayerList().getPlayers().stream().map(ServerPlayer::getScoreboardName).filter(name -> !listed.contains(name)).forEach(players::add);
        }
        json.add("players", players);
        JsonObject scores = new JsonObject();
        event.standings().forEach(standing -> scores.addProperty(standing.name(), standing.score()));
        json.add("scores", scores);
        return json;
    }

    // ---------------------------------------------------------------- sync

    private static void sync() {
        dirty = false;
        ticksSinceSync = 0;
        if (server == null) {
            return;
        }
        List<ServerPlayer> players = server.getPlayerList().getPlayers();
        Map<UUID, EventPayloads.State> states = state(players);
        for (ServerPlayer player : players) {
            send(player, states.get(player.getUUID()));
        }
    }

    /** What each player should see; standings are worked out once per event, not once per player. */
    private static Map<UUID, EventPayloads.State> state(List<ServerPlayer> players) {
        long now = System.currentTimeMillis();
        List<ActiveEvent> events = RUNNING.values().stream().sorted(Comparator.comparingLong(ActiveEvent::endsAt)).toList();
        List<List<ActiveEvent.Standing>> standings = events.stream().map(ActiveEvent::standings).toList();
        Map<UUID, EventPayloads.State> out = new HashMap<>();
        for (ServerPlayer player : players) {
            List<EventPayloads.EventView> views = new ArrayList<>(events.size());
            for (int i = 0; i < events.size(); i++) {
                ActiveEvent event = events.get(i);
                EventSpec spec = event.spec();
                List<ActiveEvent.Standing> list = standings.get(i);
                List<EventPayloads.Entry> top = list.stream().limit(TOP)
                        .map(s -> new EventPayloads.Entry(s.player(), s.name(), s.score(), s.place())).toList();
                ActiveEvent.Standing self = list.stream().filter(s -> s.player().equals(player.getUUID())).findFirst().orElse(null);
                views.add(new EventPayloads.EventView(event.id(), spec.name(),
                        spec.description().isEmpty() ? describeRules(spec) : spec.description(),
                        spec.color(), spec.icon(), spec.unit(), event.beginsAt(), event.endsAt(),
                        spec.participation() == EventSpec.Participation.OPT_IN, spec.setup().chatJoin(), event.participates(player),
                        event.participantCount(), top, self == null ? 0 : self.score(), self == null ? 0 : self.place()));
            }
            out.put(player.getUUID(), new EventPayloads.State(now, views));
        }
        return out;
    }

    static void send(ServerPlayer player, CustomPacketPayload payload) {
        if (payload != null && ServerPlayNetworking.canSend(player, payload.type())) {
            ServerPlayNetworking.send(player, payload);
        }
    }

    private static int rgb(int argb) {
        return argb & 0xFFFFFF;
    }
}
