package com.skyrpg.events.client;

import com.skyrpg.events.net.EventPayloads;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.util.Util;

/** What the server last said about running events, plus the bits the HUD animates (score pulses, new cards). */
final class ClientEvents {
    /** A score gain to flash: when, and how much (quick gains add up into one "+N"). */
    record Pulse(long at, int delta) {
    }

    private static final long PULSE_STACK_MS = 900;
    private static List<EventPayloads.EventView> events = List.of();
    /** Server clock minus ours, so countdowns match the server's. */
    private static long clockOffset;
    private static final Map<String, Pulse> PULSES = new HashMap<>();
    private static final Map<String, Long> FIRST_SEEN = new HashMap<>();

    private ClientEvents() {
    }

    static void apply(EventPayloads.State state) {
        clockOffset = state.serverNow() - System.currentTimeMillis();
        long now = Util.getMillis();
        Map<String, EventPayloads.EventView> before = new HashMap<>();
        events.forEach(view -> before.put(view.id(), view));
        Set<String> ids = new HashSet<>();
        for (EventPayloads.EventView view : state.events()) {
            ids.add(view.id());
            EventPayloads.EventView old = before.get(view.id());
            if (old == null) {
                FIRST_SEEN.put(view.id(), now);
            } else if (view.selfScore() > old.selfScore()) {
                int delta = view.selfScore() - old.selfScore();
                Pulse last = PULSES.get(view.id());
                if (last != null && now - last.at() < PULSE_STACK_MS) {
                    delta += last.delta();
                }
                PULSES.put(view.id(), new Pulse(now, delta));
            }
        }
        PULSES.keySet().retainAll(ids);
        FIRST_SEEN.keySet().retainAll(ids);
        events = state.events();
    }

    static void clear() {
        events = List.of();
        PULSES.clear();
        FIRST_SEEN.clear();
    }

    /** Soonest-ending first. */
    static List<EventPayloads.EventView> events() {
        return events;
    }

    static long serverNow() {
        return System.currentTimeMillis() + clockOffset;
    }

    static long millisLeft(EventPayloads.EventView view) {
        return Math.max(0, view.endsAt() - serverNow());
    }

    /** Milliseconds until the clock starts; 0 once it has. */
    static long millisToStart(EventPayloads.EventView view) {
        return Math.max(0, view.beginsAt() - serverNow());
    }

    static Pulse pulse(String eventId) {
        return PULSES.get(eventId);
    }

    /** When this client first saw the event, in {@link Util#getMillis()}; for the slide-in. */
    static long firstSeen(String eventId) {
        return FIRST_SEEN.getOrDefault(eventId, 0L);
    }
}
