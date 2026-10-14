package com.skyrpg.economy;

import com.mojang.serialization.Codec;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.world.entity.player.Player;

/**
 * The coins a player carries. Saved with the player, kept on death, and synced to that player's
 * client so the HUD can show it. Other mods should go through these methods, not the attachment.
 */
public final class Purse {
    public static final AttachmentType<Long> COINS = AttachmentRegistry.create(SkyRpgEconomy.id("purse"), builder -> builder
            .persistent(Codec.LONG)
            .initializer(() -> 0L)
            .copyOnDeath()
            .syncWith(ByteBufCodecs.VAR_LONG, AttachmentSyncPredicate.targetOnly()));

    private Purse() {
    }

    static void init() {
        // Loads the class so the attachment is registered during mod init.
    }

    public static long get(Player player) {
        return player.getAttachedOrElse(COINS, 0L);
    }

    public static void set(Player player, long amount) {
        player.setAttached(COINS, Math.max(0, amount));
    }

    public static void add(Player player, long amount) {
        set(player, Math.addExact(get(player), amount));
    }

    /** Takes the coins if the player has enough. Returns false and takes nothing otherwise. */
    public static boolean trySpend(Player player, long amount) {
        long coins = get(player);
        if (amount < 0 || coins < amount) {
            return false;
        }
        set(player, coins - amount);
        return true;
    }
}
