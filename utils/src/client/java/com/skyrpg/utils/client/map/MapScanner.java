package com.skyrpg.utils.client.map;

import com.skyrpg.core.client.map.MapTints;
import java.util.Iterator;
import java.util.LinkedHashSet;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.BiomeColors;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.MapColor;

/**
 * Turns loaded chunks into map pixels: the top block's colour (its texture's, tinted like in game, see
 * {@link TextureColors}; vanilla's map colour if that can't be read), darker water the deeper it is, and slopes shaded like a paper map (lit facing north, shadowed
 * facing south). Chunks are queued as they load and a few are drawn each tick; the ones around you are
 * redrawn every second so the map keeps up with building and digging. In dimensions with a ceiling (the
 * Nether) it maps the first floor below y 100 instead of the roof.
 */
public final class MapScanner {
    private static final int CHUNKS_PER_TICK = 6;
    private static final int REFRESH_TICKS = 20;
    private static final int CEILING_START_Y = 100;
    /** Shade for a column higher than, level with and lower than the one north of it. */
    private static final float UP = 1.0f;
    private static final float LEVEL = 0.88f;
    private static final float DOWN = 0.74f;

    private static final LinkedHashSet<Long> QUEUE = new LinkedHashSet<>();
    private static int refresh;
    /** Queue every loaded chunk around the player on the next tick (after joining or changing dimension). */
    private static boolean queueLoaded;

    private MapScanner() {
    }

    public static void queue(ChunkPos pos) {
        QUEUE.add(pos.pack());
    }

    /** Forgets the queue, and queues whatever is loaded once the player is in the new level. */
    public static void clear() {
        QUEUE.clear();
        queueLoaded = true;
    }

