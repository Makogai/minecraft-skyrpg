package com.skyrpg.magic;

import java.util.Locale;

/** What a spell is made of. Colours the spell's name and wheel segment; wands can strengthen one element. */
public enum Element {
    FIRE("Fire", 0xFFFF7A2A),
    FROST("Frost", 0xFF7FD8FF),
    LIGHTNING("Lightning", 0xFFFFE566),
    ARCANE("Arcane", 0xFFC27BFF),
    NATURE("Nature", 0xFF6BE36B);

    private final String label;
    private final int color;

    Element(String label, int color) {
        this.label = label;
        this.color = color;
    }

    public String label() {
        return label;
    }

    /** ARGB. */
    public int color() {
        return color;
    }

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static Element parse(String name) {
        return valueOf(name.toUpperCase(Locale.ROOT));
    }
}
