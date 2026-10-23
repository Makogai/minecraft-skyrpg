package com.skyrpg.core.net;

import com.skyrpg.core.SkyRpgCore;
import io.netty.buffer.ByteBuf;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.zip.DataFormatException;
import java.util.zip.Deflater;
import java.util.zip.Inflater;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Util;
import net.minecraft.world.level.storage.LevelResource;

/**
 * The shared world map: every player's map mod (utils) sends the chunks it maps ({@link Upload}, one colour per block
 * column, 0 = unmapped), the server merges them into 256x256 tiles kept in {@code <world>/skyrpg_map/<dimension>/},
 * and clients ask for the tiles they look at ({@link Request}), getting them back ({@link Tile}) whenever they changed
 * since the version they hold. So what anyone explores shows up on everyone's map. Players without the mod send nothing.
 */
public final class SharedMap {
    public static final int TILE = 256;
    public static final int CHUNKS_PER_UPLOAD = 16;
    /** A chunk's colours: 16 x 16 ints. */
    public static final int CHUNK_BYTES = 16 * 16 * 4;
    /** How far from the player (in chunks) an uploaded chunk may be. */
    private static final int UPLOAD_RANGE = 40;
    private static final int MAX_TILES = 128;
    private static final long SAVE_EVERY_MS = 60_000;
    /** Per player per second: uploads and tile requests. */
    private static final int MAX_UPLOADS = 10;
    private static final int MAX_REQUESTS = 30;

    private static final class Stored {
        final int[] pixels;
        long stamp;
        boolean unsaved;
        long lastUsed;

        Stored(int[] pixels, long stamp) {
            this.pixels = pixels;
            this.stamp = stamp;
        }
    }

    private static final Map<String, Stored> TILES = new HashMap<>();
    private static final Map<UUID, int[]> BUDGET = new HashMap<>();
    private static MinecraftServer server;
    private static long lastSave = Util.getMillis();
    private static long budgetSecond;

    private SharedMap() {
    }

