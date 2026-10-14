package com.skyrpg.farming;

import com.skyrpg.core.skills.SkillLevels;
import com.skyrpg.core.stats.StatTypes;
import com.skyrpg.core.stats.Stats;
import com.skyrpg.core.text.Msg;
import com.skyrpg.core.text.Notice;
import com.skyrpg.core.world.PlacedBlocks;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Prediction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CocoaBlock;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.NetherWartBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * What harvesting gives besides the crop itself, and the farming gear effects:
 * <ul>
 *   <li>Dewdrops: 15% from every grown crop you break (40% from a melon or pumpkin, 50% from a planter), more with
 *   Farming Fortune and, in daylight, Sunlit.</li>
 *   <li>Rare finds: a Lucky Clover 1 in 400, a Verdant Heart 1 in 4000 (three times rarer from planters).</li>
 *   <li>Reseed replants what you harvest; a harvest area harvests the grown crops around it too; double crops
 *   drops it all again.</li>
 *   <li>Planters: harvested by right-click or by hitting them when ripe; sneak with an empty hand to pick one up.</li>
 * </ul>
 * Drops are popped at the block, so the mining mod's auto-pickup (when installed) puts them in the inventory.
 */
public final class Harvest {
    private static final double DEW_CHANCE = 0.15;
    private static final double BIG_CROP_DEW_CHANCE = 0.4;
    private static final double PLANTER_DEW_CHANCE = 0.5;
    private static final int CLOVER_CHANCE = 400;
    private static final int HEART_CHANCE = 4000;
    private static final int PLANTER_RARITY = 3;
    /** True while an area harvest is breaking the crops around the first, so they don't each start their own. */
    private static final ThreadLocal<Boolean> SWEEPING = ThreadLocal.withInitial(() -> false);

    private Harvest() {
    }

    static void init() {
        PlayerBlockBreakEvents.BEFORE.register((level, player, pos, state, blockEntity) -> {
            if (state.getBlock() instanceof PlanterBlock && player instanceof ServerPlayer serverPlayer && !serverPlayer.isCreative() && picksUp(player)) {
                level.removeBlock(pos, false);
                give(serverPlayer, new ItemStack(state.getBlock()));
                level.playSound(null, pos, SoundEvents.ITEM_PICKUP, SoundSource.PLAYERS, 0.5f, 0.8f);
                return false;
            }
            return true;
        });
        // Hitting a ripe planter harvests it too (quicker than right-clicking a row of them).
        AttackBlockCallback.EVENT.register((player, level, hand, pos, direction) -> {
            BlockState state = level.getBlockState(pos);
            if (state.getBlock() instanceof PlanterBlock planter && state.getValue(PlanterBlock.RIPE) && !picksUp(player)) {
                if (level instanceof ServerLevel serverLevel && player instanceof ServerPlayer serverPlayer) {
                    planter.harvest(serverLevel, pos, state, serverPlayer);
                }
                return InteractionResult.SUCCESS;
            }
            return InteractionResult.PASS;
        });
        PlayerBlockBreakEvents.AFTER.register((level, player, pos, state, blockEntity) -> {
            if (level instanceof ServerLevel serverLevel && player instanceof ServerPlayer serverPlayer && !serverPlayer.isCreative()) {
                broken(serverLevel, serverPlayer, pos, state);
            }
        });
    }

    /** Sneaking with an empty hand picks a planter up instead of harvesting it. */
    static boolean picksUp(Player player) {
        return player.isShiftKeyDown() && player.getMainHandItem().isEmpty();
    }

    private static void broken(ServerLevel level, ServerPlayer player, BlockPos pos, BlockState state) {
        if (PlacedBlocks.isPlaced(level, pos)) {
            return; // a melon, pumpkin or ore a player placed
        }
        BonusDrops.roll(level, player, pos, state);
        if (!grown(state)) {
            return;
        }
        RandomSource random = player.getRandom();
        boolean big = state.is(Blocks.MELON) || state.is(Blocks.PUMPKIN);
        dew(player, pos, big ? BIG_CROP_DEW_CHANCE : DEW_CHANCE, level);
        rareFinds(player, pos, 1);
        double doubled = FarmingEffects.value(player, FarmingEffects.DOUBLE_CROPS);
        if (doubled > 0 && random.nextDouble() < doubled) {
            for (ItemStack drop : Block.getDrops(state, level, pos, null, player, player.getMainHandItem())) {
                Block.popResource(level, pos, drop);
            }
        }
        if (state.getBlock() instanceof CropBlock crop && FarmingEffects.value(player, FarmingEffects.RESEED) > 0
                && level.getBlockState(pos).isAir() && crop.defaultBlockState().canSurvive(level, pos)) {
            level.setBlock(pos, crop.getStateForAge(0), Block.UPDATE_ALL);
        }
        int area = (int) FarmingEffects.value(player, FarmingEffects.HARVEST_AREA);
        if (area > 0 && state.getBlock() instanceof CropBlock && !SWEEPING.get()) {
            SWEEPING.set(true);
            try {
                for (BlockPos at : BlockPos.betweenClosed(pos.offset(-area, 0, -area), pos.offset(area, 0, area))) {
                    if (!at.equals(pos) && grown(level.getBlockState(at)) && level.getBlockState(at).getBlock() instanceof CropBlock) {
                        player.gameMode.destroyBlock(at.immutable());
                    }
                }
            } finally {
                SWEEPING.set(false);
            }
        }
    }