    public static void tick(Minecraft minecraft) {
        ClientLevel level = minecraft.level;
        if (level == null || minecraft.player == null || !MapCache.active()) {
            return;
        }
        if (queueLoaded) {
            queueLoaded = false;
            ChunkPos center = minecraft.player.chunkPosition();
            int radius = minecraft.options.getEffectiveRenderDistance();
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    QUEUE.add(ChunkPos.pack(center.x() + dx, center.z() + dz));
                }
            }
        }
        if (++refresh >= REFRESH_TICKS) {
            refresh = 0;
            ChunkPos center = minecraft.player.chunkPosition();
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    QUEUE.add(ChunkPos.pack(center.x() + dx, center.z() + dz));
                }
            }
        }
        Iterator<Long> it = QUEUE.iterator();
        for (int done = 0; done < CHUNKS_PER_TICK && it.hasNext(); done++) {
            long packed = it.next();
            it.remove();
            LevelChunk chunk = level.getChunkSource().getChunk(ChunkPos.getX(packed), ChunkPos.getZ(packed), ChunkStatus.FULL, false);
            if (chunk != null) {
                scan(level, chunk);
            }
        }
        MapCache.tick();
    }

    private static void scan(ClientLevel level, LevelChunk chunk) {
        ChunkPos pos = chunk.getPos();
        LevelChunk north = level.getChunkSource().getChunk(pos.x(), pos.z() - 1, ChunkStatus.FULL, false);
        boolean ceiling = level.dimensionType().hasCeiling();
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        int[] heights = new int[16];
        // Heights of the row just north of this chunk, for shading its first row.
        for (int x = 0; x < 16; x++) {
            heights[x] = north == null ? Integer.MIN_VALUE : floorY(level, north, x, 15, ceiling, cursor);
        }
        int baseX = pos.getMinBlockX();
        int baseZ = pos.getMinBlockZ();
        for (int z = 0; z < 16; z++) {
            for (int x = 0; x < 16; x++) {
                int y = floorY(level, chunk, x, z, ceiling, cursor);
                int northY = heights[x] == Integer.MIN_VALUE ? y : heights[x];
                heights[x] = y;
                MapCache.set(baseX + x, baseZ + z, MapTints.apply(level.dimension().identifier(), baseX + x, baseZ + z,
                        color(level, chunk, baseX + x, y, baseZ + z, northY, cursor)));
            }
        }
    }

    /** The y of the block the map shows in this column (below level.getMinY() if there is none). */
    private static int floorY(ClientLevel level, LevelChunk chunk, int x, int z, boolean ceiling, BlockPos.MutableBlockPos cursor) {
        int min = level.getMinY();
        int y = chunk.getHeight(Heightmap.Types.WORLD_SURFACE, x, z);
        int bx = chunk.getPos().getMinBlockX() + x;
        int bz = chunk.getPos().getMinBlockZ() + z;
        if (ceiling) {
            // Under a roof: from y 100 go down to the first air, then to the first block under it.
            y = Math.min(y, CEILING_START_Y);
            while (y > min && !chunk.getBlockState(cursor.set(bx, y, bz)).isAir()) {
                y--;
            }
            while (y > min && chunk.getBlockState(cursor.set(bx, y, bz)).isAir()) {
                y--;
            }
            return y;
        }
        // Skip blocks maps don't show (glass, barriers...), a few at most.
        for (int i = 0; i < 16 && y > min; i++) {
            BlockState state = chunk.getBlockState(cursor.set(bx, y, bz));
            if (!state.isAir() && state.getMapColor(level, cursor) != MapColor.NONE) {
                return y;
            }
            y--;
        }
        return y;
    }

    private static int color(ClientLevel level, LevelChunk chunk, int x, int y, int z, int northY, BlockPos.MutableBlockPos cursor) {
        if (y < level.getMinY()) {
            return 0;
        }
        BlockState state = chunk.getBlockState(cursor.set(x, y, z));
        if (state.isAir()) {
            return 0;
        }
        if (state.getFluidState().is(FluidTags.WATER)) {
            int depth = 1;
            while (depth < 12 && chunk.getBlockState(cursor.set(x, y - depth, z)).getFluidState().is(FluidTags.WATER)) {
                depth++;
            }
            cursor.set(x, y, z);
            int water = scale(BiomeColors.getAverageWaterColor(level, cursor), 0.85f);
            // Shallow water shows lighter; checkerboard the in-between depths like a paper map.
            float shade = depth <= 2 ? 1.0f : depth <= 5 ? ((x + z & 1) == 0 ? 0.9f : 0.84f) : 0.72f;
            return 0xFF000000 | scale(water, shade);
        }
        MapColor mapColor = state.getMapColor(level, cursor);
        if (mapColor == MapColor.NONE) {
            return 0;
        }
        // The block's own texture colour where we can read it, else its map colour.
        int rgb = TextureColors.color(level, state, cursor);
        if (rgb < 0) {
            rgb = tinted(level, state, mapColor, cursor);
        }
        float shade = y > northY ? UP : y < northY ? DOWN : LEVEL;
        return 0xFF000000 | scale(rgb, shade);
    }

    /** The map colour, or the biome's colour for grass, foliage and the like. */
    private static int tinted(ClientLevel level, BlockState state, MapColor mapColor, BlockPos pos) {
        // Biome tints are for grey textures, so they're darkened to look like the blocks in game.
        if (mapColor == MapColor.GRASS) {
            return scale(BiomeColors.getAverageGrassColor(level, pos), 0.7f);
        }
        if (mapColor == MapColor.PLANT) {
            if (state.is(Blocks.BIRCH_LEAVES)) {
                return scale(0x80A755, 0.72f);
            }
            if (state.is(Blocks.SPRUCE_LEAVES)) {
                return scale(0x619961, 0.72f);
            }
            if (state.is(BlockTags.LEAVES)) {
                return scale(BiomeColors.getAverageFoliageColor(level, pos), 0.6f);
            }
            return scale(BiomeColors.getAverageGrassColor(level, pos), 0.66f);
        }
        return mapColor.col;
    }

    static int scale(int rgb, float f) {
        int r = Math.min(255, Math.round(((rgb >> 16) & 0xFF) * f));
        int g = Math.min(255, Math.round(((rgb >> 8) & 0xFF) * f));
        int b = Math.min(255, Math.round((rgb & 0xFF) * f));
        return (r << 16) | (g << 8) | b;
    }
}
