package com.skyrpg.chat.client;

import java.util.EnumSet;
import java.util.Set;

/** The tabs at the top of the open chat: views of the same history, filtered by message kind. */
public enum ChatTab {
    ALL("All", EnumSet.allOf(ChatKind.class)),
    CHAT("Chat", EnumSet.of(ChatKind.PLAYER, ChatKind.MENTION, ChatKind.WHISPER, ChatKind.EMOTE, ChatKind.CLAUDE)),
    WHISPERS("Whispers", EnumSet.of(ChatKind.WHISPER)),
    SKYRPG("SkyRPG", EnumSet.of(ChatKind.SKYRPG, ChatKind.SOCIAL, ChatKind.DEATH, ChatKind.ADVANCEMENT, ChatKind.SYSTEM));

    private final String label;
    private final Set<ChatKind> kinds;

    ChatTab(String label, Set<ChatKind> kinds) {
        this.label = label;
        this.kinds = kinds;
    }

    public String label() {
        return label;
    }

    public boolean shows(ChatKind kind) {
        return kinds.contains(kind);
    }
}
