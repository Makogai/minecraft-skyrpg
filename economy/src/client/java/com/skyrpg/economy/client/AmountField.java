package com.skyrpg.economy.client;

import java.math.BigDecimal;
import java.util.Locale;

/**
 * The number you type into a shop or bank screen. Plain digits always work; with suffixes on, "2.5k",
 * "3m" and "1b" do too, and commas are ignored.
 */
final class AmountField {
    private static final int MAX_LENGTH = 14;

    private final boolean suffixes;
    private final StringBuilder text = new StringBuilder();

    AmountField(boolean suffixes) {
        this.suffixes = suffixes;
    }

    String text() {
        return text.toString();
    }

    boolean isEmpty() {
        return text.isEmpty();
    }

    void set(long value) {
        text.setLength(0);
        text.append(value);
    }

    void clear() {
        text.setLength(0);
    }

    /** Takes a typed character if it can be part of an amount. */
    boolean type(char c) {
        char lower = Character.toLowerCase(c);
        boolean allowed = Character.isDigit(c) || (suffixes && (lower == '.' || lower == ',' || lower == 'k' || lower == 'm' || lower == 'b'));
        if (!allowed || text.length() >= MAX_LENGTH) {
            return false;
        }
        text.append(lower);
        return true;
    }

    void backspace(boolean word) {
        if (word) {
            text.setLength(0);
        } else if (!text.isEmpty()) {
            text.setLength(text.length() - 1);
        }
    }

    /** The amount typed, or -1 if it isn't a valid whole number of coins (0 when empty). */
    long value() {
        String raw = text.toString().replace(",", "").toLowerCase(Locale.ROOT);
        if (raw.isEmpty()) {
            return 0;
        }
        long multiplier = 1;
        char last = raw.charAt(raw.length() - 1);
        if (last == 'k' || last == 'm' || last == 'b') {
            multiplier = last == 'k' ? 1_000L : last == 'm' ? 1_000_000L : 1_000_000_000L;
            raw = raw.substring(0, raw.length() - 1);
        }
        try {
            BigDecimal value = new BigDecimal(raw).multiply(BigDecimal.valueOf(multiplier));
            return value.signum() < 0 || value.stripTrailingZeros().scale() > 0 ? -1 : value.longValueExact();
        } catch (NumberFormatException | ArithmeticException e) {
            return -1;
        }
    }

    /** True if a suffix makes the typed text differ from the plain number. */
    boolean usesShorthand() {
        return text.indexOf("k") >= 0 || text.indexOf("m") >= 0 || text.indexOf("b") >= 0;
    }
}
