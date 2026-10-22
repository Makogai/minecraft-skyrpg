package com.skyrpg.items.crafting;

import com.skyrpg.items.SkyRpgItems;
import io.netty.buffer.ByteBuf;
import java.util.List;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

/**
 * The Artisan Bench over the network. The server sends {@link View} (every recipe, what you have of each
 * ingredient, what's unlocked, and the last result) when the bench opens and after each craft; the client
 * asks to {@link Craft} and the server checks everything again.
 */
public final class BenchPayloads {
    private BenchPayloads() {
    }

    static void register() {
        PayloadTypeRegistry.clientboundPlay().register(View.TYPE, View.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(Craft.TYPE, Craft.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(Tracked.TYPE, Tracked.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(Track.TYPE, Track.CODEC);
    }

    /** One recipe as the player sees it: {@code have} lines up with {@code ingredients}. */
    public record Entry(Identifier id, String category, ItemStack result, List<ItemStack> ingredients, List<Integer> have,
                        String requirement, boolean unlocked) {
        static final StreamCodec<RegistryFriendlyByteBuf, Entry> CODEC = StreamCodec.composite(
                Identifier.STREAM_CODEC, Entry::id,
                ByteBufCodecs.STRING_UTF8, Entry::category,
                ItemStack.STREAM_CODEC, Entry::result,
                ItemStack.STREAM_CODEC.apply(ByteBufCodecs.list()), Entry::ingredients,
                ByteBufCodecs.VAR_INT.apply(ByteBufCodecs.list()), Entry::have,
                ByteBufCodecs.STRING_UTF8, Entry::requirement,
                ByteBufCodecs.BOOL, Entry::unlocked,
                Entry::new);

        /** How many times it can be crafted with what you have. */
        public int craftable() {
            int times = Integer.MAX_VALUE;
            for (int i = 0; i < ingredients.size(); i++) {
                times = Math.min(times, have.get(i) / Math.max(1, ingredients.get(i).getCount()));
            }
            return unlocked ? times : 0;
        }
    }

    /** {@code refresh}: update an open bench screen instead of opening one. {@code notice} may be empty. */
    public record View(List<Entry> entries, Component notice, boolean good, boolean refresh) implements CustomPacketPayload {
        public static final Type<View> TYPE = new Type<>(SkyRpgItems.id("bench_view"));
        public static final StreamCodec<RegistryFriendlyByteBuf, View> CODEC = StreamCodec.composite(
                Entry.CODEC.apply(ByteBufCodecs.list()), View::entries,
                ComponentSerialization.STREAM_CODEC, View::notice,
                ByteBufCodecs.BOOL, View::good,
                ByteBufCodecs.BOOL, View::refresh,
                View::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** Craft {@code recipe} {@code times} times (as many as can be afforded, up to that). */
    public record Craft(Identifier recipe, int times) implements CustomPacketPayload {
        public static final Type<Craft> TYPE = new Type<>(SkyRpgItems.id("bench_craft"));
        public static final StreamCodec<ByteBuf, Craft> CODEC = StreamCodec.composite(
                Identifier.STREAM_CODEC, Craft::recipe,
                ByteBufCodecs.VAR_INT, Craft::times,
                Craft::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** Track {@code recipe} on the HUD, or stop tracking ({@code recipe} empty). */
    public record Track(String recipe) implements CustomPacketPayload {
        public static final Type<Track> TYPE = new Type<>(SkyRpgItems.id("bench_track"));
        public static final StreamCodec<ByteBuf, Track> CODEC = StreamCodec.composite(
                ByteBufCodecs.STRING_UTF8, Track::recipe,
                Track::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** The tracked recipe with what you have now (sent every second while tracking); empty = nothing tracked. */
    public record Tracked(List<Entry> entry) implements CustomPacketPayload {
        public static final Type<Tracked> TYPE = new Type<>(SkyRpgItems.id("bench_tracked"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Tracked> CODEC = StreamCodec.composite(
                Entry.CODEC.apply(ByteBufCodecs.list()), Tracked::entry,
                Tracked::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }
}
