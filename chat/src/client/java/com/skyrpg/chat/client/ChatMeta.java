package com.skyrpg.chat.client;

import com.google.common.collect.MapMaker;
import java.util.Map;
import net.minecraft.client.multiplayer.chat.GuiMessage;
import net.minecraft.client.multiplayer.chat.GuiMessageTag;

/**
 * What the chat box remembers about each message beyond vanilla's GuiMessage: its kind, when it arrived (wall clock,
 * for timestamps), how many identical messages it stands for (compact repeats) and the tag vanilla gave it (restored
 * when stripes are turned off). Keyed by identity and weakly, so it goes away with the message.
 */
public final class ChatMeta {
    public record Meta(ChatKind kind, long time, int count, GuiMessageTag originalTag) {
    }

    private static final Map<GuiMessage, Meta> META = new MapMaker().weakKeys().makeMap();
    /** Set while ChatComponent.addMessage runs, for the message it is about to make. */
    private static Meta pending;

    private ChatMeta() {
    }

    public static Meta get(GuiMessage message) {
        return META.get(message);
    }

    static void put(GuiMessage message, Meta meta) {
        META.put(message, meta);
    }

    static void setPending(Meta meta) {
        pending = meta;
    }

    static Meta pending() {
        return pending;
    }

    static void clearPending() {
        pending = null;
    }
}
