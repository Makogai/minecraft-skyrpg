package com.skyrpg.progress.titles;

import com.skyrpg.progress.SkyRpgProgress;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** The titles' packets: the catalogue to clients (join and /reload) and the K page's pick (an empty id clears the title). */
public final class TitlePayloads {
    private TitlePayloads() {
    }

    public record Defs(String json) implements CustomPacketPayload {
        public static final Type<Defs> TYPE = new Type<>(SkyRpgProgress.id("title_defs"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Defs> CODEC = StreamCodec.composite(
                ByteBufCodecs.stringUtf8(262_144), Defs::json, Defs::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record Select(String id) implements CustomPacketPayload {
        public static final Type<Select> TYPE = new Type<>(SkyRpgProgress.id("title_select"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Select> CODEC = StreamCodec.composite(
                ByteBufCodecs.STRING_UTF8, Select::id, Select::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public static void register() {
        PayloadTypeRegistry.clientboundPlay().register(Defs.TYPE, Defs.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(Select.TYPE, Select.CODEC);
    }
}
