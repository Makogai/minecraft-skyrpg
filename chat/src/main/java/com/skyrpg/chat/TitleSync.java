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
 * Every online player's shown title (admin-set or earned, see {@link Titles#effective}) for every client, so the tab list
 * can draw it next to the name. Same shape as {@link LevelSync}: the whole list on join, then changes every two seconds.
 * An entry with empty text means "no title" (or the player left).
 */
public final class TitleSync {
    private static final int CHECK_TICKS = 40;
    private static final Entry NONE = new Entry("", "", 0);
    private static final Map<UUID, Entry> SENT = new HashMap<>();

    /** One player's title as drawn: text, icon (chat icon glyph or symbol) and colour. */
    public record Entry(String text, String icon, int color) {
        static final StreamCodec<ByteBuf, Entry> CODEC = StreamCodec.composite(
                ByteBufCodecs.STRING_UTF8, Entry::text,
                ByteBufCodecs.STRING_UTF8, Entry::icon,
                ByteBufCodecs.INT, Entry::color,
                Entry::new);
    }

    public record Shown(Map<UUID, Entry> titles, boolean full) implements CustomPacketPayload {
        public static final Type<Shown> TYPE = new Type<>(SkyRpgChat.id("titles"));
        public static final StreamCodec<ByteBuf, Shown> CODEC = StreamCodec.composite(
                ByteBufCodecs.map(HashMap::new, UUIDUtil.STREAM_CODEC, Entry.CODEC), Shown::titles,
                ByteBufCodecs.BOOL, Shown::full,
                Shown::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    private TitleSync() {
    }

    private static Entry of(ServerPlayer player) {
        Titles.Title title = Titles.effective(player);
        return title == null ? NONE : new Entry(title.text(), title.icon(), title.color() & 0xFFFFFF);
    }

    static void init() {
        PayloadTypeRegistry.clientboundPlay().register(Shown.TYPE, Shown.CODEC);
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> server.execute(() -> {
            ServerPlayer joined = handler.getPlayer();
            Map<UUID, Entry> all = new HashMap<>();
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                Entry entry = of(player);
                if (!entry.text().isEmpty()) {
                    all.put(player.getUUID(), entry);
                }
            }
            // the joining player may not be in the player list yet when this runs: always include them
            Entry own = of(joined);
            if (!own.text().isEmpty()) {
                all.put(joined.getUUID(), own);
            }
            if (ServerPlayNetworking.canSend(joined, Shown.TYPE)) {
                ServerPlayNetworking.send(joined, new Shown(all, true));
            }
            Entry mine = of(joined);
            SENT.put(joined.getUUID(), mine);
            if (!mine.text().isEmpty()) {
                broadcast(server, Map.of(joined.getUUID(), mine), joined);
            }
        }));
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            UUID id = handler.getPlayer().getUUID();
            if (SENT.remove(id) != null) {
                server.execute(() -> broadcast(server, Map.of(id, NONE), null));
            }
        });
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (server.getTickCount() % CHECK_TICKS != 0) {
                return;
            }
            Map<UUID, Entry> changed = new HashMap<>();
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                Entry now = of(player);
                Entry before = SENT.put(player.getUUID(), now);
                if (before == null ? !now.text().isEmpty() : !before.equals(now)) {
                    changed.put(player.getUUID(), now);
                }
            }
            if (!changed.isEmpty()) {
                broadcast(server, changed, null);
            }
        });
    }

    private static void broadcast(MinecraftServer server, Map<UUID, Entry> titles, ServerPlayer except) {
        Shown payload = new Shown(new HashMap<>(titles), false);
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (player != except && ServerPlayNetworking.canSend(player, Shown.TYPE)) {
                ServerPlayNetworking.send(player, payload);
            }
        }
    }
}
