package com.skyrpg.foraging;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Locale;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.feature.Feature;

/**
 * A wild tree of one of the woods, for world generation: {@code {"type": "skyrpg_foraging:sky_tree", "wood": "mistpine"}}
 * in {@code worldgen/feature}. Where and how often is the placed feature's business (see {@link ForagingWorldgen}).
 */
public record SkyTreeFeature(String wood) implements Feature {
    public static final MapCodec<SkyTreeFeature> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Codec.STRING.fieldOf("wood").forGetter(SkyTreeFeature::wood)).apply(instance, SkyTreeFeature::new));

    @Override
    public MapCodec<SkyTreeFeature> codec() {
        return CODEC;
    }

    @Override
    public boolean place(WorldGenLevel level, ChunkGenerator generator, RandomSource random, BlockPos pos) {
        Wood type;
        try {
            type = Wood.valueOf(wood.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return false;
        }
        return TreeShapes.grow(level, pos, type, random, Block.UPDATE_CLIENTS);
    }
}
