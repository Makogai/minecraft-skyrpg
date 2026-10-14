package com.skyrpg.combat.slayer;

import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.SpawnerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * A spawner from the Slayer Master: places a vanilla spawner already set to its mob (vanilla spawner items only take
 * their mob from operators). The mob is in the stack's custom data, {@code {"entity": "minecraft:zombie"}}.
 */
public class SpawnerItem extends BlockItem {
    private static final String ENTITY_KEY = "entity";

    public SpawnerItem(Block block, Properties properties) {
        super(block, properties);
    }

    public static ItemStack create(EntityType<?> type, int count) {
        ItemStack stack = new ItemStack(Spawners.SPAWNER, count);
        CompoundTag tag = new CompoundTag();
        tag.putString(ENTITY_KEY, BuiltInRegistries.ENTITY_TYPE.getKey(type).toString());
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        stack.set(DataComponents.ITEM_NAME, Component.literal(type.getDescription().getString() + " Spawner"));
        stack.set(DataComponents.LORE, new ItemLore(List.of(
                Component.literal("Spawns " + type.getDescription().getString() + " mobs nearby").withStyle(s -> s.withItalic(false).withColor(ChatFormatting.GRAY)),
                Component.literal("while a player is close, like any spawner.").withStyle(s -> s.withItalic(false).withColor(ChatFormatting.GRAY)),
                Component.empty(),
                Component.literal("Farmed mobs: normal loot, a quarter of the XP,").withStyle(s -> s.withItalic(false).withColor(ChatFormatting.DARK_GRAY)),
                Component.literal("no slayer progress or rare drops.").withStyle(s -> s.withItalic(false).withColor(ChatFormatting.DARK_GRAY)),
                Component.literal("Break it to pick it back up.").withStyle(s -> s.withItalic(false).withColor(ChatFormatting.YELLOW)))));
        return stack;
    }

    public static EntityType<?> type(ItemStack stack) {
        String id = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag().getStringOr(ENTITY_KEY, "");
        return BuiltInRegistries.ENTITY_TYPE.getOptional(Identifier.tryParse(id)).orElse(null);
    }

    @Override
    public InteractionResult place(BlockPlaceContext context) {
        EntityType<?> type = type(context.getItemInHand());
        if (type == null) {
            return InteractionResult.FAIL;
        }
        BlockPos pos = context.getClickedPos();
        InteractionResult result = super.place(context);
        if (result.consumesAction() && context.getLevel() instanceof ServerLevel level
                && level.getBlockEntity(pos) instanceof SpawnerBlockEntity spawner) {
            spawner.setEntityId(type, level.getRandom());
            spawner.setChanged();
            BlockState state = level.getBlockState(pos);
            level.sendBlockUpdated(pos, state, state, Block.UPDATE_ALL);
        }
        return result;
    }
}
