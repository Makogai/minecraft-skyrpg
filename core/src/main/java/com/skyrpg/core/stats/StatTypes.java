package com.skyrpg.core.stats;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Every stat in the game, in display order. Ids are what item, skill and set data files use. Symbols are
 * SkyRPG's own 8x8 icons (private-use characters drawn by the font in core's assets); code draws them in
 * the stat's colour.
 */
public final class StatTypes {
    private static final Map<String, StatType> TYPES = new LinkedHashMap<>();

    public static final StatType HEALTH = add("health", "Health", "\uE100", 0xFFFF5555, 100, false,
            "Your maximum health. 100 health is 10 hearts.");
    public static final StatType DEFENSE = add("defense", "Defense", "\uE101", 0xFF55FF55, 0, false,
            "Reduces damage you take. 100 defense blocks half of it.");
    public static final StatType STRENGTH = add("strength", "Strength", "\uE102", 0xFFE5534B, 0, false,
            "Each point adds 1% to your melee damage.");
    public static final StatType CRIT_CHANCE = add("crit_chance", "Crit Chance", "\uE103", 0xFF5C8DFF, 30, true,
            "Chance for a hit to be a critical hit.");
    public static final StatType CRIT_DAMAGE = add("crit_damage", "Crit Damage", "\uE104", 0xFF5C8DFF, 50, true,
            "Extra damage dealt by critical hits.");
    public static final StatType SPEED = add("speed", "Speed", "\uE105", 0xFFFFFFFF, 100, false,
            "How fast you walk. 100 is normal.");
    public static final StatType INTELLIGENCE = add("intelligence", "Intelligence", "\uE106", 0xFF55FFFF, 0, false,
            "Each point adds 1 to your mana (100 to start). Abilities cost mana.");
    public static final StatType MINING_SPEED = add("mining_speed", "Mining Speed", "\uE107", 0xFFFFAA00, 0, false,
            "How fast you break blocks. 100 makes it twice as fast.");
    public static final StatType MINING_FORTUNE = add("mining_fortune", "Mining Fortune", "\uE108", 0xFFFFAA00, 0, false,
            "Extra drops from ores. Every 100 is one more drop.");
    public static final StatType FARMING_FORTUNE = add("farming_fortune", "Farming Fortune", "\uE109", 0xFFFFAA00, 0, false,
            "Extra drops from crops. Every 100 is one more drop.");
    public static final StatType FORAGING_FORTUNE = add("foraging_fortune", "Foraging Fortune", "\uE10A", 0xFFFFAA00, 0, false,
            "Extra drops from logs. Every 100 is one more drop.");
    public static final StatType MAGIC_FIND = add("magic_find", "Magic Find", "\uE10B", 0xFF55FFFF, 0, false,
            "Better odds for rare drops from mobs and bosses.");
    public static final StatType FARMING_WISDOM = add("farming_wisdom", "Farming Insight", "\uE10C", 0xFF3FD0D4, 0, false,
            "Each point is 1% more Farming XP.");
    public static final StatType MINING_WISDOM = add("mining_wisdom", "Mining Insight", "\uE10D", 0xFF3FD0D4, 0, false,
            "Each point is 1% more Mining XP.");
    public static final StatType FORAGING_WISDOM = add("foraging_wisdom", "Foraging Insight", "\uE10E", 0xFF3FD0D4, 0, false,
            "Each point is 1% more Foraging XP.");
    public static final StatType COMBAT_WISDOM = add("combat_wisdom", "Combat Insight", "\uE10F", 0xFF3FD0D4, 0, false,
            "Each point is 1% more Combat XP.");
    public static final StatType FISHING_SPEED = add("fishing_speed", "Fishing Speed", "\uE114", 0xFF55FFFF, 0, false,
            "Fish bite sooner. Every 25 is one more level of Lure, up to four.");
    public static final StatType SEA_CREATURE_CHANCE = add("sea_creature_chance", "Sea Creature Chance", "\uE115", 0xFF3FA9F5, 5, true,
            "The chance that your line pulls up a sea creature instead of a fish.");
    public static final StatType FISHING_FORTUNE = add("fishing_fortune", "Fishing Fortune", "\uE116", 0xFFFFAA00, 0, false,
            "More Tidescales from your catches. Every 100 is one more.");
    public static final StatType FISHING_WISDOM = add("fishing_wisdom", "Fishing Insight", "\uE117", 0xFF3FD0D4, 0, false,
            "Each point is 1% more Fishing XP.");
    /**
     * Weapon damage. Only items carry it (it sets the item's vanilla attack damage, 5 damage = 1 vanilla
     * point); it is not a player stat that sources add to.
     */
    public static final StatType DAMAGE = add("damage", "Damage", "\uE110", 0xFFFF5555, 0, false,
            "The weapon's base damage, before Strength and crits.");

    private StatTypes() {
    }

    private static StatType add(String id, String name, String symbol, int color, double base, boolean percent, String description) {
        StatType type = new StatType(id, name, symbol, color, base, percent, description);
        TYPES.put(id, type);
        return type;
    }

    public static StatType get(String id) {
        return TYPES.get(id);
    }

    public static Collection<StatType> all() {
        return Collections.unmodifiableCollection(TYPES.values());
    }

    /** Player stats: everything except weapon-only {@link #DAMAGE}. */
    public static boolean isPlayerStat(StatType type) {
        return type != DAMAGE;
    }

    /** Icons that aren't stats, from the same font. */
    public static final String MANA_ICON = "\uE111";
    public static final String COIN_ICON = "\uE112";
    public static final String LEVEL_ICON = "\uE113";

    /** Vanilla health is our health / 5: 100 health = 20 vanilla HP = 10 hearts. */
    public static final double HEALTH_SCALE = 5;
}
