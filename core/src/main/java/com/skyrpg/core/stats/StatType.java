package com.skyrpg.core.stats;

import com.skyrpg.core.text.Msg;

/**
 * A stat (Health, Strength, ...): how it's shown and the value every player starts with.
 *
 * @param symbol  one character shown before the name, Hypixel style
 * @param color   ARGB colour for the symbol and value
 * @param percent whether values read as percentages (Crit Chance 30%)
 */
public record StatType(String id, String name, String symbol, int color, double base, boolean percent, String description) {

    /** "120" or "30%". */
    public String format(double value) {
        return Msg.number(value) + (percent ? "%" : "");
    }

    /** "+20" or "+5%", for item and reward lines. */
    public String formatBonus(double value) {
        return (value >= 0 ? "+" : "") + format(value);
    }
}
