package com.skyrpg.foraging;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;
import net.fabricmc.fabric.api.creativetab.v1.FabricCreativeModeTab;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;

/**
 * Foraging items:
 * <ul>
 *   <li>Heartwood Splinters drop from chopped logs: the Foraging Master's currency. Nine make a Heartwood Bundle.</li>
 *   <li>Golden Acorns and Elder Heartwood are rare finds, for the best gear (and the Starwood Sapling).</li>
 * </ul>
 * Plus the block items for every log, plank, leaf, sapling and stump.
 */
public final class ForagingItems {
    private static final Map<String, Item> ITEMS = new LinkedHashMap<>();

    public static final Item HEARTWOOD_SPLINTER = item("heartwood_splinter", p -> p.rarity(Rarity.UNCOMMON), Item::new);
    public static final Item HEARTWOOD_BUNDLE = item("heartwood_bundle", p -> p.rarity(Rarity.RARE), Item::new);
    public static final Item GOLDEN_ACORN = item("golden_acorn", p -> p.rarity(Rarity.RARE), Item::new);
    public static final Item ELDER_HEARTWOOD = item("elder_heartwood", p -> p.rarity(Rarity.EPIC), Item::new);

    private ForagingItems() {
    }

    static void init() {
        ForagingBlocks.all().forEach((name, block) -> {
            ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, SkyRpgForaging.id(name));
            Item.Properties properties = new Item.Properties().setId(key).useBlockDescriptionPrefix();
            if (block instanceof StumpBlock) {
                properties.rarity(Rarity.UNCOMMON);
            }
            ITEMS.put(name, Registry.register(BuiltInRegistries.ITEM, key, new BlockItem(block, properties)));
        });
        Registry.register(BuiltInRegistries.CREATIVE_MODE_TAB, ResourceKey.create(Registries.CREATIVE_MODE_TAB, SkyRpgForaging.id("foraging")),
                FabricCreativeModeTab.builder()
                        .title(Component.translatable("itemGroup.skyrpg_foraging"))
                        .icon(() -> new ItemStack(HEARTWOOD_SPLINTER))
                        .displayItems((parameters, output) -> ITEMS.values().forEach(output::accept))
                        .build());
    }

    private static Item item(String name, Function<Item.Properties, Item.Properties> properties, Function<Item.Properties, Item> factory) {
        ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, SkyRpgForaging.id(name));
        Item item = Registry.register(BuiltInRegistries.ITEM, key, factory.apply(properties.apply(new Item.Properties().setId(key))));
        ITEMS.put(name, item);
        return item;
    }
}
