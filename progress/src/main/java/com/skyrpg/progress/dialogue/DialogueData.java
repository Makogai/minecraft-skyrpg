package com.skyrpg.progress.dialogue;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.skyrpg.progress.SkyRpgProgress;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.resources.Identifier;

/**
 * Conversations as data, from {@code data/<ns>/skyrpg/dialogue/<id>.json} (see docs/design/journal.md):
 * <pre>
 * { "speaker": "skyrpg_npcs:tavern_keeper",   // an NPC id, or "narrator"
 *   "pages": [
 *     { "id": "intro", "text": "Welcome, &amp;6{player}&amp;r!", "mood": "happy",
 *       "choices": [ {"label": "Tell me more", "action": "next"}, {"label": "Bye", "action": "close"} ] } ] }
 * </pre>
 * Choice actions: {@code next}, {@code goto} (with {@code page}), {@code accept}, {@code turn_in}, {@code shop}, {@code close},
 * {@code end} and {@code run} (a whitelisted {@code do}: {@code waypoint}, {@code track}). Only the server reads these files.
 */
public final class DialogueData {
    public static final String DIRECTORY = "skyrpg/dialogue";

    public record Choice(String label, String action, String page, JsonObject args) {
    }

    public record Page(String id, String text, String mood, List<Choice> choices) {
    }

    public record Conversation(Identifier id, String speaker, List<Page> pages) {
        public boolean narrator() {
            return speaker.isEmpty();
        }

        public int indexOf(String pageId) {
            for (int i = 0; i < pages.size(); i++) {
                if (pages.get(i).id().equals(pageId)) {
                    return i;
                }
            }
            return -1;
        }
    }

    private static volatile Map<Identifier, Conversation> conversations = Map.of();

    private DialogueData() {
    }

    public static Conversation get(Identifier id) {
        return conversations.get(id);
    }

    public static Map<Identifier, Conversation> all() {
        return conversations;
    }

    public static void load(Map<Identifier, JsonElement> files) {
        Map<Identifier, Conversation> loaded = new LinkedHashMap<>();
        files.forEach((id, element) -> {
            try {
                loaded.put(id, parse(id, element.getAsJsonObject()));
            } catch (RuntimeException e) {
                SkyRpgProgress.LOGGER.warn("Skipping dialogue {}: {}", id, e.getMessage());
            }
        });
        conversations = loaded;
        SkyRpgProgress.LOGGER.info("Loaded {} conversations", loaded.size());
    }

    private static Conversation parse(Identifier id, JsonObject json) {
        String speaker = json.has("speaker") ? json.get("speaker").getAsString() : "narrator";
        List<Page> pages = new ArrayList<>();
        int number = 0;
        for (JsonElement element : json.getAsJsonArray("pages")) {
            JsonObject p = element.getAsJsonObject();
            List<Choice> choices = new ArrayList<>();
            if (p.has("choices")) {
                for (JsonElement c : p.getAsJsonArray("choices")) {
                    JsonObject choice = c.getAsJsonObject();
                    choices.add(new Choice(choice.get("label").getAsString(), choice.has("action") ? choice.get("action").getAsString() : "next",
                            choice.has("page") ? choice.get("page").getAsString() : "", choice));
                }
            }
            pages.add(new Page(p.has("id") ? p.get("id").getAsString() : "p" + number, p.get("text").getAsString(),
                    p.has("mood") ? p.get("mood").getAsString() : "", List.copyOf(choices)));
            number++;
        }
        if (pages.isEmpty()) {
            throw new IllegalArgumentException("no pages");
        }
        return new Conversation(id, speaker.equals("narrator") || speaker.equals("none") ? "" : speaker, List.copyOf(pages));
    }
}
