package com.skyrpg.foraging;

import java.util.Locale;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.MapColor;

/**
 * The five SkyRPG woods, from the first hard wood to the rarest. Each grows from its sapling only where its
 * {@link Condition} holds, into its own tree {@link TreeShapes.Shape}. Which axe tier each needs is data (core's
 * {@code tool_tier/<n>} block tags), and so are its XP, prices and unlock levels.
 */
public enum Wood {
    IRONBARK(Condition.ANYWHERE, TreeShapes.Shape.ROUND, MapColor.COLOR_GRAY, MapColor.TERRACOTTA_GRAY, MapColor.COLOR_GREEN, 0),
    MISTPINE(Condition.COLD, TreeShapes.Shape.CONE, MapColor.TERRACOTTA_WHITE, MapColor.COLOR_LIGHT_GRAY, MapColor.COLOR_CYAN, 0),
    DUSKWILLOW(Condition.DUSK_WATER, TreeShapes.Shape.WILLOW, MapColor.COLOR_PURPLE, MapColor.TERRACOTTA_PURPLE, MapColor.COLOR_MAGENTA, 0),
    EMBERWOOD(Condition.HEAT, TreeShapes.Shape.CROWN, MapColor.COLOR_ORANGE, MapColor.TERRACOTTA_RED, MapColor.COLOR_RED, 4),
    STARWOOD(Condition.STARLIGHT, TreeShapes.Shape.GRAND, MapColor.QUARTZ, MapColor.COLOR_LIGHT_BLUE, MapColor.COLOR_LIGHT_BLUE, 9);

    /** Where a sapling may grow. */
    public enum Condition {
        ANYWHERE("anywhere with enough light"),
        COLD("in the cold: a cold biome, or above y 120"),
        DUSK_WATER("near water (4 blocks), and only at night"),
        HEAT("near lava (6 blocks), or in the Nether"),
        STARLIGHT("under open sky, and only at night");

        private final String description;

        Condition(String description) {
            this.description = description;
        }

        public String description() {
            return description;
        }

        /** Grows only at night (so it can't need daylight). */
        public boolean atNight() {
            return this == DUSK_WATER || this == STARLIGHT;
        }

        boolean holds(Level level, BlockPos pos) {
            return switch (this) {
                case ANYWHERE -> true;
                case COLD -> pos.getY() > 120 || level.getBiome(pos).value().coldEnoughToSnow(pos, level.getSeaLevel());
                case DUSK_WATER -> level.isDarkOutside() && near(level, pos, 4, false);
                case HEAT -> level.dimension() == Level.NETHER || near(level, pos, 6, true);
                case STARLIGHT -> level.isDarkOutside() && level.canSeeSky(pos.above());
            };
        }

        private static boolean near(Level level, BlockPos pos, int radius, boolean lava) {
            for (BlockPos at : BlockPos.betweenClosed(pos.offset(-radius, -2, -radius), pos.offset(radius, 1, radius))) {
                if (level.getFluidState(at).is(lava ? FluidTags.LAVA : FluidTags.WATER)) {
                    return true;
                }
            }
            return false;
        }
    }

    private final Condition condition;
    private final TreeShapes.Shape shape;
    private final MapColor top;
    private final MapColor side;
    private final MapColor leaves;
    private final int leafLight;

    Wood(Condition condition, TreeShapes.Shape shape, MapColor top, MapColor side, MapColor leaves, int leafLight) {
        this.condition = condition;
        this.shape = shape;
        this.top = top;
        this.side = side;
        this.leaves = leaves;
        this.leafLight = leafLight;
    }

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    public Condition condition() {
        return condition;
    }

    public TreeShapes.Shape shape() {
        return shape;
    }

    public MapColor top() {
        return top;
    }

    public MapColor side() {
        return side;
    }

    public MapColor leafColor() {
        return leaves;
    }

    /** Emberwood grows by lava, so it can't burn. */
    public boolean fireproof() {
        return this == EMBERWOOD;
    }

    /** Light the leaves give off (Emberwood smoulders, Starwood shines). */
    public int leafLight() {
        return leafLight;
    }
}
