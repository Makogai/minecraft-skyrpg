package com.skyrpg.items;

import java.util.Locale;

/** Item rarity: sets the name colour and the "Rare Sword" line at the bottom of tooltips. */
public enum Rarity {
    COMMON(0xFFFFFFFF),
    UNCOMMON(0xFF55FF55),
    RARE(0xFF5555FF),
    EPIC(0xFFAA00AA),
    LEGENDARY(0xFFFFAA00),
    MYTHIC(0xFFFF55FF),
    SPECIAL(0xFFFF5555);

    private final int color;

    Rarity(int color) {
        this.color = color;
    }

    /** ARGB colour for names and the rarity line. */
    public int color() {
        return color;
    }

    public static Rarity parse(String name) {
        return valueOf(name.toUpperCase(Locale.ROOT));
    }
}
