package com.skyrpg.builder;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FlowerPotBlock;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.block.state.properties.SlabType;

/**
 * What each block of a blueprint costs. Most blocks cost their own item; doors, beds and tall plants are paid for on
 * one half; double slabs cost two; candles, sea pickles, petals and snow layers cost one per piece; crops cost their
 * seeds; still water and lava cost a full bucket (the empty bucket comes back). Grass, paths and farmland take plain
 * dirt. Blocks nobody can get in survival (spawners, bedrock, portals, command blocks...) are skipped, and so is
 * anything from other mods unless it's tagged {@code skyrpg_builder:buildable}.
 */
public final class Materials {
    public static final TagKey<Block> BUILDABLE = TagKey.create(Registries.BLOCK, SkyRpgBuilder.id("buildable"));
    public static final TagKey<Block> UNBUILDABLE = TagKey.create(Registries.BLOCK, SkyRpgBuilder.id("unbuildable"));

    public record Need(Item item, int count) {
    }

    private static final Set<Block> NEVER = Set.of(Blocks.BEDROCK, Blocks.BARRIER, Blocks.LIGHT, Blocks.COMMAND_BLOCK, Blocks.CHAIN_COMMAND_BLOCK,
            Blocks.REPEATING_COMMAND_BLOCK, Blocks.STRUCTURE_BLOCK, Blocks.STRUCTURE_VOID, Blocks.JIGSAW, Blocks.SPAWNER, Blocks.TRIAL_SPAWNER,
            Blocks.VAULT, Blocks.END_PORTAL_FRAME, Blocks.END_PORTAL, Blocks.END_GATEWAY, Blocks.NETHER_PORTAL, Blocks.REINFORCED_DEEPSLATE,
            Blocks.BUDDING_AMETHYST, Blocks.PETRIFIED_OAK_SLAB, Blocks.FIRE, Blocks.SOUL_FIRE, Blocks.PISTON_HEAD, Blocks.MOVING_PISTON,
            Blocks.BUBBLE_COLUMN, Blocks.FROSTED_ICE, Blocks.CHORUS_PLANT, Blocks.PLAYER_HEAD, Blocks.PLAYER_WALL_HEAD, Blocks.DRAGON_EGG,
            Blocks.INFESTED_STONE, Blocks.INFESTED_COBBLESTONE, Blocks.INFESTED_STONE_BRICKS, Blocks.INFESTED_MOSSY_STONE_BRICKS,
            Blocks.INFESTED_CRACKED_STONE_BRICKS, Blocks.INFESTED_CHISELED_STONE_BRICKS, Blocks.INFESTED_DEEPSLATE);
    private static final Set<Block> TAKES_DIRT = Set.of(Blocks.GRASS_BLOCK, Blocks.DIRT_PATH, Blocks.FARMLAND, Blocks.MYCELIUM);
    private static final List<IntegerProperty> PIECES = List.of(BlockStateProperties.CANDLES, BlockStateProperties.PICKLES, BlockStateProperties.EGGS,
            BlockStateProperties.FLOWER_AMOUNT, BlockStateProperties.SEGMENT_AMOUNT, BlockStateProperties.LAYERS);

    private static final Map<BlockState, List<Need>> CACHE = new ConcurrentHashMap<>();

    private Materials() {
    }

    /** Whether the builder places this block at all (it shows up as "skipped" when not). */
    public static boolean buildable(BlockState state) {
        if (state.isAir() || NEVER.contains(state.getBlock()) || state.is(UNBUILDABLE)) {
            return false;
        }
        if (state.getBlock() instanceof LiquidBlock) {
            return state.getValue(LiquidBlock.LEVEL) == 0 && (state.is(Blocks.WATER) || state.is(Blocks.LAVA));
        }
        String namespace = BuiltInRegistries.BLOCK.getKey(state.getBlock()).getNamespace();
        return namespace.equals("minecraft") || state.is(BUILDABLE);
    }

    /** What placing this (already transformed) block takes from the station. Empty means free (the other half of a door...). */
    public static List<Need> needs(ServerLevel level, BlockState state) {
        return CACHE.computeIfAbsent(state, s -> compute(level, s));
    }

    /** What placing this block gives back (the empty bucket), or null. */
    public static Item returns(BlockState state) {
        return state.is(Blocks.WATER) || state.is(Blocks.LAVA) ? Items.BUCKET : null;
    }

    private static List<Need> compute(ServerLevel level, BlockState state) {
        if (!buildable(state)) {
            return List.of();
        }
        if (state.is(Blocks.WATER)) {
            return List.of(new Need(Items.WATER_BUCKET, 1));
        }
        if (state.is(Blocks.LAVA)) {
            return List.of(new Need(Items.LAVA_BUCKET, 1));
        }
        if (state.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF) && state.getValue(BlockStateProperties.DOUBLE_BLOCK_HALF) == DoubleBlockHalf.UPPER) {
            return List.of();
        }
        if (state.hasProperty(BlockStateProperties.BED_PART) && state.getValue(BlockStateProperties.BED_PART) == BedPart.HEAD) {
            return List.of();
        }
        if (TAKES_DIRT.contains(state.getBlock())) {
            return List.of(new Need(Items.DIRT, 1));
        }
        if (state.getBlock() instanceof FlowerPotBlock pot) {
            List<Need> needs = new ArrayList<>();
            needs.add(new Need(Items.FLOWER_POT, 1));
            Item plant = pot.getPotted().asItem();
            if (plant != Items.AIR) {
                needs.add(new Need(plant, 1));
            }
            return List.copyOf(needs);
        }
        String path = BuiltInRegistries.BLOCK.getKey(state.getBlock()).getPath();
        if (path.endsWith("candle_cake")) {
            // "white_candle_cake" is a cake with a white candle on it.
            Item candle = BuiltInRegistries.ITEM.getValue(net.minecraft.resources.Identifier.withDefaultNamespace(
                    path.substring(0, path.length() - "_cake".length())));
            return List.of(new Need(Items.CAKE, 1), new Need(candle == Items.AIR ? Items.CANDLE : candle, 1));
        }
        Item item = state.getBlock().asItem();
        if (item == Items.AIR) {
            try {
                ItemStack picked = state.getCloneItemStack(level, BlockPos.ZERO, false);
                item = picked.isEmpty() ? Items.AIR : picked.getItem();
            } catch (RuntimeException e) {
                item = Items.AIR;
            }
        }
        if (item == Items.AIR) {
            return List.of();
        }
        int count = 1;
        if (state.hasProperty(BlockStateProperties.SLAB_TYPE) && state.getValue(BlockStateProperties.SLAB_TYPE) == SlabType.DOUBLE) {
            count = 2;
        }
        for (IntegerProperty pieces : PIECES) {
            if (state.hasProperty(pieces)) {
                count = Math.max(1, state.getValue(pieces));
            }
        }
        return List.of(new Need(item, count));
    }
}
