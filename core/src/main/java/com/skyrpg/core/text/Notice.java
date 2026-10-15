package com.skyrpg.core.text;

import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;

/**
 * Game notices in chat: a pixel icon, a short bold coloured tag, then the body. {@code [icon] SKILL  Mining reached
 * level 12!}. Every system line about the game (level-ups, coins, finds, events) goes through here so chat stays
 * scannable and consistent.
 *
 * <p>Core cannot depend on the chat mod, so the icon font {@code skyrpg_chat:icons} is referenced by id and the glyphs
 * by codepoint (see docs/design/chat.md). Without the chat mod the glyph just renders as a missing-character box, but
 * the chat mod is always shipped.
 *
 * <pre>{@code
 * Notice.send(player, Notice.Kind.COINS, Msg.text("+50 coins", ChatFormatting.GOLD));
 * Notice.of(Notice.Kind.SKILL).icon(Notice.skillIcon("mining")).tag("LEVEL UP").hover(Component.literal("Open /skills"))
 *         .click("/skills").send(player, body);
 * Notice.rareDrop(player, itemName, rarityColor);
 * }</pre>
 */
public final class Notice {
    public static final FontDescription FONT = new FontDescription.Resource(Identifier.fromNamespaceAndPath("skyrpg_chat", "icons"));

    // Glyph codepoints (private use area, same as ChatIcons in the chat mod).
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
    public static final String POTION = "";
    public static final String TROPHY = "";
    public static final String LIGHTNING = "";
    public static final String CHECK = "";
    public static final String CROSS = "";
    public static final String ENVELOPE = "";
    public static final String SPARKLE = "";
    public static final String SKILL_COMBAT = "";
    public static final String SKILL_FARMING = "";
    public static final String SKILL_FISHING = "";
    public static final String SKILL_FORAGING = "";
    public static final String SKILL_MINING = "";
    public static final String RARITY_COMMON = "";
    public static final String RARITY_UNCOMMON = "";
    public static final String RARITY_RARE = "";
    public static final String RARITY_EPIC = "";
    public static final String RARITY_LEGENDARY = "";
    public static final String RARITY_MYTHIC = "";

    /** Default body colour: a soft light grey, so coloured highlights in the body stand out. */
    public static final int BODY_COLOR = 0xD5DAE3;

    /** What kind of line it is: its icon, its tag and the accent colour of the tag. */
    public enum Kind {
        SKILL(STAR, "SKILL", 0x55E6E6),
        COINS(COIN, "COINS", 0xFFC940),
        MINING(SKILL_MINING, "MINING", 0x8FB4D9),
        FARMING(SKILL_FARMING, "FARMING", 0x7BD35A),
        FORAGING(SKILL_FORAGING, "FORAGING", 0x3FB56B),
        FISHING(SKILL_FISHING, "FISHING", 0x4FB8F0),
        COMBAT(SKILL_COMBAT, "COMBAT", 0xFF5555),
        SLAYER(SKULL, "SLAYER", 0xC02A3A),
        DUNGEON(SHIELD, "DUNGEON", 0xB06AE8),
        BLOOD_MOON(SKULL, "BLOOD MOON", 0xC8102E),
        DROP(RARITY_EPIC, "RARE DROP!", 0xFFAA00),
        COLLECTION(TROPHY, "COLLECTION", 0x6FD3C2),
        PET(HEART, "PET", 0xFF8AC8),
        ENCHANT(GEM, "ENCHANT", 0xC77DFF),
        EVENT(TROPHY, "EVENT", 0xFFE14D),
        CONTRACT(ENVELOPE, "CONTRACT", 0xF0A040),
        BANK(CROWN, "BANK", 0x6BE08A),
        SHOP(COIN, "SHOP", 0x6BE08A),
        ABILITY(FIRE, "ABILITY", 0xFF9A3C),
        ITEM(SWORD, "ITEMS", 0xE8D4A0),
        QUEST(STAR, "QUEST", 0xF2B33D),
        QUEST_COMPLETE(TROPHY, "QUEST COMPLETE", 0x55DD77),
        CHAPTER_COMPLETE(CROWN, "CHAPTER COMPLETE", 0xFFAA00),
        BUILDER(PICKAXE, "BUILDER", 0x5CE1E6),
        SUCCESS(CHECK, "DONE", 0x55DD77),
        INFO(SPARKLE, "INFO", 0x9FB3D1),
        WARN(LIGHTNING, "WARNING", 0xFFD23F),
        ERROR(CROSS, "ERROR", 0xFF5555);

        private final String glyph;
        private final String tag;
        private final int color;

        Kind(String glyph, String tag, int color) {
            this.glyph = glyph;
            this.tag = tag;
            this.color = color;
        }

        public String glyph() {
            return glyph;
        }

        public String tag() {
            return tag;
        }

