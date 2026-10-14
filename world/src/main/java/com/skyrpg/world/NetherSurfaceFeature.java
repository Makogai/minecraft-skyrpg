package com.skyrpg.world;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.feature.Feature;

/**
 * The ground of the three Nether biomes ({@link NetherRealm}), painted over the netherrack as a chunk generates (the
 * raw-generation step, so structures and every other feature come after it). Only netherrack touching air changes, plus
 * a little under the floors, and only where the block's biome is one of the three, so the borders follow the biomes:
 * <ul>
 *   <li><b>Ember Forest:</b> floors of Ember Ash (glowing specks, smouldering), with magma patches and bare netherrack.</li>
 *   <li><b>Bone Sea:</b> floors three deep in soul soil and soul sand (in blobs), heaped into rolling dunes up to five blocks
 *   high where there's headroom, bone blocks poking out; walls and ceilings patchy soul soil.</li>
 *   <li><b>Obsidian Spires:</b> every surface blackstone (the flats), obsidian specks, and Void Cracks: thin wandering lines
 *   of a glowing block across the floors, two deep.</li>
 * </ul>
 * The spires, fossils and trees are separate features. Cost: one pass over the chunk's columns with an air mask (and a
 * one-block border from the neighbours), a biome lookup only for surface blocks.
 */
public record NetherSurfaceFeature() implements Feature {
    public static final MapCodec<NetherSurfaceFeature> CODEC = MapCodec.unit(new NetherSurfaceFeature());
    private static final long ASH = 0x2545F4914F6CDD1DL;
    private static final long SOUL = 0x9E3779B97F4A7C15L;
    private static final long WALL = 0xBF58476D1CE4E5B9L;
    private static final long DUNE = 0x94D049BB133111EBL;
    private static final long DUNE_FINE = 0xD6E8FEB86659FD93L;
    private static final long BONE = 0xA0761D6478BD642FL;
    private static final long CRACK = 0xE7037ED1A0B428DBL;
    private static final long CRACK_FINE = 0x8EBC6AF09C88C6E3L;
    private static final long SPECK = 0x589965CC75374CC3L;
    /** How close to the noise's middle a floor block must be to be part of a void crack (about 8% of the floor). */
    private static final double CRACK_WIDTH = 0.02;
    private static final int MAX_DUNE = 5;
    /** Free air a dune leaves above itself, so it never plugs a low cave. */
    private static final int DUNE_HEADROOM = 3;

    @Override
    public MapCodec<NetherSurfaceFeature> codec() {
        return CODEC;
    }