    /** A fully grown crop (vanilla's or ours), or a melon or pumpkin. */
    static boolean grown(BlockState state) {
        Block block = state.getBlock();
        if (block instanceof CropBlock crop) {
            return crop.isMaxAge(state);
        }
        if (block instanceof NetherWartBlock) {
            return state.getValue(NetherWartBlock.AGE) >= 3;
        }
        if (block instanceof CocoaBlock) {
            return state.getValue(CocoaBlock.AGE) >= 2;
        }
        return state.is(Blocks.MELON) || state.is(Blocks.PUMPKIN);
    }

    /** A planter was harvested: its crop (1-2, more with Farming Fortune), Dew, rare finds and a little XP. */
    static void harvestPlanter(ServerPlayer player, BlockPos pos, FarmingBlocks.Planter planter) {
        ServerLevel level = player.level();
        RandomSource random = player.getRandom();
        int count = fortune(player, 1 + random.nextInt(2));
        double doubled = FarmingEffects.value(player, FarmingEffects.DOUBLE_CROPS);
        if (doubled > 0 && random.nextDouble() < doubled) {
            count *= 2;
        }
        give(player, new ItemStack(planter.product(), count));
        dew(player, pos, PLANTER_DEW_CHANCE, level);
        rareFinds(player, pos, PLANTER_RARITY);
        SkillLevels.giveXp(player, SkillLevels.FARMING, planter.xp());
    }

    private static void dew(ServerPlayer player, BlockPos pos, double chance, ServerLevel level) {
        double bonus = FarmingEffects.value(player, FarmingEffects.DEW_BONUS);
        if (level.isBrightOutside()) {
            bonus += FarmingEffects.value(player, FarmingEffects.SUNLIT);
        }
        chance *= 1 + bonus;
        int count = 0;
        RandomSource random = player.getRandom();
        while (chance > 0) {
            if (random.nextDouble() < Math.min(1, chance)) {
                count++;
            }
            chance -= 1;
        }
        count = fortune(player, count);
        if (count > 0) {
            give(player, new ItemStack(FarmingItems.DEWDROP, count));
        }
    }

    /** Times (1 + Farming Fortune / 100), with the fraction as a chance of one more. */
    private static int fortune(ServerPlayer player, int count) {
        if (count <= 0) {
            return count;
        }
        double total = count * (1 + Stats.get(player, StatTypes.FARMING_FORTUNE) / 100);
        return (int) total + (player.getRandom().nextDouble() < total % 1 ? 1 : 0);
    }

    private static void rareFinds(ServerPlayer player, BlockPos pos, int rarity) {
        double luck = 1 + FarmingEffects.value(player, FarmingEffects.RARE_FINDS);
        RandomSource random = player.getRandom();
        if (random.nextDouble() * HEART_CHANCE * rarity < luck) {
            rareFind(player, FarmingItems.VERDANT_HEART, ChatFormatting.DARK_GREEN);
        } else if (random.nextDouble() * CLOVER_CHANCE * rarity < luck) {
            rareFind(player, FarmingItems.LUCKY_CLOVER, ChatFormatting.GREEN);
        }
    }

    static void rareFind(ServerPlayer player, Item item, ChatFormatting color) {
        ItemStack stack = new ItemStack(item);
        give(player, stack.copy());
        Notice.rareDrop("RARE FIND!", Component.literal("You found a ").withStyle(ChatFormatting.GRAY)
                .append(stack.getHoverName().copy().withStyle(color))
                .append(Component.literal("!").withStyle(ChatFormatting.GRAY)), Notice.rgb(color));
        Msg.playSound(player, SoundEvents.PLAYER_LEVELUP, 0.6f, 1.6f);
    }

    /** Into the inventory, or at their feet if it's full. */
    static void give(ServerPlayer player, ItemStack stack) {
        com.skyrpg.core.event.ItemGains.give(player, stack, com.skyrpg.core.event.ItemGains.Source.FARMING);
        if (!stack.isEmpty()) {
            player.drop(stack, false, Prediction.SERVER_ONLY);
        }
    }
}
