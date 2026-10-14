package com.skyrpg.pets;

import java.util.Locale;
import net.minecraft.ChatFormatting;

/** A pet's rarity: rolled when its egg hatches; better rarities give more per level. */
public enum PetRarity {
    COMMON(1.0, ChatFormatting.WHITE, 60),
    UNCOMMON(1.25, ChatFormatting.GREEN, 25),
    RARE(1.5, ChatFormatting.BLUE, 10),
    EPIC(1.75, ChatFormatting.DARK_PURPLE, 4),
    LEGENDARY(2.0, ChatFormatting.GOLD, 1);

    private final double multiplier;
    private final ChatFormatting color;
    private final int weight;

    PetRarity(double multiplier, ChatFormatting color, int weight) {
        this.multiplier = multiplier;
        this.color = color;
        this.weight = weight;
    }

    public double multiplier() {
        return multiplier;
    }

    public ChatFormatting color() {
        return color;
    }

    /** How often an egg hatches into this rarity (golden eggs skip Common and Uncommon). */
    public int weight() {
        return weight;
    }

    public String label() {
        return name().charAt(0) + name().substring(1).toLowerCase(Locale.ROOT);
    }

    public static PetRarity parse(String name) {
        try {
            return valueOf(name.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return COMMON;
        }
    }
}
