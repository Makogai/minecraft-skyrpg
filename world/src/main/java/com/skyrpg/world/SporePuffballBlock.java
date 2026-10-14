package com.skyrpg.world;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.VegetationBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A Fungal Depths trap: a fat puffball that bursts into a poison spore cloud when a survival player comes within two
 * blocks (Poison II for 4 seconds to everyone in the cloud), then sits flat and spent until it regrows about 30 seconds
 * later. No block entity: {@link DeepLayers} looks around each survival player a few times a second for ready ones,
 * and a scheduled tick regrows them.
 */
final class SporePuffballBlock extends VegetationBlock {
    static final BooleanProperty BURST = BooleanProperty.create("burst");
    static final int REGROW_TICKS = 600;
    private static final double TRIGGER = 2.5;
    private static final double CLOUD = 2.75;
    private static final VoxelShape READY = box(3, 0, 3, 13, 9, 13);
    private static final VoxelShape SPENT = box(3, 0, 3, 13, 3, 13);

    SporePuffballBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(BURST, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(BURST);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return state.getValue(BURST) ? SPENT : READY;
    }

    /** Grows on any solid floor (cave stone, moss, mycelium), not only dirt. */
    @Override
    protected boolean mayPlaceOn(BlockState state, BlockGetter level, BlockPos pos) {
        return state.isFaceSturdy(level, pos, Direction.UP);
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (state.getValue(BURST)) {
            level.setBlock(pos, state.setValue(BURST, false), Block.UPDATE_ALL);
            level.playSound(null, pos, SoundEvents.FUNGUS_BREAK, SoundSource.BLOCKS, 0.4f, 1.4f);
        }
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (!state.getValue(BURST) && random.nextInt(6) == 0) {
            level.addParticle(ParticleTypes.SPORE_BLOSSOM_AIR, pos.getX() + 0.2 + random.nextDouble() * 0.6, pos.getY() + 0.6,
                    pos.getZ() + 0.2 + random.nextDouble() * 0.6, 0, 0.01, 0);
        }
    }

    /** Bursts any ready puffball within reach of this player (5 x 4 x 5 blocks around their feet). */
    static void near(ServerPlayer player) {
        ServerLevel level = player.level();
        BlockPos feet = player.blockPosition();
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int dy = -1; dy <= 2; dy++) {
            for (int dx = -2; dx <= 2; dx++) {
                for (int dz = -2; dz <= 2; dz++) {
                    cursor.set(feet.getX() + dx, feet.getY() + dy, feet.getZ() + dz);
                    BlockState state = level.getBlockState(cursor);
                    if (state.getBlock() instanceof SporePuffballBlock && !state.getValue(BURST)
                            && player.position().distanceToSqr(cursor.getX() + 0.5, cursor.getY() + 0.3, cursor.getZ() + 0.5) <= TRIGGER * TRIGGER) {
                        burst(level, cursor.immutable(), state);
                    }
                }
            }
        }
    }

    static void burst(ServerLevel level, BlockPos pos, BlockState state) {
        level.setBlock(pos, state.setValue(BURST, true), Block.UPDATE_ALL);
        level.scheduleTick(pos, state.getBlock(), REGROW_TICKS);
        double x = pos.getX() + 0.5;
        double y = pos.getY() + 0.5;
        double z = pos.getZ() + 0.5;
        level.sendParticles(ParticleTypes.SNEEZE, x, y, z, 45, 1.1, 0.7, 1.1, 0.02);
        level.sendParticles(ParticleTypes.SPORE_BLOSSOM_AIR, x, y + 0.3, z, 30, 1.4, 0.9, 1.4, 0.01);
        level.sendParticles(ParticleTypes.CRIMSON_SPORE, x, y, z, 12, 0.6, 0.4, 0.6, 0.02);
        level.playSound(null, pos, SoundEvents.PUFFER_FISH_BLOW_UP, SoundSource.BLOCKS, 1.0f, 0.6f);
        for (ServerPlayer player : level.getEntitiesOfClass(ServerPlayer.class, new AABB(pos).inflate(CLOUD))) {
            if (!player.isSpectator() && !player.isCreative() && player.position().distanceToSqr(x, y, z) <= CLOUD * CLOUD) {
                player.addEffect(new MobEffectInstance(MobEffects.POISON, 80, 1));
            }
        }
    }
}
