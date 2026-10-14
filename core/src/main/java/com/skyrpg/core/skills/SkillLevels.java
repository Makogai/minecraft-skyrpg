package com.skyrpg.core.skills;

import java.util.function.ToIntBiFunction;
import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.EventFactory;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

/**
 * Skill levels for any mod, without depending on the skills mod: shops lock offers behind them, nodes and
 * gear check them. The {@code skyrpg_skills} mod installs the lookup; without it every level is 0.
 */
public final class SkillLevels {
    /** The skills mod's ids, e.g. {@code skyrpg_skills:mining}. */
    public static final Identifier MINING = Identifier.fromNamespaceAndPath("skyrpg_skills", "mining");
    public static final Identifier FARMING = Identifier.fromNamespaceAndPath("skyrpg_skills", "farming");
    public static final Identifier COMBAT = Identifier.fromNamespaceAndPath("skyrpg_skills", "combat");
    public static final Identifier FORAGING = Identifier.fromNamespaceAndPath("skyrpg_skills", "foraging");
    public static final Identifier FISHING = Identifier.fromNamespaceAndPath("skyrpg_skills", "fishing");

    /**
     * A player levelled up, while the skills mod's level-up message is being shown: listeners can add their own
     * lines to it (like "NEW RECIPES"). Without the skills mod it never fires.
     */
    public static final Event<LevelUp> LEVEL_UP = EventFactory.createArrayBacked(LevelUp.class, listeners -> (player, skill, oldLevel, newLevel) -> {
        for (LevelUp listener : listeners) {
            listener.onLevelUp(player, skill, oldLevel, newLevel);
        }
    });

    @FunctionalInterface
    public interface LevelUp {
        void onLevelUp(ServerPlayer player, Identifier skill, int oldLevel, int newLevel);
    }

    /** XP earned by playing (after Insight), in any skill: pets and the like take their share from it. */
    public static final Event<XpEarned> XP_EARNED = EventFactory.createArrayBacked(XpEarned.class, listeners -> (player, skill, xp) -> {
        for (XpEarned listener : listeners) {
            listener.onXpEarned(player, skill, xp);
        }
    });

    @FunctionalInterface
    public interface XpEarned {
        void onXpEarned(ServerPlayer player, Identifier skill, double xp);
    }

    private static ToIntBiFunction<Player, Identifier> lookup = (player, skill) -> 0;
    /** Other levels that recipes and shops may require, by namespace (the combat mod's slayer levels). */
    private static final Map<String, ToIntBiFunction<Player, Identifier>> EXTRA = new ConcurrentHashMap<>();
    private static final Map<Identifier, String> EXTRA_NAMES = new ConcurrentHashMap<>();
    private static XpGiver xpGiver = (player, skill, xp) -> {
    };

    /** Gives skill XP (the skills mod installs it). */
    @FunctionalInterface
    public interface XpGiver {
        void give(ServerPlayer player, Identifier skill, double xp);
    }

    private SkillLevels() {
    }

    public static void install(ToIntBiFunction<Player, Identifier> installed) {
        lookup = installed;
    }

    /**
     * Levels that aren't skills but can gate recipes and shop offers like one, e.g. {@code skyrpg_combat:slayer/broodmother}:
     * every id in {@code namespace} is answered by {@code provider}.
     */
    public static void installExtra(String namespace, ToIntBiFunction<Player, Identifier> provider) {
        EXTRA.put(namespace, provider);
    }

    /** The name shown for an extra level ("Broodmother Slayer"). */
    public static void nameExtra(Identifier id, String name) {
        EXTRA_NAMES.put(id, name);
    }

    public static void installXp(XpGiver installed) {
        xpGiver = installed;
    }

    /** Gives XP in a skill, as if earned there (level-ups and all). For XP that isn't from breaking a block. */
    public static void giveXp(ServerPlayer player, Identifier skill, double xp) {
        xpGiver.give(player, skill, xp);
    }

    /** The player's level in that skill (0 if the skill is unknown). Works on both sides once skills are synced. */
    public static int level(Player player, Identifier skill) {
        ToIntBiFunction<Player, Identifier> extra = EXTRA.get(skill.getNamespace());
        return extra != null ? extra.applyAsInt(player, skill) : lookup.applyAsInt(player, skill);
    }

    /** "Mining" from {@code skyrpg_skills:mining}, for messages like "Requires Mining 10". */
    public static String displayName(Identifier skill) {
        String named = EXTRA_NAMES.get(skill);
        if (named != null) {
            return named;
        }
        String path = skill.getPath();
        return path.isEmpty() ? path : Character.toUpperCase(path.charAt(0)) + path.substring(1).replace('_', ' ');
    }
}
