package com.skyrpg.core.items;

import java.util.function.Function;
import net.minecraft.world.item.Item;

/**
 * Compacted (enchanted) materials, for any mod: the {@code skyrpg_items} mod installs which material each enchanted item is
 * made from, so, for example, a shop that buys sugar cane also buys Enchanted Sugar Cane. Without it nothing is compacted.
 */
public final class Compacted {
    private static Function<Item, Item> materialOf = item -> null;

    private Compacted() {
    }

    public static void install(Function<Item, Item> installed) {
        materialOf = installed;
    }

    /** The material this compacted item is made from, or null if it isn't one. */
    public static Item material(Item item) {
        return materialOf.apply(item);
    }
}
