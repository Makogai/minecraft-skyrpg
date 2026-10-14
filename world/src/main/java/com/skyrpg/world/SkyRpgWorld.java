package com.skyrpg.world;

import com.skyrpg.core.data.JsonDataLoader;
import com.skyrpg.world.bloodmoon.BloodMoons;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.biome.v1.BiomeModifications;
import net.fabricmc.fabric.api.biome.v1.BiomeSelectors;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The survival world comes alive: dungeons that generate from the seed (bosses, mob packs, puzzles, vaults, treasure
 * maps) and waystones to travel between the places you've found. See docs/design/dungeons.md.
 */
public class SkyRpgWorld implements ModInitializer {
    public static final String MOD_ID = "skyrpg_world";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    public static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(MOD_ID, path);
    }

    @Override
    public void onInitialize() {
        WorldContent.init();
        Explorer.init();
        WorldPayloads.register();
        JsonDataLoader.register(id("dungeons"), Dungeons.DUNGEONS_DIRECTORY, Dungeons::loadDungeons);
        JsonDataLoader.register(id("dungeon_bosses"), Dungeons.BOSSES_DIRECTORY, Dungeons::loadBosses);
        DungeonMarkers.init();
        DungeonBosses.init();
        Waystones.init();
        JsonDataLoader.register(id("danger_regions"), DangerRegions.DIRECTORY, DangerRegions::load);
        DangerRegions.init();
        RegionBosses.init();
        BloodMoons.init();
        Registry.register(BuiltInRegistries.FEATURE_TYPE, id("danger_region"), DangerRegionFeature.CODEC);
        BiomeModifications.addFeature(BiomeSelectors.foundInOverworld(), GenerationStep.Decoration.TOP_LAYER_MODIFICATION,
                ResourceKey.create(Registries.PLACED_FEATURE, id("danger_region")));
        // The Deep: underground layers restyle the caves (see docs/design/the_deep.md).
        JsonDataLoader.register(id("deep_layers"), DeepLayers.DIRECTORY, DeepLayers::load);
        DeepLayers.init();
        Registry.register(BuiltInRegistries.FEATURE_TYPE, id("deep_layer"), DeepLayerFeature.CODEC);
        BiomeModifications.addFeature(BiomeSelectors.foundInOverworld(), GenerationStep.Decoration.UNDERGROUND_DECORATION,
                ResourceKey.create(Registries.PLACED_FEATURE, id("deep_layer")));
        // The Nether: three new biomes (see docs/design/nether.md).
        NetherRealm.init();
        // The End: three new biomes (see docs/design/end_game.md).
        EndRealm.init();
        WorldCommands.init();
    }
}
