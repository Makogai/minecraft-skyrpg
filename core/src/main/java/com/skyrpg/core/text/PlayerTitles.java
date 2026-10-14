package com.skyrpg.core.text;

import java.util.function.Function;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.server.level.ServerPlayer;

/**
 * The title a player has equipped (the earned titles of the progress mod), for mods that can't depend on it: the chat
 * mod shows it before the name in chat and in the tab list. The progress mod installs the lookup; until then nobody has one.
 *
 * <p>A title's {@code icon} is either a glyph of the chat icon font (a private-use character, drawn as the pixel icon)
 * or a plain unicode symbol (drawn in the title's colour).
 */
public final class PlayerTitles {
    /** A shown title: short text, an icon (may be empty) and the colour as 0xRRGGBB. */
    public record Shown(String text, String icon, int color) {
    }

    private static Function<ServerPlayer, Shown> provider = player -> null;

    private PlayerTitles() {
    }

    public static void install(Function<ServerPlayer, Shown> installed) {
        provider = installed;
    }

    /** The player's equipped title, or null. */
    public static Shown active(ServerPlayer player) {
        return provider.apply(player);
    }

    /** Whether the icon is a glyph of the chat icon font (private-use area) rather than a normal symbol. */
    public static boolean isGlyph(String icon) {
        return !icon.isEmpty() && icon.codePointAt(0) >= 0xE000 && icon.codePointAt(0) <= 0xF8FF;
    }

    /** The icon as a component: the pixel glyph as drawn, or the symbol in {@code rgb}. Empty for no icon. */
    public static MutableComponent iconComponent(String icon, int rgb) {
        if (icon.isEmpty()) {
            return Component.empty();
        }
        Style plain = Style.EMPTY.withBold(false).withItalic(false);
        if (isGlyph(icon)) {
            return Component.literal(icon).withStyle(plain.withFont(Notice.FONT).withColor(TextColor.fromRgb(0xFFFFFF)));
        }
        return Component.literal(icon).withStyle(plain.withColor(TextColor.fromRgb(rgb & 0xFFFFFF)));
    }
}
