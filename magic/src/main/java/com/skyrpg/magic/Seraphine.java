package com.skyrpg.magic;

import com.google.gson.JsonObject;
import com.skyrpg.core.action.Actions;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;

/**
 * Seraphine Vael (the Arcane Library's enchanter) also sells the common spell scrolls: her enchanting action is wrapped so that
 * talking to her while sneaking opens the scroll shop instead (data: {@code skyrpg/shops/arcane_scrolls.json}). Wrapped once the
 * server is starting, so it does not matter which mod registered the action first.
 */
final class Seraphine {
    private static boolean wrapped;

    private Seraphine() {
    }

    static void init() {
        ServerLifecycleEvents.SERVER_STARTING.register(server -> {
            if (wrapped) {
                return;
            }
            Actions.Handler enchanting = Actions.get("open_enchanting");
            if (enchanting == null) {
                return;
            }
            wrapped = true;
            JsonObject shop = new JsonObject();
            shop.addProperty("shop", SkyRpgMagic.id("arcane_scrolls").toString());
            Actions.register("open_enchanting", (player, params) -> {
                if (!(player.isShiftKeyDown() && Actions.run("open_shop", player, shop))) {
                    enchanting.run(player, params);
                }
            });
        });
    }
}
