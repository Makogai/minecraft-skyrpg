package com.skyrpg.builder;

import net.fabricmc.api.ModInitializer;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * SkyRPG Builder: the Builder Station turns a schematic into a real build. Pick a blueprint (an uploaded
 * .litematic/.schem/.nbt or one of Minecraft's village houses), place its hologram, deliver the materials, and it
 * builds itself from the ground up. See docs/design/builder.md.
 */
public final class SkyRpgBuilder implements ModInitializer {
    public static final String MOD_ID = "skyrpg_builder";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    public static final Block STATION = registerBlock();
    public static final Item STATION_ITEM = registerItem();

    public static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(MOD_ID, path);
    }

    @Override
    public void onInitialize() {
        BuilderPayloads.register();
        Library.init();
        Stations.init();
    }

    private static Block registerBlock() {
        ResourceKey<Block> key = ResourceKey.create(Registries.BLOCK, id("builder_station"));
        return Registry.register(BuiltInRegistries.BLOCK, key, new BuilderBlock(BlockBehaviour.Properties.of().setId(key)
                .mapColor(MapColor.WOOD).strength(2.5f, 1200f).sound(SoundType.WOOD).lightLevel(state -> state.getValue(BuilderBlock.ACTIVE) ? 10 : 4)));
    }

    private static Item registerItem() {
        ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, id("builder_station"));
        return Registry.register(BuiltInRegistries.ITEM, key, new BlockItem(STATION, new Item.Properties().setId(key).rarity(Rarity.RARE)
                .useBlockDescriptionPrefix()));
    }
}
