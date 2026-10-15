package com.skyrpg.brainrots;

import java.util.UUID;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Sent to everyone on the server when someone catches a brainrot: who ({@code player}, for their skin, and
 * {@code name}), which kind (ordinal), whether it was their first of that kind, and how many they have now. The
 * client shows it as a card with both of them turning in 3D (and the brainrot says its name).
 */
public record CatchNews(UUID player, String name, int kind, boolean first, int count) implements CustomPacketPayload {
    public static final Type<CatchNews> TYPE = new Type<>(SkyRpgBrainrots.id("catch_news"));
    public static final StreamCodec<FriendlyByteBuf, CatchNews> CODEC = StreamCodec.ofMember(CatchNews::write, CatchNews::read);

    private void write(FriendlyByteBuf buf) {
        buf.writeUUID(player);
        buf.writeUtf(name, 64);
        buf.writeVarInt(kind);
        buf.writeBoolean(first);
        buf.writeVarInt(count);
    }

    private static CatchNews read(FriendlyByteBuf buf) {
        return new CatchNews(buf.readUUID(), buf.readUtf(64), buf.readVarInt(), buf.readBoolean(), buf.readVarInt());
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
