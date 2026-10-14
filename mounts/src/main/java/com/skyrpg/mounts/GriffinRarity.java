package com.skyrpg.mounts;

import java.util.Locale;
import net.minecraft.ChatFormatting;

/** How rare a griffin colour is: the colours match the pets' rarity colours. {@code weight} is each colour's wild spawn weight. */
public enum GriffinRarity {
    COMMON(ChatFormatting.WHITE, 20.0),
    UNCOMMON(ChatFormatting.GREEN, 6.0),
    RARE(ChatFormatting.BLUE, 2.5),
    LEGENDARY(ChatFormatting.GOLD, 0.5);

    private final ChatFormatting color;
    private final double weight;

    GriffinRarity(ChatFormatting color, double weight) {
        this.color = color;
        this.weight = weight;
    }

    public ChatFormatting color() {
        return color;
    }

    /** The rarity colour as 0xRRGGBB. */
    public int rgb() {
        return switch (this) {
            case COMMON -> 0xFFFFFF;
            case UNCOMMON -> 0x55FF55;
            case RARE -> 0x5555FF;
            case LEGENDARY -> 0xFFAA00;
        };
    }

    public double weight() {
        return weight;
    }

    public String label() {
        return name().charAt(0) + name().substring(1).toLowerCase(Locale.ROOT);
    }

    public boolean announced() {
        return this == RARE || this == LEGENDARY;
    }
}
