package com.skyrpg.items.backpack;

import com.skyrpg.items.SkyRpgItems;
import io.netty.buffer.ByteBuf;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** The client asks to open a carried backpack: right-click on its slot, or the backpack key (slot -1 = the first one). */
public final class BackpackPayloads {
    public record Open(int slot) implements CustomPacketPayload {
        public static final Type<Open> TYPE = new Type<>(SkyRpgItems.id("open_backpack"));
        public static final StreamCodec<ByteBuf, Open> CODEC = ByteBufCodecs.VAR_INT.map(Open::new, Open::slot);

        @Override
        public Type<Open> type() {
            return TYPE;
        }
    }

    private BackpackPayloads() {
    }

    static void init() {
        PayloadTypeRegistry.serverboundPlay().register(Open.TYPE, Open.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(Open.TYPE, (payload, context) -> Backpacks.open(context.player(), payload.slot()));
    }
}
