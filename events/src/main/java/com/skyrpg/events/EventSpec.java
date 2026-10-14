package com.skyrpg.events;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;

/**
 * Everything about an event before it starts: what it's called, how long it runs, how you score and
 * what you win. Comes from a data file ({@code data/<namespace>/skyrpg/events/<id>.json}), from
 * {@code /event run <json>}, or from Java via {@link #builder}.
 *
 * <p>JSON (only {@code name} is required):
 * <pre>{@code
 * {
 *   name: "Timber Rush", description: "Chop the most logs!", color: "#C8913C", icon: "minecraft:oak_log",
 *   duration: "5m", unit: "logs", participation: "everyone",
 *   scoring: [{type: "break_blocks", blocks: ["#minecraft:logs"], points: 1}],
 *   prizes: [{place: 1, coins: 100}, {place: 2, coins: 50}, {place: 3, coins: 25}],
 *   announcements: [60, 30, 10, {left: 120, title: "Two minutes left!", subtitle: "Logs count double... just kidding"}],
 *   countdown: true, creative: false
 * }
 * }</pre>
 * Plus the optional join window, inventory stash, kit, gliding and arena fields of {@link EventSetup}.
 * <ul>
 *   <li>{@code participation}: {@code "everyone"} (all online players score) or {@code "opt_in"} (only
 *       players who type /event join).</li>
 *   <li>{@code announcements}: a number is a "N seconds left" warning in chat and the action bar; an
 *       object is a banner ({@link Announcement} JSON) at {@code left} seconds before the end or
 *       {@code after} seconds from the start. Defaults to 60, 30 and 10.</li>
 *   <li>{@code countdown}: tick down the last 5 seconds on the action bar. {@code creative}: whether
 *       players in creative mode score from scoring rules (default no).</li>
 * </ul>
 */
