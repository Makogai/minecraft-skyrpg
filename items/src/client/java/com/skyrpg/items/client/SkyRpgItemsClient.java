package com.skyrpg.items.client;

import com.skyrpg.core.client.hud.HudManager;
import com.skyrpg.items.ItemDefinitionsPayload;
import com.skyrpg.items.client.accessory.AccessoryScreens;
import com.skyrpg.items.client.armor.ArmorDepthRendering;
import com.skyrpg.items.client.backpack.BackpackScreens;
import com.skyrpg.items.client.crafting.BenchCatalog;
import com.skyrpg.items.client.crafting.BenchScreen;
import com.skyrpg.items.client.crafting.TrackedRecipeWidget;
import com.skyrpg.items.client.render.AccessoryRendering;
import com.skyrpg.items.crafting.BenchPayloads;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

public class SkyRpgItemsClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        ClientPlayNetworking.registerGlobalReceiver(ItemDefinitionsPayload.TYPE, (payload, context) -> payload.apply());
        ClientPlayNetworking.registerGlobalReceiver(BenchPayloads.View.TYPE, (payload, context) -> BenchScreen.receive(payload));
        ClientPlayNetworking.registerGlobalReceiver(BenchPayloads.Tracked.TYPE, (payload, context) -> TrackedRecipeWidget.receive(payload));
        HudManager.register(new TrackedRecipeWidget());
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> BenchCatalog.clear());
        ItemTooltipCallback.EVENT.register((stack, context, flag, lines) -> ItemTooltips.replace(stack, flag, lines));
        AccessoryScreens.init();
        BackpackScreens.init();
        AccessoryRendering.init();
        ArmorDepthRendering.init();
        HeldParticles.init();
    }
}
