package com.skyrpg.vfx.net;

import com.skyrpg.vfx.SkyRpgVfx;
import io.netty.buffer.ByteBuf;
import java.util.HashMap;
import java.util.Map;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Server to client: play an effect ({@link Play}), shake the camera ({@link Shake}), flash the screen ({@link Flash}), and the server's effect and binding definitions ({@link Sync}). */
public final class VfxPayloads {
    private VfxPayloads() {
    }

    public static void register() {
        PayloadTypeRegistry.clientboundPlay().register(Play.TYPE, Play.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(Sync.TYPE, Sync.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(Shake.TYPE, Shake.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(Flash.TYPE, Flash.CODEC);
    }

    /**
     * Play {@code effect} on entity {@code entityId} (or -1 for none) at x, y, z. With an entity, the position is the
     * fallback for a client that doesn't have that entity (yet).
     */
    public record Play(Identifier effect, int entityId, double x, double y, double z) implements CustomPacketPayload {
        public static final Type<Play> TYPE = new Type<>(SkyRpgVfx.id("play"));
        public static final StreamCodec<ByteBuf, Play> CODEC = StreamCodec.composite(
                Identifier.STREAM_CODEC, Play::effect,
                ByteBufCodecs.VAR_INT.map(i -> i - 1, i -> i + 1), Play::entityId,
                ByteBufCodecs.DOUBLE, Play::x,
                ByteBufCodecs.DOUBLE, Play::y,
                ByteBufCodecs.DOUBLE, Play::z,
                Play::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** Shake the camera: {@code strength} is the largest swing in degrees (0.5 light, 1 solid, 2 huge), fading out over {@code ticks}. */
    public record Shake(float strength, int ticks) implements CustomPacketPayload {
        public static final Type<Shake> TYPE = new Type<>(SkyRpgVfx.id("shake"));
        public static final StreamCodec<ByteBuf, Shake> CODEC = StreamCodec.composite(
                ByteBufCodecs.FLOAT, Shake::strength,
                ByteBufCodecs.VAR_INT, Shake::ticks,
                Shake::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** A colour wash over the whole screen ({@code argb}, its alpha is the peak) that fades out over {@code ticks}. */
    public record Flash(int argb, int ticks) implements CustomPacketPayload {
        public static final Type<Flash> TYPE = new Type<>(SkyRpgVfx.id("flash"));
        public static final StreamCodec<ByteBuf, Flash> CODEC = StreamCodec.composite(
                ByteBufCodecs.INT, Flash::argb,
                ByteBufCodecs.VAR_INT, Flash::ticks,
                Flash::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /**
     * Every effect and binding as its source JSON (id -> compact JSON). Sent on join and after every reload, so the
     * server's data packs are the only source of truth and a /reload reaches everyone.
     */
    public record Sync(Map<String, String> effects, Map<String, String> bindings) implements CustomPacketPayload {
        public static final Type<Sync> TYPE = new Type<>(SkyRpgVfx.id("sync"));
        private static final StreamCodec<ByteBuf, Map<String, String>> JSON_MAP =
                ByteBufCodecs.map(HashMap::new, ByteBufCodecs.STRING_UTF8, ByteBufCodecs.STRING_UTF8);
        public static final StreamCodec<ByteBuf, Sync> CODEC = StreamCodec.composite(
                JSON_MAP, Sync::effects,
                JSON_MAP, Sync::bindings,
                Sync::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }
}
