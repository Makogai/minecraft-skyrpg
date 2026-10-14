package com.skyrpg.world;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.feature.Feature;

/**
 * Corrupts the land inside danger regions as chunks generate (after trees, so the canopy changes too): every block in
 * a column from the treetops down to just under the ground goes through the region's palette, the edge frays out
 * over a dozen blocks, and the region's decorations (crystal clusters, spikes, patches) are scattered on top.
 * Added to every overworld biome at the top-layer step; outside regions it does nothing.
 */
public record DangerRegionFeature() implements Feature {
    public static final MapCodec<DangerRegionFeature> CODEC = MapCodec.unit(new DangerRegionFeature());
    private static final int BLEND = 12;
    private static final int BELOW_GROUND = 4;
    /** How far above the ground treetops can reach. */
    private static final int CANOPY = 40;

    @Override
    public MapCodec<DangerRegionFeature> codec() {
        return CODEC;
    }

    @Override
    public boolean place(WorldGenLevel level, ChunkGenerator generator, RandomSource random, BlockPos origin) {
        ServerLevel server = level.getLevel();
        if (server.dimension() != net.minecraft.world.level.Level.OVERWORLD) {
            return false;
        }
        ChunkPos chunk = ChunkPos.containing(origin);
        int minX = chunk.getMinBlockX();
        int minZ = chunk.getMinBlockZ();
        boolean changed = false;
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        RegionShape centre = DangerRegions.at(server, minX + 8, minZ + 8);
        for (int dx = 0; dx < 16; dx++) {
            for (int dz = 0; dz < 16; dz++) {
                int x = minX + dx;
                int z = minZ + dz;
                RegionShape region = DangerRegions.at(server, x, z);
                if (region == null) {
                    continue;
                }
                double depth = region.depth(x, z);
                // A frayed edge: past the blend zone everything changes, inside it fewer and fewer blocks do.
                if (depth < BLEND && random.nextDouble() > depth / BLEND) {
                    continue;
                }
                DangerRegions.Type type = DangerRegions.type(region.type());
                if (type == null) {
                    continue;
                }
                // The worldgen heightmaps don't count trees at this step, so start well above the ground.
                int ground = level.getHeight(Heightmap.Types.OCEAN_FLOOR_WG, x, z);
                int top = Math.min(level.getMaxY(), ground + CANOPY);
                int bottom = Math.max(level.getMinY(), ground - BELOW_GROUND);
                for (int y = top; y >= bottom; y--) {
                    cursor.set(x, y, z);
                    BlockState state = level.getBlockState(cursor);
                    if (state.isAir()) {
                        continue;
                    }
                    BlockState to = type.convert(state);
                    if (to != null) {
                        level.setBlock(cursor, to, Block.UPDATE_CLIENTS);
                        changed = true;
                    }
                }
            }
        }
        // The heart shrine of any region whose centre is close enough to reach into this chunk.
        for (RegionShape region : DangerRegions.around(server, minX + 8, minZ + 8, 1)) {
            DangerRegions.Type type = DangerRegions.type(region.type());
            if (type != null && !type.shrine().isEmpty()) {
                changed |= HeartShrine.build(level, generator, region, type, minX, minZ);
            }
        }
        if (centre != null && centre.depth(minX + 8, minZ + 8) > BLEND) {
            DangerRegions.Type type = DangerRegions.type(centre.type());
            if (type != null) {
                for (JsonObject decoration : type.decorations()) {
                    if (random.nextDouble() < Dungeons.num(decoration, "chance", 0.2)) {
                        changed |= decorate(level, random, minX, minZ, decoration);
                    }
                }
            }
        }
        return changed;
    }

