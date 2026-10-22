package com.skyrpg.items.client.crafting;

import com.skyrpg.items.crafting.BenchPayloads;
import java.util.List;
import net.fabricmc.loader.api.FabricLoader;

/** Every Artisan Bench recipe, as the server last sent it (on joining and with each bench view). Used by recipe viewers. */
public final class BenchCatalog {
    private static List<BenchPayloads.Entry> entries = List.of();

    private BenchCatalog() {
    }

    public static List<BenchPayloads.Entry> entries() {
        return entries;
    }

    static void update(List<BenchPayloads.Entry> received) {
        boolean first = entries.isEmpty();
        entries = List.copyOf(received);
        if (first && FabricLoader.getInstance().isModLoaded("jei")) {
            BenchJeiPlugin.addRecipes();
        }
    }

    public static void clear() {
        entries = List.of();
    }
}
