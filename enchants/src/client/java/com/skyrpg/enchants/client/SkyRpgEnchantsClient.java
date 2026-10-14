package com.skyrpg.enchants.client;

import com.skyrpg.core.client.hud.Banners;
import com.skyrpg.enchants.Codex;
import com.skyrpg.enchants.Enchant;
import com.skyrpg.enchants.EnchantContent;
import com.skyrpg.enchants.EnchantPayloads;
import com.skyrpg.enchants.Enchants;
import com.skyrpg.enchants.SkyRpgEnchants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.event.Event;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.Identifier;

/** Enchanting on the client: the definitions from the server, tooltips, the enchanting screen and Codex banners. */
public final class SkyRpgEnchantsClient implements ClientModInitializer {
    /** Our tooltip lines go in before the items mod rebuilds SkyRPG tooltips, so it keeps them (after the stats). */
    private static final Identifier BEFORE_ITEMS = SkyRpgEnchants.id("before_items");

    @Override
    public void onInitializeClient() {
        ClientPlayNetworking.registerGlobalReceiver(Enchants.Definitions.TYPE, (payload, context) -> payload.apply());
        ClientPlayNetworking.registerGlobalReceiver(EnchantPayloads.View.TYPE, (payload, context) -> EnchantingScreen.receive(payload));
        ClientPlayNetworking.registerGlobalReceiver(Codex.Discovered.TYPE, (payload, context) -> {
            Enchant def = Enchants.get(Identifier.parse(payload.enchant()));
            if (def != null) {
                Banners.show(new Banners.Banner(EnchantContent.tome(def, 1), "CODEX DISCOVERY",
                        Component.literal(def.name()).withStyle(Style.EMPTY.withColor(def.rarity().color()).withBold(def.ultimate())),
                        Component.literal(def.rarity().label() + " enchant  ·  +" + payload.xp() + " Enchanting XP  ·  "
                                + payload.total() + "/" + Enchants.all().size() + " discovered"), def.rarity().color()));
            }
        });
        ItemTooltipCallback.EVENT.addPhaseOrdering(BEFORE_ITEMS, Event.DEFAULT_PHASE);
        ItemTooltipCallback.EVENT.register(BEFORE_ITEMS, (stack, context, flag, lines) -> EnchantTooltips.add(stack, lines));
    }
}
