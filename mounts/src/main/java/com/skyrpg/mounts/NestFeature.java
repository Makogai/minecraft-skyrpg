package com.skyrpg.mounts;

import com.mojang.serialization.MapCodec;
import com.skyrpg.core.world.Regions;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.feature.Feature;

/**
 * Griffin nests on the high ledges of the Shattered Highlands: {@code {"type": "skyrpg_mounts:griffin_nest"}}, placed on a
 * surface (the position is the air just above it) in the mountain biomes. It only places a nest where core's
 * {@link Regions#typeAt} says the spot is in a "shattered_highlands" region (the world mod installs the lookup), so without
 * that region there are no nests. A nest is a plus of nest blocks on solid ground; its wild griffins are spawned by the
 * block (see {@link GriffinNestBlock}), as entities can't be added during world generation.
 */
public record NestFeature() implements Feature {
    public static final MapCodec<NestFeature> CODEC = MapCodec.unit(new NestFeature());
    public static final String REGION = "shattered_highlands";
    /** A dev switch ({@code -Dskyrpg.mounts.anywhere=true}) to test the feature outside the region. */
    private static final boolean ANYWHERE = Boolean.getBoolean("skyrpg.mounts.anywhere");

    @Override
    public MapCodec<NestFeature> codec() {
        return CODEC;
    }

    @Override
    public boolean place(WorldGenLevel level, ChunkGenerator generator, RandomSource random, BlockPos origin) {
        if (!ANYWHERE && !REGION.equals(Regions.typeAt(level.getLevel(), origin.getX(), origin.getZ()))) {
            return false;
        }
        BlockPos ground = origin.below();
        BlockState floor = level.getBlockState(ground);
        if (!floor.isSolid() || !floor.getFluidState().isEmpty() || !level.getBlockState(origin).isAir() || !level.getFluidState(origin).isEmpty()) {
            return false;
        }
        return build(level, origin) > 0;
    }

    /** Places the nest blocks (a centre and up to four arms) on solid ground around {@code center}; returns how many. */
    public static int build(LevelAccessor level, BlockPos center) {
        BlockState nest = MountBlocks.GRIFFIN_NEST.defaultBlockState();
        int placed = 0;
        for (BlockPos at : new BlockPos[]{center, center.relative(Direction.NORTH), center.relative(Direction.SOUTH), center.relative(Direction.EAST),
                center.relative(Direction.WEST)}) {
            if (at.equals(center) || (level.getBlockState(at).isAir() && level.getBlockState(at.below()).isSolid())) {
                level.setBlock(at, nest, 2);
                placed++;
            }
        }
        return placed;
    }
}
