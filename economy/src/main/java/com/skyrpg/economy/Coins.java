package com.skyrpg.economy;

import com.skyrpg.core.stats.StatTypes;
import com.skyrpg.core.text.Msg;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

/** How coin amounts look in chat: the SkyRPG coin icon and the amount, in the brand gold. */
public final class Coins {
    /** Same gold as the client theme's accent. */
    public static final int COLOR = 0xF2B33D;

    private Coins() {
    }

    /** "⛁ 1,234" with the coin icon. */
    public static MutableComponent text(long amount) {
        return Component.literal(StatTypes.COIN_ICON + " " + Msg.number(amount)).withColor(COLOR);
    }
}
