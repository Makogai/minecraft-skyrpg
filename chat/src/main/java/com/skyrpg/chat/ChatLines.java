package com.skyrpg.chat;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

/**
 * Every line this mod sends is a translatable component with a {@code skyrpg_chat.<kind>} key and a fallback, so it
 * renders the same everywhere (no lang file defines these keys, so the fallback is always used) while the client
 * mod can tell what kind of line it is and pick it apart: arguments are the header, the body and the name of the
 * player it's about. The last argument is never shown; it's there so the client knows who sent it (for mentions and
 * for hiding blocked players).
 *
 * <pre>
 * skyrpg_chat.player       "%1$s%2$s"  header ("[42] Name: "), body, sender name
 * skyrpg_chat.emote        "%1$s%2$s"  header ("✱ Name "), body, sender name
 * skyrpg_chat.whisper_in   "%1$s%2$s"  header ("From Name: "), body, sender name
 * skyrpg_chat.whisper_out  "%1$s%2$s"  header ("To Name: "), body, receiver name
 * skyrpg_chat.social       "%1$s"      line (joins, leaves, welcomes), player name
 * skyrpg_chat.death        "%1$s"      line, player name
 * skyrpg_chat.advancement  "%1$s"      line, player name
 * skyrpg_chat.party        "%1$s%2$s"  header (tag and name), body, sender name (empty for announcements); sent by the party mod
 * </pre>
 * The client turns {@code player} into {@code mention} when the line mentions you.
 */
public final class ChatLines {
    public static final String PREFIX = "skyrpg_chat.";
    public static final String PLAYER = "player";
    public static final String MENTION = "mention";
    public static final String EMOTE = "emote";
    public static final String WHISPER_IN = "whisper_in";
    public static final String WHISPER_OUT = "whisper_out";
    public static final String SOCIAL = "social";
    public static final String DEATH = "death";
    public static final String ADVANCEMENT = "advancement";
    public static final String PARTY = "party";

    public static final String TWO_PARTS = "%1$s%2$s";
    public static final String ONE_PART = "%1$s";

    private ChatLines() {
    }

    /** A header + body line (player chat, emotes, whispers). */
    public static MutableComponent line(String kind, Component header, Component body, String who) {
        return Component.translatableWithFallback(PREFIX + kind, TWO_PARTS, header, body, who);
    }

    /** A one-part line (joins, deaths, advancements). */
    public static MutableComponent single(String kind, Component line, String who) {
        return Component.translatableWithFallback(PREFIX + kind, ONE_PART, line, who == null ? "" : who);
    }
}
