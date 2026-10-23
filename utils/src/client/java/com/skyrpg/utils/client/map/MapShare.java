package com.skyrpg.utils.client.map;

import com.skyrpg.core.net.SharedMap;
import java.nio.ByteBuffer;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.util.Util;

/**
 * Shares the map through the server ({@link SharedMap}): chunks this client maps go up a few times a second, and tiles
 * on screen are asked for now and then, so what other players explored fills in. Server pixels never overwrite chunks
 * this client mapped but hasn't sent yet. Does nothing on a server without the shared map.
 */
public final class MapShare {
    private static final long ASK_EVERY_MS = 30_000;
    private static final int REQUESTS_PER_TICK = 1;
    private static final int UPLOAD_EVERY_TICKS = 5;

    /** Chunks mapped here and not sent yet (chunk keys, as {@link MapCache#key}). */
    private static final Set<Long> UNSENT = new LinkedHashSet<>();
    /** The server version of each tile this client holds. */
    private static final Map<Long, Long> STAMPS = new HashMap<>();
    private static final Map<Long, Long> ASKED = new HashMap<>();
    private static final ArrayDeque<Long> QUEUE = new ArrayDeque<>();
    private static int ticks;

    private MapShare() {
    }

    public static void init() {
        ClientPlayNetworking.registerGlobalReceiver(SharedMap.Tile.TYPE, (payload, context) -> context.client().execute(() -> receive(payload)));
    }

    /** A block column changed colour here. */
    static void changed(int x, int z) {
        UNSENT.add(MapCache.key(x >> 4, z >> 4));
    }

    /** Tile (rx, rz) is on screen: ask the server for it if it's been a while. */
    static void seen(int rx, int rz) {
        long key = MapCache.key(rx, rz);
        long now = Util.getMillis();
        Long asked = ASKED.get(key);
        if (asked == null || now - asked > ASK_EVERY_MS) {
            ASKED.put(key, now);
            QUEUE.add(key);
        }
    }

    static void tick() {
        if (!ClientPlayNetworking.canSend(SharedMap.Request.TYPE)) {
            return;
        }
        for (int i = 0; i < REQUESTS_PER_TICK && !QUEUE.isEmpty(); i++) {
            long key = QUEUE.poll();
            ClientPlayNetworking.send(new SharedMap.Request((int) (key >> 32), (int) key, STAMPS.getOrDefault(key, 0L)));
        }
        if (++ticks % UPLOAD_EVERY_TICKS == 0 && !UNSENT.isEmpty()) {
            upload();
        }
    }

    /** Sends everything not sent yet (before leaving a dimension or server). */
    static void flush() {
        if (MapCache.active() && ClientPlayNetworking.canSend(SharedMap.Upload.TYPE)) {
            while (!UNSENT.isEmpty()) {
                upload();
            }
        }
        UNSENT.clear();
        STAMPS.clear();
        ASKED.clear();
        QUEUE.clear();
    }

    private static void upload() {
        int count = Math.min(SharedMap.CHUNKS_PER_UPLOAD, UNSENT.size());
        int[] chunks = new int[count * 2];
        ByteBuffer colors = ByteBuffer.allocate(count * SharedMap.CHUNK_BYTES);
        Iterator<Long> it = UNSENT.iterator();
        for (int i = 0; i < count; i++) {
            long key = it.next();
            it.remove();
            int cx = (int) (key >> 32);
            int cz = (int) key;
            chunks[i * 2] = cx;
            chunks[i * 2 + 1] = cz;
            MapTile tile = MapCache.tile(Math.floorDiv(cx * 16, MapTile.SIZE), Math.floorDiv(cz * 16, MapTile.SIZE), true);
            int baseX = Math.floorMod(cx * 16, MapTile.SIZE);
            int baseZ = Math.floorMod(cz * 16, MapTile.SIZE);
            for (int z = 0; z < 16; z++) {
                for (int x = 0; x < 16; x++) {
                    colors.putInt(tile == null ? 0 : tile.image.getPixel(baseX + x, baseZ + z));
                }
            }
        }
        ClientPlayNetworking.send(new SharedMap.Upload(chunks, colors.array()));
    }

    private static void receive(SharedMap.Tile payload) {
        if (!MapCache.active() || !MapCache.dimension().identifier().toString().equals(payload.dimension())) {
            return;
        }
        int[] pixels = SharedMap.inflate(payload.data());
        if (pixels == null) {
            return;
        }
        STAMPS.put(MapCache.key(payload.rx(), payload.rz()), payload.stamp());
        MapTile tile = MapCache.tile(payload.rx(), payload.rz(), true);
        int chunkX = payload.rx() * MapTile.SIZE / 16;
        int chunkZ = payload.rz() * MapTile.SIZE / 16;
        for (int z = 0; z < MapTile.SIZE; z++) {
            for (int x = 0; x < MapTile.SIZE; x++) {
                int color = pixels[z * MapTile.SIZE + x];
                if (color != 0 && !UNSENT.contains(MapCache.key(chunkX + (x >> 4), chunkZ + (z >> 4)))) {
                    tile.set(x, z, color);
                }
            }
        }
    }
}
