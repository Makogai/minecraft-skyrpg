package com.skyrpg.chat.client;

import com.skyrpg.chat.Stickers;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;

/**
 * Finds the pictures a chat message refers to, by its plain text: a sticker token ({@code [s:gg]}), a shared
 * screenshot ({@code [img:1a2b3c4d]}) or a link to a PNG/JPEG/GIF. Everything is text, so it works the same for player
 * chat, tellraw and the server log's view of the line.
 */
public final class ImageRefs {
    /** At most this many pictures per message. */
    public static final int MAX_PER_MESSAGE = 3;
    private static final Pattern ANY = Pattern.compile(Stickers.TOKEN.pattern() + "|\\[img:([0-9a-f]{8})]|(https?://[^\\s]+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern TOKENS = Pattern.compile(Stickers.TOKEN.pattern() + "|\\[img:[0-9a-f]{8}]", Pattern.CASE_INSENSITIVE);

    /** Hosts whose links load without asking (and their subdomains for the *.githubusercontent.com family). */
    private static final List<String> HOSTS = List.of("i.imgur.com", "imgur.com", "cdn.discordapp.com", "media.discordapp.net", "i.redd.it",
            "media.tenor.com", "c.tenor.com", "media1.tenor.com", "media.giphy.com", "raw.githubusercontent.com", "user-images.githubusercontent.com",
            "avatars.githubusercontent.com", "private-user-images.githubusercontent.com");
    /** On these hosts every path is a picture (no file extension needed). */
    private static final List<String> DIRECT = List.of("i.imgur.com", "i.redd.it", "media.tenor.com", "c.tenor.com", "media1.tenor.com");

    public enum Kind { STICKER, NET, URL }

    /** key: {@code sticker:gg}, {@code net:1a2b3c4d} or {@code url:<link>}; value is the sticker name, the id or the link. */
    public record Ref(Kind kind, String value) {
        public String key() {
            return kind.name().toLowerCase(Locale.ROOT) + ":" + value;
        }

        public String host() {
            try {
                return URI.create(value).getHost();
            } catch (RuntimeException e) {
                return null;
            }
        }
    }

    private ImageRefs() {
    }

    /** The pictures a message text refers to, in order, without repeats. */
    public static List<Ref> scan(String text) {
        List<Ref> refs = new ArrayList<>(2);
        if (text.indexOf('[') < 0 && !text.contains("http")) {
            return refs;
        }
        Matcher matcher = ANY.matcher(text);
        while (matcher.find() && refs.size() < MAX_PER_MESSAGE) {
            Ref ref = null;
            if (matcher.group(1) != null) {
                String name = matcher.group(1).toLowerCase(Locale.ROOT);
                if (Stickers.exists(name)) {
                    ref = new Ref(Kind.STICKER, name);
                }
            } else if (matcher.group(2) != null) {
                ref = new Ref(Kind.NET, matcher.group(2));
            } else if (matcher.group(3) != null) {
                String url = matcher.group(3).replaceAll("[.,;:!?)\\]]+$", "");
                if (looksLikeImage(url)) {
                    ref = new Ref(Kind.URL, url);
                }
            }
            if (ref != null && !refs.contains(ref)) {
                refs.add(ref);
            }
        }
        return refs;
    }

    /** True for a plain http(s) link to something that is a picture on a known host or ends in an image extension. */
    static boolean looksLikeImage(String url) {
        URI uri;
        try {
            uri = URI.create(url);
        } catch (IllegalArgumentException e) {
            return false;
        }
        String scheme = uri.getScheme();
        String host = uri.getHost();
        if (host == null || uri.getUserInfo() != null || (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme))) {
            return false;
        }
        int port = uri.getPort();
        if (port != -1 && port != 80 && port != 443) {
            return false;
        }
        String path = Optional.ofNullable(uri.getPath()).orElse("").toLowerCase(Locale.ROOT);
        boolean extension = path.endsWith(".png") || path.endsWith(".jpg") || path.endsWith(".jpeg") || path.endsWith(".gif");
        return extension || (DIRECT.contains(host.toLowerCase(Locale.ROOT)) && path.length() > 1);
    }

    /** Whether links to this host load without a click. */
    public static boolean whitelisted(String host) {
        if (host == null) {
            return false;
        }
        String lower = host.toLowerCase(Locale.ROOT);
        return HOSTS.contains(lower);
    }

    /** The message with its sticker and screenshot tokens taken out (links stay), or the same object if it has none. */
    public static Component stripTokens(Component message, boolean stickers) {
        String text = message.getString();
        if (text.indexOf('[') < 0) {
            return message;
        }
        Matcher check = TOKENS.matcher(text);
        boolean any = false;
        while (check.find()) {
            if (check.group().regionMatches(true, 1, "img:", 0, 4) || (stickers && Stickers.exists(stickerName(check.group())))) {
                any = true;
                break;
            }
        }
        if (!any) {
            return message;
        }
        MutableComponent out = Component.empty();
        message.visit((style, piece) -> {
            out.append(Component.literal(removeTokens(piece, stickers)).setStyle(style));
            return Optional.empty();
        }, Style.EMPTY);
        return out;
    }

    private static String removeTokens(String piece, boolean stickers) {
        Matcher matcher = TOKENS.matcher(piece);
        StringBuilder sb = new StringBuilder();
        int from = 0;
        while (matcher.find()) {
            boolean net = matcher.group().regionMatches(true, 1, "img:", 0, 4);
            if (net || (stickers && Stickers.exists(stickerName(matcher.group())))) {
                sb.append(piece, from, matcher.start());
                from = matcher.end();
            }
        }
        sb.append(piece, from, piece.length());
        return sb.toString();
    }

    private static String stickerName(String token) {
        return token.substring(token.indexOf(':') + 1, token.length() - 1).toLowerCase(Locale.ROOT);
    }
}
