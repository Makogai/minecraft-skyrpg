package com.skyrpg.farming;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/** A meal: food that also gives a boost when you finish eating it (see {@link Meals}). */
public final class MealItem extends Item {
    public MealItem(Properties properties) {
        super(properties);
    }

    @Override
    public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity entity) {
        if (entity instanceof ServerPlayer player) {
            Meals.eat(player, this);
        }
        return super.finishUsingItem(stack, level, entity);
    }
}
