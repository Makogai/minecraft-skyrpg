package com.skyrpg.foraging;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.BonemealSource;
import net.minecraft.world.level.block.SaplingBlock;
import net.minecraft.world.level.block.grower.TreeGrower;
import net.minecraft.world.level.block.state.BlockState;

/**
 * A SkyRPG sapling: grows like a vanilla one, but only while its wood's {@link Wood.Condition} holds, into that wood's
 * tree (see {@link TreeShapes}). Bone meal does nothing to it.
 */
public final class SkySaplingBlock extends SaplingBlock {
    private final Wood wood;

    public SkySaplingBlock(Wood wood, Properties properties) {
        // The grower is never used: advanceTree builds our own tree.
        super(TreeGrower.OAK, properties);
        this.wood = wood;
    }

    public Wood wood() {
        return wood;
    }

    @Override
    protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (!wood.condition().holds(level, pos)) {
            return;
        }
        // Night-growing woods can't wait for daylight like vanilla saplings do.
        boolean lit = wood.condition().atNight() || level.getMaxLocalRawBrightness(pos.above()) >= BRIGHTNESS_FOR_SAPLING_GROWTH;
        if (lit && random.nextInt(TICK_CHANCE_FOR_SAPLING_GROWTH) == 0) {
            advanceTree(level, pos, state, random);
        }
    }

    @Override
    public void advanceTree(ServerLevel level, BlockPos pos, BlockState state, RandomSource random) {
        if (state.getValue(STAGE) == 0) {
            level.setBlock(pos, state.cycle(STAGE), Block.UPDATE_NONE);
        } else {
            TreeShapes.grow(level, pos, wood, random, Block.UPDATE_ALL);
        }
    }

    @Override
    public boolean isValidBonemealTarget(LevelReader level, BlockPos pos, BlockState state, BonemealSource source) {
        return false;
    }
}
