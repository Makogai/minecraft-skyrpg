package com.skyrpg.mining;

import java.util.HashMap;
import java.util.Map;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Prediction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * Blocks a player breaks drop straight into their inventory: while a player is breaking a block (see
 * {@code ServerPlayerGameModeMixin}), everything the block pops (including Fortune's extra drops) goes to
 * them instead of the ground. What doesn't fit falls at their feet, with a short note. Blocks that fall apart a moment
 * later because of that break (the sugar cane or cactus above, a torch on its side) go to them too: see {@link #take}.
 */
public final class AutoPickup {
    private static final ThreadLocal<ServerPlayer> BREAKER = new ThreadLocal<>();
    private static final ThreadLocal<Boolean> PICKED_UP = ThreadLocal.withInitial(() -> false);
    /** Breaks inside a break (Treefeller, Vein Echo) nest: only the outermost one ends the pickup. */
    private static final ThreadLocal<int[]> DEPTH = ThreadLocal.withInitial(() -> new int[1]);
    /** How long a broken spot keeps catching drops from blocks next to it that collapse (sugar cane breaks a block a tick). */
    private static final int AFTERMATH_TICKS = 20;
    /** Recently broken or collapsed spots ("dimension|pos") to who broke them and until when (server tick). */
    private static final Map<String, Recent> RECENT = new HashMap<>();
    private static long tick;

    private record Recent(ServerPlayer player, long until) {
    }

    private AutoPickup() {
    }

    /** A safety net: if a break ever threw halfway, the next tick starts clean. */
    static void init() {
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            tick = server.getTickCount();
            RECENT.values().removeIf(recent -> recent.until() < tick || recent.player().isRemoved());
            if (DEPTH.get()[0] != 0) {
                DEPTH.get()[0] = 0;
                BREAKER.remove();
            }
        });
    }

    public static void begin(ServerPlayer player, BlockPos pos) {
        remember(player, player.level(), pos);
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
    public static boolean take(Level level, BlockPos pos, ItemStack stack) {
        ServerPlayer player = BREAKER.get();
        if (player == null) {
            player = aftermath(level, pos);
        }
        if (player == null || stack.isEmpty()) {
            return false;
        }
        give(player, MiningEffects.smelt(player, stack));
        return true;
    }

    /**
     * Who broke a block next to {@code pos} in the last second, if anyone: then this drop comes from a block that fell
     * apart because of it. The spot is remembered in turn, so a whole column of sugar cane follows.
     */
    private static ServerPlayer aftermath(Level level, BlockPos pos) {
        if (RECENT.isEmpty()) {
            return null;
        }
        for (Direction direction : Direction.values()) {
            Recent recent = RECENT.get(key(level, pos.relative(direction)));
            if (recent != null && recent.player().level() == level) {
                remember(recent.player(), level, pos);
                return recent.player();
            }
        }
        return null;
    }

    private static void remember(ServerPlayer player, Level level, BlockPos pos) {
        RECENT.put(key(level, pos), new Recent(player, tick + AFTERMATH_TICKS));
    }

    private static String key(Level level, BlockPos pos) {
        return level.dimension().identifier() + "|" + pos.asLong();
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
