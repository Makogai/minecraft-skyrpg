package com.skyrpg.utils.client.map;

import com.mojang.blaze3d.platform.NativeImage;
import com.skyrpg.utils.client.SkyRpgUtilsClient;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.Util;
import net.minecraft.world.level.Level;

/**
 * The map of the dimension you're in, as {@link MapTile}s: made as chunks get scanned, read from disk when
 * a tile you've mapped before comes into view, and saved under {@code skyrpg/maps/<server>/<dimension>/}
 * in the game folder. Only the most recently used tiles stay in memory. {@link MapShare} trades tiles with the server.
 */
public final class MapCache {
    private static final int DEFAULT_PORT = 25565;
    private static final int MAX_TILES = 256;
    private static final long SAVE_EVERY_MS = 60_000;

    private static final Map<Long, MapTile> TILES = new HashMap<>();
    /** Tiles known to have no file, so they aren't looked for every frame. */
    private static final Set<Long> MISSING = new HashSet<>();
    private static String world;
    private static ResourceKey<Level> dimension;
    private static long lastSave = Util.getMillis();

    private MapCache() {
    }

    /** Switches to another server or dimension: saves and drops what's loaded. */
    public static void use(String worldKey, ResourceKey<Level> dim) {
        if (worldKey.equals(world) && dim.equals(dimension)) {
            return;
        }
        clear();
        world = worldKey;
        dimension = dim;
        Waypoints.use(folder());
        TextureColors.clear();
    }

    /** Saves and drops everything (leaving a world). */
    public static void clear() {
        MapShare.flush();
        saveAll();
        for (MapTile tile : TILES.values()) {
            Minecraft.getInstance().getTextureManager().release(tile.id);
        }
        TILES.clear();
        MISSING.clear();
        world = null;
        dimension = null;
        Waypoints.use(null);
    }

    public static boolean active() {
        return world != null && dimension != null;
    }

    public static ResourceKey<Level> dimension() {
        return dimension;
    }

    static long key(int rx, int rz) {
        return ((long) rx << 32) | (rz & 0xFFFFFFFFL);
    }

    /** The tile at (rx, rz) if it's loaded or on disk, else null (or a new empty one with {@code create}). */
    static MapTile tile(int rx, int rz, boolean create) {
        if (!active()) {
            return null;
        }
        long key = key(rx, rz);
        MapTile tile = TILES.get(key);
        if (tile == null && !MISSING.contains(key)) {
            tile = load(rx, rz);
            if (tile == null) {
                MISSING.add(key);
            }
        }
        if (tile == null && create) {
            MISSING.remove(key);
            tile = add(rx, rz, new NativeImage(MapTile.SIZE, MapTile.SIZE, true));
        }
        if (tile != null) {
            tile.lastUsed = Util.getMillis();
        }
        if (!create) {
            MapShare.seen(rx, rz);
        }
        return tile;
    }

    /** Sets one block column's colour (ARGB, 0 = unmapped). */
    static void set(int x, int z, int argb) {
        MapTile tile = tile(Math.floorDiv(x, MapTile.SIZE), Math.floorDiv(z, MapTile.SIZE), true);
        if (tile != null && tile.set(Math.floorMod(x, MapTile.SIZE), Math.floorMod(z, MapTile.SIZE), argb)) {
            MapShare.changed(x, z);
        }
    }

    /** Once a tick: sends changed tiles to the GPU, saves now and then. */
    static void tick() {
        MapShare.tick();
        for (MapTile tile : TILES.values()) {
            if (tile.dirty) {
                tile.texture.upload();
                tile.dirty = false;
            }
        }
        if (Util.getMillis() - lastSave > SAVE_EVERY_MS) {
            saveAll();
        }
    }

    private static MapTile add(int rx, int rz, NativeImage image) {
        if (TILES.size() >= MAX_TILES) {
            evict();
        }
        Identifier id = SkyRpgUtilsClient.id("map/" + Integer.toString(rx).replace('-', 'm') + "_" + Integer.toString(rz).replace('-', 'm'));
        // A tile from an earlier dimension or server may still hold this name.
        Minecraft.getInstance().getTextureManager().release(id);
        MapTile tile = new MapTile(rx, rz, id, image);
        Minecraft.getInstance().getTextureManager().register(id, tile.texture);
        TILES.put(key(rx, rz), tile);
        return tile;
    }

