package com.skyrpg.world;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.feature.Feature;

/**
 * The Obsidian Spires' spires: {@code {"type": "skyrpg_world:obsidian_spire"}}, placed on a floor (the position is the air
 * just above it). A tall, slightly leaning, tapering needle of obsidian, 10 to 37 blocks, rooted two blocks into the ground;
 * where the cave roof is lower than that it becomes a pillar from floor to ceiling, pinched in the middle. Crying obsidian
 * runs down one side in a streak, is scattered through it, and tips the free-standing ones. Two in five candidates are
 * skipped so the spires stand apart; nothing generates in chunks with a structure.
 */
public record ObsidianSpireFeature() implements Feature {
    public static final MapCodec<ObsidianSpireFeature> CODEC = MapCodec.unit(new ObsidianSpireFeature());
    private static final long CRY = 0x3C79AC492BA7B653L;
    private static final int MIN_ROOM = 8;

    @Override
    public MapCodec<ObsidianSpireFeature> codec() {
        return CODEC;
    }

    @Override
    public boolean place(WorldGenLevel level, ChunkGenerator generator, RandomSource random, BlockPos origin) {
        if (level.getLevel().dimension() != Level.NETHER || random.nextFloat() < 0.4f || NetherGen.nearStructure(level, origin)) {
            return false;
        }
        int wanted = 10 + random.nextInt(28);
        int room = 0;
        while (room < wanted + 6 && level.isInsideBuildHeight(origin.getY() + room) && level.getBlockState(origin.above(room)).isAir()) {
            room++;
        }
        if (room < MIN_ROOM) {
            return false;
        }
        boolean pillar = room < wanted + 6 && level.isInsideBuildHeight(origin.getY() + room);
        int height = pillar ? room : wanted;
        double base = 1.6 + random.nextDouble() * 1.8;
        double leanX = (random.nextDouble() - 0.5) * 0.16;
        double leanZ = (random.nextDouble() - 0.5) * 0.16;
        // The crying streak: one side of the spire (an angle), plus scattered blocks.
        double streak = random.nextDouble() * Math.PI * 2;
        long seed = level.getSeed();
        BlockState obsidian = Blocks.OBSIDIAN.defaultBlockState();
        BlockState crying = Blocks.CRYING_OBSIDIAN.defaultBlockState();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        int placed = 0;
        for (int dy = -2; dy < height; dy++) {
            double t = Math.max(0, dy) / (double) height;
            double r = pillar ? base * (0.5 + 0.5 * Math.pow(Math.abs(2 * t - 1), 1.6)) : base * Math.pow(1 - t, 0.85) + 0.35;
            r = Math.max(0.5, r);
            double cx = origin.getX() + 0.5 + leanX * Math.max(0, dy);
            double cz = origin.getZ() + 0.5 + leanZ * Math.max(0, dy);
            int y = origin.getY() + dy;
            boolean tip = !pillar && dy >= height - 2;
            int reach = (int) Math.ceil(r);
            for (int x = (int) Math.floor(cx - reach); x <= (int) Math.floor(cx + reach); x++) {
                for (int z = (int) Math.floor(cz - reach); z <= (int) Math.floor(cz + reach); z++) {
                    double ddx = x + 0.5 - cx;
                    double ddz = z + 0.5 - cz;
                    if (ddx * ddx + ddz * ddz > r * r) {
                        continue;
                    }
                    pos.set(x, y, z);
                    if (!NetherGen.writable(origin, pos) || !level.isInsideBuildHeight(y) || !NetherGen.replaceable(level.getBlockState(pos))) {
                        continue;
                    }
                    double angle = Math.atan2(ddz, ddx);
                    double off = Math.abs(Math.atan2(Math.sin(angle - streak), Math.cos(angle - streak)));
                    double roll = DeepLayerFeature.unit(DeepLayerFeature.hash(seed ^ CRY, x, y, z));
                    boolean cry = tip || roll < 0.08 || (off < 0.6 && roll < 0.55);
                    level.setBlock(pos, cry ? crying : obsidian, Block.UPDATE_CLIENTS);
                    placed++;
                }
            }
        }
        return placed > 0;
    }
}
