package com.skyrpg.events;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.server.level.ServerPlayer;

/** A running event: its spec, clock and scores. Get them from {@link SkyRpgEvents#active()}. */
public final class ActiveEvent {
    /**
     * One row of the leaderboard. Tied scores share a place (two firsts, then third). Players on 0 or
     * fewer points are listed with place 0 and win nothing.
     */
    public record Standing(UUID player, String name, int score, int place) {
    }

    private final String id;
    private final EventSpec spec;
    private final long createdAt;
    private final long beginsAt;
    private long endsAt;
    private final Map<UUID, Integer> scores = new HashMap<>();
    private final Map<UUID, String> names = new HashMap<>();
    private final Set<UUID> joined = new HashSet<>();
    /** Cues already said, by index into the spec's cue list. */
    final Set<Integer> cuesDone = new HashSet<>();
    /** Join-window reminders already said, by seconds before the start. */
    final Set<Integer> lobbyCuesDone = new HashSet<>();
    int lastCountdown = Integer.MAX_VALUE;
    /** The clock has started (the join window, if any, is over). */
    boolean begun;
    /** Players who got the event's setup (stash, kit, teleport) and need it undone at the end. */
    final Set<UUID> equipped = new HashSet<>();
    /** Where to send equipped players back to when their inventory wasn't stashed (the stash file has it otherwise). */
    final Map<UUID, EventSetup.Location> returnTo = new HashMap<>();

    ActiveEvent(String id, EventSpec spec, long now) {
        this.id = id;
        this.spec = spec;
        this.createdAt = now;
        this.beginsAt = now + spec.setup().lobbySeconds() * 1000L;
        this.endsAt = beginsAt + spec.durationSeconds() * 1000L;
        // Cues that would already have passed (an event shorter than the cue) never fire.
        for (int i = 0; i < spec.cues().size(); i++) {
            if (spec.cues().get(i).secondsLeft() >= spec.durationSeconds()) {
                cuesDone.add(i);
            }
        }
    }

    /** Unique among running events; what /event commands take. Usually the spec's id. */
    public String id() {
        return id;
    }

    public EventSpec spec() {
        return spec;
    }

    /** {@link System#currentTimeMillis()} when it was announced. */
    public long createdAt() {
        return createdAt;
    }

    /** When the clock starts, after the join window ({@link EventSetup#lobbySeconds()}). */
    public long beginsAt() {
        return beginsAt;
    }

    /** Still in the join window: players can join but nobody scores yet. */
    public boolean inLobby() {
        return !begun;
    }

    public long endsAt() {
        return endsAt;
    }

    void setEndsAt(long endsAt) {
        this.endsAt = endsAt;
        // Moving the end can bring cues back into range or skip past them.
        int left = secondsLeft(System.currentTimeMillis());
        cuesDone.clear();
        for (int i = 0; i < spec.cues().size(); i++) {
            if (spec.cues().get(i).secondsLeft() >= left) {
                cuesDone.add(i);
            }
        }
        lastCountdown = Integer.MAX_VALUE;
    }

    public long millisLeft(long now) {
        return Math.max(0, endsAt - now);
    }

    /** Rounded up, so it reads 1 until the very end. */
    public int secondsLeft(long now) {
        return (int) ((millisLeft(now) + 999) / 1000);
    }

    public long durationMillis() {
        return endsAt - beginsAt;
    }

    public boolean participates(ServerPlayer player) {
        return spec.participation() == EventSpec.Participation.EVERYONE || joined.contains(player.getUUID());
    }

    public boolean hasJoined(UUID player) {
        return joined.contains(player);
    }

    /** @return false if they were already in */
    boolean join(ServerPlayer player) {
        names.put(player.getUUID(), player.getScoreboardName());
        scores.putIfAbsent(player.getUUID(), 0);
        return joined.add(player.getUUID());
    }

    boolean leave(ServerPlayer player) {
        boolean wasIn = joined.remove(player.getUUID());
        if (wasIn && scores.getOrDefault(player.getUUID(), 0) == 0) {
            scores.remove(player.getUUID());
        }
        return wasIn;
    }

    public int score(UUID player) {
        return scores.getOrDefault(player, 0);
    }

    /** @return the new score */
    int add(ServerPlayer player, int points) {
        names.put(player.getUUID(), player.getScoreboardName());
        return scores.merge(player.getUUID(), points, Integer::sum);
    }

    void set(ServerPlayer player, int points) {
        names.put(player.getUUID(), player.getScoreboardName());
        scores.put(player.getUUID(), points);
    }

    /** Names of everyone in it: joined, scored, or (for everyone-events) online and equipped. */
    public List<String> participantNames() {
        Set<UUID> all = new HashSet<>(scores.keySet());
        all.addAll(joined);
        all.addAll(equipped);
        return all.stream().map(player -> names.getOrDefault(player, "?")).sorted(String.CASE_INSENSITIVE_ORDER).toList();
    }

    void remember(ServerPlayer player) {
        names.put(player.getUUID(), player.getScoreboardName());
    }

    /** How many players have a score or joined. */
    public int participantCount() {
        Set<UUID> all = new HashSet<>(scores.keySet());
        all.addAll(joined);
        all.addAll(equipped);
        return all.size();
    }

    /** Best first. */
    public List<Standing> standings() {
        List<Map.Entry<UUID, Integer>> entries = new ArrayList<>(scores.entrySet());
        entries.sort(Comparator.<Map.Entry<UUID, Integer>>comparingInt(Map.Entry::getValue).reversed()
                .thenComparing(entry -> names.getOrDefault(entry.getKey(), "").toLowerCase()));
        List<Standing> out = new ArrayList<>(entries.size());
        int place = 0;
        int previous = Integer.MIN_VALUE;
        for (int i = 0; i < entries.size(); i++) {
            int score = entries.get(i).getValue();
            if (score != previous) {
                place = i + 1;
                previous = score;
            }
            UUID player = entries.get(i).getKey();
            out.add(new Standing(player, names.getOrDefault(player, "?"), score, score > 0 ? place : 0));
        }
        return out;
    }

    /** The standing of one player, or null if they haven't scored or joined. */
    public Standing standing(UUID player) {
        for (Standing standing : standings()) {
            if (standing.player().equals(player)) {
                return standing;
            }
        }
        return null;
    }
}
