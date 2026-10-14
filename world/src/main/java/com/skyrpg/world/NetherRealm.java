package com.skyrpg.world;

import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.biome.v1.BiomeModifications;
import net.fabricmc.fabric.api.biome.v1.BiomeSelectors;
import net.fabricmc.fabric.api.biome.v1.ModificationPhase;
import net.fabricmc.fabric.api.biome.v1.NetherBiomes;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.valueproviders.UniformInt;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.biome.MobSpawnSettings;

/**
 * The Nether rework's three biomes (docs/design/nether.md): the Ember Forest, the Bone Sea and the Obsidian Spires.
 * <ul>
 *   <li>Each biome is data ({@code data/skyrpg_world/worldgen/biome/<id>.json}: fog and sky colours, ambient particles,
 *   sounds and music, vanilla monsters, features) and joins the Nether's biome source here through Fabric's
 *   {@link NetherBiomes}, so existing worlds get them too, in Nether chunks generated from now on.</li>
 *   <li>Their look is Java features: {@link NetherSurfaceFeature} restyles the ground (ember ash; soul sand dunes; blackstone
 *   flats with glowing void cracks), {@link BoneFossilFeature} half-buries giant ribcages and skulls in the Bone Sea and
 *   {@link ObsidianSpireFeature} raises the obsidian spires. The Ember Forest's trees are the foraging mod's Emberwood.</li>
 *   <li>The combat mod's monsters (Ember Imps, Bone Giants) are added by id once every registry is complete, so a missing
 *   one only logs a warning instead of breaking the biome.</li>
 * </ul>
 *
 * <p>Multi-noise points: the Nether varies only temperature and humidity (the other four parameters are always 0). Vanilla
 * uses wastes (0, 0), soul sand valley (0, -0.5), crimson forest (0.4, 0), warped forest (0, 0.5, offset 0.375) and
 * basalt deltas (-0.5, 0, offset 0.175). The new points sit in the empty diagonals, measured by sampling the Nether's
 * own noise: about 10.5% Ember Forest, 12% Bone Sea and 10.7% Obsidian Spires; vanilla keeps nether wastes 30%, crimson
 * forest 10%, basalt deltas 12%, soul sand valley 8% and warped forest 6%.
 */
public final class NetherRealm {
    public static final ResourceKey<Biome> EMBER_FOREST = key("ember_forest");
    public static final ResourceKey<Biome> BONE_SEA = key("bone_sea");
    public static final ResourceKey<Biome> OBSIDIAN_SPIRES = key("obsidian_spires");

    /** A monster from another mod, by id: weight next to vanilla's (ghasts 50, magma cubes 100 in the deltas). */
    private record Spawn(String entity, int weight, int min, int max) {
    }

    private static final Map<ResourceKey<Biome>, List<Spawn>> MODDED_SPAWNS = Map.of(
            EMBER_FOREST, List.of(new Spawn("skyrpg_combat:ember_imp", 60, 1, 3)),
            BONE_SEA, List.of(new Spawn("skyrpg_combat:bone_giant", 12, 1, 1)));

    private NetherRealm() {
    }

    static void init() {
        Registry.register(BuiltInRegistries.FEATURE_TYPE, SkyRpgWorld.id("nether_surface"), NetherSurfaceFeature.CODEC);
        Registry.register(BuiltInRegistries.FEATURE_TYPE, SkyRpgWorld.id("bone_fossil"), BoneFossilFeature.CODEC);
        Registry.register(BuiltInRegistries.FEATURE_TYPE, SkyRpgWorld.id("obsidian_spire"), ObsidianSpireFeature.CODEC);

        // temperature, humidity, continentalness, erosion, depth, weirdness, offset
        NetherBiomes.addNetherBiome(EMBER_FOREST, Climate.parameters(0.3f, -0.3f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f));
        NetherBiomes.addNetherBiome(BONE_SEA, Climate.parameters(-0.3f, -0.3f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f));
        NetherBiomes.addNetherBiome(OBSIDIAN_SPIRES, Climate.parameters(0.45f, 0.3f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f));

        BiomeModifications.create(SkyRpgWorld.id("nether_monsters")).add(ModificationPhase.ADDITIONS,
                BiomeSelectors.includeByKey(EMBER_FOREST, BONE_SEA, OBSIDIAN_SPIRES), (selection, context) -> {
                    for (Spawn spawn : MODDED_SPAWNS.getOrDefault(selection.getBiomeKey(), List.of())) {
                        EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.getOptional(Identifier.parse(spawn.entity())).orElse(null);
                        if (type == null) {
                            SkyRpgWorld.LOGGER.warn("Nether biome {}: no monster {} to spawn", selection.getBiomeKey().identifier(), spawn.entity());
                            continue;
                        }
                        context.getMobSpawnSettings().addSpawn(MobCategory.MONSTER,
                                new MobSpawnSettings.SpawnerData(type, UniformInt.of(spawn.min(), spawn.max())), spawn.weight());
                    }
                });
    }

    /** Which of the three biomes this is, or null for any other. */
    static ResourceKey<Biome> of(Holder<Biome> biome) {
        if (biome.is(EMBER_FOREST)) {
            return EMBER_FOREST;
        }
        if (biome.is(BONE_SEA)) {
            return BONE_SEA;
        }
        return biome.is(OBSIDIAN_SPIRES) ? OBSIDIAN_SPIRES : null;
    }

    private static ResourceKey<Biome> key(String name) {
        return ResourceKey.create(Registries.BIOME, SkyRpgWorld.id(name));
    }
}
