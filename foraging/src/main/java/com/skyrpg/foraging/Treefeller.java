package com.skyrpg.foraging;

import com.skyrpg.core.items.AbilityHandlers;
import com.skyrpg.core.text.Msg;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import com.skyrpg.vfx.Vfx;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.SaplingBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Treefeller ({@code skyrpg_foraging:treefeller}, an axe's right-click ability): for a few seconds, the next natural log
 * you chop fells every log of that tree, one by one as if you'd chopped them (XP, splinters, contracts all count).
 * Axes can add {@link ForagingEffects#LEAF_CLEAR} (the leaves drop at once) and {@link ForagingEffects#REPLANT}.
 */
public final class Treefeller {
    private static final int MAX_LOGS = 200;
    private static final int REACH = 10;
    private static final int LEAF_RADIUS = 3;
    private static final Identifier ARMED_FX = Identifier.fromNamespaceAndPath("skyrpg_abilities", "treefeller_armed");
    private static final Identifier FELL_FX = Identifier.fromNamespaceAndPath("skyrpg_abilities", "treefeller_fell");
    private static final Map<UUID, Long> ARMED = new HashMap<>();
    private static final ThreadLocal<Boolean> FELLING = ThreadLocal.withInitial(() -> false);

    private Treefeller() {
    }

    static void init() {
        // params: {"seconds": 10}
        AbilityHandlers.register("skyrpg_foraging:treefeller", (player, params) -> {
            int seconds = params.has("seconds") ? params.get("seconds").getAsInt() : 10;
            ARMED.put(player.getUUID(), player.level().getServer().getTickCount() + seconds * 20L);
            // The cast (leaf whirl, chips, sounds) is the item's "cast" data; what follows is the "armed" glimmer until the next log.
            return true;
        });
        // While armed, a few leaves keep circling the axe-bearer so you can see it is waiting for a log.
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (server.getTickCount() % 12 == 0) {
                for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                    if (armed(player)) {
                        Vfx.play(player, ARMED_FX);
                    }
                }
            }
        });
    }

    /** True while there's an armed Treefeller waiting for a log (for the HUD-less hint in Chopping). */
    static boolean armed(ServerPlayer player) {
        Long until = ARMED.get(player.getUUID());
        return until != null && player.level().getServer().getTickCount() <= until;
    }

    /** After a natural log was chopped: if Treefeller is armed, fell the rest of the tree. */
    static void chopped(ServerLevel level, ServerPlayer player, BlockPos pos, BlockState state) {
        if (FELLING.get() || !armed(player)) {
            return;
        }
        ARMED.remove(player.getUUID());
        List<BlockPos> logs = connected(level, pos, state.getBlock());
        int felled = 0;
        FELLING.set(true);
        try {
            // Felling wears the axe by a quarter of a point per extra log (see ToolWear.bulk), and it stops if the axe wears out.
            int[] count = {0};
            com.skyrpg.core.items.ToolWear.bulk(() -> {
                for (BlockPos log : logs) {
                    if (player.getMainHandItem().isEmpty() || com.skyrpg.core.items.ToolWear.isBroken(player.getMainHandItem())) {
                        break; // the axe broke (or is out of durability)
                    }
                    if (player.gameMode.destroyBlock(log)) {
                        count[0]++;
                    }
                }
            });
            felled = count[0];
        } finally {
            FELLING.set(false);
        }
        level.playSound(null, pos, SoundEvents.ZOMBIE_BREAK_WOODEN_DOOR, SoundSource.PLAYERS, 0.6f, 0.7f);
        // The tree comes down: a thump ring with bark and leaves at its foot, a creak and a crash, a shake that grows with the tree.
        net.minecraft.world.phys.Vec3 foot = net.minecraft.world.phys.Vec3.atBottomCenterOf(logs.stream().min(Comparator.comparingInt(BlockPos::getY)).orElse(pos));
        Vfx.play(level, FELL_FX, foot);
        Vfx.sound(level, foot, "minecraft:block.wood.break", 1.0f, 0.6f);
        Vfx.sound(level, foot, "minecraft:entity.ravager.attack", 0.5f, 0.6f);
        Vfx.sound(level, foot, "minecraft:block.azalea_leaves.break", 1.0f, 0.7f);
        Vfx.later(level.getServer(), 4, () -> Vfx.sound(level, foot, "minecraft:entity.generic.explode", 0.35f, 0.6f));
        Vfx.shakeAround(level, foot, 20, Math.min(1.2f, 0.4f + logs.size() * 0.03f), 10);
        List<BlockPos> trunk = new ArrayList<>(logs.subList(0, felled));
        trunk.add(pos);
        if (ForagingEffects.value(player, ForagingEffects.LEAF_CLEAR) > 0) {
            clearLeaves(level, player, trunk);
        }
        if (ForagingEffects.value(player, ForagingEffects.REPLANT) > 0) {
            replant(level, trunk, state.getBlock());
        }
        player.sendOverlayMessage(Msg.text("🪓 Treefeller: " + (felled + 1) + " logs", ChatFormatting.GOLD));
    }

    /** The natural logs of this kind touching the chopped one (diagonals too), nearest first. */
    private static List<BlockPos> connected(ServerLevel level, BlockPos start, Block kind) {
        Set<BlockPos> seen = new HashSet<>();
        Deque<BlockPos> queue = new ArrayDeque<>();
        List<BlockPos> found = new ArrayList<>();
        seen.add(start);
        queue.add(start);
        while (!queue.isEmpty() && found.size() < MAX_LOGS) {
            BlockPos at = queue.poll();
            for (BlockPos next : BlockPos.betweenClosed(at.offset(-1, -1, -1), at.offset(1, 1, 1))) {
                if (Math.abs(next.getX() - start.getX()) > REACH || Math.abs(next.getZ() - start.getZ()) > REACH || next.getY() < start.getY() - 1) {
                    continue;
                }
                BlockPos fixed = next.immutable();
                if (seen.add(fixed) && Chopping.naturalLog(level, fixed, level.getBlockState(fixed), kind)) {
                    found.add(fixed);
                    queue.add(fixed);
                }
            }
        }
        found.sort(Comparator.comparingDouble(pos -> pos.distSqr(start)));
        return found;
    }

    /** Leaves near the felled trunk drop at once, their drops (saplings, sticks, apples) straight into the inventory. */
    private static void clearLeaves(ServerLevel level, ServerPlayer player, List<BlockPos> trunk) {
        Set<BlockPos> leaves = new LinkedHashSet<>();
        for (BlockPos log : trunk) {
            for (BlockPos at : BlockPos.betweenClosed(log.offset(-LEAF_RADIUS, -LEAF_RADIUS, -LEAF_RADIUS), log.offset(LEAF_RADIUS, LEAF_RADIUS, LEAF_RADIUS))) {
                BlockState state = level.getBlockState(at);
                if (state.is(BlockTags.LEAVES) && state.hasProperty(LeavesBlock.PERSISTENT) && !state.getValue(LeavesBlock.PERSISTENT)) {
                    leaves.add(at.immutable());
                }
            }
        }
        for (BlockPos at : leaves) {
            BlockState state = level.getBlockState(at);
            Block.getDrops(state, level, at, null, player, player.getMainHandItem()).forEach(drop -> Chopping.give(player, drop));
            level.setBlock(at, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        }
        if (!leaves.isEmpty()) {
            level.playSound(null, trunk.getLast(), SoundEvents.AZALEA_LEAVES_BREAK, SoundSource.BLOCKS, 1.0f, 0.8f);
        }
    }

    /** Plants the tree's sapling where its lowest log stood, if there's soil under it. */
    private static void replant(ServerLevel level, List<BlockPos> trunk, Block log) {
        BlockPos base = trunk.stream().min(Comparator.comparingInt(BlockPos::getY)).orElseThrow();
        Identifier id = BuiltInRegistries.BLOCK.getKey(log);
        if (!id.getPath().endsWith("_log")) {
            return;
        }
        Identifier saplingId = Identifier.fromNamespaceAndPath(id.getNamespace(), id.getPath().replace("_log", "_sapling"));
        Block sapling = BuiltInRegistries.BLOCK.getOptional(saplingId).orElse(null);
        if (sapling instanceof SaplingBlock && level.getBlockState(base).isAir() && sapling.defaultBlockState().canSurvive(level, base)) {
            level.setBlock(base, sapling.defaultBlockState(), Block.UPDATE_ALL);
        }
    }
}
