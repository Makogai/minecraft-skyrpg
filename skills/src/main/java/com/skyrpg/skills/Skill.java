package com.skyrpg.skills;

import java.util.List;
import java.util.Map;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.TextColor;
import net.minecraft.resources.Identifier;

/**
 * One skill (Farming, Mining, ...), loaded from {@code data/<namespace>/skyrpg/skills/<name>.json}:
 * <pre>{"name": "Farming", "color": "yellow", "order": 1, "icon": "minecraft:golden_hoe", "description": "Harvest crops."}</pre>
 * Optional {@code "levels"}: XP needed for each level in turn; defaults to {@link #DEFAULT_LEVELS}.
 * The number of entries is the max level. Optional {@code "rewards"}: stats gained per level, e.g.
 * {@code {"farming_fortune": 4, "health": 2}}.
 */
public final class Skill {
    /** Hypixel SkyBlock's skill curve: XP needed for level 1, 2, ... 50. */
    public static final List<Long> DEFAULT_LEVELS = List.of(
            50L, 125L, 200L, 300L, 500L, 750L, 1_000L, 1_500L, 2_000L, 3_500L,
            5_000L, 7_500L, 10_000L, 15_000L, 20_000L, 30_000L, 50_000L, 75_000L, 100_000L, 200_000L,
            300_000L, 400_000L, 500_000L, 600_000L, 700_000L, 800_000L, 900_000L, 1_000_000L, 1_100_000L, 1_200_000L,
            1_300_000L, 1_400_000L, 1_500_000L, 1_600_000L, 1_700_000L, 1_800_000L, 1_900_000L, 2_000_000L, 2_100_000L, 2_200_000L,
            2_300_000L, 2_400_000L, 2_500_000L, 2_600_000L, 2_750_000L, 2_900_000L, 3_100_000L, 3_400_000L, 3_700_000L, 4_000_000L);

    private final Identifier id;
    private final String name;
    private final TextColor color;
    private final int order;
    private final List<Long> levels;
    private final Identifier icon;
    private final String description;
    private final Map<String, Double> rewardsPerLevel;
    /** cumulative[n] = total XP needed to reach level n. */
    private final double[] cumulative;

    public Skill(Identifier id, String name, TextColor color, int order, List<Long> levels, Identifier icon, String description,
                 Map<String, Double> rewardsPerLevel) {
        this.id = id;
        this.name = name;
        this.color = color;
        this.order = order;
        this.levels = List.copyOf(levels);
        this.icon = icon;
        this.description = description;
        this.rewardsPerLevel = Map.copyOf(rewardsPerLevel);
        this.cumulative = new double[levels.size() + 1];
        for (int i = 0; i < levels.size(); i++) {
            cumulative[i + 1] = cumulative[i] + levels.get(i);
        }
    }

    public Identifier id() {
        return id;
    }

    public String name() {
        return name;
    }

    public TextColor color() {
        return color;
    }

    public int order() {
        return order;
    }

    /** XP needed for each level in turn. */
    public List<Long> levels() {
        return levels;
    }

    /** Item id shown for this skill in menus. */
    public Identifier icon() {
        return icon;
    }

    /** How the skill is levelled, shown in menus. */
    public String description() {
        return description;
    }

    /** Stats gained for every level, by stat id. */
    public Map<String, Double> rewardsPerLevel() {
        return rewardsPerLevel;
    }

    public int maxLevel() {
        return levels.size();
    }

    public MutableComponent displayName() {
        return Component.literal(name).withColor(color);
    }

    public int levelFor(double totalXp) {
        int level = 0;
        while (level < maxLevel() && totalXp >= cumulative[level + 1]) {
            level++;
        }
        return level;
    }

    /** XP needed to go from this level to the next, or 0 at max level. */
    public long xpForNextLevel(int level) {
        return level >= maxLevel() ? 0 : levels.get(level);
    }

    /** How far into the current level this total XP is. */
    public double xpIntoLevel(double totalXp) {
        return totalXp - cumulative[levelFor(totalXp)];
    }
}
