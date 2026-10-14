package com.skyrpg.farming;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A planter: a box of soil whose crop grows on a timer, wherever it's placed and whatever the light, biome or water.
 * When it's ripe, right-click or hit it to harvest (see {@link Harvest#harvestPlanter}); it starts over. It can't be
 * broken: sneak and hit it with an empty hand to pick it up. The timer is a scheduled block tick, so it survives
 * restarts.
 */
public final class PlanterBlock extends Block {
    public static final BooleanProperty RIPE = BooleanProperty.create("ripe");
    private static final VoxelShape BOX = Block.box(0, 0, 0, 16, 10, 16);

    private final FarmingBlocks.Planter planter;

    public PlanterBlock(FarmingBlocks.Planter planter, Properties properties) {
        super(properties);
        this.planter = planter;
        registerDefaultState(stateDefinition.any().setValue(RIPE, false));
    }

    public FarmingBlocks.Planter planter() {
        return planter;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(RIPE);
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return BOX;
    }

    @Override
    protected float getDestroyProgress(BlockState state, Player player, BlockGetter level, BlockPos pos) {
        return Harvest.picksUp(player) ? 0.5f : 0.0f;
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        if (!state.getValue(RIPE) && !oldState.is(this)) {
            level.scheduleTick(pos, this, planter.growTicks());
        }
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (!state.getValue(RIPE)) {
            level.setBlock(pos, state.setValue(RIPE, true), Block.UPDATE_ALL);
            level.sendParticles(ParticleTypes.HAPPY_VILLAGER, pos.getX() + 0.5, pos.getY() + 0.9, pos.getZ() + 0.5, 8, 0.3, 0.2, 0.3, 0.0);
            level.playSound(null, pos, SoundEvents.CROP_PLANTED, SoundSource.BLOCKS, 0.6f, 1.2f);
        }
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!state.getValue(RIPE)) {
            return InteractionResult.PASS;
        }
        if (player instanceof ServerPlayer serverPlayer && level instanceof ServerLevel serverLevel) {
            harvest(serverLevel, pos, state, serverPlayer);
        }
        return InteractionResult.SUCCESS;
    }

    /** Takes the crop: rewards for the player, then the planter starts growing again. */
    void harvest(ServerLevel level, BlockPos pos, BlockState state, ServerPlayer player) {
        level.setBlock(pos, state.setValue(RIPE, false), Block.UPDATE_ALL);
        // Planter Tender talents shorten the regrowth (up to half).
        double faster = Math.min(0.5, FarmingEffects.value(player, FarmingEffects.PLANTER_SPEED));
        level.scheduleTick(pos, this, Math.max(20, (int) Math.round(planter.growTicks() * (1 - faster))));
        level.playSound(null, pos, SoundEvents.CROP_BREAK, SoundSource.BLOCKS, 0.8f, 1.0f);
        Harvest.harvestPlanter(player, pos, planter);
    }
}
