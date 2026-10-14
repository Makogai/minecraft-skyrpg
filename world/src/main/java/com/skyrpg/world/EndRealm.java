package com.skyrpg.world;

import net.fabricmc.fabric.api.biome.v1.TheEndBiomes;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;

/**
 * The End rework's three biomes (docs/design/end_game.md): the Void Gardens, the Shattered Isles and the Astral Fields.
 * <ul>
 *   <li>Each biome is data ({@code data/skyrpg_world/worldgen/biome/<id>.json}: fog and sky colours, ambient particles,
 *   sounds and music, endermen, features) and joins the End's biome source here through Fabric's {@link TheEndBiomes}, so
 *   existing worlds get them too, in outer-End chunks generated from now on. The main island is untouched.</li>
 *   <li>The Void Gardens are an alternative to the End Highlands, the Astral Fields an alternative to the End Midlands and the
 *   Shattered Isles one to the small outer islands. Their look is Java features: {@link VoidTreeFeature} grows the purple
 *   void trees, {@link AstralSurfaceFeature} turns end stone into starstone and raises astral crystals, and
 *   {@link ShatteredIslandFeature} floats rubble islets with gaps between them. The glowing flora is plain data (random
 *   patches of {@code void_bloom}).</li>
 *   <li>No monsters from other mods are added: the combat mod's Void Stalker only exists as a slayer boss, with no normal
 *   variant, so the biomes keep vanilla's endermen (and endermites in the Shattered Isles and Astral Fields).</li>
 * </ul>
 *
 * <p>Weights are relative to vanilla's 1.0 for each parent (Fabric picks by weight from the same pool): the Void Gardens
 * 0.8 of the highlands (about 44%), the Astral Fields 0.9 of the midlands (about 47%) and the Shattered Isles 0.8 of the
 * small outer islands (about 44%). The Astral Observatory generates in all five of these biomes and the vanilla highlands and
 * midlands (see tools/builds/dungeons/astral_observatory.py).
 */
public final class EndRealm {
    public static final ResourceKey<Biome> VOID_GARDENS = key("void_gardens");
    public static final ResourceKey<Biome> SHATTERED_ISLES = key("shattered_isles");
    public static final ResourceKey<Biome> ASTRAL_FIELDS = key("astral_fields");

    private EndRealm() {
    }

    static void init() {
        Registry.register(BuiltInRegistries.FEATURE_TYPE, SkyRpgWorld.id("void_tree"), VoidTreeFeature.CODEC);
        Registry.register(BuiltInRegistries.FEATURE_TYPE, SkyRpgWorld.id("shattered_island"), ShatteredIslandFeature.CODEC);
        Registry.register(BuiltInRegistries.FEATURE_TYPE, SkyRpgWorld.id("astral_surface"), AstralSurfaceFeature.CODEC);

        TheEndBiomes.addHighlandsBiome(VOID_GARDENS, 0.8);
        TheEndBiomes.addMidlandsBiome(Biomes.END_HIGHLANDS, ASTRAL_FIELDS, 0.9);
        TheEndBiomes.addMidlandsBiome(VOID_GARDENS, ASTRAL_FIELDS, 0.9);
        TheEndBiomes.addSmallIslandsBiome(SHATTERED_ISLES, 0.8);
    }

    /** Whether a biome is one of the three. */
    static boolean is(Holder<Biome> biome) {
        return biome.is(VOID_GARDENS) || biome.is(SHATTERED_ISLES) || biome.is(ASTRAL_FIELDS);
    }

    private static ResourceKey<Biome> key(String name) {
        return ResourceKey.create(Registries.BIOME, SkyRpgWorld.id(name));
    }
}
