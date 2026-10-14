package com.skyrpg.events;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.JsonOps;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.minecraft.ChatFormatting;
import net.minecraft.core.HolderLookup;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.resources.Identifier;

/**
 * Forgiving readers for the JSON that admins type into /event run, /announce and /board. Parsing is
 * lenient, so one-liners can skip quotes: {@code {name:'Timber Rush',duration:'5m'}}. Problems throw
 * {@link IllegalArgumentException} with a message that's fine to show in chat.
 */
public final class Json {
    /** SkyRPG's gold, the default accent for events, banners and boards. */
    public static final int GOLD = 0xFFF2B33D;
    private static final Pattern DURATION = Pattern.compile("(\\d+(?:\\.\\d+)?)\\s*([hms]?)");

    private Json() {
    }

    public static JsonObject parseObject(String text) {
        JsonElement element;
        try {
            element = JsonParser.parseString(text);
        } catch (JsonParseException e) {
            throw new IllegalArgumentException("That isn't valid JSON: " + rootMessage(e));
        }
        if (!element.isJsonObject()) {
            throw new IllegalArgumentException("Expected a JSON object, like {name:'Timber Rush'}");
        }
        return element.getAsJsonObject();
    }

    private static String rootMessage(Throwable e) {
        while (e.getCause() != null) {
            e = e.getCause();
        }
        return e.getMessage();
    }

    /** The first of {@code keys} that is present, or null. */
    public static JsonElement get(JsonObject json, String... keys) {
        for (String key : keys) {
            JsonElement value = json.get(key);
            if (value != null && !value.isJsonNull()) {
                return value;
            }
        }
        return null;
    }

    public static String string(JsonObject json, String fallback, String... keys) {
        JsonElement value = get(json, keys);
        return value == null ? fallback : value.isJsonPrimitive() ? value.getAsString() : value.toString();
    }

    public static int integer(JsonObject json, int fallback, String... keys) {
        JsonElement value = get(json, keys);
        if (value == null) {
            return fallback;
        }
        try {
            return value.getAsInt();
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("'" + keys[0] + "' should be a whole number, not " + value);
        }
    }

    public static float decimal(JsonObject json, float fallback, String... keys) {
        JsonElement value = get(json, keys);
        if (value == null) {
            return fallback;
        }
        try {
            return value.getAsFloat();
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("'" + keys[0] + "' should be a number, not " + value);
        }
    }

    public static boolean bool(JsonObject json, boolean fallback, String... keys) {
        JsonElement value = get(json, keys);
        return value == null ? fallback : value.getAsBoolean();
    }

    /** A string or an array of strings, as a list. */
    public static List<String> strings(JsonObject json, String... keys) {
        JsonElement value = get(json, keys);
        List<String> out = new ArrayList<>();
        if (value == null) {
            return out;
        }
        if (value.isJsonArray()) {
            for (JsonElement entry : value.getAsJsonArray()) {
                out.add(entry.getAsString());
            }
        } else {
            out.add(value.getAsString());
        }
        return out;
    }

    public static JsonArray array(JsonObject json, String... keys) {
        JsonElement value = get(json, keys);
        if (value == null) {
            return new JsonArray();
        }
        if (value.isJsonArray()) {
            return value.getAsJsonArray();
        }
        JsonArray single = new JsonArray();
        single.add(value);
        return single;
    }

    /**
     * A colour as {@code "#2ECC71"}, {@code "0x2ECC71"}, a chat colour name ({@code "gold"}) or a number.
     * Always returns it with full alpha.
     */
    public static int color(JsonElement value, int fallback) {
        if (value == null) {
            return fallback;
        }
        if (value.isJsonPrimitive() && value.getAsJsonPrimitive().isNumber()) {
            return 0xFF000000 | value.getAsInt();
        }
        String text = value.getAsString().trim().toLowerCase(Locale.ROOT);
        if (text.startsWith("0x")) {
            text = "#" + text.substring(2);
        }
        if (text.startsWith("#") && text.length() == 9) {
            text = "#" + text.substring(3);
        }
        String parsed = text;
        return TextColor.parseColor(parsed).result().map(color -> 0xFF000000 | color.getValue())
                .orElseThrow(() -> new IllegalArgumentException("Unknown colour '" + parsed + "' (try #F2B33D or gold)"));
    }

