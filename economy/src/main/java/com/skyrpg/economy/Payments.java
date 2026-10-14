package com.skyrpg.economy;

import com.skyrpg.core.text.Msg;
import com.skyrpg.core.text.Notice;
import com.skyrpg.economy.net.EconomyPayloads;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Util;

/**
 * Coins from one player to another. /pay and the Pay page in the SkyRPG menu both go through
 * {@link #transfer}, so the checks, chat lines and sounds are the same either way. The page's requests
 * ({@link EconomyPayloads.Pay}) are checked here again in full: the client only asks.
 */
public final class Payments {
    /** Page requests sooner than this after the last one that went through are dropped (double clicks, spam). */
    private static final long MIN_INTERVAL_MILLIS = 250;
    private static final int FAIL_COLOR = 0xFF6464;
    private static final Map<UUID, Long> LAST_REQUEST = new ConcurrentHashMap<>();

    private Payments() {
    }

    /** Listens for the Pay page's requests. The payload type is registered with the others in {@link EconomyPayloads#register}. */
    public static void init() {
        ServerPlayNetworking.registerGlobalReceiver(EconomyPayloads.Pay.TYPE, Payments::receive);
        ServerPlayConnectionEvents.DISCONNECT.register(Payments::forget);
    }

    /** What happened: on success {@code message} is the payer's chat line, otherwise why nothing was paid. */
    public record Result(boolean success, Component message) {
        static Result fail(Component reason) {
            return new Result(false, reason);
        }

        static Result fail(String reason) {
            return fail(Component.literal(reason));
        }
    }

    /**
     * Moves {@code amount} coins from {@code from}'s purse to {@code to}'s, if it can. On success the
     * receiver gets their chat line and both hear a sound; the payer's line is in the result, for the
     * caller to show (a command sends it as feedback, so it also reaches /execute and RCON).
     */
    public static Result transfer(ServerPlayer from, ServerPlayer to, long amount) {
        if (from == to) {
            return Result.fail("You can't pay yourself.");
        }
        if (amount < 1) {
            return Result.fail("Pay at least 1 coin.");
        }
        long purse = Purse.get(from);
        if (purse < amount) {
            return Result.fail(Component.literal("You don't have ").append(Coins.text(amount)).append("."));
        }
        long theirs = Purse.get(to);
        if (theirs > Long.MAX_VALUE - amount) {
            return Result.fail(to.getDisplayName().copy().append(" can't carry that many coins."));
        }
        Purse.set(from, purse - amount);
        Purse.set(to, theirs + amount);
        SkyRpgEconomy.LOGGER.info("{} paid {} {} coins", from.getScoreboardName(), to.getScoreboardName(), amount);

        Notice.of(Notice.Kind.COINS).tag("RECEIVED").send(to, from.getDisplayName().copy().append(Msg.text(" paid you ", ChatFormatting.GRAY)).append(Coins.text(amount)));
        Msg.playSound(to, SoundEvents.EXPERIENCE_ORB_PICKUP, 0.7f, 1.35f);
        Msg.playSound(to, SoundEvents.NOTE_BLOCK_CHIME.value(), 0.5f, 1.6f);
        Msg.playSound(from, SoundEvents.ARMOR_EQUIP_GOLD.value(), 0.9f, 1.2f);
        Msg.playSound(from, SoundEvents.EXPERIENCE_ORB_PICKUP, 0.35f, 0.9f);
        return new Result(true, Msg.text("You paid ", ChatFormatting.GRAY).append(Coins.text(amount))
                .append(Msg.text(" to ", ChatFormatting.GRAY)).append(to.getDisplayName()));
    }

    /** A request from the Pay page: same checks as /pay, plus the target must still be online. */
    private static void receive(EconomyPayloads.Pay request, ServerPlayNetworking.Context context) {
        ServerPlayer player = context.player();
        long now = Util.getMillis();
        Long last = LAST_REQUEST.get(player.getUUID());
        if (last != null && now - last < MIN_INTERVAL_MILLIS) {
            return;
        }
        LAST_REQUEST.put(player.getUUID(), now);

        ServerPlayer target = context.server().getPlayerList().getPlayer(request.target());
        Result result = target == null ? Result.fail("That player isn't online anymore.") : transfer(player, target, request.amount());
        if (!result.success()) {
            ServerPlayNetworking.send(player, new EconomyPayloads.Notice(Component.empty().append(result.message()).withColor(FAIL_COLOR), false));
            Msg.playSound(player, SoundEvents.NOTE_BLOCK_BASS.value(), 0.7f, 0.6f);
            return;
        }
        Notice.of(Notice.Kind.COINS).tag("PAID").send(player, result.message());
        ServerPlayNetworking.send(player, new EconomyPayloads.Notice(Msg.text("Sent ", ChatFormatting.GREEN).append(Coins.text(request.amount()))
                .append(Msg.text(" to ", ChatFormatting.GREEN)).append(Component.literal(target.getScoreboardName()).withStyle(ChatFormatting.WHITE)), true));
    }

    private static void forget(ServerGamePacketListenerImpl handler, MinecraftServer server) {
        LAST_REQUEST.remove(handler.player.getUUID());
    }
}
