package com.skyrpg.progress.client.dialogue;

import com.skyrpg.core.client.NpcMarkers;
import com.skyrpg.progress.dialogue.DialoguePayloads;
import java.util.HashMap;
import java.util.Map;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;

/** Receives conversations from the server and NPC markers; a page that arrives while another screen is open waits for it to close. */
public final class DialogueClient {
    private static DialoguePayloads.Show pending;

    private DialogueClient() {
    }

    public static void init() {
        ClientPlayNetworking.registerGlobalReceiver(DialoguePayloads.Show.TYPE, (payload, context) -> context.client().execute(() -> show(payload)));
        ClientPlayNetworking.registerGlobalReceiver(DialoguePayloads.Close.TYPE, (payload, context) -> context.client().execute(() -> {
            pending = null;
            if (Minecraft.getInstance().gui.screen() instanceof DialogueScreen screen) {
                screen.closeFromServer();
            }
        }));
        ClientPlayNetworking.registerGlobalReceiver(DialoguePayloads.Markers.TYPE, (payload, context) -> context.client().execute(() -> {
            Map<Identifier, Integer> kinds = new HashMap<>();
            payload.kinds().forEach((npc, kind) -> {
                Identifier id = Identifier.tryParse(npc);
                if (id != null) {
                    kinds.put(id, kind);
                }
            });
            NpcMarkers.setKinds(kinds);
        }));
        ClientPlayConnectionEvents.DISCONNECT.register((handler, minecraft) -> {
            pending = null;
            NpcMarkers.setKinds(Map.of());
        });
        ClientTickEvents.END_CLIENT_TICK.register(minecraft -> {
            if (pending != null && minecraft.gui.screen() == null && minecraft.player != null) {
                DialoguePayloads.Show next = pending;
                pending = null;
                minecraft.gui.setScreen(new DialogueScreen(next));
            }
        });
    }

    private static void show(DialoguePayloads.Show payload) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.gui.screen() instanceof DialogueScreen screen) {
            screen.update(payload);
        } else if (minecraft.gui.screen() == null) {
            minecraft.gui.setScreen(new DialogueScreen(payload));
        } else {
            pending = payload;
        }
    }
}
