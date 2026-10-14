package com.skyrpg.foraging;

import net.fabricmc.fabric.api.biome.v1.BiomeModifications;
import net.fabricmc.fabric.api.biome.v1.BiomeSelectors;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.GenerationStep;

/**
 * Wild trees of the five woods. Each placed feature ({@code worldgen/placed_feature/<wood>_tree}, plus
 * {@code emberwood_tree_nether}) goes into the biomes in the biome tag {@code skyrpg_foraging:has_tree/<name>}, so a
 * server can move them, and set how rare they are, with a data pack. Only chunks generated after this was added have
 * them.
 */
final class ForagingWorldgen {
    /** Placed feature name -> biome tag name. */
    private static final String[][] TREES = {
            {"ironbark_tree", "ironbark"},
            {"mistpine_tree", "mistpine"},
            {"duskwillow_tree", "duskwillow"},
            {"emberwood_tree", "emberwood"},
            {"emberwood_tree_nether", "emberwood_nether"},
            {"starwood_tree", "starwood"},
    };

    private ForagingWorldgen() {
    }

    static void init() {
        Registry.register(BuiltInRegistries.FEATURE_TYPE, SkyRpgForaging.id("sky_tree"), SkyTreeFeature.CODEC);
        for (String[] tree : TREES) {
            TagKey<Biome> biomes = TagKey.create(Registries.BIOME, SkyRpgForaging.id("has_tree/" + tree[1]));
            BiomeModifications.addFeature(BiomeSelectors.tag(biomes), GenerationStep.Decoration.VEGETAL_DECORATION,
                    ResourceKey.create(Registries.PLACED_FEATURE, SkyRpgForaging.id(tree[0])));
        }
    }
}