public record EventSpec(String id, String name, String description, int color, Identifier icon, int durationSeconds,
                        String unit, Participation participation, boolean allowCreative, boolean countdown,
                        List<ScoringRule> rules, List<Prize> prizes, List<Cue> cues, EventSetup setup) {
    public static final int DEFAULT_DURATION = 300;
    public static final int MAX_DURATION = 24 * 3600;

    public enum Participation {
        /** Every online player scores. */
        EVERYONE,
        /** Only players who joined with /event join score. */
        OPT_IN
    }

    /**
     * Something said while the event runs, {@code secondsLeft} before the end. A null announcement is the
     * standard "N seconds left!" warning in chat and the action bar.
     */
    public record Cue(int secondsLeft, Announcement announcement) {
    }

    public EventSpec {
        rules = List.copyOf(rules);
        prizes = List.copyOf(prizes);
        cues = cues.stream().sorted(Comparator.comparingInt(Cue::secondsLeft).reversed()).toList();
        setup = setup == null ? EventSetup.NONE : setup;
        if ((color >>> 24) == 0) {
            color |= 0xFF000000; // text drawn in a colour without alpha is invisible
        }
    }

    public static Builder builder(String id) {
        return new Builder(id);
    }

    /** A copy that runs for a different number of seconds. */
    public EventSpec withDuration(int seconds) {
        return new EventSpec(id, name, description, color, icon, seconds, unit, participation, allowCreative, countdown, rules, prizes, cues, setup);
    }

    public EventSpec withId(String newId) {
        return new EventSpec(newId, name, description, color, icon, durationSeconds, unit, participation, allowCreative, countdown, rules, prizes, cues, setup);
    }

    /** The prizes for a place (participation prizes are separate, see {@link Prize#EVERYONE}). */
    public List<Prize> prizesFor(int place) {
        return prizes.stream().filter(prize -> prize.place() == place).toList();
    }

    /** "100 coins", or "" if that place wins nothing. Several prizes are joined with " + ". */
    public String prizeLabel(int place) {
        List<String> labels = new ArrayList<>();
        for (Prize prize : prizes) {
            if ((prize.place() == place || prize.place() == Prize.EVERYONE) && !prize.label().isEmpty()) {
                labels.add(prize.label());
            }
        }
        return String.join(" + ", labels);
    }

    /**
     * Reads a spec from JSON. {@code fallbackId} is used when the JSON has no {@code id} (data files use
     * their file name); null means "make one from the name".
     *
     * @param registries for text components in announcements; may be null
     */
    public static EventSpec parse(JsonObject json, String fallbackId, HolderLookup.Provider registries) {
        String name = Json.string(json, fallbackId == null ? "" : prettify(fallbackId), "name", "title");
        if (name.isBlank()) {
            throw new IllegalArgumentException("An event needs a name, like {name:'Timber Rush'}");
        }
        String id = Json.slug(Json.string(json, fallbackId == null ? name : fallbackId, "id"));
        int duration = Json.seconds(Json.get(json, "duration", "time", "length"), DEFAULT_DURATION);
        if (duration <= 0 || duration > MAX_DURATION) {
            throw new IllegalArgumentException("Duration should be between 1 second and 24 hours");
        }
        Identifier icon = Json.id(Json.string(json, "minecraft:clock", "icon", "item"), "item");
        if (!BuiltInRegistries.ITEM.containsKey(icon)) {
            throw new IllegalArgumentException("Unknown icon item '" + icon + "'");
        }
        String participationName = Json.string(json, "everyone", "participation", "join").toLowerCase(Locale.ROOT).replace('-', '_');
        Participation participation = switch (participationName) {
            case "everyone", "all", "auto", "false" -> Participation.EVERYONE;
            case "opt_in", "optin", "join", "true" -> Participation.OPT_IN;
            default -> throw new IllegalArgumentException("participation should be \"everyone\" or \"opt_in\", not '" + participationName + "'");
        };

        List<ScoringRule> rules = new ArrayList<>();
        for (JsonElement rule : Json.array(json, "scoring", "rules", "score")) {
            rules.add(ScoringRule.parse(rule.getAsJsonObject()));
        }
        if (rules.isEmpty()) {
            rules.add(ScoringRule.custom());
        }
        List<Prize> prizes = new ArrayList<>();
        for (JsonElement prize : Json.array(json, "prizes", "rewards")) {
            prizes.add(Prize.parse(prize.getAsJsonObject()));
        }

        List<Cue> cues = new ArrayList<>();
        JsonElement announcements = Json.get(json, "announcements", "warnings");
        if (announcements == null) {
            for (int left : new int[] {60, 30, 10}) {
                if (left < duration) {
                    cues.add(new Cue(left, null));
                }
            }
        } else {
            for (JsonElement cue : Json.array(json, "announcements", "warnings")) {
                if (cue.isJsonPrimitive()) {
                    cues.add(new Cue(Json.seconds(cue, 0), null));
                    continue;
                }
                JsonObject object = cue.getAsJsonObject();
                JsonElement after = Json.get(object, "after");
                int left = after != null ? duration - Json.seconds(after, 0) : Json.seconds(Json.get(object, "left", "at"), 0);
                cues.add(new Cue(left, Announcement.parse(object, registries)));
            }
        }

        return new EventSpec(id, name,
                Json.string(json, "", "description", "subtitle"),
                Json.color(Json.get(json, "color", "colour"), Json.GOLD),
                icon, duration,
                Json.string(json, "points", "unit", "units"),
                participation,
                Json.bool(json, false, "creative", "allow_creative"),
                Json.bool(json, true, "countdown"),
                rules, prizes, cues, EventSetup.parse(json));
    }

    private static String prettify(String id) {
        StringBuilder out = new StringBuilder();
        for (String word : id.split("_")) {
            if (!word.isEmpty()) {
                out.append(out.isEmpty() ? "" : " ").append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
            }
        }
        return out.toString();
    }

    /** For mods that start events from Java. Every setter is optional except the name. */
    public static final class Builder {
        private final String id;
        private String name;
        private String description = "";
        private int color = Json.GOLD;
        private Identifier icon = Identifier.withDefaultNamespace("clock");
        private int duration = DEFAULT_DURATION;
        private String unit = "points";
        private Participation participation = Participation.EVERYONE;
        private boolean allowCreative;
        private boolean countdown = true;
        private final List<ScoringRule> rules = new ArrayList<>();
        private final List<Prize> prizes = new ArrayList<>();
        private final List<Cue> cues = new ArrayList<>();
        private boolean defaultCues = true;
        private EventSetup setup = EventSetup.NONE;

        private Builder(String id) {
            this.id = Json.slug(id);
            this.name = prettify(this.id);
        }

        public Builder name(String name) {
            this.name = name;
            return this;
        }

        public Builder description(String description) {
            this.description = description;
            return this;
        }

        /** 0xRRGGBB or 0xFFRRGGBB. */
        public Builder color(int color) {
            this.color = 0xFF000000 | color;
            return this;
        }

        public Builder icon(Identifier item) {
            this.icon = item;
            return this;
        }

        public Builder duration(int seconds) {
            this.duration = seconds;
            return this;
        }

        /** What a point is called on the leaderboard: "logs", "kills". */
        public Builder unit(String unit) {
            this.unit = unit;
            return this;
        }

        public Builder optIn() {
            this.participation = Participation.OPT_IN;
            return this;
        }

        public Builder allowCreative(boolean allow) {
            this.allowCreative = allow;
            return this;
        }

        public Builder countdown(boolean countdown) {
            this.countdown = countdown;
            return this;
        }

        public Builder rule(ScoringRule rule) {
            rules.add(rule);
            return this;
        }

        public Builder prize(Prize prize) {
            prizes.add(prize);
            return this;
        }

        /** Adds a cue and turns off the default 60/30/10 second warnings. */
        public Builder cue(int secondsLeft, Announcement announcement) {
            cues.add(new Cue(secondsLeft, announcement));
            defaultCues = false;
            return this;
        }

        /** Join window, stash, kit, gliding and arena; see {@link EventSetup}. */
        public Builder setup(EventSetup setup) {
            this.setup = setup;
            return this;
        }

        public EventSpec build() {
            List<Cue> allCues = new ArrayList<>(cues);
            if (defaultCues) {
                for (int left : new int[] {60, 30, 10}) {
                    if (left < duration) {
                        allCues.add(new Cue(left, null));
                    }
                }
            }
            List<ScoringRule> allRules = rules.isEmpty() ? List.of(ScoringRule.custom()) : rules;
            return new EventSpec(id, name, description, color, icon, duration, unit, participation, allowCreative, countdown, allRules, prizes, allCues, setup);
        }
    }
}
