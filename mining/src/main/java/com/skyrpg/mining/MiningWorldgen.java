package com.skyrpg.mining;

import net.fabricmc.fabric.api.biome.v1.BiomeModifications;
import net.fabricmc.fabric.api.biome.v1.BiomeSelectors;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.GenerationStep;

/**
 * The new ores in every overworld biome. Features are data (worldgen/feature and worldgen/placed_feature
 * under skyrpg_mining): Skyiron in common mid-depth veins, Duskquartz in clusters below y -8, Emberite in small deep
 * veins, Lumenite as rare single crystals near bedrock. Only chunks generated after this mod was added have them.
 *
 * <p>Hellforged ore is the Nether's: a few small veins anywhere in the Nether ({@code ore_hellforged}), many more in the
 * biomes of the biome tag {@code skyrpg_mining:hellforged_rich} (the Ember Forest, Bone Sea and Obsidian Spires), and big
 * veins in {@code skyrpg_mining:hellforged_veins} (the Obsidian Spires). The tags are data, so a server can move them.
 */
final class MiningWorldgen {
    private static final String[] ORES = {"ore_skyiron", "ore_duskquartz", "ore_emberite", "ore_lumenite"};
    private static final TagKey<Biome> HELLFORGED_RICH = TagKey.create(Registries.BIOME, SkyRpgMining.id("hellforged_rich"));
    private static final TagKey<Biome> HELLFORGED_VEINS = TagKey.create(Registries.BIOME, SkyRpgMining.id("hellforged_veins"));

    private MiningWorldgen() {
    }

    static void init() {
        for (String ore : ORES) {
            BiomeModifications.addFeature(BiomeSelectors.foundInOverworld(), GenerationStep.Decoration.UNDERGROUND_ORES, placed(ore));
        }
        // The Nether's ores are at the underground-decoration step, like vanilla's quartz and ancient debris.
        BiomeModifications.addFeature(BiomeSelectors.foundInTheNether(), GenerationStep.Decoration.UNDERGROUND_DECORATION, placed("ore_hellforged"));
        BiomeModifications.addFeature(BiomeSelectors.tag(HELLFORGED_RICH), GenerationStep.Decoration.UNDERGROUND_DECORATION,
                placed("ore_hellforged_rich"));
        BiomeModifications.addFeature(BiomeSelectors.tag(HELLFORGED_VEINS), GenerationStep.Decoration.UNDERGROUND_DECORATION,
                placed("ore_hellforged_veins"));
    }

    private static ResourceKey<net.minecraft.world.level.levelgen.placement.PlacedFeature> placed(String name) {
        return ResourceKey.create(Registries.PLACED_FEATURE, SkyRpgMining.id(name));
    }
}
