package com.skyrpg.mining;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.valueproviders.UniformInt;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DropExperienceBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;

/**
 * The mining blocks: placeable ore nodes (bought from the Mining Master as you level), the new ores that generate in
 * the survival world, and Hellforged ore in the Nether. Their block items live in {@link MiningItems}.
 */
public final class MiningBlocks {
    /** Every node, with how long it takes to regrow after being mined. */
    public enum Node {
        COAL(20), IRON(30), GOLD(45), REDSTONE(40), SKYIRON(60), DUSKQUARTZ(100), DIAMOND(120), EMBERITE(150), LUMENITE(300);

        private final int respawnSeconds;

        Node(int respawnSeconds) {
            this.respawnSeconds = respawnSeconds;
        }

        public String id() {
            return name().toLowerCase(java.util.Locale.ROOT) + "_node";
        }
    }

    private static final Map<Node, NodeBlock> NODES = new LinkedHashMap<>();
    private static final Map<String, Block> BLOCKS = new LinkedHashMap<>();

    // New ores: Skyiron (common, mid depth), Emberite (rare, deep), Lumenite (very rare, the deepest layers).
    public static final Block SKYIRON_ORE = ore("skyiron_ore", false, 1, 3);
    public static final Block DEEPSLATE_SKYIRON_ORE = ore("deepslate_skyiron_ore", true, 1, 3);
    public static final Block EMBERITE_ORE = ore("emberite_ore", false, 3, 6);
    public static final Block DEEPSLATE_EMBERITE_ORE = ore("deepslate_emberite_ore", true, 3, 6);
    public static final Block LUMENITE_ORE = ore("lumenite_ore", false, 5, 9);
    public static final Block DEEPSLATE_LUMENITE_ORE = ore("deepslate_lumenite_ore", true, 5, 9);
    public static final Block DUSKQUARTZ_ORE = ore("duskquartz_ore", false, 2, 5);
    public static final Block DEEPSLATE_DUSKQUARTZ_ORE = ore("deepslate_duskquartz_ore", true, 2, 5);
    // The Nether's ore: Hellforged, in netherrack and (the blackstone variant) blackstone and basalt. Rare anywhere in the
    // Nether, common in the Ember Forest, Bone Sea and Obsidian Spires (see MiningWorldgen). Pick tier V.
    public static final Block HELLFORGED_ORE = netherOre("hellforged_ore", false, 4, 8);
    public static final Block BLACKSTONE_HELLFORGED_ORE = netherOre("blackstone_hellforged_ore", true, 4, 8);

    // Storage blocks: nine ingots (or shards) each, and good building blocks.
    public static final Block SKYIRON_BLOCK = storage("skyiron_block", MapColor.COLOR_LIGHT_BLUE);
    public static final Block EMBERITE_BLOCK = storage("emberite_block", MapColor.COLOR_ORANGE);
    public static final Block DUSKQUARTZ_BLOCK = storage("duskquartz_block", MapColor.COLOR_PURPLE);

    static {
        for (Node node : Node.values()) {
            NODES.put(node, register(node.id(), properties -> new NodeBlock(node.respawnSeconds, properties
                    .mapColor(MapColor.STONE).strength(3.0f, 1200.0f).requiresCorrectToolForDrops()
                    .sound(SoundType.DEEPSLATE).pushReaction(PushReaction.IMMOVEABLE))));
        }
    }

    private MiningBlocks() {
    }

    static void init() {
        // Loads the class so the blocks register during mod init.
    }

    public static NodeBlock node(Node node) {
        return NODES.get(node);
    }

    /** Every block this mod adds, by path, in registration order (ores first, then nodes). */
    public static Map<String, Block> all() {
        return Collections.unmodifiableMap(BLOCKS);
    }

    private static Block ore(String name, boolean deepslate, int minXp, int maxXp) {
        return register(name, properties -> new DropExperienceBlock(UniformInt.of(minXp, maxXp), properties
                .mapColor(deepslate ? MapColor.DEEPSLATE : MapColor.STONE)
                .strength(deepslate ? 4.5f : 3.0f, 3.0f).requiresCorrectToolForDrops()
                .sound(deepslate ? SoundType.DEEPSLATE : SoundType.STONE)));
    }

    private static Block netherOre(String name, boolean blackstone, int minXp, int maxXp) {
        return register(name, properties -> new DropExperienceBlock(UniformInt.of(minXp, maxXp), properties
                .mapColor(blackstone ? MapColor.COLOR_BLACK : MapColor.NETHER)
                .strength(3.0f, 3.0f).requiresCorrectToolForDrops()
                .sound(blackstone ? SoundType.GILDED_BLACKSTONE : SoundType.NETHER_ORE)));
    }

    private static Block storage(String name, MapColor color) {
        return register(name, properties -> new Block(properties.mapColor(color).strength(5.0f, 6.0f).requiresCorrectToolForDrops()
                .sound(SoundType.METAL)));
    }

    @SuppressWarnings("unchecked")
    private static <T extends Block> T register(String name, Function<BlockBehaviour.Properties, T> factory) {
        ResourceKey<Block> key = ResourceKey.create(Registries.BLOCK, SkyRpgMining.id(name));
        T block = Registry.register(BuiltInRegistries.BLOCK, key, factory.apply(BlockBehaviour.Properties.of().setId(key)));
        BLOCKS.put(name, block);
        return block;
    }
}
