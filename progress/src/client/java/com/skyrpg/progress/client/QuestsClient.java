package com.skyrpg.progress.client;

import com.skyrpg.core.client.hud.Banners;
import com.skyrpg.core.client.hud.HudManager;
import com.skyrpg.core.client.menu.SkyRpgMenu;
import com.skyrpg.progress.quests.QuestData;
import com.skyrpg.progress.quests.QuestPayloads;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** The questline on the client: the quest data the server sends, the Journal page, the quest tracker and the toasts. */
public final class QuestsClient {
    private QuestsClient() {
    }

    public static void init() {
        ClientPlayNetworking.registerGlobalReceiver(QuestPayloads.Defs.TYPE,
                (payload, context) -> context.client().execute(() -> QuestData.loadJson(payload.json())));
        ClientPlayNetworking.registerGlobalReceiver(QuestPayloads.Toast.TYPE, (payload, context) -> context.client().execute(() -> {
            ItemStack icon = new ItemStack(BuiltInRegistries.ITEM.getOptional(Identifier.parse(payload.icon())).orElse(Items.BOOK));
            Banners.show(new Banners.Banner(icon, payload.label(), Component.literal(payload.title()), Component.literal(payload.subtitle()), payload.color()));
        }));
        com.skyrpg.progress.client.dialogue.DialogueClient.init();
        SkyRpgMenu.registerPage(new JournalPage());
        HudManager.register(new QuestTrackerWidget());
    }
}
