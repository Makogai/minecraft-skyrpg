package com.skyrpg.enchants;

import java.util.Locale;

/**
 * How rare an enchant is: its colour, what it costs at the table (see {@code rules.json}) and how much a Codex
 * discovery is worth. Ultimate enchants are tome-only and an item holds just one of them.
 */
public enum EnchantRarity {
    COMMON(0xFFE9EDF4),
    UNCOMMON(0xFF55FF55),
    RARE(0xFF6C8CFF),
    EPIC(0xFFC659FF),
    LEGENDARY(0xFFFFAA00),
    ULTIMATE(0xFFFF55FF);

    private final int color;

    EnchantRarity(int color) {
        this.color = color;
    }

    public int color() {
        return color;
    }

    /** "rare", as data files and rules.json spell it. */
    public String key() {
        return name().toLowerCase(Locale.ROOT);
    }

    /** "Rare". */
    public String label() {
        return name().charAt(0) + name().substring(1).toLowerCase(Locale.ROOT);
    }

    public static EnchantRarity parse(String name) {
        return valueOf(name.toUpperCase(Locale.ROOT));
    }
}
