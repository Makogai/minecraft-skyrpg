package com.skyrpg.core.world;

import com.mojang.serialization.Codec;
import com.skyrpg.core.SkyRpgCore;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;

/**
 * Remembers blocks that players placed, so they don't pay out skill XP, shards, rare finds or event points when broken
 * again (no "place an ore, mine it, repeat"). Kept per chunk and saved with it. Blocks in the
 * {@code skyrpg_core:placed_still_rewards} tag (crops, saplings, nodes, planters...) are never marked: placing them is
 * how you use them. A mark is cleared at the end of the tick its block is broken, so every mod's break handler sees it.
 * <pre>
 * if (PlacedBlocks.isPlaced(level, pos)) { return; } // in a PlayerBlockBreakEvents.AFTER handler
 * </pre>
 */
public final class PlacedBlocks {
    public static final TagKey<Block> STILL_REWARDS = TagKey.create(Registries.BLOCK, SkyRpgCore.id("placed_still_rewards"));

    private static final AttachmentType<List<Long>> MARKS = AttachmentRegistry.create(SkyRpgCore.id("placed_blocks"), builder -> builder
            .persistent(Codec.LONG.listOf())
            .initializer(List::of));
    /** Broken this tick: cleared at the end of it. */
    private static final Set<Broken> BROKEN = new HashSet<>();

    private record Broken(ServerLevel level, long pos) {
    }

    private PlacedBlocks() {
    }

    public static void init() {
        PlayerBlockBreakEvents.AFTER.register((level, player, pos, state, blockEntity) -> {
            if (level instanceof ServerLevel serverLevel) {
                BROKEN.add(new Broken(serverLevel, pos.asLong()));
            }
        });
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (!BROKEN.isEmpty()) {
                for (Broken broken : BROKEN) {
                    clear(broken.level(), BlockPos.of(broken.pos()));
                }
                BROKEN.clear();
            }
        });
    }

    /** A player placed this block (called by the placement hook). */
    public static void mark(Level level, BlockPos pos, BlockState state) {
        if (level.isClientSide() || state.is(STILL_REWARDS) || state.isAir()) {
            return;
        }
        LevelChunk chunk = level.getChunkAt(pos);
        List<Long> marks = chunk.getAttachedOrElse(MARKS, List.of());
        long packed = pos.asLong();
        if (!marks.contains(packed)) {
            List<Long> updated = new ArrayList<>(marks);
            updated.add(packed);
            chunk.setAttached(MARKS, List.copyOf(updated));
            chunk.markUnsaved();
        }
    }

    /** Whether a player placed the block here (true until the end of the tick it was broken in). */
    public static boolean isPlaced(Level level, BlockPos pos) {
        if (!level.hasChunkAt(pos)) {
            return false;
        }
        return level.getChunkAt(pos).getAttachedOrElse(MARKS, List.of()).contains(pos.asLong());
    }

    /** Forgets that a player placed the block here (for code that removes a placed block itself). */
    public static void clear(Level level, BlockPos pos) {
        if (!level.hasChunkAt(pos)) {
            return;
        }
        LevelChunk chunk = level.getChunkAt(pos);
        List<Long> marks = chunk.getAttachedOrElse(MARKS, List.of());
        if (marks.contains(pos.asLong())) {
            List<Long> updated = new ArrayList<>(marks);
            updated.remove(pos.asLong());
            chunk.setAttached(MARKS, updated.isEmpty() ? null : List.copyOf(updated));
            chunk.markUnsaved();
        }
    }
}
