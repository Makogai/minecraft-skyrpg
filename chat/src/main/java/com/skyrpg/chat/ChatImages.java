package com.skyrpg.chat;

import io.netty.buffer.ByteBuf;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.security.SecureRandom;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * Screenshot sharing, server side. A client sends a downscaled screenshot in {@link Upload} chunks; the server checks
 * it (size, picture header, dimensions, one share per 30 s per player), relays it to every player that has the mod as
 * {@link ImageChunk}s with a fresh image id, and says "Name shared a screenshot [img:id]" in chat; clients render the
 * picture inline from the id. The server keeps the last few pictures in memory only, and never writes them to disk.
 */
public final class ChatImages {
    /** Bytes per packet (a serverbound custom payload may carry at most 32 KB). */
    public static final int CHUNK = 20_000;
    public static final int MAX_BYTES = 320_000;
    public static final int MAX_CHUNKS = (MAX_BYTES + CHUNK - 1) / CHUNK;
    public static final int MAX_DIMENSION = 1280;
    public static final long COOLDOWN_MS = 30_000;
    private static final int KEEP = 4;

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Map<UUID, Pending> PENDING = new HashMap<>();
    private static final Map<UUID, Long> LAST_SHARE = new HashMap<>();
    private static final Deque<Stored> RECENT = new ArrayDeque<>();

    private ChatImages() {
    }

