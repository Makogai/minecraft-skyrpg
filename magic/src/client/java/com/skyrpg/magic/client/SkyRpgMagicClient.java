package com.skyrpg.magic.client;

import com.skyrpg.core.client.hud.HudManager;
import com.skyrpg.magic.MagicPayloads;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.event.Event;
import net.minecraft.resources.Identifier;

/** Wand controls, the spell wheel, the spell HUD box and charge ring, tooltips and screen shake. */
public class SkyRpgMagicClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        MagicInput.init();
        ClientTickEvents.END_CLIENT_TICK.register(MagicInput::tick);
        HudManager.register(new SpellWidget());
        ChargeRing.init();
        ClientPlayNetworking.registerGlobalReceiver(MagicPayloads.Definitions.TYPE, (payload, context) -> payload.apply());
        ClientPlayNetworking.registerGlobalReceiver(MagicPayloads.Cooldown.TYPE, (payload, context) -> ClientMagic.cooldown(payload.spell(), payload.ticks()));
        ClientPlayNetworking.registerGlobalReceiver(MagicPayloads.ChargeCancel.TYPE, (payload, context) -> ClientMagic.endCharge());
        ClientPlayNetworking.registerGlobalReceiver(MagicPayloads.Shake.TYPE, (payload, context) -> ClientMagic.shake(payload.amount(), payload.ticks()));
        // After the items mod has built its tooltip (its callback is in the default phase).
        Identifier late = Identifier.fromNamespaceAndPath("skyrpg_magic", "late");
        ItemTooltipCallback.EVENT.addPhaseOrdering(Event.DEFAULT_PHASE, late);
        ItemTooltipCallback.EVENT.register(late, (stack, context, flag, lines) -> MagicTooltips.add(stack, lines));
    }
}