    @Override
    public boolean place(WorldGenLevel level, ChunkGenerator generator, RandomSource random, BlockPos origin) {
        if (level.getLevel().dimension() != Level.NETHER) {
            return false;
        }
        ChunkPos chunkPos = ChunkPos.containing(origin);
        int minX = chunkPos.getMinBlockX();
        int minZ = chunkPos.getMinBlockZ();
        int cx = minX >> 4;
        int cz = minZ >> 4;
        ChunkAccess chunk = level.getChunk(cx, cz);
        // The Nether's terrain: bedrock at the bottom and the top of the generated height, caves in between.
        int bottom = generator.getMinY() + 1;
        int top = generator.getMinY() + generator.getGenDepth() - 2;
        int height = top - bottom + 1;
        if (height <= 2) {
            return false;
        }
        long seed = level.getSeed();

        // Air mask: this chunk's columns plus the facing column of each neighbour (18 x 18 columns).
        boolean[] air = new boolean[18 * 18 * height];
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        fill(chunk, air, bottom, height, 0, 15, 0, 15, 0, 0, pos);
        if (level.hasChunk(cx - 1, cz)) {
            fill(level.getChunk(cx - 1, cz), air, bottom, height, 15, 15, 0, 15, -16, 0, pos);
        }
        if (level.hasChunk(cx + 1, cz)) {
            fill(level.getChunk(cx + 1, cz), air, bottom, height, 0, 0, 0, 15, 16, 0, pos);
        }
        if (level.hasChunk(cx, cz - 1)) {
            fill(level.getChunk(cx, cz - 1), air, bottom, height, 0, 15, 15, 15, 0, -16, pos);
        }
        if (level.hasChunk(cx, cz + 1)) {
            fill(level.getChunk(cx, cz + 1), air, bottom, height, 0, 15, 0, 0, 0, 16, pos);
        }

        int[] floorDepth = new int[height];
        int[] ceilingDepth = new int[height];
        boolean changed = false;
        for (int lz = 0; lz < 16; lz++) {
            for (int lx = 0; lx < 16; lx++) {
                int x = minX + lx;
                int z = minZ + lz;
                // How far each block is below the nearest air above it (0 = a floor) and above the nearest air below it
                // (0 = a ceiling). Past the ends of the band counts as solid.
                for (int i = height - 1; i >= 0; i--) {
                    floorDepth[i] = i == height - 1 ? 99 : air(air, lx, lz, i + 1, height) ? 0 : floorDepth[i + 1] + 1;
                }
                for (int i = 0; i < height; i++) {
                    ceilingDepth[i] = i == 0 ? 99 : air(air, lx, lz, i - 1, height) ? 0 : ceilingDepth[i - 1] + 1;
                }
                for (int i = height - 1; i >= 0; i--) {
                    if (air(air, lx, lz, i, height)) {
                        continue;
                    }
                    int fd = floorDepth[i];
                    int cd = ceilingDepth[i];
                    boolean side = air(air, lx - 1, lz, i, height) || air(air, lx + 1, lz, i, height)
                            || air(air, lx, lz - 1, i, height) || air(air, lx, lz + 1, i, height);
                    if (fd > 2 && cd > 0 && !side) {
                        continue;
                    }
                    int y = bottom + i;
                    pos.set(x, y, z);
                    BlockState state = chunk.getBlockState(pos);
                    if (!state.is(Blocks.NETHERRACK)) {
                        continue;
                    }
                    ResourceKey<Biome> biome = NetherRealm.of(level.getBiome(pos));
                    if (biome == null) {
                        continue;
                    }
                    BlockState paint;
                    if (biome == NetherRealm.EMBER_FOREST) {
                        paint = emberForest(seed, x, y, z, fd);
                    } else if (biome == NetherRealm.BONE_SEA) {
                        paint = boneSea(seed, x, y, z, fd, cd, side);
                    } else {
                        paint = obsidianSpires(seed, x, y, z, fd, cd, side);
                    }
                    if (paint != null) {
                        level.setBlock(pos, paint, Block.UPDATE_CLIENTS);
                        changed = true;
                    }
                    if (biome == NetherRealm.BONE_SEA && fd == 0) {
                        changed |= dune(level, air, seed, lx, lz, x, z, i, bottom, height, pos);
                    }
                }
            }
        }
        return changed;
    }

    // ---------------------------------------------------------------- the three styles
    private static BlockState emberForest(long seed, int x, int y, int z, int floorDepth) {
        if (floorDepth == 0) {
            double patch = DeepLayerFeature.noise(seed ^ ASH, x / 7.0, y / 7.0, z / 7.0);
            if (patch > 0.80) {
                return Blocks.MAGMA_BLOCK.defaultBlockState();
            }
            return patch < 0.14 ? null : WorldContent.EMBER_ASH.defaultBlockState();
        }
        if (floorDepth == 1) {
            return DeepLayerFeature.unit(DeepLayerFeature.hash(seed ^ ASH, x, y, z)) < 0.5 ? WorldContent.EMBER_ASH.defaultBlockState() : null;
        }
        return null;
    }

    private static BlockState boneSea(long seed, int x, int y, int z, int floorDepth, int ceilingDepth, boolean side) {
        if (floorDepth <= 2) {
            if (floorDepth == 0 && DeepLayerFeature.unit(DeepLayerFeature.hash(seed ^ BONE, x, y, z)) < 0.012) {
                return bone(seed, x, y, z);
            }
            return soul(seed, x, y, z);
        }
        if (ceilingDepth == 0 || side) {
            return DeepLayerFeature.noise(seed ^ WALL, x / 6.0, y / 6.0, z / 6.0) > 0.45 ? Blocks.SOUL_SOIL.defaultBlockState() : null;
        }
        return null;
    }