    /** Drops the least recently used quarter of the tiles, saving them first. */
    private static void evict() {
        List<MapTile> oldest = new ArrayList<>(TILES.values());
        oldest.sort(Comparator.comparingLong(tile -> tile.lastUsed));
        for (MapTile tile : oldest.subList(0, oldest.size() / 4)) {
            save(tile);
            Minecraft.getInstance().getTextureManager().release(tile.id);
            TILES.remove(key(tile.rx, tile.rz));
        }
    }

    private static MapTile load(int rx, int rz) {
        Path file = file(rx, rz);
        if (file == null || !Files.isRegularFile(file)) {
            return null;
        }
        try (InputStream in = Files.newInputStream(file)) {
            NativeImage image = NativeImage.read(in);
            if (image.getWidth() != MapTile.SIZE || image.getHeight() != MapTile.SIZE) {
                image.close();
                return null;
            }
            return add(rx, rz, image);
        } catch (IOException | RuntimeException e) {
            SkyRpgUtilsClient.LOGGER.warn("Couldn't read map tile {}", file, e);
            return null;
        }
    }

    public static void saveAll() {
        lastSave = Util.getMillis();
        for (MapTile tile : TILES.values()) {
            save(tile);
        }
    }

    private static void save(MapTile tile) {
        if (!tile.unsaved) {
            return;
        }
        Path file = file(tile.rx, tile.rz);
        if (file == null) {
            return;
        }
        try {
            Files.createDirectories(file.getParent());
            Path temp = file.resolveSibling(file.getFileName() + ".tmp");
            tile.image.writeToFile(temp);
            Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            tile.unsaved = false;
        } catch (IOException | RuntimeException e) {
            SkyRpgUtilsClient.LOGGER.warn("Couldn't save map tile {}", file, e);
        }
    }

    private static Path file(int rx, int rz) {
        Path folder = folder();
        return folder == null ? null : folder.resolve(rx + "_" + rz + ".png");
    }

    /** Where this server's and dimension's map lives, or null when not in a world. */
    static Path folder() {
        if (!active()) {
            return null;
        }
        Identifier dim = dimension.identifier();
        return FabricLoader.getInstance().getGameDir().resolve("skyrpg").resolve("maps").resolve(world)
                .resolve(dim.getNamespace()).resolve(dim.getPath());
    }

    /** A folder-safe name for the server (or singleplayer world) you're on. */
    public static String worldKey(Minecraft minecraft) {
        String name;
        if (minecraft.getSingleplayerServer() != null) {
            name = "sp_" + minecraft.getSingleplayerServer().getWorldData().getLevelName();
        } else if (minecraft.getConnection() != null && minecraft.getConnection().getServerData() != null) {
            // The connection's own server entry: getCurrentServer() can be unset for a moment while joining or changing
            // dimension, which used to file waypoints under "unknown" so they seemed to vanish on the next join.
            name = minecraft.getConnection().getServerData().ip;
        } else if (minecraft.getCurrentServer() != null) {
            name = minecraft.getCurrentServer().ip;
        } else if (minecraft.getConnection() != null && minecraft.getConnection().getConnection().getRemoteAddress() instanceof InetSocketAddress address) {
            name = address.getHostString() + (address.getPort() == DEFAULT_PORT ? "" : ":" + address.getPort());
        } else {
            name = "unknown";
        }
        // "host" and "host:25565" are the same server (the Play button and a Multiplayer entry can spell it either way).
        name = name.trim().toLowerCase(Locale.ROOT);
        if (name.endsWith(":" + DEFAULT_PORT)) {
            name = name.substring(0, name.length() - (":" + DEFAULT_PORT).length());
        }
        return name.replaceAll("[^a-z0-9._-]", "_");
    }
}
