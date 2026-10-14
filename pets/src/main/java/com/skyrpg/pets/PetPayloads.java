package com.skyrpg.pets;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** The K menu's Pets page asks the server to manage their pet storage; the server checks everything again. */
public final class PetPayloads {
    private PetPayloads() {
    }

    static void register() {
        PayloadTypeRegistry.serverboundPlay().register(Equip.TYPE, Equip.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(Unequip.TYPE, Unequip.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(TakeOut.TYPE, TakeOut.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(Equip.TYPE, (payload, context) -> Pets.equipStored(context.player(), payload.index()));
        ServerPlayNetworking.registerGlobalReceiver(Unequip.TYPE, (payload, context) -> Pets.unequip(context.player()));
        ServerPlayNetworking.registerGlobalReceiver(TakeOut.TYPE, (payload, context) -> Pets.takeOut(context.player(), payload.index()));
    }

    /** Make the stored pet at {@code index} the active pet (the old one goes into its place in storage). */
    public record Equip(int index) implements CustomPacketPayload {
        public static final Type<Equip> TYPE = new Type<>(SkyRpgPets.id("equip"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Equip> CODEC = StreamCodec.composite(ByteBufCodecs.VAR_INT, Equip::index, Equip::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** Send the active pet back to storage. */
    public record Unequip() implements CustomPacketPayload {
        public static final Type<Unequip> TYPE = new Type<>(SkyRpgPets.id("unequip"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Unequip> CODEC = StreamCodec.unit(new Unequip());

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** Move the stored pet at {@code index} into the inventory as an item. */
    public record TakeOut(int index) implements CustomPacketPayload {
        public static final Type<TakeOut> TYPE = new Type<>(SkyRpgPets.id("take_out"));
        public static final StreamCodec<RegistryFriendlyByteBuf, TakeOut> CODEC = StreamCodec.composite(ByteBufCodecs.VAR_INT, TakeOut::index, TakeOut::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }
}
