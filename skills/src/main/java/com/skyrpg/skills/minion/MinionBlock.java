package com.skyrpg.skills.minion;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/** The minion block (see {@link Minions}): while loaded it shows its work with a puff of the block it works. */
public final class MinionBlock extends Block {
    private static final VoxelShape SHAPE = Block.box(3, 0, 3, 13, 12, 13);
    private static final int PUFF_TICKS = 100;

    public MinionBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack) {
        if (level instanceof ServerLevel serverLevel && placer instanceof ServerPlayer player) {
            Minions.placed(player, serverLevel, pos);
            level.scheduleTick(pos, this, PUFF_TICKS);
        }
    }

    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand,
                                          BlockHitResult hit) {
        if (Minions.work(stack) == null) {
            return InteractionResult.TRY_WITH_EMPTY_HAND;
        }
        if (level instanceof ServerLevel serverLevel && player instanceof ServerPlayer serverPlayer) {
            Minions.use(serverPlayer, serverLevel, pos, stack);
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level instanceof ServerLevel serverLevel && player instanceof ServerPlayer serverPlayer) {
            Minions.use(serverPlayer, serverLevel, pos, ItemStack.EMPTY);
            level.scheduleTick(pos, this, PUFF_TICKS);
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        BlockState work = Minions.target(level, pos);
        if (work != null) {
            if (Minions.full(level, pos)) {
                level.sendParticles(ParticleTypes.ANGRY_VILLAGER, pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5, 1, 0.2, 0.1, 0.2, 0);
            } else {
                level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, work), pos.getX() + 0.5, pos.getY() + 0.9, pos.getZ() + 0.5,
                        8, 0.25, 0.1, 0.25, 0.05);
            }
        }
        level.scheduleTick(pos, this, PUFF_TICKS);
    }
}
