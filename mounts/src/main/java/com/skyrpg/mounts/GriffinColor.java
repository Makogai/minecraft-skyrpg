package com.skyrpg.mounts;

import java.util.Locale;
import net.minecraft.util.RandomSource;

/**
 * The griffin's ten feather colours in four rarities. The id is what saves and the whistle's model pick use; the order is
 * what the entity stores, so new colours only go at the end.
 */
public enum GriffinColor {
    GOLDEN("Golden", 0xE0B448, GriffinRarity.COMMON),
    STORM("Storm", 0x7488A0, GriffinRarity.COMMON),
    SNOW("Snow", 0xE8F0F8, GriffinRarity.COMMON),
    RUSSET("Russet", 0xC4663A, GriffinRarity.COMMON),
    MIDNIGHT("Midnight", 0x6A62C8, GriffinRarity.UNCOMMON),
    VERDANT("Verdant", 0x3CB898, GriffinRarity.UNCOMMON),
    EMBER("Ember", 0xFF7A1A, GriffinRarity.RARE),
    FROST("Frost", 0x8AD8FF, GriffinRarity.RARE),
    PRISMATIC("Prismatic", 0xF0B8F4, GriffinRarity.LEGENDARY),
    ASTRAL("Astral", 0x6878F0, GriffinRarity.LEGENDARY);

    private final String label;
    private final int rgb;
    private final GriffinRarity rarity;

    GriffinColor(String label, int rgb, GriffinRarity rarity) {
        this.label = label;
        this.rgb = rgb;
        this.rarity = rarity;
    }

    public GriffinRarity rarity() {
        return rarity;
    }

    /** Whistle and list name colour: the rarity colour, except Common, which keeps its feather colour. */
    public int nameRgb() {
        return rarity == GriffinRarity.COMMON ? rgb : rarity.rgb();
    }

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    public String label() {
        return label;
    }

    /** The colour as 0xRRGGBB, for text and the K menu. */
    public int rgb() {
        return rgb;
    }

    public static GriffinColor parse(String text) {
        for (GriffinColor color : values()) {
            if (color.id().equalsIgnoreCase(text)) {
                return color;
            }
        }
        return GOLDEN;
    }

    public static GriffinColor byOrdinal(int ordinal) {
        GriffinColor[] all = values();
        return all[Math.floorMod(ordinal, all.length)];
    }

    /** Wild griffins (nests, the Storm Griffin boss): each colour by its rarity's weight (98 in all: 80 common, 12 uncommon, 5 rare, 1 legendary). */
    public static GriffinColor random(RandomSource random) {
        double total = 0;
        for (GriffinColor color : values()) {
            total += color.rarity.weight();
        }
        double roll = random.nextDouble() * total;
        for (GriffinColor color : values()) {
            roll -= color.rarity.weight();
            if (roll < 0) {
                return color;
            }
        }
        return GOLDEN;
    }
}
