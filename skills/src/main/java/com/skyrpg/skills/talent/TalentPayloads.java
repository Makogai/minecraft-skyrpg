package com.skyrpg.skills.talent;

import com.skyrpg.skills.SkyRpgSkills;
import io.netty.buffer.ByteBuf;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Talents over the network: the trees go to clients (on join and /reload); clients ask to spend a point or reset. */
public final class TalentPayloads {
    private TalentPayloads() {
    }

    /** The talent files as loaded, by file id. */
    public record Definitions(Map<Identifier, String> files) implements CustomPacketPayload {
        public static final Type<Definitions> TYPE = new Type<>(SkyRpgSkills.id("talent_definitions"));
        public static final StreamCodec<ByteBuf, Definitions> CODEC = ByteBufCodecs.<ByteBuf, Identifier, String, Map<Identifier, String>>map(
                HashMap::new, Identifier.STREAM_CODEC, ByteBufCodecs.stringUtf8(1 << 18)).map(Definitions::new, Definitions::files);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** Spend a point on {@code talent} in {@code skill}, or (with {@code reset}) reset that skill's talents. */
    public record Action(Identifier skill, String talent, boolean reset) implements CustomPacketPayload {
        public static final Type<Action> TYPE = new Type<>(SkyRpgSkills.id("talent_action"));
        public static final StreamCodec<ByteBuf, Action> CODEC = StreamCodec.composite(
                Identifier.STREAM_CODEC, Action::skill,
                ByteBufCodecs.STRING_UTF8, Action::talent,
                ByteBufCodecs.BOOL, Action::reset,
                Action::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }
}