    public static void register() {
        PayloadTypeRegistry.serverboundPlay().register(Upload.TYPE, Upload.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(Request.TYPE, Request.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(Tile.TYPE, Tile.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(Upload.TYPE, (payload, context) -> upload(context.player(), payload));
        ServerPlayNetworking.registerGlobalReceiver(Request.TYPE, (payload, context) -> request(context.player(), payload));
        ServerLifecycleEvents.SERVER_STARTED.register(s -> server = s);
        ServerLifecycleEvents.SERVER_STOPPING.register(s -> {
            saveAll();
            TILES.clear();
            server = null;
        });
        ServerPlayConnectionEvents.DISCONNECT.register((handler, s) -> BUDGET.remove(handler.player.getUUID()));
        ServerTickEvents.END_SERVER_TICK.register(s -> {
            if (Util.getMillis() - lastSave > SAVE_EVERY_MS) {
                saveAll();
            }
        });
    }

    /** Spends one of the player's uploads (0) or requests (1) this second; false when they're out. */
    private static boolean spend(ServerPlayer player, int kind, int max) {
        long second = Util.getMillis() / 1000;
        if (second != budgetSecond) {
            budgetSecond = second;
            BUDGET.clear();
        }
        int[] used = BUDGET.computeIfAbsent(player.getUUID(), id -> new int[2]);
        return used[kind]++ < max;
    }

    private static void upload(ServerPlayer player, Upload upload) {
        int count = upload.chunks().length / 2;
        if (server == null || count > CHUNKS_PER_UPLOAD || upload.colors().length != count * CHUNK_BYTES || !spend(player, 0, MAX_UPLOADS)) {
            return;
        }
        String dimension = player.level().dimension().identifier().toString();
        ByteBuffer colors = ByteBuffer.wrap(upload.colors());
        int px = player.chunkPosition().x();
        int pz = player.chunkPosition().z();
        long now = Util.getMillis();
        for (int i = 0; i < count; i++) {
            int cx = upload.chunks()[i * 2];
            int cz = upload.chunks()[i * 2 + 1];
            if (Math.abs(cx - px) > UPLOAD_RANGE || Math.abs(cz - pz) > UPLOAD_RANGE) {
                colors.position(colors.position() + CHUNK_BYTES);
                continue;
            }
            Stored tile = tile(dimension, Math.floorDiv(cx * 16, TILE), Math.floorDiv(cz * 16, TILE), true);
            int baseX = Math.floorMod(cx * 16, TILE);
            int baseZ = Math.floorMod(cz * 16, TILE);
            boolean changed = false;
            for (int z = 0; z < 16; z++) {
                for (int x = 0; x < 16; x++) {
                    int color = colors.getInt();
                    int index = (baseZ + z) * TILE + baseX + x;
                    if (color != 0 && tile.pixels[index] != color) {
                        tile.pixels[index] = color;
                        changed = true;
                    }
                }
            }
            if (changed) {
                tile.stamp = Math.max(now, tile.stamp + 1);
                tile.unsaved = true;
            }
        }
    }

    private static void request(ServerPlayer player, Request request) {
        if (server == null || !spend(player, 1, MAX_REQUESTS)) {
            return;
        }
        String dimension = player.level().dimension().identifier().toString();
        Stored tile = tile(dimension, request.rx(), request.rz(), false);
        if (tile != null && tile.stamp > request.stamp()) {
            ServerPlayNetworking.send(player, new Tile(dimension, request.rx(), request.rz(), tile.stamp, deflate(tile.pixels)));
        }
    }

    private static Stored tile(String dimension, int rx, int rz, boolean create) {
        String key = dimension + "|" + rx + "|" + rz;
        Stored tile = TILES.get(key);
        if (tile == null) {
            tile = load(dimension, rx, rz);
            if (tile == null && create) {
                tile = new Stored(new int[TILE * TILE], 0);
            }
            if (tile == null) {
                return null;
            }
            if (TILES.size() >= MAX_TILES) {
                evict();
            }
            TILES.put(key, tile);
        }
        tile.lastUsed = Util.getMillis();
        return tile;
    }

    private static void evict() {
        List<Map.Entry<String, Stored>> oldest = new ArrayList<>(TILES.entrySet());
        oldest.sort(Comparator.comparingLong(entry -> entry.getValue().lastUsed));
        for (Map.Entry<String, Stored> entry : oldest.subList(0, oldest.size() / 4)) {
            save(entry.getKey(), entry.getValue());
            TILES.remove(entry.getKey());
        }
    }

    private static void saveAll() {
        lastSave = Util.getMillis();
        TILES.forEach(SharedMap::save);
    }

    private static void save(String key, Stored tile) {
        if (!tile.unsaved) {
            return;
        }
        String[] parts = key.split("\\|");
        Path file = file(parts[0], Integer.parseInt(parts[1]), Integer.parseInt(parts[2]));
        if (file == null) {
            return;
        }
        try {
            Files.createDirectories(file.getParent());
            Path temp = file.resolveSibling(file.getFileName() + ".tmp");
            ByteBuffer out = ByteBuffer.allocate(8);
            out.putLong(tile.stamp);
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            bytes.write(out.array());
            bytes.write(deflate(tile.pixels));
            Files.write(temp, bytes.toByteArray());
            Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            tile.unsaved = false;
        } catch (IOException | RuntimeException e) {
            SkyRpgCore.LOGGER.warn("Couldn't save shared map tile {}", file, e);
        }
    }

    private static Stored load(String dimension, int rx, int rz) {
        Path file = file(dimension, rx, rz);
        if (file == null || !Files.isRegularFile(file)) {
            return null;
        }
        try {
            byte[] bytes = Files.readAllBytes(file);
            long stamp = ByteBuffer.wrap(bytes, 0, 8).getLong();
            int[] pixels = inflate(java.util.Arrays.copyOfRange(bytes, 8, bytes.length));
            return pixels == null ? null : new Stored(pixels, stamp);
        } catch (IOException | RuntimeException e) {
            SkyRpgCore.LOGGER.warn("Couldn't read shared map tile {}", file, e);
            return null;
        }
    }

    private static Path file(String dimension, int rx, int rz) {
        if (server == null) {
            return null;
        }
        Identifier id = Identifier.parse(dimension);
        return server.getWorldPath(LevelResource.ROOT).resolve("skyrpg_map").resolve(id.getNamespace()).resolve(id.getPath())
                .resolve(rx + "_" + rz + ".bin");
    }

    /** A tile's pixels, deflated. */
    public static byte[] deflate(int[] pixels) {
        ByteBuffer raw = ByteBuffer.allocate(pixels.length * 4);
        raw.asIntBuffer().put(pixels);
        Deflater deflater = new Deflater(Deflater.BEST_SPEED);
        deflater.setInput(raw.array());
        deflater.finish();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[16384];
        while (!deflater.finished()) {
            out.write(buffer, 0, deflater.deflate(buffer));
        }
        deflater.end();
        return out.toByteArray();
    }

    /** The other way round; null if the data isn't a whole tile. */
    public static int[] inflate(byte[] data) {
        Inflater inflater = new Inflater();
        inflater.setInput(data);
        byte[] raw = new byte[TILE * TILE * 4];
        try {
            int read = 0;
            while (read < raw.length && !inflater.finished()) {
                int n = inflater.inflate(raw, read, raw.length - read);
                if (n == 0 && (inflater.needsInput() || inflater.needsDictionary())) {
                    break;
                }
                read += n;
            }
            if (read != raw.length) {
                return null;
            }
        } catch (DataFormatException e) {
            return null;
        } finally {
            inflater.end();
        }
        int[] pixels = new int[TILE * TILE];
        ByteBuffer.wrap(raw).asIntBuffer().get(pixels);
        return pixels;
    }

    /** Client to server: mapped chunks in the sender's dimension, as (x, z) pairs and 256 colours each (big-endian ints). */
    public record Upload(int[] chunks, byte[] colors) implements CustomPacketPayload {
        public static final Type<Upload> TYPE = new Type<>(SkyRpgCore.id("map_upload"));
        public static final StreamCodec<ByteBuf, Upload> CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT.apply(ByteBufCodecs.list(CHUNKS_PER_UPLOAD * 2)).map(
                        list -> list.stream().mapToInt(Integer::intValue).toArray(),
                        array -> java.util.Arrays.stream(array).boxed().toList()), Upload::chunks,
                ByteBufCodecs.byteArray(CHUNKS_PER_UPLOAD * CHUNK_BYTES), Upload::colors,
                Upload::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** Client to server: send tile (rx, rz) of my dimension if it's newer than {@code stamp}. */
    public record Request(int rx, int rz, long stamp) implements CustomPacketPayload {
        public static final Type<Request> TYPE = new Type<>(SkyRpgCore.id("map_request"));
        public static final StreamCodec<ByteBuf, Request> CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, Request::rx,
                ByteBufCodecs.VAR_INT, Request::rz,
                ByteBufCodecs.VAR_LONG, Request::stamp,
                Request::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** Server to client: tile (rx, rz) of {@code dimension}, deflated colours, at version {@code stamp}. */
    public record Tile(String dimension, int rx, int rz, long stamp, byte[] data) implements CustomPacketPayload {
        public static final Type<Tile> TYPE = new Type<>(SkyRpgCore.id("map_tile"));
        public static final StreamCodec<ByteBuf, Tile> CODEC = StreamCodec.composite(
                ByteBufCodecs.STRING_UTF8, Tile::dimension,
                ByteBufCodecs.VAR_INT, Tile::rx,
                ByteBufCodecs.VAR_INT, Tile::rz,
                ByteBufCodecs.VAR_LONG, Tile::stamp,
                ByteBufCodecs.byteArray(TILE * TILE * 4 + 1024), Tile::data,
                Tile::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }
}
