package com.skyrpg.fishing;

import com.skyrpg.core.action.Catches;
import com.skyrpg.core.skills.SkillLevels;
import com.skyrpg.core.stats.StatTypes;
import com.skyrpg.core.stats.Stats;
import com.skyrpg.core.text.Msg;
import com.skyrpg.core.text.Notice;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Prediction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.BuiltInLootTables;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;

/**
 * What fishing gives on top of the vanilla catch:
 * <ul>
 *   <li>Fishing XP for every catch.</li>
 *   <li>Tidescales (the Fishing Master's currency): 10% a catch, times (1 + Fishing Fortune / 100).</li>
 *   <li>Rare finds: an Abyssal Pearl 1 in 300, a Leviathan Scale 1 in 3000.</li>
 *   <li>A Double Catch chance from gear and talents.</li>
 *   <li>Sea creatures: Sea Creature Chance that the bite is a creature instead of a fish (see {@link SeaCreatures}).</li>
 *   <li>In lava ({@link LavaFishing}): more Fishing XP, lava's own catches and sea creatures, and a Molten Pearl
 *       (1 in 250) for the rare find. Everything else (Tidescales, Double Catch, the stats) works the same.</li>
 * </ul>
 */
public final class Catching {
    private static final double CATCH_XP = 8;
    private static final double LAVA_CATCH_XP = 14;
    private static final double SCALE_CHANCE = 0.1;
    private static final int PEARL_CHANCE = 300;
    private static final int LEVIATHAN_CHANCE = 3000;
    private static final int MOLTEN_PEARL_CHANCE = 250;
    /** Each Fishing Speed point takes 4 ticks off the wait: 25 points is one level of Lure. */
    private static final int TICKS_PER_SPEED = 4;
    private static final int MAX_EXTRA_LURE = 400;

    private Catching() {
    }

    public static int extraLureTicks(ServerPlayer player) {
        return Math.min(MAX_EXTRA_LURE, (int) Math.round(Math.max(0, Stats.get(player, StatTypes.FISHING_SPEED)) * TICKS_PER_SPEED));
    }

    /** At the moment of reeling in a bite: maybe it's a sea creature. True if one came up (the fish doesn't). */
    public static boolean trySeaCreature(FishingHook hook, ServerPlayer player) {
        double chance = Math.max(0, Stats.get(player, StatTypes.SEA_CREATURE_CHANCE)) / 100;
        boolean lava = LavaFishing.inLava(hook);
        // Never in the hub: it's a safe place.
        if (player.getRandom().nextDouble() >= chance || !(hook.isInWater() || lava)
                || player.level().dimension().identifier().getNamespace().equals("skyrpg_hub")) {
            return false;
        }
        boolean spawned = SeaCreatures.surface(hook, player, lava);
        if (spawned) {
            Catches.CAUGHT.invoker().onCaught(player, ItemStack.EMPTY, true);
        }
        return spawned;
    }

    /** After vanilla handed out the catch. */
    public static void caught(FishingHook hook, ServerPlayer player, ItemStack rod) {
        RandomSource random = player.getRandom();
        boolean lava = LavaFishing.inLava(hook);
        SkillLevels.giveXp(player, SkillLevels.FISHING, lava ? LAVA_CATCH_XP : CATCH_XP);
        if (random.nextDouble() < SCALE_CHANCE) {
            scales(player, 1);
        }
        rareFinds(player, 1, lava);
        if (random.nextDouble() < FishingEffects.value(player, FishingEffects.DOUBLE_CATCH)) {
            doubleCatch(hook, player, rod, lava);
        }
        Catches.CAUGHT.invoker().onCaught(player, ItemStack.EMPTY, false);
    }

