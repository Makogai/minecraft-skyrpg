package com.skyrpg.items.compactor;

import com.skyrpg.items.SkyRpgItems;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.component.ItemLore;

/**
 * Enchanted materials: {@value #COUNT} of a bulk material packed into one glinting item ({@code skyrpg_items:enchanted_<material>}),
 * so a long mining, farming or chopping trip fits in the inventory. Made at the Artisan Bench or by a Personal Compactor
 * ({@link PersonalTools}); NPCs pay {@value #COUNT} times the material's price. Each looks like its material (its item model
 * file is a copy of the material's) with the enchantment glint.
 */
public final class Enchanted {
    public static final int COUNT = 160;

    /** Material id and name. Generated with the item models, names, bench recipes and sell prices (see tools/gen_enchanted.py). */
    private static final String[][] MATERIALS = {
            {"minecraft:cobblestone", "Cobblestone"},
            {"minecraft:cobbled_deepslate", "Cobbled Deepslate"},
            {"minecraft:granite", "Granite"},
            {"minecraft:diorite", "Diorite"},
            {"minecraft:andesite", "Andesite"},
            {"minecraft:tuff", "Tuff"},
            {"minecraft:calcite", "Calcite"},
            {"minecraft:dripstone_block", "Dripstone Block"},
            {"minecraft:dirt", "Dirt"},
            {"minecraft:gravel", "Gravel"},
            {"minecraft:sand", "Sand"},
            {"minecraft:red_sand", "Red Sand"},
            {"minecraft:clay_ball", "Clay Ball"},
            {"minecraft:flint", "Flint"},
            {"minecraft:netherrack", "Netherrack"},
            {"minecraft:basalt", "Basalt"},
            {"minecraft:blackstone", "Blackstone"},
            {"minecraft:soul_sand", "Soul Sand"},
            {"minecraft:soul_soil", "Soul Soil"},
            {"minecraft:end_stone", "End Stone"},
            {"minecraft:obsidian", "Obsidian"},
            {"minecraft:ice", "Ice"},
            {"minecraft:snowball", "Snowball"},
            {"minecraft:coal", "Coal"},
            {"minecraft:raw_iron", "Raw Iron"},
            {"minecraft:iron_ingot", "Iron Ingot"},
            {"minecraft:raw_gold", "Raw Gold"},
            {"minecraft:gold_ingot", "Gold Ingot"},
            {"minecraft:raw_copper", "Raw Copper"},
            {"minecraft:copper_ingot", "Copper Ingot"},
            {"minecraft:lapis_lazuli", "Lapis Lazuli"},
            {"minecraft:redstone", "Redstone Dust"},
            {"minecraft:emerald", "Emerald"},
            {"minecraft:diamond", "Diamond"},
            {"minecraft:quartz", "Nether Quartz"},
            {"minecraft:glowstone_dust", "Glowstone Dust"},
            {"minecraft:amethyst_shard", "Amethyst Shard"},
            {"skyrpg_mining:glimmer_shard", "Glimmer Shard"},
            {"skyrpg_mining:raw_skyiron", "Raw Skyiron"},
            {"skyrpg_mining:duskquartz_shard", "Duskquartz Shard"},
            {"skyrpg_mining:emberite_chunk", "Emberite Chunk"},
            {"skyrpg_mining:hellforged_scrap", "Hellforged Scrap"},
            {"skyrpg_mining:lumenite_crystal", "Lumenite Crystal"},
            {"minecraft:wheat", "Wheat"},
            {"minecraft:wheat_seeds", "Wheat Seeds"},
            {"minecraft:carrot", "Carrot"},
            {"minecraft:potato", "Potato"},
            {"minecraft:beetroot", "Beetroot"},
            {"minecraft:pumpkin", "Pumpkin"},
            {"minecraft:melon_slice", "Melon Slice"},
            {"minecraft:sugar_cane", "Sugar Cane"},
            {"minecraft:cactus", "Cactus"},
            {"minecraft:cocoa_beans", "Cocoa Beans"},
            {"minecraft:nether_wart", "Nether Wart"},
            {"minecraft:red_mushroom", "Red Mushroom"},
            {"minecraft:brown_mushroom", "Brown Mushroom"},
            {"minecraft:sweet_berries", "Sweet Berries"},
            {"minecraft:apple", "Apple"},
            {"skyrpg_farming:dewdrop", "Dewdrop"},
            {"skyrpg_farming:sunwheat_sheaf", "Sunwheat Sheaf"},
            {"skyrpg_farming:frostberries", "Frostberries"},
            {"skyrpg_farming:emberpepper", "Emberpepper"},
            {"skyrpg_farming:moonmelon_slice", "Moonmelon Slice"},
            {"minecraft:oak_log", "Oak Log"},
            {"minecraft:spruce_log", "Spruce Log"},
            {"minecraft:birch_log", "Birch Log"},
            {"minecraft:jungle_log", "Jungle Log"},
            {"minecraft:acacia_log", "Acacia Log"},
            {"minecraft:dark_oak_log", "Dark Oak Log"},
            {"minecraft:mangrove_log", "Mangrove Log"},
            {"minecraft:cherry_log", "Cherry Log"},
            {"minecraft:pale_oak_log", "Pale Oak Log"},
            {"minecraft:crimson_stem", "Crimson Stem"},
            {"minecraft:warped_stem", "Warped Stem"},
            {"minecraft:bamboo", "Bamboo"},
            {"skyrpg_foraging:ironbark_log", "Ironbark Log"},
            {"skyrpg_foraging:mistpine_log", "Mistpine Log"},
            {"skyrpg_foraging:duskwillow_log", "Duskwillow Log"},
            {"skyrpg_foraging:emberwood_log", "Emberwood Log"},
            {"skyrpg_foraging:starwood_log", "Starwood Log"},
            {"minecraft:rotten_flesh", "Rotten Flesh"},
            {"minecraft:bone", "Bone"},
            {"minecraft:string", "String"},
            {"minecraft:spider_eye", "Spider Eye"},
            {"minecraft:gunpowder", "Gunpowder"},
            {"minecraft:ender_pearl", "Ender Pearl"},
            {"minecraft:slime_ball", "Slimeball"},
            {"minecraft:blaze_rod", "Blaze Rod"},
            {"minecraft:magma_cream", "Magma Cream"},
            {"minecraft:leather", "Leather"},
            {"minecraft:feather", "Feather"},
            {"minecraft:beef", "Raw Beef"},
            {"minecraft:porkchop", "Raw Porkchop"},
            {"minecraft:chicken", "Raw Chicken"},
            {"minecraft:mutton", "Raw Mutton"},
            {"minecraft:rabbit", "Raw Rabbit"},
            {"minecraft:white_wool", "White Wool"},
            {"minecraft:cod", "Raw Cod"},
            {"minecraft:salmon", "Raw Salmon"},
            {"minecraft:pufferfish", "Pufferfish"},
            {"minecraft:tropical_fish", "Tropical Fish"},
            {"minecraft:ink_sac", "Ink Sac"},
            {"minecraft:prismarine_shard", "Prismarine Shard"},
            {"minecraft:prismarine_crystals", "Prismarine Crystals"}
    };

