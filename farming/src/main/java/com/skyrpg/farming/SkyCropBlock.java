package com.skyrpg.farming;

import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.BonemealSource;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.IntegerProperty;

/**
 * A SkyRPG crop: four growth stages on farmland, like wheat, but it only grows while its {@link Condition} holds
 * (open sky by day, cold, near lava, night...). Vanilla bone meal does nothing to it; Rich Compost does (and ignores
 * the condition), and so do sprinklers and growth auras when the condition holds.
 */
public final class SkyCropBlock extends CropBlock {
    public static final IntegerProperty AGE = BlockStateProperties.AGE_3;

    /** When the crop may grow. */
    public enum Condition {
        /** Under open sky, in daylight. */
        SUN("in full sun (open sky, daytime)"),
        /** In a cold biome, or within 2 blocks of snow or ice. */
        COLD("in the cold (a cold biome, or next to snow or ice)"),
        /** Within 4 blocks of lava, or in a hot dimension (the Nether). */
        HEAT("near lava, or in the Nether"),
        /** Only at night. */
        NIGHT("only at night");

        private final String description;

        Condition(String description) {
            this.description = description;
        }

        public String description() {
            return description;
        }
    }

    private final Condition condition;
    private final Supplier<ItemLike> seeds;

    public SkyCropBlock(Condition condition, Supplier<ItemLike> seeds, Properties properties) {
        super(properties);
        this.condition = condition;
        this.seeds = seeds;
        registerDefaultState(stateDefinition.any().setValue(AGE, 0));
    }

    public Condition condition() {
        return condition;
    }

    @Override
    protected IntegerProperty getAgeProperty() {
        return AGE;
    }

    @Override
    public int getMaxAge() {
        return 3;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(AGE);
    }

    @Override
    protected ItemLike getBaseSeedId() {
        return seeds.get();
    }

    @Override
    protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        // Special crops are slower than wheat (4 stages instead of 8 make each tick count double).
        if (canGrowHere(level, pos) && random.nextInt(2) == 0) {
            super.randomTick(state, level, pos, random);
        }
    }

    @Override
    public boolean isValidBonemealTarget(LevelReader level, BlockPos pos, BlockState state, BonemealSource source) {
        return false;
    }

    /** Whether this crop's condition holds where it's planted. */
    public boolean canGrowHere(Level level, BlockPos pos) {
        return switch (condition) {
            case SUN -> level.canSeeSky(pos) && level.isBrightOutside();
            case COLD -> level.getBiome(pos).value().coldEnoughToSnow(pos, level.getSeaLevel()) || near(level, pos, 2, NearCheck.FROZEN);
            case HEAT -> level.dimension() == Level.NETHER || near(level, pos, 4, NearCheck.LAVA);
            case NIGHT -> level.isDarkOutside();
        };
    }

    private enum NearCheck {
        FROZEN, LAVA
    }

    private static boolean near(BlockGetter level, BlockPos pos, int radius, NearCheck check) {
        for (BlockPos at : BlockPos.betweenClosed(pos.offset(-radius, -1, -radius), pos.offset(radius, 1, radius))) {
            BlockState state = level.getBlockState(at);
            boolean match = check == NearCheck.LAVA
                    ? state.getFluidState().is(FluidTags.LAVA)
                    : state.is(BlockTags.ICE) || state.is(Blocks.SNOW) || state.is(Blocks.SNOW_BLOCK) || state.is(Blocks.POWDER_SNOW);
            if (match) {
                return true;
            }
        }
        return false;
    }
}
