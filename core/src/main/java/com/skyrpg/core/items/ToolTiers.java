package com.skyrpg.core.items;

import java.util.function.ToIntFunction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * How strong a pickaxe or axe is, for any mod: SkyRPG's tougher ores and woods need at least a given tier (see
 * {@code ToolGates} and the {@code skyrpg_core:tool_tier/<n>} block tags). Vanilla: wooden and golden 1, stone and copper
 * 2, iron 3, diamond 4, netherite 5. The {@code skyrpg_items} mod installs the lookup for its own tools (a definition's
 * {@code "tier"}).
 */
public final class ToolTiers {
    private static ToIntFunction<ItemStack> lookup = ToolTiers::vanilla;

    private ToolTiers() {
    }

    public static void install(ToIntFunction<ItemStack> installed) {
        lookup = installed;
    }

    /** The tier of a pickaxe or axe (0 for anything else). */
    public static int tier(ItemStack stack) {
        return ToolWear.isBroken(stack) ? 0 : lookup.applyAsInt(stack);
    }

    /** Vanilla pickaxes' and axes' tiers. */
    public static int vanilla(ItemStack stack) {
        if (stack.is(Items.NETHERITE_PICKAXE) || stack.is(Items.NETHERITE_AXE)) {
            return 5;
        }
        if (stack.is(Items.DIAMOND_PICKAXE) || stack.is(Items.DIAMOND_AXE)) {
            return 4;
        }
        if (stack.is(Items.IRON_PICKAXE) || stack.is(Items.IRON_AXE)) {
            return 3;
        }
        if (stack.is(Items.STONE_PICKAXE) || stack.is(Items.COPPER_PICKAXE) || stack.is(Items.STONE_AXE) || stack.is(Items.COPPER_AXE)) {
            return 2;
        }
        if (stack.is(Items.WOODEN_PICKAXE) || stack.is(Items.GOLDEN_PICKAXE) || stack.is(Items.WOODEN_AXE) || stack.is(Items.GOLDEN_AXE)) {
            return 1;
        }
        return 0;
    }

    /** "I", "II"... for messages. */
    public static String roman(int tier) {
        return switch (tier) {
            case 1 -> "I";
            case 2 -> "II";
            case 3 -> "III";
            case 4 -> "IV";
            case 5 -> "V";
            case 6 -> "VI";
            case 7 -> "VII";
            default -> Integer.toString(tier);
        };
    }
}