    /** Tidescales, times (1 + Fishing Fortune / 100) and the scale bonus. */
    static void scales(ServerPlayer player, int base) {
        double total = base * (1 + Math.max(0, Stats.get(player, StatTypes.FISHING_FORTUNE)) / 100)
                * (1 + FishingEffects.value(player, FishingEffects.SCALE_BONUS));
        int count = (int) total + (player.getRandom().nextDouble() < total % 1 ? 1 : 0);
        if (count > 0) {
            give(player, new ItemStack(FishingItems.TIDESCALE, count));
        }
    }

    static void rareFinds(ServerPlayer player, int rarity) {
        rareFinds(player, rarity, false);
    }

    /** In lava the only rare find is a Molten Pearl; Rare Finds bonuses (talents, rods, sets) count the same. */
    static void rareFinds(ServerPlayer player, int rarity, boolean lava) {
        double luck = 1 + FishingEffects.value(player, FishingEffects.RARE_FINDS);
        RandomSource random = player.getRandom();
        if (lava) {
            if (random.nextDouble() * MOLTEN_PEARL_CHANCE * rarity < luck) {
                rareFind(player, FishingItems.MOLTEN_PEARL, ChatFormatting.GOLD);
            }
            return;
        }
        if (random.nextDouble() * LEVIATHAN_CHANCE * rarity < luck) {
            rareFind(player, FishingItems.LEVIATHAN_SCALE, ChatFormatting.DARK_AQUA);
        } else if (random.nextDouble() * PEARL_CHANCE * rarity < luck) {
            rareFind(player, FishingItems.ABYSSAL_PEARL, ChatFormatting.AQUA);
        }
    }

    private static void rareFind(ServerPlayer player, Item item, ChatFormatting color) {
        ItemStack stack = new ItemStack(item);
        give(player, stack.copy());
        Notice.rareDrop("RARE FIND!", Component.literal("You fished up ").withStyle(ChatFormatting.GRAY)
                .append(stack.getHoverName().copy().withStyle(color))
                .append(Component.literal("!").withStyle(ChatFormatting.GRAY)), Notice.rgb(color));
        Msg.playSound(player, SoundEvents.PLAYER_LEVELUP, 0.6f, 1.4f);
    }

    /** The bite comes up twice: one more roll of the fishing loot, flung at the player like the first (lava's: handed over). */
    private static void doubleCatch(FishingHook hook, ServerPlayer player, ItemStack rod, boolean lava) {
        ServerLevel level = player.level();
        LootParams params = new LootParams.Builder(level)
                .withParameter(LootContextParams.ORIGIN, hook.position())
                .withParameter(LootContextParams.TOOL, rod)
                .withParameter(LootContextParams.THIS_ENTITY, hook)
                .withLuck(player.getLuck())
                .create(LootContextParamSets.FISHING);
        LootTable table = level.getServer().reloadableRegistries().getLootTable(lava ? LavaFishing.CATCHES : BuiltInLootTables.FISHING);
        List<ItemStack> items = table.getRandomItems(params);
        for (ItemStack stack : items) {
            if (lava) {
                give(player, stack);
                continue;
            }
            ItemEntity entity = new ItemEntity(level, hook.getX(), hook.getY(), hook.getZ(), stack);
            double dx = player.getX() - hook.getX();
            double dy = player.getY() - hook.getY();
            double dz = player.getZ() - hook.getZ();
            entity.setDeltaMovement(dx * 0.1, dy * 0.1 + Math.sqrt(Math.sqrt(dx * dx + dy * dy + dz * dz)) * 0.08, dz * 0.1);
            level.addFreshEntity(entity);
        }
        if (!items.isEmpty()) {
            player.sendOverlayMessage(Msg.text("Double Catch!", ChatFormatting.AQUA));
        }
    }

    /** Into the inventory, or at their feet if it's full. */
    static void give(ServerPlayer player, ItemStack stack) {
        com.skyrpg.core.event.ItemGains.give(player, stack, com.skyrpg.core.event.ItemGains.Source.FISHING);
        if (!stack.isEmpty()) {
            player.drop(stack, false, Prediction.SERVER_ONLY);
        }
    }
}
