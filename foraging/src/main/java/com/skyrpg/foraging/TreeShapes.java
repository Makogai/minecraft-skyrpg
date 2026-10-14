package com.skyrpg.foraging;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The trees of the five woods, built block by block: a trunk that needs room, then leaves wherever there's space, each
 * given its real distance to the trunk so it never decays. Saplings grow them, and so does world generation (see
 * {@link SkyTreeFeature}).
 */
public final class TreeShapes {
    /** A tree's silhouette. */
    public enum Shape {
        /** Ironbark: a stout oak-like tree with a round crown. */
        ROUND,
        /** Mistpine: tall and narrow, in alternating rings like a spruce. */
        CONE,
        /** Duskwillow: a wide dome with strands of leaves hanging from its edge. */
        WILLOW,
        /** Emberwood: a trunk that leans to one side under a flat crown. */
        CROWN,
        /** Starwood: tall, with two branches and a big round crown. */
        GRAND
    }

    /** What a tree would place: logs (with their axis) and leaves. */
    private static final class Plan {
        final Map<BlockPos, Direction.Axis> logs = new LinkedHashMap<>();
        final Set<BlockPos> leaves = new LinkedHashSet<>();

        void log(BlockPos pos, Direction.Axis axis) {
            logs.put(pos.immutable(), axis);
        }

        void leaf(BlockPos pos) {
            leaves.add(pos.immutable());
        }

        void trunk(BlockPos base, int height) {
            for (int y = 0; y < height; y++) {
                log(base.above(y), Direction.Axis.Y);
            }
        }

