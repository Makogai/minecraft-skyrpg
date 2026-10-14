package com.skyrpg.world;

import it.unimi.dsi.fastutil.longs.LongSet;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.structure.Structure;

/** Shared rules for the Nether biomes' big features ({@link BoneFossilFeature}, {@link ObsidianSpireFeature}). */
final class NetherGen {
    private NetherGen() {
    }

    /**
     * Whether a structure has pieces in this chunk (a fortress, a bastion, a dungeon...), where a spire or a skeleton would
     * cut through it. Vanilla's small Nether fossils don't count.
     */
    static boolean nearStructure(WorldGenLevel level, BlockPos origin) {
        ChunkAccess chunk = level.getChunk(origin);
        Map<Structure, LongSet> references = chunk.getAllReferences();
        if (references.isEmpty()) {
            return false;
        }
        Registry<Structure> registry = level.registryAccess().lookupOrThrow(Registries.STRUCTURE);
        for (Map.Entry<Structure, LongSet> entry : references.entrySet()) {
            if (entry.getValue().isEmpty()) {
                continue;
            }
            Identifier id = registry.getKey(entry.getKey());
            if (id == null || !id.getPath().equals("nether_fossil")) {
                return true;
            }
        }
        return false;
    }

    /** Air and the Nether's loose natural ground, which the features may build into. Never lava, bedrock or anything built. */
    static boolean replaceable(BlockState state) {
        return state.isAir() || state.is(Blocks.NETHERRACK) || state.is(BlockTags.SOUL_FIRE_BASE_BLOCKS) || state.is(Blocks.GRAVEL)
                || state.is(Blocks.BLACKSTONE) || state.is(Blocks.BASALT) || state.is(Blocks.MAGMA_BLOCK) || state.is(Blocks.FIRE)
                || state.is(Blocks.SOUL_FIRE) || state.is(WorldContent.EMBER_ASH) || state.is(WorldContent.VOID_CRACK);
    }

    /** Features may write into the chunk they were placed in and its eight neighbours; this keeps a margin inside that. */
    static boolean writable(BlockPos origin, BlockPos pos) {
        ChunkPos chunk = ChunkPos.containing(origin);
        return pos.getX() >= chunk.getMinBlockX() - 15 && pos.getX() <= chunk.getMaxBlockX() + 15
                && pos.getZ() >= chunk.getMinBlockZ() - 15 && pos.getZ() <= chunk.getMaxBlockZ() + 15;
    }
}
