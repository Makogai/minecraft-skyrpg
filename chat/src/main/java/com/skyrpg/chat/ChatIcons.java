package com.skyrpg.chat;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.resources.Identifier;

/**
 * Pixel-art icons in chat. They live in the bitmap font {@code skyrpg_chat:icons} (private-use codepoints, drawn by
 * {@code tools/art/generate_chat_icons.py}); a component uses one by setting that font on its style, so a
 * {@code tellraw} can too: {@code {"text":"","font":"skyrpg_chat:icons"}}. The codepoint table is in
 * docs/design/chat.md.
 *
 * <p>Full-colour icons are given a white text colour so they show as drawn. The grey-scale "frame" pieces (badge
 * brackets, pill chevrons, the level star) take their colour from the text colour, so pass the colour you want.
 */
public final class ChatIcons {
    public static final FontDescription FONT = new FontDescription.Resource(Identifier.fromNamespaceAndPath("skyrpg_chat", "icons"));

    // Emotes
    public static final String HEART = "";
    public static final String STAR = "";
    public static final String SKULL = "";
    public static final String FIRE = "";
    public static final String COIN = "";
    public static final String GEM = "";
    public static final String CROWN = "";
    public static final String SWORD = "";
    public static final String SHIELD = "";
    public static final String PICKAXE = "";
    public static final String AXE = "";
    public static final String HOE = "";
    public static final String ROD = "";
    public static final String POTION = "";
    public static final String TROPHY = "";
    public static final String LIGHTNING = "";
    public static final String MOON = "";
    public static final String SUN = "";
    public static final String CHECK = "";
    public static final String CROSS = "";
    public static final String WAVE = "";
    public static final String NOTE = "";
    public static final String GG = "";
    // UI
    public static final String ENVELOPE = "";
    public static final String ROBOT = "";
    public static final String JOIN = "";
    public static final String LEAVE = "";
    public static final String WELCOME = "";
    // Skills
    public static final String SKILL_COMBAT = "";
    public static final String SKILL_FARMING = "";
    public static final String SKILL_FISHING = "";
    public static final String SKILL_FORAGING = "";
    public static final String SKILL_MINING = "";
    // Rarity gems
    public static final String RARITY_COMMON = "";
    public static final String RARITY_UNCOMMON = "";
    public static final String RARITY_RARE = "";
    public static final String RARITY_EPIC = "";
    public static final String RARITY_LEGENDARY = "";
    public static final String RARITY_MYTHIC = "";
    // Frames (grey-scale, tinted by the text colour)
    public static final String BADGE_LEFT = "";
    public static final String BADGE_FILL = "";
    public static final String BADGE_RIGHT = "";
    public static final String PILL_LEFT = "";
    public static final String PILL_FILL = "";
    public static final String PILL_RIGHT = "";
    public static final String LEVEL = "";

    private static final int WHITE = 0xFFFFFF;

    private ChatIcons() {
    }

    /** A full-colour icon (white text colour, so the sprite shows as drawn). */
    public static MutableComponent icon(String glyph) {
        return tinted(glyph, WHITE);
    }

    /** An icon multiplied by {@code rgb}; for the grey-scale frame pieces. */
    public static MutableComponent tinted(String glyph, int rgb) {
        return Component.literal(glyph).withStyle(style -> style.withFont(FONT).withColor(TextColor.fromRgb(rgb & 0xFFFFFF))
                .withBold(false).withItalic(false).withUnderlined(false).withStrikethrough(false).withObfuscated(false));
    }

    /** An icon followed by a plain space, for the start of a line. */
    public static MutableComponent lead(String glyph) {
        return icon(glyph).append(Component.literal(" ").withStyle(Style.EMPTY.withFont(FontDescription.DEFAULT)));
    }

    /** The skill icon for a skill id path ("mining", "farming", ...); the level star for anything unknown. */
    public static String skill(String path) {
        return switch (path) {
            case "combat" -> SKILL_COMBAT;
            case "farming" -> SKILL_FARMING;
            case "fishing" -> SKILL_FISHING;
            case "foraging" -> SKILL_FORAGING;
            case "mining" -> SKILL_MINING;
            default -> STAR;
        };
    }

    /**
     * The rarity gem that best matches an item name's colour: grey or white = common, green = uncommon, blue and aqua =
     * rare, purple and pink = epic, gold and yellow = legendary, red = mythic.
     */
    public static String rarityGem(TextColor color) {
        if (color == null) {
            return RARITY_COMMON;
        }
        int rgb = color.getValue();
        float r = (rgb >> 16 & 0xFF) / 255f;
        float g = (rgb >> 8 & 0xFF) / 255f;
        float b = (rgb & 0xFF) / 255f;
        float max = Math.max(r, Math.max(g, b));
        float min = Math.min(r, Math.min(g, b));
        float delta = max - min;
        if (delta < 0.2f) {
            return RARITY_COMMON;
        }
        float hue;
        if (max == r) {
            hue = 60f * (((g - b) / delta + 6f) % 6f);
        } else if (max == g) {
            hue = 60f * ((b - r) / delta + 2f);
        } else {
            hue = 60f * ((r - g) / delta + 4f);
        }
        if (hue < 20f || hue >= 340f) {
            return RARITY_MYTHIC;
        }
        if (hue < 75f) {
            return RARITY_LEGENDARY;
        }
        if (hue < 165f) {
            return RARITY_UNCOMMON;
        }
        if (hue < 255f) {
            return RARITY_RARE;
        }
        return RARITY_EPIC;
    }
}
