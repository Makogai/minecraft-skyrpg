package com.skyrpg.mining;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Prediction;
import net.minecraft.world.item.ItemStack;

/**
 * Blocks a player breaks drop straight into their inventory: while a player is breaking a block (see
 * {@code ServerPlayerGameModeMixin}), everything the block pops (including Fortune's extra drops) goes to
 * them instead of the ground. What doesn't fit falls at their feet, with a short note.
 */
public final class AutoPickup {
    private static final ThreadLocal<ServerPlayer> BREAKER = new ThreadLocal<>();
    private static final ThreadLocal<Boolean> PICKED_UP = ThreadLocal.withInitial(() -> false);
    /** Breaks inside a break (Treefeller, Vein Echo) nest: only the outermost one ends the pickup. */
    private static final ThreadLocal<int[]> DEPTH = ThreadLocal.withInitial(() -> new int[1]);

    private AutoPickup() {
    }

    /** A safety net: if a break ever threw halfway, the next tick starts clean. */
    static void init() {
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (DEPTH.get()[0] != 0) {
                DEPTH.get()[0] = 0;
                BREAKER.remove();
            }
        });
    }

    public static void begin(ServerPlayer player) {
        if (DEPTH.get()[0]++ > 0) {
            return;
        }
        BREAKER.set(player);
        PICKED_UP.set(false);
    }

    public static void end() {
        if (--DEPTH.get()[0] > 0) {
            return;
        }
        DEPTH.get()[0] = 0;
        ServerPlayer player = BREAKER.get();
        BREAKER.remove();
        if (player != null && PICKED_UP.get()) {
            player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ITEM_PICKUP, SoundSource.PLAYERS,
                    0.2f, 1.4f + player.getRandom().nextFloat() * 0.4f);
        }
        PICKED_UP.remove();
    }

    /** True if a block is being broken by a player right now and this drop was handed to them. */
    public static boolean take(ItemStack stack) {
        ServerPlayer player = BREAKER.get();
        if (player == null || stack.isEmpty()) {
            return false;
        }
        give(player, MiningEffects.smelt(player, stack));
        return true;
    }

    /** Into the inventory, or at their feet if it's full. */
    public static void give(ServerPlayer player, ItemStack stack) {
        com.skyrpg.core.event.ItemGains.give(player, stack, com.skyrpg.core.event.ItemGains.Source.MINING);
        PICKED_UP.set(true);
        if (!stack.isEmpty()) {
            player.drop(stack, false, Prediction.SERVER_ONLY);
            player.sendOverlayMessage(Component.literal("Inventory full! Drops fell at your feet.").withColor(0xFFFF6B6B));
        }
    }
}
