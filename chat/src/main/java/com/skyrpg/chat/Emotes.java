package com.skyrpg.chat;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.skyrpg.core.data.JsonDataLoader;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.minecraft.network.chat.TextColor;
import net.minecraft.resources.Identifier;

/**
 * Chat emotes: codes like {@code :heart:} or {@code <3} that turn into a coloured glyph for everyone. Data-driven:
 * {@code data/<ns>/skyrpg/chat_emotes/*.json} holds {@code {"emotes": [{"code": ":heart:", "text": "❤", "color":
 * "red", "group": "Classic"}]}}; a later file can replace a code or add new ones. Only glyphs Minecraft's own font
 * draws (it falls back to Unifont, which covers almost all of Unicode's basic plane).
 *
 * <p>Codes wrapped in colons work anywhere in a message. Others ({@code <3}, {@code o/}) only count as a whole word,
 * so "info/" or "x<3y" stay as typed.
 */
public final class Emotes {
    public static final String DIRECTORY = "skyrpg/chat_emotes";

    /** {@code icon}: a {@link ChatIcons} glyph shown instead of {@code text} (null = plain text, which is also the fallback). */
    public record Emote(String code, String text, TextColor color, String group, String icon) {
        /** Only matched as a whole word when it isn't a {@code :code:}. */
        public boolean wordOnly() {
            return !(code.length() > 2 && code.startsWith(":") && code.endsWith(":"));
        }
    }

    private static List<Emote> emotes = List.of();
    /** Lower-case code -> emote. */
    private static Map<String, Emote> byCode = Map.of();

    private Emotes() {
    }

    static void init() {
        JsonDataLoader.register(SkyRpgChat.id("emotes"), DIRECTORY, Emotes::load);
    }

    private static void load(Map<Identifier, JsonElement> files) {
        Map<String, Emote> loaded = new LinkedHashMap<>();
        files.forEach((id, element) -> {
            try {
                for (JsonElement entry : element.getAsJsonObject().getAsJsonArray("emotes")) {
                    JsonObject json = entry.getAsJsonObject();
                    String code = json.get("code").getAsString();
                    String text = json.get("text").getAsString();
                    if (code.isBlank() || text.isEmpty()) {
                        continue;
                    }
                    TextColor color = json.has("color")
                            ? TextColor.parseColor(json.get("color").getAsString()).result().orElse(ChatColors.rgb(ChatColors.TEXT))
                            : ChatColors.rgb(ChatColors.TEXT);
                    String group = json.has("group") ? json.get("group").getAsString() : "Emotes";
                    String icon = json.has("icon") && !json.get("icon").getAsString().isEmpty() ? json.get("icon").getAsString() : null;
                    loaded.put(code.toLowerCase(Locale.ROOT), new Emote(code, text, color, group, icon));
                }
            } catch (RuntimeException e) {
                SkyRpgChat.LOGGER.warn("Skipping emote file {}: {}", id, e.getMessage());
            }
        });
        emotes = List.copyOf(loaded.values());
        byCode = Map.copyOf(loaded);
        ChatBody.rebuildPattern();
        SkyRpgChat.LOGGER.info("Loaded {} chat emotes", emotes.size());
    }

    public static List<Emote> all() {
        return emotes;
    }

    public static Emote get(String code) {
        return byCode.get(code.toLowerCase(Locale.ROOT));
    }

    /** Codes longest first, so ":tableflip:" wins over anything shorter inside it. */
    static List<String> codesLongestFirst() {
        List<String> codes = new ArrayList<>();
        for (Emote emote : emotes) {
            codes.add(emote.code());
        }
        codes.sort(Comparator.comparingInt(String::length).reversed());
        return codes;
    }
}
