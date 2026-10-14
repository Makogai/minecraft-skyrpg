package com.skyrpg.chat;

import java.net.URI;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.server.level.ServerPlayer;

/**
 * Turns what a player typed into the body of a chat line: emotes become glyphs, {@code [item]}-style tokens become
 * item showcases, and web links become clickable. Everything else stays exactly as typed, in {@code base} style.
 */
public final class ChatBody {
    /** At most this many showcases per message; more stay as typed (each one carries a whole item). */
    private static final int MAX_SHOWCASES = 4;
    private static final String URL = "https?://[^\\s]+";
    private static volatile Pattern pattern = build();

    private ChatBody() {
    }

    /** Called when emotes (re)load. */
    static void rebuildPattern() {
        pattern = build();
    }

    private static Pattern build() {
        StringBuilder alternatives = new StringBuilder("(?<url>" + URL + ")");
        for (String token : Showcase.TOKENS) {
            alternatives.append('|').append(Pattern.quote(token));
        }
        for (String code : Emotes.codesLongestFirst()) {
            alternatives.append('|').append(Pattern.quote(code));
        }
        return Pattern.compile(alternatives.toString(), Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    }

    /**
     * The body for {@code raw}, typed by {@code sender} (null for the console, which gets emotes and links but no
     * showcases).
     */
    public static MutableComponent build(ServerPlayer sender, String raw, Style base) {
        MutableComponent out = Component.empty().withStyle(base);
        Matcher matcher = pattern.matcher(raw);
        int plainFrom = 0;
        int showcases = 0;
        while (matcher.find()) {
            String match = matcher.group();
            Component replacement = null;
            if (matcher.group("url") != null) {
                replacement = link(match);
            } else if (Showcase.TOKENS.contains(match.toLowerCase(Locale.ROOT))) {
                if (sender != null && showcases < MAX_SHOWCASES) {
                    replacement = Showcase.token(sender, match);
                    showcases++;
                }
            } else {
                Emotes.Emote emote = Emotes.get(match);
                if (emote != null && (!emote.wordOnly() || isWord(raw, matcher.start(), matcher.end()))) {
                    replacement = emote(emote);
                }
            }
            if (replacement == null) {
                continue;
            }
            if (matcher.start() > plainFrom) {
                out.append(Component.literal(raw.substring(plainFrom, matcher.start())));
            }
            out.append(replacement);
            plainFrom = matcher.end();
        }
        if (plainFrom < raw.length()) {
            out.append(Component.literal(raw.substring(plainFrom)));
        }
        return out;
    }

    private static boolean isWord(String text, int start, int end) {
        return (start == 0 || Character.isWhitespace(text.charAt(start - 1))) && (end == text.length() || Character.isWhitespace(text.charAt(end)));
    }

    private static Component emote(Emotes.Emote emote) {
        HoverEvent hover = new HoverEvent.ShowText(Component.literal(emote.code()).withColor(ChatColors.MUTED));
        if (emote.icon() != null) {
            return ChatIcons.icon(emote.icon()).withStyle(style -> style.withHoverEvent(hover));
        }
        return Component.literal(emote.text()).withStyle(style -> style.withColor(emote.color()).withItalic(false).withHoverEvent(hover));
    }

    private static Component link(String url) {
        // Trailing punctuation usually ends the sentence, not the link.
        String trimmed = url.replaceAll("[.,;:!?)\\]]+$", "");
        try {
            URI uri = URI.create(trimmed);
            String where = uri.getHost() != null ? uri.getHost() : trimmed;
            MutableComponent link = Component.literal(trimmed).withStyle(style -> style.withColor(ChatColors.BLUE).withUnderlined(true)
                    .withClickEvent(new ClickEvent.OpenUrl(uri))
                    .withHoverEvent(new HoverEvent.ShowText(Component.literal("Open " + where).withColor(ChatColors.MUTED))));
            return trimmed.length() == url.length() ? link : Component.empty().append(link).append(Component.literal(url.substring(trimmed.length())));
        } catch (IllegalArgumentException e) {
            return Component.literal(url);
        }
    }

    /** All showcase tokens, for help text. */
    public static List<String> tokens() {
        return Showcase.TOKENS;
    }
}
