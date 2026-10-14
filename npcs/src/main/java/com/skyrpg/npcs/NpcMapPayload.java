package com.skyrpg.npcs;

import java.util.List;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;

/**
 * Where every NPC stands, for the maps: sent on join and after every /reload, so NPCs show on the map even when
 * they're too far away for their entity to be loaded on the client.
 */
public record NpcMapPayload(List<Entry> npcs) implements CustomPacketPayload {
    public static final Type<NpcMapPayload> TYPE = new Type<>(SkyRpgNpcs.id("map"));

    public record Entry(Identifier id, String name, String role, String hint, int color, Identifier skin, Identifier dimension,
                        double x, double y, double z) {
        static final StreamCodec<RegistryFriendlyByteBuf, Entry> CODEC = StreamCodec.of((buf, e) -> {
            Identifier.STREAM_CODEC.encode(buf, e.id());
            ByteBufCodecs.STRING_UTF8.encode(buf, e.name());
            ByteBufCodecs.STRING_UTF8.encode(buf, e.role());
            ByteBufCodecs.STRING_UTF8.encode(buf, e.hint());
            buf.writeInt(e.color());
            Identifier.STREAM_CODEC.encode(buf, e.skin());
            Identifier.STREAM_CODEC.encode(buf, e.dimension());
            buf.writeDouble(e.x());
            buf.writeDouble(e.y());
            buf.writeDouble(e.z());
        }, buf -> new Entry(Identifier.STREAM_CODEC.decode(buf), ByteBufCodecs.STRING_UTF8.decode(buf), ByteBufCodecs.STRING_UTF8.decode(buf),
                ByteBufCodecs.STRING_UTF8.decode(buf), buf.readInt(), Identifier.STREAM_CODEC.decode(buf), Identifier.STREAM_CODEC.decode(buf),
                buf.readDouble(), buf.readDouble(), buf.readDouble()));
    }

    public static final StreamCodec<RegistryFriendlyByteBuf, NpcMapPayload> CODEC =
            Entry.CODEC.apply(ByteBufCodecs.list()).map(NpcMapPayload::new, NpcMapPayload::npcs);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    static void init() {
        PayloadTypeRegistry.clientboundPlay().register(TYPE, CODEC);
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> send(handler.player));
        ServerLifecycleEvents.END_DATA_PACK_RELOAD.register((server, resources, success) ->
                server.getPlayerList().getPlayers().forEach(NpcMapPayload::send));
    }

    private static void send(ServerPlayer player) {
        List<Entry> entries = Npcs.definitions().values().stream()
                .map(def -> new Entry(def.id(), def.name(), def.role(), def.hint(), def.color(), def.skin(), def.dimension(),
                        def.pos().x, def.pos().y, def.pos().z))
                .toList();
        ServerPlayNetworking.send(player, new NpcMapPayload(entries));
    }
}
