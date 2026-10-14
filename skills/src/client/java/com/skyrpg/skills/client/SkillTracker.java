package com.skyrpg.skills.client;

import com.skyrpg.core.client.hud.Banners;
import com.skyrpg.core.client.ui.Theme;
import com.skyrpg.core.stats.StatType;
import com.skyrpg.core.stats.StatTypes;
import com.skyrpg.core.text.Msg;
import com.skyrpg.skills.PlayerSkills;
import com.skyrpg.skills.Skill;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.util.Util;

/**
 * Watches the synced skill XP for increases, to know which skill was trained last, how much the
 * last gain was and the XP-per-hour rate (over the last 10 minutes of activity).
 */
final class SkillTracker {
    private static final long RATE_WINDOW_MS = 10 * 60 * 1000;

    private record Gain(long at, double amount) {
    }

    private static final Map<Skill, Double> LAST_XP = new HashMap<>();
    private static final Map<Skill, Deque<Gain>> GAINS = new HashMap<>();
    private static Skill lastSkill;
    private static double lastAmount;
    private static long lastGainAt;

    private SkillTracker() {
    }

    static void tick(Minecraft minecraft) {
        if (minecraft.player == null) {
            LAST_XP.clear();
            return;
        }
        long now = Util.getMillis();
        for (Skill skill : ClientSkills.all()) {
            double xp = PlayerSkills.getXp(minecraft.player, skill);
            Double before = LAST_XP.put(skill, xp);
            if (before != null && xp > before) {
                int oldLevel = skill.levelFor(before);
                int newLevel = skill.levelFor(xp);
                if (newLevel > oldLevel) {
                    announceLevelUp(skill, oldLevel, newLevel);
                }
                lastSkill = skill;
                lastAmount = xp - before;
                lastGainAt = now;
                GAINS.computeIfAbsent(skill, s -> new ArrayDeque<>()).addLast(new Gain(now, xp - before));
            }
        }
        GAINS.values().forEach(gains -> {
            while (!gains.isEmpty() && now - gains.peekFirst().at() > RATE_WINDOW_MS) {
                gains.removeFirst();
            }
        });
    }

    private static void announceLevelUp(Skill skill, int oldLevel, int newLevel) {
        int color = 0xFF000000 | skill.color().getValue();
        String from = oldLevel == 0 ? "" : Msg.roman(oldLevel) + " → ";
        Component title = Component.literal(skill.name() + " ").append(Component.literal(from).withColor(Theme.MUTED))
                .append(Component.literal(Msg.roman(newLevel)).withColor(color));
        Banners.show(new Banners.Banner(ClientSkills.icon(skill), "SKILL LEVEL UP", title, rewards(skill, newLevel - oldLevel, newLevel), color));
    }

    /** "+4 ☘ Farming Fortune  +2 ❤ Health" for the levels gained, or the XP still needed if the skill has no rewards. */
    private static Component rewards(Skill skill, int levels, int newLevel) {
        if (skill.rewardsPerLevel().isEmpty()) {
            return Component.literal(newLevel >= skill.maxLevel() ? "Max level reached!"
                    : Msg.number(skill.xpForNextLevel(newLevel)) + " XP to " + Msg.roman(newLevel + 1));
        }
        MutableComponent line = Component.empty();
        skill.rewardsPerLevel().forEach((statId, perLevel) -> {
            StatType stat = StatTypes.get(statId);
            if (stat != null) {
                line.append(Component.literal(stat.formatBonus(perLevel * levels) + " " + stat.symbol() + " " + stat.name() + "  ").withColor(stat.color()));
            }
        });
        return line;
    }

    static Skill lastSkill() {
        return lastSkill;
    }

    static double lastAmount() {
        return lastAmount;
    }

    static long millisSinceGain() {
        return lastSkill == null ? Long.MAX_VALUE : Util.getMillis() - lastGainAt;
    }

    /** XP per hour for this skill, extrapolated from gains in the last 10 minutes (0 if idle). */
    static double xpPerHour(Skill skill) {
        Deque<Gain> gains = GAINS.get(skill);
        if (gains == null || gains.size() < 2) {
            return 0;
        }
        double total = gains.stream().mapToDouble(Gain::amount).sum();
        long span = Math.max(30_000, Util.getMillis() - gains.peekFirst().at());
        return total * 3_600_000.0 / span;
    }
}
