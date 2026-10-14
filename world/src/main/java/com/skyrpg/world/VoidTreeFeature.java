package com.skyrpg.world;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.feature.Feature;

/**
 * The Void Gardens' trees: {@code {"type": "skyrpg_world:void_tree"}}, placed on the End's ground (the position is the air
 * just above it). A chorus-like tree: a purpur pillar trunk 5 to 9 blocks tall that bends once or twice, two to four crooked
 * branches reaching out and slightly up, and a canopy of glowing Void Cap on every branch tip and on the crown (the caps
 * glow, so a garden is lit by its own trees). Only on end stone or starstone with room above it; nothing generates in
 * chunks with a structure.
 */
public record VoidTreeFeature() implements Feature {
    public static final MapCodec<VoidTreeFeature> CODEC = MapCodec.unit(new VoidTreeFeature());

    @Override
    public MapCodec<VoidTreeFeature> codec() {
        return CODEC;
    }

    @Override
    public boolean place(WorldGenLevel level, ChunkGenerator generator, RandomSource random, BlockPos origin) {
        if (level.getLevel().dimension() != Level.END || NetherGen.nearStructure(level, origin)) {
            return false;
        }
        BlockState ground = level.getBlockState(origin.below());
        if (!ground.is(Blocks.END_STONE) && !ground.is(WorldContent.STARSTONE)) {
            return false;
        }
        int height = 5 + random.nextInt(5);
        for (int dy = 0; dy < height + 5; dy++) {
            if (!level.isInsideBuildHeight(origin.getY() + dy) || !level.getBlockState(origin.above(dy)).isAir()) {
                return false;
            }
        }
        BlockState upright = Blocks.PURPUR_PILLAR.defaultBlockState().setValue(RotatedPillarBlock.AXIS, Direction.Axis.Y);
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        int x = origin.getX();
        int z = origin.getZ();
        int bends = 1 + random.nextInt(2);
        for (int dy = 0; dy < height; dy++) {
            level.setBlock(pos.set(x, origin.getY() + dy, z), upright, 2);
            if (bends > 0 && dy >= 2 && dy < height - 1 && random.nextInt(3) == 0) {
                // a bend: step sideways at this height (a horizontal pillar joins the two columns), then carry on up
                Direction side = Direction.Plane.HORIZONTAL.getRandomDirection(random);
                BlockState across = Blocks.PURPUR_PILLAR.defaultBlockState().setValue(RotatedPillarBlock.AXIS, side.getAxis());
                x += side.getStepX();
                z += side.getStepZ();
                level.setBlock(pos.set(x, origin.getY() + dy, z), across, 2);
                bends--;
            }
        }
        int topY = origin.getY() + height;
        blob(level, random, new BlockPos(x, topY, z), 3);
        int branches = 2 + random.nextInt(3);
        for (int i = 0; i < branches; i++) {
            Direction way = Direction.Plane.HORIZONTAL.getRandomDirection(random);
            int length = 2 + random.nextInt(3);
            int bx = x;
            int bz = z;
            int by = origin.getY() + height / 2 + random.nextInt(Math.max(1, height / 2));
            BlockState along = Blocks.PURPUR_PILLAR.defaultBlockState().setValue(RotatedPillarBlock.AXIS, way.getAxis());
            for (int step = 0; step < length; step++) {
                bx += way.getStepX();
                bz += way.getStepZ();
                if (step == length / 2) {
                    by++;
                }
                pos.set(bx, by, bz);
                if (level.getBlockState(pos).isAir()) {
                    level.setBlock(pos, along, 2);
                }
            }
            blob(level, random, new BlockPos(bx, by + 1, bz), 2);
        }
        return true;
    }

    /** A squashed ball of glowing void cap (it only fills air; about one block in eight is left out). */
    private static void blob(WorldGenLevel level, RandomSource random, BlockPos centre, int radius) {
        BlockState cap = WorldContent.VOID_CAP.defaultBlockState();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dy = -radius; dy <= radius; dy++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    double dist = Math.sqrt(dx * dx + dy * dy * 1.6 + dz * dz);
                    if (dist > radius + 0.3 || random.nextInt(8) == 0) {
                        continue;
                    }
                    pos.set(centre.getX() + dx, centre.getY() + dy, centre.getZ() + dz);
                    if (level.isInsideBuildHeight(pos.getY()) && level.getBlockState(pos).isAir()) {
                        level.setBlock(pos, cap, 2);
                    }
                }
            }
        }
    }
}
