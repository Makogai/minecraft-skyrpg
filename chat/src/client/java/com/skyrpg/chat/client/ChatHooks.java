package com.skyrpg.chat.client;

import com.skyrpg.chat.ChatLines;
import com.skyrpg.chat.client.mixin.ChatComponentAccess;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.ListIterator;
import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.chat.GuiMessage;
import net.minecraft.client.multiplayer.chat.GuiMessageSource;
import net.minecraft.client.multiplayer.chat.GuiMessageTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MessageSignature;
import net.minecraft.network.chat.contents.TranslatableContents;

/**
 * What the ChatComponent mixin does with each message: work out its kind, fold it into the previous line when it's a
 * repeat, give it a kind stripe, and keep it out of the display when the open tab doesn't show it.
 */
public final class ChatHooks {
    private static final DateTimeFormatter TAG_TIME = DateTimeFormatter.ofPattern("HH:mm:ss", Locale.ROOT);
    /** GuiMessageTag.system() and chatNotSecure()'s indicator colour. */
    private static final int VANILLA_GREY = 0xD0D0D0;
    private static GuiMessageTag pendingTag;

    private ChatHooks() {
    }

    // ------------------------------------------------------------------ kinds

    /** The kind of a message, from this mod's marker, the [Claude] or [SkyHelper] prefix, vanilla's key or (else) SkyRPG. */
    public static ChatKind classify(Component content, GuiMessageSource source) {
        if (source == GuiMessageSource.PLAYER) {
            return ChatKind.PLAYER;
        }
        if (source == GuiMessageSource.SYSTEM_CLIENT) {
            return ChatKind.SYSTEM;
        }
        String marker = marker(content);
        if (marker != null) {
            ChatKind kind = ChatKind.fromMarker(marker);
            if (kind != null) {
                return kind;
            }
        }
        String text = content.getString().replaceFirst("^[\\p{Co}\\s]+", "");
        if (text.startsWith("[Claude]")) {
            return ChatKind.CLAUDE;
        }
        if (text.startsWith("[SkyHelper]")) {
            return ChatKind.HELPER;
        }
        if (content.getContents() instanceof TranslatableContents translatable) {
            String key = translatable.getKey();
            if (key.startsWith("death.")) {
                return ChatKind.DEATH;
            }
            if (key.startsWith("chat.type.advancement.")) {
                return ChatKind.ADVANCEMENT;
            }
            if (key.startsWith("multiplayer.player.")) {
                return ChatKind.SOCIAL;
            }
            if (key.startsWith("chat.type.")) {
                return ChatKind.PLAYER;
            }
            return ChatKind.SYSTEM;
        }
        // Plain-text system messages come from SkyRPG's mods (and tellraw).
        return ChatKind.SKYRPG;
    }

    /** "player" for a {@code skyrpg_chat.player} line, or null. */
    public static String marker(Component content) {
        if (content.getContents() instanceof TranslatableContents translatable && translatable.getKey().startsWith(ChatLines.PREFIX)) {
            return translatable.getKey().substring(ChatLines.PREFIX.length());
        }
        return null;
    }

    /** The marker line's player-name argument (its last one), or null. */
    public static String markerName(Component content) {
        if (!(content.getContents() instanceof TranslatableContents translatable) || !translatable.getKey().startsWith(ChatLines.PREFIX)) {
            return null;
        }
        Object[] args = translatable.getArgs();
        if (args.length < 2) {
            return null;
        }
        Object who = args[args.length - 1];
        String name = who instanceof Component component ? component.getString() : String.valueOf(who);
        return name.isEmpty() ? null : name;
    }

    // ------------------------------------------------------------------ the ChatComponent.addMessage hooks

