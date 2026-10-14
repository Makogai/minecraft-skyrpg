package com.skyrpg.events;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * What a place wins. The commands run as the server, with {@code {player}}, {@code {place}},
 * {@code {score}} and {@code {event}} filled in. JSON:
 * <pre>{@code
 * {place: 1, commands: ["coins give {player} 100"], label: "100 coins"}
 * {place: 2, coins: 50}                      // shorthand: runs /coins give and labels it "50 coins"
 * {place: "all", commands: ["give {player} minecraft:cookie 3"], label: "3 cookies"}  // everyone who scored
 * }</pre>
 *
 * @param place 1 for first, 2 for second...; {@link #EVERYONE} for everyone who scored at least a point
 * @param label shown on the winner board, e.g. "100 coins"
 */
public record Prize(int place, List<String> commands, String label) {
    public static final int EVERYONE = 0;

    public static Prize coins(int place, long coins) {
        return new Prize(place, List.of("coins give {player} " + coins), coins + " coins");
    }

    public static Prize parse(JsonObject json) {
        JsonElement placeJson = Json.get(json, "place", "rank");
        int place;
        if (placeJson == null) {
            throw new IllegalArgumentException("A prize needs a place, like {place:1, coins:100}");
        } else if (placeJson.getAsJsonPrimitive().isNumber()) {
            place = placeJson.getAsInt();
        } else {
            String text = placeJson.getAsString().toLowerCase(Locale.ROOT);
            place = switch (text) {
                case "all", "everyone", "participants", "participation" -> EVERYONE;
                default -> {
                    try {
                        yield Integer.parseInt(text.replaceAll("(st|nd|rd|th)$", ""));
                    } catch (NumberFormatException e) {
                        throw new IllegalArgumentException("Prize place should be 1, 2, 3... or \"all\", not '" + text + "'");
                    }
                }
            };
        }
        if (place < 0) {
            throw new IllegalArgumentException("Prize place can't be negative");
        }
        List<String> commands = new ArrayList<>(Json.strings(json, "commands", "command"));
        String label = Json.string(json, "", "label", "name");
        JsonElement coins = Json.get(json, "coins");
        if (coins != null) {
            commands.add("coins give {player} " + coins.getAsLong());
            if (label.isEmpty()) {
                label = coins.getAsLong() + " coins";
            }
        }
        return new Prize(place, List.copyOf(commands), label);
    }

    /** Ordinal for chat: "1st", or "Everyone" for participation prizes. */
    public String placeName() {
        return place == EVERYONE ? "Everyone" : Json.ordinal(place);
    }
}
