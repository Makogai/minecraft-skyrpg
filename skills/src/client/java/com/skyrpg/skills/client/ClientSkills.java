package com.skyrpg.skills.client;

import com.skyrpg.skills.PlayerSkills;
import com.skyrpg.skills.Skill;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/** The skill definitions the server sent, plus summary numbers for menus. */
public final class ClientSkills {
    private static List<Skill> skills = List.of();
    private static final Map<Skill, ItemStack> ICONS = new HashMap<>();

    private ClientSkills() {
    }

    static void set(List<Skill> received) {
        skills = List.copyOf(received);
        ICONS.clear();
    }

    public static List<Skill> all() {
        return skills;
    }

    public static ItemStack icon(Skill skill) {
        return ICONS.computeIfAbsent(skill, s -> new ItemStack(BuiltInRegistries.ITEM.getValue(s.icon())));
    }

    /** Level plus progress into the next one, e.g. 4.3. */
    public static double exactLevel(Player player, Skill skill) {
        double xp = PlayerSkills.getXp(player, skill);
        int level = skill.levelFor(xp);
        return level >= skill.maxLevel() ? level : level + skill.xpIntoLevel(xp) / skill.xpForNextLevel(level);
    }

    public static double average(Player player) {
        return skills.isEmpty() ? 0 : skills.stream().mapToDouble(skill -> exactLevel(player, skill)).average().orElse(0);
    }

    public static int totalLevels(Player player) {
        return skills.stream().mapToInt(skill -> PlayerSkills.getLevel(player, skill)).sum();
    }

    public static double totalXp(Player player) {
        return skills.stream().mapToDouble(skill -> PlayerSkills.getXp(player, skill)).sum();
    }
}
