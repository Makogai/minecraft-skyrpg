package com.skyrpg.progress.collections;

import com.skyrpg.progress.SkyRpgProgress;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Server to client: what the collections are (on join and /reload) and the player's own counts (throttled). */
public final class CollectionPayloads {
    private CollectionPayloads() {
    }

    static void register() {
        PayloadTypeRegistry.clientboundPlay().register(Defs.TYPE, Defs.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(Counts.TYPE, Counts.CODEC);
    }

    /**
     * What the Collections page needs of one collection: {@code rewards} holds one line per tier.
     *
     * @param icon item id drawn next to the name
     */
    public record View(Identifier id, String category, String name, Identifier icon, List<Long> thresholds, List<String> rewards) {
        static final StreamCodec<RegistryFriendlyByteBuf, View> CODEC = StreamCodec.composite(
                Identifier.STREAM_CODEC, View::id,
                ByteBufCodecs.STRING_UTF8, View::category,
                ByteBufCodecs.STRING_UTF8, View::name,
                Identifier.STREAM_CODEC, View::icon,
                ByteBufCodecs.VAR_LONG.apply(ByteBufCodecs.list()), View::thresholds,
                ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list()), View::rewards,
                View::new);
    }

    public record Defs(List<View> views) implements CustomPacketPayload {
        public static final Type<Defs> TYPE = new Type<>(SkyRpgProgress.id("collection_defs"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Defs> CODEC = StreamCodec.composite(
                View.CODEC.apply(ByteBufCodecs.list()), Defs::views, Defs::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record Counts(Map<Identifier, Long> counts) implements CustomPacketPayload {
        public static final Type<Counts> TYPE = new Type<>(SkyRpgProgress.id("collection_counts"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Counts> CODEC = StreamCodec.composite(
                ByteBufCodecs.map(HashMap::new, Identifier.STREAM_CODEC, ByteBufCodecs.VAR_LONG), Counts::counts, Counts::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }
}
