package com.skyrpg.farming;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Supplier;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;

/**
 * The farming blocks: the four special crops (planted from their seeds), planters (bought from the Farming Master as you
 * level, they grow a crop on a timer anywhere) and sprinklers. Their block items live in {@link FarmingItems}.
 */
public final class FarmingBlocks {
    /** Every planter: what it grows, how long that takes, and the Farming XP a harvest gives. */
    public enum Planter {
        WHEAT(() -> Items.WHEAT, 45, 1.5),
        CARROT(() -> Items.CARROT, 50, 1.5),
        POTATO(() -> Items.POTATO, 50, 1.5),
        SUNWHEAT(() -> FarmingItems.SUNWHEAT_SHEAF, 90, 3),
        FROSTBERRY(() -> FarmingItems.FROSTBERRIES, 120, 4),
        EMBERPEPPER(() -> FarmingItems.EMBERPEPPER, 180, 6),
        MOONMELON(() -> FarmingItems.MOONMELON_SLICE, 240, 8),
        STARBLOOM(() -> FarmingItems.STARBLOOM_PETAL, 300, 14);

        private final Supplier<Item> product;
        private final int seconds;
        private final double xp;

        Planter(Supplier<Item> product, int seconds, double xp) {
            this.product = product;
            this.seconds = seconds;
            this.xp = xp;
        }

        public String id() {
            return name().toLowerCase(Locale.ROOT) + "_planter";
        }

        public Item product() {
            return product.get();
        }

        public int growTicks() {
            return seconds * 20;
        }

        public double xp() {
            return xp;
        }
    }

    private static final Map<String, Block> BLOCKS = new LinkedHashMap<>();
    private static final Map<Planter, PlanterBlock> PLANTERS = new LinkedHashMap<>();

    public static final SkyCropBlock SUNWHEAT = crop("sunwheat", SkyCropBlock.Condition.SUN, () -> FarmingItems.SUNWHEAT_SEEDS, MapColor.COLOR_YELLOW);
    public static final SkyCropBlock FROSTBERRY = crop("frostberry", SkyCropBlock.Condition.COLD, () -> FarmingItems.FROSTBERRY_SEEDS, MapColor.COLOR_LIGHT_BLUE);
    public static final SkyCropBlock EMBERPEPPER = crop("emberpepper", SkyCropBlock.Condition.HEAT, () -> FarmingItems.EMBERPEPPER_SEEDS, MapColor.COLOR_RED);
    public static final SkyCropBlock MOONMELON = crop("moonmelon", SkyCropBlock.Condition.NIGHT, () -> FarmingItems.MOONMELON_SEEDS, MapColor.COLOR_PURPLE);

    public static final SprinklerBlock COPPER_SPRINKLER = sprinkler("copper_sprinkler", 2, MapColor.COLOR_ORANGE);
    public static final SprinklerBlock SKYIRON_SPRINKLER = sprinkler("skyiron_sprinkler", 3, MapColor.COLOR_LIGHT_BLUE);
    public static final SprinklerBlock EMBERITE_SPRINKLER = sprinkler("emberite_sprinkler", 4, MapColor.COLOR_RED);

    static {
        for (Planter planter : Planter.values()) {
            PLANTERS.put(planter, register(planter.id(), properties -> new PlanterBlock(planter, properties
                    .mapColor(MapColor.WOOD).strength(1.0f, 1200.0f).sound(SoundType.WOOD).pushReaction(PushReaction.IMMOVEABLE).noOcclusion())));
        }
    }

    private FarmingBlocks() {
    }

    static void init() {
        // Loads the class so the blocks register during mod init.
    }

    public static PlanterBlock planter(Planter planter) {
        return PLANTERS.get(planter);
    }

    /** Every block this mod adds, by path, in registration order. */
    public static Map<String, Block> all() {
        return Collections.unmodifiableMap(BLOCKS);
    }

    private static SkyCropBlock crop(String name, SkyCropBlock.Condition condition, Supplier<net.minecraft.world.level.ItemLike> seeds, MapColor color) {
        return register(name, properties -> new SkyCropBlock(condition, seeds, properties
                .mapColor(color).noCollision().randomTicks().instabreak().sound(SoundType.CROP).pushReaction(PushReaction.POPPED)));
    }

    private static SprinklerBlock sprinkler(String name, int radius, MapColor color) {
        return register(name, properties -> new SprinklerBlock(radius, properties
                .mapColor(color).strength(2.0f, 6.0f).sound(SoundType.COPPER).noOcclusion()));
    }

    private static <T extends Block> T register(String name, Function<BlockBehaviour.Properties, T> factory) {
        ResourceKey<Block> key = ResourceKey.create(Registries.BLOCK, SkyRpgFarming.id(name));
        T block = Registry.register(BuiltInRegistries.BLOCK, key, factory.apply(BlockBehaviour.Properties.of().setId(key)));
        BLOCKS.put(name, block);
        return block;
    }
}
