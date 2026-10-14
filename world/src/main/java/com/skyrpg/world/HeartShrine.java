package com.skyrpg.world;

import com.google.gson.JsonObject;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * The shrine at a region's heart: a round platform of the region's own stone with a ruined low wall, four pillars with
 * lanterns, and an altar with the heart crystal on it. Built a chunk at a time (only the columns in the chunk being
 * generated), from nothing but the region's centre and the terrain height there, so every chunk agrees on the shape.
 * A Heart Sigil is used within a couple of dozen blocks of the altar; see {@link RegionBosses}.
 */
final class HeartShrine {
    /** The shrine reaches this far from the centre. */
    static final int RADIUS = 8;
    private static final double PLATFORM = 7.5;
    private static final int CLEARANCE = 14;
    private static final int SUPPORT = 14;

    private HeartShrine() {
    }

    /** Builds the part of this region's shrine that lies in the chunk starting at (minX, minZ). Returns whether anything was placed. */
    static boolean build(WorldGenLevel level, ChunkGenerator generator, RegionShape region, DangerRegions.Type type, int minX, int minZ) {
        if (region.cx() + RADIUS < minX || region.cx() - RADIUS > minX + 15 || region.cz() + RADIUS < minZ || region.cz() - RADIUS > minZ + 15) {
            return false;
        }
        JsonObject shrine = type.shrine();
        BlockState floor = block(shrine, "floor", Blocks.STONE_BRICKS);
        BlockState accent = block(shrine, "accent", Blocks.CRACKED_STONE_BRICKS);
        BlockState wall = block(shrine, "wall", Blocks.COBBLESTONE);
        BlockState pillar = block(shrine, "pillar", Blocks.STONE_BRICKS);
        BlockState light = block(shrine, "light", Blocks.LANTERN);
        BlockState heart = block(shrine, "heart", Blocks.CRYING_OBSIDIAN);
        // The ground at the heart, the same from every chunk: the first free block above the terrain at the centre.
        int ground = generator.getBaseHeight(region.cx(), region.cz(), Heightmap.Types.WORLD_SURFACE_WG, level,
                level.getLevel().getChunkSource().randomState());
        int floorY = ground - 1;
        boolean placed = false;
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int x = Math.max(minX, region.cx() - RADIUS); x <= Math.min(minX + 15, region.cx() + RADIUS); x++) {
            for (int z = Math.max(minZ, region.cz() - RADIUS); z <= Math.min(minZ + 15, region.cz() + RADIUS); z++) {
                int dx = x - region.cx();
                int dz = z - region.cz();
                double distance = Math.sqrt(dx * dx + dz * dz);
                if (distance > PLATFORM) {
                    continue;
                }
                // Air above, so trees and slopes don't bury it.
                for (int y = floorY + 1; y <= floorY + CLEARANCE; y++) {
                    cursor.set(x, y, z);
                    if (!level.getBlockState(cursor).isAir()) {
                        level.setBlock(cursor, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
                    }
                }
                // The floor, and stone down to the ground so it never hangs over a hollow.
                boolean altarStep = Math.abs(dx) <= 1 && Math.abs(dz) <= 1;
                boolean checker = distance <= 3.5 && (dx + dz & 1) == 0 || hash(x, z) % 7 == 0;
                level.setBlock(cursor.set(x, floorY, z), altarStep || checker ? accent : floor, Block.UPDATE_CLIENTS);
                for (int y = floorY - 1; y >= floorY - SUPPORT; y--) {
                    cursor.set(x, y, z);
                    BlockState below = level.getBlockState(cursor);
                    if (!below.isAir() && below.getFluidState().isEmpty() && below.isSolid()) {
                        break;
                    }
                    level.setBlock(cursor, wall, Block.UPDATE_CLIENTS);
                }
                // A ruined low wall around the edge, open at the four ways in.
                if (distance >= 6.2 && Math.abs(dx) > 1 && Math.abs(dz) > 1) {
                    int height = (int) (hash(x * 31, z * 17) % 4);
                    for (int h = 1; h <= height; h++) {
                        level.setBlock(cursor.set(x, floorY + h, z), h == height && hash(x, z * 3) % 3 == 0 ? accent : wall, Block.UPDATE_CLIENTS);
                    }
                }
                // Four pillars with lights on top.
                if (Math.abs(dx) == 4 && Math.abs(dz) == 4) {
                    for (int h = 1; h <= 5; h++) {
                        level.setBlock(cursor.set(x, floorY + h, z), pillar, Block.UPDATE_CLIENTS);
                    }
                    level.setBlock(cursor.set(x, floorY + 6, z), light, Block.UPDATE_CLIENTS);
                }
                // The altar: a step, a plinth and the heart.
                if (altarStep) {
                    level.setBlock(cursor.set(x, floorY + 1, z), wall, Block.UPDATE_CLIENTS);
                }
                if (dx == 0 && dz == 0) {
                    level.setBlock(cursor.set(x, floorY + 2, z), pillar, Block.UPDATE_CLIENTS);
                    level.setBlock(cursor.set(x, floorY + 3, z), heart, Block.UPDATE_CLIENTS);
                }
                placed = true;
            }
        }
        return placed;
    }

    private static BlockState block(JsonObject shrine, String key, Block fallback) {
        return BuiltInRegistries.BLOCK.getOptional(Identifier.parse(Dungeons.str(shrine, key, BuiltInRegistries.BLOCK.getKey(fallback).toString())))
                .orElse(fallback).defaultBlockState();
    }

    private static long hash(int x, int z) {
        long h = x * 0x9E3779B97F4A7C15L ^ z * 0xC2B2AE3D27D4EB4FL;
        h ^= h >>> 29;
        h *= 0xBF58476D1CE4E5B9L;
        h ^= h >>> 32;
        return h & Long.MAX_VALUE;
    }
}
