package com.skyrpg.core.text;

import java.util.Locale;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;

/** Formatting and feedback helpers so every module speaks with the same voice. */
public final class Msg {
    private static final int[] ROMAN_VALUES = {1000, 900, 500, 400, 100, 90, 50, 40, 10, 9, 5, 4, 1};
    private static final String[] ROMAN_SYMBOLS = {"M", "CM", "D", "CD", "C", "XC", "L", "XL", "X", "IX", "V", "IV", "I"};

    private Msg() {
    }

    /** 1234567 -> "1,234,567" */
    public static String number(long value) {
        return String.format(Locale.ROOT, "%,d", value);
    }

    /** 1234.5 -> "1,234.5", 4.0 -> "4" */
    public static String number(double value) {
        if (value == Math.rint(value) && Math.abs(value) < 1e15) {
            return number((long) value);
        }
        return String.format(Locale.ROOT, "%,.1f", value);
    }

    /**
     * Short form for tight spaces, three significant digits: 950 -> "950", 12_345 -> "12.3k",
     * 1_234_567_890 -> "1.23B". Below 10,000 it is the full number, since "4,321" is as short as "4.32k".
     */
    public static String compact(double value) {
        double abs = Math.abs(value);
        if (abs < 10_000) {
            return number(abs < 100 ? value : Math.round(value));
        }
        String[] units = {"k", "M", "B", "T"};
        double scaled = abs;
        int unit = -1;
        while (scaled >= 999.5 && unit < units.length - 1) {
            scaled /= 1000;
            unit++;
        }
        String digits = scaled >= 100 ? String.format(Locale.ROOT, "%.0f", scaled)
                : scaled >= 10 ? String.format(Locale.ROOT, "%.1f", scaled) : String.format(Locale.ROOT, "%.2f", scaled);
        if (digits.contains(".")) {
            digits = digits.replaceAll("0+$", "").replaceAll("\\.$", "");
        }
        return (value < 0 ? "-" : "") + digits + units[unit];
    }

    /** 14 -> "XIV", 0 -> "0" */
    public static String roman(int value) {
        if (value <= 0) {
            return "0";
        }
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < ROMAN_VALUES.length; i++) {
            while (value >= ROMAN_VALUES[i]) {
                value -= ROMAN_VALUES[i];
                out.append(ROMAN_SYMBOLS[i]);
            }
        }
        return out.toString();
    }

    public static MutableComponent text(String text, ChatFormatting... style) {
        return Component.literal(text).withStyle(style);
    }

    /** "1,234 coins" in gold. */
    public static MutableComponent coins(long amount) {
        return text(number(amount) + (amount == 1 ? " coin" : " coins"), ChatFormatting.GOLD);
    }

    /** Plays a sound only this player hears, at their position. */
    public static void playSound(ServerPlayer player, SoundEvent sound, float volume, float pitch) {
        player.connection.send(new ClientboundSoundPacket(BuiltInRegistries.SOUND_EVENT.wrapAsHolder(sound), SoundSource.PLAYERS,
                player.getX(), player.getY(), player.getZ(), volume, pitch, player.getRandom().nextLong()));
    }
}
