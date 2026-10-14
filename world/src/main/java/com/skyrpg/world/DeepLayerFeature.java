package com.skyrpg.world;

import com.mojang.serialization.MapCodec;
import it.unimi.dsi.fastutil.longs.LongSet;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.MultifaceBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.structure.Structure;

/**
 * Restyles the overworld's caves into the deep layers as chunks generate ({@link DeepLayers}; added to every overworld
 * biome at the underground-decoration step). Only cave surfaces change: solid blocks touching cave air, inside a
 * layer's height band, that the layer may replace (stone and deepslate by default). A 3D noise keeps it patchy (the
 * layer's {@code coverage}, about 60%), the bands' edges wobble over about 6 blocks, and vanilla cave biomes (lush
 * caves, dripstone caves, the deep dark) and the last few blocks under the ground are left alone. Then the layer's
 * decorations are scattered over the surfaces it found: clusters, plants, lichen, pillars, hanging lanterns, giant
 * glowcaps, lava pools.
 *
 * <p>Cost: one pass over the chunk's blocks in the bands, read straight from the chunk sections (sections without any
 * air are skipped whole), plus a one-block border from the four neighbours so walls on a chunk edge count too. Only
 * air-adjacent blocks get any further work; restyled blocks are written straight into the section, like vanilla ores.
 */
public record DeepLayerFeature() implements Feature {
    public static final MapCodec<DeepLayerFeature> CODEC = MapCodec.unit(new DeepLayerFeature());
    /** How far past a band the wobbling edges can reach (noise up to about ±3, plus a block of dither). */
    private static final int MARGIN = 5;
    /** Blocks this close under the ground (the worldgen heightmap) are not caves. */
    private static final int BELOW_SURFACE = 3;
    /** Spread of the patch noise around 0.5 (measured), for turning a coverage into a threshold. */
    private static final double NOISE_SPREAD = 0.145;
    private static final long PATCH = 0x6A09E667F3BCC908L;
    private static final long PATCH_FINE = 0xBB67AE8584CAA73BL;
    private static final long JITTER = 0x3C6EF372FE94F82BL;
    private static final long DITHER = 0xA54FF53A5F1D36F1L;
    private static final long CELL = 0x510E527FADE682D1L;
    private static final long MATERIAL = 0x9B05688C2B3E6C1FL;
    private static final Direction[] HORIZONTAL = {Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST};
    private static final int SURFACES = DeepLayers.Surface.values().length;

    @Override
    public MapCodec<DeepLayerFeature> codec() {
        return CODEC;
    }

