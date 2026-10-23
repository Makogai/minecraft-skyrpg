package com.skyrpg.events.net;

import com.skyrpg.events.SkyRpgEvents;
import io.netty.buffer.ByteBuf;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Server to client, as JSON: the full-screen season splash shown on joining ({@link Splash}) and every billboard
 * ({@link Billboards}, the whole list each time it changes).
 */
public final class ShowcasePayloads {
    private ShowcasePayloads() {
    }

    public static void register() {
        PayloadTypeRegistry.clientboundPlay().register(Splash.TYPE, Splash.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(Billboards.TYPE, Billboards.CODEC);
    }

    /**
     * {@code {id, state: "live"|"soon", force, title, subtitle, color, image, sound, lines: [...], starts, ends}}; times
     * are epoch millis, {@code force} shows it even if it was seen today.
     */
    public record Splash(String json) implements CustomPacketPayload {
        public static final Type<Splash> TYPE = new Type<>(SkyRpgEvents.id("splash"));
        public static final StreamCodec<ByteBuf, Splash> CODEC = ByteBufCodecs.stringUtf8(1 << 16).map(Splash::new, Splash::json);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** {@code [{id, dim, x, y, z, yaw, w, h, images: [...], seconds, frame}]}. */
    public record Billboards(String json) implements CustomPacketPayload {
        public static final Type<Billboards> TYPE = new Type<>(SkyRpgEvents.id("billboards"));
        public static final StreamCodec<ByteBuf, Billboards> CODEC = ByteBufCodecs.stringUtf8(1 << 18).map(Billboards::new, Billboards::json);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }
}
