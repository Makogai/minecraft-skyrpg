package com.skyrpg.economy.bank;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.skyrpg.core.text.Msg;
import com.skyrpg.core.text.Notice;
import com.skyrpg.economy.Coins;
import com.skyrpg.economy.Purse;
import com.skyrpg.economy.Sessions;
import com.skyrpg.economy.SkyRpgEconomy;
import com.skyrpg.economy.net.EconomyPayloads;
import io.netty.buffer.ByteBuf;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.player.Player;

/**
 * Coins kept in the bank: saved with the player, kept through death, and synced to that player's
 * client together with a short history of deposits and withdrawals. Opened by the "open_bank" action
 * (the banker NPC); the client asks, the server moves the coins.
 */
public final class Bank {
    public static final Identifier COUNTER = SkyRpgEconomy.id("bank");
    private static final int HISTORY = 10;

    /** One deposit or withdrawal; {@code time} is epoch millis. */
    public record Entry(boolean deposit, long amount, long time) {
        static final Codec<Entry> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.BOOL.fieldOf("deposit").forGetter(Entry::deposit),
                Codec.LONG.fieldOf("amount").forGetter(Entry::amount),
                Codec.LONG.fieldOf("time").forGetter(Entry::time)).apply(instance, Entry::new));
        static final StreamCodec<ByteBuf, Entry> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.BOOL, Entry::deposit,
                ByteBufCodecs.VAR_LONG, Entry::amount,
                ByteBufCodecs.VAR_LONG, Entry::time,
                Entry::new);
    }

    public static final AttachmentType<Long> BALANCE = AttachmentRegistry.create(SkyRpgEconomy.id("bank"), builder -> builder
            .persistent(Codec.LONG)
            .initializer(() -> 0L)
            .copyOnDeath()
            .syncWith(ByteBufCodecs.VAR_LONG, AttachmentSyncPredicate.targetOnly()));

    public static final AttachmentType<List<Entry>> LOG = AttachmentRegistry.create(SkyRpgEconomy.id("bank_log"), builder -> builder
            .persistent(Entry.CODEC.listOf())
            .initializer(List::of)
            .copyOnDeath()
            .syncWith(Entry.STREAM_CODEC.apply(ByteBufCodecs.list()), AttachmentSyncPredicate.targetOnly()));

    private Bank() {
    }

    /** The day (epoch day) interest was last paid. */
    private static final AttachmentType<Long> INTEREST_DAY = AttachmentRegistry.create(SkyRpgEconomy.id("bank_interest_day"), builder -> builder
            .persistent(Codec.LONG)
            .initializer(() -> -1L)
            .copyOnDeath());
    /** Daily interest: 1% of the balance, at most this much. */
    private static final double INTEREST_RATE = 0.01;
    private static final long INTEREST_CAP = 5000;

    public static void init() {
        // Interest is paid on your first login of the day.
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> server.execute(() -> payInterest(handler.player)));
    }

    private static void payInterest(ServerPlayer player) {
        long today = LocalDate.now().toEpochDay();
        long last = player.getAttachedOrElse(INTEREST_DAY, -1L);
        player.setAttached(INTEREST_DAY, today);
        long balance = balance(player);
        if (last == today || last < 0 && balance == 0) {
            return;
        }
        long interest = Math.min(INTEREST_CAP, (long) Math.floor(balance * INTEREST_RATE));
        if (interest <= 0) {
            return;
        }
        player.setAttached(BALANCE, balance + interest);
        List<Entry> log = new ArrayList<>(history(player));
        log.addFirst(new Entry(true, interest, System.currentTimeMillis()));
        player.setAttached(LOG, List.copyOf(log.subList(0, Math.min(HISTORY, log.size()))));
        Notice.of(Notice.Kind.BANK).tag("INTEREST").send(player, Component.literal("Your bank paid ").withStyle(ChatFormatting.GREEN).append(Coins.text(interest))
                .append(Component.literal(" in interest today (1%, up to " + com.skyrpg.core.text.Msg.number(INTEREST_CAP) + ").").withStyle(ChatFormatting.GREEN)));
    }

    public static long balance(Player player) {
        return player.getAttachedOrElse(BALANCE, 0L);
    }

    public static List<Entry> history(Player player) {
        return player.getAttachedOrElse(LOG, List.of());
    }

    public static void open(ServerPlayer player, String keeper) {
        Sessions.open(player, COUNTER);
        ServerPlayNetworking.send(player, new EconomyPayloads.OpenBank(keeper));
    }

    public static void handle(ServerPlayer player, EconomyPayloads.BankAction action) {
        if (!Sessions.isAt(player, COUNTER)) {
            fail(player, "Talk to the banker to use your bank.");
            return;
        }
        boolean deposit = action.deposit();
        long purse = Purse.get(player);
        long balance = balance(player);
        long from = deposit ? purse : balance;
        long amount = switch (action.mode()) {
            case EconomyPayloads.BankAction.ALL -> from;
            case EconomyPayloads.BankAction.HALF -> from / 2;
            default -> action.amount();
        };
        if (from <= 0) {
            fail(player, deposit ? "Your purse is empty." : "Your bank account is empty.");
            return;
        }
        if (amount <= 0) {
            fail(player, action.mode() == EconomyPayloads.BankAction.EXACT ? "Enter an amount first." : "That's less than a coin.");
            return;
        }
        if (amount > from) {
            failWith(player, Component.literal(deposit ? "You only have " : "Your bank only holds ").withColor(0xFF6464)
                    .append(Coins.text(from)).append(Component.literal(".").withColor(0xFF6464)));
            return;
        }
        try {
            if (deposit) {
                player.setAttached(BALANCE, Math.addExact(balance, amount));
                Purse.set(player, purse - amount);
            } else {
                Purse.set(player, Math.addExact(purse, amount));
                player.setAttached(BALANCE, balance - amount);
            }
        } catch (ArithmeticException e) {
            fail(player, "That's more coins than the vault can count.");
            return;
        }
        List<Entry> log = new ArrayList<>(history(player));
        log.addFirst(new Entry(deposit, amount, System.currentTimeMillis()));
        player.setAttached(LOG, List.copyOf(log.subList(0, Math.min(HISTORY, log.size()))));

        Component line = Component.literal(deposit ? "Deposited " : "Withdrew ").withStyle(ChatFormatting.GREEN).append(Coins.text(amount))
                .append(Component.literal(deposit ? " into the bank." : " from the bank.").withStyle(ChatFormatting.GREEN));
        Notice.send(player, Notice.Kind.BANK, line);
        ServerPlayNetworking.send(player, new EconomyPayloads.Notice(line, true));
        Msg.playSound(player, SoundEvents.ARMOR_EQUIP_GOLD.value(), 0.9f, deposit ? 1.25f : 1.05f);
        Msg.playSound(player, SoundEvents.EXPERIENCE_ORB_PICKUP, 0.35f, deposit ? 1.6f : 1.3f);
    }

    private static void fail(ServerPlayer player, String text) {
        failWith(player, Component.literal(text).withColor(0xFF6464));
    }

    private static void failWith(ServerPlayer player, Component text) {
        ServerPlayNetworking.send(player, new EconomyPayloads.Notice(text, false));
        Msg.playSound(player, SoundEvents.NOTE_BLOCK_BASS.value(), 0.7f, 0.6f);
    }
}
