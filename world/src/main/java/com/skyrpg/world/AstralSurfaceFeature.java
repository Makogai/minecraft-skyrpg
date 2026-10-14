package com.skyrpg.world;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.feature.Feature;

/**
 * The Astral Fields' ground: {@code {"type": "skyrpg_world:astral_surface"}}, with no placement modifiers, so it runs once for
 * every chunk that has an Astral Fields block in it. Over the chunk's columns whose biome is the Astral Fields, the top of
 * end stone becomes starstone ("stars in the ground": one column in six, and some of the block beneath), and about one
 * column in ninety grows an Astral Crystal cluster on top, often with a second one beside it. Chunks with a structure are
 * left alone (the Astral Observatory's ground is its own).
 */
public record AstralSurfaceFeature() implements Feature {
    public static final MapCodec<AstralSurfaceFeature> CODEC = MapCodec.unit(new AstralSurfaceFeature());

    @Override
    public MapCodec<AstralSurfaceFeature> codec() {
        return CODEC;
    }

    @Override
    public boolean place(WorldGenLevel level, ChunkGenerator generator, RandomSource random, BlockPos origin) {
        if (level.getLevel().dimension() != Level.END || NetherGen.nearStructure(level, origin)) {
            return false;
        }
        ChunkPos chunk = ChunkPos.containing(origin);
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        BlockState starstone = WorldContent.STARSTONE.defaultBlockState();
        BlockState crystal = WorldContent.ASTRAL_CRYSTAL.defaultBlockState();
        boolean changed = false;
        for (int lz = 0; lz < 16; lz++) {
            for (int lx = 0; lx < 16; lx++) {
                int x = chunk.getMinBlockX() + lx;
                int z = chunk.getMinBlockZ() + lz;
                int top = level.getHeight(Heightmap.Types.WORLD_SURFACE_WG, x, z) - 1;
                if (top < level.getMinY() + 1) {
                    continue;
                }
                pos.set(x, top, z);
                if (!level.getBlockState(pos).is(Blocks.END_STONE) || !level.getBiome(pos).is(EndRealm.ASTRAL_FIELDS)) {
                    continue;
                }
                if (random.nextInt(6) == 0) {
                    level.setBlock(pos, starstone, 2);
                    changed = true;
                    if (random.nextInt(3) == 0 && level.getBlockState(pos.below()).is(Blocks.END_STONE)) {
                        level.setBlock(pos.below(), starstone, 2);
                    }
                }
                if (random.nextInt(90) == 0 && level.getBlockState(pos.above()).isAir()) {
                    level.setBlock(pos.above(), crystal, 2);
                    changed = true;
                    if (random.nextInt(2) == 0) {
                        BlockPos next = pos.offset(random.nextInt(3) - 1, 0, random.nextInt(3) - 1);
                        if (level.getBlockState(next).is(Blocks.END_STONE) && level.getBlockState(next.above()).isAir()) {
                            level.setBlock(next.above(), crystal, 2);
                        }
                    }
                }
            }
        }
        return changed;
    }
}
