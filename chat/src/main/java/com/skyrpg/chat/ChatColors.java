package com.skyrpg.chat;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.TextColor;

/**
 * Chat colours. The RGB values match core's client {@code Theme}, so chat reads as part of the same game as the
 * SkyRPG menu (the server can't use the client-only Theme class itself).
 */
public final class ChatColors {
    public static final int TEXT = 0xE9EDF4;
    public static final int SOFT = 0xC9CED8;
    public static final int MUTED = 0x8D96A8;
    public static final int DIM = 0x586174;
    public static final int ACCENT = 0xF2B33D;
    public static final int RED = 0xFF6464;
    public static final int GREEN = 0x5CD68A;
    public static final int BLUE = 0x5CA8FF;
    public static final int AQUA = 0x5CE1E6;
    public static final int PURPLE = 0xB98CFF;
    /** "From" and "To" in whispers: a pink-violet nothing else in chat uses. */
    public static final int WHISPER = 0xF08CE8;

    /**
     * SkyRPG Level colours, one per 40 levels, the same steps Hypixel SkyBlock uses: grey, white, yellow, green,
     * dark green, aqua, dark aqua, blue, pink, purple, gold, red and (480+) dark red.
     */
    private static final ChatFormatting[] LEVEL_TIERS = {
            ChatFormatting.GRAY, ChatFormatting.WHITE, ChatFormatting.YELLOW, ChatFormatting.GREEN, ChatFormatting.DARK_GREEN,
            ChatFormatting.AQUA, ChatFormatting.DARK_AQUA, ChatFormatting.BLUE, ChatFormatting.LIGHT_PURPLE, ChatFormatting.DARK_PURPLE,
            ChatFormatting.GOLD, ChatFormatting.RED, ChatFormatting.DARK_RED};
    private static final String[] TIER_NAMES = {"Grey", "White", "Yellow", "Green", "Dark Green", "Aqua", "Dark Aqua", "Blue", "Pink",
            "Purple", "Gold", "Red", "Dark Red"};
    public static final int LEVELS_PER_TIER = 40;

    private ChatColors() {
    }

    public static TextColor rgb(int color) {
        return TextColor.fromRgb(color & 0xFFFFFF);
    }

    public static int tierIndex(int level) {
        return Math.clamp(level / LEVELS_PER_TIER, 0, LEVEL_TIERS.length - 1);
    }

    public static ChatFormatting tier(int level) {
        return LEVEL_TIERS[tierIndex(level)];
    }

    public static String tierName(int index) {
        return TIER_NAMES[Math.clamp(index, 0, TIER_NAMES.length - 1)];
    }

    public static int tierCount() {
        return LEVEL_TIERS.length;
    }
}
