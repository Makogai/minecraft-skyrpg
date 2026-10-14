package com.skyrpg.foraging;

import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import net.fabricmc.fabric.api.registry.FlammableBlockRegistry;
import net.minecraft.core.Direction;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.sounds.AmbientLeavesBlockSoundPlayer;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.properties.NoteBlockInstrument;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;

/**
 * The foraging blocks: a log, planks, leaves and a sapling for each {@link Wood}, and stumps (bought from the Foraging
 * Master as you level, they regrow a log on a timer anywhere). Their block items live in {@link ForagingItems}.
 */
public final class ForagingBlocks {
    /** Every stump: the log it grows and how long that takes. Its XP is data (skill_xp), like nodes. */
    public enum Stump {
        OAK(30, MapColor.WOOD),
        BIRCH(30, MapColor.SAND),
        SPRUCE(30, MapColor.PODZOL),
        IRONBARK(60, MapColor.COLOR_GRAY),
        MISTPINE(90, MapColor.TERRACOTTA_WHITE),
        DUSKWILLOW(120, MapColor.COLOR_PURPLE),
        EMBERWOOD(180, MapColor.COLOR_ORANGE);

        private final int seconds;
        private final MapColor color;

        Stump(int seconds, MapColor color) {
            this.seconds = seconds;
            this.color = color;
        }

        public String id() {
            return name().toLowerCase(Locale.ROOT) + "_stump";
        }

        public int growTicks() {
            return seconds * 20;
        }
    }

    private static final Map<String, Block> BLOCKS = new LinkedHashMap<>();
    private static final Map<Wood, Block> LOGS = new EnumMap<>(Wood.class);
    private static final Map<Wood, Block> PLANKS = new EnumMap<>(Wood.class);
    private static final Map<Wood, Block> LEAVES = new EnumMap<>(Wood.class);
    private static final Map<Wood, SkySaplingBlock> SAPLINGS = new EnumMap<>(Wood.class);
    private static final Map<Stump, StumpBlock> STUMPS = new EnumMap<>(Stump.class);

    static {
        for (Wood wood : Wood.values()) {
            String name = wood.id();
            // Emberwood is fireproof: lava next to it is where it grows.
            LOGS.put(wood, register(name + "_log", key -> new RotatedPillarBlock(wood.fireproof()
                    ? BlockBehaviour.Properties.of().setId(key).mapColor(state -> state.getValue(RotatedPillarBlock.AXIS) == Direction.Axis.Y
                            ? wood.top() : wood.side()).instrument(NoteBlockInstrument.BASS).strength(2.0f).sound(SoundType.WOOD)
                    : Blocks.logProperties(wood.top(), wood.side(), SoundType.WOOD).setId(key))));
            PLANKS.put(wood, register(name + "_planks", key -> {
                BlockBehaviour.Properties properties = BlockBehaviour.Properties.of().setId(key)
                        .mapColor(wood.top()).instrument(NoteBlockInstrument.BASS).strength(2.0f, 3.0f).sound(SoundType.WOOD);
                return new Block(wood.fireproof() ? properties : properties.ignitedByLava());
            }));
            LEAVES.put(wood, register(name + "_leaves", key -> new LeavesBlock(AmbientLeavesBlockSoundPlayer.noAmbientSound(), (wood.fireproof()
                    ? BlockBehaviour.Properties.of().strength(0.2f).randomTicks().sound(SoundType.GRASS).noOcclusion()
                            .isValidSpawn((state, level, pos, type) -> false).isSuffocating((state, level, pos) -> false)
                            .pushReaction(PushReaction.POPPED).isRedstoneConductor((state, level, pos) -> false)
                    : Blocks.leavesProperties(SoundType.GRASS)).setId(key).mapColor(wood.leafColor()).lightLevel(state -> wood.leafLight()))));
            SAPLINGS.put(wood, register(name + "_sapling", key -> new SkySaplingBlock(wood, BlockBehaviour.Properties.of().setId(key)
                    .mapColor(MapColor.PLANT).noCollision().randomTicks().instabreak().sound(SoundType.GRASS).pushReaction(PushReaction.POPPED))));
        }
        for (Stump stump : Stump.values()) {
            STUMPS.put(stump, register(stump.id(), key -> new StumpBlock(stump, BlockBehaviour.Properties.of().setId(key)
                    .mapColor(stump.color).instrument(NoteBlockInstrument.BASS).strength(2.0f, 1200.0f).sound(SoundType.WOOD)
                    .pushReaction(PushReaction.IMMOVEABLE))));
        }
    }

    private ForagingBlocks() {
    }

    static void init() {
        // Fire spreads through our woods like vanilla's (Emberwood excepted).
        FlammableBlockRegistry fire = FlammableBlockRegistry.getDefaultInstance();
        for (Wood wood : Wood.values()) {
            if (!wood.fireproof()) {
                fire.add(LOGS.get(wood), 5, 5);
                fire.add(PLANKS.get(wood), 5, 20);
                fire.add(LEAVES.get(wood), 30, 60);
            }
        }
    }

    public static Block log(Wood wood) {
        return LOGS.get(wood);
    }

    public static Block leaves(Wood wood) {
        return LEAVES.get(wood);
    }

    public static SkySaplingBlock sapling(Wood wood) {
        return SAPLINGS.get(wood);
    }

    public static StumpBlock stump(Stump stump) {
        return STUMPS.get(stump);
    }

    /** Every block this mod adds, by path, in registration order. */
    public static Map<String, Block> all() {
        return Collections.unmodifiableMap(BLOCKS);
    }

    private static <T extends Block> T register(String name, Function<ResourceKey<Block>, T> factory) {
        ResourceKey<Block> key = ResourceKey.create(Registries.BLOCK, SkyRpgForaging.id(name));
        T block = Registry.register(BuiltInRegistries.BLOCK, key, factory.apply(key));
        BLOCKS.put(name, block);
        return block;
    }
}
