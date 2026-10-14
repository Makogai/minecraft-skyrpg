package com.skyrpg.progress.quests;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.skyrpg.progress.SkyRpgProgress;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.resources.Identifier;

/**
 * The questline as data: chapters and quests parsed from {@code data/<ns>/skyrpg/quests/<chapter>/<id>.json} (the chapter's
 * own file is {@code _chapter.json} in its folder). The server loads it from the datapack and sends the raw JSON to every
 * client ({@link QuestPayloads.Defs}), which parses it with the same code, so the Journal shows what the server checks.
 */
public final class QuestData {
    public static final String DIRECTORY = "skyrpg/quests";

    /** A chapter of the Chronicle. */
    public record Chapter(Identifier id, String title, int order, String description, String icon) {
    }

    /**
     * One thing to do. {@code type} picks the checker, {@code target} is the number the bar fills to, {@code json} holds the
     * rest of the parameters (see docs/design/journal.md).
     */
    public record Objective(String type, String label, int target, JsonObject json) {
        public String str(String key) {
            return json.has(key) ? json.get(key).getAsString() : "";
        }

        public int num(String key, int fallback) {
            return json.has(key) ? json.get(key).getAsInt() : fallback;
        }

        public boolean flag(String key) {
            return json.has(key) && json.get(key).getAsBoolean();
        }

        /** Whether the Track button has somewhere to point for this objective. */
        public boolean trackable() {
            return switch (type) {
                case "visit", "dungeon_clear", "talk" -> true;
                default -> json.has("track");
            };
        }
    }

    /** One reward item. */
    public record RewardItem(Identifier item, int count) {
    }

    public record Rewards(long coins, int xp, List<RewardItem> items, String hint) {
    }

    public record Quest(Identifier id, Identifier chapter, String title, String giverName, String giverText, String description,
                        List<Objective> objectives, Rewards rewards, List<Identifier> prerequisites, boolean side, int order,
                        String giverNpc, String turnInNpc, String turnInName, List<String> dialogues, List<String> replaces) {
        /** Whether an NPC offers this quest (otherwise it starts by itself with a journal entry). */
        public boolean hasGiver() {
            return !giverNpc.isEmpty();
        }

        /** Whether the quest is handed in to an NPC (otherwise it completes the moment its objectives are met). */
        public boolean hasTurnIn() {
            return !turnInNpc.isEmpty();
        }

        public boolean hasDialogue(String kind) {
            return dialogues.contains(kind);
        }

        /** The conversation of one kind: {@code offer}, {@code progress}, {@code complete} or {@code talk_<objective>}. */
        public Identifier dialogue(String kind) {
            return Identifier.fromNamespaceAndPath(id.getNamespace(), id.getPath() + "/" + kind);
        }
    }

    private static volatile Map<Identifier, Chapter> chapters = Map.of();
    private static volatile Map<Identifier, Quest> quests = Map.of();
    /** The raw JSON as loaded, sent to clients. */
    private static volatile String rawJson = "{}";

    private QuestData() {
    }

    public static Map<Identifier, Chapter> chapters() {
        return chapters;
    }

    public static Map<Identifier, Quest> quests() {
        return quests;
    }

    public static Quest quest(Identifier id) {
        return quests.get(id);
    }

    public static Quest quest(String id) {
        Identifier parsed = Identifier.tryParse(id);
        return parsed == null ? null : quests.get(parsed);
    }

    public static String rawJson() {
        return rawJson;
    }

    /** The quests of a chapter in order. */
    public static List<Quest> inChapter(Identifier chapter) {
        List<Quest> list = new ArrayList<>();
        for (Quest quest : quests.values()) {
            if (quest.chapter().equals(chapter)) {
                list.add(quest);
            }
        }
        return list;
    }

    /** Loads from data files (server): the file id is {@code ns:chapter/quest}, or {@code ns:chapter/_chapter}. */
    public static void load(Map<Identifier, JsonElement> files) {
        JsonObject raw = new JsonObject();
        JsonObject rawChapters = new JsonObject();
        JsonObject rawQuests = new JsonObject();
        files.forEach((id, element) -> {
            if (element.isJsonObject()) {
                (id.getPath().endsWith("/_chapter") ? rawChapters : rawQuests).add(id.toString(), element);
            }
        });
        raw.add("chapters", rawChapters);
        raw.add("quests", rawQuests);
        rawJson = raw.toString();
        parse(raw);
        SkyRpgProgress.LOGGER.info("Loaded {} quests in {} chapters", quests.size(), chapters.size());
    }

    /** Loads from what the server sent (client). */
    public static void loadJson(String json) {
        rawJson = json;
        parse(JsonParser.parseString(json).getAsJsonObject());
    }

