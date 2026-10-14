package com.skyrpg.chat;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * The built-in chat stickers: 48x48 pictures in {@code assets/skyrpg_chat/textures/sticker/<name>.png}, listed in
 * {@code assets/skyrpg_chat/stickers.txt} (both written by {@code tools/art/generate_stickers.py}). Sent as the text
 * {@code [s:name]} or {@code [sticker:name]}; the client turns that into the picture.
 */
public final class Stickers {
    public static final int SIZE = 48;
    /** {@code [s:gg]} / {@code [sticker:gg]}, group 1 = the name. */
    public static final Pattern TOKEN = Pattern.compile("\\[(?:s|sticker):([A-Za-z0-9_]{1,24})]", Pattern.CASE_INSENSITIVE);

    private static List<String> names;

    private Stickers() {
    }

    /** All sticker names, in display order. */
    public static synchronized List<String> names() {
        if (names == null) {
            List<String> read = new ArrayList<>();
            try (InputStream in = Stickers.class.getResourceAsStream("/assets/skyrpg_chat/stickers.txt")) {
                if (in != null) {
                    BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
                    String line;
                    while ((line = reader.readLine()) != null) {
                        line = line.trim().toLowerCase(Locale.ROOT);
                        if (!line.isEmpty()) {
                            read.add(line);
                        }
                    }
                }
            } catch (IOException e) {
                SkyRpgChat.LOGGER.warn("Couldn't read the sticker list", e);
            }
            names = List.copyOf(read);
        }
        return names;
    }

    public static boolean exists(String name) {
        return names().contains(name.toLowerCase(Locale.ROOT));
    }
}
