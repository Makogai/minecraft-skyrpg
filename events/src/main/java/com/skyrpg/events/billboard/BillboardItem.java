package com.skyrpg.events.billboard;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.phys.Vec3;

/**
 * Places a billboard: on the side of a block it hangs on that wall (its bottom level with the block you clicked, centred
 * on it); on top of a block it stands on posts there, facing you. Opens the editor straight away.
 */
public final class BillboardItem extends Item {
    private static final int START_WIDTH = 6;

    public BillboardItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        if (!(context.getLevel() instanceof ServerLevel level) || !(context.getPlayer() instanceof ServerPlayer player)) {
            return InteractionResult.SUCCESS;
        }
        Direction face = context.getClickedFace();
        BlockPos pos = context.getClickedPos();
        float depth = BillboardTheme.depth(START_WIDTH);
        Vec3 at;
        Direction facing;
        boolean stand;
        if (face.getAxis().isHorizontal()) {
            facing = face;
            stand = false;
            at = new Vec3(pos.getX() + 0.5 + face.getStepX() * (0.5 + depth / 2), pos.getY(), pos.getZ() + 0.5 + face.getStepZ() * (0.5 + depth / 2));
        } else if (face == Direction.UP) {
            facing = player.getDirection().getOpposite();
            stand = true;
            at = new Vec3(pos.getX() + 0.5, pos.getY() + 1, pos.getZ() + 0.5);
        } else {
            return InteractionResult.FAIL;
        }
        BillboardEntity board = Billboards.TYPE.create(level, net.minecraft.world.entity.EntitySpawnReason.TRIGGERED);
        if (board == null) {
            return InteractionResult.FAIL;
        }
        board.setup(facing, START_WIDTH, stand, player);
        board.setPos(at.x, at.y, at.z);
        if (!level.noCollision(board, board.getBoundingBox().deflate(0.05))) {
            player.sendSystemMessage(Component.literal("Not enough room for a billboard here (it needs about "
                    + (int) Math.ceil(START_WIDTH + 1) + " blocks across and " + (int) Math.ceil(board.getBoundingBox().getYsize()) + " up)."));
            return InteractionResult.FAIL;
        }
        level.addFreshEntity(board);
        level.playSound(null, at.x, at.y, at.z, SoundEvents.WOOD_PLACE, SoundSource.BLOCKS, 1f, 0.8f);
        if (!player.isCreative()) {
            context.getItemInHand().shrink(1);
        }
        Billboards.openEditor(player, board);
        return InteractionResult.SUCCESS;
    }
}
