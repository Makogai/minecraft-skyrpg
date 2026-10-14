package com.skyrpg.farming;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;
import net.fabricmc.fabric.api.creativetab.v1.FabricCreativeModeTab;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.level.block.Block;

/**
 * Farming items:
 * <ul>
 *   <li>Dewdrops drop from harvested crops: the Farming Master's currency. Nine make a Dew Vial.</li>
 *   <li>Lucky Clovers and Verdant Hearts are rare finds, for the best gear.</li>
 *   <li>Seeds for the four special crops (Starbloom has no field crop: its seed goes into a Starbloom Planter), and
 *   what each crop gives.</li>
 *   <li>Rich Compost grows any crop a stage; meals give a ten-minute boost (see {@link Meals}).</li>
 * </ul>
 * Plus the block items for planters and sprinklers.
 */
public final class FarmingItems {
    private static final Map<String, Item> ITEMS = new LinkedHashMap<>();

    public static final Item DEWDROP = item("dewdrop", p -> p.rarity(Rarity.UNCOMMON), Item::new);
    public static final Item DEW_VIAL = item("dew_vial", p -> p.rarity(Rarity.RARE), Item::new);
    public static final Item LUCKY_CLOVER = item("lucky_clover", p -> p.rarity(Rarity.RARE), Item::new);
    public static final Item VERDANT_HEART = item("verdant_heart", p -> p.rarity(Rarity.EPIC), Item::new);

    public static final Item SUNWHEAT_SEEDS = seeds("sunwheat_seeds", FarmingBlocks.SUNWHEAT);
    public static final Item FROSTBERRY_SEEDS = seeds("frostberry_seeds", FarmingBlocks.FROSTBERRY);
    public static final Item EMBERPEPPER_SEEDS = seeds("emberpepper_seeds", FarmingBlocks.EMBERPEPPER);
    public static final Item MOONMELON_SEEDS = seeds("moonmelon_seeds", FarmingBlocks.MOONMELON);
    public static final Item STARBLOOM_SEED = item("starbloom_seed", p -> p.rarity(Rarity.RARE), Item::new);

    public static final Item SUNWHEAT_SHEAF = item("sunwheat_sheaf", p -> p, Item::new);
    public static final Item FROSTBERRIES = item("frostberries", p -> p.food(food(2, 0.4f)), Item::new);
    public static final Item EMBERPEPPER = item("emberpepper", p -> p, Item::new);
    public static final Item MOONMELON_SLICE = item("moonmelon_slice", p -> p.food(food(3, 0.5f)), Item::new);
    public static final Item STARBLOOM_PETAL = item("starbloom_petal", p -> p.rarity(Rarity.UNCOMMON), Item::new);

    public static final Item RICH_COMPOST = item("rich_compost", p -> p, CompostItem::new);

    public static final Item SUNBREAD = meal("sunbread", 6, 0.8f);
    public static final Item MINERS_CHILI = meal("miners_chili", 7, 0.6f);
    public static final Item FROSTBERRY_TART = meal("frostberry_tart", 5, 0.6f);
    public static final Item MOONMELON_JUICE = meal("moonmelon_juice", 4, 0.4f);
    public static final Item STARBLOOM_TEA = meal("starbloom_tea", 4, 0.6f);

    private FarmingItems() {
    }

    static void init() {
        FarmingBlocks.all().forEach((name, block) -> {
            if (block instanceof SkyCropBlock) {
                return; // crops are planted from their seeds
            }
            ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, SkyRpgFarming.id(name));
            Item.Properties properties = new Item.Properties().setId(key).useBlockDescriptionPrefix();
            if (block instanceof PlanterBlock) {
                properties.rarity(Rarity.UNCOMMON);
            }
            ITEMS.put(name, Registry.register(BuiltInRegistries.ITEM, key, new BlockItem(block, properties)));
        });
        Registry.register(BuiltInRegistries.CREATIVE_MODE_TAB, ResourceKey.create(Registries.CREATIVE_MODE_TAB, SkyRpgFarming.id("farming")),
                FabricCreativeModeTab.builder()
                        .title(Component.translatable("itemGroup.skyrpg_farming"))
                        .icon(() -> new ItemStack(SUNWHEAT_SHEAF))
                        .displayItems((parameters, output) -> ITEMS.values().forEach(output::accept))
                        .build());
    }

    public static Map<String, Item> all() {
        return Collections.unmodifiableMap(ITEMS);
    }

    private static FoodProperties food(int nutrition, float saturation) {
        return new FoodProperties.Builder().nutrition(nutrition).saturationModifier(saturation).build();
    }

    private static Item meal(String name, int nutrition, float saturation) {
        return item(name, p -> p.stacksTo(16).rarity(Rarity.UNCOMMON)
                .food(new FoodProperties.Builder().nutrition(nutrition).saturationModifier(saturation).alwaysEdible().build()), MealItem::new);
    }

    private static Item seeds(String name, Block crop) {
        return item(name, p -> p.useItemDescriptionPrefix(), properties -> new BlockItem(crop, properties));
    }

    private static Item item(String name, Function<Item.Properties, Item.Properties> props, Function<Item.Properties, Item> factory) {
        ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, SkyRpgFarming.id(name));
        Item item = Registry.register(BuiltInRegistries.ITEM, key, factory.apply(props.apply(new Item.Properties().setId(key))));
        ITEMS.put(name, item);
        return item;
    }
}
