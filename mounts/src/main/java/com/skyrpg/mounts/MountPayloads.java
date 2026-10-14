package com.skyrpg.mounts;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** The K menu's Mounts page asks the server to manage the stable; the server checks everything again. */
public final class MountPayloads {
    private MountPayloads() {
    }

    static void register() {
        PayloadTypeRegistry.serverboundPlay().register(Summon.TYPE, Summon.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(Away.TYPE, Away.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(TakeOut.TYPE, TakeOut.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(Summon.TYPE, (payload, context) -> Mounts.summon(context.player(), payload.index()));
        ServerPlayNetworking.registerGlobalReceiver(Away.TYPE, (payload, context) -> Mounts.away(context.player(), true));
        ServerPlayNetworking.registerGlobalReceiver(TakeOut.TYPE, (payload, context) -> Mounts.takeOut(context.player(), payload.index()));
    }

    /** Call the stable mount at {@code index} (the one that was out goes back); -1 calls the active mount again (its body is gone). */
    public record Summon(int index) implements CustomPacketPayload {
        public static final Type<Summon> TYPE = new Type<>(SkyRpgMounts.id("summon"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Summon> CODEC = StreamCodec.composite(ByteBufCodecs.VAR_INT, Summon::index, Summon::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** Put the mount that is out back into the stable. */
    public record Away() implements CustomPacketPayload {
        public static final Type<Away> TYPE = new Type<>(SkyRpgMounts.id("away"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Away> CODEC = StreamCodec.unit(new Away());

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** Move the stable mount at {@code index} into the inventory as a whistle. */
    public record TakeOut(int index) implements CustomPacketPayload {
        public static final Type<TakeOut> TYPE = new Type<>(SkyRpgMounts.id("take_out"));
        public static final StreamCodec<RegistryFriendlyByteBuf, TakeOut> CODEC = StreamCodec.composite(ByteBufCodecs.VAR_INT, TakeOut::index, TakeOut::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }
}