    @Override
    public boolean place(WorldGenLevel level, ChunkGenerator generator, RandomSource random, BlockPos origin) {
        if (level.getLevel().dimension() != Level.OVERWORLD) {
            return false;
        }
        List<DeepLayers.Layer> layers = DeepLayers.layers();
        if (layers.isEmpty()) {
            return false;
        }
        int lo = Math.max(level.getMinY(), DeepLayers.lowestY() - MARGIN);
        int hi = Math.min(level.getMaxY(), DeepLayers.highestY() + MARGIN);
        if (lo > hi) {
            return false;
        }
        ChunkPos chunkPos = ChunkPos.containing(origin);
        int minX = chunkPos.getMinBlockX();
        int minZ = chunkPos.getMinBlockZ();
        int cx = minX >> 4;
        int cz = minZ >> 4;
        ChunkAccess chunk = level.getChunk(cx, cz);
        long seed = level.getSeed();

        // 1. Which blocks are air: this chunk, plus the facing column of each neighbour. The mask is 18 x 18 and runs
        //    from one below the band to one above it.
        int height = hi - lo + 1;
        int maskHeight = height + 2;
        boolean[] air = new boolean[18 * 18 * maskHeight];
        if (!fillAir(chunk, air, lo - 1, maskHeight, 0, 15, 0, 15, 1, 1)) {
            return false; // no air anywhere in the bands: no caves
        }
        if (level.hasChunk(cx - 1, cz)) {
            fillAir(level.getChunk(cx - 1, cz), air, lo - 1, maskHeight, 15, 15, 0, 15, -15, 1);
        }
        if (level.hasChunk(cx + 1, cz)) {
            fillAir(level.getChunk(cx + 1, cz), air, lo - 1, maskHeight, 0, 0, 0, 15, 17, 1);
        }
        if (level.hasChunk(cx, cz - 1)) {
            fillAir(level.getChunk(cx, cz - 1), air, lo - 1, maskHeight, 0, 15, 15, 15, 1, -15);
        }
        if (level.hasChunk(cx, cz + 1)) {
            fillAir(level.getChunk(cx, cz + 1), air, lo - 1, maskHeight, 0, 15, 0, 0, 1, 17);
        }

        int[] surface = new int[256];
        for (int lz = 0; lz < 16; lz++) {
            for (int lx = 0; lx < 16; lx++) {
                surface[lz * 16 + lx] = chunk.getHeight(Heightmap.Types.OCEAN_FLOOR_WG, minX + lx, minZ + lz) - BELOW_SURFACE;
            }
        }
        boolean[] nearEdge = new boolean[height];
        for (DeepLayers.Layer layer : layers) {
            for (int edge : new int[] {layer.minY(), layer.maxY()}) {
                for (int y = edge - MARGIN; y <= edge + MARGIN; y++) {
                    if (y >= lo && y <= hi) {
                        nearEdge[y - lo] = true;
                    }
                }
            }
        }
        int quartLo = lo >> 2;
        byte[] biomeSkip = new byte[((hi >> 2) - quartLo + 1) * 16];

        // 2. Every solid block touching air: restyle it (patchily) and remember it for decorations.
        int[][] candidates = new int[layers.size() * SURFACES][];
        int[] sizes = new int[layers.size() * SURFACES];
        boolean changed = false;
        for (int ly = 0; ly < height; ly++) {
            int y = lo + ly;
            LevelChunkSection section = null;
            for (int lz = 0; lz < 16; lz++) {
                for (int lx = 0; lx < 16; lx++) {
                    int i = ((ly + 1) * 18 + lz + 1) * 18 + lx + 1;
                    if (air[i]) {
                        continue;
                    }
                    boolean up = air[i + 324];
                    boolean down = air[i - 324];
                    int side = -1;
                    if (air[i - 18]) {
                        side = 0;
                    } else if (air[i + 18]) {
                        side = 1;
                    } else if (air[i - 1]) {
                        side = 2;
                    } else if (air[i + 1]) {
                        side = 3;
                    }
                    if (!up && !down && side < 0) {
                        continue;
                    }
                    if (y > surface[lz * 16 + lx]) {
                        continue;
                    }
                    int x = minX + lx;
                    int z = minZ + lz;
                    int layerIndex = layerFor(layers, seed, x, y, z, nearEdge[ly]);
                    if (layerIndex < 0) {
                        continue;
                    }
                    int biome = ((y >> 2) - quartLo) * 16 + (lz >> 2) * 4 + (lx >> 2);
                    if (biomeSkip[biome] == 0) {
                        biomeSkip[biome] = (byte) (caveBiome(chunk.getNoiseBiome(QuartPos.fromBlock(x), QuartPos.fromBlock(y), QuartPos.fromBlock(z))) ? 2 : 1);
                    }
                    if (biomeSkip[biome] == 2) {
                        continue;
                    }
                    if (section == null) {
                        section = chunk.getSection(chunk.getSectionIndex(y));
                    }
                    BlockState state = section.getBlockState(lx, y & 15, lz);
                    DeepLayers.Layer layer = layers.get(layerIndex);
                    if (!layer.replaceable(state)) {
                        continue;
                    }
                    DeepLayers.Surface kind = up ? DeepLayers.Surface.FLOOR : down ? DeepLayers.Surface.CEILING : DeepLayers.Surface.WALL;
                    int list = layerIndex * SURFACES + kind.ordinal();
                    int[] array = candidates[list];
                    if (array == null) {
                        array = candidates[list] = new int[64];
                    } else if (sizes[list] == array.length) {
                        array = candidates[list] = Arrays.copyOf(array, array.length * 2);
                    }
                    array[sizes[list]++] = lx | lz << 4 | ly << 8 | Math.max(0, side) << 20;
                    if (patch(seed, x, y, z) >= layer.threshold()) {
                        continue;
                    }
                    BlockState to = layer.material(kind, materialHash(seed, x, y, z));
                    if (to != null && to != state) {
                        section.setBlockState(lx, y & 15, lz, to);
                        changed = true;
                    }
                }
            }
        }

        // 3. Decorations on the surfaces found, unless a structure (other than a mineshaft) reaches into this chunk.
        if (nearStructure(level, chunk)) {
            return changed;
        }
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int layerIndex = 0; layerIndex < layers.size(); layerIndex++) {
            for (DeepLayers.Decoration decoration : layers.get(layerIndex).decorations()) {
                int list = layerIndex * SURFACES + decoration.on().ordinal();
                int size = sizes[list];
                if (size == 0 || random.nextDouble() >= decoration.chance()) {
                    continue;
                }
                int count = decoration.max() > 0 ? decoration.min() + random.nextInt(decoration.max() - decoration.min() + 1)
                        : (int) (decoration.density() * size / 100.0 + random.nextDouble());
                count = Math.min(count, size);
                for (int n = 0; n < count; n++) {
                    int packed = candidates[list][random.nextInt(size)];
                    cursor.set(minX + (packed & 15), lo + (packed >> 8 & 0xFFF), minZ + (packed >> 4 & 15));
                    Direction normal = switch (decoration.on()) {
                        case FLOOR -> Direction.UP;
                        case CEILING -> Direction.DOWN;
                        case WALL -> HORIZONTAL[packed >> 20 & 3];
                    };
                    changed |= decorate(level, decoration, cursor.immutable(), normal, random, minX, minZ);
                }
            }
        }
        return changed;
    }

    /**
     * Marks the air blocks of one chunk's columns {@code [lx0, lx1] x [lz0, lz1]} in the mask (mask x = lx + offX,
     * mask z = lz + offZ). Returns whether any section in range had air.
     */
    private static boolean fillAir(ChunkAccess chunk, boolean[] air, int yStart, int maskHeight, int lx0, int lx1, int lz0, int lz1, int offX, int offZ) {
        int yEnd = yStart + maskHeight - 1;
        boolean any = false;
        int y = Math.max(yStart, chunk.getMinY());
        int top = Math.min(yEnd, chunk.getMaxY());
        while (y <= top) {
            LevelChunkSection section = chunk.getSection(chunk.getSectionIndex(y));
            int sectionTop = Math.min(top, (y & ~15) + 15);
            boolean all = section.hasOnlyAir();
            if (all || section.maybeHas(BlockState::isAir)) {
                any = true;
                for (int yy = y; yy <= sectionTop; yy++) {
                    int row = (yy - yStart) * 18;
                    for (int lz = lz0; lz <= lz1; lz++) {
                        int base = (row + lz + offZ) * 18 + offX;
                        for (int lx = lx0; lx <= lx1; lx++) {
                            air[base + lx] = all || section.getBlockState(lx, yy & 15, lz).isAir();
                        }
                    }
                }
            }
            y = sectionTop + 1;
        }
        return any;
    }

    /** Which layer a block belongs to (its height, wobbled near band edges), as an index into the list, or -1. */
    private static int layerFor(List<DeepLayers.Layer> layers, long seed, int x, int y, int z, boolean nearEdge) {
        int height = y;
        if (nearEdge) {
            double wobble = (noise(seed ^ JITTER, x / 9.0, y / 6.0, z / 9.0) - 0.5) * 10 + unit(hash(seed ^ DITHER, x, y, z)) * 2 - 1;
            height = (int) Math.floor(y + wobble);
        }
        for (int i = 0; i < layers.size(); i++) {
            if (layers.get(i).contains(height)) {
                return i;
            }
        }
        return -1;
    }

    private static boolean caveBiome(Holder<Biome> biome) {
        return biome.is(Biomes.LUSH_CAVES) || biome.is(Biomes.DRIPSTONE_CAVES) || biome.is(Biomes.DEEP_DARK);
    }

    /** Whether a structure other than a mineshaft has pieces in this chunk (decorations would land inside it). */
    private static boolean nearStructure(WorldGenLevel level, ChunkAccess chunk) {
        Map<Structure, LongSet> references = chunk.getAllReferences();
        if (references.isEmpty()) {
            return false;
        }
        Registry<Structure> registry = level.registryAccess().lookupOrThrow(Registries.STRUCTURE);
        for (Map.Entry<Structure, LongSet> entry : references.entrySet()) {
            if (entry.getValue().isEmpty()) {
                continue;
            }
            Identifier id = registry.getKey(entry.getKey());
            if (id == null || !id.getPath().startsWith("mineshaft")) {
                return true;
            }
        }
        return false;
    }

    // ---------------------------------------------------------------- decorations

    private static boolean decorate(WorldGenLevel level, DeepLayers.Decoration decoration, BlockPos surface, Direction normal, RandomSource random,
                                    int minX, int minZ) {
        return switch (decoration.type()) {
            case "replace" -> {
                level.setBlock(surface, decoration.block(), Block.UPDATE_CLIENTS);
                yield true;
            }
            case "column" -> column(level, decoration, surface, normal, random, minX, minZ);
            case "hanging" -> hanging(level, decoration, surface, random, minX, minZ);
            case "giant_mushroom" -> mushroom(level, decoration, surface, random, minX, minZ);
            case "pool" -> pool(level, decoration, surface, random, minX, minZ);
            default -> {
                BlockPos target = surface.relative(normal);
                if (!inChunk(target, minX, minZ) || !level.getBlockState(target).isAir()) {
                    yield false;
                }
                BlockState state = orient(decoration.block(), normal);
                if (!state.canSurvive(level, target)) {
                    yield false;
                }
                level.setBlock(target, state, Block.UPDATE_CLIENTS);
                yield true;
            }
        };
    }

    /** Turns a decoration to face away from its surface: clusters point out, lanterns hang, lichen clings. */
    private static BlockState orient(BlockState state, Direction normal) {
        if (state.hasProperty(BlockStateProperties.FACING)) {
            state = state.setValue(BlockStateProperties.FACING, normal);
        }
        if (state.hasProperty(BlockStateProperties.HANGING)) {
            state = state.setValue(BlockStateProperties.HANGING, normal == Direction.DOWN);
        }
        if (state.hasProperty(BlockStateProperties.VERTICAL_DIRECTION) && normal.getAxis() == Direction.Axis.Y) {
            state = state.setValue(BlockStateProperties.VERTICAL_DIRECTION, normal);
        }
        if (state.getBlock() instanceof MultifaceBlock) {
            state = state.setValue(MultifaceBlock.getFaceProperty(normal.getOpposite()), true);
        }
        return state;
    }

    /** A pillar up from a floor (or a spike down from a ceiling), cut short by whatever is in the way; {@code tip} on its end. */
    private static boolean column(WorldGenLevel level, DeepLayers.Decoration decoration, BlockPos surface, Direction normal, RandomSource random,
                                  int minX, int minZ) {
        int height = between(random, decoration.heightMin(), decoration.heightMax());
        int length = 0;
        BlockPos.MutableBlockPos cursor = surface.mutable();
        while (length < height) {
            cursor.move(normal);
            if (!inChunk(cursor, minX, minZ) || !level.getBlockState(cursor).isAir()) {
                break;
            }
            length++;
        }
        if (length == 0) {
            return false;
        }
        cursor.set(surface);
        for (int i = 1; i <= length; i++) {
            cursor.move(normal);
            BlockState state = i == length && decoration.alt() != null ? decoration.alt() : decoration.block();
            level.setBlock(cursor, orient(state, normal), Block.UPDATE_CLIENTS);
        }
        return true;
    }

    /** A chain down from the ceiling with the block (a lantern) hanging on its end; needs headroom under it. */
    private static boolean hanging(WorldGenLevel level, DeepLayers.Decoration decoration, BlockPos ceiling, RandomSource random, int minX, int minZ) {
        int chain = decoration.alt() == null ? 0 : between(random, decoration.heightMin(), decoration.heightMax());
        for (int i = 1; i <= chain + 3; i++) {
            BlockPos pos = ceiling.below(i);
            if (!inChunk(pos, minX, minZ) || !level.getBlockState(pos).isAir()) {
                return false;
            }
        }
        for (int i = 1; i <= chain; i++) {
            level.setBlock(ceiling.below(i), decoration.alt(), Block.UPDATE_CLIENTS);
        }
        level.setBlock(ceiling.below(chain + 1), orient(decoration.block(), Direction.DOWN), Block.UPDATE_CLIENTS);
        return true;
    }

    /** A giant mushroom: a stem ({@code stem}) and a domed cap of the block, only where there's room for it. */
    private static boolean mushroom(WorldGenLevel level, DeepLayers.Decoration decoration, BlockPos floor, RandomSource random, int minX, int minZ) {
        int height = between(random, decoration.heightMin(), decoration.heightMax());
        int radius = between(random, decoration.radiusMin(), decoration.radiusMax());
        BlockPos base = floor.above();
        if (!inChunk(base.offset(-radius, 0, -radius), minX, minZ) || !inChunk(base.offset(radius, 0, radius), minX, minZ)) {
            return false;
        }
        for (int i = 0; i <= height + 1; i++) {
            if (!level.getBlockState(base.above(i)).isAir()) {
                return false;
            }
        }
        int inner = (radius - 1) * (radius - 1) + (radius - 1);
        int outer = radius * radius + radius;
        int blocked = 0;
        int total = 0;
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                int d = dx * dx + dz * dz;
                if (d <= outer) {
                    total++;
                    if (!level.getBlockState(base.offset(dx, d <= inner ? height : height - 1, dz)).isAir()) {
                        blocked++;
                    }
                }
            }
        }
        if (blocked * 4 > total) {
            return false; // the cave is too small for it
        }
        BlockState stem = decoration.alt() == null ? decoration.block() : decoration.alt();
        for (int i = 0; i < height; i++) {
            level.setBlock(base.above(i), stem, Block.UPDATE_CLIENTS);
        }
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                int d = dx * dx + dz * dz;
                BlockPos pos = d <= inner ? base.offset(dx, height, dz) : d <= outer ? base.offset(dx, height - 1, dz) : null;
                if (pos != null && level.getBlockState(pos).isAir()) {
                    level.setBlock(pos, decoration.block(), Block.UPDATE_CLIENTS);
                }
            }
        }
        return true;
    }

    /** A pool (lava) sunk into the floor, only where solid floor holds it in on every side; {@code rim} around it. */
    private static boolean pool(WorldGenLevel level, DeepLayers.Decoration decoration, BlockPos floor, RandomSource random, int minX, int minZ) {
        int radius = between(random, decoration.radiusMin(), decoration.radiusMax());
        int size = radius * 2 + 1;
        boolean[] cells = new boolean[size * size];
        int count = 0;
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                if (dx * dx + dz * dz > radius * radius + radius) {
                    continue;
                }
                BlockPos pos = floor.offset(dx, 0, dz);
                if (!inChunk(pos, minX, minZ)) {
                    continue;
                }
                BlockState state = level.getBlockState(pos);
                BlockState below = level.getBlockState(pos.below());
                if (state.isAir() || !state.getFluidState().isEmpty() || !level.getBlockState(pos.above()).isAir() || below.isAir()
                        || !below.getFluidState().isEmpty()) {
                    continue;
                }
                cells[(dx + radius) * size + dz + radius] = true;
                count++;
            }
        }
        // Drop cells that would spill: any side that is neither solid nor pool.
        boolean dropped = true;
        while (dropped && count > 0) {
            dropped = false;
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    if (!cells[(dx + radius) * size + dz + radius]) {
                        continue;
                    }
                    for (Direction side : HORIZONTAL) {
                        int nx = dx + side.getStepX();
                        int nz = dz + side.getStepZ();
                        if (Math.abs(nx) <= radius && Math.abs(nz) <= radius && cells[(nx + radius) * size + nz + radius]) {
                            continue;
                        }
                        BlockState neighbour = level.getBlockState(floor.offset(nx, 0, nz));
                        if (neighbour.isAir() || !neighbour.getFluidState().isEmpty()) {
                            cells[(dx + radius) * size + dz + radius] = false;
                            count--;
                            dropped = true;
                            break;
                        }
                    }
                }
            }
        }
        if (count < 2) {
            return false;
        }
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                if (!cells[(dx + radius) * size + dz + radius]) {
                    continue;
                }
                level.setBlock(floor.offset(dx, 0, dz), decoration.block(), Block.UPDATE_CLIENTS);
                if (decoration.alt() == null) {
                    continue;
                }
                for (Direction side : HORIZONTAL) {
                    int nx = dx + side.getStepX();
                    int nz = dz + side.getStepZ();
                    if (Math.abs(nx) <= radius && Math.abs(nz) <= radius && cells[(nx + radius) * size + nz + radius]) {
                        continue;
                    }
                    BlockPos rim = floor.offset(nx, 0, nz);
                    if (inChunk(rim, minX, minZ) && level.getFluidState(rim).isEmpty()) {
                        level.setBlock(rim, decoration.alt(), Block.UPDATE_CLIENTS);
                    }
                }
            }
        }
        return true;
    }

    private static boolean inChunk(BlockPos pos, int minX, int minZ) {
        int x = pos.getX() - minX;
        int z = pos.getZ() - minZ;
        return x >= 0 && x < 16 && z >= 0 && z < 16;
    }

    private static int between(RandomSource random, int min, int max) {
        return min + random.nextInt(Math.max(1, max - min + 1));
    }

    // ---------------------------------------------------------------- noise

    /** Below the threshold a surface block is restyled. */
    private static double patch(long seed, int x, int y, int z) {
        return 0.65 * noise(seed ^ PATCH, x / 7.0, y / 5.0, z / 7.0) + 0.35 * noise(seed ^ PATCH_FINE, x / 3.0, y / 3.0, z / 3.0);
    }

    /** Picks a palette entry per cell of about 4 x 3 x 4 blocks, with ragged cell edges, so materials come in patches. */
    private static long materialHash(long seed, int x, int y, int z) {
        long jitter = hash(seed ^ CELL, x, y, z);
        int jx = (int) ((jitter & 3) % 3) - 1;
        int jy = (int) ((jitter >>> 2 & 3) % 3) - 1;
        int jz = (int) ((jitter >>> 4 & 3) % 3) - 1;
        return hash(seed ^ MATERIAL, Math.floorDiv(x + jx, 4), Math.floorDiv(y + jy, 3), Math.floorDiv(z + jz, 4)) >>> 1;
    }

    /** The patch-noise threshold that restyles about {@code coverage} of the surfaces (the noise is roughly normal around 0.5). */
    static double threshold(double coverage) {
        if (coverage <= 0) {
            return -1;
        }
        if (coverage >= 1) {
            return 2;
        }
        double lowZ = -6;
        double highZ = 6;
        for (int i = 0; i < 60; i++) {
            double mid = (lowZ + highZ) / 2;
            if (normalCdf(mid) < coverage) {
                lowZ = mid;
            } else {
                highZ = mid;
            }
        }
        return 0.5 + NOISE_SPREAD * (lowZ + highZ) / 2;
    }

    private static double normalCdf(double z) {
        // Abramowitz and Stegun 7.1.26, good to about 1e-7.
        double t = 1 / (1 + 0.3275911 * Math.abs(z) / Math.sqrt(2));
        double erf = 1 - t * (0.254829592 + t * (-0.284496736 + t * (1.421413741 + t * (-1.453152027 + t * 1.061405429))))
                * Math.exp(-z * z / 2);
        return z >= 0 ? (1 + erf) / 2 : (1 - erf) / 2;
    }

    static long hash(long seed, int x, int y, int z) {
        long h = seed ^ x * 0x9E3779B97F4A7C15L ^ y * 0xC2B2AE3D27D4EB4FL ^ z * 0x165667B19E3779F9L;
        h ^= h >>> 33;
        h *= 0xFF51AFD7ED558CCDL;
        h ^= h >>> 33;
        h *= 0xC4CEB9FE1A85EC53L;
        h ^= h >>> 33;
        return h;
    }

    static double unit(long hash) {
        return (hash >>> 11) * 0x1.0p-53;
    }

    /** Smooth value noise in [0, 1). */
    static double noise(long seed, double x, double y, double z) {
        int xi = (int) Math.floor(x);
        int yi = (int) Math.floor(y);
        int zi = (int) Math.floor(z);
        double fx = smooth(x - xi);
        double fy = smooth(y - yi);
        double fz = smooth(z - zi);
        double x00 = lerp(unit(hash(seed, xi, yi, zi)), unit(hash(seed, xi + 1, yi, zi)), fx);
        double x10 = lerp(unit(hash(seed, xi, yi + 1, zi)), unit(hash(seed, xi + 1, yi + 1, zi)), fx);
        double x01 = lerp(unit(hash(seed, xi, yi, zi + 1)), unit(hash(seed, xi + 1, yi, zi + 1)), fx);
        double x11 = lerp(unit(hash(seed, xi, yi + 1, zi + 1)), unit(hash(seed, xi + 1, yi + 1, zi + 1)), fx);
        return lerp(lerp(x00, x10, fy), lerp(x01, x11, fy), fz);
    }

    private static double smooth(double t) {
        return t * t * (3 - 2 * t);
    }

    private static double lerp(double a, double b, double t) {
        return a + (b - a) * t;
    }
}
