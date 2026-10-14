package com.skyrpg.foraging;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;

/**
 * A placeable stump, the foraging version of a mining node: chop it for its log and a little Foraging XP, then it's a
 * bare stump until it regrows (a scheduled tick, so it survives restarts). Sneaking with an empty hand picks it back up
 * (see {@link Chopping}).
 */
public final class StumpBlock extends Block {
    public static final BooleanProperty DEPLETED = BooleanProperty.create("depleted");

    private final ForagingBlocks.Stump stump;

    public StumpBlock(ForagingBlocks.Stump stump, Properties properties) {
        super(properties);
        this.stump = stump;
        registerDefaultState(stateDefinition.any().setValue(DEPLETED, false));
    }

    public ForagingBlocks.Stump stump() {
        return stump;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(DEPLETED);
    }

    @Override
    protected float getDestroyProgress(BlockState state, Player player, BlockGetter level, BlockPos pos) {
        if (Chopping.picksUp(player)) {
            return 0.5f;
        }
        return state.getValue(DEPLETED) ? 0.0f : super.getDestroyProgress(state, player, level, pos);
    }

    /** After a player chopped it: leave the bare stump and start the regrowth timer ({@code faster}: 0.1 = 10% sooner, up to half). */
    void deplete(ServerLevel level, BlockPos pos, double faster) {
        level.setBlock(pos, defaultBlockState().setValue(DEPLETED, true), Block.UPDATE_ALL);
        level.scheduleTick(pos, this, Math.max(20, (int) Math.round(stump.growTicks() * (1 - Math.min(0.5, faster)))));
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (state.getValue(DEPLETED)) {
            level.setBlock(pos, state.setValue(DEPLETED, false), Block.UPDATE_ALL);
            level.sendParticles(ParticleTypes.HAPPY_VILLAGER, pos.getX() + 0.5, pos.getY() + 0.8, pos.getZ() + 0.5, 10, 0.35, 0.3, 0.35, 0.0);
            level.playSound(null, pos, SoundEvents.CHERRY_SAPLING_PLACE, SoundSource.BLOCKS, 0.8f, 0.9f);
        }
    }
}