        public int color() {
            return color;
        }
    }

    private final Kind kind;
    private String glyph;
    private String tag;
    private int color;
    private Component hover;
    private ClickEvent click;

    private Notice(Kind kind) {
        this.kind = kind;
        this.glyph = kind.glyph;
        this.tag = kind.tag;
        this.color = kind.color;
    }

    /** Starts a notice of {@code kind}; override the icon, tag or colour, add hover and click, then build or send. */
    public static Notice of(Kind kind) {
        return new Notice(kind);
    }

    public Notice icon(String glyph) {
        this.glyph = glyph;
        return this;
    }

    public Notice tag(String tag) {
        this.tag = tag;
        return this;
    }

    public Notice color(int rgb) {
        this.color = rgb & 0xFFFFFF;
        return this;
    }

    /** Tooltip over the icon and tag. */
    public Notice hover(Component hover) {
        this.hover = hover;
        return this;
    }

    /** Runs a command when the icon or tag is clicked. */
    public Notice click(String command) {
        this.click = new ClickEvent.RunCommand(command.startsWith("/") ? command : "/" + command);
        return this;
    }

    /** Puts a command in the chat box when the icon or tag is clicked. */
    public Notice suggest(String command) {
        this.click = new ClickEvent.SuggestCommand(command);
        return this;
    }

    public MutableComponent build(Component body) {
        Style lead = Style.EMPTY;
        if (hover != null) {
            lead = lead.withHoverEvent(new HoverEvent.ShowText(hover));
        }
        if (click != null) {
            lead = lead.withClickEvent(click);
        }
        MutableComponent icon = Component.literal(glyph).withStyle(lead.withFont(FONT).withColor(TextColor.fromRgb(0xFFFFFF))
                .withBold(false).withItalic(false));
        MutableComponent label = Component.literal(" " + tag).withStyle(lead.withColor(TextColor.fromRgb(color)).withBold(true));
        MutableComponent text = Component.literal("  ").withStyle(Style.EMPTY.withColor(TextColor.fromRgb(BODY_COLOR)).withBold(false));
        text.append(body);
        return Component.empty().append(icon).append(label).append(text);
    }

    public void send(ServerPlayer player, Component body) {
        player.sendSystemMessage(build(body));
    }

    // ---- shortcuts -------------------------------------------------------------------------------------------

    /** The notice for {@code kind} and {@code body}. */
    public static MutableComponent build(Kind kind, Component body) {
        return of(kind).build(body);
    }

    /** Plain-text body convenience. */
    public static MutableComponent build(Kind kind, String body) {
        return of(kind).build(Component.literal(body));
    }

    public static void send(ServerPlayer player, Kind kind, Component body) {
        player.sendSystemMessage(build(kind, body));
    }

    public static void send(ServerPlayer player, Kind kind, String body) {
        player.sendSystemMessage(build(kind, body));
    }

    /** The skill icon for a skill id path ("mining", ...); a star for anything else. */
    public static String skillIcon(String path) {
        return switch (path) {
            case "combat" -> SKILL_COMBAT;
            case "farming" -> SKILL_FARMING;
            case "fishing" -> SKILL_FISHING;
            case "foraging" -> SKILL_FORAGING;
            case "mining" -> SKILL_MINING;
            default -> STAR;
        };
    }

    /** The RGB of a vanilla colour such as {@code ChatFormatting.AQUA}; white for non-colour formats. */
    public static int rgb(net.minecraft.ChatFormatting format) {
        TextColor color = Style.EMPTY.applyFormat(format).getColor();
        return color == null ? 0xFFFFFF : color.getValue();
    }

    /** The rarity gem matching an item name's colour (white/grey common, green, blue/aqua, purple/pink, gold/yellow, red). */
    public static String rarityGem(int rgb) {
        float r = (rgb >> 16 & 0xFF) / 255f;
        float g = (rgb >> 8 & 0xFF) / 255f;
        float b = (rgb & 0xFF) / 255f;
        float max = Math.max(r, Math.max(g, b));
        float delta = max - Math.min(r, Math.min(g, b));
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

    /** A rare-drop notice: a gold {@code RARE DROP!} tag and a rarity gem picked from {@code rarityColor}. */
    public static MutableComponent rareDrop(Component body, int rarityColor) {
        return of(Kind.DROP).icon(rarityGem(rarityColor)).build(body);
    }

    public static void rareDrop(ServerPlayer player, Component body, int rarityColor) {
        player.sendSystemMessage(rareDrop(body, rarityColor));
    }

    /** A rare-drop notice with a custom lead (for example {@code "RARE FIND!"} or {@code "TOME!"}). */
    public static MutableComponent rareDrop(String tag, Component body, int rarityColor) {
        return of(Kind.DROP).tag(tag).icon(rarityGem(rarityColor)).build(body);
    }
}
