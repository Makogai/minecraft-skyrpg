package com.skyrpg.stats;

import com.mojang.serialization.Codec;
import com.skyrpg.core.stats.StatType;
import com.skyrpg.core.stats.StatTypes;
import io.netty.buffer.ByteBuf;
import java.util.HashMap;
import java.util.Map;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.world.entity.player.Player;

/**
 * Per-player stat data, readable on both sides:
 * <ul>
 *   <li>{@link #BREAKDOWN}: stat id → source name → value, recomputed by {@link StatEngine} and synced
 *       to that player (not saved: it's rebuilt from the sources).</li>
 *   <li>{@link #MANA}: current mana, saved and synced.</li>
 * </ul>
 * Totals are a stat's base value plus every source's contribution.
 */
public final class PlayerStats {
    public static final AttachmentType<Map<String, Map<String, Double>>> BREAKDOWN = AttachmentRegistry.create(SkyRpgStats.id("breakdown"),
            builder -> builder.syncWith(ByteBufCodecs.<ByteBuf, String, Map<String, Double>, Map<String, Map<String, Double>>>map(HashMap::new,
                            ByteBufCodecs.STRING_UTF8,
                            ByteBufCodecs.<ByteBuf, String, Double, Map<String, Double>>map(HashMap::new, ByteBufCodecs.STRING_UTF8, ByteBufCodecs.DOUBLE)),
                    AttachmentSyncPredicate.targetOnly()));

    public static final AttachmentType<Double> MANA = AttachmentRegistry.create(SkyRpgStats.id("mana"), builder -> builder
            .persistent(Codec.DOUBLE)
            .syncWith(ByteBufCodecs.DOUBLE, AttachmentSyncPredicate.targetOnly()));

    /** Mana every player has before Intelligence. */
    public static final double BASE_MANA = 100;

    private PlayerStats() {
    }

    static void init() {
        // Loads the class so the attachments are registered during mod init.
    }

    /** Where this stat's bonuses come from: source name → value. Empty if nothing adds to it. */
    public static Map<String, Double> breakdown(Player player, StatType stat) {
        Map<String, Map<String, Double>> all = player.getAttachedOrElse(BREAKDOWN, Map.of());
        return all.getOrDefault(stat.id(), Map.of());
    }

    public static double total(Player player, StatType stat) {
        double total = stat.base();
        for (double value : breakdown(player, stat).values()) {
            total += value;
        }
        return total;
    }

    public static double maxMana(Player player) {
        return Math.max(0, BASE_MANA + total(player, StatTypes.INTELLIGENCE));
    }

    /** Current mana; a player who never had any recorded starts full. */
    public static double mana(Player player) {
        Double mana = player.getAttached(MANA);
        return mana == null ? maxMana(player) : mana;
    }

    static void setMana(Player player, double mana) {
        double clamped = Math.max(0, Math.min(maxMana(player), mana));
        Double current = player.getAttached(MANA);
        if (current == null || Math.abs(current - clamped) > 1e-6) {
            player.setAttached(MANA, clamped);
        }
    }
}
