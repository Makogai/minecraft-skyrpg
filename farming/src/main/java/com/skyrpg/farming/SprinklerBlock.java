package com.skyrpg.farming;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.FarmlandBlock;
import net.minecraft.world.level.block.StemBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A sprinkler: every {@link #INTERVAL} ticks it gives each crop in a square around it extra growth (special crops still
 * need their condition) and keeps the farmland under them wet. Bigger sprinklers reach further. The extra growth follows
 * the {@code random_tick_speed} game rule (one tick at the default 3, five thirds at 5...), so a sprinkler stays about
 * 14 times faster than natural growth whatever the server sets.
 */
public final class SprinklerBlock extends Block {
    public static final int INTERVAL = 100;
    private static final VoxelShape SHAPE = Block.box(5, 0, 5, 11, 11, 11);

    private final int radius;

    public SprinklerBlock(int radius, Properties properties) {
        super(properties);
        this.radius = radius;
    }

    /** How far it reaches (2 = a 5x5 square). */
    public int radius() {
        return radius;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        if (!oldState.is(this)) {
            level.scheduleTick(pos, this, INTERVAL);
        }
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        water(level, pos, radius, random);
        level.sendParticles(ParticleTypes.SPLASH, pos.getX() + 0.5, pos.getY() + 0.8, pos.getZ() + 0.5, 20, radius * 0.4, 0.1, radius * 0.4, 0.2);
        level.scheduleTick(pos, this, INTERVAL);
    }

    /** The default {@code random_tick_speed}: one extra growth tick per watering at this speed. */
    private static final int DEFAULT_TICK_SPEED = 3;

    /**
     * Growth for every crop within {@code radius} (and a level above or below), wet farmland under them: random ticks in
     * proportion to the {@code random_tick_speed} game rule (a fraction is a chance of one more), at least one.
     */
    static void water(ServerLevel level, BlockPos center, int radius, RandomSource random) {
        double perCrop = Math.max(1.0, level.getGameRules().get(GameRules.RANDOM_TICK_SPEED) / (double) DEFAULT_TICK_SPEED);
        for (BlockPos pos : BlockPos.betweenClosed(center.offset(-radius, -1, -radius), center.offset(radius, 1, radius))) {
            BlockState state = level.getBlockState(pos);
            if (state.is(BlockTags.CROPS) || state.getBlock() instanceof StemBlock) {
                BlockPos at = pos.immutable();
                int ticks = (int) perCrop + (random.nextDouble() < perCrop - (int) perCrop ? 1 : 0);
                for (int i = 0; i < ticks; i++) {
                    BlockState now = level.getBlockState(at);
                    if (!(now.is(BlockTags.CROPS) || now.getBlock() instanceof StemBlock)) {
                        break;
                    }
                    now.randomTick(level, at, random);
                }
                BlockPos below = pos.below();
                BlockState soil = level.getBlockState(below);
                if (soil.getBlock() instanceof FarmlandBlock && soil.getValue(FarmlandBlock.MOISTURE) < FarmlandBlock.MAX_MOISTURE) {
                    level.setBlock(below, soil.setValue(FarmlandBlock.MOISTURE, FarmlandBlock.MAX_MOISTURE), Block.UPDATE_CLIENTS);
                }
            }
        }
    }
}