    /** Enchanted item to the material id it's made from. Materials of other mods are looked up when first needed. */
    private static final Map<Item, Identifier> SOURCES = new LinkedHashMap<>();
    private static Map<Item, Item> byMaterial;

    private Enchanted() {
    }

    /** Registers the enchanted items (mod init). */
    public static void init() {
        for (String[] material : MATERIALS) {
            Identifier source = Identifier.parse(material[0]);
            Identifier id = SkyRpgItems.id("enchanted_" + source.getPath());
            ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, id);
            Item item = Registry.register(BuiltInRegistries.ITEM, key, new EnchantedItem(new Item.Properties().setId(key)
                    .component(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true)
                    .component(DataComponents.LORE, new ItemLore(List.of(
                            Component.literal(COUNT + " " + material[1] + ", packed into one.").withStyle(ChatFormatting.GRAY)
                                    .withStyle(style -> style.withItalic(false)))))));
            SOURCES.put(item, source);
        }
        com.skyrpg.core.items.Compacted.install(Enchanted::material);
    }

    /** Named in SkyRPG's uncommon green, like Hypixel's enchanted materials. */
    private static final class EnchantedItem extends Item {
        EnchantedItem(Properties properties) {
            super(properties);
        }

        @Override
        public Component getName(net.minecraft.world.item.ItemStack stack) {
            return Component.translatable(getDescriptionId()).withColor(com.skyrpg.items.Rarity.UNCOMMON.color());
        }
    }

    public static Collection<Item> all() {
        return Collections.unmodifiableSet(SOURCES.keySet());
    }

    public static boolean isEnchanted(Item item) {
        return SOURCES.containsKey(item);
    }

    /** The material this enchanted item is made from, or null. */
    public static Item material(Item enchanted) {
        Identifier source = SOURCES.get(enchanted);
        return source == null ? null : BuiltInRegistries.ITEM.getOptional(source).orElse(null);
    }

    /** The enchanted form of this material, or null if it has none. */
    public static Item of(Item material) {
        if (byMaterial == null) {
            Map<Item, Item> map = new HashMap<>();
            SOURCES.forEach((enchanted, source) -> BuiltInRegistries.ITEM.getOptional(source).ifPresent(item -> map.put(item, enchanted)));
            byMaterial = map;
        }
        return byMaterial.get(material);
    }
}