    private static BlockState obsidianSpires(long seed, int x, int y, int z, int floorDepth, int ceilingDepth, boolean side) {
        if (floorDepth <= 1 && crack(seed, x, z)) {
            return WorldContent.VOID_CRACK.defaultBlockState();
        }
        if (floorDepth == 0 && DeepLayerFeature.unit(DeepLayerFeature.hash(seed ^ SPECK, x, y, z)) < 0.025) {
            return Blocks.OBSIDIAN.defaultBlockState();
        }
        return floorDepth <= 2 || ceilingDepth == 0 || side ? Blocks.BLACKSTONE.defaultBlockState() : null;
    }

    /** Soul sand in blobs (about a third), soul soil around them. */
    static BlockState soul(long seed, int x, int y, int z) {
        return DeepLayerFeature.noise(seed ^ SOUL, x / 11.0, y / 8.0, z / 11.0) > 0.56
                ? Blocks.SOUL_SAND.defaultBlockState() : Blocks.SOUL_SOIL.defaultBlockState();
    }

    static BlockState bone(long seed, int x, int y, int z) {
        Direction.Axis axis = Direction.Axis.values()[(int) (DeepLayerFeature.unit(DeepLayerFeature.hash(seed ^ BONE ^ 1, x, y, z)) * 3)];
        return Blocks.BONE_BLOCK.defaultBlockState().setValue(RotatedPillarBlock.AXIS, axis);
    }

    /** A void crack: where a two-octave noise passes through its middle, a thin wandering line. */
    private static boolean crack(long seed, int x, int z) {
        double c = 0.7 * DeepLayerFeature.noise(seed ^ CRACK, x / 12.0, 0, z / 12.0) + 0.3 * DeepLayerFeature.noise(seed ^ CRACK_FINE, x / 5.0, 0, z / 5.0);
        return Math.abs(c - 0.5) < CRACK_WIDTH;
    }

    /** Heaps soul sand and soil onto a Bone Sea floor: rolling dunes, as high as the noise says and the headroom allows. */
    private static boolean dune(WorldGenLevel level, boolean[] air, long seed, int lx, int lz, int x, int z, int floorIndex, int bottom, int height,
                                BlockPos.MutableBlockPos pos) {
        double d = 0.8 * DeepLayerFeature.noise(seed ^ DUNE, x / 13.0, 0, z / 13.0) + 0.2 * DeepLayerFeature.noise(seed ^ DUNE_FINE, x / 5.0, 0, z / 5.0);
        int h = Math.min(MAX_DUNE, (int) ((d - 0.45) * 16));
        if (h <= 0) {
            return false;
        }
        for (int k = 1; k <= h + DUNE_HEADROOM; k++) {
            if (floorIndex + k >= height || !air(air, lx, lz, floorIndex + k, height)) {
                return false;
            }
        }
        for (int k = 1; k <= h; k++) {
            int y = bottom + floorIndex + k;
            pos.set(x, y, z);
            level.setBlock(pos, soul(seed, x, y, z), Block.UPDATE_CLIENTS);
        }
        return true;
    }

    // ---------------------------------------------------------------- the air mask
    private static boolean air(boolean[] air, int lx, int lz, int i, int height) {
        if (i < 0 || i >= height) {
            return false;
        }
        return air[((lz + 1) * 18 + (lx + 1)) * height + i];
    }

    /** Marks air for local columns lx0..lx1, lz0..lz1 of {@code chunk}, stored offset by (offX, offZ) in this chunk's grid. */
    private static void fill(ChunkAccess chunk, boolean[] air, int bottom, int height, int lx0, int lx1, int lz0, int lz1, int offX, int offZ,
                             BlockPos.MutableBlockPos pos) {
        int baseX = chunk.getPos().getMinBlockX();
        int baseZ = chunk.getPos().getMinBlockZ();
        for (int lz = lz0; lz <= lz1; lz++) {
            for (int lx = lx0; lx <= lx1; lx++) {
                int gx = lx + offX + 1;
                int gz = lz + offZ + 1;
                int column = (gz * 18 + gx) * height;
                for (int i = 0; i < height; i++) {
                    pos.set(baseX + lx, bottom + i, baseZ + lz);
                    air[column + i] = chunk.getBlockState(pos).isAir();
                }
            }
        }
    }
}
