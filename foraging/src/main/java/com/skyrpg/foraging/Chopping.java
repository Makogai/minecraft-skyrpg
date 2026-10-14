package com.skyrpg.foraging;

import com.skyrpg.core.stats.StatTypes;
import com.skyrpg.core.stats.Stats;
import com.skyrpg.core.text.Msg;
import com.skyrpg.core.text.Notice;
import com.skyrpg.core.world.PlacedBlocks;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Prediction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * What chopping gives besides the log itself, and the stump rules:
 * <ul>
 *   <li>Heartwood Splinters: 10% from a log, 50% from a stump, times (1 + Foraging Fortune / 100).</li>
 *   <li>Rare finds from logs: a Golden Acorn 1 in 500, Elder Heartwood 1 in 5000 (three times rarer from stumps).</li>
 *   <li>Saplings of the new woods from vanilla logs, by data ({@link ForageDrops}).</li>
 *   <li>A chopped stump goes bare and regrows; sneaking with an empty hand picks a stump up.</li>
 * </ul>
 * Logs a player placed give nothing extra (no place-and-chop farming).
 */
public final class Chopping {
    private static final double LOG_SPLINTER_CHANCE = 0.1;
    private static final double STUMP_SPLINTER_CHANCE = 0.5;
    private static final int ACORN_CHANCE = 500;
    private static final int ELDER_CHANCE = 5000;
    private static final int STUMP_RARITY = 3;

    private Chopping() {
    }

    static void init() {
        PlayerBlockBreakEvents.BEFORE.register((level, player, pos, state, blockEntity) -> {
            if (state.getBlock() instanceof StumpBlock && player instanceof ServerPlayer serverPlayer && !serverPlayer.isCreative() && picksUp(player)) {
                level.removeBlock(pos, false);
                give(serverPlayer, new ItemStack(state.getBlock()));
                level.playSound(null, pos, SoundEvents.ITEM_PICKUP, SoundSource.PLAYERS, 0.5f, 0.8f);
                return false;
            }
            return true;
        });
        PlayerBlockBreakEvents.AFTER.register((level, player, pos, state, blockEntity) -> {
            if (!(level instanceof ServerLevel serverLevel) || !(player instanceof ServerPlayer serverPlayer) || serverPlayer.isCreative()) {
                return;
            }
            boolean stump = state.getBlock() instanceof StumpBlock;
            if (!stump && (!state.is(BlockTags.LOGS) || PlacedBlocks.isPlaced(level, pos))) {
                return;
            }
            if (stump) {
                ((StumpBlock) state.getBlock()).deplete(serverLevel, pos, ForagingEffects.value(serverPlayer, ForagingEffects.STUMP_SPEED));
            }
            splinters(serverPlayer, stump);
            rareFinds(serverPlayer, stump ? STUMP_RARITY : 1);
            if (!stump) {
                ForageDrops.roll(serverLevel, serverPlayer, pos, state);
                if (serverPlayer.getRandom().nextDouble() < ForagingEffects.value(serverPlayer, ForagingEffects.DOUBLE_LOGS)) {
                    Block.getDrops(state, serverLevel, pos, null, serverPlayer, serverPlayer.getMainHandItem()).forEach(drop -> give(serverPlayer, drop));
                }
                Treefeller.chopped(serverLevel, serverPlayer, pos, state);
            }
        });
    }

    /** Sneaking with an empty hand turns chopping a stump into picking it up. */
    static boolean picksUp(Player player) {
        return player.isShiftKeyDown() && player.getMainHandItem().isEmpty();
    }

    private static void splinters(ServerPlayer player, boolean stump) {
        RandomSource random = player.getRandom();
        if (random.nextDouble() >= (stump ? STUMP_SPLINTER_CHANCE : LOG_SPLINTER_CHANCE)) {
            return;
        }
        double bonus = ForagingEffects.value(player, ForagingEffects.SPLINTER_BONUS);
        if (player.level().isDarkOutside()) {
            bonus += ForagingEffects.value(player, ForagingEffects.NIGHT_SPLINTERS);
        }
        double total = (1 + Stats.get(player, StatTypes.FORAGING_FORTUNE) / 100) * (1 + bonus);
        int count = (int) total + (random.nextDouble() < total % 1 ? 1 : 0);
        give(player, new ItemStack(ForagingItems.HEARTWOOD_SPLINTER, count));
    }

    private static void rareFinds(ServerPlayer player, int rarity) {
        double luck = 1 + ForagingEffects.value(player, ForagingEffects.RARE_FINDS);
        RandomSource random = player.getRandom();
        if (random.nextDouble() * ELDER_CHANCE * rarity < luck) {
            rareFind(player, ForagingItems.ELDER_HEARTWOOD, ChatFormatting.LIGHT_PURPLE);
        } else if (random.nextDouble() * ACORN_CHANCE * rarity < luck) {
            rareFind(player, ForagingItems.GOLDEN_ACORN, ChatFormatting.GOLD);
        }
    }

    private static void rareFind(ServerPlayer player, Item item, ChatFormatting color) {
        ItemStack stack = new ItemStack(item);
        give(player, stack.copy());
        Notice.rareDrop("RARE FIND!", Component.literal("You found ").withStyle(ChatFormatting.GRAY)
                .append(stack.getHoverName().copy().withStyle(color))
                .append(Component.literal("!").withStyle(ChatFormatting.GRAY)), Notice.rgb(color));
        Msg.playSound(player, SoundEvents.PLAYER_LEVELUP, 0.6f, 1.4f);
    }

    /** Into the inventory, or at their feet if it's full. */
    static void give(ServerPlayer player, ItemStack stack) {
        if (stack.isEmpty()) {
            return;
        }
        com.skyrpg.core.event.ItemGains.give(player, stack, com.skyrpg.core.event.ItemGains.Source.FORAGING);
        if (!stack.isEmpty()) {
            player.drop(stack, false, Prediction.SERVER_ONLY);
        }
    }

    /** Whether a block counts as a natural log here (for Treefeller). */
    static boolean naturalLog(ServerLevel level, BlockPos pos, BlockState state, Block kind) {
        return state.is(kind) && !PlacedBlocks.isPlaced(level, pos);
    }
}
