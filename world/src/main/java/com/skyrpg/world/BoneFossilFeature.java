package com.skyrpg.world;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.feature.Feature;

/**
 * The Bone Sea's giant skeletons, half-buried in the dunes: {@code {"type": "skyrpg_world:bone_fossil"}}, placed on a
 * floor (the position is the air just above it). Three times in five a ribcage: a spine of bone blocks with arching ribs
 * that curl back under it, sunk a block or two into the ground, sometimes with a small skull at its head. Otherwise a
 * giant skull: a hollow bone shell with two eye sockets, a nose hole and a row of teeth, soul fire burning inside so the
 * eyes glow. Bones only replace air and loose natural ground, and nothing generates in chunks with a structure.
 */
public record BoneFossilFeature() implements Feature {
    public static final MapCodec<BoneFossilFeature> CODEC = MapCodec.unit(new BoneFossilFeature());

    @Override
    public MapCodec<BoneFossilFeature> codec() {
        return CODEC;
    }

    @Override
    public boolean place(WorldGenLevel level, ChunkGenerator generator, RandomSource random, BlockPos origin) {
        if (level.getLevel().dimension() != Level.NETHER || NetherGen.nearStructure(level, origin)) {
            return false;
        }
        Direction facing = Direction.Plane.HORIZONTAL.getRandomDirection(random);
        if (random.nextFloat() < 0.6f) {
            return ribcage(level, random, origin, facing);
        }
        int radius = 3 + random.nextInt(2);
        return skull(level, random, origin, origin.below(1 + random.nextInt(2)).above(radius - 1), facing, radius);
    }

    /** A spine along {@code facing} with ribs every other block, the biggest in the middle; head at the facing end. */
    private static boolean ribcage(WorldGenLevel level, RandomSource random, BlockPos origin, Direction facing) {
        int length = 9 + random.nextInt(9);
        double ribRadius = 3.0 + random.nextDouble() * 1.6;
        int sink = 1 + random.nextInt(2);
        Direction side = facing.getClockWise();
        BlockPos tail = origin.relative(facing, -length / 2);
        int spineY = origin.getY() + (int) Math.round(ribRadius) - sink;
        BlockState spine = Blocks.BONE_BLOCK.defaultBlockState().setValue(RotatedPillarBlock.AXIS, facing.getAxis());
        BlockState rib = Blocks.BONE_BLOCK.defaultBlockState();
        int placed = 0;
        for (int i = 0; i < length; i++) {
            double t = i / (double) (length - 1);
            // The spine arches a little and its tail end dips into the ground.
            int y = spineY + (int) Math.round(Math.sin(t * Math.PI) * 1.2) - (i < 3 ? 3 - i : 0);
            BlockPos at = new BlockPos(tail.getX(), y, tail.getZ()).relative(facing, i);
            placed += set(level, origin, at, spine);
            if (i < 2 || i > length - 3 || i % 2 != 0) {
                continue;
            }
            double r = Math.max(1.6, ribRadius * Math.pow(Math.sin(t * Math.PI), 0.6));
            // Each rib: out and down from the spine on both sides, curling back in below it (an ellipse 140 degrees round).
            for (int s = -1; s <= 1; s += 2) {
                for (double angle = 0.15; angle <= Math.toRadians(140); angle += 0.12) {
                    int out = (int) Math.round(Math.sin(angle) * r);
                    int down = (int) Math.round((1 - Math.cos(angle)) * r * 0.9);
                    placed += set(level, origin, at.relative(side, s * out).below(down), rib);
                }
            }
        }
        if (random.nextBoolean()) {
            BlockPos head = new BlockPos(tail.getX(), spineY, tail.getZ()).relative(facing, length + 1);
            skull(level, random, origin, head, facing, 2);
        }
        return placed > 0;
    }

    /** A hollow skull of bone around {@code center}, looking along {@code facing}. */
    private static boolean skull(WorldGenLevel level, RandomSource random, BlockPos origin, BlockPos center, Direction facing, int radius) {
        Direction side = facing.getClockWise();
        double rx = radius + 0.5;
        double ry = radius - 0.2;
        double rf = radius + 1.0;
        BlockState bone = Blocks.BONE_BLOCK.defaultBlockState();
        int placed = 0;
        int reach = radius + 2;
        for (int f = -reach; f <= reach; f++) {
            for (int u = -reach; u <= reach; u++) {
                for (int s = -reach; s <= reach; s++) {
                    double d = Math.sqrt((s / rx) * (s / rx) + (u / ry) * (u / ry) + (f / rf) * (f / rf));
                    if (d > 1.0) {
                        continue;
                    }
                    BlockPos at = center.relative(facing, f).relative(side, s).above(u);
                    boolean shell = d > 1.0 - 1.3 / radius;
                    // Eye sockets and the nose on the front, the open jaw below them.
                    boolean front = f >= rf - 2.2;
                    boolean eye = front && u >= 0 && u <= 1 && Math.abs(Math.abs(s) - radius / 2.0) <= 0.75;
                    boolean nose = front && u == -1 && s == 0;
                    boolean jaw = f > 0 && u <= -radius + 1 && u >= -radius && (s + f) % 2 != 0;
                    if (shell && !eye && !nose && !jaw) {
                        placed += set(level, origin, at, bone);
                    } else {
                        placed += clear(level, origin, at);
                    }
                }
            }
        }
        // Soul fire on the lowest hollow block inside (on soul soil set into the shell), so the sockets glow blue.
        if (radius > 2 && placed > 0) {
            BlockPos fire = center.below((int) Math.floor(ry * (1.0 - 1.3 / radius)));
            if (NetherGen.writable(origin, fire) && level.isInsideBuildHeight(fire.getY() - 1) && level.getBlockState(fire).isAir()
                    && level.getBlockState(fire.below()).is(Blocks.BONE_BLOCK)) {
                level.setBlock(fire.below(), Blocks.SOUL_SOIL.defaultBlockState(), Block.UPDATE_CLIENTS);
                level.setBlock(fire, Blocks.SOUL_FIRE.defaultBlockState(), Block.UPDATE_CLIENTS);
            }
        }
        return placed > 0;
    }

    private static int set(WorldGenLevel level, BlockPos origin, BlockPos pos, BlockState state) {
        if (!NetherGen.writable(origin, pos) || !level.isInsideBuildHeight(pos.getY()) || !NetherGen.replaceable(level.getBlockState(pos))) {
            return 0;
        }
        level.setBlock(pos, state, Block.UPDATE_CLIENTS);
        return 1;
    }

    /** Hollows the skull out (only loose ground: a skull buried in solid rock keeps its rock). */
    private static int clear(WorldGenLevel level, BlockPos origin, BlockPos pos) {
        if (!NetherGen.writable(origin, pos) || !level.isInsideBuildHeight(pos.getY())) {
            return 0;
        }
        BlockState state = level.getBlockState(pos);
        if (state.isAir() || !NetherGen.replaceable(state)) {
            return 0;
        }
        level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
        return 0;
    }
}
