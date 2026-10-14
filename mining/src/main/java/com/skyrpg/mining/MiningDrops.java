package com.skyrpg.mining;

import com.skyrpg.core.text.Notice;
import com.skyrpg.core.stats.StatTypes;
import com.skyrpg.core.stats.Stats;
import com.skyrpg.core.world.PlacedBlocks;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.TagKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * What mining gives besides the block's own drops, and the node rules:
 * <ul>
 *   <li>Glimmer Shards: 10% from stone-like blocks, 2-4 from every ore, 50% from a node, times (1 + Mining
 *   Fortune / 100).</li>
 *   <li>Rare finds from ores: a Prism Fragment 1 in 300, a Heartstone 1 in 3000 (three times rarer from
 *   placed nodes, so real progression means going out to mine).</li>
 *   <li>A mined node turns into its spent husk and regrows; sneaking with an empty hand picks a node up.</li>
 * </ul>
 */
public final class MiningDrops {
    private static final TagKey<Block> STONE = TagKey.create(Registries.BLOCK, SkyRpgMining.id("shard_stone"));
    private static final TagKey<Block> ORES = TagKey.create(Registries.BLOCK, SkyRpgMining.id("shard_ores"));
    private static final int PRISM_CHANCE = 300;
    private static final int HEARTSTONE_CHANCE = 3000;
    private static final int NODE_RARITY = 3;
    /** True while Vein Echo is mining a neighbour, so that one doesn't echo again. */
    private static final ThreadLocal<Boolean> ECHOING = ThreadLocal.withInitial(() -> false);

    private MiningDrops() {
    }

    static void init() {
        PlayerBlockBreakEvents.BEFORE.register((level, player, pos, state, blockEntity) -> {
            if (state.getBlock() instanceof NodeBlock && player instanceof ServerPlayer serverPlayer && !serverPlayer.isCreative() && picksUp(player)) {
                level.removeBlock(pos, false);
                AutoPickup.give(serverPlayer, new ItemStack(state.getBlock()));
                level.playSound(null, pos, SoundEvents.ITEM_PICKUP, SoundSource.PLAYERS, 0.5f, 0.8f);
                return false;
            }
            return true;
        });
        PlayerBlockBreakEvents.AFTER.register((level, player, pos, state, blockEntity) -> {
            if (!(level instanceof ServerLevel serverLevel) || !(player instanceof ServerPlayer serverPlayer) || serverPlayer.isCreative()) {
                return;
            }
            boolean node = state.getBlock() instanceof NodeBlock;
            // Ores and stone a player placed give nothing extra (nodes are placed by design).
            if (!node && PlacedBlocks.isPlaced(level, pos)) {
                return;
            }
            if (node) {
                ((NodeBlock) state.getBlock()).deplete(serverLevel, pos, MiningEffects.value(serverPlayer, MiningEffects.NODE_SPEED));
            }
            reward(serverPlayer, state, node);
            if (!node && state.is(ORES)) {
                extraOre(serverLevel, serverPlayer, pos, state);
            }
            if (node || state.is(ORES)) {
                MiningEffects.oreMined(serverPlayer);
            }
        });
    }

    /** Talents: a chance of the ore dropping twice, and of also mining a touching ore of the same kind. */
    private static void extraOre(ServerLevel level, ServerPlayer player, BlockPos pos, BlockState state) {
        RandomSource random = player.getRandom();
        if (random.nextDouble() < MiningEffects.value(player, MiningEffects.DOUBLE_ORE)) {
            for (ItemStack drop : Block.getDrops(state, level, pos, null, player, player.getMainHandItem())) {
                Block.popResource(level, pos, drop);
            }
        }
        if (!ECHOING.get() && random.nextDouble() < MiningEffects.value(player, MiningEffects.VEIN_CHANCE)) {
            for (Direction direction : Direction.values()) {
                BlockPos next = pos.relative(direction);
                if (level.getBlockState(next).is(state.getBlock())) {
                    ECHOING.set(true);
                    try {
                        player.gameMode.destroyBlock(next);
                    } finally {
                        ECHOING.set(false);
                    }
                    level.playSound(null, next, SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 0.5f, 1.6f);
                    break;
                }
            }
        }
    }

    /** Sneaking with an empty hand turns mining a node into picking it up. */
    static boolean picksUp(Player player) {
        return player.isShiftKeyDown() && player.getMainHandItem().isEmpty();
    }

    private static void reward(ServerPlayer player, BlockState state, boolean node) {
        RandomSource random = player.getRandom();
        boolean ore = !node && state.is(ORES);
        double shards;
        if (node) {
            shards = random.nextFloat() < 0.5f ? 1 : 0;
        } else if (ore) {
            shards = 2 + random.nextInt(3);
        } else if (state.is(STONE)) {
            shards = random.nextFloat() < 0.1f ? 1 : 0;
        } else {
            return;
        }
        if (shards > 0) {
            double fortune = Stats.get(player, StatTypes.MINING_FORTUNE);
            double total = shards * (1 + fortune / 100) * (1 + MiningEffects.value(player, MiningEffects.SHARD_BONUS));
            int count = (int) total + (random.nextDouble() < total % 1 ? 1 : 0);
            AutoPickup.give(player, new ItemStack(MiningItems.GLIMMER_SHARD, count));
        }
        if (ore || node) {
            int rarity = node ? NODE_RARITY : 1;
            double luck = 1 + MiningEffects.value(player, MiningEffects.RARE_FINDS);
            if (random.nextDouble() * HEARTSTONE_CHANCE * rarity < luck) {
                rareFind(player, MiningItems.HEARTSTONE, ChatFormatting.LIGHT_PURPLE);
            } else if (random.nextDouble() * PRISM_CHANCE * rarity < luck) {
                rareFind(player, MiningItems.PRISM_FRAGMENT, ChatFormatting.AQUA);
            }
        }
    }

    private static void rareFind(ServerPlayer player, Item item, ChatFormatting color) {
        ItemStack stack = new ItemStack(item);
        Notice.rareDrop("RARE FIND!", Component.literal("You mined ").withStyle(ChatFormatting.GRAY)
                .append(stack.getHoverName().copy().withStyle(color))
                .append(Component.literal("!").withStyle(ChatFormatting.GRAY)), Notice.rgb(color));
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 1.0f, 1.2f);
        AutoPickup.give(player, stack);
    }
}
