package com.skyrpg.world;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.feature.Feature;

/**
 * The Shattered Isles' rubble: {@code {"type": "skyrpg_world:shattered_island"}}, placed in the open air (the position is
 * the middle of the islet's top). A small floating islet 2 to 5 blocks across, tapering to a point below like a vanilla End
 * island, of end stone with end stone bricks, purpur and amethyst through it and amethyst clusters (and now and then a void
 * bloom) on top, plus two to four loose blocks of rubble round it at the odd height: stepping stones for parkour, with gaps
 * a sprint-jump wide. Skipped where there is anything but air (so it never cuts into a real island) and in chunks with a
 * structure.
 */
public record ShatteredIslandFeature() implements Feature {
    public static final MapCodec<ShatteredIslandFeature> CODEC = MapCodec.unit(new ShatteredIslandFeature());

    @Override
    public MapCodec<ShatteredIslandFeature> codec() {
        return CODEC;
    }

    @Override
    public boolean place(WorldGenLevel level, ChunkGenerator generator, RandomSource random, BlockPos origin) {
        if (level.getLevel().dimension() != Level.END || NetherGen.nearStructure(level, origin)) {
            return false;
        }
        int radius = 2 + random.nextInt(4);
        int depth = radius + 2 + random.nextInt(radius + 2);
        if (!level.isInsideBuildHeight(origin.getY() + 3) || !level.isInsideBuildHeight(origin.getY() - depth - 1)) {
            return false;
        }
        // open air all round and below (a coarse check: the middle column and the rim at the top and half way down)
        int[][] probes = {{0, 0}, {radius + 1, 0}, {-radius - 1, 0}, {0, radius + 1}, {0, -radius - 1}};
        for (int[] probe : probes) {
            for (int dy : new int[] {2, 0, -depth / 2, -depth}) {
                if (!level.getBlockState(origin.offset(probe[0], dy, probe[1])).isAir()) {
                    return false;
                }
            }
        }
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        BlockState stone = Blocks.END_STONE.defaultBlockState();
        for (int layer = 0; layer <= depth; layer++) {
            double t = layer / (double) (depth + 1);
            double r = radius * Math.pow(1 - t, 0.7) + (layer == 0 ? 0.4 : 0.0);
            int reach = (int) Math.ceil(r + 1);
            for (int dx = -reach; dx <= reach; dx++) {
                for (int dz = -reach; dz <= reach; dz++) {
                    double dist = Math.sqrt(dx * dx + dz * dz) + random.nextDouble() * 0.8 - 0.4;
                    if (dist > r) {
                        continue;
                    }
                    pos.set(origin.getX() + dx, origin.getY() - 1 - layer, origin.getZ() + dz);
                    int roll = random.nextInt(100);
                    BlockState block = stone;
                    if (layer == 0 ? roll < 7 : roll < 4) {
                        block = Blocks.END_STONE_BRICKS.defaultBlockState();
                    } else if (roll >= 90 && roll < 96) {
                        block = Blocks.AMETHYST_BLOCK.defaultBlockState();
                    } else if (roll >= 96 && layer < depth - 1) {
                        block = Blocks.PURPUR_BLOCK.defaultBlockState();
                    }
                    level.setBlock(pos, block, 2);
                    if (layer == 0 && random.nextInt(9) == 0) {
                        level.setBlock(pos.above(), Blocks.AMETHYST_CLUSTER.defaultBlockState(), 2);
                    } else if (layer == 0 && random.nextInt(24) == 0) {
                        level.setBlock(pos.above(), WorldContent.VOID_BLOOM.defaultBlockState(), 2);
                    }
                }
            }
        }
        int rubble = 2 + random.nextInt(3);
        for (int i = 0; i < rubble; i++) {
            double angle = random.nextDouble() * Math.PI * 2;
            double dist = radius + 3 + random.nextInt(5);
            pos.set(origin.getX() + (int) Math.round(Math.cos(angle) * dist), origin.getY() - 1 + random.nextInt(4) - 2,
                    origin.getZ() + (int) Math.round(Math.sin(angle) * dist));
            if (level.isInsideBuildHeight(pos.getY()) && level.getBlockState(pos).isAir()) {
                int roll = random.nextInt(3);
                level.setBlock(pos, roll == 0 ? Blocks.AMETHYST_BLOCK.defaultBlockState()
                        : roll == 1 ? Blocks.END_STONE_BRICKS.defaultBlockState() : stone, 2);
            }
        }
        return true;
    }
}
