package com.skyrpg.core.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.skyrpg.core.SkyRpgCore;
import com.skyrpg.core.client.config.ClientConfig;
import com.skyrpg.core.client.config.SkyRpgSettings;
import com.skyrpg.core.client.hud.Banners;
import com.skyrpg.core.client.hud.HudManager;
import com.skyrpg.core.client.menu.ProfilePage;
import com.skyrpg.core.client.menu.SettingsPage;
import com.skyrpg.core.client.menu.SkyRpgMenu;
import com.skyrpg.core.client.tab.SkyRpgTab;
import com.skyrpg.core.client.theme.ThemePack;
import com.skyrpg.core.client.theme.ToggledPacks;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;

public class SkyRpgCoreClient implements ClientModInitializer {
    public static final KeyMapping.Category CATEGORY = KeyMapping.Category.register(SkyRpgCore.id("skyrpg"));
    public static final KeyMapping MENU_KEY = KeyMappingHelper.registerKeyMapping(
            new KeyMapping("key.skyrpg_core.menu", InputConstants.Type.KEYBOARD, InputConstants.KEY_K, CATEGORY));

    @Override
    public void onInitializeClient() {
        ClientConfig.load();
        KeyConflicts.init();
        com.skyrpg.core.client.ui.TextInput.init();
        // Settings appear grouped in this order: Interface (theme, symbols, tab list), then HUD.
        ThemePack.register();
        ToggledPacks.register(SkyRpgCore.id("skyrpg_classic_icons"), "SkyRPG Classic Symbols", "interface.classic_icons",
                SkyRpgSettings.INTERFACE, "Classic stat symbols",
                "Show stats with the original ❤ ❈ ❁ symbols instead of SkyRPG's pixel icons", false);
        ToggledPacks.register(SkyRpgCore.id("skyrpg_weapon_art"), "SkyRPG Weapon Art", "interface.weapon_art",
                SkyRpgSettings.INTERFACE, "Detailed weapon art",
                "Detailed, animated art for swords, pickaxes, spears, bows and shields (vanilla and SkyRPG)", true);
        SkyRpgTab.init();
        HudManager.init();
        Banners.init();
        com.skyrpg.core.client.combat.CombatTagWidget.init();
        ClientTickEvents.END_CLIENT_TICK.register(minecraft -> {
            while (MENU_KEY.consumeClick()) {
                if (minecraft.player != null && minecraft.gui.screen() == null) {
                    SkyRpgMenu.open();
                }
            }
        });

        SkyRpgMenu.registerPage(new ProfilePage());
        SkyRpgMenu.registerPage(new SettingsPage());
        SkyRpgMenu.registerPage(new com.skyrpg.core.client.menu.PlayersPage());
        net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.registerGlobalReceiver(com.skyrpg.core.records.PlayerRecords.Board.TYPE,
                (payload, context) -> context.client().execute(() -> com.skyrpg.core.client.menu.PlayersPage.receive(payload.json())));
    }
}
