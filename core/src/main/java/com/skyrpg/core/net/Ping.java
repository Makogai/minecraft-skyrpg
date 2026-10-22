package com.skyrpg.core.net;

import com.skyrpg.core.SkyRpgCore;
import io.netty.buffer.ByteBuf;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Util;

/**
 * Pings: a player marks a spot (the utils mod's ping key, middle mouse by default) and everyone in the same dimension sees
 * it for a few seconds. The client sends {@link Ping}; the server checks it and sends {@link Shown} to everyone nearby,
 * the pinger included. A player without the utils mod ignores it.
 */
public record Ping(double x, double y, double z) implements CustomPacketPayload {
    public static final Type<Ping> TYPE = new Type<>(SkyRpgCore.id("ping"));
    public static final StreamCodec<ByteBuf, Ping> CODEC = StreamCodec.composite(
            ByteBufCodecs.DOUBLE, Ping::x,
            ByteBufCodecs.DOUBLE, Ping::y,
            ByteBufCodecs.DOUBLE, Ping::z,
            Ping::new);

    /** How far a ping can be from the pinger, and from the players who see it. */
    private static final double RANGE = 512;
    private static final long COOLDOWN_MILLIS = 750;
    private static final Map<UUID, Long> LAST = new HashMap<>();

    public static void register() {
        PayloadTypeRegistry.serverboundPlay().register(TYPE, CODEC);
        PayloadTypeRegistry.clientboundPlay().register(Shown.TYPE, Shown.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(TYPE, (payload, context) -> relay(context.player(), payload));
    }

    private static void relay(ServerPlayer player, Ping ping) {
        long now = Util.getMillis();
        Long last = LAST.get(player.getUUID());
        if (last != null && now - last < COOLDOWN_MILLIS || !Double.isFinite(ping.x + ping.y + ping.z)
                || player.position().distanceToSqr(ping.x, ping.y, ping.z) > RANGE * RANGE || player.isSpectator()) {
            return;
        }
        LAST.put(player.getUUID(), now);
        Shown shown = new Shown(player.getGameProfile().name(), ping.x, ping.y, ping.z);
        for (ServerPlayer other : player.level().players()) {
            if (other.position().distanceToSqr(ping.x, ping.y, ping.z) <= RANGE * RANGE) {
                ServerPlayNetworking.send(other, shown);
            }
        }
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /** Server to client: {@code player} pinged this spot (in the receiver's dimension). */
    public record Shown(String player, double x, double y, double z) implements CustomPacketPayload {
        public static final Type<Shown> TYPE = new Type<>(SkyRpgCore.id("ping_shown"));
        public static final StreamCodec<ByteBuf, Shown> CODEC = StreamCodec.composite(
                ByteBufCodecs.STRING_UTF8, Shown::player,
                ByteBufCodecs.DOUBLE, Shown::x,
                ByteBufCodecs.DOUBLE, Shown::y,
                ByteBufCodecs.DOUBLE, Shown::z,
                Shown::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }
}