    /** Client to server: one piece of a screenshot. {@code upload} is the client's id for this upload. */
    public record Upload(int upload, int index, int total, byte[] data) implements CustomPacketPayload {
        public static final Type<Upload> TYPE = new Type<>(SkyRpgChat.id("share_upload"));
        public static final StreamCodec<ByteBuf, Upload> CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, Upload::upload, ByteBufCodecs.VAR_INT, Upload::index, ByteBufCodecs.VAR_INT, Upload::total,
                ByteBufCodecs.byteArray(CHUNK), Upload::data, Upload::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** Server to client: one piece of a shared picture, named by {@code id} (8 hex digits). */
    public record ImageChunk(String id, String sender, int index, int total, byte[] data) implements CustomPacketPayload {
        public static final Type<ImageChunk> TYPE = new Type<>(SkyRpgChat.id("image_chunk"));
        public static final StreamCodec<ByteBuf, ImageChunk> CODEC = StreamCodec.composite(
                ByteBufCodecs.STRING_UTF8, ImageChunk::id, ByteBufCodecs.STRING_UTF8, ImageChunk::sender,
                ByteBufCodecs.VAR_INT, ImageChunk::index, ByteBufCodecs.VAR_INT, ImageChunk::total,
                ByteBufCodecs.byteArray(CHUNK), ImageChunk::data, ImageChunk::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    private static final class Pending {
        final int upload;
        final int total;
        final List<byte[]> parts = new ArrayList<>();
        int size;

        Pending(int upload, int total) {
            this.upload = upload;
            this.total = total;
        }
    }

    private record Stored(String id, String sender, byte[] data) {
    }

    static void init() {
        PayloadTypeRegistry.serverboundPlay().register(Upload.TYPE, Upload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(ImageChunk.TYPE, ImageChunk.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(Upload.TYPE, (payload, context) -> onChunk(context.player(), payload));
    }

    static void forget(ServerPlayer player) {
        PENDING.remove(player.getUUID());
    }

    private static void onChunk(ServerPlayer player, Upload chunk) {
        UUID who = player.getUUID();
        if (chunk.data().length == 0 || chunk.data().length > CHUNK) {
            PENDING.remove(who);
            return;
        }
        Pending pending = PENDING.get(who);
        if (chunk.index() == 0) {
            long now = System.currentTimeMillis();
            Long last = LAST_SHARE.get(who);
            if (last != null && now - last < COOLDOWN_MS) {
                PENDING.remove(who);
                long wait = (COOLDOWN_MS - (now - last) + 999) / 1000;
                tell(player, "You can share one screenshot every 30 seconds. Try again in " + wait + "s.");
                return;
            }
            if (chunk.total() < 1 || chunk.total() > MAX_CHUNKS) {
                PENDING.remove(who);
                tell(player, "That screenshot is too big to share.");
                return;
            }
            LAST_SHARE.put(who, now);
            pending = new Pending(chunk.upload(), chunk.total());
            PENDING.put(who, pending);
        } else if (pending == null || pending.upload != chunk.upload() || pending.total != chunk.total() || pending.parts.size() != chunk.index()) {
            // Out of order, or the first chunk was refused: drop it.
            PENDING.remove(who);
            return;
        }
        pending.size += chunk.data().length;
        if (pending.size > MAX_BYTES) {
            PENDING.remove(who);
            tell(player, "That screenshot is too big to share (the limit is " + MAX_BYTES / 1000 + " KB).");
            return;
        }
        pending.parts.add(chunk.data());
        if (pending.parts.size() < pending.total) {
            return;
        }
        PENDING.remove(who);
        ByteArrayOutputStream out = new ByteArrayOutputStream(pending.size);
        for (byte[] part : pending.parts) {
            out.write(part, 0, part.length);
        }
        finish(player, out.toByteArray());
    }

    private static void finish(ServerPlayer player, byte[] data) {
        if (!looksLikeImage(data)) {
            tell(player, "That doesn't look like a PNG or JPEG picture.");
            return;
        }
        if (!sizeOk(data)) {
            tell(player, "That screenshot's dimensions aren't accepted.");
            return;
        }
        String id = String.format("%08x", RANDOM.nextInt());
        String sender = player.getGameProfile().name();
        RECENT.addLast(new Stored(id, sender, data));
        while (RECENT.size() > KEEP) {
            RECENT.removeFirst();
        }
        MinecraftServer server = player.level().getServer();
        int total = (data.length + CHUNK - 1) / CHUNK;
        SkyRpgChat.LOGGER.info("{} shared a screenshot ({} KB)", sender, data.length / 1000);
        for (ServerPlayer target : server.getPlayerList().getPlayers()) {
            boolean modded = ServerPlayNetworking.canSend(target, ImageChunk.TYPE);
            if (modded) {
                for (int i = 0; i < total; i++) {
                    int from = i * CHUNK;
                    byte[] part = java.util.Arrays.copyOfRange(data, from, Math.min(data.length, from + CHUNK));
                    ServerPlayNetworking.send(target, new ImageChunk(id, sender, i, total, part));
                }
            }
            if (target.getChatVisibility() == net.minecraft.world.entity.player.ChatVisiblity.FULL) {
                // The token is what the client turns into the picture; players without the mod just read the words.
                target.sendSystemMessage(ServerChat.emoteLine(player, modded ? "shared a screenshot [img:" + id + "]" : "shared a screenshot"));
            }
        }
    }

    private static void tell(ServerPlayer player, String message) {
        player.sendSystemMessage(Component.literal(message).withStyle(ChatFormatting.RED));
    }

    /** PNG or JPEG magic numbers. */
    static boolean looksLikeImage(byte[] data) {
        if (data.length < 16) {
            return false;
        }
        boolean png = (data[0] & 0xFF) == 0x89 && data[1] == 'P' && data[2] == 'N' && data[3] == 'G';
        boolean jpeg = (data[0] & 0xFF) == 0xFF && (data[1] & 0xFF) == 0xD8 && (data[2] & 0xFF) == 0xFF;
        return png || jpeg;
    }

    /** Reads only the picture's header: no pixels are decoded on the server. */
    private static boolean sizeOk(byte[] data) {
        try (ImageInputStream in = ImageIO.createImageInputStream(new ByteArrayInputStream(data))) {
            Iterator<ImageReader> readers = ImageIO.getImageReaders(in);
            if (!readers.hasNext()) {
                return false;
            }
            ImageReader reader = readers.next();
            try {
                reader.setInput(in, true, true);
                int width = reader.getWidth(0);
                int height = reader.getHeight(0);
                return width > 0 && height > 0 && width <= MAX_DIMENSION && height <= MAX_DIMENSION;
            } finally {
                reader.dispose();
            }
        } catch (IOException | RuntimeException e) {
            return false;
        }
    }
}
