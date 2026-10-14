package com.skyrpg.world.bloodmoon;

import com.skyrpg.world.SkyRpgWorld;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Tells a client whether a Blood Moon is on and how long it has left (game ticks until dawn). Sent when it starts and ends,
 * when a player joins and every five seconds; the client counts down between messages.
 */
public record BloodMoonSync(boolean active, int ticksLeft) implements CustomPacketPayload {
    public static final Type<BloodMoonSync> TYPE = new Type<>(SkyRpgWorld.id("blood_moon"));
    public static final StreamCodec<ByteBuf, BloodMoonSync> CODEC = StreamCodec.composite(
            ByteBufCodecs.BOOL, BloodMoonSync::active, ByteBufCodecs.VAR_INT, BloodMoonSync::ticksLeft, BloodMoonSync::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
