package com.skyrpg.chat.client;

import com.skyrpg.chat.ChatLines;

/** What a chat line is, which decides its stripe colour and which tabs show it. */
public enum ChatKind {
    PLAYER("Player chat", 0x5CA8FF),
    MENTION("Mentions you", 0xFF9F43),
    WHISPER("Whisper", 0xF08CE8),
    EMOTE("Emote", 0x5CE1E6),
    SOCIAL("Join and leave", 0x5CD68A),
    DEATH("Death", 0xFF6464),
    ADVANCEMENT("Advancement", 0xA6E35C),
    PARTY("Party", 0x4FD6A0),
    CLAUDE("Claude", 0xB98CFF),
    SKYRPG("SkyRPG", 0xF2B33D),
    SYSTEM("System", 0x6B7385);

    private final String label;
    private final int color;

    ChatKind(String label, int color) {
        this.label = label;
        this.color = color;
    }

    public String label() {
        return label;
    }

    /** RGB, no alpha. */
    public int color() {
        return color;
    }

    /** The kind for a {@code skyrpg_chat.<kind>} key's suffix, or null. */
    public static ChatKind fromMarker(String kind) {
        return switch (kind) {
            case ChatLines.PLAYER -> PLAYER;
            case ChatLines.MENTION -> MENTION;
            case ChatLines.WHISPER_IN, ChatLines.WHISPER_OUT -> WHISPER;
            case ChatLines.EMOTE -> EMOTE;
            case ChatLines.SOCIAL -> SOCIAL;
            case ChatLines.DEATH -> DEATH;
            case ChatLines.ADVANCEMENT -> ADVANCEMENT;
            case ChatLines.PARTY -> PARTY;
            default -> null;
        };
    }
}
