package com.skyrpg.core.economy;

import net.minecraft.server.level.ServerPlayer;

/**
 * Coins for any mod, without depending on the economy mod: rewards pay into the purse, costs take from it. The
 * {@code skyrpg_economy} mod installs the real purse; without it rewards do nothing and nothing is affordable.
 */
public final class Wallet {
    /** The purse, as the economy mod implements it. */
    public interface Backend {
        long balance(ServerPlayer player);

        void give(ServerPlayer player, long amount);

        /** Takes the amount if the purse holds it; false (and nothing taken) if not. */
        boolean take(ServerPlayer player, long amount);
    }

    private static Backend backend = new Backend() {
        @Override
        public long balance(ServerPlayer player) {
            return 0;
        }

        @Override
        public void give(ServerPlayer player, long amount) {
        }

        @Override
        public boolean take(ServerPlayer player, long amount) {
            return false;
        }
    };

    private Wallet() {
    }

    public static void install(Backend installed) {
        backend = installed;
    }

    public static long balance(ServerPlayer player) {
        return backend.balance(player);
    }

    public static void give(ServerPlayer player, long amount) {
        if (amount > 0) {
            backend.give(player, amount);
        }
    }

    public static boolean take(ServerPlayer player, long amount) {
        return amount <= 0 || backend.take(player, amount);
    }
}
