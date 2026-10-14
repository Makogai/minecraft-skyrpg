package com.skyrpg.mining;

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
 * A placeable ore node. Mining it gives its ore (straight into the inventory) and a little Mining XP, then
 * it turns into a spent husk that can't be mined until it regrows. The regrowth is a scheduled block tick,
 * so it survives chunk unloads and restarts. Sneaking with an empty hand picks a node back up (see
 * {@link MiningDrops}).
 */
public final class NodeBlock extends Block {
    public static final BooleanProperty DEPLETED = BooleanProperty.create("depleted");

    private final int respawnTicks;

    public NodeBlock(int respawnSeconds, Properties properties) {
        super(properties);
        this.respawnTicks = respawnSeconds * 20;
        registerDefaultState(stateDefinition.any().setValue(DEPLETED, false));
    }

    public int respawnSeconds() {
        return respawnTicks / 20;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(DEPLETED);
    }

    @Override
    protected float getDestroyProgress(BlockState state, Player player, BlockGetter level, BlockPos pos) {
        if (MiningDrops.picksUp(player)) {
            return 0.5f;
        }
        return state.getValue(DEPLETED) ? 0.0f : super.getDestroyProgress(state, player, level, pos);
    }

    /** Called after a player mined it: leave the spent husk and start the regrowth timer ({@code faster}: 0.1 = 10% sooner, up to half). */
    void deplete(ServerLevel level, BlockPos pos, double faster) {
        level.setBlock(pos, defaultBlockState().setValue(DEPLETED, true), Block.UPDATE_ALL);
        level.scheduleTick(pos, this, Math.max(20, (int) Math.round(respawnTicks * (1 - Math.min(0.5, faster)))));
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (state.getValue(DEPLETED)) {
            level.setBlock(pos, state.setValue(DEPLETED, false), Block.UPDATE_ALL);
            level.sendParticles(ParticleTypes.WAX_ON, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 12, 0.35, 0.35, 0.35, 0.0);
            level.playSound(null, pos, SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.BLOCKS, 0.6f, 1.3f);
        }
    }
}
