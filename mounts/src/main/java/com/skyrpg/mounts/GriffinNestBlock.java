package com.skyrpg.mounts;

import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A griffin nest: a bowl of sticks, hay and feathers. While a player is within 64 blocks it keeps one to three wild griffins
 * around it (the number is fixed per nest, so they never build up): a random tick starts a chain of scheduled ticks that
 * checks every few seconds and spawns a griffin whenever there are fewer than the cap within 40 blocks.
 */
public final class GriffinNestBlock extends Block {
    private static final VoxelShape SHAPE = Block.box(0, 0, 0, 16, 5, 16);
    private static final int PLAYER_RANGE = 64;
    private static final int CHECK_TICKS = 100;

    public GriffinNestBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        if (level instanceof ServerLevel server && !server.getBlockTicks().hasScheduledTick(pos, this)) {
            server.scheduleTick(pos, this, 20 + server.getRandom().nextInt(40));
        }
    }

    @Override
    protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        // a worldgen nest has no scheduled tick yet: this starts its chain
        if (!level.getBlockTicks().hasScheduledTick(pos, this)) {
            level.scheduleTick(pos, this, 1);
        }
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        level.scheduleTick(pos, this, CHECK_TICKS + random.nextInt(60));
        maintain(level, pos);
    }

    /** The cap for the nest this block belongs to: 1 to 3, the same for every block of one nest (they sit in the same chunk). */
    static int cap(BlockPos pos) {
        int hash = (pos.getX() >> 4) * 73856093 ^ (pos.getZ() >> 4) * 19349663;
        return 1 + Math.floorMod(hash, 3);
    }

    /** Spawns a wild griffin at this nest if a player is close and there are fewer than the cap around. Returns the new one. */
    public static GriffinEntity maintain(ServerLevel level, BlockPos pos) {
        if (level.getNearestPlayer(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, PLAYER_RANGE, false) == null) {
            return null;
        }
        List<GriffinEntity> near = level.getEntitiesOfClass(GriffinEntity.class, new AABB(pos).inflate(40, 30, 40),
                griffin -> !griffin.isTame() && !griffin.isStorm());
        if (near.size() >= cap(pos)) {
            return null;
        }
        GriffinEntity griffin = MountEntities.GRIFFIN.create(level, EntitySpawnReason.NATURAL);
        if (griffin == null) {
            return null;
        }
        double x = pos.getX() + 0.5 + (level.getRandom().nextDouble() - 0.5) * 2;
        double z = pos.getZ() + 0.5 + (level.getRandom().nextDouble() - 0.5) * 2;
        griffin.snapTo(x, pos.getY() + 0.35, z, level.getRandom().nextFloat() * 360f, 0f);
        if (!level.noCollision(griffin.getBoundingBox())) {
            griffin.snapTo(x, pos.getY() + 1.4, z, griffin.getYRot(), 0f);
            if (!level.noCollision(griffin.getBoundingBox())) {
                return null;
            }
        }
        griffin.setColor(GriffinColor.random(level.getRandom()));
        griffin.setHomeTo(pos, 14);
        level.addFreshEntity(griffin);
        return griffin;
    }
}
