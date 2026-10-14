package com.skyrpg.events.contract;

import com.skyrpg.events.SkyRpgEvents;
import io.netty.buffer.ByteBuf;
import java.util.List;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.item.ItemStack;

/** The contracts board over the network: today's contracts go to everyone; a player asks to hand in or reroll one. */
public final class ContractPayloads {
    private ContractPayloads() {
    }

    static void register() {
        PayloadTypeRegistry.clientboundPlay().register(Board.TYPE, Board.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(Action.TYPE, Action.CODEC);
    }

    /**
     * One contract as shown: what to do, progress, rewards (one line each), the item to deliver (EMPTY if none), whether
     * you helped, and whether it's done or already rerolled today.
     */
    public record Entry(String name, String description, String category, int progress, int amount, List<Component> rewards,
                        ItemStack deliver, boolean helped, boolean done, boolean rerolled) {
        static final StreamCodec<RegistryFriendlyByteBuf, Entry> CODEC = StreamCodec.of((buf, e) -> {
            ByteBufCodecs.STRING_UTF8.encode(buf, e.name());
            ByteBufCodecs.STRING_UTF8.encode(buf, e.description());
            ByteBufCodecs.STRING_UTF8.encode(buf, e.category());
            ByteBufCodecs.VAR_INT.encode(buf, e.progress());
            ByteBufCodecs.VAR_INT.encode(buf, e.amount());
            ComponentSerialization.STREAM_CODEC.apply(ByteBufCodecs.list()).encode(buf, e.rewards());
            ItemStack.OPTIONAL_STREAM_CODEC.encode(buf, e.deliver());
            ByteBufCodecs.BOOL.encode(buf, e.helped());
            ByteBufCodecs.BOOL.encode(buf, e.done());
            ByteBufCodecs.BOOL.encode(buf, e.rerolled());
        }, buf -> new Entry(ByteBufCodecs.STRING_UTF8.decode(buf), ByteBufCodecs.STRING_UTF8.decode(buf), ByteBufCodecs.STRING_UTF8.decode(buf),
                ByteBufCodecs.VAR_INT.decode(buf), ByteBufCodecs.VAR_INT.decode(buf), ComponentSerialization.STREAM_CODEC.apply(ByteBufCodecs.list()).decode(buf),
                ItemStack.OPTIONAL_STREAM_CODEC.decode(buf), ByteBufCodecs.BOOL.decode(buf), ByteBufCodecs.BOOL.decode(buf), ByteBufCodecs.BOOL.decode(buf)));
    }

    /** Today's contracts, the reroll price, and the last result for this player (may be empty). */
    public record Board(List<Entry> contracts, long rerollCost, Component notice, boolean good) implements CustomPacketPayload {
        public static final Type<Board> TYPE = new Type<>(SkyRpgEvents.id("contracts"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Board> CODEC = StreamCodec.composite(
                Entry.CODEC.apply(ByteBufCodecs.list()), Board::contracts,
                ByteBufCodecs.VAR_LONG, Board::rerollCost,
                ComponentSerialization.STREAM_CODEC, Board::notice,
                ByteBufCodecs.BOOL, Board::good,
                Board::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** Hand in items for contract {@code index}, or reroll it. */
    public record Action(int index, boolean reroll) implements CustomPacketPayload {
        public static final Type<Action> TYPE = new Type<>(SkyRpgEvents.id("contract_action"));
        public static final StreamCodec<ByteBuf, Action> CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, Action::index,
                ByteBufCodecs.BOOL, Action::reroll,
                Action::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }
}
