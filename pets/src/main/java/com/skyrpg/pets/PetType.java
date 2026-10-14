package com.skyrpg.pets;

import com.skyrpg.core.skills.SkillLevels;
import com.skyrpg.core.stats.StatType;
import com.skyrpg.core.stats.StatTypes;
import java.util.Locale;
import java.util.Map;
import net.minecraft.resources.Identifier;

/**
 * The five pets, one per skill: which skill feeds them XP, the two stats they give (per level, at Common rarity), and
 * the perks they unlock at level 25 and 50 (gear effects, done by the skill's own mod).
 */
public enum PetType {
    MOLE("Mole", SkillLevels.MINING, StatTypes.MINING_SPEED, 2.0, StatTypes.MINING_FORTUNE, 0.8,
            Map.of(Identifier.parse("skyrpg_mining:node_speed"), 0.15), "Nodes you mine regrow 15% faster",
            Map.of(Identifier.parse("skyrpg_mining:passive_echo"), 1.0), "A free Echo Sense every minute"),
    BEE("Bumblebee", SkillLevels.FARMING, StatTypes.FARMING_FORTUNE, 1.6, StatTypes.FARMING_WISDOM, 0.1,
            Map.of(Identifier.parse("skyrpg_farming:dew_bonus"), 0.15), "15% more Dewdrops",
            Map.of(Identifier.parse("skyrpg_farming:growth_aura"), 4.0), "Crops within 4 blocks of you grow faster"),
    OWL("Owl", SkillLevels.FORAGING, StatTypes.FORAGING_FORTUNE, 1.6, StatTypes.SPEED, 0.2,
            Map.of(Identifier.parse("skyrpg_foraging:soft_landing"), 3.0), "Falls 3 blocks further don't hurt",
            Map.of(Identifier.parse("skyrpg_foraging:rare_finds"), 0.5), "Rare finds from logs 50% more likely"),
    PUP("Wolf Pup", SkillLevels.COMBAT, StatTypes.STRENGTH, 0.8, StatTypes.CRIT_DAMAGE, 0.6,
            Map.of(Identifier.parse("skyrpg_combat:life_steal"), 0.02), "Heals 2% of the melee damage you deal",
            Map.of(Identifier.parse("skyrpg_combat:crit_heal"), 0.03), "Critical hits heal 3% of your health"),
    OTTER("Otter", SkillLevels.FISHING, StatTypes.FISHING_SPEED, 0.8, StatTypes.SEA_CREATURE_CHANCE, 0.08,
            Map.of(Identifier.parse("skyrpg_fishing:double_catch"), 0.05), "5% chance a catch comes up twice",
            Map.of(Identifier.parse("skyrpg_fishing:tideborn"), 1.0), "Breathe and swim easily underwater");

    private final String name;
    private final Identifier skill;
    private final StatType first;
    private final double firstPerLevel;
    private final StatType second;
    private final double secondPerLevel;
    private final Map<Identifier, Double> perk25;
    private final String perk25Text;
    private final Map<Identifier, Double> perk50;
    private final String perk50Text;

    PetType(String name, Identifier skill, StatType first, double firstPerLevel, StatType second, double secondPerLevel,
            Map<Identifier, Double> perk25, String perk25Text, Map<Identifier, Double> perk50, String perk50Text) {
        this.name = name;
        this.skill = skill;
        this.first = first;
        this.firstPerLevel = firstPerLevel;
        this.second = second;
        this.secondPerLevel = secondPerLevel;
        this.perk25 = perk25;
        this.perk25Text = perk25Text;
        this.perk50 = perk50;
        this.perk50Text = perk50Text;
    }

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    public String displayName() {
        return name;
    }

    public Identifier skill() {
        return skill;
    }

    /** The pet's stats at this level and rarity. */
    public Map<StatType, Double> stats(int level, PetRarity rarity) {
        return Map.of(first, firstPerLevel * level * rarity.multiplier(), second, secondPerLevel * level * rarity.multiplier());
    }

    public Map<Identifier, Double> perks(int level) {
        if (level >= 50) {
            Map<Identifier, Double> both = new java.util.HashMap<>(perk25);
            perk50.forEach((id, value) -> both.merge(id, value, Double::sum));
            return both;
        }
        return level >= 25 ? perk25 : Map.of();
    }

    public String perk25Text() {
        return perk25Text;
    }

    public String perk50Text() {
        return perk50Text;
    }
}