        /** A roundish disc of leaves; corners are left out at random when {@code ragged}. */
        void disc(BlockPos center, int radius, boolean ragged, RandomSource random) {
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    int d = dx * dx + dz * dz;
                    if (d > radius * radius + radius || ragged && d == 2 * radius * radius && random.nextBoolean()) {
                        continue;
                    }
                    leaf(center.offset(dx, 0, dz));
                }
            }
        }
    }

    private TreeShapes() {
    }

    /**
     * Grows {@code wood}'s tree at {@code pos} (a sapling, or open ground in world generation); false, and nothing changes,
     * if there isn't room. {@code flags} are the block update flags.
     */
    static boolean grow(WorldGenLevel level, BlockPos pos, Wood wood, RandomSource random, int flags) {
        Plan plan = new Plan();
        switch (wood.shape()) {
            case ROUND -> round(plan, pos, random);
            case CONE -> cone(plan, pos, random);
            case WILLOW -> willow(plan, pos, random);
            case CROWN -> crown(plan, pos, random);
            case GRAND -> grand(plan, pos, random);
        }
        for (BlockPos log : plan.logs.keySet()) {
            if (!log.equals(pos) && (!level.isInsideBuildHeight(log) || !free(level, log))) {
                return false;
            }
        }
        BlockState logState = ForagingBlocks.log(wood).defaultBlockState();
        plan.logs.forEach((at, axis) -> level.setBlock(at, logState.setValue(RotatedPillarBlock.AXIS, axis), flags));
        BlockState leaves = ForagingBlocks.leaves(wood).defaultBlockState();
        Map<BlockPos, Integer> distances = distances(plan);
        for (BlockPos at : plan.leaves) {
            Integer distance = distances.get(at);
            if (distance != null && !plan.logs.containsKey(at) && level.isInsideBuildHeight(at) && free(level, at)
                    && !level.getBlockState(at).is(BlockTags.LEAVES)) {
                level.setBlock(at, leaves.setValue(LeavesBlock.DISTANCE, distance), flags);
            }
        }
        return true;
    }

    /** Each leaf's distance to the nearest log through other leaves, as vanilla counts it; leaves 7 or more away are left out. */
    private static Map<BlockPos, Integer> distances(Plan plan) {
        Map<BlockPos, Integer> distances = new HashMap<>();
        Deque<BlockPos> queue = new ArrayDeque<>();
        for (BlockPos log : plan.logs.keySet()) {
            distances.put(log, 0);
            queue.add(log);
        }
        while (!queue.isEmpty()) {
            BlockPos at = queue.poll();
            int next = distances.get(at) + 1;
            if (next >= LeavesBlock.DECAY_DISTANCE) {
                continue;
            }
            for (Direction direction : Direction.values()) {
                BlockPos neighbour = at.relative(direction);
                if (plan.leaves.contains(neighbour) && !distances.containsKey(neighbour)) {
                    distances.put(neighbour, next);
                    queue.add(neighbour);
                }
            }
        }
        return distances;
    }

    /** Air, plants, leaves or our own sapling: what a growing tree may replace. */
    private static boolean free(LevelReader level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        return state.isAir() || state.canBeReplaced() || state.is(BlockTags.LEAVES) || state.getBlock() instanceof SkySaplingBlock;
    }

    private static void round(Plan plan, BlockPos pos, RandomSource random) {
        int height = 5 + random.nextInt(3);
        plan.trunk(pos, height);
        BlockPos top = pos.above(height - 1);
        plan.disc(top.below(2), 2, true, random);
        plan.disc(top.below(), 2, true, random);
        plan.disc(top, 1, false, random);
        plan.leaf(top.above());
        for (Direction side : Direction.Plane.HORIZONTAL) {
            plan.leaf(top.above().relative(side));
        }
    }

    private static void cone(Plan plan, BlockPos pos, RandomSource random) {
        int height = 8 + random.nextInt(4);
        plan.trunk(pos, height);
        plan.leaf(pos.above(height));
        plan.leaf(pos.above(height + 1));
        for (int y = height - 1; y >= 3; y--) {
            int fromTop = height - 1 - y;
            int ring = Math.min(3, 1 + fromTop / 2);
            plan.disc(pos.above(y), fromTop % 2 == 0 ? ring : ring - 1, true, random);
        }
    }

    private static void willow(Plan plan, BlockPos pos, RandomSource random) {
        int height = 5 + random.nextInt(2);
        plan.trunk(pos, height);
        BlockPos top = pos.above(height - 1);
        plan.disc(top, 3, true, random);
        plan.disc(top.above(), 2, true, random);
        plan.disc(top.above(2), 1, false, random);
        // Strands hanging from the rim of the dome.
        for (int dx = -3; dx <= 3; dx++) {
            for (int dz = -3; dz <= 3; dz++) {
                int d = dx * dx + dz * dz;
                if (d >= 5 && d <= 12 && random.nextInt(5) < 3) {
                    int length = 1 + random.nextInt(3);
                    for (int i = 1; i <= length; i++) {
                        plan.leaf(top.offset(dx, -i, dz));
                    }
                }
            }
        }
    }

    private static void crown(Plan plan, BlockPos pos, RandomSource random) {
        Direction lean = Direction.Plane.HORIZONTAL.getRandomDirection(random);
        int straight = 3 + random.nextInt(2);
        plan.trunk(pos, straight);
        BlockPos at = pos.above(straight - 1);
        for (int i = 0; i < 2; i++) {
            at = at.relative(lean).above();
            plan.log(at, Direction.Axis.Y);
        }
        plan.disc(at.above(), 3, true, random);
        plan.disc(at.above(2), 1, false, random);
    }

    private static void grand(Plan plan, BlockPos pos, RandomSource random) {
        int height = 7 + random.nextInt(3);
        plan.trunk(pos, height);
        Direction first = Direction.Plane.HORIZONTAL.getRandomDirection(random);
        for (Direction branch : new Direction[] {first, first.getOpposite()}) {
            for (int i = 1; i <= 2; i++) {
                plan.log(pos.above(height - 3).relative(branch, i), branch.getAxis());
            }
        }
        BlockPos center = pos.above(height - 1);
        for (int dx = -3; dx <= 3; dx++) {
            for (int dy = -2; dy <= 2; dy++) {
                for (int dz = -3; dz <= 3; dz++) {
                    double d = dx * dx + dy * dy * 1.6 + dz * dz;
                    if (d <= 10.5 && !(d > 7.5 && random.nextInt(3) == 0)) {
                        plan.leaf(center.offset(dx, dy, dz));
                    }
                }
            }
        }
        plan.leaf(center.above(3));
    }
}