    /** Seconds from a number ({@code 300}) or text like {@code "5m"}, {@code "90s"}, {@code "1m30s"}, {@code "1h"}. */
    public static int seconds(JsonElement value, int fallback) {
        if (value == null) {
            return fallback;
        }
        if (value.isJsonPrimitive() && value.getAsJsonPrimitive().isNumber()) {
            return value.getAsInt();
        }
        return seconds(value.getAsString());
    }

    public static int seconds(String text) {
        String compact = text.trim().toLowerCase(Locale.ROOT).replace(" ", "");
        Matcher matcher = DURATION.matcher(compact);
        double total = 0;
        int end = 0;
        while (matcher.find() && matcher.start() == end && matcher.end() > matcher.start()) {
            double amount = Double.parseDouble(matcher.group(1));
            total += switch (matcher.group(2)) {
                case "h" -> amount * 3600;
                case "m" -> amount * 60;
                default -> amount;
            };
            end = matcher.end();
        }
        if (end != compact.length() || compact.isEmpty()) {
            throw new IllegalArgumentException("Can't read '" + text + "' as a duration (try 300, \"5m\" or \"1m30s\")");
        }
        return (int) Math.round(total);
    }

    public static Identifier id(String text, String what) {
        Identifier id = Identifier.tryParse(text.startsWith("#") ? text.substring(1) : text);
        if (id == null) {
            throw new IllegalArgumentException("'" + text + "' isn't a valid " + what + " id");
        }
        return id;
    }

    /**
     * Text from JSON: a plain string (with optional {@code &6}-style colour codes), or a full text
     * component like {@code {text:'Hi',color:'gold',bold:true}} or {@code ["a",{text:'b'}]}.
     */
    public static Component text(JsonElement value, HolderLookup.Provider registries) {
        if (value == null || value.isJsonNull()) {
            return Component.empty();
        }
        if (value.isJsonPrimitive()) {
            return legacy(value.getAsString());
        }
        DynamicOps<JsonElement> ops = registries == null ? JsonOps.INSTANCE : registries.createSerializationContext(JsonOps.INSTANCE);
        return ComponentSerialization.CODEC.parse(ops, value).getOrThrow(error -> new IllegalArgumentException("Bad text " + value + ": " + error));
    }

    /** "&6Gold &lbold&r plain" -> styled text. {@code &} and {@code §} both work. */
    public static MutableComponent legacy(String text) {
        MutableComponent out = Component.empty();
        Style style = Style.EMPTY;
        StringBuilder run = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            ChatFormatting format = (c == '&' || c == '§') && i + 1 < text.length() ? ChatFormatting.getByCode(Character.toLowerCase(text.charAt(i + 1))) : null;
            if (format == null) {
                run.append(c);
                continue;
            }
            if (!run.isEmpty()) {
                out.append(Component.literal(run.toString()).setStyle(style));
                run.setLength(0);
            }
            // The first 16 formats are the colours; a colour code starts a fresh style, like vanilla's.
            style = format == ChatFormatting.RESET ? Style.EMPTY : format.ordinal() <= ChatFormatting.WHITE.ordinal() ? Style.EMPTY.withColor(format) : style.applyFormat(format);
            i++;
        }
        if (!run.isEmpty() || out.getSiblings().isEmpty()) {
            out.append(Component.literal(run.toString()).setStyle(style));
        }
        return out;
    }

    /** "Timber Rush!" -> "timber_rush" */
    public static String slug(String name) {
        String slug = name.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "_").replaceAll("^_+|_+$", "");
        return slug.isEmpty() ? "event" : slug;
    }

    /** 125 -> "2:05", 3725 -> "1:02:05" */
    public static String clock(long seconds) {
        seconds = Math.max(0, seconds);
        if (seconds >= 3600) {
            return String.format(Locale.ROOT, "%d:%02d:%02d", seconds / 3600, seconds / 60 % 60, seconds % 60);
        }
        return String.format(Locale.ROOT, "%d:%02d", seconds / 60, seconds % 60);
    }

    /** 1 -> "1st", 2 -> "2nd", 11 -> "11th" */
    public static String ordinal(int place) {
        int mod100 = place % 100;
        String suffix = mod100 >= 11 && mod100 <= 13 ? "th" : switch (place % 10) {
            case 1 -> "st";
            case 2 -> "nd";
            case 3 -> "rd";
            default -> "th";
        };
        return place + suffix;
    }
}
