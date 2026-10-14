package com.skyrpg.mounts;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;

/** The griffin nest block and its item. */
public final class MountBlocks {
    public static final Block GRIFFIN_NEST;
    public static final Item GRIFFIN_NEST_ITEM;

    static {
        ResourceKey<Block> key = ResourceKey.create(Registries.BLOCK, SkyRpgMounts.id("griffin_nest"));
        GRIFFIN_NEST = Registry.register(BuiltInRegistries.BLOCK, key, new GriffinNestBlock(BlockBehaviour.Properties.of().setId(key)
                .mapColor(MapColor.COLOR_YELLOW).strength(0.8f).sound(SoundType.GRASS).noOcclusion().randomTicks().pushReaction(PushReaction.POPPED)));
        ResourceKey<Item> itemKey = ResourceKey.create(Registries.ITEM, SkyRpgMounts.id("griffin_nest"));
        GRIFFIN_NEST_ITEM = Registry.register(BuiltInRegistries.ITEM, itemKey,
                new BlockItem(GRIFFIN_NEST, new Item.Properties().setId(itemKey).useBlockDescriptionPrefix()));
    }

    private MountBlocks() {
    }

    static void init() {
    }
}
