package com.skyrpg.economy.client;

import com.skyrpg.core.client.hud.HudAnchor;
import com.skyrpg.core.client.hud.HudManager;
import com.skyrpg.core.client.hud.HudWidget;
import com.skyrpg.core.client.menu.ProfileStat;
import com.skyrpg.core.client.menu.SkyRpgMenu;
import com.skyrpg.core.client.tab.SkyRpgTab;
import com.skyrpg.core.client.tab.TabPanel;
import com.skyrpg.core.client.ui.Draw;
import com.skyrpg.core.client.ui.Theme;
import com.skyrpg.core.stats.StatTypes;
import com.skyrpg.core.text.Msg;
import com.skyrpg.economy.Purse;
import com.skyrpg.economy.SkyRpgEconomy;
import com.skyrpg.economy.bank.Bank;
import com.skyrpg.economy.net.EconomyPayloads;
import java.util.List;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

public class SkyRpgEconomyClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        // Just the number: the coin icon says what it is, and big balances need the room ("1.23B" if even that won't fit).
        SkyRpgMenu.registerProfileStat(new ProfileStat("Purse", StatTypes.COIN_ICON, Theme.ACCENT, 0, player -> Msg.number(Purse.get(player))));
        SkyRpgMenu.registerProfileStat(new ProfileStat("Bank", StatTypes.COIN_ICON, Theme.ACCENT, 0, player -> Msg.number(Bank.balance(player))));
        SkyRpgTab.registerPanel(new TabPanel("Profile", Theme.ACCENT, 0, player -> List.of(
                new TabPanel.Line("Purse", Msg.number(Purse.get(player)), Theme.ACCENT),
                new TabPanel.Line("Bank", Msg.number(Bank.balance(player)), Theme.ACCENT))));
        HudManager.register(new PurseWidget());
        PayPage payPage = new PayPage();
        SkyRpgMenu.registerPage(payPage);

        // Shops and the bank: the server opens them and reports results (for the Pay page too).
        ClientPlayNetworking.registerGlobalReceiver(EconomyPayloads.Shop.TYPE, (payload, context) -> {
            Minecraft minecraft = context.client();
            if (minecraft.gui.screen() instanceof ShopScreen open && open.shopId().equals(payload.shop())) {
                open.update(payload);
            } else if (!payload.refresh()) {
                minecraft.gui.setScreen(new ShopScreen(payload));
            }
        });
        ClientPlayNetworking.registerGlobalReceiver(EconomyPayloads.OpenBank.TYPE, (payload, context) -> {
            if (!(context.client().gui.screen() instanceof BankScreen)) {
                context.client().gui.setScreen(new BankScreen(payload.keeper()));
            }
        });
        ClientPlayNetworking.registerGlobalReceiver(EconomyPayloads.Notice.TYPE, (payload, context) -> {
            if (context.client().gui.screen() instanceof CounterScreen screen) {
                screen.notice(payload.text(), payload.good());
            } else {
                // Answers to the menu's Pay page (shown if the page is still open).
                payPage.notice(payload.text(), payload.good());
            }
        });
    }

    /** A small pill with the coin icon and your purse. */
    private static final class PurseWidget implements HudWidget {
        @Override
        public Identifier id() {
            return SkyRpgEconomy.id("purse");
        }

        @Override
        public Component name() {
            return Component.literal("Purse");
        }

        private static String text() {
            Minecraft minecraft = Minecraft.getInstance();
            return StatTypes.COIN_ICON + " " + (minecraft.player == null ? "0" : Msg.number(Purse.get(minecraft.player)));
        }

        @Override
        public int width(Font font) {
            return font.width(text()) + 12;
        }

        @Override
        public int height(Font font) {
            return 16;
        }

        @Override
        public HudAnchor defaultAnchor() {
            return HudAnchor.TOP_LEFT;
        }

        @Override
        public int defaultX() {
            return 4;
        }

        @Override
        public int defaultY() {
            return 4;
        }

        @Override
        public void render(GuiGraphicsExtractor g, Font font, boolean editing) {
            Draw.rounded(g, 0, 0, width(font), 16, 0xB00C0F16);
            Draw.text(g, font, text(), 6, 4, Theme.ACCENT);
        }
    }
}
