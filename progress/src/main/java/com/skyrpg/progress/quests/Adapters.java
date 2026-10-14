package com.skyrpg.progress.quests;

import com.skyrpg.progress.collections.SkyRpgLevel;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

/** The quest engine's only touch points with the collections package. */
final class Adapters {
    private Adapters() {
    }

    /** How many of a collection's item the player has gathered. */
    static long collectionCount(ServerPlayer player, Identifier collection) {
        return com.skyrpg.progress.collections.Collections.count(player, collection);
    }

    /** The tier the player has reached in a collection (0 = none). */
    static int collectionTier(ServerPlayer player, Identifier collection) {
        return com.skyrpg.progress.collections.Collections.tier(player, collection);
    }

    static int level(Player player) {
        return SkyRpgLevel.level(player);
    }

    static void addXp(ServerPlayer player, int xp, String reason) {
        SkyRpgLevel.addXp(player, xp, reason);
    }
}
