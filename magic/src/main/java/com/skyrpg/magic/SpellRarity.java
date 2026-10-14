package com.skyrpg.magic;

import java.util.Locale;

/** Same names and colours as the items mod's rarities (this mod does not depend on it). */
public enum SpellRarity {
    COMMON(0xFFFFFFFF),
    UNCOMMON(0xFF55FF55),
    RARE(0xFF5555FF),
    EPIC(0xFFAA00AA),
    LEGENDARY(0xFFFFAA00);

    private final int color;

    SpellRarity(int color) {
        this.color = color;
    }

    public int color() {
        return color;
    }

    public String label() {
        return name().charAt(0) + name().substring(1).toLowerCase(Locale.ROOT);
    }

    public static SpellRarity parse(String name) {
        return valueOf(name.toUpperCase(Locale.ROOT));
    }
}
