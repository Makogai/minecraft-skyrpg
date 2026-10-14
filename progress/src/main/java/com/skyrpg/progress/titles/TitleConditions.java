package com.skyrpg.progress.titles;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.skyrpg.core.economy.Wallet;
import com.skyrpg.core.skills.SkillLevels;
import com.skyrpg.progress.collections.CollectionDef;
import com.skyrpg.progress.collections.Collections;
import com.skyrpg.progress.collections.SkyRpgLevel;
import com.skyrpg.progress.quests.QuestData;
import com.skyrpg.progress.quests.QuestState;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;

/**
 * Title unlock conditions, checked by polling a player's state (so a title is earned retroactively the first time it is
 * looked at). {@code type} is one of:
 * <ul>
 * <li>{@code skill_level} {skill, level}: that skill's level</li>
 * <li>{@code any_skill_level} {level}: the best of the five skills</li>
 * <li>{@code all_skills} {level}: every one of the five skills at that level</li>
 * <li>{@code skyrpg_level} {level}</li>
 * <li>{@code slayer_level} {level, lines?, all?}: a slayer line (any of {@code lines}, or all of them with {@code all})</li>
 * <li>{@code boss_kill} {bosses, need?, kills?}: kills of the listed boss ids (an id without a namespace matches by path); {@code need}
 * different bosses (default all of them), each {@code kills} times (default 1)</li>
 * <li>{@code kind_kills} {kind, count}: boss kills of a {@code BossKills.Kind}, repeats included</li>
 * <li>{@code collections} {tier, count}: how many collections are at that tier or higher (count -1 = every collection)</li>
 * <li>{@code coins} {amount}: the purse</li>
 * <li>{@code chronicle}: every main (non-side) quest done</li>
 * <li>{@code stat} {stat, amount, divisor?}: a vanilla custom statistic (play_time in ticks, deaths...)</li>
 * <li>{@code counter} {key, amount}: one of the title hooks' counters (Blood Moon seconds and kills)</li>
 * <li>{@code flag} {flag}: something another mod granted through {@code TitleFlags}</li>
 * </ul>
 */
public final class TitleConditions {
    private static final List<Identifier> SKILLS = List.of(SkillLevels.MINING, SkillLevels.FARMING, SkillLevels.COMBAT,
            SkillLevels.FORAGING, SkillLevels.FISHING);
    private static final List<String> SLAYERS = List.of("bone_marshal", "broodmother", "grave_tyrant", "void_stalker");

    /** Where a condition stands: {@code current} of {@code max}; done when current reaches max. */
    public record Progress(int current, int max) {
        public boolean done() {
            return max > 0 && current >= max;
        }
    }

    private TitleConditions() {
    }

    private static int num(JsonObject o, String key, int fallback) {
        return o.has(key) ? o.get(key).getAsInt() : fallback;
    }

    private static Progress of(long current, long max) {
        int goal = (int) Math.min(Integer.MAX_VALUE, Math.max(1, max));
        return new Progress((int) Math.max(0, Math.min(current, goal)), goal);
    }

    public static Progress check(ServerPlayer player, JsonObject c, TitleState state) {
        String type = c.get("type").getAsString();
        switch (type) {
            case "skill_level": {
                int level = num(c, "level", 1);
                return of(SkillLevels.level(player, Identifier.parse(c.get("skill").getAsString())), level);
            }
            case "any_skill_level": {
                int best = 0;
                for (Identifier skill : SKILLS) {
                    best = Math.max(best, SkillLevels.level(player, skill));
                }
                return of(best, num(c, "level", 1));
            }
            case "all_skills": {
                int level = num(c, "level", 1);
                int have = 0;
                for (Identifier skill : SKILLS) {
                    if (SkillLevels.level(player, skill) >= level) {
                        have++;
                    }
                }
                return of(have, SKILLS.size());
            }
            case "skyrpg_level":
                return of(SkyRpgLevel.level(player), num(c, "level", 1));
            case "slayer_level": {
                int level = num(c, "level", 1);
                List<String> lines = strings(c, "lines", SLAYERS);
                boolean all = c.has("all") && c.get("all").getAsBoolean();
                int have = 0;
                int best = 0;
                for (String line : lines) {
                    int now = SkillLevels.level(player, Identifier.fromNamespaceAndPath("skyrpg_combat", "slayer/" + line));
                    best = Math.max(best, now);
                    if (now >= level) {
                        have++;
                    }
                }
                return all ? of(have, lines.size()) : of(best, level);
            }
            case "boss_kill": {
                List<String> bosses = strings(c, "bosses", List.of());
                int kills = num(c, "kills", 1);
                int need = Math.min(bosses.size(), num(c, "need", bosses.size()));
                int have = 0;
                for (String boss : bosses) {
                    if (bossKills(state, boss) >= kills) {
                        have++;
                    }
                }
                return of(have, Math.max(1, need));
            }
            case "kind_kills":
                return of(state.counter("kind:" + c.get("kind").getAsString()), num(c, "count", 1));
            case "collections": {
                int tier = num(c, "tier", 1);
                int count = num(c, "count", 1);
                int have = 0;
                int total = 0;
                for (CollectionDef def : Collections.all()) {
                    total++;
                    if (Collections.tier(player, def.id()) >= Math.min(tier, def.tierCount())) {
                        have++;
                    }
                }
                return of(have, count < 0 ? Math.max(1, total) : count);
            }
            case "coins":
                return of(Wallet.balance(player), num(c, "amount", 1));
            case "chronicle": {
                QuestState quests = QuestState.of(player);
                int total = 0;
                int done = 0;
                for (QuestData.Quest quest : QuestData.quests().values()) {
                    if (quest.side()) {
                        continue;
                    }
                    total++;
                    if (quests.isDone(quest.id().toString())) {
                        done++;
                    }
                }
                return of(done, Math.max(1, total));
            }
            case "stat": {
                Identifier id = Identifier.parse(c.get("stat").getAsString());
                long value = player.getStats().getValue(Stats.CUSTOM.get(id));
                long divisor = Math.max(1, num(c, "divisor", 1));
                return of(value / divisor, num(c, "amount", 1));
            }
            case "counter":
                return of(state.counter(c.get("key").getAsString()), num(c, "amount", 1));
            case "flag":
                return of(state.flags().contains(c.get("flag").getAsString()) ? 1 : 0, 1);
            default:
                return new Progress(0, 1);
        }
    }

    private static List<String> strings(JsonObject o, String key, List<String> fallback) {
        if (!o.has(key)) {
            return fallback;
        }
        List<String> list = new ArrayList<>();
        JsonElement element = o.get(key);
        if (element.isJsonArray()) {
            JsonArray array = element.getAsJsonArray();
            array.forEach(item -> list.add(item.getAsString()));
        } else {
            list.add(element.getAsString());
        }
        return list;
    }

    /** Kills of a boss: by full id, or by path when the id has no namespace (so "ender_wyrm" matches whichever mod owns it). */
    private static int bossKills(TitleState state, String boss) {
        if (boss.contains(":")) {
            return state.counter("boss:" + boss);
        }
        int total = 0;
        String suffix = ":" + boss;
        for (var entry : state.counters().entrySet()) {
            if (entry.getKey().startsWith("boss:") && entry.getKey().endsWith(suffix)) {
                total += entry.getValue();
            }
        }
        return total;
    }
}
