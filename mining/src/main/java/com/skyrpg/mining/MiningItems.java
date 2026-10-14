package com.skyrpg.mining;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import net.fabricmc.fabric.api.creativetab.v1.FabricCreativeModeTab;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.Unit;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.ToolMaterial;

/**
 * Mining materials and the block items for nodes and ores.
 * <ul>
 *   <li>Glimmer Shards drop in large numbers from everything you mine: the Mining Master's currency. Nine
 *   make a Glimmer Crystal (and back) so prices can be big without whole chests of shards.</li>
 *   <li>Raw Skyiron, Emberite Chunks and Lumenite Crystals come from the new ores and nodes.</li>
 *   <li>Prism Fragments and Heartstones are rare finds from ores and nodes, for the best gear.</li>
 *   <li>Hellforged Scrap comes from the Nether's Hellforged ore; it smelts (blast furnace) into Hellforged Ingots for the
 *   fire-proof Hellforged gear and the Hellforged Pickaxe.</li>
 * </ul>
 */
public final class MiningItems {
    private static final Map<String, Item> ITEMS = new LinkedHashMap<>();

    public static final Item GLIMMER_SHARD = material("glimmer_shard", Rarity.COMMON);
    public static final Item GLIMMER_CRYSTAL = material("glimmer_crystal", Rarity.UNCOMMON);
    public static final Item RAW_SKYIRON = material("raw_skyiron", Rarity.COMMON);
    public static final Item EMBERITE_CHUNK = material("emberite_chunk", Rarity.UNCOMMON);
    public static final Item LUMENITE_CRYSTAL = material("lumenite_crystal", Rarity.RARE);
    public static final Item PRISM_FRAGMENT = material("prism_fragment", Rarity.RARE);
    public static final Item HEARTSTONE = material("heartstone", Rarity.EPIC);
    // Refined: smelted ingots, Duskquartz from the mid-deep ore, and bench-made Attuned Lumenite and Prism.
    public static final Item SKYIRON_INGOT = material("skyiron_ingot", Rarity.UNCOMMON);
    public static final Item EMBERITE_INGOT = material("emberite_ingot", Rarity.RARE);
    public static final Item DUSKQUARTZ_SHARD = material("duskquartz_shard", Rarity.UNCOMMON);
    public static final Item ATTUNED_LUMENITE = material("attuned_lumenite", Rarity.EPIC);
    public static final Item PRISM = material("prism", Rarity.EPIC);
    // The Nether: Hellforged Scrap drops from Hellforged ore, a blast furnace makes it a Hellforged Ingot. Like netherite,
    // neither burns in lava.
    public static final Item HELLFORGED_SCRAP = register("hellforged_scrap", new Item.Properties().rarity(Rarity.UNCOMMON).fireResistant());
    public static final Item HELLFORGED_INGOT = register("hellforged_ingot", new Item.Properties().rarity(Rarity.RARE).fireResistant());
    /**
     * The Hellforged Pickaxe (Tier VI, between the Emberheart Pickaxe and the Lumen Breaker): a real pickaxe here, and
     * what it does (name, stats, tier, ability) comes from its definition in {@code data/skyrpg_mining/skyrpg/items}, like
     * the items mod's pickaxes.
     */
    public static final Item HELLFORGED_PICKAXE = register("hellforged_pickaxe", new Item.Properties().stacksTo(1).fireResistant()
            .pickaxe(ToolMaterial.NETHERITE, 1.0f, -2.8f).component(DataComponents.UNBREAKABLE, Unit.INSTANCE));

    private MiningItems() {
    }

    static void init() {
        MiningBlocks.all().forEach((name, block) -> {
            ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, SkyRpgMining.id(name));
            Item.Properties properties = new Item.Properties().setId(key).useBlockDescriptionPrefix();
            if (block instanceof NodeBlock) {
                properties.rarity(Rarity.UNCOMMON);
            }
            ITEMS.put(name, Registry.register(BuiltInRegistries.ITEM, key, new BlockItem(block, properties)));
        });
        Registry.register(BuiltInRegistries.CREATIVE_MODE_TAB, ResourceKey.create(Registries.CREATIVE_MODE_TAB, SkyRpgMining.id("mining")),
                FabricCreativeModeTab.builder()
                        .title(Component.translatable("itemGroup.skyrpg_mining"))
                        .icon(() -> new ItemStack(MiningBlocks.node(MiningBlocks.Node.SKYIRON)))
                        .displayItems((parameters, output) -> ITEMS.values().forEach(output::accept))
                        .build());
    }

    public static Map<String, Item> all() {
        return Collections.unmodifiableMap(ITEMS);
    }

    private static Item material(String name, Rarity rarity) {
        ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, SkyRpgMining.id(name));
        Item item = Registry.register(BuiltInRegistries.ITEM, key, new Item(new Item.Properties().setId(key).rarity(rarity)));
        ITEMS.put(name, item);
        return item;
    }

    private static Item register(String name, Item.Properties properties) {
        ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, SkyRpgMining.id(name));
        Item item = Registry.register(BuiltInRegistries.ITEM, key, new Item(properties.setId(key)));
        ITEMS.put(name, item);
        return item;
    }
}
