package com.skyrpg.magic;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * A wand. Holding one changes what the mouse does (see the client's input handling): left click casts, hold right click opens
 * the spell wheel, sneak + right click opens the slots. The item itself does nothing when used; casting goes through payloads.
 */
public class WandItem extends Item {
    public WandItem(Properties properties) {
        super(properties);
    }

    public static boolean isWand(ItemStack stack) {
        return !stack.isEmpty() && stack.getItem() instanceof WandItem;
    }

    /** The definition of this wand stack, or null. */
    public static WandDef def(ItemStack stack) {
        return isWand(stack) ? Magic.wand(net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem())) : null;
    }

    /**
     * What the stack holds, fitted to its wand's slot count: the scrolls are the stack's container contents (slot by slot), the
     * selected slot is its own component.
     */
    public static WandData data(ItemStack stack) {
        WandDef def = def(stack);
        int count = def == null ? 2 : def.slots();
        ItemContainerContents contents = stack.getOrDefault(DataComponents.CONTAINER, ItemContainerContents.EMPTY);
        NonNullList<ItemStack> items = NonNullList.withSize(Math.max(count, contents.size()), ItemStack.EMPTY);
        contents.copyInto(items);
        List<String> slots = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            String spell = MagicItems.spellOf(items.get(i));
            slots.add(spell == null ? "" : spell);
        }
        return new WandData(slots, Math.max(0, Math.min(stack.getOrDefault(MagicItems.SELECTED, 0), count - 1)));
    }

    public static void setData(ItemStack stack, WandData data) {
        List<ItemStack> items = new ArrayList<>();
        for (String spell : data.slots()) {
            net.minecraft.world.item.Item scroll = spell.isEmpty() ? null : MagicItems.scrollFor(spell);
            items.add(scroll == null ? ItemStack.EMPTY : new ItemStack(scroll));
        }
        ItemContainerContents contents = ItemContainerContents.fromItems(items);
        if (!contents.equals(stack.get(DataComponents.CONTAINER))) {
            stack.set(DataComponents.CONTAINER, contents);
        }
        if (stack.getOrDefault(MagicItems.SELECTED, 0) != data.selected()) {
            stack.set(MagicItems.SELECTED, data.selected());
        }
    }
}