    private static boolean decorate(WorldGenLevel level, RandomSource random, int minX, int minZ, JsonObject decoration) {
        Block block = BuiltInRegistries.BLOCK.getOptional(Identifier.parse(Dungeons.str(decoration, "block", "minecraft:air"))).orElse(null);
        if (block == null) {
            return false;
        }
        int count = (int) Dungeons.num(decoration, "count", 1);
        boolean placed = false;
        for (int i = 0; i < count; i++) {
            String kind = Dungeons.str(decoration, "type", "scatter");
            if (kind.equals("islet")) {
                placed |= islet(level, random, minX, minZ, decoration, block);
                continue;
            }
            if (kind.equals("ore")) {
                placed |= ore(level, random, minX, minZ, block);
                continue;
            }
            // Keep a margin so everything stays inside this chunk.
            int x = minX + 2 + random.nextInt(12);
            int z = minZ + 2 + random.nextInt(12);
            int y = level.getHeight(Heightmap.Types.WORLD_SURFACE_WG, x, z);
            BlockPos base = new BlockPos(x, y, z);
            if (!level.getBlockState(base.below()).isFaceSturdy(level, base.below(), Direction.UP) || !level.getFluidState(base.below()).isEmpty()) {
                continue;
            }
            switch (Dungeons.str(decoration, "type", "scatter")) {
                case "spike" -> {
                    int height = range(decoration, "height", 3, 6, random);
                    Block tip = BuiltInRegistries.BLOCK.getOptional(Identifier.tryParse(Dungeons.str(decoration, "tip", ""))).orElse(block);
                    for (int h = 0; h < height; h++) {
                        level.setBlock(base.above(h), (h >= height - 1 ? tip : block).defaultBlockState(), Block.UPDATE_CLIENTS);
                    }
                    // A thicker foot for the taller ones.
                    if (height >= 6) {
                        for (Direction side : Direction.Plane.HORIZONTAL) {
                            int foot = 1 + random.nextInt(Math.max(1, height / 3));
                            for (int h = 0; h < foot; h++) {
                                BlockPos pos = base.relative(side).above(h);
                                if (level.getBlockState(pos).canBeReplaced()) {
                                    level.setBlock(pos, block.defaultBlockState(), Block.UPDATE_CLIENTS);
                                }
                            }
                        }
                    }
                }
                case "headstone" -> {
                    // A crooked headstone: a short column of wall with moss at its foot.
                    int height = range(decoration, "height", 2, 3, random);
                    for (int h = 0; h < height; h++) {
                        level.setBlock(base.above(h), block.defaultBlockState(), Block.UPDATE_CLIENTS);
                    }
                    Direction side = Direction.Plane.HORIZONTAL.getRandomDirection(random);
                    if (level.getBlockState(base.relative(side)).canBeReplaced()) {
                        level.setBlock(base.relative(side), Blocks.MOSS_CARPET.defaultBlockState(), Block.UPDATE_CLIENTS);
                    }
                }
                case "grave" -> {
                    // A grave: two cells long, headstone at one end; open (dug out two deep) or a closed mound.
                    Direction along = Direction.Plane.HORIZONTAL.getRandomDirection(random);
                    BlockPos tail = base.relative(along);
                    boolean open = random.nextBoolean();
                    boolean clear = level.getFluidState(base.below()).isEmpty() && level.getFluidState(tail.below()).isEmpty()
                            && level.getBlockState(tail.below()).isFaceSturdy(level, tail.below(), Direction.UP);
                    if (!clear) {
                        continue;
                    }
                    for (BlockPos cell : new BlockPos[] {base, tail}) {
                        if (open) {
                            level.setBlock(cell.below(), Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
                            level.setBlock(cell.below(2), Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
                            level.setBlock(cell.below(3), block.defaultBlockState(), Block.UPDATE_CLIENTS);
                        } else {
                            level.setBlock(cell, block.defaultBlockState(), Block.UPDATE_CLIENTS);
                        }
                    }
                    BlockPos stone = base.relative(along.getOpposite());
                    for (int h = 0; h < 2; h++) {
                        level.setBlock(stone.above(h), Blocks.STONE_BRICK_WALL.defaultBlockState(), Block.UPDATE_CLIENTS);
                    }
                }
                case "patch" -> {
                    int radius = range(decoration, "radius", 2, 3, random);
                    for (int px = -radius; px <= radius; px++) {
                        for (int pz = -radius; pz <= radius; pz++) {
                            if (px * px + pz * pz > radius * radius + random.nextInt(2)) {
                                continue;
                            }
                            int ax = Math.clamp(x + px, minX, minX + 15);
                            int az = Math.clamp(z + pz, minZ, minZ + 15);
                            BlockPos surface = new BlockPos(ax, level.getHeight(Heightmap.Types.OCEAN_FLOOR_WG, ax, az) - 1, az);
                            if (level.getBlockState(surface).isFaceSturdy(level, surface, Direction.UP)) {
                                level.setBlock(surface, block.defaultBlockState(), Block.UPDATE_CLIENTS);
                            }
                        }
                    }
                }
                default -> {
                    if (level.getBlockState(base).canBeReplaced() && level.getFluidState(base).isEmpty()) {
                        level.setBlock(base, block.defaultBlockState(), Block.UPDATE_CLIENTS);
                    }
                }
            }
            placed = true;
        }
        return placed;
    }

    /** A floating islet: a flat disc of rock with a cone hanging under it, high above the ground. */
    private static boolean islet(WorldGenLevel level, RandomSource random, int minX, int minZ, JsonObject decoration, Block block) {
        int radius = range(decoration, "radius", 3, 6, random);
        int cx = minX + radius + random.nextInt(Math.max(1, 16 - 2 * radius));
        int cz = minZ + radius + random.nextInt(Math.max(1, 16 - 2 * radius));
        int surface = level.getHeight(Heightmap.Types.WORLD_SURFACE_WG, cx, cz);
        int top = Math.min(level.getMaxY() - 12, surface + range(decoration, "height", 14, 34, random));
        Block topBlock = BuiltInRegistries.BLOCK.getOptional(Identifier.tryParse(Dungeons.str(decoration, "top", ""))).orElse(block);
        // optional variety in the body: "mix" blocks in patches of rock, and the odd "vein" of ore
        Block mix = BuiltInRegistries.BLOCK.getOptional(Identifier.tryParse(Dungeons.str(decoration, "mix", ""))).orElse(block);
        Block vein = BuiltInRegistries.BLOCK.getOptional(Identifier.tryParse(Dungeons.str(decoration, "vein", ""))).orElse(block);
        double mixChance = decoration.has("mix_chance") ? decoration.get("mix_chance").getAsDouble() : 0;
        double veinChance = decoration.has("vein_chance") ? decoration.get("vein_chance").getAsDouble() : 0;
        int depth = radius + 2;
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        boolean placed = false;
        for (int k = 0; k <= depth; k++) {
            double r = radius * Math.pow(1 - k / (depth + 1.0), 0.8);
            for (int px = -radius; px <= radius; px++) {
                for (int pz = -radius; pz <= radius; pz++) {
                    if (px * px + pz * pz > r * r + random.nextInt(3)) {
                        continue;
                    }
                    cursor.set(cx + px, top - k, cz + pz);
                    if (level.getBlockState(cursor).isAir()) {
                        Block body = random.nextDouble() < veinChance ? vein : random.nextDouble() < mixChance ? mix : block;
                        level.setBlock(cursor, (k == 0 ? topBlock : body).defaultBlockState(), Block.UPDATE_CLIENTS);
                        placed = true;
                    }
                }
            }
        }
        return placed;
    }

    /** Ore showing at the surface: a few blocks of the ground turn into it. */
    private static boolean ore(WorldGenLevel level, RandomSource random, int minX, int minZ, Block block) {
        int x = minX + 1 + random.nextInt(14);
        int z = minZ + 1 + random.nextInt(14);
        boolean placed = false;
        for (int i = 0; i < 1 + random.nextInt(3); i++) {
            int ox = Math.clamp(x + random.nextInt(3) - 1, minX, minX + 15);
            int oz = Math.clamp(z + random.nextInt(3) - 1, minZ, minZ + 15);
            BlockPos surface = new BlockPos(ox, level.getHeight(Heightmap.Types.OCEAN_FLOOR_WG, ox, oz) - 1, oz);
            BlockState state = level.getBlockState(surface);
            if (state.isFaceSturdy(level, surface, Direction.UP) && level.getFluidState(surface.above()).isEmpty() && state.isSolid()) {
                level.setBlock(surface, block.defaultBlockState(), Block.UPDATE_CLIENTS);
                placed = true;
            }
        }
        return placed;
    }

    private static int range(JsonObject json, String key, int min, int max, RandomSource random) {
        if (json.has(key) && json.get(key).isJsonArray()) {
            JsonArray range = json.getAsJsonArray(key);
            min = range.get(0).getAsInt();
            max = Math.max(min, range.get(1).getAsInt());
        }
        return min + random.nextInt(max - min + 1);
    }
}
