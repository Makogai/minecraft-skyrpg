package com.skyrpg.progress.dialogue;

import com.skyrpg.progress.SkyRpgProgress;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * The dialogue's packets. The server sends one page at a time ({@link Show}, already with the player's name filled in) and
 * the client answers with the choice made ({@link Choose}); the server remembers which page it showed, so a client can only
 * ever pick one of the choices it was offered.
 */
public final class DialoguePayloads {
    /** {@link Choose#choice} when the page has no choices and the player clicked to go on. */
    public static final int ADVANCE = -1;
    /** {@link Choose#choice} when the player closed the dialogue (Esc). */
    public static final int CANCEL = -2;

    private DialoguePayloads() {
    }

    /**
     * Server to client: show this page. {@code skin} is a texture path for the speaker's face (empty for the narrator),
     * {@code mood} an optional emote name, {@code choiceActions} what each choice does (for its colour) next to its label.
     * {@code page} and {@code pages} count where in the conversation this is.
     */
    public record Show(int serial, String speaker, String name, int color, String skin, String mood, String text, List<String> choiceLabels,
                       List<String> choiceActions, boolean readOnly, int page, int pages) implements CustomPacketPayload {
        public static final Type<Show> TYPE = new Type<>(SkyRpgProgress.id("dialogue_show"));
        private static final StreamCodec<io.netty.buffer.ByteBuf, List<String>> STRINGS = ByteBufCodecs.stringUtf8(400).apply(ByteBufCodecs.list(12));
        public static final StreamCodec<RegistryFriendlyByteBuf, Show> CODEC = StreamCodec.of((buf, s) -> {
            buf.writeVarInt(s.serial);
            ByteBufCodecs.STRING_UTF8.encode(buf, s.speaker);
            ByteBufCodecs.STRING_UTF8.encode(buf, s.name);
            buf.writeInt(s.color);
            ByteBufCodecs.STRING_UTF8.encode(buf, s.skin);
            ByteBufCodecs.STRING_UTF8.encode(buf, s.mood);
            ByteBufCodecs.stringUtf8(4000).encode(buf, s.text);
            STRINGS.encode(buf, s.choiceLabels);
            STRINGS.encode(buf, s.choiceActions);
            buf.writeBoolean(s.readOnly);
            buf.writeVarInt(s.page);
            buf.writeVarInt(s.pages);
        }, buf -> new Show(buf.readVarInt(), ByteBufCodecs.STRING_UTF8.decode(buf), ByteBufCodecs.STRING_UTF8.decode(buf), buf.readInt(),
                ByteBufCodecs.STRING_UTF8.decode(buf), ByteBufCodecs.STRING_UTF8.decode(buf), ByteBufCodecs.stringUtf8(4000).decode(buf),
                STRINGS.decode(buf), STRINGS.decode(buf), buf.readBoolean(), buf.readVarInt(), buf.readVarInt()));

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** Server to client: the conversation is over, close the screen. */
    public record Close() implements CustomPacketPayload {
        public static final Type<Close> TYPE = new Type<>(SkyRpgProgress.id("dialogue_close"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Close> CODEC = StreamCodec.unit(new Close());

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** Client to server: the player picked choice {@code choice} (or {@link #ADVANCE} / {@link #CANCEL}) on page {@code serial}. */
    public record Choose(int serial, int choice) implements CustomPacketPayload {
        public static final Type<Choose> TYPE = new Type<>(SkyRpgProgress.id("dialogue_choose"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Choose> CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, Choose::serial, ByteBufCodecs.VAR_INT, Choose::choice, Choose::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** Server to client: which marker (see core's {@code NpcMarkers}) each NPC gets over their head, by NPC id. */
    public record Markers(Map<String, Integer> kinds) implements CustomPacketPayload {
        public static final Type<Markers> TYPE = new Type<>(SkyRpgProgress.id("npc_markers"));
        public static final StreamCodec<io.netty.buffer.ByteBuf, Markers> CODEC = ByteBufCodecs.map(HashMap::new, ByteBufCodecs.STRING_UTF8,
                ByteBufCodecs.VAR_INT).map(Markers::new, m -> new HashMap<>(m.kinds));

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public static void register() {
        PayloadTypeRegistry.clientboundPlay().register(Show.TYPE, Show.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(Close.TYPE, Close.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(Markers.TYPE, Markers.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(Choose.TYPE, Choose.CODEC);
    }
}