    private static void parse(JsonObject raw) {
        Map<Identifier, Chapter> loadedChapters = new LinkedHashMap<>();
        Map<Identifier, Quest> loadedQuests = new LinkedHashMap<>();
        for (Map.Entry<String, JsonElement> entry : raw.getAsJsonObject("chapters").entrySet()) {
            try {
                Identifier file = Identifier.parse(entry.getKey());
                String path = file.getPath();
                Identifier id = Identifier.fromNamespaceAndPath(file.getNamespace(), path.substring(0, path.length() - "/_chapter".length()));
                JsonObject json = entry.getValue().getAsJsonObject();
                loadedChapters.put(id, new Chapter(id, str(json, "title", id.getPath()), json.has("order") ? json.get("order").getAsInt() : 100,
                        str(json, "description", ""), str(json, "icon", "minecraft:book")));
            } catch (RuntimeException e) {
                SkyRpgProgress.LOGGER.warn("Skipping chapter {}: {}", entry.getKey(), e.getMessage());
            }
        }
        List<Quest> parsed = new ArrayList<>();
        for (Map.Entry<String, JsonElement> entry : raw.getAsJsonObject("quests").entrySet()) {
            try {
                parsed.add(parseQuest(Identifier.parse(entry.getKey()), entry.getValue().getAsJsonObject()));
            } catch (RuntimeException e) {
                SkyRpgProgress.LOGGER.warn("Skipping quest {}: {}", entry.getKey(), e.getMessage());
            }
        }
        parsed.sort(Comparator.comparingInt((Quest quest) -> {
            Chapter chapter = loadedChapters.get(quest.chapter());
            return chapter == null ? 1000 : chapter.order();
        }).thenComparingInt(Quest::order).thenComparing(quest -> quest.id().toString()));
        for (Quest quest : parsed) {
            loadedQuests.put(quest.id(), quest);
        }
        Map<Identifier, Chapter> sorted = new LinkedHashMap<>();
        loadedChapters.values().stream().sorted(Comparator.comparingInt(Chapter::order)).forEach(chapter -> sorted.put(chapter.id(), chapter));
        chapters = sorted;
        quests = loadedQuests;
    }

    private static Quest parseQuest(Identifier file, JsonObject json) {
        String path = file.getPath();
        Identifier chapter = Identifier.fromNamespaceAndPath(file.getNamespace(), path.substring(0, path.indexOf('/')));
        JsonObject giver = json.has("giver") ? json.getAsJsonObject("giver") : new JsonObject();
        List<Objective> objectives = new ArrayList<>();
        for (JsonElement element : json.getAsJsonArray("objectives")) {
            JsonObject o = element.getAsJsonObject();
            String type = o.get("type").getAsString();
            objectives.add(new Objective(type, str(o, "label", type), o.has("target") ? Math.max(1, o.get("target").getAsInt()) : 1, o));
        }
        JsonObject r = json.has("rewards") ? json.getAsJsonObject("rewards") : new JsonObject();
        List<RewardItem> items = new ArrayList<>();
        if (r.has("items")) {
            for (JsonElement element : r.getAsJsonArray("items")) {
                JsonObject item = element.getAsJsonObject();
                items.add(new RewardItem(Identifier.parse(item.get("id").getAsString()), item.has("count") ? item.get("count").getAsInt() : 1));
            }
        }
        Rewards rewards = new Rewards(r.has("coins") ? r.get("coins").getAsLong() : 0, r.has("xp") ? r.get("xp").getAsInt() : 0, List.copyOf(items),
                str(r, "hint", ""));
        List<Identifier> prerequisites = new ArrayList<>();
        if (json.has("prerequisites")) {
            JsonArray array = json.getAsJsonArray("prerequisites");
            for (JsonElement element : array) {
                prerequisites.add(Identifier.parse(element.getAsString()));
            }
        }
        String giverNpc = npc(str(giver, "npc", ""));
        JsonObject turnIn = json.has("turn_in") ? json.getAsJsonObject("turn_in") : null;
        String turnInNpc = turnIn == null ? giverNpc : npc(str(turnIn, "npc", ""));
        String turnInName = turnIn == null ? str(giver, "name", "") : str(turnIn, "name", "");
        return new Quest(file, chapter, str(json, "title", file.getPath()), str(giver, "name", ""), str(giver, "text", ""),
                str(json, "description", ""), List.copyOf(objectives), rewards, List.copyOf(prerequisites),
                json.has("side") && json.get("side").getAsBoolean(), json.has("order") ? json.get("order").getAsInt() : 0,
                giverNpc, turnInNpc, turnInName, strings(json, "dialogue"), strings(json, "replaces"));
    }

    /** An NPC id from data: empty, "none" and "narrator" all mean no NPC. */
    private static String npc(String raw) {
        return raw.isEmpty() || raw.equals("none") || raw.equals("narrator") ? "" : raw;
    }

    private static List<String> strings(JsonObject json, String key) {
        List<String> out = new ArrayList<>();
        if (json.has(key)) {
            for (JsonElement element : json.getAsJsonArray(key)) {
                out.add(element.getAsString());
            }
        }
        return List.copyOf(out);
    }

    private static String str(JsonObject json, String key, String fallback) {
        return json.has(key) ? json.get(key).getAsString() : fallback;
    }
}
