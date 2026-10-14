package com.skyrpg.fishing;

import io.netty.buffer.ByteBuf;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;

/** The server tells a fisher which hotspot their bobber floats in, for the HUD line (bait is read from the client's own inventory). */
public final class FishingPayloads {
    private FishingPayloads() {
    }

    static void register() {
        PayloadTypeRegistry.clientboundPlay().register(Status.TYPE, Status.CODEC);
    }

    static void send(ServerPlayer player, String hotspot) {
        if (ServerPlayNetworking.canSend(player, Status.TYPE)) {
            ServerPlayNetworking.send(player, new Status(hotspot));
        }
    }

    /** The buffs of the hotspot the bobber is in, like "+30 Fishing Speed"; empty when it isn't in one. */
    public record Status(String hotspot) implements CustomPacketPayload {
        public static final Type<Status> TYPE = new Type<>(SkyRpgFishing.id("status"));
        public static final StreamCodec<ByteBuf, Status> CODEC = StreamCodec.composite(ByteBufCodecs.STRING_UTF8, Status::hotspot, Status::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }
}
