package com.skyrpg.chat.client;

import com.skyrpg.chat.ChatLines;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.sounds.SoundEvents;

/**
 * Mentions and pings, on the client: when someone else's message has your name (or @name) in it, the name is
 * highlighted, the line becomes a "mention" (its own stripe) and you hear a soft bell. Whispers to you get a softer
 * ding. Messages from players you've blocked or hidden (Social Interactions) are dropped, as vanilla does for player
 * chat.
 */
public final class Mentions {
    private static final int HIGHLIGHT = 0xFFC94D;
    private static String patternFor;
    private static Pattern pattern;

    private Mentions() {
    }

    /** ALLOW_GAME: false for player lines from someone this client blocked or hid. */
    static boolean allow(Component message, boolean overlay) {
        if (overlay) {
            return true;
        }
        String marker = ChatHooks.marker(message);
        if (!ChatLines.PLAYER.equals(marker) && !ChatLines.EMOTE.equals(marker) && !ChatLines.WHISPER_IN.equals(marker)) {
            return true;
        }
        String sender = ChatHooks.markerName(message);
        if (sender == null) {
            return true;
        }
        Minecraft minecraft = Minecraft.getInstance();
        UUID id = minecraft.getPlayerSocialManager().getDiscoveredUUID(sender);
        return id == null || !minecraft.getPlayerSocialManager().shouldHideMessageFrom(id);
    }

    /** MODIFY_GAME: highlights mentions of you, and pings for mentions and whispers. */
    static Component modify(Component message, boolean overlay) {
        if (overlay) {
            return message;
        }
        String marker = ChatHooks.marker(message);
        if (ChatLines.WHISPER_IN.equals(marker)) {
            if (ChatSettings.whisperPing.get() && !fromMe(message)) {
                ping(1.8f, 0.6f);
            }
            return message;
        }
        if (!ChatLines.PLAYER.equals(marker) || !ChatSettings.mentions.get() || fromMe(message)
                || !(message.getContents() instanceof TranslatableContents translatable)) {
            return message;
        }
        Object[] args = translatable.getArgs();
        if (args.length < 3 || !(args[1] instanceof Component body)) {
            return message;
        }
        Component highlighted = highlight(body, myName());
        if (highlighted == null) {
            return message;
        }
        ping(1.5f, 1.0f);
        Object[] newArgs = args.clone();
        newArgs[1] = highlighted;
        return Component.translatableWithFallback(ChatLines.PREFIX + ChatLines.MENTION, translatable.getFallback(), newArgs)
                .setStyle(message.getStyle());
    }

    /** The body with every whole-word "name" or "@name" highlighted, or null if it doesn't mention the name. */
    static Component highlight(Component body, String name) {
        if (name == null || name.isEmpty()) {
            return null;
        }
        Pattern mention = pattern(name);
        record Part(Style style, String text) {
        }
        List<Part> parts = new ArrayList<>();
        body.visit((style, text) -> {
            parts.add(new Part(style, text));
            return Optional.empty();
        }, Style.EMPTY);
        boolean found = false;
        MutableComponent out = Component.empty();
        for (Part part : parts) {
            // Showcases and emotes (anything with a hover) stay as they are.
            if (part.style().getHoverEvent() != null) {
                out.append(Component.literal(part.text()).setStyle(part.style()));
                continue;
            }
            Matcher matcher = mention.matcher(part.text());
            int from = 0;
            while (matcher.find()) {
                found = true;
                if (matcher.start() > from) {
                    out.append(Component.literal(part.text().substring(from, matcher.start())).setStyle(part.style()));
                }
                out.append(Component.literal(matcher.group()).setStyle(part.style().withColor(HIGHLIGHT).withBold(true)));
                from = matcher.end();
            }
            if (from < part.text().length()) {
                out.append(Component.literal(part.text().substring(from)).setStyle(part.style()));
            }
        }
        return found ? out : null;
    }

    private static Pattern pattern(String name) {
        if (!name.equals(patternFor)) {
            pattern = Pattern.compile("(?<![A-Za-z0-9_])@?" + Pattern.quote(name) + "(?![A-Za-z0-9_])", Pattern.CASE_INSENSITIVE);
            patternFor = name;
        }
        return pattern;
    }

    private static boolean fromMe(Component message) {
        String sender = ChatHooks.markerName(message);
        String me = myName();
        return sender != null && sender.equalsIgnoreCase(me);
    }

    private static String myName() {
        Minecraft minecraft = Minecraft.getInstance();
        return minecraft.player != null ? minecraft.player.getGameProfile().name() : minecraft.getUser().getName();
    }

    private static void ping(float pitch, float loudness) {
        float volume = (float) ChatSettings.pingVolume.number() * loudness;
        if (volume <= 0.001f) {
            return;
        }
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.NOTE_BLOCK_BELL.value(), pitch, volume));
    }
}
