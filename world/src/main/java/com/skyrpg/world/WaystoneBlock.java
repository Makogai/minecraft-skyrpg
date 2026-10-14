package com.skyrpg.world;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A waystone: right-click to attune, then to open the travel menu (see {@link Waystones}). It glows ({@code lit})
 * once anyone has attuned it. A pickaxe takes one up (it drops as an item that remembers it, so placing it again moves
 * the same waystone); the hub's and the dungeon entrances' stay put. Nothing destroys one by accident: explosions
 * can't, pistons won't push it, and anything that does remove it leaves the item behind.
 */
public class WaystoneBlock extends Block {
    public static final BooleanProperty LIT = BlockStateProperties.LIT;
    private static final VoxelShape SHAPE = Shapes.or(box(1, 0, 1, 15, 3, 15), box(3, 3, 3, 13, 16, 13));

    public WaystoneBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(LIT, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(LIT);
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (level instanceof ServerLevel serverLevel) {
            Waystones.placed(serverLevel, pos, placer, stack);
        }
    }

    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        // A creative player deletes the waystone for good; anyone else's break leaves the item (see affectNeighborsAfterRemoval).
        if (player.isCreative() && level instanceof ServerLevel serverLevel) {
            Waystones.forget(serverLevel, pos);
        }
        return super.playerWillDestroy(level, pos, state, player);
    }

    @Override
    protected void affectNeighborsAfterRemoval(BlockState state, ServerLevel level, BlockPos pos, boolean movedByPiston) {
        super.affectNeighborsAfterRemoval(state, level, pos, movedByPiston);
        // Whatever took the block away (a pickaxe, a wither, an enderman, a command), the waystone turns into its item.
        if (!level.getBlockState(pos).is(this)) {
            Waystones.removed(level, pos, state.getValue(LIT));
        }
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (player instanceof ServerPlayer serverPlayer) {
            Waystones.use(serverPlayer, pos, null);
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand,
                                          BlockHitResult hit) {
        // A renamed name tag renames the waystone; anything else just uses it.
        if (player instanceof ServerPlayer serverPlayer) {
            Waystones.use(serverPlayer, pos, stack.is(Items.NAME_TAG) ? stack : null);
        }
        return InteractionResult.SUCCESS;
    }
}