    /** Before vanilla makes the GuiMessage: kind, repeat folding, stripe. */
    public static void beforeAdd(List<GuiMessage> allMessages, List<GuiMessage.Line> trimmedMessages, Component content,
                                 MessageSignature signature, GuiMessageSource source, GuiMessageTag tag) {
        ChatKind kind = classify(content, source);
        long now = System.currentTimeMillis();
        int count = 1;
        if (ChatSettings.compact != null && ChatSettings.compact.get() && signature == null && !allMessages.isEmpty()) {
            GuiMessage last = allMessages.getFirst();
            if (last.signature() == null && last.source() == source && sameMessage(last.content(), content)) {
                ChatMeta.Meta lastMeta = ChatMeta.get(last);
                count = (lastMeta != null ? lastMeta.count() : 1) + 1;
                allMessages.removeFirst();
                trimmedMessages.removeIf(line -> line.parent() == last);
            }
        }
        ChatMeta.setPending(new ChatMeta.Meta(kind, now, count, tag));
        pendingTag = stripesOn() && replaceable(tag) ? stripe(kind, now, tag) : tag;
    }

    /**
     * Same text and same look: colours and styles count too, so lines that only differ in colour (pixel art made of
     * block characters, say) don't fold into one "(x12)".
     */
    private static boolean sameMessage(Component a, Component b) {
        return a.getString().equals(b.getString()) && a.equals(b);
    }

    /**
     * Vanilla's plain grey tags (system, not secure) make way for kind stripes; tags that say something (a modified
     * or failed signed message) stay.
     */
    private static boolean replaceable(GuiMessageTag tag) {
        return tag == null || (tag.icon() == null && tag.indicatorColor() == VANILLA_GREY);
    }

    /** The tag vanilla's new GuiMessage gets. */
    public static GuiMessageTag tagFor(GuiMessageTag original) {
        return pendingTag != null ? pendingTag : original;
    }

    public static void afterAdd() {
        pendingTag = null;
        ChatMeta.clearPending();
    }

    /** Whether a message goes on screen (the open tab may hide it). Also remembers what a new message is. */
    public static boolean display(GuiMessage message) {
        ChatMeta.Meta meta = ChatMeta.get(message);
        boolean fresh = false;
        if (meta == null) {
            meta = ChatMeta.pending();
            fresh = meta != null;
            if (meta == null) {
                meta = new ChatMeta.Meta(classify(message.content(), message.source()), System.currentTimeMillis(), 1, message.tag());
            }
            ChatMeta.put(message, meta);
        }
        if (ChatTabs.shows(meta.kind())) {
            return true;
        }
        if (fresh) {
            ChatTabs.markUnread(meta.kind());
        }
        return false;
    }

    // ------------------------------------------------------------------ stripes

    private static boolean stripesOn() {
        return ChatSettings.stripes == null || ChatSettings.stripes.get();
    }

    /** A tag that draws the kind's stripe; hovering it says the kind and the time. The client log keeps vanilla's tag. */
    static GuiMessageTag stripe(ChatKind kind, long time, GuiMessageTag original) {
        String when = LocalTime.ofInstant(Instant.ofEpochMilli(time), ZoneId.systemDefault()).format(TAG_TIME);
        Component text = Component.literal(kind.label()).withColor(kind.color())
                .append(Component.literal("  " + when).withColor(0x8D96A8));
        return new GuiMessageTag(kind.color(), null, text, original != null ? original.logTag() : null);
    }

    /** Swaps every message's stripe on or off after the setting changes. */
    static void retagAll() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.gui == null || minecraft.gui.hud == null) {
            return;
        }
        List<GuiMessage> messages = ((ChatComponentAccess) minecraft.gui.hud.getChat()).skyrpg_chat$allMessages();
        boolean on = stripesOn();
        ListIterator<GuiMessage> iterator = messages.listIterator();
        while (iterator.hasNext()) {
            GuiMessage message = iterator.next();
            ChatMeta.Meta meta = ChatMeta.get(message);
            if (meta == null) {
                continue;
            }
            GuiMessageTag tag = on && replaceable(meta.originalTag()) ? stripe(meta.kind(), meta.time(), meta.originalTag()) : meta.originalTag();
            GuiMessage retagged = new GuiMessage(message.addedTime(), message.content(), message.signature(), message.source(), tag);
            ChatMeta.put(retagged, meta);
            iterator.set(retagged);
        }
        ChatSettings.refresh();
    }
}
