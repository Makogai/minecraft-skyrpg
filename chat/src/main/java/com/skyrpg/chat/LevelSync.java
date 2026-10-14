package com.skyrpg.chat;

import io.netty.buffer.ByteBuf;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * Every online player's SkyRPG Level for every client, so the tab list can show it next to each name (a client only
 * knows the synced level of players it can see). A joining player gets the whole list; afterwards only changes go out,
 * checked every two seconds. A level of -1 means the player left.
 */
public final class LevelSync {
    private static final int CHECK_TICKS = 40;
    private static final Map<UUID, Integer> SENT = new HashMap<>();

    /** Levels by player; {@code full} replaces what the client has, otherwise it's an update. */
    public record Levels(Map<UUID, Integer> levels, boolean full) implements CustomPacketPayload {
        public static final Type<Levels> TYPE = new Type<>(SkyRpgChat.id("levels"));
        public static final StreamCodec<ByteBuf, Levels> CODEC = StreamCodec.composite(
                ByteBufCodecs.map(HashMap::new, UUIDUtil.STREAM_CODEC, ByteBufCodecs.VAR_INT), Levels::levels,
                ByteBufCodecs.BOOL, Levels::full,
                Levels::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    private LevelSync() {
    }

    static void init() {
        PayloadTypeRegistry.clientboundPlay().register(Levels.TYPE, Levels.CODEC);
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> server.execute(() -> {
            ServerPlayer joined = handler.getPlayer();
            Map<UUID, Integer> all = new HashMap<>();
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                all.put(player.getUUID(), Profiles.level(player));
            }
            all.put(joined.getUUID(), Profiles.level(joined)); // the joining player may not be in the player list yet
            if (ServerPlayNetworking.canSend(joined, Levels.TYPE)) {
                ServerPlayNetworking.send(joined, new Levels(all, true));
            }
            int level = Profiles.level(joined);
            SENT.put(joined.getUUID(), level);
            broadcast(server, Map.of(joined.getUUID(), level), joined);
        }));
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            UUID id = handler.getPlayer().getUUID();
            SENT.remove(id);
            server.execute(() -> broadcast(server, Map.of(id, -1), null));
        });
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (server.getTickCount() % CHECK_TICKS != 0) {
                return;
            }
            Map<UUID, Integer> changed = new HashMap<>();
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                int level = Profiles.level(player);
                Integer before = SENT.put(player.getUUID(), level);
                if (before == null || before != level) {
                    changed.put(player.getUUID(), level);
                }
            }
            if (!changed.isEmpty()) {
                broadcast(server, changed, null);
            }
        });
    }

    private static void broadcast(MinecraftServer server, Map<UUID, Integer> levels, ServerPlayer except) {
        Levels payload = new Levels(new HashMap<>(levels), false);
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (player != except && ServerPlayNetworking.canSend(player, Levels.TYPE)) {
                ServerPlayNetworking.send(player, payload);
            }
        }
    }
}
